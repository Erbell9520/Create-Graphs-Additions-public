package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.createstats.alarm.AlarmCoverage;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.ConfigSyncPacket;
import com.createstats.statsmk3.YAxisRange;
import com.createstats.statsmk3.helper.HelperScan;
import com.createstats.statsmk3.page.SignalArt;
import com.createstats.statsmk3.page.Signals;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

// worst first, registry order buried firing alarms
@OnlyIn(Dist.CLIENT)
public final class AlertsView extends StudioView {
    private static final float HEAD_H = 68.0f;
    private static final float ROW_H = 32.0f;
    private static final float SECTION_H = 18.0f;
    private static final float EDITOR_MIN_W = 226.0f;
    private static final float EDITOR_MAX_W = 340.0f;

    private static final float SIDE_BY_SIDE_W = 400.0f;
    private static final float STACKED_EDITOR_H = 148.0f;

    private static final int WINDOW = Signals.FULL_WINDOW;

    public record Board(List<Signals.Signal> signals, List<Row> rows, String selectedId,
                        String hoverId, int listFilter, int editorTab, Scroll scroll,
                        Signals.Signal target, float threshold, boolean above, boolean armed,
                        boolean analog, boolean draggingLimit, HelperScan.Result helpers,
                        long nowTick, java.util.Set<String> spoken, YAxisRange yRange) {
        public Board {
            yRange = yRange == null ? YAxisRange.auto() : yRange;
        }

        public Board(List<Signals.Signal> signals, List<Row> rows, String selectedId,
                     String hoverId, int listFilter, int editorTab, Scroll scroll,
                     Signals.Signal target, float threshold, boolean above, boolean armed,
                     boolean analog, boolean draggingLimit, HelperScan.Result helpers,
                     long nowTick, java.util.Set<String> spoken) {
            this(signals, rows, selectedId, hoverId, listFilter, editorTab, scroll, target,
                    threshold, above, armed, analog, draggingLimit, helpers, nowTick, spoken,
                    YAxisRange.auto());
        }
    }

    record Row(Signals.Signal signal, String title, int count, float y, float h) {
        boolean isHeader() {
            return this.signal == null;
        }
    }

    private final Scroll scroll = new Scroll();

    private HelperScan.Result helpers = HelperScan.Result.EMPTY;

    private java.util.Set<String> spoken = java.util.Set.of();
    private float rescanTimer;

    private int editorTab;

    private int listFilter;

    private List<Signals.Signal> signals = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private String hoverId = "";
    private boolean sortHold;

    private Segmented directionControl;
    private Toggle enabledControl;
    private Segmented redstoneModeControl;
    private Button bindControl;
    private Button suggestControl;
    private Button nudgeDown;
    private Button nudgeUp;
    private TextField limitField;
    private Segmented yModeControl;
    private TextField yMinField;
    private TextField yMaxField;

    private SignalArt.Mapped editorMap;
    private boolean draggingLimit;

    private boolean sendPending;
    private float sendCooldown;
    private PanelSurface lastSurface;

