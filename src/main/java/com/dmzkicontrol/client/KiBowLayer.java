package com.dmzkicontrol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Arco de Ki preso na mao esquerda (terceira pessoa). Desenha no espaco do braco, entao acompanha a
 * animacao do DMZ em vez da camera; a corda vai ate a mao direita enquanto ela puxa.
 */
public class KiBowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Centro da mao no espaco do braco (blocos; +y vai do ombro para a mao). */
    private static final Vector3f LEFT_HAND = new Vector3f(1f / 16f, 0.62f, 0f);
    private static final Vector3f RIGHT_HAND = new Vector3f(-1f / 16f, 0.62f, 0f);

    public KiBowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        float[] st = KiBowRenderer.state(player, partial);
        if (st == null || player.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        pose.pushPose();
        model.leftArm.translateAndRotate(pose);
        Matrix4f left = new Matrix4f(pose.last().pose());
        pose.popPose();
        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        Matrix4f right = new Matrix4f(pose.last().pose());
        pose.popPose();

        // braco levantado pela animacao? (se nao, o arco fica na frente do corpo em vez de apontar para baixo)
        boolean raised = Math.abs(model.leftArm.xRot) > 0.9f;
        float[] color = KiFx.kiColor(player, KiBowRenderer.FALLBACK);
        if (!raised) {
            Matrix4f root = new Matrix4f(pose.last().pose());
            Vector3f camR = new Matrix4f(root).invert().transformPosition(new Vector3f());
            // espaco do modelo: y para baixo, frente = -z, esquerda do personagem = +x
            KiBowRenderer.drawBow(KiBowRenderer.pen(buffers, root), new Vec3(0.30, 0.18, -0.72), new Vec3(0, 0, -1), new Vec3(0, -1, 0),
                    new Vec3(camR.x(), camR.y(), camR.z()), 1.15f * st[1], st[0], st[2], null, color, KiFx.seed(player));
            return;
        }

        Matrix4f inv = new Matrix4f(left).invert();
        Vector3f camL = inv.transformPosition(new Vector3f());
        Vector3f rh = inv.transformPosition(right.transformPosition(new Vector3f(RIGHT_HAND)));

        Vec3 grip = new Vec3(LEFT_HAND.x(), LEFT_HAND.y(), LEFT_HAND.z());
        Vec3 fwd = new Vec3(0, 1, 0);
        Vec3 up = new Vec3(0, 0, 1);
        Vec3 rightHand = new Vec3(rh.x(), rh.y(), rh.z());
        // so usa a mao direita se ela estiver atras da pegada e a uma distancia de quem puxa a corda
        boolean pulling = rightHand.y < grip.y - 0.12 && rightHand.distanceTo(grip) < 1.7;
        KiBowRenderer.drawBow(KiBowRenderer.pen(buffers, left), grip, fwd, up, new Vec3(camL.x(), camL.y(), camL.z()),
                1.15f * st[1], st[0], st[2], pulling ? rightHand : null, color, KiFx.seed(player));
    }
}
