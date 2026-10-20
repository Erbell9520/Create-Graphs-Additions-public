package com.createstats.book.content;

import com.createstats.blockentity.BatteryControllerBE;
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
import com.createstats.book.specimen.BatteryPlates;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.CapacitorDischarge;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeBankMerge;
import com.createstats.power.FeChargePolicy;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import com.createstats.power.FeReserve;
import com.createstats.power.FeReserveBand;
import com.createstats.power.PortableFeLadder;
import com.createstats.power.PortableFeTransfer;
import com.createstats.power.ReserveWords;
import com.createstats.power.StarterTrigger;
import com.createstats.power.TrainBankLedger;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.redstone.Redstone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class StoreSheets {
    private StoreSheets() {
    }

    private static final String STORE = "create_stats.book.group.store";
    private static final String P = "create_stats.book.";

    private static final String G = P + "goggle.";

    private static String seconds(int ticks) {
        return Num.of(ticks / (double) SharedConstants.TICKS_PER_SECOND);
    }

    static long burnedOff(int take) {
        long burned = 0;
        for (int t = 0; t < CapacitorDischarge.BURST_TICKS; t++) {
            burned += Math.max(0, CapacitorDischarge.allowanceAt(t) - take);
        }
        return burned;
    }

    static int exampleTake() {
        return Configs.bankTransferRate();
    }

    public static final Sheet STARTER_CAPACITOR = Sheet.of("starter_capacitor", Section.POWER)
            .group(STORE)
            .title("block.create_stats.starter_capacitor")
            .covers("create_stats:starter_capacitor")
            .hook(P + "starter_capacitor.hook")
            .part(new Figure("starter_capacitor", P + "part.parts_and_faces", P + "starter_capacitor.caption"))
            .part(new Ratings(List.of(

                    new Rating(P + "starter_capacitor.rating.holds", P + "value.figure",
                            () -> new Object[]{FeRates.amount(CapacitorDischarge.CAPACITY)}),

                    new Rating(P + "starter_capacitor.rating.burst", P + "starter_capacitor.value.burst",
                            () -> new Object[]{FeRates.perTick(CapacitorDischarge.PEAK_FE_PER_TICK),
                                    seconds(CapacitorDischarge.BURST_TICKS)}),

                    new Rating(P + "starter_capacitor.rating.fires", P + "starter_capacitor.value.fires",
                            () -> new Object[]{seconds(StarterTrigger.SUSTAIN_TICKS)}),

                    new Rating(P + "starter_capacitor.rating.charges", P + "starter_capacitor.value.charges",
                            () -> new Object[]{FeRates.perTick(CapacitorDischarge.CHARGE_FE_PER_TICK)}),

                    new Rating(P + "starter_capacitor.rating.refill", P + "starter_capacitor.value.refill",
                            () -> new Object[]{FeGoggle.time(CapacitorDischarge.ticksToFull(0))}),

                    new Rating(P + "rating.comparator", P + "value.range",
                            () -> new Object[]{Redstone.SIGNAL_MIN, Redstone.SIGNAL_MAX}))))
            .part(new Application(List.of(
                    new Link("create_stats:kinetic_dynamo", P + "chain.supply", false),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:starter_capacitor", P + "chain.capacitor", true),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "starter_capacitor.application"))
            .part(new SeeAlso(List.of("capacitor_bank", "fe_battery_controller", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("starter_capacitor", P + "part.part_designations", List.of(

                    new Control("mode", P + "starter_capacitor.control.mode", P + "starter_capacitor.function.mode",
                            P + "keys.hold_right_click", null),

                    new Control("lamp", P + "starter_capacitor.control.lamp", P + "starter_capacitor.function.lamp",
                            null, null),

                    new Control("jars", P + "starter_capacitor.control.jars", P + "starter_capacitor.function.jars",
                            null, null),
                    new Control("gap", P + "starter_capacitor.control.gap", P + "starter_capacitor.function.gap",
                            null, null),

                    new Control("gland", P + "starter_capacitor.control.gland", P + "starter_capacitor.function.gland",
                            null, null),
                    new Control("lug", P + "starter_capacitor.control.lug", P + "starter_capacitor.function.lug",
                            null, null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.sneak_empty_hand", P + "starter_capacitor.gesture.picks_up"),
                    new Gesture(P + "gesture.breaking", P + "starter_capacitor.gesture.drops"),
                    new Gesture(P + "gesture.redstone", P + "starter_capacitor.gesture.redstone"),
                    new Gesture(P + "gesture.comparator", P + "starter_capacitor.gesture.comparator"),
                    new Gesture(P + "gesture.goggles", P + "starter_capacitor.gesture.goggles"))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, G + "starter_capacitor.armed", P + "starter_capacitor.status.armed", null),
                    new Word(Mark.RUNNING, G + "starter_capacitor.ready", P + "starter_capacitor.status.ready", null),

                    new Word(Mark.IDLE, G + "starter_capacitor.charging", () -> new Object[]{
                            FeGoggle.time(CapacitorDischarge.ticksToFull(CapacitorDischarge.CAPACITY / 2))},
                            P + "starter_capacitor.status.charging", Symptom.WILL_NOT_START),
                    new Word(Mark.IDLE, G + "starter_capacitor.holding", P + "starter_capacitor.status.holding",
                            Symptom.WILL_NOT_START),

                    new Word(Mark.CAUTION, G + "starter_capacitor.short", () -> new Object[]{String.format(Locale.ROOT,
                            "%.1f", (StarterTrigger.SUSTAIN_TICKS - SharedConstants.TICKS_PER_SECOND) / 20.0f)},
                            P + "starter_capacitor.status.short", Symptom.NO_SUPPLY),

                    new Word(Mark.RUNNING, G + "starter_capacitor.discharging", () -> new Object[]{
                            String.format(Locale.ROOT, "%,d", CapacitorDischarge.allowanceAt(0)),
                            String.format(Locale.ROOT, "%.1f", CapacitorDischarge.BURST_TICKS / 20.0f)},
                            P + "starter_capacitor.status.discharging", null),

                    new Word(Mark.CAUTION, G + "starter_capacitor.burned", () -> new Object[]{
                            String.format(Locale.ROOT, "%,d", burnedOff(exampleTake()))},
                            P + "starter_capacitor.status.burned", Symptom.WRONG_NUMBER),
                    new Word(Mark.FAULT, G + "starter_capacitor.no_circuit", P + "starter_capacitor.status.no_circuit",
                            Symptom.NO_SUPPLY),

                    new Word(Mark.FAULT, G + "starter_capacitor.limiter", P + "starter_capacitor.status.limiter",
                            Symptom.WILL_NOT_START),

                    new Word(Mark.CAUTION, G + "starter_capacitor.input", P + "starter_capacitor.status.input",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.RUNNING, G + "starter_capacitor.managed", P + "starter_capacitor.status.managed",
                            null))))
            .part(new Faults(Symptom.WILL_NOT_START, P + "starter_capacitor.fault.symptom", List.of(
                    new Step(P + "starter_capacitor.fault.holding", P + "starter_capacitor.fault.set_to_hold",
                            P + "starter_capacitor.fault.set_to_hold.note"),

                    new Step(P + "starter_capacitor.fault.charging", P + "starter_capacitor.fault.waits_full",
                            P + "starter_capacitor.fault.waits_full.note"),

                    new Step(P + "starter_capacitor.fault.recovers", P + "starter_capacitor.fault.too_brief",
                            P + "starter_capacitor.fault.too_brief.note")),
                    P + "fault.status_word", P + "starter_capacitor.fault.status_word.note", false))

            .part(new Part.Curve(P + "starter_capacitor.curve", P + "starter_capacitor.curve.note", null,
                    CapacitorDischarge.BURST_TICKS, List.of(
                            new Part.Tick(0, P + "value.seconds", () -> new Object[]{seconds(0)}),
                            new Part.Tick(CapacitorDischarge.BURST_TICKS / 2.0, P + "value.seconds",
                                    () -> new Object[]{seconds(CapacitorDischarge.BURST_TICKS / 2)}),
                            new Part.Tick(CapacitorDischarge.BURST_TICKS, P + "value.seconds",
                                    () -> new Object[]{seconds(CapacitorDischarge.BURST_TICKS)})),
                    P + "value.fe_t", List.of(
                            new Part.Line(P + "starter_capacitor.curve.burst",
                                    t -> CapacitorDischarge.allowanceAt((int) t), Part.LineStyle.SUBJECT),
                            new Part.Line(P + "starter_capacitor.curve.bank", t -> Configs.bankTransferRate(),
                                    Part.LineStyle.REFERENCE),
                            new Part.Line(P + "starter_capacitor.curve.charge",
                                    t -> CapacitorDischarge.CHARGE_FE_PER_TICK, Part.LineStyle.DOTTED))))

            .part(new Note(NoteKind.NOTE, P + "starter_capacitor.note"))
            .build();

    static int bankHolds(int blocks) {
        return FeBankMerge.capacity(blocks, Configs.bankBlockCapacity(), Configs.bankMergeExponent());
    }

    private static Grade bankSize(String labelKey, boolean sided, Supplier<int[]> sides) {
        return new Grade("create_stats:capacitor_bank", labelKey, List.of(
                new Cell(P + "value.count", () -> new Object[]{Num.of(blocksOf(sides.get()))}),
                new Cell(P + "value.figure", () -> new Object[]{FeRates.amount(bankHolds(blocksOf(sides.get())))}),
                new Cell(P + "value.figure", () -> new Object[]{
                        FeRates.amount(bankHolds(blocksOf(sides.get())) / Math.max(1, blocksOf(sides.get())))})),
                sided ? () -> {
                    final int[] s = sides.get();
                    return new Object[]{s[0], s[1], s[2]};
                } : null);
    }

    private static int blocksOf(int[] sides) {
        return sides[0] * sides[1] * sides[2];
    }

    static int exampleBankBlocks() {
        return StoreFixtures.bank().blocks();
    }

    static int exampleBankFlow() {
        return StoreFixtures.bank().in();
    }

    static String exampleBankFullIn() {
        return FeGoggle.time(StoreFixtures.bank().enduranceTicks());
    }

    public static final Sheet CAPACITOR_BANK = Sheet.of("capacitor_bank", Section.POWER)
            .group(STORE)
            .title("block.create_stats.capacitor_bank")
            .covers("create_stats:capacitor_bank")
            .hook(P + "capacitor_bank.hook")
            .part(new Figure("capacitor_bank", P + "part.parts_and_faces", P + "capacitor_bank.caption"))

            .part(new Grades(P + "capacitor_bank.sizes", P + "capacitor_bank.sizes.note",
                    () -> new Object[]{Configs.bankMergeExponent()},
                    List.of(new Cell(P + "column.blocks", null), new Cell(P + "column.holds", null),
                            new Cell(P + "column.each_block", null)),
                    List.of(bankSize(P + "capacitor_bank.size.one", false, () -> new int[]{1, 1, 1}),
                            bankSize(P + "capacitor_bank.size", true, () -> new int[]{Configs.bankMaxFootprint(),
                                    Configs.bankMaxFootprint(), 1}),
                            bankSize(P + "capacitor_bank.size", true, () -> new int[]{Configs.bankMaxFootprint(),
                                    Configs.bankMaxFootprint(),
                                    Math.min(Configs.bankMaxFootprint(), Configs.bankMaxHeight())}),
                            bankSize(P + "capacitor_bank.size", true, () -> new int[]{Configs.bankMaxFootprint(),
                                    Configs.bankMaxFootprint(), Configs.bankMaxHeight()}))))
            .part(new Ratings(List.of(

                    new Rating(P + "capacitor_bank.rating.moves", P + "capacitor_bank.value.moves",
                            () -> new Object[]{FeRates.perTick(Configs.bankTransferRate())}),

                    new Rating(P + "capacitor_bank.rating.largest", P + "capacitor_bank.value.largest",
                            () -> new Object[]{Configs.bankMaxFootprint(), Configs.bankMaxFootprint(),
                                    Configs.bankMaxHeight()}),

                    new Rating(P + "capacitor_bank.rating.ranks", P + "capacitor_bank.value.ranks",
                            () -> new Object[]{FeReserve.MIN_RANK, FeReserve.MAX_RANK, FeReserve.DEFAULT_RANK}))))
            .part(new Application(List.of(
                    new Link("create_stats:kinetic_dynamo", P + "chain.supply", false),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:capacitor_bank", P + "chain.bank", true),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "capacitor_bank.application"))
            .part(new SeeAlso(List.of("fe_battery_controller", "starter_capacitor", "portable_power_interface")))
            .part(new Part.Ponder("create_stats:capacitor_bank"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("capacitor_bank", P + "part.part_designations", List.of(

                    new Control("face", P + "capacitor_bank.control.face", P + "capacitor_bank.function.face",
                            null, null),

                    new Control("spine", P + "capacitor_bank.control.spine", P + "capacitor_bank.function.spine",
                            null, null),
                    new Control("readout", P + "capacitor_bank.control.readout",
                            P + "capacitor_bank.function.readout", null, null),
                    new Control("layers", P + "capacitor_bank.control.layers", P + "capacitor_bank.function.layers",
                            null, null),
                    new Control("lamp", P + "capacitor_bank.control.lamp", P + "capacitor_bank.function.lamp",
                            null, null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.wrench", P + "capacitor_bank.gesture.rank"),
                    new Gesture(P + "gesture.bank_in_hand", P + "capacitor_bank.gesture.next_block"),
                    new Gesture(P + "gesture.breaking", P + "capacitor_bank.gesture.drops"),

                    new Gesture(P + "gesture.display_link", P + "capacitor_bank.gesture.display_link"),
                    new Gesture(P + "gesture.goggles", P + "capacitor_bank.gesture.goggles"))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, G + "charging", () -> new Object[]{Num.of(exampleBankFlow())},
                            P + "capacitor_bank.status.charging", null),
                    new Word(Mark.CAUTION, G + "draining", () -> new Object[]{Num.of(exampleBankFlow())},
                            P + "capacitor_bank.status.draining", Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.IDLE, G + "holding_steady", P + "capacitor_bank.status.holding",
                            Symptom.READS_ZERO),

                    new Word(Mark.RUNNING, G + "capacitor_bank.in_out", () -> new Object[]{
                            Num.of(exampleBankFlow()), Num.of(exampleBankFlow())},
                            P + "capacitor_bank.status.in_out", null),

                    new Word(Mark.RUNNING, G + "capacitor_bank.full_in", () -> new Object[]{exampleBankFullIn()},
                            P + "capacitor_bank.status.full_in", null),
                    new Word(Mark.CAUTION, G + "capacitor_bank.empty_in", () -> new Object[]{exampleBankFullIn()},
                            P + "capacitor_bank.status.empty_in", Symptom.RUNS_THEN_STOPS),

                    new Word(Mark.RUNNING, G + "capacitor_bank.managed_full", () -> new Object[]{exampleBankFullIn()},
                            P + "capacitor_bank.status.managed_full", null),
                    new Word(Mark.RUNNING, G + "capacitor_bank.managed_settles", () -> new Object[]{
                            Configs.batteryBand().ceilingPct()}, P + "capacitor_bank.status.managed_settles", null),
                    new Word(Mark.RUNNING, G + "capacitor_bank.pooled", () -> new Object[]{exampleBankBlocks()},
                            P + "capacitor_bank.status.pooled", null),

                    new Word(Mark.RUNNING, P + "capacitor_bank.lamp.glowing", P + "capacitor_bank.status.glowing",
                            null),
                    new Word(Mark.IDLE, P + "capacitor_bank.lamp.dark", P + "capacitor_bank.status.dark",
                            Symptom.READS_ZERO))))
            .part(new Faults(Symptom.READS_ZERO, P + "capacitor_bank.fault.symptom", List.of(

                    new Step(P + "capacitor_bank.fault.no_spare", P + "capacitor_bank.fault.no_surplus",
                            P + "capacitor_bank.fault.no_surplus.note"),
                    new Step(P + "capacitor_bank.fault.on_input", P + "capacitor_bank.fault.controller_decides",
                            P + "capacitor_bank.fault.controller_decides.note"),

                    new Step(P + "capacitor_bank.fault.wide_wall", P + "capacitor_bank.fault.other_bank",
                            P + "capacitor_bank.fault.other_bank.note")),
                    P + "capacitor_bank.fault.never_from_bank", P + "capacitor_bank.fault.never_from_bank.note", true))

            .part(new Note(NoteKind.NOTE, P + "capacitor_bank.joining", () -> new Object[]{
                    Configs.bankMaxFootprint(), Configs.bankMaxFootprint(), Configs.bankMaxHeight()}))
            .part(new Note(NoteKind.CAUTION, P + "capacitor_bank.caution"))
            .build();

    static final int EXAMPLE_DYNAMOS = 4;

    static int exampleClaim() {
        final FeReserveBand.Band band = Configs.batteryBand();
        final int generation = EXAMPLE_DYNAMOS * GenerateSheets.dynamoOutput(DynamoTier.STANDARD);
        return FeChargePolicy.claim(generation, band.ceilingPct(), Integer.MAX_VALUE / 2, band,
                Configs.batteryClaimPct(), Integer.MAX_VALUE / 2);
    }

    static final int EXAMPLE_HOLD_PCT = 25;

    private static Supplier<Object[]> tabName(int index) {
        return () -> new Object[]{BatteryPlates.tabLabels().get(index).toUpperCase(Locale.ROOT)};
    }

    private static Part.Tab tab(int index) {
        return new Part.Tab(index, BatteryPlates::tabLabels);
    }

    private static Control plate(String plate, String id, String keysKey) {
        final String k = P + "battery_controller." + plate + ".";
        return new Control(id, k + "control." + id, k + "function." + id, keysKey, null);
    }

    public static final Sheet BATTERY_CONTROLLER = Sheet.of("fe_battery_controller", Section.POWER)
            .group(STORE)
            .title("block.create_stats.fe_battery_controller")
            .covers("create_stats:fe_battery_controller")
            .hook(P + "battery_controller.hook")
            .part(new Figure("fe_battery_controller", P + "part.parts_and_faces", P + "battery_controller.caption"))
            .part(new Ratings(List.of(
                    new Rating(P + "battery_controller.rating.terminals", P + "battery_controller.value.terminals",
                            () -> new Object[]{BatteryControllerPorts.INPUTS, BatteryControllerPorts.OUTPUTS}),

                    new Rating(P + "battery_controller.rating.input", P + "battery_controller.value.input",
                            () -> new Object[]{FeRates.perTick(Configs.batteryInputRate())}),
                    new Rating(P + "battery_controller.rating.terminal", P + "battery_controller.value.terminal",
                            () -> new Object[]{FeRates.perTick(Configs.batteryPortRate())}),
                    new Rating(P + "battery_controller.rating.wires", P + "value.count",
                            () -> new Object[]{BatteryControllerBE.MAX_LINKS_PER_PORT}),

                    new Rating(P + "battery_controller.rating.bus", P + "value.figure",
                            () -> new Object[]{FeRates.amount(Configs.batteryBus())}),

                    new Rating(P + "battery_controller.rating.band", P + "battery_controller.value.band_minutes",
                            () -> new Object[]{Configs.batteryMinutes().floor(), Configs.batteryMinutes().ceiling(),
                                    Configs.batteryMinutes().healthy()}),
                    new Rating(P + "battery_controller.rating.band_cap", P + "battery_controller.value.band",
                            () -> new Object[]{Configs.batteryBand().floorPct(), Configs.batteryBand().ceilingPct(),
                                    Configs.batteryBand().healthyPct()}),

                    new Rating(P + "battery_controller.rating.refill", P + "battery_controller.value.refill",
                            () -> new Object[]{Configs.batteryClaimPct()}),

                    new Rating(P + "battery_controller.rating.forecast", P + "battery_controller.value.forecast",
                            () -> new Object[]{ReserveWords.horizon()}))))
            .part(new Application(List.of(
                    new Link("create_stats:capacitor_bank", P + "chain.bank", false),
                    new Link("create_stats:copper_wire", P + "chain.wire", false),
                    new Link("create_stats:fe_battery_controller", P + "chain.controller", true),
                    new Link("create_stats:copper_wire", P + "chain.wire", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "battery_controller.application"))
            .part(new SeeAlso(List.of("capacitor_bank", "starter_capacitor", "portable_power_interface", "power_meter")))
            .part(new Part.Ponder("create_stats:fe_battery_controller"))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("fe_battery_controller", P + "part.part_designations", List.of(
                    new Control("inputs", P + "battery_controller.control.inputs",
                            P + "battery_controller.function.inputs", P + "keys.wire_then_terminal", null),
                    new Control("outputs", P + "battery_controller.control.outputs",
                            P + "battery_controller.function.outputs", P + "keys.wire_then_terminal", null),
                    new Control("screen", P + "battery_controller.control.screen",
                            P + "battery_controller.function.screen", P + "keys.right_click", null),
                    new Control("case", P + "battery_controller.control.case", P + "battery_controller.function.case",
                            null, null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.wire_on_stud", P + "battery_controller.gesture.wire"),
                    new Gesture(P + "gesture.sneak_wire_on_stud", P + "battery_controller.gesture.strip"),
                    new Gesture(P + "gesture.breaking", P + "battery_controller.gesture.drops"),
                    new Gesture(P + "gesture.display_link", P + "battery_controller.gesture.display_link"),
                    new Gesture(P + "gesture.goggles", P + "battery_controller.gesture.goggles"))))

            .part(new Status(P + "battery_controller.part.reserve", List.of(
                    new Word(Mark.RUNNING, G + "battery_controller.surplus", P + "battery_controller.status.surplus",
                            null),
                    new Word(Mark.RUNNING, G + "battery_controller.nominal", P + "battery_controller.status.nominal",
                            null),
                    new Word(Mark.CAUTION, G + "battery_controller.conserve", P + "battery_controller.status.conserve",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, G + "battery_controller.critical", P + "battery_controller.status.critical",
                            Symptom.SHED_OR_OFF),

                    new Word(Mark.CAUTION, G + "battery_controller.refill", () -> new Object[]{
                            String.format(Locale.ROOT, "%,d", exampleClaim())}, P + "battery_controller.status.refill",
                            Symptom.SHED_OR_OFF),

                    new Word(Mark.CAUTION, G + "battery_controller.hold", () -> new Object[]{EXAMPLE_HOLD_PCT,
                            Configs.batteryBand().floorPct()}, P + "battery_controller.status.hold",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, G + "battery_controller.no_storage",
                            P + "battery_controller.status.no_storage", Symptom.NO_SUPPLY),

                    new Word(Mark.FAULT, G + "battery_controller.stray", () -> new Object[]{
                            BatteryControllerPorts.describe(0)}, P + "battery_controller.status.stray",
                            Symptom.READS_ZERO))))

            .part(new Status(P + "battery_controller.part.outputs", List.of(
                    new Word(Mark.RUNNING, G + "battery_controller.served", P + "battery_controller.status.served",
                            null),
                    new Word(Mark.CAUTION, G + "battery_controller.partial", P + "battery_controller.status.partial",
                            Symptom.NO_SUPPLY),
                    new Word(Mark.IDLE, G + "battery_controller.off", P + "battery_controller.status.off",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "battery_controller.floor", P + "battery_controller.status.floor",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "battery_controller.rationed",
                            P + "battery_controller.status.rationed", Symptom.SHED_OR_OFF),
                    new Word(Mark.IDLE, G + "battery_controller.no_surplus",
                            P + "battery_controller.status.no_surplus", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "battery_controller.recharge",
                            P + "battery_controller.status.recharge", Symptom.SHED_OR_OFF),
                    new Word(Mark.FAULT, G + "battery_controller.shed", P + "battery_controller.status.shed",
                            Symptom.SHED_OR_OFF),
                    new Word(Mark.IDLE, G + "battery_controller.idle", P + "battery_controller.status.idle",
                            Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "battery_controller.unwired", P + "battery_controller.status.unwired",
                            Symptom.READS_ZERO))))
            .part(new Faults(Symptom.SHED_OR_OFF, P + "battery_controller.fault.symptom", List.of(
                    new Step(P + "battery_controller.fault.breaker", P + "battery_controller.fault.switched_off",
                            P + "battery_controller.fault.switched_off.note"),

                    new Step(P + "battery_controller.fault.under_floor", P + "battery_controller.fault.held_by_floor",
                            P + "battery_controller.fault.held_by_floor.note")),
                    P + "fault.status_word", P + "battery_controller.fault.status_word.note", false))

            .part(new Part.Break(P + "kind.screen_tab", tabName(0), true))
            .part(new Figure(BatteryPlates.NOW, P + "part.the_screen", P + "caption.example_readings", tab(0)))
            .part(new Controls(BatteryPlates.NOW, List.of(
                    plate("now", "tab", P + "keys.click"),
                    plate("now", "stance", null),
                    plate("now", "headline", null),
                    plate("now", "balance", null),
                    plate("now", "inputs", null),
                    plate("now", "switch", P + "keys.click"),
                    plate("now", "band", null),
                    plate("now", "outputs", P + "keys.click"),
                    plate("now", "breaker", P + "keys.click"),
                    plate("now", "mode", P + "keys.click"),
                    plate("now", "tier", P + "keys.click"),
                    plate("now", "floor", P + "keys.drag"),
                    plate("now", "limit", P + "keys.drag"),
                    plate("now", "footer", null))))

            .part(new Part.Break(P + "kind.screen_tab", tabName(0), true))
            .part(new Figure(BatteryPlates.BAND, P + "part.the_screen", P + "caption.example_readings", tab(0)))
            .part(new Controls(BatteryPlates.BAND, List.of(
                    plate("band", "tab", P + "keys.click"),
                    plate("band", "ruler", null),
                    plate("band", "handles", P + "keys.drag"),
                    plate("band", "pins", P + "keys.drag"),
                    plate("band", "unit", P + "keys.click"),
                    plate("band", "edges", P + "keys.drag"),
                    plate("band", "caps", P + "keys.drag"),
                    plate("band", "footer", null))))
            .part(new Part.Break(P + "kind.screen_tab", tabName(1), true))
            .part(new Figure(BatteryPlates.FORECAST, P + "part.the_screen", P + "caption.example_readings", tab(1)))
            .part(new Controls(BatteryPlates.FORECAST, List.of(
                    plate("forecast", "tab", P + "keys.click"),
                    plate("forecast", "plot", null),
                    plate("forecast", "now", null),
                    plate("forecast", "marker", null),
                    plate("forecast", "strip", null),
                    plate("forecast", "range", P + "keys.click"),
                    plate("forecast", "next", null),
                    plate("forecast", "assumes", null),
                    plate("forecast", "footer", null))))
            .part(new Part.Break(P + "kind.screen_tab", tabName(2), true))
            .part(new Figure(BatteryPlates.RULES, P + "part.the_screen", P + "caption.example_readings", tab(2)))
            .part(new Controls(BatteryPlates.RULES, List.of(
                    plate("rules", "tab", P + "keys.click"),
                    plate("rules", "step1", null),
                    plate("rules", "bandbar", null),
                    plate("rules", "step2", null),
                    plate("rules", "step3", null),
                    plate("rules", "step4", null),
                    plate("rules", "step5", null),
                    plate("rules", "footer", null))))

            .part(new Steps(P + "battery_controller.tick", List.of(
                    new Instruction(P + "battery_controller.tick.inputs", "create_stats:capacitor_bank"),
                    new Instruction(P + "battery_controller.tick.outputs", "create_stats:induction_smelter"),
                    new Instruction(P + "battery_controller.tick.band", null),
                    new Instruction(P + "battery_controller.tick.refill", "create_stats:kinetic_dynamo"),
                    new Instruction(P + "battery_controller.tick.plan", null),
                    new Instruction(P + "battery_controller.tick.commit", null),
                    new Instruction(P + "battery_controller.tick.capacitors", "create_stats:starter_capacitor"),
                    new Instruction(P + "battery_controller.tick.banks", "create_stats:capacitor_bank"))))

            .part(new Note(NoteKind.NOTE, P + "battery_controller.note.refill"))
            .build();

    private static int ceiling() {
        return Configs.portableMaxRate();
    }

    public static final Sheet PORTABLE_INTERFACE = Sheet.of("portable_power_interface", Section.POWER)
            .group(STORE)
            .title("block.create_stats.portable_power_interface")
            .covers("create_stats:portable_power_interface")
            .hook(P + "portable_power_interface.hook")
            .part(new Figure("portable_power_interface", P + "part.parts_and_faces",
                    P + "portable_power_interface.caption"))
            .part(new Ratings(List.of(

                    new Rating(P + "portable_power_interface.rating.rate", P + "value.range",
                            () -> new Object[]{FeRates.perTick(PortableFeLadder.feAt(0, ceiling())),
                                    FeRates.perTick(PortableFeLadder.feAt(PortableFeLadder.maxDial(ceiling()),
                                            ceiling()))}),
                    new Rating(P + "portable_power_interface.rating.steps", P + "portable_power_interface.value.steps",
                            () -> new Object[]{PortableFeLadder.maxDial(ceiling()) + 1}),

                    new Rating(P + "portable_power_interface.rating.placed", P + "portable_power_interface.value.placed",
                            () -> new Object[]{FeRates.perTick(PortableFeLadder.feAt(
                                    PortableFeLadder.defaultDial(ceiling()), ceiling())), Configs.portableFloorPct()}),

                    new Rating(P + "portable_power_interface.rating.resumes",
                            P + "portable_power_interface.value.resumes",
                            () -> new Object[]{PortableFeTransfer.HYSTERESIS_PCT}),

                    new Rating(P + "rating.comparator", P + "portable_power_interface.value.comparator",
                            () -> new Object[]{TrainBankLedger.comparatorLevel(0, 1),
                                    TrainBankLedger.comparatorLevel(1, 1), TrainBankLedger.comparatorLevel(0, 0)}))))
            .part(new Application(List.of(
                    new Link("create_stats:capacitor_bank", P + "chain.bank", false),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:portable_power_interface", P + "chain.station", true),
                    new Link("create_stats:portable_power_interface", P + "chain.train", false),
                    new Link("create_stats:capacitor_bank", P + "chain.aboard", false)),
                    P + "portable_power_interface.application"))
            .part(new SeeAlso(List.of("capacitor_bank", "fe_battery_controller", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("portable_power_interface", P + "part.part_designations", List.of(
                    new Control("head", P + "portable_power_interface.control.head",
                            P + "portable_power_interface.function.head", null, null),

                    new Control("floor", P + "portable_power_interface.control.floor",
                            P + "portable_power_interface.function.floor", P + "keys.hold_right_click", null),
                    new Control("dial", P + "portable_power_interface.control.dial",
                            P + "portable_power_interface.function.dial", P + "keys.hold_right_click", null),
                    new Control("body", P + "portable_power_interface.control.body",
                            P + "portable_power_interface.function.body", null, null),
                    new Control("train", P + "portable_power_interface.control.train",
                            P + "portable_power_interface.function.train", null, null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.redstone", P + "portable_power_interface.gesture.redstone"),
                    new Gesture(P + "gesture.comparator", P + "portable_power_interface.gesture.comparator"),
                    new Gesture(P + "gesture.schedule", P + "portable_power_interface.gesture.schedule"),
                    new Gesture(P + "gesture.goggles", P + "portable_power_interface.gesture.goggles"))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, G + "portable_power_interface.charging", () -> new Object[]{
                            FeRates.perTick(PortableFeLadder.feAt(PortableFeLadder.defaultDial(ceiling()), ceiling()))},
                            P + "portable_power_interface.status.charging", null),
                    new Word(Mark.RUNNING, G + "portable_power_interface.drawing", () -> new Object[]{
                            FeRates.perTick(PortableFeLadder.feAt(PortableFeLadder.defaultDial(ceiling()), ceiling()))},
                            P + "portable_power_interface.status.drawing", null),
                    new Word(Mark.RUNNING, G + "portable_power_interface.full", P + "portable_power_interface.status.full",
                            null),
                    new Word(Mark.CAUTION, G + "portable_power_interface.waiting_supply",
                            P + "portable_power_interface.status.waiting_supply", Symptom.NO_SUPPLY),
                    new Word(Mark.CAUTION, G + "portable_power_interface.waiting_circuit",
                            P + "portable_power_interface.status.waiting_circuit", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, G + "portable_power_interface.nothing_docked",
                            P + "portable_power_interface.status.nothing_docked", Symptom.WILL_NOT_START),

                    new Word(Mark.CAUTION, G + "portable_power_interface.floor_station", () -> new Object[]{
                            Configs.portableFloorPct()}, P + "portable_power_interface.status.floor_station",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.CAUTION, G + "portable_power_interface.floor_train", () -> new Object[]{
                            Configs.portableFloorPct()}, P + "portable_power_interface.status.floor_train",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.IDLE, G + "portable_power_interface.empty", P + "portable_power_interface.status.empty",
                            Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.CAUTION, G + "portable_power_interface.station_held",
                            P + "portable_power_interface.status.station_held", Symptom.SHED_OR_OFF),
                    new Word(Mark.CAUTION, G + "portable_power_interface.shed",
                            P + "portable_power_interface.status.shed", Symptom.SHED_OR_OFF),
                    new Word(Mark.IDLE, G + "portable_power_interface.off", P + "portable_power_interface.status.off",
                            Symptom.SHED_OR_OFF),

                    new Word(Mark.CAUTION, G + "portable_power_interface.locked_draw",
                            P + "portable_power_interface.status.locked", Symptom.WILL_NOT_START),
                    new Word(Mark.CAUTION, G + "portable_power_interface.locked_charge",
                            P + "portable_power_interface.status.locked", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "portable_power_interface.no_storage",
                            P + "portable_power_interface.status.no_storage", Symptom.WILL_NOT_START),

                    new Word(Mark.FAULT, G + "portable_power_interface.wrong_charge",
                            P + "portable_power_interface.status.wrong_charge", Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, G + "portable_power_interface.wrong_draw",
                            P + "portable_power_interface.status.wrong_draw", Symptom.WILL_NOT_START))))
            .part(new Faults(Symptom.READS_ZERO, P + "portable_power_interface.fault.symptom", List.of(
                    new Step(P + "portable_power_interface.fault.not_docked", P + "portable_power_interface.fault.not_paired",
                            P + "portable_power_interface.fault.not_paired.note"),
                    new Step(P + "portable_power_interface.fault.paused", P + "portable_power_interface.fault.held",
                            P + "portable_power_interface.fault.held.note")),
                    P + "fault.status_word", P + "portable_power_interface.fault.status_word.note", false))
            .part(new Steps(P + "portable_power_interface.steps", List.of(
                    new Instruction(P + "portable_power_interface.step.on_train", "create_stats:portable_power_interface"),
                    new Instruction(P + "portable_power_interface.step.at_station",
                            "create_stats:portable_power_interface"),
                    new Instruction(P + "portable_power_interface.step.wire", "create_stats:surface_cable"),
                    new Instruction(P + "portable_power_interface.step.dial", null),
                    new Instruction(P + "portable_power_interface.step.schedule", "create:schedule"))))
            .build();

    public static List<Sheet> all() {
        final List<Sheet> out = new ArrayList<>();
        out.add(STARTER_CAPACITOR);
        out.add(CAPACITOR_BANK);
        out.add(BATTERY_CONTROLLER);
        out.add(PORTABLE_INTERFACE);
        return List.copyOf(out);
    }
}
