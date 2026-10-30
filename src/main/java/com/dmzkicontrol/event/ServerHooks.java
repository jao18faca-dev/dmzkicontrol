package com.dmzkicontrol.event;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.control.AuraSealManager.ReleaseReason;
import com.dmzkicontrol.control.MasteryStore;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ganchos nos eventos do DMZ ({@link DMZEvent}) e do Forge - lado servidor. */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerHooks {

    private ServerHooks() {}

    // ------------------------------------------------------------ poder

    // O bonus de poder agora entra no multiplicador do DMZ (AuraSealManager.updatePowerEffect),
    // entao nao multiplicamos mais o dano aqui (seria contado duas vezes).

    /** Limpa o efeito de poder que possa ter sobrado de uma sessao anterior. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) AuraSealManager.clearPowerEffect(sp);
    }

    /** Ki controlado = golpes mais precisos. */
    @SubscribeEvent
    public static void onCrit(DMZEvent.CritChanceEvent event) {
        Player p = event.getPlayer();
        if (!(p instanceof ServerPlayer sp) || !AuraSealManager.isSealed(sp)) return;
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return;
        String key = AuraSealManager.formKey(data);
        if (key == null) return;
        event.setChance(Math.min(1.0, event.getChance() + AuraSealManager.critBonus(sp, key)));
    }

    /** O ki nao vaza do corpo: regenera um pouco mais enquanto selado. */
    @SubscribeEvent
    public static void onEnergyRegen(DMZEvent.EnergyRegenEvent event) {
        Player p = event.getPlayer();
        if (!(p instanceof ServerPlayer sp) || !AuraSealManager.isSealed(sp)) return;
        String key = AuraSealManager.formKey(event.getStatsData());
        if (key == null || event.getAmount() <= 0) return;
        double bonus = KiControlConfig.get().energyRegenBonus * MasteryStore.mastery(sp, key);
        event.setAmount(event.getAmount() * (1.0 + Math.max(0.0, bonus)));
    }

    /** Guarda a regen final (depois de todos os listeners) para separar regen de upkeep no mesmo tick. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEnergyRegenFinal(DMZEvent.EnergyRegenEvent event) {
        AuraSealManager.noteKiRegen(event.getPlayer(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStaminaRegenFinal(DMZEvent.StaminaRegenEvent event) {
        AuraSealManager.noteStaminaRegen(event.getPlayer(), event.getAmount());
    }

    // ------------------------------------------------------------ gastos que NAO sao upkeep

    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        AuraSealManager.markBusy(event.getPlayer(), 2);
        AuraSealManager.markCombat(event.getPlayer());
        if (event.getPlayer() instanceof ServerPlayer sp) {
            AuraSealManager.addStrainFromEvent(sp, KiControlConfig.get().strain.perKiAttackPercent);
        }
    }

    // ------------------------------------------------------------ combate (treino rende mais lutando)

    @SubscribeEvent
    public static void onDamageDealt(DMZEvent.DamageDealtEvent event) {
        if (event.getAttacker() != null) AuraSealManager.markCombat(event.getAttacker());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.getAmount() <= 0) return;
        AuraSealManager.markCombat(sp);
        if (event.getSource().getEntity() instanceof Player attacker) AuraSealManager.markCombat(attacker);
        AuraSealManager.addStrainFromEvent(sp, KiControlConfig.get().strain.perHitTakenPercent);
    }

    @SubscribeEvent
    public static void onKiCast(DMZEvent.KiAttackCastEvent event) {
        AuraSealManager.markBusy(event.getPlayer(), 2);
    }

    @SubscribeEvent
    public static void onStrikeCast(DMZEvent.StrikeAttackCastEvent event) {
        AuraSealManager.markBusy(event.getPlayer(), 2);
    }

    @SubscribeEvent
    public static void onDash(DMZEvent.PlayerDashEvent event) {
        if (!event.isCanceled()) AuraSealManager.markBusy(event.getPlayer(), 2);
    }

    @SubscribeEvent
    public static void onEvasion(DMZEvent.PlayerEvasionEvent event) {
        if (!event.isCanceled()) AuraSealManager.markBusy(event.getPlayer(), 2);
    }

    /** Carregar ki = expandir a aura (opcional, desligado por padrao). */
    @SubscribeEvent
    public static void onKiCharge(DMZEvent.KiChargeEvent event) {
        if (!KiControlConfig.get().chargingReleasesSeal) return;
        if (event.getPlayer() instanceof ServerPlayer sp && AuraSealManager.isSealed(sp)) {
            AuraSealManager.release(sp, ReleaseReason.CHARGED);
        }
    }

    // ------------------------------------------------------------ formas

    @SubscribeEvent
    public static void onFormChange(DMZEvent.FormChangeEvent event) {
        // A troca de forma pode custar ki: ignora essa queda. Se o jogador voltou para a base,
        // o tick seguinte percebe (formKey == null) e encerra o selo.
        AuraSealManager.markBusy(event.getPlayer(), 5);
    }

    @SubscribeEvent
    public static void onStackFormChange(DMZEvent.StackFormChangeEvent event) {
        AuraSealManager.markBusy(event.getPlayer(), 5);
    }

    // ------------------------------------------------------------ ciclo de vida

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player instanceof ServerPlayer sp) AuraSealManager.tick(sp);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) AuraSealManager.release(sp, ReleaseReason.DEATH);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // devolve a aura permanente antes do save do jogador
            AuraSealManager.release(sp, ReleaseReason.SILENT);
        }
        AuraSealManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            AuraSealManager.onStartTracking(viewer, target);
            com.dmzkicontrol.cerealian.CerealianEye.onStartTracking(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            AuraSealManager.onStopTracking(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) AuraSealManager.resendSealedState(sp);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        MasteryStore.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AuraSealManager.clearAll();
    }
}
