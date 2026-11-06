package com.dmzkicontrol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DMZ Ultra 0.4: tudo o que ele tem de Bleach (racas Shinigami e Fullbringer, Hollows, NPCs, sagas e missoes,
 * formas, tecnicas, itens, telas, teclas e comandos) passa por um interruptor geral, BleachSwitch.configured().
 * Desligado, o proprio Ultra tira tudo isso do jogo sem apagar nada do mundo. Aqui ele fica desligado
 * (config ultra.removeBleach do DragonMineZ Evolution), para o pacote ficar so Dragon Ball.
 */
@Pseudo
@Mixin(targets = "com.dmzultra.r3.bleach.BleachSwitch", remap = false)
public abstract class UltraBleachSwitchMixin {
    @Inject(method = "configured", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void dmzkicontrol$noBleach(CallbackInfoReturnable<Boolean> cir) {
        if (com.dmzkicontrol.compat.UltraCompat.bleachOff()) cir.setReturnValue(false);
    }
}
