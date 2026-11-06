package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A curadoria tira formas inventadas dos arquivos do DMZ. Quem tinha saido do jogo transformado numa delas
 * voltava com uma forma ativa que nao existe mais (sem cor, sem multiplicador, aura quebrada). No login,
 * forma ativa que o DMZ nao acha mais volta para a base.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormSafety {
    private FormSafety() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sp.server.execute(() -> settle(sp));
    }

    public static void settle(ServerPlayer sp) {
        try {
            StatsData data = AuraSealManager.stats(sp);
            if (data == null) return;
            Character c = data.getCharacter();
            boolean changed = false;
            if (c.hasActiveForm() && c.getActiveFormData() == null) {
                KiControlMod.LOGGER.info("[Formas] {} estava em {}.{}, que nao existe mais: volta para a base.",
                        sp.getGameProfile().getName(), c.getActiveFormGroup(), c.getActiveForm());
                c.clearActiveForm(sp);
                changed = true;
            }
            if (c.hasActiveStackForm() && c.getActiveStackFormData() == null) {
                KiControlMod.LOGGER.info("[Formas] {} estava em {}.{} (somada), que nao existe mais: sai dela.",
                        sp.getGameProfile().getName(), c.getActiveStackFormGroup(), c.getActiveStackForm());
                c.clearActiveStackForm(sp);
                changed = true;
            }
            if (changed) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(sp), sp);
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("[Formas] verificacao da forma ativa: {}", t.toString());
        }
    }
}
