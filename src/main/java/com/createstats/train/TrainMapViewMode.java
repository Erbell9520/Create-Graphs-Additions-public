package com.createstats.train;

public enum TrainMapViewMode
{
    ORIGINAL("Original"),
    REAL_WORLD("Real World"),
    SCHEMATIC("Schematic");
    
    private final String label;
    
    private TrainMapViewMode(String label) {
        this.label = label;
    }
    
    public String getLabel() {
        return this.label;
    }
    
    public TrainMapViewMode next() {
        final TrainMapViewMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
    
    public TrainMapViewMode previous() {
        final TrainMapViewMode[] values = values();
        return values[(this.ordinal() - 1 + values.length) % values.length];
    }
    
    public boolean defaultsToOctilinear() {
        return this == TrainMapViewMode.SCHEMATIC;
    }
}
