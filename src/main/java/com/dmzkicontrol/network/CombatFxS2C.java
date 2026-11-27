package com.dmzkicontrol.network;

import com.dmzkicontrol.client.CombatFeel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> Cliente: sensacao do golpe (tremida, congelada, zoom) no combate dinamico. */
public record CombatFxS2C(int kind) {
    public static final int SMASH = 0, VANISH = 1, CHASE = 2, ENEMY_VANISH = 3;

    public static void encode(CombatFxS2C m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
    }

    public static CombatFxS2C decode(FriendlyByteBuf buf) {
        return new CombatFxS2C(buf.readByte());
    }

    public static void handle(CombatFxS2C m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CombatFeel.play(m.kind)));
        ctx.get().setPacketHandled(true);
    }
}
