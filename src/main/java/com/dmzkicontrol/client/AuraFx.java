package com.dmzkicontrol.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Efeitos de aura desenhados com cuidado (aditivos, RenderType.lightning): em vez de cilindros solidos e
 * riscos grossos, linguas de chama que sobem e balancam em volta do corpo, faiscas eletricas curtas que
 * correm rente ao corpo (estilo Super Saiyajin 2) e coluna de luz macia virada para a camera.
 * Todas as posicoes sao relativas a camera.
 */
public final class AuraFx {
    private AuraFx() {}

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0f, 1f)).endVertex();
    }

    /** Quad visivel dos dois lados, com alfa por vertice. */
    public static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] c, float a0, float a1, float a2, float a3) {
        v(vc, m, p0, c[0], c[1], c[2], a0); v(vc, m, p1, c[0], c[1], c[2], a1); v(vc, m, p2, c[0], c[1], c[2], a2); v(vc, m, p3, c[0], c[1], c[2], a3);
        v(vc, m, p3, c[0], c[1], c[2], a3); v(vc, m, p2, c[0], c[1], c[2], a2); v(vc, m, p1, c[0], c[1], c[2], a1); v(vc, m, p0, c[0], c[1], c[2], a0);
    }

    /** Lado de uma fita virada para a camera (a camera esta na origem). */
    private static Vec3 side(Vec3 a, Vec3 b, double w) {
        Vec3 s = b.subtract(a).cross(a);
        if (s.lengthSqr() < 1e-10) s = new Vec3(0, 1, 0);
        return s.normalize().scale(w);
    }

    private static float[] lighter(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    /**
     * Chamas da aura: linguas finas que nascem nos pes, abracam o corpo e terminam em ponta acima da cabeca,
     * balancando e "subindo" sem parar. Duas camadas: a cor da aura por fora e quase branco por dentro.
     *
     * @param feet  pes (relativo a camera)
     * @param h     altura do corpo
     * @param scale tamanho (1 = aura normal; 1.6 = transformacao explodindo)
     * @param t     tempo em ticks (com o parcial)
     */
    public static void flames(VertexConsumer vc, Matrix4f m, Vec3 feet, double h, double scale, float[] c, float a, double t, long seed) {
        if (a <= 0.01f) return;
        int tongues = 14;
        float[] inner = lighter(c, 0.75f);
        for (int k = 0; k < tongues; k++) {
            RandomSource r = RandomSource.create(seed * 31L + k * 977L);
            double ang = k * Math.PI * 2 / tongues + r.nextDouble() * 0.4;
            double baseR = (0.32 + r.nextDouble() * 0.12) * scale;
            double len = h * (1.05 + r.nextDouble() * 0.45) * (0.85 + 0.15 * Math.sin(t * 0.35 + k * 1.7)) * scale;
            double w = (0.16 + r.nextDouble() * 0.08) * scale;
            double sway = 0.09 * scale;
            int segs = 7;
            Vec3 prev = null;
            double prevW = 0;
            for (int s = 0; s <= segs; s++) {
                double f = s / (double) segs;
                // abre na cintura e fecha em ponta acima da cabeca
                double rr = baseR * (0.75 + 0.55 * Math.sin(Math.PI * Math.min(1, f * 1.25))) * (1 - 0.6 * f * f);
                double a2 = ang + Math.sin(t * 0.22 + k + f * 3.0) * 0.12;
                double y = f * len - 0.05;
                Vec3 p = feet.add(Math.cos(a2) * rr + Math.sin(t * 0.3 + k * 2.1 + f * 4) * sway * f,
                        y, Math.sin(a2) * rr + Math.cos(t * 0.27 + k * 1.3 + f * 4) * sway * f);
                double ww = w * Math.sin(Math.PI * Math.min(1, f * 1.1 + 0.05)) * (1 - f * 0.55);
                if (prev != null) {
                    // brilho percorrendo a lingua de baixo para cima
                    double scroll = (t * 0.08 + k * 0.37) % 1.0;
                    float bump = (float) (0.75 + 0.25 * Math.cos((f - scroll) * Math.PI * 2));
                    float aa0 = (float) (a * Math.sin(Math.PI * (f - 1.0 / segs)) * bump);
                    float aa1 = (float) (a * Math.sin(Math.PI * f) * bump);
                    Vec3 s0 = side(prev, p, prevW), s1 = side(prev, p, ww);
                    quad(vc, m, prev.add(s0), prev.subtract(s0), p.subtract(s1), p.add(s1), c, aa0 * 0.55f, aa0 * 0.55f, aa1 * 0.55f, aa1 * 0.55f);
                    Vec3 i0 = side(prev, p, prevW * 0.35), i1 = side(prev, p, ww * 0.35);
                    quad(vc, m, prev.add(i0), prev.subtract(i0), p.subtract(i1), p.add(i1), inner, aa0 * 0.7f, aa0 * 0.7f, aa1 * 0.7f, aa1 * 0.7f);
                }
                prev = p;
                prevW = ww;
            }
        }
    }

    /**
     * Faiscas eletricas curtas, rente ao corpo, que piscam e trocam de lugar a cada 2 ticks (Super Saiyajin 2).
     * Fio branco fininho com um brilho da cor por fora.
     */
    public static void sparks(VertexConsumer vc, Matrix4f m, Vec3 feet, double h, double scale, float[] c, float a, double t, long seed, int count) {
        if (a <= 0.01f) return;
        int frame = (int) (t / 2.0);
        float[] glow = lighter(c, 0.35f);
        for (int k = 0; k < count; k++) {
            RandomSource r = RandomSource.create(seed * 92821L + frame * 1013L + k * 7L);
            if (r.nextFloat() > 0.65f) continue;
            double ang = r.nextDouble() * Math.PI * 2;
            double y0 = h * (0.15 + r.nextDouble() * 0.8);
            double rad = (0.3 + r.nextDouble() * 0.18) * scale;
            Vec3 start = feet.add(Math.cos(ang) * rad, y0, Math.sin(ang) * rad);
            double ang2 = ang + (r.nextDouble() - 0.5) * 1.1;
            double rad2 = rad + (r.nextDouble() * 0.25) * scale;
            Vec3 end = feet.add(Math.cos(ang2) * rad2, y0 + (r.nextDouble() - 0.4) * 0.6 * scale, Math.sin(ang2) * rad2);
            float flick = r.nextFloat() < 0.3f ? 0.5f : 1f;
            Vec3 prev = start;
            int segs = 5;
            for (int s = 1; s <= segs; s++) {
                double f = s / (double) segs;
                Vec3 p = start.add(end.subtract(start).scale(f));
                if (s < segs) p = p.add((r.nextDouble() - 0.5) * 0.12 * scale, (r.nextDouble() - 0.5) * 0.12 * scale, (r.nextDouble() - 0.5) * 0.12 * scale);
                Vec3 sg = side(prev, p, 0.035 * scale), sc = side(prev, p, 0.009 * scale);
                quad(vc, m, prev.add(sg), prev.subtract(sg), p.subtract(sg), p.add(sg), glow, a * 0.35f * flick, a * 0.35f * flick, a * 0.35f * flick, a * 0.35f * flick);
                quad(vc, m, prev.add(sc), prev.subtract(sc), p.subtract(sc), p.add(sc), new float[]{1f, 1f, 1f}, a * flick, a * flick, a * flick, a * flick);
                prev = p;
            }
        }
    }

    /**
     * Coluna de luz macia: fitas viradas para a camera, forte no meio e transparente nas bordas e no alto
     * (nada de tubo solido). Sobe de {@code feet} ate {@code height}.
     */
    public static void column(VertexConsumer vc, Matrix4f m, Vec3 feet, double height, double radius, float[] c, float a, double t) {
        if (a <= 0.01f || height <= 0.01) return;
        int bands = 10;
        Vec3 axisUp = new Vec3(0, 1, 0);
        Vec3 view = feet.add(0, height * 0.3, 0);
        Vec3 across = axisUp.cross(view);
        if (across.lengthSqr() < 1e-8) across = new Vec3(1, 0, 0);
        across = across.normalize();
        float[] core = lighter(c, 0.7f);
        for (int b = 0; b < bands; b++) {
            double y0 = height * b / bands, y1 = height * (b + 1) / bands;
            float f0 = (float) (1 - b / (double) bands), f1 = (float) (1 - (b + 1) / (double) bands);
            float fall0 = f0 * f0, fall1 = f1 * f1;
            double wob = 1 + 0.05 * Math.sin(t * 0.5 + b);
            Vec3 l0 = feet.add(0, y0, 0), l1 = feet.add(0, y1, 0);
            Vec3 o0 = across.scale(radius * wob), o1 = across.scale(radius * wob * (0.85 + 0.15 * f1));
            // metade esquerda e direita: alfa cheio no eixo, zero na borda
            quad(vc, m, l0, l0.add(o0), l1.add(o1), l1, c, a * fall0 * 0.6f, 0f, 0f, a * fall1 * 0.6f);
            quad(vc, m, l0, l0.subtract(o0), l1.subtract(o1), l1, c, a * fall0 * 0.6f, 0f, 0f, a * fall1 * 0.6f);
            Vec3 c0 = across.scale(radius * 0.3), c1 = across.scale(radius * 0.3);
            quad(vc, m, l0, l0.add(c0), l1.add(c1), l1, core, a * fall0 * 0.8f, 0f, 0f, a * fall1 * 0.8f);
            quad(vc, m, l0, l0.subtract(c0), l1.subtract(c1), l1, core, a * fall0 * 0.8f, 0f, 0f, a * fall1 * 0.8f);
        }
    }

    /** Fita fina com ponta (rastro de ki), que some perto da camera para nao cobrir a tela. */
    public static void streak(VertexConsumer vc, Matrix4f m, Vec3 head, Vec3 tail, double w, float[] c, float a) {
        double near = Math.min(head.length(), tail.length());
        if (near < 1.2) return;
        float fade = (float) Mth.clamp((near - 1.2) / 1.5, 0, 1);
        Vec3 s = side(tail, head, w);
        Vec3 sc = side(tail, head, w * 0.3);
        quad(vc, m, tail, tail, head.subtract(s), head.add(s), c, 0f, 0f, a * 0.6f * fade, a * 0.6f * fade);
        quad(vc, m, tail, tail, head.subtract(sc), head.add(sc), new float[]{1f, 1f, 1f}, 0f, 0f, a * fade, a * fade);
    }
}
