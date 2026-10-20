package com.createstats.cinecam.core;

public final class FilmFormat {
    public static final int[] RATES = {24, 25, 30, 50, 60};
    public static final int DEFAULT_RATE = 30;

    public static final int MAX_WIDTH = 3840;
    public static final int MIN_BITRATE = 2_000_000;
    public static final int MAX_BITRATE = 80_000_000;

    public enum Resolution {
        NATIVE("native", 0, "Native"),
        HD("720p", 1280, "720p"),
        FULL_HD("1080p", 1920, "1080p"),
        QHD("1440p", 2560, "1440p"),
        UHD("4k", 3840, "4K");

        private final String key;
        private final int width;
        private final String label;

        Resolution(String key, int width, String label) {
            this.key = key;
            this.width = width;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public int width() {
            return width;
        }

        public static Resolution byKey(String key) {
            for (Resolution r : values()) {
                if (r.key.equals(key)) return r;
            }
            return DEFAULT_RESOLUTION;
        }
    }

    public enum Quality {
        STANDARD("standard", 0.08),
        HIGH("high", 0.15),
        BEST("best", 0.30);

        private final String key;
        private final double bitsPerPixel;

        Quality(String key, double bitsPerPixel) {
            this.key = key;
            this.bitsPerPixel = bitsPerPixel;
        }

        public String key() {
            return key;
        }

        public double bitsPerPixel() {
            return bitsPerPixel;
        }

        public static Quality byKey(String key) {
            for (Quality q : values()) {
                if (q.key.equals(key)) return q;
            }
            return DEFAULT_QUALITY;
        }
    }

    public enum Shape {
        SCOPE("scope", 2.39, "2.39:1"),
        FLAT("flat", 1.85, "1.85:1"),
        VIDEO("video", 16.0 / 9.0, "16:9");

        private final String key;
        private final double aspect;
        private final String label;

        Shape(String key, double aspect, String label) {
            this.key = key;
            this.aspect = aspect;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public double aspect() {
            return aspect;
        }

        public String label() {
            return label;
        }

        public Shape next() {
            Shape[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static Shape byKey(String key) {
            for (Shape s : values()) {
                if (s.key.equals(key)) return s;
            }
            return DEFAULT_SHAPE;
        }
    }

    public enum Speed {
        NORMAL("normal", 1.0),
        SLOW_HALF("slow_2", 0.5),
        SLOW_QUARTER("slow_4", 0.25),
        LAPSE_2("lapse_2", 2.0),
        LAPSE_5("lapse_5", 5.0),
        LAPSE_10("lapse_10", 10.0),
        LAPSE_30("lapse_30", 30.0),
        LAPSE_60("lapse_60", 60.0);

        private final String key;
        private final double factor;

        Speed(String key, double factor) {
            this.key = key;
            this.factor = factor;
        }

        public String key() {
            return key;
        }

        public double factor() {
            return factor;
        }

        public boolean slow() {
            return factor < 1.0;
        }

        public boolean lapse() {
            return factor > 1.0;
        }

        public String label() {
            return factor >= 1.0 ? String.format(java.util.Locale.ROOT, "%dx", Math.round(factor))
                    : "1/" + Math.round(1.0 / factor) + "x";
        }

        public static Speed byKey(String key) {
            for (Speed s : values()) {
                if (s.key.equals(key)) return s;
            }
            return DEFAULT_SPEED;
        }
    }

    public static final Resolution DEFAULT_RESOLUTION = Resolution.FULL_HD;
    public static final Quality DEFAULT_QUALITY = Quality.HIGH;
    public static final Shape DEFAULT_SHAPE = Shape.SCOPE;
    public static final Speed DEFAULT_SPEED = Speed.NORMAL;

    public static final Speed[] SPEED_CYCLE = {Speed.NORMAL, Speed.SLOW_HALF, Speed.SLOW_QUARTER, Speed.LAPSE_2,
            Speed.LAPSE_5, Speed.LAPSE_10, Speed.LAPSE_30, Speed.LAPSE_60};

    private FilmFormat() {
    }

    public static int clampRate(int fps) {
        int best = RATES[0];
        for (int r : RATES) {
            if (Math.abs(r - fps) < Math.abs(best - fps)) best = r;
        }
        return best;
    }

    public static int[] nativeSize(double aspect, int windowW, int windowH) {
        int w = Math.max(16, Math.min(MAX_WIDTH, windowW) / 16 * 16);
        int h = even((int) Math.round(w / aspect));
        if (h > windowH) {
            h = Math.max(2, windowH & ~1);
            w = Math.max(16, (int) (h * aspect) / 16 * 16);
        }
        return new int[]{w, h};
    }

    public static boolean fits(Resolution r, double aspect, int windowW, int windowH) {
        return r == Resolution.NATIVE || r.width() <= nativeSize(aspect, windowW, windowH)[0];
    }

    public static int[] size(Resolution r, double aspect, int windowW, int windowH) {
        if (r == Resolution.NATIVE || !fits(r, aspect, windowW, windowH)) return nativeSize(aspect, windowW, windowH);
        return new int[]{r.width(), even((int) Math.round(r.width() / aspect))};
    }

    public static String label(int w, int h) {
        return switch (w) {
            case 1280 -> "720p";
            case 1920 -> "1080p";
            case 2560 -> "1440p";
            case 3840 -> "4K";
            default -> w + "x" + h;
        };
    }

    public static String fileSafe(String name) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < name.length() && b.length() < 40; i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c)) {
                b.append(' ');
            } else if (c >= 32 && c != 127 && "<>:\"/\\|?*".indexOf(c) < 0) {
                b.append(c);
            }
        }
        String s = b.toString().trim().replaceAll(" {2,}", " ");
        while (!s.isEmpty() && (s.endsWith(".") || s.endsWith(" "))) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static int bitrate(int w, int h, int fps, Quality q) {
        long b = (long) (w * (double) h * fps * q.bitsPerPixel());
        return (int) Math.max(MIN_BITRATE, Math.min(MAX_BITRATE, b));
    }

    public static double megabytesPerMinute(int bitrate) {
        return bitrate * 60.0 / 8.0 / 1048576.0;
    }

    public static int pack(int rate, Resolution r, Quality q, Shape s, Speed v) {
        int at = 0;
        for (int i = 0; i < RATES.length; i++) {
            if (RATES[i] == clampRate(rate)) at = i;
        }
        return at | r.ordinal() << 4 | q.ordinal() << 8 | s.ordinal() << 12 | v.ordinal() << 16;
    }

    public static Speed speedOf(int packed) {
        return Speed.values()[clamp(packed >> 16 & 0xF, Speed.values().length)];
    }

    public static int rateOf(int packed) {
        return RATES[clamp(packed & 0xF, RATES.length)];
    }

    public static Resolution resolutionOf(int packed) {
        return Resolution.values()[clamp(packed >> 4 & 0xF, Resolution.values().length)];
    }

    public static Quality qualityOf(int packed) {
        return Quality.values()[clamp(packed >> 8 & 0xF, Quality.values().length)];
    }

    public static Shape shapeOf(int packed) {
        return Shape.values()[clamp(packed >> 12 & 0xF, Shape.values().length)];
    }

    private static int clamp(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    private static int even(int v) {
        return Math.max(2, v + (v & 1));
    }
}
