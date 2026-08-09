package com.ceclientmod.net;

import com.ceclientbridge.protocol.BridgeCapabilities;
import com.ceclientbridge.protocol.BridgeHelloCodec;
import com.ceclientbridge.protocol.BridgeProtocol;
import com.ceclientmod.version.BridgeClientTarget;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: sent once right after we register our channels, so the server can validate the pair. */
public record HelloPayload(int protocolVersion, String minecraftTarget, int capabilities) implements CustomPayload {

    public static final CustomPayload.Id<HelloPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of("ceclientbridge", "hello"));
    public static final PacketCodec<RegistryByteBuf, HelloPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeVarInt(BridgeHelloCodec.MAGIC);
                buf.writeVarInt(payload.protocolVersion());
                buf.writeString(payload.minecraftTarget(), 32);
                buf.writeVarInt(payload.capabilities());
            },
            buf -> new HelloPayload(buf.readVarInt(), buf.readString(32), buf.readVarInt())
    );

    public HelloPayload() {
        this(BridgeProtocol.CURRENT_VERSION, BridgeClientTarget.minecraftTarget(), BridgeCapabilities.ALL);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
