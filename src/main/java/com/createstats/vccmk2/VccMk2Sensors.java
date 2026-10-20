package com.createstats.vccmk2;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.function.Predicate;

// a miss streak before LOST, chunk churn shouldn't flicker it
final class VccMk2Sensors {
    private static final int SCAN_PERIOD_TICKS = 20;
    private static final int SCAN_CHUNK_RADIUS = 8;
    private static final int MISS_THRESHOLD = 3;

    private final VccMk2BlockEntity vcc;

    private final int[] miss = new int[VccMk2BlockEntity.SENSOR_COUNT];

    private final byte[] lastState = new byte[VccMk2BlockEntity.SENSOR_COUNT];
    private long lastScanTick = Long.MIN_VALUE;

    VccMk2Sensors(VccMk2BlockEntity vcc) {
        this.vcc = vcc;
        for (int i = 0; i < miss.length; i++) {
            miss[i] = MISS_THRESHOLD;
        }
    }

    void forceScanNow() {
        lastScanTick = Long.MIN_VALUE;
        for (int i = 0; i < miss.length; i++) {
            miss[i] = MISS_THRESHOLD - 1;
        }
    }

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
        }
    }
}
