package com.dmzkicontrol.encounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Roteiro de uma luta cinematografica (quick time events) contra um chefe. Igual no servidor e no cliente
 * (os dois montam o mesmo roteiro em {@link EncScripts}); os pacotes so mandam id, fase e cena.
 *
 * <p>Coordenadas da arena: origem no chefe quando a luta comecou; x = direita de quem olha para o chefe,
 * y = cima, z = distancia do chefe na direcao dos jogadores. Os jogadores ficam em faixas lado a lado
 * (1 a 4), o primeiro (quem bateu) na direita.
 */
public final class EncScript {
    /** Alvos dos eventos: papeis 0..3 sao jogadores; estes sao especiais. */
    public static final int BOSS = -1, ALL = -2, SELF = -3, OTHERS = -4;

    public enum Kind { CINE, PROMPT, MASH, CHARGE }

    /** Como uma deixa de botao e distribuida entre os jogadores. */
    public enum Mode {
        /** Todo mundo recebe a mesma deixa, cada um responde por si. */
        EACH,
        /** Um de cada vez, em rodizio (combo em dupla/trio/quarteto). */
        TURN,
        /** Todo mundo junto: acertar no mesmo instante da o golpe combinado. */
        ALL
    }

    /** Teclas: as do proprio jogador (andar, atacar, pular). DODGE = para o lado da faixa do colega. */
    public enum Key { LEFT, RIGHT, UP, DOWN, ATTACK, JUMP, DODGE }

    public enum MoveKind {
        /** Volta para a propria faixa (mais o deslocamento). */
        LANE,
        /** Troca de lado com o colega (x vira -x). Quem esta no meio pula para um lado. */
        MIRROR,
        /** Soma ao lugar atual. */
        OFFSET,
        /** Vai para onde esta o jogador do papel ref (mais o deslocamento). */
        TO_ROLE,
        /** Ponto fixo da arena (o chefe usa este). */
        ABS,
        /** Esquiva curta (zanzoken) para o lado da tecla DODGE: x = distancia. */
        HOP,
        /** Corpo a corpo: em arco na frente do chefe, colado nele (todos na mesma distancia, o soco alcanca). */
        CLOSE,
        /** Avanco do golpe: x blocos na direcao do chefe (volta com CLOSE). */
        LUNGE
    }

    public final String id;
    public final String bossType;
    public final List<Phase> phases = new ArrayList<>();

    public EncScript(String id, String bossType) {
        this.id = id;
        this.bossType = bossType;
    }

    public Phase phase(float hpBelow, String titleKey) {
        Phase p = new Phase(hpBelow, titleKey);
        phases.add(p);
        return p;
    }

    public static final class Phase {
        public final float at;
        public final String title;
        public final List<Beat> beats = new ArrayList<>();
        /** Afastamento inicial dos jogadores (blocos do chefe). */
        public double distance = 7.0;
        /** Altura dos jogadores e do chefe na arena (voando). */
        public double lift = 0.0;
        /** Fase encadeada: comeca sozinha logo depois da anterior, na mesma cena (sem gatilho de vida). */
        public boolean chained;

        Phase(float at, String title) {
            this.at = at;
            this.title = title;
        }

        public Phase distance(double d) { this.distance = d; return this; }

        public Phase lift(double y) { this.lift = y; return this; }

        public Phase chained() { this.chained = true; return this; }

        public Beat beat(Kind kind, int len) {
            Beat b = new Beat(kind, len);
            beats.add(b);
            return b;
        }
    }

