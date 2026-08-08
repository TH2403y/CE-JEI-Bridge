package com.ceclientmod.net;

import com.ceclientbridge.protocol.BridgeCapabilities;
import com.ceclientbridge.protocol.BridgeHelloCodec;
import com.ceclientbridge.protocol.BridgeProtocol;
import com.ceclientmod.version.BridgeClientTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: sent once right after we register our channels, so the server can validate the pair. */
public record HelloPayload(int protocolVersion, String minecraftTarget, int capabilities) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("ceclientbridge", "hello");
    public static final Type<HelloPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(BridgeHelloCodec.MAGIC);
                buf.writeVarInt(payload.protocolVersion());
                buf.writeUtf(payload.minecraftTarget(), 32);
                buf.writeVarInt(payload.capabilities());
            },
            buf -> new HelloPayload(buf.readVarInt(), buf.readUtf(32), buf.readVarInt())
    );

    public HelloPayload() {
        this(BridgeProtocol.CURRENT_VERSION, BridgeClientTarget.minecraftTarget(), BridgeCapabilities.ALL);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
