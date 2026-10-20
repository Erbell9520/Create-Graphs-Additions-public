package com.createstats.battle.core;

import java.util.List;
import java.util.function.IntPredicate;

public final class PlaceNamer implements Places.Namer {
    public record Marker(String name, int dx, int dy, int dz, int cover) {
        boolean covers(double x, double y, double z) {
            return Math.abs(x - dx) <= cover + 0.5 && Math.abs(y - dy) <= cover + 0.5 && Math.abs(z - dz) <= cover + 0.5;
        }
    }

    public record Part(int number, int[] cells) {
        boolean holds(int packed) {
            for (int c : cells) {
                if (c == packed) return true;
            }
            return false;
        }
    }

    private final boolean ground;
    private final ShipFrame frame;
    private final int minFore;
    private final int maxFore;
    private final int minStbd;
    private final int maxStbd;
    private final int bridgeDy;
    private final List<Marker> markers;
    private final List<Part> guns;
    private final List<Part> propellers;
    private final IntPredicate lift;

    public PlaceNamer(boolean ground, ShipFrame frame, int minFore, int maxFore, int minStbd, int maxStbd, int bridgeDy,
                      List<Marker> markers, List<Part> guns, List<Part> propellers, IntPredicate lift) {
        this.ground = ground;
        this.frame = frame;
        this.minFore = minFore;
        this.maxFore = maxFore;
        this.minStbd = minStbd;
        this.maxStbd = maxStbd;
        this.bridgeDy = bridgeDy;
        this.markers = markers;
        this.guns = guns;
        this.propellers = propellers;
        this.lift = lift;
    }

    @Override
    public String name(int[] cells, byte kind) {
        if (kind == Places.KIND_BROKE_AWAY) return PlaceName.brokeAway();
        if (cells.length == 0) return PlaceName.ship(PlaceName.FORE_MID, PlaceName.SIDE_CENTRE, PlaceName.LEVEL_MAIN);
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        int liftCells = 0;
        for (int c : cells) {
            cx += PackedPos.dx(c);
            cy += PackedPos.dy(c);
            cz += PackedPos.dz(c);
            if (lift.test(c)) liftCells++;
        }
        cx /= cells.length;
        cy /= cells.length;
        cz /= cells.length;

        for (Marker m : markers) {
            if (m.covers(cx, cy, cz) && m.name() != null && !m.name().isBlank()) return PlaceName.marker(m.name());
        }
        for (Part g : guns) {
            for (int c : cells) {
                if (g.holds(c)) return PlaceName.gun(g.number());
            }
        }
        for (Part p : propellers) {
            for (int c : cells) {
                if (p.holds(c)) return PlaceName.propeller(p.number());
            }
        }
        int fore = third(frame.fore((int) Math.round(cx), (int) Math.round(cz)), minFore, maxFore);
        if (!ground && liftCells * 2 >= cells.length) return PlaceName.envelope(fore);
        return geometry((int) Math.round(cx), cy, (int) Math.round(cz));
    }

    public String geometry(int packed) {
        return geometry(PackedPos.dx(packed), PackedPos.dy(packed), PackedPos.dz(packed));
    }

    private String geometry(int x, double y, int z) {
        int fore = third(frame.fore(x, z), minFore, maxFore);
        int side = third(frame.stbd(x, z), minStbd, maxStbd);
        int level = level(y);
        if (ground) {
            return PlaceName.compass(side, fore, level);
        }
        return PlaceName.ship(fore, side, level);
    }

    static int third(int value, int lo, int hi) {
        int span = hi - lo + 1;
        if (span <= 0) return 1;
        double t = (value - lo + 0.5) / span;
        return t < 1.0 / 3.0 ? 0 : t < 2.0 / 3.0 ? 1 : 2;
    }

    private int level(double y) {
        if (y < bridgeDy - 1) return PlaceName.LEVEL_BELOW;
        if (y >= bridgeDy + 3) return PlaceName.LEVEL_UPPER;
        return PlaceName.LEVEL_MAIN;
    }
}
