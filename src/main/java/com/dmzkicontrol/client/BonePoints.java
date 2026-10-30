package com.dmzkicontrol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Posicoes reais (no mundo) da cabeca, dos olhos e da ponta da mao direita de cada jogador, lidas do
 * modelo ja animado pelo DMZ (voando, correndo, transformando, qualquer pose).
 *
 * <p>Funciona como uma camada do jogador: o DMZ chama as camadas dos outros mods com os ossos na pose
 * certa. A matriz de cada osso leva ao espaco da camera; com a matriz do mundo deste quadro
 * (capturada no inicio da renderizacao) a gente volta para coordenadas do mundo.
 */
public final class BonePoints {
    /** Pontos de um jogador num quadro. Tudo em coordenadas do mundo. */
    public record Points(Vec3 rightHand, Vec3 rightEye, Vec3 leftEye, Vec3 headForward, Vec3 headUp, long frame) {}

    private static final Map<Integer, Points> POINTS = new HashMap<>();
    private static Matrix4f levelInverse;
    private static Vec3 camera = Vec3.ZERO;
    private static long frame;

    // espaco do modelo vanilla (1 unidade = 1/16 de bloco): y para baixo, frente = -z, direita do personagem = -x
    private static final Vector3f RIGHT_HAND = new Vector3f(-1f / 16f, 10.5f / 16f, 0f);
    private static final Vector3f RIGHT_EYE = new Vector3f(-2f / 16f, -3.5f / 16f, -4.3f / 16f);
    private static final Vector3f LEFT_EYE = new Vector3f(2f / 16f, -3.5f / 16f, -4.3f / 16f);

    private BonePoints() {}

    /** Inicio do quadro (antes das entidades): guarda a matriz da camera para desfazer depois. */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        frame++;
        levelInverse = new Matrix4f(event.getPoseStack().last().pose()).invert();
        camera = event.getCamera().getPosition();
        if (POINTS.size() > 64) POINTS.entrySet().removeIf(e -> frame - e.getValue().frame() > 40);
    }

    /** Pontos deste quadro (null se o jogador nao foi desenhado agora, ex.: voce em primeira pessoa). */
    public static Points get(int entityId) {
        Points p = POINTS.get(entityId);
        return p != null && frame - p.frame() <= 1 ? p : null;
    }

    public static void clear() {
        POINTS.clear();
    }

    private static Vec3 world(Matrix4f bone, Vector3f local) {
        Vector3f v = bone.transformPosition(new Vector3f(local));
        Vector3f w = levelInverse.transformPosition(v);
        return camera.add(w.x(), w.y(), w.z());
    }

    private static Vec3 worldDir(Matrix4f bone, Vector3f local) {
        Vector3f v = bone.transformDirection(new Vector3f(local));
        Vector3f w = levelInverse.transformDirection(v);
        Vec3 d = new Vec3(w.x(), w.y(), w.z());
        return d.lengthSqr() < 1e-9 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** Camada que so le os ossos (nao desenha nada). */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
            if (levelInverse == null) return;
            if (Minecraft.getInstance().level == null) return;
            try {
                PlayerModel<AbstractClientPlayer> model = this.getParentModel();
                pose.pushPose();
                model.head.translateAndRotate(pose);
                Matrix4f head = new Matrix4f(pose.last().pose());
                pose.popPose();
                pose.pushPose();
                model.rightArm.translateAndRotate(pose);
                Matrix4f arm = new Matrix4f(pose.last().pose());
                pose.popPose();
                POINTS.put(player.getId(), new Points(
                        world(arm, RIGHT_HAND),
                        world(head, RIGHT_EYE),
                        world(head, LEFT_EYE),
                        worldDir(head, new Vector3f(0, 0, -1)),
                        worldDir(head, new Vector3f(0, -1, 0)),
                        frame));
            } catch (Throwable ignored) {
            }
        }
    }
}
