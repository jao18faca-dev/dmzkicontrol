package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.client.AuraFx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Efeitos no mundo das lutas: Kienzan, Raio da Morte, rajadas de ki, Kamehameha e o cabo de guerra de energia,
 * explosoes, ondas de choque, crateras, imagens residuais do zanzoken, aura da transformacao, linhas de velocidade.
 * Tudo desenhado com brilho (aditivo) e preso aos personagens, entao funciona no chao, voando e de qualquer angulo.
 */
public final class EncFx {
    private static final class Fx {
        final String kind;
        final int src, dst;
        final Vec3 srcSnap, dstSnap;
        final double start;
        final int dur;
        final float size;
        final long seed;

        Fx(String kind, Entity src, Entity dst, int dur, float size, double start) {
            this.kind = kind;
            this.src = src == null ? -1 : src.getId();
            this.dst = dst == null ? -1 : dst.getId();
            this.srcSnap = src == null ? null : src.position();
            this.dstSnap = dst == null ? null : dst.position();
            this.start = start;
            this.dur = Math.max(1, dur);
            this.size = size;
            this.seed = (long) (start * 31) ^ (this.src * 7919L) ^ (this.dst * 104729L);
        }
    }

    private static final List<Fx> FX = new ArrayList<>();

    private EncFx() {}

    public static void clear() {
        FX.clear();
    }

    public static void spawn(String kind, Entity src, Entity dst, int dur, float size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Fx f = new Fx(kind, src, dst, dur, size, mc.level.getGameTime());
        FX.add(f);
        onStart(mc.level, f);
    }

    // ------------------------------------------------------------------ posicoes

    private static Entity ent(int id) {
        Minecraft mc = Minecraft.getInstance();
        return id < 0 || mc.level == null ? null : mc.level.getEntity(id);
    }

    private static Vec3 feet(int id, Vec3 snap, float partial) {
        Entity e = ent(id);
        return e != null ? e.getPosition(partial) : snap;
    }

