package com.ceclientbridge.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.util.UniqueKey;

/**
 * Version-specific recipe rebuilding for the client recipe-sync resend. CraftEngine's own
 * crafting recipes carry a logical/server-bound result, so this rebuilds the equivalent vanilla
 * Shaped/Shapeless/SmithingTransform recipe with a client-bound-corrected result before it is
 * resent. The concrete implementation is provided per Minecraft target because the recipe result
 * representation and constructor shapes differ between 26.x and 1.21.11.
 */
public interface RecipeCorrector {

    /** Builds an equivalent plain-vanilla Shaped/Shapeless recipe for a CraftEngine crafting-table recipe. */
    RecipeHolder<?> buildCorrectedRecipe(String id, CustomCraftingTableRecipe crafting);

    /** Corrects a recipe that is already a plain vanilla Shaped/Shapeless/SmithingTransform whose result is a CraftEngine item. */
    RecipeHolder<?> tryCorrectVanillaResult(RecipeHolder<?> holder);

    /** Vanilla Ingredient matches only by item type, not component data - a best-effort material mapping for ingredient slots. */
    static Ingredient toVanillaIngredient(net.momirealms.craftengine.core.item.recipe.Ingredient ceIngredient) {
        for (UniqueKey key : ceIngredient.minecraftItems()) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key.key().asString()));
            if (item != null) {
                return Ingredient.of(item);
            }
        }
        return Ingredient.of();
    }
}