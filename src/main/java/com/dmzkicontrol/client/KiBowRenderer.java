package com.dmzkicontrol.client;

import com.dmzkicontrol.cerealian.KiArrowTechnique;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Desenha o Arco de Ki na mao do Cerealiano enquanto ele carrega a tecnica: um arco de luz com corda
 * que vai sendo puxada e uma flecha de ki que cresce com a carga. Ao soltar, a corda volta.
 */
public final class KiBowRenderer {
    private static final int COLOR = 0x5FD3C4;
    /** entityId do dono -> tick em que soltou (para o arco sumir aos poucos). */
    private static final Map<Integer, Long> RELEASED = new HashMap<>();

    private KiBowRenderer() {}

    public static void onRelease(Entity owner) {
        if (owner.level() != null) RELEASED.put(owner.getId(), owner.level().getGameTime());
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();

        Map<Integer, Float> bows = new HashMap<>();
        for (AbstractKiProjectile p : mc.level.getEntitiesOfClass(AbstractKiProjectile.class, new AABB(cam, cam).inflate(96),
                e -> KiArrowTechnique.ID.equals(e.getTechniqueId()))) {
            if (p.getOwner() instanceof LivingEntity owner) {
                float draw = Math.min(1f, (p.tickCount + partial) / 40f);
                bows.merge(owner.getId(), draw, Math::max);
            }
        }
        RELEASED.entrySet().removeIf(en -> now - en.getValue() > 8);
        for (Map.Entry<Integer, Long> en : RELEASED.entrySet()) bows.putIfAbsent(en.getKey(), -1f);
        if (bows.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = event.getPoseStack().last().pose();

        for (Map.Entry<Integer, Float> en : bows.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity owner)) continue;
            float draw = en.getValue();
            float fade = 1f;
            if (draw < 0f) { // acabou de soltar: corda relaxada, arco sumindo
                Long t = RELEASED.get(en.getKey());
                fade = t == null ? 0f : Math.max(0f, 1f - (now - t + partial) / 8f);
                draw = 0f;
            }
            drawBow(buf, m, cam, owner, partial, draw, fade);
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    private static void drawBow(BufferBuilder buf, Matrix4f m, Vec3 cam, LivingEntity p, float partial, float draw, float fade) {
        Vec3 look = p.getViewVector(partial).normalize();
        double yaw = Math.toRadians(p.getViewYRot(partial));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = look.cross(left).normalize();
        Vec3 center = p.getPosition(partial).add(0, p.getBbHeight() * 0.78, 0).add(look.scale(0.6)).add(left.scale(0.22));

        float r = ((COLOR >> 16) & 0xFF) / 255f, g = ((COLOR >> 8) & 0xFF) / 255f, b = (COLOR & 0xFF) / 255f;
        int seg = 12;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double t = -1.0 + 2.0 * i / seg;
            Vec3 pt = center.add(up.scale(0.62 * t)).subtract(look.scale(0.2 * t * t));
            if (prev != null) {
                ribbon(buf, m, cam, prev, pt, 0.07f, r, g, b, 0.45f * fade);
                ribbon(buf, m, cam, prev, pt, 0.025f, 1f, 1f, 1f, 0.9f * fade);
            }
            prev = pt;
        }
        Vec3 top = center.add(up.scale(0.62)).subtract(look.scale(0.2));
        Vec3 bottom = center.add(up.scale(-0.62)).subtract(look.scale(0.2));
        Vec3 nock = center.subtract(look.scale(0.2 + 0.45 * draw));
        ribbon(buf, m, cam, top, nock, 0.012f, 1f, 1f, 1f, 0.85f * fade);
        ribbon(buf, m, cam, nock, bottom, 0.012f, 1f, 1f, 1f, 0.85f * fade);
        if (draw > 0.12f && fade > 0.99f) {
            Vec3 tip = center.add(look.scale(0.25 + 0.1 * draw));
            float w = 0.03f + 0.03f * draw;
            ribbon(buf, m, cam, nock, tip, w * 2.6f, r, g, b, 0.5f);
            ribbon(buf, m, cam, nock, tip, w, 1f, 1f, 1f, 0.95f);
        }
    }

    /** Fita voltada para a camera entre dois pontos (coordenadas do mundo). */
    private static void ribbon(BufferBuilder buf, Matrix4f m, Vec3 cam, Vec3 a, Vec3 c, float width, float r, float g, float b, float alpha) {
        Vec3 dir = c.subtract(a);
        Vec3 toCam = cam.subtract(a.add(c).scale(0.5));
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width);
        Vec3 a1 = a.add(side).subtract(cam), a2 = a.subtract(side).subtract(cam);
        Vec3 c1 = c.add(side).subtract(cam), c2 = c.subtract(side).subtract(cam);
        buf.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, b, alpha).endVertex();
        buf.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).color(r, g, b, alpha).endVertex();
        buf.vertex(m, (float) c2.x, (float) c2.y, (float) c2.z).color(r, g, b, alpha).endVertex();
        buf.vertex(m, (float) c1.x, (float) c1.y, (float) c1.z).color(r, g, b, alpha).endVertex();
    }
}
