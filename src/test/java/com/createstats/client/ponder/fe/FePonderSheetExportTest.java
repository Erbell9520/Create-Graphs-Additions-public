package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.Ease;
import com.createstats.gui.pdu.PduGlyphs;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.CableTier;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeGoggle;
import com.createstats.power.FePriority;
import com.createstats.power.FeRates;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.power.ponder.FePonderChapters;
import com.createstats.power.ponder.FePonderGrid;
import com.createstats.power.ponder.FePonderChapters.Cue;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderSheetExportTest {
    private static final Path OUT = Path.of("build", "fe-ponder");
    private static final Path SCENES = Path.of("src/main/java/com/createstats/client/FeScenes.java");

    private static final Pattern SHOW = Pattern.compile(
            "showText\\(\\s*sheet\\.get\\(\"([a-z_]+)\"\\)\\.hold\\(\\)\\)((?:[^;\"]|\"(?:[^\"\\\\]|\\\\.)*\")*);");
    private static final Pattern PALETTE = Pattern.compile("\\.colored\\(PonderPalette\\.([A-Z]+)\\)");

    private abstract static class Part {
        final int start;
        final int end;

        Part(int start, int ticks) {
            this.start = start;
            this.end = start + Math.max(1, ticks);
        }

        void begin() {
        }

        abstract void step(int t, boolean last);

        abstract void write(Frame f, int t);
    }

    private static final class Frame {
        final JsonObject json = new JsonObject();
        final JsonArray beads = new JsonArray();
        final JsonArray labels = new JsonArray();

        Frame() {
            json.add("beads", beads);
            json.add("labels", labels);
        }

        void label(String kind, Vec3 anchor, int dx, int dy, String text, int rgb, double alpha) {
            label(kind, anchor, dx, dy, false, text, rgb, alpha);
        }

        void label(String kind, Vec3 anchor, int dx, int dy, boolean leftOf, String text, int rgb, double alpha) {
            if (text == null || text.isEmpty() || alpha < 0.05) return;
            final JsonObject o = new JsonObject();
            o.addProperty("kind", kind);
            o.add("anchor", vec(anchor));
            o.addProperty("dx", dx);
            o.addProperty("dy", dy);
            if (leftOf) o.addProperty("leftOf", true);
            o.addProperty("text", text);
            o.addProperty("rgb", String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF));
            o.addProperty("alpha", round(alpha));
            labels.add(o);
        }
    }

    private static JsonArray vec(Vec3 v) {
        final JsonArray a = new JsonArray();
        a.add(round(v.x));
        a.add(round(v.y));
        a.add(round(v.z));
        return a;
    }

    private static JsonArray pos(BlockPos p) {
        final JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static float fade(int t, int start, int ticks) {
        final int total = Math.max(ticks, 2 * FeLabels.FADE + 1);
        final int in = t - start + 1;
        final int out = start + total - t - 1;
        return (float) Ease.SMOOTH.clamped(Math.min(1f, Math.min(in / (float) FeLabels.FADE, out / (float) FeLabels.FADE)));
    }

    private static final class Chapter {
        final String id;
        final String stage;
        final int plate;
        final float scale;
        final Sheet sheet;
        final List<Part> parts = new ArrayList<>();
        final JsonObject json = new JsonObject();
        final JsonArray reveals = new JsonArray();
        final JsonArray controls = new JsonArray();
        final JsonArray events = new JsonArray();

        final JsonArray placed = new JsonArray();
        final JsonArray removed = new JsonArray();
        final JsonArray streams = new JsonArray();
        final JsonArray spans = new JsonArray();
        Recording rec;
        Warp warp;
        IntFunction<JsonObject> states = t -> new JsonObject();

        Chapter(String id, String stage, int plate, float scale, Sheet sheet) {
            this.id = id;
            this.stage = stage;
            this.plate = plate;
            this.scale = scale;
            this.sheet = sheet;
        }

        void reveal(int tick, List<BlockPos> blocks) {
            for (BlockPos p : blocks) {
                final JsonObject o = new JsonObject();
                o.add("pos", pos(p));
                o.addProperty("tick", tick);
                reveals.add(o);
            }
        }

        int build(int from, List<FeLayout.Reveal> steps) {
            int t = from;
            for (FeLayout.Reveal s : steps) {
                reveal(t, s.blocks());
                t += s.then();
            }
            return t;
        }

        void control(int tick, int ticks, Vec3 at, String item, String button) {
            control(tick, ticks, at, item);
            controls.get(controls.size() - 1).getAsJsonObject().addProperty("button", button);
        }

        void place(int tick, String name, BlockPos... at) {
            for (BlockPos p : at) {
                final JsonObject o = new JsonObject();
                o.add("pos", pos(p));
                o.addProperty("name", name);
                o.addProperty("tick", tick);
                placed.add(o);
            }
        }

        void remove(int tick, BlockPos at) {
            final JsonObject o = new JsonObject();
            o.add("pos", pos(at));
            o.addProperty("tick", tick);
            removed.add(o);
        }

        void control(int tick, int ticks, Vec3 at, String item) {
            final JsonObject o = new JsonObject();
            o.addProperty("tick", tick);
            o.addProperty("ticks", ticks);
            o.add("anchor", vec(at));
            if (item != null) o.addProperty("item", item);
            controls.add(o);
        }

        void event(String kind, int tick, Vec3 at) {
            final JsonObject o = new JsonObject();
            o.addProperty("kind", kind);
            o.addProperty("tick", tick);
            o.add("anchor", vec(at));
            events.add(o);
        }

        int path(String name, FePaths.Path p) {
            final JsonObject o = new JsonObject();
            o.addProperty("name", name);
            final JsonArray pts = new JsonArray();
            for (Vec3 v : p.points()) pts.add(vec(v));
            o.add("points", pts);
            streams.add(o);
            return streams.size() - 1;
        }

        int leg(int i) {
            final JsonObject o = new JsonObject();
            o.addProperty("name", "leg" + i);
            o.addProperty("leg", i);
            o.add("stud", vec(FePaths.studPoint(FeStage.PDU, Direction.NORTH, FeStage.STUD[i])));
            o.add("far", pos(FeStage.LEG_END[i]));
            streams.add(o);
            return streams.size() - 1;
        }

        int spanStream(String name, BlockPos from, BlockPos to, WireTension tension, FePaths.Path standIn) {
            final JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.add("from", pos(from));
            o.add("to", pos(to));
            o.addProperty("slack", tension.slack());
            o.addProperty("standIn", round(standIn.length()));
            streams.add(o);
            return streams.size() - 1;
        }

        int portStream(String name, Vec3 from, BlockPos to, FePaths.Path standIn, boolean reverse) {
            final JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.add("start", vec(from));
            o.add("to", pos(to));
            o.addProperty("slack", WireTension.STANDARD.slack());
            o.addProperty("standIn", round(standIn.length()));
            if (reverse) o.addProperty("reverse", true);
            streams.add(o);
            return streams.size() - 1;
        }

        void reverse(int i) {
            streams.get(i).getAsJsonObject().addProperty("reverse", true);
        }

        void portSpan(Vec3 from, BlockPos to, WireTier tier, int tick, int ticks) {
            final JsonObject o = new JsonObject();
            o.add("start", vec(from));
            o.add("to", pos(to));
            o.addProperty("tier", tier.ordinal());
            o.addProperty("tick", tick);
            o.addProperty("ticks", ticks);
            final JsonArray h = new JsonArray();
            final JsonArray s = new JsonArray();
            s.add(0);
            s.add(WireTension.STANDARD.id());
            h.add(s);
            o.add("tension", h);
            spans.add(o);
        }

        void span(BlockPos from, BlockPos to, WireTier tier, int tick, int ticks, int until, int[][] hung) {
            final JsonObject o = new JsonObject();
            o.add("from", pos(from));
            o.add("to", pos(to));
            o.addProperty("tier", tier.ordinal());
            o.addProperty("tick", tick);
            o.addProperty("ticks", ticks);
            if (until < Integer.MAX_VALUE) o.addProperty("until", until);
            final JsonArray h = new JsonArray();
            for (int[] step : hung) {
                final JsonArray s = new JsonArray();
                s.add(step[0]);
                s.add(step[1]);
                h.add(s);
            }
            o.add("tension", h);
            spans.add(o);
        }

        void add(Part p) {
            parts.add(p);
        }
    }

    private static Part beads(Chapter ch, int[] index, List<FeFlow.Stream> streams, int start, int ticks) {
        return new Part(start, ticks) {
            FeFlow.Emitter e;

            @Override
            void begin() {
                e = new FeFlow.Emitter(streams);
            }

            @Override
            void step(int t, boolean last) {
                e.step(t);
            }

            @Override
            void write(Frame f, int t) {
                for (double[] b : e.beads) {
                    final double len = streams.get((int) b[0]).path().length();
                    final double taper = FeFlow.taper(b[1], len);
                    if (taper <= 0.02) continue;
                    final JsonArray a = new JsonArray();
                    a.add(index[(int) b[0]]);
                    a.add(round(b[1]));
                    a.add(round(taper));
                    a.add(round(streams.get((int) b[0]).width()));
                    f.beads.add(a);
                }
            }
        };
    }

    private static Part dash(FeDash.Model m, int stripFrom, int start, int ticks) {
        return new Part(start, ticks) {
            @Override
            void begin() {
                m.appear = start;
                m.flyStart = stripFrom;
                m.step(start);
                m.settle(start);
            }

            @Override
            void step(int t, boolean last) {
                m.step(t);
            }

            @Override
            void write(Frame f, int t) {
                final JsonObject o = new JsonObject();
                o.addProperty("fill", round(m.fill));
                o.addProperty("grow", round(Ease.OUT_CUBIC.clamped(m.grow)));
                if (m.marks) {
                    o.addProperty("floor", m.rec.setup.tuning().floorPct() / 100.0);
                    o.addProperty("restore", m.rec.setup.tuning().restorePct() / 100.0);
                }
                if (m.strip()) {
                    final JsonArray bands = new JsonArray();
                    for (int i : m.order) {
                        final JsonObject b = new JsonObject();
                        b.addProperty("machine", i);
                        b.addProperty("start", m.startOf(i));
                        b.addProperty("draw", m.rec.machine(i).draw());
                        b.addProperty("fly", round(m.fly[i]));
                        b.addProperty("dim", round(m.dim[i]));
                        b.addProperty("rgb", String.format(Locale.ROOT, "#%06X", tierColour(m.rec.machine(i).tier())));
                        b.add("from", vec(m.from[i]));
                        bands.add(b);
                    }
                    o.add("bands", bands);
                    o.addProperty("total", m.total);
                    o.addProperty("served", round(m.served));
                    o.addProperty("servedNow", m.servedNow());
                }
                if (m.clock.level > 0.05f && !m.clock.shown.isEmpty()) {
                    o.addProperty("clock", m.clock.shown);
                    o.addProperty("clockAlpha", round(Ease.SMOOTH.clamped(m.clock.level)));
                }
                f.json.add("dash", o);
            }
        };
    }

    private static Part flag(String kind, Vec3 anchor, IntFunction<String> text, IntFunction<Integer> rgb, int dx, int dy,
                             boolean leftOf, int hold, int start, int ticks) {
        return new Part(start, ticks) {
            final FeLabels.Fader fader = new FeLabels.Fader(hold);

            @Override
            void step(int t, boolean last) {
                fader.follow(text.apply(t), rgb.apply(t), last);
            }

            @Override
            void write(Frame f, int t) {
                f.label(kind, anchor, dx, dy, leftOf, fader.shown, fader.rgb, Ease.SMOOTH.clamped(fader.level));
            }
        };
    }

    private static Part tag(Vec3 anchor, IntFunction<String> text, int rgb, int dx, int dy, int start, int ticks) {
        return new Part(start, Math.max(ticks, 2 * FeLabels.FADE + 1)) {
            @Override
            void step(int t, boolean last) {
            }

            @Override
            void write(Frame f, int t) {
                f.label("tag", anchor, dx, dy, text.apply(t), rgb, fade(t, start, end - start));
            }
        };
    }

    private static Part tagLeft(Vec3 anchor, IntFunction<String> text, int rgb, int gap, int dy, int start, int ticks) {
        return new Part(start, Math.max(ticks, 2 * FeLabels.FADE + 1)) {
            @Override
            void step(int t, boolean last) {
            }

            @Override
            void write(Frame f, int t) {
                f.label("tag", anchor, gap, dy, true, text.apply(t), rgb, fade(t, start, end - start));
            }
        };
    }

    private static Part chip(Vec3 anchor, IntFunction<String> job, int start, int ticks) {
        return new Part(start, Math.max(ticks, 2 * FeLabels.FADE + 1)) {
            @Override
            void step(int t, boolean last) {
            }

            @Override
            void write(Frame f, int t) {
                final String j = job.apply(t);
                final int rise = (int) Math.round(4.0 * Ease.OUT_CUBIC.clamped((t - start + 1) / (double) FeLabels.FADE));
                f.label("chip", anchor, 0, -rise, j, tierColour(tierOf(j)), fade(t, start, end - start));
            }
        };
    }

    private static Part gauge(Vec3 from, Vec3 to, IntToDoubleFunction share, int start, int ticks) {
        return new Part(start, ticks) {
            @Override
            void step(int t, boolean last) {
            }

            @Override
            void write(Frame f, int t) {
                final JsonObject o = new JsonObject();
                o.add("from", vec(from));
                o.add("to", vec(to));
                o.addProperty("share", round(share.applyAsDouble(t)));
                o.addProperty("grow", round(Ease.OUT_CUBIC.clamped(Math.min(1f, (t - start + 1) / 16f))));
                f.json.add("gauge", o);
            }
        };
    }

    private static Part goggles(Vec3 anchor, IntFunction<List<Component>> lines, int start, int ticks) {
        return new Part(start, ticks) {
            float level;

            @Override
            void step(int t, boolean last) {
                final boolean shown = t - start < (end - start) - FeGoggles.FADE;
                level = shown ? Math.min(1f, level + 1f / FeGoggles.FADE) : Math.max(0f, level - 1f / FeGoggles.FADE);
            }

            @Override
            void write(Frame f, int t) {
                if (level < 0.03f) return;
                final JsonObject o = new JsonObject();
                o.add("anchor", vec(anchor));
                o.addProperty("level", round(level));
                final JsonArray a = new JsonArray();
                for (Component c : lines.apply(t)) a.add(c.getString());
                o.add("lines", a);
                f.json.add("goggles", o);
            }
        };
    }

    private static Part wire(double[] grown, int leg, int start, int ticks) {
        return new Part(start, ticks) {
            @Override
            void step(int t, boolean last) {
                grown[leg] = last ? 1.0
                        : Ease.OUT_CUBIC.clamped(Math.min(1f, (t - start + 1) / (float) Math.max(1, ticks - 1)));
            }

            @Override
            void write(Frame f, int t) {
            }
        };
    }

    private static int tierColour(int tier) {
        return PduGlyphs.tierColour(FePriority.byOrdinal(tier)) & 0xFFFFFF;
    }

    private static int tierOf(String shortName) {
        for (FePriority p : FePriority.values()) if (p.getShortName().equals(shortName)) return p.ordinal();
        return FePriority.DEFAULT.ordinal();
    }

    private static String fe(int fePerTick) {
        return FeRates.perTick(fePerTick);
    }

    private static Chapter cables(Tuning tune) {
        final Sheet sheet = FePonderChapters.Cables.sheet();
        final Chapter ch = new Chapter("fe_cables", "bench", FeStage.BENCH_PLATE, FeStage.BENCH_SCALE, sheet);
        final int spin = sheet.tick("power");
        final int gauge = sheet.tick("gauge");
        final int ladder = sheet.tick("ladder");
        final int lamp = sheet.tick("lamp");
        final int goggles = sheet.tick("goggles");
        final int rip = FePonderChapters.Cables.RIP;
        final int ripped = FePonderChapters.Cables.RIPPED;
        final int end = sheet.length();
        final int[] flow = FePonderChapters.Cables.flow(tune, end);
        final int rating = FeRates.cable(CableTier.SURFACE);
        final java.util.function.IntUnaryOperator carried = t -> t <= spin || t >= ripped ? 0
                : flow[Math.min(flow.length - 1, t - spin - 1)];

        ch.build(10, FeLayout.benchBuild());
        ch.reveal(sheet.tick("anchor"), List.of(FeStage.BENCH_RUN[0]));
        ch.build(sheet.tick("route"), FeLayout.benchRoute());
        for (int i = 0; i < CableTier.values().length; i++) {
            ch.reveal(ladder + FeLayout.LADDER_STEP * i, FeLayout.ladderRow(i));
        }
        ch.reveal(lamp, List.of(FeStage.BENCH_LAMP_BASE));
        ch.reveal(lamp + FeLayout.LAMP_STEP, List.of(FeStage.BENCH_LAMP));
        final int lit = lamp + 2 * FeLayout.LAMP_STEP;
        final int dark = ripped + FePonderChapters.Cables.LAMP_COAST;
        ch.states = t -> {
            final JsonObject o = new JsonObject();
            o.addProperty("benchLamp", t >= lit && t < dark);
            o.addProperty("spinning", t >= spin);
            return o;
        };
        ch.control(FePonderChapters.Cables.LAY, 30, FeLayout.benchAnchor(), "surface_cable");
        ch.control(sheet.tick("route") - 8, 30, FeLayout.benchMachine(), "surface_cable");

        final BlockPos[] runBlocks = FeStage.BENCH_RUN;
        ch.control(sheet.tick("dye"), 30, top(runBlocks[runBlocks.length / 2], 0.3), "blue_dye");
        ch.control(rip, 30, top(runBlocks[1], 0.3), "surface_cable", "sneak");
        for (BlockPos p : runBlocks) ch.remove(ripped, p);
        ch.event("drop", ripped, top(runBlocks[1], 0.3));

        final int run = ch.path("run", FeLayout.benchRun());
        ch.add(beads(ch, new int[] {run}, List.of(new FeFlow.Stream(FeLayout.benchRun(), carried::applyAsInt)), spin,
                ripped - spin));
        ch.add(gauge(FeStage.GAUGE_FROM, FeStage.GAUGE_TO, t -> carried.applyAsInt(t) / (double) rating, gauge,
                rip - gauge));
        ch.add(tag(FeStage.GAUGE_FROM.add(0.1, 0.0, 0.0),
                t -> String.format(Locale.ROOT, "%,d / %s", carried.applyAsInt(t), fe(rating)), FeFlow.GREEN, 6, -5,
                gauge + 6, rip - gauge - 6));
        for (int i = 0; i < CableTier.values().length; i++) {
            final int at = ladder + FeLayout.LADDER_STEP * i;
            final String figure = fe(FeRates.cable(CableTier.values()[i]));
            ch.add(tag(FeLayout.ladderTag(i), t -> figure, 0xE4E9EF, 4, -5, at + 4, goggles - at - 4));
        }
        final int toLamp = ch.path("lamp", FeLayout.benchLampRun());
        ch.add(beads(ch, new int[] {toLamp}, List.of(new FeFlow.Stream(FeLayout.benchLampRun(), t -> 1.0)), lit,
                ripped - lit));
        ch.add(goggles(FeLayout.benchGoggles(), t -> List.of(FeGoggle.title(CableTier.SURFACE.getDisplayName()),
                FeGoggle.carrying(carried.applyAsInt(t), rating), FeGoggle.capacity(rating)), goggles,
                FePonderChapters.Cables.GOGGLES_END - goggles));
        ch.json.add("targets", targets(FeLayout.cables()));
        ch.json.add("args", args(Map.of("gauge_caption", List.of(fe(rating)))));
        return ch;
    }

    private static Chapter pdu(Tuning tune) {
        Recording rec = FePonderChapters.Pdu.replay(tune);
        if (FePonderChapters.Pdu.triage(rec) < 0) rec = FePonderChapters.Pdu.replay(Tuning.defaults());
        final Recording r = rec;
        final Warp warp = FePonderChapters.Pdu.warp(r);
        final Sheet sheet = FePonderChapters.Pdu.sheet(r, warp);
        final Chapter ch = new Chapter("fe_pdu", "workshop", FeStage.WORKSHOP_PLATE, FeStage.WORKSHOP_SCALE, sheet);
        ch.rec = r;
        ch.warp = warp;
        final int end = sheet.length();
        final int place = sheet.tick("place");
        final int legsAt = sheet.tick("legs");
        final int tiersAt = sheet.tick("tiers");
        final int power = sheet.tick("power");
        final int lever = sheet.tick("lever");
        final int wireTicks = FePonderChapters.Pdu.WIRE_TICKS;
        final int wireGap = FePonderChapters.Pdu.WIRE_GAP;

        ch.build(10, FeLayout.plant(false));
        ch.reveal(place, List.of(FeStage.PDU));
        final double[] grown = new double[FeStage.MACHINE.length];
        ch.json.addProperty("wiresFromSchematic", false);
        final int appliances = power - 10;
        ch.states = t -> workshopStates(r, warp, t, appliances, lever, Integer.MAX_VALUE, grown);
        ch.control(legsAt, 40, new Vec3(FeStage.PDU.getX() + 0.5, FeStage.PDU.getY() + 1.3, FeStage.PDU.getZ() + 0.5),
                "copper_wire");
        ch.control(tiersAt, 30, FePaths.faceCentre(FeStage.PDU, Direction.NORTH), null);
        ch.control(lever - 12, 24, leverTop(), null);
        ch.event("sparkle", sheet.tick("payoff"), centre(FeStage.FAN_MOTOR));
        ch.control(sheet.tick("screen"), 30, FePaths.faceCentre(FeStage.PDU, Direction.NORTH), null);

        for (int i = 0; i < FeStage.MACHINE.length; i++) ch.add(wire(grown, i, legsAt + i * wireGap, wireTicks));
        chips(ch, r, warp, tiersAt, end, t -> t >= tiersAt + 20);
        ch.add(dash(new FeDash.Model(r, warp, false, chipPoints()), appliances, appliances, end - appliances));
        ch.add(beads(ch, trunkIndex(ch), trunk(r, warp), appliances, end - appliances));
        reasons(ch, r, warp, power, end);
        ch.add(beads(ch, legIndex(ch), legs(r, warp), power + 2, end - power - 2));
        ch.json.add("targets", targets(FeLayout.pdu()));
        return ch;
    }

    private static Chapter auto(Tuning tune) {
        Recording rec = FePonderChapters.Auto.replay(tune);
        if (FePonderChapters.Auto.mark(rec) < 0 || FePonderChapters.Auto.deeper(rec) < 0
                || FePonderChapters.Auto.restored(rec) < 0) {
            rec = FePonderChapters.Auto.replay(Tuning.defaults());
        }
        final Recording r = rec;
        final Warp warp = FePonderChapters.Auto.warp(r);
        final Sheet sheet = FePonderChapters.Auto.sheet(r, warp);
        final Chapter ch = new Chapter("fe_pdu_auto", "workshop", FeStage.WORKSHOP_PLATE, FeStage.WORKSHOP_SCALE, sheet);
        ch.rec = r;
        ch.warp = warp;
        final int end = sheet.length();
        final int start = sheet.tick("running");
        final int lever = sheet.tick("lever");
        final int back = sheet.tick("back");
        final int stripAt = sheet.tick("strip");

        ch.build(10, FeLayout.plant(true));
        final double[] grown = {1, 1, 1, 1, 1};
        ch.json.addProperty("wiresFromSchematic", true);
        ch.states = t -> workshopStates(r, warp, t, start, lever, back, grown);
        ch.control(lever - 12, 24, leverTop(), null);
        ch.control(back - 12, 24, leverTop(), null);
        ch.event("sparkle", sheet.tick("payoff"), new Vec3(FeStage.PDU.getX() + 0.5, FeStage.PDU.getY() + 1.2,
                FeStage.PDU.getZ() + 0.5));
        ch.control(sheet.tick("locks"), 30, FePaths.faceCentre(FeStage.PDU, Direction.NORTH), null);

        ch.add(beads(ch, trunkIndex(ch), trunk(r, warp), start, end - start));
        ch.add(beads(ch, legIndex(ch), legs(r, warp), start, end - start));
        ch.add(dash(new FeDash.Model(r, warp, true, chipPoints()), stripAt, start + 2, end - start - 2));
        chips(ch, r, warp, stripAt, end, t -> true);
        reasons(ch, r, warp, stripAt, end);
        ch.json.add("targets", targets(Map.of()));
        return ch;
    }

    private static Chapter wires(Tuning tune) {
        final FePonderGrid.Recording r = FePonderChapters.Spans.replay(tune);
        final Sheet sheet = FePonderChapters.Spans.sheet();
        final Chapter ch = new Chapter("fe_wires", "spans", FeStage.SPANS_PLATE, FeStage.SPANS_SCALE, sheet);
        final int end = sheet.length();
        final BlockPos[] a = FeStage.SPANS_A;
        final BlockPos b = FeStage.SPANS_B;
        final int power = FePonderChapters.Spans.POWER;
        final int range = FePonderChapters.Spans.RANGE;
        final int lamp = FePonderChapters.Spans.LAMP;
        final int spanTicks = FePonderChapters.Spans.SPAN_TICKS;
        final int stripped = FePonderChapters.Spans.STRIPPED;
        final int never = Integer.MAX_VALUE;
        final int[][] standard = {{0, WireTension.STANDARD.id()}};
        final WireTension[] ways = FePonderChapters.Spans.REHUNG;
        final int[][] rehung = new int[ways.length + 1][];
        rehung[0] = standard[0];
        for (int s = 0; s < ways.length; s++) {
            rehung[s + 1] = new int[] {FePonderChapters.Spans.REHANG + FePonderChapters.Spans.REHANG_STEP * s,
                    ways[s].id()};
        }

        ch.build(10, FeLayout.spansBuild());
        ch.control(FePonderChapters.Spans.STRING, 30, top(a[0], 0.8), "copper_wire");
        ch.control(FePonderChapters.Spans.CLICK_B, 30, top(b, 0.8), "copper_wire");
        ch.span(a[0], b, WireTier.COPPER, FePonderChapters.Spans.CLICK_B + 4, spanTicks, stripped, rehung);
        for (int k = 1; k < a.length; k++) {
            final int from = k == 1 ? FePonderChapters.Spans.SECOND : FePonderChapters.Spans.THIRD;
            ch.reveal(from, List.of(a[k]));
            ch.control(from + 6, 16, top(a[k], 0.8), "copper_wire");
            ch.control(from + 22, 16, top(b, 0.8), "copper_wire");
            ch.span(a[k], b, WireTier.COPPER, from + 26, spanTicks, stripped, standard);
        }
        final int grades = FePonderChapters.Spans.GRADES;
        ch.build(grades, FeLayout.rack());
        final WireTier[] wires = WireTier.values();
        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            ch.span(FeStage.RACK_EAST[i], FeStage.RACK_WEST[i], wires[i], grades + 15 + 10 * i, spanTicks, never, standard);
        }
        final int pair = FePonderChapters.Spans.PAIR;
        ch.build(pair, FeLayout.pair());
        ch.span(a[0], FeStage.SPANS_D, WireTier.COPPER, pair + 12, spanTicks, never, standard);
        final int join = FePonderChapters.Spans.JOIN;
        ch.control(join, 10, top(FeStage.SPANS_D, 0.8), "copper_wire");
        ch.control(join + 10, 16, top(FeStage.SPANS_C, 0.8), "copper_wire");
        ch.span(FeStage.SPANS_D, FeStage.SPANS_C, WireTier.COPPER, join + 14, FePonderChapters.Spans.JOIN_TICKS, never,
                standard);
        ch.control(FePonderChapters.Spans.TENSION, 80, top(a[0], 0.8), null);
        ch.control(FePonderChapters.Spans.STRIP, 20, top(b, 0.8), "copper_wire");
        ch.event("drop", stripped, top(b, 0.6));
        ch.states = t -> {
            final JsonObject o = new JsonObject();
            final JsonArray lit = new JsonArray();
            if (t >= power && r.working[range][r.at(t)]) lit.add(pos(FeStage.SPANS_RANGE));
            if (t >= power && r.working[lamp][r.at(t)]) lit.add(pos(FeStage.SPANS_LAMP));
            o.add("lit", lit);
            o.addProperty("spans", FePonderChapters.Spans.spansAt(t));
            o.addProperty("range", Math.max(0, r.flow[range][r.at(t)]));
            o.addProperty("lamp", Math.max(0, r.flow[lamp][r.at(t)]));
            return o;
        };

        final List<Integer> index = new ArrayList<>();
        final List<FeFlow.Stream> streams = FeLayout.spanStreams((from, to, tension) -> {
            final FePaths.Path standIn = new FePaths.Path(FePaths.catenary(centre(from), centre(to), tension.slack()));
            index.add(ch.spanStream("span" + index.size(), from, to, tension, standIn));
            return standIn;
        }, r);
        ch.add(beads(ch, index.stream().mapToInt(Integer::intValue).toArray(), streams, power, end - power));

        final int goggles = FePonderChapters.Spans.GOGGLES;
        final int base = FeRates.base();
        final IntFunction<List<Component>> card = t -> {
            final List<WireTier> tiers = new ArrayList<>();
            for (int i = 0; i < FePonderChapters.Spans.spansAt(t); i++) tiers.add(WireTier.COPPER);
            return FeGoggles.connector(ConnectorTier.POWER, base, Math.max(0, r.flow[range][r.at(t)]), tiers);
        };
        ch.add(goggles(FeLayout.spansGoggles(), card, goggles, FePonderChapters.Spans.CARD_OFF - goggles));

        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            final int from = grades + 15 + 10 * i + spanTicks;
            final int ticks = pair - from;
            final String wireWords = FeLayout.rackWireWords(i);
            ch.add(tagLeft(FeLayout.rackWireTag(i), t -> wireWords, 0xE4E9EF, 2, -5, from, ticks));
            final String words = FeLayout.rackConnectorWords(i);
            if (words != null) ch.add(tag(FeLayout.rackConnectorTag(i), t -> words, 0xB8C4D0, 6, -5, from, ticks));
        }
        final int tension = FePonderChapters.Spans.TENSION;
        ch.add(goggles(FeLayout.spansGoggles(), card, tension, FePonderChapters.Spans.CARD_GONE - tension));
        ch.json.add("targets", targets(FeLayout.wires()));
        return ch;
    }

    private static Part banksBoard(FeDash.Banks m, int start, int ticks) {
        return new Part(start, ticks) {
            @Override
            void step(int t, boolean last) {
                m.step(t);
            }

            @Override
            void write(Frame f, int t) {
                final JsonObject o = new JsonObject();
                final JsonArray rows = new JsonArray();
                for (int k = 0; k < m.rows.size(); k++) {
                    final JsonObject row = new JsonObject();
                    row.addProperty("fill", round(m.fill[k]));
                    row.addProperty("grow", round(Ease.OUT_CUBIC.clamped(m.grow[k])));
                    row.addProperty("words", m.words[k] == null ? "" : m.words[k]);
                    final JsonArray marks = new JsonArray();
                    for (int pct : m.rows.get(k).marks()) marks.add(pct);
                    row.add("marks", marks);
                    rows.add(row);
                }
                o.add("rows", rows);
                if (m.clock.level > 0.05f && !m.clock.shown.isEmpty()) {
                    o.addProperty("clock", m.clock.shown);
                    o.addProperty("clockAlpha", round(Ease.SMOOTH.clamped(m.clock.level)));
                }
                f.json.add("dash", o);
            }
        };
    }

    private static Part travel(FePaths.Path path, String words, int rgb, boolean leftOf, int start, int ticks) {
        return new Part(start, ticks) {
            final FeLabels.Fader fader = new FeLabels.Fader(0);
            double now;

            @Override
            void begin() {
                now = 0.0;
                fader.rgb = rgb;
            }

            @Override
            void step(int t, boolean last) {
                now = Ease.SMOOTH.clamped((t - start + 1) / (double) Math.max(1, end - start - 1));
                fader.steer(!last && t - start < end - start - FeLabels.FADE, words);
            }

            @Override
            void write(Frame f, int t) {
                f.label("tag", path.at(now * path.length()), 4, -12, leftOf, fader.shown, fader.rgb,
                        Ease.SMOOTH.clamped(fader.level));
            }
        };
    }

    private static Chapter board(Tuning tune) {
        final FePonderChapters.Deal.Run run = FePonderChapters.Deal.replay(tune,
                FePonderChapters.Controller.Stock.defaults(), FeStage.YC.asLong(), FeStage.BOARDS[0].asLong(),
                FeStage.BOARDS[1].asLong());
        final com.createstats.power.ponder.FePonderDeal.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Deal.sheet(run);
        final Chapter ch = new Chapter("fe_board_and_reserve", "yard", FeStage.YARD_PLATE, FeStage.YARD_SCALE, sheet);
        final int end = sheet.length();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));

        final int from = ch.build(10, FeLayout.yardBuild());
        ch.json.addProperty("wiresFromSchematic", true);

        final BlockPos[] ends = {null, FeStage.YARD_BANK_ENDS[0], FeStage.YARD_BANK_ENDS[1], FeStage.BOARD_FEEDS[0],
                FeStage.BOARD_FEEDS[1]};
        for (int p = 1; p <= 4; p++) ch.portSpan(FeStage.terminal(FeStage.YC, p), ends[p], WireTier.COPPER, 0, 1);
        final FeLayout.PortHang hang = (a, to) -> new FePaths.Path(FePaths.catenary(a, centre(to),
                WireTension.STANDARD.slack()));
        final List<Integer> index = new ArrayList<>();
        final FeLayout.PortHang registered = (a, to) -> {
            final FePaths.Path standIn = hang.between(a, to);
            index.add(ch.portStream("feed" + index.size(), a, to, standIn, false));
            return standIn;
        };
        final FeLayout.LegHang legs = (board, stud, far) -> {
            final Vec3 a = FePaths.studPoint(board, Direction.NORTH, stud);
            final FePaths.Path standIn = new FePaths.Path(FePaths.catenary(a, centre(far), WireTension.STANDARD.slack()));
            index.add(ch.portStream("leg" + index.size(), a, far, standIn, false));
            return standIn;
        };
        final List<FeFlow.Stream> streams = FeLayout.yardStreams(registered, legs, run);

        ch.reverse(index.get(0));
        ch.reverse(index.get(1));
        ch.add(beads(ch, index.stream().mapToInt(Integer::intValue).toArray(), streams, from, end - from));
        ch.add(banksBoard(new FeDash.Banks(warp, FeLayout.yardRows(run)), from, end - from));
        final int ask = FePonderChapters.Deal.ASK;
        final int answer = FePonderChapters.Deal.ANSWER;
        for (int b = 0; b < 2; b++) {
            final int t0 = at.applyAsInt(ask);
            final String words = String.format(Locale.ROOT, "Critical %,d, running %,d", r.askCritical[b][t0],
                    r.askRunning[b][t0]) + (r.askRestore[b][t0] > 0
                    ? String.format(Locale.ROOT, ", restore %,d", r.askRestore[b][t0]) : "");
            ch.add(travel(FeLayout.reversed(FeLayout.boardFeed(hang, b)), words, 0x9ECFE8, b == 1, ask, 70));
        }
        for (int b = 0; b < 2; b++) {
            ch.add(travel(FeLayout.boardFeed(hang, b), r.reason[b][at.applyAsInt(answer)].sentence(), 0x8CBA51, b == 1,
                    answer, 70));
        }
        final int split = sheet.tick("split");
        ch.add(travel(FeLayout.boardFeed(hang, 1), r.reason[1][at.applyAsInt(split + 2)].sentence(), 0xD9A441, true,
                split, 90));
        final int floor = sheet.tick("floor");
        for (int b = 0; b < 2; b++) {
            ch.add(travel(FeLayout.boardFeed(hang, b), r.reason[b][at.applyAsInt(floor + 2)].sentence(), 0xD9A441, b == 1,
                    floor, 90));
        }
        ch.states = t -> {
            final JsonObject o = new JsonObject();
            final int a = at.applyAsInt(t);
            final JsonArray lit = new JsonArray();
            final JsonArray heats = new JsonArray();
            if (t >= from) {
                if (r.working[0][FePonderChapters.Deal.LAMP_1][a]) {
                    lit.add(pos(FeStage.BOARD_MACHINES[0][FePonderChapters.Deal.LAMP_1]));
                }
                if (r.working[1][FePonderChapters.Deal.LAMP_2][a]) {
                    lit.add(pos(FeStage.BOARD_MACHINES[1][FePonderChapters.Deal.LAMP_2]));
                }
                heats.add(heat(FeStage.BOARD_MACHINES[0][FePonderChapters.Deal.RANGE_1],
                        r.working[0][FePonderChapters.Deal.RANGE_1][a] ? 3 : 0));
                heats.add(heat(FeStage.BOARD_MACHINES[1][FePonderChapters.Deal.RANGE_2],
                        r.working[1][FePonderChapters.Deal.RANGE_2][a] ? 3 : 0));
            }
            o.add("lit", lit);
            o.add("heats", heats);
            final JsonArray banks = new JsonArray();
            for (int k = 0; k < 2; k++) {
                banks.add(bankState(FeStage.YARD_BANKS[k], FeStage.YARD_BANKS[k],
                        r.bank[k][a] / (double) r.setup.banks()[k]));
            }
            o.add("banks", banks);
            o.addProperty("replay", a);
            o.addProperty("factor", round(warp.factor(t)));
            o.addProperty("soc", r.socPct[a]);
            o.addProperty("stance", r.stance[a].label());
            o.addProperty("b1", r.granted(0, a) + "/" + r.asked(0, a) + " " + r.reason[0][a].name());
            o.addProperty("b2", r.granted(1, a) + "/" + r.asked(1, a) + " " + r.reason[1][a].name());
            return o;
        };
        ch.control(sheet.tick("feed"), 30, top(FeStage.BOARD_FEEDS[0], 0.7), "copper_wire");
        ch.json.add("targets", targets(FeLayout.yard()));
        return ch;
    }

    private static JsonObject heat(BlockPos p, int level) {
        final JsonObject h = new JsonObject();
        h.add("pos", pos(p));
        h.addProperty("level", level);
        return h;
    }

    private static Chapter controller(Tuning tune) {
        final FePonderChapters.Controller.Run run = FePonderChapters.Controller.replay(tune,
                FePonderChapters.Controller.Stock.defaults());
        final com.createstats.power.ponder.FePonderController.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Controller.sheet(run);
        final Chapter ch = new Chapter("fe_battery_controller", "controller", FeStage.CONTROLLER_PLATE,
                FeStage.CONTROLLER_SCALE, sheet);
        final int end = sheet.length();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final int terminals = FePonderChapters.Controller.TERMINALS;
        final int wires = FePonderChapters.Controller.WIRES;
        final int power = FePonderChapters.Controller.POWER;

        ch.build(10, FeLayout.controllerBuild());
        ch.add(tag(FeLayout.inputsTag(), t -> "inputs A B C", 0x9ECFE8, 8, -5, terminals, power - terminals));
        ch.add(tagLeft(FeLayout.outputsTag(), t -> "outputs 1 2 3", 0xE0A060, 8, -5, terminals, power - terminals));
        ch.control(wires, 6 * FePonderChapters.Controller.WIRE_GAP, top(FeStage.BC, 1.4), "copper_wire");
        for (int p = 0; p < 6; p++) {
            ch.portSpan(FeStage.terminal(p), FeStage.BC_ENDS[p], WireTier.COPPER,
                    wires + FePonderChapters.Controller.WIRE_GAP * p, FePonderChapters.Controller.WIRE_TICKS);
        }
        final List<FeFlow.Stream> streams = FeLayout.controllerStreams((from, to) -> new FePaths.Path(
                FePaths.catenary(from, centre(to), WireTension.STANDARD.slack())), run);

        final int[] ports = {0, 1, 1, 2, 2, 3, 4, 5};
        final boolean[] inward = {true, false, true, false, true, false, false, false};
        final int[] index = new int[streams.size()];
        for (int i = 0; i < index.length; i++) {
            index[i] = ch.portStream("port" + i, FeStage.terminal(ports[i]), FeStage.BC_ENDS[ports[i]],
                    streams.get(i).path(), inward[i]);
        }
        ch.add(beads(ch, index, streams, wires, end - wires));
        ch.add(banksBoard(new FeDash.Banks(warp, FeLayout.controllerRows(run)), wires, end - wires));
        ch.control(power - 12, 12, top(FeStage.BC_MOTOR, 0.6), null, "scroll");
        final int floor = FePonderChapters.Controller.FLOOR;
        final int stop = FePonderChapters.Controller.STOP;
        ch.add(tag(FeLayout.floorTag(), t -> "floor " + FePonderChapters.Controller.FLOOR_PCT + "%", 0xFF6A3C, 8, -5,
                floor, stop - floor));
        ch.control(stop - 12, 12, top(FeStage.BC_MOTOR, 0.6), null, "scroll");
        final int critical = sheet.tick("critical_caption");
        final int refill = FePonderChapters.Controller.REFILL;
        ch.add(tag(FeLayout.criticalTag(), t -> "Critical", 0xFF4A4A, 8, -5, critical, refill - critical));
        ch.control(refill - 12, 12, top(FeStage.BC_MOTOR, 0.6), null, "scroll");
        ch.control(sheet.tick("screen"), 30, FePaths.faceCentre(FeStage.BC, Direction.NORTH), null);
        ch.states = t -> {
            final JsonObject o = new JsonObject();
            final int a = at.applyAsInt(t);
            final JsonArray lit = new JsonArray();
            if (t >= wires && r.level[FePonderChapters.Controller.CRITICAL_LAMP][a] > 0) lit.add(pos(FeStage.BC_DEVICES[4]));
            if (t >= wires && r.level[FePonderChapters.Controller.FLOORED_LAMP][a] > 0) lit.add(pos(FeStage.BC_DEVICES[5]));
            o.add("lit", lit);
            final JsonObject heat = new JsonObject();
            heat.add("pos", pos(FeStage.BC_DEVICES[3]));
            heat.addProperty("level", t >= wires ? r.level[FePonderChapters.Controller.RANGE][a] : 0);
            o.add("heat", heat);
            final JsonArray banks = new JsonArray();
            for (int b = 0; b < 2; b++) {
                banks.add(bankState(FeStage.BC_DEVICES[1 + b], FeStage.BC_DEVICES[1 + b],
                        r.bank[b][a] / (double) r.setup.banks()[b]));
            }
            o.add("banks", banks);
            o.addProperty("replay", a);
            o.addProperty("factor", round(warp.factor(t)));
            o.addProperty("soc", r.socPct[a]);
            o.addProperty("stance", r.stance[a].label());
            o.addProperty("out1", r.flow[FePonderChapters.Controller.RANGE][a]);
            o.addProperty("why1", r.why[FePonderChapters.Controller.RANGE][a].label());
            return o;
        };
        ch.json.add("targets", targets(FeLayout.controller()));
        return ch;
    }

    private static Chapter bank(Tuning tune) {
        final FePonderChapters.Bank.Stock stock = FePonderChapters.Bank.Stock.defaults();
        final FePonderChapters.Bank.Run run = FePonderChapters.Bank.replay(tune, stock);
        final FePonderGrid.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Bank.sheet(run);
        final Chapter ch = new Chapter("fe_capacitor_bank", "bank", FeStage.BANK_PLATE, FeStage.BANK_SCALE, sheet);
        final int end = sheet.length();
        final int place = FePonderChapters.Bank.PLACE;
        final int power = FePonderChapters.Bank.POWER;
        final int stop = FePonderChapters.Bank.STOP;
        final int never = FePonderChapters.Bank.NEVER;
        final int broken = FePonderChapters.Bank.BREAK;
        final int range = FePonderChapters.Bank.RANGE;
        final int bank = FePonderChapters.Bank.BANK;
        final int spare = FePonderChapters.Bank.SPARE;

        ch.build(10, FeLayout.bankBuild());
        ch.add(banksBoard(new FeDash.Banks(warp, FeLayout.bankRows(run, stock)), place, end - place));
        final int[] built = FePonderChapters.Bank.BUILT;
        for (int k = 0; k < built.length; k++) {
            final int at = place + FePonderChapters.Bank.PLACE_GAP * k;
            final int from = k == 0 ? 0 : built[k - 1];
            ch.control(at, 8, top(FeStage.BANK_BLOCKS[from], 1.0), "capacitor_bank");
            ch.place(at, "capacitor_bank", java.util.Arrays.copyOfRange(FeStage.BANK_BLOCKS, from, built[k]));
        }
        ch.control(power - 12, 12, top(FeStage.BANK_MOTOR, 0.6), null, "scroll");
        final List<FeFlow.Stream> streams = FeLayout.bankStreams(run);
        final int[] index = new int[streams.size()];
        for (int i = 0; i < index.length; i++) index[i] = ch.path("bank" + i, streams.get(i).path());
        ch.add(beads(ch, index, streams, power, end - power));
        ch.control(stop - 12, 12, top(FeStage.BANK_MOTOR, 0.6), null, "scroll");
        ch.control(never - 12, 12, top(FeStage.SPARE_LINK_BASE, 1.1), "surface_cable");
        ch.place(never, "surface_cable", FeStage.SPARE_LINK);
        ch.control(broken - 12, 12, top(FeStage.SPARE, 1.0), null, "left");
        ch.remove(broken, FeStage.SPARE);
        ch.event("drop", broken, top(FeStage.SPARE, 0.5));
        ch.control(sheet.tick("merge"), 30, FeLayout.bankTop(), "capacitor_bank");

        ch.json.add("args", args(Map.of("merge_caption", List.of("3", "3", "6"))));
        ch.states = t -> {
            final JsonObject o = new JsonObject();
            final int at = r.at(warp.tick(t));
            final JsonArray lit = new JsonArray();
            if (t >= power && r.working[range][at]) lit.add(pos(FeStage.BANK_RANGE));
            o.add("lit", lit);
            final JsonArray banks = new JsonArray();
            final int n = FePonderChapters.Bank.builtAt(t);
            if (n > 0) {
                BlockPos lo = FeStage.BANK_BLOCKS[0];
                BlockPos hi = FeStage.BANK_BLOCKS[0];
                for (int i = 1; i < n; i++) {
                    lo = BlockPos.min(lo, FeStage.BANK_BLOCKS[i]);
                    hi = BlockPos.max(hi, FeStage.BANK_BLOCKS[i]);
                }
                banks.add(bankState(lo, hi, r.fill(bank, at)));
            }
            if (t < broken) banks.add(bankState(FeStage.SPARE, FeStage.SPARE, r.fill(spare, at)));
            o.add("banks", banks);
            o.addProperty("replay", at);
            o.addProperty("factor", round(warp.factor(t)));
            o.addProperty("bankFill", round(r.fill(bank, at)));
            o.addProperty("spareFill", round(r.fill(spare, at)));
            o.addProperty("rangeOn", r.working[range][at]);
            return o;
        };
        ch.json.add("targets", targets(FeLayout.bank()));
        return ch;
    }

    private static JsonObject bankState(BlockPos lo, BlockPos hi, double fill) {
        final JsonObject b = new JsonObject();
        b.add("min", pos(lo));
        b.add("max", pos(hi));
        b.addProperty("fill", round(fill));
        return b;
    }

    private static Vec3 top(BlockPos p, double up) {
        return new Vec3(p.getX() + 0.5, p.getY() + up, p.getZ() + 0.5);
    }

    private static Vec3[] chipPoints() {
        final Vec3[] out = new Vec3[FeStage.MACHINE.length];
        for (int i = 0; i < out.length; i++) out[i] = FeStage.chipPoint(i);
        return out;
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private static Vec3 leverTop() {
        return new Vec3(FeStage.LEVER.getX() + 0.5, FeStage.LEVER.getY() + 0.4, FeStage.LEVER.getZ() + 0.5);
    }

    private static JsonObject workshopStates(Recording r, Warp warp, int t, int appliances, int leverOn, int leverOff,
                                             double[] grown) {
        final JsonObject o = new JsonObject();
        o.addProperty("lever", t >= leverOn && t < leverOff);
        final int at = r.at(warp.tick(t));
        final JsonArray working = new JsonArray();
        final JsonArray shed = new JsonArray();
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            working.add(t >= appliances && r.working[i][at]);
            shed.add(t >= appliances && r.connected[i][at] && !r.admitted[i][at]);
        }
        o.add("working", working);
        o.add("shed", shed);
        final JsonArray wires = new JsonArray();
        for (double g : grown) wires.add(round(g));
        o.add("wires", wires);
        o.addProperty("replay", at);
        o.addProperty("factor", round(warp.factor(t)));
        o.addProperty("fill", round(r.fill(at)));
        o.addProperty("budget", r.runBudget[at] == Integer.MAX_VALUE ? -1 : r.runBudget[at]);
        o.addProperty("intake", r.intake[at]);
        final JsonArray reasons = new JsonArray();
        for (int i = 0; i < FeStage.MACHINE.length; i++) reasons.add(r.reason[i][at] == null ? "" : r.reason[i][at].name());
        o.add("reasons", reasons);
        return o;
    }

    private static List<FeFlow.Stream> trunk(Recording r, Warp warp) {
        final List<FeFlow.Stream> out = new ArrayList<>();
        for (int d = 0; d < FeStage.DYNAMOS.length; d++) {
            final int dyn = d;
            out.add(new FeFlow.Stream(FeLayout.trunk(d), t -> FeLayout.trunkRate(r, dyn, r.at(warp.tick(t)))));
        }
        return out;
    }

    private static int[] trunkIndex(Chapter ch) {
        final int[] out = new int[FeStage.DYNAMOS.length];
        for (int d = 0; d < out.length; d++) out[d] = ch.path("trunk" + d, FeLayout.trunk(d));
        return out;
    }

    private static List<FeFlow.Stream> legs(Recording r, Warp warp) {
        final List<FeFlow.Stream> out = new ArrayList<>();
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            out.add(FeFlow.Stream.wire(legStandIn(i), t -> r.fed[m][r.at(warp.tick(t))], WireTier.COPPER, t -> true));
        }
        return out;
    }

    private static FePaths.Path legStandIn(int i) {
        return new FePaths.Path(FePaths.catenary(FePaths.studPoint(FeStage.PDU, Direction.NORTH, FeStage.STUD[i]),
                centre(FeStage.LEG_END[i]), com.createstats.power.WireTension.STANDARD.slack()));
    }

    private static int[] legIndex(Chapter ch) {
        final int[] out = new int[FeStage.MACHINE.length];
        for (int i = 0; i < out.length; i++) out[i] = ch.leg(i);
        return out;
    }

    private static void chips(Chapter ch, Recording r, Warp warp, int from, int to, IntPredicate tiered) {
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            ch.add(chip(FeStage.chipPoint(i), t -> FePriority.byOrdinal(tiered.test(t)
                    ? r.tier[m][r.at(warp.tick(t))] : FePriority.DEFAULT.ordinal()).getShortName(), from + 3 * i,
                    to - from - 3 * i));
        }
    }

    private static void reasons(Chapter ch, Recording r, Warp warp, int from, int to) {
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            final int[] hang = FeLayout.REASON[i];
            ch.add(flag("reason", FeStage.chipPoint(i).add(0.0, -0.05, 0.0),
                    t -> FeLabels.reason(r, m, r.at(warp.tick(t))), t -> PduTheme.WARN & 0xFFFFFF, hang[0], hang[1],
                    hang[2] == 1, FeLabels.HOLD, from, to - from));
        }
    }

    private static JsonObject targets(Map<String, Vec3> m) {
        final JsonObject o = new JsonObject();
        for (String k : new TreeSet<>(m.keySet())) o.add(k, vec(m.get(k)));
        return o;
    }

    private static JsonObject args(Map<String, List<String>> m) {
        final JsonObject o = new JsonObject();
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            final JsonArray a = new JsonArray();
            for (String s : e.getValue()) a.add(s);
            o.add(e.getKey(), a);
        }
        return o;
    }

    private static List<int[]> frames(Sheet sheet) {
        final List<int[]> out = new ArrayList<>();
        final List<Cue> cues = sheet.cues();
        for (int k = 0; k < cues.size(); k++) {
            final Cue c = cues.get(k);
            int at;
            if (c.kind() == FePonderChapters.CueKind.CAPTION) {
                at = c.tick() + 16;
            } else {
                boolean captioned = false;
                for (Cue d : cues) {
                    if (d.kind() == FePonderChapters.CueKind.CAPTION && c.name().equals(d.about())) captioned = true;
                }
                if (captioned) continue;
                at = c.name().equals("build") ? 50 : c.name().equals("rest") ? sheet.length() - 10 : c.tick() + 12;
            }
            add(out, Math.min(sheet.length() - 1, at), k, 0, 8);
        }
        for (int k = 0; k < cues.size(); k++) {
            final Cue c = cues.get(k);
            if (c.kind() != FePonderChapters.CueKind.CAPTION) continue;
            add(out, c.tick() + c.hold() / 2, k, 1, 4);
            add(out, c.tick() + c.hold() - 6, k, 1, 4);
        }
        out.sort((a, b) -> Integer.compare(a[0], b[0]));
        return out;
    }

    private static void add(List<int[]> out, int at, int cue, int lint, int apart) {
        for (int[] f : out) if (Math.abs(f[0] - at) < apart) return;
        out.add(new int[] {at, cue, lint});
    }

    private static void write(Chapter ch, Map<String, String[]> looks) throws IOException {
        final JsonObject root = ch.json;
        root.addProperty("chapter", ch.id);
        root.addProperty("stage", ch.stage);
        root.addProperty("plate", ch.plate);
        root.addProperty("scale", ch.scale);
        root.addProperty("offsetY", FeStage.OFFSET_Y);
        root.addProperty("length", ch.sheet.length());
        final JsonArray cues = new JsonArray();
        final Map<String, List<Vec3>> clear = FeLayout.keepClear(ch.id);
        int index = 0;
        for (Cue c : ch.sheet.cues()) {
            final JsonObject o = new JsonObject();
            o.addProperty("kind", c.kind().name());
            o.addProperty("name", c.name());
            o.addProperty("tick", c.tick());
            if (c.kind() == FePonderChapters.CueKind.CAPTION) {
                o.addProperty("hold", c.hold());
                o.addProperty("keyframe", c.keyframe());
                o.addProperty("about", c.about());
                o.addProperty("text", ++index);
                final String[] look = looks.get(ch.id + "/" + c.name());
                assertTrue(look != null, ch.id + " " + c.name() + ": no colour found in FeScenes");
                o.addProperty("palette", look[0]);
                o.addProperty("place", look[1]);
                if ("independent".equals(look[1])) o.addProperty("independent", FeLayout.UPPER);
                final JsonArray keep = new JsonArray();
                for (Vec3 v : clear.getOrDefault(c.name(), List.of())) keep.add(vec(v));
                o.add("clear", keep);
            }
            cues.add(o);
        }
        root.add("cues", cues);
        root.add("reveals", ch.reveals);
        root.add("controls", ch.controls);
        root.add("events", ch.events);
        root.add("streams", ch.streams);
        root.add("spans", ch.spans);
        root.add("placed", ch.placed);
        root.add("removed", ch.removed);

        final List<int[]> frames = frames(ch.sheet);
        final JsonArray out = new JsonArray();
        int next = 0;
        for (int t = 0; t < ch.sheet.length() && next < frames.size(); t++) {
            for (Part p : ch.parts) {
                if (t < p.start || t >= p.end) continue;
                if (t == p.start) p.begin();
                p.step(t, t == p.end - 1);
            }
            while (next < frames.size() && frames.get(next)[0] == t) {
                final Frame f = new Frame();
                for (Part p : ch.parts) if (t >= p.start && t < p.end) p.write(f, t);
                f.json.addProperty("tick", t);
                f.json.addProperty("beat", ch.sheet.cues().get(frames.get(next)[1]).name());
                if (frames.get(next)[2] == 1) f.json.addProperty("lint", true);
                f.json.add("states", ch.states.apply(t));
                out.add(f.json);
                next++;
            }
        }
        root.add("frames", out);
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve(ch.id + ".json"), new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
    }

    private static Map<String, String[]> looks() throws IOException {
        final String src = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final Map<String, String[]> out = new java.util.HashMap<>();
        final Matcher stage = Pattern.compile("\\bstage\\(builder,\\s*\"([a-z0-9_]+)\"").matcher(src);
        final List<int[]> spans = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        while (stage.find()) {
            if (!spans.isEmpty()) spans.get(spans.size() - 1)[1] = stage.start();
            spans.add(new int[] {stage.end(), src.length()});
            ids.add(stage.group(1));
        }
        for (int i = 0; i < spans.size(); i++) {
            final Matcher m = SHOW.matcher(src.substring(spans.get(i)[0], spans.get(i)[1]));
            while (m.find()) {
                final Matcher p = PALETTE.matcher(m.group(2));
                if (p.find()) {
                    final String place = m.group(2).contains(".independent(FeLayout.UPPER)") ? "independent"
                            : m.group(2).contains(".placeNearTarget()") ? "near" : "far";
                    out.put(ids.get(i) + "/" + m.group(1), new String[] {p.group(1), place});
                }
            }
        }
        return out;
    }

    @Test
    void writesEveryChaptersContactSheet() throws IOException {
        final Tuning tune = Tuning.defaults();
        final Map<String, String[]> looks = looks();
        for (Chapter ch : List.of(cables(tune), pdu(tune), auto(tune), wires(tune), bank(tune), controller(tune),
                board(tune))) {
            write(ch, looks);
        }
        for (String id : List.of("fe_cables", "fe_pdu", "fe_pdu_auto", "fe_wires", "fe_capacitor_bank",
                "fe_battery_controller", "fe_board_and_reserve")) {
            assertTrue(Files.size(OUT.resolve(id + ".json")) > 1_000, id + " sheet data is empty");
        }
    }
}
