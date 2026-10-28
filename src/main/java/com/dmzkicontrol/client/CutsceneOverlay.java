package com.dmzkicontrol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Faixas de cinema, clarao do pulso e titulo da cutscene (as outras partes do HUD somem durante a cena). */
public final class CutsceneOverlay implements IGuiOverlay {
    public static final CutsceneOverlay INSTANCE = new CutsceneOverlay();

    private CutsceneOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!CutsceneController.isActive()) return;
        float t = CutsceneController.ticks() + partialTick;
        float dur = CutsceneController.DURATION;

        // faixas pretas entram nos primeiros 10 ticks e saem nos ultimos 10
        float bars = Math.min(ease(t / 10f), ease((dur - t) / 10f));
        int barH = Math.round(h * 0.11f * bars);
        if (barH > 0) {
            g.fill(0, 0, w, barH, 0xFF000000);
            g.fill(0, h - barH, w, h, 0xFF000000);
        }

        // linhas de foco de manga convergindo para o personagem (antes e logo depois do selo)
        float pulse = CutsceneController.PULSE_TICK;
        float focus = t < pulse ? Mth.clamp((t - (pulse - 14)) / 14f, 0f, 1f) : Mth.clamp(1f - (t - pulse) / 12f, 0f, 1f);
        if (focus > 0.01f) focusLines(g, w, h, focus, (int) (t / 2));

        // clarao no instante do selo
        float sincePulse = t - CutsceneController.PULSE_TICK;
        if (sincePulse >= 0 && sincePulse < 8) {
            float a = 0.55f * (1f - sincePulse / 8f);
            g.fill(0, 0, w, h, DmzStyle.argb(a, 0xFFFFFF));
        }

        // "batimento" escrito como onomatopeia de manga
        dokun(g, w, h, sincePulse, 0f, 1.0f);
        dokun(g, w, h, sincePulse - 7f, 0.16f, 0.7f);

        // titulo
        if (t > 47) {
            float in = Mth.clamp((t - 47) / 6f, 0f, 1f);
            float out = Mth.clamp((dur - t) / 6f, 0f, 1f);
            float a = Math.min(in, out);
            float slide = (1f - in) * 8f;
            DmzStyle.text(g, Component.translatable("cutscene.dmzkicontrol.title"), w / 2f, h * 0.70f + slide, 2.2f, 0.5f, 0xBFEFFF, a);
            DmzStyle.text(g, Component.translatable("cutscene.dmzkicontrol.subtitle"), w / 2f, h * 0.70f + 24 + slide, 1.0f, 0.5f, 0xFFFFFF, a * 0.9f);
        }

        // dica de pular
        if (barH > 4) {
            Component key = ClientSetup.SEAL_KEY.getTranslatedKeyMessage();
            DmzStyle.text(g, Component.translatable("cutscene.dmzkicontrol.skip", key), w - 6f, h - barH / 2f - 3f, 0.75f, 1f, 0xA0A0A0, bars * 0.8f);
        }
    }

    private static void focusLines(GuiGraphics g, int w, int h, float k, int seed) {
        float cx = w / 2f, cy = h / 2f;
        float maxR = (float) Math.sqrt(cx * cx + cy * cy);
        java.util.Random r = new java.util.Random(seed * 31L + 7);
        int lines = 90;
        for (int i = 0; i < lines; i++) {
            float ang = (i + r.nextFloat() * 0.8f) / lines * 360f;
            float inner = maxR * (0.48f + 0.25f * r.nextFloat() + 0.2f * (1f - k));
            float thick = 0.6f + r.nextFloat() * 1.8f;
            int a = (int) (k * (140 + r.nextInt(90)));
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(ang));
            // fino perto do centro, mais grosso na borda (como o traco de nanquim)
            int steps = 4;
            for (int sIdx = 0; sIdx < steps; sIdx++) {
                float x0 = inner + (maxR - inner) * sIdx / steps, x1 = inner + (maxR - inner) * (sIdx + 1) / steps;
                float hw = thick * (sIdx + 1) / steps;
                g.fill(Math.round(x0), Math.round(-hw), Math.round(x1), Math.max(Math.round(hw), Math.round(-hw) + 1), (a << 24));
            }
            g.pose().popPose();
        }
    }

    /** Onomatopeia "ドクン" (batimento) tremendo um pouco, com contorno preto. */
    private static void dokun(GuiGraphics g, int w, int h, float since, float offset, float scale) {
        if (since < 0f || since > 12f) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String txt = "\u30C9\u30AF\u30F3";
        float a = since < 2f ? since / 2f : 1f - Mth.clamp((since - 6f) / 6f, 0f, 1f);
        int alpha = Math.max(8, (int) (a * 255));
        float s = (3.2f + 0.6f * Mth.clamp(1f - since / 3f, 0f, 1f)) * scale;
        float shake = since < 4f ? (4f - since) * 0.6f : 0f;
        g.pose().pushPose();
        g.pose().translate(w * (0.24f + offset) + (float) Math.sin(since * 13) * shake, h * (0.36f + offset * 0.6f) + (float) Math.cos(since * 11) * shake, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-10f));
        g.pose().scale(s, s, 1f);
        int black = (alpha << 24);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) g.drawString(font, txt, dx, dy, black, false);
        g.drawString(font, txt, 0, 0, (alpha << 24) | 0xFFFFFF, false);
        g.pose().popPose();
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
