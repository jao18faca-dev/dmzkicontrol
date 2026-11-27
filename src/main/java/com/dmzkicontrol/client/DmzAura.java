package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A aura de verdade, a do proprio DragonMineZ, nas cenas e nas lutas: em vez de desenhar uma aura nossa, a gente
 * liga a aura do DMZ no jogador (aura forcada, so neste cliente) ou o brilho de carregar ki nos chefes das sagas,
 * pelo tempo que a cena pedir. Quando acaba, volta como estava.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzAura {
    /** id da entidade -> tick do cliente em que a aura desliga. */
    private static final Map<Integer, Long> UNTIL = new HashMap<>();
    /** Como estava antes (aura forcada do jogador / ki do chefe). */
    private static final Map<Integer, Boolean> BEFORE = new HashMap<>();

    private DmzAura() {}

    /** Liga a aura do DMZ nesta entidade por {@code ticks} (estende se ja estiver ligada). */
    public static void hold(Entity e, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.level == null) return;
        long until = mc.level.getGameTime() + Math.max(1, ticks);
        UNTIL.merge(e.getId(), until, Math::max);
        apply(e, true);
    }

    private static void apply(Entity e, boolean on) {
        try {
            if (e instanceof Player p) {
                StatsProvider.get(StatsCapability.INSTANCE, p).ifPresent(d -> {
                    Status st = d.getStatus();
                    if (on) {
                        BEFORE.putIfAbsent(e.getId(), st.isForcedAura());
                        st.setForcedAura(true);
                    } else {
                        Boolean b = BEFORE.remove(e.getId());
                        st.setForcedAura(b != null && b);
                    }
                });
            } else if (e instanceof DBSagasEntity s) {
                if (on) {
                    BEFORE.putIfAbsent(e.getId(), false);
                    s.setKiCharge(true);
                } else {
                    BEFORE.remove(e.getId());
                    s.setKiCharge(false);
                }
            }
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("Aura do DMZ: {}", t.toString());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || UNTIL.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            UNTIL.clear();
            BEFORE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = UNTIL.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) {
                it.remove();
                BEFORE.remove(en.getKey());
                continue;
            }
            if (now > en.getValue()) {
                it.remove();
                apply(e, false);
            } else apply(e, true);
        }
    }

    /** O servidor manda os dados do jogador de tempos em tempos; reforca logo antes de desenhar. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRender(RenderLivingEvent.Pre<?, ?> event) {
        if (!UNTIL.isEmpty() && UNTIL.containsKey(event.getEntity().getId())) apply(event.getEntity(), true);
    }
}
