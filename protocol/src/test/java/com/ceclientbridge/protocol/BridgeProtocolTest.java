package com.ceclientbridge.protocol;

final class BridgeProtocolTest {

    public static void main(String[] args) {
        frameRoundTripPreservesHeaderAndPayload();
        assemblerRequiresEveryChunkExactlyOnce();
        decoderRejectsChecksumTampering();
        payloadLimitIsEnforced();
        System.out.println("BridgeProtocolTest: 4 tests passed");
    }

    private static void frameRoundTripPreservesHeaderAndPayload() {
        BridgeFrame frame = new BridgeFrame(
                BridgeProtocol.CURRENT_VERSION,
                "items",
                42L,
                0,
                2,
                5,
                new byte[]{1, 2, 3}
        );

        BridgeFrame decoded = BridgeProtocol.decodeFrame(BridgeProtocol.encodeFrame(frame));

        check(frame.protocolVersion() == decoded.protocolVersion(), "protocol version");
        check(frame.type().equals(decoded.type()), "type");
        check(frame.generation() == decoded.generation(), "generation");
        check(frame.chunkIndex() == decoded.chunkIndex(), "chunk index");
        check(frame.chunkCount() == decoded.chunkCount(), "chunk count");
        check(frame.totalLength() == decoded.totalLength(), "total length");
        check(java.util.Arrays.equals(frame.payload(), decoded.payload()), "payload");
    }

    private static void assemblerRequiresEveryChunkExactlyOnce() {
        BridgeGenerationAssembler assembler = new BridgeGenerationAssembler("items", 42L, 5, 2);

        BridgeFrame second = new BridgeFrame(BridgeProtocol.CURRENT_VERSION, "items", 42L, 1, 2, 5, new byte[]{4, 5});
        BridgeFrame first = new BridgeFrame(BridgeProtocol.CURRENT_VERSION, "items", 42L, 0, 2, 5, new byte[]{1, 2, 3});

        expectFailure(() -> assembler.accept(second), IllegalStateException.class);
        check(!assembler.isComplete(), "incomplete after out-of-order frame");
        assembler.accept(first);
        assembler.accept(second);
        check(assembler.isComplete(), "complete after all frames");
        check(java.util.Arrays.equals(new byte[]{1, 2, 3, 4, 5}, assembler.join()), "joined payload");
    }

    private static void decoderRejectsChecksumTampering() {
        BridgeFrame frame = new BridgeFrame(BridgeProtocol.CURRENT_VERSION, "blocks", 7L, 0, 1, 2, new byte[]{9, 8});
        byte[] encoded = BridgeProtocol.encodeFrame(frame);
        encoded[encoded.length - 1] ^= 0x01;

        expectFailure(() -> BridgeProtocol.decodeFrame(encoded), IllegalArgumentException.class);
    }

    private static void payloadLimitIsEnforced() {
        byte[] oversized = new byte[BridgeProtocol.MAX_PAYLOAD_BYTES + 1];

        expectFailure(() -> new BridgeFrame(
                BridgeProtocol.CURRENT_VERSION, "items", 1L, 0, 1, oversized.length, oversized),
                IllegalArgumentException.class);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void expectFailure(Runnable action, Class<? extends Throwable> expected) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError("expected " + expected.getName() + ", got " + actual, actual);
        }
        throw new AssertionError("expected " + expected.getName());
    }
}
