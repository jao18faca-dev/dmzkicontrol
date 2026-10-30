package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.kaio.BubblesEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Bubbles com modelo GeckoLib: geo/entity/bubbles, animations/entity/bubbles, textures/entity/bubbles. */
public class BubblesRenderer extends GeoEntityRenderer<BubblesEntity> {
    public BubblesRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(KiControlMod.MODID, "bubbles"), true));
        this.shadowRadius = 0.3f;
    }
}
