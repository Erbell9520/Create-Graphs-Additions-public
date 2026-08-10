package com.createstats.vccmk2;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/** [1] */
final class VccMk2Sensors {

    private static final int SCAN_PERIOD_TICKS = 20;
    private static final int SCAN_CHUNK_RADIUS = 8;
    private static final int MISS_THRESHOLD = 3;

    private final VccMk2BlockEntity vcc;
    /** Consecutive failed validations per sensor slot (for hysteresis). */
    private final int[] miss = new int[VccMk2BlockEntity.SENSOR_COUNT];
    /** Last reported state per slot, so a brief miss streak can keep a good link alive. */
    private final byte[] lastState = new byte[VccMk2BlockEntity.SENSOR_COUNT];
    private long lastScanTick = Long.MIN_VALUE;

    VccMk2Sensors(VccMk2BlockEntity vcc) {
        this.vcc = vcc;
        for (int i = 0; i < miss.length; i++) {
            miss[i] = MISS_THRESHOLD;
        }
    }

    /** Force the next {@link #maybeScan} to run immediately (used right after a manual bind changes). */
    void forceScanNow() {
        lastScanTick = Long.MIN_VALUE;
        for (int i = 0; i < miss.length; i++) {
            miss[i] = MISS_THRESHOLD - 1;
        }
    }

    /** Cadenced re-scan; a cheap no-op until due. Resolves each sensor and writes state back to the BE. */
    void maybeScan(long gameTime) {
        if (gameTime - lastScanTick < SCAN_PERIOD_TICKS && lastScanTick != Long.MIN_VALUE) {
            return;
        }
        lastScanTick = gameTime;
        Level level = vcc.getLevel();
        if (level == null) {
            return;
        }

        BlockPos[] autoPos = new BlockPos[VccMk2BlockEntity.SENSOR_COUNT];
        boolean[] need = new boolean[VccMk2BlockEntity.SENSOR_COUNT];
        boolean anyNeed = false;
        for (int i = 0; i < VccMk2BlockEntity.SENSOR_COUNT; i++) {
            // A manual bind masks auto-discovery for that slot, so the chosen sensor never silently swaps.
            need[i] = vcc.sensorManualPos(i) == null;
            anyNeed |= need[i];
        }
        if (anyNeed) {
            scanForSensors(level, vcc.getBlockPos(), need, autoPos);
        }

        byte[] state = new byte[VccMk2BlockEntity.SENSOR_COUNT];
        for (int i = 0; i < VccMk2BlockEntity.SENSOR_COUNT; i++) {
            BlockPos manual = vcc.sensorManualPos(i);
            BlockPos eff = manual != null ? manual : autoPos[i];
            boolean present = eff != null && matches(level, eff, i);
            if (present) {
                miss[i] = 0;
            } else {
                miss[i] = Math.min(miss[i] + 1, MISS_THRESHOLD);
            }
            boolean connected = present
                    || (lastState[i] == VccMk2BlockEntity.SENSOR_CONNECTED && miss[i] < MISS_THRESHOLD);
            if (connected) {
                state[i] = (byte) VccMk2BlockEntity.SENSOR_CONNECTED;
            } else if (manual != null) {
                // The player bound a sensor here, but it is no longer present.
                state[i] = (byte) VccMk2BlockEntity.SENSOR_LOST;
            } else {
                state[i] = (byte) VccMk2BlockEntity.SENSOR_NONE;
            }
            lastState[i] = state[i];
        }
        vcc.applySensorScan(state, autoPos);
    }

    private static boolean matches(Level level, BlockPos pos, int which) {
        BlockEntity be;
        try {
            be = SableBridge.getBlockEntityOnContraption(level, pos);
        } catch (RuntimeException | LinkageError ex) {
            be = null;
        }
        return be != null && matcher(which).test(be);
    }

    private static Predicate<BlockEntity> matcher(int which) {
        return switch (which) {
            case VccMk2BlockEntity.SENSOR_GIMBAL -> SableBridge::matchesGimbalSensor;
            case VccMk2BlockEntity.SENSOR_ALTITUDE -> SableBridge::matchesAltitudeSensor;
            case VccMk2BlockEntity.SENSOR_VELOCITY -> SableBridge::matchesVelocitySensor;
            case VccMk2BlockEntity.SENSOR_NAVTABLE -> SableBridge::matchesNavTable;
            default -> be -> false;
        };
    }

    /** Chunk-bounded scan for the nearest unbound sensor of each needed kind around {@code center}. */
    private static void scanForSensors(Level level, BlockPos center, boolean[] need, BlockPos[] out) {
        double[] bestDist = new double[VccMk2BlockEntity.SENSOR_COUNT];
        for (int i = 0; i < bestDist.length; i++) {
            bestDist[i] = Double.MAX_VALUE;
        }
        try {
            int cxc = center.getX() >> 4;
            int czc = center.getZ() >> 4;
            int r = SCAN_CHUNK_RADIUS;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    var chunk = level.getChunkSource().getChunk(cxc + dx, czc + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                        BlockEntity be = chunk.getBlockEntity(bePos);
                        if (be == null) {
                            continue;
                        }
                        for (int i = 0; i < VccMk2BlockEntity.SENSOR_COUNT; i++) {
                            if (!need[i] || !matcher(i).test(be)) {
                                continue;
                            }
                            double d = bePos.distSqr(center);
                            if (d < bestDist[i]) {
                                bestDist[i] = d;
                                out[i] = bePos.immutable();
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            // Never let a chunk-iteration glitch break the tick.
        }
    }
}
