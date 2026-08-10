package com.ceclientmod.cache;

import com.ceclientbridge.protocol.JadeIconProtocol;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Exact inventory-item icons for CraftEngine blocks, keyed by their client-visible block state. */
public final class CeBlockIconRegistry {
    private final Map<String, ItemStack> byVisualState = new HashMap<>();

    public void readFrom(byte[] payload) throws IOException {
        Map<String, ItemStack> fresh = new HashMap<>();
        for (JadeIconProtocol.BlockIcon icon : JadeIconProtocol.decodeBlockIcons(payload)) {
            ItemStack stack = CeItemRegistry.readAppearance(
                    new DataInputStream(new ByteArrayInputStream(icon.appearance())));
            fresh.put(icon.visualState(), stack);
        }
        byVisualState.clear();
        byVisualState.putAll(fresh);
    }

    public Optional<ItemStack> iconFor(BlockState state) {
        ItemStack stack = byVisualState.get(BlockStateStringifier.stringify(state));
        return stack == null ? Optional.empty() : Optional.of(stack);
    }

    public void clear() {
        byVisualState.clear();
    }
}
