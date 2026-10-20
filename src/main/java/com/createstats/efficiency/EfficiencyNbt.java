package com.createstats.efficiency;

import net.minecraft.nbt.CompoundTag;

public final class EfficiencyNbt {
    private EfficiencyNbt() {
    }

    public static CompoundTag saveScore(EfficiencyScore score) {
        final CompoundTag tag = new CompoundTag();
        tag.putFloat("Score", score.compositeScore());
        tag.putString("Grade", score.grade());
        tag.putInt("Color", score.gradeColor());
        tag.putLong("Tick", score.computedAtTick());
        tag.putInt("Known", score.knownMask());
        tag.putFloat("Obs", score.observability());
        tag.putFloat("Warm", score.warmup());
        tag.putFloat("Eeq", score.equilibrium());
        final float[] dims = score.dimensionScores();
        if (dims != null) {
            for (int i = 0; i < dims.length; i++) {
                tag.putFloat("Dim" + i, dims[i]);
            }
        }
        return tag;
    }

    public static EfficiencyScore loadScore(CompoundTag tag) {
        final int n = EfficiencyDimension.values().length;
        final float[] dims = new float[n];
        for (int i = 0; i < n; i++) {
            dims[i] = tag.contains("Dim" + i) ? tag.getFloat("Dim" + i) : 0.0f;
        }
        final float score = tag.getFloat("Score");
        final String grade = tag.contains("Grade")
                ? tag.getString("Grade")
                : EfficiencyGrader.gradeFromScore(score);

        final int known = tag.contains("Known") ? tag.getInt("Known") : (1 << n) - 1;
        final float obs = tag.contains("Obs") ? tag.getFloat("Obs") : 1.0f;
        final float warm = tag.contains("Warm") ? tag.getFloat("Warm") : 1.0f;
        final float eEq = tag.contains("Eeq") ? tag.getFloat("Eeq") : score;
        final int color = tag.contains("Color")
                ? tag.getInt("Color")
                : EfficiencyGrader.gradeColor(score);
        return new EfficiencyScore(score, grade, color, dims, known, obs, warm, eEq,
                tag.getLong("Tick"));
    }

    // sync tag only, a saved trace is stale the moment it loads
    public static CompoundTag saveTrace(EfficiencyTrace t) {
        final CompoundTag tag = new CompoundTag();
        tag.putFloat("Cap", t.capacitySU);
        tag.putFloat("Str", t.stressSU);
        tag.putFloat("PeakStr", t.peakStressSU);
        tag.putFloat("U", t.u);
        tag.putFloat("PeakU", t.peakU);
        tag.putFloat("RpmCv", t.rpmCv);
        tag.putBoolean("RpmK", t.rpmKnown);
        tag.putFloat("StrCv", t.stressCv);
        tag.putBoolean("StrCvK", t.stressCvKnown);
        tag.putBoolean("Over", t.overstressed);
        tag.putFloat("Longest", t.longestOverstressRunSeconds);
        tag.putBoolean("ResK", t.resilienceKnown);
        tag.putFloat("OverRate", t.overstressRate);
        for (int i = 0; i < t.phi.length; i++) {
            tag.putFloat("Phi" + i, t.phi[i]);
            tag.putFloat("W" + i, t.weightApplied[i]);
            tag.putFloat("T" + i, t.term[i]);
        }
        tag.putInt("Known", t.knownMask);
        tag.putFloat("TotW", t.totalWeight);
        tag.putFloat("Sum", t.meanSum);
        tag.putFloat("Eeq", t.equilibrium);
        tag.putFloat("Obs", t.observability);
        tag.putFloat("PrevU", t.previousU);
        tag.putFloat("DuDt", t.duDt);
        tag.putFloat("SwingRaw", t.swingRaw);
        tag.putFloat("Swing", t.swingApplied);
        tag.putFloat("Sev", t.severity);
        tag.putFloat("OverPen", t.overstressPenalty);
        tag.putFloat("Psi", t.psi);
        tag.putFloat("PsiPts", t.penaltyPoints);
        tag.putInt("Ticks", t.elapsedTicks);
        tag.putFloat("Dt", t.dtSeconds);
        tag.putBoolean("Clamped", t.stepClamped);
        tag.putFloat("Alpha", t.alpha);
        tag.putBoolean("Rising", t.rising);
        tag.putFloat("Blend", t.relaxBlend);
        tag.putBoolean("Warm", t.warmStart);
        tag.putFloat("Eprev", t.ePrev);
        tag.putFloat("Erlx", t.eRelaxed);
        tag.putFloat("Eraw", t.eBeforeClamp);
        tag.putFloat("Enew", t.eNew);
        tag.putInt("N", t.observations);
        tag.putFloat("UmemA", t.uptimeMemoryBefore);
        tag.putFloat("UmemB", t.uptimeMemoryAfter);
        tag.putLong("Tick", t.computedAtTick);
        return tag;
    }

