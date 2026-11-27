package com.dmzkicontrol.training;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Drone de treino da Capsule Corp (o treino do Vegeta na sala de gravidade): voa em volta, mira (o olho vermelho
 * pulsa) e dispara um laser. Quem controla tudo e o {@link DroneTraining}; aqui so o corpo e a animacao.
 */
public class TrainingDroneEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(TrainingDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.training_drone.idle");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("animation.training_drone.charge");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TrainingDroneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CHARGING, false);
    }

    public boolean isCharging() {
        return this.entityData.get(CHARGING);
    }

    public void setCharging(boolean c) {
        this.entityData.set(CHARGING, c);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        if (!DroneTraining.onDroneHit(this, source)) return false;
        return super.hurt(source, Math.max(amount, getMaxHealth() * 2));
    }

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        if (!level().isClientSide() && this.tickCount > 20 && !DroneTraining.owns(this)) this.discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(isCharging() ? CHARGE : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
