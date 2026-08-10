package com.createstats.dispatch;

/**
 * Information-density tier derived from the assembled dispatch wall size.
 * Determines which regions, row counts, and widget sets the renderer and UI should show.
 */
public enum BoardTier {

    /** 1x1, minimal status tile. Station name, linked/unlinked, train presence, top rows, progress. */
    MINI(1, 4, true, false, false),

    /** 1x2 or 2x1, compact strip. Header + summary + rows with progress. */
    COMPACT(2, 6, true, true, false),

    /** 2x2, first real wall. Strong header, berth state, rows, cargo, warnings. */
    STANDARD(4, 10, true, true, true),

    /** 3x3, rich operations wall. Full rows, cargo, history, visits, roster. */
    RICH(9, 16, true, true, true),

    /**
     * 4x4+, full station intelligence wall. Maximum rows, cargo blocks, train roster,
     * visit history timeline, station-specific train metadata, everything.
     */
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

    /** [1] */
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

    /**
     * Base row capacity before extra-area scaling. The renderer uses this as a minimum;
     * larger walls within the same tier may add extra rows via
     * {@link BoardLayoutSpec#getDispatchRowCapacity()}.
     */
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

    /** Whether this tier should include the station train roster panel. */
    public boolean showsTrainRoster() {
        return this == RICH || this == FULL;
    }

    /** Whether this tier should include the visit history timeline. */
    public boolean showsVisitTimeline() {
        return this == RICH || this == FULL;
    }

    /** Whether this tier should show per-row detail (reset badge, cycle info). */
    public boolean showsRowDetail() {
        return this.ordinal() >= COMPACT.ordinal();
    }

    /** Whether the warning/exception strip is shown. */
    public boolean showsExceptionStrip() {
        return this.ordinal() >= COMPACT.ordinal();
    }
}
