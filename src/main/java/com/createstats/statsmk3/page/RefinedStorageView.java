package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

import com.createstats.data.WindowedTrend;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.RsReadings.Board;
import com.createstats.statsmk3.page.RsReadings.Stock;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// two columns, one thing legible from across the room
@OnlyIn(Dist.CLIENT)
public final class RefinedStorageView extends StudioView {
    private final Scroll scroll = new Scroll();
    private final Spring heroCount = Spring.ui(0.0f);

    private Segmented windowControl;
    private Segmented networkControl;
    private Button faceButton;

    private List<Board> networks = List.of();
    private List<Stock> stock = List.of();
    private int networkIndex;
    private String hoveredItemId = "";
    private final List<Rect> rowRects = new ArrayList<>();
    private final List<String> rowIds = new ArrayList<>();

    public RefinedStorageView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "refined_storage";
    }

    @Override
    public String label() {
        return "Storage Net";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        RsArt.diskGlyph(c, cx - 5.0f, cy - 5.5f, 11.0f, color);
    }

    @Override
    protected void build() {
        final WindowedTrend.Window[] windows = WindowedTrend.Window.values();
        final String[] labels = new String[windows.length];
        for (int i = 0; i < windows.length; i++) {
            labels[i] = windows[i].label();
        }
        this.windowControl = new Segmented(labels,
                () -> this.ctx.settings().rsWindow,
                i -> {
                    this.ctx.settings().rsWindow = i;
                    this.ctx.commit();
                });
        this.windowControl.textScale(Text.META);
        final float windowW = 132.0f;
        this.windowControl.setBounds(new Rect(this.area.right() - windowW, this.area.y + 4.0f,
                windowW, 24.0f));
        this.widgets.add(this.windowControl);

        this.faceButton = new Button(faceLabel(), this::toggleFace).textScale(Text.MICRO);
        this.faceButton.setBounds(new Rect(this.area.right() - windowW - 8.0f - 104.0f,
                this.area.y + 4.0f, 104.0f, 24.0f));
        this.widgets.add(this.faceButton);

        if (this.networks.size() > 1) {
            final String[] names = new String[this.networks.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = this.networks.get(i).label();
            }
            this.networkControl = new Segmented(names, () -> this.networkIndex,
                    i -> this.networkIndex = i);
            this.networkControl.textScale(Text.MICRO);
            final float w = Math.min(300.0f, RsArt.RAIL_W);
            this.networkControl.setBounds(new Rect(this.area.right() - w,
                    this.area.y + RsArt.HEADER_H + 4.0f, w, 22.0f));
            this.widgets.add(this.networkControl);
        } else {
            this.networkControl = null;
        }
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        final int before = this.networks.size();
        this.networks = RsReadings.boardsOf(this.ctx.panel);
        if (this.networks.size() != before) {
            layout(this.area);
            settle();
        }
        if (this.networkIndex >= this.networks.size()) {
            this.networkIndex = 0;
        }
        final Board net = current();
        this.stock = net == null ? List.of() : net.stock();

        final Stock hero = hero();
        this.heroCount.set(hero == null ? 0.0f : hero.amount()).step(dt);
        this.scroll.setMetrics(this.stock.size() * RsArt.ROW_H + 6.0f, listArea().h);
        this.scroll.update(dt);
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
        }
    }

    private Board current() {
        return this.networks.isEmpty() ? null
                : this.networks.get(Math.min(this.networkIndex, this.networks.size() - 1));
    }

    private Stock hero() {
        return RsReadings.hero(this.stock, this.ctx.settings().rsFocusItem);
    }

    private WindowedTrend.Window window() {
        return WindowedTrend.Window.byOrdinal(this.ctx.settings().rsWindow);
    }

    private boolean hasSelector() {
        return this.networks.size() > 1;
    }

    private Rect listArea() {
        return RsArt.listBox(this.area, hasSelector());
    }

    private Rect scrollTrack() {
        final Rect list = listArea();
        return new Rect(list.right() - 5.0f, list.y, 5.0f, list.h);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        this.rowRects.clear();
        this.rowIds.clear();
        RsArt.page(c, this.area, current(), this.networks.size(), window(),
                this.ctx.settings().rsFocusItem, this.heroCount.value(), this.ctx.time,
                this.scroll.value(), this.hoveredItemId, (rect, itemId) -> {
                    this.rowRects.add(rect);
                    this.rowIds.add(itemId);
                });
        if (this.scroll.scrollable()) {
            this.scroll.drawBar(c, scrollTrack());
        }
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (this.scroll.scrollable() && this.scroll.pressBar(scrollTrack(), mx, my)) {
            return true;
        }
        for (int i = 0; i < this.rowRects.size(); i++) {
            if (this.rowRects.get(i).contains(mx, my) && listArea().contains(mx, my)) {
                focusOn(this.rowIds.get(i));
                return true;
            }
        }
        return false;
    }

    private void focusOn(String itemId) {
        final Mk3Settings settings = this.ctx.settings();
        settings.rsFocusItem = itemId.equals(settings.rsFocusItem) ? "" : itemId;
        this.ctx.commit();
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        final boolean widget = super.mouseMoved(mx, my);
        this.hoveredItemId = "";
        if (listArea().contains(mx, my)) {
            for (int i = 0; i < this.rowRects.size(); i++) {
                if (this.rowRects.get(i).contains(mx, my)) {
                    this.hoveredItemId = this.rowIds.get(i);
                    break;
                }
            }
        }
        return widget || !this.hoveredItemId.isEmpty();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.scroll.dragging()) {
            return this.scroll.dragBar(scrollTrack(), my);
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.scroll.endDrag();
        return super.mouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return listArea().contains(mx, my) && this.scroll.wheel(amount);
    }

    private String faceLabel() {
        return showingOnFace() ? "On face ✓" : "Show on face";
    }

    private boolean showingOnFace() {
        return this.ctx.settings().faceMode == Mk3Settings.FaceMode.REFINED_STORAGE;
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        settings.faceMode = showingOnFace()
                ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.REFINED_STORAGE;
        this.ctx.commit();
    }
}
