package com.dmzkicontrol.network;

import com.dmzkicontrol.encounter.EncounterDirector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor: resultado de uma deixa (o cliente mede o proprio tempo: sem atraso de rede no julgamento). */
public record EncInputC2S(int id, int beat, int cue, byte result, float offset) {
    public static void encode(EncInputC2S m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeVarInt(m.beat);
        buf.writeVarInt(m.cue);
        buf.writeByte(m.result);
        buf.writeFloat(m.offset);
    }

    public static EncInputC2S decode(FriendlyByteBuf buf) {
        return new EncInputC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readFloat());
    }

    public static void handle(EncInputC2S m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (sp != null) EncounterDirector.onInput(sp, m.id, m.beat, m.cue, m.result, m.offset);
        });
        ctx.get().setPacketHandled(true);
    }
}
