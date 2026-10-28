package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;

/**
 * Flecha de Ki: tecnica predefinida exclusiva dos Cerealianos, registrada no mesmo catalogo das
 * tecnicas do DMZ (Kamehameha, Dodonpa...). O DMZ cuida do slot, do custo de ki, da recarga e da XP;
 * quando ela e disparada, trocamos a bola de ki por uma flecha de luz que atravessa alvos.
 */
public final class KiArrowTechnique {
    public static final String ID = "ki_arrow";

    private KiArrowTechnique() {}

    public static void register() {
        try {
            KiAttackData data = new KiAttackData();
            data.setId(ID);
            data.setName("technique.dragonminez." + ID);
            data.setAuthor("Granolah");
            data.setKiType(KiAttackData.KiType.SMALL_BALL);
            data.setUtility(KiAttackData.Utility.DAMAGE);
            data.setDamageMultiplier(1.75f);
            data.setColorInterior(0xE8FFFB);
            data.setColorExterior(0x5FD3C4);
            data.setColorOutline(0x2A8F86);
            data.setSize(0.4f);
            data.setSpeed(3.0f);
            data.setArmorPenetration(0);
            data.getAllowedRaces().clear();
            data.getAllowedRaces().add("cerealian");
            data.setAnimation("ki.laser");
            data.setCastTime(0);
            data.setCooldown(6);
            data.calculateDerivedValues();
            PredefinedTechniques.REGISTRY.put(ID, data);
        } catch (Throwable t) {
            KiControlMod.LOGGER.error("Nao foi possivel registrar a Flecha de Ki no DMZ", t);
        }
    }
}
