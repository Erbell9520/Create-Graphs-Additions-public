package com.createstats.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab createStats$getSelectedTab() {
        throw new UnsupportedOperationException("Mixin not applied");
    }

    @Accessor("scrollOffs")
    float createStats$getScrollOffs();

    @Accessor("scrollOffs")
    void createStats$setScrollOffs(float value);
}
