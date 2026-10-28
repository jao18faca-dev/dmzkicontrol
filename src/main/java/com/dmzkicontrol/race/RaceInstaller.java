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
        changed |= ensureEyeSkill(c);
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
            String teacher = cfg.auraControlTeacher == null ? "" : cfg.auraControlTeacher.toLowerCase(java.util.Locale.ROOT);
            if (!teacher.isEmpty()) {
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
                KiControlMod.LOGGER.info("Habilidade '{}' adicionada ao {} (ensinada por '{}')", skill, file, teacher);
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
