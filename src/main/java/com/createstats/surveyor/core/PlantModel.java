package com.createstats.surveyor.core;

// offroad wheels push a flat 1.75 per rpm, learned from that
public final class PlantModel {
    public static final double UNLEARNED_CRUISE = 0.7;

    public static final int LEARN_SAMPLES = 60;
    public static final int LEARN_BRAKES = 6;

    static final double LAMBDA = 0.995;
    static final double DT = 0.05;

    private double k;
    private double tau;
    private double tauBrake;
    private int samples;
    private int brakeSamples;

    private double b;
    private double a;
    private double p00 = 50.0;
    private double p01 = 0.0;
    private double p11 = 50.0;

    private double skidGain;

    public PlantModel(int maxRpm) {
        this(Math.max(3.0, 0.1 * Math.max(16, maxRpm)), 2.0, 0.5, 0, 0, 0.0);
    }

    public PlantModel(double k, double tau, double tauBrake, int samples, int brakeSamples, double skidGain) {
        this.k = clamp(k, 0.5, 80.0);
        this.tau = clamp(tau, 0.15, 12.0);
        this.tauBrake = clamp(tauBrake, 0.04, 4.0);
        this.samples = Math.max(0, samples);
        this.brakeSamples = Math.max(0, brakeSamples);
        this.skidGain = Double.isFinite(skidGain) ? Math.max(0.0, skidGain) : 0.0;
        this.a = DT / this.tau;
        this.b = this.k * this.a;

        if (this.samples >= LEARN_SAMPLES) {
            p00 = 0.5;
            p11 = 5.0e-3;
        }
    }

    public PlantModel copy() {
        return new PlantModel(k, tau, tauBrake, samples, brakeSamples, skidGain);
    }

    public double k() {
        return k;
    }

    public double tau() {
        return tau;
    }

    public double tauBrake() {
        return tauBrake;
    }

    public int samples() {
        return samples;
    }

    public int brakeSamples() {
        return brakeSamples;
    }

    public double skidGain() {
        return skidGain;
    }

    public boolean learned() {
        return samples >= LEARN_SAMPLES;
    }

    public boolean brakesLearned() {
        return brakeSamples >= LEARN_BRAKES;
    }

    public void seedSkid(double gain) {
        if (skidGain <= 0.0 && Double.isFinite(gain) && gain > 0.0) skidGain = gain;
    }

    public double throttleFor(double v) {
        return v / k;
    }

    public double beta() {
        return clamp(tau / tauBrake - 1.0, 0.5, 12.0);
    }

    public double accel(double u, double b, double v, double grade) {
        double brake = clamp(b, 0.0, 1.0);
        return (k * u * (1.0 - brake) - v * (1.0 + beta() * brake)) / tau + grade;
    }

    public double brakeDistance(double v) {
        double speed = Math.max(0.0, v);
        double d = speed * (tauBrake + 2.0 * DT);
        return brakesLearned() ? d : d * 1.4 + 0.2;
    }

    public void observe(double u, double v0, double v1, boolean braking, double steer) {
        observe(u, v0, v1, braking ? 1.0 : 0.0, steer, 0.0);
    }

