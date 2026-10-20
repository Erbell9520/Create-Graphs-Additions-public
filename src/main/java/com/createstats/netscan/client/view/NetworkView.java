package com.createstats.netscan.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NetworkView extends ScanView {
    private static final float GAUGE_H = 66.0f;
    private static final float TILES_H = 52.0f;

    private static final float TREND_MIN_H = 96.0f;
    private static final float CHAIN_ROW_H = 14.0f;

    private static final int MIN_SAMPLES = 4;

    private static final float CARD_CHROME_H = Tokens.S4 + 6.0f + Tokens.S3 + 4.0f;

    private Rect gauge = Rect.EMPTY;
    private Rect tiles = Rect.EMPTY;
    private Rect trend = Rect.EMPTY;
    private Rect chain = Rect.EMPTY;

    public NetworkView(ScanContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "network";
    }

    @Override
    public String label() {
        return "Network";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy + 3.0f, 6.0f, 1.6f, 180.0f, 180.0f, color);
        Shapes.line(c, cx, cy + 3.0f, cx + 3.5f, cy - 2.5f, 1.4f, color);
    }

    @Override
    protected void build() {
        final Rect[] rows = Layout.column(this.area, Tokens.S3, GAUGE_H, TILES_H);
        this.gauge = rows[0];
        this.tiles = rows[1];

        splitRemainder(0);
    }

    private void splitRemainder(int chainLines) {
        final float top = this.tiles.bottom() + Tokens.S3;
        final float rest = Math.max(0.0f, this.area.bottom() - top);
        final float wanted = CARD_CHROME_H + Math.max(1, chainLines) * CHAIN_ROW_H;
        final float chainH = Math.max(0.0f, Math.min(wanted, rest - TREND_MIN_H - Tokens.S3));
        this.trend = new Rect(this.area.x, top, this.area.w,
                Math.max(0.0f, rest - chainH - (chainH > 0.0f ? Tokens.S3 : 0.0f)));
        this.chain = new Rect(this.area.x, this.trend.bottom() + Tokens.S3, this.area.w, chainH);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final ScanGraph g = this.ctx.graph;
        if (g.isEmpty()) {
            c.textCenterV("No survey", this.area.centerX(), this.area.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        drawGauge(c, g);
        drawTiles(c, g);
        final List<String> chainLines = chainLines(g);
        splitRemainder(chainLines.size());
        if (this.ctx.allows(3)) {
            drawTrend(c, g);
        } else {
            locked(c, this.trend, "The load trend and overstress projection", 3);
        }
        drawChain(c, chainLines);
    }

    private void drawGauge(PanelSurface c, ScanGraph g) {
        final Rect body = card(c, this.gauge, "Stress");
        final float frac = g.loadFraction();
        final int color = g.isOverStressed() ? Tokens.CRITICAL : Tokens.statusFor(frac);

        final String value = String.format(Locale.ROOT, "%s of %s su",
                Text.compact(g.totalStress()), Text.compact(g.totalCapacity()));
        c.text(value, body.x, body.y, Tokens.INK, Text.SUB);

        final String state = g.isOverStressed() ? "OVERSTRESSED"
                : frac >= 0.85f ? "CRITICAL" : frac >= 0.70f ? "HEAVY" : "HEALTHY";
        c.textRight(state, body.right(), body.y + 3.0f, color, Text.META);

        final Rect track = new Rect(body.x, body.bottom() - 12.0f, body.w, 8.0f);
        Shapes.roundedRect(c, track, 4.0f, Tokens.CANVAS);
        Shapes.roundedRect(c, new Rect(track.x, track.y, track.w * Math.min(1.0f, frac), track.h),
                4.0f, color);
        if (frac > 1.0f) {
            c.textRightV(String.format(Locale.ROOT, "%.0f%% over", (frac - 1.0f) * 100.0f),
                    track.right(), track.centerY() - 10.0f, Tokens.CRITICAL, Text.MICRO);
        }
    }

    private void drawTiles(PanelSurface c, ScanGraph g) {
        final Rect[] cells = Layout.columns(this.tiles, 4, Tokens.S2);
        stat(c, cells[0], "Elements", g.truncated() ? g.size() + "+" : String.valueOf(g.size()),
                g.truncated() ? Tokens.CAUTION : Tokens.INK);
        stat(c, cells[1], "Sources", String.valueOf(g.sourceCount()),
                g.sourceCount() == 0 ? Tokens.CAUTION : Tokens.INK);
        stat(c, cells[2], "Machines", String.valueOf(g.machineCount()), Tokens.INK);
        final int ratios = (int) g.edges().stream().filter(ScanEdge::isRatioChange).count();
        stat(c, cells[3], "Ratio changes", String.valueOf(ratios),
                ratios > 0 ? Tokens.ACCENT : Tokens.INK_3);
    }

    private void drawTrend(PanelSurface c, ScanGraph g) {
        final Rect body = card(c, this.trend, "Load trend");
        final List<float[]> history = this.ctx.history;
        if (history.size() < MIN_SAMPLES) {
            c.textCenterV("Collecting samples  ·  " + history.size() + " of " + MIN_SAMPLES,
                    body.centerX(), body.centerY(), Tokens.INK_3, Text.META);
            Shapes.rect(c, body.x, body.bottom() - 14.0f, body.w, 1.0f,
                    Tokens.alpha(Tokens.LINE, 0.6f));
            return;
        }

        float peak = 1.0f;
        for (final float[] sample : history) {
            peak = Math.max(peak, Math.max(sample[0], sample[1]));
        }
        final float[] stressPoints = new float[history.size() * 2];
        final float[] capacityPoints = new float[history.size() * 2];
        for (int i = 0; i < history.size(); i++) {
            final float x = body.x + body.w * (i / (float) Math.max(1, history.size() - 1));
            stressPoints[i * 2] = x;
            stressPoints[i * 2 + 1] = body.bottom() - 14.0f
                    - (history.get(i)[0] / peak) * Math.max(1.0f, body.h - 20.0f);
            capacityPoints[i * 2] = x;
            capacityPoints[i * 2 + 1] = body.bottom() - 14.0f
                    - (history.get(i)[1] / peak) * Math.max(1.0f, body.h - 20.0f);
        }
        Shapes.polyline(c, capacityPoints, history.size(), 1.4f, Tokens.alpha(Tokens.GOOD, 0.55f));
        Shapes.polyline(c, stressPoints, history.size(), Tokens.DATA_STROKE, Tokens.SERIES[0]);

        c.textV("stress", body.x, body.bottom() - 5.0f, Tokens.SERIES[0], Text.MICRO);
        c.textV("capacity", body.x + 42.0f, body.bottom() - 5.0f, Tokens.GOOD, Text.MICRO);
        c.textRightV(projection(history), body.right(), body.bottom() - 5.0f,
                Tokens.INK_2, Text.MICRO);
    }

    private static String projection(List<float[]> history) {
        final int n = history.size();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumXX = 0.0;
        double capacity = 0.0;
        for (int i = 0; i < n; i++) {
            final double y = history.get(i)[0];
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumXX += (double) i * i;
            capacity += history.get(i)[1];
        }
        capacity /= n;
        final double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1.0e-6) {
            return "steady";
        }
        final double slope = (n * sumXY - sumX * sumY) / denominator;
        final double intercept = (sumY - slope * sumX) / n;
        if (slope <= 1.0e-4 || capacity <= 0.0) {
            return "not rising";
        }
        final double crossing = (capacity - intercept) / slope;
        final double secondsAway = crossing - (n - 1);
        if (secondsAway <= 0.0) {
            return "already over";
        }
        if (secondsAway > n) {
            return "over in more than " + n + "s";
        }
        return String.format(Locale.ROOT, "overstress in ~%.0fs", secondsAway);
    }

    private static List<String> chainLines(ScanGraph g) {
        final List<String> lines = new ArrayList<>();
        for (final ScanNode node : g.nodes().values()) {
            if (node.isSource()) {
                lines.add(node.name() + "  ·  " + Text.value(node.speed()) + " rpm  ·  +"
                        + Text.compact(node.capacityProvided()) + " su");
            }
        }
        for (final ScanEdge edge : g.edges()) {
            if (!edge.isRatioChange()) {
                continue;
            }
            final ScanNode from = g.node(edge.from());
            final ScanNode to = g.node(edge.to());
            if (from != null && to != null) {
                lines.add(from.name() + " -> " + to.name() + "  ·  x" + Text.value(edge.speedRatio()));
            }
        }
        return lines;
    }

    private void drawChain(PanelSurface c, List<String> lines) {
        if (this.chain.h <= 0.0f) {
            return;
        }
        final Rect body = card(c, this.chain, "Sources and gearing");
        if (lines.isEmpty()) {
            c.text("No sources or ratio changes in this survey", body.x, body.y + 2.0f,
                    Tokens.INK_3, Text.META);
            return;
        }
        float y = body.y + 2.0f;
        for (int i = 0; i < lines.size(); i++) {
            if (y > body.bottom() - CHAIN_ROW_H) {
                c.textV("+" + (lines.size() - i) + " more", body.x, y + 5.0f,
                        Tokens.INK_3, Text.MICRO);
                break;
            }
            c.textV(Text.truncate(c, lines.get(i), body.w, Text.META), body.x, y + 5.0f,
                    Tokens.INK_2, Text.META);
            y += CHAIN_ROW_H;
        }
    }
}
