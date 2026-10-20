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

public record SlicingRecipe(Ingredient loaf, ItemStack result, Optional<Integer> slices)
        implements Recipe<SlicingRecipeInput> {
    public static final MapCodec<SlicingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("loaf").forGetter(SlicingRecipe::loaf),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(SlicingRecipe::result),
            Codec.intRange(1, 64).optionalFieldOf("slices").forGetter(SlicingRecipe::slices)
    ).apply(inst, SlicingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlicingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, SlicingRecipe::loaf,
                    ItemStack.STREAM_CODEC, SlicingRecipe::result,
                    ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SlicingRecipe::slices,
                    SlicingRecipe::new);

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && loaf.test(stack);
    }

    public ItemStack yield(int strokes) {
        final int pieces = slices.orElse(strokes);
        return result.copyWithCount(Math.max(1, result.getCount() * pieces));
    }

    @Override
    public boolean matches(SlicingRecipeInput in, Level level) {
        return accepts(in.loaf());
    }

    @Override
    public ItemStack assemble(SlicingRecipeInput in, HolderLookup.Provider registries) {
        return result.copy();
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
        list.add(loaf);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.createstats.registry.ModRecipes.SLICING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return com.createstats.registry.ModRecipes.SLICING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<SlicingRecipe> {
        @Override
        public MapCodec<SlicingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SlicingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
