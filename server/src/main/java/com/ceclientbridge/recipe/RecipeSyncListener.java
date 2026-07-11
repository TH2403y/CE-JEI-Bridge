package com.ceclientbridge.recipe;

import com.ceclientbridge.sync.SyncManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.item.recipe.BukkitRecipeManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomShapedRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomShapelessRecipe;
import net.momirealms.craftengine.core.item.recipe.RecipeType;
import net.momirealms.craftengine.core.util.UniqueKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * This Minecraft version stopped sending clients the vanilla recipe-sync packet at all, so JEI (and the
 * vanilla recipe book) has no recipe data - including CraftEngine's own custom recipes, which live in
 * the same RecipeManager - unless something resends it. Ported from Mrbysco/JEIRecipeBridge's
 * RecipeHandler (Fabric branch only; this server only supports Fabric clients), which feeds Fabric API's
 * own built-in "fabric:recipe_sync" consumer to repopulate the client's RecipeManager directly - JEI's
 * stock vanilla recipe categories then pick everything up natively, no custom JEI code needed.
 */
public final class RecipeSyncListener implements Listener {

    private final JavaPlugin plugin;
    private final SyncManager syncManager;

    public RecipeSyncListener(JavaPlugin plugin, SyncManager syncManager) {
        this.plugin = plugin;
        this.syncManager = syncManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player originalPlayer = event.getPlayer();
        String brand = originalPlayer.getClientBrandName();
        if (brand == null || !brand.equalsIgnoreCase("fabric")) {
            return;
        }
        try {
            ServerPlayer player = ((CraftPlayer) originalPlayer).getHandle();
            RecipeMap recipeMap = player.level().getServer().getRecipeManager().recipes;
            sendFabricPayload(player, recipeMap);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to sync recipes to '" + originalPlayer.getName() + "'", t);
        }
    }

