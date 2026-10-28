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
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        com.dmzkicontrol.registry.ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);
    }

    /** Depois que todos os mods instalaram suas formas (Sparking instala no setup): curadoria de formas. */
    private void loadComplete(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            if (com.dmzkicontrol.compat.FormCurator.run("load")) {
                try {
                    com.dragonminez.common.config.ConfigManager.reload();
                } catch (Throwable t) {
                    LOGGER.warn("Curadoria aplicada, mas o DMZ nao recarregou agora ({}); vale no proximo inicio do mundo.", t.toString());
                }
            }
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(KiControlNetwork::register);
        event.enqueueWork(com.dmzkicontrol.cerealian.KiArrowTechnique::register);
        String dmzVersion = ModList.get().getModContainerById("dragonminez")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        LOGGER.info("DragonMineZ Evolution ativo - integrado ao DragonMineZ {}", dmzVersion);
    }
}
