package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.statsmk3.page.SignalArt;
import com.createstats.statsmk3.page.Signals;
import com.createstats.statsmk3.YAxisRange;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.Motion;
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

@OnlyIn(Dist.CLIENT)
public final class LiveView extends StudioView {
    private static final float HEAD_H = 42.0f;
    private static final float RAIL_MIN_W = 118.0f;
    private static final float RAIL_MAX_W = 182.0f;
    private static final float ROW_H = 26.0f;
    private static final float ROW_H_PLAIN = 19.0f;
    private static final float GROUP_H = 17.0f;
    private static final float AXIS_H = 15.0f;
    private static final float TILES_H = 46.0f;
    private static final float FILTER_H = 19.0f;

    private static final int FILTER_FROM = 8;

    private static final int MAX_LANES = 3;

    private static final float RAIL_SPARK_W = 150.0f;

    public record Board(List<Signals.Signal> signals, String focusId, List<String> pins,
                        String hoverId, int windowIndex, Signals.By by, float heroValue,
                        Scroll scroll, Map<String, Float> reveal, double mouseX, double mouseY,
                        long nowTick, String query, Map<String, YAxisRange> yRanges) {
        public Board {
            yRanges = yRanges == null ? Map.of() : yRanges;
        }

        public Board(List<Signals.Signal> signals, String focusId, List<String> pins,
                     String hoverId, int windowIndex, Signals.By by, float heroValue,
                     Scroll scroll, Map<String, Float> reveal, double mouseX, double mouseY,
                     long nowTick, String query) {
            this(signals, focusId, pins, hoverId, windowIndex, by, heroValue, scroll, reveal,
                    mouseX, mouseY, nowTick, query, Map.of());
        }

        public int windowSeconds() {
            return Signals.WINDOWS[Math.max(0, Math.min(this.windowIndex,
                    Signals.WINDOWS.length - 1))];
        }

        public Signals.Signal focus() {
            final Signals.Signal s = Signals.byId(this.signals, this.focusId);
            return s != null ? s : (this.signals.isEmpty() ? null : this.signals.get(0));
        }

        boolean filterShowing() {
            return this.signals.size() >= FILTER_FROM || !this.query.isEmpty();
        }

        float revealOf(int slot, Signals.Signal s) {
            return Motion.reducedMotion() ? 1.0f
                    : this.reveal.getOrDefault(slot + ":" + s.id, 0.0f);
        }

        YAxisRange yRange(String channelId) {
            return this.yRanges.getOrDefault(channelId, YAxisRange.auto());
        }
    }

    record Row(Signals.Signal signal, String title, float y, float h) {
        boolean isHeader() {
            return this.signal == null;
        }
    }

    private final Scroll scroll = new Scroll();
    private final Spring hero = Spring.ui(0.0f);

    private final Map<String, Float> reveal = new HashMap<>();

    private Segmented groupControl;
    private TextField filter;
    private Segmented yModeControl;
    private TextField yMinField;
    private TextField yMaxField;

    private int windowIndex = Signals.WINDOWS.length - 1;
    private Signals.By by = Signals.By.MACHINE;

    private String focusId = "";
    private final List<String> pins = new ArrayList<>();
    private String hoverId = "";
    private String heroId = "";

    private double mouseX = -1.0;
    private double mouseY = -1.0;

    private List<Signals.Signal> signals = new ArrayList<>();

