package com.createstats.efficiency;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;

public final class EfficiencySampler {
    private static final int WINDOW = 120;

    private static final int OVERSTRESS_WINDOW = 600;

    private static final int MIN_SAMPLES = 20;

    private static final int MIN_CV_SAMPLES = 12;

    private static final float MIN_MEANINGFUL_RPM = 1.0f;

    private static final float TRUE_THRESHOLD = 0.5f;

    private EfficiencySampler() {
    }

    @Nullable
    public static EfficiencyInputs fromPanel(StatsDisplayPanelBE panel) {
        final Map<String, StatsDisplayPanelBE.DataChannel> channels = panel.getAllChannels();
        final StatsDisplayPanelBE.DataChannel stressCh = findBySuffix(channels, "_auto_stress_su");
        final StatsDisplayPanelBE.DataChannel capacityCh = findBySuffix(channels, "_auto_capacity_su");
        if (stressCh == null || capacityCh == null) {
            return null;
        }
        final TimeSeriesStore stress = stressCh.getStore();
        final TimeSeriesStore capacity = capacityCh.getStore();
        if (stress.getSampleCount() < MIN_SAMPLES || capacity.getSampleCount() < MIN_SAMPLES) {
            return null;
        }

        final StatsDisplayPanelBE.DataChannel rpmCh = findBySuffix(channels, "_auto_rpm");
        final StatsDisplayPanelBE.DataChannel overCh = findBySuffix(channels, "_auto_overstressed");

        final float capacitySU = capacity.getLatestValue();
        final float stressSU = stress.getLatestValue();
        final float u = capacitySU > 0.0f ? stressSU / capacitySU : 0.0f;

        final float peakStressSU = stress.getMaxWindow(WINDOW);

        final float stressAvg = stress.getAverageWindow(WINDOW);
        final boolean stressCvKnown = stress.getSampleCount() >= MIN_CV_SAMPLES && stressAvg > 0.0f;
        final float stressCv = stressCvKnown ? stress.getStdDevWindow(WINDOW) / stressAvg : 0.0f;

        final float rpmAvg = rpmCh != null ? rpmCh.getStore().getAverageWindow(WINDOW) : 0.0f;
        final boolean rpmKnown = rpmCh != null
                && rpmCh.getStore().getSampleCount() >= MIN_CV_SAMPLES
                && rpmAvg >= MIN_MEANINGFUL_RPM;
        final float rpmCv = rpmKnown ? rpmCh.getStore().getStdDevWindow(WINDOW) / rpmAvg : 0.0f;

        // create's own overstressed flag wins when bound

        final boolean overstressed = overCh != null
                ? overCh.getStore().getLatestValue() >= TRUE_THRESHOLD
                : u >= 1.0f;

        float longestRunSeconds = 0.0f;
        float overstressRate = 0.0f;
        boolean episodesKnown = false;
        if (overCh != null) {
            final TimeSeriesStore over = overCh.getStore();
            final int count = Math.min(OVERSTRESS_WINDOW, over.getSampleCount());
            final float[] data = over.getDataWindow(count);
            if (data != null && data.length > 0) {
                final float secondsPerSample = Math.max(1, over.getSampleIntervalTicks()) / 20.0f;
                int run = 0;
                int longest = 0;
                int total = 0;
                for (float v : data) {
                    if (v >= TRUE_THRESHOLD) {
                        run++;
                        total++;
                        if (run > longest) {
                            longest = run;
                        }
                    } else {
                        run = 0;
                    }
                }
                longestRunSeconds = longest * secondsPerSample;
                overstressRate = total / (float) data.length;
                episodesKnown = true;
            }
        }

        return new EfficiencyInputs(
                capacitySU,
                stressSU,
                peakStressSU,
                stressCv,
                stressCvKnown,
                rpmCv,
                rpmKnown,
                overstressed,
                longestRunSeconds,
                episodesKnown,
                overstressRate,
                episodesKnown,
                panel.getLevel() != null ? panel.getLevel().getGameTime() : 0L);
    }

    @Nullable
    private static StatsDisplayPanelBE.DataChannel findBySuffix(
            Map<String, StatsDisplayPanelBE.DataChannel> channels, String suffix) {
        for (Map.Entry<String, StatsDisplayPanelBE.DataChannel> entry : channels.entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
