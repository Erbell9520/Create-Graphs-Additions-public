package com.createstats.book.content;

import com.createstats.book.content.Part.Term;
import com.createstats.book.content.Part.Terms;
import com.createstats.book.content.Part.Unlisted;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.redstone.Redstone;

import java.util.List;

public final class BackMatter {
    private BackMatter() {
    }

    private static final String BACK = "create_stats.book.group.at_the_back";

    public static final String INDEX_ID = "troubleshooting";

    public static final Sheet INDEX = Sheet.of(INDEX_ID, Section.FAULTS)
            .group(BACK)
            .title("create_stats.book.index.title")
            .hook(Section.FAULTS.hookKey())
            .part(new Part.FaultIndex())
            .build();

    public static final Sheet GLOSSARY = Sheet.of("glossary", Section.FAULTS)
            .group(BACK)
            .title("create_stats.book.glossary.title")
            .loose()
            .hook("create_stats.book.glossary.hook")
            .part(new Terms("create_stats.book.part.terms", List.of(
                    term("fe"), term("fe_per_tick"),
                    new Term("create_stats.book.term.tick", "create_stats.book.term.tick.means",
                            () -> new Object[]{SharedConstants.TICKS_PER_SECOND}),
                    term("rate"), term("peak"), term("supply"), term("load"), term("buffer"),
                    new Term("create_stats.book.term.comparator", "create_stats.book.term.comparator.means",
                            () -> new Object[]{Redstone.SIGNAL_MIN, Redstone.SIGNAL_MAX}),
                    term("display_link"), term("goggles"), term("ponder"), term("wrench")), true))
            .build();

    private static Term term(String id) {
        return new Term("create_stats.book.term." + id, "create_stats.book.term." + id + ".means");
    }

    public static final Sheet RETIRED = Sheet.of("retired", Section.FAULTS)
            .group(BACK)
            .title("create_stats.book.retired.title")
            .loose()
            .hook("create_stats.book.retired.hook")
            .part(new Part.Retired(List.of(
                    new Unlisted("create_stats:stats_alarm", true, "create_stats.book.retired.stats_alarm"),
                    new Unlisted("create_stats:stats_recorder", true, "create_stats.book.retired.stats_recorder"),
                    new Unlisted("create_stats:memory_cog", true, "create_stats.book.retired.memory_cog"),
                    new Unlisted("create_stats:flour", true, "create_stats.book.retired.flour"),
                    new Unlisted("create_stats:suction_wand", false, "create_stats.book.retired.suction_wand"))))
            .build();

    public static final Sheet A_TO_Z = Sheet.of("a_to_z", Section.FAULTS)
            .group(BACK)
            .title("create_stats.book.a_to_z.title")
            .loose()
            .hook("create_stats.book.a_to_z.hook")
            .part(new Part.AtoZ())
            .build();

    public static List<Sheet> all() {
        return List.of(INDEX, GLOSSARY, RETIRED, A_TO_Z);
    }
}
