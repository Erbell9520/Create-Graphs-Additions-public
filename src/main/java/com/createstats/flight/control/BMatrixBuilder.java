package com.createstats.flight.control;

import com.createstats.CreateStats;
import com.createstats.flight.FccBinding;
import com.createstats.flight.FlightConfig;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the 6×N control-effectiveness matrix (B) from the current contraption
 * geometry and live actuator state.
 *
 * <h2>Frame conventions (MUST hold for B to be valid)</h2>
 * <ul>
 *   <li>{@link FccBinding#prop()} is the propeller-bearing block position in
 *       <b>sub-level (body) local</b> integer coordinates. The applied force
 *       point is taken at the block centre: {@code propPos + (0.5, 0.5, 0.5)}.</li>
 *   <li>{@link SableBridge#centerOfMassLocal(Object)} is in <b>body local</b>.</li>
 *   <li>{@link SableBridge.PropData#thrustDirLocal()} is in <b>body local</b>;
 *       for gyroscopic propellers we ASSUME the local thrust direction already
 *       reflects the active {@code tiltQuat} (Aeronautics applies tilt before
 *       publishing {@code thrustDirection}). If runtime testing disproves this,
 *       set {@link #FORCE_APPLY_GYRO_TILT} to {@code true} so the builder
 *       multiplies by {@code tiltQuat} on top.</li>
 * </ul>
 *
 * <p>Because positions, the thrust direction and the CoM are all in the body
 * frame, {@code r × F} produces a body-frame torque directly, no use of
 * {@link SableBridge#bodyOrientation(Object)} is required for B itself.
 * {@code bodyOrientation} is only consulted for the world-up diagnostic.</p>
 *
 * <h2>Effective gain {@code k} (per unit RPM)</h2>
 * <pre>
     * k = sign(thrustSigned / governorAppliedRpm when observable, otherwise +1)
 *     * adaptiveGain(sailPower, observed thrust/rpm)
 *
 * F_body = k * thrustDirLocal
 * r_body = (propPos + 0.5) - CoM_local
     * T_body = r_body × F_body + (yawFactor * |k| * ratio) * thrustDirLocal
 * column_i = [Fx, Fy, Fz, Tx, Ty, Tz]
 * </pre>
 */
public final class BMatrixBuilder {

    /** Minimum |RPM| to trust the dynamic {@code thrust/rpm} ratio. Below this we use the analytic seed. */
    public static final double RPM_DYNAMIC_THRESHOLD = 2.0;

    /**
     * Minimum |RPM| required before live telemetry may establish or flip the prop's signed gain.
     * Near zero, Aeronautics thrust snapshots jitter enough to invert {@code thrust/rpm} even when
     * the mechanical lift sign has not changed, which destabilizes the B matrix.
     */
    public static final double RPM_SIGN_UPDATE_THRESHOLD = 24.0;

    /** Sane upper bound on |k| (Newtons per RPM), guards against transient Aeronautics glitches. */
    public static final double K_HARD_CAP = 1.0e4;

    /** Analytic seed coefficient when prop is at rest: {@code k = sailPower * STATIC_K_PER_SAIL}. */
    public static final double STATIC_K_PER_SAIL = 0.25;
    /** Blend factor for live thrust/RPM adaptation. Small value avoids mid-flight gain jumps. */
    private static final double GAIN_EWMA_ALPHA = 0.08;
    private static final Map<Long, GainEstimate> GAIN_ESTIMATES = new HashMap<>();

    /** Evict a single prop's gain estimate (call when a single prop is recalibrated). */
    public static synchronized void evictGainEstimate(long propPosKey) {
        GAIN_ESTIMATES.remove(propPosKey);
    }

    /**
     * Evict all cached gain estimates so that stale (possibly sign-inverted) estimates from a
     * previous session do not seed the live EWMA. Retained for callers that reset the readiness
     * diagnostic; the old per-tick control loop and calibration sweep that once called it are gone.
     */
    public static synchronized void clearAllGainEstimates() {
        GAIN_ESTIMATES.clear();
    }

    /** Toggle if runtime testing shows {@code thrustDirLocal} does NOT include gyro tilt. */
    public static volatile boolean FORCE_APPLY_GYRO_TILT = false;

    /** Per-second diagnostic logging for B-matrix build (per-prop k/sign/source + vertical auth). */
    public static volatile boolean DIAG_LOG = true;
    private static long lastDiagTickMs = 0L;

    private BMatrixBuilder() {}

    /**
     * Build the B matrix for the given FCC. Returns a usable result whenever any
     * propeller binding resolved to a live BE; otherwise returns
     * {@link BMatrix#unable(String)}.
     */
    public static BMatrix build(Level level, BlockEntity fccBe, List<FccBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) return BMatrix.unable("noBindings");

        Object sla = SableBridge.subLevelAccessOf(fccBe);
        if (sla == null) return BMatrix.unable("notOnContraption");

        Vector3d com = SableBridge.centerOfMassLocal(sla);
        if (com == null) return BMatrix.unable("noCenterOfMass");

        Quaterniond bodyToWorld = SableBridge.bodyOrientation(sla);

        Vector3d gravUpBody = computeBodyUpFromGravity(fccBe, bodyToWorld);
        boolean gravityFallback = (gravUpBody == null);
        if (gravityFallback) gravUpBody = new Vector3d(0, 1, 0);

        int n = bindings.size();
        Matrix6xN.Builder mb = new Matrix6xN.Builder(n);
        double[] verticalAuth = new double[n];
        List<BMatrix.Diagnostic> diags = new ArrayList<>(n);
        int validColumns = 0;

        // Stable order for the structural fingerprint, sort by prop pos then governor pos.
        List<FccBinding> sorted = new ArrayList<>(bindings);
        sorted.sort(Comparator
                .comparingLong((FccBinding b) -> b.prop().asLong())
                .thenComparingLong(b -> b.governor().asLong()));

        // Snapshot the gyro flag once so a mid-loop toggle can't produce a mixed matrix.
        final boolean applyGyroTilt = FORCE_APPLY_GYRO_TILT;

        for (int i = 0; i < n; i++) {
            FccBinding binding = bindings.get(i);
            BlockPos propPos = binding.prop();
            BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, propPos);

            if (propBe == null) {
                mb.zeroColumn(i);
                verticalAuth[i] = 0.0;
                diags.add(new BMatrix.Diagnostic(i, false, "noPropBE", 0.0, false, false, 0.0, 0.0));
                continue;
            }

            SableBridge.PropData pd = SableBridge.readProp(propBe);
            if (pd == null) {
                mb.zeroColumn(i);
                verticalAuth[i] = 0.0;
                diags.add(new BMatrix.Diagnostic(i, false, "noPropData", 0.0, false, false, 0.0, 0.0));
                continue;
            }

            Vector3d dir = pd.thrustDirLocal();
            if (dir == null || !isFinite(dir)) {
                mb.zeroColumn(i);
                verticalAuth[i] = 0.0;
                diags.add(new BMatrix.Diagnostic(i, false, "badThrustDir", 0.0, false, false, 0.0, 0.0));
                continue;
            }
            // Defensive copy, never mutate bridge-returned vector.
            Vector3d dirLocal = new Vector3d(dir);

            // Optional escape hatch: apply gyro tilt explicitly if the bridge data is
            // empirically untilted. Snapshotted at top of build() so a mid-loop toggle
            // can't produce a mixed matrix.
            if (applyGyroTilt && pd.isGyro() && pd.tiltQuat() != null) {
                org.joml.Vector3f tmp = new org.joml.Vector3f(
                        (float) dirLocal.x, (float) dirLocal.y, (float) dirLocal.z);
                pd.tiltQuat().transform(tmp);
                dirLocal.set(tmp.x(), tmp.y(), tmp.z());
            }

            // Normalize. If the published direction is zero-length (rare), skip.
            double dirLen = dirLocal.length();
            if (!Double.isFinite(dirLen) || dirLen < 1.0e-9) {
                mb.zeroColumn(i);
                verticalAuth[i] = 0.0;
                diags.add(new BMatrix.Diagnostic(i, false, "zeroDir", 0.0, false, false, 0.0, 0.0));
                continue;
            }
            dirLocal.div(dirLen);

             GainResult gain = adaptiveGain(level, propPos, binding, pd);
            double k = gain.k();
            boolean staticFallback = gain.staticFallback();
            if (!Double.isFinite(k)) k = 0.0;
            if (k > K_HARD_CAP) k = K_HARD_CAP;
            if (k < -K_HARD_CAP) k = -K_HARD_CAP;

            // Force application point, block centre.
            Vector3d propLocal = new Vector3d(
                    propPos.getX() + 0.5,
                    propPos.getY() + 0.5,
                    propPos.getZ() + 0.5);
            Vector3d r = new Vector3d(propLocal).sub(com);

            Vector3d F = new Vector3d(dirLocal).mul(k);
            Vector3d T = new Vector3d(r).cross(F);
            double yawFactor = finiteOrZero(binding.yawFactor());
            double reactionTorquePerRpm = yawFactor * Math.abs(k) * FlightConfig.REACTION_TORQUE_TO_THRUST_RATIO;
            if (reactionTorquePerRpm != 0.0 && Double.isFinite(reactionTorquePerRpm)) {
                T.add(new Vector3d(dirLocal).mul(reactionTorquePerRpm));
            } else {
                reactionTorquePerRpm = 0.0;
            }

            if (!isFinite(F) || !isFinite(T)) {
                mb.zeroColumn(i);
                verticalAuth[i] = 0.0;
                diags.add(new BMatrix.Diagnostic(i, false, "nonFiniteColumn", k, staticFallback,
                        gain.usedCalibration(), yawFactor, reactionTorquePerRpm));
                continue;
            }

            mb.setColumn(i, F.x, F.y, F.z, T.x, T.y, T.z);
            double va = F.dot(gravUpBody);
            verticalAuth[i] = Double.isFinite(va) ? va : 0.0;
            diags.add(new BMatrix.Diagnostic(i, true, null, k, staticFallback,
                    gain.usedCalibration(), yawFactor, reactionTorquePerRpm));
            validColumns++;
        }

        // Structural fingerprint, separate pass over the SORTED bindings so reorderings
        // of the input list do not change the hash. Includes prop pos, governor pos,
        // sail-power bucket (quantized 0.01), actuator class name, and isGyro flag.
        long fp = 0xcbf29ce484222325L; // canonical FNV-1a 64 offset basis
        final long FNV_PRIME = 1099511628211L;
        for (FccBinding sb : sorted) {
            fp = mix(fp, FNV_PRIME, sb.prop().asLong());
            fp = mix(fp, FNV_PRIME, sb.governor().asLong());
            fp = mix(fp, FNV_PRIME, sb.calibratedGainValid() ? quantize(sb.calibratedK(), 10000.0) : 0L);
            fp = mix(fp, FNV_PRIME, quantize(sb.yawFactor(), 1000.0));
            BlockEntity sbBe = SableBridge.getBlockEntityOnContraption(level, sb.prop());
            int sailBucket = 0;
            int classHash = 0;
            int gyroFlag = 0;
            if (sbBe != null) {
                classHash = sbBe.getClass().getName().hashCode();
                SableBridge.PropData sbPd = SableBridge.readProp(sbBe);
                if (sbPd != null) {
                    sailBucket = (int) Math.round(Math.max(0f, sbPd.sailPower()) * 100f);
                    gyroFlag = sbPd.isGyro() ? 1 : 0;
                }
            }
            fp = mix(fp, FNV_PRIME, sailBucket);
            fp = mix(fp, FNV_PRIME, classHash);
            fp = mix(fp, FNV_PRIME, gyroFlag);
        }

        if (validColumns == 0) return BMatrix.unable("noValidProps");

        if (DIAG_LOG) {
            long now = System.currentTimeMillis();
            if (now - lastDiagTickMs >= 1000L) {
                lastDiagTickMs = now;
                double sumVA = 0.0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    BMatrix.Diagnostic d = diags.get(i);
                    sumVA += verticalAuth[i];
                    sb.append(String.format("[%d k=%.4f va=%.2f cal=%s static=%s] ",
                            i, d.effectiveK(), verticalAuth[i],
                            d.usedCalibration(), d.usedStaticFallback()));
                }
                CreateStats.LOGGER.info(
                        "[FCC diag] gravityFallback={} sumVerticalAuth={} props={} {}",
                        gravityFallback, sumVA, n, sb);
            }
        }

        Matrix6xN m = mb.build();
        return new BMatrix(m, verticalAuth, m.rowNorms(), fp, gravityFallback, diags, null);
    }

    /**
     * gravityUp_body = inverse(bodyOrientation) * (-normalize(gravityWorld)).
     * Returns null if gravity OR orientation is unavailable, caller falls back to
     * body +Y and marks {@code gravityFallbackUsed}. Returning a world-frame up
     * here would silently mis-credit vertical authority on tilted craft.
     */
    @Nullable
    private static Vector3d computeBodyUpFromGravity(BlockEntity be, @Nullable Quaterniond bodyToWorld) {
        Vector3d gWorld = SableBridge.gravityWorld(be);
        if (gWorld == null) return null;
        double gLen = gWorld.length();
        if (!Double.isFinite(gLen) || gLen < 1.0e-9) return null;
        if (bodyToWorld == null) return null;
        Vector3d up = new Vector3d(gWorld).div(-gLen);
        Quaterniond inv = new Quaterniond(bodyToWorld).conjugate();
        inv.transform(up);
        if (!isFinite(up)) return null;
        return up;
    }

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private record GainEstimate(double magnitude, double sign) {}
    private record GainResult(double k, boolean staticFallback, boolean usedCalibration) {}

    private static GainResult adaptiveGain(Level level, BlockPos propPos, FccBinding binding, SableBridge.PropData pd) {
        double sail = Math.max(0.0, pd.sailPower());
        double analytic = Math.max(0.0, sail * STATIC_K_PER_SAIL);
        double rpm = governorAppliedRpm(level, binding);
        double thrust = pd.thrustSigned();
        long key = propPos.asLong();

        GainEstimate est = GAIN_ESTIMATES.get(key);
        boolean usedCalibration = false;
        double magnitude = est != null ? est.magnitude() : analytic;
        double sign = est != null ? est.sign() : 1.0;
        double calibratedMag = 0.0;
        double calibratedSign = 0.0;
        if (binding.calibratedGainValid() && Double.isFinite(binding.calibratedK())
                && Math.abs(binding.calibratedK()) > 1.0e-6) {
            double calibratedK = clamp(binding.calibratedK(), -K_HARD_CAP, K_HARD_CAP);
            calibratedMag = Math.abs(calibratedK);
            calibratedSign = Math.signum(calibratedK);
            if (calibratedSign == 0.0) calibratedSign = 1.0;
            magnitude = calibratedMag;
            sign = calibratedSign;
            usedCalibration = true;
            GAIN_ESTIMATES.put(key, new GainEstimate(magnitude, sign));
        }
        boolean staticFallback = true;

        if (Math.abs(rpm) >= RPM_DYNAMIC_THRESHOLD && Double.isFinite(thrust)) {
            double observed = thrust / rpm;
            double observedMag = Math.abs(observed);
            if (Double.isFinite(observed) && observedMag > 1.0e-6) {
                double lo = analytic > 1.0e-6 ? analytic * 0.20 : 1.0e-4;
                double hi = analytic > 1.0e-6 ? analytic * 5.00 : K_HARD_CAP;
                observedMag = clamp(observedMag, lo, hi);
                if (usedCalibration) {
                    // Hard-lock sign to calibrated value; never let live signum invert it.
                    // Seed EWMA from calibrated magnitude when the cache was just evicted
                    // so the first live tick does not discard calibration data.
                    double seedMag = calibratedMag;
                    double blendMag = est == null
                            ? seedMag
                            : (1.0 - GAIN_EWMA_ALPHA) * magnitude + GAIN_EWMA_ALPHA * observedMag;
                    magnitude = blendMag;
                    // sign already = calibratedSign; do NOT overwrite.
                    // usedCalibration stays true.
                } else {
                    double observedSign = Math.signum(observed);
                    // Prop lift sign is a structural property of the binding, not something that
                    // should flip because thrust telemetry is noisy while the governor dithers
                    // through zero. Once learned, only allow a sign update at a clearly driven RPM.
                    if (observedSign != 0.0 && (est == null || Math.abs(rpm) >= RPM_SIGN_UPDATE_THRESHOLD)) {
                        sign = observedSign;
                    }
                    magnitude = est == null
                            ? observedMag
                            : (1.0 - GAIN_EWMA_ALPHA) * magnitude + GAIN_EWMA_ALPHA * observedMag;
                }
                staticFallback = false;
                GAIN_ESTIMATES.put(key, new GainEstimate(magnitude, sign));
            }
        }

        if (!Double.isFinite(magnitude) || magnitude <= 0.0) magnitude = analytic;
        if (!Double.isFinite(sign) || sign == 0.0) sign = 1.0;
        if (usedCalibration) staticFallback = false;
        return new GainResult(clamp(sign * magnitude, -K_HARD_CAP, K_HARD_CAP),
                staticFallback, usedCalibration);
    }

    private static double governorAppliedRpm(Level level, FccBinding binding) {
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, binding.governor());
        if (be instanceof com.createstats.flight.blockentity.RpmGovernorBE rg) {
            return rg.getAppliedRpm();
        }
        return 0.0;
    }

    private static double finiteOrZero(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static long mix(long h, long prime, long v) {
        h ^= v;
        h *= prime;
        return h;
    }

    private static long quantize(double v, double scale) {
        return Double.isFinite(v) ? Math.round(v * scale) : 0L;
    }
}
