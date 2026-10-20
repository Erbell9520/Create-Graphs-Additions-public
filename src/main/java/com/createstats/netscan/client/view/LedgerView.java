package com.createstats.netscan.client.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// sort by drawn stress, not the JEI coefficient. the coefficient names the wrong machine every time
@OnlyIn(Dist.CLIENT)
public final class LedgerView extends ScanView {
    private static final float ROW_H = 20.0f;
    private static final float HEADER_H = 20.0f;

    private static final float BAR_W = 3.0f;

    private enum Sort {
        LOAD("Stress", Comparator.comparingDouble(n -> -((ScanNode) n).drawnStress())),
        SPEED("Speed", Comparator.comparingDouble(n -> -Math.abs(((ScanNode) n).speed()))),
        NAME("Name", Comparator.comparing(n -> ((ScanNode) n).name())),
        KIND("Role", Comparator.comparing(n -> ((ScanNode) n).kind().ordinal()));

        private final String label;
        private final Comparator<ScanNode> comparator;

        @SuppressWarnings("unchecked")
        Sort(String label, Comparator<?> comparator) {
            this.label = label;
            this.comparator = (Comparator<ScanNode>) comparator;
        }
    }

    private final Scroll scroll = new Scroll();
    private Sort sort = Sort.LOAD;
    private Rect table = Rect.EMPTY;
    private final List<Rect> headerCells = new ArrayList<>();

    public LedgerView(ScanContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "ledger";
    }

    @Override
    public String label() {
        return "Ledger";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        for (int i = 0; i < 3; i++) {
            Shapes.rect(c, cx - 5.5f, cy - 5.0f + i * 4.5f, 11.0f, 1.6f, color);
        }
    }

    @Override
    protected void build() {
        this.table = this.area;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final ScanGraph g = this.ctx.graph;
        c.card(this.table, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        if (g.isEmpty()) {
            c.textCenterV("No elements surveyed", this.table.centerX(), this.table.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        final List<ScanNode> rows = new ArrayList<>(g.nodes().values());
        rows.sort(this.sort.comparator);

        final Rect inner = this.table.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        drawHeader(c, inner.top(HEADER_H));

        final Rect body = inner.below(HEADER_H + 2.0f);
        this.scroll.setMetrics(rows.size() * ROW_H, body.h);

        final float rowW = this.scroll.scrollable() ? body.w - BAR_W - Tokens.S1 : body.w;
        c.clip(body);
        final float top = body.y - this.scroll.value();
        for (int i = 0; i < rows.size(); i++) {
            final float y = top + i * ROW_H;
            if (y + ROW_H < body.y || y > body.bottom()) {
                continue;
            }
            drawRow(c, new Rect(body.x, y, rowW, ROW_H), rows.get(i), g, i);
        }
        c.unclip();
        this.scroll.drawBar(c, barTrack(body));
    }

    private void drawHeader(PanelSurface c, Rect r) {
        this.headerCells.clear();
        final float[] widths = columnWidths(r.w);
        float x = r.x;
        final Sort[] order = {Sort.NAME, Sort.KIND, Sort.SPEED, Sort.LOAD};
        for (int i = 0; i < order.length; i++) {
            final Rect cell = new Rect(x, r.y, widths[i], r.h);
            this.headerCells.add(cell);
            final boolean active = this.sort == order[i];
            final String label = order[i].label + (active ? " ▾" : "");
            if (i >= 2) {
                c.textRightV(label, cell.right() - 8.0f, cell.centerY(),
                        active ? Tokens.INK : Tokens.INK_3, Text.MICRO);
            } else {
                c.textV(label, cell.x, cell.centerY(), active ? Tokens.INK : Tokens.INK_3, Text.MICRO);
            }
            x += widths[i];
        }
        c.hairlineH(r.x, r.bottom(), r.w, Tokens.LINE);
    }

    private void drawRow(PanelSurface c, Rect r, ScanNode node, ScanGraph g, int index) {
        final boolean selected = node.pos().equals(this.ctx.selected);
        final boolean hovered = r.contains(this.mouseX, this.mouseY);

        if (this.ctx.allows(2) && g.totalStress() > 0.0f) {
            final float frac = Math.min(1.0f, node.drawnStress() / g.totalStress());
            if (frac > 0.001f) {
                Shapes.roundedRect(c, new Rect(r.x, r.y, Math.max(2.0f, r.w * frac), r.h),
                        Tokens.R_CHIP, Tokens.alpha(MapView.colorFor(node), 0.12f));
            }
        }
        if (selected) {
            Shapes.roundedRect(c, r, Tokens.R_CHIP, Tokens.ACCENT_DIM);
        } else if (hovered) {
            Shapes.roundedRect(c, r, Tokens.R_CHIP, Tokens.RAISED);
        } else if (index % 2 == 1) {
            Shapes.rect(c, r, Tokens.alpha(Tokens.RAISED, 0.35f));
        }

        final float[] widths = columnWidths(r.w);
        float x = r.x;
        Shapes.circle(c, x + 4.0f, r.centerY(), 3.0f, MapView.colorFor(node));
        c.textV(Text.truncate(c, node.name(), widths[0] - 14.0f, Text.META), x + 12.0f, r.centerY(),
                Tokens.INK, Text.META);
        x += widths[0];
        c.textV(node.kind().getSerializedName(), x, r.centerY(), Tokens.INK_3, Text.MICRO);
        x += widths[1];
        c.textRightV(Text.value(node.speed()), x + widths[2] - 8.0f, r.centerY(),
                Math.abs(node.speed()) < 0.01f ? Tokens.INK_3 : Tokens.INK_2, Text.META);
        x += widths[2];

        if (this.ctx.allows(2)) {
            c.textRightV(Text.compact(node.drawnStress()), x + widths[3] - 8.0f, r.centerY(),
                    node.isOverStressed() ? Tokens.CRITICAL : Tokens.INK_2, Text.META);
        } else {
            c.textRightV("--", x + widths[3] - 8.0f, r.centerY(), Tokens.INK_3, Text.META);
        }
    }

    private static float[] columnWidths(float total) {
        return new float[]{total * 0.44f, total * 0.20f, total * 0.16f, total * 0.20f};
    }

    private Rect bodyRect() {
        return this.table.inset(Tokens.S3, Tokens.S2 + HEADER_H + 2.0f, Tokens.S3, Tokens.S2);
    }

    private static Rect barTrack(Rect body) {
        return new Rect(body.right() - BAR_W, body.y, BAR_W, body.h);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        final Sort[] order = {Sort.NAME, Sort.KIND, Sort.SPEED, Sort.LOAD};
        for (int i = 0; i < this.headerCells.size() && i < order.length; i++) {
            if (this.headerCells.get(i).contains(mx, my)) {
                this.sort = order[i];
                return true;
            }
        }
        final Rect body = bodyRect();
        if (button == 0 && this.scroll.pressBar(barTrack(body), mx, my)) {
            return true;
        }
        if (!body.contains(mx, my) || this.ctx.graph.isEmpty()) {
            return false;
        }
        final List<ScanNode> rows = new ArrayList<>(this.ctx.graph.nodes().values());
        rows.sort(this.sort.comparator);
        final int index = (int) Math.floor((my - body.y + this.scroll.value()) / ROW_H);
        if (index >= 0 && index < rows.size()) {
            this.ctx.selected = rows.get(index).pos();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return this.scroll.dragBar(barTrack(bodyRect()), my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!this.scroll.dragging()) {
            return false;
        }
        this.scroll.endDrag();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return this.table.contains(mx, my) && this.scroll.wheel(amount);
    }
}
