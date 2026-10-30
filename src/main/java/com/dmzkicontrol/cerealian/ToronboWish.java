package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * O desejo ao Toronbo (dragao das Esferas de Cereal): "quero ser o guerreiro mais forte do universo".
 * Como no manga, o Toronbo so consegue dar o poder que o corpo aguenta: para um Cerealiano ele libera o
 * Poder do Desejo (nivel 3 das formas da raca, que nao se compra com TP) e cobra anos de vida - a vida
 * maxima cai de vez. Para as outras racas ele so desperta parte do potencial (TP).
 * O desejo chama "/dmzkicontrol desejo_toronbo &lt;jogador&gt;" (desejo do tipo comando do DMZ).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ToronboWish {
    private static final UUID COST_ID = UUID.fromString("7c1f6a52-3b9e-4d0e-9a51-0e6f2a7d9c41");
    private static final String FLAG = "dmzkicontrol_toronbo_wish";
    private static final String FORM_SKILL = "superforms";
    private static final int WISH_LEVEL = 3;

    private ToronboWish() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzkicontrol")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("desejo_toronbo")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))));

    }

    private static int grant(CommandSourceStack src, ServerPlayer sp) {
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return 0;
        if (!CerealianEye.isCerealian(sp)) {
            // o Toronbo so pode dar o que ja existe dentro de quem pede
            int tp = KiControlConfig.get().cerealian.toronboOtherRaceTp;
            data.getResources().addTrainingPoints(tp);
            sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.toronbo.not_cerealian", tp).withStyle(ChatFormatting.AQUA));
            return 1;
        }
        if (data.getBaseSkills().getSkillLevel(FORM_SKILL) >= WISH_LEVEL || sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(FLAG)) {
            int tp = KiControlConfig.get().cerealian.toronboOtherRaceTp;
            data.getResources().addTrainingPoints(tp);
            sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.toronbo.already", tp).withStyle(ChatFormatting.AQUA));
            return 1;
        }
        data.grantSkillLevel(FORM_SKILL, WISH_LEVEL);
        NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(sp), sp);
        CompoundTag persisted = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(FLAG, true);
        sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        applyCost(sp);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f);
        sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.toronbo.granted").withStyle(ChatFormatting.GREEN));
        int pct = (int) Math.round(KiControlConfig.get().cerealian.toronboLifeCost * 100);
        if (pct > 0) sp.sendSystemMessage(Component.translatable("message.dmzkicontrol.toronbo.cost", pct).withStyle(ChatFormatting.RED));
        KiControlMod.LOGGER.info("[Toronbo] {} fez o desejo: Poder do Desejo liberado.", sp.getGameProfile().getName());
        return 1;
    }

    /** Anos de vida dados ao Toronbo: vida maxima reduzida para sempre (some se o custo for 0 na config). */
    private static void applyCost(ServerPlayer sp) {
        AttributeInstance hp = sp.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        boolean wished = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(FLAG);
        double cost = KiControlConfig.get().cerealian.toronboLifeCost;
        AttributeModifier current = hp.getModifier(COST_ID);
        if (!wished || cost <= 0) {
            if (current != null) hp.removeModifier(COST_ID);
            return;
        }
        if (current != null && Math.abs(current.getAmount() + cost) < 1e-6) return;
        if (current != null) hp.removeModifier(COST_ID);
        hp.addPermanentModifier(new AttributeModifier(COST_ID, "Desejo ao Toronbo", -cost, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) applyCost(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) applyCost(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) applyCost(sp);
    }
}
