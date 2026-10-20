package com.createstats.power;

import java.util.Locale;

// not the limiter ladder, nobody docks a train to trickle charge at 4 FE/t
public final class PortableFeLadder {
    public static final int FLOOR_FE = 256;

    public static final int DEFAULT_CEILING_FE = 32768;

    public static final int DEFAULT_FE = 8192;

    public static final int MILESTONE = 2;

    private static final int FIRST_NOTCH = LimiterLadder.nearestNotch(FLOOR_FE);

    private PortableFeLadder() {
    }

    public static int maxDial(int ceilingFe) {
        int top = FIRST_NOTCH;
        for (int notch = FIRST_NOTCH; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            if (LimiterLadder.feAt(notch) > ceilingFe) {
                break;
            }
            top = notch;
        }
        return top - FIRST_NOTCH;
    }

    public static int feAt(int dial, int ceilingFe) {
        return LimiterLadder.feAt(FIRST_NOTCH + clamp(dial, ceilingFe));
    }

    public static int dialFor(int fe, int ceilingFe) {
        return clamp(LimiterLadder.nearestNotch(Math.max(FLOOR_FE, fe)) - FIRST_NOTCH, ceilingFe);
    }

    public static int defaultDial(int ceilingFe) {
        return dialFor(DEFAULT_FE, ceilingFe);
    }

    public static int clamp(int dial, int ceilingFe) {
        return dial < 0 ? 0 : Math.min(dial, maxDial(ceilingFe));
    }

    public static final int CURSOR_TEXT_MAX_PX = 53;

    public static String cursorLabel(int fe) {
        final String[] candidates = {
                LimiterLadder.format(fe),
                String.format(Locale.ROOT, "%.1fk FE/t", fe / 1000.0),
                String.format(Locale.ROOT, "%.0fk FE/t", fe / 1000.0),
                String.format(Locale.ROOT, "%.2fM FE/t", fe / 1_000_000.0),
                String.format(Locale.ROOT, "%.1fM FE/t", fe / 1_000_000.0),
        };
        for (String candidate : candidates) {
            if (textWidth(candidate) <= CURSOR_TEXT_MAX_PX) {
                return candidate;
            }
        }
        return String.format(Locale.ROOT, "%.0fM FE/t", fe / 1_000_000.0);
    }

    static int textWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += switch (text.charAt(i)) {
                case ',', '.' -> 2;
                case ' ', 't' -> 4;
                case 'k' -> 5;
                default -> 6;
            };
        }
        return width;
    }
}
