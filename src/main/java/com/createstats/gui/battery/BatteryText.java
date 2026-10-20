package com.createstats.gui.battery;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeReserveReport;
import com.createstats.power.ReserveWords;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BatteryText {
    private BatteryText() {
    }

    private static String fe(long v) {
        return ReserveWords.fe(v);
    }

    public static String now(BatteryView v) {
        for (BatteryView.Input in : v.inputs()) {
            if (in.strayPanel()) {
                return "Input " + BatteryControllerPorts.label(in.index())
                        + " runs to a panel's leg, which carries nothing. Feed it from the panel's back boss.";
            }
        }
        if (v.capacity() <= 0) {
            return "No banks yet: wire a Capacitor Bank to an input to give this controller a reserve.";
        }
        if (v.upstreamHolds()) {
            return "A board above is short, so nothing is being banked: everything arriving goes to the outputs.";
        }
        final int out = v.delivered();
        final int gen = v.generation();
        if (out <= 0 && gen <= 0) {
            return "Nothing is generating and nothing is drawing. The banks hold " + v.socPct() + "%.";
        }
        if (v.chargeClaim() > 0) {
            return "Refilling the banks: " + fe(v.chargeClaim()) + " of " + fe(gen)
                    + " FE/t generated is held back for them, and the lowest outputs wait.";
        }
        if (v.bankDrawn() > v.bankCharged()) {
            final int cover = gen <= 0 ? 0 : (int) Math.min(100, (long) gen * 100L / Math.max(1, out));
            return "Generating " + fe(gen) + " FE/t, " + cover + "% of the " + fe(out)
                    + " going out. The banks give the other " + fe(v.bankDrawn() - v.bankCharged()) + " FE/t.";
        }
        if (v.bankCharged() > 0 || v.storeCharged() > 0) {
            final int spare = v.bankCharged() + v.storeCharged();
            return "Generating " + fe(gen) + " FE/t, " + fe(out) + " going out. The spare " + fe(spare)
                    + " FE/t is going into " + (v.storeCharged() > 0 && v.bankCharged() > 0 ? "capacitors and banks"
                    : v.storeCharged() > 0 ? "the Starter Capacitors" : "the banks") + ".";
        }
        return "Generating " + fe(gen) + " FE/t and sending out " + fe(out) + ". The banks are holding at "
                + v.socPct() + "%.";
    }

    public static String foreseen(BatteryView v) {
        if (v.capacity() <= 0) {
            return "Nothing to forecast without banks.";
        }

        final String supply = ReserveWords.nextSupplyChange(v.outlook());
        final String lead = supply == null ? "" : Character.toLowerCase(supply.charAt(0)) + supply.substring(1) + "; ";
        return "Foreseen: " + lead + ReserveWords.controllerOutlook(v.outlook(), v.reserveRate()) + ".";
    }

    public static String supplyAssumption(BatteryView v) {
        final int[] supply = v.outlook().supply();
        if (supply.length < 2) {
            return "Generation holds at " + fe(v.generation()) + " FE/t.";
        }
        int lo = Integer.MAX_VALUE;
        int hi = 0;
        for (int f : supply) {
            lo = Math.min(lo, f);
            hi = Math.max(hi, f);
        }
        if (lo == hi) {
            return "Supply holds at " + fe(lo) + " FE/t: nothing on the inputs forecasts a change.";
        }
        return "Supply follows its sources: " + fe(supply[0]) + " FE/t now, " + fe(lo) + " to " + fe(hi)
                + " over the next " + ReserveWords.horizon() + ". Panels keep today's weather; a burner lasts "
                + "as long as its fuel unless a hopper feeds it.";
    }

    public static int severity(BatteryView v) {
        if (v.anyStrayInput() || v.capacity() <= 0 || v.stance() == FeReserveReport.Stance.CRITICAL) {
            return 2;
        }
        for (BatteryView.Output o : v.outputs()) {
            if (o.isShort()) {
                return 1;
            }
        }
        return v.stance() == FeReserveReport.Stance.CONSERVE || v.chargeClaim() > 0 ? 1 : 0;
    }

    public static String inputWhat(BatteryView.Input in) {
        if (in.strayPanel()) {
            return "Panel leg: carries nothing";
        }
        final List<String> parts = new ArrayList<>(3);
        if (in.sources() > 0) {
            parts.add(in.sources() + (in.sources() == 1 ? " source" : " sources"));
        }
        if (in.tanks() > 0) {
            parts.add(in.tanks() + (in.tanks() == 1 ? " bank" : " banks"));
        }
        if (in.stores() > 0) {
            parts.add(in.stores() + (in.stores() == 1 ? " capacitor" : " capacitors"));
        }
        return parts.isEmpty() ? (in.flow() > 0 ? "Fed by a panel above" : "Not wired") : String.join(", ", parts);
    }

    public static String outputWhat(BatteryView.Output o) {
        if (!o.wired()) {
            return "Not wired";
        }
        final List<String> parts = new ArrayList<>(3);
        if (o.panels() > 0) {
            parts.add(o.panels() == 1 ? "Distribution panel" : o.panels() + " distribution panels");
        }
        final int machines = o.devices() - o.panels();
        if (machines > 0) {
            parts.add(machines + (machines == 1 ? " device" : " devices"));
        }
        if (o.stores() > 0) {
            parts.add(o.stores() == 1 ? "Starter Capacitor" : o.stores() + " capacitors");
        }
        return parts.isEmpty() ? "Wired, nothing on it" : String.join(", ", parts);
    }

    public static String outputWhy(BatteryView.Output o, BatteryView v) {
        if (!o.wired()) {
            return "Nothing on this terminal";
        }
        if (!o.enabled()) {
            return "Breaker open: switched off here";
        }
        if (o.panels() > 0 && o.boardGranted() < o.boardAsked() && o.reason() != BatteryControllerPlan.Reason.OFF) {
            return "Board given " + fe(o.boardGranted()) + " of " + fe(o.boardAsked()) + ": "
                    + boardShort(o.boardReason());
        }
        return switch (o.reason()) {
            case SERVED -> o.panels() > 0 ? "Board granted everything it asked" : "Served in full";
            case PARTIAL -> "Short: the bus ran dry before this tier";
            case SHED -> "Shed: higher tiers took everything";
            case RECHARGE -> "Waiting while generation refills the banks";
            case FLOOR -> "Held: banks " + v.socPct() + "% are under its " + o.floorPct() + "% floor";
            case RATIONED -> "Metered to its board's Critical: banks under " + o.floorPct() + "%";
            case NO_SURPLUS -> "Surplus only, and nothing is spare";
            case OFF -> "Breaker open: switched off here";
            case IDLE -> o.stores() > 0 && o.storeFlow() > 0 ? "Refilling its capacitor" : "Nothing asking";
            case UNWIRED -> "Nothing on this terminal";
        };
    }

    public static String modeWords(BatteryView.Output o) {
        return switch (o.mode()) {
            case DIRECT -> "Direct";
            case LIMIT -> "Limit " + fe(o.limit());
            case SURPLUS -> "Surplus only";
        };
    }

    public static String modeBlurb(BatteryOutputMode mode) {
        return switch (mode) {
            case DIRECT -> "Serves everything it asks for, out of generation and the banks.";
            case LIMIT -> "Never more than the rate you set, even when the banks are full.";
            case SURPLUS -> "Only generation nobody else wants. Never touches the banks.";
        };
    }

    public static String bandRule(BatteryView v) {
        final int soc = v.socPct();
        final String where = switch (v.stance()) {
            case SURPLUS -> soc + "% is at or above healthy (" + v.healthyPct() + "%): Surplus";
            case NOMINAL -> soc + "% is between the band's top (" + v.ceilingPct() + "%) and healthy: Nominal";
            case CONSERVE -> soc + "% is inside the band (" + v.floorPct() + " to " + v.ceilingPct() + "%): Conserve";
            case CRITICAL -> soc + "% is under the floor (" + v.floorPct() + "%): Critical";
        };
        return where + ".";
    }

    public static String bandAllows(BatteryView v) {
        if (v.stance() == FeReserveReport.Stance.CRITICAL) {
            return "Only Critical may draw on the banks now, " + fe(v.criticalDraw()) + " FE/t at most.";
        }
        return "Ordinary load may take " + fe(v.sustainedDraw()) + " FE/t from the banks, Critical "
                + fe(v.criticalDraw()) + ".";
    }

    public static String bandWhy(BatteryView v) {
        if (v.capacity() <= 0) {
            return "No banks, so no band: everything generated passes straight through.";
        }
        final int minutes = v.floorMinutes();

        final boolean held = v.holdPct() > v.configuredFloorPct();
        if (minutes < 0) {
            return held ? "No Critical declared here: the floor is a board's hold."
                    : v.configuredFloorPct() <= 0
                    ? "No Critical declared, so no floor: the banks glide down instead of being held."
                    : "No Critical declared: the floor is " + v.configuredFloorPct() + "%.";
        }
        final String span = minutes < 1 ? "under a minute" : minutes + " min";
        return held ? "Held up from " + v.configuredFloorPct() + "% for a board (" + span + " of Critical, "
                + fe(v.bandWant()) + " FE/t)."
                : "Floor " + v.configuredFloorPct() + "% holds " + span + " of Critical at today's peak, "
                + fe(v.bandWant()) + " FE/t.";
    }

    public static String claimRule(BatteryView v) {
        if (v.upstreamHolds()) {
            return "No refill: a board above is short, and its machines come first.";
        }
        if (v.chargeClaim() > 0) {
            final int pct = v.generation() <= 0 ? 0 : (int) ((long) v.chargeClaim() * 100L / v.generation());
            return "Holding " + fe(v.chargeClaim()) + " FE/t (" + pct + "% of generation) back to refill the banks.";
        }
        return "Nothing held back: refilling starts under " + v.healthyPct() + "% and reaches " + v.claimPct()
                + "% of generation at the " + v.floorPct() + "% floor.";
    }

    public static String outputRule(BatteryView.Output o, BatteryView v) {
        if (!o.wired()) {
            return "Output " + (o.index() + 1) + ": not wired";
        }
        final StringBuilder sb = new StringBuilder("Output ").append(o.index() + 1).append(": ")
                .append(o.tier().getDisplayName()).append(", ").append(modeWords(o).toLowerCase(Locale.ROOT));
        sb.append(o.floorPct() > 0 ? ", floor " + o.floorPct() + "%" : ", no floor");
        sb.append(". Asked ").append(fe(o.asked())).append(", given ").append(fe(o.flow())).append(".");
        return sb.toString();
    }

    public static String storeRule(BatteryView v) {
        if (v.storeCount() <= 0) {
            return "No Starter Capacitors on any terminal or behind any board.";
        }
        final String held = fe(v.storeStored()) + " of " + fe(v.storeCapacity()) + " FE";
        if (v.storeCharged() > 0) {
            return "Refilled first, out of the spare: " + fe(v.storeCharged()) + " FE/t now, " + held + ".";
        }
        if (v.storeStored() >= v.storeCapacity()) {
            return "Full (" + held + "). They refill first, out of the spare, after a shot.";
        }
        return v.storeBudget() <= 0 ? "Waiting for spare: nothing is left after the outputs (" + held + ")."
                : "Refilled first, out of the spare (" + held + ").";
    }

    public static String bankRule(BatteryView v) {
        if (v.bankCharged() > 0) {
            return "What is left goes into the banks: +" + fe(v.bankCharged()) + " FE/t.";
        }
        if (v.bankDrawn() > 0) {
            return "Nothing left over: the banks are giving " + fe(v.bankDrawn()) + " FE/t instead.";
        }
        return "Nothing left over, nothing drawn.";
    }

    public static String boardShort(com.createstats.power.FeBoardDeal.Reason reason) {
        return switch (reason) {
            case GRANTED -> "granted in full";
            case SHARED -> "shared with other boards";
            case CHARGING -> "the refill comes first";
            case BAND -> "the band holds the banks";
            case UPSTREAM -> "a board above is short";
            case FLOOR -> "banks under the floor";
            case WIRE -> "the wire is full";
            case HELD -> "output held";
            case NOTHING -> "nothing to give";
        };
    }
}
