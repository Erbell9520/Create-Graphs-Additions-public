package com.createstats.seismic;

import java.util.List;

public final class ShellScan<S> {
    public interface Ground<S> {
        S section(int sx, int sy, int sz);

        int kindAt(S s, int x, int y, int z);

        boolean covered(int x, int y, int z);
    }

    private static final Object SKIP = new Object();

    private final Ground<S> ground;
    private final int bx;
    private final int by;
    private final int bz;
    private final int radius;
    private final VeinBook book;
    private final Object[] sections;
    private final int sx0;
    private final int sy0;
    private final int sz0;
    private final int nx;
    private final int ny;
    private final int nz;
    private int tick;
    private boolean done;
    private long checked;
    private int veins;
    private int blocks;
    private float nearest = Float.MAX_VALUE;

    public ShellScan(Ground<S> ground, int bx, int by, int bz, int radius, VeinBook book) {
        this.ground = ground;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.radius = Math.max(1, Math.min(SurveyShells.MAX_RADIUS, radius));
        this.book = book;
        this.sx0 = (bx - this.radius) >> 4;
        this.sz0 = (bz - this.radius) >> 4;
        this.sy0 = (by - this.radius - 1) >> 4;
        this.nx = ((bx + this.radius) >> 4) - sx0 + 1;
        this.nz = ((bz + this.radius) >> 4) - sz0 + 1;
        this.ny = ((by - 1) >> 4) - sy0 + 1;
        this.sections = new Object[nx * ny * nz];
    }

    public int radius() {
        return radius;
    }

    public int tick() {
        return tick;
    }

    public boolean done() {
        return done;
    }

    public boolean full() {
        return book.full();
    }

    public long checked() {
        return checked;
    }

    public int veins() {
        return veins;
    }

    public int blocks() {
        return blocks;
    }

    public int nearest() {
        return veins == 0 ? -1 : Math.round(nearest);
    }

    public List<VeinBook.Vein> step() {
        if (done) {
            return List.of();
        }
        tick++;
        final int from = SurveyShells.from(tick, radius);
        final int to = SurveyShells.to(tick, radius);
        for (int i = from; i < to; i++) {
            final int p = SurveyShells.packed(i);
            final int x = bx + SurveyShells.dx(p);
            final int y = by + SurveyShells.dy(p);
            final int z = bz + SurveyShells.dz(p);
            final S section = section(x, y, z);
            if (section == null) {
                continue;
            }
            checked++;
            final int kind = ground.kindAt(section, x, y, z);
            if (kind < 0 || !ground.covered(x, y, z)) {
                continue;
            }
            book.add(p, SurveyShells.distance(i), kind);
        }
        final double front = Math.min(radius, SeismicWave.frontAt(tick));
        final List<VeinBook.Vein> closed;
        if (front >= radius) {
            closed = book.closeAll();
            done = true;
        } else {
            closed = book.close(front);
        }
        for (final VeinBook.Vein v : closed) {
            veins++;
            blocks += v.count();
            nearest = Math.min(nearest, v.nearest());
        }
        return closed;
    }

    @SuppressWarnings("unchecked")
    private S section(int x, int y, int z) {
        final int ix = (x >> 4) - sx0;
        final int iy = (y >> 4) - sy0;
        final int iz = (z >> 4) - sz0;
        if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) {
            return null;
        }
        final int cell = (iy * nz + iz) * nx + ix;
        Object s = sections[cell];
        if (s == null) {
            final S got = ground.section(x >> 4, y >> 4, z >> 4);
            s = got == null ? SKIP : got;
            sections[cell] = s;
        }
        return s == SKIP ? null : (S) s;
    }
}
