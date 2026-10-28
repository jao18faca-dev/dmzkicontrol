package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.EyeStateC2S;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Olho Cereal, lado do cliente: segurar a tecla da zoom de atirador, contorna quem tem ki ao redor
 * (atraves das paredes) e mostra a mira com o alvo e o ponto fraco marcado.
 */
public final class CerealianEyeClient implements IGuiOverlay {
    public static final CerealianEyeClient OVERLAY = new CerealianEyeClient();

    /** Padrao: B (segurar). */
    public static final KeyMapping EYE_KEY = new KeyMapping(
            "key.dmzkicontrol.cereal_eye",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.dmzkicontrol");

    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    private static final int BRONZE = 0xE0A04A;

    private static boolean active;
    private static boolean wasDown;
    private static float zoom, zoomO;
    private static int markedId = -1;
    private static long markEnd;
    private static final Set<Integer> GLOWING = new HashSet<>();

    private CerealianEyeClient() {}

    public static boolean isCerealian() {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        return StatsProvider.get(StatsCapability.INSTANCE, p).resolve()
                .map(d -> d.getCharacter().getRaceName())
                .map(r -> r != null && r.toLowerCase(Locale.ROOT).equals(KiControlConfig.get().cerealian.raceKey.toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    public static void onMark(int id, int ticks) {
        markedId = id;
        var level = Minecraft.getInstance().level;
        markEnd = level == null ? 0 : level.getGameTime() + ticks;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        zoomO = zoom;
        zoom = Mth.lerp(0.35f, zoom, active ? 1f : 0f);
        if (mc.player == null) { reset(); return; }

        boolean down = EYE_KEY.isDown() && mc.screen == null;
        if (down && !wasDown) {
            if (isCerealian()) {
                active = true;
                KiControlNetwork.CHANNEL.sendToServer(new EyeStateC2S(true));
            } else {
                mc.player.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_not_cerealian"), true);
            }
        } else if (!down && wasDown && active) {
            setInactive();
        }
        wasDown = down;
        while (EYE_KEY.consumeClick()) { /* tecla de segurar */ }

        if (active) updateGlow(mc);
        else if (!GLOWING.isEmpty()) clearGlow(mc);
    }

    private static void setInactive() {
        active = false;
        KiControlNetwork.CHANNEL.sendToServer(new EyeStateC2S(false));
        clearGlow(Minecraft.getInstance());
    }

    /** Contorno (so no seu cliente) em todos com ki ao alcance do olho. */
    private static void updateGlow(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        double range = KiControlConfig.get().cerealian.eyeRange;
        Set<Integer> now = new HashSet<>();
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(range),
                e -> e != mc.player && e.isAlive() && !e.isSpectator())) {
            e.setGlowingTag(true);
            now.add(e.getId());
        }
        for (Iterator<Integer> it = GLOWING.iterator(); it.hasNext(); ) {
            int id = it.next();
            if (!now.contains(id)) { unglow(mc, id); it.remove(); }
        }
        GLOWING.addAll(now);
    }

    private static void clearGlow(Minecraft mc) {
        for (int id : GLOWING) unglow(mc, id);
        GLOWING.clear();
    }

    private static void unglow(Minecraft mc, int id) {
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e instanceof LivingEntity le && !le.hasEffect(MobEffects.GLOWING)) le.setGlowingTag(false);
    }

    public static void reset() {
        active = false;
        wasDown = false;
        zoom = zoomO = 0f;
        markedId = -1;
        GLOWING.clear();
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        float z = Mth.lerp((float) event.getPartialTick(), zoomO, zoom);
        if (z > 0.001f) event.setFOV(event.getFOV() * Mth.lerp(z, 1.0, 0.4));
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        float z = Mth.lerp(partialTick, zoomO, zoom);
        if (z < 0.02f || mc.player == null || mc.options.hideGui) return;

        // bordas cor de bronze
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(((BRONZE >> 16) & 0xFF) / 255f, ((BRONZE >> 8) & 0xFF) / 255f, (BRONZE & 0xFF) / 255f, 0.35f * z);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // mira
        int cx = w / 2, cy = h / 2;
        int col = DmzStyle.argb(0.85f * z, BRONZE);
        int r = 14;
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
        g.fill(cx - r - 6, cy, cx - r + 3, cy + 1, col);
        g.fill(cx + r - 2, cy, cx + r + 7, cy + 1, col);
        g.fill(cx, cy - r - 6, cx + 1, cy - r + 3, col);
        g.fill(cx, cy + r - 2, cx + 1, cy + r + 7, col);
        g.fill(cx, cy, cx + 1, cy + 1, DmzStyle.argb(z, 0xFFFFFF));

        DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.eye_title"), cx, 8f, 1.2f, 0.5f, BRONZE, z);

        // alvo na mira
        LivingEntity target = lookTarget(mc, KiControlConfig.get().cerealian.eyeRange, partialTick);
        float ty = cy + r + 12;
        if (target != null) {
            int dist = Math.round(mc.player.distanceTo(target));
            DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.eye_target", target.getDisplayName(), dist),
                    cx, ty, 0.9f, 0.5f, 0xFFFFFF, z);
            ty += 11;
        }
        var level = mc.level;
        if (markedId >= 0 && level != null && level.getGameTime() < markEnd) {
            Entity marked = level.getEntity(markedId);
            if (marked != null) {
                int secs = (int) Math.ceil((markEnd - level.getGameTime()) / 20.0);
                DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.eye_marked", marked.getDisplayName(), secs),
                        cx, ty, 0.9f, 0.5f, 0xFF7050, z);
            }
        }
    }

    private static LivingEntity lookTarget(Minecraft mc, double range, float partial) {
        var p = mc.player;
        if (p == null || mc.level == null) return null;
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        Vec3 end = eye.add(look.scale(range));
        var blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double max = blockHit.getLocation().distanceToSqr(eye);
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != p && !e.isSpectator(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }
}
