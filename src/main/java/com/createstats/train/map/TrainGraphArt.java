package com.createstats.train.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

// ibry's diagram, 1843. still the best
public final class TrainGraphArt {
    private TrainGraphArt() {
    }

    private static final float AXIS_H = 26.0f;

    private static final float LANE_PAD = 9.0f;

    private static final float GUTTER_MIN = 48.0f;
    private static final float GUTTER_MAX = 156.0f;

    private static final float LABEL_MIN_GAP = 9.0f;

    private static final float GRID_MIN_PX = 44.0f;

    private static final int[] TIME_STEPS = {10, 15, 30, 60, 120, 300, 600, 1800};

    private static final int MAX_CONFLICT_TRAINS = 12;
    private static final int MAX_PLANNED_POINTS = 14;

    private static final float WRAP_FRACTION = 0.55f;

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        float[] points = new float[1024];
        final List<float[]> planned = new ArrayList<>();
        final List<UUID> plannedOwner = new ArrayList<>();
        final List<Integer> plannedCount = new ArrayList<>();
        final List<float[]> conflicts = new ArrayList<>();

        final List<Rect> labels = new ArrayList<>();

        float[] ensure(int size) {
            if (this.points.length < size) {
                this.points = java.util.Arrays.copyOf(this.points,
                        Math.max(size, this.points.length * 2));
            }
            return this.points;
        }

