package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlClientConfig;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dmzkicontrol.network.ToggleSealC2S;
import com.dragonminez.client.gui.UtilityMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Tudo que so existe no cliente: tecla, HUD, cutscene e o botao no menu utilitario do DMZ. */
public final class ClientSetup {

    /**
     * Padrao: N. O G e a tecla de acao/transformacao do DMZ, entao saimos dela.
     * (Id novo de proposito: quem tinha o G salvo da versao anterior ganha o N automaticamente.)
     */
    public static final KeyMapping SEAL_KEY = new KeyMapping(
            "key.dmzkicontrol.seal_aura",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.dmzkicontrol");

    private static final ResourceLocation CUTSCENE_OVERLAY = new ResourceLocation(KiControlMod.MODID, "aura_cutscene");
    private static boolean wasDown;

    private ClientSetup() {}

    public static boolean cutsceneEnabled() {
        return switch (KiControlClientConfig.INSTANCE.cutscene.get()) {
            case ALWAYS -> true;
            case ONCE_PER_SESSION -> !CutsceneController.playedThisSession();
            case OFF -> false;
        };
    }

    /** Uma acao por aperto (segurar a tecla nao fica alternando). */
    public static void onSealPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (CutsceneController.isActive()) {          // pular a cena: o selo vale na hora
            CutsceneController.stop();
            KiControlNetwork.CHANNEL.sendToServer(new ToggleSealC2S(false));
            return;
        }
        boolean cinematic = !ClientSealState.sealed && !ClientSealState.pending && cutsceneEnabled();
        KiControlNetwork.CHANNEL.sendToServer(new ToggleSealC2S(cinematic));
    }

    @Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> UtilityMenuScreen.addMenuSlot(new SealMenuSlot()));
        }

        @SubscribeEvent
        public static void onLayerDefinitions(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(CerealPartsLayer.LAYER, CerealPartsLayer::createLayer);
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onAddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                var renderer = event.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                    pr.addLayer(new CerealPartsLayer(pr, event.getEntityModels().bakeLayer(CerealPartsLayer.LAYER)));
                    pr.addLayer(new KiBowLayer(pr));
                }
            }
        }

        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(com.dmzkicontrol.registry.ModRegistry.CEREAL_SHOT.get(), CerealShotRenderer::new);
            event.registerEntityRenderer(com.dmzkicontrol.registry.ModRegistry.CEREAL_BEAM.get(), CerealBeamRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(SEAL_KEY);
            event.register(CerealianEyeClient.EYE_KEY);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "aura_seal", SealHudOverlay.INSTANCE);
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "cereal_eye", CerealianEyeClient.OVERLAY);
            event.registerAboveAll("aura_cutscene", CutsceneOverlay.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            boolean down = SEAL_KEY.isDown();
            if (down && !wasDown && mc.screen == null && mc.player != null) onSealPressed();
            wasDown = down;
            while (SEAL_KEY.consumeClick()) { /* descarta repeticoes do teclado */ }

            CutsceneController.clientTick();
            SealAnimations.clientTick();
            CerealianEyeClient.clientTick();
            KiBowRenderer.clientTick();
            ArmAnimDriver.clientTick();
            SealFxRenderer.clientTick();
            if (mc.player != null && mc.player.tickCount % 20 == 0) SealAnimations.ensureInjected();
        }

        @SubscribeEvent
        public static void onRenderLevel(net.minecraftforge.client.event.RenderLevelStageEvent event) {
            WeakPointRenderer.render(event);
            KiBowRenderer.render(event);
            SealFxRenderer.render(event);
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            CutsceneController.lockRotation();
            if (event.phase == TickEvent.Phase.START) {
                SealAnimations.suppressShootPose();
                SealAnimations.hideAurasForFrame();
            }
            else SealAnimations.restoreAurasAfterFrame();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
            CutsceneController.onCameraAngles(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onFov(ViewportEvent.ComputeFov event) {
            CutsceneController.onFov(event);
            CerealianEyeClient.onFov(event);
        }

        /** Durante a cena o personagem fica parado. */
        @SubscribeEvent
        public static void onMovement(MovementInputUpdateEvent event) {
            if (!CutsceneController.isActive()) return;
            var in = event.getInput();
            in.forwardImpulse = 0f;
            in.leftImpulse = 0f;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
            if (!CutsceneController.isActive()) return;
            event.setCanceled(true);
            event.setSwingHand(false);
        }

        /** Esconde o resto do HUD (vanilla e DMZ) durante a cena. */
        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
            if (CutsceneController.isActive() && !CUTSCENE_OVERLAY.equals(event.getOverlay().id())) event.setCanceled(true);
        }

        /** Renascer/trocar de dimensao recria o jogador local com outro id: zera os estados por id. */
        @SubscribeEvent
        public static void onClientClone(ClientPlayerNetworkEvent.Clone event) {
            SealAnimations.clearSealed();
            CutsceneController.stop();
            CerealianEyeClient.reset();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSealState.reset();
            CutsceneController.resetSession();
            SealAnimations.clearSealed();
            CerealianEyeClient.reset();
        }
    }
}
