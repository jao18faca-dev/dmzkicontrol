package com.dmzkicontrol.mixin;

import com.dmzkicontrol.compat.IntegrationHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dragon Block Noea (ButterJaffa): o Instinto Superior dele tem um desvio automatico proprio
 * ({@code UltraCombatEvents.rollDodge}). Com o DMZ Sparking instalado, o Instinto Superior ja desvia
 * pelo sistema do Sparking, entao aqui o do Noea responde "nao desviou" e nada mais muda: a raiva do
 * Ultra Ego e o resto do Noea continuam iguais.
 *
 * <p>{@link Pseudo}: se o Noea nao estiver instalado, este mixin simplesmente nao e aplicado.
 */
@Pseudo
@Mixin(targets = "com.butterjaffa.noeabosses.UltraCombatEvents", remap = false)
public abstract class NoeaUltraDodgeMixin {
    @Inject(method = "rollDodge", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void dmzkicontrol$onlySparkingDodge(Player player, Entity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (IntegrationHooks.blockNoeaUltraDodge()) cir.setReturnValue(false);
    }
}
