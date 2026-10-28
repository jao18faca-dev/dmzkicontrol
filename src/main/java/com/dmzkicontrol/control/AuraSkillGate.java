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
        String skill = cfg.requiredSkill;
        if (skill == null || skill.isBlank() || !skill.equalsIgnoreCase("aura_control")) return;
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return;
        int level = data.getSkills().getSkillLevel(skill);
        if (level <= 0 || sagaRequirementMet(data, cfg.auraControlRequiredSaga)) {
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
