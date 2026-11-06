package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.compat.UltraCompat;
import com.dmzkicontrol.config.KiControlClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Com o DMZ Ultra: na primeira vez que entra num mundo, pergunta qual HUD usar; ao entrar, reaplica a escolha;
 * /dmzhud abre a escolha de novo.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UltraHudClient {
    /** Ticks ate abrir a pergunta (deixa o mundo terminar de carregar e as telas de entrada fecharem). */
    private static int askIn = -1;
    private static boolean openNext;

    private UltraHudClient() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!UltraCompat.present()) return;
        KiControlClientConfig.UltraHud mode = UltraHud.mode();
        if (mode == KiControlClientConfig.UltraHud.ASK) askIn = 60;
        else UltraHud.apply(mode);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (openNext) {
            openNext = false;
            mc.setScreen(new HudChoiceScreen(null));
            return;
        }
        if (askIn < 0 || mc.player == null) return;
        if (mc.screen != null) return; // espera as outras telas (carregamento, tutorial) fecharem
        if (--askIn > 0) return;
        askIn = -1;
        if (UltraHud.mode() == KiControlClientConfig.UltraHud.ASK && UltraHud.available()) mc.setScreen(new HudChoiceScreen(null));
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzhud").executes(ctx -> {
            if (!UltraHud.available()) {
                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("gui.dmzkicontrol.hud_choice.no_ultra"));
                return 0;
            }
            openNext = true; // o chat ainda esta fechando: abre no proximo tick
            return 1;
        }));
    }
}
