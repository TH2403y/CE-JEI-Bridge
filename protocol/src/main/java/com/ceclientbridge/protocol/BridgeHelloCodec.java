package com.ceclientbridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Byte-compatible hello codec for Bukkit plugin messages and Fabric custom payloads. */
public final class BridgeHelloCodec {
    public static final int MAGIC = 0x43454832; // "CEH2"

    private BridgeHelloCodec() {
    }

    public static byte[] encode(BridgeHello hello) {
        byte[] target = hello.minecraftTarget().getBytes(StandardCharsets.UTF_8);
        if (target.length > 32) throw new IllegalArgumentException("Minecraft target is too long");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(48);
            DataOutputStream out = new DataOutputStream(bytes);
            writeVarInt(out, MAGIC);
            writeVarInt(out, hello.protocolVersion());
            writeVarInt(out, target.length);
            out.write(target);
            writeVarInt(out, hello.capabilities());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static BridgeHello decode(byte[] encoded) {
        if (encoded == null || encoded.length > 256) {
            throw new IllegalArgumentException("hello payload exceeds maximum size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (readVarInt(in) != MAGIC) throw new IllegalArgumentException("invalid hello magic");
            int protocolVersion = readVarInt(in);
            int targetLength = readVarInt(in);
            if (targetLength <= 0 || targetLength > 32) throw new IllegalArgumentException("invalid target length");
            byte[] targetBytes = in.readNBytes(targetLength);
            if (targetBytes.length != targetLength) throw new EOFException("truncated hello target");
            int capabilities = readVarInt(in);
            if (in.available() != 0) throw new IllegalArgumentException("trailing hello bytes");
            return new BridgeHello(protocolVersion, new String(targetBytes, StandardCharsets.UTF_8), capabilities);
        } catch (IOException e) {
            throw new IllegalArgumentException("truncated hello payload", e);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        if (value < 0) throw new IllegalArgumentException("hello varint must be non-negative");
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = in.readUnsignedByte();
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("hello varint is too long");
    }
}
