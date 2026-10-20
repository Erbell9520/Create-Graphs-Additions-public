package com.createstats.book.content;

import com.createstats.block.KineticDynamoBlock;
import com.createstats.blockentity.BiofuelBurnerBE;
import com.createstats.blockentity.SolarPanelBE;
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
import com.createstats.power.BiofuelGrade;
import com.createstats.power.BiofuelValue;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeRates;
import com.createstats.power.SolarCurve;
import com.createstats.power.SolarTier;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.redstone.Redstone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GenerateSheets {
    private GenerateSheets() {
    }

    private static final String GENERATE = "create_stats.book.group.generate";
    private static final String P = "create_stats.book.";

    static final String OUTPUT = P + "goggle.output";
    static final String IDLE = P + "goggle.idle";

    static int dynamoOutput(DynamoTier tier) {
        return (int) Math.round(tier.fePerRpm() * KineticDynamoBlock.TOP_RPM);
    }

    public static final Sheet DYNAMOS = Sheet.of("kinetic_dynamo", Section.POWER)
            .group(GENERATE)
            .title(P + "dynamos.title")
            .covers("create_stats:kinetic_dynamo", "create_stats:open_frame_dynamo",
                    "create_stats:industrial_alternator")
            .hook(P + "dynamos.hook")
            .part(new Figure("kinetic_dynamo", P + "part.parts_and_faces", P + "dynamos.caption"))
            .part(new Grades(P + "part.grades", P + "dynamos.grades.note",
                    () -> new Object[]{KineticDynamoBlock.TOP_RPM},
                    List.of(new Cell(P + "column.output", null), new Cell(P + "column.stress", null),
                            new Cell(P + "column.fe_per_su", null)),
                    List.of(dynamoRow("create_stats:open_frame_dynamo", DynamoTier.OPEN_FRAME),
                            dynamoRow("create_stats:kinetic_dynamo", DynamoTier.STANDARD),
                            dynamoRow("create_stats:industrial_alternator", DynamoTier.INDUSTRIAL))))
            .part(new Ratings(List.of(

                    new Rating(P + "rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(Configs.dynamoBuffer())}),

                    new Rating(P + "rating.hands_out", P + "value.every_face_together",
                            () -> new Object[]{FeRates.perTick(Configs.dynamoTransferRate())}),

                    new Rating(P + "dynamos.rating.smoke", P + "value.rpm",
                            () -> new Object[]{Num.of(KineticDynamoBlock.SMOKE_LOAD * KineticDynamoBlock.TOP_RPM)}))))

            .part(new Application(List.of(
                    new Link("create:shaft", P + "chain.shaft", false),
                    new Link("create_stats:kinetic_dynamo", P + "chain.dynamo", true),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "dynamos.application"))
            .part(new SeeAlso(List.of("biofuel_burner", "solar_panel", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("kinetic_dynamo", P + "part.part_designations", List.of(
                    new Control("shaft", P + "dynamos.control.shaft", P + "dynamos.function.shaft", null, null),
                    new Control("fan", P + "dynamos.control.fan", P + "dynamos.function.fan", null, null),
                    new Control("terminal", P + "dynamos.control.terminal", P + "dynamos.function.terminal",
                            null, null),
                    new Control("casing", P + "dynamos.control.casing", P + "dynamos.function.casing", null,
                            null))))
            .part(new World(List.of(
                    new Gesture(P + "gesture.wrench_sneak", P + "gesture.picks_it_up"),
                    new Gesture(P + "gesture.goggles", P + "dynamos.gesture.goggles"))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, OUTPUT, () -> new Object[]{dynamoOutput(DynamoTier.STANDARD)},
                            P + "dynamos.status.output", null),

                    new Word(Mark.IDLE, IDLE, P + "dynamos.status.idle", Symptom.READS_ZERO),

                    new Word(Mark.CAUTION, P + "goggle.kinetic_dynamo.capable",
                            () -> new Object[]{dynamoOutput(DynamoTier.STANDARD)}, P + "dynamos.status.capable",
                            Symptom.WRONG_NUMBER))))
            .part(new Faults(Symptom.READS_ZERO, P + "fault.reads_idle", List.of(

                    new Step(P + "dynamos.fault.fan_still", P + "dynamos.fault.no_rotation",
                            P + "dynamos.fault.no_rotation.note")),

                    P + "fault.nothing_draws", P + "dynamos.fault.nothing_draws.note", true))

            .part(new Figure("dynamo_grades", P + "dynamos.portrait", P + "dynamos.portrait.caption"))

            .part(new Note(NoteKind.CAUTION, P + "dynamos.caution"))
            .build();

    private static Grade dynamoRow(String id, DynamoTier tier) {
        final int rpm = KineticDynamoBlock.TOP_RPM;
        return new Grade(id, null, List.of(
                new Cell(P + "value.figure", () -> new Object[]{FeRates.perTick(dynamoOutput(tier))}),
                new Cell(P + "value.su", () -> new Object[]{
                        String.format(Locale.ROOT, "%,d", Math.round(tier.stressPerRpm() * rpm))}),
                new Cell(P + "value.figure", () -> new Object[]{
                        String.format(Locale.ROOT, "%.4f", tier.fePerStress())})));
    }

    public static final Sheet BIOFUEL_BURNER = Sheet.of("biofuel_burner", Section.POWER)
            .group(GENERATE)
            .title("block.create_stats.biofuel_burner")
            .covers("create_stats:biofuel_burner", "create_stats:compacted_biofuel")
            .hook(P + "biofuel_burner.hook")
            .part(new Figure("biofuel_burner", P + "part.parts_and_faces", P + "biofuel_burner.caption"))
            .part(new Ratings(List.of(
                    new Rating(P + "rating.output_burning", P + "value.figure",
                            () -> new Object[]{FeRates.perTick(BiofuelValue.OUTPUT_FE_PER_TICK)}),
                    new Rating(P + "rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(BiofuelBurnerBE.BUFFER)}),
                    new Rating(P + "rating.hands_out", P + "value.every_face_together",
                            () -> new Object[]{FeRates.perTick(BiofuelBurnerBE.OUTFLOW_RATE)}),
                    new Rating(P + "biofuel_burner.rating.mouth", P + "biofuel_burner.value.mouth",
                            () -> new Object[]{BiofuelBurnerBE.FUEL_CAPACITY}),

                    new Rating(P + "rating.comparator", P + "value.range",
                            () -> new Object[]{Redstone.SIGNAL_MIN, Redstone.SIGNAL_MAX}))))
            .part(new Application(List.of(
                    new Link("minecraft:hopper", P + "chain.feed", false),
                    new Link("create_stats:biofuel_burner", P + "chain.burner", true),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "biofuel_burner.application"))
            .part(new SeeAlso(List.of("kinetic_dynamo", "solar_panel", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("biofuel_burner", P + "part.part_designations", List.of(
                    new Control("door", P + "biofuel_burner.control.door", P + "biofuel_burner.function.door",
                            null, null),
                    new Control("mouth", P + "biofuel_burner.control.mouth", P + "biofuel_burner.function.mouth",
                            P + "keys.right_click", null),
                    new Control("flank", P + "biofuel_burner.control.flank", P + "biofuel_burner.function.flank",
                            null, null),
                    new Control("studs", P + "biofuel_burner.control.studs", P + "biofuel_burner.function.studs",
                            null, null))))
            .part(new World(List.of(
                    new Gesture(P + "biofuel_burner.gesture.empty_hand", P + "biofuel_burner.gesture.takes_back"),
                    new Gesture(P + "biofuel_burner.gesture.feeder", P + "biofuel_burner.gesture.fed"),
                    new Gesture(P + "gesture.comparator", P + "biofuel_burner.gesture.reads_mouth"),
                    new Gesture(P + "gesture.breaking", P + "biofuel_burner.gesture.drops"))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, P + "biofuel_burner.lamp.window", P + "biofuel_burner.status.window",
                            null),
                    new Word(Mark.RUNNING, OUTPUT, () -> new Object[]{BiofuelValue.OUTPUT_FE_PER_TICK},
                            P + "biofuel_burner.status.output", null),

                    new Word(Mark.RUNNING, P + "goggle.biofuel_burner.burning",
                            () -> new Object[]{BiofuelBurnerBE.clock(BiofuelGrade.LOG.burnTicks())},
                            P + "biofuel_burner.status.burning", null),
                    new Word(Mark.RUNNING, P + "goggle.biofuel_burner.auto_fed", P + "biofuel_burner.status.auto_fed",
                            null),

                    new Word(Mark.IDLE, P + "goggle.biofuel_burner.holding", P + "biofuel_burner.status.holding",
                            Symptom.RUNS_THEN_STOPS),

                    new Word(Mark.IDLE, P + "goggle.biofuel_burner.ready", P + "biofuel_burner.status.ready",
                            Symptom.WILL_NOT_START),
                    new Word(Mark.FAULT, P + "goggle.biofuel_burner.out_of_fuel",
                            P + "biofuel_burner.status.out_of_fuel", Symptom.RUNS_THEN_STOPS),

                    new Word(Mark.FAULT, "message.create_stats.biofuel_burner.not_bio",
                            P + "biofuel_burner.status.not_bio", Symptom.WILL_NOT_START))))
            .part(new Faults(Symptom.READS_ZERO, P + "biofuel_burner.fault.symptom", List.of(
                    new Step(P + "biofuel_burner.fault.mouth_empty", P + "biofuel_burner.fault.nothing_to_burn",
                            P + "biofuel_burner.fault.nothing_to_burn.note")),

                    P + "fault.nothing_draws", P + "biofuel_burner.fault.nothing_draws.note", true))

            .part(new Grades(P + "biofuel_burner.fuels", P + "biofuel_burner.fuels.note",
                    () -> new Object[]{FeRates.perTick(BiofuelValue.OUTPUT_FE_PER_TICK)},
                    List.of(new Cell(P + "column.burns", null), new Cell(P + "column.fe_each", null),
                            new Cell(P + "column.nine_press", () -> new Object[]{BiofuelGrade.PRESS_BATCH})),
                    fuelRows()))
            .part(new Note(NoteKind.NOTE, P + "biofuel_burner.note"))
            .build();

    private static List<Grade> fuelRows() {
        final List<Grade> out = new ArrayList<>();
        out.add(fuel("minecraft:short_grass", "green", BiofuelGrade.GREEN));
        out.add(fuel("minecraft:sugar_cane", "fibre", BiofuelGrade.FIBRE));
        out.add(fuel("minecraft:wheat", "crop", BiofuelGrade.CROP));
        out.add(fuel("minecraft:oak_planks", "plank", BiofuelGrade.PLANK));
        out.add(fuel("minecraft:melon", "packed", BiofuelGrade.PACKED));
        out.add(fuel("create_stats:compacted_biofuel", null, BiofuelGrade.PRESSED));
        out.add(fuel("minecraft:dried_kelp_block", "dense", BiofuelGrade.DENSE));
        out.add(fuel("minecraft:oak_log", "log", BiofuelGrade.LOG));
        out.add(fuel("minecraft:hay_block", "bale", BiofuelGrade.BALE));
        return out;
    }

    private static Grade fuel(String icon, String band, BiofuelGrade grade) {
        final String press = !grade.compacts() ? P + "biofuel_burner.press.none"
                : grade.pressedBricks() == 1 ? P + "biofuel_burner.press.brick" : P + "biofuel_burner.press.bricks";
        return new Grade(icon, band == null ? null : P + "biofuel_burner.band." + band, List.of(
                new Cell(P + "value.figure", () -> new Object[]{BiofuelValue.duration(grade.burnTicks())}),
                new Cell(P + "value.figure", () -> new Object[]{
                        FeRates.amount((int) BiofuelValue.totalFe(grade.burnTicks()))}),
                new Cell(press, () -> grade.compacts() ? new Object[]{grade.pressedBricks()} : new Object[0])));
    }

    static final int EXAMPLE_SKY_LIGHT = 12;

    static final long NOON = SolarCurve.DAYLIGHT_TICKS / 2;

    static int panelAlone(SolarTier tier) {
        return SolarCurve.fePerTick(tier.peakFePerTick(), 1, NOON, SolarCurve.Sky.CLEAR, SolarCurve.FULL_SKY_LIGHT);
    }

    static int roof(SolarTier tier) {
        final int n = SolarCurve.ARRAY_REFERENCE;
        return SolarCurve.fePerTick(tier.peakFePerTick(), n, NOON, SolarCurve.Sky.CLEAR, SolarCurve.FULL_SKY_LIGHT) * n;
    }

    static int dayAveragePercent() {
        double sum = 0.0;
        for (int t = 0; t < SolarCurve.DAY_TICKS; t++) {
            sum += SolarCurve.sunFactor(t);
        }
        return (int) Math.round(100.0 * sum / SolarCurve.DAY_TICKS);
    }

    private static String factor(double f) {
        return String.format(Locale.ROOT, "%.2f", f);
    }

    public static final Sheet SOLAR_PANELS = Sheet.of("solar_panel", Section.POWER)
            .group(GENERATE)
            .title(P + "solar.title")
            .covers("create_stats:solar_panel", "create_stats:reinforced_solar_panel", "create_stats:photovoltaic_array")
            .icon("create_stats:reinforced_solar_panel")
            .hook(P + "solar.hook")
            .part(new Figure("solar_panel", P + "part.parts_and_faces", P + "solar.caption"))
            .part(new Grades(P + "part.grades", P + "solar.grades.note", null,
                    List.of(new Cell(P + "column.scale", null), new Cell(P + "column.alone", null),
                            new Cell(P + "column.roof_of", () -> new Object[]{SolarCurve.ARRAY_REFERENCE})),
                    List.of(solarRow("create_stats:solar_panel", SolarTier.BASIC),
                            solarRow("create_stats:reinforced_solar_panel", SolarTier.REINFORCED),
                            solarRow("create_stats:photovoltaic_array", SolarTier.PHOTOVOLTAIC))))
            .part(new Ratings(List.of(
                    new Rating(P + "solar.rating.bonus", P + "solar.value.bonus", () -> new Object[]{
                            factor(SolarCurve.arrayMultiplier(SolarCurve.ARRAY_REFERENCE)),
                            SolarCurve.ARRAY_REFERENCE}),
                    new Rating(P + "solar.rating.bonus_most", P + "solar.value.bonus", () -> new Object[]{
                            factor(SolarCurve.ARRAY_CAP), SolarCurve.ARRAY_CAP_PANELS}),
                    new Rating(P + "solar.rating.daylight", P + "solar.value.daylight", () -> new Object[]{
                            Num.of(SolarCurve.DAYLIGHT_TICKS / (SharedConstants.TICKS_PER_SECOND * 60.0)),
                            Num.of(SolarCurve.DAY_TICKS / (SharedConstants.TICKS_PER_SECOND * 60.0))}),
                    new Rating(P + "solar.rating.average", P + "solar.value.of_noon",
                            () -> new Object[]{dayAveragePercent()}),
                    new Rating(P + "solar.rating.rain", P + "value.times",
                            () -> new Object[]{factor(SolarCurve.Sky.RAIN.factor())}),
                    new Rating(P + "solar.rating.thunder", P + "value.times",
                            () -> new Object[]{factor(SolarCurve.Sky.THUNDER.factor())}),
                    new Rating(P + "rating.buffer", P + "value.figure",
                            () -> new Object[]{FeRates.amount(SolarPanelBE.BUFFER)}))))
            .part(new Application(List.of(
                    new Link("create_stats:reinforced_solar_panel", P + "chain.roof", true),
                    new Link("create_stats:surface_cable", P + "chain.cable", false),
                    new Link("create_stats:capacitor_bank", P + "chain.bank", false),
                    new Link("create_stats:induction_smelter", P + "chain.load", false)),
                    P + "solar.application"))
            .part(new SeeAlso(List.of("kinetic_dynamo", "biofuel_burner", "power_meter")))
            .part(new Part.Break(P + "kind.controls_and_status"))
            .part(new Controls("solar_panel", P + "part.part_designations", List.of(
                    new Control("cells", P + "solar.control.cells", P + "solar.function.cells", null, null),
                    new Control("rail", P + "solar.control.rail", P + "solar.function.rail", null, null),
                    new Control("lone", P + "solar.control.lone", P + "solar.function.lone", null, null),
                    new Control("edge", P + "solar.control.edge", P + "solar.function.edge", null, null))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, OUTPUT, () -> new Object[]{SolarCurve.fePerTick(
                            SolarTier.REINFORCED.peakFePerTick(), SolarCurve.ARRAY_REFERENCE, NOON,
                            SolarCurve.Sky.CLEAR, SolarCurve.FULL_SKY_LIGHT)},
                            P + "solar.status.output", null),
                    new Word(Mark.RUNNING, P + "goggle.solar_panel.array", () -> new Object[]{
                            SolarCurve.ARRAY_REFERENCE, factor(SolarCurve.arrayMultiplier(SolarCurve.ARRAY_REFERENCE))},
                            P + "solar.status.array", null),
                    new Word(Mark.IDLE, IDLE, P + "solar.status.idle", Symptom.READS_ZERO),

                    new Word(Mark.IDLE, P + "goggle.solar_panel.night", P + "solar.status.night", Symptom.READS_ZERO),
                    new Word(Mark.IDLE, P + "goggle.solar_panel.holding", () -> new Object[]{
                            panelAlone(SolarTier.REINFORCED)}, P + "solar.status.holding", Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.FAULT, P + "goggle.solar_panel.covered", P + "solar.status.covered",
                            Symptom.READS_ZERO),
                    new Word(Mark.FAULT, P + "goggle.solar_panel.no_sky", P + "solar.status.no_sky",
                            Symptom.READS_ZERO),

                    new Word(Mark.CAUTION, P + "goggle.solar_panel.shaded", () -> new Object[]{
                            EXAMPLE_SKY_LIGHT * 100 / SolarCurve.FULL_SKY_LIGHT}, P + "solar.status.shaded",
                            Symptom.WRONG_NUMBER),
                    new Word(Mark.CAUTION, P + "goggle.solar_panel.raining", () -> new Object[]{
                            factor(SolarCurve.Sky.RAIN.factor())}, P + "solar.status.raining", Symptom.WRONG_NUMBER),
                    new Word(Mark.CAUTION, P + "goggle.solar_panel.thunder", () -> new Object[]{
                            factor(SolarCurve.Sky.THUNDER.factor())}, P + "solar.status.thunder", Symptom.WRONG_NUMBER),

                    new Word(Mark.CAUTION, P + "goggle.solar_panel.too_little", P + "solar.status.too_little",
                            Symptom.READS_ZERO))))
            .part(new Faults(Symptom.READS_ZERO, P + "fault.reads_idle", List.of(
                    new Step(P + "solar.fault.says_night", P + "solar.fault.sun_down", P + "solar.fault.sun_down.note"),
                    new Step(P + "solar.fault.shaded", P + "solar.fault.no_light", P + "solar.fault.no_light.note")),
                    P + "fault.nothing_draws", P + "solar.fault.nothing_draws.note", true))

            .part(new Part.Curve(P + "solar.curve", P + "solar.curve.note",
                    () -> new Object[]{SolarCurve.ARRAY_REFERENCE}, SolarCurve.DAY_TICKS, List.of(
                            new Part.Tick(0, P + "time.sunrise"),
                            new Part.Tick(NOON, P + "time.noon"),
                            new Part.Tick(SolarCurve.DAYLIGHT_TICKS, P + "time.sunset"),
                            new Part.Tick((SolarCurve.DAYLIGHT_TICKS + SolarCurve.DAY_TICKS) / 2.0, P + "time.midnight"),
                            new Part.Tick(SolarCurve.DAY_TICKS, P + "time.sunrise")),
                    P + "value.fe_t", List.of(
                            new Part.Line(P + "solar.curve.clear", t -> roofAt((long) t, SolarCurve.Sky.CLEAR),
                                    Part.LineStyle.SUBJECT),
                            new Part.Line(P + "solar.curve.rain", t -> roofAt((long) t, SolarCurve.Sky.RAIN),
                                    Part.LineStyle.DOTTED),
                            new Part.Line(P + "solar.curve.dynamo", t -> dynamoOutput(DynamoTier.STANDARD),
                                    Part.LineStyle.REFERENCE))))
            .build();

    static int roofAt(long dayTime, SolarCurve.Sky sky) {
        final int n = SolarCurve.ARRAY_REFERENCE;
        return SolarCurve.fePerTick(SolarTier.REINFORCED.peakFePerTick(), n, dayTime, sky, SolarCurve.FULL_SKY_LIGHT) * n;
    }

    private static Grade solarRow(String id, SolarTier tier) {
        return new Grade(id, null, List.of(
                new Cell(P + "value.times", () -> new Object[]{factor(tier.outputScale())}),
                new Cell(P + "value.figure", () -> new Object[]{FeRates.perTick(panelAlone(tier))}),
                new Cell(P + "value.figure", () -> new Object[]{FeRates.perTick(roof(tier))})));
    }

    public static List<Sheet> all() {
        return List.of(DYNAMOS, BIOFUEL_BURNER, SOLAR_PANELS);
    }
}
