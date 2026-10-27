package com.dmzkicontrol.client;

import com.dmzkicontrol.network.SealStateS2C;
import net.minecraft.client.Minecraft;

/** Ultimo estado do selo recebido do servidor (so cliente). */
public final class ClientSealState {
    public static boolean sealed;
    public static boolean pending;
    public static String formKey = "";
    public static boolean hasSkill = true;
    public static float mastery;
    public static float kiReduction;
    public static float powerBonus;
    /** Tensao 0..1. */
    public static float strain;
    public static boolean perfect;
    public static float fatigueAt = 0.5f;
    public static float hurtAt = 0.9f;
    private static long burstEndTick;
    private static boolean everReceived;

    private ClientSealState() {}

    public static void update(SealStateS2C m) {
        sealed = m.sealed();
        pending = m.pending();
        formKey = m.formKey();
        hasSkill = m.hasSkill();
        mastery = m.mastery();
        kiReduction = m.kiReduction();
        powerBonus = m.powerBonus();
        strain = m.strain();
        perfect = m.perfect();
        fatigueAt = m.fatigueAt();
        hurtAt = m.hurtAt();
        burstEndTick = now() + m.burstTicks();
        everReceived = true;
    }

    public static boolean hasData() {
        return everReceived;
    }

    public static int burstTicksLeft() {
        return (int) Math.max(0, burstEndTick - now());
    }

    public static void reset() {
        sealed = false;
        pending = false;
        formKey = "";
        strain = 0f;
        perfect = false;
        burstEndTick = 0;
        everReceived = false;
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }
}
