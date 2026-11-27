package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Com o DMZ Sparking, as paginas de missoes dele (Agora, Historia, Episodios, Missoes extras) trocam a arvore de
 * missoes do DragonMineZ. Aqui a arvore do DragonMineZ volta: abrir a arvore do DMZ abre ela mesma, e abrir uma
 * pagina de missoes do Sparking abre a arvore do DMZ. As outras paginas do Sparking ficam como estao.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KeepDmzQuestMenu {
    private static final String TREE = "com.dragonminez.client.gui.character.QuestTreeScreen";
    private static final Set<String> SPARKING_QUEST_PAGES = Set.of(
            "com.yami.dmzsparking.client.menu.StoryPage",
            "com.yami.dmzsparking.client.menu.EpisodesPage",
            "com.yami.dmzsparking.client.menu.SideQuestsPage",
            "com.yami.dmzsparking.client.menu.OverhaulQuests",
            "com.yami.dmzsparking.client.menu.frame.NowPage");
    private static Screen wanted;

    private KeepDmzQuestMenu() {}

    private static boolean on() {
        try {
            return KiControlConfig.get().story.keepDmzQuestMenu;
        } catch (Throwable t) {
            return true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void first(ScreenEvent.Opening event) {
        wanted = null;
        Screen s = event.getNewScreen();
        if (s != null && on() && s.getClass().getName().equals(TREE)) wanted = s;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void last(ScreenEvent.Opening event) {
        if (!on()) return;
        Screen now = event.getNewScreen();
        if (wanted != null) {
            if (now != wanted) event.setNewScreen(wanted);
            wanted = null;
            return;
        }
        if (now != null && SPARKING_QUEST_PAGES.contains(now.getClass().getName())) {
            try {
                event.setNewScreen(new com.dragonminez.client.gui.character.QuestTreeScreen());
            } catch (Throwable t) {
                KiControlMod.LOGGER.debug("Arvore de missoes do DMZ nao abriu: {}", t.toString());
            }
        }
    }
}
