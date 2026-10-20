package com.createstats.gui.pdu;

import com.createstats.power.PduMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PduDerivation {
    private PduDerivation() {
    }

    public enum Kind {
        INPUT,

        TERM,

        RESULT,

        VERDICT,

        HEADING
    }

    public record Row(Kind kind, String label, String value, String note) {
    }

    public record Inputs(PduMode mode, boolean hasBranch, int generation, int measuredInput, int ceiling,
                         int runBudget, int demand, int servedDemand, int served, int shed,
                         int stored, int capacity, float floorFraction, boolean surplus,
                         boolean throughputLimited, Link link, int export, int upkeep) {
        public Inputs(PduMode mode, boolean hasBranch, int generation, int measuredInput, int ceiling,
                      int runBudget, int demand, int servedDemand, int served, int shed,
                      int stored, int capacity, float floorFraction, boolean surplus,
                      boolean throughputLimited) {
            this(mode, hasBranch, generation, measuredInput, ceiling, runBudget, demand, servedDemand,
                    served, shed, stored, capacity, floorFraction, surplus, throughputLimited,
                    null, 0, 0);
        }

        public Inputs(PduMode mode, boolean hasBranch, int generation, int measuredInput, int ceiling,
                      int runBudget, int demand, int servedDemand, int served, int shed,
                      int stored, int capacity, float floorFraction, boolean surplus,
                      boolean throughputLimited, Link link) {
            this(mode, hasBranch, generation, measuredInput, ceiling, runBudget, demand, servedDemand,
                    served, shed, stored, capacity, floorFraction, surplus, throughputLimited,
                    link, 0, 0);
        }
    }

    public record Link(int socPct, String stanceLabel, String stanceBlurb, boolean rationing,
                       int sustained, int floorPct, int ceilingPct, int chargeClaim,
                       int controllers) {
        public Link(int socPct, String stanceLabel, String stanceBlurb, boolean rationing,
                    int sustained, int floorPct, int ceilingPct) {
            this(socPct, stanceLabel, stanceBlurb, rationing, sustained, floorPct, ceilingPct, 0, 1);
        }
    }

    private static String fe(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE/t";
    }

    private static String feTotal(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE";
    }

    public static List<Row> terse(List<Row> rows) {
        final List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            out.add(r.kind() == Kind.VERDICT ? r : new Row(r.kind(), r.label(), r.value(), ""));
        }
        return out;
    }

    private static List<Row> withoutNotesOn(List<Row> rows, Kind drop) {
        final List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            out.add(r.kind() == drop ? new Row(r.kind(), r.label(), r.value(), "") : r);
        }
        return out;
    }

    public static List<Row> fit(List<Row> rows, int availableLines) {
        if (lineCount(rows) <= availableLines) {
            return rows;
        }
        final List<Row> lean = withoutNotesOn(rows, Kind.TERM);
        return lineCount(lean) <= availableLines ? lean : terse(rows);
    }

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

        if (in.link() != null) {
            final Link link = in.link();
            rows.add(new Row(Kind.INPUT,
                    link.controllers() > 1 ? link.controllers() + " Battery Controllers"
                            : "Battery Controller",
                    link.socPct() + "% charged",
                    link.controllers() > 1
                            ? "their banks pooled; the worst stance of the two is the one that counts"
                            : "the banks are behind it, so this panel is told rather than surveying them"));
            rows.add(new Row(Kind.INPUT, "Its stance", link.stanceLabel(),
                    link.stanceBlurb() + ", band " + link.floorPct() + " to " + link.ceilingPct()
                            + " percent"));
        }
        rows.add(new Row(Kind.INPUT, "Generation upstream", fe(in.generation()),
                in.link() != null ? "measured on the controller's inputs, not guessed from intake"
                        : "dynamo capacity, or the best intake seen"));
        rows.add(new Row(Kind.INPUT, "Flowing in now", fe(in.measuredInput()),
                in.measuredInput() <= 0 && in.stored() >= in.capacity() - Math.max(1, in.capacity() / 100)
                        ? "nothing, the buffer is full and has nowhere to put it"
                        : "measured at the input face"));
        rows.add(new Row(Kind.INPUT, "Reserve above floor", feTotal(spendable),
                "the floor is " + Math.round(in.floorFraction() * 100) + " percent of the buffer"));
        rows.add(new Row(Kind.INPUT, "Wire ceiling", fe(in.ceiling()),
                "every stud's wire added up, per tick"));

        rows.add(new Row(Kind.HEADING, "Budget", "", ""));

        final int claim = in.link() == null ? 0 : in.link().chargeClaim();
        final int export = Math.max(0, in.export());
        final int upkeep = Math.max(0, in.upkeep());
        final int spendableGen = Math.max(0, in.generation() - claim - export - upkeep);
        final int fromReserve = Math.max(0, in.runBudget() - spendableGen);
        rows.add(new Row(Kind.TERM, "from generation",
                fe(Math.min(spendableGen, in.runBudget())), ""));

        if (claim > 0) {
            rows.add(new Row(Kind.TERM, "held back to refill the banks", "-" + fe(claim),
                    "the controller's recharge, funded by the tiers below Critical"));
        }

        if (export > 0) {
            rows.add(new Row(Kind.TERM, "leaving through the back boss", "-" + fe(export),
                    "whatever is wired behind the panel is drawing this"));
        }

        if (upkeep > 0) {
            rows.add(new Row(Kind.TERM, "running this panel", "-" + fe(upkeep),
                    "the board's own overhead, waived under creative energy"));
        }
        if (fromReserve > 0) {
            rows.add(new Row(Kind.TERM, "from the reserve", fe(fromReserve),
                    in.link() != null ? "released by the controller's band"
                            : in.surplus() ? "buffer is healthy, so it funds the branch"
                            : "spent as ride-through"));
        } else if (in.link() != null && in.link().rationing()) {
            rows.add(new Row(Kind.TERM, "from the reserve", fe(0),
                    "the controller is holding its band, so this branch lives on generation"));
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
        if (in.throughputLimited()) {
            return "The wiring is the limit, not the supply.";
        }

        if (in.link() != null && in.link().chargeClaim() > 0) {
            return "The controller is refilling its reserve.";
        }
        return in.link() != null && in.link().rationing()
                ? "The controller is protecting its reserve."
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
        if (in.throughputLimited()) {
            return "Upgrade a leg's wire, or split its load across more studs. More generation would not help.";
        }
        if (in.link() != null && in.link().chargeClaim() > 0) {
            return "It is holding " + fe(in.link().chargeClaim()) + " of generation back to put the "
                    + "banks up from " + in.link().socPct() + " percent. This ends on its own; "
                    + "lower the claim in [power.batteryController] if you would rather it did not.";
        }
        if (in.link() != null && in.link().rationing()) {
            return "The banks are at " + in.link().socPct() + " percent, inside the band it holds. "
                    + "Add generation, or lower the controller's band to spend more of what is stored.";
        }
        return "Build more generation, raise a device's tier, or switch something off.";
    }
}
