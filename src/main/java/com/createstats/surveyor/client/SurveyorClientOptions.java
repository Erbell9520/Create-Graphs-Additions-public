package com.createstats.surveyor.client;

import com.createstats.ModConfiguration;

public final class SurveyorClientOptions {
    private SurveyorClientOptions() {
    }

    public static boolean alwaysShow() {
        try {
            return ModConfiguration.CLIENT.pathwaysAlwaysShown.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean gogglesShow() {
        try {
            return ModConfiguration.CLIENT.pathwaysWithGoggles.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public enum Snap {
        OFF("off"),

        GRID("grid"),

        ROAD("road"),

        LINES("lines");

        public final String key;

        Snap(String key) {
            this.key = key;
        }

        public static Snap byKey(String key) {
            for (Snap s : values()) if (s.key.equals(key)) return s;
            return OFF;
        }
    }

    private static Snap snap = Snap.OFF;

    public static Snap snap() {
        return snap;
    }

    public static void snap(Snap s) {
        snap = s == null ? Snap.OFF : s;
    }
}
