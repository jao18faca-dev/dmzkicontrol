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

    public static final RegistryObject<EntityType<CerealShotEntity>> CEREAL_SHOT = ENTITIES.register("cereal_shot",
            () -> EntityType.Builder.<CerealShotEntity>of(CerealShotEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("cereal_shot"));

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(ModRegistry::addToTabs);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(CEREAL_SCOPE);
    }
}
