package com.dmzkicontrol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Helpers para desenhar no mesmo estilo do HUD do DragonMineZ: a fonte "dragonminez:smooth" com
 * contorno preto e as folhas de sprites dele (texturas lidas do proprio jar do DMZ, nada copiado).
 */
public final class DmzStyle {
    public static final ResourceLocation DMZ_FONT = new ResourceLocation("dragonminez", "smooth");
    /** Folha dos medidores laterais do DMZ (Reserva de Ki / Furia): 128x256 texels. */
    public static final ResourceLocation RESERVE_SHEET = new ResourceLocation("dragonminez", "textures/gui/hud/hud_reserve.png");
    public static final int SHEET_W = 128, SHEET_H = 256;

    private DmzStyle() {}

    public static MutableComponent dmz(Component c) {
        return c.copy().withStyle(Style.EMPTY.withFont(DMZ_FONT));
    }

    public static MutableComponent dmz(String s) {
        return dmz(Component.literal(s));
    }

    /** Texto com contorno preto, como o HudRender.dmzText do DMZ. align: 0 = esquerda, 0.5 = centro. */
    public static void text(GuiGraphics g, Component text, float x, float y, float scale, float align, int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        if (a <= 3) return;
        Font font = Minecraft.getInstance().font;
        Component c = dmz(text);
        int color = (a << 24) | (rgb & 0xFFFFFF);
        int border = a << 24;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-font.width(c) * align, 0f, 0f);
        FormattedCharSequence outline = uncolored(c.getVisualOrderText());
        g.drawString(font, outline, -1, 0, border, false);
        g.drawString(font, outline, 1, 0, border, false);
        g.drawString(font, outline, 0, -1, border, false);
        g.drawString(font, outline, 0, 1, border, false);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    private static FormattedCharSequence uncolored(FormattedCharSequence seq) {
        return sink -> seq.accept((index, style, cp) -> sink.accept(index, style.withColor((TextColor) null), cp));
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gg = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (gg << 8) | b;
    }

    public static int argb(float alpha, int rgb) {
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | (rgb & 0xFFFFFF);
    }
}
