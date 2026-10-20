package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineWordsTest {
    private static final String SRC = "src/main/java/com/createstats/";
    private static final Path FE_GOGGLE = Path.of(SRC + "power/FeGoggle.java");
    private static final Path DYNAMO = Path.of(SRC + "blockentity/KineticDynamoBE.java");
    private static final Path BURNER = Path.of(SRC + "blockentity/BiofuelBurnerBE.java");
    private static final Path SOLAR = Path.of(SRC + "blockentity/SolarPanelBE.java");
    private static final Path STARTER = Path.of(SRC + "blockentity/StarterCapacitorBE.java");
    private static final Path BANK = Path.of(SRC + "blockentity/CapacitorBankBE.java");
    private static final Path CONTROLLER = Path.of(SRC + "blockentity/BatteryControllerBE.java");
    private static final Path STANCE = Path.of(SRC + "power/FeReserveReport.java");
    private static final Path PLAN = Path.of(SRC + "power/BatteryControllerPlan.java");
    private static final Path INTERFACE = Path.of(SRC + "blockentity/PortablePowerInterfaceBE.java");
    private static final Path CABLE = Path.of(SRC + "blockentity/CableBE.java");
    private static final Path INDOOR = Path.of(SRC + "blockentity/IndoorCableBE.java");
    private static final Path BORE = Path.of(SRC + "item/CableBoreItem.java");
    private static final Path JUNCTION = Path.of(SRC + "blockentity/DiagnosticJunctionBE.java");
    private static final Path JUNCTION_SCREEN = Path.of(SRC + "renderer/DiagnosticJunctionTextureManager.java");
    private static final Path OUTLET = Path.of(SRC + "blockentity/WallOutletBE.java");
    private static final Path CORD = Path.of(SRC + "item/ApplianceCordItem.java");
    private static final Path READOUT = Path.of(SRC + "power/ConnectorReadout.java");
    private static final Path PDU = Path.of(SRC + "blockentity/PowerDistributionUnitBE.java");
    private static final Path PDU_FACE = Path.of(SRC + "renderer/PowerDistributionUnitTextureManager.java");
    private static final Path PDU_SCREEN = Path.of(SRC + "gui/PowerDistributionUnitScreen.java");
    private static final Path DERIVATION = Path.of(SRC + "gui/pdu/PduDerivation.java");
    private static final Path CHART = Path.of(SRC + "gui/pdu/PduChart.java");
    private static final Path DAY = Path.of(SRC + "gui/pdu/PduDayOutlook.java");

    record Source(Path file, String literal, Map<Integer, String> fills, List<String> pieces, boolean more) {
        Source(Path file, String literal, Map<Integer, String> fills, List<String> pieces) {
            this(file, literal, fills, pieces, false);
        }

        Source(Path file, String literal, Map<Integer, String> fills) {
            this(file, literal, fills, null);
        }

        static Source then(Path file, String literal) {
            return new Source(file, literal, Map.of(), null, true);
        }

        Source(Path file, String literal) {
            this(file, literal, Map.of());
        }

        static Source pieces(Path file, String... pieces) {
            return new Source(file, null, Map.of(), List.of(pieces));
        }

        List<String> literals() {
            if (pieces == null) {
                return List.of(literal);
            }
            return pieces.stream().filter(x -> !x.isEmpty()).toList();
        }
    }

    static final Map<String, Source> QUOTES = new LinkedHashMap<>();

    static {
        final String g = "create_stats.book.goggle.";

        QUOTES.put(g + "output", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Output")));
        QUOTES.put(g + "idle", new Source(FE_GOGGLE, "Idle"));
        QUOTES.put(g + "kinetic_dynamo.capable",
                new Source(DYNAMO, "Capable of %,d FE/t, grid not drawing the rest"));
        QUOTES.put(g + "biofuel_burner.burning", new Source(BURNER, "Burning, %s left"));
        QUOTES.put(g + "biofuel_burner.auto_fed", new Source(BURNER, "Auto-fed: forecasts keep it burning"));
        QUOTES.put(g + "biofuel_burner.holding", new Source(BURNER, "Buffer full, holding the fire"));
        QUOTES.put(g + "biofuel_burner.ready", new Source(BURNER, "Ready to light"));
        QUOTES.put(g + "biofuel_burner.out_of_fuel", new Source(BURNER, "Out of fuel"));
        QUOTES.put(g + "solar_panel.array", new Source(SOLAR, "Array: %d %s, x%.2f", Map.of(1, "panels")));
        QUOTES.put(g + "solar_panel.night", new Source(SOLAR, "Night"));
        QUOTES.put(g + "solar_panel.holding", new Source(SOLAR, "Buffer full, holding %d FE/t"));
        QUOTES.put(g + "solar_panel.covered", new Source(SOLAR, "Shaded by the panel above"));
        QUOTES.put(g + "solar_panel.no_sky", new Source(SOLAR, "No sky above"));
        QUOTES.put(g + "solar_panel.shaded", new Source(SOLAR, "Shaded, %d%% of the sky"));
        QUOTES.put(g + "solar_panel.raining", new Source(SOLAR, "%s, x%.2f", Map.of(0, "Raining")));
        QUOTES.put(g + "solar_panel.thunder", new Source(SOLAR, "%s, x%.2f", Map.of(0, "Thunderstorm")));
        QUOTES.put(g + "solar_panel.too_little", new Source(SOLAR, "Too little light to generate"));

        QUOTES.put(g + "charging", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Charging")));
        QUOTES.put(g + "draining", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Draining")));
        QUOTES.put(g + "holding_steady", new Source(FE_GOGGLE, "Holding steady"));

        QUOTES.put(g + "starter_capacitor.armed", new Source(STARTER, "Armed"));
        QUOTES.put(g + "starter_capacitor.ready", new Source(STARTER, "Ready: fires on a redstone pulse"));
        QUOTES.put(g + "starter_capacitor.charging", Source.pieces(STARTER, "Charging, full in ", ""));
        QUOTES.put(g + "starter_capacitor.holding", new Source(STARTER, "Holding: charges, never fires"));
        QUOTES.put(g + "starter_capacitor.short", new Source(STARTER, "Grid short: firing in %.1f s"));
        QUOTES.put(g + "starter_capacitor.discharging", new Source(STARTER, "Discharging  %,d FE/t, %.1f s left"));
        QUOTES.put(g + "starter_capacitor.burned", new Source(STARTER, "Burned off in the gap: %,d FE"));
        QUOTES.put(g + "starter_capacitor.no_circuit", new Source(STARTER, "No circuit on either terminal"));
        QUOTES.put(g + "starter_capacitor.limiter",
                new Source(STARTER, "Behind a Power Limiter: a burst cannot reach the panel"));
        QUOTES.put(g + "starter_capacitor.input",
                new Source(STARTER, "On a controller input: fires into it, cannot charge there"));
        QUOTES.put(g + "starter_capacitor.managed",
                new Source(STARTER, "Managed: fires when the board or controller comes up short"));

        QUOTES.put(g + "capacitor_bank.in_out", new Source(BANK, "In %,d FE/t, out %,d FE/t"));
        QUOTES.put(g + "capacitor_bank.full_in", Source.pieces(BANK, "Full in ~", ""));
        QUOTES.put(g + "capacitor_bank.empty_in", Source.pieces(BANK, "Empty in ~", ""));
        QUOTES.put(g + "capacitor_bank.managed_full",
                Source.pieces(BANK, "Managed by a Battery Controller: full in ~", ""));
        QUOTES.put(g + "capacitor_bank.managed_settles",
                Source.pieces(BANK, "Managed by a Battery Controller: settles near ", "%"));
        QUOTES.put(g + "capacitor_bank.pooled", new Source(BANK, "Pooled across %d blocks, fills bottom up"));

        QUOTES.put(g + "battery_controller.surplus", new Source(STANCE, "Surplus"));
        QUOTES.put(g + "battery_controller.nominal", new Source(STANCE, "Nominal"));
        QUOTES.put(g + "battery_controller.conserve", new Source(STANCE, "Conserve"));
        QUOTES.put(g + "battery_controller.critical", new Source(STANCE, "Critical"));

        QUOTES.put(g + "battery_controller.refill",
                new Source(CONTROLLER, "Holding %,d FE/t back to refill the banks"));
        QUOTES.put(g + "battery_controller.hold",
                new Source(CONTROLLER, "Holding %d%% back for a board upstream (the floor is %d%% without it)"));
        QUOTES.put(g + "battery_controller.no_storage", new Source(CONTROLLER, "No storage wired to any input"));
        QUOTES.put(g + "battery_controller.stray",
                Source.pieces(CONTROLLER, "", " is wired to a panel leg, which carries nothing"));

        QUOTES.put(g + "battery_controller.served", new Source(PLAN, "Served"));
        QUOTES.put(g + "battery_controller.partial", new Source(PLAN, "Partial"));
        QUOTES.put(g + "battery_controller.off", new Source(PLAN, "Breaker off"));
        QUOTES.put(g + "battery_controller.floor", new Source(PLAN, "Below floor"));
        QUOTES.put(g + "battery_controller.rationed", new Source(PLAN, "Rationed"));
        QUOTES.put(g + "battery_controller.no_surplus", new Source(PLAN, "No surplus"));
        QUOTES.put(g + "battery_controller.recharge", new Source(PLAN, "Recharging reserve"));
        QUOTES.put(g + "battery_controller.shed", new Source(PLAN, "Shed"));
        QUOTES.put(g + "battery_controller.idle", new Source(PLAN, "Idle"));
        QUOTES.put(g + "battery_controller.unwired", new Source(PLAN, "Not wired"));

        QUOTES.put(g + "portable_power_interface.charging", Source.pieces(INTERFACE, "Charging ", ""));
        QUOTES.put(g + "portable_power_interface.drawing", Source.pieces(INTERFACE, "Drawing ", ""));
        QUOTES.put(g + "portable_power_interface.full", new Source(INTERFACE, "Train full"));
        QUOTES.put(g + "portable_power_interface.waiting_supply", new Source(INTERFACE, "Docked, waiting for supply"));
        QUOTES.put(g + "portable_power_interface.waiting_circuit",
                new Source(INTERFACE, "Docked, waiting for the circuit to take it"));
        QUOTES.put(g + "portable_power_interface.nothing_docked", new Source(INTERFACE, "Nothing docked"));
        QUOTES.put(g + "portable_power_interface.floor_station",
                Source.pieces(INTERFACE, "Paused: station charge is down to ", "%"));
        QUOTES.put(g + "portable_power_interface.floor_train",
                Source.pieces(INTERFACE, "Paused: train charge is down to ", "%"));
        QUOTES.put(g + "portable_power_interface.empty", new Source(INTERFACE, "Train empty"));
        QUOTES.put(g + "portable_power_interface.station_held",
                new Source(INTERFACE, "Paused: the board feeding this station is short"));
        QUOTES.put(g + "portable_power_interface.shed", new Source(INTERFACE, "Shed at the panel"));
        QUOTES.put(g + "portable_power_interface.off", new Source(INTERFACE, "Off (redstone)"));
        QUOTES.put(g + "portable_power_interface.locked_draw",
                new Source(INTERFACE, "Another interface is drawing from this train"));
        QUOTES.put(g + "portable_power_interface.locked_charge",
                new Source(INTERFACE, "Another interface is charging this train"));
        QUOTES.put(g + "portable_power_interface.no_storage", new Source(INTERFACE, "Nothing aboard stores FE"));
        QUOTES.put(g + "portable_power_interface.wrong_charge",
                new Source(INTERFACE, "Nothing feeds this circuit: charge from a controller output"));
        QUOTES.put(g + "portable_power_interface.wrong_draw",
                new Source(INTERFACE, "Nothing reads this circuit: draw into a controller input, not past a limiter"));

        QUOTES.put(g + "carrying", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Carrying")));
        QUOTES.put(g + "capacity", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Capacity")));
        QUOTES.put(g + "needs", new Source(FE_GOGGLE, "Needs %,d, getting %,d FE/t"));

        QUOTES.put(g + "cable.throttled", new Source(CABLE, "Run of %d throttled to %,d FE/t by a thinner grade"));
        QUOTES.put(g + "cable.held", new Source(CABLE, "Branch held to %,d FE/t by an inline limiter"));

        QUOTES.put(g + "indoor_cable.bores", new Source(INDOOR, "Run of %d segments, %d bore%s from here"));

        QUOTES.put(g + "cable_bore.bored", new Source(BORE, "Bored through %d block%s."));
        QUOTES.put(g + "cable_bore.pulled", new Source(BORE, "Bore pulled."));
        QUOTES.put(g + "cable_bore.aim", new Source(BORE, "Point at what is in the way, with cable behind you."));
        QUOTES.put(g + "cable_bore.no_partner", new Source(BORE, "No matching cable within %d blocks that way."));
        QUOTES.put(g + "cable_bore.nothing", new Source(BORE, "Nothing to bore through."));
        QUOTES.put(g + "cable_bore.already", new Source(BORE, "Already bored that way."));
        QUOTES.put(g + "cable_bore.no_bore", new Source(BORE, "No bore that way."));

        QUOTES.put(g + "junction.conducting", new Source(JUNCTION, "Power: conducting"));
        QUOTES.put(g + "junction.cut_off", new Source(JUNCTION, "Power: cut off"));
        QUOTES.put(g + "junction.flow", new Source(JUNCTION, "Flow: %,d / %,d FE/t"));
        QUOTES.put(g + "junction.separate", new Source(JUNCTION, "Mode: separate A, B, C"));
        QUOTES.put(g + "junction.merged", new Source(JUNCTION, "Mode: merged bus, all six studs"));
        QUOTES.put(g + "junction.nothing_passes", new Source(JUNCTION_SCREEN, "HANDLE OPEN, NOTHING PASSES"));

        QUOTES.put(g + "outlet.feeding", new Source(OUTLET, "Feeding"));
        QUOTES.put(g + "outlet.live", new Source(OUTLET, "Live, nothing drawing"));
        QUOTES.put(g + "outlet.no_supply", new Source(OUTLET, "No supply"));
        QUOTES.put(g + "outlet.dead_bore", new Source(OUTLET, "Bored, but the circuit is dead"));
        QUOTES.put(g + "outlet.gated", new Source(OUTLET, "Gated by a switch"));
        QUOTES.put(g + "outlet.paired", new Source(OUTLET, "Paired to a switch"));

        QUOTES.put(g + "cord.held", new Source(CORD, "Socket held. Click the machine to plug it in."));
        QUOTES.put(g + "cord.plugged", new Source(CORD, "Plugged in. %d FE/t through the socket."));
        QUOTES.put(g + "cord.unplugged", new Source(CORD, "Unplugged."));
        QUOTES.put(g + "cord.nothing_plugged", new Source(CORD, "Nothing is plugged into that socket."));
        QUOTES.put(g + "cord.gone", new Source(CORD, "That socket is gone."));
        QUOTES.put(g + "cord.itself", new Source(OUTLET, "A socket cannot be plugged into itself."));
        QUOTES.put(g + "cord.both_full", new Source(OUTLET, "Both sockets already have a cord. Unplug one first."));
        QUOTES.put(g + "cord.no_plug", new Source(OUTLET, "Nothing on that block will take a plug."));
        QUOTES.put(g + "cord.too_far", new Source(OUTLET, "Too far for a cord. Move it closer, or run cable."));
        QUOTES.put(g + "cord.no_way", new Source(OUTLET, "No way to lay a cord between those two."));

        QUOTES.put(g + "connector.feeding", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Feeding block")));
        QUOTES.put(g + "connector.drawing", new Source(FE_GOGGLE, "%s: %,d FE/t", Map.of(0, "Drawing from block")));
        QUOTES.put(g + "connector.block_idle", new Source(READOUT, "Its block: idle"));
        QUOTES.put(g + "connector.held", new Source(READOUT, "Branch held to %,d FE/t by an inline limiter"));
        QUOTES.put(g + "connector.wires", Source.then(READOUT, "%d of %d wire%s: "));
        QUOTES.put(g + "connector.on_cable", new Source(READOUT, "No wires, on a cable"));
        QUOTES.put(g + "connector.serving", new Source(READOUT, "No wires, serving the block it is on"));
        QUOTES.put(g + "connector.nothing", new Source(READOUT, "No wires, no cable, and its block takes no power"));
        QUOTES.put(g + "connector.bridging", new Source(READOUT, "%d bridging to another mod's network"));
        QUOTES.put(g + "connector.send_only",
                new Source(READOUT, "Send only: a New Age network cannot be drawn from"));
        QUOTES.put(g + "connector.rated", new Source(READOUT, "Rated %,d FE/t, its grade"));
        QUOTES.put(g + "connector.rated_wiring", new Source(READOUT, "Rated %,d FE/t; its wiring carries %,d"));
        QUOTES.put(g + "connector.cable", new Source(READOUT, "Cable touching it: %,d FE/t"));
        QUOTES.put(g + "connector.block_takes_none", new Source(READOUT, "Its block takes no power"));

        QUOTES.put(g + "more", new Source(FE_GOGGLE, "Sneak for more"));

        QUOTES.put(g + "pdu.mode", Source.pieces(PDU, "Mode: ", ""));
        QUOTES.put(g + "pdu.branch", new Source(PDU, "Branch: %d served, %d shed"));
        QUOTES.put(g + "pdu.gen", new Source(PDU, "Gen %,d FE/t, running %,d of %,d"));
        QUOTES.put(g + "pdu.reserve_low", new Source(PDU, "Reserve low: what is left is held for Critical"));
        QUOTES.put(g + "pdu.export_held",
                new Source(PDU, "Back boss held: the buffer is below the mark, the branch comes first"));
        QUOTES.put(g + "pdu.upstream_off", new Source(PDU, "An upstream panel is not feeding this board right now"));
        QUOTES.put(g + "pdu.locked_off_upstream", new Source(PDU, "Locked off from the panel upstream"));
        QUOTES.put(g + "pdu.stray_controller",
                Source.pieces(PDU, "", " runs to a Battery Controller input and carries nothing"));
        QUOTES.put(g + "pdu.stray_stud", Source.pieces(PDU, "", " runs to another panel's stud and carries nothing"));
        QUOTES.put(g + "pdu.new_age",
                new Source(PDU, "New Age connector with no Coupler: fit a Universal Power Coupler between them"));

        QUOTES.put(g + "pdu.face_roll", new Source(PDU_FACE, "%d SERVED   %d SHED"));
        QUOTES.put(g + "pdu.face_no_input", new Source(PDU_FACE, "NO INPUT"));

        QUOTES.put(g + "pdu.working.running", new Source(DERIVATION, "Everything on the branch is running."));
        QUOTES.put(g + "pdu.working.supply", new Source(DERIVATION, "Supply is the limit."));
        QUOTES.put(g + "pdu.working.wiring", new Source(DERIVATION, "The wiring is the limit, not the supply."));
        QUOTES.put(g + "pdu.working.refilling", new Source(DERIVATION, "The controller is refilling its reserve."));
        QUOTES.put(g + "pdu.working.protecting", new Source(DERIVATION, "The controller is protecting its reserve."));
        QUOTES.put(g + "pdu.working.not_auto", new Source(DERIVATION, "Active Auto is not running."));
        QUOTES.put(g + "pdu.working.unwired", new Source(DERIVATION, "Nothing is wired to this panel."));
        QUOTES.put(g + "pdu.working.no_devices", new Source(DERIVATION, "No devices on the branch."));
        QUOTES.put(g + "pdu.working.over_budget", new Source(DERIVATION, "Over budget by"));
        QUOTES.put(g + "pdu.history.gathering", new Source(CHART, "Gathering history"));
        QUOTES.put(g + "pdu.history.covered",
                new Source(DAY, "Covered: the supply carries the critical load all day"));
        QUOTES.put(g + "pdu.history.no_critical", new Source(DAY, "No critical load declared, so nothing to bank for"));
        QUOTES.put(g + "pdu.history.short", new Source(DAY, "Short %s FE before the supply is back%s"));

        QUOTES.put(g + "pdu.inout.shed", Source.pieces(PDU_SCREEN, "Shed ", " FE/t"));
        QUOTES.put(g + "pdu.inout.spare", new Source(PDU_SCREEN, "Spare, to buffer"));
        QUOTES.put(g + "pdu.inout.board_upstream", new Source(PDU_SCREEN, "Board upstream"));
        QUOTES.put(g + "pdu.inout.quiet", new Source(PDU_SCREEN, "Nothing is flowing through this board."));
        QUOTES.put(g + "pdu.inout.legs_limit", new Source(PDU_SCREEN, "The legs are the limit, not the supply."));
        QUOTES.put(g + "pdu.inout.night", Source.pieces(PDU_SCREEN, "Night outlook: ",
                " FE short before supply is back; asking the boards on its legs to hold it."));
        QUOTES.put(g + "pdu.inout.holding", Source.pieces(PDU_SCREEN, "Holding ", " FE back for a board upstream."));
        QUOTES.put(g + "pdu.inout.not_auto", new Source(PDU_SCREEN, "Switch to Active Auto to see a budget."));
    }

    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    private static final Pattern SPECIFIER = Pattern.compile("%%|%[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");

    private static String source(Path p) throws IOException {
        return COMMENTS.matcher(Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")).replaceAll("");
    }

    static String expected(Source s) {
        return printed(rawExpected(s));
    }

    static String printed(String words) {
        return words.replaceAll(" {2,}", " ");
    }

    private static String rawExpected(Source s) {
        if (s.pieces() != null) {
            final List<String> escaped = s.pieces().stream().map(x -> x.replace("%", "%%")).toList();
            return String.join("%s", escaped);
        }
        final Matcher m = SPECIFIER.matcher(s.literal());
        final StringBuilder out = new StringBuilder();
        int index = 0;
        while (m.find()) {
            if (m.group().equals("%%")) {
                m.appendReplacement(out, "%%");
                continue;
            }
            final String fill = s.fills().get(index++);
            m.appendReplacement(out, Matcher.quoteReplacement(fill != null ? fill : "%s"));
        }
        m.appendTail(out);
        if (s.more()) {
            out.append("%s");
        }
        return out.toString();
    }

    @Test
    void everyQuotedWordIsTheLiteralInItsSource() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        final List<String> bad = new ArrayList<>();
        for (final Map.Entry<String, Source> e : QUOTES.entrySet()) {
            final Source s = e.getValue();
            final String code = source(s.file());
            for (final String literal : s.literals()) {
                if (!code.contains("\"" + literal + "\"")) {
                    bad.add(e.getKey() + ": " + s.file().getFileName() + " no longer holds \"" + literal + "\"");
                }
            }
            for (final String fill : s.fills().values()) {
                if (!code.contains("\"" + fill + "\"")) {
                    bad.add(e.getKey() + ": " + s.file().getFileName() + " no longer holds the word \"" + fill + "\"");
                }
            }
            final String book = lang.get(e.getKey());
            if (!expected(s).equals(book)) {
                bad.add(e.getKey() + ": the book says \"" + book + "\", the source \"" + expected(s) + "\"");
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void everyQuoteASheetUsesIsHeld() {
        final TreeSet<String> used = new TreeSet<>();
        for (final Sheet sheet : Book.sheets()) {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Status st) {
                    for (final Part.Word w : st.rows()) {
                        if (w.wordKey().startsWith("create_stats.book.goggle.")) {
                            used.add(w.wordKey());
                        }
                    }
                }
            }
        }
        final TreeSet<String> missing = new TreeSet<>(used);
        missing.removeAll(QUOTES.keySet());
        assertTrue(missing.isEmpty(), "goggle words quoted with no source to hold them to: " + missing);
        final TreeSet<String> stale = new TreeSet<>(QUOTES.keySet());
        stale.removeAll(used);
        assertTrue(stale.isEmpty(), "sources listed for words no sheet quotes: " + stale);
    }
}