        void reset() {
            this.planned.clear();
            this.plannedOwner.clear();
            this.plannedCount.clear();
            this.conflicts.clear();
            this.labels.clear();
        }
    }

    public record Frame(Rect gutter, Rect plot, Rect lane, Rect axis) {
        public static final Frame EMPTY =
                new Frame(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);
    }

    public static Frame draw(PanelSurface c, Rect area, RailMap map, TrainGraphView view) {
        if (area.isEmpty() || map == null) {
            return Frame.EMPTY;
        }
        view.ensureLine(map);
        final LineChainage chainage = RailHistory.chainage(map, view.line());
        final RailMap.Line line = view.line() < 0 || view.line() >= map.lines().size()
                ? null : map.lines().get(view.line());

        if (line == null || chainage.isEmpty()) {
            drawNothingToGraph(c, area, map);
            return Frame.EMPTY;
        }

        final float gutterW = gutterWidth(c, chainage, area);
        final Rect gutter = new Rect(area.x, area.y, gutterW, area.h - AXIS_H);
        final Rect plot = new Rect(area.x + gutterW, area.y,
                area.w - gutterW, area.h - AXIS_H);
        final Rect axis = new Rect(area.x + gutterW, area.bottom() - AXIS_H,
                area.w - gutterW, AXIS_H);
        if (plot.w < 40.0f || plot.h < 40.0f) {
            c.textCenterV("Too small for a diagram", area.centerX(), area.centerY(),
                    Tokens.INK_3, Text.MICRO);
            return Frame.EMPTY;
        }

        final Rect lane = new Rect(plot.x, plot.y + LANE_PAD, plot.w,
                Math.max(1.0f, plot.h - LANE_PAD * 2.0f));
        final float now = RailHistory.latestTime();
        final Scratch s = SCRATCH.get();
        s.reset();

        drawFutureWash(c, plot, view, now);
        drawStopBands(c, plot, lane, chainage, view);
        if (view.showGrid()) {
            drawTimeGrid(c, plot, axis, view, now);
        }
        drawStopRules(c, gutter, plot, lane, chainage, view);

        final RailSnapshot snapshot = map.snapshot();
        if (view.showPlanned()) {
            buildPlanned(map, chainage, view, snapshot, plot, lane, now, s);
            drawPlanned(c, plot, map, view, s);
        }
        drawRecorded(c, plot, lane, map, chainage, view, snapshot, now, s);
        drawNowLine(c, plot, view, now);
        if (view.showConflicts()) {
            findConflicts(s, plot);
            drawConflicts(c, plot, s);
        }
        drawCursor(c, plot, lane, chainage, view, now);
        drawFooter(c, axis, map, line, chainage, view);
        return new Frame(gutter, plot, lane, axis);
    }

    private static void drawNothingToGraph(PanelSurface c, Rect area, RailMap map) {
        final boolean anyTrains = !map.snapshot().trains().isEmpty();
        c.textCenterV(anyTrains ? "No scheduled service to graph" : "No trains on the network",
                area.centerX(), area.centerY() - 10.0f, Tokens.INK_2, Text.BODY);
        c.textCenterV(anyTrains
                        ? "A diagram plots one service against time. Give a train a schedule with two"
                        + " or more stops."
                        : "Assemble a train and give it a schedule to see it here.",
                area.centerX(), area.centerY() + 8.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void drawFutureWash(PanelSurface c, Rect plot, TrainGraphView view, float now) {
        if (Float.isNaN(now)) {
            return;
        }
        final float x = Math.max(plot.x, view.xOf(now, now, plot.x, plot.w));
        if (x >= plot.right()) {
            return;
        }
        Shapes.rect(c, x, plot.y, plot.right() - x, plot.h, Tokens.alpha(Tokens.CANVAS, 0.42f));
    }

    private static float gutterWidth(PanelSurface c, LineChainage chainage, Rect area) {
        float widest = 0.0f;
        for (int i = 0; i < chainage.stopCount(); i++) {
            final RailMap.Node node = chainage.node(i);
            if (node != null) {
                widest = Math.max(widest, c.textWidth(node.name, Text.MICRO));
            }
        }

        return Math.max(GUTTER_MIN, Math.min(Math.min(GUTTER_MAX, area.w * 0.33f), widest + 12.0f));
    }

    private static void drawStopBands(PanelSurface c, Rect plot, Rect lane, LineChainage chainage,
                                      TrainGraphView view) {
        final int band = Tokens.alpha(Tokens.INK, 0.022f);
        for (int i = 0; i + 1 < chainage.stopCount(); i += 2) {
            final float top = view.yOf(chainage.chain[i], chainage, lane.y, lane.h);
            final float bottom = view.yOf(chainage.chain[i + 1], chainage, lane.y, lane.h);
            Shapes.rect(c, plot.x, top, plot.w, Math.max(1.0f, bottom - top), band);
        }
    }

    private static void drawStopRules(PanelSurface c, Rect gutter, Rect plot, Rect lane,
                                      LineChainage chainage, TrainGraphView view) {
        final int stops = chainage.stopCount();
        final boolean[] labelled = pickLabels(chainage, view, lane, stops);

        for (int i = 0; i < stops; i++) {
            final float y = view.yOf(chainage.chain[i], chainage, lane.y, lane.h);
            final RailMap.Node node = chainage.node(i);
            final boolean end = i == 0 || i == stops - 1;
            final boolean interchange = node != null && node.isInterchange();

            c.hairlineH(plot.x, y, plot.w,
                    Tokens.alpha(end ? Tokens.LINE_HI : Tokens.LINE, end ? 0.95f : 0.65f));

            Shapes.rect(c, plot.x - 3.0f, y - 0.5f, 3.0f, 1.0f,
                    Tokens.alpha(interchange ? Tokens.ACCENT : Tokens.LINE_HI, 0.8f));

            if (!labelled[i] || node == null) {
                continue;
            }
            final int ink = node.presentTrain() != null ? Tokens.GOOD
                    : interchange ? Tokens.INK_2 : Tokens.INK_3;
            c.textRightV(Text.truncate(c, node.name, gutter.w - 8.0f, Text.MICRO),
                    gutter.right() - 5.0f, y, ink, Text.MICRO);
        }
    }

    private static boolean[] pickLabels(LineChainage chainage, TrainGraphView view, Rect lane,
                                        int stops) {
        final boolean[] out = new boolean[stops];

        final int[] priority = new int[stops];
        for (int i = 0; i < stops; i++) {
            final RailMap.Node node = chainage.node(i);
            priority[i] = i == 0 || i == stops - 1 ? 2
                    : node != null && (node.isInterchange() || node.terminus) ? 1 : 0;
        }
        for (int pass = 2; pass >= 0; pass--) {
            for (int i = 0; i < stops; i++) {
                if (priority[i] != pass || out[i]) {
                    continue;
                }
                final float y = view.yOf(chainage.chain[i], chainage, lane.y, lane.h);
                boolean clear = true;
                for (int j = 0; j < stops; j++) {
                    if (!out[j]) {
                        continue;
                    }
                    final float other = view.yOf(chainage.chain[j], chainage, lane.y, lane.h);
                    if (Math.abs(other - y) < LABEL_MIN_GAP) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    out[i] = true;
                }
            }
        }
        return out;
    }

    private static void drawTimeGrid(PanelSurface c, Rect plot, Rect axis, TrainGraphView view,
                                     float now) {
        final float total = view.span() + view.future();
        final float pxPerSecond = plot.w / Math.max(1.0f, total);
        int step = TIME_STEPS[TIME_STEPS.length - 1];
        for (final int candidate : TIME_STEPS) {
            if (candidate * pxPerSecond >= GRID_MIN_PX) {
                step = candidate;
                break;
            }
        }

        final float right = view.rightEdgeTime(now);
        final float left = view.leftEdgeTime(now);
        final int first = (int) Math.ceil((left - now) / step);
        final int last = (int) Math.floor((right - now) / step);

        for (int k = first; k <= last; k++) {
            final float time = now + (float) k * step;
            final float x = view.xOf(time, now, plot.x, plot.w);
            final boolean major = k % 5 == 0;
            if (k == 0) {
                continue;
            }
            c.hairlineV(x, plot.y, plot.h,
                    Tokens.alpha(Tokens.LINE, major ? 0.85f : 0.42f));
            if (major || step * pxPerSecond >= 34.0f) {
                c.textCenter(offsetLabel((float) k * step), x, axis.y + 3.0f,
                        Tokens.alpha(Tokens.INK_3, major ? 0.9f : 0.6f), Text.MICRO);
            }
        }
    }

    private static String offsetLabel(float seconds) {
        final float a = Math.abs(seconds);
        final String sign = seconds < 0.0f ? "-" : "+";
        if (a < 60.0f) {
            return sign + Math.round(a) + "s";
        }
        if (a % 60.0f < 0.5f) {
            return sign + Math.round(a / 60.0f) + "m";
        }
        return String.format(Locale.ROOT, "%s%d:%02d", sign, (int) (a / 60.0f),
                Math.round(a % 60.0f));
    }

    private static void drawNowLine(PanelSurface c, Rect plot, TrainGraphView view, float now) {
        final float x = view.xOf(now, now, plot.x, plot.w);
        if (x < plot.x - 1.0f || x > plot.right() + 1.0f) {
            return;
        }
        Shapes.rect(c, x - 0.5f, plot.y, 1.0f, plot.h, Tokens.alpha(Tokens.ACCENT, 0.55f));
        final String label = view.live() ? "NOW" : "THEN";
        final float w = c.textWidth(label, Text.MICRO) + 10.0f;
        final Rect pill = new Rect(
                Math.max(plot.x, Math.min(x - w * 0.5f, plot.right() - w)), plot.y - 1.0f, w, 12.0f);
        c.card(pill, 6.0f, Tokens.alpha(Tokens.CANVAS, 0.92f), Tokens.alpha(Tokens.ACCENT, 0.5f));
        c.textCenterV(label, pill.centerX(), pill.centerY(), Tokens.ACCENT, Text.MICRO);
    }

    private static void drawRecorded(PanelSurface c, Rect plot, Rect lane, RailMap map,
                                     LineChainage chainage, TrainGraphView view,
                                     RailSnapshot snapshot, float now, Scratch s) {
        if (Float.isNaN(now)) {
            c.textCenterV("Recording. The diagram fills in as the trains run.",
                    plot.centerX(), plot.centerY(), Tokens.INK_3, Text.MICRO);
            return;
        }
        final RailMap.Line line = map.lines().get(view.line());
        final float left = view.leftEdgeTime(now);
        final float right = view.rightEdgeTime(now);

        for (final UUID id : line.trains) {
            final RailHistory.Series series = RailHistory.of(id);
            if (series == null || series.size() < 2) {
                continue;
            }
            final RailSnapshot.TrainState t = snapshot.train(id);
            final int color = trainColor(t, id);
            final boolean selected = id.equals(view.selectedTrain());
            final boolean dimmed = view.selectedTrain() != null && !selected;
            final float width = selected ? 2.4f : 1.45f;
            final int stroke = dimmed ? Tokens.alpha(color, 0.22f) : color;

            drawSeries(c, plot, lane, chainage, view, series, now, left, right, stroke, width, s);
            if (view.showHolds() && !dimmed) {
                drawHolds(c, plot, lane, chainage, view, series, now, left, right);
            }
            drawHead(c, plot, lane, chainage, view, series, t, now, left, right, stroke,
                    selected, s);
        }
    }

    // break at wraps and gaps or a line runs through the whole sheet
    private static void drawSeries(PanelSurface c, Rect plot, Rect lane, LineChainage chainage,
                                   TrainGraphView view, RailHistory.Series series, float now,
                                   float left, float right, int color, float width, Scratch s) {
        final float wrap = chainage.total * WRAP_FRACTION;
        final float maxGap = 4.0f;
        float[] pts = s.ensure(series.size() * 2);
        int n = 0;
        float previousTime = Float.NaN;
        float previousChain = Float.NaN;

        for (int i = 0; i < series.size(); i++) {
            final float time = series.time(i);
            if (time < left - maxGap || time > right + maxGap) {
                if (n > 1) {
                    c.polyline(pts, n, width, color);
                }
                n = 0;
                previousTime = Float.NaN;
                continue;
            }
            final float chain = series.chain(i);
            final boolean broken = !Float.isNaN(previousTime)
                    && (time - previousTime > maxGap || Math.abs(chain - previousChain) > wrap);
            if (broken) {
                if (n > 1) {
                    c.polyline(pts, n, width, color);
                }
                n = 0;
            }
            pts = s.ensure((n + 1) * 2);
            pts[n * 2] = view.xOf(time, now, plot.x, plot.w);
            pts[n * 2 + 1] = view.yOf(chain, chainage, lane.y, lane.h);
            n++;
            previousTime = time;
            previousChain = chain;
        }
        if (n > 1) {
            c.polyline(pts, n, width, color);
        }
    }

    private static void drawHolds(PanelSurface c, Rect plot, Rect lane, LineChainage chainage,
                                  TrainGraphView view, RailHistory.Series series, float now,
                                  float left, float right) {
        byte previous = RailHistory.MOVING;
        for (int i = 0; i < series.size(); i++) {
            final byte state = series.state(i);
            final float time = series.time(i);
            if (state == RailHistory.HELD && previous != RailHistory.HELD
                    && time >= left && time <= right) {
                final float x = view.xOf(time, now, plot.x, plot.w);
                final float y = view.yOf(series.chain(i), chainage, lane.y, lane.h);
                Shapes.rect(c, x - 0.5f, y - 3.5f, 1.0f, 7.0f, Tokens.alpha(Tokens.CAUTION, 0.9f));
            }
            previous = state;
        }
    }

    private static void drawHead(PanelSurface c, Rect plot, Rect lane, LineChainage chainage,
                                 TrainGraphView view, RailHistory.Series series,
                                 RailSnapshot.TrainState t, float now, float left, float right,
                                 int color, boolean selected, Scratch s) {
        final float time = series.latestTime();
        if (Float.isNaN(time) || time < left || time > right) {
            return;
        }
        final float x = view.xOf(time, now, plot.x, plot.w);
        final float y = view.yOf(series.latestChain(), chainage, lane.y, lane.h);
        Shapes.circle(c, x, y, selected ? 4.2f : 3.0f, Tokens.alpha(Tokens.CANVAS, 0.85f));
        Shapes.circle(c, x, y, selected ? 3.0f : 2.0f, color);
        if (t == null) {
            return;
        }
        if (t.unhealthy()) {
            Shapes.circle(c, x, y, 6.0f, Tokens.alpha(Tokens.CRITICAL, 0.30f));
        }
        if (!selected && view.selectedTrain() != null) {
            return;
        }

        final String name = Text.truncate(c, t.name, 76.0f, Text.MICRO);
        final float w = c.textWidth(name, Text.MICRO);
        if (x - w - 10.0f < plot.x) {
            return;
        }
        final float[] offsets = {-8.0f, -18.0f, 4.0f, -28.0f, 14.0f};
        for (final float dy : offsets) {
            final Rect box = new Rect(x - 6.0f - w, y + dy - 1.0f, w + 4.0f, 10.0f);
            if (box.y < plot.y || box.bottom() > plot.bottom() || collides(s.labels, box)) {
                continue;
            }
            s.labels.add(box);
            c.textRight(name, x - 6.0f, box.y + 1.0f,
                    Tokens.alpha(selected ? Tokens.INK : Tokens.INK_2, 0.92f), Text.MICRO);
            return;
        }
    }

    private static boolean collides(List<Rect> placed, Rect candidate) {
        for (final Rect other : placed) {
            if (!other.intersect(candidate).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void buildPlanned(RailMap map, LineChainage chainage, TrainGraphView view,
                                     RailSnapshot snapshot, Rect plot, Rect lane, float now,
                                     Scratch s) {
        if (Float.isNaN(now)) {
            return;
        }
        final RailMap.Line line = map.lines().get(view.line());
        final float right = view.rightEdgeTime(now);

        for (final UUID id : line.trains) {
            final RailSnapshot.TrainState t = snapshot.train(id);
            if (t == null) {
                continue;
            }
            final float from = chainage.chainageOf(t);
            int at = chainage.startIndex(t);
            if (Float.isNaN(from) || at < 0) {
                continue;
            }

            final List<float[]> run = new ArrayList<>();
            run.add(new float[]{now, from, 0.0f});

            float lastTime = now;
            for (final RailSnapshot.Arrival arrival : snapshot.arrivals()) {
                if (!id.equals(arrival.trainId) || !arrival.timed()) {
                    continue;
                }
                final float when = now + arrival.seconds();
                if (when > right + 5.0f || when < lastTime - 0.5f) {
                    continue;
                }
                final int target = chainage.nodeOfFilter(arrival.station, t.dimension);
                int next = chainage.stepTo(at, target);
                boolean wrapped = false;
                if (next < 0) {
                    final int partner = chainage.wrapPartner(at);
                    next = partner < 0 ? -1 : chainage.stepTo(partner, target);
                    if (next < 0) {
                        break;
                    }
                    run.add(new float[]{lastTime, chainage.chainAt(partner), 1.0f});
                    wrapped = true;
                }
                run.add(new float[]{when, chainage.chainAt(next), 0.0f});
                at = next;
                lastTime = when;
                if (run.size() >= MAX_PLANNED_POINTS) {
                    break;
                }
            }
            emit(run, view, plot, lane, chainage, now, s, id);
        }
    }

    private static void emit(List<float[]> run, TrainGraphView view, Rect plot, Rect lane,
                             LineChainage chainage, float now, Scratch s, UUID owner) {
        int from = 0;
        for (int i = 1; i <= run.size(); i++) {
            final boolean breakHere = i < run.size() && run.get(i)[2] > 0.5f;
            if (!breakHere && i < run.size()) {
                continue;
            }

            final int end = breakHere ? i : run.size();
            push(run, from, end, view, plot, lane, chainage, now, s, owner);
            from = breakHere ? i : end;
        }
    }

    private static void push(List<float[]> run, int from, int to, TrainGraphView view, Rect plot,
                             Rect lane, LineChainage chainage, float now, Scratch s, UUID owner) {
        final int points = to - from;
        if (points < 2) {
            return;
        }
        final float[] screen = new float[points * 2];
        for (int i = 0; i < points; i++) {
            final float[] point = run.get(from + i);
            screen[i * 2] = view.xOf(point[0], now, plot.x, plot.w);
            screen[i * 2 + 1] = view.yOf(point[1], chainage, lane.y, lane.h);
        }
        s.planned.add(screen);
        s.plannedCount.add(points);
        s.plannedOwner.add(owner);
    }

    private static void drawPlanned(PanelSurface c, Rect plot, RailMap map, TrainGraphView view,
                                    Scratch s) {
        for (int i = 0; i < s.planned.size(); i++) {
            final UUID owner = s.plannedOwner.get(i);
            final boolean selected = owner.equals(view.selectedTrain());
            final boolean dimmed = view.selectedTrain() != null && !selected;
            final int color = trainColor(map.snapshot().train(owner), owner);
            dash(c, s.planned.get(i), s.plannedCount.get(i), selected ? 1.8f : 1.2f,
                    Tokens.alpha(color, dimmed ? 0.14f : selected ? 0.8f : 0.5f), 5.0f, 4.0f);
        }
    }

    private static void findConflicts(Scratch s, Rect plot) {
        final int lines = Math.min(s.planned.size(), MAX_CONFLICT_TRAINS);
        for (int a = 0; a < lines; a++) {
            for (int b = a + 1; b < lines; b++) {
                if (s.plannedOwner.get(a).equals(s.plannedOwner.get(b))) {
                    continue;
                }
                crossings(s, s.planned.get(a), s.plannedCount.get(a),
                        s.planned.get(b), s.plannedCount.get(b), plot);
                if (s.conflicts.size() >= 32) {
                    return;
                }
            }
        }
    }

    private static void crossings(Scratch s, float[] p, int pn, float[] q, int qn, Rect plot) {
        for (int i = 0; i + 1 < pn; i++) {
            final float pMinX = Math.min(p[i * 2], p[(i + 1) * 2]);
            final float pMaxX = Math.max(p[i * 2], p[(i + 1) * 2]);
            for (int j = 0; j + 1 < qn; j++) {
                if (Math.max(q[j * 2], q[(j + 1) * 2]) < pMinX
                        || Math.min(q[j * 2], q[(j + 1) * 2]) > pMaxX) {
                    continue;
                }
                final float[] hit = intersect(
                        p[i * 2], p[i * 2 + 1], p[(i + 1) * 2], p[(i + 1) * 2 + 1],
                        q[j * 2], q[j * 2 + 1], q[(j + 1) * 2], q[(j + 1) * 2 + 1]);
                if (hit == null || !plot.contains(hit[0], hit[1])) {
                    continue;
                }

                final float slopeP = p[(i + 1) * 2 + 1] - p[i * 2 + 1];
                final float slopeQ = q[(j + 1) * 2 + 1] - q[j * 2 + 1];
                final boolean meet = slopeP * slopeQ < 0.0f;
                s.conflicts.add(new float[]{hit[0], hit[1], meet ? 1.0f : 0.0f});
                if (s.conflicts.size() >= 32) {
                    return;
                }
            }
        }
    }

    private static float[] intersect(float ax, float ay, float bx, float by,
                                     float cx, float cy, float dx, float dy) {
        final float rx = bx - ax;
        final float ry = by - ay;
        final float sx = dx - cx;
        final float sy = dy - cy;
        final float denominator = rx * sy - ry * sx;
        if (Math.abs(denominator) < 1.0e-5f) {
            return null;
        }
        final float t = ((cx - ax) * sy - (cy - ay) * sx) / denominator;
        final float u = ((cx - ax) * ry - (cy - ay) * rx) / denominator;
        if (t < 0.0f || t > 1.0f || u < 0.0f || u > 1.0f) {
            return null;
        }
        return new float[]{ax + rx * t, ay + ry * t};
    }

    private static void drawConflicts(PanelSurface c, Rect plot, Scratch s) {
        for (final float[] hit : s.conflicts) {
            final boolean meet = hit[2] > 0.5f;
            final int color = meet ? Tokens.CAUTION : Tokens.INK_2;
            final float r = meet ? 3.6f : 3.0f;

            c.tri(hit[0], hit[1] - r, color, hit[0] + r, hit[1], color, hit[0], hit[1] + r, color);
            c.tri(hit[0], hit[1] - r, color, hit[0] - r, hit[1], color, hit[0], hit[1] + r, color);
            if (meet) {
                Shapes.circle(c, hit[0], hit[1], r + 3.0f, Tokens.alpha(color, 0.16f));
            }
        }
    }

    private static void drawCursor(PanelSurface c, Rect plot, Rect lane, LineChainage chainage,
                                   TrainGraphView view, float now) {
        if (!view.hasCursor() || Float.isNaN(now)) {
            return;
        }
        final float x = view.xOf(view.cursorTime(), now, plot.x, plot.w);
        final float y = view.yOf(view.cursorChain(), chainage, lane.y, lane.h);
        if (!plot.contains(x, y)) {
            return;
        }
        final int hair = Tokens.alpha(Tokens.INK, 0.20f);
        c.hairlineV(x, plot.y, plot.h, hair);
        c.hairlineH(plot.x, y, plot.w, hair);

        final int stop = chainage.stopNear(view.cursorChain());
        final RailMap.Node node = chainage.node(stop);
        final String when = offsetLabel(view.cursorTime() - now);
        final String where = node == null ? TrainIntel.distance(view.cursorChain())
                : node.name + "  " + TrainIntel.distance(view.cursorChain());
        final float w = Math.max(c.textWidth(when, Text.MICRO), c.textWidth(where, Text.MICRO))
                + 12.0f;
        final float boxX = Math.min(x + 8.0f, plot.right() - w - 2.0f);
        final float boxY = Math.min(y + 8.0f, plot.bottom() - 30.0f);
        final Rect box = new Rect(boxX, boxY, w, 28.0f);
        c.card(box, Tokens.R_CHIP, Tokens.alpha(Tokens.OVERLAY, 0.96f), Tokens.LINE_HI);
        c.text(when, box.x + 6.0f, box.y + 4.0f, Tokens.INK, Text.MICRO);
        c.text(Text.truncate(c, where, box.w - 12.0f, Text.MICRO), box.x + 6.0f, box.y + 15.0f,
                Tokens.INK_3, Text.MICRO);
    }

    private static void drawFooter(PanelSurface c, Rect axis, RailMap map, RailMap.Line line,
                                   LineChainage chainage, TrainGraphView view) {
        if (axis.w < 130.0f) {
            return;
        }
        final float y = axis.y + 14.0f;
        if (axis.w >= 190.0f) {
            final String title = Text.truncate(c, line.label(map), axis.w * 0.42f, Text.MICRO);
            c.text(title, axis.x + 2.0f, y, Tokens.INK_2, Text.MICRO);
        }

        final StringBuilder note = new StringBuilder();
        note.append(chainage.stopCount()).append(" stops  .  ")
                .append(TrainIntel.distance(chainage.total));
        note.append(view.evenSpacing() ? "  .  even spacing" : "  .  to scale");
        c.textRight(note.toString(), axis.right() - 2.0f, y,
                Tokens.alpha(Tokens.INK_3, 0.8f), Text.MICRO);

        final float mid = axis.centerX();
        final String key = view.showPlanned() ? "run . . . forecast" : "recorded run";
        final float keyW = c.textWidth(key, Text.MICRO);
        if (axis.w >= 320.0f && keyW < axis.w * 0.3f) {
            c.textCenter(key, mid, y, Tokens.alpha(Tokens.INK_3, 0.55f), Text.MICRO);
        }
    }

    public static int trainColor(RailSnapshot.TrainState t, UUID id) {
        if (t != null && t.mapColor >= 0) {
            return Tokens.SERIES[t.mapColor % Tokens.SERIES.length];
        }
        final int h = id == null ? 0 : id.hashCode();
        return Tokens.SERIES[Math.floorMod(h, Tokens.SERIES.length)];
    }

    private static void dash(PanelSurface c, float[] pts, int points, float width, int color,
                             float on, float off) {
        if (points < 2) {
            return;
        }
        float carry = 0.0f;
        boolean drawing = true;
        for (int i = 0; i + 1 < points; i++) {
            float x0 = pts[i * 2];
            float y0 = pts[i * 2 + 1];
            final float x1 = pts[(i + 1) * 2];
            final float y1 = pts[(i + 1) * 2 + 1];
            float remaining = (float) Math.hypot(x1 - x0, y1 - y0);
            if (remaining < 0.001f) {
                continue;
            }
            final float ux = (x1 - x0) / remaining;
            final float uy = (y1 - y0) / remaining;
            while (remaining > 0.0f) {
                final float want = (drawing ? on : off) - carry;
                final float take = Math.min(want, remaining);
                if (drawing) {
                    Shapes.line(c, x0, y0, x0 + ux * take, y0 + uy * take, width, color);
                }
                x0 += ux * take;
                y0 += uy * take;
                remaining -= take;
                carry += take;
                if (carry >= (drawing ? on : off) - 0.001f) {
                    carry = 0.0f;
                    drawing = !drawing;
                }
            }
        }
    }
}
