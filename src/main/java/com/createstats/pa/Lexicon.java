package com.createstats.pa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// closed vocabulary, clips baked at build time. no speech engine
public final class Lexicon {
    private Lexicon() {
    }

    public static final String ATTENTION = "attention";
    public static final String CAUTION = "caution";
    public static final String WARNING = "warning";
    public static final String ALERT = "alert";
    public static final String ALL_CLEAR = "all_clear";

    public static final String UNIT = "unit";
    public static final String AND = "and";
    public static final String AT = "at";
    public static final String IS = "is";
    public static final String OF = "of";
    public static final String OTHERS = "others";
    public static final String OUTSTANDING = "outstanding";
    public static final String NOMINAL = "nominal";
    public static final String REPORT = "report";
    public static final String END_OF_REPORT = "end_of_report";
    public static final String NO_FAULTS = "no_faults";

    private static final String[] UNITS = {
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen",
    };
    private static final String[] TENS = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    };
    public static final String HUNDRED = "hundred";
    public static final String THOUSAND = "thousand";
    public static final String MILLION = "million";
    public static final String BILLION = "billion";
    public static final String POINT = "point";
    public static final String NEGATIVE = "negative";

    public static final String PERCENT = "percent";
    public static final String RPM = "rpm";
    public static final String STRESS_UNITS = "stress_units";
    public static final String FE = "fe";
    public static final String FE_PER_TICK = "fe_per_tick";
    public static final String MILLIBUCKETS = "millibuckets";
    public static final String PER_MINUTE = "per_minute";
    public static final String ITEMS = "items";
    public static final String DEGREES = "degrees";
    public static final String SECONDS = "seconds";
    public static final String MINUTES = "minutes";

    public static final String CAPACITY = "capacity";
    public static final String STRESS = "stress";
    public static final String SPEED = "speed";
    public static final String FILL = "fill";
    public static final String FLOW = "flow";
    public static final String CHARGE = "charge";
    public static final String DEMAND = "demand";
    public static final String SHORTFALL = "shortfall";
    public static final String ENDURANCE = "endurance";
    public static final String THROUGHPUT = "throughput";
    public static final String TEMPERATURE = "temperature";
    public static final String LEVEL = "level";
    public static final String STOCK = "stock";

    public static final String STRESS_NETWORK = "stress_network";
    public static final String POWER_GRID = "power_grid";
    public static final String BATTERY_BANK = "battery_bank";
    public static final String FLUID_TANK = "fluid_tank";
    public static final String ITEM_STORE = "item_store";
    public static final String BOILER = "boiler";
    public static final String PANEL = "panel";
    public static final String ALARM = "alarm";
    public static final String TRAIN = "train";
    public static final String STATION = "station";
    public static final String MACHINE = "machine";

    public static final String NORMAL = "normal";
    public static final String HIGH = "high";
    public static final String LOW = "low";
    public static final String EMPTY = "empty";
    public static final String FULL = "full";
    public static final String RISING = "rising";
    public static final String FALLING = "falling";
    public static final String STEADY = "steady";
    public static final String OVERSTRESSED = "overstressed";
    public static final String OFFLINE = "offline";
    public static final String RESTORED = "restored";
    public static final String STOPPED = "stopped";
    public static final String DELAYED = "delayed";
    public static final String CRITICAL = "critical";

    public static List<String> number(long value) {
        final List<String> out = new ArrayList<>();
        if (value < 0) {
            out.add(NEGATIVE);
            value = -value;
        }
        speakWhole(value, out);
        return out;
    }

    private static void speakWhole(long value, List<String> out) {
        if (value < 20) {
            out.add(UNITS[(int) value]);
            return;
        }
        if (value < 100) {
            out.add(TENS[(int) (value / 10)]);
            if (value % 10 != 0) {
                out.add(UNITS[(int) (value % 10)]);
            }
            return;
        }
        for (long scale : new long[]{1_000_000_000L, 1_000_000L, 1_000L, 100L}) {
            if (value >= scale) {
                speakWhole(value / scale, out);
                out.add(scaleWord(scale));
                final long rest = value % scale;
                if (rest > 0) {
                    speakWhole(rest, out);
                }
                return;
            }
        }
    }

    private static String scaleWord(long scale) {
        if (scale == 100L) {
            return HUNDRED;
        }
        if (scale == 1_000L) {
            return THOUSAND;
        }
        return scale == 1_000_000L ? MILLION : BILLION;
    }

    public static List<String> quantity(double value) {
        final double magnitude = Math.abs(value);
        if (magnitude >= 10.0 || magnitude == Math.floor(magnitude)) {
            return number(Math.round(value));
        }
        final List<String> out = new ArrayList<>();
        final long whole = (long) Math.floor(magnitude);
        final int tenth = (int) Math.round((magnitude - whole) * 10.0);
        if (value < 0) {
            out.add(NEGATIVE);
        }

        if (tenth == 10) {
            out.addAll(number(whole + 1));
            return out;
        }
        out.addAll(number(whole));
        if (tenth > 0) {
            out.add(POINT);
            out.add(UNITS[tenth]);
        }
        return out;
    }

    public static Set<String> allClips() {
        final Set<String> out = new LinkedHashSet<>();
        Collections.addAll(out, ATTENTION, CAUTION, WARNING, ALERT, ALL_CLEAR,
                UNIT, AND, AT, IS, OF, OTHERS, OUTSTANDING, NOMINAL, REPORT, END_OF_REPORT, NO_FAULTS);
        Collections.addAll(out, UNITS);
        for (String ten : TENS) {
            if (!ten.isEmpty()) {
                out.add(ten);
            }
        }
        Collections.addAll(out, HUNDRED, THOUSAND, MILLION, BILLION, POINT, NEGATIVE);
        Collections.addAll(out, PERCENT, RPM, STRESS_UNITS, FE, FE_PER_TICK, MILLIBUCKETS, PER_MINUTE,
                ITEMS, DEGREES, SECONDS, MINUTES);
        Collections.addAll(out, CAPACITY, STRESS, SPEED, FILL, FLOW, CHARGE, DEMAND, SHORTFALL,
                ENDURANCE, THROUGHPUT, TEMPERATURE, LEVEL, STOCK);
        Collections.addAll(out, STRESS_NETWORK, POWER_GRID, BATTERY_BANK, FLUID_TANK, ITEM_STORE,
                BOILER, PANEL, ALARM, TRAIN, STATION, MACHINE);
        Collections.addAll(out, NORMAL, HIGH, LOW, EMPTY, FULL, RISING, FALLING, STEADY, OVERSTRESSED,
                OFFLINE, RESTORED, STOPPED, DELAYED, CRITICAL);
        return out;
    }
}
