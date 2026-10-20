package com.createstats.statsmk3.page;

import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.V3;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// the world surface has no scissor, clip here
@OnlyIn(Dist.CLIENT)
public final class AeroInstruments {
    public static final int BLUE = Tokens.SERIES[0];
    public static final int ROSE = Tokens.SERIES[1];
    public static final int BRASS = Tokens.SERIES[2];
    public static final int GREEN = Tokens.SERIES[3];
    public static final int VIOLET = Tokens.SERIES[4];
    public static final int ORANGE = Tokens.SERIES[5];
    public static final int SKY_TOP = Tokens.mix(Tokens.CANVAS, BLUE, 0.42f);
    public static final int SKY_LOW = Tokens.mix(Tokens.CANVAS, BLUE, 0.22f);
    public static final int GROUND_HI = Tokens.mix(Tokens.CANVAS, BRASS, 0.32f);
    public static final int GROUND_LO = Tokens.mix(Tokens.CANVAS, BRASS, 0.12f);

    private AeroInstruments() {
    }

    static void fill(PanelSurface s, float[] xy, int n, int color) {
        for (int i = 1; i + 1 < n; i++) {
            s.tri(xy[0], xy[1], color, xy[i * 2], xy[i * 2 + 1], color, xy[i * 2 + 2], xy[i * 2 + 3], color);
        }
    }

