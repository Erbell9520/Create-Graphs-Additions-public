package com.createstats.vccmk3.client;

import com.createstats.vccmk3.network.VccMk3SnapshotPacket;

import javax.annotation.Nullable;

public final class VccMk3ClientState {
    private static final long STALE_AFTER_MS = 1500L;

    @Nullable private static volatile VccMk3SnapshotPacket latest;
    private static volatile long receivedAtMs;

    private VccMk3ClientState() {}

    public static void accept(@Nullable VccMk3SnapshotPacket packet) {
        latest = packet;
        receivedAtMs = System.currentTimeMillis();
    }

    @Nullable
    public static VccMk3SnapshotPacket snapshot() {
        VccMk3SnapshotPacket packet = latest;
        if (packet == null) return null;
        return isStale() ? null : packet;
    }

    public static boolean isStale() {
        return latest != null && System.currentTimeMillis() - receivedAtMs > STALE_AFTER_MS;
    }

    public static long ageMs() {
        return latest == null ? -1L : System.currentTimeMillis() - receivedAtMs;
    }

    public static void clear() {
        latest = null;
        receivedAtMs = 0L;
    }
}
