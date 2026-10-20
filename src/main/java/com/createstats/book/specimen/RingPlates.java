package com.createstats.book.specimen;

import com.createstats.book.layout.Callouts;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockView;
import com.createstats.book.scene.FigureWorld;
import com.createstats.book.scene.WireFigures;
import com.createstats.client.wire.WireCanvas;
import com.createstats.client.wire.WireRing;
import com.createstats.client.wire.WireRingLayout;
import com.createstats.client.wire.WrenchPanel;
import com.createstats.power.WireTension;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class RingPlates {
    private RingPlates() {
    }

    public static final String RING = "wire_tension.ring";
    public static final String WRENCH = "wire_tension.wrench";

    public static final List<String> IDS = List.of(RING, WRENCH);

    public static boolean isPlate(String id) {
        return IDS.contains(id);
    }

    public static BlockFigure world(String id) {
        return WRENCH.equals(id) ? WireFigures.WRENCH_WORLD : WireFigures.RING_WORLD;
    }

    public static int width(String id) {
        return world(id).width();
    }

    public static int height(String id) {
        return world(id).height();
    }

    private static Function<String, String> text = RingPlates::enUs;
    private static final Map<String, SpecimenAnatomy> ANATOMY = new HashMap<>();
    private static Map<String, String> enUs;

    public static synchronized void useText(Function<String, String> lookup) {
        if (lookup != null && lookup != text) {
            text = lookup;
            ANATOMY.clear();
        }
    }

    public static synchronized void useDefaultText() {
        text = RingPlates::enUs;
        ANATOMY.clear();
    }

    private static synchronized String enUs(String key) {
        if (enUs == null) {
            enUs = new HashMap<>();
            try (InputStream in = RingPlates.class.getResourceAsStream("/assets/create_stats/lang/en_us.json")) {
                if (in != null) {
                    final JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (final String k : o.keySet()) {
                        enUs.put(k, o.get(k).getAsString());
                    }
                }
            } catch (java.io.IOException | RuntimeException unreadable) {
            }
        }
        return enUs.getOrDefault(key, key);
    }

    static String t(String key, Object... args) {
        final String format = text.apply(key);
        return args.length == 0 ? format : String.format(Locale.ROOT, format, args);
    }

    public static List<WireRing.Panel> panels() {
        final BlockFigure f = WireFigures.RING_WORLD;
        final FigureWorld w = FigureWorld.of(f);
        final BlockView v = f.view();
        final List<BlockFigure.Tie> spans = w.spans();
        final double[] raw = new double[spans.size()];
        for (int i = 0; i < spans.size(); i++) {
            final BlockFigure.Tie s = spans.get(i);
            final Vec3 a = w.terminalPoint(new BlockPos(s.from()[0], s.from()[1], s.from()[2]));
            final Vec3 b = w.terminalPoint(new BlockPos(s.to()[0], s.to()[1], s.to()[2]));
            final double[] d = v.turn(b.x - a.x, b.y - a.y, b.z - a.z);

            raw[i] = Math.atan2(-d[1], d[0]);
        }
        final double[] spread = WireRingLayout.spread(raw, WireRing.MIN_GAP);
        final List<WireRing.Panel> out = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            final BlockFigure.Tie s = spans.get(i);
            out.add(new WireRing.Panel(s.tier().strandA(), t(WireRing.levelKey(s.tension())),
                    t(WireRing.SPAN_KEY, WireRing.spanArgs(w.spanLength(s), s.tension())), s.tension().ordinal(),
                    WireTension.values().length, spread[i]));
        }
        return out;
    }

    public static String all() {
        return t(WireRing.ALL_KEY);
    }

    public static String hubSub() {
        WireTension shared = null;
        for (final BlockFigure.Tie s : FigureWorld.of(WireFigures.RING_WORLD).spans()) {
            if (shared == null) {
                shared = s.tension();
            } else if (shared != s.tension()) {
                return t(WireRing.MIXED_KEY);
            }
        }
        return shared == null ? t(WireRing.MIXED_KEY) : t(WireRing.levelKey(shared));
    }

    public static String hint() {
        return t(WireRing.HINT_KEY);
    }

    public static final int WRENCH_PICK = 1;

    public static WrenchPanel.View wrenchView() {
        final FigureWorld w = FigureWorld.of(WireFigures.WRENCH_WORLD);
        final List<BlockFigure.Tie> spans = w.spans();
        final List<int[]> tabs = new ArrayList<>();
        for (final BlockFigure.Tie s : spans) {
            tabs.add(s.tier().strandA());
        }
        final BlockFigure.Tie picked = spans.get(WRENCH_PICK);
        return new WrenchPanel.View(tabs, WRENCH_PICK, t(WrenchPanel.ALL_KEY), t(WireRing.levelKey(picked.tension())),
                picked.tier().strandA(), picked.tension().ordinal(), WireTension.values().length,
                t(WireRing.SPAN_KEY, WireRing.spanArgs(w.spanLength(picked), picked.tension())), t(WrenchPanel.HINT_KEY));
    }

    static final int WRENCH_FOOT = 8;

    public static int[] wrenchAt(WireCanvas m) {
        final int[] size = WrenchPanel.size(m, wrenchView());
        return new int[]{(width(WRENCH) - size[0]) / 2, height(WRENCH) - WRENCH_FOOT - size[1]};
    }

    public static int[] aim() {
        return WireFigures.WRENCH_AIM.clone();
    }

    public static final int CROSSHAIR = 15;

    public static int resting() {
        final SpecimenAnatomy a = anatomy(RING);
        final SpecimenAnatomy.Hot panel = a.control("panel");
        final List<WireRing.Panel> panels = panels();
        final WireRing.Frame frame = WireRing.Frame.of(width(RING), height(RING));
        for (int k = 0; k < panels.size(); k++) {
            final int[] b = WireRing.panelBox(MEASURE, frame, panels.get(k));
            if (panel != null && panel.anchorX() >= b[0] && panel.anchorX() < b[0] + b[2]
                    && panel.anchorY() >= b[1] && panel.anchorY() < b[1] + b[3]) {
                return k;
            }
        }
        return -1;
    }

    public static void drawRing(WireCanvas c, int mouseX, int mouseY) {
        final WireRing.Frame frame = WireRing.Frame.of(width(RING), height(RING));
        final List<WireRing.Panel> panels = panels();
        final boolean on = mouseX >= 0 && mouseY >= 0 && mouseX < width(RING) && mouseY < height(RING);
        final int lit = on ? WireRing.pick(frame, panels, mouseX - frame.cx(), mouseY - frame.cy()) : resting();
        WireRing.draw(c, frame, panels, all(), hubSub(), hint(), lit);
    }

    public static void drawWrench(WireCanvas c) {
        final int[] at = wrenchAt(c);
        WrenchPanel.draw(c, wrenchView(), at[0], at[1]);
    }

    static final WireCanvas MEASURE = new WireCanvas() {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
        }

        @Override
        public void text(String s, int x, int y, int argb) {
        }

        @Override
        public int width(String s) {
            return VanillaFont.width(s);
        }
    };

    public static synchronized SpecimenAnatomy anatomy(String id) {
        return ANATOMY.computeIfAbsent(id, k -> WRENCH.equals(k) ? wrenchAnatomy() : ringAnatomy());
    }

    public static List<int[]> print(String id) {
        return WRENCH.equals(id) ? wrenchPrint() : ringPrint();
    }

    private static SpecimenAnatomy.Hot part(String id, int[] rect, int ax, int ay) {
        return new SpecimenAnatomy.Hot(id, rect[0], rect[1], Math.max(1, rect[2]), Math.max(1, rect[3]), ax, ay, false);
    }

    private static SpecimenAnatomy.Hot at(String id, int[] rect, int ax, int ay) {
        return new SpecimenAnatomy.Hot(id, rect[0], rect[1], Math.max(1, rect[2]), Math.max(1, rect[3]), ax, ay, true);
    }

    private static int[] around(int x, int y, int r) {
        return new int[]{x - r, y - r, 2 * r + 1, 2 * r + 1};
    }

    static List<int[]> ringPrint() {
        final WireRing.Frame f = WireRing.Frame.of(width(RING), height(RING));
        final List<int[]> out = new ArrayList<>();
        for (final WireRing.Panel p : panels()) {
            final int[] b = WireRing.panelBox(MEASURE, f, p);
            out.add(b);
            out.add(WireRing.swatchBox(b));
            out.add(WireRing.levelBox(MEASURE, b, p.level()));
            out.add(WireRing.spanBox(MEASURE, b, p.span()));
            out.add(WireRing.pipsBox(b, p.pips()));
        }
        final int[] hub = WireRing.hubBox(MEASURE, f, all(), hubSub());
        out.add(hub);
        out.add(WireRing.hintBox(MEASURE, f, hint()));
        return out;
    }

    private static final int MARGIN = 0;

    private static SpecimenAnatomy ringAnatomy() {
        final int w = width(RING);
        final int h = height(RING);
        final WireRing.Frame f = WireRing.Frame.of(w, h);
        final List<WireRing.Panel> panels = panels();
        final List<int[]> print = ringPrint();

        final List<Integer> order = new ArrayList<>();
        for (int k = 0; k < panels.size(); k++) {
            order.add(k);
        }
        final List<int[]> boxes = new ArrayList<>();
        for (final WireRing.Panel p : panels) {
            boxes.add(WireRing.panelBox(MEASURE, f, p));
        }
        order.sort((a, b) -> Integer.compare(Math.abs(boxes.get(b)[0] + boxes.get(b)[2] / 2 - f.cx()),
                Math.abs(boxes.get(a)[0] + boxes.get(a)[2] / 2 - f.cx())));

        final List<Callouts.Choice> choices = new ArrayList<>();
        final int[] hub = WireRing.hubBox(MEASURE, f, all(), hubSub());

        final int between = hub[1] + 14;
        final int right = hub[0] + hub[2] - 1;
        final int foot = hub[1] + hub[3] - 1;
        choices.add(new Callouts.Choice("hub", List.of(at("hub", hub, right, between), at("hub", hub, hub[0], between),
                at("hub", hub, right, hub[1]), at("hub", hub, hub[0], hub[1]), at("hub", hub, right, foot),
                at("hub", hub, hub[0], foot))));
        final List<SpecimenAnatomy.Hot> panel = new ArrayList<>();
        final List<SpecimenAnatomy.Hot> swatch = new ArrayList<>();
        final List<SpecimenAnatomy.Hot> level = new ArrayList<>();
        final List<SpecimenAnatomy.Hot> span = new ArrayList<>();
        final List<SpecimenAnatomy.Hot> pips = new ArrayList<>();
        final List<SpecimenAnatomy.Hot> spoke = new ArrayList<>();
        for (final int k : order) {
            final WireRing.Panel p = panels.get(k);
            final int[] b = boxes.get(k);
            final boolean left = b[0] + b[2] / 2 < w / 2;

            for (final int[] corner : new int[][]{{b[0], b[1]}, {b[0] + b[2] - 1, b[1]}, {b[0], b[1] + b[3] - 1},
                    {b[0] + b[2] - 1, b[1] + b[3] - 1}}) {
                if ((corner[0] < w / 2) == left) {
                    panel.add(at("panel", b, corner[0], corner[1]));
                }
            }
            final int[] s = WireRing.swatchBox(b);
            final int[] l = WireRing.levelBox(MEASURE, b, p.level());
            final int[] sp = WireRing.spanBox(MEASURE, b, p.span());
            final int[] pp = WireRing.pipsBox(b, p.pips());
            if (left) {
                swatch.add(at("swatch", s, b[0] + MARGIN, s[1] + 3));
                span.add(at("span", sp, b[0] + MARGIN, sp[1] + 3));
                pips.add(at("pips", pp, b[0] + MARGIN, pp[1] + 1));
            } else {
                span.add(at("span", sp, sp[0] + sp[2] + 2, sp[1] + 3));
                pips.add(at("pips", pp, pp[0] + pp[2] + 2, pp[1] + 1));
            }
            level.add(at("level", l, l[0] + l[2] + 2, l[1] + 3));

            final int[][] dots = WireRing.dots(f, p.angle());
            for (int i = 0; i < dots.length; i++) {
                final int[] d = dots[i];
                if (!near(print, d[0], d[1], 3) && (i == dots.length / 2 || i == dots.length / 3
                        || i == 2 * dots.length / 3)) {
                    spoke.add(at("spoke", around(d[0], d[1], 2), d[0], d[1]));
                }
            }
        }
        if (swatch.isEmpty()) {
            final int[] s = WireRing.swatchBox(boxes.get(order.get(0)));
            swatch.add(part("swatch", s, s[0] + 3, s[1] + 3));
        }
        choices.add(new Callouts.Choice("panel", panel));
        choices.add(new Callouts.Choice("swatch", swatch));
        choices.add(new Callouts.Choice("level", level));
        choices.add(new Callouts.Choice("span", span));
        choices.add(new Callouts.Choice("pips", pips));
        choices.add(new Callouts.Choice("spoke", spoke));
        final int[] hint = WireRing.hintBox(MEASURE, f, hint());
        choices.add(new Callouts.Choice("hint", List.of(at("hint", hint, hint[0] + hint[2] + 2, hint[1] + 3),
                at("hint", hint, hint[0] - 3, hint[1] + 3))));
        choices.add(new Callouts.Choice("wire", wirePlaces(f)));
        return Callouts.cleanest(RING, w, h, SpecimenAnatomy.Kind.OVERLAY, choices, print);
    }

    static List<SpecimenAnatomy.Hot> wirePlaces(WireRing.Frame f) {
        final BlockFigure world = WireFigures.RING_WORLD;
        final FigureWorld w = FigureWorld.of(world);
        final BlockView v = world.view();
        final List<int[]> print = ringPrint();
        final List<SpecimenAnatomy.Hot> out = new ArrayList<>();
        for (final BlockFigure.Tie s : w.spans()) {
            final List<Vec3> points = w.spanPoints(s);

            final List<int[]> visible = new ArrayList<>();
            for (int i = 0; i + 1 < points.size(); i++) {
                for (int step = 0; step < 4; step++) {
                    final Vec3 a = points.get(i);
                    final Vec3 b = points.get(i + 1);
                    final double t = step / 4.0;
                    final double[] p = v.project(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
                    final int x = (int) Math.round(p[0]);
                    final int y = (int) Math.round(p[1]);
                    if (x < 6 || y < 6 || x >= f.width() - 6 || y >= f.height() - 6 || near(print, x, y, 4)) {
                        continue;
                    }
                    visible.add(new int[]{x, y});
                }
            }
            if (visible.isEmpty()) {
                continue;
            }
            for (final int frac : new int[]{4, 2}) {
                final int[] p = visible.get(Math.min(visible.size() - 1, visible.size() / frac));
                out.add(at("wire", around(p[0], p[1], 2), p[0], p[1]));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("no wire shows round the ring");
        }
        return out;
    }

    private static boolean near(List<int[]> rects, int x, int y, int margin) {
        for (final int[] r : rects) {
            if (x >= r[0] - margin && x < r[0] + r[2] + margin && y >= r[1] - margin && y < r[1] + r[3] + margin) {
                return true;
            }
        }
        return false;
    }

    static List<int[]> wrenchPrint() {
        final WrenchPanel.View v = wrenchView();
        final int[] at = wrenchAt(MEASURE);
        final int[] size = WrenchPanel.size(MEASURE, v);
        final List<int[]> out = new ArrayList<>();
        out.add(new int[]{at[0], at[1], size[0], size[1]});
        out.add(WrenchPanel.allTabBox(MEASURE, v, at[0], at[1]));
        for (int i = 0; i < v.tabs().size(); i++) {
            out.add(WrenchPanel.wireTabBox(MEASURE, v, at[0], at[1], i));
        }
        out.add(WrenchPanel.levelBox(MEASURE, v, at[0], at[1]));
        if (v.swatch() != null) {
            out.add(WrenchPanel.swatchBox(v, at[0], at[1]));
        }
        out.add(WrenchPanel.pipsBox(v, at[0], at[1], size[0]));
        out.add(WrenchPanel.detailBox(MEASURE, v, at[0], at[1]));
        out.add(WrenchPanel.hintBox(MEASURE, v, at[0], at[1]));
        final int[] aim = aim();
        out.add(around(aim[0], aim[1], CROSSHAIR / 2));
        return out;
    }

    private static SpecimenAnatomy wrenchAnatomy() {
        final WrenchPanel.View v = wrenchView();
        final int[] at = wrenchAt(MEASURE);
        final int[] size = WrenchPanel.size(MEASURE, v);
        final int x = at[0];
        final List<Callouts.Choice> choices = new ArrayList<>();
        final int[] aim = aim();

        final int[] cross = around(aim[0], aim[1], CROSSHAIR / 2);
        choices.add(new Callouts.Choice("aim", List.of(at("aim", cross, aim[0] + 6, aim[1]),
                at("aim", cross, aim[0] - 6, aim[1]))));
        final int[] all = WrenchPanel.allTabBox(MEASURE, v, x, at[1]);

        choices.add(new Callouts.Choice("all", List.of(at("all", all, all[0], all[1] + 5), at("all", all, all[0], all[1]),
                at("all", all, all[0] + all[2] / 2, all[1]))));
        final List<SpecimenAnatomy.Hot> tabs = new ArrayList<>();
        for (int i = v.tabs().size() - 1; i >= 0; i--) {
            final int[] b = WrenchPanel.wireTabBox(MEASURE, v, x, at[1], i);
            tabs.add(at("tab", b, b[0] + b[2] - 1, b[1] + 5));
            tabs.add(at("tab", b, b[0] + b[2] / 2, b[1]));
        }
        choices.add(new Callouts.Choice("tab", tabs));
        final int[] swatch = WrenchPanel.swatchBox(v, x, at[1]);
        final int[] word = WrenchPanel.levelBox(MEASURE, v, x, at[1]);
        final int[] level = swatch == null ? word : new int[]{swatch[0], word[1], word[0] + word[2] - swatch[0], 8};
        choices.add(new Callouts.Choice("level", List.of(at("level", level, x + MARGIN, word[1] + 3),
                at("level", level, word[0] + word[2] + 2, word[1] + 3))));
        final int[] pips = WrenchPanel.pipsBox(v, x, at[1], size[0]);
        choices.add(new Callouts.Choice("pips", List.of(at("pips", pips, pips[0] + pips[2] + 2, pips[1] + 1))));
        final int[] detail = WrenchPanel.detailBox(MEASURE, v, x, at[1]);
        choices.add(new Callouts.Choice("detail", List.of(at("detail", detail, x + MARGIN, detail[1] + 3),
                at("detail", detail, detail[0] + detail[2] + 2, detail[1] + 3))));
        final int[] hint = WrenchPanel.hintBox(MEASURE, v, x, at[1]);
        choices.add(new Callouts.Choice("hint", List.of(at("hint", hint, hint[0] + hint[2] + 2, hint[1] + 3),
                at("hint", hint, x + MARGIN, hint[1] + 3))));
        return Callouts.cleanest(WRENCH, width(WRENCH), height(WRENCH), SpecimenAnatomy.Kind.OVERLAY, choices,
                wrenchPrint());
    }
}
