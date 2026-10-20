package com.createstats.chunkloader.core;

public record CrownState(
        int held,
        int picked,
        int form,
        int tickingMask,
        CrownHold.Phase phase,
        float store,
        long storedFe,
        long capacityFe,
        int drawFePerTick,
        int pickedDrawFePerTick,
        int intakeFePerTick,
        int nextPriceFePerTick,
        int ownerTotal,
        int maxPerStation,
        int maxPerPlayer,
        int studs,
        int studARating,
        int studBRating,
        int heartbeat,
        int light,
        Reason reason,
        long otherStation,
        boolean blocked,
        int blockRight,
        int blockUp,
        int blockFront,
        String blockingId,
        int regionX,
        int regionZ) {
    public enum Reason {
        NONE,

        NO_OWNER,

        REGION_TAKEN,

        ON_SABLE,

        SWITCHED_OFF,

        NO_POWER;

        public static Reason of(int ordinal) {
            final Reason[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
        }

        public boolean mayHold() {
            return this == NONE || this == NO_POWER;
        }
    }

    public static final CrownState EMPTY = new CrownState(0, 0, 0, 0, CrownHold.Phase.DARK, 0f, 0L, 0L, 0, 0, 0, 0,
            0, CrownLaw.DEFAULT.maxPerStation(), CrownLaw.DEFAULT.maxPerPlayer(), 0, 0, 0, 0, 0, Reason.NONE, 0L, false,
            0, 0, 0, "", 0, 0);

    private static final int PACKED = 28;

    public CrownState {
        phase = phase == null ? CrownHold.Phase.DARK : phase;
        reason = reason == null ? Reason.NONE : reason;
        blockingId = blockingId == null ? "" : blockingId;
    }

    public boolean studAWired() {
        return (studs & 1) != 0;
    }

    public boolean studBWired() {
        return (studs & 2) != 0;
    }

    public boolean ticking(int index) {
        return index >= 0 && index < 32 && (tickingMask >>> index & 1) != 0;
    }

    public float studLoad(int stud) {
        final int[] parts = CrownStuds.split(intakeFePerTick, studARating, studBRating, studAWired(), studBWired());
        final int rating = stud == 1 ? studBRating : studARating;
        return rating <= 0 ? 0f : Math.min(1f, parts[stud == 1 ? 1 : 0] / (float) rating);
    }

    public float rideThroughSeconds() {
        return drawFePerTick <= 0 ? 0f : (storedFe / drawFePerTick) / 20f;
    }

    public long[] pack() {
        return new long[] {
                held, picked, form, tickingMask, phase.ordinal(), Float.floatToIntBits(store), storedFe, capacityFe,
                drawFePerTick, pickedDrawFePerTick, intakeFePerTick, nextPriceFePerTick, ownerTotal, maxPerStation,
                maxPerPlayer, studs, studARating, studBRating, heartbeat, light, reason.ordinal(), otherStation,
                blocked ? 1L : 0L, blockRight, blockUp, blockFront, regionX, regionZ};
    }

    public static CrownState unpack(long[] p, String blockingId) {
        if (p == null || p.length < PACKED) {
            return EMPTY;
        }
        return new CrownState((int) p[0], (int) p[1], (int) p[2], (int) p[3], CrownHold.Phase.of((int) p[4]),
                Float.intBitsToFloat((int) p[5]), p[6], p[7], (int) p[8], (int) p[9], (int) p[10], (int) p[11],
                (int) p[12], (int) p[13], (int) p[14], (int) p[15], (int) p[16], (int) p[17], (int) p[18],
                (int) p[19], Reason.of((int) p[20]), p[21], p[22] != 0L, (int) p[23], (int) p[24], (int) p[25],
                blockingId, (int) p[26], (int) p[27]);
    }
}
