package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.client.ArmAnimDriver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Cena da primeira transformacao dentro de uma luta (no estilo dos "dramatic finish" do FighterZ):
 * clarao, camera baixa girando em volta de quem se transforma, close no rosto, explosao de poder com
 * quadro de impacto e um plano aberto de heroi. So a camera e os efeitos: o jogo nao para e ninguem fica preso.
 */
public final class EncTransformCine {
    /** Duracao total, em ticks (a janela de transformacao no servidor espera 56). */
    public static final int LEN = 54;
    private static final int BLEND_OUT = 8;

    /**
     * Planos: tick inicial, tick final, e de/para (angulo em graus em volta do personagem, 0 = de frente para ele;
     * distancia; altura da camera; altura do olhar; fov; inclinacao). Plano seguinte com outro inicio = corte seco.
     */
    private static final double[][] SHOTS = {
            // contra-plongee girando: de baixo, inclinado, o poder subindo
            {0, 20, -40, 1.5, 0.15, 1.25, 0.82, 11, 35, 1.25, 0.35, 1.45, 0.78, 6},
            // close no rosto, empurrando a camera (o grito)
            {20, 32, 12, 0.95, 1.62, 1.6, 0.62, -5, 6, 0.62, 1.64, 1.62, 0.55, -2},
            // explosao: plano aberto de heroi, girando devagar
            {32, LEN, -150, 5.2, 0.4, 1.2, 1.12, 0, -205, 4.4, 1.3, 1.3, 1.0, 0},
    };

    private static int role = -1;
    private static long start;
    private static int beat = -1;
    private static int fired = -1;
    private static boolean used;

    private EncTransformCine() {}

    public static void reset() {
        role = -1;
        fired = -1;
        used = false;
        beat = -1;
    }

    /** Chamado quando o servidor avisa que alguem mudou de forma na janela. So a primeira da luta ganha a cena. */
    public static void onTransformed(EncClient.State s, int who) {
        if (used || s == null || !s.participant()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        used = true;
        role = who;
        start = mc.level.getGameTime();
        beat = s.beatIdx;
        fired = -1;
        tick(s);
    }

    public static boolean active(EncClient.State s) {
        if (role < 0 || s == null) return false;
        if (s.beatIdx != beat) {
            role = -1;
            return false;
        }
        return elapsed(0) < LEN + BLEND_OUT;
    }

    private static double elapsed(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1e9 : mc.level.getGameTime() - start + partial;
    }

    /** Eventos da cena (poses, efeitos, sons, impactos). */
    public static void tick(EncClient.State s) {
        if (!active(s)) return;
        int t = (int) elapsed(0);
        Entity p = s.player(role);
        for (int k = fired + 1; k <= t; k++) at(s, k, p);
        fired = t;
    }

    private static void at(EncClient.State s, int k, Entity p) {
        if (p == null) return;
        switch (k) {
            case 0 -> {
                EncHud.impact(1);
                pose(p, "evs.rage_scream", 32);
                EncFx.spawn("aura_burst", p, null, 30, 1.3f);
                EncFx.spawn("rage", p, null, 34, 1.3f);
                EncClient.playAt(p, "dragonminez:aura_start", 1f, 0.8f);
            }
            case 6, 14 -> EncFx.spawn("rage", p, null, 20, 1.5f);
            case 20 -> {
                EncHud.shake(0.8f);
                EncClient.playAt(p, "dragonminez:ki_charge_loop", 0.9f, 1.3f);
            }
            case 32 -> {
                EncHud.impact(3);
                EncHud.shake(3.2f);
                pose(p, "transf.ssj", 22);
                EncFx.spawn("power_burst", p, null, 30, 2.0f);
                EncFx.spawn("shockwave", p, null, 18, 2.2f);
                EncFx.spawn("crater", p, null, 40, 1.4f);
                EncClient.playAt(p, "dragonminez:ki_explosion_impact", 1.2f, 0.9f);
                EncClient.playAt(p, "dragonminez:transform_on", 1f, 1f);
            }
            case 40 -> EncFx.spawn("aura_burst", p, null, 24, 1.6f);
            default -> {}
        }
    }

    private static void pose(Entity e, String name, int ticks) {
        if (e instanceof AbstractClientPlayer acp) ArmAnimDriver.play(acp, name, ticks);
    }

    /**
     * Camera da cena: {olho, alvo, fov, inclinacao, mistura}. mistura < 1 = voltando para a camera da luta.
     * Devolve null quando a cena nao esta ativa.
     */
    public static Object[] camera(EncClient.State s, float partial) {
        if (!active(s)) return null;
        Entity p = s.player(role);
        if (p == null) return null;
        double t = elapsed(partial);
        double[] sh = SHOTS[SHOTS.length - 1];
        for (double[] c : SHOTS) if (t >= c[0] && t < c[1]) { sh = c; break; }
        double u = Mth.clamp((t - sh[0]) / (sh[1] - sh[0]), 0, 1);
        u = u * u * (3 - 2 * u);
        double ang = Mth.lerp(u, sh[2], sh[8]);
        double dist = Mth.lerp(u, sh[3], sh[9]);
        double h = Mth.lerp(u, sh[4], sh[10]);
        double look = Mth.lerp(u, sh[5], sh[11]);
        float fov = (float) Mth.lerp(u, sh[6], sh[12]);
        float roll = (float) Mth.lerp(u, sh[7], sh[13]);

        Vec3 pos = p.getPosition(partial);
        // frente do personagem = direcao do chefe
        Entity b = s.boss();
        Vec3 fwd = b != null ? b.getPosition(partial).subtract(pos) : s.dir(0, 0, -1);
        fwd = new Vec3(fwd.x, 0, fwd.z);
        fwd = fwd.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
        double a = Math.toRadians(ang);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 off = fwd.scale(Math.cos(a) * dist).add(right.scale(Math.sin(a) * dist));
        Vec3 eye = pos.add(off.x, h, off.z);
        Vec3 at = pos.add(0, look, 0);
        float mix = t <= LEN ? 1f : (float) (1 - (t - LEN) / BLEND_OUT);
        mix = mix * mix * (3 - 2 * mix);
        return new Object[]{eye, at, fov, roll, mix};
    }
}
