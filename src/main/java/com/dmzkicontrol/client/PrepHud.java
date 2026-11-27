package com.dmzkicontrol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Relogio do prazo de treino (canto de cima, a direita): quanto falta para o inimigo chegar, no tempo da
 * historia ("7 meses") e no tempo de jogo, com uma barra que esvazia.
 */
public final class PrepHud {
    private static final int GOLD = 0xFFC94A, WHITE = 0xFFFFFF, RED = 0xFF5A4A;
    private static String id = "";
    private static int left, total = 1, storyDays;

    private PrepHud() {}

    public static void onPacket(String i, int l, int t, int sd) {
        id = i == null ? "" : i;
        left = l;
        total = Math.max(1, t);
        storyDays = sd;
    }

    public static void reset() {
        id = "";
    }

    /** "1 ano e 3 meses", "7 meses", "9 dias", "5 horas". */
    private static Component story(double days) {
        if (days >= 365) {
            int y = (int) (days / 365), m = (int) ((days - y * 365) / 30.4);
            return m > 0 ? Component.translatable("hud.dmzkicontrol.prep.years_months", y, m) : Component.translatable("hud.dmzkicontrol.prep.years", y);
        }
        if (days >= 45) return Component.translatable("hud.dmzkicontrol.prep.months", (int) Math.round(days / 30.4));
        if (days >= 1) return Component.translatable("hud.dmzkicontrol.prep.days", (int) Math.ceil(days));
        return Component.translatable("hud.dmzkicontrol.prep.hours", Math.max(1, (int) Math.ceil(days * 24)));
    }

    public static final IGuiOverlay OVERLAY = (gui, g, partial, w, h) -> {
        if (id.isEmpty() || left <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float frac = Mth.clamp(left / (float) total, 0f, 1f);
        float x = w - 8f, y = 8f;
        int col = frac < 0.25f ? DmzStyle.mix(WHITE, RED, 0.5f + 0.5f * Mth.sin((mc.player.tickCount + partial) * 0.3f)) : WHITE;
        DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.prep.title." + id), x, y, 0.8f, 1f, GOLD, 0.95f);
        DmzStyle.text(g, story(storyDays * (double) frac), x, y + 11, 1.3f, 1f, col, 1f);
        int secs = left / 20;
        DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.prep.real", secs / 60, String.format("%02d", secs % 60)), x, y + 27, 0.7f, 1f, 0xBFBFBF, 0.85f);
        int bw = 96, bx = (int) x - bw, by = (int) y + 37;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0xAA000000);
        g.fill(bx + Math.round(bw * (1 - frac)), by, bx + bw, by + 2, DmzStyle.argb(1f, frac < 0.25f ? RED : GOLD));
    };
}
