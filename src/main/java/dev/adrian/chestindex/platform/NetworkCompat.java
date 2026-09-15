package dev.adrian.chestindex.platform;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class NetworkCompat {

    private NetworkCompat() {}

    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> playC2S() {
        //? if >=26.1 {
        /*return PayloadTypeRegistry.serverboundPlay();
        *///?} else {
        return PayloadTypeRegistry.playC2S();
        //?}
    }

    public static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C() {
        //? if >=26.1 {
        /*return PayloadTypeRegistry.clientboundPlay();
        *///?} else {
        return PayloadTypeRegistry.playS2C();
        //?}
    }
}
