package com.createstats.statsmk3.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Tokens {
    private Tokens() {
    }

    public static final int CANVAS = 0xFF0A0B0E;

    public static final int SURFACE = 0xFF111318;

    public static final int RAISED = 0xFF171A21;

    public static final int OVERLAY = 0xFF1E222B;

    public static final int LINE = 0xFF242932;

    public static final int LINE_HI = 0xFF323844;

    public static final int INK = 0xFFECEEF2;

    public static final int INK_2 = 0xFF98A0AD;

    public static final int INK_3 = 0xFF5F6773;

    public static final int ACCENT = 0xFFFF5C90;

    public static final int ACCENT_DEEP = 0xFFF0417A;

    public static final int ACCENT_DIM = 0xFF4A1E32;

    public static final int[] SERIES = {
            0xFF3B82F6,
            0xFFF0417A,
            0xFFBC8309,
            0xFF0FA97A,
            0xFF8B5CF6,
            0xFFE4622A
    };

    public static final int DEVIATION_LO = 0xFF54637A;

    public static int seriesFor(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return SERIES[0];
        }
        int h = 0;
        for (int i = 0; i < channelId.length(); i++) {
            h = h * 31 + channelId.charAt(i);
        }
        return SERIES[Math.floorMod(h, SERIES.length)];
    }

    // never the only signal, always with a label
    public static final int GOOD = 0xFF34D399;
    public static final int CAUTION = 0xFFFBBF24;
    public static final int CRITICAL = 0xFFFF5A47;

    public static final int S1 = 4;
    public static final int S2 = 8;
    public static final int S3 = 12;
    public static final int S4 = 16;
    public static final int S5 = 24;
    public static final int S6 = 32;
    public static final int S7 = 48;

    public static final float R_CHIP = 4.0f;
    public static final float R_CARD = 8.0f;
    public static final float R_PANEL = 12.0f;

    public static final int T_MICRO = 10;
    public static final int T_META = 12;
    public static final int T_BODY = 13;
    public static final int T_SUB = 15;
    public static final int T_TITLE = 19;
    public static final int T_HEAD = 30;
    public static final int T_HERO = 52;

    public static final float DATA_STROKE = 2.0f;

    public static final float BAR_END_RADIUS = 4.0f;

    public static final float FILL_GAP = 2.0f;

    public static final int MIN_HIT = 8;

    public static final int HELD = 0xFFE3B341;

    public static final int LIMIT = 0xFFC9483A;

    public static final int GAS = SERIES[5];

    public static final int ENGINE = SERIES[3];

    public static final int DESTINATION = 0xFF3FC8C8;

    public static final int EARTH = 0xFF5B4128;

    public static final int EARTH_EDGE = 0xFF8C6A3F;

    public static final int LAMP_WHITE = 0xFFFFF4E2;
    public static final int LAMP_RED = 0xFFFF2A1C;
    public static final int LAMP_AMBER = 0xFFFF9C1E;

    public static final int BEAM_BLUE = 0xFF4C8DFF;

    public static int alpha(int argb, float a) {
        final int aa = Math.max(0, Math.min(255, Math.round(a * 255.0f)));
        return (argb & 0x00FFFFFF) | (aa << 24);
    }

    public static int fade(int argb, float mul) {
        final int cur = (argb >>> 24) & 0xFF;
        final int aa = Math.max(0, Math.min(255, Math.round(cur * mul)));
        return (argb & 0x00FFFFFF) | (aa << 24);
    }

    public static int mix(int a, int b, float t) {
        final float k = t < 0 ? 0 : t > 1 ? 1 : t;
        final int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        final int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        final int ra = Math.round(aa + (ba - aa) * k);
        final int rr = Math.round(ar + (br - ar) * k);
        final int rg = Math.round(ag + (bg - ag) * k);
        final int rb = Math.round(ab + (bb - ab) * k);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int statusFor(float frac) {
        if (frac >= 0.85f) {
            return CRITICAL;
        }
        if (frac >= 0.70f) {
            return CAUTION;
        }
        return GOOD;
    }
}
