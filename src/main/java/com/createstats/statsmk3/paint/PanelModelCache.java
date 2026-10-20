package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.SourceNames;
import com.createstats.statsmk3.ui.Spring;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PanelModelCache {
    private static final Map<BlockPos, PanelModelCache> INSTANCES = new HashMap<>();
    private static final long IDLE_EVICT_NANOS = 10_000_000_000L;

    private final Map<String, Spring> springs = new HashMap<>();
    private final Map<String, ChannelView> views = new HashMap<>();
    private final PanelModel model = new PanelModel();
    private long lastTouched;
    private long lastBuildNanos;
    private long buildRevision;

    private PanelModelCache() {
    }

    public static PanelModelCache get(BlockPos pos) {
        return INSTANCES.computeIfAbsent(pos.immutable(), p -> new PanelModelCache());
    }

    public static void dispose(BlockPos pos) {
        INSTANCES.remove(pos);
    }

    public static void disposeAll() {
        INSTANCES.clear();
    }

    public static void evictIdle() {
        final long now = System.nanoTime();
        final Iterator<Map.Entry<BlockPos, PanelModelCache>> it = INSTANCES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastTouched > IDLE_EVICT_NANOS) {
                it.remove();
            }
        }
    }

    public PanelModel build(StatsDisplayPanelBE be, int historyBudget, float clientTime) {
        final long now = System.nanoTime();
        final float dt = this.lastBuildNanos == 0L
                ? 1.0f / 60.0f
                : Math.max(0.0f, Math.min((now - this.lastBuildNanos) / 1.0e9f, 0.05f));
        this.lastBuildNanos = now;
        this.lastTouched = now;
        this.model.channels.clear();
        this.buildRevision++;

        final Mk3PanelBlockEntity panel = be instanceof Mk3PanelBlockEntity mk3 ? mk3 : null;
        final Mk3Settings settings = panel != null ? panel.mk3Settings() : new Mk3Settings();
        applyStyle(this.model.style, settings);
        this.model.instrumentId = settings.instrument;
        this.model.autoTiny = settings.autoTiny;
        this.model.autoWide = settings.autoWide;
        this.model.autoSquare = settings.autoSquare;
        this.model.faceMode = settings.faceMode;
        this.model.boardStation = settings.boardStation;
        this.model.diagramLine = settings.diagramLine;
        this.model.focusTrain = settings.focusTrain;
        this.model.mapFocus = settings.mapFocus;
        this.model.mapRadius = settings.mapRadius;
        this.model.faceFlags = settings.faceFlags;
        this.model.storageKind = settings.storageKind;
        this.model.time = clientTime;

        if (be != null) {
            this.model.faceX = be.getBlockPos().getX() + 0.5;
            this.model.faceZ = be.getBlockPos().getZ() + 0.5;
        }

        this.model.storage.clear();
        if (settings.faceMode == Mk3Settings.FaceMode.STORAGE) {
            this.model.storage.addAll(safeStores(be));
        }
        this.model.topology.clear();
        if (settings.faceMode == Mk3Settings.FaceMode.TOPOLOGY) {
            resolveTopology(be);
        }
        this.model.refinedStorage.clear();
        this.model.rsFocusItem = settings.rsFocusItem;
        this.model.rsWindow = settings.rsWindow;
        if (settings.faceMode == Mk3Settings.FaceMode.REFINED_STORAGE) {
            this.model.refinedStorage.addAll(safeRefinedStorage(be));
        }
        this.model.efficiency = null;
        this.model.efficiencyTrace = null;
        this.model.efficiencyTrend = null;
        if (settings.faceMode == Mk3Settings.FaceMode.EFFICIENCY && be != null) {
            this.model.efficiency = be.getLatestEfficiencyScore();
            this.model.efficiencyTrace = be.getLatestEfficiencyTrace();
            final com.createstats.efficiency.GradeHistory history = be.getGradeHistory();
            this.model.efficiencyTrend = history == null ? null : history.getSparklineData(60);
        }
        this.model.aero = null;
        this.model.aeroLinked = false;
        if (settings.faceMode.isAero() && be != null) {
            final StatsDisplayPanelBE controller = be.getControllerBE() != null ? be.getControllerBE() : be;
            this.model.aeroLinked = controller.aerialLinks().hasLinks();
            this.model.aero = controller.aerialLinks().feed().frame(System.nanoTime());
        }
        this.model.drive = null;
        this.model.driveLinked = false;
        this.model.driveLook = settings.driveLook;
        if (settings.faceMode.isDrive() && be != null) {
            final StatsDisplayPanelBE controller = be.getControllerBE() != null ? be.getControllerBE() : be;
            this.model.driveLinked = controller.groundLinks().hasLinks();
            final long tick = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
            this.model.drive = controller.groundLinks().feed().frame(System.nanoTime(), tick, 0.0f);
        }
        this.model.survey = null;
        if (settings.faceMode.isSurvey() && be != null) {
            final StatsDisplayPanelBE controller = be.getControllerBE() != null ? be.getControllerBE() : be;
            final long tick = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
            controller.seismicLinks().requestIfNeeded(tick);
            this.model.survey = com.createstats.statsmk3.page.SurveyArt.modelOf(controller, settings, tick);
        }

        com.createstats.netscan.client.NetscanClient.tickWatch();

        final List<StatsDisplayPanelBE.DataChannel> channels = safeChannels(be);
        boolean anyAlerting = false;

        for (final StatsDisplayPanelBE.DataChannel raw : channels) {
            final ChannelView view = toView(raw, dt, historyBudget, settings.timeRangeSeconds);
            if (view != null) {
                this.model.channels.add(view);
                anyAlerting |= view.alerting;
            }
        }
        this.views.values().removeIf(view -> view.usedInBuild != this.buildRevision);
        this.springs.keySet().removeIf(id -> !this.views.containsKey(id));

        if (panel != null) {
            settings.ensureLayout(panel.layoutCols(), panel.layoutRows(), this.model.channels.size());
        } else if (settings.layout.isEmpty()) {
            settings.ensureLayout(settings.layout.cols(), settings.layout.rows(),
                    this.model.channels.size());
        }
        this.model.layout = settings.layout;
        LayoutResolver.bind(this.model);

        this.model.title = titleFor(this.model, safeRegistry(be));
        if (this.model.channels.isEmpty()) {
            this.model.status = "NO DATA";
            this.model.statusColor = com.createstats.statsmk3.ui.Tokens.INK_3;
        } else if (anyAlerting) {
            this.model.status = "ALERT";
            this.model.statusColor = com.createstats.statsmk3.ui.Tokens.CRITICAL;
        } else {
            this.model.status = "LIVE";
            this.model.statusColor = com.createstats.statsmk3.ui.Tokens.GOOD;
        }
        this.model.timeRange = com.createstats.statsmk3.Mk3Settings.TIME_RANGE_LABELS[
                com.createstats.statsmk3.Mk3Settings.timeRangeIndex(settings.timeRangeSeconds)];
        this.model.helperFiring = anyAlerting;
        fillLamps(this.model, settings);
        return this.model;
    }

    private static String titleFor(PanelModel model, Map<String, SourceConfig> registry) {
        if (model.channels.isEmpty()) {
            return "Stats Display";
        }
        if (model.slots.size() <= 1) {
            final ChannelView primary = LayoutResolver.primary(model) == null
                    ? model.channels.get(0) : LayoutResolver.primary(model).channel;
            return primary == null ? model.channels.get(0).label : primary.label;
        }
        String source = null;
        for (final PanelModel.Slot slot : model.slots) {
            if (slot.channel == null) {
                continue;
            }
            final String id = sourceIdOf(slot.channel.id);
            if (source == null) {
                source = id;
            } else if (!source.equals(id)) {
                return model.channels.size() + " signals";
            }
        }
        if (source == null || source.isEmpty()) {
            return model.channels.size() + " signals";
        }
        final SourceConfig config = registry.get(source);

        return config == null ? pretty(source) : SourceNames.label(config, false, false);
    }

    private static Map<String, SourceConfig> safeRegistry(StatsDisplayPanelBE be) {
        try {
            final Map<String, SourceConfig> registry = be == null ? null : be.getSourceRegistry();
            return registry == null ? Map.of() : registry;
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }

    private static String sourceIdOf(String channelId) {
        if (channelId != null && channelId.startsWith("src_")) {
            final int auto = channelId.indexOf("_auto_");
            if (auto > 4) {
                return channelId.substring(4, auto);
            }
        }
        return channelId == null ? "" : channelId;
    }

    private static String pretty(String raw) {
        final String s = raw.replace('_', ' ').trim();
        return s.isEmpty() ? "Source" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<com.createstats.statsmk3.page.RsReadings.Board> safeRefinedStorage(
            StatsDisplayPanelBE be) {
        try {
            return com.createstats.statsmk3.page.RsReadings.boardsOf(be);
        } catch (final RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static List<com.createstats.statsmk3.page.StorageReadings.Store> safeStores(
            StatsDisplayPanelBE be) {
        try {
            return com.createstats.statsmk3.page.StorageReadings.storesOf(be);
        } catch (final RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private void resolveTopology(StatsDisplayPanelBE be) {
        try {
            final java.util.List<net.minecraft.core.BlockPos> scanners =
                    com.createstats.netscan.NetscanLink.scannersFor(be);
            for (final net.minecraft.core.BlockPos pos : scanners) {
                if (be.getLevel() != null && be.getLevel().isLoaded(pos)
                        && be.getLevel().getBlockEntity(pos)
                                instanceof com.createstats.netscan.NetscanHost host) {
                    this.model.topology.add(com.createstats.statsmk3.page.TopoArt.readingOf(host));
                }
            }
            com.createstats.netscan.client.NetscanClient.watchFace(be.getBlockPos(), scanners);
            com.createstats.netscan.client.NetscanClient.tickWatch();
        } catch (final RuntimeException e) {
            this.model.topology.clear();
        }
    }

    private static List<StatsDisplayPanelBE.DataChannel> safeChannels(StatsDisplayPanelBE be) {
        try {
            final List<StatsDisplayPanelBE.DataChannel> visible = be.getVisibleChannels();
            return visible == null ? new ArrayList<>() : visible;
        } catch (final RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private ChannelView toView(StatsDisplayPanelBE.DataChannel raw, float dt, int historyBudget,
                               int timeRangeSeconds) {
        if (raw == null || raw.getId() == null) {
            return null;
        }
        final TimeSeriesStore store = raw.getStore();
        if (store == null) {
            return null;
        }
        final ChannelView view = this.views.computeIfAbsent(raw.getId(), ChannelView::new);
        view.usedInBuild = this.buildRevision;
        view.label = raw.getLabel() == null ? raw.getId() : raw.getLabel();

        final MetricType type = store.getMetricType();
        view.unit = type == null || type.getUnit() == null ? "" : type.getUnit();

        final float latest = store.getLatestValue();
        final Spring spring = this.springs.computeIfAbsent(raw.getId(), id -> {
            final Spring s = Spring.ui(latest);
            s.jump(latest);
            return s;
        });
        view.value = spring.set(latest).step(dt);

        if (historyBudget > 0) {
            final int budget = timeRangeSeconds <= 0
                    ? historyBudget
                    : Math.min(historyBudget, store.getSampleCountForSeconds(timeRangeSeconds));
            final int wanted = Math.min(store.getSampleCount(), Math.max(1, budget));
            final long revision = store.getRevision();
            if (view.historyRevision != revision || view.historyBudget != wanted) {
                view.ensureHistoryCapacity(wanted);
                view.historyCount = store.copyRecent(view.history, wanted);
                view.historyRevision = revision;
                view.historyBudget = wanted;
            }
        } else {
            view.historyCount = 0;
            view.historyBudget = 0;
        }

        float hi = Math.max(latest, store.getMax());
        if (!Float.isFinite(hi) || hi <= 0.0f) {
            hi = Math.max(1.0f, Math.abs(latest));
        }
        float lo = Math.min(0.0f, store.getMin());
        if (!Float.isFinite(lo)) {
            lo = 0.0f;
        }
        view.min = lo;
        view.max = hi * 1.08f + 1.0e-3f;

        view.alerting = raw.isAlertEnabled() && raw.isAlertTriggered();
        return view;
    }

    private static void applyStyle(PanelStyle style, Mk3Settings s) {
        style.ground = s.ground;
        style.accent = s.accent;
        style.grid = s.grid;
        style.glass = s.glass;
        style.glow = s.glow;
        style.density = s.density;
        style.faceScale = s.faceScale;
    }

    private static void fillLamps(PanelModel model, Mk3Settings settings) {
        for (int i = 0; i < model.lamps.length; i++) {
            final String bound = i < settings.lampChannels.length ? settings.lampChannels[i] : "";
            if (bound == null || bound.isEmpty()) {
                final ChannelView ch = i < model.channels.size() ? model.channels.get(i) : null;
                model.lamps[i] = ch == null ? 0 : (ch.alerting ? 2 : 1);
                continue;
            }
            int state = 0;
            for (final ChannelView ch : model.channels) {
                if (ch.id.equals(bound)) {
                    state = ch.alerting ? 2 : 1;
                    break;
                }
            }
            model.lamps[i] = state;
        }
    }
}
