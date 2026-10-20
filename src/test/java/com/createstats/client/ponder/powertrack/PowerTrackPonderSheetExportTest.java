package com.createstats.client.ponder.powertrack;

import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.LampGlow;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.SectionGraph;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerTrackPonderSheetExportTest {
    private static final Path OUT = Path.of("build/powertrack-ponder");

    private static JsonArray vec(double... v) {
        final JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(Math.round(d * 1e4) / 1e4);
        }
        return a;
    }

    private static JsonArray ints(int... v) {
        final JsonArray a = new JsonArray();
        for (int d : v) {
            a.add(d);
        }
        return a;
    }

    private static JsonObject cue(PowerTrackPonderBeats.Cue c, int index) {
        final JsonObject o = new JsonObject();
        o.addProperty("name", c.name());
        o.addProperty("text", index + 1);
        o.addProperty("tick", c.tick());
        o.addProperty("hold", c.hold());
        o.addProperty("key", c.key());
        if (c.at() == null) {
            o.addProperty("independent", c.independent());
        } else {
            o.add("at", vec(c.at()));
        }
        return o;
    }

    private static JsonArray cues(List<PowerTrackPonderBeats.Cue> list) {
        final JsonArray a = new JsonArray();
        for (int i = 0; i < list.size(); i++) {
            a.add(cue(list.get(i), i));
        }
        return a;
    }

    private static JsonObject show(String kind, int tick, String dir, int[]... boxes) {
        final JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("tick", tick);
        o.addProperty("dir", dir);
        final JsonArray b = new JsonArray();
        for (int[] box : boxes) {
            b.add(ints(box));
        }
        o.add("boxes", b);
        return o;
    }

    private static int[] one(int x, int y, int z) {
        return new int[] {x, y, z, x, y, z};
    }

    private static JsonObject control(int tick, int ticks, double[] at, String button, String item) {
        final JsonObject o = new JsonObject();
        o.addProperty("tick", tick);
        o.addProperty("ticks", ticks);
        o.add("anchor", vec(at));
        o.addProperty("button", button);
        o.addProperty("pointing", "down");
        if (item != null) {
            o.addProperty("item", item);
        }
        return o;
    }

    private static JsonObject tag(String kind, int from, int to, double[] anchor, int dx, int dy, boolean rideCar) {
        final JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("from", from);
        o.addProperty("to", to);
        o.add("anchor", vec(anchor));
        o.addProperty("dx", dx);
        o.addProperty("dy", dy);
        o.addProperty("rideCar", rideCar);
        return o;
    }

    private static JsonObject layout(PowerTrackStage.Stage st) {
        final JsonObject o = new JsonObject();
        final JsonArray shapes = new JsonArray();
        final RailLayout layout = st.layout();
        for (RailLayout.Shape s : layout.shapes()) {
            final JsonObject so = new JsonObject();
            so.addProperty("lo", s.lo());
            so.addProperty("hi", s.hi());
            so.addProperty("length", s.length());
            final JsonArray xyz = new JsonArray();
            final int n = s.samples();
            for (int k = 0; k < n; k++) {
                final double[] p = s.point(s.length() * k / (n - 1));
                xyz.add(vec(p));
            }
            so.add("xyz", xyz);
            final JsonArray pieces = new JsonArray();
            for (SectionGraph.Piece p : layout.sections().pieces(s.lo(), s.hi())) {
                final JsonObject po = new JsonObject();
                po.addProperty("section", p.section());
                po.addProperty("from", p.from());
                po.addProperty("to", p.to());
                po.addProperty("railFrom", p.railFrom());
                po.addProperty("railTo", p.railTo());
                po.addProperty("hasRail", p.hasRail());
                po.addProperty("fromEnd", p.fromEnd().name());
                po.addProperty("toEnd", p.toEnd().name());
                pieces.add(po);
            }
            so.add("pieces", pieces);
            shapes.add(so);
        }
        o.add("shapes", shapes);
        final JsonArray taps = new JsonArray();
        for (PowerTrackStage.Tap t : st.taps()) {
            final JsonObject to = new JsonObject();
            to.addProperty("lo", t.lo());
            to.addProperty("hi", t.hi());
            to.addProperty("at", t.at());
            to.addProperty("side", t.side());
            taps.add(to);
        }
        o.add("taps", taps);
        o.addProperty("sections", layout.sections().sectionCount());
        return o;
    }

    private static JsonObject head(String id, String title, String stage, int length, int plate, int plateX, float scale,
                                   float offsetY, float pitch, float yaw) {
        final JsonObject root = new JsonObject();
        root.addProperty("scene", id);
        root.addProperty("title", title);
        root.addProperty("stage", stage);
        root.addProperty("length", length);
        root.addProperty("plate", plate);
        root.addProperty("offX", plateX);
        root.addProperty("scale", scale);
        root.addProperty("offsetY", offsetY);
        root.addProperty("yaw", yaw);
        root.addProperty("pitch", pitch);
        return root;
    }

    private static final class Lantern {
        final LampGlow glow = new LampGlow();
        final FeederLook needle = new FeederLook();
        int hold;
        int sign;

        void tick(RailStory.FeederFrame f) {
            needle.tick(f.railFlow() / (float) Math.max(1, f.dialFe()));
            if (f.lamp() != 0) {
                sign = f.lamp();
                hold = 8;
            } else if (hold > 0) {
                hold--;
            } else {
                sign = 0;
            }
            glow.tick(sign);
        }

        JsonObject json(RailStory.FeederFrame f) {
            final JsonObject o = new JsonObject();
            o.addProperty("needle", needle.needle);
            o.addProperty("lamp", glow.colour());
            o.addProperty("glow", glow.glow(1f));
            o.addProperty("mode", f.mode().name());
            o.addProperty("dialFe", f.dialFe());
            o.addProperty("floorPct", f.floorPct());
            o.addProperty("buffer", f.buffer());
            o.addProperty("railFlow", f.railFlow());
            o.addProperty("circuitFlow", f.circuitFlow());
            o.addProperty("station", f.stationStored() / (double) PowerTrackPonderBeats.BANK_FE);
            o.addProperty("reason", f.reason().name());
            o.addProperty("sectionState", f.view().state());
            return o;
        }
    }

    private static final class Lamps {
        final LampGlow[] glows = {new LampGlow(), new LampGlow()};

        JsonArray tick(RailStory.Frame f, SectionGraph g, int lo, int hi, double cut) {
            final JsonArray a = new JsonArray();
            for (int i = 0; i < 2; i++) {
                final int s = RailStory.lampView(f.sections(), g, lo, hi, cut, i == 0).state();
                glows[i].tick(s == SectionView.LIVE || s == SectionView.COLLECTING ? s : 0);
                final JsonObject o = new JsonObject();
                o.addProperty("colour", glows[i].colour());
                o.addProperty("glow", glows[i].glow(1f));
                a.add(o);
            }
            return a;
        }
    }

    private static float fade(float level, boolean on) {
        final float step = 1f / 8f;
        return on ? Math.min(1f, level + step) : Math.max(0f, level - step);
    }

    private static final class Level {
        int shown = -1;

        int tick(double fill) {
            final float scaled = (float) (Math.max(0.0, Math.min(1.0, fill)) * 8.0);
            int lit = Math.max(0, Math.min(8, Math.round(scaled)));
            if (shown >= 0 && Math.abs(scaled - shown) < 0.65f) {
                lit = shown;
            }
            shown = lit;
            return lit;
        }
    }

    private static JsonObject bank(int[] pos, double fill, int level, int flow) {
        final JsonObject o = new JsonObject();
        o.add("at", ints(pos));
        o.addProperty("fill", fill);
        o.addProperty("level", level);
        o.addProperty("flow", flow);
        return o;
    }

    private static JsonArray shoes(RailStory.Frame f) {
        final JsonArray a = new JsonArray();
        for (RailStory.Shoe s : f.shoes()) {
            final JsonObject o = new JsonObject();
            o.addProperty("angle", s.angle());
            o.addProperty("angleOld", s.angleOld());
            o.addProperty("touching", s.touching());
            o.addProperty("section", s.section());
            o.add("at", vec(s.x(), s.y(), s.z()));
            o.addProperty("railTop", Double.isNaN(s.railTop()) ? -1.0 : s.railTop());
            o.addProperty("arc", s.arc());
            a.add(o);
        }
        return a;
    }

    private static final double WHEEL_RADIUS = 6.5 / 16.0;

    private static void write(String scene, JsonObject root) throws IOException {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve(scene + ".json"), new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
    }

    private static int[] carBox(int x0, int z) {
        return new int[] {x0, PowerTrackStage.TRACK_Y + 1, z, x0 + PowerTrackStage.CAR_LENGTH - 1, PowerTrackStage.CAR_DECK_Y, z};
    }

    private static void carShows(JsonArray shows, int x0, int z, int at, boolean cascade) {
        final int floor = PowerTrackStage.TRACK_Y + 1;
        if (!cascade) {
            shows.add(show("show", at, "down", carBox(x0, z)));
            return;
        }
        for (int b : PowerTrackStage.CAR_BOGIES) {
            shows.add(show("show", at, "down", one(x0 + b, floor, z)));
        }
        for (int k : PowerTrackStage.CAR_COLLECTORS) {
            shows.add(show("show", at + PowerTrackPonderBeats.CAR_STEP, "down", one(x0 + k, floor, z)));
        }
        shows.add(show("show", at + 2 * PowerTrackPonderBeats.CAR_STEP, "down",
                new int[] {x0, PowerTrackStage.CAR_DECK_Y, z, x0 + PowerTrackStage.CAR_LENGTH - 1, PowerTrackStage.CAR_DECK_Y, z}));
    }

    private static void lineShows(JsonArray shows, int trackAt) {
        shows.add(show("show", 0, "up", new int[] {0, 0, 0, PowerTrackStage.LINE_FLOOR[0] - 1, 0, PowerTrackStage.LINE_FLOOR[1] - 1}));
        for (int x = PowerTrackStage.LINE_X[0]; x <= PowerTrackStage.LINE_X[1]; x++) {
            shows.add(show("show", trackAt + PowerTrackPonderBeats.CASCADE * (x - PowerTrackStage.LINE_X[0]), "down",
                    one(x, PowerTrackStage.TRACK_Y, PowerTrackStage.LINE_Z)));
        }
    }

    private static void stationShows(JsonArray shows, int[] feeder, int[] cable, int[] bank, int at) {
        shows.add(show("show", at, "down", one(feeder[0], feeder[1], feeder[2])));
        shows.add(show("show", at + 3, "down", one(cable[0], cable[1], cable[2])));
        shows.add(show("show", at + 6, "down", one(bank[0], bank[1], bank[2])));
    }

    private static JsonObject reveal(double chairs, double rail) {
        final JsonObject o = new JsonObject();
        o.addProperty("chairs", chairs);
        o.addProperty("rail", rail);
        return o;
    }

    @Test
    void writesTheLayingScene() throws IOException {
        final int length = PowerTrackPonderBeats.Laying.LENGTH;
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Laying.stage();
        final JsonObject root = head("power_track_laying", "Electrifying a line", "line", length, PowerTrackStage.LINE_PLATE,
                PowerTrackStage.LINE_PLATE_X, PowerTrackStage.LINE_SCALE, PowerTrackStage.LINE_OFFSET_Y, PowerTrackStage.LINE_PITCH,
                PowerTrackStage.LINE_YAW);
        root.add("layout", layout(st));
        final JsonArray shows = new JsonArray();
        lineShows(shows, PowerTrackPonderBeats.Laying.TRACK);
        final int[] a = PowerTrackStage.FEEDER_A;
        shows.add(show("show", PowerTrackPonderBeats.Laying.FEEDER, "down", one(a[0], a[1], a[2])));
        shows.add(show("show", PowerTrackPonderBeats.Laying.CABLE, "down", one(PowerTrackStage.CABLE_A[0], PowerTrackStage.CABLE_A[1], PowerTrackStage.CABLE_A[2])));
        shows.add(show("show", PowerTrackPonderBeats.Laying.BANK, "down", one(PowerTrackStage.BANK_A[0], PowerTrackStage.BANK_A[1], PowerTrackStage.BANK_A[2])));
        root.add("sections", shows);
        root.add("cues", cues(PowerTrackPonderBeats.Laying.cues()));
        final JsonArray controls = new JsonArray();
        controls.add(control(PowerTrackPonderBeats.Laying.FEEDER_CUE, 30, PowerTrackPonderBeats.railAt(a[0] + 0.5, PowerTrackStage.LINE_Z), "right", "track_feeder"));
        controls.add(control(PowerTrackPonderBeats.Laying.DIAL_CUE, 24, PowerTrackPonderBeats.dialWindow(a), "right", null));
        controls.add(control(PowerTrackPonderBeats.Laying.SCROLL_CUE, 24, PowerTrackPonderBeats.dialWindow(a), "scroll", null));
        root.add("controls", controls);
        final JsonArray tags = new JsonArray();
        tags.add(tag("mode", PowerTrackPonderBeats.Laying.MODE_TAG, PowerTrackPonderBeats.Laying.MODE_TAG_END,
                new double[] {a[0] + 0.5, a[1] + 1.35, a[2] + 0.5}, 0, -14, false));
        root.add("tags", tags);
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Laying.story(st.layout()).play(length);
        final Lantern lantern = new Lantern();
        final Level level = new Level();
        final JsonArray out = new JsonArray();
        for (RailStory.Frame f : frames) {
            final JsonObject fo = new JsonObject();
            fo.addProperty("tick", f.tick());
            lantern.tick(f.feeders()[0]);
            final JsonArray feeders = new JsonArray();
            feeders.add(lantern.json(f.feeders()[0]));
            fo.add("feeders", feeders);
            final JsonArray banks = new JsonArray();
            final double fill = f.feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE;
            banks.add(bank(PowerTrackStage.BANK_A, fill, level.tick(fill), -f.feeders()[0].circuitFlow()));
            fo.add("banks", banks);
            final JsonArray reveal = new JsonArray();
            reveal.add(reveal(PowerTrackPonderBeats.Laying.chairFront(f.tick()), PowerTrackPonderBeats.Laying.railFront(f.tick())));
            fo.add("reveal", reveal);
            fo.add("tapsAt", ints(PowerTrackPonderBeats.Laying.TAP, Integer.MAX_VALUE / 2));
            fo.addProperty("stretch", 1);
            out.add(fo);
        }
        root.add("frames", out);
        write("power_track_laying", root);
        assertEquals(length, out.size());
    }

    private static void lineScene(String id, String title, int length, PowerTrackStage.Stage st, List<RailStory.Frame> frames,
                                  List<PowerTrackPonderBeats.Cue> cues, JsonArray shows, JsonArray controls, JsonArray tags,
                                  int carAt, boolean cascade, IntUnaryOperator stretch, java.util.function.IntToDoubleFunction railFront,
                                  int tapA, int tapB, int slowFrom, int slowTo) throws IOException {
        final JsonObject root = head(id, title, "line", length, PowerTrackStage.LINE_PLATE, PowerTrackStage.LINE_PLATE_X,
                PowerTrackStage.LINE_SCALE, PowerTrackStage.LINE_OFFSET_Y, PowerTrackStage.LINE_PITCH, PowerTrackStage.LINE_YAW);
        root.add("layout", layout(st));
        carShows(shows, PowerTrackStage.LINE_CAR_X, PowerTrackStage.LINE_Z, carAt, cascade);
        root.add("sections", shows);
        root.add("cues", cues(cues));
        root.add("controls", controls);
        root.add("tags", tags);
        root.addProperty("carX0", PowerTrackStage.LINE_CAR_X);
        root.add("collectors", ints(PowerTrackStage.CAR_COLLECTORS));
        root.add("bogies", ints(PowerTrackStage.CAR_BOGIES));
        root.addProperty("carFrom", carAt);
        final JsonObject insulator = new JsonObject();
        insulator.add("at", ints(PowerTrackStage.INSULATOR));
        root.add("insulator", insulator);
        final Lantern[] lanterns = {new Lantern(), new Lantern()};
        final Lamps lamps = new Lamps();
        final Level[] levels = {new Level(), new Level(), new Level()};
        final double cut = PowerTrackStage.lineAt(PowerTrackStage.INSULATOR[0]);
        final JsonArray out = new JsonArray();
        double wheel = 0.0;
        float note = 0f;
        for (RailStory.Frame f : frames) {
            final JsonObject fo = new JsonObject();
            fo.addProperty("tick", f.tick());
            final JsonObject car = new JsonObject();
            car.addProperty("x", f.carX());
            car.addProperty("xOld", f.carXOld());
            wheel = (wheel - 360.0 * (f.carX() - f.carXOld()) / (2 * Math.PI * WHEEL_RADIUS)) % 360.0;
            car.addProperty("wheel", wheel);
            car.addProperty("stored", f.carStored() / (double) PowerTrackPonderBeats.BANK_FE);
            car.addProperty("flow", f.carFlow());
            car.addProperty("section", f.carSection());
            fo.add("car", car);
            fo.add("shoes", shoes(f));
            final JsonArray feeders = new JsonArray();
            for (int i = 0; i < 2; i++) {
                lanterns[i].tick(f.feeders()[i]);
                feeders.add(lanterns[i].json(f.feeders()[i]));
            }
            fo.add("feeders", feeders);
            fo.add("lamps", lamps.tick(f, st.layout().sections(), PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST, cut));
            final JsonArray banks = new JsonArray();
            final double fa = f.feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE;
            final double fb = f.feeders()[1].stationStored() / (double) PowerTrackPonderBeats.BANK_FE;
            final double fc = f.carStored() / (double) PowerTrackPonderBeats.BANK_FE;
            banks.add(bank(PowerTrackStage.BANK_A, fa, levels[0].tick(fa), -f.feeders()[0].circuitFlow()));
            banks.add(bank(PowerTrackStage.BANK_B, fb, levels[1].tick(fb), -f.feeders()[1].circuitFlow()));
            banks.add(bank(new int[] {PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_BANK_X, PowerTrackStage.CAR_DECK_Y, PowerTrackStage.LINE_Z},
                    fc, levels[2].tick(fc), f.carFlow()));
            fo.add("banks", banks);
            final JsonArray reveal = new JsonArray();
            final double front = railFront.applyAsDouble(f.tick());
            reveal.add(reveal(front, front));
            fo.add("reveal", reveal);
            fo.add("tapsAt", ints(tapA, tapB));
            final int s = stretch.applyAsInt(f.tick());
            fo.addProperty("stretch", s);
            note = fade(note, s > 1 && f.tick() >= slowFrom && f.tick() < slowTo);
            fo.addProperty("slowNote", note);
            out.add(fo);
        }
        root.add("frames", out);
        write(id, root);
        assertEquals(length, out.size());
    }

    @Test
    void writesTheCollectorScene() throws IOException {
        final JsonArray shows = new JsonArray();
        lineShows(shows, PowerTrackPonderBeats.Collector.TRACK);
        stationShows(shows, PowerTrackStage.FEEDER_A, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, PowerTrackPonderBeats.Collector.STATION_A);
        shows.add(show("show", PowerTrackPonderBeats.Collector.INSULATOR, "down", one(PowerTrackStage.INSULATOR[0], PowerTrackStage.INSULATOR[1], PowerTrackStage.INSULATOR[2])));
        stationShows(shows, PowerTrackStage.FEEDER_B, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, PowerTrackPonderBeats.Collector.STATION_B);
        lineScene("power_track_collector", "A train on the rail", PowerTrackPonderBeats.Collector.LENGTH,
                PowerTrackPonderBeats.Collector.stage(), PowerTrackPonderBeats.Collector.frames(), PowerTrackPonderBeats.Collector.cues(),
                shows, new JsonArray(), new JsonArray(), PowerTrackPonderBeats.Collector.CAR, true,
                PowerTrackPonderBeats.Collector.slow(), PowerTrackPonderBeats.Collector::railFront,
                PowerTrackPonderBeats.Collector.STATION_A, PowerTrackPonderBeats.Collector.STATION_B,
                PowerTrackPonderBeats.Collector.SLOW_IN, PowerTrackPonderBeats.Collector.SLOW_END);
    }

    @Test
    void writesTheFeederScene() throws IOException {
        final JsonArray shows = new JsonArray();
        lineShows(shows, PowerTrackPonderBeats.Feeder.TRACK);
        stationShows(shows, PowerTrackStage.FEEDER_A, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, PowerTrackPonderBeats.Feeder.STATION_A);
        shows.add(show("show", PowerTrackPonderBeats.Feeder.INSULATOR, "down", one(PowerTrackStage.INSULATOR[0], PowerTrackStage.INSULATOR[1], PowerTrackStage.INSULATOR[2])));
        stationShows(shows, PowerTrackStage.FEEDER_B, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, PowerTrackPonderBeats.Feeder.STATION_B);
        final int[] a = PowerTrackStage.FEEDER_A;
        final int[] b = PowerTrackStage.FEEDER_B;
        final JsonArray controls = new JsonArray();
        controls.add(control(PowerTrackPonderBeats.Feeder.SUPPLY_CUE, 24, PowerTrackPonderBeats.dialWindow(a), "scroll", null));
        controls.add(control(PowerTrackPonderBeats.Feeder.COLLECT_CUE, 24, PowerTrackPonderBeats.dialWindow(b), "scroll", null));
        controls.add(control(PowerTrackPonderBeats.Feeder.AUTO_FLOOR_CUE, 24, PowerTrackPonderBeats.floorWindow(a), "scroll", null));
        controls.add(control(PowerTrackPonderBeats.Feeder.AUTO_A_CUE, 24, PowerTrackPonderBeats.dialWindow(a), "scroll", null));
        controls.add(control(PowerTrackPonderBeats.Feeder.AUTO_B_CUE, 24, PowerTrackPonderBeats.dialWindow(b), "scroll", null));
        final JsonArray tags = new JsonArray();
        final int length = PowerTrackPonderBeats.Feeder.LENGTH;
        tags.add(tag("modeA", PowerTrackPonderBeats.Feeder.SUPPLY_CUE, length, new double[] {a[0] + 0.5, a[1] + 1.35, a[2] + 0.5}, 0, -14, false));
        tags.add(tag("floorA", PowerTrackPonderBeats.Feeder.SUPPLY_CUE, length, PowerTrackPonderBeats.underDoor(a), 0, 4, false));
        tags.add(tag("modeB", PowerTrackPonderBeats.Feeder.COLLECT_CUE, length, new double[] {b[0] + 0.5, b[1] + 1.35, b[2] + 0.5}, 0, -14, false));
        tags.add(tag("floorB", PowerTrackPonderBeats.Feeder.COLLECT_CUE, length, PowerTrackPonderBeats.underDoor(b), 0, 4, false));
        lineScene("power_track_feeder", "Supply, Collect and Auto", length, PowerTrackPonderBeats.Feeder.stage(),
                PowerTrackPonderBeats.Feeder.frames(), PowerTrackPonderBeats.Feeder.cues(), shows, controls, tags,
                PowerTrackPonderBeats.Feeder.CAR, false, t -> 1, PowerTrackPonderBeats.Feeder::railFront,
                PowerTrackPonderBeats.Feeder.STATION_A, PowerTrackPonderBeats.Feeder.STATION_B, 0, 0);
    }

    @Test
    void writesTheGapsScene() throws IOException {
        final int length = PowerTrackPonderBeats.Gaps.LENGTH;
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Gaps.stage();
        final JsonObject root = head("power_track_gaps", "Where the rail stops", "yard", length, PowerTrackStage.YARD_PLATE,
                PowerTrackStage.YARD_PLATE_X, PowerTrackStage.YARD_SCALE, PowerTrackStage.YARD_OFFSET_Y, PowerTrackStage.YARD_PITCH,
                PowerTrackStage.YARD_YAW);
        root.add("layout", layout(st));
        final JsonArray shows = new JsonArray();
        shows.add(show("show", 0, "up", new int[] {0, 0, 0, PowerTrackStage.YARD_FLOOR[0] - 1, 0, PowerTrackStage.YARD_FLOOR[1] - 1}));
        for (int x = PowerTrackStage.YARD_X[0]; x <= PowerTrackStage.YARD_X[1]; x++) {
            shows.add(show("show", PowerTrackPonderBeats.Gaps.TRACK + PowerTrackPonderBeats.CASCADE * (x - PowerTrackStage.YARD_X[0]), "down",
                    one(x, PowerTrackStage.TRACK_Y, PowerTrackStage.LINE_Z)));
        }
        final int[] to = PowerTrackStage.YARD_TURN_TO;
        final int[] br = PowerTrackStage.YARD_BRANCH_END;
        shows.add(show("show", PowerTrackPonderBeats.Gaps.BRANCH, "down", one(to[0], to[1], to[2])));
        shows.add(show("show", PowerTrackPonderBeats.Gaps.BRANCH + PowerTrackPonderBeats.CASCADE, "down", one(br[0], br[1], br[2])));
        int t = PowerTrackPonderBeats.Gaps.SLOPE;
        final int z = PowerTrackStage.YARD_SLOPE_Z;
        for (int x = PowerTrackStage.YARD_SLOPE_LOW[0]; x <= PowerTrackStage.YARD_SLOPE_LOW[1]; x++, t += PowerTrackPonderBeats.CASCADE) {
            shows.add(show("show", t, "down", one(x, PowerTrackStage.TRACK_Y, z)));
        }
        for (int i = 0; i <= PowerTrackStage.YARD_SLOPE_RISE[1] - PowerTrackStage.YARD_SLOPE_RISE[0]; i++, t += PowerTrackPonderBeats.CASCADE) {
            shows.add(show("show", t, "down", one(PowerTrackStage.YARD_SLOPE_RISE[0] + i, PowerTrackStage.TRACK_Y + i, z)));
        }
        for (int x = PowerTrackStage.YARD_SLOPE_HIGH[0]; x <= PowerTrackStage.YARD_SLOPE_HIGH[1]; x++, t += PowerTrackPonderBeats.CASCADE) {
            shows.add(show("show", t, "down", one(x, PowerTrackStage.YARD_SLOPE_TOP_Y, z)));
        }
        assertEquals(PowerTrackPonderBeats.Gaps.slopeDone() + PowerTrackPonderBeats.CASCADE, t, "the slope's cascade ends where the beats say");
        stationShows(shows, PowerTrackStage.YARD_FEEDER_A, PowerTrackStage.YARD_CABLE_A, PowerTrackStage.YARD_BANK_A, PowerTrackPonderBeats.Gaps.STATION_A);
        final int[] ins = PowerTrackStage.YARD_INSULATOR;
        shows.add(show("show", PowerTrackPonderBeats.Gaps.INSULATOR, "down", one(ins[0], ins[1], ins[2])));
        stationShows(shows, PowerTrackStage.YARD_FEEDER_B, PowerTrackStage.YARD_CABLE_B, PowerTrackStage.YARD_BANK_B, PowerTrackPonderBeats.Gaps.STATION_B);
        carShows(shows, PowerTrackStage.YARD_CAR_X, PowerTrackStage.LINE_Z, PowerTrackPonderBeats.Gaps.CAR, true);
        root.add("sections", shows);
        root.add("cues", cues(PowerTrackPonderBeats.Gaps.cues()));
        root.add("controls", new JsonArray());
        final JsonArray tags = new JsonArray();
        final RailLayout.Shape west = st.layout().shape(PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION);
        final RailLayout.Shape east = st.layout().shape(PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_EAST);
        final double westEnd = st.layout().sections().pieces(west.lo(), west.hi()).get(1).railTo();
        final double eastStart = st.layout().sections().pieces(east.lo(), east.hi()).get(0).railFrom();
        tags.add(tag("gap.insulated", PowerTrackPonderBeats.Gaps.TAGS, length,
                new double[] {ins[0] + 0.5, PowerTrackStage.TRACK_Y + 0.4, PowerTrackStage.LINE_Z + 0.5}, 0, -12, false));
        tags.add(tag("gap.junction", PowerTrackPonderBeats.Gaps.TAGS + 4, length,
                new double[] {(west.point(westEnd)[0] + east.point(eastStart)[0]) / 2.0, PowerTrackStage.TRACK_Y + 0.4, PowerTrackStage.LINE_Z + 0.5}, 0, -12, false));
        tags.add(tag("gap.grade", PowerTrackPonderBeats.Gaps.TAGS + 8, length,
                new double[] {PowerTrackStage.yardNode(PowerTrackStage.YARD_SLOPE_FOOT)[0], PowerTrackStage.TRACK_Y + 0.4, z + 0.5}, 0, -12, false));
        root.add("tags", tags);
        root.addProperty("carX0", PowerTrackStage.YARD_CAR_X);
        root.add("collectors", ints(PowerTrackStage.CAR_COLLECTORS));
        root.add("bogies", ints(PowerTrackStage.CAR_BOGIES));
        root.addProperty("carFrom", PowerTrackPonderBeats.Gaps.CAR);
        final JsonObject insulator = new JsonObject();
        insulator.add("at", ints(ins));
        root.add("insulator", insulator);
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Gaps.frames();
        final Lantern[] lanterns = {new Lantern(), new Lantern()};
        final Lamps lamps = new Lamps();
        final Level[] levels = {new Level(), new Level(), new Level()};
        final double cut = PowerTrackStage.yardWestAt(ins[0]);
        final JsonArray out = new JsonArray();
        for (RailStory.Frame f : frames) {
            final JsonObject fo = new JsonObject();
            fo.addProperty("tick", f.tick());
            final JsonObject car = new JsonObject();
            car.addProperty("x", f.carX());
            car.addProperty("xOld", f.carXOld());
            car.addProperty("wheel", 0.0);
            car.addProperty("stored", f.carStored() / (double) PowerTrackPonderBeats.BANK_FE);
            car.addProperty("flow", f.carFlow());
            car.addProperty("section", f.carSection());
            fo.add("car", car);
            fo.add("shoes", shoes(f));
            final JsonArray feeders = new JsonArray();
            for (int i = 0; i < 2; i++) {
                lanterns[i].tick(f.feeders()[i]);
                feeders.add(lanterns[i].json(f.feeders()[i]));
            }
            fo.add("feeders", feeders);
            fo.add("lamps", lamps.tick(f, st.layout().sections(), PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION, cut));
            final JsonArray banks = new JsonArray();
            final double fa = f.feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE;
            final double fb = f.feeders()[1].stationStored() / (double) PowerTrackPonderBeats.BANK_FE;
            final double fc = f.carStored() / (double) PowerTrackPonderBeats.BANK_FE;
            banks.add(bank(PowerTrackStage.YARD_BANK_A, fa, levels[0].tick(fa), -f.feeders()[0].circuitFlow()));
            banks.add(bank(PowerTrackStage.YARD_BANK_B, fb, levels[1].tick(fb), -f.feeders()[1].circuitFlow()));
            banks.add(bank(new int[] {PowerTrackStage.YARD_CAR_X + PowerTrackStage.CAR_BANK_X, PowerTrackStage.CAR_DECK_Y, PowerTrackStage.LINE_Z},
                    fc, levels[2].tick(fc), f.carFlow()));
            fo.add("banks", banks);
            final JsonArray reveal = new JsonArray();
            final double front = PowerTrackPonderBeats.Gaps.railFront(f.tick());
            for (int e = 0; e < st.layout().shapes().size(); e++) {
                reveal.add(reveal(front, front));
            }
            fo.add("reveal", reveal);
            fo.add("tapsAt", ints(PowerTrackPonderBeats.Gaps.STATION_A, PowerTrackPonderBeats.Gaps.STATION_B));
            fo.addProperty("stretch", 1);
            out.add(fo);
        }
        root.add("frames", out);
        write("power_track_gaps", root);
        assertEquals(length, out.size());
    }
}
