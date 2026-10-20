package com.createstats.statsmk3.paint;

import java.util.List;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrainFaceArt {
    private TrainFaceArt() {
    }

    public static int color(TrainIntel.Severity severity) {
        return switch (severity) {
            case CRITICAL -> Tokens.CRITICAL;
            case WARN -> Tokens.CAUTION;
            default -> Tokens.INK_2;
        };
    }

    public static int health(RailSnapshot.TrainState t) {
        if (t == null) {
            return Tokens.INK_3;
        }
        if (t.unhealthy()) {
            return Tokens.CRITICAL;
        }
        if (t.waitingForSignal) {
            return t.ticksWaitingForSignal >= TrainIntel.SIGNAL_HOLD_ALERT_TICKS
                    ? Tokens.CAUTION : Tokens.INK_2;
        }
        if (t.moving()) {
            return Tokens.GOOD;
        }
        return t.docked() ? Tokens.ACCENT : Tokens.INK_2;
    }

    public static int latenessColor(float seconds) {
        if (Float.isNaN(seconds)) {
            return Tokens.INK_3;
        }
        return switch (TrainIntel.latenessSeverity(seconds)) {
            case CRITICAL -> Tokens.CRITICAL;
            case WARN -> Tokens.CAUTION;
            default -> seconds < -12.0f ? Tokens.ACCENT : Tokens.GOOD;
        };
    }

    public static String lateShort(float seconds) {
        if (Float.isNaN(seconds)) {
            return "";
        }
        if (Math.abs(seconds) < 12.0f) {
            return "RT";
        }
        final String body = TrainIntel.duration(Math.abs(seconds));
        return (seconds > 0.0f ? "+" : "-") + body.replace(" ", "");
    }

    public record Head(float bottom, boolean drawn) {
    }

    public static Head header(PanelSurface s, Rect area, FaceType ft, Lod lod, String title,
                              String status, int statusColor, String crumb) {
        if (!lod.showsHeader()) {
            return new Head(area.y + ft.pad, false);
        }
        final float alpha = lod.headerAlpha();
        final float pad = ft.pad;
        float y = area.y + pad;

        final float statusW = status == null || status.isEmpty() ? 0.0f
                : s.textWidth(status, ft.micro) + ft.pad;
        final String name = Text.track(title);
        final float titleScale = Text.fitScale(s, name, area.w - pad * 2.0f - statusW,
                area.h * 0.19f, ft.title, ft.micro);
        s.text(Text.truncate(s, name, area.w - pad * 2.0f - statusW, titleScale),
                area.x + pad, y, Tokens.fade(Tokens.INK, alpha), titleScale);

        if (statusW > 0.0f) {
            s.textRight(status, area.right() - pad, y, Tokens.fade(statusColor, alpha), ft.micro);
        }
        y += Text.lineHeight(titleScale);

        if (crumb != null && !crumb.isEmpty() && area.h > 60.0f) {
            y += ft.gap * 0.5f;
            s.text(Text.truncate(s, crumb, area.w - pad * 2.0f, ft.micro), area.x + pad, y,
                    Tokens.fade(Tokens.INK_3, alpha), ft.micro);
            y += Text.lineHeight(ft.micro);
        }
        y += ft.gap;
        Shapes.rect(s, area.x + pad, y, area.w - pad * 2.0f, Math.max(1.0f, ft.hair),
                Tokens.fade(Tokens.LINE_HI, alpha));
        return new Head(y + Math.max(1.0f, ft.hair) + ft.gap, true);
    }

    // fill the bottom strip, half a lit screen black reads as broken
    public static Rect footer(PanelSurface s, Rect area, FaceType ft, RailSnapshot snap,
                              float maxHeight) {
        final float h = Math.min(maxHeight, Text.lineHeight(ft.micro) + ft.gap * 2.0f);
        if (h < Text.lineHeight(ft.micro) + 1.0f) {
            return Rect.EMPTY;
        }
        final Rect strip = new Rect(area.x, area.bottom() - h, area.w, h);
        Shapes.rect(s, strip.x, strip.y, strip.w, Math.max(1.0f, ft.hair),
                Tokens.alpha(Tokens.LINE, 0.8f));

        final TrainIntel.Summary sum = TrainIntel.summarise(snap);
        final float cy = strip.centerY() + ft.hair;
        s.textV(sum.moving + " running", strip.x + ft.pad, cy, Tokens.INK_3, ft.micro);

        final StringBuilder right = new StringBuilder();
        if (sum.broken > 0) {
            right.append(sum.broken).append(" fault");
        } else if (sum.held > 0) {
            right.append(sum.held).append(" held");
        } else if (sum.averageSpeed > 0.0f) {
            right.append("avg ").append(TrainIntel.speed(sum.averageSpeed))
                    .append(' ').append(TrainIntel.SPEED_UNIT);
        }
        if (right.length() > 0) {
            s.textRightV(right.toString(), strip.right() - ft.pad, cy,
                    sum.broken > 0 ? Tokens.CRITICAL : sum.held > 0 ? Tokens.CAUTION : Tokens.INK_3,
                    ft.micro);
        }
        return strip;
    }

    public static void noData(PanelSurface s, Rect area, FaceType ft, String title, String detail) {
        final float cx = area.centerX();
        final float cy = area.centerY();
        final float room = area.w * 0.9f;
        final float titleScale = Text.fitScale(s, title, room, area.h * 0.3f, ft.head, ft.nano);
        s.textCenter(Text.truncate(s, title, room, titleScale), cx,
                cy - Text.lineHeight(titleScale), Tokens.INK_2, titleScale);
        if (detail != null && !detail.isEmpty() && area.h > 34.0f) {
            for (final String line : Text.wrap(s, detail, room, ft.micro)) {
                s.textCenter(line, cx, cy + ft.gap, Tokens.INK_3, ft.micro);
                break;
            }
        }
    }

    public static float chip(PanelSurface s, float x, float y, String label, int color,
                             FaceType ft) {
        final float h = Text.lineHeight(ft.micro) + ft.gap;
        final float w = s.textWidth(label, ft.micro) + ft.gap * 3.0f;
        Shapes.roundedRect(s, new Rect(x, y, w, h), h * 0.5f, Tokens.alpha(color, 0.18f));
        s.textV(label, x + ft.gap * 1.5f, y + h * 0.5f, color, ft.micro);
        return w;
    }

    public static void meter(PanelSurface s, Rect r, float frac, int color) {
        if (r.isEmpty()) {
            return;
        }
        final float radius = Math.min(r.h * 0.5f, 2.0f);
        Shapes.roundedRect(s, r, radius, Tokens.alpha(Tokens.INK_3, 0.22f));
        final float f = Float.isNaN(frac) ? 0.0f : frac < 0.0f ? 0.0f : Math.min(frac, 1.0f);
        if (f > 0.0f) {
            Shapes.roundedRect(s, new Rect(r.x, r.y, Math.max(r.h * 0.6f, r.w * f), r.h),
                    radius, color);
        }
    }

    public static void marker(PanelSurface s, float cx, float cy, float size, float headingRad,
                              int color, boolean moving) {
        Shapes.circle(s, cx, cy, size * 1.35f, Tokens.alpha(Tokens.CANVAS, 0.85f));
        if (!moving || Float.isNaN(headingRad)) {
            Shapes.circle(s, cx, cy, size, color);
            return;
        }
        final float c = (float) Math.cos(headingRad);
        final float sn = (float) Math.sin(headingRad);
        final float nose = size * 1.7f;
        final float tail = size * 1.0f;
        final float side = size * 0.95f;
        s.tri(cx + c * nose, cy + sn * nose, color,
                cx - c * tail - sn * side, cy - sn * tail + c * side, color,
                cx - c * tail + sn * side, cy - sn * tail - c * side, color);
    }

    public static void node(PanelSurface s, float cx, float cy, float size, boolean occupied,
                            boolean interchange) {
        Shapes.circle(s, cx, cy, size * 1.5f, Tokens.alpha(Tokens.CANVAS, 0.9f));
        Shapes.circle(s, cx, cy, size, interchange ? Tokens.INK_2 : Tokens.INK_3);
        Shapes.circle(s, cx, cy, size * 0.55f,
                occupied ? Tokens.GOOD : interchange ? Tokens.SURFACE : Tokens.CANVAS);
    }

    public static void scaleBar(PanelSurface s, float x, float bottom, float maxWidth,
                                float pixelsPerBlock, FaceType ft) {
        if (pixelsPerBlock <= 0.0f || maxWidth < 18.0f) {
            return;
        }
        final float wanted = maxWidth / pixelsPerBlock;
        double step = Math.pow(10.0, Math.floor(Math.log10(Math.max(1.0f, wanted))));
        if (wanted / step >= 5.0) {
            step *= 5.0;
        } else if (wanted / step >= 2.0) {
            step *= 2.0;
        }
        final float w = (float) (step * pixelsPerBlock);
        if (w < 10.0f || w > maxWidth) {
            return;
        }
        final float y = bottom - Text.lineHeight(ft.nano) - 2.0f;
        Shapes.rect(s, x, y, w, Math.max(1.0f, ft.hair), Tokens.alpha(Tokens.INK_3, 0.8f));
        Shapes.rect(s, x, y - 2.0f, Math.max(1.0f, ft.hair), 4.0f, Tokens.alpha(Tokens.INK_3, 0.8f));
        Shapes.rect(s, x + w - 1.0f, y - 2.0f, Math.max(1.0f, ft.hair), 4.0f,
                Tokens.alpha(Tokens.INK_3, 0.8f));
        s.text(TrainIntel.distance(step), x, y + 2.0f, Tokens.alpha(Tokens.INK_3, 0.9f), ft.nano);
    }

    public static void plate(PanelSurface s, String label, float x, float y, int color,
                             float scale, FaceType ft) {
        final float w = s.textWidth(label, scale);
        final float h = Text.lineHeight(scale);
        Shapes.roundedRect(s, new Rect(x - ft.gap * 0.5f, y - ft.gap * 0.25f,
                w + ft.gap, h + ft.gap * 0.5f), 1.5f, Tokens.alpha(Tokens.CANVAS, 0.72f));
        s.text(label, x, y, color, scale);
    }

    public static final class Fit {
        public final float scale;
        private final float offsetX;
        private final float offsetY;
        private final float centreX;
        private final float centreZ;

        private Fit(float scale, float offsetX, float offsetY, float centreX, float centreZ) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.centreX = centreX;
            this.centreZ = centreZ;
        }

        public float x(double worldX) {
            return this.offsetX + (float) (worldX - this.centreX) * this.scale;
        }

        public float y(double worldZ) {
            return this.offsetY + (float) (worldZ - this.centreZ) * this.scale;
        }
    }

    public static Fit fit(Rect plot, double[] box, float margin) {
        if (box == null || plot.isEmpty()) {
            return new Fit(1.0f, plot.centerX(), plot.centerY(), 0.0f, 0.0f);
        }
        final double spanX = Math.max(1.0, box[2] - box[0]);
        final double spanZ = Math.max(1.0, box[3] - box[1]);
        final float usableW = Math.max(1.0f, plot.w - margin * 2.0f);
        final float usableH = Math.max(1.0f, plot.h - margin * 2.0f);
        final float scale = (float) Math.min(usableW / spanX, usableH / spanZ);
        return new Fit(scale, plot.centerX(), plot.centerY(),
                (float) ((box[0] + box[2]) * 0.5), (float) ((box[1] + box[3]) * 0.5));
    }

    public static double[] grow(double[] box, double x, double z) {
        if (box == null) {
            return new double[]{x, z, x, z};
        }
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], z);
        box[2] = Math.max(box[2], x);
        box[3] = Math.max(box[3], z);
        return box;
    }

    public static double[] padBox(double[] box, double margin) {
        if (box == null) {
            return null;
        }

        final double halfW = Math.max(margin, (box[2] - box[0]) * 0.08);
        final double halfH = Math.max(margin, (box[3] - box[1]) * 0.08);
        return new double[]{box[0] - halfW, box[1] - halfH, box[2] + halfW, box[3] + halfH};
    }

    public static void polylineClipped(PanelSurface s, float[] pts, int count, float width,
                                       int color, Rect clip) {
        if (count < 2) {
            return;
        }
        final float[] run = new float[Math.min(count, 256) * 2];
        int n = 0;
        final float[] seg = new float[4];
        for (int i = 0; i + 1 < count; i++) {
            seg[0] = pts[i * 2];
            seg[1] = pts[i * 2 + 1];
            seg[2] = pts[i * 2 + 2];
            seg[3] = pts[i * 2 + 3];
            final boolean visible = clipSegment(seg, clip);
            if (!visible) {
                if (n > 1) {
                    Shapes.polyline(s, run, n, width, color);
                }
                n = 0;
                continue;
            }

            final boolean joins = n > 0 && Math.abs(run[(n - 1) * 2] - seg[0]) < 0.01f
                    && Math.abs(run[(n - 1) * 2 + 1] - seg[1]) < 0.01f;
            if (!joins) {
                if (n > 1) {
                    Shapes.polyline(s, run, n, width, color);
                }
                n = 0;
                run[n * 2] = seg[0];
                run[n * 2 + 1] = seg[1];
                n++;
            }
            if (n * 2 + 1 < run.length) {
                run[n * 2] = seg[2];
                run[n * 2 + 1] = seg[3];
                n++;
            }
        }
        if (n > 1) {
            Shapes.polyline(s, run, n, width, color);
        }
    }

    private static boolean clipSegment(float[] seg, Rect clip) {
        int a = outcode(seg[0], seg[1], clip);
        int b = outcode(seg[2], seg[3], clip);
        for (int guard = 0; guard < 8; guard++) {
            if ((a | b) == 0) {
                return true;
            }
            if ((a & b) != 0) {
                return false;
            }
            final int out = a != 0 ? a : b;
            final float x;
            final float y;
            final float dx = seg[2] - seg[0];
            final float dy = seg[3] - seg[1];
            if ((out & 8) != 0) {
                y = clip.y;
                x = seg[0] + dx * (y - seg[1]) / (Math.abs(dy) < 1.0e-6f ? 1.0e-6f : dy);
            } else if ((out & 4) != 0) {
                y = clip.bottom();
                x = seg[0] + dx * (y - seg[1]) / (Math.abs(dy) < 1.0e-6f ? 1.0e-6f : dy);
            } else if ((out & 2) != 0) {
                x = clip.right();
                y = seg[1] + dy * (x - seg[0]) / (Math.abs(dx) < 1.0e-6f ? 1.0e-6f : dx);
            } else {
                x = clip.x;
                y = seg[1] + dy * (x - seg[0]) / (Math.abs(dx) < 1.0e-6f ? 1.0e-6f : dx);
            }
            if (out == a) {
                seg[0] = x;
                seg[1] = y;
                a = outcode(x, y, clip);
            } else {
                seg[2] = x;
                seg[3] = y;
                b = outcode(x, y, clip);
            }
        }
        return false;
    }

    private static int outcode(float x, float y, Rect clip) {
        int code = 0;
        if (x < clip.x) {
            code |= 1;
        } else if (x > clip.right()) {
            code |= 2;
        }
        if (y > clip.bottom()) {
            code |= 4;
        } else if (y < clip.y) {
            code |= 8;
        }
        return code;
    }

    public static void tracks(PanelSurface s, RailSnapshot snap, Fit fit, Rect plot, float width,
                              int color) {
        final float[] scratch = new float[512];
        for (final RailSnapshot.Track track : snap.tracks()) {
            if (!track.valid()) {
                continue;
            }
            final float[] bounds = track.bounds();

            if (fit.x(bounds[2]) < plot.x || fit.x(bounds[0]) > plot.right()
                    || fit.y(bounds[3]) < plot.y || fit.y(bounds[1]) > plot.bottom()) {
                continue;
            }
            final int points = Math.min(track.path.length / 2, scratch.length / 2);
            for (int i = 0; i < points; i++) {
                scratch[i * 2] = fit.x(track.path[i * 2]);
                scratch[i * 2 + 1] = fit.y(track.path[i * 2 + 1]);
            }
            if (points > 1) {
                polylineClipped(s, scratch, points, width, color, plot.inset(width * 0.5f));
            }
        }
    }

    public static float heading(RailSnapshot snap, RailSnapshot.TrainState t, Fit fit) {
        if (t == null || t.destinationStation == null) {
            return Float.NaN;
        }
        final RailSnapshot.Station dest = snap.station(t.destinationStation);
        if (dest == null || !t.positionValid) {
            return Float.NaN;
        }
        final float dx = fit.x(dest.x) - fit.x(t.x);
        final float dy = fit.y(dest.z) - fit.y(t.z);
        if (dx * dx + dy * dy < 0.25f) {
            return Float.NaN;
        }
        return (float) Math.atan2(dy, dx);
    }

    public static Rows rows(float bodyHeight, int wanted, float minRow, float idealRow) {
        if (wanted <= 0 || bodyHeight < minRow) {
            return new Rows(0, 0.0f);
        }
        final int capacity = Math.max(1, (int) Math.floor(bodyHeight / minRow));
        final int count = Math.min(wanted, capacity);

        final float even = bodyHeight / count;
        return new Rows(count, Math.min(even, idealRow * 1.5f));
    }

    public record Rows(int count, float height) {
        public boolean isEmpty() {
            return this.count <= 0;
        }

        public float used() {
            return this.count * this.height;
        }
    }

    public static boolean fits(PanelSurface s, float room, float scale, String... parts) {
        float need = 0.0f;
        for (final String part : parts) {
            if (part != null && !part.isEmpty()) {
                need += s.textWidth(part, scale);
            }
        }
        return need <= room;
    }

    public static String join(List<String> parts, int limit) {
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (shown > 0) {
                sb.append(" · ");
            }
            sb.append(part);
            if (++shown >= limit) {
                break;
            }
        }
        if (parts.size() > shown) {
            sb.append(" +").append(parts.size() - shown);
        }
        return sb.toString();
    }
}
