package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicRig;
import com.createstats.seismic.VeinVisibility;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderSheetExportTest {
    private static final Path OUT = Path.of("build/seismic-ponder");

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

    private static JsonObject cue(SeismicPonderBeats.Cue c, int index) {
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

    private static JsonArray cues(List<SeismicPonderBeats.Cue> list) {
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

    private static JsonObject control(int tick, int ticks, double[] at, String button) {
        return control(tick, ticks, at, button, "down");
    }

    private static JsonObject control(int tick, int ticks, double[] at, String button, String pointing) {
        final JsonObject o = new JsonObject();
        o.addProperty("tick", tick);
        o.addProperty("ticks", ticks);
        o.add("anchor", vec(at));
        o.addProperty("button", button);
        o.addProperty("pointing", pointing);
        return o;
    }

    private static JsonArray veins(SeismicPonderSurvey.Survey s) {
        final JsonArray a = new JsonArray();
        for (SeismicPonderSurvey.Vein v : s.veins()) {
            final JsonObject o = new JsonObject();
            o.addProperty("label", v.label());
            o.addProperty("depth", v.depth());
            o.addProperty("colour", v.colour());
            o.addProperty("echo", v.echo());
            o.addProperty("nearest", v.nearest());
            o.add("centre", vec(v.centre()));
            o.add("near", vec(v.near()));
            o.add("runs", ints(v.runs()));
            final JsonArray cells = new JsonArray();
            for (int[] c : v.cells()) {
                cells.add(ints(c));
            }
            o.add("cells", cells);
            a.add(o);
        }
        return a;
    }

    private static JsonArray faces(List<Wave.Face> faces) {
        final JsonArray a = new JsonArray();
        for (Wave.Face f : faces) {
            final JsonObject o = new JsonObject();
            o.addProperty("axis", f.axis());
            o.addProperty("at", f.at());
            o.add("rect", vec(f.u0(), f.u1(), f.v0(), f.v1()));
            if (f.hole() != null) {
                o.add("hole", vec(f.hole()));
            }
            o.addProperty("facing", f.facing());
            a.add(o);
        }
        return a;
    }

    private static JsonObject rig(String name, int[] at, RigScript script, RigLook look, int tick) {
        final RigScript.Frame f = script.at(tick);
        final JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.add("at", ints(at));
        o.addProperty("weight", look.weight);
        o.addProperty("dogs", look.dogs);
        o.addProperty("glow", look.glow);
        o.add("lamp", vec(look.rgb[0], look.rgb[1], look.rgb[2]));
        o.addProperty("paper", look.paper);
        o.addProperty("specimen", look.specimen == null ? "" : look.specimen);
        o.addProperty("specimenOut", look.specimenOut);
        o.addProperty("dropTick", f.dropTick());
        o.addProperty("fall", f.fallTicks());
        o.addProperty("dropHeight", f.dropHeight());
        o.addProperty("impactTick", f.impactTick());
        o.addProperty("blow", f.blow());
        o.add("echoes", ints(f.echoes()));
        o.addProperty("state", f.state().name());
        return o;
    }

    private static float fade(float level, boolean on) {
        final float step = 1f / 8f;
        return on ? Math.min(1f, level + step) : Math.max(0f, level - step);
    }

    private static void write(String scene, JsonObject root) throws IOException {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve(scene + ".json"), new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
    }

    private static JsonObject head(String id, String title, String stage, int length, int plate, float scale, float offsetY,
                                   float pitch, float yaw, int offX) {
        final JsonObject root = new JsonObject();
        root.addProperty("scene", id);
        root.addProperty("title", title);
        root.addProperty("stage", stage);
        root.addProperty("length", length);
        root.addProperty("plate", plate);
        root.addProperty("scale", scale);
        root.addProperty("offsetY", offsetY);
        root.addProperty("yaw", yaw);
        root.addProperty("pitch", pitch);
        root.addProperty("offX", offX);
        return root;
    }

    @Test
    void writesTheSetupScene() throws IOException {
        final int length = SeismicPonderBeats.Setup.LENGTH;
        final JsonObject root = head("seismic_scanner_setup", "Setting up a seismic survey", "pad", length,
                SeismicStage.PAD_PLATE, SeismicStage.PAD_SCALE, SeismicStage.PAD_OFFSET_Y, SeismicStage.PAD_PITCH,
                SeismicStage.YAW, 0);
        final int[] r = SeismicStage.PAD_RIG;
        final int[] d = SeismicStage.PAD_DECOY;
        final int[][] g = SeismicStage.PAD_GROUND;
        final JsonArray shows = new JsonArray();
        shows.add(show("show", 0, "up", new int[] {0, 0, 0, SeismicStage.PAD_PLATE - 1, 0, SeismicStage.PAD_PLATE - 1}));
        shows.add(show("show", SeismicPonderBeats.Setup.DIRT, "down", new int[] {g[0][0], 1, g[0][2], g[1][0], 1, g[1][2]}));
        shows.add(show("show", SeismicPonderBeats.Setup.GRASS, "down", new int[] {g[0][0], 2, g[0][2], g[1][0], 2, g[1][2]}));
        shows.add(show("show", SeismicPonderBeats.Setup.RIG, "down", new int[] {r[0], r[1], r[2], r[0], r[1], r[2]}));
        shows.add(show("show", SeismicPonderBeats.Setup.RIG + 3, "down", new int[] {r[0], r[1] + 1, r[2], r[0], r[1] + 1, r[2]}));
        final int[] p = SeismicStage.PAD_PLANK;
        final int dec = SeismicPonderBeats.Setup.DECOY;
        shows.add(show("show", dec, "down", new int[] {p[0], p[1], p[2], p[0], p[1], p[2]}));
        shows.add(show("show", dec + 4, "down", new int[] {d[0], d[1], d[2], d[0], d[1], d[2]}));
        shows.add(show("show", dec + 7, "down", new int[] {d[0], d[1] + 1, d[2], d[0], d[1] + 1, d[2]}));
        final int out = SeismicPonderBeats.Setup.DECOY_OUT;
        shows.add(show("hide", out, "up", new int[] {d[0], d[1] + 1, d[2], d[0], d[1] + 1, d[2]}));
        shows.add(show("hide", out + 3, "up", new int[] {d[0], d[1], d[2], d[0], d[1], d[2]}));
        shows.add(show("hide", out + 6, "up", new int[] {p[0], p[1], p[2], p[0], p[1], p[2]}));
        final int[] b = SeismicStage.PAD_BANK;
        final int[] c = SeismicStage.PAD_CABLE;
        shows.add(show("show", SeismicPonderBeats.Setup.BANK, "down", new int[] {b[0], b[1], b[2], b[0], b[1], b[2]}));
        shows.add(show("show", SeismicPonderBeats.Setup.CABLE, "down", new int[] {c[0], c[1], c[2], c[0], c[1], c[2]}));
        root.add("sections", shows);
        root.add("cues", cues(SeismicPonderBeats.Setup.cues()));
        final JsonArray controls = new JsonArray();
        final double[] grip = SeismicPonderBeats.onRig(r, 8.0, 22.0, 4.0);
        controls.add(control(SeismicPonderBeats.Setup.HOLD, 40, grip, "right"));
        controls.add(control(SeismicPonderBeats.Setup.SCROLL, 40, grip, "scroll"));
        root.add("controls", controls);

        final RigScript hero = SeismicPonderBeats.Setup.hero();
        final RigScript decoy = SeismicPonderBeats.Setup.decoy();
        final RigLook heroLook = new RigLook();
        final RigLook decoyLook = new RigLook();
        final JsonArray frames = new JsonArray();
        float hub = 0f;
        float cell = 0f;
        float dogs = 0f;
        long given = 0;
        for (int t = 0; t < length; t++) {
            if (t == 0) {
                heroLook.settle(hero.at(0), 0);
                decoyLook.settle(decoy.at(0), 0);
            } else {
                heroLook.step(hero.at(t), t);
                decoyLook.step(decoy.at(t), t);
            }
            final JsonObject f = new JsonObject();
            f.addProperty("tick", t);
            final JsonArray rigs = new JsonArray();
            rigs.add(rig("hero", r, hero, heroLook, t));
            rigs.add(rig("decoy", d, decoy, decoyLook, t));
            f.add("rigs", rigs);
            final int release = SeismicPonderBeats.Setup.RELEASE;
            hub = fade(hub, t >= SeismicPonderBeats.Setup.HUB && t < release);
            final JsonObject h = new JsonObject();
            h.addProperty("level", hub);
            h.addProperty("station", t >= SeismicPonderBeats.Setup.PICK ? SeismicPonderSurvey.IRON : "");
            h.addProperty("range", t >= SeismicPonderBeats.Setup.R64 ? 64 : t >= SeismicPonderBeats.Setup.R48 ? 48 : 32);
            h.add("anchor", vec(SeismicPonderBeats.Setup.hub()));
            h.addProperty("dx", SeismicPonderBeats.Setup.HUB_DX);
            f.add("hub", h);
            final JsonArray tags = new JsonArray();
            cell = fade(cell, t >= release + 8 && t < release + 8 + 70 - 9);
            dogs = fade(dogs, t >= SeismicPonderBeats.Setup.COST && t < SeismicPonderBeats.Setup.COST + 76 - 9);
            tags.add(tag("Iron Ore", SeismicPonderBeats.Setup.cell(), cell, SeismicPonderBeats.Setup.CELL_TAG[0],
                    SeismicPonderBeats.Setup.CELL_TAG[1], 0xFFFFFF));
            tags.add(tag("Radius 64, 500,000 FE", SeismicPonderBeats.Setup.dogs(), dogs, SeismicPonderBeats.Setup.DOGS_TAG[0],
                    SeismicPonderBeats.Setup.DOGS_TAG[1], 0xFFAA33));
            f.add("tags", tags);
            given += hero.at(t).arriving();
            f.addProperty("feed", hero.at(t).arriving());
            f.addProperty("bank", Math.max(0L, 1_024_000L - given) / 1_024_000.0);
            frames.add(f);
        }
        root.add("frames", frames);
        final JsonArray run = new JsonArray();
        for (Vec3 q : SeismicPonderBeats.Setup.run().points()) {
            run.add(vec(q.x, q.y, q.z));
        }
        root.add("run", run);
        write("seismic_scanner_setup", root);
        assertTrue(frames.size() == length);
    }

    private static final class RingSet {
        final int start;
        final int end;
        final Walker.Walk walk;
        final List<Walker.Ring> rings;
        float level;
        float tags;

        RingSet(int start, int end, Walker.Walk walk, List<Walker.Ring> rings) {
            this.start = start;
            this.end = end;
            this.walk = walk;
            this.rings = rings;
        }

        void step(int t, double top, double edge, double x0, double x1, JsonArray sets, JsonArray tagsOut) {
            if (t < start || t >= end) {
                return;
            }
            final boolean on = t < end - 10;
            level = Math.max(0f, Math.min(1f, level + (on ? 0.1f : -0.1f)));
            tags = fade(tags, on);
            final Vec3 c = walk.at(t);
            final JsonObject o = new JsonObject();
            o.add("centre", vec(c.x, top, c.z));
            o.addProperty("level", level);
            final JsonArray rs = new JsonArray();
            for (Walker.Ring ring : rings) {
                final JsonObject ro = new JsonObject();
                ro.addProperty("r", ring.radius());
                ro.addProperty("rgb", ring.rgb());
                ro.addProperty("alpha", ring.alpha());
                rs.add(ro);
                for (double[] x : Walker.crossings(c, ring.radius(), edge, x0, x1)) {
                    tagsOut.add(tag(ring.text(), new double[] {x[0], top, edge}, (float) (tags * x[1]), 0, -8, ring.rgb()));
                }
            }
            o.add("rings", rs);
            sets.add(o);
        }
    }

    private static JsonObject tag(String text, double[] at, float level, int dx, int dy, int rgb) {
        final JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.add("anchor", vec(at));
        o.addProperty("level", level);
        o.addProperty("dx", dx);
        o.addProperty("dy", dy);
        o.addProperty("rgb", rgb);
        return o;
    }

    @Test
    void writesTheSurveyScene() throws IOException {
        final int length = SeismicPonderBeats.Survey.length();
        final JsonObject root = head("seismic_scanner_survey", "Reading the ground", "rig", length, SeismicStage.RIG_PLATE,
                SeismicStage.RIG_SCALE, SeismicStage.RIG_OFFSET_Y, SeismicStage.RIG_PITCH, SeismicStage.YAW, 0);
        final int[] r = SeismicStage.RIG;
        final int[][] g = SeismicStage.RIG_GROUND;
        final int[][] n = SeismicStage.NOTCH;
        final JsonArray shows = new JsonArray();
        shows.add(show("show", 0, "up", new int[] {0, 0, 0, SeismicStage.RIG_PLATE - 1, 0, SeismicStage.RIG_PLATE - 1}));
        for (int y = g[0][1]; y <= g[1][1]; y++) {
            final int t = SeismicPonderBeats.Survey.FIRST_LAYER + (y - g[0][1]) * SeismicPonderBeats.Survey.LAYER_STEP;
            shows.add(show("show", t, "down", new int[] {g[0][0], y, g[0][2], g[1][0], y, g[1][2]}));
        }
        shows.add(show("show", SeismicPonderBeats.Survey.RIG, "down", new int[] {r[0], r[1], r[2], r[0], r[1], r[2]}));
        shows.add(show("show", SeismicPonderBeats.Survey.RIG + 3, "down", new int[] {r[0], r[1] + 1, r[2], r[0], r[1] + 1, r[2]}));
        shows.add(show("hide", SeismicPonderBeats.Survey.CUT, "north", new int[] {n[0][0], n[0][1], n[0][2], n[1][0], n[1][1], n[1][2]}));
        shows.add(show("show", SeismicPonderBeats.Survey.back(), "down", new int[] {n[0][0], n[0][1], n[0][2], n[1][0], n[1][1], n[1][2]}));
        root.add("sections", shows);
        root.add("cues", cues(SeismicPonderBeats.Survey.cues()));
        final JsonArray controls = new JsonArray();
        controls.add(control(SeismicPonderBeats.Survey.LEVER, SeismicPonderBeats.Survey.PULL - SeismicPonderBeats.Survey.LEVER,
                SeismicPonderBeats.lever(r), "right", "up"));
        root.add("controls", controls);
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Survey.ground();
        root.add("veins", veins(ground));
        final int impact = SeismicPonderBeats.Survey.impact();
        final JsonObject wave = new JsonObject();
        wave.addProperty("impact", impact);
        wave.add("strike", vec(r[0] + 0.5, r[1], r[2] + 0.5));
        wave.addProperty("fadeAt", 9.0);
        wave.add("faces", faces(SeismicScenes.rigFaces()));
        root.add("wave", wave);
        final double[] w = SeismicStage.RIG_WITNESS;
        final JsonObject parrot = new JsonObject();
        parrot.add("at", vec(w));
        parrot.addProperty("from", SeismicPonderBeats.Survey.WITNESS);
        root.add("parrot", parrot);

        final RigScript hero = SeismicPonderBeats.Survey.hero(ground);
        final RigLook look = new RigLook();
        final VeinView.Light light = new VeinView.Light(ground, impact);
        final IntUnaryOperator slow = SeismicPonderBeats.Survey.slow();
        final JsonArray frames = new JsonArray();
        float recorder = 0f;
        float note = 0f;
        float reading = 0f;
        final int readAt = SeismicPonderBeats.Survey.reading();
        final int readFor = SeismicPonderBeats.Survey.readingTicks();
        final int orbit = SeismicPonderBeats.Survey.orbit();
        for (int t = 0; t < length; t++) {
            if (t == 0) {
                look.settle(hero.at(0), 0);
            } else {
                look.step(hero.at(t), t);
            }
            final JsonObject f = new JsonObject();
            f.addProperty("tick", t);
            final JsonArray rigs = new JsonArray();
            rigs.add(rig("hero", r, hero, look, t));
            f.add("rigs", rigs);
            if (t >= impact) {
                light.step(t, w[0], w[1] + Walker.EYE, w[2]);
                f.add("shown", floats(light.shown));
            }
            recorder = fade(recorder, t >= SeismicPonderBeats.Survey.RECORDER && t < length - 9);
            note = fade(note, slow.applyAsInt(t) > 2);
            reading = fade(reading, t >= readAt && t < readAt + readFor - SeismicOverlays.READING_OUT);
            f.addProperty("recorder", recorder);
            f.addProperty("slowNote", note);
            f.addProperty("reading", reading);
            f.addProperty("stretch", slow.applyAsInt(t));

            final double u = Math.max(0.0, Math.min(1.0, (t - 1 - orbit) / (double) SeismicPonderBeats.Survey.ORBIT_TICKS));
            f.addProperty("yaw", SeismicStage.YAW + SeismicPonderBeats.Survey.ORBIT_DEGREES * u * u * (3.0 - 2.0 * u));
            frames.add(f);
        }
        root.add("frames", frames);
        root.addProperty("readingLine", "Iron Ore: " + ground.veins().size() + " veins, nearest " + ground.nearest()
                + " blocks, down to Y " + ground.bottom());
        root.addProperty("recorderSpan", SeismicPonderBeats.Survey.RECORDER_SPAN);
        write("seismic_scanner_survey", root);
    }

    private static JsonArray floats(float[] v) {
        final JsonArray a = new JsonArray();
        for (float x : v) {
            a.add(Math.round(x * 1e4) / 1e4);
        }
        return a;
    }

    @Test
    void writesTheReadingScene() throws IOException {
        final int length = SeismicPonderBeats.Reading.LENGTH;
        final JsonObject root = head("seismic_scanner_reading", "Reading the map", "strip", length, SeismicStage.STRIP_PLATE,
                SeismicStage.STRIP_SCALE, SeismicStage.STRIP_OFFSET_Y, SeismicStage.STRIP_PITCH, SeismicStage.STRIP_YAW,
                SeismicStage.STRIP_PLATE_X);
        final int[] r = SeismicStage.STRIP_RIG;
        final int[][] g = SeismicStage.STRIP_GROUND;
        final JsonArray shows = new JsonArray();
        shows.add(show("show", 0, "up", new int[] {0, 0, 0, SeismicStage.STRIP_LENGTH - 1, 0, SeismicStage.STRIP_DEPTH - 1}));
        final int chunk = SeismicPonderBeats.Reading.CHUNK;
        for (int i = 0; i * chunk <= g[1][0]; i++) {
            final int x1 = g[1][0] - i * chunk;
            final int x0 = Math.max(g[0][0], x1 - chunk + 1);
            shows.add(show("show", SeismicPonderBeats.Reading.FIRST_CHUNK + i * SeismicPonderBeats.Reading.CHUNK_STEP, "down",
                    new int[] {x0, g[0][1], g[0][2], x1, g[1][1], g[1][2]}));
        }
        shows.add(show("show", SeismicPonderBeats.Reading.RIG, "down", new int[] {r[0], r[1], r[2], r[0], r[1], r[2]}));
        shows.add(show("show", SeismicPonderBeats.Reading.RIG + 3, "down", new int[] {r[0], r[1] + 1, r[2], r[0], r[1] + 1, r[2]}));
        root.add("sections", shows);
        root.add("cues", cues(SeismicPonderBeats.Reading.cues()));
        final JsonArray controls = new JsonArray();
        controls.add(control(SeismicPonderBeats.Reading.LEVER, SeismicPonderBeats.Reading.PULL - SeismicPonderBeats.Reading.LEVER,
                SeismicPonderBeats.lever(r), "right", "up"));
        root.add("controls", controls);
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Reading.ground();
        root.add("veins", veins(ground));
        final int impact = SeismicPonderBeats.Reading.impact();
        final JsonObject wave = new JsonObject();
        wave.addProperty("impact", impact);
        wave.add("strike", vec(r[0] + 0.5, r[1], r[2] + 0.5));
        wave.addProperty("fadeAt", 36.0);
        wave.add("faces", faces(SeismicScenes.stripFaces()));
        root.add("wave", wave);
        final Walker.Walk walk = SeismicPonderBeats.Reading.walk();
        final RigScript hero = SeismicPonderBeats.Reading.hero(ground);
        final RigLook look = new RigLook();
        final VeinView.Light light = new VeinView.Light(ground, impact);
        final JsonArray frames = new JsonArray();
        final double top = g[1][1] + 1;
        final double edge = g[1][2] + 1;
        final Walker.Walk atRig = new Walker.Walk(top, new int[] {0}, new double[][] {{r[0] + 0.5, r[2] + 0.5}});
        final List<Walker.Ring> witness = List.of(new Walker.Ring(8.0, Walker.WITNESS_RGB, 1f, "8 blocks"));
        final List<RingSet> ringSets = List.of(
                new RingSet(SeismicPonderBeats.Reading.WITNESS_RING, SeismicPonderBeats.Reading.WITNESS_RING_END, atRig, witness),
                new RingSet(SeismicPonderBeats.Reading.RINGS, SeismicPonderBeats.Reading.walkEnd() + 20, walk, List.of(
                        new Walker.Ring(VeinVisibility.NEAR, Walker.NEAR_RGB, 1f, "16 blocks"),
                        new Walker.Ring(VeinVisibility.FAR, Walker.FAR_RGB, 0.85f, "32 blocks"))),
                new RingSet(SeismicPonderBeats.Reading.TIP_RING, length, atRig, witness));
        float sight = 0f;
        int dwell = 0;
        for (int t = 0; t < length; t++) {
            if (t == 0) {
                look.settle(hero.at(0), 0);
            } else {
                look.step(hero.at(t), t);
            }
            final JsonObject f = new JsonObject();
            f.addProperty("tick", t);
            final JsonArray rigs = new JsonArray();
            rigs.add(rig("hero", r, hero, look, t));
            f.add("rigs", rigs);
            final Vec3 feet = walk.at(t);
            f.add("parrot", vec(feet.x, feet.y, feet.z));
            if (t >= impact) {
                dwell = walk.looking(t) ? dwell + 1 : 0;
                if (dwell >= SeismicPonderBeats.DWELL_TICKS) {
                    light.look(t);
                }
                final Vec3 e = walk.eye(t);
                light.step(t, e.x, e.y, e.z);
                f.add("shown", floats(light.shown));
            }

            final JsonArray sets = new JsonArray();
            final JsonArray tags = new JsonArray();
            for (RingSet set : ringSets) {
                set.step(t, top, edge, g[0][0], g[1][0] + 1, sets, tags);
            }
            f.add("ringSets", sets);
            f.add("tags", tags);
            sight = Math.max(0f, Math.min(1f, sight + (walk.looking(t) ? 0.125f : -0.125f)));
            f.addProperty("sight", sight);
            f.addProperty("recorder", t >= SeismicPonderBeats.Reading.RECORDER ? Math.min(1f, (t - SeismicPonderBeats.Reading.RECORDER) / 8f) : 0f);
            frames.add(f);
        }
        root.add("frames", frames);
        root.add("look", vec(r[0] + 0.5, r[1] + 1.0, r[2] + 0.5));
        root.addProperty("recorderSpan", SeismicPonderBeats.Reading.RECORDER_SPAN);
        root.addProperty("near", VeinVisibility.NEAR);
        root.addProperty("far", VeinVisibility.FAR);
        write("seismic_scanner_reading", root);
        assertTrue(SeismicCharge.MAX_RADIUS > 0 && SeismicRig.State.values().length > 0);
    }
}
