package com.createstats.pa;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public final class PaAudioZone {
    private PaAudioZone() {
    }

    public static boolean covers(BlockPos source, BlockPos listener, int range, int halfHeight) {
        final int dy = listener.getY() - source.getY();
        if (dy > halfHeight || dy < -halfHeight) {
            return false;
        }
        final long dx = listener.getX() - source.getX();
        final long dz = listener.getZ() - source.getZ();
        return dx * dx + dz * dz <= (long) range * range;
    }

    public static boolean covers(BlockPos speaker, BlockPos listener, SpeakerKind kind) {
        return covers(speaker, listener, kind.range(), kind.halfHeight());
    }

    public static boolean covers(BlockPos source, Entity listener, int range, int halfHeight) {
        return covers(source, listener.blockPosition(), range, halfHeight);
    }
}
