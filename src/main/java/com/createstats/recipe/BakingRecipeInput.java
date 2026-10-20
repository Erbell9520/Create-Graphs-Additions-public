package com.createstats.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record BakingRecipeInput(ItemStack tin) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No shelf slot " + index);
        }
        return tin;
    }

    @Override
    public int size() {
        return 1;
    }
}
