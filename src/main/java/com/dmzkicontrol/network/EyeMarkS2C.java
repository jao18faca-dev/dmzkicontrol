package com.dmzkicontrol.network;

import com.dmzkicontrol.client.CerealianEyeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: alvo marcado (-1 = nenhum), tempo restante e quais pontos fracos ainda existem. */
public record EyeMarkS2C(int entityId, int ticks, int mask) {
    public static void encode(EyeMarkS2C m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId + 1);
        buf.writeVarInt(m.ticks);
        buf.writeVarInt(m.mask);
    }
    public static EyeMarkS2C decode(FriendlyByteBuf buf) {
        return new EyeMarkS2C(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
    }
    public static void handle(EyeMarkS2C m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CerealianEyeClient.onMark(m.entityId, m.ticks, m.mask));
        ctx.get().setPacketHandled(true);
    }
}
