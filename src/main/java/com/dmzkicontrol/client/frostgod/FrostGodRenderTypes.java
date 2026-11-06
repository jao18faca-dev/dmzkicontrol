package com.dmzkicontrol.client.frostgod;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Brilho metalico: o mesmo shader do brilho de encantamento (desliza pelo corpo e acompanha qualquer pose),
 * mas com uma textura nossa. Desenha so onde o corpo ja foi desenhado (profundidade igual) e soma luz.
 */
public final class FrostGodRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> SHEEN = new HashMap<>();

    private FrostGodRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                                Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType sheen(ResourceLocation texture) {
        return SHEEN.computeIfAbsent(texture, t -> create("dmzkicontrol_frostgod_sheen", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_GLINT_SHADER)
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(ENTITY_GLINT_TEXTURING)
                        .createCompositeState(false)));
    }
}
