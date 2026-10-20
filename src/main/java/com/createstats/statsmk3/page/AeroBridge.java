package com.createstats.statsmk3.page;

import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialText;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.HelmWords;
import com.createstats.aerial.V3;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AeroBridge {
    public static final String[] TABS = {"Bridge", "Lift", "Engines", "Nav", "Log", "Traffic"};

    static final int TRAFFIC_TAB = 5;
    public static final float STRIP_H = 40.0f;
    public static final float TABS_H = 18.0f;
    static final float GAP = Tokens.S2;
    static final float PI = (float) Math.PI;

    private AeroBridge() {
    }

    public static Rect content(Rect area) {
        final float top = STRIP_H + 4.0f + TABS_H + 6.0f;
        return new Rect(area.x, area.y + top, area.w, area.h - top);
    }

    public static Rect[] tabRects(PanelSurface s, Rect area) {
        final Rect row = new Rect(area.x, area.y + STRIP_H + 4.0f, area.w, TABS_H);
        final Rect[] out = new Rect[TABS.length];
        float x = row.x + 2.0f;
        for (int i = 0; i < TABS.length; i++) {
            final float w = s.textWidth(TABS[i], Text.META) + 14.0f;
            out[i] = new Rect(x, row.y, w, row.h);
            x += w + 2.0f;
        }
        return out;
    }

    public static void frame(PanelSurface s, Rect area, AerialFrame f, int tab, AeroSkin k) {
        strip(s, area.top(STRIP_H), f, k);
        final Rect[] tabs = tabRects(s, area);
        final float y = area.y + STRIP_H + 4.0f;
        Shapes.rect(s, area.x, y + TABS_H - 1.0f, area.w, 1.0f, k.cardEdge());
        for (int i = 0; i < tabs.length; i++) {
            final Rect t = tabs[i];
            final boolean on = i == tab;

            final boolean quiet = i == TRAFFIC_TAB && f.readout().traffic.isEmpty();
            s.textCenterV(TABS[i], t.centerX(), t.centerY() - 1.0f, on ? k.ink() : quiet ? Tokens.alpha(k.ink3(), 0.5f)
                    : k.ink3(), Text.META);
            if (on) {
                Shapes.rect(s, t.x + 4.0f, t.bottom() - 2.0f, t.w - 8.0f, 2.0f, Tokens.ACCENT);
            }
        }
    }

    public static void strip(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect in = r.inset(10.0f, 5.0f, 8.0f, 5.0f);

        final float lampW = Math.min(78.0f, Math.max(62.0f, in.w * 0.13f));
        master(s, new Rect(in.right() - lampW, in.y + 3.0f, lampW, in.h - 6.0f), f);

        final float nameW = Math.max(118.0f, Math.min(in.w * 0.30f, 200.0f));
        final String name = rd.snapshot.name().isBlank() ? "Aerial" : rd.snapshot.name();
        s.text(Text.truncate(s, name, nameW - 4.0f, Text.SUB), in.x, in.y + 1.0f, k.ink(), Text.SUB);
        s.text(Text.truncate(s, modeLine(rd), nameW - 4.0f, Text.META), in.x, in.y + 18.0f, k.ink2(), Text.META);

        final float x0 = in.x + nameW;
        final float x1 = in.right() - lampW - 8.0f;
        final float w = x1 - x0;
        if (w < 120.0f) {
            return;
        }
        final boolean roomy = w >= 300.0f;
        final float orderW = Math.min(118.0f, w * 0.40f);
        final int cells = roomy ? 3 : 2;
        final float cellW = (w - orderW) / cells;
        float x = x0;
        figure(s, x, in, cellW, "ALT", whole(rd.altitude), heightColor(f, k), Text.TITLE, "");
        x += cellW;
        figure(s, x, in, cellW, "V/S", signed(rd.verticalSpeed), vsColor(f, k), Text.SUB, "");
        x += cellW;
        if (roomy) {
            figure(s, x, in, cellW, rd.airMeasured ? "AIRSPEED" : "SPEED", tenth(rd.airMeasured ? rd.airspeed
                    : rd.groundSpeed), k.ink(), Text.SUB, "b/s");
            x += cellW;
        }
        final String order = h.known() ? HelmWords.order(h.order()) : "--";
        final float scale = s.textWidth(order, Text.SUB) <= orderW - 4.0f ? Text.SUB : Text.META;
        figure(s, x, in, orderW, "ORDER", order, h.known() ? k.ink() : k.ink3(), scale, "");
    }

    private static void figure(PanelSurface s, float x, Rect in, float w, String label, String value, int color,
                               float scale, String unit) {
        final float vy = in.y + 11.0f + (Text.TITLE - scale) * 4.0f;
        s.text(Text.truncate(s, value, w - 6.0f, scale), x, vy, color, scale);
        if (!unit.isEmpty() && s.textWidth(value, scale) + s.textWidth(unit, Text.META) + 10.0f <= w) {
            s.text(unit, x + s.textWidth(value, scale) + 3.0f, vy + Text.lineHeight(scale) - Text.lineHeight(Text.META),
                    labelInk(), Text.META);
        }
        s.text(label, x, in.y + 1.0f, labelInk(), Text.META);
    }

    private static int labelInk() {
        return Tokens.INK_3;
    }

    static String modeLine(AirframeReadout rd) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        if (!h.known()) {
            return rd.landed ? "on the ground" : "no helm reported";
        }
        final String mode = HelmWords.mode(h.mode());
        final String words = mode.isEmpty() ? h.mode() : mode.charAt(0) + mode.substring(1).toLowerCase(Locale.ROOT);
        final long since = h.since() == AerialSnapshot.NO_TICK ? -1L : rd.snapshot.gameTime() - h.since();
        final String time = since >= 0L ? AerialAlerts.duration(since / 20.0) : "";
        if (h.atHelm()) {
            return words + (h.pilot().isEmpty() ? "" : ": " + h.pilot());
        }
        if (h.unattended()) {
            return "No pilot: " + words.toLowerCase(Locale.ROOT) + (time.isEmpty() ? "" : ", " + time);
        }
        return words + (time.isEmpty() ? "" : ", " + time);
    }

    public static void master(PanelSurface s, Rect r, AerialFrame f) {
        final AerialAlerts.Level worst = worstRaised(f);
        final int n = AeroArt.countAtLeast(f.alerts(), AerialAlerts.Level.CAUTION);
        final int col = worst == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : worst == AerialAlerts.Level.CAUTION ? Tokens.CAUTION : Tokens.GOOD;

        final String full = worst == AerialAlerts.Level.CRITICAL ? "WARNING " + n
                : worst == AerialAlerts.Level.CAUTION ? "CAUTION " + n : "ALL CLEAR";
        final String shorter = worst == null ? "CLEAR" : (worst == AerialAlerts.Level.CRITICAL ? "WARN " : "CAUT ") + n;
        final String least = worst == null ? "OK" : Integer.toString(n);
        final String text = s.textWidth(full, Text.META) <= r.w - 6.0f ? full
                : s.textWidth(shorter, Text.META) <= r.w - 6.0f ? shorter : least;
        if (worst == null) {
            Shapes.roundedRectOutline(s, r, 4.0f, 1.0f, Tokens.alpha(col, 0.45f));
            s.textCenterV(text, r.centerX(), r.centerY(), Tokens.alpha(col, 0.85f), Text.META);
            return;
        }
        Shapes.roundedRect(s, r, 4.0f, col);
        s.textCenterV(text, r.centerX(), r.centerY(), Tokens.CANVAS, Text.META);
    }

    static AerialAlerts.Level worstRaised(AerialFrame f) {
        final AerialAlerts.Level w = f.worst();
        return w == AerialAlerts.Level.ADVISORY ? null : w;
    }

    static int heightColor(AerialFrame f, AeroSkin k) {
        AerialAlerts.Level level = null;
        for (final AerialAlerts.Alert a : f.alerts()) {
            if ((a.code() == AerialAlerts.Code.TERRAIN || a.code() == AerialAlerts.Code.SINK_RATE
                    || a.code() == AerialAlerts.Code.LIFT_SHORT) && (level == null || a.level().compareTo(level) > 0)) {
                level = a.level();
            }
        }
        return level == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : level == AerialAlerts.Level.CAUTION ? Tokens.CAUTION : k.ink();
    }

    static int vsColor(AerialFrame f, AeroSkin k) {
        for (final AerialAlerts.Alert a : f.alerts()) {
            if (a.code() == AerialAlerts.Code.SINK_RATE) {
                return a.level() == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL : Tokens.CAUTION;
            }
        }
        return k.ink();
    }

    public record Layout(Rect portrait, Rect tape, Rect dial, Rect order, Rect lift, Rect alerts, Rect headline) {
    }

    public static Layout layout(Rect r) {
        final float headH = r.h >= 260.0f ? 28.0f : 22.0f;
        final Rect main = new Rect(r.x, r.y, r.w, r.h - headH - 6.0f);
        final float colW = Math.max(128.0f, Math.min(r.w * 0.26f, 196.0f));
        final float tapeW = r.w >= 560.0f ? 52.0f : 44.0f;
        final Rect col = new Rect(main.right() - colW, main.y, colW, main.h);
        final Rect tape = new Rect(col.x - GAP - tapeW, main.y, tapeW, main.h);
        final Rect portrait = new Rect(main.x, main.y, tape.x - GAP - main.x, main.h);
        final float dialH = Math.min(col.w * 0.56f, col.h * 0.46f);
        final Rect dial = new Rect(col.x, col.y, col.w, dialH);
        final Rect order = new Rect(col.x, dial.bottom(), col.w, 30.0f);
        final Rect lift = new Rect(col.x, order.bottom() + 6.0f, col.w, 42.0f);
        final float left = col.bottom() - lift.bottom() - 6.0f;
        final Rect alerts = left >= 44.0f ? new Rect(col.x, lift.bottom() + 6.0f, col.w, left) : Rect.EMPTY;
        return new Layout(portrait, tape, dial, order, lift, alerts, new Rect(r.x, r.bottom() - headH, r.w, headH));
    }

    public static void bridge(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final Layout l = layout(r);
        final AirframeReadout rd = f.readout();
        portrait(s, l.portrait(), f, k, true);
        altitudeTape(s, l.tape(), rd, k);
        final Rect dialCard = new Rect(l.dial().x, l.dial().y, l.dial().w, l.order().bottom() - l.dial().y);
        Shapes.card(s, dialCard, 6.0f, k.card(), k.cardEdge());
        telegraph(s, l.dial().inset(6.0f, 6.0f, 6.0f, 0.0f), f, k, true);
        orderWords(s, l.order(), rd, k);
        liftGauge(s, l.lift(), rd, k);
        if (!l.alerts().isEmpty()) {
            alertList(s, l.alerts(), f, k);
        }
        headline(s, l.headline(), f, k, Text.SUB);
    }

    public static void portrait(PanelSurface s, Rect r, AerialFrame f, AeroSkin k, boolean labels) {
        portrait(s, r, f, k, labels ? DETAIL_FULL : DETAIL_NONE);
    }

    public static final int DETAIL_NONE = 0;

    public static final int DETAIL_LEVELS = 1;

    public static final int DETAIL_FULL = 2;

    public static void portrait(PanelSurface s, Rect r, AerialFrame f, AeroSkin k, int detail) {
        final boolean labels = detail >= DETAIL_FULL;
        final boolean levels = detail >= DETAIL_LEVELS;
        final AirframeReadout rd = f.readout();
        final AerialSnapshot snap = rd.snapshot;
        final AerialSnapshot.Silhouette sil = snap.silhouette();
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect view = r.inset(1.0f);
        Shapes.gradientV(s, view.inset(0.0f, 2.0f, 0.0f, 2.0f), k.skyTop(), k.skyLow());
        s.advanceLayer();
        final float compassH = labels && view.h >= 90.0f ? 17.0f : 0.0f;
        if (compassH > 0.0f) {
            compass(s, new Rect(view.x + 4.0f, view.y + 3.0f, view.w - 8.0f, compassH - 3.0f), rd, k);
        }
        final Rect scene = new Rect(view.x, view.y + compassH, view.w, view.h - compassH);
        if (sil.isEmpty() || scene.h < 20.0f || scene.w < 30.0f) {
            s.textCenterV("The Aerial has not sent its outline", scene.centerX(), scene.centerY(), k.ink3(), Text.META);
            return;
        }
        final double length = sil.cols() * sil.cell();
        final double height = sil.sideRows() * sil.cell();
        final float ppm = (float) Math.max(0.5, Math.min((scene.w - 20.0f) * 0.88 / length,
                (scene.h - 12.0f) * 0.64 / height));
        // centre on the outline, not CoM. bottom heavy ships hung off the picture

        final double midF = sil.originF() + length * 0.5;
        final double midU = sil.originU() + height * 0.5;
        final float cx = scene.centerX() - (float) (midF * ppm);
        final float cy = scene.y + scene.h * 0.47f + (float) (midU * ppm);
        final double pitch = Math.toRadians(rd.pitch);
        final float cp = (float) Math.cos(pitch);
        final float sp = (float) Math.sin(pitch);
        final double alt = rd.altitude;
        final AerialSnapshot.Helm h = snap.helm();

        ground(s, scene, rd, k, cx, cy, ppm, levels);

        final double held = h.known() && Double.isFinite(h.heldAltitude()) ? h.heldAltitude()
                : (snap.autopilot().engaged() ? snap.autopilot().targetAltitude() : Double.NaN);
        final double flown = h.known() ? h.flownAltitude() : Double.NaN;
        final double ceiling = ceilingOf(rd);
        level(s, scene, cy - (float) ((ceiling - alt) * ppm), k.limit(), levels ? "ceiling " + whole(ceiling) : null,
                true);
        if (Double.isFinite(flown) && Double.isFinite(held) && flown > held + 0.5) {
            level(s, scene, cy - (float) ((flown - alt) * ppm), Tokens.alpha(k.held(), 0.55f),
                    levels ? "guard " + whole(flown) : null, false);
        }
        level(s, scene, cy - (float) ((held - alt) * ppm), k.held(), levels ? "hold " + whole(held) : null, false);
        s.advanceLayer();

        final float c = (float) (sil.cell() * ppm);
        final int[] envRows = envelopeRows(sil);
        final double fill = gasFill(snap);
        final double gasLine = envRows == null || !Double.isFinite(fill) ? Double.POSITIVE_INFINITY
                : envRows[1] + 1 - fill * (envRows[1] - envRows[0] + 1);
        final boolean seams = c >= 5.0f;
        for (int row = 0; row < sil.sideRows(); row++) {
            for (int col = 0; col < sil.cols(); col++) {
                if (!sil.sideAt(col, row)) {
                    continue;
                }
                final boolean env = sil.envSideAt(col, row);
                final int base = env ? k.envelope() : k.hull();
                int colr = base;
                if (env && row + 0.5 >= gasLine) {
                    final double depth = (envRows[1] + 1 - (row + 0.5)) / Math.max(1.0, envRows[1] + 1 - gasLine);
                    final float glow = (float) (0.78 - 0.48 * depth);
                    colr = Tokens.mix(base, k.gas(), Math.max(0.12f, glow));
                }
                final double f0 = sil.originF() + col * sil.cell();
                final double u0 = sil.originU() + row * sil.cell();
                cell(s, cx, cy, ppm, cp, sp, f0, u0, sil.cell(), colr, seams ? (env ? k.envelopeSeam() : k.hullEdge()) : 0);
            }
        }
        s.advanceLayer();
        outline(s, sil, cx, cy, ppm, cp, sp, Tokens.alpha(Tokens.INK_2, 0.55f));
        s.advanceLayer();

        final V3 nose = snap.body().nose();

        for (final AerialSnapshot.Balloon b : snap.balloons()) {
            burners(s, b, sil, cx, cy, ppm, cp, sp, nose, k);
        }

        double maxThrust = 1.0;
        for (final AirframeReadout.PropState p : rd.props) {
            maxThrust = Math.max(maxThrust, Math.abs(p.prop().staticThrust()));
        }
        for (final AirframeReadout.PropState p : rd.props) {
            final V3 at = p.prop().position();
            final float[] pt = project(cx, cy, ppm, cp, sp, at.dot(nose), at.y());
            final V3 d = p.prop().direction();
            propeller(s, pt[0], pt[1], Math.max(2.5f, Math.min(c * 0.9f, 7.0f)), p.prop().active(), k);
            final double len = AirframeMath.clamp(p.thrust() / maxThrust, 0.0, 1.0) * Math.max(8.0, c * 2.4);
            if (len > 2.0) {
                final float[] tip = project(cx, cy, ppm, cp, sp, at.dot(nose) + d.dot(nose) * len / ppm,
                        at.y() + d.y() * len / ppm);
                arrow(s, pt[0], pt[1], tip[0], tip[1], 1.6f, k.engine());
            }
        }

        for (final AerialSnapshot.Part part : snap.parts().parts()) {
            final V3 at = part.at();
            final float[] pt = project(cx, cy, ppm, cp, sp, at.dot(nose), at.y());
            if (!inside(scene, pt[0], pt[1])) {
                continue;
            }
            switch (part.kind()) {
                case AerialSnapshot.Part.THRUSTER -> thruster(s, pt[0], pt[1], part, nose, cp, sp, Math.max(3.0f, c), k);
                case AerialSnapshot.Part.GUN -> gun(s, pt[0], pt[1], Math.max(3.0f, c * 0.8f));
                case AerialSnapshot.Part.LAMP -> {
                    if (part.value() > 0.5) {
                        Shapes.circle(s, pt[0], pt[1], Math.max(1.8f, c * 0.35f), lampColor((int) part.extra()));
                    }
                }
                default -> {
                }
            }
        }
        s.advanceLayer();

        final float[] com = {cx, cy};
        final float disc = Math.max(3.5f, Math.min(6.0f, c * 0.55f));
        final float armLen = Math.max(16.0f, Math.min(scene.h * 0.22f, c * 3.2f));
        final V3 liftAt = rd.liftCentre.isFinite() ? rd.liftCentre : balloonCentre(snap);
        final float[] lp = liftAt == null ? null : project(cx, cy, ppm, cp, sp, liftAt.dot(nose), liftAt.y());
        final String liftTag = Double.isFinite(rd.support) ? "LIFT " + AeroInstruments.pct(rd.support)
                : (h.known() && Double.isFinite(h.liftUse()) ? "LIFT " + AeroInstruments.pct(h.liftUse()) + " of most"
                : "LIFT");
        if (lp != null) {
            outlinedArrow(s, lp[0], lp[1] - disc, lp[0], lp[1] - disc - armLen, k.ink(),
                    Tokens.CANVAS);
            if (labels) {
                tag(s, scene, lp[0] + 5.0f, lp[1] - disc - armLen - 1.0f, liftTag, k, k.ink());
            }
        }
        outlinedArrow(s, com[0], com[1] + disc, com[0], com[1] + disc + armLen, k.ink2(),
                Tokens.CANVAS);
        centreOfMass(s, com[0], com[1], disc);
        if (labels) {
            final String weight = Double.isFinite(rd.carried) ? "WEIGHT " + Text.group(Math.round(rd.carried)) + " kpg"
                    : "WEIGHT";
            tag(s, scene, com[0] + 5.0f, com[1] + disc + armLen - 11.0f, weight, k, k.ink2());
            float ty = scene.y + 4.0f;
            if (Double.isFinite(rd.load) && rd.load > 0.5) {
                ty = cornerTag(s, scene, ty, "+" + Math.round(rd.load) + " kpg aboard, weighed", k.held(), k);
            }
            if (h.weighing()) {
                ty = cornerTag(s, scene, ty, "WEIGHING OFF", k.held(), k);
            }
            if (h.staff() > 0) {
                ty = cornerTag(s, scene, ty, h.staff() == 2 ? "PINNED BY A STAFF" : "HELD BY A STAFF", k.held(), k);
            }
        }
        if (levels && Double.isFinite(fill) && envRows != null) {
            final String gas = "gas " + AeroInstruments.pct(fill);
            s.text(gas, scene.right() - 5.0f - s.textWidth(gas, Text.META), scene.bottom() - 12.0f, k.gas(), Text.META);
        }
    }

    static float[] project(float cx, float cy, float ppm, float cp, float sp, double f, double u) {
        return new float[]{cx + (float) (f * cp - u * sp) * ppm, cy - (float) (f * sp + u * cp) * ppm};
    }

    private static void cell(PanelSurface s, float cx, float cy, float ppm, float cp, float sp, double f0, double u0,
                             double size, int color, int seam) {
        final float[] a = project(cx, cy, ppm, cp, sp, f0, u0);
        final float[] b = project(cx, cy, ppm, cp, sp, f0 + size, u0);
        final float[] c = project(cx, cy, ppm, cp, sp, f0 + size, u0 + size);
        final float[] d = project(cx, cy, ppm, cp, sp, f0, u0 + size);
        s.tri(a[0], a[1], color, b[0], b[1], color, c[0], c[1], color);
        s.tri(a[0], a[1], color, c[0], c[1], color, d[0], d[1], color);
        if (seam != 0) {
            Shapes.line(s, a[0], a[1], b[0], b[1], 0.6f, Tokens.alpha(seam, 0.8f));
            Shapes.line(s, a[0], a[1], d[0], d[1], 0.6f, Tokens.alpha(seam, 0.8f));
        }
    }

    private static void outline(PanelSurface s, AerialSnapshot.Silhouette sil, float cx, float cy, float ppm, float cp,
                                float sp, int color) {
        final double c = sil.cell();
        for (int row = 0; row < sil.sideRows(); row++) {
            for (int col = 0; col < sil.cols(); col++) {
                if (!sil.sideAt(col, row)) {
                    continue;
                }
                final double f0 = sil.originF() + col * c;
                final double u0 = sil.originU() + row * c;
                if (!sil.sideAt(col, row + 1)) {
                    edge(s, cx, cy, ppm, cp, sp, f0, u0 + c, f0 + c, u0 + c, color);
                }
                if (!sil.sideAt(col, row - 1)) {
                    edge(s, cx, cy, ppm, cp, sp, f0, u0, f0 + c, u0, color);
                }
                if (!sil.sideAt(col - 1, row)) {
                    edge(s, cx, cy, ppm, cp, sp, f0, u0, f0, u0 + c, color);
                }
                if (!sil.sideAt(col + 1, row)) {
                    edge(s, cx, cy, ppm, cp, sp, f0 + c, u0, f0 + c, u0 + c, color);
                }
            }
        }
    }

    private static void edge(PanelSurface s, float cx, float cy, float ppm, float cp, float sp, double f0, double u0,
                             double f1, double u1, int color) {
        final float[] a = project(cx, cy, ppm, cp, sp, f0, u0);
        final float[] b = project(cx, cy, ppm, cp, sp, f1, u1);
        Shapes.line(s, a[0], a[1], b[0], b[1], 1.0f, color);
    }

    // a resting ship's "ceiling" is below it, drawing it hatched the whole tape
    static double ceilingOf(AirframeReadout rd) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        if (h.known()) {
            return h.flying() ? h.ceiling() : Double.NaN;
        }
        return Double.isFinite(rd.ceilingY) && rd.ceilingY > rd.altitude && !rd.landed ? rd.ceilingY : Double.NaN;
    }

    static int[] envelopeRows(AerialSnapshot.Silhouette sil) {
        if (!sil.hasEnvelope()) {
            return null;
        }
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int row = 0; row < sil.sideRows(); row++) {
            for (int col = 0; col < sil.cols(); col++) {
                if (sil.envSideAt(col, row)) {
                    lo = Math.min(lo, row);
                    hi = Math.max(hi, row);
                }
            }
        }
        return lo > hi ? null : new int[]{lo, hi};
    }

    static double gasFill(AerialSnapshot snap) {
        double gas = 0.0;
        double capacity = 0.0;
        for (final AerialSnapshot.Balloon b : snap.balloons()) {
            if (Double.isFinite(b.filled()) && Double.isFinite(b.capacity()) && b.capacity() > 0.0) {
                gas += b.filled();
                capacity += b.capacity();
            }
        }
        return capacity > 0.0 ? AirframeMath.clamp(gas / capacity, 0.0, 1.0) : Double.NaN;
    }

    private static V3 balloonCentre(AerialSnapshot snap) {
        V3 sum = V3.ZERO;
        double w = 0.0;
        for (final AerialSnapshot.Balloon b : snap.balloons()) {
            if (b.position().isFinite() && Double.isFinite(b.lift())) {
                sum = sum.plus(b.position().times(Math.max(1.0e-6, b.lift())));
                w += Math.max(1.0e-6, b.lift());
            }
        }
        return w > 0.0 ? sum.times(1.0 / w) : null;
    }

    private static void ground(PanelSurface s, Rect scene, AirframeReadout rd, AeroSkin k, float cx, float cy, float ppm,
                               boolean labels) {
        final AerialSnapshot.Terrain t = rd.snapshot.terrain();
        final V3 pos = rd.snapshot.body().position();
        final V3 fwd = rd.forward.horizontal().normalized();
        if (t.size() == 0 || fwd.length() < 0.5) {
            groundTag(s, scene, rd, k, labels);
            return;
        }
        final int n = Math.max(2, (int) (scene.w / 3.0f) + 1);
        final float[] pts = new float[n * 2];
        int m = 0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            final float sx = scene.x + i * (scene.w / (n - 1));
            final double d = (sx - cx) / ppm;
            final double g = t.heightAt(pos.x() + fwd.x() * d, pos.z() + fwd.z() * d);
            if (!Double.isFinite(g)) {
                continue;
            }
            final float sy = cy - (float) ((g - rd.altitude) * ppm);
            any |= sy < scene.bottom() - 1.0f;
            pts[m * 2] = sx;
            pts[m * 2 + 1] = Math.max(scene.y, Math.min(scene.bottom(), sy));
            m++;
        }
        if (m < 2 || !any) {
            groundTag(s, scene, rd, k, labels);
            return;
        }
        Shapes.areaFill(s, pts, m, scene.bottom(), k.earth(), k.earth());
        Shapes.polyline(s, pts, m, 1.2f, k.earthEdge());
    }

    private static void groundTag(PanelSurface s, Rect scene, AirframeReadout rd, AeroSkin k, boolean labels) {
        if (!labels || !Double.isFinite(rd.agl)) {
            return;
        }
        final String text = "ground " + whole(rd.agl) + " b below";
        s.textCenter(text, scene.centerX(), scene.bottom() - 12.0f, k.ink3(), Text.META);
    }

    private static void level(PanelSurface s, Rect scene, float y, int color, String label, boolean onlyWhenNear) {
        if (!Float.isFinite(y)) {
            return;
        }
        final boolean in = y > scene.y + 3.0f && y < scene.bottom() - 3.0f;
        if (in) {
            for (float x = scene.x + 3.0f; x < scene.right() - 3.0f; x += 9.0f) {
                Shapes.rect(s, x, y - 0.75f, Math.min(5.0f, scene.right() - 3.0f - x), 1.5f, color);
            }
            if (label != null && s.textWidth(label, Text.META) + 10.0f <= scene.w) {
                s.text(label, scene.x + 5.0f, y > scene.y + 14.0f ? y - 11.0f : y + 3.0f, color, Text.META);
            }
            return;
        }
        if (onlyWhenNear || label == null) {
            return;
        }
        final boolean above = y <= scene.y + 3.0f;
        final float ay = above ? scene.y + 5.0f : scene.bottom() - 5.0f;
        final float ax = scene.x + 8.0f;
        if (above) {
            s.tri(ax, ay - 1.0f, color, ax - 4.0f, ay + 5.0f, color, ax + 4.0f, ay + 5.0f, color);
        } else {
            s.tri(ax, ay + 1.0f, color, ax + 4.0f, ay - 5.0f, color, ax - 4.0f, ay - 5.0f, color);
        }
        s.text(label, ax + 7.0f, above ? ay - 1.0f : ay - 9.0f, color, Text.META);
    }

    private static void burners(PanelSurface s, AerialSnapshot.Balloon b, AerialSnapshot.Silhouette sil, float cx,
                                float cy, float ppm, float cp, float sp, V3 nose, AeroSkin k) {
        if (b.heaters() <= 0 || !b.position().isFinite()) {
            return;
        }

        int[] rows = envelopeRows(sil);
        final double floor = rows == null ? b.position().y() - 2.0 : sil.originU() + rows[0] * sil.cell();
        final double f = b.position().dot(nose);
        final double spacing = Math.max(1.0, sil.cell()) * 1.2;
        for (int i = 0; i < b.heaters(); i++) {
            final double at = f + (i - (b.heaters() - 1) * 0.5) * spacing;
            final float[] p = project(cx, cy, ppm, cp, sp, at, floor - 0.6 * sil.cell());
            final boolean lit = i < Math.max(0, b.lit());
            final float r = Math.max(1.6f, Math.min(3.5f, (float) (0.35 * sil.cell() * ppm)));
            Shapes.rect(s, p[0] - r, p[1] - r * 0.6f, r * 2.0f, r * 1.2f, Tokens.LINE_HI);
            if (lit) {
                s.tri(p[0] - r * 0.8f, p[1] - r * 0.6f, Tokens.alpha(k.gas(), 0.95f), p[0] + r * 0.8f, p[1] - r * 0.6f,
                        Tokens.alpha(k.gas(), 0.95f), p[0], p[1] - r * 2.6f, Tokens.alpha(Tokens.HELD, 0.9f));
            }
        }
    }

    private static void propeller(PanelSurface s, float x, float y, float r, boolean active, AeroSkin k) {
        final int col = active ? k.ink2() : k.ink3();

        Shapes.rect(s, x - 0.9f, y - r, 1.8f, r * 2.0f, col);
        Shapes.circle(s, x, y, Math.max(1.3f, r * 0.35f), col);
    }

    private static void thruster(PanelSurface s, float x, float y, AerialSnapshot.Part p, V3 nose, float cp, float sp,
                                 float size, AeroSkin k) {
        final double df = p.dir().dot(nose);
        final double du = p.dir().y();
        final float dx = (float) (df * cp - du * sp);
        final float dy = (float) -(df * sp + du * cp);
        final float len = (float) Math.hypot(dx, dy);
        final float ux = len > 1.0e-4f ? dx / len : 1.0f;
        final float uy = len > 1.0e-4f ? dy / len : 0.0f;
        final int body = k.ink2();
        Shapes.line(s, x - ux * size * 0.6f, y - uy * size * 0.6f, x + ux * size * 0.4f, y + uy * size * 0.4f,
                Math.max(2.0f, size * 0.55f), body);
        if (p.value() > 0.02) {
            final float fl = size * (0.8f + 1.6f * (float) AirframeMath.clamp(p.value(), 0.0, 1.0));
            final float bx = x - ux * size * 0.6f;
            final float by = y - uy * size * 0.6f;
            s.tri(bx - uy * size * 0.35f, by + ux * size * 0.35f, Tokens.alpha(k.gas(), 0.95f), bx + uy * size * 0.35f,
                    by - ux * size * 0.35f, Tokens.alpha(k.gas(), 0.95f), bx - ux * fl, by - uy * fl,
                    Tokens.alpha(Tokens.HELD, 0.6f));
        }
    }

    private static void gun(PanelSurface s, float x, float y, float r) {
        final int col = Tokens.SERIES[0];
        Shapes.arc(s, x, y, r, 1.0f, 0.0f, 2.0f * PI, col);
        Shapes.rect(s, x - r - 1.5f, y - 0.5f, 2.0f * r + 3.0f, 1.0f, col);
        Shapes.rect(s, x - 0.5f, y - r - 1.5f, 1.0f, 2.0f * r + 3.0f, col);
    }

    static int lampColor(int kind) {
        return switch (kind) {
            case AerialSnapshot.Part.RED -> Tokens.LIMIT;
            case AerialSnapshot.Part.AMBER -> Tokens.CAUTION;
            case AerialSnapshot.Part.WHITE -> Tokens.INK;
            default -> Tokens.INK_2;
        };
    }

    static void centreOfMass(PanelSurface s, float x, float y, float r) {
        Shapes.circle(s, x, y, r + 0.8f, Tokens.CANVAS);
        Shapes.circle(s, x, y, r, Tokens.INK);
        s.tri(x, y, Tokens.CANVAS, x + r, y, Tokens.CANVAS, x, y - r, Tokens.CANVAS);
        s.tri(x, y, Tokens.CANVAS, x - r, y, Tokens.CANVAS, x, y + r, Tokens.CANVAS);
    }

    static void arrow(PanelSurface s, float x0, float y0, float x1, float y1, float w, int color) {
        final float dx = x1 - x0;
        final float dy = y1 - y0;
        final float len = (float) Math.hypot(dx, dy);
        if (len < 1.0f) {
            return;
        }
        final float ux = dx / len;
        final float uy = dy / len;
        final float head = Math.min(5.0f, len * 0.5f);
        Shapes.line(s, x0, y0, x1 - ux * head * 0.6f, y1 - uy * head * 0.6f, w, color);
        s.tri(x1, y1, color, x1 - ux * head - uy * head * 0.6f, y1 - uy * head + ux * head * 0.6f, color,
                x1 - ux * head + uy * head * 0.6f, y1 - uy * head - ux * head * 0.6f, color);
    }

    static void outlinedArrow(PanelSurface s, float x0, float y0, float x1, float y1, int fill, int edge) {
        arrow(s, x0, y0, x1, y1, 3.6f, edge);
        arrow(s, x0, y0 + Math.signum(y1 - y0) * 0.6f, x1, y1 - Math.signum(y1 - y0) * 1.0f, 1.8f, fill);
    }

    static void tag(PanelSurface s, Rect scene, float x, float y, String text, AeroSkin k, int color) {
        final float w = s.textWidth(text, Text.META) + 6.0f;
        final float tx = Math.max(scene.x + 2.0f, Math.min(x, scene.right() - w - 2.0f));
        final float ty = Math.max(scene.y + 2.0f, Math.min(y, scene.bottom() - 13.0f));
        Shapes.roundedRect(s, new Rect(tx, ty, w, 11.0f), 2.0f, Tokens.alpha(Tokens.CANVAS,
                0.78f));
        s.text(text, tx + 3.0f, ty + 1.5f, color, Text.META);
    }

    private static float cornerTag(PanelSurface s, Rect scene, float y, String text, int color, AeroSkin k) {
        if (y + 12.0f > scene.bottom()) {
            return y;
        }
        final float w = s.textWidth(text, Text.META) + 8.0f;
        if (w > scene.w - 8.0f) {
            return y;
        }
        final Rect r = new Rect(scene.right() - w - 4.0f, y, w, 12.0f);
        Shapes.roundedRect(s, r, 2.0f, Tokens.alpha(Tokens.CANVAS, 0.8f));
        s.textCenterV(text, r.centerX(), r.centerY(), color, Text.META);
        return y + 14.0f;
    }

    static void compass(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final double heading = rd.heading;
        final float ppd = r.w / 70.0f;
        final float mid = r.centerX();
        final int ink = k.ink2();
        Shapes.roundedRect(s, r, 3.0f, Tokens.alpha(Tokens.CANVAS, 0.6f));
        for (int d = (int) Math.floor(heading) - 36; d <= (int) Math.ceil(heading) + 36; d++) {
            final float x = mid + (float) (d - heading) * ppd;
            if (x <= r.x + 3.0f || x >= r.right() - 3.0f) {
                continue;
            }
            final int dd = Math.floorMod(d, 360);
            if (dd % 10 == 0) {
                Shapes.rect(s, x - 0.5f, r.bottom() - 4.0f, 1.0f, 3.0f, ink);
                if (dd % 30 == 0 && Math.abs(x - mid) > 16.0f) {
                    final String l = switch (dd) {
                        case 0 -> "N";
                        case 90 -> "E";
                        case 180 -> "S";
                        case 270 -> "W";
                        default -> Integer.toString(dd / 10);
                    };
                    if (x - s.textWidth(l, Text.META) * 0.5f > r.x + 2.0f && x + s.textWidth(l, Text.META) * 0.5f < r.right() - 2.0f) {
                        s.textCenter(l, x, r.y + 1.0f, ink, Text.META);
                    }
                }
            } else if (dd % 5 == 0) {
                Shapes.rect(s, x - 0.5f, r.bottom() - 2.5f, 1.0f, 1.5f, ink);
            }
        }
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        if (h.known() && Double.isFinite(h.heldHeading())) {
            final float x = mid + (float) AirframeMath.clamp(AirframeMath.wrap180(h.heldHeading() - heading), -33.0, 33.0) * ppd;
            s.tri(x - 4.0f, r.bottom(), k.held(), x + 4.0f, r.bottom(), k.held(), x, r.bottom() - 5.0f, k.held());
        }
        if (rd.nav != null) {
            final float x = mid + (float) AirframeMath.clamp(AirframeMath.wrap180(rd.nav.bearing() - heading), -33.0, 33.0) * ppd;
            Shapes.rect(s, x - 0.5f, r.y + 1.0f, 1.0f, r.h - 2.0f, k.destination());
            s.tri(x, r.y + 1.0f, k.destination(), x + 5.0f, r.y + 3.5f, k.destination(), x, r.y + 6.0f, k.destination());
        }
        if (rd.groundSpeed > 0.5) {
            final float x = mid + (float) AirframeMath.clamp(AirframeMath.wrap180(rd.track - heading), -33.0, 33.0) * ppd;
            AeroInstruments.diamond(s, x, r.bottom() - 3.0f, 2.5f, ink, false);
        }
        final String hdg = String.format(Locale.ROOT, "%03d", Math.round(AirframeMath.wrap360(heading)) % 360);
        final float bw = s.textWidth(hdg, Text.META) + 8.0f;
        final Rect box = new Rect(mid - bw * 0.5f, r.y, bw, r.h);
        Shapes.roundedRect(s, box, 2.0f, Tokens.CANVAS);
        s.textCenterV(hdg, box.centerX(), box.centerY(), Tokens.INK, Text.META);
    }

    public static void telegraph(PanelSurface s, Rect r, AerialFrame f, AeroSkin k, boolean labels) {
        final AerialSnapshot.Helm h = f.snapshot().helm();
        final float labelRoom = labels ? 12.0f : 2.0f;
        final float radius = Math.min(r.w * 0.5f - 4.0f, r.h - labelRoom - 4.0f);
        if (radius < 10.0f) {
            return;
        }
        final float cx = r.centerX();
        final float cy = r.y + radius + 3.0f;

        Shapes.arc(s, cx, cy, radius * 0.5f, radius, PI, PI, k.dialFace());
        final float sector = PI / 7.0f;
        final float inner = radius * 0.46f;
        for (int i = 0; i < 7; i++) {
            final int fill = i < 3 ? k.astern() : (i == 3 ? k.dialFace() : k.ahead());
            Shapes.arc(s, cx, cy, (inner + radius) * 0.5f, radius - inner, PI + i * sector, sector, fill);
        }
        s.advanceLayer();
        for (int i = 0; i <= 7; i++) {
            final float a = PI + i * sector;
            Shapes.line(s, cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, 1.0f, Tokens.alpha(k.dialInk2(), 0.6f));
        }
        Shapes.arc(s, cx, cy, radius, 1.5f, PI, PI, k.dialRim());
        Shapes.rect(s, cx - radius, cy - 0.75f, radius * 2.0f, 1.5f, k.dialRim());

        final float lr = (inner + radius) * 0.5f;
        for (int i = 0; i < 7; i++) {
            final float a = PI + (i + 0.5f) * sector;
            final String word = HelmWords.NOTCH_SHORT[i];
            final float arc = lr * sector;
            final float scale = s.textWidth(word, Text.META) <= arc - 2.0f ? Text.META : 0.0f;
            if (scale > 0.0f && radius >= 34.0f) {
                s.textCenterV(word, cx + (float) Math.cos(a) * lr, cy + (float) Math.sin(a) * lr,
                        i < 3 ? k.dialInk() : k.dialInk(), scale);
            }
        }
        if (labels) {
            s.text("ASTERN", r.x + 1.0f, cy + 2.0f, Tokens.mix(k.ink2(), Tokens.LIMIT, 0.45f),
                    Text.META);
            s.textRight("AHEAD", r.right() - 1.0f, cy + 2.0f, k.ink2(), Text.META);
        }
        s.advanceLayer();
        if (!h.known()) {
            return;
        }

        final float answer = angleOf(h.engine());
        final float order = angleOf(h.order());
        final float ax = (float) Math.cos(answer);
        final float ay = (float) Math.sin(answer);
        final float base = Math.max(2.5f, radius * 0.07f);
        s.tri(cx + ax * radius * 0.84f, cy + ay * radius * 0.84f, k.answerNeedle(), cx - ay * base, cy + ax * base,
                k.answerNeedle(), cx + ay * base, cy - ax * base, k.answerNeedle());
        Shapes.line(s, cx, cy, cx + (float) Math.cos(order) * radius * 0.97f, cy + (float) Math.sin(order) * radius * 0.97f,
                Math.max(1.4f, radius * 0.035f), k.orderNeedle());
        Shapes.circle(s, cx, cy, Math.max(3.0f, radius * 0.09f), k.dialRim());

        final double rung = f.rungAgo();
        if (rung >= 0.0 && rung < Motion.TELEGRAPH_RING && !Motion.reducedMotion()) {
            final float t = (float) (rung / Motion.TELEGRAPH_RING);
            s.advanceLayer();
            Shapes.arc(s, cx, cy, radius + 2.0f + 4.0f * t, 1.5f, PI, PI, Tokens.alpha(k.ink(), 0.75f * (1.0f - t)));
            bell(s, cx, cy - radius * 0.30f, Math.max(3.5f, radius * 0.13f), Tokens.alpha(k.ink(), 1.0f - t * t));
        }
    }

    static void bell(PanelSurface s, float x, float y, float b, int col) {
        Shapes.circle(s, x, y - b * 0.25f, b * 0.55f, col);
        s.tri(x - b * 0.55f, y - b * 0.25f, col, x + b * 0.55f, y - b * 0.25f, col, x + b * 0.95f, y + b * 0.55f, col);
        s.tri(x - b * 0.55f, y - b * 0.25f, col, x + b * 0.95f, y + b * 0.55f, col, x - b * 0.95f, y + b * 0.55f, col);
        Shapes.circle(s, x, y + b * 0.8f, b * 0.22f, col);
    }

    static float angleOf(double order) {
        final double o = AirframeMath.clamp(Double.isFinite(order) ? order : 0.0, -1.0, 1.0);
        final double[] n = HelmWords.NOTCHES;
        double pos = 3.0;
        for (int i = 0; i + 1 < n.length; i++) {
            if (o >= n[i] && o <= n[i + 1]) {
                pos = i + (o - n[i]) / (n[i + 1] - n[i]);
                break;
            }
        }
        return PI + (float) ((pos + 0.5) * Math.PI / 7.0);
    }

    static void orderWords(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        if (!h.known()) {
            s.textCenter("no telegraph reported", r.centerX(), r.y + 4.0f, k.ink3(), Text.META);
            return;
        }
        final String order = HelmWords.order(h.order());
        final float scale = s.textWidth(order, Text.SUB) <= r.w - 8.0f ? Text.SUB : Text.META;
        s.textCenter(order, r.centerX(), r.y + 1.0f, k.ink(), scale);
        final String answer = h.braking() && Math.abs(h.order()) < 1.0e-6 ? "engines braking"
                : "engines " + Math.round(Math.abs(h.engine()) * 100.0) + "%" + (h.engine() < -1.0e-3 ? " astern" : "");

        final String speed = Double.isFinite(h.fullAhead()) ? ", full ahead " + tenth(h.fullAhead()) + " b/s" : "";
        final String line = s.textWidth(answer + speed, Text.META) <= r.w - 8.0f ? answer + speed : answer;
        s.textCenter(Text.truncate(s, line, r.w - 8.0f, Text.META), r.centerX(), r.y + 17.0f, k.ink2(), Text.META);
    }

    public static void altitudeTape(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        Shapes.card(s, r, 5.0f, k.tapeFace(), k.cardEdge());
        final Rect in = r.inset(1.0f, 3.0f, 1.0f, 3.0f);
        final double y = rd.altitude;
        if (!Double.isFinite(y)) {
            s.textCenterV("--", r.centerX(), r.centerY(), k.tapeInk(), Text.META);
            return;
        }
        final float ppb = Math.max(2.0f, Math.min(5.0f, in.h / 44.0f));
        final float mid = in.centerY();
        final AerialSnapshot.Helm h = rd.snapshot.helm();

        final double floor = Double.isFinite(rd.groundY) ? rd.groundY + rd.keelDrop : Double.NaN;
        if (Double.isFinite(floor)) {
            final float gy = mid - (float) (floor - y) * ppb;
            if (gy < in.bottom()) {
                final float top = Math.max(in.y, gy);
                Shapes.rect(s, in.x, top, in.w, in.bottom() - top, Tokens.alpha(k.earth(), 0.9f));
                Shapes.rect(s, in.x, top, in.w, 1.5f, k.earthEdge());
            }
        }

        final double ceiling = ceilingOf(rd);
        if (Double.isFinite(ceiling)) {
            final float cyl = mid - (float) (ceiling - y) * ppb;
            if (cyl > in.y) {
                final float bottom = Math.min(in.bottom(), cyl);
                for (float yy = in.y; yy < bottom; yy += 4.0f) {
                    AeroInstruments.clippedLine(s, in.x, yy + 4.0f, in.right(), yy, new Rect(in.x, in.y, in.w, bottom - in.y),
                            1.0f, Tokens.alpha(k.limit(), 0.55f));
                }
                if (cyl < in.bottom()) {
                    Shapes.rect(s, in.x, cyl - 0.75f, in.w, 1.5f, k.limit());
                }
            }
        }

        final double lo = y - (mid - in.y) / ppb;
        final double hi = y + (in.bottom() - mid) / ppb;
        for (double v = Math.floor(lo / 2.0) * 2.0; v <= hi + 2.0; v += 2.0) {
            final float ty = mid - (float) (v - y) * ppb;
            if (ty <= in.y + 1.0f || ty >= in.bottom() - 1.0f) {
                continue;
            }
            final boolean major = Math.round(v) % 10 == 0;
            Shapes.rect(s, in.right() - (major ? 7.0f : 4.0f), ty - 0.5f, major ? 7.0f : 4.0f, 1.0f,
                    Tokens.alpha(k.tapeInk(), major ? 0.9f : 0.5f));
            if (major && Math.abs(ty - mid) > 12.0f && ty - 5.0f > in.y && ty + 5.0f < in.bottom()) {
                s.textRightV(Long.toString(Math.round(v)), in.right() - 9.0f, ty, Tokens.alpha(k.tapeInk(), 0.85f), Text.META);
            }
        }

        final double held = h.known() ? h.heldAltitude() : rd.snapshot.autopilot().targetAltitude();
        if (Double.isFinite(held)) {
            final float hy = (float) AirframeMath.clamp(mid - (held - y) * ppb, in.y + 4.0, in.bottom() - 4.0);
            s.tri(in.x, hy - 4.5f, k.held(), in.x + 7.0f, hy, k.held(), in.x, hy + 4.5f, k.held());
            for (float x = in.x + 9.0f; x < in.right() - 8.0f; x += 5.0f) {
                Shapes.rect(s, x, hy - 0.75f, 3.0f, 1.5f, k.held());
            }
        }

        final String text = whole(y);
        final float bh = 17.0f;
        final Rect box = new Rect(in.x + 1.0f, mid - bh * 0.5f, in.w - 2.0f, bh);
        Shapes.roundedRect(s, box, 2.0f, k.window());
        Shapes.roundedRectOutline(s, box, 2.0f, 1.0f, Tokens.LINE_HI);
        final float scale = s.textWidth(text, Text.SUB) <= box.w - 4.0f ? Text.SUB : Text.META;
        s.textCenterV(text, box.centerX(), box.centerY(), k.windowInk(), scale);
    }

    public static void liftGauge(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect in = r.inset(8.0f, 5.0f, 8.0f, 5.0f);
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final boolean aerial = h.known() && Double.isFinite(h.liftUse());
        final double use = aerial ? h.liftUse() : rd.support;
        final String pct = AeroInstruments.pct(use);
        final String title = aerial && s.textWidth("LIFT IN USE " + pct, Text.META) + 4.0f <= in.w ? "LIFT IN USE" : "LIFT";
        s.text(title, in.x, in.y, k.title(), Text.META);
        s.textRight(pct, in.right(), in.y, h.liftShort() ? Tokens.CRITICAL : k.ink(), Text.META);
        final Rect bar = new Rect(in.x, in.y + 12.0f, in.w, 6.0f);
        Shapes.roundedRect(s, bar, 2.0f, Tokens.SURFACE);
        if (Double.isFinite(use)) {
            Shapes.roundedRect(s, new Rect(bar.x, bar.y, bar.w * (float) AirframeMath.clamp(use, 0.0, 1.0), bar.h), 2.0f,
                    h.liftShort() ? Tokens.CRITICAL : k.gas());
        }
        if (aerial) {
            final float mark = bar.x + bar.w * 0.9f;
            Shapes.rect(s, mark - 0.5f, bar.y - 2.0f, 1.0f, bar.h + 4.0f, k.limit());
        }
        final String figure = aerial && Double.isFinite(h.ceiling()) ? "ceiling " + whole(h.ceiling()) : null;
        final String under = aerial
                ? (figure != null ? figure + ", " + HelmWords.ceilingReason(h)
                : h.ceiling() == Double.NEGATIVE_INFINITY ? "too heavy for its balloons" : "of the balloons' most")
                : (Double.isFinite(rd.support) ? "of the weight carried" : "no lift reported");

        final String fits = s.textWidth(under, Text.META) <= in.w ? under
                : figure != null && s.textWidth(figure, Text.META) <= in.w ? figure : null;
        if (fits != null) {
            s.text(fits, in.x, bar.bottom() + 4.0f, k.ink3(), Text.META);
        }
    }

    static void alertList(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect in = r.inset(8.0f, 6.0f, 8.0f, 6.0f);
        final List<AerialAlerts.Alert> alerts = f.alerts();
        if (alerts.isEmpty()) {
            s.text("Nothing wrong", in.x, in.y, Tokens.GOOD, Text.META);
            return;
        }
        float y = in.y;
        for (final AerialAlerts.Alert a : alerts) {
            if (y + 10.0f > in.bottom()) {
                break;
            }
            final int col = a.level() == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                    : a.level() == AerialAlerts.Level.CAUTION ? Tokens.CAUTION : k.ink3();
            Shapes.circle(s, in.x + 2.5f, y + 4.5f, 2.5f, col);
            final String name = a.text().contains("  ") ? a.text().substring(0, a.text().indexOf("  ")) : a.text();
            s.text(Text.truncate(s, name, in.w - 8.0f, Text.META), in.x + 8.0f, y, col == k.ink3() ? k.ink2() : col, Text.META);
            y += 12.0f;
        }
    }

    public static void headline(PanelSurface s, Rect r, AerialFrame f, AeroSkin k, float scale) {
        final AerialText.Headline head = AerialText.headline(f.readout(), f.alerts());
        final String text = head != null ? head.text() : fallbackLine(f.readout());
        final int col = head == null || head.level() == null ? k.ink()
                : head.level() == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL : Tokens.CAUTION;
        final float sc = s.textWidth(text, scale) <= r.w - 4.0f ? scale : Text.META;
        final float mark = head != null && head.level() != null ? 10.0f : 0.0f;
        if (mark > 0.0f) {
            Shapes.roundedRect(s, new Rect(r.x, r.centerY() - 5.0f, 4.0f, 10.0f), 1.5f, col);
        }
        s.textV(Text.truncate(s, text, r.w - 4.0f - mark, sc), r.x + mark, r.centerY(), col, sc);
    }

    static String fallbackLine(AirframeReadout rd) {
        return String.format(Locale.ROOT, "ALTITUDE %s, %s b/s %s", whole(rd.altitude), tenth(Math.abs(rd.verticalSpeed)),
                rd.verticalSpeed >= 0.0 ? "UP" : "DOWN");
    }

    public static void log(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final float listW = r.w >= 440.0f ? Math.min(250.0f, r.w * 0.42f) : 0.0f;
        voyageChart(s, new Rect(r.x, r.y, r.w - (listW > 0.0f ? listW + GAP : 0.0f), r.h), f, k);
        if (listW > 0.0f) {
            eventList(s, new Rect(r.right() - listW, r.y, listW, r.h), f, k);
        }
    }

    public static void voyageChart(PanelSurface s, Rect chart, AerialFrame f, AeroSkin k) {
        final AerialSnapshot snap = f.snapshot();
        final AerialSnapshot.Voyage v = snap.voyage();
        Shapes.card(s, chart, 6.0f, k.card(), k.cardEdge());
        final Rect in = chart.inset(10.0f, 8.0f, 10.0f, 8.0f);
        final String title = v.aloft() ? "THIS FLIGHT" : (v.last() != null ? "THE LAST FLIGHT" : "NO FLIGHT YET");
        s.text(title, in.x, in.y, k.title(), Text.META);
        final double seconds = v.aloft() ? v.seconds() : (v.last() != null ? v.last().seconds() : 0.0);
        final double dist = v.aloft() ? v.distance() : (v.last() != null ? v.last().distance() : 0.0);
        final double top = v.aloft() ? v.highest() : (v.last() != null ? v.last().highest() : Double.NaN);
        final String time = AerialAlerts.duration(seconds) + " aloft";
        final String far = dist >= 1000.0 ? String.format(Locale.ROOT, "%.1f km", dist / 1000.0) : Math.round(dist) + " b";

        final float room = in.w - s.textWidth(title, Text.META) - 10.0f;
        final String totals = AeroTabs.first(s, room, time + "  " + far + "  highest " + whole(top), time + "  " + far, time);
        s.textRight(totals, in.right(), in.y, k.ink2(), Text.META);
        final Rect plot = new Rect(in.x + 24.0f, in.y + 18.0f, in.w - 24.0f, in.h - 30.0f);
        final float[] trace = v.trace();
        if (trace.length < 2) {
            s.textCenterV("Fly and the story fills in", plot.centerX(), plot.centerY(), k.ink3(), Text.META);
        } else {
            final double[] range = AeroInstruments.range(trace, 0.12);
            AeroInstruments.spark(s, plot, trace, k.held(), range[0], range[1], Double.NaN, true);
            s.textRightV(whole(range[1]), plot.x - 4.0f, plot.y + 4.0f, k.ink3(), Text.META);
            s.textRightV(whole(range[0]), plot.x - 4.0f, plot.bottom() - 4.0f, k.ink3(), Text.META);

            final long t0 = v.traceLast() - (long) (trace.length - 1) * v.tracePeriod();
            final double span = Math.max(1.0, (double) v.traceLast() - t0);
            for (final AerialSnapshot.Event e : v.events()) {
                if (e.tick() < t0 || e.tick() > v.traceLast() || !Float.isFinite(e.altitude())) {
                    continue;
                }
                final float x = plot.x + (float) ((e.tick() - t0) / span) * plot.w;
                final float y = plot.bottom() - (float) AirframeMath.clamp((e.altitude() - range[0]) / (range[1] - range[0]),
                        0.0, 1.0) * plot.h;
                final int col = eventColor(e, k);
                Shapes.rect(s, x - 0.5f, Math.min(y, plot.bottom() - 4.0f), 1.0f, plot.bottom() - Math.min(y, plot.bottom() - 4.0f),
                        Tokens.alpha(col, 0.5f));
                Shapes.circle(s, x, y, 2.5f, col);
            }
            s.text("take-off", plot.x, plot.bottom() + 3.0f, k.ink3(), Text.META);
            s.textRight(v.aloft() ? "now" : "landed", plot.right(), plot.bottom() + 3.0f, k.ink3(), Text.META);
        }
    }

    public static void eventList(PanelSurface s, Rect list, AerialFrame f, AeroSkin k) {
        final AerialSnapshot snap = f.snapshot();
        final AerialSnapshot.Voyage v = snap.voyage();
        Shapes.card(s, list, 6.0f, k.card(), k.cardEdge());
        final Rect li = list.inset(10.0f, 8.0f, 10.0f, 8.0f);
        s.text("WHAT HAPPENED", li.x, li.y, k.title(), Text.META);
        float y = li.y + 16.0f;
        final long start = v.aloft() ? v.takeoff() : (v.last() != null ? v.last().takeoff() : snap.gameTime());
        for (int i = v.events().size() - 1; i >= 0 && y + 10.0f <= li.bottom(); i--) {
            final AerialSnapshot.Event e = v.events().get(i);
            final String when = AerialAlerts.duration(Math.max(0L, e.tick() - start) / 20.0);
            final int col = eventColor(e, k);
            Shapes.circle(s, li.x + 2.5f, y + 4.5f, 2.5f, col);
            s.text(when, li.x + 9.0f, y, k.ink3(), Text.META);

            final List<String> lines = Text.wrap(s, e.text(), li.w - 52.0f, Text.META);
            final int ink = e.kind() == AerialSnapshot.Event.ALERT ? col : k.ink();
            if (lines.size() >= 2 && y + 23.0f <= li.bottom()) {
                s.text(lines.get(0), li.x + 50.0f, y, ink, Text.META);
                final String rest = String.join(" ", lines.subList(1, lines.size()));
                s.text(Text.truncate(s, rest, li.w - 52.0f, Text.META), li.x + 50.0f, y + 10.0f, ink, Text.META);
                y += 10.0f;
            } else {
                s.text(Text.truncate(s, e.text(), li.w - 52.0f, Text.META), li.x + 50.0f, y, ink, Text.META);
            }
            y += 13.0f;
        }
        if (v.events().isEmpty()) {
            s.text("Nothing yet", li.x, y, k.ink3(), Text.META);
        }
    }

    static int eventColor(AerialSnapshot.Event e, AeroSkin k) {
        return switch (e.kind()) {
            case AerialSnapshot.Event.ORDER -> k.engine();
            case AerialSnapshot.Event.ALERT -> e.level() >= 2 ? Tokens.CRITICAL : Tokens.CAUTION;
            case AerialSnapshot.Event.GUARD, AerialSnapshot.Event.WEIGHED, AerialSnapshot.Event.LOAD -> k.held();
            case AerialSnapshot.Event.ARRIVED -> k.destination();
            default -> k.ink2();
        };
    }

    public static double gasFillOf(AerialSnapshot snap) {
        return gasFill(snap);
    }

    public static String modeLineOf(AirframeReadout rd) {
        return modeLine(rd);
    }

    public static int heightColorOf(AerialFrame f, AeroSkin k) {
        return heightColor(f, k);
    }

    public static int vsColorOf(AerialFrame f, AeroSkin k) {
        return vsColor(f, k);
    }

    static boolean inside(Rect r, float x, float y) {
        return x >= r.x && x <= r.right() && y >= r.y && y <= r.bottom();
    }

    static String whole(double v) {
        return Double.isFinite(v) ? Long.toString(Math.round(v)) : "--";
    }

    static String tenth(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.1f", v) : "--";
    }

    static String signed(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%+.1f", Math.abs(v) < 0.05 ? 0.0 : v) : "--";
    }
}
