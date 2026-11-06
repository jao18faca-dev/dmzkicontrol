package com.dmzkicontrol.compat;

import com.dmzkicontrol.config.KiControlConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;

/**
 * DMZ Ultra (DarkForest). A versao 0.4 dele ja traz as sagas do Moro e do Granolah e o Planeta Cereal
 * proprios: com ele instalado este add-on usa os dele (sem saga, NPC nem planeta repetido) e liga a eles
 * o que so existe aqui (Esferas de Cereal, Toronbo, formas Cerealianas).
 */
public final class UltraCompat {
    public static final String MODID = "dmzultra";
    public static final String MORO = "moro_saga";
    public static final String GRANOLAH = "granolah_saga";
    public static final String OUR_MORO = "dmzkicontrol_s1_moro";
    public static final String OUR_GRANOLAH = "dmzkicontrol_s2_granolah";
    public static final String OUR_CEREAL = "dmzkicontrol:cereal";
    public static final String ULTRA_CEREAL = "dmzultra:cereal";

    private static Boolean present;

    private UltraCompat() {}

    /** Funciona desde a inicializacao (antes da lista de mods ficar pronta). */
    public static boolean present() {
        Boolean p = present;
        if (p != null) return p;
        boolean found = false;
        try {
            ModList list = ModList.get();
            if (list != null) found = list.isLoaded(MODID);
            else found = LoadingModList.get() != null && LoadingModList.get().getModFileById(MODID) != null;
        } catch (Throwable t) {
            try {
                found = LoadingModList.get() != null && LoadingModList.get().getModFileById(MODID) != null;
            } catch (Throwable ignored) {}
        }
        present = found;
        return found;
    }

    private static KiControlConfig.Ultra cfg() {
        try {
            KiControlConfig c = KiControlConfig.get();
            return c != null && c.ultra != null ? c.ultra : new KiControlConfig.Ultra();
        } catch (Throwable t) {
            return new KiControlConfig.Ultra();
        }
    }

    /** As sagas do Moro/Granolah do Ultra substituem as deste add-on. */
    public static boolean ultraSagas() {
        return present() && cfg().useUltraSagas;
    }

    /** O conteudo de Bleach do Ultra fica desligado. */
    public static boolean bleachOff() {
        return cfg().removeBleach;
    }

    public static String granolahSaga() {
        return ultraSagas() ? GRANOLAH : OUR_GRANOLAH;
    }

    public static String moroSaga() {
        return ultraSagas() ? MORO : OUR_MORO;
    }

    public static String cerealDimension() {
        return ultraSagas() ? ULTRA_CEREAL : OUR_CEREAL;
    }

    /** Saga equivalente do Ultra para uma saga deste add-on (ou null). */
    public static String ultraOf(String ourSaga) {
        if (OUR_MORO.equals(ourSaga)) return MORO;
        if (OUR_GRANOLAH.equals(ourSaga)) return GRANOLAH;
        return null;
    }
}
