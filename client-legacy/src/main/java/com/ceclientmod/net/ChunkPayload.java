package com.ceclientmod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * One raw protocol frame carried by one of the existing bridge channels. The server writes the complete
 * generation/chunk header before sending this payload; the client validates it in {@link ChunkAssembler}.
 */
public record ChunkPayload(CustomPayload.Id<ChunkPayload> payloadId, byte[] data) implements CustomPayload {

    @Override
    public Id<? extends CustomPayload> getId() {
        return payloadId;
    }

    public static PacketCodec<RegistryByteBuf, ChunkPayload> codecFor(CustomPayload.Id<ChunkPayload> id) {
        return PacketCodec.tuple(
                PacketCodecs.BYTE_ARRAY, ChunkPayload::data,
                data -> new ChunkPayload(id, data)
        );
    }
}
