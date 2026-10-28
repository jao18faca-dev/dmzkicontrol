package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;

/**
 * Arco de Ki: tecnica predefinida exclusiva dos Cerealianos, registrada no mesmo catalogo das tecnicas
 * do DMZ (Kamehameha, Dodonpa...). E do tipo "onda" para poder ser carregada: segurar = puxar a corda
 * (o add-on desenha o arco de ki na mao), soltar = atirar. O DMZ cuida do slot, da carga, do custo de
 * ki, da recarga e da XP; no disparo trocamos a onda por uma flecha de luz que atravessa alvos.
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
            data.setKiType(KiAttackData.KiType.WAVE);
            data.setUtility(KiAttackData.Utility.DAMAGE);
            data.setDamageMultiplier(2.0f);
            data.setColorInterior(0xE8FFFB);
            data.setColorExterior(0x5FD3C4);
            data.setColorOutline(0x2A8F86);
            data.setSize(0.5f);
            data.setSpeed(2.0f);
            data.setArmorPenetration(0);
            data.getAllowedRaces().clear();
            data.getAllowedRaces().add("cerealian");
            data.setAnimation("ki.kibow");
            data.setCastTime(0);
            data.setCooldown(8);
            data.calculateDerivedValues();
            PredefinedTechniques.REGISTRY.put(ID, data);
        } catch (Throwable t) {
            KiControlMod.LOGGER.error("Nao foi possivel registrar o Arco de Ki no DMZ", t);
        }
    }
}
