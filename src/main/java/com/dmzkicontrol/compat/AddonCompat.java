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