    static float[] clipHalf(float[] poly, int n, float nx, float ny, float c) {
        final float[] out = new float[(n + 2) * 2];
        int m = 0;
        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            final float ax = poly[i * 2];
            final float ay = poly[i * 2 + 1];
            final float bx = poly[j * 2];
            final float by = poly[j * 2 + 1];
            final float da = nx * ax + ny * ay - c;
            final float db = nx * bx + ny * by - c;
            if (da <= 0.0f) {
                out[m * 2] = ax;
                out[m * 2 + 1] = ay;
                m++;
            }
            if ((da < 0.0f) != (db < 0.0f) && Math.abs(da - db) > 1.0e-6f) {
                final float t = da / (da - db);
                out[m * 2] = ax + (bx - ax) * t;
                out[m * 2 + 1] = ay + (by - ay) * t;
                m++;
            }
        }
        final float[] trimmed = new float[m * 2 + 1];
        System.arraycopy(out, 0, trimmed, 0, m * 2);
        trimmed[m * 2] = m;
        return trimmed;
    }

    static float[] clip(float x1, float y1, float x2, float y2, Rect r) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        float t0 = 0.0f;
        float t1 = 1.0f;
        final float[] p = {-dx, dx, -dy, dy};
        final float[] q = {x1 - r.x, r.right() - x1, y1 - r.y, r.bottom() - y1};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0e-9f) {
                if (q[i] < 0.0f) {
                    return null;
                }
                continue;
            }
            final float t = q[i] / p[i];
            if (p[i] < 0.0f) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
            if (t0 > t1) {
                return null;
            }
        }
        return new float[]{x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1};
    }

    public static void clippedLine(PanelSurface s, float x1, float y1, float x2, float y2, Rect r, float w, int color) {
        final float[] c = clip(x1, y1, x2, y2, r);
        if (c != null) {
            Shapes.line(s, c[0], c[1], c[2], c[3], w, color);
        }
    }

    public static void clippedDashes(PanelSurface s, float x1, float y1, float x2, float y2, Rect r, float w, float dash,
                              float gap, int color) {
        final float len = (float) Math.hypot(x2 - x1, y2 - y1);
        if (len < 1.0e-3f) {
            return;
        }
        final float ux = (x2 - x1) / len;
        final float uy = (y2 - y1) / len;
        for (float t = 0.0f; t < len; t += dash + gap) {
            final float e = Math.min(len, t + dash);
            clippedLine(s, x1 + ux * t, y1 + uy * t, x1 + ux * e, y1 + uy * e, r, w, color);
        }
    }

    private static boolean inside(Rect r, float x, float y) {
        return x >= r.x && x <= r.right() && y >= r.y && y <= r.bottom();
    }

    public static void attitude(PanelSurface s, Rect r, double pitch, double bank, double sideslip, double fdPitch,
                                double fdBank, boolean alarm) {
        if (r.w < 12.0f || r.h < 12.0f) {
            return;
        }
        final float cx = r.centerX();
        final float cy = r.centerY();
        final float ppd = r.h / 40.0f;
        final float b = (float) Math.toRadians(bank);

        final float ux = (float) -Math.sin(b);
        final float uy = (float) -Math.cos(b);
        final float dx = (float) Math.cos(b);
        final float dy = (float) -Math.sin(b);
        final float p = (float) pitch;
        final float hx = cx - ux * p * ppd;
        final float hy = cy - uy * p * ppd;

        Shapes.gradientV(s, r, SKY_TOP, SKY_LOW);
        final float[] box = {r.x, r.y, r.right(), r.y, r.right(), r.bottom(), r.x, r.bottom()};
        final float[] ground = clipHalf(box, 4, ux, uy, ux * hx + uy * hy);
        final int gn = (int) ground[ground.length - 1];
        if (gn >= 3) {
            fill(s, ground, gn, GROUND_HI);
        }
        s.advanceLayer();
        final float far = r.w + r.h;
        clippedLine(s, hx - dx * far, hy - dy * far, hx + dx * far, hy + dy * far, r, 1.5f, Tokens.INK);

        final Rect ladder = r.inset(4.0f);
        for (int deg = -30; deg <= 30; deg += 5) {
            if (deg == 0) {
                continue;
            }
            final float lx = hx + ux * deg * ppd;
            final float ly = hy + uy * deg * ppd;
            final float half = (deg % 10 == 0 ? 0.17f : 0.08f) * r.w;
            final int col = deg > 0 ? Tokens.INK : Tokens.mix(Tokens.INK, BRASS, 0.3f);
            if (deg > 0) {
                clippedLine(s, lx - dx * half, ly - dy * half, lx + dx * half, ly + dy * half, ladder, 1.0f, col);
            } else {
                clippedDashes(s, lx - dx * half, ly - dy * half, lx + dx * half, ly + dy * half, ladder, 1.0f,
                        3.0f, 2.0f, col);
            }
            if (deg % 10 == 0) {
                final String label = Integer.toString(Math.abs(deg));
                final float tx = lx + dx * (half + 7.0f);
                final float ty = ly + dy * (half + 7.0f);
                if (inside(ladder, tx - 5.0f, ty - 4.0f) && inside(ladder, tx + 5.0f, ty + 4.0f)) {
                    s.textCenterV(label, tx, ty, col, Text.MICRO);
                }
            }
        }

        final float radius = Math.min(r.w, r.h) * 0.42f;
        if (radius > 16.0f) {
            Shapes.arc(s, cx, cy, radius, 1.0f, (float) Math.toRadians(-150.0), (float) Math.toRadians(120.0),
                    Tokens.alpha(Tokens.INK, 0.55f));
            for (final int a : new int[]{-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60}) {
                final float ang = (float) Math.toRadians(a - 90.0);
                final float len = a % 30 == 0 ? 5.0f : 3.0f;
                Shapes.line(s, cx + (float) Math.cos(ang) * radius, cy + (float) Math.sin(ang) * radius,
                        cx + (float) Math.cos(ang) * (radius + len), cy + (float) Math.sin(ang) * (radius + len),
                        1.0f, Tokens.INK);
            }
            final float pa = (float) Math.toRadians(-bank - 90.0);
            final float tipR = radius - 1.0f;
            final float baseR = radius - 7.0f;
            final float px = (float) Math.cos(pa + Math.PI / 2.0);
            final float py = (float) Math.sin(pa + Math.PI / 2.0);
            final float bx = cx + (float) Math.cos(pa) * baseR;
            final float by = cy + (float) Math.sin(pa) * baseR;
            final int pointer = Math.abs(bank) > 35.0 ? Tokens.CAUTION : Tokens.INK;
            s.tri(cx + (float) Math.cos(pa) * tipR, cy + (float) Math.sin(pa) * tipR, pointer,
                    bx + px * 3.5f, by + py * 3.5f, pointer, bx - px * 3.5f, by - py * 3.5f, pointer);

            final float slip = (float) AirframeMath.clamp(sideslip / 15.0, -1.0, 1.0) * 5.0f;
            final float sx = bx - (float) Math.cos(pa) * 2.5f + px * slip;
            final float sy = by - (float) Math.sin(pa) * 2.5f + py * slip;
            Shapes.line(s, sx - px * 4.0f, sy - py * 4.0f, sx + px * 4.0f, sy + py * 4.0f, 1.6f, pointer);
        }

        if (Double.isFinite(fdPitch) && Double.isFinite(fdBank)) {
            final float fy = cy + (float) (pitch - fdPitch) * ppd;
            final float fx = cx + (float) (fdBank - bank) * 1.2f;
            clippedLine(s, fx - r.w * 0.2f, fy, fx + r.w * 0.2f, fy, ladder, 1.4f, VIOLET);
            clippedLine(s, fx, fy - r.h * 0.18f, fx, fy + r.h * 0.18f, ladder, 1.4f, VIOLET);
        }

        final int sym = alarm ? Tokens.CRITICAL : Tokens.ACCENT;
        final float wing = r.w * 0.30f;
        final float inner = r.w * 0.10f;
        Shapes.rect(s, cx - wing, cy - 1.0f, wing - inner, 2.0f, sym);
        Shapes.rect(s, cx - inner - 2.0f, cy - 1.0f, 2.0f, 5.0f, sym);
        Shapes.rect(s, cx + inner, cy - 1.0f, wing - inner, 2.0f, sym);
        Shapes.rect(s, cx + inner, cy - 1.0f, 2.0f, 5.0f, sym);
        Shapes.rect(s, cx - 1.5f, cy - 1.5f, 3.0f, 3.0f, sym);
        Shapes.roundedRectOutline(s, r, 5.0f, 1.0f, Tokens.LINE_HI);
    }

    public record TapeMarks(double bandLo, double bandHi, int bandColor, double ground, double bug, double trend,
                            String unit, String sub, int readoutColor) {
        public static TapeMarks plain(String unit) {
            return new TapeMarks(Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN, Double.NaN, unit, null, Tokens.INK);
        }
    }

    public static void tape(PanelSurface s, Rect r, double value, double step, int labelEvery, boolean scaleOnLeft,
                            TapeMarks m) {
        if (r.w < 10.0f || r.h < 20.0f || !Double.isFinite(value)) {
            Shapes.card(s, r, 3.0f, Tokens.SURFACE, Tokens.LINE);
            s.textCenterV("--", r.centerX(), r.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        Shapes.card(s, r, 3.0f, Tokens.SURFACE, Tokens.LINE);
        final float mid = r.centerY();
        final float ppu = 7.0f / (float) step;
        final Rect inner = r.inset(1.0f, 2.0f, 1.0f, 2.0f);
        if (Double.isFinite(m.bandLo()) && Double.isFinite(m.bandHi())) {
            final float y0 = Math.max(inner.y, mid - (float) (m.bandHi() - value) * ppu);
            final float y1 = Math.min(inner.bottom(), mid - (float) (m.bandLo() - value) * ppu);
            if (y1 > y0) {
                final float bx = scaleOnLeft ? r.right() - 3.0f : r.x + 1.0f;
                Shapes.rect(s, bx, y0, 2.0f, y1 - y0, Tokens.alpha(m.bandColor(), 0.85f));
            }
        }
        if (Double.isFinite(m.ground())) {
            final float gy = mid - (float) (m.ground() - value) * ppu;
            if (gy < inner.bottom()) {
                final float top = Math.max(inner.y, gy);
                final int hatch = Tokens.mix(Tokens.SURFACE, BRASS, 0.55f);
                for (float y = top; y < inner.bottom(); y += 3.0f) {
                    clippedLine(s, inner.x, y + 3.0f, inner.right(), y, inner, 0.8f, hatch);
                }
                Shapes.rect(s, inner.x, top, inner.w, 1.0f, BRASS);
            }
        }
        final double lo = value - (r.h * 0.5) / ppu;
        final double hi = value + (r.h * 0.5) / ppu;
        for (double v = Math.floor(lo / step) * step; v <= hi; v += step) {
            final float y = mid - (float) (v - value) * ppu;
            if (y <= inner.y || y >= inner.bottom()) {
                continue;
            }
            final long index = Math.round(v / step);
            final boolean major = index % labelEvery == 0;
            final float len = major ? 5.0f : 3.0f;
            if (scaleOnLeft) {
                Shapes.rect(s, r.right() - 1.0f - len, y, len, 1.0f, Tokens.INK_2);
            } else {
                Shapes.rect(s, r.x + 1.0f, y, len, 1.0f, Tokens.INK_2);
            }
            if (major && Math.abs(y - mid) > 11.0f && y - 4.0f > inner.y && y + 4.0f < inner.bottom()) {
                final String label = Long.toString(Math.round(v));
                if (scaleOnLeft) {
                    s.textRightV(label, r.right() - 8.0f, y, Tokens.INK_2, Text.MICRO);
                } else {
                    s.textV(label, r.x + 8.0f, y, Tokens.INK_2, Text.MICRO);
                }
            }
        }
        if (Double.isFinite(m.bug())) {
            final float y = (float) AirframeMath.clamp(mid - (m.bug() - value) * ppu, inner.y + 3.0, inner.bottom() - 3.0);

            if (scaleOnLeft) {
                s.tri(r.right() - 1.0f, y - 3.0f, Tokens.HELD, r.right() - 5.0f, y, Tokens.HELD, r.right() - 1.0f, y + 3.0f,
                        Tokens.HELD);
            } else {
                s.tri(r.x + 1.0f, y - 3.0f, Tokens.HELD, r.x + 5.0f, y, Tokens.HELD, r.x + 1.0f, y + 3.0f, Tokens.HELD);
            }
        }
        if (Double.isFinite(m.trend()) && Math.abs(m.trend()) > 0.05) {
            final float ty = (float) AirframeMath.clamp(mid - m.trend() * ppu, inner.y, inner.bottom());
            final float tx = scaleOnLeft ? r.right() - 2.0f : r.x + 2.0f;
            Shapes.line(s, tx, mid, tx, ty, 1.5f, Tokens.GOOD);
        }
        final String readout = Long.toString(Math.round(value));
        final float bw = Math.max(r.w - 4.0f, s.textWidth(readout, Text.SUB) + 8.0f);
        final Rect box = new Rect(scaleOnLeft ? r.x + 2.0f : r.right() - 2.0f - bw, mid - 8.0f, bw, 16.0f);
        Shapes.roundedRect(s, box, 2.0f, Tokens.CANVAS);
        Shapes.roundedRectOutline(s, box, 2.0f, 1.0f, m.readoutColor());
        final float scale = s.textWidth(readout, Text.SUB) <= box.w - 4.0f ? Text.SUB : Text.META;
        s.textCenterV(readout, box.centerX(), box.centerY(), m.readoutColor(), scale);

        if (m.unit() != null && !m.unit().isEmpty() && s.textWidth(m.unit(), Text.MICRO) <= r.w) {
            s.textCenter(m.unit(), r.centerX(), r.y - 10.0f, Tokens.INK_3, Text.MICRO);
        }
        if (m.sub() != null && !m.sub().isEmpty() && s.textWidth(m.sub(), Text.MICRO) <= r.w) {
            s.textCenter(m.sub(), r.centerX(), r.bottom() + 3.0f, Tokens.INK_2, Text.MICRO);
        }
    }

    public static void headingTape(PanelSurface s, Rect r, double heading, double track, double bug) {
        Shapes.card(s, r, 3.0f, Tokens.SURFACE, Tokens.LINE);
        if (!Double.isFinite(heading)) {
            return;
        }
        final float ppd = r.w / 60.0f;
        final float mid = r.centerX();
        for (int d = (int) Math.floor(heading) - 35; d <= (int) Math.ceil(heading) + 35; d++) {
            final float x = mid + (float) (d - heading) * ppd;
            if (x <= r.x + 2.0f || x >= r.right() - 2.0f) {
                continue;
            }
            final int dd = Math.floorMod(d, 360);
            if (dd % 10 == 0) {
                Shapes.rect(s, x, r.bottom() - 5.0f, 1.0f, 4.0f, Tokens.INK_2);
                final String label = switch (dd) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> String.format(Locale.ROOT, "%02d", dd / 10);
                };
                if (Math.abs(x - mid) > 14.0f) {
                    s.textCenter(label, x, r.y + 3.0f, label.length() == 1 ? Tokens.INK : Tokens.INK_2, Text.MICRO);
                }
            } else if (dd % 5 == 0) {
                Shapes.rect(s, x, r.bottom() - 3.0f, 1.0f, 2.0f, Tokens.INK_3);
            }
        }
        if (Double.isFinite(track)) {
            final float d = (float) AirframeMath.wrap180(track - heading);
            if (Math.abs(d) < 29.0f) {
                final float tx = mid + d * ppd;
                final float ty = r.bottom() - 5.0f;
                Shapes.line(s, tx, ty - 4.0f, tx + 3.0f, ty, 1.0f, Tokens.GOOD);
                Shapes.line(s, tx + 3.0f, ty, tx, ty + 4.0f, 1.0f, Tokens.GOOD);
                Shapes.line(s, tx, ty + 4.0f, tx - 3.0f, ty, 1.0f, Tokens.GOOD);
                Shapes.line(s, tx - 3.0f, ty, tx, ty - 4.0f, 1.0f, Tokens.GOOD);
            }
        }
        if (Double.isFinite(bug)) {
            final float d = (float) AirframeMath.clamp(AirframeMath.wrap180(bug - heading), -29.0, 29.0);
            final float bx = mid + d * ppd;
            Shapes.rect(s, bx - 4.0f, r.bottom() - 3.0f, 8.0f, 3.0f, Tokens.HELD);
        }
        final String h = String.format(Locale.ROOT, "%03d°", Math.round(heading) % 360);
        final float bw = s.textWidth(h, Text.META) + 8.0f;
        final Rect box = new Rect(mid - bw * 0.5f, r.y - 7.0f, bw, 12.0f);
        Shapes.roundedRect(s, box, 2.0f, Tokens.CANVAS);
        Shapes.roundedRectOutline(s, box, 2.0f, 1.0f, Tokens.INK);
        s.textCenterV(h, box.centerX(), box.centerY(), Tokens.INK, Text.META);
    }

    public static void vsi(PanelSurface s, Rect r, double vs, double limit) {
        Shapes.card(s, r, 3.0f, Tokens.SURFACE, Tokens.LINE);
        final float mid = r.centerY();
        final float half = r.h * 0.5f - 7.0f;
        for (final int v : new int[]{-6, -4, -2, -1, 0, 1, 2, 4, 6}) {
            final float y = vsiY(v, limit, mid, half);
            Shapes.rect(s, r.x + 1.0f, y, v % 2 == 0 ? 4.0f : 2.0f, 1.0f, Tokens.INK_3);
            if ((v == 2 || v == -2 || v == 4 || v == -4) && r.w > 14.0f) {
                s.textV(Integer.toString(Math.abs(v)), r.x + 6.0f, y, Tokens.INK_3, Text.MICRO);
            }
        }
        if (!Double.isFinite(vs)) {
            return;
        }
        final float y = vsiY(vs, limit, mid, half);
        final int col = vs > -2.5 ? Tokens.GOOD : (vs > -4.0 ? Tokens.CAUTION : Tokens.CRITICAL);
        Shapes.line(s, r.right() - 1.0f, mid, r.x + 3.0f, y, 1.6f, col);
        final String text = String.format(Locale.ROOT, "%+.1f", vs);
        s.textCenter(text, r.centerX(), vs >= 0.0 ? r.y + 2.0f : r.bottom() - 9.0f, col, Text.MICRO);
    }

    private static float vsiY(double v, double limit, float mid, float half) {
        final double c = AirframeMath.clamp(v, -limit, limit);
        return mid - (float) (Math.copySign(Math.sqrt(Math.abs(c) / limit), c) * half);
    }

    public static void windArrow(PanelSurface s, float cx, float cy, float radius, double towardScreenDeg, int color) {
        Shapes.arc(s, cx, cy, radius, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE_HI);
        if (!Double.isFinite(towardScreenDeg)) {
            return;
        }
        final float a = (float) Math.toRadians(towardScreenDeg - 90.0);
        final float ex = (float) Math.cos(a);
        final float ey = (float) Math.sin(a);
        final float len = radius * 0.8f;
        Shapes.line(s, cx - ex * len, cy - ey * len, cx + ex * len, cy + ey * len, 1.4f, color);
        final float px = -ey;
        final float py = ex;
        s.tri(cx + ex * len, cy + ey * len, color, cx + ex * (len - 4.0f) + px * 2.5f,
                cy + ey * (len - 4.0f) + py * 2.5f, color, cx + ex * (len - 4.0f) - px * 2.5f,
                cy + ey * (len - 4.0f) - py * 2.5f, color);
    }

    @FunctionalInterface
    interface Projection {
        float[] at(double x, double z);
    }

    public static void diamond(PanelSurface s, float x, float y, float r, int color, boolean solid) {
        if (solid) {
            s.tri(x, y - r, color, x + r, y, color, x, y + r, color);
            s.tri(x, y - r, color, x, y + r, color, x - r, y, color);
        } else {
            Shapes.line(s, x, y - r, x + r, y, 1.0f, color);
            Shapes.line(s, x + r, y, x, y + r, 1.0f, color);
            Shapes.line(s, x, y + r, x - r, y, 1.0f, color);
            Shapes.line(s, x - r, y, x, y - r, 1.0f, color);
        }
    }

    public static void chevron(PanelSurface s, float x, float y, float size, float radians, int color) {
        final float fx = (float) Math.sin(radians);
        final float fy = (float) -Math.cos(radians);
        final float px = -fy;
        final float py = fx;
        final float tipX = x + fx * size;
        final float tipY = y + fy * size;
        final float backX = x - fx * size * 0.7f;
        final float backY = y - fy * size * 0.7f;
        final float notchX = x - fx * size * 0.25f;
        final float notchY = y - fy * size * 0.25f;
        s.tri(tipX, tipY, color, backX + px * size * 0.7f, backY + py * size * 0.7f, color, notchX, notchY, color);
        s.tri(tipX, tipY, color, notchX, notchY, color, backX - px * size * 0.7f, backY - py * size * 0.7f, color);
    }

    public static void spark(PanelSurface s, Rect r, float[] series, int color, double lo, double hi, double ref,
                             boolean fill) {
        int n = 0;
        final float[] pts = new float[series.length * 2];
        final double span = hi - lo > 1.0e-6 ? hi - lo : 1.0;
        for (int i = 0; i < series.length; i++) {
            if (!Float.isFinite(series[i])) {
                continue;
            }
            pts[n * 2] = r.x + (series.length == 1 ? 0.0f : (float) i / (series.length - 1) * r.w);
            pts[n * 2 + 1] = r.bottom() - (float) AirframeMath.clamp((series[i] - lo) / span, 0.0, 1.0) * r.h;
            n++;
        }
        if (Double.isFinite(ref)) {
            final float ry = r.bottom() - (float) AirframeMath.clamp((ref - lo) / span, 0.0, 1.0) * r.h;
            for (float x = r.x; x < r.right(); x += 5.0f) {
                Shapes.rect(s, x, ry, Math.min(2.5f, r.right() - x), 1.0f, Tokens.alpha(Tokens.INK_3, 0.8f));
            }
        }
        if (n < 2) {
            s.textCenterV("Waiting for history", r.centerX(), r.centerY(), Tokens.INK_3, Text.MICRO);
            return;
        }
        if (fill) {
            Shapes.areaFill(s, pts, n, r.bottom(), Tokens.alpha(color, 0.22f), Tokens.alpha(color, 0.02f));
        }
        Shapes.polyline(s, pts, n, 1.4f, color);
    }

    public static double[] range(float[] series, double pad) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (final float v : series) {
            if (Float.isFinite(v)) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        }
        if (!Double.isFinite(lo)) {
            return new double[]{0.0, 1.0};
        }
        if (hi - lo < 1.0e-3) {
            lo -= 1.0;
            hi += 1.0;
        }
        final double p = (hi - lo) * pad;
        return new double[]{lo - p, hi + p};
    }

    public static String num(double v, int decimals) {
        if (!Double.isFinite(v)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public static String signed(double v, int decimals) {
        if (!Double.isFinite(v)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%+." + decimals + "f", v);
    }

    public static String deg(double v) {
        if (!Double.isFinite(v)) {
            return "---";
        }
        return String.format(Locale.ROOT, "%03d°", Math.round(AirframeMath.wrap360(v)) % 360);
    }

    public static String pct(double ratio) {
        if (!Double.isFinite(ratio)) {
            return "--";
        }
        return Math.round(ratio * 100.0) + "%";
    }
}
