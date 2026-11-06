package com.dmzkicontrol.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poe a camada do Freeza Dourado / Black Freeza no renderizador de jogador do DMZ (modelo GeckoLib dele). */
@Pseudo
@Mixin(targets = "com.dragonminez.client.render.DMZPlayerRenderer", remap = false)
public abstract class DmzPlayerRendererLayerMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dmzkicontrol$frostGodLayer(CallbackInfo ci) {
        try {
            Object self = this;
            if (self instanceof software.bernie.geckolib.renderer.GeoEntityRenderer renderer) {
                renderer.addRenderLayer(new com.dmzkicontrol.client.frostgod.FrostGodLayer(renderer));
            }
        } catch (Throwable t) {
            com.dmzkicontrol.compat.CerealPack.warn("[DragonMineZ Evolution] camada do Freeza Dourado/Black nao adicionada: " + t);
        }
    }
}
