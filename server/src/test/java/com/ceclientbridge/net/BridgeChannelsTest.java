package com.ceclientbridge.net;

import com.ceclientbridge.protocol.BridgeFrame;
import com.ceclientbridge.protocol.BridgeProtocol;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BridgeChannelsTest {

    public static void main(String[] args) {
        pluginMessagesFramePayloadForByteArrayCodec();
        System.out.println("BridgeChannelsTest: 1 test passed");
    }

    private static void pluginMessagesFramePayloadForByteArrayCodec() {
        byte[] body = new byte[18_179];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        List<byte[]> messages = new ArrayList<>();
        Player player = capturingPlayer(messages);

        BridgeChannels.send(noopPlugin(), player, BridgeChannels.ITEMS, 7L, body);

        check(messages.size() == 1, "one message for a sub-30KB payload");
        byte[] wire = messages.getFirst();
        VarInt frameLength = readVarInt(wire);
        check(frameLength.value == wire.length - frameLength.bytes,
                "the custom payload must begin with the full bridge-frame length");

        BridgeFrame frame = BridgeProtocol.decodeFrame(Arrays.copyOfRange(wire, frameLength.bytes, wire.length));
        check(frame.generation() == 7L, "generation");
        check(frame.type().equals("items"), "channel type");
        check(Arrays.equals(frame.payload(), body), "frame body");
    }

    private static Player capturingPlayer(List<byte[]> messages) {
        return (Player) Proxy.newProxyInstance(
                BridgeChannelsTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendPluginMessage")) {
                        messages.add(Arrays.copyOf((byte[]) args[2], ((byte[]) args[2]).length));
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "capturing-player";
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static Plugin noopPlugin() {
        return (Plugin) Proxy.newProxyInstance(
                BridgeChannelsTest.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "noop-plugin";
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static VarInt readVarInt(byte[] data) {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = data[index] & 0xFF;
            value |= (next & 0x7F) << (index * 7);
            if ((next & 0x80) == 0) {
                return new VarInt(value, index + 1);
            }
        }
        throw new AssertionError("invalid VarInt");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record VarInt(int value, int bytes) {
    }
}
