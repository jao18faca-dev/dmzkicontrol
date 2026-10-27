package com.dmzkicontrol.control;

import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.KiControlNetwork;
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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logica de servidor do Selamento de Aura ("Controle Perfeito de Ki").
 *
 * <h2>Como o desconto funciona</h2>
 * O DMZ cobra o upkeep da forma uma vez por segundo ({@code TickHandler.handleActiveFormDrains}),
 * usando {@link StatsData#getEffectiveEnergyDrain()} e {@link StatsData#getEffectiveStaminaDrain()}.
 * Esses metodos sao publicos, entao sabemos exatamente quanto o DMZ vai cobrar. Todo tick comparamos
 * o ki/estamina com o tick anterior: quando aparece a queda do upkeep, devolvemos
 * {@code reducao x upkeep}. Efeito liquido: manter a forma custa menos, sem tocar em codigo interno
 * do DMZ nem em mixins (como a wiki recomenda).
 */
public final class AuraSealManager {

    /** Uma queda so conta como upkeep se for pelo menos esta fracao do upkeep esperado. */
    private static final double UPKEEP_MATCH = 0.5;
    /** Intervalo minimo entre dois reembolsos (o DMZ cobra a cada 20 ticks). */
    private static final int REFUND_COOLDOWN = 18;
    private static final int TOGGLE_COOLDOWN = 10;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static final class Session {
        public boolean sealed;
        public int sealedTicks;
        public float prevEnergy = Float.NaN;
        public float prevStamina = Float.NaN;
        public long lastKiRefund = Long.MIN_VALUE / 2;
        public long lastStaminaRefund = Long.MIN_VALUE / 2;
        public long busyUntil;
        public long lastToggle = Long.MIN_VALUE / 2;
        public long burstUntil;
        public long burstCooldownUntil;
        public boolean hidAura;
        public boolean auraWasOn;
        public boolean strainWarned;
        public boolean strainActive;
        public int trainTicker;
        public int hudTicker;
        public String lastKey;
        public double savedKi;
        /** Regen do DMZ aplicada no mesmo tick do upkeep (o DMZ faz os dois no mesmo tick). */
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

    public static boolean isBursting(Player p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.burstUntil;
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

    /** Registra a regen final do DMZ deste segundo (chamado pelo EnergyRegenEvent, prioridade LOWEST). */
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
        double r = c.tierFor(key).maxKiReduction * MasteryStore.mastery(p, key);
        if (stackOnTop) r *= c.stackFormPenalty;
        return Math.max(0.0, Math.min(0.95, r));
    }

    public static double powerBonus(Player p, String key) {
        return Math.max(0.0, KiControlConfig.get().tierFor(key).maxPowerBonus * MasteryStore.mastery(p, key));
    }

    public static double critBonus(Player p, String key) {
        return Math.max(0.0, KiControlConfig.get().tierFor(key).maxCritBonus * MasteryStore.mastery(p, key));
    }

    /** Bonus de dano total agora (selo + pico de liberacao). */
    public static double currentDamageBonus(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) return 0.0;
        StatsData data = stats(sp);
        if (data == null) return 0.0;
        String key = formKey(data);
        if (key == null) return 0.0;
        double bonus = s.sealed ? powerBonus(sp, key) : 0.0;
        if (sp.level().getGameTime() < s.burstUntil) {
            bonus += KiControlConfig.get().burst.damageBonus * MasteryStore.mastery(sp, key);
        }
        return bonus;
    }

    /** Ticks que o jogador aguenta selado nesta forma; -1 = sem limite (controle perfeito). */
    public static int strainLimitTicks(Player p, String key) {
        KiControlConfig.Strain st = KiControlConfig.get().strain;
        if (!st.enabled) return -1;
        double m = MasteryStore.mastery(p, key);
        if (m >= st.strainFreeMastery) return -1;
        return (int) Math.max(20, (st.baseSeconds + st.extraSecondsAtFullMastery * m) * 20.0);
    }

    // ================================================================== acoes

    public static void toggle(ServerPlayer sp) {
        Session s = session(sp);
        long now = sp.level().getGameTime();
        if (now - s.lastToggle < TOGGLE_COOLDOWN) return;
        s.lastToggle = now;
        if (s.sealed) release(sp, ReleaseReason.VOLUNTARY);
        else seal(sp);
    }

    public static boolean seal(ServerPlayer sp) {
        Session s = session(sp);
        if (s.sealed) return true;
        StatsData data = stats(sp);
        if (data == null) return false;

        if (!hasRequiredSkill(data)) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.need_skill",
                    Component.translatable("skill.dragonminez." + KiControlConfig.get().requiredSkill)), true);
            return false;
        }
        String key = formKey(data);
        if (key == null) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.need_form"), true);
            return false;
        }

        s.sealed = true;
        s.sealedTicks = 0;
        s.prevEnergy = Float.NaN;
        s.prevStamina = Float.NaN;
        s.strainWarned = false;
        s.strainActive = false;
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

        boolean stack = hasStackOnTop(data);
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.sealed",
                pct(MasteryStore.mastery(sp, key)), pct(kiReduction(sp, key, stack)), pct(powerBonus(sp, key)))
                .withStyle(ChatFormatting.AQUA), true);
        sound(sp, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
        sendHud(sp, data);
        return true;
    }

    public enum ReleaseReason { VOLUNTARY, LOST_FORM, NO_SKILL, AURA_EXPANDED, CHARGED, STRAIN_BREAK, DEATH, SILENT }

    public static void release(ServerPlayer sp, ReleaseReason reason) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null || !s.sealed) return;
        KiControlConfig c = KiControlConfig.get();
        StatsData data = stats(sp);
        long now = sp.level().getGameTime();
        int heldTicks = s.sealedTicks;
        String key = s.lastKey;

        s.sealed = false;
        s.sealedTicks = 0;
        s.prevEnergy = Float.NaN;
        s.prevStamina = Float.NaN;
        s.strainActive = false;

        // devolve a aura permanente que escondemos (se o jogador a usava)
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
            }
            case LOST_FORM -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.lost_form"), true);
            case NO_SKILL -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.lost_skill"), true);
            case AURA_EXPANDED -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.aura_expanded"), true);
            case CHARGED -> sp.displayClientMessage(Component.translatable("message.dmzkicontrol.charged"), true);
            case STRAIN_BREAK -> {
                if (data != null) {
                    var res = data.getResources();
                    res.setCurrentEnergy((float) (res.getCurrentEnergy() * (1.0 - MasteryStore.clamp01(c.strain.kiLossOnBreak))));
                }
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain_break")
                        .withStyle(ChatFormatting.RED), true);
                sound(sp, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
            }
            case DEATH, SILENT -> { }
        }
        if (data != null) sendHud(sp, data);
    }

    // ================================================================== tick

    /** Chamado todo tick de servidor (fase END) para cada jogador. */
    public static void tick(ServerPlayer sp) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) {
            // HUD "ocioso": mostra o dominio da forma atual de vez em quando
            if (sp.tickCount % 40 == 0) {
                StatsData data = stats(sp);
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

        if (!s.sealed) {
            if (++s.hudTicker >= 40) {
                s.hudTicker = 0;
                sendHud(sp, data);
            }
            return;
        }

        String key = formKey(data);
        if (key == null) { release(sp, ReleaseReason.LOST_FORM); return; }
        if (!hasRequiredSkill(data)) { release(sp, ReleaseReason.NO_SKILL); return; }

        if (!key.equals(s.lastKey)) {
            // trocou de forma sem sair do selo: recomeca a contagem de tensao, ignora o custo da troca
            s.lastKey = key;
            s.sealedTicks = 0;
            s.strainWarned = false;
            s.prevEnergy = Float.NaN;
            s.prevStamina = Float.NaN;
            s.busyUntil = sp.level().getGameTime() + 5;
        }

        // aura: o ki fica preso dentro do corpo
        if (c.hideAura && data.getStatus().isPermanentAura()) {
            if (c.auraToggleReleasesSeal) { release(sp, ReleaseReason.AURA_EXPANDED); return; }
            data.getStatus().setPermanentAura(false);
            s.hidAura = true;
            s.auraWasOn = true;
            StatsDeltaSync.markDirty(sp);
        }

        long now = sp.level().getGameTime();
        boolean stack = hasStackOnTop(data);
        double reduction = kiReduction(sp, key, stack);
        var res = data.getResources();
        float energy = res.getCurrentEnergy();
        float stamina = res.getCurrentStamina();
        boolean drainsApply = !sp.isCreative() && !sp.isSpectator();
        // regen do DMZ deste segundo (registrada no mesmo tick ou no anterior); consumida uma unica vez
        double kiRegenAdj = now - s.kiRegenTick <= 1 ? s.pendingKiRegen : 0.0;
        double stRegenAdj = now - s.staminaRegenTick <= 1 ? s.pendingStaminaRegen : 0.0;
        s.kiRegenTick = Long.MIN_VALUE / 2;
        s.staminaRegenTick = Long.MIN_VALUE / 2;

        if (drainsApply && now >= s.busyUntil) {
            // ---- ki
            long expectedKi = Math.round(data.getEffectiveEnergyDrain());
            if (expectedKi > 0 && reduction > 0 && !Float.isNaN(s.prevEnergy)) {
                float drop = s.prevEnergy - energy;
                drop += (float) kiRegenAdj; // desconta a regen aplicada junto com o upkeep
                if (drop >= expectedKi * UPKEEP_MATCH && now - s.lastKiRefund >= REFUND_COOLDOWN) {
                    float refund = (float) (Math.min(drop, expectedKi) * reduction);
                    res.setCurrentEnergy(energy + refund);   // o DMZ ja limita ao maximo
                    energy = res.getCurrentEnergy();
                    s.lastKiRefund = now;
                    s.savedKi += refund;
                }
            }
            // ---- estamina
            double stRed = reduction * MasteryStore.clamp01(c.staminaReductionShare);
            long expectedSt = Math.round(data.getEffectiveStaminaDrain());
            if (expectedSt > 0 && stRed > 0 && !Float.isNaN(s.prevStamina)) {
                float drop = s.prevStamina - stamina;
                drop += (float) stRegenAdj;
                if (drop >= expectedSt * UPKEEP_MATCH && now - s.lastStaminaRefund >= REFUND_COOLDOWN) {
                    res.setCurrentStamina(stamina + (float) (Math.min(drop, expectedSt) * stRed));
                    stamina = res.getCurrentStamina();
                    s.lastStaminaRefund = now;
                }
            }
        }
        s.prevEnergy = energy;
        s.prevStamina = stamina;

        // ---- treino: 1 s de pratica por segundo selado, acelerado pelo dominio de forma do DMZ
        if (++s.trainTicker >= 20) {
            s.trainTicker = 0;
            double speed = 1.0 + dmzFormMastery(data) * Math.max(0.0, c.dmzMasteryTrainBoost);
            double before = MasteryStore.mastery(sp, key);
            MasteryStore.addTrained(sp, key, speed);
            double after = MasteryStore.mastery(sp, key);
            if (before < 1.0 && after >= 1.0) {
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.perfect", key)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                sound(sp, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
            } else if (c.strain.enabled && before < c.strain.strainFreeMastery && after >= c.strain.strainFreeMastery) {
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.no_more_strain", key)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }

        // ---- tensao interna
        s.sealedTicks++;
        int limit = strainLimitTicks(sp, key);
        if (limit > 0) {
            if (!s.strainWarned && s.sealedTicks >= limit * 0.8) {
                s.strainWarned = true;
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain_warning")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            if (s.sealedTicks > limit) {
                s.strainActive = true;
                KiControlConfig.Strain st = c.strain;
                if (s.sealedTicks >= limit * Math.max(1.0, st.breakAtLimitMultiplier)) {
                    release(sp, ReleaseReason.STRAIN_BREAK);
                    return;
                }
                if ((s.sealedTicks - limit) % Math.max(1, st.pulseTicks) == 0 && drainsApply) {
                    float hpLoss = (float) (sp.getMaxHealth() * MasteryStore.clamp01(st.healthPercentPerPulse));
                    sp.setHealth(Math.max(1.0f, sp.getHealth() - hpLoss)); // tensao nao mata sozinha
                    res.setCurrentStamina((float) (res.getCurrentStamina() - data.getMaxStamina() * MasteryStore.clamp01(st.staminaPercentPerPulse)));
                    s.prevStamina = res.getCurrentStamina();
                    sp.displayClientMessage(Component.translatable("message.dmzkicontrol.strain")
                            .withStyle(ChatFormatting.RED), true);
                    sound(sp, SoundEvents.PLAYER_HURT, 0.6f, 0.6f);
                }
            }
        }

        if (++s.hudTicker >= Math.max(1, c.hudSyncTicks)) {
            s.hudTicker = 0;
            sendHud(sp, data);
        }
    }

    // ================================================================== HUD / util

    public static void sendHud(ServerPlayer sp, StatsData data) {
        Session s = session(sp);
        String key = formKey(data);
        boolean skill = hasRequiredSkill(data);
        float mastery = 0f, red = 0f, power = 0f, strain = -1f;
        if (key != null) {
            mastery = (float) MasteryStore.mastery(sp, key);
            red = (float) kiReduction(sp, key, hasStackOnTop(data));
            power = (float) powerBonus(sp, key);
            int limit = strainLimitTicks(sp, key);
            if (limit > 0) strain = s.sealed ? (float) s.sealedTicks / limit : 0f;
        }
        int burstTicks = (int) Math.max(0, s.burstUntil - sp.level().getGameTime());
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new SealStateS2C(s.sealed, key == null ? "" : key, skill, mastery, red, power, strain,
                        (float) Math.max(1.0, KiControlConfig.get().strain.breakAtLimitMultiplier), burstTicks));
    }

    private static void sound(ServerPlayer sp, SoundEvent ev, float vol, float pitch) {
        if (!KiControlConfig.get().playSounds) return;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    public static String pct(double v) {
        return String.valueOf(Math.round(v * 100.0));
    }
}
