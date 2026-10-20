package com.createstats.display;

public enum StatsColorScheme
{
    DARK("Dark (Oscilloscope)", -15066578, -16711800, -12303258, -5588020),
    LIGHT("Light (Graph Paper)", -657936, -14531414, -3355461, -13421773),
    RETRO_CRT("Retro CRT", -16119286, -22016, -14540288, -7838208);

    private final String displayName;
    private final int backgroundColor;
    private final int lineColor;
    private final int gridColor;
    private final int textColor;

    private StatsColorScheme(String displayName, int backgroundColor, int lineColor, int gridColor, int textColor) {
        this.displayName = displayName;
        this.backgroundColor = backgroundColor;
        this.lineColor = lineColor;
        this.gridColor = gridColor;
        this.textColor = textColor;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getBackgroundColor() {
        return this.backgroundColor;
    }

    public int getLineColor() {
        return this.lineColor;
    }

    public int getGridColor() {
        return this.gridColor;
    }

    public int getTextColor() {
        return this.textColor;
    }
}
