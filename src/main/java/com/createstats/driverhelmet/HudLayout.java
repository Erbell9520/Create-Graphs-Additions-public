package com.createstats.driverhelmet;

public final class HudLayout {
    public static final int LEDS = 15;
    public static final int LED = 4;
    public static final int LED_GAP = 2;
    public static final int BAR_WIDTH = LEDS * LED + (LEDS - 1) * LED_GAP;

    public static final int TOP_PLATE_HEIGHT = 32;

    public static final int DASH_WIDTH = 110;
    public static final int DASH_HEIGHT = 62;
    public static final int WHEELS_HEIGHT = 84;

    public static final int G_WIDTH = 110;

    public static final int G_HEIGHT = 184;
    public static final int MARGIN = 10;
    static final int GAP = 4;

    static final int CHAT_BOTTOM = 48;
    static final int CHAT_HEIGHT = 90;

    static final int TOP_PLATE_BELOW_BAR = TOP_PLATE_HEIGHT - 8;

    static final int HOTBAR_HALF = 91;
    static final int OFFHAND = 29;

    static final int STATUS_ROWS = 60;

    static final int CROSSHAIR_CLEAR = 24;

    public final int barX;
    public final int barY;

    public final int dashX0;
    public final int dashY0;
    public final int dashX1;
    public final int dashY1;

    public final boolean wheels;
    public final int wheelsY0;
    public final int wheelsY1;

    public final boolean g;
    public final int gX0;
    public final int gY0;
    public final int gX1;
    public final int gY1;

    private HudLayout(int width, int height) {
        barX = width / 2 - BAR_WIDTH / 2;
        barY = 8;
        dashX0 = width - MARGIN - DASH_WIDTH;
        dashX1 = dashX0 + DASH_WIDTH;
        final boolean besideHotbar = dashX0 >= width / 2 + HOTBAR_HALF + OFFHAND + GAP;
        dashY1 = besideHotbar ? height - 12 : height - STATUS_ROWS - GAP;
        dashY0 = dashY1 - DASH_HEIGHT;
        wheelsY1 = dashY0 - GAP;
        wheelsY0 = wheelsY1 - WHEELS_HEIGHT;
        wheels = wheelsY0 >= 2 && (wheelsY0 >= TOP_PLATE_HEIGHT + GAP || dashX0 >= barX + BAR_WIDTH + 6 + GAP);
        gX0 = MARGIN;
        gX1 = gX0 + G_WIDTH;
        final int centre = (int) Math.round(height * 0.39);
        // lift clear of chat at 1280x720 scale 2

        final int chatTop = height - CHAT_BOTTOM - CHAT_HEIGHT;
        int y0 = Math.max(TOP_PLATE_HEIGHT + GAP, centre - G_HEIGHT / 2);
        if (y0 + G_HEIGHT > chatTop - GAP) {
            y0 = Math.max(TOP_PLATE_HEIGHT + GAP, chatTop - GAP - G_HEIGHT);
        }
        gY0 = y0;
        gY1 = gY0 + G_HEIGHT;
        g = gY1 <= height - STATUS_ROWS - GAP && gX1 < width / 2 - CROSSHAIR_CLEAR
                && (height < 360 || gY1 <= chatTop);
        final int crosshairLimit = height / 2 - CROSSHAIR_CLEAR - TOP_PLATE_BELOW_BAR;
        int limit = crosshairLimit;
        final int plateX0 = barX - 6;
        final int plateX1 = barX + BAR_WIDTH + 5;
        if (g && plateX0 < gX1 && gX0 < plateX1) {
            limit = Math.min(limit, gY0 - GAP - TOP_PLATE_BELOW_BAR);
        }
        if (wheels && plateX0 < dashX1 && dashX0 < plateX1) {
            limit = Math.min(limit, wheelsY0 - GAP - TOP_PLATE_BELOW_BAR);
        }
        maxBarY = Math.max(barY, limit);
    }

    public final int maxBarY;

    public static HudLayout of(int width, int height) {
        return new HudLayout(width, height);
    }

    public int barYBelow(int blockedBottom) {
        return Math.min(maxBarY, Math.max(barY, blockedBottom + GAP + 4));
    }
}
