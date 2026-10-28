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
    /** Invocacao do arco: o ki junta na mao e os bracos crescem para fora. */
    private static final float SUMMON_TICKS = 8f;
    private static final String CAST_ANIM = "ki.kibow_cast";
    /** Puxada da corda depois da invocacao (bate com a animacao: 0,45 s a 1,3 s). */
    private static final float PULL_START = 9f;
    private static final float PULL_TICKS = 17f;
    /** donos que ja tocaram o som/particulas da invocacao nesta carga. */
    private static final java.util.Set<Integer> SUMMONED = new java.util.HashSet<>();
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
        if (mc.level == null || mc.player == null) { RELEASED.clear(); SUMMONED.clear(); return; }
        long now = mc.level.getGameTime();
        RELEASED.entrySet().removeIf(en -> now - en.getValue() > RELEASE_TICKS || now < en.getValue());
        for (AbstractKiProjectile p : mc.level.getEntitiesOfClass(AbstractKiProjectile.class, mc.player.getBoundingBox().inflate(128),
                e -> KiArrowTechnique.ID.equals(e.getTechniqueId()) && !e.isFiring())) {
            // logo depois de soltar a bola de carga ainda pode existir por um tick: nao conta como nova carga
            if (p.getOwner() != null && !RELEASED.containsKey(p.getOwner().getId())) CHARGING.put(p.getOwner().getId(), p.getId());
        }
        SUMMONED.retainAll(CHARGING.keySet());
        // garante a pose de puxar o arco (o DMZ manda a animacao so no inicio da carga; se ela se perder,
        // por exemplo por um pacote que chegou antes do jogador, o arco ficaria com o braco abaixado)
        for (Integer ownerId : CHARGING.keySet()) {
            if (mc.level.getEntity(ownerId) instanceof net.minecraft.client.player.AbstractClientPlayer pl
                    && ((Object) pl) instanceof com.dragonminez.client.animation.IPlayerAnimatable anim) {
                try {
                    if (!CAST_ANIM.equals(anim.dragonminez$getCurrentKiAnimation()) && SealAnimations.ensureInjected()) {
                        anim.dragonminez$playKiAnimation(CAST_ANIM, true);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        for (Map.Entry<Integer, Integer> en : CHARGING.entrySet()) {
            Entity owner = mc.level.getEntity(en.getKey());
            Entity charge = mc.level.getEntity(en.getValue());
            if (owner instanceof LivingEntity le && charge != null) summonFx(mc, le, charge.tickCount);
        }
    }

    /** {puxada 0..1, escala 0..1, invocacao 0..1} ou null se essa entidade nao esta com o arco. */
    public static float[] state(Entity owner, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Integer id = CHARGING.get(owner.getId());
        if (id != null && mc.level.getEntity(id) instanceof AbstractKiProjectile p && !p.isRemoved()) {
            float age = p.tickCount + partial;
            return new float[]{Mth.clamp((age - PULL_START) / PULL_TICKS, 0f, 1f), 1f, Mth.clamp(age / SUMMON_TICKS, 0f, 1f)};
        }
        Long t = RELEASED.get(owner.getId());
        if (t != null) {
            float k = 1f - Mth.clamp((mc.level.getGameTime() - t + partial) / RELEASE_TICKS, 0f, 1f);
            if (k > 0.02f) return new float[]{0f, k, 1f};
        }
        return null;
    }

    /** Som e faiscas de ki convergindo para a mao enquanto o arco se forma. */
    private static void summonFx(Minecraft mc, LivingEntity p, int age) {
        if (age > SUMMON_TICKS + 2) return;
        Vec3 look = p.getViewVector(1f).normalize();
        double yaw = Math.toRadians(p.getViewYRot(1f));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        double sc = p.getBbHeight() / 1.8;
        Vec3 hand = p.position().add(0, p.getBbHeight() * 0.74, 0).add(look.scale(0.62 * sc)).add(left.scale(0.24 * sc));
        float[] c = KiFx.kiColor(p, FALLBACK);
        var rnd = p.getRandom();
        for (int i = 0; i < 5; i++) {
            Vec3 off = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().scale(0.8 + rnd.nextDouble() * 0.6);
            Vec3 from = hand.add(off);
            Vec3 v = off.scale(-0.22);
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
            mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(c[0], c[1], c[2]), 1.2f),
                    from.x, from.y, from.z, v.x, v.y, v.z);
        }
        if (SUMMONED.add(p.getId())) {
            mc.level.playLocalSound(hand.x, hand.y, hand.z, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.9f, false);
            mc.level.playLocalSound(hand.x, hand.y, hand.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.4f, false);
        }
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
        drawBow(pen(bs, event.getPoseStack().last().pose()), grip, look, up, Vec3.ZERO, 0.62f * st[1], st[0], st[2], null,
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
    public static void drawBow(Pen pen, Vec3 grip, Vec3 fwd, Vec3 up, Vec3 cam, float s, float draw, float summon, Vec3 nockAt, float[] c, float seed) {
        float pulse = 1f + 0.2f * draw;
        double height = 1.0 * s;

        // invocacao: primeiro uma explosao de ki na mao, depois os bracos crescem ate as pontas
        if (summon < 1f) {
            float flare = (float) Math.sin(Math.min(1f, summon * 1.25f) * Math.PI);
            pen.blade(cam, grip.subtract(fwd.scale(0.08 * s)), grip.add(fwd.scale(0.5 * s)), (0.25f + 0.55f * flare) * s, c, seed + 0.4f);
        }
        float grow = Mth.clamp((summon - 0.2f) / 0.55f, 0f, 1f);
        grow = 1f - (1f - grow) * (1f - grow) * (1f - grow);
        if (grow <= 0f) return;

        int seg = 6;
        double reach = grow; // ate onde o braco ja cresceu (0..1)
        for (int side = -1; side <= 1; side += 2) {
            Vec3 prev = limb(grip, up, fwd, side, 0.0, height, s);
            for (int i = 1; i <= seg; i++) {
                double t0 = (i - 1) / (double) seg, t = i / (double) seg;
                if (t0 >= reach) break;
                boolean last = t >= reach || i == seg;
                double tt = Math.min(t, reach);
                Vec3 pt = limb(grip, up, fwd, side, tt, height, s);
                if (!last) pen.rod(cam, prev, pt, KiFx.ROD_ELBOW, (1.15f - 0.4f * (float) t) * s * pulse, c, seed);
                else pen.rod(cam, prev, pt, KiFx.ROD_TINE, 0.95f * s * pulse, c, seed);
                prev = pt;
            }
        }
        // empunhadura mais grossa
        pen.rod(cam, grip.subtract(up.scale(0.2 * s * grow)), grip.add(up.scale(0.2 * s * grow)), KiFx.ROD_ELBOW, 1.6f * s * pulse, c, seed);
        if (grow < 1f) return;
        float string = Mth.clamp((summon - 0.75f) / 0.25f, 0f, 1f);
        if (string <= 0f) return;

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
        pen.rod(cam, nock, top, KiFx.ROD_SHAFT, 0.26f * s * string, c, seed);
        pen.rod(cam, nock, bottom, KiFx.ROD_SHAFT, 0.26f * s * string, c, seed);

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
