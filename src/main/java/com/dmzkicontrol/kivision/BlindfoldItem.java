package com.dmzkicontrol.kivision;

import com.dmzkicontrol.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Venda de Treino: vai na cabeca. Com ela voce nao enxerga nada, so sente o ki de quem esta por perto
 * (visao de ki), como o Goku lutando contra o Tenshinhan depois do Taiyoken. Treinar assim em combate
 * rende mais TP.
 */
public class BlindfoldItem extends Item implements Equipable {

    public BlindfoldItem(Properties props) {
        super(props);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dmzkicontrol.blindfold.tip1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.dmzkicontrol.blindfold.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.dmzkicontrol.blindfold.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isWearing(LivingEntity e) {
        return e != null && e.getItemBySlot(EquipmentSlot.HEAD).is(ModRegistry.BLINDFOLD.get());
    }
}
