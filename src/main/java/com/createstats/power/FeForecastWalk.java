package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class FeForecastWalk {
    private FeForecastWalk() {
    }

    public static final int MAX_DEPTH = 4;

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static final class Memo {
        private long tick = Long.MIN_VALUE;
        private boolean walking;
        private FeForecastRelay.Upstream value = FeForecastRelay.Upstream.NONE;
    }

    public static FeForecastRelay.Upstream behind(@Nullable Level level, Memo memo, BlockEntity self,
                                                  Supplier<List<FeWireNetwork>> grids,
                                                  Supplier<List<BlockEntity>> bare) {
        if (level == null || level.isClientSide()) {
            return FeForecastRelay.Upstream.NONE;
        }
        if (memo.walking) {
            return FeForecastRelay.Upstream.NONE;
        }
        final long now = level.getGameTime();
        if (memo.tick == now) {
            return memo.value;
        }
        final int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            return FeForecastRelay.Upstream.NONE;
        }
        memo.walking = true;
        depth[0]++;
        try {
            memo.value = walk(level, self, grids.get(), bare.get());
            memo.tick = now;
            return memo.value;
        } finally {
            depth[0]--;
            memo.walking = false;
        }
    }

    private static FeForecastRelay.Upstream walk(Level level, BlockEntity self, List<FeWireNetwork> grids,
                                                 List<BlockEntity> bare) {
        final int[] offsets = PduSupplySurvey.FORECAST_OFFSETS;
        final int[] totals = new int[offsets.length];
        final int[] sensitive = {0};
        final Set<BlockPos> seen = new HashSet<>();
        final boolean[] any = {false};
        for (FeWireNetwork grid : grids) {
            if (grid == null) {
                continue;
            }
            for (FeGridMember node : grid.members()) {
                final IEnergyStorage handler = node.attachedStorage();
                if (handler == null
                        || FeNodeRole.of(handler, node.transferRate()).kind() != FeNodeRole.Kind.GENERATOR) {
                    continue;
                }
                add(blockEntityOf(level, node), self, seen, totals, sensitive, any);
            }
        }
        for (BlockEntity be : bare) {
            add(be, self, seen, totals, sensitive, any);
        }
        return any[0] ? new FeForecastRelay.Upstream(totals, sensitive[0]) : FeForecastRelay.Upstream.NONE;
    }

    private static void add(@Nullable BlockEntity be, BlockEntity self, Set<BlockPos> seen, int[] totals,
                            int[] sensitive, boolean[] any) {
        if (be == null || be == self || be.isRemoved()
                || !(be instanceof FeForecastingSource source) || !seen.add(be.getBlockPos())) {
            return;
        }
        final int[] offsets = PduSupplySurvey.FORECAST_OFFSETS;
        for (int i = 0; i < offsets.length; i++) {
            totals[i] = FeRates.plus(totals[i], Math.max(0, source.potentialFePerTickIn(offsets[i])));
        }

        final int share = source.weatherSensitive()
                ? (be instanceof FeSupply supply ? Math.max(0, supply.potentialFePerTick())
                        : Math.max(0, source.potentialFePerTickIn(0)))
                : Math.max(0, source.relayedWeatherShareFePerTick());
        sensitive[0] = FeRates.plus(sensitive[0], share);
        any[0] = true;
    }

    @Nullable
    public static BlockEntity blockEntityOf(Level level, FeGridMember node) {
        if (node instanceof FeAttachment attachment) {
            return level.isLoaded(attachment.machine()) ? level.getBlockEntity(attachment.machine()) : null;
        }
        if (node instanceof com.createstats.blockentity.PowerConnectorBE connector) {
            final Direction facing = connector.getBlockState()
                    .hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                    ? connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING)
                    : Direction.UP;
            final BlockPos mount = connector.getBlockPos().relative(facing.getOpposite());
            return level.isLoaded(mount) ? level.getBlockEntity(mount) : null;
        }
        return node instanceof BlockEntity be ? be : null;
    }
}
