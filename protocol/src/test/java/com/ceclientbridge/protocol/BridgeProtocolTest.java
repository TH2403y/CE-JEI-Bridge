package com.ceclientbridge.protocol;

final class BridgeProtocolTest {

    public static void main(String[] args) {
        frameRoundTripPreservesHeaderAndPayload();
        assemblerRequiresEveryChunkExactlyOnce();
        decoderRejectsChecksumTampering();
        payloadLimitIsEnforced();
        jadeBlockIconsRoundTripPreservesEntries();
        jadeFurnitureProbeRoundTripPreservesRequest();
        jadeFurnitureIconRoundTripPreservesAppearance();
        jadeIconDecoderRejectsTruncatedAppearance();
        fixedWindowLimiterRejectsOverflowAndResets();
        System.out.println("BridgeProtocolTest: 9 tests passed");
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

    private static void jadeBlockIconsRoundTripPreservesEntries() {
        var icons = java.util.List.of(
                new JadeIconProtocol.BlockIcon("minecraft:note_block[instrument=harp,note=1,powered=false]", "demo:machine", new byte[]{1, 2, 3}),
                new JadeIconProtocol.BlockIcon("minecraft:tripwire[attached=false]", "demo:cable", new byte[]{4, 5})
        );

        var decoded = JadeIconProtocol.decodeBlockIcons(JadeIconProtocol.encodeBlockIcons(icons));

        check(decoded.size() == 2, "block icon count");
        check(decoded.get(0).visualState().equals(icons.get(0).visualState()), "block icon visual state");
        check(decoded.get(0).ceId().equals("demo:machine"), "block icon CE id");
        check(java.util.Arrays.equals(decoded.get(1).appearance(), new byte[]{4, 5}), "block icon appearance");
    }

    private static void jadeFurnitureProbeRoundTripPreservesRequest() {
        var probe = new JadeIconProtocol.FurnitureProbe(17, 2048);

        var decoded = JadeIconProtocol.decodeFurnitureProbe(JadeIconProtocol.encodeFurnitureProbe(probe));

        check(decoded.requestId() == 17, "furniture probe request id");
        check(decoded.entityId() == 2048, "furniture probe entity id");
    }

    private static void jadeFurnitureIconRoundTripPreservesAppearance() {
        var response = new JadeIconProtocol.FurnitureIcon(18, 4096, "demo:chair", new byte[]{9, 8, 7});

        var decoded = JadeIconProtocol.decodeFurnitureIcon(JadeIconProtocol.encodeFurnitureIcon(response));

        check(decoded.requestId() == 18, "furniture icon request id");
        check(decoded.entityId() == 4096, "furniture icon entity id");
        check(decoded.ceId().equals("demo:chair"), "furniture icon CE id");
        check(java.util.Arrays.equals(decoded.appearance(), new byte[]{9, 8, 7}), "furniture icon appearance");
    }

    private static void jadeIconDecoderRejectsTruncatedAppearance() {
        byte[] encoded = JadeIconProtocol.encodeFurnitureIcon(
                new JadeIconProtocol.FurnitureIcon(19, 8192, "demo:table", new byte[]{6, 5, 4}));
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);

        expectFailure(() -> JadeIconProtocol.decodeFurnitureIcon(truncated), IllegalArgumentException.class);
    }

    private static void fixedWindowLimiterRejectsOverflowAndResets() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 1_000L);

        check(limiter.tryAcquire(10_000L), "rate limit first request");
        check(limiter.tryAcquire(10_500L), "rate limit second request");
        check(!limiter.tryAcquire(10_999L), "rate limit overflow");
        check(limiter.tryAcquire(11_000L), "rate limit reset");
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
