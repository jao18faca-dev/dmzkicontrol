package com.dmzkicontrol.client;

import com.dmzkicontrol.network.SealStateS2C;
import net.minecraft.client.Minecraft;

/** Ultimo estado do selo recebido do servidor (so cliente). */
public final class ClientSealState {
    public static boolean sealed;
    public static String formKey = "";
    public static boolean hasSkill = true;
    public static float mastery;
    public static float kiReduction;
    public static float powerBonus;
    /** fracao do limite de tensao (0..2); -1 = controle perfeito, sem tensao. */
    public static float strain = -1f;
    /** multiplo do limite em que o selo quebra. */
    public static float breakAt = 2f;
    private static long burstEndTick;
    private static boolean everReceived;

    private ClientSealState() {}

    public static void update(SealStateS2C m) {
        sealed = m.sealed();
        formKey = m.formKey();
        hasSkill = m.hasSkill();
        mastery = m.mastery();
        kiReduction = m.kiReduction();
        powerBonus = m.powerBonus();
        strain = m.strain();
        breakAt = m.breakAt();
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
        formKey = "";
        strain = -1f;
        burstEndTick = 0;
        everReceived = false;
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }
}
