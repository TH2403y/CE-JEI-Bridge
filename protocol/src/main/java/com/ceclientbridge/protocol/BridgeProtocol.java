package com.ceclientbridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Dependency-free wire framing shared by the Paper plugin and Fabric mod. */
public final class BridgeProtocol {
    public static final int CURRENT_VERSION = 3;
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
    public static final int MAX_GENERATION_BYTES = 64 * 1024 * 1024;

    private static final int MAGIC = 0x43454232; // "CEB2"
    private static final int MAX_FRAME_BYTES = MAX_PAYLOAD_BYTES + 256;

    private BridgeProtocol() {
    }

    public static byte[] encodeFrame(BridgeFrame frame) {
        if (frame.protocolVersion() != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported protocol version: " + frame.protocolVersion());
        }
        byte[] type = frame.type().getBytes(StandardCharsets.UTF_8);
        byte[] payload = frame.payload();
        if (type.length > 255) throw new IllegalArgumentException("frame type is too long");

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(frame.protocolVersion());
            out.writeByte(type.length);
            out.write(type);
            out.writeLong(frame.generation());
            out.writeInt(frame.chunkIndex());
            out.writeInt(frame.chunkCount());
            out.writeInt(frame.totalLength());
            out.writeInt(payload.length);
            out.write(payload);
            out.writeInt(checksum(payload));
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static BridgeFrame decodeFrame(byte[] encoded) {
        if (encoded == null || encoded.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("frame exceeds maximum size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid bridge frame magic");
            int version = in.readInt();
            if (version != CURRENT_VERSION) {
                throw new IllegalArgumentException("unsupported protocol version: " + version);
            }
            int typeLength = in.readUnsignedByte();
            if (typeLength == 0) throw new IllegalArgumentException("empty frame type");
            byte[] typeBytes = in.readNBytes(typeLength);
            if (typeBytes.length != typeLength) throw new EOFException("truncated frame type");
            String type = new String(typeBytes, StandardCharsets.UTF_8);
            long generation = in.readLong();
            int chunkIndex = in.readInt();
            int chunkCount = in.readInt();
            int totalLength = in.readInt();
            int payloadLength = in.readInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("invalid payload length");
            }
            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) throw new EOFException("truncated frame payload");
            int expectedChecksum = in.readInt();
            if (in.available() != 0) throw new IllegalArgumentException("trailing bridge frame bytes");
            if (checksum(payload) != expectedChecksum) throw new IllegalArgumentException("bridge frame checksum mismatch");
            return new BridgeFrame(version, type, generation, chunkIndex, chunkCount, totalLength, payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("truncated bridge frame", e);
        }
    }

    private static int checksum(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return (int) crc.getValue();
    }
}
