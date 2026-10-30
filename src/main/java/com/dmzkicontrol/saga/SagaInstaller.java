package com.dmzkicontrol.saga;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * Escreve as sagas do Moro e do Granolah na pasta do mundo, no formato que o DMZ 2.2 le
 * (world/dragonminez/sagas e world/dragonminez/quests), encadeadas depois do Torneio do Poder do DMZ Sparking
 * (ou depois da saga do Buu, sem o Sparking). Roda antes do DMZ carregar as missoes (ServerStarting).
 * Arquivo que o dono do mundo editou fica como esta (livro-caixa em world/dmzkicontrol/sagas_written.json).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SagaInstaller {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String ROOT = "/dmzkicontrol_sagas/";
    /** Com o DMZ Sparking, Moro e Granolah seguem o Torneio do Poder dele (a historia principal; os filmes sao a parte). */
    private static final String SPARKING_TOP = "dmzsparking_s5_top";
    private static final String SPARKING_ANY = "dmzsparking_s1_beerus";

    private SagaInstaller() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!KiControlConfig.get().sagas.installMoroAndGranolah) return;
        MinecraftServer server = event.getServer();
        Path world = server.getWorldPath(LevelResource.ROOT);
        Path dmz = world.resolve("dragonminez");
        try {
            JsonObject previous = previousCondition(dmz);
            SparkingScale scale = previous != null && SPARKING_TOP.equals(previous.get("sagaId").getAsString())
                    && KiControlConfig.get().sagas.sparkingScale ? SparkingScale.read(dmz, SPARKING_TOP) : null;
            if (previous == null) {
                KiControlMod.LOGGER.warn("[Sagas] Nao achei onde encaixar as sagas do Moro e do Granolah (nem o Broly do Sparking nem a saga do Buu) - ficam para o proximo inicio.");
                return;
            }
            Ledger ledger = Ledger.load(world.resolve("dmzkicontrol").resolve("sagas_written.json"));
            JsonObject index = jar("index.json");
            int written = 0, kept = 0;
            JsonObject prev = previous;
            String prevSaga = previous.get("sagaId").getAsString();
            for (JsonElement e : index.getAsJsonArray("sagas")) {
                JsonObject saga = e.getAsJsonObject();
                String id = saga.get("id").getAsString();
                String folder = saga.get("folder").getAsString();
                JsonObject manifest = new JsonObject();
                manifest.addProperty("id", id);
                manifest.addProperty("name", saga.get("name").getAsString());
                JsonObject req = new JsonObject();
                req.addProperty("previousSaga", prevSaga);
                manifest.add("requirements", req);
                manifest.addProperty("questFolder", folder);
                switch (ledger.write(dmz, "sagas/" + id + ".json", manifest)) {
                    case WRITTEN -> written++;
                    case KEPT -> kept++;
                    default -> {}
                }
                JsonArray quests = saga.getAsJsonArray("quests");
                for (JsonElement q : quests) {
                    String stem = q.getAsString();
                    JsonObject quest = jar("quests/" + folder + "/" + stem + ".json");
                    if (scale != null) scale.apply(quest);
                    JsonObject pre = quest.getAsJsonObject("prerequisites");
                    if (pre != null) {
                        JsonArray conds = pre.getAsJsonArray("conditions");
                        for (int i = 0; i < conds.size(); i++) {
                            if (conds.get(i).isJsonPrimitive() && "@PREVIOUS".equals(conds.get(i).getAsString())) conds.set(i, prev.deepCopy());
                        }
                    }
                    switch (ledger.write(dmz, "quests/" + folder + "/" + stem + ".json", quest)) {
                        case WRITTEN -> written++;
                        case KEPT -> kept++;
                        default -> {}
                    }
                }
                prev = condition(id, quests.size());
                prevSaga = id;
            }
            ledger.save();
            KiControlMod.LOGGER.info("[Sagas] Moro e Granolah: {} arquivos escritos, {} editados pelo dono mantidos (depois de {}{}).", written, kept,
                    previous.get("sagaId").getAsString() + ":" + previous.get("questId").getAsInt(), scale != null ? ", na escala do Sparking: " + scale : "");
        } catch (Exception ex) {
            KiControlMod.LOGGER.error("[Sagas] Sagas do Moro e do Granolah nao instaladas: {}", ex.toString());
        }
        if (KiControlConfig.get().sagas.cerealNpcs) CerealNpcs.install(world, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!KiControlConfig.get().sagas.installMoroAndGranolah || !KiControlConfig.get().sagas.cerealNpcs) return;
        CerealNpcs.install(event.getServer().getWorldPath(LevelResource.ROOT), true);
    }

    /** Ultima missao antes da saga do Moro: o fim do Torneio do Poder (Sparking) ou o fim da saga do Buu (so o DMZ). */
    private static JsonObject previousCondition(Path dmz) {
        if (ModList.get().isLoaded("dmzsparking") || Files.isRegularFile(dmz.resolve("sagas").resolve(SPARKING_ANY + ".json"))) {
            int last = lastQuestId(dmz.resolve("quests").resolve(questFolder(dmz, SPARKING_TOP, "dmzsparking_saga_top")));
            return condition(SPARKING_TOP, last > 0 ? last : 36);
        }
        int last = lastQuestId(dmz.resolve("quests").resolve("saga_buu"));
        return last > 0 ? condition("buu_saga", last) : null;
    }

    static String questFolder(Path dmz, String sagaId, String fallback) {
        try {
            Path f = dmz.resolve("sagas").resolve(sagaId + ".json");
            if (Files.isRegularFile(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("questFolder")) return o.get("questFolder").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    static java.util.List<JsonObject> quests(Path folder) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    out.add(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static int lastQuestId(Path folder) {
        int last = 0;
        for (JsonObject q : quests(folder)) {
            if (q.has("id") && q.get("id").isJsonPrimitive() && q.get("id").getAsJsonPrimitive().isNumber()) last = Math.max(last, q.get("id").getAsInt());
        }
        return last;
    }

    static JsonObject condition(String saga, int quest) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "SAGA_QUEST");
        c.addProperty("sagaId", saga);
        c.addProperty("questId", quest);
        return c;
    }

    static JsonObject jar(String path) throws Exception {
        try (InputStream in = SagaInstaller.class.getResourceAsStream(ROOT + path)) {
            if (in == null) throw new java.io.FileNotFoundException(ROOT + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    enum Outcome { WRITTEN, SAME, KEPT }

    /** Lembra o hash do que escrevemos: se o arquivo no mundo mudou depois disso, quem mexeu foi o dono e fica. */
    static final class Ledger {
        private final Path file;
        private final JsonObject data;
        private boolean dirty;

        private Ledger(Path file, JsonObject data) {
            this.file = file;
            this.data = data;
        }

        static Ledger load(Path file) {
            JsonObject d = new JsonObject();
            try {
                if (Files.isRegularFile(file)) d = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ignored) {}
            return new Ledger(file, d);
        }

        Outcome write(Path dmz, String rel, JsonObject content) throws Exception {
            Path target = dmz.resolve(rel);
            String text = GSON.toJson(content);
            String want = hash(text);
            if (Files.isRegularFile(target)) {
                String now = hash(Files.readString(target, StandardCharsets.UTF_8));
                if (now.equals(want)) {
                    if (!want.equals(str(rel))) { data.addProperty(rel, want); dirty = true; }
                    return Outcome.SAME;
                }
                String last = str(rel);
                if (last == null || !last.equals(now)) return Outcome.KEPT;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, text, StandardCharsets.UTF_8);
            data.addProperty(rel, want);
            dirty = true;
            return Outcome.WRITTEN;
        }

        private String str(String rel) {
            return data.has(rel) ? data.get(rel).getAsString() : null;
        }

        void save() throws Exception {
            if (!dirty) return;
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        }

        static String hash(String s) throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        }
    }
}
