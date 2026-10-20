package com.createstats.statsmk3.instrument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InstrumentRegistry {
    private static final Map<String, Instrument> BY_ID = new LinkedHashMap<>();
    private static final List<Instrument> ORDER = new ArrayList<>();

    static {
        register(new Instruments.Area());
        register(new Instruments.Line());
        register(new Instruments.Bar());
        register(new Instruments.Arc());
        register(new Instruments.Tape());
        register(new Instruments.Numeric());
        register(new Instruments.Ring());
        register(new Instruments.Matrix());
        register(new Instruments.SparkRow());
        register(new Horizon());
        register(new Radar());

        register(new FlightInstruments.Attitude());
        register(new FlightInstruments.Heading());
        register(new FlightInstruments.Vsi());
        register(new FlightInstruments.Wind());
        register(new FlightInstruments.AltTape());

        register(new DriveInstruments.Speedo());
        register(new DriveInstruments.Gear());
        register(new DriveInstruments.Gg());
        register(new DriveInstruments.Steer());
        register(new DriveInstruments.Telltales());
    }

    private InstrumentRegistry() {
    }

    public static void register(Instrument instrument) {
        if (instrument == null || instrument.id() == null || instrument.id().isEmpty()) {
            return;
        }
        if (BY_ID.putIfAbsent(instrument.id(), instrument) == null) {
            ORDER.add(instrument);
        }
    }

    public static Instrument get(String id) {
        final Instrument found = BY_ID.get(id);
        return found != null ? found : defaultInstrument();
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }

    public static Instrument defaultInstrument() {
        return BY_ID.get("area");
    }

    public static List<Instrument> all() {
        return Collections.unmodifiableList(ORDER);
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ORDER.size(); i++) {
            if (ORDER.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }
}
