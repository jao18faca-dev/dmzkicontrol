package com.dmzkicontrol.mixin;

import com.dragonminez.common.dragonball.DragonBallPackManager;
import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Esferas do Dragao de Cereal e o Toronbo: o DMZ 2.2 monta os conjuntos de esferas, radares e dragoes
 * a partir de "pacotes" (pasta dragonballs/ do jogo). Este mixin junta o pacote que vem dentro do add-on
 * toda vez que o DMZ le os pacotes - inclusive na inicializacao, quando ele registra os blocos das esferas,
 * o radar e a entidade do dragao. Assim nada precisa ser copiado para a pasta do jogo.
 */
@Mixin(value = DragonBallPackManager.class, remap = false)
public abstract class DragonBallPackMixin {
    private static final String[] DMZK_FILES = {
            "assets/ballset.json", "assets/dragon.json",
            "definitions/ballset.json", "definitions/radar.json", "definitions/dragon.json", "definitions/wishes.json"};

    @Shadow
    private static void readDefinition(String normalizedPath, JsonObject root, DragonBallPackManager.LoadedDefinitions loaded) {
        throw new AssertionError();
    }

    @Inject(method = "loadAll", at = @At("RETURN"), remap = false, require = 0)
    private static void dmzkicontrol$addCerealPack(CallbackInfoReturnable<DragonBallPackManager.LoadedDefinitions> cir) {
        DragonBallPackManager.LoadedDefinitions loaded = cir.getReturnValue();
        if (loaded == null) return;
        int added = 0;
        for (String file : DMZK_FILES) {
            try {
                JsonObject root = com.dmzkicontrol.compat.CerealPack.read(file);
                if (root == null) continue;
                String id = root.has("id") ? root.get("id").getAsString() : (root.has("dragon") ? root.get("dragon").getAsString() : "");
                // um pacote que o dono colocou na pasta dragonballs/ com o mesmo id tem prioridade
                if (file.equals("definitions/ballset.json") && loaded.ballSets.containsKey(id)) continue;
                if (file.equals("definitions/radar.json") && loaded.radars.containsKey(id)) continue;
                if (file.equals("definitions/dragon.json") && loaded.dragons.containsKey(id)) continue;
                if (file.equals("definitions/wishes.json") && loaded.wishes.containsKey(id)) continue;
                if (file.equals("assets/ballset.json") && loaded.ballSetAssets.containsKey(id)) continue;
                if (file.equals("assets/dragon.json") && loaded.dragonAssets.containsKey(id)) continue;
                readDefinition("dmzkicontrol/dragonballs/cereal/" + file, root, loaded);
                added++;
            } catch (Throwable t) {
                System.err.println("[DragonMineZ Evolution] pacote das Esferas de Cereal: " + file + " nao lido: " + t);
            }
        }
        System.out.println("[DragonMineZ Evolution] Esferas de Cereal e Toronbo: " + added + " definicoes adicionadas ao DMZ.");
    }
}
