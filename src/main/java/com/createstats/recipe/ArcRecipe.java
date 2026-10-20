package com.createstats.recipe;

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
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

public record ArcRecipe(Ingredient first, int firstCount, Ingredient second, int secondCount,
                        ItemStack result, int duration) implements Recipe<ArcRecipeInput> {
    public static final int DEFAULT_DURATION = 200;

    public static final MapCodec<ArcRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("first").forGetter(ArcRecipe::first),
            Codec.intRange(1, 64).optionalFieldOf("first_count", 1).forGetter(ArcRecipe::firstCount),
            Ingredient.CODEC_NONEMPTY.fieldOf("second").forGetter(ArcRecipe::second),
            Codec.intRange(1, 64).optionalFieldOf("second_count", 1).forGetter(ArcRecipe::secondCount),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ArcRecipe::result),
            Codec.intRange(1, 72000).optionalFieldOf("duration", DEFAULT_DURATION)
                    .forGetter(ArcRecipe::duration)
    ).apply(inst, ArcRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, ArcRecipe::first,
                    ByteBufCodecs.VAR_INT, ArcRecipe::firstCount,
                    Ingredient.CONTENTS_STREAM_CODEC, ArcRecipe::second,
                    ByteBufCodecs.VAR_INT, ArcRecipe::secondCount,
                    ItemStack.STREAM_CODEC, ArcRecipe::result,
                    ByteBufCodecs.VAR_INT, ArcRecipe::duration,
                    ArcRecipe::new);

    @Nullable
    public int[] costs(ArcRecipeInput in) {
        if (holds(first, firstCount, in.a()) && holds(second, secondCount, in.b())) {
            return new int[]{firstCount, secondCount};
        }
        if (holds(first, firstCount, in.b()) && holds(second, secondCount, in.a())) {
            return new int[]{secondCount, firstCount};
        }
        return null;
    }

    private static boolean holds(Ingredient what, int howMany, ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() >= howMany && what.test(stack);
    }

    public boolean acceptsAsCharge(ItemStack stack) {
        return first.test(stack) || second.test(stack);
    }

    @Override
    public boolean matches(ArcRecipeInput in, Level level) {
        return costs(in) != null;
    }

    @Override
    public ItemStack assemble(ArcRecipeInput in, HolderLookup.Provider registries) {
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
        list.add(first);
        list.add(second);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.createstats.registry.ModRecipes.ARC_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return com.createstats.registry.ModRecipes.ARC_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<ArcRecipe> {
        @Override
        public MapCodec<ArcRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ArcRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
