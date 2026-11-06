package com.dmzkicontrol.client.frostgod;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Freeza Dourado e Black Freeza: o DMZ so pinta o corpo de outra cor. Esta camada desenha por cima do
 * corpo do proprio modelo do DMZ (mesma pose, voando, de frente e de costas):
 * - um brilho metalico que corre pelo corpo (dourado no Dourado; lilas, fino e polido no Black);
 * - as gemas (as cupulas da cabeca, dos ombros, do peito, dos bracos e das pernas) brilhando de verdade.
 */
public class FrostGodLayer<T extends AbstractClientPlayer> extends GeoRenderLayer<T> {
    private static final ResourceLocation SHEEN_GOLD = new ResourceLocation("dmzkicontrol", "textures/misc/frostgod_sheen_gold.png");
    private static final ResourceLocation SHEEN_BLACK = new ResourceLocation("dmzkicontrol", "textures/misc/frostgod_sheen_black.png");
    private static final String[] ARMOR_BONES = {"armorHead", "armorBody", "armorBody2", "armorLeggingsBody", "armorRightArm",
            "armorLeftArm", "armorLeftLeg", "armorLeftBoot", "armorRightLeg", "armorRightBoot"};
    /** Camadas do corpo da raca do Freeza que sao gemas: 2 (cupulas) e 4 (cupula da cabeca). */
    private static final int[] GEM_LAYERS = {2, 4};
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    public FrostGodLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack pose, T player, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                       VertexConsumer buffer, float partialTick, int light, int overlay) {
        FrostGodLook look = FrostGodLook.of(player);
        if (look == null || player.isSpectator()) return;
        List<GeoBone> hidden = new ArrayList<>();
        for (String name : ARMOR_BONES) {
            model.getBone(name).ifPresent(b -> {
                if (!b.isHidden()) {
                    b.setHidden(true);
                    hidden.add(b);
                }
            });
        }
        try {
            float time = (player.tickCount + partialTick) / 20f;
            // 1) brilho metalico
            RenderType sheen = FrostGodRenderTypes.sheen(look == FrostGodLook.GOLDEN ? SHEEN_GOLD : SHEEN_BLACK);
            getRenderer().reRender(model, pose, buffers, player, sheen, buffers.getBuffer(sheen), partialTick, light,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            // 2) gemas acesas (pulsam devagar)
            int bodyType = bodyType(player);
            float pulse = 0.55f + 0.25f * Mth.sin(time * 2.4f);
            float r, g, b;
            if (look == FrostGodLook.GOLDEN) { r = 0.62f; g = 0.22f; b = 1.0f; }
            else { r = 0.95f; g = 0.25f; b = 0.85f; }
            for (int layer : GEM_LAYERS) {
                ResourceLocation tex = new ResourceLocation("dragonminez", "textures/entity/races/frostdemon/bodytype_" + bodyType + "_layer" + layer + ".png");
                if (!exists(tex)) tex = new ResourceLocation("dragonminez", "textures/entity/races/frostdemon/bodytype_0_layer" + layer + ".png");
                if (!exists(tex)) continue;
                RenderType glow = RenderType.eyes(tex);
                getRenderer().reRender(model, pose, buffers, player, glow, buffers.getBuffer(glow), partialTick, 0xF000F0,
                        OverlayTexture.NO_OVERLAY, r * pulse, g * pulse, b * pulse, 1f);
            }
        } catch (Throwable ignored) {
        } finally {
            for (GeoBone b : hidden) b.setHidden(false);
        }
    }

    private static int bodyType(AbstractClientPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
            return data == null ? 0 : Math.max(0, data.getCharacter().getBodyType());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean exists(ResourceLocation tex) {
        return EXISTS.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }
}
