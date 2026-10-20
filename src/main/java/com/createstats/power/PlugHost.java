package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public interface PlugHost {
    List<BlockPos> pluggedAppliances(Level level);
}