    public static EfficiencyTrace loadTrace(CompoundTag tag) {
        final EfficiencyTrace t = new EfficiencyTrace();
        t.capacitySU = tag.getFloat("Cap");
        t.stressSU = tag.getFloat("Str");
        t.peakStressSU = tag.getFloat("PeakStr");
        t.u = tag.getFloat("U");
        t.peakU = tag.getFloat("PeakU");
        t.rpmCv = tag.getFloat("RpmCv");
        t.rpmKnown = tag.getBoolean("RpmK");
        t.stressCv = tag.getFloat("StrCv");
        t.stressCvKnown = tag.getBoolean("StrCvK");
        t.overstressed = tag.getBoolean("Over");
        t.longestOverstressRunSeconds = tag.getFloat("Longest");
        t.resilienceKnown = tag.getBoolean("ResK");
        t.overstressRate = tag.getFloat("OverRate");
        for (int i = 0; i < t.phi.length; i++) {
            t.phi[i] = tag.getFloat("Phi" + i);
            t.weightApplied[i] = tag.getFloat("W" + i);
            t.term[i] = tag.getFloat("T" + i);
        }
        t.knownMask = tag.getInt("Known");
        t.totalWeight = tag.getFloat("TotW");
        t.meanSum = tag.getFloat("Sum");
        t.equilibrium = tag.getFloat("Eeq");
        t.observability = tag.getFloat("Obs");

        t.previousU = tag.contains("PrevU") ? tag.getFloat("PrevU") : ODEState.NO_PREVIOUS_U;
        t.duDt = tag.getFloat("DuDt");
        t.swingRaw = tag.getFloat("SwingRaw");
        t.swingApplied = tag.getFloat("Swing");
        t.severity = tag.getFloat("Sev");
        t.overstressPenalty = tag.getFloat("OverPen");
        t.psi = tag.getFloat("Psi");
        t.penaltyPoints = tag.getFloat("PsiPts");
        t.elapsedTicks = tag.getInt("Ticks");
        t.dtSeconds = tag.getFloat("Dt");
        t.stepClamped = tag.getBoolean("Clamped");
        t.alpha = tag.getFloat("Alpha");
        t.rising = tag.getBoolean("Rising");
        t.relaxBlend = tag.getFloat("Blend");
        t.warmStart = tag.getBoolean("Warm");
        t.ePrev = tag.getFloat("Eprev");
        t.eRelaxed = tag.getFloat("Erlx");
        t.eBeforeClamp = tag.getFloat("Eraw");
        t.eNew = tag.getFloat("Enew");
        t.observations = tag.getInt("N");
        t.uptimeMemoryBefore = tag.getFloat("UmemA");
        t.uptimeMemoryAfter = tag.getFloat("UmemB");
        t.computedAtTick = tag.getLong("Tick");
        return t;
    }

    public static CompoundTag saveState(ODEState state) {
        final CompoundTag tag = new CompoundTag();
        tag.putFloat("E", state.currentE);
        tag.putFloat("PrevU", state.previousU);
        tag.putFloat("Uptime", state.uptimeMemory);
        tag.putInt("Obs", state.observations);
        tag.putInt("GradeIdx", state.gradeIndex);
        tag.putBoolean("Seeded", state.uptimeSeeded);
        return tag;
    }

    public static ODEState loadState(CompoundTag tag) {
        final ODEState s = new ODEState();
        if (tag.contains("E")) {
            s.currentE = tag.getFloat("E");
        }

        s.previousU = tag.contains("PrevU") ? tag.getFloat("PrevU") : ODEState.NO_PREVIOUS_U;
        if (s.previousU == 0.0f) {
            s.previousU = ODEState.NO_PREVIOUS_U;
        }
        if (tag.contains("Uptime")) {
            s.uptimeMemory = tag.getFloat("Uptime");
        }
        s.observations = tag.contains("Obs") ? tag.getInt("Obs") : 0;
        s.gradeIndex = tag.contains("GradeIdx")
                ? tag.getInt("GradeIdx")
                : (tag.contains("E") ? GradeScale.indexFor(s.currentE) : ODEState.NO_GRADE);

        s.uptimeSeeded = tag.contains("Seeded") ? tag.getBoolean("Seeded") : tag.contains("Uptime");
        return s;
    }
}
