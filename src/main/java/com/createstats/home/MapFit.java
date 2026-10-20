package com.createstats.home;

// whole pixels per block or walls come out two thicknesses
public final class MapFit {
    public static final int[] SCALES = {2, 3, 4, 5, 6, 7, 8, 10, 12, 16};

    public static final int MAX_SPAN = 96;

    public static final int MARGIN = 2;

    public static final int MIN_SPAN = 12;

    public static final int MIN_FIT_SCALE = 4;

    private final int box;
    private final int scale;

    private final int originX;
    private final int originZ;

    private MapFit(int box, int scale, int originX, int originZ) {
        this.box = box;
        this.scale = scale;
        this.originX = originX;
        this.originZ = originZ;
    }

    public static MapFit fit(int box, int minX, int minZ, int maxX, int maxZ, int zoom, int panX, int panZ) {
        return fit(box, minX, minZ, maxX, maxZ, (minX + maxX) / 2, (minZ + maxZ) / 2, zoom, panX, panZ);
    }

    public static MapFit fit(int box, int minX, int minZ, int maxX, int maxZ, int focusX, int focusZ, int zoom,
                             int panX, int panZ) {
        int cx2 = minX + maxX;
        int cz2 = minZ + maxZ;
        int span = Math.max(maxX - minX, maxZ - minZ) + 1 + 2 * MARGIN;
        span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, span));
        int fitted = 0;
        for (int i = 0; i < SCALES.length; i++) {
            if (SCALES[i] * span <= box || SCALES[i] <= MIN_FIT_SCALE) {
                fitted = i;
            }
        }
        if (SCALES[fitted] * span > box) {
            cx2 = 2 * focusX;
            cz2 = 2 * focusZ;
        }
        final int index = Math.max(0, Math.min(SCALES.length - 1, fitted + zoom));
        final int scale = SCALES[index];

        final int centreX = (cx2 + 2 * panX) * scale / 2 + scale / 2;
        final int centreZ = (cz2 + 2 * panZ) * scale / 2 + scale / 2;
        return new MapFit(box, scale, centreX - box / 2, centreZ - box / 2);
    }

    public static int[] panToKeep(int box, int minX, int minZ, int maxX, int maxZ, int focusX, int focusZ,
                                  int zoomFrom, int zoomTo, int panX, int panZ, int cursorPx, int cursorPz) {
        final MapFit before = fit(box, minX, minZ, maxX, maxZ, focusX, focusZ, zoomFrom, panX, panZ);
        final int bx = before.blockX(cursorPx);
        final int bz = before.blockZ(cursorPz);

        final float fx = (cursorPx - before.px(bx)) / (float) before.scale();
        final float fz = (cursorPz - before.pz(bz)) / (float) before.scale();
        final MapFit after = fit(box, minX, minZ, maxX, maxZ, focusX, focusZ, zoomTo, panX, panZ);

        final int dx = Math.round((after.px(bx) + fx * after.scale() - cursorPx) / (float) after.scale());
        final int dz = Math.round((after.pz(bz) + fz * after.scale() - cursorPz) / (float) after.scale());
        return new int[] {panX + dx, panZ + dz};
    }

    public int scale() {
        return scale;
    }

    public int box() {
        return box;
    }

    public int px(int x) {
        return x * scale - originX;
    }

    public int pz(int z) {
        return z * scale - originZ;
    }

    public int blockX(int px) {
        return Math.floorDiv(px + originX, scale);
    }

    public int blockZ(int pz) {
        return Math.floorDiv(pz + originZ, scale);
    }

    public boolean visible(int x, int z) {
        final int ax = px(x);
        final int az = pz(z);
        return ax + scale > 0 && ax < box && az + scale > 0 && az < box;
    }

    public int firstX() {
        return blockX(0);
    }

    public int lastX() {
        return blockX(box - 1);
    }

    public int firstZ() {
        return blockZ(0);
    }

    public int lastZ() {
        return blockZ(box - 1);
    }

    public int clampInside(int p, int inset) {
        return Math.max(inset, Math.min(box - 1 - inset, p));
    }
}
