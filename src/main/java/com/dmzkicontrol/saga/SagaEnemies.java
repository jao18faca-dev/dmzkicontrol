package com.dmzkicontrol.saga;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity.ComboType;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity.KiSkillType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Inimigos das sagas do Moro (Prisioneiro da Patrulha Galactica) e do Granolah, o Sobrevivente.
 * Modelos, texturas e perfis de luta vem do DMZ Ultra (DarkForest), usados com permissao.
 */
public final class SagaEnemies {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KiControlMod.MODID);
    public static final Map<String, RegistryObject<EntityType<SagaEnemyEntity>>> ALL = new LinkedHashMap<>();

    /** model: outro modelo (null = dmzk_&lt;nome&gt;); flies; size = escala visual. */
    public record Spec(String model, boolean flies, float size) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();
    private static final Map<String, Consumer<DBSagasEntity>> PROFILES = new LinkedHashMap<>();

    static {
        // saga do Moro
        add("saga_moro", 0.7f, 2.1f, 1.0f, e -> {
            e.setSkillColors(0x8B5BFF, 0x3A1078);
            e.setEvade(true, 60);
            e.setAllowedCombos(40, ComboType.BASIC, ComboType.AIR);
            e.addKiSkill(KiSkillType.KI_VOLLEY, 70, 1.2f, 0x8B5BFF, 0x3A1078);
            e.addKiSkill(KiSkillType.GENERIC_KI_WAVE, 150, 1.8f, 0xB891FF, 0x5A5AF0);
        });
        add("saga_moro_unveiled", 0.8f, 2.3f, 1.08f, e -> {
            e.setSkillColors(0xAA5AFF, 0x4402B0);
            e.setKiBlastSpeed(1.3f);
            e.setEvade(true, 55);
            e.setAllowedCombos(30, ComboType.BASIC, ComboType.AIR, ComboType.METEOR_COMBINATION);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 95, 2.2f, 0xAA5AFF, 0x4402B0);
            e.addKiSkill(KiSkillType.KI_VOLLEY, 55, 1.3f, 0xD080FF, 0x5C5AC0);
        });
        add("saga_moro_full", 1.0f, 2.9f, 1.3f, e -> {
            e.setAuraType("god");
            e.setAuraColor(0xF8F8FF);
            e.setSkillColors(0xFFD54A, 0x813400);
            e.setKiBlastSpeed(1.5f);
            e.setAllowedCombos(24, ComboType.BASIC, ComboType.METEOR_COMBINATION, ComboType.KI_CHARGE_ATTACK);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 85, 2.7f, 0xFFD54A, 0x813400);
            e.addKiSkill(KiSkillType.GENERIC_KI_WAVE, 105, 2.4f, 0xFFD54A, 0x910000);
            e.addKiSkill(KiSkillType.BIG_BANG, 210, 3.2f, 0xB891FF, 0x80F870);
        });
        add("saga_saganbo", 0.7f, 2.1f, 1.0f, e -> {
            e.setSkillColors(0x508FA8, 0x187380);
            e.setZanzoken(1, 70);
            e.setEvade(true, 60);
            e.setAllowedCombos(30, ComboType.BASIC, ComboType.AIR, ComboType.RAPID_KICKS);
            e.addKiSkill(KiSkillType.KI_LASER, 65, 1.2f, 0x508FA8, 0x187380);
            e.addKiSkill(KiSkillType.TRIPLE_LASER, 120, 1.5f, 0xB0223C, 0x5C1020);
        });
        add("saga_seven_three", 1.0f, 2.6f, 1.12f, e -> {
            e.setSkillColors(0xE07B2A, 0x603020);
            e.setAllowedCombos(36, ComboType.BASIC, ComboType.METEOR_COMBINATION);
            e.addKiSkill(KiSkillType.KI_SMALL, 60, 1.3f, 0xE07B2A, 0x603020);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 120, 2.2f, 0xE07B2A, 0x910000);
        });
        // saga do Granolah
        add("saga_granolah", 0.7f, 2.0f, 1.0f, e -> {
            e.setSkillColors(0x50FE88, 0x22883C);
            e.setZanzoken(1, 70);
            e.setEvade(true, 55);
            e.setAllowedCombos(32, ComboType.BASIC, ComboType.AIR, ComboType.RAPID_KICKS);
            e.addKiSkill(KiSkillType.KI_LASER, 60, 1.3f, 0x50FE88, 0x22883C);
            e.addKiSkill(KiSkillType.GENERIC_KI_WAVE, 140, 1.8f, 0x7FFF7F, 0x2FFE58);
        });
        add("saga_granolah_wish", 0.8f, 2.2f, 1.05f, e -> {
            e.setSkillColors(0x42ACC0, 0x1E0000);
            e.setKiBlastSpeed(1.5f);
            e.setZanzoken(2, 80);
            e.setAllowedCombos(26, ComboType.BASIC, ComboType.AIR, ComboType.METEOR_COMBINATION);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 90, 2.5f, 0x42ACC0, 0x1E0000);
            e.addKiSkill(KiSkillType.KI_LASER, 105, 2.4f, 0x42ACC0, 0x910000);
        });
        add("saga_gas", 0.6f, 1.6f, 0.85f, e -> {
            e.setSkillColors(0xFF4444, 0x800000);
            e.setAllowedCombos(34, ComboType.BASIC, ComboType.AIR);
            e.addKiSkill(KiSkillType.KI_SMALL, 55, 1.2f, 0xFF4444, 0x800000);
            e.addKiSkill(KiSkillType.KI_VOLLEY, 90, 1.4f, 0xFF8080, 0x900000);
        });
        add("saga_gas_wished", 0.8f, 2.3f, 1.15f, e -> {
            e.setSkillColors(0xFF5555, 0x900000);
            e.setKiBlastSpeed(1.4f);
            e.setAllowedCombos(28, ComboType.BASIC, ComboType.AIR, ComboType.METEOR_COMBINATION);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 90, 2.4f, 0xFF5555, 0x900000);
            e.addKiSkill(KiSkillType.GENERIC_KI_WAVE, 105, 2.3f, 0xFF5555, 0x910000);
        });
        add("saga_gas_berserk", 1.0f, 2.7f, 1.35f, e -> {
            e.setSkillColors(0xFFD700, 0xA00000);
            e.setKiBlastSpeed(1.6f);
            e.setAllowedCombos(22, ComboType.BASIC, ComboType.METEOR_COMBINATION, ComboType.KI_CHARGE_ATTACK);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 80, 2.8f, 0xFFD700, 0xA00000);
            e.addKiSkill(KiSkillType.GENERIC_KI_WAVE, 100, 2.5f, 0xFFD700, 0x910000);
            e.addKiSkill(KiSkillType.BIG_BANG, 200, 3.2f, 0xFF8000, 0x80F870);
        });
        add("saga_elec", 0.7f, 2.2f, 1.12f, e -> {
            e.setSkillColors(0xDD3A27, 0x700000);
            e.setEvade(true, 55);
            e.setAllowedCombos(32, ComboType.BASIC, ComboType.AIR);
            e.addKiSkill(KiSkillType.KI_LASER, 60, 1.3f, 0xDD3A27, 0x700000);
            e.addKiSkill(KiSkillType.TRIPLE_LASER, 120, 1.5f, 0xFF5555, 0x900000);
        });
        add("saga_oil", 1.1f, 2.4f, 1.3f, e -> {
            e.setSkillColors(0xDD3A27, 0x700000);
            e.setAllowedCombos(38, ComboType.BASIC, ComboType.METEOR_COMBINATION);
            e.addKiSkill(KiSkillType.KI_SMALL, 60, 1.4f, 0xDD3A27, 0x700000);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 115, 2.2f, 0xDD3A27, 0x910000);
        });
        add("saga_macki", 0.6f, 1.7f, 0.9f, e -> {
            e.setSkillColors(0xC7419C, 0x640000);
            e.setZanzoken(1, 70);
            e.setAllowedCombos(32, ComboType.BASIC, ComboType.AIR, ComboType.RAPID_KICKS);
            e.addKiSkill(KiSkillType.KI_LASER, 55, 1.2f, 0xC7419C, 0x640000);
        });
        add("saga_heeter_soldier", 0.6f, 1.9f, 1.0f, e -> {
            e.setSkillColors(0xDD3A27, 0x700000);
            e.setAllowedCombos(40, ComboType.BASIC);
            e.addKiSkill(KiSkillType.KI_SMALL, 50, 1.0f, 0xDD3A27, 0x700000);
        });
        add("saga_black_frieza", 0.9f, 2.6f, 1.0f, e -> {
            e.setSkillColors(0xF0BA32, 0x202020);
            e.setKiBlastSpeed(1.7f);
            e.setZanzoken(3, 85);
            e.setAllowedCombos(22, ComboType.BASIC, ComboType.METEOR_COMBINATION, ComboType.KI_CHARGE_ATTACK);
            e.addKiSkill(KiSkillType.KI_EXPLOSION, 80, 2.8f, 0xF0BA32, 0x202020);
            e.addKiSkill(KiSkillType.DEATH_BALL, 200, 3.4f, 0xFFD54A, 0x80F870);
        });
        // o Black Freeza usa o modelo do Freeza Forca Total do proprio DMZ (mesmo mapa de textura)
        SPECS.put("saga_black_frieza", new Spec("saga_frieza_fp", true, 1.0f));
    }

    private SagaEnemies() {}

    private static void add(String name, float width, float height, float size, Consumer<DBSagasEntity> profile) {
        SPECS.put(name, new Spec(null, true, size));
        PROFILES.put(name, profile);
        ALL.put(name, REGISTER.register(name, () -> EntityType.Builder.<SagaEnemyEntity>of(SagaEnemyEntity::new, MobCategory.MONSTER)
                .sized(width, height)
                .clientTrackingRange(10)
                .build(new ResourceLocation(KiControlMod.MODID, name).toString())));
    }

    public static Spec spec(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null || !KiControlMod.MODID.equals(key.getNamespace()) ? null : SPECS.get(key.getPath());
    }

    public static void applyProfile(String name, DBSagasEntity e) {
        Consumer<DBSagasEntity> p = PROFILES.get(name);
        if (p == null) return;
        try {
            p.accept(e);
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Sagas] perfil de luta de {} nao aplicado: {}", name, t.toString());
        }
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
        modBus.addListener(SagaEnemies::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        ALL.values().forEach(t -> event.put(t.get(), SagaEnemyEntity.values().build()));
    }
}
