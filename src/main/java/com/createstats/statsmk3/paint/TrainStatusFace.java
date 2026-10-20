package com.createstats.statsmk3.paint;

import java.util.List;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.RailHistory;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class TrainStatusFace {
    private TrainStatusFace() {
    }

    static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final RailSnapshot snap = ClientRailNetwork.get();
        final FaceType ft = FaceType.of(area);

        if (snap.isEmpty()) {
            TrainFaceArt.noData(s, area, ft, "No railway", "Waiting for the network");
            return;
        }
        final TrainIntel.Summary sum = TrainIntel.summarise(snap);
        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(snap);
        final int worst = alerts.isEmpty() ? Tokens.GOOD
                : TrainFaceArt.color(alerts.get(0).severity);

        if (lod.isGlyph()) {
            glyph(s, area, ft, sum, alerts, worst);
            return;
        }

        final TrainFaceArt.Head head = TrainFaceArt.header(s, area, ft, lod, "Network",
                alerts.isEmpty() ? "ALL CLEAR" : alerts.size() + (alerts.size() == 1
                        ? " ALERT" : " ALERTS"), worst,
                sum.stations + " stations · " + sum.lines
                        + (sum.lines == 1 ? " service" : " services"));

        Rect body = new Rect(area.x + ft.pad, head.bottom(), area.w - ft.pad * 2.0f,
                area.bottom() - ft.pad - head.bottom());
        if (body.h < 10.0f || body.w < 24.0f) {
            return;
        }

        // numbers first, then reasons. 1 broken of 2 and 1 of 40 are different days

        final float kpiH = Math.min(body.h * (alerts.isEmpty() ? 0.55f : 0.38f),
                Text.lineHeight(ft.head) + Text.lineHeight(ft.nano) + ft.gap * 3.0f);
        if (kpiH >= Text.lineHeight(ft.row)) {
            drawKpis(s, new Rect(body.x, body.y, body.w, kpiH), ft, sum);
            body = new Rect(body.x, body.y + kpiH + ft.gap, body.w, body.h - kpiH - ft.gap);
        }

        if (body.h < Text.lineHeight(ft.micro)) {
            return;
        }
        if (alerts.isEmpty()) {
            drawHealthy(s, body, ft, lod, snap);
            return;
        }

        final Rect used = drawAlerts(s, body, ft, lod, alerts);
        final Rect rest = new Rect(body.x, used.bottom() + ft.gap, body.w,
                body.bottom() - used.bottom() - ft.gap);
        if (rest.h >= Text.lineHeight(ft.micro) * 3.0f) {
            Shapes.rect(s, rest.x, rest.y - ft.gap * 0.5f, rest.w, Math.max(1.0f, ft.hair),
                    Tokens.alpha(Tokens.LINE, 0.9f));
            drawHealthy(s, rest, ft, lod, snap);
        }
    }

    static void supplement(PanelSurface s, Rect r, FaceType ft, Lod lod, RailSnapshot snap) {
        if (r.h < Text.lineHeight(ft.micro) * 2.0f || r.w < 40.0f) {
            return;
        }
        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(snap);
        final TrainIntel.Summary sum = TrainIntel.summarise(snap);
        final int worst = alerts.isEmpty() ? Tokens.GOOD
                : TrainFaceArt.color(alerts.get(0).severity);

        final String heading = Text.track(alerts.isEmpty() ? "Service information" : "Disruption");
        s.text(Text.truncate(s, heading, r.w, ft.micro), r.x, r.y, Tokens.fade(worst, 0.85f),
                ft.micro);

        final float room = r.w - s.textWidth(heading, ft.micro) - ft.pad;
        final String tally = sum.moving + " running · " + sum.trains + " on the railway";
        final String shortTally = sum.moving + "/" + sum.trains + " running";
        if (s.textWidth(tally, ft.micro) <= room) {
            s.textRight(tally, r.right(), r.y, Tokens.INK_3, ft.micro);
        } else if (s.textWidth(shortTally, ft.micro) <= room) {
            s.textRight(shortTally, r.right(), r.y, Tokens.INK_3, ft.micro);
        }
        final float headH = Text.lineHeight(ft.micro) + ft.gap;
        Shapes.rect(s, r.x, r.y + headH - ft.gap * 0.5f, r.w, Math.max(1.0f, ft.hair),
                Tokens.alpha(Tokens.LINE, 0.9f));

        final Rect rest = new Rect(r.x, r.y + headH, r.w, r.h - headH);
        if (rest.h < Text.lineHeight(ft.micro)) {
            return;
        }
        if (alerts.isEmpty()) {
            drawHealthy(s, rest, ft, lod, snap);
        } else {
            drawAlerts(s, rest, ft, lod, alerts);
        }
    }

    private static void drawHealthy(PanelSurface s, Rect body, FaceType ft, Lod lod,
                                    RailSnapshot snap) {
        final List<TrainIntel.Line> lines = TrainIntel.lines(snap);
        final float traceH = Math.min(body.h * 0.34f, 26.0f * ft.unit);
        Rect list = body;
        if (!lines.isEmpty() && body.h - traceH >= Text.lineHeight(ft.micro) * 2.0f) {
            list = new Rect(body.x, body.y, body.w, body.h - traceH - ft.gap);
            drawActivity(s, new Rect(body.x, body.bottom() - traceH, body.w, traceH), ft, lod);
            drawServices(s, list, ft, snap, lines);
            return;
        }
        drawActivity(s, list, ft, lod);
    }

    private static void drawServices(PanelSurface s, Rect body, FaceType ft, RailSnapshot snap,
                                     List<TrainIntel.Line> lines) {
        final TrainFaceArt.Rows plan = TrainFaceArt.rows(body.h, lines.size(),
                Text.lineHeight(ft.micro) + ft.gap * 0.5f, ft.oneLineRow());
        for (int i = 0; i < plan.count() && i < lines.size(); i++) {
            final TrainIntel.Line line = lines.get(i);
            final Rect row = new Rect(body.x, body.y + i * plan.height(), body.w, plan.height());
            final int color = Tokens.SERIES[line.colorIndex % Tokens.SERIES.length];
            Shapes.rect(s, row.x, row.y + ft.gap * 0.25f, Math.max(1.5f, 2.0f * ft.unit),
                    Math.max(3.0f, row.h - ft.gap * 0.5f), color);
            int moving = 0;
            for (final java.util.UUID id : line.trains) {
                final RailSnapshot.TrainState t = snap.train(id);
                if (t != null && t.moving()) {
                    moving++;
                }
            }
            final String right = moving + "/" + line.trains.size();
            final float rightW = s.textWidth(right, ft.micro) + ft.gap;
            s.textV(Text.truncate(s, line.label(), row.w - rightW - ft.pad, ft.micro),
                    row.x + ft.pad, row.centerY(), Tokens.INK_2, ft.micro);
            s.textRightV(right, row.right(), row.centerY(),
                    moving > 0 ? Tokens.GOOD : Tokens.INK_3, ft.micro);
        }
        if (plan.count() < lines.size()) {
            s.textRight("+" + (lines.size() - plan.count()), body.right(),
                    body.bottom() - Text.lineHeight(ft.nano), Tokens.INK_3, ft.nano);
        }
    }

    private static void drawKpis(PanelSurface s, Rect r, FaceType ft, TrainIntel.Summary sum) {
        record Kpi(String value, String label, int color) {
        }
        final Kpi[] all = {
                new Kpi(String.valueOf(sum.broken), sum.broken == 1 ? "fault" : "faults",
                        sum.broken > 0 ? Tokens.CRITICAL : Tokens.INK_3),
                new Kpi(String.valueOf(sum.held), "held",
                        sum.held > 0 ? Tokens.CAUTION : Tokens.INK_3),
                new Kpi(String.valueOf(sum.moving), "running", Tokens.GOOD),
                new Kpi(String.valueOf(sum.trains), sum.trains == 1 ? "train" : "trains",
                        Tokens.INK),
                new Kpi(TrainIntel.speed(sum.averageSpeed), "avg " + TrainIntel.SPEED_UNIT,
                        Tokens.INK_2),
        };

        float widest = 0.0f;
        for (final Kpi k : all) {
            widest = Math.max(widest, Math.max(s.textWidth(k.value(), ft.head),
                    s.textWidth(k.label(), ft.nano)));
        }
        final int count = Math.max(1, Math.min(all.length,
                (int) Math.floor(r.w / (widest + ft.pad * 1.5f))));
        final float tileW = r.w / count;

        for (int i = 0; i < count; i++) {
            final Kpi k = all[i];
            final Rect tile = new Rect(r.x + i * tileW, r.y, tileW, r.h);
            final float valueScale = Text.fitScale(s, k.value(), tile.w - ft.gap,
                    tile.h - Text.lineHeight(ft.nano) - ft.gap, ft.head, ft.micro);
            s.text(k.value(), tile.x, tile.y, k.color(), valueScale);
            s.text(Text.truncate(s, k.label(), tile.w - ft.gap, ft.nano), tile.x,
                    tile.y + Text.lineHeight(valueScale) + ft.gap * 0.5f, Tokens.INK_3, ft.nano);
            if (i > 0) {
                Shapes.rect(s, tile.x - ft.gap, tile.y, Math.max(1.0f, ft.hair), tile.h,
                        Tokens.alpha(Tokens.LINE_HI, 0.8f));
            }
        }
    }

    static Rect drawAlerts(PanelSurface s, Rect body, FaceType ft, Lod lod,
                           List<TrainIntel.Alert> alerts) {
        final boolean detail = lod.showsLabels();
        final float ideal = detail
                ? Text.lineHeight(ft.row) + Text.lineHeight(ft.nano) * 2.0f + ft.gap * 2.0f
                : Text.lineHeight(ft.row) + ft.gap;
        final float min = Text.lineHeight(ft.micro) + ft.gap;

        final int columns = Math.max(1, Math.min(2,
                (int) Math.floor(body.w / (s.textWidth("MMMMMMMMMMMMMMMM", ft.row) + ft.pad))));
        final float colW = (body.w - ft.pad * (columns - 1)) / columns;
        final TrainFaceArt.Rows plan = TrainFaceArt.rows(body.h,
                (alerts.size() + columns - 1) / columns, min, ideal);
        if (plan.isEmpty()) {
            return new Rect(body.x, body.y, body.w, 0.0f);
        }

        int index = 0;
        for (int c = 0; c < columns && index < alerts.size(); c++) {
            final Rect col = new Rect(body.x + c * (colW + ft.pad), body.y, colW, body.h);
            for (int i = 0; i < plan.count() && index < alerts.size(); i++, index++) {
                drawAlert(s, new Rect(col.x, col.y + i * plan.height(), col.w,
                        plan.height() - ft.gap * 0.5f), ft, alerts.get(index), detail);
            }
        }

        if (index < alerts.size()) {
            s.textRight("+" + (alerts.size() - index) + " more", body.right(),
                    body.bottom() - Text.lineHeight(ft.nano), Tokens.INK_3, ft.nano);
            return body;
        }
        final int drawn = (index + columns - 1) / columns;
        return new Rect(body.x, body.y, body.w, Math.min(body.h, drawn * plan.height()));
    }

    private static void drawAlert(PanelSurface s, Rect r, FaceType ft, TrainIntel.Alert alert,
                                  boolean detail) {
        final int color = TrainFaceArt.color(alert.severity);
        Shapes.rect(s, r.x, r.y, Math.max(1.5f, 2.0f * ft.unit), r.h, color);
        final float left = r.x + Math.max(1.5f, 2.0f * ft.unit) + ft.gap;
        final float room = r.right() - left;

        s.text(Text.truncate(s, alert.subject, room, ft.row), left, r.y, color, ft.row);
        if (!detail) {
            return;
        }
        float y = r.y + Text.lineHeight(ft.row) + ft.gap * 0.25f;
        for (final String line : Text.wrap(s, alert.detail, room, ft.nano)) {
            if (y + Text.lineHeight(ft.nano) > r.bottom() + 0.5f) {
                break;
            }
            s.text(line, left, y, Tokens.INK_3, ft.nano);
            y += Text.lineHeight(ft.nano);
        }
    }

    private static void drawActivity(PanelSurface s, Rect body, FaceType ft, Lod lod) {
        s.text(Text.track("Trains in motion"), body.x, body.y, Tokens.INK_3, ft.nano);
        final Rect plot = new Rect(body.x, body.y + Text.lineHeight(ft.nano) + ft.gap,
                body.w, body.h - Text.lineHeight(ft.nano) - ft.gap);
        if (plot.h < 6.0f) {
            return;
        }
        final RailHistory.Pulse pulse = RailHistory.pulse();
        final int n = pulse.size();
        if (n < 3) {
            s.textCenterV("Recording", plot.centerX(), plot.centerY(), Tokens.INK_3, ft.micro);
            return;
        }
        final int step = Math.max(1, n / Math.max(8, (int) plot.w));
        final int points = (n + step - 1) / step;
        final float[] xy = new float[points * 2];
        int high = 1;
        for (int i = 0; i < n; i += step) {
            high = Math.max(high, pulse.moving(i));
        }

        final float ceiling = high + 1.0f;
        int w = 0;
        for (int i = 0; i < n && w < points; i += step, w++) {
            xy[w * 2] = plot.x + plot.w * (i / (float) Math.max(1, n - 1));
            xy[w * 2 + 1] = plot.bottom() - plot.h * (pulse.moving(i) / ceiling);
        }
        if (w > 1) {
            Shapes.areaFill(s, xy, w, plot.bottom(), Tokens.alpha(Tokens.GOOD, 0.13f),
                    Tokens.alpha(Tokens.GOOD, 0.01f));
            Shapes.polyline(s, xy, w, Math.max(1.0f, 1.3f * ft.unit), Tokens.GOOD);
        }
        Shapes.rect(s, plot.x, plot.bottom(), plot.w, Math.max(1.0f, ft.hair),
                Tokens.alpha(Tokens.LINE_HI, 0.9f));
        if (lod.showsLabels() && ft.hasNano()) {
            s.textRight("peak " + high, plot.right(), plot.y, Tokens.INK_3, ft.nano);
        }
    }

    private static void glyph(PanelSurface s, Rect area, FaceType ft, TrainIntel.Summary sum,
                              List<TrainIntel.Alert> alerts, int worst) {
        Shapes.rect(s, area, Tokens.alpha(worst, 0.16f));
        s.advanceLayer();
        final String figure = alerts.isEmpty() ? String.valueOf(sum.moving)
                : String.valueOf(alerts.size());
        final String label = alerts.isEmpty() ? "RUNNING"
                : alerts.size() == 1 ? "ALERT" : "ALERTS";
        final float scale = Text.fitScale(s, figure, area.w * 0.7f, area.h * 0.46f, ft.hero, ft.row);
        s.textCenter(figure, area.centerX(), area.y + area.h * 0.22f, worst, scale);
        if (area.h > 30.0f) {
            s.textCenter(label, area.centerX(), area.y + area.h * 0.22f + Text.lineHeight(scale),
                    Tokens.alpha(worst, 0.8f), ft.micro);
        }
    }
}
