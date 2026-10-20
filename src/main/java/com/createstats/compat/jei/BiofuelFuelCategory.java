package com.createstats.compat.jei;

import com.createstats.power.BiofuelFuel;
import com.createstats.power.BiofuelPages;
import com.createstats.power.BiofuelValue;
import com.createstats.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BiofuelFuelCategory implements IRecipeCategory<BiofuelFuelCategory.FuelPage> {
    public record FuelPage(int burnTicks, List<ItemStack> items) {
    }

    public static final RecipeType<FuelPage> TYPE =
            RecipeType.create("create_stats", "biofuel_fuel", FuelPage.class);

    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    public static final int PER_PAGE = COLUMNS * ROWS;

    private static final int WIDTH = 170;
    private static final int SLOT = 18;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 26;
    private static final int HEIGHT = GRID_Y + ROWS * SLOT + 2;

    private static final int INK = 0xFF404040;

    private static final int POWER = 0xFF2A5E8C;

    private final IDrawable icon;

    public BiofuelFuelCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.BIOFUEL_BURNER.get());
    }

    public static List<FuelPage> pagesFor(Iterable<ItemStack> everything) {
        final Map<Item, ItemStack> distinct = new LinkedHashMap<>();
        for (ItemStack stack : everything) {
            if (!stack.isEmpty() && !distinct.containsKey(stack.getItem())) {
                final ItemStack fresh = stack.getItem().getDefaultInstance();
                if (BiofuelFuel.accepts(fresh)) {
                    distinct.put(stack.getItem(), fresh);
                }
            }
        }
        final List<FuelPage> pages = new ArrayList<>();
        for (BiofuelPages.Page<ItemStack> page : BiofuelPages.build(distinct.values(),
                BiofuelFuel::burnTicksOf,
                Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem())),
                PER_PAGE)) {
            pages.add(new FuelPage(page.burnTicks(), page.items()));
        }
        return pages;
    }

    @Override
    public RecipeType<FuelPage> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.biofuel_fuel");
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
    public ResourceLocation getRegistryName(FuelPage page) {
        return ResourceLocation.fromNamespaceAndPath("create_stats",
                "biofuel_fuel/" + page.burnTicks() + "_"
                        + BuiltInRegistries.ITEM.getKey(page.items().getFirst().getItem()).toString()
                        .replace(':', '_').replace('/', '_'));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FuelPage page, IFocusGroup focuses) {
        final List<ItemStack> items = page.items();
        for (int i = 0; i < items.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT,
                            GRID_X + (i % COLUMNS) * SLOT + 1, GRID_Y + (i / COLUMNS) * SLOT + 1)
                    .setStandardSlotBackground()
                    .addItemStack(items.get(i));
        }
    }

    @Override
    public void draw(FuelPage page, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        final Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("create_stats.jei.biofuel_fuel.burns",
                BiofuelValue.duration(page.burnTicks())), 2, 1, INK, false);
        graphics.drawString(font, Component.translatable("create_stats.jei.biofuel_fuel.fe",
                String.format(Locale.ROOT, "%,d", BiofuelValue.totalFe(page.burnTicks())),
                BiofuelValue.OUTPUT_FE_PER_TICK), 2, 12, POWER, false);
    }
}
