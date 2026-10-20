package com.createstats.seismic;

public final class SeismicRig {
    public enum State {
        NO_TARGET,

        NO_GROUND,

        CHARGING,

        WAITING,

        SHED,

        READY,

        DROPPING;

        public static State of(int i) {
            final State[] all = values();
            return i >= 0 && i < all.length ? all[i] : NO_TARGET;
        }
    }

    public enum Lamp { OFF, RED, AMBER, AMBER_BLINK, AMBER_SLOW, GREEN, WHITE }

    public static final int LEVER_REPEAT_TICKS = 5;

    private SeismicRig() {
    }

    public static State state(boolean tuned, boolean coupled, boolean dropping, boolean suppressed, boolean ready,
                              int arriving) {
        if (dropping) {
            return State.DROPPING;
        }
        if (!coupled) {
            return State.NO_GROUND;
        }
        if (!tuned) {
            return State.NO_TARGET;
        }
        if (ready) {
            return State.READY;
        }
        if (suppressed) {
            return State.SHED;
        }
        return arriving > 0 ? State.CHARGING : State.WAITING;
    }

    public static Lamp lamp(State state) {
        return switch (state) {
            case NO_GROUND -> Lamp.RED;
            case CHARGING -> Lamp.AMBER;
            case WAITING -> Lamp.AMBER_BLINK;
            case SHED -> Lamp.AMBER_SLOW;
            case READY -> Lamp.GREEN;
            case DROPPING -> Lamp.WHITE;
            default -> Lamp.OFF;
        };
    }

    public static boolean lampLit(Lamp lamp, long tick) {
        return switch (lamp) {
            case OFF -> false;
            case AMBER_BLINK -> Math.floorMod(tick, 20L) < 10L;
            case AMBER_SLOW -> Math.floorMod(tick, 80L) < 20L;
            case RED -> {
                final long t = Math.floorMod(tick, FAULT_PERIOD);
                yield t < FAULT_FLASH || (t >= 2L * FAULT_FLASH && t < 3L * FAULT_FLASH);
            }
            default -> true;
        };
    }

    public static final long FAULT_PERIOD = 30L;
    public static final long FAULT_FLASH = 4L;

    public static boolean signalFires(boolean powered, boolean wasPowered, boolean settled) {
        return settled && powered && !wasPowered;
    }

    public static int comparator(long stored, long burst) {
        if (burst <= 0L || stored <= 0L) {
            return 0;
        }
        if (stored >= burst) {
            return 15;
        }
        return 1 + (int) Math.floor(14.0 * stored / burst);
    }

    public static boolean winchTakes(boolean tuned, boolean coupled, boolean dropping, boolean suppressed,
                                     boolean ready) {
        return tuned && coupled && !dropping && !suppressed && !ready;
    }

    public static int rated(boolean tuned, boolean coupled, boolean dropping, boolean forcedOff, long stored,
                            int radius) {
        if (!tuned || !coupled || dropping || forcedOff) {
            return 0;
        }
        final long room = SeismicCharge.burstFor(radius) - stored;
        return (int) Math.max(0L, Math.min(SeismicCharge.WINCH_FE_PER_TICK, room));
    }

    // held use repeats every 4 ticks, would drop the weight again. MIN_VALUE checked by name, now - MIN_VALUE wraps
    public static boolean newPull(long lastClick, long now) {
        if (lastClick == Long.MIN_VALUE || now < lastClick) {
            return true;
        }
        return now - lastClick > LEVER_REPEAT_TICKS;
    }
}
