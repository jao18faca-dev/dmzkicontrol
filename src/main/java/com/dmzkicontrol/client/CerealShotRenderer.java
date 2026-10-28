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

/**
 * Tiro e flecha cerealianos no estilo das armas de ki do DMZ (shader de lamina do proprio DMZ):
 * <ul>
 *   <li>tiro: bala de ki com a cabeca brilhando e uma cauda de chamas que afina;</li>
 *   <li>flecha: lamina de ki apontada para frente com farpas, envolta por uma aura de chamas maior e
 *   uma espiral de energia. Tudo na cor do ki de quem atirou, maior quanto mais carregada.</li>
 * </ul>
 */
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
        Vec3 here = new Vec3(e.xo + (e.getX() - e.xo) * partial, e.yo + (e.getY() - e.yo) * partial, e.zo + (e.getZ() - e.zo) * partial);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(here);
        float[] c = KiFx.rgb(e.getColor());
        float seed = KiFx.seed(e);
        float age = e.tickCount + partial;
        Matrix4f m = pose.last().pose();

        if (KiFx.available()) {
            VertexConsumer vc = buffers.getBuffer(KiFx.type());
            if (e.isArrow()) drawArrow(vc, buffers, m, cam, dir, e.getScale(), age, c, seed);
            else drawShot(vc, m, cam, dir, speed, age, c, seed);
        } else {
            drawFallback(buffers.getBuffer(RenderType.lightning()), m, cam, dir, e.isArrow() ? e.getScale() : 0.6f, c);
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    /** Bala de ki: explosao de ki na cabeca e chamas afinando para tras. */
    private static void drawShot(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 dir, double speed, float age, float[] c, float seed) {
        double len = Math.min(1.5, 0.35 + age * speed * 0.5);
        Vec3 head = dir.scale(0.12);
        Vec3 tail = head.subtract(dir.scale(len));
        KiFx.blade(vc, m, cam, head, tail, (float) (len * 0.28), c, seed);
    }

    private static void drawArrow(VertexConsumer vc, MultiBufferSource buffers, Matrix4f m, Vec3 cam, Vec3 dir, float s, float age, float[] c, float seed) {
        Vec3 tip = dir.scale(0.7 * s);
        Vec3 tail = dir.scale(-2.3 * s);
        // aura de chamas maior por tras
        KiFx.blade(vc, m, cam, dir.scale(-2.9 * s), dir.scale(1.0 * s), 0.85f * s, c, seed + 0.37f);
        // corpo da flecha
        KiFx.blade(vc, m, cam, tail, tip, 0.42f * s, c, seed);
        // farpas da ponta
        Vec3 side = KiFx.side(tail, tip, cam);
        Vec3 back = tip.subtract(dir.scale(0.55 * s));
        KiFx.rod(vc, m, cam, tip, back.add(side.scale(0.32 * s)), KiFx.ROD_TINE, 0.9f * s, c, seed);
        KiFx.rod(vc, m, cam, tip, back.subtract(side.scale(0.32 * s)), KiFx.ROD_TINE, 0.9f * s, c, seed);

        // espiral de energia em volta (aditiva)
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Vec3 a1 = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0, 1, 0)).normalize() : dir.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 a2 = dir.cross(a1).normalize();
        int steps = 18;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double ang = age * 0.9 + t * Math.PI * 4 + strand * Math.PI;
                double r = 0.38 * s * (0.4 + 0.6 * Math.sin(t * Math.PI));
                Vec3 pt = dir.scale(0.4 * s - t * 3.0 * s).add(a1.scale(Math.cos(ang) * r)).add(a2.scale(Math.sin(ang) * r));
                if (prev != null) KiFx.glow(glow, m, cam, prev, pt, 0.035f * s, c[0], c[1], c[2], 0.55f * (float) (1 - t));
                prev = pt;
            }
        }
    }

    private static void drawFallback(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 dir, float s, float[] c) {
        Vec3 head = dir.scale(0.3 * s);
        Vec3 tail = dir.scale(-2.2 * s);
        KiFx.glow(vc, m, cam, head, tail, 0.18f * s, c[0], c[1], c[2], 0.45f);
        KiFx.glow(vc, m, cam, head, tail, 0.06f * s, 1f, 1f, 1f, 0.95f);
    }

    @Override
    public ResourceLocation getTextureLocation(CerealShotEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
