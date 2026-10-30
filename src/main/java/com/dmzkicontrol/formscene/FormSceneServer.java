package com.dmzkicontrol.formscene;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.network.FormSceneS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cutscene de transformacao: toda vez que alguem entra numa forma (ou troca de forma, ou liga o Kaioken,
 * o Instinto Superior...), o servidor escolhe o roteiro daquela forma e manda para quem esta perto.
 * Quem se transforma fica parado e nao leva dano nem empurrao ate a cena acabar (ou ser pulada).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormSceneServer {
    private record Running(String scene, long end, Vec3 anchor, float yaw, float pitch, boolean hadNoGravity) {}

    private static final Map<UUID, Running> RUNNING = new ConcurrentHashMap<>();

    private FormSceneServer() {}

    public static boolean inScene(ServerPlayer sp) {
        return RUNNING.containsKey(sp.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFormChange(DMZEvent.FormChangeEvent event) {
        ServerPlayer sp = event.getPlayer();
        if (sp == null) return;
        if (event.getNewForm().isEmpty()) {
            // voltou a base no meio da cena (ki acabou, etc.): a cena termina
            if (RUNNING.containsKey(sp.getUUID())) stop(sp, true);
            return;
        }
        start(sp, event.getNewGroup(), event.getNewForm());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStackFormChange(DMZEvent.StackFormChangeEvent event) {
        ServerPlayer sp = event.getPlayer();
        if (sp == null || event.getNewForm().isEmpty()) return;
        if (!KiControlConfig.get().formScenes.stackForms) return;
        start(sp, event.getNewGroup(), event.getNewForm());
    }

    private static void start(ServerPlayer sp, String group, String form) {
        KiControlConfig.FormScenes cfg = KiControlConfig.get().formScenes;
        if (!cfg.enabled) return;
        String key = (group + "." + form).toLowerCase(Locale.ROOT);
        for (String off : cfg.disabledForms) if (off != null && key.equals(off.toLowerCase(Locale.ROOT))) return;
        StatsData data = AuraSealManager.stats(sp);
        String race = data == null ? "" : data.getCharacter().getRaceName();
        String scene = SceneLibrary.resolve(race, group, form);
        int len = SceneLibrary.length(scene);
        long now = sp.level().getGameTime();
        Running prev = RUNNING.get(sp.getUUID());
        boolean noGrav = prev != null ? prev.hadNoGravity() : sp.isNoGravity();
        Vec3 anchor = prev != null ? prev.anchor() : sp.position();
        RUNNING.put(sp.getUUID(), new Running(scene, now + len, anchor, sp.getYRot(), sp.getXRot(), noGrav));
        if (cfg.freeze) {
            sp.setNoGravity(true);
            sp.setDeltaMovement(Vec3.ZERO);
            sp.hurtMarked = true;
        }
        KiControlNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new FormSceneS2C(sp.getId(), scene, now, false));
    }

    public static void skip(ServerPlayer sp) {
        if (RUNNING.containsKey(sp.getUUID())) stop(sp, true);
    }

    private static void stop(ServerPlayer sp, boolean broadcast) {
        Running r = RUNNING.remove(sp.getUUID());
        if (r == null) return;
        if (KiControlConfig.get().formScenes.freeze) sp.setNoGravity(r.hadNoGravity());
        if (broadcast) {
            KiControlNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                    new FormSceneS2C(sp.getId(), r.scene(), sp.level().getGameTime(), true));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNNING.isEmpty() || !(event.player instanceof ServerPlayer sp)) return;
        Running r = RUNNING.get(sp.getUUID());
        if (r == null) return;
        if (sp.level().getGameTime() >= r.end() || !sp.isAlive()) {
            stop(sp, false); // o cliente encerra sozinho no fim
            return;
        }
        if (KiControlConfig.get().formScenes.freeze) {
            sp.setDeltaMovement(Vec3.ZERO);
            sp.fallDistance = 0;
            if (sp.position().distanceToSqr(r.anchor()) > 0.01) {
                sp.connection.teleport(r.anchor().x, r.anchor().y, r.anchor().z, r.yaw(), r.pitch());
            }
        }
    }

    /** Ninguem bate em quem esta se transformando. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && RUNNING.containsKey(sp.getUUID())
                && KiControlConfig.get().formScenes.invulnerable && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && RUNNING.containsKey(sp.getUUID()) && KiControlConfig.get().formScenes.invulnerable) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) stop(sp, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) stop(sp, false);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) stop(sp, true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }
}
