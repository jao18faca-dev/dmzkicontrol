package com.dmzkicontrol.saga;

import com.dmzkicontrol.KiControlMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Monaito e Granolah no Planeta Cereal: entram no world/dragonminez/npcs/placements.json do DMZ,
 * do mesmo jeito que o DMZ Sparking coloca os NPCs dele (o DMZ cria o NPC de missao no lugar).
 * Jaco, Merus e o Granolah da Terra vem pelos structure_npcs (casa do Goku e casa do Kame).
 */
final class CerealNpcs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CerealNpcs() {}

    private static List<JsonObject> ours() {
        return List.of(
                npc("dmzkicontrol_monaito", "monaito", "dmzk_saga_monaito", 0.0, 0.0, 180.0),
                npc("dmzkicontrol_granolah_cereal", "granolah", "dmzk_saga_granolah", 3.0, 1.0, 200.0));
    }

    private static JsonObject npc(String id, String npcId, String model, double x, double z, double yaw) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("entity", "dragonminez:quest_npc");
        o.addProperty("npc_id", npcId);
        o.addProperty("model", model);
        o.addProperty("texture", model);
        o.addProperty("dimension", "dmzkicontrol:cereal");
        o.addProperty("relative_to_spawn", true);
        o.addProperty("x", x);
        o.addProperty("y", 0.0);
        o.addProperty("z", z);
        o.addProperty("yaw", yaw);
        o.addProperty("surface", true);
        o.addProperty("enabled", true);
        o.addProperty("override", true);
        return o;
    }

    /** createIfMissing=false: so mexe se o DMZ ja criou o arquivo (antes de iniciar, para valer ja nesta abertura). */
    static void install(Path world, boolean createIfMissing) {
        Path file = world.resolve("dragonminez").resolve("npcs").resolve("placements.json");
        try {
            JsonObject root;
            if (Files.isRegularFile(file)) {
                root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                if (!createIfMissing) return;
                root = new JsonObject();
                root.addProperty("schema", 1);
                root.add("placements", new JsonArray());
            }
            if (!root.has("placements") || !root.get("placements").isJsonArray()) return;
            JsonArray list = root.getAsJsonArray("placements");
            int added = 0;
            for (JsonObject n : ours()) {
                boolean present = false;
                for (JsonElement e : list) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("id") && n.get("id").getAsString().equals(e.getAsJsonObject().get("id").getAsString())) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    list.add(n);
                    added++;
                }
            }
            if (added == 0) return;
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            KiControlMod.LOGGER.info("[Sagas] {} NPC(s) do Planeta Cereal registrados (Monaito e Granolah).", added);
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("[Sagas] NPCs do Planeta Cereal nao registrados: {}", e.toString());
        }
    }
}
