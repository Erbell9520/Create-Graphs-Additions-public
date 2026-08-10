package com.createstats.renderer;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NeedlePhysicsRegistry
{
    private static final Map<String, NeedlePhysics> needles;
    
    private NeedlePhysicsRegistry() {
    }
    
    public static NeedlePhysics getOrCreate(BlockPos pos, int slotIndex) {
        final String key = pos.asLong() + "_" + slotIndex;
        return NeedlePhysicsRegistry.needles.computeIfAbsent(key, k -> new NeedlePhysics());
    }
    
    public static void remove(BlockPos pos) {
        final String prefix = "" + pos.asLong();
        NeedlePhysicsRegistry.needles.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }
    
    public static void clear() {
        NeedlePhysicsRegistry.needles.clear();
    }
    
    static {
        needles = new HashMap<String, NeedlePhysics>();
    }
}
