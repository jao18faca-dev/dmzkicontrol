package com.dmzkicontrol.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Config do cliente (config/dmzkicontrol-client.toml): so visual. */
public final class KiControlClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final KiControlClientConfig INSTANCE;

    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ABOVE_HOTBAR }

    public final ForgeConfigSpec.BooleanValue showHud;
    public final ForgeConfigSpec.BooleanValue showWhenIdle;
    public final ForgeConfigSpec.EnumValue<Anchor> anchor;
    public final ForgeConfigSpec.IntValue offsetX;
    public final ForgeConfigSpec.IntValue offsetY;

    static {
        Pair<KiControlClientConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(KiControlClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private KiControlClientConfig(ForgeConfigSpec.Builder b) {
        b.push("hud");
        showHud = b.comment("Mostra o indicador do Selamento de Aura.").define("showHud", true);
        showWhenIdle = b.comment("Mostra o indicador mesmo com o selo desligado (enquanto transformado).")
                .define("showWhenIdle", true);
        anchor = b.comment("Canto da tela onde o indicador fica.").defineEnum("anchor", Anchor.ABOVE_HOTBAR);
        offsetX = b.defineInRange("offsetX", 0, -2000, 2000);
        offsetY = b.defineInRange("offsetY", 0, -2000, 2000);
        b.pop();
    }
}
