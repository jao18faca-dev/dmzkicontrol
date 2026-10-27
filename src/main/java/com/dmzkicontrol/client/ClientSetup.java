package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dmzkicontrol.network.ToggleSealC2S;
import com.dragonminez.client.gui.UtilityMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Tudo que so existe no cliente: tecla, HUD e o botao no menu utilitario do DMZ. */
public final class ClientSetup {

    /** Padrao: G (rebindavel em Opcoes > Controles > DMZ Ki Control). */
    public static final KeyMapping TOGGLE_SEAL = new KeyMapping(
            "key.dmzkicontrol.toggle_seal",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.dmzkicontrol");

    private ClientSetup() {}

    public static void requestToggle() {
        if (Minecraft.getInstance().player != null) {
            KiControlNetwork.CHANNEL.sendToServer(new ToggleSealC2S());
        }
    }

    @Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Hook oficial para add-ons no cliente: slot extra no menu utilitario (radial) do DMZ
            event.enqueueWork(() -> UtilityMenuScreen.addMenuSlot(new SealMenuSlot()));
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_SEAL);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "aura_seal", SealHudOverlay.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = KiControlMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (TOGGLE_SEAL.consumeClick()) requestToggle();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSealState.reset();
        }
    }
}