    private void sendFabricPayload(ServerPlayer player, RecipeMap recipeMap) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().getServer().registryAccess());

        // CraftEngine's own crafting-table recipes only ever carry their logical/server-bound result in
        // the live RecipeManager - item_model and friends are applied by a client-bound processor that
        // normally only runs for the ordinary item/container packets CraftEngine's own network layer
        // intercepts, not this raw resend. Rebuild these specific recipes as plain vanilla ShapedRecipe/
        // ShapelessRecipe with a corrected result before resending, and exclude their broken originals
        // from the generic pass below (see SyncManager#toClientBoundStack for the same fix applied to
        // the main item/brewing sync).
        List<RecipeHolder<?>> correctedRecipes = new ArrayList<>();
        Set<String> correctedIds = new HashSet<>();
        try {
            for (net.momirealms.craftengine.core.item.recipe.Recipe recipe : BukkitRecipeManager.instance().recipesByType(RecipeType.CRAFTING)) {
                try {
                    if (!(recipe instanceof CustomCraftingTableRecipe crafting)) continue;
                    RecipeHolder<?> corrected = buildCorrectedRecipe(recipe.id().asString(), crafting);
                    if (corrected == null) continue;
                    correctedRecipes.add(corrected);
                    correctedIds.add(recipe.id().asString());
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Failed to rebuild CraftEngine crafting recipe '" + recipe.id() + "' for client recipe sync", t);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to read CraftEngine crafting recipes for client recipe sync", t);
        }

        // Authoritative: every id SyncManager actually built a precise JEI display entry for (covers
        // CraftEngine's own recipes above AND any other plugin's, e.g. Craftorithm, that involves a
        // CraftEngine item as an INGREDIENT even when the result itself doesn't need correcting - the
        // narrower result-only check below can't detect that case on its own). Same reasoning now
        // applies to smithing: CeSmithingCategory (a wholly separate JEI recipe type, not a reuse of
        // RecipeTypes.SMITHING) covers these ids with precise template/base/addition/result stacks, so
        // resending them here would just let JEI's own automatic smithing category show the generic
        // type-only version alongside the precise one.
        correctedIds.addAll(syncManager.craftingDisplayRecipeIds());
        correctedIds.addAll(syncManager.smithingDisplayRecipeIds());

        // Recipes that are ALREADY plain vanilla ShapedRecipe/ShapelessRecipe/SmithingTransformRecipe -
        // genuinely vanilla ones, another plugin's (e.g. Craftorithm, via Bukkit.addRecipe()), or
        // CraftEngine's own datapack-style recipes - never go through CraftEngine's client-bound
        // transform either, since that's only ever applied inside buildCorrectedRecipe's own
        // CraftEngine-config-driven path above. Detect a CraftEngine result via reflection and correct
        // it in a copy.
        //
        // Crafting-shaped and smithing results covered by the two display-id sets above get fully
        // excluded below - JEI's precise per-recipe-type display (CeJeiPlugin, fed by
        // SyncManager#buildCraftingDisplayPayload / #buildSmithingDisplayPayload) covers those under the
        // SAME recipe id, and resending a type-only-Ingredient copy alongside it caused JEI to prefer the
        // wrong one for crafting (confirmed: 1230/1230 display recipes registered successfully, but
        // ingredient slots still showed the generic look until these were excluded entirely) - excluding
        // smithing's own the same way from the start avoids repeating that bug.
        // Any recipe type with no precise JEI registration at all still gets the old treatment: resend a
        // corrected copy instead of excluding it, otherwise it would disappear from JEI entirely instead
        // of merely showing an uncorrected result.
        List<RecipeHolder<?>> resendCorrected = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            if (correctedIds.contains(holder.id().identifier().toString())) continue;
            try {
                RecipeHolder<?> fixed = tryCorrectVanillaResult(holder);
                if (fixed == null) continue;
                correctedIds.add(holder.id().identifier().toString());
                if (!(fixed.value() instanceof ShapedRecipe) && !(fixed.value() instanceof ShapelessRecipe)) {
                    resendCorrected.add(fixed);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to check/correct recipe '" + holder.id() + "' for a CraftEngine result", t);
            }
        }

        var list = new ArrayList<FabricRecipeSyncPayload.Entry>();
        var seen = new HashSet<RecipeSerializer<?>>();

        for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
            if (!seen.add(serializer)) continue;

            List<RecipeHolder<?>> recipes = new ArrayList<>();
            for (RecipeHolder<?> holder : recipeMap.values()) {
                if (correctedIds.contains(holder.id().identifier().toString())) continue;
                if (holder.value().getSerializer() == serializer) {
                    recipes.add(holder);
                }
            }
            for (RecipeHolder<?> holder : resendCorrected) {
                if (holder.value().getSerializer() == serializer) {
                    recipes.add(holder);
                }
            }

            if (!recipes.isEmpty()) {
                list.add(new FabricRecipeSyncPayload.Entry(serializer, recipes));
            }
        }

        // correctedRecipes (the CraftEngine-own-crafting-format pass above) is intentionally NOT resent
        // here - it exists only to populate correctedIds for exclusion. JEI's own display for those ids
        // comes exclusively from CeJeiPlugin's precise IVanillaRecipeFactory registration.

        var payload = new FabricRecipeSyncPayload(list);
        FabricRecipeSyncPayload.CODEC.encode(buffer, payload);

        byte[] bytes = new byte[buffer.writerIndex()];
        buffer.getBytes(0, bytes);

        player.connection.send(new ClientboundCustomPayloadPacket(
                new DiscardedPayload(Identifier.fromNamespaceAndPath("fabric", "recipe_sync"), bytes)));
    }

    /** Builds an equivalent plain-vanilla ShapedRecipe/ShapelessRecipe carrying the same id, shape and
     *  ingredients but a client-bound-corrected result, so JEI's stock recipe view (and the vanilla
     *  recipe book) render what players actually see rather than the logical/server-bound stack. */
    @SuppressWarnings("unchecked")
    private RecipeHolder<?> buildCorrectedRecipe(String id, CustomCraftingTableRecipe crafting) {
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
        ItemStackTemplate resultTemplate = ItemStackTemplate.fromNonEmptyStack(nmsResult);
        Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(true);
        CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> resourceKey =
                ResourceKey.create(Registries.RECIPE, Identifier.parse(id));

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
                nmsIngredients.add(ceIngredient.map(this::toVanillaIngredient));
            }
            ShapedRecipePattern pattern = new ShapedRecipePattern(width, height, nmsIngredients, Optional.empty());
            return new RecipeHolder<>(resourceKey, new ShapedRecipe(commonInfo, bookInfo, pattern, resultTemplate));
        } else if (crafting instanceof CustomShapelessRecipe shapeless) {
            List<Ingredient> nmsIngredients = new ArrayList<>();
            for (net.momirealms.craftengine.core.item.recipe.Ingredient ceIngredient : shapeless.ingredientsInUse()) {
                nmsIngredients.add(toVanillaIngredient(ceIngredient));
            }
            return new RecipeHolder<>(resourceKey, new ShapelessRecipe(commonInfo, bookInfo, resultTemplate, nmsIngredients));
        }
        plugin.getLogger().warning("Recipe sync: '" + id + "' is a " + crafting.getClass().getSimpleName()
                + " - neither shaped nor shapeless, skipping correction (ingredients/result may show incorrectly in JEI)");
        return null;
    }

    /** Recipes already in plain vanilla ShapedRecipe/ShapelessRecipe form - genuinely vanilla ones,
     *  another plugin's (e.g. Craftorithm), or CraftEngine's own datapack-style recipes - never go
     *  through CraftEngine's client-bound transform, since that only runs inside buildCorrectedRecipe's
     *  own CraftEngine-config-driven path above. Detect a CraftEngine result via reflection (there's no
     *  public "getResult()" on Recipe - assemble() needs a real crafting-grid input to compute one) and
     *  rebuild with a corrected result, keeping the original ingredients untouched. */
    private RecipeHolder<?> tryCorrectVanillaResult(RecipeHolder<?> holder) {
        net.minecraft.world.item.crafting.Recipe<?> recipe = holder.value();
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe) && !(recipe instanceof SmithingTransformRecipe)) {
            return null;
        }
        ItemStackTemplate originalTemplate = readResultField(recipe);
        if (originalTemplate == null) {
            return null;
        }
        org.bukkit.inventory.ItemStack bukkitResult = CraftItemStack.asBukkitCopy(originalTemplate);
        if (bukkitResult == null || bukkitResult.getType().isAir()) {
            return null;
        }
        if (BukkitItemManager.instance().wrap(bukkitResult).getDefinition().isEmpty()) {
            return null; // not a CraftEngine item - nothing to fix here
        }
        org.bukkit.inventory.ItemStack corrected = SyncManager.toClientBoundStack(bukkitResult);
        net.minecraft.world.item.ItemStack nmsCorrected = CraftItemStack.asNMSCopy(corrected);
        if (nmsCorrected.isEmpty()) {
            return null;
        }
        ItemStackTemplate correctedTemplate = ItemStackTemplate.fromNonEmptyStack(nmsCorrected);
        Recipe.CommonInfo commonInfo = new Recipe.CommonInfo(recipe.showNotification());

        if (recipe instanceof ShapedRecipe shaped) {
            CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, recipe.group());
            ShapedRecipePattern pattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(), Optional.empty());
            return new RecipeHolder<>(holder.id(), new ShapedRecipe(commonInfo, bookInfo, pattern, correctedTemplate));
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            CraftingRecipe.CraftingBookInfo bookInfo = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, recipe.group());
            List<Ingredient> ingredients = readShapelessIngredients(shapelessRecipe);
            if (ingredients == null) {
                plugin.getLogger().warning("Recipe sync: '" + holder.id() + "' has a CraftEngine result but its ingredients list couldn't be read, skipping correction");
                return null;
            }
            return new RecipeHolder<>(holder.id(), new ShapelessRecipe(commonInfo, bookInfo, correctedTemplate, ingredients));
        }
        // SmithingTransformRecipe: templateIngredient()/baseIngredient()/additionIngredient() are public
        // getters (no reflection needed here, unlike the crafting-shape cases above) - reused as-is,
        // same "vanilla Ingredient can't carry a specific skin" limitation as crafting's ingredients.
        SmithingTransformRecipe smithing = (SmithingTransformRecipe) recipe;
        return new RecipeHolder<>(holder.id(), new SmithingTransformRecipe(
                commonInfo, smithing.templateIngredient(), smithing.baseIngredient(), smithing.additionIngredient(), correctedTemplate));
    }

    private static ItemStackTemplate readResultField(net.minecraft.world.item.crafting.Recipe<?> recipe) {
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
            return (ItemStackTemplate) field.get(recipe);
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

    /** Vanilla Ingredient only matches by item TYPE, not by component data - it can't distinguish "a
     *  plain leather helmet" from "a CraftEngine cap" the way a full ItemStack can, so ingredient slots
     *  are a best-effort match on the underlying vanilla material. The result (what players actually
     *  look at) is what gets the full appearance fix above. */
    private Ingredient toVanillaIngredient(net.momirealms.craftengine.core.item.recipe.Ingredient ceIngredient) {
        for (UniqueKey key : ceIngredient.minecraftItems()) {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key.key().asString()));
            if (item != null) {
                return Ingredient.of(item);
            }
        }
        return Ingredient.of();
    }
}
