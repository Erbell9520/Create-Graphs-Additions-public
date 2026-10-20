package com.createstats.power;

// solved perfectly and priced at nothing = a shipped bug. hence the ninth wiring site
public interface FeSupply {
    int potentialFePerTick();

    default int potentialFePerTickFor(long consumerPos) {
        return potentialFePerTick();
    }
}
