package com.createstats.power;

public interface FeGridMember extends FeGridNode {
    boolean isGone(net.minecraft.world.level.Level level);

    void setLastFlow(int flow);

    default int lastFlow() {
        return 0;
    }

    void invalidateNetwork();

    @javax.annotation.Nullable
    default FeWireNetwork network() {
        return null;
    }

    default void adoptNetwork(FeWireNetwork network) {
    }

    @Override
    default int deliveryAllowance() {
        final FeWireNetwork grid = network();
        return grid == null ? FeGridSolver.NO_CEILING : grid.conductorAllowance(nodeKey());
    }

    @Override
    default void bookDelivery(int fe) {
        final FeWireNetwork grid = network();
        if (grid != null) {
            grid.bookConductorDelivery(nodeKey(), fe);
        }
    }
}
