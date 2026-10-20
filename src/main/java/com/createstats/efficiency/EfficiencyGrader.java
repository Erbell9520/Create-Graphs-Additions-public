package com.createstats.efficiency;

public final class EfficiencyGrader {
    public static final float KAPPA = 0.6f;

    public static final float ALPHA_UP = 0.0125f;

    public static final float ALPHA_DOWN = 0.030f;

    public static final float BETA_TRANSIENT = 32.0f;

    public static final float PSI_TRANSIENT_CAP = 6.0f;

    public static final float BETA_OVERSTRESS = 1.0f;

    public static final float UPTIME_TAU = 300.0f;

    public static final int WARMUP_OBSERVATIONS = 15;

    public static final int DEFAULT_INTERVAL_TICKS = 40;

    public static final float MAX_STEP_SECONDS = 10.0f;

    private static final float TICKS_PER_SECOND = 20.0f;

    private EfficiencyGrader() {
    }

    public static EfficiencyScore calculate(EfficiencyInputs in, ODEState state) {
        return calculate(in, state, DEFAULT_INTERVAL_TICKS);
    }

    public static EfficiencyScore calculate(EfficiencyInputs in, ODEState state, int elapsedTicks) {
        return evaluate(in, state, elapsedTicks).score();
    }

    public static Evaluation evaluate(EfficiencyInputs in, ODEState state, int elapsedTicks) {
        if (in == null || !in.hasNetwork()) {
            return new Evaluation(EfficiencyScore.noNetwork(), null);
        }
        final EfficiencyTrace trace = new EfficiencyTrace();
        return new Evaluation(step(in, state, elapsedTicks, trace), trace);
    }

    public record Evaluation(EfficiencyScore score, EfficiencyTrace trace) {
    }

    private static EfficiencyScore step(EfficiencyInputs in, ODEState state, int elapsedTicks,
                                        EfficiencyTrace trace) {
        final float rawDt = Math.max(1.0f, elapsedTicks) / TICKS_PER_SECOND;
        final float dt = Math.min(MAX_STEP_SECONDS, rawDt);

        final float u = in.utilization();
        final float peakU = in.peakUtilization();

        trace.capacitySU = in.capacitySU();
        trace.stressSU = in.stressSU();
        trace.peakStressSU = in.peakStressSU();
        trace.u = u;
        trace.peakU = peakU;
        trace.rpmCv = in.rpmCv();
        trace.rpmKnown = in.rpmKnown();
        trace.stressCv = in.stressCv();
        trace.stressCvKnown = in.stressCvKnown();
        trace.overstressed = in.overstressed();
        trace.longestOverstressRunSeconds = in.longestOverstressRunSeconds();
        trace.resilienceKnown = in.resilienceKnown();
        trace.overstressRate = in.overstressRate();
        trace.elapsedTicks = elapsedTicks;
        trace.dtSeconds = dt;
        trace.stepClamped = rawDt > MAX_STEP_SECONDS;
        trace.previousU = state.previousU;
        trace.ePrev = state.currentE;
        trace.uptimeMemoryBefore = state.uptimeMemory;
        trace.computedAtTick = in.computedAtTick();

        final int n = EfficiencyDimension.values().length;
        final float[] phi = new float[n];
        final boolean[] known = new boolean[n];

        phi[EfficiencyDimension.UTILIZATION.ordinal()] = EfficiencyKernels.load(u);
        known[EfficiencyDimension.UTILIZATION.ordinal()] = true;

        phi[EfficiencyDimension.HEADROOM.ordinal()] = EfficiencyKernels.reserve(peakU);
        known[EfficiencyDimension.HEADROOM.ordinal()] = true;

        phi[EfficiencyDimension.STABILITY.ordinal()] = EfficiencyKernels.rhythm(in.rpmCv());
        known[EfficiencyDimension.STABILITY.ordinal()] = in.rpmKnown();

        phi[EfficiencyDimension.UPTIME.ordinal()] = advanceUptime(in, state, dt);
        known[EfficiencyDimension.UPTIME.ordinal()] = true;
        trace.uptimeMemoryAfter = state.uptimeMemory;

        phi[EfficiencyDimension.SMOOTHNESS.ordinal()] = EfficiencyKernels.smoothness(in.stressCv());
        known[EfficiencyDimension.SMOOTHNESS.ordinal()] = in.stressCvKnown();

        phi[EfficiencyDimension.RESILIENCE.ordinal()] =
                EfficiencyKernels.resilience(in.longestOverstressRunSeconds());
        known[EfficiencyDimension.RESILIENCE.ordinal()] = in.resilienceKnown();

        final float eEq = computeEquilibrium(phi, known, trace);
        final float observability = observabilityOf(known);

        System.arraycopy(phi, 0, trace.phi, 0, n);
        trace.knownMask = maskOf(known);
        trace.equilibrium = eEq;
        trace.observability = observability;

        final float psi = computePenalty(in, state, u, dt, trace);

        final float alpha = (eEq >= state.currentE) ? ALPHA_UP : ALPHA_DOWN;
        final float relaxBlend = 1.0f - (float) Math.exp(-alpha * dt);
        float blend = relaxBlend;
        if (state.observations < WARMUP_OBSERVATIONS) {
            blend = Math.max(blend, 1.0f / (state.observations + 1.0f));
        }
        final float eRelaxed = state.currentE + blend * (eEq - state.currentE);
        final float ePenalised = eRelaxed - psi * dt;
        float e = clamp01(ePenalised / 100.0f) * 100.0f;

        trace.alpha = alpha;
        trace.rising = eEq >= state.currentE;
        trace.relaxBlend = blend;
        trace.warmStart = blend > relaxBlend;
        trace.eRelaxed = eRelaxed;
        trace.eBeforeClamp = ePenalised;
        trace.eNew = e;
        trace.observations = state.observations + 1;

        state.currentE = e;
        state.previousU = u;
        state.observations = Math.min(Integer.MAX_VALUE - 1, state.observations + 1);

        final int gradeIndex = GradeScale.resolve(e, state.gradeIndex);
        state.gradeIndex = gradeIndex;

        return new EfficiencyScore(
                e,
                GradeScale.letterAt(gradeIndex),
                GradeScale.colorAt(gradeIndex),
                phi,
                maskOf(known),
                observability,
                state.warmupProgress(),
                eEq,
                in.computedAtTick());
    }

