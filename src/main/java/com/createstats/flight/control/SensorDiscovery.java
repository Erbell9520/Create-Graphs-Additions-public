package com.createstats.flight.control;

import com.createstats.flight.SensorRequirement;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/** [1] */
public final class SensorDiscovery {

    public static final int SCAN_PERIOD_TICKS = 20;
    public static final int SCAN_CHUNK_RADIUS = 8;
    public static final int MISS_THRESHOLD    = 3;

    private final FlightControlComputerBE fcc;

    /** Auto-discovered (closest) sensor positions in the FCC's sub-level. Never persisted. */
    @Nullable private BlockPos autoGimbal;
    @Nullable private BlockPos autoAltitude;
    @Nullable private BlockPos autoVelocity;
    @Nullable private BlockPos autoNavTable;

    /** Per-requirement consecutive miss counts for hysteresis. */
    private final EnumMap<SensorRequirement, Integer> missCount = new EnumMap<>(SensorRequirement.class);

    /** Currently validated availability set. Mutated on every scan tick. */
    private final EnumSet<SensorRequirement> validated = EnumSet.noneOf(SensorRequirement.class);

    private long lastScanTick = Long.MIN_VALUE;
    /** Number of scans completed. Used by {@link #isWarmedUp()} to suppress mode-fallback
     *  before hysteresis has had a chance to seed itself on freshly-loaded FCCs. */
    private int scansCompleted = 0;

    public SensorDiscovery(FlightControlComputerBE fcc) {
        this.fcc = fcc;
        for (SensorRequirement r : SensorRequirement.values()) missCount.put(r, MISS_THRESHOLD);
    }

    /** Cadenced re-scan. Cheap; no-op if not yet due. */
    public void maybeScan(long gameTime) {
        if (gameTime - lastScanTick < SCAN_PERIOD_TICKS && lastScanTick != Long.MIN_VALUE) return;
        lastScanTick = gameTime;
        Level level = fcc.getLevel();
        if (level == null) return;
        BlockPos center = fcc.getBlockPos();
        boolean sableActive = isSableActive();

        // [2]
        boolean needGimbal   = !sableActive && fcc.getGimbalPos()         == null;
        boolean needAltitude = !sableActive && fcc.getAltitudeSensorPos() == null;
        boolean needVelocity = !sableActive && fcc.getVelocitySensorPos() == null;
        boolean needNavTable = fcc.getNavTablePos()        == null;

        ClosestTracker gT = new ClosestTracker(center);
        ClosestTracker aT = new ClosestTracker(center);
        ClosestTracker vT = new ClosestTracker(center);
        ClosestTracker nT = new ClosestTracker(center);

        if (needGimbal || needAltitude || needVelocity || needNavTable) {
            try {
                int cxc = center.getX() >> 4;
                int czc = center.getZ() >> 4;
                int r = SCAN_CHUNK_RADIUS;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        var chunk = level.getChunkSource().getChunk(cxc + dx, czc + dz, false);
                        if (chunk == null) continue;
                        for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                            BlockEntity be = chunk.getBlockEntity(bePos);
                            if (be == null) continue;
                            if (needGimbal   && SableBridge.matchesGimbalSensor(be))   gT.consider(bePos);
                            if (needAltitude && SableBridge.matchesAltitudeSensor(be)) aT.consider(bePos);
                            if (needVelocity && SableBridge.matchesVelocitySensor(be)) vT.consider(bePos);
                            if (needNavTable && SableBridge.matchesNavTable(be))       nT.consider(bePos);
                        }
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                // Defensive: never let chunk iteration glitch break the FCC.
            }
        }

        autoGimbal   = needGimbal   ? gT.best() : null;
        autoAltitude = needAltitude ? aT.best() : null;
        autoVelocity = needVelocity ? vT.best() : null;
        autoNavTable = needNavTable ? nT.best() : null;

        // [3]
        revalidateByPredicate(SensorRequirement.GIMBAL,    effectiveGimbalPos(),   SableBridge::matchesGimbalSensor,   sableActive);
        revalidateByPredicate(SensorRequirement.ALTITUDE,  effectiveAltitudePos(), SableBridge::matchesAltitudeSensor, sableActive);
        revalidateByPredicate(SensorRequirement.VELOCITY,  effectiveVelocityPos(), SableBridge::matchesVelocitySensor, sableActive);
        revalidateByPredicate(SensorRequirement.NAV_TABLE, effectiveNavTablePos(), SableBridge::matchesNavTable,       false);

