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

        // clarao no instante do selo
        float sincePulse = t - CutsceneController.PULSE_TICK;
        if (sincePulse >= 0 && sincePulse < 8) {
            float a = 0.55f * (1f - sincePulse / 8f);
            g.fill(0, 0, w, h, DmzStyle.argb(a, 0xFFFFFF));
        }

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

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
