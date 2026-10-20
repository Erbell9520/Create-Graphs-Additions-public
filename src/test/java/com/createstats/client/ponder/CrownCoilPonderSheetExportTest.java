package com.createstats.client.ponder;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.core.CrownStuds;
import com.createstats.chunkloader.look.CrownAnchors;
import com.createstats.chunkloader.look.CrownFx;
import com.createstats.chunkloader.look.CrownMaterial;
import com.createstats.chunkloader.look.CrownMesh;
import com.createstats.chunkloader.look.CrownMotion;
import com.createstats.chunkloader.look.CrownPose;
import com.createstats.chunkloader.ponder.CrownPonderScript;
import com.createstats.stormcoil.CoilMotion;
import com.createstats.stormcoil.StormCoilStuds;
import com.createstats.stormcoil.ponder.CoilPonderScript;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownCoilPonderSheetExportTest {
    private static final Path OUT = Path.of("build", "crown-coil-ponder");

    private static double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static JsonArray vec(double... v) {
        final JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(r(d));
        }
        return a;
    }

    private static JsonArray cues(java.util.List<? extends Object> cues) {
        final JsonArray out = new JsonArray();
        int n = 1;
        for (Object o : cues) {
            final JsonObject c = new JsonObject();
            if (o instanceof CrownPonderScript.Cue k) {
                c.addProperty("name", k.name());
                c.addProperty("tick", k.tick());
                c.addProperty("hold", k.hold());
                if (k.target() != null) c.add("target", vec(k.target()));
                c.addProperty("free_y", k.freeY());
            } else if (o instanceof CoilPonderScript.Cue k) {
                c.addProperty("name", k.name());
                c.addProperty("tick", k.tick());
                c.addProperty("hold", k.hold());
                if (k.target() != null) c.add("target", vec(k.target()));
                c.addProperty("free_y", k.freeY());
            }
            c.addProperty("text", n++);
            out.add(c);
        }
        return out;
    }

    private static final class CrownDumpSink implements CrownMesh.Sink, CrownFx.Sink {
        final JsonArray quads = new JsonArray();
        final JsonArray glows = new JsonArray();
        final JsonArray strokes = new JsonArray();
        final double ox;
        final double oy;
        final double oz;

        CrownDumpSink(int[] origin) {
            ox = origin[0] * 16.0;
            oy = origin[1] * 16.0;
            oz = origin[2] * 16.0;
        }

        @Override
        public void quad(float[] p, float[] uv, float nx, float ny, float nz, CrownMaterial material, boolean glow) {
            final JsonArray q = new JsonArray();
            for (int i = 0; i < 4; i++) {
                q.add(r(ox + p[i * 3]));
                q.add(r(oy + p[i * 3 + 1]));
                q.add(r(oz + p[i * 3 + 2]));
            }
            q.add(r(nx));
            q.add(r(ny));
            q.add(r(nz));
            q.add(material.name());
            q.add(glow ? 1 : 0);
            quads.add(q);
        }

        @Override
        public void glow(double x, double y, double z, double radius, int rgb, double alpha, double core, double nudge) {
            if (alpha < 0.05) return;
            final JsonArray g = vec(ox + x, oy + y, oz + z, radius, alpha);
            g.add(rgb);
            glows.add(g);
        }

        @Override
        public void stroke(double ax, double ay, double az, double bx, double by, double bz, double core, double sheath,
                           int coreRgb, int sheathRgb, double alpha) {
            if (alpha < 0.05) return;
            final JsonArray s = vec(ox + ax, oy + ay, oz + az, ox + bx, oy + by, oz + bz, alpha);
            s.add(sheathRgb);
            strokes.add(s);
        }

        @Override
        public void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0, double alpha1,
                         boolean floor) {
        }

        @Override
        public void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb,
                          int glowRgb, double alpha) {
        }

        @Override
        public void star(double x, double y, double z, double size, double alpha) {
        }

        @Override
        public void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha) {
        }

        @Override
        public void spark(double x, double y, double z) {
        }
    }

    @Test
    void exportCrown() throws IOException {
        final JsonObject root = new JsonObject();
        root.addProperty("scene", "spark_crown");
        root.addProperty("stage", "spark_crown/station");
        root.addProperty("plate", CrownPonderScript.PLATE);
        root.addProperty("scale", CrownPonderScript.SCALE);
        root.addProperty("offset_y", CrownPonderScript.OFFSET_Y);
        root.addProperty("length", CrownPonderScript.LENGTH);
        root.add("crown", vec(CrownPonderScript.CROWN[0], CrownPonderScript.CROWN[1], CrownPonderScript.CROWN[2]));
        root.add("cues", cues(CrownPonderScript.cues()));

        final JsonObject points = new JsonObject();
        for (int stud = 0; stud < 2; stud++) {
            final double[] p = CrownStuds.attachPoint(CrownStuds.turnsFor("south"), stud);
            points.add(stud == 0 ? "stud_a" : "stud_b", vec(CrownPonderScript.CROWN[0] + p[0],
                    CrownPonderScript.CROWN[1] + p[1], CrownPonderScript.CROWN[2] + p[2]));
        }
        breakerStuds(points, CrownPonderScript.BREAKER);
        root.add("points", points);

        root.add("tag_at", vec(CrownPonderScript.TAG_AT));
        root.add("tag_offset", vec(CrownPonderScript.TAG_DX, CrownPonderScript.TAG_DY));

        final JsonObject keep = new JsonObject();
        final int[] c = CrownPonderScript.CROWN;
        keep.add("the crown", vec(c[0] - 1, c[1], c[2] - 1, c[0] + 2, c[1] + 3, c[2] + 2));
        final int[] a = CrownPonderScript.CONNECTOR_A;
        final int[] b = CrownPonderScript.CONNECTOR_B;
        keep.add("the lines", vec(a[0], a[1] - 1, a[2], b[0] + 1, a[1] + 0.6, c[2]));
        final int[] k = CrownPonderScript.BREAKER;
        keep.add("the breaker", vec(k[0], k[1], k[2], k[0] + 1, k[1] + 0.5, k[2] + 1));
        final int[] m = CrownPonderScript.MAP_CENTRE;
        keep.add("the map", vec(m[0] - 2, m[1], m[2] - 2, m[0] + 3, m[1] + 0.1, m[2] + 3));
        root.add("keep_clear", keep);

        final TreeSet<Integer> ticks = new TreeSet<>();
        for (CrownPonderScript.Cue cue : CrownPonderScript.cues()) {
            ticks.add(cue.tick() + 10);
        }
        ticks.add(30);
        ticks.add(CrownPonderScript.LINE_A + 12);
        ticks.add(CrownPonderScript.firstDark(CrownPonderScript.CUT) + 1);
        ticks.add(CrownPonderScript.LENGTH - 5);

        final JsonArray frames = new JsonArray();
        final CrownMotion motion = new CrownMotion();
        final CrownMesh mesh = new CrownMesh();
        final CrownFx fx = new CrownFx();
        final CrownAnchors anchors = new CrownAnchors();
        final CrownPose pose = new CrownPose();
        for (int t = 0; t <= CrownPonderScript.LENGTH; t++) {
            final CrownPonderScript.Frame f = CrownPonderScript.at(t);
            motion.tick(f.state());
            if (!ticks.contains(t)) continue;
            motion.pose(1f, pose);
            final CrownDumpSink sink = new CrownDumpSink(CrownPonderScript.CROWN);
            mesh.build(pose, anchors, sink, CrownMesh.LOD_FULL);
            fx.emit(pose, anchors, sink, CrownMesh.LOD_FULL);
            final CrownState s = f.state();
            final JsonObject fr = new JsonObject();
            fr.addProperty("tick", t);
            fr.addProperty("held", s.held());
            fr.addProperty("phase", s.phase().name());
            fr.addProperty("draw", s.drawFePerTick());
            fr.addProperty("draw_text", String.format(Locale.ROOT, "%,d FE/t", s.drawFePerTick()));
            fr.addProperty("draw_rgb", s.held() <= 0 ? 0x9AA3AD : s.phase() == CrownHold.Phase.RIDING ? 0xFFB347 : 0xFFFFFF);
            fr.addProperty("tag_shown", t >= CrownPonderScript.firstHolding(1, 0));
            fr.addProperty("line_a", f.lineA());
            fr.addProperty("line_b", f.lineB());
            fr.addProperty("closed", f.closed());
            final int[] split = CrownStuds.split(s.intakeFePerTick(), s.studARating(), s.studBRating(), s.studAWired(),
                    s.studBWired());
            fr.add("flow", vec(split[0], split[1]));
            fr.addProperty("motion", motion.kind().name());
            fr.add("quads", sink.quads);
            fr.add("glows", sink.glows);
            fr.add("strokes", sink.strokes);
            frames.add(fr);
        }
        root.add("frames", frames);
        write("spark_crown", root);
    }

    @Test
    void exportCoil() throws IOException {
        final JsonObject root = new JsonObject();
        root.addProperty("scene", "storm_coil");
        root.addProperty("stage", "storm_coil/yard");
        root.addProperty("plate", CoilPonderScript.PLATE);
        root.addProperty("scale", CoilPonderScript.SCALE);
        root.addProperty("offset_y", CoilPonderScript.OFFSET_Y);
        root.addProperty("length", CoilPonderScript.LENGTH);
        final int[] c = CoilPonderScript.COIL;
        root.add("coil", vec(c[0], c[1], c[2]));
        final JsonObject points = new JsonObject();
        for (int stud = 0; stud < 2; stud++) {
            final double[] p = StormCoilStuds.anchor(1, stud);
            points.add(stud == StormCoilStuds.IN ? "stud_in" : "stud_out", vec(c[0] + p[0], c[1] + p[1], c[2] + p[2]));
        }
        breakerStuds(points, CoilPonderScript.BREAKER);
        root.add("points", points);
        root.add("cues", cues(CoilPonderScript.cues()));
        final JsonObject keep = new JsonObject();

        keep.add("the coil", vec(c[0] - 1, c[1], c[2] - 1, c[0] + 2, c[1] + 4.5, c[2] + 2));
        final int[] in = CoilPonderScript.IN_CONNECTOR;
        keep.add("IN's wire", vec(c[0] + 0.5, c[1] - 1, in[2], in[0] + 1, in[1] + 0.6, c[2]));
        final int[] out = CoilPonderScript.OUT_CONNECTOR;
        keep.add("OUT's wire and the range", vec(out[0], out[1] - 1, out[2], c[0] + 0.5, out[1] + 0.6, c[2]));
        for (int i = 0; i < CoilPonderScript.HUSKS; i++) {
            final double[] h = CoilPonderScript.HUSK_AT[i];
            keep.add("husk " + (i + 1), vec(h[0] - 0.35, h[1], h[2] - 0.35, h[0] + 0.35, h[1] + 1.95, h[2] + 0.35));
        }
        final double[] p = CoilPonderScript.PIG_AT;
        keep.add("the pig", vec(p[0] - 0.45, p[1], p[2] - 0.45, p[0] + 0.45, p[1] + 0.9, p[2] + 0.45));
        root.add("keep_clear", keep);
        final JsonArray husks = new JsonArray();
        for (double[] h : CoilPonderScript.HUSK_AT) {
            husks.add(vec(h));
        }
        root.add("husks", husks);
        root.add("pig", vec(p));

        final TreeSet<Integer> ticks = new TreeSet<>();
        for (CoilPonderScript.Cue cue : CoilPonderScript.cues()) {
            ticks.add(cue.tick() + 10);
        }
        ticks.add(30);
        ticks.add(CoilPonderScript.HEIGHT + CoilPonderScript.STAGE_STEP + 9);
        ticks.add(CoilPonderScript.FIRST_VOLLEY + 2);
        ticks.add(CoilPonderScript.STRIKE - 20);
        ticks.add(CoilPonderScript.STRIKE + 1);

        final JsonArray frames = new JsonArray();
        final CoilMotion motion = new CoilMotion();
        for (int t = 0; t <= CoilPonderScript.LENGTH; t++) {
            final CoilPonderScript.Frame f = CoilPonderScript.at(t);

            motion.aim(f.stage());
            motion.tick(f.capacitor() > 0 || f.bank() > 0);
            if (!ticks.contains(t)) continue;
            final JsonObject fr = new JsonObject();
            fr.addProperty("tick", t);
            fr.addProperty("stage", f.stage());
            fr.addProperty("height", r(motion.height(1f)));
            fr.addProperty("settle", r(motion.settle(1f)));
            fr.addProperty("cap", r(f.capacitorSize() <= 0 ? 0 : f.capacitor() / (double) f.capacitorSize()));
            fr.addProperty("bank", r(f.bankSize() <= 0 ? 0 : f.bank() / (double) f.bankSize()));
            fr.addProperty("in", f.in());
            fr.addProperty("out", f.out());
            fr.addProperty("in_wired", f.inWired());
            fr.addProperty("out_wired", f.outWired());
            final long since = f.lastZap() == Long.MIN_VALUE ? Long.MAX_VALUE : t - f.lastZap();
            fr.addProperty("firing", since < 6 ? r(1.0 - since / 6.0) : 0.0);
            fr.addProperty("volley", f.volley());
            fr.addProperty("primed", f.primed());
            fr.addProperty("corona", f.corona());
            fr.addProperty("bolt", t >= CoilPonderScript.STRIKE && t < CoilPonderScript.STRIKE + 10);
            fr.addProperty("mobs", t >= CoilPonderScript.MOBS);
            final int last = CoilPonderScript.lastVolley();
            fr.addProperty("husk_death", last >= 0 && t > last ? Math.min(20, t - last) : 0);
            fr.addProperty("husk_burning", f.huskHits() > 0);
            fr.addProperty("husks_gone", last >= 0 && t - last >= 20);
            fr.addProperty("range_hot", f.out() > 0);
            frames.add(fr);
        }
        root.add("frames", frames);
        write("storm_coil", root);
    }

    private static void breakerStuds(JsonObject points, int[] at) {
        for (com.createstats.power.MainBreakerStuds.Stud s : com.createstats.power.MainBreakerStuds.Stud.values()) {
            final net.minecraft.world.phys.Vec3 a = com.createstats.power.MainBreakerStuds.anchor(
                    net.minecraft.core.Direction.UP, s);
            points.add("breaker_" + s.name().toLowerCase(Locale.ROOT), vec(at[0] + a.x, at[1] + a.y, at[2] + a.z));
        }
    }

    private static void write(String name, JsonObject root) throws IOException {
        Files.createDirectories(OUT);
        final Path file = OUT.resolve(name + ".json");
        Files.writeString(file, new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
        assertTrue(Files.size(file) > 100, "nothing written to " + file);
    }
}
