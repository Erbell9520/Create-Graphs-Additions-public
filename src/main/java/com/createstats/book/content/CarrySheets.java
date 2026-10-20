package com.createstats.book.content;

import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.blockentity.WallOutletBE;
import com.createstats.book.content.Part.Application;
import com.createstats.book.content.Part.Cell;
import com.createstats.book.content.Part.Control;
import com.createstats.book.content.Part.Controls;
import com.createstats.book.content.Part.Faults;
import com.createstats.book.content.Part.Figure;
import com.createstats.book.content.Part.Gesture;
import com.createstats.book.content.Part.Grade;
import com.createstats.book.content.Part.Grades;
import com.createstats.book.content.Part.Link;
import com.createstats.book.content.Part.Note;
import com.createstats.book.content.Part.NoteKind;
import com.createstats.book.content.Part.Rating;
import com.createstats.book.content.Part.Ratings;
import com.createstats.book.content.Part.SeeAlso;
import com.createstats.book.content.Part.Status;
import com.createstats.book.content.Part.Step;
import com.createstats.book.content.Part.Word;
import com.createstats.book.content.Part.World;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.scene.FigureWorld;
import com.createstats.book.specimen.CarryFixtures;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.power.CablePlacement;
import com.createstats.power.CableRun;
import com.createstats.power.CableTier;
import com.createstats.power.CordRoute;
import com.createstats.power.DiagnosticJunctionStuds;
import com.createstats.power.FeRates;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IndoorCable;
import com.createstats.power.LimiterLadder;
import com.createstats.power.WireTier;
import net.minecraft.SharedConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CarrySheets {
    private CarrySheets() {
    }

    private static final String CARRY = "create_stats.book.group.carry";
    private static final String P = "create_stats.book.";

    private static final String G = P + "goggle.";

    private static String seconds(int ticks) {
        return Num.of(ticks / (double) SharedConstants.TICKS_PER_SECOND);
    }

    private static String factor(double f) {
        return String.format(Locale.ROOT, "%.2f", f);
    }

    static int lengthsOf(BlockFigure figure, String block) {
        int n = 0;
        for (final BlockFigure.Placement p : figure.placements()) {
            if (p.block().equals(block)) {
                n++;
            }
        }
        return n;
    }

    static String plural(int n) {
        return n == 1 ? "" : "s";
    }

    static int runLengths() {
        int n = 0;
        for (final String block : FigureWorld.CABLES.keySet()) {
            n += lengthsOf(BlockFigures.CABLE_RUN, block);
        }
        return n;
    }

    private static Grade cableRow(String id, CableTier tier) {
        return new Grade(id, null, List.of(
                new Cell(P + "value.times", () -> new Object[]{factor(tier.multiplier())}),
                new Cell(P + "value.px", () -> new Object[]{Num.of(tier.halfWidthPx() * 2.0)}),
                new Cell(P + "value.figure", () -> new Object[]{FeRates.perTick(FeRates.cable(tier))})));
    }

    public static final Sheet CABLES = Sheet.of("surface_cable", Section.POWER)
            .group(CARRY)
            .title(P + "cables.title")
            .covers("create_stats:surface_cable", "create_stats:cable_tray", "create_stats:bus_duct",
                    "create_stats:busbar_trunk")
            .hook(P + "cables.hook")
            .part(new Figure(BlockFigures.CABLE_RUN.id(), P + "part.parts_of_a_run", P + "cables.caption"))

            .part(new Grades(P + "part.grades", P + "cables.grades.note",
                    () -> new Object[]{FeRates.perTick(FeRates.base())},
                    List.of(new Cell(P + "column.scale", null), new Cell(P + "column.wide", null),
                            new Cell(P + "column.carries", null)),
                    List.of(cableRow("create_stats:surface_cable", CableTier.SURFACE),
                            cableRow("create_stats:cable_tray", CableTier.TRAY),
                            cableRow("create_stats:bus_duct", CableTier.DUCT),
                            cableRow("create_stats:busbar_trunk", CableTier.TRUNK))))
            .part(new Ratings(List.of(

                    new Rating(P + "cables.rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(CableBE.SEGMENT_BUFFER)}),

                    new Rating(P + "cables.rating.one_click", P + "value.blocks",
                            () -> new Object[]{Num.of(CablePlacement.MAX_RUN)}),

                    new Rating(P + "cables.rating.pull_up", P + "value.blocks",
                            () -> new Object[]{Num.of(CableRun.MAX_RUN_BLOCKS)}))))
            .part(new Application(List.of(
                    new Link("create_stats:kinetic_dynamo", P + "chain.supply", false),
                    new Link("create_stats:bus_duct", P + "chain.trunk", false),
                    new Link("create_stats:surface_cable", P + "chain.spur", true),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),

                    P + "cables.application"))
            .part(new SeeAlso(List.of("indoor_cable", "power_connector", "cable_diagnostic_junction", "power_meter",
                    "power_limiter")))
            .part(new Part.Ponder("create_stats:surface_cable"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(BlockFigures.CABLE_RUN.id(), P + "part.part_designations", List.of(

                    new Control("feed", P + "cables.control.feed", P + "cables.function.feed", null, null),

                    new Control("joint", P + "cables.control.joint", P + "cables.function.joint", null, null),

                    new Control("reducer", P + "cables.control.reducer", P + "cables.function.reducer", null, null),

                    new Control("tracer", P + "cables.control.tracer", P + "cables.function.tracer",
                            P + "keys.dye", null),

                    new Control("elbow", P + "cables.control.elbow", P + "cables.function.elbow", null, null),

                    new Control("lands", P + "cables.control.lands", P + "cables.function.lands", null, null))))
            .part(new World(List.of(

                    new Gesture(P + "cables.gesture.start", P + "cables.gesture.start.does"),
                    new Gesture(P + "cables.gesture.again", P + "cables.gesture.again.does"),

                    new Gesture(P + "cables.gesture.sneak_laying", P + "cables.gesture.sneak_laying.does"),
                    new Gesture(P + "cables.gesture.rip_up", P + "cables.gesture.rip_up.does"),
                    new Gesture(P + "gesture.dye", P + "cables.gesture.dye"),

                    new Gesture(P + "gesture.cable_bore", P + "cables.gesture.bore"),
                    new Gesture(P + "gesture.goggles", P + "cables.gesture.goggles"))))

            .part(new Status(List.of(

                    new Word(Mark.RUNNING, G + "carrying", () -> new Object[]{Num.of(StoreFixtures.dynamoFlow())},
                            P + "cables.status.carrying", null),
                    new Word(Mark.RUNNING, G + "capacity", () -> new Object[]{Num.of(FeRates.cable(CableTier.SURFACE))},
                            P + "cables.status.capacity", null),
                    new Word(Mark.IDLE, G + "idle", P + "cables.status.idle", Symptom.READS_ZERO),

                    new Word(Mark.CAUTION, G + "carrying", () -> new Object[]{Num.of(FeRates.cable(CableTier.SURFACE))},
                            P + "cables.status.at_rating", Symptom.WRONG_NUMBER),

                    new Word(Mark.CAUTION, G + "cable.throttled", () -> new Object[]{
                            lengthsOf(BlockFigures.CABLE_RUN, "create_stats:bus_duct"),
                            Num.of(FeRates.cable(CableTier.SURFACE))}, P + "cables.status.throttled",
                            Symptom.WRONG_NUMBER),

                    new Word(Mark.CAUTION, G + "cable.held", () -> new Object[]{
                            Num.of(LimiterLadder.feAt(LimiterLadder.DEFAULT_NOTCH))}, P + "cables.status.held",
                            Symptom.WRONG_NUMBER),

                    new Word(Mark.RUNNING, P + "cables.look.band", P + "cables.status.band", null),

                    new Word(Mark.CAUTION, P + "cables.look.amber", P + "cables.status.amber", Symptom.WRONG_NUMBER))))

            .part(new Status(P + "cables.part.laying", List.of(
                    new Word(Mark.RUNNING, "message.create_stats.cable.anchored", P + "cables.status.anchored", null),
                    new Word(Mark.RUNNING, "message.create_stats.cable.run", () -> new Object[]{
                            lengthsOf(BlockFigures.CABLE_RUN, "create_stats:surface_cable")},
                            P + "cables.status.laid", null),
                    new Word(Mark.RUNNING, "message.create_stats.cable.connected", P + "cables.status.connected", null),
                    new Word(Mark.FAULT, "message.create_stats.cable.no_route", P + "cables.status.no_route",
                            Symptom.WILL_NOT_START),

                    new Word(Mark.CAUTION, "message.create_stats.cable.short", () -> new Object[]{
                            lengthsOf(BlockFigures.CABLE_RUN, "create_stats:surface_cable"),
                            lengthsOf(BlockFigures.CABLE_RUN, "create_stats:surface_cable") - 1},
                            P + "cables.status.short", Symptom.WILL_NOT_START),
                    new Word(Mark.RUNNING, "message.create_stats.cable.ripped", () -> new Object[]{runLengths()},
                            P + "cables.status.ripped", null))))
            .part(new Faults(Symptom.READS_ZERO, P + "cables.fault.symptom", List.of(

                    new Step(P + "cables.fault.beside_it", P + "cables.fault.wrong_face",
                            P + "cables.fault.wrong_face.note"),

                    new Step(P + "cables.fault.colour_change", P + "cables.fault.two_circuits",
                            P + "cables.fault.two_circuits.note"),
                    new Step(P + "cables.fault.idle_all_along", P + "cables.fault.no_supply",
                            P + "cables.fault.no_supply.note")),
                    P + "fault.status_word", P + "cables.fault.status_word.note", false))

            .part(new Figure(BlockFigures.CABLE_GRADES.id(), P + "cables.portrait", P + "cables.portrait.caption"))

            .part(new Note(NoteKind.CAUTION, P + "cables.caution"))

            .part(new Note(NoteKind.NOTE, P + "cables.note.config"))
            .build();

    static int indoorLengths() {
        return lengthsOf(BlockFigures.INDOOR_RUN, FigureWorld.INDOOR);
    }

    static int indoorBores() {
        int n = 0;
        for (final BlockFigure.Tie t : BlockFigures.INDOOR_RUN.ties()) {
            if (t.kind() == BlockFigure.Tie.Kind.BORE) {
                n++;
            }
        }
        return n;
    }

    static int boredThrough() {
        int n = 0;
        for (final BlockFigure.Tie t : BlockFigures.INDOOR_RUN.ties()) {
            if (t.kind() == BlockFigure.Tie.Kind.BORE) {
                n += Math.abs(t.to()[0] - t.from()[0]) + Math.abs(t.to()[1] - t.from()[1])
                        + Math.abs(t.to()[2] - t.from()[2]) - 1;
            }
        }
        return n;
    }

    public static final Sheet INDOOR = Sheet.of("indoor_cable", Section.POWER)
            .group(CARRY)
            .title("block.create_stats.indoor_cable")
            .covers("create_stats:indoor_cable", "create_stats:cable_bore")
            .hook(P + "indoor.hook")
            .part(new Figure(BlockFigures.INDOOR_RUN.id(), P + "part.parts_of_a_run", P + "indoor.caption"))
            .part(new Ratings(List.of(

                    new Rating(P + "indoor.rating.carries", P + "indoor.value.carries",
                            () -> new Object[]{FeRates.perTick(FeRates.indoorCable())}),

                    new Rating(P + "indoor.rating.scale", P + "indoor.value.scale", () -> new Object[]{
                            String.format(Locale.ROOT, "%.3f", IndoorCable.MULTIPLIER)}),
                    new Rating(P + "rating.buffer", P + "indoor.value.buffer",
                            () -> new Object[]{FeRates.amount(IndoorCable.SEGMENT_BUFFER)}),

                    new Rating(P + "indoor.rating.bore", P + "indoor.value.bore",
                            () -> new Object[]{Num.of(IndoorCable.MAX_BORE)}))))
            .part(new Application(List.of(
                    new Link("create_stats:bus_duct", P + "chain.trunk", false),
                    new Link("create_stats:indoor_cable", P + "chain.spur", true),
                    new Link("create_stats:cable_bore", P + "chain.bore", false),
                    new Link("create_stats:water_cooler", P + "chain.load", false)),

                    P + "indoor.application"))
            .part(new SeeAlso(List.of("surface_cable", "power_outlet", "cable_diagnostic_junction")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(BlockFigures.INDOOR_RUN.id(), P + "part.part_designations", List.of(

                    new Control("gland", P + "indoor.control.gland", P + "indoor.function.gland", null, null),

                    new Control("clipped", P + "indoor.control.clipped", P + "indoor.function.clipped",
                            P + "keys.right_click_empty", null),

                    new Control("free", P + "indoor.control.free", P + "indoor.function.free", null, null),

                    new Control("collar", P + "indoor.control.collar", P + "indoor.function.collar", null, null))))
            .part(new World(List.of(
                    new Gesture(P + "indoor.gesture.place", P + "indoor.gesture.place.does"),
                    new Gesture(P + "indoor.gesture.empty_hand", P + "indoor.gesture.empty_hand.does"),
                    new Gesture(P + "gesture.sneak_empty_hand", P + "indoor.gesture.sneak.does"),
                    new Gesture(P + "gesture.dye", P + "indoor.gesture.dye"),
                    new Gesture(P + "gesture.cable_bore", P + "indoor.gesture.bore"),
                    new Gesture(P + "indoor.gesture.pull", P + "indoor.gesture.pull.does"),
                    new Gesture(P + "gesture.goggles", P + "indoor.gesture.goggles"))))

            .part(new Status(List.of(
                    new Word(Mark.RUNNING, G + "carrying", () -> new Object[]{Num.of(StoreFixtures.dynamoFlow())},
                            P + "indoor.status.carrying", null),
                    new Word(Mark.RUNNING, G + "capacity", () -> new Object[]{Num.of(FeRates.indoorCable())},
                            P + "indoor.status.capacity", null),
                    new Word(Mark.IDLE, G + "idle", P + "indoor.status.idle", Symptom.READS_ZERO),

                    new Word(Mark.RUNNING, G + "indoor_cable.bores", () -> new Object[]{indoorLengths(), indoorBores(),
                            plural(indoorBores())}, P + "indoor.status.bores", null),
                    new Word(Mark.RUNNING, "message.create_stats.indoor_cable.run_clipped", P + "indoor.status.clipped",
                            null),
                    new Word(Mark.RUNNING, "message.create_stats.indoor_cable.run_centred", P + "indoor.status.centred",
                            null),

                    new Word(Mark.RUNNING, P + "indoor.look.bead", P + "indoor.status.bead", null),
                    new Word(Mark.CAUTION, P + "indoor.look.amber", P + "indoor.status.amber", Symptom.WRONG_NUMBER))))

            .part(new Status(P + "indoor.part.bore", List.of(
                    new Word(Mark.RUNNING, G + "cable_bore.bored", () -> new Object[]{boredThrough(),
                            plural(boredThrough())}, P + "indoor.status.bored", null),
                    new Word(Mark.RUNNING, G + "cable_bore.pulled", P + "indoor.status.pulled", null),
                    new Word(Mark.FAULT, G + "cable_bore.aim", P + "indoor.status.aim", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "cable_bore.no_partner", () -> new Object[]{IndoorCable.MAX_BORE},
                            P + "indoor.status.no_partner", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "cable_bore.nothing", P + "indoor.status.nothing", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "cable_bore.already", P + "indoor.status.already", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "cable_bore.no_bore", P + "indoor.status.no_bore",
                            Symptom.WILL_NOT_START))))
            .part(new Faults(Symptom.READS_ZERO, P + "indoor.fault.symptom", List.of(

                    new Step(P + "indoor.fault.no_gland", P + "indoor.fault.not_bored", P + "indoor.fault.not_bored.note"),

                    new Step(P + "indoor.fault.wall_gone", P + "indoor.fault.bore_dropped",
                            P + "indoor.fault.bore_dropped.note"),

                    new Step(P + "indoor.fault.idle_both_sides", P + "indoor.fault.no_supply",
                            P + "indoor.fault.no_supply.note")),
                    P + "fault.status_word", P + "indoor.fault.status_word.note", false))

            .part(new Note(NoteKind.NOTE, P + "indoor.note.spur"))

            .part(new Note(NoteKind.NOTE, P + "indoor.note.through"))
            .build();

    private static Word code(Mark mark, DiagnosticJunctionBE.Fault fault, String meaningKey, Symptom symptom) {
        return new Word(mark, P + "junctions.code", () -> new Object[]{fault.code(),
                DiagnosticJunctionBE.channelStatus(fault)}, meaningKey, symptom);
    }

    public static final Sheet JUNCTIONS = Sheet.of("cable_diagnostic_junction", Section.POWER)
            .group(CARRY)
            .title(P + "junctions.title")
            .covers("create_stats:cable_diagnostic_junction", "create_stats:wire_terminal_junction")
            .hook(P + "junctions.hook")
            .part(new Figure(BlockFigures.JUNCTIONS.id(), P + "part.parts_and_faces", P + "junctions.caption"))
            .part(new Ratings(List.of(

                    new Rating(P + "junctions.rating.studs", P + "junctions.value.studs",
                            () -> new Object[]{DiagnosticJunctionStuds.COUNT, DiagnosticJunctionStuds.CHANNELS}),

                    new Rating(P + "junctions.rating.channel", P + "junctions.value.channel", () -> new Object[]{
                            FeRates.perTick(FeWireNetwork.passThroughRate(WireTier.COPPER, WireTier.SHIELDED))}),
                    new Rating(P + "junctions.rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(DiagnosticJunctionBE.JUNCTION_BUFFER)}))))
            .part(new Application(List.of(
                    new Link("create_stats:bus_duct", P + "chain.trunk", false),
                    new Link("create_stats:cable_diagnostic_junction", P + "chain.splice", true),
                    new Link("create_stats:surface_cable", P + "chain.spur", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "junctions.application"))
            .part(new SeeAlso(List.of("surface_cable", "power_connector", "copper_wire", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(BlockFigures.JUNCTIONS.id(), P + "part.part_designations", List.of(

                    new Control("card", P + "junctions.control.card", P + "junctions.function.card", null, null),
                    new Control("screen", P + "junctions.control.screen", P + "junctions.function.screen", null, null),
                    new Control("glands", P + "junctions.control.glands", P + "junctions.function.glands", null, null),
                    new Control("lamps", P + "junctions.control.lamps", P + "junctions.function.lamps", null, null),

                    new Control("lever", P + "junctions.control.lever", P + "junctions.function.lever",
                            P + "keys.right_click", null),
                    new Control("studs", P + "junctions.control.studs", P + "junctions.function.studs",
                            P + "keys.wire_then_terminal", null),

                    new Control("isolator", P + "junctions.control.isolator", P + "junctions.function.isolator",
                            P + "keys.right_click", null))))
            .part(new World(List.of(
                    new Gesture(P + "junctions.gesture.mount", P + "junctions.gesture.mount.does"),
                    new Gesture(P + "junctions.gesture.door", P + "junctions.gesture.door.does"),
                    new Gesture(P + "gesture.wire_on_stud", P + "junctions.gesture.wire"),
                    new Gesture(P + "gesture.breaking", P + "junctions.gesture.drops"),
                    new Gesture(P + "gesture.goggles", P + "junctions.gesture.goggles"))))

            .part(new Status(P + "junctions.part.codes", List.of(
                    code(Mark.RUNNING, DiagnosticJunctionBE.Fault.OK, P + "junctions.status.ok", null),
                    code(Mark.IDLE, DiagnosticJunctionBE.Fault.NO_BUS, P + "junctions.status.no_bus",
                            Symptom.READS_ZERO),
                    code(Mark.CAUTION, DiagnosticJunctionBE.Fault.OPEN_CIRCUIT, P + "junctions.status.open",
                            Symptom.READS_ZERO),
                    code(Mark.CAUTION, DiagnosticJunctionBE.Fault.SOURCE_STARVED, P + "junctions.status.starved",
                            Symptom.NO_SUPPLY),
                    code(Mark.FAULT, DiagnosticJunctionBE.Fault.OVERLOAD, P + "junctions.status.overload",
                            Symptom.WRONG_NUMBER),
                    code(Mark.CAUTION, DiagnosticJunctionBE.Fault.BUFFER_FULL, P + "junctions.status.buffer_full",
                            Symptom.RUNS_THEN_STOPS),
                    code(Mark.FAULT, DiagnosticJunctionBE.Fault.ISOLATED, P + "junctions.status.isolated",
                            Symptom.SHED_OR_OFF))))

            .part(new Status(List.of(
                    new Word(Mark.RUNNING, G + "junction.conducting", P + "junctions.status.conducting", null),
                    new Word(Mark.FAULT, G + "junction.cut_off", P + "junctions.status.cut_off", Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, G + "junction.nothing_passes", P + "junctions.status.nothing_passes",
                            Symptom.SHED_OR_OFF),

                    new Word(Mark.RUNNING, G + "junction.flow", () -> new Object[]{
                            Num.of(CarryFixtures.cableBox().flow()), Num.of(CarryFixtures.cableBox().capacity())},
                            P + "junctions.status.flow", null),
                    new Word(Mark.RUNNING, G + "junction.separate", P + "junctions.status.separate", null),
                    new Word(Mark.CAUTION, G + "junction.merged", P + "junctions.status.merged", Symptom.WRONG_NUMBER),
                    new Word(Mark.RUNNING, P + "junctions.lens.green", P + "junctions.status.lens_green", null),
                    new Word(Mark.CAUTION, P + "junctions.lens.amber", P + "junctions.status.lens_amber",
                            Symptom.WRONG_NUMBER))))
            .part(new Faults(Symptom.READS_ZERO, P + "junctions.fault.symptom", List.of(
                    new Step(P + "junctions.fault.handle_out", P + "junctions.fault.isolated",
                            P + "junctions.fault.isolated.note"),

                    new Step(P + "junctions.fault.one_run", P + "junctions.fault.stub", P + "junctions.fault.stub.note"),

                    new Step(P + "junctions.fault.one_stud", P + "junctions.fault.channel_open",
                            P + "junctions.fault.channel_open.note")),
                    P + "junctions.fault.code", P + "junctions.fault.code.note", false))

            .part(new Note(NoteKind.NOTE, P + "junctions.note.capacity"))

            .part(new Note(NoteKind.NOTE, P + "junctions.note.card", () -> new Object[]{
                    DiagnosticJunctionBE.Fault.ROUTE_BLOCKED.code(), DiagnosticJunctionBE.Fault.BACKFEED.code(),
                    DiagnosticJunctionBE.Fault.UNSTABLE.code()}))
            .build();

    public static final Sheet OUTLETS = Sheet.of("power_outlet", Section.POWER)
            .group(CARRY)
            .title(P + "outlets.title")
            .covers("create_stats:power_outlet", "create_stats:duplex_power_outlet", "create_stats:appliance_cord")
            .hook(P + "outlets.hook")
            .part(new Figure(BlockFigures.OUTLET.id(), P + "part.parts_of_a_run", P + "outlets.caption"))
            .part(new Ratings(List.of(

                    new Rating(P + "outlets.rating.passes", P + "outlets.value.passes",
                            () -> new Object[]{FeRates.perTick(WallOutletBE.RATED_FE_PER_TICK)}),

                    new Rating(P + "outlets.rating.reach", P + "value.blocks",
                            () -> new Object[]{Num.of(CordRoute.MAX_REACH)}),

                    new Rating(P + "rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(Configs.outletBuffer())}),

                    new Rating(P + "outlets.rating.review", P + "outlets.value.review",
                            () -> new Object[]{seconds(WallOutletBE.CORD_REVIEW_TICKS)}))))
            .part(new Application(List.of(
                    new Link("create_stats:indoor_cable", P + "chain.behind_wall", false),
                    new Link("create_stats:cable_bore", P + "chain.bore", false),
                    new Link("create_stats:power_outlet", P + "chain.socket", true),
                    new Link("create_stats:appliance_cord", P + "chain.cord", false),
                    new Link("create_stats:bread_slicer", P + "chain.appliance", false)),
                    P + "outlets.application"))
            .part(new SeeAlso(List.of("indoor_cable", "surface_cable", "cable_diagnostic_junction")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(BlockFigures.OUTLET.id(), P + "part.part_designations", List.of(

                    new Control("feed", P + "outlets.control.feed", P + "outlets.function.feed", null, null),

                    new Control("lamp", P + "outlets.control.lamp", P + "outlets.function.lamp", null, null),
                    new Control("plug", P + "outlets.control.plug", P + "outlets.function.plug",
                            P + "keys.cord_then_machine", null),

                    new Control("cord", P + "outlets.control.cord", P + "outlets.function.cord", null, null),

                    new Control("relief", P + "outlets.control.relief", P + "outlets.function.relief", null,
                            null))))
            .part(new World(List.of(
                    new Gesture(P + "outlets.gesture.mount", P + "outlets.gesture.mount.does"),
                    new Gesture(P + "gesture.cable_bore", P + "outlets.gesture.bore"),
                    new Gesture(P + "outlets.gesture.plug", P + "outlets.gesture.plug.does"),
                    new Gesture(P + "outlets.gesture.unplug", P + "outlets.gesture.unplug.does"),
                    new Gesture(P + "outlets.gesture.beside", P + "outlets.gesture.beside.does"),
                    new Gesture(P + "outlets.gesture.link", P + "outlets.gesture.link.does"),
                    new Gesture(P + "gesture.goggles", P + "outlets.gesture.goggles"))))

            .part(new Status(List.of(
                    new Word(Mark.RUNNING, P + "outlets.lamp.green", P + "outlets.status.green", null),
                    new Word(Mark.CAUTION, P + "outlets.lamp.amber", P + "outlets.status.amber", Symptom.WRONG_NUMBER),
                    new Word(Mark.IDLE, P + "outlets.lamp.grey", P + "outlets.status.grey", Symptom.NO_SUPPLY),
                    new Word(Mark.RUNNING, G + "outlet.feeding", P + "outlets.status.feeding", null),
                    new Word(Mark.IDLE, G + "outlet.live", P + "outlets.status.live", Symptom.READS_ZERO),
                    new Word(Mark.FAULT, G + "outlet.no_supply", P + "outlets.status.no_supply", Symptom.NO_SUPPLY),
                    new Word(Mark.FAULT, G + "outlet.dead_bore", P + "outlets.status.dead_bore", Symptom.NO_SUPPLY),
                    new Word(Mark.CAUTION, G + "outlet.gated", P + "outlets.status.gated", Symptom.SHED_OR_OFF),
                    new Word(Mark.RUNNING, G + "outlet.paired", P + "outlets.status.paired", null),

                    new Word(Mark.CAUTION, G + "needs", () -> new Object[]{Num.of(WallOutletBE.RATED_FE_PER_TICK),
                            Num.of(0L)}, P + "outlets.status.needs", Symptom.SHED_OR_OFF))))

            .part(new Status(P + "outlets.part.cord", List.of(
                    new Word(Mark.RUNNING, G + "cord.held", P + "outlets.status.held", null),
                    new Word(Mark.RUNNING, G + "cord.plugged", () -> new Object[]{WallOutletBE.RATED_FE_PER_TICK},
                            P + "outlets.status.plugged", null),
                    new Word(Mark.RUNNING, G + "cord.unplugged", P + "outlets.status.unplugged", null),
                    new Word(Mark.FAULT, G + "cord.no_plug", P + "outlets.status.no_plug", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "cord.too_far", P + "outlets.status.too_far", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "cord.no_way", P + "outlets.status.no_way", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "cord.both_full", P + "outlets.status.both_full", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "cord.itself", P + "outlets.status.itself", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "cord.nothing_plugged", P + "outlets.status.nothing_plugged",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "cord.gone", P + "outlets.status.gone", Symptom.WILL_NOT_START))))

            .part(new Note(NoteKind.NOTE, P + "outlets.note.share"))

            .part(new Note(NoteKind.CAUTION, P + "outlets.caution"))
            .part(new Faults(Symptom.READS_ZERO, P + "outlets.fault.symptom", List.of(
                    new Step(P + "outlets.fault.lamp_grey", P + "outlets.fault.no_supply",
                            P + "outlets.fault.no_supply.note"),
                    new Step(P + "outlets.fault.gated", P + "outlets.fault.switched_off",
                            P + "outlets.fault.switched_off.note"),

                    new Step(P + "outlets.fault.getting_nothing", P + "outlets.fault.shed",
                            P + "outlets.fault.shed.note")),
                    P + "fault.status_word", P + "outlets.fault.status_word.note", false))
            .build();

    public static List<Sheet> before() {
        return List.of(CABLES, INDOOR);
    }

    public static List<Sheet> after() {
        return List.of(JUNCTIONS, OUTLETS);
    }

    public static List<Sheet> all() {
        final List<Sheet> out = new ArrayList<>(before());
        out.addAll(after());
        return List.copyOf(out);
    }
}
