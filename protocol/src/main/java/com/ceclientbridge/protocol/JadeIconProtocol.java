package com.ceclientbridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free payload codec for the optional Jade 26.2 icon bridge. */
public final class JadeIconProtocol {
    public static final int MAX_BLOCK_ICONS = 100_000;
    public static final int MAX_APPEARANCE_BYTES = 1024 * 1024;

    private JadeIconProtocol() {
    }

    public record BlockIcon(String visualState, String ceId, byte[] appearance) {
        public BlockIcon {
            requireText(visualState, "visual state");
            requireText(ceId, "CraftEngine id");
            appearance = requireAppearance(appearance);
        }
    }

    public record FurnitureProbe(int requestId, int entityId) {
        public FurnitureProbe {
            if (requestId < 0) throw new IllegalArgumentException("negative request id");
            if (entityId < 0) throw new IllegalArgumentException("negative entity id");
        }
    }

    /** Empty {@code ceId} and appearance mean the entity is not recognized as CraftEngine furniture. */
    public record FurnitureIcon(int requestId, int entityId, String ceId, byte[] appearance) {
        public FurnitureIcon {
            if (requestId < 0) throw new IllegalArgumentException("negative request id");
            if (entityId < 0) throw new IllegalArgumentException("negative entity id");
            if (ceId == null) throw new IllegalArgumentException("null CraftEngine id");
            appearance = requireAppearance(appearance);
            if (ceId.isEmpty() != (appearance.length == 0)) {
                throw new IllegalArgumentException("furniture id and appearance must both be present or absent");
            }
        }
    }

    public static byte[] encodeBlockIcons(List<BlockIcon> icons) {
        if (icons == null || icons.size() > MAX_BLOCK_ICONS) {
            throw new IllegalArgumentException("invalid block icon count");
        }
        return write(out -> {
            out.writeInt(icons.size());
            for (BlockIcon icon : icons) {
                out.writeUTF(icon.visualState());
                out.writeUTF(icon.ceId());
                writeAppearance(out, icon.appearance());
            }
        });
    }

    public static List<BlockIcon> decodeBlockIcons(byte[] payload) {
        return read(payload, in -> {
            int count = in.readInt();
            if (count < 0 || count > MAX_BLOCK_ICONS) throw new IllegalArgumentException("invalid block icon count");
            List<BlockIcon> icons = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                icons.add(new BlockIcon(in.readUTF(), in.readUTF(), readAppearance(in)));
            }
            return List.copyOf(icons);
        });
    }

    public static byte[] encodeFurnitureProbe(FurnitureProbe probe) {
        return write(out -> {
            writeVarInt(out, probe.requestId());
            writeVarInt(out, probe.entityId());
        });
    }

    public static FurnitureProbe decodeFurnitureProbe(byte[] payload) {
        return read(payload, in -> new FurnitureProbe(readVarInt(in), readVarInt(in)));
    }

    public static byte[] encodeFurnitureIcon(FurnitureIcon icon) {
        return write(out -> {
            writeVarInt(out, icon.requestId());
            writeVarInt(out, icon.entityId());
            out.writeUTF(icon.ceId());
            writeAppearance(out, icon.appearance());
        });
    }

    public static FurnitureIcon decodeFurnitureIcon(byte[] payload) {
        return read(payload, in -> new FurnitureIcon(
                readVarInt(in), readVarInt(in), in.readUTF(), readAppearance(in)));
    }

    private static void writeAppearance(DataOutputStream out, byte[] appearance) throws IOException {
        out.writeInt(appearance.length);
        out.write(appearance);
    }

    private static byte[] readAppearance(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_APPEARANCE_BYTES) {
            throw new IllegalArgumentException("invalid item appearance length");
        }
        byte[] appearance = in.readNBytes(length);
        if (appearance.length != length) throw new EOFException("truncated item appearance");
        return appearance;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int position = 0; position < 32; position += 7) {
            int current = in.readUnsignedByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt is too large");
    }

    private static byte[] write(IoWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static <T> T read(byte[] payload, IoReader<T> reader) {
        if (payload == null || payload.length > BridgeProtocol.MAX_GENERATION_BYTES) {
            throw new IllegalArgumentException("invalid Jade icon payload size");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            T value = reader.read(in);
            if (in.available() != 0) throw new IllegalArgumentException("trailing Jade icon payload bytes");
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException("truncated Jade icon payload", e);
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("empty " + label);
    }

    private static byte[] requireAppearance(byte[] appearance) {
        if (appearance == null || appearance.length > MAX_APPEARANCE_BYTES) {
            throw new IllegalArgumentException("invalid item appearance length");
        }
        return appearance.clone();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface IoReader<T> {
        T read(DataInputStream in) throws IOException;
    }
}
