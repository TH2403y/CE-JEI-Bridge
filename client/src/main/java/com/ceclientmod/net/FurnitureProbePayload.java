package com.ceclientmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S request for the CraftEngine furniture represented by the entity currently targeted by Jade. */
public record FurnitureProbePayload(int requestId, int entityId) implements CustomPacketPayload {
    public static final Type<FurnitureProbePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("ceclientbridge", "furniture_probe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FurnitureProbePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.requestId());
                buf.writeVarInt(payload.entityId());
            },
            buf -> new FurnitureProbePayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