    public static final class Beat {
        public final Kind kind;
        public int len;
        public String caption;
        public final List<Shot> shots = new ArrayList<>();
        public final List<Ev> evs = new ArrayList<>();
        public final List<Cue> cues = new ArrayList<>();
        // cabo de guerra (MASH) e janela de transformacao (CHARGE)
        public float push = 0.010f, gain = 0.040f;
        public float dmgWin, hurtLose;
        public Beat win, lose;
        /** Momentos em que o chefe empurra mais forte no cabo de guerra: {inicio, fim}. */
        public final List<int[]> surges = new ArrayList<>();
        /** Onde o cabo de guerra comeca (0 = chefe ganhando, 1 = jogadores ganhando). */
        public float startProgress = 0.5f;
        /** Vencer este cabo de guerra derrota o chefe de vez (golpe final). */
        public boolean killOnWin;
        /** Perder aqui encerra a cena (o chefe fica com pouca vida e a finalizacao pode voltar depois). */
        public boolean endOnLose;
        private int turns;

        Beat(Kind kind, int len) {
            this.kind = kind;
            this.len = len;
        }

        public Beat caption(String key) { this.caption = key; return this; }

        public Beat shot(int t, int blend, String from, double r, double u, double f, String at, double ar, double au, double af, float fov) {
            return addShot(new Shot(t, blend, from, new double[]{r, u, f}, at, new double[]{ar, au, af}, fov, 0f));
        }

        public Beat shot(int t, int blend, String from, double r, double u, double f, String at, double ar, double au, double af, float fov, float roll) {
            return addShot(new Shot(t, blend, from, new double[]{r, u, f}, at, new double[]{ar, au, af}, fov, roll));
        }

        /** Mantem os planos em ordem de tempo (as rodadas de combo em equipe acrescentam planos no meio). */
        private Beat addShot(Shot sh) {
            int i = shots.size();
            while (i > 0 && shots.get(i - 1).t() > sh.t()) i--;
            shots.add(i, sh);
            return this;
        }

        public Ev ev(int t, int who) {
            Ev e = new Ev(t, who);
            evs.add(e);
            return e;
        }

        public Cue cue(int t, Mode mode, Key key, int win) {
            Cue c = new Cue(cues.size(), t, mode, key, win);
            if (mode == Mode.TURN) c.turn = turns++;
            cues.add(c);
            return c;
        }

        public Beat tug(float push, float gain) { this.push = push; this.gain = gain; return this; }

        public Beat surge(int from, int to) { surges.add(new int[]{from, to}); return this; }

        public Beat start(float progress) { this.startProgress = progress; return this; }

        public Beat finisher() { this.killOnWin = true; this.endOnLose = true; return this; }

        public Beat mustWin() { this.endOnLose = true; return this; }

        public float pushAt(int t) {
            float p = push * (1f + 0.35f * (float) Math.sin(t / 9.0));
            for (int[] s : surges) if (t >= s[0] && t < s[1]) p *= 2.3f;
            return p;
        }

        public Beat onWin(int len, float dmg) { this.win = new Beat(Kind.CINE, len); this.dmgWin = dmg; return win; }

        public Beat onLose(int len, float hurt) { this.lose = new Beat(Kind.CINE, len); this.hurtLose = hurt; return lose; }
    }

    /**
     * Enquadramento: camera em {@code from}+deslocamento olhando para {@code at}+deslocamento.
     * Ancoras: boss, group (meio dos jogadores), me, mid (entre o grupo e o chefe), p0..p3.
     */
    public record Shot(int t, int blend, String from, double[] off, String at, double[] atOff, float fov, float roll) {}

    public static final class Ev {
        public int t;
        public final int who;
        public String pose;
        public int poseTicks = 20;
        public String snd;
        public float vol = 1f, pitch = 1f;
        public String fx;
        public int fxTo = Integer.MIN_VALUE;
        public int fxDur = 20;
        public float fxSize = 1f;
        public String text;
        public String sfx;
        public int impact;
        public float shake;
        public Move move;
        /** Dano no chefe (% da vida maxima) e nos alvos (% da vida maxima, nunca mata). Aplicados no servidor. */
        public float dmg, hurt;
        public int minN = 1, maxN = 4;
        /** Reacao do chefe com as animacoes do proprio DMZ: hurt_left, hurt_right, hurt_top, hurt_down, grabbed,
         *  knocked_out, parried, attack (golpe), charge_on / charge_off (carregando ki). */
        public String bossAnim;
        /** Empurrao no chefe (blocos para longe dos jogadores, volta sozinho). */
        public float shove;

