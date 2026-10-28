package com.dmzkicontrol.client;

import com.dmzkicontrol.network.SealAnimS2C;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Efeitos do Selamento de Aura inspirados no treino com o Whis no manga (arco do Black): o Goku fecha
 * o Blue sem deixar o ki vazar.
 *
 * <ol>
 *   <li><b>Inspirar</b>: a aura vira riscos de ki em espiral, puxados para a mao no peito, onde um
 *   nucleo de ki vai se concentrando.</li>
 *   <li><b>Agua parada</b>: no instante do selo o nucleo some dentro do corpo, um "batimento" (som de
 *   coracao) e aneis finos se espalham no chao como numa superficie de agua calma.</li>
 *   <li><b>Liberar / perder o controle</b>: os riscos saem do corpo de uma vez (vermelhos quando o
 *   selo estoura).</li>
 * </ol>
 * Usa o shader das armas de ki do DMZ quando disponivel (cor da aura da forma atual).
 */
public final class SealFxRenderer {
    private static final int STREAKS = 14;
    /** entityId -> {tipo, tick de inicio}. */
    private static final Map<Integer, long[]> ACTIVE = new HashMap<>();

    private SealFxRenderer() {}

    public static void start(Player p, byte type) {
        if (p.level() == null) return;
        ACTIVE.put(p.getId(), new long[]{type, p.level().getGameTime()});
    }

    public static void cancel(Entity e) {
        ACTIVE.remove(e.getId());
    }

