package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/** Indicador do Selamento de Aura: estado, dominio, ki poupado, tensao e pico de liberacao. */
public final class SealHudOverlay implements IGuiOverlay {
    public static final SealHudOverlay INSTANCE = new SealHudOverlay();

    private static final int BAR_W = 90;
    private static final int BAR_H = 3;

    private SealHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientSealState.hasData()) return;
        KiControlClientConfig cfg = KiControlClientConfig.INSTANCE;
        if (!cfg.showHud.get()) return;

        boolean sealed = ClientSealState.sealed;
        boolean transformed = !ClientSealState.formKey.isEmpty();
        int burst = ClientSealState.burstTicksLeft();
        if (!sealed && burst <= 0 && (!transformed || !cfg.showWhenIdle.get())) return;

        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (sealed) {
            lines.add(Component.translatable("hud.dmzkicontrol.sealed"));
            colors.add(0x66E0FF);
        } else if (transformed) {
            lines.add(Component.translatable(ClientSealState.hasSkill ? "hud.dmzkicontrol.free" : "hud.dmzkicontrol.need_skill"));
            colors.add(0xA0A0A0);
        }
        if (transformed) {
            lines.add(Component.translatable("hud.dmzkicontrol.stats",
                    pct(ClientSealState.mastery), pct(ClientSealState.kiReduction), pct(ClientSealState.powerBonus)));
            colors.add(sealed ? 0xE0F8FF : 0x909090);
        }
        if (burst > 0) {
            lines.add(Component.translatable("hud.dmzkicontrol.burst", (burst + 19) / 20));
            colors.add(0xFFC040);
        }

        boolean showBar = sealed && ClientSealState.strain >= 0f;
        boolean perfect = sealed && ClientSealState.strain < 0f;
        if (perfect) {
            lines.add(Component.translatable("hud.dmzkicontrol.perfect"));
            colors.add(0xD8A0FF);
        }

        int lineH = font.lineHeight + 1;
        int width = BAR_W;
        for (Component c : lines) width = Math.max(width, font.width(c));
        int height = lines.size() * lineH + (showBar ? BAR_H + 2 : 0);

        int x, y;
        switch (cfg.anchor.get()) {
            case TOP_LEFT -> { x = 4; y = 4; }
            case TOP_RIGHT -> { x = screenW - width - 4; y = 4; }
            case BOTTOM_LEFT -> { x = 4; y = screenH - height - 4; }
            case BOTTOM_RIGHT -> { x = screenW - width - 4; y = screenH - height - 4; }
            default -> { x = (screenW - width) / 2; y = screenH - 64 - height; }
        }
        x += cfg.offsetX.get();
        y += cfg.offsetY.get();

        for (int i = 0; i < lines.size(); i++) {
            Component c = lines.get(i);
            int lx = cfg.anchor.get() == KiControlClientConfig.Anchor.ABOVE_HOTBAR ? x + (width - font.width(c)) / 2 : x;
            g.drawString(font, c, lx, y + i * lineH, colors.get(i), true);
        }

        if (showBar) {
            int by = y + lines.size() * lineH + 1;
            float breakAt = Math.max(1f, ClientSealState.breakAt);
            float ratio = Math.min(1f, ClientSealState.strain / breakAt); // cheia = o selo quebra
            int filled = Math.round(BAR_W * ratio);
            int bx = x + (width - BAR_W) / 2;
            int color = ClientSealState.strain < 0.8f ? 0xFF55D0FF
                    : ClientSealState.strain < 1.0f ? 0xFFFFD040 : 0xFFFF4040;
            g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, 0x90000000);
            if (filled > 0) g.fill(bx, by, bx + filled, by + BAR_H, color);
            int mark = bx + Math.round(BAR_W / breakAt); // marca do limite (inicio da tensao)
            g.fill(mark, by - 1, mark + 1, by + BAR_H + 1, 0xFFFFFFFF);
        }
    }

    private static String pct(float v) {
        return String.valueOf(Math.round(v * 100f));
    }
}
