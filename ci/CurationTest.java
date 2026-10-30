import com.dmzkicontrol.compat.FormCurator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.*;
import java.util.*;

/** Teste automatico da curadoria de formas (roda no GitHub Actions, fora do Minecraft). */
public class CurationTest {
    static int fails = 0;
    static Path cfg;

    static JsonObject read(String rel) throws Exception {
        return JsonParser.parseString(Files.readString(cfg.resolve("dragonminez").resolve(rel))).getAsJsonObject();
    }
    static JsonObject forms(String rel) throws Exception { return read(rel).getAsJsonObject("forms"); }
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FALHA ") + what);
        if (!ok) fails++;
    }
    static boolean exists(String rel) { return Files.exists(cfg.resolve("dragonminez").resolve(rel)); }

    public static void main(String[] a) throws Exception {
        Path src = Paths.get(a[0]);
        cfg = Files.createTempDirectory("curation");
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path d = cfg.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d); else Files.copy(p, d);
            }
        }
        boolean changed = FormCurator.run(cfg, "teste");
        check(changed, "primeira passada muda arquivos");

        JsonObject god = forms("races/saiyan/forms/godforms.json");
        check(!god.has("supersaiyanblueevolved"), "Blue Evoluido removido");
        check(!god.has("supersaiyanrose") && !god.has("supersaiyanroseevolved"), "Rose repetido tirado do godforms");
        check(god.getAsJsonObject("supersaiyanblueperfected").get("formRequisite").getAsString().equals("godforms.supersaiyanblue"),
                "Blue Aperfeicoado agora exige o Blue (pre-requisito consertado)");
        check(god.getAsJsonObject("supersaiyanblueperfected").get("unlockOnSkillLevel").getAsInt() == 3, "Blue Aperfeicoado desceu do nivel 4 para o 3");
        check(god.getAsJsonObject("supersaiyanblue").get("strMultiplier").getAsDouble() == 6.0, "Blue balanceado (6.0)");

        JsonObject evil = forms("races/saiyan/forms/evildivineforms.json");
        check(evil.keySet().equals(Set.of("supersaiyanrose")), "so o Rose sobrou no evildivineforms");
        check(!exists("races/saiyan/forms/divinerose.json"), "divinerose (Rose repetido) movido para o backup");
        check(!exists("races/saiyan/forms/dmzsparking_af.json"), "SSJ5 removido");

        JsonObject leg = forms("races/saiyan/forms/legendaryforms.json");
        check(!leg.has("ssjlegendary3") && !leg.has("ssjlegendary4") && leg.has("ssjfullpower"), "SSJ3/SSJ4 Lendario fora, Lendario FP fica");
        JsonObject ooz = forms("races/saiyan/forms/oozaru.json");
        check(!ooz.has("supersaiyan4fp") && !ooz.has("supersaiyan4lb") && ooz.has("supersaiyan4"), "SSJ4 FP/LB fora, SSJ4 fica");
        check(!forms("races/saiyan/forms/supersaiyan.json").has("supersaiyan4") && forms("races/saiyan/forms/supersaiyan.json").has("supersaiyan3"),
                "SSJ4 so uma vez (fica o do Oozaru Dourado, como no GT)");
        check(ooz.getAsJsonObject("goldenoozaru").get("unlockOnSkillLevel").getAsInt() == 7, "niveis do superforms intactos (sem buracos)");
        check(forms("races/saiyan/forms/beastforms.json").getAsJsonObject("beast").get("strMultiplier").getAsDouble() == 7.5, "Beast balanceado (22.85 -> 7.5)");

        JsonObject ch = read("races/saiyan/character.json").getAsJsonObject("formSkillsCosts");
        check(ch.getAsJsonObject("superforms").getAsJsonArray("prices").size() == 8, "precos do superforms cortados para 8 niveis");
        check(ch.getAsJsonObject("godforms").getAsJsonArray("prices").size() == 3, "precos do godforms cortados para 3 niveis");
        check(ch.getAsJsonObject("evildivineforms").getAsJsonArray("prices").size() == 1, "precos do evildivineforms cortados para 1 nivel");
        check(!ch.has("dmzsparking_af") && !ch.has("divinerose"), "grupos removidos sairam do character.json");

        check(!exists("races/bioandroid/forms/biogodforms.json"), "Cell God/Blue removido");
        check(!forms("races/bioandroid/forms/bioevolution.json").has("corruptedgiant") && forms("races/bioandroid/forms/cellmax.json").has("corruptedgiant"),
                "Cell Max so uma vez (fica o do cellmax)");
        check(!exists("races/namekian/forms/pureprogress.json"), "Orange Piccolo repetido (pureprogress) movido");
        JsonObject div = forms("races/namekian/forms/divineforms.json");
        check(div.has("golden") && div.has("awakenedgoldengiant") && !div.has("supernamekiangod"), "Namekuseijin: Orange e Gigante ficam, Deus Namek sai");
        check(!exists("races/majin/forms/mimicforms.json"), "Super Majin removido");

        JsonObject gk = forms("forms/godkienhancement.json");
        check(gk.keySet().equals(Set.of("controlled")), "Ki Divino so uma versao");
        check(!exists("forms/friezabiodata.json"), "Bio-Android Golden/Black removido");
        check(forms("forms/frostgodforms.json").getAsJsonObject("black").get("strMultiplier").getAsDouble() == 2.4, "Black Freeza balanceado (14 -> 2.4)");

        JsonObject skills = read("skills.json");
        String fs = skills.getAsJsonArray("formSkills").toString(), ss = skills.getAsJsonArray("stackSkills").toString();
        check(!fs.contains("mimicforms") && !fs.contains("biogodforms") && !fs.contains("dmzsparking_af") && !fs.contains("divinerose") && !fs.contains("pureprogress"),
                "skills.json sem os grupos removidos");
        check(fs.contains("godforms") && fs.contains("cellmax") && fs.contains("divineforms"), "skills.json mantem os grupos vivos");
        check(!ss.contains("friezabiodata") && ss.contains("frostgodforms"), "stackSkills sem o friezabiodata");
        check(skills.getAsJsonObject("skills").has("aura_control") && !skills.getAsJsonObject("skills").has("mimicforms"), "skills: Aura Control fica, Super Majin sai");
        check(Files.exists(cfg.resolve("dragonminez/_removidos_evolution/races/saiyan/forms/dmzsparking_af.json")), "backup dos grupos removidos existe");

        boolean again = FormCurator.run(cfg, "teste-2");
        check(!again, "segunda passada nao muda nada (idempotente)");

        // arquivo de curadoria de versao antiga: ganha so as entradas novas, o resto do dono fica
        Path old = Files.createTempDirectory("curation-old");
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path d = old.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d); else Files.copy(p, d);
            }
        }
        JsonObject v1 = JsonParser.parseString(Files.readString(cfg.resolve("dmzkicontrol_forms.json"))).getAsJsonObject();
        v1.addProperty("version", 1);
        var dd = v1.getAsJsonArray("dedupe");
        for (int i = dd.size() - 1; i >= 0; i--) if (dd.get(i).toString().contains("supersaiyan4")) dd.remove(i);
        v1.addProperty("meuCampo", "fica");
        Files.writeString(old.resolve("dmzkicontrol_forms.json"), v1.toString());
        Path saved = cfg;
        cfg = old;
        FormCurator.run(old, "teste-versao-antiga");
        JsonObject up = JsonParser.parseString(Files.readString(old.resolve("dmzkicontrol_forms.json"))).getAsJsonObject();
        check(up.get("version").getAsInt() >= 2 && up.getAsJsonArray("dedupe").toString().contains("supersaiyan.supersaiyan4"), "arquivo antigo atualizado com a regra nova");
        check(up.has("meuCampo"), "o que o dono mudou no arquivo fica");
        check(!forms("races/saiyan/forms/supersaiyan.json").has("supersaiyan4"), "regra nova ja aplicada no arquivo antigo");
        cfg = saved;

        System.out.println(fails == 0 ? "CURADORIA: TODOS OS TESTES PASSARAM" : "CURADORIA: " + fails + " FALHA(S)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
