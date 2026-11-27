package com.dmzkicontrol.network;

import com.dmzkicontrol.client.PrepHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: prazo de treino em andamento (id vazio = nenhum). */
public record PrepHudS2C(String id, int left, int total, int storyDays) {
    public static void encode(PrepHudS2C m, FriendlyByteBuf buf) {
        buf.writeUtf(m.id, 64);
        buf.writeVarInt(Math.max(0, m.left));
        buf.writeVarInt(Math.max(1, m.total));
        buf.writeVarInt(Math.max(0, m.storyDays));
    }

    public static PrepHudS2C decode(FriendlyByteBuf buf) {
        return new PrepHudS2C(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PrepHudS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrepHud.onPacket(m.id, m.left, m.total, m.storyDays)));
        ctx.get().setPacketHandled(true);
    }
}
