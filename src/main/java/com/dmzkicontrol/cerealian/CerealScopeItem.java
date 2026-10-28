package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Mira Cereal: a lente de atirador dos Cerealianos. Vai na cabeca (slot de capacete) ou no slot
 * "Head Tech" do Curios, o mesmo do scouter do DMZ.
 * Para Cerealianos: +1 ponto fraco no Olho Cereal, +16 blocos de alcance e metade do ki gasto.
 */
public class CerealScopeItem extends Item implements Equipable {

    public CerealScopeItem(Properties props) {
        super(props);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dmzkicontrol.cereal_scope.tip1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.dmzkicontrol.cereal_scope.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.dmzkicontrol.cereal_scope.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Equipada na cabeca ou no slot head_tech do Curios. */
    public static boolean isWearing(LivingEntity e) {
        if (e.getItemBySlot(EquipmentSlot.HEAD).is(ModRegistry.CEREAL_SCOPE.get())) return true;
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(e).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ModRegistry.CEREAL_SCOPE.get()))
                    .isPresent();
        } catch (Throwable t) {
            return false;
        }
    }
}
