package com.createstats.vccmk4.air.core;

// walk a section at a time under a budget, the one shot read gave up above 262k cells
public final class ShapeWalk {
    public static final int CELLS_PER_TICK = 32_768;

    public static final int SKIP_COST = 64;

    static final int SECTION_CELLS = 16 * 16 * 16;

    @FunctionalInterface
    public interface Sections {
        int read(int sectionX, int sectionY, int sectionZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    }

    private final int[] box;
    private final int sx0;
    private final int sy0;
    private final int sz0;
    private final int sx1;
    private final int sy1;
    private final int sz1;
    private int sx;
    private int sy;
    private int sz;
    private boolean done;
    private int steps;

    public ShapeWalk(int[] box) {
        this.box = box.clone();
        sx0 = box[0] >> 4;
        sy0 = box[1] >> 4;
        sz0 = box[2] >> 4;
        sx1 = box[3] >> 4;
        sy1 = box[4] >> 4;
        sz1 = box[5] >> 4;
        sx = sx0;
        sy = sy0;
        sz = sz0;
        done = box[3] < box[0] || box[4] < box[1] || box[5] < box[2];
    }

    public boolean step(int budget, Sections sections) {
        if (done) return true;
        steps++;
        long spent = 0;
        while (!done && spent < budget) {
            int read = sections.read(sx, sy, sz,
                    Math.max(box[0], sx << 4), Math.max(box[1], sy << 4), Math.max(box[2], sz << 4),
                    Math.min(box[3], (sx << 4) + 15), Math.min(box[4], (sy << 4) + 15), Math.min(box[5], (sz << 4) + 15));
            spent += Math.max(SKIP_COST, read);
            advance();
        }
        return done;
    }

    public boolean done() {
        return done;
    }

    public int steps() {
        return steps;
    }

    public long sections() {
        return (long) (sx1 - sx0 + 1) * (sy1 - sy0 + 1) * (sz1 - sz0 + 1);
    }

    private void advance() {
        if (++sy <= sy1) return;
        sy = sy0;
        if (++sz <= sz1) return;
        sz = sz0;
        if (++sx <= sx1) return;
        done = true;
    }
}
