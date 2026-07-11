package com.ceclientmod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Channel identities shared with the server-side CraftEngineClientBridge plugin (see its BridgeChannels). */
public final class BridgeChannels {

    public static final CustomPacketPayload.Type<ChunkPayload> ITEMS =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ceclientbridge", "items"));
    public static final CustomPacketPayload.Type<ChunkPayload> BLOCKS =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ceclientbridge", "blocks"));
    public static final CustomPacketPayload.Type<ChunkPayload> BREWING =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ceclientbridge", "brewing"));
    public static final CustomPacketPayload.Type<ChunkPayload> CRAFTING_DISPLAY =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ceclientbridge", "crafting_display"));
    public static final CustomPacketPayload.Type<ChunkPayload> SMITHING_DISPLAY =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ceclientbridge", "smithing_display"));

    private BridgeChannels() {
    }
}
