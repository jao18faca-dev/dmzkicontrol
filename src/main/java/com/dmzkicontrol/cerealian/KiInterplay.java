package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.control.AuraSealManager;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Como os tiros cerealianos conversam com o ki dos outros (DMZ, Sparking, Noea, Overhaul):
 * <ul>
 *   <li><b>Parry</b>: quem bloqueia no tempo certo (a janela de parry do DMZ) rebate a flecha de volta
 *       para quem atirou, e devolve o laser;</li>
 *   <li><b>Choque</b>: flecha contra rajada de ki, o mais forte passa; flecha contra uma onda de ki
 *       (Kamehameha, Galick Gun...) trava na ponta da onda por um instante e quem tiver mais poder vence;</li>
 *   <li><b>Laser</b>: derruba rajadas mais fracas no caminho; onda de ki ou rajada mais forte segura o laser.</li>
 * </ul>
 */
public final class KiInterplay {
    private KiInterplay() {}

    public static boolean enabled() {
        return com.dmzkicontrol.config.KiControlConfig.get().cerealian.kiClashAndParry;
    }

    /** Bloqueou agora, dentro da janela de parry do DMZ? (consome a janela, como o proprio DMZ/Overhaul fazem) */
    public static boolean perfectParry(Entity target) {
        if (!(target instanceof ServerPlayer sp) || !enabled()) return false;
        try {
            var combat = com.dragonminez.common.config.ConfigManager.getCombatConfig();
            if (combat != null && Boolean.FALSE.equals(combat.getEnableParrying())) return false;
            int window = combat == null || combat.getParryWindowMs() == null ? 250 : combat.getParryWindowMs();
            StatsData data = AuraSealManager.stats(sp);
            if (data == null) return false;
            var st = data.getStatus();
            if (!st.isBlocking()) return false;
            long since = System.currentTimeMillis() - st.getLastBlockTime();
            if (since < 0 || since > window) return false;
            st.setLastBlockTime(0L);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Projetil de ki de outra pessoa (nao do atirador nem de um aliado dele)? */
    public static boolean hostile(@Nullable Entity owner, AbstractKiProjectile p) {
        if (p.isRemoved()) return false;
        Entity other = p.getOwner();
        if (owner == null) return true;
        if (other == null) return true;
        return other != owner && !owner.isAlliedTo(other);
    }

    public static float power(AbstractKiProjectile p) {
        try {
            return Math.max(0f, p.getKiDamage());
        } catch (Throwable t) {
            return 0f;
        }
    }

    public static boolean isBeam(AbstractKiProjectile p) {
        try {
            return p.isClashableBeam();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ponta da onda de ki (onde ela esta chegando agora). */
    public static Vec3 beamHead(AbstractKiProjectile p) {
        try {
            Vec3 dir = Vec3.directionFromRotation(p.getClashPitch(), p.getClashYaw());
            return p.position().add(dir.scale(Math.max(0.0, p.getClashBeamLength())));
        } catch (Throwable t) {
            return p.position();
        }
    }

    /** Raio de contato de um projetil do DMZ. */
    public static double radius(AbstractKiProjectile p) {
        return Math.max(0.35, p.getSize() * 0.6);
    }

    /** Distancia do ponto ao segmento a-b. */
    public static double pointSegment(Vec3 pt, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-9) return pt.distanceTo(a);
        double t = Math.max(0, Math.min(1, pt.subtract(a).dot(ab) / len2));
        return pt.distanceTo(a.add(ab.scale(t)));
    }

    /** Menor distancia entre dois segmentos (amostrado: bom o bastante para tiros de alguns blocos). */
    public static double segmentSegment(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= 8; i++) {
            Vec3 p = a0.add(a1.subtract(a0).scale(i / 8.0));
            best = Math.min(best, pointSegment(p, b0, b1));
        }
        for (int i = 0; i <= 8; i++) {
            Vec3 p = b0.add(b1.subtract(b0).scale(i / 8.0));
            best = Math.min(best, pointSegment(p, a0, a1));
        }
        return best;
    }

    /** Projeteis de ki inimigos perto de um trecho. */
    public static List<AbstractKiProjectile> near(ServerLevel level, @Nullable Entity owner, Vec3 a, Vec3 b, double pad) {
        if (!enabled()) return List.of();
        AABB box = new AABB(a, b).inflate(pad);
        return level.getEntitiesOfClass(AbstractKiProjectile.class, box.inflate(64.0), p -> hostile(owner, p)
                && (box.intersects(p.getBoundingBox()) || (isBeam(p) && box.inflate(4.0).contains(beamHead(p)))
                || segmentTouchesBeam(p, a, b, pad)));
    }

    private static boolean segmentTouchesBeam(AbstractKiProjectile p, Vec3 a, Vec3 b, double pad) {
        if (!isBeam(p)) return false;
        return segmentSegment(a, b, p.position(), beamHead(p)) <= radius(p) + pad;
    }

    /** Faisca do choque de ki. */
    public static void clashFx(ServerLevel sl, Vec3 at, int colorA, int colorB, float size, Entity source) {
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, (int) (10 + 8 * size), 0.15 * size, 0.15 * size, 0.15 * size, 0.25);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (int) (8 + 6 * size), 0.2 * size, 0.2 * size, 0.2 * size, 0.4);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 0.7f + 0.3f / Math.max(0.5f, size));
        try {
            com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                    new com.dragonminez.common.network.S2C.ShockwaveVfxS2C(at.x, at.y, at.z, 1.2f + size, colorA, 10), source);
        } catch (Throwable ignored) {
        }
    }

    /** Barulho e brilho do parry. */
    public static void parryFx(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.5);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.5f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.8f);
    }
}
