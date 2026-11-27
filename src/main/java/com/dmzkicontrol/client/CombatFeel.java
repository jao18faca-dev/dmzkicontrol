package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.network.CombatFxS2C;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sensacao dos golpes do combate dinamico: tremida e um "soco" de zoom no golpe pesado, puxao de camera no
 * zanzoken e na perseguicao (como os jogos de luta de Dragon Ball). Tudo curto, para nao enjoar.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatFeel {
    private static long at = -100;
    private static int kind = -1;

    private CombatFeel() {}

    public static void play(int k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        kind = k;
        at = mc.level.getGameTime();
    }

    private static double age(double partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 999 : mc.level.getGameTime() + partial - at;
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        double a = age(event.getPartialTick());
        if (a > 12 || kind < 0) return;
        double k = Math.max(0, 1 - a / 12.0);
        switch (kind) {
            case CombatFxS2C.SMASH -> {
                float amp = (float) (2.2 * k * k);
                event.setPitch(event.getPitch() + amp * (float) Math.sin(a * 3.1));
                event.setYaw(event.getYaw() + amp * (float) Math.cos(a * 2.3));
                event.setRoll(event.getRoll() + amp * 0.8f * (float) Math.sin(a * 1.7));
            }
            case CombatFxS2C.VANISH, CombatFxS2C.CHASE -> event.setRoll(event.getRoll() + (float) (6 * k * k) * (kind == CombatFxS2C.CHASE ? -1 : 1));
            case CombatFxS2C.ENEMY_VANISH -> event.setYaw(event.getYaw() + (float) (3 * k * Math.sin(a * 2.5)));
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        double a = age(event.getPartialTick());
        if (a > 10 || kind < 0) return;
        double k = Math.max(0, 1 - a / 10.0);
        double punch = switch (kind) {
            case CombatFxS2C.SMASH -> -8 * k * k;
            case CombatFxS2C.VANISH, CombatFxS2C.CHASE -> 12 * k * k;
            default -> 0;
        };
        event.setFOV(event.getFOV() + punch);
    }
}
