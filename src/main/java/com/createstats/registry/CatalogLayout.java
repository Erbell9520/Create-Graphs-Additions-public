package com.createstats.registry;

public final class CatalogLayout {
    public static final int VISIBLE_ROWS = 5;

    public static final int SCROLL_TRAVEL = 95;

    public static final int SCROLLER_MIDDLE = 7;

    public static final int TITLE_X = 8;

    public static final int RUNNING_HEAD_RIGHT = 187;

    public static final int RUNNING_HEAD_GAP = 8;

    public static final int LABEL_X = 3;

    public static final int LABEL_PAD = 9;

    public static final int LABEL_MAX_TEXT = 96;

    public static final int LABEL_CALM_EDGE = 66;

    private final int[] bannerRows;
    private final int[] sectionRows;
    private final int totalRows;

    public CatalogLayout(int[] sectionRows) {
        this.sectionRows = sectionRows.clone();
        this.bannerRows = new int[sectionRows.length];
        int row = 0;
        for (int i = 0; i < sectionRows.length; i++) {
            if (sectionRows[i] <= 0) {
                this.bannerRows[i] = -1;
                continue;
            }
            this.bannerRows[i] = row;
            row += 1 + sectionRows[i];
        }
        this.totalRows = row;
    }

    public int sectionCount() {
        return this.bannerRows.length;
    }

    public int totalRows() {
        return this.totalRows;
    }

    public int bannerRow(int section) {
        return this.bannerRows[section];
    }

    public int itemRows(int section) {
        return Math.max(0, this.sectionRows[section]);
    }

    public int bannerAt(int row) {
        for (int i = 0; i < this.bannerRows.length; i++) {
            if (this.bannerRows[i] == row) {
                return i;
            }
        }
        return -1;
    }

    public int sectionAt(int row) {
        int found = -1;
        for (int i = 0; i < this.bannerRows.length; i++) {
            if (this.bannerRows[i] >= 0 && this.bannerRows[i] <= row) {
                found = i;
            }
        }
        return found;
    }

    public int rowsBeyondView() {
        return this.totalRows - VISIBLE_ROWS;
    }

    public int firstRowFor(float scrollOffs) {
        if (this.rowsBeyondView() <= 0) {
            return 0;
        }
        return Math.max((int) ((double) (scrollOffs * (float) this.rowsBeyondView()) + 0.5), 0);
    }

    public float scrollFor(int firstRow) {
        if (this.rowsBeyondView() <= 0) {
            return 0.0F;
        }
        final float offs = (float) firstRow / (float) this.rowsBeyondView();
        return Math.max(0.0F, Math.min(1.0F, offs));
    }

    public int lastFirstRow() {
        return Math.max(0, this.rowsBeyondView());
    }

    public int jumpTarget(int section) {
        final int banner = this.bannerRows[section];
        return banner < 0 ? -1 : Math.min(banner, this.lastFirstRow());
    }

    public int notchY(int section) {
        final int target = this.jumpTarget(section);
        if (target < 0) {
            return -1;
        }
        return (int) ((float) SCROLL_TRAVEL * this.scrollFor(target)) + SCROLLER_MIDDLE;
    }

    public int runningHead(int firstRow) {
        final int section = this.sectionAt(firstRow);
        if (section < 0) {
            return -1;
        }
        return this.bannerRows[section] < firstRow ? section : -1;
    }
}
