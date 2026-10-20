package com.createstats.power;

public final class FeManagedState {
    private FePriority priority = FePriority.DEFAULT;
    private FeLock lock = FeLock.NONE;
    private boolean shed;

    public FePriority priority() {
        return priority;
    }

    public void setPriority(FePriority tier) {
        this.priority = tier == null ? FePriority.DEFAULT : tier;
    }

    public FeLock lock() {
        return lock;
    }

    public void setLock(FeLock lock) {
        this.lock = lock == null ? FeLock.NONE : lock;
        this.shed = false;
    }

    public boolean isShed() {
        return shed;
    }

    public void setShed(boolean shed) {
        this.shed = shed;
    }

    public boolean isForcedOff() {
        return lock == FeLock.FORCE_OFF;
    }

    public boolean isSuppressed() {
        return lock == FeLock.FORCE_OFF || (shed && lock != FeLock.FORCE_ON);
    }

    public int priorityOrdinal() {
        return priority.ordinal();
    }

    public int lockOrdinal() {
        return lock.ordinal();
    }

    public void load(int priorityOrdinal, int lockOrdinal) {
        this.priority = FePriority.byOrdinal(priorityOrdinal);
        this.lock = FeLock.byOrdinal(lockOrdinal);
        this.shed = false;
    }
}
