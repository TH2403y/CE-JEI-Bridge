package com.ceclientbridge.protocol;

/** Capability bits advertised by both sides of the bridge handshake. */
public final class BridgeCapabilities {
    public static final int ITEMS = 1;
    public static final int BLOCKS = 1 << 1;
    public static final int BREWING = 1 << 2;
    public static final int CRAFTING_DISPLAY = 1 << 3;
    public static final int SMITHING_DISPLAY = 1 << 4;
    public static final int ALL = ITEMS | BLOCKS | BREWING | CRAFTING_DISPLAY | SMITHING_DISPLAY;

    private BridgeCapabilities() {
    }
}
