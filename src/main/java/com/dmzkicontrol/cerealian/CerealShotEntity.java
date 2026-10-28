package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.registry.ModRegistry;
import com.dragonminez.common.init.MainDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiro de ki dos Cerealianos (no lugar da bola de ki) e a flecha do Arco de Ki.
 *
 * <ul>
 *   <li>Tiro: bala de ki rapida e precisa. Se o alvo esta marcado pelo Olho Cereal e a mira esta
 *   perto de um ponto fraco, o tiro trava nesse ponto e faz uma leve curva ate ele.</li>
 *   <li>Flecha: maior, na cor do ki do usuario, atravessa alvos e explode no fim (cratera e dano em
 *   area, respeitando as regras de destruicao do DMZ). Tudo cresce com a carga.</li>
 * </ul>
 */
public class CerealShotEntity extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARROW = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage;
    private float charge;
    private int pierceLeft;
    private int life;
    private double speed;
    private int lockTarget = -1;
    private int lockIdx = -1;
    private boolean exploded;
    private final Set<Integer> alreadyHit = new HashSet<>();

    public CerealShotEntity(EntityType<? extends CerealShotEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Tiro comum. */
    public static CerealShotEntity create(LivingEntity owner, Vec3 from, float damage, int color, boolean arrow, int pierce) {
        return create(owner, from, damage, color, arrow, pierce, 1.0f);
    }

    /** Cria o tiro saindo de {@code from} na direcao da mira (ou do ponto fraco travado). */
    public static CerealShotEntity create(LivingEntity owner, Vec3 from, float damage, int color, boolean arrow, int pierce, float charge) {
        CerealShotEntity shot = new CerealShotEntity(ModRegistry.CEREAL_SHOT.get(), owner.level());
        shot.setOwner(owner);
        shot.damage = damage;
        shot.charge = Math.max(0.1f, charge);
        shot.pierceLeft = Math.max(0, pierce);
        shot.entityData.set(COLOR, color & 0xFFFFFF);
        shot.entityData.set(ARROW, arrow);
        shot.entityData.set(SCALE, arrow ? 1.0f + 0.55f * Math.min(2.0f, shot.charge) : 1.0f);
        shot.speed = arrow ? 3.0 : 4.2;

        Vec3 dir = owner.getViewVector(1.0f).normalize();
        if (owner instanceof ServerPlayer sp) {
            int[] lock = CerealianEye.lockOn(sp);
            if (lock != null && sp.level().getEntity(lock[0]) instanceof LivingEntity target) {
                shot.lockTarget = lock[0];
                shot.lockIdx = lock[1];
                shot.entityData.set(LOCKED, true);
                dir = WeakPoints.position(target, lock[1], 1.0f).subtract(from).normalize();
            }
        }
        shot.setPos(from.x, from.y, from.z);
        shot.setDeltaMovement(dir.scale(shot.speed));
        shot.faceVelocity();
        return shot;
    }

    /** Faz o tiro mirar e perseguir um alvo (ponto fraco {@code idx}), usado pela chuva de flechas. */
    public void homeOn(LivingEntity target, int idx, Vec3 initialDir) {
        this.lockTarget = target.getId();
        this.lockIdx = Math.max(0, Math.min(WeakPoints.MAX - 1, idx));
        this.entityData.set(LOCKED, true);
        aim(initialDir);
    }

    /** Muda a direcao inicial mantendo a velocidade. */
    public void aim(Vec3 dir) {
        Vec3 d = dir.normalize();
        this.setDeltaMovement(d.scale(this.speed > 0 ? this.speed : 3.0));
        faceVelocity();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COLOR, 0x5FD3C4);
        this.entityData.define(ARROW, false);
        this.entityData.define(SCALE, 1.0f);
        this.entityData.define(LOCKED, false);
    }

    public int getColor() {
        return this.entityData.get(COLOR);
    }

    public boolean isArrow() {
        return this.entityData.get(ARROW);
    }

    public float getScale() {
        return this.entityData.get(SCALE);
    }

    public boolean isLocked() {
        return this.entityData.get(LOCKED);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = this.position();
        Vec3 vel = this.getDeltaMovement();

        if (!this.level().isClientSide) {
            vel = steer(pos, vel);
            this.setDeltaMovement(vel);
        }
        Vec3 next = pos.add(vel);

        if (!this.level().isClientSide) {
            BlockHitResult block = this.level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : next;

            // varios alvos no mesmo trecho (a flecha atravessa)
            while (!this.isRemoved()) {
                AABB box = this.getBoundingBox().expandTowards(end.subtract(pos)).inflate(isArrow() ? 0.6 * getScale() : 0.35);
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(this.level(), this, pos, end, box, this::canHitEntity);
                if (hit == null) break;
                Vec3 impact = hit.getEntity().getBoundingBox().inflate(0.1).clip(pos, end).orElse(null);
                hitEntity(hit.getEntity(), impact);
                if (pierceLeft-- <= 0) {
                    Vec3 at = impact != null ? impact : hit.getEntity().position();
                    finish(at);
                    return;
                }
            }
            if (block.getType() != HitResult.Type.MISS) {
                finish(block.getLocation());
                return;
            }
            if (++life > (isArrow() ? 40 : 28)) {
                if (isArrow()) finish(this.position());
                else this.discard();
                return;
            }
        } else {
            trailFx(pos);
        }
        this.setPos(next.x, next.y, next.z);
        this.faceVelocity();
    }

    /** Curva leve ate o ponto fraco travado (so enquanto o ponto ainda esta na frente). */
    private Vec3 steer(Vec3 pos, Vec3 vel) {
        if (lockTarget < 0) return vel;
        if (!(this.level().getEntity(lockTarget) instanceof LivingEntity target) || !target.isAlive() || alreadyHit.contains(lockTarget)) {
            lockTarget = -1;
            return vel;
        }
        Vec3 to = WeakPoints.position(target, lockIdx, 1.0f).subtract(pos);
        double dist = to.length();
        if (dist < 0.5) return vel;
        Vec3 want = to.scale(1.0 / dist);
        Vec3 cur = vel.normalize();
        if (cur.dot(want) < 0.2) { lockTarget = -1; return vel; } // passou do alvo: segue reto
        double turn = isArrow() ? 0.25 : 0.45;
        return cur.add(want.subtract(cur).scale(turn)).normalize().scale(speed > 0 ? speed : vel.length());
    }

    private void faceVelocity() {
        Vec3 v = this.getDeltaMovement();
        double h = v.horizontalDistance();
        if (v.lengthSqr() < 1e-8) return;
        this.setYRot((float) (Math.atan2(v.x, v.z) * (180.0 / Math.PI)));
        this.setXRot((float) (Math.atan2(v.y, h) * (180.0 / Math.PI)));
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity le && le.isAlive() && !alreadyHit.contains(e.getId());
    }

    private void hitEntity(Entity target, Vec3 impact) {
        alreadyHit.add(target.getId());
        if (!(target instanceof LivingEntity le)) return;
        Entity owner = this.getOwner();
        float dmg = damage;
        if (owner instanceof ServerPlayer sp) {
            Vec3 at = impact != null ? impact : le.position().add(0, le.getBbHeight() * 0.5, 0);
            int forced = target.getId() == lockTarget ? lockIdx : -1;
            dmg *= (float) CerealianEye.preciseHit(sp, le, at, 0.9, forced);
        }
        le.invulnerableTime = 0;
        le.hurt(MainDamageTypes.kiblast(this.level(), this, owner), dmg);
        impactFx(impact != null ? impact : le.position().add(0, le.getBbHeight() * 0.5, 0), true);
    }

    /** Fim do tiro: a flecha explode; o tiro comum so faz o impacto (e uma cratera, se configurado). */
    private void finish(Vec3 at) {
        if (exploded) return;
        exploded = true;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        if (isArrow()) {
            float radius = c.kiBowExplosionRadius + c.kiBowExplosionRadiusPerCharge * Math.min(2.0f, charge);
            explode(at, radius, true);
        } else {
            impactFx(at, false);
            if (c.kiShotExplosionRadius > 0f) explode(at, c.kiShotExplosionRadius, false);
        }
        this.discard();
    }

    private void explode(Vec3 at, float radius, boolean big) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Entity owner = this.getOwner();
        int color = getColor();
        // cratera pelo sistema de destruicao do DMZ (respeita gamerules, estruturas protegidas e WorldGuard)
        if (radius > 0f && (KiControlConfig.get().cerealian.kiBowExplosionRadius > 0f || !big)) {
            try {
                com.dragonminez.common.combat.util.KiTerrainDestruction.carve(sl, BlockPos.containing(at), 0.0f, radius,
                        Block.UPDATE_ALL, owner != null ? owner : this);
            } catch (Throwable t) {
                KiControlMod.LOGGER.debug("Destruicao do DMZ indisponivel: {}", t.toString());
            }
        }
        if (big) {
            // dano em area para quem nao levou a flechada direta
            float area = radius * 1.3f;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(area),
                    e -> e.isAlive() && e != owner && !alreadyHit.contains(e.getId()))) {
                double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(at);
                if (d > area) continue;
                e.invulnerableTime = 0;
                e.hurt(MainDamageTypes.kiblast(sl, this, owner), (float) (damage * 0.5 * (1.0 - 0.6 * d / area)));
            }
            try {
                var visual = new com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity(
                        com.dragonminez.common.init.MainEntities.KI_EXPLOSION_VISUAL.get(), sl);
                visual.setupExplosion(color, brighten(color), 0xFFFFFF, Math.max(1.2f, radius * 0.75f));
                visual.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
                sl.addFreshEntity(visual);
                com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                        new com.dragonminez.common.network.S2C.ShockwaveVfxS2C(at.x, at.y, at.z, radius * 1.6f, color, 14), this);
                if (charge >= 1.0f && owner instanceof ServerPlayer sp) {
                    com.dragonminez.common.network.NetworkHandler.sendToPlayer(
                            new com.dragonminez.common.network.S2C.TriggerImpactFrameS2C(0.85f, 0.05f, 3, true), sp);
                }
                sl.playSound(null, at.x, at.y, at.z, com.dragonminez.common.init.MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS,
                        1.6f + 0.4f * charge, 0.9f);
            } catch (Throwable t) {
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 1.1f);
            }
            sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, radius * 0.3, radius * 0.3, radius * 0.3, 0.25);
        }
    }

    private static int brighten(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = r + (255 - r) / 2; g = g + (255 - g) / 2; b = b + (255 - b) / 2;
        return (r << 16) | (g << 8) | b;
    }

    private void impactFx(Vec3 at, boolean entity) {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, entity ? 10 : 5, 0.08, 0.08, 0.08, 0.12);
            sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (entity) sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    /** Faiscas de ki deixadas pelo caminho (so no cliente). */
    private void trailFx(Vec3 pos) {
        if (!isArrow() && this.tickCount % 2 != 0) return;
        Vec3 v = this.getDeltaMovement();
        for (int i = 0; i < (isArrow() ? 3 : 1); i++) {
            double t = this.random.nextDouble();
            this.level().addParticle(ParticleTypes.END_ROD,
                    pos.x - v.x * t + (this.random.nextDouble() - 0.5) * 0.2 * getScale(),
                    pos.y - v.y * t + (this.random.nextDouble() - 0.5) * 0.2 * getScale(),
                    pos.z - v.z * t + (this.random.nextDouble() - 0.5) * 0.2 * getScale(), 0, 0, 0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.charge = tag.getFloat("Charge");
        this.pierceLeft = tag.getInt("Pierce");
        this.speed = tag.contains("Speed") ? tag.getDouble("Speed") : 4.0;
        this.entityData.set(COLOR, tag.getInt("Color"));
        this.entityData.set(ARROW, tag.getBoolean("Arrow"));
        this.entityData.set(SCALE, tag.contains("Scale") ? tag.getFloat("Scale") : 1.0f);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putFloat("Charge", charge);
        tag.putInt("Pierce", pierceLeft);
        tag.putDouble("Speed", speed);
        tag.putInt("Color", getColor());
        tag.putBoolean("Arrow", isArrow());
        tag.putFloat("Scale", getScale());
    }
}
