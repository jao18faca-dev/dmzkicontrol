package com.dmzkicontrol.client.scene;

import com.dmzkicontrol.client.DmzStyle;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Tela da cutscene de transformacao (so de quem se transforma): faixas de cinema, clarao, tingimento das bordas,
 * escurecer, linhas de foco de manga, onomatopeias em pe, nome da forma e frase. O resto do HUD some.
 */
public final class SceneOverlay implements IGuiOverlay {
    public static final SceneOverlay INSTANCE = new SceneOverlay();

    private SceneOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        ScenePlayback.Play play = ScenePlayback.local();
        if (play == null) return;
        Scene s = play.scene;
        float t = (float) Math.min(s.len, play.time(partialTick));
        float bars = Math.min(ease(t / 10f), ease((s.len - t) / 10f));
        int barH = Math.round(h * 0.11f * bars);

        for (Scene.Fx f : s.scr) {
            if (t < f.t() || t > f.t() + f.d()) continue;
            float u = (t - f.t()) / Math.max(1, f.d());
            JsonObject q = f.p();
            int rgb = Scene.rgb(q, "c", 0xFFFFFF);
            float a = (float) Scene.num(q, "a", 0.6);
            switch (f.kind()) {
                case "flash" -> g.fill(0, 0, w, h, DmzStyle.argb(a * (1f - u) * (1f - u), rgb));
                case "fade" -> {
                    // escurece ate o meio e volta
                    float k = (float) Math.sin(u * Math.PI);
                    g.fill(0, 0, w, h, DmzStyle.argb(a * k, rgb));
                }
                case "hold" -> g.fill(0, 0, w, h, DmzStyle.argb(a * Math.min(1f, Math.min(u / 0.1f, (1f - u) / 0.2f)), rgb));
                case "tint" -> vignette(g, w, h, rgb, a * Math.min(1f, Math.min(u / 0.15f, (1f - u) / 0.25f)));
                case "focus" -> focusLines(g, w, h, Math.min(1f, Math.min(u / 0.15f, (1f - u) / 0.2f)) * a, (int) (t / 2), rgb);
                case "sfx" -> vertical(g, Scene.str(q, "txt", "ゴゴゴ"), w * (float) Scene.num(q, "x", 0.75), h * (float) Scene.num(q, "y", 0.2),
                        (float) Scene.num(q, "s", 2.4), (float) Scene.num(q, "tilt", 4), Math.min(1f, Math.min(u / 0.12f, (1f - u) / 0.25f)),
                        (float) Scene.num(q, "shake", 1.0), t - f.t(), rgb);
                case "bigsfx" -> big(g, Scene.str(q, "txt", "ドン"), w * (float) Scene.num(q, "x", 0.25), h * (float) Scene.num(q, "y", 0.35),
                        (float) Scene.num(q, "s", 3.4), (float) Scene.num(q, "tilt", -10), t - f.t(), f.d(), rgb);
                default -> {}
            }
        }

        if (barH > 0) {
            g.fill(0, 0, w, barH, 0xFF000000);
            g.fill(0, h - barH, w, h, 0xFF000000);
        }

