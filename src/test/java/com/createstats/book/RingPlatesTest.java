package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.Part;
import com.createstats.book.content.Sheet;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Callouts;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.Page;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockView;
import com.createstats.book.scene.FigureWorld;
import com.createstats.book.scene.WireFigures;
import com.createstats.book.specimen.RingPlates;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.client.wire.WireRing;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingPlatesTest {
    @Test
    void theRingShowsTheHubsFourWires() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        final List<WireRing.Panel> panels = RingPlates.panels();
        assertEquals(WireTier.values().length, panels.size());
        final List<String> levels = new ArrayList<>();
        for (final WireRing.Panel p : panels) {
            levels.add(p.level());
        }
        assertEquals(List.of(lang.get("create_stats.wire_tension.slack"), lang.get("create_stats.wire_tension.standard"),
                lang.get("create_stats.wire_tension.taut"), lang.get("create_stats.wire_tension.drum")), levels);
        final FigureWorld w = FigureWorld.of(WireFigures.RING_WORLD);
        for (int i = 0; i < panels.size(); i++) {
            final BlockFigure.Tie s = w.spans().get(i);
            final WireRing.Panel p = panels.get(i);
            assertTrue(java.util.Arrays.equals(s.tier().strandA(), p.swatch()), "the swatch is the wire's colour");
            assertEquals(String.format(Locale.ROOT, lang.get(WireRing.SPAN_KEY),
                    WireRing.spanArgs(w.spanLength(s), s.tension())), p.span(), "the length and sag of its own span");
            assertEquals(s.tension().ordinal(), p.pip());
        }
        assertEquals(lang.get(WireRing.MIXED_KEY), RingPlates.hubSub(), "four levels: the hub says Mixed");

        final Set<WireTier> tiers = new TreeSet<>();
        final Set<WireTension> tensions = new TreeSet<>();
        for (final BlockFigure.Tie s : WireFigures.HUB_SPANS) {
            tiers.add(s.tier());
            tensions.add(s.tension());
        }
        assertEquals(Set.of(WireTier.values()), tiers);
        assertEquals(Set.of(WireTension.values()), tensions);
    }

    @Test
    void thePanelsPointTheWayTheirWiresLeave() {
        final BlockFigure f = WireFigures.RING_WORLD;
        final FigureWorld w = FigureWorld.of(f);
        final BlockView v = f.view();
        final List<WireRing.Panel> panels = RingPlates.panels();
        for (int i = 0; i < panels.size(); i++) {
            final List<Vec3> points = w.spanPoints(w.spans().get(i));
            final Vec3 a = points.get(0);
            final Vec3 b = points.get(points.size() - 1);
            final double[] pa = v.project(a.x, a.y, a.z);
            final double[] pb = v.project(b.x, b.y, b.z);

            final double drawn = Math.atan2(pb[1] - pa[1], pb[0] - pa[0]);
            final double gap = com.createstats.client.wire.WireRingLayout.between(drawn, panels.get(i).angle());

            assertTrue(gap <= WireRing.MIN_GAP, "panel " + i + " points " + Math.toDegrees(gap)
                    + " degrees off its wire");
        }
    }

    @Test
    void eachPlatePinsEveryPartOnce() {
        assertEquals(Set.of("hub", "panel", "swatch", "level", "span", "pips", "spoke", "hint", "wire"),
                ids(RingPlates.anatomy(RingPlates.RING)));
        assertEquals(Set.of("aim", "all", "tab", "level", "pips", "detail", "hint"),
                ids(RingPlates.anatomy(RingPlates.WRENCH)));
        for (final String id : RingPlates.IDS) {
            final SpecimenAnatomy a = RingPlates.anatomy(id);
            assertEquals(a.controls().size(), ids(a).size(), id + ": a part pinned twice");
            for (final SpecimenAnatomy.Hot h : a.controls()) {
                assertTrue(h.anchorX() >= 0 && h.anchorY() >= 0 && h.anchorX() < a.width() && h.anchorY() < a.height(),
                        id + " " + h.id() + ": pinned off the plate");
            }
        }
    }

    private static Set<String> ids(SpecimenAnatomy a) {
        final Set<String> out = new TreeSet<>();
        for (final SpecimenAnatomy.Hot h : a.controls()) {
            out.add(h.id());
        }
        return out;
    }

    @Test
    void everyPinReadsCleanlyAtEveryScale() {
        for (final String id : RingPlates.IDS) {
            final SpecimenAnatomy a = RingPlates.anatomy(id);
            final Map<String, Integer> numbers = Callouts.numbering(a);
            for (int den = 2; den <= 3; den++) {
                for (int n = 1; n <= den; n++) {
                    final int fw = (int) Math.ceil(a.width() * n / (double) den);
                    final int fh = (int) Math.ceil(a.height() * n / (double) den);
                    final int fx = com.createstats.book.BookTokens.PIN_W + com.createstats.book.BookTokens.PIN_RUN;
                    final List<Callouts.Pin> pins = Callouts.layout(a, fx, 0, n, den, 0,
                            fx + fw + com.createstats.book.BookTokens.PIN_RUN, -2, fh + 2, numbers);
                    assertEquals(0, Callouts.faults(pins, fx, 0, n, den, RingPlates.print(id)),
                            id + " at " + n + "/" + den + " reads wrongly");
                }
            }
        }
    }

    @Test
    void theWorldsPinsAreOnWhatTheyName() {
        final SpecimenAnatomy a = RingPlates.anatomy(RingPlates.RING);
        final SpecimenAnatomy.Hot wire = a.control("wire");
        final BlockFigure f = WireFigures.RING_WORLD;
        final FigureWorld w = FigureWorld.of(f);
        final BlockView v = f.view();
        double nearest = Double.MAX_VALUE;
        for (final BlockFigure.Tie s : w.spans()) {
            final List<Vec3> points = w.spanPoints(s);
            for (int i = 0; i + 1 < points.size(); i++) {
                for (int k = 0; k <= 8; k++) {
                    final Vec3 p = points.get(i).lerp(points.get(i + 1), k / 8.0);
                    final double[] q = v.project(p.x, p.y, p.z);
                    nearest = Math.min(nearest, Math.hypot(q[0] - wire.anchorX(), q[1] - wire.anchorY()));
                }
            }
        }
        assertTrue(nearest <= 1.0, "the wire's pin is " + nearest + " px from any wire");
        for (final int[] r : RingPlates.print(RingPlates.RING)) {
            assertTrue(!(wire.anchorX() >= r[0] && wire.anchorX() < r[0] + r[2] && wire.anchorY() >= r[1]
                    && wire.anchorY() < r[1] + r[3]), "the wire's pin is under the ring");
        }
        final SpecimenAnatomy.Hot spoke = a.control("spoke");
        final WireRing.Frame frame = WireRing.Frame.of(RingPlates.width(RingPlates.RING),
                RingPlates.height(RingPlates.RING));
        boolean onADot = false;
        for (final WireRing.Panel p : RingPlates.panels()) {
            for (final int[] d : WireRing.dots(frame, p.angle())) {
                onADot |= d[0] == spoke.anchorX() && d[1] == spoke.anchorY();
            }
        }
        assertTrue(onADot, "the spoke's pin is on one of the dots the ring draws");
        for (final int[] r : RingPlates.print(RingPlates.RING)) {
            assertTrue(!(spoke.anchorX() >= r[0] && spoke.anchorX() < r[0] + r[2] && spoke.anchorY() >= r[1]
                    && spoke.anchorY() < r[1] + r[3]), "the spoke's pin is under a panel");
        }
    }

    @Test
    void theRestingPanelIsThePinnedOne() {
        final int k = RingPlates.resting();
        assertTrue(k >= 0, "a panel holds the panel pin");
        final SpecimenAnatomy.Hot pin = RingPlates.anatomy(RingPlates.RING).control("panel");
        final int[] b = WireRing.panelBox(new com.createstats.client.wire.WireCanvas() {
            @Override
            public void fill(int x0, int y0, int x1, int y1, int argb) {
            }

            @Override
            public void text(String s, int x, int y, int argb) {
            }

            @Override
            public int width(String s) {
                return com.createstats.book.specimen.VanillaFont.width(s);
            }
        }, WireRing.Frame.of(RingPlates.width(RingPlates.RING), RingPlates.height(RingPlates.RING)),
                RingPlates.panels().get(k));
        assertTrue(pin.anchorX() >= b[0] && pin.anchorX() < b[0] + b[2] && pin.anchorY() >= b[1]
                && pin.anchorY() < b[1] + b[3]);
    }

    @Test
    void longerWordsStillReadCleanly() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        try {
            RingPlates.useText(key -> {
                final String v = lang.getOrDefault(key, key);
                if (key.equals(WireRing.HINT_KEY)) {
                    return v;
                }

                return v.replaceAll("([A-Za-z]{3,})", "$1ung");
            });
            final SpecimenAnatomy a = RingPlates.anatomy(RingPlates.RING);
            assertEquals(0, Callouts.faultsAtEveryScale(a, RingPlates.print(RingPlates.RING)),
                    "the ring reads wrongly in longer words");
        } finally {
            RingPlates.useDefaultText();
        }
    }
}
