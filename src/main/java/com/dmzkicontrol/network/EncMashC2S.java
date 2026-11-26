package com.dmzkicontrol.network;

import com.dmzkicontrol.encounter.EncounterDirector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor: apertos no cabo de guerra / ticks segurando o carregar ki na janela de transformacao. */
public record EncMashC2S(int id, int beat, int presses) {
    public static void encode(EncMashC2S m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeVarInt(m.beat);
        buf.writeVarInt(m.presses);
    }

    public static EncMashC2S decode(FriendlyByteBuf buf) {
        return new EncMashC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EncMashC2S m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sp != null) EncounterDirector.onMash(sp, m.id, m.beat, m.presses);
        });
        ctx.get().setPacketHandled(true);
    }
}
