package com.createstats.compat.jei;

import com.createstats.blockentity.ArcFurnaceBE;
import com.createstats.recipe.ArcRecipe;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.createstats.registry.ModRecipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Locale;

public class ArcFurnaceCategory implements IRecipeCategory<RecipeHolder<ArcRecipe>> {
    public static final RecipeType<RecipeHolder<ArcRecipe>> TYPE =
            RecipeType.createFromVanilla(ModRecipes.ARC_TYPE.get());

    private static final int WIDTH = 170;
    private static final int HEIGHT = 73;

    private final IDrawable icon;
    private final IDrawable arrow;

    public ArcFurnaceCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.ARC_FURNACE.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<ArcRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.arc_smelting");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ArcRecipe> holder,
                          IFocusGroup focuses) {
        final ArcRecipe recipe = holder.value();

        builder.addSlot(RecipeIngredientRole.INPUT, 2, 4)
                .setStandardSlotBackground()
                .addItemStacks(counted(recipe.first(), recipe.firstCount()));
        builder.addSlot(RecipeIngredientRole.INPUT, 22, 4)
                .setStandardSlotBackground()
                .addItemStacks(counted(recipe.second(), recipe.secondCount()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 76, 4)
                .setOutputSlotBackground()
                .addItemStack(recipe.result())

                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("create_stats.jei.arc_smelting.clean_bonus",
                                "I".repeat(ArcFurnaceBE.MIN_TAP))));

        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 100, 4)
                .addItemStack(new ItemStack(ModItems.SLAG.get()))
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("create_stats.jei.arc_smelting.slag")));
    }

    @Override
    public void draw(RecipeHolder<ArcRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 50, 5);
        final int duration = holder.value().duration();
        final var font = Minecraft.getInstance().font;
        for (int tap = ArcFurnaceBE.MIN_TAP; tap <= ArcFurnaceBE.MAX_TAP; tap++) {
            final int ticks = Math.max(1, (duration + tap - 1) / tap);
            final int total = ArcFurnaceBE.drawFor(tap) * ticks;
            final String line = String.format(Locale.ROOT, "Tap %-3s  %5.1fs   %,d FE",
                    "I".repeat(tap), ticks / 20.0f, total);
            graphics.drawString(font, line, 2, 26 + (tap - 1) * 11, 0xFF404040, false);
        }

        graphics.drawString(font, Component.translatable("create_stats.jei.arc_smelting.clean_row",
                "I".repeat(ArcFurnaceBE.MIN_TAP)), 2, 26 + ArcFurnaceBE.MAX_TAP * 11, 0xFF404040, false);
    }

    private static java.util.List<ItemStack> counted(net.minecraft.world.item.crafting.Ingredient ingredient,
                                                     int count) {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            out.add(stack.copyWithCount(count));
        }
        return out;
    }
}
