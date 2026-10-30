package com.dmzkicontrol.mixin;

import com.dragonminez.common.dragonball.DragonBallPackManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        for (String file : DMZK_FILES) {
            String res = "/dmzkicontrol_dragonballs/cereal/" + file;
            // um pacote que o dono colocou na pasta dragonballs/ com o mesmo id tem prioridade
            try (InputStream in = DragonBallPackMixin.class.getResourceAsStream(res)) {
                if (in == null) continue;
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                String id = root.has("id") ? root.get("id").getAsString() : (root.has("dragon") ? root.get("dragon").getAsString() : "");
                if (file.equals("definitions/ballset.json") && loaded.ballSets.containsKey(id)) continue;
                if (file.equals("definitions/radar.json") && loaded.radars.containsKey(id)) continue;
                if (file.equals("definitions/dragon.json") && loaded.dragons.containsKey(id)) continue;
                if (file.equals("definitions/wishes.json") && loaded.wishes.containsKey(id)) continue;
                if (file.equals("assets/ballset.json") && loaded.ballSetAssets.containsKey(id)) continue;
                if (file.equals("assets/dragon.json") && loaded.dragonAssets.containsKey(id)) continue;
                readDefinition("dmzkicontrol/dragonballs/cereal/" + file, root, loaded);
            } catch (Throwable t) {
                System.err.println("[DragonMineZ Evolution] pacote das Esferas de Cereal: " + res + " nao lido: " + t);
            }
        }
    }
}
