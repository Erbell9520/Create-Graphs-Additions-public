package com.createstats.client;

public record HudSourceWidgetReadout(Status status, String title, String label, String value, String detail, int color) {
    public enum Status {
        OK,
        SAMPLE,
        NO_SOURCE,
        NO_DATA
    }

    public boolean hasLiveData() {
        return this.status == Status.OK;
    }
}
