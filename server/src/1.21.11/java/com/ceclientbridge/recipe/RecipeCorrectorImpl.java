package com.ceclientbridge.recipe;

import com.ceclientbridge.sync.SyncManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomShapedRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomShapelessRecipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 1.21.11 implementation. Crafting results are plain ItemStack, smithing results are TransmuteResult, and
 * Shaped/Shapeless/SmithingTransformRecipe take group/category/result directly (no CommonInfo/CraftingBookInfo).
 */
public final class RecipeCorrectorImpl implements RecipeCorrector {

    private final JavaPlugin plugin;

    public RecipeCorrectorImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeHolder<?> buildCorrectedRecipe(String id, CustomCraftingTableRecipe crafting) {
        Item resultItem = crafting.buildVisualOrActualResult(ItemBuildContext.empty());
        if (resultItem == null) {
            plugin.getLogger().warning("Recipe sync: '" + id + "' has no result item, skipping correction");
            return null;
        }
        if (!(resultItem.platformItem() instanceof org.bukkit.inventory.ItemStack bukkitResult)) {
            plugin.getLogger().warning("Recipe sync: '" + id + "' result is not a Bukkit ItemStack (" + resultItem.platformItem() + "), skipping correction");
            return null;
        }
        bukkitResult = SyncManager.toClientBoundStack(bukkitResult);
        net.minecraft.world.item.ItemStack nmsResult = CraftItemStack.asNMSCopy(bukkitResult);
        if (nmsResult.isEmpty()) {
            plugin.getLogger().warning("Recipe sync: '" + id + "' corrected result converted to an empty NMS stack, skipping correction");
            return null;
        }
        String group = "";
        CraftingBookCategory category = CraftingBookCategory.MISC;
        ResourceKey<Recipe<?>> resourceKey = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));

        if (crafting instanceof CustomShapedRecipe shaped) {
            var parsed = shaped.parsedPattern();
            int width = parsed.width();
            int height = parsed.height();
            if (width == 0 || height == 0) {
                plugin.getLogger().warning("Recipe sync: '" + id + "' has a 0-size shaped pattern, skipping correction");
                return null;
            }
            Optional<net.momirealms.craftengine.core.item.recipe.Ingredient>[] ceIngredients = parsed.ingredients();
            List<Optional<Ingredient>> nmsIngredients = new ArrayList<>(ceIngredients.length);
            for (Optional<net.momirealms.craftengine.core.item.recipe.Ingredient> ceIngredient : ceIngredients) {
                nmsIngredients.add(ceIngredient.map(RecipeCorrector::toVanillaIngredient));
            }
            ShapedRecipePattern pattern = new ShapedRecipePattern(width, height, nmsIngredients, Optional.empty());
            return new RecipeHolder<>(resourceKey, new ShapedRecipe(group, category, pattern, nmsResult, true));
        } else if (crafting instanceof CustomShapelessRecipe shapeless) {
            List<Ingredient> nmsIngredients = new ArrayList<>();
            for (net.momirealms.craftengine.core.item.recipe.Ingredient ceIngredient : shapeless.ingredientsInUse()) {
                nmsIngredients.add(RecipeCorrector.toVanillaIngredient(ceIngredient));
            }
            return new RecipeHolder<>(resourceKey, new ShapelessRecipe(group, category, nmsResult, nmsIngredients));
        }
        plugin.getLogger().warning("Recipe sync: '" + id + "' is a " + crafting.getClass().getSimpleName()
                + " - neither shaped nor shapeless, skipping correction (ingredients/result may show incorrectly in JEI)");
        return null;
    }

    @Override
    public RecipeHolder<?> tryCorrectVanillaResult(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe) && !(recipe instanceof SmithingTransformRecipe)) {
            return null;
        }
        net.minecraft.world.item.ItemStack original = readResultField(recipe);
        if (original == null || original.isEmpty()) {
            return null;
        }
        org.bukkit.inventory.ItemStack bukkitResult = CraftItemStack.asBukkitCopy(original);
        if (bukkitResult == null || bukkitResult.getType().isAir()) {
            return null;
        }
        if (BukkitItemManager.instance().wrap(bukkitResult).getDefinition().isEmpty()) {
            return null;
        }
        org.bukkit.inventory.ItemStack corrected = SyncManager.toClientBoundStack(bukkitResult);
        net.minecraft.world.item.ItemStack nmsCorrected = CraftItemStack.asNMSCopy(corrected);
        if (nmsCorrected.isEmpty()) {
            return null;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            ShapedRecipePattern pattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(), Optional.empty());
            return new RecipeHolder<>(holder.id(), new ShapedRecipe(shaped.group(), shaped.category(), pattern, nmsCorrected, shaped.showNotification()));
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            List<Ingredient> ingredients = readShapelessIngredients(shapelessRecipe);
            if (ingredients == null) {
                plugin.getLogger().warning("Recipe sync: '" + holder.id() + "' has a CraftEngine result but its ingredients list couldn't be read, skipping correction");
                return null;
            }
            return new RecipeHolder<>(holder.id(), new ShapelessRecipe(shapelessRecipe.group(), shapelessRecipe.category(), nmsCorrected, ingredients));
        }
        SmithingTransformRecipe smithing = (SmithingTransformRecipe) recipe;
        TransmuteResult correctedResult = new TransmuteResult(nmsCorrected.getItemHolder(), nmsCorrected.getCount(), nmsCorrected.getComponentsPatch());
        return new RecipeHolder<>(holder.id(), new SmithingTransformRecipe(
                smithing.templateIngredient(), smithing.baseIngredient(), smithing.additionIngredient(), correctedResult));
    }

    /** Shaped/Shapeless store their result as ItemStack; SmithingTransformRecipe stores a TransmuteResult. */
    private static net.minecraft.world.item.ItemStack readResultField(Recipe<?> recipe) {
        try {
            Class<?> targetClass;
            if (recipe instanceof ShapedRecipe) {
                targetClass = ShapedRecipe.class;
            } else if (recipe instanceof ShapelessRecipe) {
                targetClass = ShapelessRecipe.class;
            } else {
                targetClass = SmithingTransformRecipe.class;
            }
            java.lang.reflect.Field field = targetClass.getDeclaredField("result");
            field.setAccessible(true);
            Object raw = field.get(recipe);
            if (raw instanceof net.minecraft.world.item.ItemStack stack) {
                return stack;
            }
            if (raw instanceof TransmuteResult transmute) {
                return new net.minecraft.world.item.ItemStack(transmute.item(), transmute.count(), transmute.components());
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Ingredient> readShapelessIngredients(ShapelessRecipe recipe) {
        try {
            java.lang.reflect.Field field = ShapelessRecipe.class.getDeclaredField("ingredients");
            field.setAccessible(true);
            return (List<Ingredient>) field.get(recipe);
        } catch (Throwable t) {
            return null;
        }
    }
}