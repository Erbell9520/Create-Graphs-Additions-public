package com.createstats.client;

import com.createstats.ModConfiguration;

public final class DisplayPrefs {
    private DisplayPrefs() {
    }

    public static final float BASE_FULL_RANGE = 18.0f;

    public static final float BASE_REDUCED_RANGE = 48.0f;

    public static final float FADE_BAND = 0.3f;

    public enum Simplify {
        NEVER("Never", "The face draws everything at any range it renders at",
                Float.POSITIVE_INFINITY),
        FAR("Far", "Holds full detail roughly twice as far as normal", 1.8f),
        BALANCED("Balanced", "Full detail across a room, simplified across the factory", 1.0f),
        AGGRESSIVE("Aggressive", "Simplifies early. Cheapest on a large wall of panels", 0.55f);

        private final String label;
        private final String detail;
        private final float rangeFactor;

        Simplify(String label, String detail, float rangeFactor) {
            this.label = label;
            this.detail = detail;
            this.rangeFactor = rangeFactor;
        }

        public String label() {
            return this.label;
        }

        public String detail() {
            return this.detail;
        }

        public float rangeFactor() {
            return this.rangeFactor;
        }

        public static Simplify parse(String raw) {
            if (raw != null) {
                for (final Simplify s : values()) {
                    if (s.name().equalsIgnoreCase(raw.trim())) {
                        return s;
                    }
                }
            }
            return BALANCED;
        }
    }

    public static Simplify simplify() {
        try {
            return Simplify.parse(ModConfiguration.CLIENT.panelSimplify.get());
        } catch (final RuntimeException | LinkageError ignored) {
            return Simplify.BALANCED;
        }
    }

    public static void setSimplify(Simplify mode) {
        try {
            ModConfiguration.CLIENT.panelSimplify.set(mode.name());
            ModConfiguration.CLIENT_SPEC.save();
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    public static float rangeTrim() {
        try {
            return (float) (double) ModConfiguration.CLIENT.panelDetailRangeTrim.get();
        } catch (final RuntimeException | LinkageError ignored) {
            return 1.0f;
        }
    }

    public static boolean crossFade() {
        try {
            return ModConfiguration.CLIENT.panelCrossFade.get();
        } catch (final RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static void setCrossFade(boolean value) {
        try {
            ModConfiguration.CLIENT.panelCrossFade.set(value);
            ModConfiguration.CLIENT_SPEC.save();
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    public static boolean keepHeader() {
        try {
            return ModConfiguration.CLIENT.panelKeepHeader.get();
        } catch (final RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static void setKeepHeader(boolean value) {
        try {
            ModConfiguration.CLIENT.panelKeepHeader.set(value);
            ModConfiguration.CLIENT_SPEC.save();
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    public static int maxRenderDistance() {
        try {
            return ModConfiguration.CLIENT.maxRenderDistance.get();
        } catch (final RuntimeException | LinkageError ignored) {
            return 128;
        }
    }

    public static void setMaxRenderDistance(int blocks) {
        try {
            ModConfiguration.CLIENT.maxRenderDistance.set(Math.max(16, Math.min(128, blocks)));
            ModConfiguration.CLIENT_SPEC.save();
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    // half overall size, half short side. area alone gives a 6x1 strip the reach of a 3x3
    public static float sizeFactor(int multiblockWidth, int multiblockHeight) {
        final int w = Math.max(1, multiblockWidth);
        final int h = Math.max(1, multiblockHeight);
        final float area = (float) Math.sqrt(w * (double) h);
        final float shortSide = Math.min(w, h);
        final float f = area * 0.5f + shortSide * 0.5f;
        return f < 1.0f ? 1.0f : Math.min(f, 4.0f);
    }

    public static float fullRangeFor(int multiblockWidth, int multiblockHeight) {
        return BASE_FULL_RANGE * sizeFactor(multiblockWidth, multiblockHeight)
                * simplify().rangeFactor() * rangeTrim();
    }

    public static float glyphRangeFor(int multiblockWidth, int multiblockHeight) {
        return BASE_REDUCED_RANGE * sizeFactor(multiblockWidth, multiblockHeight)
                * simplify().rangeFactor() * rangeTrim();
    }
}
