package com.ceclientmod.net;

import net.minecraft.network.packet.CustomPayload;

/** Channel identities shared with the server-side CraftEngineClientBridge plugin (see its BridgeChannels). */
public final class BridgeChannels {

    public static final CustomPayload.Id<ChunkPayload> ITEMS = CustomPayload.id("ceclientbridge:items");
    public static final CustomPayload.Id<ChunkPayload> BLOCKS = CustomPayload.id("ceclientbridge:blocks");
    public static final CustomPayload.Id<ChunkPayload> BREWING = CustomPayload.id("ceclientbridge:brewing");
    public static final CustomPayload.Id<ChunkPayload> CRAFTING_DISPLAY = CustomPayload.id("ceclientbridge:crafting_display");
    public static final CustomPayload.Id<ChunkPayload> SMITHING_DISPLAY = CustomPayload.id("ceclientbridge:smithing_display");

    private BridgeChannels() {
    }
}
