package com.createstats.battle.core;

// one place decides the header so the helmet, block face and link text agree
public final class HeaderStatus {
    public static final long UNDER_FIRE_TICKS = 600L;

    public enum Kind {
        NO_POWER(Lamp.RED, false),
        FIRE(Lamp.RED, true),
        UNDER_FIRE(Lamp.RED, false),
        WEAPONS_FREE(Lamp.AMBER, false),
        SURVEYING(Lamp.GREY, false),
        ALL_CLEAR(Lamp.GREEN, false);

        private final Lamp lamp;
        private final boolean blinks;

        Kind(Lamp lamp, boolean blinks) {
            this.lamp = lamp;
            this.blinks = blinks;
        }

        public Lamp lamp() {
            return lamp;
        }

        public boolean blinks() {
            return blinks;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Lamp {
        RED, AMBER, GREEN, GREY
    }

    public record Status(Kind kind, int number, String place) {
    }

    private HeaderStatus() {
    }

    public static Status of(boolean powered, boolean fireAboard, String firePlace, long hitAgeTicks,
                            boolean weaponsFree, int surveyPermille) {
        if (!powered) return new Status(Kind.NO_POWER, 0, "");
        if (fireAboard) return new Status(Kind.FIRE, 0, firePlace == null ? "" : firePlace);
        if (hitAgeTicks >= 0 && hitAgeTicks < UNDER_FIRE_TICKS) return new Status(Kind.UNDER_FIRE, (int) (hitAgeTicks / 20), "");
        if (weaponsFree) return new Status(Kind.WEAPONS_FREE, 0, "");
        if (surveyPermille >= 0) return new Status(Kind.SURVEYING, surveyPermille / 10, "");
        return new Status(Kind.ALL_CLEAR, 0, "");
    }
}
