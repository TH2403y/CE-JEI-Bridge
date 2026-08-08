package com.ceclientmod.cache;

import net.minecraft.item.ItemStack;

public record CeBrewingEntry(String recipeId, String ingredientKey, ItemStack result) {
}
