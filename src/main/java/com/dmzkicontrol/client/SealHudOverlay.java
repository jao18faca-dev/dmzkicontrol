package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Medidor de Tensao do selo no estilo dos medidores laterais do DragonMineZ (Reserva de Ki / Furia):
 * mesma moldura, mesmo preenchimento e mesma capsula (sprites do proprio DMZ), fonte "smooth" do DMZ.
 * Fica do lado direito por padrao, espelhando os medidores do DMZ que ficam na esquerda.
 */
public final class SealHudOverlay implements IGuiOverlay {
    public static final SealHudOverlay INSTANCE = new SealHudOverlay();

    private static final ResourceLocation ICON = new ResourceLocation(KiControlMod.MODID, "textures/gui/seal_icon.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft", "textures/misc/vignette.png");

    // Sprites do hud_reserve.png do DMZ (em texels; o DMZ desenha 2 texels por pixel de GUI)
    private static final int FRAME_U = 0, FRAME_V = 0, FRAME_W = 23, FRAME_H = 150;
    private static final int FILL_U = 25, FILL_V = 0, FILL_W = 17, FILL_H = 144;
    private static final int SHINE_U = 44;
    private static final int CAPSULE_U = 63, CAPSULE_V = 0, CAPSULE = 23;
    private static final int BORDER = 3;

    private static final int CALM = 0x5FD8FF, TENSE = 0xFFD24A, PAIN = 0xFF3B3B, PERFECT = 0xC77DFF, IDLE = 0x8A8F99;

    private float shown;      // valor exibido (suavizado)
    private float visibility; // fade in/out

