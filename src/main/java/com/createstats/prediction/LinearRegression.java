package com.createstats.prediction;

public final class LinearRegression
{
    private LinearRegression() {
    }

    public static RegressionCoefficients compute(float[] data, int offset, int length) {
        final int n = length;
        if (n < 3 || data == null || offset < 0 || offset + length > data.length) {
            return null;
        }
        final double xBar = (n - 1) / 2.0;
        final double Sxx = n * (double)(n - 1) * (2 * n - 1) / 6.0 - n * xBar * xBar;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumY2 = 0.0;
        for (int i = 0; i < n; ++i) {
            final double y = data[offset + i];
            sumY += y;
            sumXY += i * y;
            sumY2 += y * y;
        }
        final double yBar = sumY / n;
        final double Sxy = sumXY - n * xBar * yBar;
        final double Syy = sumY2 - n * yBar * yBar;
        if (Sxx < 1.0E-12) {
            return null;
        }
        final double slope = Sxy / Sxx;
        final double intercept = yBar - slope * xBar;
        double rSquared;
        if (Syy < 1.0E-10) {
            rSquared = 0.0;
        }
        else {
            rSquared = Sxy * Sxy / (Sxx * Syy);
            rSquared = Math.max(0.0, Math.min(1.0, rSquared));
        }
        double SSE = 0.0;
        for (int j = 0; j < n; ++j) {
            final double residual = data[offset + j] - (slope * j + intercept);
            SSE += residual * residual;
        }
        final double standardError = (n > 2) ? Math.sqrt(SSE / (n - 2)) : 0.0;
        return new RegressionCoefficients(slope, intercept, rSquared, standardError, Sxx, xBar, n);
    }

    public static double predictionIntervalHalfWidth(RegressionCoefficients coeffs, double xNew, double tCritical) {
        if (coeffs == null || coeffs.standardError() < 1.0E-12) {
            return 0.0;
        }
        final double term = 1.0 + 1.0 / coeffs.n() + Math.pow(xNew - coeffs.xBar(), 2.0) / coeffs.Sxx();
        return tCritical * coeffs.standardError() * Math.sqrt(term);
    }

    public static double solveForX(double slope, double intercept, double thresholdY) {
        if (Math.abs(slope) < 1.0E-10) {
            return Double.NaN;
        }
        return (thresholdY - intercept) / slope;
    }

    public static float[] exponentialSmooth(float[] data, int offset, int length, float alpha) {
        if (length <= 0 || alpha >= 1.0f) {
            final float[] copy = new float[length];
            System.arraycopy(data, offset, copy, 0, length);
            return copy;
        }
        final float[] smoothed = new float[length];
        smoothed[0] = data[offset];
        for (int i = 1; i < length; ++i) {
            smoothed[i] = alpha * data[offset + i] + (1.0f - alpha) * smoothed[i - 1];
        }
        return smoothed;
    }

    public record RegressionCoefficients(double slope, double intercept, double rSquared, double standardError, double Sxx, double xBar, int n) {}
}
