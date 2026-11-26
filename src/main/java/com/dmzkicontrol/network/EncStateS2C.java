package com.dmzkicontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: luta cinematografica comecou / mudou de cena / acabou. Estado completo (idempotente). */
public record EncStateS2C(int id, String script, int phase, int beat, int variant, long start,
                          double ox, double oy, double oz, float fx, float fz, int bossId, int[] players, boolean end) {
    public static void encode(EncStateS2C m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeUtf(m.script, 64);
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.beat);
        buf.writeByte(m.variant);
        buf.writeLong(m.start);
        buf.writeDouble(m.ox);
        buf.writeDouble(m.oy);
        buf.writeDouble(m.oz);
        buf.writeFloat(m.fx);
        buf.writeFloat(m.fz);
        buf.writeVarInt(m.bossId);
        buf.writeVarIntArray(m.players);
        buf.writeBoolean(m.end);
    }

    public static EncStateS2C decode(FriendlyByteBuf buf) {
        return new EncStateS2C(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readVarIntArray(8), buf.readBoolean());
    }

    public static void handle(EncStateS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.dmzkicontrol.client.encounter.EncClient.onState(m)));
        ctx.get().setPacketHandled(true);
    }
}
