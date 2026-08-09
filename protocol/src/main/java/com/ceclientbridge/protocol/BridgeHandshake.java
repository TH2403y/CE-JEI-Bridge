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
        if (!targetsCompatible(server.minecraftTarget(), client.minecraftTarget())) {
            return new BridgeNegotiation(false, "Minecraft target mismatch: server="
                    + server.minecraftTarget() + ", client=" + client.minecraftTarget());
        }
        int missing = server.capabilities() & ~client.capabilities();
        if (missing != 0) {
            return new BridgeNegotiation(false, "missing bridge capability bits: " + missing);
        }
        return new BridgeNegotiation(true, "compatible");
    }

    private static boolean targetsCompatible(String serverTarget, String clientTarget) {
        if (serverTarget.equals(clientTarget)) {
            return true;
        }
        return (is26Family(serverTarget) && is121Family(clientTarget))
                || (is121Family(serverTarget) && is26Family(clientTarget));
    }

    private static boolean is26Family(String target) {
        return "26.x".equals(target);
    }

    private static boolean is121Family(String target) {
        return "1.21.x".equals(target);
    }
}
