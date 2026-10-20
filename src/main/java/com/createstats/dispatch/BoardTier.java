package com.createstats.dispatch;

public enum BoardTier {
    MINI(1, 4, true, false, false),

    COMPACT(2, 6, true, true, false),

    STANDARD(4, 10, true, true, true),

    RICH(9, 16, true, true, true),

    FULL(16, 24, true, true, true);

    private final int minimumArea;
    private final int baseRowCapacity;
    private final boolean showsDispatchRows;
    private final boolean showsCargoSummary;
    private final boolean showsHistory;

    BoardTier(int minimumArea, int baseRowCapacity, boolean showsDispatchRows,
              boolean showsCargoSummary, boolean showsHistory) {
        this.minimumArea = minimumArea;
        this.baseRowCapacity = baseRowCapacity;
        this.showsDispatchRows = showsDispatchRows;
        this.showsCargoSummary = showsCargoSummary;
        this.showsHistory = showsHistory;
    }

    public static BoardTier fromSize(int width, int height) {
        int area = Math.max(1, width) * Math.max(1, height);
        if (area >= 16) return FULL;
        if (area >= 9) return RICH;
        if (area >= 4) return STANDARD;
        if (area >= 2) return COMPACT;
        return MINI;
    }

    public int getMinimumArea() {
        return this.minimumArea;
    }

    public int getBaseRowCapacity() {
        return this.baseRowCapacity;
    }

    public boolean showsDispatchRows() {
        return this.showsDispatchRows;
    }

    public boolean showsCargoSummary() {
        return this.showsCargoSummary;
    }

    public boolean showsHistory() {
        return this.showsHistory;
    }

    public boolean showsTrainRoster() {
        return this == RICH || this == FULL;
    }

    public boolean showsVisitTimeline() {
        return this == RICH || this == FULL;
    }

    public boolean showsRowDetail() {
        return this.ordinal() >= COMPACT.ordinal();
    }

    public boolean showsExceptionStrip() {
        return this.ordinal() >= COMPACT.ordinal();
    }
}
