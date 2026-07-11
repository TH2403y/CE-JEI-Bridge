package com.ceclientmod.cache;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Formats a BlockState the same way CraftEngine's ImmutableBlockState#visualBlockState()#getAsString()
 * does server-side (namespace:path[prop=val,...], properties sorted by name for determinism), so a chunk
 * of BlockState received over vanilla protocol can be looked up directly in CeBlockRegistry.
 * RISK (see design plan risk item #6): the exact property-value string formatting must be verified
 * against a real CraftEngine block in-game and adjusted here if it disagrees with the server's format.
 */
final class BlockStateStringifier {

    private BlockStateStringifier() {
    }

    static String stringify(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String props = state.getValues()
                .sorted(Comparator.comparing(v -> v.property().getName()))
                .map(v -> v.property().getName() + "=" + v.valueName())
                .collect(Collectors.joining(","));
        return props.isEmpty() ? id : id + "[" + props + "]";
    }
}
