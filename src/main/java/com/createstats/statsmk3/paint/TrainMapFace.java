package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.map.WaitForArt;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// no cursor on a wall, but showing the whole network made every yard two pixels
@OnlyIn(Dist.CLIENT)
final class TrainMapFace {
    private TrainMapFace() {
    }

    static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final RailSnapshot snap = ClientRailNetwork.get();
        final FaceType ft = FaceType.of(area);

        if (snap.isEmpty()) {
            TrainFaceArt.noData(s, area, ft, "No railway", "Waiting for the network");
            return;
        }

        final Frame frame = frame(snap, model);
        if (frame.box() == null) {
            TrainFaceArt.noData(s, area, ft, "Nothing in frame", frame.emptyHint());
            return;
        }

        float top = area.y + ft.pad;
        if (lod.showsHeader() && Math.min(area.w, area.h) >= 72.0f) {
            final TrainIntel.Summary sum = TrainIntel.summarise(snap);
            s.text(Text.truncate(s, Text.track(frame.title()), area.w * 0.6f, ft.micro),
                    area.x + ft.pad, top, Tokens.fade(Tokens.INK_2, lod.headerAlpha()), ft.micro);
            final int statusColor = sum.broken > 0 ? Tokens.CRITICAL
                    : sum.held > 0 ? Tokens.CAUTION : Tokens.GOOD;
            final String status = sum.broken > 0
                    ? sum.broken + (sum.broken == 1 ? " FAULT" : " FAULTS")
                    : sum.held > 0 ? sum.held + " HELD" : sum.moving + " RUNNING";
            s.textRight(status, area.right() - ft.pad, top,
                    Tokens.fade(statusColor, lod.headerAlpha()), ft.micro);
            top += Text.lineHeight(ft.micro) + ft.gap;
        }

        Rect plot = new Rect(area.x + ft.pad, top, area.w - ft.pad * 2.0f,
                area.bottom() - ft.pad - top);
        if (plot.h <= 8.0f || plot.w <= 8.0f) {
            return;
        }

        final double[] box = TrainFaceArt.padBox(frame.box(), 12.0);

        final float aspect = (float) ((box[2] - box[0]) / Math.max(1.0, box[3] - box[1]));
        final float mapW = Math.min(plot.w, plot.h * aspect);
        final float gutter = plot.w - mapW - ft.pad;
        final float legendMin = s.textWidth("MMMMMMMM", ft.nano) + ft.pad;
        Rect legend = Rect.EMPTY;
        if (gutter >= legendMin && plot.h >= 60.0f && lod.showsLabels()) {
            legend = new Rect(plot.right() - gutter, plot.y, gutter, plot.h);
            plot = new Rect(plot.x, plot.y, plot.w - gutter - ft.pad, plot.h);
        }

        final float margin = Math.max(4.0f, 8.0f * ft.unit);
        final TrainFaceArt.Fit fit = TrainFaceArt.fit(plot, box, margin);

        final boolean labels = model.faceFlag(Mk3Settings.FACE_LABELS) && lod.showsLabels()
                && ft.hasNano() && Math.min(plot.w, plot.h) >= 120.0f;

        s.clip(plot);
        try {
            drawTracks(s, snap, model, fit, plot, ft, lod);
            drawLinks(s, snap, fit, plot, ft, frame);
            s.advanceLayer();
            drawStations(s, snap, fit, plot, ft, labels, frame);
            drawTrains(s, snap, fit, plot, ft, labels, frame);
            drawStalls(s, snap, fit, plot, ft, model);
        } finally {
            s.unclip();
        }

