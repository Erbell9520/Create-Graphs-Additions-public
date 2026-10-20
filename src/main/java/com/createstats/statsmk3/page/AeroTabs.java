package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.FlightCharts;
import com.createstats.aerial.HelmWords;
import com.createstats.aerial.V3;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AeroTabs {
    static final float GAP = AeroBridge.GAP;
    private static final float ROW = 12.0f;

    private AeroTabs() {
    }

    static Rect titled(PanelSurface s, Rect r, String title, String note, AeroSkin k) {
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect in = r.inset(9.0f, 7.0f, 9.0f, 6.0f);
        s.text(fit(s, title, in.w), in.x, in.y, k.title(), Text.META);

        if (note != null && !note.isEmpty() && s.textWidth(note, Text.META) <= in.w - s.textWidth(title, Text.META) - 10.0f) {
            s.textRight(note, in.right(), in.y, k.ink3(), Text.META);
        }
        return new Rect(in.x, in.y + 14.0f, in.w, Math.max(0.0f, in.h - 14.0f));
    }

    public static String fit(PanelSurface s, String text, float w) {
        if (text == null || text.isEmpty() || w <= 0.0f) {
            return "";
        }
        if (s.textWidth(text, Text.META) <= w) {
            return text;
        }
        return s.textWidth("...", Text.META) + 4.0f <= w ? Text.truncate(s, text, w, Text.META) : "";
    }

    public static String first(PanelSurface s, float w, String... options) {
        for (final String o : options) {
            if (o != null && s.textWidth(o, Text.META) <= w) {
                return o;
            }
        }
        return "";
    }

    static void centred(PanelSurface s, Rect r, String line, int color) {
        final List<String> lines = Text.wrap(s, line, r.w - 8.0f, Text.META);
        float y = r.centerY() - lines.size() * 5.5f;
        for (final String l : lines) {
            if (y + 10.0f > r.bottom()) {
                break;
            }
            if (s.textWidth(l, Text.META) <= r.w) {
                s.textCenter(l, r.centerX(), y, color, Text.META);
            }
            y += 11.0f;
        }
    }

    static void dashesH(PanelSurface s, Rect r, float y, int color, float dash, float gap) {
        if (y < r.y || y > r.bottom()) {
            return;
        }
        for (float x = r.x + 1.0f; x < r.right() - 1.0f; x += dash + gap) {
            Shapes.rect(s, x, y - 0.75f, Math.min(dash, r.right() - 1.0f - x), 1.5f, color);
        }
    }

    static void dashesV(PanelSurface s, Rect r, float x, int color, float dash, float gap) {
        if (x < r.x || x > r.right()) {
            return;
        }
        for (float y = r.y + 1.0f; y < r.bottom() - 1.0f; y += dash + gap) {
            Shapes.rect(s, x - 0.5f, y, 1.0f, Math.min(dash, r.bottom() - 1.0f - y), color);
        }
    }

    static void chartLabel(PanelSurface s, Rect r, String text, float x, float y, int color) {
        if (text.isEmpty()) {
            return;
        }
        final float tw = s.textWidth(text, Text.META);
        if (tw > r.w - 4.0f) {
            return;
        }
        float tx = x + 4.0f;
        if (tx + tw > r.right() - 2.0f) {
            tx = x - 4.0f - tw;
        }
        tx = Math.max(r.x + 2.0f, Math.min(tx, r.right() - 2.0f - tw));
        final float ty = Math.max(r.y + 1.0f, Math.min(y, r.bottom() - 10.0f));
        Shapes.roundedRect(s, new Rect(tx - 2.0f, ty - 1.5f, tw + 4.0f, 11.0f), 2.0f, Tokens.alpha(Tokens.CANVAS, 0.72f));
        s.text(text, tx, ty, color, Text.META);
    }

    static double niceStep(double raw) {
        if (!(raw > 0.0) || !Double.isFinite(raw)) {
            return 1.0;
        }
        final double mag = Math.pow(10.0, Math.floor(Math.log10(raw)));
        final double n = raw / mag;
        return (n <= 1.0 ? 1.0 : n <= 2.0 ? 2.0 : n <= 2.5 ? 2.5 : n <= 5.0 ? 5.0 : 10.0) * mag;
    }

    static String kpg(double v) {
        return Double.isFinite(v) ? Text.group(Math.round(v)) + " kpg" : "--";
    }

    static int groupInk(String group, AeroSkin k) {
        return switch (group) {
            case "balloon_lift" -> k.gas();
            case "propulsion" -> k.engine();
            case "lift" -> AeroInstruments.BLUE;
            case "levitation" -> AeroInstruments.VIOLET;
            case "magnetic_force" -> AeroInstruments.ROSE;
            default -> k.ink3();
        };
    }

    static String groupWord(String group) {
        return switch (group) {
            case "balloon_lift" -> "balloons";
            case "propulsion" -> "props";
            case "lift" -> "sails";
            case "levitation" -> "levitite";
            case "magnetic_force" -> "magnets";
            default -> "other";
        };
    }

    static float columnWidth(Rect r) {
        return Math.max(172.0f, Math.min(r.w * 0.40f, 290.0f));
    }

    public static void lift(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final float colW = columnWidth(r);
        final Rect chart = new Rect(r.x, r.y, r.w - colW - GAP, r.h);
        ceilingChart(s, chart, rd, k);
        final Rect col = new Rect(chart.right() + GAP, r.y, colW, r.h);
        final Rect held = new Rect(col.x, col.y, col.w, 60.0f);
        heldUpBy(s, held, rd, k);
        final Rect room = new Rect(col.x, held.bottom() + GAP, col.w, 50.0f);
        room(s, room, rd, k);
        final float left = col.bottom() - room.bottom() - GAP;
        if (left >= 38.0f) {
            balloons(s, new Rect(col.x, room.bottom() + GAP, col.w, left), rd, k);
        }
    }

    public static void ceilingChart(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final Rect in = titled(s, r, "CEILING", "as the Aerial reckons", k);
        if (in.h < 60.0f || in.w < 110.0f) {
            return;
        }
        if (!h.known()) {
            centred(s, in, "This backend does not say where its ceiling is", k.ink3());
            return;
        }
        final double bc = h.balloonCeiling();
        if (bc == Double.POSITIVE_INFINITY) {
            centred(s, in, "The ship holds itself up without its balloons: they set no ceiling", k.ink2());
            return;
        }
        if (bc == Double.NEGATIVE_INFINITY) {
            centred(s, in, "Too heavy for its balloons at any height: lighten it or add burners", Tokens.CRITICAL);
            return;
        }
        final double must = FlightCharts.mustHold(rd);
        if (!Double.isFinite(bc) || !Double.isFinite(must) || !(rd.lapse > 0.0)) {
            centred(s, in, "No balloon with a burner under it: nothing to reckon a ceiling from", k.ink3());
            return;
        }
        final double alt = rd.altitude;
        final double held = h.heldAltitude();
        final double ceiling = h.ceiling();
        final double cap = h.cap();
        final double worldTop = h.worldTop();

        double top = Math.max(bc, alt);
        double bottom = Math.min(bc, alt);
        for (final double v : new double[]{held, ceiling, cap}) {
            if (Double.isFinite(v)) {
                top = Math.max(top, v);
                bottom = Math.min(bottom, v);
            }
        }
        if (Double.isFinite(rd.groundY)) {
            bottom = Math.min(bottom, rd.groundY);
        }
        final double span = Math.max(40.0, top - bottom);
        top += span * 0.14;
        bottom -= span * 0.08;
        if (Double.isFinite(worldTop)) {
            top = Math.min(top, worldTop + 6.0);
        }
        if (top - bottom < 20.0) {
            top = bottom + 20.0;
        }
        final float axisW = 26.0f;
        final float captionH = in.h >= 120.0f ? 12.0f : 0.0f;
        final Rect plot = new Rect(in.x + axisW, in.y + 2.0f, in.w - axisW - 2.0f, in.h - 4.0f - captionH);
        Shapes.rect(s, plot, Tokens.mix(k.card(), Tokens.CANVAS, 0.55f));
        final double lo = bottom;
        final double hi = top;
        final java.util.function.DoubleUnaryOperator yOf = v -> plot.bottom() - (v - lo) / (hi - lo) * plot.h;
        final double xMax = must * AirframeMath.clamp(FlightCharts.usableAt(bottom, bc, rd.lapse) * 1.06, 1.5, 3.0);
        final java.util.function.DoubleUnaryOperator xOf = v -> plot.x + v / xMax * plot.w;

        final float altY = Double.isFinite(alt) && alt < hi && alt > lo ? (float) yOf.applyAsDouble(alt) : Float.NaN;
        final double step = niceStep((hi - lo) / Math.max(2.0, Math.floor(plot.h / 30.0)));
        for (double v = Math.ceil(lo / step) * step; v <= hi; v += step) {
            final float y = (float) yOf.applyAsDouble(v);
            Shapes.rect(s, plot.x, y - 0.5f, plot.w, 1.0f, Tokens.alpha(Tokens.LINE, 0.8f));
            if (y - 5.0f > plot.y && y + 5.0f < plot.bottom() && (Float.isNaN(altY) || Math.abs(y - altY) > 11.0f)) {
                s.textRightV(Long.toString(Math.round(v)), plot.x - 4.0f, y, k.ink3(), Text.META);
            }
        }
        s.advanceLayer();

        if (Double.isFinite(rd.groundY) && rd.groundY > lo) {
            final float gy = (float) Math.max(plot.y, yOf.applyAsDouble(rd.groundY));
            Shapes.rect(s, plot.x, gy, plot.w, plot.bottom() - gy, k.earth());
            Shapes.rect(s, plot.x, gy, plot.w, 1.5f, k.earthEdge());
        }

        final int bands = Math.max(16, (int) (plot.h / 3.0f));
        final float[] pts = new float[(bands + 1) * 2];
        int n = 0;
        for (int i = 0; i <= bands; i++) {
            final double y = lo + (hi - lo) * i / bands;
            final double x = FlightCharts.holdAt(y, bc, rd.lapse, must);
            final float sx = (float) Math.min(plot.right(), xOf.applyAsDouble(x));
            final float sy = (float) yOf.applyAsDouble(y);
            if (i < bands) {
                final float sy2 = (float) yOf.applyAsDouble(lo + (hi - lo) * (i + 1) / bands);
                Shapes.rect(s, plot.x, sy2, Math.max(0.0f, sx - plot.x), sy - sy2, Tokens.alpha(k.gas(), 0.13f));
            }
            if (xOf.applyAsDouble(x) <= plot.right()) {
                pts[n * 2] = sx;
                pts[n * 2 + 1] = sy;
                n++;
            }
        }
        s.advanceLayer();
        if (n >= 2) {
            Shapes.polyline(s, pts, n, 1.8f, k.gas());
        }

        if (Double.isFinite(ceiling)) {
            final float cy = (float) yOf.applyAsDouble(ceiling);
            if (cy > plot.y) {
                final Rect hatch = new Rect(plot.x, plot.y, plot.w, Math.min(plot.h, cy - plot.y));
                for (float y = hatch.y; y < hatch.bottom() + hatch.w; y += 6.0f) {
                    AeroInstruments.clippedLine(s, hatch.x, y, hatch.x + hatch.w, y - hatch.w * 0.35f, hatch, 1.0f,
                            Tokens.alpha(k.limit(), 0.22f));
                }
            }
        }

        final float mx = (float) xOf.applyAsDouble(must);
        Shapes.rect(s, mx - 0.75f, plot.y, 1.5f, plot.h, k.ink2());
        s.advanceLayer();

        if (Double.isFinite(cap) && cap < hi && cap > lo && !(Double.isFinite(ceiling) && Math.abs(cap - ceiling) < 0.5)) {
            final float y = (float) yOf.applyAsDouble(cap);
            dashesH(s, plot, y, k.ink3(), 4.0f, 3.0f);
            chartLabel(s, plot, "your cap " + AeroBridge.whole(cap), plot.x + 2.0f, y - 12.0f, k.ink2());
        }
        if (Double.isFinite(worldTop) && worldTop < hi && worldTop > lo) {
            final float y = (float) yOf.applyAsDouble(worldTop);
            dashesH(s, plot, y, k.ink3(), 2.0f, 3.0f);
        }
        if (Double.isFinite(ceiling) && ceiling < hi && ceiling > lo) {
            final float y = (float) yOf.applyAsDouble(ceiling);
            dashesH(s, plot, y, k.limit(), 6.0f, 3.0f);
            final String why = HelmWords.ceilingReason(h);
            chartLabel(s, plot, first(s, plot.w - 8.0f, "ceiling " + AeroBridge.whole(ceiling) + ": " + why,
                    "ceiling " + AeroBridge.whole(ceiling)), plot.x + 2.0f, y - 12.0f, k.limit());
        }
        if (Double.isFinite(held) && held < hi && held > lo) {
            final float y = (float) yOf.applyAsDouble(held);
            dashesH(s, plot, y, k.held(), 5.0f, 3.0f);
        }
        if (Double.isFinite(alt) && alt < hi && alt > lo) {
            final float y = (float) yOf.applyAsDouble(alt);
            final double holdNow = FlightCharts.holdAt(alt, bc, rd.lapse, must);
            final float hx = (float) Math.min(plot.right(), xOf.applyAsDouble(holdNow));

            final int roomInk = holdNow >= must ? Tokens.GOOD : Tokens.CRITICAL;
            Shapes.rect(s, Math.min(mx, hx), y - 1.5f, Math.abs(hx - mx), 3.0f, roomInk);

            final String now = AeroBridge.whole(alt);
            final float bw = Math.min(axisW, s.textWidth(now, Text.META) + 6.0f);
            final Rect box = new Rect(plot.x - bw - 1.0f, y - 6.0f, bw, 12.0f);
            Shapes.roundedRect(s, box, 2.0f, k.window());
            Shapes.roundedRectOutline(s, box, 2.0f, 1.0f, k.ink());
            if (s.textWidth(now, Text.META) <= bw - 2.0f) {
                s.textCenterV(now, box.centerX(), box.centerY(), k.ink(), Text.META);
            }
            s.tri(plot.x, y - 4.0f, k.ink(), plot.x + 6.0f, y, k.ink(), plot.x, y + 4.0f, k.ink());

            if (Double.isFinite(h.liftUse())) {
                final double asked = h.liftUse() * holdNow / FlightCharts.RESERVE;
                final float ax = (float) xOf.applyAsDouble(asked);
                if (ax >= plot.x && ax <= plot.right()) {
                    Shapes.circle(s, ax, y, 3.0f, k.ink());
                    Shapes.circle(s, ax, y, 1.6f, k.gas());
                }
            }
        }

        final String mustLabel = first(s, plot.right() - mx - 6.0f, "must hold " + kpg(must), kpg(must));
        chartLabel(s, plot, mustLabel.isEmpty() ? kpg(must) : mustLabel, mx, plot.bottom() - 14.0f, k.ink2());
        if (captionH > 0.0f) {
            s.text(first(s, in.w, "kpg the balloons can hold at each height, a tenth kept back",
                    "kpg the balloons can hold, a tenth kept back", "kpg they can hold"), in.x, plot.bottom() + 3.0f,
                    k.ink3(), Text.META);
        }
    }

    public static void heldUpBy(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final boolean measured = Double.isFinite(rd.support) && !rd.shares.isEmpty();
        final Rect in = titled(s, r, "HELD UP BY", measured ? "measured" : "", k);
        if (!measured) {
            s.text(fit(s, "Sable has reported no forces yet", in.w), in.x, in.y + 1.0f, k.ink3(), Text.META);
            s.text(fit(s, "they are read while a panel shows the ship", in.w), in.x, in.y + 13.0f, k.ink3(), Text.META);
            return;
        }
        final double max = Math.max(1.25, rd.support + 0.08);
        final Rect bar = new Rect(in.x, in.y + 1.0f, in.w, 9.0f);
        Shapes.roundedRect(s, bar, 2.0f, Tokens.SURFACE);
        float x = bar.x;
        final List<AirframeReadout.Share> parts = new ArrayList<>();
        for (final AirframeReadout.Share sh : rd.shares) {
            if (sh.share() > 0.005) {
                parts.add(sh);
                final float w = (float) (sh.share() / max * bar.w);
                Shapes.rect(s, x, bar.y, Math.max(0.0f, Math.min(w, bar.right() - x) - 1.0f), bar.h, groupInk(sh.group(), k));
                x += w;
            }
        }
        final float wx = bar.x + (float) (1.0 / max) * bar.w;
        Shapes.rect(s, wx - 0.75f, bar.y - 3.0f, 1.5f, bar.h + 6.0f, k.ink());

        parts.sort(Comparator.comparingDouble(AirframeReadout.Share::share).reversed());
        float lx = in.x;
        for (final AirframeReadout.Share sh : parts) {
            final String t = groupWord(sh.group()) + " " + AeroInstruments.pct(sh.share());
            final float tw = s.textWidth(t, Text.META) + 9.0f;
            if (lx + tw > in.right()) {
                break;
            }
            Shapes.circle(s, lx + 2.5f, bar.bottom() + 8.5f, 2.5f, groupInk(sh.group(), k));
            s.text(t, lx + 7.0f, bar.bottom() + 4.0f, k.ink2(), Text.META);
            lx += tw + 6.0f;
        }
        final double reserve = rd.support - 1.0;
        final String state = Math.abs(reserve) < 0.02 ? "holding" : reserve > 0.0 ? "rising"
                : AeroInstruments.pct(-reserve) + " short";
        final String verdict = first(s, in.w, AeroInstruments.pct(rd.support) + " of the weight: " + state,
                AeroInstruments.pct(rd.support) + ": " + state);
        s.text(verdict, in.x, bar.bottom() + 17.0f,
                Math.abs(reserve) < 0.02 ? k.ink2() : reserve > 0.0 ? Tokens.GOOD : Tokens.CAUTION, Text.META);
    }

    public static void room(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final Rect in = titled(s, r, "ROOM AT THIS HEIGHT", "a tenth kept back", k);
        final double spare = h.known() ? FlightCharts.spareAt(rd.altitude, h.balloonCeiling(), rd.lapse,
                FlightCharts.mustHold(rd)) : Double.NaN;
        if (!Double.isFinite(spare)) {
            s.text(fit(s, h.known() && h.balloonCeiling() == Double.NEGATIVE_INFINITY ? "none: too heavy at any height"
                    : "not reckoned: no balloon ceiling", in.w), in.x, in.y + 3.0f, k.ink3(), Text.META);
            return;
        }
        final String figure = spare >= 0.0 ? "+" + kpg(spare) : "short " + kpg(-spare);
        final float scale = s.textWidth(figure, Text.SUB) <= in.w * 0.62f ? Text.SUB : Text.META;
        s.text(figure, in.x, in.y + 1.0f, spare >= 0.0 ? Tokens.GOOD : Tokens.CRITICAL, scale);
        final boolean loaded = Double.isFinite(rd.load) && rd.load > 0.5;
        final float lx = in.x + s.textWidth(figure, scale) + 10.0f;
        final String l = loaded ? first(s, in.right() - lx, kpg(rd.load) + " aboard", kpg(rd.load))
                : first(s, in.right() - lx, "no load weighed", "no load");
        if (!l.isEmpty()) {
            s.textRight(l, in.right(), in.y + 5.0f, loaded ? k.held() : k.ink3(), Text.META);
        }
    }

    public static void balloons(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final double fill = AeroBridge.gasFillOf(rd.snapshot);
        final Rect in = titled(s, r, "BALLOONS", Double.isFinite(fill) ? "gas " + AeroInstruments.pct(fill) : "", k);
        if (rd.balloons.isEmpty()) {
            s.text(fit(s, "No balloons reported", in.w), in.x, in.y + 1.0f, k.ink3(), Text.META);
            return;
        }
        float y = in.y + 1.0f;
        int shown = 0;
        for (final AirframeReadout.BalloonState b : rd.balloons) {
            if (y + ROW > in.bottom() + 2.0f) {
                break;
            }
            final AerialSnapshot.Balloon bl = b.balloon();
            final String name = bl.name().isEmpty() ? "Balloon " + (shown + 1) : bl.name();
            final float nameW = Math.min(in.w * 0.34f, 64.0f);
            s.text(fit(s, name, nameW), in.x, y, k.ink(), Text.META);
            final String state = b.starved() ? (b.burnersOut() ? "burners out" : "not keeping up")
                    : bl.heaters() > 0 ? bl.lit() + " of " + bl.heaters() + " lit" : "";
            final float stateW = s.textWidth(state, Text.META);
            final float barX = in.x + nameW + 4.0f;
            final float barW = in.right() - barX - stateW - 8.0f;
            if (barW >= 16.0f) {
                final Rect bar = new Rect(barX, y + 2.0f, barW, 5.0f);
                Shapes.roundedRect(s, bar, 2.0f, Tokens.SURFACE);
                final double f = bl.capacity() > 0.0 ? AirframeMath.clamp(bl.filled() / bl.capacity(), 0.0, 1.0) : 0.0;
                Shapes.roundedRect(s, new Rect(bar.x, bar.y, (float) (bar.w * f), bar.h), 2.0f,
                        b.starved() ? Tokens.CAUTION : k.gas());
                if (bl.capacity() > 0.0 && Double.isFinite(bl.target())) {
                    final float tx = bar.x + (float) (bar.w * AirframeMath.clamp(bl.target() / bl.capacity(), 0.0, 1.0));
                    Shapes.rect(s, tx - 0.5f, bar.y - 2.0f, 1.0f, bar.h + 4.0f, k.ink());
                }
            }
            if (!state.isEmpty() && stateW <= in.w - nameW - 4.0f) {
                s.textRight(state, in.right(), y, b.starved() ? Tokens.CAUTION : k.ink3(), Text.META);
            }
            y += ROW;
            shown++;
        }
        if (shown < rd.balloons.size() && y <= in.bottom()) {
            s.textRight(fit(s, "+" + (rd.balloons.size() - shown) + " more", in.w), in.right(), in.bottom() - 9.0f,
                    k.ink3(), Text.META);
        }
    }

    public static void engines(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final float colW = columnWidth(r);
        final Rect chart = new Rect(r.x, r.y, r.w - colW - GAP, r.h);
        speedWall(s, chart, rd, k);
        final Rect col = new Rect(chart.right() + GAP, r.y, colW, r.h);
        final Rect order = new Rect(col.x, col.y, col.w, 46.0f);
        telegraphCard(s, order, rd, k);
        final float fuelH = 46.0f;
        final Rect props = new Rect(col.x, order.bottom() + GAP, col.w, col.h - order.h - fuelH - GAP * 2.0f);
        if (props.h >= 36.0f) {
            propellers(s, props, rd, k);
        }
        fuel(s, new Rect(col.x, col.bottom() - fuelH, col.w, fuelH), rd, k);
    }

    // push vs drag over airspeed, where they cross is top speed
    public static void speedWall(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final FlightCharts.Drag drag = FlightCharts.drag(rd);
        final String note = switch (drag.source()) {
            case MEASURED -> "drag measured now";
            case LEARNED -> "drag as the Aerial learned it";
            case NONE -> "";
        };
        final Rect in = titled(s, r, "SPEED WALL", note, k);
        if (in.h < 60.0f || in.w < 110.0f) {
            return;
        }
        final double push0 = FlightCharts.pushAt(rd, 0.0);
        if (!(push0 > 0.0)) {
            centred(s, in, "No propeller pushes this ship forward: nothing to chart", k.ink3());
            return;
        }
        final double now = Double.isFinite(rd.indicatedAirspeed) ? Math.max(0.0, rd.indicatedAirspeed)
                : Math.max(0.0, rd.groundSpeed);
        double reach = Math.max(4.0, now);
        for (final double v : new double[]{rd.propTopOut, drag.top()}) {
            if (Double.isFinite(v) && v < 200.0) {
                reach = Math.max(reach, v);
            }
        }
        final double uStep = niceStep(reach * 1.15 / 5.0);
        final double uMax = Math.ceil(reach * 1.15 / uStep) * uStep;
        final float verdictH = 12.0f;
        final Rect plot = new Rect(in.x + 2.0f, in.y + 4.0f, in.w - 4.0f, in.h - 18.0f - verdictH);
        Shapes.rect(s, plot, Tokens.mix(k.card(), Tokens.CANVAS, 0.55f));
        final double yMax = push0 * 1.14;
        final java.util.function.DoubleUnaryOperator xOf = u -> plot.x + u / uMax * plot.w;
        final java.util.function.DoubleUnaryOperator yOf = p -> plot.bottom() - p / yMax * plot.h;

        for (double u = uStep; u < uMax - 1.0e-6; u += uStep) {
            final float x = (float) xOf.applyAsDouble(u);
            Shapes.rect(s, x - 0.5f, plot.y, 1.0f, plot.h, Tokens.alpha(Tokens.LINE, 0.8f));
        }
        for (double u = 0.0; u <= uMax + 1.0e-6; u += uStep) {
            final float x = (float) xOf.applyAsDouble(u);
            final String t = AeroInstruments.num(u, uStep < 1.0 ? 1 : 0);
            final float tw = s.textWidth(t, Text.META);
            if (x - tw * 0.5f >= in.x && x + tw * 0.5f <= in.right()) {
                s.textCenter(t, x, plot.bottom() + 3.0f, k.ink3(), Text.META);
            }
        }
        s.advanceLayer();

        final int samples = Math.max(24, (int) (plot.w / 3.0f));
        final float[] pts = new float[(samples + 1) * 2];
        for (int i = 0; i <= samples; i++) {
            final double u = uMax * i / samples;
            pts[i * 2] = (float) xOf.applyAsDouble(u);
            pts[i * 2 + 1] = (float) yOf.applyAsDouble(FlightCharts.pushAt(rd, u));
        }
        Shapes.areaFill(s, pts, samples + 1, plot.bottom(), Tokens.alpha(k.engine(), 0.22f), Tokens.alpha(k.engine(), 0.04f));
        s.advanceLayer();
        Shapes.polyline(s, pts, samples + 1, 1.8f, k.engine());
        chartLabel(s, plot, "push", plot.x + 2.0f, (float) yOf.applyAsDouble(push0) - 12.0f, k.engine());

        if (drag.source() != FlightCharts.DragSource.NONE && Double.isFinite(drag.slope()) && drag.slope() > 0.0) {
            final double uEnd = Math.min(uMax, yMax / drag.slope());
            AeroInstruments.clippedLine(s, plot.x, plot.bottom(), (float) xOf.applyAsDouble(uEnd),
                    (float) yOf.applyAsDouble(drag.slope() * uEnd), plot, 1.4f, k.ink2());
            final float ex = (float) xOf.applyAsDouble(uEnd * 0.86);
            chartLabel(s, plot, "drag", ex, (float) yOf.applyAsDouble(drag.slope() * uEnd * 0.86) - 12.0f, k.ink2());
            if (Double.isFinite(drag.top()) && drag.top() <= uMax) {
                final float tx = (float) xOf.applyAsDouble(drag.top());
                final float ty = (float) yOf.applyAsDouble(FlightCharts.pushAt(rd, drag.top()));
                Shapes.circle(s, tx, ty, 3.5f, k.ink());
                Shapes.circle(s, tx, ty, 2.0f, k.engine());
                final String top = (drag.source() == FlightCharts.DragSource.LEARNED ? "full ahead " : "top speed ")
                        + AeroBridge.tenth(drag.top()) + " b/s";
                chartLabel(s, plot, top, tx + 2.0f, ty - 14.0f, k.ink());
            }
        }

        if (Double.isFinite(rd.propTopOut) && rd.propTopOut <= uMax) {
            final float ox = (float) xOf.applyAsDouble(rd.propTopOut);
            s.tri(ox - 3.5f, plot.bottom(), k.engine(), ox + 3.5f, plot.bottom(), k.engine(), ox, plot.bottom() - 5.0f, k.engine());
        }

        final float nx = (float) xOf.applyAsDouble(Math.min(now, uMax));
        dashesV(s, plot, nx, Tokens.alpha(k.ink(), 0.85f), 3.0f, 2.0f);
        chartLabel(s, plot, "now " + AeroBridge.tenth(now), nx, plot.y + 2.0f, k.ink());

        final String units = "b/s";
        final String verdict;
        if (drag.source() == FlightCharts.DragSource.NONE) {
            verdict = first(s, in.w, "Drag not known yet: the Aerial learns it in flight", "Drag not learned yet");
        } else if (FlightCharts.propsLimit(drag.top(), rd.propTopOut)) {
            verdict = first(s, in.w,
                    "The props run out of air at " + AeroBridge.tenth(rd.propTopOut) + " " + units + ": more RPM moves the wall",
                    "Props out of air at " + AeroBridge.tenth(rd.propTopOut) + " " + units);
        } else if (Double.isFinite(rd.propTopOut)) {
            verdict = first(s, in.w,
                    "Drag stops it at " + AeroBridge.tenth(drag.top()) + " " + units + "; the props could push to "
                            + AeroBridge.tenth(rd.propTopOut),
                    "Drag stops it at " + AeroBridge.tenth(drag.top()) + " " + units);
        } else {
            verdict = first(s, in.w, "Drag stops it at " + AeroBridge.tenth(drag.top()) + " " + units);
        }
        s.text(verdict, in.x, in.bottom() - 9.0f, k.ink2(), Text.META);
        s.textRight(units, in.right(), plot.bottom() + 3.0f - 0.0f, k.ink3(), Text.META);
    }

    public static void telegraphCard(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final Rect in = titled(s, r, "TELEGRAPH", h.known() && h.rings() > 0 ? "rung " + h.rings() + " times" : "", k);
        if (!h.known()) {
            s.text(fit(s, "no telegraph reported", in.w), in.x, in.y + 3.0f, k.ink3(), Text.META);
            return;
        }
        final String order = HelmWords.order(h.order());
        final float scale = s.textWidth(order, Text.SUB) <= in.w * 0.6f ? Text.SUB : Text.META;
        s.text(order, in.x, in.y + 1.0f, k.ink(), scale);
        final String answer = h.braking() && Math.abs(h.order()) < 1.0e-6 ? "braking"
                : "engines " + Math.round(Math.abs(h.engine()) * 100.0) + "%" + (h.engine() < -1.0e-3 ? " astern" : "");
        final float ax = in.x + s.textWidth(order, scale) + 8.0f;
        final String a = fit(s, answer, in.right() - ax);
        if (!a.isEmpty()) {
            s.textRight(a, in.right(), in.y + (scale == Text.SUB ? 4.0f : 1.0f), k.engine(), Text.META);
        }
    }

    public static void propellers(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        int turning = 0;
        for (final AirframeReadout.PropState p : rd.props) {
            if (p.prop().active()) {
                turning++;
            }
        }
        final Rect in = titled(s, r, "PROPELLERS", rd.props.isEmpty() ? "" : turning + " of " + rd.props.size() + " turning", k);
        float y = in.y + 1.0f;
        int shown = 0;
        final int total = rd.props.size() + countThrusters(rd.snapshot);
        if (total == 0) {
            s.text(fit(s, "No propellers or thrusters reported", in.w), in.x, y, k.ink3(), Text.META);
            return;
        }
        final float nameW = Math.min(in.w * 0.36f, 70.0f);
        for (final AirframeReadout.PropState p : rd.props) {
            if (y + ROW > in.bottom() + 2.0f) {
                break;
            }
            final String name = p.prop().name().isEmpty() ? "Propeller" : p.prop().name();
            final boolean trouble = p.lagging() || p.overloaded();
            final int ink = !p.prop().active() ? k.ink3() : trouble ? Tokens.CAUTION : k.ink();
            s.text(fit(s, name, nameW), in.x, y, ink, Text.META);
            final String state = !p.prop().active() ? "idle"
                    : p.overloaded() ? "stressed " + AeroInstruments.pct(p.prop().shaftStress())
                    : p.lagging() ? "lagging"
                    : p.saturated() ? "out of air"
                    : Math.round(p.prop().rpm()) + " rpm";
            final float stateW = s.textWidth(state, Text.META);
            final float barX = in.x + nameW + 4.0f;
            final float barW = in.right() - barX - stateW - 8.0f;
            if (barW >= 14.0f) {
                final Rect bar = new Rect(barX, y + 2.0f, barW, 5.0f);
                Shapes.roundedRect(s, bar, 2.0f, Tokens.SURFACE);
                final float fr = (float) AirframeMath.clamp(p.delivered(), 0.0, 1.0);
                Shapes.roundedRect(s, new Rect(bar.x, bar.y, bar.w * fr, bar.h), 2.0f, trouble ? Tokens.CAUTION : k.engine());
            }
            if (stateW <= in.w - nameW - 4.0f) {
                s.textRight(state, in.right(), y, trouble ? Tokens.CAUTION : k.ink3(), Text.META);
            }
            y += ROW;
            shown++;
        }
        for (final AerialSnapshot.Part part : rd.snapshot.parts().parts()) {
            if (part.kind() != AerialSnapshot.Part.THRUSTER) {
                continue;
            }
            if (y + ROW > in.bottom() + 2.0f) {
                break;
            }
            final boolean ok = part.state() == AerialSnapshot.Part.OK;
            s.text(fit(s, part.name().isEmpty() ? "Thruster" : part.name(), nameW), in.x, y, ok ? k.ink() : Tokens.CAUTION,
                    Text.META);

            final String state = switch (part.state()) {
                case AerialSnapshot.Part.NO_POWER -> "no power";
                case AerialSnapshot.Part.NO_FUEL -> "no fuel";
                case AerialSnapshot.Part.OFF -> "off";
                default -> "thrusting " + AeroInstruments.pct(AirframeMath.clamp(part.value(), 0.0, 1.0));
            };
            s.textRight(fit(s, state, in.w - nameW - 4.0f), in.right(), y, ok ? k.ink3() : Tokens.CAUTION, Text.META);
            y += ROW;
            shown++;
        }
        if (shown < total && y <= in.bottom()) {
            s.textRight(fit(s, "+" + (total - shown) + " more", in.w), in.right(), in.bottom() - 9.0f, k.ink3(), Text.META);
        }
    }

    static int countThrusters(AerialSnapshot snap) {
        int n = 0;
        for (final AerialSnapshot.Part p : snap.parts().parts()) {
            if (p.kind() == AerialSnapshot.Part.THRUSTER) {
                n++;
            }
        }
        return n;
    }

    public static void fuel(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final AerialSnapshot.Engine e = rd.snapshot.engine();
        final Rect in = titled(s, r, "FUEL", e.count() > 0 ? e.count() + (e.count() == 1 ? " portable engine" : " portable engines")
                : "", k);
        if (e.count() == 0) {
            s.text(fit(s, "No portable engines aboard", in.w), in.x, in.y + 1.0f, k.ink3(), Text.META);
            s.text(first(s, in.w, "burner fuel is not told by Aeronautics", "burner fuel is not told"), in.x,
                    in.y + 13.0f, k.ink3(), Text.META);
            return;
        }
        if (!Double.isFinite(e.fuelSeconds())) {
            s.text(fit(s, "The engines report no fuel", in.w), in.x, in.y + 1.0f, k.ink3(), Text.META);
            return;
        }
        final String time = AerialAlerts.duration(e.fuelSeconds());
        s.text(time, in.x, in.y + 1.0f, e.fuelSeconds() < 120.0 ? Tokens.CAUTION : k.ink(), Text.SUB);
        final String sub = e.superHeated() ? "left, superheated" : "left at this burn";
        final float sx = in.x + s.textWidth(time, Text.SUB) + 8.0f;
        final String t = fit(s, sub, in.right() - sx);
        if (!t.isEmpty()) {
            s.text(t, sx, in.y + 5.0f, k.ink3(), Text.META);
        }
    }

    public static void nav(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final float side = Math.min(r.h, r.w * 0.46f);
        final Rect map = new Rect(r.x, r.y, side, r.h);
        terrainMap(s, map, rd, k);
        final Rect col = new Rect(map.right() + GAP, r.y, r.right() - map.right() - GAP, r.h);
        final float targetH = Math.min(74.0f, Math.max(62.0f, col.h * 0.36f));
        final Rect ahead = new Rect(col.x, col.y, col.w, col.h - targetH - GAP);
        groundAhead(s, ahead, rd, k);
        target(s, new Rect(col.x, ahead.bottom() + GAP, col.w, targetH), rd, k);
    }

    public static void terrainMap(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect plot = r.inset(2.0f);
        final AerialSnapshot snap = rd.snapshot;
        final AerialSnapshot.Terrain t = snap.terrain();
        final V3 own = snap.body().position();
        final double range = t.size() > 0 ? t.size() * t.cell() * 0.5 : 128.0;
        final float scale = (float) (Math.min(plot.w, plot.h) * 0.5 / range);
        final float cx = plot.centerX();
        final float cy = plot.centerY();
        final AeroInstruments.Projection proj = (x, z) -> new float[]{cx + (float) (x - own.x()) * scale,
                cy + (float) (z - own.z()) * scale};
        Shapes.rect(s, plot, Tokens.mix(Tokens.CANVAS, k.earth(), 0.35f));
        if (t.size() > 0) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < t.size() * t.size(); i++) {
                final double g = t.sample(i % t.size(), i / t.size());
                if (!Double.isNaN(g)) {
                    lo = Math.min(lo, g);
                    hi = Math.max(hi, g);
                }
            }
            final double keel = own.y() - rd.keelDrop;
            for (int iz = 0; iz < t.size(); iz++) {
                for (int ix = 0; ix < t.size(); ix++) {
                    final double g = t.sample(ix, iz);
                    if (Double.isNaN(g)) {
                        continue;
                    }
                    final float[] a = proj.at(t.originX() + ix * t.cell(), t.originZ() + iz * t.cell());
                    final float[] b = proj.at(t.originX() + (ix + 1) * t.cell(), t.originZ() + (iz + 1) * t.cell());
                    final float x0 = Math.max(plot.x, a[0]);
                    final float y0 = Math.max(plot.y, a[1]);
                    final float x1 = Math.min(plot.right(), b[0]);
                    final float y1 = Math.min(plot.bottom(), b[1]);
                    if (x1 <= x0 || y1 <= y0) {
                        continue;
                    }
                    Shapes.rect(s, x0, y0, x1 - x0, y1 - y0, groundInk(t, ix, iz, g, lo, hi, keel, k));
                }
            }
        } else {
            centred(s, plot, "The Aerial has not sent the ground round it", k.ink3());
        }
        s.advanceLayer();

        for (final double ring : new double[]{range * 0.5, range}) {
            final float rr = (float) (ring * scale);
            if (rr * 2.0f <= plot.w + 1.0f && rr * 2.0f <= plot.h + 1.0f) {
                Shapes.arc(s, cx, cy, rr, 1.0f, 0.0f, 2.0f * AeroBridge.PI, Tokens.alpha(Tokens.INK_3, 0.45f));
            }
        }

        final List<AerialSnapshot.Fix> fixes = snap.trail().fixes();
        for (int i = 1; i < fixes.size(); i++) {
            final float[] a = proj.at(fixes.get(i - 1).position().x(), fixes.get(i - 1).position().z());
            final float[] b = proj.at(fixes.get(i).position().x(), fixes.get(i).position().z());
            AeroInstruments.clippedLine(s, a[0], a[1], b[0], b[1], plot, 1.3f,
                    Tokens.alpha(k.ink(), 0.15f + 0.55f * i / fixes.size()));
        }
        float[] prev = new float[]{cx, cy};
        for (int i = 0; i < rd.predicted.size(); i++) {
            final float[] a = proj.at(rd.predicted.get(i).x(), rd.predicted.get(i).z());
            if (i % 2 == 0) {
                AeroInstruments.clippedLine(s, prev[0], prev[1], a[0], a[1], plot, 1.5f, Tokens.GOOD);
            }
            prev = a;
        }

        final List<AerialSnapshot.Waypoint> wps = snap.route().points();
        for (int i = 1; i < wps.size(); i++) {
            final float[] a = proj.at(wps.get(i - 1).position().x(), wps.get(i - 1).position().z());
            final float[] b = proj.at(wps.get(i).position().x(), wps.get(i).position().z());
            AeroInstruments.clippedLine(s, a[0], a[1], b[0], b[1], plot, 1.2f, Tokens.alpha(AeroInstruments.VIOLET, 0.8f));
        }
        for (final AerialSnapshot.Waypoint w : wps) {
            final float[] a = proj.at(w.position().x(), w.position().z());
            if (AeroBridge.inside(plot.inset(3.0f), a[0], a[1])) {
                Shapes.circle(s, a[0], a[1], 2.2f, AeroInstruments.VIOLET);
            }
        }

        for (final AirframeReadout.TrafficState tr : rd.traffic) {
            final float[] a = proj.at(tr.traffic().position().x(), tr.traffic().position().z());
            if (AeroBridge.inside(plot.inset(5.0f), a[0], a[1])) {
                AeroInstruments.diamond(s, a[0], a[1], 4.0f, trafficInk(tr), true);
            }
        }

        if (rd.nav != null && snap.target() != null) {
            final float[] tp = proj.at(snap.target().position().x(), snap.target().position().z());
            if (AeroBridge.inside(plot.inset(7.0f), tp[0], tp[1])) {
                pennant(s, tp[0], tp[1], k.destination());
            } else {
                edgeArrow(s, plot, cx, cy, tp[0], tp[1], k.destination(), Text.group(Math.round(rd.nav.distance())) + " b");
            }
        }
        s.advanceLayer();
        AeroInstruments.chevron(s, cx, cy, 7.5f, (float) Math.toRadians(rd.heading), Tokens.CANVAS);
        AeroInstruments.chevron(s, cx, cy, 6.0f, (float) Math.toRadians(rd.heading), k.ink());

        final float nx = plot.x + 8.0f;
        s.tri(nx, plot.y + 3.0f, k.ink2(), nx - 3.0f, plot.y + 7.5f, k.ink2(), nx + 3.0f, plot.y + 7.5f, k.ink2());
        s.textCenter("N", nx, plot.y + 9.0f, k.ink2(), Text.META);
        final double barM = niceStep(range * 0.4);
        final float barPx = (float) (barM * scale);
        if (barPx + 12.0f < plot.w) {
            final float bx = plot.x + 6.0f;
            final float by = plot.bottom() - 6.0f;
            Shapes.rect(s, bx, by, barPx, 1.5f, k.ink2());
            Shapes.rect(s, bx, by - 3.0f, 1.0f, 4.5f, k.ink2());
            Shapes.rect(s, bx + barPx - 1.0f, by - 3.0f, 1.0f, 4.5f, k.ink2());
            final String l = Math.round(barM) + " b";
            if (s.textWidth(l, Text.META) + 4.0f <= plot.w - barPx - 12.0f) {
                s.text(l, bx + barPx + 4.0f, by - 7.0f, k.ink2(), Text.META);
            }
        }
    }

    static int groundInk(AerialSnapshot.Terrain t, int ix, int iz, double g, double lo, double hi, double keel,
                         AeroSkin k) {
        final double gx = slope(t, ix - 1, iz, ix + 1, iz, g);
        final double gz = slope(t, ix, iz - 1, ix, iz + 1, g);

        final double nx = -gx / t.cell();
        final double nz = -gz / t.cell();
        final double len = Math.sqrt(nx * nx + 4.0 + nz * nz);
        final double lit = AirframeMath.clamp(((nx * -0.55) + 2.0 * 0.64 + (nz * -0.55)) / len, 0.0, 1.0);
        final double rel = hi > lo ? (g - lo) / (hi - lo) : 0.5;
        int col = Tokens.mix(Tokens.mix(Tokens.CANVAS, k.earth(), (float) (0.55 + 0.45 * rel)), k.earthEdge(),
                (float) AirframeMath.clamp((lit - 0.45) * 1.1, 0.0, 0.7));
        col = Tokens.mix(col, Tokens.CANVAS, (float) AirframeMath.clamp((0.55 - lit) * 0.9, 0.0, 0.5));
        final double below = keel - g;
        if (below < 0.0) {
            col = Tokens.mix(col, Tokens.CRITICAL, 0.55f);
        } else if (below < 15.0) {
            col = Tokens.mix(col, Tokens.CAUTION, 0.35f);
        }
        return col;
    }

    private static double slope(AerialSnapshot.Terrain t, int ax, int az, int bx, int bz, double here) {
        final double a = inPatch(t, ax, az) ? t.sample(ax, az) : Double.NaN;
        final double b = inPatch(t, bx, bz) ? t.sample(bx, bz) : Double.NaN;
        if (Double.isNaN(a) && Double.isNaN(b)) {
            return 0.0;
        }
        if (Double.isNaN(a)) {
            return b - here;
        }
        if (Double.isNaN(b)) {
            return here - a;
        }
        return (b - a) * 0.5;
    }

    private static boolean inPatch(AerialSnapshot.Terrain t, int ix, int iz) {
        return ix >= 0 && iz >= 0 && ix < t.size() && iz < t.size();
    }

    static void pennant(PanelSurface s, float x, float y, int color) {
        Shapes.rect(s, x - 0.5f, y - 9.0f, 1.2f, 9.0f, color);
        s.tri(x + 0.5f, y - 9.0f, color, x + 7.0f, y - 6.5f, color, x + 0.5f, y - 4.0f, color);
        Shapes.circle(s, x, y, 1.8f, color);
    }

    static void edgeArrow(PanelSurface s, Rect r, float cx, float cy, float tx, float ty, int color, String label) {
        final float dx = tx - cx;
        final float dy = ty - cy;
        final float len = (float) Math.hypot(dx, dy);
        if (len < 1.0f) {
            return;
        }
        final float ux = dx / len;
        final float uy = dy / len;
        final float half = Math.min(r.w, r.h) * 0.5f - 10.0f;
        final float t = Math.min((r.w * 0.5f - 10.0f) / Math.max(1.0e-3f, Math.abs(ux)),
                (r.h * 0.5f - 10.0f) / Math.max(1.0e-3f, Math.abs(uy)));
        final float ax = cx + ux * Math.min(t, Math.max(half, t));
        final float ay = cy + uy * Math.min(t, Math.max(half, t));
        final float px = -uy;
        final float py = ux;
        s.tri(ax + ux * 5.0f, ay + uy * 5.0f, color, ax - ux * 3.0f + px * 4.0f, ay - uy * 3.0f + py * 4.0f, color,
                ax - ux * 3.0f - px * 4.0f, ay - uy * 3.0f - py * 4.0f, color);
        chartLabel(s, r, label, ax - ux * 10.0f, ay - uy * 10.0f - 5.0f, color);
    }

    public static void groundAhead(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final double[] ground = rd.profile;
        final double length = Math.max(1, ground.length - 1) * AirframeMath.PROFILE_STEP;
        final Rect in = titled(s, r, "GROUND AHEAD", "along the track, " + Math.round(length) + " b", k);
        if (in.h < 40.0f) {
            return;
        }
        final Rect plot = new Rect(in.x + 26.0f, in.y + 2.0f, in.w - 28.0f, in.h - 16.0f);
        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final AerialSnapshot.Guard guard = h.known() ? h.guard() : AerialSnapshot.Guard.NONE;
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (final double g : ground) {
            if (Double.isFinite(g)) {
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
            }
        }
        if (!Double.isFinite(lo)) {
            centred(s, plot, ground.length < 2 ? "No terrain sampled ahead" : "The ground ahead is not loaded", k.ink3());
            s.text(fit(s, words(rd, guard), in.w), in.x, in.bottom() - 9.0f, k.ink2(), Text.META);
            return;
        }
        final double keel = rd.altitude - rd.keelDrop;
        final double gs = Math.max(0.5, rd.groundSpeed);
        // clamp the climb or it flattens the ground into a line

        final double keelEnd = keel + AirframeMath.clamp(rd.verticalSpeed * (length / gs), -60.0, 60.0);
        double top = Math.max(hi, Math.max(keel, keelEnd));
        double bottom = Math.min(lo, Math.min(keel, keelEnd));
        if (Double.isFinite(guard.floor())) {
            top = Math.max(top, guard.floor() - rd.keelDrop);
        }
        final double pad = Math.max(6.0, (top - bottom) * 0.15);
        top += pad;
        bottom -= pad * 0.6;
        final double t0 = bottom;
        final double t1 = top;
        final java.util.function.DoubleUnaryOperator yOf = v -> plot.bottom() - (v - t0) / (t1 - t0) * plot.h;
        Shapes.rect(s, plot, Tokens.mix(k.card(), Tokens.CANVAS, 0.55f));
        final float[] pts = new float[ground.length * 2];
        int n = 0;
        for (int i = 0; i < ground.length; i++) {
            if (!Double.isFinite(ground[i])) {
                continue;
            }
            pts[n * 2] = plot.x + (float) (i * AirframeMath.PROFILE_STEP / length) * plot.w;
            pts[n * 2 + 1] = (float) yOf.applyAsDouble(ground[i]);
            n++;
        }
        if (n >= 2) {
            Shapes.areaFill(s, pts, n, plot.bottom(), k.earth(), k.earth());
            s.advanceLayer();
            Shapes.polyline(s, pts, n, 1.4f, k.earthEdge());
        }

        if (Double.isFinite(guard.floor()) && !"CLEAR".equals(guard.verdict())) {
            dashesH(s, plot, (float) yOf.applyAsDouble(guard.floor() - rd.keelDrop), k.held(), 5.0f, 3.0f);
        }

        final float ky = (float) yOf.applyAsDouble(keel);
        final float ky1 = (float) yOf.applyAsDouble(keelEnd);
        AeroInstruments.clippedLine(s, plot.x, ky, plot.right(), ky1, plot, 1.6f, k.ink());
        s.textRightV(Long.toString(Math.round(keel)), plot.x - 3.0f, Math.max(plot.y + 5.0f, Math.min(ky, plot.bottom() - 5.0f)),
                k.ink(), Text.META);
        final AirframeReadout.TerrainCheck tc = rd.terrain;
        if (Double.isFinite(tc.scanned()) && tc.scanned() < length - 1.0) {
            dashesV(s, plot, plot.x + (float) (tc.scanned() / length) * plot.w, Tokens.alpha(k.ink3(), 0.8f), 2.0f, 3.0f);
        }
        if (Double.isFinite(tc.ridgeDistance()) && tc.ridgeDistance() > 1.0 && tc.ridgeDistance() < length) {
            final float rx = plot.x + (float) (tc.ridgeDistance() / length) * plot.w;
            final double clear = tc.ridgeClearance();
            final int col = !Double.isFinite(clear) ? k.ink3() : clear < 5.0 ? Tokens.CRITICAL : clear < 15.0 ? Tokens.CAUTION
                    : k.ink2();
            dashesV(s, plot, rx, col, 3.0f, 2.0f);
        }
        final String w = words(rd, guard);
        final boolean danger = Double.isFinite(tc.impactSeconds()) || (Double.isFinite(tc.ridgeClearance())
                && tc.ridgeClearance() < 5.0);
        s.text(fit(s, w, in.w), in.x, in.bottom() - 9.0f, danger ? Tokens.CRITICAL : k.ink2(), Text.META);
    }

    public static String words(AirframeReadout rd, AerialSnapshot.Guard guard) {
        final String g = HelmWords.guard(guard);
        if (!g.isEmpty()) {
            return g;
        }
        final AirframeReadout.TerrainCheck tc = rd.terrain;
        if (Double.isFinite(tc.impactSeconds())) {
            return "Ground in " + AerialAlerts.duration(tc.impactSeconds()) + " on this climb";
        }
        if (Double.isFinite(tc.ridgeDistance()) && Double.isFinite(tc.ridgeClearance()) && tc.ridgeDistance() > 1.0) {
            final long c = Math.round(Math.abs(tc.ridgeClearance()));
            final String where = tc.risingAtEdge() ? "rising ground at the scan's edge" : "the ridge "
                    + Math.round(tc.ridgeDistance()) + " b ahead";
            return tc.ridgeClearance() >= 0.0 ? "Clears " + where + " by " + c + " b" : "Short of " + where + " by " + c + " b";
        }
        if (Double.isFinite(rd.agl)) {
            return "Clear ahead, " + Math.round(rd.agl) + " b over the ground";
        }
        return "";
    }

    public static String wordsShort(AirframeReadout rd, AerialSnapshot.Guard guard) {
        if (guard != null && !"CLEAR".equals(guard.verdict()) && Double.isFinite(guard.floor())) {
            return "Guard: holding " + Math.round(guard.floor());
        }
        final AirframeReadout.TerrainCheck tc = rd.terrain;
        if (Double.isFinite(tc.impactSeconds())) {
            return "Ground in " + AerialAlerts.duration(tc.impactSeconds());
        }
        if (Double.isFinite(tc.ridgeClearance()) && Double.isFinite(tc.ridgeDistance()) && tc.ridgeDistance() > 1.0) {
            final long c = Math.round(Math.abs(tc.ridgeClearance()));
            return tc.ridgeClearance() >= 0.0 ? "Clears the ridge by " + c + " b" : "Short of the ridge by " + c + " b";
        }
        return Double.isFinite(rd.agl) ? "Clear, " + Math.round(rd.agl) + " b up" : "";
    }

    public static void target(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final String label = rd.nav == null ? "" : fit(s, rd.nav.label(), r.w - 30.0f - s.textWidth("TARGET", Text.META));
        final Rect in = titled(s, r, "TARGET", label, k);
        float y = in.y + 1.0f;
        if (rd.nav != null) {
            final String head = Text.group(Math.round(rd.nav.distance())) + " b  " + AeroInstruments.deg(rd.nav.bearing());
            final float scale = s.textWidth(head, Text.SUB) <= in.w ? Text.SUB : Text.META;
            s.text(head, in.x, y, k.destination(), scale);
            final String eta = Double.isFinite(rd.nav.eta()) ? "ETA " + AerialAlerts.duration(rd.nav.eta()) : "not closing";
            final float ex = in.x + s.textWidth(head, scale) + 8.0f;
            final String e = fit(s, eta, in.right() - ex);
            if (!e.isEmpty()) {
                s.textRight(e, in.right(), y + (scale == Text.SUB ? 4.0f : 0.0f), k.ink2(), Text.META);
            }
            y += scale == Text.SUB ? 17.0f : 12.0f;
        } else {
            s.text(fit(s, "No target set: a Navigation Table or a lodestone compass gives one", in.w), in.x, y, k.ink3(),
                    Text.META);
            y += 12.0f;
        }
        if (y + 9.0f <= in.bottom() + 2.0f) {
            final String wind = !rd.airMeasured || !Double.isFinite(rd.windSpeed) ? "no air data: the wind is unknown"
                    : rd.windSpeed < 0.1 || !Double.isFinite(rd.windFrom) ? "calm"
                    : "wind " + AeroBridge.tenth(rd.windSpeed) + " b/s from " + AeroInstruments.deg(rd.windFrom);
            s.text(fit(s, wind, in.w), in.x, y, rd.airMeasured ? k.ink2() : k.ink3(), Text.META);
            y += ROW;
        }
        if (y + 9.0f <= in.bottom() + 2.0f) {
            final String track = Math.abs(rd.turnRate) < 0.2
                    ? "track " + AeroInstruments.deg(rd.track) + ", drift " + AeroInstruments.signed(rd.drift, 0) + "°"
                    : String.format(Locale.ROOT, "turning %s°/s %s", AeroInstruments.num(Math.abs(rd.turnRate), 1),
                    rd.turnRate > 0.0 ? "right" : "left");
            s.text(fit(s, track, in.w), in.x, y, k.ink3(), Text.META);
        }
    }

    public static void traffic(PanelSurface s, Rect r, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        final float side = Math.min(r.h, r.w * 0.46f);
        final Rect scope = new Rect(r.x, r.y, side, r.h);
        scope(s, scope, rd, k);
        list(s, new Rect(scope.right() + GAP, r.y, r.right() - scope.right() - GAP, r.h), rd, k);
    }

    public static double scopeRange(AirframeReadout rd) {
        final List<Double> d = new ArrayList<>();
        for (final AirframeReadout.TrafficState t : rd.traffic) {
            d.add(t.distance());
        }
        d.sort(Double::compare);
        final double want = d.isEmpty() ? 0.0 : d.get(Math.min(2, d.size() - 1)) * 1.15;
        for (final double r : new double[]{64.0, 128.0, 256.0}) {
            if (want <= r) {
                return r;
            }
        }
        return 512.0;
    }

    public static void scope(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        Shapes.card(s, r, 6.0f, k.card(), k.cardEdge());
        final Rect plot = r.inset(2.0f);
        Shapes.rect(s, plot, Tokens.mix(Tokens.CANVAS, k.skyTop(), 0.35f));
        final double range = scopeRange(rd);
        final float cx = plot.centerX();
        final float cy = plot.centerY();
        final float scale = (float) ((Math.min(plot.w, plot.h) * 0.5 - 8.0) / range);
        final V3 own = rd.snapshot.body().position();
        final V3 ownV = rd.snapshot.body().velocity();
        s.advanceLayer();
        for (final double ring : new double[]{range * 0.5, range}) {
            final float rr = (float) (ring * scale);
            Shapes.arc(s, cx, cy, rr, 1.0f, 0.0f, 2.0f * AeroBridge.PI, Tokens.alpha(Tokens.INK_3, 0.55f));
            final String l = Math.round(ring) + " b";
            final float lx = cx + rr * 0.70f;
            final float ly = cy - rr * 0.70f - 10.0f;
            if (AeroBridge.inside(plot, lx, ly) && AeroBridge.inside(plot, lx + s.textWidth(l, Text.META), ly + 9.0f)) {
                s.text(l, lx, ly, k.ink3(), Text.META);
            }
        }
        s.text("N", cx - 2.5f, plot.y + 3.0f, k.ink3(), Text.META);
        for (final AirframeReadout.TrafficState t : rd.traffic) {
            final V3 rel = t.traffic().position().minus(own);
            final float x = cx + (float) rel.x() * scale;
            final float y = cy + (float) rel.z() * scale;
            final int ink = trafficInk(t);
            if (t.conflict()) {
                final V3 rv = t.traffic().velocity().minus(ownV);
                final double tc = Math.max(0.0, t.cpaTime());
                final float px = x + (float) (rv.x() * tc) * scale;
                final float py = y + (float) (rv.z() * tc) * scale;
                AeroInstruments.clippedDashes(s, x, y, px, py, plot, 1.2f, 4.0f, 3.0f, ink);
                if (AeroBridge.inside(plot.inset(3.0f), px, py)) {
                    Shapes.arc(s, px, py, 3.5f, 1.0f, 0.0f, 2.0f * AeroBridge.PI, ink);
                }
            }
            if (!AeroBridge.inside(plot.inset(6.0f), x, y)) {
                continue;
            }
            final V3 v = t.traffic().velocity();
            AeroInstruments.clippedLine(s, x, y, x + (float) (v.x() * 10.0) * scale, y + (float) (v.z() * 10.0) * scale,
                    plot, 1.2f, Tokens.alpha(ink, 0.8f));
            AeroInstruments.diamond(s, x, y, 4.5f, ink, true);
            final String tag = AeroInstruments.signed(t.climb(), 0);
            if (AeroBridge.inside(plot, x + 7.0f + s.textWidth(tag, Text.META), y + 4.0f)) {
                s.text(tag, x + 7.0f, y - 4.0f, ink, Text.META);
            }
        }
        s.advanceLayer();
        AeroInstruments.chevron(s, cx, cy, 7.5f, (float) Math.toRadians(rd.heading), Tokens.CANVAS);
        AeroInstruments.chevron(s, cx, cy, 6.0f, (float) Math.toRadians(rd.heading), k.ink());
        if (rd.traffic.isEmpty()) {
            centred(s, new Rect(plot.x, plot.bottom() - 30.0f, plot.w, 26.0f), "No other ship within 512 b", k.ink3());
        }
    }

    public static int trafficInk(AirframeReadout.TrafficState t) {
        return t.conflict() ? (t.cpaTime() <= 10.0 ? Tokens.CRITICAL : Tokens.CAUTION) : AeroInstruments.BLUE;
    }

    public static void list(PanelSurface s, Rect r, AirframeReadout rd, AeroSkin k) {
        final int n = rd.traffic.size();
        final Rect in = titled(s, r, "IN RANGE", n == 0 ? "" : n + (n == 1 ? " ship" : " ships"), k);
        if (n == 0) {
            float y = in.y + 1.0f;
            for (final String line : Text.wrap(s, "No other ship within 512 blocks. The Aerial looks once a second; one that "
                    + "comes in range appears here.", in.w, Text.META)) {
                if (y + 9.0f > in.bottom()) {
                    break;
                }
                s.text(line, in.x, y, k.ink3(), Text.META);
                y += 11.0f;
            }
            return;
        }
        final List<AirframeReadout.TrafficState> sorted = new ArrayList<>(rd.traffic);
        sorted.sort(Comparator.comparingDouble(AirframeReadout.TrafficState::distance));
        float y = in.y + 1.0f;
        int shown = 0;
        for (final AirframeReadout.TrafficState t : sorted) {
            if (y + 22.0f > in.bottom() + 2.0f) {
                break;
            }
            final int ink = trafficInk(t);
            Shapes.circle(s, in.x + 3.0f, y + 4.5f, 2.5f, ink);
            final String dist = Text.group(Math.round(t.distance())) + " b";
            final float dw = s.textWidth(dist, Text.META);
            s.text(fit(s, t.traffic().name().isEmpty() ? "Ship" : t.traffic().name(), in.w - dw - 16.0f), in.x + 9.0f, y,
                    k.ink(), Text.META);
            s.textRight(dist, in.right(), y, k.ink2(), Text.META);
            final String second = t.conflict()
                    ? "closest in " + AerialAlerts.duration(t.cpaTime()) + ", " + Math.round(t.cpaDistance()) + " b apart"
                    : AeroInstruments.signed(t.climb(), 0) + " b, " + (t.closing() > 0.2 ? "closing "
                    + AeroBridge.tenth(t.closing()) + " b/s" : "opening");
            s.text(fit(s, second, in.w - 9.0f), in.x + 9.0f, y + 10.0f, t.conflict() ? ink : k.ink3(), Text.META);
            y += 24.0f;
            shown++;
        }
        if (shown < n && y <= in.bottom()) {
            s.textRight(fit(s, "+" + (n - shown) + " more", in.w), in.right(), in.bottom() - 9.0f, k.ink3(), Text.META);
        }
    }
}
