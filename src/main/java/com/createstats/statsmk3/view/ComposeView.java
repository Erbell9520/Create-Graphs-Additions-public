package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.YAxisRange;
import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.layout.LayoutPresets;
import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.LayoutResolver;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// the canvas IS the panel, same painter as the block face
@OnlyIn(Dist.CLIENT)
public final class ComposeView extends StudioView {
    private static final float PRESET_H = 46.0f;
    private static final float INSPECTOR_H = 86.0f;
    private static final float TRAY_H = 74.0f;
    private static final float CHIP_H = 20.0f;
    private static final float HANDLE = 7.0f;
    private static final float ROW_GAP = 4.0f;

    private static final int DROP_W = 2;
    private static final int DROP_H = 2;

    private static final String[] TRAY_EMPTY = {
            "No sources linked.",
            "Point a Create Display",
            "Link at this panel.",
    };

    private enum Drag { NONE, MOVE, RESIZE, SIGNAL }

    private record Hit<T>(Rect rect, T value) {
    }

    private final Scroll trayScroll = new Scroll();
    private final Spring gridFade = Spring.ui(0.0f);
    private final InstrumentCtx thumbCtx = new InstrumentCtx();
    private Segmented yModeControl;
    private TextField yMinField;
    private TextField yMaxField;

    private final List<Hit<String>> presetHits = new ArrayList<>();
    private final List<Hit<String>> instrumentHits = new ArrayList<>();
    private final List<Hit<String>> chipHits = new ArrayList<>();
    private final List<Hit<Integer>> buttonHits = new ArrayList<>();

    private Rect presetArea = Rect.EMPTY;
    private Rect canvasArea = Rect.EMPTY;
    private Rect inspectorArea = Rect.EMPTY;
    private Rect trayArea = Rect.EMPTY;

    private Rect face = Rect.EMPTY;
    private Rect body = Rect.EMPTY;
    private float gap = 4.0f;

    private int selectedIndex = -1;
    private Drag drag = Drag.NONE;
    private int grabDx;
    private int grabDy;
    private int ghostX;
    private int ghostY;
    private int ghostW = 1;
    private int ghostH = 1;
    private boolean ghostValid = true;
    private String dragChannelId = "";
    private double mouseX;
    private double mouseY;

