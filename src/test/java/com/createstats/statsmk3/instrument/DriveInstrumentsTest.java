package com.createstats.statsmk3.instrument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundChannels;
import com.createstats.ground.GroundFixtures;
import com.createstats.ground.GroundSnapshot;
import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveInstrumentsTest {
    static final List<Instrument> ALL = List.of(new DriveInstruments.Speedo(), new DriveInstruments.Gear(),
            new DriveInstruments.Gg(), new DriveInstruments.Steer(), new DriveInstruments.Telltales());

    static List<ChannelView> channels(GroundSnapshot s) {
        final DriveReadout r = DriveMath.read(s);
        final Map<String, ChannelView> out = new LinkedHashMap<>();
        GroundChannels.publish(r, GroundAlerts.evaluate(r, List.of(), 0.0), (id, label, value, type) -> {
            final ChannelView ch = new ChannelView("src_car1_auto_" + id);
            ch.label = label;
            ch.value = value;
            ch.min = Math.min(0.0f, value);
            ch.max = Math.max(1.0f, value);
            ch.historyCount = 30;
            ch.history = new float[30];
            for (int i = 0; i < 30; i++) {
                ch.history[i] = value * (float) Math.sin(i * 0.2);
            }
            out.put(ch.id, ch);
        });
        return new ArrayList<>(out.values());
    }

    static InstrumentCtx ctx(List<ChannelView> all, String primaryMetric) {
        final InstrumentCtx c = new InstrumentCtx();
        c.all = all;
        c.primary = all.stream().filter(ch -> ch.id.endsWith("_auto_drive_" + primaryMetric)).findFirst()
                .orElse(all.get(0));
        c.lod = Lod.FULL;
        c.time = 1.0f;
        return c;
    }

    @Test
    void everyPartnerTheInstrumentsLookUpIsPublished() throws IOException {
        final String src = Files.readString(Path.of("src/main/java/com/createstats/statsmk3/instrument/DriveInstruments.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        final Matcher m = Pattern.compile("partner\\(ctx, \"(\\w+)\"\\)").matcher(src);
        final Set<String> metrics = new LinkedHashSet<>();
        while (m.find()) {
            metrics.add(m.group(1));
        }
        assertTrue(metrics.size() >= 10, "the scan found " + metrics + ", so it is not reading the partners");
        final Set<String> published = new LinkedHashSet<>();
        for (final GroundSnapshot s : List.of(GroundFixtures.car(), GroundFixtures.braking(), GroundFixtures.roadTrain())) {
            for (final ChannelView ch : channels(s)) {
                published.add(ch.id.substring(ch.id.indexOf("_auto_drive_") + "_auto_drive_".length()));
            }
        }
        for (final String metric : metrics) {
            assertTrue(published.contains(metric), "an instrument looks for drive_" + metric
                    + ", which GroundChannels never publishes: its part would always be drawn as missing");
        }
    }

    @Test
    void theInstrumentsFindTheirPartnersFromTheTilesOwnSource() {
        final List<ChannelView> all = new ArrayList<>(channels(GroundFixtures.car()));

        for (final ChannelView ch : channels(GroundFixtures.tank())) {
            final ChannelView other = new ChannelView(ch.id.replace("src_car1_", "src_tank2_"));
            other.value = ch.value;
            all.add(other);
        }
        final InstrumentCtx c = ctx(all, "speed_kmh");
        assertEquals("src_car1_auto_drive_lateral_g", DriveInstruments.partner(c, "lateral_g").id);
        c.primary = all.stream().filter(ch -> ch.id.equals("src_tank2_auto_drive_steer")).findFirst().orElseThrow();
        assertEquals("src_tank2_auto_drive_gear", DriveInstruments.partner(c, "gear").id);
        assertEquals(c.primary, DriveInstruments.partner(c, "steer"), "a tile bound to the metric is its own partner");
    }

    @Test
    void theGearLettersAreTheChannelsCodes() {
        final String letters = "PHNDRB";
        for (int code = 0; code < letters.length(); code++) {
            assertEquals(letters.charAt(code), DriveInstruments.gearLetter(code));
            assertEquals(code, GroundChannels.gearCode(letters.charAt(code)), "the two maps must agree");
        }
        assertEquals('-', DriveInstruments.gearLetter(6));
        assertEquals('-', DriveInstruments.gearLetter(Double.NaN));
    }

    @Test
    void theSpeedScaleCoversWhatTheVehicleCanDo() {
        final InstrumentCtx c = ctx(channels(GroundFixtures.car()), "speed_kmh");
        final double scale = DriveInstruments.speedScale(c, 54.0);
        assertTrue(scale >= 54.0 * 1.1 && scale % 20.0 == 0.0, "scale " + scale);
        assertEquals(20.0, DriveInstruments.speedScale(new InstrumentCtx().with(new ChannelView("x")), 0.0), 0.0);
    }

    @Test
    void nothingLeavesItsTile() {
        final int[][] tiles = {{16, 16}, {24, 24}, {40, 40}, {64, 48}, {96, 40}, {120, 120}, {200, 60}, {300, 120},
                {48, 140}, {180, 180}};
        final List<List<ChannelView>> sets = new ArrayList<>();
        for (final GroundSnapshot s : List.of(GroundFixtures.car(), GroundFixtures.braking(), GroundFixtures.tank(),
                GroundFixtures.roadTrain(), GroundFixtures.parked(), GroundSnapshot.empty(10L))) {
            sets.add(channels(s));
        }

        final ChannelView stranger = new ChannelView("src_x_auto_stress_percent");
        stranger.value = 73.0f;
        stranger.unit = "%";
        sets.add(new ArrayList<>(List.of(stranger)));
        final List<String> failures = new ArrayList<>();
        for (final Instrument inst : ALL) {
            for (final List<ChannelView> set : sets) {
                if (set.isEmpty()) {
                    continue;
                }
                for (final int[] wh : tiles) {
                    for (final Lod lod : new Lod[]{Lod.FULL, Lod.REDUCED, Lod.preview(48)}) {
                        final Rect tile = new Rect(5.0f, 7.0f, wh[0], wh[1]);
                        final Extent s = new Extent(tile);
                        final InstrumentCtx c = ctx(set, primaryFor(inst));
                        c.lod = lod;
                        inst.draw(s, tile, c);
                        if (s.shapesOver > 1.0f || s.textOver > 0.0f) {
                            failures.add(String.format(Locale.ROOT, "  %s %dx%d %s on %s: shapes %.1f, text %.1f (\"%s\")",
                                    inst.id(), wh[0], wh[1], lod, set.get(0).id, s.shapesOver, s.textOver, s.worst));
                        }
                        for (final String w : s.words) {
                            if (w.contains("NaN") || w.contains("Infinity")) {
                                failures.add("  " + inst.id() + " printed \"" + w + "\"");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside a tile:\n"
                + String.join("\n", failures.subList(0, Math.min(40, failures.size()))));
    }

    @Test
    void theInstrumentIdsAreTheirOwnAndStable() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final Instrument i : ALL) {
            assertTrue(i.id().startsWith("drive_"), i.id());
            assertTrue(ids.add(i.id()), i.id());
            assertFalse(i.displayName().isBlank());
        }
        assertEquals(List.of("drive_speedo", "drive_gear", "drive_gg", "drive_steer", "drive_telltales"),
                new ArrayList<>(ids), "persisted in panel settings: never rename one in place");
    }

    static String primaryFor(Instrument i) {
        return switch (i.id()) {
            case "drive_speedo" -> "speed_kmh";
            case "drive_gear" -> "gear";
            case "drive_gg" -> "lateral_g";
            case "drive_steer" -> "steer";
            default -> "brake";
        };
    }

    static final class Extent implements PanelSurface {
        final Rect bound;
        float shapesOver;
        float textOver;
        String worst = "";
        final List<String> words = new ArrayList<>();

        Extent(Rect bound) {
            this.bound = bound;
        }

        private float over(float x, float y) {
            return Math.max(Math.max(this.bound.x - x, x - this.bound.right()), Math.max(this.bound.y - y, y - this.bound.bottom()));
        }

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            if ((c1 >>> 24) == 0 && (c2 >>> 24) == 0 && (c3 >>> 24) == 0) {
                return;
            }
            this.shapesOver = Math.max(this.shapesOver, Math.max(over(x1, y1), Math.max(over(x2, y2), over(x3, y3))));
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s == null || s.isEmpty() || (color >>> 24) == 0) {
                return;
            }
            this.words.add(s);
            final float o = Math.max(over(x, y), over(x + textWidth(s, scale), y + Text.lineHeight(scale)));
            if (o > this.textOver) {
                this.textOver = o;
                this.worst = s;
            }
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            text(s, centerX - textWidth(s, scale) * 0.5f, y, color, scale);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            text(s, rightX - textWidth(s, scale), y, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return CountingSurface.advance(s, scale);
        }

        @Override
        public void clip(Rect r) {
        }

        @Override
        public void unclip() {
        }

        @Override
        public boolean culled(Rect r) {
            return r.isEmpty();
        }
    }
}
