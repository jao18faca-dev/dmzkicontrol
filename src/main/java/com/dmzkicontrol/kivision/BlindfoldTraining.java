package com.dmzkicontrol.kivision;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
    /** Fracao de TP extra que ainda nao fechou 1 TP (o DMZ da 2 TP por golpe: 20% disso se perdia no arredondamento). */
    private static final Map<UUID, Double> CARRY = new ConcurrentHashMap<>();
    /** TP extra ganho desde o ultimo aviso na tela. */
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

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
        markCombat(event.getSource().getEntity(), event.getEntity());
    }

    /** Golpe bloqueado, desviado ou que nao chegou a ferir tambem e combate. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        markCombat(event.getSource().getEntity(), event.getEntity());
    }

    private static void markCombat(net.minecraft.world.entity.Entity source, net.minecraft.world.entity.LivingEntity target) {
        if (source instanceof ServerPlayer attacker && attacker != target) {
            LAST_COMBAT.put(attacker.getUUID(), attacker.level().getGameTime());
        }
        if (target instanceof ServerPlayer victim && source != null && source != victim) {
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
        UUID id = sp.getUUID();
        double extra = event.getTpGain() * bonus + CARRY.getOrDefault(id, 0.0);
        int whole = (int) Math.floor(extra);
        CARRY.put(id, extra - whole);
        if (whole <= 0) return;
        event.setTpGain((int) Math.min(Integer.MAX_VALUE, (long) event.getTpGain() + whole));
        PENDING.merge(id, whole, Integer::sum);
    }

    /** A cada 2 s em combate mostra quanto TP a venda rendeu a mais. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp) || sp.tickCount % 40 != 0) return;
        Integer extra = PENDING.remove(sp.getUUID());
        if (extra == null || extra <= 0) return;
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.blindfold_tp", extra).withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_COMBAT.remove(event.getEntity().getUUID());
        CARRY.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }
}
