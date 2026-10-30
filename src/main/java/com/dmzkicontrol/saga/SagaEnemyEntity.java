package com.dmzkicontrol.saga;

import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Inimigo das sagas do Moro e do Granolah (vindas do DMZ Ultra, de DarkForest, com permissao).
 * Usa o sistema de inimigos de saga do proprio DMZ: IA, voo, ki, combos e o renderizador DBSagasRenderer.
 * Modelo e textura ficam em assets/dragonminez/.../sagas/dmzk_&lt;nome&gt; para nao bater com nada do DMZ ou do Sparking.
 */
public class SagaEnemyEntity extends DBSagasEntity {

    public SagaEnemyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        SagaEnemies.Spec spec = SagaEnemies.spec(type);
        setCanFly(spec == null || spec.flies());
        if (spec != null && spec.size() != 1.0f) setScaleVal(spec.size());
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key != null) SagaEnemies.applyProfile(key.getPath(), this);
    }

    private String path() {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(getType());
        return key == null ? "" : key.getPath();
    }

    @Override
    public String getGeckolibModelName() {
        SagaEnemies.Spec spec = SagaEnemies.spec(getType());
        if (spec != null && spec.model() != null) return spec.model();
        return "dmzk_" + path();
    }

    @Override
    public String getGeckolibTextureName() {
        return "dmzk_" + path();
    }

    public static AttributeSupplier.Builder values() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }
}
