package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.client.DmzStyle;
import com.dmzkicontrol.client.scene.SceneOverlay;
import com.dmzkicontrol.encounter.EncScript;
import com.dmzkicontrol.encounter.EncScript.Beat;
import com.dmzkicontrol.encounter.EncScript.Cue;
import com.dmzkicontrol.encounter.EncScript.Key;
import com.dmzkicontrol.encounter.EncScript.Kind;
import com.dmzkicontrol.encounter.EncounterDirector;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tela da luta: faixas de cinema, titulo da fase, falas, deixas de botao com o anel que fecha, carimbos de acerto,
 * painel do grupo (1 a 4), cabo de guerra, janela de transformacao, onomatopeias e os impact frames
 * (tela invertida, quadro preto com linhas de impacto, clarao) mais o tremor da camera.
 */
public final class EncHud implements IGuiOverlay {
    public static final EncHud INSTANCE = new EncHud();
    /** Cor de cada jogador (laranja, azul, verde, roxo). */
    public static final int[] ROLE_COLORS = {0xFF9A2E, 0x3FA9FF, 0x58E07A, 0xC77DFF};
    private static final int GOLD = 0xFFD54A, RED = 0xFF4A4A, GREEN = 0x6CFF8A, BOSS_TEXT = 0xE88BFF;

    private record Line(Component text, boolean boss, Component name, double at) {}

    private record Sfx(String txt, float x, float y, float tilt, double at, int color) {}

    private record Stamp(Component text, int color, double at, float x, float y, float scale) {}

    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Sfx> SFX = new ArrayList<>();
    private static final List<Stamp> STAMPS = new ArrayList<>();
    private static int impactLevel;
    private static double impactAt = -100;
    private static float shakeAmp;
    private static double shakeAt;
    private static double punchAt = -100;
    private static double hurtAt = -100;
    private static double phaseAt = -100;
    private static double mashPulse = -100;
    private static final Random R = new Random();

    private EncHud() {}

    private static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    // ------------------------------------------------------------------ gatilhos

    public static void reset() {
        LINES.clear();
        SFX.clear();
        STAMPS.clear();
        impactLevel = 0;
        shakeAmp = 0;
    }

    public static void onPhaseStart(EncClient.State s) {
        reset();
        phaseAt = now(0);
    }

    public static void subtitle(EncClient.State s, String key, boolean boss, Component name) {
        LINES.add(new Line(Component.translatable(key), boss, name, now(0)));
        while (LINES.size() > 2) LINES.remove(0);
    }

    public static void sfx(String txt) {
        float[][] spots = {{0.24f, 0.30f}, {0.74f, 0.26f}, {0.30f, 0.62f}, {0.70f, 0.58f}, {0.5f, 0.22f}};
        float[] p = spots[R.nextInt(spots.length)];
        SFX.add(new Sfx(txt, p[0], p[1], (R.nextFloat() - 0.5f) * 24f, now(0), R.nextBoolean() ? 0xFFFFFF : 0xFFE27A));
        while (SFX.size() > 4) SFX.remove(0);
    }

    public static void impact(int level) {
        if (level >= impactLevel || now(0) - impactAt > 6) {
            impactLevel = level;
            impactAt = now(0);
        }
        punchAt = now(0);
        shake(level * 1.4f);
    }

    public static void shake(float amp) {
        double since = now(0) - shakeAt;
        float left = shakeAmp * (float) Math.max(0, 1 - since / 12.0);
        shakeAmp = Math.max(left, amp);
        shakeAt = now(0);
    }

    public static void hurtFlash() {
        hurtAt = now(0);
    }

    public static void onMash() {
        mashPulse = now(0);
    }

    public static void onLocalAnswer(EncClient.State s, Cue c, byte res) {
        Component txt = switch (res) {
            case EncounterDirector.RES_PERFECT -> Component.translatable("enc.dmzkicontrol.perfect");
            case EncounterDirector.RES_HIT -> Component.translatable("enc.dmzkicontrol.good");
            default -> Component.translatable("enc.dmzkicontrol.miss");
        };
        int col = res == EncounterDirector.RES_PERFECT ? GOLD : res == EncounterDirector.RES_HIT ? GREEN : RED;
        STAMPS.add(new Stamp(txt, col, now(0), 0.5f, 0.55f, res == EncounterDirector.RES_PERFECT ? 2.0f : 1.6f));
        if (res == EncounterDirector.RES_PERFECT) punchAt = now(0);
    }