    public LiveView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "live";
    }

    @Override
    public String label() {
        return "Live";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public boolean capturesTyping() {
        return this.filter != null && this.filter.editing()
                || this.yMinField != null && this.yMinField.editing()
                || this.yMaxField != null && this.yMaxField.editing();
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        final float[] pts = {cx - 6.5f, cy + 2.5f, cx - 2.5f, cy - 2.5f,
                cx + 0.5f, cy + 1.0f, cx + 3.0f, cy - 4.0f, cx + 6.5f, cy + 3.0f};
        Shapes.polyline(c, pts, 5, 1.3f, color);
    }

    static float railWidth(Rect area) {
        return Math.max(RAIL_MIN_W, Math.min(RAIL_MAX_W, area.w * 0.30f));
    }

    static Rect railArea(Rect area) {
        return new Rect(area.x, area.y + HEAD_H, railWidth(area),
                Math.max(0.0f, area.h - HEAD_H));
    }

    static Rect bodyArea(Rect area) {
        final float railW = railWidth(area);
        return new Rect(area.x + railW + Tokens.S4, area.y + HEAD_H,
                Math.max(0.0f, area.w - railW - Tokens.S4), Math.max(0.0f, area.h - HEAD_H));
    }

    static Rect listArea(Rect area, Board b) {
        final Rect rail = railArea(area);
        final float top = rail.y + (b.filterShowing() ? FILTER_H + Tokens.S1 : 0.0f);
        return new Rect(rail.x, top, rail.w, Math.max(0.0f, rail.bottom() - top));
    }

    public static Rect windowBounds(Rect area) {
        final float w = Math.min(156.0f, Math.max(112.0f, bodyArea(area).w * 0.4f));
        return new Rect(area.right() - w, area.y, w, 21.0f);
    }

    public static Rect groupBounds(Rect area) {
        return new Rect(area.x, area.y + 19.0f, railWidth(area), 20.0f);
    }

    public static Rect yModeBounds(Rect area) {
        final Rect body = bodyArea(area);
        return new Rect(body.x + 36.0f, area.y + 20.0f,
                Math.min(92.0f, body.w * 0.34f), 19.0f);
    }

    public static Rect yMinBounds(Rect area) {
        final Rect mode = yModeBounds(area);
        return new Rect(mode.right() + 5.0f, mode.y, 70.0f, mode.h);
    }

    public static Rect yMaxBounds(Rect area) {
        final Rect min = yMinBounds(area);
        return new Rect(min.right() + 5.0f, min.y, 70.0f, min.h);
    }

    public static Rect filterBounds(Rect area) {
        final Rect rail = railArea(area);
        return new Rect(rail.x, rail.y, rail.w - 15.0f, FILTER_H);
    }

    private static Rect clearBounds(Rect area) {
        final Rect rail = railArea(area);
        return new Rect(rail.right() - 14.0f, rail.y, 14.0f, FILTER_H);
    }

    private static float heroHeight(Rect area) {
        return bodyArea(area).h < 150.0f ? 34.0f : 46.0f;
    }

    static boolean tilesShowing(Rect area) {
        final Rect body = bodyArea(area);
        return body.h - heroHeight(area) - AXIS_H - Tokens.S3 * 2.0f - TILES_H
                >= SignalArt.LANE_MIN_H;
    }

    static Rect chartArea(Rect area) {
        final Rect body = bodyArea(area);
        final float top = body.y + heroHeight(area) + Tokens.S2;
        final float bottom = body.bottom() - AXIS_H
                - (tilesShowing(area) ? TILES_H + Tokens.S3 : 0.0f);
        return new Rect(body.x, top, body.w, Math.max(0.0f, bottom - top));
    }

    static int maxLanes(Rect area) {
        return Math.max(1, Math.min(MAX_LANES, (int) Math.floor(chartArea(area).h / 78.0f)));
    }

    static List<Signals.Signal> laneSignals(Rect area, Board b) {
        final List<Signals.Signal> out = new ArrayList<>();
        final Signals.Signal focus = b.focus();
        if (focus != null) {
            out.add(focus);
        }
        for (final String id : b.pins()) {
            final Signals.Signal s = Signals.byId(b.signals(), id);
            if (s != null && !s.id.equals(b.focusId()) && out.size() < maxLanes(area)) {
                out.add(s);
            }
        }
        return out;
    }

    static List<Row> rowsOf(Rect area, Board b) {
        final List<Row> out = new ArrayList<>();
        final float rowH = railWidth(area) >= RAIL_SPARK_W ? ROW_H : ROW_H_PLAIN;
        float y = 0.0f;
        for (final Signals.Group g : Signals.grouped(b.signals(), b.by())) {
            out.add(new Row(null, g.title, y, GROUP_H));
            y += GROUP_H;
            for (final Signals.Signal s : g.signals) {
                out.add(new Row(s, s.name, y, rowH));
                y += rowH;
            }
            y += Tokens.S1;
        }
        return out;
    }

    static float contentHeight(List<Row> rows) {
        float y = 0.0f;
        for (final Row r : rows) {
            y = Math.max(y, r.y() + r.h());
        }
        return y;
    }

    private static Rect rectOf(Row row, Rect list, float scroll) {
        return new Rect(list.x, list.y - scroll + row.y(), list.w, row.h());
    }

    private static Rect pinRect(Rect card) {
        return new Rect(card.right() - 15.0f, card.bottom() - 12.0f, 13.0f, 11.0f);
    }

    private static boolean pinnable(Rect area) {
        return maxLanes(area) > 1 && railWidth(area) >= RAIL_SPARK_W;
    }

    static Rect barTrack(Rect area, Board b) {
        final Rect list = listArea(area, b);
        return new Rect(list.right() - 3.0f, list.y, 3.0f, list.h);
    }

    @Override
    protected void build() {
        this.widgets.add(new Segmented(Signals.WINDOW_LABELS, () -> this.windowIndex,
                i -> this.windowIndex = i)
                .textScale(Text.MICRO).setBounds(windowBounds(this.area)));

        this.groupControl = new Segmented(new String[]{"Machine", "Kind"},
                () -> this.by == Signals.By.MACHINE ? 0 : 1,
                i -> this.by = i == 0 ? Signals.By.MACHINE : Signals.By.KIND).textScale(Text.MICRO);
        this.groupControl.setBounds(groupBounds(this.area));
        this.widgets.add(this.groupControl);

        this.filter = new TextField(s -> {
        }).placeholder("Filter").textScale(Text.MICRO);
        this.filter.setBounds(filterBounds(this.area));
        this.widgets.add(this.filter);

        this.yModeControl = new Segmented(new String[]{"Auto", "Fixed"},
                () -> focusedRange().isFixed() ? 1 : 0, this::setYAxisMode).textScale(Text.MICRO);
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
        return new Board(this.signals, this.focusId, this.pins, this.hoverId, this.windowIndex,
                this.by, this.hero.value(), this.scroll, this.reveal,
                this.mouseX, this.mouseY, nowTick(),
                this.filter == null ? "" : this.filter.text(), this.ctx.settings().liveYRanges());
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);

        this.signals = Signals.filter(Signals.of(this.ctx.panel),
                this.filter == null ? "" : this.filter.text());
        final Board b = board();
        if (this.filter != null) {
            this.filter.setVisible(b.filterShowing());
        }
        if (this.groupControl != null) {
            this.groupControl.setVisible(this.signals.size() > 1);
        }

        if (Signals.byId(this.signals, this.focusId) == null) {
            final Signals.Signal fallback = Signals.byId(this.signals, this.ctx.focusedChannelId);
            this.focusId = fallback != null ? fallback.id
                    : (this.signals.isEmpty() ? "" : this.signals.get(0).id);
        }
        this.pins.removeIf(id -> Signals.byId(this.signals, id) == null || id.equals(this.focusId));
        while (this.pins.size() > maxLanes(this.area) - 1) {
            this.pins.remove(this.pins.size() - 1);
        }

        this.scroll.setMetrics(contentHeight(rowsOf(this.area, board())),
                listArea(this.area, board()).h);
        stepReveal(dt, laneSignals(this.area, board()));

        final Signals.Signal focus = board().focus();
        if (focus != null) {
            // jump on channel change, only ease value changes. 1847 SU easing to 64 RPM is nonsense

            if (!focus.id.equals(this.heroId)) {
                this.heroId = focus.id;
                this.hero.jump(focus.value());
            }
            this.hero.set(focus.value()).step(dt);
        }
        syncYAxisControls();
    }

    private YAxisRange focusedRange() {
        final Signals.Signal focus = Signals.byId(this.signals, this.focusId);
        return focus == null ? YAxisRange.auto() : this.ctx.settings().liveYRange(focus.id);
    }

    private void syncYAxisControls() {
        if (this.yModeControl == null) {
            return;
        }
        final Signals.Signal focus = Signals.byId(this.signals, this.focusId);
        final YAxisRange range = focusedRange();
        this.yModeControl.setVisible(focus != null);
        this.yMinField.setVisible(focus != null && range.isFixed());
        this.yMaxField.setVisible(focus != null && range.isFixed());
        if (range.isFixed()) {
            this.yMinField.setText(Float.toString(range.min()));
            this.yMaxField.setText(Float.toString(range.max()));
        }
    }

    private void setYAxisMode(int mode) {
        final Signals.Signal focus = Signals.byId(this.signals, this.focusId);
        if (focus == null) {
            return;
        }
        final Signals.Stats stats = Signals.stats(focus, board().windowSeconds(), nowTick());
        final YAxisRange range = mode == 0 ? YAxisRange.auto()
                : stats.usable() ? YAxisRange.rounded(stats.lo, stats.hi)
                        : YAxisRange.fixed(0.0f, 1.0f);
        this.ctx.settings().setLiveYRange(focus.id, range);
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
        final Signals.Signal focus = Signals.byId(this.signals, this.focusId);
        final YAxisRange current = focusedRange();
        if (focus == null || !current.isFixed()) {
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
            this.ctx.settings().setLiveYRange(focus.id, next);
            this.ctx.commit();
        } catch (final NumberFormatException e) {
        }
        syncYAxisControls();
    }

    private void stepReveal(float dt, List<Signals.Signal> lanes) {
        for (int i = 0; i < lanes.size(); i++) {
            this.reveal.merge(i + ":" + lanes.get(i).id,
                    Math.min(1.0f, dt / Motion.SERIES_REVEAL), (a, b) -> Math.min(1.0f, a + b));
        }

        this.reveal.keySet().removeIf(k -> {
            for (int i = 0; i < lanes.size(); i++) {
                if (k.equals(i + ":" + lanes.get(i).id)) {
                    return false;
                }
            }
            return true;
        });
    }

    private long nowTick() {
        try {
            return this.ctx.panel == null || this.ctx.panel.getLevel() == null
                    ? 0L : this.ctx.panel.getLevel().getGameTime();
        } catch (final RuntimeException e) {
            return 0L;
        }
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, board());
        if (!this.signals.isEmpty()) {
            final Rect mode = yModeBounds(this.area);
            c.textRightV("Y scale", mode.x - 5.0f, mode.centerY(), Tokens.INK_3, Text.MICRO);
        }
    }

    public static void paint(PanelSurface c, Rect area, Board b) {
        c.text("Live", area.x, area.y, Tokens.INK, Text.SUB);
        if (b.signals().isEmpty()) {
            final boolean filtered = !b.query().isEmpty();
            SignalArt.empty(c, new Rect(area.x, area.y + HEAD_H, area.w, area.h - HEAD_H),
                    filtered ? "Nothing matches \"" + b.query() + "\""
                            : "This panel is not reading anything yet",
                    filtered ? "Clear the filter to see every signal again."
                            : "Link a machine on the Sources step and its readings appear here.");
            return;
        }
        drawRail(c, area, b);
        drawBody(c, area, b);
    }

    private static void drawRail(PanelSurface c, Rect area, Board b) {
        if (b.filterShowing() && !b.query().isEmpty()) {
            final Rect clear = clearBounds(area);
            c.textCenterV("x", clear.centerX(), clear.centerY(), Tokens.INK_3, Text.MICRO);
        }
        final Rect list = listArea(area, b);
        if (list.isEmpty()) {
            return;
        }
        c.clip(list);
        for (final Row row : rowsOf(area, b)) {
            final Rect r = rectOf(row, list, b.scroll().value());
            if (r.bottom() < list.y - 2.0f || r.y > list.bottom() + 2.0f) {
                continue;
            }
            if (row.isHeader()) {
                drawGroupHeading(c, r, row.title());
            } else {
                drawSignalRow(c, area, b, r, row.signal());
            }
        }
        c.unclip();
        b.scroll().drawBar(c, barTrack(area, b));
    }

    private static void drawGroupHeading(PanelSurface c, Rect r, String title) {
        c.text(Text.track(Text.truncate(c, title, r.w - 10.0f, Text.MICRO)),
                r.x + 1.0f, r.bottom() - Text.lineHeight(Text.MICRO) - 3.0f,
                Tokens.INK_3, Text.MICRO);
        c.hairlineH(r.x + 1.0f, r.bottom() - 1.0f, r.w - 8.0f, Tokens.alpha(Tokens.LINE, 0.7f));
    }

    private static void drawSignalRow(PanelSurface c, Rect area, Board b, Rect r,
                                      Signals.Signal s) {
        final boolean isFocus = s.id.equals(b.focusId());
        final boolean pinned = b.pins().contains(s.id) && !isFocus;
        final boolean hovered = s.id.equals(b.hoverId());
        final Rect card = new Rect(r.x, r.y + 1.0f, r.w - 6.0f, r.h - 2.0f);

        if (isFocus || pinned || hovered) {
            c.roundedRect(card, Tokens.R_CHIP,
                    Tokens.alpha(Tokens.RAISED, isFocus ? 1.0f : hovered ? 0.65f : 0.4f));
        }
        if (isFocus || pinned) {
            Shapes.roundedRect(c, new Rect(card.x, card.y + 3.0f, 2.0f, card.h - 6.0f), 1.0f,
                    s.series);
        }

        final float left = card.x + 7.0f;
        final boolean wide = card.w >= RAIL_SPARK_W - 12.0f;
        final float valueRight = card.right() - 5.0f;
        final float nameY = wide ? card.y + 3.0f
                : card.centerY() - Text.lineHeight(Text.MICRO) * 0.5f;

        final String value = Text.compact(s.value());
        final float valueW = c.textWidth(value, Text.MICRO);
        c.text(Text.truncate(c, s.name, card.w - 18.0f - valueW, Text.MICRO), left, nameY,
                isFocus ? Tokens.INK : Tokens.INK_2, Text.MICRO);
        c.textRight(value, valueRight, nameY, isFocus ? Tokens.INK : Tokens.INK_2, Text.MICRO);

        if (!wide) {
            return;
        }

        c.text(s.tag(), left, card.bottom() - Text.lineHeight(Text.MICRO) - 3.0f,
                Tokens.INK_3, Text.MICRO);
        final boolean showPin = pinnable(area) && (hovered || pinned) && !isFocus;
        final float sparkX = left + Math.max(20.0f, c.textWidth(s.tag(), Text.MICRO) + 5.0f);
        final float sparkRight = showPin ? pinRect(card).x - 4.0f : valueRight;
        final Rect spark = new Rect(sparkX, card.bottom() - 11.0f,
                Math.max(0.0f, sparkRight - sparkX), 9.0f);
        if (spark.w > 12.0f) {
            SignalArt.spark(c, spark, Signals.window(s, b.windowSeconds()),
                    Tokens.alpha(s.series, isFocus ? 1.0f : 0.62f),
                    s.armed(), s.threshold(), s.above(),
                    s.firing() ? Tokens.CRITICAL : Tokens.CAUTION);
        }

        if (s.firing()) {
            Shapes.circle(c, card.right() - 4.0f, card.y + 5.0f, 2.6f, Tokens.CRITICAL);
        }

        if (showPin) {
            final Rect dot = pinRect(card);
            c.card(dot, Tokens.R_CHIP,
                    Tokens.alpha(pinned ? s.series : Tokens.OVERLAY, pinned ? 0.35f : 0.9f),
                    Tokens.alpha(pinned ? s.series : Tokens.LINE_HI, 0.8f));
            c.textCenterV(pinned ? "-" : "+", dot.centerX(), dot.centerY(),
                    pinned ? s.series : Tokens.INK_2, Text.MICRO);
        }
    }

    private static void drawBody(PanelSurface c, Rect area, Board b) {
        final Signals.Signal focus = b.focus();
        final Rect body = bodyArea(area);
        if (focus == null || body.isEmpty()) {
            return;
        }
        final int seconds = b.windowSeconds();
        final Signals.Stats st = Signals.stats(focus, seconds, b.nowTick());
        drawHero(c, area, b, focus, st);

        final List<Signals.Signal> lanes = laneSignals(area, b);
        final Rect chart = chartArea(area);
        if (chart.isEmpty() || lanes.isEmpty()) {
            return;
        }
        final Rect[] cells = Layout.rows(chart, lanes.size(), Tokens.S2);
        final float heroArea = Math.max(1.0f, chart.w * chart.h);
        final boolean pointing = chart.contains(b.mouseX(), b.mouseY());
        int crossIndex = -1;
        float pointerT = -1.0f;

        for (int i = 0; i < lanes.size(); i++) {
            final Signals.Signal s = lanes.get(i);
            final Rect r = cells[i];
            final Signals.Stats lst = i == 0 ? st : Signals.stats(s, seconds, b.nowTick());
            final SignalArt.Lane lane = new SignalArt.Lane(s, Signals.window(s, seconds), lst,
                    s.series, s.armed(), s.threshold(), s.above(),
                    s.firing() ? Tokens.CRITICAL : Tokens.CAUTION, b.yRange(s.id));
            final float detail = ramp((r.w * r.h) / heroArea, 0.22f, 0.85f);
            final SignalArt.Mapped map = SignalArt.lane(c, r, lane, detail, b.revealOf(i, s));

            if (!pointing || map.count() < 2) {
                continue;
            }

            if (crossIndex < 0) {
                crossIndex = map.indexAt(b.mouseX());
                pointerT = crossIndex / (float) Math.max(1, map.count() - 1);
            }
            final int at = Math.min(crossIndex, map.count() - 1);
            Shapes.rect(c, Math.round(map.xFor(at)), r.y + 4.0f, 1.0f, r.h - 8.0f,
                    Tokens.alpha(Tokens.LINE_HI, 0.9f));
            SignalArt.crosshair(c, map, lane, at);
        }

        SignalArt.timeAxis(c, new Rect(chart.x, chart.bottom() + 2.0f,
                        chart.w - (chart.w > 210.0f ? SignalArt.AXIS_W : 0.0f), AXIS_H - 3.0f),
                st.seconds, pointerT);

        if (tilesShowing(area)) {
            drawTiles(c, area, focus, st);
        }
    }

    private static void drawHero(PanelSurface c, Rect area, Board b, Signals.Signal s,
                                 Signals.Stats st) {
        final Rect body = bodyArea(area);
        final Rect r = new Rect(body.x, body.y, body.w, heroHeight(area));
        final boolean stale = st.regime == Signals.Regime.STALE;

        c.text(Text.truncate(c, s.full(), r.w - 120.0f, Text.MICRO), r.x, r.y - 12.0f,
                Tokens.INK_3, Text.MICRO);

        final float unitW = s.unit.isEmpty() ? 0.0f : c.textWidth(s.unit, Text.META) + 5.0f;
        final Text.Fitted fit = Text.fitValue(c, b.heroValue(), r.w * 0.52f - unitW, r.h,
                Text.HERO, Text.SUB);
        c.text(fit.text(), r.x, r.y, stale ? Tokens.INK_2 : Tokens.INK, fit.scale());
        if (!s.unit.isEmpty()) {
            c.text(s.unit, r.x + c.textWidth(fit.text(), fit.scale()) + 5.0f,
                    r.y + Text.lineHeight(fit.scale()) - Text.lineHeight(Text.META) - 1.0f,
                    Tokens.INK_3, Text.META);
        }

        final float rightX = r.x + r.w * 0.56f;
        SignalArt.regime(c, rightX, r.y + 8.0f, st, s.unit);
        if (st.usable() && r.h > 40.0f) {
            c.textV(st.count + " samples over " + Signals.ago(st.seconds),
                    rightX, r.y + 24.0f, Tokens.INK_3, Text.MICRO);
        }

        if (s.armed()) {
            final String label = s.firing() ? "FIRING" : "ARMED";
            final int color = s.firing() ? Tokens.CRITICAL : Tokens.GOOD;
            final float w = SignalArt.pillWidth(c, label, 15.0f);
            SignalArt.pill(c, new Rect(r.right() - w, r.y + 2.0f, w, 15.0f), label, color, 1.0f);
        }
    }

    // median and p80 band say what's normal
    private static void drawTiles(PanelSurface c, Rect area, Signals.Signal s, Signals.Stats st) {
        final Rect body = bodyArea(area);
        final Rect[] cells = Layout.columns(
                new Rect(body.x, body.bottom() - TILES_H, body.w, TILES_H), 4, Tokens.S2);
        if (!st.usable()) {
            SignalArt.tile(c, cells[0], "Typical", "--", "no samples yet", Tokens.INK_3);
            return;
        }
        SignalArt.tile(c, cells[0], "Typical", Signals.withUnit(st.median, s.unit),
                "middle reading", Tokens.INK);
        SignalArt.tile(c, cells[1], "Usual swing", "+- " + Text.compact(st.spread() * 0.5f),
                "8 samples in 10", Tokens.INK);
        SignalArt.tile(c, cells[2], "Low / high",
                Text.compact(st.lo) + " - " + Text.compact(st.hi),
                "over " + Signals.ago(st.seconds), Tokens.INK);

        if (s.armed()) {
            final float distance = s.above() ? s.threshold() - st.latest : st.latest - s.threshold();
            final boolean tripped = distance <= 0.0f;
            SignalArt.tile(c, cells[3], "To alarm",
                    (tripped ? "+" : "") + Text.compact(Math.abs(distance)),
                    tripped ? "past the limit"
                            : (s.above() ? "fires over " : "fires under ")
                                    + Text.compact(s.threshold()),
                    tripped ? Tokens.CRITICAL
                            : Tokens.statusFor(Signals.proximity(s, st)));
            return;
        }
        // neutral ink, rising energy and rising stress mean opposite things

        SignalArt.tile(c, cells[3], "Change", Text.delta(st.drift, Math.abs(st.drift) >= 10 ? 0 : 2),
                "across " + Signals.ago(st.seconds), Tokens.INK);
    }

    private static float ramp(float v, float lo, float hi) {
        if (hi <= lo) {
            return v >= hi ? 1.0f : 0.0f;
        }
        final float t = (v - lo) / (hi - lo);
        return t < 0.0f ? 0.0f : Math.min(t, 1.0f);
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
        final boolean widget = super.mouseMoved(mx, my);

        String hit = "";
        final Board b = board();
        final Rect list = listArea(this.area, b);
        if (list.contains(mx, my)) {
            for (final Row row : rowsOf(this.area, b)) {
                if (!row.isHeader() && rectOf(row, list, b.scroll().value()).contains(mx, my)) {
                    hit = row.signal().id;
                    break;
                }
            }
        }
        this.hoverId = hit;

        final Signals.Signal hovered = Signals.byId(this.signals, hit);
        if (hovered != null && hovered.pos != null) {
            com.createstats.client.PeerIdentifyHighlight.show(hovered.pos,
                    hovered.series & 0xFFFFFF);
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
        final Board b = board();
        if (this.scroll.pressBar(barTrack(this.area, b), mx, my)) {
            return true;
        }

        if (b.filterShowing() && this.filter != null) {
            if (!this.filter.text().isEmpty() && clearBounds(this.area).contains(mx, my)) {
                this.filter.setText("");
                return true;
            }
            if (this.filter.editing()) {
                this.filter.commit();
            }
        }

        final Rect list = listArea(this.area, b);
        if (!list.contains(mx, my)) {
            return false;
        }
        for (final Row row : rowsOf(this.area, b)) {
            if (row.isHeader()) {
                continue;
            }
            final Rect r = rectOf(row, list, b.scroll().value());
            if (!r.contains(mx, my)) {
                continue;
            }
            final Signals.Signal s = row.signal();
            final Rect card = new Rect(r.x, r.y + 1.0f, r.w - 6.0f, r.h - 2.0f);
            if (pinnable(this.area) && !s.id.equals(this.focusId)
                    && pinRect(card).contains(mx, my)) {
                togglePin(s);
            } else {
                setFocus(s);
            }
            return true;
        }
        return false;
    }

    private void setFocus(Signals.Signal s) {
        this.focusId = s.id;
        this.pins.remove(s.id);

        this.ctx.focusedChannelId = s.id;
    }

    private void togglePin(Signals.Signal s) {
        if (this.pins.remove(s.id)) {
            return;
        }
        this.pins.add(s.id);
        while (this.pins.size() > maxLanes(this.area) - 1) {
            this.pins.remove(0);
        }
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return railArea(this.area).contains(mx, my) && this.scroll.wheel(amount);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.scroll.dragBar(barTrack(this.area, board()), my)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.scroll.endDrag();
        return super.mouseUp(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 264 || keyCode == 265) {
            return step(keyCode == 264 ? 1 : -1);
        }
        return false;
    }

    private boolean step(int direction) {
        final List<Row> rows = rowsOf(this.area, board());
        final List<Signals.Signal> flat = new ArrayList<>();
        for (final Row row : rows) {
            if (!row.isHeader()) {
                flat.add(row.signal());
            }
        }
        if (flat.isEmpty()) {
            return false;
        }
        int at = 0;
        for (int i = 0; i < flat.size(); i++) {
            if (flat.get(i).id.equals(this.focusId)) {
                at = i;
                break;
            }
        }
        final int n = flat.size();
        setFocus(flat.get(((at + direction) % n + n) % n));
        revealFocusedRow(rows);
        return true;
    }

    private void revealFocusedRow(List<Row> rows) {
        final float viewport = listArea(this.area, board()).h;
        for (final Row row : rows) {
            if (row.isHeader() || !row.signal().id.equals(this.focusId)) {
                continue;
            }
            if (row.y() < this.scroll.value()) {
                this.scroll.jumpTo(row.y() - GROUP_H);
            } else if (row.y() + row.h() > this.scroll.value() + viewport) {
                this.scroll.jumpTo(row.y() + row.h() - viewport);
            }
            return;
        }
    }
}
