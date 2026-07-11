package com.ceclientmod.jei;

import com.ceclientmod.cache.CeSmithingEntry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A fully custom JEI recipe category for the smithing table, bypassing JEI's own built-in
 * RecipeTypes.SMITHING display entirely. That built-in category (like the vanilla recipe book) can only
 * show vanilla's own type-only Ingredient for the template/base/addition slots - it has no equivalent of
 * IVanillaRecipeFactory#createShapedRecipeBuilder's SlotDisplay override (the fix used for the crafting
 * table), so a reskinned CraftEngine item in those slots always rendered as its generic base material.
 * Manually placing all four slots here with exact ItemStacks (see CeSmithingEntry, sent by
 * SyncManager#buildSmithingDisplayPayload) sidesteps that limitation completely. RecipeSyncListener
 * excludes any recipe id registered here from the native Fabric recipe resync (see
 * SyncManager#smithingDisplayRecipeIds), so JEI's automatic smithing category never shows a competing,
 * incorrectly-skinned entry for the same recipe.
 */
public final class CeSmithingCategory implements IRecipeCategory<CeSmithingEntry> {

    public static final IRecipeType<CeSmithingEntry> TYPE =
            IRecipeType.create("ceclientmod", "smithing_display", CeSmithingEntry.class);

    private static final int SLOT_SIZE = 18;

    private final IDrawable icon;

    public CeSmithingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(Items.SMITHING_TABLE);
    }

    @Override
    public IRecipeType<CeSmithingEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.smithing");
    }

    @Override
    public int getWidth() {
        return 4 + 3 * SLOT_SIZE + 22 + SLOT_SIZE + 4;
    }

    @Override
    public int getHeight() {
        return SLOT_SIZE + 8;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CeSmithingEntry entry, IFocusGroup focuses) {
        addSlot(builder, RecipeIngredientRole.INPUT, 4, entry.template());
        addSlot(builder, RecipeIngredientRole.INPUT, 4 + SLOT_SIZE, entry.base());
        addSlot(builder, RecipeIngredientRole.INPUT, 4 + 2 * SLOT_SIZE, entry.addition());
        addSlot(builder, RecipeIngredientRole.OUTPUT, 4 + 3 * SLOT_SIZE + 22, entry.result());
    }

    private static void addSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, ItemStack stack) {
        IRecipeSlotBuilder slot = builder.addSlot(role, x, 4);
        if (role == RecipeIngredientRole.OUTPUT) {
            slot.setOutputSlotBackground();
        } else {
            slot.setStandardSlotBackground();
        }
        if (!stack.isEmpty()) {
            slot.add(stack);
        }
    }
}
