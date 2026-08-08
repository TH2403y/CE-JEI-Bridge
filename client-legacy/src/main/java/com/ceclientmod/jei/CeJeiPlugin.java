package com.ceclientmod.jei;

import com.ceclientmod.CraftEngineClientModInit;
import com.ceclientmod.cache.CeCraftingEntry;
import com.ceclientmod.cache.CeItem;
import com.ceclientmod.cache.CeItemRegistry;
import com.ceclientmod.cache.CeSmithingEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.vanilla.IJeiShapedRecipeBuilder;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loaded by JEI only via the "jei_mod_plugin" fabric.mod.json entrypoint (JEI resolves and classloads
 * this itself) - never referenced from our own client entrypoint, so the game still launches fine
 * without JEI installed.
 *
 * Distinguishes CraftEngine's custom items from vanilla ones for every vanilla base item up front
 * (see {@link #registerItemSubtypes}), then pushes the actual item list into JEI's ingredient list at
 * runtime once we have it (server sync arrives after JEI has already started up).
 */
@JeiPlugin
public final class CeJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.of("ceclientmod", "jei_plugin");
    private static final Logger LOGGER = LoggerFactory.getLogger("ceclientmod");

    private static IIngredientManager ingredientManager;
    private static List<ItemStack> lastRegistered = List.of();

    private static IVanillaRecipeFactory vanillaRecipeFactory;
    private static IRecipeManager recipeManager;
    // Same limitation as items: IRecipeManager#addRecipes has no matching remove, so these display
    // recipes are pushed once per session; a later CraftEngine reload won't add/remove them until the
    // player reconnects.
    private static boolean craftingDisplayAdded = false;
    private static boolean smithingDisplayAdded = false;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // We don't know which base items CraftEngine reskins until a sync arrives from a server, and
        // this method only runs once at JEI startup - so register the interpreter for every vanilla
        // item up front. It's a cheap no-op for any stack without a craftengine:id custom_data tag.
        //
        // Some vanilla items (decorated_pot, light, potions, etc.) already have JEI's OWN built-in
        // interpreter registered - registerSubtypeInterpreter throws IllegalArgumentException on a
        // collision. Uncaught, that exception aborted this loop partway through the item registry AND
        // (per JEI's own plugin-loading behavior) appears to have caused JEI to skip this plugin's
        // later callbacks entirely (registerRecipes never ran, so vanillaRecipeFactory stayed null,
        // so the crafting-display recipes below were never registered) - silently leaving every item
        // after the first collision (including the vanilla materials CraftEngine items are built on)
        // with no interpreter at all. That's why ingredient slots never resolved to a CraftEngine
        // identity while the item list (populated a different way, via addIngredientsAtRuntime) worked.
        ISubtypeInterpreter<ItemStack> interpreter = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return CeItemRegistry.ceIdOf(stack).orElse(null);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                return CeItemRegistry.ceIdOf(stack).orElse("");
            }
        };
        for (Item item : Registries.ITEM) {
            try {
                registration.registerSubtypeInterpreter(item, interpreter);
            } catch (IllegalArgumentException ignored) {
                // another interpreter already owns this item type - leave it alone and keep going.
            }
        }
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        // no-op here: CraftEngine's item list only exists after connecting to a server - see onItemsUpdated().
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // A wholly separate JEI recipe type from RecipeTypes.SMITHING (JEI's own built-in one) - see
        // CeSmithingCategory for why the crafting-table fix (IVanillaRecipeFactory's SlotDisplay
        // override) doesn't extend to smithing: there's no equivalent precise-display builder for it.
        registration.addRecipeCategories(new CeSmithingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.SMITHING_TABLE), CeSmithingCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Only the vanilla-recipe-factory reference is available here (server data doesn't exist yet) -
        // cache it for onCraftingDisplayUpdated(), which runs once the sync arrives.
        vanillaRecipeFactory = registration.getVanillaRecipeFactory();
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        ingredientManager = jeiRuntime.getIngredientManager();
        recipeManager = jeiRuntime.getRecipeManager();
        pushCurrentItems();
        pushCraftingDisplayIfPending();
        pushSmithingDisplayIfPending();
    }

    @Override
    public void onRuntimeUnavailable() {
        ingredientManager = null;
        recipeManager = null;
        craftingDisplayAdded = false;
        smithingDisplayAdded = false;
    }

    /** Called by CraftEngineClientModInit whenever a fresh items sync finishes parsing. */
    public static void onItemsUpdated() {
        pushCurrentItems();
    }

    /** Called by CraftEngineClientModInit whenever a fresh crafting-display sync finishes parsing. */
    public static void onCraftingDisplayUpdated() {
        pushCraftingDisplayIfPending();
    }

    /** Called by CraftEngineClientModInit whenever a fresh smithing-display sync finishes parsing. */
    public static void onSmithingDisplayUpdated() {
        pushSmithingDisplayIfPending();
    }

    private static void pushCurrentItems() {
        if (ingredientManager == null) {
            return;
        }
        List<ItemStack> fresh = new ArrayList<>();
        for (CeItem item : CraftEngineClientModInit.items().all()) {
            fresh.add(item.stack());
        }
        if (!lastRegistered.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, lastRegistered);
        }
        if (!fresh.isEmpty()) {
            ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, fresh);
        }
        lastRegistered = fresh;
    }

    /**
     * Feeds JEI's OWN stock crafting category (RecipeTypes.CRAFTING) a second, more precise set of
     * display-only recipes for CraftEngine's crafting-table recipes, built from EXACT per-slot
     * ItemStacks (see CeCraftingRegistry) rather than the type-only Ingredient the native Fabric recipe
     * resync carries. Vanilla's Ingredient matches by item TYPE, not by component data - it can never
     * show "this specific reskinned plank", only "any plank of this base material" - which is why
     * ingredient slots kept rendering the generic vanilla look even after the resync's result fix.
     * IJeiShapedRecipeBuilder#define's SlotDisplay parameter is exactly JEI's own escape hatch for this:
     * a rendering-only override independent of the Ingredient used alongside it. These recipes don't
     * replace or affect real crafting mechanics - only what JEI shows when you look one up.
     */
    private static void pushCraftingDisplayIfPending() {
        if (recipeManager == null || vanillaRecipeFactory == null || craftingDisplayAdded) {
            LOGGER.info("ceclientmod: crafting display push skipped (recipeManager={}, vanillaRecipeFactory={}, alreadyAdded={})",
                    recipeManager != null, vanillaRecipeFactory != null, craftingDisplayAdded);
            return;
        }
        List<CeCraftingEntry> entries = CraftEngineClientModInit.craftingDisplay().all();
        LOGGER.info("ceclientmod: attempting to build {} crafting display entries for JEI", entries.size());
        if (entries.isEmpty()) {
            return;
        }
        List<RecipeEntry<CraftingRecipe>> recipes = new ArrayList<>();
        for (CeCraftingEntry entry : entries) {
            try {
                RecipeEntry<CraftingRecipe> recipe = buildDisplayRecipe(entry);
                if (recipe != null) {
                    recipes.add(recipe);
                }
            } catch (Exception e) {
                LOGGER.warn("ceclientmod: failed to build JEI display recipe for '" + entry.recipeId() + "'", e);
            }
        }
        LOGGER.info("ceclientmod: successfully built {}/{} crafting display recipes", recipes.size(), entries.size());
        if (!recipes.isEmpty()) {
            try {
                recipeManager.addRecipes(RecipeTypes.CRAFTING, recipes);
                LOGGER.info("ceclientmod: registered {} precisely-skinned crafting display recipes with JEI", recipes.size());
            } catch (Exception e) {
                LOGGER.warn("ceclientmod: failed to register crafting display recipes with JEI's IRecipeManager", e);
            }
        }
        craftingDisplayAdded = true;
    }

    private static RecipeEntry<CraftingRecipe> buildDisplayRecipe(CeCraftingEntry entry) {
        List<ItemStack> inputs = entry.inputs();
        int width = entry.width();
        int height = entry.height();
        if (width <= 0 || height <= 0 || inputs.size() != width * height) {
            return null;
        }

        IJeiShapedRecipeBuilder builder = vanillaRecipeFactory.createShapedRecipeBuilder(
                CraftingRecipeCategory.MISC, List.of(entry.result()));

        Map<ItemStack, Character> assigned = new LinkedHashMap<>();
        char next = 'a';
        for (int row = 0; row < height; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < width; col++) {
                ItemStack stack = inputs.get(row * width + col);
                if (stack.isEmpty()) {
                    line.append(' ');
                    continue;
                }
                Character ch = assigned.get(stack);
                if (ch == null) {
                    ch = next++;
                    assigned.put(stack, ch);
                    Ingredient typeIngredient = Ingredient.ofItem(stack.getItem());
                    builder.define(ch, typeIngredient);
                }
                line.append((char) ch);
            }
            builder.pattern(line.toString());
        }
        CraftingRecipe recipe = builder.build();
        RegistryKey<net.minecraft.recipe.Recipe<?>> id =
                RegistryKey.of(RegistryKeys.RECIPE, Identifier.of(entry.recipeId()));
        return new RecipeEntry<>(id, recipe);
    }

    /**
     * Feeds the custom CeSmithingCategory (registered in registerCategories) the exact template/base/
     * addition/result stacks SyncManager#buildSmithingDisplayPayload sent - no IVanillaRecipeFactory
     * involved here, unlike crafting, since there's no equivalent precise-display builder for smithing
     * and CeSmithingCategory places all four slots manually via IRecipeLayoutBuilder#addSlot instead.
     */
    private static void pushSmithingDisplayIfPending() {
        if (recipeManager == null || smithingDisplayAdded) {
            return;
        }
        List<CeSmithingEntry> entries = CraftEngineClientModInit.smithingDisplay().all();
        LOGGER.info("ceclientmod: attempting to register {} smithing display entries with JEI", entries.size());
        if (entries.isEmpty()) {
            // Don't latch smithingDisplayAdded yet - onRuntimeAvailable fires before the server sync has
            // necessarily arrived, so an empty registry here just means "too early", not "nothing to
            // show". Leaving the flag unset lets onSmithingDisplayUpdated's later call actually register
            // the real entries once they exist (same pattern as pushCraftingDisplayIfPending).
            return;
        }
        try {
            recipeManager.addRecipes(CeSmithingCategory.TYPE, entries);
            LOGGER.info("ceclientmod: registered {} precisely-skinned smithing display recipes with JEI", entries.size());
        } catch (Exception e) {
            LOGGER.warn("ceclientmod: failed to register smithing display recipes with JEI's IRecipeManager", e);
        }
        smithingDisplayAdded = true;
    }
}
