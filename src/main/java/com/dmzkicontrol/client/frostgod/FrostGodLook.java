package com.dmzkicontrol.client.frostgod;

import com.dmzkicontrol.config.KiControlClientConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/** Qual das duas formas divinas do Freeza o jogador esta usando (forma principal ou forma somada). */
public enum FrostGodLook {
    GOLDEN, BLACK;

    public static final String GROUP = "frostgodforms";

    public static FrostGodLook of(Player player) {
        if (player == null || player.isInvisible()) return null;
        try {
            if (!KiControlClientConfig.INSTANCE.frostGodVisuals.get()) return null;
        } catch (Throwable ignored) {}
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
            if (data == null) return null;
            Character c = data.getCharacter();
            FrostGodLook look = null;
            if (c.hasActiveStackForm()) look = match(c.getActiveStackFormGroup(), c.getActiveStackForm());
            if (look == null && c.hasActiveForm()) look = match(c.getActiveFormGroup(), c.getActiveForm());
            return look;
        } catch (Throwable t) {
            return null;
        }
    }

    private static FrostGodLook match(String group, String form) {
        if (group == null || form == null || !GROUP.equals(group.toLowerCase(Locale.ROOT))) return null;
        return switch (form.toLowerCase(Locale.ROOT)) {
            case "golden" -> GOLDEN;
            case "black" -> BLACK;
            default -> null;
        };
    }
}
