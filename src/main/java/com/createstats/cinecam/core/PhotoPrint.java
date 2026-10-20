package com.createstats.cinecam.core;

public final class PhotoPrint {
    public static final int MIN_SIDE = 1;
    public static final int MAX_SIDE = 4;
    public static final int DEFAULT_SIDE = 1;

    public static final int MAP_PIXELS = MapPalette.SIZE;

    public enum Target {
        MAP("map", true, false),
        FILE("file", false, true),
        BOTH("both", true, true);

        private final String key;
        private final boolean maps;
        private final boolean file;

        Target(String key, boolean maps, boolean file) {
            this.key = key;
            this.maps = maps;
            this.file = file;
        }

        public String key() {
            return key;
        }

        public boolean maps() {
            return maps;
        }

        public boolean file() {
            return file;
        }

        public static Target byKey(String key) {
            for (Target t : values()) {
                if (t.key.equals(key)) return t;
            }
            return DEFAULT_TARGET;
        }
    }

    public static final Target DEFAULT_TARGET = Target.MAP;

    private PhotoPrint() {
    }

    public static int clampSide(int side) {
        return Math.max(MIN_SIDE, Math.min(MAX_SIDE, side));
    }

    public static int mapsFor(int side) {
        int s = clampSide(side);
        return s * s;
    }

    public static int pixelsFor(int side) {
        return clampSide(side) * MAP_PIXELS;
    }

    public static int pack(Target target, int side) {
        return target.ordinal() | clampSide(side) << 4;
    }

    public static Target targetOf(int packed) {
        Target[] all = Target.values();
        return all[Math.max(0, Math.min(all.length - 1, packed & 0xF))];
    }

    public static int sideOf(int packed) {
        return clampSide(packed >> 4 & 0xF);
    }

    public static byte[] tile(byte[] print, int side, int row, int col) {
        int s = clampSide(side);
        int w = s * MAP_PIXELS;
        if (print.length != w * w) throw new IllegalArgumentException("a " + s + " by " + s + " print is " + w * w + " pixels, not " + print.length);
        if (row < 0 || row >= s || col < 0 || col >= s) throw new IllegalArgumentException("no tile " + row + ", " + col);
        byte[] out = new byte[MAP_PIXELS * MAP_PIXELS];
        for (int y = 0; y < MAP_PIXELS; y++) {
            System.arraycopy(print, (row * MAP_PIXELS + y) * w + col * MAP_PIXELS, out, y * MAP_PIXELS, MAP_PIXELS);
        }
        return out;
    }
}
