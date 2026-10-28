package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.registry.ModRegistry;
import com.dragonminez.common.init.MainDamageTypes;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiro de ki dos Cerealianos: um disparo fino e muito rapido, preciso (sem espalhar), no lugar da
 * bola de ki. Tambem e usado pela Flecha de Ki (mais longo, atravessa alvos e acerta mais forte).
 * Acertar perto de um ponto fraco marcado pelo Olho Cereal e um "tiro preciso".
 */
public class CerealShotEntity extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARROW = SynchedEntityData.defineId(CerealShotEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage;
    private int pierceLeft;
    private int life;
    private final Set<Integer> alreadyHit = new HashSet<>();

    public CerealShotEntity(EntityType<? extends CerealShotEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Cria o tiro saindo de {@code from} na direcao em que o dono esta olhando. */
    public static CerealShotEntity create(LivingEntity owner, Vec3 from, float damage, int color, boolean arrow, int pierce) {
        CerealShotEntity shot = new CerealShotEntity(ModRegistry.CEREAL_SHOT.get(), owner.level());
        shot.setOwner(owner);
        shot.damage = damage;
        shot.pierceLeft = Math.max(0, pierce);
        shot.entityData.set(COLOR, color & 0xFFFFFF);
        shot.entityData.set(ARROW, arrow);
        Vec3 look = owner.getViewVector(1.0f);
        double speed = arrow ? 4.0 : 5.0;
        shot.setPos(from.x, from.y, from.z);
        shot.setDeltaMovement(look.scale(speed));
        shot.setYRot(owner.getYRot());
        shot.setXRot(owner.getXRot());
        return shot;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COLOR, 0x5FD3C4);
        this.entityData.define(ARROW, false);
    }

    public int getColor() {
        return this.entityData.get(COLOR);
    }

    public boolean isArrow() {
        return this.entityData.get(ARROW);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 next = pos.add(vel);

        if (!this.level().isClientSide) {
            BlockHitResult block = this.level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : next;

            // varios alvos no mesmo trecho (a flecha atravessa)
            while (!this.isRemoved()) {
                AABB box = this.getBoundingBox().expandTowards(end.subtract(pos)).inflate(0.35);
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(this.level(), this, pos, end, box, this::canHitEntity);
                if (hit == null) break;
                // ponto exato onde o tiro entrou na caixa do alvo (para checar os pontos fracos)
                Vec3 impact = hit.getEntity().getBoundingBox().inflate(0.1).clip(pos, end).orElse(null);
                hitEntity(hit.getEntity(), impact);
                if (pierceLeft-- <= 0) { this.discard(); return; }
            }
            if (block.getType() != HitResult.Type.MISS) {
                impactFx(block.getLocation(), false);
                this.discard();
                return;
            }
            if (++life > (isArrow() ? 30 : 24)) { this.discard(); return; }
        }
        this.setPos(next.x, next.y, next.z);
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
            dmg *= (float) CerealianEye.preciseHitMultiplier(sp, le, at);
        }
        le.invulnerableTime = 0;
        le.hurt(MainDamageTypes.kiblast(this.level(), this, owner), dmg);
        impactFx(impact != null ? impact : le.position(), true);
    }

    private void impactFx(Vec3 at, boolean entity) {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, entity ? 6 : 3, 0.05, 0.05, 0.05, 0.08);
            if (entity) sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.2);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
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
        this.pierceLeft = tag.getInt("Pierce");
        this.entityData.set(COLOR, tag.getInt("Color"));
        this.entityData.set(ARROW, tag.getBoolean("Arrow"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Pierce", pierceLeft);
        tag.putInt("Color", getColor());
        tag.putBoolean("Arrow", isArrow());
    }
}
