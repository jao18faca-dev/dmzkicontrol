package com.dmzkicontrol.mixin;

import com.dmzkicontrol.compat.IntegrationHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dragon Block Noea (ButterJaffa): o Ultra Ego so pode ser escolhido depois da linha de Deus da Destruicao
 * DO NOEA. Com o DMZ Sparking, o Ultra Ego vem da carreira de destruidor do Beerus no Sparking, que o Noea
 * nao reconhece: o Ultra Ego aparecia comprado e nao dava para transformar. Aqui, quem ja tem o Ultra Ego
 * pelo Sparking tambem passa no portao do Noea.
 */
@Pseudo
@Mixin(targets = "com.butterjaffa.noeabosses.UltraEgoUnlockPolicy", remap = false)
public abstract class NoeaUltraEgoGateMixin {
    @Inject(method = "canPurchase", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void dmzkicontrol$acceptSparkingDestroyer(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) && IntegrationHooks.ultraEgoFromSparking(player)) cir.setReturnValue(true);
    }
}
