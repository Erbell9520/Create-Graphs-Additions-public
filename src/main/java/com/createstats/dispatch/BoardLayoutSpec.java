package com.createstats.dispatch;

/** [1] */
public class BoardLayoutSpec {

    private final int width;
    private final int height;
    private final BoardTier tier;

    private final int dispatchRowCapacity;
    private final int historyEntryCapacity;
    private final int rosterCapacity;
    private final int cargoSummarySlots;

    private BoardLayoutSpec(int width, int height, BoardTier tier,
                            int dispatchRowCapacity, int historyEntryCapacity,
                            int rosterCapacity, int cargoSummarySlots) {
        this.width = width;
        this.height = height;
        this.tier = tier;
        this.dispatchRowCapacity = dispatchRowCapacity;
        this.historyEntryCapacity = historyEntryCapacity;
        this.rosterCapacity = rosterCapacity;
        this.cargoSummarySlots = cargoSummarySlots;
    }

    /**
     * Computes a layout spec for the given wall dimensions.
     * Row and slot counts scale with area beyond the tier's base threshold.
     */
    public static BoardLayoutSpec compute(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        BoardTier tier = BoardTier.fromSize(w, h);
        int area = w * h;

        int rows;
        int history;
        int roster;
        int cargoSlots;

        switch (tier) {
            case MINI:
                // 1x1: pack as much as 192px allows, header + 4 rows + bar
                rows = 4;
                history = 0;
                roster = 0;
                cargoSlots = 0;
                break;

            case COMPACT:
                // 1x2 / 2x1: header + summary + 6-8 rows
                rows = 6 + Math.max(0, area - 2);
                history = 0;
                roster = 0;
                cargoSlots = 2;
                break;

            case STANDARD:
                // 2x2: first real wall, header + summary + 10+ rows + cargo
                rows = 10 + Math.max(0, area - 4) * 2;
                history = 3;
                roster = 0;
                cargoSlots = 4;
                break;

            case RICH:
                // 3x3: full operations wall, rows + cargo + history + visits
                rows = 16 + Math.max(0, area - 9) * 2;
                history = 6 + Math.max(0, area - 9);
                roster = 3;
                cargoSlots = 8;
                break;

            case FULL:
            default:
                // 4x4+: full station intelligence, everything maxed
                int extraArea = Math.max(0, area - 16);
                rows = 24 + extraArea * 2;
                history = 10 + extraArea;
                roster = 6 + extraArea;
                cargoSlots = 12 + extraArea;
                break;
        }

        return new BoardLayoutSpec(w, h, tier, rows, history, roster, cargoSlots);
    }

    // getters

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public BoardTier getTier() {
        return this.tier;
    }

    /** Maximum dispatch rows that fit in the main row region. */
    public int getDispatchRowCapacity() {
        return this.dispatchRowCapacity;
    }

    /** Maximum history/event entries visible in the history region. */
    public int getHistoryEntryCapacity() {
        return this.historyEntryCapacity;
    }

    /** Maximum train roster entries visible (FULL tier only). */
    public int getRosterCapacity() {
        return this.rosterCapacity;
    }

    /** Number of cargo summary slots (top-N items/fluids to show). */
    public int getCargoSummarySlots() {
        return this.cargoSummarySlots;
    }

    /** Whether the layout has room for a dedicated header region. */
    public boolean hasHeaderRegion() {
        return true; // all tiers show at least a mini header
    }

    /** Whether the layout has a dispatch-row region. */
    public boolean hasRowRegion() {
        return this.tier.showsDispatchRows();
    }

    /** Whether the layout has a cargo summary region. */
    public boolean hasCargoRegion() {
        return this.tier.showsCargoSummary() && this.cargoSummarySlots > 0;
    }

    /** Whether the layout has a history/event region. */
    public boolean hasHistoryRegion() {
        return this.tier.showsHistory() && this.historyEntryCapacity > 0;
    }

    /** Whether the layout has a train roster region. */
    public boolean hasRosterRegion() {
        return this.tier.showsTrainRoster() && this.rosterCapacity > 0;
    }

    /** Whether the layout has an exception/warning strip. */
    public boolean hasExceptionStrip() {
        return this.tier.showsExceptionStrip();
    }

    @Override
    public String toString() {
        return "BoardLayoutSpec[" + this.width + "x" + this.height + " " + this.tier
                + " rows=" + this.dispatchRowCapacity
                + " history=" + this.historyEntryCapacity
                + " roster=" + this.rosterCapacity
                + " cargo=" + this.cargoSummarySlots + "]";
    }
}
