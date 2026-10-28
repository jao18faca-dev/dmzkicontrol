package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.events.DMZEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Eventos das integracoes com outros add-ons. */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntegrationHooks {
    /** Jogadores que acabaram de entrar no Blue Aperfeicoado: sela no proximo tick (dados da forma ja sincronizados). */
    private static final Map<UUID, Long> AUTO_SEAL = new ConcurrentHashMap<>();

    private IntegrationHooks() {}

    /**
     * Blue Aperfeicoado (DMZ Sparking): no manga e o Blue com o ki todo preso no corpo. Quem tem o Aura
     * Control no nivel exigido ja transforma com a aura selada, sem cutscene.
     */
    @SubscribeEvent
    public static void onFormChange(DMZEvent.FormChangeEvent event) {
        if (!KiControlConfig.get().integrations.perfectedBlueAutoSeal) return;
        String form = event.getNewForm() == null ? "" : event.getNewForm().toLowerCase(Locale.ROOT);
        if (!form.contains("blueperfected")) return;
        ServerPlayer sp = event.getPlayer();
        if (sp == null || AuraSealManager.isSealed(sp)) return;
        AUTO_SEAL.put(sp.getUUID(), sp.level().getGameTime() + 2);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || AUTO_SEAL.isEmpty() || !(event.player instanceof ServerPlayer sp)) return;
        Long at = AUTO_SEAL.get(sp.getUUID());
        if (at == null || sp.level().getGameTime() < at) return;
        AUTO_SEAL.remove(sp.getUUID());
        if (AuraSealManager.isSealed(sp)) return;
        var data = AuraSealManager.stats(sp);
        String key = data == null ? null : AuraSealManager.formKey(data);
        if (key == null || AuraSealManager.auraLevel(data) < KiControlConfig.get().tierFor(key).minAuraLevel) return;
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.perfected_blue").withStyle(ChatFormatting.AQUA), true);
        AuraSealManager.requestToggle(sp, false);
    }
}
