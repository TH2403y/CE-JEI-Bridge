package com.ceclientbridge.protocol;

public record BridgeNegotiation(boolean accepted, String reason) {
    public BridgeNegotiation {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
    }
}
