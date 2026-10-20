package com.createstats.gui.meter.plate;

import com.createstats.gui.meter.plate.PlateGeometry.Rect;

public final class PlateHotspots {
    private final PlateGeometry geo;

    public PlateHotspots(PlateGeometry geo) {
        this.geo = geo;
    }

    public Rect key(int i) {
        return geo.keys[i];
    }

    public Rect keys() {
        final Rect first = geo.keys[0];
        final Rect last = geo.keys[geo.keys.length - 1];
        return new Rect(first.x(), first.y(), first.w(), last.bottom() - first.y());
    }

    public Rect stud() {
        final Rect s = geo.stud;
        return new Rect(s.x() - 2, s.y() - 2, 20, 20);
    }

    public static Rect line(PlateGeometry.Line l) {
        final Rect w = l.window();
        return new Rect(l.legendX(), w.y(), w.right() - l.legendX(), w.h());
    }

    public Rect peak() {
        return line(geo.peak);
    }

    public Rect carried() {
        return line(geo.carried);
    }

    public Rect comparator() {
        return line(geo.comparator);
    }

    public Rect load() {
        return line(geo.load);
    }

    public Rect rate() {
        return geo.rate;
    }

    public Rect trace() {
        return geo.trace.glass();
    }

    public Rect terminal() {
        return new Rect(geo.terminal.x() - 1, geo.jewelIn.y() - 1, 24, 26);
    }

    public Rect gland() {
        return new Rect(geo.jewelOut.x() - 1, geo.jewelOut.y() - 1, 26, 26);
    }

    public Rect supply() {
        return new Rect(6, PlateGeometry.ICON_Y, geo.w / 2 - 6, 16);
    }

    public Rect loadName() {
        return new Rect(geo.w / 2, PlateGeometry.ICON_Y, geo.w / 2 - 6, 16);
    }

    public Rect stamp() {
        return new Rect(geo.w - 110, PlateGeometry.TITLE_Y - 2, 104, 11);
    }
}
