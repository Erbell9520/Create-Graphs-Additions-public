package com.createstats.book.content;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.book.content.Part.Application;
import com.createstats.book.content.Part.Control;
import com.createstats.book.content.Part.Controls;
import com.createstats.book.content.Part.Faults;
import com.createstats.book.content.Part.Figure;
import com.createstats.book.content.Part.Gesture;
import com.createstats.book.content.Part.Instruction;
import com.createstats.book.content.Part.Link;
import com.createstats.book.content.Part.Made;
import com.createstats.book.content.Part.Note;
import com.createstats.book.content.Part.NoteKind;
import com.createstats.book.content.Part.Rating;
import com.createstats.book.content.Part.Ratings;
import com.createstats.book.content.Part.SeeAlso;
import com.createstats.book.content.Part.Status;
import com.createstats.book.content.Part.Step;
import com.createstats.book.content.Part.Steps;
import com.createstats.book.content.Part.Word;
import com.createstats.book.content.Part.World;
import com.createstats.book.specimen.PduPlates;
import com.createstats.gui.pdu.PduLegRow;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeRates;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import com.createstats.power.PduStuds;
import net.minecraft.SharedConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class ControlSheets {
    private ControlSheets() {
    }

    private static final String CONTROL = "create_stats.book.group.control";
    private static final String P = "create_stats.book.";
    private static final String D = P + "pdu.";

    private static final String G = P + "goggle.pdu.";

    private static String seconds(int ticks) {
        return Num.of(ticks / (double) SharedConstants.TICKS_PER_SECOND);
    }

    private static String grouped(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static List<String> viewTitles() {
        return com.createstats.gui.PowerDistributionUnitScreen.viewTitles();
    }

    private static Part.Tab view(int index) {
        return new Part.Tab(index, () -> viewTitles());
    }

    private static Supplier<Object[]> viewName(int index) {
        return () -> new Object[]{viewTitles().get(index).toUpperCase(Locale.ROOT)};
    }

    private static Control plate(String plate, String id, String keysKey) {
        final String k = D + plate + ".";
        return new Control(id, k + "control." + id, k + "function." + id, keysKey, null);
    }

    private static Control rail() {
        return new Control("views", D + "control.views", D + "function.views", D + "keys.views",
                () -> new Object[]{1, viewTitles().size()});
    }

    private static final String FIGURE = "...";

    private static Word legState(Mark mark, PduLegRow.State state, String meaning, Symptom symptom) {
        return new Word(mark, P + "value.figure", () -> new Object[]{state.label()}, D + "legs.status." + meaning,
                symptom);
    }

    private static Word river(Mark mark, com.createstats.gui.pdu.PduFlowModel.Kind kind, String meaning,
                              Symptom symptom) {
        return new Word(mark, P + "value.figure", () -> new Object[]{kind.label()}, D + "in_out.status." + meaning,
                symptom);
    }

    private static Word reason(Mark mark, PduReason reason, String meaning, Symptom symptom) {
        return new Word(mark, P + "value.figure", () -> new Object[]{reason.shortLabel()},
                D + "status." + meaning, symptom);
    }

    public static final Sheet PDU = Sheet.of("power_distribution_unit", Section.POWER)
            .group(CONTROL)
            .title("block.create_stats.power_distribution_unit")
            .covers("create_stats:power_distribution_unit")
            .hook(D + "hook")
            .part(new Figure("power_distribution_unit", P + "part.parts_and_faces", D + "caption"))
            .part(new Ratings(List.of(

                    new Rating(D + "rating.legs", D + "value.legs",
                            () -> new Object[]{PowerDistributionUnitBE.LEGS, PduStuds.COUNT}),

                    new Rating(D + "rating.hv_studs", D + "value.hv_studs",
                            () -> new Object[]{FeRates.perTick(PowerDistributionUnitBE.hvStudRate()), 1,
                                    PowerDistributionUnitBE.HV_STUDS}),

                    new Rating(D + "rating.stud", D + "value.stud",
                            () -> new Object[]{FeRates.perTick(PowerDistributionUnitBE.studRate())}),

                    new Rating(D + "rating.wires", P + "value.count",
                            () -> new Object[]{ConnectorTier.ADVANCED.maxLinks()}),

                    new Rating(D + "rating.back", D + "value.back",
                            () -> new Object[]{FeRates.perTick(PowerDistributionUnitBE.backRate())}),

                    new Rating(D + "rating.feed", P + "value.figure",
                            () -> new Object[]{FeRates.perTick(PowerDistributionUnitBE.inputRatePerBoss())}),

                    new Rating(P + "rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(Configs.pduBuffer())}),

                    new Rating(D + "rating.upkeep", D + "value.upkeep",
                            () -> new Object[]{FeRates.perTick(PowerDistributionUnitBE.idleDrainPerTick())}))))
            .part(new Application(List.of(
                    new Link("create_stats:kinetic_dynamo", P + "chain.supply", false),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:power_distribution_unit", D + "chain.board", true),
                    new Link("create_stats:copper_wire", P + "chain.wire", false),
                    new Link("create_stats:power_connector", P + "chain.terminal", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    D + "application"))
            .part(new Made(P + "part.made", P + "made.note", List.of("power_distribution_unit")))
            .part(new SeeAlso(List.of("power_meter", "fe_battery_controller", "capacitor_bank", "copper_wire",
                    "power_connector", "power_limiter", "main_breaker")))
            .part(new Part.Ponder("create_stats:power_distribution_unit"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("power_distribution_unit", P + "part.part_designations", List.of(

                    new Control("face", D + "control.face", D + "function.face", P + "keys.right_click", null),

                    new Control("lamps", D + "control.lamps", D + "function.lamps", null, null),

                    new Control("studs", D + "control.studs", D + "function.studs", P + "keys.wire_then_terminal",
                            null),

                    new Control("inputs", D + "control.inputs", D + "function.inputs", null, null),

                    new Control("back", D + "control.back", D + "function.back", null, null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.wire_on_stud", D + "gesture.wire"),
                    new Gesture(P + "gesture.sneak_wire_on_stud", D + "gesture.strip"),
                    new Gesture(P + "gesture.breaking", D + "gesture.drops"),
                    new Gesture(P + "gesture.display_link", D + "gesture.display_link"),
                    new Gesture(P + "gesture.goggles", D + "gesture.goggles"))))

            .part(new Status(D + "part.face", List.of(

                    new Word(Mark.RUNNING, P + "value.figure", () -> new Object[]{String.join(", ",
                            PduMode.AUTO.name(), PduMode.MANUAL.name(), PduMode.OFF.name())},
                            D + "status.face_modes", null),
                    new Word(Mark.CAUTION, G + "face_roll", () -> new Object[]{
                            PduPlates.decide().plan().admitted().size(), PduPlates.shed().size()},
                            D + "status.face_roll", Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, G + "face_no_input", D + "status.face_no_input", Symptom.NO_SUPPLY),
                    new Word(Mark.RUNNING, D + "word.lamp_green", D + "status.lamp_green", null),
                    new Word(Mark.CAUTION, D + "word.lamp_steel", D + "status.lamp_steel", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, D + "word.lamp_amber", D + "status.lamp_amber", Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, D + "word.lamp_red", D + "status.lamp_red", Symptom.NO_SUPPLY),
                    new Word(Mark.IDLE, D + "word.lamp_dark", D + "status.lamp_dark", Symptom.READS_ZERO))))

            .part(new Status(D + "part.goggles", List.of(
                    new Word(Mark.RUNNING, G + "mode", () -> new Object[]{PduMode.AUTO.getDisplayName()},
                            D + "status.mode", null),
                    new Word(Mark.CAUTION, G + "branch", () -> new Object[]{
                            PduPlates.decide().plan().admitted().size(), PduPlates.shed().size()},
                            D + "status.branch", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "gen", () -> new Object[]{grouped(PduPlates.generation()),
                            grouped(PduPlates.decide().plan().admittedDemand()), grouped(PduPlates.demand())},
                            D + "status.gen", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "reserve_low", D + "status.reserve_low", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "export_held", D + "status.export_held", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "upstream_off", D + "status.upstream_off", Symptom.NO_SUPPLY),
                    new Word(Mark.FAULT, G + "locked_off_upstream", D + "status.locked_off_upstream",
                            Symptom.NO_SUPPLY),

                    new Word(Mark.FAULT, G + "stray_controller", () -> new Object[]{PduLegRow.name(2)},
                            D + "status.stray_controller", Symptom.READS_ZERO),
                    new Word(Mark.FAULT, G + "stray_stud", () -> new Object[]{PduLegRow.name(2)},
                            D + "status.stray_stud", Symptom.READS_ZERO),
                    new Word(Mark.FAULT, G + "new_age", D + "status.new_age", Symptom.NO_SUPPLY))))

            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Status(D + "part.machines", List.of(
                    reason(Mark.RUNNING, PduReason.RUNNING, "running", null),
                    reason(Mark.RUNNING, PduReason.LOCKED_ON, "locked_on", null),
                    reason(Mark.IDLE, PduReason.LOCKED_OFF, "locked_off", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.OVER_BUDGET, "over_budget", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.OVER_CAPACITY, "over_capacity", Symptom.SHED_OR_OFF),
                    reason(Mark.FAULT, PduReason.BREAKER_OPEN, "breaker_open", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.BELOW_FLOOR, "below_floor", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.TIER_SHED, "tier_shed", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.AWAITING_SUPPLY, "awaiting_supply", Symptom.SHED_OR_OFF),
                    reason(Mark.IDLE, PduReason.STAGED, "staged", Symptom.SHED_OR_OFF),
                    reason(Mark.IDLE, PduReason.AWAITING_DWELL, "awaiting_dwell", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.CORE_RESERVE, "core_reserve", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.SUPPLY_FALLING, "supply_falling", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.HEADROOM, "headroom", Symptom.SHED_OR_OFF),
                    reason(Mark.CAUTION, PduReason.WIRE_SHORT, "wire_short", Symptom.RUNS_THEN_STOPS),
                    reason(Mark.RUNNING, PduReason.STORAGE_CHARGING, "storage_charging", null),
                    reason(Mark.RUNNING, PduReason.STORAGE_DISCHARGING, "storage_discharging", null),
                    reason(Mark.RUNNING, PduReason.STORAGE_FULL, "storage_full", null),
                    reason(Mark.IDLE, PduReason.STORAGE_IDLE, "storage_idle", Symptom.READS_ZERO))))
            .part(new Faults(Symptom.SHED_OR_OFF, D + "fault.symptom", List.of(

                    new Step(D + "fault.off_by_you", D + "fault.switched_off", D + "fault.switched_off.note"),

                    new Step(D + "fault.breaker", D + "fault.leg_open", D + "fault.leg_open.note"),

                    new Step(D + "fault.wire_full", D + "fault.leg_limit", D + "fault.leg_limit.note"),

                    new Step(D + "fault.below_tier", D + "fault.buffer_first", D + "fault.buffer_first.note")),
                    P + "fault.status_word", D + "fault.status_word.note", false))

            .part(new Faults(Symptom.RUNS_THEN_STOPS, D + "fault.runs.symptom", List.of(
                    new Step(D + "fault.runs.from_buffer", D + "fault.runs.draining", D + "fault.runs.draining.note"),
                    new Step(D + "fault.runs.rail", D + "fault.runs.cuts_in", D + "fault.runs.cuts_in.note"),
                    new Step(D + "fault.runs.night", D + "fault.runs.short_night", D + "fault.runs.short_night.note")),
                    P + "fault.status_word", D + "fault.runs.end.note", false))

            .part(new Steps(D + "part.auto", List.of(
                    new Instruction(D + "auto.locks", null),
                    new Instruction(D + "auto.order", null),
                    new Instruction(D + "auto.budgets", "create_stats:kinetic_dynamo"),
                    new Instruction(D + "auto.walk", null),
                    new Instruction(D + "auto.restart", null),
                    new Instruction(D + "auto.critical", null),
                    new Instruction(D + "auto.timers", null),
                    new Instruction(D + "auto.manual", null))))
            .part(new Ratings(D + "part.auto_settings", List.of(

                    new Rating(D + "rating.floor", D + "value.floor", () -> new Object[]{Configs.pduFloorPct()}),

                    new Rating(D + "rating.spare", D + "value.spare", () -> new Object[]{Configs.pduRestoreSocPct()}),

                    new Rating(D + "rating.margin", D + "value.margin",
                            () -> new Object[]{Configs.pduBurstMarginPct()}),

                    new Rating(D + "rating.ride", D + "value.ride",
                            () -> new Object[]{seconds(Configs.pduRideHorizonTicks())}),

                    new Rating(D + "rating.dwell", P + "value.seconds",
                            () -> new Object[]{seconds(Configs.pduDwellTicks())}),

                    new Rating(D + "rating.restarts", D + "value.restarts",
                            () -> new Object[]{seconds(Configs.pduRestoreIntervalTicks())}),

                    new Rating(D + "rating.last_tank", D + "value.last_tank",
                            () -> new Object[]{Configs.pduCoreProtection() ? Configs.pduCoreProtectionPct() : 0}))))

            .part(new Note(NoteKind.CAUTION, D + "note.critical"))

            .part(new Part.Break(P + "kind.screen_view", viewName(0), true))
            .part(new Figure(PduPlates.OVERVIEW, P + "part.the_screen", D + "plate.caption", view(0)))
            .part(new Controls(PduPlates.OVERVIEW, List.of(
                    rail(),
                    plate("overview", "mode", D + "keys.mode"),
                    plate("overview", "assign", D + "keys.assign"),
                    plate("overview", "supply", null),
                    plate("overview", "demand", null),
                    plate("overview", "buffer", null),
                    plate("overview", "gauge", null),
                    plate("overview", "balance", null),
                    plate("overview", "status", null),
                    plate("overview", "roll", P + "keys.click"),
                    plate("overview", "footer", null))))
            .part(new Part.Break(P + "kind.screen_view", viewName(1), true))
            .part(new Figure(PduPlates.LOADS, P + "part.the_screen", D + "plate.caption", view(1)))
            .part(new Controls(PduPlates.LOADS, List.of(
                    rail(),
                    plate("loads", "search", D + "keys.search"),
                    plate("loads", "group", P + "keys.click"),
                    plate("loads", "shed", P + "keys.click"),
                    plate("loads", "grouprow", P + "keys.click"),
                    plate("loads", "row", D + "keys.select"),
                    plate("loads", "tier", D + "keys.cycle"),
                    plate("loads", "pip", null),
                    plate("loads", "drawcol", null),
                    plate("loads", "inspector", null),
                    plate("loads", "inspector_tier", P + "keys.click"),
                    plate("loads", "locate", P + "keys.click"))))
            .part(new Part.Break(P + "kind.screen_view", viewName(2), true))
            .part(new Figure(PduPlates.LIGHTING, P + "part.the_screen", D + "plate.caption", view(2)))
            .part(new Controls(PduPlates.LIGHTING, List.of(
                    rail(),
                    plate("lighting", "count", null),
                    plate("lighting", "setall", P + "keys.click"),
                    plate("lighting", "bulk", P + "keys.click"),
                    plate("lighting", "bulk_lock", P + "keys.click"),
                    plate("lighting", "bulk_clear", P + "keys.click"),
                    plate("lighting", "heads", P + "keys.click"),
                    plate("lighting", "state", null),
                    plate("lighting", "lock", D + "keys.lock"))))
            .part(new Part.Break(P + "kind.screen_view", viewName(3), true))
            .part(new Figure(PduPlates.WIRING, P + "part.the_screen", D + "plate.caption", view(3)))
            .part(new Controls(PduPlates.WIRING, List.of(
                    rail(),
                    plate("wiring", "pdu", null),
                    plate("wiring", "switch", null),
                    plate("wiring", "device", P + "keys.click"),
                    plate("wiring", "shed_device", null),
                    plate("wiring", "locked_on", null),
                    plate("wiring", "locked_off", null),
                    plate("wiring", "card", null))))

            .part(new Part.Break(P + "kind.screen_view", viewName(4), true))
            .part(new Figure(PduPlates.FLOW, P + "part.the_screen", D + "plate.caption", view(4)))
            .part(new Controls(PduPlates.FLOW, List.of(
                    rail(),
                    plate("flow", "scene", D + "keys.scene"),
                    plate("flow", "hint", null),
                    plate("flow", "tally", null),
                    plate("flow", "working", null),
                    plate("flow", "supply", null),
                    plate("flow", "budget", null),
                    plate("flow", "demand", null))))

            .part(new Part.Break(P + "kind.screen_view", viewName(5), true))
            .part(new Figure(PduPlates.LEGS, P + "part.the_screen", D + "plate.caption", view(5)))
            .part(new Controls(PduPlates.LEGS, List.of(
                    rail(),
                    plate("legs", "row", P + "keys.click"),
                    plate("legs", "tab", null),
                    plate("legs", "state", null),
                    plate("legs", "rank", P + "keys.click"),
                    plate("legs", "breaker", P + "keys.click"),
                    plate("legs", "unwired", null),
                    plate("legs", "back", null))))

            .part(new Part.Break(P + "kind.screen_view", viewName(6), true))
            .part(new Figure(PduPlates.HISTORY, P + "part.the_screen", D + "plate.history_caption", view(6)))
            .part(new Controls(PduPlates.HISTORY, List.of(
                    rail(),
                    plate("history", "supply", null),
                    plate("history", "demand", null),
                    plate("history", "ranges", D + "keys.ranges"),
                    plate("history", "day", D + "keys.ranges"),
                    plate("history", "plot", P + "keys.point"),
                    plate("history", "scale", null),
                    plate("history", "rail", null))))

            .part(new Part.Break(P + "kind.screen_view", viewName(7), true))
            .part(new Figure(PduPlates.IN_OUT, P + "part.the_screen", D + "plate.caption", view(7)))
            .part(new Controls(PduPlates.IN_OUT, List.of(
                    rail(),
                    plate("in_out", "toggle", P + "keys.click"),
                    plate("in_out", "source", P + "keys.point"),
                    plate("in_out", "from_buffer", P + "keys.point"),
                    plate("in_out", "bus", P + "keys.point"),
                    plate("in_out", "sink", P + "keys.point"),
                    plate("in_out", "others", P + "keys.point"),
                    plate("in_out", "shed", P + "keys.point"),
                    plate("in_out", "ladder_supply", P + "keys.point"),
                    plate("in_out", "ladder_demand", P + "keys.point"),
                    plate("in_out", "admit", P + "keys.point"),
                    plate("in_out", "run", P + "keys.point"),
                    plate("in_out", "critical", P + "keys.point"))))

            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Status(D + "part.flow", List.of(
                    new Word(Mark.RUNNING, G + "working.running", D + "flow.status.running", null),
                    new Word(Mark.CAUTION, G + "working.supply", D + "flow.status.supply", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "working.wiring", D + "flow.status.wiring", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "working.refilling", D + "flow.status.refilling", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "working.protecting", D + "flow.status.protecting",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "working.over_budget", D + "flow.status.over_budget",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.FAULT, G + "working.unwired", D + "flow.status.unwired", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "working.no_devices", D + "flow.status.no_devices", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "working.not_auto", D + "flow.status.not_auto", Symptom.READS_ZERO))))
            .part(new Status(D + "part.legs", List.of(
                    legState(Mark.RUNNING, PduLegRow.State.LIVE, "live", null),
                    legState(Mark.FAULT, PduLegRow.State.AT_CAPACITY, "at_capacity", Symptom.SHED_OR_OFF),
                    legState(Mark.FAULT, PduLegRow.State.TRIPPED, "tripped", Symptom.SHED_OR_OFF),
                    legState(Mark.IDLE, PduLegRow.State.OPEN, "open", Symptom.SHED_OR_OFF),
                    legState(Mark.IDLE, PduLegRow.State.UNWIRED, "unwired", Symptom.READS_ZERO),

                    new Word(Mark.CAUTION, P + "value.figure", () -> new Object[]{PduLegRow.detail(
                            PduLegRow.State.LIVE, 0, 0, 0, false, true, true)}, D + "legs.status.board_held",
                            Symptom.SHED_OR_OFF))))
            .part(new Status(D + "part.history", List.of(
                    new Word(Mark.RUNNING, G + "history.covered", D + "history.status.covered", null),
                    new Word(Mark.RUNNING, G + "history.no_critical", D + "history.status.no_critical", null),
                    new Word(Mark.CAUTION, D + "word.rail_mark", D + "history.status.rail_mark", Symptom.SHED_OR_OFF),

                    new Word(Mark.CAUTION, G + "history.short", () -> new Object[]{FIGURE, ""},
                            D + "history.status.short", Symptom.RUNS_THEN_STOPS),

                    new Word(Mark.IDLE, P + "value.figure", () -> new Object[]{PduPlates.dayOutlook().summary(false)},
                            D + "history.status.learning", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "history.gathering", D + "history.status.gathering",
                            Symptom.READS_ZERO))))
            .part(new Status(D + "part.in_out", List.of(
                    new Word(Mark.RUNNING, G + "inout.spare", D + "in_out.status.spare", null),
                    new Word(Mark.RUNNING, G + "inout.board_upstream", D + "in_out.status.board_upstream", null),
                    river(Mark.CAUTION, com.createstats.gui.pdu.PduFlowModel.Kind.FROM_BUFFER, "from_buffer",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.CAUTION, G + "inout.shed", () -> new Object[]{grouped(PduPlates.shedFe())},
                            D + "in_out.status.shed", Symptom.SHED_OR_OFF),

                    new Word(Mark.CAUTION, P + "value.figure", () -> new Object[]{PduPlates.verdict()},
                            D + "in_out.status.verdict", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "inout.legs_limit", D + "in_out.status.legs_limit",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "inout.night", () -> new Object[]{FIGURE}, D + "in_out.status.night",
                            Symptom.RUNS_THEN_STOPS),
                    river(Mark.IDLE, com.createstats.gui.pdu.PduFlowModel.Kind.UNPRICED, "unpriced",
                            Symptom.WRONG_NUMBER),
                    river(Mark.IDLE, com.createstats.gui.pdu.PduFlowModel.Kind.SOURCE_BUFFERS, "source_buffers",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.IDLE, G + "inout.holding", () -> new Object[]{FIGURE}, D + "in_out.status.holding",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.IDLE, G + "inout.quiet", D + "in_out.status.quiet", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "inout.not_auto", D + "in_out.status.not_auto", Symptom.READS_ZERO))))
            .build();

    public static List<Sheet> all() {
        return new ArrayList<>(List.of(PDU));
    }
}
