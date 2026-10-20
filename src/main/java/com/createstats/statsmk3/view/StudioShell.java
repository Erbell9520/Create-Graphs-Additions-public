package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.page.Mk3Page;
import com.createstats.statsmk3.page.Mk3PageRegistry;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StudioShell {
    private static final int DIM = 0xC0070A0E;

    protected final StudioContext ctx;
    private final List<StudioView> views = new ArrayList<>();
    private final Spring railIndicator = new Spring(0.0f, 120.0f, 21.0f, 1.0f);

    private int activeIndex;

    private String pageSignature = "";
    private float pageRecheck;

    private boolean pointerDown;

    private StudioLayout box = StudioLayout.of(0.0f, 0.0f);

    public StudioShell(StatsDisplayPanelBE panel, StudioContext ctx) {
        this.ctx = ctx;
    }

    public StudioContext context() {
        return this.ctx;
    }

    public List<StudioView> views() {
        return this.views;
    }

    public int activeIndex() {
        return this.activeIndex;
    }

    public StudioLayout box() {
        return this.box;
    }

    public void layout(float width, float height) {
        this.box = StudioLayout.of(width, height);

        final List<StudioView> previous = new ArrayList<>(this.views);
        this.views.clear();
        this.views.add(reuse(previous, "sources", SourcesView::new));
        this.views.add(reuse(previous, "compose", ComposeView::new));
        this.views.add(reuse(previous, "instruments", InstrumentsView::new));
        this.views.add(reuse(previous, "style", StyleView::new));
        this.views.add(reuse(previous, "screen", ScreenView::new));
        this.views.add(reuse(previous, "alerts", AlertsView::new));
        this.views.add(reuse(previous, "live", LiveView::new));
        this.views.add(reuse(previous, "web", WebView::new));

        for (final Mk3Page page : Mk3PageRegistry.pagesForPanel(this.ctx.panel)) {
            this.views.add(reuse(previous, page.id(), ctx -> page.create(ctx)));
        }
        this.pageSignature = Mk3PageRegistry.signatureFor(this.ctx.panel);

        for (final StudioView view : this.views) {
            view.layout(this.box.viewArea(view.usesDock()));
        }
        this.activeIndex = Math.min(this.activeIndex, this.views.size() - 1);
        this.railIndicator.jump(this.activeIndex);
        active().onEnter();
    }

    private StudioView reuse(List<StudioView> previous, String id,
                             java.util.function.Function<StudioContext, StudioView> factory) {
        for (final StudioView view : previous) {
            if (id.equals(view.id())) {
                return view;
            }
        }
        return factory.apply(this.ctx);
    }

    private void refreshPagesIfChanged(float dt) {
        this.pageRecheck -= dt;
        if (this.pageRecheck > 0.0f) {
            return;
        }

        if (this.pointerDown) {
            return;
        }
        this.pageRecheck = 1.0f;
        final String signature = Mk3PageRegistry.signatureFor(this.ctx.panel);
        if (signature.equals(this.pageSignature)) {
            return;
        }
        final String wasActive = active().id();
        layout(this.box.width, this.box.height);
        for (int i = 0; i < this.views.size(); i++) {
            if (this.views.get(i).id().equals(wasActive)) {
                this.activeIndex = i;
                this.railIndicator.jump(i);
                break;
            }
        }

        active().settle();
    }

    public StudioView active() {
        return this.views.get(Math.max(0, Math.min(this.activeIndex, this.views.size() - 1)));
    }

    public void setActive(int index) {
        if (index == this.activeIndex || index < 0 || index >= this.views.size()) {
            return;
        }
        this.activeIndex = index;
        this.railIndicator.set(index);
        active().onEnter();
    }

    public boolean setActive(String id) {
        for (int i = 0; i < this.views.size(); i++) {
            if (this.views.get(i).id().equals(id)) {
                setActive(i);
                return true;
            }
        }
        return false;
    }

    public void update(float dt) {
        this.ctx.tick(dt);
        this.ctx.currentModel = this.ctx.model(Lod.FULL.historyBudget());
        this.railIndicator.step(dt);
        refreshPagesIfChanged(dt);
        active().update(dt);
    }

    public void draw(PanelSurface s, double mouseX, double mouseY) {
        s.rect(0.0f, 0.0f, this.box.width, this.box.height, DIM);

        s.shadow(this.box.root.inset(-6.0f), Tokens.R_PANEL + 6.0f, 22.0f, 0xB0000000);
        s.card(this.box.root, Tokens.R_PANEL, Tokens.SURFACE, Tokens.LINE_HI);

        drawTitlebar(s);
        drawRail(s, mouseX, mouseY);

        final boolean dock = active().usesDock();
        s.clip(dock ? this.box.work : this.box.wideWork);
        active().draw(s);
        s.unclip();

        if (dock && this.box.hasDock()) {
            drawDock(s, this.ctx.currentModel);
        }
    }

    private void drawTitlebar(PanelSurface c) {
        final Rect bar = this.box.titlebar();
        c.hairlineH(bar.x + 1.0f, bar.bottom() - 1.0f, bar.w - 2.0f, Tokens.LINE);

        final float x = bar.x + Tokens.S3;
        final float y = bar.centerY() - Text.lineHeight(Text.META) * 0.5f;
        c.text("STATS DISPLAY ", x, y, Tokens.INK, Text.META);
        c.text("MK III", x + c.textWidth("STATS DISPLAY ", Text.META), y, Tokens.ACCENT, Text.META);

        final int mbW = Math.max(1, this.ctx.panel.getMultiblockWidth());
        final int mbH = Math.max(1, this.ctx.panel.getMultiblockHeight());
        final String size = mbW + " x " + mbH;
        final float sizeW = c.textWidth(size, Text.MICRO) + 16.0f;
        final Rect sizeChip = new Rect(x + c.textWidth("STATS DISPLAY MK III", Text.META) + Tokens.S3,
                bar.centerY() - 9.0f, sizeW, 18.0f);
        c.card(sizeChip, 9.0f, Tokens.RAISED, Tokens.LINE);
        c.textCenterV(size, sizeChip.centerX(), sizeChip.centerY(), Tokens.INK_2, Text.MICRO);

        final PanelModel model = this.ctx.currentModel;
        if (model == null) {
            return;
        }
        final String status = model.status;
        final float w = c.textWidth(status, Text.MICRO) + 24.0f;
        final Rect pill = new Rect(bar.right() - Tokens.S3 - w, bar.centerY() - 9.0f, w, 18.0f);
        c.card(pill, 9.0f, Tokens.alpha(model.statusColor, 0.10f),
                Tokens.alpha(model.statusColor, 0.42f));
        Shapes.circle(c, pill.x + 10.0f, pill.centerY(), 3.0f, model.statusColor);
        c.textV(status, pill.x + 17.0f, pill.centerY(), model.statusColor, Text.MICRO);

        final String fe = feReadout();
        if (fe != null) {
            c.textRightV(fe, pill.x - Tokens.S2, bar.centerY(), Tokens.INK_3, Text.MICRO);
        }
    }

    private String feReadout() {
        try {
            final var consumer = this.ctx.panel.feConsumer();
            if (consumer == null || !consumer.requiresPower()) {
                return null;
            }
            final int draw = consumer.measuredDrainFePerTick();
            return draw <= 0 ? null : Text.group(draw) + " FE/t";
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private void drawRail(PanelSurface c, double mouseX, double mouseY) {
        final Rect railArea = this.box.rail;
        final boolean labels = this.box.railLabels;
        c.hairlineV(railArea.right(), railArea.y + 1.0f, railArea.h - 2.0f, Tokens.LINE);
        if (labels) {
            c.text(Text.track("Studio"), railArea.x + Tokens.S3, railArea.y + Tokens.S3,
                    Tokens.INK_3, Text.MICRO);
        }

        final float indicatorY = this.box.railTop()
                + this.railIndicator.value() * StudioLayout.RAIL_ITEM_H + 4.0f;
        Shapes.roundedRect(c, new Rect(railArea.x + (labels ? Tokens.S2 : 2.0f), indicatorY, 2.0f,
                StudioLayout.RAIL_ITEM_H - 8.0f), 1.0f, Tokens.ACCENT);

        final int shown = Math.min(this.views.size(), this.box.railCapacity());
        for (int i = 0; i < shown; i++) {
            final StudioView view = this.views.get(i);
            final Rect item = this.box.railItem(i);
            final boolean isActive = i == this.activeIndex;
            final boolean hovered = item.contains(mouseX, mouseY);

            if (isActive || hovered) {
                c.roundedRect(item, Tokens.R_CARD * 0.85f,
                        Tokens.alpha(Tokens.RAISED, isActive ? 1.0f : 0.6f));
            }
            final int color = isActive ? Tokens.INK : hovered ? Tokens.INK_2 : Tokens.INK_3;
            if (labels) {
                view.drawIcon(c, item.x + 14.0f, item.centerY(), color);
                c.textV(Text.truncate(c, view.label(), item.w - 34.0f, Text.META),
                        item.x + 28.0f, item.centerY(), color, Text.META);
            } else {
                view.drawIcon(c, item.centerX(), item.centerY(), color);

                if (hovered) {
                    drawRailTip(c, item, view.label());
                }
            }
        }
        if (shown < this.views.size()) {
            final Rect item = this.box.railItem(shown);
            c.textV(labels ? "+" + (this.views.size() - shown) + " more"
                            : "+" + (this.views.size() - shown),
                    labels ? item.x + 28.0f : item.x + 4.0f,
                    item.centerY(), Tokens.INK_3, Text.MICRO);
        }
    }

    private void drawRailTip(PanelSurface c, Rect item, String label) {
        final float w = c.textWidth(label, Text.META) + Tokens.S3 * 2.0f;
        final Rect tip = new Rect(item.right() + Tokens.S1, item.centerY() - 10.0f, w, 20.0f);
        c.card(tip, Tokens.R_CHIP, Tokens.OVERLAY, Tokens.LINE_HI);
        c.textCenterV(label, tip.centerX(), tip.centerY(), Tokens.INK, Text.META);
    }

    private void drawDock(PanelSurface c, PanelModel model) {
        final Rect dockArea = this.box.dock;
        c.hairlineV(dockArea.x, dockArea.y + 1.0f, dockArea.h - 2.0f, Tokens.LINE);
        final Rect inner = this.box.dockInner();
        c.text(Text.track("World panel"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);
        if (model == null) {
            return;
        }

        if (this.box.hasPreview()) {
            final int mbW = Math.max(1, this.ctx.panel.getMultiblockWidth());
            final int mbH = Math.max(1, this.ctx.panel.getMultiblockHeight());
            drawPreview(c, StudioLayout.fitAspect(this.box.bezelFrame(), mbW / (float) mbH), model);
        } else {
            c.textV("Make the window taller to see the face", inner.x,
                    inner.y + 16.0f + Text.lineHeight(Text.MICRO), Tokens.INK_3, Text.MICRO);
        }

        float y = this.box.dockFactsTop();
        c.text("Exactly what the block face shows right now.", inner.x, y, Tokens.INK_3, Text.MICRO);
        y += 16.0f;
        final int tiles = model.layout == null ? 0 : model.layout.size();
        y = fact(c, inner, y, "Tiles", tiles + " on "
                + (model.layout == null ? "?" : model.layout.cols() + " x " + model.layout.rows()));
        y = fact(c, inner, y, "Bound", boundCount(model) + " of " + tiles);
        y = fact(c, inner, y, "Signals", String.valueOf(model.channels.size()));
        fact(c, inner, y, "Refresh", "per frame");
    }

    public static void drawPreview(PanelSurface c, Rect bezel, PanelModel model) {
        if (bezel.isEmpty() || model == null) {
            return;
        }

        c.roundedRect(bezel, Tokens.R_CARD, 0xFF20242C);
        c.roundedRectOutline(bezel, Tokens.R_CARD, 1.0f, 0xFF39414E);
        final Rect face = bezel.inset(Math.min(8.0f, Math.min(bezel.w, bezel.h) * 0.08f));
        if (face.isEmpty()) {
            return;
        }
        c.clip(face);
        PanelPainter.paint(c, face, model, Lod.FULL);
        c.unclip();
    }

    private static int boundCount(PanelModel model) {
        if (model.layout == null) {
            return 0;
        }
        int n = 0;
        for (final com.createstats.statsmk3.layout.Tile t : model.layout.tiles()) {
            if (t.isBound()) {
                n++;
            }
        }
        return n;
    }

    private float fact(PanelSurface c, Rect area, float y, String label, String value) {
        c.text(label, area.x, y, Tokens.INK_3, Text.META);
        c.textRight(value, area.right(), y, Tokens.INK_2, Text.META);
        return y + 13.0f;
    }

    public void mouseMoved(double mx, double my) {
        active().mouseMoved(mx, my);
    }

    // re-hover under a still cursor, mc only reports moves. scroll left the highlight on the old row
    public void hover(double mx, double my) {
        if (!this.pointerDown) {
            active().mouseMoved(mx, my);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        this.pointerDown = true;
        final int shown = Math.min(this.views.size(), this.box.railCapacity());
        for (int i = 0; i < shown; i++) {
            if (this.box.railItem(i).contains(mx, my)) {
                setActive(i);
                return true;
            }
        }
        return active().mouseDown(mx, my, button);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        this.pointerDown = false;
        return active().mouseUp(mx, my, button);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return active().mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return active().mouseScrolled(mx, my, amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active().capturesTyping() && keyCode >= 49 && keyCode < 49 + this.views.size()) {
            setActive(keyCode - 49);
            return true;
        }
        return active().keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return active().charTyped(c, modifiers);
    }

    public void close() {
        this.ctx.flush();
    }
}
