package com.dmzkicontrol.combat;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.encounter.EncounterDirector;
import com.dmzkicontrol.network.CombatFxS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combate no ritmo do Sparking Zero / Xenoverse 2, por cima do combate do DragonMineZ:
 * <ul>
 * <li><b>Zanzoken</b>: dar dash logo antes do golpe chegar faz sumir e aparecer nas costas de quem atacou; o
 * proximo golpe nele tira mais.</li>
 * <li><b>Golpe pesado</b>: uma sequencia de golpes sem parar termina num soco que arremessa o inimigo longe.</li>
 * <li><b>Perseguicao</b>: dar dash enquanto ele voa = aparecer na frente dele e continuar o combo (de novo e de
 * novo, como o "Vanishing Assault").</li>
 * <li><b>Chefes</b>: os chefes da historia tambem somem as vezes e contra-atacam pelas costas.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatFlow {
    private static final class State {
        long lastDash = -100, lastVanish = -1000;
        int hits;
        long lastHit = -100;
        int lastVictim = -1;
        /** Perseguicao liberada ate este tick, contra este alvo. */
        long chaseUntil = -1;
        int chaseTarget = -1;
        int chains;
        /** Proximo golpe com bonus contra este alvo, ate este tick. */
        long bonusUntil = -1;
        int bonusTarget = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<Integer, Long> BOSS_VANISH = new HashMap<>();

    private CombatFlow() {}

    private static KiControlConfig.Combat cfg() {
        return KiControlConfig.get().combat;
    }

    private static State st(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    // ------------------------------------------------------------ dash: esquiva e perseguicao

    @SubscribeEvent
    public static void onDash(DMZEvent.PlayerDashEvent event) {
        ServerPlayer sp = event.getPlayer();
        if (sp == null || EncounterDirector.isLocked(sp)) return;
        State s = st(sp);
        long now = sp.level().getGameTime();
        s.lastDash = now;
        if (!cfg().vanishingChase || now > s.chaseUntil) return;
        Entity e = sp.level().getEntity(s.chaseTarget);
        if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(sp) > 40) return;
        // aparece na frente do inimigo que esta voando, de frente para ele
        Vec3 v = target.getDeltaMovement();
        Vec3 dir = v.lengthSqr() > 1e-4 ? v.normalize() : sp.getLookAngle();
        Vec3 ahead = target.position().add(v.scale(3)).add(dir.scale(1.6));
        Vec3 from = sp.position();
        sp.teleportTo(ahead.x, Math.max(ahead.y, target.getY() - 0.2), ahead.z);
        face(sp, target);
        target.setDeltaMovement(target.getDeltaMovement().scale(0.15));
        target.hurtMarked = true;
        s.chaseUntil = -1;
        s.chains++;
        s.hits = Math.max(0, cfg().hitsForSmash - 3);
        s.lastHit = now;
        s.lastVictim = target.getId();
        s.bonusTarget = target.getId();
        s.bonusUntil = now + 30;
        afterimage((ServerLevel) sp.level(), from);
        play(sp, "dragonminez:zanzoken", 1f, 1.1f + 0.1f * Math.min(3, s.chains));
        fx(sp, CombatFxS2C.CHASE);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource src = event.getSource();
        Entity attacker = src.getEntity();
        if (!(attacker instanceof LivingEntity la) || attacker == victim) return;

        // jogador com dash na hora certa: zanzoken
        if (victim instanceof ServerPlayer sp && cfg().vanishDodge && !EncounterDirector.isLocked(sp)) {
            State s = st(sp);
            long now = sp.level().getGameTime();
            if (now - s.lastDash <= cfg().vanishWindowTicks && now - s.lastVanish >= cfg().vanishCooldownSeconds * 20) {
                event.setCanceled(true);
                s.lastVanish = now;
                Vec3 from = sp.position();
                Vec3 back = la.position().subtract(la.getLookAngle().multiply(1, 0, 1).normalize().scale(1.7));
                sp.teleportTo(back.x, la.getY(), back.z);
                face(sp, la);
                s.bonusTarget = la.getId();
                s.bonusUntil = now + 30;
                afterimage((ServerLevel) sp.level(), from);
                play(sp, "dragonminez:zanzoken", 1f, 1.0f);
                fx(sp, CombatFxS2C.VANISH);
                return;
            }
        }

        // chefe da historia somindo de um golpe corpo a corpo e contra-atacando
        if (cfg().bossVanish && victim instanceof DBSagasEntity && attacker instanceof ServerPlayer sp && src.getDirectEntity() == sp
                && !EncounterDirector.isLocked(sp)) {
            long now = victim.level().getGameTime();
            Long last = BOSS_VANISH.get(victim.getId());
            if ((last == null || now - last > 60) && victim.getRandom().nextDouble() < cfg().bossVanishChance) {
                event.setCanceled(true);
                BOSS_VANISH.put(victim.getId(), now);
                Vec3 from = victim.position();
                Vec3 back = sp.position().subtract(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
                victim.teleportTo(back.x, sp.getY(), back.z);
                face(victim, sp);
                afterimage((ServerLevel) victim.level(), from);
                play(victim, "dragonminez:zanzoken", 1f, 0.85f);
                if (victim instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setTarget(sp);
                    mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    mob.doHurtTarget(sp);
                }
                fx(sp, CombatFxS2C.ENEMY_VANISH);
            }
        }
    }

    // ------------------------------------------------------------ combo e golpe pesado

    @SubscribeEvent
    public static void onDealt(DMZEvent.DamageDealtEvent event) {
        if (!(event.getAttacker() instanceof ServerPlayer sp) || event.getVictim() == null) return;
        if (event.getSourceType() != DMZEvent.DamageSourceType.MELEE || event.isBlocked() || event.isParried()) return;
        if (EncounterDirector.isLocked(sp)) return;
        LivingEntity victim = event.getVictim();
        State s = st(sp);
        long now = sp.level().getGameTime();
        if (victim.getId() != s.lastVictim || now - s.lastHit > 30) {
            s.hits = 0;
            s.chains = 0;
        }
        s.lastVictim = victim.getId();
        s.lastHit = now;
        s.hits++;
        if (s.hits < Math.max(2, cfg().hitsForSmash) || !victim.isAlive()) return;
        // golpe pesado: arremessa na direcao do olhar
        s.hits = 0;
        Vec3 look = sp.getLookAngle();
        double power = 1.6 + 0.25 * Math.min(3, s.chains);
        victim.setDeltaMovement(look.x * power, Math.max(0.35, look.y * power + 0.3), look.z * power);
        victim.hurtMarked = true;
        ServerLevel level = (ServerLevel) sp.level();
        Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.6);
        play(sp, "dragonminez:critic_punch2", 1.1f, 0.85f);
        s.chaseTarget = victim.getId();
        s.chaseUntil = now + 20;
        fx(sp, CombatFxS2C.SMASH);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        State s = STATES.get(sp.getUUID());
        if (s == null || s.bonusTarget != event.getEntity().getId() || sp.level().getGameTime() > s.bonusUntil) return;
        s.bonusUntil = -1;
        event.setAmount((float) (event.getAmount() * (1 + cfg().counterDamageBonus)));
        Vec3 c = event.getEntity().position().add(0, event.getEntity().getBbHeight() * 0.6, 0);
        ((ServerLevel) sp.level()).sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------ utilidades

    private static void face(Entity e, Entity target) {
        Vec3 d = target.position().subtract(e.position());
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw);
        if (e instanceof ServerPlayer sp) {
            float pitch = (float) (-Mth.atan2(d.y + target.getBbHeight() * 0.5 - sp.getEyeHeight(), Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), yaw, pitch);
        }
    }

    private static void afterimage(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 10, 0.25, 0.5, 0.25, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 6, 0.2, 0.5, 0.2, 0.02);
    }

    private static void play(Entity at, String id, float vol, float pitch) {
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if (ev != null) at.level().playSound(null, at.blockPosition(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    private static void fx(ServerPlayer sp, int kind) {
        if (!cfg().impactFeel) return;
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new CombatFxS2C(kind));
    }
}
