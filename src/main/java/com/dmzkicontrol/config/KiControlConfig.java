package com.dmzkicontrol.config;

import com.dmzkicontrol.KiControlMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Config do servidor em JSON: config/dmzkicontrol.json  (recarregue com /auracontrol reload).
 *
 * Valores finais = valor do tier x dominio (0..1) naquela forma.
 * Busca do tier de uma forma (chave "grupo:forma", minuscula):
 *   1) forms (chave exata)  2) rules (a PRIMEIRA cujo "contains" aparece na chave)  3) defaultTier
 */
public class KiControlConfig {

    /** Quanto a tecnica rende numa forma com 100% de dominio. */
    public static class Tier {
        /** Fracao do upkeep de ki da forma que voce deixa de pagar (0.80 = -80%). */
        public double maxKiReduction;
        /** Bonus de dano (melee, ki e strike) enquanto selado (0.10 = +10%). */
        public double maxPowerBonus;
        /** Chance de critico extra somada (0.05 = +5 pontos percentuais). */
        public double maxCritBonus;
        /** Multiplica o tempo necessario para dominar esta forma (2.0 = demora o dobro). */
        public double trainTimeMultiplier = 1.0;

        public Tier() {}

        public Tier(double maxKiReduction, double maxPowerBonus, double maxCritBonus, double trainTimeMultiplier) {
            this.maxKiReduction = maxKiReduction;
            this.maxPowerBonus = maxPowerBonus;
            this.maxCritBonus = maxCritBonus;
            this.trainTimeMultiplier = trainTimeMultiplier;
        }
    }

    public static class Rule extends Tier {
        public String contains;

        public Rule() {}

        public Rule(String contains, double ki, double power, double crit, double trainMul) {
            super(ki, power, crit, trainMul);
            this.contains = contains;
        }
    }

    public static class Strain {
        /** Liga a tensao interna (segurar o ki sem dominio machuca). */
        public boolean enabled = true;
        /** Segundos que voce aguenta selado com 0% de dominio. */
        public double baseSeconds = 45.0;
        /** Segundos extras de tolerancia ganhos com 100% de dominio (escala linear). */
        public double extraSecondsAtFullMastery = 900.0;
        /** A partir deste dominio (0..1) nao ha mais tensao: controle perfeito. */
        public double strainFreeMastery = 0.90;
        /** A cada quantos ticks a tensao pulsa depois de passar do limite. */
        public int pulseTicks = 40;
        /** Fracao da vida maxima perdida por pulso. */
        public double healthPercentPerPulse = 0.03;
        /** Fracao da estamina maxima perdida por pulso. */
        public double staminaPercentPerPulse = 0.05;
        /** Depois de limite x este valor, a aura explode para fora e o selo quebra. */
        public double breakAtLimitMultiplier = 2.0;
        /** Fracao do ki atual perdida quando o selo quebra a forca. */
        public double kiLossOnBreak = 0.15;
    }

    public static class Burst {
        /** Liberar o selo depois de segura-lo um tempo da um pico de poder ("explosao de ki"). */
        public boolean enabled = true;
        /** Segundos minimos selado para a liberacao contar. */
        public double minSealSeconds = 20.0;
        /** Duracao do pico em segundos. */
        public double durationSeconds = 6.0;
        /** Bonus de dano do pico com 100% de dominio (somado ao bonus normal). */
        public double damageBonus = 0.15;
        /** Tempo de recarga entre picos, em segundos. */
        public double cooldownSeconds = 30.0;
    }

    // ------------------------------------------------------------------ geral

    /** Skill do DMZ exigida para usar o selo ("kicontrol", vendida pelos mestres). Vazio = sem exigencia. */
    public String requiredSkill = "kicontrol";
    /** Segundos selado numa forma para chegar a 100% de dominio (antes do trainTimeMultiplier do tier). */
    public double trainSeconds = 900.0;
    /** Dominio inicial (0..1) numa forma que voce nunca treinou. */
    public double startingMastery = 0.05;
    /** Quanto o dominio de forma DO DMZ acelera o treino: velocidade x (1 + dominioDMZ% x valor). */
    public double dmzMasteryTrainBoost = 1.0;
    /** Multiplicador da reducao quando uma stack form (Kaioken, UI...) esta ativa junto. */
    public double stackFormPenalty = 0.5;
    /** Fracao da reducao de ki aplicada tambem ao dreno de estamina da forma. */
    public double staminaReductionShare = 0.5;
    /** Bonus de regeneracao de ki enquanto selado com 100% de dominio (o ki nao vaza do corpo). */
    public double energyRegenBonus = 0.15;
    /** Esconde a aura permanente enquanto selado (ela volta ao liberar). */
    public boolean hideAura = true;
    /** Ligar a aura permanente do DMZ enquanto selado libera o selo. */
    public boolean auraToggleReleasesSeal = true;
    /** Carregar ki enquanto selado libera o selo (carregar = expandir a aura). */
    public boolean chargingReleasesSeal = false;
    /** Toca sons ao selar, liberar e quando a aura explode. */
    public boolean playSounds = true;
    /** A cada quantos ticks o estado e enviado ao HUD do jogador. */
    public int hudSyncTicks = 10;

