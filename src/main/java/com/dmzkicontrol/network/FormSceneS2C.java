package com.dmzkicontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: comeca (ou termina) a cutscene de transformacao de um jogador. */
public record FormSceneS2C(int entityId, String scene, long startTick, boolean stop) {
    public static void encode(FormSceneS2C m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.scene, 128);
        buf.writeLong(m.startTick);
        buf.writeBoolean(m.stop);
    }

    public static FormSceneS2C decode(FriendlyByteBuf buf) {
        return new FormSceneS2C(buf.readVarInt(), buf.readUtf(128), buf.readLong(), buf.readBoolean());
    }

    public static void handle(FormSceneS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.dmzkicontrol.client.scene.ScenePlayback.onPacket(m.entityId, m.scene, m.startTick, m.stop)));
        ctx.get().setPacketHandled(true);
    }
}
