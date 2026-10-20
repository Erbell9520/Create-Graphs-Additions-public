package com.createstats.stormcoil;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StormCoilAliases {
    public static final ResourceLocation GYROSCOPE_CORE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "gyroscope_core");
    public static final ResourceLocation STORM_COIL =
            ResourceLocation.fromNamespaceAndPath("create_stats", "storm_coil");

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "create_stats");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "create_stats");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "create_stats");

    static {
        BLOCKS.addAlias(GYROSCOPE_CORE, STORM_COIL);
        ITEMS.addAlias(GYROSCOPE_CORE, STORM_COIL);
        BLOCK_ENTITIES.addAlias(GYROSCOPE_CORE, STORM_COIL);
    }

    private StormCoilAliases() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
