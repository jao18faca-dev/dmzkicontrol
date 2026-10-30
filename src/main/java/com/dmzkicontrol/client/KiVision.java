package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.kivision.BlindfoldItem;
import com.dmzkicontrol.kivision.BlindfoldTraining;
import com.dragonminez.client.systems.taiyoken.TaiyokenBlindState;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Visao de ki (luta as cegas), so para o jogador local.
 *
 * <p>Voce nao enxerga a luz, sente o ki: o mundo apaga (so um contorno de poucos blocos em volta),
 * e quem esta por perto aparece como uma silhueta brilhando na cor do proprio ki, mais forte quanto
 * maior o poder, ate atraves das paredes. Rajadas de ki aparecem como riscos de luz. Quem esta com a
 * aura selada (Aura Control) quase some.
 *
 * <p>Liga com a Venda de Treino na cabeca, ou quando um Taiyoken te cega e voce tem Ki Sense:
 * o clarao do DMZ passa rapido e voce fecha os olhos e luta pelo ki ate a cegueira acabar.
 */
public final class KiVision {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    private static final float FOG_R = 0.012f, FOG_G = 0.016f, FOG_B = 0.040f;
    private static final int PLAYER_KI = 0xCFE6FF, CALM_KI = 0x9FD8FF, EVIL_KI = 0xFF4A4A, BLAST_KI = 0xFFF1C2;
    private static final int PULSE_EVERY = 40, PULSE_GROW = 25;

    /** Ticks de visao vindos de um Taiyoken. */
    private static int visionTicks;
    private static int visionTotal;
    private static int blindSeen;
    private static boolean blindfold;
    private static int kiSense;
    private static float fade, fadePrev;
    private static boolean wantedBefore;
    private static long pulseStart = Long.MIN_VALUE;
    /** Durante o passe das silhuetas (esconde nomes). */
    private static boolean ghostPass;
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();
    private static MultiBufferSource.BufferSource buffers;

    private KiVision() {}

    // ------------------------------------------------------------------ estado

    private static KiControlConfig.KiVision cfg() {
        return KiControlConfig.get().kiVision;
    }

    private static boolean wanted() {
        return visionTicks > 0 || blindfold;
    }

    public static boolean active() {
        return fade > 0.001f || fadePrev > 0.001f;
    }

    private static float fade(float partial) {
        float f = Mth.lerp(partial, fadePrev, fade);
        return f * f * (3f - 2f * f);
    }

    public static double range() {
        KiControlConfig.KiVision c = cfg();
        return Math.min(c.maxRange, c.baseRange + c.rangePerKiSenseLevel * kiSense);
    }

    public static void reset() {
        visionTicks = visionTotal = blindSeen = 0;
        blindfold = false;
        fade = fadePrev = 0f;
        wantedBefore = false;
        pulseStart = Long.MIN_VALUE;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !cfg().enabled) {
            reset();
            return;
        }
        kiSense = BlindfoldTraining.kiSense(p);
        blindfold = BlindfoldItem.isWearing(p);

        // Taiyoken do DMZ: quem sente ki deixa o clarao passar e luta de olhos fechados
        boolean blind = false;
        try { blind = TaiyokenBlindState.isActive(); } catch (Throwable ignored) {}
        if (blind) {
            blindSeen++;
            int needed = blindfold ? 0 : cfg().taiyokenFlashTicks;
            if ((kiSense > 0 || blindfold) && blindSeen > needed) {
                int left = 0;
                try {
                    left = TaiyokenBlindState.getTicksRemaining();
                    TaiyokenBlindState.clear();
                } catch (Throwable ignored) {}
                if (left > visionTicks) {
                    visionTicks = left;
                    visionTotal = left;
                }
            }
        } else blindSeen = 0;

