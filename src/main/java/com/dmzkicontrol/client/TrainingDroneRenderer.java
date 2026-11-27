package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.training.TrainingDroneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Drone de treino: geo/entity/training_drone, animations/entity/training_drone, textures/entity/training_drone. */
public class TrainingDroneRenderer extends GeoEntityRenderer<TrainingDroneEntity> {
    public TrainingDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(KiControlMod.MODID, "training_drone"), false));
        this.shadowRadius = 0.25f;
    }
}