    public Strain strain = new Strain();
    public Burst burst = new Burst();

    public Tier defaultTier = new Tier(0.35, 0.04, 0.02, 1.0);
    public List<Rule> rules = defaultRules();
    public Map<String, Tier> forms = new LinkedHashMap<>();

    private static List<Rule> defaultRules() {
        List<Rule> r = new ArrayList<>();
        // A ordem importa: a primeira regra que casar vence.
        // Principio do Goku na saga Black: o Blue e onde o controle mais compensa,
        // mas tambem e o mais dificil de dominar.
        r.add(new Rule("blue", 0.80, 0.10, 0.05, 2.0));
        r.add(new Rule("god", 0.60, 0.08, 0.04, 1.5));
        r.add(new Rule("ultrainstinct", 0.30, 0.06, 0.08, 2.5));
        r.add(new Rule("ultraego", 0.30, 0.10, 0.02, 2.5));
        r.add(new Rule("kaioken", 0.15, 0.03, 0.01, 1.5));
        r.add(new Rule("oozaru", 0.00, 0.00, 0.00, 1.0));
        r.add(new Rule("supersaiyan4", 0.40, 0.06, 0.03, 1.5));
        r.add(new Rule("supersaiyan3", 0.25, 0.08, 0.02, 1.75));
        r.add(new Rule("supersaiyan2", 0.40, 0.07, 0.03, 1.25));
        r.add(new Rule("mastered", 0.60, 0.05, 0.03, 0.75));
        r.add(new Rule("grade", 0.20, 0.03, 0.01, 1.0));
        r.add(new Rule("fullpower", 0.30, 0.06, 0.02, 1.25));
        r.add(new Rule("ultimate", 0.45, 0.06, 0.03, 1.5));
        return r;
    }

    // ------------------------------------------------------------------ carga

    public static final String FILE_NAME = "dmzkicontrol.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile KiControlConfig instance = new KiControlConfig();

    public static KiControlConfig get() {
        return instance;
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                KiControlConfig loaded = GSON.fromJson(reader, KiControlConfig.class);
                if (loaded != null) {
                    loaded.fillMissing();
                    instance = loaded;
                    save(file, loaded); // grava campos novos de versoes futuras
                    return;
                }
            } catch (Exception e) {
                KiControlMod.LOGGER.error("Falha ao ler {}; usando valores padrao.", file, e);
                instance = new KiControlConfig();
                return; // nao sobrescreve o arquivo do usuario com erro de sintaxe
            }
        }
        instance = new KiControlConfig();
        save(file, instance);
    }

    private static void save(Path file, KiControlConfig cfg) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, writer);
        } catch (IOException e) {
            KiControlMod.LOGGER.error("Nao foi possivel escrever {}", file, e);
        }
    }

    private void fillMissing() {
        if (requiredSkill == null) requiredSkill = "";
        if (strain == null) strain = new Strain();
        if (burst == null) burst = new Burst();
        if (defaultTier == null) defaultTier = new Tier(0.35, 0.04, 0.02, 1.0);
        if (rules == null) rules = new ArrayList<>();
        if (forms == null) forms = new LinkedHashMap<>();
        Map<String, Tier> lower = new LinkedHashMap<>();
        forms.forEach((k, v) -> { if (k != null && v != null) lower.put(k.toLowerCase(Locale.ROOT), v); });
        forms = lower;
    }

    public Tier tierFor(String formKey) {
        String k = formKey.toLowerCase(Locale.ROOT);
        Tier exact = forms.get(k);
        if (exact != null) return exact;
        for (Rule r : rules) {
            if (r != null && r.contains != null && !r.contains.isEmpty()
                    && k.contains(r.contains.toLowerCase(Locale.ROOT))) {
                return r;
            }
        }
        return defaultTier;
    }
}
