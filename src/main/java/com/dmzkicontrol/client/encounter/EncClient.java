package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.client.ArmAnimDriver;
import com.dmzkicontrol.encounter.EncScript;
import com.dmzkicontrol.encounter.EncScript.Beat;
import com.dmzkicontrol.encounter.EncScript.Cue;
import com.dmzkicontrol.encounter.EncScript.Ev;
import com.dmzkicontrol.encounter.EncScript.Key;
import com.dmzkicontrol.encounter.EncScript.Kind;
import com.dmzkicontrol.encounter.EncScript.Move;
import com.dmzkicontrol.encounter.EncScript.Phase;
import com.dmzkicontrol.encounter.EncScripts;
import com.dmzkicontrol.encounter.EncounterDirector;
import com.dmzkicontrol.network.EncEventS2C;
import com.dmzkicontrol.network.EncInputC2S;
import com.dmzkicontrol.network.EncMashC2S;
import com.dmzkicontrol.network.EncStateS2C;
import com.dmzkicontrol.network.KiControlNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Cliente das lutas cinematograficas: segue o roteiro no mesmo relogio do servidor, toca poses, sons e efeitos,
 * move o proprio personagem pela arena, le as teclas e julga o proprio tempo de reacao.
 */
public final class EncClient {
    public static final class State {
        public int id;
        public EncScript script;
        public Phase phase;
        public int phaseIdx;
        public int beatIdx;
        public int variant;
        public long start;
        public Vec3 origin;
        public double fx, fz;
        public int bossId;
        public int[] players;
        public int myRole = -1;
        int lastFired = Integer.MIN_VALUE;
        long phaseStart;
        // deixas
        public final Map<Integer, byte[]> results = new HashMap<>();
        public final Map<Integer, Byte> syncs = new HashMap<>();
        public final Map<Integer, Byte> mine = new HashMap<>();
        public final Map<Integer, Long> mineAt = new HashMap<>();
        public final Map<Integer, Long> resultAt = new HashMap<>();
        // cabo de guerra / janela
        public float progress = 0.5f, progressShown = 0.5f;
        public byte[] mashRecent = new byte[0];
        public int mashResult = -1;
        public byte[] charged = new byte[4];
        // movimento local
        Vec3 anchor;
        float speed = 0.25f;
        int hopSide = 1;

        public int n() {
            return players.length;
        }

        public Beat beat() {
            Beat b = phase.beats.get(Math.min(beatIdx, phase.beats.size() - 1));
            if (variant == 1 && b.win != null) return b.win;
            if (variant == 2 && b.lose != null) return b.lose;
            return b;
        }

        public double time(float partial) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : mc.level.getGameTime() - start + partial;
        }

