package com.dmzkicontrol.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Le os arquivos do pacote das Esferas de Cereal de dentro do jar do add-on.
 * Precisa ser uma classe do add-on: o codigo do mixin roda dentro da classe do DMZ, e uma classe do DMZ
 * so enxerga os arquivos do proprio jar (cada mod e um modulo separado no Forge).
 */
public final class CerealPack {
    private CerealPack() {}

    public static JsonObject read(String file) {
        String res = "/dmzkicontrol_dragonballs/cereal/" + file;
        try (InputStream in = CerealPack.class.getResourceAsStream(res)) {
            if (in == null) {
                System.err.println("[DragonMineZ Evolution] pacote das Esferas de Cereal: " + res + " nao encontrado no jar");
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // com o DMZ Ultra, as esferas ficam no Planeta Cereal dele (o deste add-on nao aparece na nave)
            String dim = UltraCompat.cerealDimension();
            if (!UltraCompat.OUR_CEREAL.equals(dim)) text = text.replace("\"" + UltraCompat.OUR_CEREAL + "\"", "\"" + dim + "\"");
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            System.err.println("[DragonMineZ Evolution] pacote das Esferas de Cereal: " + res + " nao lido: " + e);
            return null;
        }
    }
}
