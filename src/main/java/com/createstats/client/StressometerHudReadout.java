package com.createstats.client;

public record StressometerHudReadout(
        Status status,
        String sourceLabel,
        float stressUsed,
        float stressCapacity,
        float stressPercent,
        float remainingStress,
        float rpm,
        boolean overstressed,
        int animationSlot
) {
    public static StressometerHudReadout sample() {
        return new StressometerHudReadout(Status.SAMPLE, "Sample stress network", 8192.0f, 16384.0f, 50.0f, 8192.0f, 128.0f, false, 0);
    }

    public boolean hasLiveData() {
        return this.status == Status.OK;
    }

    public enum Status {
        OK,
        SAMPLE,
        NO_SOURCE,
        NO_DATA,
        SOURCE_MISSING
    }
}
