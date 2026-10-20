package com.createstats.driverhelmet;

public final class HudBoot {
    public static final double START = 0.26;

    public static final double SELF_TEST = LedBar.SELF_TEST + 0.14;

    public static final double FADE = 0.10;

    public static final double CRAWL = 150.0;

    public static final double WELCOME_MAX_SPEED = 3.0;

    public enum Phase { DARK, SELF_TEST, WELCOME, LIVE }

    public enum Anchor { NONE, STROKE, WORN, HELM }

    private HudBoot() {
    }

    public static Anchor anchor(double stroke, double worn, double helm) {
        Anchor best = Anchor.NONE;
        double at = Double.NEGATIVE_INFINITY;
        if (!Double.isNaN(stroke) && (Double.isNaN(worn) || stroke >= worn)) {
            best = Anchor.STROKE;
            at = stroke;
        } else if (!Double.isNaN(worn)) {
            best = Anchor.WORN;
            at = worn;
        }
        if (!Double.isNaN(helm) && helm > at) {
            best = Anchor.HELM;
        }
        return best;
    }

    public static double welcomeLength(int textWidth, int window) {
        return (textWidth + window) / CRAWL;
    }

    public static Phase phase(double t, boolean welcome, double welcomeLength) {
        if (t < START) {
            return Phase.DARK;
        }
        if (t < START + SELF_TEST) {
            return Phase.SELF_TEST;
        }
        if (welcome && t < START + SELF_TEST + welcomeLength) {
            return Phase.WELCOME;
        }
        return Phase.LIVE;
    }

    public static double into(double t, Phase phase) {
        return switch (phase) {
            case DARK -> t;
            case SELF_TEST -> t - START;
            case WELCOME, LIVE -> t - START - SELF_TEST;
        };
    }

    public static double fade(double t) {
        return Math.max(0.0, Math.min(1.0, (t - START) / FADE));
    }

    public static boolean welcomeAllowed(double speed) {
        return Math.abs(speed) <= WELCOME_MAX_SPEED;
    }
}
