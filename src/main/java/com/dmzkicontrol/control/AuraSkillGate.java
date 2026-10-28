package com.dmzkicontrol.control;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * O Goku so ensina o Aura Control depois que o jogador termina a Saga do Freeza.
 *
 * <p>A compra acontece na loja do mestre do proprio DMZ (sem mixin, nao da para esconder o item da
 * lista), entao conferimos depois: quem comprou antes da hora recebe os TP de volta e uma fala do Goku.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuraSkillGate {
    private AuraSkillGate() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp) || sp.tickCount % 40 != 7) return;
        KiControlConfig cfg = KiControlConfig.get();
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return;
        clearRemovedForms(sp, data);
        String skill = cfg.requiredSkill;
        if (skill == null || skill.isBlank() || !skill.equalsIgnoreCase("aura_control")) return;
        int level = data.getSkills().getSkillLevel(skill);
        if (level <= 0 || sagaRequirementMet(data, cfg.auraControlRequiredSaga)) {
            if (level > 0) level = applyStoryCap(sp, data, skill, level, cfg);
            mentorLines(sp, level);
            return;
        }

        // devolve o que foi pago e tira a habilidade
        double refund = 0;
        List<Integer> costs = cfg.auraControlCosts;
        for (int i = 0; i < level && costs != null && i < costs.size(); i++) if (costs.get(i) != null) refund += Math.max(0, costs.get(i));
        data.getSkills().setSkillLevel(skill, 0);
        if (refund > 0) data.getResources().setTrainingPointsExact(data.getResources().getTrainingPointsExact() + refund);
        AuraSealManager.release(sp, AuraSealManager.ReleaseReason.SILENT);
        StatsDeltaSync.markDirty(sp);
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.aura_teacher_locked").withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Teto pela historia (ex.: sagas do DMZ Sparking): o nivel 5, o controle perfeito, so vem depois de
     * enfrentar o Black. Quem subiu antes da hora volta ao teto e recebe os TP dos niveis excedentes.
     */
    private static int applyStoryCap(ServerPlayer sp, StatsData data, String skill, int level, KiControlConfig cfg) {
        int cap = 5;
        String blocking = null;
        for (int lv = 2; lv <= 5; lv++) {
            String req = cfg.auraLevelStoryRequirements.get(String.valueOf(lv));
            if (req == null || req.isBlank()) continue;
            String[] parts = req.split(":");
            int quest = parts.length > 1 ? parseInt(parts[1]) : 0;
            if (!questDone(data, parts[0].trim(), quest)) { cap = lv - 1; blocking = parts[0].trim(); break; }
        }
        if (level <= cap) return level;
        double refund = 0;
        List<Integer> costs = cfg.auraControlCosts;
        for (int i = cap; i < level && costs != null && i < costs.size(); i++) if (costs.get(i) != null) refund += Math.max(0, costs.get(i));
        data.getSkills().setSkillLevel(skill, cap);
        if (refund > 0) data.getResources().setTrainingPointsExact(data.getResources().getTrainingPointsExact() + refund);
        StatsDeltaSync.markDirty(sp);
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.aura_story_cap", cap + 1,
                Component.translatable("dmz.saga." + blocking)).withStyle(ChatFormatting.GOLD), false);
        return cap;
    }

    private static int parseInt(String v) {
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** A missao (ou a saga inteira, com 0) esta completa? Saga inexistente ou modo historia desligado = sim. */
    public static boolean questDone(StatsData data, String sagaId, int questId) {
        if (sagaId == null || sagaId.isBlank()) return true;
        try {
            var gameplay = com.dragonminez.common.config.ConfigManager.getServerConfig().getGameplay();
            if (Boolean.FALSE.equals(gameplay.getStoryModeEnabled())) return true;
            var saga = com.dragonminez.common.quest.QuestRegistry.getSaga(sagaId);
            if (saga == null || saga.getQuests() == null || saga.getQuests().isEmpty()) return true;
            int id = questId;
            if (id <= 0 || saga.getQuestById(id) == null) id = saga.getQuests().get(saga.getQuests().size() - 1).getId();
            return data.getPlayerQuestData().isQuestCompleted(com.dragonminez.common.quest.PlayerQuestData.sagaQuestKey(sagaId, id));
        } catch (Throwable t) {
            return true;
        }
    }

    private static final java.util.Map<java.util.UUID, Integer> KNOWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** Uma fala do Goku sempre que o Aura Control sobe de nivel (na entrada so memoriza o nivel). */
    private static void mentorLines(ServerPlayer sp, int level) {
        Integer before = KNOWN.put(sp.getUUID(), level);
        if (before == null || level <= before || level < 1 || level > 5) return;
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.goku_level_" + level).withStyle(ChatFormatting.AQUA), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        KNOWN.remove(event.getEntity().getUUID());
    }

    /**
     * Se a curadoria tirou a forma em que o jogador estava (ex.: SSJ5, Rose 3), ele volta ao normal em vez
     * de ficar numa forma que nao existe mais.
     */
    private static void clearRemovedForms(ServerPlayer sp, StatsData data) {
        try {
            var c = data.getCharacter();
            boolean dirty = false;
            if (c.hasActiveForm() && c.getActiveFormData() == null) { c.clearActiveForm(sp); dirty = true; }
            if (c.hasActiveStackForm() && c.getActiveStackFormData() == null) { c.clearActiveStackForm(sp); dirty = true; }
            if (dirty) {
                StatsDeltaSync.markDirty(sp);
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.form_removed").withStyle(ChatFormatting.GRAY), true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** A ultima missao da saga exigida esta completa? (sem saga, sem modo historia ou saga inexistente = liberado) */
    public static boolean sagaRequirementMet(StatsData data, String sagaId) {
        if (sagaId == null || sagaId.isBlank()) return true;
        try {
            var gameplay = com.dragonminez.common.config.ConfigManager.getServerConfig().getGameplay();
            if (Boolean.FALSE.equals(gameplay.getStoryModeEnabled())) return true;
            var saga = com.dragonminez.common.quest.QuestRegistry.getSaga(sagaId);
            if (saga == null || saga.getQuests() == null || saga.getQuests().isEmpty()) return true;
            int last = saga.getQuests().get(saga.getQuests().size() - 1).getId();
            return data.getPlayerQuestData().isQuestCompleted(com.dragonminez.common.quest.PlayerQuestData.sagaQuestKey(sagaId, last));
        } catch (Throwable t) {
            return true;
        }
    }
}
