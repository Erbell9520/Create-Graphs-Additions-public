package com.createstats.book.specimen;

import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.gui.meter.plate.PlateGeometry;
import com.createstats.gui.meter.plate.PlateGeometry.Rect;
import com.createstats.gui.meter.plate.PlateHotspots;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// from each screen's own geometry class, never measured off a picture
public final class Specimens {
    private Specimens() {
    }

    public static Optional<SpecimenAnatomy> anatomy(String id) {
        if ("power_meter".equals(id)) {
            return Optional.of(powerMeter());
        }
        if (SheetDrawing.ID.equals(id)) {
            return Optional.of(SheetDrawing.anatomy());
        }
        if (BatteryPlates.isPlate(id)) {
            return Optional.of(BatteryPlates.anatomy(id));
        }
        if (RingPlates.isPlate(id)) {
            return Optional.of(RingPlates.anatomy(id));
        }
        if (PduPlates.isPlate(id)) {
            return Optional.of(PduPlates.anatomy(id));
        }
        return BlockFigures.byId(id).map(BlockFigure::anatomy);
    }

    public static List<String> ids() {
        final List<String> out = new ArrayList<>(List.of("power_meter", SheetDrawing.ID));
        out.addAll(BatteryPlates.IDS);
        out.addAll(RingPlates.IDS);
        out.addAll(PduPlates.IDS);
        for (final BlockFigure f : BlockFigures.all()) {
            out.add(f.id());
        }
        return out;
    }

    public static SpecimenAnatomy powerMeter() {
        return powerMeter(PlateGeometry.of(PlateGeometry.MAX_W + 12, PlateGeometry.MAX_H + 12));
    }

    public static SpecimenAnatomy powerMeter(PlateGeometry geo) {
        final PlateHotspots hot = new PlateHotspots(geo);
        final List<SpecimenAnatomy.Hot> controls = new ArrayList<>();
        controls.add(hot("terminal", hot.terminal()));
        controls.add(hot("keys", hot.keys()));
        controls.add(hot("stud", hot.stud()));
        controls.add(hot("peak", hot.peak()));
        controls.add(hot("carried", hot.carried()));
        controls.add(hot("rate", hot.rate()));
        controls.add(hot("trace", hot.trace()));
        controls.add(hot("gland", hot.gland()));
        controls.add(hot("comparator", hot.comparator()));
        controls.add(hot("load", hot.load()));
        return new SpecimenAnatomy("power_meter", geo.w, geo.h, controls);
    }

    private static SpecimenAnatomy.Hot hot(String id, Rect r) {
        return new SpecimenAnatomy.Hot(id, r.x(), r.y(), r.w(), r.h());
    }
}
