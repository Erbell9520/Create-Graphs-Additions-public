package com.createstats.gui.home;

public final class HomeLayout {
    public static final int FULL_H = 300;

    public static final int MIN_H = 196;

    public static final int SIDE_MAX = 232;
    public static final int SIDE_MIN = 198;

    public static final int TITLE_H = 18;

    public static final int FOOT_H = 24;

    public static final int PAD = 7;

    public static final int PARCHMENT = 8;
    public static final int HOTBAR_W = 182;
    public static final int HOTBAR_H = 22;

    public static final int BUTTON_H = 14;

    public static final int SCROLLER_W = 7;

    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public final int mapX;
    public final int mapY;
    public final int mapSize;

    public final int planX;
    public final int planY;
    public final int planSize;

    public final int footY;

    public final int sideX;
    public final int sideW;
    public final int sideY;

    public final int sideBottom;

    public final int scenesX;
    public final int scenesY;

    private HomeLayout(int screenW, int screenH) {
        final int height = Math.max(MIN_H, Math.min(FULL_H, screenH - 24));
        int map = height - TITLE_H - FOOT_H;
        int side = SIDE_MAX;
        final int room = screenW - 8 - 3 * PAD;
        if (map + side > room) {
            side = Math.max(SIDE_MIN, room - map);
            if (map + side > room) {
                map = Math.max(MIN_H - TITLE_H - FOOT_H, room - side);
            }
        }
        this.h = height;
        this.mapSize = map;
        this.sideW = side;
        this.w = 3 * PAD + map + side;
        this.x = (screenW - w) / 2;
        this.y = (screenH - h) / 2;
        this.mapX = x + PAD;
        this.mapY = y + TITLE_H;
        this.planX = mapX + PARCHMENT;
        this.planY = mapY + PARCHMENT;
        this.planSize = map - 2 * PARCHMENT;
        this.footY = mapY + map + 5;
        this.sideX = mapX + map + PAD;
        this.sideY = y + TITLE_H;
        this.scenesX = sideX + (side - HOTBAR_W) / 2;
        this.scenesY = y + h - PAD - HOTBAR_H;
        this.sideBottom = scenesY - 13;
    }

    public static HomeLayout of(int screenW, int screenH) {
        return new HomeLayout(screenW, screenH);
    }

    public boolean onPlan(double mx, double my) {
        return mx >= planX && mx < planX + planSize && my >= planY && my < planY + planSize;
    }

    public boolean onTitle(double mx, double my, int nameWidth) {
        return mx >= x + 6 && mx < x + 10 + nameWidth && my >= y + 3 && my < y + TITLE_H - 1;
    }
}
