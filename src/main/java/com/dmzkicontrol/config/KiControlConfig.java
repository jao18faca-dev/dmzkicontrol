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
        /** Nivel minimo da habilidade Aura Control (1-5) para selar nesta forma. */
        public int minAuraLevel = 1;

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

        public Rule(String contains, double ki, double power, double crit, double trainMul, int minLevel) {
            this(contains, ki, power, crit, trainMul);
            this.minAuraLevel = minLevel;
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
        /**
         * Peso do custo da forma: tensao x (1 + peso x %kiMaximo/s que a forma gasta sem o selo).
         * Formas do DMZ/Sparking/Noea gastam ~0,2%-0,6%/s; com 3.0: 0,2%/s = x1,6 e 0,5%/s = x2,5.
         */
        public double drainWeight = 3.0;
        /** Limite desse multiplicador. */
        public double drainMaxFactor = 4.0;
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

    /**
     * Habilidade exigida para usar o selo: "aura_control" (Aura Control, niveis 1-5, ensinada pelo Goku
     * depois da Saga do Freeza). Vazio = qualquer um usa, com eficiencia de nivel 5.
     */
    public String requiredSkill = "aura_control";
    /** Custo em TP de cada nivel do Aura Control (o 1o e pago ao Goku, os outros no menu de habilidades). */
    public List<Integer> auraControlCosts = new ArrayList<>(List.of(10000, 20000, 40000, 70000, 110000));
    /** Mestre que ensina o Aura Control (id de mestre do DMZ). */
    public String auraControlTeacher = "goku";
    /** Outros mestres que tambem ensinam (no manga o Whis treina o controle do Blue). */
    public List<String> auraControlTeachers = new ArrayList<>(List.of("goku", "whis"));
    /**
     * Teto do Aura Control pela historia: nivel -> "saga:missao" que precisa estar completa. So vale se
     * a saga existir (ex.: sagas do DMZ Sparking); missao 0 = a saga inteira. Vazio = sem teto.
     */
    public Map<String, String> auraLevelStoryRequirements = defaultStoryRequirements();
    /** Saga que precisa estar completa para aprender (vazio = sem exigencia; ignorado com o modo historia desligado). */
    public String auraControlRequiredSaga = "frieza_saga";
    /** Por nivel (1-5): fracao do rendimento maximo da forma (reducao de ki, poder, critico). */
    public List<Double> auraLevelEfficiency = new ArrayList<>(List.of(0.60, 0.70, 0.80, 0.90, 1.00));
    /** Por nivel: velocidade do treino de dominio. */
    public List<Double> auraLevelTrainSpeed = new ArrayList<>(List.of(1.00, 1.15, 1.30, 1.45, 1.60));
    /** Por nivel: multiplicador da tensao interna (quanto menor, mais tempo da para segurar). */
    public List<Double> auraLevelStrain = new ArrayList<>(List.of(1.00, 0.90, 0.80, 0.70, 0.60));
    /** Versao do formato do arquivo. */
    public int configVersion = 6;
    /** Escala do treino: com este tanto de segundos de pratica efetiva numa forma voce chega a ~95% (antes do trainTimeMultiplier). */
    public double trainSeconds = 900.0;
    /** Dominio inicial (0..1) numa forma que voce nunca treinou. */
    public double startingMastery = 0.05;
    /** Quanto o dominio de forma DO DMZ acelera o treino: velocidade x (1 + dominioDMZ% x valor). */
    public double dmzMasteryTrainBoost = 1.0;
    /** Multiplicador da reducao quando uma stack form (Kaioken, UI...) esta ativa junto. */
    public double stackFormPenalty = 0.5;
    /** O mesmo, com controle perfeito (Aura Control 5 e dominio sem tensao): o "Blue Kaioken". */
    public double perfectControlStackPenalty = 0.85;
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

    /**
     * Regras para formas do Dragon Block Noea (addon de ButterJaffa). Precisam vir antes das genericas:
     * "godforms:supersaiyanrose" contem "god", mas o Rose e o equivalente do Blue para o Black.
     */
    /** Regras para formas do DMZ Sparking (addon de Yami). Precisam vir antes das genericas. */
    static List<Rule> sparkingRules() {
        List<Rule> r = new ArrayList<>();
        // Blue Aperfeicoado: o proprio controle perfeito do manga; selar nele rende muito e treina rapido
        r.add(new Rule("blueperfected", 0.90, 0.12, 0.06, 0.5, 4));
        r.add(new Rule("evildivineforms", 0.80, 0.10, 0.05, 2.0, 4));
        r.add(new Rule("ssj5", 0.40, 0.10, 0.04, 2.0, 5));
        r.add(new Rule("ssjlegendary", 0.30, 0.08, 0.03, 2.0, 4));
        r.add(new Rule("cellmax", 0.00, 0.00, 0.00, 1.0, 1));
        r.add(new Rule("divineforms", 0.55, 0.07, 0.03, 1.5, 3));
        return r;
    }

    static List<Rule> noeaRules() {
        List<Rule> r = new ArrayList<>();
        r.add(new Rule("rose", 0.80, 0.10, 0.05, 2.0, 4));
        r.add(new Rule("beastforms", 0.50, 0.10, 0.04, 2.0, 4));
        r.add(new Rule("frostgodforms", 0.60, 0.08, 0.04, 1.5, 3));
        r.add(new Rule("orangepiccolo", 0.45, 0.07, 0.03, 1.5, 3));
        r.add(new Rule("enhancewithgodki", 0.40, 0.06, 0.03, 1.5, 3));
        return r;
    }

    private static Map<String, String> defaultStoryRequirements() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("3", "beerus_saga:7");   // a licao do Whis (DMZ Sparking)
        m.put("4", "black_saga:3");    // o primeiro encontro com o Black
        m.put("5", "black_saga:7");    // o controle perfeito contra o Black e o Zamasu
        return m;
    }

    /** Integracao com outros add-ons (so vale quando eles estao instalados). */
    public static class Integrations {
        /** DMZ Sparking: meditar com a aura selada multiplica o treino do dominio. */
        public double sparkingMeditationTrainMultiplier = 2.0;
        /** DMZ Sparking: % de tensao aliviada por segundo meditando, mesmo selado. */
        public double sparkingMeditationStrainRelief = 2.0;
        /** DMZ Sparking: transformar no Blue Aperfeicoado ja sela a aura (controle perfeito do manga). */
        public boolean perfectedBlueAutoSeal = true;
        /** Olho Cereal mostra o poder de luta do alvo (usa o calculo do DMZ e dos add-ons instalados). */
        public boolean eyeShowsBattlePower = true;
        /**
         * Desvio automatico do Instinto Superior do Dragon Block Noea. Com o DMZ Sparking instalado, o
         * Instinto Superior ja tem o desvio do Sparking (esquiva do DMZ melhorada); os dois juntos
         * davam desvio dobrado. false = so o do Sparking. Sem o Sparking, o do Noea continua valendo.
         */
        public boolean noeaUltraInstinctDodge = false;
    }

    public Integrations integrations = new Integrations();

    /**
     * Visao de ki (luta as cegas). So para jogadores: sem enxergar, voce sente o ki de quem esta perto.
     * Liga com a Venda de Treino ou quando um Taiyoken te cega e voce tem Ki Sense.
     */
    public static class KiVision {
        public boolean enabled = true;
        /** Ticks do clarao do Taiyoken antes de fechar os olhos e sentir o ki (20 = 1 s). */
        public int taiyokenFlashTicks = 8;
        /** Alcance para sentir o ki, em blocos: base + por nivel do Ki Sense, ate o maximo. */
        public double baseRange = 6.0;
        public double rangePerKiSenseLevel = 6.0;
        public double maxRange = 48.0;
        /** Quantos blocos em volta ainda se enxerga (so o contorno, para nao cair de penhasco). */
        public double sightBlocks = 2.5;
        /** Quem esta com a aura selada aparece com esta fracao do brilho e do alcance. */
        public double sealedVisibility = 0.3;
        /** Venda de Treino: TP extra ganho em combate (0.20 = +20%), mais um tanto por nivel do Ki Sense. */
        public double blindfoldTpBonus = 0.20;
        public double blindfoldTpBonusPerKiSense = 0.05;
        public double blindfoldTpBonusMax = 0.50;
        /** Conta como "em combate" ate estes ticks depois de bater ou apanhar. */
        public int combatWindowTicks = 100;
    }

    public KiVision kiVision = new KiVision();

    /**
     * Treino do Senhor Kaioh: pegar o Bubbles sem voar, so correndo e pulando. 5 niveis; listas por nivel.
     */
    public static class Bubbles {
        public boolean enabled = true;
        /** O Bubbles aparece sozinho perto do Senhor Kaioh do DMZ. */
        public boolean spawnNearKingKai = true;
        /** Raio (blocos) em volta da casa dele por onde ele corre. */
        public int homeRadius = 14;
        public int timeSeconds = 60;
        /** Velocidade do Bubbles em relacao a sua (1.0 = igual). */
        public List<Double> speedFactors = new ArrayList<>(List.of(0.82, 0.92, 1.0, 1.07, 1.14));
        /** Chance de pular para o lado quando voce chega perto, e o intervalo minimo entre pulos (ticks). */
        public List<Double> leapChance = new ArrayList<>(List.of(0.35, 0.5, 0.65, 0.8, 0.9));
        public List<Integer> leapCooldownTicks = new ArrayList<>(List.of(40, 30, 22, 16, 12));
        /** Distancia em que ele para para dancar e te provocar. */
        public double tauntDistance = 9.0;
        /** Recompensa em "pontos de atributo" do DMZ (o TP de 1 ponto no seu nivel atual) por nivel. */
        public List<Double> rewardStatPoints = new ArrayList<>(List.of(1.0, 1.5, 2.0, 2.5, 3.0));
        /** A primeira vitoria em cada nivel rende isto vezes mais. */
        public double firstClearMultiplier = 2.0;
        /** Espera depois de pegar o Bubbles (minutos) e depois de falhar (segundos). */
        public int cooldownMinutes = 10;
        public int failCooldownSeconds = 10;
    }

    public Bubbles bubbles = new Bubbles();

    /** Movimento enquanto carrega o ki (o DMZ deixa o personagem parado). */
    public static class Movement {
        /** true = da para andar, correr, pular e voar carregando o ki. */
        public boolean moveWhileChargingKi = true;
        /** Velocidade enquanto carrega (1.0 = normal). */
        public double chargingMoveSpeed = 1.0;
    }

    public Movement movement = new Movement();

    private static List<Rule> defaultRules() {
        List<Rule> r = new ArrayList<>();
        // A ordem importa: a primeira regra que casar vence.
        // Principio do Goku na saga Black: o Blue e onde o controle mais compensa,
        // mas tambem e o mais dificil de dominar.
        // DMZ Sparking (Yami) e Dragon Block Noea (ButterJaffa): formas que nao caem bem nas regras genericas
        r.addAll(sparkingRules());
        r.addAll(noeaRules());
        r.add(new Rule("blue", 0.80, 0.10, 0.05, 2.0, 4));
        r.add(new Rule("god", 0.60, 0.08, 0.04, 1.5, 3));
        r.add(new Rule("ultrainstinct", 0.30, 0.06, 0.08, 2.5, 5));
        r.add(new Rule("ultraego", 0.30, 0.10, 0.02, 2.5, 5));
        r.add(new Rule("kaioken", 0.15, 0.03, 0.01, 1.5, 2));
        r.add(new Rule("oozaru", 0.00, 0.00, 0.00, 1.0, 1));
        r.add(new Rule("supersaiyan4", 0.40, 0.06, 0.03, 1.5, 4));
        r.add(new Rule("supersaiyan3", 0.25, 0.08, 0.02, 1.75, 3));
        r.add(new Rule("supersaiyan2", 0.40, 0.07, 0.03, 1.25, 2));
        r.add(new Rule("mastered", 0.60, 0.05, 0.03, 0.75, 1));
        r.add(new Rule("grade", 0.20, 0.03, 0.01, 1.0, 1));
        r.add(new Rule("fullpower", 0.30, 0.06, 0.02, 1.25, 2));
        r.add(new Rule("ultimate", 0.45, 0.06, 0.03, 1.5, 3));
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
                    loaded.migrate();
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

    /** Atualiza configs de versoes antigas sem perder o que o dono mudou. */
    private void migrate() {
        if (configVersion < 4) {
            // o selo agora usa a habilidade propria Aura Control em vez do "kicontrol" do DMZ
            if ("kicontrol".equalsIgnoreCase(requiredSkill)) requiredSkill = "aura_control";
            // niveis minimos do Aura Control nas regras padrao que o dono nao mudou
            Map<String, Integer> levels = new java.util.HashMap<>();
            for (Rule d : defaultRules()) levels.put(d.contains, d.minAuraLevel);
            if (rules != null) for (Rule r : rules) {
                if (r != null && r.contains != null && r.minAuraLevel <= 1 && levels.containsKey(r.contains)) r.minAuraLevel = levels.get(r.contains);
            }
            configVersion = 4;
        }
        if (configVersion < 6) {
            if (strain != null && Math.abs(strain.drainWeight - 0.5) < 1e-6) strain.drainWeight = 3.0;
            if (rules != null) {
                List<Rule> add = new ArrayList<>();
                for (Rule n : sparkingRules()) {
                    boolean has = false;
                    for (Rule r : rules) if (r != null && n.contains.equalsIgnoreCase(r.contains)) has = true;
                    if (!has) add.add(n);
                }
                rules.addAll(0, add);
            }
        }
        if (configVersion < 5) {
            // formas do Dragon Block Noea: entram no comeco (antes de "god"), so as que faltam
            if (rules != null) {
                List<Rule> add = new ArrayList<>();
                for (Rule n : noeaRules()) {
                    boolean has = false;
                    for (Rule r : rules) if (r != null && n.contains.equalsIgnoreCase(r.contains)) has = true;
                    if (!has) add.add(n);
                }
                rules.addAll(0, add);
            }
            configVersion = 5;
        }
        if (configVersion < 6) configVersion = 6;
    }

    private void fillMissing() {
        if (requiredSkill == null) requiredSkill = "";
        if (auraControlCosts == null || auraControlCosts.isEmpty()) auraControlCosts = new ArrayList<>(List.of(10000, 20000, 40000, 70000, 110000));
        if (auraControlTeacher == null) auraControlTeacher = "";
        if (auraControlTeachers == null) auraControlTeachers = new ArrayList<>();
        if (auraLevelStoryRequirements == null) auraLevelStoryRequirements = new LinkedHashMap<>();
        if (integrations == null) integrations = new Integrations();
        if (kiVision == null) kiVision = new KiVision();
        if (bubbles == null) bubbles = new Bubbles();
        if (movement == null) movement = new Movement();
        if (auraControlRequiredSaga == null) auraControlRequiredSaga = "";
        if (auraLevelEfficiency == null || auraLevelEfficiency.isEmpty()) auraLevelEfficiency = new ArrayList<>(List.of(0.60, 0.70, 0.80, 0.90, 1.00));
        if (auraLevelTrainSpeed == null || auraLevelTrainSpeed.isEmpty()) auraLevelTrainSpeed = new ArrayList<>(List.of(1.00, 1.15, 1.30, 1.45, 1.60));
        if (auraLevelStrain == null || auraLevelStrain.isEmpty()) auraLevelStrain = new ArrayList<>(List.of(1.00, 0.90, 0.80, 0.70, 0.60));
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

    /** Valor da lista para o nivel (1 = primeiro item; nivel acima do tamanho usa o ultimo). */
    public static double perLevel(List<Double> list, int level, double fallback) {
        if (list == null || list.isEmpty() || level <= 0) return fallback;
        Double v = list.get(Math.min(level, list.size()) - 1);
        return v == null ? fallback : v;
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
