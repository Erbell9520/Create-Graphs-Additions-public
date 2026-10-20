package com.createstats.contactor;

public final class ContactorState {
    public static final int LEFT_SIDE = 0;
    public static final int RIGHT_SIDE = 1;

    private boolean settled;
    private final long[] lastMoveTick = {ContactorLogic.NEVER, ContactorLogic.NEVER};
    private int comparatorLevel;

    // safety net, a schematicannon print can leave armatures disagreeing with the coils
    public boolean takeFirstTick() {
        if (settled) {
            return false;
        }
        settled = true;
        return true;
    }

    public long lastMoveTick(int side) {
        return lastMoveTick[side];
    }

    public void recordMove(int side, long now) {
        lastMoveTick[side] = now;
    }

    public int comparatorLevel() {
        return comparatorLevel;
    }

    public boolean moveComparator(int level) {
        if (level == comparatorLevel) {
            return false;
        }
        comparatorLevel = level;
        return true;
    }
}
