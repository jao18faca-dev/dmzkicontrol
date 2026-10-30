package com.dmzkicontrol.registry;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.cerealian.CerealScopeItem;
import com.dmzkicontrol.cerealian.CerealShotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Itens e entidades do add-on. */
public final class ModRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KiControlMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KiControlMod.MODID);

    public static final RegistryObject<Item> CEREAL_SCOPE = ITEMS.register("cereal_scope",
            () -> new CerealScopeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Venda de Treino: luta as cegas sentindo o ki. */
    public static final RegistryObject<Item> BLINDFOLD = ITEMS.register("blindfold",
            () -> new com.dmzkicontrol.kivision.BlindfoldItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<EntityType<CerealShotEntity>> CEREAL_SHOT = ENTITIES.register("cereal_shot",
            () -> EntityType.Builder.<CerealShotEntity>of(CerealShotEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("cereal_shot"));

    public static final RegistryObject<EntityType<com.dmzkicontrol.cerealian.CerealBeamEntity>> CEREAL_BEAM = ENTITIES.register("cereal_beam",
            () -> EntityType.Builder.<com.dmzkicontrol.cerealian.CerealBeamEntity>of(com.dmzkicontrol.cerealian.CerealBeamEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .build("cereal_beam"));

    /** Bubbles, o macaco do Senhor Kaioh (treino de pegar o Bubbles). */
    public static final RegistryObject<EntityType<com.dmzkicontrol.kaio.BubblesEntity>> BUBBLES = ENTITIES.register("bubbles",
            () -> EntityType.Builder.<com.dmzkicontrol.kaio.BubblesEntity>of(com.dmzkicontrol.kaio.BubblesEntity::new, MobCategory.CREATURE)
                    .sized(0.5f, 0.85f)
                    .clientTrackingRange(10)
                    .build("bubbles"));

    public static final RegistryObject<Item> BUBBLES_EGG = ITEMS.register("bubbles_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(BUBBLES, 0x3A2618, 0xD9A877, new Item.Properties()));

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(ModRegistry::addToTabs);
        modBus.addListener(ModRegistry::attributes);
    }

    private static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(BUBBLES.get(), com.dmzkicontrol.kaio.BubblesEntity.createAttributes().build());
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(BUBBLES_EGG);
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CEREAL_SCOPE);
            event.accept(BLINDFOLD);
        }
    }
}