    private static float advanceUptime(EfficiencyInputs in, ODEState state, float dt) {
        if (!state.uptimeSeeded) {
            if (in.overstressRateKnown()) {
                state.uptimeMemory = clamp01(1.0f - in.overstressRate());
            }
            state.uptimeSeeded = true;
        }
        final float target = in.overstressed() ? 0.0f : 1.0f;
        final float blend = 1.0f - (float) Math.exp(-dt / UPTIME_TAU);
        state.uptimeMemory = clamp01(state.uptimeMemory + blend * (target - state.uptimeMemory));
        return state.uptimeMemory;
    }

    private static float computePenalty(EfficiencyInputs in, ODEState state, float u, float dt,
                                        EfficiencyTrace trace) {
        float swing = 0.0f;
        float swingRaw = 0.0f;
        float duDt = 0.0f;
        if (state.previousU >= 0.0f) {
            duDt = (u - state.previousU) / dt;
            swingRaw = BETA_TRANSIENT * duDt * duDt;
            swing = Math.min(PSI_TRANSIENT_CAP, swingRaw);
        }
        final float severity = in.overstressed() ? EfficiencyKernels.overstressSeverity(u) : 0.0f;
        final float overstress = BETA_OVERSTRESS * severity;

        trace.duDt = duDt;
        trace.swingRaw = swingRaw;
        trace.swingApplied = swing;
        trace.severity = severity;
        trace.overstressPenalty = overstress;
        trace.psi = swing + overstress;
        trace.penaltyPoints = trace.psi * dt;
        return trace.psi;
    }

    public static float computeEquilibrium(float[] phi, boolean[] known) {
        return computeEquilibrium(phi, known, null);
    }

    private static float computeEquilibrium(float[] phi, boolean[] known, EfficiencyTrace trace) {
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        float totalW = 0.0f;
        for (int i = 0; i < dims.length; i++) {
            if (known[i]) {
                totalW += dims[i].getWeight();
            }
        }
        if (trace != null) {
            trace.totalWeight = totalW;
        }
        if (totalW <= 0.0f) {
            return 0.0f;
        }
        if (Math.abs(KAPPA) < 0.001f) {
            float logSum = 0.0f;
            for (int i = 0; i < dims.length; i++) {
                if (known[i]) {
                    logSum += (dims[i].getWeight() / totalW) * safeLog(phi[i]);
                }
            }
            return 100.0f * clamp01((float) Math.exp(logSum));
        }
        float sum = 0.0f;
        for (int i = 0; i < dims.length; i++) {
            if (!known[i]) {
                continue;
            }
            final float w = dims[i].getWeight() / totalW;
            final float term = w * safePow(phi[i], KAPPA);
            sum += term;
            if (trace != null) {
                trace.weightApplied[i] = w;
                trace.term[i] = term;
            }
        }
        if (trace != null) {
            trace.meanSum = sum;
        }
        final float mean = (float) Math.pow(Math.max(0.0f, sum), 1.0f / KAPPA);
        return 100.0f * clamp01(mean);
    }

    public static float observabilityOf(boolean[] known) {
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        float w = 0.0f;
        for (int i = 0; i < dims.length; i++) {
            if (known[i]) {
                w += dims[i].getWeight();
            }
        }
        return clamp01(w / Math.max(1.0E-6f, EfficiencyDimension.weightSum()));
    }

    private static int maskOf(boolean[] known) {
        int mask = 0;
        for (int i = 0; i < known.length; i++) {
            if (known[i]) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private static float safePow(float base, float exp) {
        return base <= 0.0f ? 0.0f : (float) Math.pow(base, exp);
    }

    private static float safeLog(float x) {
        return (float) Math.log(Math.max(1.0E-10f, x));
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    public static String gradeFromScore(float score) {
        return GradeScale.letterAt(GradeScale.indexFor(score));
    }

    public static int gradeColor(float score) {
        return GradeScale.colorAt(GradeScale.indexFor(score));
    }
}
