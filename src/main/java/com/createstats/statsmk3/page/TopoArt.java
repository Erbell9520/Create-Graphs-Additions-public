package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.createstats.netscan.NetscanHost;
import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopoArt {
    private TopoArt() {
    }

    public static final float HEADER_H = 62.0f;

    public static final float CAUTION_AT = 70.0f;
    public static final float CRITICAL_AT = 90.0f;

    private static final float FLOW_PERIOD_MS = 1400.0f;
    private static final float ROW_H = 18.0f;

    public record Reading(boolean present, boolean powered, boolean seeded, boolean sweeping,
                          int sweepCount, ScanGraph graph, ScannerTier tier, ScannerForm form,
                          float[] history) {
        public static Reading absent() {
            return new Reading(false, false, false, false, 0, ScanGraph.EMPTY, ScannerTier.FIELD,
                    ScannerForm.INLINE, new float[0]);
        }

        public boolean live() {
            return this.present && this.powered && this.seeded && !this.graph.isEmpty();
        }

        public float pct() {
            return this.graph.loadFraction() * 100.0f;
        }

        public boolean over() {
            return this.graph.isOverStressed();
        }

        public boolean measured() {
            return this.graph.totalCapacity() > 0.0f;
        }

        public String excuse() {
            if (!this.present) {
                return "Scanner missing";
            }
            if (!this.powered) {
                return this.form.isInline() ? "No rotation" : "No power";
            }
            if (!this.seeded) {
                return "Not clamped";
            }
            if (this.graph.isEmpty()) {
                return this.sweeping ? "Sweeping " + this.sweepCount : "No network found";
            }
            return "";
        }

        public String remedy() {
            if (!this.present) {
                return "The block is gone. Re-link a scanner with a Display Link.";
            }
            if (!this.powered) {
                return this.form.isInline()
                        ? "Give the shaft it sits in some rotation."
                        : "Wire it up with a cable, a wire or a cord.";
            }
            if (!this.seeded) {
                return "It has to face a kinetic block to measure one.";
            }
            if (this.graph.isEmpty()) {
                return this.sweeping ? "Reading the drivetrain now."
                        : "Nothing kinetic is connected to it.";
            }
            return "";
        }

        public String state() {
            if (!live()) {
                return excuse();
            }
            if (over()) {
                return "OVERSTRESSED";
            }
            if (!measured()) {
                return "CAP UNKNOWN";
            }
            final float pct = pct();
            return pct >= CRITICAL_AT ? "CRITICAL" : pct >= CAUTION_AT ? "HEAVY" : "HEALTHY";
        }
    }

    public record PeerArt(String numeral, String label, String bearing, int series, boolean compared,
                          float printedPct, Reading data, ScanLayout.Result layout) {
    }

    public static Reading readingOf(NetscanHost host) {
        if (host == null) {
            return Reading.absent();
        }
        final List<float[]> raw = host.scanEngine().history();
        final float[] history = new float[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            final float[] sample = raw.get(i);
            history[i] = sample[1] <= 0.0f ? 0.0f : Math.max(0.0f, sample[0] / sample[1]) * 100.0f;
        }
        return new Reading(true, host.scanPowered(), host.scanSeed() != null,
                host.scanEngine().sweeping(), host.scanEngine().sweepCount(),
                host.scanEngine().graph(), host.scannerTier(), host.scannerForm(), history);
    }

    public static int statusColor(Reading r) {
        if (!r.live()) {
            return r.present() ? Tokens.CAUTION : Tokens.INK_3;
        }
        if (r.over()) {
            return Tokens.CRITICAL;
        }
        if (!r.measured()) {
            return Tokens.CAUTION;
        }
        return Tokens.statusFor(r.graph().loadFraction());
    }

    public static float[] percentSeries(Reading r) {
        return r.history() == null ? new float[0] : r.history();
    }

    public static void header(PanelSurface c, Rect area, List<PeerArt> peers, String notice,
                              float rightInset) {
        c.text("Topology", area.x, area.y, Tokens.INK, Text.SUB);

        int elements = 0;
        int machines = 0;
        int troubled = 0;
        float worst = 0.0f;
        float totalUsed = 0.0f;
        float totalCap = 0.0f;
        for (final PeerArt p : peers) {
            elements += p.data().graph().size();
            machines += p.data().graph().machineCount();
            worst = Math.max(worst, p.data().pct());
            if (p.data().over() || p.data().pct() >= CAUTION_AT || !p.data().live()) {
                troubled++;
            }
            totalUsed += p.data().graph().totalStress();
            totalCap += p.data().graph().totalCapacity();
        }

        final String sub;
        if (peers.isEmpty()) {
            sub = "No Topology Scanner linked";
        } else if (peers.size() == 1) {
            final PeerArt only = peers.get(0);
            final String where = only.bearing().isEmpty() ? "" : "  ·  " + only.bearing();
            sub = only.data().graph().size() + " elements  ·  "
                    + only.data().graph().machineCount() + " machines  ·  "
                    + only.data().tier().getSerializedName() + " grade" + where;
        } else {
            sub = peers.size() + " surveys  ·  " + elements + " elements  ·  " + machines
                    + " machines  ·  " + troubled + " needing attention  ·  worst "
                    + Math.round(worst) + "%";
        }
        c.text(Text.truncate(c, sub, Math.max(40.0f, area.w - rightInset - 12.0f), Text.META),
                area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        if (peers.size() > 1) {
            fleetBar(c, area, peers, totalUsed, totalCap);
        }
        if (notice != null && !notice.isEmpty()) {
            // say it, two scanners on one network are redundant not two networks

            final float w = c.textWidth(notice, Text.MICRO) + 32.0f;
            final Rect chip = new Rect(area.right() - rightInset - w - 8.0f, area.y + 3.0f, w, 20.0f);
            c.card(chip, 9.0f, Tokens.alpha(Tokens.CAUTION, 0.08f), Tokens.alpha(Tokens.CAUTION, 0.35f));
            Shapes.circle(c, chip.x + 11.0f, chip.centerY(), 3.0f, Tokens.CAUTION);
            c.textV(notice, chip.x + 19.0f, chip.centerY(), Tokens.CAUTION, Text.MICRO);
        }
    }

    private static void fleetBar(PanelSurface c, Rect area, List<PeerArt> peers, float used,
                                 float cap) {
        final float labelW = 216.0f;
        final Rect bar = new Rect(area.x, area.y + 40.0f, Math.max(60.0f, area.w - labelW), 14.0f);
        c.card(bar, 7.0f, Tokens.RAISED, Tokens.LINE);
        float x = bar.x + 2.0f;
        for (final PeerArt p : peers) {
            final float capacity = Math.max(1.0f, p.data().graph().totalCapacity());
            final float w = (bar.w - 4.0f) * (capacity / Math.max(1.0f, cap));
            final float fill = Math.min(w - 2.0f,
                    w * Math.min(1.0f, p.data().graph().totalStress() / capacity));
            Shapes.roundedRect(c, new Rect(x, bar.y + 2.0f, Math.max(2.0f, w - 2.0f), bar.h - 4.0f),
                    3.0f, Tokens.LINE);
            Shapes.roundedRect(c, new Rect(x, bar.y + 2.0f, Math.max(2.0f, fill), bar.h - 4.0f),
                    3.0f, statusColor(p.data()));
            x += w;
        }
        c.textV(Text.compact(used) + " / " + Text.compact(cap) + " SU across all surveys",
                bar.right() + 10.0f, bar.centerY(), Tokens.INK_3, Text.MICRO);
    }

    public static Rect diagramRect(Rect r) {
        if (r.w < 80.0f || r.h < 4.0f) {
            return Rect.EMPTY;
        }
        final Rect body = r.inset(12.0f, 30.0f, 12.0f, 10.0f);
        if (body.h < 24.0f) {
            return Rect.EMPTY;
        }
        final float afterBar = body.y + 28.0f;
        if (body.bottom() - afterBar < 20.0f) {
            return Rect.EMPTY;
        }
        final float room = body.bottom() - afterBar;
        final int tiles = body.w >= 300.0f && room >= 190.0f ? 5
                : body.w >= 190.0f && room >= 140.0f ? 3 : 0;
        final float y = tiles > 0 ? afterBar + 32.0f + Tokens.S2 : afterBar;
        final float rest = body.bottom() - y;
        if (rest < 40.0f) {
            return Rect.EMPTY;
        }
        final boolean sideBySide = body.w >= 520.0f && rest >= 160.0f;
        return sideBySide
                ? new Rect(body.x, y, body.w * 0.6f - Tokens.S2 * 0.5f, rest)
                : new Rect(body.x, y, body.w, rest);
    }

    public static void paintPeer(PanelSurface c, PeerArt p, Rect r, float big, float mid,
                                 boolean hovered, ScanLayoutMode mode, float time) {
        if (r.w < 4.0f || r.h < 4.0f) {
            return;
        }
        final Reading data = p.data();
        final int status = statusColor(data);

        c.card(r, Tokens.R_CARD, hovered ? Tokens.RAISED : Tokens.SURFACE,
                hovered ? Tokens.alpha(status, 0.55f) : Tokens.LINE);

        Shapes.roundedRect(c, new Rect(r.x, r.y, 3.0f, r.h), 1.5f, status);

        final float numScale = Text.MICRO + 0.25f * mid;
        c.textV(p.numeral(), r.x + 12.0f, r.y + 13.0f,
                p.compared() ? p.series() : Tokens.INK_2, numScale);
        final float numW = c.textWidth(p.numeral(), numScale);
        if (p.compared()) {
            Shapes.rect(c, r.x + 12.0f, r.y + 21.0f, Math.max(10.0f, numW), 2.0f, p.series());
        }
        if (mid > 0.05f) {
            c.textV(Text.truncate(c, p.label(), Math.max(10.0f, r.w - numW - 40.0f), Text.META),
                    r.x + 16.0f + numW + 6.0f, r.y + 13.0f, Tokens.INK, Text.META);
        }
        if (big > 0.05f) {
            final String meta = data.tier().getSerializedName() + " · "
                    + (data.form().isInline() ? "inline" : "clamp-on")
                    + (p.bearing().isEmpty() ? "" : " · " + p.bearing());
            c.textRightV(Text.truncate(c, meta, r.w * 0.42f, Text.MICRO),
                    r.right() - 12.0f, r.y + 13.0f, Tokens.INK_3, Text.MICRO);
        }

        if (r.w < 80.0f) {
            chip(c, r, p, status);
            return;
        }
        if (!data.live()) {
            final boolean roomy = r.w >= 200.0f && r.h >= 80.0f;
            c.textCenterV(data.excuse(), r.centerX(), roomy ? r.centerY() - 8.0f : r.centerY(),
                    Tokens.CAUTION, r.w < 140.0f ? Text.MICRO : Text.META);
            if (roomy) {
                c.textCenterV(Text.truncate(c, data.remedy(), r.w - 24.0f, Text.MICRO),
                        r.centerX(), r.centerY() + 8.0f, Tokens.INK_3, Text.MICRO);
            }
            return;
        }

        final Rect body = r.inset(12.0f, 30.0f, 12.0f, 10.0f);
        if (body.h < 24.0f) {
            return;
        }

        final Rect track = new Rect(body.x, body.y, body.w, 8.0f);
        Shapes.roundedRect(c, track, 4.0f, Tokens.CANVAS);
        Shapes.roundedRect(c, new Rect(track.x, track.y,
                Math.max(2.0f, track.w * Math.min(1.0f, p.printedPct() / 100.0f)), track.h),
                4.0f, status);
        float y = track.bottom() + 4.0f;
        c.textV(String.format(Locale.ROOT, "%.0f%%", p.printedPct()), body.x, y + 5.0f,
                status, Text.META);
        c.textRightV(Text.compact(data.graph().totalStress()) + " / "
                        + Text.compact(data.graph().totalCapacity()) + " su  ·  " + data.state(),
                body.right(), y + 5.0f, Tokens.INK_3, Text.MICRO);
        y += 16.0f;

        if (body.bottom() - y < 20.0f) {
            return;
        }

        // key on room available, not roster mode. two-up cards came out a third empty

        final float room = body.bottom() - y;
        final int tiles = body.w >= 300.0f && room >= 190.0f ? 5
                : body.w >= 190.0f && room >= 140.0f ? 3 : 0;
        if (tiles > 0) {
            final Rect strip = new Rect(body.x, y, body.w, 32.0f);
            final Rect[] cells = Layout.columns(strip, tiles, Tokens.S2);
            final ScanGraph g = data.graph();
            stat(c, cells[0], "Elements", g.truncated() ? g.size() + "+" : String.valueOf(g.size()),
                    g.truncated() ? Tokens.CAUTION : Tokens.INK);
            stat(c, cells[1], "Machines", String.valueOf(g.machineCount()), Tokens.INK);
            stat(c, cells[2], "Sources", String.valueOf(g.sourceCount()),
                    g.sourceCount() == 0 ? Tokens.CAUTION : Tokens.INK);
            if (tiles >= 5) {
                final int ratios = (int) g.edges().stream().filter(ScanEdge::isRatioChange).count();
                stat(c, cells[3], "Ratio changes", String.valueOf(ratios),
                        ratios > 0 ? Tokens.ACCENT : Tokens.INK_3);
                stat(c, cells[4], "Deepest", p.layout().depth() + " steps", Tokens.INK);
            }
            y = strip.bottom() + Tokens.S2;
        }

        final float rest = body.bottom() - y;
        if (rest < 40.0f) {
            return;
        }

        final boolean sideBySide = body.w >= 520.0f && rest >= 160.0f;
        final Rect graphArea = sideBySide
                ? new Rect(body.x, y, body.w * 0.6f - Tokens.S2 * 0.5f, rest)
                : new Rect(body.x, y, body.w, rest);
        diagram(c, data.graph(), p.layout(), graphArea, mode, time, big);
        if (sideBySide) {
            heaviest(c, data.graph(), new Rect(graphArea.right() + Tokens.S2, y,
                    body.right() - graphArea.right() - Tokens.S2, rest));
        }
    }

    private static void chip(PanelSurface c, Rect r, PeerArt p, int status) {
        if (r.h < 28.0f) {
            return;
        }
        final float radius = Math.min(r.w, r.h - 22.0f) * 0.34f;
        if (radius < 5.0f) {
            return;
        }
        final float cx = r.centerX();
        final float cy = r.y + 26.0f + radius;
        Shapes.arc(c, cx, cy, radius, 3.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE);
        Shapes.arc(c, cx, cy, radius, 3.0f, (float) (-Math.PI * 0.5),
                (float) (Math.PI * 2.0 * Math.min(1.0f, p.printedPct() / 100.0f)), status);
        c.textCenterV(p.data().live() ? Math.round(p.printedPct()) + "%" : "—", cx, cy,
                status, Text.MICRO);
    }

    private static void stat(PanelSurface c, Rect r, String label, String value, int color) {
        c.card(r, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
        c.text(Text.track(label), r.x + 6.0f, r.y + 5.0f, Tokens.INK_3, Text.MICRO);
        c.text(Text.truncate(c, value, r.w - 10.0f, Text.META), r.x + 6.0f, r.y + 17.0f,
                color, Text.META);
    }

    public static void diagram(PanelSurface c, ScanGraph g, ScanLayout.Result flow, Rect a,
                               ScanLayoutMode mode, float time, float detail) {
        if (a.isEmpty() || a.h < 24.0f) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
        final boolean titled = a.h >= 70.0f;
        if (titled) {
            c.text(Text.track(mode.label()), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, Text.MICRO);
            c.textRightV(Text.truncate(c, mode.reading(), a.w * 0.62f, Text.MICRO),
                    a.right() - Tokens.S3, a.y + 11.0f, Tokens.INK_3, Text.MICRO);
        }
        final float legendH = detail > 0.55f && a.h >= 140.0f ? 14.0f : 0.0f;
        final Rect plot = a.inset(Tokens.S3, titled ? Tokens.S4 + 6.0f : Tokens.S2,
                Tokens.S3, Tokens.S2 + legendH);
        if (plot.isEmpty() || flow.positions().isEmpty()) {
            return;
        }

        final Rect canvas;
        if (mode.square()) {
            final float s = Math.min(plot.w, plot.h);
            canvas = new Rect(plot.centerX() - s * 0.5f, plot.centerY() - s * 0.5f, s, s);
        } else {
            canvas = plot;
        }
        final float padX = Math.min(10.0f, canvas.w * 0.08f);
        final float padY = Math.min(10.0f, canvas.h * 0.08f);
        final Map<BlockPos, float[]> norm = flow.positions();
        final Map<BlockPos, float[]> pos = new java.util.HashMap<>();
        for (final Map.Entry<BlockPos, float[]> e : norm.entrySet()) {
            final float[] n = e.getValue();
            pos.put(e.getKey(), new float[]{
                    canvas.x + padX + n[0] * Math.max(1.0f, canvas.w - padX * 2.0f),
                    canvas.y + padY + n[1] * Math.max(1.0f, canvas.h - padY * 2.0f)});
        }

        final boolean power = mode == ScanLayoutMode.POWER;
        final float peak = Math.max(1.0e-3f, flow.maxSubtreeStress());
        c.clip(plot);
        for (final ScanEdge edge : g.edges()) {
            final float[] from = pos.get(edge.from());
            final float[] to = pos.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            final boolean primary = flow.isPrimaryEdge(edge);
            final int color = edge.isRatioChange() ? Tokens.ACCENT
                    : primary ? Tokens.alpha(0xFF4EA8FF, 0.9f)
                    : Tokens.alpha(Tokens.LINE_HI, 0.35f);
            final float width = power && primary
                    ? 1.0f + 4.0f * (float) Math.sqrt(Math.min(1.0f, flow.carried(edge) / peak))
                    : primary ? 1.6f : 1.0f;
            Shapes.line(c, from[0], from[1], to[0], to[1], width, color);
        }

        if (detail > 0.35f) {
            final float phase = (Motion.wallMillis() % (long) FLOW_PERIOD_MS) / FLOW_PERIOD_MS;
            for (final ScanEdge edge : g.edges()) {
                if (!flow.isPrimaryEdge(edge)) {
                    continue;
                }
                final float[] from = pos.get(edge.from());
                final float[] to = pos.get(edge.to());
                final ScanNode driver = g.node(edge.from());
                if (from == null || to == null || driver == null
                        || Math.abs(driver.speed()) < 0.01f) {
                    continue;
                }
                float dx = to[0] - from[0];
                float dy = to[1] - from[1];
                final float span = (float) Math.hypot(dx, dy);
                if (span < 1.0e-3f) {
                    continue;
                }
                dx /= span;
                dy /= span;
                final float half = Math.min(3.5f, span * 0.2f);
                for (int d = 0; d < 2; d++) {
                    float t = phase + d * 0.5f;
                    if (t >= 1.0f) {
                        t -= 1.0f;
                    }

                    final float px = from[0] + (to[0] - from[0]) * t;
                    final float py = from[1] + (to[1] - from[1]) * t;
                    Shapes.line(c, px - dx * half, py - dy * half, px + dx * half, py + dy * half,
                            1.8f, Tokens.alpha(0xFFB6ECFF, 0.9f));
                }
            }
        }

        final float heaviest = heaviestDraw(g);
        for (final Map.Entry<BlockPos, float[]> e : pos.entrySet()) {
            final ScanNode n = g.node(e.getKey());
            if (n == null) {
                continue;
            }
            final float[] p = e.getValue();
            float r = n.kind().isSource() || n.kind().isLoad() ? 4.0f : 3.0f;
            if (power) {
                r = 2.2f + 4.0f * (float) Math.sqrt(Math.min(1.0f, n.drawnStress() / heaviest));
            }
            if (n.isOverStressed()) {
                final float pulse = 0.5f + 0.5f
                        * (float) Math.sin(Motion.wallMillis() / 500.0 * Math.PI);
                Shapes.circle(c, p[0], p[1], r + 3.5f,
                        Tokens.alpha(Tokens.CRITICAL, 0.25f + pulse * 0.45f));
            } else if (flow.isPrimaryRoot(e.getKey())) {
                Shapes.circle(c, p[0], p[1], r + 2.4f, Tokens.alpha(Tokens.GOOD, 0.45f));
            }
            Shapes.circle(c, p[0], p[1], r + 1.2f, Tokens.RAISED);
            int color = nodeColor(n);
            if (mode == ScanLayoutMode.PLAN) {
                color = Tokens.mix(color, Tokens.CANVAS, 0.42f * (1.0f - flow.elevation(e.getKey())));
            }
            Shapes.circle(c, p[0], p[1], r, color);
        }
        c.unclip();

        if (legendH > 0.0f) {
            final float ly = a.bottom() - 9.0f;
            float lx = a.x + Tokens.S3;
            lx = legend(c, lx, ly, Tokens.GOOD, "source");
            lx = legend(c, lx, ly, Tokens.SERIES[0], "machine");
            lx = legend(c, lx, ly, Tokens.ACCENT, "gearing");
            lx = legend(c, lx, ly, 0xFFA4A4A4, "transfer");
            legend(c, lx, ly, Tokens.CRITICAL, "overstressed");
        }
    }

    private static float heaviestDraw(ScanGraph g) {
        float peak = 1.0e-3f;
        for (final ScanNode n : g.nodes().values()) {
            peak = Math.max(peak, n.drawnStress());
        }
        return peak;
    }

    private static float legend(PanelSurface c, float x, float y, int color, String label) {
        Shapes.circle(c, x + 3.0f, y, 3.0f, color);
        c.textV(label, x + 9.0f, y, Tokens.INK_3, Text.MICRO);
        return x + 9.0f + c.textWidth(label, Text.MICRO) + 9.0f;
    }

    public static int nodeColor(ScanNode n) {
        if (n.isOverStressed()) {
            return Tokens.CRITICAL;
        }
        return switch (n.kind()) {
            case SOURCE -> Tokens.GOOD;
            case GEARING -> Tokens.ACCENT;
            case MACHINE -> Tokens.SERIES[0];
            case INSTRUMENT -> Tokens.SERIES[4];
            case TRANSFER -> 0xFFA4A4A4;
            default -> Tokens.INK_3;
        };
    }

    public static void heaviest(PanelSurface c, ScanGraph g, Rect a) {
        if (a.isEmpty() || a.h < 30.0f) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
        c.text(Text.track("Heaviest consumers"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, Text.MICRO);
        c.textRightV("SU", a.right() - Tokens.S3, a.y + 10.0f, Tokens.INK_3, Text.MICRO);

        final Rect list = a.inset(Tokens.S2, Tokens.S4 + 6.0f, Tokens.S2, Tokens.S2);
        if (list.isEmpty()) {
            return;
        }
        final List<ScanNode> consumers = new ArrayList<>();
        for (final ScanNode n : g.byLoad()) {
            if (n.drawnStress() > 0.0f) {
                consumers.add(n);
            }
        }
        if (consumers.isEmpty()) {
            c.textCenterV("No measured consumers", list.centerX(), list.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        final float top = consumers.get(0).drawnStress();
        final int fits = Math.max(1, (int) ((list.h - (consumers.size() > 1 ? 12.0f : 0.0f)) / ROW_H));
        final int shown = Math.min(fits, consumers.size());
        for (int i = 0; i < shown; i++) {
            final ScanNode n = consumers.get(i);
            final Rect row = new Rect(list.x, list.y + i * ROW_H, list.w, ROW_H - 2.0f);
            final float su = n.drawnStress();
            final int col = n.isOverStressed() ? Tokens.CRITICAL : Tokens.seriesFor(n.name());
            final float frac = top <= 0.0f ? 0.0f : su / top;

            Shapes.roundedRect(c, new Rect(row.x, row.y, Math.max(2.0f, row.w * frac), row.h),
                    Tokens.R_CHIP, Tokens.alpha(col, 0.12f));
            Shapes.circle(c, row.x + 7.0f, row.centerY(), 3.0f, col);
            c.textV(Text.truncate(c, n.name(), row.w - 68.0f, Text.META), row.x + 14.0f,
                    row.centerY(), Tokens.INK, Text.META);
            c.textRightV(Text.compact(su), row.right() - 6.0f, row.centerY(),
                    n.isOverStressed() ? Tokens.CRITICAL : Tokens.INK_2, Text.META);
        }
        if (shown < consumers.size()) {
            c.textV("+" + (consumers.size() - shown) + " more on the scanner's Ledger",
                    list.x + 14.0f, list.y + shown * ROW_H + 6.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    public static void compareChart(PanelSurface c, Rect area, List<PeerArt> shown) {
        if (area.isEmpty() || shown.isEmpty()) {
            return;
        }
        c.card(area, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        int longest = 0;
        for (final PeerArt p : shown) {
            longest = Math.max(longest, percentSeries(p.data()).length);
        }
        final boolean trended = longest >= 4;
        c.text(Text.track(trended ? "Load trend" : "Load now"), area.x + Tokens.S3, area.y + 6.0f,
                Tokens.INK_3, Text.MICRO);

        final Rect plot = area.inset(Tokens.S3, Tokens.S4 + 6.0f, Tokens.S3, Tokens.S4 + 2.0f);
        if (plot.isEmpty()) {
            return;
        }
        float ceiling = 100.0f;
        for (final PeerArt p : shown) {
            ceiling = Math.max(ceiling, p.data().pct());
            for (final float v : percentSeries(p.data())) {
                ceiling = Math.max(ceiling, v);
            }
        }
        ceiling = Math.max(1.0f, ceiling * 1.08f);

        final float overY = plot.bottom() - (100.0f / ceiling) * plot.h;
        if (overY > plot.y) {
            Shapes.rect(c, plot.x, overY, plot.w, 1.0f, Tokens.alpha(Tokens.CRITICAL, 0.5f));
            c.textRightV("overstress", plot.right(), overY - 6.0f, Tokens.alpha(Tokens.CRITICAL, 0.7f),
                    Text.MICRO);
        }

        if (trended) {
            for (final PeerArt p : shown) {
                final float[] series = percentSeries(p.data());
                if (series.length < 2) {
                    continue;
                }
                final float[] pts = new float[series.length * 2];
                for (int i = 0; i < series.length; i++) {
                    pts[i * 2] = plot.x + plot.w * (i / (float) Math.max(1, series.length - 1));
                    pts[i * 2 + 1] = plot.bottom() - Math.min(1.0f, series[i] / ceiling) * plot.h;
                }
                Shapes.polyline(c, pts, series.length, Tokens.DATA_STROKE, p.series());
            }
        } else {
            final Rect[] cells = Layout.columns(plot, shown.size(), Tokens.S2);
            c.hairlineH(plot.x, plot.bottom(), plot.w, Tokens.LINE);
            for (int i = 0; i < shown.size(); i++) {
                final PeerArt p = shown.get(i);

                final float w = Math.min(cells[i].w, 54.0f);
                final float x = cells[i].centerX() - w * 0.5f;
                final float h = Math.min(1.0f, p.data().pct() / ceiling) * cells[i].h;
                Shapes.roundedRect(c, new Rect(x, cells[i].y, w, cells[i].h), Tokens.R_CHIP,
                        Tokens.alpha(Tokens.CANVAS, 0.55f));
                if (h > 1.0f) {
                    Shapes.roundedRect(c, new Rect(x, cells[i].bottom() - h, w, h), Tokens.R_CHIP,
                            Tokens.alpha(p.series(), 0.9f));
                }
                c.textCenterV(p.data().live() ? Math.round(p.data().pct()) + "%"
                                : p.data().excuse(),
                        cells[i].centerX(), cells[i].bottom() - h - 7.0f,
                        p.data().live() ? Tokens.INK_2 : Tokens.CAUTION, Text.MICRO);
            }
        }

        float lx = area.x + Tokens.S3;
        for (final PeerArt p : shown) {
            Shapes.rect(c, lx, area.bottom() - 12.0f, 10.0f, 2.0f, p.series());
            final String name = p.numeral() + " " + p.label();
            c.textV(Text.truncate(c, name, area.w / Math.max(1, shown.size()) - 24.0f, Text.MICRO),
                    lx + 14.0f, area.bottom() - 11.0f, Tokens.INK_3, Text.MICRO);
            lx += 20.0f + c.textWidth(Text.truncate(c, name,
                    area.w / Math.max(1, shown.size()) - 24.0f, Text.MICRO), Text.MICRO);
        }
    }

    public static void identify(PanelSurface c, Rect area, Rect card, PeerArt p, float t) {
        if (t <= 0.01f || card.isEmpty()) {
            return;
        }
        final ScanGraph g = p.data().graph();
        final String line = p.label() + (p.bearing().isEmpty() ? "" : "  ·  " + p.bearing())
                + "  ·  " + (p.data().live()
                ? g.size() + " elements, " + g.machineCount() + " machines" : p.data().excuse());
        final float w = c.textWidth(line, Text.MICRO) + 24.0f;
        final boolean above = card.y - 28.0f >= area.y + HEADER_H;
        final float y = above ? card.y - 24.0f : card.bottom() + 6.0f;
        final Rect chip = new Rect(
                Math.max(area.x, Math.min(area.right() - w, card.centerX() - w * 0.5f)), y, w, 20.0f);
        c.card(chip, 9.0f, Tokens.alpha(Tokens.OVERLAY, t), Tokens.alpha(Tokens.LINE_HI, t * 0.8f));
        Shapes.circle(c, chip.x + 11.0f, chip.centerY(), 3.0f,
                Tokens.alpha(statusColor(p.data()), t));
        c.textV(line, chip.x + 19.0f, chip.centerY(), Tokens.alpha(Tokens.INK_2, t), Text.MICRO);
    }

    public static void empty(PanelSurface c, Rect area) {
        final Rect box = area.inset(0.0f, HEADER_H, 0.0f, 0.0f).top(112.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No Topology Scanner is linked to this panel", box.centerX(), box.y + 34.0f,
                Tokens.INK_2, Text.META);
        c.textCenter("Point a Display Link at a Topology Scanner and target this panel.",
                box.centerX(), box.y + 54.0f, Tokens.INK_3, Text.MICRO);
        c.textCenter("Link a second scanner and this page becomes a roster you can compare.",
                box.centerX(), box.y + 70.0f, Tokens.INK_3, Text.MICRO);
    }

    public static List<int[]> twins(List<PeerArt> peers) {
        final List<int[]> groups = new ArrayList<>();
        if (peers == null || peers.size() < 2) {
            return groups;
        }
        final boolean[] taken = new boolean[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            if (taken[i] || !peers.get(i).data().live()) {
                continue;
            }
            final long id = peers.get(i).data().graph().networkId();
            if (id == 0L) {
                continue;
            }
            final List<Integer> group = new ArrayList<>();
            group.add(i);
            for (int j = i + 1; j < peers.size(); j++) {
                if (!taken[j] && peers.get(j).data().live()
                        && peers.get(j).data().graph().networkId() == id) {
                    taken[j] = true;
                    group.add(j);
                }
            }
            if (group.size() > 1) {
                taken[i] = true;
                final int[] out = new int[group.size()];
                for (int k = 0; k < out.length; k++) {
                    out[k] = group.get(k);
                }
                groups.add(out);
            }
        }
        return groups;
    }

    public static String describe(List<PeerArt> peers, int[] group) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < group.length; i++) {
            if (i > 0) {
                sb.append(i == group.length - 1 ? " and " : ", ");
            }
            sb.append(peers.get(group[i]).numeral());
        }
        return sb.toString();
    }

    public static boolean isLoad(ScanNodeKind kind) {
        return kind != null && kind.isLoad();
    }
}