        Ev(int t, int who) {
            this.t = t;
            this.who = who;
        }

        public Ev pose(String p, int ticks) { this.pose = p; this.poseTicks = ticks; return this; }

        public Ev snd(String id) { this.snd = id; return this; }

        public Ev snd(String id, float vol, float pitch) { this.snd = id; this.vol = vol; this.pitch = pitch; return this; }

        public Ev fx(String kind, int dur) { this.fx = kind; this.fxDur = dur; return this; }

        public Ev fx(String kind, int dur, int to) { this.fx = kind; this.fxDur = dur; this.fxTo = to; return this; }

        public Ev size(float s) { this.fxSize = s; return this; }

        public Ev text(String key) { this.text = key; return this; }

        public Ev sfx(String s) { this.sfx = s; return this; }

        public Ev impact(int level) { this.impact = level; return this; }

        public Ev shake(float amp) { this.shake = amp; return this; }

        public Ev move(MoveKind kind, double x, double y, double z, float speed) { this.move = new Move(kind, x, y, z, speed, 0); return this; }

        public Ev moveTo(int role, double x, double y, double z, float speed) { this.move = new Move(MoveKind.TO_ROLE, x, y, z, speed, role); return this; }

        public Ev dmg(float pct) { this.dmg = pct; return this; }

        public Ev boss(String anim) { this.bossAnim = anim; return this; }

        public Ev shove(float blocks) { this.shove = blocks; return this; }

        public Ev hurt(float pct) { this.hurt = pct; return this; }

        /** So acontece com esta quantidade de jogadores. */
        public Ev n(int min, int max) { this.minN = min; this.maxN = max; return this; }
    }

    public record Move(MoveKind kind, double x, double y, double z, float speed, int ref) {
        /** Mesmo movimento, mais lento por {@code k}: a fracao por tick vira 1-(1-s)^(1/k); passo fixo divide. */
        Move slow(double k) {
            float sp = speed;
            if (sp < 0) sp = (float) (sp / k);
            else if (sp > 0 && sp < 1) sp = (float) (1 - Math.pow(1 - sp, 1 / k));
            return new Move(kind, x, y, z, sp, ref);
        }
    }

    // ------------------------------------------------------------ ritmo

    private boolean slowed;

    /**
     * Deixa a cena inteira mais lenta (mais facil de acompanhar): todos os tempos do roteiro multiplicam por
     * {@code k} (batidas, cortes de camera, eventos, poses, efeitos, deixas e janelas), os movimentos ficam mais
     * lentos na mesma medida e o chefe empurra o cabo de guerra mais devagar. As animacoes dos golpes tocam mais
     * lentas pelo mesmo fator (ArmAnimDriver), entao o contato continua batendo no tempo certo.
     */
    public void slow(double k) {
        if (slowed || k <= 1.001) return;
        slowed = true;
        for (Phase ph : phases) for (Beat b : ph.beats) slowBeat(b, k);
    }

    private static int sc(int t, double k) {
        return (int) Math.round(t * k);
    }

    private static void slowBeat(Beat b, double k) {
        if (b == null) return;
        b.len = sc(b.len, k);
        b.push = (float) (b.push / k);
        for (int i = 0; i < b.shots.size(); i++) {
            Shot s = b.shots.get(i);
            b.shots.set(i, new Shot(sc(s.t(), k), sc(s.blend(), k), s.from(), s.off(), s.at(), s.atOff(), s.fov(), s.roll()));
        }
        for (Ev e : b.evs) slowEv(e, k);
        for (int[] s : b.surges) {
            s[0] = sc(s[0], k);
            s[1] = sc(s[1], k);
        }
        for (Cue c : b.cues) {
            c.t = sc(c.t, k);
            c.win = sc(c.win, k);
            c.lead = sc(c.lead, k);
            for (List<Ev> l : List.of(c.hit, c.miss, c.sync, c.partial, c.tell)) for (Ev e : l) slowEv(e, k);
        }
        slowBeat(b.win, k);
        slowBeat(b.lose, k);
    }

