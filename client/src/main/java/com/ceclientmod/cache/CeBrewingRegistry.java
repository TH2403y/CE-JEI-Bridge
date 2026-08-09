package com.ceclientmod.cache;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CeBrewingRegistry {

    private final List<CeBrewingEntry> entries = new ArrayList<>();

    public void readFrom(DataInputStream in) throws IOException {
        List<CeBrewingEntry> fresh = new ArrayList<>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String recipeId = in.readUTF();
            String ingredientKey = in.readUTF();
            var stack = CeItemRegistry.readAppearance(in);
            fresh.add(new CeBrewingEntry(recipeId, ingredientKey, stack));
        }
        entries.clear();
        entries.addAll(fresh);
    }

    public List<CeBrewingEntry> all() {
        return List.copyOf(entries);
    }
}
