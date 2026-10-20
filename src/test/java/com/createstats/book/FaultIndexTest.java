package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.FaultIndex;
import com.createstats.book.content.Part;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.Symptom;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.IndexSearch;
import com.createstats.book.layout.IndexState;
import com.createstats.book.layout.Page;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultIndexTest {
    private static final String NO_SUPPLY = "gui.create_stats.power_meter.state.no_supply";

    private static BookText text() throws IOException {
        return BookFixtures.text(BookFixtures.lang());
    }

    @Test
    void everyWordAndCauseIsIndexed() {
        final List<String> expected = new ArrayList<>();
        for (final Sheet sheet : Book.sheets()) {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Status s) {
                    s.rows().forEach(w -> expected.add(sheet.id() + " " + w.wordKey()));
                }
                if (p instanceof Part.Faults f) {
                    f.steps().forEach(st -> expected.add(sheet.id() + " " + st.yesKey()));
                    if (f.endCause()) {
                        expected.add(sheet.id() + " " + f.endKey());
                    }
                }
            }
        }
        final List<String> indexed = FaultIndex.entries().stream().map(e -> e.sheet().id() + " " + e.headKey()).toList();
        assertEquals(expected, indexed);
        assertTrue(indexed.size() >= 7, "the Power Meter alone gives six words and a cause");
    }

    @Test
    void theMeterIsFiledByWhatThePlayerSees() {
        final List<FaultIndex.Entry> meter = FaultIndex.entries().stream()
                .filter(e -> e.sheet().id().equals("power_meter")).toList();
        assertEquals(Symptom.NO_SUPPLY, symptomOf(meter, NO_SUPPLY));
        assertEquals(Symptom.READS_ZERO, symptomOf(meter, "gui.create_stats.power_meter.state.no_load"));
        assertEquals(Symptom.READS_ZERO, symptomOf(meter, "gui.create_stats.power_meter.state.looped"));
        assertEquals(Symptom.READS_ZERO, symptomOf(meter, "gui.create_stats.power_meter.state.idle"));
        assertEquals(Symptom.RUNS_THEN_STOPS, symptomOf(meter, "gui.create_stats.power_meter.state.backed_up"));
        assertEquals(null, symptomOf(meter, "gui.create_stats.power_meter.state.flowing"));
        assertEquals(Symptom.READS_ZERO, symptomOf(meter, "create_stats.book.power_meter.fault.finished"));
    }

    private static Symptom symptomOf(List<FaultIndex.Entry> entries, String head) {
        return entries.stream().filter(e -> e.headKey().equals(head)).findFirst().orElseThrow().symptom();
    }

    @Test
    void searchFindsTheMachinesOwnWords() throws IOException {
        final BookText t = text();
        for (final String q : List.of("no supply", "NO SUPPLY", "supply, no", "  No   Supply ")) {
            final IndexSearch.Result r = IndexSearch.run(t, IndexState.searching(q));
            assertTrue(r.entries().stream().anyMatch(e -> e.headKey().equals(NO_SUPPLY)), q + " should find NO SUPPLY");
        }
        assertTrue(IndexSearch.run(t, IndexState.searching("supply zebra")).entries().isEmpty(),
                "every word typed must match");
        assertEquals("shed critical only", IndexSearch.normalise("Shed: Critical only!"));
    }

    @Test
    void atRestEveryRowShowsInSymptomOrder() throws IOException {
        final IndexSearch.Result r = IndexSearch.run(text(), IndexState.REST);
        assertEquals(FaultIndex.entries().size(), r.entries().size());
        assertTrue(r.sheets().isEmpty(), "sheets are a search result, not a listing");
        int last = -1;
        for (final FaultIndex.Entry e : r.entries()) {
            final int rank = e.symptom() == null ? Symptom.values().length : e.symptom().ordinal();
            assertTrue(rank >= last, "rows out of symptom order at " + e.headKey());
            last = rank;
        }
    }

    @Test
    void sheetsFollowRowsAndSymptomsNarrow() throws IOException {
        final BookText t = text();
        final IndexSearch.Result meter = IndexSearch.run(t, IndexState.searching("power meter"));
        assertFalse(meter.entries().isEmpty());
        assertEquals(List.of("power_meter"), meter.sheets().stream().map(Sheet::id).toList());
        assertEquals(meter.entries().size() + 1, meter.total());

        final IndexSearch.Result flowing = IndexSearch.run(t, IndexState.searching("flowing"));
        assertTrue(flowing.sheets().stream().anyMatch(s -> s.id().equals("power_meter")),
                "a sheet is found by the words its machine shows");

        final IndexSearch.Result zero = IndexSearch.run(t,
                IndexState.searching("power meter").withSymptom(Symptom.READS_ZERO));
        assertTrue(zero.sheets().isEmpty());
        assertTrue(zero.entries().stream().allMatch(e -> e.symptom() == Symptom.READS_ZERO));
        assertEquals(meter.count(Symptom.READS_ZERO), zero.entries().size(),
                "a symptom's count on the left is how many rows picking it shows");
    }

    @Test
    void sectionChipsFilter() throws IOException {
        final BookText t = text();
        final IndexSearch.Result power = IndexSearch.run(t, IndexState.REST.withSection(Section.POWER));
        assertTrue(power.entries().stream().allMatch(e -> e.sheet().section() == Section.POWER));
        assertFalse(power.entries().isEmpty());
        final IndexSearch.Result stats = IndexSearch.run(t, IndexState.searching("meter").withSection(Section.STATS));
        assertEquals(0, stats.total());
        assertTrue(power.indexed().contains(Section.POWER));
    }
}
