package com.createstats.registry;

import com.createstats.ModConfiguration;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

public record ExperimentalRecipeCondition() implements ICondition {
    public static final ExperimentalRecipeCondition INSTANCE = new ExperimentalRecipeCondition();
    public static final MapCodec<ExperimentalRecipeCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        try {
            return ModConfiguration.COMMON.showExperimentalBlocks.get();
        } catch (RuntimeException configNotLoadedYet) {
            return false;
        }
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
