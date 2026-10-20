package com.createstats.client.timeswitch;

public record TimeSwitchRingLayout(int rx, int ry, int hubW) {
    public static final int PAD = 5;

    public static final int PANEL_W = TimeSwitchStrip.WIDTH + PAD * 2;
    public static final int PANEL_H = PAD + 10 + 4 + TimeSwitchStrip.HEIGHT_LABELLED + PAD - 2;

    public static final int HUB_H = PAD + 10 + PAD - 2;

    public static final int GAP = 4;

    public static TimeSwitchRingLayout of(int width, int height, int keepWidth) {
        final int hubW = keepWidth + PAD * 2;
        final int nearestX = (hubW + 1) / 2 + GAP + (PANEL_W + 1) / 2;
        final int nearestY = (HUB_H + 1) / 2 + GAP + (PANEL_H + 1) / 2;
        final int rx = Math.max(nearestX, Math.min(PANEL_W + 12, width / 2 - PANEL_W / 2 - 6));
        final int ry = Math.max(nearestY, Math.min(PANEL_H + 34, height / 2 - PANEL_H / 2 - 22));
        return new TimeSwitchRingLayout(rx, ry, hubW);
    }

    public int[][] rects(int width, int height) {
        final int cx = width / 2;
        final int cy = height / 2;
        final int[][] centres = {{cx, cy - ry}, {cx + rx, cy}, {cx, cy + ry}, {cx - rx, cy}};
        final int[][] out = new int[5][];
        for (int k = 0; k < 4; k++) {
            out[k] = new int[] {centres[k][0] - PANEL_W / 2, centres[k][1] - PANEL_H / 2, PANEL_W, PANEL_H};
        }
        out[4] = new int[] {cx - hubW / 2, cy - HUB_H / 2, hubW, HUB_H};
        return out;
    }
}
