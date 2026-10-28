package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.CerealBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Laser cerealiano (estilo Raio da Morte): raio fino de ki da mao ate o alvo, com o nucleo branco,
 * borda na cor do ki, um brilho na ponta do dedo e uma explosao pequena no ponto de impacto.
 * Afina e some em poucos ticks.
 */
public class CerealBeamRenderer extends EntityRenderer<CerealBeamEntity> {

    public CerealBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(CerealBeamEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(CerealBeamEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partial;
        float k = 1f - Math.min(1f, age / CerealBeamEntity.LIFE);
        if (k <= 0.01f) return;
        Vec3 here = e.position();
        Vec3 end = e.getEnd().subtract(here);
        double len = end.length();
        if (len < 0.05) return;
        Vec3 dir = end.scale(1.0 / len);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(here);
        float[] c = KiFx.rgb(e.getColor());
        float seed = KiFx.seed(e);
        Matrix4f m = pose.last().pose();
        float thick = 0.35f + 0.3f * k;

        if (KiFx.available()) {
            VertexConsumer vc = buffers.getBuffer(KiFx.type());
            // segmentos curtos para as chamas de ki ficarem com a mesma cara ao longo do raio
            double segLen = 1.2;
            int segs = Math.max(1, (int) Math.ceil(len / segLen));
            for (int i = 0; i < segs; i++) {
                Vec3 a = dir.scale(len * i / segs);
                Vec3 b = dir.scale(len * (i + 1) / segs);
                if (i == 0) KiFx.rod(vc, m, cam, b, a, KiFx.ROD_TINE, thick, c, seed); // afina saindo do dedo
                else KiFx.rod(vc, m, cam, a, b, KiFx.ROD_ELBOW, thick, c, seed);
            }
            // brilho no dedo e explosao no impacto
            KiFx.blade(vc, m, cam, Vec3.ZERO, dir.scale(0.55), 0.22f * (0.4f + 0.6f * k), c, seed);
            if (e.didHit()) KiFx.blade(vc, m, cam, end, end.subtract(dir.scale(1.1)), 0.45f * (0.5f + 0.5f * k), c, seed + 0.5f);
        }
        // nucleo branco bem fino (aditivo) por cima
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        KiFx.glow(glow, m, cam, Vec3.ZERO, end, 0.018f * (0.5f + k), 1f, 1f, 1f, 0.9f * k);
        KiFx.glow(glow, m, cam, Vec3.ZERO, end, 0.07f * (0.5f + k), c[0], c[1], c[2], 0.35f * k);
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CerealBeamEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
