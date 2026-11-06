package com.dmzkicontrol.saga;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.compat.UltraCompat;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Com o DMZ Ultra as sagas do Moro e do Granolah sao as dele. As missoes sao as mesmas (mesma ordem, mesmos
 * numeros), entao quem ja tinha feito as deste add-on fica com as do Ultra feitas tambem, sem receber a
 * recompensa de novo. E os NPCs de saga deste add-on que ja estavam no mundo saem (o Ultra coloca os dele).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltraSagaMigration {
    private UltraSagaMigration() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !UltraCompat.ultraSagas()) return;
        sp.server.execute(() -> migrate(sp));
    }

    private static void migrate(ServerPlayer sp) {
        try {
            StatsData data = AuraSealManager.stats(sp);
            if (data == null) return;
            PlayerQuestData quests = data.getPlayerQuestData();
            List<String> todo = new ArrayList<>();
            for (String key : quests.getCompletedQuestIds()) {
                int colon = key.lastIndexOf(':');
                if (colon <= 0) continue;
                String ultra = UltraCompat.ultraOf(key.substring(0, colon));
                if (ultra == null) continue;
                String target = ultra + key.substring(colon);
                if (!quests.isQuestCompleted(target)) todo.add(target);
            }
            if (todo.isEmpty()) return;
            for (String target : todo) quests.completeWithoutRewards(target, 8);
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(sp), sp);
            sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.ultra_sagas_migrated", todo.size()).withStyle(ChatFormatting.GOLD));
            KiControlMod.LOGGER.info("[Sagas] {}: {} missao(oes) do Moro/Granolah passadas para as sagas do DMZ Ultra.", sp.getGameProfile().getName(), todo.size());
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Sagas] progresso das sagas nao passado para o DMZ Ultra: {}", t.toString());
        }
    }

    /** NPC de saga deste add-on que ja estava no mundo: nao entra (Jaco, Merus, Granolah e Monaito vem do Ultra). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !UltraCompat.ultraSagas()) return;
        try {
            String id = event.getEntity().getPersistentData().getString("dmz_npc_placement_id");
            if (!id.isEmpty() && CerealNpcs.ALL_IDS.contains(id)) {
                event.setCanceled(true);
                event.getEntity().discard();
            }
        } catch (Throwable ignored) {}
    }
}
