package com.dmzkicontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> Cliente: o que aconteceu na luta.
 * CUE: a = deixa, b = 0 normal / 1 parcial / 2 combinado, data = resultado de cada papel.
 * MASH: f = cabo de guerra (0..1), data = apertos recentes de cada papel. MASH_END: a = 1 vitoria / 0 derrota.
 * CHARGED: a = papel, b = 1 transformou / 2 elevou o ki.
 */
public record EncEventS2C(int id, int beat, byte type, int a, byte b, byte[] data, float f) {
    public static final byte CUE = 0, MASH = 1, MASH_END = 2, CHARGED = 3;

    public static void encode(EncEventS2C m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeVarInt(m.beat);
        buf.writeByte(m.type);
        buf.writeVarInt(m.a);
        buf.writeByte(m.b);
        buf.writeByteArray(m.data);
        buf.writeFloat(m.f);
    }

    public static EncEventS2C decode(FriendlyByteBuf buf) {
        return new EncEventS2C(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readByte(), buf.readByteArray(16), buf.readFloat());
    }

    public static void handle(EncEventS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.dmzkicontrol.client.encounter.EncClient.onEvent(m)));
        ctx.get().setPacketHandled(true);
    }
}