    private static Vec3 chest(int id, Vec3 snap, float partial) {
        Entity e = ent(id);
        if (e == null) return snap == null ? null : snap.add(0, 1.1, 0);
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.62, 0);
    }

    private static double height(int id) {
        Entity e = ent(id);
        return e == null ? 1.8 : e.getBbHeight();
    }

    /** Direcao "para frente" de um personagem na arena (jogador olha o chefe; chefe olha o grupo). */
    private static Vec3 forward(int id) {
        EncClient.State s = EncClient.state();
        if (s == null) return new Vec3(0, 0, 1);
        return id == s.bossId ? s.dir(0, 0, 1) : s.dir(0, 0, -1);
    }

    private static Vec3 groupChest(float partial) {
        EncClient.State s = EncClient.state();
        if (s == null) return Vec3.ZERO;
        return EncCamera.group(s, partial).add(0, 1.1, 0);
    }

    // ------------------------------------------------------------------ particulas

    private static void onStart(ClientLevel level, Fx f) {
        RandomSource r = RandomSource.create(f.seed);
        Vec3 c = chest(f.src, f.srcSnap, 0);
        if (c == null) return;
        switch (f.kind) {
            case "afterimage" -> {
                for (int i = 0; i < 14; i++) level.addParticle(ParticleTypes.CLOUD, c.x + r.nextGaussian() * 0.3, c.y + r.nextGaussian() * 0.6, c.z + r.nextGaussian() * 0.3, 0, 0.02, 0);
                level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
            }
            case "burst" -> {
                level.addParticle(f.size > 1 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, c.x, c.y, c.z, 0, 0, 0);
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5);
            }
            case "spirit" -> {
                EncClient.State st = EncClient.state();
                if (st != null) {
                    int role = 0;
                    for (int i = 0; i < st.n(); i++) if (st.players[i] == f.src) role = i;
                    EncSpirit.show(st, role, f.dur);
                }
                level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
                for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.END_ROD, c.x + r.nextGaussian() * 0.6, c.y + r.nextGaussian(), c.z + r.nextGaussian() * 0.6, 0, 0.03, 0);
            }
            case "genki_win", "hakai_win" -> {
                level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0, 0, 0);
                for (int i = 0; i < 40; i++) level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, r.nextGaussian() * 0.7, r.nextGaussian() * 0.7, r.nextGaussian() * 0.7);
            }
            case "disintegrate" -> {
                for (int i = 0; i < 40; i++) level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5);
            }
            case "impact_star" -> {
                for (int i = 0; i < 18; i++) level.addParticle(ParticleTypes.CRIT, c.x, c.y, c.z, r.nextGaussian() * 0.8, r.nextGaussian() * 0.8, r.nextGaussian() * 0.8);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.CLOUD, c.x, c.y, c.z, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25);
            }
            case "hit_spark", "deflect_spark" -> {
                for (int i = 0; i < 14; i++) level.addParticle(ParticleTypes.CRIT, c.x, c.y, c.z, r.nextGaussian() * 0.6, r.nextGaussian() * 0.6, r.nextGaussian() * 0.6);
                level.addParticle(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 0, 0, 0);
            }
            case "cut" -> {
                for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.CRIT, c.x, c.y, c.z, r.nextGaussian() * 0.4, r.nextGaussian() * 0.4, r.nextGaussian() * 0.4);
            }
            case "crater" -> crater(level, feet(f.src, f.srcSnap, 0), f.size, r);
            case "shockwave" -> {
                for (int i = 0; i < 30; i++) {
                    double a = i / 30.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.CLOUD, c.x, c.y, c.z, Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6);
                }
            }
            case "aura_burst", "power_burst" -> {
                level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
                for (int i = 0; i < 40; i++) level.addParticle(ParticleTypes.END_ROD, c.x, c.y - 0.6, c.z, r.nextGaussian() * 0.35, r.nextDouble() * 0.6, r.nextGaussian() * 0.35);
                crater(level, feet(f.src, f.srcSnap, 0), 0.6f, r);
            }
            case "blast_behind" -> {
                Vec3 p = c.add(forward(f.src).scale(-3.5));
                level.addParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 0, 0, 0);
            }
            default -> {}
        }
    }

    private static void crater(ClientLevel level, Vec3 at, float size, RandomSource r) {
        if (at == null) return;
        BlockPos.MutableBlockPos m = BlockPos.containing(at).mutable();
        BlockState ground = null;
        for (int i = 0; i < 8 && ground == null; i++) {
            BlockState st = level.getBlockState(m);
            if (!st.isAir()) ground = st;
            else m.move(0, -1, 0);
        }
        double gy = m.getY() + 1.0;
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, at.x, gy, at.z, 0, 0, 0);
        ParticleOptions dust = ground != null ? new BlockParticleOption(ParticleTypes.BLOCK, ground) : ParticleTypes.CLOUD;
        int n = (int) (60 * size);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, sp = 0.3 + r.nextDouble() * 0.6 * size;
            level.addParticle(dust, at.x, gy + 0.1, at.z, Math.cos(a) * sp, 0.2 + r.nextDouble() * 0.6, Math.sin(a) * sp);
        }
        for (int i = 0; i < n / 2; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + Math.cos(a) * 2 * size, gy, at.z + Math.sin(a) * 2 * size, 0, 0.03, 0);
        }
    }

    public static void tick(EncClient.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientLevel level = mc.level;
        double now = level.getGameTime();
        FX.removeIf(f -> now - f.start > f.dur + 30);
        RandomSource r = level.random;
        for (Fx f : FX) {
            double age = now - f.start;
            if (age > f.dur) continue;
            Vec3 c = chest(f.src, f.srcSnap, 0);
            if (c == null) continue;
            switch (f.kind) {
                case "rage" -> {
                    if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + r.nextGaussian() * 0.5, c.y + r.nextGaussian() * 0.7, c.z + r.nextGaussian() * 0.5, 0, 0.1, 0);
                    Vec3 ft = feet(f.src, f.srcSnap, 0);
                    level.addParticle(ParticleTypes.CLOUD, ft.x + r.nextGaussian(), ft.y, ft.z + r.nextGaussian(), 0, 0.05, 0);
                }
                case "charge" -> {
                    Vec3 ft = feet(f.src, f.srcSnap, 0);
                    level.addParticle(ParticleTypes.END_ROD, ft.x + r.nextGaussian() * 0.5, ft.y + r.nextDouble() * 0.5, ft.z + r.nextGaussian() * 0.5, 0, 0.15 + r.nextDouble() * 0.1, 0);
                    if (age % 4 == 0) level.addParticle(ParticleTypes.CLOUD, ft.x + r.nextGaussian() * 1.5, ft.y, ft.z + r.nextGaussian() * 1.5, 0, 0.02, 0);
                }
                case "kame_charge", "boss_charge" -> {
                    if (r.nextInt(3) == 0) {
                        Vec3 p = ballPos(f, 0);
                        double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * Math.PI;
                        Vec3 o = new Vec3(Math.sin(b) * Math.cos(a), Math.cos(b), Math.sin(b) * Math.sin(a)).scale(1.2);
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + o.x, p.y + o.y, p.z + o.z, -o.x * 0.15, -o.y * 0.15, -o.z * 0.15);
                    }
                }
                case "clash" -> {
                    Vec3 p = clashPoint(s, 0);
                    if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, r.nextGaussian() * 0.6, r.nextGaussian() * 0.6, r.nextGaussian() * 0.6);
                    if (age % 9 == 0) level.addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
                    if (age % 3 == 0) level.addParticle(ParticleTypes.CLOUD, p.x, p.y - 2.5, p.z, r.nextGaussian() * 0.3, 0.1, r.nextGaussian() * 0.3);
                }
                case "beam_win" -> {
                    Vec3 b = chest(f.src, f.srcSnap, 0);
                    if (age % 5 == 0) level.addParticle(ParticleTypes.EXPLOSION, b.x + r.nextGaussian(), b.y + r.nextGaussian(), b.z + r.nextGaussian(), 0, 0, 0);
                }
                case "beam_lose" -> {
                    if (age % 5 == 0) {
                        Vec3 g = groupChest(0);
                        level.addParticle(ParticleTypes.EXPLOSION, g.x + r.nextGaussian() * 2, g.y + r.nextGaussian(), g.z + r.nextGaussian() * 2, 0, 0, 0);
                    }
                }
                case "genki", "genki_clash" -> {
                    if (r.nextInt(2) == 0) {
                        Vec3 p = genkiPos(f, s, 0, age / f.dur);
                        double rad = genkiRad(f, age / f.dur);
                        Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(rad * 1.05);
                        level.addParticle(ParticleTypes.END_ROD, p.x + o.x, p.y + o.y, p.z + o.z, o.x * 0.03, o.y * 0.03, o.z * 0.03);
                    }
                    if (f.kind.equals("genki_clash") && age % 4 == 0) {
                        Vec3 p = genkiPos(f, s, 0, 0);
                        level.addParticle(ParticleTypes.CLOUD, p.x + r.nextGaussian() * 2, p.y - 3, p.z + r.nextGaussian() * 2, 0, -0.05, 0);
                    }
                }
                case "hakai", "hakai_clash" -> {
                    if (r.nextInt(2) == 0) {
                        Vec3 p = hakaiPos(f, s, 0, age / f.dur);
                        double rad = hakaiRad(f, age / f.dur);
                        Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(rad);
                        level.addParticle(ParticleTypes.FLAME, p.x + o.x, p.y + o.y, p.z + o.z, o.x * 0.02, o.y * 0.02, o.z * 0.02);
                    }
                }
                case "fist_clash" -> {
                    Vec3 p = clashPoint(s, 0);
                    if (age % 9 == 0) level.addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
                    if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, r.nextGaussian() * 0.8, r.nextGaussian() * 0.8, r.nextGaussian() * 0.8);
                    if (age % 3 == 0) level.addParticle(ParticleTypes.CLOUD, p.x + r.nextGaussian() * 3, p.y - 3, p.z + r.nextGaussian() * 3, r.nextGaussian() * 0.4, 0.05, r.nextGaussian() * 0.4);
                }
                case "sky_explosion" -> {
                    if (age % 6 == 0) {
                        Vec3 p = skyPoint(f, 0);
                        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x + r.nextGaussian() * 3, p.y + r.nextGaussian() * 2, p.z + r.nextGaussian() * 3, 0, 0, 0);
                    }
                }
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ desenho

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) return;
        EncClient.State s = EncClient.state();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || s == null) return;
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        Vec3 L = new Vec3(lv.x(), lv.y(), lv.z()), U = new Vec3(uv.x(), uv.y(), uv.z());
        double now = mc.level.getGameTime() + partial;
        R r = new R(vc, m, cam, L, U);
        for (Fx f : FX) {
            double age = now - f.start;
            if (age < 0 || age > f.dur + 2) continue;
            double u = Mth.clamp(age / f.dur, 0, 1);
            float fade = (float) Math.min(1, Math.min(age / 2.0, (f.dur + 2 - age) / 4.0));
            Vec3 c = chest(f.src, f.srcSnap, partial);
            if (c == null) continue;
            float sz = f.size;
            switch (f.kind) {
                case "kienzan_charge" -> {
                    Vec3 p = feet(f.src, f.srcSnap, partial).add(0, height(f.src) * 1.12 + 0.5 * sz, 0);
                    double rad = 0.9 * sz * Math.min(1, u * 2.5);
                    kienzan(r, p, rad, now, fade);
                }
                case "kienzan", "kienzan_low" -> {
                    Vec3 from = feet(f.src, f.srcSnap, partial).add(0, height(f.src) * 1.12, 0);
                    Vec3 to = f.kind.equals("kienzan_low") ? feet(f.dst, f.dstSnap, partial).add(0, 0.35, 0) : chest(f.dst, f.dstSnap, partial);
                    if (to == null) continue;
                    double k = age / f.dur;
                    Vec3 dir = to.subtract(from);
                    Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
                    Vec3 p = k <= 1 ? from.lerp(to, k).add(side.scale(Math.sin(k * Math.PI) * 1.6)) : to.add(dir.normalize().scale((k - 1) * 14));
                    kienzan(r, p, 0.7 * sz, now, (float) Math.min(1, (f.dur * 1.4 - age) / 4.0));
                }
                case "kienzan_return" -> {
                    Vec3 from = groupChest(partial).add(0, 1.2, 0), to = c;
                    kienzan(r, from.lerp(to, u), 0.8 * sz, now, fade);
                }
                case "deathbeam" -> {
                    Vec3 to = chest(f.dst, f.dstSnap, partial);
                    if (to == null) continue;
                    Vec3 from = c.add(forward(f.src).scale(0.6));
                    float a = fade * (float) (0.8 + 0.2 * Math.sin(now * 3));
                    beam(r, from, to, 0.09 * sz, new float[]{1f, 0.35f, 0.9f}, a * 0.7f);
                    beam(r, from, to, 0.035 * sz, new float[]{1f, 0.9f, 1f}, a);
                    glow(r, from, 0.25, new float[]{1f, 0.4f, 0.95f}, a);
                }
                case "finger_glow" -> glow(r, c.add(forward(f.src).scale(0.6)), 0.12 + 0.05 * Math.sin(now * 2), new float[]{1f, 0.4f, 0.9f}, fade);
                case "kiblast" -> {
                    Vec3 to = chest(f.dst, f.dstSnap, partial);
                    if (to == null) continue;
                    Vec3 from = c.add(forward(f.src).scale(0.8));
                    double k = age / f.dur;
                    Vec3 dir = to.subtract(from);
                    Vec3 p = k <= 1 ? from.lerp(to, k) : to.add(dir.normalize().scale((k - 1) * 18));
                    float a = (float) Math.min(1, (f.dur * 1.4 - age) / 3.0);
                    float[] kc = f.src == s.bossId ? bossColor(s) : new float[]{1f, 0.85f, 0.3f};
                    glow(r, p, 0.45 * sz, kc, a);
                    glow(r, p, 0.22 * sz, new float[]{1f, 1f, 0.9f}, a);
                    streak(r, p, p.subtract(dir.normalize().scale(1.8)), 0.25 * sz, kc, a * 0.6f, 0f);
                }
                case "ki_rain" -> {
                    // chuva de rajadas caindo do ceu em cima do alvo (Ataque de Extincao Humana)
                    Vec3 to = chest(f.dst, f.dstSnap, partial);
                    if (to == null) continue;
                    RandomSource rr = RandomSource.create(f.seed);
                    float[] kc = bossColor(s);
                    for (int i = 0; i < 5; i++) {
                        double delay = i * 0.12, k = (u - delay) / 0.6;
                        if (k < 0 || k > 1.15) continue;
                        Vec3 off = new Vec3((rr.nextDouble() - 0.5) * 2.4, 0, (rr.nextDouble() - 0.5) * 2.4);
                        Vec3 from = to.add(off.scale(3)).add(0, 14, 0), hit = to.add(off);
                        Vec3 p = from.lerp(hit, Math.min(1, k));
                        float a = (float) Math.min(1, (1.15 - k) * 4);
                        glow(r, p, 0.42 * sz, kc, a);
                        glow(r, p, 0.2 * sz, new float[]{1f, 0.95f, 1f}, a);
                        streak(r, p, p.add(from.subtract(hit).normalize().scale(2.4)), 0.22 * sz, kc, a * 0.6f, 0f);
                    }
                }
                case "burst" -> {
                    double rad = (0.5 + 1.6 * easeOut(u)) * sz;
                    glow(r, c, rad, new float[]{1f, 0.9f, 0.6f}, fade * (float) (1 - u));
                    glow(r, c, rad * 0.5, new float[]{1f, 1f, 1f}, fade * (float) (1 - u));
                }
                case "blast_behind" -> {
                    Vec3 p = c.add(forward(f.src).scale(-3.5));
                    double rad = (0.4 + 1.4 * easeOut(u)) * sz;
                    glow(r, p, rad, new float[]{1f, 0.8f, 0.4f}, (float) (1 - u));
                }
                case "spirit" -> {
                    Entity sp = EncSpirit.puppet();
                    if (sp != null) {
                        Vec3 sc = sp.getPosition(partial).add(0, sp.getBbHeight() * 0.55, 0);
                        float pulse = (float) (0.8 + 0.2 * Math.sin(now * 0.9));
                        glow(r, sc, 1.5 * pulse, new float[]{1f, 0.85f, 0.35f}, fade * 0.45f);
                        glow(r, sc, 0.8, new float[]{1f, 1f, 0.85f}, fade * 0.35f);
                    }
                }
                case "disintegrate" -> {
                    // o chefe some dentro do feixe: brilho que cresce e, no fim, o corpo apaga
                    glow(r, c, (1.2 + 4 * easeOut(u)) * sz, new float[]{0.85f, 0.95f, 1f}, fade * 0.8f);
                    glow(r, c, (0.6 + 2 * easeOut(u)) * sz, new float[]{1f, 1f, 1f}, fade);
                    Entity be = Minecraft.getInstance().level.getEntity(f.src);
                    if (be != null && u > 0.55) be.setInvisible(true);
                }
                case "impact_star" -> {
                    // estrela de impacto de manga: estoura grande em 2 ticks, segura e some
                    double pop = u < 0.25 ? 0.55 + 0.75 * easeOut(u / 0.25) : 1.3 - 0.15 * (u - 0.25);
                    float a = (float) (u < 0.6 ? 1 : 1 - (u - 0.6) / 0.4);
                    Vec3 p = c.add(forward(f.src).scale(0.45));
                    spiky(r, p, 0.95 * pop * sz, new float[]{1f, 0.25f, 0.1f}, a, f.seed, 11);
                    spiky(r, p, 0.6 * pop * sz, new float[]{1f, 0.75f, 0.2f}, a, f.seed + 7, 9);
                    glow(r, p, 0.3 * pop * sz, new float[]{1f, 1f, 0.95f}, a);
                }
                case "hit_spark", "deflect_spark" -> {
                    float[] col = f.kind.equals("hit_spark") ? new float[]{1f, 0.95f, 0.7f} : new float[]{1f, 0.5f, 0.95f};
                    star(r, c.add(forward(f.src).scale(f.kind.equals("hit_spark") ? 0.3 : 0.5)), 0.9 * (0.5 + u), col, (float) (1 - u), f.seed);
                    if (f.kind.equals("deflect_spark")) {
                        Vec3 out = c.add(0, 25, 0).add(forward(f.src).scale(-6));
                        beam(r, c, c.lerp(out, Math.min(1, u * 2)), 0.05, new float[]{1f, 0.4f, 0.9f}, (float) (1 - u));
                    }
                }
                case "cut" -> {
                    Vec3 a0 = c.add(L.scale(0.7)).add(U.scale(0.5)), a1 = c.subtract(L.scale(0.7)).subtract(U.scale(0.5));
                    streak(r, a0, a1, 0.05, new float[]{1f, 0.2f, 0.2f}, (float) (1 - u), (float) (1 - u));
                }
                case "afterimage" -> {
                    Vec3 base = f.srcSnap;
                    double h = height(f.src);
                    for (int i = 0; i < 9; i++) {
                        double y = base.y + h * (i + 0.5) / 9.0;
                        Vec3 a0 = new Vec3(base.x, y, base.z).add(L.scale(0.35)), a1 = new Vec3(base.x, y, base.z).subtract(L.scale(0.35));
                        streak(r, a0, a1, 0.06, new float[]{0.85f, 0.9f, 1f}, (float) (1 - u) * 0.8f, (float) (1 - u) * 0.8f);
                    }
                }
                case "telekinesis" -> {
                    Vec3 hand = c.add(0, 0.6, 0).add(forward(f.src).scale(0.5));
                    glow(r, hand, 0.3 + 0.08 * Math.sin(now * 2.2), new float[]{0.8f, 0.3f, 1f}, fade);
                    ring(r, hand, 0.5 + 0.4 * ((now * 0.15) % 1), 0.06, new float[]{0.8f, 0.4f, 1f}, fade * 0.8f, forward(f.src));
                }
                case "sky_explosion" -> {
                    Vec3 p = skyPoint(f, partial);
                    double rad = (2 + 9 * easeOut(Math.min(1, u * 1.6))) * sz;
                    glow(r, p, rad, new float[]{1f, 0.85f, 0.5f}, fade * (float) (1 - u * 0.7));
                    glow(r, p, rad * 0.55, new float[]{1f, 1f, 0.95f}, fade * (float) (1 - u));
                    ring(r, p, rad * 1.3, rad * 0.12, new float[]{1f, 0.9f, 0.7f}, fade * (float) (1 - u), new Vec3(0, 1, 0));
                }
                case "rage" -> {
                    // furia: chamas da aura balancando e faiscas eletricas rente ao corpo
                    Vec3 ft = feet(f.src, f.srcSnap, partial).subtract(r.cam);
                    double h = height(f.src);
                    float[] col = auraOf(f.src, new float[]{1f, 0.85f, 0.3f});
                    AuraFx.flames(r.vc, r.m, ft, h, 1.2 * sz, col, fade * 0.7f, now, f.seed);
                    AuraFx.sparks(r.vc, r.m, ft, h, 1.0 * sz, col, fade, now, f.seed, 8);
                }
                case "charge" -> {
                    // carregando ki: aura calma subindo e um anel leve no chao
                    Vec3 ft = feet(f.src, f.srcSnap, partial);
                    double h = height(f.src);
                    float[] col = auraOf(f.src, new float[]{0.85f, 0.93f, 1f});
                    AuraFx.flames(r.vc, r.m, ft.subtract(r.cam), h, 1.05, col, fade * 0.5f * (float) (0.85 + 0.15 * Math.sin(now * 0.9)), now, f.seed);
                    double rr = (now * 0.06) % 1;
                    ring(r, ft.add(0, 0.05, 0), 0.8 + 1.6 * rr, 0.05, col, fade * 0.45f * (float) (1 - rr), new Vec3(0, 1, 0));
                }
                case "kame_charge", "boss_charge" -> {
                    Vec3 p = ballPos(f, partial);
                    double grow = Math.min(1, u * 1.8);
                    double rad = (0.18 + 0.32 * grow) * sz * (1 + 0.08 * Math.sin(now * 4));
                    float[] col = f.kind.equals("boss_charge") ? new float[]{0.95f, 0.3f, 0.6f} : bossOrBlue(f.src);
                    glow(r, p, rad * 2.2, col, fade * 0.6f);
                    glow(r, p, rad, new float[]{0.9f, 0.97f, 1f}, fade);
                }
                case "clash" -> clash(r, s, partial, now, fade);
                case "beam_win" -> {
                    Vec3 from = groupChest(partial);
                    Vec3 dir = c.subtract(from).normalize();
                    Vec3 to = c.add(dir.scale(30));
                    double w = 1.1 * (1 + 0.1 * Math.sin(now * 3));
                    if (!EncBeams.available()) {
                        beam(r, from, to, w, new float[]{0.35f, 0.75f, 1f}, fade * 0.6f);
                        beam(r, from, to, w * 0.45, new float[]{0.9f, 0.97f, 1f}, fade);
                    }
                    glow(r, c, 2.5 + 3 * easeOut(u), new float[]{0.7f, 0.9f, 1f}, fade * 0.6f);
                }
                case "beam_lose" -> {
                    Vec3 g = groupChest(partial);
                    Vec3 dir = g.subtract(c).normalize();
                    Vec3 to = g.add(dir.scale(25));
                    float[] col = bossColor(s);
                    if (!EncBeams.available()) {
                        beam(r, c, to, 1.4, col, fade * 0.6f);
                        beam(r, c, to, 0.6, new float[]{1f, 0.95f, 1f}, fade);
                    }
                }
                case "speedlines" -> speedlines(r, s, partial, now, fade);
                case "shockwave" -> {
                    double rad = 0.5 + 5 * easeOut(u);
                    ring(r, c, rad, 0.35, new float[]{1f, 1f, 1f}, (float) (1 - u), new Vec3(0, 1, 0));
                    ring(r, c, rad * 0.8, 0.2, new float[]{1f, 0.9f, 0.6f}, (float) (1 - u), s.dir(0, 0, 1));
                }
                case "crater" -> {
                    Vec3 ft = feet(f.src, f.srcSnap, partial);
                    double rad = (1 + 5 * easeOut(u)) * sz;
                    ring(r, ft.add(0, 0.1, 0), rad, 0.5 * sz, new float[]{1f, 0.9f, 0.7f}, (float) (1 - u), new Vec3(0, 1, 0));
                }
                case "aura_burst", "power_burst" -> {
                    // explosao de poder: luz subindo macia, a aura estourando grande e voltando ao tamanho normal,
                    // onda no chao e um clarao curto no peito
                    Vec3 ft = feet(f.src, f.srcSnap, partial);
                    double h = height(f.src);
                    float[] col = auraOf(f.src, f.kind.equals("aura_burst") ? new float[]{1f, 0.88f, 0.35f} : new float[]{0.6f, 0.85f, 1f});
                    float k = (float) (1 - u);
                    AuraFx.column(r.vc, r.m, ft.subtract(r.cam), h * (1.6 + 5 * easeOut(Math.min(1, u * 2))), 0.9 * sz, col, k * 0.6f, now);
                    AuraFx.flames(r.vc, r.m, ft.subtract(r.cam), h, (1.7 - 0.6 * easeOut(u)) * sz, col, k * 0.85f, now, f.seed);
                    ring(r, ft.add(0, 0.08, 0), 0.5 + 5.5 * easeOut(u), 0.18 * (1 - u) + 0.04, col, k * 0.8f, new Vec3(0, 1, 0));
                    ring(r, ft.add(0, 0.08, 0), 0.3 + 4 * easeOut(u), 0.03, new float[]{1f, 1f, 1f}, k, new Vec3(0, 1, 0));
                    if (u < 0.3) glow(r, c, 0.8 + 2 * u, new float[]{1f, 1f, 1f}, (float) (1 - u / 0.3));
                }
                case "genki", "genki_clash", "genki_win" -> {
                    Vec3 p = genkiPos(f, s, partial, u);
                    double rad = genkiRad(f, u);
                    float a = f.kind.equals("genki_win") ? (float) (1 - Math.max(0, u - 0.55) / 0.45) * fade : fade;
                    // halo macio por fora (o corpo da esfera e o modelo do DMZ; sem ele, desenha a nossa)
                    glow(r, p, rad * 1.7, new float[]{0.55f, 0.9f, 1f}, a * 0.32f);
                    if (!EncBeams.available()) {
                        glow(r, p, rad, new float[]{0.75f, 0.97f, 1f}, a * 0.9f);
                        glow(r, p, rad * 0.6, new float[]{1f, 1f, 1f}, a);
                    }
                    if (f.kind.equals("genki")) {
                        // energia de todos os seres vindo de longe ate a esfera
                        RandomSource rr = RandomSource.create(f.seed);
                        for (int i = 0; i < 22; i++) {
                            double th = rr.nextDouble() * Math.PI * 2, ph = 0.2 + rr.nextDouble() * 1.1;
                            Vec3 dir = new Vec3(Math.cos(th) * Math.cos(ph), -Math.sin(ph) * 0.4 + 0.2, Math.sin(th) * Math.cos(ph)).normalize();
                            double k = (now * (0.05 + rr.nextDouble() * 0.03) + rr.nextDouble()) % 1.0;
                            Vec3 head = p.add(dir.scale(rad + (1 - k) * 16)), tail = head.add(dir.scale(2.2));
                            float al = (float) (Math.sin(Math.PI * k)) * a * 0.75f;
                            streak(r, head, tail, 0.07, new float[]{0.7f, 0.95f, 1f}, al, 0f);
                        }
                    } else if (f.kind.equals("genki_clash")) {
                        Entity b = s.boss();
                        if (b != null) {
                            // o Boo segurando a esfera com as maos, ki rosa estalando no contato
                            Vec3 hb = hands(b, p, partial);
                            glow(r, hb, 0.9 + 0.15 * Math.sin(now * 3), bossColor(s), a * 0.7f);
                            bolts(r, p.subtract(0, rad, 0), rad * 2, now, f.seed, new float[]{0.85f, 0.95f, 1f}, a * 0.7f);
                        }
                    } else if (u > 0.4) {
                        double k = (u - 0.4) / 0.6;
                        glow(r, p, rad * (1.2 + 2.5 * easeOut(k)), new float[]{1f, 1f, 1f}, a * (float) (1 - k));
                        ring(r, p, rad * (1.4 + 4 * easeOut(k)), 0.4, new float[]{0.7f, 0.95f, 1f}, a * (float) (1 - k), new Vec3(0, 1, 0));
                    }
                }
                case "hakai", "hakai_clash", "hakai_win" -> {
                    Vec3 p = hakaiPos(f, s, partial, u);
                    double rad = hakaiRad(f, u);
                    float a = f.kind.equals("hakai_win") ? (float) (1 - Math.max(0, u - 0.5) / 0.5) * fade : fade;
                    glow(r, p, rad * 1.8, new float[]{1f, 0.55f, 0.2f}, a * 0.35f);
                    if (!EncBeams.available()) {
                        glow(r, p, rad, new float[]{1f, 0.72f, 0.3f}, a * 0.9f);
                        glow(r, p, rad * 0.55, new float[]{1f, 0.97f, 0.85f}, a);
                    }
                    // estalos vermelhos correndo pela superficie
                    bolts(r, p.subtract(0, rad * 0.9, 0), rad * 1.8, now, f.seed, new float[]{1f, 0.35f, 0.2f}, a * 0.8f);
                    if (f.kind.equals("hakai_win") && u > 0.35) {
                        double k = (u - 0.35) / 0.65;
                        glow(r, p, rad * (1.5 + 4 * easeOut(k)), new float[]{1f, 0.9f, 0.7f}, a * (float) (1 - k));
                        ring(r, p, rad * (1.6 + 6 * easeOut(k)), 0.6, new float[]{1f, 0.6f, 0.25f}, a * (float) (1 - k), new Vec3(0, 1, 0));
                    }
                }
                case "fist_clash" -> {
                    // choque de punhos entre deuses: ondas de choque em pulsos, raios roxos e dourados
                    Vec3 p = clashPoint(s, partial);
                    Vec3 axis = s.dir(0, 0, 1);
                    float flick = (float) (0.85 + 0.15 * Math.sin(now * 4.3));
                    glow(r, p, (1.2 + 0.25 * Math.sin(now * 2.1)) * sz, new float[]{1f, 0.95f, 0.85f}, fade * flick);
                    glow(r, p, 2.6 * sz, auraOf(s.player(0) == null ? -1 : s.player(0).getId(), new float[]{1f, 0.85f, 0.35f}), fade * 0.35f);
                    for (int i = 0; i < 3; i++) {
                        double k = ((now / 9.0) + i / 3.0) % 1.0;
                        ring(r, p, (0.6 + 7 * easeOut(k)) * sz, 0.35 * (1 - k) + 0.05, new float[]{1f, 1f, 1f}, fade * (float) (1 - k) * 0.8f, axis);
                    }
                    bolts(r, p.subtract(0, 1.6, 0), 3.2, now, f.seed, bossColor(s), fade * 0.8f);
                    bolts(r, p.subtract(0, 1.6, 0), 3.2, now + 1, f.seed + 3, new float[]{1f, 0.9f, 0.45f}, fade * 0.8f);
                }
                default -> {}
            }
        }
        bs.endBatch(RenderType.lightning());
    }

    /** Cor da aura de quem e jogador (a do DMZ, da forma atual); o resto usa a cor padrao do efeito. */
    private static float[] auraOf(int id, float[] fallback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(id) instanceof net.minecraft.world.entity.player.Player p) {
            try {
                return com.dmzkicontrol.client.KiFx.rgb(com.dmzkicontrol.client.SealEffects.auraColor(p));
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private static float[] bossColor(EncClient.State s) {
        String id = s == null || s.script == null ? "" : s.script.id;
        if (id.startsWith("frieza")) return new float[]{0.9f, 0.3f, 1f};
        if (id.contains("buu")) return new float[]{1f, 0.42f, 0.78f};
        if (id.startsWith("beerus")) return new float[]{0.72f, 0.42f, 1f};
        if (id.startsWith("vegeta")) return new float[]{0.78f, 0.35f, 1f};
        return new float[]{0.3f, 1f, 0.85f};
    }

    /** Cores do feixe do chefe (modelo do DMZ): Cell = Kamehameha; Freeza e Vegeta = roxo; Boo = rosa; Beerus = laranja. */
    static int[] bossBeam(EncClient.State s) {
        String id = s == null || s.script == null ? "" : s.script.id;
        if (id.startsWith("frieza") || id.startsWith("vegeta")) return EncBeams.PURPLE;
        if (id.contains("buu")) return EncBeams.PINK;
        if (id.startsWith("beerus")) return EncBeams.HAKAI;
        return EncBeams.KAME;
    }

    private static float[] bossOrBlue(int id) {
        EncClient.State s = EncClient.state();
        if (s != null && id == s.bossId) return bossColor(s);
        return new float[]{0.35f, 0.7f, 1f};
    }

    private static Vec3 ballPos(Fx f, float partial) {
        EncClient.State s = EncClient.state();
        Vec3 c = chest(f.src, f.srcSnap, partial);
        if (f.kind.equals("boss_charge")) return feet(f.src, f.srcSnap, partial).add(0, height(f.src) * 1.15 + 0.6, 0);
        if (s == null) return c;
        // maos em concha do lado do quadril (direita de quem segura)
        boolean boss = f.src == s.bossId;
        Vec3 right = boss ? s.dir(-1, 0, 0) : s.dir(1, 0, 0);
        return c.add(0, -0.25, 0).add(right.scale(0.45)).add(forward(f.src).scale(-0.1));
    }

    /** Genki Dama: em cima do grupo (carregando), entre o grupo e o chefe (cabo de guerra) e dentro do chefe (vitoria). */
    private static Vec3 genkiPos(Fx f, EncClient.State s, float partial, double u) {
        Vec3 above = EncCamera.group(s, partial).add(0, 6.0 + 3.5 * f.size, 0).add(s.dir(0, 0, 0.6));
        Entity b = s.boss();
        Vec3 bc = b != null ? b.getPosition(partial).add(0, b.getBbHeight() * 0.7, 0) : s.world(0, s.phase.lift + 1.4, 0);
        Vec3 face = bc.add(s.dir(0, 0, 1).scale(3.0 * f.size + 0.4));
        switch (f.kind) {
            case "genki_clash" -> {
                double pr = Mth.clamp(s.progressShown, 0f, 1f);
                return above.lerp(face, 0.55 + 0.45 * pr);
            }
            case "genki_win" -> {
                return face.lerp(bc, easeOut(Math.min(1, u / 0.45)));
            }
            default -> {
                return above;
            }
        }
    }

    private static double genkiRad(Fx f, double u) {
        return switch (f.kind) {
            case "genki" -> (0.6 + 2.6 * easeOut(Math.min(1, u * 1.2))) * f.size;
            default -> 3.2 * f.size;
        };
    }

    /** Esfera da Destruicao: em cima do Beerus, empurrada pelo grupo, e devolvida a ele na vitoria. */
    private static Vec3 hakaiPos(Fx f, EncClient.State s, float partial, double u) {
        Entity b = s.boss();
        Vec3 bf = b != null ? b.getPosition(partial) : s.world(0, s.phase.lift, 0);
        double h = b != null ? b.getBbHeight() : 2.0;
        Vec3 over = bf.add(0, h + 1.4 + hakaiRad(f, 1) , 0);
        switch (f.kind) {
            case "hakai_clash" -> {
                Vec3 g = groupChest(partial);
                double pr = Mth.clamp(s.progressShown, 0f, 1f);
                // perdendo, a esfera chega perto do grupo; ganhando, volta para o Beerus
                return g.lerp(over, 0.2 + 0.7 * pr);
            }
            case "hakai_win" -> {
                return over.lerp(bf.add(0, h * 0.6, 0), easeOut(Math.min(1, u / 0.35)));
            }
            default -> {
                return over;
            }
        }
    }

    private static double hakaiRad(Fx f, double u) {
        return switch (f.kind) {
            case "hakai" -> (0.4 + 2.4 * easeOut(Math.min(1, u * 1.3))) * f.size;
            default -> 2.8 * f.size;
        };
    }

    private static Vec3 skyPoint(Fx f, float partial) {
        EncClient.State s = EncClient.state();
        Vec3 b = feet(f.src, f.srcSnap, partial);
        return s == null ? b.add(0, 14, 0) : b.add(s.dir(0, 14, 4));
    }

    /**
     * Pedidos dos Kamehamehas do DMZ para este quadro (chamado no estagio AFTER_ENTITIES, antes do desenho
     * dos outros efeitos): cabo de guerra, vitoria, derrota e disparos soltos.
     */
    public static void beamPass(float partial) {
        EncClient.State s = EncClient.state();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null || FX.isEmpty()) return;
        double now = mc.level.getGameTime() + partial;
        for (Fx f : FX) {
            double age = now - f.start;
            if (age < 0 || age > f.dur + 2) continue;
            float grow = (float) Mth.clamp(age / 5.0, 0, 1);
            float shrink = (float) Mth.clamp((f.dur + 2 - age) / 5.0, 0, 1);
            float k = grow * shrink;
            if (k <= 0.02f) continue;
            switch (f.kind) {
                case "clash" -> {
                    Vec3 p = clashPoint(s, partial);
                    float pr = Mth.clamp(s.progressShown, 0f, 1f);
                    // quem esta ganhando tem o feixe mais grosso; o tamanho do efeito (pai e filho) engrossa o do grupo
                    float mine = (0.75f + 0.7f * pr) * f.size * k;
                    float theirs = (1.5f - 0.6f * pr) * k;
                    for (int i = 0; i < s.n(); i++) {
                        Entity e = s.player(i);
                        if (e == null) continue;
                        Vec3 from = hands(e, p, partial);
                        EncBeams.beam("p" + i, from, p, mine / (float) Math.sqrt(s.n()), EncBeams.KAME);
                    }
                    Entity b = s.boss();
                    if (b != null) EncBeams.beam("boss", hands(b, p, partial), p, theirs, bossBeam(s));
                }
                case "beam_win" -> {
                    Vec3 c = chest(f.src, f.srcSnap, partial);
                    if (c == null) continue;
                    for (int i = 0; i < s.n(); i++) {
                        Entity e = s.player(i);
                        if (e == null) continue;
                        Vec3 from = hands(e, c, partial);
                        Vec3 to = c.add(c.subtract(from).normalize().scale(30));
                        EncBeams.beam("p" + i, from, to, 1.6f * f.size * k / (float) Math.sqrt(s.n()), EncBeams.KAME);
                    }
                }
                case "beam_lose" -> {
                    Vec3 c = chest(f.src, f.srcSnap, partial);
                    if (c == null) continue;
                    Vec3 g = groupChest(partial);
                    Entity b = s.boss();
                    Vec3 from = b != null ? hands(b, g, partial) : c;
                    EncBeams.beam("boss", from, g.add(g.subtract(from).normalize().scale(25)), 1.9f * k, bossBeam(s));
                }
                case "genki", "genki_clash", "genki_win" -> {
                    double u = Mth.clamp(age / f.dur, 0, 1);
                    float a = f.kind.equals("genki_win") ? (float) Mth.clamp(1 - (u - 0.55) / 0.45, 0, 1) : 1f;
                    double rad = genkiRad(f, u) * (f.kind.equals("genki_win") ? 1 + 0.6 * Mth.clamp((u - 0.4) / 0.6, 0, 1) : 1);
                    if (a * k > 0.05f) EncBeams.ball("genki", genkiPos(f, s, partial, u), (float) (rad * a * k), EncBeams.GENKI_TYPE, EncBeams.GENKI);
                }
                case "hakai", "hakai_clash", "hakai_win" -> {
                    double u = Mth.clamp(age / f.dur, 0, 1);
                    float a = f.kind.equals("hakai_win") ? (float) Mth.clamp(1 - (u - 0.5) / 0.5, 0, 1) : 1f;
                    Vec3 hp = hakaiPos(f, s, partial, u);
                    EncBeams.ball("hakai", hp, (float) (hakaiRad(f, u) * a * k), EncBeams.LARGE_TYPE, EncBeams.HAKAI);
                    if (f.kind.equals("hakai_clash")) {
                        // o grupo segura a esfera com Kamehamehas
                        float pr = Mth.clamp(s.progressShown, 0f, 1f);
                        for (int i = 0; i < s.n(); i++) {
                            Entity e = s.player(i);
                            if (e == null) continue;
                            Vec3 from = hands(e, hp, partial);
                            Vec3 to = hp.add(from.subtract(hp).normalize().scale(hakaiRad(f, u) * 0.85));
                            EncBeams.beam("p" + i, from, to, (0.8f + 0.6f * pr) * k / (float) Math.sqrt(s.n()), EncBeams.KAME);
                        }
                    }
                }
                case "dmz_beam" -> {
                    // disparo solto: o feixe sai de quem atira e cresce ate o alvo
                    Entity src = mc.level.getEntity(f.src);
                    if (src == null) continue;
                    Vec3 target = f.dst >= 0 && mc.level.getEntity(f.dst) != null ? chest(f.dst, f.dstSnap, partial)
                            : (src == s.boss() ? groupChest(partial) : clashPoint(s, partial));
                    if (target == null) continue;
                    Vec3 from = hands(src, target, partial);
                    Vec3 to = from.lerp(target, Mth.clamp(age / 6.0, 0.05, 1.0));
                    EncBeams.beam("solo" + f.src, from, to, 1.2f * f.size * k, src == s.boss() ? bossBeam(s) : EncBeams.KAME);
                }
                default -> {}
            }
        }
    }

    /** Ponto das maos de quem dispara (na altura do peito, um pouco a frente, virado para o alvo). */
    private static Vec3 hands(Entity e, Vec3 target, float partial) {
        Vec3 base = e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0);
        Vec3 d = target.subtract(base);
        Vec3 h = new Vec3(d.x, 0, d.z);
        if (h.lengthSqr() < 1e-4) return base;
        return base.add(h.normalize().scale(0.55 + e.getBbWidth() * 0.3));
    }

    private static Vec3 clashPoint(EncClient.State s, float partial) {
        Entity b = s.boss();
        Vec3 bc = b != null ? b.getPosition(partial).add(0, b.getBbHeight() * 0.62, 0) : s.world(0, s.phase.lift + 1.2, 0);
        Vec3 g = groupChest(partial);
        double k = 0.1 + 0.8 * Mth.clamp(s.progressShown, 0f, 1f);
        return g.lerp(bc, k);
    }

    private static void clash(R r, EncClient.State s, float partial, double now, float fade) {
        Vec3 p = clashPoint(s, partial);
        float flick = (float) (0.85 + 0.15 * Math.sin(now * 5));
        for (int i = 0; i < s.n(); i++) {
            Entity e = s.player(i);
            if (e == null) continue;
            Vec3 from = e.getPosition(partial).add(0, e.getBbHeight() * 0.62, 0).add(s.dir(0, 0, -0.6));
            if (EncBeams.available()) continue;
            beam(r, from, p, 0.42, new float[]{0.3f, 0.65f, 1f}, fade * 0.55f * flick);
            beam(r, from, p, 0.18, new float[]{0.9f, 0.97f, 1f}, fade * flick);
        }
        Entity b = s.boss();
        if (b != null) {
            Vec3 from = b.getPosition(partial).add(0, b.getBbHeight() * 0.62, 0).add(s.dir(0, 0, 0.7));
            float[] col = bossColor(s);
            if (!EncBeams.available()) {
                beam(r, from, p, 0.5, col, fade * 0.55f * flick);
                beam(r, from, p, 0.2, new float[]{1f, 0.95f, 1f}, fade * flick);
            }
        }
        double rad = 1.4 + 0.3 * Math.sin(now * 2.3) + 0.4 * (s.n() - 1) * 0.3;
        glow(r, p, rad * 1.8, new float[]{0.8f, 0.75f, 1f}, fade * 0.45f);
        glow(r, p, rad, new float[]{1f, 1f, 1f}, fade);
        bolts(r, p.subtract(0, rad, 0), rad * 2, now, 99, new float[]{0.95f, 0.9f, 1f}, fade * 0.8f);
    }

    private static void speedlines(R r, EncClient.State s, float partial, double now, float fade) {
        Vec3 g = EncCamera.group(s, partial);
        Vec3 back = s.dir(0, 0, 1);
        RandomSource rand = RandomSource.create(7);
        for (int i = 0; i < 46; i++) {
            double ox = (rand.nextDouble() - 0.5) * 12, oy = (rand.nextDouble() - 0.2) * 5;
            double phase = (rand.nextDouble() * 30 + now * 2.2) % 30 - 15;
            Vec3 a = g.add(s.dir(ox, oy, phase));
            streak(r, a, a.add(back.scale(2.5 + rand.nextDouble() * 2)), 0.03, new float[]{1f, 1f, 1f}, fade * 0.5f, 0f);
        }
    }

    // ------------------------------------------------------------------ primitivas

    private record R(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 L, Vec3 U) {}

    private static double easeOut(double u) {
        return 1 - (1 - u) * (1 - u);
    }

    private static void v(R r, Vec3 p, float[] c, float a) {
        r.vc.vertex(r.m, (float) (p.x - r.cam.x), (float) (p.y - r.cam.y), (float) (p.z - r.cam.z))
                .color(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f)).endVertex();
    }

    private static void quad(R r, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] c, float a0, float a1, float a2, float a3) {
        v(r, p0, c, a0); v(r, p1, c, a1); v(r, p2, c, a2); v(r, p3, c, a3);
        v(r, p3, c, a3); v(r, p2, c, a2); v(r, p1, c, a1); v(r, p0, c, a0);
    }

    /** Bola de luz virada para a camera (centro forte, borda some). */
    private static void glow(R r, Vec3 c, double rad, float[] col, float a) {
        if (a <= 0.01) return;
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 e0 = c.add(r.L.scale(Math.cos(a0) * rad)).add(r.U.scale(Math.sin(a0) * rad));
            Vec3 e1 = c.add(r.L.scale(Math.cos(a1) * rad)).add(r.U.scale(Math.sin(a1) * rad));
            quad(r, c, c, e1, e0, col, a, a, 0f, 0f);
        }
    }

    /** Anel num plano com a normal dada. */
    private static void ring(R r, Vec3 c, double rad, double w, float[] col, float a, Vec3 normal) {
        if (a <= 0.01) return;
        Vec3 n = normal.normalize();
        Vec3 t1 = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 bx = n.cross(t1).normalize(), by = n.cross(bx).normalize();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 d0 = bx.scale(Math.cos(a0)).add(by.scale(Math.sin(a0))), d1 = bx.scale(Math.cos(a1)).add(by.scale(Math.sin(a1)));
            Vec3 i0 = c.add(d0.scale(Math.max(0, rad - w))), i1 = c.add(d1.scale(Math.max(0, rad - w)));
            Vec3 m0 = c.add(d0.scale(rad)), m1 = c.add(d1.scale(rad));
            Vec3 o0 = c.add(d0.scale(rad + w)), o1 = c.add(d1.scale(rad + w));
            quad(r, i0, m0, m1, i1, col, 0f, a, a, 0f);
            quad(r, m0, o0, o1, m1, col, a, 0f, 0f, a);
        }
    }

    /** Feixe (cilindro com bordas suaves) de a ate b. */
    private static void beam(R r, Vec3 a, Vec3 b, double w, float[] col, float al) {
        if (al <= 0.01) return;
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 n = d.normalize();
        Vec3 t1 = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 bx = n.cross(t1).normalize(), by = n.cross(bx).normalize();
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 o0 = bx.scale(Math.cos(a0) * w).add(by.scale(Math.sin(a0) * w)), o1 = bx.scale(Math.cos(a1) * w).add(by.scale(Math.sin(a1) * w));
            quad(r, a.add(o0), a.add(o1), b.add(o1), b.add(o0), col, al * 0.55f, al * 0.55f, al * 0.55f, al * 0.55f);
        }
        glow(r, b, w * 1.6, col, al * 0.7f);
    }

    /** Fita virada para a camera. */
    private static void streak(R r, Vec3 a, Vec3 b, double w, float[] col, float a0, float a1) {
        Vec3 dir = b.subtract(a);
        Vec3 view = a.subtract(r.cam);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w);
        quad(r, a.add(side), a.subtract(side), b.subtract(side), b.add(side), col, a0, a0, a1, a1);
    }

    /** Estrela cheia de pontas irregulares, virada para a camera. */
    private static void spiky(R r, Vec3 c, double rad, float[] col, float a, long seed, int spikes) {
        if (a <= 0.01) return;
        RandomSource rand = RandomSource.create(seed);
        int n = spikes * 2;
        double rot = rand.nextDouble() * Math.PI;
        Vec3[] pts = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double ang = rot + i * Math.PI * 2 / n + (rand.nextDouble() - 0.5) * 0.25;
            double rr = i % 2 == 0 ? rad * (0.7 + rand.nextDouble() * 0.6) : rad * 0.38;
            pts[i] = c.add(r.L.scale(Math.cos(ang) * rr)).add(r.U.scale(Math.sin(ang) * rr));
        }
        for (int i = 0; i < n; i++) quad(r, c, c, pts[(i + 1) % n], pts[i], col, a, a, a * 0.9f, a * 0.9f);
    }

    private static void star(R r, Vec3 c, double rad, float[] col, float a, long seed) {
        RandomSource rand = RandomSource.create(seed);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + rand.nextDouble() * 0.3;
            Vec3 tip = c.add(r.L.scale(Math.cos(ang) * rad * (0.6 + rand.nextDouble() * 0.6))).add(r.U.scale(Math.sin(ang) * rad * (0.6 + rand.nextDouble() * 0.6)));
            streak(r, c, tip, 0.07, col, a, 0f);
        }
        glow(r, c, rad * 0.4, new float[]{1f, 1f, 1f}, a);
    }

    private static void pillar(R r, Vec3 base, double rad, double h, float[] col, float a) {
        int seg = 18;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 b0 = base.add(Math.cos(a0) * rad, 0, Math.sin(a0) * rad), b1 = base.add(Math.cos(a1) * rad, 0, Math.sin(a1) * rad);
            quad(r, b0, b1, b1.add(0, h, 0), b0.add(0, h, 0), col, a, a, 0f, 0f);
        }
    }

    private static void bolts(R r, Vec3 feet, double h, double now, long seed, float[] col, float a) {
        RandomSource rand = RandomSource.create(seed + (long) (now / 2));
        for (int b = 0; b < 4; b++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            Vec3 p = feet.add(Math.cos(ang) * 0.6, h * rand.nextDouble() * 0.4, Math.sin(ang) * 0.6);
            for (int k = 0; k < 5; k++) {
                Vec3 q = p.add(rand.nextGaussian() * 0.25, h * 0.18, rand.nextGaussian() * 0.25);
                streak(r, p, q, 0.035, col, a, a);
                p = q;
            }
        }
    }

    /** Disco cortante (Kienzan): anel serrilhado girando, quase horizontal. */
    private static void kienzan(R r, Vec3 c, double rad, double now, float a) {
        if (rad <= 0.02 || a <= 0.01) return;
        float[] gold = {1f, 0.9f, 0.35f}, white = {1f, 1f, 0.9f};
        double spin = now * 1.4;
        int teeth = 16;
        Vec3 bx = new Vec3(1, 0, 0), bz = new Vec3(0, 0.12, 1).normalize();
        for (int i = 0; i < teeth; i++) {
            double a0 = spin + i * Math.PI * 2 / teeth, a1 = spin + (i + 1) * Math.PI * 2 / teeth, am = (a0 + a1) / 2;
            Vec3 p0 = c.add(bx.scale(Math.cos(a0) * rad)).add(bz.scale(Math.sin(a0) * rad));
            Vec3 p1 = c.add(bx.scale(Math.cos(a1) * rad)).add(bz.scale(Math.sin(a1) * rad));
            Vec3 tip = c.add(bx.scale(Math.cos(am) * rad * 1.18)).add(bz.scale(Math.sin(am) * rad * 1.18));
            quad(r, c, p0, tip, p1, gold, a * 0.35f, a * 0.8f, a, a * 0.8f);
        }
        ring(r, c, rad * 0.95, rad * 0.08, white, a, bz.cross(bx));
    }
}
