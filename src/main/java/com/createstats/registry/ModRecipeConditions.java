package com.createstats.registry;

import com.createstats.CreateStats;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModRecipeConditions {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, CreateStats.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ExperimentalRecipeCondition>>
            EXPERIMENTAL_BLOCKS_ENABLED =
            CONDITION_CODECS.register("experimental_blocks_enabled", () -> ExperimentalRecipeCondition.CODEC);

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
