package com.ceclientmod.cache;

import net.minecraft.block.BlockState;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side cache mapping a CraftEngine block's *visual* (disguise) blockstate string - what any
 * vanilla-protocol client, including us, actually receives in chunk data - back to its real CraftEngine id.
 * Built from the server's {@code ceclientbridge:blocks} sync. See CraftEngineClientBridge's SyncManager
 * for how the visual string is produced server-side (ImmutableBlockState#visualBlockState#getAsString()).
 */
public final class CeBlockRegistry {

    private final Map<String, String> visualToCeId = new HashMap<>();

    public void readFrom(DataInputStream in) throws IOException {
        Map<String, String> fresh = new HashMap<>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String ceId = in.readUTF();
            String visual = in.readUTF();
            fresh.put(visual, ceId);
        }
        visualToCeId.clear();
        visualToCeId.putAll(fresh);
    }

    public Optional<String> ceIdFor(BlockState state) {
        return Optional.ofNullable(visualToCeId.get(BlockStateStringifier.stringify(state)));
    }
}
