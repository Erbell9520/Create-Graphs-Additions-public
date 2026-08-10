package com.createstats.power;

/** [1] */
public interface FeManagedLoad extends FeRatedLoad {

    /** The tier this device competes at. Never null. */
    FePriority managedPriority();

    void setManagedPriority(FePriority tier);

    /** The player's override, if any. Never null. */
    FeLock managedLock();

    void setManagedLock(FeLock lock);

    /**
     * Told by the panel that it may not draw. Must take effect on this tick: stop spending, stop
     * doing work, and show it.
     */
    void setManagedShed(boolean shed);

    boolean isManagedShed();
}
