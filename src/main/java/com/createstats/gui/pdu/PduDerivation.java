package com.createstats.gui.pdu;

import com.createstats.power.PduMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** [1] */
public final class PduDerivation {

    private PduDerivation() {
    }

    /** How a row reads: an input measurement, a step of arithmetic, a total, or the conclusion. */
    public enum Kind {
        /** Something measured or configured. Neutral. */
        INPUT,
        /** A term contributing to the total above or below it. Indented, dim. */
        TERM,
        /** A computed total. Emphasised. */
        RESULT,
        /** The plain language conclusion. */
        VERDICT,
        /** A section heading. */
        HEADING
    }

    /**
     * One line of the working.
     *
     * @param label what the number is
     * @param value the number, already formatted, or empty for a heading
     * @param note  where it came from, or the arithmetic that produced it. May be empty.
     */
    public record Row(Kind kind, String label, String value, String note) {
    }

    /**
     * Everything the working needs, all of it synced.
     *
     * @param mode              distribution mode
     * @param hasBranch         whether anything is wired to the output studs
     * @param generation        {@code generationFePerTick()}, the supply the controller budgeted against
     * @param measuredInput     {@code measuredInputFePerTick()}, what actually flowed in last tick
     * @param ceiling           {@code branchCeilingFePerTick()}, every stud's wire rating added up
     * @param runBudget         {@code budgetFePerTick()}, what a running device was allowed to draw against
     * @param demand            {@code branchDemandFePerTick()}, summed cost of every device
     * @param servedDemand      {@code servedDemandFePerTick()}, summed cost of the admitted set
     * @param served            devices the controller admitted
     * @param shed              devices it dropped
     * @param stored            FE in the buffer
     * @param capacity          buffer size
     * @param floorFraction     reserve floor, 0 to 1
     * @param surplus           whether supply is spare
     * @param throughputLimited whether the wiring rather than generation is the binding constraint
     */
    public record Inputs(PduMode mode, boolean hasBranch, int generation, int measuredInput, int ceiling,
                         int runBudget, int demand, int servedDemand, int served, int shed,
                         int stored, int capacity, float floorFraction, boolean surplus,
                         boolean throughputLimited) {
    }

    private static String fe(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE/t";
    }

    private static String feTotal(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE";
    }

