package com.createstats.stormcoil;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StormCoilIndex {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    private StormCoilIndex() {
    }

    public static void add(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
        }
    }

    public static void remove(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide()) {
            final Set<BlockPos> set = LOADED.get(level.dimension());
            if (set != null) {
                set.remove(pos);
            }
        }
    }

    public static List<BlockPos> in(Level level) {
        final Set<BlockPos> set = LOADED.get(level.dimension());
        return set == null ? List.of() : new ArrayList<>(set);
    }
}
