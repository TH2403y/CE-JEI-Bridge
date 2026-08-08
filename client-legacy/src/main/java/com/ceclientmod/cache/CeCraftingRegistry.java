package com.ceclientmod.cache;

import net.minecraft.item.ItemStack;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of every CraftEngine custom crafting-table recipe's EXACT per-slot appearance,
 * as told by the server. Rebuilt whenever a fresh {@code ceclientbridge:crafting_display} sync arrives.
 * Feeds com.ceclientmod.jei.CeJeiPlugin's JEI display-recipe registration - see that class for why this
 * is needed on top of the native Fabric recipe resync (vanilla Ingredient can't carry per-stack skins).
 */
public final class CeCraftingRegistry {

    private final List<CeCraftingEntry> entries = new ArrayList<>();

    /** Mirrors SyncManager#buildCraftingDisplayPayload: [recipeId][shapeless][width:byte][height:byte][slot0..slotN][result], each slot gated by [hasItem:bool], the result always present. */
    public void readFrom(DataInputStream in) throws IOException {
        List<CeCraftingEntry> fresh = new ArrayList<>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String recipeId = in.readUTF();
            boolean shapeless = in.readBoolean();
            int width = in.readByte();
            int height = in.readByte();
            int slots = width * height;
            List<ItemStack> inputs = new ArrayList<>(slots);
            for (int s = 0; s < slots; s++) {
                if (in.readBoolean()) {
                    inputs.add(CeItemRegistry.readAppearance(in, recipeId));
                } else {
                    inputs.add(ItemStack.EMPTY);
                }
            }
            ItemStack result = CeItemRegistry.readAppearance(in, recipeId);
            fresh.add(new CeCraftingEntry(recipeId, shapeless, width, height, inputs, result));
        }
        entries.clear();
        entries.addAll(fresh);
    }

    public List<CeCraftingEntry> all() {
        return List.copyOf(entries);
    }
}
