package com.dmzkicontrol.network;

import com.dmzkicontrol.client.SealAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> quem esta vendo o jogador: toca a animacao do selo (e a cutscene, se for voce). */
public record SealAnimS2C(int entityId, byte type) {
    public static final byte QUICK = 0;
    public static final byte CUTSCENE = 1;
    public static final byte RELEASE = 2;
    public static final byte BREAK = 3;
    public static final byte CANCEL = 4;
    /** Estado: aura selada (esconde todas as auras do DMZ desse jogador) / livre. */
    public static final byte SEALED_ON = 5;
    public static final byte SEALED_OFF = 6;
    /** Arco de Ki disparado: encerra a pose de arco depois da animacao de soltar. */
    public static final byte KIBOW_RELEASE = 7;

    public static void encode(SealAnimS2C m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeByte(m.type);
    }

    public static SealAnimS2C decode(FriendlyByteBuf buf) {
        return new SealAnimS2C(buf.readVarInt(), buf.readByte());
    }

    public static void handle(SealAnimS2C m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SealAnimations.onPacket(m.entityId, m.type));
        ctx.get().setPacketHandled(true);
    }
}
