package com.dmzkicontrol.network;

import com.dmzkicontrol.formscene.FormSceneServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor: pular a cutscene de transformacao. */
public record FormSceneSkipC2S() {
    public static void encode(FormSceneSkipC2S m, FriendlyByteBuf buf) {}

    public static FormSceneSkipC2S decode(FriendlyByteBuf buf) {
        return new FormSceneSkipC2S();
    }

    public static void handle(FormSceneSkipC2S m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) FormSceneServer.skip(sp);
        });
        ctx.get().setPacketHandled(true);
    }
}
