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

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// transit map conventions, a century of people figured these out
@OnlyIn(Dist.CLIENT)
public final class RailMapArt {
    private RailMapArt() {
    }

    private static final float LINE_W = 3.0f;

    private static final float BUNDLE_GAP = 3.6f;

    private static final float NODE_R = 4.2f;
    private static final float INTERCHANGE_R = 6.4f;

    private static final float LOD_LABELS = 26.0f;
    private static final float LOD_DETAIL = 88.0f;

    private static final float TAU = (float) (Math.PI * 2.0);

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final float[][] path = new float[1][];
        final float[][] offset = new float[1][];
        float[] screen = new float[256];
        final List<Rect> labels = new ArrayList<>();
        final List<TrainMark> trains = new ArrayList<>();

        int[] tiers = new int[0];
        int tierCount;

        float[] screen(int size) {
            if (this.screen.length < size) {
                this.screen = new float[Math.max(size, this.screen.length * 2)];
            }
            return this.screen;
        }

        int[] tiers(int size) {
            if (this.tiers.length < size) {
                this.tiers = new int[Math.max(size, 16)];
            }
            this.tierCount = size;
            return this.tiers;
        }
    }

    private record TrainMark(UUID id, float x, float y, int color, boolean selected) {
    }

    public static void draw(PanelSurface c, Rect plot, RailMap map, MapLayout layout, MapView view) {
        if (plot.isEmpty()) {
            return;
        }
        view.setViewport(plot);
        final Scratch s = SCRATCH.get();
        s.labels.clear();
        s.trains.clear();

        if (view.showGrid()) {
            drawGrid(c, plot, view, layout);
        }
        if (map.isEmpty()) {
            return;
        }

        final float detail = detailScale(plot, map, layout, view);

        final int[] tiers = s.tiers(map.lines().size());
        for (int i = 0; i < map.lines().size(); i++) {
            tiers[i] = map.lines().get(i).tier(map.snapshot());
        }

        if (view.showTrack()) {
            drawTrackBed(c, plot, map, view, s);
        }
        drawEdges(c, plot, map, layout, view, s, detail);
        drawNodes(c, plot, map, layout, view, s, detail);
        drawTrains(c, plot, map, view, s, detail);
        if (view.showLabels()) {
            drawLabels(c, plot, map, layout, view, s, detail);
        }
        drawStalls(c, plot, map, view);
        drawPlayer(c, plot, view);
        drawScaleBar(c, plot, view);
        drawCursorReadout(c, plot, view);
        drawCompass(c, plot, view);
    }

    private static void drawPlayer(PanelSurface c, Rect plot, MapView view) {
        final float truth = view.geographicWeight();
        if (!view.hasPlayer() || truth < 0.25f) {
            return;
        }
        final float x = view.screenX(view.playerX());
        final float y = view.screenY(view.playerZ());
        if (!inside(plot, x, y, 12.0f)) {
            return;
        }

        final double radians = Math.toRadians(view.playerFacing());
        final float ux = (float) -Math.sin(radians);
        final float uy = (float) Math.cos(radians);
        final float px = -uy;
        final float py = ux;

        final int halo = Tokens.alpha(Tokens.INK, 0.14f * truth);
        Shapes.circle(c, x, y, 9.0f, halo);
        Shapes.circle(c, x, y, 5.4f, Tokens.alpha(Tokens.CANVAS, 0.9f * truth));
        final int fill = Tokens.alpha(Tokens.INK, 0.95f * truth);
        c.tri(x + ux * 6.0f, y + uy * 6.0f, fill,
                x - ux * 3.2f + px * 3.4f, y - uy * 3.2f + py * 3.4f, fill,
                x - ux * 3.2f - px * 3.4f, y - uy * 3.2f - py * 3.4f, fill);
    }

    private static void drawCursorReadout(PanelSurface c, Rect plot, MapView view) {
        if (!view.hasCursor() || view.geographicWeight() < 0.6f) {
            return;
        }
        final String text = Math.round(view.cursorLayoutX()) + ", " + Math.round(view.cursorLayoutY());
        final float w = c.textWidth(text, Text.MICRO);
        c.textRightV(text, plot.right() - 8.0f, plot.bottom() - 9.0f,
                Tokens.alpha(Tokens.INK_3, 0.85f), Text.MICRO);
        c.textRightV("x, z", plot.right() - 12.0f - w, plot.bottom() - 9.0f,
                Tokens.alpha(Tokens.INK_3, 0.45f), Text.MICRO);
    }

    private static void drawGrid(PanelSurface c, Rect plot, MapView view, MapLayout layout) {
        final float scale = view.scale();
        if (scale <= 0.0f) {
            return;
        }

        final float preferred = view.geographicWeight() > 0.5f ? 0.0f : layout.gridStep();
        double step = preferred > 0.0f ? preferred : niceStep(64.0 / scale);
        if (step * scale < 8.0) {
            step = niceStep(64.0 / scale);
        }

        final float minorAlpha = Math.min(0.5f, (float) (step * scale) / 220.0f);
        final int minor = Tokens.alpha(Tokens.LINE, 0.30f + minorAlpha * 0.30f);
        final int major = Tokens.alpha(Tokens.LINE_HI, 0.42f);

        final float left = view.layoutX(plot.x);
        final float right = view.layoutX(plot.right());
        final float top = view.layoutY(plot.y);
        final float bottom = view.layoutY(plot.bottom());

        final long firstX = (long) Math.floor(left / step);
        final long lastX = (long) Math.ceil(right / step);
        final long firstY = (long) Math.floor(top / step);
        final long lastY = (long) Math.ceil(bottom / step);

        if (lastX - firstX > 400 || lastY - firstY > 400) {
            return;
        }

        for (long i = firstX; i <= lastX; i++) {
            final float x = view.screenX((float) (i * step));
            if (x < plot.x - 1.0f || x > plot.right() + 1.0f) {
                continue;
            }
            Shapes.rect(c, x, plot.y, 1.0f, plot.h, i % 5 == 0 ? major : minor);
        }
        for (long i = firstY; i <= lastY; i++) {
            final float y = view.screenY((float) (i * step));
            if (y < plot.y - 1.0f || y > plot.bottom() + 1.0f) {
                continue;
            }
            Shapes.rect(c, plot.x, y, plot.w, 1.0f, i % 5 == 0 ? major : minor);
        }
    }

    private static double niceStep(double raw) {
        final double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1.0e-6, raw))));
        final double n = raw / magnitude;
        final double snapped = n <= 1.0 ? 1.0 : n <= 2.0 ? 2.0 : n <= 5.0 ? 5.0 : 10.0;
        return snapped * magnitude;
    }

    // the track bed is ground truth
    private static void drawTrackBed(PanelSurface c, Rect plot, RailMap map, MapView view,
                                     Scratch s) {
        final float truth = view.geographicWeight();
        if (truth < 0.02f) {
            return;
        }
        final List<RailSnapshot.Track> tracks = map.snapshot().tracks();
        if (tracks.isEmpty()) {
            return;
        }
        final float scale = view.scale();

        final float width = Math.max(1.2f, Math.min(5.0f, 1.1f + scale * 3.0f));
        final float focus = 1.0f - view.focus() * 0.55f;
        // grey, there but quieter than any service

        final int bed = Tokens.alpha(Tokens.INK_3,
                (0.44f + 0.24f * Math.min(1.0f, scale * 2.0f)) * truth * focus);
        final int rail = Tokens.alpha(Tokens.INK_2, 0.30f * truth * focus);

        final float left = view.layoutX(plot.x - 8.0f);
        final float right = view.layoutX(plot.right() + 8.0f);
        final float top = view.layoutY(plot.y - 8.0f);
        final float bottom = view.layoutY(plot.bottom() + 8.0f);
        final float shortest = 1.0f / Math.max(1.0e-5f, scale);
        boolean drawn = false;

        for (final RailSnapshot.Track track : tracks) {
            final int points = track.path.length / 2;
            if (points < 2) {
                continue;
            }
            final float[] box = track.bounds();
            if (box[2] < left || box[0] > right || box[3] < top || box[1] > bottom) {
                continue;
            }

            if (box[2] - box[0] < shortest && box[3] - box[1] < shortest) {
                continue;
            }
            final float[] screen = s.screen(points * 2);
            for (int i = 0; i < points; i++) {
                screen[i * 2] = view.screenX(track.path[i * 2]);
                screen[i * 2 + 1] = view.screenY(track.path[i * 2 + 1]);
            }
            drawn = true;
            c.polyline(screen, points, width, bed);
            if (width > 3.2f) {
                final float[] offsetBuffer = ensure(s.offset, points * 2);
                for (int side = -1; side <= 1; side += 2) {
                    MapLayout.offsetPath(screen, points, side * width * 0.26f, offsetBuffer);
                    c.polyline(offsetBuffer, points, 1.0f, rail);
                }
            }
        }

        if (drawn && map.snapshot().tracksTruncated) {
            c.textV("rail layer partial", plot.x + 8.0f, plot.y + 10.0f,
                    Tokens.alpha(Tokens.CAUTION, 0.75f * truth), Text.MICRO);
        }
    }

    private static boolean drawsTrackBed(RailMap map, MapView view) {
        return view.showTrack() && view.geographicWeight() > 0.9f
                && !map.snapshot().tracks().isEmpty();
    }

    private static void drawEdges(PanelSurface c, Rect plot, RailMap map, MapLayout layout,
                                  MapView view, Scratch s, float detail) {
        final float blend = view.morph();
        final float zoomW = strokeWidth(detail);

        for (final RailMap.Edge edge : map.edges()) {
            final float presenceA = layout.presence(edge.a, view.previousMode(), view.mode(), blend);
            final float presenceB = layout.presence(edge.b, view.previousMode(), view.mode(), blend);
            final float presence = Math.min(presenceA, presenceB);
            if (presence < 0.02f) {
                continue;
            }
            final int points = layout.edgePath(edge, view.previousMode(), view.mode(), blend, s.path);
            if (points < 2) {
                continue;
            }
            final float[] screen = s.screen(points * 2);
            boolean anyVisible = false;
            for (int i = 0; i < points; i++) {
                screen[i * 2] = view.screenX(s.path[0][i * 2]);
                screen[i * 2 + 1] = view.screenY(s.path[0][i * 2 + 1]);
                if (!anyVisible && inside(plot, screen[i * 2], screen[i * 2 + 1], 64.0f)) {
                    anyVisible = true;
                }
            }
            if (!anyVisible) {
                continue;
            }

            if (edge.lines.isEmpty()) {
                if (!edge.inferred && drawsTrackBed(map, view)) {
                    continue;
                }

                final int color = Tokens.alpha(Tokens.INK_3, 0.34f * presence);
                if (edge.inferred) {
                    dashed(c, screen, points, 1.4f, color);
                } else {
                    c.polyline(screen, points, 1.6f, color);
                }
                continue;
            }
            drawBundle(c, edge, screen, points, zoomW, presence, map, view, s);
        }
    }

    private static void drawBundle(PanelSurface c, RailMap.Edge edge, float[] screen, int points,
                                   float width, float presence, RailMap map, MapView view,
                                   Scratch s) {
        final int count = edge.lines.size();

        int slots = 0;
        boolean anyDormant = false;
        for (int i = 0; i < count; i++) {
            if (tierOf(s, edge.lines.get(i), view) == RailMap.Line.TIER_DORMANT) {
                anyDormant = true;
            } else {
                slots++;
            }
        }
        final int dormantSlot = anyDormant ? slots++ : -1;
        if (slots == 0) {
            return;
        }

        final float gap = Math.max(2.0f, BUNDLE_GAP * (width / LINE_W));
        final float[] offsetBuffer = ensure(s.offset, points * 2);

        for (int pass = 0; pass < 2; pass++) {
            int slot = 0;
            boolean dormantDrawn = false;
            for (int i = 0; i < count; i++) {
                final int lineIndex = edge.lines.get(i);
                final int tier = tierOf(s, lineIndex, view);
                final boolean dormant = tier == RailMap.Line.TIER_DORMANT;
                if (dormant && dormantDrawn) {
                    continue;
                }
                final int here = dormant ? dormantSlot : slot++;
                dormantDrawn |= dormant;

                final float alpha = presence * lineAlpha(view, lineIndex) * tierAlpha(tier);
                if (alpha < 0.02f) {
                    continue;
                }
                final float offset = (here - (slots - 1) * 0.5f) * gap;
                MapLayout.offsetPath(screen, points, offset, offsetBuffer);

                // inferred runs: narrower, fainter, dashed. three signals so it reads in greyscale

                final float w = (edge.inferred ? width * 0.55f : width) * tierWidth(tier);
                final float a = edge.inferred ? alpha * 0.5f : alpha;

                if (pass == 0) {
                    if (edge.inferred || dormant) {
                        continue;
                    }

                    c.polyline(offsetBuffer, points, w + 2.4f,
                            Tokens.alpha(Tokens.CANVAS, 0.92f * a));
                    continue;
                }

                final int color = dormant ? Tokens.alpha(Tokens.INK_3, a)
                        : Tokens.alpha(lineColor(lineIndex), a);
                if (edge.inferred) {
                    dashed(c, offsetBuffer, points, w, color);
                    continue;
                }
                c.polyline(offsetBuffer, points, w, color);
                if (view.animate() && tier == RailMap.Line.TIER_RUNNING) {
                    drawFlow(c, offsetBuffer, points, lineIndex, color, width, map, view);
                }
            }
        }
    }

    private static int tierOf(Scratch s, int lineIndex, MapView view) {
        if (!view.dimIdle() || lineIndex < 0 || lineIndex >= s.tierCount
                || lineIndex == view.selectedLine()) {
            return RailMap.Line.TIER_RUNNING;
        }
        return s.tiers[lineIndex];
    }

    private static float tierAlpha(int tier) {
        return switch (tier) {
            case RailMap.Line.TIER_IDLE -> 0.36f;
            case RailMap.Line.TIER_DORMANT -> 0.15f;
            default -> 1.0f;
        };
    }

    private static float tierWidth(int tier) {
        return switch (tier) {
            case RailMap.Line.TIER_IDLE -> 0.72f;
            case RailMap.Line.TIER_DORMANT -> 0.40f;
            default -> 1.0f;
        };
    }

    private static void drawFlow(PanelSurface c, float[] path, int points, int lineIndex, int color,
                                 float width, RailMap map, MapView view) {
        if (lineIndex < 0 || lineIndex >= map.lines().size()) {
            return;
        }
        final RailMap.Line line = map.lines().get(lineIndex);
        final int active = line.activeTrains(map.snapshot());
        if (active == 0) {
            return;
        }
        float length = 0.0f;
        for (int i = 0; i < points - 1; i++) {
            final float dx = path[(i + 1) * 2] - path[i * 2];
            final float dy = path[(i + 1) * 2 + 1] - path[i * 2 + 1];
            length += (float) Math.sqrt(dx * dx + dy * dy);
        }
        if (length < 42.0f) {
            return;
        }
        final int beads = Math.max(1, Math.min(5, (int) (length / 90.0f)));
        final float radius = Math.max(1.1f, width * 0.34f);

        final float phase = view.clock() * (0.18f + Math.min(0.5f, active * 0.09f))
                + lineIndex * 0.37f;

        for (int i = 0; i < beads; i++) {
            final float t = ((phase + i / (float) beads) % 1.0f);
            final float[] at = MapLayout.along(path, points, t);

            final float edgeFade = Math.min(1.0f, Math.min(t, 1.0f - t) * 7.0f);
            if (edgeFade <= 0.02f) {
                continue;
            }
            Shapes.circle(c, at[0], at[1], radius + 1.0f,
                    Tokens.alpha(Tokens.CANVAS, 0.5f * edgeFade));
            Shapes.circle(c, at[0], at[1], radius, Tokens.alpha(color, 0.95f * edgeFade));
        }
    }

    private static void dashed(PanelSurface c, float[] path, int points, float width, int color) {
        final float dash = Math.max(5.0f, width * 3.0f);
        drawDashPattern(c, path, points, width, color, dash, true);
    }

    private static void drawDashPattern(PanelSurface c, float[] path, int points, float width,
                                        int color, float dash, boolean onFirst) {
        float carried = 0.0f;
        boolean on = onFirst;
        for (int i = 0; i < points - 1; i++) {
            float x0 = path[i * 2];
            float y0 = path[i * 2 + 1];
            final float x1 = path[(i + 1) * 2];
            final float y1 = path[(i + 1) * 2 + 1];
            final float dx = x1 - x0;
            final float dy = y1 - y0;
            final float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-3f) {
                continue;
            }
            final float ux = dx / len;
            final float uy = dy / len;
            float travelled = 0.0f;
            while (travelled < len) {
                final float remaining = dash - carried;
                final float run = Math.min(remaining, len - travelled);
                if (on) {
                    Shapes.line(c, x0 + ux * travelled, y0 + uy * travelled,
                            x0 + ux * (travelled + run), y0 + uy * (travelled + run), width, color);
                }
                travelled += run;
                carried += run;
                if (carried >= dash - 1.0e-4f) {
                    carried = 0.0f;
                    on = !on;
                }
            }
        }
    }

    private static void drawNodes(PanelSurface c, Rect plot, RailMap map, MapLayout layout,
                                  MapView view, Scratch s, float detail) {
        final float blend = view.morph();

        for (int i = 0; i < map.nodes().size(); i++) {
            final float presence = layout.presence(i, view.previousMode(), view.mode(), blend);
            if (presence < 0.02f) {
                continue;
            }
            final RailMap.Node node = map.nodes().get(i);
            final float x = view.screenX(layout.x(i, view.previousMode(), view.mode(), blend));
            final float y = view.screenY(layout.y(i, view.previousMode(), view.mode(), blend));
            if (!inside(plot, x, y, 40.0f)) {
                continue;
            }
            final float alpha = presence * nodeAlpha(view, node);
            if (alpha < 0.02f) {
                continue;
            }

            final boolean selected = i == view.selectedNode();
            final boolean hovered = i == view.hoverNode();
            final boolean matched = view.matchesSearch(node.name);
            final UUID present = node.presentTrain();
            final boolean grabbed = i == view.grabbedNode();

            if (grabbed) {
                Shapes.circle(c, x, y, INTERCHANGE_R + 8.0f, Tokens.alpha(Tokens.ACCENT, 0.16f));
                Shapes.arc(c, x, y, INTERCHANGE_R + 6.0f, 1.2f, 0.0f, TAU,
                        Tokens.alpha(Tokens.ACCENT, 0.7f));
            }

            if (matched) {
                final float pulse = 0.5f + 0.5f * (float) Math.sin(view.clock() * 4.0);
                Shapes.circle(c, x, y, INTERCHANGE_R + 5.0f + pulse * 2.5f,
                        Tokens.alpha(Tokens.ACCENT, 0.30f * alpha));
            }
            if (selected) {
                Shapes.circle(c, x, y, INTERCHANGE_R + 4.0f, Tokens.alpha(Tokens.ACCENT, 0.34f * alpha));
            } else if (hovered) {
                Shapes.circle(c, x, y, INTERCHANGE_R + 3.0f, Tokens.alpha(Tokens.INK, 0.16f * alpha));
            }

            if (node.isGroup()) {
                drawComplex(c, node, x, y, alpha, detail);
            } else if (node.isInterchange()) {
                drawInterchange(c, node, x, y, alpha);
            } else if (node.terminus) {
                drawTerminus(c, node, x, y, alpha);
            } else {
                drawStop(c, node, x, y, alpha, detail);
            }

            if (present != null) {
                Shapes.circle(c, x, y, 2.0f, Tokens.alpha(Tokens.GOOD, alpha));
            } else if (node.imminentTrain() != null) {
                final float pulse = 0.45f + 0.55f * (float) Math.sin(view.clock() * 5.0);
                Shapes.circle(c, x, y, 1.9f, Tokens.alpha(Tokens.CAUTION, alpha * pulse));
            }
            if (node.pinned && MapLayout.arrangeable(view.mode()) && detail > 0.5f) {
                Shapes.circle(c, x + INTERCHANGE_R + 1.0f, y - INTERCHANGE_R - 1.0f, 2.6f,
                        Tokens.alpha(Tokens.CANVAS, 0.9f * alpha));
                Shapes.circle(c, x + INTERCHANGE_R + 1.0f, y - INTERCHANGE_R - 1.0f, 1.7f,
                        Tokens.alpha(Tokens.ACCENT, 0.9f * alpha));
            }
            if (node.assembling() && detail > 0.6f) {
                Shapes.arc(c, x, y, INTERCHANGE_R + 2.4f, 1.4f, -TAU * 0.31f, TAU * 0.12f,
                        Tokens.alpha(Tokens.CAUTION, 0.85f * alpha));
            }
        }
    }

    private static void drawComplex(PanelSurface c, RailMap.Node node, float x, float y,
                                    float alpha, float detail) {
        final int platforms = node.members.size();
        final float w = Math.min(30.0f, 12.0f + platforms * 2.4f);
        final float h = 11.0f;
        final Rect box = new Rect(x - w * 0.5f, y - h * 0.5f, w, h);

        Shapes.roundedRect(c, box.inset(-1.6f), h * 0.5f + 1.6f, Tokens.alpha(Tokens.CANVAS, 0.92f * alpha));
        Shapes.roundedRect(c, box, h * 0.5f, Tokens.alpha(Tokens.INK, 0.95f * alpha));
        Shapes.roundedRect(c, box.inset(1.5f), (h - 3.0f) * 0.5f,
                Tokens.alpha(Tokens.CANVAS, 0.96f * alpha));

        if (detail < 0.45f) {
            return;
        }

        final int shown = Math.min(6, platforms);
        final int occupied = node.occupiedPlatforms();
        final float pipGap = 3.4f;
        final float startX = x - (shown - 1) * pipGap * 0.5f;
        for (int i = 0; i < shown; i++) {
            final boolean busy = i < occupied;
            Shapes.circle(c, startX + i * pipGap, y, 1.35f,
                    Tokens.alpha(busy ? Tokens.GOOD : Tokens.INK_3, (busy ? 0.95f : 0.55f) * alpha));
        }
    }

    private static void drawInterchange(PanelSurface c, RailMap.Node node, float x, float y,
                                        float alpha) {
        Shapes.circle(c, x, y, INTERCHANGE_R + 1.6f, Tokens.alpha(Tokens.CANVAS, 0.94f * alpha));

        final int count = node.lines.size();
        final float sweep = TAU / count;

        final float gap = Math.min(0.20f, sweep * 0.10f);
        for (int i = 0; i < count; i++) {
            Shapes.arc(c, x, y, INTERCHANGE_R, 2.6f, i * sweep + gap * 0.5f, sweep - gap,
                    Tokens.alpha(lineColor(node.lines.get(i)), 0.95f * alpha));
        }
        Shapes.circle(c, x, y, INTERCHANGE_R - 2.2f, Tokens.alpha(Tokens.INK, 0.92f * alpha));
        Shapes.circle(c, x, y, INTERCHANGE_R - 3.4f, Tokens.alpha(Tokens.CANVAS, 0.9f * alpha));
    }

    private static void drawTerminus(PanelSurface c, RailMap.Node node, float x, float y,
                                     float alpha) {
        final int color = node.lines.isEmpty() ? Tokens.INK_2 : lineColor(node.lines.get(0));
        Shapes.circle(c, x, y, NODE_R + 2.4f, Tokens.alpha(Tokens.CANVAS, 0.94f * alpha));
        Shapes.circle(c, x, y, NODE_R + 1.2f, Tokens.alpha(color, 0.95f * alpha));
        Shapes.circle(c, x, y, NODE_R - 0.6f, Tokens.alpha(Tokens.CANVAS, 0.92f * alpha));
        Shapes.rect(c, x - NODE_R - 2.6f, y - 0.9f, (NODE_R + 2.6f) * 2.0f, 1.8f,
                Tokens.alpha(color, 0.95f * alpha));
    }

    private static void drawStop(PanelSurface c, RailMap.Node node, float x, float y, float alpha,
                                 float detail) {
        final int color = node.lines.isEmpty() ? Tokens.INK_3 : lineColor(node.lines.get(0));
        if (detail < 0.35f) {
            Shapes.circle(c, x, y, 2.2f, Tokens.alpha(color, 0.9f * alpha));
            return;
        }
        Shapes.circle(c, x, y, NODE_R + 1.4f, Tokens.alpha(Tokens.CANVAS, 0.92f * alpha));
        Shapes.circle(c, x, y, NODE_R, Tokens.alpha(color, 0.95f * alpha));
        Shapes.circle(c, x, y, NODE_R - 1.7f, Tokens.alpha(Tokens.CANVAS, 0.94f * alpha));
    }

    private static void drawTrains(PanelSurface c, Rect plot, RailMap map, MapView view,
                                   Scratch s, float detail) {
        final RailSnapshot snap = map.snapshot();

        for (final RailSnapshot.TrainState t : snap.trains()) {
            final MapView.Motion m = view.motion(t.id);
            if (m == null) {
                continue;
            }
            final float x = view.screenX(m.x);
            final float y = view.screenY(m.y);
            if (!inside(plot, x, y, 30.0f)) {
                continue;
            }
            final int lineIndex = map.lineIndexOf(t.id);
            final float alpha = lineIndex < 0 ? 1.0f : lineAlpha(view, lineIndex);
            if (alpha < 0.05f) {
                continue;
            }
            final int color = trainColor(t, lineIndex);
            final boolean selected = t.id.equals(view.selectedTrain());

            if (view.animate() && t.moving()) {
                drawTrail(c, view, m, color, alpha);
            }
            if (t.unhealthy()) {
                final float pulse = 0.4f + 0.6f * (float) Math.sin(view.clock() * 4.5);
                Shapes.circle(c, x, y, 11.0f, Tokens.alpha(Tokens.CRITICAL, 0.3f * pulse * alpha));
            }
            if (t.id.equals(view.followTrain())) {
                drawFollowReticle(c, x, y, view, alpha);
            } else if (selected) {
                Shapes.circle(c, x, y, 10.0f, Tokens.alpha(color, 0.32f * alpha));
            }

            Shapes.circle(c, x, y, 5.6f, Tokens.alpha(Tokens.CANVAS, 0.95f * alpha));
            if (t.moving() && detail > 0.4f) {
                drawHeading(c, x, y, m.headingX, m.headingY, color, alpha);
            } else {
                Shapes.circle(c, x, y, 3.6f, Tokens.alpha(color, 0.98f * alpha));
            }
            if (!t.positionValid) {
                Shapes.circle(c, x, y, 1.7f, Tokens.alpha(Tokens.CANVAS, 0.95f * alpha));
            }
            if (t.waitingForSignal) {
                Shapes.arc(c, x, y, 7.4f, 1.5f, 0.0f, TAU,
                        Tokens.alpha(Tokens.CAUTION, 0.8f * alpha));
            }
            s.trains.add(new TrainMark(t.id, x, y, color, selected));
        }
    }

    private static void drawHeading(PanelSurface c, float x, float y, float hx, float hy, int color,
                                    float alpha) {
        final float len = (float) Math.sqrt(hx * hx + hy * hy);
        if (len < 1.0e-3f) {
            Shapes.circle(c, x, y, 3.6f, Tokens.alpha(color, 0.98f * alpha));
            return;
        }
        final float ux = hx / len;
        final float uy = hy / len;
        final float px = -uy;
        final float py = ux;
        final float nose = 5.2f;
        final float tail = 3.0f;
        final float half = 3.1f;
        final int fill = Tokens.alpha(color, 0.98f * alpha);
        c.tri(x + ux * nose, y + uy * nose, fill,
                x - ux * tail + px * half, y - uy * tail + py * half, fill,
                x - ux * tail - px * half, y - uy * tail - py * half, fill);
    }

    private static void drawTrail(PanelSurface c, MapView view, MapView.Motion m, int color,
                                  float alpha) {
        if (m.trailCount < 2) {
            return;
        }
        for (int i = 1; i < m.trailCount; i++) {
            final float t = i / (float) (m.trailCount - 1);
            final float x0 = view.screenX(m.trailX(i - 1));
            final float y0 = view.screenY(m.trailY(i - 1));
            final float x1 = view.screenX(m.trailX(i));
            final float y1 = view.screenY(m.trailY(i));
            final float dx = x1 - x0;
            final float dy = y1 - y0;

            if (dx * dx + dy * dy > 40000.0f) {
                continue;
            }
            Shapes.line(c, x0, y0, x1, y1, 1.0f + t * 2.2f,
                    Tokens.alpha(color, 0.30f * t * t * alpha));
        }
    }

    private static void drawFollowReticle(PanelSurface c, float x, float y, MapView view,
                                          float alpha) {
        final float spin = view.clock() * 0.8f;
        final int color = Tokens.alpha(Tokens.ACCENT, 0.85f * alpha);
        for (int i = 0; i < 4; i++) {
            Shapes.arc(c, x, y, 11.0f, 1.6f, spin + i * (TAU * 0.25f), TAU * 0.10f, color);
        }
        Shapes.circle(c, x, y, 13.5f, Tokens.alpha(Tokens.ACCENT, 0.10f * alpha));
    }

    private static void drawStalls(PanelSurface c, Rect plot, RailMap map, MapView view) {
        final RailSnapshot snap = map.snapshot();
        boolean anyHeld = false;
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if (t.waitingForSignal
                    && t.ticksWaitingForSignal >= TrainIntel.SIGNAL_HOLD_ALERT_TICKS) {
                anyHeld = true;
                break;
            }
        }
        if (!anyHeld) {
            return;
        }
        final List<TrainIntel.Stall> stalls = TrainIntel.stalls(snap);
        if (stalls.isEmpty()) {
            return;
        }
        WaitForArt.draw(c, plot, stalls, id -> {
            final MapView.Motion m = view.motion(id);
            return m == null ? null : new float[]{view.screenX(m.x), view.screenY(m.y)};
        }, view.clock(), 1.25f, view.showLabels());
    }

    private static void drawLabels(PanelSurface c, Rect plot, RailMap map, MapLayout layout,
                                   MapView view, Scratch s, float detail) {
        if (detail < 0.28f) {
            return;
        }
        final float blend = view.morph();
        final Integer[] order = labelOrder(map, view);

        for (final int i : order) {
            final RailMap.Node node = map.nodes().get(i);
            final float presence = layout.presence(i, view.previousMode(), view.mode(), blend);
            if (presence < 0.5f) {
                continue;
            }
            final float alpha = presence * nodeAlpha(view, node);
            if (alpha < 0.08f) {
                continue;
            }
            final boolean important = node.isInterchange() || node.terminus || node.isGroup()
                    || i == view.selectedNode() || view.matchesSearch(node.name);

            if (!important && detail < 0.62f) {
                continue;
            }

            final float x = view.screenX(layout.x(i, view.previousMode(), view.mode(), blend));
            final float y = view.screenY(layout.y(i, view.previousMode(), view.mode(), blend));
            if (!inside(plot, x, y, 20.0f)) {
                continue;
            }

            final float scale = Text.MICRO;
            final String name = Text.truncate(c, node.name, 118.0f, scale);
            final float w = c.textWidth(name, scale);
            final float h = 9.0f;
            final float pad = node.isGroup() ? 18.0f : node.isInterchange() ? 11.0f : 8.0f;

            final Rect[] candidates = {
                    new Rect(x + pad, y - h * 0.5f, w, h),
                    new Rect(x - pad - w, y - h * 0.5f, w, h),
                    new Rect(x - w * 0.5f, y + pad * 0.8f, w, h),
                    new Rect(x - w * 0.5f, y - pad * 0.8f - h, w, h)};

            Rect placed = null;
            for (final Rect candidate : candidates) {
                if (candidate.x < plot.x || candidate.right() > plot.right()
                        || candidate.y < plot.y || candidate.bottom() > plot.bottom()) {
                    continue;
                }
                if (!collides(s.labels, candidate)) {
                    placed = candidate;
                    break;
                }
            }
            if (placed == null) {
                continue;
            }
            s.labels.add(placed.inset(-2.0f));

            final int color = view.matchesSearch(node.name) ? Tokens.ACCENT
                    : important ? Tokens.INK : Tokens.INK_2;

            final boolean group = node.isGroup() && detail > 0.75f;
            final Rect plate = group
                    ? new Rect(placed.x, placed.y, w, h + 9.0f) : placed;
            c.roundedRect(plate.inset(-3.0f, -2.0f, -3.0f, -2.0f), 3.0f,
                    Tokens.alpha(Tokens.CANVAS, 0.82f * alpha));
            c.text(name, placed.x, placed.y, Tokens.alpha(color, alpha), scale);

            if (group) {
                c.text(node.members.size() + " platforms", placed.x, placed.y + 9.0f,
                        Tokens.alpha(Tokens.INK_3, 0.9f * alpha), scale);
                s.labels.add(new Rect(placed.x, placed.y + 9.0f, w, h).inset(-2.0f));
            }
        }

        if (detail > 0.85f) {
            drawTrainLabels(c, plot, map, view, s);
        }
    }

    private static void drawTrainLabels(PanelSurface c, Rect plot, RailMap map, MapView view,
                                        Scratch s) {
        for (final TrainMark mark : s.trains) {
            final RailSnapshot.TrainState t = map.snapshot().train(mark.id());
            if (t == null) {
                continue;
            }
            if (!mark.selected() && !t.unhealthy() && !t.id.equals(view.hoverTrain())) {
                continue;
            }
            final String name = Text.truncate(c, t.name, 96.0f, Text.MICRO);
            final float w = c.textWidth(name, Text.MICRO);
            final Rect box = new Rect(mark.x() - w * 0.5f, mark.y() - 18.0f, w, 9.0f);
            if (collides(s.labels, box) || box.x < plot.x || box.right() > plot.right()) {
                continue;
            }
            s.labels.add(box.inset(-2.0f));
            c.roundedRect(box.inset(-3.0f, -2.0f, -3.0f, -2.0f), 3.0f,
                    Tokens.alpha(Tokens.CANVAS, 0.82f));
            c.text(name, box.x, box.y, mark.color(), Text.MICRO);
        }
    }

    private static Integer[] labelOrder(RailMap map, MapView view) {
        final int n = map.nodes().size();
        final Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(
                labelRank(map.nodes().get(b), b, view), labelRank(map.nodes().get(a), a, view)));
        return order;
    }

    private static int labelRank(RailMap.Node node, int index, MapView view) {
        int rank = node.lines.size() * 4 + node.members.size();
        if (view.matchesSearch(node.name)) {
            rank += 1000;
        }
        if (index == view.selectedNode()) {
            rank += 500;
        }
        if (node.terminus) {
            rank += 40;
        }
        if (node.isGroup()) {
            rank += 20;
        }
        return rank;
    }

    private static boolean collides(List<Rect> placed, Rect candidate) {
        for (final Rect r : placed) {
            if (candidate.x < r.right() && candidate.right() > r.x
                    && candidate.y < r.bottom() && candidate.bottom() > r.y) {
                return true;
            }
        }
        return false;
    }

    private static void drawScaleBar(PanelSurface c, Rect plot, MapView view) {
        final float scale = view.scale();
        if (scale <= 0.0f) {
            return;
        }
        final float truth = view.geographicWeight();
        if (truth < 0.35f) {
            c.text("not to scale", plot.x + 8.0f, plot.bottom() - 14.0f,
                    Tokens.alpha(Tokens.INK_3, 0.8f), Text.MICRO);
            return;
        }
        final double blocks = niceStep(84.0 / scale);
        final float width = (float) (blocks * scale);
        if (width < 12.0f || width > plot.w * 0.55f) {
            return;
        }
        final float y = plot.bottom() - 12.0f;
        final int color = Tokens.alpha(Tokens.INK_3, truth);
        Shapes.rect(c, plot.x + 8.0f, y, width, 1.5f, color);
        Shapes.rect(c, plot.x + 8.0f, y - 3.0f, 1.0f, 7.0f, color);
        Shapes.rect(c, plot.x + 8.0f + width, y - 3.0f, 1.0f, 7.0f, color);
        c.text(TrainIntel.distance(blocks), plot.x + 12.0f + width, y - 6.0f, color, Text.MICRO);
    }

    private static void drawCompass(PanelSurface c, Rect plot, MapView view) {
        final float cx = plot.right() - 22.0f;
        final float cy = plot.y + 22.0f;
        final float r = 11.0f;
        final float alpha = 0.5f + 0.5f * view.geographicWeight();

        Shapes.circle(c, cx, cy, r + 2.0f, Tokens.alpha(Tokens.CANVAS, 0.55f * alpha));
        Shapes.arc(c, cx, cy, r, 1.0f, 0.0f, TAU, Tokens.alpha(Tokens.LINE_HI, alpha));
        final int north = Tokens.alpha(Tokens.ACCENT, 0.95f * alpha);
        c.tri(cx, cy - r + 1.0f, north, cx - 3.4f, cy + 2.4f, north, cx + 3.4f, cy + 2.4f, north);
        final int south = Tokens.alpha(Tokens.INK_3, 0.8f * alpha);
        c.tri(cx, cy + r - 1.0f, south, cx - 3.4f, cy + 2.4f, south, cx + 3.4f, cy + 2.4f, south);
        c.textCenter("N", cx, cy - r - 9.0f, Tokens.alpha(Tokens.INK_2, alpha), Text.MICRO);
    }

    public static final float LEGEND_ROW_H = 26.0f;

    public static int[] legendOrder(RailMap map, MapView view) {
        final RailSnapshot snap = map.snapshot();
        final int n = map.lines().size();
        final Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        final int[] tier = new int[n];
        for (int i = 0; i < n; i++) {
            tier[i] = i == view.selectedLine() ? -1 : map.lines().get(i).tier(snap);
        }
        java.util.Arrays.sort(boxed, (a, b) -> {
            final int byTier = Integer.compare(tier[a], tier[b]);
            if (byTier != 0) {
                return byTier;
            }

            final int byTrains = Integer.compare(map.lines().get(b).trains.size(),
                    map.lines().get(a).trains.size());
            return byTrains != 0 ? byTrains : Integer.compare(a, b);
        });
        final int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = boxed[i];
        }
        return order;
    }

    public static int[] drawLegend(PanelSurface c, Rect area, RailMap map, MapView view,
                                   List<Rect> hits) {
        return drawLegend(c, area, map, view, hits, LEGEND_ROW_H, Text.MICRO,
                legendOrder(map, view));
    }

    public static int[] drawLegend(PanelSurface c, Rect area, RailMap map, MapView view,
                                   List<Rect> hits, float rowH, float scale, int[] order) {
        hits.clear();
        if (area.isEmpty()) {
            return new int[0];
        }
        final RailSnapshot snap = map.snapshot();
        final int[] drawn = new int[order.length];
        int rows = 0;
        float y = area.y;
        for (final int i : order) {
            if (y + rowH > area.bottom()) {
                break;
            }
            drawn[rows++] = i;
            final RailMap.Line line = map.lines().get(i);
            final Rect row = new Rect(area.x, y, area.w, rowH - 2.0f);
            hits.add(row);

            final boolean focused = i == view.selectedLine();
            if (focused) {
                c.roundedRect(row, Tokens.R_CHIP, Tokens.RAISED);
            }
            final int color = lineColor(i);
            final float swatchY = row.centerY();

            final int tier = view.dimIdle() && i != view.selectedLine()
                    ? line.tier(snap) : RailMap.Line.TIER_RUNNING;
            final float swatchH = tier == RailMap.Line.TIER_DORMANT ? 1.6f : 3.2f;
            Shapes.rect(c, row.x + 6.0f, swatchY - swatchH * 0.5f, 16.0f, swatchH,
                    tier == RailMap.Line.TIER_DORMANT ? Tokens.alpha(Tokens.INK_3, 0.7f)
                            : Tokens.alpha(color, tier == RailMap.Line.TIER_IDLE ? 0.6f : 1.0f));

            final String label = Text.truncate(c, line.label(map), row.w - 66.0f, scale);
            c.text(label, row.x + 28.0f, row.y + 3.0f,
                    focused ? Tokens.INK : Tokens.INK_2, scale);

            final int active = line.activeTrains(snap);
            final String detail = line.nodes.size() + " stops"
                    + (line.loop ? " . circular" : "");
            c.text(Text.truncate(c, detail, row.w - 66.0f, scale), row.x + 28.0f,
                    row.y + 3.0f + Text.lineHeight(scale), Tokens.INK_3, scale);

            final String count = active + "/" + line.trains.size();
            c.textRightV(count, row.right() - 8.0f, row.centerY(),
                    active > 0 ? Tokens.GOOD : Tokens.INK_3, scale);
            y += rowH;
        }
        if (order.length == 0) {
            c.textCenterV(map.lines().isEmpty() ? "No scheduled services" : "No match",
                    area.centerX(), area.y + 20.0f, Tokens.INK_3, scale);
        }
        return java.util.Arrays.copyOf(drawn, rows);
    }

    public static void drawRouteStrip(PanelSurface c, Rect area, RailMap map,
                                      RailSnapshot.TrainState train) {
        if (area.isEmpty() || train == null) {
            return;
        }
        final List<String> stops = train.stops;
        if (stops.isEmpty()) {
            c.textCenterV("No schedule on this train", area.centerX(), area.centerY(),
                    Tokens.INK_3, Text.MICRO);
            return;
        }

        final int lineIndex = map.lineIndexOf(train.id);
        final int color = trainColor(train, lineIndex);
        final float railY = area.y + 26.0f;
        final float left = area.x + 16.0f;
        final float right = area.right() - 16.0f;
        if (right - left < 40.0f) {
            return;
        }

        final int count = stops.size();
        final int target = Math.floorMod(train.currentEntry, count);
        final int previous = Math.floorMod(target - 1, count);
        final float legFraction = train.docked() ? 0.0f : train.legProgress();

        final float step = count > 1 ? (right - left) / (count - 1) : 0.0f;

        c.text(Text.track("Route"), area.x + 16.0f, area.y + 4.0f, Tokens.INK_3, Text.MICRO);
        c.textRight(Text.truncate(c, train.name, area.w * 0.4f, Text.MICRO), area.right() - 16.0f,
                area.y + 4.0f, Tokens.alpha(color, 0.9f), Text.MICRO);

        Shapes.rect(c, left, railY - 1.5f, right - left, 3.0f, Tokens.alpha(Tokens.LINE_HI, 0.8f));

        final boolean wrapping = target != previous + 1;
        final float trainX = count > 1
                ? left + (previous + (wrapping ? 0.0f : legFraction)) * step
                : left;
        if (count > 1) {
            Shapes.rect(c, left, railY - 1.5f, Math.max(0.0f, trainX - left), 3.0f,
                    Tokens.alpha(color, 0.45f));
        }

        for (int i = 0; i < count; i++) {
            final float x = count > 1 ? left + i * step : (left + right) * 0.5f;

            final boolean ahead = i >= target;
            final boolean isNext = i == target;
            final float alpha = ahead ? 1.0f : 0.42f;

            Shapes.circle(c, x, railY, isNext ? 6.0f : 4.4f, Tokens.alpha(Tokens.CANVAS, 0.95f));
            Shapes.circle(c, x, railY, isNext ? 4.6f : 3.2f, Tokens.alpha(color, alpha));
            if (!isNext) {
                Shapes.circle(c, x, railY, 1.6f, Tokens.alpha(Tokens.CANVAS, 0.9f));
            }

            final String name = Text.truncate(c, stops.get(i), step * 1.85f, Text.MICRO);
            final float w = c.textWidth(name, Text.MICRO);
            final float ty = (i % 2 == 0) ? railY + 10.0f : railY - 19.0f;
            c.text(name, Math.max(area.x + 2.0f, Math.min(x - w * 0.5f, area.right() - w - 2.0f)),
                    ty, Tokens.alpha(isNext ? Tokens.INK : Tokens.INK_2, alpha), Text.MICRO);

            if (isNext) {
                final String eta = train.docked() ? "here"
                        : TrainIntel.distance(train.distanceToDestination);
                final float ew = c.textWidth(eta, Text.MICRO);
                c.text(eta, Math.max(area.x + 2.0f,
                                Math.min(x - ew * 0.5f, area.right() - ew - 2.0f)),
                        ty + ((i % 2 == 0) ? 10.0f : -10.0f), color, Text.MICRO);
            }
        }

        if (!train.docked()) {
            final int fill = Tokens.alpha(color, 0.98f);
            c.tri(trainX + 5.0f, railY, fill, trainX - 3.0f, railY - 4.6f, fill,
                    trainX - 3.0f, railY + 4.6f, fill);
        }
    }

    private static final int[] LINE_SERIES = {
            0xFF3B82F6,
            0xFFF0417A,
            0xFFBC8309,
            0xFF0FA97A,
            0xFF8B5CF6,
            0xFFE4622A,
            0xFF22B8CF,
            0xFFD64FD0,
            0xFF8AA828,
            0xFF4FAE45,
            0xFF9C5A3C,
            0xFF6E7BA8
    };

    public static int lineColor(int lineIndex) {
        if (lineIndex < 0) {
            return Tokens.INK_2;
        }
        return LINE_SERIES[lineIndex % LINE_SERIES.length];
    }

    public static int trainColor(RailSnapshot.TrainState t, int lineIndex) {
        if (t.unhealthy()) {
            return Tokens.CRITICAL;
        }
        if (t.waitingForSignal) {
            return Tokens.CAUTION;
        }
        if (t.docked()) {
            return Tokens.INK_2;
        }
        return lineIndex < 0 ? Tokens.ACCENT : lineColor(lineIndex);
    }

    private static float lineAlpha(MapView view, int lineIndex) {
        final float focus = view.focus();
        if (focus < 0.01f || view.selectedLine() < 0) {
            return 1.0f;
        }
        return lineIndex == view.selectedLine() ? 1.0f : 1.0f - focus * 0.82f;
    }

    private static float nodeAlpha(MapView view, RailMap.Node node) {
        final float focus = view.focus();
        if (focus < 0.01f || view.selectedLine() < 0) {
            return 1.0f;
        }
        return node.lines.contains(view.selectedLine()) ? 1.0f : 1.0f - focus * 0.72f;
    }

    private static float detailScale(Rect plot, RailMap map, MapLayout layout, MapView view) {
        final float blend = view.morph();
        int visible = 0;
        for (int i = 0; i < map.nodes().size(); i++) {
            if (layout.presence(i, view.previousMode(), view.mode(), blend) < 0.5f) {
                continue;
            }
            final float x = view.screenX(layout.x(i, view.previousMode(), view.mode(), blend));
            final float y = view.screenY(layout.y(i, view.previousMode(), view.mode(), blend));
            if (inside(plot, x, y, 20.0f)) {
                visible++;
            }
        }
        if (visible == 0) {
            return 1.0f;
        }
        final float perNode = (float) Math.sqrt(plot.w * plot.h / (float) visible);
        if (perNode <= LOD_LABELS) {
            return Math.max(0.0f, perNode / LOD_LABELS * 0.34f);
        }
        if (perNode >= LOD_DETAIL) {
            return 1.0f;
        }
        return 0.34f + (perNode - LOD_LABELS) / (LOD_DETAIL - LOD_LABELS) * 0.66f;
    }

    private static float strokeWidth(float detail) {
        return LINE_W * (0.72f + detail * 0.55f);
    }

    private static boolean inside(Rect plot, float x, float y, float slack) {
        return x >= plot.x - slack && x <= plot.right() + slack
                && y >= plot.y - slack && y <= plot.bottom() + slack;
    }

    private static float[] ensure(float[][] holder, int size) {
        if (holder[0] == null || holder[0].length < size) {
            holder[0] = new float[Math.max(size, 32)];
        }
        return holder[0];
    }

    public static void drawNodeTooltip(PanelSurface c, Rect plot, RailMap map, RailMap.Node node,
                                       double mx, double my) {
        final List<String> body = new ArrayList<>();
        if (node.isGroup()) {
            body.add(node.members.size() + " platforms . " + node.occupiedPlatforms() + " occupied");
        }
        final RailSnapshot snap = map.snapshot();
        int shown = 0;
        for (final RailSnapshot.Station station : node.members) {
            for (final TrainIntel.BoardRow row : TrainIntel.board(snap, station, 3)) {
                if (shown >= 4) {
                    break;
                }
                body.add(pad(row.eta(), 7) + row.train);
                shown++;
            }
            if (shown >= 4) {
                break;
            }
        }
        if (shown == 0) {
            body.add("nothing scheduled to call here");
        }
        if (node.assembling()) {
            body.add("assembly platform");
        }
        if (node.packagePorts() > 0) {
            body.add(node.packagePorts() + " package ports");
        }
        final int accent = node.lines.isEmpty() ? Tokens.INK_2 : lineColor(node.lines.get(0));
        tooltip(c, plot, mx, my, node.name, body, accent);
    }

    public static void drawTrainTooltip(PanelSurface c, Rect plot, RailMap map,
                                        RailSnapshot.TrainState t, double mx, double my) {
        final List<String> body = new ArrayList<>();
        body.add(TrainIntel.status(t));
        body.add(String.format(Locale.ROOT, "%.1f m/s . %d carriages", t.metresPerSecond(),
                t.carriages));
        if (!t.destinationName.isEmpty()) {
            body.add("to " + t.destinationName + " . "
                    + TrainIntel.distance(t.distanceToDestination) + " of track left");
        }
        final String route = TrainIntel.route(t, 4);
        if (!route.isEmpty()) {
            body.add(route);
        }
        final int lineIndex = map.lineIndexOf(t.id);
        tooltip(c, plot, mx, my, t.name, body, trainColor(t, lineIndex));
    }

    private static String pad(String s, int width) {
        final StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static void tooltip(PanelSurface c, Rect plot, double mx, double my, String title,
                                List<String> body, int accent) {
        float w = c.textWidth(title, Text.META) + 22.0f;
        for (final String line : body) {
            w = Math.max(w, c.textWidth(line, Text.MICRO) + 22.0f);
        }
        w = Math.min(w, 290.0f);
        final float h = 22.0f + body.size() * 11.0f + 8.0f;

        float x = (float) mx + 14.0f;
        float y = (float) my + 12.0f;
        if (x + w > plot.right()) {
            x = (float) mx - 14.0f - w;
        }
        if (y + h > plot.bottom()) {
            y = plot.bottom() - h;
        }
        x = Math.max(plot.x, x);
        y = Math.max(plot.y, y);

        final Rect box = new Rect(x, y, w, h);
        c.shadow(box.inset(-2.0f), Tokens.R_CARD, 10.0f, 0x90000000);
        c.card(box, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);
        Shapes.roundedRect(c, new Rect(box.x + 7.0f, box.y + 8.0f, 3.0f, 10.0f), 1.5f, accent);
        c.text(Text.truncate(c, title, w - 24.0f, Text.META), box.x + 15.0f, box.y + 7.0f,
                Tokens.INK, Text.META);
        float ly = box.y + 22.0f;
        for (final String line : body) {
            c.text(Text.truncate(c, line, w - 20.0f, Text.MICRO), box.x + 11.0f, ly, Tokens.INK_3,
                    Text.MICRO);
            ly += 11.0f;
        }
    }
}
