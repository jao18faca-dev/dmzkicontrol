package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.network.EyeMarkS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Olho Cereal (habilidade racial dos Cerealianos), lado do servidor.
 *
 * <p>A skill "Mira" (cereal_eye, niveis 1-4 comprados com TP no menu de skills do DMZ) define
 * quantos pontos fracos o olho enxerga no corpo do alvo. Segurando a tecla, o alvo na mira fica
 * marcado com esses pontos. Cada golpe corpo a corpo consome um ponto (dano extra + ignora defesa);
 * um tiro que acerta perto de um ponto e um "tiro preciso" (bonus maior). Tiros de longe ganham
 * bonus de atirador. As formas da raca e a Mira Cereal (item) fortalecem tudo isso.
 */
public final class CerealianEye {
    public static final String SKILL = "cereal_eye";
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        boolean active;
        int markedId = -1;
        int mask;
        long markUntil;
        int ticker;
    }

    private CerealianEye() {}

    public static boolean isCerealian(Player p) {
        StatsData data = AuraSealManager.stats(p);
        if (data == null) return false;
        String race = data.getCharacter().getRaceName();
        return race != null && race.toLowerCase(Locale.ROOT).equals(KiControlConfig.get().cerealian.raceKey.toLowerCase(Locale.ROOT));
    }

    /** Nivel da Mira (1-4) + 1 com a Mira Cereal equipada. */
    public static int eyeLevel(Player p) {
        StatsData data = AuraSealManager.stats(p);
        int lvl = data == null ? 1 : Math.max(1, Math.min(4, data.getSkills().getSkillLevel(SKILL)));
        if (CerealScopeItem.isWearing(p)) lvl++;
        return Math.min(WeakPoints.MAX, lvl);
    }

    /** Multiplicador dos bonus pelo estado do olho (formas da raca). */
    public static double formMultiplier(StatsData data) {
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        String key = AuraSealManager.formKey(data);
        if (key == null) return 1.0;
        String k = key.toLowerCase(Locale.ROOT);
        if (!c.twinEyesForm.isEmpty() && k.contains(c.twinEyesForm)) return c.twinEyesMultiplier;
        if (!c.awakenedEyeForm.isEmpty() && k.contains(c.awakenedEyeForm)) return c.awakenedEyeMultiplier;
        return 1.0;
    }

    public static double range(Player p) {
        double r = KiControlConfig.get().cerealian.eyeRange + 8.0 * (eyeLevel(p) - 1);
        return CerealScopeItem.isWearing(p) ? r + 16.0 : r;
    }

    public static void setActive(ServerPlayer sp, boolean active) {
        State st = STATES.computeIfAbsent(sp.getUUID(), id -> new State());
        if (active && !isCerealian(sp)) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_not_cerealian"), true);
            return;
        }
        if (active == st.active) return;
        st.active = active;
        broadcastEye(sp, active);
        st.ticker = 0;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                active ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** Avisa quem esta vendo (e o proprio jogador) para mostrar/esconder a mira no olho. */
    private static void broadcastEye(ServerPlayer sp, boolean on) {
        KiControlNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new com.dmzkicontrol.network.SealAnimS2C(sp.getId(), on ? com.dmzkicontrol.network.SealAnimS2C.EYE_ON : com.dmzkicontrol.network.SealAnimS2C.EYE_OFF));
    }

    /** Quem passa a ver um Cerealiano com o olho ligado tambem ve a mira. */
    public static void onStartTracking(ServerPlayer viewer, Player target) {
        State st = STATES.get(target.getUUID());
        if (st != null && st.active) {
            KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new com.dmzkicontrol.network.SealAnimS2C(target.getId(), com.dmzkicontrol.network.SealAnimS2C.EYE_ON));
        }
    }

    public static void tick(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        long now = sp.level().getGameTime();
        if (st.markedId >= 0 && (now > st.markUntil || st.mask == 0)) clearMark(sp, st);
        if (!st.active) return;
        if (!isCerealian(sp) || !sp.isAlive()) { st.active = false; broadcastEye(sp, false); return; }

        StatsData data = AuraSealManager.stats(sp);
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        if (++st.ticker % 20 == 0 && data != null && !sp.isCreative()) {
            var res = data.getResources();
            double pct = c.eyeKiCostPercentPerSecond * (1.0 - 0.1 * (eyeLevel(sp) - 1)) * (CerealScopeItem.isWearing(sp) ? 0.5 : 1.0);
            float cost = (float) (data.getMaxEnergy() * Math.max(0.0, pct) / 100.0);
            if (res.getCurrentEnergy() < cost) {
                st.active = false;
                broadcastEye(sp, false);
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_no_ki").withStyle(ChatFormatting.RED), true);
                return;
            }
            res.setCurrentEnergy(res.getCurrentEnergy() - cost);
        }
        if (st.ticker % 4 == 0) {
            LivingEntity target = lookTarget(sp, range(sp));
            if (target != null) {
                if (target.getId() != st.markedId) {
                    st.markedId = target.getId();
                    st.mask = (1 << eyeLevel(sp)) - 1;
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
                }
                st.markUntil = now + (long) (c.markSeconds * 20);
                send(sp, st);
            }
        }
    }

    private static LivingEntity lookTarget(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        var blockHit = sp.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double max = blockHit.getLocation().distanceToSqr(eye);
        AABB box = sp.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != sp && !e.isSpectator(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static boolean isMarked(State st, Entity victim, long now) {
        return st != null && st.markedId == victim.getId() && now <= st.markUntil && st.mask != 0;
    }

    /** Golpe corpo a corpo/strike no alvo marcado: consome um ponto fraco. {multDano, penetracao} ou null. */
    public static double[] consumeWeakPoint(ServerPlayer attacker, Entity victim) {
        State st = STATES.get(attacker.getUUID());
        if (!isMarked(st, victim, attacker.level().getGameTime())) return null;
        StatsData data = AuraSealManager.stats(attacker);
        if (data == null || !isCerealian(attacker)) return null;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        double mul = formMultiplier(data);
        int bit = Integer.numberOfTrailingZeros(st.mask);
        st.mask &= ~(1 << bit);
        weakPointFx(attacker, victim instanceof LivingEntity le ? WeakPoints.position(le, bit, 1.0f) : victim.position());
        send(attacker, st);
        return new double[]{1.0 + c.weakPointDamageBonus * mul, c.weakPointDefensePenetration * mul};
    }

    /** Tiro que entrou perto de um ponto fraco ativo: "tiro preciso". Devolve o multiplicador (1 = nada). */
    public static double preciseHitMultiplier(ServerPlayer shooter, LivingEntity victim, Vec3 impact) {
        return preciseHit(shooter, victim, impact, 0.65, -1);
    }

    /**
     * Versao dos tiros cerealianos: raio de acerto maior e, se o tiro veio travado num ponto
     * ({@code lockedIdx} >= 0), esse ponto conta mesmo que o impacto tenha saido um pouco do lugar.
     */
    public static double preciseHit(ServerPlayer shooter, LivingEntity victim, Vec3 impact, double radius, int lockedIdx) {
        State st = STATES.get(shooter.getUUID());
        if (!isMarked(st, victim, shooter.level().getGameTime()) || !isCerealian(shooter)) return 1.0;
        int idx = lockedIdx >= 0 && (st.mask & (1 << lockedIdx)) != 0 ? lockedIdx : WeakPoints.nearest(victim, st.mask, impact, radius);
        if (idx < 0) return 1.0;
        StatsData data = AuraSealManager.stats(shooter);
        double mul = data == null ? 1.0 : formMultiplier(data);
        st.mask &= ~(1 << idx);
        weakPointFx(shooter, WeakPoints.position(victim, idx, 1.0f));
        shooter.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_precise").withStyle(ChatFormatting.GOLD), true);
        send(shooter, st);
        return 1.0 + KiControlConfig.get().cerealian.weakPointDamageBonus * 1.5 * mul;
    }

    /**
     * Mira assistida: o ponto fraco ativo do alvo marcado mais perto da linha de visada, se estiver
     * dentro do cone (maior com o Olho aberto e com o nivel da Mira). Devolve {idEntidade, ponto} ou null.
     */
    public static int[] lockOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (!isCerealian(sp) || st == null || st.markedId < 0 || st.mask == 0 || sp.level().getGameTime() > st.markUntil) return null;
        if (!(sp.level().getEntity(st.markedId) instanceof LivingEntity le) || !le.isAlive()) return null;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f).normalize();
        double cone = Math.toRadians((st.active ? c.aimAssistEyeDegrees : c.aimAssistDegrees) + 1.5 * (eyeLevel(sp) - 1));
        double range = range(sp);
        int best = -1;
        double bestAngle = cone;
        for (int i = 0; i < WeakPoints.MAX; i++) {
            if ((st.mask & (1 << i)) == 0) continue;
            Vec3 d = WeakPoints.position(le, i, 1.0f).subtract(eye);
            double dist = d.length();
            if (dist < 1e-3 || dist > range) continue;
            double ang = Math.acos(Math.max(-1.0, Math.min(1.0, d.scale(1.0 / dist).dot(look))));
            if (ang < bestAngle) { bestAngle = ang; best = i; }
        }
        return best < 0 ? null : new int[]{le.getId(), best};
    }

    private static void weakPointFx(ServerPlayer p, Vec3 at) {
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.15, 0.15, 0.15, 0.35);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
        p.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_weak_point").withStyle(ChatFormatting.GOLD), true);
    }

    /** Bonus de atirador: tiro (projetil) de longe. Devolve o multiplicador (1 = nada). */
    public static double sniperMultiplier(ServerPlayer shooter, Entity victim) {
        if (!isCerealian(shooter)) return 1.0;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        double d = shooter.distanceTo(victim);
        if (d < c.sniperMinDistance) return 1.0;
        double t = Math.min(1.0, (d - c.sniperMinDistance) / Math.max(1.0, c.sniperMaxDistance - c.sniperMinDistance));
        StatsData data = AuraSealManager.stats(shooter);
        double mul = data == null ? 1.0 : formMultiplier(data);
        return 1.0 + c.sniperMaxBonus * t * mul;
    }

    private static void clearMark(ServerPlayer sp, State st) {
        st.markedId = -1;
        st.mask = 0;
        send(sp, st);
    }

    private static void send(ServerPlayer sp, State st) {
        int ticks = (int) Math.max(0, st.markUntil - sp.level().getGameTime());
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new EyeMarkS2C(st.markedId, ticks, st.mask));
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
