package com.createstats.dispatch;

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

                rows = 4;
                history = 0;
                roster = 0;
                cargoSlots = 0;
                break;

            case COMPACT:

                rows = 6 + Math.max(0, area - 2);
                history = 0;
                roster = 0;
                cargoSlots = 2;
                break;

            case STANDARD:

                rows = 10 + Math.max(0, area - 4) * 2;
                history = 3;
                roster = 0;
                cargoSlots = 4;
                break;

            case RICH:

                rows = 16 + Math.max(0, area - 9) * 2;
                history = 6 + Math.max(0, area - 9);
                roster = 3;
                cargoSlots = 8;
                break;

            case FULL:
            default:

                int extraArea = Math.max(0, area - 16);
                rows = 24 + extraArea * 2;
                history = 10 + extraArea;
                roster = 6 + extraArea;
                cargoSlots = 12 + extraArea;
                break;
        }

        return new BoardLayoutSpec(w, h, tier, rows, history, roster, cargoSlots);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public BoardTier getTier() {
        return this.tier;
    }

    public int getDispatchRowCapacity() {
        return this.dispatchRowCapacity;
    }

    public int getHistoryEntryCapacity() {
        return this.historyEntryCapacity;
    }

    public int getRosterCapacity() {
        return this.rosterCapacity;
    }

    public int getCargoSummarySlots() {
        return this.cargoSummarySlots;
    }

    public boolean hasHeaderRegion() {
        return true;
    }

    public boolean hasRowRegion() {
        return this.tier.showsDispatchRows();
    }

    public boolean hasCargoRegion() {
        return this.tier.showsCargoSummary() && this.cargoSummarySlots > 0;
    }

    public boolean hasHistoryRegion() {
        return this.tier.showsHistory() && this.historyEntryCapacity > 0;
    }

    public boolean hasRosterRegion() {
        return this.tier.showsTrainRoster() && this.rosterCapacity > 0;
    }

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
