package com.ceclientmod.net;

/** Small runtime check for the 1.21.x custom-payload identifiers. */
public final class LegacyChannelIdentifierTest {

    private LegacyChannelIdentifierTest() {
    }

    public static void main(String[] args) {
        assertIdentifier("ceclientbridge:items", BridgeChannels.ITEMS.id().toString());
        assertIdentifier("ceclientbridge:blocks", BridgeChannels.BLOCKS.id().toString());
        assertIdentifier("ceclientbridge:brewing", BridgeChannels.BREWING.id().toString());
        assertIdentifier("ceclientbridge:crafting_display", BridgeChannels.CRAFTING_DISPLAY.id().toString());
        assertIdentifier("ceclientbridge:smithing_display", BridgeChannels.SMITHING_DISPLAY.id().toString());
        assertIdentifier("ceclientbridge:hello", HelloPayload.TYPE.id().toString());
    }

    private static void assertIdentifier(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected channel " + expected + " but got " + actual);
        }
    }
}
