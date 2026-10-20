package com.createstats.power;

public final class PortableFeTransfer {
    public enum Mode {
        CHARGE,
        DRAW;

        public static Mode byRow(int row) {
            return row == 1 ? DRAW : CHARGE;
        }

        public int row() {
            return ordinal();
        }
    }

    public enum Reason {
        READY,
        SWITCHED_OFF,
        DISCONNECTED,
        NO_STORAGE,
        LOCKED,
        FLOOR_HELD,
        TRAIN_FULL,
        TRAIN_EMPTY,

        STATION_HELD,

        WRONG_TERMINAL
    }

    public static final int HYSTERESIS_PCT = 5;

    public static final int DEFAULT_FLOOR_PCT = 50;

    private PortableFeTransfer() {
    }

    public static int bufferCapacity(int rate) {
        return Math.max(PortableFeLadder.FLOOR_FE, rate);
    }

    public static boolean floorHolds(boolean wasHolding, long stored, long capacity, int floorPct) {
        if (floorPct <= 0 || capacity <= 0) {
            return false;
        }
        final double pct = 100.0 * Math.max(0L, stored) / capacity;
        return wasHolding ? pct < floorPct + HYSTERESIS_PCT : pct <= floorPct;
    }

    public static long aboveFloor(long stored, long capacity, int floorPct) {
        if (capacity <= 0) {
            return 0L;
        }
        final long floor = floorPct <= 0 ? 0L : (long) Math.ceil(capacity * (floorPct / 100.0));
        return Math.max(0L, stored - floor);
    }

    public record Input(Mode mode, boolean connected, boolean switchedOff, boolean locked,
                        int rate, int buffer, long trainStored, long trainCapacity,
                        long sourceStored, long sourceCapacity, int floorPct, boolean floorWasHolding,
                        boolean stationHolds, boolean wrongTerminal) {
        public Input(Mode mode, boolean connected, boolean switchedOff, boolean locked,
                     int rate, int buffer, long trainStored, long trainCapacity,
                     long sourceStored, long sourceCapacity, int floorPct, boolean floorWasHolding) {
            this(mode, connected, switchedOff, locked, rate, buffer, trainStored, trainCapacity,
                    sourceStored, sourceCapacity, floorPct, floorWasHolding, false, false);
        }
    }

    public record Plan(int gridIntake, boolean offersToGrid, int trainDelivery, int trainPull,
                       boolean floorHolding, Reason reason) {
    }

    public static Plan plan(Input in) {
        final int rate = Math.max(0, in.rate());
        final int buffer = Math.max(0, in.buffer());
        final int capacity = bufferCapacity(rate);
        final boolean live = in.connected() && !in.switchedOff();
        final long trainRoom = Math.max(0L, in.trainCapacity() - Math.max(0L, in.trainStored()));

        if (in.mode() == Mode.CHARGE) {
            final boolean holding = floorHolds(in.floorWasHolding(), in.sourceStored(),
                    in.sourceCapacity(), in.floorPct());
            final Reason reason = reason(in, holding, trainRoom);

            final int delivery = live && !in.locked()
                    ? (int) Math.min(Math.min(buffer, rate), trainRoom) : 0;

            final int intake = reason == Reason.READY
                    ? (int) Math.max(0L, Math.min(capacity - buffer, trainRoom - buffer)) : 0;
            return new Plan(intake, false, delivery, 0, holding, reason);
        }

        final boolean holding = floorHolds(in.floorWasHolding(), in.trainStored(), in.trainCapacity(),
                in.floorPct());
        final Reason reason = reason(in, holding, trainRoom);
        final int pull = reason == Reason.READY
                ? (int) Math.max(0L, Math.min(Math.min(rate, capacity - buffer),
                        aboveFloor(in.trainStored(), in.trainCapacity(), in.floorPct())))
                : 0;

        return new Plan(0, buffer > 0 && !in.switchedOff(), 0, pull, holding, reason);
    }

    private static Reason reason(Input in, boolean holding, long trainRoom) {
        if (in.switchedOff()) {
            return Reason.SWITCHED_OFF;
        }
        if (!in.connected()) {
            return Reason.DISCONNECTED;
        }
        if (in.trainCapacity() <= 0) {
            return Reason.NO_STORAGE;
        }

        if (in.wrongTerminal()) {
            return Reason.WRONG_TERMINAL;
        }
        if (in.locked()) {
            return Reason.LOCKED;
        }

        if (in.mode() == Mode.CHARGE && in.stationHolds()) {
            return Reason.STATION_HELD;
        }
        if (holding) {
            return Reason.FLOOR_HELD;
        }
        if (in.mode() == Mode.CHARGE && trainRoom <= 0) {
            return Reason.TRAIN_FULL;
        }
        if (in.mode() == Mode.DRAW
                && aboveFloor(in.trainStored(), in.trainCapacity(), in.floorPct()) <= 0) {
            return in.trainStored() <= 0 ? Reason.TRAIN_EMPTY : Reason.FLOOR_HELD;
        }
        return Reason.READY;
    }
}
