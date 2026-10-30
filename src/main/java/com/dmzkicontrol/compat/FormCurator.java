package com.dmzkicontrol.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Curadoria das formas quando o DragonMineZ roda junto com o DMZ Sparking e/ou o Dragon Block Noea.
 *
 * <p>Os dois add-ons instalam formas nos arquivos de config do DMZ (e recolocam o que falta a cada
 * inicio). Esta classe roda DEPOIS deles e deixa um conjunto unico e coerente:
 * <ol>
 *   <li>remove formas e grupos fan-made (lista em {@code config/dmzkicontrol_forms.json});</li>
 *   <li>remove repeticoes (ex.: o Rose que existe em tres grupos) mantendo a primeira da lista;</li>
 *   <li>conserta pre-requisitos que apontavam para formas removidas;</li>
 *   <li>fecha buracos nos niveis da habilidade de forma e corta precos de niveis que ficaram vazios;</li>
 *   <li>aplica o balanceamento e o visual da tabela "tune".</li>
 * </ol>
 * Grupos removidos sao movidos para {@code config/dragonminez/_removidos_evolution} (nada e apagado).
 * Idempotente: quando nao ha nada a mudar, nao escreve nenhum arquivo.
 */
public final class FormCurator {
    private static final String FILE = "dmzkicontrol_forms.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("dmzkicontrol");

    private FormCurator() {}

    private static final class FormFile {
        final Path path;
        final String race; // null = forma global (stack)
        final JsonObject root;
        final JsonObject forms;
        final String group;
        final String formType;
        boolean dirty;
        boolean delete;