    private SealHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientSealState.hasData()) return;
        KiControlClientConfig cfg = KiControlClientConfig.INSTANCE;

        boolean sealed = ClientSealState.sealed;
        boolean perfect = ClientSealState.perfect && sealed;
        float strain = ClientSealState.strain;
        int burst = ClientSealState.burstTicksLeft();
        float time = (mc.player.tickCount + partialTick) / 20f;

        if (cfg.vignette.get() && sealed) drawVignette(g, screenW, screenH, strain, perfect, time);
        if (!cfg.showHud.get()) return;

        boolean wanted = sealed || ClientSealState.pending || strain > 0.005f || burst > 0;
        visibility = Mth.lerp(0.15f, visibility, wanted ? 1f : 0f);
        if (visibility < 0.02f) return;
        float target = perfect ? 1f : strain;
        shown = Mth.lerp(0.2f, shown, target);

        // cor do preenchimento
        int color;
        if (perfect) color = PERFECT;
        else if (strain < ClientSealState.fatigueAt) color = DmzStyle.mix(CALM, TENSE, strain / Math.max(0.01f, ClientSealState.fatigueAt));
        else color = DmzStyle.mix(TENSE, PAIN, (strain - ClientSealState.fatigueAt) / Math.max(0.01f, ClientSealState.hurtAt - ClientSealState.fatigueAt));
        if (!sealed && !perfect) color = DmzStyle.mix(color, IDLE, 0.55f);
        float pulse = 0f;
        if (sealed && strain >= ClientSealState.hurtAt) pulse = 0.5f + 0.5f * Mth.sin(time * 11f);
        else if (ClientSealState.pending) pulse = 0.5f + 0.5f * Mth.sin(time * 14f);
        else if (perfect) pulse = 0.25f + 0.25f * Mth.sin(time * 2.5f);
        int fillColor = DmzStyle.mix(color, 0xFFFFFF, pulse * 0.35f);

        // posicao (em pixels de GUI)
        float scale = (float) (double) cfg.scale.get();
        float guiW = CAPSULE / 2f, guiH = (CAPSULE + BORDER + FILL_H + BORDER) / 2f + 12f;
        float x = cfg.meterSide.get() == KiControlClientConfig.MeterSide.RIGHT ? screenW - 2f - guiW * scale : 2f;
        float y = screenH / 2f - guiH * scale / 2f;
        x += cfg.offsetX.get();
        y += cfg.offsetY.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale * 0.5f, scale * 0.5f, 1f); // espaco de texels, como o DMZ
        try {
            int barX = BORDER, barY = CAPSULE + BORDER;
            // moldura
            tint(0xFFFFFF, visibility);
            blit(g, DmzStyle.RESERVE_SHEET, 0, CAPSULE, FRAME_U, FRAME_V, FRAME_W, FRAME_H);
            // preenchimento (de baixo para cima)
            int fillPx = Math.round(FILL_H * Mth.clamp(shown, 0f, 1f));
            if (fillPx > 0) {
                int top = FILL_H - fillPx;
                tint(fillColor, visibility);
                blit(g, DmzStyle.RESERVE_SHEET, barX, barY + top, FILL_U, FILL_V + top, FILL_W, fillPx);
                tint(0xFFFFFF, visibility * 0.8f);
                blit(g, DmzStyle.RESERVE_SHEET, barX, barY + top, SHINE_U, FILL_V + top, FILL_W, fillPx);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                g.fill(barX, barY + top, barX + FILL_W, barY + top + 2, DmzStyle.argb(0.7f * visibility, 0xFFFFFF));
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // marcas de cansaco e dor
            if (!perfect) {
                mark(g, barX, barY, ClientSealState.fatigueAt);
                mark(g, barX, barY, ClientSealState.hurtAt);
            }
            // capsula + icone
            tint(0xFFFFFF, visibility);
            blit(g, DmzStyle.RESERVE_SHEET, 0, 0, CAPSULE_U, CAPSULE_V, CAPSULE, CAPSULE);
            int iconColor = burst > 0 ? DmzStyle.mix(0xFFC040, 0xFFFFFF, 0.3f + 0.3f * Mth.sin(time * 10f))
                    : DmzStyle.mix(color, 0xFFFFFF, 0.45f + pulse * 0.3f);
            tint(iconColor, visibility);
            RenderSystem.setShaderTexture(0, ICON);
            g.blit(ICON, BORDER, BORDER, 0, 0, 17, 17, 17, 17);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // textos
            float textY = CAPSULE + BORDER * 2 + FILL_H + 4;
            String top = perfect ? "MAX" : Math.round(strain * 100f) + "%";
            DmzStyle.text(g, Component.literal(top), CAPSULE / 2f, textY, 1.0f, 0.5f, DmzStyle.mix(color, 0xFFFFFF, 0.7f), visibility);
            if (!ClientSealState.formKey.isEmpty()) {
                DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.mastery_short", Math.round(ClientSealState.mastery * 100f)),
                        CAPSULE / 2f, textY + 11, 0.8f, 0.5f, 0xBFEFFF, visibility * 0.9f);
            }
            if (burst > 0) {
                DmzStyle.text(g, Component.translatable("hud.dmzkicontrol.burst_short", (burst + 19) / 20),
                        CAPSULE / 2f, -11, 0.8f, 0.5f, 0xFFC040, visibility);
            }
        } finally {
            g.pose().popPose();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static void mark(GuiGraphics g, int barX, int barY, float at) {
        int my = barY + Math.round(FILL_H * (1f - at));
        g.fill(barX - 2, my, barX + FILL_W + 2, my + 1, 0xB0000000);
        g.fill(barX - 2, my - 1, barX, my + 2, 0xC0FFFFFF);
    }

    private static void tint(int rgb, float alpha) {
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                Math.max(0f, Math.min(1f, alpha)));
    }

    private static void blit(GuiGraphics g, ResourceLocation sheet, int x, int y, int u, int v, int w, int h) {
        g.blit(sheet, x, y, u, v, w, h, DmzStyle.SHEET_W, DmzStyle.SHEET_H);
    }

    /** Brilho nas bordas: azul-aura calmo, laranja no cansaco, vermelho pulsando na dor. */
    private static void drawVignette(GuiGraphics g, int w, int h, float strain, boolean perfect, float time) {
        int color;
        float alpha;
        if (perfect) { color = PERFECT; alpha = 0.10f + 0.04f * Mth.sin(time * 2f); }
        else if (strain >= ClientSealState.hurtAt) { color = PAIN; alpha = 0.32f + 0.12f * Mth.sin(time * 9f); }
        else if (strain >= ClientSealState.fatigueAt) { color = 0xFF9A3C; alpha = 0.18f; }
        else { color = CALM; alpha = 0.10f; }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        tint(color, alpha);
        g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
