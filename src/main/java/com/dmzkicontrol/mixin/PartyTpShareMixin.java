package com.dmzkicontrol.mixin;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DMZ 2.2.1: o TP que o grupo divide passa por BabaReviveService.canReceiveSharedTp, que exige que quem ganhou
 * o TP tenha a Transmissao Instantanea e que o colega esteja no alcance dela. Sem a TI ninguem do grupo recebia
 * nada. Aqui a regra vira a mesma das missoes do grupo: mesmo mundo e dentro do alcance do grupo.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.server.util.BabaReviveService", remap = false)
public abstract class PartyTpShareMixin {
    @Inject(method = "canReceiveSharedTp", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void dmzkicontrol$shareWithoutTransmission(ServerPlayer earner, StatsData earnerData, ServerPlayer member, StatsData memberData,
                                                              CallbackInfoReturnable<Boolean> cir) {
        Boolean ok = com.dmzkicontrol.party.PartyFixes.canReceiveSharedTp(earner, member, memberData);
        if (ok != null) cir.setReturnValue(ok);
    }
}
