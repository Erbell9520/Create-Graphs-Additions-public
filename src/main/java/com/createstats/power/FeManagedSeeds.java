package com.createstats.power;

import java.util.HashMap;
import java.util.Map;

public final class FeManagedSeeds {
    private final Map<Long, Integer> seeds = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private long boundaryKey = FeFlowGraph.NO_BOUNDARY;
    private int boundaryFlow;

    public void add(Map<Long, Integer> signedFlow, long anchorKey, int anchorSupply, long gameTime) {
        if (tick != gameTime) {
            tick = gameTime;
            seeds.clear();
            boundaryKey = FeFlowGraph.NO_BOUNDARY;
            boundaryFlow = 0;
        }
        int total = 0;
        if (signedFlow != null) {
            for (Map.Entry<Long, Integer> entry : signedFlow.entrySet()) {
                total += entry.getValue();
                seeds.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        if (anchorKey == FeFlowGraph.NO_BOUNDARY) {
            return;
        }

        final int supply = Math.max(0, anchorSupply);
        seeds.merge(anchorKey, -total - supply, Integer::sum);

        boundaryKey = anchorKey;
        boundaryFlow = Math.max(boundaryFlow, Math.abs(total) + supply);
    }

    public Map<Long, Integer> seeds() {
        return seeds;
    }

    public long boundaryKey() {
        return boundaryKey;
    }

    public int boundaryFlow() {
        return boundaryFlow;
    }
}
