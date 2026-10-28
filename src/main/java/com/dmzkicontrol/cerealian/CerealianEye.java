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
 * <ul>
 *   <li>Segurando a tecla: o olho fica ativo (gasta um pouco de ki por segundo) e marca o ponto
 *       fraco de quem estiver na mira.</li>
 *   <li>O primeiro golpe corpo a corpo/strike no alvo marcado causa dano extra e ignora parte da defesa.</li>
 *   <li>Tiros de longe (rajadas de ki, flechas...) ganham bonus de atirador conforme a distancia.</li>
 *   <li>As formas Olho Desperto e Olhos Gemeos multiplicam esses bonus.</li>
 * </ul>
 */
public final class CerealianEye {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        boolean active;
        int markedId = -1;
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

    public static void setActive(ServerPlayer sp, boolean active) {
        State st = STATES.computeIfAbsent(sp.getUUID(), id -> new State());
        if (active && !isCerealian(sp)) {
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_not_cerealian"), true);
            return;
        }
        if (active == st.active) return;
        st.active = active;
        st.ticker = 0;
        if (active) sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        else sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    public static void tick(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        long now = sp.level().getGameTime();
        if (st.markedId >= 0 && now > st.markUntil) clearMark(sp, st);
        if (!st.active) return;
        if (!isCerealian(sp) || !sp.isAlive()) { st.active = false; return; }

        StatsData data = AuraSealManager.stats(sp);
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        if (++st.ticker % 20 == 0 && data != null && !sp.isCreative()) {
            var res = data.getResources();
            float cost = (float) (data.getMaxEnergy() * c.eyeKiCostPercentPerSecond / 100.0);
            if (res.getCurrentEnergy() < cost) {
                st.active = false;
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_no_ki").withStyle(ChatFormatting.RED), true);
                return;
            }
            res.setCurrentEnergy(res.getCurrentEnergy() - cost);
        }
        if (st.ticker % 5 == 0) {
            LivingEntity target = lookTarget(sp, c.eyeRange);
            if (target != null && target.getId() != st.markedId) {
                st.markedId = target.getId();
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
                send(sp, st.markedId, (int) (c.markSeconds * 20));
            }
            if (target != null) st.markUntil = now + (long) (c.markSeconds * 20);
        }
    }

    private static LivingEntity lookTarget(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        var blockHit = sp.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        double max = blockHit.getLocation().distanceToSqr(eye);
        AABB box = sp.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != sp && !e.isSpectator(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Golpe corpo a corpo/strike: consome a marca e devolve o multiplicador de dano (1 = nada). */
    public static double[] consumeWeakPoint(ServerPlayer attacker, Entity victim) {
        State st = STATES.get(attacker.getUUID());
        if (st == null || st.markedId != victim.getId() || attacker.level().getGameTime() > st.markUntil) return null;
        StatsData data = AuraSealManager.stats(attacker);
        if (data == null || !isCerealian(attacker)) return null;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        double mul = formMultiplier(data);
        clearMark(attacker, st);
        if (attacker.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 18, 0.3, 0.4, 0.3, 0.3);
            sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
        attacker.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_weak_point").withStyle(ChatFormatting.GOLD), true);
        return new double[]{1.0 + c.weakPointDamageBonus * mul, c.weakPointDefensePenetration * mul};
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
        send(sp, -1, 0);
    }

    private static void send(ServerPlayer sp, int id, int ticks) {
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new EyeMarkS2C(id, ticks));
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
