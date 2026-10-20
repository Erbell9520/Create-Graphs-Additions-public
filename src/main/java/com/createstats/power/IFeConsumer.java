package com.createstats.power;

public interface IFeConsumer {
    FeConsumer feConsumer();

    default void onFePowerLost() {
    }
}
