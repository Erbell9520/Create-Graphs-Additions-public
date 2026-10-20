package com.createstats.client.train;

import com.createstats.network.TrainChargeSyncPacket;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientTrainCharge {
    private static final long STALE_MILLIS = 7_000L;

    private record Held(TrainChargeSyncPacket.Summary summary, long receivedAt) {
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();

    private ClientTrainCharge() {
    }

    public static void accept(TrainChargeSyncPacket.Summary summary) {
        HELD.put(summary.train(), new Held(summary, System.currentTimeMillis()));
    }

    @Nullable
    public static TrainChargeSyncPacket.Summary get(UUID train) {
        final Held held = HELD.get(train);
        if (held == null || System.currentTimeMillis() - held.receivedAt() > STALE_MILLIS) {
            return null;
        }
        return held.summary();
    }

    public static void clear() {
        HELD.clear();
    }
}
