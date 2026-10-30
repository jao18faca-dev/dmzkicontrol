package com.dmzkicontrol.kaio;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.BubblesHudS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dmzkicontrol.registry.ModRegistry;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Treino do Senhor Kaioh: pegar o Bubbles.
 *
 * <p>Clique no Bubbles para comecar. Voce tem um tempo para encostar nele (ou bater, nao machuca),
 * sem voar, sem teletransporte e sem dash, so correndo e pulando. 5 niveis: a cada vitoria ele fica
 * mais rapido e pula mais. A recompensa e TP do DMZ, calculada pelo custo de 1 ponto de atributo no
 * seu nivel (cresce junto com voce); a primeira vitoria em cada nivel rende mais.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BubblesTraining {
    private static final String TAG = "dmzkicontrol";
    private static final String LEVEL_KEY = "bubbles_level";
    private static final String NEXT_KEY = "bubbles_next";
    private static final ResourceLocation KING_KAI = new ResourceLocation("dragonminez", "master_kaiosama");
    /** 1 unidade do atributo de velocidade anda ~2.16 blocos por tick no chao (mesma conta para jogador e mob). */
    private static final double BLOCKS_PER_TICK_PER_SPEED = 2.16;

    private static final class Session {
        final UUID player;
        final int bubblesId;
        final int level;
        final long end;
        final int total;
        Vec3 last;
        int airTicks;
        double topSpeed;
        long nextNearMiss;
        boolean said30, said10;

        Session(ServerPlayer p, BubblesEntity b, int level, int ticks) {
            this.player = p.getUUID();
            this.bubblesId = b.getId();
            this.level = level;
            this.total = ticks;
            this.end = p.level().getGameTime() + ticks;
            this.last = p.position();
            this.topSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3 * BLOCKS_PER_TICK_PER_SPEED;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private BubblesTraining() {}

    private static KiControlConfig.Bubbles cfg() {
        return KiControlConfig.get().bubbles;
    }

    // ------------------------------------------------------------------ mensagens

    private static MutableComponent kai(String key, Object... args) {
        return Component.translatable("message.dmzkicontrol.kai_prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.dmzkicontrol." + key, args).withStyle(ChatFormatting.YELLOW));
    }

    private static void say(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(kai(key, args), false);
    }

    private static void hud(ServerPlayer sp, int state, int level, int ticksLeft, int total, int bubblesId) {
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BubblesHudS2C(state, level, ticksLeft, total, bubblesId));
    }

    // ------------------------------------------------------------------ progresso salvo

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    private static long clock(ServerPlayer sp) {
        return sp.server.overworld().getGameTime();
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG)) event.getEntity().getPersistentData().put(TAG, old.getCompound(TAG).copy());
    }

    // ------------------------------------------------------------------ inicio

    public static void tryStart(ServerPlayer sp, BubblesEntity b) {
        KiControlConfig.Bubbles c = cfg();
        if (!c.enabled) return;
        Session mine = SESSIONS.get(sp.getUUID());
        if (mine != null) return;
        if (b.isTraining()) {
            say(sp, "bubbles_busy");
            return;
        }
        CompoundTag d = data(sp);
        long wait = d.getLong(NEXT_KEY) - clock(sp);
        if (wait > 0) {
            long secs = (wait + 19) / 20;
            String time = secs >= 60 ? (secs / 60) + " min" : secs + " s";
            say(sp, "bubbles_rest", time);
            return;
        }
        int done = Mth.clamp(d.getInt(LEVEL_KEY), 0, 5);
        int level = Math.min(5, done + 1);
        int ticks = Math.max(10, c.timeSeconds) * 20;
        Session s = new Session(sp, b, level, ticks);
        SESSIONS.put(sp.getUUID(), s);
        b.startChase(sp, level);
        say(sp, done >= 5 ? "bubbles_start_replay" : "bubbles_start", level, ticks / 20);
        sp.level().playSound(null, b.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 1f, 1.8f);
        hud(sp, BubblesHudS2C.RUNNING, level, ticks, ticks, b.getId());
    }

    // ------------------------------------------------------------------ durante

    /** Velocidade do jogador em unidades de atributo (o que o Bubbles usa como base). */
    public static double effectiveSpeed(Player p) {
        Session s = SESSIONS.get(p.getUUID());
        double attr = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3;
        if (s == null) return attr;
        return Math.max(attr, s.topSpeed / BLOCKS_PER_TICK_PER_SPEED);
    }

    public static void onLeap(BubblesEntity b, Player p) {
        Session s = p == null ? null : SESSIONS.get(p.getUUID());
        if (!(p instanceof ServerPlayer sp) || s == null) return;
        long now = sp.level().getGameTime();
        if (b.distanceTo(sp) < 2.0 && now >= s.nextNearMiss) {
            s.nextNearMiss = now + 80;
            sp.displayClientMessage(kai("bubbles_almost"), true);
        }
    }

    public static void onTouched(ServerPlayer sp, BubblesEntity b) {
        Session s = SESSIONS.get(sp.getUUID());
        if (s != null && s.bubblesId == b.getId()) win(sp, s, b);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof BubblesEntity b)) return;
        if (event.getEntity() instanceof ServerPlayer sp) onTouched(sp, b);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDash(DMZEvent.PlayerDashEvent event) {
        ServerPlayer sp = event.getPlayer();
        if (sp == null || !SESSIONS.containsKey(sp.getUUID()) || !event.isCancelable()) return;
        event.setCanceled(true);
        sp.displayClientMessage(kai("bubbles_no_dash"), true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(s.player);
            if (sp == null) {
                Entity e = findBubbles(server, s);
                if (e instanceof BubblesEntity b) b.stopChase(false);
                it.remove();
                continue;
            }
            Entity e = sp.level().getEntity(s.bubblesId);
            if (!(e instanceof BubblesEntity b) || !b.isAlive() || b.level() != sp.level()) {
                it.remove();
                hud(sp, BubblesHudS2C.NONE, s.level, 0, s.total, -1);
                continue;
            }
            String fail = check(sp, s, b);
            if (fail != null) {
                it.remove();
                lose(sp, s, b, fail);
                continue;
            }
            if (sp.getBoundingBox().inflate(0.3).intersects(b.getBoundingBox())) {
                it.remove();
                win(sp, s, b);
                continue;
            }
            long left = s.end - sp.level().getGameTime();
            if (!s.said30 && left <= s.total / 2 && s.total >= 40 * 20) { s.said30 = true; say(sp, "bubbles_half"); }
            if (!s.said10 && left <= 200) { s.said10 = true; say(sp, "bubbles_ten"); }
            if (sp.tickCount % 10 == 0) hud(sp, BubblesHudS2C.RUNNING, s.level, (int) left, s.total, b.getId());
        }
    }

    private static Entity findBubbles(net.minecraft.server.MinecraftServer server, Session s) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(s.bubblesId);
            if (e != null) return e;
        }
        return null;
    }

    /** null = tudo certo; senao, o motivo da falha. */
    private static String check(ServerPlayer sp, Session s, BubblesEntity b) {
        long left = s.end - sp.level().getGameTime();
        if (left <= 0) return "time";
        Vec3 pos = sp.position();
        double moved = pos.subtract(s.last).horizontalDistance();
        s.last = pos;
        if (moved > 10.0) return "teleport";
        if (moved < 2.5) s.topSpeed = Math.max(moved, s.topSpeed * 0.985);
        if (sp.getAbilities().flying) return "fly";
        if (!sp.onGround() && !sp.isInWater() && heightAboveGround(sp) > 4.0) {
            if (++s.airTicks > 30) return "fly";
        } else s.airTicks = 0;
        if (sp.distanceTo(b) > 48) return "lost";
        return null;
    }

    private static double heightAboveGround(ServerPlayer sp) {
        BlockPos p = sp.blockPosition();
        for (int i = 0; i <= 6; i++) {
            if (!sp.level().getBlockState(p.below(i)).getCollisionShape(sp.level(), p.below(i)).isEmpty()) return i;
        }
        return 99;
    }

    // ------------------------------------------------------------------ fim

    private static void win(ServerPlayer sp, Session s, BubblesEntity b) {
        SESSIONS.remove(sp.getUUID());
        b.stopChase(true);
        KiControlConfig.Bubbles c = cfg();
        CompoundTag d = data(sp);
        int done = d.getInt(LEVEL_KEY);
        boolean first = s.level > done;
        if (first) d.putInt(LEVEL_KEY, s.level);
        d.putLong(NEXT_KEY, clock(sp) + Math.max(0, c.cooldownMinutes) * 60L * 20L);

        long tp = reward(sp, s.level, first);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.2f);
        say(sp, "bubbles_win", s.level, tp);
        if (first && s.level == 5) say(sp, "bubbles_master");
        else if (first) say(sp, "bubbles_next_level", s.level + 1);
        hud(sp, BubblesHudS2C.WIN, s.level, (int) Math.max(0, s.end - sp.level().getGameTime()), s.total, b.getId());
    }

    private static void lose(ServerPlayer sp, Session s, BubblesEntity b, String why) {
        b.stopChase(false);
        data(sp).putLong(NEXT_KEY, clock(sp) + Math.max(0, cfg().failCooldownSeconds) * 20L);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1.0f);
        say(sp, "bubbles_fail_" + why);
        hud(sp, BubblesHudS2C.FAIL, s.level, 0, s.total, b.getId());
    }

    private static long reward(ServerPlayer sp, int level, boolean first) {
        KiControlConfig.Bubbles c = cfg();
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, sp).resolve().orElse(null);
        if (d == null) return 0;
        double points = c.rewardStatPoints == null || c.rewardStatPoints.isEmpty() ? 1.0
                : c.rewardStatPoints.get(Mth.clamp(level - 1, 0, c.rewardStatPoints.size() - 1));
        double cost;
        try {
            cost = d.getSingleStatCost(d.getTpCostTotalStats());
        } catch (Throwable t) {
            cost = 50;
        }
        if (cost <= 0 || cost == Integer.MAX_VALUE) cost = 50;
        double tp = cost * points * (first ? c.firstClearMultiplier : 1.0);
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
        if (s != null && event.getEntity().level().getEntity(s.bubblesId) instanceof BubblesEntity b) b.stopChase(false);
    }

    // ------------------------------------------------------------------ o Bubbles aparece perto do Kaioh

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 100 != 37) return;
        KiControlConfig.Bubbles c = cfg();
        if (!c.enabled || !c.spawnNearKingKai) return;
        ServerLevel level = sp.serverLevel();
        List<Entity> kais = level.getEntities((Entity) null, sp.getBoundingBox().inflate(64),
                e -> KING_KAI.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())));
        for (Entity kai : kais) {
            AABB around = kai.getBoundingBox().inflate(48);
            if (!level.getEntitiesOfClass(BubblesEntity.class, around).isEmpty()) continue;
            BubblesEntity b = ModRegistry.BUBBLES.get().create(level);
            if (b == null) continue;
            double ang = level.random.nextDouble() * Math.PI * 2;
            double r = 4 + level.random.nextDouble() * 3;
            int x = Mth.floor(kai.getX() + Math.cos(ang) * r);
            int z = Mth.floor(kai.getZ() + Math.sin(ang) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - kai.getY()) > 6) y = Mth.floor(kai.getY());
            b.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            b.setHome(kai.blockPosition());
            level.addFreshEntity(b);
        }
    }
}