    public static void onCueResult(EncClient.State s, Cue c, byte[] res, byte sync) {
        if (c.mode == EncScript.Mode.ALL && s.n() > 1) {
            if (sync == 2) STAMPS.add(new Stamp(Component.translatable("enc.dmzkicontrol.sync", s.n()), GOLD, now(0), 0.5f, 0.36f, 2.6f));
            else if (sync == 1) STAMPS.add(new Stamp(Component.translatable("enc.dmzkicontrol.partial"), 0xFFA040, now(0), 0.5f, 0.36f, 1.8f));
        } else if (c.mode == EncScript.Mode.ALL && sync == 2) {
            STAMPS.add(new Stamp(Component.translatable("enc.dmzkicontrol.finisher"), GOLD, now(0), 0.5f, 0.36f, 2.4f));
        }
        while (STAMPS.size() > 5) STAMPS.remove(0);
    }

    public static void onCharged(EncClient.State s, int role, byte kind) {
        Component name = EncClient.nameOf(role);
        STAMPS.add(new Stamp(Component.translatable(kind == 1 ? "enc.dmzkicontrol.transformed" : "enc.dmzkicontrol.powered", name),
                kind == 1 ? GOLD : 0x8FE3FF, now(0), 0.5f, 0.30f + 0.06f * role, 1.5f));
        if (role == s.myRole) impact(2);
    }

    public static void tick(EncClient.State s) {
        double t = now(0);
        LINES.removeIf(l -> t - l.at > 80);
        SFX.removeIf(x -> t - x.at > 26);
        STAMPS.removeIf(x -> t - x.at > 22);
    }

    // ------------------------------------------------------------------ tremor / zoom

    public static float[] shakeOffset(float partial) {
        double since = now(partial) - shakeAt;
        float k = shakeAmp * (float) Math.max(0, 1 - since / 12.0);
        if (k <= 0.01f) return new float[]{0, 0, 0};
        double t = now(partial);
        return new float[]{(float) (Math.sin(t * 9.7) * k), (float) (Math.cos(t * 8.3) * k * 0.8), (float) (Math.sin(t * 6.1) * k * 0.6)};
    }

    /** Congela a camera logo depois de um impacto (1,5 a 4 ticks, conforme a forca). */
    public static boolean hitstop(float partial) {
        double since = now(partial) - impactAt;
        // o golpe ja segura a pose no contato (feito na animacao); a camera para junto
        return impactLevel >= 1 && since >= 0 && since < (impactLevel >= 3 ? 4.0 : impactLevel == 2 ? 3.0 : 1.5);
    }

    /** Tranco de inclinacao (camera "torta" estilo FighterZ) que volta sozinho depois do impacto. */
    public static float rollKick(float partial) {
        double since = now(partial) - punchAt;
        if (since < 0 || since > 10) return 0f;
        double u = since / 10.0;
        float side = ((int) punchAt & 1) == 0 ? 1f : -1f;
        return (float) (side * (2.0 + 2.0 * impactLevel) * Math.pow(1 - u, 2));
    }

