package com.createstats.powertrack.core;

import com.createstats.power.PortableFeTransfer;

// PPI grammar, far side is a rail
public final class FeederPlan {
    public enum Mode {
        SUPPLY,
        COLLECT,
        AUTO;

        public static Mode byRow(int row) {
            final Mode[] values = values();
            return values[Math.floorMod(row, values.length)];
        }

        public int row() {
            return ordinal();
        }
    }

    public enum Reason {
        READY,
        SWITCHED_OFF,

        NO_RAIL,
        FLOOR_HELD,

        STATION_HELD,

        WRONG_TERMINAL
    }

    public record Input(Mode mode, boolean onRail, boolean switchedOff, int rate, int buffer,
                        long stationStored, long stationCapacity, int floorPct, boolean floorWasHolding,
                        boolean autoSupplying, boolean stationHolds, boolean refusesSupply,
                        boolean refusesCollect) {
        public Input(Mode mode, boolean onRail, boolean switchedOff, int rate, int buffer,
                     long stationStored, long stationCapacity, int floorPct, boolean floorWasHolding,
                     boolean autoSupplying) {
            this(mode, onRail, switchedOff, rate, buffer, stationStored, stationCapacity, floorPct,
                    floorWasHolding, autoSupplying, false, false, false);
        }
    }

    public record Plan(SectionExchange.Role role, int gridIntake, boolean offersToGrid, int railAvailable,
                       int railRoom, int trainFloorPct, boolean floorHolding, boolean autoSupplying,
                       Reason reason) {
        public boolean chargeOpen() {
            return role == SectionExchange.Role.SUPPLY && reason == Reason.READY;
        }
    }

    private FeederPlan() {
    }

    public static int bufferCapacity(int rate) {
        return PortableFeTransfer.bufferCapacity(rate);
    }

    public static boolean autoSupplies(boolean wasSupplying, long stored, long capacity, int floorPct) {
        if (capacity <= 0) {
            return true;
        }
        final double pct = 100.0 * Math.max(0L, stored) / capacity;
        return wasSupplying ? pct > floorPct : pct >= floorPct + PortableFeTransfer.HYSTERESIS_PCT;
    }

    public static Plan plan(Input in) {
        final int rate = Math.max(0, in.rate());
        final int buffer = Math.max(0, in.buffer());
        final int capacity = bufferCapacity(rate);
        final int floor = Math.max(0, Math.min(100, in.floorPct()));

        boolean auto = in.autoSupplying();
        final SectionExchange.Role want;
        if (in.mode() == Mode.SUPPLY) {
            want = SectionExchange.Role.SUPPLY;
        } else if (in.mode() == Mode.COLLECT) {
            want = SectionExchange.Role.COLLECT;
        } else {
            auto = autoSupplies(in.autoSupplying(), in.stationStored(), in.stationCapacity(), floor);

            if (auto && (in.refusesSupply() || in.stationHolds()) && !in.refusesCollect()) {
                auto = false;
            } else if (!auto && in.refusesCollect() && !in.refusesSupply()) {
                auto = true;
            }
            want = auto ? SectionExchange.Role.SUPPLY : SectionExchange.Role.COLLECT;
        }

        if (want == SectionExchange.Role.SUPPLY) {
            final boolean holding = in.mode() == Mode.SUPPLY
                    && PortableFeTransfer.floorHolds(in.floorWasHolding(), in.stationStored(),
                    in.stationCapacity(), floor);
            final Reason reason = reason(in, holding, in.refusesSupply(), true);
            final boolean live = in.onRail() && !in.switchedOff();

            final int railAvailable = live ? Math.min(buffer, rate) : 0;
            final int intake = reason == Reason.READY ? Math.max(0, capacity - buffer) : 0;
            return new Plan(want, intake, false, railAvailable, 0, floor, holding, auto, reason);
        }

        final Reason reason = reason(in, false, in.refusesCollect(), false);
        final int room = reason == Reason.READY ? Math.max(0, Math.min(rate, capacity - buffer)) : 0;

        return new Plan(want, 0, !in.switchedOff(), 0, room, floor, false, auto, reason);
    }

    private static Reason reason(Input in, boolean holding, boolean wrongWay, boolean supplying) {
        if (in.switchedOff()) {
            return Reason.SWITCHED_OFF;
        }
        if (!in.onRail()) {
            return Reason.NO_RAIL;
        }
        if (wrongWay) {
            return Reason.WRONG_TERMINAL;
        }
        if (supplying && in.stationHolds()) {
            return Reason.STATION_HELD;
        }
        if (holding) {
            return Reason.FLOOR_HELD;
        }
        return Reason.READY;
    }
}
