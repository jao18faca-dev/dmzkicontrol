package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.KiArrowTechnique;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * O Arco de Ki do Granolah: dois bracos recurvos de energia (shader das armas de ki do DMZ, na cor do
 * ki do usuario), corda que acompanha a mao que puxa e uma flecha de ki encaixada que cresce com a
 * carga. Em terceira pessoa ele e desenhado pelo {@link KiBowLayer} (preso a mao); aqui fica o estado
 * de quem esta carregando e o arco da primeira pessoa.
 */
public final class KiBowRenderer {
    public static final int FALLBACK = 0x5FD3C4;
    /** Bate com a animacao de puxar (1,3 s). */
    private static final float CHARGE_TICKS = 26f;
    private static final float RELEASE_TICKS = 6f;
    /** dono -> entidade de carga do DMZ. */
    private static final Map<Integer, Integer> CHARGING = new HashMap<>();
    /** dono -> tick em que soltou. */
    private static final Map<Integer, Long> RELEASED = new HashMap<>();

    private KiBowRenderer() {}

    public static void onRelease(Entity owner) {
        if (owner.level() != null) RELEASED.put(owner.getId(), owner.level().getGameTime());
        CHARGING.remove(owner.getId());
    }

    /** A cada tick: quem esta segurando o Arco de Ki (pela bola de carga do DMZ, que fica escondida). */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        CHARGING.clear();
        if (mc.level == null || mc.player == null) { RELEASED.clear(); return; }
        for (AbstractKiProjectile p : mc.level.getEntitiesOfClass(AbstractKiProjectile.class, mc.player.getBoundingBox().inflate(128),
                e -> KiArrowTechnique.ID.equals(e.getTechniqueId()) && !e.isFiring())) {
            if (p.getOwner() != null) CHARGING.put(p.getOwner().getId(), p.getId());
        }
        long now = mc.level.getGameTime();
        RELEASED.entrySet().removeIf(en -> now - en.getValue() > RELEASE_TICKS);
    }

    /** {puxada 0..1, escala 0..1} ou null se essa entidade nao esta com o arco. */
    public static float[] state(Entity owner, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Integer id = CHARGING.get(owner.getId());
        if (id != null && mc.level.getEntity(id) instanceof AbstractKiProjectile p && !p.isRemoved()) {
            return new float[]{Math.min(1f, (p.tickCount + partial) / CHARGE_TICKS), 1f};
        }
        Long t = RELEASED.get(owner.getId());
        if (t != null) {
            float k = 1f - Mth.clamp((mc.level.getGameTime() - t + partial) / RELEASE_TICKS, 0f, 1f);
            if (k > 0.02f) return new float[]{0f, k};
        }
        return null;
    }

    /** Primeira pessoa: o arco fica na frente da camera, na mao esquerda. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof LivingEntity p) || !mc.options.getCameraType().isFirstPerson()) return;
        float partial = event.getPartialTick();
        float[] st = state(p, partial);
        if (st == null) return;
        Vec3 look = p.getViewVector(partial).normalize();
        double yaw = Math.toRadians(p.getViewYRot(partial));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = look.cross(left).normalize();
        Vec3 camWorld = event.getCamera().getPosition();
        Vec3 grip = p.getEyePosition(partial).add(look.scale(1.05)).add(left.scale(0.34)).subtract(up.scale(0.12)).subtract(camWorld);

        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        drawBow(pen(bs, event.getPoseStack().last().pose()), grip, look, up, Vec3.ZERO, 0.62f * st[1], st[0], null,
                KiFx.kiColor(p, FALLBACK), KiFx.seed(p));
        bs.endBatch(KiFx.available() ? KiFx.type() : RenderType.lightning());
    }

    // ------------------------------------------------------------------ geometria

    /**
     * @param grip   centro da pegada (mao esquerda)
     * @param fwd    para onde a flecha aponta (unitario)
     * @param up     eixo dos bracos (unitario, perpendicular a fwd)
     * @param cam    posicao da camera no mesmo espaco
     * @param nockAt mao que puxa a corda (ou null para calcular pela carga)
     */
    public static void drawBow(Pen pen, Vec3 grip, Vec3 fwd, Vec3 up, Vec3 cam, float s, float draw, Vec3 nockAt, float[] c, float seed) {
        float pulse = 1f + 0.2f * draw;
        double height = 1.0 * s;
        int seg = 6;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 prev = limb(grip, up, fwd, side, 0.0, height, s);
            for (int i = 1; i <= seg; i++) {
                double t = i / (double) seg;
                Vec3 pt = limb(grip, up, fwd, side, t, height, s);
                if (i < seg) pen.rod(cam, prev, pt, KiFx.ROD_ELBOW, (1.15f - 0.4f * (float) t) * s * pulse, c, seed);
                else pen.rod(cam, prev, pt, KiFx.ROD_TINE, 0.95f * s * pulse, c, seed);
                prev = pt;
            }
        }
        // empunhadura mais grossa e um brilho de ki na mao
        pen.rod(cam, grip.subtract(up.scale(0.2 * s)), grip.add(up.scale(0.2 * s)), KiFx.ROD_ELBOW, 1.6f * s * pulse, c, seed);

        // corda
        Vec3 top = limb(grip, up, fwd, 1, 1.0, height, s);
        Vec3 bottom = limb(grip, up, fwd, -1, 1.0, height, s);
        double rest = depth(1.0) * s;
        Vec3 nockRest = grip.subtract(fwd.scale(rest));
        float ease = 1f - (1f - draw) * (1f - draw);
        Vec3 nock;
        if (nockAt != null) {
            nock = nockRest.add(nockAt.subtract(nockRest).scale(Mth.clamp(draw * 5f, 0f, 1f)));
        } else {
            nock = grip.subtract(fwd.scale(rest + 0.62 * s * ease));
        }
        // a corda vibra um pouco quando esta toda puxada
        if (draw >= 1f) nock = nock.add(up.scale(Math.sin(System.nanoTime() / 2.5e7) * 0.006 * s));
        pen.rod(cam, nock, top, KiFx.ROD_SHAFT, 0.26f * s, c, seed);
        pen.rod(cam, nock, bottom, KiFx.ROD_SHAFT, 0.26f * s, c, seed);

        // flecha de ki encaixada: aparece logo e cresce com a carga
        if (draw > 0.05f && s > 0.3f) {
            float g = 0.55f + 0.65f * ease;
            Vec3 tip = grip.add(fwd.scale((0.5 + 0.3 * ease) * s));
            double len = tip.subtract(nock).length();
            pen.blade(cam, nock, tip, (float) Math.min(0.48 * s * g, len * 0.33), c, seed + 0.21f);
            Vec3 bs = KiFx.side(nock, tip, cam);
            Vec3 back = tip.subtract(fwd.scale(0.3 * s * g));
            pen.rod(cam, tip, back.add(bs.scale(0.2 * s * g)), KiFx.ROD_TINE, 0.8f * s * g, c, seed);
            pen.rod(cam, tip, back.subtract(bs.scale(0.2 * s * g)), KiFx.ROD_TINE, 0.8f * s * g, c, seed);
        }
    }

    /** Recuo do braco (para tras) em funcao de t (0 = pegada, 1 = ponta): curva de arco recurvo. */
    private static double depth(double t) {
        double tip = Math.max(0.0, t - 0.74);
        return 0.42 * t * t - 2.6 * tip * tip;
    }

    private static Vec3 limb(Vec3 grip, Vec3 up, Vec3 fwd, int side, double t, double height, float s) {
        return grip.add(up.scale(side * height * t)).subtract(fwd.scale(depth(t) * s));
    }

    // ------------------------------------------------------------------ "canetas" (shader do DMZ ou fita simples)

    public static Pen pen(MultiBufferSource buffers, Matrix4f m) {
        return KiFx.available() ? new ShaderPen(buffers.getBuffer(KiFx.type()), m) : new GlowPen(buffers.getBuffer(RenderType.lightning()), m);
    }

    public interface Pen {
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
