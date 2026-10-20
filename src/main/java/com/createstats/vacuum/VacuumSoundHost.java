package com.createstats.vacuum;

public interface VacuumSoundHost {
    boolean fanRunning();

    boolean isSoundPlaying();

    void setSoundPlaying(boolean playing);

    boolean isRemoved();
}
