package com.createstats.efficiency;

import net.minecraft.nbt.CompoundTag;

public final class EfficiencyGrader
{
    public static final float KAPPA = 0.6f;
    private static final float ALPHA = 0.025f;
    private static final float BETA_1 = 200.0f;
    private static final float BETA_2 = 2.0f;
    private static final float DT = 40.0f;
    private static final float BETA_A = 3.5f;
    private static final float BETA_B = 1.8f;
    private static final float U_PEAK = 0.7575757f;
    private static final float BETA_NORM;
    private static final float H0 = 0.08f;
    private static final float SIGMA_0 = 0.020000001f;
    private static final float TAU = 6000.0f;
    
    private EfficiencyGrader() {
    }
    
    public static EfficiencyScore calculate(NetworkDataSnapshot snap, ODEState state) {
        if (snap.capacitySU <= 0.0f) {
            return EfficiencyScore.noNetwork();
        }
        final float u = snap.stressSU / snap.capacitySU;
        final float phiU = computePhiU(u);
        final float phiH = computePhiH(u);
        final float phiS = computePhiS(snap);
        final float uptimeTarget = snap.isOverstressed ? 0.0f : 1.0f;
        final float decay = EfficiencyGrader.DT / EfficiencyGrader.TAU;
        state.uptimeMemory += decay * (uptimeTarget - state.uptimeMemory);
        state.uptimeMemory = Math.max(0.0f, Math.min(1.0f, state.uptimeMemory));
        final float uMem = state.uptimeMemory;
        final float eEq = computeEquilibrium(phiU, phiH, phiS, uMem);
        float duDt = 0.0f;
        if (state.previousU >= 0.0f) {
            duDt = (u - state.previousU) / EfficiencyGrader.DT;
        }
        final float psi = EfficiencyGrader.BETA_1 * duDt * duDt + EfficiencyGrader.BETA_2 * (snap.isOverstressed ? 1.0f : 0.0f);
        float E = state.currentE;
        E = E + EfficiencyGrader.ALPHA * (eEq - E) - psi;
        E = Math.max(0.0f, Math.min(100.0f, E));
        state.currentE = E;
        state.previousU = u;
        final float[] dimScores = new float[EfficiencyDimension.values().length];
        dimScores[EfficiencyDimension.UTILIZATION.ordinal()] = phiU;
        dimScores[EfficiencyDimension.HEADROOM.ordinal()] = phiH;
        dimScores[EfficiencyDimension.STABILITY.ordinal()] = phiS;
        dimScores[EfficiencyDimension.UPTIME.ordinal()] = uMem;
        final String grade = gradeFromScore(E);
        final int color = gradeColor(E);
        return new EfficiencyScore(E, grade, color, dimScores, snap.computedAtTick);
    }
    
    public static float computePhiU(float u) {
        if (u <= 0.0f || u >= 1.0f) {
            return 0.0f;
        }
        return Math.min(1.0f, EfficiencyGrader.BETA_NORM * (float)(Math.pow(u, EfficiencyGrader.BETA_A - 1.0f) * Math.pow(1.0f - u, EfficiencyGrader.BETA_B - 1.0f)));
    }
    
    public static float computePhiH(float u) {
        final float h = Math.max(0.0f, 1.0f - u);
        return 1.0f - (float)Math.exp(-h / EfficiencyGrader.H0);
    }
    
    public static float computePhiS(NetworkDataSnapshot snap) {
        if (snap.rpmAvg < 1.0f) {
            return 0.0f;
        }
        final float cv = snap.rpmStdDev / snap.rpmAvg;
        return (float)Math.exp(-cv * cv / EfficiencyGrader.SIGMA_0);
    }
    
    public static float computeEquilibrium(float phiU, float phiH, float phiS, float uMem) {
        final float kappa = EfficiencyGrader.KAPPA;
        if (Math.abs(kappa) < 0.001f) {
            final float logSum = EfficiencyDimension.UTILIZATION.getWeight() * safeLog(phiU) + EfficiencyDimension.HEADROOM.getWeight() * safeLog(phiH) + EfficiencyDimension.STABILITY.getWeight() * safeLog(phiS) + EfficiencyDimension.UPTIME.getWeight() * safeLog(uMem);
            return 100.0f * (float)Math.exp(logSum);
        }
        final float sum = EfficiencyDimension.UTILIZATION.getWeight() * safePow(phiU, kappa) + EfficiencyDimension.HEADROOM.getWeight() * safePow(phiH, kappa) + EfficiencyDimension.STABILITY.getWeight() * safePow(phiS, kappa) + EfficiencyDimension.UPTIME.getWeight() * safePow(uMem, kappa);
        final float mean = (float)Math.pow(Math.max(0.0f, sum), 1.0f / kappa);
        return 100.0f * Math.max(0.0f, Math.min(1.0f, mean));
    }
    
    private static float safePow(float base, float exp) {
        if (base <= 0.0f) {
            return 0.0f;
        }
        return (float)Math.pow(base, exp);
    }
    
    private static float safeLog(float x) {
        return (float)Math.log(Math.max(1.0E-10f, x));
    }
    
    public static String gradeFromScore(float score) {
        if (score >= 95.0f) {
            return "A+";
        }
        if (score >= 90.0f) {
            return "A";
        }
        if (score >= 85.0f) {
            return "A-";
        }
        if (score >= 80.0f) {
            return "B+";
        }
        if (score >= 75.0f) {
            return "B";
        }
        if (score >= 70.0f) {
            return "B-";
        }
        if (score >= 65.0f) {
            return "C+";
        }
        if (score >= 60.0f) {
            return "C";
        }
        if (score >= 55.0f) {
            return "C-";
        }
        if (score >= 50.0f) {
            return "D+";
        }
        if (score >= 45.0f) {
            return "D";
        }
        if (score >= 31.0f) {
            return "D-";
        }
        return "F";
    }
    
    public static int gradeColor(float score) {
        if (score >= 85.0f) {
            return -16711800;
        }
        if (score >= 70.0f) {
            return -3355648;
        }
        if (score >= 55.0f) {
            return -30720;
        }
        if (score >= 40.0f) {
            return -48128;
        }
        return -65536;
    }
    
    static {
        final float fPeak = (float)(Math.pow(EfficiencyGrader.U_PEAK, EfficiencyGrader.BETA_A - 1.0f) * Math.pow(1.0f - EfficiencyGrader.U_PEAK, EfficiencyGrader.BETA_B - 1.0f));
        BETA_NORM = 1.0f / fPeak;
    }
    
    public static class ODEState
    {
        public float currentE;
        public float previousU;
        public float uptimeMemory;
        
        public ODEState() {
            this.currentE = 50.0f;
            this.previousU = -1.0f;
            this.uptimeMemory = 1.0f;
        }
        
        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putFloat("E", this.currentE);
            tag.putFloat("PrevU", this.previousU);
            tag.putFloat("Uptime", this.uptimeMemory);
            return tag;
        }
        
        public static ODEState load(CompoundTag tag) {
            final ODEState s = new ODEState();
            s.currentE = tag.getFloat("E");
            s.previousU = tag.getFloat("PrevU");
            s.uptimeMemory = (tag.contains("Uptime") ? tag.getFloat("Uptime") : 1.0f);
            return s;
        }
    }
}
