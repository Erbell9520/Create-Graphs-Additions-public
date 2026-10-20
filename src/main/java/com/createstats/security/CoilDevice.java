package com.createstats.security;

import com.createstats.blockentity.HomeSecurityPanelBE;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface CoilDevice extends GovernedDevice {
    CoilReading coilReading();

    @Nullable
    HomeSecurityPanelBE securityPanel();

    boolean clears(Player player);
}
