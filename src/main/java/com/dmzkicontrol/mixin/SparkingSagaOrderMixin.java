package com.dmzkicontrol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * DMZ Sparking: a historia principal dele (paginas Agora e Episodios, a escolha da proxima missao,
 * a ordem da arvore de sagas) so anda pelas sagas da lista SagaOrder.MANDATORY. As sagas do Moro e do
 * Granolah ficavam fora dela - escondidas no fim da arvore e nunca oferecidas pela historia.
 * Aqui as duas entram na lista, logo depois do Torneio do Poder.
 */
@Pseudo
@Mixin(targets = "com.yami.dmzsparking.story.SagaOrder", remap = false)
public abstract class SparkingSagaOrderMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/List;of([Ljava/lang/Object;)Ljava/util/List;"),
            index = 0, remap = false, require = 0)
    private static Object[] dmzkicontrol$addMoroAndGranolah(Object[] order) {
        return com.dmzkicontrol.compat.SparkingStory.extend(order);
    }
}
