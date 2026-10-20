package com.createstats.statsmk3.view;

import java.util.List;

import com.createstats.blockentity.ScanSettings;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.ScanControlPacket;
import com.createstats.network.SourceLabelPacket;
import com.createstats.statsmk3.page.SourceNames;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Slider;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class SourcesView extends StudioView {
    private static final float CARD_H = 56.0f;
    private static final float ROW_H = 26.0f;
    private static final float SCAN_H = 68.0f;
    private static final float SCAN_FLASH_SECONDS = 3.0f;

    private static final int MIN_RADIUS = 4;
    private static final int LOCKED_RADIUS = 64;
    private static final int UNLOCKED_RADIUS = 512;
    private static final int MIN_INTERVAL = 20;
    private static final int MAX_INTERVAL = 1200;

    private static final float LABEL_W = 44.0f;

    private static final float READOUT_W = 62.0f;

    private static final float MODE_W = 108.0f;

    private static final float MIN_TRACK_W = 60.0f;

    private static final float SCAN_H_STACKED = 92.0f;

    private boolean stacked;

    private final Scroll scroll = new Scroll();
    private List<StudioContext.Source> sources = List.of();
    private Rect listArea = Rect.EMPTY;
    private Rect scanArea = Rect.EMPTY;
    private Rect radiusTrack = Rect.EMPTY;
    private Rect intervalTrack = Rect.EMPTY;
    private Button scanButton;
    private Slider intervalSlider;
    private TextField rename;

    private String renamingId = "";

    private float scanFlash;
    private int sourcesAtScan;
    private boolean scanDirty;
    private float scanCooldown;
    private double mouseX;
    private double mouseY;

    public SourcesView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "sources";
    }

    @Override
    public String label() {
        return "Sources";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.rect(c, cx - 6.0f, cy - 4.5f, 12.0f, 1.4f, color);
        Shapes.rect(c, cx - 6.0f, cy - 0.7f, 12.0f, 1.4f, color);
        Shapes.rect(c, cx - 6.0f, cy + 3.1f, 7.0f, 1.4f, color);
        Shapes.circle(c, cx + 4.5f, cy + 3.8f, 1.8f, color);
    }

    @Override
    protected void build() {
        final Rect cardBody = new Rect(this.area.x, this.area.y + 44.0f, this.area.w, SCAN_H)
                .inset(Tokens.S3, 22.0f, Tokens.S3, Tokens.S2);
        this.stacked = cardBody.w < MODE_W + Tokens.S3 + LABEL_W + Tokens.S2 + MIN_TRACK_W
                + Tokens.S2 + READOUT_W;
        final float scanH = this.stacked ? SCAN_H_STACKED : SCAN_H;

        this.scanArea = new Rect(this.area.x, this.area.y + 44.0f, this.area.w, scanH);
        this.listArea = this.area.inset(0.0f, 44.0f + scanH + Tokens.S3, 0.0f, 0.0f);

        this.scanButton = new Button("Scan now", () -> {
            PacketDistributor.sendToServer(
                    ScanControlPacket.triggerScan(this.ctx.panel.getBlockPos()));
            // flash so the scan button visibly did something

            this.scanFlash = SCAN_FLASH_SECONDS;
            this.sourcesAtScan = countSources();
        });
        this.scanButton.setBounds(new Rect(this.area.right() - 84.0f, this.area.y - 2.0f,
                84.0f, 22.0f));
        this.widgets.add(this.scanButton);

        this.rename = new TextField(this::commitRename)
                .placeholder("Name this machine")
                .textScale(Text.BODY);
        this.rename.setVisible(false);
        this.widgets.add(this.rename);

        final Rect body = this.scanArea.inset(Tokens.S3, 22.0f, Tokens.S3, Tokens.S2);
        final Rect[] rows = Layout.rows(body, this.stacked ? 3 : 2, Tokens.S1);
        final Rect modeRow = rows[0];
        final Rect radiusRow = this.stacked ? rows[1] : rows[0];
        final Rect intervalRow = this.stacked ? rows[2] : rows[1];

        final Segmented mode = new Segmented(new String[]{"Manual", "Auto"},
                () -> scan().getScanMode() == ScanSettings.ScanMode.AUTO ? 1 : 0,
                i -> {
                    scan().setScanMode(i == 1
                            ? ScanSettings.ScanMode.AUTO : ScanSettings.ScanMode.MANUAL);
                    pushScan();
                });
        mode.setBounds(new Rect(modeRow.x, modeRow.y, Math.min(MODE_W, modeRow.w), modeRow.h));
        this.widgets.add(mode);

        final float labelX = this.stacked ? radiusRow.x : radiusRow.x + MODE_W + Tokens.S3;
        final float trackX = labelX + LABEL_W + Tokens.S2;
        final float trackW = Math.max(MIN_TRACK_W,
                radiusRow.right() - READOUT_W - Tokens.S2 - trackX);
        this.radiusTrack = new Rect(trackX, radiusRow.centerY() - Slider.HEIGHT * 0.5f,
                trackW, Slider.HEIGHT);
        final Slider radius = new Slider(
                () -> (scan().getScanRadius() - MIN_RADIUS) / (float) (maxRadius() - MIN_RADIUS),
                f -> {
                    scan().setScanRadius(Math.round(MIN_RADIUS + f * (maxRadius() - MIN_RADIUS)));
                    pushScan();
                });
        radius.setBounds(this.radiusTrack);
        this.widgets.add(radius);

        this.intervalTrack = new Rect(this.radiusTrack.x,
                intervalRow.centerY() - Slider.HEIGHT * 0.5f, this.radiusTrack.w, Slider.HEIGHT);
        final Slider interval = new Slider(
                () -> (scan().getAutoIntervalTicks() - MIN_INTERVAL)
                        / (float) (MAX_INTERVAL - MIN_INTERVAL),
                f -> {
                    scan().setAutoIntervalTicks(
                            Math.round(MIN_INTERVAL + f * (MAX_INTERVAL - MIN_INTERVAL)));
                    pushScan();
                });
        interval.setBounds(this.intervalTrack);
        this.intervalSlider = interval;
        this.widgets.add(interval);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);
        if (this.scanFlash > 0.0f) {
            this.scanFlash -= dt;
        }

        final boolean auto = scan().getScanMode() == ScanSettings.ScanMode.AUTO;
        this.intervalSlider.setEnabled(auto);

        if (this.scanDirty) {
            this.scanCooldown -= dt;
            if (this.scanCooldown <= 0.0f) {
                this.scanCooldown = 0.15f;
                this.scanDirty = false;
                PacketDistributor.sendToServer(ScanControlPacket.updateSettings(
                        this.ctx.panel.getBlockPos(), scan()));
            }
        }
    }

    private ScanSettings scan() {
        return this.ctx.panel.getScanSettings();
    }

    private void pushScan() {
        this.scanDirty = true;
    }

    private int maxRadius() {
        return scan().isRadiusUnlocked() ? UNLOCKED_RADIUS : LOCKED_RADIUS;
    }

    private int countSources() {
        final var discovered = scan().getDiscoveredSources();
        return discovered == null ? 0 : discovered.size();
    }

    private void drawScanCard(PanelSurface c) {
        c.card(this.scanArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Scan"), this.scanArea.x + Tokens.S3, this.scanArea.y + Tokens.S2,
                Tokens.INK_3, Text.MICRO);

        final ScanSettings settings = scan();
        final boolean auto = settings.getScanMode() == ScanSettings.ScanMode.AUTO;

        c.textRightV(countSources() + " found", this.scanArea.right() - Tokens.S3,
                this.scanArea.y + Tokens.S2 + 4.0f, Tokens.INK_3, Text.MICRO);

        c.textRightV("Radius", this.radiusTrack.x - Tokens.S2, this.radiusTrack.centerY(),
                Tokens.INK_2, Text.META);
        c.textV(settings.getScanRadius() + " blocks", this.radiusTrack.right() + Tokens.S2,
                this.radiusTrack.centerY(), Tokens.INK, Text.META);

        final int labelColor = auto ? Tokens.INK_2 : Tokens.INK_3;
        c.textRightV("Every", this.intervalTrack.x - Tokens.S2, this.intervalTrack.centerY(),
                labelColor, Text.META);
        c.textV(String.format(java.util.Locale.ROOT, "%.1f s", settings.getAutoIntervalSeconds()),
                this.intervalTrack.right() + Tokens.S2, this.intervalTrack.centerY(),
                auto ? Tokens.INK : Tokens.INK_3, Text.META);
    }

    private void drawScanFeedback(PanelSurface c) {
        if (this.scanFlash <= 0.0f) {
            return;
        }
        final int found = countSources();
        final String message = found == this.sourcesAtScan
                ? "Scanning" : found + (found == 1 ? " source found" : " sources found");
        final int color = found == this.sourcesAtScan ? Tokens.INK_3 : Tokens.GOOD;

        final float fade = Math.min(1.0f, this.scanFlash / 0.6f);
        c.textRightV(message, this.scanButton.bounds().x - Tokens.S2,
                this.scanButton.bounds().centerY(), Tokens.alpha(color, fade), Text.META);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        c.text("Sources", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text("Machines feeding this panel. Select one to focus it everywhere else.",
                this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        drawScanCard(c);
        drawScanFeedback(c);

        this.sources = this.ctx.sources();
        if (this.sources.isEmpty()) {
            drawEmpty(c);
            return;
        }

        final int cols = this.listArea.w > 420.0f ? 2 : 1;
        final float gap = Tokens.S2;
        final float cardsH = Layout.gridHeight(this.sources.size(), cols, CARD_H, gap);

        final List<StatsDisplayPanelBE.DataChannel> focusChannels = this.ctx.channels();
        final float signalsH = 22.0f + focusChannels.size() * ROW_H;
        this.scroll.setMetrics(cardsH + Tokens.S5 + signalsH, this.listArea.h);

        c.clip(this.listArea);
        final float top = this.listArea.y - this.scroll.value();

        for (int i = 0; i < this.sources.size(); i++) {
            final Rect cell = Layout.gridCell(
                    new Rect(this.listArea.x, top, this.listArea.w, cardsH), cols, CARD_H, gap, i);
            if (cell.bottom() >= this.listArea.y && cell.y <= this.listArea.bottom()) {
                drawSourceCard(c, cell, this.sources.get(i), i);
            }
        }

        final float sigY = top + cardsH + Tokens.S5;
        c.text(Text.track("Signals"), this.listArea.x, sigY - 14.0f, Tokens.INK_3, Text.MICRO);
        for (int i = 0; i < focusChannels.size(); i++) {
            final Rect row = new Rect(this.listArea.x, sigY + i * ROW_H, this.listArea.w, ROW_H - 2.0f);
            if (row.bottom() >= this.listArea.y && row.y <= this.listArea.bottom()) {
                drawSignalRow(c, row, focusChannels.get(i));
            }
        }
        c.unclip();

        this.scroll.drawBar(c, new Rect(this.listArea.right() - 3.0f, this.listArea.y,
                3.0f, this.listArea.h));
    }

    private void drawSourceCard(PanelSurface c, Rect r, StudioContext.Source source, int index) {
        final boolean selected = source.id.equals(this.ctx.focusedSourceId);
        final float p = enterProgress(index);
        final int fill = selected ? Tokens.RAISED : Tokens.SURFACE;
        final int border = selected ? Tokens.ACCENT : Tokens.LINE;

        if (selected) {
            Shapes.shadow(c, r.inset(-2.0f), Tokens.R_CARD + 2.0f, 10.0f,
                    Tokens.alpha(Tokens.ACCENT, 0.18f * p));
        }
        c.card(r, Tokens.R_CARD, fill, border);

        final Rect inner = r.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        final int shown = this.ctx.visibleCount(source);
        final int n = source.channels.size();
        final boolean muted = shown == 0;

        final Rect title = new Rect(inner.x, inner.y - 2.0f, inner.w - 76.0f, 16.0f);
        if (source.id.equals(this.renamingId) && this.rename != null) {
            this.rename.setBounds(title);
            this.rename.setVisible(true);
        } else {
            c.text(Text.truncate(c, source.displayName(), title.w, Text.BODY),
                    inner.x, inner.y, muted ? Tokens.INK_3 : Tokens.INK, Text.BODY);
        }

        if (r.contains(this.mouseX, this.mouseY) && !source.id.equals(this.renamingId)) {
            drawPencil(c, sourceRenameRect(r));
        }
        c.text(muted ? "muted, " + n + (n == 1 ? " signal" : " signals")
                        : shown < n ? shown + " of " + n + " shown"
                        : n + (n == 1 ? " signal" : " signals"),
                inner.x, inner.y + 12.0f, muted ? Tokens.CAUTION : Tokens.INK_3, Text.MICRO);

        final Rect eye = sourceEyeRect(r);
        drawEye(c, eye, !muted);

        if (!source.channels.isEmpty() && !muted) {
            final StatsDisplayPanelBE.DataChannel first = source.channels.get(0);
            final ChannelView view = findView(first.getId());
            if (view != null) {
                c.textRight(Text.value(view.value), inner.right(), inner.y + 14.0f,
                        view.color(), Text.SUB);
                drawSpark(c, new Rect(inner.right() - 74.0f, inner.bottom() - 18.0f, 70.0f, 16.0f),
                        view);
            }
        }
    }

    private static Rect sourceRenameRect(Rect card) {
        return new Rect(card.right() - Tokens.S3 - 40.0f, card.y + Tokens.S2, 18.0f, 16.0f);
    }

    private void drawPencil(PanelSurface c, Rect r) {
        final boolean hover = r.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CHIP, hover ? Tokens.RAISED : Tokens.CANVAS,
                hover ? Tokens.LINE_HI : Tokens.LINE);
        final int color = hover ? Tokens.INK : Tokens.INK_3;
        final float cx = r.centerX();
        final float cy = r.centerY();
        Shapes.line(c, cx - 3.4f, cy + 3.4f, cx + 3.0f, cy - 3.0f, 1.6f, color);
        Shapes.line(c, cx - 4.6f, cy + 4.6f, cx - 3.6f, cy + 3.0f, 1.4f, color);
    }

    private void beginRename(StudioContext.Source source) {
        if (this.rename == null || source.config == null) {
            return;
        }
        this.renamingId = source.id;
        this.rename.begin(SourceNames.named(source.config) ? source.config.getUserLabel() : "");
    }

    private void commitRename(String value) {
        final String id = this.renamingId;
        this.renamingId = "";
        if (this.rename != null) {
            this.rename.setVisible(false);
        }
        if (id.isEmpty()) {
            return;
        }
        for (final StudioContext.Source s : this.sources) {
            if (s.id.equals(id) && s.config != null) {
                s.config.setUserLabel(value.isEmpty() ? null : value);
            }
        }
        PacketDistributor.sendToServer(
                new SourceLabelPacket(this.ctx.panel.getBlockPos(), id, value));
    }

    @Override
    public boolean capturesTyping() {
        return this.rename != null && this.rename.editing();
    }

    private static Rect sourceEyeRect(Rect card) {
        return new Rect(card.right() - Tokens.S3 - 18.0f, card.y + Tokens.S2, 18.0f, 16.0f);
    }

    private void drawEye(PanelSurface c, Rect r, boolean visible) {
        final boolean hover = r.contains(this.mouseX, this.mouseY);
        c.card(r, Tokens.R_CHIP, hover ? Tokens.RAISED : Tokens.CANVAS,
                hover ? Tokens.LINE_HI : Tokens.LINE);
        final int color = visible ? (hover ? Tokens.INK : Tokens.INK_2) : Tokens.INK_3;
        final float cx = r.centerX();
        final float cy = r.centerY();

        Shapes.arc(c, cx, cy + 2.4f, 4.6f, 1.0f, (float) (Math.PI * 1.15), (float) (Math.PI * 0.7),
                color);
        Shapes.arc(c, cx, cy - 2.4f, 4.6f, 1.0f, (float) (Math.PI * 0.15), (float) (Math.PI * 0.7),
                color);
        if (visible) {
            Shapes.circle(c, cx, cy, 1.7f, color);
        } else {
            Shapes.line(c, cx - 5.0f, cy + 4.0f, cx + 5.0f, cy - 4.0f, 1.2f, Tokens.CAUTION);
        }
    }

    private void drawSpark(PanelSurface c, Rect r, ChannelView view) {
        if (!view.hasTrend() || r.isEmpty()) {
            return;
        }
        final int n = Math.min(48, view.historyCount);
        final float[] e = view.historyExtent(n, 0.16f);
        final float span = Math.max(1.0e-6f, e[1] - e[0]);
        final float[] xy = new float[n * 2];
        final int start = view.historyCount - n;
        for (int i = 0; i < n; i++) {
            final float t = (view.history[start + i] - e[0]) / span;
            xy[i * 2] = r.x + r.w * (i / (float) (n - 1));
            xy[i * 2 + 1] = r.bottom() - t * r.h;
        }
        Shapes.areaFill(c, xy, n, r.bottom(),
                Tokens.alpha(view.color(), 0.22f), Tokens.alpha(view.color(), 0.0f));
        Shapes.polyline(c, xy, n, 1.5f, view.color());
    }

    private void drawSignalRow(PanelSurface c, Rect r, StatsDisplayPanelBE.DataChannel ch) {
        final boolean focused = ch.getId().equals(this.ctx.focusedChannelId);
        final boolean hidden = this.ctx.isHidden(ch.getId());
        if (focused) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.RAISED);
        }
        final ChannelView view = findView(ch.getId());
        final int color = view != null ? view.color() : Tokens.seriesFor(ch.getId());

        Shapes.roundedRect(c, new Rect(r.x + 6.0f, r.centerY() - 4.0f, 8.0f, 8.0f), 2.0f,
                hidden ? Tokens.LINE_HI : color);
        c.text(Text.truncate(c, ch.getLabel(), r.w * 0.5f - 24.0f, Text.META),
                r.x + 20.0f, r.centerY() - Text.lineHeight(Text.META) * 0.5f,
                hidden ? Tokens.INK_3 : Tokens.INK, Text.META);

        drawEye(c, signalEyeRect(r), !hidden);

        if (view != null && !hidden) {
            c.textRightV(Text.value(view.value), signalEyeRect(r).x - Tokens.S2, r.centerY(),
                    view.alerting ? Tokens.CRITICAL : Tokens.INK_2, Text.META);
        } else if (hidden) {
            c.textRightV("hidden", signalEyeRect(r).x - Tokens.S2, r.centerY(),
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static Rect signalEyeRect(Rect row) {
        return new Rect(row.right() - 26.0f, row.centerY() - 8.0f, 18.0f, 16.0f);
    }

    private ChannelView findView(String id) {
        if (this.ctx.currentModel == null) {
            return null;
        }
        for (final ChannelView v : this.ctx.currentModel.channels) {
            if (v.id.equals(id)) {
                return v;
            }
        }
        return null;
    }

    private void drawEmpty(PanelSurface c) {
        final Rect box = this.listArea.top(96.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No sources linked yet", box.centerX(), box.centerY() - 14.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Create Display Link from a machine at this panel",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.META);
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

        if (this.rename != null && this.rename.editing() && !this.rename.bounds().contains(mx, my)) {
            this.rename.commit();
        }

        if (button == 0 && this.scroll.pressBar(barTrack(), mx, my)) {
            return true;
        }
        if (button != 0 || !this.listArea.contains(mx, my)) {
            return false;
        }
        final int cols = this.listArea.w > 420.0f ? 2 : 1;
        final float gap = Tokens.S2;
        final float cardsH = Layout.gridHeight(this.sources.size(), cols, CARD_H, gap);
        final float top = this.listArea.y - this.scroll.value();

        for (int i = 0; i < this.sources.size(); i++) {
            final Rect cell = Layout.gridCell(
                    new Rect(this.listArea.x, top, this.listArea.w, cardsH), cols, CARD_H, gap, i);
            if (!cell.contains(mx, my)) {
                continue;
            }
            final StudioContext.Source s = this.sources.get(i);

            if (sourceEyeRect(cell).contains(mx, my)) {
                this.ctx.setSourceHidden(s.id, this.ctx.visibleCount(s) > 0);
                return true;
            }
            if (sourceRenameRect(cell).contains(mx, my)) {
                beginRename(s);
                return true;
            }
            if (s.id.equals(this.renamingId)) {
                return true;
            }

            this.ctx.focusedSourceId = s.id.equals(this.ctx.focusedSourceId) ? "" : s.id;
            if (!s.channels.isEmpty()) {
                this.ctx.focusedChannelId = s.channels.get(0).getId();
            }
            return true;
        }

        final List<StatsDisplayPanelBE.DataChannel> channels = this.ctx.channels();
        final float sigY = top + cardsH + Tokens.S5;
        for (int i = 0; i < channels.size(); i++) {
            final Rect row = new Rect(this.listArea.x, sigY + i * ROW_H, this.listArea.w, ROW_H - 2.0f);
            if (!row.contains(mx, my)) {
                continue;
            }
            final String id = channels.get(i).getId();
            if (signalEyeRect(row).contains(mx, my)) {
                this.ctx.setHidden(id, !this.ctx.isHidden(id));
                return true;
            }
            this.ctx.focusedChannelId = id;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return this.listArea.contains(mx, my) && this.scroll.wheel(amount);
    }

    private Rect barTrack() {
        return new Rect(this.listArea.right() - 3.0f, this.listArea.y, 3.0f, this.listArea.h);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
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
}
