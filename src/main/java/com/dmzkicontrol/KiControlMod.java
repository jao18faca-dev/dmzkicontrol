package com.dmzkicontrol;

import com.dmzkicontrol.config.KiControlClientConfig;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.KiControlNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * DMZ Ki Control - Controle Perfeito de Aura.
 * Add-on para DragonMineZ (Forge 1.20.1): prende a aura dentro do corpo para reduzir
 * o gasto de ki das transformacoes (principio do Goku na saga Black com o SSJ Blue).
 */
@Mod(KiControlMod.MODID)
public class KiControlMod {
    public static final String MODID = "dmzkicontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KiControlMod() {
        KiControlConfig.load();
        com.dmzkicontrol.race.RaceInstaller.installAll();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, KiControlClientConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(KiControlNetwork::register);
        String dmzVersion = ModList.get().getModContainerById("dragonminez")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        LOGGER.info("DMZ Ki Control ativo - integrado ao DragonMineZ {}", dmzVersion);
    }
}
