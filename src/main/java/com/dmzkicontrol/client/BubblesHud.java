package com.dmzkicontrol.client;

import com.dmzkicontrol.network.BubblesHudS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** HUD do treino do Bubbles: nivel, cronometro e distancia; "PEGOU!" ou "ESCAPOU!" no fim. */
public final class BubblesHud {
    private static final int GOLD = 0xFFC94A, WHITE = 0xFFFFFF, RED = 0xFF5A4A, GREEN = 0x7CFF6B;
    private static int state = BubblesHudS2C.NONE;
    private static int level;
    private static int ticksLeft;
    private static int total = 1;
    private static int bubblesId = -1;
    private static int resultTicks;

    private BubblesHud() {}

    public static void onPacket(int st, int lvl, int left, int tot, int id) {
        state = st;
        level = lvl;
        ticksLeft = left;
        total = Math.max(1, tot);
        bubblesId = id;
        resultTicks = st == BubblesHudS2C.WIN || st == BubblesHudS2C.FAIL ? 60 : 0;
    }

    public static void clientTick() {
        if (state == BubblesHudS2C.RUNNING && ticksLeft > 0) ticksLeft--;
        if (resultTicks > 0 && --resultTicks == 0) state = BubblesHudS2C.NONE;
    }

    public static void reset() {
        state = BubblesHudS2C.NONE;
        resultTicks = 0;
    }

    public static final IGuiOverlay OVERLAY = (gui, g, partial, w, h) -> {
        if (state == BubblesHudS2C.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float cx = w / 2f;
        DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.bubbles_title", level), cx, 10f, 1.0f, 0.5f, GOLD, 1f);
        if (state == BubblesHudS2C.RUNNING) {
            int secs = (ticksLeft + 19) / 20;
            float urgent = secs <= 10 ? 0.5f + 0.5f * Mth.sin((mc.player.tickCount + partial) * 0.6f) : 0f;
            int col = DmzStyle.mix(WHITE, RED, secs <= 10 ? 0.6f + 0.4f * urgent : 0f);
            DmzStyle.text(g, Component.literal(String.format("%d:%02d", secs / 60, secs % 60)), cx, 22f, 2.0f + urgent * 0.15f, 0.5f, col, 1f);
            Entity b = mc.level == null || bubblesId < 0 ? null : mc.level.getEntity(bubblesId);
            if (b != null) {
                int dist = Math.round(mc.player.distanceTo(b));
                DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.bubbles_distance", dist), cx, 42f, 0.9f, 0.5f, WHITE, 0.9f);
            }
            // barra de tempo
            int bw = 120, bx = (int) cx - bw / 2, by = 54;
            float frac = Mth.clamp(ticksLeft / (float) total, 0f, 1f);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xAA000000);
            g.fill(bx, by, bx + Math.round(bw * frac), by + 3, DmzStyle.argb(1f, col == WHITE ? GOLD : col));
        } else {
            boolean win = state == BubblesHudS2C.WIN;
            float t = resultTicks / 60f;
            float pop = 1f + 0.3f * Math.max(0f, (t - 0.8f) / 0.2f);
            DmzStyle.text(g, Component.translatable(win ? "hud.dmzkicontrol.bubbles_win" : "hud.dmzkicontrol.bubbles_fail"),
                    cx, h * 0.3f, 2.6f * pop, 0.5f, win ? GREEN : RED, Math.min(1f, t * 3f));
        }
    };

}
