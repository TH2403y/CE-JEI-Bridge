package com.ceclientmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: sent once right after we register our channels, so the server knows to push a full sync. */
public record HelloPayload() implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("ceclientbridge", "hello");
    public static final Type<HelloPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.unit(new HelloPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