        public double phaseTime(float partial) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : mc.level.getGameTime() - phaseStart + partial;
        }

        public Vec3 world(double x, double y, double z) {
            return origin.add(fz * x + fx * z, y, -fx * x + fz * z);
        }

        public Vec3 local(Vec3 w) {
            Vec3 d = w.subtract(origin);
            return new Vec3(d.x * fz - d.z * fx, d.y, d.x * fx + d.z * fz);
        }

        /** Vetor da arena (sem origem) em coordenadas do mundo. */
        public Vec3 dir(double x, double y, double z) {
            return new Vec3(fz * x + fx * z, y, -fx * x + fz * z);
        }

        private long lastSearch;
        private boolean warned;

        public Entity boss() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            Entity e = mc.level.getEntity(bossId);
            if (e != null) return e;
            // o id nao bateu (outro mod trocou/escondeu a entidade): procura o chefe pelo tipo perto da arena
            long now = mc.level.getGameTime();
            if (now - lastSearch < 10) return null;
            lastSearch = now;
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(script.bossType));
            Entity best = null;
            if (type != null && origin != null) {
                for (Entity c : mc.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(origin, origin).inflate(64), x -> x.getType() == type)) {
                    if (best == null || c.distanceToSqr(origin) < best.distanceToSqr(origin)) best = c;
                }
            }
            if (best != null) {
                com.dmzkicontrol.KiControlMod.LOGGER.info("[Lutas] chefe {} achado pelo tipo (id {} -> {}).", script.bossType, bossId, best.getId());
                bossId = best.getId();
            } else if (!warned) {
                warned = true;
                com.dmzkicontrol.KiControlMod.LOGGER.warn("[Lutas] o cliente nao recebeu o chefe {} (id {}) perto de {}.", script.bossType, bossId, origin);
            }
            return best;
        }

        public Entity player(int role) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || role < 0 || role >= players.length) return null;
            return mc.level.getEntity(players[role]);
        }

        public boolean participant() {
            return myRole >= 0;
        }
    }

    private static State S;
    private static CameraType previousCamera;
    private static final List<Object[]> SCHEDULED = new ArrayList<>();
    // teclas
    private static final boolean[] WAS = new boolean[6];
    private static int mashBuffer;
    private static int chargeBuffer;

    private EncClient() {}

    public static State state() {
        return S;
    }

    /** Este cliente esta dentro de uma cena (camera, HUD e controles da luta). */
    public static boolean active() {
        return S != null && S.participant();
    }

    // ------------------------------------------------------------------ pacotes

    public static void onState(EncStateS2C m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (m.end()) {
            if (S != null && S.id == m.id()) stop();
            return;
        }
        EncScript script = EncScripts.byId(m.script());
        if (script == null || m.phase() < 0 || m.phase() >= script.phases.size()) return;
        boolean fresh = S == null || S.id != m.id();
        if (fresh) {
            if (S != null) stop();
            S = new State();
            S.id = m.id();
            S.script = script;
            S.phaseIdx = m.phase();
            S.phase = script.phases.get(m.phase());
            S.phaseStart = m.start();
            SCHEDULED.clear();
            EncFx.clear();
        }
        S.beatIdx = m.beat();
        S.variant = m.variant();
        S.start = m.start();
        S.origin = new Vec3(m.ox(), m.oy(), m.oz());
        S.fx = m.fx();
        S.fz = m.fz();
        S.bossId = m.bossId();
        S.players = m.players();
        S.lastFired = -1;
        S.results.clear();
        S.syncs.clear();
        S.mine.clear();
        S.mineAt.clear();
        S.resultAt.clear();
        S.progress = S.progressShown = 0.5f;
        S.mashResult = -1;
        S.charged = new byte[4];
        int me = -1;
        for (int i = 0; i < S.players.length; i++) if (S.players[i] == mc.player.getId()) me = i;
        S.myRole = me;
        if (fresh && me >= 0) {
            S.anchor = new Vec3(EncScript.laneX(me, S.n()), S.phase.lift, S.phase.distance);
            S.speed = 0.22f;
            S.hopSide = me % 2 == 0 ? 1 : -1;
            if (previousCamera == null) previousCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            EncHud.onPhaseStart(S);
        }
    }

    public static void onEvent(EncEventS2C m) {
        if (S == null || S.id != m.id()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        switch (m.type()) {
            case EncEventS2C.CUE -> {
                if (m.beat() != S.beatIdx || S.variant != 0) return;
                Beat beat = S.beat();
                if (m.a() < 0 || m.a() >= beat.cues.size()) return;
                Cue c = beat.cues.get(m.a());
                byte[] res = m.data();
                S.results.put(c.index, res);
                S.syncs.put(c.index, m.b());
                S.resultAt.put(c.index, now);
                for (int r = 0; r < res.length; r++) {
                    if (res[r] == EncounterDirector.RES_NONE) continue;
                    boolean hit = res[r] >= EncounterDirector.RES_HIT;
                    boolean predicted = r == S.myRole && hit && S.mine.getOrDefault(c.index, (byte) 0) >= EncounterDirector.RES_HIT;
                    for (Ev ev : hit ? c.hit : c.miss) {
                        if (predicted && ev.who == EncScript.SELF) continue;
                        SCHEDULED.add(new Object[]{now + ev.t, ev, r});
                    }
                }
                if (m.b() == 2) for (Ev ev : c.sync) SCHEDULED.add(new Object[]{now + ev.t, ev, -1});
                if (m.b() == 1) for (Ev ev : c.partial) SCHEDULED.add(new Object[]{now + ev.t, ev, -1});
                EncHud.onCueResult(S, c, res, m.b());
            }
            case EncEventS2C.MASH -> {
                S.progress = m.f();
                S.mashRecent = m.data();
            }
            case EncEventS2C.MASH_END -> {
                S.progress = m.f();
                S.mashResult = m.a();
            }
            case EncEventS2C.CHARGED -> {
                if (m.a() >= 0 && m.a() < 4) S.charged[m.a()] = m.b();
                Entity p = S.player(m.a());
                if (p != null) {
                    EncFx.spawn(m.b() == 1 ? "aura_burst" : "power_burst", p, null, 30, 1f);
                    playAt(p, m.b() == 1 ? "dragonminez:transform_on" : "dragonminez:aura_start", 1f, 1f);
                }
                EncHud.onCharged(S, m.a(), m.b());
            }
            default -> {}
        }
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (S != null && S.participant() && previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
        S = null;
        SCHEDULED.clear();
        EncFx.clear();
        EncHud.reset();
    }

    // ------------------------------------------------------------------ tick

    /** Inicio do tick (antes do jogo tratar as teclas): le e "come" os apertos de ataque. */
    public static void clientTickStart() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null) return;
        if (mc.screen != null) {
            while (mc.options.keyAttack.consumeClick()) { /* nada */ }
            return;
        }
        int attacks = 0;
        while (mc.options.keyAttack.consumeClick()) attacks++;
        while (mc.options.keyUse.consumeClick()) { /* nao usa itens no meio da cena */ }
        KeyMapping[] keys = {mc.options.keyLeft, mc.options.keyRight, mc.options.keyUp, mc.options.keyDown, mc.options.keyJump, mc.options.keyAttack};
        Key[] map = {Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN, Key.JUMP, Key.ATTACK};
        List<Key> pressed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            boolean down = keys[i].isDown();
            if (down && !WAS[i]) pressed.add(map[i]);
            WAS[i] = down;
        }
        for (int i = 0; i < attacks; i++) pressed.add(Key.ATTACK);

        Beat beat = S.beat();
        if (S.variant == 0 && beat.kind == Kind.PROMPT) {
            for (Key k : pressed) press(k);
        } else if (S.variant == 0 && beat.kind == Kind.MASH) {
            for (Key k : pressed) if (k == Key.ATTACK || k == Key.JUMP) mashBuffer++;
            if (!pressed.isEmpty()) EncHud.onMash();
        } else if (S.variant == 0 && beat.kind == Kind.CHARGE) {
            KeyMapping ki = dmzKey("key.dragonminez.ki_charge");
            if ((ki != null && ki.isDown()) || mc.options.keyAttack.isDown()) chargeBuffer++;
        }
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (S == null) return;
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        int t = (int) (now - S.start);
        Beat beat = S.beat();
        // eventos do roteiro e avisos das deixas
        for (int tt = Math.max(S.lastFired + 1, 0); tt <= t; tt++) {
            for (Ev ev : beat.evs) if (ev.t == tt) fire(ev, -1);
            if (beat.kind == Kind.PROMPT && S.variant == 0) {
                for (Cue c : beat.cues) {
                    for (Ev ev : c.tell) {
                        if (c.t + ev.t != tt) continue;
                        int r = c.role(S.n());
                        if (r >= 0) fire(ev, r);
                        else for (int i = 0; i < S.n(); i++) fire(ev, i);
                    }
                }
            }
        }
        S.lastFired = Math.max(S.lastFired, t);
        Iterator<Object[]> it = SCHEDULED.iterator();
        List<Object[]> due = new ArrayList<>();
        while (it.hasNext()) {
            Object[] s = it.next();
            if ((long) s[0] <= now) {
                due.add(s);
                it.remove();
            }
        }
        for (Object[] s : due) fire((Ev) s[1], (int) s[2]);

        if (S.participant()) {
            // deixa perdida: avisa o servidor logo (nao espera a folga)
            if (S.variant == 0 && beat.kind == Kind.PROMPT) {
                for (Cue c : beat.cues) {
                    if (!c.involves(S.myRole, S.n()) || S.mine.containsKey(c.index)) continue;
                    if (t > c.t + window(c)) answer(c, EncounterDirector.RES_MISS, t - c.t, now);
                }
            }
            if (t % 2 == 0 && mashBuffer > 0 && beat.kind == Kind.MASH) {
                KiControlNetwork.CHANNEL.sendToServer(new EncMashC2S(S.id, S.beatIdx, mashBuffer));
                mashBuffer = 0;
            }
            if (t % 4 == 0 && chargeBuffer > 0 && beat.kind == Kind.CHARGE) {
                KiControlNetwork.CHANNEL.sendToServer(new EncMashC2S(S.id, S.beatIdx, chargeBuffer));
                chargeBuffer = 0;
            }
            moveLocal(mc.player);
        }
        S.progressShown += (S.progress - S.progressShown) * 0.35f;
        EncFx.tick(S);
        EncHud.tick(S);
    }

    // ------------------------------------------------------------------ teclas e julgamento

    public static int window(Cue c) {
        return Math.max(2, Math.round(c.win * (float) com.dmzkicontrol.config.KiControlConfig.get().encounters.windowScale));
    }

    /** Tecla que esta deixa pede para este jogador (DODGE vira esquerda/direita conforme a faixa). */
    public static Key need(Cue c) {
        if (c.key != Key.DODGE) return c.key;
        double x = S.anchor == null ? 0 : S.anchor.x;
        if (x > 0.3) return Key.LEFT;
        if (x < -0.3) return Key.RIGHT;
        return S.hopSide > 0 ? Key.LEFT : Key.RIGHT;
    }

    private static void press(Key k) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level.getGameTime();
        double t = now - S.start + 0.5;
        Cue best = null;
        for (Cue c : S.beat().cues) {
            if (!c.involves(S.myRole, S.n()) || S.mine.containsKey(c.index)) continue;
            int w = window(c);
            if (t < c.t - w - 3 || t > c.t + w) continue;
            if (best == null || c.t < best.t) best = c;
        }
        if (best == null) return;
        int w = window(best);
        double dt = t - best.t;
        byte res;
        if (k != need(best) || dt < -w) res = EncounterDirector.RES_MISS;
        else res = Math.abs(dt) <= Math.max(1.0, w * 0.4) ? EncounterDirector.RES_PERFECT : EncounterDirector.RES_HIT;
        answer(best, res, (float) dt, now);
    }

    private static void answer(Cue c, byte res, float dt, long now) {
        S.mine.put(c.index, res);
        S.mineAt.put(c.index, now);
        KiControlNetwork.CHANNEL.sendToServer(new EncInputC2S(S.id, S.beatIdx, c.index, res, dt));
        EncHud.onLocalAnswer(S, c, res);
        if (res >= EncounterDirector.RES_HIT) {
            // previsao: o proprio personagem reage na hora
            for (Ev ev : c.hit) {
                if (ev.who != EncScript.SELF) continue;
                if (ev.t == 0) fire(ev, S.myRole);
                else SCHEDULED.add(new Object[]{now + ev.t, ev, S.myRole});
            }
            Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, res == EncounterDirector.RES_PERFECT ? 1.8f : 1.4f);
        } else {
            Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ eventos

    private static void fire(Ev ev, int self) {
        if (S == null) return;
        int n = S.n();
        if (n < ev.minN || n > ev.maxN) return;
        List<Integer> tg = targets(ev.who, self, n);
        if (tg.isEmpty()) return;
        boolean once = ev.who == EncScript.ALL || ev.who == EncScript.OTHERS;
        boolean mine = tg.contains(S.myRole);
        // sem falas nem onomatopeias na tela: so a acao (e as teclas)
        if (S.participant() && ev.impact > 0) EncHud.impact(ev.impact);
        if (S.participant() && ev.shake > 0) EncHud.shake(ev.shake);
        boolean soundDone = false;
        for (int role : tg) {
            Entity e = role < 0 ? S.boss() : S.player(role);
            if (ev.pose != null && e instanceof AbstractClientPlayer acp) {
                String pose = ev.pose;
                if ("dodge".equals(pose)) {
                    double x = role == S.myRole && S.anchor != null ? S.anchor.x : S.local(acp.position()).x;
                    pose = x > 0.3 || (Math.abs(x) <= 0.3 && role % 2 == 0) ? "base.dodge_left" : "base.dodge_right";
                }
                // nome com ponto = animacao do proprio DMZ (base., combat., ki., skp., evs.); senao, pose nossa
                ArmAnimDriver.play(acp, pose.contains(".") ? pose : "dmzkicontrol.cs." + pose, ev.poseTicks);
            }
            if (ev.snd != null && e != null && !(once && soundDone)) {
                playAt(e, ev.snd, ev.vol, ev.pitch);
                soundDone = true;
            }
            if (ev.fx != null) {
                if (ev.fxTo == Integer.MIN_VALUE) EncFx.spawn(ev.fx, e, null, ev.fxDur, ev.fxSize);
                else for (int d : targets(ev.fxTo, self, n)) EncFx.spawn(ev.fx, e, d < 0 ? S.boss() : S.player(d), ev.fxDur, ev.fxSize);
            }
            if (ev.move != null && role >= 0 && role == S.myRole) moveMine(ev.move, self);
        }
        if (mine && ev.hurt > 0) EncHud.hurtFlash();
    }

    private static List<Integer> targets(int who, int self, int n) {
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

    public static Component nameOf(int role) {
        if (S == null) return Component.empty();
        if (role < 0) {
            Entity b = S.boss();
            if (b != null) return b.getDisplayName();
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(S.script.bossType));
            return type != null ? type.getDescription() : Component.literal("?");
        }
        Entity p = S.player(role);
        return p != null ? p.getDisplayName() : Component.literal("?");
    }

    private static void moveMine(Move m, int self) {
        if (S.anchor == null) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 a = S.anchor;
        switch (m.kind()) {
            case LANE -> a = new Vec3(EncScript.laneX(S.myRole, S.n()) + m.x(), S.phase.lift + m.y(), S.phase.distance + m.z());
            case MIRROR -> {
                if (Math.abs(a.x) > 0.3) a = new Vec3(-a.x, a.y, a.z);
                else {
                    a = new Vec3(S.hopSide > 0 ? -2.4 : 2.4, a.y, a.z);
                }
                S.hopSide = -S.hopSide;
            }
            case HOP -> {
                double side = a.x > 0.3 ? -1 : a.x < -0.3 ? 1 : -S.hopSide;
                a = a.add(side * m.x(), 0, 0);
            }
            case OFFSET -> a = a.add(m.x(), m.y(), m.z());
            case TO_ROLE -> {
                int ref = m.ref() == EncScript.SELF ? self : m.ref();
                Entity p = S.player(ref);
                if (p != null && mc.player != null && p != mc.player) a = S.local(p.position()).add(m.x(), m.y(), m.z());
            }
            case ABS -> a = new Vec3(m.x(), m.y(), m.z());
            case LUNGE -> {
                Entity b = S.boss();
                Vec3 bl = b != null ? S.local(b.position()) : new Vec3(0, a.y, 0);
                Vec3 d = new Vec3(bl.x - a.x, 0, bl.z - a.z);
                if (d.lengthSqr() > 1e-4) a = a.add(d.normalize().scale(m.x()));
            }
            case CLOSE -> {
                Entity b = S.boss();
                Vec3 bl = b != null ? S.local(b.position()) : new Vec3(0, S.phase.lift, 0);
                double reach = 0.8 + (b != null ? b.getBbWidth() * 0.5 : 0.3);
                double ang = Math.toRadians(((S.n() - 1) / 2.0 - S.myRole) * 40.0);
                a = bl.add(Math.sin(ang) * reach + m.x(), m.y(), Math.cos(ang) * reach + m.z());
            }
        }
        S.anchor = a;
        S.speed = m.speed();
    }

    private static void moveLocal(LocalPlayer lp) {
        if (S.anchor == null) return;
        Vec3 want = S.world(S.anchor.x, S.anchor.y, S.anchor.z);
        Vec3 d = want.subtract(lp.position());
        Vec3 v;
        if (S.speed < 0) {
            double step = -S.speed;
            v = d.length() <= step ? d : d.normalize().scale(step);
        } else {
            v = d.scale(Math.min(1f, S.speed));
        }
        if (v.length() > 3.0) v = v.normalize().scale(3.0);
        lp.setDeltaMovement(v);
        lp.fallDistance = 0;
        faceBoss(lp);
    }

    /** O personagem sempre encara o chefe (durante a cena o mouse nao gira o corpo). */
    public static void faceBoss(LocalPlayer lp) {
        if (!active()) return;
        Entity b = S.boss();
        Vec3 target = b != null ? b.position().add(0, b.getBbHeight() * 0.7, 0) : S.world(0, 1.5, 0);
        Vec3 d = target.subtract(lp.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        lp.setYRot(yaw);
        lp.yRotO = yaw;
        lp.setXRot(pitch);
        lp.xRotO = pitch;
        lp.yHeadRot = yaw;
        lp.yBodyRot = yaw;
    }

    public static void renderTickLock() {
        Minecraft mc = Minecraft.getInstance();
        if (active() && mc.player != null) faceBoss(mc.player);
    }

    // ------------------------------------------------------------------ utilidades

    public static void playAt(Entity e, String id, float vol, float pitch) {
        try {
            SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
            if (ev == null) return;
            e.level().playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), ev, SoundSource.PLAYERS, vol, pitch, false);
        } catch (Exception ignored) {
        }
    }

    private static final Map<String, KeyMapping> DMZ_KEYS = new HashMap<>();

    /** Tecla do DMZ pelo nome (ex.: "key.dragonminez.action_key"), para mostrar e ler. */
    public static KeyMapping dmzKey(String name) {
        return DMZ_KEYS.computeIfAbsent(name, k -> {
            for (KeyMapping km : Minecraft.getInstance().options.keyMappings) if (km.getName().equals(k)) return km;
            return null;
        });
    }

    public static Entity bossEntity() {
        return S == null ? null : S.boss();
    }

    public static float bossHeight() {
        Entity b = bossEntity();
        return b instanceof LivingEntity le ? le.getBbHeight() : 2f;
    }
}
