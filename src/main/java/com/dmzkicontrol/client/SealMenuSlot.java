package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlClientConfig;
import com.dmzkicontrol.config.KiControlClientConfig.CutsceneMode;
import com.dragonminez.client.gui.utilitymenu.ButtonInfo;
import com.dragonminez.client.gui.utilitymenu.IUtilityMenuSlot;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Botao "Selar Aura" no menu utilitario do DMZ (UtilityMenuScreen.addMenuSlot).
 * Clique esquerdo: sela/libera. Clique direito: liga/desliga a cutscene.
 */
public final class SealMenuSlot implements IUtilityMenuSlot {

    @Override
    public ButtonInfo render(StatsData statsData) {
        boolean on = ClientSealState.sealed;
        boolean cutscene = KiControlClientConfig.INSTANCE.cutscene.get() != CutsceneMode.OFF;
        ButtonInfo info = new ButtonInfo(
                Component.translatable("menu.dmzkicontrol.seal"),
                Component.translatable(on ? "menu.dmzkicontrol.on" : "menu.dmzkicontrol.off")
                        .append(Component.literal(cutscene ? " ✦" : "")),
                on);
        info.setColor(on ? 0x66E0FF : 0xFFFFFF);
        return info;
    }

    @Override
    public void handle(StatsData statsData, boolean rightClick) {
        if (!rightClick) {
            ClientSetup.onSealPressed();
            return;
        }
        var value = KiControlClientConfig.INSTANCE.cutscene;
        CutsceneMode next = value.get() == CutsceneMode.OFF ? CutsceneMode.ALWAYS : CutsceneMode.OFF;
        value.set(next);
        value.save();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(next == CutsceneMode.OFF
                    ? "message.dmzkicontrol.cutscene_off" : "message.dmzkicontrol.cutscene_on"), true);
        }
    }

    @Override
    public boolean hasRightClickAction(StatsData statsData) {
        return true;
    }
}
