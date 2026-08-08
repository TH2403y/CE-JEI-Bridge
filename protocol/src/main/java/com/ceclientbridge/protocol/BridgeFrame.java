package com.ceclientbridge.protocol;

import java.util.Arrays;
import java.util.Objects;

/** One validated logical bridge payload chunk. */
public record BridgeFrame(
        int protocolVersion,
        String type,
        long generation,
        int chunkIndex,
        int chunkCount,
        int totalLength,
        byte[] payload
) {

    public BridgeFrame {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (protocolVersion <= 0) throw new IllegalArgumentException("protocolVersion must be positive");
        if (type.isBlank() || type.length() > 64) throw new IllegalArgumentException("invalid frame type");
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        if (chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("invalid chunk position");
        }
        if (totalLength < 0 || totalLength > BridgeProtocol.MAX_GENERATION_BYTES) {
            throw new IllegalArgumentException("invalid total length");
        }
        if (payload.length > BridgeProtocol.MAX_PAYLOAD_BYTES || payload.length > totalLength) {
            throw new IllegalArgumentException("payload exceeds frame limits");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
