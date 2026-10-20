package com.createstats.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.TrainMapGroupPacket;
import com.createstats.statsmk3.ui.DrawCtx;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.MapLayout;
import com.createstats.train.map.MapView;
import com.createstats.train.map.RailMap;
import com.createstats.train.map.ClientRailMap;
import com.createstats.train.map.RailHistory;
import com.createstats.train.map.RailMapArt;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.map.TrainGraphView;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailFilter;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class TrainMapScreen extends Screen {
    private static final int DIM = 0xC0070A0E;
    private static final float SIDEBAR_W = 250.0f;
    private static final float TITLEBAR_H = 30.0f;
    private static final float TOOLBAR_H = 28.0f;
    private static final float STRIP_H = 68.0f;

    private static final String[] TABS = {"Lines", "Fleet", "Stations", "Alerts"};

    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;

    private final MapView view = new MapView();
    private final Scroll sidebarScroll = new Scroll();

    private RailMap map = RailMap.flat(RailSnapshot.EMPTY);
    private MapLayout layout = new MapLayout(this.map);
    private int builtChecksum = Integer.MIN_VALUE;
    private String builtSignature = "";

    private Rect root = Rect.EMPTY;
    private Rect mapArea = Rect.EMPTY;
    private Rect plotArea = Rect.EMPTY;
    private Rect sidebarArea = Rect.EMPTY;
    private Rect toolbarArea = Rect.EMPTY;
    private Rect stripArea = Rect.EMPTY;

    private int tab;

    private boolean diagram;
    private final TrainGraphView graph = new TrainGraphView();
    private TrainGraphArt.Frame graphFrame = TrainGraphArt.Frame.EMPTY;

    private long lastFrameNanos;
    private float lastDelta = 1.0f / 60.0f;

    private long lastClickAt;
    private double lastClickX;
    private double lastClickY;

    private final List<String> groupPatterns = new ArrayList<>();
    private boolean groupEditorOpen;
    private String groupDraft = "";
    private boolean groupDraftFocused;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Rect> legendHits = new ArrayList<>();

    private record Hit(Rect rect, UUID trainId, Integer nodeIndex, Integer tabIndex, Action action,
                       String payload) {
    }

    private enum Action {
        NONE, TAB, SEARCH, DIMENSION, MODE_GEO, MODE_SCHEMATIC, MODE_STRIP, TOGGLE_GRID,
        TOGGLE_LABELS, TOGGLE_ANIMATE, TOGGLE_GROUPING, TOGGLE_TRACK, TOGGLE_DIM_IDLE, FIT,
        FOLLOW, ZOOM_IN,
        ZOOM_OUT, RESET_PINS, CENTRE_PLAYER, GROUP_EDITOR, GROUP_ADD, GROUP_REMOVE, GROUP_EXPAND, LINE,
        DIAGRAM, GRAPH_SPAN, GRAPH_SPACING, GRAPH_PLANNED, GRAPH_MEETS, GRAPH_LIVE, SHOW_ALL,
        FRAME_STALL
    }

    public TrainMapScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Train Network Map"));
        this.panel = panel;
        this.parentScreen = parentScreen;
        if (panel != null) {
            this.groupPatterns.addAll(panel.getTrainMapGroups());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        final float margin = Math.max(12.0f, Math.min(this.width, this.height) / 22.0f);
        this.root = new Rect(margin, margin, this.width - margin * 2.0f,
                this.height - margin * 2.0f);

        final Rect body = this.root.inset(0.0f, TITLEBAR_H, 0.0f, 0.0f);

        final float sidebar = Math.min(SIDEBAR_W, Math.max(160.0f, body.w * 0.28f));
        this.sidebarArea = new Rect(body.right() - sidebar, body.y, sidebar, body.h);
        this.mapArea = new Rect(body.x, body.y, body.w - sidebar, body.h);
        this.toolbarArea = this.mapArea.top(TOOLBAR_H);
        recomputePlot();
    }

    private void recomputePlot() {
        final boolean strip = this.view.selectedTrain() != null;
        this.stripArea = strip
                ? new Rect(this.mapArea.x + 6.0f, this.mapArea.bottom() - STRIP_H - 4.0f,
                this.mapArea.w - 12.0f, STRIP_H)
                : Rect.EMPTY;
        final float bottomInset = strip ? STRIP_H + 10.0f : 6.0f;
        this.plotArea = new Rect(this.mapArea.x + 6.0f, this.toolbarArea.bottom(),
                this.mapArea.w - 12.0f,
                this.mapArea.h - TOOLBAR_H - bottomInset);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final float dt = frameDelta();
        ClientRailNetwork.requestUpdates();
        if (this.panel != null) {
            this.panel.markTrainMapActive();
        }
        rebuildIfChanged();
        recomputePlot();
        updatePlayerMark();
        this.view.setViewport(this.plotArea);
        this.view.update(dt, this.map, this.layout);
        this.sidebarScroll.update(dt);

        ClientRailMap.sampled();

        final DrawCtx c = new DrawCtx(g, this.font);
        g.fill(0, 0, this.width, this.height, DIM);
        c.shadow(this.root.inset(-6.0f), Tokens.R_PANEL + 6.0f, 22.0f, 0xB0000000);
        c.card(this.root, Tokens.R_PANEL, Tokens.SURFACE, Tokens.LINE_HI);

        this.hits.clear();
        updateHover(mouseX, mouseY);

        drawTitlebar(c);
        drawToolbar(c);

        c.clip(this.plotArea);
        if (this.diagram) {
            final RailMap sheet = ClientRailMap.get();
            this.graph.ensureLine(sheet);
            this.graphFrame = TrainGraphArt.draw(c, this.plotArea, sheet, this.graph);
        } else {
            this.graphFrame = TrainGraphArt.Frame.EMPTY;
            RailMapArt.draw(c, this.plotArea, this.map, this.layout, this.view);
            drawHint(c);
        }
        c.unclip();
        drawEmptyState(c);
        drawArrangeControls(c);
        drawStallBanner(c);
        drawHoverCard(c, mouseX, mouseY);

        drawStrip(c);
        drawSidebar(c, mouseX, mouseY);
        if (this.groupEditorOpen) {
            drawGroupEditor(c, mouseX, mouseY);
        }

        c.finish();
        super.render(g, mouseX, mouseY, partialTick);
    }

    // refold only when the roster signature changes
    private void rebuildIfChanged() {
        final RailSnapshot full = RailFilter.get().apply(ClientRailNetwork.get());
        final RailSnapshot snap = this.view.dimension().isEmpty()
                ? full : full.inDimension(this.view.dimension());

        final String signature = this.groupPatterns + "|" + this.view.expandedGroups()
                + "|" + this.view.autoGroup() + "|" + this.view.dimension()
                + "|" + RailFilter.get().revision();
        final int roster = RailMap.rosterSignature(snap);
        if (roster == this.builtChecksum && signature.equals(this.builtSignature)) {
            this.map.refresh(snap);
            return;
        }
        this.builtChecksum = roster;
        this.builtSignature = signature;
        if (this.view.grabbedNode() >= 0) {
            // refold renumbers nodes, cancel the drag or the wrong station moves

            this.view.cancelGesture();
        }

        this.map = RailMap.build(snap, this.groupPatterns, this.view.autoGroup(),
                this.view.expandedGroups());
        this.layout = new MapLayout(this.map);
        // reapply pins every refold or hand layouts rearrange themselves next packet

        this.layout.restorePins(MapPins.all());
        if (this.view.mode() == MapLayout.Mode.STRIP) {
            this.layout.buildStrip(stripLine());
        }
        if (!this.view.everFitted() && !this.map.isEmpty()) {
            this.view.fit(this.map, this.layout, true);
        }
    }

    private void updatePlayerMark() {
        final net.minecraft.client.player.LocalPlayer player =
                this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            this.view.clearPlayer();
            return;
        }
        final String dimension = player.level().dimension().location().toString();
        final String shown = this.view.dimension();
        if (!shown.isEmpty() && !shown.equals(dimension)) {
            this.view.clearPlayer();
            return;
        }
        this.view.setPlayer((float) player.getX(), (float) player.getZ(), player.getYRot());
    }

    private int stripLine() {
        if (this.view.selectedLine() >= 0) {
            return this.view.selectedLine();
        }
        if (this.view.selectedTrain() != null) {
            return this.map.lineIndexOf(this.view.selectedTrain());
        }
        return this.map.lines().isEmpty() ? -1 : 0;
    }

    private void selectOnDiagram(double mx, double my) {
        final Rect plot = this.graphFrame.plot();
        final Rect lane = this.graphFrame.lane();
        final RailMap sheet = ClientRailMap.get();
        if (plot.isEmpty() || this.graph.line() < 0
                || this.graph.line() >= sheet.lines().size()) {
            return;
        }
        final var chainage = RailHistory.chainage(sheet, this.graph.line());
        final float now = RailHistory.latestTime();
        if (Float.isNaN(now) || chainage.isEmpty()) {
            return;
        }
        final float time = this.graph.timeAt((float) mx, now, plot.x, plot.w);
        UUID best = null;
        float bestGap = 26.0f;
        for (final UUID id : sheet.lines().get(this.graph.line()).trains) {
            final RailHistory.Series series = RailHistory.of(id);
            if (series == null) {
                continue;
            }
            for (int i = 0; i < series.size(); i++) {
                if (Math.abs(series.time(i) - time) > 1.0f) {
                    continue;
                }
                final float gap = Math.abs(
                        this.graph.yOf(series.chain(i), chainage, lane.y, lane.h) - (float) my);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = id;
                }
            }
        }
        this.graph.selectTrain(best);
        if (this.graph.selectedTrain() != null) {
            this.view.selectTrain(this.graph.selectedTrain());
        }
    }

    private void updateHover(int mouseX, int mouseY) {
        if (!this.plotArea.contains(mouseX, mouseY) || this.groupEditorOpen) {
            this.view.setHover(-1, null);
            this.view.clearCursor();
            this.graph.clearCursor();
            return;
        }
        if (this.diagram) {
            this.view.setHover(-1, null);
            this.view.clearCursor();
            final Rect plot = this.graphFrame.plot();
            final Rect lane = this.graphFrame.lane();
            final float now = RailHistory.latestTime();
            final var chainage = RailHistory.chainage(ClientRailMap.get(), this.graph.line());
            if (!plot.isEmpty() && !Float.isNaN(now) && !chainage.isEmpty()) {
                this.graph.setCursor(this.graph.timeAt(mouseX, now, plot.x, plot.w),
                        this.graph.chainAt(mouseY, chainage, lane.y, lane.h));
            }
            return;
        }
        this.graph.clearCursor();
        this.view.setCursor(this.view.layoutX(mouseX), this.view.layoutY(mouseY));
        if (this.view.dragging()) {
            this.view.setHover(-1, null);
            return;
        }
        final UUID train = this.view.trainAt(this.map, mouseX, mouseY, 11.0f);
        this.view.setHover(train != null ? -1
                : this.view.nodeAt(this.map, this.layout, mouseX, mouseY, 12.0f), train);
    }

    private void drawArrangeControls(DrawCtx c) {
        if (this.diagram || !MapLayout.arrangeable(this.view.mode())
                || !this.layout.anyPinned()) {
            return;
        }
        final String label = "Reset arrangement";
        final float w = c.textWidth(label, Text.MICRO) + 18.0f;
        final Rect chip = new Rect(this.plotArea.x + 8.0f, this.plotArea.y + 8.0f, w, 18.0f);
        c.card(chip, Tokens.R_CHIP, Tokens.RAISED, Tokens.alpha(Tokens.ACCENT, 0.45f));
        c.textCenterV(label, chip.centerX(), chip.centerY(), Tokens.INK_2, Text.MICRO);
        this.hits.add(new Hit(chip, null, null, null, Action.RESET_PINS, ""));
    }

    private void drawStallBanner(DrawCtx c) {
        if (this.diagram || this.plotArea.isEmpty()) {
            return;
        }
        final TrainIntel.Stall worst = TrainIntel.worstStall(
                TrainIntel.stalls(this.map.snapshot()));
        if (worst == null) {
            return;
        }
        final int held = worst.longestHold;
        final String hold = TrainIntel.duration(held / TrainIntel.TICKS_PER_SECOND);
        final String label;
        if (worst.circular) {
            label = worst.trains.size() + " trains deadlocked  .  " + hold + "  .  show me";
        } else {
            final int queued = Math.max(1, worst.trains.size() - 1);
            final String head = worst.names.isEmpty()
                    ? "a train" : worst.names.get(worst.names.size() - 1);
            label = queued + (queued == 1 ? " train" : " trains") + " held behind " + head
                    + "  .  show me";
        }
        final int accent = worst.circular ? Tokens.CRITICAL : Tokens.CAUTION;
        final float w = c.textWidth(label, Text.MICRO) + 26.0f;
        final Rect chip = new Rect(this.plotArea.centerX() - w * 0.5f, this.plotArea.y + 8.0f,
                w, 20.0f);
        c.card(chip, Tokens.R_CHIP, Tokens.alpha(accent, 0.14f), Tokens.alpha(accent, 0.6f));
        Shapes.circle(c, chip.x + 11.0f, chip.centerY(), 3.0f, accent);
        c.textCenterV(label, chip.centerX() + 6.0f, chip.centerY(), accent, Text.MICRO);
        this.hits.add(new Hit(chip, null, null, null, Action.FRAME_STALL, ""));
    }

    private TrainIntel.Stall stallOf(UUID trainId) {
        if (trainId == null) {
            return null;
        }
        for (final TrainIntel.Stall stall : TrainIntel.stalls(this.map.snapshot())) {
            if (stall.trains.contains(trainId)) {
                return stall;
            }
        }
        return null;
    }

    private void frameWorstStall() {
        final TrainIntel.Stall worst = TrainIntel.worstStall(
                TrainIntel.stalls(this.map.snapshot()));
        if (worst == null) {
            return;
        }
        this.view.frameTrains(worst.trains, 0.6f);

        this.view.selectTrain(worst.circular ? null : worst.cause());
    }

    private void drawEmptyState(DrawCtx c) {
        if (this.diagram || !this.map.isEmpty()) {
            return;
        }
        c.textCenterV("No railway on this network", this.plotArea.centerX(),
                this.plotArea.centerY() - 8.0f, Tokens.INK_2, Text.BODY);
        c.textCenterV("Build a Train Station to see it here", this.plotArea.centerX(),
                this.plotArea.centerY() + 12.0f, Tokens.INK_3, Text.META);
    }

    private void drawHoverCard(DrawCtx c, int mouseX, int mouseY) {
        if (this.groupEditorOpen || this.diagram || !this.plotArea.contains(mouseX, mouseY)) {
            return;
        }
        if (this.view.hoverTrain() != null) {
            final RailSnapshot.TrainState t = this.map.snapshot().train(this.view.hoverTrain());
            if (t != null) {
                RailMapArt.drawTrainTooltip(c, this.plotArea, this.map, t, mouseX, mouseY);
            }
            return;
        }
        final RailMap.Node node = this.map.node(this.view.hoverNode());
        if (node != null) {
            RailMapArt.drawNodeTooltip(c, this.plotArea, this.map, node, mouseX, mouseY);
        }
    }

    private void drawTitlebar(DrawCtx c) {
        final Rect bar = this.root.top(TITLEBAR_H);
        c.hairlineH(bar.x + 1.0f, bar.bottom() - 1.0f, bar.w - 2.0f, Tokens.LINE);

        final float x = bar.x + Tokens.S3;
        c.textV("TRAIN ", x, bar.centerY(), Tokens.INK, Text.META);
        c.textV("NETWORK", x + c.textWidth("TRAIN ", Text.META), bar.centerY(), Tokens.ACCENT,
                Text.META);

        final TrainIntel.Summary s = TrainIntel.summarise(this.map.snapshot());
        final StringBuilder counts = new StringBuilder();
        counts.append(this.map.nodes().size()).append(" stations");
        final int folded = this.map.snapshot().stations().size() - this.map.nodes().size();
        if (folded > 0) {
            counts.append(" (").append(this.map.snapshot().stations().size()).append(" platforms)");
        }
        counts.append("  .  ").append(s.trains).append(" trains  .  ")
                .append(this.map.lines().size()).append(" lines");
        c.textV(counts.toString(), x + c.textWidth("TRAIN NETWORK", Text.META) + Tokens.S4,
                bar.centerY(), Tokens.INK_3, Text.MICRO);

        final boolean live = ClientRailNetwork.isLive();
        final int liveColor = live ? Tokens.GOOD : Tokens.CAUTION;
        final String liveLabel = live ? "LIVE" : "STALE";
        final float liveW = c.textWidth(liveLabel, Text.MICRO) + 22.0f;
        final Rect pill = new Rect(bar.right() - Tokens.S3 - liveW, bar.centerY() - 9.0f,
                liveW, 18.0f);
        c.card(pill, 9.0f, Tokens.alpha(liveColor, 0.10f), Tokens.alpha(liveColor, 0.42f));
        Shapes.circle(c, pill.x + 10.0f, pill.centerY(), 3.0f, liveColor);
        c.textV(liveLabel, pill.x + 17.0f, pill.centerY(), liveColor, Text.MICRO);

        final float afterFilter = drawFilterChip(c, pill.x - Tokens.S3, bar.centerY());
        drawDimensionChip(c, afterFilter, bar.centerY());
    }

    private float drawFilterChip(DrawCtx c, float rightX, float centerY) {
        final int hidden = RailFilter.get().hiddenCount();
        if (hidden <= 0) {
            return rightX;
        }
        final String label = hidden + " hidden  .  show all";
        final float w = c.textWidth(label, Text.MICRO) + 20.0f;
        final Rect chip = new Rect(rightX - w, centerY - 9.0f, w, 18.0f);
        c.card(chip, 9.0f, Tokens.alpha(Tokens.CAUTION, 0.12f), Tokens.alpha(Tokens.CAUTION, 0.45f));
        c.textCenterV(label, chip.centerX(), chip.centerY(), Tokens.CAUTION, Text.MICRO);
        this.hits.add(new Hit(chip, null, null, null, Action.SHOW_ALL, ""));
        return chip.x - Tokens.S3;
    }

    private void drawDimensionChip(DrawCtx c, float rightX, float centerY) {
        final List<String> dims = new ArrayList<>(ClientRailNetwork.get().dimensions());
        if (dims.size() < 2) {
            return;
        }
        final String label = this.view.dimension().isEmpty() ? "All dimensions"
                : shortDimension(this.view.dimension());
        final float w = c.textWidth(label, Text.MICRO) + 26.0f;
        final Rect chip = new Rect(rightX - w, centerY - 9.0f, w, 18.0f);
        c.card(chip, 9.0f, Tokens.RAISED, Tokens.LINE);
        c.textCenterV(label, chip.centerX(), chip.centerY(), Tokens.INK_2, Text.MICRO);
        this.hits.add(new Hit(chip, null, null, null, Action.DIMENSION, ""));
    }

    private static String shortDimension(String id) {
        final int colon = id.indexOf(':');
        final String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    private void drawToolbar(DrawCtx c) {
        final Rect bar = this.toolbarArea;
        float x = bar.x + 6.0f;
        final float y = bar.centerY() - 9.0f;

        x = chip(c, x, y, this.diagram ? "Diagram" : "Map", Action.DIAGRAM, true, "") + 10.0f;

        if (this.diagram) {
            drawDiagramToolbar(c, bar, x, y);
            return;
        }

        final MapLayout.Mode[] modes = MapLayout.Mode.values();
        final Action[] actions = {Action.MODE_GEO, Action.MODE_SCHEMATIC, Action.MODE_STRIP};
        for (int i = 0; i < modes.length; i++) {
            x = chip(c, x, y, modes[i].label, actions[i], this.view.mode() == modes[i], "") + 4.0f;
        }

        c.textV(this.view.mode().hint, x + 8.0f, bar.centerY(), Tokens.alpha(Tokens.INK_3, 0.85f),
                Text.MICRO);

        float rx = bar.right() - 6.0f;
        rx = zoomButtons(c, rx, y);
        rx = toggle(c, rx, y, "Fit", Action.FIT, false);
        if (this.view.hasPlayer()) {
            rx = toggle(c, rx, y, "Me", Action.CENTRE_PLAYER, false);
        }
        rx = toggle(c, rx, y, "Group", Action.TOGGLE_GROUPING, this.view.autoGroup());
        rx = toggle(c, rx, y, "Flow", Action.TOGGLE_ANIMATE, this.view.animate());
        rx = toggle(c, rx, y, "Names", Action.TOGGLE_LABELS, this.view.showLabels());
        rx = toggle(c, rx, y, "Rail", Action.TOGGLE_TRACK, this.view.showTrack());
        rx = toggle(c, rx, y, "Idle", Action.TOGGLE_DIM_IDLE, !this.view.dimIdle());
        toggle(c, rx, y, "Grid", Action.TOGGLE_GRID, this.view.showGrid());
    }

    private void drawDiagramToolbar(DrawCtx c, Rect bar, float startX, float y) {
        float x = startX;
        for (int i = 0; i < TrainGraphView.SPANS.length; i++) {
            x = chip(c, x, y, TrainGraphView.SPAN_LABELS[i], Action.GRAPH_SPAN,
                    this.graph.spanIndex() == i, String.valueOf(i)) + 3.0f;
        }
        c.textV("distance against time  .  drag to look back", x + 8.0f, bar.centerY(),
                Tokens.alpha(Tokens.INK_3, 0.85f), Text.MICRO);

        float rx = bar.right() - 6.0f;
        if (!this.graph.live()) {
            rx = toggle(c, rx, y, "Live", Action.GRAPH_LIVE, true);
        }
        rx = toggle(c, rx, y, "Meets", Action.GRAPH_MEETS, this.graph.showConflicts());
        rx = toggle(c, rx, y, "Forecast", Action.GRAPH_PLANNED, this.graph.showPlanned());
        toggle(c, rx, y, "To scale", Action.GRAPH_SPACING, !this.graph.evenSpacing());
    }

    private float chip(DrawCtx c, float x, float y, String label, Action action, boolean active,
                       String payload) {
        final float w = c.textWidth(label, Text.MICRO) + 18.0f;
        final Rect box = new Rect(x, y, w, 18.0f);
        c.card(box, Tokens.R_CHIP, active ? Tokens.alpha(Tokens.ACCENT, 0.14f) : Tokens.CANVAS,
                active ? Tokens.alpha(Tokens.ACCENT, 0.5f) : Tokens.LINE);
        c.textCenterV(label, box.centerX(), box.centerY(), active ? Tokens.ACCENT : Tokens.INK_3,
                Text.MICRO);
        this.hits.add(new Hit(box, null, null,
                payload.isEmpty() ? null : Integer.valueOf(payload), action, payload));
        return box.right();
    }

    private float zoomButtons(DrawCtx c, float rightX, float y) {
        final Rect in = new Rect(rightX - 19.0f, y, 19.0f, 18.0f);
        final Rect out = new Rect(in.x - 20.0f, y, 19.0f, 18.0f);
        for (int i = 0; i < 2; i++) {
            final Rect box = i == 0 ? out : in;
            c.card(box, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
            c.textCenterV(i == 0 ? "-" : "+", box.centerX(), box.centerY(), Tokens.INK_2,
                    Text.MICRO);
            this.hits.add(new Hit(box, null, null, null,
                    i == 0 ? Action.ZOOM_OUT : Action.ZOOM_IN, ""));
        }
        return out.x - 6.0f;
    }

    private void drawHint(DrawCtx c) {
        if (this.map.isEmpty()) {
            return;
        }
        final String hint = MapLayout.arrangeable(this.view.mode())
                ? "Drag the map to move it  .  drag a station to arrange it  .  right click to reset it"
                : "Drag to move  .  scroll to zoom  .  double click to zoom in  .  R to fit";
        c.textV(hint, this.plotArea.x + 8.0f, this.plotArea.bottom() - 24.0f,
                Tokens.alpha(Tokens.INK_3, this.view.dragging() ? 0.28f : 0.6f), Text.MICRO);
    }

    private float toggle(DrawCtx c, float rightX, float y, String label, Action action, boolean on) {
        final float w = c.textWidth(label, Text.MICRO) + 16.0f;
        final Rect chip = new Rect(rightX - w, y, w, 18.0f);
        c.card(chip, Tokens.R_CHIP, on ? Tokens.RAISED : Tokens.CANVAS,
                on ? Tokens.LINE_HI : Tokens.LINE);
        c.textCenterV(label, chip.centerX(), chip.centerY(), on ? Tokens.INK_2 : Tokens.INK_3,
                Text.MICRO);
        this.hits.add(new Hit(chip, null, null, null, action, ""));
        return chip.x - 4.0f;
    }

    private void drawStrip(DrawCtx c) {
        if (this.stripArea.isEmpty()) {
            return;
        }
        final RailSnapshot.TrainState t = this.map.snapshot().train(this.view.selectedTrain());
        if (t == null) {
            return;
        }
        c.card(this.stripArea, Tokens.R_CARD, Tokens.CANVAS, Tokens.LINE);
        RailMapArt.drawRouteStrip(c, this.stripArea, this.map, t);

        final String label = t.id.equals(this.view.followTrain()) ? "Following" : "Follow";
        final float w = c.textWidth(label, Text.MICRO) + 18.0f;
        final Rect button = new Rect(this.stripArea.right() - w - 8.0f, this.stripArea.y + 6.0f,
                w, 16.0f);
        final boolean on = t.id.equals(this.view.followTrain());
        c.card(button, 8.0f, Tokens.alpha(on ? Tokens.ACCENT : Tokens.INK_3, on ? 0.16f : 0.08f),
                Tokens.alpha(on ? Tokens.ACCENT : Tokens.LINE_HI, 0.6f));
        c.textCenterV(label, button.centerX(), button.centerY(), on ? Tokens.ACCENT : Tokens.INK_3,
                Text.MICRO);
        this.hits.add(new Hit(button, t.id, null, null, Action.FOLLOW, ""));
    }

    private void drawSidebar(DrawCtx c, int mouseX, int mouseY) {
        final Rect a = this.sidebarArea;
        if (a.isEmpty()) {
            return;
        }
        c.hairlineV(a.x, a.y + 1.0f, a.h - 2.0f, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);

        drawSearch(c, new Rect(inner.x, inner.y, inner.w, 22.0f));

        final Rect tabRow = new Rect(inner.x, inner.y + 28.0f, inner.w, 20.0f);
        final float tabW = tabRow.w / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final Rect t = new Rect(tabRow.x + i * tabW, tabRow.y, tabW, tabRow.h);
            final boolean active = i == this.tab;
            if (active) {
                c.roundedRect(t.inset(1.0f), Tokens.R_CHIP, Tokens.RAISED);
                Shapes.rect(c, t.x + 5.0f, t.bottom() - 2.0f, t.w - 10.0f, 1.5f, Tokens.ACCENT);
            }
            c.textCenterV(TABS[i], t.centerX(), t.centerY(), active ? Tokens.INK : Tokens.INK_3,
                    Text.MICRO);
            this.hits.add(new Hit(t, null, null, i, Action.TAB, ""));
        }

        final Rect list = new Rect(inner.x, tabRow.bottom() + Tokens.S2, inner.w,
                inner.bottom() - tabRow.bottom() - Tokens.S2);
        if (list.isEmpty()) {
            return;
        }
        c.clip(list);
        switch (this.tab) {
            case 1 -> drawFleetList(c, list);
            case 2 -> drawStationList(c, list);
            case 3 -> drawAlertList(c, list);
            default -> drawLineList(c, list);
        }
        c.unclip();
    }

    private void drawSearch(DrawCtx c, Rect box) {
        final boolean focused = this.view.searchFocused();
        c.card(box, Tokens.R_CHIP, focused ? Tokens.RAISED : Tokens.CANVAS,
                focused ? Tokens.ACCENT : Tokens.LINE);
        final String search = this.view.search();
        final String shown = search.isEmpty() && !focused
                ? "Search stations  /" : search + (focused ? "_" : "");
        c.textV(Text.truncate(c, shown, box.w - 16.0f, Text.MICRO), box.x + 8.0f, box.centerY(),
                search.isEmpty() && !focused ? Tokens.INK_3 : Tokens.INK, Text.MICRO);
        this.hits.add(new Hit(box, null, null, null, Action.SEARCH, ""));
    }

    private void drawLineList(DrawCtx c, Rect list) {
        final float contentH = this.map.lines().size() * RailMapArt.LEGEND_ROW_H + 46.0f;
        this.sidebarScroll.setMetrics(contentH, list.h);
        final Rect scrolled = new Rect(list.x, list.y - this.sidebarScroll.value(), list.w,
                Math.max(list.h, contentH));

        final int[] order = RailMapArt.drawLegend(c, scrolled, this.map, this.view,
                this.legendHits);
        for (int i = 0; i < this.legendHits.size() && i < order.length; i++) {
            this.hits.add(new Hit(this.legendHits.get(i), null, null, order[i], Action.LINE, ""));
        }

        final float y = scrolled.y + this.map.lines().size() * RailMapArt.LEGEND_ROW_H + 8.0f;
        if (y + 30.0f < list.bottom() + this.sidebarScroll.value() + list.h) {
            final Rect button = new Rect(scrolled.x, y, scrolled.w, 20.0f);
            c.card(button, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
            c.textCenterV("Station groups", button.centerX(), button.centerY(), Tokens.INK_2,
                    Text.MICRO);
            this.hits.add(new Hit(button, null, null, null, Action.GROUP_EDITOR, ""));
        }
        this.sidebarScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawFleetList(DrawCtx c, Rect list) {
        final List<RailSnapshot.TrainState> trains = new ArrayList<>(this.map.snapshot().trains());
        trains.sort(Comparator
                .comparingInt((RailSnapshot.TrainState t) -> t.unhealthy() ? 0
                        : t.waitingForSignal ? 1 : t.moving() ? 2 : 3)
                .thenComparing(t -> t.name.toLowerCase(Locale.ROOT)));
        if (trains.isEmpty()) {
            c.textCenterV("No trains", list.centerX(), list.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        final float rowH = 34.0f;
        this.sidebarScroll.setMetrics(trains.size() * rowH, list.h);
        for (int i = 0; i < trains.size(); i++) {
            final RailSnapshot.TrainState t = trains.get(i);
            final Rect row = new Rect(list.x, list.y - this.sidebarScroll.value() + i * rowH,
                    list.w - 5.0f, rowH - 2.0f);
            if (row.bottom() < list.y || row.y > list.bottom()) {
                continue;
            }
            if (t.id.equals(this.view.selectedTrain())) {
                c.roundedRect(row, Tokens.R_CHIP, Tokens.RAISED);
            }
            this.hits.add(new Hit(row, t.id, null, null, Action.NONE, ""));
            final int lineIndex = this.map.lineIndexOf(t.id);
            Shapes.roundedRect(c, new Rect(row.x + 4.0f, row.y + 4.0f, 3.0f, row.h - 8.0f), 1.5f,
                    RailMapArt.trainColor(t, lineIndex));
            c.text(Text.truncate(c, t.name, row.w - 30.0f, Text.META), row.x + 13.0f, row.y + 3.0f,
                    Tokens.INK, Text.META);
            c.text(Text.truncate(c, TrainIntel.status(t), row.w - 30.0f, Text.MICRO),
                    row.x + 13.0f, row.y + 17.0f,
                    t.unhealthy() ? Tokens.CRITICAL : Tokens.INK_3, Text.MICRO);
            if (t.id.equals(this.view.followTrain())) {
                c.textRightV("eye", row.right() - 6.0f, row.centerY(), Tokens.ACCENT, Text.MICRO);
            }
        }
        this.sidebarScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawStationList(DrawCtx c, Rect list) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < this.map.nodes().size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(
                i -> this.map.nodes().get(i).name.toLowerCase(Locale.ROOT)));
        if (order.isEmpty()) {
            c.textCenterV("No stations", list.centerX(), list.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        final float rowH = 34.0f;
        this.sidebarScroll.setMetrics(order.size() * rowH, list.h);
        for (int i = 0; i < order.size(); i++) {
            final int index = order.get(i);
            final RailMap.Node node = this.map.nodes().get(index);
            final Rect row = new Rect(list.x, list.y - this.sidebarScroll.value() + i * rowH,
                    list.w - 5.0f, rowH - 2.0f);
            if (row.bottom() < list.y || row.y > list.bottom()) {
                continue;
            }
            if (index == this.view.selectedNode()) {
                c.roundedRect(row, Tokens.R_CHIP, Tokens.RAISED);
            }
            this.hits.add(new Hit(row, null, index, null, Action.NONE, ""));

            final int dot = node.presentTrain() != null ? Tokens.GOOD
                    : node.imminentTrain() != null ? Tokens.CAUTION : Tokens.LINE_HI;
            Shapes.circle(c, row.x + 9.0f, row.y + 10.0f, 3.0f, dot);
            c.text(Text.truncate(c, node.name, row.w - 40.0f, Text.META), row.x + 17.0f,
                    row.y + 4.0f, Tokens.INK, Text.META);

            String next = "nothing due";
            for (final RailSnapshot.Station s : node.members) {
                final List<TrainIntel.BoardRow> board =
                        TrainIntel.board(this.map.snapshot(), s, 1);
                if (!board.isEmpty()) {
                    next = board.get(0).eta() + "  " + board.get(0).train;
                    break;
                }
            }
            c.text(Text.truncate(c, next, row.w - 40.0f, Text.MICRO), row.x + 17.0f, row.y + 18.0f,
                    Tokens.INK_3, Text.MICRO);

            if (node.isGroup()) {
                final String count = node.members.size() + "x";
                final float w = c.textWidth(count, Text.MICRO) + 12.0f;
                final Rect chip = new Rect(row.right() - w - 4.0f, row.y + 8.0f, w, 14.0f);
                c.card(chip, 7.0f, Tokens.CANVAS, Tokens.LINE);
                c.textCenterV(count, chip.centerX(), chip.centerY(), Tokens.INK_2, Text.MICRO);
                this.hits.add(new Hit(chip, null, null, null, Action.GROUP_EXPAND, node.groupKey));
            }
        }
        this.sidebarScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private void drawAlertList(DrawCtx c, Rect list) {
        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(this.map.snapshot());
        if (alerts.isEmpty()) {
            c.textCenterV("Nothing wrong", list.centerX(), list.centerY() - 8.0f, Tokens.GOOD,
                    Text.META);
            c.textCenterV("every train has a driver and a route", list.centerX(),
                    list.centerY() + 8.0f, Tokens.INK_3, Text.MICRO);
            return;
        }
        final float rowH = 46.0f;
        this.sidebarScroll.setMetrics(alerts.size() * rowH, list.h);
        for (int i = 0; i < alerts.size(); i++) {
            final TrainIntel.Alert alert = alerts.get(i);
            final Rect row = new Rect(list.x, list.y - this.sidebarScroll.value() + i * rowH,
                    list.w - 5.0f, rowH - 4.0f);
            if (row.bottom() < list.y || row.y > list.bottom()) {
                continue;
            }
            final int color = switch (alert.severity) {
                case CRITICAL -> Tokens.CRITICAL;
                case WARN -> Tokens.CAUTION;
                default -> Tokens.INK_3;
            };
            c.card(row, Tokens.R_CHIP, Tokens.alpha(color, 0.07f), Tokens.alpha(color, 0.3f));
            this.hits.add(new Hit(row, alert.trainId, null, null, Action.NONE, ""));
            Shapes.circle(c, row.x + 10.0f, row.y + 11.0f, 3.0f, color);
            c.text(Text.truncate(c, alert.subject, row.w - 24.0f, Text.META), row.x + 18.0f,
                    row.y + 5.0f, Tokens.INK, Text.META);
            final List<String> wrapped = wrap(c, alert.detail, row.w - 24.0f, 2);
            for (int w = 0; w < wrapped.size(); w++) {
                c.text(wrapped.get(w), row.x + 18.0f, row.y + 19.0f + w * 11.0f, Tokens.INK_3,
                        Text.MICRO);
            }
        }
        this.sidebarScroll.drawBar(c, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h));
    }

    private List<String> wrap(DrawCtx c, String text, float width, int maxLines) {
        final List<String> out = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String word : text.split(" ")) {
            final String candidate = current.length() == 0 ? word : current + " " + word;
            if (c.textWidth(candidate, Text.MICRO) > width && current.length() > 0) {
                out.add(current.toString());
                if (out.size() >= maxLines) {
                    return out;
                }
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (current.length() > 0 && out.size() < maxLines) {
            out.add(current.toString());
        }
        return out;
    }

    private void drawGroupEditor(DrawCtx c, int mouseX, int mouseY) {
        final float w = Math.min(330.0f, this.root.w - 40.0f);
        final float h = Math.min(300.0f, this.root.h - 40.0f);
        final Rect box = new Rect(this.root.centerX() - w * 0.5f, this.root.centerY() - h * 0.5f,
                w, h);
        c.shadow(box.inset(-4.0f), Tokens.R_PANEL, 18.0f, 0xA0000000);
        c.card(box, Tokens.R_PANEL, Tokens.OVERLAY, Tokens.LINE_HI);

        this.hits.add(new Hit(box, null, null, null, Action.NONE, ""));

        final Rect in = box.inset(Tokens.S4);
        c.text("Station groups", in.x, in.y, Tokens.INK, Text.SUB);
        c.text("Platforms matching a pattern are drawn as one station.", in.x, in.y + 22.0f,
                Tokens.INK_3, Text.MICRO);

        final Rect field = new Rect(in.x, in.y + 38.0f, in.w - 52.0f, 20.0f);
        c.card(field, Tokens.R_CHIP, this.groupDraftFocused ? Tokens.RAISED : Tokens.CANVAS,
                this.groupDraftFocused ? Tokens.ACCENT : Tokens.LINE);
        final String shown = this.groupDraft.isEmpty() && !this.groupDraftFocused
                ? "Quarry *" : this.groupDraft + (this.groupDraftFocused ? "_" : "");
        c.textV(Text.truncate(c, shown, field.w - 12.0f, Text.MICRO), field.x + 7.0f,
                field.centerY(),
                this.groupDraft.isEmpty() && !this.groupDraftFocused ? Tokens.INK_3 : Tokens.INK,
                Text.MICRO);
        this.hits.add(new Hit(field, null, null, null, Action.SEARCH, "group"));

        final Rect add = new Rect(field.right() + 6.0f, field.y, 46.0f, 20.0f);
        c.card(add, Tokens.R_CHIP, Tokens.alpha(Tokens.ACCENT, 0.14f),
                Tokens.alpha(Tokens.ACCENT, 0.5f));
        c.textCenterV("Add", add.centerX(), add.centerY(), Tokens.ACCENT, Text.MICRO);
        this.hits.add(new Hit(add, null, null, null, Action.GROUP_ADD, ""));

        float y = field.bottom() + 12.0f;
        for (final String pattern : new ArrayList<>(this.groupPatterns)) {
            if (y + 22.0f > in.bottom() - 40.0f) {
                break;
            }
            final Rect row = new Rect(in.x, y, in.w, 20.0f);
            c.roundedRect(row, Tokens.R_CHIP, Tokens.CANVAS);
            c.textV(Text.truncate(c, pattern, row.w - 60.0f, Text.MICRO), row.x + 8.0f,
                    row.centerY(), Tokens.INK_2, Text.MICRO);
            final Rect remove = new Rect(row.right() - 52.0f, row.y + 2.0f, 48.0f, 16.0f);
            c.card(remove, 8.0f, Tokens.alpha(Tokens.CRITICAL, 0.10f),
                    Tokens.alpha(Tokens.CRITICAL, 0.35f));
            c.textCenterV("Remove", remove.centerX(), remove.centerY(), Tokens.CRITICAL,
                    Text.MICRO);
            this.hits.add(new Hit(remove, null, null, null, Action.GROUP_REMOVE, pattern));
            y += 24.0f;
        }

        if (this.groupPatterns.isEmpty()) {
            c.text("No saved patterns. Automatic grouping is "
                            + (this.view.autoGroup() ? "on." : "off."), in.x, y,
                    Tokens.INK_3, Text.MICRO);
            y += 18.0f;
        }

        final List<String> suggestions =
                RailMap.suggestGroups(this.map.snapshot(), this.groupPatterns);
        if (!suggestions.isEmpty() && y + 30.0f < in.bottom() - 26.0f) {
            c.text(Text.track("Found automatically"), in.x, y, Tokens.INK_3, Text.MICRO);
            y += 14.0f;
            for (final String suggestion : suggestions) {
                if (y + 20.0f > in.bottom() - 26.0f) {
                    break;
                }
                final Rect row = new Rect(in.x, y, in.w, 18.0f);
                c.roundedRect(row, Tokens.R_CHIP, Tokens.alpha(Tokens.GOOD, 0.07f));
                c.textV(Text.truncate(c, suggestion, row.w - 20.0f, Text.MICRO), row.x + 8.0f,
                        row.centerY(), Tokens.INK_3, Text.MICRO);
                c.textRightV("save", row.right() - 8.0f, row.centerY(), Tokens.GOOD, Text.MICRO);
                this.hits.add(new Hit(row, null, null, null, Action.GROUP_ADD, suggestion));
                y += 21.0f;
            }
        }

        final Rect close = new Rect(in.x, in.bottom() - 20.0f, in.w, 20.0f);
        c.card(close, Tokens.R_CHIP, Tokens.RAISED, Tokens.LINE_HI);
        c.textCenterV("Done", close.centerX(), close.centerY(), Tokens.INK_2, Text.MICRO);
        this.hits.add(new Hit(close, null, null, null, Action.GROUP_EDITOR, ""));
    }

    private void commitGroups() {
        if (this.panel != null) {
            this.panel.setTrainMapGroups(this.groupPatterns);
            PacketDistributor.sendToServer(
                    new TrainMapGroupPacket(this.panel.getBlockPos(), this.groupPatterns));
        }
        this.builtSignature = "";
    }

    // select on release, only if the pointer didn't move. deciding on press made the map feel nailed down
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = this.hits.size() - 1; i >= 0; i--) {
                final Hit hit = this.hits.get(i);
                if (hit.rect().contains(mx, my) && handle(hit)) {
                    return true;
                }
            }
            if (this.groupEditorOpen) {
                this.groupEditorOpen = false;
                return true;
            }
            this.view.setSearchFocused(false);
            this.groupDraftFocused = false;
        }
        if (this.groupEditorOpen || !this.plotArea.contains(mx, my)) {
            return super.mouseClicked(mx, my, button);
        }
        if (this.diagram) {
            selectOnDiagram(mx, my);
            return true;
        }
        if (button == 1) {
            final int node = this.view.nodeAt(this.map, this.layout, mx, my, 13.0f);
            if (node >= 0 && this.layout.isPinned(node)) {
                this.layout.unpin(node);
                MapPins.adopt(this.layout.pins());
                return true;
            }
        }
        this.view.press(mx, my, this.view.nodeAt(this.map, this.layout, mx, my, 13.0f), this.layout);
        return true;
    }

    private boolean handle(Hit hit) {
        switch (hit.action()) {
            case TAB -> {
                this.tab = hit.tabIndex();
                this.sidebarScroll.jumpTo(0.0f);
            }
            case SEARCH -> {
                final boolean group = "group".equals(hit.payload());
                this.view.setSearchFocused(!group);
                this.groupDraftFocused = group;
            }
            case DIMENSION -> cycleDimension();
            case DIAGRAM -> {
                this.diagram = !this.diagram;
                this.graph.ensureLine(this.map);
                if (this.diagram && this.view.selectedTrain() != null) {
                    this.graph.setSelectedTrain(this.view.selectedTrain());
                    final int line = ClientRailMap.get().lineIndexOf(this.view.selectedTrain());
                    if (line >= 0) {
                        this.graph.setLine(line);
                    }
                }
            }
            case GRAPH_SPAN -> this.graph.setSpanIndex(hit.tabIndex() == null ? 2 : hit.tabIndex());

            case GRAPH_SPACING -> this.graph.toggleSpacing();
            case GRAPH_PLANNED -> this.graph.togglePlanned();
            case GRAPH_MEETS -> this.graph.toggleConflicts();
            case GRAPH_LIVE -> this.graph.goLive();
            case SHOW_ALL -> {
                RailFilter.get().showEverything();
                RailFilter.get().flush();
                this.builtSignature = "";
            }
            case MODE_GEO -> setMode(MapLayout.Mode.GEOGRAPHIC);
            case MODE_SCHEMATIC -> setMode(MapLayout.Mode.SCHEMATIC);
            case MODE_STRIP -> setMode(MapLayout.Mode.STRIP);
            case TOGGLE_GRID -> this.view.toggleGrid();
            case TOGGLE_LABELS -> this.view.toggleLabels();
            case TOGGLE_ANIMATE -> this.view.toggleAnimate();
            case TOGGLE_TRACK -> this.view.toggleTrack();
            case TOGGLE_DIM_IDLE -> this.view.toggleDimIdle();
            case ZOOM_IN -> this.view.zoomBy(1.35f);
            case ZOOM_OUT -> this.view.zoomBy(1.0f / 1.35f);
            case RESET_PINS -> {
                this.layout.clearPins();
                MapPins.adopt(this.layout.pins());
            }
            case TOGGLE_GROUPING -> {
                this.view.setAutoGroup(!this.view.autoGroup());
                this.builtSignature = "";
            }
            case FIT -> this.view.fit(this.map, this.layout, false);
            case CENTRE_PLAYER -> this.view.centreOnPlayer();
            case FOLLOW -> this.view.toggleFollow(hit.trainId());
            case GROUP_EDITOR -> {
                this.groupEditorOpen = !this.groupEditorOpen;
                this.groupDraftFocused = false;
            }
            case GROUP_ADD -> {
                final String pattern = hit.payload().isEmpty() ? this.groupDraft.trim()
                        : hit.payload();
                if (!pattern.isEmpty() && !this.groupPatterns.contains(pattern)) {
                    this.groupPatterns.add(pattern);
                    commitGroups();
                }
                if (hit.payload().isEmpty()) {
                    this.groupDraft = "";
                }
            }
            case GROUP_REMOVE -> {
                this.groupPatterns.remove(hit.payload());
                commitGroups();
            }
            case GROUP_EXPAND -> {
                this.view.toggleGroup(hit.payload());
                this.builtSignature = "";
            }
            case LINE -> {
                this.view.selectLine(hit.tabIndex());
                if (this.view.selectedLine() >= 0) {
                    this.view.fitLine(this.map, this.layout, this.view.selectedLine());
                    if (this.view.mode() == MapLayout.Mode.STRIP) {
                        this.layout.buildStrip(stripLine());
                    }
                }
            }
            case FRAME_STALL -> frameWorstStall();
            default -> {
                if (hit.trainId() != null) {
                    this.view.selectTrain(hit.trainId());
                    final TrainIntel.Stall stall = stallOf(hit.trainId());
                    final MapView.Motion m = this.view.motion(hit.trainId());
                    if (stall != null && this.view.selectedTrain() != null) {
                        this.view.frameTrains(stall.trains, 0.6f);
                    } else if (m != null && this.view.selectedTrain() != null) {
                        this.view.centreOn(m.x, m.y);
                    }
                } else if (hit.nodeIndex() != null) {
                    this.view.selectNode(hit.nodeIndex());
                    if (this.view.selectedNode() >= 0) {
                        centreOnNode(this.view.selectedNode());
                    }
                }
            }
        }
        return true;
    }

    private void setMode(MapLayout.Mode mode) {
        if (mode == MapLayout.Mode.STRIP) {
            this.layout.buildStrip(stripLine());
        }
        this.view.setMode(mode);

        this.view.fit(this.map, this.layout, false);
    }

    private void centreOnNode(int index) {
        this.view.centreOn(this.layout.x(index, this.view.mode()),
                this.layout.y(index, this.view.mode()));
    }

    private boolean selectOnMap(double mx, double my) {
        final UUID train = this.view.trainAt(this.map, mx, my, 12.0f);
        if (train != null) {
            this.view.selectTrain(train);
            return true;
        }
        final int node = this.view.nodeAt(this.map, this.layout, mx, my, 13.0f);
        if (node >= 0) {
            this.view.selectNode(node);
            if (this.view.selectedNode() >= 0) {
                this.tab = 2;
            }
            return true;
        }
        return false;
    }

    private void cycleDimension() {
        final List<String> dims = new ArrayList<>(ClientRailNetwork.get().dimensions());
        if (dims.isEmpty()) {
            return;
        }
        final int current = this.view.dimension().isEmpty() ? -1
                : dims.indexOf(this.view.dimension());
        final int next = current + 1;
        this.view.setDimension(next >= dims.size() ? "" : dims.get(next));
        this.builtSignature = "";
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.diagram && !this.graphFrame.plot().isEmpty()
                && this.plotArea.contains(mx, my)) {
            final float perPixel = (this.graph.span() + this.graph.future())
                    / Math.max(1.0f, this.graphFrame.plot().w);
            this.graph.scrollBy((float) dx * perPixel, RailHistory.span());
            return true;
        }
        if (this.view.gesture() != MapView.Gesture.NONE) {
            this.view.drag(mx, my, button != 0, this.layout, this.lastDelta);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.view.gesture() != MapView.Gesture.NONE) {
            final boolean wasClick = this.view.release(this.layout);
            if (this.layout.anyPinned() || !MapPins.all().isEmpty()) {
                MapPins.adopt(this.layout.pins());
            }
            if (wasClick && button == 0) {
                final long now = System.currentTimeMillis();
                final boolean doubleClick = now - this.lastClickAt < 300L
                        && Math.hypot(mx - this.lastClickX, my - this.lastClickY) < 6.0;
                this.lastClickAt = now;
                this.lastClickX = mx;
                this.lastClickY = my;
                if (doubleClick) {
                    this.view.zoomAt(mx, my, 1.9f);
                } else {
                    selectOnMap(mx, my);
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.groupEditorOpen) {
            return true;
        }
        if (this.plotArea.contains(mx, my)) {
            if (this.diagram) {
                this.graph.zoom(sy > 0);
            } else {
                this.view.zoomAt(mx, my, sy > 0 ? 1.16f : 0.862f);
            }
            return true;
        }
        if (this.sidebarArea.contains(mx, my)) {
            return this.sidebarScroll.wheel(sy);
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.groupDraftFocused) {
            return editText(keyCode, true);
        }
        if (this.view.searchFocused()) {
            return editText(keyCode, false);
        }
        switch (keyCode) {
            case 256 -> {
                if (this.groupEditorOpen) {
                    this.groupEditorOpen = false;
                } else {
                    onClose();
                }
                return true;
            }

            case 82 -> {
                this.view.fit(this.map, this.layout, false);
                return true;
            }
            case 49 -> {
                setMode(MapLayout.Mode.GEOGRAPHIC);
                return true;
            }
            case 50 -> {
                setMode(MapLayout.Mode.SCHEMATIC);
                return true;
            }
            case 51 -> {
                setMode(MapLayout.Mode.STRIP);
                return true;
            }
            case 71 -> {
                this.view.toggleGrid();
                return true;
            }
            case 76 -> {
                this.view.toggleLabels();
                return true;
            }
            case 70 -> {
                if (this.view.selectedTrain() != null) {
                    this.view.toggleFollow(this.view.selectedTrain());
                }
                return true;
            }

            case 47 -> {
                this.view.setSearchFocused(true);
                this.tab = 2;
                return true;
            }
            case 84 -> {
                this.view.toggleTrack();
                return true;
            }

            case 263, 65 -> {
                this.view.nudge(-panStep(), 0.0f);
                return true;
            }
            case 262, 68 -> {
                this.view.nudge(panStep(), 0.0f);
                return true;
            }
            case 265, 87 -> {
                this.view.nudge(0.0f, -panStep());
                return true;
            }
            case 264, 83 -> {
                this.view.nudge(0.0f, panStep());
                return true;
            }

            case 61, 334 -> {
                this.view.zoomBy(1.35f);
                return true;
            }
            case 45, 333 -> {
                this.view.zoomBy(1.0f / 1.35f);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private boolean editText(int keyCode, boolean group) {
        if (keyCode == 256) {
            if (group) {
                this.groupDraftFocused = false;
                this.groupDraft = "";
            } else {
                this.view.setSearchFocused(false);
                this.view.setSearch("");
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (group) {
                final String pattern = this.groupDraft.trim();
                if (!pattern.isEmpty() && !this.groupPatterns.contains(pattern)) {
                    this.groupPatterns.add(pattern);
                    commitGroups();
                }
                this.groupDraft = "";
            } else {
                this.view.setSearchFocused(false);

                final List<Integer> found = this.view.searchHits(this.map);
                if (!found.isEmpty()) {
                    this.view.selectNode(found.get(0));
                    centreOnNode(found.get(0));
                }
            }
            return true;
        }
        if (keyCode == 259) {
            if (group && !this.groupDraft.isEmpty()) {
                this.groupDraft = this.groupDraft.substring(0, this.groupDraft.length() - 1);
            } else if (!group && !this.view.search().isEmpty()) {
                this.view.setSearch(
                        this.view.search().substring(0, this.view.search().length() - 1));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (this.groupDraftFocused && this.groupDraft.length() < 48 && c >= ' ') {
            this.groupDraft += c;
            return true;
        }
        if (this.view.searchFocused() && this.view.search().length() < 48 && c >= ' ') {
            this.view.setSearch(this.view.search() + c);
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private float frameDelta() {
        final long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
            return 1.0f / 60.0f;
        }
        final float dt = (now - this.lastFrameNanos) / 1.0e9f;
        this.lastFrameNanos = now;
        this.lastDelta = Math.max(1.0f / 240.0f, Math.min(dt, 0.05f));
        return Math.max(0.0f, Math.min(dt, 0.05f));
    }

    private float panStep() {
        return Math.max(40.0f, this.plotArea.w * 0.25f);
    }

    @Override
    public void onClose() {
        MapPins.flush();
        if (this.minecraft != null && this.parentScreen != null) {
            this.minecraft.setScreen(this.parentScreen);
            return;
        }
        super.onClose();
    }
}