    /** [2] */
    public static List<Row> terse(List<Row> rows) {
        final List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            // verdict keeps its note - it's not a footnote, it's the actual advice
            out.add(r.kind() == Kind.VERDICT ? r : new Row(r.kind(), r.label(), r.value(), ""));
        }
        return out;
    }

    /** Rendered height of a working, in lines, so the caller can pick between the two forms. */
    public static int lineCount(List<Row> rows) {
        int lines = 0;
        for (Row r : rows) {
            lines += switch (r.kind()) {
                case HEADING -> 2;
                case VERDICT -> 3;
                default -> r.note().isEmpty() ? 1 : 2;
            };
        }
        return lines;
    }

    /** The working, top to bottom. Never empty: an unwired or inactive panel says so and stops. */
    public static List<Row> derive(Inputs in) {
        final List<Row> rows = new ArrayList<>(20);
        if (!in.hasBranch()) {
            rows.add(new Row(Kind.VERDICT, "Nothing is wired to this panel.", "",
                    "Copper Wire from a stud to a machine's connector, or cable from the back boss."));
            return rows;
        }
        if (in.mode() != PduMode.AUTO) {
            rows.add(new Row(Kind.VERDICT, "Active Auto is not running.", "",
                    in.mode() == PduMode.OFF
                            ? "Distribution is off, so every device shares the branch evenly."
                            : "Manual mode serves strictly by tier until the buffer runs out."));
            rows.add(new Row(Kind.HEADING, "Branch", "", ""));
            rows.add(new Row(Kind.INPUT, "Devices", String.valueOf(in.served() + in.shed()), "on the branch"));
            rows.add(new Row(Kind.INPUT, "Drawing now", fe(in.demand()), "summed across every device"));
            rows.add(new Row(Kind.INPUT, "Wire ceiling", fe(in.ceiling()), "every stud's wire added up"));
            return rows;
        }

        final int floorEnergy = Math.round(in.capacity() * in.floorFraction());
        final int spendable = Math.max(0, in.stored() - floorEnergy);

        rows.add(new Row(Kind.HEADING, "Supply", "", ""));
        rows.add(new Row(Kind.INPUT, "Generation upstream", fe(in.generation()),
                "dynamo capacity, or the best intake seen"));
        rows.add(new Row(Kind.INPUT, "Flowing in now", fe(in.measuredInput()),
                in.measuredInput() <= 0 && in.stored() >= in.capacity() - Math.max(1, in.capacity() / 100)
                        ? "nothing, the buffer is full and has nowhere to put it"
                        : "measured at the input face"));
        rows.add(new Row(Kind.INPUT, "Reserve above floor", feTotal(spendable),
                "the floor is " + Math.round(in.floorFraction() * 100) + " percent of the buffer"));
        rows.add(new Row(Kind.INPUT, "Wire ceiling", fe(in.ceiling()),
                "every stud's wire added up, per tick"));

        rows.add(new Row(Kind.HEADING, "Budget", "", ""));
        // [3]
        final int fromReserve = Math.max(0, in.runBudget() - in.generation());
        rows.add(new Row(Kind.TERM, "from generation",
                fe(Math.min(in.generation(), in.runBudget())), ""));
        if (fromReserve > 0) {
            rows.add(new Row(Kind.TERM, "from the reserve", fe(fromReserve),
                    in.surplus() ? "buffer is healthy, so it funds the branch" : "spent as ride-through"));
        }
        rows.add(new Row(Kind.RESULT, "Run budget", fe(in.runBudget()),
                in.runBudget() >= in.ceiling() ? "capped by the wire ceiling"
                        : "generation plus what the reserve can spend"));

        rows.add(new Row(Kind.HEADING, "Demand", "", ""));
        rows.add(new Row(Kind.INPUT, (in.served() + in.shed()) + " devices want", fe(in.demand()),
                "each costed at what it spends, not what it can hold"));
        rows.add(new Row(Kind.TERM, "admitted (" + in.served() + ")", fe(in.servedDemand()), ""));
        final int shedDemand = Math.max(0, in.demand() - in.servedDemand());
        if (in.shed() > 0 || shedDemand > 0) {
            rows.add(new Row(Kind.TERM, "shed (" + in.shed() + ")", fe(shedDemand), ""));
        }
        final int headroom = in.runBudget() - in.servedDemand();
        rows.add(new Row(Kind.RESULT, headroom >= 0 ? "Headroom" : "Over budget by",
                fe(Math.abs(headroom)),
                headroom >= 0 ? "budget left after everything admitted" : "the reserve is covering this"));

        rows.add(new Row(Kind.HEADING, "Verdict", "", ""));
        rows.add(new Row(Kind.VERDICT, verdict(in), "", verdictNote(in)));
        return rows;
    }

    private static String verdict(Inputs in) {
        if (in.served() + in.shed() == 0) {
            return "No devices on the branch.";
        }
        if (in.shed() == 0) {
            return "Everything on the branch is running.";
        }
        return in.throughputLimited()
                ? "The wiring is the limit, not the supply."
                : "Supply is the limit.";
    }

    private static String verdictNote(Inputs in) {
        if (in.served() + in.shed() == 0) {
            return "Run a wire from a stud to a machine's Power Connector to bring it under management.";
        }
        if (in.shed() == 0) {
            return in.surplus() ? "Supply is spare and the reserve is holding."
                    : "Nothing is being dropped, but there is no spare supply.";
        }
        return in.throughputLimited()
                ? "Upgrade a leg's wire, or split its load across more studs. More generation would not help."
                : "Build more generation, raise a device's tier, or switch something off.";
    }
}