        if (labels) {
            TrainFaceArt.scaleBar(s, plot.x, plot.bottom(), Math.min(plot.w * 0.4f, 70.0f * ft.unit),
                    fit.scale, ft);
        }
        if (!legend.isEmpty()) {
            drawLegend(s, legend, ft, snap, frame);
        }
    }

    private static void drawLegend(PanelSurface s, Rect r, FaceType ft, RailSnapshot snap,
                                   Frame frame) {
        final List<RailSnapshot.TrainState> inFrame = new ArrayList<>();
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if (frame.highlightTrains().isEmpty() || frame.highlightTrains().contains(t.id)) {
                inFrame.add(t);
            }
        }
        inFrame.sort(java.util.Comparator.comparingInt(
                (RailSnapshot.TrainState t) -> t.unhealthy() ? 0 : t.waitingForSignal ? 1
                        : t.moving() ? 2 : 3).thenComparing(t -> t.name));
        if (inFrame.isEmpty()) {
            return;
        }
        final float rowH = Text.lineHeight(ft.nano) * 2.0f + ft.gap;
        final TrainFaceArt.Rows plan = TrainFaceArt.rows(r.h, inFrame.size(),
                Text.lineHeight(ft.nano) + ft.gap * 0.5f, rowH);
        for (int i = 0; i < plan.count() && i < inFrame.size(); i++) {
            final RailSnapshot.TrainState t = inFrame.get(i);
            final float y = r.y + i * plan.height();
            final int color = TrainFaceArt.health(t);
            Shapes.rect(s, r.x, y + 1.0f, Math.max(1.2f, 1.6f * ft.unit),
                    Math.max(3.0f, plan.height() - ft.gap), color);
            final float left = r.x + Math.max(1.2f, 1.6f * ft.unit) + ft.gap;
            s.text(Text.truncate(s, t.name, r.right() - left, ft.nano), left, y, Tokens.INK_2,
                    ft.nano);
            if (plan.height() >= rowH * 0.9f) {
                final String note = TrainIntel.cause(snap, t);
                s.text(Text.truncate(s, note.isEmpty()
                                ? TrainIntel.speed(t.metresPerSecond()) + " "
                                        + TrainIntel.SPEED_UNIT : note,
                                r.right() - left, ft.nano), left,
                        y + Text.lineHeight(ft.nano), Tokens.fade(color, 0.85f), ft.nano);
            }
        }
    }

    private record Frame(double[] box, String title, String emptyHint, Set<UUID> route,
                         Set<UUID> highlightTrains, int accent) {
    }

    private static Frame frame(RailSnapshot snap, PanelModel model) {
        return switch (model.mapFocus) {
            case LINE -> lineFrame(snap, model);
            case TRAIN -> trainFrame(snap, model);
            case STATION -> stationFrame(snap, model);
            case NEAR -> nearFrame(snap, model);
            default -> new Frame(boxOf(snap.stations(), snap.tracks()), "Network",
                    "No stations found", Set.of(), Set.of(), Tokens.ACCENT);
        };
    }

    private static Frame lineFrame(RailSnapshot snap, PanelModel model) {
        final List<TrainIntel.Line> lines = TrainIntel.lines(snap);
        TrainIntel.Line found = null;
        for (final TrainIntel.Line line : lines) {
            if (line.label().equalsIgnoreCase(model.diagramLine)) {
                found = line;
                break;
            }
        }
        if (found == null && !lines.isEmpty() && model.diagramLine.isEmpty()) {
            found = lines.get(0);
        }
        if (found == null) {
            return new Frame(null, "Service", "No service named " + model.diagramLine, Set.of(),
                    Set.of(), Tokens.ACCENT);
        }
        final List<RailSnapshot.Station> members = new ArrayList<>();
        final Set<UUID> route = new HashSet<>();
        for (final String stop : found.stops) {
            final RailSnapshot.Station st = snap.resolveFilter(stop, "");
            if (st != null && route.add(st.id)) {
                members.add(st);
            }
        }
        return new Frame(boxOf(members, List.of()), found.label(), "That service has no stops",
                route, new HashSet<>(found.trains),
                Tokens.SERIES[found.colorIndex % Tokens.SERIES.length]);
    }

    private static Frame trainFrame(RailSnapshot snap, PanelModel model) {
        final RailSnapshot.TrainState t = TrainFocusFace.resolve(snap, model.focusTrain);
        if (t == null) {
            return new Frame(null, "Train", model.focusTrain.isEmpty()
                    ? "No train on the railway" : "No train called " + model.focusTrain,
                    Set.of(), Set.of(), Tokens.ACCENT);
        }

        double[] box = null;
        if (t.positionValid) {
            box = TrainFaceArt.grow(null, t.x, t.z);
        }
        final Set<UUID> route = new HashSet<>();
        for (final String stop : t.stops) {
            final RailSnapshot.Station st = snap.resolveFilter(stop, t.dimension);
            if (st != null) {
                route.add(st.id);
            }
        }
        final RailSnapshot.Station dest = t.destinationStation == null ? null
                : snap.station(t.destinationStation);
        if (dest != null) {
            box = TrainFaceArt.grow(box, dest.x, dest.z);
        }
        final RailSnapshot.Station from = t.currentStation == null ? null
                : snap.station(t.currentStation);
        if (from != null) {
            box = TrainFaceArt.grow(box, from.x, from.z);
        }
        if (box == null) {
            for (final UUID id : route) {
                final RailSnapshot.Station st = snap.station(id);
                if (st != null) {
                    box = TrainFaceArt.grow(box, st.x, st.z);
                }
            }
        }
        return new Frame(box, t.name, "That train has no position", route, Set.of(t.id),
                TrainGraphArt.trainColor(t, t.id));
    }

    private static Frame stationFrame(RailSnapshot snap, PanelModel model) {
        final List<RailSnapshot.Station> stations =
                TrainIntel.stationsMatching(snap, model.boardStation);
        if (stations.isEmpty()) {
            return new Frame(null, "Station", model.boardStation.isEmpty()
                    ? "No station chosen" : "No platform matches " + model.boardStation,
                    Set.of(), Set.of(), Tokens.ACCENT);
        }
        final Set<UUID> ids = new HashSet<>();
        for (final RailSnapshot.Station st : stations) {
            ids.add(st.id);
        }

        final List<RailSnapshot.Station> framed = new ArrayList<>(stations);
        for (final RailSnapshot.Link link : snap.links()) {
            if (ids.contains(link.from)) {
                addIfPresent(framed, snap.station(link.to));
            } else if (ids.contains(link.to)) {
                addIfPresent(framed, snap.station(link.from));
            }
        }
        return new Frame(boxOf(framed, List.of()),
                stations.size() == 1 ? stations.get(0).name : stations.size() + " platforms",
                "", ids, Set.of(), Tokens.ACCENT);
    }

    private static Frame nearFrame(RailSnapshot snap, PanelModel model) {
        if (!model.hasPosition()) {
            return new Frame(boxOf(snap.stations(), snap.tracks()), "Nearby",
                    "No stations found", Set.of(), Set.of(), Tokens.ACCENT);
        }
        final double r = Math.max(Mk3Settings.MIN_MAP_RADIUS, model.mapRadius);
        return new Frame(new double[]{model.faceX - r, model.faceZ - r,
                model.faceX + r, model.faceZ + r},
                Math.round(r) + " m around", "", Set.of(), Set.of(), Tokens.ACCENT);
    }

    private static void addIfPresent(List<RailSnapshot.Station> into, RailSnapshot.Station st) {
        if (st != null && !into.contains(st)) {
            into.add(st);
        }
    }

    private static double[] boxOf(List<RailSnapshot.Station> stations,
                                  List<RailSnapshot.Track> tracks) {
        double[] box = null;
        for (final RailSnapshot.Station st : stations) {
            box = TrainFaceArt.grow(box, st.x, st.z);
        }

        if (box == null) {
            for (final RailSnapshot.Track track : tracks) {
                if (track.valid()) {
                    final float[] b = track.bounds();
                    box = TrainFaceArt.grow(box, b[0], b[1]);
                    box = TrainFaceArt.grow(box, b[2], b[3]);
                }
            }
        }
        return box;
    }

    private static void drawTracks(PanelSurface s, RailSnapshot snap, PanelModel model,
                                   TrainFaceArt.Fit fit, Rect plot, FaceType ft, Lod lod) {
        if (!model.faceFlag(Mk3Settings.FACE_TRACK) || !lod.showsGrid() || snap.tracks().isEmpty()) {
            return;
        }
        TrainFaceArt.tracks(s, snap, fit, plot, Math.max(0.8f, 1.0f * ft.unit),
                Tokens.alpha(Tokens.LINE_HI, 0.55f));
    }

    private static void drawLinks(PanelSurface s, RailSnapshot snap, TrainFaceArt.Fit fit,
                                  Rect plot, FaceType ft, Frame frame) {
        final boolean focused = !frame.route().isEmpty();
        final float[] scratch = new float[512];
        for (final RailSnapshot.Link link : snap.links()) {
            final boolean onRoute = focused
                    && frame.route().contains(link.from) && frame.route().contains(link.to);

            final int color = onRoute ? Tokens.alpha(frame.accent(), 0.95f)
                    : focused ? Tokens.alpha(Tokens.INK_3, 0.28f) : Tokens.alpha(Tokens.INK_3, 0.7f);
            final float width = (onRoute ? 2.1f : 1.4f) * ft.unit;

            if (link.hasPath()) {
                final int points = Math.min(link.path.length / 2, scratch.length / 2);
                for (int i = 0; i < points; i++) {
                    scratch[i * 2] = fit.x(link.path[i * 2]);
                    scratch[i * 2 + 1] = fit.y(link.path[i * 2 + 1]);
                }
                if (points > 1) {
                    TrainFaceArt.polylineClipped(s, scratch, points, width, color,
                            plot.inset(width * 0.5f));
                }
            } else {
                final RailSnapshot.Station a = snap.station(link.from);
                final RailSnapshot.Station b = snap.station(link.to);
                if (a != null && b != null) {
                    TrainFaceArt.polylineClipped(s, new float[]{fit.x(a.x), fit.y(a.z),
                                    fit.x(b.x), fit.y(b.z)}, 2, width * 0.8f, color,
                            plot.inset(width * 0.4f));
                }
            }
        }
    }

    private static void drawStations(PanelSurface s, RailSnapshot snap, TrainFaceArt.Fit fit,
                                     Rect plot, FaceType ft, boolean labels, Frame frame) {
        final float size = Math.max(1.6f, 2.6f * ft.unit);

        final List<float[]> placed = new ArrayList<>();

        final Rect inside = plot.inset(size * 1.6f);
        for (final RailSnapshot.Station station : snap.stations()) {
            final float x = fit.x(station.x);
            final float y = fit.y(station.z);
            if (!inside.contains(x, y)) {
                continue;
            }
            final boolean inFrame = frame.route().isEmpty() || frame.route().contains(station.id);
            TrainFaceArt.node(s, x, y, inFrame ? size : size * 0.7f, station.presentTrain != null,
                    inFrame && !frame.route().isEmpty());
        }

        if (!labels) {
            return;
        }
        s.advanceLayer();
        for (final RailSnapshot.Station station : snap.stations()) {
            final float x = fit.x(station.x);
            final float y = fit.y(station.z);
            if (!inside.contains(x, y)
                    || (!frame.route().isEmpty() && !frame.route().contains(station.id))) {
                continue;
            }
            final String name = Text.truncate(s, station.name, plot.w * 0.4f, ft.nano);
            final float w = s.textWidth(name, ft.nano);

            final float lx = Math.max(plot.x + ft.gap,
                    Math.min(x + size + 2.0f, plot.right() - w - ft.gap));
            final float ly = Math.max(plot.y, Math.min(y - Text.lineHeight(ft.nano) * 0.5f,
                    plot.bottom() - Text.lineHeight(ft.nano) - ft.gap));

            if (collides(placed, lx, ly, w, Text.lineHeight(ft.nano))) {
                continue;
            }
            placed.add(new float[]{lx, ly, w, Text.lineHeight(ft.nano)});
            TrainFaceArt.plate(s, name, lx, ly,
                    station.presentTrain != null ? Tokens.INK : Tokens.INK_2, ft.nano, ft);
        }
    }

    private static void drawTrains(PanelSurface s, RailSnapshot snap, TrainFaceArt.Fit fit,
                                   Rect plot, FaceType ft, boolean labels, Frame frame) {
        final List<TrainIntel.Line> lines = TrainIntel.lines(snap);
        final float size = Math.max(1.8f, 2.9f * ft.unit);

        for (final RailSnapshot.TrainState t : snap.trains()) {
            final float[] pos = TrainIntel.positionOf(snap, t);
            if (pos == null) {
                continue;
            }
            final float x = fit.x(pos[0]);
            final float y = fit.y(pos[1]);
            if (!plot.inset(size * 2.4f).contains(x, y)) {
                continue;
            }
            final boolean starred = frame.highlightTrains().contains(t.id);
            final int color = t.unhealthy() ? Tokens.CRITICAL
                    : t.waitingForSignal ? Tokens.CAUTION
                    : starred ? frame.accent() : lineColor(lines, t);

            if (t.waitingForSignal || t.unhealthy()) {
                Shapes.circle(s, x, y, size * 2.2f, Tokens.alpha(color, 0.22f));
            }
            TrainFaceArt.marker(s, x, y, size, TrainFaceArt.heading(snap, t, fit), color,
                    t.moving());

            if (labels && (starred || frame.highlightTrains().isEmpty())) {
                final String name = Text.truncate(s, t.name, plot.w * 0.34f, ft.nano);
                final float nw = s.textWidth(name, ft.nano);
                TrainFaceArt.plate(s, name,
                        Math.max(plot.x + ft.gap,
                                Math.min(x + size + 2.0f, plot.right() - nw - ft.gap)),
                        Math.min(y + size, plot.bottom() - Text.lineHeight(ft.nano) - ft.gap),
                        color, ft.nano, ft);
            }
        }
    }

    private static int lineColor(List<TrainIntel.Line> lines, RailSnapshot.TrainState t) {
        final TrainIntel.Line line = TrainIntel.lineOf(lines, t.id);
        return line == null ? Tokens.ACCENT : Tokens.SERIES[line.colorIndex % Tokens.SERIES.length];
    }

    private static void drawStalls(PanelSurface s, RailSnapshot snap, TrainFaceArt.Fit fit,
                                   Rect plot, FaceType ft, PanelModel model) {
        if (Math.min(plot.w, plot.h) < 64.0f) {
            return;
        }
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
        final boolean words = ft.hasNano() && Math.min(plot.w, plot.h) >= 96.0f;
        WaitForArt.draw(s, plot, stalls, id -> {
            final RailSnapshot.TrainState t = snap.train(id);
            if (t == null) {
                return null;
            }
            final float[] world = TrainIntel.positionOf(snap, t);
            return world == null ? null
                    : new float[]{fit.x(world[0]), fit.y(world[1])};
        }, model.time, Math.max(0.5f, ft.unit), words);
    }

    private static boolean collides(List<float[]> placed, float x, float y, float w, float h) {
        for (final float[] r : placed) {
            if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
                return true;
            }
        }
        return false;
    }
}
