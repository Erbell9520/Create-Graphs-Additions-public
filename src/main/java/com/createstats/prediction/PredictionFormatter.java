package com.createstats.prediction;

public final class PredictionFormatter
{
    private PredictionFormatter() {
    }
    
    public static String formatWarning(PredictionResult result) {
        if (result == null) {
            return "";
        }
        if (!result.reliable()) {
            return "(" + formatReliability(result) + ")";
        }
        else {
            if (!result.hasThresholdPrediction()) {
                return "(Holding steady)";
            }
            if (result.secondsToThreshold() <= 0.0) {
                return "\u26a0 " + result.thresholdLabel() + " now";
            }
            final String timeStr = formatDuration(result.secondsToThreshold());
            return "\u26a0 " + result.thresholdLabel() + " in " + timeStr + " (" + formatReliability(result) + ")";
        }
    }
    
    public static String formatCompact(PredictionResult result) {
        if (result == null) {
            return "";
        }
        if (!result.reliable()) {
            return "Forecast hazy";
        }
        if (!result.hasTimeWarning()) {
            return "Holding steady";
        }
        final String time = formatDuration(result.secondsToThreshold());
        String label = result.thresholdLabel();
        if (label.length() > 16) {
            label = label.substring(0, 14) + "..";
        }
        return label + " in " + time;
    }
    
    public static String formatRSquared(PredictionResult result) {
        if (result == null) {
            return "";
        }
        return "Confidence " + Math.round(Math.max(0.0, Math.min(1.0, result.rSquared())) * 100.0) + "%";
    }
    
    public static String formatDuration(double seconds) {
        if (seconds < 0.0) {
            return "now";
        }
        if (seconds < 60.0) {
            return "~" + (int)seconds + " sec";
        }
        if (seconds < 600.0) {
            final double min = seconds / 60.0;
            final boolean hasDecimal = min % 1.0 > 0.2 && min < 10.0;
            return hasDecimal ? String.format("~%.1f min", min) : ("~" + (int)(min + 0.5) + " min");
        }
        if (seconds < 3600.0) {
            return "~" + (int)(seconds / 60.0 + 0.5) + " min";
        }
        if (seconds < 7200.0) {
            return String.format("~%.1f hr", seconds / 3600.0);
        }
        return ">1 hr";
    }
    
    public static int getRSquaredColor(double rSquared) {
        if (rSquared >= 0.75) {
            return -16711800;
        }
        if (rSquared >= 0.45) {
            return -13312;
        }
        return -48060;
    }

    private static String formatReliability(PredictionResult result) {
        final int pct = Math.round((float)(Math.max(0.0, Math.min(1.0, result.rSquared())) * 100.0));
        if (pct >= 75) {
            return "good confidence";
        }
        if (pct >= 45) {
            return "fair confidence";
        }
        return "low confidence";
    }
}
