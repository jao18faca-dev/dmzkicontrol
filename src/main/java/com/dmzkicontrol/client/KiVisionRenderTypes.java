package com.dmzkicontrol.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Tipo de render da visao de ki: cor pura, aditivo, sem neblina e sem teste de profundidade
 * (o ki e sentido atraves das paredes).
 */
public final class KiVisionRenderTypes extends RenderType {
    public static final RenderType GHOST = create("dmzkicontrol_ki_ghost",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private KiVisionRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
