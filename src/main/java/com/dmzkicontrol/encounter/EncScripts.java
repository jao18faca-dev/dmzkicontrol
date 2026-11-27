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
     * Golpes do combo, medidos no Blockbench com o Cell (DMZ) na frente do jogador:
     * distancia centro-a-centro (blocos) em que o punho/pe encosta no peito dele, e o tick
     * do contato (2 de transicao do GeckoLib + pico da animacao).
     */
    private record Strike(String pose, double reach, int contact) {}
    private static final Strike[] COMBO = {
            new Strike("base.jab_right", 1.38, 5),
            new Strike("base.jab_left", 1.38, 5),
            new Strike("combat.gutkick_right", 1.36, 6),
            new Strike("combat.one_handed_uppercut_left", 0.95, 4),
            new Strike("combat.one_handed_punch_right", 1.40, 5),
            new Strike("combat.gutkick_left", 1.36, 6),
            new Strike("base.jab_left", 1.38, 5),
            new Strike("combat.one_handed_uppercut_right", 0.95, 4)};

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
        c.onHit(st.contact(), BOSS).fx("hit_spark", 6).impact(heavy ? 2 : 1).shake(heavy ? 1.6f : 0.7f)
                .snd(PUNCH[i % PUNCH.length], 1f, 0.9f + (i % 4) * 0.06f)
                .boss(HURTS[i % HURTS.length]).shove(heavy ? 0.55f : 0.25f);
        // errou: o chefe defende e devolve
        c.onMiss(0, BOSS).boss("attack").snd(d("block2"));
        c.onMiss(2, SELF).pose("hit_recoil", 10).impact(1).shake(1f).snd(PUNCH[(i + 3) % PUNCH.length], 1f, 0.7f);
        return c;
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
        b.ev(72, ALL).text("enc.dmzkicontrol.kuririn").pose("evs.rage_scream", 24).sfx("クリリン!!");
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
        c3.onSync(10, BOSS).impact(3).sfx("ズバッ!!").shake(3f).snd(d("critic_punch2")).fx("burst", 14).size(1.5f).boss("hurt_top").shove(1.2f);
        c3.onSync(12, BOSS).text("enc.dmzkicontrol.frieza.kienzan_cut");
        c3.onPartial(0, BOSS).fx("kienzan_return", 10);
        c3.onPartial(10, BOSS).impact(2).sfx("ズバッ").shake(2f).snd(d("critic_punch1")).boss("hurt_left").shove(0.6f);
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

        q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 3.6, 1.6, 0.4, "mid", 0, 1.4, 0, 0.95f)
                .shot(50, 8, "mid", -3.4, 2.4, 1.2, "boss", 0, 1.5, 0, 0.9f)
                .shot(96, 8, "boss", 1.4, 1.0, -2.0, "mid", 0, 1.6, 0, 0.85f);
        Key[] combo = {Key.ATTACK, Key.JUMP, Key.ATTACK, Key.LEFT, Key.ATTACK, Key.RIGHT, Key.JUMP, Key.ATTACK, Key.DOWN};
        for (int i = 0; i < combo.length; i++) comboHit(q, i, 14 + 11 * i, combo[i], 4, 0.012f);
        Cue fin = q.cue(126, Mode.ALL, Key.ATTACK, 5).lead(20).syncDmg(0.09f).hurt(0.04f);
        fin.tell(-18, SELF).move(MoveKind.OFFSET, 0, 2.6, -0.9, 0.35f).pose("double_axe", 2);
        fin.onHit(0, SELF).pose("double_axe", 12).snd(d("critic_punch2"), 1f, 0.8f);
        fin.onSync(2, BOSS).impact(3).sfx("ドゴォッ!!").shake(4f).move(MoveKind.ABS, 0, 0, 5.0, 0.55f).snd(d("ki_explosion_impact")).boss("hurt_down");
        fin.onSync(8, BOSS).fx("crater", 30).size(1.4f);
        fin.onPartial(2, BOSS).impact(2).sfx("ドッ").shake(2.5f).move(MoveKind.ABS, 0, 0.5, 5.0, 0.4f).boss("hurt_top");
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
        b.ev(8, ALL).sfx("か");
        b.ev(20, ALL).sfx("め");
        b.ev(32, ALL).sfx("は");
        b.ev(44, ALL).sfx("め");
        b.ev(40, BOSS).fx("boss_charge", 36).size(1.3f).snd(d("ki_explosion_charge")).text("enc.dmzkicontrol.frieza.die_line").boss("charge_on");

        Beat m = p.beat(Kind.MASH, 240).caption("enc.dmzkicontrol.cap.mash").tug(0.010f, 0.040f).surge(90, 118).surge(170, 196);
        m.shot(0, 0, "mid", 10, 1.2, 0, "mid", 0, 1.6, 0, 1.0f)
                .shot(110, 30, "group", 2.0, 1.4, 4.6, "mid", 0, 1.6, 0, 0.95f)
                .shot(180, 20, "boss", -1.6, 1.8, -3, "mid", 0, 1.6, 0, 0.9f);
        m.ev(0, ALL).pose("ki.kameha_fire", 240).sfx("波ーッ!!").impact(2).shake(2f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("clash", 240).snd(d("ki_beam_fire"), 1f, 0.8f).boss("charge_off");
        m.ev(90, BOSS).text("enc.dmzkicontrol.frieza.surge").shake(2f);
        m.ev(170, BOSS).text("enc.dmzkicontrol.frieza.surge2").shake(2.5f);
        Beat win = m.onWin(64, 0.15f);
        win.shot(0, 0, "boss", 2.4, 1.2, -4, "boss", 0, 1.6, 0, 0.95f);
        win.ev(0, BOSS).fx("beam_win", 44).impact(3).sfx("ドォォン!!").shake(4.5f).snd(d("ki_explosion_impact"), 1.3f, 0.8f).boss("knocked_out").shove(2.5f);
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

    // ------------------------------------------------------------------ Cell Perfeito: arrancada, arremesso, Kamehameha

    private static EncScript cellPerfect() {
        EncScript s = new EncScript("cell_perfect", d("saga_cell_perfect"));

        // ---------- fase 1: a arrancada em zigue-zague e o combo
        Phase p = s.phase(0.85f, "enc.dmzkicontrol.title.cell_rush").distance(26).lift(2.0);
        Beat b = p.beat(Kind.CINE, 52);
        b.shot(0, 0, "boss", 0.9, 1.9, 2.6, "boss", 0, 1.8, 0, 0.75f)
                .shot(26, 0, "group", 10, 1.4, 0, "group", 0, 1.2, -3, 0.95f);
        b.ev(2, BOSS).text("enc.dmzkicontrol.cell.taunt");
        b.ev(26, ALL).pose("base.fly_fast", 200).fx("speedlines", 180).snd(d("tp_short"), 1f, 0.6f).sfx("ゴォッ");

        Beat q = p.beat(Kind.PROMPT, 150).caption("enc.dmzkicontrol.cap.rush");
        q.shot(0, 0, "group", 10, 1.4, 0, "group", 0, 1.2, -3, 0.9f)
                .shot(66, 18, "group", 0, 2.2, 5.5, "boss", 0, 1.6, 0, 1.05f)
                .shot(116, 12, "group", -10, 1.6, 0, "group", 0, 1.2, -3, 0.9f);
        q.ev(0, ALL).move(MoveKind.LANE, 0, 0, -14, -0.12f);
        int[] rt = {26, 52, 74, 94, 112, 128};
        int[] rw = {5, 5, 4, 4, 4, 3};
        for (int i = 0; i < rt.length; i++) {
            Cue c = q.cue(rt[i], Mode.EACH, Key.DODGE, rw[i]).lead(Math.max(12, 20 - i * 2)).hurt(0.04f);
            c.tell(-14, BOSS).fx("kiblast", 14, SELF).snd(d("kiblast_shoot"), 0.9f, 0.9f + i * 0.05f).boss("attack");
            dodge(c, MoveKind.MIRROR);
            c.onHit(3, SELF).fx("blast_behind", 12).snd(d("ki_explosion_impact"), 0.5f, 1.3f);
            c.onMiss(0, SELF).fx("burst", 10).size(0.8f).pose("hit_recoil", 10).impact(1).shake(2f).snd(d("ki_explosion_impact"), 0.8f, 1.1f);
        }

        Beat arrive = p.beat(Kind.CINE, 26);
        arrive.shot(0, 0, "mid", 5, 0.4, 1.5, "mid", 0, 1.8, 0, 1.0f);
        arrive.ev(0, ALL).move(MoveKind.CLOSE, 0, 0, 0, 0.35f).pose("base.block", 26).sfx("ザッ");
        arrive.ev(6, BOSS).text("enc.dmzkicontrol.cell.come");

        q = p.beat(Kind.PROMPT, 156).caption("enc.dmzkicontrol.cap.combo");
        q.shot(0, 0, "mid", 4, 1.6, 0.6, "mid", 0, 1.5, 0, 0.95f)
                .shot(52, 8, "mid", -4, 2.4, 1.4, "boss", 0, 1.6, 0, 0.9f)
                .shot(104, 8, "boss", 1.5, 1.0, -2.5, "mid", 0, 1.6, 0, 0.85f);
        Key[] combo = {Key.ATTACK, Key.JUMP, Key.LEFT, Key.RIGHT, Key.ATTACK, Key.DOWN, Key.ATTACK, Key.JUMP, Key.RIGHT, Key.ATTACK};
        for (int i = 0; i < combo.length; i++) {
            comboHit(q, i, 12 + 10 * i, combo[i], 4, 0.012f);
        }
        Cue fin = q.cue(132, Mode.ALL, Key.ATTACK, 6).lead(20).syncDmg(0.09f).hurt(0.04f);
        fin.tell(-18, SELF).move(MoveKind.OFFSET, 0, 3.0, -0.9, 0.35f).pose("double_axe", 2);
        fin.onHit(0, SELF).pose("double_axe", 12).snd(d("critic_punch2"), 1f, 0.8f);
        fin.onSync(2, BOSS).impact(3).sfx("ドゴォン!!").shake(4.5f).move(MoveKind.ABS, 0, 0, 0, 0.55f).snd(d("ki_explosion_impact")).boss("hurt_down");
        fin.onSync(8, BOSS).fx("crater", 34).size(1.5f);
        fin.onPartial(2, BOSS).impact(2).sfx("ドッ").shake(2.5f).move(MoveKind.ABS, 0, 0.6, 0, 0.4f).boss("hurt_top");
        fin.onHit(14, SELF).move(MoveKind.LANE, 0, 0, -20, 0.3f);
        fin.onMiss(0, SELF).pose("hit_recoil", 14).impact(1).snd(d("block3"));
        fin.onMiss(14, SELF).move(MoveKind.LANE, 0, 0, -20, 0.3f);

        Beat a = p.beat(Kind.CINE, 46);
        a.shot(0, 0, "boss", 1.4, 0.5, 2.4, "boss", 0, 1.2, 0, 0.8f)
                .shot(24, 10, "group", 0, 2.0, 4, "boss", 0, 1.6, 0, 0.95f);
        a.ev(6, BOSS).text("enc.dmzkicontrol.cell.interesting");
        a.ev(20, BOSS).move(MoveKind.ABS, 0, 2.0, 0, 0.12f);

        // ---------- fase 2: o arremesso e a janela de transformacao
        p = s.phase(0.55f, "enc.dmzkicontrol.title.cell_throw").distance(6).lift(1.0);
        b = p.beat(Kind.CINE, 104);
        b.shot(0, 0, "group", 0, 2.0, 5, "group", 0, 1.3, 0, 1.0f)
                .shot(8, 0, "p0", 2.6, 1.6, 3.0, "p0", 0, 1.3, 0, 0.8f)
                .shot(38, 0, "mid", 9, 3, 2, "group", 0, 2, 1, 1.05f)
                .shot(70, 0, "boss", 0.4, 0.9, 1.2, "group", 0, 1.4, 0, 1.1f);
        b.ev(4, BOSS).fx("afterimage", 12).snd(ZANZOKEN).moveTo(0, 0, 0, 1.6, 1f);
        b.ev(10, BOSS).text("enc.dmzkicontrol.cell.behind");
        b.ev(14, 0).pose("skp.grabbed", 40).sfx("ガシッ").impact(1).snd(d("block3"));
        b.ev(12, BOSS).boss("attack");
        b.ev(38, BOSS).boss("attack");
        b.ev(20, BOSS).moveTo(0, 0, 3, 4.5, 0.2f);
        b.ev(20, 0).move(MoveKind.OFFSET, 0, 3, 3, 0.2f);
        // com 2 ou mais: arremessa um no outro; o resto toma a onda de choque
        b.ev(40, 0).pose("base.flyback", 30).moveTo(1, 0, 0, 0, 0.5f).snd(d("knockback_character")).sfx("ブンッ").n(2, 4);
        b.ev(46, 1).impact(3).sfx("ドガッ!!").shake(3.5f).fx("burst", 12).snd(d("critic_punch1")).n(2, 4);
        b.ev(47, 0).move(MoveKind.OFFSET, 0.8, 0.6, 7, 0.32f).hurt(0.07f).n(2, 4);
        b.ev(47, 1).move(MoveKind.OFFSET, -0.8, 0.6, 7, 0.32f).pose("base.flyback", 26).hurt(0.07f).n(2, 4);
        b.ev(47, OTHERS).fx("shockwave", 16).move(MoveKind.OFFSET, 0, 0.5, 6, 0.3f).pose("base.flyback", 22).hurt(0.04f).n(3, 4);
        // sozinho: arremessado no chao
        b.ev(40, 0).pose("base.flyback", 40).move(MoveKind.OFFSET, 0, -4, 6, 0.45f).snd(d("knockback_character")).sfx("ブンッ").n(1, 1);
        b.ev(46, 0).impact(3).sfx("ドガァ!!").shake(4f).fx("crater", 30).hurt(0.08f).snd(d("ki_explosion_impact")).n(1, 1);
        b.ev(60, BOSS).move(MoveKind.ABS, 0, 1.0, 0, 0.3f);
        b.ev(74, BOSS).text("enc.dmzkicontrol.cell.show_me");
        b.ev(80, ALL).pose("base.ki_charge", 24);

        Beat w = p.beat(Kind.CHARGE, 200).caption("enc.dmzkicontrol.cap.transform");
        w.shot(0, 0, "group", 3.4, 1.0, 3.4, "group", 0, 1.3, 0, 0.85f)
                .shot(100, 100, "group", -3.4, 1.8, 3.2, "group", 0, 1.3, 0, 0.8f);
        w.ev(0, ALL).pose("base.ki_charge", 200).fx("charge", 200).snd(d("ki_charge_loop"), 0.8f, 0.9f);

        Beat rise = p.beat(Kind.CINE, 44);
        rise.shot(0, 0, "boss", -1.0, 0.8, 2.0, "group", 0, 1.6, 0, 0.95f);
        rise.ev(0, ALL).move(MoveKind.LANE, 0, 0, 0, 0.22f).pose("base.fly_idle", 40);
        rise.ev(8, BOSS).text("enc.dmzkicontrol.cell.excellent").impact(1);

        q = p.beat(Kind.PROMPT, 126).caption("enc.dmzkicontrol.cap.speed");
        q.shot(0, 0, "group", 0, 2.6, 5.5, "group", 0, 1.2, -1, 1.05f)
                .shot(64, 12, "mid", 7, 2.0, 1, "group", 0, 1.3, 0, 1.0f);
        Key[] spd = {Key.LEFT, Key.UP, Key.RIGHT, Key.DOWN, Key.ATTACK, Key.JUMP};
        for (int i = 0; i < spd.length; i++) {
            Cue c = q.cue(16 + 17 * i, Mode.TURN, spd[i], 4).lead(14).dmg(0.015f).hurt(0.04f);
            c.tell(-10, BOSS).moveTo(SELF, 0, 0, -1.15, 1f).fx("afterimage", 10).snd(ZANZOKEN, 0.9f, 1.2f);
            c.tell(-3, BOSS).boss("attack");
            c.onHit(0, SELF).pose(i % 2 == 0 ? "base.block" : "base.jab_left", 9).snd(d(i % 2 == 0 ? "block1" : "punch3"));
            c.onHit(1, BOSS).fx("hit_spark", 6).impact(1).shake(1f).boss(HURTS[i % HURTS.length]).shove(0.5f);
            c.onMiss(0, SELF).pose("hit_recoil", 10).impact(1).shake(1.5f).snd(d("critic_punch1"));
        }

        a = p.beat(Kind.CINE, 30);
        a.shot(0, 0, "boss", 0.8, 1.7, 2.4, "boss", 0, 1.7, 0, 0.75f);
        a.ev(0, BOSS).move(MoveKind.ABS, 0, 1.0, 0, 1f).fx("afterimage", 10).snd(ZANZOKEN);
        a.ev(4, BOSS).text("enc.dmzkicontrol.cell.not_bad");

        // ---------- fase 3: Kamehameha contra Kamehameha
        p = s.phase(0.25f, "enc.dmzkicontrol.title.cell_kame").distance(11).lift(3.0);
        b = p.beat(Kind.CINE, 92);
        b.shot(0, 0, "boss", 1.3, 1.4, 2.4, "boss", 0, 1.5, 0, 0.8f)
                .shot(46, 0, "group", 2.2, 1.0, 3.0, "group", 0, 1.4, 0, 0.85f)
                .shot(72, 14, "mid", 11, 0.4, 0, "mid", 0, 1.8, 0, 1.0f);
        b.ev(0, BOSS).fx("kame_charge", 92).size(1.4f).snd(d("ki_kame_charge")).boss("charge_on");
        b.ev(8, BOSS).text("enc.dmzkicontrol.cell.kame");
        b.ev(46, ALL).pose("ki.kameha_cast", 46).fx("kame_charge", 46).snd(d("ki_kame_charge"), 1f, 1.1f);
        b.ev(52, ALL).sfx("か");
        b.ev(62, ALL).sfx("め");
        b.ev(72, ALL).sfx("は");
        b.ev(82, ALL).sfx("め");

        Beat m = p.beat(Kind.MASH, 260).caption("enc.dmzkicontrol.cap.mash").tug(0.011f, 0.040f).surge(90, 120).surge(190, 220);
        m.shot(0, 0, "mid", 11, 1.0, 0, "mid", 0, 1.8, 0, 1.0f)
                .shot(120, 30, "group", 2.0, 1.4, 4.6, "mid", 0, 1.8, 0, 0.95f)
                .shot(200, 20, "boss", -1.6, 1.8, -3, "mid", 0, 1.8, 0, 0.9f);
        m.ev(0, ALL).pose("ki.kameha_fire", 260).sfx("波ーッ!!").impact(2).shake(2f).snd(d("ki_kame_fire"));
        m.ev(0, BOSS).fx("clash", 260).snd(d("ki_kame_fire"), 1f, 0.8f).boss("charge_off");
        m.ev(90, BOSS).text("enc.dmzkicontrol.cell.surge").shake(2f);
        m.ev(190, BOSS).text("enc.dmzkicontrol.cell.surge2").shake(2.5f);
        Beat win = m.onWin(70, 0.16f);
        win.shot(0, 0, "boss", 2.4, 1.2, -4, "boss", 0, 1.6, 0, 0.95f);
        win.ev(0, BOSS).fx("beam_win", 48).impact(3).sfx("ドォォォン!!").shake(5f).snd(d("ki_explosion_impact"), 1.3f, 0.7f).boss("knocked_out").shove(2.5f);
        win.ev(18, BOSS).text("enc.dmzkicontrol.cell.scream");
        win.ev(46, ALL).pose("base.fly_idle", 24);
        Beat lose = m.onLose(56, 0.16f);
        lose.shot(0, 0, "group", 3, 2, 6, "group", 0, 1.2, 0, 1.1f);
        lose.ev(0, ALL).fx("beam_lose", 30).pose("base.flyback", 30).impact(2).shake(3.5f).move(MoveKind.OFFSET, 0, 1, 6, 0.35f)
                .snd(d("ki_explosion_impact"));
        lose.ev(8, BOSS).text("enc.dmzkicontrol.cell.laugh");
        lose.ev(34, ALL).move(MoveKind.LANE, 0, 0, 0, 0.2f).pose("base.fly_idle", 20);
        return s;
    }

    // registra por ultimo: os campos estaticos acima (sons, poses) precisam existir antes de montar os roteiros
    static {
        add(friezaBase());
        add(friezaFullPower());
        add(cellPerfect());
    }
}
