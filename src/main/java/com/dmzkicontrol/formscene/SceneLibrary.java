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

    /** Prefixo das versoes rapidas (3 s) usadas da segunda transformacao em diante. */
    public static final String QUICK = "quick:";
    private static final Map<String, JsonObject> QUICKS = new java.util.concurrent.ConcurrentHashMap<>();

    public static JsonObject get(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        if (k.startsWith(QUICK)) {
            String base = k.substring(QUICK.length());
            JsonObject full = all().get(base);
            return full == null ? null : QUICKS.computeIfAbsent(base, b -> quick(full));
        }
        return all().get(k);
    }

    /**
     * Versao rapida (60 ticks) de uma cena de transformacao: pega da cena completa a cor, a pose e os sons do
     * momento em que a forma explode, e monta um roteiro curto: close baixo com o poder subindo, estalo para um
     * plano aberto girando no instante da transformacao, e o nome da forma.
     */
    private static JsonObject quick(JsonObject full) {
        int climax = Integer.MAX_VALUE;
        for (JsonElement e : arr(full, "scr")) {
            var a = e.getAsJsonArray();
            if (a.get(2).getAsString().equals("flash")) climax = Math.min(climax, a.get(0).getAsInt());
        }
        if (climax == Integer.MAX_VALUE) {
            for (JsonElement e : arr(full, "fx")) {
                var a = e.getAsJsonArray();
                if (a.get(2).getAsString().equals("ring")) climax = Math.min(climax, a.get(0).getAsInt());
            }
        }
        if (climax == Integer.MAX_VALUE) climax = 30;
        String pose = "scream";
        int best = -1;
        for (JsonElement e : arr(full, "pose")) {
            var a = e.getAsJsonArray();
            int t = a.get(0).getAsInt();
            if (t <= climax + 2 && t > best) { best = t; pose = a.get(1).getAsString(); }
        }
        String col = "#FFFFFF";
        for (JsonElement e : arr(full, "fx")) {
            var a = e.getAsJsonArray();
            String kind = a.get(2).getAsString();
            if ((kind.equals("pillar") || kind.equals("glow") || kind.equals("ring")) && a.size() > 3 && a.get(3).isJsonObject()
                    && a.get(3).getAsJsonObject().has("c")) {
                col = a.get(3).getAsJsonObject().get("c").getAsString();
                if (kind.equals("pillar")) break;
            }
        }
        final int C = 16;
        StringBuilder b = new StringBuilder();
        b.append("{\"len\":60,");
        b.append("\"cam\":[[0,30,2.0,0.15,0.8,0.85,-5],[14,12,1.5,0.05,0.88,0.74,-2],[16,-30,4.2,0.9,0.55,1.08,6],[38,-48,3.4,0.5,0.6,1.0,2],[60,-60,3.3,0.5,0.6,1.0,0]],");
        b.append("\"pose\":[[0,\"clench\"],[").append(C).append(",\"").append(pose).append("\"],[46,\"stance\"]],");
        b.append("\"fx\":[");
        b.append("[0,16,\"glow\",{\"c\":\"").append(col).append("\",\"r\":0.6,\"a\":0.6,\"y\":0.55,\"pulse\":0.1}],");
        b.append("[0,15,\"streaks\",{\"c\":\"").append(col).append("\",\"n\":12,\"far\":2.6,\"out\":0,\"a\":0.55}],");
        b.append("[5,11,\"bolts\",{\"c\":\"").append(col).append("\",\"n\":3,\"a\":0.85}],");
        b.append("[").append(C).append(",16,\"ring\",{\"c\":\"").append(col).append("\",\"r0\":0.3,\"r1\":4.5,\"y\":0.05,\"w\":0.3,\"a\":0.8}],");
        b.append("[").append(C).append(",1,\"crater\",{\"n\":24,\"r\":2.0}],");
        b.append("[").append(C).append(",40,\"pillar\",{\"c\":\"").append(col).append("\",\"r\":0.8,\"h\":6,\"a\":0.6}],");
        b.append("[").append(C).append(",44,\"aura\",{\"c\":\"").append(col).append("\",\"s\":1.1,\"a\":0.6}],");
        b.append("[").append(C).append(",30,\"particles\",{\"p\":\"minecraft:end_rod\",\"n\":2,\"r\":0.9,\"up\":0.12,\"hgt\":1.0,\"in\":0.0}]");
        b.append("],");
        b.append("\"scr\":[[15,6,\"flash\",{\"c\":\"#FFFFFF\",\"a\":0.8}],[").append(C).append(",24,\"tint\",{\"c\":\"").append(col).append("\",\"a\":0.16}]],");
        b.append("\"snd\":[[0,\"dragonminez:ki_charge_loop\",0.5,1.3]");
        for (JsonElement e : arr(full, "snd")) {
            var a = e.getAsJsonArray();
            int t = a.get(0).getAsInt();
            if (Math.abs(t - climax) <= 2) {
                b.append(",[").append(C).append(",\"").append(a.get(1).getAsString()).append("\",")
                        .append(a.size() > 2 ? a.get(2).getAsDouble() : 1.0).append(",").append(a.size() > 3 ? a.get(3).getAsDouble() : 1.0).append("]");
            }
        }
        b.append("],");
        b.append("\"shake\":[[").append(C).append(",8,1.4]],");
        b.append("\"title\":[24,32],\"sub\":[28,28]}");
        return JsonParser.parseString(b.toString()).getAsJsonObject();
    }

    private static com.google.gson.JsonArray arr(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new com.google.gson.JsonArray();
    }

    /** Duracao em ticks. */
    public static int length(String id) {
        JsonObject s = get(id);
        return s != null && s.has("len") ? s.get("len").getAsInt() : 120;
    }
}