    public ComposeView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "compose";
    }

    @Override
    public String label() {
        return "Compose";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public boolean capturesTyping() {
        return this.yMinField != null && this.yMinField.editing()
                || this.yMaxField != null && this.yMaxField.editing();
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.roundedRectOutline(c, new Rect(cx - 6.5f, cy - 6.0f, 13.0f, 12.0f), 2.0f, 1.1f, color);
        Shapes.rect(c, cx - 6.5f, cy - 0.6f, 8.0f, 1.1f, color);
        Shapes.rect(c, cx - 1.6f, cy - 6.0f, 1.1f, 6.6f, color);
        Shapes.rect(c, cx + 1.5f, cy - 0.6f, 1.1f, 6.6f, color);
    }

    @Override
    protected void build() {
        final Segmented range = new Segmented(Mk3Settings.TIME_RANGE_LABELS,
                () -> Mk3Settings.timeRangeIndex(this.ctx.settings().timeRangeSeconds),
                i -> {
                    this.ctx.settings().timeRangeSeconds = Mk3Settings.TIME_RANGES[i];
                    this.ctx.commit();
                });
        range.setBounds(new Rect(this.area.right() - 150.0f, this.area.y - 2.0f, 150.0f, 20.0f));
        this.widgets.add(range);

        final float top = this.area.y + 40.0f;
        this.presetArea = new Rect(this.area.x, top, this.area.w, PRESET_H);
        this.inspectorArea = new Rect(this.area.x, this.area.bottom() - INSPECTOR_H,
                this.area.w, INSPECTOR_H);

        final float trayW = Math.max(140.0f, Math.min(this.area.w * 0.28f, 220.0f));
        final float midTop = this.presetArea.bottom() + Tokens.S3;
        final float midH = this.inspectorArea.y - midTop - Tokens.S3;
        this.trayArea = new Rect(this.area.right() - trayW, midTop, trayW, midH);
        this.canvasArea = new Rect(this.area.x, midTop,
                this.area.w - trayW - Tokens.S3, midH);

        this.yModeControl = new Segmented(new String[]{"Auto", "Fixed"},
                () -> selectedRange().isFixed() ? 1 : 0, this::setYAxisMode).textScale(Text.MICRO);
        this.yModeControl.setBounds(yModeBounds());
        this.widgets.add(this.yModeControl);
        this.yMinField = new TextField(this::commitYMin).placeholder("Min").textScale(Text.MICRO);
        this.yMinField.setBounds(yMinBounds());
        this.widgets.add(this.yMinField);
        this.yMaxField = new TextField(this::commitYMax).placeholder("Max").textScale(Text.MICRO);
        this.yMaxField.setBounds(yMaxBounds());
        this.widgets.add(this.yMaxField);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.trayScroll.update(dt);
        this.gridFade.set(this.drag == Drag.NONE ? 0.0f : 1.0f).step(dt);

        if (this.selectedIndex >= layout().size()) {
            this.selectedIndex = -1;
            this.drag = Drag.NONE;
        }
        syncYAxisControls();
    }

    private Rect yModeBounds() {
        final Rect inner = this.inspectorArea.inset(Tokens.S2, 6.0f, Tokens.S2, 6.0f);
        return new Rect(inner.x + 42.0f, inner.bottom() - 19.0f, 84.0f, 19.0f);
    }

    private Rect yMinBounds() {
        final Rect mode = yModeBounds();
        return new Rect(mode.right() + 5.0f, mode.y, 68.0f, mode.h);
    }

    private Rect yMaxBounds() {
        final Rect min = yMinBounds();
        return new Rect(min.right() + 5.0f, min.y, 68.0f, min.h);
    }

    private YAxisRange selectedRange() {
        final Tile selected = selected();
        return selected == null ? YAxisRange.auto() : selected.yAxis();
    }

    private void syncYAxisControls() {
        if (this.yModeControl == null) {
            return;
        }
        final boolean selected = selected() != null;
        final YAxisRange range = selectedRange();
        this.yModeControl.setVisible(selected);
        this.yMinField.setVisible(selected && range.isFixed());
        this.yMaxField.setVisible(selected && range.isFixed());
        if (range.isFixed()) {
            this.yMinField.setText(Float.toString(range.min()));
            this.yMaxField.setText(Float.toString(range.max()));
        }
    }

    private void setYAxisMode(int mode) {
        final Tile selected = selected();
        if (selected == null) {
            return;
        }
        YAxisRange next = YAxisRange.auto();
        if (mode != 0) {
            final PanelModel.Slot slot = slotFor(selected);
            final ChannelView channel = slot == null ? null : slot.channel;
            if (channel == null || channel.historyCount == 0) {
                next = YAxisRange.fixed(0.0f, 1.0f);
            } else {
                final float[] extent = channel.historyExtent(channel.historyCount, 0.0f);
                next = YAxisRange.rounded(extent[0], extent[1]);
            }
        }
        selected.setYAxis(next);
        this.ctx.commit();
        syncYAxisControls();
    }

    private void commitYMin(String text) {
        commitYAxis(text, true);
    }

    private void commitYMax(String text) {
        commitYAxis(text, false);
    }

    private void commitYAxis(String text, boolean minimum) {
        final Tile selected = selected();
        final YAxisRange current = selectedRange();
        if (selected == null || !current.isFixed()) {
            syncYAxisControls();
            return;
        }
        try {
            final float value = Float.parseFloat(text.replace(" ", "").trim());
            final YAxisRange next = minimum
                    ? YAxisRange.fixed(value, current.max())
                    : YAxisRange.fixed(current.min(), value);
            if (!next.isFixed()) {
                syncYAxisControls();
                return;
            }
            selected.setYAxis(next);
            this.ctx.commit();
        } catch (final NumberFormatException e) {
        }
        syncYAxisControls();
    }

    @Override
    protected void drawContent(PanelSurface c) {
        c.text("Compose", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text("Drag a signal onto a tile, or a tile to move it. Arrows nudge, shift resizes, "
                        + "D copies, Delete removes.",
                this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        drawPresets(c);
        drawCanvas(c);
        drawInspector(c);
        drawTray(c);
        drawDragGhost(c);
    }

    private void drawPresets(PanelSurface c) {
        this.presetHits.clear();
        final Mk3Layout layout = layout();
        final List<LayoutPresets.Preset> presets =
                LayoutPresets.fitting(layout.cols(), layout.rows());
        if (presets.isEmpty()) {
            return;
        }
        c.text(Text.track("Start from"), this.presetArea.x, this.presetArea.y, Tokens.INK_3,
                Text.MICRO);

        final Rect row = this.presetArea.inset(0.0f, 13.0f, 0.0f, 0.0f);
        final float cellW = Math.min(58.0f,
                (row.w - Tokens.S1 * (presets.size() - 1)) / Math.max(1, presets.size()));
        for (int i = 0; i < presets.size(); i++) {
            final Rect cell = new Rect(row.x + i * (cellW + Tokens.S1), row.y, cellW, row.h);
            if (cell.right() > row.right() + 0.5f) {
                break;
            }
            final LayoutPresets.Preset preset = presets.get(i);
            drawPresetChip(c, cell, preset, preset.id().equals(layout.presetId));
            this.presetHits.add(new Hit<>(cell, preset.id()));
        }
    }

    private void drawPresetChip(PanelSurface c, Rect r, LayoutPresets.Preset preset, boolean selected) {
        final boolean hover = r.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CHIP, selected ? Tokens.RAISED : Tokens.SURFACE,
                selected ? Tokens.ACCENT : hover ? Tokens.LINE_HI : Tokens.LINE);

        final Mk3Layout preview = LayoutPresets.build(preset.id(), layout().cols(), layout().rows(),
                channelCount());
        final Rect art = r.inset(4.0f, 3.0f, 4.0f, 12.0f);
        for (final Tile t : preview.tiles()) {
            final Rect cell = LayoutResolver.rectOf(t, preview, art, 1.0f);
            if (!cell.isEmpty()) {
                Shapes.roundedRect(c, cell, 1.0f, selected
                        ? Tokens.alpha(Tokens.ACCENT, 0.55f) : Tokens.LINE_HI);
            }
        }
        c.textCenterV(Text.truncate(c, preset.name(), r.w - 4.0f, Text.MICRO), r.centerX(),
                r.bottom() - 6.0f, selected ? Tokens.INK : Tokens.INK_3, Text.MICRO);
    }

    private void drawCanvas(PanelSurface c) {
        final PanelModel model = this.ctx.currentModel;
        if (model == null || this.canvasArea.h < 40.0f) {
            this.body = Rect.EMPTY;
            return;
        }
        final Mk3Layout layout = layout();
        this.face = fitFace(this.canvasArea, layout.cols(), layout.rows());
        if (this.face.isEmpty()) {
            this.body = Rect.EMPTY;
            return;
        }
        this.body = PanelPainter.bodyOf(this.face, model, Lod.FULL);
        this.gap = PanelPainter.gapOf(this.face, model);

        final Rect bezel = this.face.inset(-6.0f);
        c.roundedRect(bezel, Tokens.R_CARD, 0xFF20242C);
        c.roundedRectOutline(bezel, Tokens.R_CARD, 1.0f, 0xFF39414E);

        c.clip(this.face);
        PanelPainter.paint(c, this.face, model, Lod.FULL);
        c.unclip();

        if (this.body.isEmpty()) {
            return;
        }
        drawGridHint(c);
        drawTileChrome(c, model);
    }

    private static Rect fitFace(Rect box, int cols, int rows) {
        final Rect avail = box.inset(8.0f);
        if (avail.isEmpty()) {
            return Rect.EMPTY;
        }
        final float aspect = cols / (float) Math.max(1, rows);
        float w = avail.w;
        float h = w / aspect;
        if (h > avail.h) {
            h = avail.h;
            w = h * aspect;
        }
        return new Rect(avail.centerX() - w * 0.5f, avail.centerY() - h * 0.5f, w, h);
    }

    // grid only while dragging
    private void drawGridHint(PanelSurface c) {
        final float strength = this.gridFade.value();
        if (strength <= 0.02f) {
            return;
        }
        final Mk3Layout layout = layout();
        final int cols = layout.cols();
        final int rows = layout.rows();
        final float cw = cellWidth();
        final float ch = cellHeight();
        final int line = Tokens.alpha(Tokens.INK_3, 0.32f * strength);
        for (int i = 1; i < cols; i++) {
            c.hairlineV(this.body.x + i * (cw + this.gap) - this.gap * 0.5f, this.body.y,
                    this.body.h, line);
        }
        for (int i = 1; i < rows; i++) {
            c.hairlineH(this.body.x, this.body.y + i * (ch + this.gap) - this.gap * 0.5f,
                    this.body.w, line);
        }
    }

    private void drawTileChrome(PanelSurface c, PanelModel model) {
        final Mk3Layout layout = layout();
        if (layout.isEmpty()) {
            c.textCenterV("Click anywhere on the screen to add a tile",
                    this.face.centerX(), this.face.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        final Tile hovered = this.drag == Drag.NONE ? tileUnder(this.mouseX, this.mouseY) : null;

        for (int i = 0; i < layout.size(); i++) {
            final Tile tile = layout.get(i);
            final Rect r = LayoutResolver.rectOf(tile, layout, this.body, this.gap);
            if (r.isEmpty()) {
                continue;
            }
            final boolean isSelected = i == this.selectedIndex;
            if (isSelected) {
                Shapes.roundedRectOutline(c, r.inset(-1.0f), 4.0f, 1.4f, Tokens.ACCENT);
            } else if (tile == hovered) {
                Shapes.roundedRectOutline(c, r.inset(-1.0f), 4.0f, 1.0f,
                        Tokens.alpha(Tokens.INK_2, 0.7f));
            }

            if (tile.isBound() && r.w > 16.0f && r.h > 16.0f) {
                final ChannelView ch = model.channelById(tile.channelId);
                Shapes.circle(c, r.right() - 5.0f, r.y + 5.0f, 2.0f,
                        ch == null ? Tokens.CAUTION : ch.color());
            }
            if (isSelected) {
                final Rect handle = handleRect(r);
                Shapes.roundedRect(c, handle, 2.0f, Tokens.ACCENT);
                Shapes.roundedRect(c, handle.inset(2.0f), 1.0f, Tokens.CANVAS);
            }
        }
    }

    private static Rect handleRect(Rect tile) {
        return new Rect(tile.right() - HANDLE, tile.bottom() - HANDLE, HANDLE, HANDLE);
    }

    private void drawDragGhost(PanelSurface c) {
        if (this.drag == Drag.NONE || this.body.isEmpty()) {
            return;
        }
        if (this.drag == Drag.SIGNAL) {
            drawSignalDrag(c);
            return;
        }

        final Tile sel = selected();
        if (sel != null && this.ghostX == sel.gx && this.ghostY == sel.gy
                && this.ghostW == sel.gw && this.ghostH == sel.gh) {
            return;
        }
        final Rect ghost = LayoutResolver.rectOf(
                new Tile(this.ghostX, this.ghostY, this.ghostW, this.ghostH),
                layout(), this.body, this.gap);
        if (ghost.isEmpty()) {
            return;
        }
        final int color = this.ghostValid ? Tokens.ACCENT : Tokens.CRITICAL;
        Shapes.roundedRect(c, ghost, 4.0f, Tokens.alpha(color, 0.16f));
        Shapes.roundedRectOutline(c, ghost, 4.0f, 1.4f, color);
        c.textCenterV(this.ghostValid ? this.ghostW + " x " + this.ghostH : "blocked",
                ghost.centerX(), ghost.centerY(),
                this.ghostValid ? Tokens.alpha(Tokens.INK, 0.85f) : Tokens.CRITICAL, Text.MICRO);
    }

    private void drawSignalDrag(PanelSurface c) {
        final Tile target = tileUnder(this.mouseX, this.mouseY);
        if (target != null) {
            final Rect r = LayoutResolver.rectOf(target, layout(), this.body, this.gap);
            Shapes.roundedRect(c, r, 4.0f, Tokens.alpha(Tokens.ACCENT, 0.20f));
            Shapes.roundedRectOutline(c, r, 4.0f, 1.6f, Tokens.ACCENT);
        } else {
            final Tile free = freeTileUnder(this.mouseX, this.mouseY);
            if (free != null) {
                final Rect r = LayoutResolver.rectOf(free, layout(), this.body, this.gap);
                Shapes.roundedRect(c, r, 4.0f, Tokens.alpha(Tokens.GOOD, 0.16f));
                Shapes.roundedRectOutline(c, r, 4.0f, 1.4f, Tokens.GOOD);
                c.textCenterV("new tile", r.centerX(), r.centerY(), Tokens.GOOD, Text.MICRO);
            }
        }

        final ChannelView ch = channelView(this.dragChannelId);
        final String label = ch == null ? this.dragChannelId : ch.label;
        final float w = c.textWidth(label, Text.MICRO) + 22.0f;
        final Rect chip = new Rect((float) this.mouseX + 10.0f, (float) this.mouseY - 8.0f, w, CHIP_H);
        c.shadow(chip, Tokens.R_CHIP, 8.0f, 0x80000000);
        c.card(chip, Tokens.R_CHIP, Tokens.OVERLAY, Tokens.ACCENT);
        Shapes.roundedRect(c, new Rect(chip.x + 6.0f, chip.centerY() - 3.0f, 6.0f, 6.0f), 1.5f,
                ch == null ? Tokens.INK_3 : ch.color());
        c.textV(label, chip.x + 16.0f, chip.centerY(), Tokens.INK, Text.MICRO);
    }

    private void drawInspector(PanelSurface c) {
        this.buttonHits.clear();
        this.instrumentHits.clear();
        c.card(this.inspectorArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = this.inspectorArea.inset(Tokens.S2, 6.0f, Tokens.S2, 6.0f);

        final Tile sel = selected();
        if (sel == null) {
            c.textV("Select a tile to choose its signal and its instrument",
                    inner.x, inner.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        final PanelModel model = this.ctx.currentModel;
        final PanelModel.Slot slot = slotFor(sel);
        final ChannelView ch = slot == null ? null : slot.channel;

        final String source = sel.isBound()
                ? this.ctx.sourceNameOf(sel.channelId)
                : "Auto";
        final String signal = ch != null ? ch.label
                : sel.isBound() ? "signal lost" : "next in order";

        Shapes.roundedRect(c, new Rect(inner.x, inner.y + 2.0f, 7.0f, 7.0f), 2.0f,
                ch == null ? Tokens.CAUTION : ch.color());
        c.text(Text.truncate(c, signal, inner.w * 0.36f, Text.META), inner.x + 12.0f, inner.y,
                Tokens.INK, Text.META);

        c.text(Text.truncate(c, source + "  /  " + sel.gw + " x " + sel.gh
                                + "  /  #" + (this.selectedIndex + 1) + " of " + layout().size(),
                        inner.w * 0.36f, Text.MICRO),
                inner.x + 12.0f, inner.y + 12.0f, Tokens.INK_3, Text.MICRO);

        float bx = inner.right();
        bx = drawMiniButton(c, bx, inner.y, "Remove", Tokens.CRITICAL, 0);
        bx = drawMiniButton(c, bx - ROW_GAP, inner.y, "Copy", Tokens.INK_2, 4);
        bx = drawMiniButton(c, bx - ROW_GAP, inner.y, "Unpin",
                sel.isBound() ? Tokens.INK_2 : Tokens.INK_3, 1);
        bx = drawMiniButton(c, bx - ROW_GAP, inner.y,
                sel.value ? "Value on" : "Value off",
                sel.value ? Tokens.ACCENT : Tokens.INK_3, 2);
        bx = drawMiniButton(c, bx - ROW_GAP, inner.y, sel.label ? "Label on" : "Label off",
                sel.label ? Tokens.ACCENT : Tokens.INK_3, 3);

        bx = drawMiniButton(c, bx - ROW_GAP, inner.y, ">",
                this.selectedIndex < layout().size() - 1 ? Tokens.INK_2 : Tokens.INK_3, 6);
        drawMiniButton(c, bx - 2.0f, inner.y, "<",
                this.selectedIndex > 0 ? Tokens.INK_2 : Tokens.INK_3, 5);

        drawInstrumentStrip(c, new Rect(inner.x, inner.y + 26.0f, inner.w, 23.0f),
                model, ch, sel);
        c.textV("Plot Y", inner.x, inner.bottom() - 9.5f, Tokens.INK_3, Text.MICRO);
    }

    private void drawInstrumentStrip(PanelSurface c, Rect strip, PanelModel model, ChannelView ch,
                                     Tile sel) {
        final List<Instrument> all = InstrumentRegistry.all();
        final int count = all.size() + 1;
        final float cw = Math.min(38.0f, (strip.w - Tokens.S1 * (count - 1)) / count);
        if (cw < 14.0f || strip.h < 10.0f) {
            return;
        }
        for (int i = 0; i < count; i++) {
            final Rect cell = new Rect(strip.x + i * (cw + Tokens.S1), strip.y, cw, strip.h);
            final boolean isAuto = i == 0;
            final String id = isAuto ? "" : all.get(i - 1).id();
            final boolean active = id.equals(sel.instrumentId);
            final boolean hover = cell.contains(this.mouseX, this.mouseY);

            c.card(cell, 3.0f, active ? Tokens.RAISED : Tokens.CANVAS,
                    active ? Tokens.ACCENT : hover ? Tokens.LINE_HI : Tokens.LINE);
            this.instrumentHits.add(new Hit<>(cell, id));

            if (isAuto) {
                c.textCenterV("AUTO", cell.centerX(), cell.centerY(),
                        active ? Tokens.INK : Tokens.INK_3, Text.MICRO);
                continue;
            }

            if (ch != null && model != null) {
                this.thumbCtx.primary = ch;
                this.thumbCtx.all = model.channels;
                this.thumbCtx.style = model.style;
                this.thumbCtx.lod = Lod.REDUCED;
                this.thumbCtx.time = model.time;
                this.thumbCtx.yAxis = sel.yAxis();
                final Rect art = cell.inset(3.0f);
                c.clip(art);
                all.get(i - 1).draw(c, art, this.thumbCtx);
                c.unclip();
            } else {
                c.textCenterV(id.substring(0, Math.min(2, id.length()))
                                .toUpperCase(java.util.Locale.ROOT),
                        cell.centerX(), cell.centerY(), Tokens.INK_3, Text.MICRO);
            }
        }
    }

    private float drawMiniButton(PanelSurface c, float rightX, float y, String label, int color,
                                 int action) {
        final float w = c.textWidth(label, Text.MICRO) + 12.0f;
        final Rect r = new Rect(rightX - w, y - 2.0f, w, 16.0f);
        final boolean hover = r.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CHIP, hover ? Tokens.RAISED : Tokens.CANVAS, hover ? color : Tokens.LINE);
        c.textCenterV(label, r.centerX(), r.centerY(), color, Text.MICRO);
        this.buttonHits.add(new Hit<>(r, action));
        return r.x;
    }

    private void drawTray(PanelSurface c) {
        this.chipHits.clear();
        c.card(this.trayArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = this.trayArea.inset(Tokens.S2, 6.0f, Tokens.S2, 6.0f);
        c.text(Text.track("Signals"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);

        final List<StudioContext.Source> sources = this.ctx.sources();
        if (sources.isEmpty()) {
            for (int i = 0; i < TRAY_EMPTY.length; i++) {
                c.text(TRAY_EMPTY[i], inner.x, inner.y + 22.0f + i * 11.0f, Tokens.INK_3,
                        Text.MICRO);
            }
            return;
        }

        final Rect list = trayList();
        c.clip(list);
        float y = list.y - this.trayScroll.value();
        for (final StudioContext.Source source : sources) {
            c.text(Text.truncate(c, Text.track(source.displayName()), list.w, Text.MICRO),
                    list.x, y, Tokens.INK_3, Text.MICRO);
            y += 12.0f;
            for (final StatsDisplayPanelBE.DataChannel ch : source.channels) {
                final Rect chip = new Rect(list.x, y, list.w, CHIP_H);
                drawSignalChip(c, chip, ch);

                this.chipHits.add(new Hit<>(chip, ch.getId()));
                y += CHIP_H + Tokens.S1;
            }
            y += Tokens.S2;
        }
        c.unclip();

        this.trayScroll.setMetrics(y + this.trayScroll.value() - list.y, list.h);
        this.trayScroll.drawBar(c, trayTrack());
    }

    private void drawSignalChip(PanelSurface c, Rect r, StatsDisplayPanelBE.DataChannel ch) {
        final boolean pinned = isPinnedAnywhere(ch.getId());
        final boolean hover = r.contains(this.mouseX, this.mouseY) && this.drag == Drag.NONE;
        final int color = seriesColor(ch.getId());

        c.card(r, Tokens.R_CHIP, hover ? Tokens.RAISED : Tokens.CANVAS,
                pinned ? Tokens.alpha(color, 0.75f) : hover ? Tokens.LINE_HI : Tokens.LINE);
        Shapes.roundedRect(c, new Rect(r.x + 5.0f, r.centerY() - 3.0f, 6.0f, 6.0f), 1.5f, color);
        c.textV(Text.truncate(c, ch.getLabel(), r.w - 20.0f, Text.MICRO), r.x + 15.0f, r.centerY(),
                pinned ? Tokens.INK : Tokens.INK_2, Text.MICRO);
    }

    private Rect trayTrack() {
        final Rect inner = this.trayArea.inset(Tokens.S2, 6.0f, Tokens.S2, 6.0f);
        return new Rect(inner.right() - 3.0f, inner.y + 13.0f, 3.0f, inner.h - 13.0f);
    }

    private Rect trayList() {
        final Rect inner = this.trayArea.inset(Tokens.S2, 6.0f, Tokens.S2, 6.0f);
        return new Rect(inner.x, inner.y + 13.0f, inner.w - 6.0f, inner.h - 13.0f);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0) {
            if (this.yMinField != null && this.yMinField.editing()
                    && !this.yMinField.bounds().contains(mx, my)) {
                this.yMinField.commit();
            }
            if (this.yMaxField != null && this.yMaxField.editing()
                    && !this.yMaxField.bounds().contains(mx, my)) {
                this.yMaxField.commit();
            }
        }
        return super.mouseDown(mx, my, button);
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.mouseX = mx;
        this.mouseY = my;
        return super.mouseMoved(mx, my);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        this.mouseX = mx;
        this.mouseY = my;

        if (button == 0 && this.presetArea.contains(mx, my)) {
            for (final Hit<String> hit : this.presetHits) {
                if (hit.rect().contains(mx, my)) {
                    stampPreset(hit.value());
                    return true;
                }
            }
            return false;
        }
        if (button == 0 && this.inspectorArea.contains(mx, my)) {
            return pressInspector(mx, my);
        }
        if (this.trayArea.contains(mx, my)) {
            if (this.trayScroll.pressBar(trayTrack(), mx, my)) {
                return true;
            }
            if (button != 0 || !trayList().contains(mx, my)) {
                return false;
            }
            for (final Hit<String> hit : this.chipHits) {
                if (hit.rect().contains(mx, my)) {
                    this.drag = Drag.SIGNAL;
                    this.dragChannelId = hit.value();
                    return true;
                }
            }
            return false;
        }
        if (!this.body.isEmpty() && this.body.contains(mx, my)) {
            return pressCanvas(mx, my, button);
        }
        return false;
    }

    private void stampPreset(String presetId) {
        final Mk3Layout old = layout();
        final List<String> pins = new ArrayList<>();
        final List<String> instruments = new ArrayList<>();
        final List<YAxisRange> yRanges = new ArrayList<>();
        for (final Tile t : old.tiles()) {
            if (t.isBound()) {
                pins.add(t.channelId);
                instruments.add(t.instrumentId == null ? "" : t.instrumentId);
                yRanges.add(t.yAxis());
            }
        }
        final Mk3Layout next = LayoutPresets.build(presetId, old.cols(), old.rows(), channelCount());
        for (int i = 0; i < next.size() && i < pins.size(); i++) {
            next.get(i).channelId = pins.get(i);
            if (!instruments.get(i).isEmpty()) {
                next.get(i).instrumentId = instruments.get(i);
            }
            next.get(i).setYAxis(yRanges.get(i));
        }
        next.presetId = presetId;
        this.ctx.settings().adopt(next);
        this.selectedIndex = -1;
        this.drag = Drag.NONE;
        this.ctx.commit();
    }

    private boolean pressInspector(double mx, double my) {
        final Tile sel = selected();
        if (sel == null) {
            return false;
        }
        for (final Hit<Integer> hit : this.buttonHits) {
            if (!hit.rect().contains(mx, my)) {
                continue;
            }
            switch (hit.value()) {
                case 0 -> {
                    layout().remove(sel);
                    this.selectedIndex = -1;
                }
                case 1 -> sel.channelId = "";
                case 2 -> sel.value = !sel.value;
                case 3 -> sel.label = !sel.label;
                case 4 -> duplicateSelection();
                case 5 -> moveSelection(-1);
                default -> moveSelection(1);
            }
            this.ctx.commit();
            return true;
        }
        for (final Hit<String> hit : this.instrumentHits) {
            if (hit.rect().contains(mx, my)) {
                sel.instrumentId = hit.value();
                this.ctx.commit();
                return true;
            }
        }
        return false;
    }

    private boolean pressCanvas(double mx, double my, int button) {
        final Tile tile = tileUnder(mx, my);
        if (button == 1) {
            if (tile != null && tile.isBound()) {
                tile.channelId = "";
                this.ctx.commit();
                return true;
            }
            return false;
        }
        if (button != 0) {
            return false;
        }
        if (tile == null) {
            final Tile free = freeTileUnder(mx, my);
            final Tile added = free == null ? null : layout().add(free);
            if (added != null) {
                this.selectedIndex = layout().indexOf(added);
                this.ctx.commit();
                return true;
            }
            this.selectedIndex = -1;
            return false;
        }

        this.selectedIndex = layout().indexOf(tile);
        this.ghostX = tile.gx;
        this.ghostY = tile.gy;
        this.ghostW = tile.gw;
        this.ghostH = tile.gh;
        this.ghostValid = true;

        final Rect r = LayoutResolver.rectOf(tile, layout(), this.body, this.gap);
        if (handleRect(r).inset(-2.0f).contains(mx, my)) {
            this.drag = Drag.RESIZE;
            return true;
        }
        final int[] cell = LayoutResolver.cellAt(layout(), this.body, this.gap, mx, my);
        if (cell != null) {
            this.drag = Drag.MOVE;
            this.grabDx = cell[0] - tile.gx;
            this.grabDy = cell[1] - tile.gy;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.mouseX = mx;
        this.mouseY = my;
        if (this.trayScroll.dragBar(trayTrack(), my)) {
            return true;
        }
        if (this.drag == Drag.SIGNAL) {
            return true;
        }
        final Tile sel = selected();
        if (this.drag == Drag.NONE || sel == null) {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        final Mk3Layout layout = layout();
        if (this.drag == Drag.MOVE) {
            final int[] cell = LayoutResolver.nearestCell(layout, this.body, this.gap, mx, my);
            this.ghostX = clamp(cell[0] - this.grabDx, 0, layout.cols() - sel.gw);
            this.ghostY = clamp(cell[1] - this.grabDy, 0, layout.rows() - sel.gh);
            this.ghostW = sel.gw;
            this.ghostH = sel.gh;
        } else {
            final int[] cell = LayoutResolver.nearestCell(layout, this.body, this.gap,
                    mx + cellWidth() * 0.5f, my + cellHeight() * 0.5f);
            this.ghostX = sel.gx;
            this.ghostY = sel.gy;
            this.ghostW = clamp(cell[0] - sel.gx + 1, 1, layout.cols() - sel.gx);
            this.ghostH = clamp(cell[1] - sel.gy + 1, 1, layout.rows() - sel.gy);
        }
        this.ghostValid = layout.canPlace(this.ghostX, this.ghostY, this.ghostW, this.ghostH, sel);
        return true;
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.mouseX = mx;
        this.mouseY = my;
        this.trayScroll.endDrag();

        switch (this.drag) {
            case MOVE, RESIZE -> {
                final Tile sel = selected();
                if (this.ghostValid && sel != null && layout().place(sel,
                        this.ghostX, this.ghostY, this.ghostW, this.ghostH)) {
                    this.ctx.commit();
                }
            }
            case SIGNAL -> dropSignal(mx, my);
            default -> {
            }
        }
        this.drag = Drag.NONE;
        this.dragChannelId = "";
        return super.mouseUp(mx, my, button);
    }

    private void dropSignal(double mx, double my) {
        if (this.dragChannelId.isEmpty()) {
            return;
        }
        final Tile target = tileUnder(mx, my);
        if (target != null) {
            target.channelId = this.dragChannelId;
            this.selectedIndex = layout().indexOf(target);
            this.ctx.commit();
            return;
        }
        if (!this.body.isEmpty() && this.body.contains(mx, my)) {
            final Tile free = freeTileUnder(mx, my);
            final Tile added = free == null ? null : layout().add(free);
            if (added != null) {
                added.channelId = this.dragChannelId;
                this.selectedIndex = layout().indexOf(added);
                this.ctx.commit();
            }
            return;
        }

        final Tile sel = selected();
        if (sel != null) {
            sel.channelId = this.dragChannelId;
            this.ctx.commit();
        }
    }

    // arrow keys, a one cell tile on 24x12 is a few pixels
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        final Tile sel = selected();
        if (sel == null) {
            return false;
        }

        if (keyCode == 261 || keyCode == 259) {
            layout().remove(sel);
            this.selectedIndex = -1;
            this.ctx.commit();
            return true;
        }

        if (keyCode == 68) {
            duplicateSelection();
            this.ctx.commit();
            return true;
        }
        if (keyCode == 256) {
            this.selectedIndex = -1;
            return true;
        }
        final int dx = keyCode == 263 ? -1 : keyCode == 262 ? 1 : 0;
        final int dy = keyCode == 265 ? -1 : keyCode == 264 ? 1 : 0;
        if (dx == 0 && dy == 0) {
            return false;
        }
        final Mk3Layout layout = layout();
        final boolean resize = (modifiers & 1) != 0;
        final boolean moved = resize
                ? layout.place(sel, sel.gx, sel.gy,
                        clamp(sel.gw + dx, 1, layout.cols() - sel.gx),
                        clamp(sel.gh + dy, 1, layout.rows() - sel.gy))
                : layout.place(sel, clamp(sel.gx + dx, 0, layout.cols() - sel.gw),
                        clamp(sel.gy + dy, 0, layout.rows() - sel.gh), sel.gw, sel.gh);
        if (moved) {
            this.ctx.commit();
        }

        return true;
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        if (this.trayArea.contains(mx, my)) {
            return this.trayScroll.wheel(amount);
        }

        final Tile sel = selected();
        if (sel != null && !this.body.isEmpty() && this.body.contains(mx, my)) {
            final int step = amount > 0 ? 1 : -1;
            final Mk3Layout layout = layout();
            final int w = clamp(sel.gw + step, 1, layout.cols() - sel.gx);
            final int h = clamp(sel.gh + step, 1, layout.rows() - sel.gy);
            if ((w != sel.gw || h != sel.gh) && layout.place(sel, sel.gx, sel.gy, w, h)) {
                this.ctx.commit();
            }
            return true;
        }
        return false;
    }

    private Mk3Layout layout() {
        return this.ctx.settings().layout;
    }

    private Tile selected() {
        return this.selectedIndex < 0 ? null : layout().get(this.selectedIndex);
    }

    private void duplicateSelection() {
        final Tile copy = layout().duplicate(selected());
        if (copy != null) {
            this.selectedIndex = layout().indexOf(copy);
        }
    }

    private void moveSelection(int step) {
        final int to = this.selectedIndex + step;
        if (to < 0 || to >= layout().size()) {
            return;
        }
        layout().reorder(this.selectedIndex, to);
        this.selectedIndex = to;
    }

    private int channelCount() {
        return this.ctx.currentModel == null ? 0 : this.ctx.currentModel.channels.size();
    }

    private float cellWidth() {
        final int cols = Math.max(1, layout().cols());
        return (this.body.w - this.gap * (cols - 1)) / cols;
    }

    private float cellHeight() {
        final int rows = Math.max(1, layout().rows());
        return (this.body.h - this.gap * (rows - 1)) / rows;
    }

    private Tile tileUnder(double mx, double my) {
        if (this.body.isEmpty()) {
            return null;
        }
        final int[] cell = LayoutResolver.cellAt(layout(), this.body, this.gap, mx, my);
        return cell == null ? null : layout().tileAtCell(cell[0], cell[1]);
    }

    private Tile freeTileUnder(double mx, double my) {
        if (this.body.isEmpty()) {
            return null;
        }
        final int[] cell = LayoutResolver.cellAt(layout(), this.body, this.gap, mx, my);
        return cell == null ? null : layout().freeTileAt(cell[0], cell[1], DROP_W, DROP_H);
    }

    private PanelModel.Slot slotFor(Tile tile) {
        if (this.ctx.currentModel == null) {
            return null;
        }
        for (final PanelModel.Slot slot : this.ctx.currentModel.slots) {
            if (slot.tile == tile) {
                return slot;
            }
        }
        return null;
    }

    private ChannelView channelView(String id) {
        return this.ctx.currentModel == null ? null : this.ctx.currentModel.channelById(id);
    }

    private int seriesColor(String channelId) {
        final ChannelView view = channelView(channelId);
        return view != null ? view.color() : Tokens.seriesFor(channelId);
    }

    private boolean isPinnedAnywhere(String channelId) {
        for (final Tile t : layout().tiles()) {
            if (t.isBound() && t.channelId.equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
