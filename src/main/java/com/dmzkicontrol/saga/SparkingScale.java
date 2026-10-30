package com.dmzkicontrol.saga;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.Locale;

/**
 * As sagas do Moro e do Granolah vieram na escala do DMZ puro (nivel ~5.600, logo depois do Buu). Com o
 * DMZ Sparking a historia continua depois do Torneio do Poder, que pede nivel ~44.500 e inimigos dez vezes
 * mais fortes - ai o Moro virava um saco de pancada. Aqui o nivel pedido e a forca dos inimigos sao
 * levados para a escala do Sparking, medida no ultimo chefe do Torneio do Poder do proprio mundo
 * (assim acompanha qualquer ajuste que o Sparking ou o dono do mundo facam).
 */
final class SparkingScale {
    /** Inicio e passo da escala original das sagas (nivel por missao). */
    private static final double OLD_START = 5580, NEW_STEP = 7.6;
    /** Forca por nivel do primeiro chefe original (Moro, missao 7): vida, corpo a corpo, ki. */
    private static final double BASE_HP = 760.9, BASE_MELEE = 13.600, BASE_KI = 12.512;
    /** Quanto acima do ultimo chefe do Torneio a saga do Moro comeca. */
    private static final double GAP = 900;

    private final double refLevel, hpPerLevel, meleePerLevel, kiPerLevel, defenseShare;

    private SparkingScale(double refLevel, double hp, double melee, double ki, double defense) {
        this.refLevel = refLevel;
        this.hpPerLevel = hp / refLevel;
        this.meleePerLevel = melee / refLevel;
        this.kiPerLevel = ki / refLevel;
        this.defenseShare = melee > 0 ? defense / melee : 0;
    }

    /** Le o chefe de nivel mais alto da saga do Sparking; sem os arquivos, usa os valores do Sparking 1.5. */
    static SparkingScale read(Path dmz, String sagaId) {
        double bestLevel = 0, hp = 0, melee = 0, ki = 0, def = 0;
        for (JsonObject q : SagaInstaller.quests(dmz.resolve("quests").resolve(SagaInstaller.questFolder(dmz, sagaId, "dmzsparking_saga_top")))) {
            double level = level(q);
            if (level <= bestLevel || !q.has("objectives")) continue;
            for (JsonElement e : q.getAsJsonArray("objectives")) {
                JsonObject o = e.getAsJsonObject();
                int count = o.has("count") ? o.get("count").getAsInt() : 1;
                if (!"KILL".equals(str(o, "type")) || count != 1 || num(o, "health") <= 0 || num(o, "meleeDamage") <= 0) continue;
                bestLevel = level;
                hp = num(o, "health");
                melee = num(o, "meleeDamage");
                ki = num(o, "kiDamage") > 0 ? num(o, "kiDamage") : melee;
                def = num(o, "Defense");
                break;
            }
        }
        if (bestLevel <= 0) return new SparkingScale(44528, 5819237, 554441, 670718, 499629);
        return new SparkingScale(bestLevel, hp, melee, ki, def);
    }

    void apply(JsonObject quest) {
        double oldLevel = level(quest);
        if (oldLevel <= 0) return;
        double newLevel = Math.round(refLevel + GAP + (oldLevel - OLD_START) * NEW_STEP);
        JsonObject req = quest.getAsJsonObject("requirements");
        if (req != null && req.has("conditions")) {
            for (JsonElement e : req.getAsJsonArray("conditions")) {
                if (e.isJsonObject() && "LEVEL".equals(str(e.getAsJsonObject(), "type"))) e.getAsJsonObject().addProperty("minLevel", (int) newLevel);
            }
        }
        if (!quest.has("objectives")) return;
        double grow = newLevel / oldLevel;
        for (JsonElement e : quest.getAsJsonArray("objectives")) {
            JsonObject o = e.getAsJsonObject();
            if (!"KILL".equals(str(o, "type")) || num(o, "health") <= 0) continue;
            o.addProperty("health", Math.round(num(o, "health") * grow * hpPerLevel / BASE_HP));
            double melee = num(o, "meleeDamage") * grow * meleePerLevel / BASE_MELEE;
            o.addProperty("meleeDamage", round1(melee));
            if (num(o, "kiDamage") > 0) o.addProperty("kiDamage", round1(num(o, "kiDamage") * grow * kiPerLevel / BASE_KI));
            if (defenseShare > 0 && !o.has("Defense")) o.addProperty("Defense", round1(melee * defenseShare));
        }
    }

    private static double level(JsonObject q) {
        JsonObject req = q.getAsJsonObject("requirements");
        if (req == null || !req.has("conditions")) return 0;
        for (JsonElement e : req.getAsJsonArray("conditions")) {
            if (e.isJsonObject() && "LEVEL".equals(str(e.getAsJsonObject(), "type"))) return num(e.getAsJsonObject(), "minLevel");
        }
        return 0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static double num(JsonObject o, String k) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "nivel %d a %d (referencia: chefe do Torneio no nivel %d)",
                Math.round(refLevel + GAP), Math.round(refLevel + GAP + 864 * NEW_STEP), Math.round(refLevel));
    }
}