        if (visionTicks > 0) visionTicks--;
        boolean want = wanted();
        if (want && !wantedBefore) {
            pulseStart = mc.level.getGameTime();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.7f, 0.35f));
        }
        wantedBefore = want;
        fadePrev = fade;
        fade = want ? Math.min(1f, fade + 0.10f) : Math.max(0f, fade - 0.07f);

        if (want && pulseStart != Long.MIN_VALUE && (mc.level.getGameTime() - pulseStart) % PULSE_EVERY == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.55f, 0.18f));
        }
    }

    // ------------------------------------------------------------------ neblina e ceu

    public static void onFog(ViewportEvent.RenderFog event) {
        if (!active()) return;
        float k = fade((float) event.getPartialTick());
        float far = (float) Mth.lerp(k, event.getFarPlaneDistance(), (float) cfg().sightBlocks);
        event.setNearPlaneDistance(Mth.lerp(k, event.getNearPlaneDistance(), 0f));
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active()) return;
        float k = fade((float) event.getPartialTick());
        event.setRed(Mth.lerp(k, event.getRed(), FOG_R));
        event.setGreen(Mth.lerp(k, event.getGreen(), FOG_G));
        event.setBlue(Mth.lerp(k, event.getBlue(), FOG_B));
    }

    /** Durante o passe das silhuetas nenhum nome e desenhado. */
    public static void onNameTag(RenderNameTagEvent event) {
        if (ghostPass) event.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ mundo

    public static void render(RenderLevelStageEvent event) {
        if (!active()) return;
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            coverSky(event.getPoseStack(), fade(event.getPartialTick()));
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            renderGhosts(event);
        }
    }

    /** Sol, lua e estrelas nao passam pela neblina: um cubo escuro em volta da camera, antes do terreno. */
    private static void coverSky(PoseStack pose, float alpha) {
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float s = 30f;
        float[][] faces = {
                {-s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s},
                {-s, -s, s, -s, s, s, s, s, s, s, -s, s},
                {-s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s},
                {s, -s, -s, s, -s, s, s, s, s, s, s, -s},
                {-s, s, -s, s, s, -s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) b.vertex(m, f[i], f[i + 1], f[i + 2]).color(FOG_R, FOG_G, FOG_B, alpha).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) buffers = MultiBufferSource.immediate(new BufferBuilder(1 << 16));
        return buffers;
    }

    private static double bp(Entity e) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, e).resolve().map(d -> {
                try { return d.getBattlePowerExact(); } catch (Throwable t) { return 0.0; }
            }).orElse(0.0);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** 0 = ignora, 1 = ser vivo, 2 = rajada de ki. */
    private static int kind(Entity e) {
        if (e instanceof LivingEntity le) return le.isSpectator() || !le.isAlive() ? 0 : 1;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null) return 0;
        String ns = id.getNamespace(), path = id.getPath();
        if (ns.equals("dmzkicontrol")) return path.startsWith("cereal_") ? 2 : 0;
        if (!ns.equals("dragonminez") && !ns.equals("dmzsparking") && !ns.equals("noeabosses")) return 0;
        return path.startsWith("ki_") || path.contains("blast") || path.contains("beam") || path.contains("wave")
                || path.contains("laser") || path.contains("disc") || path.contains("grenade") || path.contains("ball") ? 2 : 0;
    }

    private static void renderGhosts(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;
        KiControlConfig.KiVision c = cfg();
        float partial = event.getPartialTick();
        float k = fade(partial);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        double range = range();
        double selfBp = Math.max(1.0, bp(self));
        float time = mc.level.getGameTime() + partial;
        MultiBufferSource.BufferSource src = buffers();
        Ghost ghost = new Ghost();
        GhostSource gsrc = new GhostSource(ghost, src);

        ghostPass = true;
        try {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == self || BROKEN.contains(e.getType())) continue;
                int kind = kind(e);
                if (kind == 0) continue;
                boolean sealed = kind == 1 && e instanceof Player && SealAnimations.isSealed(e.getId());
                double reach = sealed ? range * c.sealedVisibility : range;
                double d = e.distanceTo(self);
                if (d > reach) continue;

                int color;
                float strength;
                if (kind == 2) {
                    color = BLAST_KI;
                    strength = 0.95f;
                } else {
                    double other = bp(e);
                    if (other > 0) {
                        strength = (float) Mth.clamp(0.55 + 0.22 * Math.log10(other / selfBp), 0.2, 1.0);
                        float[] rgb = KiFx.kiColor(e, PLAYER_KI);
                        color = ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
                    } else {
                        boolean evil = e instanceof Enemy;
                        strength = evil ? 0.55f : 0.38f;
                        color = evil ? EVIL_KI : CALM_KI;
                    }
                    if (sealed) strength *= (float) c.sealedVisibility;
                }
                float edge = (float) (1.0 - Mth.clamp((d - reach * 0.7) / (reach * 0.3), 0.0, 1.0));
                float breath = 0.85f + 0.15f * Mth.sin((time + e.getId() * 7f) * 0.12f);
                float alpha = 0.24f * strength * edge * breath * k;
                if (alpha < 0.01f) continue;
                ghost.set(color, alpha);

                pose.pushPose();
                try {
                    double x = Mth.lerp(partial, e.xOld, e.getX()) - cam.x;
                    double y = Mth.lerp(partial, e.yOld, e.getY()) - cam.y;
                    double z = Mth.lerp(partial, e.zOld, e.getZ()) - cam.z;
                    EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(e);
                    Vec3 off = renderer.getRenderOffset(e, partial);
                    pose.translate(x + off.x, y + off.y, z + off.z);
                    renderer.render(e, Mth.lerp(partial, e.yRotO, e.getYRot()), partial, pose, gsrc, LightTexture.FULL_BRIGHT);
                } catch (Throwable t) {
                    BROKEN.add(e.getType()); // esse desenho nao combina com a visao de ki: pula daqui em diante
                } finally {
                    pose.popPose();
                }
                src.endBatch();
            }
            pulse(pose, cam, self, partial, range, k, src);
        } finally {
            ghostPass = false;
        }
    }

    /** Onda de ki que sai de voce de tempos em tempos, como um radar. */
    private static void pulse(PoseStack pose, Vec3 cam, LocalPlayer self, float partial, double range, float k,
                              MultiBufferSource.BufferSource src) {
        if (pulseStart == Long.MIN_VALUE || self.level() == null) return;
        float age = (float) ((self.level().getGameTime() - pulseStart) % PULSE_EVERY) + partial;
        if (age > PULSE_GROW) return;
        float t = age / PULSE_GROW;
        float radius = (float) (range * (1f - (1f - t) * (1f - t)));
        float alpha = 0.45f * (1f - t) * k;
        if (alpha < 0.01f) return;
        float[] rgb = KiFx.kiColor(self, PLAYER_KI);
        double px = Mth.lerp(partial, self.xOld, self.getX()) - cam.x;
        double py = Mth.lerp(partial, self.yOld, self.getY()) - cam.y + 0.08;
        double pz = Mth.lerp(partial, self.zOld, self.getZ()) - cam.z;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = src.getBuffer(KiVisionRenderTypes.GHOST);
        int seg = 72;
        float in = Math.max(0f, radius - 0.18f), out = radius + 0.18f;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.vertex(m, (float) (px + c0 * in), (float) py, (float) (pz + s0 * in)).color(rgb[0], rgb[1], rgb[2], 0f).endVertex();
            vc.vertex(m, (float) (px + c0 * out), (float) py, (float) (pz + s0 * out)).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            vc.vertex(m, (float) (px + c1 * out), (float) py, (float) (pz + s1 * out)).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            vc.vertex(m, (float) (px + c1 * in), (float) py, (float) (pz + s1 * in)).color(rgb[0], rgb[1], rgb[2], 0f).endVertex();
        }
        src.endBatch(KiVisionRenderTypes.GHOST);
    }

    // ------------------------------------------------------------------ HUD

    public static final IGuiOverlay OVERLAY = (gui, g, partial, w, h) -> {
        if (!active()) return;
        float k = fade(partial);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // bordas escuras
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        float v = 0.95f * k;
        RenderSystem.setShaderColor(v, v, v, 1f);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // 心眼 ("olho da mente") em pe, do lado direito
        float[] rgb = KiFx.kiColor(mc.player, PLAYER_KI);
        int col = ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
        float time = mc.player.tickCount + partial;
        float breath = 0.75f + 0.25f * Mth.sin(time * 0.1f);
        Font font = mc.font;
        String[] chars = {"心", "眼"};
        float x = w - 24f, y = h * 0.30f;
        for (String ch : chars) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            g.pose().scale(2.4f, 2.4f, 1f);
            g.drawString(font, ch, -font.width(ch) / 2, 0, DmzStyle.argb(0.6f * k * breath, col), false);
            g.pose().popPose();
            y += 26f;
        }

        Component label = visionTicks > 0 && !blindfold
                ? Component.translatable("hud.dmzkicontrol.kivision_time", (visionTicks + 19) / 20)
                : Component.translatable("hud.dmzkicontrol.kivision");
        DmzStyle.text(g, label, w / 2f, h - 64f, 0.9f, 0.5f, col, 0.85f * k);
    };

    // ------------------------------------------------------------------ silhueta

    /** Troca qualquer desenho de modelo por cor de ki pura no tipo GHOST. */
    private static final class GhostSource implements MultiBufferSource {
        private final Ghost ghost;
        private final MultiBufferSource.BufferSource src;
        private static final VertexConsumer NONE = new Ghost();

        GhostSource(Ghost ghost, MultiBufferSource.BufferSource src) {
            this.ghost = ghost;
            this.src = src;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (type.mode() != VertexFormat.Mode.QUADS) return NONE;
            ghost.out = src.getBuffer(KiVisionRenderTypes.GHOST);
            return ghost;
        }
    }

    private static final class Ghost implements VertexConsumer {
        VertexConsumer out;
        int r, g, b, a;

        void set(int rgb, float alpha) {
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
            a = Mth.clamp((int) (alpha * 255), 0, 255);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            if (out != null) out.vertex(x, y, z).color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            if (out != null) out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
