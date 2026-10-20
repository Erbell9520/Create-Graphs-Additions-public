package com.createstats.ground;

public interface GroundTelemetrySource {
    int FEED_TICKS = 4;

    GroundSnapshot groundSnapshot();

    default boolean onGroundCommand(GroundCommand command) {
        return false;
    }
}
