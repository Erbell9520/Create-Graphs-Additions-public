package com.createstats.netscan.scan;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldProbe implements KineticWalk.NeighbourSource {
    private final Level level;
    private final Map<BlockPos, List<BlockPos>> neighbourCache = new HashMap<>();

    public WorldProbe(Level level) {
        this.level = level;
    }

    public long networkIdAt(BlockPos pos) {
        final KineticBlockEntity kbe = kinetic(pos);
        if (kbe == null || kbe.network == null) {
            return 0L;
        }
        return kbe.network;
    }

    public int networkSizeAt(BlockPos pos) {
        final KineticBlockEntity kbe = kinetic(pos);
        if (kbe == null || !kbe.hasNetwork()) {
            return 0;
        }
        try {
            return kbe.getOrCreateNetwork().getSize();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public boolean isKinetic(BlockPos pos) {
        return kinetic(pos) != null;
    }

    private KineticBlockEntity kinetic(BlockPos pos) {
        if (this.level == null || pos == null || !this.level.isLoaded(pos)) {
            return null;
        }
        final BlockEntity be = this.level.getBlockEntity(pos);
        return be instanceof KineticBlockEntity kbe ? kbe : null;
    }

    @Override
    public boolean unloaded(BlockPos pos) {
        return this.level == null || !this.level.isLoaded(pos);
    }

    @Override
    public ScanNode probe(BlockPos pos) {
        final KineticBlockEntity kbe = kinetic(pos);
        if (kbe == null) {
            return null;
        }
        final BlockState state = this.level.getBlockState(pos);
        final String name = state.getBlock().getName().getString();
        final String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));

        final float speed = safe(kbe::getSpeed);
        final float coefficient = safe(kbe::calculateStressApplied);
        final float capacityCoefficient = safe(kbe::calculateAddedStressCapacity);
        final float generated = safe(kbe::getGeneratedSpeed);

        float stress = coefficient * Math.abs(speed);
        float capacity = capacityCoefficient * Math.abs(speed);
        if (kbe.hasNetwork()) {
            try {
                final KineticNetwork network = kbe.getOrCreateNetwork();
                stress = network.getActualStressOf(kbe);
                capacity = network.getActualCapacityOf(kbe);
            } catch (RuntimeException ignored) {
            }
        }

        final ScanNodeKind kind = classify(kbe, pos, generated, coefficient, capacityCoefficient);
        return new ScanNode(pos, name, id, kind, speed, stress, capacity, coefficient,
                kbe.isSource() || generated != 0.0f, kbe.isOverStressed(), kbe.source);
    }

    private ScanNodeKind classify(KineticBlockEntity kbe, BlockPos pos, float generated,
                                  float coefficient, float capacityCoefficient) {
        if (kbe.isSource() || generated != 0.0f || capacityCoefficient > 0.0f) {
            return ScanNodeKind.SOURCE;
        }
        if (kbe instanceof GaugeBlockEntity || kbe instanceof com.createstats.netscan.NetscanHost) {
            return ScanNodeKind.INSTRUMENT;
        }
        for (final BlockPos neighbour : neighbours(pos)) {
            if (Math.abs(Math.abs(ratio(pos, neighbour)) - 1.0f) > 1.0e-4f) {
                return ScanNodeKind.GEARING;
            }
        }
        if (coefficient > 0.0f) {
            return ScanNodeKind.MACHINE;
        }
        return ScanNodeKind.TRANSFER;
    }

    @Override
    public List<BlockPos> neighbours(BlockPos pos) {
        return this.neighbourCache.computeIfAbsent(pos.immutable(), this::findNeighbours);
    }

    private List<BlockPos> findNeighbours(BlockPos pos) {
        final KineticBlockEntity kbe = kinetic(pos);
        if (kbe == null) {
            return List.of();
        }
        final List<BlockPos> found = Propagator.connectedNeighbours(kbe);
        if (found != null) {
            return found;
        }
        return fallbackNeighbours(kbe, pos);
    }

    // generous candidates, strict acceptance. a false edge is worse than a missing one
    private List<BlockPos> fallbackNeighbours(KineticBlockEntity kbe, BlockPos pos) {
        final List<BlockPos> candidates = new ArrayList<>(14);
        for (final Direction direction : Direction.values()) {
            candidates.add(pos.relative(direction));
        }
        final BlockState state = this.level.getBlockState(pos);
        if (state.getBlock() instanceof IRotate rotate) {
            final Direction.Axis axis = rotate.getRotationAxis(state);
            for (final Direction a : Direction.values()) {
                if (a.getAxis() == axis) {
                    continue;
                }
                for (final Direction b : Direction.values()) {
                    if (b.getAxis() == axis || b.getAxis() == a.getAxis()) {
                        continue;
                    }
                    candidates.add(pos.relative(a).relative(b));
                }
            }
        }
        final List<BlockPos> connected = new ArrayList<>();
        for (final BlockPos candidate : candidates) {
            final KineticBlockEntity other = kinetic(candidate);
            if (other == null || connected.contains(candidate)) {
                continue;
            }
            try {
                if (RotationPropagator.isConnected(kbe, other)) {
                    connected.add(candidate.immutable());
                }
            } catch (RuntimeException ignored) {
            }
        }
        return connected;
    }

    @Override
    public float ratio(BlockPos from, BlockPos to) {
        final KineticBlockEntity a = kinetic(from);
        final KineticBlockEntity b = kinetic(to);
        if (a == null || b == null) {
            return 1.0f;
        }
        final float driving = safe(a::getTheoreticalSpeed);
        if (Math.abs(driving) > 1.0e-4f) {
            final Float conveyed = Propagator.conveyedSpeed(a, b);
            if (conveyed != null && conveyed != 0.0f) {
                return conveyed / driving;
            }
        }
        final Float modifier = Propagator.speedModifier(a, b);

        return modifier == null || modifier == 0.0f ? 1.0f : modifier;
    }

    private static float safe(FloatCall call) {
        try {
            return call.get();
        } catch (RuntimeException ignored) {
            return 0.0f;
        }
    }

    private interface FloatCall {
        float get();
    }

    private static final class Propagator {
        private static boolean initialised;
        private static Method connectedNeighbours;
        private static Method speedModifier;
        private static Method conveyedSpeed;

        private Propagator() {
        }

        private static synchronized void init() {
            if (initialised) {
                return;
            }
            initialised = true;
            for (final Method method : RotationPropagator.class.getDeclaredMethods()) {
                if (connectedNeighbours == null && method.getName().equals("getConnectedNeighbours")
                        && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    connectedNeighbours = method;
                } else if (speedModifier == null && method.getName().equals("getRotationSpeedModifier")
                        && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    speedModifier = method;
                } else if (conveyedSpeed == null && method.getName().equals("getConveyedSpeed")
                        && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    conveyedSpeed = method;
                }
            }
        }

        @SuppressWarnings("unchecked")
        static List<BlockPos> connectedNeighbours(KineticBlockEntity kbe) {
            init();
            if (connectedNeighbours == null) {
                return null;
            }
            try {
                final List<KineticBlockEntity> found =
                        (List<KineticBlockEntity>) connectedNeighbours.invoke(null, kbe);
                if (found == null) {
                    return null;
                }
                final List<BlockPos> positions = new ArrayList<>(found.size());
                for (final KineticBlockEntity other : found) {
                    if (other != null) {
                        positions.add(other.getBlockPos().immutable());
                    }
                }
                return positions;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        static Float speedModifier(KineticBlockEntity from, KineticBlockEntity to) {
            init();
            if (speedModifier == null) {
                return null;
            }
            try {
                return (Float) speedModifier.invoke(null, from, to);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        static Float conveyedSpeed(KineticBlockEntity from, KineticBlockEntity to) {
            init();
            if (conveyedSpeed == null) {
                return null;
            }
            try {
                return (Float) conveyedSpeed.invoke(null, from, to);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }
}
