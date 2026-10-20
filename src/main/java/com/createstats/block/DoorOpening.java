package com.createstats.block;

import java.util.Locale;
import java.util.Set;

public enum DoorOpening {
    SWING(14, DoorEase.SWING),

    SLIDE(18, DoorEase.SERVO),

    WINGS(16, DoorEase.SERVO),

    FOLD_PARK(20, DoorEase.SERVO),

    FOLD_RETRACT(24, DoorEase.SERVO),

    CONCERTINA(20, DoorEase.SERVO),

    STACK_SLIDE(24, DoorEase.SERVO),

    PLUG_RETRACT(22, DoorEase.SERVO),

    ROLL_UP(26, DoorEase.DRUM);

    private static final DoorOpening[] ORDER = values();

    private final int ticks;
    private final DoorEase ease;

    DoorOpening(int ticks, DoorEase ease) {
        this.ticks = ticks;
        this.ease = ease;
    }

    public int ticks() {
        return ticks;
    }

    public DoorEase ease() {
        return ease;
    }

    public boolean pairOnly() {
        return single() != this;
    }

    public DoorOpening effective(boolean paired) {
        return paired ? this : single();
    }

    public DoorOpening single() {
        return switch (this) {
            case CONCERTINA -> FOLD_PARK;
            case STACK_SLIDE, PLUG_RETRACT -> SLIDE;
            default -> this;
        };
    }

    public DoorOpening next(boolean paired, Set<DoorOpening> offered) {
        DoorOpening candidate = this;
        for (int i = 0; i < ORDER.length; i++) {
            candidate = ORDER[(candidate.ordinal() + 1) % ORDER.length];
            if (offered.contains(candidate) && (paired || !candidate.pairOnly())) {
                return candidate;
            }
        }
        return this;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DoorOpening byName(String name) {
        for (final DoorOpening style : ORDER) {
            if (style.name().equals(name)) {
                return style;
            }
        }
        return SWING;
    }
}
