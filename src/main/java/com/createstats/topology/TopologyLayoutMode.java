package com.createstats.topology;

public enum TopologyLayoutMode {
    ORGANIC("Organic"),
    SOURCE_FOCUS("Source Focus"),
    SOURCE_RINGS("Source Rings"),
    LINEAR("Linear");

    private final String label;

    TopologyLayoutMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    public TopologyLayoutMode next() {
        final TopologyLayoutMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public TopologyLayoutMode previous() {
        final TopologyLayoutMode[] values = values();
        return values[(this.ordinal() + values.length - 1) % values.length];
    }
}
