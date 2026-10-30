package com.dmzkicontrol.client.scene;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Efeitos no mundo das cutscenes de transformacao, desenhados em volta de quem se transforma e vistos
 * por todos por perto (quads dos dois lados: de frente e de costas). Coisas de chao (pedras, cratera)
 * so aparecem se houver chao perto; voando, a cena usa so os efeitos de ar.
 */
public final class SceneFx {
    /** Pedras que sobem do chao (geradas no inicio do efeito). */
    private record Rock(BlockState state, double ang, double rad, double size, double spin, double lift, double phase) {}
    private record RockSet(int t, int d, double groundY, List<Rock> rocks, double rise, double orbit) {}

    private static final Map<Integer, List<RockSet>> ROCKS = new HashMap<>();

    private SceneFx() {}

    static void onStart(ScenePlayback.Play p) {
        ROCKS.remove(p.entityId);
    }

    static void onEnd(ScenePlayback.Play p) {
        ROCKS.remove(p.entityId);
    }

    static void clear() {
        ROCKS.clear();
    }

    // ------------------------------------------------------------------ eventos de inicio (cliente)

    static void onFxStart(ScenePlayback.Play p, Entity e, Scene.Fx f) {
        Level level = e.level();
        RandomSource r = RandomSource.create(p.seed + f.t() * 977L);
        double unit = unit(e);
        switch (f.kind()) {
            case "rocks" -> {
                BlockPos ground = ground(e);
                if (ground == null) return;
                BlockState st = level.getBlockState(ground);
                if (st.isAir() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) st = Blocks.STONE.defaultBlockState();
                int n = (int) Scene.num(f.p(), "n", 10);
                double rad = Scene.num(f.p(), "r", 2.5) * unit;
                List<Rock> rocks = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    BlockState s = st;
                    // mistura um pouco com o bloco de baixo
                    if (i % 3 == 1) {
                        BlockState below = level.getBlockState(ground.below());
                        if (!below.isAir() && below.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) s = below;
                    }
                    rocks.add(new Rock(s, r.nextDouble() * Math.PI * 2, rad * (0.45 + r.nextDouble() * 0.7), (0.18 + r.nextDouble() * 0.32) * Math.min(2.5, unit),
                            (r.nextDouble() - 0.5) * 8, 0.5 + r.nextDouble(), r.nextDouble() * 6));
                }
                ROCKS.computeIfAbsent(p.entityId, k -> new ArrayList<>()).add(new RockSet(f.t(), f.d(), ground.getY() + 1.0, rocks,
                        Scene.num(f.p(), "rise", 1.6) * unit, Scene.num(f.p(), "orbit", 0.0)));
            }
            case "crater" -> {
                BlockPos ground = ground(e);
                int n = (int) Scene.num(f.p(), "n", 40);
                double rad = Scene.num(f.p(), "r", 2.5) * unit;
                if (ground != null) {
                    BlockState st = level.getBlockState(ground);
                    double gy = ground.getY() + 1.05;
                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * rad;
                        double x = e.getX() + Math.cos(a) * rr, z = e.getZ() + Math.sin(a) * rr;
                        double vx = Math.cos(a) * (0.15 + r.nextDouble() * 0.35), vz = Math.sin(a) * (0.15 + r.nextDouble() * 0.35);
                        if (!st.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, gy, z, vx, 0.25 + r.nextDouble() * 0.4, vz);
                        if (i % 2 == 0) level.addParticle(ParticleTypes.CLOUD, x, gy, z, vx * 1.6, 0.02, vz * 1.6);
                    }
                } else {
                    // no ar: anel de poeira de energia
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        level.addParticle(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45);
                    }
                }
            }
            case "burst" -> {
                ParticleOptions po = particle(f.p(), r);
                if (po == null) return;
                int n = (int) Scene.num(f.p(), "n", 30);
                double spd = Scene.num(f.p(), "spd", 0.4);
                double cy = e.getY() + e.getBbHeight() * Scene.num(f.p(), "y", 0.55);
                for (int i = 0; i < n; i++) {
                    Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize().scale(spd * (0.5 + r.nextDouble()));
                    level.addParticle(po, e.getX(), cy, e.getZ(), v.x, v.y, v.z);
                }
            }
            default -> {}
        }
    }

    /** Efeitos de particulas continuas (a cada tick). */
    static void tick(ScenePlayback.Play p, Entity e, int now) {
        Level level = e.level();
        double unit = unit(e);
        for (Scene.Fx f : p.scene.fx) {
            if (now < f.t() || now > f.t() + f.d()) continue;
            RandomSource r = RandomSource.create(p.seed + now * 131L + f.t());
            switch (f.kind()) {
                case "particles" -> {
                    ParticleOptions po = particle(f.p(), r);
                    if (po == null) continue;
                    int n = (int) Scene.num(f.p(), "n", 3);
                    double rad = Scene.num(f.p(), "r", 0.8) * unit;
                    double up = Scene.num(f.p(), "up", 0.08);
                    double in = Scene.num(f.p(), "in", 0.0);
                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * Math.PI * 2, rr = rad * Math.sqrt(r.nextDouble());
                        double x = e.getX() + Math.cos(a) * rr, z = e.getZ() + Math.sin(a) * rr;
                        double y = e.getY() + r.nextDouble() * e.getBbHeight() * Scene.num(f.p(), "hgt", 1.0);
                        level.addParticle(po, x, y, z, -Math.cos(a) * in, up * (0.5 + r.nextDouble()), -Math.sin(a) * in);
                    }
                }
                case "darkness" -> {
                    int n = (int) Scene.num(f.p(), "n", 4);
                    double rad = Scene.num(f.p(), "r", 2.0) * unit;
                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double x = e.getX() + Math.cos(a) * rad, z = e.getZ() + Math.sin(a) * rad;
                        double y = e.getY() + r.nextDouble() * e.getBbHeight();
                        level.addParticle(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, x, y, z,
                                -Math.cos(a) * 0.12, 0.02, -Math.sin(a) * 0.12);
                    }
                }
                default -> {}
            }
        }
    }

    private static ParticleOptions particle(JsonObject p, RandomSource r) {
        String id = Scene.str(p, "p", "minecraft:end_rod");
        if (id.equals("dust")) {
            float[] c = Scene.color(p, "c", 0xFFFFFF);
            return new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), (float) Scene.num(p, "size", 1.2));
        }
        ParticleType<?> t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(id));
        return t instanceof SimpleParticleType s ? s : null;
    }

    private static double unit(Entity e) {
        return Math.max(1.0, e.getBbHeight() / 1.8);
    }

    private static BlockPos ground(Entity e) {
        BlockPos.MutableBlockPos m = BlockPos.containing(e.getX(), e.getY() + 0.1, e.getZ()).mutable();
        for (int i = 0; i < 7; i++) {
            m.move(0, -1, 0);
            BlockState s = e.level().getBlockState(m);
            if (!s.isAir() && s.isSolidRender(e.level(), m)) return m.immutable();
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Map<Integer, ScenePlayback.Play> plays = ScenePlayback.plays();
        if (plays.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        Vector3f left = camera.getLeftVector(), upv = camera.getUpVector();
        Vec3 L = new Vec3(left.x(), left.y(), left.z()), U = new Vec3(upv.x(), upv.y(), upv.z());
        boolean drewRocks = false;
        for (ScenePlayback.Play p : plays.values()) {
            Entity e = mc.level.getEntity(p.entityId);
            if (e == null) continue;
            double t = p.time(partial);
            Vec3 feet = e.getPosition(partial).subtract(cam);
            double h = e.getBbHeight(), unit = unit(e);
            for (int i = 0; i < p.scene.fx.size(); i++) {
                Scene.Fx f = p.scene.fx.get(i);
                if (t < f.t() || t > f.t() + f.d()) continue;
                double u = (t - f.t()) / Math.max(1, f.d());
                float env = (float) (Math.min(1.0, u / 0.12) * Math.min(1.0, (1.0 - u) / 0.25));
                JsonObject q = f.p();
                float[] c = Scene.color(q, "c", 0xFFFFFF);
                float a = (float) Scene.num(q, "a", 0.8) * env;
                RandomSource r = RandomSource.create(p.seed + i * 7919L);
                switch (f.kind()) {
                    case "ring" -> {
                        double rad = Mth.lerp(easeOut(u), Scene.num(q, "r0", 0.3), Scene.num(q, "r1", 4.0)) * unit;
                        double w = Scene.num(q, "w", 0.35) * unit;
                        ring(vc, m, feet.add(0, h * Scene.num(q, "y", 0.05), 0), rad, w, c, a * (float) (1.0 - u * 0.6), Scene.num(q, "tilt", 0));
                    }
                    case "pillar" -> {
                        double grow = Math.min(1.0, u / 0.2);
                        double rad = Scene.num(q, "r", 0.9) * unit * (1.0 + 0.06 * Math.sin(t * 1.7));
                        double hh = Scene.num(q, "h", 10) * unit * grow;
                        cylinder(vc, m, feet, rad, hh, c, a * 0.55f);
                        cylinder(vc, m, feet, rad * 0.45, hh, new float[]{Math.min(1, c[0] + 0.4f), Math.min(1, c[1] + 0.4f), Math.min(1, c[2] + 0.4f)}, a * 0.8f);
                    }
                    case "beam" -> {
                        double grow = Math.min(1.0, u / 0.15);
                        cylinder(vc, m, feet.add(0, h * 0.9, 0), Scene.num(q, "r", 0.12) * unit, Scene.num(q, "h", 60) * unit * grow, c, a);
                    }
                    case "glow" -> {
                        double rad = Scene.num(q, "r", 1.2) * unit * (1.0 + Scene.num(q, "pulse", 0.08) * Math.sin(t * 0.9));
                        disc(vc, m, feet.add(0, h * Scene.num(q, "y", 0.55), 0), L, U, rad, c, a * 0.6f);
                    }
                    case "bolts" -> bolts(vc, m, feet, cam, h, unit, q, c, a, p.seed + i, t);
                    case "halo" -> ring(vc, m, feet.add(0, h + 0.18 * unit, 0), 0.3 * unit, 0.05 * unit, c, a, 0);
                    case "orbs" -> {
                        int n = (int) Scene.num(q, "n", 6);
                        double rad = Scene.num(q, "r", 1.2) * unit, spd = Scene.num(q, "spd", 0.12);
                        for (int k = 0; k < n; k++) {
                            double ang = k * Math.PI * 2 / n + t * spd;
                            double y = h * (0.3 + 0.5 * ((k * 0.618) % 1.0)) + Math.sin(t * 0.2 + k) * 0.15 * unit;
                            disc(vc, m, feet.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad), L, U, Scene.num(q, "s", 0.14) * unit, c, a);
                        }
                    }
                    case "spiral" -> spiral(vc, m, feet, cam, h, unit, q, c, a, t);
                    case "dome" -> {
                        double rad = Mth.lerp(easeOut(u), 0.2, Scene.num(q, "r1", 5.0)) * unit;
                        for (int k = 0; k < 6; k++) {
                            double lat = k * (Math.PI / 2) / 6.0;
                            ring(vc, m, feet.add(0, Math.sin(lat) * rad, 0), Math.cos(lat) * rad, 0.12 * unit, c, a * (float) (1.0 - u), 0);
                        }
                    }
                    case "streaks" -> streaks(vc, m, feet, cam, h, unit, q, c, a, t, r);
                    case "shards" -> shards(vc, m, feet, L, U, h, unit, q, c, a, t - f.t(), r);
                    case "rocks" -> drewRocks = true;
                    default -> {}
                }
            }
        }
        bs.endBatch(RenderType.lightning());
        if (drewRocks) renderRocks(event, partial, cam, ps, bs);
    }

    private static double easeOut(double u) {
        u = Mth.clamp(u, 0, 1);
        return 1 - (1 - u) * (1 - u);
    }

    private static void renderRocks(RenderLevelStageEvent event, float partial, Vec3 cam, PoseStack ps, MultiBufferSource.BufferSource bs) {
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getBlockRenderer();
        for (Map.Entry<Integer, List<RockSet>> en : ROCKS.entrySet()) {
            ScenePlayback.Play p = ScenePlayback.plays().get(en.getKey());
            if (p == null || mc.level == null) continue;
            Entity e = mc.level.getEntity(p.entityId);
            if (e == null) continue;
            double t = p.time(partial);
            Vec3 pos = e.getPosition(partial);
            for (RockSet set : en.getValue()) {
                if (t < set.t() || t > set.t() + set.d()) continue;
                double u = (t - set.t()) / set.d();
                double lift = u < 0.25 ? easeOut(u / 0.25) : (u > 0.85 ? Math.max(0, 1 - (u - 0.85) / 0.15 * 1.4) : 1.0);
                for (Rock rock : set.rocks()) {
                    double ang = rock.ang() + t * 0.02 * set.orbit();
                    double y = set.groundY() + set.rise() * rock.lift() * lift + Math.sin(t * 0.15 + rock.phase()) * 0.12;
                    double x = pos.x + Math.cos(ang) * rock.rad(), z = pos.z + Math.sin(ang) * rock.rad();
                    ps.pushPose();
                    ps.translate(x - cam.x, y - cam.y, z - cam.z);
                    ps.mulPose(Axis.YP.rotationDegrees((float) (t * rock.spin())));
                    ps.mulPose(Axis.XP.rotationDegrees((float) (t * rock.spin() * 0.7)));
                    float s = (float) rock.size();
                    ps.scale(s, s, s);
                    ps.translate(-0.5, -0.5, -0.5);
                    int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
                    dispatcher.renderSingleBlock(rock.state(), ps, bs, light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
            }
        }
        bs.endBatch();
    }

    // ---- primitivas (quads dos dois lados, aditivos)

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float[] c, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] c, float a0, float a1, float a2, float a3) {
        v(vc, m, p0, c, a0); v(vc, m, p1, c, a1); v(vc, m, p2, c, a2); v(vc, m, p3, c, a3);
        v(vc, m, p3, c, a3); v(vc, m, p2, c, a2); v(vc, m, p1, c, a1); v(vc, m, p0, c, a0);
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 center, double rad, double w, float[] c, float a, double tiltDeg) {
        int seg = 48;
        double ri = Math.max(0, rad - w / 2), ro = rad + w / 2;
        double tilt = Math.toRadians(tiltDeg);
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 i0 = pt(center, a0, ri, tilt), i1 = pt(center, a1, ri, tilt), o0 = pt(center, a0, ro, tilt), o1 = pt(center, a1, ro, tilt);
            Vec3 m0 = pt(center, a0, rad, tilt), m1 = pt(center, a1, rad, tilt);
            quad(vc, m, i0, m0, m1, i1, c, 0f, a, a, 0f);
            quad(vc, m, m0, o0, o1, m1, c, a, 0f, 0f, a);
        }
    }

    private static Vec3 pt(Vec3 c, double ang, double r, double tilt) {
        double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
        double y = z * Math.sin(tilt);
        z = z * Math.cos(tilt);
        return c.add(x, y, z);
    }

    private static void cylinder(VertexConsumer vc, Matrix4f m, Vec3 base, double r, double h, float[] c, float a) {
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 b0 = base.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), b1 = base.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            Vec3 t0 = b0.add(0, h, 0), t1 = b1.add(0, h, 0);
            quad(vc, m, b0, b1, t1, t0, c, a, a, 0f, 0f);
        }
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 center, Vec3 L, Vec3 U, double r, float[] c, float a) {
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 e0 = center.add(L.scale(Math.cos(a0) * r)).add(U.scale(Math.sin(a0) * r));
            Vec3 e1 = center.add(L.scale(Math.cos(a1) * r)).add(U.scale(Math.sin(a1) * r));
            quad(vc, m, center, center, e1, e0, c, a, a, 0f, 0f);
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 camRel, double w, float[] c, float al0, float al1) {
        Vec3 dir = b.subtract(a);
        Vec3 view = a.subtract(camRel == null ? Vec3.ZERO : camRel);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1e-8) side = new Vec3(0, 1, 0);
        side = side.normalize().scale(w);
        quad(vc, m, a.add(side), a.subtract(side), b.subtract(side), b.add(side), c, al0, al0, al1, al1);
    }

    private static void bolts(VertexConsumer vc, Matrix4f m, Vec3 feet, Vec3 cam, double h, double unit, JsonObject q, float[] c, float a, long seed, double t) {
        int n = (int) Scene.num(q, "n", 5);
        double rad = Scene.num(q, "r", 1.1) * unit;
        int frame = (int) (t / Scene.num(q, "rate", 2));
        for (int k = 0; k < n; k++) {
            RandomSource r = RandomSource.create(seed * 92821L + frame * 1013L + k * 7L);
            if (r.nextFloat() > Scene.num(q, "chance", 0.8)) continue;
            double a0 = r.nextDouble() * Math.PI * 2;
            Vec3 start = feet.add(Math.cos(a0) * 0.25 * unit, h * (0.15 + r.nextDouble() * 0.8), Math.sin(a0) * 0.25 * unit);
            double a1 = a0 + (r.nextDouble() - 0.5) * 1.6;
            Vec3 end = feet.add(Math.cos(a1) * rad, h * (r.nextDouble() * 1.1), Math.sin(a1) * rad);
            Vec3 prev = start;
            int segs = 6;
            for (int s = 1; s <= segs; s++) {
                double f = s / (double) segs;
                Vec3 p = start.add(end.subtract(start).scale(f));
                if (s < segs) p = p.add((r.nextDouble() - 0.5) * 0.35 * unit, (r.nextDouble() - 0.5) * 0.35 * unit, (r.nextDouble() - 0.5) * 0.35 * unit);
                ribbon(vc, m, prev, p, Vec3.ZERO, 0.035 * unit, new float[]{1f, 1f, 1f}, a, a);
                ribbon(vc, m, prev, p, Vec3.ZERO, 0.11 * unit, c, a * 0.6f, a * 0.6f);
                prev = p;
            }
        }
    }

    private static void spiral(VertexConsumer vc, Matrix4f m, Vec3 feet, Vec3 cam, double h, double unit, JsonObject q, float[] c, float a, double t) {
        int n = (int) Scene.num(q, "n", 3);
        double rad = Scene.num(q, "r", 0.9) * unit, spd = Scene.num(q, "spd", 0.25), turns = Scene.num(q, "turns", 2.0);
        int steps = 28;
        for (int k = 0; k < n; k++) {
            double phase = k * Math.PI * 2 / n + t * spd;
            Vec3 prev = null;
            for (int s = 0; s <= steps; s++) {
                double f = s / (double) steps;
                double ang = phase + f * Math.PI * 2 * turns;
                double rr = rad * (1.0 - 0.35 * f);
                Vec3 p = feet.add(Math.cos(ang) * rr, f * h * 1.25, Math.sin(ang) * rr);
                if (prev != null) {
                    float al = a * (float) Math.sin(f * Math.PI);
                    ribbon(vc, m, prev, p, Vec3.ZERO, 0.06 * unit, c, al, al);
                }
                prev = p;
            }
        }
    }

    private static void streaks(VertexConsumer vc, Matrix4f m, Vec3 feet, Vec3 cam, double h, double unit, JsonObject q, float[] c, float a, double t, RandomSource r) {
        int n = (int) Scene.num(q, "n", 14);
        double far = Scene.num(q, "far", 3.5) * unit;
        boolean out = Scene.num(q, "out", 0) > 0;
        for (int k = 0; k < n; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double y = h * (0.1 + r.nextDouble() * 0.9);
            double cyc = ((t * 0.09 + r.nextDouble()) % 1.0);
            double d = out ? Mth.lerp(cyc, 0.4 * unit, far) : Mth.lerp(cyc, far, 0.35 * unit);
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 p0 = feet.add(dir.scale(d)).add(0, y, 0);
            Vec3 p1 = feet.add(dir.scale(d + (out ? -0.8 : 0.8) * unit)).add(0, y, 0);
            float al = a * (float) Math.sin(cyc * Math.PI);
            ribbon(vc, m, p0, p1, Vec3.ZERO, 0.025 * unit, c, al, 0f);
        }
    }

    private static void shards(VertexConsumer vc, Matrix4f m, Vec3 feet, Vec3 L, Vec3 U, double h, double unit, JsonObject q, float[] c, float a, double age, RandomSource r) {
        int n = (int) Scene.num(q, "n", 24);
        double spd = Scene.num(q, "spd", 0.25) * unit;
        Vec3 center = feet.add(0, h * 0.55, 0);
        for (int k = 0; k < n; k++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7 + 0.3, r.nextGaussian()).normalize().scale(spd * (0.6 + r.nextDouble()));
            Vec3 p = center.add(v.scale(age)).add(0, -0.004 * age * age, 0);
            double s = (0.08 + r.nextDouble() * 0.12) * unit;
            double spin = age * (0.2 + r.nextDouble() * 0.3) + k;
            Vec3 ax = L.scale(Math.cos(spin) * s).add(U.scale(Math.sin(spin) * s));
            Vec3 ay = L.scale(-Math.sin(spin) * s).add(U.scale(Math.cos(spin) * s));
            quad(vc, m, p.add(ax), p.add(ay), p.subtract(ax), p.subtract(ax), c, a, a, a, a);
        }
    }
}
