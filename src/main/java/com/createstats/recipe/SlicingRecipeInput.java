package com.createstats.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record SlicingRecipeInput(ItemStack loaf) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No deck slot " + index);
        }
        return loaf;
    }

    @Override
    public int size() {
        return 1;
    }
}
