package com.createstats.gui.pdu;

import com.createstats.power.PduMode;

import java.util.Locale;

/** [1] */
public final class PduStatusLine {

    private PduStatusLine() {
    }

    /** Thirty seconds. Under this much reserve left, a served grid is still called out as draining. */
    private static final int DRAINING_TICKS = 20 * 30;

    /**
     * The single status sentence.
     *
     * @param mode              the distribution mode
     * @param hasBranch         whether anything is wired to the output studs
     * @param dark              whether the branch has no power at all, buffer empty and nothing coming in
     * @param surplus           whether there is spare supply (Active Auto only, pass false otherwise)
     * @param throughputLimited whether the branch wiring, not generation, is the binding limit
     * @param shedCount         how many loads are currently shed
     * @param spareFePerTick    spare generation over demand, for the surplus sentence
     * @param enduranceTicks    ticks of reserve left at the current drain, or {@link Integer#MAX_VALUE}
     * @param fillFraction      buffer fill, 0 to 1
     * @param floorPct          reserve floor percent, the line below which only Critical draws
     */
    public static String derive(PduMode mode, boolean hasBranch, boolean dark, boolean surplus,
                                boolean throughputLimited, int shedCount, int spareFePerTick,
                                int enduranceTicks, float fillFraction, int floorPct) {
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

    /** A tick count as a rough duration: "45s", "4m 20s", "1h 3m". */
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

    /**
     * The Supply card's subtitle, which has to reconcile a headline of "3,843 FE/t" with an intake of
     * zero without either of them looking like a fault.
     *
     * <p>Both are true at once and the reason is not guessable from the panel: a full buffer has
     * nowhere to put incoming energy, so the PDU stops pulling and measured intake legitimately falls
     * to nothing while the branch runs happily off the reserve. The old subtitle said "0 FE/t drawn
     * now" under a large supply figure, which reads as broken. Naming the cause is the whole job of
     * this string.</p>
     *
     * @param measuredIntake real FE per tick entering the PDU
     * @param fillFraction   buffer fill, 0 to 1
     */
    public static String formatSupplySubtitle(int measuredIntake, float fillFraction) {
        if (measuredIntake > 0) {
            return String.format(Locale.ROOT, "%,d", measuredIntake) + " FE/t flowing in";
        }
        if (fillFraction >= 0.98f) {
            return "buffer full, intake paused";
        }
        return "nothing flowing in";
    }

    /** [2] */
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
