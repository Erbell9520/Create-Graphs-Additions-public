package com.createstats.registry;

import com.createstats.CreateStats;
import com.createstats.recipe.ArcRecipe;
import com.createstats.recipe.BakingRecipe;
import com.createstats.recipe.SlicingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreateStats.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateStats.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ArcRecipe>> ARC_TYPE =
            TYPES.register("arc_smelting", name -> new RecipeType<ArcRecipe>() {
                @Override
                public String toString() {
                    return name.toString();
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, ArcRecipe.Serializer> ARC_SERIALIZER =
            SERIALIZERS.register("arc_smelting", () -> new ArcRecipe.Serializer());

    public static final DeferredHolder<RecipeType<?>, RecipeType<SlicingRecipe>> SLICING_TYPE =
            TYPES.register("slicing", name -> new RecipeType<SlicingRecipe>() {
                @Override
                public String toString() {
                    return name.toString();
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, SlicingRecipe.Serializer>
            SLICING_SERIALIZER = SERIALIZERS.register("slicing", () -> new SlicingRecipe.Serializer());

    public static final DeferredHolder<RecipeType<?>, RecipeType<BakingRecipe>> BAKING_TYPE =
            TYPES.register("baking", name -> new RecipeType<BakingRecipe>() {
                @Override
                public String toString() {
                    return name.toString();
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, BakingRecipe.Serializer>
            BAKING_SERIALIZER = SERIALIZERS.register("baking", () -> new BakingRecipe.Serializer());

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }

    private ModRecipes() {
    }
}
