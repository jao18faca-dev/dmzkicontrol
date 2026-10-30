package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Mira magica do Olho Cereal: enquanto o Cerealiano usa o olho, circulos de ki flutuam na frente do olho
 * direito (dos dois olhos nos Olhos Gemeos), girando, na cor do ki dele. Todo mundo por perto ve.
 * Em primeira pessoa nao aparece para voce mesmo (o HUD do olho ja faz esse papel).
 */
public final class EyeReticleRenderer {
    private static final int FALLBACK = 0xFFB347;
    /** entityId -> tick em que ligou (para a animacao de abrir). */
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    /** entityId -> tick em que desligou (para fechar suave). */
    private static final Map<Integer, Long> CLOSING = new HashMap<>();

    private EyeReticleRenderer() {}

    public static void set(int entityId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (on) {
            CLOSING.remove(entityId);
            ACTIVE.putIfAbsent(entityId, now);
        } else if (ACTIVE.remove(entityId) != null) {
            CLOSING.put(entityId, now);
        }
    }

    public static void clear() {
        ACTIVE.clear();
        CLOSING.clear();
    }

    private static boolean twinEyes(Player p) {
        try {
            String twin = KiControlConfig.get().cerealian.twinEyesForm;
            if (twin == null || twin.isEmpty()) return false;
            return StatsProvider.get(StatsCapability.INSTANCE, p).resolve().map(d -> {
                var c = d.getCharacter();
                return c.hasActiveForm() && c.getActiveForm() != null && c.getActiveForm().toLowerCase(Locale.ROOT).contains(twin);
            }).orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty() && CLOSING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        for (Map.Entry<Integer, Long> en : ACTIVE.entrySet()) {
            float open = Mth.clamp((now - en.getValue() + partial) / 6f, 0f, 1f);
            draw(vc, m, cam, mc.level.getEntity(en.getKey()), open, now + partial, firstPerson, partial);
        }
        Iterator<Map.Entry<Integer, Long>> it = CLOSING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> en = it.next();
            float k = 1f - (now - en.getValue() + partial) / 5f;
            if (k <= 0f) { it.remove(); continue; }
            draw(vc, m, cam, mc.level.getEntity(en.getKey()), k, now + partial, firstPerson, partial);
        }
        bs.endBatch(RenderType.lightning());
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Vec3 cam, Entity e, float open, float time,
                             boolean firstPerson, float partial) {
        if (!(e instanceof Player p) || p.isInvisible() || open <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && firstPerson) return;

        float yaw = (float) Math.toRadians(Mth.lerp(partial, p.yHeadRotO, p.yHeadRot));
        float pitch = (float) Math.toRadians(Mth.lerp(partial, p.xRotO, p.getXRot()));
        Vec3 fwd = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();

        Vec3 eye = p.getEyePosition(partial).subtract(cam);
        float[] rgb = KiFx.kiColor(p, FALLBACK);
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.25f + p.getId());
        float s = p.getScale();
        boolean both = twinEyes(p);

        drawEye(vc, m, eye.add(right.scale(0.11 * s)), fwd, right, up, rgb, open * pulse, time, s);
        if (both) drawEye(vc, m, eye.add(right.scale(-0.11 * s)), fwd, right, up, rgb, open * pulse, -time, s);
    }

    /** Dois circulos magicos empilhados na frente do olho, girando em sentidos opostos, com marcas de mira. */
    private static void drawEye(VertexConsumer vc, Matrix4f m, Vec3 eye, Vec3 fwd, Vec3 right, Vec3 up,
                                float[] rgb, float a, float time, float s) {
        Vec3 near = eye.add(fwd.scale(0.30 * s));
        Vec3 far = eye.add(fwd.scale(0.52 * s));
        float grow = 0.6f + 0.4f * a;

        // perto: anel fino + miolo
        ring(vc, m, near, right, up, 0.050f * s * grow, 0.0045f * s, 32, rgb, 0.9f * a, 0f, 0);
        disc(vc, m, near, right, up, 0.010f * s, 12, rgb, 0.9f * a);
        // longe: anel maior girando, com 4 marcas e tracos
        float spin = time * 0.06f;
        ring(vc, m, far, right, up, 0.10f * s * grow, 0.004f * s, 48, rgb, 0.8f * a, 0f, 0);
        ring(vc, m, far, right, up, 0.078f * s * grow, 0.003f * s, 48, rgb, 0.6f * a, -spin * 1.6f, 12); // tracejado
        for (int i = 0; i < 4; i++) {
            float ang = spin + i * Mth.HALF_PI;
            tick(vc, m, far, right, up, ang, 0.092f * s * grow, 0.135f * s * grow, 0.0045f * s, rgb, 0.9f * a);
        }
        // brilho suave atras do circulo de longe
        disc(vc, m, far, right, up, 0.095f * s * grow, 24, rgb, 0.10f * a);
    }

    private static Vec3 at(Vec3 c, Vec3 right, Vec3 up, float ang, float r) {
        return c.add(right.scale(Mth.cos(ang) * r)).add(up.scale(Mth.sin(ang) * r));
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float[] rgb, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(rgb[0], rgb[1], rgb[2], Mth.clamp(a, 0f, 1f)).endVertex();
    }

    /** Anel; dashes > 0 desenha so metade dos segmentos (tracejado). */
    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, float w, int seg,
                             float[] rgb, float a, float phase, int dashes) {
        for (int i = 0; i < seg; i++) {
            if (dashes > 0 && (i * dashes / seg) % 2 == 1) continue;
            float a0 = phase + Mth.TWO_PI * i / seg, a1 = phase + Mth.TWO_PI * (i + 1) / seg;
            v(vc, m, at(c, right, up, a0, r - w), rgb, a);
            v(vc, m, at(c, right, up, a0, r + w), rgb, a);
            v(vc, m, at(c, right, up, a1, r + w), rgb, a);
            v(vc, m, at(c, right, up, a1, r - w), rgb, a);
        }
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, int seg, float[] rgb, float a) {
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            v(vc, m, c, rgb, a);
            v(vc, m, at(c, right, up, a0, r), rgb, a * 0.3f);
            v(vc, m, at(c, right, up, a1, r), rgb, a * 0.3f);
            v(vc, m, c, rgb, a);
        }
    }

    private static void tick(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float ang, float r0, float r1,
                             float w, float[] rgb, float a) {
        Vec3 dir = right.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
        Vec3 side = right.scale(-Mth.sin(ang)).add(up.scale(Mth.cos(ang))).scale(w);
        Vec3 p0 = c.add(dir.scale(r0)), p1 = c.add(dir.scale(r1));
        v(vc, m, p0.subtract(side), rgb, a);
        v(vc, m, p1.subtract(side), rgb, a * 0.4f);
        v(vc, m, p1.add(side), rgb, a * 0.4f);
        v(vc, m, p0.add(side), rgb, a);
    }
}
