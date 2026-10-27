package com.dmzkicontrol.network;

import com.dmzkicontrol.control.AuraSealManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> Servidor: "quero alternar o selo". {@code cinematic} so diz se o cliente vai mostrar
 * a cutscene (muda o tempo ate o selo valer); o servidor valida todo o resto.
 */
public record ToggleSealC2S(boolean cinematic) {

    public static void encode(ToggleSealC2S msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.cinematic);
    }

    public static ToggleSealC2S decode(FriendlyByteBuf buf) {
        return new ToggleSealC2S(buf.readBoolean());
    }

    public static void handle(ToggleSealC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) AuraSealManager.requestToggle(sender, msg.cinematic);
        ctx.get().setPacketHandled(true);
    }
}
