package com.dmzkicontrol.training;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.registry.ModRegistry;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Treino dos drones na sala de gravidade (o treino do Vegeta na Capsule Corp): agache e clique com o botao
 * direito no Aparelho de Gravidade. Ondas de drones voam em volta, miram (o olho vermelho pulsa) e disparam
 * lasers; saia da mira na hora certa e destrua todos antes do tempo acabar. Os drones mais novos desviam dos
 * golpes. A gravidade da sala deixa tudo mais dificil - e a recompensa (TP) maior.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DroneTraining {
    private static final int WAVES = 6;
    private static final String TAG = "dmzkicontrol";
    private static final ResourceLocation DEVICE = new ResourceLocation("dragonminez", "gravity_device");

    private static final class Drone {
        final UUID id;
        double angle, radius, height, spin;
        long nextShot, chargeEnd = -1;
        Vec3 aim;

        Drone(UUID id) {
            this.id = id;
        }
    }

    private static final class Session {
        final UUID player;
        final ServerLevel level;
        final AABB room;
        final double gravity;
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_6);
        final List<Drone> drones = new ArrayList<>();
        int wave;
        long waveStart, waveEnd, nextWaveAt = -1;
        int destroyed, hitsTaken, dodged, cleared;

        Session(ServerPlayer sp, AABB room, double gravity) {
            this.player = sp.getUUID();
            this.level = sp.serverLevel();
            this.room = room;
            this.gravity = gravity;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> DRONES = new HashSet<>();

    private DroneTraining() {}

    public static boolean owns(TrainingDroneEntity d) {
        return DRONES.contains(d.getUUID());
    }

    // ------------------------------------------------------------ inicio

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) return;
        BlockPos pos = event.getPos();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(pos).getBlock());
        if (!DEVICE.equals(id)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Session old = SESSIONS.get(sp.getUUID());
        if (old != null) {
            end(sp, old, "stopped");
            return;
        }
        AABB room = new AABB(sp.blockPosition()).inflate(6, 2, 6).expandTowards(0, 3, 0);
        double gravity = 1;
        try {
            if (event.getLevel().getBlockEntity(pos) instanceof GravityDeviceBlockEntity g) {
                if (g.isRoomValid() && g.getRoomMin() != null && g.getRoomMax() != null) {
                    room = new AABB(g.getRoomMin(), g.getRoomMax().offset(1, 1, 1));
                }
                if (g.isActive()) gravity = Math.max(1, g.getTargetGravity());
            }
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("[Drones] aparelho de gravidade: {}", t.toString());
        }
        Session s = new Session(sp, room, gravity);
        SESSIONS.put(sp.getUUID(), s);
        s.bar.addPlayer(sp);
        sp.sendSystemMessage(Component.translatable("training.dmzkicontrol.drones.start", (int) gravity).withStyle(ChatFormatting.AQUA));
        startWave(sp, s, 1);
    }

    private static int droneCount(int wave) {
        return Math.min(2 + wave, 8);
    }

    private static int chargeTicks(int wave) {
        return Math.max(9, 18 - (int) (wave * 1.5));
    }

    private static int cooldown(int wave) {
        return Math.max(26, 72 - wave * 8);
    }

    private static double dodgeChance(int wave) {
        return Math.min(0.5, 0.05 + 0.08 * (wave - 1));
    }

    private static void startWave(ServerPlayer sp, Session s, int wave) {
        s.wave = wave;
        long now = s.level.getGameTime();
        s.waveStart = now;
        s.waveEnd = now + 20L * (40 + 6 * wave);
        s.nextWaveAt = -1;
        RandomSource r = sp.getRandom();
        int n = droneCount(wave);
        for (int i = 0; i < n; i++) {
            TrainingDroneEntity d = ModRegistry.TRAINING_DRONE.get().create(s.level);
            if (d == null) continue;
            Drone st = new Drone(d.getUUID());
            st.angle = i * Math.PI * 2 / n + r.nextDouble() * 0.3;
            st.radius = 3.2 + r.nextDouble() * 2.5;
            st.height = 1.2 + r.nextDouble() * 2.2;
            st.spin = (r.nextBoolean() ? 1 : -1) * (0.025 + 0.006 * wave + r.nextDouble() * 0.01);
            st.nextShot = now + 30 + i * 12L + r.nextInt(20);
            Vec3 at = clampRoom(s, orbit(sp, st));
            d.moveTo(at.x, at.y, at.z, 0, 0);
            DRONES.add(d.getUUID());
            s.level.addFreshEntity(d);
            s.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
            s.drones.add(st);
        }
        title(sp, Component.translatable("training.dmzkicontrol.drones.wave", wave, WAVES).withStyle(ChatFormatting.AQUA),
                Component.translatable("training.dmzkicontrol.drones.wave_sub", n));
        s.level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    private static Vec3 orbit(ServerPlayer sp, Drone d) {
        return sp.position().add(Math.cos(d.angle) * d.radius, d.height, Math.sin(d.angle) * d.radius);
    }

    private static Vec3 clampRoom(Session s, Vec3 p) {
        AABB in = s.room.deflate(0.6);
        if (in.getXsize() <= 0 || in.getZsize() <= 0) return p;
        return new Vec3(Mth.clamp(p.x, in.minX, in.maxX), Mth.clamp(p.y, in.minY + 0.4, Math.max(in.minY + 0.4, in.maxY - 0.6)), Mth.clamp(p.z, in.minZ, in.maxZ));
    }

    // ------------------------------------------------------------ golpes nos drones

    /** O drone so leva dano de quem treina; as vezes desvia (zanzoken). */
    static boolean onDroneHit(TrainingDroneEntity d, DamageSource src) {
        Entity e = src.getEntity();
        if (!(e instanceof ServerPlayer sp)) return false;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) return false;
        Drone st = null;
        for (Drone x : s.drones) if (x.id.equals(d.getUUID())) st = x;
        if (st == null) return false;
        if (st.chargeEnd < 0 && sp.getRandom().nextDouble() < dodgeChance(s.wave)) {
            st.angle += Math.PI * (0.6 + sp.getRandom().nextDouble() * 0.8);
            Vec3 to = clampRoom(s, orbit(sp, st));
            s.level.sendParticles(ParticleTypes.POOF, d.getX(), d.getY() + 0.4, d.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
            d.teleportTo(to.x, to.y, to.z);
            playId(s.level, d.blockPosition(), "dragonminez:zanzoken", 0.7f, 1.5f);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ relogio

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(s.player);
            if (sp == null) {
                cleanup(s);
                SESSIONS.remove(s.player);
                continue;
            }
            tick(sp, s);
        }
    }

    private static void tick(ServerPlayer sp, Session s) {
        long now = s.level.getGameTime();
        if (!sp.isAlive() || sp.level() != s.level || !s.room.inflate(6).contains(sp.position())) {
            end(sp, s, "left");
            return;
        }
        if (sp.getHealth() < sp.getMaxHealth() * 0.15f) {
            end(sp, s, "fainted");
            return;
        }
        if (s.nextWaveAt >= 0) {
            if (now >= s.nextWaveAt) startWave(sp, s, s.wave + 1);
            bar(s, now);
            return;
        }
        int alive = 0;
        for (Drone st : new ArrayList<>(s.drones)) {
            Entity e = s.level.getEntity(st.id);
            if (!(e instanceof TrainingDroneEntity d) || !d.isAlive()) {
                s.drones.remove(st);
                DRONES.remove(st.id);
                s.destroyed++;
                Vec3 p = e != null ? e.position() : sp.position();
                s.level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.4, p.z, 1, 0, 0, 0, 0);
                s.level.playSound(null, BlockPos.containing(p), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.6f);
                continue;
            }
            alive++;
            fly(sp, s, st, d, now);
        }
        if (alive == 0) {
            s.cleared = s.wave;
            if (s.wave >= WAVES) {
                end(sp, s, "win");
                return;
            }
            s.nextWaveAt = now + 50;
            sp.displayClientMessage(Component.translatable("training.dmzkicontrol.drones.cleared", s.wave).withStyle(ChatFormatting.GREEN), true);
        } else if (now > s.waveEnd) {
            end(sp, s, "time");
            return;
        }
        bar(s, now);
    }

    private static void fly(ServerPlayer sp, Session s, Drone st, TrainingDroneEntity d, long now) {
        Vec3 eye = d.position().add(0, 0.45, 0);
        if (st.chargeEnd < 0) {
            st.angle += st.spin;
            Vec3 want = clampRoom(s, orbit(sp, st));
            d.setDeltaMovement(want.subtract(d.position()).scale(0.18));
            if (now >= st.nextShot) {
                st.chargeEnd = now + chargeTicks(s.wave);
                st.aim = sp.position().add(0, 1.0, 0);
                d.setCharging(true);
                playId(s.level, d.blockPosition(), "dragonminez:ki_charge_loop", 0.35f, 1.8f);
            }
        } else {
            d.setDeltaMovement(Vec3.ZERO);
            // mira tremendo: pontinhos vermelhos ate o alvo marcado
            if (now % 2 == 0) {
                Vec3 dir = st.aim.subtract(eye);
                double len = dir.length();
                for (double k = 0.6; k < len; k += 1.2) {
                    Vec3 p = eye.add(dir.scale(k / len));
                    s.level.sendParticles(sp, new DustParticleOptions(new Vector3f(1f, 0.15f, 0.1f), 0.5f), true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            if (now >= st.chargeEnd) {
                fire(sp, s, st, d, eye);
                st.chargeEnd = -1;
                st.nextShot = now + cooldown(s.wave) + sp.getRandom().nextInt(24);
                d.setCharging(false);
            }
        }
        Vec3 look = sp.getEyePosition().subtract(d.position());
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        d.setYRot(yaw);
        d.setYBodyRot(yaw);
        d.setYHeadRot(yaw);
    }

    private static void fire(ServerPlayer sp, Session s, Drone st, TrainingDroneEntity d, Vec3 eye) {
        Vec3 dir = st.aim.subtract(eye).normalize();
        Vec3 end = st.aim.add(dir.scale(3));
        double len = end.subtract(eye).length();
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1f, 0.25f, 0.2f), 1.1f);
        for (double k = 0; k < len; k += 0.25) {
            Vec3 p = eye.add(dir.scale(k));
            s.level.sendParticles(red, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
        }
        playId(s.level, d.blockPosition(), "dragonminez:laserbeam", 0.6f, 1.7f);
        // acertou se o jogador ficou na linha do tiro
        Vec3 body = sp.position().add(0, 1.0, 0);
        Vec3 rel = body.subtract(eye);
        double along = Mth.clamp(rel.dot(dir), 0, len);
        double miss = rel.subtract(dir.scale(along)).length();
        if (miss < 0.75) {
            float dmg = sp.getMaxHealth() * (0.05f + 0.012f * s.wave) * (float) Math.min(2.0, 1 + (s.gravity - 1) / 200.0);
            sp.hurt(s.level.damageSources().mobAttack(d), dmg);
            s.hitsTaken++;
            s.level.sendParticles(ParticleTypes.CRIT, body.x, body.y, body.z, 8, 0.2, 0.3, 0.2, 0.2);
        } else {
            s.dodged++;
            sp.displayClientMessage(Component.translatable("training.dmzkicontrol.drones.dodged", s.dodged).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void bar(Session s, long now) {
        float frac = s.nextWaveAt >= 0 ? 1f : Mth.clamp((s.waveEnd - now) / (float) Math.max(1, s.waveEnd - s.waveStart), 0f, 1f);
        s.bar.setProgress(frac);
        s.bar.setColor(frac < 0.25f ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
        s.bar.setName(Component.translatable("training.dmzkicontrol.drones.bar", s.wave, WAVES, s.drones.size(), (int) Math.max(0, (s.waveEnd - now) / 20)));
    }

    // ------------------------------------------------------------ fim

    private static void end(ServerPlayer sp, Session s, String why) {
        SESSIONS.remove(s.player);
        cleanup(s);
        boolean win = why.equals("win");
        long tp = reward(sp, s, win);
        CompoundTag t = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag mine = t.getCompound(TAG);
        int best = mine.getInt("drones_best");
        boolean record = s.cleared > best;
        if (record) {
            mine.putInt("drones_best", s.cleared);
            t.put(TAG, mine);
            sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, t);
        }
        title(sp, Component.translatable("training.dmzkicontrol.drones.end." + why).withStyle(win ? ChatFormatting.GOLD : ChatFormatting.RED),
                Component.translatable("training.dmzkicontrol.drones.end_sub", s.cleared, WAVES));
        sp.sendSystemMessage(Component.translatable("training.dmzkicontrol.drones.summary", s.destroyed, s.dodged, s.hitsTaken, tp).withStyle(ChatFormatting.YELLOW));
        if (record) sp.sendSystemMessage(Component.translatable("training.dmzkicontrol.drones.record", s.cleared).withStyle(ChatFormatting.GOLD));
    }

    private static void cleanup(Session s) {
        s.bar.removeAllPlayers();
        for (Drone st : s.drones) {
            Entity e = s.level.getEntity(st.id);
            if (e != null) e.discard();
            DRONES.remove(st.id);
        }
        s.drones.clear();
    }

    /** TP do DMZ pelo custo de 1 ponto de atributo no seu nivel, vezes as ondas vencidas e a gravidade. */
    private static long reward(ServerPlayer sp, Session s, boolean win) {
        if (s.cleared <= 0) return 0;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, sp).resolve().orElse(null);
        if (d == null) return 0;
        double cost;
        try {
            cost = d.getSingleStatCost(d.getTpCostTotalStats());
        } catch (Throwable t) {
            cost = 50;
        }
        if (cost <= 0 || cost == Integer.MAX_VALUE) cost = 50;
        double points = 0.35 * s.cleared + (win ? 1.5 : 0);
        double grav = Mth.clamp(Math.sqrt(s.gravity / 10.0), 1.0, 4.0);
        double tp = cost * points * grav;
        try {
            tp *= d.getTpSourceMultiplier(TpSource.TRAINING);
        } catch (Throwable ignored) {
        }
        long amount = Math.max(1, Math.round(tp));
        d.getResources().addTrainingPoints((float) amount);
        return amount;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session s = SESSIONS.remove(event.getEntity().getUUID());
        if (s != null) cleanup(s);
    }

    private static void title(ServerPlayer sp, Component main, Component sub) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(main));
    }

    private static void playId(ServerLevel level, BlockPos at, String id, float vol, float pitch) {
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if (ev != null) level.playSound(null, at, ev, SoundSource.HOSTILE, vol, pitch);
    }
}
