package com.dmzkicontrol.network;

import com.dmzkicontrol.cerealian.CerealianEye;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> Servidor: segurando/soltando a tecla do Olho Cereal. O servidor valida a raca. */
public record EyeStateC2S(boolean active) {
    public static void encode(EyeStateC2S m, FriendlyByteBuf buf) { buf.writeBoolean(m.active); }
    public static EyeStateC2S decode(FriendlyByteBuf buf) { return new EyeStateC2S(buf.readBoolean()); }
    public static void handle(EyeStateC2S m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) CerealianEye.setActive(sp, m.active);
        ctx.get().setPacketHandled(true);
    }
}