    /** Ticks desde o pulso do selo rapido do proprio jogador (ou -1). */
    public static float localQuickSince(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return -1f;
        long[] v = ACTIVE.get(mc.player.getId());
        if (v == null || v[0] != SealAnimS2C.QUICK) return -1f;
        return mc.level.getGameTime() - v[1] + partial - pulseTick(SealAnimS2C.QUICK);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static int pulseTick(long type) {
        return type == SealAnimS2C.CUTSCENE ? CutsceneController.PULSE_TICK : 8;
    }

    private static int duration(long type) {
        if (type == SealAnimS2C.RELEASE) return 14;
        if (type == SealAnimS2C.BREAK) return 18;
        return pulseTick(type) + 34;
    }

    /** Sons no tick certo (batimento no selo). */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, long[]>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, long[]> en = it.next();
            long type = en.getValue()[0];
            long t = now - en.getValue()[1];
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || t > duration(type)) { it.remove(); continue; }
            boolean sealing = type == SealAnimS2C.QUICK || type == SealAnimS2C.CUTSCENE;
            if (sealing && t == pulseTick(type)) {
                mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.9f, false);
                mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.6f, false);
            }
            if (sealing && t == pulseTick(type) + 7) {
                mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.85f, false);
            }
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 camWorld = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        boolean shader = KiFx.available();

        // 1) riscos e nucleo (shader de ki do DMZ)
        if (shader) {
            VertexConsumer vc = bs.getBuffer(KiFx.type());
            for (Map.Entry<Integer, long[]> en : ACTIVE.entrySet()) {
                if (!(mc.level.getEntity(en.getKey()) instanceof Player p)) continue;
                drawKi(vc, null, m, camWorld, p, en.getValue()[0], now - en.getValue()[1] + partial, partial);
            }
            bs.endBatch(KiFx.type());
        }
        // 2) aneis no chao e fallback aditivo
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        for (Map.Entry<Integer, long[]> en : ACTIVE.entrySet()) {
            if (!(mc.level.getEntity(en.getKey()) instanceof Player p)) continue;
            float t = now - en.getValue()[1] + partial;
            long type = en.getValue()[0];
            if (!shader) drawKi(null, glow, m, camWorld, p, type, t, partial);
            drawRipples(glow, m, camWorld, p, type, t, partial);
        }
        bs.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------------------------ riscos de ki

    private static void drawKi(VertexConsumer vc, VertexConsumer glow, Matrix4f m, Vec3 camWorld, Player p, long type, float t, float partial) {
        boolean first = p == Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (first) return; // em primeira pessoa os riscos cobririam a tela
        float[] c = KiFx.rgb(type == SealAnimS2C.BREAK ? 0xFF4030 : SealEffects.auraColor(p));
        float seed = KiFx.seed(p);
        Vec3 chest = p.getPosition(partial).add(0, p.getBbHeight() * 0.68, 0).subtract(camWorld);
        Vec3 cam = Vec3.ZERO;
        double sc = p.getBbHeight() / 1.8;
        double yaw = Math.toRadians(p.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 hand = chest.add(fwd.scale(0.22 * sc));

        if (type == SealAnimS2C.QUICK || type == SealAnimS2C.CUTSCENE) {
            int pulse = pulseTick(type);
            if (t < pulse) {
                float k = t / pulse; // 0..1 ate o selo
                double radius = (type == SealAnimS2C.CUTSCENE ? 2.8 : 1.7) * sc;
                double period = type == SealAnimS2C.CUTSCENE ? 14.0 : 6.0;
                int n = type == SealAnimS2C.CUTSCENE ? STREAKS : STREAKS / 2 + 2;
                for (int i = 0; i < n; i++) {
                    double phase = frac(t / period + hash(i, seed));
                    double r = radius * (1.0 - phase) + 0.2 * sc;
                    double r2 = Math.max(0.12 * sc, r - (0.9 + 0.6 * k) * sc);
                    double th = hash(i * 7 + 3, seed) * Math.PI * 2;
                    double ph = (hash(i * 13 + 5, seed) - 0.5) * 1.6;
                    double twist = 1.4; // espiral: o risco gira enquanto entra
                    Vec3 outer = hand.add(sphere(th + twist * (1 - phase), ph, r));
                    Vec3 inner = hand.add(sphere(th + twist * (1 - phase) + 0.35, ph * 0.8, r2));
                    float thick = (float) ((0.35 + 0.35 * k) * sc * Math.sin(Math.PI * Math.min(1.0, phase * 1.1)));
                    if (thick < 0.03f) continue;
                    line(vc, glow, m, cam, inner, outer, thick, c, seed);
                }
                // nucleo de ki concentrando na mao
                float core = (float) ((0.12 + 0.38 * k) * sc);
                core(vc, glow, m, cam, hand, fwd, core, c, seed);
            } else {
                // o nucleo afunda no peito e some
                float after = (t - pulse) / 4f;
                if (after < 1f) core(vc, glow, m, cam, hand.subtract(fwd.scale(0.18 * sc * after)), fwd, (float) (0.5 * sc * (1 - after)), c, seed);
            }
        } else if (type == SealAnimS2C.RELEASE || type == SealAnimS2C.BREAK) {
            boolean violent = type == SealAnimS2C.BREAK;
            float life = violent ? 18f : 14f;
            float k = Mth.clamp(t / life, 0f, 1f);
            int n = violent ? STREAKS + 6 : STREAKS;
            for (int i = 0; i < n; i++) {
                double th = hash(i * 7 + 1, seed) * Math.PI * 2;
                double ph = (hash(i * 11 + 9, seed) - 0.5) * 1.8;
                double r1 = (0.3 + (violent ? 3.6 : 2.4) * easeOut(k)) * sc;
                double r0 = Math.max(0.2 * sc, r1 - (violent ? 1.4 : 1.0) * sc);
                float thick = (float) ((violent ? 0.7 : 0.5) * sc * (1 - k));
                if (thick < 0.03f) continue;
                line(vc, glow, m, cam, chest.add(sphere(th, ph, r1)), chest.add(sphere(th, ph, r0)), thick, c, seed);
            }
        }
    }

    private static void line(VertexConsumer vc, VertexConsumer glow, Matrix4f m, Vec3 cam, Vec3 head, Vec3 tail, float thick, float[] c, float seed) {
        if (vc != null) KiFx.rod(vc, m, cam, head, tail, KiFx.ROD_TINE, thick, c, seed);
        else {
            KiFx.glow(glow, m, cam, head, tail, 0.06f * thick, c[0], c[1], c[2], 0.5f);
            KiFx.glow(glow, m, cam, head, tail, 0.02f * thick, 1f, 1f, 1f, 0.8f);
        }
    }

    private static void core(VertexConsumer vc, VertexConsumer glow, Matrix4f m, Vec3 cam, Vec3 at, Vec3 fwd, float size, float[] c, float seed) {
        if (size < 0.02f) return;
        if (vc != null) KiFx.blade(vc, m, cam, at.subtract(fwd.scale(size * 0.3)), at.add(fwd.scale(size * 1.6)), size * 1.2f, c, seed + 0.3f);
        else KiFx.glow(glow, m, cam, at.subtract(fwd.scale(size * 0.4)), at.add(fwd.scale(size * 0.4)), size * 0.5f, c[0], c[1], c[2], 0.7f);
    }

    // ------------------------------------------------------------------ agua parada

    /** Aneis finos se abrindo no chao depois do selo, como gotas numa agua calma. */
    private static void drawRipples(VertexConsumer glow, Matrix4f m, Vec3 camWorld, Player p, long type, float t, float partial) {
        boolean sealing = type == SealAnimS2C.QUICK || type == SealAnimS2C.CUTSCENE;
        float since = sealing ? t - pulseTick(type) : t;
        if (since < 0) return;
        float[] c = KiFx.rgb(type == SealAnimS2C.BREAK ? 0xFF4030 : SealEffects.auraColor(p));
        Vec3 feet = p.getPosition(partial).add(0, 0.06, 0).subtract(camWorld);
        int rings = sealing ? 3 : 1;
        float speed = sealing ? 0.22f : 0.45f;
        float life = sealing ? 22f : 12f;
        for (int i = 0; i < rings; i++) {
            float s = since - i * 4f;
            if (s < 0 || s > life) continue;
            float r = 0.4f + s * speed * (1f + 0.25f * i);
            float a = (1f - s / life) * (sealing ? 0.55f : 0.45f);
            ring(glow, m, feet, r, 0.035f + 0.02f * (1f - s / life), c, a);
            ring(glow, m, feet, r, 0.012f, new float[]{1f, 1f, 1f}, a * 0.9f);
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float w, float[] col, float a) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            float ri = r - w, ro = r + w;
            v(vc, m, c.x + x0 * ri, c.y, c.z + z0 * ri, col, a);
            v(vc, m, c.x + x0 * ro, c.y, c.z + z0 * ro, col, a);
            v(vc, m, c.x + x1 * ro, c.y, c.z + z1 * ro, col, a);
            v(vc, m, c.x + x1 * ri, c.y, c.z + z1 * ri, col, a);
            // verso (visto de baixo)
            v(vc, m, c.x + x1 * ri, c.y, c.z + z1 * ri, col, a);
            v(vc, m, c.x + x1 * ro, c.y, c.z + z1 * ro, col, a);
            v(vc, m, c.x + x0 * ro, c.y, c.z + z0 * ro, col, a);
            v(vc, m, c.x + x0 * ri, c.y, c.z + z0 * ri, col, a);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, float[] c, float a) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(c[0], c[1], c[2], a).endVertex();
    }

    // ------------------------------------------------------------------ util

    private static Vec3 sphere(double th, double ph, double r) {
        return new Vec3(Math.cos(th) * Math.cos(ph) * r, Math.sin(ph) * r * 0.85, Math.sin(th) * Math.cos(ph) * r);
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    private static double hash(int i, float seed) {
        double v = Math.sin(i * 127.1 + seed * 311.7) * 43758.5453;
        return v - Math.floor(v);
    }

    private static double easeOut(double x) {
        return 1 - (1 - x) * (1 - x);
    }
}
