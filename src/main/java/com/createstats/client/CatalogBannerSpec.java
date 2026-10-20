package com.createstats.client;

// the numbers here are the ones the banner script prints, keep them matched
public enum CatalogBannerSpec {
    STATS("stats", 4, 35, 1),
    POWER("power", 3, 20, 2),
    MACHINES("machines", 3, 12, 2),
    TRANSPORT("transport", 4, 55, 1),
    HOME("home", 5, 18, 4),
    SECURITY("security", 21, 24, 1);

    public static final int FRAME_WIDTH = 162;
    public static final int FRAME_HEIGHT = 18;

    public static final int TICK_MILLIS = 50;

    public final String key;
    public final int intro;
    public final int loop;
    public final int frameTicks;

    CatalogBannerSpec(String key, int intro, int loop, int frameTicks) {
        this.key = key;
        this.intro = intro;
        this.loop = loop;
        this.frameTicks = frameTicks;
    }

    public int frames() {
        return 1 + this.intro + this.loop;
    }

    public int textureHeight() {
        return this.frames() * FRAME_HEIGHT;
    }

    public String texturePath() {
        return "textures/gui/catalog/banner_" + this.key + ".png";
    }

    public static CatalogBannerSpec of(String key) {
        for (CatalogBannerSpec spec : values()) {
            if (spec.key.equals(key)) {
                return spec;
            }
        }
        return null;
    }
}
