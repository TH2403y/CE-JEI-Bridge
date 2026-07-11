package com.ceclientmod.cache;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One CraftEngine custom crafting-table recipe's EXACT per-slot appearance data, as synced by
 *  SyncManager#buildCraftingDisplayPayload - used purely to feed JEI a precisely-skinned display,
 *  since vanilla's own Ingredient can't carry component-level (item_model) data. */
public record CeCraftingEntry(String recipeId, boolean shapeless, int width, int height,
                               List<ItemStack> inputs, ItemStack result) {
}
