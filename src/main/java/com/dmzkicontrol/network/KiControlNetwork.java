package com.dmzkicontrol.network;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal proprio do addon (a wiki do DMZ pede: nunca registre pacotes no canal do DMZ). */
public final class KiControlNetwork {
    private static final String PROTOCOL = "6";

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
        CHANNEL.messageBuilder(EyeStateC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EyeStateC2S::encode)
                .decoder(EyeStateC2S::decode)
                .consumerMainThread(EyeStateC2S::handle)
                .add();
        CHANNEL.messageBuilder(EyeMarkS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EyeMarkS2C::encode)
                .decoder(EyeMarkS2C::decode)
                .consumerMainThread(EyeMarkS2C::handle)
                .add();
        CHANNEL.messageBuilder(BubblesHudS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BubblesHudS2C::encode)
                .decoder(BubblesHudS2C::decode)
                .consumerMainThread(BubblesHudS2C::handle)
                .add();
        CHANNEL.messageBuilder(FormSceneS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FormSceneS2C::encode)
                .decoder(FormSceneS2C::decode)
                .consumerMainThread(FormSceneS2C::handle)
                .add();
        CHANNEL.messageBuilder(FormSceneSkipC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FormSceneSkipC2S::encode)
                .decoder(FormSceneSkipC2S::decode)
                .consumerMainThread(FormSceneSkipC2S::handle)
                .add();
    }
}
