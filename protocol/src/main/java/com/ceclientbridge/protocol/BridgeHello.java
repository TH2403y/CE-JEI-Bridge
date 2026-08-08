package com.ceclientbridge.protocol;

import java.util.Objects;

public record BridgeHello(int protocolVersion, String minecraftTarget, int capabilities) {
    public BridgeHello {
        Objects.requireNonNull(minecraftTarget, "minecraftTarget");
        if (protocolVersion <= 0) throw new IllegalArgumentException("protocolVersion must be positive");
        if (minecraftTarget.isBlank() || minecraftTarget.length() > 32) {
            throw new IllegalArgumentException("invalid Minecraft target");
        }
        if ((capabilities & ~BridgeCapabilities.ALL) != 0) {
            throw new IllegalArgumentException("unknown capability bit");
        }
    }
}
