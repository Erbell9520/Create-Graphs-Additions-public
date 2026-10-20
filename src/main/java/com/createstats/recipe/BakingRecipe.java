package com.createstats.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

// the only process that hands the ingredient back, so not smelting
public record BakingRecipe(Ingredient tin, ItemStack result, Optional<ItemStack> container,
                           int bakeTime) implements Recipe<BakingRecipeInput> {
    public static final int DEFAULT_BAKE_TIME = 300;

    public static final MapCodec<BakingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("tin").forGetter(BakingRecipe::tin),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(BakingRecipe::result),
            ItemStack.STRICT_CODEC.optionalFieldOf("container").forGetter(BakingRecipe::container),
            Codec.intRange(1, 72_000).optionalFieldOf("bake_time", DEFAULT_BAKE_TIME)
                    .forGetter(BakingRecipe::bakeTime)
    ).apply(inst, BakingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BakingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, BakingRecipe::tin,
                    ItemStack.STREAM_CODEC, BakingRecipe::result,
                    ByteBufCodecs.optional(ItemStack.STREAM_CODEC), BakingRecipe::container,
                    ByteBufCodecs.VAR_INT, BakingRecipe::bakeTime,
                    BakingRecipe::new);

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && tin.test(stack);
    }

    public ItemStack bake() {
        return result.copy();
    }

    public ItemStack emptied() {
        return container.map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean matches(BakingRecipeInput in, Level level) {
        return accepts(in.tin());
    }

    @Override
    public ItemStack assemble(BakingRecipeInput in, HolderLookup.Provider registries) {
        return bake();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        final NonNullList<Ingredient> list = NonNullList.create();
        list.add(tin);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.createstats.registry.ModRecipes.BAKING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return com.createstats.registry.ModRecipes.BAKING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<BakingRecipe> {
        @Override
        public MapCodec<BakingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BakingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
