package com.ceclientbridge.protocol;

/** Pure compatibility checks shared by the server and client integration layers. */
public final class BridgeHandshake {
    private BridgeHandshake() {
    }

    public static BridgeNegotiation negotiate(BridgeHello server, BridgeHello client) {
        if (server.protocolVersion() != client.protocolVersion()
                || server.protocolVersion() != BridgeProtocol.CURRENT_VERSION) {
            return new BridgeNegotiation(false, "protocol version mismatch: server="
                    + server.protocolVersion() + ", client=" + client.protocolVersion());
        }
        if (!server.minecraftTarget().equals(client.minecraftTarget())) {
            return new BridgeNegotiation(false, "Minecraft target mismatch: server="
                    + server.minecraftTarget() + ", client=" + client.minecraftTarget());
        }
        int missing = server.capabilities() & ~client.capabilities();
        if (missing != 0) {
            return new BridgeNegotiation(false, "missing bridge capability bits: " + missing);
        }
        return new BridgeNegotiation(true, "compatible");
    }
}
