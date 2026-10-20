package com.createstats.compat.jei;

import com.createstats.blockentity.BulkWasherBE;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Locale;

public class BulkWasherCategory implements IRecipeCategory<RecipeHolder<SplashingRecipe>> {
    public static final RecipeType<RecipeHolder<SplashingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "drum_washing"));

    public static final RecipeType<RecipeHolder<SplashingRecipe>> FAN_WASHING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create", "fan_washing"));

    private static final int WIDTH = 170;
    private static final int HEIGHT = 86;

    private static final int SLOT_IN_X = 2;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 26;
    private static final int ARROW_Y = 5;

    private static final int OUT_X = 56;
    private static final int OUT_STEP = 19;
    private static final int OUT_COLUMNS = 6;

    private static final int FACT_Y = 25;

    private static final int HEAD_Y = 43;
    private static final int RULE_Y = 52;
    private static final int ROW_Y = 55;
    private static final int ROW_STEP = 10;

    private static final int RATE_RIGHT = 82;
    private static final int FE_RIGHT = 134;
    private static final int DRUM_RIGHT = 168;

    private static final int INK = 0xFF404040;
    private static final int DIM = 0xFF7A7A7A;

    private static final int POWER = 0xFF2A5E8C;
    private static final int WATER = 0xFF2F7FA0;

    private static final int GAIN = 0xFF2F6B2F;
    private static final int RULE = 0x30000000;

    private final IDrawable icon;

    private final IDrawableAnimated arrow;

    public BulkWasherCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.BULK_WASHER.get());
        this.arrow = guiHelper.createAnimatedRecipeArrow(BulkWasherBE.WashMode.HALF.cycleTicks);
    }

    @Override
    public RecipeType<RecipeHolder<SplashingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.drum_washing");
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
    public ResourceLocation getRegistryName(RecipeHolder<SplashingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<SplashingRecipe> holder,
                          IFocusGroup focuses) {
        final SplashingRecipe recipe = holder.value();
        final List<Ingredient> inputs = recipe.getIngredients();
        if (!inputs.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, SLOT_IN_X, SLOT_Y)
                    .setStandardSlotBackground()
                    .addIngredients(inputs.getFirst());
        }

        final List<ProcessingOutput> results = recipe.getRollableResults();
        for (int i = 0; i < results.size(); i++) {
            final ProcessingOutput output = results.get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT,
                            OUT_X + (i % OUT_COLUMNS) * OUT_STEP,
                            SLOT_Y + (i / OUT_COLUMNS) * OUT_STEP)
                    .setOutputSlotBackground()
                    .addItemStack(output.getStack())

                    .addRichTooltipCallback((view, tooltip) -> {
                        final float chance = output.getChance();
                        if (chance != 1.0f) {
                            tooltip.add(Component.translatable(
                                            "create_stats.jei.drum_washing.chance",
                                            chance < 0.01f ? "<1" : Integer.toString((int) (chance * 100)))
                                    .withStyle(ChatFormatting.GOLD));
                        }
                    });
        }
    }

    @Override
    public void draw(RecipeHolder<SplashingRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        final SplashingRecipe recipe = holder.value();
        final Font font = Minecraft.getInstance().font;

        arrow.draw(graphics, ARROW_X, ARROW_Y);

        if (recipe.getRollableResults().size() <= OUT_COLUMNS) {
            graphics.drawString(font, Component.translatable("create_stats.jei.drum_washing.water",
                    BulkWasherBE.WATER_PER_ITEM), 2, FACT_Y, WATER, false);
            final float yield = averageYield(recipe);
            final Component out = Component.translatable("create_stats.jei.drum_washing.yield",
                    String.format(Locale.ROOT, "%.2f", yield));
            graphics.drawString(font, out, DRUM_RIGHT - font.width(out), FACT_Y,
                    yield > 1.0f ? GAIN : INK, false);
        }

        graphics.drawString(font, Component.translatable("create_stats.jei.drum_washing.col_mode"),
                2, HEAD_Y, DIM, false);
        head(graphics, font, "create_stats.jei.drum_washing.col_rate", RATE_RIGHT);
        head(graphics, font, "create_stats.jei.drum_washing.col_fe", FE_RIGHT);
        head(graphics, font, "create_stats.jei.drum_washing.col_drum", DRUM_RIGHT);
        graphics.fill(2, RULE_Y, WIDTH - 2, RULE_Y + 1, RULE);

        final BulkWasherBE.WashMode[] modes = BulkWasherBE.WashMode.values();
        for (int i = 0; i < modes.length; i++) {
            row(graphics, font, modes[i], ROW_Y + i * ROW_STEP);
        }
    }

    private static void head(GuiGraphics graphics, Font font, String key, int right) {
        final Component text = Component.translatable(key);
        graphics.drawString(font, text, right - font.width(text), HEAD_Y, DIM, false);
    }

    private static void row(GuiGraphics graphics, Font font, BulkWasherBE.WashMode mode, int y) {
        graphics.drawString(font,
                Component.translatable("mode.create_stats.bulk_washer." + mode.key()), 2, y, INK,
                false);
        right(graphics, font, String.format(Locale.ROOT, "%.1f/s", mode.itemsPerSecond()),
                RATE_RIGHT, y, INK);
        right(graphics, font, String.format(Locale.ROOT, "%,d FE", mode.fePerItem()),
                FE_RIGHT, y, POWER);

        final int ticks = (BulkWasherBE.DRUM_CAPACITY * mode.cycleTicks + mode.batch - 1) / mode.batch;
        right(graphics, font, BulkWasherBE.clock(ticks), DRUM_RIGHT, y, INK);
    }

    private static void right(GuiGraphics graphics, Font font, String text, int rightEdge, int y,
                              int colour) {
        graphics.drawString(font, text, rightEdge - font.width(text), y, colour, false);
    }

    private static float averageYield(SplashingRecipe recipe) {
        float total = 0.0f;
        for (final ProcessingOutput output : recipe.getRollableResults()) {
            total += output.getStack().getCount() * output.getChance();
        }
        return total;
    }

    @Override
    public List<Component> getTooltipStrings(RecipeHolder<SplashingRecipe> holder,
                                             IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < FACT_Y) {
            return List.of();
        }
        final float yield = averageYield(holder.value());
        return List.of(
                Component.translatable("create_stats.jei.drum_washing.water_note",
                        BulkWasherBE.WATER_PER_ITEM,

                        String.format(Locale.ROOT, "%,d", BulkWasherBE.TANK_CAPACITY),
                        BulkWasherBE.TANK_CAPACITY / BulkWasherBE.WATER_PER_ITEM),
                Component.translatable(yield > 1.0f
                                ? "create_stats.jei.drum_washing.yield_gain_note"
                                : "create_stats.jei.drum_washing.yield_note",
                        BulkWasherBE.DRUM_CAPACITY),
                Component.translatable("create_stats.jei.drum_washing.dial_note"),
                Component.translatable("create_stats.jei.drum_washing.draw_note",
                        BulkWasherBE.WashMode.ECO.drawFe, BulkWasherBE.WashMode.FULL.drawFe,
                        BulkWasherBE.DRAW_IDLE));
    }
}
