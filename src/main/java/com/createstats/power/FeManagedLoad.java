package com.createstats.power;

public interface FeManagedLoad extends FeRatedLoad {
    FePriority managedPriority();

    void setManagedPriority(FePriority tier);

    FeLock managedLock();

    void setManagedLock(FeLock lock);

    void setManagedShed(boolean shed);

    boolean isManagedShed();
}
