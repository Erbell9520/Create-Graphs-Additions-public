package com.createstats.prediction;

public record PredictionResult(double slope, double slopePerSecond, double intercept, double rSquared, double standardError, boolean reliable, int sampleCount, boolean hasThresholdPrediction, String thresholdLabel, double secondsToThreshold, double thresholdValue, double Sxx, double xBar, int regressionWindowStart, int regressionWindowLength, int predictionSamples, long computedAtTick, int dataHashAtCompute) {
    public boolean isRenderable() {
        return this.reliable && Math.abs(this.slope) > 1.0E-7 && this.sampleCount >= 10;
    }
    
    public boolean hasTimeWarning() {
        return this.hasThresholdPrediction && !Double.isNaN(this.secondsToThreshold) && this.secondsToThreshold >= 0.0 && this.secondsToThreshold < 7200.0;
    }
}
