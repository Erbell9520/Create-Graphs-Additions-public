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

public final class BMatrixBuilder {
    public static final double RPM_DYNAMIC_THRESHOLD = 2.0;

    public static final double RPM_SIGN_UPDATE_THRESHOLD = 24.0;

    public static final double K_HARD_CAP = 1.0e4;

    public static final double STATIC_K_PER_SAIL = 0.25;

    private static final double GAIN_EWMA_ALPHA = 0.08;
    private static final Map<Long, GainEstimate> GAIN_ESTIMATES = new HashMap<>();

    public static synchronized void evictGainEstimate(long propPosKey) {
        GAIN_ESTIMATES.remove(propPosKey);
    }

    public static synchronized void clearAllGainEstimates() {
        GAIN_ESTIMATES.clear();
    }

    public static volatile boolean FORCE_APPLY_GYRO_TILT = false;

    public static volatile boolean DIAG_LOG = true;
    private static long lastDiagTickMs = 0L;

    private BMatrixBuilder() {}

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

        List<FccBinding> sorted = new ArrayList<>(bindings);
        sorted.sort(Comparator
                .comparingLong((FccBinding b) -> b.prop().asLong())
                .thenComparingLong(b -> b.governor().asLong()));

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

            Vector3d dirLocal = new Vector3d(dir);

            if (applyGyroTilt && pd.isGyro() && pd.tiltQuat() != null) {
                org.joml.Vector3f tmp = new org.joml.Vector3f(
                        (float) dirLocal.x, (float) dirLocal.y, (float) dirLocal.z);
                pd.tiltQuat().transform(tmp);
                dirLocal.set(tmp.x(), tmp.y(), tmp.z());
            }

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

        long fp = 0xcbf29ce484222325L;
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
                    double seedMag = calibratedMag;
                    double blendMag = est == null
                            ? seedMag
                            : (1.0 - GAIN_EWMA_ALPHA) * magnitude + GAIN_EWMA_ALPHA * observedMag;
                    magnitude = blendMag;
                } else {
                    double observedSign = Math.signum(observed);

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
