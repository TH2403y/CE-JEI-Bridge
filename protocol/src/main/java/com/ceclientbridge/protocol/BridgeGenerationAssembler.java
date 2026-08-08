package com.ceclientbridge.protocol;

import java.io.ByteArrayOutputStream;

/** Reassembles one ordered generation and rejects stale or duplicated chunks. */
public final class BridgeGenerationAssembler {
    private final String type;
    private final long generation;
    private final int totalLength;
    private final int chunkCount;
    private final ByteArrayOutputStream joined;
    private int nextChunk;

    public BridgeGenerationAssembler(String type, long generation, int totalLength, int chunkCount) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is required");
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        if (totalLength < 0 || totalLength > BridgeProtocol.MAX_GENERATION_BYTES) {
            throw new IllegalArgumentException("invalid total length");
        }
        if (chunkCount <= 0) throw new IllegalArgumentException("chunk count must be positive");
        this.type = type;
        this.generation = generation;
        this.totalLength = totalLength;
        this.chunkCount = chunkCount;
        this.joined = new ByteArrayOutputStream(totalLength);
    }

    public void accept(BridgeFrame frame) {
        if (isComplete()) throw new IllegalStateException("generation is already complete");
        if (!type.equals(frame.type()) || generation != frame.generation()
                || totalLength != frame.totalLength() || chunkCount != frame.chunkCount()) {
            throw new IllegalArgumentException("frame belongs to another generation");
        }
        if (frame.chunkIndex() != nextChunk) {
            throw new IllegalStateException("expected chunk " + nextChunk + ", got " + frame.chunkIndex());
        }
        byte[] payload = frame.payload();
        if ((long) joined.size() + payload.length > totalLength) {
            throw new IllegalArgumentException("generation exceeds declared length");
        }
        joined.writeBytes(payload);
        nextChunk++;
    }

    public boolean isComplete() {
        return nextChunk == chunkCount && joined.size() == totalLength;
    }

    public byte[] join() {
        if (!isComplete()) throw new IllegalStateException("generation is incomplete");
        return joined.toByteArray();
    }
}
