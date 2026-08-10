package com.createstats.power;

/** [1] */
public interface IFeConsumer {

    /** The FE buffer and upkeep component this block entity owns. Never null. */
    FeConsumer feConsumer();

    /** [2] */
    default void onFePowerLost() {
    }
}
