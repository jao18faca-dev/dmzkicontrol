package com.dmzkicontrol.story;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.quest.QuestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Chefes da historia mais fortes: mais vida e mais dano, por cima da dificuldade escolhida no DMZ (no "dificil"
 * os chefes ainda caiam rapido). Vale para todo inimigo de missao (marcado pelo DMZ com a missao), uma vez so.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SagaBossTuning {
    private static final String MARK = "dmzk_tuned";
    private static final List<LivingEntity> PENDING = new ArrayList<>();

    private SagaBossTuning() {}

    private static KiControlConfig.Story cfg() {
        return KiControlConfig.get().story;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.isCanceled()) return;
        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentData().contains(QuestService.QUEST_KEY_TAG) || le.getPersistentData().getBoolean(MARK)) return;
        if (cfg().bossHealthMultiplier == 1.0 && cfg().bossDamageMultiplier == 1.0) return;
        // aplica no tick seguinte: o DMZ termina de ajustar a vida da missao logo depois de colocar no mundo
        PENDING.add(le);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<LivingEntity> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (LivingEntity le : now) {
            if (!le.isAlive() || !(le.level() instanceof ServerLevel)) continue;
            if (le.getPersistentData().getBoolean(MARK)) continue;
            le.getPersistentData().putBoolean(MARK, true);
            double mult = Math.max(0.1, cfg().bossHealthMultiplier);
            AttributeInstance hp = le.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && mult != 1.0) {
                hp.setBaseValue(hp.getBaseValue() * mult);
                le.setHealth(le.getMaxHealth());
            }
        }
    }

    /** Dano dos inimigos de missao (golpes e ki: o dono do projetil conta). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        Entity src = event.getSource().getEntity();
        if (src == null || src.level().isClientSide()) return;
        if (!src.getPersistentData().getBoolean(MARK)) return;
        double mult = Math.max(0.1, cfg().bossDamageMultiplier);
        if (mult != 1.0) event.setAmount((float) (event.getAmount() * mult));
    }
}
