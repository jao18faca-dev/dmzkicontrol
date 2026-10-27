package com.dmzkicontrol.control;

import com.dmzkicontrol.config.KiControlConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dominio do selo por forma, salvo nos dados persistentes do jogador (sobrevive a morte e
 * fica separado dos dados do DMZ - nao mexemos no save do DMZ).
 * Guardamos "segundos de pratica efetivos" por chave "grupo:forma".
 */
public final class MasteryStore {
    private static final String NBT_ROOT = "dmzkicontrol";
    private static final String NBT_TRAINED = "trained";

    private MasteryStore() {}

    private static CompoundTag modTag(Player p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getCompound(NBT_ROOT);
    }

    private static void writeModTag(Player p, CompoundTag mod) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(NBT_ROOT, mod);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static double getTrained(Player p, String key) {
        return modTag(p).getCompound(NBT_TRAINED).getDouble(key);
    }

    public static void setTrained(Player p, String key, double seconds) {
        CompoundTag mod = modTag(p);
        CompoundTag trained = mod.getCompound(NBT_TRAINED);
        if (seconds <= 0) trained.remove(key);
        else trained.putDouble(key, seconds);
        mod.put(NBT_TRAINED, trained);
        writeModTag(p, mod);
    }

    public static void addTrained(Player p, String key, double seconds) {
        setTrained(p, key, getTrained(p, key) + seconds);
    }

    public static Map<String, Double> all(Player p) {
        Map<String, Double> out = new LinkedHashMap<>();
        CompoundTag trained = modTag(p).getCompound(NBT_TRAINED);
        for (String k : trained.getAllKeys()) out.put(k, trained.getDouble(k));
        return out;
    }

    public static void resetAll(Player p) {
        CompoundTag mod = modTag(p);
        mod.remove(NBT_TRAINED);
        writeModTag(p, mod);
    }

    /** Copia o progresso para a nova entidade do jogador (morte / volta do End). */
    public static void copy(Player from, Player to) {
        CompoundTag old = from.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(NBT_ROOT)) return;
        writeModTag(to, old.getCompound(NBT_ROOT).copy());
    }

    /** Segundos de pratica necessarios para 100% nesta forma. */
    public static double secondsForFull(String key) {
        KiControlConfig c = KiControlConfig.get();
        double mul = Math.max(0.05, c.tierFor(key).trainTimeMultiplier);
        return Math.max(1.0, c.trainSeconds * mul);
    }

    /** Dominio 0..1 nesta forma. */
    public static double mastery(Player p, String key) {
        if (key == null) return 0.0;
        KiControlConfig c = KiControlConfig.get();
        double frac = Math.min(1.0, getTrained(p, key) / secondsForFull(key));
        double start = clamp01(c.startingMastery);
        return clamp01(start + (1.0 - start) * frac);
    }

    /** Define o dominio (0..1) convertendo para segundos de pratica. */
    public static void setMastery(Player p, String key, double mastery) {
        KiControlConfig c = KiControlConfig.get();
        double start = clamp01(c.startingMastery);
        double m = clamp01(mastery);
        double frac = start >= 1.0 ? 1.0 : Math.max(0.0, (m - start) / (1.0 - start));
        setTrained(p, key, frac * secondsForFull(key));
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
