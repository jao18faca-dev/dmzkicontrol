package com.dmzkicontrol.network;

import com.dmzkicontrol.client.ClientSealState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: estado do selo para o HUD e para o botao do menu utilitario. */
public record SealStateS2C(boolean sealed, boolean pending, String formKey, boolean hasSkill, float mastery,
                           float kiReduction, float powerBonus, float strain, boolean perfect,
                           float fatigueAt, float hurtAt, int burstTicks) {

    public static void encode(SealStateS2C m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.sealed);
        buf.writeBoolean(m.pending);
        buf.writeUtf(m.formKey, 128);
        buf.writeBoolean(m.hasSkill);
        buf.writeFloat(m.mastery);
        buf.writeFloat(m.kiReduction);
        buf.writeFloat(m.powerBonus);
        buf.writeFloat(m.strain);
        buf.writeBoolean(m.perfect);
        buf.writeFloat(m.fatigueAt);
        buf.writeFloat(m.hurtAt);
        buf.writeVarInt(m.burstTicks);
    }

    public static SealStateS2C decode(FriendlyByteBuf buf) {
        return new SealStateS2C(buf.readBoolean(), buf.readBoolean(), buf.readUtf(128), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(SealStateS2C m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSealState.update(m));
        ctx.get().setPacketHandled(true);
    }
}
