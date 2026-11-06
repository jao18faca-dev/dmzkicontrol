package com.dmzkicontrol.party;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestAvailabilityChecker;
import com.dragonminez.common.quest.QuestParty;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Consertos do grupo (party) do DMZ 2.2.1:
 * - TP dividido: o DMZ so dividia com a Transmissao Instantanea (ver PartyTpShareMixin).
 * - Missoes: o DMZ so conta a missao para quem do grupo ja a tinha aceitado. Com a historia que comeca as
 *   missoes sozinha (Sparking/Ultra), muitas vezes o colega ainda nao tinha a missao aceita e tinha de fazer tudo
 *   de novo. Agora quem esta perto e ja podia fazer a missao tambem a termina; a recompensa fica para pegar.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PartyFixes {
    private PartyFixes() {}

    /** null = deixa a regra do DMZ. */
    public static Boolean canReceiveSharedTp(ServerPlayer earner, ServerPlayer member, StatsData memberData) {
        try {
            if (!KiControlConfig.get().party.fixTpShare) return null;
            if (earner == null || member == null || member == earner) return false;
            if (member.isSpectator() || member.isCreative()) return false;
            if (!QuestParty.inShareRange(earner, member)) return false;
            return memberData == null || !memberData.getStatus().isFused() || memberData.getStatus().isFusionLeader();
        } catch (Throwable t) {
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onQuestCompleted(DMZEvent.QuestCompletedEvent event) {
        if (event.isCanceled() || !KiControlConfig.get().party.shareQuestCompletion) return;
        ServerPlayer player = event.getPlayer();
        String key = event.getQuestKey();
        Quest quest = event.getQuest();
        if (player == null || key == null || quest == null) return;
        try {
            for (ServerPlayer member : PartyManager.getAllPartyMembers(player)) {
                if (member == null || member == player || !QuestParty.inShareRange(player, member)) continue;
                StatsData data = AuraSealManager.stats(member);
                if (data == null) continue;
                PlayerQuestData pqd = data.getPlayerQuestData();
                // quem ja aceitou o DMZ ja conta sozinho; quem ja terminou nao precisa
                if (pqd.isQuestCompleted(key) || pqd.isQuestAccepted(key)) continue;
                if (!canDo(key, quest, event.getSaga(), data)) continue;
                pqd.acceptQuest(key);
                pqd.completeQuest(key);
                QuestService.syncQuestState(member);
                member.sendSystemMessage(Component.translatable("message.dmzkicontrol.party_quest_shared", player.getDisplayName())
                        .withStyle(ChatFormatting.GREEN));
            }
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Grupo] missao {} nao dividida com o grupo: {}", key, t.toString());
        }
    }

    /** O colega ja podia fazer esta missao: na saga, a anterior feita (e a saga anterior, se for a primeira). */
    private static boolean canDo(String key, Quest quest, com.dragonminez.common.quest.Saga saga, StatsData data) {
        PlayerQuestData pqd = data.getPlayerQuestData();
        if (saga != null && saga.getQuests() != null) {
            int index = saga.getQuests().indexOf(quest);
            if (index >= 0) {
                if (!QuestAvailabilityChecker.isSagaQuestAvailable(quest, saga, index, data)) return false;
                if (index == 0) {
                    String prev = saga.getRequirements() != null ? saga.getRequirements().previousSagaId() : null;
                    if (prev != null && !prev.isBlank()) {
                        com.dragonminez.common.quest.Saga before = com.dragonminez.common.quest.QuestRegistry.getSaga(prev);
                        if (before != null && before.getQuests() != null && !before.getQuests().isEmpty()) {
                            Quest last = before.getQuests().get(before.getQuests().size() - 1);
                            if (!pqd.isQuestCompleted(PlayerQuestData.sagaQuestKey(prev, last.getId()))) return false;
                        }
                    }
                }
                return true;
            }
        }
        if (quest.hasPrerequisites()) return QuestAvailabilityChecker.arePrerequisitesMet(quest.getPrerequisites(), data);
        return true;
    }
}
