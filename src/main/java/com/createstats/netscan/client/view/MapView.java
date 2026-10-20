package com.createstats.netscan.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;
import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// one layout computation, four arrangements. switching can't change what it claims
@OnlyIn(Dist.CLIENT)
public final class MapView extends ScanView {
    private static final float PLOT_PAD = 16.0f;
    private static final float FLOW_PERIOD_MS = 1400.0f;
    private static final float REVEAL_SECONDS = 0.9f;
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 6.0f;
    private static final float HIT_RADIUS = 7.0f;

    private static final float HEADER_H = 32.0f;

    private static final float FOOTER_H = 26.0f;

    private static final int GLYPH_BUDGET = 220;

    private static final float DIM = 0.34f;

    private final Map<BlockPos, float[]> offsets = new HashMap<>();
    private final Map<BlockPos, float[]> screen = new LinkedHashMap<>();
    private final Set<BlockPos> highlight = new HashSet<>();

    private Segmented modeControl;
    private Rect card = Rect.EMPTY;
    private Rect body = Rect.EMPTY;
    private Rect plot = Rect.EMPTY;

    private Rect canvas = Rect.EMPTY;
    private float unitW = 1.0f;
    private float unitH = 1.0f;

    private float zoom = 1.0f;
    private float panX;
    private float panY;
    private boolean panning;
    private BlockPos dragging;
    private BlockPos hovered;
    private float reveal = 1.0f;
    private int revealHash = Integer.MIN_VALUE;
    private boolean fitPending = true;

    public MapView(ScanContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "map";
    }

    @Override
    public String label() {
        return "Map";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.circle(c, cx - 5.0f, cy, 2.0f, color);
        Shapes.circle(c, cx + 4.0f, cy - 4.0f, 2.0f, color);
        Shapes.circle(c, cx + 4.0f, cy + 4.0f, 2.0f, color);
        Shapes.line(c, cx - 4.0f, cy, cx + 3.0f, cy - 4.0f, 1.1f, color);
        Shapes.line(c, cx - 4.0f, cy, cx + 3.0f, cy + 4.0f, 1.1f, color);
    }

