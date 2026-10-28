package com.dmzkicontrol.control;

import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dmzkicontrol.network.SealAnimS2C;
import com.dmzkicontrol.network.SealStateS2C;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logica de servidor do Selamento de Aura ("Controle Perfeito de Ki").
 *
 * <h2>Desconto de ki</h2>
 * O DMZ cobra o upkeep da forma uma vez por segundo, usando {@link StatsData#getEffectiveEnergyDrain()}
 * e {@link StatsData#getEffectiveStaminaDrain()} (publicos). Todo tick comparamos ki/estamina com o tick
 * anterior (descontando a regen do mesmo tick); quando aparece a queda do upkeep, devolvemos
 * {@code reducao x upkeep}. Sem mixins e sem tocar em codigo interno do DMZ.
 *
 * <h2>Tensao (cansaco)</h2>
 * Medidor 0..1 por jogador. Enche enquanto selado, com velocidade que cai com o dominio
 * ({@code (1 - dominio/strainFree)^curva}); golpes recebidos e tecnicas de ki tambem enchem.
 * Efeitos progressivos: cansaco (estamina), corpo pesado (lentidao), dor (vida) e, a 100%,
 * a aura explode para fora. Esvazia com a aura livre.
 */
public final class AuraSealManager {

    private static final double UPKEEP_MATCH = 0.5;
    private static final int REFUND_COOLDOWN = 18;
    private static final int TOGGLE_COOLDOWN = 6;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static final class Session {
        public boolean sealed;
        /** Tick em que o selo pedido passa a valer (0 = nada pendente). */
        public long pendingAt;
        public boolean pendingCinematic;
        public int sealedTicks;
        /** Tensao 0..1. */
        public double strain;
        public float prevEnergy = Float.NaN;
        public float prevStamina = Float.NaN;
        public long lastKiRefund = Long.MIN_VALUE / 2;
        public long lastStaminaRefund = Long.MIN_VALUE / 2;
        public long busyUntil;
        public long lastToggle = Long.MIN_VALUE / 2;
        public long burstUntil;
        public long burstCooldownUntil;
        public long lastCombat = Long.MIN_VALUE / 2;
        public boolean hidAura;
        public boolean auraWasOn;
        public boolean warnedFatigue;
        public int trainTicker;
        public int hudTicker;
        public String lastKey;
        public double pendingKiRegen;
        public long kiRegenTick = Long.MIN_VALUE / 2;
        public double pendingStaminaRegen;
        public long staminaRegenTick = Long.MIN_VALUE / 2;
    }

    private AuraSealManager() {}

    // ================================================================== sessao

    public static Session session(Player p) {
        return SESSIONS.computeIfAbsent(p.getUUID(), id -> new Session());
    }

    public static boolean isSealed(Player p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && s.sealed;
    }

    public static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    /** Janela curta em que quedas de ki sao de ataque/dash/esquiva, nao de upkeep. */
    public static void markBusy(Player p, int ticks) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null && s.sealed) s.busyUntil = Math.max(s.busyUntil, p.level().getGameTime() + ticks);
    }

    public static void markCombat(Player p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) s.lastCombat = p.level().getGameTime();
    }

    /** Golpe recebido / tecnica disparada enquanto selado: segurar o ki fica mais dificil. */
    public static void addStrainFromEvent(ServerPlayer sp, double percent) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || !s.sealed || !KiControlConfig.get().strain.enabled) return;
        StatsData data = stats(sp);
        String key = data == null ? null : formKey(data);
        if (key == null) return;
        double m = MasteryStore.mastery(sp, key);
        if (m >= KiControlConfig.get().strain.strainFreeMastery) return;
        s.strain = Math.min(1.0, s.strain + percent / 100.0 * (1.0 - m));
    }

    public static void noteKiRegen(Player p, double amount) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.sealed) return;
        s.pendingKiRegen = Math.ceil(amount);
        s.kiRegenTick = p.level().getGameTime();
    }

    public static void noteStaminaRegen(Player p, double amount) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !s.sealed) return;
        s.pendingStaminaRegen = Math.ceil(amount);
        s.staminaRegenTick = p.level().getGameTime();
    }

    // ================================================================== leitura do DMZ

    public static StatsData stats(Player p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).resolve().orElse(null);
    }

    /** Chave "grupo:forma" da forma principal (ou da stack form, se for so ela), ou null na base. */
    public static String formKey(StatsData data) {
        var c = data.getCharacter();
        if (c.hasActiveForm()) return key(c.getActiveFormGroup(), c.getActiveForm());
        if (c.hasActiveStackForm()) return key(c.getActiveStackFormGroup(), c.getActiveStackForm());
        return null;
    }

    private static String key(String group, String form) {
        if (form == null || form.isEmpty()) return null;
        return (group == null || group.isEmpty()) ? form : group + ":" + form;
    }

    public static boolean hasStackOnTop(StatsData data) {
        var c = data.getCharacter();
        return c.hasActiveForm() && c.hasActiveStackForm();
    }

    public static boolean hasRequiredSkill(StatsData data) {
        String skill = KiControlConfig.get().requiredSkill;
        return skill == null || skill.isBlank() || data.getSkills().getSkillLevel(skill) > 0;
    }

    /** Nivel do Aura Control (1-5). Sem habilidade exigida na config = 5 (rendimento total). */
    public static int auraLevel(StatsData data) {
        String skill = KiControlConfig.get().requiredSkill;
        if (skill == null || skill.isBlank()) return 5;
        if (data == null) return 0;
        return Math.max(0, Math.min(5, data.getSkills().getSkillLevel(skill)));
    }

    public static int auraLevel(Player p) {
        return auraLevel(stats(p));
    }

    /** Fracao do rendimento maximo liberada pelo nivel do Aura Control. */
    private static double levelEfficiency(Player p) {
        KiControlConfig c = KiControlConfig.get();
        return KiControlConfig.perLevel(c.auraLevelEfficiency, auraLevel(p), 1.0);
    }

    /** Dominio de forma do proprio DMZ (0..1) na forma principal. */
    public static double dmzFormMastery(StatsData data) {
        var c = data.getCharacter();
        try {
            if (c.hasActiveForm()) {
                FormConfig.FormData fd = c.getActiveFormData();
                double max = fd != null && fd.getMaxMastery() != null ? fd.getMaxMastery() : 100.0;
                double m = c.getFormMasteries().getMastery(c.getActiveFormGroup(), c.getActiveForm());
                return max > 0 ? MasteryStore.clamp01(m / max) : 1.0;
            }
            if (c.hasActiveStackForm()) {
                FormConfig.FormData fd = c.getActiveStackFormData();
                double max = fd != null && fd.getMaxMastery() != null ? fd.getMaxMastery() : 100.0;
                double m = c.getFormMasteries().getMastery(c.getActiveStackFormGroup(), c.getActiveStackForm());
                return max > 0 ? MasteryStore.clamp01(m / max) : 1.0;
            }
        } catch (RuntimeException ignored) {
            // forma removida da config enquanto o jogador estava nela
        }
        return 0.0;
    }

    // ================================================================== numeros

    public static double kiReduction(Player p, String key, boolean stackOnTop) {
        KiControlConfig c = KiControlConfig.get();
        double r = c.tierFor(key).maxKiReduction * MasteryStore.mastery(p, key) * levelEfficiency(p);
        if (stackOnTop) {
            // "Blue Kaioken": com controle perfeito (Aura Control 5 e dominio sem tensao) o ki da stack
            // form tambem fica preso no corpo, entao quase nao se perde eficiencia
            boolean perfect = auraLevel(p) >= 5 && MasteryStore.mastery(p, key) >= c.strain.strainFreeMastery;
            r *= perfect ? Math.max(c.stackFormPenalty, c.perfectControlStackPenalty) : c.stackFormPenalty;
        }
        return Math.max(0.0, Math.min(0.95, r));
    }

    public static double powerBonus(Player p, String key) {
        return Math.max(0.0, KiControlConfig.get().tierFor(key).maxPowerBonus * MasteryStore.mastery(p, key) * levelEfficiency(p));
    }

    public static double critBonus(Player p, String key) {
        return Math.max(0.0, KiControlConfig.get().tierFor(key).maxCritBonus * MasteryStore.mastery(p, key) * levelEfficiency(p));
    }

    public static boolean isPerfect(Player p, String key) {
        KiControlConfig.Strain st = KiControlConfig.get().strain;
        return !st.enabled || MasteryStore.mastery(p, key) >= st.strainFreeMastery;
    }

    /** Tensao por segundo selado nesta forma (fracao 0..1). */
    public static double strainPerSecond(Player p, String key) {
        KiControlConfig c = KiControlConfig.get();
        KiControlConfig.Strain st = c.strain;
        if (!st.enabled) return 0.0;
        double m = MasteryStore.mastery(p, key);
        double free = Math.max(0.01, st.strainFreeMastery);
        if (m >= free) return 0.0;
        double relief = Math.pow(1.0 - m / free, Math.max(0.1, st.curve));
        double formMul = Math.max(0.1, c.tierFor(key).trainTimeMultiplier);
        double levelMul = KiControlConfig.perLevel(c.auraLevelStrain, auraLevel(p), 1.0);
        return st.fillPercentPerSecond / 100.0 * formMul * relief * Math.max(0.0, levelMul) * drainFactor(p);
    }

    /**
     * Formas que gastam muito ki forcam mais o selo: quanto maior o upkeep da forma (em % do ki maximo
     * por segundo, o custo cheio do DMZ antes da reducao do selo), mais rapido a tensao enche.
     */
    public static double drainFactor(Player p) {
        KiControlConfig.Strain st = KiControlConfig.get().strain;
        StatsData data = stats(p);
        if (data == null || st.drainWeight <= 0) return 1.0;
        double max = data.getMaxEnergy();
        if (max <= 0) return 1.0;
        double pct = Math.max(0.0, data.getEffectiveEnergyDrain()) / max * 100.0;
        return Math.min(Math.max(1.0, st.drainMaxFactor), 1.0 + st.drainWeight * pct);
    }

    /** Nome do efeito de multiplicador que o selo coloca no sistema de efeitos do DMZ. */
    public static final String POWER_EFFECT = "dmzkicontrol_seal";

    /** Bonus de poder total agora (selo + pico de liberacao), como fracao do multiplicador da forma. */
    public static double currentPowerBonus(ServerPlayer sp, StatsData data) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || data == null) return 0.0;
        String key = formKey(data);
        if (key == null) return 0.0;
        double bonus = s.sealed ? powerBonus(sp, key) : 0.0;
        if (sp.level().getGameTime() < s.burstUntil) {
            bonus += KiControlConfig.get().burst.damageBonus * MasteryStore.mastery(sp, key);
        }
        return bonus;
    }

    /**
     * Aplica o bonus de poder DENTRO do multiplicador do DMZ (sistema de efeitos, o mesmo da Fruta da
     * Arvore do Poder): sobe STR, SKP e PWR (e metade em DEF) de verdade, entao muda o power level e
     * os status, nao so o dano final.
     *
     * <p>O bonus e uma porcentagem do multiplicador da forma: no modo multiplicativo do DMZ o efeito
     * vale (1 + bonus); no modo aditivo (padrao) vale (1 + multForma x bonus), que da o mesmo resultado
     * em cima da forma. Ex.: SSJ3 x3.3 com +8% vira x3.56.
     */
    public static void updatePowerEffect(ServerPlayer sp, StatsData data) {
        try {
            var effects = data.getEffects();
            double bonus = currentPowerBonus(sp, data);
            if (bonus <= 0.0001) {
                if (effects.hasEffect(POWER_EFFECT)) {
                    effects.removeEffect(POWER_EFFECT);
                    StatsDeltaSync.markDirty(sp);
                }
                return;
            }
            boolean multiplicative = Boolean.TRUE.equals(com.dragonminez.common.config.ConfigManager.getServerConfig()
                    .getGameplay().getMultiplicationInsteadOfAdditionForMultipliers());
            double power;
            if (multiplicative) {
                power = 1.0 + bonus;
            } else {
                double formMult = data.getFormMultiplier("STR") + (data.getStackFormMultiplier("STR") - 1.0);
                power = 1.0 + Math.max(1.0, formMult) * bonus;
            }
            if (!effects.hasEffect(POWER_EFFECT) || Math.abs(effects.getEffectPower(POWER_EFFECT) - power) > 1e-4) {
                effects.addEffect(POWER_EFFECT, power, -1); // removido ao liberar/deslogar/logar
                StatsDeltaSync.markDirty(sp);
            }
        } catch (RuntimeException e) {
            // config do DMZ ainda nao carregada etc.: tenta de novo no proximo ciclo
        }
    }

    /** Remove o efeito que tenha sobrado (ex.: servidor fechado com o selo ativo). */
    public static void clearPowerEffect(ServerPlayer sp) {
        StatsData data = stats(sp);
        if (data != null && data.getEffects().hasEffect(POWER_EFFECT)) {
            data.getEffects().removeEffect(POWER_EFFECT);
            StatsDeltaSync.markDirty(sp);
        }
    }

    // ================================================================== acoes

    /** Tecla / botao do menu / comando. {@code cinematic} = o cliente vai mostrar a cutscene. */
    public static void requestToggle(ServerPlayer sp, boolean cinematic) {
        Session s = session(sp);
        long now = sp.level().getGameTime();
        if (s.pendingAt > 0) {           // apertou de novo durante a cutscene: pula direto para o selo
            finishSeal(sp);
            return;
        }
        if (now - s.lastToggle < TOGGLE_COOLDOWN) return;
        s.lastToggle = now;
        if (s.sealed) {
            release(sp, ReleaseReason.VOLUNTARY);
            return;
        }
        StatsData data = stats(sp);
        if (data == null || !canSeal(sp, data, s, true)) return;

        KiControlConfig c = KiControlConfig.get();
        int delay = Math.max(1, cinematic ? c.cutsceneSealDelayTicks : c.quickSealDelayTicks);
        s.pendingAt = now + delay;
        s.pendingCinematic = cinematic;
        broadcastAnim(sp, cinematic ? SealAnimS2C.CUTSCENE : SealAnimS2C.QUICK);
        if (!cinematic) sound(sp, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.8f);
        sendHud(sp, data);
    }

    private static boolean canSeal(ServerPlayer sp, StatsData data, Session s, boolean tellPlayer) {
        KiControlConfig c = KiControlConfig.get();
        if (!hasRequiredSkill(data)) {
            if (tellPlayer) sp.displayClientMessage(Component.translatable("message.dmzkicontrol.need_skill",
                    Component.translatable("skill.dragonminez." + c.requiredSkill)), true);
            return false;
        }
        if (formKey(data) == null) {
            if (tellPlayer) sp.displayClientMessage(Component.translatable("message.dmzkicontrol.need_form"), true);
            return false;
        }
        int needed = c.tierFor(formKey(data)).minAuraLevel;
        if (auraLevel(data) < needed) {
            if (tellPlayer) sp.displayClientMessage(Component.translatable("message.dmzkicontrol.need_aura_level",
                    needed, auraLevel(data)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (c.strain.enabled && s.strain * 100.0 >= c.strain.resealLockoutPercent) {
            if (tellPlayer) sp.displayClientMessage(Component.translatable("message.dmzkicontrol.exhausted",
                    pct(s.strain)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Conclui um selo pendente (fim da animacao/cutscene). */
    public static void finishSeal(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || s.pendingAt <= 0) return;
        s.pendingAt = 0;
        StatsData data = stats(sp);
        if (data == null || !canSeal(sp, data, s, true)) {
            broadcastAnim(sp, SealAnimS2C.CANCEL);
            if (data != null) sendHud(sp, data);
            return;
        }
        seal(sp, data, s);
    }

    private static void seal(ServerPlayer sp, StatsData data, Session s) {
        String key = formKey(data);
        s.sealed = true;
        if (KiControlConfig.get().hideAura) broadcastAnim(sp, SealAnimS2C.SEALED_ON);
        s.sealedTicks = 0;
        s.prevEnergy = Float.NaN;
        s.prevStamina = Float.NaN;
        s.warnedFatigue = false;
        s.trainTicker = 0;
        s.lastKey = key;
        s.busyUntil = sp.level().getGameTime() + 2;

        KiControlConfig c = KiControlConfig.get();
        s.auraWasOn = data.getStatus().isPermanentAura();
        s.hidAura = false;
        if (c.hideAura && s.auraWasOn) {
            data.getStatus().setPermanentAura(false);
            s.hidAura = true;
            StatsDeltaSync.markDirty(sp);
        }

        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.sealed",
                pct(MasteryStore.mastery(sp, key)), pct(kiReduction(sp, key, hasStackOnTop(data))), pct(powerBonus(sp, key)))
                .withStyle(ChatFormatting.AQUA), true);
        sound(sp, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
        updatePowerEffect(sp, data);
        sendHud(sp, data);
    }

    public enum ReleaseReason { VOLUNTARY, LOST_FORM, NO_SKILL, AURA_EXPANDED, CHARGED, STRAIN_BREAK, DEATH, SILENT }

    public static void release(ServerPlayer sp, ReleaseReason reason) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) return;
        if (s.pendingAt > 0 && !s.sealed) {     // cancelou durante a animacao
            s.pendingAt = 0;
            if (reason != ReleaseReason.SILENT && reason != ReleaseReason.DEATH) broadcastAnim(sp, SealAnimS2C.CANCEL);
            return;
        }
        if (!s.sealed) return;
        KiControlConfig c = KiControlConfig.get();
        StatsData data = stats(sp);
        long now = sp.level().getGameTime();
        int heldTicks = s.sealedTicks;
        String key = s.lastKey;

        s.sealed = false;
        broadcastAnim(sp, SealAnimS2C.SEALED_OFF);
        s.sealedTicks = 0;
        s.prevEnergy = Float.NaN;
        s.prevStamina = Float.NaN;

        if (data != null && s.hidAura && s.auraWasOn && reason != ReleaseReason.DEATH) {
            data.getStatus().setPermanentAura(true);
            StatsDeltaSync.markDirty(sp);
        }
        s.hidAura = false;

        switch (reason) {
            case VOLUNTARY -> {
                boolean burst = c.burst.enabled && key != null && data != null && formKey(data) != null
                        && heldTicks >= c.burst.minSealSeconds * 20.0 && now >= s.burstCooldownUntil;
                if (burst) {
                    s.burstUntil = now + (long) (c.burst.durationSeconds * 20.0);
                    s.burstCooldownUntil = now + (long) (c.burst.cooldownSeconds * 20.0);
                    double bonus = c.burst.damageBonus * MasteryStore.mastery(sp, key);
                    sp.displayClientMessage(Component.translatable("message.dmzkicontrol.burst", pct(bonus),
                            String.valueOf((int) c.burst.durationSeconds)).withStyle(ChatFormatting.GOLD), true);
                    sound(sp, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
                } else {
                    sp.displayClientMessage(Component.translatable("message.dmzkicontrol.released"), true);
                }
                sound(sp, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
                broadcastAnim(sp, SealAnimS2C.RELEASE);
            }
            case LOST_FORM -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.lost_form"), true);
            case NO_SKILL -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.lost_skill"), true);
            case AURA_EXPANDED -> {
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.aura_expanded"), true);
                broadcastAnim(sp, SealAnimS2C.RELEASE);
            }
            case CHARGED -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.charged"), true);
            case STRAIN_BREAK -> {
                if (data != null) {
                    var res = data.getResources();
                    double loss = MasteryStore.clamp01(c.strain.kiLossOnBreakPercent / 100.0);
                    res.setCurrentEnergy((float) (res.getCurrentEnergy() * (1.0 - loss)));
                }
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain_break")
                        .withStyle(ChatFormatting.RED), true);
                sound(sp, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.2f);
                sound(sp, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
                broadcastAnim(sp, SealAnimS2C.BREAK);
            }
            case DEATH, SILENT -> { }
        }
        if (data != null) {
            if (reason == ReleaseReason.SILENT) clearPowerEffect(sp);
            else updatePowerEffect(sp, data);
            sendHud(sp, data);
        }
    }

    // ================================================================== tick

    /** Chamado todo tick de servidor (fase END) para cada jogador. */
    public static void tick(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) {
            if (sp.tickCount % 40 == 0) {
                StatsData data = stats(sp);
                if (data != null && data.getEffects().hasEffect(POWER_EFFECT)) clearPowerEffect(sp); // sobra de save antigo
                if (data != null && formKey(data) != null) {
                    session(sp);
                    sendHud(sp, data);
                }
            }
            return;
        }

        StatsData data = stats(sp);
        if (data == null) return;
        KiControlConfig c = KiControlConfig.get();
        long now = sp.level().getGameTime();

        // ---- selo pedido (animacao / cutscene em andamento)
        if (s.pendingAt > 0) {
            if (formKey(data) == null) { release(sp, ReleaseReason.LOST_FORM); }
            else if (now >= s.pendingAt) { finishSeal(sp); }
        }

        if (sp.tickCount % 5 == 0) updatePowerEffect(sp, data); // pico de liberacao acabando, mudanca de forma etc.

        if (!s.sealed) {
            // aura livre: o corpo se recupera
            if (s.strain > 0) {
                String key = formKey(data);
                double m = key == null ? 0.0 : MasteryStore.mastery(sp, key);
                s.strain = Math.max(0.0, s.strain - c.strain.recoverPercentPerSecond / 100.0 * (1.0 + m) / 20.0);
            }
            int interval = s.strain > 0 || s.pendingAt > 0 ? Math.max(1, c.hudSyncTicks) : 40;
            if (++s.hudTicker >= interval) {
                s.hudTicker = 0;
                sendHud(sp, data);
            }
            return;
        }

        String key = formKey(data);
        if (key == null) { release(sp, ReleaseReason.LOST_FORM); return; }
        if (!hasRequiredSkill(data)) { release(sp, ReleaseReason.NO_SKILL); return; }
        if (auraLevel(data) < c.tierFor(key).minAuraLevel) { release(sp, ReleaseReason.NO_SKILL); return; }

        if (!key.equals(s.lastKey)) {
            s.lastKey = key;
            s.prevEnergy = Float.NaN;
            s.prevStamina = Float.NaN;
            s.busyUntil = now + 5;
        }

        if (c.hideAura && data.getStatus().isPermanentAura()) {
            if (c.auraToggleReleasesSeal) { release(sp, ReleaseReason.AURA_EXPANDED); return; }
            data.getStatus().setPermanentAura(false);
            s.hidAura = true;
            s.auraWasOn = true;
            StatsDeltaSync.markDirty(sp);
        }

        boolean stack = hasStackOnTop(data);
        double reduction = kiReduction(sp, key, stack);
        var res = data.getResources();
        float energy = res.getCurrentEnergy();
        float stamina = res.getCurrentStamina();
        boolean survival = !sp.isCreative() && !sp.isSpectator();
        double kiRegenAdj = now - s.kiRegenTick <= 1 ? s.pendingKiRegen : 0.0;
        double stRegenAdj = now - s.staminaRegenTick <= 1 ? s.pendingStaminaRegen : 0.0;
        s.kiRegenTick = Long.MIN_VALUE / 2;
        s.staminaRegenTick = Long.MIN_VALUE / 2;

        // ---- desconto do upkeep
        if (survival && now >= s.busyUntil) {
            long expectedKi = Math.round(data.getEffectiveEnergyDrain());
            if (expectedKi > 0 && reduction > 0 && !Float.isNaN(s.prevEnergy)) {
                float drop = s.prevEnergy - energy + (float) kiRegenAdj;
                if (drop >= expectedKi * UPKEEP_MATCH && now - s.lastKiRefund >= REFUND_COOLDOWN) {
                    res.setCurrentEnergy(energy + (float) (Math.min(drop, expectedKi) * reduction));
                    energy = res.getCurrentEnergy();
                    s.lastKiRefund = now;
                }
            }
            double stRed = reduction * MasteryStore.clamp01(c.staminaReductionShare);
            long expectedSt = Math.round(data.getEffectiveStaminaDrain());
            if (expectedSt > 0 && stRed > 0 && !Float.isNaN(s.prevStamina)) {
                float drop = s.prevStamina - stamina + (float) stRegenAdj;
                if (drop >= expectedSt * UPKEEP_MATCH && now - s.lastStaminaRefund >= REFUND_COOLDOWN) {
                    res.setCurrentStamina(stamina + (float) (Math.min(drop, expectedSt) * stRed));
                    stamina = res.getCurrentStamina();
                    s.lastStaminaRefund = now;
                }
            }
        }

        // ---- tensao: enche selado; com controle perfeito, alivia mesmo selado
        double perSecond = strainPerSecond(sp, key);
        if (perSecond > 0) s.strain = Math.min(1.0, s.strain + perSecond / 20.0);
        else if (s.strain > 0) s.strain = Math.max(0.0, s.strain - c.strain.recoverPercentPerSecond / 100.0 / 20.0);

        // ---- treino organico (uma vez por segundo)
        if (++s.trainTicker >= 20) {
            s.trainTicker = 0;
            train(sp, data, s, key, c);
        }

        // ---- efeitos da tensao
        s.sealedTicks++;
        if (c.strain.enabled) {
            KiControlConfig.Strain st = c.strain;
            double pctStrain = s.strain * 100.0;
            if (s.strain >= 1.0) {
                release(sp, ReleaseReason.STRAIN_BREAK);
                return;
            }
            if (pctStrain >= st.fatiguePercent) {
                if (!s.warnedFatigue) {
                    s.warnedFatigue = true;
                    sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain_warning")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
                if (survival && sp.tickCount % 20 == 0) {
                    res.setCurrentStamina((float) (res.getCurrentStamina()
                            - data.getMaxStamina() * MasteryStore.clamp01(st.staminaPercentPerSecond / 100.0)));
                    stamina = res.getCurrentStamina();
                }
            } else if (pctStrain < st.fatiguePercent - 10.0) {
                s.warnedFatigue = false;
            }
            if (survival && pctStrain >= st.slownessPercent && sp.tickCount % 10 == 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false, true));
            }
            if (survival && pctStrain >= st.hurtPercent && sp.tickCount % Math.max(1, st.pulseTicks) == 0) {
                float hpLoss = (float) (sp.getMaxHealth() * MasteryStore.clamp01(st.healthPercentPerPulse / 100.0));
                sp.setHealth(Math.max(1.0f, sp.getHealth() - hpLoss));
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain")
                        .withStyle(ChatFormatting.RED), true);
                sound(sp, SoundEvents.PLAYER_HURT, 0.6f, 0.6f);
            }
        }
        s.prevEnergy = energy;
        s.prevStamina = stamina;

        if (++s.hudTicker >= Math.max(1, c.hudSyncTicks)) {
            s.hudTicker = 0;
            sendHud(sp, data);
        }
    }

    /** 1 s de pratica, ponderado pela situacao. */
    private static void train(ServerPlayer sp, StatsData data, Session s, String key, KiControlConfig c) {
        KiControlConfig.Mastery mc = c.mastery;
        long now = sp.level().getGameTime();
        double gain = 1.0 + dmzFormMastery(data) * Math.max(0.0, c.dmzMasteryTrainBoost);
        gain *= Math.max(0.0, KiControlConfig.perLevel(c.auraLevelTrainSpeed, auraLevel(data), 1.0));
        if (now - s.lastCombat <= mc.combatWindowSeconds * 20.0) gain *= Math.max(0.0, mc.combatMultiplier);
        double pctStrain = s.strain * 100.0;
        if (pctStrain >= c.strain.hurtPercent) gain *= Math.max(0.0, mc.overstrainMultiplier);
        else if (pctStrain >= 40.0) gain *= Math.max(0.0, mc.pushingLimitMultiplier);

        double before = MasteryStore.mastery(sp, key);
        MasteryStore.addTrained(sp, key, gain);
        double after = MasteryStore.mastery(sp, key);

        int[] milestones = {25, 50, 75};
        for (int ms : milestones) {
            if (before * 100 < ms && after * 100 >= ms) {
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.milestone_" + ms, key)
                        .withStyle(ChatFormatting.AQUA), false);
                sound(sp, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
            }
        }
        if (c.strain.enabled && before < c.strain.strainFreeMastery && after >= c.strain.strainFreeMastery) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.no_more_strain", key)
                    .withStyle(ChatFormatting.AQUA), false);
            sound(sp, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        }
        if (before < 1.0 && after >= 1.0) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.perfect", key)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            sound(sp, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
        }
    }

    // ================================================================== rede / util

    public static void sendHud(ServerPlayer sp, StatsData data) {
        Session s = session(sp);
        String key = formKey(data);
        boolean skill = hasRequiredSkill(data);
        float mastery = 0f, red = 0f, power = 0f;
        boolean perfect = false;
        if (key != null) {
            mastery = (float) MasteryStore.mastery(sp, key);
            red = (float) kiReduction(sp, key, hasStackOnTop(data));
            power = (float) powerBonus(sp, key);
            perfect = isPerfect(sp, key);
        }
        int burstTicks = (int) Math.max(0, s.burstUntil - sp.level().getGameTime());
        KiControlConfig.Strain st = KiControlConfig.get().strain;
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new SealStateS2C(s.sealed, s.pendingAt > 0, key == null ? "" : key, skill, mastery, red, power,
                        (float) s.strain, perfect, (float) (st.fatiguePercent / 100.0), (float) (st.hurtPercent / 100.0),
                        burstTicks));
    }

    /** Quem passa a ver um jogador selado tambem precisa esconder a aura dele. */
    public static void onStartTracking(ServerPlayer viewer, Player target) {
        Session s = SESSIONS.get(target.getUUID());
        if (s != null && s.sealed && KiControlConfig.get().hideAura) {
            KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new SealAnimS2C(target.getId(), SealAnimS2C.SEALED_ON));
        }
    }

    /** Quem deixa de ver o jogador esquece o estado dele (os ids de entidade sao reaproveitados). */
    public static void onStopTracking(ServerPlayer viewer, Player target) {
        Session s = SESSIONS.get(target.getUUID());
        if (s != null && s.sealed) {
            KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new SealAnimS2C(target.getId(), SealAnimS2C.SEALED_OFF));
        }
    }

    /** Trocou de dimensao/renasceu: o id de entidade mudou, reenvia o estado do selo. */
    public static void resendSealedState(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s != null && s.sealed && KiControlConfig.get().hideAura) broadcastAnim(sp, SealAnimS2C.SEALED_ON);
    }

    private static void broadcastAnim(ServerPlayer sp, byte type) {
        KiControlNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new SealAnimS2C(sp.getId(), type));
    }

    private static void sound(ServerPlayer sp, SoundEvent ev, float vol, float pitch) {
        if (!KiControlConfig.get().playSounds) return;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    public static String pct(double v) {
        return String.valueOf(Math.round(v * 100.0));
    }
}
