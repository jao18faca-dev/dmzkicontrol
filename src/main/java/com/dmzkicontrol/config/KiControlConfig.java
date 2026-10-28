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
        /** Bonus no multiplicador da forma enquanto selado (0.10 = forma x1.10): sobe STR, SKP, PWR (metade em DEF) e o power level. */
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

    /**
     * Tensao interna ("cansaco" de segurar o ki dentro do corpo), em PORCENTAGEM (0-100).
     * Enche enquanto a aura esta selada (mais devagar quanto maior o dominio), esvazia quando livre.
     */
    public static class Strain {
        /** Liga a tensao interna. */
        public boolean enabled = true;
        /** % de tensao por segundo selado com 0% de dominio (x trainTimeMultiplier do tier: o Blue cansa o dobro). */
        public double fillPercentPerSecond = 1.6;
        /** Curva do alivio pelo dominio: taxa = fill x (1 - dominio/strainFreeMastery)^curve. */
        public double curve = 1.5;
        /** A partir deste dominio (0..1) o selo nao cansa mais: controle perfeito. */
        public double strainFreeMastery = 0.90;
        /** % recuperada por segundo com a aura livre (x (1 + dominio)). */
        public double recoverPercentPerSecond = 5.0;
        /** Nao da para selar de novo enquanto a tensao estiver acima disto (%): corpo exausto. */
        public double resealLockoutPercent = 60.0;
        /** % extra por golpe recebido selado (x (1 - dominio)). */
        public double perHitTakenPercent = 3.0;
        /** % extra por tecnica de ki disparada selado (x (1 - dominio)). */
        public double perKiAttackPercent = 2.0;
        /** A partir desta %: cansaco, perde estamina continuamente. */
        public double fatiguePercent = 50.0;
        /** Estamina perdida por segundo no cansaco (% da estamina maxima). */
        public double staminaPercentPerSecond = 2.0;
        /** A partir desta %: o corpo pesa (Lentidao I). */
        public double slownessPercent = 75.0;
        /** A partir desta %: o ki preso fere o corpo. */
        public double hurtPercent = 90.0;
        /** Ticks entre cada pulso de dano. */
        public int pulseTicks = 40;
        /** Vida perdida por pulso (% da vida maxima). A tensao sozinha nunca mata. */
        public double healthPercentPerPulse = 3.0;
        /** Ki atual perdido quando a aura explode a 100% (%). */
        public double kiLossOnBreakPercent = 15.0;
    }

    /** Como o dominio cresce: rapido no comeco, cada vez mais lento perto da perfeicao. */
    public static class Mastery {
        /** Inclinacao da curva: dominio = inicial + (1-inicial) x (1 - e^(-k x pratica/trainSeconds)). */
        public double curveSteepness = 3.0;
        /** Treino rende mais lutando (golpe dado/recebido nos ultimos segundos). */
        public double combatMultiplier = 1.75;
        /** Janela de combate, em segundos. */
        public double combatWindowSeconds = 5.0;
        /** Treino rende mais forcando o limite (tensao entre 40% e 90%). */
        public double pushingLimitMultiplier = 1.5;
        /** Treino rende menos perdendo o controle (tensao acima de 90%). */
        public double overstrainMultiplier = 0.6;
    }

    public static class Burst {
        /** Liberar o selo depois de segura-lo um tempo da um pico de poder ("explosao de ki"). */
        public boolean enabled = true;
        /** Segundos minimos selado para a liberacao contar. */
        public double minSealSeconds = 20.0;
        /** Duracao do pico em segundos. */
        public double durationSeconds = 6.0;
        /** Bonus no multiplicador da forma durante o pico, com 100% de dominio (somado ao bonus normal). */
        public double damageBonus = 0.15;
        /** Tempo de recarga entre picos, em segundos. */
        public double cooldownSeconds = 30.0;
    }

    /** Modulo da raca Cerealiana (Granolah). */
    public static class Cerealian {
        /** Instala os arquivos da raca em config/dragonminez/races/cerealian (so os que faltarem). */
        public boolean installRace = true;
        /** Chave da raca no DMZ. */
        public String raceKey = "cerealian";
        /** Olho Cereal: ki gasto por segundo segurando a tecla (% do ki maximo). */
        public double eyeKiCostPercentPerSecond = 0.8;
        /** Alcance da mira e da visao de ki, em blocos. */
        public double eyeRange = 64.0;
        /** Dano extra do primeiro golpe no ponto fraco marcado (0.25 = +25%). */
        public double weakPointDamageBonus = 0.25;
        /** Penetracao de defesa extra nesse golpe (0.20 = ignora +20% da defesa). */
        public double weakPointDefensePenetration = 0.20;
        /** Segundos que a marca do ponto fraco dura depois de mirar. */
        public double markSeconds = 10.0;
        /** Tiros (rajadas de ki, flechas...) a partir desta distancia ganham bonus de atirador. */
        public double sniperMinDistance = 12.0;
        /** Distancia em que o bonus de atirador chega ao maximo. */
        public double sniperMaxDistance = 48.0;
        /** Bonus maximo de dano de tiro a longa distancia (0.30 = +30%). */
        public double sniperMaxBonus = 0.30;
        /** Trecho do id da forma "Olho Desperto" e quanto ela multiplica os bonus do olho. */
        public String awakenedEyeForm = "olhodesperto";
        public double awakenedEyeMultiplier = 1.5;
        /** Trecho do id da forma "Olhos Gemeos" e quanto ela multiplica os bonus do olho. */
        public String twinEyesForm = "olhosgemeos";
        public double twinEyesMultiplier = 2.0;
        /** Troca a bola de ki basica dos Cerealianos por um tiro fino e preciso. */
        public boolean preciseKiShot = true;
        /** Passiva "Leitura de Trajetoria": chance de desviar de projeteis (base + por nivel da Mira). */
        public double dodgeChanceBase = 0.06;
        public double dodgeChancePerLevel = 0.03;
        /** Intervalo minimo entre duas esquivas, em segundos. */
        public double dodgeCooldownSeconds = 3.0;
        /** Passiva "Olhar Preciso": chance de critico extra por nivel da Mira (0.015 = +1,5 pontos). */
        public double critChancePerLevel = 0.015;
        /** Custo em TP de cada nivel da Mira (o nivel 1 vem de graca para todo Cerealiano). */
        public java.util.List<Integer> eyeSkillCosts = new java.util.ArrayList<>(java.util.List.of(8000, 24000, 60000, 120000));
        /** Nivel da Mira que libera a Flecha de Ki. */
        public int kiArrowUnlockLevel = 2;
        /** Raio da explosao do Arco de Ki (respeita as gamerules de destruicao do DMZ). 0 = sem destruicao. */
        public float kiBowExplosionRadius = 2.0f;
        /** Raio extra por carga (carga 1.0 = 100%). */
        public float kiBowExplosionRadiusPerCharge = 1.5f;
        /** Raio da cratera do tiro de ki comum ao bater em bloco (0 = nao quebra blocos). */
        public float kiShotExplosionRadius = 0.0f;
        /** Mira assistida: angulo (graus) para o tiro travar num ponto fraco marcado. */
        public double aimAssistDegrees = 7.0;
        /** Angulo com o Olho Cereal aberto (segurando a tecla). */
        public double aimAssistEyeDegrees = 16.0;
    }

    // ------------------------------------------------------------------ geral

    /** Skill do DMZ exigida para usar o selo ("kicontrol", vendida pelos mestres). Vazio = sem exigencia. */
    public String requiredSkill = "kicontrol";
    /** Versao do formato do arquivo. */
    public int configVersion = 3;
    /** Escala do treino: com este tanto de segundos de pratica efetiva numa forma voce chega a ~95% (antes do trainTimeMultiplier). */
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
    /** Ticks entre apertar a tecla e o selo valer, sem cutscene (o tempo da mao chegar ao peito). */
    public int quickSealDelayTicks = 8;
    /** Ticks entre apertar a tecla e o selo valer, com cutscene (o momento do pulso na cena). */
    public int cutsceneSealDelayTicks = 44;
    /** Toca sons ao selar, liberar e quando a aura explode. */
    public boolean playSounds = true;
    /** A cada quantos ticks o estado e enviado ao HUD do jogador. */
    public int hudSyncTicks = 10;

    public Strain strain = new Strain();
    public Mastery mastery = new Mastery();
    public Cerealian cerealian = new Cerealian();
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
        if (mastery == null) mastery = new Mastery();
        if (cerealian == null) cerealian = new Cerealian();
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
