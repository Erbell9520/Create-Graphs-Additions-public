package com.createstats.power;

/** [1] */
public interface FeGridMember extends FeGridNode {

    /** True when this member has been removed or belongs to a different level, so it must be dropped. */
    boolean isGone(net.minecraft.world.level.Level level);

    /** Records the signed FE per tick that moved between this node and its attached block. */
    void setLastFlow(int flow);

    /**
     * Forces this member's network to be rediscovered on its next tick. Called when a neighbour's
     * topology changes, so both sides of a switch rebuild the instant its contacts move.
     */
    void invalidateNetwork();
}
