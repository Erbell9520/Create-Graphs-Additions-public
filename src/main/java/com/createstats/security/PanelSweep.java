package com.createstats.security;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.power.FeWireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PanelSweep {
    private PanelSweep() {
    }

    @Nullable
    public static HomeSecurityPanelBE nearest(@Nullable Level level, @Nullable FeWireNetwork network,
                                              BlockPos self, BlockPos anchor) {
        return nearest(level, network, self, anchor, null);
    }

    @Nullable
    public static HomeSecurityPanelBE nearest(@Nullable Level level, @Nullable FeWireNetwork network,
                                              BlockPos self, BlockPos anchor, @Nullable UUID remembered) {
        if (level == null || network == null || self == null) {
            return null;
        }
        final SecurityEndpoint key = SecurityIdentity.at(level, anchor == null ? self : anchor);
        final UUID bound = remembered != null && !SecurityIdentity.free(level, remembered, self)
                ? remembered : null;

        HomeSecurityPanelBE bestKnown = null;
        double knownDist = Double.MAX_VALUE;
        HomeSecurityPanelBE bestAny = null;
        double anyDist = Double.MAX_VALUE;

        for (long reached : network.reachedPositions()) {
            final BlockPos at = BlockPos.of(reached);
            if (!level.isLoaded(at)) {
                continue;
            }
            if (!(level.getBlockEntity(at) instanceof HomeSecurityPanelBE panel)) {
                continue;
            }
            if (bound != null) {
                final SecurityHousehold its = panel.household();
                if (its == null || !bound.equals(its.id())) {
                    continue;
                }
            }
            final double d = at.distSqr(self);

            if (closer(d, anyDist, at, bestAny)) {
                anyDist = d;
                bestAny = panel;
            }
            final SecurityHousehold house = panel.household();
            if (house != null && house.knows(key) && closer(d, knownDist, at, bestKnown)) {
                knownDist = d;
                bestKnown = panel;
            }
        }
        return bestKnown != null ? bestKnown : bestAny;
    }

    private static boolean closer(double candidate, double best, BlockPos at,
                                  @Nullable HomeSecurityPanelBE incumbent) {
        return candidate < best
                || (candidate == best && incumbent != null
                && at.asLong() < incumbent.getBlockPos().asLong());
    }
}
