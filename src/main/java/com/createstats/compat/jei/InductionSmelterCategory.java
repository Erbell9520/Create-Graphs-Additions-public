package com.createstats.compat.jei;

import com.createstats.blockentity.InductionSmelterBE;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InductionSmelterCategory implements IRecipeCategory<RecipeHolder<AbstractCookingRecipe>> {
    public static final RecipeType<RecipeHolder<AbstractCookingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "induction_smelting"));

    private static final int WIDTH = 170;
    private static final int HEIGHT = 52;

    private static final int SLOT_IN_X = 2;
    private static final int SLOT_OUT_X = 58;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 26;
    private static final int ARROW_Y = 5;

    private static final int FIG_X = 84;
    private static final int FIG_TIME_Y = 4;
    private static final int FIG_FE_Y = 15;
    private static final int FIG_NOTE_Y = 26;
    private static final int LANE_Y = 41;

    private static final int INK = 0xFF404040;
    private static final int DIM = 0xFF7A7A7A;

    private static final int POWER = 0xFF2A5E8C;
    private static final int BLAST = 0xFFA0561E;
    private static final int SMELT = 0xFF5E5E5E;

    private final IGuiHelper guiHelper;
    private final IDrawable icon;

    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();

    public InductionSmelterCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.INDUCTION_SMELTER.get());
    }

    @Override
    public RecipeType<RecipeHolder<AbstractCookingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.induction_smelting");
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
    public ResourceLocation getRegistryName(RecipeHolder<AbstractCookingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AbstractCookingRecipe> holder,
                          IFocusGroup focuses) {
        final AbstractCookingRecipe recipe = holder.value();
        final net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        final net.minecraft.core.RegistryAccess access =
                level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        builder.addSlot(RecipeIngredientRole.INPUT, SLOT_IN_X, SLOT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.getIngredients().getFirst());
        builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_OUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(access));
    }

    @Override
    public void draw(RecipeHolder<AbstractCookingRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        final AbstractCookingRecipe recipe = holder.value();
        final boolean blasting = recipe instanceof BlastingRecipe;
        final int ticks = InductionSmelterBE.durationOf(recipe);

        arrows.computeIfAbsent(ticks, guiHelper::createAnimatedRecipeArrow)
                .draw(graphics, ARROW_X, ARROW_Y);

        final var font = Minecraft.getInstance().font;
        graphics.drawString(font, String.format(Locale.ROOT, "%.1f s", ticks / 20.0f),
                FIG_X, FIG_TIME_Y, INK, false);
        graphics.drawString(font, String.format(Locale.ROOT, "%,d FE",
                ticks * InductionSmelterBE.DRAW_WORK), FIG_X, FIG_FE_Y, POWER, false);

        graphics.drawString(font,
                Component.translatable("create_stats.jei.induction_smelting.at_full"),
                FIG_X, FIG_NOTE_Y, DIM, false);

        graphics.drawString(font, Component.translatable(blasting
                        ? "create_stats.jei.induction_smelting.lane_blast"
                        : "create_stats.jei.induction_smelting.lane_smelt"),
                2, LANE_Y, blasting ? BLAST : SMELT, false);
    }

    @Override
    public List<Component> getTooltipStrings(RecipeHolder<AbstractCookingRecipe> holder,
                                             IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < FIG_TIME_Y || mouseY > LANE_Y + 9
                || (mouseY < LANE_Y - 2 && mouseX < FIG_X)) {
            return List.of();
        }
        final boolean blasting = holder.value() instanceof BlastingRecipe;
        return List.of(
                Component.translatable(blasting
                        ? "create_stats.jei.induction_smelting.lane_blast_note"
                        : "create_stats.jei.induction_smelting.lane_smelt_note"),
                Component.translatable("create_stats.jei.induction_smelting.cold_start",
                        InductionSmelterBE.HEAT_MAX * InductionSmelterBE.DRAW_WORK,
                        InductionSmelterBE.HEAT_MAX / 20),
                Component.translatable("create_stats.jei.induction_smelting.hold",
                        InductionSmelterBE.DRAW_IDLE));
    }
}
