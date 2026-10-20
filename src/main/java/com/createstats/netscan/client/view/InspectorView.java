package com.createstats.netscan.client.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InspectorView extends ScanView {
    private static final int[] CURVE_RPM = {16, 32, 64, 128, 256};

    private Rect header = Rect.EMPTY;
    private Rect stats = Rect.EMPTY;
    private Rect curve = Rect.EMPTY;
    private Rect path = Rect.EMPTY;

    public InspectorView(ScanContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "inspector";
    }

    @Override
    public String label() {
        return "Inspector";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.roundedRectOutline(c, new Rect(cx - 5.5f, cy - 5.5f, 11.0f, 11.0f), 2.0f, 1.2f, color);
        Shapes.circle(c, cx, cy, 2.2f, color);
    }

    @Override
    protected void build() {
        final Rect[] rows = Layout.column(this.area, Tokens.S3, 46.0f, 52.0f, 78.0f, 0.0f);
        this.header = rows[0];
        this.stats = rows[1];
        this.curve = rows[2];
        this.path = new Rect(rows[3].x, rows[3].y, rows[3].w,
                Math.max(0.0f, this.area.bottom() - rows[3].y));
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final ScanGraph g = this.ctx.graph;
        final ScanNode node = resolve(g);
        if (node == null) {
            c.textCenterV("Nothing to inspect", this.area.centerX(), this.area.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        drawHeader(c, node);
        drawStats(c, g, node);

        if (this.ctx.allows(2)) {
            drawCurve(c, node);
        } else {
            locked(c, this.curve, "The speed and stress curve", 2);
        }
        drawPath(c, g, node);
    }

    private ScanNode resolve(ScanGraph g) {
        if (g.isEmpty()) {
            return null;
        }
        final ScanNode selected = this.ctx.selected == null ? null : g.node(this.ctx.selected);
        if (selected != null) {
            return selected;
        }
        final List<ScanNode> ranked = g.byLoad();
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    private void drawHeader(PanelSurface c, ScanNode node) {
        c.card(this.header, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.truncate(c, node.name(), this.header.w - Tokens.S4 * 2.0f, Text.SUB),
                this.header.x + Tokens.S3, this.header.y + 8.0f, Tokens.INK, Text.SUB);
        final String meta = node.kind().getSerializedName() + "  ·  " + node.pos().toShortString();
        c.text(Text.truncate(c, meta, this.header.w * 0.5f, Text.MICRO),
                this.header.x + Tokens.S3, this.header.y + 28.0f, Tokens.INK_3, Text.MICRO);

        final String downstream = downstream(node);
        if (!downstream.isEmpty()) {
            c.textRight(Text.truncate(c, downstream, this.header.w * 0.5f, Text.MICRO),
                    this.header.right() - Tokens.S5, this.header.y + 28.0f, Tokens.INK_2, Text.MICRO);
        }
        Shapes.circle(c, this.header.right() - Tokens.S4, this.header.centerY(), 4.5f,
                MapView.colorFor(node));
    }

    private String downstream(ScanNode node) {
        final ScanLayout.Result layout = this.ctx.layout();
        final int count = subtreeSize(layout, node.pos());
        if (count <= 0) {
            return "";
        }
        final float carried = layout.subtreeStress(node.pos()) - node.drawnStress();
        return (carried > 0.0f ? "carries " + Text.compact(carried) + " su to " : "drives ")
                + count + (count == 1 ? " element" : " elements") + " downstream";
    }

    private static int subtreeSize(ScanLayout.Result layout, BlockPos pos) {
        int count = 0;
        final java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(layout.children(pos));
        final Set<BlockPos> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            final BlockPos next = queue.poll();
            if (!seen.add(next)) {
                continue;
            }
            count++;
            queue.addAll(layout.children(next));
        }
        return count;
    }

    private void drawStats(PanelSurface c, ScanGraph g, ScanNode node) {
        final Rect[] cells = Layout.columns(this.stats, this.ctx.allows(2) ? 4 : 2, Tokens.S2);
        stat(c, cells[0], "Speed", Text.value(node.speed()) + " rpm",
                Math.abs(node.speed()) < 0.01f ? Tokens.INK_3 : Tokens.INK);
        final float share = g.totalStress() <= 0.0f ? 0.0f : node.drawnStress() / g.totalStress();
        stat(c, cells[1], "Share of load", String.format(Locale.ROOT, "%.1f%%", share * 100.0f),
                share >= 0.35f ? Tokens.CAUTION : Tokens.INK);
        if (this.ctx.allows(2)) {
            stat(c, cells[2], "Stress drawn", Text.value(node.drawnStress()) + " su",
                    node.isOverStressed() ? Tokens.CRITICAL : Tokens.INK);
            stat(c, cells[3], "Per rpm", Text.value(node.stressCoefficient()) + " su", Tokens.INK);
        }
    }

    private void drawCurve(PanelSurface c, ScanNode node) {
        final Rect body = card(c, this.curve, "Stress at speed");
        if (node.stressCoefficient() <= 0.0f) {
            c.textCenterV("Costs no stress at any speed", body.centerX(), body.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }
        final float peak = node.stressCoefficient() * CURVE_RPM[CURVE_RPM.length - 1];
        final Rect[] cells = Layout.columns(body, CURVE_RPM.length, Tokens.S2);
        for (int i = 0; i < CURVE_RPM.length; i++) {
            final float value = node.stressCoefficient() * CURVE_RPM[i];
            final float frac = peak <= 0.0f ? 0.0f : value / peak;
            final Rect cell = cells[i];
            final float barTop = cell.bottom() - 14.0f - frac * Math.max(1.0f, cell.h - 26.0f);
            final boolean here = Math.abs(Math.abs(node.speed()) - CURVE_RPM[i]) < CURVE_RPM[i] * 0.2f;
            Shapes.bar(c, new Rect(cell.x + 2.0f, barTop, cell.w - 4.0f, cell.bottom() - 14.0f - barTop),
                    here ? Tokens.ACCENT : Tokens.alpha(Tokens.SERIES[0], 0.75f));
            c.textCenterV(Text.compact(value), cell.centerX(), barTop - 6.0f,
                    here ? Tokens.INK : Tokens.INK_2, Text.MICRO);
            c.textCenterV(CURVE_RPM[i] + " rpm", cell.centerX(), cell.bottom() - 6.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private void drawPath(PanelSurface c, ScanGraph g, ScanNode node) {
        final Rect body = card(c, this.path, "Upstream path");
        final List<ScanNode> chain = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        ScanNode current = node;
        while (current != null && seen.add(current.pos()) && chain.size() < 12) {
            chain.add(current);
            final BlockPos up = current.upstream();
            current = up == null ? null : g.node(up);
        }
        if (chain.size() <= 1) {
            c.text(chain.isEmpty() || chain.get(0).isSource()
                            ? "This element is the source" : "No upstream recorded",
                    body.x, body.y + 2.0f, Tokens.INK_3, Text.META);
            return;
        }
        float y = body.y + 2.0f;
        for (int i = chain.size() - 1; i >= 0 && y < body.bottom() - 10.0f; i--) {
            final ScanNode step = chain.get(i);
            Shapes.circle(c, body.x + 4.0f, y + 5.0f, 3.0f, MapView.colorFor(step));
            if (i > 0) {
                Shapes.line(c, body.x + 4.0f, y + 9.0f, body.x + 4.0f, y + 18.0f, 1.0f, Tokens.LINE_HI);
            }
            c.textV(Text.truncate(c, step.name(), body.w * 0.55f, Text.META),
                    body.x + 14.0f, y + 5.0f, i == 0 ? Tokens.INK : Tokens.INK_2, Text.META);
            final String rate = Text.value(step.speed()) + " rpm";
            c.textRightV(rate, body.right(), y + 5.0f, Tokens.INK_3, Text.MICRO);
            if (i < chain.size() - 1) {
                final float ratio = ratioBetween(g, chain.get(i + 1).pos(), step.pos());
                if (Math.abs(Math.abs(ratio) - 1.0f) > 1.0e-4f) {
                    c.textRightV("x" + Text.value(ratio), body.right() - 54.0f, y + 5.0f,
                            Tokens.ACCENT, Text.MICRO);
                } else if (ratio < 0.0f) {
                    c.textRightV("reversed", body.right() - 54.0f, y + 5.0f,
                            Tokens.INK_3, Text.MICRO);
                }
            }
            y += 18.0f;
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
}