        FormFile(Path path, String race, JsonObject root) {
            this.path = path;
            this.race = race;
            this.root = root;
            this.forms = root.has("forms") && root.get("forms").isJsonObject() ? root.getAsJsonObject("forms") : new JsonObject();
            String g = str(root, "groupName");
            if (g.isEmpty()) g = path.getFileName().toString().replace(".json", "");
            this.group = g.toLowerCase(Locale.ROOT);
            String t = str(root, "formType");
            this.formType = (t.isEmpty() ? this.group : t).toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------ config

    /** Le (ou cria a partir do padrao do jar) a curadoria. null = desligada/ilegivel. */
    private static JsonObject loadCuration(Path configDir) {
        Path file = configDir.resolve(FILE);
        try {
            if (!Files.exists(file)) {
                try (InputStream in = FormCurator.class.getResourceAsStream("/dmzkicontrol_defaults/form_curation.json")) {
                    if (in == null) return null;
                    Files.writeString(file, new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                }
            }
            JsonObject cfg = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (upgrade(cfg)) Files.writeString(file, GSON.toJson(cfg), StandardCharsets.UTF_8);
            if (cfg.has("enabled") && !cfg.get("enabled").getAsBoolean()) return null;
            return cfg;
        } catch (Exception e) {
            LOGGER.warn("Curadoria de formas: nao consegui ler {} ({}); nada foi alterado.", file, e.toString());
            return null;
        }
    }

    /**
     * Arquivo de curadoria de uma versao antiga: acrescenta so as entradas NOVAS do padrao do jar
     * (formas/grupos a remover, repeticoes e ajustes que o arquivo ainda nao tem). O que o dono mudou fica.
     */
    private static boolean upgrade(JsonObject cfg) {
        try (InputStream in = FormCurator.class.getResourceAsStream("/dmzkicontrol_defaults/form_curation.json")) {
            if (in == null) return false;
            JsonObject def = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            int have = cfg.has("version") ? cfg.get("version").getAsInt() : 1;
            int want = def.has("version") ? def.get("version").getAsInt() : 1;
            if (have >= want) return false;
            for (String key : new String[]{"removeForms", "removeGroups", "dedupe"}) {
                if (!def.has(key)) continue;
                if (!cfg.has(key) || !cfg.get(key).isJsonArray()) cfg.add(key, new com.google.gson.JsonArray());
                com.google.gson.JsonArray mine = cfg.getAsJsonArray(key);
                for (JsonElement e : def.getAsJsonArray(key)) if (!mine.contains(e)) mine.add(e);
            }
            if (def.has("tune")) {
                if (!cfg.has("tune") || !cfg.get("tune").isJsonObject()) cfg.add("tune", new JsonObject());
                JsonObject mine = cfg.getAsJsonObject("tune");
                for (Map.Entry<String, JsonElement> e : def.getAsJsonObject("tune").entrySet()) if (!mine.has(e.getKey())) mine.add(e.getKey(), e.getValue());
            }
            cfg.addProperty("version", want);
            LOGGER.info("Curadoria de formas: arquivo atualizado da versao {} para {} (entradas novas acrescentadas).", have, want);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ execucao

    /** Aplica a curadoria. true = algum arquivo do DMZ mudou (recarregue o ConfigManager). */
    public static boolean run(String phase) {
        return run(FMLPaths.CONFIGDIR.get(), phase);
    }

    /** Mesma coisa, numa pasta de config qualquer (usado tambem pelo teste automatico do build). */
    public static synchronized boolean run(Path configDir, String phase) {
        JsonObject cfg = loadCuration(configDir);
        if (cfg == null) return false;
        Path dmz = configDir.resolve("dragonminez");
        if (!Files.isDirectory(dmz)) return false;
        try {
            List<FormFile> files = loadFormFiles(dmz);
            if (files.isEmpty()) return false;

            Map<String, String> removed = new LinkedHashMap<>(); // "grupo.forma" -> pre-requisito que ela tinha
            Set<String> removedGroups = new HashSet<>();
            Set<String> touched = new HashSet<>(); // "raca|formType" afetados

            for (String key : strings(cfg, "removeForms")) removeForm(files, key, removed, touched);
            for (String g : strings(cfg, "removeGroups")) {
                String group = g.toLowerCase(Locale.ROOT);
                for (FormFile f : files) {
                    if (!f.group.equals(group) || f.delete) continue;
                    for (Map.Entry<String, JsonElement> e : f.forms.entrySet()) removed.put(group + "." + e.getKey().toLowerCase(Locale.ROOT), requisite(e.getValue()));
                    f.delete = true;
                    removedGroups.add(group);
                    touched.add(f.race + "|" + f.formType);
                }
            }
            if (cfg.has("dedupe") && cfg.get("dedupe").isJsonArray()) {
                for (JsonElement set : cfg.getAsJsonArray("dedupe")) {
                    if (!set.isJsonArray()) continue;
                    boolean kept = false;
                    for (JsonElement el : set.getAsJsonArray()) {
                        String key = el.getAsString().toLowerCase(Locale.ROOT);
                        if (!exists(files, key)) continue;
                        if (!kept) { kept = true; continue; }
                        removeForm(files, key, removed, touched);
                    }
                }
            }
            // grupo que ficou vazio some tambem
            for (FormFile f : files) {
                if (!f.delete && f.forms.size() == 0 && f.dirty) {
                    f.delete = true;
                    removedGroups.add(f.group);
                }
            }

            int fixedReqs = repairRequisites(files, removed);
            Map<String, Integer> newMax = compactLevels(files, touched);
            int tuned = tune(files, cfg);

            int written = 0, moved = 0;
            Path backup = dmz.resolve("_removidos_evolution");
            for (FormFile f : files) {
                if (f.delete) {
                    Path rel = dmz.relativize(f.path);
                    Path dest = backup.resolve(rel.toString());
                    Files.createDirectories(dest.getParent());
                    Files.move(f.path, dest, StandardCopyOption.REPLACE_EXISTING);
                    moved++;
                } else if (f.dirty) {
                    Files.writeString(f.path, GSON.toJson(f.root), StandardCharsets.UTF_8);
                    written++;
                }
            }
            int other = cleanSkillsAndPrices(dmz, files, removedGroups, newMax);
            boolean changed = written + moved + other > 0;
            if (changed) {
                LOGGER.info("Curadoria de formas ({}): {} formas removidas, {} grupos movidos para {}, {} pre-requisitos e {} formas ajustadas, {} outros arquivos.",
                        phase, removed.size(), moved, backup.getFileName(), fixedReqs, tuned, other);
            }
            return changed;
        } catch (Exception e) {
            LOGGER.error("Curadoria de formas falhou ({}); os arquivos que ja estavam certos continuam valendo.", phase, e);
            return false;
        }
    }

    private static List<FormFile> loadFormFiles(Path dmz) throws IOException {
        List<FormFile> out = new ArrayList<>();
        Path races = dmz.resolve("races");
        if (Files.isDirectory(races)) {
            try (Stream<Path> raceDirs = Files.list(races)) {
                for (Path raceDir : raceDirs.toList()) {
                    Path formsDir = raceDir.resolve("forms");
                    if (!Files.isDirectory(formsDir)) continue;
                    try (Stream<Path> fs = Files.list(formsDir)) {
                        for (Path p : fs.filter(x -> x.toString().endsWith(".json")).toList()) {
                            JsonObject o = read(p);
                            if (o != null) out.add(new FormFile(p, raceDir.getFileName().toString().toLowerCase(Locale.ROOT), o));
                        }
                    }
                }
            }
        }
        Path global = dmz.resolve("forms");
        if (Files.isDirectory(global)) {
            try (Stream<Path> fs = Files.list(global)) {
                for (Path p : fs.filter(x -> x.toString().endsWith(".json")).toList()) {
                    JsonObject o = read(p);
                    if (o != null) out.add(new FormFile(p, null, o));
                }
            }
        }
        return out;
    }

    private static boolean exists(List<FormFile> files, String key) {
        String[] gf = split(key);
        if (gf == null) return false;
        for (FormFile f : files) if (!f.delete && f.group.equals(gf[0]) && hasKey(f.forms, gf[1])) return true;
        return false;
    }

    private static void removeForm(List<FormFile> files, String key, Map<String, String> removed, Set<String> touched) {
        String[] gf = split(key);
        if (gf == null) return;
        for (FormFile f : files) {
            if (f.delete || !f.group.equals(gf[0])) continue;
            String real = realKey(f.forms, gf[1]);
            if (real == null) continue;
            removed.put(gf[0] + "." + gf[1], requisite(f.forms.get(real)));
            f.forms.remove(real);
            f.dirty = true;
            touched.add(f.race + "|" + f.formType);
        }
    }

    /** Quem dependia de uma forma removida passa a depender do que ela dependia. */
    private static int repairRequisites(List<FormFile> files, Map<String, String> removed) {
        if (removed.isEmpty()) return 0;
        Set<String> alive = new HashSet<>();
        for (FormFile f : files) {
            if (f.delete) continue;
            for (String k : f.forms.keySet()) alive.add(f.group + "." + k.toLowerCase(Locale.ROOT));
        }
        int fixed = 0;
        for (FormFile f : files) {
            if (f.delete) continue;
            for (Map.Entry<String, JsonElement> e : f.forms.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject form = e.getValue().getAsJsonObject();
                String req = str(form, "formRequisite").toLowerCase(Locale.ROOT);
                if (req.isEmpty() || !removed.containsKey(req)) continue;
                String next = req;
                for (int i = 0; i < 12 && removed.containsKey(next); i++) next = removed.get(next).toLowerCase(Locale.ROOT);
                if (!next.isEmpty() && !alive.contains(next)) next = "";
                form.addProperty("formRequisite", next);
                f.dirty = true;
                fixed++;
            }
        }
        return fixed;
    }

    /**
     * Fecha buracos nos niveis de cada habilidade de forma (por raca) que perdeu formas: o Blue
     * Aperfeicoado, que era nivel 4 depois do Blue Evoluido removido, vira nivel 3. Niveis especiais
     * (0 e 90+) ficam como estao. Devolve "raca|formType" -> maior nivel depois de compactar.
     */
    private static Map<String, Integer> compactLevels(List<FormFile> files, Set<String> touched) {
        Map<String, Integer> result = new HashMap<>();
        for (String t : touched) {
            String[] parts = t.split("\\|", 2);
            String race = "null".equals(parts[0]) ? null : parts[0];
            String type = parts[1];
            List<JsonObject> forms = new ArrayList<>();
            List<FormFile> owners = new ArrayList<>();
            for (FormFile f : files) {
                if (f.delete || !type.equals(f.formType) || !java.util.Objects.equals(race, f.race)) continue;
                for (Map.Entry<String, JsonElement> e : f.forms.entrySet()) {
                    if (e.getValue().isJsonObject()) { forms.add(e.getValue().getAsJsonObject()); owners.add(f); }
                }
            }
            TreeSet<Integer> levels = new TreeSet<>();
            for (JsonObject o : forms) {
                int lv = level(o);
                if (lv > 0 && lv < 90) levels.add(lv);
            }
            Map<Integer, Integer> remap = new HashMap<>();
            int n = 0;
            for (int lv : levels) remap.put(lv, ++n);
            for (int i = 0; i < forms.size(); i++) {
                JsonObject o = forms.get(i);
                int lv = level(o);
                Integer to = remap.get(lv);
                if (to != null && to != lv) {
                    o.addProperty("unlockOnSkillLevel", to);
                    owners.get(i).dirty = true;
                }
            }
            result.put(t, n);
        }
        return result;
    }

    private static int tune(List<FormFile> files, JsonObject cfg) {
        if (!cfg.has("tune") || !cfg.get("tune").isJsonObject()) return 0;
        JsonObject tune = cfg.getAsJsonObject("tune");
        int count = 0;
        for (FormFile f : files) {
            if (f.delete) continue;
            for (Map.Entry<String, JsonElement> e : f.forms.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonElement t = tune.get(f.group + "." + e.getKey().toLowerCase(Locale.ROOT));
                if (t == null || !t.isJsonObject()) continue;
                JsonObject form = e.getValue().getAsJsonObject();
                boolean changed = false;
                for (Map.Entry<String, JsonElement> kv : t.getAsJsonObject().entrySet()) {
                    if (kv.getKey().startsWith("_")) continue;
                    JsonElement cur = form.get(kv.getKey());
                    if (!sameValue(cur, kv.getValue())) {
                        form.add(kv.getKey(), kv.getValue().deepCopy());
                        changed = true;
                    }
                }
                if (changed) { f.dirty = true; count++; }
            }
        }
        return count;
    }

    /** Tira dos skills.json e character.json o que apontava para grupos que nao existem mais e corta precos vazios. */
    private static int cleanSkillsAndPrices(Path dmz, List<FormFile> files, Set<String> removedGroups, Map<String, Integer> newMax) throws IOException {
        int changedFiles = 0;
        Set<String> aliveTypesAnyRace = new HashSet<>();
        Map<String, Set<String>> aliveTypesByRace = new HashMap<>();
        Set<String> deletedTypes = new HashSet<>();
        for (FormFile f : files) {
            if (f.delete) { deletedTypes.add(f.formType); continue; }
            aliveTypesAnyRace.add(f.formType);
            aliveTypesByRace.computeIfAbsent(String.valueOf(f.race), k -> new HashSet<>()).add(f.formType);
        }
        deletedTypes.removeAll(aliveTypesAnyRace);
        Set<String> gone = new HashSet<>(removedGroups);
        gone.addAll(deletedTypes);
        gone.removeAll(aliveTypesAnyRace);

        Path skills = dmz.resolve("skills.json");
        if (!gone.isEmpty() && Files.exists(skills)) {
            JsonObject root = read(skills);
            if (root != null) {
                boolean dirty = false;
                for (String list : new String[]{"formSkills", "stackSkills", "kiSkills"}) {
                    if (root.has(list) && root.get(list).isJsonArray()) {
                        JsonArray kept = new JsonArray();
                        for (JsonElement el : root.getAsJsonArray(list)) {
                            if (el.isJsonPrimitive() && gone.contains(el.getAsString().toLowerCase(Locale.ROOT))) { dirty = true; continue; }
                            kept.add(el);
                        }
                        root.add(list, kept);
                    }
                }
                if (root.has("skills") && root.get("skills").isJsonObject()) {
                    JsonObject s = root.getAsJsonObject("skills");
                    for (String g : gone) {
                        String real = realKey(s, g);
                        if (real != null) { s.remove(real); dirty = true; }
                    }
                }
                if (dirty) { Files.writeString(skills, GSON.toJson(root), StandardCharsets.UTF_8); changedFiles++; }
            }
        }

        Path races = dmz.resolve("races");
        if (Files.isDirectory(races)) {
            try (Stream<Path> dirs = Files.list(races)) {
                for (Path raceDir : dirs.toList()) {
                    Path ch = raceDir.resolve("character.json");
                    if (!Files.exists(ch)) continue;
                    JsonObject root = read(ch);
                    if (root == null || !root.has("formSkillsCosts") || !root.get("formSkillsCosts").isJsonObject()) continue;
                    String race = raceDir.getFileName().toString().toLowerCase(Locale.ROOT);
                    Set<String> alive = aliveTypesByRace.getOrDefault(race, Set.of());
                    JsonObject costs = root.getAsJsonObject("formSkillsCosts");
                    boolean dirty = false;
                    for (String key : new ArrayList<>(costs.keySet())) {
                        String k = key.toLowerCase(Locale.ROOT);
                        if (gone.contains(k) && !alive.contains(k)) { costs.remove(key); dirty = true; continue; }
                        Integer max = newMax.get(race + "|" + k);
                        if (max != null && max > 0 && costs.get(key).isJsonObject()) {
                            JsonObject c = costs.getAsJsonObject(key);
                            if (c.has("prices") && c.get("prices").isJsonArray() && c.getAsJsonArray("prices").size() > max) {
                                JsonArray cut = new JsonArray();
                                JsonArray prices = c.getAsJsonArray("prices");
                                for (int i = 0; i < max; i++) cut.add(prices.get(i));
                                c.add("prices", cut);
                                dirty = true;
                            }
                        }
                    }
                    if (dirty) { Files.writeString(ch, GSON.toJson(root), StandardCharsets.UTF_8); changedFiles++; }
                }
            }
        }
        return changedFiles;
    }

    // ------------------------------------------------------------------ util

    private static JsonObject read(Path p) {
        try {
            JsonElement e = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String[] split(String key) {
        int dot = key.indexOf('.');
        if (dot <= 0 || dot == key.length() - 1) return null;
        return new String[]{key.substring(0, dot).toLowerCase(Locale.ROOT), key.substring(dot + 1).toLowerCase(Locale.ROOT)};
    }

    private static boolean hasKey(JsonObject o, String key) {
        return realKey(o, key) != null;
    }

    private static String realKey(JsonObject o, String key) {
        for (String k : o.keySet()) if (k.equalsIgnoreCase(key)) return k;
        return null;
    }

    private static String requisite(JsonElement form) {
        return form != null && form.isJsonObject() ? str(form.getAsJsonObject(), "formRequisite") : "";
    }

    private static int level(JsonObject o) {
        try {
            return o.has("unlockOnSkillLevel") ? o.get("unlockOnSkillLevel").getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) for (JsonElement e : o.getAsJsonArray(key)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    private static boolean sameValue(JsonElement a, JsonElement b) {
        if (a == null || b == null) return a == b;
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive pa = a.getAsJsonPrimitive(), pb = b.getAsJsonPrimitive();
            if (pa.isNumber() && pb.isNumber()) return Math.abs(pa.getAsDouble() - pb.getAsDouble()) < 1e-9;
            if (pa.isString() && pb.isString()) return pa.getAsString().equalsIgnoreCase(pb.getAsString());
        }
        return a.equals(b);
    }
}
