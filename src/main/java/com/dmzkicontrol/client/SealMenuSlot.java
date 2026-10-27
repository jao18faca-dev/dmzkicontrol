package com.dmzkicontrol.client;

import com.dragonminez.client.gui.utilitymenu.ButtonInfo;
import com.dragonminez.client.gui.utilitymenu.IUtilityMenuSlot;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.network.chat.Component;

/** Botao "Selar Aura" no menu utilitario do DMZ (UtilityMenuScreen.addMenuSlot). */
public final class SealMenuSlot implements IUtilityMenuSlot {

    @Override
    public ButtonInfo render(StatsData statsData) {
        boolean on = ClientSealState.sealed;
        ButtonInfo info = new ButtonInfo(
                Component.translatable("menu.dmzkicontrol.seal"),
                Component.translatable(on ? "menu.dmzkicontrol.on" : "menu.dmzkicontrol.off"),
                on);
        info.setColor(on ? 0x66E0FF : 0xFFFFFF);
        return info;
    }

    @Override
    public void handle(StatsData statsData, boolean rightClick) {
        ClientSetup.requestToggle();
    }
}
