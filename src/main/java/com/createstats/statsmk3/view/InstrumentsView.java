package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InstrumentsView extends StudioView {
    private static final float CARD_H = 96.0f;

    private static final float CARD_H_TIGHT = 72.0f;
    private static final float RULES_H = 92.0f;

    private static final float RULES_H_TIGHT = 46.0f;
    private static final float ACTIONS_H = 40.0f;

    private static final float MIN_GALLERY_H = 96.0f;

    private record Hit<T>(Rect rect, T value) {
    }

    private final Scroll scroll = new Scroll();
    private final InstrumentCtx cell = new InstrumentCtx();

    private final List<Hit<PanelPainter.AutoRule>> ruleHits = new ArrayList<>();
    private final List<Hit<String>> cardHits = new ArrayList<>();
    private final List<Hit<Integer>> actionHits = new ArrayList<>();

    private Rect rulesArea = Rect.EMPTY;
    private Rect galleryArea = Rect.EMPTY;
    private Rect actionsArea = Rect.EMPTY;

    private boolean tight;
    private float cardH = CARD_H;

    private String selectedId = "";
    private double mouseX;
    private double mouseY;

    public InstrumentsView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "instruments";
    }

    @Override
    public String label() {
        return "Instruments";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy + 1.5f, 5.5f, 1.3f, (float) (Math.PI * 0.8), (float) (Math.PI * 1.4), color);
        Shapes.line(c, cx, cy + 1.5f, cx + 3.2f, cy - 2.6f, 1.3f, color);
    }

    @Override
    protected void build() {
        final Bands bands = bandsFor(this.area);
        this.rulesArea = bands.rules();
        this.galleryArea = bands.gallery();
        this.actionsArea = bands.actions();
        this.tight = bands.tight();
        this.cardH = bands.cardHeight();
    }

    record Bands(Rect rules, Rect gallery, Rect actions, boolean tight, float cardHeight) {
    }

    static Bands bandsFor(Rect area) {
        final float headerH = 44.0f;
        final Rect actions = new Rect(area.x, area.bottom() - ACTIONS_H, area.w, ACTIONS_H);
        final float top = area.y + headerH;

        final float forBands = actions.y - top - Tokens.S4 * 2.0f;
        final boolean tight = forBands - RULES_H < MIN_GALLERY_H;
        final float rulesH = Math.max(0.0f,
                Math.min(tight ? RULES_H_TIGHT : RULES_H, actions.y - top));

        final Rect rules = new Rect(area.x, top, area.w, rulesH);
        final float galleryTop = Math.min(rules.bottom() + Tokens.S4, actions.y);
        final Rect gallery = new Rect(area.x, galleryTop, area.w,
                Math.max(0.0f, actions.y - galleryTop - Tokens.S4));
        final float cardHeight = gallery.h < CARD_H + Tokens.S2 ? CARD_H_TIGHT : CARD_H;
        return new Bands(rules, gallery, actions, tight, cardHeight);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.mouseX = mx;
        this.mouseY = my;
        return super.mouseMoved(mx, my);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        c.text("Instruments", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text(Text.truncate(c,
                        "What a tile draws with when it says AUTO. Compose overrides this tile by tile.",
                        this.area.w, Text.META),
                this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        drawRules(c);
        drawGallery(c);
        drawActions(c);
    }

    private void drawRules(PanelSurface c) {
        this.ruleHits.clear();
        c.card(this.rulesArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);

        final int autoTiles = countAutoTiles();
        c.text(Text.track("Auto chooses"), this.rulesArea.x + Tokens.S3,
                this.rulesArea.y + Tokens.S2, Tokens.INK_3, Text.MICRO);
        c.textRightV(autoTiles + (autoTiles == 1 ? " tile on auto" : " tiles on auto"),
                this.rulesArea.right() - Tokens.S3, this.rulesArea.y + Tokens.S2 + 4.0f,
                autoTiles == 0 ? Tokens.INK_3 : Tokens.INK_2, Text.MICRO);

        final Rect row = this.rulesArea.inset(Tokens.S3, 20.0f, Tokens.S3, Tokens.S2);
        final PanelPainter.AutoRule[] rules = PanelPainter.AutoRule.values();
        final Rect[] cells = Layout.columns(row, rules.length, Tokens.S2);
        final ChannelView focus = focusView();

        for (int i = 0; i < rules.length; i++) {
            drawRuleSlot(c, cells[i], rules[i], focus);
            this.ruleHits.add(new Hit<>(cells[i], rules[i]));
        }
    }

    private void drawRuleSlot(PanelSurface c, Rect r, PanelPainter.AutoRule rule, ChannelView focus) {
        final boolean hover = r.contains(this.mouseX, this.mouseY);

        final int hits = countTilesUnder(rule);
        final boolean live = hits > 0;
        c.card(r, Tokens.R_CHIP, Tokens.CANVAS,
                hover ? Tokens.ACCENT : live ? Tokens.LINE_HI : Tokens.LINE);

        c.text(rule.label(), r.x + 6.0f, r.y + 5.0f, live ? Tokens.INK_2 : Tokens.INK_3,
                Text.MICRO);
        c.text(live ? hits + (hits == 1 ? " tile" : " tiles") : rule.detail(),
                r.x + 6.0f, r.y + 15.0f, live ? Tokens.ACCENT : Tokens.INK_3, Text.MICRO);

        final String id = ruleValue(rule);
        final int nameColor = hover ? Tokens.INK : live ? Tokens.INK_2 : Tokens.INK_3;
        final String name = InstrumentRegistry.get(id).displayName();

        // short page drops the art, not the controls

        if (this.tight) {
            c.textRightV(Text.truncate(c, name, r.w * 0.55f, Text.MICRO),
                    r.right() - 6.0f, r.y + 20.0f, nameColor, Text.MICRO);
            return;
        }

        final Rect art = new Rect(r.x + 6.0f, r.y + 27.0f, r.w - 12.0f, r.h - 44.0f);
        if (!art.isEmpty() && focus != null) {
            prepare(focus);
            c.clip(art);
            InstrumentRegistry.get(id).draw(c, art, this.cell);
            c.unclip();
        }
        c.textV(Text.truncate(c, name, r.w - 12.0f, Text.MICRO), r.x + 6.0f, r.bottom() - 9.0f,
                nameColor, Text.MICRO);
    }

    private int countTilesUnder(PanelPainter.AutoRule rule) {
        final com.createstats.statsmk3.layout.Mk3Layout layout = this.ctx.settings().layout;
        if (layout.isEmpty() || this.ctx.currentModel == null) {
            return 0;
        }

        final Rect face = new Rect(0.0f, 0.0f, layout.cols() * 16.0f, layout.rows() * 16.0f);
        final Rect body = PanelPainter.bodyOf(face, this.ctx.currentModel, Lod.FULL);
        final float gap = PanelPainter.gapOf(face, this.ctx.currentModel);
        if (body.isEmpty()) {
            return 0;
        }
        final float inset = this.ctx.currentModel.style.pad(6.0f);
        int n = 0;
        for (final Tile t : layout.tiles()) {
            if (t.instrumentId != null && !t.instrumentId.isEmpty()) {
                continue;
            }
            final Rect cell = com.createstats.statsmk3.paint.LayoutResolver
                    .rectOf(t, layout, body, gap).inset(inset);
            if (!cell.isEmpty() && PanelPainter.autoRuleFor(cell.w, cell.h) == rule) {
                n++;
            }
        }
        return n;
    }

    private int galleryColumns() {
        return this.galleryArea.w > 480.0f ? 3 : 2;
    }

    private void drawGallery(PanelSurface c) {
        this.cardHits.clear();
        if (this.galleryArea.h < 30.0f) {
            return;
        }
        c.text(Text.track("Catalog"), this.galleryArea.x, this.galleryArea.y - 14.0f,
                Tokens.INK_3, Text.MICRO);

        final List<Instrument> all = InstrumentRegistry.all();
        final int cols = galleryColumns();
        final float gap = Tokens.S2;
        this.scroll.setMetrics(Layout.gridHeight(all.size(), cols, this.cardH, gap),
                this.galleryArea.h);

        final ChannelView focus = focusView();
        c.clip(this.galleryArea);
        final Rect grid = new Rect(this.galleryArea.x, this.galleryArea.y - this.scroll.value(),
                this.galleryArea.w, this.galleryArea.h);
        for (int i = 0; i < all.size(); i++) {
            final Rect r = Layout.gridCell(grid, cols, this.cardH, gap, i);
            this.cardHits.add(new Hit<>(r, all.get(i).id()));
            if (r.bottom() < this.galleryArea.y || r.y > this.galleryArea.bottom()) {
                continue;
            }
            drawCard(c, r, all.get(i), focus);
        }
        c.unclip();

        this.scroll.drawBar(c, barTrack());
    }

    private void drawCard(PanelSurface c, Rect r, Instrument instrument, ChannelView focus) {
        final boolean selected = instrument.id().equals(this.selectedId);
        final boolean hover = r.contains(this.mouseX, this.mouseY)
                && this.galleryArea.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CARD, selected ? Tokens.RAISED : Tokens.SURFACE,
                selected ? Tokens.ACCENT : hover ? Tokens.LINE_HI : Tokens.LINE);

        final Rect art = r.inset(Tokens.S2).top(r.h - 26.0f);
        Shapes.roundedRect(c, art, 4.0f, Tokens.CANVAS);

        if (focus != null) {
            prepare(focus);
            final Rect inner = art.inset(6.0f);
            if (!inner.isEmpty()) {
                c.clip(inner);
                instrument.draw(c, inner, this.cell);
                c.unclip();
            }
        }

        c.text(instrument.displayName(), r.x + Tokens.S2, r.bottom() - 22.0f,
                selected ? Tokens.INK : Tokens.INK_2, Text.META);

        final int used = countTilesUsing(instrument.id());
        if (used > 0) {
            final String badge = used + (used == 1 ? " tile" : " tiles");
            final float w = c.textWidth(badge, Text.MICRO) + 12.0f;
            final Rect pill = new Rect(r.right() - Tokens.S2 - w, r.bottom() - 24.0f, w, 14.0f);
            c.card(pill, 7.0f, Tokens.alpha(Tokens.ACCENT, 0.12f),
                    Tokens.alpha(Tokens.ACCENT, 0.4f));
            c.textCenterV(badge, pill.centerX(), pill.centerY(), Tokens.ACCENT, Text.MICRO);
        }
        c.text(instrument.description(), r.x + Tokens.S2, r.bottom() - 11.0f,
                Tokens.INK_3, Text.MICRO);
    }

    private void drawActions(PanelSurface c) {
        this.actionHits.clear();
        final boolean has = !this.selectedId.isEmpty();
        final int tiles = tiles().size();

        c.hairlineH(this.actionsArea.x, this.actionsArea.y, this.actionsArea.w, Tokens.LINE);
        final float y = this.actionsArea.y + Tokens.S2;

        c.textV(has
                        ? "Selected: " + InstrumentRegistry.get(this.selectedId).displayName()
                        : "Pick one from the catalog to apply it to the whole face",
                this.actionsArea.x, y + 8.0f, has ? Tokens.INK : Tokens.INK_3, Text.META);

        float right = this.actionsArea.right();
        right = drawAction(c, right, y, "Clear all overrides", countOverrides() > 0, 1);
        drawAction(c, right - Tokens.S2, y,
                "Apply to all " + tiles + (tiles == 1 ? " tile" : " tiles"), has && tiles > 0, 0);
    }

    private float drawAction(PanelSurface c, float rightX, float y, String label, boolean enabled,
                             int action) {
        final float w = c.textWidth(label, Text.META) + 20.0f;
        final Rect r = new Rect(rightX - w, y, w, 22.0f);
        final boolean hover = enabled && r.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CARD * 0.85f, hover ? Tokens.OVERLAY : Tokens.RAISED,
                hover ? Tokens.LINE_HI : Tokens.LINE);
        c.textCenterV(label, r.centerX(), r.centerY(),
                enabled ? (hover ? Tokens.INK : Tokens.INK_2) : Tokens.INK_3, Text.META);
        if (enabled) {
            this.actionHits.add(new Hit<>(r, action));
        }
        return r.x;
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        this.mouseX = mx;
        this.mouseY = my;

        for (final Hit<PanelPainter.AutoRule> hit : this.ruleHits) {
            if (hit.rect().contains(mx, my)) {
                cycleRule(hit.value(), button == 1 ? -1 : 1);
                return true;
            }
        }
        if (button == 0 && this.scroll.pressBar(barTrack(), mx, my)) {
            return true;
        }
        if (button == 0 && this.galleryArea.contains(mx, my)) {
            for (final Hit<String> hit : this.cardHits) {
                if (hit.rect().contains(mx, my)) {
                    this.selectedId = hit.value().equals(this.selectedId) ? "" : hit.value();
                    return true;
                }
            }
            return false;
        }
        if (button == 0) {
            for (final Hit<Integer> hit : this.actionHits) {
                if (hit.rect().contains(mx, my)) {
                    if (hit.value() == 0) {
                        applyToAll(this.selectedId);
                    } else {
                        applyToAll("");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return this.galleryArea.contains(mx, my) && this.scroll.wheel(amount);
    }

    private Rect barTrack() {
        return new Rect(this.galleryArea.right() - 3.0f, this.galleryArea.y, 3.0f,
                this.galleryArea.h);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.mouseX = mx;
        this.mouseY = my;
        if (this.scroll.dragBar(barTrack(), my)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.scroll.endDrag();
        return super.mouseUp(mx, my, button);
    }

    private void cycleRule(PanelPainter.AutoRule rule, int step) {
        final List<Instrument> all = InstrumentRegistry.all();
        final int next = Math.floorMod(InstrumentRegistry.indexOf(ruleValue(rule)) + step,
                all.size());
        final String id = all.get(next).id();
        switch (rule) {
            case TINY -> this.ctx.settings().autoTiny = id;
            case WIDE -> this.ctx.settings().autoWide = id;
            case SQUARE -> this.ctx.settings().autoSquare = id;
            case DEFAULT -> this.ctx.settings().instrument = id;
        }
        this.ctx.commit();
    }

    private void applyToAll(String instrumentId) {
        for (final Tile tile : tiles()) {
            tile.instrumentId = instrumentId;
        }
        this.ctx.commit();
    }

    private String ruleValue(PanelPainter.AutoRule rule) {
        final com.createstats.statsmk3.Mk3Settings s = this.ctx.settings();
        return switch (rule) {
            case TINY -> s.autoTiny;
            case WIDE -> s.autoWide;
            case SQUARE -> s.autoSquare;
            case DEFAULT -> s.instrument;
        };
    }

    private List<Tile> tiles() {
        return this.ctx.settings().layout.tiles();
    }

    private int countAutoTiles() {
        int n = 0;
        for (final Tile t : tiles()) {
            if (t.instrumentId == null || t.instrumentId.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private int countOverrides() {
        return tiles().size() - countAutoTiles();
    }

    private int countTilesUsing(String instrumentId) {
        int n = 0;
        for (final Tile t : tiles()) {
            if (instrumentId.equals(t.instrumentId)) {
                n++;
            }
        }
        return n;
    }

    private static final Lod PREVIEW = Lod.preview(48);

    private void prepare(ChannelView focus) {
        this.cell.primary = focus;
        this.cell.all = previewSeries(focus);
        if (this.ctx.currentModel != null) {
            this.cell.style = this.ctx.currentModel.style;
        }
        this.cell.lod = PREVIEW;
        this.cell.time = this.ctx.time;
    }

    private static final int PREVIEW_SERIES = 4;

    private List<ChannelView> previewSeries(ChannelView focus) {
        if (this.ctx.currentModel == null || this.ctx.currentModel.channels.isEmpty()) {
            return List.of(focus);
        }
        final List<ChannelView> all = this.ctx.currentModel.channels;
        return all.size() <= PREVIEW_SERIES ? all : all.subList(0, PREVIEW_SERIES);
    }

    private ChannelView focusView() {
        if (this.ctx.currentModel == null || this.ctx.currentModel.channels.isEmpty()) {
            return null;
        }
        for (final ChannelView v : this.ctx.currentModel.channels) {
            if (v.id.equals(this.ctx.focusedChannelId)) {
                return v;
            }
        }
        return this.ctx.currentModel.channels.get(0);
    }
}
