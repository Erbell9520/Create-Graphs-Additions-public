package com.createstats.book;

public final class BookTokens {
    private BookTokens() {
    }

    public static final int VINYL = 0xFF2B333C;
    public static final int VINYL_OUTLINE = 0xFF0C0E10;
    public static final int VINYL_LIT = 0xFF46515C;

    public static final float VINYL_GRAIN = 0.03f;

    public static final int PAPER = 0xFFF0F1EC;
    public static final int PAPER_EDGE = 0xFFB9BCB6;

    public static final float PAPER_GRAIN = 0.018f;

    public static final int GRAIN_CELL = 2;

    public static final int[] CURL_ALPHA = {0x30, 0x20, 0x12, 0x08};
    public static final int CURL_STEP = 3;

    public static final int PLATE = 0xFF5D646B;
    public static final int PLATE_LIGHT = 0xFF9AA2AA;
    public static final int PLATE_DARK = 0xFF353A40;
    public static final int RING = 0xFF8C949C;
    public static final int RING_LIT = 0xFFE2E6EA;
    public static final int RING_SHADE = 0xFF4A5056;
    public static final int HOLE = 0xFF23272B;
    public static final int HOLE_RIM = 0xFF7E837F;

    public static final int INK = 0xFF17191C;
    public static final int MUTE = 0xFF5E646B;
    public static final int RULE = 0xFF9AA0A6;
    public static final int ROW_RULE = 0xFFDADDD8;
    public static final int WHITE = 0xFFFFFFFF;

    public static final int BUTTON_SHADOW = 0xFF3F3F3F;

    public static final int SCENE_WELL = 0xFFE3E5DF;

    public static final int WORLD_SKY = 0xFF78A7FF;

    public static final int[] SECTION = {
            0xFF59626C, 0xFF1F7F99, 0xFFC8402A, 0xFFB27A12, 0xFF3D7A35, 0xFFA8507E, 0xFF6E4A9A, 0xFF2A2A2A,
    };

    public static final int ROW_TINT_ALPHA = 0x26;

    public static final int MARK_RUNNING = 0xFF2E9E4F;
    public static final int MARK_IDLE = 0xFF6E747A;
    public static final int MARK_CAUTION = 0xFFD99A1E;
    public static final int MARK_FAULT = 0xFFC8402A;

    public static final int RIBBON = 0xFFB2321F;
    public static final int RIBBON_LIT = 0xFFD2513A;
    public static final int RIBBON_EDGE = 0xFF5E150D;

    public static final int LINE = 9;
    public static final int LEAD = 10;

    public static final int BINDER_SIDE = 28;
    public static final int BINDER_TOP = 9;
    public static final int BINDER_BOTTOM = 8;

    public static final int GUTTER = 10;

    public static final int SINGLE_RING_ZONE = 36;

    public static final int CANVAS_MARGIN = 12;

    public static final int SPREAD_SHEET_MIN_W = 352;
    public static final int SPREAD_SHEET_MAX_W = 430;
    public static final int SPREAD_SHEET_MIN_H = 360;
    public static final int SHEET_MAX_H = 560;

    public static final int SINGLE_SHEET_MAX_W = 640;

    public static final int TAB_W = 22;
    public static final int TAB_H = 56;
    public static final int TAB_PITCH = 60;
    public static final int TAB_NUMBERED_MIN_H = 36;

    public static final int TAB_PULL = 4;

    public static final int BAND_H = 16;

    public static final int FOOTER_H = 16;

    public static final int PAD_OPEN = 15;
    public static final int PAD_CURL = 18;

    public static final int PAD_PUNCHED = 22;
    public static final int PAD_SINGLE_OPEN = 12;

    public static final int PAD_TOP = 8;
    public static final int PAD_BOTTOM = 6;

    public static final int PIN_W = 13;
    public static final int PIN_H = 10;
    public static final int PIN_PITCH = 13;

    public static final int PIN_RUN = 20;

    public static final int PART_GAP = 9;

    public static final int TURN_TICKS = 6;
    public static final int RIFFLE_TICKS = 2;

    public static final int RIFFLE_MAX = 4;

    public static int lerp(int a, int b, float t) {
        final int aa = a >>> 24;
        final int ar = (a >> 16) & 0xFF;
        final int ag = (a >> 8) & 0xFF;
        final int ab = a & 0xFF;
        final int ba = b >>> 24;
        final int br = (b >> 16) & 0xFF;
        final int bg = (b >> 8) & 0xFF;
        final int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int alpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int well(int section) {
        return lerp(section, WHITE, 0.78f);
    }

    public static int tabLit(int section) {
        return lerp(section, WHITE, 0.35f);
    }

    public static int tabShade(int section) {
        return lerp(section, 0xFF000000, 0.35f);
    }
}
