package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.KiArrowTechnique;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O Arco de Ki do Granolah, desenhado com o mesmo shader das armas de ki do DMZ e na cor do ki do
 * usuario: dois bracos recurvos de energia com chamas de ki, a corda puxada conforme a carga e uma
 * flecha de ki encaixada que cresce. Ao soltar, o arco se desfaz.
 *
 * <p>Na primeira pessoa o arco fica menor e na frente da camera, como uma arma segurada.
 */
public final class KiBowRenderer {
    private static final int FALLBACK = 0x5FD3C4;
    private static final float CHARGE_TICKS = 30f;
    private static final float RELEASE_TICKS = 6f;
    /** entityId do dono -> tick em que soltou. */
    private static final Map<Integer, Long> RELEASED = new HashMap<>();

    private KiBowRenderer() {}

    public static void onRelease(Entity owner) {
        if (owner.level() != null) RELEASED.put(owner.getId(), owner.level().getGameTime());
    }

    private record Bow(LivingEntity owner, float draw, float scale) {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();

        Map<Integer, Float> draws = new HashMap<>();
        for (AbstractKiProjectile p : mc.level.getEntitiesOfClass(AbstractKiProjectile.class, new AABB(cam, cam).inflate(96),
                e -> KiArrowTechnique.ID.equals(e.getTechniqueId()) && !e.isFiring())) {
            if (p.getOwner() instanceof LivingEntity owner) {
                draws.merge(owner.getId(), Math.min(1f, (p.tickCount + partial) / CHARGE_TICKS), Math::max);
            }
        }
        RELEASED.entrySet().removeIf(en -> now - en.getValue() > RELEASE_TICKS);
        List<Bow> bows = new ArrayList<>();
        for (Map.Entry<Integer, Float> en : draws.entrySet()) {
            if (mc.level.getEntity(en.getKey()) instanceof LivingEntity owner) bows.add(new Bow(owner, en.getValue(), 1f));
        }
        for (Map.Entry<Integer, Long> en : RELEASED.entrySet()) {
            if (draws.containsKey(en.getKey())) continue;
            float k = 1f - Mth.clamp((now - en.getValue() + partial) / RELEASE_TICKS, 0f, 1f);
            if (k > 0.02f && mc.level.getEntity(en.getKey()) instanceof LivingEntity owner) bows.add(new Bow(owner, 0f, k));
        }
        if (bows.isEmpty()) return;