    public AlertsView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "alerts";
    }

    @Override
    public String label() {
        return "Alerts";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public boolean capturesTyping() {
        return this.limitField != null && this.limitField.editing()
                || this.yMinField != null && this.yMinField.editing()
                || this.yMaxField != null && this.yMaxField.editing();
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.line(c, cx, cy - 6.0f, cx + 6.0f, cy + 5.0f, 1.3f, color);
        Shapes.line(c, cx + 6.0f, cy + 5.0f, cx - 6.0f, cy + 5.0f, 1.3f, color);
        Shapes.line(c, cx - 6.0f, cy + 5.0f, cx, cy - 6.0f, 1.3f, color);
        Shapes.rect(c, cx - 0.6f, cy - 1.5f, 1.3f, 3.6f, color);
    }

    static Rect listArea(Rect area) {
        final Rect body = new Rect(area.x, area.y + HEAD_H, area.w,
                Math.max(0.0f, area.h - HEAD_H));
        if (area.w >= SIDE_BY_SIDE_W) {
            return new Rect(body.x, body.y, body.w - editorWidth(area) - Tokens.S4, body.h);
        }
        return new Rect(body.x, body.y, body.w,
                Math.max(0.0f, body.h - stackedEditorHeight(area) - Tokens.S3));
    }

    static Rect editorArea(Rect area) {
        final Rect body = new Rect(area.x, area.y + HEAD_H, area.w,
                Math.max(0.0f, area.h - HEAD_H));
        if (area.w >= SIDE_BY_SIDE_W) {
            final float w = editorWidth(area);
            return new Rect(body.right() - w, body.y, w, body.h);
        }
        final float h = stackedEditorHeight(area);
        return new Rect(body.x, body.bottom() - h, body.w, h);
    }

    private static float editorWidth(Rect area) {
        return Math.max(EDITOR_MIN_W, Math.min(EDITOR_MAX_W, area.w * 0.46f));
    }

    private static float stackedEditorHeight(Rect area) {
        return Math.min(STACKED_EDITOR_H, Math.max(0.0f, area.h - HEAD_H) * 0.55f);
    }

    static Rect editorInner(Rect area) {
        return editorArea(area).inset(Tokens.S3, Tokens.S2 + 1.0f, Tokens.S3, Tokens.S2);
    }

    static float controlsTop(Rect area) {
        return editorArea(area).bottom() - Tokens.S2 - 57.0f;
    }

    private static final float AXIS_H = 14.0f;

    private static boolean axisShowing(Rect area) {
        return editorArea(area).h >= 190.0f;
    }

    static Rect chartRect(Rect area) {
        final Rect inner = editorInner(area);
        final float top = inner.y + 17.0f;

        final float axis = axisShowing(area) ? AXIS_H : 0.0f;
        return new Rect(inner.x, top,
                inner.w, Math.max(0.0f, controlsTop(area) - axis - Tokens.S2 - top));
    }

    public static Rect filterBounds(Rect area) {
        final float w = Math.min(174.0f, area.w * 0.36f);
        return new Rect(area.right() - w, area.y, w, 21.0f);
    }

    public static Rect tabBounds(Rect area) {
        final Rect inner = editorInner(area);
        return new Rect(inner.right() - 98.0f, inner.y - 2.0f, 98.0f, 16.0f);
    }

    public static Rect yModeBounds(Rect area) {
        final Rect editor = editorArea(area);
        return new Rect(editor.x + 30.0f, area.y + 45.0f,
                Math.min(68.0f, editor.w * 0.32f), 19.0f);
    }

    public static Rect yMinBounds(Rect area) {
        final Rect mode = yModeBounds(area);
        return new Rect(mode.right() + 4.0f, mode.y, 54.0f, mode.h);
    }

    public static Rect yMaxBounds(Rect area) {
        final Rect min = yMinBounds(area);
        return new Rect(min.right() + 4.0f, min.y, 54.0f, min.h);
    }

    public static Rect directionBounds(Rect area) {
        return new Rect(editorInner(area).x, controlsTop(area), 86.0f, 19.0f);
    }

    public static Rect limitFieldBounds(Rect area) {
        final Rect inner = editorInner(area);
        final float x = inner.x + 92.0f;

        final float reserved = Toggle.WIDTH + 36.0f;
        final float w = Math.min(76.0f, inner.right() - reserved - x);
        return new Rect(x, controlsTop(area), Math.max(0.0f, w), 19.0f);
    }

    private static boolean limitFieldFits(Rect area) {
        return limitFieldBounds(area).w >= 40.0f;
    }

    public static Rect toggleBounds(Rect area) {
        return new Rect(editorInner(area).right() - Toggle.WIDTH, controlsTop(area) + 1.0f,
                Toggle.WIDTH, Toggle.HEIGHT);
    }

    public static Rect suggestBounds(Rect area) {
        return new Rect(editorInner(area).x - 4.0f, controlsTop(area) + 24.0f, 122.0f, 17.0f);
    }

    public static Rect nudgeBounds(Rect area, boolean up) {
        final Rect inner = editorInner(area);
        return new Rect(inner.right() - (up ? 22.0f : 46.0f), controlsTop(area) + 24.0f,
                20.0f, 17.0f);
    }

    public static Rect redstoneModeBounds(Rect area) {
        return new Rect(editorInner(area).x, controlsTop(area) + 24.0f, 92.0f, 17.0f);
    }

    public static Rect bindBounds(Rect area) {
        final Rect inner = editorInner(area);
        return new Rect(inner.x + 96.0f, controlsTop(area) + 24.0f,
                Math.max(0.0f, inner.w - 96.0f - 48.0f), 17.0f);
    }

    static Rect barTrack(Rect area) {
        final Rect list = listArea(area);
        return new Rect(list.right() - 3.0f, list.y, 3.0f, list.h);
    }

    private static Rect rowRect(Rect area, Row row, float scroll) {
        final Rect list = listArea(area);
        return new Rect(list.x, list.y - scroll + row.y(), list.w, row.h());
    }

    private static Rect cardOf(Rect r) {
        return new Rect(r.x, r.y + 1.0f, r.w - 7.0f, r.h - 3.0f);
    }

    private static Rect pillRect(Rect card, PanelSurface c, String label) {
        final float w = Math.min(64.0f, SignalArt.pillWidth(c, label, 15.0f));
        return new Rect(card.right() - w - 6.0f, card.centerY() - 7.5f, w, 15.0f);
    }

    @Override
    protected void build() {
        this.widgets.add(new Segmented(new String[]{"All", "Armed", "Firing"},
                () -> this.listFilter, i -> this.listFilter = i)
                .textScale(Text.MICRO).setBounds(filterBounds(this.area)));
        this.widgets.add(new Segmented(new String[]{"Alert", "Redstone"},
                () -> this.editorTab, i -> this.editorTab = i)
                .textScale(Text.MICRO).setBounds(tabBounds(this.area)));

        this.directionControl = new Segmented(new String[]{"Above", "Below"},
                () -> above() ? 0 : 1, i -> setAbove(i == 0)).textScale(Text.MICRO);
        this.directionControl.setBounds(directionBounds(this.area));
        this.widgets.add(this.directionControl);

        this.limitField = new TextField(this::commitTypedLimit).textScale(Text.MICRO);
        this.limitField.setBounds(limitFieldBounds(this.area));
        this.widgets.add(this.limitField);

        this.enabledControl = new Toggle(this::enabled, this::setEnabled);
        this.enabledControl.setBounds(toggleBounds(this.area));
        this.widgets.add(this.enabledControl);

        this.suggestControl = new Button("Just outside normal", this::applySuggestion)
                .style(Button.Style.SECONDARY).textScale(Text.MICRO);
        this.suggestControl.setBounds(suggestBounds(this.area));
        this.widgets.add(this.suggestControl);

        this.nudgeDown = new Button("-", () -> nudge(-1)).style(Button.Style.SECONDARY);
        this.nudgeDown.setBounds(nudgeBounds(this.area, false));
        this.widgets.add(this.nudgeDown);
        this.nudgeUp = new Button("+", () -> nudge(1)).style(Button.Style.SECONDARY);
        this.nudgeUp.setBounds(nudgeBounds(this.area, true));
        this.widgets.add(this.nudgeUp);

        this.redstoneModeControl = new Segmented(new String[]{"Switch", "Analog"},
                () -> this.ctx.panel.getRedstoneMode() == 1 ? 1 : 0, this::setRedstoneMode)
                .textScale(Text.MICRO);
        this.redstoneModeControl.setBounds(redstoneModeBounds(this.area));
        this.widgets.add(this.redstoneModeControl);

        this.bindControl = new Button("Bind selected signal", this::bindRedstoneToFocus)
                .style(Button.Style.GHOST).textScale(Text.MICRO);
        this.bindControl.setBounds(bindBounds(this.area));
        this.widgets.add(this.bindControl);

        this.yModeControl = new Segmented(new String[]{"Auto", "Fixed"},
                () -> targetRange().isFixed() ? 1 : 0, this::setYAxisMode).textScale(Text.MICRO);
        this.yModeControl.setBounds(yModeBounds(this.area));
        this.widgets.add(this.yModeControl);
        this.yMinField = new TextField(this::commitYMin).placeholder("Min").textScale(Text.MICRO);
        this.yMinField.setBounds(yMinBounds(this.area));
        this.widgets.add(this.yMinField);
        this.yMaxField = new TextField(this::commitYMax).placeholder("Max").textScale(Text.MICRO);
        this.yMaxField.setBounds(yMaxBounds(this.area));
        this.widgets.add(this.yMaxField);
    }

    private Board board() {
        final Signals.Signal target = targetSignal();
        return new Board(this.signals, this.rows,
                target == null ? this.ctx.focusedChannelId : target.id,
                this.hoverId, this.listFilter, this.editorTab, this.scroll,
                target, threshold(), above(), enabled(),
                this.editorTab == 1 && this.ctx.panel.getRedstoneMode() == 1,
                this.draggingLimit, this.helpers, nowTick(), this.spoken,
                target == null ? YAxisRange.auto() : this.ctx.settings().alertYRange(target.id));
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);
        this.signals = Signals.of(this.ctx.panel);
        if (!this.sortHold) {
            this.rows = rowsOf(this.signals, this.listFilter, nowTick());
        }
        this.scroll.setMetrics(contentHeight(this.rows), listArea(this.area).h);
        syncEditorVisibility();

        this.rescanTimer -= dt;
        if (this.rescanTimer <= 0.0f) {
            this.rescanTimer = 2.0f;
            this.helpers = HelperScan.scan(this.ctx.panel);

            this.spoken = AlarmCoverage.spokenChannelsOf(this.ctx.panel.getLevel(),
                    this.ctx.panel.getBlockPos());
        }

        if (this.sendPending) {
            this.sendCooldown -= dt;
            if (this.sendCooldown <= 0.0f) {
                this.sendCooldown = 0.12f;
                this.sendPending = false;
                sendNow();
            }
        }
    }

    static List<Row> rowsOf(List<Signals.Signal> signals, int listFilter, long nowTick) {
        final List<Signals.Signal> firing = new ArrayList<>();
        final List<Signals.Signal> armed = new ArrayList<>();
        final List<Signals.Signal> quiet = new ArrayList<>();
        for (final Signals.Signal s : signals) {
            if (s.firing()) {
                firing.add(s);
            } else if (s.armed()) {
                armed.add(s);
            } else {
                quiet.add(s);
            }
        }
        // rank once per signal, not per comparison. sorting 300 samples n log n times a frame, no

        final java.util.Map<String, Float> rank = new java.util.HashMap<>();
        for (final Signals.Signal s : firing) {
            rank.put(s.id, overshoot(s, nowTick));
        }
        for (final Signals.Signal s : armed) {
            rank.put(s.id, Signals.proximity(s, Signals.stats(s, WINDOW, nowTick)));
        }
        final Comparator<Signals.Signal> worstFirst =
                Comparator.comparingDouble((Signals.Signal s) -> -rank.getOrDefault(s.id, 0.0f))
                        .thenComparingInt(s -> s.index);
        firing.sort(worstFirst);
        armed.sort(worstFirst);

        final List<Row> out = new ArrayList<>();
        float y = 0.0f;
        y = section(out, y, "Firing now", firing, 2, listFilter);
        y = section(out, y, "Armed", armed, 1, listFilter);
        section(out, y, "Not watched", quiet, 0, listFilter);
        return out;
    }

    private static float section(List<Row> out, float y, String title, List<Signals.Signal> members,
                                 int minFilter, int listFilter) {
        if (members.isEmpty() || listFilter > minFilter) {
            return y;
        }
        out.add(new Row(null, title, members.size(), y, SECTION_H));
        y += SECTION_H;
        for (final Signals.Signal s : members) {
            out.add(new Row(s, s.name, 0, y, ROW_H));
            y += ROW_H;
        }
        return y + Tokens.S1;
    }

    static float contentHeight(List<Row> rows) {
        float y = 0.0f;
        for (final Row r : rows) {
            y = Math.max(y, r.y() + r.h());
        }
        return y;
    }

    private static float overshoot(Signals.Signal s, long nowTick) {
        final Signals.Stats st = Signals.stats(s, WINDOW, nowTick);
        if (!st.usable()) {
            return 0.0f;
        }
        final float past = s.above() ? st.latest - s.threshold() : s.threshold() - st.latest;
        final float scale = Math.max(st.spread(), Math.max(Math.abs(s.threshold()) * 0.05f, 1.0e-3f));
        return past / scale;
    }

    private long nowTick() {
        try {
            return this.ctx.panel == null || this.ctx.panel.getLevel() == null
                    ? 0L : this.ctx.panel.getLevel().getGameTime();
        } catch (final RuntimeException e) {
            return 0L;
        }
    }

    private void syncEditorVisibility() {
        final boolean redstone = this.editorTab == 1;
        final boolean analog = redstone && this.ctx.panel.getRedstoneMode() == 1;
        final boolean hasTarget = target() != null;

        this.redstoneModeControl.setVisible(redstone && hasTarget);
        this.bindControl.setVisible(redstone && hasTarget);
        this.suggestControl.setVisible(!redstone && hasTarget);
        if (redstone) {
            final String bound = this.ctx.panel.getRedstoneChannelId();
            final StatsDisplayPanelBE.DataChannel ch =
                    bound == null || bound.isEmpty() ? null : this.ctx.panel.getChannel(bound);
            this.bindControl.setLabel(ch == null ? "Bind selected signal"
                    : "On " + Text.truncate(this.ctx.font, ch.getLabel(), 108.0f, 1.0f));
        }

        final boolean limit = hasTarget && !analog;
        this.directionControl.setVisible(limit);
        this.nudgeDown.setVisible(limit);
        this.nudgeUp.setVisible(limit);
        this.enabledControl.setVisible(hasTarget);
        this.limitField.setVisible(limit && limitFieldFits(this.area));
        if (limit && !this.limitField.editing()) {
            syncLimitField();
        }
        final YAxisRange range = targetRange();
        this.yModeControl.setVisible(hasTarget);
        this.yMinField.setVisible(hasTarget && range.isFixed());
        this.yMaxField.setVisible(hasTarget && range.isFixed());
        if (range.isFixed()) {
            this.yMinField.setText(Float.toString(range.min()));
            this.yMaxField.setText(Float.toString(range.max()));
        }
    }

    private YAxisRange targetRange() {
        final Signals.Signal target = targetSignal();
        return target == null ? YAxisRange.auto() : this.ctx.settings().alertYRange(target.id);
    }

    private void setYAxisMode(int mode) {
        final Signals.Signal target = targetSignal();
        if (target == null) {
            return;
        }
        final Signals.Stats stats = Signals.stats(target, WINDOW, nowTick());
        final YAxisRange next = mode == 0 ? YAxisRange.auto()
                : stats.usable() ? YAxisRange.rounded(stats.lo, stats.hi)
                        : YAxisRange.fixed(0.0f, 1.0f);
        this.ctx.settings().setAlertYRange(target.id, next);
        this.ctx.commit();
        syncEditorVisibility();
    }

    private void commitYMin(String text) {
        commitYAxis(text, true);
    }

    private void commitYMax(String text) {
        commitYAxis(text, false);
    }

    private void commitYAxis(String text, boolean minimum) {
        final Signals.Signal target = targetSignal();
        final YAxisRange current = targetRange();
        if (target == null || !current.isFixed()) {
            syncEditorVisibility();
            return;
        }
        try {
            final float value = Float.parseFloat(text.replace(" ", "").trim());
            final YAxisRange next = minimum
                    ? YAxisRange.fixed(value, current.max())
                    : YAxisRange.fixed(current.min(), value);
            if (!next.isFixed()) {
                syncEditorVisibility();
                return;
            }
            this.ctx.settings().setAlertYRange(target.id, next);
            this.ctx.commit();
        } catch (final NumberFormatException e) {
        }
        syncEditorVisibility();
    }

    private void syncLimitField() {
        this.limitField.setText(Text.value(threshold()));
    }

    private StatsDisplayPanelBE.DataChannel target() {
        if (this.editorTab == 0) {
            // resolve against our own list. hidden channels resolved to someone else's and clicks edited the wrong threshold

            final Signals.Signal picked = Signals.byId(this.signals, this.ctx.focusedChannelId);
            return picked != null ? picked.channel : this.ctx.focusedChannel();
        }
        final String bound = this.ctx.panel.getRedstoneChannelId();
        final StatsDisplayPanelBE.DataChannel ch =
                bound == null || bound.isEmpty() ? null : this.ctx.panel.getChannel(bound);
        return ch != null ? ch : this.ctx.focusedChannel();
    }

    private Signals.Signal targetSignal() {
        final StatsDisplayPanelBE.DataChannel ch = target();
        return ch == null ? null : Signals.byId(this.signals, ch.getId());
    }

    private boolean above() {
        if (this.editorTab == 1) {
            return this.ctx.panel.isRedstoneAbove();
        }
        final StatsDisplayPanelBE.DataChannel ch = target();
        return ch == null || ch.isAlertAbove();
    }

    private void setAbove(boolean above) {
        if (this.editorTab == 1) {
            this.ctx.panel.setRedstoneAbove(above);
        } else {
            final StatsDisplayPanelBE.DataChannel ch = target();
            if (ch == null) {
                return;
            }
            ch.setAlertAbove(above);
        }
        queueSend();
    }

    private boolean enabled() {
        if (this.editorTab == 1) {
            return this.ctx.panel.isRedstoneEnabled();
        }
        final StatsDisplayPanelBE.DataChannel ch = target();
        return ch != null && ch.isAlertEnabled();
    }

    private void setEnabled(boolean on) {
        if (this.editorTab == 1) {
            this.ctx.panel.setRedstoneEnabled(on);
            if (on && this.ctx.panel.getRedstoneChannelId().isEmpty()) {
                bindRedstoneToFocus();
                return;
            }
        } else {
            final StatsDisplayPanelBE.DataChannel ch = target();
            if (ch == null) {
                return;
            }

            if (on && ch.getAlertThreshold() == 0.0f) {
                applySuggestion();
            }
            ch.setAlertEnabled(on);
        }
        queueSend();
    }

    private float threshold() {
        if (this.editorTab == 1) {
            return this.ctx.panel.getRedstoneThreshold();
        }
        final StatsDisplayPanelBE.DataChannel ch = target();
        return ch == null ? 0.0f : ch.getAlertThreshold();
    }

    private void setThreshold(float value) {
        if (this.editorTab == 1) {
            this.ctx.panel.setRedstoneThreshold(value);
        } else {
            final StatsDisplayPanelBE.DataChannel ch = target();
            if (ch == null) {
                return;
            }
            ch.setAlertThreshold(value);
        }
        if (this.limitField != null && !this.limitField.editing()) {
            syncLimitField();
        }
        queueSend();
    }

    private void commitTypedLimit(String text) {
        if (text == null || text.trim().isEmpty()) {
            syncLimitField();
            return;
        }
        try {
            setThreshold(Float.parseFloat(text.replace(" ", "").replace("%", "").trim()));
        } catch (final NumberFormatException e) {
            syncLimitField();
        }
    }

    private void nudge(int direction) {
        final float span = this.editorMap == null
                ? Math.max(1.0f, Math.abs(threshold()) * 0.1f)
                : this.editorMap.hi() - this.editorMap.lo();
        setThreshold(threshold() + span * 0.02f * direction);
    }

    private void applySuggestion() {
        final Signals.Signal s = targetSignal();
        if (s == null) {
            return;
        }
        setThreshold(Signals.suggest(Signals.stats(s, WINDOW, nowTick()), above()));
    }

    private void setRedstoneMode(int mode) {
        this.ctx.panel.setRedstoneMode(mode);
        queueSend();
    }

    private void bindRedstoneToFocus() {
        final StatsDisplayPanelBE.DataChannel ch = this.ctx.focusedChannel();
        if (ch == null) {
            return;
        }
        this.ctx.panel.setRedstoneChannelId(ch.getId());
        queueSend();
    }

    private void queueSend() {
        this.sendPending = true;
    }

    private void sendNow() {
        final StatsDisplayPanelBE.DataChannel ch = target();
        if (this.editorTab == 1) {
            PacketDistributor.sendToServer(ConfigSyncPacket.redstoneConfig(
                    this.ctx.panel.getBlockPos(), this.ctx.panel.getRedstoneChannelId(),
                    this.ctx.panel.getRedstoneThreshold(), this.ctx.panel.isRedstoneAbove(),
                    this.ctx.panel.isRedstoneEnabled(), this.ctx.panel.getRedstoneMode()));
            return;
        }
        if (ch == null) {
            return;
        }
        PacketDistributor.sendToServer(ConfigSyncPacket.alertConfig(
                this.ctx.panel.getBlockPos(), ch.getId(),
                ch.getAlertThreshold(), ch.isAlertAbove(), ch.isAlertEnabled()));
    }

    @Override
    public void draw(PanelSurface c) {
        this.lastSurface = c;
        super.draw(c);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        this.editorMap = paint(c, this.area, board());
        if (targetSignal() != null) {
            final Rect mode = yModeBounds(this.area);
            c.textRightV("Y", mode.x - 5.0f, mode.centerY(), Tokens.INK_3, Text.MICRO);
        }
    }

    public static SignalArt.Mapped paint(PanelSurface c, Rect area, Board b) {
        drawHeader(c, area, b);
        if (b.signals().isEmpty()) {
            SignalArt.empty(c, new Rect(area.x, area.y + HEAD_H, area.w, area.h - HEAD_H),
                    "Nothing to watch yet",
                    "Link a machine on the Sources step, then set a limit on one of its readings.");
            return null;
        }
        drawList(c, area, b);
        return drawEditor(c, area, b);
    }

    private static void drawHeader(PanelSurface c, Rect area, Board b) {
        c.text("Alerts", area.x, area.y, Tokens.INK, Text.SUB);

        int firing = 0;
        int armed = 0;
        for (final Signals.Signal s : b.signals()) {
            if (s.firing()) {
                firing++;
            } else if (s.armed()) {
                armed++;
            }
        }
        final int quiet = b.signals().size() - firing - armed;
        final float y = area.y + 27.0f;
        float x = area.x;
        x = tally(c, x, y, firing, "firing", firing > 0 ? Tokens.CRITICAL : Tokens.INK_3);
        x = tally(c, x, y, armed, "armed", armed > 0 ? Tokens.GOOD : Tokens.INK_3);
        tally(c, x, y, quiet, "not watched", Tokens.INK_3);

        drawHardware(c, area.right(), y, b.helpers());
    }

    private static float tally(PanelSurface c, float x, float y, int n, String word, int color) {
        final String value = String.valueOf(n);
        c.textV(value, x, y, color, Text.META);
        final float vw = c.textWidth(value, Text.META) + 4.0f;
        c.textV(word, x + vw, y, Tokens.INK_3, Text.MICRO);
        return x + vw + c.textWidth(word, Text.MICRO) + 12.0f;
    }

    private static void drawHardware(PanelSurface c, float rightX, float y,
                                     HelperScan.Result helpers) {
        if (helpers == null || helpers.isEmpty()) {
            c.textRightV("Nothing in range will sound", rightX, y, Tokens.INK_3, Text.MICRO);
            return;
        }
        float x = rightX;
        x = hardwareChip(c, x, y, "Recorder", helpers.recorders, false);
        x = hardwareChip(c, x, y, "Beacon", helpers.beacons, helpers.alarmFiring);
        x = hardwareChip(c, x, y, "Annunciator", helpers.annunciators, false);
        hardwareChip(c, x, y, "Alarm", helpers.alarms, helpers.alarmFiring);
    }

    private static float hardwareChip(PanelSurface c, float rightX, float centerY, String name,
                                      int count, boolean firing) {
        if (count <= 0) {
            return rightX;
        }
        final String label = count > 1 ? name + " x" + count : name;
        final int color = firing ? Tokens.CRITICAL : Tokens.GOOD;
        final float w = SignalArt.pillWidth(c, label, 15.0f);
        SignalArt.pill(c, new Rect(rightX - w, centerY - 7.5f, w, 15.0f), label, color, 1.0f);
        return rightX - w - 5.0f;
    }

    private static void drawList(PanelSurface c, Rect area, Board b) {
        final Rect list = listArea(area);
        if (list.isEmpty()) {
            return;
        }
        if (b.rows().isEmpty()) {
            SignalArt.empty(c, list,
                    b.listFilter() == 2 ? "Nothing is firing" : "Nothing is armed",
                    b.listFilter() == 2 ? "Every rule on this panel is quiet."
                            : "Pick a signal and give it a limit to start watching it.");
            return;
        }
        c.clip(list);
        for (final Row row : b.rows()) {
            final Rect r = rowRect(area, row, b.scroll().value());
            if (r.bottom() < list.y - 2.0f || r.y > list.bottom() + 2.0f) {
                continue;
            }
            if (row.isHeader()) {
                drawSectionHeading(c, r, row);
            } else {
                drawAlertRow(c, r, row.signal(), b);
            }
        }
        c.unclip();
        b.scroll().drawBar(c, barTrack(area));
    }

    private static void drawSectionHeading(PanelSurface c, Rect r, Row row) {
        final int color = "Firing now".equals(row.title()) ? Tokens.CRITICAL
                : "Armed".equals(row.title()) ? Tokens.GOOD : Tokens.INK_3;
        final float y = r.bottom() - Text.lineHeight(Text.MICRO) - 4.0f;
        c.text(Text.track(row.title()), r.x + 1.0f, y, color, Text.MICRO);
        c.textRight(String.valueOf(row.count()), r.right() - 8.0f, y, Tokens.INK_3, Text.MICRO);
        c.hairlineH(r.x + 1.0f, r.bottom() - 1.0f, r.w - 9.0f, Tokens.alpha(Tokens.LINE, 0.8f));
    }

    private static void drawAlertRow(PanelSurface c, Rect r, Signals.Signal s, Board b) {
        final boolean selected = s.id.equals(b.selectedId());
        final boolean hovered = s.id.equals(b.hoverId());
        final boolean firing = s.firing();
        final Rect card = cardOf(r);

        c.card(card, Tokens.R_CHIP,
                firing ? Tokens.alpha(Tokens.CRITICAL, 0.10f)
                        : selected ? Tokens.RAISED
                                : Tokens.alpha(Tokens.SURFACE, hovered ? 1.0f : 0.6f),
                firing ? Tokens.alpha(Tokens.CRITICAL, 0.75f)
                        : selected ? Tokens.LINE_HI : Tokens.LINE);
        final int accent = firing ? Tokens.CRITICAL : s.armed() ? Tokens.GOOD : s.series;
        Shapes.roundedRect(c, new Rect(card.x + 3.0f, card.y + 4.0f, 2.0f, card.h - 8.0f), 1.0f,
                s.armed() || firing ? accent : Tokens.alpha(s.series, 0.55f));

        final String pillText = firing ? "FIRING" : s.armed() ? "ARMED" : "OFF";
        final int pillColor = firing ? Tokens.CRITICAL : s.armed() ? Tokens.GOOD : Tokens.INK_3;
        final Rect pill = pillRect(card, c, pillText);
        SignalArt.pill(c, pill, pillText, pillColor, s.armed() || firing ? 1.0f : 0.4f);

        final float sparkW = Math.min(56.0f, Math.max(0.0f, (card.w - 118.0f) * 0.5f));
        final Rect spark = new Rect(pill.x - sparkW - 8.0f, card.centerY() - 6.0f, sparkW, 12.0f);
        if (sparkW > 16.0f) {
            SignalArt.spark(c, spark, Signals.window(s, WINDOW),
                    Tokens.alpha(s.series, selected || hovered ? 1.0f : 0.7f),
                    s.armed(), s.threshold(), s.above(),
                    firing ? Tokens.CRITICAL : Tokens.CAUTION);
        }

        final float textLeft = card.x + 10.0f;
        final float valueRight = (sparkW > 16.0f ? spark.x : pill.x) - 8.0f;
        final String value = Signals.withUnit(s.value(), s.unit);
        c.textRight(value, valueRight, card.y + 4.0f, firing ? Tokens.CRITICAL : Tokens.INK,
                Text.MICRO);

        final float nameRoom = valueRight - textLeft - c.textWidth(value, Text.MICRO) - 8.0f;
        c.text(Text.truncate(c, s.name, nameRoom, Text.MICRO), textLeft, card.y + 4.0f,
                Tokens.INK, Text.MICRO);

        final String rule = s.armed() || s.threshold() != 0.0f
                ? (s.above() ? "over " : "under ") + Signals.withUnit(s.threshold(), s.unit)
                : "no limit set";

        final String spoken = b.spoken().contains(s.id) ? "   spoken" : "";
        final String under = (s.group.isEmpty() ? rule : s.group + "   " + rule) + spoken;
        c.text(Text.truncate(c, under, valueRight - textLeft, Text.MICRO),
                textLeft, card.bottom() - Text.lineHeight(Text.MICRO) - 4.0f,
                Tokens.INK_3, Text.MICRO);
    }

    private static SignalArt.Mapped drawEditor(PanelSurface c, Rect area, Board b) {
        final Rect editor = editorArea(area);
        c.card(editor, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
        final Rect inner = editorInner(area);
        final Signals.Signal s = b.target();
        final String heading = Text.track(b.editorTab() == 1 ? "Redstone output" : "Rule");
        c.text(heading, inner.x, inner.y, Tokens.INK_3, Text.MICRO);

        if (s == null) {
            SignalArt.empty(c, inner, "No signal picked",
                    "Choose one on the left to give it a limit.");
            return null;
        }
        final Rect chart = chartRect(area);

        if (chart.h < 54.0f) {
            c.text(Text.truncate(c, s.name + "  " + s.group,
                            inner.w - c.textWidth(heading, Text.MICRO) - 116.0f, Text.MICRO),
                    inner.x + c.textWidth(heading, Text.MICRO) + 8.0f, inner.y,
                    Tokens.INK_2, Text.MICRO);
        }

        final boolean redstone = b.editorTab() == 1;
        final Signals.Stats st = Signals.stats(s, WINDOW, b.nowTick());
        final float[] window = Signals.window(s, WINDOW);
        final boolean tripped = b.above() ? st.latest >= b.threshold() : st.latest <= b.threshold();

        SignalArt.Mapped map = null;
        if (!chart.isEmpty()) {
            final int limitColor = tripped ? (redstone ? Tokens.GOOD : Tokens.CRITICAL)
                    : Tokens.CAUTION;
            final SignalArt.Lane lane = new SignalArt.Lane(s, window, st, s.series,
                    !b.analog(), b.threshold(), b.above(), limitColor, b.yRange());
            map = SignalArt.lane(c, chart, lane, 1.0f, 1.0f);
            if (!b.analog() && map.count() >= 2) {
                drawLimitHandle(c, map, b, limitColor);
            }
            if (axisShowing(area)) {
                SignalArt.timeAxis(c, new Rect(chart.x, chart.bottom() + 2.0f,
                                chart.w - (chart.w > 210.0f ? SignalArt.AXIS_W : 0.0f),
                                AXIS_H - 2.0f), st.seconds, -1.0f);
            }
        }

        c.textRightV(redstone ? "Output" : "Armed", inner.right() - Toggle.WIDTH - 6.0f,
                controlsTop(area) + 10.0f, Tokens.INK_3, Text.MICRO);

        drawVerdict(c, inner, b, s, st, window, redstone, tripped);
        return map;
    }

    private static void drawLimitHandle(PanelSurface c, SignalArt.Mapped map, Board b, int color) {
        final float y = Math.round(map.yFor(b.threshold()));
        if (y < map.plot().y - 1.0f || y > map.plot().bottom() + 1.0f) {
            return;
        }
        final Rect grip = new Rect(map.plot().x - 3.0f, y - 5.0f, 11.0f, 11.0f);
        c.card(grip, 3.0f, Tokens.alpha(color, b.draggingLimit() ? 0.9f : 0.55f),
                Tokens.alpha(color, 1.0f));
        Shapes.rect(c, grip.x + 3.0f, grip.centerY() - 2.0f, 5.0f, 1.0f,
                Tokens.alpha(Tokens.CANVAS, 0.85f));
        Shapes.rect(c, grip.x + 3.0f, grip.centerY() + 1.0f, 5.0f, 1.0f,
                Tokens.alpha(Tokens.CANVAS, 0.85f));
        if (b.draggingLimit()) {
            c.textV(Text.value(b.threshold()), grip.right() + 5.0f, grip.centerY(),
                    color, Text.MICRO);
        }
    }

    private static void drawVerdict(PanelSurface c, Rect inner, Board b, Signals.Signal s,
                                    Signals.Stats st, float[] window, boolean redstone,
                                    boolean tripped) {
        final float y = inner.bottom() - 6.0f;
        if (b.analog()) {
            Shapes.circle(c, inner.x + 3.0f, y, 3.0f, Tokens.GOOD);
            c.textV(Text.truncate(c, "Emits 0 to 15 across " + Text.compact(st.lo) + " to "
                            + Text.compact(st.hi), inner.w - 12.0f, Text.MICRO),
                    inner.x + 10.0f, y, Tokens.INK_2, Text.MICRO);
            return;
        }
        final int color = tripped ? (redstone ? Tokens.GOOD : Tokens.CRITICAL) : Tokens.INK_3;
        final StringBuilder sb = new StringBuilder();
        sb.append("Now ").append(Signals.withUnit(st.latest, s.unit));
        sb.append(tripped ? (redstone ? ", emitting 15" : ", firing") : ", quiet");

        if (st.usable() && window.length > 2) {
            final int fired = Signals.crossings(window, b.threshold(), b.above());
            final String span = Signals.ago(st.seconds);
            sb.append(".  ");

            if (inner.w >= 250.0f) {
                sb.append(fired == 0 ? "Never crossed in " + span + "."
                        : "Would have fired " + fired + (fired == 1 ? " time in " : " times in ")
                                + span + ".");
            } else {
                sb.append(fired == 0 ? "Never crossed in " + span
                        : fired + (fired == 1 ? " alarm in " : " alarms in ") + span);
            }
        }
        Shapes.circle(c, inner.x + 3.0f, y, 3.0f, color);
        c.textV(Text.truncate(c, sb.toString(), inner.w - 12.0f, Text.MICRO),
                inner.x + 10.0f, y, color, Text.MICRO);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (button == 0) {
            if (this.limitField != null && this.limitField.editing()
                    && !this.limitField.bounds().contains(mx, my)) {
                this.limitField.commit();
            }
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
        final boolean widget = super.mouseMoved(mx, my);
        String hit = "";
        final Rect list = listArea(this.area);
        if (list.contains(mx, my)) {
            for (final Row row : this.rows) {
                if (!row.isHeader() && rowRect(this.area, row, this.scroll.value()).contains(mx, my)) {
                    hit = row.signal().id;
                    break;
                }
            }
        }
        this.hoverId = hit;

        this.sortHold = list.contains(mx, my);

        final Signals.Signal hovered = Signals.byId(this.signals, hit);
        if (hovered != null && hovered.pos != null) {
            com.createstats.client.PeerIdentifyHighlight.show(hovered.pos,
                    (hovered.firing() ? Tokens.CRITICAL : hovered.series) & 0xFFFFFF);
        } else {
            com.createstats.client.PeerIdentifyHighlight.clear();
        }
        return !hit.isEmpty() || widget;
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (this.scroll.pressBar(barTrack(this.area), mx, my)) {
            return true;
        }

        if (this.limitField != null && this.limitField.editing()) {
            this.limitField.commit();
        }
        if (this.yMinField != null && this.yMinField.editing()) {
            this.yMinField.commit();
        }
        if (this.yMaxField != null && this.yMaxField.editing()) {
            this.yMaxField.commit();
        }

        if (this.editorMap != null && this.editorMap.count() >= 2
                && this.editorMap.plot().contains(mx, my)) {
            this.draggingLimit = true;
            return true;
        }

        if (!listArea(this.area).contains(mx, my)) {
            return false;
        }
        for (final Row row : this.rows) {
            if (row.isHeader()) {
                continue;
            }
            final Rect r = rowRect(this.area, row, this.scroll.value());
            if (!r.contains(mx, my)) {
                continue;
            }
            final Signals.Signal s = row.signal();
            final String label = s.firing() ? "FIRING" : s.armed() ? "ARMED" : "OFF";
            if (this.lastSurface != null
                    && pillRect(cardOf(r), this.lastSurface, label).contains(mx, my)) {
                toggleAlert(s);
            } else {
                this.ctx.focusedChannelId = s.id;
                this.editorTab = 0;
            }
            return true;
        }
        return false;
    }

    private void toggleAlert(Signals.Signal s) {
        final StatsDisplayPanelBE.DataChannel ch = s.channel;
        final boolean next = !ch.isAlertEnabled();
        if (next && ch.getAlertThreshold() == 0.0f) {
            ch.setAlertThreshold(Signals.suggest(
                    Signals.stats(s, WINDOW, nowTick()), ch.isAlertAbove()));
        }
        ch.setAlertEnabled(next);
        PacketDistributor.sendToServer(ConfigSyncPacket.alertConfig(
                this.ctx.panel.getBlockPos(), ch.getId(),
                ch.getAlertThreshold(), ch.isAlertAbove(), next));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.draggingLimit && this.editorMap != null) {
            final Rect plot = this.editorMap.plot();
            final float t = (float) ((plot.bottom() - my) / Math.max(1.0f, plot.h));
            final float clamped = Math.max(-0.05f, Math.min(1.05f, t));
            setThreshold(this.editorMap.lo()
                    + (this.editorMap.hi() - this.editorMap.lo()) * clamped);
            return true;
        }
        if (this.scroll.dragBar(barTrack(this.area), my)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.draggingLimit = false;
        this.scroll.endDrag();
        return super.mouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return listArea(this.area).contains(mx, my) && this.scroll.wheel(amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 265 || keyCode == 264) {
            nudge(keyCode == 265 ? 1 : -1);
            return true;
        }
        return false;
    }
}
