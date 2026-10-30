package com.dmzkicontrol.saga;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Radar de Cereal para quem ja passou da missao 11 da saga do Granolah (o Monaito entrega o radar).
 * Quem fez a missao antes da 3.12.2 nao recebeu o radar (o item ainda nao existia no jogo): recebe agora, uma vez.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CerealRadarGrant {
    private static final String FLAG = "dmzkicontrol_cereal_radar";
    private static final String SAGA = "dmzkicontrol_s2_granolah";
    private static final int QUEST = 11;

    private CerealRadarGrant() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) check(sp);
    }

    @SubscribeEvent
    public static void onQuest(DMZEvent.QuestCompletedEvent event) {
        try {
            ServerPlayer sp = event.getPlayer();
            if (sp != null) sp.server.execute(() -> check(sp));
        } catch (Throwable ignored) {}
    }

    private static void check(ServerPlayer sp) {
        try {
            CompoundTag persisted = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(FLAG)) return;
            StatsData data = AuraSealManager.stats(sp);
            if (data == null || !data.getPlayerQuestData().isQuestCompleted(PlayerQuestData.sagaQuestKey(SAGA, QUEST))) return;
            Item radar = ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", "cereal_dball_radar"));
            if (radar == null || radar == net.minecraft.world.item.Items.AIR) return;
            persisted.putBoolean(FLAG, true);
            sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            // a recompensa da missao tambem pode ter dado o radar agora: so entrega se ainda nao tiver
            if (sp.getInventory().contains(new ItemStack(radar))) return;
            if (!sp.getInventory().add(new ItemStack(radar))) sp.drop(new ItemStack(radar), false);
            sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.cereal_radar").withStyle(ChatFormatting.GREEN));
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("[Sagas] radar de Cereal: {}", t.toString());
        }
    }
}
