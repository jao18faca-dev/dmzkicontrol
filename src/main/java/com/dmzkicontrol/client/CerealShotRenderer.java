package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.CerealShotEntity;
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

/** Desenha o tiro como um risco de luz fino (nucleo branco + brilho da cor do ki), afinando na cauda. */
public class CerealShotRenderer extends EntityRenderer<CerealShotEntity> {

    public CerealShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CerealShotEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 vel = e.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1e-4) return;
        Vec3 dir = vel.scale(1.0 / speed);
        boolean arrow = e.isArrow();
        float len = (float) Math.min(arrow ? 3.2 : 2.2, (e.tickCount + partial) * speed);
        if (len <= 0.05f) return;

        Vec3 here = new Vec3(e.xo + (e.getX() - e.xo) * partial, e.yo + (e.getY() - e.yo) * partial, e.zo + (e.getZ() - e.zo) * partial);
        Vec3 toCam = this.entityRenderDispatcher.camera.getPosition().subtract(here);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize();

        int color = e.getColor();
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float core = arrow ? 0.06f : 0.035f;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 tail = dir.scale(-len);

        streak(vc, m, Vec3.ZERO, tail, side, core * 3.2f, core * 0.6f, r, g, b, 0.45f);
        streak(vc, m, Vec3.ZERO, tail, side, core, core * 0.25f, 1f, 1f, 1f, 0.95f);
        if (arrow) {
            // ponta da flecha
            Vec3 tip = dir.scale(0.35);
            Vec3 a = side.scale(core * 4.5).add(dir.scale(-0.15));
            Vec3 bb = side.scale(-core * 4.5).add(dir.scale(-0.15));
            quad(vc, m, tip, a, Vec3.ZERO, bb, r, g, b, 0.8f);
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    private static void streak(VertexConsumer vc, Matrix4f m, Vec3 head, Vec3 tail, Vec3 side, float wHead, float wTail,
                               float r, float g, float b, float a) {
        Vec3 h1 = head.add(side.scale(wHead)), h2 = head.add(side.scale(-wHead));
        Vec3 t1 = tail.add(side.scale(wTail)), t2 = tail.add(side.scale(-wTail));
        quad(vc, m, h1, t1, t2, h2, r, g, b, a);
    }

    /** Quad nos dois sentidos (sem depender de culling). */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
        v(vc, m, p1, r, g, b, a); v(vc, m, p2, r, g, b, a * 0.4f); v(vc, m, p3, r, g, b, a * 0.4f); v(vc, m, p4, r, g, b, a);
        v(vc, m, p4, r, g, b, a); v(vc, m, p3, r, g, b, a * 0.4f); v(vc, m, p2, r, g, b, a * 0.4f); v(vc, m, p1, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(CerealShotEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