    public void observe(double u, double v0, double v1, double brake, double steer, double grade) {
        if (!Double.isFinite(u) || !Double.isFinite(v0) || !Double.isFinite(v1)) return;
        double g = Double.isFinite(grade) ? grade : 0.0;
        double dv = v1 - v0 - g * DT;

        if (Math.abs(v1 - v0) > 1.5) return;
        if (brake > 1.0e-3) {
            if (brake >= 0.3 && u < 0.02 && v0 > 0.8 && v1 > 0.05 && v1 < v0) {
                double t = DT / Math.log(v0 / v1);
                if (Double.isFinite(t) && t > 0.0) {
                    double betaSeen = (tau / t - 1.0) / brake;
                    double full = tau / (1.0 + Math.max(0.5, betaSeen));
                    double w = brakeSamples < 10 ? 1.0 / (brakeSamples + 1) : 0.1;
                    tauBrake = clamp(tauBrake + (full - tauBrake) * w, 0.04, 4.0);
                    brakeSamples++;
                }
            }
            return;
        }

        if ((u > 0.05 && v0 == v1 && v0 != 0.0) || Math.abs(steer) > 0.5 || u < 0.0 || (u < 0.02 && v0 < 0.2)) {
            blockN = 0;
            return;
        }

        if (blockN == 0) {
            blockV0 = v0;
            blockU = 0.0;
            blockV = 0.0;
            blockG = 0.0;
            blockUMin = u;
            blockUMax = u;
        }
        blockU += u;
        blockV += v0;
        blockG += g;
        blockUMin = Math.min(blockUMin, u);
        blockUMax = Math.max(blockUMax, u);
        blockN++;
        if (blockN < BLOCK) return;
        blockN = 0;
        double uMean = blockU / BLOCK;
        double vMean = blockV / BLOCK;
        double dvTick = (v1 - blockV0) / BLOCK - (blockG / BLOCK) * DT;
        double accel = dvTick / DT;

        if (Math.abs(accel) < INFORMATIVE && blockUMax - blockUMin < 0.05) {
            if (uMean > 0.2 && Math.abs(accel) < 0.05 && samples >= LEARN_SAMPLES) {
                double ks = vMean / uMean;
                if (ks > 0.5 && ks < 80.0) {
                    k += (ks - k) * STEADY_SHARE;
                    b = k * a;
                }
            }
            return;
        }
        double f0 = uMean;
        double f1 = -vMean;
        dv = dvTick;
        double err = dv - (f0 * b + f1 * a);

        if (samples >= LEARN_SAMPLES && err * err > 16.0 * residual + FLOOR * FLOOR) {
            outliers++;

            if (outliers < 20) return;
        } else {
            outliers = 0;
        }
        residual += (err * err - residual) * 0.02;

        double pf0 = p00 * f0 + p01 * f1;
        double pf1 = p01 * f0 + p11 * f1;
        double denom = LAMBDA + f0 * pf0 + f1 * pf1;
        if (denom < 1.0e-12) return;
        double g0 = pf0 / denom;
        double g1 = pf1 / denom;
        double nb = b + g0 * err;
        double na = a + g1 * err;
        double q00 = (p00 - g0 * pf0) / LAMBDA;
        double q01 = (p01 - g0 * pf1) / LAMBDA;
        double q11 = (p11 - g1 * pf1) / LAMBDA;

        p00 = Math.min(q00, P_MAX_B);
        p01 = Math.max(-P_MAX_AB, Math.min(q01, P_MAX_AB));
        p11 = Math.min(q11, P_MAX_A);
        a = clamp(na, DT / 12.0, DT / 0.15);
        b = Math.max(1.0e-5, nb);
        tau = DT / a;
        k = clamp(b / a, 0.5, 80.0);
        samples += BLOCK;
    }

    static final int BLOCK = 5;

    static final double INFORMATIVE = 0.2;

    static final double STEADY_SHARE = 0.02;
    private int blockN;
    private double blockV0;
    private double blockU;
    private double blockV;
    private double blockG;
    private double blockUMin;
    private double blockUMax;

    private double residual = 0.01;
    static final double FLOOR = 0.01;
    private int outliers;

    static final double P_MAX_B = 20.0;
    static final double P_MAX_A = 0.2;
    static final double P_MAX_AB = 2.0;

    public void observeSkid(double steer, double yawRate, double v) {
        if (Math.abs(steer) < 0.12 || !Double.isFinite(yawRate) || Math.abs(v) < 0.5) return;
        double g = yawRate / steer;
        if (!(g > 0.0) || g > 10.0) return;
        skidGain = skidGain <= 0.0 ? g : skidGain + (g - skidGain) * 0.05;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }
}
