package com.createstats.client.statspower;

import java.util.Locale;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// one formatter or 12.4k sits next to 12400 and you do maths
@OnlyIn(Dist.CLIENT)
public final class PowerFmt {
    private PowerFmt() {
    }

    public static String fe(double value) {
        return compact(value);
    }

    public static String rate(double value) {
        return compact(value) + " FE/t";
    }

    public static String bare(double value) {
        return compact(value);
    }

    public static String mbt(double value) {
        return compact(value) + " mB/t";
    }

    public static String volume(double mb) {
        if (Math.abs(mb) >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM mB", mb / 1_000_000.0);
        }
        if (Math.abs(mb) >= 1000.0) {
            return String.format(Locale.ROOT, "%.1f B", mb / 1000.0);
        }
        return String.format(Locale.ROOT, "%.0f mB", mb);
    }

    public static String compact(double value) {
        final double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0) {
            return String.format(Locale.ROOT, "%.2fG", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (abs >= 100_000.0) {
            return String.format(Locale.ROOT, "%.0fk", value / 1000.0);
        }
        if (abs >= 10_000.0) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        }
        if (abs >= 1000.0) {
            return String.format(Locale.ROOT, "%.2fk", value / 1000.0);
        }
        if (abs >= 100.0 || abs == Math.floor(abs)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (abs >= 10.0) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String signed(double value) {
        final String body = compact(Math.abs(value));
        if (value > 0.5) {
            return "+" + body;
        }
        if (value < -0.5) {
            return "-" + body;
        }
        return body;
    }

    public static String pct(double value) {
        if (Math.abs(value) >= 99.95) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    public static String pct0(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    public static String endurance(float ticks, boolean draining) {
        if (!draining || ticks <= 0.0f) {
            return "stable";
        }
        final int seconds = Math.round(ticks / 20.0f);
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        }
        if (seconds >= 60) {
            return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        }
        return seconds + "s";
    }

    public static String ago(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    public static String prettyFluidId(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return "";
        }
        final int colon = fluidId.indexOf(':');
        final String path = colon >= 0 ? fluidId.substring(colon + 1) : fluidId;
        final StringBuilder out = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '_' || c == '.' || c == '/') {
                out.append(' ');
                upper = true;
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }
}
