package com.createstats.compat.jei;

import com.createstats.blockentity.AirFryerBE;
import com.createstats.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AirFryerCategory implements IRecipeCategory<RecipeHolder<SmeltingRecipe>> {
    public static final RecipeType<RecipeHolder<SmeltingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "air_frying"));

    private static final int WIDTH = 170;
    private static final int HEIGHT = 54;

    private static final int SLOT_IN_X = 2;
    private static final int SLOT_OUT_X = 58;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 26;
    private static final int ARROW_Y = 5;

    private static final int FIG_X = 84;
    private static final int LANE_Y = 4;
    private static final int RATIO_Y = 15;

    private static final int ROW_ONE_Y = 30;
    private static final int ROW_BASKET_Y = 41;

    private static final int TIME_RIGHT = 100;
    private static final int FE_RIGHT = 166;

    private static final int INK = 0xFF404040;
    private static final int DIM = 0xFF7A7A7A;

    private static final int POWER = 0xFF2A5E8C;
    private static final int FOOD = 0xFF2F6B2F;
    private static final int BULK = 0xFF8A5A1E;

    private final IDrawable icon;

    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();

    private final IGuiHelper guiHelper;

    public AirFryerCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.AIR_FRYER.get());
    }

    @Override
    public RecipeType<RecipeHolder<SmeltingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.air_frying");
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
    public ResourceLocation getRegistryName(RecipeHolder<SmeltingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<SmeltingRecipe> holder,
                          IFocusGroup focuses) {
        final SmeltingRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.INPUT, SLOT_IN_X, SLOT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.getIngredients().getFirst());
        builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_OUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(access()));
    }

    @Override
    public void draw(RecipeHolder<SmeltingRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        final SmeltingRecipe recipe = holder.value();
        final ItemStack result = recipe.getResultItem(access());
        final boolean food = AirFryerBE.isFood(result);
        final int ticks = AirFryerBE.cookTicksOf(recipe, result);

        arrows.computeIfAbsent(ticks, guiHelper::createAnimatedRecipeArrow)
                .draw(graphics, ARROW_X, ARROW_Y);

        final var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable(food
                        ? "create_stats.jei.air_frying.lane_food"
                        : "create_stats.jei.air_frying.lane_bulk"),
                FIG_X, LANE_Y, food ? FOOD : BULK, false);

        graphics.drawString(font, Component.translatable("create_stats.jei.air_frying.of_furnace",
                        String.format(Locale.ROOT, "%.2f", food
                                ? AirFryerBE.FOOD_SPEED : AirFryerBE.BULK_SPEED)),
                FIG_X, RATIO_Y, DIM, false);

        row(graphics, ROW_ONE_Y, Component.translatable("create_stats.jei.air_frying.one"),
                ticks, ticks * AirFryerBE.DRAW_COOK);
        row(graphics, ROW_BASKET_Y, Component.translatable("create_stats.jei.air_frying.basket",
                        AirFryerBE.BASKET_CAPACITY),
                ticks * AirFryerBE.BASKET_CAPACITY,
                ticks * AirFryerBE.BASKET_CAPACITY * AirFryerBE.DRAW_COOK);
    }

    private void row(GuiGraphics graphics, int y, Component label, int ticks, long fe) {
        final var font = Minecraft.getInstance().font;
        graphics.drawString(font, label, 2, y, INK, false);
        final String time = String.format(Locale.ROOT, "%.1f s", ticks / 20.0f);
        graphics.drawString(font, time, TIME_RIGHT - font.width(time), y, INK, false);
        final String bill = String.format(Locale.ROOT, "%,d FE", fe);
        graphics.drawString(font, bill, FE_RIGHT - font.width(bill), y, POWER, false);
    }

    @Override
    public List<Component> getTooltipStrings(RecipeHolder<SmeltingRecipe> holder,
                                             IRecipeSlotsView slots, double mouseX, double mouseY) {
        final boolean overFigures = mouseX >= FIG_X && mouseY >= LANE_Y && mouseY < ROW_ONE_Y;
        final boolean overRows = mouseY >= ROW_ONE_Y && mouseY <= ROW_BASKET_Y + 9;
        if (!overFigures && !overRows) {
            return List.of();
        }
        final boolean food = AirFryerBE.isFood(holder.value().getResultItem(access()));
        return List.of(
                Component.translatable(food
                        ? "create_stats.jei.air_frying.lane_food_note"
                        : "create_stats.jei.air_frying.lane_bulk_note"),
                Component.translatable("create_stats.jei.air_frying.basket_note",
                        AirFryerBE.BASKET_CAPACITY),
                Component.translatable("create_stats.jei.air_frying.draw_note",
                        AirFryerBE.DRAW_COOK, AirFryerBE.DRAW_IDLE));
    }

    private static RegistryAccess access() {
        final net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        return level == null ? RegistryAccess.EMPTY : level.registryAccess();
    }
}
