package com.dmzkicontrol.network;

import com.dmzkicontrol.control.AuraSealManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> Servidor: "quero alternar o selo". Sem payload: o servidor valida tudo
 * (forma ativa, skill, cooldown), como a wiki do DMZ recomenda.
 */
public final class ToggleSealC2S {

    public static void encode(ToggleSealC2S msg, FriendlyByteBuf buf) {}

    public static ToggleSealC2S decode(FriendlyByteBuf buf) {
        return new ToggleSealC2S();
    }

    public static void handle(ToggleSealC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) AuraSealManager.toggle(sender);
        ctx.get().setPacketHandled(true);
    }
}
