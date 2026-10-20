package com.createstats.statsmk3.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Text {
    private Text() {
    }

    public static final float MICRO = 0.75f;

    public static final float META = 1.0f;

    public static final float BODY = 1.0f;

    public static final float SUB = 1.5f;

    public static final float TITLE = 2.0f;

    public static final float HERO = 4.0f;

    public static final int LINE_HEIGHT = 9;

    public static float lineHeight(float scale) {
        return LINE_HEIGHT * scale;
    }

    public static String track(String s) {
        return s == null || s.isEmpty() ? "" : s.toUpperCase(java.util.Locale.ROOT);
    }

    public static String truncate(PanelSurface surface, String s, float maxWidth, float scale) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (surface.textWidth(s, scale) <= maxWidth) {
            return s;
        }
        final float ellipsis = surface.textWidth("...", scale);
        final StringBuilder sb = new StringBuilder();
        float w = 0.0f;
        for (int i = 0; i < s.length(); i++) {
            final float cw = surface.textWidth(String.valueOf(s.charAt(i)), scale);
            if (w + cw + ellipsis > maxWidth) {
                break;
            }
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb + "...";
    }

    public static String truncate(Font font, String s, float maxWidth, float scale) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (font.width(s) * scale <= maxWidth) {
            return s;
        }
        final float ellipsis = font.width("...") * scale;
        final StringBuilder sb = new StringBuilder();
        float w = 0.0f;
        for (int i = 0; i < s.length(); i++) {
            final float cw = font.width(String.valueOf(s.charAt(i))) * scale;
            if (w + cw + ellipsis > maxWidth) {
                break;
            }
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb + "...";
    }

    public static List<String> wrap(PanelSurface s, String text, float maxWidth, float scale) {
        final List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (final String word : text.split(" ")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (s.textWidth(candidate, scale) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        for (int i = 0; i < lines.size(); i++) {
            if (s.textWidth(lines.get(i), scale) > maxWidth) {
                lines.set(i, truncate(s, lines.get(i), maxWidth, scale));
            }
        }
        return lines;
    }

    public static List<String> wrap(Font font, String s, float maxWidth, float scale) {
        final List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (final String word : s.split(" ")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) * scale > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static String value(float v) {
        final float a = Math.abs(v);

        if (a >= 10_000_000.0f) {
            return String.format(java.util.Locale.ROOT, "%.0fM", v / 1_000_000.0f);
        }
        if (a >= 1_000_000.0f) {
            return String.format(java.util.Locale.ROOT, "%.1fM", v / 1_000_000.0f);
        }
        if (a >= 100000.0f) {
            return String.format(java.util.Locale.ROOT, "%.0fk", v / 1000.0f);
        }
        if (a >= 1000.0f) {
            return group(Math.round(v));
        }
        if (a >= 100.0f) {
            return String.valueOf(Math.round(v));
        }
        if (a >= 10.0f) {
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    public static String compact(float v) {
        final float a = Math.abs(v);
        if (a >= 1_000_000.0f) {
            return abbreviate(v / 1_000_000.0f, "M");
        }
        if (a >= 1000.0f) {
            return abbreviate(v / 1000.0f, "k");
        }
        if (a >= 100.0f) {
            return String.valueOf(Math.round(v));
        }
        if (a >= 10.0f) {
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String abbreviate(float scaled, String suffix) {
        return String.format(java.util.Locale.ROOT,
                Math.abs(scaled) >= 10.0f ? "%.0f" + suffix : "%.1f" + suffix, scaled);
    }

    public record Fitted(String text, float scale) {
    }

    public static Fitted fitValue(PanelSurface s, float value, float maxWidth, float maxHeight,
                                  float start, float min) {
        final String full = value(value);
        final float scale = fitScale(s, full, maxWidth, maxHeight, start, min);
        if (s.textWidth(full, scale) <= maxWidth) {
            return new Fitted(full, scale);
        }
        final String small = compact(value);
        final float compactScale = fitScale(s, small, maxWidth, maxHeight, start, min);
        if (s.textWidth(small, compactScale) <= maxWidth) {
            return new Fitted(small, compactScale);
        }

        return new Fitted(truncate(s, small, maxWidth, min), min);
    }

    public static float fitScale(PanelSurface s, String text, float maxWidth, float maxHeight,
                                 float start, float min) {
        float scale = Math.max(min, start);
        while (scale > min
                && (s.textWidth(text, scale) > maxWidth || lineHeight(scale) > maxHeight)) {
            scale = Math.max(min, scale - 0.5f);
        }
        return scale;
    }

    public static String group(long v) {
        final String s = String.valueOf(Math.abs(v));
        final StringBuilder sb = new StringBuilder();
        final int lead = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + sb;
    }

    public static String delta(float v, int decimals) {
        final String body = String.format(java.util.Locale.ROOT, "%." + decimals + "f", Math.abs(v));
        return (v < 0 ? "-" : "+") + body;
    }
}
