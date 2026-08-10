package com.createstats.manual;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record FactoryManualPage(String id, Supplier<ItemStack> iconSupplier, Supplier<Component> titleSupplier, String bodyKey) {

    public ItemStack iconStack() {
        return iconSupplier.get().copy();
    }

    public Component title() {
        return titleSupplier.get();
    }

    public Component body() {
        return Component.translatable(bodyKey);
    }
}
