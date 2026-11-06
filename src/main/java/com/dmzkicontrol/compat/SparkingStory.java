package com.dmzkicontrol.compat;

import com.dmzkicontrol.config.KiControlConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Coloca as sagas do add-on na historia principal do DMZ Sparking (lista SagaOrder.MANDATORY). */
public final class SparkingStory {
    public static final String MORO = "dmzkicontrol_s1_moro";
    public static final String GRANOLAH = "dmzkicontrol_s2_granolah";
    /** Saga do Sparking depois da qual as nossas entram (Torneio do Poder). */
    public static final String AFTER = "dmzsparking_s5_top";
    private static final String AFTER_LEGACY = "top_saga";

    private SparkingStory() {}

    public static Object[] extend(Object[] order) {
        try {
            // com o DMZ Ultra o Sparking fica parado (o Ultra assume a historia) e as sagas sao as dele
            if (order == null || !enabled() || UltraCompat.ultraSagas()) return order;
            List<Object> list = new ArrayList<>(Arrays.asList(order));
            if (list.contains(MORO) || !list.contains(AFTER)) return order;
            int at = Math.max(list.indexOf(AFTER), list.indexOf(AFTER_LEGACY)) + 1;
            list.add(at, MORO);
            list.add(at + 1, GRANOLAH);
            com.dmzkicontrol.compat.CerealPack.info("[DragonMineZ Evolution] Moro e Granolah entram na historia principal do DMZ Sparking (depois do Torneio do Poder).");
            return list.toArray(new Object[0]);
        } catch (Throwable t) {
            com.dmzkicontrol.compat.CerealPack.warn("[DragonMineZ Evolution] historia do Sparking nao estendida: " + t);
            return order;
        }
    }

    private static boolean enabled() {
        try {
            KiControlConfig c = KiControlConfig.get();
            return c == null || (c.sagas.installMoroAndGranolah && c.sagas.sparkingStory);
        } catch (Throwable t) {
            return true;
        }
    }
}
