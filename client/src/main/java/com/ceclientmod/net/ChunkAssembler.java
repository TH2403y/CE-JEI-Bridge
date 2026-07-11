package com.ceclientmod.net;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

/** Buffers ChunkPayload fragments for one channel until all `total` chunks have arrived, then concatenates them. */
public final class ChunkAssembler {

    private int expectedTotal = -1;
    private byte[][] chunks;
    private int received;

    public synchronized Optional<byte[]> accept(ChunkPayload payload) {
        if (payload.total() != expectedTotal) {
            // A fresh sync started (or the first chunk of the first sync ever); reset the buffer.
            expectedTotal = payload.total();
            chunks = new byte[expectedTotal][];
            received = 0;
        }
        if (payload.index() < 0 || payload.index() >= chunks.length || chunks[payload.index()] != null) {
            return Optional.empty();
        }
        chunks[payload.index()] = payload.data();
        received++;
        if (received < expectedTotal) {
            return Optional.empty();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            out.write(chunk, 0, chunk.length);
        }
        expectedTotal = -1;
        chunks = null;
        return Optional.of(out.toByteArray());
    }
}
