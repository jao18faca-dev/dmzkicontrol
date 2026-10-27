package com.dmzkicontrol.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Config do cliente (config/dmzkicontrol-client.toml): visual e cutscene. */
public final class KiControlClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final KiControlClientConfig INSTANCE;

    public enum CutsceneMode { ALWAYS, ONCE_PER_SESSION, OFF }
    public enum MeterSide { RIGHT, LEFT }

    public final ForgeConfigSpec.EnumValue<CutsceneMode> cutscene;
    public final ForgeConfigSpec.BooleanValue showHud;
    public final ForgeConfigSpec.EnumValue<MeterSide> meterSide;
    public final ForgeConfigSpec.IntValue offsetX;
    public final ForgeConfigSpec.IntValue offsetY;
    public final ForgeConfigSpec.DoubleValue scale;
    public final ForgeConfigSpec.BooleanValue vignette;

    static {
        Pair<KiControlClientConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(KiControlClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private KiControlClientConfig(ForgeConfigSpec.Builder b) {
        b.push("cutscene");
        cutscene = b.comment("Cutscene ao selar a aura: ALWAYS (sempre), ONCE_PER_SESSION (so a primeira vez",
                        "em cada sessao) ou OFF (desligada: o personagem so leva a mao ao peito e sela).",
                        "Tambem da para alternar clicando com o botao direito em 'Selar Aura' no Menu Utilitario.")
                .defineEnum("mode", CutsceneMode.ALWAYS);
        b.pop();

        b.push("hud");
        showHud = b.comment("Mostra o medidor de tensao do selo (estilo dos medidores laterais do DMZ).").define("showHud", true);
        meterSide = b.comment("Lado da tela do medidor (os medidores do DMZ ficam na esquerda).").defineEnum("side", MeterSide.RIGHT);
        offsetX = b.defineInRange("offsetX", 0, -2000, 2000);
        offsetY = b.defineInRange("offsetY", 0, -2000, 2000);
        scale = b.defineInRange("scale", 1.0, 0.5, 3.0);
        vignette = b.comment("Brilho suave nas bordas da tela enquanto selado (fica vermelho com a tensao alta).")
                .define("vignette", true);
        b.pop();
    }
}
