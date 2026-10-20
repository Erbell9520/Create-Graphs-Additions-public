package com.createstats.train.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.smart.TrainIntel;

public final class WaitForArt {
    private WaitForArt() {
    }

    @FunctionalInterface
    public interface Locator {
        float[] at(UUID trainId);
    }

    private static final float GAP = 8.0f;

    private static final float BOW = 9.0f;

    private static final int RING_DASHES = 11;

    private static final float TAU = (float) (Math.PI * 2.0);

    public static void draw(PanelSurface c, Rect plot, List<TrainIntel.Stall> stalls,
                            Locator locator, float clock, float weight, boolean labels) {
        if (plot == null || plot.isEmpty() || stalls == null || stalls.isEmpty()) {
            return;
        }
        for (final TrainIntel.Stall stall : stalls) {
            drawStall(c, plot, stall, locator, clock, Math.max(0.35f, weight), labels);
        }
    }

    private static void drawStall(PanelSurface c, Rect plot, TrainIntel.Stall stall, Locator locator,
                                  float clock, float weight, boolean labels) {
        final Map<UUID, float[]> pos = new HashMap<>();
        final List<float[]> located = new ArrayList<>();
        for (final UUID id : stall.trains) {
            final float[] at = locator.at(id);
            if (at != null) {
                pos.put(id, at);
                located.add(at);
            }
        }

        if (located.size() < 2) {
            return;
        }

        final float[] pts = new float[located.size() * 2];
        for (int i = 0; i < located.size(); i++) {
            pts[i * 2] = located.get(i)[0];
            pts[i * 2 + 1] = located.get(i)[1];
        }
        final float[] circle = new float[3];
        enclose(pts, located.size(), circle);

        if (circle[0] < plot.x - circle[2] - 40.0f || circle[0] > plot.right() + circle[2] + 40.0f
                || circle[1] < plot.y - circle[2] - 40.0f
                || circle[1] > plot.bottom() + circle[2] + 40.0f) {
            return;
        }

        final int color = stall.circular ? Tokens.CRITICAL : Tokens.CAUTION;
        final float head = Math.max(4.6f, 5.4f * weight);

        final float m = head + 1.0f;
        final float loX = plot.x + m;
        final float hiX = plot.right() - m;
        final float loY = plot.y + m;
        final float hiY = plot.bottom() - m;

        float ringR = circle[2] + 13.0f * weight;
        final float fitR = Math.min(Math.min(circle[0] - plot.x, plot.right() - circle[0]),
                Math.min(circle[1] - plot.y, plot.bottom() - circle[1])) - 2.0f * weight;
        ringR = Math.min(ringR, fitR);
        if (stall.circular && ringR > circle[2] + 5.0f * weight) {
            drawRing(c, circle[0], circle[1], ringR, weight, clock, color);
        }

        final float[] path = new float[6];
        final float w = Math.max(1.6f, 2.1f * weight);
        for (final Map.Entry<UUID, UUID> edge : stall.waitsOn.entrySet()) {
            final float[] from = pos.get(edge.getKey());
            final float[] to = pos.get(edge.getValue());
            if (from == null || to == null) {
                continue;
            }
            final float bow = stall.circular
                    ? bowSign(from, to, circle) * BOW * weight : 0.0f;
            final int points = arrowPath(from[0], from[1], to[0], to[1], bow, GAP * weight, path);
            for (int i = 0; i < points; i++) {
                path[i * 2] = clamp(path[i * 2], loX, hiX);
                path[i * 2 + 1] = clamp(path[i * 2 + 1], loY, hiY);
            }
            Shapes.polyline(c, path, points, w + 2.0f, Tokens.alpha(Tokens.CANVAS, 0.85f));
            Shapes.polyline(c, path, points, w, color);
            head(c, path, points, head, color);
        }

        // no second ring on a train already alarming

        float labelX = circle[0];
        float labelY = circle[1] - Math.max(ringR, circle[2] + 6.0f * weight);
        if (!stall.circular) {
            final float[] cause = pos.get(stall.cause());
            if (cause != null) {
                labelX = cause[0];
                labelY = cause[1] - 9.0f * weight;
                if (!stall.causeUnhealthy) {
                    final float cx = clamp(cause[0], loX, hiX);
                    final float cy = clamp(cause[1], loY, hiY);
                    Shapes.circle(c, cx, cy, 8.0f * weight, Tokens.alpha(color, 0.20f));
                    Shapes.arc(c, cx, cy, 7.0f * weight, Math.max(1.4f, 1.8f * weight), 0.0f, TAU,
                            color);
                }
            }
        }

        if (labels) {
            label(c, plot, stall, labelX, labelY, weight, color);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return lo > hi ? (lo + hi) * 0.5f : Math.max(lo, Math.min(v, hi));
    }

    private static void drawRing(PanelSurface c, float cx, float cy, float r, float weight,
                                 float clock, int color) {
        Shapes.circle(c, cx, cy, r, Tokens.alpha(Tokens.CANVAS, 0.22f));
        Shapes.circle(c, cx, cy, r, Tokens.alpha(color, 0.05f));
        final float spin = clock * 0.6f;
        final float dash = TAU / RING_DASHES;
        for (int i = 0; i < RING_DASHES; i++) {
            Shapes.arc(c, cx, cy, r, Math.max(1.6f, 2.1f * weight),
                    spin + i * dash, dash * 0.55f, color);
        }
    }

    private static void label(PanelSurface c, Rect plot, TrainIntel.Stall stall, float anchorX,
                              float anchorY, float weight, int color) {
        final String hold = TrainIntel.duration(stall.longestHold / TrainIntel.TICKS_PER_SECOND);
        final String text;
        if (stall.circular) {
            text = stall.trains.size() + " trains deadlocked  .  " + hold;
        } else {
            final int queued = Math.max(1, stall.trains.size() - 1);
            final String cause = stall.names.isEmpty()
                    ? "a train" : stall.names.get(stall.names.size() - 1);
            text = queued + (queued == 1 ? " train held behind " : " trains held behind ")
                    + cause + "  .  " + hold;
        }
        final float lh = Text.lineHeight(Text.MICRO);
        final float tw = c.textWidth(text, Text.MICRO);

        if (tw + 12.0f > plot.w) {
            return;
        }
        float y = anchorY - lh - 6.0f * weight;
        if (y < plot.y + 3.0f) {
            y = anchorY + 12.0f * weight;
        }
        y = Math.max(plot.y + 3.0f, Math.min(y, plot.bottom() - lh - 3.0f));
        final float x = Math.max(plot.x + 5.0f,
                Math.min(anchorX - tw * 0.5f, plot.right() - tw - 5.0f));
        final Rect box = new Rect(x - 5.0f, y - 3.0f, tw + 10.0f, lh + 5.0f);
        Shapes.roundedRect(c, box, 3.0f, Tokens.OVERLAY);
        Shapes.roundedRectOutline(c, box, 3.0f, 1.0f, Tokens.alpha(color, 0.5f));
        c.text(text, x, y, Tokens.alpha(color, 0.95f), Text.MICRO);
    }

    public static int arrowPath(float ax, float ay, float bx, float by, float bow, float gap,
                                float[] out) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3f) {
            out[0] = ax;
            out[1] = ay;
            out[2] = bx;
            out[3] = by;
            return 2;
        }
        final float ux = dx / len;
        final float uy = dy / len;