        if (scansCompleted < MISS_THRESHOLD + 1) scansCompleted++;
    }

    private boolean isSableActive() {
        try {
            Object subLevelAccess = SableBridge.subLevelAccessOf(fcc);
            return subLevelAccess != null && SableBridge.bodyOrientation(subLevelAccess) != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** [4] */
    public void forceScanNow() {
        lastScanTick = Long.MIN_VALUE;
        for (SensorRequirement r : SensorRequirement.values()) missCount.put(r, MISS_THRESHOLD - 1);
    }

    /** Closest-block tracker with deterministic tie-break by BlockPos.asLong(). */
    private static final class ClosestTracker {
        private final BlockPos center;
        @Nullable private BlockPos best;
        private long bestDist = Long.MAX_VALUE;
        private long bestKey  = Long.MAX_VALUE;
        ClosestTracker(BlockPos c) { this.center = c; }
        void consider(BlockPos p) {
            long dx = p.getX() - center.getX();
            long dy = p.getY() - center.getY();
            long dz = p.getZ() - center.getZ();
            long d2 = dx*dx + dy*dy + dz*dz;
            long k  = p.asLong();
            if (d2 < bestDist || (d2 == bestDist && k < bestKey)) {
                bestDist = d2; bestKey = k; best = p.immutable();
            }
        }
        @Nullable BlockPos best() { return best; }
    }

    private void revalidateByPredicate(SensorRequirement req, @Nullable BlockPos pos,
                                        Predicate<BlockEntity> matcher, boolean sableValidated) {
        // [5]
        boolean ok = sableValidated;
        if (!ok && pos != null) {
            Level level = fcc.getLevel();
            if (level != null) {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
                ok = be != null && matcher.test(be);
            }
        }
        if (ok) {
            missCount.put(req, 0);
            validated.add(req);
        } else {
            int m = missCount.get(req) + 1;
            missCount.put(req, m);
            if (m >= MISS_THRESHOLD) validated.remove(req);
        }
    }

    @Nullable
    private static BlockPos closestOfClass(Level level, BlockPos center, @Nullable Class<?> cls) {
        // [6]
        if (cls == null) return null;
        List<BlockPos> hits = SableBridge.findBlocksOfType(level, center, cls, SCAN_CHUNK_RADIUS);
        if (hits.isEmpty()) return null;
        ClosestTracker t = new ClosestTracker(center);
        for (BlockPos p : hits) t.consider(p);
        return t.best();
    }

    // effective position resolution: manual override beats auto

    @Nullable
    public BlockPos effectiveGimbalPos() {
        BlockPos m = fcc.getGimbalPos();         // manual / persisted
        return m != null ? m : autoGimbal;
    }

    @Nullable
    public BlockPos effectiveAltitudePos() {
        BlockPos m = fcc.getAltitudeSensorPos();
        return m != null ? m : autoAltitude;
    }

    @Nullable
    public BlockPos effectiveVelocityPos() {
        BlockPos m = fcc.getVelocitySensorPos();
        return m != null ? m : autoVelocity;
    }

    @Nullable
    public BlockPos effectiveNavTablePos() {
        BlockPos m = fcc.getNavTablePos();
        return m != null ? m : autoNavTable;
    }

    /** True iff the sensor has been seen for {@code MISS_THRESHOLD-1} or fewer consecutive failed scans. */
    public boolean isAvailable(SensorRequirement req) {
        return validated.contains(req);
    }

    public boolean hasGimbal()    { return validated.contains(SensorRequirement.GIMBAL); }
    public boolean hasAltitude()  { return validated.contains(SensorRequirement.ALTITUDE); }
    public boolean hasVelocity()  { return validated.contains(SensorRequirement.VELOCITY); }
    public boolean hasNavTable()  { return validated.contains(SensorRequirement.NAV_TABLE); }

    // Whether each sensor position was set manually (vs auto-discovered).
    public boolean isGimbalManual()   { return fcc.getGimbalPos()         != null; }
    public boolean isAltitudeManual() { return fcc.getAltitudeSensorPos() != null; }
    public boolean isVelocityManual() { return fcc.getVelocitySensorPos() != null; }
    public boolean isNavTableManual() { return fcc.getNavTablePos()       != null; }

    /** Read-only set of currently-validated requirements (defensive copy). */
    public EnumSet<SensorRequirement> validatedSet() {
        return EnumSet.copyOf(validated.isEmpty() ? EnumSet.noneOf(SensorRequirement.class) : validated);
    }

    /** [7] */
    public boolean isWarmedUp() {
        return scansCompleted >= MISS_THRESHOLD + 1;
    }

    public void reset() {
        autoGimbal = autoAltitude = autoVelocity = autoNavTable = null;
        validated.clear();
        for (SensorRequirement r : SensorRequirement.values()) missCount.put(r, MISS_THRESHOLD);
        lastScanTick = Long.MIN_VALUE;
        scansCompleted = 0;
    }
}