    public static float fovPunch(float partial) {
        double since = now(partial) - punchAt;
        if (since < 0 || since > 8) return 1f;
        double u = since / 8.0;
        return (float) (1.0 - 0.10 * Math.sin(u * Math.PI) * (1 - u));
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        EncClient.State s = EncClient.state();
        if (s == null || !s.participant()) return;
        double now = now(partial);
        double t = s.time(partial);
        Beat beat = s.beat();

        // escurecer / vinheta da janela de transformacao
        if (beat.kind == Kind.CHARGE && s.variant == 0) {
            float k = (float) Math.min(1, Math.min(t / 10.0, (beat.len - t) / 10.0));
            g.fill(0, 0, w, h, DmzStyle.argb(0.22f * k, 0x000010));
            SceneOverlay.vignette(g, w, h, 0xFFC040, 0.55f * k);
        }
        // dano tomado: borda vermelha
        double hs = now - hurtAt;
        if (hs >= 0 && hs < 12) SceneOverlay.vignette(g, w, h, 0xFF0000, (float) (0.9 * (1 - hs / 12.0)));

        // faixas de cinema
        float bars = (float) Math.min(1, s.phaseTime(partial) / 8.0);
        int barH = Math.round(h * 0.10f * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);

        // titulo da fase
        double ps = now - phaseAt;
        if (ps >= 0 && ps < 70) {
            float a = (float) Math.min(1, Math.min(ps / 6.0, (70 - ps) / 10.0));
            float slide = (float) Math.max(0, 1 - ps / 8.0) * 40f;
            DmzStyle.text(g, Component.translatable(s.phase.title), w / 2f - slide, h * 0.40f, 3.0f, 0.5f, GOLD, a);
            DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.phase", s.phaseIdx + 1, s.script.phases.size()),
                    w / 2f + slide, h * 0.40f + 30, 1.0f, 0.5f, 0xFFFFFF, a * 0.85f);
        }
        // legenda da cena (o que fazer)
        if (beat.caption != null && barH > 6) {
            float a = (float) Math.min(1, t / 6.0);
            DmzStyle.text(g, Component.translatable(beat.caption), w / 2f, barH / 2f - 4, 1.1f, 0.5f, GOLD, a);
        }

        switch (beat.kind) {
            case PROMPT -> { if (s.variant == 0) prompts(g, s, beat, t, w, h); }
            case MASH -> { if (s.variant == 0) mash(g, s, beat, t, w, h); }
            case CHARGE -> { if (s.variant == 0) charge(g, s, beat, t, w, h); }
            default -> {}
        }
        if (s.n() > 1) party(g, s, beat, t, w, barH);

