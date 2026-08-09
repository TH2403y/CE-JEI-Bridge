package com.ceclientbridge.protocol;

final class BridgeHandshakeTest {

    public static void main(String[] args) {
        acceptsMatchingTargetAndCapabilities();
        acceptsSupportedCrossTargetClient();
        rejectsProtocolMismatch();
        rejectsUnsupportedTargetMismatch();
        rejectsMissingRequiredCapability();
        helloCodecRoundTripPreservesTarget();
        compatibilityGateRequiresSuccessfulHandshake();
        System.out.println("BridgeHandshakeTest: 7 tests passed");
    }

    private static void acceptsMatchingTargetAndCapabilities() {
        BridgeHello server = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.2", BridgeCapabilities.ALL);
        BridgeHello client = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.2", BridgeCapabilities.ALL);

        BridgeNegotiation result = BridgeHandshake.negotiate(server, client);

        check(result.accepted(), "matching hello should be accepted");
    }

    private static void rejectsProtocolMismatch() {
        BridgeHello server = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.2", BridgeCapabilities.ALL);
        BridgeHello client = new BridgeHello(BridgeProtocol.CURRENT_VERSION - 1, "26.2", BridgeCapabilities.ALL);

        BridgeNegotiation result = BridgeHandshake.negotiate(server, client);

        check(!result.accepted() && result.reason().contains("protocol"), "protocol mismatch should be explained");
    }

    private static void acceptsSupportedCrossTargetClient() {
        BridgeHello server = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.x", BridgeCapabilities.ALL);
        BridgeHello client = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "1.21.x", BridgeCapabilities.ALL);

        BridgeNegotiation result = BridgeHandshake.negotiate(server, client);

        check(result.accepted(), "supported cross-target client should be accepted");
    }

    private static void rejectsUnsupportedTargetMismatch() {
        BridgeHello server = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.x", BridgeCapabilities.ALL);
        BridgeHello client = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "forge", BridgeCapabilities.ALL);

        BridgeNegotiation result = BridgeHandshake.negotiate(server, client);

        check(!result.accepted() && result.reason().contains("Minecraft"), "unsupported target mismatch should be explained");
    }

    private static void rejectsMissingRequiredCapability() {
        BridgeHello server = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.2", BridgeCapabilities.ALL);
        BridgeHello client = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "26.2", BridgeCapabilities.ITEMS);

        BridgeNegotiation result = BridgeHandshake.negotiate(server, client);

        check(!result.accepted() && result.reason().contains("capability"), "missing capability should be explained");
    }

    private static void helloCodecRoundTripPreservesTarget() {
        BridgeHello original = new BridgeHello(BridgeProtocol.CURRENT_VERSION, "1.21.11", BridgeCapabilities.ALL);

        BridgeHello decoded = BridgeHelloCodec.decode(BridgeHelloCodec.encode(original));

        check(original.equals(decoded), "hello codec round-trip");
    }

    private static void compatibilityGateRequiresSuccessfulHandshake() {
        java.util.UUID player = java.util.UUID.randomUUID();
        BridgeCompatibilityGate gate = new BridgeCompatibilityGate();

        check(!gate.isCompatible(player), "new player must not receive a sync");
        gate.markCompatible(player);
        check(gate.isCompatible(player), "accepted handshake must enable sync");
        gate.clear(player);
        check(!gate.isCompatible(player), "cleared player must not receive a sync");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
