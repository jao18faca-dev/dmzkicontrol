package com.dmzkicontrol.formscene;

import com.dmzkicontrol.KiControlMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Roteiros das cutscenes de transformacao (um por forma), em /dmzkicontrol_cutscenes/scenes.json dentro do jar.
 * Chaves: "raca:grupo.forma" (ex.: "saiyan:supersaiyan.supersaiyan3"), "*:grupo.forma" para formas de qualquer raca
 * (Kaioken, Instinto Superior...), "group:grupo" e "generic" como ultimo recurso para formas que o add-on nao conhece
 * (formas criadas pelo jogador, formas de outros add-ons).
 */
public final class SceneLibrary {
    private static Map<String, JsonObject> scenes;

    private SceneLibrary() {}

    public static synchronized Map<String, JsonObject> all() {
        if (scenes == null) load();
        return scenes;
    }

    private static void load() {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        try (InputStream in = SceneLibrary.class.getResourceAsStream("/dmzkicontrol_cutscenes/scenes.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonObject()) map.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            KiControlMod.LOGGER.error("[Cutscenes] roteiros nao carregados: {}", e.toString());
        }
        scenes = Collections.unmodifiableMap(map);
        KiControlMod.LOGGER.info("[Cutscenes] {} roteiros de transformacao carregados.", map.size());
    }

    /** Id do roteiro para esta forma (nunca null: cai no generico). */
    public static String resolve(String race, String group, String form) {
        Map<String, JsonObject> all = all();
        String r = race == null ? "" : race.toLowerCase(Locale.ROOT);
        String g = group == null ? "" : group.toLowerCase(Locale.ROOT);
        String f = form == null ? "" : form.toLowerCase(Locale.ROOT);
        String[] tries = {r + ":" + g + "." + f, "*:" + g + "." + f, "group:" + g, "generic"};
        for (String t : tries) if (all.containsKey(t)) return t;
        return "generic";
    }

    public static JsonObject get(String id) {
        return id == null ? null : all().get(id.toLowerCase(Locale.ROOT));
    }

    /** Duracao em ticks. */
    public static int length(String id) {
        JsonObject s = get(id);
        return s != null && s.has("len") ? s.get("len").getAsInt() : 120;
    }
}
