package com.createstats.timeswitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// time rules out of the BE, otherwise you test by waiting for dusk
public final class TimeSwitchProgram {
    public static final int HOURS = 24;

    public static final long DAY_TICKS = 24000L;

    public static final long HOUR_TICKS = 1000L;

    public static final int HOUR_AT_ZERO = 6;

    public static final int ALL = (1 << HOURS) - 1;

    public static final int NONE = 0;

    private TimeSwitchProgram() {
    }

    public enum Mode {
        AUTO, ON, OFF;

        public static Mode byId(int id) {
            final Mode[] all = values();
            return id >= 0 && id < all.length ? all[id] : AUTO;
        }

        public int id() {
            return ordinal();
        }
    }

    public enum Preset {
        DUSK_TO_DAWN, DAYTIME, CLEAR, INVERT;

        public static Preset byId(int id) {
            final Preset[] all = values();
            return id >= 0 && id < all.length ? all[id] : CLEAR;
        }

        public int id() {
            return ordinal();
        }
    }

    public static long tickOfDay(long dayTime) {
        return Math.floorMod(dayTime, DAY_TICKS);
    }

    public static int hourOf(long dayTime) {
        return (int) ((tickOfDay(dayTime) / HOUR_TICKS + HOUR_AT_ZERO) % HOURS);
    }

    public static double hourOf(double dayTime) {
        final double tick = dayTime - Math.floor(dayTime / DAY_TICKS) * DAY_TICKS;
        final double hour = tick / HOUR_TICKS + HOUR_AT_ZERO;
        return hour >= HOURS ? hour - HOURS : hour;
    }

    public static long startTick(int hour) {
        return Math.floorMod((long) (hour - HOUR_AT_ZERO) * HOUR_TICKS, DAY_TICKS);
    }

    public static int bit(int hour) {
        return 1 << Math.floorMod(hour, HOURS);
    }

    public static boolean isIn(int pins, int hour) {
        return (pins & bit(hour)) != 0;
    }

    public static int set(int pins, int mask, boolean in) {
        return (in ? pins | mask : pins & ~mask) & ALL;
    }

    public static int count(int pins) {
        return Integer.bitCount(pins & ALL);
    }

    public static boolean closed(long dayTime, int pins, Mode mode) {
        return switch (mode) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> isIn(pins, hourOf(dayTime));
        };
    }

    public static int nextChange(long dayTime, int pins, Mode mode) {
        if (mode != Mode.AUTO) {
            return -1;
        }
        final int now = hourOf(dayTime);
        final boolean state = isIn(pins, now);
        for (int k = 1; k < HOURS; k++) {
            final int hour = (now + k) % HOURS;
            if (isIn(pins, hour) != state) {
                return hour;
            }
        }
        return -1;
    }

    public static long ticksToNextChange(long dayTime, int pins, Mode mode) {
        final int hour = nextChange(dayTime, pins, mode);
        if (hour < 0) {
            return -1L;
        }
        final long ahead = Math.floorMod(startTick(hour) - tickOfDay(dayTime), DAY_TICKS);
        return ahead == 0L ? DAY_TICKS : ahead;
    }

    public static int startingIn(long from, long to) {
        final long a = tickOfDay(from);
        final long b = tickOfDay(to);
        int pins = 0;
        for (int hour = 0; hour < HOURS; hour++) {
            final long s = startTick(hour);
            final boolean inside = a <= b ? s >= a && s < b : s >= a || s < b;
            if (inside) {
                pins |= bit(hour);
            }
        }
        return pins;
    }

    public static int preset(Preset preset, int current, long dusk, long dawn) {
        return switch (preset) {
            case DUSK_TO_DAWN -> startingIn(dusk, dawn);
            case DAYTIME -> startingIn(dawn, dusk);
            case CLEAR -> NONE;
            case INVERT -> ~current & ALL;
        };
    }

    public static List<int[]> ranges(int pins) {
        pins &= ALL;
        final List<int[]> out = new ArrayList<>();
        if (pins == NONE) {
            return out;
        }
        if (pins == ALL) {
            out.add(new int[] {0, HOURS});
            return out;
        }
        for (int hour = 0; hour < HOURS; hour++) {
            if (isIn(pins, hour) && !isIn(pins, hour - 1)) {
                int end = hour;
                while (isIn(pins, end)) {
                    end = (end + 1) % HOURS;
                }
                out.add(new int[] {hour, end});
            }
        }
        return out;
    }

    public static String clock(int hour) {
        return String.format(Locale.ROOT, "%02d:00", hour);
    }

    public static String describe(int pins, String to, String joiner) {
        final StringBuilder out = new StringBuilder();
        for (int[] run : ranges(pins)) {
            if (out.length() > 0) {
                out.append(joiner);
            }
            out.append(clock(run[0])).append(' ').append(to).append(' ').append(clock(run[1]));
        }
        return out.toString();
    }
}
