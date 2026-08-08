package com.ceclientmod.cache;

import net.minecraft.item.ItemStack;

/** template/addition may be ItemStack.EMPTY - CraftEngine's smithing-transform recipes allow either
 *  slot to be omitted (unlike vanilla 1.20+, which always requires a template). base and result are
 *  always present. */
public record CeSmithingEntry(String recipeId, ItemStack template, ItemStack base, ItemStack addition, ItemStack result) {
}
