package com.ceclientbridge.net;

import com.ceclientbridge.protocol.BridgeFrame;
import com.ceclientbridge.protocol.BridgeProtocol;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends generation-tagged protocol frames over the existing plugin-message channels. The Fabric side
 * reads the Minecraft {@code BYTE_ARRAY} envelope before receiving the raw frame for generation
 * validation and registry updates.
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
        send(plugin, player, channel, 0L, fullPayload);
    }

    public static void send(Plugin plugin, Player player, String channel, long generation, byte[] fullPayload) {
        if (fullPayload.length > BridgeProtocol.MAX_GENERATION_BYTES) {
            throw new IllegalArgumentException("bridge payload exceeds maximum generation size: " + fullPayload.length);
        }
        List<byte[]> raw = new ArrayList<>();
        for (int offset = 0; offset < fullPayload.length; offset += MAX_CHUNK_BYTES) {
            int end = Math.min(offset + MAX_CHUNK_BYTES, fullPayload.length);
            raw.add(java.util.Arrays.copyOfRange(fullPayload, offset, end));
        }
        if (raw.isEmpty()) {
            raw.add(new byte[0]);
        }
        int total = raw.size();
        String type = frameType(channel);
        for (int i = 0; i < total; i++) {
            BridgeFrame frame = new BridgeFrame(
                    BridgeProtocol.CURRENT_VERSION,
                    type,
                    generation,
                    i,
                    total,
                    fullPayload.length,
                    raw.get(i)
            );
            player.sendPluginMessage(plugin, channel, byteArrayPayload(BridgeProtocol.encodeFrame(frame)));
        }
    }

    private static byte[] byteArrayPayload(byte[] frame) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(frame.length + 5);
        int length = frame.length;
        while ((length & ~0x7F) != 0) {
            payload.write((length & 0x7F) | 0x80);
            length >>>= 7;
        }
        payload.write(length);
        payload.write(frame, 0, frame.length);
        return payload.toByteArray();
    }

    private static String frameType(String channel) {
        int separator = channel.indexOf(':');
        if (separator < 0 || separator == channel.length() - 1) {
            throw new IllegalArgumentException("invalid bridge channel: " + channel);
        }
        return channel.substring(separator + 1);
    }
}
