package com.dmzkicontrol.encounter;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.encounter.EncScript.Beat;
import com.dmzkicontrol.encounter.EncScript.Cue;
import com.dmzkicontrol.encounter.EncScript.Ev;
import com.dmzkicontrol.encounter.EncScript.Kind;
import com.dmzkicontrol.encounter.EncScript.Mode;
import com.dmzkicontrol.encounter.EncScript.Move;
import com.dmzkicontrol.encounter.EncScript.MoveKind;
import com.dmzkicontrol.encounter.EncScript.Phase;
import com.dmzkicontrol.network.EncEventS2C;
import com.dmzkicontrol.network.EncStateS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Servidor das lutas cinematograficas. Quando a vida de um chefe com roteiro cai abaixo de uma fase, a luta
 * "congela" (chefe sem IA e invulneravel, jogadores invulneraveis e flutuando) e o roteiro roda: cenas, deixas de
 * botao (cada cliente julga o proprio tempo e manda o resultado), cabo de guerra e janela de transformacao.
 * Acertos tiram vida do chefe; erros tiram vida de quem errou (nunca mata). No fim tudo volta ao normal e a luta
 * segue de onde estava.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EncounterDirector {
    private static final String DONE = "dmzk_enc_done";
    private static final String LAST = "dmzk_enc_last";
    private static final String LOCK = "dmzk_enc_lock";
    private static final String PLAYER_LOCK = "dmzk_enc_nograv";
    /** Folga para o pacote do cliente chegar depois do fim da janela. */
    private static final int GRACE = 8;
    /** Diferenca maxima (ticks) entre os acertos para valer o golpe combinado. */
    private static final float SYNC_TICKS = 4.5f;

    public static final byte RES_NONE = 0, RES_MISS = 1, RES_HIT = 2, RES_PERFECT = 3;

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final List<Runnable> PENDING = new ArrayList<>();
    /** Cenas agendadas pelo comando de teste: {nivel, tick, acao}. */
    private static final List<Object[]> DELAYED = new ArrayList<>();
    private static int nextId = 1;

    private EncounterDirector() {}

    // ------------------------------------------------------------------ estado de uma luta

    static final class Run {
        final int id;
        final EncScript script;
        final int phaseIdx;
        final Phase phase;
        final ServerLevel level;
        final LivingEntity boss;
        final List<ServerPlayer> players;
        final Vec3 origin;
        final double fx, fz;
        int beatIdx;
        int variant;
        long beatStart;
        // chefe
        Vec3 bossTarget;
        float bossSpeed = 0.3f;
        float shove;
        // deixas
        byte[][] res;
        float[][] off;
        boolean[] resolved;
        // cabo de guerra / janela
        float progress = 0.5f;
        int[] mash;
        boolean mashDone;
        String[] formAtStart;
        int[] charge;
        byte[] charged;
        long allDoneAt = -1;
        final float[] mult;
        final List<Object[]> scheduled = new ArrayList<>();

        Run(int id, EncScript script, int phaseIdx, ServerLevel level, LivingEntity boss, List<ServerPlayer> players, Vec3 origin, double fx, double fz) {
            this.id = id;
            this.script = script;
            this.phaseIdx = phaseIdx;
            this.phase = script.phases.get(phaseIdx);
            this.level = level;
            this.boss = boss;
            this.players = players;
            this.origin = origin;
            this.fx = fx;
            this.fz = fz;
            this.mult = new float[players.size()];
            Arrays.fill(mult, 1f);
            this.bossTarget = new Vec3(0, phase.lift, 0);
        }

        int n() {
            return players.size();
        }

        Beat beat() {
            Beat b = phase.beats.get(beatIdx);
            if (variant == 1 && b.win != null) return b.win;
            if (variant == 2 && b.lose != null) return b.lose;
            return b;
        }

        Vec3 world(double x, double y, double z) {
            // x = direita de quem olha o chefe = (fz, 0, -fx)
            return origin.add(fz * x + fx * z, y, -fx * x + fz * z);
        }

        Vec3 local(Vec3 w) {
            Vec3 d = w.subtract(origin);
            return new Vec3(d.x * fz - d.z * fx, d.y, d.x * fx + d.z * fz);
        }

        int roleOf(ServerPlayer sp) {
            for (int i = 0; i < players.size(); i++) if (players.get(i).getUUID().equals(sp.getUUID())) return i;
            return -1;
        }
    }

    public static boolean isLocked(LivingEntity e) {
        for (Run r : RUNS.values()) {
            if (r.boss == e) return true;
            for (ServerPlayer p : r.players) if (p == e) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ gatilho

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        // a luta cinematografica nunca pode derrubar o jogo: qualquer erro aqui so desliga a cena
        try {
            tryStart(event);
        } catch (Throwable t) {
            if (!triggerFailed) KiControlMod.LOGGER.error("[Lutas] erro ao checar o gatilho da cena; ignorando.", t);
            triggerFailed = true;
        }
    }

    private static boolean triggerFailed;

    private static void tryStart(LivingDamageEvent event) {
        KiControlConfig.Encounters cfg = KiControlConfig.get().encounters;
        if (!cfg.enabled || event.isCanceled()) return;
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide || boss instanceof Player) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(boss.getType());
        if (key == null) return;
        EncScript script = EncScripts.forBoss(key.toString());
        if (script == null || cfg.disabled.contains(script.id)) return;
        if (isLocked(boss) || isLocked(attacker)) return;
        float max = boss.getMaxHealth();
        float after = boss.getHealth() - event.getAmount();
        if (after <= 0 || max <= 0) return;
        float frac = after / max;
        CompoundTag pd = boss.getPersistentData();
        int done = pd.getInt(DONE);
        long now = boss.level().getGameTime();
        if (now - pd.getLong(LAST) < (long) (cfg.cooldownSeconds * 20) && pd.contains(LAST)) return;
        int pick = -1;
        for (int i = 0; i < script.phases.size(); i++) {
            if ((done & (1 << i)) != 0) continue;
            if (frac < script.phases.get(i).at) { pick = i; break; }
        }
        if (pick < 0) return;
        // fases que ficaram para tras (dano grande de uma vez) nao voltam
        for (int i = 0; i <= pick; i++) done |= 1 << i;
        pd.putInt(DONE, done);
        final int phase = pick;
        PENDING.add(() -> start(script, phase, boss, attacker));
    }

    private static void start(EncScript script, int phaseIdx, LivingEntity boss, ServerPlayer attacker) {
        if (!boss.isAlive() || !attacker.isAlive() || !(boss.level() instanceof ServerLevel level)) return;
        if (attacker.level() != level || isLocked(boss) || isLocked(attacker)) return;
        KiControlConfig.Encounters cfg = KiControlConfig.get().encounters;
        List<ServerPlayer> ps = new ArrayList<>();
        ps.add(attacker);
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p == attacker || !p.isAlive() || p.isSpectator() || isLocked(p)) continue;
            if (p.distanceTo(boss) <= cfg.joinRadius) near.add(p);
        }
        near.sort((a, b) -> Double.compare(a.distanceToSqr(boss), b.distanceToSqr(boss)));
        for (ServerPlayer p : near) if (ps.size() < cfg.maxPlayers) ps.add(p);

        // arena: origem no chao embaixo do chefe, eixo do chefe para quem bateu
        Vec3 bp = boss.position();
        BlockPos.MutableBlockPos m = BlockPos.containing(bp).mutable();
        int floor = (int) Math.floor(bp.y);
        for (int i = 0; i < 24; i++) {
            m.setY(floor - i);
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) { floor = floor - i + 1; break; }
        }
        Vec3 origin = new Vec3(bp.x, Math.min(bp.y, floor), bp.z);
        Vec3 to = attacker.position().subtract(bp);
        double len = Math.sqrt(to.x * to.x + to.z * to.z);
        double fx, fz;
        if (len < 0.5) {
            float yaw = boss.getYRot() * Mth.DEG_TO_RAD;
            fx = -Mth.sin(yaw);
            fz = Mth.cos(yaw);
        } else {
            fx = to.x / len;
            fz = to.z / len;
        }
        Run run = new Run(nextId++, script, phaseIdx, level, boss, ps, origin, fx, fz);

        // trava o chefe (e guarda como estava, para o caso do servidor cair no meio)
        CompoundTag lock = new CompoundTag();
        lock.putBoolean("ai", boss instanceof Mob mob && mob.isNoAi());
        lock.putBoolean("inv", boss.isInvulnerable());
        lock.putBoolean("ng", boss.isNoGravity());
        boss.getPersistentData().put(LOCK, lock);
        if (boss instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        boss.setInvulnerable(true);
        boss.setNoGravity(true);
        boss.setDeltaMovement(Vec3.ZERO);
        for (ServerPlayer p : ps) {
            p.getPersistentData().putBoolean(PLAYER_LOCK, p.isNoGravity());
            p.setNoGravity(true);
            p.fallDistance = 0;
        }
        RUNS.put(run.id, run);
        KiControlMod.LOGGER.info("[Lutas] {} fase {} com {} jogador(es).", script.id, phaseIdx + 1, ps.size());
        beginBeat(run, 0, 0);
    }

    // ------------------------------------------------------------------ comando de teste

    /** /dmzluta: usa o chefe mais perto (32 blocos) ou invoca um na frente do jogador, e comeca a fase. */
    public static String debugStart(ServerPlayer sp, EncScript script, int phase) {
        if (isLocked(sp)) return "Voce ja esta numa cena (use /dmzluta parar).";
        ServerLevel level = sp.serverLevel();
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation(script.bossType));
        if (type == null) return "Chefe nao encontrado no DragonMineZ: " + script.bossType;
        LivingEntity boss = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(32), x -> x.getType() == type && x.isAlive())) {
            if (boss == null || sp.distanceToSqr(e) < sp.distanceToSqr(boss)) boss = e;
        }
        if (boss != null && isLocked(boss)) return "Esse chefe ja esta numa cena.";
        if (boss == null) {
            Vec3 look = sp.getLookAngle();
            Vec3 h = new Vec3(look.x, 0, look.z);
            if (h.lengthSqr() < 1e-4) h = new Vec3(0, 0, 1);
            BlockPos pos = BlockPos.containing(sp.position().add(h.normalize().scale(6)));
            var spawned = type.spawn(level, pos, net.minecraft.world.entity.MobSpawnType.COMMAND);
            if (!(spawned instanceof LivingEntity le)) return "Nao consegui invocar o chefe.";
            boss = le;
        }
        final LivingEntity b = boss;
        b.getPersistentData().remove(LAST);
        // espera meio segundo: o chefe recem-criado chega aos clientes antes da cena
        DELAYED.add(new Object[]{level, level.getGameTime() + 10, (Runnable) () -> start(script, phase, b, sp)});
        return null;
    }

    /** /dmzluta parar */
    public static int stopAll() {
        int n = RUNS.size();
        for (Run r : new ArrayList<>(RUNS.values())) finish(r, true);
        DELAYED.clear();
        return n;
    }

    // ------------------------------------------------------------------ andamento

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!DELAYED.isEmpty()) {
            Iterator<Object[]> dit = DELAYED.iterator();
            List<Runnable> now = new ArrayList<>();
            while (dit.hasNext()) {
                Object[] d = dit.next();
                if (((ServerLevel) d[0]).getGameTime() >= (long) d[1]) {
                    now.add((Runnable) d[2]);
                    dit.remove();
                }
            }
            PENDING.addAll(now);
        }
        if (!PENDING.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Runnable r : copy) {
                try {
                    r.run();
                } catch (Throwable t) {
                    KiControlMod.LOGGER.error("[Lutas] falha ao comecar a cena", t);
                }
            }
        }
        if (RUNS.isEmpty()) return;
        for (Run run : new ArrayList<>(RUNS.values())) {
            try {
                tick(run);
            } catch (Throwable t) {
                KiControlMod.LOGGER.error("[Lutas] erro na cena {}; encerrando.", run.script.id, t);
                finish(run, true);
            }
        }
    }

    private static void tick(Run run) {
        if (!run.boss.isAlive() || run.boss.isRemoved() || run.boss.level() != run.level) {
            finish(run, true);
            return;
        }
        run.players.removeIf(p -> !p.isAlive() || p.hasDisconnected() || p.level() != run.level);
        if (run.players.isEmpty()) {
            finish(run, true);
            return;
        }
        long now = run.level.getGameTime();
        int t = (int) (now - run.beatStart);
        Beat beat = run.beat();

        // eventos do roteiro neste tick (a parte que e do servidor: chefe, dano)
        for (Ev ev : beat.evs) if (ev.t == t) apply(run, ev, -1);
        if (beat.kind == Kind.PROMPT) {
            for (Cue c : beat.cues) {
                for (Ev ev : c.tell) {
                    if (c.t + ev.t != t) continue;
                    for (int role : involved(run, c)) apply(run, ev, role);
                }
            }
        }
        Iterator<Object[]> it = run.scheduled.iterator();
        List<Object[]> due = new ArrayList<>();
        while (it.hasNext()) {
            Object[] s = it.next();
            if ((long) s[0] <= now) {
                due.add(s);
                it.remove();
            }
        }
        for (Object[] s : due) apply(run, (Ev) s[1], (int) s[2]);

        moveBoss(run);
        for (ServerPlayer p : run.players) {
            p.fallDistance = 0;
            p.setNoGravity(true);
        }

        boolean over = t >= beat.len;
        switch (beat.kind) {
            case PROMPT -> {
                boolean all = true;
                for (Cue c : beat.cues) {
                    if (run.resolved[c.index]) continue;
                    if (ready(run, c, t)) resolve(run, beat, c, now);
                    else all = false;
                }
                over = over && all;
            }
            case MASH -> {
                if (!run.mashDone) {
                    tickMash(run, beat, t, now);
                    over = false;
                }
            }
            case CHARGE -> {
                tickCharge(run, now);
                boolean allDone = true;
                for (byte b : run.charged) if (b == 0) allDone = false;
                if (allDone && run.allDoneAt < 0) run.allDoneAt = now;
                if (run.allDoneAt >= 0 && now - run.allDoneAt > 30) over = true;
            }
            default -> {}
        }
        if (over) advance(run);
    }

    private static void advance(Run run) {
        if (run.variant == 0 && run.beat().kind == Kind.MASH && run.mashDone) {
            // a variante (vitoria/derrota) ja foi aberta em resolveMash
            return;
        }
        int next = run.beatIdx + 1;
        if (next >= run.phase.beats.size()) {
            finish(run, false);
            return;
        }
        beginBeat(run, next, 0);
    }

    private static void beginBeat(Run run, int idx, int variant) {
        run.beatIdx = idx;
        run.variant = variant;
        // comeca no proximo tick: o pacote chega antes e o t = 0 roda nos dois lados
        run.beatStart = run.level.getGameTime() + 1;
        Beat b = run.beat();
        int n = run.n();
        run.res = new byte[b.cues.size()][n];
        run.off = new float[b.cues.size()][n];
        run.resolved = new boolean[b.cues.size()];
        run.mash = new int[n];
        run.progress = 0.5f;
        run.mashDone = false;
        run.charge = new int[n];
        run.charged = new byte[n];
        run.allDoneAt = -1;
        if (b.kind == Kind.CHARGE) {
            run.formAtStart = new String[n];
            for (int i = 0; i < n; i++) run.formAtStart[i] = formKey(run.players.get(i));
        }
        sendState(run, false);
    }

    private static void finish(Run run, boolean aborted) {
        RUNS.remove(run.id);
        LivingEntity boss = run.boss;
        CompoundTag lock = boss.getPersistentData().getCompound(LOCK);
        restoreBoss(boss, lock);
        bossAnim(run, "charge_off");
        boss.getPersistentData().putLong(LAST, run.level.getGameTime());
        for (ServerPlayer p : run.players) restorePlayer(p);
        sendState(run, true);
        if (boss instanceof Mob mob && !run.players.isEmpty()) mob.setTarget(run.players.get(0));
        KiControlMod.LOGGER.info("[Lutas] {} fase {} {}.", run.script.id, run.phaseIdx + 1, aborted ? "interrompida" : "concluida");
    }

    private static void restoreBoss(LivingEntity boss, CompoundTag lock) {
        if (boss instanceof Mob mob) mob.setNoAi(lock.getBoolean("ai"));
        boss.setInvulnerable(lock.getBoolean("inv"));
        boss.setNoGravity(lock.getBoolean("ng"));
        boss.getPersistentData().remove(LOCK);
    }

    private static void restorePlayer(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        p.setNoGravity(pd.getBoolean(PLAYER_LOCK));
        pd.remove(PLAYER_LOCK);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ eventos (lado do servidor)

    private static List<Integer> involved(Run run, Cue c) {
        List<Integer> out = new ArrayList<>();
        int n = run.n();
        int r = c.role(n);
        if (r >= 0) out.add(r);
        else for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    /** Alvos de um evento: -1 = chefe, 0..3 = papeis. */
    static List<Integer> targets(int who, int self, int n) {
        List<Integer> out = new ArrayList<>();
        switch (who) {
            case EncScript.BOSS -> out.add(-1);
            case EncScript.ALL -> { for (int i = 0; i < n; i++) out.add(i); }
            case EncScript.OTHERS -> { for (int i = 2; i < n; i++) out.add(i); }
            case EncScript.SELF -> { if (self >= 0 && self < n) out.add(self); }
            default -> { if (who >= 0 && who < n) out.add(who); }
        }
        return out;
    }

    private static void apply(Run run, Ev ev, int self) {
        int n = run.n();
        if (n < ev.minN || n > ev.maxN) return;
        List<Integer> tg = targets(ev.who, self, n);
        for (int role : tg) {
            if (role < 0) {
                if (ev.move != null) bossMove(run, ev.move, self);
                if (ev.dmg > 0) damageBoss(run, ev.dmg);
                if (ev.bossAnim != null) bossAnim(run, ev.bossAnim);
                if (ev.shove > 0) run.shove = Math.max(run.shove, ev.shove);
            } else if (ev.hurt > 0) {
                hurt(run, role, ev.hurt);
            }
        }
    }

    private static void bossMove(Run run, Move m, int self) {
        Vec3 cur = run.bossTarget;
        Vec3 target = switch (m.kind()) {
            case ABS -> new Vec3(m.x(), m.y(), m.z());
            case OFFSET -> cur.add(m.x(), m.y(), m.z());
            case TO_ROLE -> {
                int ref = m.ref() == EncScript.SELF ? self : m.ref();
                if (ref < 0 || ref >= run.n()) yield cur;
                yield run.local(run.players.get(ref).position()).add(m.x(), m.y(), m.z());
            }
            default -> cur;
        };
        run.bossTarget = target;
        run.bossSpeed = m.speed();
    }

    private static void moveBoss(Run run) {
        LivingEntity boss = run.boss;
        Vec3 want = run.world(run.bossTarget.x, run.bossTarget.y, run.bossTarget.z - run.shove);
        run.shove *= 0.72f;
        if (run.shove < 0.01f) run.shove = 0;
        Vec3 pos = boss.position();
        Vec3 d = want.subtract(pos);
        Vec3 next;
        if (run.bossSpeed >= 1f || d.lengthSqr() < 0.0004) next = want;
        else if (run.bossSpeed < 0) next = d.length() <= -run.bossSpeed ? want : pos.add(d.normalize().scale(-run.bossSpeed));
        else next = pos.add(d.scale(run.bossSpeed));
        // olha para o grupo
        Vec3 c = Vec3.ZERO;
        for (ServerPlayer p : run.players) c = c.add(p.position());
        c = c.scale(1.0 / run.players.size());
        Vec3 look = c.subtract(next);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        boss.setDeltaMovement(Vec3.ZERO);
        boss.moveTo(next.x, next.y, next.z, yaw, 0f);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
    }

    /** Faz o chefe reagir com as animacoes do proprio DMZ (sincronizadas pelo GeckoLib para todos). */
    private static void bossAnim(Run run, String anim) {
        LivingEntity b = run.boss;
        try {
            switch (anim) {
                case "attack" -> b.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                case "charge_on", "charge_off" -> {
                    if (b instanceof com.dragonminez.common.init.entities.sagas.DBSagasEntity s) s.setKiCharge(anim.equals("charge_on"));
                }
                default -> {
                    if (b instanceof software.bernie.geckolib.animatable.GeoEntity geo) geo.triggerAnim("hurt_controller", anim);
                }
            }
        } catch (Throwable t) {
            // chefe de outro mod ou versao: segue sem a animacao
        }
    }

    private static void damageBoss(Run run, float pct) {
        LivingEntity boss = run.boss;
        float max = boss.getMaxHealth();
        float hp = boss.getHealth();
        float next = Math.max(max * 0.03f, hp - max * pct * (float) KiControlConfig.get().encounters.bossDamageMultiplier);
        if (next < hp) boss.setHealth(next);
    }

    private static void hurt(Run run, int role, float pct) {
        if (role < 0 || role >= run.n()) return;
        ServerPlayer p = run.players.get(role);
        if (p.isCreative()) return;
        float max = p.getMaxHealth();
        float next = Math.max(1f, p.getHealth() - max * pct * (float) KiControlConfig.get().encounters.missDamageMultiplier);
        if (next < p.getHealth()) p.setHealth(next);
    }

    private static void schedule(Run run, long now, List<Ev> evs, int self) {
        for (Ev ev : evs) run.scheduled.add(new Object[]{now + ev.t, ev, self});
    }

    // ------------------------------------------------------------------ deixas

    private static boolean ready(Run run, Cue c, int t) {
        if (t > c.t + c.win + GRACE) return true;
        for (int role : involved(run, c)) if (run.res[c.index][role] == RES_NONE) return false;
        return true;
    }

    private static void resolve(Run run, Beat beat, Cue c, long now) {
        run.resolved[c.index] = true;
        List<Integer> roles = involved(run, c);
        byte[] res = run.res[c.index];
        int hits = 0;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, multSum = 0;
        for (int r : roles) {
            if (res[r] == RES_NONE) res[r] = RES_MISS;
            boolean hit = res[r] >= RES_HIT;
            if (hit) {
                hits++;
                lo = Math.min(lo, run.off[c.index][r]);
                hi = Math.max(hi, run.off[c.index][r]);
                multSum += run.mult[r];
                if (c.dmg > 0) damageBoss(run, c.dmg * run.mult[r]);
                schedule(run, now, c.hit, r);
            } else {
                if (c.hurt > 0) hurt(run, r, c.hurt);
                schedule(run, now, c.miss, r);
            }
        }
        byte sync = 0;
        if (c.mode == Mode.ALL && hits > 0) {
            float avg = multSum / hits;
            if (hits == roles.size() && hi - lo <= SYNC_TICKS) {
                sync = 2;
                if (c.syncDmg > 0) damageBoss(run, c.syncDmg * avg);
                schedule(run, now, c.sync, -1);
            } else {
                sync = 1;
                if (c.syncDmg > 0) damageBoss(run, c.syncDmg * 0.5f * avg * hits / roles.size());
                schedule(run, now, c.partial, -1);
            }
        }
        send(run, new EncEventS2C(run.id, run.beatIdx, EncEventS2C.CUE, c.index, sync, res.clone(), 0f));
    }

    /** Resultado de uma deixa vindo do cliente. */
    public static void onInput(ServerPlayer sp, int encId, int beat, int cue, byte result, float offset) {
        Run run = RUNS.get(encId);
        if (run == null || run.beatIdx != beat || run.variant != 0) return;
        Beat b = run.beat();
        if (b.kind != Kind.PROMPT || cue < 0 || cue >= b.cues.size() || run.resolved[cue]) return;
        int role = run.roleOf(sp);
        if (role < 0 || !b.cues.get(cue).involves(role, run.n())) return;
        if (run.res[cue][role] != RES_NONE) return;
        run.res[cue][role] = (byte) Mth.clamp(result, RES_MISS, RES_PERFECT);
        run.off[cue][role] = offset;
    }

    // ------------------------------------------------------------------ cabo de guerra e janela

    public static void onMash(ServerPlayer sp, int encId, int beat, int presses) {
        Run run = RUNS.get(encId);
        if (run == null || run.beatIdx != beat || run.variant != 0) return;
        int role = run.roleOf(sp);
        if (role < 0) return;
        presses = Mth.clamp(presses, 0, 12);
        Kind k = run.beat().kind;
        if (k == Kind.MASH) run.mash[role] += presses;
        else if (k == Kind.CHARGE) run.charge[role] += presses;
    }

    private static void tickMash(Run run, Beat beat, int t, long now) {
        int n = run.n();
        int total = 0;
        for (int i = 0; i < n; i++) total += run.mash[i];
        float share = 0.5f + 0.5f * n;
        run.progress += total * beat.gain / share - beat.pushAt(t);
        run.progress = Mth.clamp(run.progress, 0f, 1f);
        byte[] recent = new byte[n];
        for (int i = 0; i < n; i++) recent[i] = (byte) Math.min(127, run.mash[i]);
        Arrays.fill(run.mash, 0);
        if (t % 2 == 0) send(run, new EncEventS2C(run.id, run.beatIdx, EncEventS2C.MASH, 0, (byte) 0, recent, run.progress));
        boolean win = run.progress >= 1f;
        boolean lose = run.progress <= 0f;
        if (!win && !lose && t < beat.len) return;
        if (!win && !lose) win = run.progress >= 0.5f;
        run.mashDone = true;
        send(run, new EncEventS2C(run.id, run.beatIdx, EncEventS2C.MASH_END, win ? 1 : 0, (byte) 0, new byte[0], run.progress));
        float avg = 0;
        for (float m : run.mult) avg += m;
        avg /= n;
        if (win) {
            if (beat.dmgWin > 0) damageBoss(run, beat.dmgWin * avg);
        } else if (beat.hurtLose > 0) {
            for (int i = 0; i < n; i++) hurt(run, i, beat.hurtLose);
        }
        Beat next = win ? beat.win : beat.lose;
        if (next != null) beginBeat(run, run.beatIdx, win ? 1 : 2);
        else {
            run.mashDone = false;
            int idx = run.beatIdx + 1;
            if (idx >= run.phase.beats.size()) finish(run, false);
            else beginBeat(run, idx, 0);
        }
    }

    private static void tickCharge(Run run, long now) {
        for (int i = 0; i < run.n(); i++) {
            if (run.charged[i] != 0) continue;
            ServerPlayer p = run.players.get(i);
            String form = formKey(p);
            if (!form.isEmpty() && !form.equals(run.formAtStart[i])) {
                run.charged[i] = 1;
                run.mult[i] = Math.max(run.mult[i], 1.5f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 40, 0, false, true));
                send(run, new EncEventS2C(run.id, run.beatIdx, EncEventS2C.CHARGED, i, (byte) 1, new byte[0], 0f));
            } else if (run.charge[i] >= 50) {
                run.charged[i] = 2;
                run.mult[i] = Math.max(run.mult[i], 1.25f);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 25, 0, false, true));
                send(run, new EncEventS2C(run.id, run.beatIdx, EncEventS2C.CHARGED, i, (byte) 2, new byte[0], 0f));
            }
        }
    }

    private static String formKey(ServerPlayer p) {
        try {
            StatsData d = AuraSealManager.stats(p);
            if (d == null) return "";
            Character c = d.getCharacter();
            String a = c.hasActiveForm() ? c.getActiveFormGroup() + "." + c.getActiveForm() : "";
            String b = c.hasActiveStackForm() ? c.getActiveStackFormGroup() + "." + c.getActiveStackForm() : "";
            return a.isEmpty() && b.isEmpty() ? "" : a + "|" + b;
        } catch (Throwable t) {
            return "";
        }
    }

    // ------------------------------------------------------------------ rede

    private static void sendState(Run run, boolean end) {
        int[] ids = new int[run.players.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = run.players.get(i).getId();
        send(run, new EncStateS2C(run.id, run.script.id, run.phaseIdx, run.beatIdx, run.variant, run.beatStart,
                run.origin.x, run.origin.y, run.origin.z, (float) run.fx, (float) run.fz, run.boss.getId(), ids, end));
    }

    private static void send(Run run, Object msg) {
        for (ServerPlayer p : run.level.players()) {
            if (p.distanceToSqr(run.boss) > 128 * 128 && run.roleOf(p) < 0) continue;
            KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }
    }

    // ------------------------------------------------------------------ protecoes

    /** Ninguem da cena toma dano de fora dela (nem o chefe). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (RUNS.isEmpty() || event.getEntity().level().isClientSide) return;
        if (isLocked(event.getEntity())) event.setCanceled(true);
    }

    /** Servidor caiu no meio da cena: o chefe nao fica congelado para sempre. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity le)) return;
        if (le instanceof Player) return;
        CompoundTag pd = le.getPersistentData();
        if (pd.contains(LOCK) && !isLocked(le)) restoreBoss(le, pd.getCompound(LOCK));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getPersistentData().contains(PLAYER_LOCK)) restorePlayer(sp);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Run run : new ArrayList<>(RUNS.values())) finish(run, true);
        PENDING.clear();
    }
}
