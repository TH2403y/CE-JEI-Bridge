package com.ceclientmod.cache;

import net.minecraft.world.item.ItemStack;

/** One CraftEngine custom item, as reconstructed on the client from the server's sync payload. */
public record CeItem(String ceId, ItemStack stack) {
}
