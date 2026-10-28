package com.dmzkicontrol.client;

import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Desenho de energia no mesmo estilo das armas de ki do DragonMineZ: usa o shader de lamina de ki do
 * proprio DMZ (borda colorida, nucleo claro, chamas de ki tremulando nas bordas).
 *
 * <p>O shader tem dois modos que usamos:
 * <ul>
 *   <li>lamina: forma de folha com uma explosao de ki na base (u 0..1, v -1..1);</li>
 *   <li>bastao: segmentos com chamas (u a partir de 60), usados nos bracos e na corda do arco.</li>
 * </ul>
 * As coordenadas recebidas devem estar no mesmo espaco do {@code pose}; {@code cam} e a posicao da
 * camera nesse espaco (as fitas sempre ficam viradas para ela).
 */
public final class KiFx {
    public static final int ROD_SHAFT = 0;
    public static final int ROD_ELBOW = 1;
    public static final int ROD_TINE = 5;

    private KiFx() {}

    public static boolean available() {
        try {
            return DMZShaders.kiBladeShader != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static RenderType type() {
        return ModRenderTypes.kiBlade();
    }

    public static float seed(Entity e) {
        return (e.getId() * 0.618034f) % 1.0f;
    }

    /** Cor do ki (aura atual) de uma entidade com dados do DMZ, em RGB 0..1. */
    public static float[] kiColor(Entity e, int fallback) {
        int c = fallback;
        try {
            String hex = StatsProvider.get(StatsCapability.INSTANCE, e).resolve()
                    .map(d -> d.getCharacter().getActiveAuraColor()).orElse(null);
            if (hex != null && !hex.isEmpty()) c = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (Throwable ignored) {
        }
        return rgb(c);
    }

    public static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    /** Lado da fita: perpendicular ao eixo e a direcao da camera. */
    public static Vec3 side(Vec3 a, Vec3 b, Vec3 cam) {
        Vec3 dir = b.subtract(a);
        Vec3 s = dir.cross(cam.subtract(a.add(b).scale(0.5)));
        if (s.lengthSqr() < 1e-10) s = dir.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1e-10) s = new Vec3(1, 0, 0);
        return s.normalize();
    }

    /**
     * Lamina de ki de {@code start} (base, onde fica a explosao) ate {@code end} (ponta).
     * {@code halfWidth} e a meia-largura do quad em blocos (a folha ocupa ~40% dela).
     */
    public static void blade(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 start, Vec3 end, float halfWidth, float[] c, float seed) {
        Vec3 axis = end.subtract(start);
        double len = axis.length();
        if (len < 1e-4) return;
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 side = side(start, end, cam);
        double u0 = -0.07;
        quad(vc, m, start, dir, side, u0 * len, len, -halfWidth, halfWidth, (float) u0, 1f, -1f, 1f, c, seed);
    }

    /**
     * Bastao de ki com chamas de {@code from} a {@code to}. {@code piece}: 0 = haste (afina no fim, em {@code to}; grossura
     * constante), 1-4 = braco (afina pouco), 5+ = ponta (afina ate virar ponta em {@code to}).
     * {@code thickness} escala a grossura (1 = ~0,11 bloco de raio com as chamas).
     */
    public static void rod(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 from, Vec3 to, int piece, float thickness, float[] c, float seed) {
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 1e-4) return;
        float uvLen = piece == ROD_SHAFT ? 1.875f : piece < ROD_TINE ? 0.5634f : 0.7344f;
        double k = len / uvLen;
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 side = side(from, to, cam);
        float pad = 0.3f, reach = 0.36f;
        float uBase = 60f + piece * 4f;
        quad(vc, m, from, dir, side, -pad * k, (uvLen + pad) * k, -reach * thickness, reach * thickness,
                uBase - pad, uBase + uvLen + pad, -reach, reach, c, seed);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 origin, Vec3 dir, Vec3 side,
                             double s0, double s1, double t0, double t1, float u0, float u1, float v0, float v1, float[] c, float seed) {
        vert(vc, m, origin, dir, side, s0, t0, u0, v0, c, seed);
        vert(vc, m, origin, dir, side, s0, t1, u0, v1, c, seed);
        vert(vc, m, origin, dir, side, s1, t1, u1, v1, c, seed);
        vert(vc, m, origin, dir, side, s1, t0, u1, v0, c, seed);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 o, Vec3 dir, Vec3 side, double s, double t, float u, float v, float[] c, float seed) {
        float x = (float) (o.x + dir.x * s + side.x * t);
        float y = (float) (o.y + dir.y * s + side.y * t);
        float z = (float) (o.z + dir.z * s + side.z * t);
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], seed).uv(u, v).endVertex();
    }

    /** Fita aditiva simples (para quem nao tem o shader do DMZ, e para espirais de aura). */
    public static void glow(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        Vec3 s = side(a, b, cam).scale(width);
        vc.vertex(m, (float) (a.x + s.x), (float) (a.y + s.y), (float) (a.z + s.z)).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) (a.x - s.x), (float) (a.y - s.y), (float) (a.z - s.z)).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) (b.x - s.x), (float) (b.y - s.y), (float) (b.z - s.z)).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) (b.x + s.x), (float) (b.y + s.y), (float) (b.z + s.z)).color(r, g, bl, alpha).endVertex();
    }
}
