package com.createstats.client.dynlight;

import com.createstats.dynlight.Light;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ItemLight {
    @Nullable
    Light lightFor(Entity carrier, ItemStack stack);
}
