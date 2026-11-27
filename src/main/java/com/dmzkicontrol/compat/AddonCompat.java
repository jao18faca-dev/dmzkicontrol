package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ponte com outros add-ons do DragonMineZ, sem depender deles para compilar nem para rodar:
 * <ul>
 *   <li><b>DMZ Sparking</b> (Yami) - meditacao, sagas do Super (Beerus, Black...), Blue Aperfeicoado;</li>
 *   <li><b>Dragon Block Noea</b> (ButterJaffa) - formas extras (Rose, Beast...).</li>
 * </ul>
 * Tudo por reflexao: se o add-on nao estiver instalado (ou mudar), a integracao so desliga.
 */
public final class AddonCompat {
    public static final String SPARKING = "dmzsparking";
    public static final String NOEA = "noeabosses";

    private static Method meditating;
    private static boolean meditatingTried;

    private AddonCompat() {}

    public static boolean sparking() {
        return ModList.get() != null && ModList.get().isLoaded(SPARKING);
    }

    public static boolean noea() {
        return ModList.get() != null && ModList.get().isLoaded(NOEA);
    }

    private static Boolean sparkingStory;

    /**
     * A historia do DMZ Sparking esta ligada (modulo "story" do config/dmzsparking/common.toml, ligado por padrao)?
     * Nela o proprio Sparking conta cenas como a morte do Kuririn em Namek.
     */
    public static boolean sparkingStory() {
        if (sparkingStory != null) return sparkingStory;
        boolean on = sparking();
        if (on) {
            try {
                java.nio.file.Path f = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("dmzsparking").resolve("common.toml");
                if (java.nio.file.Files.isRegularFile(f)) {
                    for (String line : java.nio.file.Files.readAllLines(f)) {
                        String l = line.trim().replace(" ", "");
                        if (l.equals("story=false")) on = false;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        sparkingStory = on;
        return on;
    }

    private static java.lang.reflect.Method godKiActive, godKiLearned;
    private static boolean godKiTried;

    private static void godKi() {
        if (godKiTried) return;
        godKiTried = true;
        if (!sparking()) return;
        try {
            Class<?> rules = Class.forName("com.yami.dmzsparking.form.GodKiRules");
            Class<?> stats = Class.forName("com.dragonminez.common.stats.StatsData");
            godKiActive = rules.getMethod("active", stats);
            godKiLearned = rules.getMethod("learned", stats);
        } catch (Throwable t) {
            KiControlMod.LOGGER.info("DMZ Sparking sem a API de God Ki esperada; a visao de ki ve todo mundo.");
        }
    }

    /** Ki divino ativo (DMZ Sparking): quem nao tem ki divino nao sente essa pessoa. */
    public static boolean godKiActive(Object statsData) {
        godKi();
        if (godKiActive == null || statsData == null) return false;
        try {
            return Boolean.TRUE.equals(godKiActive.invoke(null, statsData));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ja aprendeu o ki divino (DMZ Sparking)? So assim da para sentir quem usa ki divino. */
    public static boolean godKiLearned(Object statsData) {
        godKi();
        if (godKiLearned == null || statsData == null) return false;
        try {
            return Boolean.TRUE.equals(godKiLearned.invoke(null, statsData));
        } catch (Throwable t) {
            return false;
        }
    }

    /** O jogador esta meditando pelo sistema de meditacao do DMZ Sparking? */
    public static boolean isMeditating(UUID id) {
        if (!meditatingTried) {
            meditatingTried = true;
            if (sparking()) {
                try {
                    meditating = Class.forName("com.yami.dmzsparking.meditation.Meditation").getMethod("meditating", UUID.class);
                } catch (Throwable t) {
                    KiControlMod.LOGGER.info("DMZ Sparking sem a API de meditacao esperada; integracao de meditacao desligada.");
                }
            }
        }
        if (meditating == null) return false;
        try {
            return Boolean.TRUE.equals(meditating.invoke(null, id));
        } catch (Throwable t) {
            meditating = null;
            return false;
        }
    }
}
