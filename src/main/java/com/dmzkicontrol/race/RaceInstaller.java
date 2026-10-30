package com.dmzkicontrol.race;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.cerealian.CerealianEye;
import com.dmzkicontrol.config.KiControlConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Instala o conteudo do add-on nos arquivos de config do DragonMineZ:
 * <ul>
 *   <li>racas em {@code config/dragonminez/races/<raca>/} (so arquivos que faltam);</li>
 *   <li>a skill "Mira" (cereal_eye) no {@code skills.json} do DMZ, restrita aos Cerealianos;</li>
 *   <li>pequenas correcoes de padroes antigos que o usuario nunca mudou.</li>
 * </ul>
 * Edicoes do dono do servidor nunca sao sobrescritas.
 */
public final class RaceInstaller {
    private static final String[] CEREALIAN_FILES = {"character.json", "stats.json", "forms/cerealforms.json"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static boolean installedSomething;

    private RaceInstaller() {}

    public static boolean installedSomething() {
        return installedSomething;
    }

    private static Path dmzDir() {
        return FMLPaths.CONFIGDIR.get().resolve("dragonminez");
    }

    /** Na construcao do mod. */
    public static void installAll() {
        if (runAll()) installedSomething = true;
    }

    /** No ServerAboutToStart (o DMZ pode ter criado o skills.json so agora). true = mudou algo. */
    public static boolean installLate() {
        return runAll();
    }

    private static boolean runAll() {
        boolean aura = ensureAuraControlSkill(KiControlConfig.get());
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        if (!c.installRace) return aura;
        boolean changed = aura;
        changed |= install("cerealian", c.raceKey, CEREALIAN_FILES);
        changed |= patchCerealianDefaults(c.raceKey);
        changed |= addNewCerealianForms(c.raceKey);
        changed |= ensureEyeSkill(c);
        changed |= ensureRaceAccess(c.raceKey);
        return changed;
    }

    private static boolean install(String bundled, String raceKey, String[] files) {
        Path dir = dmzDir().resolve("races").resolve(raceKey);
        String version = dmzConfigVersion();
        boolean changed = false;
        for (String file : files) {
            Path target = dir.resolve(file);
            if (Files.exists(target)) continue;
            String res = "/dmzkicontrol_defaults/races/" + bundled + "/" + file;
            try (InputStream in = RaceInstaller.class.getResourceAsStream(res)) {
                if (in == null) {
                    KiControlMod.LOGGER.error("Arquivo padrao da raca nao encontrado no jar: {}", res);
                    continue;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (version != null) json = json.replace("\"configVersion\": \"2.2.0\"", "\"configVersion\": \"" + version + "\"");
                Files.createDirectories(target.getParent());
                Files.writeString(target, json, StandardCharsets.UTF_8);
                changed = true;
                KiControlMod.LOGGER.info("Raca '{}': instalado {}", raceKey, target);
            } catch (Exception e) {
                KiControlMod.LOGGER.error("Falha ao instalar {} da raca '{}'", file, raceKey, e);
            }
        }
        return changed;
    }

    /**
     * Formas novas da raca (Poder do Desejo e Alem do Limite) em quem ja tinha o cerealforms.json antigo:
     * acrescenta so as que faltam e os precos dos niveis novos. Cada forma entra uma vez so
     * (config/dmzkicontrol_state.json): se o dono apagar, nao volta.
     */
    private static boolean addNewCerealianForms(String raceKey) {
        Path dir = dmzDir().resolve("races").resolve(raceKey);
        Path formsFile = dir.resolve("forms").resolve("cerealforms.json");
        Path charFile = dir.resolve("character.json");
        Path stateFile = FMLPaths.CONFIGDIR.get().resolve("dmzkicontrol_state.json");
        if (!Files.exists(formsFile)) return false;
        try (InputStream in = RaceInstaller.class.getResourceAsStream("/dmzkicontrol_defaults/races/cerealian/forms/cerealforms.json");
             InputStream inChar = RaceInstaller.class.getResourceAsStream("/dmzkicontrol_defaults/races/cerealian/character.json")) {
            if (in == null || inChar == null) return false;
            JsonObject bundled = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("forms");
            JsonObject bundledChar = JsonParser.parseString(new String(inChar.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject state = Files.exists(stateFile) ? JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            JsonArray done = state.has("cerealianFormsAdded") && state.get("cerealianFormsAdded").isJsonArray() ? state.getAsJsonArray("cerealianFormsAdded") : new JsonArray();
            java.util.Set<String> doneSet = new java.util.HashSet<>();
            for (JsonElement e : done) doneSet.add(e.getAsString());

            JsonObject root = JsonParser.parseString(Files.readString(formsFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject forms = root.getAsJsonObject("forms");
            boolean formsDirty = false, stateDirty = false;
            for (String name : new String[]{"olhodesperto", "olhosgemeos"}) if (!doneSet.contains(name)) { done.add(name); doneSet.add(name); stateDirty = true; }
            for (java.util.Map.Entry<String, JsonElement> e : bundled.entrySet()) {
                if (doneSet.contains(e.getKey())) continue;
                if (!forms.has(e.getKey())) {
                    JsonObject f = e.getValue().getAsJsonObject().deepCopy();
                    // nivel logo depois do ultimo que o arquivo do dono ja tem
                    int max = 0;
                    for (java.util.Map.Entry<String, JsonElement> o : forms.entrySet()) {
                        JsonObject of = o.getValue().getAsJsonObject();
                        if (of.has("unlockOnSkillLevel")) max = Math.max(max, of.get("unlockOnSkillLevel").getAsInt());
                    }
                    f.addProperty("unlockOnSkillLevel", Math.max(f.get("unlockOnSkillLevel").getAsInt(), max + 1));
                    forms.add(e.getKey(), f);
                    formsDirty = true;
                    KiControlMod.LOGGER.info("Raca '{}': forma nova {} (nivel {})", raceKey, e.getKey(), f.get("unlockOnSkillLevel").getAsInt());
                }
                done.add(e.getKey());
                doneSet.add(e.getKey());
                stateDirty = true;
            }
            if (formsDirty) Files.writeString(formsFile, GSON.toJson(root), StandardCharsets.UTF_8);

            boolean charDirty = false;
            if (formsDirty && Files.exists(charFile)) {
                JsonObject ch = JsonParser.parseString(Files.readString(charFile, StandardCharsets.UTF_8)).getAsJsonObject();
                int levels = 0;
                for (java.util.Map.Entry<String, JsonElement> o : forms.entrySet()) {
                    JsonObject of = o.getValue().getAsJsonObject();
                    if (of.has("unlockOnSkillLevel")) levels = Math.max(levels, of.get("unlockOnSkillLevel").getAsInt());
                }
                JsonArray want = bundledChar.getAsJsonObject("formSkillsCosts").getAsJsonObject("superforms").getAsJsonArray("prices");
                JsonObject costs = ch.has("formSkillsCosts") ? ch.getAsJsonObject("formSkillsCosts") : new JsonObject();
                JsonObject sf = costs.has("superforms") ? costs.getAsJsonObject("superforms") : new JsonObject();
                JsonArray prices = sf.has("prices") && sf.get("prices").isJsonArray() ? sf.getAsJsonArray("prices") : new JsonArray();
                while (prices.size() < levels) {
                    int i = prices.size();
                    long last = prices.size() == 0 ? 16000 : prices.get(prices.size() - 1).getAsLong();
                    prices.add(i < want.size() ? want.get(i).getAsLong() : last * 2);
                    charDirty = true;
                }
                if (charDirty) {
                    sf.add("prices", prices);
                    costs.add("superforms", sf);
                    ch.add("formSkillsCosts", costs);
                    Files.writeString(charFile, GSON.toJson(ch), StandardCharsets.UTF_8);
                }
            }
            if (stateDirty) {
                state.add("cerealianFormsAdded", done);
                Files.writeString(stateFile, GSON.toJson(state), StandardCharsets.UTF_8);
            }
            return formsDirty || charDirty;
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("Nao consegui acrescentar as formas novas dos Cerealianos: {}", e.toString());
            return false;
        }
    }

    /** Cabelo verde-agua: atualiza o padrao antigo (so se ainda estiver no valor original). */
    private static boolean patchCerealianDefaults(String raceKey) {
        Path file = dmzDir().resolve("races").resolve(raceKey).resolve("character.json");
        if (!Files.exists(file)) return false;
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean dirty = false;
            JsonElement hair = obj.get("defaultHairColor");
            if (hair != null && "#F1E4DF".equalsIgnoreCase(hair.getAsString())) {
                obj.addProperty("defaultHairColor", "#5FD3C4");
                dirty = true;
            }
            // versao anterior herdava as orelhas da Glind ("ears1"); os Cerealianos tem as proprias
            JsonElement bones = obj.get("headBones");
            if (bones != null && bones.isJsonArray() && bones.toString().replace(" ", "").equals("[\"ears1\",\"hair\"]")) {
                JsonArray only = new JsonArray();
                only.add("hair");
                obj.add("headBones", only);
                dirty = true;
            }
            if (dirty) {
                Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
                return true;
            }
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("Nao consegui atualizar {}: {}", file, e.toString());
        }
        return false;
    }

    /**
     * Habilidade Aura Control no skills.json do DMZ: custos dos 5 niveis e o mestre que ensina (o Goku,
     * na lista "skillOfferings"). So adiciona o que falta.
     */
    private static boolean ensureAuraControlSkill(KiControlConfig cfg) {
        String skill = cfg.requiredSkill;
        if (skill == null || skill.isBlank() || !skill.equalsIgnoreCase("aura_control")) return false;
        Path file = dmzDir().resolve("skills.json");
        if (!Files.exists(file)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean changed = false;
            JsonObject skills = root.has("skills") && root.get("skills").isJsonObject() ? root.getAsJsonObject("skills") : new JsonObject();
            if (!skills.has(skill)) {
                JsonObject entry = new JsonObject();
                JsonArray costs = new JsonArray();
                for (Integer cost : cfg.auraControlCosts) costs.add(cost);
                entry.add("costs", costs);
                entry.add("allowedRaces", new JsonArray());
                skills.add(skill, entry);
                root.add("skills", skills);
                changed = true;
            }
            java.util.Set<String> teachers = new java.util.LinkedHashSet<>();
            if (cfg.auraControlTeacher != null && !cfg.auraControlTeacher.isBlank()) teachers.add(cfg.auraControlTeacher.toLowerCase(java.util.Locale.ROOT));
            if (cfg.auraControlTeachers != null) for (String t : cfg.auraControlTeachers) if (t != null && !t.isBlank()) teachers.add(t.toLowerCase(java.util.Locale.ROOT));
            for (String teacher : teachers) {
                JsonObject offerings = root.has("skillOfferings") && root.get("skillOfferings").isJsonObject()
                        ? root.getAsJsonObject("skillOfferings") : new JsonObject();
                JsonArray list = offerings.has(teacher) && offerings.get(teacher).isJsonArray() ? offerings.getAsJsonArray(teacher) : new JsonArray();
                boolean present = false;
                for (JsonElement el : list) if (el.isJsonPrimitive() && skill.equalsIgnoreCase(el.getAsString())) present = true;
                if (!present) {
                    list.add(skill);
                    offerings.add(teacher, list);
                    root.add("skillOfferings", offerings);
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
                KiControlMod.LOGGER.info("Habilidade '{}' adicionada ao {} (ensinada por {})", skill, file, teachers);
            }
            return changed;
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("Nao consegui adicionar o Aura Control ao {}: {}", file, e.toString());
            return false;
        }
    }

    /** Adiciona a skill "Mira" ao skills.json do DMZ (custos por nivel e so para Cerealianos). */
    private static boolean ensureEyeSkill(KiControlConfig.Cerealian c) {
        Path file = dmzDir().resolve("skills.json");
        if (!Files.exists(file)) return false; // o DMZ ainda vai criar; tentamos de novo no ServerAboutToStart
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject skills = root.has("skills") && root.get("skills").isJsonObject() ? root.getAsJsonObject("skills") : new JsonObject();
            if (skills.has(CerealianEye.SKILL)) return false;
            JsonObject entry = new JsonObject();
            JsonArray costs = new JsonArray();
            for (Integer cost : c.eyeSkillCosts) costs.add(cost);
            JsonArray races = new JsonArray();
            races.add(c.raceKey);
            entry.add("costs", costs);
            entry.add("allowedRaces", races);
            skills.add(CerealianEye.SKILL, entry);
            root.add("skills", skills);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            KiControlMod.LOGGER.info("Skill '{}' adicionada ao {}", CerealianEye.SKILL, file);
            return true;
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("Nao consegui adicionar a skill da Mira ao {}: {}", file, e.toString());
            return false;
        }
    }

    /** Racas "mortais" de base: uma habilidade liberada para todas elas e para todo mundo (ex.: Ki Divino). */
    private static final String[] MORTAL_RACES = {"saiyan", "human", "namekian", "frostdemon", "majin", "bioandroid"};

    /**
     * Outros add-ons (DMZ Sparking, Dragon Block Noea) gravam listas de racas nas habilidades que liberam para
     * "todo mundo" (Ki Divino, formas criadas...), mas so com as racas que conheciam. Se a lista tem todas as
     * racas mortais de base e nao tem a nossa, a nossa entra. Habilidades exclusivas de uma raca nao mudam.
     */
    private static boolean ensureRaceAccess(String raceKey) {
        Path file = dmzDir().resolve("skills.json");
        if (!Files.exists(file) || raceKey == null || raceKey.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("skills") || !root.get("skills").isJsonObject()) return false;
            java.util.List<String> added = new java.util.ArrayList<>();
            for (var e : root.getAsJsonObject("skills").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject skill = e.getValue().getAsJsonObject();
                if (!skill.has("allowedRaces") || !skill.get("allowedRaces").isJsonArray()) continue;
                JsonArray races = skill.getAsJsonArray("allowedRaces");
                if (races.isEmpty()) continue; // vazia = todas as racas
                java.util.Set<String> have = new java.util.HashSet<>();
                for (var r : races) have.add(r.getAsString().toLowerCase(java.util.Locale.ROOT));
                if (have.contains("all") || have.contains(raceKey)) continue;
                boolean everyone = true;
                for (String m : MORTAL_RACES) if (!have.contains(m)) { everyone = false; break; }
                if (!everyone) continue;
                races.add(raceKey);
                added.add(e.getKey());
            }
            if (added.isEmpty()) return false;
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            KiControlMod.LOGGER.info("Raca '{}' liberada nas habilidades de todas as racas: {}", raceKey, added);
            return true;
        } catch (Exception e) {
            KiControlMod.LOGGER.warn("Nao consegui liberar a raca {} no {}: {}", raceKey, file, e.toString());
            return false;
        }
    }

    /** Versao de config do DMZ instalado, lida em tempo de execucao. */
    private static String dmzConfigVersion() {
        try {
            Object v = Class.forName("com.dragonminez.common.config.ConfigManager").getField("CONFIG_VERSION").get(null);
            return v instanceof String str ? str : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
