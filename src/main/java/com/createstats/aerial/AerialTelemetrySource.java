package com.createstats.aerial;

public interface AerialTelemetrySource {
    int FEED_TICKS = 4;

    AerialSnapshot aerialSnapshot();

    default boolean onAerialCommand(AerialCommand command) {
        return false;
    }
}
