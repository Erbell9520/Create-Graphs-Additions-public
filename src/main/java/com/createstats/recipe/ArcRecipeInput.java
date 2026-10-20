package com.createstats.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record ArcRecipeInput(ItemStack a, ItemStack b) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> a;
            case 1 -> b;
            default -> throw new IllegalArgumentException("No charge slot " + index);
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
