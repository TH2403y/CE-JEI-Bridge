package com.ceclientmod.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client-side cache of every CraftEngine custom item the server told us about. Rebuilt whenever a fresh
 * {@code ceclientbridge:items} sync arrives (join, or a CraftEngine reload on the server).
 * The NBT key mirrors CraftEngine's own item-identity marker (see IdProcessor.CRAFT_ENGINE_ID
 * server-side): a string tag "craftengine:id" inside the minecraft:custom_data component.
 */
public final class CeItemRegistry {

    public static final String CUSTOM_DATA_ID_KEY = "craftengine:id";

    private final Map<String, CeItem> byCeId = new LinkedHashMap<>();
    private final Map<Item, List<CeItem>> byBaseItem = new HashMap<>();

    public void readFrom(DataInputStream in) throws IOException {
        Map<String, CeItem> newById = new LinkedHashMap<>();
        Map<Item, List<CeItem>> newByBase = new HashMap<>();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String ceId = in.readUTF();
            ItemStack stack = readAppearance(in, ceId);
            CeItem item = new CeItem(ceId, stack);
            newById.put(ceId, item);
            newByBase.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(item);
        }
        byCeId.clear();
        byCeId.putAll(newById);
        byBaseItem.clear();
        byBaseItem.putAll(newByBase);
    }

    /** Mirrors SyncManager#writeItemAppearance on the server: [baseItem][hasCMD][cmd?][hasItemModel][itemModel?][hasName][name:JSON?]. */
    public static ItemStack readAppearance(DataInputStream in, String ceId) throws IOException {
        Identifier baseId = Identifier.of(in.readUTF());
        Item baseItem = Registries.ITEM.get(baseId);
        ItemStack stack = new ItemStack(baseItem);

        if (in.readBoolean()) {
            int cmd = in.readInt();
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                    new CustomModelDataComponent(List.of((float) cmd), List.of(), List.of(), List.of()));
        }
        if (in.readBoolean()) {
            Identifier itemModel = Identifier.of(in.readUTF());
            stack.set(DataComponentTypes.ITEM_MODEL, itemModel);
        }
        if (in.readBoolean()) {
            // Sent as full JSON, not plain text: CraftEngine commonly sets its name to a *translatable*
            // component (e.g. a <lang:...> key), so this must be resolved through the client's own loaded
            // language file rather than flattened to a literal string server-side.
            String json = in.readUTF();
            JsonElement element = JsonParser.parseString(json);
            Text name = TextCodecs.CODEC.parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(error -> {})
                    .orElseGet(() -> Text.literal(json));
            stack.set(DataComponentTypes.CUSTOM_NAME, name);
        }

        NbtCompound tag = new NbtCompound();
        tag.putString(CUSTOM_DATA_ID_KEY, ceId);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return stack;
    }

    /** Reads the craftengine:id marker back off a stack - used by the JEI subtype interpreter. */
    public static Optional<String> ceIdOf(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        NbtCompound tag = data.copyNbt();
        if (!tag.contains(CUSTOM_DATA_ID_KEY)) {
            return Optional.empty();
        }
        return Optional.of(tag.getString(CUSTOM_DATA_ID_KEY).orElse(null));
    }

    public Optional<CeItem> byId(String ceId) {
        return Optional.ofNullable(byCeId.get(ceId));
    }

    public Collection<CeItem> all() {
        return byCeId.values();
    }

    public List<CeItem> byBaseItem(Item item) {
        return byBaseItem.getOrDefault(item, List.of());
    }

    public Set<Item> baseItems() {
        return byBaseItem.keySet();
    }
}
