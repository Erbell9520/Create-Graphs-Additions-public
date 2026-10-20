package com.createstats.aviatorhelmet;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AviatorHudLayout {
    public static final int COMPASS_PX_PER_DEG = 2;
    public static final int TAPE_PX_PER_BLOCK = 3;

    static final int COMPASS_HALF = 100;
    static final int COMPASS_HALF_MIN = 50;

    static final int TAPE_HALF = 90;
    public static final int STATOSCOPE_HALF = 34;
    public static final int TELEGRAPH_R = 34;

    static final int BANNER_WIDTH = 440;
    public static final int BANNER_UP = 58;
    public static final int LINE = 10;

    public final int width;
    public final int height;
    public final int compassX;
    public final int compassHalf;
    public final int compassTop;
    public final int tapeX;
    public final int tapeMid;
    public final int tapeHalf;
    public final int statoscopeX;
    public final int telegraphX;
    public final int telegraphY;
    public final int reticleX;
    public final int reticleY;
    public final int bannerWidth;
    public final int modeX;
    public final int modeY;

    private AviatorHudLayout(int width, int height, int compassTop) {
        this.width = width;
        this.height = height;
        this.compassX = width / 2;

        this.compassHalf = clamp(Math.min((width - 300) / 2, width / 2 - 158), COMPASS_HALF_MIN, COMPASS_HALF);
        this.compassTop = compassTop;
        this.tapeX = 46;
        this.tapeMid = height / 2 - 10;

        this.tapeHalf = clamp(Math.min(tapeMid - 36, height - BANNER_UP - LINE - 18 - tapeMid), 24, TAPE_HALF);
        this.statoscopeX = tapeX + 50;
        this.telegraphX = width - 78;
        this.telegraphY = clamp((int) Math.round(height * 0.375), 50, height - 128);
        this.reticleX = width / 2;
        this.reticleY = height / 2;
        this.bannerWidth = Math.min(BANNER_WIDTH, width - 40);
        this.modeX = 8;
        this.modeY = 8;
    }

    public static AviatorHudLayout of(int width, int height) {
        return new AviatorHudLayout(width, height, 14);
    }

    public static AviatorHudLayout of(int width, int height, int top) {
        return new AviatorHudLayout(width, height, Math.max(14, top));
    }

    public int[] compassSpan() {
        return new int[] {compassX - compassHalf - 26, compassX + compassHalf + 10};
    }

    public int bannerTop(int lines) {
        return height - BANNER_UP - LINE * (Math.max(1, lines) - 1);
    }

    public Map<String, int[]> boxes(int bannerLines) {
        Map<String, int[]> out = new LinkedHashMap<>();
        out.put("mode", new int[] {modeX, modeY, modeX + 120, modeY + 19});
        out.put("compass", new int[] {compassX - compassHalf - 26, compassTop - 3, compassX + compassHalf + 10,
            compassTop + 36});
        out.put("tape", new int[] {tapeX - 30, tapeMid - tapeHalf - 4, tapeX + 36, tapeMid + tapeHalf + 16});
        out.put("statoscope", new int[] {statoscopeX - 8, tapeMid - STATOSCOPE_HALF - 12, statoscopeX + 36,
            tapeMid + STATOSCOPE_HALF + 5});
        out.put("telegraph", new int[] {telegraphX - TELEGRAPH_R - 8, telegraphY - TELEGRAPH_R - 3,
            telegraphX + TELEGRAPH_R + 8, telegraphY + 60});
        out.put("reticle", new int[] {reticleX - 27, reticleY - 10, reticleX + 27, reticleY + 41});
        int top = bannerTop(bannerLines);
        out.put("banner", new int[] {width / 2 - bannerWidth / 2, top, width / 2 + bannerWidth / 2,
            top + LINE * Math.max(1, bannerLines)});
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
