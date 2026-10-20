package com.createstats.tuner;

public record TunerStation(String id, String labelKey, String subLabel, String icon, int colour, int minRange,
                           int style, int dial) {
    public static final int STYLE_COLUMN = 1;

    public boolean shownAt(int range, String tuned) {
        return (minRange > 0 && minRange <= range) || id.equals(tuned);
    }

    public boolean column() {
        return (style & STYLE_COLUMN) != 0;
    }
}
