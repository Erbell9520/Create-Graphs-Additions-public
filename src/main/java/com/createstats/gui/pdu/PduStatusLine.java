package com.createstats.gui.pdu;

import com.createstats.power.PduMode;

import java.util.Locale;

public final class PduStatusLine {
    private PduStatusLine() {
    }

    private static final int DRAINING_TICKS = 20 * 30;

    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct) {
        return derive(mode, hasBranch, dark, surplus, throughputLimited, shedCount, spareFePerTick,
                enduranceTicks, fillFraction, floorPct, false);
    }

    // new age connector bolted straight on is said first, it's the cause of everything else on the line
    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct,
                                boolean feedMisplaced) {
        return derive(mode, hasBranch, dark, surplus, throughputLimited, shedCount, spareFePerTick,
                enduranceTicks, fillFraction, floorPct, feedMisplaced, -1);
    }

    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct,
                                boolean feedMisplaced, int strayControllerLeg) {
        return derive(mode, hasBranch, dark, surplus, throughputLimited, shedCount, spareFePerTick,
                enduranceTicks, fillFraction, floorPct, feedMisplaced, strayControllerLeg, 0);
    }

    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct,
                                boolean feedMisplaced, int strayControllerLeg, int exportFePerTick) {
        return derive(mode, hasBranch, dark, surplus, throughputLimited, shedCount, spareFePerTick,
                enduranceTicks, fillFraction, floorPct, feedMisplaced, strayControllerLeg,
                exportFePerTick, -1);
    }

    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct,
                                boolean feedMisplaced, int strayControllerLeg, int exportFePerTick,
                                int strayPanelStudLeg) {
        if (feedMisplaced) {
            return "A New Age connector is bolted straight to this board. Fit a Universal Power Coupler between them.";
        }
        if (strayPanelStudLeg >= 0) {
            return PduLegRow.name(strayPanelStudLeg) + " runs to another panel's stud, which carries "
                    + "nothing. A stud is an output on both boards: land this leg on that panel's "
                    + "side boss instead.";
        }
        if (strayControllerLeg >= 0) {
            return PduLegRow.name(strayControllerLeg) + " runs to a Battery Controller input, which carries "
                    + "nothing. Wire the stud to the connector that controller's input already holds.";
        }
        if (!hasBranch) {
            return "Nothing is wired. Run Copper Wire from a stud, or cable from the back boss.";
        }
        if (dark) {
            return "Not receiving power. The branch is coasting on its reserve.";
        }
        if (mode == PduMode.OFF) {
            return "Distribution off. Every device shares the branch evenly.";
        }
        if (throughputLimited && shedCount > 0) {
            return "Branch wiring is the limit, not generation. Upgrade the wire on a stud, or split the load across more studs.";
        }
        if (shedCount > 0) {
            final boolean belowFloor = fillFraction < floorPct / 100.0f;
            final String noun = shedCount == 1 ? " load " : " loads ";
            if (exportFePerTick > 0) {
                return "Shedding " + shedCount + noun + "while "
                        + String.format(Locale.ROOT, "%,d", exportFePerTick)
                        + " FE/t leaves through the back boss.";
            }
            return "Shedding " + shedCount + noun
                    + (belowFloor ? "to hold the reserve floor." : "to stay within supply.");
        }
        if (enduranceTicks != Integer.MAX_VALUE && enduranceTicks < DRAINING_TICKS) {
            return "All loads served, but the reserve is draining, " + formatDuration(enduranceTicks) + " to empty.";
        }
        if (surplus && spareFePerTick > 0) {
            return "All loads served, " + String.format(Locale.ROOT, "%,d", spareFePerTick) + " FE/t spare.";
        }
        return "All loads served.";
    }

    public static String formatDuration(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        if (seconds >= 3600) {
            return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        }
        if (seconds >= 60) {
            return seconds / 60 + "m " + seconds % 60 + "s";
        }
        return seconds + "s";
    }

    public static String formatSupplySubtitle(int measuredIntake, float fillFraction) {
        if (measuredIntake > 0) {
            return String.format(Locale.ROOT, "%,d", measuredIntake) + " FE/t flowing in";
        }
        if (fillFraction >= 0.98f) {
            return "buffer full, intake paused";
        }
        return "nothing flowing in";
    }

    public static String formatEndurance(int enduranceTicks, boolean surplus, float fillFraction) {
        if (enduranceTicks != Integer.MAX_VALUE) {
            return formatDuration(enduranceTicks) + " to empty";
        }
        if (surplus && fillFraction < 0.98f) {
            return "charging";
        }
        return "steady";
    }
}
