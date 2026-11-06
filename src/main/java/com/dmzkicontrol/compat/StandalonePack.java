package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Os NPCs de saga (Jaco na Casa do Kame, Merus e Granolah na casa do Goku) e o Planeta Cereal na nave
 * espacial so entram quando as sagas do Moro e do Granolah sao as deste add-on. Com o DMZ Ultra, que tem
 * os mesmos NPCs e o proprio Planeta Cereal, este pacote fica de fora (senao aparecem dois de cada).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StandalonePack {
    private static final String FOLDER = "dmzkicontrol_standalone";

    private StandalonePack() {}

    @SubscribeEvent
    public static void onAddPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        if (UltraCompat.ultraSagas()) {
            KiControlMod.LOGGER.info("[Sagas] DMZ Ultra instalado: NPCs de saga e Planeta Cereal deste add-on ficam de fora (os do Ultra valem).");
            return;
        }
        if (!KiControlConfig.get().sagas.installMoroAndGranolah) return;
        try {
            Path root = ModList.get().getModFileById(KiControlMod.MODID).getFile().findResource(FOLDER);
            if (root == null || !Files.exists(root)) return;
            event.addRepositorySource(consumer -> {
                Pack pack = Pack.readMetaAndCreate("dmzkicontrol:sagas", Component.literal("DragonMineZ Evolution - sagas"), true,
                        id -> new PathPackResources(id, root, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
                if (pack != null) consumer.accept(pack);
            });
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Sagas] pacote dos NPCs de saga nao registrado: {}", t.toString());
        }
    }
}
