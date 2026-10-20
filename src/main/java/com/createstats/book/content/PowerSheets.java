package com.createstats.book.content;

import com.createstats.blockentity.PowerMeterBE;
import com.createstats.book.content.Part.Application;
import com.createstats.book.content.Part.Control;
import com.createstats.book.content.Part.Controls;
import com.createstats.book.content.Part.Faults;
import com.createstats.book.content.Part.Figure;
import com.createstats.book.content.Part.Link;
import com.createstats.book.content.Part.Rating;
import com.createstats.book.content.Part.Ratings;
import com.createstats.book.content.Part.SeeAlso;
import com.createstats.book.content.Part.Status;
import com.createstats.book.content.Part.Step;
import com.createstats.book.content.Part.Word;
import com.createstats.gui.meter.MeterHistory;
import com.createstats.gui.meter.plate.HoldGesture;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.redstone.Redstone;

import java.util.ArrayList;
import java.util.List;

public final class PowerSheets {
    private PowerSheets() {
    }

    public static final Sheet POWER_METER = Sheet.of("power_meter", Section.POWER)
            .group("create_stats.book.group.control")
            .title("block.create_stats.power_meter")
            .covers("create_stats:power_meter")
            .hook("create_stats.book.power_meter.hook")
            .part(new Figure("power_meter", "create_stats.book.part.terminal_designations",
                    "create_stats.book.caption.example_readings"))
            .part(new Ratings(List.of(

                    new Rating("create_stats.book.power_meter.rating.trace", "create_stats.book.value.samples_a_span",
                            () -> new Object[]{MeterHistory.SIZE}),

                    new Rating("create_stats.book.power_meter.rating.spans", "create_stats.book.value.minutes",
                            () -> new Object[]{spanMinutes()}),

                    new Rating("create_stats.book.power_meter.rating.reset", "create_stats.book.value.hold_or_sneak",
                            () -> new Object[]{Num.of(HoldGesture.TICKS / (double) SharedConstants.TICKS_PER_SECOND)}),

                    new Rating("create_stats.book.power_meter.rating.comparator", "create_stats.book.value.range",
                            () -> new Object[]{Redstone.SIGNAL_MIN, Redstone.SIGNAL_MAX}),

                    new Rating("create_stats.book.power_meter.rating.updates", "create_stats.book.value.per_second_flowing",
                            () -> new Object[]{Num.of(SharedConstants.TICKS_PER_SECOND
                                    / (double) PowerMeterBE.READING_SYNC_FLOOR_TICKS)})
            )))
            .part(new Application(List.of(
                    new Link("create_stats:kinetic_dynamo", "create_stats.book.chain.supply", false),
                    new Link("create_stats:surface_cable", "create_stats.book.chain.cable", false),
                    new Link("create_stats:power_meter", "create_stats.book.chain.meter", true),
                    new Link("create_stats:surface_cable", "create_stats.book.chain.cable", false),
                    new Link("create_stats:induction_smelter", "create_stats.book.chain.load", false)),

                    "create_stats.book.power_meter.application"))
            .part(new SeeAlso(List.of("power_limiter", "main_breaker")))
            .part(new Part.Break("create_stats.book.kind.controls_and_status"))
            .part(new Controls("power_meter", List.of(
                    new Control("terminal", "create_stats.book.power_meter.control.terminal",
                            "create_stats.book.power_meter.function.terminal", null, null),
                    new Control("keys", "create_stats.book.power_meter.control.keys",
                            "create_stats.book.power_meter.function.keys", "create_stats.book.keys.number_keys",
                            () -> new Object[]{rangeKeys()}),
                    new Control("peak", "gui.create_stats.power_meter.tip.peak",
                            "gui.create_stats.power_meter.tip.peak.body", null, null),
                    new Control("stud", "create_stats.book.power_meter.control.stud",
                            "gui.create_stats.power_meter.tip.stud.body", "create_stats.book.keys.hold_seconds",
                            () -> new Object[]{Num.of(HoldGesture.TICKS / (double) SharedConstants.TICKS_PER_SECOND)}),
                    new Control("carried", "gui.create_stats.power_meter.tip.carried",
                            "gui.create_stats.power_meter.tip.carried.body", null, null),
                    new Control("rate", "create_stats.book.power_meter.control.rate",
                            "gui.create_stats.power_meter.tip.rate.body", null, null),
                    new Control("gland", "create_stats.book.power_meter.control.gland",
                            "create_stats.book.power_meter.function.gland", null, null),
                    new Control("trace", "create_stats.book.power_meter.control.trace",
                            "create_stats.book.power_meter.function.trace", null, null),
                    new Control("comparator", "create_stats.book.power_meter.control.comparator",
                            "gui.create_stats.power_meter.tip.comparator.body", null, null),
                    new Control("load", "gui.create_stats.power_meter.tip.load",
                            "gui.create_stats.power_meter.tip.load.body", null, null))))
            .part(new Status(List.of(

                    new Word(Mark.RUNNING, "gui.create_stats.power_meter.state.flowing",
                            "create_stats.book.power_meter.status.flowing", null),
                    new Word(Mark.IDLE, "gui.create_stats.power_meter.state.idle",
                            "create_stats.book.power_meter.status.idle", Symptom.READS_ZERO),
                    new Word(Mark.CAUTION, "gui.create_stats.power_meter.state.backed_up",
                            "create_stats.book.power_meter.status.backed_up", Symptom.RUNS_THEN_STOPS),
                    new Word(Mark.FAULT, "gui.create_stats.power_meter.state.no_supply",
                            "create_stats.book.power_meter.status.no_supply", Symptom.NO_SUPPLY),
                    new Word(Mark.FAULT, "gui.create_stats.power_meter.state.no_load",
                            "create_stats.book.power_meter.status.no_load", Symptom.READS_ZERO),
                    new Word(Mark.FAULT, "gui.create_stats.power_meter.state.looped",
                            "create_stats.book.power_meter.status.looped", Symptom.READS_ZERO))))
            .part(new Faults(Symptom.READS_ZERO, "create_stats.book.power_meter.fault.symptom", List.of(

                    new Step("create_stats.book.power_meter.fault.buffer_full",
                            "create_stats.book.power_meter.fault.finished",
                            "create_stats.book.power_meter.fault.finished.note")),

                    "create_stats.book.power_meter.fault.status_word",
                    "create_stats.book.power_meter.fault.status_word.note", false))
            .build();

    static String spanMinutes() {
        final List<String> out = new ArrayList<>();
        for (final int cadence : MeterHistory.CADENCE) {
            out.add(Num.of(MeterHistory.SIZE * (double) cadence / (SharedConstants.TICKS_PER_SECOND * 60.0)));
        }
        return String.join(", ", out);
    }

    static String rangeKeys() {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < MeterHistory.CADENCE.length; i++) {
            out.add(String.valueOf(i + 1));
        }
        return String.join(", ", out);
    }

    public static List<Sheet> all() {
        final List<Sheet> out = new ArrayList<>(GenerateSheets.all());
        out.addAll(StoreSheets.all());
        out.addAll(CarrySheets.before());
        out.addAll(WireSheets.all());
        out.addAll(CarrySheets.after());

        out.addAll(ControlSheets.all());
        out.add(POWER_METER);
        return List.copyOf(out);
    }
}
