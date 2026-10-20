package com.createstats.vccmk3.avionics;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// scan the controller's own sub-level so it finds this ship's sensors, not the neighbour's
public final class SensorDiscovery {
    public enum Kind {
        GIMBAL("Gimbal Sensor", "attitude"),
        ALTITUDE("Altitude Sensor", "altitude and air pressure"),
        VELOCITY("Velocity Sensor", "speed"),
        OPTICAL("Optical Sensor", "terrain clearance"),
        NAV_TABLE("Nav Table", "a navigation target"),
        PATHFINDER("Pathfinder Computer", "a planned route");

        private final String label;
        private final String provides;

        Kind(String label, String provides) {
            this.label = label;
            this.provides = provides;
        }

        public String label() {
            return label;
        }

        public String provides() {
            return provides;
        }
    }

    public static final int SCAN_PERIOD_TICKS = 20;

    public static final int MISS_THRESHOLD = 3;

    public static final int SCAN_CHUNK_RADIUS = 4;

    private final Map<Kind, BlockPos> discovered = new EnumMap<>(Kind.class);
    private final Map<Kind, BlockPos> manual = new EnumMap<>(Kind.class);
    private final Map<Kind, Integer> misses = new EnumMap<>(Kind.class);
    private boolean warmedUp;

    public void scan(@Nullable Level level, BlockPos origin) {
        if (level == null) return;

        for (Kind kind : Kind.values()) {
            BlockPos manualPos = manual.get(kind);
            if (manualPos != null) {
                if (!matches(level, manualPos, kind)) {
                    misses.merge(kind, 1, Integer::sum);
                } else {
                    misses.put(kind, 0);
                }
                continue;
            }

            BlockPos nearest = findNearest(level, origin, kind);
            if (nearest != null) {
                discovered.put(kind, nearest);
                misses.put(kind, 0);
            } else if (discovered.containsKey(kind)) {
                int missed = misses.merge(kind, 1, Integer::sum);
                if (missed >= MISS_THRESHOLD) {
                    discovered.remove(kind);
                    misses.put(kind, 0);
                }
            }
        }
        warmedUp = true;
    }

    @Nullable
    public BlockPos effective(Kind kind) {
        BlockPos manualPos = manual.get(kind);
        if (manualPos != null) return manualPos;
        return discovered.get(kind);
    }

    public boolean isLive(Level level, Kind kind) {
        BlockPos pos = effective(kind);
        return pos != null && matches(level, pos, kind);
    }

    public boolean isManual(Kind kind) {
        return manual.containsKey(kind);
    }

    public void bind(Kind kind, @Nullable BlockPos pos) {
        if (pos == null) {
            manual.remove(kind);
        } else {
            manual.put(kind, pos.immutable());
        }
        misses.put(kind, 0);
    }

    public boolean isWarmedUp() {
        return warmedUp;
    }

    public List<Kind> missing(Level level) {
        List<Kind> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (!isLive(level, kind)) out.add(kind);
        }
        return out;
    }

    @Nullable
    private BlockPos findNearest(Level level, BlockPos origin, Kind kind) {
        Class<?> type = classFor(kind);
        if (type == null) return null;
        List<BlockPos> found = SableBridge.findBlocksOfType(level, origin, type, SCAN_CHUNK_RADIUS);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : found) {
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static Class<?> classFor(Kind kind) {
        return switch (kind) {
            case GIMBAL -> SableBridge.gimbalSensorClass();
            case ALTITUDE -> SableBridge.altitudeSensorClass();
            case VELOCITY -> SableBridge.velocitySensorClass();
            case OPTICAL -> SableBridge.opticalSensorClass();
            case NAV_TABLE -> SableBridge.navTableClass();

            case PATHFINDER -> com.createstats.pathfinder.blockentity.PathfinderComputerBE.class;
        };
    }

    private static boolean matches(Level level, BlockPos pos, Kind kind) {
        if (!level.isLoaded(pos)) return false;
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (be == null) return false;
        Predicate<BlockEntity> predicate = switch (kind) {
            case GIMBAL -> SableBridge::matchesGimbalSensor;
            case ALTITUDE -> SableBridge::matchesAltitudeSensor;
            case VELOCITY -> SableBridge::matchesVelocitySensor;
            case OPTICAL -> SableBridge::matchesOpticalSensor;
            case NAV_TABLE -> SableBridge::matchesNavTable;
            case PATHFINDER -> candidate ->
                    candidate instanceof com.createstats.pathfinder.blockentity.PathfinderComputerBE;
        };
        return predicate.test(be);
    }

    public net.minecraft.nbt.CompoundTag toNbt() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        for (Map.Entry<Kind, BlockPos> entry : manual.entrySet()) {
            tag.putLong(entry.getKey().name(), entry.getValue().asLong());
        }
        return tag;
    }

    public void fromNbt(@Nullable net.minecraft.nbt.CompoundTag tag) {
        manual.clear();
        if (tag == null) return;
        for (Kind kind : Kind.values()) {
            if (tag.contains(kind.name())) manual.put(kind, BlockPos.of(tag.getLong(kind.name())));
        }
    }
}
