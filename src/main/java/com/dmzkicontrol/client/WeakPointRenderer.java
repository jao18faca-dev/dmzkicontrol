package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.WeakPoints;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Circulos vermelhos nos pontos fracos do alvo marcado pelo Olho Cereal (visiveis atraves do corpo).
 * A cada nivel da Mira aparece mais um; cada golpe/tiro preciso apaga o ponto atingido.
 */
public final class WeakPointRenderer {
    private WeakPointRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int id = CerealianEyeClient.markedId();
        int mask = CerealianEyeClient.markMask();
        if (id < 0 || mask == 0) return;
        Entity e = mc.level.getEntity(id);
        if (!(e instanceof LivingEntity target) || !target.isAlive()) return;

        float partial = event.getPartialTick();
        float eye = CerealianEyeClient.zoom(partial);
        float alphaBase = 0.45f + 0.55f * eye; // mais forte com o olho aberto
        float time = (mc.player.tickCount + partial) / 20f;
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = event.getPoseStack();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < WeakPoints.MAX; i++) {
            if ((mask & (1 << i)) == 0) continue;
            Vec3 p = WeakPoints.position(target, i, partial);
            double dist = p.distanceTo(camPos);
            float size = (float) (0.09 + dist * 0.011);
            float pulse = 0.85f + 0.15f * (float) Math.sin(time * 6.0 + i);
            pose.pushPose();
            pose.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            pose.mulPose(cam.rotation());
            Matrix4f m = pose.last().pose();
            ring(buf, m, size * pulse * 1.45f, size * 0.10f, 1f, 0.2f, 0.15f, 0.25f * alphaBase);  // brilho
            ring(buf, m, size * pulse, size * 0.22f, 1f, 0.12f, 0.1f, 0.95f * alphaBase);          // anel
            disc(buf, m, size * 0.28f, 1f, 0.35f, 0.3f, 0.95f * alphaBase);                        // centro
            pose.popPose();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void ring(BufferBuilder buf, Matrix4f m, float radius, float thick, float r, float g, float b, float a) {
        int seg = 24;
        float in = radius - thick, out = radius + thick;
        for (int s = 0; s < seg; s++) {
            double a0 = s * Math.PI * 2 / seg, a1 = (s + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buf.vertex(m, c0 * in, s0 * in, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, c0 * out, s0 * out, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, c1 * out, s1 * out, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, c1 * in, s1 * in, 0).color(r, g, b, a).endVertex();
        }
    }

    private static void disc(BufferBuilder buf, Matrix4f m, float radius, float r, float g, float b, float a) {
        int seg = 12;
        for (int s = 0; s < seg; s++) {
            double a0 = s * Math.PI * 2 / seg, a1 = (s + 1) * Math.PI * 2 / seg;
            buf.vertex(m, 0, 0, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, (float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, 0, 0, 0).color(r, g, b, a).endVertex();
        }
    }
}
