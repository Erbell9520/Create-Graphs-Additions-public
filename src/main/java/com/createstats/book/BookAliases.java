package com.createstats.book;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BookAliases {
    public static final ResourceLocation FACTORY_MANUAL =
            ResourceLocation.fromNamespaceAndPath("create_stats", "factory_manual");
    public static final ResourceLocation STUDENTS_MANUAL =
            ResourceLocation.fromNamespaceAndPath("create_stats", "students_manual");

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "create_stats");

    static {
        ITEMS.addAlias(FACTORY_MANUAL, STUDENTS_MANUAL);
    }

    private BookAliases() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