        // nome da forma e frase
        if (t >= s.titleT && t <= s.titleT + s.titleD) {
            float k = t - s.titleT;
            float a = Math.min(Mth.clamp(k / 6f, 0f, 1f), Mth.clamp((s.titleD - k) / 6f, 0f, 1f));
            float slide = (1f - Mth.clamp(k / 6f, 0f, 1f)) * 10f;
            int col = titleColor(s);
            DmzStyle.text(g, Component.translatable(s.titleKey), w / 2f, h * 0.72f + slide, 2.4f, 0.5f, col, a);
        }
        if (t >= s.subT && t <= s.subT + s.subD) {
            float k = t - s.subT;
            float a = Math.min(Mth.clamp(k / 6f, 0f, 1f), Mth.clamp((s.subD - k) / 6f, 0f, 1f));
            DmzStyle.text(g, Component.translatable(s.subKey), w / 2f, h * 0.72f + 26, 1.0f, 0.5f, 0xFFFFFF, a * 0.9f);
        }
        if (barH > 4) {
            Component key = SceneKeys.SKIP.getTranslatedKeyMessage();
            DmzStyle.text(g, Component.translatable("scene.dmzkicontrol.skip", key), w - 6f, h - barH / 2f - 3f, 0.75f, 1f, 0xA0A0A0, bars * 0.8f);
        }
    }

    private static int titleColor(Scene s) {
        for (Scene.Fx f : s.fx) if (f.p().has("c")) return Scene.rgb(f.p(), "c", 0xFFFFFF);
        return 0xFFFFFF;
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float a) {
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int col = DmzStyle.argb(a * k * k * 0.5f, rgb);
            int dx = Math.round(w * 0.22f * i / steps), dy = Math.round(h * 0.22f * i / steps);
            int bw = Math.max(1, Math.round(w * 0.22f / steps)), bh = Math.max(1, Math.round(h * 0.22f / steps));
            g.fill(dx, dy, w - dx, dy + bh, col);
            g.fill(dx, h - dy - bh, w - dx, h - dy, col);
            g.fill(dx, dy + bh, dx + bw, h - dy - bh, col);
            g.fill(w - dx - bw, dy + bh, w - dx, h - dy - bh, col);
        }
    }

    private static void focusLines(GuiGraphics g, int w, int h, float k, int seed, int rgb) {
        if (k <= 0.01f) return;
        float cx = w / 2f, cy = h / 2f;
        float maxR = (float) Math.sqrt(cx * cx + cy * cy);
        java.util.Random r = new java.util.Random(seed * 31L + 7);
        int lines = 90;
        for (int i = 0; i < lines; i++) {
            float ang = (i + r.nextFloat() * 0.8f) / lines * 360f;
            float inner = maxR * (0.5f + 0.25f * r.nextFloat() + 0.2f * (1f - k));
            float thick = 0.6f + r.nextFloat() * 1.8f;
            int a = (int) (k * (120 + r.nextInt(90)));
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(ang));
            for (int sIdx = 0; sIdx < 4; sIdx++) {
                float x0 = inner + (maxR - inner) * sIdx / 4f, x1 = inner + (maxR - inner) * (sIdx + 1) / 4f;
                float hw = thick * (sIdx + 1) / 4f;
                g.fill(Math.round(x0), Math.round(-hw), Math.round(x1), Math.max(Math.round(hw), Math.round(-hw) + 1), (a << 24) | (rgb & 0xFFFFFF));
            }
            g.pose().popPose();
        }
    }

    /** Onomatopeia em pe (de cima para baixo), letras surgindo uma a uma e tremendo, com contorno preto. */
    private static void vertical(GuiGraphics g, String txt, float x, float y, float scale, float tilt, float alpha, float shake, float time, int color) {
        if (alpha <= 0.02f) return;
        var font = Minecraft.getInstance().font;
        int a = Math.max(8, (int) (alpha * 255));
        int shown = Math.min(txt.length(), Math.max(1, (int) (time / 2f) + 1));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(scale, scale, 1f);
        float line = font.lineHeight + 2;
        for (int i = 0; i < shown; i++) {
            String ch = txt.substring(i, i + 1);
            int cw = font.width(ch);
            float jx = (float) Math.sin(time * 12.7 + i * 2.1) * shake / scale;
            float jy = (float) Math.cos(time * 10.3 + i * 1.7) * shake / scale;
            g.pose().pushPose();
            g.pose().translate(-cw / 2f + jx, i * line + jy, 0);
            int black = a << 24;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(font, ch, dx, dy, black, false);
            g.drawString(font, ch, 0, 0, (a << 24) | (color & 0xFFFFFF), false);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    /** Onomatopeia grande de impacto (ドン!), surge maior e encolhe. */
    private static void big(GuiGraphics g, String txt, float x, float y, float scale, float tilt, float since, int dur, int color) {
        var font = Minecraft.getInstance().font;
        float a = since < 2f ? since / 2f : 1f - Mth.clamp((since - dur * 0.5f) / (dur * 0.5f), 0f, 1f);
        int alpha = Math.max(8, (int) (a * 255));
        float s = scale * (1f + 0.25f * Mth.clamp(1f - since / 3f, 0f, 1f));
        float shake = since < 4f ? (4f - since) * 0.7f : 0f;
        g.pose().pushPose();
        g.pose().translate(x + (float) Math.sin(since * 13) * shake, y + (float) Math.cos(since * 11) * shake, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(s, s, 1f);
        int black = alpha << 24;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(font, txt, dx, dy, black, false);
        g.drawString(font, txt, 0, 0, (alpha << 24) | (color & 0xFFFFFF), false);
        g.pose().popPose();
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
