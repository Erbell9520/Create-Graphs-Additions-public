package com.createstats.book.content;

import com.createstats.block.ConnectorAnchor;
import com.createstats.book.content.Part.Application;
import com.createstats.book.content.Part.Cell;
import com.createstats.book.content.Part.Control;
import com.createstats.book.content.Part.Controls;
import com.createstats.book.content.Part.Faults;
import com.createstats.book.content.Part.Figure;
import com.createstats.book.content.Part.Gesture;
import com.createstats.book.content.Part.Grade;
import com.createstats.book.content.Part.Grades;
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
import com.createstats.book.scene.WireFigures;
import com.createstats.book.specimen.RingPlates;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.client.wire.WireTensionHold;
import com.createstats.power.CableTier;
import com.createstats.power.ConnectorReadout;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeRates;
import com.createstats.power.LimiterLadder;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import net.minecraft.SharedConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WireSheets {
    private WireSheets() {
    }

    private static final String CARRY = "create_stats.book.group.carry";
    private static final String P = "create_stats.book.";

    private static final String G = P + "goggle.";
    private static final String C = "create_stats:";

    private static String seconds(int ticks) {
        return Num.of(ticks / (double) SharedConstants.TICKS_PER_SECOND);
    }

    static WireTier matchingWire(ConnectorTier tier) {
        for (final WireTier w : WireTier.values()) {
            if (w.multiplier() == tier.multiplier()) {
                return w;
            }
        }
        throw new IllegalStateException("no wire fills a " + tier + " exactly");
    }

    static String wireId(WireTier tier) {
        return switch (tier) {
            case COPPER -> C + "copper_wire";
            case INSULATED -> C + "insulated_wire";
            case SHIELDED -> C + "shielded_wire";
            case HV -> C + "hv_line";
        };
    }

    private static Grade connectorRow(String id, ConnectorTier tier) {
        return new Grade(id, null, List.of(
                new Cell(P + "value.fe_t", () -> new Object[]{Num.of(FeRates.connector(tier))}),
                new Cell(P + "value.count", () -> new Object[]{tier.maxLinks()}),
                new Cell("item." + wireId(matchingWire(tier)).replace(':', '.'), null)));
    }

    static int oneCopperOnAPowerConnector() {
        return FeRates.connectorThroughput(FeRates.base(), ConnectorTier.POWER, List.of(WireTier.COPPER));
    }

    static ConnectorReadout ringHub() {
        final List<WireTier> spans = new ArrayList<>();
        for (final WireTier w : WireTier.values()) {
            spans.add(w);
        }
        return new ConnectorReadout("", ConnectorTier.INDUSTRIAL, FeRates.base(), spans, 0, 0, 0, true,
                FeGridSolver.NO_CEILING, 0, 0, 0, 0);
    }

    static Object[] wiresLine(ConnectorReadout r) {
        final String line = r.wiresLine();
        final int max = r.tier().maxLinks();
        return new Object[]{r.spans().size(), max, max == 1 ? "" : "s", line.substring(line.indexOf(": ") + 2)};
    }

    public static final Sheet CONNECTORS = Sheet.of("power_connector", Section.POWER)
            .group(CARRY)
            .title(P + "connectors.title")
            .covers(C + "power_connector", C + "advanced_power_connector", C + "industrial_power_connector")
            .hook(P + "connectors.hook")
            .part(new Figure(WireFigures.CONNECTOR.id(), P + "part.parts_of_a_connector", P + "connectors.caption"))

            .part(new Grades(P + "part.grades", P + "connectors.grades.note",
                    () -> new Object[]{FeRates.perTick(FeRates.base())},
                    List.of(new Cell(P + "column.rated", null), new Cell(P + "column.wires", null),
                            new Cell(P + "column.filled_by", null)),
                    List.of(connectorRow(C + "power_connector", ConnectorTier.POWER),
                            connectorRow(C + "advanced_power_connector", ConnectorTier.ADVANCED),
                            connectorRow(C + "industrial_power_connector", ConnectorTier.INDUSTRIAL))))
            .part(new Ratings(List.of(

                    new Rating(P + "connectors.rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(Configs.connectorBuffer())}),

                    new Rating(P + "connectors.rating.reach", P + "value.blocks",
                            () -> new Object[]{Num.of(Configs.wireReach())}),

                    new Rating(P + "connectors.rating.tension_reach", P + "value.blocks",
                            () -> new Object[]{Num.of(WireTension.REACH)}),

                    new Rating(P + "connectors.rating.hold", P + "value.seconds",
                            () -> new Object[]{seconds(WireTensionHold.HOLD_TICKS)}),

                    new Rating(P + "connectors.rating.shift", P + "value.px",
                            () -> new Object[]{Num.of(ConnectorAnchor.SHIFT_PX)}))))
            .part(new Application(List.of(
                    new Link(C + "kinetic_dynamo", P + "chain.supply", false),
                    new Link(C + "power_connector", P + "chain.terminal", true),
                    new Link(C + "insulated_wire", P + "chain.wire", false),
                    new Link(C + "power_connector", P + "chain.terminal", false),
                    new Link(C + "induction_smelter", P + "chain.load", false)),
                    P + "connectors.application"))
            .part(new Made(P + "part.made", P + "made.note", List.of("power_connector", "advanced_power_connector",
                    "mechanical_crafting/industrial_power_connector")))
            .part(new SeeAlso(List.of("copper_wire", "surface_cable", "cable_diagnostic_junction", "power_meter",
                    "power_limiter")))
            .part(new Part.Ponder(C + "power_connector"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(WireFigures.CONNECTOR.id(), P + "part.part_designations", List.of(

                    new Control("terminal", P + "connectors.control.terminal", P + "connectors.function.terminal",
                            P + "keys.wire_then_far_end", null),

                    new Control("sheds", P + "connectors.control.sheds", P + "connectors.function.sheds", null, null),

                    new Control("base", P + "connectors.control.base", P + "connectors.function.base", null, null),

                    new Control("block", P + "connectors.control.block", P + "connectors.function.block", null, null),
                    new Control("span", P + "connectors.control.span", P + "connectors.function.span", null,
                            null))))
            .part(new World(List.of(
                    new Gesture(P + "connectors.gesture.place", P + "connectors.gesture.place.does"),
                    new Gesture(P + "gesture.a_wire", P + "connectors.gesture.wire"),

                    new Gesture(P + "gesture.hold_use_empty", P + "connectors.gesture.hold"),

                    new Gesture(P + "gesture.wrench", P + "connectors.gesture.wrench"),

                    new Gesture(P + "gesture.sneak_wire", P + "connectors.gesture.strip"),

                    new Gesture(P + "gesture.breaking", P + "connectors.gesture.breaking"),
                    new Gesture(P + "gesture.goggles", P + "connectors.gesture.goggles"))))

            .part(new Status(List.of(

                    new Word(Mark.RUNNING, G + "carrying", () -> new Object[]{Num.of(StoreFixtures.dynamoFlow())},
                            P + "connectors.status.carrying", null),

                    new Word(Mark.RUNNING, G + "connector.feeding",
                            () -> new Object[]{Num.of(StoreFixtures.dynamoFlow())}, P + "connectors.status.feeding",
                            null),
                    new Word(Mark.RUNNING, G + "connector.drawing",
                            () -> new Object[]{Num.of(StoreFixtures.dynamoFlow())}, P + "connectors.status.drawing",
                            null),
                    new Word(Mark.IDLE, G + "connector.block_idle", P + "connectors.status.block_idle",
                            Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "idle", P + "connectors.status.idle", Symptom.READS_ZERO),

                    new Word(Mark.RUNNING, G + "capacity", () -> new Object[]{Num.of(oneCopperOnAPowerConnector())},
                            P + "connectors.status.capacity", null),

                    new Word(Mark.CAUTION, G + "carrying", () -> new Object[]{Num.of(oneCopperOnAPowerConnector())},
                            P + "connectors.status.at_rating", Symptom.WRONG_NUMBER),

                    new Word(Mark.CAUTION, G + "connector.held",
                            () -> new Object[]{Num.of(LimiterLadder.feAt(LimiterLadder.DEFAULT_NOTCH))},
                            P + "connectors.status.held", Symptom.WRONG_NUMBER),

                    new Word(Mark.RUNNING, G + "connector.wires", () -> wiresLine(ringHub()),
                            P + "connectors.status.wires", null),
                    new Word(Mark.RUNNING, G + "connector.on_cable", P + "connectors.status.on_cable", null),
                    new Word(Mark.IDLE, G + "connector.serving", P + "connectors.status.serving", Symptom.NO_SUPPLY),
                    new Word(Mark.FAULT, G + "connector.nothing", P + "connectors.status.nothing",
                            Symptom.NO_SUPPLY),

                    new Word(Mark.RUNNING, G + "connector.bridging", () -> new Object[]{1},
                            P + "connectors.status.bridging", null),
                    new Word(Mark.CAUTION, G + "connector.send_only", P + "connectors.status.send_only",
                            Symptom.NO_SUPPLY),
                    new Word(Mark.RUNNING, G + "more", P + "connectors.status.more", null))))

            .part(new Status(P + "connectors.part.sneaking", List.of(
                    new Word(Mark.RUNNING, G + "connector.rated",
                            () -> new Object[]{Num.of(FeRates.connector(ConnectorTier.POWER))},
                            P + "connectors.status.rated", null),
                    new Word(Mark.CAUTION, G + "connector.rated_wiring",
                            () -> new Object[]{Num.of(FeRates.connector(ConnectorTier.POWER)),
                                    Num.of(oneCopperOnAPowerConnector())},
                            P + "connectors.status.rated_wiring", Symptom.WRONG_NUMBER),

                    new Word(Mark.RUNNING, G + "connector.cable",
                            () -> new Object[]{Num.of(FeRates.cable(CableTier.SURFACE))},
                            P + "connectors.status.cable", null),
                    new Word(Mark.CAUTION, G + "connector.block_takes_none", P + "connectors.status.takes_none",
                            Symptom.READS_ZERO))))
            .part(new Faults(Symptom.READS_ZERO, P + "connectors.fault.symptom", List.of(

                    new Step(P + "connectors.fault.takes_none", P + "connectors.fault.wrong_face",
                            P + "connectors.fault.wrong_face.note"),

                    new Step(P + "connectors.fault.no_wires", P + "connectors.fault.not_wired",
                            P + "connectors.fault.not_wired.note"),

                    new Step(P + "connectors.fault.capacity_low", P + "connectors.fault.thin_wiring",
                            P + "connectors.fault.thin_wiring.note")),
                    P + "fault.status_word", P + "connectors.fault.status_word.note", false))

            .part(new Part.Break(P + "kind.wire_tension", null, true))
            .part(new Figure(RingPlates.RING, P + "part.tension_ring", P + "connectors.ring.caption"))
            .part(new Controls(RingPlates.RING, List.of(

                    new Control("hub", P + "ring.control.hub", P + "ring.function.hub", P + "keys.scroll", null),

                    new Control("panel", P + "ring.control.panel", P + "ring.function.panel", P + "keys.point", null),
                    new Control("swatch", P + "ring.control.swatch", P + "ring.function.swatch", null, null),

                    new Control("level", P + "ring.control.level", P + "ring.function.level", P + "keys.scroll_or_click",
                            null),

                    new Control("span", P + "ring.control.span", P + "ring.function.span", null, null),
                    new Control("pips", P + "ring.control.pips", P + "ring.function.pips", null, null),
                    new Control("spoke", P + "ring.control.spoke", P + "ring.function.spoke", null, null),

                    new Control("hint", P + "ring.control.hint", P + "ring.function.hint", P + "keys.release_use",
                            null),

                    new Control("wire", P + "ring.control.wire", P + "ring.function.wire", null, null))))

            .part(new Figure(WireFigures.CONNECTOR_GRADES.id(), P + "connectors.portrait",
                    P + "connectors.portrait.caption"))

            .part(new Note(NoteKind.NOTE, P + "connectors.note.hub"))

            .part(new Note(NoteKind.NOTE, P + "connectors.note.touching"))
            .part(new Part.Break(P + "kind.wire_tension", null, true))
            .part(new Figure(RingPlates.WRENCH, P + "part.wrench_panel", P + "connectors.wrench.caption"))
            .part(new Controls(RingPlates.WRENCH, List.of(
                    new Control("aim", P + "wrench.control.aim", P + "wrench.function.aim", P + "keys.look", null),
                    new Control("all", P + "wrench.control.all", P + "wrench.function.all", null, null),

                    new Control("tab", P + "wrench.control.tab", P + "wrench.function.tab", P + "keys.sneak_scroll",
                            null),
                    new Control("level", P + "wrench.control.level", P + "wrench.function.level", P + "keys.scroll",
                            null),
                    new Control("pips", P + "wrench.control.pips", P + "ring.function.pips", null, null),
                    new Control("detail", P + "wrench.control.detail", P + "wrench.function.detail", null, null),
                    new Control("hint", P + "wrench.control.hint", P + "wrench.function.hint", null, null))))

            .part(new Status(P + "connectors.part.tension", List.of(
                    new Word(Mark.IDLE, "create_stats.wire_tension.no_wires", P + "connectors.status.no_wires",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.RUNNING, "create_stats.wire_tension.mixed", P + "connectors.status.mixed", null))))
            .build();

    static ConnectorTier endsFor(WireTier tier) {
        for (final ConnectorTier c : ConnectorTier.values()) {
            if (c.multiplier() >= tier.multiplier()) {
                return c;
            }
        }
        return ConnectorTier.INDUSTRIAL;
    }

    private static Grade wireRow(WireTier tier) {
        return new Grade(wireId(tier), null, List.of(
                new Cell(P + "value.fe_t", () -> new Object[]{Num.of(FeRates.wire(tier))}),

                new Cell(P + "value.px", () -> new Object[]{Num.of(tier.halfThickness() * 2.0 * 16.0)}),
                new Cell(P + "wires.ends." + endsFor(tier).name().toLowerCase(Locale.ROOT), null)));
    }

    private static Grade tensionRow(WireTension level) {
        return new Grade(null, "create_stats.wire_tension." + level.key(), List.of(
                new Cell(P + "value.percent", () -> new Object[]{Num.of(level.sagPerBlock() * 100.0)}),
                new Cell(P + "value.blocks", () -> new Object[]{Num.of(level.sagOver(Configs.wireReach()))})));
    }

    public static final Sheet WIRES = Sheet.of("copper_wire", Section.POWER)
            .group(CARRY)
            .title(P + "wires.title")
            .covers(C + "copper_wire", C + "insulated_wire", C + "shielded_wire", C + "hv_line")
            .also(C + "refined_copper", C + "refined_copper_sheet", C + "incomplete_shielded_wire",
                    C + "incomplete_hv_line", C + "unfired_insulator", C + "porcelain_insulator")
            .hook(P + "wires.hook")
            .part(new Figure(WireFigures.WIRES.id(), P + "part.the_four_grades", P + "wires.caption"))
            .part(new Grades(P + "part.grades", P + "wires.grades.note",
                    () -> new Object[]{Num.of(Configs.wireReach())},
                    List.of(new Cell(P + "column.carries", null), new Cell(P + "column.thick", null),
                            new Cell(P + "column.connector", null)),
                    List.of(wireRow(WireTier.COPPER), wireRow(WireTier.INSULATED), wireRow(WireTier.SHIELDED),
                            wireRow(WireTier.HV))))
            .part(new Steps(P + "wires.part.stringing", List.of(
                    new Instruction(P + "wires.step.connectors", C + "power_connector"),
                    new Instruction(P + "wires.step.first", C + "copper_wire",
                            List.of("message.create_stats.copper_wire.attached")),
                    new Instruction(P + "wires.step.second", C + "copper_wire",
                            List.of("message.create_stats.copper_wire.linked")),
                    new Instruction(P + "wires.step.tension", null),
                    new Instruction(P + "wires.step.down", null))))
            .part(new Application(List.of(
                    new Link(C + "kinetic_dynamo", P + "chain.supply", false),
                    new Link(C + "industrial_power_connector", P + "chain.terminal", false),
                    new Link(C + "hv_line", P + "chain.line", true),
                    new Link(C + "industrial_power_connector", P + "chain.terminal", false),
                    new Link(C + "arc_furnace", P + "chain.load", false)),
                    P + "wires.application"))
            .part(new SeeAlso(List.of("power_connector", "surface_cable", "cable_diagnostic_junction", "power_meter")))
            .part(new Part.Ponder(C + "copper_wire"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls(WireFigures.WIRES.id(), P + "part.part_designations", List.of(

                    new Control("copper", "item.create_stats.copper_wire", P + "wires.function.copper", null, null),
                    new Control("insulated", "item.create_stats.insulated_wire", P + "wires.function.insulated", null,
                            null),
                    new Control("shielded", "item.create_stats.shielded_wire", P + "wires.function.shielded", null,
                            null),
                    new Control("hv", "item.create_stats.hv_line", P + "wires.function.hv", null, null),
                    new Control("end", P + "wires.control.end", P + "wires.function.end", null, null))))
            .part(new World(List.of(
                    new Gesture(P + "wires.gesture.first", P + "wires.gesture.first.does"),
                    new Gesture(P + "wires.gesture.second", P + "wires.gesture.second.does"),

                    new Gesture(P + "wires.gesture.same", P + "wires.gesture.same.does"),
                    new Gesture(P + "gesture.sneak_wire", P + "wires.gesture.strip"),
                    new Gesture(P + "gesture.hold_use_empty", P + "wires.gesture.hold"),
                    new Gesture(P + "gesture.wrench", P + "wires.gesture.wrench"),
                    new Gesture(P + "gesture.breaking", P + "wires.gesture.breaking"))))

            .part(new Status(P + "wires.part.messages", List.of(
                    new Word(Mark.RUNNING, "message.create_stats.copper_wire.attached", P + "wires.status.attached",
                            null),
                    new Word(Mark.RUNNING, "message.create_stats.copper_wire.linked", P + "wires.status.linked", null),
                    new Word(Mark.IDLE, "message.create_stats.copper_wire.cancelled", P + "wires.status.cancelled",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, "message.create_stats.copper_wire.too_far",
                            () -> new Object[]{Num.of(Configs.wireReach())}, P + "wires.status.too_far",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, "message.create_stats.copper_wire.full", P + "wires.status.full",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, "message.create_stats.copper_wire.already_linked",
                            P + "wires.status.already_linked", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, "message.create_stats.copper_wire.lost_first", P + "wires.status.lost_first",
                            Symptom.WILL_NOT_START),

                    new Word(Mark.RUNNING, "message.create_stats.copper_wire.stripped", () -> new Object[]{1},
                            P + "wires.status.stripped", null),
                    new Word(Mark.CAUTION, "message.create_stats.copper_wire.nothing_to_strip",
                            P + "wires.status.nothing_to_strip", Symptom.WILL_NOT_START))))
            .part(new Faults(Symptom.WILL_NOT_START, P + "wires.fault.symptom", List.of(
                    new Step(P + "wires.fault.too_far", P + "wires.fault.reach", P + "wires.fault.reach.note"),
                    new Step(P + "wires.fault.full", P + "wires.fault.slots", P + "wires.fault.slots.note"),
                    new Step(P + "wires.fault.already", P + "wires.fault.one_pair", P + "wires.fault.one_pair.note")),
                    P + "fault.its_message", P + "wires.fault.message.note", false))

            .part(new Figure(WireFigures.TENSIONS.id(), P + "wires.part.tensions", P + "wires.tensions.caption"))
            .part(new Grades(P + "wires.part.levels", P + "wires.levels.note", null,
                    List.of(new Cell(P + "column.sags", null),
                            new Cell(P + "column.at_reach", () -> new Object[]{Num.of(Configs.wireReach())})),
                    List.of(tensionRow(WireTension.SLACK), tensionRow(WireTension.STANDARD),
                            tensionRow(WireTension.TAUT), tensionRow(WireTension.DRUM))))

            .part(new Note(NoteKind.NOTE, P + "wires.note.cosmetic"))

            .part(new Note(NoteKind.CAUTION, P + "wires.caution.ends"))

            .part(new Note(NoteKind.NOTE, P + "wires.note.config"))

            .part(new Made(P + "part.made", P + "made.note", List.of("copper_wire", "cutting/copper_wire",
                    "deploying/insulated_wire", "sequenced_assembly/shielded_wire", "arc/refined_copper",
                    "pressing/refined_copper_sheet", "sequenced_assembly/hv_line", "compacting/unfired_insulator",
                    "blasting/porcelain_insulator")))
            .build();

    public static List<Sheet> all() {
        return List.of(CONNECTORS, WIRES);
    }
}
