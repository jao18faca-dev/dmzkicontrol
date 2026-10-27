package com.dmzkicontrol.network;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal proprio do addon (a wiki do DMZ pede: nunca registre pacotes no canal do DMZ). */
public final class KiControlNetwork {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KiControlMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private KiControlNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ToggleSealC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleSealC2S::encode)
                .decoder(ToggleSealC2S::decode)
                .consumerMainThread(ToggleSealC2S::handle)
                .add();
        CHANNEL.messageBuilder(SealStateS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SealStateS2C::encode)
                .decoder(SealStateS2C::decode)
                .consumerMainThread(SealStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(SealAnimS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SealAnimS2C::encode)
                .decoder(SealAnimS2C::decode)
                .consumerMainThread(SealAnimS2C::handle)
                .add();
    }
}
