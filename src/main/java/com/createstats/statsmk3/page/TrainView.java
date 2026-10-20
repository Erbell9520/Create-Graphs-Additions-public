package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.createstats.client.MapPins;
import com.createstats.client.TrainMapScreen;
import com.createstats.network.TrainMapGroupPacket;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.Layout;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;
import com.createstats.train.map.MapLayout;
import com.createstats.train.map.ClientRailMap;
import com.createstats.train.map.MapView;
import com.createstats.train.map.RailHistory;
import com.createstats.train.map.RailMap;
import com.createstats.train.map.RailMapArt;
import com.createstats.train.map.RailPerformance;
import com.createstats.train.map.RailPerformanceArt;
import com.createstats.train.map.TrainConsistArt;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.map.TrainGraphView;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailFilter;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrainView extends StudioView {
    private static final String[] MODES = TrainPageLayout.MODES;
    private static final int NETWORK = 0;
    private static final int DIAGRAM = 1;
    private static final int BOARD = 2;
    private static final int FLEET = 3;
    private static final int SERVICE = 4;
    private static final int ALERTS = 5;

    private static final int GLFW_ESCAPE = 256;
    private static final int GLFW_SLASH = 47;
    private static final int GLFW_F = 70;

    private final Scroll listScroll = new Scroll();
    private final Scroll boardScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private final Scroll serviceScroll = new Scroll();

    private final List<Rect> legendHits = new ArrayList<>();

    private int mode;
    private UUID selectedTrain;
    private UUID selectedStation;

    private int selectedService = -1;

    private static final String[] FLEET_SORTS = {"Attention", "Name", "Speed", "Service"};
    private int fleetSort;

    private RailMap map = RailMap.flat(RailSnapshot.EMPTY);
    private MapLayout layout = new MapLayout(this.map);
    private final MapView mapView = new MapView();
    private final TrainGraphView graph = new TrainGraphView();
    private int builtRoster = Integer.MIN_VALUE;
    private int builtFilter = Integer.MIN_VALUE;

    private float lastDelta = 1.0f / 60.0f;

    private double hoverX = Double.NaN;
    private double hoverY = Double.NaN;

    private TrainPageLayout.Density density = TrainPageLayout.density(Rect.EMPTY);

    private Rect kpiArea = Rect.EMPTY;
    private Rect controlArea = Rect.EMPTY;
    private Rect contentArea = Rect.EMPTY;
    private Rect listArea = Rect.EMPTY;
    private Rect detailArea = Rect.EMPTY;
    private Rect plotArea = Rect.EMPTY;
    private Rect inspectorArea = Rect.EMPTY;
    private Rect overlayArea = Rect.EMPTY;

    private TrainGraphArt.Frame graphFrame = TrainGraphArt.Frame.EMPTY;

    private enum Action {
        SELECT_TRAIN, SELECT_STATION, SELECT_LINE, BACK, TOGGLE_RULE, DROP_RULE, ADD_STATION_RULE,
        ADD_TRAIN_RULE, ADD_GROUP, DROP_GROUP, SHOW_ALL, FLAG_DOCKED, FLAG_PARKED, FLAG_PROBLEMS, CLOSE_OVERLAY,
        OPEN_FILTERS, OPEN_VIEW, FULL_MAP, MAP_MODE, KPI, SELECT_SERVICE, GRAPH_LINE, GRAPH_SPAN,
        GRAPH_SPACING, OPEN_SERVICE, MAP_FIT, MAP_ME, FOLLOW, OPEN_BOARD, FLEET_SORT,
        GRAPH_PLANNED, GRAPH_MEETS, GRAPH_LIVE, GRAPH_HOLDS, GRAPH_GRID, VIEW_TOGGLE,
        FACE_BOARD, FACE_DIAGRAM, OPEN_FACE, FACE_MODE, FACE_FOCUS, FACE_FLAG, FACE_TARGET,
        FACE_ADD_STATION, FACE_SET
    }

    private record Hit(Rect rect, Action action, Object payload) {
    }

    private record KpiTarget(int mode, UUID train) {
    }

    private final List<Hit> hits = new ArrayList<>();

    private int overlay;
    private static final int OVERLAY_NONE = 0;
    private static final int OVERLAY_FILTERS = 1;
    private static final int OVERLAY_VIEW = 2;

    private static final int OVERLAY_FACE = 3;

    private final TextField patternField = new TextField(this::commitPattern);

    private final TextField searchField = new TextField(this::commitSearch);
    private Rect searchArea = Rect.EMPTY;

    private int patternTarget;
    private static final int PATTERN_STATION = 0;
    private static final int PATTERN_TRAIN = 1;
    private static final int PATTERN_GROUP = 2;

    private Rect faceButton = Rect.EMPTY;
    private String faceButtonStation = "";

    public TrainView(StudioContext ctx) {
        super(ctx);
        this.patternField.textScale(Text.MICRO).placeholder("Name or Name *");
        this.patternField.setVisible(false);
        this.searchField.placeholder("Search");
        this.searchField.setVisible(false);
    }

    private float u(float v) {
        return v * this.density.step();
    }

    private float primary() {
        return this.density.primary();
    }

    private float secondary() {
        return this.density.secondary();
    }

    private float rowH() {
        return this.density.rowH();
    }

    private float listRowH() {
        return this.density.listRowH();
    }

    @Override
    public String id() {
        return "trains";
    }

    @Override
    public String label() {
        return "Trains";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.roundedRectOutline(c, new Rect(cx - 5.0f, cy - 6.0f, 10.0f, 9.0f), 2.0f, 1.2f, color);
        Shapes.rect(c, cx - 5.0f, cy - 2.0f, 10.0f, 1.2f, color);
        Shapes.circle(c, cx - 3.0f, cy + 4.5f, 1.5f, color);
        Shapes.circle(c, cx + 3.0f, cy + 4.5f, 1.5f, color);
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public boolean capturesTyping() {
        return this.patternField.editing() || this.searchField.editing();
    }

    @Override
    protected void build() {
        final TrainPageLayout.Frame frame = TrainPageLayout.of(this.area);
        this.density = frame.density();
        this.kpiArea = frame.kpi();
        this.controlArea = frame.control();
        this.contentArea = frame.content();
        this.listArea = frame.list();
        this.detailArea = frame.detail();

        final Segmented modes = new Segmented(MODES, () -> this.mode, this::switchMode)
                .textScale(this.density.secondary());
        modes.setBounds(frame.modes());
        this.searchField.textScale(this.density.secondary());
        this.widgets.add(modes);
        this.widgets.add(this.patternField);
        this.widgets.add(this.searchField);
    }

    private void switchMode(int next) {
        this.mode = next;
        this.listScroll.jumpTo(0.0f);
        this.boardScroll.jumpTo(0.0f);
        this.serviceScroll.jumpTo(0.0f);
        this.overlay = OVERLAY_NONE;
        this.patternField.setVisible(false);
    }

    private String query() {
        return this.searchField.text().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String name) {
        final String q = query();
        return q.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(q));
    }

    private void commitSearch(String text) {
        if (this.mode != NETWORK || text == null || text.isBlank()) {
            return;
        }
        final List<Integer> found = this.mapView.searchHits(this.map);
        if (!found.isEmpty()) {
            this.mapView.selectNode(found.get(0));
            this.mapView.centreOn(this.layout, found.get(0));
        }
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.lastDelta = Math.max(1.0f / 240.0f, Math.min(dt, 0.05f));
        this.listScroll.update(dt);
        this.boardScroll.update(dt);
        this.panelScroll.update(dt);
        this.serviceScroll.update(dt);

        this.ctx.panel.markTrainMapActive();
        ClientRailNetwork.requestUpdates();

        rebuildIfChanged();

        ClientRailMap.sampled();

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.mapView.setPlayer((float) mc.player.getX(), (float) mc.player.getZ(),
                    mc.player.getYRot());
        }
        this.mapView.update(dt, this.map, this.layout);
    }

    private void rebuildIfChanged() {
        final RailSnapshot snap = RailFilter.get().apply(ClientRailNetwork.get());
        final int roster = RailMap.rosterSignature(snap);
        final int filter = RailFilter.get().revision();
        if (roster == this.builtRoster && filter == this.builtFilter) {
            this.map.refresh(snap);
            return;
        }
        this.builtRoster = roster;
        this.builtFilter = filter;
        if (this.mapView.grabbedNode() >= 0) {
            this.mapView.cancelGesture();
        }

        this.map = RailMap.build(snap, this.ctx.panel.getTrainMapGroups(), true,
                this.mapView.expandedGroups());
        this.layout = new MapLayout(this.map);

        this.layout.restorePins(MapPins.all());
        offerFilterRules();
    }

    private void offerFilterRules() {
        final RailFilter filter = RailFilter.get();
        for (final RailMap.Node node : this.map.groups()) {
            filter.offerStation(node.name + " *");
        }
        for (final RailMap.Line line : this.map.lines()) {
            if (!line.title.isEmpty()) {
                filter.offerTrain(line.title + " *");
            }
        }
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final RailSnapshot snap = this.map.snapshot();
        this.hits.clear();

        c.text("Train network", this.area.x, this.area.y, Tokens.INK, Text.SUB);

        if (snap.isEmpty() && !RailFilter.get().active()) {
            drawLiveBadge(c);
            drawWaiting(c);
            return;
        }

        final TrainIntel.Summary summary = TrainIntel.summarise(snap);
        c.text(summary.stations + " stations  .  " + summary.trains + " trains  .  "
                        + summary.lines + " lines", this.area.x, this.area.y + 20.0f,
                Tokens.INK_3, Text.META);
        drawLiveBadge(c);
        drawSearch(c, drawHeaderButtons(c));

        drawKpis(c, summary);
        this.faceButton = Rect.EMPTY;

        switch (this.mode) {
            case DIAGRAM -> drawDiagramMode(c, snap);
            case BOARD -> drawBoardMode(c, snap);
            case FLEET -> drawFleetMode(c, snap);
            case SERVICE -> drawServiceMode(c);
            case ALERTS -> drawAlertsMode(c, snap);
            default -> drawNetworkMode(c, snap);
        }

        if (this.overlay == OVERLAY_FILTERS) {
            drawFilterOverlay(c);
        } else if (this.overlay == OVERLAY_VIEW) {
            drawViewOverlay(c);
        } else if (this.overlay == OVERLAY_FACE) {
            drawFaceOverlay(c);
        } else {
            this.patternField.setVisible(false);
        }
    }

    private void drawLiveBadge(PanelSurface c) {
        final boolean live = ClientRailNetwork.isLive();
        final String label = live ? "LIVE" : "STALE";
        final int color = live ? Tokens.GOOD : Tokens.CAUTION;
        final float w = c.textWidth(label, Text.MICRO) + 22.0f;
        final Rect pill = new Rect(this.area.right() - w, this.area.y - 2.0f, w, 18.0f);
        c.card(pill, 9.0f, Tokens.alpha(color, 0.10f), Tokens.alpha(color, 0.42f));
        Shapes.circle(c, pill.x + 10.0f, pill.centerY(), 3.0f, color);
        c.textV(label, pill.x + 17.0f, pill.centerY(), color, Text.MICRO);
    }

    private float drawHeaderButtons(PanelSurface c) {
        final RailFilter filter = RailFilter.get();
        float x = this.controlArea.right();

        if (this.mode == NETWORK || this.mode == DIAGRAM) {
            x = headerChip(c, x, "View", this.overlay == OVERLAY_VIEW, Action.OPEN_VIEW,
                    Tokens.INK_2) - 6.0f;
        }

        final boolean onFace = this.ctx.settings().faceMode.isTrain();
        x = headerChip(c, x, "Face", this.overlay == OVERLAY_FACE || onFace, Action.OPEN_FACE,
                onFace ? Tokens.GOOD : Tokens.INK_2) - 6.0f;
        final int hidden = filter.hiddenCount();
        final String label = hidden > 0 ? "Filters " + hidden : "Filters";
        return headerChip(c, x, label, this.overlay == OVERLAY_FILTERS || hidden > 0,
                Action.OPEN_FILTERS, hidden > 0 ? Tokens.CAUTION : Tokens.INK_2);
    }

    private void drawSearch(PanelSurface c, float chipsLeft) {
        final float left = this.controlArea.x
                + Math.min(this.controlArea.w, TrainPageLayout.modesWidth(this.controlArea,
                        this.density)) + Tokens.S2;
        final float right = chipsLeft - Tokens.S2;
        if (right - left < 96.0f) {
            this.searchArea = Rect.EMPTY;
            this.searchField.setVisible(false);
            if (this.searchField.editing()) {
                this.searchField.cancel();
            }
            this.mapView.setSearch("");
            return;
        }
        this.searchArea = new Rect(left, this.controlArea.y + 2.0f,
                Math.min(240.0f * this.density.step(), right - left), this.controlArea.h - 4.0f);
        this.searchField.setBounds(this.searchArea);
        this.searchField.setVisible(true);

        this.mapView.setSearch(this.mode == NETWORK ? this.searchField.text() : "");
    }

    private float headerChip(PanelSurface c, float rightX, String label, boolean active,
                             Action action, int color) {
        final float w = c.textWidth(label, Text.MICRO) + 18.0f;
        final Rect r = new Rect(rightX - w, this.controlArea.y + 3.0f, w, 20.0f);
        c.card(r, Tokens.R_CHIP, active ? Tokens.alpha(color, 0.14f) : Tokens.SURFACE,
                active ? Tokens.alpha(color, 0.5f) : Tokens.LINE);
        c.textCenterV(label, r.centerX(), r.centerY(), active ? color : Tokens.INK_3, Text.MICRO);
        this.hits.add(new Hit(r, action, null));
        return r.x;
    }

    private void drawWaiting(PanelSurface c) {
        c.text("No train network data", this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);
        final Rect box = this.area.inset(0.0f, 56.0f, 0.0f, 0.0f).top(110.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenterV("Waiting for the railway", box.centerX(), box.centerY() - 12.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenterV("Build a Train Station, or link one to this panel",
                box.centerX(), box.centerY() + 8.0f, Tokens.INK_3, Text.META);
    }

    private void drawKpis(PanelSurface c, TrainIntel.Summary s) {
        if (this.kpiArea.h < 30.0f) {
            drawKpiStrip(c, s);
            return;
        }

        final int cells = TrainPageLayout.kpiColumns(this.kpiArea);
        final Rect[] r = Layout.columns(this.kpiArea, cells, Tokens.S2);
        int i = 0;
        kpi(c, r[i++], "Moving", String.valueOf(s.moving),
                s.moving > 0 ? Tokens.GOOD : Tokens.INK_2,
                s.averageSpeed > 0.0f ? String.format(Locale.ROOT, "%.1f m/s avg", s.averageSpeed)
                        : "", true, FLEET, null);
        // name the longest hold. "worst 114h 54m" and nothing else tells you nothing

        kpi(c, r[i++], "Held at signal", String.valueOf(s.held),
                s.held > 0 ? Tokens.CAUTION : Tokens.INK_2,
                holdFooter(s), false, ALERTS, s.longestHoldTrainId);
        kpi(c, r[i++], "Faults", String.valueOf(s.broken),
                s.broken > 0 ? Tokens.CRITICAL : Tokens.INK_2,
                s.broken > 0 ? "needs attention" : "all healthy", false, ALERTS, null);
        if (i < cells) {
            kpi(c, r[i++], "At platform", String.valueOf(s.docked), Tokens.INK_2,
                    s.docked > 0 ? "see the boards" : "", false, BOARD, null);
        }
        if (i < cells) {
            kpi(c, r[i], "Utilisation", Math.round(s.utilisation() * 100.0f) + "%",
                    Tokens.INK, "of the fleet running", false, DIAGRAM, null);
        }
    }

    private String holdFooter(TrainIntel.Summary s) {
        if (s.longestHold <= 0) {
            return "";
        }
        final String time = TrainIntel.duration(s.longestHold / TrainIntel.TICKS_PER_SECOND);
        return s.longestHoldTrain.isEmpty() ? "worst " + time : s.longestHoldTrain + "  .  " + time;
    }

    private void drawKpiStrip(PanelSurface c, TrainIntel.Summary s) {
        final Rect r = this.kpiArea;
        c.card(r, Tokens.R_CHIP, Tokens.SURFACE, Tokens.LINE);
        float x = r.x + Tokens.S3;

        x = strip(c, x, r, String.valueOf(s.moving), "moving",
                s.moving > 0 ? Tokens.GOOD : Tokens.INK_2, FLEET, null);
        x = strip(c, x, r, String.valueOf(s.held), "held",
                s.held > 0 ? Tokens.CAUTION : Tokens.INK_2, ALERTS, s.longestHoldTrainId);
        x = strip(c, x, r, String.valueOf(s.docked), "at platform", Tokens.INK_2, BOARD, null);
        strip(c, x, r, String.valueOf(s.broken), "faults",
                s.broken > 0 ? Tokens.CRITICAL : Tokens.INK_2, ALERTS, null);
        c.textRightV(Math.round(s.utilisation() * 100.0f) + "% running", r.right() - Tokens.S3,
                r.centerY(), Tokens.INK_3, Text.MICRO);
    }

    private float strip(PanelSurface c, float x, Rect band, String value, String label, int color,
                        int target, UUID select) {
        final float cy = band.centerY();
        c.textV(value, x, cy, color, Text.META);
        final float vw = c.textWidth(value, Text.META) + 4.0f;
        c.textV(label, x + vw, cy, Tokens.INK_3, Text.MICRO);
        final float end = x + vw + c.textWidth(label, Text.MICRO) + 14.0f;
        this.hits.add(new Hit(new Rect(x - 4.0f, band.y, end - x, band.h), Action.KPI,
                new KpiTarget(target, select)));
        return end;
    }

    private void kpi(PanelSurface c, Rect r, String label, String value, int color, String foot,
                     boolean spark, int target, UUID select) {
        this.hits.add(new Hit(r, Action.KPI, new KpiTarget(target, select)));
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final float pad = u(Tokens.S3);
        c.text(Text.track(label), r.x + pad, r.y + u(6.0f), Tokens.INK_3, secondary());
        c.text(value, r.x + pad, r.y + u(18.0f), color, Text.SUB * this.density.step());
        if (spark) {
            drawPulse(c, new Rect(r.right() - u(62.0f), r.y + u(8.0f), u(54.0f), r.h - u(18.0f)),
                    color);
        }
        if (!foot.isEmpty()) {
            c.textRight(Text.truncate(c, foot, r.w - pad * 2.0f, secondary()),
                    r.right() - pad, r.bottom() - u(12.0f), Tokens.INK_3, secondary());
        }
    }

    private void drawPulse(PanelSurface c, Rect r, int color) {
        final RailHistory.Pulse pulse = RailHistory.pulse();
        final int n = pulse.size();
        if (n < 4 || r.isEmpty()) {
            return;
        }
        final int step = Math.max(1, n / 56);
        final int points = (n + step - 1) / step;
        final float[] xy = new float[points * 2];
        int peak = 1;
        for (int i = 0; i < n; i += step) {
            peak = Math.max(peak, pulse.moving(i));
        }
        int w = 0;
        for (int i = 0; i < n && w < points; i += step, w++) {
            xy[w * 2] = r.x + r.w * (i / (float) Math.max(1, n - 1));
            xy[w * 2 + 1] = r.bottom() - r.h * (pulse.moving(i) / (float) peak);
        }
        if (w > 1) {
            Shapes.polyline(c, xy, w, 1.2f, Tokens.alpha(color, 0.55f));
        }
    }

    private void drawNetworkMode(PanelSurface c, RailSnapshot snap) {
        final boolean inspector = TrainPageLayout.hasInspector(this.contentArea);
        final Rect mapArea = TrainPageLayout.mapArea(this.contentArea, this.density);
        this.inspectorArea = TrainPageLayout.inspectorArea(this.contentArea, this.density);
        if (mapArea.isEmpty()) {
            return;
        }
        c.card(mapArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        drawMapControls(c, mapArea.top(Tokens.S4 + 8.0f));

        this.plotArea = mapArea.inset(Tokens.S2, Tokens.S4 + 10.0f, Tokens.S2, Tokens.S2);
        if (this.plotArea.isEmpty()) {
            return;
        }
        this.mapView.setViewport(this.plotArea);
        if (!this.mapView.everFitted() && !this.map.isEmpty()) {
            this.mapView.fit(this.map, this.layout, true);
        }

        c.clip(this.plotArea);
        RailMapArt.draw(c, this.plotArea, this.map, this.layout, this.mapView);
        c.unclip();
        drawHover(c);

        if (inspector) {
            drawInspector(c, snap);
        } else if (this.selectedTrain != null) {
            drawSelectedTrainCard(c, snap, this.plotArea);
        }
    }

    private void drawMapControls(PanelSurface c, Rect header) {
        float x = header.x + Tokens.S3;
        final float y = header.y + 3.0f;
        final MapLayout.Mode[] modes = {MapLayout.Mode.GEOGRAPHIC, MapLayout.Mode.SCHEMATIC};
        for (int i = 0; i < modes.length; i++) {
            final String label = modes[i].label;
            final float w = c.textWidth(label, Text.MICRO) + 16.0f;
            final Rect chip = new Rect(x, y, w, 16.0f);
            final boolean active = this.mapView.mode() == modes[i];
            c.card(chip, Tokens.R_CHIP, active ? Tokens.alpha(Tokens.ACCENT, 0.14f) : Tokens.CANVAS,
                    active ? Tokens.alpha(Tokens.ACCENT, 0.5f) : Tokens.LINE);
            c.textCenterV(label, chip.centerX(), chip.centerY(),
                    active ? Tokens.ACCENT : Tokens.INK_3, Text.MICRO);
            this.hits.add(new Hit(chip, Action.MAP_MODE, i));
            x += w + 4.0f;
        }

        final String label = "Full map";
        final float w = c.textWidth(label, Text.MICRO) + 20.0f;
        final Rect expand = new Rect(header.right() - Tokens.S3 - w, y, w, 16.0f);
        c.card(expand, Tokens.R_CHIP, Tokens.alpha(Tokens.ACCENT, 0.12f),
                Tokens.alpha(Tokens.ACCENT, 0.45f));
        c.textCenterV(label, expand.centerX(), expand.centerY(), Tokens.ACCENT, Text.MICRO);
        this.hits.add(new Hit(expand, Action.FULL_MAP, null));

        float rx = expand.x - 4.0f;
        if (this.mapView.hasPlayer()) {
            rx = miniChip(c, rx, y, "Me", Action.MAP_ME) - 4.0f;
        }
        rx = miniChip(c, rx, y, "Fit", Action.MAP_FIT) - 6.0f;
        if (rx - x > 110.0f) {
            faceChip(c, rx, y, Mk3Settings.FaceMode.TRAIN_MAP, "");
        }
    }

    // the wall board button lives where you're already looking
    private float faceChip(PanelSurface c, float rightX, float y, Mk3Settings.FaceMode mode,
                           String target) {
        final Mk3Settings settings = this.ctx.settings();
        final boolean showing = settings.faceMode == mode
                && (target.isEmpty() || target.equals(faceTargetOf(settings)));
        final String label = showing ? "On face" : "Show on face";
        final float w = c.textWidth(label, Text.MICRO) + 18.0f;
        final Rect chip = new Rect(rightX - w, y, w, 16.0f);
        final int color = showing ? Tokens.GOOD : Tokens.ACCENT;
        c.card(chip, Tokens.R_CHIP, Tokens.alpha(color, showing ? 0.16f : 0.10f),
                Tokens.alpha(color, 0.45f));
        c.textCenterV(label, chip.centerX(), chip.centerY(), color, Text.MICRO);
        this.hits.add(new Hit(chip, Action.FACE_SET, new FaceSet(mode, target, showing)));
        return chip.x;
    }

    private record FaceSet(Mk3Settings.FaceMode mode, String target, boolean showing) {
    }

    private float miniChip(PanelSurface c, float rightX, float y, String label, Action action) {
        final float w = c.textWidth(label, Text.MICRO) + 14.0f;
        final Rect r = new Rect(rightX - w, y, w, 16.0f);
        c.card(r, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        c.textCenterV(label, r.centerX(), r.centerY(), Tokens.INK_3, Text.MICRO);
        this.hits.add(new Hit(r, action, null));
        return r.x;
    }

    private void drawInspector(PanelSurface c, RailSnapshot snap) {
        final Rect a = this.inspectorArea;
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final RailSnapshot.TrainState t = snap.train(this.selectedTrain);
        if (t != null) {
            final Rect body = a.inset(Tokens.S3);
            drawTrainDetail(c, body.inset(0.0f, 0.0f, 0.0f, 22.0f), snap, t, true);
            drawTrainActions(c, new Rect(body.x, body.bottom() - 18.0f, body.w, 18.0f), t);
            return;
        }

        final RailSnapshot.Station picked = snap.station(this.selectedStation);
        if (picked != null && this.mapView.selectedNode() >= 0) {
            final Rect card = a.inset(Tokens.S2, Tokens.S2, Tokens.S2, 0.0f).top(44.0f);
            c.card(card, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
            c.text(Text.truncate(c, picked.name, card.w - 12.0f, primary()), card.x + 6.0f,
                    card.y + 4.0f, Tokens.INK, primary());
            final float bw = c.textWidth("Departures", secondary()) + 18.0f;
            final Rect go = new Rect(card.x + 6.0f, card.bottom() - 20.0f, bw, 16.0f);
            c.card(go, Tokens.R_CHIP, Tokens.alpha(Tokens.ACCENT, 0.12f),
                    Tokens.alpha(Tokens.ACCENT, 0.45f));
            c.textCenterV("Departures", go.centerX(), go.centerY(), Tokens.ACCENT, secondary());
            this.hits.add(new Hit(go, Action.OPEN_BOARD, picked.id));
            drawServicesColumn(c, a.inset(0.0f, 50.0f, 0.0f, 0.0f));
            return;
        }
        drawServicesColumn(c, a);
    }

    private void drawTrainActions(PanelSurface c, Rect r, RailSnapshot.TrainState t) {
        final boolean following = t.id.equals(this.mapView.followTrain());
        final String label = following ? "Following" : "Follow";
        final int color = following ? Tokens.GOOD : Tokens.ACCENT;
        final float w = c.textWidth(label, secondary()) + 18.0f;
        final Rect chip = new Rect(r.x, r.y, w, 16.0f);
        c.card(chip, Tokens.R_CHIP, Tokens.alpha(color, following ? 0.16f : 0.10f),
                Tokens.alpha(color, 0.45f));
        c.textCenterV(label, chip.centerX(), chip.centerY(), color, secondary());
        this.hits.add(new Hit(chip, Action.FOLLOW, t.id));

        if (!t.destinationName.isEmpty() && r.w - w > 40.0f) {
            c.textRightV(Text.truncate(c, "to " + t.destinationName, r.w - w - 8.0f, secondary()),
                    r.right(), chip.centerY(), Tokens.alpha(Tokens.INK_3, 0.8f), secondary());
        }
    }

    private void drawServicesColumn(PanelSurface c, Rect a) {
        if (a.isEmpty()) {
            return;
        }
        c.text(Text.track("Services"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, secondary());
        final int count = this.map.lines().size();
        c.textRight(count + (count == 1 ? " service" : " services"), a.right() - Tokens.S3,
                a.y + 6.0f, Tokens.alpha(Tokens.INK_3, 0.7f), secondary());

        final Rect list = a.inset(Tokens.S2, Tokens.S4 + 6.0f, Tokens.S2, Tokens.S2);
        if (list.isEmpty()) {
            return;
        }

        final float step = RailMapArt.LEGEND_ROW_H * this.density.step();
        int[] order = RailMapArt.legendOrder(this.map, this.mapView);
        if (!query().isEmpty()) {
            final List<Integer> kept = new ArrayList<>();
            for (final int i : order) {
                if (matches(this.map.lines().get(i).label(this.map))) {
                    kept.add(i);
                }
            }
            order = new int[kept.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = kept.get(i);
            }
        }
        final float contentH = order.length * step;
        this.serviceScroll.setMetrics(contentH, list.h);
        final Rect scrolled = new Rect(list.x, list.y - this.serviceScroll.value(), list.w,
                Math.max(list.h, contentH));

        c.clip(list);
        order = RailMapArt.drawLegend(c, scrolled, this.map, this.mapView,
                this.legendHits, step, secondary(), order);
        c.unclip();
        for (int i = 0; i < this.legendHits.size() && i < order.length; i++) {
            final Rect row = this.legendHits.get(i);
            if (row.bottom() >= list.y && row.y <= list.bottom()) {
                this.hits.add(new Hit(row, Action.SELECT_LINE, order[i]));
            }
        }
        this.serviceScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawHover(PanelSurface c) {
        if (Double.isNaN(this.hoverX) || !this.plotArea.contains(this.hoverX, this.hoverY)
                || this.mapView.gesture() != MapView.Gesture.NONE
                || (this.overlay != OVERLAY_NONE && this.overlayArea.contains(this.hoverX,
                        this.hoverY))) {
            this.mapView.setHover(-1, null);
            return;
        }
        final UUID train = this.mapView.trainAt(this.map, this.hoverX, this.hoverY, 12.0f);
        final int node = train != null ? -1
                : this.mapView.nodeAt(this.map, this.layout, this.hoverX, this.hoverY, 13.0f);
        this.mapView.setHover(node, train);

        if (train != null) {
            final RailSnapshot.TrainState t = this.map.snapshot().train(train);
            if (t != null) {
                RailMapArt.drawTrainTooltip(c, this.plotArea, this.map, t, this.hoverX,
                        this.hoverY);
            }
            return;
        }
        final RailMap.Node hovered = node >= 0 ? this.map.node(node) : null;
        if (hovered != null) {
            RailMapArt.drawNodeTooltip(c, this.plotArea, this.map, hovered, this.hoverX,
                    this.hoverY);
        }
    }

    private void drawSelectedTrainCard(PanelSurface c, RailSnapshot snap, Rect plot) {
        final RailSnapshot.TrainState t = snap.train(this.selectedTrain);
        if (t == null) {
            return;
        }
        final Rect card = new Rect(plot.right() - 210.0f, plot.y, 210.0f, 104.0f);
        c.card(card, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);
        final Rect in = card.inset(Tokens.S3);
        c.text(Text.truncate(c, t.name, in.w, Text.META), in.x, in.y, Tokens.INK, Text.META);
        c.text(Text.truncate(c, TrainIntel.status(t), in.w, Text.MICRO), in.x, in.y + 14.0f,
                Tokens.INK_3, Text.MICRO);
        c.text(String.format(Locale.ROOT, "%.1f m/s", t.metresPerSecond()), in.x, in.y + 28.0f,
                Tokens.INK_2, Text.MICRO);
        if (t.distanceToDestination > 0.0) {
            c.textRight(TrainIntel.distance(t.distanceToDestination) + " to go", in.right(),
                    in.y + 28.0f, Tokens.INK_2, Text.MICRO);
        }

        final Rect bar = new Rect(in.x, in.y + 44.0f, in.w, 3.0f);
        Shapes.rect(c, bar.x, bar.y, bar.w, bar.h, Tokens.LINE);
        Shapes.rect(c, bar.x, bar.y, bar.w * t.legProgress(), bar.h, Tokens.ACCENT);
        TrainConsistArt.draw(c, new Rect(in.x, in.y + 52.0f, in.w, 22.0f), t,
                TrainGraphArt.trainColor(t, t.id));
        drawTrainActions(c, new Rect(in.x, in.y + 78.0f, in.w, 16.0f), t);
    }

    private void drawDiagramMode(PanelSurface c, RailSnapshot snap) {
        final Rect a = this.contentArea;
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final RailMap sheet = graphMap();
        this.graph.ensureLine(sheet);
        drawDiagramControls(c, a.top(Tokens.S4 + 8.0f), sheet);

        this.plotArea = a.inset(Tokens.S3, Tokens.S4 + 12.0f, Tokens.S3, Tokens.S2);
        if (this.plotArea.isEmpty()) {
            return;
        }
        c.clip(this.plotArea);
        this.graphFrame = TrainGraphArt.draw(c, this.plotArea, sheet, this.graph);
        c.unclip();
    }

    private RailMap graphMap() {
        return ClientRailMap.get();
    }

    private void drawDiagramControls(PanelSurface c, Rect header, RailMap sheet) {
        final float y = header.y + 3.0f;
        float x = header.x + Tokens.S3;

        for (int i = 0; i < sheet.lines().size(); i++) {
            final RailMap.Line line = sheet.lines().get(i);
            if (line.nodes.size() < 2) {
                continue;
            }
            final String label = Text.truncate(c, line.label(sheet), 96.0f, Text.MICRO);
            final float w = c.textWidth(label, Text.MICRO) + 20.0f;
            if (x + w > header.right() - 210.0f) {
                break;
            }
            final boolean active = this.graph.line() == i;
            final int color = RailMapArt.lineColor(i);
            final Rect chip = new Rect(x, y, w, 16.0f);
            c.card(chip, Tokens.R_CHIP, active ? Tokens.alpha(color, 0.16f) : Tokens.CANVAS,
                    active ? Tokens.alpha(color, 0.55f) : Tokens.LINE);
            Shapes.circle(c, chip.x + 7.0f, chip.centerY(), 2.5f, color);
            c.textCenterV(label, chip.centerX() + 4.0f, chip.centerY(),
                    active ? Tokens.INK : Tokens.INK_3, Text.MICRO);
            this.hits.add(new Hit(chip, Action.GRAPH_LINE, i));
            x += w + 4.0f;
        }

        float right = header.right() - Tokens.S3;
        for (int i = TrainGraphView.SPANS.length - 1; i >= 0; i--) {
            final String label = TrainGraphView.SPAN_LABELS[i];
            final float w = c.textWidth(label, Text.MICRO) + 12.0f;
            final Rect chip = new Rect(right - w, y, w, 16.0f);
            final boolean active = this.graph.spanIndex() == i;
            c.card(chip, Tokens.R_CHIP, active ? Tokens.alpha(Tokens.ACCENT, 0.14f) : Tokens.CANVAS,
                    active ? Tokens.alpha(Tokens.ACCENT, 0.5f) : Tokens.LINE);
            c.textCenterV(label, chip.centerX(), chip.centerY(),
                    active ? Tokens.ACCENT : Tokens.INK_3, Text.MICRO);
            this.hits.add(new Hit(chip, Action.GRAPH_SPAN, i));
            right -= w + 3.0f;
        }

        final var settings = this.ctx.settings();
        final RailMap.Line service = this.graph.line() >= 0
                && this.graph.line() < sheet.lines().size()
                ? sheet.lines().get(this.graph.line()) : null;
        if (service != null && right - header.x > 280.0f) {
            final boolean onFace = settings.faceMode == Mk3Settings.FaceMode.TRAIN_DIAGRAM
                    && service.label(sheet).equals(settings.diagramLine);
            final String label = onFace ? "On face" : "Show on face";
            final float w = c.textWidth(label, Text.MICRO) + 18.0f;
            final Rect chip = new Rect(right - w - 8.0f, y, w, 16.0f);
            final int color = onFace ? Tokens.GOOD : Tokens.ACCENT;
            c.card(chip, Tokens.R_CHIP, Tokens.alpha(color, onFace ? 0.16f : 0.10f),
                    Tokens.alpha(color, 0.45f));
            c.textCenterV(label, chip.centerX(), chip.centerY(), color, Text.MICRO);
            this.hits.add(new Hit(chip, Action.FACE_DIAGRAM, service.label(sheet)));
            right = chip.x - 4.0f;
        }

        if (!this.graph.live()) {
            final float w = c.textWidth("Live", Text.MICRO) + 16.0f;
            final Rect chip = new Rect(right - w - 6.0f, y, w, 16.0f);
            c.card(chip, Tokens.R_CHIP, Tokens.alpha(Tokens.CAUTION, 0.16f),
                    Tokens.alpha(Tokens.CAUTION, 0.5f));
            c.textCenterV("Live", chip.centerX(), chip.centerY(), Tokens.CAUTION, Text.MICRO);
            this.hits.add(new Hit(chip, Action.GRAPH_LIVE, null));
        }
    }

    // create's own predictions, same as a flap display on that platform
    private void drawBoardMode(PanelSurface c, RailSnapshot snap) {
        if (this.detailArea.isEmpty()) {
            if (this.selectedStation != null && snap.station(this.selectedStation) != null) {
                drawSinglePane(c, snap, true);
            } else {
                drawStationList(c, snap, this.contentArea);
            }
            return;
        }
        drawStationList(c, snap, this.listArea);

        final Rect a = this.detailArea;
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);

        RailSnapshot.Station station = snap.station(this.selectedStation);
        if (station == null && !snap.stations().isEmpty()) {
            station = snap.stations().get(0);
            this.selectedStation = station.id;
        }
        drawBoardBody(c, a.inset(0.0f, 0.0f, 0.0f, 0.0f), snap, station);
    }

    private void drawBoardBody(PanelSurface c, Rect a, RailSnapshot snap,
                               RailSnapshot.Station station) {
        if (a.isEmpty()) {
            return;
        }
        if (station == null) {
            c.textCenterV("No stations", a.centerX(), a.centerY(), Tokens.INK_3, Text.META);
            return;
        }

        c.text(Text.truncate(c, station.name, a.w - u(130.0f), Text.SUB * this.density.step()),
                a.x + Tokens.S3, a.y + u(8.0f), Tokens.INK, Text.SUB * this.density.step());
        c.text(stationSubtitle(station), a.x + Tokens.S3, a.y + u(26.0f), Tokens.INK_3,
                secondary());
        drawPutOnFaceButton(c, a, station);

        final List<TrainIntel.BoardRow> rows = TrainIntel.board(snap, station, 40);
        final Rect head = new Rect(a.x + Tokens.S2, a.y + u(44.0f), a.w - Tokens.S2 * 2.0f,
                u(12.0f));
        drawBoardHeader(c, head);

        final Rect list = new Rect(head.x, head.bottom() + 2.0f, head.w,
                a.bottom() - Tokens.S2 - head.bottom() - 2.0f);
        if (list.isEmpty()) {
            return;
        }
        if (rows.isEmpty()) {
            c.textCenterV("Nothing scheduled to call here", list.centerX(), list.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        final float step = rowH();
        this.boardScroll.setMetrics(rows.size() * step, list.h);
        c.clip(list);
        for (int i = 0; i < rows.size(); i++) {
            final Rect row = new Rect(list.x, list.y - this.boardScroll.value() + i * step,
                    list.w, step - 2.0f);
            if (row.bottom() >= list.y && row.y <= list.bottom()) {
                drawBoardRow(c, row, rows.get(i));
            }
        }
        c.unclip();
        this.boardScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawBoardHeader(PanelSurface c, Rect r) {
        final float[] cols = boardColumns(r);
        c.text(Text.track("Due"), r.x + u(8.0f), r.y, Tokens.INK_3, secondary());
        c.text(Text.track("Destination"), cols[0], r.y, Tokens.INK_3, secondary());
        if (cols[1] > 0.0f) {
            c.textRight(Text.track("Status"), r.right() - u(8.0f), r.y, Tokens.INK_3, secondary());
        }
        c.hairlineH(r.x, r.bottom(), r.w, Tokens.LINE);
    }

    private float[] boardColumns(Rect r) {
        final float destination = r.x + u(62.0f);
        final float status = r.w >= 320.0f ? r.right() - u(96.0f) : -1.0f;
        return new float[]{destination, status};
    }

    private void drawBoardRow(PanelSurface c, Rect r, TrainIntel.BoardRow row) {
        if (row.present) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.alpha(Tokens.GOOD, 0.08f));
        } else if (!row.blockedBy.isEmpty()) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.alpha(Tokens.CRITICAL, 0.07f));
        }
        this.hits.add(new Hit(r, Action.SELECT_TRAIN, row.trainId));

        final int etaColor = !row.blockedBy.isEmpty() ? Tokens.CRITICAL
                : row.present ? Tokens.GOOD
                : row.ticks < 0 ? Tokens.INK_3
                : row.ticks < 200 ? Tokens.GOOD : Tokens.INK;

        final RailMap.Line onLine = row.trainId == null ? null : this.map.lineOf(row.trainId);
        if (onLine != null) {
            Shapes.roundedRect(c, new Rect(r.x + 2.0f, r.y + u(5.0f), 2.5f, r.h - u(10.0f)), 1.2f,
                    RailMapArt.lineColor(this.map.lines().indexOf(onLine)));
        }
        c.textV(row.eta(), r.x + u(8.0f), r.centerY(), etaColor, primary());

        final float[] cols = boardColumns(r);
        final float destWidth = (cols[1] > 0.0f ? cols[1] : r.right() - u(8.0f))
                - cols[0] - u(8.0f);

        final String destination = row.terminates ? "Terminates here"
                : row.destination.isEmpty() ? row.train : row.destination;
        c.text(Text.truncate(c, destination, destWidth, primary()), cols[0], r.y + u(4.0f),
                Tokens.INK, primary());

        final String under = callingLine(row);
        c.text(Text.truncate(c, under, destWidth, secondary()), cols[0], r.y + u(19.0f),
                Tokens.INK_3, secondary());

        if (cols[1] > 0.0f) {
            final String status = !row.blockedBy.isEmpty() ? row.blockedBy
                    : row.present ? "at platform"
                    : row.imminent ? "approaching"
                    : TrainIntel.lateness(row.lateBy);
            final int color = !row.blockedBy.isEmpty() ? Tokens.CRITICAL
                    : switch (TrainIntel.latenessSeverity(row.lateBy)) {
                        case CRITICAL -> Tokens.CRITICAL;
                        case WARN -> Tokens.CAUTION;
                        default -> row.present ? Tokens.GOOD : Tokens.INK_2;
                    };
            c.textRight(Text.truncate(c, status, u(92.0f), secondary()), r.right() - u(8.0f),
                    r.y + u(4.0f), color, secondary());
            c.textRight(Text.truncate(c, row.train, u(92.0f), secondary()), r.right() - u(8.0f),
                    r.y + u(19.0f), Tokens.alpha(Tokens.INK_3, 0.8f), secondary());
        }
    }

    private String callingLine(TrainIntel.BoardRow row) {
        if (!row.blockedBy.isEmpty()) {
            return "will not arrive . " + row.blockedBy;
        }
        if (row.callingAt.size() <= 1) {
            return row.scheduleTitle.isEmpty() ? row.train : row.scheduleTitle;
        }
        final StringBuilder sb = new StringBuilder("calling at ");
        for (int i = 0; i < row.callingAt.size() - 1 && i < 4; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(row.callingAt.get(i));
        }
        if (row.callingAt.size() - 1 > 4) {
            sb.append(" +").append(row.callingAt.size() - 5);
        }
        return sb.toString();
    }

    private void drawPutOnFaceButton(PanelSurface c, Rect a, RailSnapshot.Station station) {
        final var settings = this.ctx.settings();
        final boolean showing = settings.faceMode == Mk3Settings.FaceMode.TRAIN_BOARD
                && station.name.equals(settings.boardStation);
        final String label = showing ? "On the face" : "Show on face";
        final float w = c.textWidth(label, Text.MICRO) + 20.0f;
        final Rect button = new Rect(a.right() - Tokens.S3 - w, a.y + 8.0f, w, 18.0f);
        final int color = showing ? Tokens.GOOD : Tokens.ACCENT;
        c.card(button, button.h * 0.5f, Tokens.alpha(color, showing ? 0.16f : 0.10f),
                Tokens.alpha(color, 0.45f));
        c.textCenterV(label, button.centerX(), button.centerY(), color, Text.MICRO);
        this.faceButton = button;
        this.faceButtonStation = station.name;
        this.hits.add(new Hit(button, Action.FACE_BOARD, station.name));
    }

    private String stationSubtitle(RailSnapshot.Station s) {
        final StringBuilder sb = new StringBuilder();
        if (s.assembling) {
            sb.append("assembly platform");
        }
        if (s.packagePorts > 0) {
            if (sb.length() > 0) {
                sb.append("  .  ");
            }
            sb.append(s.packagePorts).append(s.packagePorts == 1 ? " port" : " ports");
        }
        if (sb.length() == 0) {
            sb.append((int) s.x).append(", ").append((int) s.z);
        }
        return sb.toString();
    }

    private void drawStationList(PanelSurface c, RailSnapshot snap, Rect a) {
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Stations"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, secondary());

        final Rect list = a.inset(Tokens.S2, Tokens.S4 + 6.0f, Tokens.S2, Tokens.S2);
        if (list.isEmpty()) {
            return;
        }

        final List<RailSnapshot.Station> stations = new ArrayList<>();
        for (final RailSnapshot.Station s : snap.stations()) {
            if (matches(s.name)) {
                stations.add(s);
            }
        }
        stations.sort(Comparator
                .comparingInt((RailSnapshot.Station s) -> s.presentTrain != null ? 0
                        : s.imminentTrain != null ? 1 : 2)
                .thenComparing(s -> s.name.toLowerCase(Locale.ROOT)));

        if (stations.isEmpty()) {
            c.textCenterV("No station matches", list.centerX(), list.centerY(), Tokens.INK_3,
                    secondary());
            return;
        }
        final float step = listRowH();
        this.listScroll.setMetrics(stations.size() * step, list.h);
        c.clip(list);
        for (int i = 0; i < stations.size(); i++) {
            final RailSnapshot.Station s = stations.get(i);
            final Rect row = new Rect(list.x, list.y - this.listScroll.value() + i * step,
                    list.w, step - 2.0f);
            if (row.bottom() < list.y || row.y > list.bottom()) {
                continue;
            }
            if (s.id.equals(this.selectedStation)) {
                c.roundedRect(row, Tokens.R_CHIP, Tokens.RAISED);
            }
            this.hits.add(new Hit(row, Action.SELECT_STATION, s.id));
            final int dot = s.presentTrain != null ? Tokens.GOOD
                    : s.imminentTrain != null ? Tokens.CAUTION : Tokens.LINE_HI;
            Shapes.circle(c, row.x + u(10.0f), row.centerY(), 3.0f, dot);
            c.textV(Text.truncate(c, s.name, row.w - u(30.0f), primary()), row.x + u(18.0f),
                    row.centerY(), Tokens.INK_2, primary());
        }
        c.unclip();
        this.listScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawFleetMode(PanelSurface c, RailSnapshot snap) {
        if (this.detailArea.isEmpty() && this.selectedTrain != null
                && snap.train(this.selectedTrain) != null) {
            drawSinglePane(c, snap, false);
            return;
        }
        final Rect a = this.listArea;
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Fleet"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, secondary());

        final String order = FLEET_SORTS[this.fleetSort];
        final float ow = c.textWidth(order, secondary()) + 16.0f;
        final Rect chip = new Rect(a.right() - Tokens.S3 - ow, a.y + 2.0f, ow, 16.0f);
        c.card(chip, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        c.textCenterV(order, chip.centerX(), chip.centerY(), Tokens.INK_3, secondary());
        this.hits.add(new Hit(chip, Action.FLEET_SORT, null));

        final Rect list = a.inset(Tokens.S2, Tokens.S4 + 6.0f, Tokens.S2, Tokens.S2);
        final float step = rowH();
        final List<RailSnapshot.TrainState> trains = new ArrayList<>();
        for (final RailSnapshot.TrainState t : snap.trains()) {
            if (matches(t.name) || matches(t.scheduleTitle)) {
                trains.add(t);
            }
        }
        trains.sort(fleetOrder());

        if (trains.isEmpty()) {
            c.textCenterV(query().isEmpty() ? "No trains" : "No train matches", list.centerX(),
                    list.centerY(), Tokens.INK_3, primary());
        } else {
            this.listScroll.setMetrics(trains.size() * step, list.h);
            c.clip(list);
            for (int i = 0; i < trains.size(); i++) {
                final Rect row = new Rect(list.x, list.y - this.listScroll.value() + i * step,
                        list.w, step - 2.0f);
                if (row.bottom() >= list.y && row.y <= list.bottom()) {
                    drawFleetRow(c, row, trains.get(i));
                }
            }
            c.unclip();
            this.listScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
        }

        if (this.detailArea.isEmpty()) {
            return;
        }
        c.card(this.detailArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final RailSnapshot.TrainState t = snap.train(this.selectedTrain);
        if (t == null) {
            c.textCenterV("Select a train", this.detailArea.centerX(), this.detailArea.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }
        drawTrainDetail(c, this.detailArea.inset(Tokens.S3), snap, t, false);
    }

    private Comparator<RailSnapshot.TrainState> fleetOrder() {
        final Comparator<RailSnapshot.TrainState> byName =
                Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT));
        return switch (this.fleetSort) {
            case 1 -> byName;
            case 2 -> Comparator
                    .comparingDouble((RailSnapshot.TrainState t) -> -Math.abs(t.metresPerSecond()))
                    .thenComparing(byName);
            case 3 -> Comparator
                    .comparing((RailSnapshot.TrainState t) ->
                            t.scheduleTitle.isEmpty() ? "\uffff" : t.scheduleTitle.toLowerCase(
                                    Locale.ROOT))
                    .thenComparing(byName);
            default -> Comparator
                    .comparingInt((RailSnapshot.TrainState t) -> t.unhealthy() ? 0
                            : t.waitingForSignal ? 1 : t.moving() ? 2 : 3)
                    .thenComparing(byName);
        };
    }

    private void drawSinglePane(PanelSurface c, RailSnapshot snap, boolean board) {
        final Rect a = this.contentArea;
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final String label = board ? "All stations" : "All trains";
        final float w = c.textWidth(label, Text.MICRO) + 22.0f;
        final Rect back = new Rect(a.x + Tokens.S2, a.y + 6.0f, w, 18.0f);
        c.card(back, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        c.textCenterV("< " + label, back.centerX(), back.centerY(), Tokens.INK_2, Text.MICRO);
        this.hits.add(new Hit(back, Action.BACK, board));

        final Rect body = a.inset(Tokens.S3, 30.0f, Tokens.S3, Tokens.S3);
        if (board) {
            drawBoardBody(c, body, snap, snap.station(this.selectedStation));
        } else {
            drawTrainDetail(c, body, snap, snap.train(this.selectedTrain), false);
        }
    }

    private void drawFleetRow(PanelSurface c, Rect r, RailSnapshot.TrainState t) {
        final boolean selected = t.id.equals(this.selectedTrain);
        if (selected) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.RAISED);
        } else if (t.unhealthy()) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.alpha(Tokens.CRITICAL, 0.07f));
        }
        this.hits.add(new Hit(r, Action.SELECT_TRAIN, t.id));

        final int color = trainColor(t);
        Shapes.roundedRect(c, new Rect(r.x + u(6.0f), r.y + u(5.0f), 3.0f, r.h - u(10.0f)), 1.5f,
                color);

        c.text(Text.truncate(c, t.name, r.w * 0.62f, primary()), r.x + u(16.0f), r.y + u(5.0f),
                Tokens.INK, primary());
        c.text(Text.truncate(c, TrainIntel.status(t), r.w * 0.68f, secondary()), r.x + u(16.0f),
                r.y + u(19.0f), t.unhealthy() ? Tokens.CRITICAL : Tokens.INK_3, secondary());
        c.textRightV(String.format(Locale.ROOT, "%.1f", t.metresPerSecond()), r.right() - u(8.0f),
                r.centerY(), t.moving() ? Tokens.INK : Tokens.INK_3, primary());
    }

    private void drawTrainDetail(PanelSurface c, Rect in, RailSnapshot snap,
                                 RailSnapshot.TrainState t, boolean compact) {
        final float titleScale = Text.SUB * this.density.step();
        float y = in.y;
        c.text(Text.truncate(c, t.name, in.w - u(20.0f), titleScale), in.x, y, Tokens.INK,
                titleScale);
        Shapes.circle(c, in.right() - 5.0f, y + u(8.0f), 4.0f, trainColor(t));
        y += u(22.0f);
        c.text(Text.truncate(c, TrainIntel.status(t), in.w, primary()), in.x, y,
                t.unhealthy() ? Tokens.CRITICAL : Tokens.INK_2, primary());
        y += u(18.0f);

        final String late = TrainIntel.lateness(t.secondsLate());
        if (!late.isEmpty()) {
            final int color = switch (TrainIntel.latenessSeverity(t.secondsLate())) {
                case CRITICAL -> Tokens.CRITICAL;
                case WARN -> Tokens.CAUTION;
                default -> Tokens.GOOD;
            };
            c.text(late + " on this leg", in.x, y, color, secondary());
            y += u(14.0f);
        }

        final RailMap.Line line = this.map.lineOf(t.id);
        if (line != null) {
            Shapes.roundedRect(c, new Rect(in.x, y + 2.0f, 3.0f, u(9.0f)), 1.5f,
                    RailMapArt.lineColor(this.map.lines().indexOf(line)));
            c.text(Text.truncate(c, line.label(this.map), in.w - u(10.0f), secondary()),
                    in.x + u(8.0f), y, Tokens.INK_3, secondary());
            y += u(16.0f);
        }

        if (in.w > 200.0f && in.bottom() - y > u(70.0f)) {
            faceChip(c, in.right(), y, Mk3Settings.FaceMode.TRAIN_FOCUS, t.name);
            y += u(22.0f);
        }

        if (in.bottom() - y > u(46.0f)) {
            y += u(4.0f);
            TrainConsistArt.draw(c, new Rect(in.x, y, in.w, u(30.0f)), t, trainColor(t));
            y += u(42.0f);
        }

        if (t.distanceStartedAt > 0.5) {
            final Rect bar = new Rect(in.x, y, in.w, 3.0f);
            Shapes.rect(c, bar.x, bar.y, bar.w, bar.h, Tokens.LINE);
            Shapes.rect(c, bar.x, bar.y, bar.w * t.legProgress(), bar.h, Tokens.ACCENT);
            y += u(10.0f);
        }

        final boolean twoUp = in.w >= 260.0f;
        final Rect[] cols = twoUp
                ? Layout.columns(new Rect(in.x, y, in.w, in.bottom() - y), 2, Tokens.S3)
                : new Rect[]{new Rect(in.x, y, in.w, in.bottom() - y)};
        final List<String[]> facts = facts(t);
        final float factH = u(14.0f);
        final int perColumn = (facts.size() + cols.length - 1) / cols.length;
        for (int i = 0; i < facts.size(); i++) {
            final Rect col = cols[Math.min(cols.length - 1, i / Math.max(1, perColumn))];
            final float rowY = col.y + (i % Math.max(1, perColumn)) * factH;
            if (rowY + u(12.0f) > col.bottom()) {
                continue;
            }
            c.text(facts.get(i)[0], col.x, rowY, Tokens.INK_3, secondary());
            c.textRight(Text.truncate(c, facts.get(i)[1], col.w * 0.62f, secondary()),
                    col.right(), rowY, Tokens.INK_2, secondary());
        }

        float tail = cols[0].y + perColumn * factH + u(6.0f);
        if (compact || tail + u(26.0f) > in.bottom()) {
            return;
        }

        final RailHistory.Series series = RailHistory.of(t.id);
        if (series != null && series.size() > 4) {
            c.text(Text.track("Speed"), in.x, tail, Tokens.INK_3, secondary());
            drawSpeedTrace(c, new Rect(in.x, tail + u(12.0f), in.w, u(22.0f)), series,
                    trainColor(t));
            tail += u(38.0f);
        }

        final String route = TrainIntel.route(t, 8);
        if (!route.isEmpty() && tail + u(24.0f) < in.bottom()) {
            c.text(Text.track("Route"), in.x, tail, Tokens.INK_3, secondary());
            tail += u(13.0f);
            for (final String stopLine : wrapRoute(c, route, in.w)) {
                if (tail + u(12.0f) > in.bottom()) {
                    break;
                }
                c.text(stopLine, in.x, tail, Tokens.INK_2, secondary());
                tail += u(12.0f);
            }
        }
    }

    private void drawSpeedTrace(PanelSurface c, Rect r, RailHistory.Series series, int color) {
        final int n = series.size();
        final float[] xy = new float[n * 2];
        float peak = 0.5f;
        final float[] speed = new float[n];
        for (int i = 1; i < n; i++) {
            final float dt = series.time(i) - series.time(i - 1);
            final float dx = Math.abs(series.chain(i) - series.chain(i - 1));

            speed[i] = dt <= 0.001f || dx > 400.0f ? speed[i - 1] : dx / dt;
            peak = Math.max(peak, speed[i]);
        }
        if (n > 1) {
            speed[0] = speed[1];
        }
        for (int i = 0; i < n; i++) {
            xy[i * 2] = r.x + r.w * (i / (float) Math.max(1, n - 1));
            xy[i * 2 + 1] = r.bottom() - r.h * (speed[i] / peak);
        }
        c.card(r, 2.0f, Tokens.CANVAS, Tokens.LINE);
        Shapes.polyline(c, xy, n, 1.2f, Tokens.alpha(color, 0.85f));
        c.textRight(String.format(Locale.ROOT, "%.1f m/s peak", peak), r.right() - 3.0f,
                r.y + 1.0f, Tokens.alpha(Tokens.INK_3, 0.8f), secondary());
    }

    private List<String[]> facts(RailSnapshot.TrainState t) {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[]{"Speed", String.format(Locale.ROOT, "%.2f m/s", t.metresPerSecond())});
        if (t.maxSpeed > 0.0f) {
            out.add(new String[]{"Top speed",
                    String.format(Locale.ROOT, "%.1f m/s", t.maxSpeed * TrainIntel.TICKS_PER_SECOND)});
        }
        if (!t.destinationName.isEmpty()) {
            out.add(new String[]{"Next stop", t.destinationName});
            out.add(new String[]{"Track left", TrainIntel.distance(t.distanceToDestination)});
        }
        if (t.waitingForSignal) {
            out.add(new String[]{"Signal ahead", TrainIntel.distance(t.distanceToSignal)});
        }

        if (t.occupiedBlocks > 0 || t.reservedBlocks > 0) {
            out.add(new String[]{"Signal blocks",
                    t.occupiedBlocks + " held, " + t.reservedBlocks + " claimed"});
        }
        if (t.fuelTicks > 0) {
            out.add(new String[]{"Fuel left",
                    TrainIntel.duration(t.fuelTicks / TrainIntel.TICKS_PER_SECOND)});
        }
        if (t.itemCount > 0) {
            out.add(new String[]{"Cargo", Text.group(t.itemCount)
                    + (t.topItem.isEmpty() ? " items" : " " + t.topItem)});
        }
        if (t.fluidAmount > 0) {
            out.add(new String[]{"Fluid", Text.group(t.fluidAmount) + " mB"
                    + (t.topFluid.isEmpty() ? "" : " " + t.topFluid)});
        }
        final com.createstats.train.power.TrainChargeReading charge = t.charge();
        if (charge != null) {
            final long now = ClientRailNetwork.get() == null ? charge.tick() : ClientRailNetwork.get().serverTick;
            out.add(new String[]{"Charge", charge.percent() + "% of "
                    + com.createstats.power.LimiterLadder.compact(charge.capacity()) + " FE"
                    + (charge.isLive(now) ? "" : ", " + charge.ageLabel(now))});
        }
        if (t.railState != 0) {
            out.add(new String[]{"Power", com.createstats.powertrack.PowerTrackFacts.line(t.railState, t.railFlow)});
        }
        if (t.manual) {
            out.add(new String[]{"Control", "driven by hand"});
        }
        if (!t.owner.isEmpty()) {
            out.add(new String[]{"Owner", t.owner});
        }
        return out;
    }

    private List<String> wrapRoute(PanelSurface c, String route, float width) {
        final List<String> out = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String part : route.split(" \\. ")) {
            final String candidate = current.length() == 0 ? part : current + " . " + part;
            if (c.textWidth(candidate, secondary()) > width && current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
                current.append(part);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private void drawServiceMode(PanelSurface c) {
        final List<RailPerformance.Report> all = RailPerformance.report(this.map);
        final List<RailPerformance.Report> reports = new ArrayList<>();
        for (final RailPerformance.Report r : all) {
            if (matches(r.label())) {
                reports.add(r);
            }
        }

        if (this.detailArea.isEmpty()) {
            final RailPerformance.Report picked = pickedReport(reports);
            if (picked != null && this.selectedService >= 0) {
                drawServiceSinglePane(c, picked);
            } else {
                drawServiceList(c, reports, this.contentArea);
            }
            return;
        }
        drawServiceList(c, reports, this.listArea);
        c.card(this.detailArea, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        drawServiceDetail(c, this.detailArea.inset(Tokens.S3), pickedReport(reports));
    }

    private RailPerformance.Report pickedReport(List<RailPerformance.Report> reports) {
        for (final RailPerformance.Report r : reports) {
            if (r.lineIndex() == this.selectedService) {
                return r;
            }
        }

        return reports.isEmpty() ? null : reports.get(0);
    }

    private void drawServiceList(PanelSurface c, List<RailPerformance.Report> reports, Rect a) {
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Regularity"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3, secondary());
        c.textRight(Text.track("added wait"), a.right() - Tokens.S3, a.y + 6.0f,
                Tokens.alpha(Tokens.INK_3, 0.7f), secondary());

        final Rect list = a.inset(Tokens.S2, Tokens.S4 + 6.0f, Tokens.S2, Tokens.S2);
        if (list.isEmpty()) {
            return;
        }
        if (reports.isEmpty()) {
            c.textCenterV(this.map.lines().isEmpty() ? "No scheduled services" : "Nothing measured",
                    list.centerX(), list.centerY() - u(8.0f), Tokens.INK_3, primary());
            c.textCenterV("Regularity needs a train to pass the same place twice",
                    list.centerX(), list.centerY() + u(10.0f), Tokens.alpha(Tokens.INK_3, 0.7f),
                    secondary());
            return;
        }

        final float step = rowH();
        this.listScroll.setMetrics(reports.size() * step, list.h);
        c.clip(list);
        for (int i = 0; i < reports.size(); i++) {
            final Rect row = new Rect(list.x, list.y - this.listScroll.value() + i * step,
                    list.w, step - 2.0f);
            if (row.bottom() >= list.y && row.y <= list.bottom()) {
                drawServiceRow(c, row, reports.get(i));
            }
        }
        c.unclip();
        this.listScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawServiceRow(PanelSurface c, Rect r, RailPerformance.Report report) {
        final boolean selected = report.lineIndex() == this.selectedService;
        if (selected) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.RAISED);
        } else if (report.grade() == RailPerformance.Grade.BUNCHED) {
            c.roundedRect(r, Tokens.R_CHIP, Tokens.alpha(Tokens.CRITICAL, 0.07f));
        }
        this.hits.add(new Hit(r, Action.SELECT_SERVICE, report.lineIndex()));

        final int color = RailMapArt.lineColor(report.lineIndex());
        Shapes.roundedRect(c, new Rect(r.x + u(6.0f), r.y + u(5.0f), 3.0f, r.h - u(10.0f)), 1.5f,
                color);

        final float right = r.right() - u(8.0f);
        final String added = report.measured() && report.excessWait() >= 1.0f
                ? "+" + RailPerformance.clock(report.excessWait()) : "-";
        c.textRightV(added, right, r.centerY(),
                report.excessWait() >= 30.0f ? Tokens.CRITICAL
                        : report.excessWait() >= 5.0f ? Tokens.CAUTION : Tokens.INK_3, primary());
        final float valueW = c.textWidth(added, primary()) + u(14.0f);

        c.text(Text.truncate(c, report.label(), r.w - valueW - u(24.0f), primary()), r.x + u(16.0f),
                r.y + u(4.0f), Tokens.INK, primary());

        final Rect strip = new Rect(r.x + u(16.0f), r.y + u(18.0f),
                Math.max(10.0f, r.w - valueW - u(24.0f)), r.h - u(21.0f));
        if (report.measured()) {
            RailPerformanceArt.headwayStrip(c, strip, report, color);
        } else {
            c.textV(RailPerformance.describe(report), strip.x, strip.centerY(),
                    Tokens.alpha(Tokens.INK_3, 0.7f), secondary());
        }
    }

    private void drawServiceSinglePane(PanelSurface c, RailPerformance.Report report) {
        final Rect a = this.contentArea;
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final float w = c.textWidth("All services", secondary()) + 22.0f;
        final Rect back = new Rect(a.x + Tokens.S2, a.y + 6.0f, w, 18.0f);
        c.card(back, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        c.textCenterV("< All services", back.centerX(), back.centerY(), Tokens.INK_2, secondary());
        this.hits.add(new Hit(back, Action.SELECT_SERVICE, -1));
        drawServiceDetail(c, a.inset(Tokens.S3, 30.0f, Tokens.S3, Tokens.S3), report);
    }

    private void drawServiceDetail(PanelSurface c, Rect in, RailPerformance.Report report) {
        if (in.isEmpty()) {
            return;
        }
        if (report == null) {
            c.textCenterV("Select a service", in.centerX(), in.centerY(), Tokens.INK_3, primary());
            return;
        }
        final int color = RailMapArt.lineColor(report.lineIndex());
        float y = in.y;

        final float titleScale = Text.SUB * this.density.step();
        c.text(Text.truncate(c, report.label(), in.w - u(70.0f), titleScale), in.x, y, Tokens.INK,
                titleScale);
        RailPerformanceArt.gradeChip(c, in.right() - u(62.0f), y + u(8.0f), report, secondary());
        y += u(24.0f);

        if (in.w > 200.0f) {
            faceChip(c, in.right(), y, Mk3Settings.FaceMode.TRAIN_ROUTE, report.label());
            y += u(22.0f);
        }

        c.text(RailPerformanceArt.excessLine(report), in.x, y,
                report.excessWait() >= 30.0f ? Tokens.CRITICAL
                        : report.excessWait() >= 5.0f ? Tokens.CAUTION : Tokens.GOOD, primary());
        y += u(18.0f);
        if (report.measured()) {
            c.text(RailPerformanceArt.spread(report) + "  .  measured at " + report.cutPoint(),
                    in.x, y, Tokens.INK_3, secondary());
        } else {
            c.text("Needs " + RailPerformance.MIN_CROSSINGS + " passings of one place to say "
                    + "anything. Seen " + report.crossings() + ".", in.x, y, Tokens.INK_3,
                    secondary());
        }
        y += u(20.0f);

        if (in.bottom() - y > u(52.0f)) {
            c.text(Text.track("Gaps between trains"), in.x, y, Tokens.INK_3, secondary());
            y += u(13.0f);
            RailPerformanceArt.headways(c, new Rect(in.x, y, in.w, u(46.0f)), report, color,
                    secondary());
            y += u(54.0f);
        }

        if (in.bottom() - y > u(40.0f)) {
            c.text(Text.track("Where the time goes"), in.x, y, Tokens.INK_3, secondary());
            y += u(13.0f);
            RailPerformanceArt.timeSplit(c, new Rect(in.x, y, in.w, u(30.0f)), report, secondary());
            y += u(38.0f);
        }

        final float ideal = RailPerformance.idealHeadway(report);
        if (ideal > 0.0f && in.bottom() - y > u(30.0f)) {
            c.text(report.trains() + (report.trains() == 1 ? " train on a " : " trains on a ")
                            + RailPerformance.clock(report.cycleTime()) + " round trip",
                    in.x, y, Tokens.INK_3, secondary());
            y += u(13.0f);
            final String line = "Evenly spread that is a " + RailPerformance.clock(ideal)
                    + " gap.  One more train would make it "
                    + RailPerformance.clock(RailPerformance.headwayWithOneMore(report)) + ".";
            c.text(Text.truncate(c, line, in.w, secondary()), in.x, y, Tokens.INK_2, secondary());
            y += u(20.0f);
        }

        final List<String[]> facts = new ArrayList<>();
        if (report.measured()) {
            facts.add(new String[]{"Average gap", RailPerformance.clock(report.meanHeadway())});
            facts.add(new String[]{"Longest gap", RailPerformance.clock(report.worstHeadway())});
            facts.add(new String[]{"Shortest gap", RailPerformance.clock(report.bestHeadway())});
            facts.add(new String[]{"Typical wait", RailPerformance.clock(report.actualWait())});
        }
        if (report.cycleTime() > 0.0f) {
            facts.add(new String[]{"Round trip", RailPerformance.clock(report.cycleTime())});
        }
        facts.add(new String[]{"Trains on it", String.valueOf(report.trains())});
        facts.add(new String[]{"Passings seen", String.valueOf(report.crossings())});

        final Rect[] cols = in.w >= 260.0f
                ? Layout.columns(new Rect(in.x, y, in.w, in.bottom() - y), 2, Tokens.S3)
                : new Rect[]{new Rect(in.x, y, in.w, in.bottom() - y)};
        final float factH = u(14.0f);
        final int perColumn = (facts.size() + cols.length - 1) / cols.length;
        for (int i = 0; i < facts.size(); i++) {
            final Rect col = cols[Math.min(cols.length - 1, i / Math.max(1, perColumn))];
            final float rowY = col.y + (i % Math.max(1, perColumn)) * factH;
            if (rowY + u(12.0f) > col.bottom()) {
                continue;
            }
            c.text(facts.get(i)[0], col.x, rowY, Tokens.INK_3, secondary());
            c.textRight(facts.get(i)[1], col.right(), rowY, Tokens.INK_2, secondary());
        }
    }

    private void drawAlertsMode(PanelSurface c, RailSnapshot snap) {
        final Rect a = this.contentArea;
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("What needs attention"), a.x + Tokens.S3, a.y + 6.0f, Tokens.INK_3,
                secondary());
        if (a.w > 240.0f) {
            faceChip(c, a.right() - Tokens.S3, a.y + 4.0f, Mk3Settings.FaceMode.TRAIN_STATUS, "");
        }

        final List<AlertRow> all = new ArrayList<>();
        for (final TrainIntel.Alert alert : TrainIntel.alerts(snap)) {
            all.add(new AlertRow(alert, -1));
        }

        for (final RailPerformance.Report r : RailPerformance.report(this.map)) {
            if (r.grade() != RailPerformance.Grade.BUNCHED) {
                continue;
            }
            all.add(new AlertRow(TrainIntel.Alert.of(TrainIntel.Severity.WARN, r.label(),
                    "Trains are running in convoy. The longest gap is "
                            + RailPerformance.clock(r.worstHeadway()) + " against an average of "
                            + RailPerformance.clock(r.meanHeadway()) + ", adding "
                            + RailPerformance.clock(r.excessWait()) + " to the wait.", null),
                    r.lineIndex()));
        }
        all.sort(Comparator.comparingInt((AlertRow row) -> -row.alert().severity.ordinal())
                .thenComparing(row -> row.alert().subject));

        final List<AlertRow> alerts = new ArrayList<>();
        for (final AlertRow row : all) {
            if (matches(row.alert().subject) || matches(row.alert().detail)) {
                alerts.add(row);
            }
        }
        final Rect list = a.inset(Tokens.S3, Tokens.S4 + 6.0f, Tokens.S3, Tokens.S3);
        if (alerts.isEmpty() && !all.isEmpty()) {
            c.textCenterV("No fault matches", list.centerX(), list.centerY(), Tokens.INK_3,
                    primary());
            return;
        }
        if (alerts.isEmpty()) {
            c.textCenterV("Nothing wrong on the network", list.centerX(), list.centerY() - u(8.0f),
                    Tokens.GOOD, Text.BODY * this.density.step());
            c.textCenterV("Every train has a driver, a route and a clear signal",
                    list.centerX(), list.centerY() + u(10.0f), Tokens.INK_3, secondary());
            return;
        }

        final int cols = list.w >= 640.0f ? 2 : 1;
        final float rowH = u(44.0f);
        final float contentH = Layout.gridHeight(alerts.size(), cols, rowH - 2.0f, 2.0f);
        this.listScroll.setMetrics(contentH, list.h);
        c.clip(list);
        for (int i = 0; i < alerts.size(); i++) {
            final Rect cell = Layout.gridCell(
                    new Rect(list.x, list.y - this.listScroll.value(), list.w, list.h),
                    cols, rowH - 2.0f, 2.0f, i);
            if (cell.bottom() < list.y || cell.y > list.bottom()) {
                continue;
            }
            drawAlert(c, cell, alerts.get(i));
        }
        c.unclip();
        this.listScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private record AlertRow(TrainIntel.Alert alert, int serviceLine) {
    }

    private void drawAlert(PanelSurface c, Rect row, AlertRow entry) {
        final TrainIntel.Alert alert = entry.alert();
        final int color = switch (alert.severity) {
            case CRITICAL -> Tokens.CRITICAL;
            case WARN -> Tokens.CAUTION;
            default -> Tokens.INK_3;
        };
        c.card(row, Tokens.R_CHIP, Tokens.alpha(color, 0.07f), Tokens.alpha(color, 0.3f));

        if (entry.serviceLine() >= 0) {
            this.hits.add(new Hit(row, Action.OPEN_SERVICE, entry.serviceLine()));
        } else {
            this.hits.add(new Hit(row, Action.SELECT_TRAIN, alert.trainId));
        }
        Shapes.circle(c, row.x + u(14.0f), row.centerY(), 3.5f, color);
        c.text(Text.truncate(c, alert.subject, row.w - u(40.0f), primary()), row.x + u(26.0f),
                row.y + u(7.0f), Tokens.INK, primary());
        c.text(Text.truncate(c, alert.detail, row.w - u(40.0f), secondary()), row.x + u(26.0f),
                row.y + u(22.0f), Tokens.INK_3, secondary());
    }

    private void drawFilterOverlay(PanelSurface c) {
        final RailFilter filter = RailFilter.get();

        this.patternField.setVisible(false);
        final Rect a = overlayRect(300.0f);
        this.overlayArea = a;
        c.shadow(a.inset(-3.0f), Tokens.R_CARD + 3.0f, 8.0f, Tokens.alpha(0xFF000000, 0.35f));
        c.card(a, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);

        c.text("Filters", a.x + Tokens.S3, a.y + 9.0f, Tokens.INK, Text.META);
        headerAction(c, a.right() - Tokens.S3, a.y + 9.0f, "Show all", Action.SHOW_ALL);

        final Rect body = a.inset(Tokens.S3, 30.0f, Tokens.S3, Tokens.S3);
        c.clip(body);
        float y = body.y - this.panelScroll.value();

        y = section(c, body, y, "Trains");
        y = flagRow(c, body, y, "Hide trains at a platform", filter.hideDocked(),
                Action.FLAG_DOCKED);
        y = flagRow(c, body, y, "Hide trains with no schedule", filter.hideParked(),
                Action.FLAG_PARKED);
        y = flagRow(c, body, y, "Only show problems", filter.onlyProblems(), Action.FLAG_PROBLEMS);
        for (final RailFilter.Rule rule : filter.trainRules()) {
            y = ruleRow(c, body, y, rule);
        }
        y = addRow(c, body, y, "Hide trains named...", Action.ADD_TRAIN_RULE, PATTERN_TRAIN);

        y = section(c, body, y + 6.0f, "Stations and groups");
        for (final RailFilter.Rule rule : filter.stationRules()) {
            y = ruleRow(c, body, y, rule);
        }
        y = addRow(c, body, y, "Hide stations named...", Action.ADD_STATION_RULE, PATTERN_STATION);

        y = section(c, body, y + 6.0f, "Fold platforms into one stop");
        final List<String> groups = this.ctx.panel.getTrainMapGroups();
        for (final String pattern : groups) {
            y = groupRow(c, body, y, pattern);
        }
        if (groups.isEmpty()) {
            y = note(c, body, y, "Obvious yards are folded automatically.");
        }
        y = addRow(c, body, y, "Fold platforms named...", Action.ADD_GROUP, PATTERN_GROUP);

        c.unclip();
        this.panelScroll.setMetrics(y - (body.y - this.panelScroll.value()) + 8.0f, body.h);
        this.panelScroll.drawBar(c, new Rect(body.right() - 3.0f, body.y, 3.0f, body.h));
    }

    private void drawViewOverlay(PanelSurface c) {
        final Rect a = overlayRect(250.0f);
        this.overlayArea = a;
        c.shadow(a.inset(-3.0f), Tokens.R_CARD + 3.0f, 8.0f, Tokens.alpha(0xFF000000, 0.35f));
        c.card(a, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);
        c.text(this.mode == DIAGRAM ? "Diagram options" : "Map options", a.x + Tokens.S3,
                a.y + 9.0f, Tokens.INK, Text.META);

        final Rect body = a.inset(Tokens.S3, 30.0f, Tokens.S3, Tokens.S3);
        float y = body.y;
        if (this.mode == DIAGRAM) {
            y = flagRow(c, body, y, "Distance to scale", !this.graph.evenSpacing(),
                    Action.GRAPH_SPACING);
            y = flagRow(c, body, y, "Show forecasts", this.graph.showPlanned(),
                    Action.GRAPH_PLANNED);
            y = flagRow(c, body, y, "Mark meets and overtakes", this.graph.showConflicts(),
                    Action.GRAPH_MEETS);
            y = flagRow(c, body, y, "Mark signal holds", this.graph.showHolds(), Action.GRAPH_HOLDS);
            flagRow(c, body, y, "Time grid", this.graph.showGrid(), Action.GRAPH_GRID);
            return;
        }
        y = viewToggle(c, body, y, "Real track", this.mapView.showTrack(), 0);
        y = viewToggle(c, body, y, "Station names", this.mapView.showLabels(), 1);
        y = viewToggle(c, body, y, "Ground grid", this.mapView.showGrid(), 2);
        y = viewToggle(c, body, y, "Animate", this.mapView.animate(), 3);
        y = viewToggle(c, body, y, "Fold station complexes", this.mapView.autoGroup(), 4);

        viewToggle(c, body, y, "Dim idle services", this.mapView.dimIdle(), 5);
    }

    private static final Mk3Settings.FaceMode[] FACE_MODES = {
            Mk3Settings.FaceMode.INSTRUMENTS, Mk3Settings.FaceMode.TRAIN_BOARD,
            Mk3Settings.FaceMode.TRAIN_MAP, Mk3Settings.FaceMode.TRAIN_ROUTE,
            Mk3Settings.FaceMode.TRAIN_DIAGRAM, Mk3Settings.FaceMode.TRAIN_FOCUS,
            Mk3Settings.FaceMode.TRAIN_STATUS};

    private static final String[] FACE_MODE_LABELS = {
            "Instruments", "Departures", "Line map", "Route strip", "Diagram", "One train",
            "Status"};

    private static final int[] FACE_FLAGS = {
            Mk3Settings.FACE_SPEED, Mk3Settings.FACE_CALLING, Mk3Settings.FACE_CAUSE,
            Mk3Settings.FACE_CARGO, Mk3Settings.FACE_LABELS, Mk3Settings.FACE_TRACK,
            Mk3Settings.FACE_PLANNED};

    private static final String[] FACE_FLAG_LABELS = {
            "Speed", "Calling points", "Why it is late", "Cargo", "Names on the map",
            "Real track", "Forecasts"};

    private static final String[] FACE_FOCUS_LABELS = {
            "Whole network", "This service", "This train", "Board stations", "Around the panel"};

    private void drawFaceOverlay(PanelSurface c) {
        final Rect a = overlayRect(258.0f);
        this.overlayArea = a;
        c.shadow(a.inset(-3.0f), Tokens.R_CARD + 3.0f, 8.0f, Tokens.alpha(0xFF000000, 0.35f));
        c.card(a, Tokens.R_CARD, Tokens.OVERLAY, Tokens.LINE_HI);
        c.text("Wall face", a.x + Tokens.S3, a.y + 9.0f, Tokens.INK, Text.META);

        final Mk3Settings settings = this.ctx.settings();
        c.textRight(settings.faceMode.isTrain() ? "LIVE" : "OFF", a.right() - Tokens.S3, a.y + 9.0f,
                settings.faceMode.isTrain() ? Tokens.GOOD : Tokens.INK_3, Text.MICRO);

        final Rect body = a.inset(Tokens.S3, 28.0f, Tokens.S3, Tokens.S3);
        float y = section(c, body, body.y, "Showing");
        y = chipGrid(c, body, y, FACE_MODE_LABELS, Action.FACE_MODE,
                java.util.Arrays.asList(FACE_MODES).indexOf(settings.faceMode), 2);

        y = section(c, body, y + 4.0f, targetTitle(settings.faceMode));
        y = drawFaceTarget(c, body, y, settings);

        if (settings.faceMode == Mk3Settings.FaceMode.TRAIN_MAP) {
            y = section(c, body, y + 4.0f, "Frame");
            y = chipGrid(c, body, y, FACE_FOCUS_LABELS, Action.FACE_FOCUS,
                    settings.mapFocus.ordinal(), 2);
        }

        y = section(c, body, y + 4.0f, "Detail");
        for (int i = 0; i < FACE_FLAGS.length && y + 20.0f <= body.bottom(); i++) {
            y = faceFlagRow(c, body, y, FACE_FLAG_LABELS[i], FACE_FLAGS[i], settings);
        }
    }

    private static String targetTitle(Mk3Settings.FaceMode mode) {
        return switch (mode) {
            case TRAIN_BOARD -> "Platforms";
            case TRAIN_MAP -> "Framed on";
            case TRAIN_ROUTE, TRAIN_DIAGRAM -> "Service";
            case TRAIN_FOCUS -> "Train";
            default -> "Target";
        };
    }

    private float drawFaceTarget(PanelSurface c, Rect body, float y, Mk3Settings settings) {
        final String current = faceTargetOf(settings);
        c.text(Text.truncate(c, current.isEmpty() ? "not set" : current, body.w - 12.0f,
                        Text.MICRO), body.x, y + 2.0f,
                current.isEmpty() ? Tokens.CAUTION : Tokens.INK_2, Text.MICRO);
        y += 16.0f;

        final String offer = faceSelectionFor(settings.faceMode);
        if (offer.isEmpty()) {
            c.text("Select one on this page to set it", body.x, y + 2.0f, Tokens.INK_3, Text.MICRO);
            return y + 18.0f;
        }
        final boolean already = offer.equals(current);
        final float w = Math.min(body.w - 6.0f, c.textWidth(offer, Text.MICRO) + 44.0f);
        final Rect button = new Rect(body.x, y, w, 18.0f);
        final int color = already ? Tokens.GOOD : Tokens.ACCENT;
        c.card(button, 9.0f, Tokens.alpha(color, already ? 0.16f : 0.10f),
                Tokens.alpha(color, 0.45f));
        c.textCenterV(already ? "Showing " + offer : "Use " + offer, button.centerX(),
                button.centerY(), color, Text.MICRO);
        this.hits.add(new Hit(button, Action.FACE_TARGET, offer));

        if (settings.faceMode == Mk3Settings.FaceMode.TRAIN_BOARD && !already
                && !current.isEmpty()) {
            final float addW = c.textWidth("Add", Text.MICRO) + 20.0f;
            final Rect add = new Rect(Math.min(button.right() + 6.0f, body.right() - addW), y,
                    addW, 18.0f);
            c.card(add, 9.0f, Tokens.alpha(Tokens.ACCENT, 0.10f), Tokens.alpha(Tokens.ACCENT, 0.4f));
            c.textCenterV("Add", add.centerX(), add.centerY(), Tokens.ACCENT, Text.MICRO);
            this.hits.add(new Hit(add, Action.FACE_ADD_STATION, offer));
        }
        return y + 22.0f;
    }

    private static String faceTargetOf(Mk3Settings settings) {
        return switch (settings.faceMode) {
            case TRAIN_BOARD -> settings.boardStation;
            case TRAIN_MAP -> settings.mapFocus == Mk3Settings.MapFocus.LINE ? settings.diagramLine
                    : settings.mapFocus == Mk3Settings.MapFocus.TRAIN ? settings.focusTrain
                    : settings.mapFocus == Mk3Settings.MapFocus.STATION ? settings.boardStation
                    : settings.mapFocus == Mk3Settings.MapFocus.NEAR
                            ? settings.mapRadius + " m" : "everything";
            case TRAIN_ROUTE, TRAIN_DIAGRAM -> settings.diagramLine.isEmpty()
                    ? "the busiest service" : settings.diagramLine;
            case TRAIN_FOCUS -> settings.focusTrain.isEmpty()
                    ? "whatever needs attention" : settings.focusTrain;
            default -> "";
        };
    }

    private String faceSelectionFor(Mk3Settings.FaceMode mode) {
        final RailSnapshot snap = ClientRailNetwork.get();
        switch (mode) {
            case TRAIN_BOARD -> {
                final RailSnapshot.Station station = this.selectedStation == null ? null
                        : snap.station(this.selectedStation);
                return station == null ? "" : station.name;
            }
            case TRAIN_FOCUS -> {
                final RailSnapshot.TrainState t = this.selectedTrain == null ? null
                        : snap.train(this.selectedTrain);
                return t == null ? "" : t.name;
            }
            case TRAIN_ROUTE, TRAIN_DIAGRAM -> {
                final int line = this.graph.line();
                return line >= 0 && line < this.map.lines().size()
                        ? this.map.lines().get(line).label(this.map) : "";
            }
            case TRAIN_MAP -> {
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    private float chipGrid(PanelSurface c, Rect body, float y, String[] labels, Action action,
                           int selected, int columns) {
        final float w = (body.w - 6.0f - 4.0f * (columns - 1)) / columns;
        for (int i = 0; i < labels.length; i++) {
            final int col = i % columns;
            final int row = i / columns;
            final Rect chip = new Rect(body.x + col * (w + 4.0f), y + row * 20.0f, w, 18.0f);
            if (chip.bottom() > body.bottom()) {
                break;
            }
            final boolean on = i == selected;
            c.card(chip, Tokens.R_CHIP, on ? Tokens.alpha(Tokens.ACCENT, 0.16f) : Tokens.CANVAS,
                    on ? Tokens.alpha(Tokens.ACCENT, 0.5f) : Tokens.LINE);
            c.textCenterV(Text.truncate(c, labels[i], chip.w - 6.0f, Text.MICRO), chip.centerX(),
                    chip.centerY(), on ? Tokens.ACCENT : Tokens.INK_2, Text.MICRO);
            this.hits.add(new Hit(chip, action, i));
        }
        return y + ((labels.length + columns - 1) / columns) * 20.0f;
    }

    private float faceFlagRow(PanelSurface c, Rect body, float y, String label, int bit,
                              Mk3Settings settings) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        final boolean on = settings.faceFlag(bit);
        c.textV(Text.truncate(c, label, row.w - 34.0f, Text.MICRO), row.x, row.centerY(),
                on ? Tokens.INK : Tokens.INK_2, Text.MICRO);
        switchGlyph(c, row.right() - 24.0f, row.centerY(), on);
        this.hits.add(new Hit(row, Action.FACE_FLAG, bit));
        return y + 21.0f;
    }

    private Rect overlayRect(float width) {
        return TrainPageLayout.overlay(this.area, this.controlArea, this.contentArea, width,
                switch (this.overlay) {
                    case OVERLAY_VIEW -> 211.0f;
                    case OVERLAY_FACE -> 396.0f;
                    default -> 340.0f;
                });
    }

    private float section(PanelSurface c, Rect body, float y, String title) {
        c.text(Text.track(title), body.x, y + 2.0f, Tokens.INK_3, Text.MICRO);
        c.hairlineH(body.x, y + 14.0f, body.w - 6.0f, Tokens.LINE);
        return y + 20.0f;
    }

    private float flagRow(PanelSurface c, Rect body, float y, String label, boolean on,
                          Action action) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        if (body.intersect(row).isEmpty()) {
            return y + 21.0f;
        }
        c.textV(Text.truncate(c, label, row.w - 34.0f, Text.MICRO), row.x, row.centerY(),
                on ? Tokens.INK : Tokens.INK_2, Text.MICRO);
        switchGlyph(c, row.right() - 24.0f, row.centerY(), on);
        this.hits.add(new Hit(row, action, null));
        return y + 21.0f;
    }

    private float viewToggle(PanelSurface c, Rect body, float y, String label, boolean on,
                             int which) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        c.textV(Text.truncate(c, label, row.w - 34.0f, Text.MICRO), row.x, row.centerY(),
                on ? Tokens.INK : Tokens.INK_2, Text.MICRO);
        switchGlyph(c, row.right() - 24.0f, row.centerY(), on);
        this.hits.add(new Hit(row, Action.VIEW_TOGGLE, which));
        return y + 21.0f;
    }

    private float ruleRow(PanelSurface c, Rect body, float y, RailFilter.Rule rule) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        if (body.intersect(row).isEmpty()) {
            return y + 21.0f;
        }
        final int ink = rule.hidden ? Tokens.INK_3 : Tokens.INK_2;
        c.textV(Text.truncate(c, rule.label(), row.w - 56.0f, Text.MICRO), row.x + 20.0f,
                row.centerY(), ink, Text.MICRO);
        eyeGlyph(c, row.x + 8.0f, row.centerY(), !rule.hidden);
        this.hits.add(new Hit(new Rect(row.x, row.y, row.w - 22.0f, row.h), Action.TOGGLE_RULE,
                rule));

        final Rect drop = new Rect(row.right() - 18.0f, row.y, 18.0f, row.h);
        c.textCenterV("x", drop.centerX(), drop.centerY(), Tokens.alpha(Tokens.INK_3, 0.7f),
                Text.MICRO);
        this.hits.add(new Hit(drop, Action.DROP_RULE, rule));
        return y + 21.0f;
    }

    private float groupRow(PanelSurface c, Rect body, float y, String pattern) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        if (body.intersect(row).isEmpty()) {
            return y + 21.0f;
        }
        c.textV(Text.truncate(c, pattern, row.w - 30.0f, Text.MICRO), row.x + 4.0f, row.centerY(),
                Tokens.INK_2, Text.MICRO);
        final Rect drop = new Rect(row.right() - 18.0f, row.y, 18.0f, row.h);
        c.textCenterV("x", drop.centerX(), drop.centerY(), Tokens.alpha(Tokens.INK_3, 0.7f),
                Text.MICRO);
        this.hits.add(new Hit(drop, Action.DROP_GROUP, pattern));
        return y + 21.0f;
    }

    private float note(PanelSurface c, Rect body, float y, String text) {
        c.textV(Text.truncate(c, text, body.w - 8.0f, Text.MICRO), body.x + 2.0f, y + 9.0f,
                Tokens.alpha(Tokens.INK_3, 0.7f), Text.MICRO);
        return y + 18.0f;
    }

    private float addRow(PanelSurface c, Rect body, float y, String label, Action action,
                         int target) {
        final Rect row = new Rect(body.x, y, body.w - 6.0f, 20.0f);
        if (body.intersect(row).isEmpty()) {
            return y + 22.0f;
        }
        final boolean editing = this.patternField.editing() && this.patternTarget == target;
        if (editing) {
            this.patternField.setBounds(row);
            this.patternField.setVisible(true);
        } else {
            c.textV("+  " + label, row.x + 2.0f, row.centerY(),
                    Tokens.alpha(Tokens.ACCENT, 0.85f), Text.MICRO);
            this.hits.add(new Hit(row, action, null));
        }
        return y + 22.0f;
    }

    private void headerAction(PanelSurface c, float rightX, float y, String label, Action action) {
        final float w = c.textWidth(label, Text.MICRO);
        c.textRight(label, rightX, y, Tokens.alpha(Tokens.ACCENT, 0.9f), Text.MICRO);
        this.hits.add(new Hit(new Rect(rightX - w - 4.0f, y - 4.0f, w + 8.0f, 16.0f), action, null));
    }

    private void switchGlyph(PanelSurface c, float x, float cy, boolean on) {
        final Rect track = new Rect(x, cy - 5.0f, 22.0f, 10.0f);
        c.roundedRect(track, 5.0f, on ? Tokens.alpha(Tokens.ACCENT, 0.4f) : Tokens.LINE);
        Shapes.circle(c, on ? track.right() - 5.0f : track.x + 5.0f, cy, 3.6f,
                on ? Tokens.ACCENT : Tokens.INK_3);
    }

    private void eyeGlyph(PanelSurface c, float cx, float cy, boolean open) {
        final int color = open ? Tokens.INK_2 : Tokens.alpha(Tokens.INK_3, 0.6f);
        if (open) {
            Shapes.circle(c, cx, cy, 3.4f, Tokens.alpha(color, 0.35f));
            Shapes.circle(c, cx, cy, 1.6f, color);
        } else {
            Shapes.rect(c, cx - 4.0f, cy - 0.5f, 8.0f, 1.0f, color);
        }
    }

    private int trainColor(RailSnapshot.TrainState t) {
        if (t.unhealthy()) {
            return Tokens.CRITICAL;
        }
        if (t.waitingForSignal) {
            return Tokens.CAUTION;
        }
        return TrainGraphArt.trainColor(t, t.id);
    }

    private void commitPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            this.patternField.setVisible(false);
            return;
        }
        if (this.patternTarget == PATTERN_GROUP) {
            final List<String> groups = new ArrayList<>(this.ctx.panel.getTrainMapGroups());

            final String cleaned = pattern.trim();
            final String stored = cleaned.contains("*") ? cleaned : cleaned + " *";
            if (!groups.contains(stored)) {
                groups.add(stored);
                commitGroups(groups);
            }
            this.patternField.setVisible(false);
            return;
        }
        final RailFilter filter = RailFilter.get();
        final RailFilter.Rule rule = this.patternTarget == PATTERN_TRAIN
                ? filter.offerTrain(pattern) : filter.offerStation(pattern);

        if (rule != null && !rule.hidden) {
            filter.toggle(rule);
        }
        filter.flush();
        this.patternField.setVisible(false);
    }

    private void commitGroups(List<String> groups) {
        this.ctx.panel.setTrainMapGroups(groups);
        PacketDistributor.sendToServer(
                new TrainMapGroupPacket(this.ctx.panel.getBlockPos(), groups));
        this.builtRoster = Integer.MIN_VALUE;
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (this.overlay != OVERLAY_NONE && !this.overlayArea.isEmpty()
                && !this.overlayArea.contains(mx, my) && !this.controlArea.contains(mx, my)) {
            closeOverlay();
            return true;
        }

        for (int i = this.hits.size() - 1; i >= 0; i--) {
            final Hit hit = this.hits.get(i);
            if (hit.rect().contains(mx, my) && handle(hit)) {
                return true;
            }
        }
        if (this.mode == NETWORK && this.plotArea.contains(mx, my)) {
            this.mapView.press(mx, my,
                    this.mapView.nodeAt(this.map, this.layout, mx, my, 13.0f), this.layout);
            return true;
        }
        if (this.mode == DIAGRAM && this.plotArea.contains(mx, my)) {
            return selectOnDiagram(mx, my);
        }
        return false;
    }

    private boolean handle(Hit hit) {
        final RailFilter filter = RailFilter.get();
        switch (hit.action()) {
            case SELECT_TRAIN -> {
                final UUID id = (UUID) hit.payload();
                if (id == null) {
                    return false;
                }
                this.selectedTrain = id.equals(this.selectedTrain) ? null : id;
                this.graph.setSelectedTrain(this.selectedTrain);
                return true;
            }
            case SELECT_STATION -> {
                this.selectedStation = (UUID) hit.payload();
                this.boardScroll.jumpTo(0.0f);
                return true;
            }
            case BACK -> {
                if (Boolean.TRUE.equals(hit.payload())) {
                    this.selectedStation = null;
                } else {
                    this.selectedTrain = null;
                    this.graph.setSelectedTrain(null);
                }
                return true;
            }
            case SELECT_SERVICE -> {
                final int index = (Integer) hit.payload();
                this.selectedService = index == this.selectedService ? -1 : index;
                return true;
            }
            case OPEN_SERVICE -> {
                this.selectedService = (Integer) hit.payload();
                switchMode(SERVICE);
                return true;
            }
            case MAP_FIT -> {
                this.mapView.fit(this.map, this.layout, false);
                return true;
            }
            case MAP_ME -> {
                this.mapView.centreOnPlayer();
                return true;
            }
            case FOLLOW -> {
                this.mapView.toggleFollow((UUID) hit.payload());
                return true;
            }
            case OPEN_BOARD -> {
                this.selectedStation = (UUID) hit.payload();
                this.boardScroll.jumpTo(0.0f);
                switchMode(BOARD);
                return true;
            }
            case FLEET_SORT -> {
                this.fleetSort = (this.fleetSort + 1) % FLEET_SORTS.length;
                this.listScroll.jumpTo(0.0f);
                return true;
            }
            case SELECT_LINE -> {
                final int index = (Integer) hit.payload();
                this.mapView.selectLine(this.mapView.selectedLine() == index ? -1 : index);
                if (this.mapView.selectedLine() >= 0) {
                    this.mapView.fitLine(this.map, this.layout, index);
                }
                return true;
            }
            case OPEN_FILTERS -> {
                this.overlay = this.overlay == OVERLAY_FILTERS ? OVERLAY_NONE : OVERLAY_FILTERS;
                this.panelScroll.jumpTo(0.0f);
                this.patternField.cancel();
                return true;
            }
            case OPEN_VIEW -> {
                this.overlay = this.overlay == OVERLAY_VIEW ? OVERLAY_NONE : OVERLAY_VIEW;
                return true;
            }
            case CLOSE_OVERLAY -> {
                closeOverlay();
                return true;
            }
            case TOGGLE_RULE -> {
                filter.toggle((RailFilter.Rule) hit.payload());
                filter.flush();
                return true;
            }
            case DROP_RULE -> {
                filter.remove((RailFilter.Rule) hit.payload());
                filter.flush();
                return true;
            }
            case ADD_STATION_RULE -> {
                this.patternTarget = PATTERN_STATION;
                this.patternField.begin("");
                return true;
            }
            case ADD_TRAIN_RULE -> {
                this.patternTarget = PATTERN_TRAIN;
                this.patternField.begin("");
                return true;
            }
            case ADD_GROUP -> {
                this.patternTarget = PATTERN_GROUP;
                this.patternField.begin("");
                return true;
            }
            case DROP_GROUP -> {
                final List<String> groups = new ArrayList<>(this.ctx.panel.getTrainMapGroups());
                if (groups.remove((String) hit.payload())) {
                    commitGroups(groups);
                }
                return true;
            }
            case SHOW_ALL -> {
                filter.showEverything();
                filter.flush();
                return true;
            }
            case FLAG_DOCKED -> {
                filter.setHideDocked(!filter.hideDocked());
                filter.flush();
                return true;
            }
            case FLAG_PARKED -> {
                filter.setHideParked(!filter.hideParked());
                filter.flush();
                return true;
            }
            case FLAG_PROBLEMS -> {
                filter.setOnlyProblems(!filter.onlyProblems());
                filter.flush();
                return true;
            }
            case KPI -> {
                final KpiTarget target = (KpiTarget) hit.payload();
                if (target.train() != null) {
                    this.selectedTrain = target.train();
                    this.graph.setSelectedTrain(this.selectedTrain);
                    this.mapView.selectTrain(this.selectedTrain);
                }
                switchMode(target.mode());
                return true;
            }
            case FULL_MAP -> {
                openFullMap();
                return true;
            }
            case MAP_MODE -> {
                this.mapView.setMode((Integer) hit.payload() == 1
                        ? MapLayout.Mode.SCHEMATIC : MapLayout.Mode.GEOGRAPHIC);
                this.mapView.fit(this.map, this.layout, false);
                return true;
            }
            case GRAPH_LINE -> {
                this.graph.setLine((Integer) hit.payload());
                return true;
            }
            case GRAPH_SPAN -> {
                this.graph.setSpanIndex((Integer) hit.payload());
                return true;
            }
            case GRAPH_LIVE -> {
                this.graph.goLive();
                return true;
            }
            case GRAPH_SPACING -> {
                this.graph.toggleSpacing();
                return true;
            }
            case GRAPH_PLANNED -> {
                this.graph.togglePlanned();
                return true;
            }
            case GRAPH_MEETS -> {
                this.graph.toggleConflicts();
                return true;
            }
            case GRAPH_HOLDS -> {
                this.graph.toggleHolds();
                return true;
            }
            case GRAPH_GRID -> {
                this.graph.toggleGrid();
                return true;
            }
            case VIEW_TOGGLE -> {
                return viewToggleAction(hit.payload());
            }
            case FACE_BOARD -> {
                toggleFace((String) hit.payload());
                return true;
            }
            case OPEN_FACE -> {
                this.overlay = this.overlay == OVERLAY_FACE ? OVERLAY_NONE : OVERLAY_FACE;
                this.patternField.setVisible(false);
                return true;
            }
            case FACE_MODE -> {
                if (hit.payload() instanceof Integer index && index >= 0
                        && index < FACE_MODES.length) {
                    this.ctx.settings().faceMode = FACE_MODES[index];
                    this.ctx.commit();
                }
                return true;
            }
            case FACE_FOCUS -> {
                if (hit.payload() instanceof Integer index) {
                    final Mk3Settings.MapFocus[] all = Mk3Settings.MapFocus.values();
                    if (index >= 0 && index < all.length) {
                        this.ctx.settings().mapFocus = all[index];
                        this.ctx.commit();
                    }
                }
                return true;
            }
            case FACE_FLAG -> {
                if (hit.payload() instanceof Integer bit) {
                    this.ctx.settings().toggleFaceFlag(bit);
                    this.ctx.commit();
                }
                return true;
            }
            case FACE_TARGET -> {
                setFaceTarget((String) hit.payload(), false);
                return true;
            }
            case FACE_ADD_STATION -> {
                setFaceTarget((String) hit.payload(), true);
                return true;
            }
            case FACE_SET -> {
                if (hit.payload() instanceof FaceSet set) {
                    final Mk3Settings settings = this.ctx.settings();

                    settings.faceMode = set.showing()
                            ? Mk3Settings.FaceMode.INSTRUMENTS : set.mode();
                    if (!set.showing() && !set.target().isEmpty()) {
                        setFaceTarget(set.target(), false);
                    }
                    this.ctx.commit();
                }
                return true;
            }
            case FACE_DIAGRAM -> {
                final var settings = this.ctx.settings();
                final String label = (String) hit.payload();
                final boolean showing = settings.faceMode == Mk3Settings.FaceMode.TRAIN_DIAGRAM
                        && label.equals(settings.diagramLine);
                settings.faceMode = showing
                        ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.TRAIN_DIAGRAM;
                settings.diagramLine = showing ? "" : label;
                this.ctx.commit();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean viewToggleAction(Object payload) {
        if (!(payload instanceof Integer which)) {
            return false;
        }
        switch (which) {
            case 0 -> this.mapView.toggleTrack();
            case 1 -> this.mapView.toggleLabels();
            case 2 -> this.mapView.toggleGrid();
            case 3 -> this.mapView.toggleAnimate();
            case 5 -> this.mapView.toggleDimIdle();
            default -> {
                this.mapView.setAutoGroup(!this.mapView.autoGroup());
                this.builtRoster = Integer.MIN_VALUE;
            }
        }
        return true;
    }

    private void setFaceTarget(String name, boolean add) {
        if (name == null || name.isEmpty()) {
            return;
        }
        final Mk3Settings settings = this.ctx.settings();
        switch (settings.faceMode) {
            case TRAIN_BOARD -> {
                if (add && !settings.boardStation.isEmpty()) {
                    for (final String part : settings.boardStation.split(",")) {
                        if (part.trim().equalsIgnoreCase(name)) {
                            return;
                        }
                    }
                    settings.boardStation = settings.boardStation + ", " + name;
                } else {
                    settings.boardStation = name;
                }
            }
            case TRAIN_ROUTE, TRAIN_DIAGRAM -> settings.diagramLine = name;
            case TRAIN_FOCUS -> settings.focusTrain = name;
            default -> {
                return;
            }
        }
        this.ctx.commit();
    }

    private void toggleFace(String stationName) {
        final var settings = this.ctx.settings();
        final boolean showing = settings.faceMode == Mk3Settings.FaceMode.TRAIN_BOARD
                && stationName.equals(settings.boardStation);
        settings.faceMode = showing
                ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.TRAIN_BOARD;
        settings.boardStation = showing ? "" : stationName;
        this.ctx.commit();
    }

    private void closeOverlay() {
        this.overlay = OVERLAY_NONE;
        this.patternField.cancel();
        this.patternField.setVisible(false);
        RailFilter.get().flush();
    }

    private void openFullMap() {
        final Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new TrainMapScreen(this.ctx.panel, mc.screen));
    }

    private boolean selectOnMap(double mx, double my) {
        final UUID train = this.mapView.trainAt(this.map, mx, my, 12.0f);
        if (train != null) {
            this.mapView.selectTrain(train);
            this.selectedTrain = this.mapView.selectedTrain();
            this.graph.setSelectedTrain(this.selectedTrain);
            return true;
        }
        final int node = this.mapView.nodeAt(this.map, this.layout, mx, my, 13.0f);
        if (node >= 0) {
            this.mapView.selectNode(node);
            final RailMap.Node picked = this.map.node(node);

            if (picked != null && !picked.members.isEmpty()) {
                RailSnapshot.Station chosen = picked.members.get(0);
                for (final RailSnapshot.Station s : picked.members) {
                    if (s.presentTrain != null || s.imminentTrain != null) {
                        chosen = s;
                        break;
                    }
                }
                this.selectedStation = chosen.id;
            }
            return true;
        }
        return false;
    }

    private boolean selectOnDiagram(double mx, double my) {
        final RailMap sheet = graphMap();
        final RailMap.Line line = this.graph.line() < 0
                || this.graph.line() >= sheet.lines().size()
                ? null : sheet.lines().get(this.graph.line());
        if (line == null) {
            return false;
        }
        final var chainage = RailHistory.chainage(sheet, this.graph.line());
        final float now = RailHistory.latestTime();
        if (Float.isNaN(now) || chainage.isEmpty()) {
            return false;
        }
        final Rect plot = this.graphFrame.plot();
        final Rect lane = this.graphFrame.lane();
        if (plot.isEmpty()) {
            return false;
        }
        final float time = this.graph.timeAt((float) mx, now, plot.x, plot.w);

        UUID best = null;
        float bestGap = 26.0f;
        for (final UUID id : line.trains) {
            final RailHistory.Series series = RailHistory.of(id);
            if (series == null) {
                continue;
            }
            for (int i = 0; i < series.size(); i++) {
                if (Math.abs(series.time(i) - time) > 1.0f) {
                    continue;
                }
                final float y = this.graph.yOf(series.chain(i), chainage, lane.y, lane.h);
                final float gap = Math.abs(y - (float) my);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = id;
                }
            }
        }
        if (best == null) {
            return false;
        }
        this.graph.selectTrain(best);
        this.selectedTrain = this.graph.selectedTrain();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW_ESCAPE) {
            if (this.searchField.editing() || !this.searchField.text().isEmpty()) {
                this.searchField.cancel();
                this.searchField.setText("");
                this.mapView.setSearch("");
                return true;
            }
            if (this.overlay != OVERLAY_NONE) {
                closeOverlay();
                return true;
            }
            return false;
        }
        if (keyCode == GLFW_SLASH && !this.searchField.editing() && !this.patternField.editing()
                && !this.searchArea.isEmpty()) {
            this.searchField.begin(this.searchField.text());
            return true;
        }
        if (keyCode == GLFW_F && this.mode == NETWORK && this.selectedTrain != null
                && !this.searchField.editing() && !this.patternField.editing()) {
            this.mapView.toggleFollow(this.selectedTrain);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.hoverX = mx;
        this.hoverY = my;
        final Rect plot = this.graphFrame.plot();
        final Rect lane = this.graphFrame.lane();
        if (this.mode == DIAGRAM && !plot.isEmpty() && plot.contains(mx, my)) {
            final float now = RailHistory.latestTime();
            final var chainage = RailHistory.chainage(graphMap(), this.graph.line());
            if (!Float.isNaN(now) && !chainage.isEmpty()) {
                this.graph.setCursor(this.graph.timeAt((float) mx, now, plot.x, plot.w),
                        this.graph.chainAt((float) my, chainage, lane.y, lane.h));
            }
        } else {
            this.graph.clearCursor();
        }
        return super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        if (this.mode == DIAGRAM && this.plotArea.contains(mx, my)) {
            final float perPixel = (this.graph.span() + this.graph.future())
                    / Math.max(1.0f, Math.max(1.0f, this.graphFrame.plot().w));
            this.graph.scrollBy((float) dx * perPixel, RailHistory.span());
            return true;
        }
        if (this.mapView.gesture() == MapView.Gesture.NONE) {
            return false;
        }
        this.mapView.drag(mx, my, button != 0, this.layout, this.lastDelta);
        return true;
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        if (this.mapView.gesture() != MapView.Gesture.NONE) {
            final boolean wasClick = this.mapView.release(this.layout);
            if (this.layout.anyPinned() || !MapPins.all().isEmpty()) {
                MapPins.adopt(this.layout.pins());
                MapPins.flush();
            }
            if (wasClick && button == 0) {
                selectOnMap(mx, my);
            }
            return true;
        }
        return super.mouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        if (this.overlay != OVERLAY_NONE && this.overlayArea.contains(mx, my)) {
            return this.panelScroll.wheel(amount);
        }
        if (this.mode == NETWORK && this.plotArea.contains(mx, my)) {
            this.mapView.zoomAt(mx, my, amount > 0 ? 1.16f : 0.862f);
            return true;
        }
        if (this.mode == DIAGRAM && this.plotArea.contains(mx, my)) {
            this.graph.zoom(amount > 0);
            return true;
        }
        if (this.mode == BOARD && this.detailArea.contains(mx, my)) {
            return this.boardScroll.wheel(amount);
        }
        if (this.mode == NETWORK && this.inspectorArea.contains(mx, my)) {
            return this.serviceScroll.wheel(amount);
        }
        return this.contentArea.contains(mx, my) && this.listScroll.wheel(amount);
    }
}
