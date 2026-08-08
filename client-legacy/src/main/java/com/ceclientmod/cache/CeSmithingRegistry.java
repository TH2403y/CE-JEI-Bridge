package com.ceclientmod.cache;

import net.minecraft.item.ItemStack;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of every CraftEngine smithing-table recipe's EXACT template/base/addition/result
 * appearance, as told by the server. Rebuilt whenever a fresh {@code ceclientbridge:smithing_display}
 * sync arrives. Feeds com.ceclientmod.jei.CeSmithingCategory, a custom JEI recipe category (JEI's own
 * built-in smithing display can't carry per-stack skins - see that class for details).
 */
public final class CeSmithingRegistry {

    private final List<CeSmithingEntry> entries = new ArrayList<>();

    /** Mirrors SyncManager#buildSmithingDisplayPayload: [recipeId][hasTemplate][template?][hasBase][base?][hasAddition][addition?][result], result always present. */
    public void readFrom(DataInputStream in) throws IOException {
        List<CeSmithingEntry> fresh = new ArrayList<>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String recipeId = in.readUTF();
            ItemStack template = in.readBoolean() ? CeItemRegistry.readAppearance(in, recipeId) : ItemStack.EMPTY;
            ItemStack base = in.readBoolean() ? CeItemRegistry.readAppearance(in, recipeId) : ItemStack.EMPTY;
            ItemStack addition = in.readBoolean() ? CeItemRegistry.readAppearance(in, recipeId) : ItemStack.EMPTY;
            ItemStack result = CeItemRegistry.readAppearance(in, recipeId);
            fresh.add(new CeSmithingEntry(recipeId, template, base, addition, result));
        }
        entries.clear();
        entries.addAll(fresh);
    }

    public List<CeSmithingEntry> all() {
        return List.copyOf(entries);
    }
}
