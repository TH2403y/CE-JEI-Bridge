package com.ceclientbridge.net;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire format shared with the client mod: a full payload (produced by {@link com.ceclientbridge.sync.SyncManager})
 * is split into chunks of at most {@link #MAX_CHUNK_BYTES}. Each chunk on the wire is:
 * [totalChunks:varint][chunkIndex:varint][chunkBytes:varint-length-prefixed], matching the client's
 * {@code ChunkPayload} StreamCodec (VAR_INT, VAR_INT, BYTE_ARRAY) exactly. The client must concatenate
 * chunkBytes across all chunkIndex 0..totalChunks-1 (in order) into one buffer before parsing entries -
 * chunk boundaries carry no entry-alignment meaning.
 */
public final class BridgeChannels {

    public static final String ITEMS = "ceclientbridge:items";
    public static final String BLOCKS = "ceclientbridge:blocks";
    public static final String BREWING = "ceclientbridge:brewing";
    public static final String CRAFTING_DISPLAY = "ceclientbridge:crafting_display";
    public static final String SMITHING_DISPLAY = "ceclientbridge:smithing_display";
    public static final String HELLO = "ceclientbridge:hello";

    private static final int MAX_CHUNK_BYTES = 30000;

    private BridgeChannels() {
    }

    public static void send(Plugin plugin, Player player, String channel, byte[] fullPayload) {
        List<byte[]> raw = new ArrayList<>();
        for (int offset = 0; offset < fullPayload.length; offset += MAX_CHUNK_BYTES) {
            int end = Math.min(offset + MAX_CHUNK_BYTES, fullPayload.length);
            raw.add(java.util.Arrays.copyOfRange(fullPayload, offset, end));
        }
        if (raw.isEmpty()) {
            raw.add(new byte[0]);
        }
        int total = raw.size();
        for (int i = 0; i < total; i++) {
            player.sendPluginMessage(plugin, channel, framedChunk(total, i, raw.get(i)));
        }
    }

    private static byte[] framedChunk(int total, int index, byte[] body) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(body.length + 16);
            java.io.DataOutputStream out = new java.io.DataOutputStream(bos);
            writeVarInt(out, total);
            writeVarInt(out, index);
            writeVarInt(out, body.length);
            out.write(body);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Same unsigned LEB128 encoding as Minecraft's {@code FriendlyByteBuf.writeVarInt} / {@code ByteBufCodecs.VAR_INT}. */
    private static void writeVarInt(java.io.DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
