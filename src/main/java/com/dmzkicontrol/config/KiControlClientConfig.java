package com.dmzkicontrol.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Config do cliente (config/dmzkicontrol-client.toml): visual e cutscene. */
public final class KiControlClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final KiControlClientConfig INSTANCE;

    public enum CutsceneMode { ALWAYS, ONCE_PER_SESSION, OFF }
    public enum MeterSide { RIGHT, LEFT }
    /** HUD com o DMZ Ultra: ASK (pergunta ao entrar no mundo), ULTRA (a dele) ou DMZ (a original do DragonMineZ). */
    public enum UltraHud { ASK, ULTRA, DMZ }

    public final ForgeConfigSpec.EnumValue<CutsceneMode> cutscene;
    public final ForgeConfigSpec.BooleanValue showHud;
    public final ForgeConfigSpec.EnumValue<MeterSide> meterSide;
    public final ForgeConfigSpec.IntValue offsetX;
    public final ForgeConfigSpec.IntValue offsetY;
    public final ForgeConfigSpec.DoubleValue scale;
    public final ForgeConfigSpec.BooleanValue vignette;
    public final ForgeConfigSpec.EnumValue<UltraHud> ultraHud;

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
        scale = b.comment("Tamanho do medidor (1.0 = mesmo tamanho dos medidores do DMZ).").defineInRange("meterScale", 1.6, 0.5, 4.0);
        vignette = b.comment("Brilho suave nas bordas da tela enquanto selado (fica vermelho com a tensao alta).")
                .define("vignette", true);
        b.pop();

        b.push("ultra");
        ultraHud = b.comment("So com o DMZ Ultra instalado. Qual HUD usar:",
                        "ASK   - pergunta na primeira vez que voce entra num mundo (padrao)",
                        "ULTRA - a HUD do DMZ Ultra (moldura com retrato, barras de vidro, menus redesenhados)",
                        "DMZ   - a HUD e os menus originais do DragonMineZ",
                        "Para trocar no jogo: comando /dmzhud ou Mods > DragonMineZ Evolution > Config.")
                .defineEnum("hud", UltraHud.ASK);
        b.pop();
    }
}
