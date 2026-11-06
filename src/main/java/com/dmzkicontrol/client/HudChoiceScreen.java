package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Escolha entre a HUD do DMZ Ultra e a original do DragonMineZ (aparece uma vez; depois, /dmzhud). */
public class HudChoiceScreen extends Screen {
    private final Screen parent;

    public HudChoiceScreen(Screen parent) {
        super(Component.translatable("gui.dmzkicontrol.hud_choice.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(260, this.width - 32);
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 10;
        KiControlClientConfig.UltraHud now = UltraHud.mode();
        addRenderableWidget(Button.builder(label("gui.dmzkicontrol.hud_choice.dmz", now == KiControlClientConfig.UltraHud.DMZ),
                b -> pick(KiControlClientConfig.UltraHud.DMZ)).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(label("gui.dmzkicontrol.hud_choice.ultra", now == KiControlClientConfig.UltraHud.ULTRA),
                b -> pick(KiControlClientConfig.UltraHud.ULTRA)).bounds(x, y + 46, w, 20).build());
    }

    private static Component label(String key, boolean current) {
        Component c = Component.translatable(key);
        return current ? Component.literal("> ").append(c).append(" <") : c;
    }

    private void pick(KiControlClientConfig.UltraHud mode) {
        UltraHud.choose(mode);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 - 10;
        g.drawCenteredString(this.font, this.title, cx, y - 52, 0xFFE9A23B);
        g.drawCenteredString(this.font, Component.translatable("gui.dmzkicontrol.hud_choice.question"), cx, y - 36, 0xFFFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("gui.dmzkicontrol.hud_choice.dmz.desc"), cx, y + 23, 0xFFAAAAAA);
        g.drawCenteredString(this.font, Component.translatable("gui.dmzkicontrol.hud_choice.ultra.desc"), cx, y + 69, 0xFFAAAAAA);
        g.drawCenteredString(this.font, Component.translatable("gui.dmzkicontrol.hud_choice.later"), cx, y + 96, 0xFF808080);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // fechar sem escolher: pergunta de novo na proxima vez que entrar num mundo
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
