package com.ceclientbridge.protocol;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks players whose client bridge completed a compatible handshake. */
public final class BridgeCompatibilityGate {

    private final Set<UUID> compatiblePlayers = ConcurrentHashMap.newKeySet();

    public void markCompatible(UUID playerId) {
        compatiblePlayers.add(playerId);
    }

    public void clear(UUID playerId) {
        compatiblePlayers.remove(playerId);
    }

    public boolean isCompatible(UUID playerId) {
        return compatiblePlayers.contains(playerId);
    }
}
