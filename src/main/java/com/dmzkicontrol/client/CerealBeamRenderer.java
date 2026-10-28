package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.CerealBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
        // cresce da mao ate o alvo em ~1,5 tick, fica cheio, e depois afina e apaga devagar
        float grow = Mth.clamp(age / 1.5f, 0f, 1f);
        float fade = 1f - smooth(Mth.clamp((age - 3.5f) / (CerealBeamEntity.LIFE - 3.5f), 0f, 1f));
        if (fade <= 0.01f) return;
        Vec3 here = e.position();
        Vec3 fullEnd = e.getEnd().subtract(here);
        double fullLen = fullEnd.length();
        if (fullLen < 0.05) return;
        Vec3 dir = fullEnd.scale(1.0 / fullLen);
        double len = fullLen * grow;
        Vec3 end = dir.scale(len);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(here);
        float[] c = KiFx.rgb(e.getColor());
        float seed = KiFx.seed(e);
        Matrix4f m = pose.last().pose();
        float thick = (0.25f + 0.45f * fade) * fade;
        boolean arrived = grow >= 1f;

        if (KiFx.available() && thick > 0.03f) {
            VertexConsumer vc = buffers.getBuffer(KiFx.type());
            double segLen = 1.2;
            int segs = Math.max(1, (int) Math.ceil(len / segLen));
            for (int i = 0; i < segs; i++) {
                Vec3 a = dir.scale(len * i / segs);
                Vec3 b = dir.scale(len * (i + 1) / segs);
                if (i == 0) KiFx.rod(vc, m, cam, b, a, KiFx.ROD_TINE, thick, c, seed); // afina saindo do dedo
                else KiFx.rod(vc, m, cam, a, b, KiFx.ROD_ELBOW, thick, c, seed);
            }
            // brilho na ponta do dedo; a frente do raio brilha enquanto viaja; explosao no impacto
            KiFx.blade(vc, m, cam, Vec3.ZERO, dir.scale(0.55), 0.24f * (0.3f + 0.7f * fade), c, seed);
            if (!arrived) KiFx.blade(vc, m, cam, end, end.subtract(dir.scale(0.8)), 0.3f, c, seed + 0.5f);
            else if (e.didHit()) KiFx.blade(vc, m, cam, end, end.subtract(dir.scale(1.1)), 0.5f * (0.35f + 0.65f * fade), c, seed + 0.5f);
        }
        // nucleo branco fino + brilho na cor do ki (estes sim apagam com transparencia)
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        KiFx.glow(glow, m, cam, Vec3.ZERO, end, 0.02f * (0.4f + fade), 1f, 1f, 1f, 0.9f * fade);
        KiFx.glow(glow, m, cam, Vec3.ZERO, end, 0.08f * (0.4f + fade), c[0], c[1], c[2], 0.35f * fade);
        super.render(e, yaw, partial, pose, buffers, light);
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    @Override
    public ResourceLocation getTextureLocation(CerealBeamEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