        final float clampedGap = Math.min(gap, len * 0.4f);
        final float sx = ax + ux * clampedGap;
        final float sy = ay + uy * clampedGap;
        final float ex = bx - ux * clampedGap;
        final float ey = by - uy * clampedGap;

        out[0] = sx;
        out[1] = sy;
        out[2] = (sx + ex) * 0.5f + (-uy) * bow;
        out[3] = (sy + ey) * 0.5f + ux * bow;
        out[4] = ex;
        out[5] = ey;
        return 3;
    }

    public static void enclose(float[] pts, int n, float[] out) {
        float sx = 0.0f;
        float sy = 0.0f;
        for (int i = 0; i < n; i++) {
            sx += pts[i * 2];
            sy += pts[i * 2 + 1];
        }
        final float cx = sx / n;
        final float cy = sy / n;
        float r = 0.0f;
        for (int i = 0; i < n; i++) {
            r = Math.max(r, (float) Math.hypot(pts[i * 2] - cx, pts[i * 2 + 1] - cy));
        }
        out[0] = cx;
        out[1] = cy;
        out[2] = r;
    }

    private static float bowSign(float[] from, float[] to, float[] circle) {
        final float mx = (from[0] + to[0]) * 0.5f - circle[0];
        final float my = (from[1] + to[1]) * 0.5f - circle[1];
        if (mx * mx + my * my < 1.0f) {
            return 1.0f;
        }
        final float dx = to[0] - from[0];
        final float dy = to[1] - from[1];

        return Math.signum(dx * my - dy * mx) >= 0.0f ? 1.0f : -1.0f;
    }

    private static void head(PanelSurface c, float[] path, int points, float size, int color) {
        final float ex = path[(points - 1) * 2];
        final float ey = path[(points - 1) * 2 + 1];
        final float px = path[(points - 2) * 2];
        final float py = path[(points - 2) * 2 + 1];
        final float dx = ex - px;
        final float dy = ey - py;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3f) {
            return;
        }
        final float ux = dx / len;
        final float uy = dy / len;
        final float nx = -uy;
        final float ny = ux;
        c.tri(ex, ey, color,
                ex - ux * size + nx * size * 0.62f, ey - uy * size + ny * size * 0.62f, color,
                ex - ux * size - nx * size * 0.62f, ey - uy * size - ny * size * 0.62f, color);
    }
}
