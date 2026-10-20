package com.createstats.vccmk4.core;

public final class LinkLogic {
    public static final int BLINK_TICKS = 8;

    public static final double SIGNAL_STEER = 0.25;
    public static final int FULL = 15;

    public static final int TAIL = 6;

    public static final int DIPPED = 8;

    public record Signals(boolean driving, boolean braking, boolean reversing, boolean boosting, double steer,
                          double[] aux, long tick, boolean beam) {
        public static final Signals NONE = new Signals(false, false, false, false, 0.0, new double[4], 0L, false);

        public Signals(boolean driving, boolean braking, boolean reversing, boolean boosting, double steer,
                       double[] aux, long tick) {
            this(driving, braking, reversing, boosting, steer, aux, tick, false);
        }

        double auxOf(Control c) {
            int i = switch (c) {
                case AUX_A -> 0;
                case AUX_B -> 1;
                case AUX_C -> 2;
                case AUX_D -> 3;
                default -> -1;
            };
            return i >= 0 && aux != null && i < aux.length ? aux[i] : 0.0;
        }
    }

    private LinkLogic() {
    }

    public static boolean raw(LinkSource source, Signals s) {
        return raw(source, s, false);
    }

    public static boolean raw(LinkSource source, Signals s, boolean steady) {
        if (source == null || !s.driving()) return false;
        boolean flash = steady || Math.floorMod(s.tick() / BLINK_TICKS, 2L) == 0L;
        return switch (source) {
            case OFF -> false;
            case DRIVING -> true;
            case BRAKE, TAIL_BRAKE -> s.braking();
            case REVERSE -> s.reversing();
            case BOOST -> s.boosting();
            case LEFT -> s.steer() < -SIGNAL_STEER && flash;
            case RIGHT -> s.steer() > SIGNAL_STEER && flash;
            case HEADLIGHT -> s.beam();
            default -> source.sign() * s.auxOf(source.control()) > 0.5;
        };
    }

    static int resting(LinkSource source) {
        if (source == LinkSource.TAIL_BRAKE) return TAIL;
        if (source == LinkSource.HEADLIGHT) return DIPPED;
        return 0;
    }

    public static int forLamp(LinkSource source, boolean white, int strength) {
        return white && source == LinkSource.REVERSE && strength > 0 ? DIPPED : strength;
    }

    public static final class State {
        private boolean on;
        private boolean last;

        public boolean on() {
            return on;
        }

        public void restore(boolean value) {
            on = value;
            last = false;
        }

        public int step(LinkSource source, boolean toggle, Signals s) {
            return step(source, toggle, s, false);
        }

        public int step(LinkSource source, boolean toggle, Signals s, boolean coupler) {
            return step(source, toggle, s, coupler, false);
        }

        public int step(LinkSource source, boolean toggle, Signals s, boolean coupler, boolean steady) {
            boolean now = raw(source, s, steady);
            if (!s.driving()) {
                last = false;
                if (coupler && toggle && source != null && source.keyed()) return on ? FULL : 0;
                on = false;
                return 0;
            }
            if (toggle && source != null && source.keyed()) {
                if (now && !last) on = !on;
                last = now;
                return on ? FULL : resting(source);
            }
            on = false;
            last = now;
            return now ? FULL : resting(source);
        }

        public void reset() {
            on = false;
            last = false;
        }
    }
}
