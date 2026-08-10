package com.createstats.efficiency;

public enum EfficiencyDimension
{
    UTILIZATION("Load", 0.3f, -12276993, "How well the network is using its capacity"), 
    HEADROOM("Reserve", 0.2f, -16711800, "How much breathing room remains before overstress"), 
    STABILITY("Rhythm", 0.25f, -12255284, "How steady the RPM stays while working"), 
    UPTIME("Reliability", 0.25f, -7798972, "How consistently the network avoids overstress");
    
    private final String displayName;
    private final float weight;
    private final int barColor;
    private final String description;
    
    private EfficiencyDimension(String displayName, float weight, int barColor, String description) {
        this.displayName = displayName;
        this.weight = weight;
        this.barColor = barColor;
        this.description = description;
    }
    
    public String getDisplayName() {
        return this.displayName;
    }
    
    public float getWeight() {
        return this.weight;
    }
    
    public int getBarColor() {
        return this.barColor;
    }
    
    public String getDescription() {
        return this.description;
    }
}
