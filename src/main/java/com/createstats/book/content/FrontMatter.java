package com.createstats.book.content;

import com.createstats.book.content.Part.Application;
import com.createstats.book.content.Part.Control;
import com.createstats.book.content.Part.Controls;
import com.createstats.book.content.Part.Figure;
import com.createstats.book.content.Part.Instruction;
import com.createstats.book.content.Part.Link;
import com.createstats.book.content.Part.Note;
import com.createstats.book.content.Part.NoteKind;
import com.createstats.book.content.Part.Rating;
import com.createstats.book.content.Part.Ratings;
import com.createstats.book.content.Part.SeeAlso;
import com.createstats.book.content.Part.Steps;
import com.createstats.book.content.Part.Term;
import com.createstats.book.content.Part.Terms;

import java.util.List;

public final class FrontMatter {
    private FrontMatter() {
    }

    private static final String START = "create_stats.book.group.start_here";

    public static final Sheet HOW_TO_READ = Sheet.of("how_to_read", Section.BASICS)
            .group(START)
            .title("create_stats.book.how_to_read.title")
            .covers("create_stats:students_manual")
            .hook("create_stats.book.how_to_read.hook")
            .part(new Figure("data_sheet", "create_stats.book.part.parts_of_a_spread",
                    "create_stats.book.how_to_read.caption"))
            .part(new Ratings("create_stats.book.part.this_book", List.of(
                    new Rating("create_stats.book.how_to_read.rating.sections", "create_stats.book.value.count",
                            () -> new Object[]{Section.values().length}),
                    new Rating("create_stats.book.how_to_read.rating.sheets", "create_stats.book.value.count",
                            () -> new Object[]{Book.blockSheets().size()}),
                    new Rating("create_stats.book.how_to_read.rating.faults", "create_stats.book.value.count",
                            () -> new Object[]{FaultIndex.entries().size()}))))
            .part(new Part.Break("create_stats.book.kind.parts_and_marks"))
            .part(new Controls("data_sheet", "create_stats.book.part.part_designations", List.of(
                    part("band"), part("ribbon"), part("title"), part("tabs"), part("figure"), part("ratings"),
                    part("application"), part("see_also"), part("controls"), part("status"), part("faults"),
                    part("page"))))
            .part(new Part.Marks("create_stats.book.part.status_marks"))
            .build();

    private static Control part(String id) {
        return new Control(id, "create_stats.book.how_to_read.part." + id,
                "create_stats.book.how_to_read.part." + id + ".does", null, null);
    }

    public static final Sheet ONE_IDEA = Sheet.of("one_idea", Section.BASICS)
            .group(START)
            .title("create_stats.book.one_idea.title")
            .icon("create_stats:stats_display_mk3")
            .hook("create_stats.book.one_idea.hook")
            .loose()
            .part(new Application("create_stats.book.part.where_a_reading_goes", List.of(
                    new Link("create_stats:power_meter", "create_stats.book.verb.measure", false),
                    new Link("create:display_link", "create_stats.book.verb.move", false),
                    new Link("create_stats:stats_display_mk3", "create_stats.book.verb.show", false),
                    new Link("create_stats:klaxon_alarm", "create_stats.book.verb.act", false)),
                    "create_stats.book.one_idea.chain"))
            .part(new Terms("create_stats.book.part.four_verbs", List.of(
                    new Term("create_stats.book.verb.measure", "create_stats.book.one_idea.measure"),
                    new Term("create_stats.book.verb.move", "create_stats.book.one_idea.move"),
                    new Term("create_stats.book.verb.show", "create_stats.book.one_idea.show"),
                    new Term("create_stats.book.verb.act", "create_stats.book.one_idea.act")), false))
            .part(new Note(NoteKind.NOTE, "create_stats.book.one_idea.note"))
            .part(new SeeAlso(List.of("power_meter")))
            .build();

    public static final Sheet FIRST_READING = Sheet.of("first_reading", Section.BASICS)
            .group(START)
            .title("create_stats.book.first_reading.title")
            .icon("create_stats:power_meter")
            .hook("create_stats.book.first_reading.hook")
            .loose()
            .part(new Steps("create_stats.book.part.step_by_step", List.of(
                    new Instruction("create_stats.book.first_reading.step.place", "create_stats:power_meter"),
                    new Instruction("create_stats.book.first_reading.step.supply", "create_stats:surface_cable"),
                    new Instruction("create_stats.book.first_reading.step.open", null),
                    new Instruction("create_stats.book.first_reading.step.read", null,
                            List.of("gui.create_stats.power_meter.state.flowing")),
                    new Instruction("create_stats.book.first_reading.step.else", "create_stats:students_manual"))))
            .part(new Note(NoteKind.NOTE, "create_stats.book.first_reading.note"))
            .part(new SeeAlso(List.of("power_meter")))
            .build();

    public static final Terms FINDING_YOUR_WAY = new Terms("create_stats.book.part.finding_your_way", List.of(
            way("open"), way("sneak"), way("tabs"), way("turn"), way("back"), way("search"), way("pins"),
            way("ponder"), way("close")), false);

    private static Term way(String id) {
        return new Term("create_stats.book.way." + id, "create_stats.book.way." + id + ".does");
    }

    public static List<Sheet> all() {
        return List.of(HOW_TO_READ, ONE_IDEA, FIRST_READING);
    }
}
