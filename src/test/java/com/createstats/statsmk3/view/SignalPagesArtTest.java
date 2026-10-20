package com.createstats.statsmk3.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.helper.HelperScan;
import com.createstats.statsmk3.page.Signals;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalPagesArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 900;
    private static final int H = 500;

    private static final int NARROW_W = 496;
    private static final int NARROW_H = 246;

    @Test
    void rendersLivePage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * W + 72, 2 * H + 96);
        final Graphics2D g = sheet.createGraphics();

        place(g, live(board -> board), 0, 0,
                "Live / one signal, its normal band, its alarm limit and a real time axis");
        place(g, live(b -> withPins(b, "src_s1_auto_rpm", "src_s3_auto_items_per_min")), 1, 0,
                "Live / three lanes sharing one time axis and one pointer");
        place(g, live(b -> withPointer(withPins(b, "src_s1_auto_rpm"), 0.42)), 0, 1,
                "Live / pointer names one moment and every lane answers for it");
        place(g, live(b -> withBy(b, Signals.By.KIND)), 1, 1,
                "Live / the same signals grouped by what they measure");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_live.png"));
    }

    @Test
    void rendersAlertsPage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * W + 72, 2 * H + 96);
        final Graphics2D g = sheet.createGraphics();

        place(g, alerts(0, "src_s1_auto_stress_pct", false), 0, 0,
                "Alerts / firing first, then the armed ones by which is closest to going off");
        place(g, alerts(0, "src_s4_auto_fill_pct", true), 1, 0,
                "Alerts / dragging the limit onto the history it will be judged against");
        place(g, alerts(2, "src_s1_auto_stress_pct", false), 0, 1,
                "Alerts / filtered to what is firing");
        place(g, alerts(0, "src_s6_auto_fe_per_tick", false), 1, 1,
                "Alerts / a limit above everything the signal has done, marked at the edge");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_alerts.png"));
    }

    @Test
    void rendersAtTheDefaultGuiScale() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(2 * NARROW_W + 72, 2 * NARROW_H + 96);
        final Graphics2D g = sheet.createGraphics();

        placeAt(g, liveNarrow(), 0, 0, NARROW_W, NARROW_H, "Live / 496 x 246, GUI scale 3");
        placeAt(g, alertsNarrow(), 1, 0, NARROW_W, NARROW_H, "Alerts / 496 x 246, GUI scale 3");
        placeAt(g, liveEmpty(), 0, 1, NARROW_W, NARROW_H, "Live / nothing linked yet");
        placeAt(g, alertsEmpty(), 1, 1, NARROW_W, NARROW_H, "Alerts / nothing linked yet");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_signal_pages_cramped.png"));
    }

    @Test
    void namesDisambiguateOnlyWhenAmbiguous() {
        final SourceConfig lone = new SourceConfig("s9", new BlockPos(4, 70, 9), "Mixer");
        final Signals.Signal only = Signals.signal(
                channel("src_s9_auto_rpm", "RPM", MetricType.RPM, flat(60, 64.0f)), lone, false, 0);
        assertEquals("Mixer", only.group);
        assertEquals("RPM", only.name);

        final Signals.Signal twin = Signals.signal(
                channel("src_s9_auto_rpm", "RPM", MetricType.RPM, flat(60, 64.0f)), lone, true, 0);
        assertEquals("Mixer 4, 70, 9", twin.group);

        final SourceConfig named = new SourceConfig("s8", new BlockPos(0, 64, 0), "Observer");
        named.setUserLabel("Cargo Bay 3");
        final Signals.Signal prefixed = Signals.signal(
                channel("src_s8_auto_cargo_items_pct", "Cargo Bay 3 Cargo Items %",
                        MetricType.FILL_LEVEL, flat(60, 40.0f)), named, false, 0);
        assertEquals("Cargo Bay 3", prefixed.group);
        assertEquals("Cargo Items %", prefixed.name);
    }

    @Test
    void kindsComeFromTheMetricType() {
        assertEquals(Signals.Kind.LOAD, Signals.kindOf(MetricType.STRESS_PERCENT));
        assertEquals(Signals.Kind.LOAD, Signals.kindOf(MetricType.FILL_LEVEL));
        assertEquals(Signals.Kind.AMOUNT, Signals.kindOf(MetricType.STRESS_SU));
        assertEquals(Signals.Kind.RATE, Signals.kindOf(MetricType.ENERGY_FE_PER_TICK));
        assertEquals(Signals.Kind.SPEED, Signals.kindOf(MetricType.RPM));
        assertEquals(Signals.Kind.STATE, Signals.kindOf(MetricType.BOOLEAN_FLAG));
        assertEquals(Signals.Kind.OTHER, Signals.kindOf(MetricType.GENERIC));
    }

    @Test
    void groupsByMachineThenByKind() {
        final List<Signals.Signal> signals = fixture();
        final List<Signals.Group> machines = Signals.grouped(signals, Signals.By.MACHINE);
        assertEquals("Stressometer 18, 66, -12", machines.get(0).title);
        assertEquals(Signals.DIRECT_GROUP, machines.get(machines.size() - 1).title);

        assertEquals("Stress %", machines.get(0).signals.get(0).name);

        final List<Signals.Group> kinds = Signals.grouped(signals, Signals.By.KIND);
        assertEquals("Load", kinds.get(0).title);
        for (final Signals.Signal s : kinds.get(0).signals) {
            assertEquals(Signals.Kind.LOAD, s.kind);
        }
    }

    @Test
    void classifiesWhatTheHistoryIsDoing() {
        assertEquals(Signals.Regime.NO_DATA, Signals.describe(new float[0], 1.0f, false).regime);
        assertEquals(Signals.Regime.STALE, Signals.describe(flat(60, 5.0f), 1.0f, true).regime);
        assertEquals(Signals.Regime.STEADY, Signals.describe(flat(60, 5.0f), 1.0f, false).regime);
        assertEquals(Signals.Regime.RISING, Signals.describe(ramp(60, 10.0f, 90.0f), 1.0f, false).regime);
        assertEquals(Signals.Regime.FALLING, Signals.describe(ramp(60, 90.0f, 10.0f), 1.0f, false).regime);
        assertEquals(Signals.Regime.SWINGING, Signals.describe(wobble(120, 50.0f, 14.0f), 1.0f, false).regime);
    }

    @Test
    void countsAlarmsNotSamples() {
        final float[] twoExcursions = new float[40];
        for (int i = 0; i < twoExcursions.length; i++) {
            twoExcursions[i] = (i >= 5 && i < 12) || (i >= 25 && i < 33) ? 95.0f : 40.0f;
        }
        assertEquals(2, Signals.crossings(twoExcursions, 90.0f, true));
        assertEquals(0, Signals.crossings(twoExcursions, 200.0f, true));

        assertEquals(3, Signals.crossings(twoExcursions, 50.0f, false));

        assertEquals(1, Signals.crossings(new float[]{95.0f, 96.0f, 20.0f}, 90.0f, true));
    }

    @Test
    void suggestsAThresholdOutsideNormal() {
        final Signals.Stats st = Signals.describe(wobble(200, 60.0f, 8.0f), 1.0f, false);
        assertTrue(Signals.suggest(st, true) > st.p90, "an Above limit must clear the band");
        assertTrue(Signals.suggest(st, false) < st.p10, "a Below limit must clear the band");

        final Signals.Stats dead = Signals.describe(flat(60, 100.0f), 1.0f, false);
        assertTrue(Signals.suggest(dead, true) > 100.0f);
    }

    @Test
    void triagesFiringFirstThenClosestToFiring() {
        final List<AlertsView.Row> rows = AlertsView.rowsOf(fixture(), 0, 0L);
        assertEquals("Firing now", rows.get(0).title());

        final List<String> sections = new ArrayList<>();
        for (final AlertsView.Row r : rows) {
            if (r.isHeader()) {
                sections.add(r.title());
            }
        }
        assertEquals(List.of("Firing now", "Armed", "Not watched"), sections);

        float previous = Float.MAX_VALUE;
        boolean inArmed = false;
        for (final AlertsView.Row r : rows) {
            if (r.isHeader()) {
                inArmed = "Armed".equals(r.title());
                continue;
            }
            if (!inArmed) {
                continue;
            }
            final float p = Signals.proximity(r.signal(), Signals.stats(r.signal(), 600, 0L));
            assertTrue(p <= previous + 1.0e-4f,
                    "armed rows must run closest-to-firing first, saw " + p + " after " + previous);
            previous = p;
        }
    }

    @Test
    void filterHidesSectionsWithoutReordering() {
        for (final AlertsView.Row r : AlertsView.rowsOf(fixture(), 2, 0L)) {
            if (r.isHeader()) {
                assertEquals("Firing now", r.title());
            } else {
                assertTrue(r.signal().firing(), r.signal().name + " is not firing");
            }
        }
    }

    @Test
    void aShortBodyGivesUpTheTilesRatherThanTheChart() {
        for (int h = 120; h <= 420; h += 6) {
            final Rect area = new Rect(24.0f, 24.0f, 700.0f, h);
            final Rect chart = LiveView.chartArea(area);
            assertTrue(chart.h >= 0.0f, "negative chart height at " + h);
            if (LiveView.tilesShowing(area)) {
                assertTrue(chart.h >= 40.0f,
                        "tiles kept at " + h + " but only " + chart.h + "px of chart left");
            }
            assertTrue(LiveView.maxLanes(area) >= 1);
        }
    }

    @Test
    void railRowsStayInsideTheRail() {
        for (int w = 380; w <= 1100; w += 20) {
            final Rect area = new Rect(24.0f, 24.0f, w, 320.0f);
            final LiveView.Board b = board(fixture());
            final Rect list = LiveView.listArea(area, b);
            assertTrue(list.w > 40.0f, "rail collapsed at width " + w);
            assertTrue(LiveView.bodyArea(area).w > 150.0f, "body squeezed out at width " + w);
            for (final LiveView.Row row : LiveView.rowsOf(area, b)) {
                assertTrue(row.h() >= 17.0f, "row collapsed at width " + w);
            }
        }
    }

    @Test
    void theAlertPanesNeverOverlap() {
        for (int w = 300; w <= 1100; w += 20) {
            for (int h = 160; h <= 520; h += 20) {
                final Rect area = new Rect(24.0f, 24.0f, w, h);
                final Rect list = AlertsView.listArea(area);
                final Rect editor = AlertsView.editorArea(area);
                assertTrue(list.intersect(editor).isEmpty(),
                        "list and editor overlap at " + w + "x" + h);
                assertTrue(AlertsView.chartRect(area).h >= 0.0f,
                        "negative chart height at " + w + "x" + h);
                assertTrue(editor.bottom() <= area.bottom() + 0.6f,
                        "editor runs past the bottom at " + w + "x" + h);
            }
        }
    }

    @Test
    void bothPagesStayWithinTheTriangleBudget() {
        final CountingSurface counter = new CountingSurface();
        final Rect area = new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f);
        LiveView.paint(counter, area,
                withPins(board(fixture()), "src_s1_auto_rpm", "src_s3_auto_items_per_min"));
        assertTrue(counter.triangles < 9446,
                "Live drew " + counter.triangles + " triangles, over the Instruments page budget");

        counter.reset();
        AlertsView.paint(counter, area, alertBoard(fixture(), 0, "src_s1_auto_stress_pct", false));
        assertTrue(counter.triangles < 9446,
                "Alerts drew " + counter.triangles + " triangles");
    }

    @Test
    void bothPagesSurviveAnEmptyPanel() {
        final CountingSurface counter = new CountingSurface();
        for (int w = 300; w <= 1000; w += 100) {
            for (int h = 140; h <= 500; h += 60) {
                final Rect area = new Rect(24.0f, 24.0f, w, h);
                LiveView.paint(counter, area, board(new ArrayList<>()));
                AlertsView.paint(counter, area, alertBoard(new ArrayList<>(), 0, "", false));
            }
        }
        assertTrue(counter.triangles > 0);
    }

    @Test
    void aDistantLimitDoesNotSquashThePlot() {
        final Signals.Signal s = fixtureById("src_s6_auto_fe_per_tick");
        assertNotNull(s);
        final Signals.Stats st = Signals.stats(s, 600, 0L);
        final RasterSurface surface = new RasterSurface(400, 160, 0x111318);
        final com.createstats.statsmk3.page.SignalArt.Lane lane =
                new com.createstats.statsmk3.page.SignalArt.Lane(s, Signals.window(s, 600), st,
                        s.series, true, st.hi * 40.0f, true, 0xFFFBBF24);
        final com.createstats.statsmk3.page.SignalArt.Mapped map =
                com.createstats.statsmk3.page.SignalArt.lane(surface,
                        new Rect(8.0f, 8.0f, 384.0f, 144.0f), lane, 1.0f, 1.0f);
        assertTrue(map.hi() < st.hi * 3.0f,
                "a limit forty times the data pulled the range with it: hi=" + map.hi());
        assertTrue(map.hi() > st.hi, "the range must still contain the data");
        assertFalse(map.plot().isEmpty());
    }

    private static StatsDisplayPanelBE.DataChannel channel(String id, String label, MetricType type,
                                                           float[] data) {
        final StatsDisplayPanelBE.DataChannel ch = new StatsDisplayPanelBE.DataChannel(id, label);
        ch.getStore().setMetricType(type);
        for (final float v : data) {
            ch.getStore().addSampleDirect(v);
        }
        return ch;
    }

    private static StatsDisplayPanelBE.DataChannel alarmed(StatsDisplayPanelBE.DataChannel ch,
                                                           float threshold, boolean above) {
        ch.setAlertThreshold(threshold);
        ch.setAlertAbove(above);
        ch.setAlertEnabled(true);
        return ch;
    }

    private static List<Signals.Signal> fixture() {
        final List<Signals.Signal> out = new ArrayList<>();
        final SourceConfig s1 = new SourceConfig("s1", new BlockPos(18, 66, -12), "Stressometer");
        final SourceConfig s2 = new SourceConfig("s2", new BlockPos(-7, 64, 1), "Stressometer");
        final SourceConfig s3 = new SourceConfig("s3", new BlockPos(12, 66, -9), "Mechanical Mixer");
        s3.setUserLabel("Kiln line");
        final SourceConfig s4 = new SourceConfig("s4", new BlockPos(-20, 65, 14), "Fluid Tank");
        final SourceConfig s6 = new SourceConfig("s6", new BlockPos(44, 70, 3), "Energy Meter");

        int i = 0;
        out.add(Signals.signal(alarmed(channel("src_s1_auto_stress_pct", "Stress %",
                MetricType.STRESS_PERCENT, climb(300, 62.0f, 97.0f, 4.0f)), 90.0f, true),
                s1, true, i++));
        out.add(Signals.signal(channel("src_s1_auto_stress_su", "Stress (SU)",
                MetricType.STRESS_SU, climb(300, 1270.0f, 1985.0f, 40.0f)), s1, true, i++));
        out.add(Signals.signal(channel("src_s1_auto_capacity_su", "Capacity",
                MetricType.STRESS_SU, flat(300, 2048.0f)), s1, true, i++));
        out.add(Signals.signal(channel("src_s1_auto_rpm", "RPM",
                MetricType.RPM, wobble(300, 64.0f, 3.0f)), s1, true, i++));

        out.add(Signals.signal(alarmed(channel("src_s2_auto_stress_pct", "Stress %",
                MetricType.STRESS_PERCENT, wobble(300, 31.0f, 5.0f)), 85.0f, true),
                s2, true, i++));
        out.add(Signals.signal(channel("src_s2_auto_rpm", "RPM",
                MetricType.RPM, flat(300, 32.0f)), s2, true, i++));

        out.add(Signals.signal(alarmed(channel("src_s3_auto_items_per_min", "Items/min",
                MetricType.ITEMS_PER_MIN, wobble(300, 412.0f, 46.0f)), 300.0f, false),
                s3, false, i++));
        out.add(Signals.signal(channel("src_s3_auto_fill_pct", "Buffer %",
                MetricType.FILL_LEVEL, wobble(300, 58.0f, 17.0f)), s3, false, i++));

        out.add(Signals.signal(alarmed(channel("src_s4_auto_fill_pct", "Tank Fill %",
                MetricType.FILL_LEVEL, climb(300, 74.0f, 22.0f, 6.0f)), 20.0f, false),
                s4, false, i++));
        out.add(Signals.signal(channel("src_s4_auto_fluid_mb", "Contents",
                MetricType.FLUID_MB, climb(300, 14800.0f, 4400.0f, 300.0f)), s4, false, i++));

        out.add(Signals.signal(alarmed(channel("src_s6_auto_fe_per_tick", "Draw",
                MetricType.ENERGY_FE_PER_TICK, wobble(300, 1840.0f, 210.0f)), 2600.0f, true),
                s6, false, i++));
        out.add(Signals.signal(channel("src_s6_auto_fe", "Stored",
                MetricType.ENERGY_FE, climb(300, 3.6e6f, 1.1e6f, 4.0e4f)), s6, false, i++));

        out.add(Signals.signal(channel("manual_kiln_temp", "Kiln temperature",
                MetricType.GENERIC, wobble(300, 780.0f, 24.0f)), null, false, i));
        return out;
    }

    private static Signals.Signal fixtureById(String id) {
        return Signals.byId(fixture(), id);
    }

    private static float[] flat(int n, float value) {
        final float[] out = new float[n];
        java.util.Arrays.fill(out, value);
        return out;
    }

    private static float[] ramp(int n, float from, float to) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = from + (to - from) * (i / (float) (n - 1));
        }
        return out;
    }

    private static float[] wobble(int n, float mid, float amplitude) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = mid + (float) (Math.sin(i * 0.19) * amplitude * 0.7
                    + Math.sin(i * 0.041) * amplitude * 0.3);
        }
        return out;
    }

    private static float[] climb(int n, float from, float to, float noise) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            final float t = i / (float) (n - 1);
            out[i] = from + (to - from) * t
                    + (float) (Math.sin(i * 0.37) * noise * 0.6 + Math.sin(i * 0.11) * noise * 0.4);
        }
        return out;
    }

    private static LiveView.Board board(List<Signals.Signal> signals) {
        final Map<String, Float> reveal = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            for (final Signals.Signal s : signals) {
                reveal.put(i + ":" + s.id, 1.0f);
            }
        }
        final Scroll scroll = new Scroll();
        return new LiveView.Board(signals,
                signals.isEmpty() ? "" : signals.get(0).id, new ArrayList<>(), "",
                Signals.WINDOWS.length - 1,
                Signals.By.MACHINE, signals.isEmpty() ? 0.0f : signals.get(0).value(),
                scroll, reveal, -1.0, -1.0, 0L, "");
    }

    private static LiveView.Board withPins(LiveView.Board b, String... ids) {
        return new LiveView.Board(b.signals(), b.focusId(), List.of(ids), b.hoverId(),
                b.windowIndex(), b.by(), b.heroValue(), b.scroll(), b.reveal(),
                b.mouseX(), b.mouseY(), b.nowTick(), b.query());
    }

    private static LiveView.Board withBy(LiveView.Board b, Signals.By by) {
        return new LiveView.Board(b.signals(), b.focusId(), b.pins(), b.hoverId(),
                b.windowIndex(), by, b.heroValue(), b.scroll(), b.reveal(),
                b.mouseX(), b.mouseY(), b.nowTick(), b.query());
    }

    private static LiveView.Board withPointer(LiveView.Board b, double t) {
        final Rect chart = LiveView.chartArea(new Rect(24.0f, 24.0f, W - 48.0f, H - 48.0f));
        return new LiveView.Board(b.signals(), b.focusId(), b.pins(), b.hoverId(),
                b.windowIndex(), b.by(), b.heroValue(), b.scroll(), b.reveal(),
                chart.x + chart.w * t, chart.centerY(), b.nowTick(), b.query());
    }

    private static AlertsView.Board alertBoard(List<Signals.Signal> signals, int filter,
                                               String selectedId, boolean dragging) {
        final List<AlertsView.Row> rows = AlertsView.rowsOf(signals, filter, 0L);
        final Signals.Signal target = Signals.byId(signals, selectedId);
        final Scroll scroll = new Scroll();
        final HelperScan.Result helpers = new HelperScan.Result();
        helpers.alarms = 2;
        helpers.beacons = 1;
        helpers.alarmFiring = true;

        return new AlertsView.Board(signals, rows, selectedId, "", filter, 0, scroll,
                target, target == null ? 0.0f : target.threshold(),
                target == null || target.above(), target != null && target.armed(),
                false, dragging, helpers, 0L, java.util.Set.of("src_s1_auto_stress_pct"));
    }

    private interface Tweak {
        LiveView.Board apply(LiveView.Board b);
    }

    private static BufferedImage live(Tweak tweak) {
        return renderLive(tweak.apply(board(fixture())), W, H);
    }

    private static BufferedImage liveNarrow() {
        return renderLive(board(fixture()), NARROW_W, NARROW_H);
    }

    private static BufferedImage liveEmpty() {
        return renderLive(board(new ArrayList<>()), NARROW_W, NARROW_H);
    }

    private static BufferedImage renderLive(LiveView.Board b, int w, int h) {
        final RasterSurface s = new RasterSurface(w, h, 0x111318);
        final Rect area = new Rect(24.0f, 16.0f, w - 48.0f, h - 32.0f);
        b.scroll().setMetrics(LiveView.contentHeight(LiveView.rowsOf(area, b)),
                LiveView.listArea(area, b).h);
        LiveView.paint(s, area, b);
        drawLiveChrome(s, area, b);
        return s.finish();
    }

    private static void drawLiveChrome(PanelSurface s, Rect area, LiveView.Board b) {
        final int[] window = {b.windowIndex()};
        final Segmented windows = new Segmented(Signals.WINDOW_LABELS, () -> window[0], i -> {
        }).textScale(Text.MICRO);
        windows.setBounds(LiveView.windowBounds(area));
        windows.draw(s);

        if (b.signals().size() > 1) {
            final Segmented groups = new Segmented(new String[]{"Machine", "Kind"},
                    () -> b.by() == Signals.By.MACHINE ? 0 : 1, i -> {
            }).textScale(Text.MICRO);
            groups.setBounds(LiveView.groupBounds(area));
            groups.draw(s);
        }
        if (b.signals().size() >= 8) {
            final TextField filter = new TextField(t -> {
            }).placeholder("Filter").textScale(Text.MICRO);
            filter.setBounds(LiveView.filterBounds(area));
            filter.draw(s);
        }
    }

    private static BufferedImage alerts(int filter, String selected, boolean dragging) {
        return renderAlerts(alertBoard(fixture(), filter, selected, dragging), W, H);
    }

    private static BufferedImage alertsNarrow() {
        return renderAlerts(alertBoard(fixture(), 0, "src_s1_auto_stress_pct", false),
                NARROW_W, NARROW_H);
    }

    private static BufferedImage alertsEmpty() {
        return renderAlerts(alertBoard(new ArrayList<>(), 0, "", false), NARROW_W, NARROW_H);
    }

    private static BufferedImage renderAlerts(AlertsView.Board b, int w, int h) {
        final RasterSurface s = new RasterSurface(w, h, 0x111318);
        final Rect area = new Rect(24.0f, 16.0f, w - 48.0f, h - 32.0f);
        b.scroll().setMetrics(AlertsView.contentHeight(b.rows()), AlertsView.listArea(area).h);
        AlertsView.paint(s, area, b);
        drawAlertChrome(s, area, b);
        return s.finish();
    }

    private static void drawAlertChrome(PanelSurface s, Rect area, AlertsView.Board b) {
        final Segmented filter = new Segmented(new String[]{"All", "Armed", "Firing"},
                b::listFilter, i -> {
        }).textScale(Text.MICRO);
        filter.setBounds(AlertsView.filterBounds(area));
        filter.draw(s);

        if (b.target() == null) {
            return;
        }
        final Segmented tabs = new Segmented(new String[]{"Alert", "Redstone"},
                b::editorTab, i -> {
        }).textScale(Text.MICRO);
        tabs.setBounds(AlertsView.tabBounds(area));
        tabs.draw(s);

        final Segmented direction = new Segmented(new String[]{"Above", "Below"},
                () -> b.above() ? 0 : 1, i -> {
        }).textScale(Text.MICRO);
        direction.setBounds(AlertsView.directionBounds(area));
        direction.draw(s);

        final Rect limitBounds = AlertsView.limitFieldBounds(area);
        if (limitBounds.w >= 40.0f) {
            final TextField limit = new TextField(t -> {
            }).textScale(Text.MICRO);
            limit.setBounds(limitBounds);
            limit.begin(Text.value(b.threshold()));
            limit.cancel();
            limit.draw(s);
        }

        final Toggle armed = new Toggle(b::armed, on -> {
        });
        armed.setBounds(AlertsView.toggleBounds(area));
        armed.update(1.0f);
        armed.draw(s);

        final com.createstats.statsmk3.ui.Button suggest =
                new com.createstats.statsmk3.ui.Button("Just outside normal", () -> {
                }).style(com.createstats.statsmk3.ui.Button.Style.SECONDARY).textScale(Text.MICRO);
        suggest.setBounds(AlertsView.suggestBounds(area));
        suggest.draw(s);

        for (final boolean up : new boolean[]{false, true}) {
            final com.createstats.statsmk3.ui.Button nudge =
                    new com.createstats.statsmk3.ui.Button(up ? "+" : "-", () -> {
                    }).style(com.createstats.statsmk3.ui.Button.Style.SECONDARY);
            nudge.setBounds(AlertsView.nudgeBounds(area, up));
            nudge.draw(s);
        }
    }

    private static void ensureOut() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
    }

    private static BufferedImage sheet(int w, int h) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x07080B));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        placeAt(g, img, col, row, W, H, caption);
    }

    private static void placeAt(Graphics2D g, BufferedImage img, int col, int row, int w, int h,
                                String caption) {
        final int x = 24 + col * (w + 24);
        final int y = 40 + row * (h + 48);
        g.drawImage(img, x, y, null);
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(caption, x, y - 8);
    }
}
