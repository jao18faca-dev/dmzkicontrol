package com.dmzkicontrol.kivision;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Treinar vendado: o TP que o DMZ da em combate rende mais enquanto a Venda de Treino esta no rosto. */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlindfoldTraining {
    public static final String KI_SENSE = "kisense";

    /** Ultimo tick em que o jogador bateu ou apanhou. */
    private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();

    private BlindfoldTraining() {}

    public static int kiSense(Player p) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, p).resolve()
                    .map(d -> Math.max(0, d.getSkills().getSkillLevel(KI_SENSE))).orElse(0);
        } catch (Throwable t) {
            return 0;
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            LAST_COMBAT.put(attacker.getUUID(), attacker.level().getGameTime());
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            LAST_COMBAT.put(victim.getUUID(), victim.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onTpGain(DMZEvent.TPGainEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        KiControlConfig.KiVision cfg = KiControlConfig.get().kiVision;
        if (!cfg.enabled || event.getTpGain() <= 0 || !BlindfoldItem.isWearing(sp)) return;
        Long last = LAST_COMBAT.get(sp.getUUID());
        if (last == null || sp.level().getGameTime() - last > cfg.combatWindowTicks) return;
        double bonus = Math.min(cfg.blindfoldTpBonusMax, cfg.blindfoldTpBonus + cfg.blindfoldTpBonusPerKiSense * kiSense(sp));
        if (bonus <= 0) return;
        event.setTpGain((int) Math.round(event.getTpGain() * (1.0 + bonus)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_COMBAT.remove(event.getEntity().getUUID());
    }
}
