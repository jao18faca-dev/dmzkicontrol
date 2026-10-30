package com.dmzkicontrol.network;

import com.dmzkicontrol.client.BubblesHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: estado do treino do Bubbles para o HUD. */
public record BubblesHudS2C(int state, int level, int ticksLeft, int total, int bubblesId) {
    public static final int NONE = 0, RUNNING = 1, WIN = 2, FAIL = 3;

    public static void encode(BubblesHudS2C m, FriendlyByteBuf buf) {
        buf.writeByte(m.state);
        buf.writeByte(m.level);
        buf.writeVarInt(Math.max(0, m.ticksLeft));
        buf.writeVarInt(Math.max(0, m.total));
        buf.writeVarInt(m.bubblesId + 1);
    }

    public static BubblesHudS2C decode(FriendlyByteBuf buf) {
        return new BubblesHudS2C(buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1);
    }

    public static void handle(BubblesHudS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BubblesHud.onPacket(m.state, m.level, m.ticksLeft, m.total, m.bubblesId)));
        ctx.get().setPacketHandled(true);
    }
}
