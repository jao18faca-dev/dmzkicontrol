package com.dmzkicontrol.encounter;

import com.dmzkicontrol.encounter.EncScript.Beat;
import com.dmzkicontrol.encounter.EncScript.Cue;
import com.dmzkicontrol.encounter.EncScript.Key;
import com.dmzkicontrol.encounter.EncScript.Kind;
import com.dmzkicontrol.encounter.EncScript.Mode;
import com.dmzkicontrol.encounter.EncScript.MoveKind;
import com.dmzkicontrol.encounter.EncScript.Phase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.dmzkicontrol.encounter.EncScript.ALL;
import static com.dmzkicontrol.encounter.EncScript.BOSS;
import static com.dmzkicontrol.encounter.EncScript.OTHERS;
import static com.dmzkicontrol.encounter.EncScript.SELF;

/**
 * Os roteiros das lutas. Tempos em ticks (20 = 1 segundo). Tudo se adapta de 1 a 4 jogadores: faixas lado a lado,
 * deixas para cada um, combos em rodizio, golpe final todos juntos, e eventos que so existem com N jogadores.
 */
public final class EncScripts {
    private static final Map<String, EncScript> BY_ID = new LinkedHashMap<>();
    private static final Map<String, EncScript> BY_BOSS = new LinkedHashMap<>();

    private EncScripts() {}

    private static void add(EncScript s) {
        BY_ID.put(s.id, s);
        BY_BOSS.put(s.bossType, s);
    }

    public static EncScript byId(String id) {
        return BY_ID.get(id);
    }

    public static EncScript forBoss(String entityType) {
        return BY_BOSS.get(entityType);
    }

    public static Map<String, EncScript> all() {
        return Collections.unmodifiableMap(BY_ID);
    }

    // ------------------------------------------------------------------ atalhos

    private static String d(String s) {
        return "dragonminez:" + s;
    }

    private static final String ZANZOKEN = d("zanzoken");
    /** Reacoes do chefe (animacoes de dano do proprio DMZ). */
    private static final String[] HURTS = {"hurt_left", "hurt_right", "hurt_top", "hurt_right", "hurt_left", "hurt_top2"};
    private static final String[] PUNCH = {d("punch1"), d("punch2"), d("punch3"), d("punch4"), d("punch5"), d("punch6")};
    /**
     * Golpes do combo, montados e medidos no Blockbench com o Cell (DMZ) na frente do jogador, no estilo da luta
     * Goku/Gohan x Cell: arrancada voando, soco no estomago com o corpo todo, chute voador, gancho que lanca.
     * reach = distancia centro-a-centro (blocos) em que o punho/pe encosta; contact = tick do contato
     * (2 de transicao do GeckoLib + o quadro do golpe). Cada golpe segura a pose no contato (o "congelamento").
     */
    private record Strike(String pose, double reach, int contact) {}
    private static final Strike[] COMBO = {
            new Strike("dbz_dash_punch", 1.48, 5),
            new Strike("dbz_gut_punch", 1.74, 6),
            new Strike("dbz_flying_kick", 1.34, 5),
            new Strike("dbz_rising_uppercut", 1.24, 6),
            new Strike("base.jab_left", 1.38, 5),
            new Strike("dbz_dash_punch", 1.48, 5),
            new Strike("combat.gutkick_right", 1.36, 6),
            new Strike("dbz_gut_punch", 1.74, 6)};
    /** Martelo duplo do golpe final: mergulha ate ficar 1,44 bloco na frente e 1,38 acima do chefe. */
    private static final double HAMMER_REACH = 1.44, HAMMER_UP = 1.38;
    private static final int HAMMER_CONTACT = 6;

    /** Esquiva de zanzoken (padrao de todas as deixas de desvio). */
    private static void dodge(Cue c, MoveKind kind) {
        c.onHit(0, SELF).pose("dodge", 9).move(kind, kind == MoveKind.HOP ? 2.2 : 0, 0, 0, kind == MoveKind.HOP ? 1f : 0.55f)
                .fx("afterimage", 12).snd(ZANZOKEN, 0.9f, 1.1f);
    }

    /** Golpe do combo em rodizio. */
    private static Cue comboHit(Beat b, int i, int t, Key key, int win, float dmg) {
        Cue c = b.cue(t, Mode.TURN, key, win).lead(12).dmg(dmg).hurt(0.03f);
        Strike st = COMBO[i % COMBO.length];
        boolean heavy = i % 3 == 2;
        // entra ja na distancia exata do golpe: o punho encosta no chefe em vez de parar no ar
        c.onHit(0, SELF).pose(st.pose(), 9).move(MoveKind.CLOSE, st.reach(), 0, 0, 0.75f);
        // reacao no instante do contato (nao antes: empurrar o chefe antes do soco chegar fazia errar)
        c.onHit(st.contact(), BOSS).fx("impact_star", heavy ? 9 : 7).size(heavy ? 1.3f : 1f).impact(heavy ? 2 : 1).shake(heavy ? 1.6f : 0.7f)
                .snd(PUNCH[i % PUNCH.length], 1f, 0.9f + (i % 4) * 0.06f)
                .boss(HURTS[i % HURTS.length]).shove(heavy ? 1.0f : 0.3f);
        // errou: o chefe defende e devolve
        c.onMiss(0, BOSS).boss("attack").snd(d("block2"));
        c.onMiss(2, SELF).pose("hit_recoil", 10).impact(1).shake(1f).snd(PUNCH[(i + 3) % PUNCH.length], 1f, 0.7f);
        return c;
    }

    // ------------------------------------------------------------------ combo em equipe

    /** Golpe de um papel fixo do combo em equipe (o punho encosta e o chefe reage no contato). */
    private static void strike(Cue c, Strike st, int i, boolean heavy) {
        c.onHit(0, SELF).pose(st.pose(), 9).move(MoveKind.CLOSE, st.reach(), 0, 0, 0.8f);
        c.onHit(st.contact(), BOSS).fx("impact_star", heavy ? 9 : 7).size(heavy ? 1.3f : 1f).impact(heavy ? 2 : 1).shake(heavy ? 1.6f : 0.7f)
                .snd(PUNCH[i % PUNCH.length], 1f, 0.9f + (i % 4) * 0.06f).boss(HURTS[i % HURTS.length]).shove(heavy ? 0.6f : 0.25f);
        c.onMiss(0, BOSS).boss("attack").snd(d("block2"));
        c.onMiss(2, SELF).pose("hit_recoil", 10).impact(1).shake(1f);
    }

