package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.cerealian.CerealScopeItem;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Orelhas pontudas dos Cerealianos (na cor da pele) e o modelo da Mira Cereal (a lente do Granolah:
 * faixa de couro na testa, protetor redondo na orelha direita e lente cilindrica no olho direito, do mesmo lado do protetor).
 * E uma camada comum de jogador: o DMZ repassa camadas de outros mods para o modelo dele, ja com a
 * cabeca na pose certa.
 */
public class CerealPartsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(KiControlMod.MODID, "cereal_parts"), "main");
    private static final ResourceLocation EARS_TEX = new ResourceLocation(KiControlMod.MODID, "textures/entity/cereal_ears.png");
    private static final ResourceLocation SCOPE_TEX = new ResourceLocation(KiControlMod.MODID, "textures/entity/cereal_scope.png");
    private static final ResourceLocation BLINDFOLD_TEX = new ResourceLocation(KiControlMod.MODID, "textures/entity/blindfold.png");

    private final ModelPart ears;
    private final ModelPart scope;
    private final ModelPart blindfold;

    public CerealPartsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ModelPart root) {
        super(parent);
        this.ears = root.getChild("ears");
        this.scope = root.getChild("scope");
        this.blindfold = root.getChild("blindfold");
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- orelhas (pivo na lateral da cabeca; apontam para fora, um pouco para tras e para cima)
        PartDefinition ears = root.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition right = ears.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -1.5F, -0.5F, 3, 3, 1),
                PartPose.offsetAndRotation(-4.0F, -4.2F, 0.5F, 0F, rad(25), rad(18)));
        right.addOrReplaceChild("right_tip",
                CubeListBuilder.create().texOffs(0, 8).addBox(-2.0F, -1.0F, -0.5F, 2, 2, 1),
                PartPose.offsetAndRotation(-3.0F, -0.5F, 0F, 0F, 0F, rad(22)));
        PartDefinition left = ears.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.0F, -1.5F, -0.5F, 3, 3, 1),
                PartPose.offsetAndRotation(4.0F, -4.2F, 0.5F, 0F, rad(-25), rad(-18)));
        left.addOrReplaceChild("left_tip",
                CubeListBuilder.create().texOffs(0, 8).mirror().addBox(0.0F, -1.0F, -0.5F, 2, 2, 1),
                PartPose.offsetAndRotation(3.0F, -0.5F, 0F, 0F, 0F, rad(-22)));

        // ---- Mira Cereal (cabeca: x -4..4, y -8..0, z -4..4; frente = -z; direita do personagem = -x)
        PartDefinition scope = root.addOrReplaceChild("scope", CubeListBuilder.create(), PartPose.ZERO);
        scope.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.0F, -6.6F, -4.0F, 8, 2, 8, new CubeDeformation(0.45F)), PartPose.ZERO);
        scope.addOrReplaceChild("ear_cover", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-6.2F, -5.6F, -3.0F, 2, 6, 6), PartPose.ZERO);
        scope.addOrReplaceChild("ear_rim", CubeListBuilder.create().texOffs(20, 32)
                .addBox(-6.7F, -4.6F, -2.0F, 1, 4, 4), PartPose.ZERO);
        scope.addOrReplaceChild("lens_housing", CubeListBuilder.create().texOffs(32, 32)
                .addBox(-3.6F, -5.6F, -6.4F, 3, 3, 3), PartPose.ZERO);
        scope.addOrReplaceChild("lens_ring", CubeListBuilder.create().texOffs(32, 40)
                .addBox(-3.6F, -5.6F, -6.9F, 3, 3, 1, new CubeDeformation(0.25F)), PartPose.ZERO);
        scope.addOrReplaceChild("lens_glass", CubeListBuilder.create().texOffs(32, 48)
                .addBox(-3.1F, -5.1F, -7.2F, 2, 2, 1), PartPose.ZERO);
        scope.addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(48, 16)
                .addBox(4.3F, -7.0F, -1.5F, 1, 3, 3), PartPose.ZERO);

        // ---- Venda de Treino: faixa nos olhos, no com duas pontas caindo atras
        PartDefinition fold = root.addOrReplaceChild("blindfold", CubeListBuilder.create(), PartPose.ZERO);
        fold.addOrReplaceChild("cloth", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -5.2F, -4.0F, 8, 2, 8, new CubeDeformation(0.55F)), PartPose.ZERO);
        fold.addOrReplaceChild("knot", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-1.5F, -5.7F, 4.3F, 3, 3, 1), PartPose.ZERO);
        fold.addOrReplaceChild("tail_left", CubeListBuilder.create().texOffs(10, 12)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(-0.9F, -4.4F, 5.0F, rad(22), 0F, rad(14)));
        fold.addOrReplaceChild("tail_right", CubeListBuilder.create().texOffs(14, 12)
                .addBox(-0.5F, 0.0F, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(0.9F, -4.4F, 5.0F, rad(18), 0F, rad(-12)));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        if (player.isInvisible()) return;
        if (com.dmzkicontrol.kivision.BlindfoldItem.isWearing(player)) {
            pose.pushPose();
            this.getParentModel().head.translateAndRotate(pose);
            blindfold.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BLINDFOLD_TEX)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (!CerealScopeItem.isWearing(player)) return;
        pose.pushPose();
        this.getParentModel().head.translateAndRotate(pose);
        scope.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SCOPE_TEX)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static int parse(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
