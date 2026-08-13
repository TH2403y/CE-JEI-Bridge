package com.ceclientbridge.recipe;

import com.ceclientbridge.sync.SyncManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.momirealms.craftengine.bukkit.item.recipe.BukkitRecipeManager;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.item.recipe.RecipeType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * This Minecraft version stopped sending clients the vanilla recipe-sync packet at all, so JEI (and the
 * vanilla recipe book) has no recipe data - including CraftEngine's own custom recipes, which live in
 * the same RecipeManager - unless something resends it. Ported from Mrbysco/JEIRecipeBridge's
 * RecipeHandler (Fabric branch only; this server only supports Fabric clients), which feeds Fabric API's
 * own built-in "fabric:recipe_sync" consumer to repopulate the client's RecipeManager directly - JEI's
 * stock vanilla recipe categories then pick everything up natively, no custom JEI code needed.
 *
 * The recipe rebuilding itself is version-specific (26.x uses ItemStackTemplate/CommonInfo/CraftingBookInfo
 * while 1.21.11 uses plain ItemStack/TransmuteResult), so it lives behind {@link RecipeCorrector}.
 */
public final class RecipeSyncListener implements Listener {

    private final JavaPlugin plugin;
    private final SyncManager syncManager;
    private final RecipeCorrector corrector;

    public RecipeSyncListener(JavaPlugin plugin, SyncManager syncManager) {
        this.plugin = plugin;
        this.syncManager = syncManager;
        this.corrector = new RecipeCorrectorImpl(plugin);
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
                    RecipeHolder<?> corrected = corrector.buildCorrectedRecipe(recipe.id().asString(), crafting);
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
        List<RecipeHolder<?>> resendCorrected = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            if (correctedIds.contains(holder.id().identifier().toString())) continue;
            try {
                RecipeHolder<?> fixed = corrector.tryCorrectVanillaResult(holder);
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
}