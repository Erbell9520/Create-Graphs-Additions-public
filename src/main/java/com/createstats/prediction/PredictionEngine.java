package com.createstats.prediction;

import com.createstats.display.StatsDisplaySettings;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import java.util.concurrent.ConcurrentHashMap;

public final class PredictionEngine
{
    public static final int MIN_SAMPLES = 10;
    public static final double MIN_R_SQUARED = 0.2;
    public static final long RECOMPUTE_INTERVAL = 20L;
    private static final ConcurrentHashMap<String, PredictionResult> cache;

    private PredictionEngine() {
    }

    public static PredictionResult computePrediction(String channelId, TimeSeriesStore store, MetricType metricType, PredictionConfig config, StatsDisplaySettings settings, long currentTick, double alertThreshold) {
        if (metricType == MetricType.BOOLEAN_FLAG) {
            return null;
        }
        final int n = store.getSampleCount();
        if (n < 10) {
            return null;
        }
        final PredictionResult cached = PredictionEngine.cache.get(channelId);
        if (cached != null) {
            final long ticksSince = currentTick - cached.computedAtTick();
            final int currentHash = store.getRecentDataHash();
            if (ticksSince < 20L && currentHash == cached.dataHashAtCompute()) {
                return cached;
            }
        }
        int W;
        if (config != null && config.getWindowSamples() > 0) {
            W = Math.min(config.getWindowSamples(), n);
        }
        else {
            int visibleSeconds = (settings != null) ? settings.getTimeRange().getSeconds() : StatsDisplaySettings.TimeRange.FIVE_MIN.getSeconds();
            if (visibleSeconds == Integer.MAX_VALUE) {
                visibleSeconds = store.getDurationSeconds();
            }
            if (visibleSeconds <= 0) {
                visibleSeconds = 300;
            }
            final int samplesPerSec = Math.max(1, 20 / store.getSampleIntervalTicks());
            W = Math.min(visibleSeconds * samplesPerSec / 2, n);
            W = Math.max(10, W);
        }
        float[] data;
        final float[] rawData = data = store.getDataWindow(W);
        final float alpha = (config != null) ? config.getSmoothingAlpha() : 1.0f;
        if (alpha < 1.0f) {
            data = LinearRegression.exponentialSmooth(rawData, 0, rawData.length, alpha);
        }
        final LinearRegression.RegressionCoefficients coeffs = LinearRegression.compute(data, 0, data.length);
        if (coeffs == null) {
            return null;
        }
        final int samplesPerSec2 = Math.max(1, 20 / store.getSampleIntervalTicks());
        final double slopePerSecond = coeffs.slope() * samplesPerSec2;
        final boolean reliable = coeffs.rSquared() >= 0.2 && W >= 10;
        final boolean slopePositive = coeffs.slope() > 0.0;
        final ThresholdResolver.Threshold threshold = ThresholdResolver.resolve(metricType, slopePositive, alertThreshold);
        boolean hasThresholdPrediction = false;
        double secondsToThreshold = Double.NaN;
        String thresholdLabel = "";
        double thresholdValue = Double.NaN;
        if (threshold != null && Math.abs(coeffs.slope()) > 1.0E-7) {
            final double xThreshold = LinearRegression.solveForX(coeffs.slope(), coeffs.intercept(), threshold.value());
            final double samplesUntil = xThreshold - (data.length - 1);
            final double secondsUntil = samplesUntil / samplesPerSec2;
            final int maxPredSec = (config != null) ? config.getMaxPredictionSeconds() : 600;
            if (secondsUntil > 0.0 && secondsUntil < maxPredSec) {
                hasThresholdPrediction = true;
                secondsToThreshold = secondsUntil;
                thresholdLabel = threshold.label();
                thresholdValue = threshold.value();
            }
            else if (secondsUntil <= 0.0) {
                hasThresholdPrediction = true;
                secondsToThreshold = 0.0;
                thresholdLabel = threshold.label() + " (now)";
                thresholdValue = threshold.value();
            }
        }
        final float predRatio = (settings != null) ? settings.getPredictionRatio() : 0.5f;
        int predictionSamples = (int)(W * predRatio);
        final int maxPredSamples = (config != null) ? (config.getMaxPredictionSeconds() * samplesPerSec2) : (600 * samplesPerSec2);
        predictionSamples = Math.min(predictionSamples, maxPredSamples);
        predictionSamples = Math.max(1, predictionSamples);
        final PredictionResult result = new PredictionResult(coeffs.slope(), slopePerSecond, coeffs.intercept(), coeffs.rSquared(), coeffs.standardError(), reliable, W, hasThresholdPrediction, thresholdLabel, secondsToThreshold, thresholdValue, coeffs.Sxx(), coeffs.xBar(), n - W, W, predictionSamples, currentTick, store.getRecentDataHash());
        PredictionEngine.cache.put(channelId, result);
        return result;
    }

    public static void invalidate(String channelId) {
        PredictionEngine.cache.remove(channelId);
    }

    public static void clearAll() {
        PredictionEngine.cache.clear();
    }

    static {
        cache = new ConcurrentHashMap<String, PredictionResult>();
    }
}
