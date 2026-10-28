package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.race.RaceInstaller;
import com.dragonminez.common.events.DMZEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos do modulo Cerealiano (lado servidor). */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CerealianHooks {

    private CerealianHooks() {}

    /**
     * Servidor dedicado: o DMZ ja leu as racas na inicializacao, entao se acabamos de instalar
     * arquivos novos, pedimos um reload das configs dele (no singleplayer o DMZ ja recarrega sozinho).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!RaceInstaller.installedSomething() || !event.getServer().isDedicatedServer()) return;
        try {
            com.dragonminez.common.config.ConfigManager.reload();
            KiControlMod.LOGGER.info("Racas do add-on instaladas; configuracao do DragonMineZ recarregada.");
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("Instalei racas novas, mas nao consegui recarregar o DMZ ({}). Rode /dmzreload config.", t.toString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player instanceof ServerPlayer sp) CerealianEye.tick(sp);
    }

    /** Ponto fraco: primeiro golpe corpo a corpo/strike no alvo marcado. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamageModify(DMZEvent.DamageModifyEvent event) {
        if (!(event.getAttacker() instanceof ServerPlayer sp) || event.getVictim() == null) return;
        double[] bonus = CerealianEye.consumeWeakPoint(sp, event.getVictim());
        if (bonus == null) return;
        event.setAmount(event.getAmount() * bonus[0]);
        event.setDefensePenetration(Math.min(1.0, event.getDefensePenetration() + bonus[1]));
    }

    /** Atirador: projeteis (rajadas de ki, flechas...) de longe causam mais dano. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer shooter) || direct == null || direct == shooter) return;
        double mul = CerealianEye.sniperMultiplier(shooter, event.getEntity());
        if (mul > 1.0) event.setAmount((float) (event.getAmount() * mul));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CerealianEye.forget(event.getEntity().getUUID());
    }
}