        Matrix4f m = event.getPoseStack().last().pose();
        if (KiFx.available()) {
            MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
            RenderType type = KiFx.type();
            VertexConsumer vc = bs.getBuffer(type);
            for (Bow b : bows) drawBow(new ShaderPen(vc, m), mc, b, partial, cam);
            bs.endBatch(type);
        } else {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buf = Tesselator.getInstance().getBuilder();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Bow b : bows) drawBow(new GlowPen(buf, m), mc, b, partial, cam);
            BufferUploader.drawWithShader(buf.end());
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }
    }

    // ------------------------------------------------------------------ geometria

    private static void drawBow(Pen pen, Minecraft mc, Bow b, float partial, Vec3 camWorld) {
        LivingEntity p = b.owner();
        boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        Vec3 look = p.getViewVector(partial).normalize();
        double yaw = Math.toRadians(p.getViewYRot(partial));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = look.cross(left).normalize();

        float s = (firstPerson ? 0.55f : 1.0f) * b.scale();
        Vec3 grip;
        if (firstPerson) {
            grip = p.getEyePosition(partial).add(look.scale(0.95)).add(left.scale(0.30)).subtract(up.scale(0.16));
        } else {
            double h = p.getBbHeight() * 0.74;
            grip = p.getPosition(partial).add(0, h, 0).add(look.scale(0.62 * p.getBbHeight() / 1.8)).add(left.scale(0.24 * p.getBbHeight() / 1.8));
        }
        // tudo relativo a camera
        grip = grip.subtract(camWorld);
        Vec3 cam = Vec3.ZERO;
        float draw = b.draw();
        float[] c = KiFx.kiColor(p, FALLBACK);
        float seed = KiFx.seed(p);
        float pulse = 1f + 0.25f * draw;

        // bracos recurvos: sobem/descem, curvam para tras e as pontas voltam para frente
        int seg = 5;
        double height = 0.82 * s;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 prev = limb(grip, up, look, side, 0.0, height, s);
            for (int i = 1; i <= seg; i++) {
                double t = i / (double) seg;
                Vec3 pt = limb(grip, up, look, side, t, height, s);
                if (i < seg) pen.rod(cam, prev, pt, KiFx.ROD_ELBOW, (0.95f - 0.3f * (float) t) * s * pulse, c, seed);
                else pen.rod(cam, prev, pt, KiFx.ROD_TINE, 0.8f * s * pulse, c, seed);
                prev = pt;
            }
        }
        // empunhadura (mais grossa, onde a mao segura)
        pen.rod(cam, grip.subtract(up.scale(0.16 * s)), grip.add(up.scale(0.16 * s)), KiFx.ROD_ELBOW, 1.35f * s * pulse, c, seed);

        // corda: da pegada (nock) ate as pontas
        Vec3 top = limb(grip, up, look, 1, 1.0, height, s);
        Vec3 bottom = limb(grip, up, look, -1, 1.0, height, s);
        double rest = depth(1.0) * s;
        Vec3 nock = grip.subtract(look.scale(rest + 0.5 * s * draw));
        pen.rod(cam, nock, top, KiFx.ROD_SHAFT, 0.22f * s, c, seed);
        pen.rod(cam, nock, bottom, KiFx.ROD_SHAFT, 0.22f * s, c, seed);

        // flecha de ki encaixada (cresce com a carga)
        if (draw > 0.08f && b.scale() > 0.99f) {
            float g = 0.6f + 0.6f * draw;
            Vec3 tip = grip.add(look.scale((0.45 + 0.25 * draw) * s));
            double len = tip.subtract(nock).length();
            pen.blade(cam, nock, tip, (float) Math.min(0.42 * s * g, len * 0.32), c, seed);
            Vec3 bs = KiFx.side(nock, tip, cam);
            Vec3 back = tip.subtract(look.scale(0.28 * s * g));
            pen.rod(cam, tip, back.add(bs.scale(0.18 * s * g)), KiFx.ROD_TINE, 0.7f * s * g, c, seed);
            pen.rod(cam, tip, back.subtract(bs.scale(0.18 * s * g)), KiFx.ROD_TINE, 0.7f * s * g, c, seed);
        }
    }

    /** Recuo do braco (para tras) em funcao de t (0 = pegada, 1 = ponta): curva de arco recurvo. */
    private static double depth(double t) {
        double tip = Math.max(0.0, t - 0.76);
        return 0.40 * t * t - 2.4 * tip * tip;
    }

    private static Vec3 limb(Vec3 grip, Vec3 up, Vec3 look, int side, double t, double height, float s) {
        return grip.add(up.scale(side * height * t)).subtract(look.scale(depth(t) * s));
    }

    // ------------------------------------------------------------------ "canetas" (shader do DMZ ou fita simples)

    private interface Pen {
        void rod(Vec3 cam, Vec3 a, Vec3 b, int piece, float thickness, float[] c, float seed);

        void blade(Vec3 cam, Vec3 a, Vec3 b, float halfWidth, float[] c, float seed);
    }

    private record ShaderPen(VertexConsumer vc, Matrix4f m) implements Pen {
        public void rod(Vec3 cam, Vec3 a, Vec3 b, int piece, float thickness, float[] c, float seed) {
            KiFx.rod(vc, m, cam, a, b, piece, thickness, c, seed);
        }

        public void blade(Vec3 cam, Vec3 a, Vec3 b, float halfWidth, float[] c, float seed) {
            KiFx.blade(vc, m, cam, a, b, halfWidth, c, seed);
        }
    }

    private record GlowPen(VertexConsumer vc, Matrix4f m) implements Pen {
        public void rod(Vec3 cam, Vec3 a, Vec3 b, int piece, float thickness, float[] c, float seed) {
            KiFx.glow(vc, m, cam, a, b, 0.07f * thickness, c[0], c[1], c[2], 0.45f);
            KiFx.glow(vc, m, cam, a, b, 0.025f * thickness, 1f, 1f, 1f, 0.9f);
        }

        public void blade(Vec3 cam, Vec3 a, Vec3 b, float halfWidth, float[] c, float seed) {
            KiFx.glow(vc, m, cam, a, b, halfWidth * 0.5f, c[0], c[1], c[2], 0.5f);
            KiFx.glow(vc, m, cam, a, b, halfWidth * 0.2f, 1f, 1f, 1f, 0.95f);
        }
    }
}
