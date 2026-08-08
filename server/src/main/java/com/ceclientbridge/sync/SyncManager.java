package com.ceclientbridge.sync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.item.recipe.BukkitRecipeManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.recipe.CustomBrewingRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomSmithingTransformRecipe;
import net.momirealms.craftengine.core.item.recipe.Ingredient;
import net.momirealms.craftengine.core.item.recipe.Recipe;
import net.momirealms.craftengine.core.item.recipe.RecipeType;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.UniqueKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Builds the three synced payloads (items / blocks / brewing) from CraftEngine's public Bukkit API and
 * caches them so they can be pushed to any player on demand. Rebuilt on CraftEngineReloadEvent.
 * Item entries carry the handful of components that determine identity/appearance (custom_model_data,
 * item_model, display name) extracted via Bukkit's ItemMeta - not a full NBT dump - so the client can
 * reconstruct an equivalent ItemStack using plain vanilla DataComponents setters instead of having to
 * independently re-implement Minecraft's NBT/DataFixer item-loading pipeline.
 * <p>
 * CraftEngine only attaches purely client-visual components (item_model in particular) during its
 * server-to-client packet transform; {@code BukkitItemDefinition#buildItem} returns the logical/
 * server-bound stack and never carries them, no matter what the item's config declares. See
 * {@link #toClientBoundStack}.
 */
public final class SyncManager {

    private final JavaPlugin plugin;
    private volatile byte[] itemsPayload = emptyCountPayload();
    private volatile byte[] blocksPayload = emptyCountPayload();
    private volatile byte[] brewingPayload = emptyCountPayload();
    private volatile byte[] craftingDisplayPayload = emptyCountPayload();
    private volatile Set<String> craftingDisplayRecipeIds = Set.of();
    private volatile byte[] smithingDisplayPayload = emptyCountPayload();
    private volatile Set<String> smithingDisplayRecipeIds = Set.of();
    private volatile long generation;

    public SyncManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public byte[] itemsPayload() {
        return itemsPayload;
    }

    public byte[] blocksPayload() {
        return blocksPayload;
    }

    public byte[] brewingPayload() {
        return brewingPayload;
    }

    public byte[] craftingDisplayPayload() {
        return craftingDisplayPayload;
    }

    public byte[] smithingDisplayPayload() {
        return smithingDisplayPayload;
    }

    public long generation() {
        return generation;
    }

    /** Every recipe id (CraftEngine's own crafting-table recipes AND any other plugin's, e.g.
     *  Craftorithm, that involves a CraftEngine item anywhere) that got a precise per-slot JEI display
     *  entry - see buildCraftingDisplayPayload. RecipeSyncListener uses this as the single source of
     *  truth for which recipes to exclude from the native Fabric recipe resync, instead of guessing
     *  independently with a narrower (result-only) check that missed ingredient-only cases. */
    public Set<String> craftingDisplayRecipeIds() {
        return craftingDisplayRecipeIds;
    }

    /** Same idea as craftingDisplayRecipeIds but for the smithing table - see buildSmithingDisplayPayload
     *  and CeSmithingCategory (the custom JEI recipe category these entries feed). */
    public Set<String> smithingDisplayRecipeIds() {
        return smithingDisplayRecipeIds;
    }

    public void rebuild() {
        long nextGeneration = generation + 1;
        Set<Key> craftingReferencedItems = collectCraftingReferencedItemIds();
        itemsPayload = buildItemsPayload(craftingReferencedItems);
        blocksPayload = buildBlocksPayload();
        brewingPayload = buildBrewingPayload();
        craftingDisplayPayload = buildCraftingDisplayPayload();
        smithingDisplayPayload = buildSmithingDisplayPayload();
        generation = nextGeneration;
        plugin.getLogger().info("CraftEngine sync rebuilt (generation " + generation + "): " + itemsPayload.length + "B items ("
                + craftingReferencedItems.size() + " referenced by a crafting recipe), "
                + blocksPayload.length + "B blocks, " + brewingPayload.length + "B brewing, "
                + craftingDisplayPayload.length + "B crafting display, "
                + smithingDisplayPayload.length + "B smithing display");
    }

    /**
     * Per-slot EXACT appearance data (not just the underlying vanilla material) for CraftEngine's own
     * crafting-table recipes, so the client can feed JEI's IVanillaRecipeFactory a precisely-skinned
     * SlotDisplay per ingredient - something vanilla's own type-based Ingredient can never carry, since
     * it matches by item type, not by component data like item_model. This is purely a JEI display aid;
     * it doesn't touch actual crafting mechanics (that's still whatever CraftEngine itself registered).
     */
    private byte[] buildCraftingDisplayPayload() {
        List<byte[]> entries = new ArrayList<>();
        Set<String> allIds = new HashSet<>();

        try {
            for (Recipe recipe : BukkitRecipeManager.instance().recipesByType(RecipeType.CRAFTING)) {
                try {
                    byte[] entry = buildCraftEngineDisplayEntry(recipe);
                    if (entry != null) {
                        entries.add(entry);
                        allIds.add(recipe.id().asString());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to export CraftEngine crafting recipe '" + recipe.id() + "' for display sync", t);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine crafting recipes for display sync", t);
        }

        // Also cover recipes registered by OTHER plugins via the plain Bukkit recipe API (e.g.
        // Craftorithm, which registers via Bukkit.addRecipe() rather than through CraftEngine's own
        // recipe system) - only bother building precise display data for ones that actually involve a
        // CraftEngine item somewhere; plain vanilla recipes already display correctly on their own.
        java.util.Iterator<org.bukkit.inventory.Recipe> bukkitRecipes = plugin.getServer().recipeIterator();
        while (bukkitRecipes.hasNext()) {
            org.bukkit.inventory.Recipe recipe = bukkitRecipes.next();
            try {
                byte[] entry = buildBukkitDisplayEntry(recipe, allIds);
                if (entry != null) {
                    entries.add(entry);
                    if (recipe instanceof org.bukkit.Keyed keyed) {
                        allIds.add(keyed.getKey().toString());
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to export a Bukkit-registered recipe for crafting display sync", t);
            }
        }

        // Authoritative record of "which recipe ids got a precise JEI display entry" - RecipeSyncListener
        // reads this to decide native-resync exclusion instead of re-deriving its own (narrower,
        // result-only) guess, which missed recipes where only an INGREDIENT (not the result) was a
        // CraftEngine item - exactly the case for Craftorithm recipes crafting a CraftEngine block/item
        // FROM CraftEngine materials where the two checks previously disagreed.
        craftingDisplayRecipeIds = Set.copyOf(allIds);
        return countPrefixed(entries);
    }

    private byte[] buildCraftEngineDisplayEntry(Recipe recipe) throws IOException {
        if (!(recipe instanceof CustomCraftingTableRecipe crafting)) return null;
        boolean shapeless;
        int width;
        int height;
        Ingredient[] grid;
        if (crafting instanceof net.momirealms.craftengine.core.item.recipe.CustomShapedRecipe shaped) {
            shapeless = false;
            var parsed = shaped.parsedPattern();
            width = parsed.width();
            height = parsed.height();
            if (width == 0 || height == 0) return null;
            var parsedIngredients = parsed.ingredients();
            grid = new Ingredient[parsedIngredients.length];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = parsedIngredients[i].orElse(null);
            }
        } else if (crafting instanceof net.momirealms.craftengine.core.item.recipe.CustomShapelessRecipe shapelessRecipe) {
            shapeless = true;
            width = 3;
            height = 3;
            List<Ingredient> list = shapelessRecipe.ingredientsInUse();
            grid = new Ingredient[9];
            for (int i = 0; i < list.size() && i < 9; i++) {
                grid[i] = list.get(i);
            }
        } else {
            return null;
        }

        Item resultItem = crafting.buildVisualOrActualResult(ItemBuildContext.empty());
        if (resultItem == null || !(resultItem.platformItem() instanceof ItemStack resultStack)) return null;
        resultStack = toClientBoundStack(resultStack);

        ByteArrayOutputStream ebos = new ByteArrayOutputStream();
        DataOutputStream eout = new DataOutputStream(ebos);
        eout.writeUTF(recipe.id().asString());
        eout.writeBoolean(shapeless);
        eout.writeByte(width);
        eout.writeByte(height);
        for (Ingredient ingredient : grid) {
            ItemStack slotStack = representativeStack(ingredient);
            boolean hasItem = slotStack != null;
            eout.writeBoolean(hasItem);
            if (hasItem) {
                writeItemAppearance(eout, slotStack);
            }
        }
        writeItemAppearance(eout, resultStack);
        return ebos.toByteArray();
    }

    /** Mirrors buildCraftEngineDisplayEntry but sourced from Bukkit's own recipe API (ShapedRecipe/
     *  ShapelessRecipe + RecipeChoice) instead of CraftEngine's - covers recipes any OTHER plugin
     *  (Craftorithm in particular) registers via Bukkit.addRecipe(), which CraftEngine's own recipe
     *  list never tracks. Skipped entirely unless a CraftEngine item actually appears somewhere in it. */
    private byte[] buildBukkitDisplayEntry(org.bukkit.inventory.Recipe recipe, Set<String> seenIds) throws IOException {
        if (!(recipe instanceof org.bukkit.Keyed keyed)) return null;
        String recipeId = keyed.getKey().toString();
        if (seenIds.contains(recipeId)) return null;

        boolean shapeless;
        int width;
        int height;
        ItemStack[] grid;
        if (recipe instanceof org.bukkit.inventory.ShapedRecipe shaped) {
            shapeless = false;
            String[] shape = shaped.getShape();
            height = shape.length;
            width = height == 0 ? 0 : shape[0].length();
            if (width == 0 || height == 0 || width > 3 || height > 3) return null;
            Map<Character, org.bukkit.inventory.RecipeChoice> choiceMap = shaped.getChoiceMap();
            grid = new ItemStack[width * height];
            for (int row = 0; row < height; row++) {
                String line = shape[row];
                for (int col = 0; col < width; col++) {
                    char ch = col < line.length() ? line.charAt(col) : ' ';
                    org.bukkit.inventory.RecipeChoice choice = ch == ' ' ? null : choiceMap.get(ch);
                    grid[row * width + col] = choice == null ? null : choice.getItemStack();
                }
            }
        } else if (recipe instanceof org.bukkit.inventory.ShapelessRecipe shapelessRecipe) {
            shapeless = true;
            width = 3;
            height = 3;
            List<org.bukkit.inventory.RecipeChoice> choices = shapelessRecipe.getChoiceList();
            grid = new ItemStack[9];
            for (int i = 0; i < choices.size() && i < 9; i++) {
                grid[i] = choices.get(i).getItemStack();
            }
        } else {
            return null;
        }

        ItemStack result = recipe.getResult();
        if (result == null || result.getType().isAir()) return null;

        boolean involvesCraftEngine = isCraftEngineItem(result);
        if (!involvesCraftEngine) {
            for (ItemStack stack : grid) {
                if (isCraftEngineItem(stack)) {
                    involvesCraftEngine = true;
                    break;
                }
            }
        }
        if (!involvesCraftEngine) return null;

        result = toClientBoundStack(result);

        ByteArrayOutputStream ebos = new ByteArrayOutputStream();
        DataOutputStream eout = new DataOutputStream(ebos);
        eout.writeUTF(recipeId);
        eout.writeBoolean(shapeless);
        eout.writeByte(width);
        eout.writeByte(height);
        for (ItemStack slotStack : grid) {
            ItemStack corrected = (slotStack == null || slotStack.getType().isAir()) ? null : toClientBoundStack(slotStack);
            boolean hasItem = corrected != null;
            eout.writeBoolean(hasItem);
            if (hasItem) {
                writeItemAppearance(eout, corrected);
            }
        }
        writeItemAppearance(eout, result);
        return ebos.toByteArray();
    }

    /**
     * Same idea as buildCraftingDisplayPayload but for the smithing table: CraftEngine's own
     * CustomSmithingTransformRecipe (template/base/addition ingredients + a fixed result) and any other
     * plugin's Bukkit-registered SmithingTransformRecipe that involves a CraftEngine item anywhere.
     * Trim recipes (CustomSmithingTrimRecipe) are intentionally not covered - their "result" is computed
     * dynamically from whatever base/addition/pattern the player picks, not a fixed ItemStack, so there's
     * no single precise result to show.
     */
    private byte[] buildSmithingDisplayPayload() {
        List<byte[]> entries = new ArrayList<>();
        Set<String> allIds = new HashSet<>();

        try {
            for (Recipe recipe : BukkitRecipeManager.instance().recipesByType(RecipeType.SMITHING)) {
                try {
                    byte[] entry = buildCraftEngineSmithingDisplayEntry(recipe);
                    if (entry != null) {
                        entries.add(entry);
                        allIds.add(recipe.id().asString());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to export CraftEngine smithing recipe '" + recipe.id() + "' for display sync", t);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine smithing recipes for display sync", t);
        }

        java.util.Iterator<org.bukkit.inventory.Recipe> smithingBukkitRecipes = plugin.getServer().recipeIterator();
        while (smithingBukkitRecipes.hasNext()) {
            org.bukkit.inventory.Recipe recipe = smithingBukkitRecipes.next();
            try {
                byte[] entry = buildBukkitSmithingDisplayEntry(recipe, allIds);
                if (entry != null) {
                    entries.add(entry);
                    if (recipe instanceof org.bukkit.Keyed keyed) {
                        allIds.add(keyed.getKey().toString());
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to export a Bukkit-registered smithing recipe for display sync", t);
            }
        }

        smithingDisplayRecipeIds = Set.copyOf(allIds);
        return countPrefixed(entries);
    }

    private byte[] buildCraftEngineSmithingDisplayEntry(Recipe recipe) throws IOException {
        if (!(recipe instanceof CustomSmithingTransformRecipe smithing)) return null;

        Item resultItem = smithing.buildVisualOrActualResult(ItemBuildContext.empty());
        if (resultItem == null || !(resultItem.platformItem() instanceof ItemStack resultStack)) return null;
        resultStack = toClientBoundStack(resultStack);

        ItemStack templateStack = representativeStack(smithing.template());
        ItemStack baseStack = representativeStack(smithing.base());
        ItemStack additionStack = representativeStack(smithing.addition());

        ByteArrayOutputStream ebos = new ByteArrayOutputStream();
        DataOutputStream eout = new DataOutputStream(ebos);
        eout.writeUTF(recipe.id().asString());
        writeOptionalAppearance(eout, templateStack);
        writeOptionalAppearance(eout, baseStack);
        writeOptionalAppearance(eout, additionStack);
        writeItemAppearance(eout, resultStack);
        return ebos.toByteArray();
    }

    /** Mirrors buildCraftEngineSmithingDisplayEntry but sourced from Bukkit's own SmithingTransformRecipe
     *  (RecipeChoice-based) - covers recipes any OTHER plugin registers via Bukkit.addRecipe(). Skipped
     *  entirely unless a CraftEngine item actually appears somewhere in it. */
    private byte[] buildBukkitSmithingDisplayEntry(org.bukkit.inventory.Recipe recipe, Set<String> seenIds) throws IOException {
        if (!(recipe instanceof org.bukkit.inventory.SmithingTransformRecipe smithing)) return null;
        String recipeId = smithing.getKey().toString();
        if (seenIds.contains(recipeId)) return null;

        ItemStack templateStack = firstChoice(smithing.getTemplate());
        ItemStack baseStack = firstChoice(smithing.getBase());
        ItemStack additionStack = firstChoice(smithing.getAddition());
        ItemStack result = smithing.getResult();
        if (result == null || result.getType().isAir()) return null;

        boolean involvesCraftEngine = isCraftEngineItem(result) || isCraftEngineItem(templateStack)
                || isCraftEngineItem(baseStack) || isCraftEngineItem(additionStack);
        if (!involvesCraftEngine) return null;

        result = toClientBoundStack(result);

        ByteArrayOutputStream ebos = new ByteArrayOutputStream();
        DataOutputStream eout = new DataOutputStream(ebos);
        eout.writeUTF(recipeId);
        writeOptionalAppearance(eout, templateStack == null ? null : toClientBoundStack(templateStack));
        writeOptionalAppearance(eout, baseStack == null ? null : toClientBoundStack(baseStack));
        writeOptionalAppearance(eout, additionStack == null ? null : toClientBoundStack(additionStack));
        writeItemAppearance(eout, result);
        return ebos.toByteArray();
    }

    private static ItemStack firstChoice(org.bukkit.inventory.RecipeChoice choice) {
        if (choice == null) return null;
        ItemStack stack = choice.getItemStack();
        return (stack == null || stack.getType().isAir()) ? null : stack;
    }

    /** [hasItem:bool][appearance?] - like writeItemAppearance but for a slot that may legitimately be
     *  empty (smithing's template/addition are optional on CraftEngine's own recipe type). */
    private static void writeOptionalAppearance(DataOutputStream out, ItemStack stack) throws IOException {
        boolean has = stack != null && !stack.getType().isAir();
        out.writeBoolean(has);
        if (has) {
            writeItemAppearance(out, stack);
        }
    }

    private static boolean isCraftEngineItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        return BukkitItemManager.instance().wrap(stack).getDefinition().isPresent();
    }

    /** One representative, appearance-correct ItemStack for an ingredient slot: prefers an actual
     *  CraftEngine custom item (so the slot shows the right skin, not just the right shape), falling
     *  back to the ingredient's underlying vanilla material otherwise. */
    private static ItemStack representativeStack(Ingredient ingredient) {
        if (ingredient == null) return null;
        for (UniqueKey key : ingredient.items()) {
            BukkitItemDefinition def = CraftEngineItems.byId(key.key());
            if (def != null) {
                ItemStack stack = def.buildItem(ItemBuildContext.empty(), 1).getBukkitItem();
                return stack == null ? null : toClientBoundStack(stack);
            }
        }
        for (UniqueKey key : ingredient.minecraftItems()) {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(key.key().asString());
            if (material != null) {
                return new ItemStack(material);
            }
        }
        return null;
    }

    /** Every CraftEngine item id that appears as an ingredient or result of a custom crafting-table
     *  recipe. Used to trim the items payload down to what players can actually look up in a recipe
     *  book/JEI, instead of shipping every loaded item (most servers load far more than are reachable
     *  via crafting - the rest come from commands, mob drops, other plugins, etc). */
    private Set<Key> collectCraftingReferencedItemIds() {
        Set<Key> ids = new HashSet<>();
        try {
            for (Recipe recipe : BukkitRecipeManager.instance().recipesByType(RecipeType.CRAFTING)) {
                try {
                    if (!(recipe instanceof CustomCraftingTableRecipe crafting)) continue;
                    for (Ingredient ingredient : crafting.ingredientsInUse()) {
                        if (ingredient == null) continue;
                        for (UniqueKey key : ingredient.items()) {
                            ids.add(key.key());
                        }
                    }
                    Item resultItem = crafting.buildVisualOrActualResult(ItemBuildContext.empty());
                    if (resultItem != null) {
                        resultItem.getDefinition().ifPresent(def -> ids.add(def.id()));
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to inspect CraftEngine crafting recipe '" + recipe.id() + "' while collecting referenced items", t);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine crafting recipes while collecting referenced items", t);
        }

        // Also recognize CraftEngine items used in recipes registered by OTHER plugins (e.g. Craftorithm)
        // via the plain Bukkit recipe API - those never show up in CraftEngine's own recipe list above,
        // since that only tracks recipes CraftEngine itself parsed from its own config.
        java.util.Iterator<org.bukkit.inventory.Recipe> bukkitRecipes = plugin.getServer().recipeIterator();
        while (bukkitRecipes.hasNext()) {
            org.bukkit.inventory.Recipe recipe = bukkitRecipes.next();
            try {
                addIfCraftEngineItem(recipe.getResult(), ids);
                if (recipe instanceof org.bukkit.inventory.ShapedRecipe shaped) {
                    for (org.bukkit.inventory.RecipeChoice choice : shaped.getChoiceMap().values()) {
                        if (choice != null) addIfCraftEngineItem(choice.getItemStack(), ids);
                    }
                } else if (recipe instanceof org.bukkit.inventory.ShapelessRecipe shapeless) {
                    for (org.bukkit.inventory.RecipeChoice choice : shapeless.getChoiceList()) {
                        addIfCraftEngineItem(choice.getItemStack(), ids);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to inspect a Bukkit-registered recipe while collecting referenced items", t);
            }
        }
        return ids;
    }

    private static void addIfCraftEngineItem(ItemStack stack, Set<Key> ids) {
        if (stack == null || stack.getType().isAir()) return;
        BukkitItemManager.instance().wrap(stack).getDefinition().ifPresent(def -> ids.add(def.id()));
    }

    private byte[] buildItemsPayload(Set<Key> referencedItemIds) {
        Map<Key, ItemDefinition> loaded;
        try {
            loaded = CraftEngineItems.loadedItems();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine loaded items", t);
            return emptyCountPayload();
        }
        List<byte[]> entries = new ArrayList<>();
        for (Key id : loaded.keySet()) {
            if (!referencedItemIds.contains(id)) continue;
            try {
                BukkitItemDefinition def = CraftEngineItems.byId(id);
                if (def == null) continue;
                ItemStack stack = def.buildItem(ItemBuildContext.empty(), 1).getBukkitItem();
                if (stack == null) continue;
                stack = toClientBoundStack(stack);
                ByteArrayOutputStream ebos = new ByteArrayOutputStream();
                DataOutputStream eout = new DataOutputStream(ebos);
                eout.writeUTF(id.asString());
                writeItemAppearance(eout, stack);
                entries.add(ebos.toByteArray());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to build CraftEngine item '" + id + "' for sync", t);
            }
        }
        return countPrefixed(entries);
    }

    private byte[] buildBlocksPayload() {
        Map<Key, BlockDefinition> loaded;
        try {
            loaded = CraftEngineBlocks.loadedBlocks();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine loaded blocks", t);
            return emptyCountPayload();
        }
        List<byte[]> entries = new ArrayList<>();
        for (Map.Entry<Key, BlockDefinition> e : loaded.entrySet()) {
            try {
                for (ImmutableBlockState state : e.getValue().variantProvider().states()) {
                    if (state.visualBlockState() == null) continue;
                    String visual = state.visualBlockState().getAsString();
                    ByteArrayOutputStream ebos = new ByteArrayOutputStream();
                    DataOutputStream eout = new DataOutputStream(ebos);
                    eout.writeUTF(e.getKey().asString());
                    eout.writeUTF(visual);
                    entries.add(ebos.toByteArray());
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to export CraftEngine block '" + e.getKey() + "' for sync", t);
            }
        }
        return countPrefixed(entries);
    }

    private byte[] buildBrewingPayload() {
        List<Recipe> recipes;
        try {
            recipes = BukkitRecipeManager.instance().recipesByType(RecipeType.BREWING);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine brewing recipes", t);
            return emptyCountPayload();
        }
        List<byte[]> entries = new ArrayList<>();
        for (Recipe recipe : recipes) {
            try {
                if (!(recipe instanceof CustomBrewingRecipe brewing)) continue;
                String ingredientKey = representativeKey(brewing.ingredient());
                if (ingredientKey == null) continue;
                ItemStack result = (ItemStack) brewing.result().buildItem(ItemBuildContext.empty()).platformItem();
                if (result == null) continue;
                result = toClientBoundStack(result);
                ByteArrayOutputStream ebos = new ByteArrayOutputStream();
                DataOutputStream eout = new DataOutputStream(ebos);
                eout.writeUTF(recipe.id().asString());
                eout.writeUTF(ingredientKey);
                writeItemAppearance(eout, result);
                entries.add(ebos.toByteArray());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to export CraftEngine brewing recipe for sync", t);
            }
        }
        return countPrefixed(entries);
    }

    /**
     * CraftEngine only applies its client-bound processors - which set item_model, and may rewrite
     * name/lore text - during the server-to-client packet transform. {@code BukkitItemDefinition#buildItem}
     * alone returns the logical/server-bound stack and never carries them, no matter what the item's
     * config declares. {@link BukkitItemManager#s2c} is the same public entry point CraftEngine's own
     * packet listener uses, so this reproduces exactly what a connected player's client actually renders.
     * A null player is safe here: the processor that sets item_model (obfuscation included) keys off
     * CraftEngine's global item-model mapping, not per-player state, and this cache is built once and
     * shared across all players anyway.
     */
    public static ItemStack toClientBoundStack(ItemStack stack) {
        return BukkitItemManager.instance().s2c(stack, null).orElse(stack);
    }

    /** [baseItem:UTF][hasCMD:bool][cmd:int?][hasItemModel:bool][itemModel:UTF?][hasName:bool][name:JSON?] */
    private static void writeItemAppearance(DataOutputStream out, ItemStack stack) throws IOException {
        out.writeUTF(stack.getType().getKey().toString());
        ItemMeta meta = stack.getItemMeta();
        boolean hasCmd = meta != null && meta.hasCustomModelData();
        out.writeBoolean(hasCmd);
        if (hasCmd) {
            out.writeInt(meta.getCustomModelData());
        }
        boolean hasItemModel = meta != null && meta.hasItemModel();
        out.writeBoolean(hasItemModel);
        if (hasItemModel) {
            out.writeUTF(meta.getItemModel().toString());
        }
        // custom_name wins over item_name (matches vanilla's own tooltip name priority). CraftEngine
        // commonly sets item_name to a *translatable* component (e.g. a <lang:...> key) rather than
        // literal text, so this must travel as full JSON and get resolved client-side - plain-text
        // serialization would silently drop the translation and send an empty string.
        Component name = null;
        if (meta != null) {
            if (meta.hasCustomName()) name = meta.customName();
            else if (meta.hasItemName()) name = meta.itemName();
        }
        boolean hasName = name != null;
        out.writeBoolean(hasName);
        if (hasName) {
            out.writeUTF(GsonComponentSerializer.gson().serialize(name));
        }
    }

    private static String representativeKey(Ingredient ingredient) {
        if (ingredient == null) return null;
        if (!ingredient.minecraftItems().isEmpty()) return ingredient.minecraftItems().get(0).toString();
        if (!ingredient.items().isEmpty()) return ingredient.items().get(0).toString();
        return null;
    }

    private static byte[] countPrefixed(List<byte[]> entries) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(entries.size());
            for (byte[] entry : entries) {
                out.write(entry);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] emptyCountPayload() {
        return countPrefixed(List.of());
    }
}
