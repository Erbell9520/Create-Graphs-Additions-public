package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.ClientRailMap;
import com.createstats.train.map.LineChainage;
import com.createstats.train.map.RailMap;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class TrainRouteFace {
    private TrainRouteFace() {
    }

    static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final FaceType ft = FaceType.of(area);
        final RailMap map = ClientRailMap.get();
        if (map.isEmpty()) {
            TrainFaceArt.noData(s, area, ft, "No railway", "Waiting for the network");
            return;
        }
        final int index = ClientRailMap.lineNamed(map, model.diagramLine);
        if (index < 0) {
            TrainFaceArt.noData(s, area, ft, "No scheduled service",
                    "A route strip needs a schedule with stops on it");
            return;
        }
        final RailMap.Line service = map.lines().get(index);
        final LineChainage chain = map.chainage(index);
        if (chain.isEmpty()) {
            TrainFaceArt.noData(s, area, ft, service.label(map), "That service has one stop");
            return;
        }
        final RailSnapshot snap = ClientRailNetwork.get();
        final int color = Tokens.SERIES[service.colorIndex % Tokens.SERIES.length];

        if (lod.isGlyph()) {
            glyph(s, area, ft, snap, service, chain, color);
            return;
        }

        final TrainFaceArt.Head head = TrainFaceArt.header(s, area, ft, lod, service.label(map),
                running(snap, service) + " RUNNING",
                running(snap, service) > 0 ? Tokens.GOOD : Tokens.INK_3,
                crumb(chain, service));

        Rect body = new Rect(area.x + ft.pad, head.bottom(), area.w - ft.pad * 2.0f,
                area.bottom() - ft.pad - head.bottom());
        if (body.h < 12.0f || body.w < 24.0f) {
            return;
        }

        final boolean vertical = body.h > body.w * 1.35f;
        if (vertical) {
            drawVertical(s, body, ft, lod, snap, chain, color, model);
            return;
        }

        final float stripH = Math.min(body.h, stripHeight(ft, lod, model));
        drawHorizontal(s, new Rect(body.x, body.y, body.w, stripH), ft, lod, snap, chain, color,
                model);
        final Rect rest = new Rect(body.x, body.y + stripH + ft.gap, body.w,
                body.h - stripH - ft.gap);
        if (rest.h < Text.lineHeight(ft.micro) * 2.0f || !lod.showsLabels()) {
            return;
        }
        final List<UUID> working = trainsOn(snap, chain);
        if (working.isEmpty()) {
            TrainStatusFace.supplement(s, rest, ft, lod, snap);
            return;
        }
        final Rect roster = drawRoster(s, rest, ft, snap, chain, working);
        final Rect spare = new Rect(rest.x, roster.bottom() + ft.gap, rest.w,
                rest.bottom() - roster.bottom() - ft.gap);
        if (spare.h >= Text.lineHeight(ft.micro) * 4.0f) {
            TrainStatusFace.supplement(s, spare, ft, lod, snap);
        }
    }

    private static float stripHeight(FaceType ft, Lod lod, PanelModel model) {
        final boolean labels = lod.showsLabels() && model.faceFlag(Mk3Settings.FACE_LABELS);
        final float names = labels ? (Text.lineHeight(ft.micro) + ft.gap) * 2.0f : 0.0f;
        final float tags = labels ? Text.lineHeight(ft.nano) + ft.gap : 0.0f;
        return names + tags + Math.max(2.5f, 4.0f * ft.unit) + ft.gap * 2.0f;
    }

    private static Rect drawRoster(PanelSurface s, Rect r, FaceType ft, RailSnapshot snap,
                                   LineChainage chain, List<UUID> working) {
        final List<RailSnapshot.TrainState> trains = new ArrayList<>();
        for (final UUID id : working) {
            final RailSnapshot.TrainState t = snap.train(id);
            if (t != null) {
                trains.add(t);
            }
        }
        trains.sort(java.util.Comparator.comparingInt(
                (RailSnapshot.TrainState t) -> t.unhealthy() ? 0 : t.waitingForSignal ? 1
                        : t.moving() ? 2 : 3).thenComparing(t -> t.name));

        final float ideal = Text.lineHeight(ft.row) + ft.gap * 1.5f;
        final TrainFaceArt.Rows plan = TrainFaceArt.rows(r.h, trains.size(),
                Text.lineHeight(ft.micro) + ft.gap * 0.5f, ideal);
        for (int i = 0; i < plan.count() && i < trains.size(); i++) {
            final RailSnapshot.TrainState t = trains.get(i);
            final Rect row = new Rect(r.x, r.y + i * plan.height(), r.w, plan.height());
            final int color = TrainFaceArt.health(t);
            Shapes.rect(s, row.x, row.y + ft.gap * 0.25f, Math.max(1.5f, 2.0f * ft.unit),
                    Math.max(3.0f, row.h - ft.gap * 0.5f), color);
            final float left = row.x + Math.max(1.5f, 2.0f * ft.unit) + ft.gap;

            float tail = row.right();
            final float late = t.secondsLate();
            if (!t.manual && !Float.isNaN(late)) {
                final String tag = TrainFaceArt.lateShort(late);
                s.textRightV(tag, tail, row.centerY(), TrainFaceArt.latenessColor(late), ft.micro);
                tail -= s.textWidth(tag, ft.micro) + ft.gap * 1.5f;
            }
            if (t.moving()) {
                final String fig = TrainIntel.speed(t.metresPerSecond());
                s.textRightV(fig, tail, row.centerY(), Tokens.INK_2, ft.micro);
                tail -= s.textWidth(fig, ft.micro) + ft.gap * 1.5f;
            }
            final String cause = TrainIntel.cause(snap, t);
            final String note = cause.isEmpty()
                    ? (t.destinationName.isEmpty() ? "" : "to " + t.destinationName) : cause;
            final float nameW = Math.min(s.textWidth(t.name, ft.row) + ft.gap,
                    Math.max(0.0f, tail - left) * 0.55f);
            s.textV(Text.truncate(s, t.name, nameW, ft.row), left, row.centerY(), Tokens.INK,
                    ft.row);
            if (!note.isEmpty() && tail - left - nameW > s.textWidth("MMM", ft.micro)) {
                s.textV(Text.truncate(s, note, tail - left - nameW, ft.micro), left + nameW,
                        row.centerY(), cause.isEmpty() ? Tokens.INK_3 : color, ft.micro);
            }
        }
        return new Rect(r.x, r.y, r.w, Math.min(r.h, plan.used()));
    }

    private static void drawHorizontal(PanelSurface s, Rect body, FaceType ft, Lod lod,
                                       RailSnapshot snap, LineChainage chain, int color,
                                       PanelModel model) {
        final boolean labels = lod.showsLabels() && model.faceFlag(Mk3Settings.FACE_LABELS);

        final float nameH = labels ? Text.lineHeight(ft.micro) + ft.gap : 0.0f;
        final float trainH = labels ? Text.lineHeight(ft.nano) + ft.gap : 0.0f;
        final float railY = body.y + nameH + Math.max(0.0f, (body.h - nameH * 2.0f - trainH) * 0.5f);
        final float railH = Math.max(2.5f, 4.0f * ft.unit);

        final float x0 = body.x + Math.max(4.0f, 6.0f * ft.unit);
        final float x1 = body.right() - Math.max(4.0f, 6.0f * ft.unit);
        final float span = Math.max(1.0f, x1 - x0);
        final float total = Math.max(1.0f, chain.total);

        Shapes.roundedRect(s, new Rect(x0, railY, span, railH), railH * 0.5f,
                Tokens.alpha(color, 0.42f));

        final List<float[]> above = new ArrayList<>();
        final List<float[]> below = new ArrayList<>();
        for (int i = 0; i < chain.stopCount(); i++) {
            final RailMap.Node node = chain.node(i);
            final float x = x0 + span * (chain.chainAt(i) / total);
            final boolean terminal = i == 0 || i == chain.stopCount() - 1;
            final boolean occupied = node != null && node.presentTrain() != null;
            TrainFaceArt.node(s, x, railY + railH * 0.5f,
                    Math.max(2.0f, (terminal ? 3.4f : 2.6f) * ft.unit), occupied,
                    node != null && node.isInterchange());
            if (!labels || node == null) {
                continue;
            }
            final boolean up = i % 2 == 0;
            final String name = Text.truncate(s, node.name, span * 0.3f, ft.micro);
            final float w = s.textWidth(name, ft.micro);
            final float lx = clamp(x - w * 0.5f, body.x, body.right() - w);
            final float ly = up ? railY - nameH : railY + railH + ft.gap;
            final List<float[]> lane = up ? above : below;
            if (!terminal && hits(lane, lx, w)) {
                continue;
            }
            lane.add(new float[]{lx, w});
            s.text(name, lx, ly, occupied ? Tokens.INK : Tokens.INK_2, ft.micro);
        }

        s.advanceLayer();
        final List<float[]> taken = new ArrayList<>();
        for (final UUID id : trainsOn(snap, chain)) {
            final RailSnapshot.TrainState t = snap.train(id);
            final float metres = chain.chainageOf(t);
            if (Float.isNaN(metres)) {
                continue;
            }
            final float x = x0 + span * clamp01(metres / total);
            drawTrain(s, ft, snap, t, x, railY + railH * 0.5f, railH, labels,
                    railY + railH + ft.gap + (labels ? nameH : 0.0f), taken, body);
        }
    }

    private static void drawVertical(PanelSurface s, Rect body, FaceType ft, Lod lod,
                                     RailSnapshot snap, LineChainage chain, int color,
                                     PanelModel model) {
        final boolean labels = lod.showsLabels() && model.faceFlag(Mk3Settings.FACE_LABELS);
        final float railW = Math.max(2.5f, 4.0f * ft.unit);
        final float railX = body.x + Math.max(4.0f, 6.0f * ft.unit);
        final float y0 = body.y + Math.max(4.0f, 6.0f * ft.unit);
        final float y1 = body.bottom() - Math.max(4.0f, 6.0f * ft.unit);
        final float span = Math.max(1.0f, y1 - y0);
        final float total = Math.max(1.0f, chain.total);

        Shapes.roundedRect(s, new Rect(railX, y0, railW, span), railW * 0.5f,
                Tokens.alpha(color, 0.42f));

        final float labelX = railX + railW + ft.gap * 2.0f;
        final float labelW = body.right() - labelX;
        final List<float[]> lane = new ArrayList<>();
        for (int i = 0; i < chain.stopCount(); i++) {
            final RailMap.Node node = chain.node(i);
            final float y = y0 + span * (chain.chainAt(i) / total);
            final boolean terminal = i == 0 || i == chain.stopCount() - 1;
            final boolean occupied = node != null && node.presentTrain() != null;
            TrainFaceArt.node(s, railX + railW * 0.5f, y,
                    Math.max(2.0f, (terminal ? 3.4f : 2.6f) * ft.unit), occupied,
                    node != null && node.isInterchange());
            if (!labels || node == null || labelW < s.textWidth("MMM", ft.micro)) {
                continue;
            }
            final float ly = y - Text.lineHeight(ft.micro) * 0.5f;
            if (!terminal && hits(lane, ly, Text.lineHeight(ft.micro))) {
                continue;
            }
            lane.add(new float[]{ly, Text.lineHeight(ft.micro)});

            final String due = nextDue(snap, node);
            final float dueW = due.isEmpty() ? 0.0f : s.textWidth(due, ft.nano) + ft.gap;
            s.text(Text.truncate(s, node.name, labelW - dueW, ft.micro), labelX, ly,
                    occupied ? Tokens.INK : Tokens.INK_2, ft.micro);
            if (!due.isEmpty()) {
                s.textRight(due, body.right(), ly, occupied ? Tokens.GOOD : Tokens.INK_3, ft.nano);
            }
        }

        s.advanceLayer();
        for (final UUID id : trainsOn(snap, chain)) {
            final RailSnapshot.TrainState t = snap.train(id);
            final float metres = chain.chainageOf(t);
            if (Float.isNaN(metres)) {
                continue;
            }
            final float y = y0 + span * clamp01(metres / total);
            final int tint = TrainFaceArt.health(t);
            if (t.waitingForSignal || t.unhealthy()) {
                Shapes.circle(s, railX + railW * 0.5f, y, railW * 1.9f, Tokens.alpha(tint, 0.24f));
            }
            TrainFaceArt.marker(s, railX + railW * 0.5f, y, Math.max(2.0f, 2.9f * ft.unit),
                    (float) (Math.PI * 0.5), tint, t.moving());
        }
    }

    // never drop the train marker. a strip that hides a train says the section is clear when it isn't
    private static void drawTrain(PanelSurface s, FaceType ft, RailSnapshot snap,
                                  RailSnapshot.TrainState t, float x, float cy, float railH,
                                  boolean labels, float labelY, List<float[]> taken, Rect body) {
        final int tint = TrainFaceArt.health(t);
        if (t.waitingForSignal || t.unhealthy()) {
            Shapes.circle(s, x, cy, railH * 1.9f, Tokens.alpha(tint, 0.24f));
        }

        final float heading = headingOn(snap, t) ? 0.0f : (float) Math.PI;
        TrainFaceArt.marker(s, x, cy, Math.max(2.0f, 2.9f * ft.unit), heading, tint, t.moving());

        if (!labels || !ft.hasNano()) {
            return;
        }
        final StringBuilder sb = new StringBuilder(t.name);
        final String cause = TrainIntel.cause(snap, t);
        if (!cause.isEmpty()) {
            sb.append(" · ").append(cause);
        } else if (t.moving()) {
            sb.append(' ').append(TrainIntel.speed(t.metresPerSecond()));
        }
        final String label = Text.truncate(s, sb.toString(), body.w * 0.42f, ft.nano);
        final float w = s.textWidth(label, ft.nano);
        final float lx = clamp(x - w * 0.5f, body.x, body.right() - w);
        if (hits(taken, lx, w)) {
            return;
        }
        taken.add(new float[]{lx, w});
        TrainFaceArt.plate(s, label, lx, labelY, tint, ft.nano, ft);
    }

    private static boolean headingOn(RailSnapshot snap, RailSnapshot.TrainState t) {
        return t.destinationStation == null || !t.backwards;
    }

    private static List<UUID> trainsOn(RailSnapshot snap, LineChainage chain) {
        final List<UUID> out = new ArrayList<>();
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if (!Float.isNaN(chain.chainageOf(t))) {
                out.add(t.id);
            }
        }
        return out;
    }

    private static String nextDue(RailSnapshot snap, RailMap.Node node) {
        int best = Integer.MAX_VALUE;
        for (final RailSnapshot.Station member : node.members) {
            for (final RailSnapshot.Arrival a : snap.arrivals()) {
                if (a.ticks >= 0 && RailSnapshot.matches(a.station, member.name)) {
                    best = Math.min(best, a.ticks);
                }
            }
        }
        return best == Integer.MAX_VALUE ? "" : TrainIntel.eta(best);
    }

    private static int running(RailSnapshot snap, RailMap.Line service) {
        return service.activeTrains(snap);
    }

    private static String crumb(LineChainage chain, RailMap.Line service) {
        return chain.stopCount() + " stops · " + TrainIntel.distance(chain.total)
                + (service.loop ? " circular" : "");
    }

    private static void glyph(PanelSurface s, Rect area, FaceType ft, RailSnapshot snap,
                              RailMap.Line service, LineChainage chain, int color) {
        final int live = running(snap, service);
        Shapes.rect(s, area, Tokens.alpha(live > 0 ? color : Tokens.INK_3, 0.12f));
        s.advanceLayer();
        final float x0 = area.x + area.w * 0.12f;
        final float span = area.w * 0.76f;
        final float y = area.centerY();
        Shapes.rect(s, x0, y - 1.0f, span, Math.max(1.5f, 2.0f * ft.unit),
                Tokens.alpha(color, 0.55f));
        final float total = Math.max(1.0f, chain.total);
        for (int i = 0; i < chain.stopCount(); i++) {
            final RailMap.Node node = chain.node(i);
            Shapes.circle(s, x0 + span * (chain.chainAt(i) / total), y,
                    Math.max(1.5f, 2.2f * ft.unit),
                    node != null && node.presentTrain() != null ? Tokens.GOOD : Tokens.INK_3);
        }
        for (final UUID id : trainsOn(snap, chain)) {
            final RailSnapshot.TrainState t = snap.train(id);
            final float metres = chain.chainageOf(t);
            if (!Float.isNaN(metres)) {
                Shapes.circle(s, x0 + span * clamp01(metres / total), y,
                        Math.max(2.0f, 3.0f * ft.unit), TrainFaceArt.health(t));
            }
        }
    }

    private static boolean hits(List<float[]> lane, float at, float length) {
        for (final float[] r : lane) {
            if (at < r[0] + r[1] + 2.0f && at + length + 2.0f > r[0]) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.0f : v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }
}
