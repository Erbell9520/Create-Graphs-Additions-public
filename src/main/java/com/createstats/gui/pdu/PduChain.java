package com.createstats.gui.pdu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PduChain {
    private PduChain() {
    }

    public record Upstream(boolean linked, int controllers, int socPct, int stored, int capacity,
                           String stanceLabel, String stanceBlurb, int generation, int sustained,
                           int criticalDraw, int chargeClaim, int floorPct, int ceilingPct,
                           int surveyTanks, int surveyStored, int surveyCapacity, int sourceCount,
                           boolean feedMisplaced, int panels, int panelFeed, int observedSupply) {
        public Upstream(boolean linked, int controllers, int socPct, int stored, int capacity,
                        String stanceLabel, String stanceBlurb, int generation, int sustained,
                        int criticalDraw, int chargeClaim, int floorPct, int ceilingPct,
                        int surveyTanks, int surveyStored, int surveyCapacity, int sourceCount,
                        boolean feedMisplaced) {
            this(linked, controllers, socPct, stored, capacity, stanceLabel, stanceBlurb, generation,
                    sustained, criticalDraw, chargeClaim, floorPct, ceilingPct, surveyTanks,
                    surveyStored, surveyCapacity, sourceCount, feedMisplaced, 0, 0, 0);
        }

        public Upstream(boolean linked, int controllers, int socPct, int stored, int capacity,
                        String stanceLabel, String stanceBlurb, int generation, int sustained,
                        int criticalDraw, int chargeClaim, int floorPct, int ceilingPct,
                        int surveyTanks, int surveyStored, int surveyCapacity, int sourceCount,
                        boolean feedMisplaced, int panels, int panelFeed) {
            this(linked, controllers, socPct, stored, capacity, stanceLabel, stanceBlurb, generation,
                    sustained, criticalDraw, chargeClaim, floorPct, ceilingPct, surveyTanks,
                    surveyStored, surveyCapacity, sourceCount, feedMisplaced, panels, panelFeed, 0);
        }

        public boolean blind() {
            return !linked && surveyTanks <= 0 && sourceCount <= 0 && panels <= 0;
        }
    }

    public record Board(int intake, int stored, int capacity, int floorPct, int budget, int demand,
                        int served, int shed, boolean shedFromUpstream, boolean lockedOff) {
    }

    public record Panel(String name, int leg, int draw, String tier, String state) {
    }

    public record Downstream(List<Panel> panels, int controllers, int controllerFe, int backExport,
                             int strayLeg) {
        public Downstream {
            panels = List.copyOf(panels);
        }

        public boolean isEmpty() {
            return panels.isEmpty() && controllers <= 0 && backExport <= 0 && strayLeg < 0;
        }
    }

    private static String fe(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE/t";
    }

    private static String feTotal(int value) {
        return String.format(Locale.ROOT, "%,d", value) + " FE";
    }

    private static String pct(int part, int whole) {
        return whole <= 0 ? "0%" : Math.round(part * 100.0f / whole) + "%";
    }

    public static List<PduDerivation.Row> rows(Upstream up, Board board, Downstream down) {
        final List<PduDerivation.Row> rows = new ArrayList<>(24);
        upstream(rows, up);
        board(rows, board);
        downstream(rows, down);
        return rows;
    }

    private static void upstream(List<PduDerivation.Row> rows, Upstream up) {
        rows.add(new PduDerivation.Row(PduDerivation.Kind.HEADING, "Upstream", "", ""));

        if (up.feedMisplaced()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    "A New Age connector is bolted straight to this board.", "",
                    "Their network only conducts against a Universal Power Coupler. Fit one between them."));
        }

        if (up.panels() > 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    up.panels() == 1 ? "Fed by a Power Distribution Unit"
                            : "Fed by " + up.panels() + " Power Distribution Units",
                    fe(up.panelFeed()),
                    "this board is a device on its branch; raise its tier there to be served sooner"));
        }
        if (up.linked()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    up.controllers() > 1 ? up.controllers() + " Battery Controllers"
                            : "Battery Controller",
                    up.socPct() + "% charged",
                    up.controllers() > 1
                            ? "pooled; the worst of their stances is the one this board obeys"
                            : "its banks are behind it, so this board is told rather than surveying"));
            rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM, "banks hold",
                    feTotal(up.stored()) + " of " + feTotal(up.capacity()), ""));
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT, "Stance", up.stanceLabel(),
                    up.stanceBlurb() + ", band " + up.floorPct() + " to " + up.ceilingPct()
                            + " percent"));
            rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM, "generation on its inputs",
                    fe(up.generation()), ""));
            rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM, "allowed out of the reserve",
                    fe(up.sustained()),
                    "Critical may take " + fe(up.criticalDraw()) + " of it"));
            if (up.chargeClaim() > 0) {
                rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM,
                        "held back to refill the banks", "-" + fe(up.chargeClaim()),
                        "this ends by itself; it is not a shortage"));
            }
            return;
        }
        if (up.surveyTanks() > 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    up.surveyTanks() == 1 ? "Bank on the feed"
                            : up.surveyTanks() + " tanks on the feed",
                    pct(up.surveyStored(), up.surveyCapacity()) + " charged",
                    "surveyed directly, with no controller between them and this board"));
            rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM, "they hold",
                    feTotal(up.surveyStored()) + " of " + feTotal(up.surveyCapacity()), ""));
        }

        if (up.blind() && up.observedSupply() > 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    "Fed by something this board cannot price", fe(up.observedSupply()),
                    "no generator, bank, panel or controller on the feed answers a survey, so the "
                            + "budget follows what has actually been arriving"));
            return;
        }
        rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT, "Generators on the feed",
                String.valueOf(up.sourceCount()),
                up.sourceCount() == 0
                        ? "nothing this board can price; it is budgeting on measured intake alone"
                        : "counted on the side bosses"));
        if (up.blind()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    "Nothing is reaching this board.", "",
                    "Feed a side boss, or wire a Battery Controller's output to a connector on one."));
        }
    }

    private static void board(List<PduDerivation.Row> rows, Board board) {
        rows.add(new PduDerivation.Row(PduDerivation.Kind.HEADING, "This board", "", ""));
        rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT, "Taking in", fe(board.intake()),
                "measured on the side bosses last tick"));
        rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT, "Buffer",
                pct(board.stored(), board.capacity()) + "  " + feTotal(board.stored()),
                "floor at " + board.floorPct() + " percent, below which only Critical draws"));
        rows.add(new PduDerivation.Row(PduDerivation.Kind.RESULT, "Run budget", fe(board.budget()),
                "what a running device is allowed to draw against"));
        rows.add(new PduDerivation.Row(PduDerivation.Kind.TERM, "branch wants", fe(board.demand()),
                board.served() + " running, " + board.shed() + " shed"));
        if (board.lockedOff()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    "Locked off from the panel upstream.", "",
                    "Nothing is being fed to this board on purpose. Unlock its row on that panel."));
        } else if (board.shedFromUpstream()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    "The panel upstream has shed this board.", "",
                    "It is coasting on its own buffer and shedding its own branch by tier. "
                            + "Raise its tier on that panel, or give that panel more supply."));
        }
    }

    private static void downstream(List<PduDerivation.Row> rows, Downstream down) {
        rows.add(new PduDerivation.Row(PduDerivation.Kind.HEADING, "Downstream", "", ""));
        if (down.isEmpty()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    "Nothing downstream of this board is a panel or a controller.", "",
                    "Its legs feed machines directly, so this page has nothing further to say."));
            return;
        }

        if (down.strayLeg() >= 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.VERDICT,
                    PduLegRow.name(down.strayLeg()) + " runs to a Battery Controller input.", "",

                    "That span carries nothing: a leg pushes into machines, and a controller is fed "
                            + "only through its own terminals. Run cable off this board's back boss "
                            + "instead, then wire the controller's input to a connector on that "
                            + "cable run."));
        }
        for (Panel panel : down.panels()) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    PduLegRow.name(panel.leg()) + "  " + panel.name(), fe(panel.draw()),
                    panel.tier() + " tier, " + panel.state()));
        }
        if (down.controllers() > 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT,
                    down.controllers() == 1 ? "Battery Controller on the back boss"
                            : down.controllers() + " Battery Controllers on the back boss",
                    fe(down.controllerFe()),
                    "drawing this board as a feed, which is how a board chains into the next reserve"));
        }

        final int unattributed = down.backExport() - down.controllerFe();
        if (unattributed > 0) {
            rows.add(new PduDerivation.Row(PduDerivation.Kind.INPUT, "Other draw on the back boss",
                    fe(unattributed),
                    "wiring this board does not drive: another mod's network, or another panel"));
        }
        rows.add(new PduDerivation.Row(PduDerivation.Kind.RESULT, "Leaving the back boss",
                fe(down.backExport()),
                "spent from this board's buffer, against the back leg's own rating"));
    }
}
