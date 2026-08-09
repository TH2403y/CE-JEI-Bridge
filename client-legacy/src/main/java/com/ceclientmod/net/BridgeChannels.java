package com.ceclientmod.net;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Channel identities shared with the server-side CraftEngineClientBridge plugin (see its BridgeChannels). */
public final class BridgeChannels {

    public static final CustomPayload.Id<ChunkPayload> ITEMS = id("items");
    public static final CustomPayload.Id<ChunkPayload> BLOCKS = id("blocks");
    public static final CustomPayload.Id<ChunkPayload> BREWING = id("brewing");
    public static final CustomPayload.Id<ChunkPayload> CRAFTING_DISPLAY = id("crafting_display");
    public static final CustomPayload.Id<ChunkPayload> SMITHING_DISPLAY = id("smithing_display");

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
        return new CustomPayload.Id<>(Identifier.of("ceclientbridge", path));
    }

    private BridgeChannels() {
    }
}
