package com.dmzkicontrol.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Mira do laser cerealiano: enquanto o braco mira, o ki se junta na ponta do dedo. Uma bolinha de luz
 * cresce, faiscas vem de fora para dentro e uma estrela de 4 pontas gira cada vez mais rapido;
 * no fim o laser sai dali (mesmo ponto que o servidor usa para o disparo).
 */
public final class LaserAimFx {
    private static final int FALLBACK = 0xFFB347;
    /** entityId -> {tick de inicio, duracao}. */
    private static final Map<Integer, long[]> AIMING = new HashMap<>();

    private LaserAimFx() {}

    public static void start(int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        AIMING.put(entityId, new long[]{mc.level.getGameTime(), Math.max(1, ticks)});
    }

    public static void stop(int entityId) {
        AIMING.remove(entityId);
    }

    public static void clear() {
        AIMING.clear();
    }

    /** Ponta do dedo: a mao de verdade no modelo do DMZ (voando inclusive); sem ela, a conta do servidor. */
    public static Vec3 fingertip(Player p, float partial) {
        BonePoints.Points bp = BonePoints.get(p.getId());
        if (bp != null) return bp.rightHand();
        return estimatedTip(p, partial);
    }

    /** Igual ao CerealianHooks.fireLaser no servidor (personagem em pe). */
    public static Vec3 estimatedTip(Player p, float partial) {
        float yaw = Mth.lerp(partial, p.yRotO, p.getYRot());
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        double x = Mth.lerp(partial, p.xOld, p.getX());
        double y = Mth.lerp(partial, p.yOld, p.getY()) + p.getEyeHeight() - 0.42;
        double z = Mth.lerp(partial, p.zOld, p.getZ());
        return new Vec3(x, y, z).add(right.scale(0.36)).add(p.getViewVector(partial).scale(0.75));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || AIMING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { AIMING.clear(); return; }
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        // eixos da tela (billboard): tudo de frente para a camera
        Vec3 look = new Vec3(event.getCamera().getLookVector());
        Vec3 r0 = look.cross(new Vec3(0, 1, 0));
        final Vec3 right = (r0.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r0).normalize();
        final Vec3 up = right.cross(look).normalize();

        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        AIMING.entrySet().removeIf(en -> {
            long[] v = en.getValue();
            float age = now - v[0] + partial;
            if (age > v[1] + 10) return true;
            Entity e = mc.level.getEntity(en.getKey());
            if (!(e instanceof Player p) || !p.isAlive()) return true;
            boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson();
            float t = Mth.clamp(age / v[1], 0f, 1f);
            Vec3 tip = self ? firstPersonTip(p, partial) : fingertip(p, partial);
            draw(vc, m, tip.subtract(cam), right, up, KiFx.kiColor(p, FALLBACK), t, age, p.getId());
            return false;
        });
        bs.endBatch(RenderType.lightning());
    }

    public static Vec3 firstPersonTipPublic(Player p, float partial) {
        return firstPersonTip(p, partial);
    }

    /** Em primeira pessoa o braco aparece no canto: a luz fica na frente da mao direita na tela. */
    private static Vec3 firstPersonTip(Player p, float partial) {
        Vec3 eye = p.getEyePosition(partial);
        float yaw = Mth.lerp(partial, p.yRotO, p.getYRot());
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        return eye.add(p.getViewVector(partial).scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float[] rgb, float t, float age, int seed) {
        float ease = t * t * (3f - 2f * t);
        // nucleo que cresce
        float core = 0.025f + 0.07f * ease;
        disc(vc, m, c, right, up, core * 2.2f, 16, rgb, 0.18f + 0.25f * ease, false);
        disc(vc, m, c, right, up, core, 16, rgb, 0.7f + 0.3f * ease, false);
        disc(vc, m, c, right, up, core * 0.45f, 12, new float[]{1f, 1f, 1f}, 0.8f * ease, false);
        // estrela de 4 pontas girando cada vez mais rapido
        float spin = age * (0.05f + 0.35f * ease);
        float len = 0.06f + 0.22f * ease;
        for (int i = 0; i < 4; i++) {
            float a = spin + i * Mth.HALF_PI;
            spike(vc, m, c, right, up, a, len, 0.012f + 0.01f * ease, rgb, 0.8f * ease + 0.1f);
        }
        // faiscas vindo de fora para dentro
        for (int i = 0; i < 8; i++) {
            float phase = ((age * 0.09f + i * 0.125f + seed * 0.37f) % 1f);
            float ang = i * 0.785f + seed;
            float r = 0.45f * (1f - phase);
            Vec3 dir = right.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
            Vec3 p0 = c.add(dir.scale(r)), p1 = c.add(dir.scale(r + 0.06f));
            Vec3 side = right.scale(-Mth.sin(ang)).add(up.scale(Mth.cos(ang))).scale(0.006f);
            float a = 0.9f * phase * (0.3f + 0.7f * ease);
            v(vc, m, p0.subtract(side), rgb, a);
            v(vc, m, p1.subtract(side), rgb, 0f);
            v(vc, m, p1.add(side), rgb, 0f);
            v(vc, m, p0.add(side), rgb, a);
        }
    }

    /** Junta 4 vertices e desenha o quadrado dos dois lados. */
    private static final float[][] QUAD = new float[4][7];
    private static int quadN;

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float[] rgb, float a) {
        float[] q = QUAD[quadN++];
        q[0] = (float) p.x; q[1] = (float) p.y; q[2] = (float) p.z;
        q[3] = rgb[0]; q[4] = rgb[1]; q[5] = rgb[2]; q[6] = Mth.clamp(a, 0f, 1f);
        if (quadN < 4) return;
        quadN = 0;
        for (int i = 0; i < 4; i++) emit(vc, m, QUAD[i]);
        for (int i = 3; i >= 0; i--) emit(vc, m, QUAD[i]);
    }

    private static void emit(VertexConsumer vc, Matrix4f m, float[] q) {
        vc.vertex(m, q[0], q[1], q[2]).color(q[3], q[4], q[5], q[6]).endVertex();
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, int seg, float[] rgb, float a, boolean hard) {
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            v(vc, m, c, rgb, a);
            v(vc, m, c.add(right.scale(Mth.cos(a0) * r)).add(up.scale(Mth.sin(a0) * r)), rgb, hard ? a : 0f);
            v(vc, m, c.add(right.scale(Mth.cos(a1) * r)).add(up.scale(Mth.sin(a1) * r)), rgb, hard ? a : 0f);
            v(vc, m, c, rgb, a);
        }
    }

    private static void spike(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float ang, float len, float w, float[] rgb, float a) {
        Vec3 dir = right.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
        Vec3 side = right.scale(-Mth.sin(ang)).add(up.scale(Mth.cos(ang))).scale(w);
        Vec3 tip = c.add(dir.scale(len));
        v(vc, m, c.subtract(side), rgb, a);
        v(vc, m, tip, rgb, 0f);
        v(vc, m, tip, rgb, 0f);
        v(vc, m, c.add(side), rgb, a);
    }
}
