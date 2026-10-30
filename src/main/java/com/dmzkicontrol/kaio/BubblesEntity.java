package com.dmzkicontrol.kaio;

import com.dmzkicontrol.config.KiControlConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bubbles, o macaco do Senhor Kaioh. Fica perto do Kaioh; clique com o botao direito para comecar o
 * treino: pegar o Bubbles sem voar, so correndo e pulando, como o Goku. Ele foge, muda de direcao,
 * pula para o lado quando voce chega perto e para para dancar quando voce fica para tras.
 * Nao leva dano: encostar ou bater nele conta como pegar.
 */
public class BubblesEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DANCING = SynchedEntityData.defineId(BubblesEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRAINING = SynchedEntityData.defineId(BubblesEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bubbles.idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.bubbles.run");
    private static final RawAnimation DANCE = RawAnimation.begin().thenLoop("animation.bubbles.dance");
    private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold("animation.bubbles.leap");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos home;
    @Nullable private UUID chaser;
    private int level = 1;
    private int leapCooldown;
    private int idleDance;
    private float seed;

    public BubblesEntity(EntityType<? extends BubblesEntity> type, Level world) {
        super(type, world);
        this.setMaxUpStep(1.0f);
        this.setPersistenceRequired();
        this.seed = this.random.nextFloat() * 100f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DANCING, false);
        this.entityData.define(TRAINING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !isTraining() && !isDancing() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isTraining() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f) {
            @Override
            public boolean canUse() {
                return !isTraining() && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ estado

    public boolean isDancing() {
        return this.entityData.get(DANCING);
    }

    public boolean isTraining() {
        return this.entityData.get(TRAINING);
    }

    public void setHome(BlockPos pos) {
        this.home = pos.immutable();
        this.restrictTo(this.home, homeRadius());
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    private int homeRadius() {
        return Math.max(6, KiControlConfig.get().bubbles.homeRadius);
    }

    /** Comeca a fugir deste jogador no nivel dado. */
    public void startChase(ServerPlayer player, int level) {
        this.chaser = player.getUUID();
        this.level = Mth.clamp(level, 1, 5);
        this.leapCooldown = 20;
        this.entityData.set(TRAINING, true);
        this.entityData.set(DANCING, false);
        this.getNavigation().stop();
        if (this.home == null) setHome(this.blockPosition());
    }

    public void stopChase(boolean caught) {
        this.chaser = null;
        this.entityData.set(TRAINING, false);
        this.getNavigation().stop();
        this.idleDance = caught ? 0 : 60; // escapou: comemora dancando
        this.entityData.set(DANCING, !caught);
    }

    @Nullable
    public UUID getChaser() {
        return chaser;
    }

    // ------------------------------------------------------------------ logica

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (home == null) setHome(this.blockPosition());
        if (leapCooldown > 0) leapCooldown--;
        if (!isTraining()) {
            if (idleDance > 0 && --idleDance == 0) this.entityData.set(DANCING, false);
            else if (idleDance == 0 && this.random.nextInt(900) == 0 && this.getNavigation().isDone()) {
                idleDance = 60;
                this.entityData.set(DANCING, true);
            }
            return;
        }
        Player p = chaser == null ? null : this.level().getPlayerByUUID(chaser);
        if (p == null) return;
        flee(p);
    }

    private void flee(Player p) {
        KiControlConfig.Bubbles cfg = KiControlConfig.get().bubbles;
        int idx = level - 1;
        Vec3 away = this.position().subtract(p.position()).multiply(1, 0, 1);
        double dist = away.length();

        // ficou muito para tras: para e provoca com a dancinha
        if (dist > cfg.tauntDistance) {
            this.entityData.set(DANCING, true);
            this.getNavigation().stop();
            this.getLookControl().setLookAt(p, 30f, 30f);
            return;
        }
        if (isDancing() && dist > cfg.tauntDistance * 0.65) return;
        this.entityData.set(DANCING, false);

        Vec3 dir = dist < 1e-3 ? new Vec3(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5).normalize() : away.normalize();
        double t = this.tickCount * 0.17 + seed;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        dir = dir.add(side.scale(Math.sin(t) * (0.45 + 0.08 * level))).normalize();

        // nao foge do planeta: perto da borda corre em volta
        if (home != null) {
            Vec3 h = Vec3.atBottomCenterOf(home);
            Vec3 fromHome = this.position().subtract(h).multiply(1, 0, 1);
            double hd = fromHome.length();
            double r = homeRadius();
            if (hd > r * 0.7 && hd > 1e-3) {
                Vec3 in = fromHome.scale(-1 / hd);
                Vec3 tang = new Vec3(-in.z, 0, in.x);
                if (tang.dot(dir) < 0) tang = tang.scale(-1);
                double w = Mth.clamp((hd - r * 0.7) / (r * 0.3), 0, 1);
                dir = dir.scale(1 - w).add(tang.scale(w)).add(in.scale(w * 0.6)).normalize();
            }
        }

        // velocidade relativa a do jogador (o DMZ deixa jogadores muito rapidos)
        double player = BubblesTraining.effectiveSpeed(p);
        double factor = pick(cfg.speedFactors, idx, 1.0);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Mth.clamp(player * factor, 0.15, 3.0));

        Vec3 target = this.position().add(dir.scale(3.0));
        this.getMoveControl().setWantedPosition(target.x, this.getY(), target.z, 1.0);

        // chegou perto: pulo para o lado ou por cima
        if (dist < 2.8 && this.onGround() && leapCooldown <= 0 && this.random.nextDouble() < pick(cfg.leapChance, idx, 0.5)) {
            double sign = this.random.nextBoolean() ? 1 : -1;
            Vec3 leap = side.scale(sign).add(dir.scale(0.6)).normalize().scale(0.55 + 0.06 * level);
            this.setDeltaMovement(leap.x, 0.55, leap.z);
            this.hasImpulse = true;
            leapCooldown = (int) pick(cfg.leapCooldownTicks, idx, 30);
            this.playSound(SoundEvents.PARROT_AMBIENT, 0.8f, 1.6f + this.random.nextFloat() * 0.3f);
            BubblesTraining.onLeap(this, p);
        }
    }

    private static double pick(java.util.List<? extends Number> list, int idx, double fallback) {
        if (list == null || list.isEmpty()) return fallback;
        return list.get(Mth.clamp(idx, 0, list.size() - 1)).doubleValue();
    }

    // ------------------------------------------------------------------ interacao e dano

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) BubblesTraining.tryStart(sp, this);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer sp) BubblesTraining.onTouched(sp, this);
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 1.5f + this.random.nextFloat() * 0.3f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) setHome(new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ")));
    }

    // ------------------------------------------------------------------ animacao

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (isDancing()) return state.setAndContinue(DANCE);
            if (isTraining() && !this.onGround()) return state.setAndContinue(LEAP);
            if (state.isMoving()) return state.setAndContinue(RUN);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