    @Override
    protected void build() {
        this.card = this.area;
        this.body = this.card.inset(Tokens.S3, HEADER_H, Tokens.S3, Tokens.S3);
        this.plot = new Rect(this.body.x, this.body.y, this.body.w,
                Math.max(0.0f, this.body.h - FOOTER_H));

        final ScanLayoutMode[] modes = ScanLayoutMode.values();
        final String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].label();
        }
        this.modeControl = new Segmented(labels, () -> this.ctx.mode.ordinal(), i -> {
            final ScanLayoutMode next = ScanLayoutMode.byOrdinal(i);
            if (next != this.ctx.mode) {
                this.ctx.mode = next;

                this.offsets.clear();
                this.fitPending = true;
            }
        });
        this.modeControl.textScale(Text.MICRO);
        final float w = 46.0f * modes.length;
        this.modeControl.setBounds(new Rect(this.card.right() - Tokens.S3 - w,
                this.card.y + 5.0f, w, 22.0f));
    }

    public void frame() {
        this.fitPending = true;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (this.modeControl != null) {
            this.modeControl.update(dt);
        }
        final int hash = this.ctx.graph.contentHash();
        if (hash != this.revealHash) {
            this.revealHash = hash;
            this.reveal = Motion.reducedMotion() ? 1.0f : 0.0f;

            this.offsets.clear();
            this.fitPending = true;
        }
        this.reveal = Math.min(1.0f, this.reveal + dt / REVEAL_SECONDS);
    }

    @Override
    public void hover(double mx, double my) {
        super.hover(mx, my);
        if (this.modeControl != null) {
            this.modeControl.mouseMoved(mx, my);
        }
    }

    @Override
    protected void drawContent(PanelSurface c) {
        c.card(this.card, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final ScanGraph g = this.ctx.graph;
        final ScanLayoutMode mode = this.ctx.mode;

        c.text(Text.track(mode.label()), this.card.x + Tokens.S3, this.card.y + 7.0f,
                Tokens.INK_2, Text.MICRO);
        c.text(Text.truncate(c, mode.reading(),
                        this.modeControl.bounds().x - this.card.x - Tokens.S4 - 46.0f, Text.MICRO),
                this.card.x + Tokens.S3 + 46.0f, this.card.y + 7.0f, Tokens.INK_3, Text.MICRO);
        c.hairlineH(this.card.x + Tokens.S2, this.card.y + HEADER_H - 4.0f,
                this.card.w - Tokens.S4, Tokens.LINE);
        this.modeControl.draw(c);

        if (g.isEmpty()) {
            c.textCenterV(emptyMessage(), this.body.centerX(), this.body.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        final ScanLayout.Result layout = this.ctx.layout();

        measure(canvasOf(this.plot, mode));
        if (this.fitPending) {
            this.fitPending = false;
            fit(layout);
        }
        project(layout);
        buildHighlight(layout);

        c.clip(this.plot);
        if (mode == ScanLayoutMode.PLAN) {
            drawPlanGrid(c, layout);
        } else if (mode == ScanLayoutMode.RING) {
            drawRings(c, layout);
        }
        drawEdges(c, g, layout);
        drawFlow(c, g, layout);
        drawNodes(c, g, layout);
        c.unclip();

        drawLegend(c, mode);
        drawFooter(c, g, layout);
        drawTooltip(c, g, layout);
    }

    private String emptyMessage() {
        if (!this.ctx.powered) {
            return this.ctx.form.isInline() ? "No rotation on the shaft" : "No power";
        }
        if (this.ctx.seedMissing) {
            return "Not clamped to a kinetic block";
        }
        return this.ctx.sweeping ? "Sweeping..." : "No kinetic network found";
    }

    private static Rect canvasOf(Rect plot, ScanLayoutMode mode) {
        if (!mode.square()) {
            return plot;
        }
        final float s = Math.min(plot.w, plot.h);
        return new Rect(plot.centerX() - s * 0.5f, plot.centerY() - s * 0.5f, s, s);
    }

    private void measure(Rect canvas) {
        this.canvas = canvas;
        this.unitW = Math.max(1.0f, canvas.w - PLOT_PAD * 2.0f);
        this.unitH = Math.max(1.0f, canvas.h - PLOT_PAD * 2.0f);
    }

    private void project(ScanLayout.Result layout) {
        this.screen.clear();
        for (final Map.Entry<BlockPos, float[]> entry : layout.positions().entrySet()) {
            final float[] n = entry.getValue();
            final float[] off = this.offsets.getOrDefault(entry.getKey(), ZERO);
            this.screen.put(entry.getKey(), toScreen(n[0], n[1], off[0], off[1]));
        }
    }

    private float[] toScreen(float nx, float ny, float offX, float offY) {
        final float cx = this.canvas.centerX();
        final float cy = this.canvas.centerY();
        final float bx = this.canvas.x + PLOT_PAD + nx * this.unitW + offX;
        final float by = this.canvas.y + PLOT_PAD + ny * this.unitH + offY;
        return new float[]{cx + (bx - cx) * this.zoom + this.panX,
                cy + (by - cy) * this.zoom + this.panY};
    }

    private static final float[] ZERO = {0.0f, 0.0f};

    private void fit(ScanLayout.Result layout) {
        this.zoom = 1.0f;
        this.panX = 0.0f;
        this.panY = 0.0f;
        if (layout.positions().isEmpty()) {
            return;
        }
        float x0 = Float.MAX_VALUE;
        float y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE;
        float y1 = -Float.MAX_VALUE;
        for (final float[] n : layout.positions().values()) {
            x0 = Math.min(x0, n[0]);
            y0 = Math.min(y0, n[1]);
            x1 = Math.max(x1, n[0]);
            y1 = Math.max(y1, n[1]);
        }
        final float spanX = Math.max(1.0e-3f, x1 - x0);
        final float spanY = Math.max(1.0e-3f, y1 - y0);

        this.zoom = Math.max(MIN_ZOOM, Math.min(2.2f, Math.min(1.0f / spanX, 1.0f / spanY)));
        final float cx = this.canvas.centerX();
        final float cy = this.canvas.centerY();
        final float midX = this.canvas.x + PLOT_PAD + (x0 + x1) * 0.5f * this.unitW;
        final float midY = this.canvas.y + PLOT_PAD + (y0 + y1) * 0.5f * this.unitH;
        this.panX = -(midX - cx) * this.zoom;
        this.panY = -(midY - cy) * this.zoom;
    }

    private void buildHighlight(ScanLayout.Result layout) {
        this.highlight.clear();
        if (this.ctx.selected == null || layout.position(this.ctx.selected) == null) {
            return;
        }
        this.highlight.addAll(layout.pathToRoot(this.ctx.selected));
    }

    private float appear(ScanLayout.Result layout, BlockPos pos) {
        if (this.reveal >= 1.0f) {
            return 1.0f;
        }
        final float front = this.reveal * (layout.depth() + 2.0f);
        return Easing.clamp01(front - layout.rank(pos));
    }

    private float emphasis(ScanLayout.Result layout, BlockPos pos) {
        final float a = appear(layout, pos);
        if (this.highlight.isEmpty()) {
            return a;
        }
        return this.highlight.contains(pos) ? a : a * DIM;
    }

    private void drawPlanGrid(PanelSurface c, ScanLayout.Result layout) {
        final int[] span = layout.worldSpan();
        final float extent = Math.max(1.0f, Math.max(span[0], span[2]));
        final float centreX = span[4] + span[0] * 0.5f;
        final float centreZ = span[5] + span[2] * 0.5f;

        final int step = extent > 320.0f ? 64 : extent > 160.0f ? 32 : 16;
        for (int wx = Math.floorDiv(span[4], step) * step; wx <= span[4] + span[0] + step; wx += step) {
            final float nx = 0.5f + (wx - centreX) / extent * 0.92f;
            final float x = toScreen(nx, 0.0f, 0.0f, 0.0f)[0];
            if (x >= this.plot.x && x <= this.plot.right()) {
                c.hairlineV(x, this.plot.y, this.plot.h, Tokens.alpha(Tokens.LINE, 0.55f));
            }
        }
        for (int wz = Math.floorDiv(span[5], step) * step; wz <= span[5] + span[2] + step; wz += step) {
            final float nz = 0.5f + (wz - centreZ) / extent * 0.92f;
            final float y = toScreen(0.0f, nz, 0.0f, 0.0f)[1];
            if (y >= this.plot.y && y <= this.plot.bottom()) {
                c.hairlineH(this.plot.x, y, this.plot.w, Tokens.alpha(Tokens.LINE, 0.55f));
            }
        }

        final float nx = this.plot.right() - 16.0f;
        final float ny = this.plot.y + 18.0f;
        Shapes.line(c, nx, ny + 7.0f, nx, ny - 7.0f, 1.2f, Tokens.INK_3);
        Shapes.line(c, nx, ny - 7.0f, nx - 3.0f, ny - 2.0f, 1.2f, Tokens.INK_3);
        Shapes.line(c, nx, ny - 7.0f, nx + 3.0f, ny - 2.0f, 1.2f, Tokens.INK_3);
        c.textCenterV("N", nx, ny - 13.0f, Tokens.INK_3, Text.MICRO);

        final float ax = toScreen(0.5f, 0.0f, 0.0f, 0.0f)[0];
        final float bx = toScreen(0.5f + step / extent * 0.92f, 0.0f, 0.0f, 0.0f)[0];
        final float barW = Math.abs(bx - ax);
        if (barW > 12.0f && barW < this.plot.w * 0.6f) {
            final float y = this.plot.bottom() - 8.0f;
            Shapes.line(c, this.plot.x + 6.0f, y, this.plot.x + 6.0f + barW, y, 1.4f, Tokens.INK_3);
            Shapes.line(c, this.plot.x + 6.0f, y - 3.0f, this.plot.x + 6.0f, y + 3.0f, 1.2f, Tokens.INK_3);
            Shapes.line(c, this.plot.x + 6.0f + barW, y - 3.0f, this.plot.x + 6.0f + barW, y + 3.0f,
                    1.2f, Tokens.INK_3);
            c.textV(step + "m", this.plot.x + 10.0f + barW, y, Tokens.INK_3, Text.MICRO);
        }
    }

    private void drawRings(PanelSurface c, ScanLayout.Result layout) {
        final int depth = layout.depth();
        if (depth <= 1) {
            return;
        }
        final float[] centre = toScreen(0.5f, 0.5f, 0.0f, 0.0f);
        for (int rank = 1; rank < depth; rank++) {
            final float n = 0.5f + 0.46f * (rank / (float) (depth - 1));
            final float radius = Math.abs(toScreen(n, 0.5f, 0.0f, 0.0f)[0] - centre[0]);
            if (radius < 4.0f || radius > this.plot.w) {
                continue;
            }
            Shapes.arc(c, centre[0], centre[1], radius, 1.0f, 0.0f, (float) (Math.PI * 2.0),
                    Tokens.alpha(Tokens.LINE, rank % 2 == 0 ? 0.75f : 0.45f));
        }
    }

    private void drawEdges(PanelSurface c, ScanGraph g, ScanLayout.Result layout) {
        final boolean power = this.ctx.mode == ScanLayoutMode.POWER;
        final float peak = Math.max(1.0e-3f, layout.maxSubtreeStress());
        for (final ScanEdge edge : g.edges()) {
            final float[] from = this.screen.get(edge.from());
            final float[] to = this.screen.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            final float a = Math.min(emphasis(layout, edge.from()), emphasis(layout, edge.to()));
            if (a <= 0.01f) {
                continue;
            }
            final boolean primary = layout.isPrimaryEdge(edge);
            final boolean onPath = !this.highlight.isEmpty()
                    && this.highlight.contains(edge.from()) && this.highlight.contains(edge.to());
            final int color = onPath ? 0xFFB6ECFF
                    : edge.isRatioChange() ? Tokens.ACCENT
                    : primary ? Tokens.alpha(0xFF4EA8FF, 0.9f)
                    : Tokens.alpha(Tokens.LINE_HI, 0.35f);

            float width = edge.isRatioChange() ? 2.0f : primary ? 1.6f : 1.0f;
            if (power && primary) {
                // sqrt widths or the twigs vanish next to the trunk

                width = 1.2f + 5.0f * (float) Math.sqrt(
                        Math.min(1.0f, layout.carried(edge) / peak));
            }
            if (onPath) {
                width += 1.0f;
            }
            Shapes.line(c, from[0], from[1], to[0], to[1], width * this.zoom,
                    Tokens.fade(color, a));
        }
    }

    private void drawFlow(PanelSurface c, ScanGraph g, ScanLayout.Result layout) {
        if (Motion.reducedMotion() || this.reveal < 1.0f) {
            return;
        }
        final float phase = (Motion.wallMillis() % (long) FLOW_PERIOD_MS) / FLOW_PERIOD_MS;
        for (final ScanEdge edge : g.edges()) {
            if (!layout.isPrimaryEdge(edge)) {
                continue;
            }
            final float[] from = this.screen.get(edge.from());
            final float[] to = this.screen.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            final float a = Math.min(emphasis(layout, edge.from()), emphasis(layout, edge.to()));
            if (a <= 0.2f) {
                continue;
            }

            final ScanNode source = g.node(edge.from());
            if (source == null || Math.abs(source.speed()) < 0.01f) {
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
            final float half = Math.min(3.5f * this.zoom, span * 0.2f);
            for (int d = 0; d < 2; d++) {
                float t = phase + d * 0.5f;
                if (t >= 1.0f) {
                    t -= 1.0f;
                }

                final float px = from[0] + (to[0] - from[0]) * t;
                final float py = from[1] + (to[1] - from[1]) * t;
                Shapes.line(c, px - dx * half, py - dy * half, px + dx * half, py + dy * half,
                        1.8f * this.zoom, Tokens.fade(Tokens.alpha(0xFFB6ECFF, 0.9f), a));
            }
        }
    }

    private void drawNodes(PanelSurface c, ScanGraph g, ScanLayout.Result layout) {
        this.hovered = null;
        final boolean detail = g.size() <= GLYPH_BUDGET && this.zoom >= 0.85f;
        final boolean power = this.ctx.mode == ScanLayoutMode.POWER;
        final boolean plan = this.ctx.mode == ScanLayoutMode.PLAN;
        final float peak = Math.max(1.0e-3f, heaviestDraw(g));

        for (final Map.Entry<BlockPos, float[]> entry : this.screen.entrySet()) {
            final ScanNode node = g.node(entry.getKey());
            if (node == null) {
                continue;
            }
            final float[] p = entry.getValue();
            final float a = emphasis(layout, entry.getKey());
            if (a <= 0.01f) {
                continue;
            }
            float r = radiusFor(node) * this.zoom;
            if (power) {
                r = (2.4f + 5.0f * (float) Math.sqrt(Math.min(1.0f, node.drawnStress() / peak)))
                        * this.zoom;
            }
            final boolean isHovered = Math.hypot(this.mouseX - p[0], this.mouseY - p[1])
                    <= Math.max(HIT_RADIUS, r + 2.0f) && this.plot.contains(this.mouseX, this.mouseY);
            if (isHovered) {
                this.hovered = entry.getKey();
            }

            if (node.isOverStressed()) {
                final float pulse = 0.5f + 0.5f
                        * (float) Math.sin(Motion.wallMillis() / 500.0 * Math.PI);
                Shapes.circle(c, p[0], p[1], r + 3.5f,
                        Tokens.fade(Tokens.alpha(Tokens.CRITICAL, 0.25f + pulse * 0.45f), a));
            }
            if (entry.getKey().equals(this.ctx.selected)) {
                Shapes.circle(c, p[0], p[1], r + 3.4f, Tokens.fade(Tokens.ACCENT, a * 0.9f));
            } else if (layout.isPrimaryRoot(entry.getKey())) {
                Shapes.circle(c, p[0], p[1], r + 2.6f, Tokens.fade(Tokens.alpha(Tokens.GOOD, 0.5f), a));
            }
            Shapes.circle(c, p[0], p[1], r + 1.2f, Tokens.fade(Tokens.SURFACE, a));

            int color = colorFor(node);
            if (plan) {
                color = Tokens.mix(color, Tokens.CANVAS, 0.42f * (1.0f - layout.elevation(entry.getKey())));
            }
            if (detail) {
                glyph(c, node.kind(), p[0], p[1], r, Tokens.fade(color, a));
            } else {
                Shapes.circle(c, p[0], p[1], r, Tokens.fade(color, a));
            }
            if (isHovered || (this.zoom >= 1.8f && node.kind() != ScanNodeKind.TRANSFER)) {
                c.textCenterV(Text.truncate(c, node.name(), 120.0f, Text.MICRO),
                        p[0], p[1] - r - 7.0f, Tokens.fade(Tokens.INK_2, a), Text.MICRO);
            }
        }
    }

    private static float heaviestDraw(ScanGraph g) {
        float peak = 0.0f;
        for (final ScanNode n : g.nodes().values()) {
            peak = Math.max(peak, n.drawnStress());
        }
        return peak;
    }

    private static float radiusFor(ScanNode node) {
        return switch (node.kind()) {
            case SOURCE, MACHINE -> 4.0f;
            case GEARING, INSTRUMENT -> 3.5f;
            default -> 2.6f;
        };
    }

    private static void glyph(PanelSurface c, ScanNodeKind kind, float x, float y, float r, int color) {
        switch (kind) {
            case SOURCE -> {
                Shapes.circle(c, x, y, r, color);

                for (int i = 0; i < 4; i++) {
                    final double a = i * Math.PI / 4.0;
                    Shapes.line(c, x - (float) Math.cos(a) * (r + 2.2f),
                            y - (float) Math.sin(a) * (r + 2.2f),
                            x + (float) Math.cos(a) * (r + 2.2f),
                            y + (float) Math.sin(a) * (r + 2.2f), 1.0f, color);
                }
            }
            case GEARING -> {
                Shapes.arc(c, x, y, r * 0.72f, Math.max(1.2f, r * 0.55f), 0.0f,
                        (float) (Math.PI * 2.0), color);
                for (int i = 0; i < 6; i++) {
                    final double a = i * Math.PI / 3.0;
                    Shapes.line(c, x + (float) Math.cos(a) * r * 0.9f,
                            y + (float) Math.sin(a) * r * 0.9f,
                            x + (float) Math.cos(a) * (r + 1.8f),
                            y + (float) Math.sin(a) * (r + 1.8f), 1.3f, color);
                }
            }
            case MACHINE -> Shapes.roundedRect(c,
                    new Rect(x - r, y - r, r * 2.0f, r * 2.0f), r * 0.35f, color);
            case INSTRUMENT -> {
                final float d = r * 1.25f;
                Shapes.quad(c, x, y - d, x + d, y, x, y + d, x - d, y, color, color, color, color);
            }
            case TRANSFER -> Shapes.circle(c, x, y, r * 0.8f, color);
            default -> {
                Shapes.circle(c, x, y, r, color);
                Shapes.circle(c, x, y, Math.max(0.8f, r - 1.4f), Tokens.SURFACE);
            }
        }
    }

    public static int colorFor(ScanNode node) {
        if (node.isOverStressed()) {
            return Tokens.CRITICAL;
        }
        return switch (node.kind()) {
            case SOURCE -> Tokens.GOOD;
            case GEARING -> Tokens.ACCENT;
            case MACHINE -> Tokens.SERIES[0];
            case INSTRUMENT -> Tokens.SERIES[4];
            case TRANSFER -> 0xFFA4A4A4;
            default -> Tokens.INK_3;
        };
    }

    private void drawLegend(PanelSurface c, ScanLayoutMode mode) {
        final float y = this.card.bottom() - 20.0f;
        float x = this.card.x + Tokens.S3;
        x = legend(c, x, y, ScanNodeKind.SOURCE, "source");
        x = legend(c, x, y, ScanNodeKind.MACHINE, "machine");
        x = legend(c, x, y, ScanNodeKind.GEARING, "gearing");
        x = legend(c, x, y, ScanNodeKind.TRANSFER, "transfer");
        x = legend(c, x, y, ScanNodeKind.INSTRUMENT, "instrument");
        Shapes.line(c, x + 1.0f, y, x + 11.0f, y, 2.0f, Tokens.ACCENT);
        c.textV("ratio change", x + 15.0f, y, Tokens.INK_3, Text.MICRO);
        x += 19.0f + c.textWidth("ratio change", Text.MICRO);
        if (mode == ScanLayoutMode.POWER) {
            c.textV("width = stress carried", x, y, Tokens.INK_3, Text.MICRO);
        } else if (mode == ScanLayoutMode.PLAN) {
            c.textV("brighter = higher", x, y, Tokens.INK_3, Text.MICRO);
        }
    }

    private static float legend(PanelSurface c, float x, float y, ScanNodeKind kind, String label) {
        glyph(c, kind, x + 4.0f, y, 3.2f, kindColor(kind));
        c.textV(label, x + 11.0f, y, Tokens.INK_3, Text.MICRO);
        return x + 11.0f + c.textWidth(label, Text.MICRO) + 10.0f;
    }

    private static int kindColor(ScanNodeKind kind) {
        return switch (kind) {
            case SOURCE -> Tokens.GOOD;
            case GEARING -> Tokens.ACCENT;
            case MACHINE -> Tokens.SERIES[0];
            case INSTRUMENT -> Tokens.SERIES[4];
            case TRANSFER -> 0xFFA4A4A4;
            default -> Tokens.INK_3;
        };
    }

    private void drawFooter(PanelSurface c, ScanGraph g, ScanLayout.Result layout) {
        final float y = this.card.bottom() - 8.0f;
        c.textV(Text.truncate(c, summary(g, layout), this.card.w * 0.55f, Text.MICRO),
                this.card.x + Tokens.S3, y, Tokens.INK_3, Text.MICRO);

        final String right;
        int color = Tokens.INK_3;
        if (g.truncated()) {
            right = "survey cut off at " + this.ctx.tier.elementBudget() + " elements";
            color = Tokens.CAUTION;
        } else if (!g.unloadedBoundary().isEmpty()) {
            right = g.unloadedBoundary().size() + " beyond loaded chunks";
            color = Tokens.CAUTION;
        } else if (this.hovered != null && g.node(this.hovered) != null) {
            right = g.node(this.hovered).name() + "  "
                    + Text.value(g.node(this.hovered).speed()) + " rpm";
        } else {
            right = "drag to pan · scroll to zoom · F fit · M mode";
        }
        c.textRightV(Text.truncate(c, right, this.card.w * 0.45f, Text.MICRO),
                this.card.right() - Tokens.S3, y, color, Text.MICRO);
    }

    private String summary(ScanGraph g, ScanLayout.Result layout) {
        return switch (this.ctx.mode) {
            case RING -> layout.depth() + " rings  ·  " + g.size() + " elements  ·  "
                    + widestRank(layout) + " in the widest";
            case POWER -> "trunk carries " + Text.compact(layout.maxSubtreeStress()) + " su  ·  "
                    + heaviestBranch(g, layout);
            case PLAN -> planSpan(layout);
            default -> layout.depth() + " steps deep  ·  " + g.size() + " elements  ·  "
                    + g.edges().size() + " links";
        };
    }

    private static int widestRank(ScanLayout.Result layout) {
        final Map<Integer, Integer> counts = new HashMap<>();
        int widest = 0;
        for (final BlockPos pos : layout.positions().keySet()) {
            widest = Math.max(widest, counts.merge(layout.rank(pos), 1, Integer::sum));
        }
        return widest;
    }

    private static String heaviestBranch(ScanGraph g, ScanLayout.Result layout) {
        BlockPos at = layout.primaryRoot();
        if (at == null) {
            return "no source found";
        }
        float load = 0.0f;
        final Set<BlockPos> seen = new HashSet<>();
        while (seen.add(at)) {
            BlockPos best = null;
            float bestLoad = 0.0f;
            for (final BlockPos child : layout.children(at)) {
                final float childLoad = layout.subtreeStress(child);
                if (childLoad > bestLoad) {
                    bestLoad = childLoad;
                    best = child;
                }
            }
            if (best == null) {
                break;
            }
            at = best;
            load = bestLoad;
            final ScanNode node = g.node(at);
            if (node == null || node.kind() != ScanNodeKind.TRANSFER
                    || layout.children(at).size() > 1) {
                break;
            }
        }
        final ScanNode node = g.node(at);
        return node == null || layout.isPrimaryRoot(at) ? "one branch"
                : "heaviest branch via " + node.name() + " at " + Text.compact(load) + " su";
    }

    private static String planSpan(ScanLayout.Result layout) {
        final int[] span = layout.worldSpan();
        return String.format(Locale.ROOT, "%d x %d blocks  ·  y %d to %d",
                span[0] + 1, span[2] + 1, span[3], span[3] + span[1]);
    }

    private void drawTooltip(PanelSurface c, ScanGraph g, ScanLayout.Result layout) {
        if (this.hovered == null) {
            return;
        }
        final ScanNode node = g.node(this.hovered);
        final float[] at = this.screen.get(this.hovered);
        if (node == null || at == null) {
            return;
        }

        final List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Speed", Text.value(node.speed()) + " rpm"});
        if (node.isSource()) {
            rows.add(new String[]{"Provides", Text.compact(node.capacityProvided()) + " su"});
        } else if (node.drawnStress() > 0.0f) {
            rows.add(new String[]{"Draws", Text.compact(node.drawnStress()) + " su"});
            final float share = g.totalStress() <= 0.0f ? 0.0f : node.drawnStress() / g.totalStress();
            rows.add(new String[]{"Share of load", String.format(Locale.ROOT, "%.1f%%", share * 100.0f)});
        }
        final float carried = layout.subtreeStress(this.hovered);
        if (carried > node.drawnStress() + 0.01f) {
            rows.add(new String[]{"Carries", Text.compact(carried) + " su downstream"});
        }
        final int rank = layout.rank(this.hovered);
        rows.add(new String[]{"Distance", rank == 0 ? "at the source"
                : rank + (rank == 1 ? " step from source" : " steps from source")});
        final BlockPos parent = layout.parent(this.hovered);
        if (parent != null) {
            final float ratio = ratioBetween(g, parent, this.hovered);
            if (Math.abs(Math.abs(ratio) - 1.0f) > 1.0e-4f) {
                rows.add(new String[]{"Geared", "x" + Text.value(ratio) + " into this element"});
            } else if (ratio < 0.0f) {
                rows.add(new String[]{"Geared", "reversed, same speed"});
            }
        }
        final int dy = this.hovered.getY() - this.ctx.pos.getY();
        rows.add(new String[]{"At", this.hovered.toShortString()
                + (dy == 0 ? "" : dy > 0 ? "  (y+" + dy + ")" : "  (y" + dy + ")")});

        float labelW = 0.0f;
        float valueW = 0.0f;
        for (final String[] row : rows) {
            labelW = Math.max(labelW, c.textWidth(row[0], Text.MICRO));
            valueW = Math.max(valueW, c.textWidth(row[1], Text.MICRO));
        }
        final float w = Math.max(c.textWidth(node.name(), Text.META) + Tokens.S4,
                labelW + valueW + Tokens.S5) + Tokens.S4;
        final float h = 30.0f + rows.size() * 12.0f;

        float x = at[0] + 14.0f;
        float y = at[1] + 12.0f;
        if (x + w > this.card.right() - Tokens.S2) {
            x = at[0] - 14.0f - w;
        }
        if (y + h > this.card.bottom() - Tokens.S2) {
            y = at[1] - 12.0f - h;
        }
        x = Math.max(this.card.x + Tokens.S2, x);
        y = Math.max(this.card.y + HEADER_H, y);
        final Rect tip = new Rect(x, y, w, h);

        c.shadow(tip, Tokens.R_CARD, 8.0f, 0x50000000);
        c.card(tip, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);
        glyph(c, node.kind(), tip.x + Tokens.S3, tip.y + 12.0f, 3.4f, colorFor(node));
        c.text(Text.truncate(c, node.name(), tip.w - Tokens.S5 - Tokens.S2, Text.META),
                tip.x + Tokens.S4 + 2.0f, tip.y + 7.0f, Tokens.INK, Text.META);
        c.text(node.kind().getSerializedName(), tip.x + Tokens.S3, tip.y + 20.0f,
                Tokens.INK_3, Text.MICRO);
        float ry = tip.y + 36.0f;
        for (final String[] row : rows) {
            c.textV(row[0], tip.x + Tokens.S3, ry, Tokens.INK_3, Text.MICRO);
            c.textRightV(row[1], tip.right() - Tokens.S3, ry,
                    node.isOverStressed() && "Draws".equals(row[0]) ? Tokens.CRITICAL : Tokens.INK_2,
                    Text.MICRO);
            ry += 12.0f;
        }
    }

    private static float ratioBetween(ScanGraph g, BlockPos a, BlockPos b) {
        for (final ScanEdge edge : g.edges()) {
            if ((edge.from().equals(a) && edge.to().equals(b))
                    || (edge.from().equals(b) && edge.to().equals(a))) {
                return edge.speedRatio();
            }
        }
        return 1.0f;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.modeControl != null && this.modeControl.mouseDown(mx, my, button)) {
            return true;
        }
        if (!this.plot.contains(mx, my)) {
            return false;
        }
        final BlockPos hit = nodeAt(mx, my);
        if (hit != null) {
            this.ctx.selected = hit.equals(this.ctx.selected) ? null : hit;
            this.dragging = hit;
            return true;
        }
        this.panning = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.modeControl != null) {
            this.modeControl.mouseUp(mx, my, button);
        }
        this.dragging = null;
        this.panning = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging != null) {
            final float[] off = this.offsets.computeIfAbsent(this.dragging, k -> new float[2]);
            off[0] += (float) (dx / this.zoom);
            off[1] += (float) (dy / this.zoom);
            return true;
        }
        if (this.panning) {
            this.panX += (float) dx;
            this.panY += (float) dy;
            clampPan();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!this.plot.contains(mx, my)) {
            return false;
        }
        final float before = this.zoom;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                this.zoom * (amount > 0 ? 1.15f : 1.0f / 1.15f)));

        final float scale = this.zoom / before;
        this.panX = (float) (mx - (mx - this.panX) * scale);
        this.panY = (float) (my - (my - this.panY) * scale);
        clampPan();
        return true;
    }

    private void clampPan() {
        final float cx = this.canvas.centerX();
        final float cy = this.canvas.centerY();
        this.panX = Math.max(this.plot.x - cx, Math.min(this.plot.right() - cx, this.panX));
        this.panY = Math.max(this.plot.y - cy, Math.min(this.plot.bottom() - cy, this.panY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> {
                this.zoom = 1.0f;
                this.panX = 0.0f;
                this.panY = 0.0f;
                this.offsets.clear();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_F -> {
                this.fitPending = true;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_M -> {
                this.ctx.mode = ScanLayoutMode.byOrdinal(
                        (this.ctx.mode.ordinal() + 1) % ScanLayoutMode.values().length);
                this.offsets.clear();
                this.fitPending = true;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                if (this.ctx.selected != null) {
                    this.ctx.selected = null;
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private BlockPos nodeAt(double mx, double my) {
        BlockPos best = null;
        double bestDistance = HIT_RADIUS * HIT_RADIUS;
        for (final Map.Entry<BlockPos, float[]> entry : this.screen.entrySet()) {
            final float[] p = entry.getValue();
            final double d = (mx - p[0]) * (mx - p[0]) + (my - p[1]) * (my - p[1]);
            if (d <= bestDistance) {
                bestDistance = d;
                best = entry.getKey();
            }
        }
        return best;
    }
}
