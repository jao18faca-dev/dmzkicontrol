package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.registry.ModRegistry;
import com.dragonminez.common.init.MainDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Laser de ki dos Cerealianos (no lugar da bola de ki), no estilo do Raio da Morte do Freeza: um raio
 * fino e instantaneo que sai da mao e acerta na hora o que estiver na mira. Se o alvo estiver marcado
 * pelo Olho Cereal e a mira estiver perto de um ponto fraco, o raio vai direto nesse ponto.
 *
 * <p>A entidade e so o desenho do raio (fica alguns ticks na tela); o acerto acontece ao disparar.
 */
public class CerealBeamEntity extends Entity {
    public static final int LIFE = 7;
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(CerealBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CerealBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIT = SynchedEntityData.defineId(CerealBeamEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;

    public CerealBeamEntity(EntityType<? extends CerealBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Dispara o laser: calcula o acerto, aplica o dano e devolve o raio para ser adicionado ao mundo. */
    public static CerealBeamEntity fire(ServerPlayer sp, Vec3 hand, float damage, int color) {
        Level level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1.0f).normalize();
        int lockTarget = -1, lockIdx = -1;
        int[] lock = CerealianEye.lockOn(sp);
        if (lock != null && level.getEntity(lock[0]) instanceof LivingEntity t) {
            lockTarget = lock[0];
            lockIdx = lock[1];
            dir = WeakPoints.position(t, lockIdx, 1.0f).subtract(eye).normalize();
        }
        double range = CerealianEye.range(sp) + 16.0;
        Vec3 far = eye.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : far;
        AABB box = sp.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && e != sp && !e.isSpectator(), eye.distanceToSqr(end));

        CerealBeamEntity beam = new CerealBeamEntity(ModRegistry.CEREAL_BEAM.get(), level);
        beam.ownerId = sp.getUUID();
        beam.setPos(hand.x, hand.y, hand.z);
        beam.entityData.set(COLOR, color & 0xFFFFFF);

        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            Vec3 impact = le.getBoundingBox().inflate(0.1).clip(eye, end).orElse(le.position().add(0, le.getBbHeight() * 0.5, 0));
            int forced = le.getId() == lockTarget ? lockIdx : -1;
            float dmg = damage * (float) CerealianEye.preciseHit(sp, le, impact, 0.9, forced);
            end = impact;
            beam.entityData.set(HIT, true);
            le.invulnerableTime = 0;
            le.hurt(MainDamageTypes.kiblast(level, beam, sp), dmg);
            impactFx(level, impact, true);
        } else if (block.getType() != HitResult.Type.MISS) {
            beam.entityData.set(HIT, true);
            impactFx(level, end, false);
            float r = KiControlConfig.get().cerealian.kiShotExplosionRadius;
            if (r > 0f && level instanceof ServerLevel sl) {
                try {
                    com.dragonminez.common.combat.util.KiTerrainDestruction.carve(sl, BlockPos.containing(end), 0.0f, r, Block.UPDATE_ALL, sp);
                } catch (Throwable t) {
                    KiControlMod.LOGGER.debug("Destruicao do DMZ indisponivel: {}", t.toString());
                }
            }
        }
        beam.entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        return beam;
    }

    private static void impactFx(Level level, Vec3 at, boolean entity) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, entity ? 10 : 6, 0.06, 0.06, 0.06, 0.14);
            sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (entity) sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.7f);
        }
    }

    public Vec3 getEnd() {
        Vector3f v = this.entityData.get(END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public int getColor() {
        return this.entityData.get(COLOR);
    }

    public boolean didHit() {
        return this.entityData.get(HIT);
    }

    public Entity getOwnerEntity() {
        if (ownerId == null || !(this.level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(ownerId);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > LIFE) this.discard();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(END, new Vector3f());
        this.entityData.define(COLOR, 0x5FD3C4);
        this.entityData.define(HIT, false);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.position(), getEnd()).inflate(1.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192 * 192;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
