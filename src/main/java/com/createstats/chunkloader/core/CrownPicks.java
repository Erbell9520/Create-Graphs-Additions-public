package com.createstats.chunkloader.core;

public final class CrownPicks {
    public enum Verdict {
        PICK,
        DROP,
        NO_OWNER,
        NOT_OWNER,
        STATION_DARK_FOR_GOOD,
        OUTSIDE_REGION,
        SABLE_PLOT,
        OWN_CHUNK,
        STATION_FULL,
        PLAYER_FULL,

        BOARD_CARRY,

        BOARD_SUPPLY;

        public boolean changes() {
            return this == PICK || this == DROP;
        }
    }

    public record Request(boolean ownerKnown, boolean byOwner, boolean mayHold, int stationX, int stationZ, int x, int z,
                          boolean alreadyPicked, int stationPicks, int ownerTotal, long boardCarry, int boardShortIn) {
        public Request(boolean ownerKnown, boolean byOwner, boolean mayHold, int stationX, int stationZ, int x, int z,
                       boolean alreadyPicked, int stationPicks, int ownerTotal) {
            this(ownerKnown, byOwner, mayHold, stationX, stationZ, x, z, alreadyPicked, stationPicks, ownerTotal,
                    NO_BOARD, -1);
        }

        public Request(boolean ownerKnown, boolean byOwner, boolean mayHold, int stationX, int stationZ, int x, int z,
                       boolean alreadyPicked, int stationPicks, int ownerTotal, long boardCarry) {
            this(ownerKnown, byOwner, mayHold, stationX, stationZ, x, z, alreadyPicked, stationPicks, ownerTotal,
                    boardCarry, -1);
        }
    }

    public static final long NO_BOARD = Long.MAX_VALUE;

    private CrownPicks() {
    }

    public static Verdict toggle(Request r, CrownLaw law) {
        if (!r.ownerKnown()) {
            return Verdict.NO_OWNER;
        }
        if (!r.byOwner()) {
            return Verdict.NOT_OWNER;
        }
        if (r.alreadyPicked()) {
            return r.x() == r.stationX() && r.z() == r.stationZ() ? Verdict.OWN_CHUNK : Verdict.DROP;
        }
        if (!r.mayHold()) {
            return Verdict.STATION_DARK_FOR_GOOD;
        }
        if (!CrownRegion.sameRegion(r.stationX(), r.stationZ(), r.x(), r.z())) {
            return Verdict.OUTSIDE_REGION;
        }
        if (CrownRegion.isPlotChunk(r.x(), r.z())) {
            return Verdict.SABLE_PLOT;
        }
        if (r.stationPicks() >= law.maxPerStation()) {
            return Verdict.STATION_FULL;
        }
        if (r.ownerTotal() >= law.maxPerPlayer()) {
            return Verdict.PLAYER_FULL;
        }
        if (r.boardCarry() != NO_BOARD && law.totalDraw(r.stationPicks() + 1) > r.boardCarry()) {
            return Verdict.BOARD_CARRY;
        }
        if (r.boardShortIn() >= 0 && r.boardShortIn() < CrownBoard.SUPPLY_HOLD_TICKS) {
            return Verdict.BOARD_SUPPLY;
        }
        return Verdict.PICK;
    }
}
