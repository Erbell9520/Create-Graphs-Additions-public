package com.createstats.power;

public record CouplerPushPlan(int direct, int network) {
    public int total() {
        return this.direct + this.network;
    }

    public static CouplerPushPlan of(int offer, int ownRoom, int networkRoom) {
        final int give = Math.max(0, offer);
        final int direct = Math.min(give, Math.max(0, ownRoom));
        final int network = Math.min(give - direct, Math.max(0, networkRoom));
        return new CouplerPushPlan(direct, network);
    }
}
