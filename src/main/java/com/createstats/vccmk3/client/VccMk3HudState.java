package com.createstats.vccmk3.client;

import com.createstats.vccmk3.network.VccMk3HudPacket;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public final class VccMk3HudState {
    private static final long STALE_AFTER_MS = 2000L;

    private static final long HIDE_AFTER_MS = 8000L;

    @Nullable private static volatile VccMk3HudPacket latest;
    private static volatile long receivedAtMs;

    @Nullable private static volatile BlockPos pinned;

    private VccMk3HudState() {}

    public static void accept(@Nullable VccMk3HudPacket packet) {
        latest = packet;
        receivedAtMs = System.currentTimeMillis();
    }

    @Nullable
    public static VccMk3HudPacket packet() {
        VccMk3HudPacket packet = latest;
        if (packet == null || pinned == null) return null;
        if (System.currentTimeMillis() - receivedAtMs > HIDE_AFTER_MS) return null;
        return packet;
    }

    public static boolean isStale() {
        return latest != null && pinned != null
                && System.currentTimeMillis() - receivedAtMs > STALE_AFTER_MS;
    }

    @Nullable
    public static BlockPos pinned() {
        return pinned;
    }

    public static boolean isPinned(BlockPos pos) {
        BlockPos current = pinned;
        return current != null && current.equals(pos);
    }

    public static void setPinned(@Nullable BlockPos pos) {
        pinned = pos == null ? null : pos.immutable();
        if (pos == null) {
            latest = null;
            receivedAtMs = 0L;
        }
    }

    public static void clear() {
        latest = null;
        pinned = null;
        receivedAtMs = 0L;
    }
}
