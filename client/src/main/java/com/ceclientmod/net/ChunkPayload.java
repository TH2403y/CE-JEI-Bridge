package com.ceclientmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Wire shape shared across the items/blocks/brewing S2C channels: [total:varint][index:varint][data:byte[]].
 * The server (see CraftEngineClientBridge's BridgeChannels) frames chunks the same way; the client must
 * concatenate {@code data} across index 0..total-1 (in order) before parsing entries out of the result -
 * chunk boundaries carry no entry-alignment meaning. One record class is reused for all three channels;
 * each channel gets its own {@link CustomPacketPayload.Type} identity via {@link #codecFor}.
 */
public record ChunkPayload(CustomPacketPayload.Type<ChunkPayload> payloadType, int total, int index,
                            byte[] data) implements CustomPacketPayload {

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return payloadType;
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ChunkPayload> codecFor(CustomPacketPayload.Type<ChunkPayload> type) {
        return StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChunkPayload::total,
                ByteBufCodecs.VAR_INT, ChunkPayload::index,
                ByteBufCodecs.BYTE_ARRAY, ChunkPayload::data,
                (total, index, data) -> new ChunkPayload(type, total, index, data)
        );
    }
}
