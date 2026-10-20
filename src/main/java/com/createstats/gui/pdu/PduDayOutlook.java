package com.createstats.gui.pdu;

import com.createstats.power.SolarCurve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PduDayOutlook(int stepTicks, int[] rate, int[] clear, long coldMask, long startDayTime,
                            int criticalFePerTick, int shortfallFe, boolean certain,
                            int ownHoldFe, boolean ownHeld, int peerHoldFe, int warmBuckets,
                            SolarCurve.Sky skyNow, int ticksToChange, SolarCurve.Sky skyThen) {
    public static final int DAY_TICKS = 24_000;

    public static final int HOURS = 24;

    public PduDayOutlook {
        rate = rate == null ? new int[0] : rate.clone();
        clear = clear == null || clear.length != rate.length ? rate.clone() : clear.clone();
        stepTicks = Math.max(0, stepTicks);
        criticalFePerTick = Math.max(0, criticalFePerTick);
        shortfallFe = Math.max(0, shortfallFe);
        ownHoldFe = Math.max(0, ownHoldFe);
        peerHoldFe = Math.max(0, peerHoldFe);
        warmBuckets = Math.max(0, Math.min(HOURS, warmBuckets));
        skyNow = skyNow == null ? SolarCurve.Sky.CLEAR : skyNow;
        skyThen = skyThen == null ? skyNow : skyThen;
    }

    public boolean hasCurve() {
        return stepTicks > 0 && rate.length > 0;
    }

    public int steps() {
        return rate.length;
    }

    public int spanTicks() {
        return steps() * stepTicks;
    }

    public boolean isCold(int step) {
        return step >= 0 && step < 64 && (coldMask >>> step & 1L) != 0;
    }

    public boolean anyCold() {
        for (int i = 0; i < steps(); i++) {
            if (isCold(i)) {
                return true;
            }
        }
        return false;
    }

    public int rainCostAt(int step) {
        return step < 0 || step >= steps() ? 0 : Math.max(0, clear[step] - rate[step]);
    }

    public boolean anyRainCost() {
        for (int i = 0; i < steps(); i++) {
            if (rainCostAt(i) > 0) {
                return true;
            }
        }
        return false;
    }

    public int deficitAt(int step) {
        return step < 0 || step >= steps() ? 0 : Math.max(0, criticalFePerTick - rate[step]);
    }

    public int scaleMax() {
        int max = criticalFePerTick;
        for (int i = 0; i < steps(); i++) {
            max = Math.max(max, Math.max(rate[i], clear[i]));
        }
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, max + (long) max / 10));
    }

    public int deepestStep() {
        long balance = 0L;
        long deepest = 0L;
        int at = -1;
        for (int i = 0; i < steps(); i++) {
            balance += (long) (rate[i] - criticalFePerTick) * stepTicks;
            if (balance < deepest) {
                deepest = balance;
                at = i;
            }
        }
        return at;
    }

    public int weatherChangeStep() {
        if (ticksToChange < 0 || stepTicks <= 0) {
            return -1;
        }
        final int step = ticksToChange / stepTicks;
        return step < steps() ? step : -1;
    }

    public boolean wetAt(int step) {
        final SolarCurve.Sky sky = ticksToChange >= 0 && (long) step * stepTicks >= ticksToChange
                ? skyThen : skyNow;
        return sky != SolarCurve.Sky.CLEAR;
    }

    public String clockAt(int step) {
        return clock(startDayTime + (long) step * stepTicks);
    }

    public static String clock(long dayTime) {
        final long t = Math.floorMod(dayTime + 6_000L, (long) DAY_TICKS);
        final int hour = (int) (t / 1_000);
        final int minute = (int) (t % 1_000 * 60 / 1_000);
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    public int ticksToNextMark(int everyHours) {
        final int every = Math.max(1, everyHours) * 1_000;
        final long clockTicks = Math.floorMod(startDayTime + 6_000L, (long) DAY_TICKS);
        final long rem = Math.floorMod(clockTicks, (long) every);
        return (int) (rem == 0 ? 0 : every - rem);
    }

    public String summary(boolean narrow) {
        if (!hasCurve()) {
            return String.format(Locale.ROOT, narrow ? "Learning: %d of %d hours"
                    : "Learning the day: %d of %d hours watched", warmBuckets, HOURS);
        }
        if (criticalFePerTick <= 0) {
            return narrow ? "No critical load to bank for" : "No critical load declared, so nothing to bank for";
        }
        if (shortfallFe <= 0) {
            return narrow ? "Covered all day" : "Covered: the supply carries the critical load all day";
        }
        return String.format(Locale.ROOT, narrow ? "Short %s FE" : "Short %s FE before the supply is back%s",
                compact(shortfallFe), certain ? ", weather allowed for" : "");
    }

    public String holdLine(boolean narrow) {
        if (!hasCurve() || shortfallFe <= 0) {
            return null;
        }
        final List<String> parts = new ArrayList<>(2);
        if (ownHoldFe > 0) {
            parts.add(ownHeld
                    ? (narrow ? "own banks " : "own banks hold ") + compact(ownHoldFe)
                    : (narrow ? "no bank for " : "no bank of its own holds ") + compact(ownHoldFe));
        }
        if (peerHoldFe > 0) {
            parts.add((narrow ? "legs " : "boards on its legs asked for ") + compact(peerHoldFe));
        }
        return parts.isEmpty() ? null : String.join(narrow ? ", " : "; ", parts);
    }

    public List<String> hoverLines(int step) {
        final List<String> lines = new ArrayList<>(5);
        if (step < 0 || step >= steps()) {
            return lines;
        }
        lines.add(clockAt(step) + (step == 0 ? " (now)" : ""));
        if (isCold(step)) {
            lines.add("Not learned yet: this hour has not been watched enough");
        }
        lines.add("Supply " + fmt(rate[step]) + " FE/t");
        if (rainCostAt(step) > 0) {
            lines.add("Clear sky " + fmt(clear[step]) + " FE/t, rain takes " + fmt(rainCostAt(step)));
        }
        if (criticalFePerTick > 0) {
            lines.add("Critical " + fmt(criticalFePerTick) + " FE/t"
                    + (deficitAt(step) > 0 ? ", short " + fmt(deficitAt(step)) : ""));
        }
        return lines;
    }

    static String fmt(int v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    static String compact(int fe) {
        if (fe >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", fe / 1_000_000.0);
        }
        if (fe >= 10_000) {
            return String.format(Locale.ROOT, "%,dk", fe / 1_000);
        }
        return String.format(Locale.ROOT, "%,d", fe);
    }
}
