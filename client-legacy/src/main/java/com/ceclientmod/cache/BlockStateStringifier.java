package com.ceclientmod.cache;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;

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
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        String props = state.getEntries().entrySet().stream()
                .sorted(Comparator.comparing(v -> v.getKey().getName()))
                .map(v -> stringifyProperty(v.getKey(), v.getValue()))
                .collect(Collectors.joining(","));
        return props.isEmpty() ? id : id + "[" + props + "]";
    }

    private static <T extends Comparable<T>> String stringifyProperty(Property<T> property, Comparable<?> value) {
        return property.getName() + "=" + property.name((T) value);
    }
}