        // falas
        float ly = h - barH / 2f - 4;
        for (int i = LINES.size() - 1; i >= 0; i--) {
            Line l = LINES.get(i);
            double age = now - l.at;
            float a = (float) Math.min(1, Math.min(age / 4.0, (80 - age) / 12.0));
            Component c = l.boss ? Component.empty().append(l.name).append(": ").append(l.text) : l.text;
            DmzStyle.text(g, c, w / 2f, ly, l.boss ? 1.1f : 1.4f, 0.5f, l.boss ? BOSS_TEXT : 0xFFFFFF, a);
            ly -= 14;
        }
        // onomatopeias
        for (Sfx x : SFX) SceneOverlay.big(g, x.txt, w * x.x, h * x.y, 3.2f, x.tilt, (float) (now - x.at), 26, x.color);
        // carimbos
        for (Stamp st : STAMPS) {
            double age = now - st.at;
            float pop = (float) (1 + 0.6 * Math.max(0, 1 - age / 4.0));
            float a = (float) Math.min(1, (22 - age) / 8.0);
            DmzStyle.text(g, st.text, w * st.x, h * st.y - (float) age * 0.6f, st.scale * pop, 0.5f, st.color, a);
        }
        impactFrames(g, now, w, h);
    }

    // ------------------------------------------------------------------ deixas

    private static void prompts(GuiGraphics g, EncClient.State s, Beat beat, double t, int w, int h) {
        int slot = 0;
        for (Cue c : beat.cues) {
            int win = EncClient.window(c);
            if (t < c.t - c.lead || t > c.t + win + 8) continue;
            boolean mine = c.involves(s.myRole, s.n());
            Byte answered = s.mine.get(c.index);
            if (mine) {
                if (answered != null && now(0) - s.mineAt.get(c.index) > 6) continue;
                float cx = w / 2f + slot * 60f, cy = h * 0.68f;
                drawCue(g, c, EncClient.need(c), t, win, cx, cy, 1f, answered, ROLE_COLORS[s.myRole % 4]);
                slot++;
            } else if (c.mode == EncScript.Mode.TURN) {
                // vez do colega: mostra pequeno, do lado dele
                int r = c.role(s.n());
                Byte res = s.results.containsKey(c.index) ? s.results.get(c.index)[r] : null;
                if (res != null && now(0) - s.resultAt.get(c.index) > 6) continue;
                float side = EncScript.laneX(r, s.n()) >= EncScript.laneX(s.myRole, s.n()) ? 1 : -1;
                float cx = w / 2f + side * w * 0.22f, cy = h * 0.64f;
                drawCue(g, c, c.key == Key.DODGE ? Key.LEFT : c.key, t, win, cx, cy, 0.65f, res, ROLE_COLORS[r % 4]);
                DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.turn_of", EncClient.nameOf(r)), cx, cy + 26, 0.8f, 0.5f, ROLE_COLORS[r % 4], 0.9f);
            }
        }
    }

    private static void drawCue(GuiGraphics g, Cue c, Key key, double t, int win, float cx, float cy, float scale, Byte answered, int roleColor) {
        double dt = c.t - t;
        boolean inWin = Math.abs(dt) <= win;
        float base = 17f * scale;
        float appear = (float) Mth.clamp((t - (c.t - c.lead)) / 4.0, 0, 1);
        // anel que fecha
        if (answered == null) {
            float k = (float) Mth.clamp(dt / c.lead, 0, 1);
            float rr = base * (1f + 2.2f * k);
            int ringCol = inWin ? DmzStyle.argb(0.95f * appear, GOLD) : DmzStyle.argb(0.75f * appear, 0xFFFFFF);
            ring(g, cx, cy, rr, inWin ? 3.2f * scale : 2f * scale, ringCol);
            if (c.mode == EncScript.Mode.ALL) ring(g, cx, cy, rr + 5 * scale, 1.2f * scale, DmzStyle.argb(0.5f * appear, GOLD));
        }
        // tecla
        int fill = answered == null ? 0x1A1A2A : answered >= EncounterDirector.RES_HIT ? 0x2B5A2B : 0x5A1E1E;
        disc(g, cx, cy, base + 2 * scale, DmzStyle.argb(0.85f * appear, roleColor));
        disc(g, cx, cy, base, DmzStyle.argb(0.92f * appear, fill));
        if (inWin && answered == null) disc(g, cx, cy, base * 0.85f, DmzStyle.argb(0.25f * appear * (float) (0.6 + 0.4 * Math.sin(t * 2.5)), GOLD));
        DmzStyle.text(g, Component.literal(glyph(key)), cx, cy - 5 * scale * 1.4f, 1.7f * scale, 0.5f, 0xFFFFFF, appear);
        DmzStyle.text(g, keyName(key), cx, cy + base + 5, 1.15f * scale, 0.5f, 0xFFFFFF, appear);
        if (c.mode == EncScript.Mode.ALL && scale >= 1f) {
            DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.together"), cx, cy - base * 3.4f, 1.0f, 0.5f, GOLD, appear);
        }
    }

    private static String glyph(Key k) {
        return switch (k) {
            case LEFT, DODGE -> "←";
            case RIGHT -> "→";
            case UP -> "↑";
            case DOWN -> "↓";
            case JUMP -> "⇑";
            case ATTACK -> "✦";
        };
    }

    public static Component keyName(Key k) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping km = switch (k) {
            case LEFT, DODGE -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
            case UP -> mc.options.keyUp;
            case DOWN -> mc.options.keyDown;
            case JUMP -> mc.options.keyJump;
            case ATTACK -> mc.options.keyAttack;
        };
        return km.getTranslatedKeyMessage();
    }

    // ------------------------------------------------------------------ painel do grupo

    private static void party(GuiGraphics g, EncClient.State s, Beat beat, double t, int w, int barH) {
        float x = 8, y = barH + 8;
        for (int r = 0; r < s.n(); r++) {
            int col = ROLE_COLORS[r % 4];
            g.fill((int) x, (int) y + 1, (int) x + 4, (int) y + 9, 0xFF000000 | col);
            Component name = EncClient.nameOf(r);
            String status = "";
            int stCol = 0xAAAAAA;
            if (beat.kind == Kind.PROMPT) {
                Cue last = null;
                for (Cue c : beat.cues) if (c.involves(r, s.n()) && c.t - c.lead <= t) last = c;
                if (last != null) {
                    byte[] res = s.results.get(last.index);
                    Byte mine = r == s.myRole ? s.mine.get(last.index) : null;
                    byte v = res != null ? res[r] : (mine != null ? mine.byteValue() : (byte) 0);
                    if (v == 0) { status = "…"; stCol = 0xCCCCCC; }
                    else if (v >= EncounterDirector.RES_PERFECT) { status = "★"; stCol = GOLD; }
                    else if (v >= EncounterDirector.RES_HIT) { status = "✔"; stCol = GREEN; }
                    else { status = "✖"; stCol = RED; }
                }
            } else if (beat.kind == Kind.CHARGE) {
                byte c = s.charged[r];
                status = c == 1 ? "⚡" : c == 2 ? "▲" : "…";
                stCol = c == 1 ? GOLD : c == 2 ? 0x8FE3FF : 0xCCCCCC;
            } else if (beat.kind == Kind.MASH && r < s.mashRecent.length) {
                int k = Math.min(8, s.mashRecent[r]);
                g.fill((int) x + 70, (int) y + 3, (int) x + 70 + k * 5, (int) y + 7, 0xFF000000 | col);
            }
            DmzStyle.text(g, name, x + 8, y, 0.85f, 0f, r == s.myRole ? 0xFFFFFF : 0xCFCFCF, 0.95f);
            if (!status.isEmpty()) DmzStyle.text(g, Component.literal(status), x + 64, y, 0.95f, 0.5f, stCol, 1f);
            y += 12;
        }
    }

    // ------------------------------------------------------------------ cabo de guerra

    private static void mash(GuiGraphics g, EncClient.State s, Beat beat, double t, int w, int h) {
        float bw = w * 0.6f, bh = 12f;
        float x0 = (w - bw) / 2f, y0 = h * 0.76f;
        float p = Mth.clamp(s.progressShown, 0f, 1f);
        float split = x0 + bw * p;
        g.fill((int) x0 - 2, (int) y0 - 2, (int) (x0 + bw) + 2, (int) (y0 + bh) + 2, 0xC0000000);
        int playerCol = 0x4FB8FF, bossCol = s.script.id.startsWith("frieza") ? 0xD24BFF : 0x46FFD0;
        g.fill((int) x0, (int) y0, (int) split, (int) (y0 + bh), 0xFF000000 | playerCol);
        g.fill((int) split, (int) y0, (int) (x0 + bw), (int) (y0 + bh), 0xFF000000 | bossCol);
        // brilho correndo
        float shine = (float) ((t * 6) % bw);
        g.fill((int) (x0 + Math.min(shine, split - x0)), (int) y0, (int) (x0 + Math.min(shine + 10, split - x0)), (int) (y0 + bh), 0x60FFFFFF);
        float jit = (float) Math.sin(t * 3.1) * 1.5f;
        disc(g, split + jit, y0 + bh / 2f, 9f + (float) Math.sin(t * 1.7) * 1.5f, 0xEEFFFFFF);
        disc(g, split + jit, y0 + bh / 2f, 14f, 0x55FFFFFF);
        double pulse = now(0) - mashPulse;
        float k = pulse >= 0 && pulse < 4 ? 1.15f : 1f;
        if (s.mashResult < 0) {
            Component msg = Component.translatable("enc.dmzkicontrol.mash_hint", keyName(Key.ATTACK), keyName(Key.JUMP));
            DmzStyle.text(g, msg, w / 2f, y0 - 18, 1.1f * k, 0.5f, 0xFFFFFF, 0.8f + 0.2f * (float) Math.sin(t * 1.4));
            for (int[] su : beat.surges) {
                if (t >= su[0] && t < su[1]) DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.surge"), w / 2f, y0 + bh + 8, 1.0f, 0.5f, RED, 1f);
            }
        } else {
            DmzStyle.text(g, Component.translatable(s.mashResult == 1 ? "enc.dmzkicontrol.clash_win" : "enc.dmzkicontrol.clash_lose"),
                    w / 2f, y0 - 22, 2.0f, 0.5f, s.mashResult == 1 ? GOLD : RED, 1f);
        }
    }

    // ------------------------------------------------------------------ janela de transformacao

    private static void charge(GuiGraphics g, EncClient.State s, Beat beat, double t, int w, int h) {
        float a = (float) Math.min(1, t / 8.0);
        DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.window"), w / 2f, h * 0.30f, 2.2f, 0.5f, GOLD, a);
        KeyMapping action = EncClient.dmzKey("key.dragonminez.action_key");
        KeyMapping ki = EncClient.dmzKey("key.dragonminez.ki_charge");
        Component act = action != null ? action.getTranslatedKeyMessage() : Component.literal("G");
        Component kiName = ki != null ? ki.getTranslatedKeyMessage() : keyName(Key.ATTACK);
        byte mine = s.charged[s.myRole];
        if (mine == 0) {
            DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.window_transform", act), w / 2f, h * 0.30f + 26, 1.0f, 0.5f, 0xFFFFFF, a);
            DmzStyle.text(g, Component.translatable("enc.dmzkicontrol.window_charge", kiName), w / 2f, h * 0.30f + 40, 0.9f, 0.5f, 0xBFE8FF, a * 0.9f);
        }
        // tempo restante
        float left = (float) Mth.clamp(1 - t / beat.len, 0, 1);
        float bw = w * 0.4f;
        float x0 = (w - bw) / 2f, y0 = h * 0.30f + 56;
        g.fill((int) x0, (int) y0, (int) (x0 + bw), (int) y0 + 3, 0x80000000);
        g.fill((int) x0, (int) y0, (int) (x0 + bw * left), (int) y0 + 3, 0xFF000000 | GOLD);
    }

    // ------------------------------------------------------------------ impact frames

    private static void impactFrames(GuiGraphics g, double now, int w, int h) {
        double since = now - impactAt;
        if (impactLevel <= 0 || since < 0 || since > 14) return;
        int seed = (int) impactAt;
        switch (impactLevel) {
            case 1 -> {
                if (since < 3) g.fill(0, 0, w, h, DmzStyle.argb((float) (0.45 * (1 - since / 3.0)), 0xFFFFFF));
            }
            case 2 -> {
                if (since < 1.5) invert(g, w, h);
                else if (since < 6) g.fill(0, 0, w, h, DmzStyle.argb((float) (0.5 * (1 - (since - 1.5) / 4.5)), 0xFFFFFF));
                if (since < 8) SceneOverlay.focusLines(g, w, h, (float) (1 - since / 8.0), seed, 0xFFFFFF);
            }
            default -> {
                if (since < 1.2) invert(g, w, h);
                else if (since < 2.6) {
                    g.fill(0, 0, w, h, 0xFF000000);
                    SceneOverlay.focusLines(g, w, h, 1f, seed + 3, 0xFFFFFF);
                    g.fill(0, 0, w, h, 0x50FF0000);
                } else if (since < 3.6) invert(g, w, h);
                else if (since < 10) g.fill(0, 0, w, h, DmzStyle.argb((float) (0.6 * (1 - (since - 3.6) / 6.4)), 0xFFFFFF));
                if (since > 2.6 && since < 14) SceneOverlay.focusLines(g, w, h, (float) (1 - (since - 2.6) / 11.4), seed + 7, 0xFFFFFF);
            }
        }
    }

    /** Inverte as cores da tela inteira (o "impact frame" de anime). */
    private static void invert(GuiGraphics g, int w, int h) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(m, 0, h, 0).color(1f, 1f, 1f, 1f).endVertex();
        b.vertex(m, w, h, 0).color(1f, 1f, 1f, 1f).endVertex();
        b.vertex(m, w, 0, 0).color(1f, 1f, 1f, 1f).endVertex();
        b.vertex(m, 0, 0, 0).color(1f, 1f, 1f, 1f).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ formas

    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        Matrix4f m = g.pose().last().pose();
        float a = ((argb >>> 24) & 0xFF) / 255f, rr = ((argb >> 16) & 0xFF) / 255f, gg = ((argb >> 8) & 0xFF) / 255f, bb = (argb & 0xFF) / 255f;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(m, cx, cy, 0).color(rr, gg, bb, a).endVertex();
        int seg = 32;
        for (int i = seg; i >= 0; i--) {
            double ang = i * Math.PI * 2 / seg;
            b.vertex(m, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, 0).color(rr, gg, bb, a).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }

    static void ring(GuiGraphics g, float cx, float cy, float r, float thick, int argb) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        Matrix4f m = g.pose().last().pose();
        float a = ((argb >>> 24) & 0xFF) / 255f, rr = ((argb >> 16) & 0xFF) / 255f, gg = ((argb >> 8) & 0xFF) / 255f, bb = (argb & 0xFF) / 255f;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int seg = 48;
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            b.vertex(m, cx + c * (r + thick / 2), cy + sn * (r + thick / 2), 0).color(rr, gg, bb, a).endVertex();
            b.vertex(m, cx + c * (r - thick / 2), cy + sn * (r - thick / 2), 0).color(rr, gg, bb, a).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }
}
