package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.ControlSheets;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.StoreSheets;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.Ink;
import com.createstats.book.layout.Page;
import com.createstats.book.specimen.BatteryPlates;
import com.createstats.book.specimen.PduPlates;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.book.specimen.Specimens;
import com.createstats.gui.battery.BatteryPages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabbedPlatesTest {
    private static final Map<String, Integer> WRITTEN_SO_FAR = Map.of();

    private static final java.util.Set<String> SECOND_PLATES = java.util.Set.of(BatteryPlates.BAND);

    private static List<Part.Figure> plates(Sheet sheet) {
        final List<Part.Figure> out = new ArrayList<>();
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.Figure f && f.tab() != null) {
                out.add(f);
            }
        }
        return out;
    }

    @Test
    void everyTabIsAPlateInTheScreensOrder() throws IOException {
        final BookText t = BookFixtures.text(BookFixtures.lang());
        int screens = 0;
        for (final Sheet sheet : Book.sheets()) {
            final List<Part.Figure> plates = plates(sheet);
            if (plates.isEmpty()) {
                continue;
            }
            screens++;
            final List<String> names = plates.get(0).tab().names();
            final int seconds = (int) plates.stream().filter(f -> SECOND_PLATES.contains(f.specimen())).count();
            assertEquals((int) WRITTEN_SO_FAR.getOrDefault(sheet.id(), names.size()), plates.size() - seconds,
                    sheet.id() + ": one plate a tab");
            assertTrue(plates.size() - seconds <= names.size(), sheet.id() + ": no more plates than tabs");
            int tabAt = 0;
            for (int i = 0; i < plates.size(); i++) {
                final Part.Figure f = plates.get(i);
                if (SECOND_PLATES.contains(f.specimen())) {
                    assertTrue(i > 0 && plates.get(i - 1).tab().index() == f.tab().index(),
                            sheet.id() + " " + f.specimen() + ": a second plate sits beside its tab's own");
                } else {
                    assertEquals(tabAt++, f.tab().index(), sheet.id() + ": plates follow the screen's tab order");
                }
                assertEquals(names, f.tab().names(), sheet.id() + ": every plate carries the same strip");
                final int at = sheet.parts().indexOf(f);
                assertTrue(at > 0 && sheet.parts().get(at - 1) instanceof Part.Break b && b.left(),
                        sheet.id() + " " + f.specimen() + ": a plate opens on a left sheet of its own");
                final Part.Break b = (Part.Break) sheet.parts().get(at - 1);
                assertTrue(t.get(b.bandKey(), b.argsOrNone()).contains(f.tab().name()),
                        sheet.id() + " " + f.specimen() + ": its band names its tab");
                assertTrue(sheet.parts().stream().anyMatch(p -> p instanceof Part.Controls c
                        && c.specimen().equals(f.specimen())), sheet.id() + " " + f.specimen() + ": a table");
            }
        }
        assertTrue(screens >= 1, "no screen with tabs is drawn as plates");

        final List<String> expected = new ArrayList<>();
        for (final BatteryPages.Tab tab : BatteryPages.Tab.values()) {
            expected.add(tab.label.toUpperCase(Locale.ROOT));
        }
        assertEquals(expected, plates(StoreSheets.BATTERY_CONTROLLER).get(0).tab().names());
        assertEquals(BatteryPlates.IDS, plates(StoreSheets.BATTERY_CONTROLLER).stream().map(Part.Figure::specimen)
                .toList());

        final List<String> views = new ArrayList<>();
        for (final String title : com.createstats.gui.PowerDistributionUnitScreen.viewTitles()) {
            views.add(title.toUpperCase(Locale.ROOT));
        }
        assertEquals(views, plates(ControlSheets.PDU).get(0).tab().names());
        assertEquals(PduPlates.IDS, plates(ControlSheets.PDU).stream().map(Part.Figure::specimen).toList());
        for (final String id : PduPlates.IDS) {
            assertEquals(PduPlates.view(id), plates(ControlSheets.PDU).get(PduPlates.IDS.indexOf(id)).tab().index(),
                    id + ": its plate is its view's tab");
        }
    }

    @Test
    void everyPlatePinsItsOwnTab() {
        for (final String id : BatteryPlates.IDS) {
            final SpecimenAnatomy a = Specimens.anatomy(id).orElseThrow();
            assertTrue(a.controls().stream().anyMatch(h -> h.id().equals("tab")), id + " pins the tab it opens on");
        }

        final List<List<String>> ids = new ArrayList<>();
        for (final String id : BatteryPlates.IDS) {
            ids.add(Specimens.anatomy(id).orElseThrow().controls().stream().map(SpecimenAnatomy.Hot::id).sorted()
                    .toList());
        }
        assertTrue(!ids.get(0).equals(ids.get(1)) && !ids.get(1).equals(ids.get(2)) && !ids.get(0).equals(ids.get(2)),
                "each plate pins its own tab's controls: " + ids);

        final List<List<String>> views = new ArrayList<>();
        for (final String id : PduPlates.IDS) {
            final SpecimenAnatomy a = Specimens.anatomy(id).orElseThrow();
            assertTrue(a.controls().stream().anyMatch(h -> h.id().equals("views")), id + " pins the view it shows");
            views.add(a.controls().stream().map(SpecimenAnatomy.Hot::id).sorted().toList());
        }
        assertEquals(views.size(), new java.util.HashSet<>(views).size(), "each view's plate pins its own: " + views);
    }
}