    /**
     * Uma rodada de combo em equipe (66 ticks). Cada jogador tem um papel diferente, ao mesmo tempo:
     * <ul>
     * <li>1o: arrancada e soco no estomago de frente;</li>
     * <li>2o: some num zanzoken, aparece nas costas do chefe e solta um Kamehameha que joga o chefe de volta
     * no 1o (sozinho, o proprio 1o faz isso);</li>
     * <li>1o de novo: recebe o chefe com o gancho que lanca;</li>
     * <li>3o (com 3 ou 4): pega o chefe no ar com o chute voador;</li>
     * <li>o ultimo (sozinho: o 1o; com 2 ou 3: o 2o; com 4: o 4o): cai de cima com o martelo duplo.</li>
     * </ul>
     * A camera acompanha cada parte: de lado no soco, por tras do chefe no Kamehameha, de baixo no gancho,
     * do alto no chute e de cima no martelo.
     *
     * @param lift altura do chefe na arena nesta fase
     * @param dist distancia da fase (para todos voltarem a ~3,5 blocos do chefe no fim)
     */
    private static void teamRound(Beat q, int t, double lift, double dist, int i) {
        q.shot(t, 0, "mid", 3.4, 1.3, 1.2, "boss", 0, 1.5, 0, 0.95f, 3f)
                .shot(t + 14, 0, "boss", 2.6, 1.4, -3.2, "boss", 0, 1.4, 0, 0.95f, -5f)
                .shot(t + 30, 0, "boss", 1.1, -0.3, 2.6, "boss", 0, 2.4, 0, 0.9f, -7f)
                .shot(t + 42, 6, "boss", 3.4, 2.0, 1.6, "boss", 0, 0.8, 0, 1.0f, 6f)
                .shot(t + 54, 0, "boss", 0.6, 6.5, 1.8, "boss", 0, -1.5, 0, 1.05f);

        // 1) o primeiro, de frente
        Cue a = q.cue(t, Mode.EACH, Key.ATTACK, 4).only(0).lead(12).dmg(0.004f).hurt(0.03f);
        strike(a, COMBO[0], i, false);
        Cue a2 = q.cue(t + 9, Mode.EACH, Key.ATTACK, 4).only(0).lead(8).dmg(0.004f).hurt(0.03f);
        strike(a2, COMBO[1], i + 1, true);

        // 2) Kamehameha nas costas: o 2o (ou o 1o, sozinho)
        int[][] behind = {{1, 2, 4}, {0, 1, 1}};
        for (int[] w : behind) {
            Cue k = q.cue(t + 24, Mode.EACH, Key.UP, 5).only(w[0]).n(w[1], w[2]).lead(14).dmg(0.006f).hurt(0.03f);
            k.tell(-14, SELF).move(MoveKind.ABS, 0.5, lift + 0.2, -2.0, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.15f).pose("ki.kameha_cast", 14);
            k.tell(-12, SELF).fx("kame_charge", 12).size(0.8f);
            k.onHit(0, SELF).pose("ki.kameha_fire", 12).fx("dmz_beam", 12, BOSS).size(0.85f).snd(d("ki_kame_fire"), 0.9f, 1.2f).impact(2).shake(1.8f);
            k.onHit(3, BOSS).boss("hurt_down").move(MoveKind.ABS, 0, lift, 1.1, 0.5f).fx("impact_star", 8).size(1.2f);
            k.onMiss(0, SELF).pose("hit_recoil", 10).impact(1);
        }

        // 3) o 1o recebe com o gancho que lanca
        Cue up = q.cue(t + 33, Mode.EACH, Key.JUMP, 5).only(0).lead(10).dmg(0.006f).hurt(0.03f);
        up.onHit(0, SELF).pose("dbz_rising_uppercut", 14).move(MoveKind.CLOSE, 1.24, 0, 0, 0.95f).fx("afterimage", 8);
        up.onHit(6, BOSS).fx("impact_star", 10).size(1.5f).impact(2).shake(2.2f).boss("hurt_top").move(MoveKind.ABS, 0, lift + 3.4, 0.6, 0.35f)
                .snd(d("critic_punch2"), 1f, 1.0f);
        up.onMiss(0, SELF).pose("hit_recoil", 10);

        // 4) o 3o pega no ar com o chute voador
        Cue kick = q.cue(t + 45, Mode.EACH, Key.RIGHT, 5).only(2).n(3, 4).lead(12).dmg(0.006f).hurt(0.03f);
        kick.onHit(0, SELF).pose("dbz_flying_kick", 14).move(MoveKind.CLOSE, 1.34, 0, 0, 0.95f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.2f);
        kick.onHit(5, BOSS).fx("impact_star", 10).size(1.4f).impact(2).shake(2f).boss("hurt_left").move(MoveKind.ABS, 1.8, lift + 3.8, 0.4, 0.35f)
                .snd(d("critic_punch1"), 1f, 1.1f);
        kick.onMiss(0, SELF).pose("hit_recoil", 10);

        // 5) martelo duplo de cima: o ultimo da fila
        int[][] hammer = {{0, 1, 1}, {1, 2, 3}, {3, 4, 4}};
        for (int[] w : hammer) {
            Cue h = q.cue(t + 56, Mode.EACH, Key.DOWN, 5).only(w[0]).n(w[1], w[2]).lead(12).dmg(0.008f).hurt(0.03f);
            h.tell(-12, SELF).move(MoveKind.OFFSET, 0, 2.6, 0, 0.3f).pose("base.fly_idle", 12);
            h.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.85f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.3f);
            h.onHit(HAMMER_CONTACT, BOSS).fx("impact_star", 12).size(1.7f).impact(3).shake(3.5f).boss("hurt_down").move(MoveKind.ABS, 0, lift, 0, 0.55f)
                    .snd(d("critic_punch2"), 1.1f, 0.8f);
            h.onHit(HAMMER_CONTACT + 5, BOSS).fx("shockwave", 14).size(1.1f);
            h.onHit(14, ALL).move(MoveKind.LANE, 0, 0, 3.5 - dist, 0.3f).pose("dbz_guard", 16);
            h.onMiss(0, BOSS).move(MoveKind.ABS, 0, lift, 0, 0.3f);
            h.onMiss(4, ALL).move(MoveKind.LANE, 0, 0, 3.5 - dist, 0.3f);
        }
    }

    // ------------------------------------------------------------------ Freeza (forma final): o momento do Kuririn

    private static EncScript friezaBase() {
        EncScript s = new EncScript("frieza_base", d("saga_frieza_base"));
        Phase p = s.phase(0.50f, "enc.dmzkicontrol.title.frieza_base").distance(7).lift(0.5);

        Beat b = p.beat(Kind.CINE, 120);
        b.shot(0, 0, "boss", 0.8, 1.8, 2.4, "boss", 0, 1.7, 0, 0.75f)
                .shot(34, 0, "group", 0, 1.4, 3.2, "boss", 0, 14, 4, 0.95f)
                .shot(70, 0, "group", 1.6, 1.5, -1.2, "group", 0, 1.5, 0, 0.8f)
                .shot(96, 12, "group", 0, 1.2, 2.6, "group", 0, 1.4, 0, 0.7f);
        b.ev(2, BOSS).snd(d("entity.frieza.s.ambient"));
        b.ev(6, BOSS).text("enc.dmzkicontrol.frieza.kuririn_line");
        b.ev(20, BOSS).fx("telekinesis", 40).snd(d("ki_explosion_charge"), 0.7f, 1.4f);
        b.ev(20, ALL).pose("tremble", 40);
        b.ev(56, BOSS).fx("sky_explosion", 40).size(1.6f).impact(2).shake(2.5f).snd(d("ki_explosion_impact"), 1.2f, 0.8f);
        b.ev(72, ALL).text("enc.dmzkicontrol.kuririn").pose("evs.rage_scream", 24);
        b.ev(96, ALL).pose("evs.rage_scream", 24).fx("rage", 24).impact(2).shake(3f).snd(d("aura_start"), 1f, 0.7f);

        Beat w = p.beat(Kind.CHARGE, 200).caption("enc.dmzkicontrol.cap.transform");
        w.shot(0, 0, "group", 3.2, 1.0, 3.2, "group", 0, 1.3, 0, 0.85f)
                .shot(100, 100, "group", -3.2, 1.6, 3.0, "group", 0, 1.3, 0, 0.8f);
        w.ev(0, ALL).pose("base.ki_charge", 200).fx("rage", 200).snd(d("ki_charge_loop"), 0.8f, 0.8f);

        Beat e = p.beat(Kind.CINE, 50);
        e.shot(0, 0, "boss", 0.7, 1.7, 2.2, "boss", 0, 1.6, 0, 0.7f)
                .shot(26, 10, "boss", -1.5, 0.6, 3.6, "group", 0, 1.6, 0, 0.9f);
        e.ev(4, BOSS).text("enc.dmzkicontrol.frieza.what_power");
        e.ev(26, ALL).pose("base.fly_idle", 24);
        return s;
    }

    // ------------------------------------------------------------------ Freeza 100%: Kienzan, Raio da Morte, cabo de guerra

    private static EncScript friezaFullPower() {
        EncScript s = new EncScript("frieza_fp", d("saga_frieza_fp"));

        // ---------- fase 1: Kienzan
        Phase p = s.phase(0.70f, "enc.dmzkicontrol.title.kienzan").distance(8).lift(1.5);
        Beat b = p.beat(Kind.CINE, 60);
        b.shot(0, 0, "boss", 0.9, 1.9, 2.6, "boss", 0, 1.7, 0, 0.75f)
                .shot(32, 6, "group", 2.2, 2.0, 4.5, "boss", 0, 2.6, 0, 0.9f);
        b.ev(2, BOSS).text("enc.dmzkicontrol.frieza.kienzan_line").snd(d("entity.frieza.s.attack"));
        b.ev(6, BOSS).fx("kienzan_charge", 54).size(1.4f).snd(d("ki_disk_charge"));
        b.ev(10, ALL).pose("base.block", 50);
        b.ev(30, BOSS).move(MoveKind.ABS, 0, 2.5, 0, 0.15f);

        Beat q = p.beat(Kind.PROMPT, 128).caption("enc.dmzkicontrol.cap.dodge");
        q.shot(0, 0, "group", 0, 2.4, 6, "mid", 0, 1.5, 0, 1.0f)
                .shot(56, 10, "mid", 7.5, 2.0, 0, "mid", 0, 1.5, 0, 0.95f)
                .shot(84, 8, "group", -2, 1.6, 4, "boss", 0, 2.2, 0, 0.9f);
        Cue c1 = q.cue(30, Mode.EACH, Key.DODGE, 5).lead(22).hurt(0.06f);
        c1.tell(-20, BOSS).fx("kienzan", 20, SELF).snd(d("ki_disk_fire")).boss("attack");
        dodge(c1, MoveKind.MIRROR);
        c1.onMiss(0, SELF).pose("hit_recoil", 12).fx("cut", 8).impact(1).shake(1.5f).snd(d("critic_punch1"));
        Cue c2 = q.cue(64, Mode.EACH, Key.UP, 5).lead(20).hurt(0.06f);
        c2.tell(-18, BOSS).fx("kienzan_low", 18, SELF).snd(d("ki_disk_fire"), 1f, 1.2f).boss("attack");
        c2.onHit(0, SELF).pose("float_spread", 10).move(MoveKind.OFFSET, 0, 1.8, 0, 0.7f).fx("afterimage", 10).snd(ZANZOKEN);
        c2.onHit(12, SELF).move(MoveKind.OFFSET, 0, -1.8, 0, 0.3f);
        c2.onMiss(0, SELF).pose("hit_recoil", 12).fx("cut", 8).impact(1).shake(1.5f).snd(d("critic_punch1"));
        Cue c3 = q.cue(100, Mode.ALL, Key.ATTACK, 6).lead(24).syncDmg(0.08f).hurt(0.08f);
        c3.tell(-22, BOSS).fx("kienzan", 22, SELF).size(1.3f).snd(d("ki_disk_fire"), 1f, 0.8f).boss("attack");
        c3.onHit(0, SELF).pose("combat.one_handed_uppercut_right", 10).snd(d("parry"));
        c3.onSync(0, BOSS).fx("kienzan_return", 10).snd(d("ki_disk_fire"), 1f, 1.4f);
        c3.onSync(10, BOSS).impact(3).shake(3f).snd(d("critic_punch2")).fx("burst", 14).size(1.5f).boss("hurt_top").shove(1.2f);
        c3.onSync(12, BOSS).text("enc.dmzkicontrol.frieza.kienzan_cut");
        c3.onPartial(0, BOSS).fx("kienzan_return", 10);
        c3.onPartial(10, BOSS).impact(2).shake(2f).snd(d("critic_punch1")).boss("hurt_left").shove(0.6f);
        c3.onMiss(0, SELF).pose("hit_recoil", 14).fx("cut", 10).impact(2).shake(2.5f).snd(d("critic_punch2"));

        Beat a = p.beat(Kind.CINE, 36);
        a.shot(0, 0, "boss", 0.7, 1.7, 2.3, "boss", 0, 1.6, 0, 0.72f);
        a.ev(0, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 30);
        a.ev(2, BOSS).text("enc.dmzkicontrol.frieza.angry").snd(d("entity.frieza.s.hurt")).move(MoveKind.ABS, 0, 0, 0, 0.2f);

        // ---------- fase 2: Raio da Morte + contra-ataque
        p = s.phase(0.45f, "enc.dmzkicontrol.title.deathbeam").distance(9).lift(1.5);
        b = p.beat(Kind.CINE, 44);
        b.shot(0, 0, "boss", 1.0, 1.6, 1.8, "boss", 0, 1.5, 0, 0.6f)
                .shot(24, 0, "me", 0.9, 1.9, 2.4, "boss", 0, 1.5, 0, 0.85f);
        b.ev(0, BOSS).text("enc.dmzkicontrol.frieza.deathbeam_line").fx("finger_glow", 44);
        b.ev(24, ALL).pose("base.block", 20);
        b.ev(36, BOSS).snd(d("laserbeam"), 0.6f, 1.6f);

        q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.deflect");
        q.shot(0, 0, "me", 0.9, 1.9, 2.6, "boss", 0, 1.5, 0, 0.9f)
                .shot(70, 12, "mid", 8, 1.4, 0.5, "mid", 0, 1.4, 0, 1.0f);
        int[] beamT = {24, 46, 66, 84, 100, 116, 130};
        Key[] beamK = {Key.ATTACK, Key.ATTACK, Key.DODGE, Key.ATTACK, Key.DODGE, Key.ATTACK, Key.ATTACK};
        for (int i = 0; i < beamT.length; i++) {
            Cue c = q.cue(beamT[i], Mode.TURN, beamK[i], i < 3 ? 5 : 4).lead(16).hurt(0.05f);
            c.tell(-5, BOSS).fx("deathbeam", 5, SELF).snd(d("laserbeam"), 0.9f, 1.5f).boss("attack");
            if (beamK[i] == Key.DODGE) {
                dodge(c, MoveKind.HOP);
                c.onHit(10, SELF).move(MoveKind.LANE, 0, 0, 0, 0.4f);
            } else {
                c.onHit(0, SELF).pose("combat.one_handed_uppercut_right", 9).fx("deflect_spark", 8).snd(d("parry"), 1f, 1f + i * 0.05f);
            }
            c.onMiss(0, SELF).pose("hit_recoil", 12).fx("burst", 8).size(0.5f).impact(1).shake(1.5f).snd(d("critic_punch1"));
        }

        Beat dash = p.beat(Kind.CINE, 26);
        dash.shot(0, 0, "mid", 5, 1.4, 1, "mid", 0, 1.4, 0, 1.0f);
        dash.ev(0, BOSS).move(MoveKind.ABS, 0, 1.5, 5.0, 0.4f).fx("afterimage", 10).snd(ZANZOKEN, 1f, 0.8f);
        dash.ev(12, ALL).move(MoveKind.CLOSE, 0, 0, 0, 0.45f);
        dash.ev(6, BOSS).text("enc.dmzkicontrol.frieza.come_here");
        dash.ev(8, ALL).pose("base.fly_idle", 18).text("enc.dmzkicontrol.cap.counter").impact(1);

        q = p.beat(Kind.PROMPT, 176).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.6, 0.4, "mid", 0, 1.4, 0, 0.95f)
                .shot(28, 8, "mid", -3.4, 2.4, 1.2, "boss", 0, 1.5, 0, 0.9f)
                // close nos olhos antes do golpe final, depois camera baixa e torta para o martelo
                .shot(128, 0, "me", 0.22, 1.66, -0.8, "me", 0, 1.62, 0, 0.5f, -6f)
                .shot(138, 0, "mid", 4.2, 0.2, 1.2, "boss", 0, 2.0, 0, 1.05f, 9f);
        // abre em rodizio e vira combo em equipe (cada um com um papel)
        Key[] combo = {Key.ATTACK, Key.JUMP, Key.LEFT, Key.ATTACK};
        for (int i = 0; i < combo.length; i++) comboHit(q, i, 12 + 10 * i, combo[i], 4, 0.012f);
        teamRound(q, 54, 1.5, 9, 2);
        Cue fin = q.cue(146, Mode.ALL, Key.ATTACK, 5).lead(20).syncDmg(0.09f).hurt(0.04f);
        fin.tell(-18, SELF).move(MoveKind.OFFSET, 0, 2.4, -0.6, 0.35f).pose("base.fly_idle", 18);
        fin.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.8f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.3f);
        fin.onHit(HAMMER_CONTACT, SELF).snd(d("critic_punch2"), 1f, 0.8f);
        fin.onSync(HAMMER_CONTACT, BOSS).fx("impact_star", 12).size(1.8f).impact(3).shake(4f).move(MoveKind.ABS, 0, 0, -4.0, 0.55f).snd(d("ki_explosion_impact")).boss("hurt_down");
        fin.onSync(HAMMER_CONTACT + 6, BOSS).fx("crater", 30).size(1.4f);
        fin.onPartial(HAMMER_CONTACT, BOSS).fx("impact_star", 9).size(1.3f).impact(2).shake(2.5f).move(MoveKind.ABS, 0, 0.5, -2.5, 0.4f).boss("hurt_top");
        fin.onHit(14, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
        fin.onMiss(0, SELF).pose("hit_recoil", 14).impact(1).snd(d("block3"));
        fin.onMiss(14, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);

        a = p.beat(Kind.CINE, 44);
        a.shot(0, 0, "boss", 1.2, 0.6, -2.6, "boss", 0, 1.0, 0, 0.8f)
                .shot(22, 10, "group", 0, 2.2, 4, "boss", 0, 1.2, 0, 0.95f);
        a.ev(4, BOSS).text("enc.dmzkicontrol.frieza.impossible").snd(d("entity.frieza.s.hurt"));
        a.ev(20, BOSS).move(MoveKind.ABS, 0, 1.5, 0, 0.12f);

        // ---------- fase 3: cabo de guerra de energia
        p = s.phase(0.20f, "enc.dmzkicontrol.title.final_clash").distance(11).lift(2.0);
        b = p.beat(Kind.CINE, 76);
        b.shot(0, 0, "group", 2.4, 0.6, 2.6, "group", 0, 1.6, 0, 0.85f)
                .shot(40, 0, "boss", 1.2, 1.4, 2.6, "boss", 0, 2.2, 0, 0.8f)
                .shot(58, 8, "mid", 10, 0.6, 0, "mid", 0, 1.6, 0, 1.0f);
        b.ev(0, ALL).pose("ki.kameha_cast", 76).fx("kame_charge", 76).snd(d("ki_kame_charge"));
        b.ev(40, BOSS).fx("boss_charge", 36).size(1.3f).snd(d("ki_explosion_charge")).text("enc.dmzkicontrol.frieza.die_line").boss("charge_on");

        Beat m = p.beat(Kind.MASH, 240).caption("enc.dmzkicontrol.cap.mash").tug(0.010f, 0.040f).surge(90, 118).surge(170, 196);
        m.shot(0, 0, "mid", 10, 1.2, 0, "mid", 0, 1.6, 0, 1.0f)
                .shot(110, 30, "group", 2.0, 1.4, 4.6, "mid", 0, 1.6, 0, 0.95f)
                .shot(180, 20, "boss", -1.6, 1.8, -3, "mid", 0, 1.6, 0, 0.9f);
        m.ev(0, ALL).pose("ki.kameha_fire", 240).impact(2).shake(2f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("clash", 240).snd(d("ki_beam_fire"), 1f, 0.8f).boss("charge_off");
        m.ev(90, BOSS).text("enc.dmzkicontrol.frieza.surge").shake(2f);
        m.ev(170, BOSS).text("enc.dmzkicontrol.frieza.surge2").shake(2.5f);
        Beat win = m.onWin(64, 0.15f);
        win.shot(0, 0, "boss", 2.4, 1.2, -4, "boss", 0, 1.6, 0, 0.95f);
        win.ev(0, BOSS).fx("beam_win", 44).impact(3).shake(4.5f).snd(d("ki_explosion_impact"), 1.3f, 0.8f).boss("knocked_out").shove(2.5f);
        win.ev(16, BOSS).text("enc.dmzkicontrol.frieza.scream").snd(d("entity.frieza.s.hurt"), 1.2f, 0.7f);
        win.ev(40, ALL).pose("base.fly_idle", 24);
        Beat lose = m.onLose(54, 0.15f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, ALL).fx("beam_lose", 30).pose("base.flyback", 30).impact(2).shake(3.5f).move(MoveKind.OFFSET, 0, 1, 6, 0.35f)
                .snd(d("ki_explosion_impact"));
        lose.ev(8, BOSS).text("enc.dmzkicontrol.frieza.laugh");
        lose.ev(34, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // ------------------------------------------------------------------ Cell Perfeito: a finalizacao inteira

    /**
     * A finalizacao do Cell Perfeito: uma cena so, longa, quando ele chega a 20% de vida. A arrancada vista
     * por ele, o combo e o martelo, o arremesso com a janela de transformacao, o duelo de velocidade, a furia
     * (soco no estomago, gancho e martelo ate o chao) e o Kamehameha contra Kamehameha. Vencer o cabo de
     * guerra derrota o Cell; perder deixa ele vivo e a finalizacao volta depois.
     */
    private static EncScript cellPerfect() {
        EncScript s = new EncScript("cell_perfect", d("saga_cell_perfect"));

        // ---------- 1: a arrancada e o combo
        Phase p = s.phase(0.20f, "enc.dmzkicontrol.title.cell_final").distance(26).lift(2.0);
        Beat b = p.beat(Kind.CINE, 56);
        b.shot(0, 0, "boss", 0.9, 1.9, 2.6, "boss", 0, 1.8, 0, 0.75f)
                .shot(14, 0, "boss", 0.35, 1.75, 1.1, "boss", 0, 1.75, 0, 0.5f, -4f)
                // a arrancada vista pelo Cell: por cima do ombro dele, os jogadores vindo de longe
                .shot(30, 0, "boss", 0.75, 2.35, -1.5, "group", 0, 1.0, 0, 0.8f, 3f);
        b.ev(2, BOSS).snd(d("aura_start"), 0.9f, 0.6f).boss("charge_on");
        b.ev(14, BOSS).fx("boss_charge", 16).boss("charge_off");
        b.ev(30, ALL).pose("base.fly_fast", 200).fx("speedlines", 200).snd(d("tp_short"), 1f, 0.6f);

        Beat q = p.beat(Kind.PROMPT, 196).caption("enc.dmzkicontrol.cap.rush");
        // visao do Cell: as rajadas saem do ombro dele e voam ate os jogadores, que desviam em zigue-zague e crescem
        // na tela a cada desvio; troca de ombro, passa de lado acompanhando e volta ao ombro com eles chegando
        q.shot(0, 0, "boss", 0.75, 2.35, -1.5, "group", 0, 1.0, 0, 0.8f, 3f)
                .shot(60, 0, "boss", -0.8, 2.2, -1.4, "group", 0, 1.0, 0, 0.85f, -4f)
                .shot(112, 16, "group", 6, 0.8, 1.5, "group", 0, 1.2, -2, 0.95f, 5f)
                .shot(150, 0, "boss", 0.5, 1.9, -1.2, "group", 0, 1.2, 0, 0.95f, 2f);
        q.ev(0, ALL).move(MoveKind.LANE, 0, 0, -14, -0.10f);
        int[] rt = {24, 46, 66, 86, 104, 122, 140, 158, 174};
        int[] rw = {5, 5, 5, 4, 4, 4, 3, 3, 3};
        for (int i = 0; i < rt.length; i++) {
            Cue c = q.cue(rt[i], Mode.EACH, Key.DODGE, rw[i]).lead(Math.max(12, 20 - i * 2)).hurt(0.04f);
            c.tell(-14, BOSS).fx("kiblast", 14, SELF).snd(d("kiblast_shoot"), 0.9f, 0.9f + i * 0.04f).boss("attack");
            dodge(c, MoveKind.MIRROR);
            c.onHit(3, SELF).fx("blast_behind", 12).snd(d("ki_explosion_impact"), 0.5f, 1.3f);
            c.onMiss(0, SELF).fx("burst", 10).size(0.8f).pose("hit_recoil", 10).impact(1).shake(2f).snd(d("ki_explosion_impact"), 0.8f, 1.1f);
        }

        Beat arrive = p.beat(Kind.CINE, 28);
        arrive.shot(0, 0, "boss", 0.4, 1.8, -1.0, "group", 0, 1.5, 0, 0.9f)
                .shot(12, 0, "mid", 5, 0.4, 1.5, "mid", 0, 1.8, 0, 1.0f, 6f);
        arrive.ev(0, ALL).move(MoveKind.CLOSE, 0, 0, 0, 0.35f).pose("dbz_guard", 28).fx("afterimage", 8).snd(ZANZOKEN, 0.9f, 1f);
        arrive.ev(10, BOSS).boss("attack");
        arrive.ev(12, ALL).impact(1).shake(1.2f).snd(d("block2"));

        q = p.beat(Kind.PROMPT, 260).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 4, 1.6, 0.6, "mid", 0, 1.5, 0, 0.95f)
                .shot(26, 8, "mid", -4, 2.4, 1.4, "boss", 0, 1.6, 0, 0.9f, -5f)
                .shot(206, 0, "me", 0.22, 1.66, -0.8, "me", 0, 1.62, 0, 0.5f, -6f)
                .shot(216, 0, "mid", 4.4, 0.2, 1.2, "boss", 0, 2.0, 0, 1.05f, 9f);
        // rodizio e duas rodadas de combo em equipe
        Key[] combo = {Key.ATTACK, Key.JUMP, Key.LEFT, Key.RIGHT};
        for (int i = 0; i < combo.length; i++) comboHit(q, i, 10 + 10 * i, combo[i], 4, 0.004f);
        teamRound(q, 52, 2.0, 26, 4);
        teamRound(q, 128, 2.0, 26, 7);
        Cue fin = q.cue(226, Mode.ALL, Key.ATTACK, 6).lead(20).syncDmg(0.02f).hurt(0.04f);
        fin.tell(-18, SELF).move(MoveKind.OFFSET, 0, 2.4, -0.6, 0.35f).pose("base.fly_idle", 18);
        fin.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.8f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.3f);
        fin.onHit(HAMMER_CONTACT, SELF).snd(d("critic_punch2"), 1f, 0.8f);
        fin.onSync(HAMMER_CONTACT, BOSS).fx("impact_star", 12).size(1.8f).impact(3).shake(4.5f).move(MoveKind.ABS, 0, 0, 0, 0.55f).snd(d("ki_explosion_impact")).boss("hurt_down");
        fin.onSync(HAMMER_CONTACT + 6, BOSS).fx("crater", 34).size(1.5f);
        fin.onPartial(HAMMER_CONTACT, BOSS).fx("impact_star", 9).size(1.3f).impact(2).shake(2.5f).move(MoveKind.ABS, 0, 0.6, 0, 0.4f).boss("hurt_top");
        fin.onHit(14, SELF).move(MoveKind.LANE, 0, 0, -20, 0.3f);
        fin.onMiss(0, SELF).pose("hit_recoil", 14).impact(1).snd(d("block3"));
        fin.onMiss(14, SELF).move(MoveKind.LANE, 0, 0, -20, 0.3f);

        Beat a = p.beat(Kind.CINE, 50);
        a.shot(0, 0, "boss", 1.4, 0.5, 2.4, "boss", 0, 1.2, 0, 0.8f, 4f)
                .shot(24, 10, "boss", 0.3, 1.2, 1.4, "boss", 0, 1.7, 0, 0.6f);
        a.ev(10, BOSS).fx("crater", 20).size(0.8f).shake(1f);
        a.ev(20, BOSS).move(MoveKind.ABS, 0, 1.5, 0, 0.12f).fx("boss_charge", 28).boss("charge_on").snd(d("aura_start"), 1f, 0.7f);
        a.ev(46, BOSS).boss("charge_off");

        // ---------- 2: o arremesso, a janela de transformacao e o duelo de velocidade (encadeada)
        p = s.phase(0f, null).distance(6).lift(1.0).chained();
        b = p.beat(Kind.CINE, 104);
        b.shot(0, 0, "group", 0, 2.0, 5, "group", 0, 1.3, 0, 1.0f)
                .shot(8, 0, "p0", 2.6, 1.6, 3.0, "p0", 0, 1.3, 0, 0.8f, -5f)
                .shot(38, 0, "mid", 9, 3, 2, "group", 0, 2, 1, 1.05f)
                .shot(70, 0, "boss", 0.4, 0.9, 1.2, "group", 0, 1.4, 0, 1.1f, 6f);
        b.ev(4, BOSS).fx("afterimage", 12).snd(ZANZOKEN).moveTo(0, 0, 0, 1.6, 1f);
        b.ev(14, 0).pose("skp.grabbed", 40).impact(1).snd(d("block3"));
        b.ev(12, BOSS).boss("attack");
        b.ev(38, BOSS).boss("attack");
        b.ev(20, BOSS).moveTo(0, 0, 3, 4.5, 0.2f);
        b.ev(20, 0).move(MoveKind.OFFSET, 0, 3, 3, 0.2f);
        // com 2 ou mais: arremessa um no outro; o resto toma a onda de choque
        b.ev(40, 0).pose("base.flyback", 30).moveTo(1, 0, 0, 0, 0.5f).snd(d("knockback_character")).n(2, 4);
        b.ev(46, 1).impact(3).shake(3.5f).fx("impact_star", 10).size(1.4f).snd(d("critic_punch1")).n(2, 4);
        b.ev(47, 0).move(MoveKind.OFFSET, 0.8, 0.6, 7, 0.32f).hurt(0.07f).n(2, 4);
        b.ev(47, 1).move(MoveKind.OFFSET, -0.8, 0.6, 7, 0.32f).pose("base.flyback", 26).hurt(0.07f).n(2, 4);
        b.ev(47, OTHERS).fx("shockwave", 16).move(MoveKind.OFFSET, 0, 0.5, 6, 0.3f).pose("base.flyback", 22).hurt(0.04f).n(3, 4);
        // sozinho: arremessado no chao
        b.ev(40, 0).pose("base.flyback", 40).move(MoveKind.OFFSET, 0, -4, 6, 0.45f).snd(d("knockback_character")).n(1, 1);
        b.ev(46, 0).impact(3).shake(4f).fx("crater", 30).hurt(0.08f).snd(d("ki_explosion_impact")).n(1, 1);
        b.ev(60, BOSS).move(MoveKind.ABS, 0, 1.0, 0, 0.3f);
        b.ev(80, ALL).pose("base.ki_charge", 24);

        Beat w = p.beat(Kind.CHARGE, 200).caption("enc.dmzkicontrol.cap.transform");
        w.shot(0, 0, "group", 3.4, 1.0, 3.4, "group", 0, 1.3, 0, 0.85f)
                .shot(100, 100, "group", -3.4, 1.8, 3.2, "group", 0, 1.3, 0, 0.8f);
        w.ev(0, ALL).pose("base.ki_charge", 200).fx("charge", 200).snd(d("ki_charge_loop"), 0.8f, 0.9f);

        Beat rise = p.beat(Kind.CINE, 44);
        rise.shot(0, 0, "boss", -1.0, 0.8, 2.0, "group", 0, 1.6, 0, 0.95f, -3f);
        rise.ev(0, ALL).move(MoveKind.LANE, 0, 0, 0, 0.22f).pose("base.fly_idle", 40);
        rise.ev(8, BOSS).impact(1).boss("attack");

        q = p.beat(Kind.PROMPT, 160).caption("enc.dmzkicontrol.cap.speed");
        q.shot(0, 0, "group", 0, 2.6, 5.5, "group", 0, 1.2, -1, 1.05f)
                .shot(56, 12, "mid", 7, 2.0, 1, "group", 0, 1.3, 0, 1.0f, 5f)
                .shot(110, 10, "boss", -1.2, 1.6, -1.8, "group", 0, 1.3, 0, 0.95f, -4f);
        Key[] spd = {Key.LEFT, Key.UP, Key.RIGHT, Key.DOWN, Key.ATTACK, Key.JUMP, Key.LEFT, Key.ATTACK};
        for (int i = 0; i < spd.length; i++) {
            Cue c = q.cue(16 + 17 * i, Mode.TURN, spd[i], 4).lead(14).dmg(0.004f).hurt(0.04f);
            c.tell(-10, BOSS).moveTo(SELF, 0, 0, -1.15, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.2f);
            c.tell(-3, BOSS).boss("attack");
            c.onHit(0, SELF).pose(i % 2 == 0 ? "base.block" : "dbz_dash_punch", 10).snd(d(i % 2 == 0 ? "block1" : "punch3"));
            c.onHit(4, BOSS).fx(i % 2 == 0 ? "deflect_spark" : "impact_star", 7).impact(1).shake(1f).boss(HURTS[i % HURTS.length]).shove(0.5f);
            c.onMiss(0, SELF).pose("hit_recoil", 10).impact(1).shake(1.5f).snd(d("critic_punch1"));
        }

        // a furia: some na frente dele, soco no estomago, gancho que lanca e martelo ate o chao
        Beat rg = p.beat(Kind.CINE, 36);
        rg.shot(0, 0, "boss", 0.5, 1.0, 2.0, "boss", 0, 1.7, 0, 0.6f, -5f)
                .shot(16, 0, "group", 0.4, 1.0, 2.4, "group", 0, 1.5, 0, 0.7f, 4f);
        rg.ev(0, BOSS).boss("attack").snd(d("aura_start"), 0.8f, 0.6f);
        rg.ev(16, ALL).fx("afterimage", 10).snd(ZANZOKEN, 1f, 0.9f).move(MoveKind.CLOSE, 1.74, 0, 0, 1f).pose("dbz_guard", 20).fx("rage", 20);

        Beat gut = p.beat(Kind.PROMPT, 118).caption("enc.dmzkicontrol.cap.rage");
        gut.shot(0, 0, "boss", 1.7, 0.5, 1.3, "boss", 0, 1.1, 0, 0.72f, 7f)
                .shot(46, 0, "boss", 0.5, 0.2, 2.2, "boss", 0, 2.6, 0, 0.95f, -5f)
                .shot(80, 0, "boss", 2.6, 3.4, 1.0, "boss", 0, 0.4, 0, 1.0f, 8f);
        Cue g1 = gut.cue(22, Mode.ALL, Key.ATTACK, 6).lead(18).syncDmg(0.02f).hurt(0.03f);
        g1.onHit(0, SELF).pose("dbz_gut_punch", 16).move(MoveKind.CLOSE, 1.74, 0, 0, 0.9f);
        g1.onSync(6, BOSS).fx("impact_star", 14).size(2.2f).impact(3).shake(4f).boss("hurt_down").shove(0.4f).snd(d("critic_punch2"), 1.2f, 0.7f);
        g1.onSync(8, BOSS).fx("shockwave", 16).size(1.2f);
        g1.onPartial(6, BOSS).fx("impact_star", 10).size(1.4f).impact(2).shake(2.5f).boss("hurt_down").snd(d("critic_punch1"));
        g1.onMiss(0, SELF).pose("hit_recoil", 12).impact(1).snd(d("block3"));
        Cue g2 = gut.cue(56, Mode.ALL, Key.JUMP, 6).lead(16).syncDmg(0.02f).hurt(0.03f);
        g2.onHit(0, SELF).pose("dbz_rising_uppercut", 16).move(MoveKind.CLOSE, 1.24, 0, 0, 0.9f);
        g2.onSync(6, BOSS).fx("impact_star", 12).size(1.9f).impact(3).shake(3.5f).boss("hurt_top").move(MoveKind.ABS, 0, 7.5, 1.0, 0.3f).snd(d("critic_punch2"), 1f, 0.9f);
        g2.onPartial(6, BOSS).fx("impact_star", 9).size(1.3f).impact(2).boss("hurt_top").move(MoveKind.ABS, 0, 5, 1.0, 0.3f);
        g2.onHit(12, SELF).pose("base.fly_fast", 16).move(MoveKind.OFFSET, 0, 6.2, 0, 0.28f).fx("speedlines", 16);
        Cue g3 = gut.cue(92, Mode.ALL, Key.DOWN, 6).lead(16).syncDmg(0.02f).hurt(0.03f);
        g3.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.85f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.3f);
        g3.onSync(HAMMER_CONTACT, BOSS).fx("impact_star", 14).size(2.0f).impact(3).shake(5f).boss("knocked_out").move(MoveKind.ABS, 0, 0, 0, 0.6f).snd(d("ki_explosion_impact"), 1.2f, 0.8f);
        g3.onSync(HAMMER_CONTACT + 6, BOSS).fx("crater", 40).size(1.8f).shake(3f);
        g3.onPartial(HAMMER_CONTACT, BOSS).fx("impact_star", 9).size(1.3f).impact(2).boss("hurt_down").move(MoveKind.ABS, 0, 1, 0, 0.5f);
        g3.onHit(16, SELF).move(MoveKind.LANE, 0, 1.5, 0, 0.25f).pose("base.fly_idle", 20);
        g3.onMiss(0, SELF).pose("hit_recoil", 12).impact(1).snd(d("block3"));
        g3.onMiss(10, SELF).move(MoveKind.LANE, 0, 1.5, 0, 0.25f);

        // ---------- 3: Kamehameha contra Kamehameha - o golpe final (encadeada)
        p = s.phase(0f, null).distance(11).lift(3.0).chained();
        b = p.beat(Kind.CINE, 100);
        b.shot(0, 0, "boss", 1.3, 1.4, 2.4, "boss", 0, 1.5, 0, 0.8f)
                .shot(26, 0, "boss", 0.3, 1.7, 1.2, "boss", 0, 1.7, 0, 0.5f, -6f)
                .shot(46, 0, "group", 2.2, 1.0, 3.0, "group", 0, 1.4, 0, 0.85f, 4f)
                .shot(70, 0, "p0", 0.25, 1.6, -0.9, "p0", 0, 1.3, 0, 0.55f, 5f)
                .shot(84, 12, "mid", 11, 0.4, 0, "mid", 0, 1.8, 0, 1.0f);
        b.ev(0, BOSS).fx("kame_charge", 100).size(1.5f).snd(d("ki_kame_charge")).boss("charge_on");
        b.ev(46, ALL).pose("ki.kameha_cast", 54).fx("kame_charge", 54).snd(d("ki_kame_charge"), 1f, 1.1f);
        b.ev(70, ALL).fx("rage", 30).shake(1f);

        Beat m = p.beat(Kind.MASH, 320).caption("enc.dmzkicontrol.cap.mash").tug(0.011f, 0.036f).surge(90, 130).surge(190, 230).surge(270, 300).finisher();
        m.shot(0, 0, "mid", 11, 1.0, 0, "mid", 0, 1.8, 0, 1.0f)
                .shot(70, 20, "group", 1.2, 0.5, 3.4, "mid", 0, 2.2, 0, 0.85f, 6f)
                .shot(130, 0, "boss", -1.6, 1.8, -3, "mid", 0, 1.8, 0, 0.9f, -4f)
                .shot(190, 24, "mid", -9, 3.0, 2, "mid", 0, 1.6, 0, 1.05f)
                .shot(270, 0, "p0", 0.3, 1.7, -1.0, "mid", 0, 1.6, 0, 0.7f, 8f);
        m.ev(0, ALL).pose("ki.kameha_fire", 320).impact(2).shake(2f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("clash", 320).snd(d("ki_kame_fire"), 1f, 0.8f).boss("charge_off");
        m.ev(90, BOSS).shake(2f).fx("boss_charge", 40);
        m.ev(190, BOSS).shake(2.5f).fx("boss_charge", 40);
        m.ev(270, ALL).fx("rage", 30).shake(3f).impact(1);
        Beat win = m.onWin(84, 0.0f);
        win.shot(0, 0, "group", 1.0, 0.8, 3.0, "boss", 0, 1.6, 0, 0.9f, 5f)
                .shot(20, 0, "boss", 2.4, 1.2, -4, "boss", 0, 1.6, 0, 0.95f)
                .shot(52, 18, "mid", 0, 9, 22, "boss", 0, 1, 0, 1.15f);
        win.ev(0, BOSS).fx("beam_win", 60).size(1.4f).impact(3).shake(5f).snd(d("ki_explosion_impact"), 1.3f, 0.7f).boss("knocked_out").shove(2.5f);
        win.ev(14, BOSS).fx("disintegrate", 40).size(1.4f);
        win.ev(50, BOSS).fx("sky_explosion", 34).size(2.2f).impact(3).shake(4f).snd(d("ki_explosion_impact"), 1.4f, 0.6f);
        win.ev(66, ALL).pose("base.fly_idle", 18);
        Beat lose = m.onLose(56, 0.16f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, ALL).fx("beam_lose", 30).pose("base.flyback", 30).impact(2).shake(3.5f).move(MoveKind.OFFSET, 0, 1, 6, 0.35f)
                .snd(d("ki_explosion_impact"));
        lose.ev(34, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // ------------------------------------------------------------------ Cell Super Perfeito: Kamehameha pai e filho

    /**
     * A finalizacao do Cell Super Perfeito: o Kamehameha pai e filho. O Cell sobe e solta o Kamehameha dele;
     * quem segura o feixe do grupo (o "Gohan") dispara com uma mao so, o braco machucado pendurado. O cabo de
     * guerra aperta, o espirito do Goku aparece atras do ombro dele e o feixe do grupo dobra de tamanho;
     * no ultimo cabo de guerra o Cell se desfaz dentro do Kamehameha.
     */
    private static EncScript cellSuperPerfect() {
        EncScript s = new EncScript("cell_superperfect", d("saga_cell_superperfect"));
        Phase p = s.phase(0.20f, "enc.dmzkicontrol.title.father_son").distance(12).lift(2.5);

        Beat b = p.beat(Kind.CINE, 110);
        b.shot(0, 0, "group", 0, 0.3, 4.0, "boss", 0, 3.5, 0, 1.05f, 4f)
                .shot(34, 0, "boss", 0.4, 1.7, 1.3, "boss", 0, 1.7, 0, 0.5f, -5f)
                .shot(56, 0, "p0", 1.2, 1.0, 1.6, "p0", 0, 1.3, 0, 0.7f, 6f)
                .shot(82, 14, "mid", 12, 1.6, 0, "mid", 0, 2.4, 0, 1.0f);
        b.ev(0, BOSS).move(MoveKind.ABS, 0, 6.0, -1.0, 0.08f).boss("charge_on").snd(d("aura_start"), 1f, 0.7f);
        b.ev(10, BOSS).fx("kame_charge", 100).size(2.4f).snd(d("ki_kame_charge"), 1.2f, 0.8f);
        b.ev(30, BOSS).shake(1.5f).fx("boss_charge", 60);
        b.ev(56, 0).pose("dbz_kame_onehand_loop", 60).fx("kame_charge", 54).snd(d("ki_kame_charge"), 1f, 1.1f);
        b.ev(56, OTHERS).pose("ki.kameha_cast", 54).fx("kame_charge", 54);
        b.ev(56, 1).pose("ki.kameha_cast", 54).fx("kame_charge", 54).n(2, 4);
        b.ev(96, ALL).fx("rage", 14).shake(1f);

        // o Cell dispara primeiro; o grupo responde e os feixes se encontram
        Beat f = p.beat(Kind.CINE, 30);
        f.shot(0, 0, "boss", -0.6, 2.0, -1.6, "group", 0, 1.2, 0, 0.85f, -4f)
                .shot(14, 0, "mid", 9, 0.6, 1.0, "mid", 0, 2.0, 0, 1.0f, 3f);
        f.ev(0, BOSS).fx("dmz_beam", 30, 0).size(1.6f).boss("charge_off").snd(d("ki_kame_fire"), 1.3f, 0.7f).shake(2f);
        f.ev(14, 0).pose("dbz_kame_onehand_loop", 400).impact(2).shake(2.5f).snd(d("ki_kame_fire"));
        f.ev(14, OTHERS).pose("ki.kameha_fire", 400);
        f.ev(14, 1).pose("ki.kameha_fire", 400).n(2, 4);
        f.ev(14, BOSS).fx("clash", 16).impact(3).fx("shockwave", 20);

        // primeiro cabo de guerra: o Cell e mais forte, o grupo vai perdendo
        Beat m1 = p.beat(Kind.MASH, 220).caption("enc.dmzkicontrol.cap.mash").tug(0.013f, 0.034f).start(0.42f)
                .surge(50, 100).surge(150, 210).mustWin();
        m1.shot(0, 0, "mid", 10, 0.8, 0, "mid", 0, 2.0, 0, 1.0f)
                .shot(60, 20, "p0", -0.6, 1.2, 2.2, "mid", 0, 2.2, 0, 0.8f, -6f)
                .shot(120, 0, "boss", 0.8, 2.1, -1.4, "mid", 0, 1.6, 0, 0.85f, 4f)
                .shot(170, 16, "group", 0, 0.2, 3.2, "mid", 0, 2.6, 0, 0.9f, 7f);
        m1.ev(0, BOSS).fx("clash", 220).size(1.0f);
        m1.ev(50, BOSS).fx("boss_charge", 50).shake(2f);
        m1.ev(150, BOSS).fx("boss_charge", 60).shake(2.8f);
        m1.ev(150, ALL).hurt(0.02f).fx("burst", 10).size(0.6f);
        // o espirito do Goku aparece: "pai e filho"
        Beat sp = m1.onWin(90, 0.0f);
        sp.shot(0, 0, "p0", 0.25, 1.65, -0.85, "p0", 0, 1.6, 0, 0.5f, -4f)
                .shot(24, 22, "p0", -1.6, 1.4, 1.8, "p0", 0, 1.4, 0, 0.75f, 3f)
                .shot(62, 0, "mid", 10, 0.6, 0, "mid", 0, 2.0, 0, 1.0f);
        sp.ev(0, BOSS).fx("clash", 90).size(1.1f);
        sp.ev(16, 0).fx("spirit", 600).impact(1).snd(d("aura_start"), 1.2f, 1.2f);
        sp.ev(50, ALL).fx("aura_burst", 30).size(1.6f).impact(2).shake(3f).snd(d("transform_on"), 1f, 0.9f);
        sp.ev(50, BOSS).fx("clash", 40).size(1.6f);
        Beat l1 = m1.onLose(56, 0.12f);
        l1.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        l1.ev(0, BOSS).fx("beam_lose", 34);
        l1.ev(0, ALL).pose("base.flyback", 30).impact(2).shake(3.5f).move(MoveKind.OFFSET, 0, 1, 6, 0.35f).snd(d("ki_explosion_impact"));
        l1.ev(34, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);

        // segundo cabo de guerra: pai e filho, feixe dobrado - vencer acaba com o Cell
        Beat m2 = p.beat(Kind.MASH, 200).caption("enc.dmzkicontrol.cap.father_son").tug(0.010f, 0.050f).start(0.5f)
                .surge(100, 140).finisher();
        m2.shot(0, 0, "group", 0.5, 0.0, 3.6, "mid", 0, 2.4, 0, 0.9f, 6f)
                .shot(60, 24, "mid", -10, 1.6, 1.5, "mid", 0, 2.0, 0, 1.05f)
                .shot(130, 0, "boss", 0.6, 2.2, -1.5, "group", 0, 1.2, 0, 0.9f, -5f)
                .shot(170, 0, "p0", 0.3, 1.7, -1.0, "mid", 0, 1.6, 0, 0.62f, 9f);
        m2.ev(0, BOSS).fx("clash", 200).size(1.8f);
        m2.ev(0, ALL).impact(2).shake(2.5f).fx("charge", 200).snd(d("ki_kame_fire"), 1.2f, 0.9f);
        m2.ev(100, BOSS).fx("boss_charge", 40).shake(3f);
        m2.ev(170, ALL).fx("rage", 30).shake(3.5f).impact(1);
        Beat win = m2.onWin(110, 0.0f);
        win.shot(0, 0, "group", 1.0, 0.6, 3.2, "boss", 0, 1.6, 0, 0.9f, 6f)
                .shot(18, 0, "boss", 0.4, 1.8, 1.6, "boss", 0, 1.7, 0, 0.55f, -6f)
                .shot(40, 0, "boss", 3.0, 1.2, -5, "boss", 0, 1.6, 0, 1.0f)
                .shot(70, 24, "mid", 0, 12, 26, "boss", 0, 1, 0, 1.2f);
        win.ev(0, BOSS).fx("beam_win", 80).size(2.2f).impact(3).shake(6f).snd(d("ki_explosion_impact"), 1.4f, 0.6f).boss("knocked_out");
        win.ev(18, BOSS).fx("disintegrate", 50).size(1.8f).impact(2);
        win.ev(66, BOSS).fx("sky_explosion", 40).size(3.0f).impact(3).shake(5f).snd(d("ki_explosion_impact"), 1.5f, 0.5f);
        win.ev(92, ALL).pose("base.fly_idle", 18);
        Beat lose = m2.onLose(60, 0.16f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, BOSS).fx("beam_lose", 34);
        lose.ev(0, ALL).pose("base.flyback", 30).impact(2).shake(4f).move(MoveKind.OFFSET, 0, 1, 7, 0.35f).snd(d("ki_explosion_impact"));
        lose.ev(36, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // ------------------------------------------------------------------ combinacoes reutilizadas

    /** Arrancada de todos ate o chefe (camera de lado, rastro de zanzoken). */
    private static void closeIn(Phase p, int len) {
        Beat c = p.beat(Kind.CINE, len);
        c.shot(0, 0, "mid", 6, 1.2, 1.2, "mid", 0, 1.5, 0, 1.0f, 4f);
        c.ev(0, ALL).move(MoveKind.CLOSE, 0, 0, 0, 0.45f).pose("base.fly_fast", len).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1f);
        c.ev(len - 6, ALL).pose("dbz_guard", 12).impact(1);
    }

    /**
     * Rajada em equipe (40 ticks): o 1o fica na frente socando sem parar (varios socos), o 2o some e reaparece
     * de lado disparando rajadas de ki; com 3 ou 4, o 3o vem por cima e o 4o fecha com um chute pelas costas.
     */
    private static void rushVolley(Beat q, int t, double lift, int i) {
        q.shot(t, 0, "mid", 2.8, 1.0, 1.6, "boss", 0, 1.5, 0, 0.85f, 4f)
                .shot(t + 18, 0, "boss", -2.6, 2.2, 1.4, "boss", 0, 1.3, 0, 1.0f, -6f);
        Cue a = q.cue(t, Mode.EACH, Key.ATTACK, 5).only(0).lead(12).dmg(0.007f).hurt(0.03f);
        a.onHit(0, SELF).pose("dbz_rush_loop", 26).move(MoveKind.CLOSE, 1.3, 0, 0, 0.8f).fx("afterimage", 8);
        for (int k = 0; k < 6; k++) {
            a.onHit(2 + k * 4, BOSS).fx("impact_star", 5).size(0.8f).impact(k == 5 ? 2 : 1).shake(k == 5 ? 1.6f : 0.6f)
                    .snd(PUNCH[(i + k) % PUNCH.length], 0.9f, 0.95f + k * 0.04f).boss(HURTS[(i + k) % HURTS.length]).shove(k == 5 ? 0.8f : 0.12f);
        }
        a.onMiss(0, BOSS).boss("attack").snd(d("block2"));
        a.onMiss(2, SELF).pose("hit_recoil", 10).impact(1);

        Cue v = q.cue(t + 6, Mode.EACH, Key.LEFT, 5).only(1).n(2, 4).lead(12).dmg(0.006f).hurt(0.03f);
        v.tell(-12, SELF).move(MoveKind.ABS, -3.2, lift + 0.8, 1.6, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.2f);
        v.onHit(0, SELF).pose("dbz_ki_volley_loop", 28);
        for (int k = 0; k < 5; k++) {
            v.onHit(k * 5, SELF).fx("kiblast", 6, BOSS).size(0.7f).snd(d("kiblast_shoot"), 0.7f, 1.1f + k * 0.05f);
            v.onHit(k * 5 + 6, BOSS).fx("burst", 8).size(0.6f).impact(1).shake(0.6f);
        }
        v.onMiss(0, SELF).pose("hit_recoil", 10);

        Cue top = q.cue(t + 22, Mode.EACH, Key.DOWN, 5).only(2).n(3, 4).lead(12).dmg(0.006f).hurt(0.03f);
        top.tell(-12, SELF).move(MoveKind.ABS, 0.4, lift + 3.2, 0.6, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.25f);
        top.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.85f);
        top.onHit(HAMMER_CONTACT, BOSS).fx("impact_star", 10).size(1.4f).impact(2).shake(2f).boss("hurt_down").snd(d("critic_punch2"));
        top.onMiss(0, SELF).pose("hit_recoil", 10);

        Cue back = q.cue(t + 30, Mode.EACH, Key.RIGHT, 5).only(3).n(4, 4).lead(12).dmg(0.006f).hurt(0.03f);
        back.tell(-12, SELF).move(MoveKind.ABS, 0.3, lift + 0.3, -1.8, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.3f);
        back.onHit(0, SELF).pose("dbz_flying_kick", 14).move(MoveKind.CLOSE, 1.34, 0, 0, 0.9f);
        back.onHit(5, BOSS).fx("impact_star", 10).size(1.3f).impact(2).shake(1.8f).boss("hurt_left").shove(1.0f).snd(d("critic_punch1"));
        back.onMiss(0, SELF).pose("hit_recoil", 10);

        q.ev(t + 40, ALL).move(MoveKind.LANE, 0, 0, 0, 0.3f).pose("dbz_guard", 14);
    }

    /** Golpe final de todos juntos com o martelo duplo (chefe vai ao chao com cratera). */
    private static void hammerFinish(Beat q, int t, float dmg, double groundY) {
        Cue fin = q.cue(t, Mode.ALL, Key.ATTACK, 5).lead(20).syncDmg(dmg).hurt(0.04f);
        fin.tell(-18, SELF).move(MoveKind.OFFSET, 0, 2.4, -0.6, 0.35f).pose("base.fly_idle", 18);
        fin.onHit(0, SELF).pose("dbz_hammer", 14).move(MoveKind.CLOSE, HAMMER_REACH, HAMMER_UP, 0, 0.8f).fx("afterimage", 8).snd(ZANZOKEN, 0.8f, 1.3f);
        fin.onHit(HAMMER_CONTACT, SELF).snd(d("critic_punch2"), 1f, 0.8f);
        fin.onSync(HAMMER_CONTACT, BOSS).fx("impact_star", 12).size(1.8f).impact(3).shake(4f).move(MoveKind.ABS, 0, groundY, 0, 0.55f).snd(d("ki_explosion_impact")).boss("hurt_down");
        fin.onSync(HAMMER_CONTACT + 6, BOSS).fx("crater", 30).size(1.4f);
        fin.onPartial(HAMMER_CONTACT, BOSS).fx("impact_star", 9).size(1.3f).impact(2).shake(2.5f).boss("hurt_top");
        fin.onHit(14, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
        fin.onMiss(0, SELF).pose("hit_recoil", 14).impact(1).snd(d("block3"));
        fin.onMiss(14, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
    }

    // ------------------------------------------------------------------ Freeza segunda forma: a chifrada

    private static EncScript friezaSecond() {
        EncScript s = new EncScript("frieza_second", d("saga_frieza_second"));
        Phase p = s.phase(0.50f, "enc.dmzkicontrol.title.frieza_second").distance(8).lift(1.0);
        Beat b = p.beat(Kind.CINE, 54);
        b.shot(0, 0, "boss", 0.9, 0.6, 2.2, "boss", 0, 2.2, 0, 0.7f, 5f)
                .shot(22, 0, "boss", 0.3, 2.3, 1.3, "boss", 0, 2.3, 0, 0.45f, -3f)
                .shot(38, 6, "group", 2.4, 1.2, 3.6, "boss", 0, 1.8, 0, 0.95f);
        b.ev(2, BOSS).snd(d("aura_start"), 0.9f, 0.7f).boss("charge_on");
        b.ev(22, BOSS).fx("boss_charge", 30).boss("charge_off");
        b.ev(30, ALL).pose("dbz_guard", 24);

        // as chifradas: ele atravessa o grupo, cada um desvia para um lado
        Beat q = p.beat(Kind.PROMPT, 124).caption("enc.dmzkicontrol.cap.dodge");
        q.shot(0, 0, "group", 0, 1.4, 4.5, "boss", 0, 1.6, 0, 0.95f)
                .shot(46, 0, "mid", 7.5, 1.0, 0, "mid", 0, 1.4, 0, 1.0f, 6f)
                .shot(86, 10, "boss", -1.4, 1.8, -2.2, "group", 0, 1.3, 0, 0.9f, -5f);
        int[] ht = {28, 62, 96};
        Key[] hk = {Key.DODGE, Key.UP, Key.DODGE};
        for (int i = 0; i < ht.length; i++) {
            Cue c = q.cue(ht[i], Mode.EACH, hk[i], 5).lead(18).hurt(0.06f);
            c.tell(-16, BOSS).boss("attack").fx("afterimage", 12).snd(ZANZOKEN, 1f, 0.8f);
            c.tell(-8, BOSS).move(MoveKind.ABS, 0, 1.0, 6.5, 0.8f).fx("speedlines", 12);
            if (hk[i] == Key.UP) {
                c.onHit(0, SELF).pose("float_spread", 10).move(MoveKind.OFFSET, 0, 2.0, 0, 0.7f).fx("afterimage", 10).snd(ZANZOKEN);
                c.onHit(12, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
            } else {
                dodge(c, MoveKind.MIRROR);
                c.onHit(12, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
            }
            c.onMiss(0, SELF).pose("base.flyback", 16).fx("impact_star", 8).impact(2).shake(2f).move(MoveKind.OFFSET, 0, 0.8, 3.5, 0.4f).snd(d("critic_punch1"));
            c.onMiss(16, SELF).move(MoveKind.LANE, 0, 0, 0, 0.25f);
            q.ev(ht[i] + 12, BOSS).move(MoveKind.ABS, 0, 1.0, 0, 0.35f);
        }

        closeIn(p, 22);
        q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f)
                .shot(118, 0, "me", 0.22, 1.66, -0.8, "me", 0, 1.62, 0, 0.5f, -5f)
                .shot(126, 0, "mid", 4.2, 0.2, 1.2, "boss", 0, 2.0, 0, 1.05f, 8f);
        Key[] combo = {Key.ATTACK, Key.LEFT, Key.JUMP};
        for (int i = 0; i < combo.length; i++) comboHit(q, i, 10 + 10 * i, combo[i], 4, 0.012f);
        rushVolley(q, 42, 1.0, 1);
        teamRound(q, 84, 1.0, 8, 3);
        hammerFinish(q, 138, 0.08f, 0);

        Beat a = p.beat(Kind.CINE, 40);
        a.shot(0, 0, "boss", 1.2, 0.6, -2.6, "boss", 0, 1.0, 0, 0.8f)
                .shot(22, 10, "group", 0, 2.2, 4, "boss", 0, 1.2, 0, 0.95f);
        a.ev(10, BOSS).fx("crater", 20).size(0.8f).shake(1f);
        a.ev(20, BOSS).move(MoveKind.ABS, 0, 1.0, 0, 0.12f);
        return s;
    }

    // ------------------------------------------------------------------ Vegeta Majin: punhos e Galick Ho

    private static EncScript vegetaMajin() {
        EncScript s = new EncScript("vegeta_majin", d("saga_vegeta_majin"));
        Phase p = s.phase(0.45f, "enc.dmzkicontrol.title.vegeta_majin").distance(7).lift(1.5);
        Beat b = p.beat(Kind.CINE, 50);
        b.shot(0, 0, "boss", 0.5, 1.9, 1.4, "boss", 0, 1.8, 0, 0.5f, -4f)
                .shot(24, 0, "group", -2.2, 1.0, 3.6, "boss", 0, 1.8, 0, 0.9f, 3f);
        b.ev(2, BOSS).boss("charge_on").fx("aura_burst", 30).snd(d("aura_start"), 1f, 0.8f).shake(1.5f);
        b.ev(24, ALL).fx("aura_burst", 24).pose("evs.rage_scream", 20).shake(1.5f);
        b.ev(40, BOSS).boss("charge_off");

        // choque de punhos em sequencia: os dois somem e batem em pontos diferentes do ceu
        Beat q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.speed");
        q.shot(0, 0, "group", 0, 2.6, 5.5, "group", 0, 1.2, -1, 1.05f)
                .shot(50, 10, "mid", 7, 2.0, 1, "group", 0, 1.3, 0, 1.0f, 5f)
                .shot(100, 8, "boss", -1.2, 1.6, -1.8, "group", 0, 1.3, 0, 0.95f, -4f);
        Key[] spd = {Key.ATTACK, Key.LEFT, Key.UP, Key.ATTACK, Key.RIGHT, Key.DOWN, Key.ATTACK};
        for (int i = 0; i < spd.length; i++) {
            Cue c = q.cue(16 + 18 * i, Mode.TURN, spd[i], 4).lead(14).dmg(0.005f).hurt(0.05f);
            c.tell(-10, BOSS).moveTo(SELF, 0, 0, -1.3, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.2f);
            c.tell(-3, BOSS).boss("attack");
            c.onHit(0, SELF).pose("dbz_dash_punch", 10).snd(d("punch3"));
            c.onHit(4, BOSS).fx("impact_star", 8).size(1.2f).impact(2).shake(1.6f).boss(HURTS[i % HURTS.length]).shove(0.4f);
            c.onHit(4, SELF).fx("shockwave", 10).size(0.7f);
            c.onMiss(0, SELF).pose("hit_recoil", 10).impact(1).shake(1.5f).snd(d("critic_punch1"));
        }

        closeIn(p, 20);
        q = p.beat(Kind.PROMPT, 120).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f);
        rushVolley(q, 8, 1.5, 2);
        teamRound(q, 50, 1.5, 7, 5);

        // Kamehameha contra Galick Ho
        b = p.beat(Kind.CINE, 64);
        b.shot(0, 0, "group", 2.4, 0.6, 2.6, "group", 0, 1.6, 0, 0.85f)
                .shot(30, 0, "boss", 1.0, 1.6, 2.2, "boss", 0, 1.8, 0, 0.7f, -4f)
                .shot(48, 8, "mid", 10, 0.6, 0, "mid", 0, 1.6, 0, 1.0f);
        b.ev(0, ALL).move(MoveKind.LANE, 0, 0, 2, 0.3f).pose("ki.kameha_cast", 64).fx("kame_charge", 64).snd(d("ki_kame_charge"));
        b.ev(28, BOSS).fx("boss_charge", 36).size(1.1f).snd(d("ki_explosion_charge")).boss("charge_on");
        Beat m = p.beat(Kind.MASH, 200).caption("enc.dmzkicontrol.cap.mash").tug(0.010f, 0.042f).surge(80, 104).surge(150, 172);
        m.shot(0, 0, "mid", 10, 1.2, 0, "mid", 0, 1.6, 0, 1.0f)
                .shot(90, 30, "group", 2.0, 1.4, 4.6, "mid", 0, 1.6, 0, 0.95f, 4f)
                .shot(160, 20, "boss", -1.6, 1.8, -3, "mid", 0, 1.6, 0, 0.9f, -4f);
        m.ev(0, ALL).pose("ki.kameha_fire", 200).impact(2).shake(2f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("clash", 200).snd(d("ki_beam_fire"), 1f, 0.8f).boss("charge_off");
        Beat win = m.onWin(60, 0.15f);
        win.shot(0, 0, "boss", 2.4, 1.2, -4, "boss", 0, 1.6, 0, 0.95f);
        win.ev(0, BOSS).fx("beam_win", 44).impact(3).shake(4.5f).snd(d("ki_explosion_impact"), 1.3f, 0.8f).boss("knocked_out").shove(2.5f);
        win.ev(40, ALL).pose("base.fly_idle", 20);
        Beat lose = m.onLose(54, 0.15f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, BOSS).fx("beam_lose", 30);
        lose.ev(0, ALL).pose("base.flyback", 30).impact(2).shake(3.5f).move(MoveKind.OFFSET, 0, 1, 6, 0.35f).snd(d("ki_explosion_impact"));
        lose.ev(34, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // ------------------------------------------------------------------ Super Boo: ataque de extincao humana

    private static EncScript superBuu() {
        EncScript s = new EncScript("superbuu", d("saga_superbuu"));
        Phase p = s.phase(0.50f, "enc.dmzkicontrol.title.superbuu").distance(9).lift(2.0);
        Beat b = p.beat(Kind.CINE, 56);
        b.shot(0, 0, "boss", 0.8, 1.8, 2.4, "boss", 0, 1.8, 0, 0.7f)
                .shot(22, 0, "boss", 0.2, -1.2, 2.6, "boss", 0, 4, 0, 1.0f, -6f)
                .shot(40, 0, "group", 0, 2.6, 6, "boss", 0, 9, 0, 1.05f);
        b.ev(4, BOSS).boss("attack").fx("boss_charge", 40).size(1.2f).snd(d("ki_explosion_charge"), 0.9f, 1.3f);
        b.ev(26, BOSS).fx("sky_explosion", 30).size(0.8f).shake(1.5f);
        b.ev(40, ALL).pose("dbz_guard", 16);

        // a chuva rosa: todo mundo desvia ao mesmo tempo, cada vez mais rapido; no fim, todos rebatem juntos
        Beat q = p.beat(Kind.PROMPT, 170).caption("enc.dmzkicontrol.cap.dodge");
        q.shot(0, 0, "group", 0, 3.6, 7, "group", 0, 1.4, 0, 1.05f)
                .shot(60, 12, "mid", 8, 1.2, 1.5, "group", 0, 2.0, 0, 1.0f, 5f)
                .shot(120, 10, "group", -2.6, 0.4, 3.6, "group", 0, 4.0, 0, 1.0f, -6f);
        int[] rt = {24, 44, 62, 80, 96, 112, 128};
        Key[] rk = {Key.DODGE, Key.DODGE, Key.UP, Key.DODGE, Key.DODGE, Key.UP, Key.DODGE};
        for (int i = 0; i < rt.length; i++) {
            Cue c = q.cue(rt[i], Mode.EACH, rk[i], i < 3 ? 5 : 4).lead(Math.max(12, 18 - i)).hurt(0.04f);
            c.tell(-14, BOSS).fx("ki_rain", 14, SELF).snd(d("kiblast_shoot"), 0.8f, 0.8f + i * 0.05f);
            if (rk[i] == Key.UP) {
                c.onHit(0, SELF).pose("float_spread", 10).move(MoveKind.OFFSET, 0, 1.8, 0, 0.7f).fx("afterimage", 10).snd(ZANZOKEN);
                c.onHit(12, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
            } else {
                dodge(c, MoveKind.MIRROR);
            }
            c.onHit(2, SELF).fx("blast_behind", 10).snd(d("ki_explosion_impact"), 0.5f, 1.4f);
            c.onMiss(0, SELF).fx("burst", 10).size(0.8f).pose("hit_recoil", 10).impact(1).shake(2f).snd(d("ki_explosion_impact"), 0.8f, 1.1f);
        }
        Cue back = q.cue(152, Mode.ALL, Key.ATTACK, 6).lead(20).syncDmg(0.06f).hurt(0.06f);
        back.tell(-18, BOSS).fx("kiblast", 18, SELF).size(1.6f).snd(d("kiblast_shoot"), 1f, 0.7f).boss("attack");
        back.onHit(0, SELF).pose("combat.one_handed_uppercut_right", 10).fx("deflect_spark", 8).snd(d("parry"));
        back.onSync(8, BOSS).fx("burst", 16).size(1.6f).impact(3).shake(3f).boss("hurt_top").shove(1.2f).snd(d("ki_explosion_impact"));
        back.onPartial(8, BOSS).fx("burst", 12).size(1f).impact(2).shake(2f).boss("hurt_left");
        back.onMiss(0, SELF).fx("burst", 12).pose("hit_recoil", 12).impact(2).shake(2.5f);

        closeIn(p, 22);
        q = p.beat(Kind.PROMPT, 176).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f)
                .shot(142, 0, "me", 0.22, 1.66, -0.8, "me", 0, 1.62, 0, 0.5f, -6f)
                .shot(150, 0, "mid", 4.2, 0.2, 1.2, "boss", 0, 2.0, 0, 1.05f, 9f);
        Key[] combo = {Key.ATTACK, Key.RIGHT, Key.JUMP};
        for (int i = 0; i < combo.length; i++) comboHit(q, i, 10 + 10 * i, combo[i], 4, 0.01f);
        teamRound(q, 42, 2.0, 9, 1);
        rushVolley(q, 110, 2.0, 4);
        hammerFinish(q, 160, 0.08f, 0);

        Beat a = p.beat(Kind.CINE, 40);
        a.shot(0, 0, "boss", 1.4, 0.5, 2.4, "boss", 0, 1.2, 0, 0.8f, 4f);
        a.ev(8, BOSS).fx("crater", 22).size(1f).shake(1.2f);
        a.ev(22, BOSS).move(MoveKind.ABS, 0, 2.0, 0, 0.12f);
        return s;
    }

    // ------------------------------------------------------------------ Kid Boo: a Genki Dama

    private static EncScript kidBuu() {
        EncScript s = new EncScript("kidbuu", d("saga_kidbuu"));

        // ---------- fase 1: o Boo sem controle
        Phase p = s.phase(0.60f, "enc.dmzkicontrol.title.kidbuu").distance(8).lift(0.5);
        Beat b = p.beat(Kind.CINE, 50);
        b.shot(0, 0, "boss", 0.6, 1.2, 1.8, "boss", 0, 1.1, 0, 0.6f, 6f)
                .shot(20, 0, "boss", -0.5, 0.4, 1.4, "boss", 0, 1.3, 0, 0.5f, -8f)
                .shot(34, 6, "group", 0, 1.6, 4.2, "boss", 0, 1.2, 0, 0.95f);
        b.ev(2, BOSS).boss("attack").snd(d("aura_start"), 1f, 1.4f).fx("rage", 40);
        b.ev(30, ALL).pose("dbz_guard", 20);

        Beat q = p.beat(Kind.PROMPT, 140).caption("enc.dmzkicontrol.cap.dodge");
        q.shot(0, 0, "group", 0, 1.8, 5, "boss", 0, 1.2, 0, 1.0f)
                .shot(50, 0, "mid", 7, 1.0, 0.5, "mid", 0, 1.1, 0, 1.0f, 5f)
                .shot(96, 8, "boss", 1.2, 1.4, -2, "group", 0, 1.1, 0, 0.9f, -4f);
        // ele aparece colado em cada um (sem aviso de onde) e da uma cabecada; desvio ou defesa
        Key[] bk = {Key.DODGE, Key.ATTACK, Key.DODGE, Key.UP, Key.ATTACK, Key.DODGE};
        for (int i = 0; i < bk.length; i++) {
            Cue c = q.cue(20 + 19 * i, Mode.TURN, bk[i], 4).lead(14).hurt(0.05f).dmg(bk[i] == Key.ATTACK ? 0.008f : 0f);
            c.tell(-12, BOSS).moveTo(SELF, 0, 0, -1.3, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.3f);
            c.tell(-4, BOSS).boss("attack");
            if (bk[i] == Key.ATTACK) {
                c.onHit(0, SELF).pose("dbz_gut_punch", 10).snd(d("punch4"));
                c.onHit(5, BOSS).fx("impact_star", 8).size(1.2f).impact(2).shake(1.5f).boss("hurt_top").shove(1.2f);
            } else if (bk[i] == Key.UP) {
                c.onHit(0, SELF).pose("float_spread", 10).move(MoveKind.OFFSET, 0, 1.8, 0, 0.7f).fx("afterimage", 10).snd(ZANZOKEN);
                c.onHit(12, SELF).move(MoveKind.LANE, 0, 0, 0, 0.3f);
            } else {
                dodge(c, MoveKind.MIRROR);
            }
            c.onMiss(0, SELF).pose("base.flyback", 14).fx("impact_star", 8).impact(2).shake(2f).move(MoveKind.OFFSET, 0, 0.6, 3, 0.4f).snd(d("critic_punch1"));
            c.onMiss(14, SELF).move(MoveKind.LANE, 0, 0, 0, 0.25f);
        }
        q.ev(136, BOSS).move(MoveKind.ABS, 0, 0.5, 0, 0.3f);

        closeIn(p, 20);
        q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f)
                .shot(118, 0, "me", 0.22, 1.66, -0.8, "me", 0, 1.62, 0, 0.5f, -5f)
                .shot(126, 0, "mid", 4.2, 0.2, 1.2, "boss", 0, 2.0, 0, 1.05f, 8f);
        rushVolley(q, 8, 0.5, 6);
        teamRound(q, 54, 0.5, 8, 2);
        hammerFinish(q, 136, 0.07f, 0);
        Beat a = p.beat(Kind.CINE, 30);
        a.shot(0, 0, "boss", 1.4, 0.5, 2.4, "boss", 0, 1.2, 0, 0.8f, 4f);
        a.ev(6, BOSS).fx("crater", 22).size(1f).shake(1.2f);
        a.ev(18, BOSS).move(MoveKind.ABS, 0, 0.5, 0, 0.12f);

        // ---------- fase 2: a Genki Dama
        p = s.phase(0.20f, "enc.dmzkicontrol.title.spirit_bomb").distance(14).lift(0.5);
        b = p.beat(Kind.CINE, 110);
        b.shot(0, 0, "group", 0, 1.0, 4, "group", 0, 2.4, 0, 0.9f)
                .shot(26, 0, "group", 1.4, 0.4, 2.8, "group", 0, 9, 0, 1.1f, -5f)
                .shot(64, 0, "boss", 0.6, 1.4, -1.6, "group", 0, 7, 0, 1.0f, 3f)
                .shot(90, 10, "group", 0, 3, 9, "group", 0, 6, 0, 1.15f);
        b.ev(0, ALL).move(MoveKind.LANE, 0, 0, 0, 0.25f).pose("dbz_spirit_raise_loop", 110).fx("charge", 110);
        b.ev(4, BOSS).fx("genki", 110).size(1.0f).snd(d("ki_spiritbomb_charge"), 1f, 1f);
        b.ev(60, BOSS).boss("attack").fx("rage", 50);

        // juntando energia: cada um aperta para encher a esfera (a esfera ja cresce, o MASH vem depois)
        Beat w = p.beat(Kind.PROMPT, 90).caption("enc.dmzkicontrol.cap.spirit_bomb");
        w.shot(0, 0, "group", 0, 3, 9, "group", 0, 6, 0, 1.15f)
                .shot(46, 14, "boss", 0.8, 1.2, -2, "group", 0, 8, 0, 1.05f, -4f);
        w.ev(0, ALL).pose("dbz_spirit_raise_loop", 90);
        w.ev(0, BOSS).fx("genki", 90).size(1.25f);
        Key[] gk = {Key.UP, Key.UP, Key.ATTACK, Key.UP};
        for (int i = 0; i < gk.length; i++) {
            Cue c = teamCue(w, 14 + 18 * i, gk[i]);
            c.onHit(0, SELF).fx("aura_burst", 14).snd(d("aura_start"), 0.6f, 1.4f + i * 0.1f).impact(1);
        }

        // o arremesso e o cabo de guerra: o Boo segura a esfera
        b = p.beat(Kind.CINE, 30);
        b.shot(0, 0, "group", 0.8, 0.6, 2.6, "boss", 0, 2.0, 0, 0.95f, 5f)
                .shot(14, 0, "boss", 2.6, 1.2, 3.2, "boss", 0, 2.0, 0, 1.0f);
        b.ev(0, ALL).pose("dbz_spirit_throw", 30).snd(d("ki_spiritbomb_fire"), 1.2f, 1f).impact(2).shake(2f);
        b.ev(0, BOSS).fx("genki_clash", 30).size(1.0f);
        Beat m = p.beat(Kind.MASH, 240).caption("enc.dmzkicontrol.cap.mash").tug(0.010f, 0.045f).start(0.45f)
                .surge(80, 110).surge(170, 200).finisher();
        m.shot(0, 0, "boss", 2.6, 1.2, 3.2, "boss", 0, 2.0, 0, 1.0f)
                .shot(70, 20, "boss", -1.0, 0.4, -2.4, "boss", 0, 3.2, 0, 0.95f, -6f)
                .shot(140, 20, "group", 3, 1.2, 5, "boss", 0, 2.4, 0, 1.0f, 4f)
                .shot(200, 10, "boss", 0.4, 2.0, 1.4, "boss", 0, 1.6, 0, 0.6f, 6f);
        m.ev(0, ALL).pose("dbz_spirit_throw", 240).fx("charge", 240);
        m.ev(0, BOSS).fx("genki_clash", 240).size(1.0f).boss("attack").shake(2f);
        m.ev(80, BOSS).fx("rage", 30).shake(3f);
        m.ev(170, BOSS).fx("rage", 30).shake(3.5f);
        Beat win = m.onWin(100, 0f);
        win.shot(0, 0, "group", 2, 1.4, 5, "boss", 0, 1.6, 0, 1.0f)
                .shot(20, 0, "boss", 0.5, 1.4, 1.8, "boss", 0, 1.4, 0, 0.6f, -5f)
                .shot(50, 20, "group", 0, 10, 20, "boss", 0, 1, 0, 1.2f);
        win.ev(0, BOSS).fx("genki_win", 90).size(1.0f).impact(3).shake(5f).snd(d("ki_explosion_impact"), 1.4f, 0.6f).boss("knocked_out");
        win.ev(24, BOSS).fx("disintegrate", 50).size(1.6f).impact(2);
        win.ev(70, ALL).pose("base.fly_idle", 30);
        Beat lose = m.onLose(60, 0.15f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, BOSS).fx("genki_win", 30).size(0.6f);
        lose.ev(0, ALL).pose("base.flyback", 30).impact(2).shake(4f).move(MoveKind.OFFSET, 0, 1, 7, 0.35f).snd(d("ki_explosion_impact"));
        lose.ev(36, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    /** Deixa simples de equipe (todos, cada um no seu tempo). */
    private static Cue teamCue(Beat b, int t, Key k) {
        return b.cue(t, Mode.EACH, k, 5).lead(12).hurt(0f);
    }

    // ------------------------------------------------------------------ Beerus: o deus da destruicao

    private static EncScript beerus() {
        EncScript s = new EncScript("beerus", "dmzsparking:saga_beerus");

        // ---------- fase 1: ele defende tudo com um dedo; depois o choque de punhos que treme o ceu
        Phase p = s.phase(0.60f, "enc.dmzkicontrol.title.beerus").distance(7).lift(2.0);
        Beat b = p.beat(Kind.CINE, 50);
        b.shot(0, 0, "boss", 0.7, 1.8, 2.0, "boss", 0, 1.7, 0, 0.6f)
                .shot(26, 6, "group", -2.4, 1.0, 3.8, "boss", 0, 1.8, 0, 0.9f, 3f);
        b.ev(2, BOSS).snd(d("aura_start"), 0.8f, 0.6f);
        b.ev(26, ALL).fx("aura_burst", 24).pose("evs.rage_scream", 20).shake(1.5f);

        closeIn(p, 20);
        Beat q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.speed");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f)
                .shot(50, 10, "boss", -2.6, 1.6, 1.2, "boss", 0, 1.5, 0, 0.8f, -5f)
                .shot(100, 8, "mid", 6, 2.4, 1.6, "boss", 0, 1.5, 0, 1.0f, 4f);
        Key[] spd = {Key.ATTACK, Key.LEFT, Key.RIGHT, Key.UP, Key.ATTACK, Key.DOWN, Key.ATTACK};
        for (int i = 0; i < spd.length; i++) {
            Cue c = q.cue(14 + 18 * i, Mode.TURN, spd[i], 4).lead(12).dmg(0.004f).hurt(0.06f);
            c.tell(-3, BOSS).boss("attack");
            Strike st = COMBO[i % COMBO.length];
            c.onHit(0, SELF).pose(st.pose(), 9).move(MoveKind.CLOSE, st.reach(), 0, 0, 0.8f);
            c.onHit(st.contact(), BOSS).fx("deflect_spark", 7).impact(1).shake(1f).snd(d("block1"), 1f, 1.2f).shove(0.2f);
            c.onMiss(0, SELF).pose("base.flyback", 14).fx("impact_star", 9).impact(2).shake(2.2f).move(MoveKind.OFFSET, 0, 0.6, 4, 0.4f).snd(d("critic_punch2"));
            c.onMiss(14, SELF).move(MoveKind.CLOSE, 0, 0, 0, 0.35f);
        }

        Beat clash = p.beat(Kind.MASH, 160).caption("enc.dmzkicontrol.cap.fist_clash").tug(0.012f, 0.05f).start(0.45f).surge(70, 100);
        clash.shot(0, 0, "mid", 5, 0.8, 0.5, "mid", 0, 1.5, 0, 0.95f, 6f)
                .shot(60, 20, "group", 0, 8, 6, "mid", 0, 1, 0, 1.15f)
                .shot(110, 12, "boss", -1.8, 1.4, 1.6, "mid", 0, 1.5, 0, 0.85f, -6f);
        clash.ev(0, ALL).move(MoveKind.CLOSE, 1.48, 0, 0, 0.6f).pose("dbz_dash_punch", 160).impact(3).shake(4f).snd(d("critic_punch2"), 1.2f, 0.6f);
        clash.ev(0, BOSS).fx("fist_clash", 160).size(1.2f).boss("attack");
        clash.ev(0, ALL).fx("rage", 160);
        Beat win = clash.onWin(50, 0.12f);
        win.shot(0, 0, "boss", 2.0, 1.0, -3, "boss", 0, 1.6, 0, 0.9f);
        win.ev(0, BOSS).fx("impact_star", 12).size(2f).impact(3).shake(5f).boss("hurt_top").shove(3f).snd(d("ki_explosion_impact"), 1.3f, 0.7f);
        win.ev(0, BOSS).fx("shockwave", 20).size(1.6f);
        win.ev(30, ALL).move(MoveKind.LANE, 0, 0, 0, 0.25f).pose("dbz_guard", 20);
        Beat lose = clash.onLose(46, 0.12f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, ALL).pose("base.flyback", 30).impact(3).shake(4f).fx("shockwave", 16).move(MoveKind.OFFSET, 0, 1, 7, 0.4f).snd(d("ki_explosion_impact"));
        lose.ev(30, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 16);

        closeIn(p, 18);
        q = p.beat(Kind.PROMPT, 112).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.5, 0.6, "mid", 0, 1.4, 0, 0.95f);
        rushVolley(q, 6, 2.0, 3);
        teamRound(q, 46, 2.0, 7, 6);

        // ---------- fase 2: a Esfera da Destruicao
        p = s.phase(0.20f, "enc.dmzkicontrol.title.hakai").distance(12).lift(2.5);
        b = p.beat(Kind.CINE, 90);
        b.shot(0, 0, "boss", 0.8, 1.4, 2.4, "boss", 0, 2.6, 0, 0.7f, 4f)
                .shot(24, 0, "boss", 0.4, -0.4, 3.2, "boss", 0, 6, 0, 1.0f, -6f)
                .shot(56, 0, "group", 0, 1.2, 4.6, "boss", 0, 5, 0, 1.1f)
                .shot(76, 8, "mid", 10, 2, 0, "mid", 0, 3, 0, 1.15f);
        b.ev(0, BOSS).fx("hakai", 90).size(1.2f).snd(d("ki_explosion_charge"), 1.2f, 0.6f).boss("charge_on");
        b.ev(40, ALL).pose("ki.kameha_cast", 50).fx("kame_charge", 50).snd(d("ki_kame_charge"));
        b.ev(60, BOSS).shake(2f).boss("attack");
        Beat m = p.beat(Kind.MASH, 240).caption("enc.dmzkicontrol.cap.mash").tug(0.011f, 0.042f).start(0.4f)
                .surge(70, 100).surge(160, 196).finisher();
        m.shot(0, 0, "mid", 10, 1.6, 0, "mid", 0, 2.4, 0, 1.05f)
                .shot(90, 24, "group", 2.0, 0.6, 4.4, "boss", 0, 4, 0, 1.0f, 5f)
                .shot(170, 20, "boss", -1.8, 1.8, -3, "group", 0, 1.6, 0, 0.95f, -5f);
        m.ev(0, ALL).pose("ki.kameha_fire", 240).impact(2).shake(2.5f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("hakai_clash", 240).size(1.2f).boss("charge_off");
        m.ev(70, BOSS).shake(3f);
        m.ev(160, BOSS).shake(3.5f);
        m.ev(160, ALL).fx("rage", 40).impact(1);
        win = m.onWin(100, 0f);
        win.shot(0, 0, "group", 2, 1.4, 5, "boss", 0, 2.4, 0, 1.0f)
                .shot(24, 0, "boss", 3.0, 1.2, -5, "boss", 0, 1.6, 0, 1.0f)
                .shot(60, 20, "mid", 0, 12, 24, "boss", 0, 1, 0, 1.2f);
        win.ev(0, BOSS).fx("hakai_win", 90).size(1.2f).impact(3).shake(5f).snd(d("ki_explosion_impact"), 1.4f, 0.6f).boss("knocked_out");
        win.ev(34, BOSS).fx("sky_explosion", 40).size(2.4f).impact(3).shake(5f);
        win.ev(80, ALL).pose("base.fly_idle", 20);
        lose = m.onLose(60, 0.18f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, ALL).pose("base.flyback", 30).impact(3).shake(4.5f).fx("burst", 20).size(2f).move(MoveKind.OFFSET, 0, 1, 7, 0.35f).snd(d("ki_explosion_impact"));
        lose.ev(36, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // registra por ultimo: os campos estaticos acima (sons, poses) precisam existir antes de montar os roteiros
    static {
        add(friezaBase());
        add(friezaFullPower());
        add(cellPerfect());
        add(cellSuperPerfect());
        add(friezaSecond());
        add(vegetaMajin());
        add(superBuu());
        add(kidBuu());
        add(beerus());
    }
}