    private static void slowEv(Ev e, double k) {
        e.t = sc(e.t, k);
        e.poseTicks = sc(e.poseTicks, k);
        e.fxDur = sc(e.fxDur, k);
        if (e.move != null) e.move = e.move.slow(k);
    }

    public static final class Cue {
        public final int index;
        public int t;
        public final Mode mode;
        public final Key key;
        public int win;
        public int turn;
        public int lead = 20;
        public float dmg, syncDmg, hurt;
        /** Eventos de quem acertou / errou (alvo SELF = o proprio). */
        public final List<Ev> hit = new ArrayList<>();
        public final List<Ev> miss = new ArrayList<>();
        /** Todo mundo acertou junto (ALL) ou o acerto foi perfeito (sozinho). */
        public final List<Ev> sync = new ArrayList<>();
        /** ALL sem sincronia: so parte acertou. */
        public final List<Ev> partial = new ArrayList<>();
        /** Aviso antes da deixa (t relativo a deixa, negativo); SELF = quem vai responder. */
        public final List<Ev> tell = new ArrayList<>();

        Cue(int index, int t, Mode mode, Key key, int win) {
            this.index = index;
            this.t = t;
            this.mode = mode;
            this.key = key;
            this.win = win;
        }

        public Cue lead(int ticks) { this.lead = ticks; return this; }

        public Cue dmg(float pct) { this.dmg = pct; return this; }

        public Cue syncDmg(float pct) { this.syncDmg = pct; return this; }

        public Cue hurt(float pct) { this.hurt = pct; return this; }

        public Ev onHit(int dt, int who) { Ev e = new Ev(dt, who); hit.add(e); return e; }

        public Ev onMiss(int dt, int who) { Ev e = new Ev(dt, who); miss.add(e); return e; }

        public Ev onSync(int dt, int who) { Ev e = new Ev(dt, who); sync.add(e); return e; }

        public Ev onPartial(int dt, int who) { Ev e = new Ev(dt, who); partial.add(e); return e; }

        public Ev tell(int dt, int who) { Ev e = new Ev(dt, who); tell.add(e); return e; }

        /** Papel fixo (combo em equipe): so esse jogador responde, e a deixa so existe se ele estiver na cena. */
        public int fixed = -1;

        /** Ninguem responde (papel fixo de um jogador que nao esta na cena). */
        public static final int NONE = -2;

        public Cue only(int role) { this.fixed = role; return this; }

        /** Quantos jogadores precisam estar na cena para esta deixa existir. */
        public int minN = 1, maxN = 4;

        public Cue n(int min, int max) { this.minN = min; this.maxN = max; return this; }

        /** Papel que responde a esta deixa (TURN ou papel fixo; -1 = todos; NONE = ninguem). */
        public int role(int n) {
            if (n < minN || n > maxN) return NONE;
            if (fixed >= 0) return fixed < n ? fixed : NONE;
            return mode == Mode.TURN ? turn % Math.max(1, n) : -1;
        }

        public boolean active(int n) {
            return role(n) != NONE;
        }

        public boolean involves(int role, int n) {
            int r = role(n);
            if (r == NONE) return false;
            return r < 0 || r == role;
        }
    }

    // ------------------------------------------------------------------ arena

    /** Faixa de cada jogador (x da arena), o primeiro na direita. */
    public static double laneX(int role, int n) {
        double spacing = n >= 4 ? 2.3 : 2.6;
        return ((n - 1) / 2.0 - role) * spacing;
    }
}
