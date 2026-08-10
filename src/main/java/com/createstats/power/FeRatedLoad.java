package com.createstats.power;

/** [1] */
public interface FeRatedLoad {

    /** [2] */
    int ratedDrawFePerTick();

    /** What it actually spent on the last server tick. Zero when it went unserved and stalled. */
    int measuredDrawFePerTick();
}
