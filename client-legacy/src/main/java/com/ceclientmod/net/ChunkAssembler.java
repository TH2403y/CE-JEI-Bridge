package com.ceclientmod.net;

import com.ceclientbridge.protocol.BridgeFrame;
import com.ceclientbridge.protocol.BridgeGenerationAssembler;
import com.ceclientbridge.protocol.BridgeProtocol;

import java.util.Optional;

/** Validates and reassembles one generation for one bridge channel. */
public final class ChunkAssembler {

    private final String expectedType;
    private long activeGeneration = -1L;
    private BridgeGenerationAssembler active;

    public ChunkAssembler(String expectedType) {
        this.expectedType = expectedType;
    }

    public synchronized Optional<byte[]> accept(ChunkPayload payload) {
        final BridgeFrame frame;
        try {
            frame = BridgeProtocol.decodeFrame(payload.data());
        } catch (IllegalArgumentException invalidFrame) {
            return Optional.empty();
        }
        if (!expectedType.equals(frame.type())) {
            return Optional.empty();
        }
        if (frame.generation() < activeGeneration) {
            return Optional.empty();
        }
        if (active == null || frame.generation() > activeGeneration) {
            if (frame.chunkIndex() != 0) return Optional.empty();
            activeGeneration = frame.generation();
            active = new BridgeGenerationAssembler(
                    frame.type(), frame.generation(), frame.totalLength(), frame.chunkCount());
        }
        try {
            active.accept(frame);
            if (!active.isComplete()) return Optional.empty();
            byte[] result = active.join();
            active = null;
            return Optional.of(result);
        } catch (IllegalArgumentException | IllegalStateException invalidGeneration) {
            return Optional.empty();
        }
    }

    public synchronized void clear() {
        activeGeneration = -1L;
        active = null;
    }
}
