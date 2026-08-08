package com.ceclientmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One raw protocol frame carried by one of the existing bridge channels. The server writes the complete
 * generation/chunk header before sending this payload; the client validates it in {@link ChunkAssembler}.
 */
public record ChunkPayload(CustomPacketPayload.Type<ChunkPayload> payloadType, byte[] data) implements CustomPacketPayload {

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return payloadType;
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ChunkPayload> codecFor(CustomPacketPayload.Type<ChunkPayload> type) {
        return StreamCodec.composite(
                ByteBufCodecs.BYTE_ARRAY, ChunkPayload::data,
                data -> new ChunkPayload(type, data)
        );
    }
}
