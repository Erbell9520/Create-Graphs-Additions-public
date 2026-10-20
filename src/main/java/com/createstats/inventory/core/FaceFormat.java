package com.createstats.inventory.core;

import java.util.Locale;

public final class FaceFormat {
    private FaceFormat() {
    }

    public static final int COMPACT_MAX = 5;

    public static String compact(long n) {
        if (n < 0) {
            return "-" + compact(n == Long.MIN_VALUE ? Long.MAX_VALUE : -n);
        }
        if (n < 10_000L) {
            return Long.toString(n);
        }
        return scaled(n, 1_000L, "K", 1_000_000L, "M", 1_000_000_000L, "B", 1_000_000_000_000L, "T");
    }

    private static String scaled(long n, Object... steps) {
        for (int i = 0; i < steps.length; i += 2) {
            final long unit = (Long) steps[i];
            final String suffix = (String) steps[i + 1];
            final double v = n / (double) unit;
            if (v < 9.995) {
                return trimZeros(String.format(Locale.ROOT, "%.2f", v), unit == 1_000L) + suffix;
            }
            if (v < 99.95) {
                return String.format(Locale.ROOT, "%.1f", v) + suffix;
            }
            if (v < 999.5) {
                return Math.round(v) + suffix;
            }
        }
        return Math.round(Math.floor(n / 1e15)) + "Q";
    }

    private static String trimZeros(String s, boolean thousands) {
        return thousands ? String.format(Locale.ROOT, "%.1f", Double.parseDouble(s)) : s;
    }

    public static String grouped(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public static String clock(double seconds, String hoursMinutesTemplate, String daysTemplate) {
        final long s = Math.max(0L, Math.round(seconds));
        if (s < 3600L) {
            return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
        }
        if (s < 86_400L) {
            return String.format(Locale.ROOT, hoursMinutesTemplate, Long.toString(s / 3600L),
                    String.format(Locale.ROOT, "%02d", (s % 3600L) / 60L));
        }
        return String.format(Locale.ROOT, daysTemplate, Long.toString(s / 86_400L));
    }

    public static String rateFigure(double perMinute) {
        final double v = Math.abs(perMinute);
        final String sign = perMinute < 0 ? "-" : "";
        if (v >= 1000.0) {
            return sign + compact(Math.round(v));
        }
        if (v >= 10.0) {
            return sign + Math.round(v);
        }
        final String one = String.format(Locale.ROOT, "%.1f", v);
        return sign + (one.endsWith(".0") ? one.substring(0, one.length() - 2) : one);
    }

    public static String rate(double perMinute, String unitTemplate) {
        return String.format(Locale.ROOT, unitTemplate, rateFigure(perMinute));
    }

    public static String wholeRate(double perMinute) {
        return grouped(Math.round(perMinute));
    }

    public static String buckets(long millibuckets) {
        final double b = millibuckets / 1000.0;
        if (b >= 1000.0) {
            return compact(Math.round(b));
        }
        if (b >= 100.0) {
            return Long.toString(Math.round(b));
        }

        return strip(String.format(Locale.ROOT, "%.1f", b));
    }

    public static String fe(long fe) {
        if (fe >= 1_000_000L) {
            return compact(fe) + " FE";
        }
        if (fe >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fK FE", fe / 1000.0);
        }
        return grouped(fe) + " FE";
    }

    public static String percent(double fraction) {
        return Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0) + "%";
    }

    public static String stockCount(long n) {
        if (n < 1000L) {
            return Long.toString(n);
        }
        if (n < 10_000L) {
            return strip(String.format(Locale.ROOT, "%.1f", n / 1000.0)) + "K";
        }
        if (n < 1_000_000L) {
            return (n / 1000L) + "K";
        }
        return strip(String.format(Locale.ROOT, "%.1f", n / 1_000_000.0)) + "M";
    }

    private static String strip(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static boolean faceSafe(String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    public static int glassWidth(String s, int scale) {
        return s.isEmpty() ? 0 : (s.length() * 6 - 1) * scale;
    }

    public static String trimToGlass(String s, int maxWidth) {
        if (glassWidth(s, 1) <= maxWidth) {
            return s;
        }
        String shown = s;
        while (shown.length() > 3 && glassWidth(shown + ".", 1) > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown.length() < s.length() ? shown + "." : shown;
    }
}
