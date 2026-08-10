package com.createstats.client;

import com.createstats.CreateStats;
import java.lang.invoke.CallSite;
import java.lang.reflect.UndeclaredThrowableException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.function.Supplier;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.network.PanelActivityPacket;
import com.createstats.network.TrainMapGroupPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Iterator;
import java.nio.charset.StandardCharsets;
import com.createstats.display.DisplayTheme;
import com.createstats.train.TrainMapLayoutHelper;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import com.createstats.ModConfiguration;
import com.createstats.train.TrainMapData;
import com.createstats.train.TrainMapViewMode;
import net.minecraft.client.gui.components.EditBox;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class TrainMapScreen extends Screen
{
    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private float panX;
    private float panY;
    private float zoom;
    private float initialZoom;
    private boolean panning;
    private double panStartMX;
    private double panStartMY;
    private float panStartPX;
    private float panStartPY;
    private boolean batchActive;
    private BufferBuilder batchBuilder;
    private Matrix4f batchMatrix;
    private final List<Object[]> deferredLabels;
    private final Map<UUID, float[]> layoutPos;
    private final Map<UUID, float[]> targetLayoutPos;
    private Set<UUID> lastLayoutIds;
    private final List<String> groupPatterns;
    private final Map<String, List<UUID>> groupMembers;
    private final Map<UUID, String> stationToGroup;
    private final Set<String> expandedGroups;
    private final Map<String, UUID> groupUUIDs;
    private Map<UUID, UUID> effectiveMap;
    private final List<int[]> routeInfoStops;
    private final List<String> routeKeys;
    private final List<List<String>> routeStops;
    private final List<Integer> routeColors;
    private final List<List<UUID>> routeTrains;
    private final Map<String, Integer> routeColorByKey;
    private final Map<String, Integer> trainToRoute;
    private final Map<String, List<Integer>> edgeRoutes;
    private final Map<UUID, List<Integer>> nodeRoutes;
    private final Map<String, UUID[]> edgeCanonicalDir;
    private Integer selectedRoute;
    private int legendScroll;
    private EditBox groupInput;
    private boolean showGroupPopup;
    private int popupScroll;
    private int groupBtnX;
    private int groupBtnY;
    private int groupBtnW;
    private int groupBtnH;
    private final Map<TrainMapViewMode, int[]> viewModeButtons;
    private TrainMapViewMode viewMode;
    private boolean octilinearMode;
    private float octilinearBlend;
    private float targetOctilinearBlend;
    private boolean searchFocused;
    private String searchQuery;
    private final Set<UUID> searchHighlight;
    private Map<String, TrainMapData.StationEntry> currByName;
    private final Map<String, float[]> trainPrevWorld;
    private final Map<String, float[]> trainCurrWorld;
    private final Map<String, Long> trainLastValidWorldMs;
    private final Map<String, TrainMotionState> trainMotion;
    private long lastTrainUpdateMs;
    private UUID draggedNode;
    private TrainMapData.StationEntry hoveredStation;
    private TrainMapData.TrainEntry hoveredTrain;
    private UUID pinnedTrainId;
    private String selectedRouteKey;
    private String hoveredGroup;
    private TrainMapData currSnapshot;
    private final Map<UUID, Integer> trainColors;
    private int nextColorIdx;
    private static final int[] PALETTE;
    private static final float LOD_ULTRA_LOW = 0.1f;
    private static final float LOD_LOW = 0.2f;
    private static final float LOD_MEDIUM = 0.4f;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int BORDER;
    private final List<String> availableDimensions;
    private String activeDimension;
    private int dimPrevX;
    private int dimPrevY;
    private int dimPrevW;
    private int dimPrevH;
    private int dimNextX;
    private int dimNextY;
    private int dimNextW;
    private int dimNextH;
    private int trainActivityPingCooldown;
    private int currentLayoutHash;
    private long lastLayoutAnimationMs;
    private long layoutStartMs;
    private int layoutTargetIterations;
    private int layoutIterationBudget;
    private boolean layoutConverged;
    private boolean layoutDiagnosticsLogged;
    
    public TrainMapScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Train Network Map"));
        this.panX = 0.0f;
        this.panY = 0.0f;
        this.zoom = 1.0f;
        this.initialZoom = 1.0f;
        this.panning = false;
        this.batchActive = false;
        this.deferredLabels = new ArrayList<Object[]>();
        this.layoutPos = new LinkedHashMap<UUID, float[]>();
        this.targetLayoutPos = new LinkedHashMap<UUID, float[]>();
        this.lastLayoutIds = Collections.emptySet();
        this.groupPatterns = new ArrayList<String>();
        this.groupMembers = new LinkedHashMap<String, List<UUID>>();
        this.stationToGroup = new HashMap<UUID, String>();
        this.expandedGroups = new HashSet<String>();
        this.groupUUIDs = new HashMap<String, UUID>();
        this.effectiveMap = new HashMap<UUID, UUID>();
        this.routeInfoStops = new ArrayList<int[]>();
        this.routeKeys = new ArrayList<String>();
        this.routeStops = new ArrayList<List<String>>();
        this.routeColors = new ArrayList<Integer>();
        this.routeTrains = new ArrayList<List<UUID>>();
        this.routeColorByKey = new LinkedHashMap<String, Integer>();
        this.trainToRoute = new HashMap<String, Integer>();
        this.edgeRoutes = new LinkedHashMap<String, List<Integer>>();
        this.nodeRoutes = new LinkedHashMap<UUID, List<Integer>>();
        this.edgeCanonicalDir = new LinkedHashMap<String, UUID[]>();
        this.selectedRoute = null;
        this.legendScroll = 0;
        this.showGroupPopup = false;
        this.popupScroll = 0;
        this.viewModeButtons = new EnumMap<TrainMapViewMode, int[]>(TrainMapViewMode.class);
        this.viewMode = TrainMapViewMode.ORIGINAL;
        this.octilinearMode = false;
        this.octilinearBlend = 0.0f;
        this.targetOctilinearBlend = 0.0f;
        this.searchFocused = false;
        this.searchQuery = "";
        this.searchHighlight = new HashSet<UUID>();
        this.currByName = new HashMap<String, TrainMapData.StationEntry>();
        this.trainPrevWorld = new HashMap<String, float[]>();
        this.trainCurrWorld = new HashMap<String, float[]>();
        this.trainLastValidWorldMs = new HashMap<String, Long>();
        this.trainMotion = new HashMap<String, TrainMotionState>();
        this.lastTrainUpdateMs = 0L;
        this.draggedNode = null;
        this.hoveredStation = null;
        this.hoveredTrain = null;
        this.pinnedTrainId = null;
        this.selectedRouteKey = null;
        this.hoveredGroup = null;
        this.currSnapshot = null;
        this.trainColors = new LinkedHashMap<UUID, Integer>();
        this.nextColorIdx = 0;
        this.availableDimensions = new ArrayList<String>();
        this.activeDimension = "";
        this.trainActivityPingCooldown = 0;
        this.lastLayoutAnimationMs = 0L;
        this.panel = panel;
        this.parentScreen = parentScreen;
    }
    
    protected void init() {
        final int margin = 20;
        this.areaX = margin;
        this.areaY = margin + 14;
        this.areaW = this.width - margin * 2;
        this.areaH = this.height - margin * 2 - 14;
        final DisplayTheme theme = this.panel.getSettings().getDisplayTheme();
        this.TEXT = theme.text;
        this.DIM = theme.dim;
        this.ACCENT = theme.accent;
        this.BORDER = theme.border;
        this.groupPatterns.clear();
        this.groupPatterns.addAll(this.panel.getTrainMapGroups());
        (this.groupInput = new EditBox(this.font, 0, 0, 200, 16, Component.literal("Group pattern (e.g. Station*)"))).setMaxLength(64);
        this.groupInput.setVisible(false);
        this.groupInput.setBordered(true);
        this.groupInput.setHint(Component.literal("Pattern e.g. Basmane*"));
        this.addRenderableWidget(this.groupInput);
        this.currSnapshot = this.panel.getTrainMapData();
        if (this.currSnapshot != null) {
            this.updateTrainWorldPositions(this.currSnapshot);
            this.refreshDimensionSelection(this.currSnapshot);
            this.assignColors(this.currSnapshot);
            this.rebuildVisibleState();
        }
        this.setOctilinearMode(this.viewMode.defaultsToOctilinear());
        this.octilinearBlend = this.targetOctilinearBlend;
        this.panX = 0.0f;
        this.panY = 0.0f;
        this.zoom = this.initialZoom;
    }

    public void tick() {
        super.tick();
        if (!this.panel.hasTrainConnection()) {
            this.trainActivityPingCooldown = 0;
            return;
        }
        if (this.trainActivityPingCooldown > 0) {
            --this.trainActivityPingCooldown;
            return;
        }
        this.trainActivityPingCooldown = 20;
        PacketDistributor.sendToServer(new PanelActivityPacket(this.panel.getBlockPos(), true, false));
    }
    
    private void resolveGroups(TrainMapData data) {
        this.groupMembers.clear();
        this.stationToGroup.clear();
        this.groupUUIDs.clear();
        this.effectiveMap.clear();
        for (String pattern : this.groupPatterns) {
            final List<UUID> members = new ArrayList<UUID>();
            for (TrainMapData.StationEntry s : data.getStations()) {
                if (this.matchesGroupPattern(pattern, s.name)) {
                    members.add(s.id);
                    this.stationToGroup.put(s.id, pattern);
                }
            }
            if (!members.isEmpty()) {
                this.groupMembers.put(pattern, members);
                this.groupUUIDs.put(pattern, UUID.nameUUIDFromBytes(pattern.getBytes(StandardCharsets.UTF_8)));
            }
        }
        for (TrainMapData.StationEntry s2 : data.getStations()) {
            final String group = this.stationToGroup.get(s2.id);
            if (group != null && !this.expandedGroups.contains(group)) {
                this.effectiveMap.put(s2.id, this.groupUUIDs.get(group));
            }
            else {
                this.effectiveMap.put(s2.id, s2.id);
            }
        }
    }
    
    private boolean matchesGroupPattern(String pattern, String name) {
        if (pattern == null || name == null) {
            return false;
        }
        if (pattern.endsWith("*") && pattern.startsWith("*")) {
            return name.contains(pattern.substring(1, pattern.length() - 1));
        }
        if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        if (pattern.startsWith("*")) {
            return name.endsWith(pattern.substring(1));
        }
        return name.equals(pattern);
    }
    
    private void addGroup(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }
        pattern = pattern.trim();
        if (this.groupPatterns.contains(pattern)) {
            return;
        }
        this.groupPatterns.add(pattern);
        this.syncGroupsToServer();
        if (this.currSnapshot != null) {
            this.resolveGroups(this.currSnapshot);
            this.forceRelayout();
        }
    }
    
    private void removeGroup(String pattern) {
        this.groupPatterns.remove(pattern);
        this.expandedGroups.remove(pattern);
        this.syncGroupsToServer();
        if (this.currSnapshot != null) {
            this.resolveGroups(this.currSnapshot);
            this.forceRelayout();
        }
    }
    
    private void toggleGroupExpand(String pattern) {
        if (this.expandedGroups.contains(pattern)) {
            this.expandedGroups.remove(pattern);
        }
        else {
            this.expandedGroups.add(pattern);
        }
        if (this.currSnapshot != null) {
            this.resolveGroups(this.currSnapshot);
            this.forceRelayout();
        }
    }
    
    private void syncGroupsToServer() {
        PacketDistributor.sendToServer(new TrainMapGroupPacket(this.panel.getBlockPos(), new ArrayList<String>(this.groupPatterns)));
    }
    
    private void forceRelayout() {
        this.lastLayoutIds = Collections.emptySet();
        this.currentLayoutHash = 0;
        this.layoutIterationBudget = 0;
        this.layoutConverged = false;
        this.lastLayoutAnimationMs = 0L;
        this.rebuildVisibleState();
    }
     
    private void updateSearchHighlight() {
        this.searchHighlight.clear();
        final TrainMapData visibleData = this.getVisibleData();
        if (this.searchQuery.isEmpty() || visibleData == null) {
            return;
        }
        UUID firstMatch = null;
        for (TrainMapData.StationEntry s : visibleData.getStations()) {
            if (s.name.toLowerCase().contains(this.searchQuery)) {
                this.searchHighlight.add(s.id);
                final UUID eid = this.effectiveMap.getOrDefault(s.id, s.id);
                this.searchHighlight.add(eid);
                if (firstMatch != null) {
                    continue;
                }
                firstMatch = eid;
            }
        }
        if (firstMatch != null) {
            final float[] lp = this.layoutPos.get(firstMatch);
            if (lp != null) {
                this.panX = -(lp[0] - 0.5f) * this.areaW * this.zoom;
                this.panY = -(lp[1] - 0.5f) * this.areaH * this.zoom;
            }
        }
    }

    private void refreshDimensionSelection(TrainMapData data) {
        this.availableDimensions.clear();
        this.availableDimensions.addAll(data.getDimensions());
        if (this.availableDimensions.isEmpty()) {
            this.activeDimension = "";
            return;
        }
        if (!this.activeDimension.isEmpty() && this.availableDimensions.contains(this.activeDimension)) {
            return;
        }
        if (this.panel.getLevel() != null) {
            final String panelDimension = this.panel.getLevel().dimension().location().toString();
            if (this.availableDimensions.contains(panelDimension)) {
                this.activeDimension = panelDimension;
                return;
            }
        }
        this.activeDimension = this.availableDimensions.get(0);
    }

    private TrainMapData getVisibleData() {
        if (this.currSnapshot == null) {
            return null;
        }
        return this.currSnapshot.filterByDimension(this.activeDimension);
    }

    private void rebuildVisibleState() {
        final TrainMapData visibleData = this.getVisibleData();
        if (visibleData != null && !visibleData.isEmpty()) {
            this.resolveGroups(visibleData);
            this.buildRoutes(visibleData);
            this.ensureLayout(visibleData);
        }
        else {
            this.lastLayoutIds = Collections.emptySet();
            this.layoutPos.clear();
            this.targetLayoutPos.clear();
        }
        this.updateSearchHighlight();
    }

    private void cycleDimension(int direction) {
        if (this.availableDimensions.size() <= 1) {
            return;
        }
        int idx = this.availableDimensions.indexOf(this.activeDimension);
        if (idx < 0) {
            idx = 0;
        }
        idx = (idx + direction + this.availableDimensions.size()) % this.availableDimensions.size();
        this.activeDimension = this.availableDimensions.get(idx);
        this.panX = 0.0f;
        this.panY = 0.0f;
        this.zoom = this.initialZoom;
        this.rebuildVisibleState();
    }

    private void renderDimensionSelector(GuiGraphics g, int mx, int my) {
        if (this.availableDimensions.size() <= 1) {
            this.dimPrevW = 0;
            this.dimNextW = 0;
            return;
        }
        final String label = shortDimensionName(this.activeDimension);
        final int labelW = this.font.width(label) + 18;
        final int btnW = 14;
        final int btnH = 14;
        final int totalW = btnW * 2 + labelW + 6;
        final int x = this.areaX + this.areaW / 2 - totalW / 2;
        final int y = this.areaY + 4;
        this.dimPrevX = x;
        this.dimPrevY = y;
        this.dimPrevW = btnW;
        this.dimPrevH = btnH;
        this.dimNextX = x + btnW + 6 + labelW;
        this.dimNextY = y;
        this.dimNextW = btnW;
        this.dimNextH = btnH;
        final boolean prevHover = mx >= this.dimPrevX && mx < this.dimPrevX + this.dimPrevW && my >= this.dimPrevY && my < this.dimPrevY + this.dimPrevH;
        final boolean nextHover = mx >= this.dimNextX && mx < this.dimNextX + this.dimNextW && my >= this.dimNextY && my < this.dimNextY + this.dimNextH;
        final int labelX = this.dimPrevX + this.dimPrevW + 6;
        g.fill(labelX, y, labelX + labelW, y + btnH, -2012213216);
        this.drawBorder(g, labelX, y, labelW, btnH, -13417370);
        g.fill(this.dimPrevX, y, this.dimPrevX + this.dimPrevW, y + btnH, prevHover ? -867941240 : -870178236);
        g.fill(this.dimNextX, y, this.dimNextX + this.dimNextW, y + btnH, nextHover ? -867941240 : -870178236);
        this.drawBorder(g, this.dimPrevX, y, this.dimPrevW, btnH, -13417370);
        this.drawBorder(g, this.dimNextX, y, this.dimNextW, btnH, -13417370);
        g.drawCenteredString(this.font, "<", this.dimPrevX + this.dimPrevW / 2, y + 3, -3355427);
        g.drawCenteredString(this.font, ">", this.dimNextX + this.dimNextW / 2, y + 3, -3355427);
        g.drawCenteredString(this.font, label, labelX + labelW / 2, y + 3, this.ACCENT);
    }
    
    private void renderViewModeSelector(GuiGraphics g, int mx, int my) {
        final int x = this.areaX + 4;
        final int y = this.areaY + 22;
        final int height = 16;
        int cursorX = x;
        this.viewModeButtons.clear();
        for (TrainMapViewMode mode : TrainMapViewMode.values()) {
            final String label = mode.getLabel();
            final int width = this.font.width(label) + 12;
            final boolean selected = mode == this.viewMode;
            final boolean hovered = mx >= cursorX && mx < cursorX + width && my >= y && my < y + height;
            final int fill = selected ? -12303258 : (hovered ? -867941240 : -870178236);
            final int border = selected ? this.ACCENT : -13417370;
            final int text = selected ? -1 : -3355427;
            g.fill(cursorX, y, cursorX + width, y + height, fill);
            this.drawBorder(g, cursorX, y, width, height, border);
            g.drawCenteredString(this.font, label, cursorX + width / 2, y + 4, text);
            this.viewModeButtons.put(mode, new int[] { cursorX, y, width, height });
            cursorX += width + 4;
        }
    }
    
    private void cycleViewMode(int direction) {
        this.setViewMode((direction < 0) ? this.viewMode.previous() : this.viewMode.next());
    }
    
    private void setViewMode(TrainMapViewMode mode) {
        if (mode == null || mode == this.viewMode) {
            return;
        }
        this.viewMode = mode;
        this.currentLayoutHash = 0;
        this.lastLayoutAnimationMs = 0L;
        this.setOctilinearMode(mode.defaultsToOctilinear());
    }
    
    private void setOctilinearMode(boolean enabled) {
        this.octilinearMode = enabled;
        this.targetOctilinearBlend = enabled ? 1.0f : 0.0f;
    }

    private static String shortDimensionName(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "All";
        }
        if (dimension.equals("minecraft:overworld")) {
            return "Overworld";
        }
        if (dimension.equals("minecraft:the_nether")) {
            return "Nether";
        }
        if (dimension.equals("minecraft:the_end")) {
            return "The End";
        }
        final int idx = dimension.indexOf(58);
        final String path = (idx >= 0 && idx + 1 < dimension.length()) ? dimension.substring(idx + 1) : dimension;
        final String[] parts = path.split("_");
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? dimension : sb.toString();
    }
    
    private void buildRoutes(TrainMapData data) {
        this.routeKeys.clear();
        this.routeStops.clear();
        this.routeColors.clear();
        this.routeTrains.clear();
        this.trainToRoute.clear();
        final Map<String, List<String>> stopsByKey = new LinkedHashMap<String, List<String>>();
        final Map<String, List<UUID>> trainsByKey = new LinkedHashMap<String, List<UUID>>();
        final Map<String, TrainMapData.StationEntry> byName = this.indexStationsByName(data.getStations());
        final Map<UUID, TrainMapData.StationEntry> byId = new LinkedHashMap<UUID, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station : data.getStations()) {
            byId.put(station.id, station);
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final List<TrainMapData.StationEntry> route = this.resolveRoute(train, byName, byId);
            if (train.scheduleStops.isEmpty() && route.size() < 2) {
                continue;
            }
            final String key = routeKey(train, route);
            stopsByKey.computeIfAbsent(key, ignored -> routeLabels(route, train.scheduleStops));
            trainsByKey.computeIfAbsent(key, ignored -> new ArrayList<UUID>()).add(train.id);
        }
        final List<String> sortedKeys = new ArrayList<String>(stopsByKey.keySet());
        sortedKeys.sort(String.CASE_INSENSITIVE_ORDER);
        final Map<String, Integer> routeByKey = new HashMap<String, Integer>();
        for (String key : sortedKeys) {
            final int routeIndex = this.routeStops.size();
            this.routeKeys.add(key);
            this.routeStops.add(stopsByKey.get(key));
            this.routeColors.add(this.colorForRouteKey(key));
            this.routeTrains.add(new ArrayList<UUID>(trainsByKey.getOrDefault(key, Collections.emptyList())));
            routeByKey.put(key, routeIndex);
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final List<TrainMapData.StationEntry> route = this.resolveRoute(train, byName, byId);
            if (train.scheduleStops.isEmpty() && route.size() < 2) {
                continue;
            }
            final Integer routeIndex = routeByKey.get(routeKey(train, route));
            if (routeIndex != null) {
                this.trainToRoute.put(train.id.toString(), routeIndex);
            }
        }
        if (this.selectedRouteKey != null) {
            this.selectedRoute = routeByKey.get(this.selectedRouteKey);
            if (this.selectedRoute == null) {
                this.selectedRouteKey = null;
            }
        }
        else {
            this.selectedRoute = null;
        }
    }

    private static String routeKey(TrainMapData.TrainEntry train) {
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            return train.routeKey;
        }
        return String.join("|", train.scheduleStops);
    }

    private static String routeKey(TrainMapData.TrainEntry train, List<TrainMapData.StationEntry> route) {
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            return train.routeKey;
        }
        if (route.size() >= 2) {
            final ArrayList<String> ids = new ArrayList<String>();
            for (TrainMapData.StationEntry station : route) {
                ids.add(station.id.toString());
            }
            return String.join("|", ids);
        }
        return String.join("|", train.scheduleStops);
    }

    private static List<String> routeLabels(List<TrainMapData.StationEntry> route, List<String> fallbackStops) {
        if (route.isEmpty()) {
            return new ArrayList<String>(fallbackStops);
        }
        final ArrayList<String> labels = new ArrayList<String>();
        for (TrainMapData.StationEntry station : route) {
            labels.add(station.name);
        }
        return labels;
    }

    private int colorForRouteKey(String key) {
        Integer existing = this.routeColorByKey.get(key);
        if (existing != null) {
            return existing;
        }
        final int color = TrainMapScreen.PALETTE[this.routeColorByKey.size() % TrainMapScreen.PALETTE.length];
        this.routeColorByKey.put(key, color);
        return color;
    }

    private void toggleSelectedRoute(int routeIndex) {
        if (routeIndex < 0 || routeIndex >= this.routeKeys.size()) {
            this.clearSelectedRoute();
            return;
        }
        if (this.selectedRoute != null && this.selectedRoute == routeIndex) {
            this.clearSelectedRoute();
            return;
        }
        this.selectedRoute = routeIndex;
        this.selectedRouteKey = this.routeKeys.get(routeIndex);
    }

    private void clearSelectedRoute() {
        this.selectedRoute = null;
        this.selectedRouteKey = null;
    }
    
    private String edgeKey(UUID a, UUID b) {
        return (a.compareTo(b) <= 0) ? (a.toString() + ":" + b.toString()) : (b.toString() + ":" + a.toString());
    }

    private UUID[] parseEdgeKey(String key) {
        final String[] parts = key.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new UUID[] { UUID.fromString(parts[0]), UUID.fromString(parts[1]) };
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }
    
    private void buildEdges(TrainMapData data, Map<String, TrainMapData.StationEntry> byName) {
        this.edgeRoutes.clear();
        this.nodeRoutes.clear();
        this.edgeCanonicalDir.clear();
        final Map<UUID, TrainMapData.StationEntry> byId = new LinkedHashMap<UUID, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station : data.getStations()) {
            byId.put(station.id, station);
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final Integer routeIndex = this.trainToRoute.get(train.id.toString());
            if (routeIndex == null) {
                continue;
            }
            final List<TrainMapData.StationEntry> route = this.resolveRoute(train, byName, byId);
            if (route.size() < 2) {
                continue;
            }
            UUID lastEid = null;
            for (TrainMapData.StationEntry s : route) {
                final UUID eid = this.effectiveMap.getOrDefault(s.id, s.id);
                if (lastEid != null && !eid.equals(lastEid)) {
                    final String ek = this.edgeKey(lastEid, eid);
                    if (!this.edgeRoutes.containsKey(ek)) {
                        this.edgeCanonicalDir.put(ek, new UUID[] { lastEid, eid });
                    }
                    final List<Integer> list = this.edgeRoutes.computeIfAbsent(ek, k -> new ArrayList());
                    if (!list.contains(routeIndex)) {
                        list.add(routeIndex);
                    }
                }
                lastEid = eid;
            }
            if (lastEid != null) {
                final UUID firstEid = this.effectiveMap.getOrDefault(route.get(0).id, route.get(0).id);
                if (!firstEid.equals(lastEid)) {
                    final String ek2 = this.edgeKey(lastEid, firstEid);
                    if (!this.edgeRoutes.containsKey(ek2)) {
                        this.edgeCanonicalDir.put(ek2, new UUID[] { lastEid, firstEid });
                    }
                    final List<Integer> list2 = this.edgeRoutes.computeIfAbsent(ek2, k -> new ArrayList());
                    if (!list2.contains(routeIndex)) {
                        list2.add(routeIndex);
                    }
                }
            }
        }
        this.nodeRoutes.clear();
        final List<String> invalidEdgeKeys = new ArrayList<String>();
        for (Map.Entry<String, List<Integer>> entry : this.edgeRoutes.entrySet()) {
            final UUID[] edge = this.parseEdgeKey(entry.getKey());
            if (edge == null) {
                invalidEdgeKeys.add(entry.getKey());
                CreateStats.LOGGER.warn("Dropping malformed train map edge '{}'", entry.getKey());
                continue;
            }
            final UUID ua = edge[0];
            final UUID ub = edge[1];
            for (int ri2 : entry.getValue()) {
                this.nodeRoutes.computeIfAbsent(ua, k -> new ArrayList());
                if (!this.nodeRoutes.get(ua).contains(ri2)) {
                    this.nodeRoutes.get(ua).add(ri2);
                }
                this.nodeRoutes.computeIfAbsent(ub, k -> new ArrayList());
                if (!this.nodeRoutes.get(ub).contains(ri2)) {
                    this.nodeRoutes.get(ub).add(ri2);
                }
            }
        }
        for (String invalidEdgeKey : invalidEdgeKeys) {
            this.edgeRoutes.remove(invalidEdgeKey);
            this.edgeCanonicalDir.remove(invalidEdgeKey);
        }
    }
    
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, -301331942);
        g.drawCenteredString(this.font, "§lTrain Network Map", this.width / 2, 6, this.ACCENT);
        g.fill(this.areaX, this.areaY, this.areaX + this.areaW, this.areaY + this.areaH, -15921888);
        this.drawBorder(g, this.areaX, this.areaY, this.areaW, this.areaH, this.BORDER);
        final TrainMapData nd = this.panel.getTrainMapData();
        if (nd != null && nd != this.currSnapshot) {
            this.updateTrainWorldPositions(nd);
            this.assignColors(this.currSnapshot = nd);
            this.refreshDimensionSelection(this.currSnapshot);
            this.rebuildVisibleState();
        }
        final TrainMapData visibleData = this.getVisibleData();
        if (this.currSnapshot == null || visibleData == null || visibleData.isEmpty()) {
            g.drawCenteredString(this.font, "No train network data", this.areaX + this.areaW / 2, this.areaY + this.areaH / 2 - 8, this.DIM);
            g.drawCenteredString(this.font, "Connect a Display Link from a Train Station", this.areaX + this.areaW / 2, this.areaY + this.areaH / 2 + 8, -12303258);
            this.renderDimensionSelector(g, mouseX, mouseY);
            this.renderViewModeSelector(g, mouseX, mouseY);
            this.drawGroupButton(g, mouseX, mouseY);
            if (this.showGroupPopup) {
                this.drawGroupPopup(g, mouseX, mouseY, pt);
            }
            return;
        }
        this.ensureLayout(visibleData);
        this.animateLayout();
        this.renderDimensionSelector(g, mouseX, mouseY);
        this.drawGrid(g);
        g.enableScissor(this.areaX, this.areaY, this.areaX + this.areaW, this.areaY + this.areaH);
        this.currByName = this.indexStationsByName(visibleData.getStations());
        final Map<String, TrainMapData.StationEntry> byName = this.currByName;
        this.buildEdges(visibleData, byName);
        this.beginBatch(g);
        this.drawRouteLines(g, visibleData, byName);
        this.hoveredStation = null;
        this.hoveredGroup = null;
        this.drawGroupNodes(g, visibleData, mouseX, mouseY);
        this.drawStations(g, visibleData, mouseX, mouseY);
        this.hoveredTrain = null;
        this.drawTrains(g, visibleData, byName, mouseX, mouseY, 0.0f);
        this.endBatch();
        this.renderDeferredLabels(g);
        g.disableScissor();
        this.drawSearchBox(g, mouseX, mouseY);
        this.renderViewModeSelector(g, mouseX, mouseY);
        this.drawGroupButton(g, mouseX, mouseY);
        this.renderSummaryBar(g, visibleData);
        this.drawLegend(g);
        if (!this.showGroupPopup) {
            final TrainMapData.TrainEntry pinnedTrain = this.findTrainById(visibleData, this.pinnedTrainId);
            if (this.pinnedTrainId != null && pinnedTrain == null) {
                this.pinnedTrainId = null;
            }
            else if (pinnedTrain != null) {
                this.renderPinnedTrainTooltip(g, pinnedTrain, this.currByName);
            }
            if (this.hoveredGroup != null) {
                this.renderGroupTooltip(g, mouseX, mouseY, this.hoveredGroup);
            }
            else if (this.hoveredStation != null) {
                this.renderStationTooltip(g, mouseX, mouseY, this.hoveredStation);
            }
            else if (this.hoveredTrain != null && (this.pinnedTrainId == null || !this.pinnedTrainId.equals(this.hoveredTrain.id))) {
                this.renderTrainTooltip(g, mouseX, mouseY, this.hoveredTrain, this.currByName);
            }
        }
        final String help = "Scroll: Zoom | Click Train: Pin | Drag: Pan | Drag Station: Move | M: View | O: Route Style | G: Groups | R: Reset | F: Fit | ESC: Back";
        g.drawString(this.font, help, this.areaX + 4, this.areaY + this.areaH - 10, 1157627903, false);
        if (this.showGroupPopup) {
            this.drawGroupPopup(g, mouseX, mouseY, pt);
        }
    }
    
    private void drawSearchBox(GuiGraphics g, int mx, int my) {
        final int sx = this.areaX + 4;
        final int sy = this.areaY + 4;
        final int sw = 160;
        final int sh = 14;
        final boolean hover = mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
        final int bgColor = this.searchFocused ? -586149856 : -2012213216;
        final int borderColor = this.searchFocused ? -5592372 : (hover ? -10061927 : -13417370);
        g.fill(sx, sy, sx + sw, sy + sh, bgColor);
        this.drawBorder(g, sx, sy, sw, sh, borderColor);
        if (this.searchFocused || !this.searchQuery.isEmpty()) {
            final boolean cursorOn = this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L;
            final String display = this.searchQuery + (cursorOn ? "|" : (this.searchFocused ? " " : ""));
            g.drawString(this.font, display, sx + 3, sy + 3, this.searchFocused ? -2236929 : -5592406, false);
        }
        else {
            g.drawString(this.font, "§7Search stations...", sx + 3, sy + 3, this.DIM, false);
        }
    }
    
    private void updateTrainWorldPositions(TrainMapData data) {
        final long now = System.currentTimeMillis();
        final Set<String> seenTrains = new HashSet<String>();
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final String tid = train.id.toString();
            seenTrains.add(tid);
            final float[] curr = this.trainCurrWorld.get(tid);
            if (!this.isTrainPositionUsable(train, data)) {
                if (curr != null) {
                    this.trainPrevWorld.put(tid, new float[] { curr[0], curr[1] });
                }
                continue;
            }
            this.trainPrevWorld.put(tid, (curr != null) ? new float[] { curr[0], curr[1] } : new float[] { (float)train.x, (float)train.z });
            this.trainCurrWorld.put(tid, new float[] { (float)train.x, (float)train.z });
            this.trainLastValidWorldMs.put(tid, now);
        }
        this.trainPrevWorld.keySet().removeIf(id -> !seenTrains.contains(id));
        this.trainCurrWorld.keySet().removeIf(id -> !seenTrains.contains(id));
        this.trainLastValidWorldMs.keySet().removeIf(id -> !seenTrains.contains(id));
        this.trainMotion.keySet().removeIf(id -> !seenTrains.contains(id));
        this.lastTrainUpdateMs = now;
    }

    private boolean isTrainPositionUsable(TrainMapData.TrainEntry train, TrainMapData data) {
        if (!train.positionValid) {
            return false;
        }
        if (!Double.isFinite(train.x) || !Double.isFinite(train.z)) {
            return false;
        }
        if (Math.abs(train.x) > 1.0E-4 || Math.abs(train.z) > 1.0E-4) {
            return true;
        }
        if (!train.currentStationName.isEmpty()) {
            return true;
        }
        for (TrainMapData.StationEntry station : data.getStations()) {
            if (train.dimension != null && station.dimension != null && !train.dimension.isEmpty() && !station.dimension.isEmpty() && !train.dimension.equals(station.dimension)) {
                continue;
            }
            final double dx = station.x - train.x;
            final double dz = station.z - train.z;
            if (dx * dx + dz * dz <= 256.0) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseCachedTrainPosition(TrainMapData.TrainEntry train, TrainMapData data, long now) {
        if (this.isTrainPositionUsable(train, data)) {
            return true;
        }
        final Long lastValid = this.trainLastValidWorldMs.get(train.id.toString());
        return lastValid != null && now - lastValid <= 1500L && this.trainCurrWorld.containsKey(train.id.toString());
    }
    
    private float[] getInterpolatedTrainPos(TrainMapData.TrainEntry train) {
        final String tid = train.id.toString();
        final float[] curr = this.trainCurrWorld.get(tid);
        if (curr == null) {
            return new float[] { (float)train.x, (float)train.z };
        }
        final float[] prev = this.trainPrevWorld.get(tid);
        if (prev == null) {
            return curr;
        }
        final float alpha = Math.min(1.0f, (System.currentTimeMillis() - this.lastTrainUpdateMs) / 250.0f);
        return new float[] { prev[0] + (curr[0] - prev[0]) * alpha, prev[1] + (curr[1] - prev[1]) * alpha };
    }

    private float[] getSmoothedTrainWorldPos(TrainMapData.TrainEntry train, TrainMapData.StationEntry prev, TrainMapData.StationEntry dest) {
        final String trainKey = train.id.toString();
        final float[] base = this.getInterpolatedTrainPos(train);
        if (train.stopped || train.waitingForSignal || prev == null || dest == null || this.lastTrainUpdateMs <= 0L) {
            this.trainMotion.remove(trainKey);
            return base;
        }
        final double routeDx = dest.x - prev.x;
        final double routeDz = dest.z - prev.z;
        final double routeLen = Math.sqrt(routeDx * routeDx + routeDz * routeDz);
        if (routeLen < 1.0 || Math.abs(train.speed) < 0.001f) {
            return base;
        }
        final float elapsedTicks = Math.max(0.0f, Math.min(60.0f, (System.currentTimeMillis() - this.lastTrainUpdateMs) / 50.0f));
        final float baseProgress = this.estimateProgress(base[0], base[1], prev, dest);
        final float targetProgress = Math.max(0.0f, Math.min(1.0f, baseProgress + (float)(Math.abs(train.speed) * elapsedTicks / routeLen)));
        final String segmentKey = prev.id.toString() + ">" + dest.id.toString();
        final long now = System.currentTimeMillis();
        final TrainMotionState state = this.trainMotion.computeIfAbsent(trainKey, ignored -> new TrainMotionState());
        if (!segmentKey.equals(state.segmentKey) || state.lastFrameMs <= 0L || targetProgress + 0.55f < state.progress) {
            state.segmentKey = segmentKey;
            state.progress = targetProgress;
            state.lastFrameMs = now;
        }
        else {
            final float deltaSeconds = Math.max(0.0f, Math.min(0.12f, (now - state.lastFrameMs) / 1000.0f));
            state.lastFrameMs = now;
            final float maxStep = Math.max(0.012f, (float)(Math.abs(train.speed) * 1.8f * deltaSeconds / routeLen) + 0.006f);
            final float delta = targetProgress - state.progress;
            if (Math.abs(delta) <= Math.max(0.004f, maxStep)) {
                state.progress = targetProgress;
            }
            else {
                state.progress += Math.max(-maxStep, Math.min(maxStep, delta));
            }
            state.progress = Math.max(0.0f, Math.min(1.0f, state.progress));
        }
        return new float[] { (float)(prev.x + routeDx * state.progress), (float)(prev.z + routeDz * state.progress) };
    }
    
    private void ensureLayout(TrainMapData data) {
        final Set<UUID> ids = new HashSet<UUID>(this.effectiveMap.values());
        final int hash = this.computeLayoutHash(data);
        final boolean layoutChanged = hash != this.currentLayoutHash || !ids.equals(this.lastLayoutIds) || !this.targetLayoutPos.keySet().containsAll(ids);
        if (!layoutChanged) {
            return;
        }
        this.lastLayoutIds = new HashSet<UUID>(ids);
        this.currentLayoutHash = hash;
        final Map<UUID, float[]> computed = TrainMapLayoutHelper.computeLayout(data, this.effectiveMap, this.viewMode, this.resolveTrainLayoutIterations(ids.size()));
        final Map<UUID, float[]> display = new LinkedHashMap<UUID, float[]>();
        for (Map.Entry<UUID, float[]> entry : computed.entrySet()) {
            final float[] existing = this.layoutPos.get(entry.getKey());
            display.put(entry.getKey(), (existing != null) ? new float[] { existing[0], existing[1] } : new float[] { entry.getValue()[0], entry.getValue()[1] });
        }
        this.layoutPos.clear();
        this.layoutPos.putAll(display);
        this.targetLayoutPos.clear();
        for (Map.Entry<UUID, float[]> entry2 : computed.entrySet()) {
            final float[] pos = entry2.getValue();
            this.targetLayoutPos.put(entry2.getKey(), new float[] { pos[0], pos[1] });
        }
        this.lastLayoutAnimationMs = 0L;
    }
    
    private int resolveTrainLayoutIterations(int nodeCount) {
        int configured = 180;
        try {
            configured = (int)ModConfiguration.COMMON.trainMapLayoutIterations.get();
        }
        catch (Exception ex) {}
        configured = Math.max(30, configured);
        final float scale = Math.min(1.0f, 260.0f / Math.max(260, nodeCount));
        return Math.max(24, Math.round(configured * scale));
    }

    private int computeLayoutHash(TrainMapData data) {
        int hash = data.getStations().size();
        hash = hash * 31 + this.viewMode.ordinal();
        for (TrainMapData.StationEntry station : data.getStations()) {
            hash = hash * 31 + station.id.hashCode();
            hash = hash * 31 + station.name.hashCode();
            hash = hash * 31 + Double.hashCode(station.x);
            hash = hash * 31 + Double.hashCode(station.z);
            hash = hash * 31 + this.effectiveMap.getOrDefault(station.id, station.id).hashCode();
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            hash = hash * 31 + train.id.hashCode();
            hash = hash * 31 + train.routeKey.hashCode();
            hash = hash * 31 + ((train.currentStationId != null) ? train.currentStationId.hashCode() : 0);
            hash = hash * 31 + ((train.previousStationId != null) ? train.previousStationId.hashCode() : 0);
            hash = hash * 31 + ((train.destinationStationId != null) ? train.destinationStationId.hashCode() : 0);
            hash = hash * 31 + ((train.activeSegmentFromId != null) ? train.activeSegmentFromId.hashCode() : 0);
            hash = hash * 31 + ((train.activeSegmentToId != null) ? train.activeSegmentToId.hashCode() : 0);
            for (String stop : train.scheduleStops) {
                hash = hash * 31 + stop.hashCode();
            }
        }
        return hash;
    }

    private void animateLayout() {
        if (this.layoutPos.isEmpty() || this.targetLayoutPos.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.lastLayoutAnimationMs == 0L) {
            this.lastLayoutAnimationMs = now;
            return;
        }
        final float deltaSeconds = Math.max(0.0f, Math.min(0.1f, (now - this.lastLayoutAnimationMs) / 1000.0f));
        this.lastLayoutAnimationMs = now;
        final float alpha = 1.0f - (float)Math.exp(-deltaSeconds * 6.75f);
        for (Map.Entry<UUID, float[]> entry : this.targetLayoutPos.entrySet()) {
            if (entry.getKey().equals(this.draggedNode)) {
                continue;
            }
            final float[] current = this.layoutPos.get(entry.getKey());
            final float[] target = entry.getValue();
            if (current == null || target == null) {
                continue;
            }
            current[0] += (target[0] - current[0]) * alpha;
            current[1] += (target[1] - current[1]) * alpha;
            if (Math.abs(target[0] - current[0]) < 5.0E-4f) {
                current[0] = target[0];
            }
            if (Math.abs(target[1] - current[1]) < 5.0E-4f) {
                current[1] = target[1];
            }
        }
        this.octilinearBlend += (this.targetOctilinearBlend - this.octilinearBlend) * alpha;
        if (Math.abs(this.targetOctilinearBlend - this.octilinearBlend) < 0.01f) {
            this.octilinearBlend = this.targetOctilinearBlend;
        }
    }
    
    private List<TrainMapData.StationEntry> resolveRoute(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId) {
        final List<TrainMapData.StationEntry> result = new ArrayList<TrainMapData.StationEntry>();
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            for (String token : train.routeKey.split("\\|")) {
                this.addRouteStation(result, this.findRouteStation(byName, byId, token));
            }
        }
        if (result.size() < 2) {
            result.clear();
            for (String filter : train.scheduleStops) {
                this.addRouteStation(result, this.findStation(byName, filter));
            }
        }
        if (result.size() < 2) {
            result.clear();
            TrainMapData.StationEntry from = byId.get(train.activeSegmentFromId);
            if (from == null) {
                from = byId.get(train.previousStationId);
            }
            if (from == null) {
                from = byId.get(train.currentStationId);
            }
            TrainMapData.StationEntry to = byId.get(train.activeSegmentToId);
            if (to == null) {
                to = byId.get(train.destinationStationId);
            }
            if (to == null) {
                to = this.findStation(byName, train.nextDestination);
            }
            this.addRouteStation(result, from);
            this.addRouteStation(result, to);
        }
        return result;
    }

    private TrainMapData.StationEntry findRouteStation(Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            final TrainMapData.StationEntry station = byId.get(UUID.fromString(token));
            if (station != null) {
                return station;
            }
        }
        catch (IllegalArgumentException ex) {}
        return this.findStation(byName, token);
    }

    private void addRouteStation(List<TrainMapData.StationEntry> route, TrainMapData.StationEntry station) {
        if (station == null) {
            return;
        }
        if (!route.isEmpty() && route.get(route.size() - 1).id.equals(station.id)) {
            return;
        }
        route.add(station);
    }
    
    private int toSX(float lx) {
        return (int)(this.areaX + this.areaW * 0.5f + (lx - 0.5f) * this.areaW * this.zoom + this.panX);
    }
    
    private int toSY(float ly) {
        return (int)(this.areaY + this.areaH * 0.5f + (ly - 0.5f) * this.areaH * this.zoom + this.panY);
    }
    
    private float toLX(double sx) {
        return (float)((sx - this.areaX - this.areaW * 0.5f - this.panX) / (this.areaW * this.zoom)) + 0.5f;
    }
    
    private float toLY(double sy) {
        return (float)((sy - this.areaY - this.areaH * 0.5f - this.panY) / (this.areaH * this.zoom)) + 0.5f;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private float[] getEffectivePos(UUID stationId) {
        final UUID eid = this.effectiveMap.getOrDefault(stationId, stationId);
        return this.layoutPos.get(eid);
    }
    
    private boolean isVisible(int sx, int sy, int margin) {
        return sx >= this.areaX - margin && sx <= this.areaX + this.areaW + margin && sy >= this.areaY - margin && sy <= this.areaY + this.areaH + margin;
    }
    
    private void drawRouteLines(GuiGraphics g, TrainMapData data, Map<String, TrainMapData.StationEntry> byName) {
        final float lineW = Math.max(2.0f, Math.min(4.0f, 3.0f * (float)Math.sqrt(this.zoom)));
        final float gap = lineW + 1.5f;
        for (Map.Entry<String, List<Integer>> entry : this.edgeRoutes.entrySet()) {
            final UUID[] edge = this.parseEdgeKey(entry.getKey());
            if (edge == null) {
                continue;
            }
            final UUID ua = edge[0];
            final UUID ub = edge[1];
            final float[] pa = this.layoutPos.get(ua);
            final float[] pb = this.layoutPos.get(ub);
            if (pa == null) {
                continue;
            }
            if (pb == null) {
                continue;
            }
            final int ax = this.toSX(pa[0]);
            final int ay = this.toSY(pa[1]);
            final int bx = this.toSX(pb[0]);
            final int by = this.toSY(pb[1]);
            if (!this.isVisible(ax, ay, 200) && !this.isVisible(bx, by, 200)) {
                continue;
            }
            final List<Integer> rIds = entry.getValue();
            final int routeCount = rIds.size();
            final float edx = (float)(bx - ax);
            final float edy = (float)(by - ay);
            final float len = (float)Math.sqrt(edx * edx + edy * edy);
            if (len < 1.0f) {
                continue;
            }
            final float nx = -edy / len;
            final float ny = edx / len;
            for (int i = 0; i < routeCount; ++i) {
                final float offset = (i - (routeCount - 1) / 2.0f) * gap;
                final int ox = (int)(nx * offset);
                final int oy = (int)(ny * offset);
                final int ri = rIds.get(i);
                final int color = this.routeColors.get(ri);
                int alpha;
                if (this.selectedRoute == null) {
                    alpha = 204;
                }
                else if (this.selectedRoute.equals(ri)) {
                    alpha = 255;
                }
                else {
                    alpha = 32;
                }
                final int lineColor = (color & 0xFFFFFF) | alpha << 24;
                this.drawRouteShape(g, ax + ox, ay + oy, bx + ox, by + oy, lineColor, (int)lineW);
                final int capR = Math.max(1, (int)(lineW / 2.0f));
                final int capAA = (lineColor & 0xFFFFFF) | Math.max(0, (lineColor >> 24 & 0xFF) / 4) << 24;
                this.fillCircle(g, ax + ox, ay + oy, capR + 2, capAA);
                this.fillCircle(g, bx + ox, by + oy, capR + 2, capAA);
                this.fillCircle(g, ax + ox, ay + oy, capR, lineColor);
                this.fillCircle(g, bx + ox, by + oy, capR, lineColor);
                this.drawRoutePulseBeads(g, ax + ox, ay + oy, bx + ox, by + oy, ri, lineColor, lineW, data.getServerTick());
            }
        }
    }

    private void drawRoutePulseBeads(GuiGraphics g, int ax, int ay, int bx, int by, int routeIndex, int color, float lineWidth, long serverTick) {
        final int lineAlpha = color >> 24 & 0xFF;
        if (lineAlpha < 96 || routeIndex < 0 || routeIndex >= this.routeTrains.size() || this.routeTrains.get(routeIndex).isEmpty()) {
            return;
        }
        final float dx = bx - ax;
        final float dy = by - ay;
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 32.0f) {
            return;
        }
        final int trainCount = this.routeTrains.get(routeIndex).size();
        final int beadCount = Math.max(2, Math.min(6, trainCount * 2));
        final int beadSize = Math.max(1, Math.min(3, Math.round(lineWidth * 0.65f)));
        final double phaseSeconds = System.currentTimeMillis() / 1800.0 + routeIndex * 0.173;
        final float basePhase = (float)(phaseSeconds - Math.floor(phaseSeconds));
        final int outerColor = (color & 0xFFFFFF) | Math.min(132, Math.max(56, lineAlpha - 76)) << 24;
        final int innerColor = (color & 0xFFFFFF) | Math.min(218, Math.max(112, lineAlpha + 8)) << 24;
        for (int i = 0; i < beadCount; ++i) {
            final float t = (basePhase + i / (float)beadCount) % 1.0f;
            final float[] point = this.routePoint(ax, ay, bx, by, t, this.octilinearBlend);
            final int px = Math.round(point[0]);
            final int py = Math.round(point[1]);
            this.fillCircle(g, px, py, beadSize + 1, outerColor);
            this.fillCircle(g, px, py, beadSize, innerColor);
        }
    }
    
    private void drawRouteShape(GuiGraphics g, int ax, int ay, int bx, int by, int color, int lw) {
        final int baseAlpha = color >> 24 & 0xFF;
        this.drawInterpolatedRoute(g, ax, ay, bx, by, (color & 0xFFFFFF) | Math.max(0, baseAlpha / 8) << 24, lw + 4, this.octilinearBlend);
        this.drawInterpolatedRoute(g, ax, ay, bx, by, (color & 0xFFFFFF) | Math.max(0, baseAlpha / 4) << 24, lw + 2, this.octilinearBlend);
        this.drawInterpolatedRoute(g, ax, ay, bx, by, color, lw, this.octilinearBlend);
    }
    
    private void drawInterpolatedRoute(GuiGraphics g, int ax, int ay, int bx, int by, int color, int lw, float blend) {
        if (blend <= 0.001f) {
            this.drawThickLine(g, ax, ay, bx, by, color, lw);
            return;
        }
        if (blend >= 0.999f) {
            this.drawOctilinearRoute(g, ax, ay, bx, by, color, lw);
            return;
        }
        float[] previous = this.routePoint(ax, ay, bx, by, 0.0f, blend);
        for (int step = 1; step <= 12; ++step) {
            final float t = step / 12.0f;
            final float[] point = this.routePoint(ax, ay, bx, by, t, blend);
            this.drawThickLine(g, Math.round(previous[0]), Math.round(previous[1]), Math.round(point[0]), Math.round(point[1]), color, lw);
            previous = point;
        }
    }
    
    private void drawOctilinearRoute(GuiGraphics g, int ax, int ay, int bx, int by, int color, int lw) {
        final int ddx = bx - ax;
        final int ddy = by - ay;
        final int adx = Math.abs(ddx);
        final int ady = Math.abs(ddy);
        final int diag = Math.min(adx, ady);
        final int sx = (ddx >= 0) ? 1 : -1;
        final int sy = (ddy >= 0) ? 1 : -1;
        final int mx1 = ax + sx * diag;
        final int my1 = ay + sy * diag;
        this.drawThickLine(g, ax, ay, mx1, my1, color, lw);
        this.fillCircle(g, mx1, my1, Math.max(1, lw / 2), color);
        this.drawThickLine(g, mx1, my1, bx, by, color, lw);
    }
    
    private void drawGroupNodes(GuiGraphics g, TrainMapData data, int mx, int my) {
        final long now = System.currentTimeMillis();
        final int r = Math.max(8, Math.min(16, (int)(12.0 * Math.sqrt(this.zoom))));
        for (Map.Entry<String, List<UUID>> entry : this.groupMembers.entrySet()) {
            final String pattern = entry.getKey();
            if (this.expandedGroups.contains(pattern)) {
                continue;
            }
            final UUID gid = this.groupUUIDs.get(pattern);
            if (gid == null) {
                continue;
            }
            final float[] lp = this.layoutPos.get(gid);
            if (lp == null) {
                continue;
            }
            final int sx = this.toSX(lp[0]);
            final int sy = this.toSY(lp[1]);
            if (!this.isVisible(sx, sy, 60)) {
                continue;
            }
            if (this.zoom < 0.1f) {
                final int ringColor = -7829317;
                this.fillCircle(g, sx, sy, 4, ringColor);
            }
            else {
                if (this.searchHighlight.contains(gid)) {
                    this.fillCircle(g, sx, sy, r + 10, -1996488960);
                    this.fillCircle(g, sx, sy, r + 8, 1157627648);
                }
                final int memberCount = entry.getValue().size();
                final double d = Math.sqrt((mx - sx) * (mx - sx) + (my - sy) * (my - sy));
                if (d < r + 6) {
                    this.hoveredGroup = pattern;
                }
                final boolean hovered = pattern.equals(this.hoveredGroup);
                int dockedCount = 0;
                if (data != null) {
                    final Set<String> memberNames = new HashSet<String>();
                    for (UUID mid : entry.getValue()) {
                        final TrainMapData.StationEntry ms = this.getStationById(data, mid);
                        if (ms != null) {
                            memberNames.add(ms.name);
                        }
                    }
                    for (TrainMapData.TrainEntry t : data.getTrains()) {
                        if (memberNames.contains(t.currentStationName)) {
                            ++dockedCount;
                        }
                    }
                }
                if (dockedCount > 0) {
                    final float pulse = (float)(0.5 + 0.5 * Math.sin(now / 400.0));
                    final int alpha = (int)(40.0f + 50.0f * pulse);
                    this.fillCircle(g, sx, sy, r + 6 + (int)(3.0f * pulse), alpha << 24 | 0xFF4433);
                }
                final List<Integer> grRoutes = this.nodeRoutes.get(gid);
                if (grRoutes != null && grRoutes.size() >= 2) {
                    this.drawInterchangeCircle(g, sx, sy, r, grRoutes);
                }
                final int ringColor2 = hovered ? -1 : ((dockedCount > 0) ? -39356 : -7829317);
                this.fillCircle(g, sx, sy, r + 2, ringColor2);
                this.fillCircle(g, sx, sy, r, -15066571);
                this.fillCircle(g, sx, sy, r - 3, ringColor2);
                this.fillCircle(g, sx, sy, r - 5, -15066571);
                this.fillCircle(g, sx, sy, Math.max(2, r / 4), ringColor2);
                if (this.zoom <= 0.08f) {
                    continue;
                }
                final String displayName = pattern.replace("*", "").trim();
                String label = displayName + " (" + memberCount;
                if (label.length() > 26) {
                    label = label.substring(0, 24) + "..";
                }
                final int lw = this.font.width(label);
                final int ly = sy + r + 4;
                if (this.batchActive) {
                    this.batchQuad(sx - lw / 2 - 3, ly - 1, sx + lw / 2 + 3, ly + 10, -1073741824);
                    this.deferLabel(sx, ly, (dockedCount > 0) ? -21880 : -6706467, label, true);
                }
                else {
                    g.fill(sx - lw / 2 - 3, ly - 1, sx + lw / 2 + 3, ly + 10, -1073741824);
                    g.drawCenteredString(this.font, label, sx, ly, (dockedCount > 0) ? -21880 : -6706467);
                }
            }
        }
    }
    
    private void drawStations(GuiGraphics g, TrainMapData data, int mx, int my) {
        final Map<String, List<TrainMapData.TrainEntry>> docked = new HashMap<String, List<TrainMapData.TrainEntry>>();
        for (TrainMapData.TrainEntry t : data.getTrains()) {
            docked.computeIfAbsent(t.currentStationName, k -> new ArrayList()).add(t);
        }
        final long now = System.currentTimeMillis();
        final int r = Math.max(4, Math.min(9, (int)(6.0 * Math.sqrt(this.zoom))));
        final boolean ultraLow = this.zoom < 0.1f;
        final boolean lowDetail = this.zoom < 0.2f;
        for (TrainMapData.StationEntry station : data.getStations()) {
            final String group = this.stationToGroup.get(station.id);
            final float[] lp = this.layoutPos.get(station.id);
            if (lp == null) {
                continue;
            }
            final int sx = this.toSX(lp[0]);
            final int sy = this.toSY(lp[1]);
            if (!this.isVisible(sx, sy, 50)) {
                continue;
            }
            if (ultraLow) {
                if (group != null && !this.expandedGroups.contains(group)) {
                    continue;
                }
                this.fillCircle(g, sx, sy, 2, -1429418787);
                final double d2 = Math.sqrt((mx - sx) * (mx - sx) + (my - sy) * (my - sy));
                if (d2 >= 8.0) {
                    continue;
                }
                this.hoveredStation = station;
            }
            else if (lowDetail) {
                final int col = -1428300306;
                this.fillCircle(g, sx, sy, Math.max(2, r - 2), col);
                final double d3 = Math.sqrt((mx - sx) * (mx - sx) + (my - sy) * (my - sy));
                if (d3 >= r + 4) {
                    continue;
                }
                this.hoveredStation = station;
            }
            else {
                final double d4 = Math.sqrt((mx - sx) * (mx - sx) + (my - sy) * (my - sy));
                if (d4 < r + 6) {
                    this.hoveredStation = station;
                }
                final boolean hovered = station == this.hoveredStation;
                if (this.searchHighlight.contains(station.id)) {
                    this.fillCircle(g, sx, sy, r + 8, -1996488960);
                    this.fillCircle(g, sx, sy, r + 6, 1157627648);
                }
                final List<TrainMapData.TrainEntry> here = docked.get(station.name);
                final boolean hasTrain = here != null && !here.isEmpty();
                int ring = station.assembling ? -13312 : -2236946;
                if (hasTrain) {
                    ring = -39356;
                }
                if (hovered) {
                    ring = -1;
                }
                if (this.selectedRoute != null) {
                    final UUID eid = this.effectiveMap.getOrDefault(station.id, station.id);
                    final List<Integer> stRts = this.nodeRoutes.get(eid);
                    final boolean onRoute = stRts != null && stRts.contains(this.selectedRoute);
                    if (!onRoute) {
                        ring = ((ring & 0xFFFFFF) | 0x30000000);
                    }
                }
                if (hasTrain) {
                    final float pulse = (float)(0.5 + 0.5 * Math.sin(now / 400.0));
                    final int alpha = (int)(30.0f + 40.0f * pulse);
                    this.fillCircle(g, sx, sy, r + 4, alpha << 24 | (ring & 0xFFFFFF));
                }
                List<Integer> stRoutes = this.nodeRoutes.get(station.id);
                if (stRoutes == null) {
                    final UUID eid2 = this.effectiveMap.getOrDefault(station.id, station.id);
                    stRoutes = this.nodeRoutes.get(eid2);
                }
                if (stRoutes != null && stRoutes.size() >= 2) {
                    this.drawInterchangeCircle(g, sx, sy, r, stRoutes);
                }
                this.fillCircle(g, sx, sy, r + 1, ring);
                this.fillCircle(g, sx, sy, r - 1, hasTrain ? -13430511 : -15921888);
                if (this.zoom <= 0.4f && !hovered) {
                    continue;
                }
                String label = station.name;
                if (label.length() > 22) {
                    label = label.substring(0, 20) + "..";
                }
                final int lw = this.font.width(label);
                final int ly = sy + r + 3;
                if (this.batchActive) {
                    this.batchQuad(sx - lw / 2 - 2, ly - 1, sx + lw / 2 + 2, ly + 10, -1610612736);
                    this.deferLabel(sx, ly, hasTrain ? -21880 : -3355427, label, true);
                }
                else {
                    g.fill(sx - lw / 2 - 2, ly - 1, sx + lw / 2 + 2, ly + 10, -1610612736);
                    g.drawCenteredString(this.font, label, sx, ly, hasTrain ? -21880 : -3355427);
                }
            }
        }
    }
    
    private void drawTrains(GuiGraphics g, TrainMapData data, Map<String, TrainMapData.StationEntry> byName, int mx, int my, float interp) {
        final long now = System.currentTimeMillis();
        final int size = Math.max(2, Math.min(8, (int)(5.0 * Math.sqrt(this.zoom))));
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final Integer ri = this.trainToRoute.get(train.id.toString());
            int color = (ri != null) ? this.routeColors.get(ri) : this.getColor(train.id);
            if (this.selectedRoute != null && (ri == null || !this.selectedRoute.equals(ri))) {
                color = ((color & 0xFFFFFF) | 0x20000000);
            }
            TrainMapData.StationEntry dest = this.findStationById(data, train.activeSegmentToId);
            if (dest == null) {
                dest = this.findStationById(data, train.destinationStationId);
            }
            if (dest == null) {
                dest = train.nextDestination.isEmpty() ? null : this.findStation(byName, train.nextDestination);
            }
            final TrainMapData.StationEntry prev = this.findPrevStation(train, byName, data);
            float dirX = 0.0f;
            float dirY = 1.0f;
            int dx;
            int dy;
            if (train.stopped) {
                TrainMapData.StationEntry at = this.findStationById(data, train.currentStationId);
                if (at == null) {
                    at = train.currentStationName.isEmpty() ? dest : byName.get(train.currentStationName);
                }
                if (at == null) {
                    at = dest;
                }
                if (at == null) {
                    continue;
                }
                final float[] lp = this.getEffectivePos(at.id);
                if (lp == null) {
                    continue;
                }
                dx = this.toSX(lp[0]);
                dy = this.toSY(lp[1]);
                if (this.countDockedAtStation(data, at.name) > 1) {
                    final float[] offset = this.trainStackOffset(train.id, size);
                    dx += Math.round(offset[0]);
                    dy += Math.round(offset[1]);
                }
            }
            else if (prev != null && dest != null && !prev.id.equals(dest.id)) {
                if (!this.canUseCachedTrainPosition(train, data, now)) {
                    continue;
                }
                final float[] ipos = this.getSmoothedTrainWorldPos(train, prev, dest);
                final float progress = this.estimateProgress(ipos[0], ipos[1], prev, dest);
                final UUID prevEid = this.effectiveMap.getOrDefault(prev.id, prev.id);
                final UUID destEid = this.effectiveMap.getOrDefault(dest.id, dest.id);
                final float[] lpA = this.getEffectivePos(prevEid);
                final float[] lpB = this.getEffectivePos(destEid);
                if (lpA == null) {
                    continue;
                }
                if (lpB == null) {
                    continue;
                }
                if (prevEid.equals(destEid)) {
                    dx = this.toSX(lpA[0]);
                    dy = this.toSY(lpA[1]);
                    final float[] offset = this.trainStackOffset(train.id, size);
                    dx += Math.round(offset[0]);
                    dy += Math.round(offset[1]);
                }
                else {
                final int ax = this.toSX(lpA[0]);
                final int ay = this.toSY(lpA[1]);
                final int bx = this.toSX(lpB[0]);
                final int by = this.toSY(lpB[1]);
                final float[] routePoint = this.routePoint(ax, ay, bx, by, progress, this.octilinearBlend);
                dx = Math.round(routePoint[0]);
                dy = Math.round(routePoint[1]);
                dirX = routePoint[2];
                dirY = routePoint[3];
                if (ri != null) {
                    final String ek = this.edgeKey(prevEid, destEid);
                    final List<Integer> eRIds = this.edgeRoutes.get(ek);
                    if (eRIds != null && eRIds.size() > 1) {
                        final int idx = eRIds.indexOf(ri);
                        if (idx >= 0) {
                            final float lw = Math.max(2.0f, Math.min(4.0f, 3.0f * (float)Math.sqrt(this.zoom)));
                            final float gp = lw + 1.5f;
                            final float offs = (idx - (eRIds.size() - 1) / 2.0f) * gp;
                            dx += (int)(-dirY * offs);
                            dy += (int)(dirX * offs);
                        }
                    }
                }
                }
            }
            else {
                if (dest == null) {
                    continue;
                }
                final float[] lp2 = this.getEffectivePos(dest.id);
                if (lp2 == null) {
                    continue;
                }
                dx = this.toSX(lp2[0]);
                dy = this.toSY(lp2[1]);
            }
            if (!this.isVisible(dx, dy, 50)) {
                continue;
            }
            final double d = Math.sqrt((mx - dx) * (mx - dx) + (my - dy) * (my - dy));
            if (d < size + 6) {
                this.hoveredTrain = train;
            }
            final boolean hovered = train == this.hoveredTrain;
            final int bodyLen = (int)(size * 2.2f);
            final int bodyW = Math.max(2, (int)(size * 1.1f));
            final int tx1 = dx - (int)(dirX * bodyLen / 2.0f);
            final int ty1 = dy - (int)(dirY * bodyLen / 2.0f);
            final int tx2 = dx + (int)(dirX * bodyLen / 2.0f);
            final int ty2 = dy + (int)(dirY * bodyLen / 2.0f);
            if (train.waitingForSignal) {
                final float pulse = (float)(0.5 + 0.5 * Math.sin(now / 350.0));
                final int alpha = (int)(50.0f + 60.0f * pulse);
                this.drawThickLine(g, tx1, ty1, tx2, ty2, alpha << 24 | 0xFFAA00, bodyW + 6);
            }
            else if (!train.stopped && train.speed > 0.01f && this.zoom > 0.22f) {
                this.drawTrainTrail(g, dx, dy, dirX, dirY, color, size, train.speed);
            }
            if (hovered) {
                this.drawThickLine(g, tx1, ty1, tx2, ty2, (color & 0xFFFFFF) | 0x40000000, bodyW + 4);
            }
            this.drawThickLine(g, tx1, ty1, tx2, ty2, this.darken(color, 0.5f), bodyW + 2);
            this.drawThickLine(g, tx1, ty1, tx2, ty2, color, bodyW);
            final int fx = dx + (int)(dirX * bodyLen / 4.0f);
            final int fy = dy + (int)(dirY * bodyLen / 4.0f);
            this.drawThickLine(g, dx, dy, fx, fy, this.brighten(color, 1.4f), bodyW - 1);
            if (train.stopped && !train.currentStationName.isEmpty()) {
                if (this.batchActive) {
                    this.batchQuad(dx - 1, dy - 1, dx + 2, dy + 2, -1);
                }
                else {
                    g.fill(dx - 1, dy - 1, dx + 2, dy + 2, -1);
                }
            }
            else if (train.waitingForSignal) {
                if (this.batchActive) {
                    this.batchQuad(dx - 2, dy, dx + 3, dy + 1, -52429);
                    this.batchQuad(dx, dy - 2, dx + 1, dy + 3, -52429);
                }
                else {
                    g.fill(dx - 2, dy, dx + 3, dy + 1, -52429);
                    g.fill(dx, dy - 2, dx + 1, dy + 3, -52429);
                }
            }
            if (train.waitingForSignal) {
                final int sigSize = Math.max(3, (int)(4.0 * Math.sqrt(this.zoom)));
                final int sigColor = (train.ticksWaitingForSignal > 200) ? -65536 : -48060;
                if (this.batchActive) {
                    this.batchQuad(dx - 1, dy - sigSize - size - 4, dx + 2, dy - sigSize - size - 3, sigColor);
                    this.batchQuad(dx - sigSize / 2, dy - size - 4, dx + sigSize / 2 + 1, dy - size - 3, sigColor);
                }
                else {
                    g.fill(dx - 1, dy - sigSize - size - 4, dx + 2, dy - sigSize - size - 3, sigColor);
                    g.fill(dx - sigSize / 2, dy - size - 4, dx + sigSize / 2 + 1, dy - size - 3, sigColor);
                }
                final int secs = train.ticksWaitingForSignal / 20;
                if (secs > 0 && this.zoom > 0.3f) {
                    if (this.batchActive) {
                        this.deferLabel(dx, dy + size + 2, sigColor, "" + secs, true);
                    }
                    else {
                        g.drawCenteredString(this.font, "" + secs, dx, dy + size + 2, sigColor);
                    }
                }
            }
            if (train.speed > 0.01f && !train.waitingForSignal && this.zoom > 0.4f) {
                this.drawThickLine(g, tx1, ty1, tx2, ty2, (color & 0xFFFFFF) | 0x28000000, bodyW + 3);
            }
            if (!train.waitingForSignal && train.speed > 0.01f && this.zoom > 0.25f) {
                final int bw = Math.max(10, size * 3);
                final int bh = 2;
                final int bx2 = dx - bw / 2;
                final int by2 = dy + size + 2;
                final int fw = Math.max(1, (int)(bw * Math.min(1.0f, train.speed * 20.0f / 25.0f)));
                if (this.batchActive) {
                    this.batchQuad(bx2 - 1, by2 - 1, bx2 + bw + 1, by2 + bh + 1, Integer.MIN_VALUE);
                    this.batchQuad(bx2, by2, bx2 + bw, by2 + bh, -13421739);
                    this.batchQuad(bx2, by2, bx2 + fw, by2 + bh, -14496666);
                }
                else {
                    g.fill(bx2 - 1, by2 - 1, bx2 + bw + 1, by2 + bh + 1, Integer.MIN_VALUE);
                    g.fill(bx2, by2, bx2 + bw, by2 + bh, -13421739);
                    g.fill(bx2, by2, bx2 + fw, by2 + bh, -14496666);
                }
            }
            if (this.zoom <= 0.15f) {
                continue;
            }
            String name = train.name;
            if (name.length() > 16) {
                name = name.substring(0, 14) + "..";
            }
            final String suffix = train.waitingForSignal ? " [SIG]" : (train.stopped ? " [STP]" : "");
            final String full = name + suffix;
            final int nw = this.font.width(full);
            final int lc = train.waitingForSignal ? -21965 : color;
            if (this.batchActive) {
                this.batchQuad(dx - nw / 2 - 2, dy - size - 12, dx + nw / 2 + 2, dy - size - 1, -1610612736);
                this.deferLabel(dx, dy - size - 11, lc, full, true);
            }
            else {
                g.fill(dx - nw / 2 - 2, dy - size - 12, dx + nw / 2 + 2, dy - size - 1, -1610612736);
                g.drawCenteredString(this.font, full, dx, dy - size - 11, lc);
            }
        }
    }

    private void drawTrainTrail(GuiGraphics g, int x, int y, float dirX, float dirY, int color, int size, float speed) {
        final float speedScale = Math.max(0.25f, Math.min(1.0f, Math.abs(speed) * 20.0f / 28.0f));
        final int trailLength = Math.max(8, Math.min(34, (int)(size * (3.0f + speedScale * 3.0f))));
        final int startX = x - Math.round(dirX * (size + 1));
        final int startY = y - Math.round(dirY * (size + 1));
        final int endX = x - Math.round(dirX * trailLength);
        final int endY = y - Math.round(dirY * trailLength);
        final int outer = (color & 0xFFFFFF) | 0x26000000;
        final int inner = (color & 0xFFFFFF) | 0x4A000000;
        this.drawThickLine(g, endX, endY, startX, startY, outer, Math.max(2, size));
        this.drawThickLine(g, (endX + startX) / 2, (endY + startY) / 2, startX, startY, inner, Math.max(1, size / 2));
    }
    
    private float[] routePoint(int ax, int ay, int bx, int by, float t, float blend) {
        final float linearX = ax + (bx - ax) * t;
        final float linearY = ay + (by - ay) * t;
        final float linearDx = (float)(bx - ax);
        final float linearDy = (float)(by - ay);
        final float linearLen = (float)Math.sqrt(linearDx * linearDx + linearDy * linearDy);
        float dirX = (linearLen > 1.0E-4f) ? (linearDx / linearLen) : 0.0f;
        float dirY = (linearLen > 1.0E-4f) ? (linearDy / linearLen) : 1.0f;
        if (blend <= 0.001f) {
            return new float[] { linearX, linearY, dirX, dirY };
        }
        final float[] octilinear = this.octilinearPoint(ax, ay, bx, by, t);
        final float blendedX = linearX + (octilinear[0] - linearX) * blend;
        final float blendedY = linearY + (octilinear[1] - linearY) * blend;
        dirX += (octilinear[2] - dirX) * blend;
        dirY += (octilinear[3] - dirY) * blend;
        final float dirLen = (float)Math.sqrt(dirX * dirX + dirY * dirY);
        if (dirLen > 1.0E-4f) {
            dirX /= dirLen;
            dirY /= dirLen;
        }
        return new float[] { blendedX, blendedY, dirX, dirY };
    }
    
    private float distanceToRouteShape(double px, double py, int ax, int ay, int bx, int by) {
        if (this.octilinearBlend <= 0.001f) {
            return this.distanceToSegment((float)px, (float)py, ax, ay, bx, by);
        }
        float best = Float.MAX_VALUE;
        float[] previous = this.routePoint(ax, ay, bx, by, 0.0f, this.octilinearBlend);
        for (int step = 1; step <= 12; ++step) {
            final float t = step / 12.0f;
            final float[] point = this.routePoint(ax, ay, bx, by, t, this.octilinearBlend);
            best = Math.min(best, this.distanceToSegment((float)px, (float)py, previous[0], previous[1], point[0], point[1]));
            previous = point;
        }
        return best;
    }
    
    private float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1.0f) {
            return (float)Math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay));
        }
        final float t = Math.max(0.0f, Math.min(1.0f, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
        final float cx = ax + dx * t;
        final float cy = ay + dy * t;
        return (float)Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
    }
    
    private float[] octilinearPoint(int ax, int ay, int bx, int by, float t) {
        final int ddx = bx - ax;
        final int ddy = by - ay;
        final int adx = Math.abs(ddx);
        final int ady = Math.abs(ddy);
        final int diag = Math.min(adx, ady);
        final int sx = (ddx >= 0) ? 1 : -1;
        final int sy = (ddy >= 0) ? 1 : -1;
        final int ex = ax + sx * diag;
        final int ey = ay + sy * diag;
        final float seg1Len = (float)(diag * Math.sqrt(2.0));
        final float edx2 = (float)(bx - ex);
        final float edy2 = (float)(by - ey);
        final float seg2Len = (float)Math.sqrt(edx2 * edx2 + edy2 * edy2);
        final float totalLen = seg1Len + seg2Len;
        if (totalLen < 0.001f) {
            return new float[] { (float)ax, (float)ay, 0.0f, 1.0f };
        }
        final float dist = t * totalLen;
        if (seg2Len < 0.001f || dist <= seg1Len) {
            final float s = (seg1Len > 0.001f) ? Math.min(1.0f, dist / seg1Len) : 0.0f;
            final float dxDir = sx * 0.7071f;
            final float dyDir = sy * 0.7071f;
            return new float[] { ax + (ex - ax) * s, ay + (ey - ay) * s, dxDir, dyDir };
        }
        final float s = Math.min(1.0f, (dist - seg1Len) / seg2Len);
        final float dxDir = (seg2Len > 0.001f) ? (edx2 / seg2Len) : 0.0f;
        final float dyDir = (seg2Len > 0.001f) ? (edy2 / seg2Len) : 1.0f;
        return new float[] { ex + (bx - ex) * s, ey + (by - ey) * s, dxDir, dyDir };
    }
    
    private TrainMapData.StationEntry findPrevStation(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, TrainMapData data) {
        TrainMapData.StationEntry station = this.findStationById(data, train.activeSegmentFromId);
        if (station != null) {
            return station;
        }
        station = this.findStationById(data, train.previousStationId);
        if (station != null) {
            return station;
        }
        if (train.scheduleStops.isEmpty() || train.nextDestination.isEmpty()) {
            return null;
        }
        int destIdx = -1;
        for (int i = 0; i < train.scheduleStops.size(); ++i) {
            if (this.matchesFilter(train.scheduleStops.get(i), train.nextDestination)) {
                destIdx = i;
                break;
            }
            final TrainMapData.StationEntry s = this.findStation(byName, train.scheduleStops.get(i));
            if (s != null && train.nextDestination.equals(s.name)) {
                destIdx = i;
                break;
            }
        }
        if (destIdx < 0) {
            return null;
        }
        final int prevIdx = (destIdx - 1 + train.scheduleStops.size()) % train.scheduleStops.size();
        return this.findStation(byName, train.scheduleStops.get(prevIdx));
    }

    private TrainMapData.StationEntry findStationById(TrainMapData data, UUID id) {
        if (data == null || id == null) {
            return null;
        }
        for (TrainMapData.StationEntry station : data.getStations()) {
            if (id.equals(station.id)) {
                return station;
            }
        }
        return null;
    }
    
    private float estimateProgress(float trainX, float trainZ, TrainMapData.StationEntry from, TrainMapData.StationEntry to) {
        final double dx = to.x - from.x;
        final double dz = to.z - from.z;
        final double totalDistSq = dx * dx + dz * dz;
        if (totalDistSq < 1.0) {
            return 0.5f;
        }
        final double tx = trainX - from.x;
        final double tz = trainZ - from.z;
        final double projected = (tx * dx + tz * dz) / totalDistSq;
        return Math.max(0.0f, Math.min(1.0f, (float)projected));
    }
    
    private void drawGroupButton(GuiGraphics g, int mx, int my) {
        final int bx = this.areaX + 4;
        final int by = this.areaY + 40;
        final int bh = 16;
        final String label = this.groupPatterns.isEmpty() ? "Groups [G]" : ("Groups (" + this.groupPatterns.size() + ") [G]");
        final int bw = this.font.width(label) + 10;
        final boolean hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, hovered ? -867941240 : -870178236);
        g.fill(bx, by, bx + bw, by + 1, 1090519039);
        g.drawString(this.font, label, bx + 5, by + 4, -4473891, false);
        this.groupBtnX = bx;
        this.groupBtnY = by;
        this.groupBtnW = bw;
        this.groupBtnH = bh;
    }
    
    private void drawGroupPopup(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, -2013265920);
        final int popW = 280;
        final int rowH = 14;
        final int headerH = 20;
        final int footerH = 28;
        final int padding = 6;
        int contentH = 0;
        for (String pattern : this.groupPatterns) {
            contentH += rowH;
            if (this.expandedGroups.contains(pattern)) {
                final List<UUID> members = this.groupMembers.get(pattern);
                if (members == null) {
                    continue;
                }
                contentH += members.size() * rowH;
            }
        }
        if (this.groupPatterns.isEmpty()) {
            contentH += rowH;
        }
        final int maxContentH = (int)(this.height * 0.7f) - headerH - footerH - padding * 2;
        final int visibleContentH = Math.min(contentH, maxContentH);
        final int popH = headerH + visibleContentH + footerH + padding * 2;
        final int popX = (this.width - popW) / 2;
        final int popY = (this.height - popH) / 2;
        g.fill(popX - 1, popY - 1, popX + popW + 1, popY + popH + 1, -12303258);
        g.fill(popX, popY, popX + popW, popY + popH, -267382744);
        g.fill(popX, popY, popX + popW, popY + headerH, -15066568);
        g.drawCenteredString(this.font, "§l Station Groups", popX + popW / 2, popY + 6, -3355393);
        final int closeBtnX = popX + popW - 16;
        final int closeBtnY = popY + 2;
        final boolean closeHovered = mx >= closeBtnX && mx < closeBtnX + 14 && my >= closeBtnY && my < closeBtnY + 14;
        g.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 14, closeHovered ? -5622989 : -11197918);
        g.drawCenteredString(this.font, "X", closeBtnX + 7, closeBtnY + 3, -21846);
        final int listX = popX + padding;
        final int listY = popY + headerH + padding;
        final int listW = popW - padding * 2;
        final int listH = visibleContentH;
        final int maxScroll = Math.max(0, contentH - listH);
        this.popupScroll = Math.max(0, Math.min(this.popupScroll, maxScroll));
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int cy = listY - this.popupScroll;
        if (this.groupPatterns.isEmpty()) {
            g.drawString(this.font, "§7No groups defined yet", listX + 4, cy + 2, -7829368, false);
            cy += rowH;
        }
        for (int gi = 0; gi < this.groupPatterns.size(); ++gi) {
            final String pattern2 = this.groupPatterns.get(gi);
            final List<UUID> members2 = this.groupMembers.get(pattern2);
            final int memberCount = (members2 != null) ? members2.size() : 0;
            final boolean expanded = this.expandedGroups.contains(pattern2);
            final boolean rowHovered = mx >= listX && mx < listX + listW && my >= cy && my < cy + rowH && my >= listY && my < listY + listH;
            g.fill(listX, cy, listX + listW, cy + rowH, rowHovered ? -14342843 : -15066576);
            g.fill(listX, cy + rowH - 1, listX + listW, cy + rowH, 553648127);
            final String arrow = expanded ? "\u25bc" : "\u25b6";
            g.drawString(this.font, arrow, listX + 3, cy + 3, -7829317, false);
            String displayName = pattern2;
            if (displayName.length() > 24) {
                displayName = displayName.substring(0, 22) + "..";
            }
            g.drawString(this.font, displayName, listX + 14, cy + 3, expanded ? -7798904 : -3355427, false);
            final String countStr = "(" + memberCount;
            int countX = listX + listW - this.font.width(countStr) - 20;
            g.drawString(this.font, countStr, countX, cy + 3, -7829351, false);
            final int delX = listX + listW - 14;
            final boolean delHovered = mx >= delX && mx < delX + 12 && my >= cy && my < cy + rowH && my >= listY && my < listY + listH;
            g.drawString(this.font, "X", delX + 1, cy + 3, delHovered ? -48060 : -5609882, false);
            cy += rowH;
            if (expanded && members2 != null) {
                for (UUID mid : members2) {
                    final TrainMapData.StationEntry ms = (this.currSnapshot != null) ? this.getStationById(this.currSnapshot, mid) : null;
                    String mName = (ms != null) ? ms.name : mid.toString().substring(0, 8);
                    if (mName.length() > 30) {
                        mName = mName.substring(0, 28) + "..";
                    }
                    g.fill(listX, cy, listX + listW, cy + rowH, -15461339);
                    g.drawString(this.font, "  §7" + mName, listX + 14, cy + 3, -6710887, false);
                    cy += rowH;
                }
            }
        }
        g.disableScissor();
        if (contentH > listH) {
            final int sbX = listX + listW - 3;
            final int sbFullH = listH;
            final int sbThumbH = Math.max(10, sbFullH * listH / contentH);
            final int sbThumbY = listY + (int)(this.popupScroll / (float)maxScroll * (sbFullH - sbThumbH));
            g.fill(sbX, listY, sbX + 3, listY + listH, 1090519039);
            g.fill(sbX, sbThumbY, sbX + 3, sbThumbY + sbThumbH, -1426063361);
        }
        final int footerY = popY + popH - footerH - padding + 4;
        g.fill(popX, footerY - 2, popX + popW, footerY - 1, 822083583);
        final int inputW = popW - padding * 2 - 40;
        final int inputX = popX + padding;
        final int inputY = footerY + 2;
        if (this.groupInput != null) {
            this.groupInput.setX(inputX);
            this.groupInput.setY(inputY);
            this.groupInput.setWidth(inputW);
            this.groupInput.setVisible(true);
            this.groupInput.render(g, mx, my, pt);
        }
        final int addBtnX = inputX + inputW + 4;
        final int addBtnW = 32;
        final int addBtnH = 16;
        final boolean addHovered = mx >= addBtnX && mx < addBtnX + addBtnW && my >= inputY && my < inputY + addBtnH;
        g.fill(addBtnX, inputY, addBtnX + addBtnW, inputY + addBtnH, addHovered ? -13408717 : -14531550);
        g.fill(addBtnX, inputY, addBtnX + addBtnW, inputY + 1, 1090519039);
        g.drawCenteredString(this.font, "Add", addBtnX + addBtnW / 2, inputY + 4, -7798904);
    }
    
    private void renderGroupTooltip(GuiGraphics g, int mx, int my, String pattern) {
        final List<String> lines = new ArrayList<String>();
        final String displayName = pattern.replace("*", "").trim();
        final List<UUID> members = this.groupMembers.get(pattern);
        lines.add("§f§l" + displayName + " Group");
        lines.add("§7Pattern: §f" + pattern);
        if (members != null) {
            lines.add("§7Stations: §f" + members.size());
            final boolean expanded = this.expandedGroups.contains(pattern);
            lines.add(expanded ? "§aExpanded §7(click to collapse)" : "§eCollapsed §7(click to expand)");
            int shown = 0;
            if (this.currSnapshot != null) {
                for (UUID mid : members) {
                    final TrainMapData.StationEntry ms = this.getStationById(this.currSnapshot, mid);
                    if (ms != null) {
                        lines.add("§8  §7" + ms.name);
                        if (++shown >= 8) {
                            lines.add("§8  ... and " + (members.size() - shown) + " more");
                            break;
                        }
                        continue;
                    }
                }
            }
        }
        this.renderTooltipBox(g, mx, my, lines);
    }
    
    private void renderStationTooltip(GuiGraphics g, int mx, int my, TrainMapData.StationEntry station) {
        final TrainMapData visibleData = this.getVisibleData();
        final List<String> lines = new ArrayList<String>();
        lines.add("§f§l" + station.name);
        lines.add("§7Pos: §f" + (int)station.x + ", " + (int)station.z);
        lines.add("§7Dim: §f" + station.dimension);
        if (station.assembling) {
            lines.add("§e\u2699 Assembling train");
        }
        if (visibleData != null) {
            for (TrainMapData.TrainEntry t : visibleData.getTrains()) {
                if (t.currentStationName.equals(station.name)) {
                }
            }
            for (TrainMapData.TrainEntry t : visibleData.getTrains()) {
                if (!t.stopped && this.matchesFilter(t.nextDestination, station.name)) {
                    lines.add("§e\u2192 §f" + t.name + " §7incoming (" + String.format("%.1f", t.speed * 20.0f) + " m/s)");
                }
            }
            for (TrainMapData.TrainEntry t : visibleData.getTrains()) {
                if (t.waitingForSignal && this.matchesFilter(t.nextDestination, station.name)) {
                    lines.add("§6\u26a0 §f" + t.name + " §7signal wait (" + t.ticksWaitingForSignal / 20 + "s)");
                }
            }
        }
        this.renderTooltipBox(g, mx, my, lines);
    }
    
    private void renderTrainTooltip(GuiGraphics g, int mx, int my, TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName) {
        this.renderTooltipBox(g, mx, my, this.buildTrainTooltipLines(train, byName));
    }

    private void renderPinnedTrainTooltip(GuiGraphics g, TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName) {
        final List<String> lines = this.buildTrainTooltipLines(train, byName);
        lines.add(1, "§8Pinned train info §7(click empty space or ESC to clear)");
        this.renderTooltipBoxAt(g, this.areaX + 8, this.areaY + 58, lines);
    }

    private List<String> buildTrainTooltipLines(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName) {
        final List<String> lines = new ArrayList<String>();
        lines.add("§f§l" + train.name);
        final Integer ri = this.trainToRoute.get(train.id.toString());
        if (ri != null) {
            final int routeTrainCount = this.routeTrains.get(ri).size();
            lines.add("§7Route: §f" + this.routeStops.get(ri).size() + " stops §8(+" + (routeTrainCount - 1) + " other trains)");
        }
        lines.add("§7Owner: §f" + train.ownerName);
        lines.add("§7Speed: §f" + String.format("%.1f", train.speed * 20.0f) + " m/s");
        if (train.waitingForSignal) {
            lines.add("§6\u26a0 Waiting for signal §7(" + train.ticksWaitingForSignal / 20 + "s)");
        }
        else if (train.stopped && !train.currentStationName.isEmpty()) {
            lines.add("§a\u25cf Stopped at §f" + train.currentStationName);
        }
        else if (train.stopped) {
            lines.add("§a\u25a0 Stopped");
        }
        else {
            lines.add("§2\u25b6 Moving");
        }
        if (!train.stopped && !train.waitingForSignal && !train.nextDestination.isEmpty() && byName != null) {
            final TrainMapData.StationEntry dest = this.findStation(byName, train.nextDestination);
            if (dest != null && train.speed > 0.1f) {
                final double dist = Math.sqrt((train.x - dest.x) * (train.x - dest.x) + (train.z - dest.z) * (train.z - dest.z));
                final float speedMs = train.speed * 20.0f;
                final float etaSecs = (float)(dist / speedMs);
                final String etaStr = (etaSecs < 60.0f) ? String.format("%.0fs", etaSecs) : String.format("%.1fmin", etaSecs / 60.0f);
                lines.add("§7ETA: §e" + etaStr + " §7(§f" + String.format("%.0f", dist) + " blocks§7)");
            }
        }
        if (!train.nextDestination.isEmpty()) {
            lines.add("§7Next: §e" + train.nextDestination);
        }
        if (!train.scheduleStops.isEmpty()) {
            lines.add("§7Route: §f" + String.join(" \u2192 ", train.scheduleStops));
        }
        return lines;
    }
    
    private void renderTooltipBox(GuiGraphics g, int mx, int my, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        int maxW = 0;
        for (String l : lines) {
            maxW = Math.max(maxW, this.font.width(l));
        }
        final int tw = maxW + 10;
        final int th = lines.size() * 11 + 6;
        int tx = mx + 12;
        int ty = my - 4;
        if (tx + tw > this.width - 4) {
            tx = mx - tw - 4;
        }
        if (ty + th > this.height - 4) {
            ty = this.height - th - 4;
        }
        if (ty < 4) {
            ty = 4;
        }
        this.renderTooltipBoxAt(g, tx, ty, lines);
    }

    private void renderTooltipBoxAt(GuiGraphics g, int tx, int ty, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        int maxW = 0;
        for (String l : lines) {
            maxW = Math.max(maxW, this.font.width(l));
        }
        final int tw = maxW + 10;
        final int th = lines.size() * 11 + 6;
        tx = Math.max(4, Math.min(this.width - tw - 4, tx));
        ty = Math.max(4, Math.min(this.height - th - 4, ty));
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, -14540220);
        g.fill(tx, ty, tx + tw, ty + th, -267382736);
        for (int i = 0; i < lines.size(); ++i) {
            g.drawString(this.font, (String)lines.get(i), tx + 5, ty + 3 + i * 11, -1, false);
        }
    }
    
    private void renderSummaryBar(GuiGraphics g, TrainMapData data) {
        final int barY = this.areaY + this.areaH + 2;
        final int st = data.getStations().size();
        final int tr = data.getTrains().size();
        final long moving = data.getTrains().stream().filter(t -> t.speed > 0.01f && !t.waitingForSignal).count();
        final long dockedC = data.getTrains().stream().filter(t -> !t.currentStationName.isEmpty()).count();
        final long sig = data.getTrains().stream().filter(t -> t.waitingForSignal).count();
        final int groupCount = (int)this.groupMembers.values().stream().filter(m -> !m.isEmpty()).count();
        final int effectiveNodes = this.lastLayoutIds.size();
        String s = String.format("§7Stations: §f%d", st);
        if (groupCount > 0) {
            s += String.format("  §7Groups: §9%d §7(§f%d§7 nodes)", groupCount, effectiveNodes);
        }
        if (this.availableDimensions.size() > 1 && !this.activeDimension.isEmpty()) {
            s += String.format("  §7Dim: §f%s", shortDimensionName(this.activeDimension));
        }
        s += String.format("  §7View: §f%s", this.viewMode.getLabel());
        s += String.format("  §7Routes: §d%d", this.routeStops.size());
        s += String.format("  §7Trains: §f%d  §7Moving: §a%d  §7Docked: §f%d", tr, moving, dockedC);
        if (sig > 0L) {
            s += String.format("  §6Signal: %d", sig);
        }
        g.drawString(this.font, s, this.areaX + 4, barY, this.TEXT, false);
        final String zs = String.format("%.0f%%", this.zoom * 100.0f);
        g.drawString(this.font, zs, this.areaX + this.areaW - this.font.width(zs) - 4, barY, this.DIM, false);
    }
    
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.showGroupPopup) {
            this.popupScroll -= (int)(scrollY * 14.0);
            return true;
        }
        if (!this.routeStops.isEmpty()) {
            final int legendW = 150;
            final int lx = this.areaX + this.areaW - legendW - 4;
            final int ly = this.areaY + 4;
            final int maxVisible = Math.min(this.routeStops.size(), Math.max(4, (this.areaH - 40) / 12));
            final int legendH = maxVisible * 12 + 10 + ((this.legendScroll > 0) ? 12 : 0) + ((this.legendScroll + maxVisible < this.routeStops.size()) ? 12 : 0);
            if (mx >= lx && mx <= lx + legendW && my >= ly && my <= ly + legendH) {
                this.legendScroll -= (int)scrollY;
                this.legendScroll = Math.max(0, Math.min(this.legendScroll, this.routeStops.size() - maxVisible));
                return true;
            }
        }
        final float oldZoom = this.zoom;
        this.zoom *= (float)Math.pow(1.08, scrollY);
        this.zoom = Math.max(0.01f, Math.min(20.0f, this.zoom));
        final float cx = (float)(mx - this.areaX - this.areaW * 0.5f) - this.panX;
        final float cy = (float)(my - this.areaY - this.areaH * 0.5f) - this.panY;
        this.panX -= cx * (this.zoom / oldZoom - 1.0f);
        this.panY -= cy * (this.zoom / oldZoom - 1.0f);
        return true;
    }
    
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.showGroupPopup && button == 0) {
            return this.handlePopupClick(mx, my);
        }
        final int sbx = this.areaX + 4;
        final int sby = this.areaY + 4;
        final int sbw = 160;
        final int sbh = 14;
        if (mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + sbh) {
            return this.searchFocused = true;
        }
        if (this.searchFocused) {
            this.searchFocused = false;
        }
        if (button == 0 && this.availableDimensions.size() > 1) {
            if (mx >= this.dimPrevX && mx < this.dimPrevX + this.dimPrevW && my >= this.dimPrevY && my < this.dimPrevY + this.dimPrevH) {
                this.cycleDimension(-1);
                return true;
            }
            if (mx >= this.dimNextX && mx < this.dimNextX + this.dimNextW && my >= this.dimNextY && my < this.dimNextY + this.dimNextH) {
                this.cycleDimension(1);
                return true;
            }
        }
        if (button == 0) {
            for (Map.Entry<TrainMapViewMode, int[]> entry : this.viewModeButtons.entrySet()) {
                final int[] bounds = entry.getValue();
                if (bounds != null && mx >= bounds[0] && mx < bounds[0] + bounds[2] && my >= bounds[1] && my < bounds[1] + bounds[3]) {
                    this.setViewMode(entry.getKey());
                    return true;
                }
            }
        }
        if (button == 0 && mx >= this.groupBtnX && mx < this.groupBtnX + this.groupBtnW && my >= this.groupBtnY && my < this.groupBtnY + this.groupBtnH) {
            this.openGroupPopup();
            return true;
        }
        if (!this.showGroupPopup && !this.routeStops.isEmpty() && button == 0) {
            final int maxVisible = Math.min(this.routeStops.size(), Math.max(4, (this.areaH - 40) / 12));
            final int lineH = 12;
            final int pad = 5;
            final int legendW = 150;
            final int lx = this.areaX + this.areaW - legendW - 4;
            final int ly = this.areaY + 4;
            int legendH = maxVisible * lineH + pad * 2;
            final boolean hasScrollUp = this.legendScroll > 0;
            final boolean hasScrollDown = this.legendScroll + maxVisible < this.routeStops.size();
            if (hasScrollUp) {
                legendH += lineH;
            }
            if (hasScrollDown) {
                legendH += lineH;
            }
            if (mx >= lx && mx <= lx + legendW && my >= ly && my <= ly + legendH) {
                int yOff = ly + pad;
                if (hasScrollUp) {
                    if (my >= yOff && my < yOff + lineH) {
                        this.legendScroll = Math.max(0, this.legendScroll - maxVisible);
                        return true;
                    }
                    yOff += lineH;
                }
                final int row = (int)((my - yOff) / lineH);
                if (row >= 0 && row < maxVisible) {
                    final int ri = this.legendScroll + row;
                    if (ri < this.routeStops.size()) {
                        this.toggleSelectedRoute(ri);
                        return true;
                    }
                }
                if (hasScrollDown) {
                    final int downY = yOff + maxVisible * lineH;
                    if (my >= downY && my < downY + lineH) {
                        this.legendScroll = Math.min(this.routeStops.size() - maxVisible, this.legendScroll + maxVisible);
                        return true;
                    }
                }
                return true;
            }
        }
        if (button == 0) {
            final TrainMapData visibleData = this.getVisibleData();
            if (visibleData != null) {
                final int gr = Math.max(8, Math.min(16, (int)(12.0 * Math.sqrt(this.zoom))));
                for (Map.Entry<String, UUID> entry : this.groupUUIDs.entrySet()) {
                    if (this.expandedGroups.contains(entry.getKey())) {
                        continue;
                    }
                    final float[] lp = this.layoutPos.get(entry.getValue());
                    if (lp == null) {
                        continue;
                    }
                    final int sx = this.toSX(lp[0]);
                    final int sy = this.toSY(lp[1]);
                    final double d = Math.sqrt((mx - sx) * (mx - sx) + (my - sy) * (my - sy));
                    if (d < gr + 4) {
                        this.draggedNode = entry.getValue();
                        return true;
                    }
                }
                final int sr = Math.max(4, Math.min(9, (int)(6.0 * Math.sqrt(this.zoom))));
                for (TrainMapData.StationEntry s : visibleData.getStations()) {
                    final float[] lp2 = this.layoutPos.get(s.id);
                    if (lp2 == null) {
                        continue;
                    }
                    final int sx2 = this.toSX(lp2[0]);
                    final int sy2 = this.toSY(lp2[1]);
                    final double d2 = Math.sqrt((mx - sx2) * (mx - sx2) + (my - sy2) * (my - sy2));
                    if (d2 < sr + 6) {
                        this.draggedNode = s.id;
                        return true;
                    }
                }
                if (this.hoveredTrain != null) {
                    this.pinnedTrainId = (this.pinnedTrainId != null && this.pinnedTrainId.equals(this.hoveredTrain.id)) ? null : this.hoveredTrain.id;
                    return true;
                }
                if (this.zoom > 0.2f) {
                    for (Map.Entry<String, List<Integer>> entry2 : this.edgeRoutes.entrySet()) {
                        final UUID[] edge = this.parseEdgeKey(entry2.getKey());
                        if (edge == null) {
                            continue;
                        }
                        final UUID eua = edge[0];
                        final UUID eub = edge[1];
                        final float[] pa = this.layoutPos.get(eua);
                        final float[] pb = this.layoutPos.get(eub);
                        if (pa == null || pb == null) {
                            continue;
                        }
                        final int eax = this.toSX(pa[0]);
                        final int eay = this.toSY(pa[1]);
                        final int ebx = this.toSX(pb[0]);
                        final int eby = this.toSY(pb[1]);
                        if (this.distanceToRouteShape(mx, my, eax, eay, ebx, eby) >= 6.0f) {
                            continue;
                        }
                        final List<Integer> rIds = entry2.getValue();
                        final int newSel = rIds.get(0);
                        this.toggleSelectedRoute(newSel);
                        return true;
                    }
                }
            }
            if (this.pinnedTrainId != null) {
                this.pinnedTrainId = null;
            }
            if (this.selectedRoute != null) {
                this.clearSelectedRoute();
            }
            this.panning = true;
            this.panStartMX = mx;
            this.panStartMY = my;
            this.panStartPX = this.panX;
            this.panStartPY = this.panY;
            return true;
        }
        if (button == 2) {
            this.panning = true;
            this.panStartMX = mx;
            this.panStartMY = my;
            this.panStartPX = this.panX;
            this.panStartPY = this.panY;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    
    private void openGroupPopup() {
        this.showGroupPopup = true;
        this.popupScroll = 0;
        if (this.groupInput != null) {
            this.groupInput.setVisible(true);
            this.groupInput.setFocused(true);
            this.groupInput.setValue("");
        }
    }
    
    private void closeGroupPopup() {
        this.showGroupPopup = false;
        if (this.groupInput != null) {
            this.groupInput.setVisible(false);
            this.groupInput.setFocused(false);
        }
    }
    
    private boolean handlePopupClick(double mx, double my) {
        final int popW = 280;
        final int rowH = 14;
        final int headerH = 20;
        final int footerH = 28;
        final int padding = 6;
        int contentH = 0;
        for (String pattern : this.groupPatterns) {
            contentH += rowH;
            if (this.expandedGroups.contains(pattern)) {
                final List<UUID> members = this.groupMembers.get(pattern);
                if (members == null) {
                    continue;
                }
                contentH += members.size() * rowH;
            }
        }
        if (this.groupPatterns.isEmpty()) {
            contentH += rowH;
        }
        final int maxContentH = (int)(this.height * 0.7f) - headerH - footerH - padding * 2;
        final int visibleContentH = Math.min(contentH, maxContentH);
        final int popH = headerH + visibleContentH + footerH + padding * 2;
        final int popX = (this.width - popW) / 2;
        final int popY = (this.height - popH) / 2;
        final int closeBtnX = popX + popW - 16;
        final int closeBtnY = popY + 2;
        if (mx >= closeBtnX && mx < closeBtnX + 14 && my >= closeBtnY && my < closeBtnY + 14) {
            this.closeGroupPopup();
            return true;
        }
        if (mx < popX || mx > popX + popW || my < popY || my > popY + popH) {
            this.closeGroupPopup();
            return true;
        }
        if (this.groupInput != null && this.groupInput.isVisible() && this.groupInput.mouseClicked(mx, my, 0)) {
            return true;
        }
        final int inputW = popW - padding * 2 - 40;
        final int inputX = popX + padding;
        final int footerY = popY + popH - footerH - padding + 4;
        final int inputY = footerY + 2;
        final int addBtnX = inputX + inputW + 4;
        final int addBtnW = 32;
        final int addBtnH = 16;
        if (mx >= addBtnX && mx < addBtnX + addBtnW && my >= inputY && my < inputY + addBtnH) {
            final String val = (this.groupInput != null) ? this.groupInput.getValue().trim() : "";
            if (!val.isEmpty()) {
                this.addGroup(val);
                this.groupInput.setValue("");
            }
            return true;
        }
        final int listX = popX + padding;
        final int listY = popY + headerH + padding;
        final int listW = popW - padding * 2;
        final int listH = visibleContentH;
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int cy = listY - this.popupScroll;
            int gi = 0;
            while (gi < this.groupPatterns.size()) {
                final String pattern2 = this.groupPatterns.get(gi);
                final List<UUID> members2 = this.groupMembers.get(pattern2);
                final boolean expanded = this.expandedGroups.contains(pattern2);
                if (my >= cy && my < cy + rowH && cy + rowH > listY && cy < listY + listH) {
                    final int delX = listX + listW - 14;
                    if (mx >= delX && mx < delX + 12) {
                        this.removeGroup(pattern2);
                        return true;
                    }
                    this.toggleGroupExpand(pattern2);
                    return true;
                }
                else {
                    cy += rowH;
                    if (expanded && members2 != null) {
                        cy += members2.size() * rowH;
                    }
                    ++gi;
                }
            }
        }
        return true;
    }
    
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            this.draggedNode = null;
            this.panning = false;
            return true;
        }
        if (button == 2) {
            this.panning = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (this.showGroupPopup) {
            return true;
        }
        if (this.draggedNode != null) {
            final float[] lp = this.layoutPos.get(this.draggedNode);
            if (lp != null) {
                lp[0] = this.toLX(mx);
                lp[1] = this.toLY(my);
            }
            final float[] target = this.targetLayoutPos.get(this.draggedNode);
            if (target != null) {
                target[0] = this.toLX(mx);
                target[1] = this.toLY(my);
            }
            return true;
        }
        if (this.panning) {
            this.panX = this.panStartPX + (float)(mx - this.panStartMX);
            this.panY = this.panStartPY + (float)(my - this.panStartMY);
            return true;
        }
        return super.mouseDragged(mx, my, button, ddx, ddy);
    }
    
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (this.showGroupPopup) {
                this.closeGroupPopup();
                return true;
            }
            if (this.searchFocused) {
                this.searchFocused = false;
                this.searchQuery = "";
                this.updateSearchHighlight();
                return true;
            }
            if (this.pinnedTrainId != null) {
                this.pinnedTrainId = null;
                return true;
            }
            if (this.selectedRoute != null) {
                this.clearSelectedRoute();
                return true;
            }
            if (this.parentScreen != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
            else {
                this.onClose();
            }
            return true;
        }
        else {
            if (this.searchFocused && key != 256) {
                if (key == 259 && !this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    this.updateSearchHighlight();
                }
                return true;
            }
            if (this.showGroupPopup) {
                if (key == 257 && this.groupInput != null && this.groupInput.isFocused()) {
                    final String val = this.groupInput.getValue().trim();
                    if (!val.isEmpty()) {
                        this.addGroup(val);
                        this.groupInput.setValue("");
                    }
                    return true;
                }
                if (this.groupInput != null && this.groupInput.isFocused()) {
                    if (!this.groupInput.keyPressed(key, scan, mods)) {}
                    return true;
                }
                return true;
            }
            else {
                if (key == 71) {
                    this.openGroupPopup();
                    return true;
                }
                if (key == 82 && this.currSnapshot != null) {
                    this.forceRelayout();
                    this.panX = 0.0f;
                    this.panY = 0.0f;
                    this.zoom = this.initialZoom;
                    return true;
                }
                if (key == 70 && !this.showGroupPopup) {
                    this.zoom = this.initialZoom;
                    this.panX = 0.0f;
                    this.panY = 0.0f;
                    return true;
                }
                if (key == 77 && !this.showGroupPopup) {
                    this.cycleViewMode(1);
                    return true;
                }
                if (key == 79 && !this.showGroupPopup) {
                    this.setOctilinearMode(!this.octilinearMode);
                    return true;
                }
                return super.keyPressed(key, scan, mods);
            }
        }
    }
    
    public boolean charTyped(char c, int mods) {
        if (this.searchFocused) {
            if (this.searchQuery.length() < 32) {
                this.searchQuery += c;
            }
            this.updateSearchHighlight();
            return true;
        }
        if (this.showGroupPopup && this.groupInput != null && this.groupInput.isFocused()) {
            return this.groupInput.charTyped(c, mods);
        }
        return this.showGroupPopup || super.charTyped(c, mods);
    }
    
    public boolean isPauseScreen() {
        return false;
    }
    
    private TrainMapData.StationEntry findStation(Map<String, TrainMapData.StationEntry> byName, String filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        final TrainMapData.StationEntry exact = byName.get(filter);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, TrainMapData.StationEntry> e : byName.entrySet()) {
            if (this.matchesFilter(filter, e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private Map<String, TrainMapData.StationEntry> indexStationsByName(List<TrainMapData.StationEntry> stations) {
        final LinkedHashMap<String, TrainMapData.StationEntry> byName = new LinkedHashMap<String, TrainMapData.StationEntry>();
        final ArrayList<TrainMapData.StationEntry> ordered = new ArrayList<TrainMapData.StationEntry>(stations);
        ordered.sort((a, b) -> {
            final int nameCompare = a.name.compareToIgnoreCase(b.name);
            return nameCompare != 0 ? nameCompare : a.id.compareTo(b.id);
        });
        for (TrainMapData.StationEntry station : ordered) {
            byName.putIfAbsent(station.name, station);
        }
        return byName;
    }

    private TrainMapData.TrainEntry findTrainById(TrainMapData data, UUID id) {
        if (data == null || id == null) {
            return null;
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            if (train.id.equals(id)) {
                return train;
            }
        }
        return null;
    }

    private int countDockedAtStation(TrainMapData data, String stationName) {
        if (data == null || stationName == null || stationName.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            if (train.stopped && stationName.equals(train.currentStationName)) {
                ++count;
            }
        }
        return count;
    }

    private float[] trainStackOffset(UUID trainId, int markerSize) {
        final int bucket = Math.floorMod(trainId.hashCode(), 8);
        final double angle = bucket * Math.PI * 2.0 / 8.0;
        final float distance = Math.max(5.0f, markerSize + 4.0f);
        return new float[] { (float)Math.cos(angle) * distance, (float)Math.sin(angle) * distance };
    }
    
    private boolean matchesFilter(String filter, String name) {
        return TrainMapData.matchesStationFilter(filter, name);
    }
    
    private TrainMapData.StationEntry getStationById(TrainMapData data, UUID id) {
        for (TrainMapData.StationEntry s : data.getStations()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
    
    private void drawLegend(GuiGraphics g) {
        if (this.routeStops.isEmpty()) {
            return;
        }
        final int lineH = 12;
        final int pad = 5;
        final int legendW = 150;
        final int maxVisible = Math.min(this.routeStops.size(), Math.max(4, (this.areaH - 40) / lineH));
        this.legendScroll = Math.max(0, Math.min(this.legendScroll, this.routeStops.size() - maxVisible));
        int legendH = maxVisible * lineH + pad * 2;
        final boolean hasScrollUp = this.legendScroll > 0;
        final boolean hasScrollDown = this.legendScroll + maxVisible < this.routeStops.size();
        if (hasScrollUp) {
            legendH += lineH;
        }
        if (hasScrollDown) {
            legendH += lineH;
        }
        final int lx = this.areaX + this.areaW - legendW - 4;
        final int ly = this.areaY + 4;
        g.fill(lx - 1, ly - 1, lx + legendW + 1, ly + legendH + 1, -1441129926);
        g.fill(lx, ly, lx + legendW, ly + legendH, -871888872);
        int yOff = ly + pad;
        if (hasScrollUp) {
            g.drawCenteredString(this.font, "\u25b2 scroll up", lx + legendW / 2, yOff + 1, -10066296);
            yOff += lineH;
        }
        for (int vi = 0; vi < maxVisible; ++vi) {
            final int ri = this.legendScroll + vi;
            if (ri >= this.routeStops.size()) {
                break;
            }
            final int ry = yOff + vi * lineH;
            final int col = this.routeColors.get(ri);
            final boolean isSelected = this.selectedRoute != null && this.selectedRoute.equals(ri);
            g.fill(lx, ry - 1, lx + legendW, ry + lineH, isSelected ? -14013872 : 0);
            if (isSelected) {
                g.fill(lx, ry - 1, lx + 2, ry + lineH, (int)this.routeColors.get(ri));
            }
            g.fill(lx + pad, ry + 2, lx + pad + 12, ry + lineH - 2, col);
            final String name = this.getRouteLegendName(ri);
            final int nameColor = isSelected ? -1 : -3355427;
            g.drawString(this.font, name, lx + pad + 15, ry + 2, nameColor, false);
        }
        if (hasScrollDown) {
            final int moreY = yOff + maxVisible * lineH;
            g.drawCenteredString(this.font, "\u25bc " + (this.routeStops.size() - this.legendScroll - maxVisible) + " more", lx + legendW / 2, moreY + 1, -10066296);
        }
    }
    
    private String getRouteLegendName(int idx) {
        final List<String> stops = this.routeStops.get(idx);
        if (stops.isEmpty()) {
            return "Route " + (idx + 1);
        }
        String first = stops.get(0);
        String last = stops.get(stops.size() - 1);
        if (first.length() > 9) {
            first = first.substring(0, 8);
        }
        if (last.length() > 9) {
            last = last.substring(0, 8);
        }
        final int trains = this.routeTrains.get(idx).size();
        return first + "\u2192" + last + " §8x" + trains;
    }
    
    private void drawGrid(GuiGraphics g) {
        final int step = Math.max(20, (int)(60.0f * this.zoom));
        final int offX = (int)this.panX % step;
        final int offY = (int)this.panY % step;
        for (int gx = this.areaX + offX; gx < this.areaX + this.areaW; gx += step) {
            g.fill(gx, this.areaY, gx + 1, this.areaY + this.areaH, 150994943);
        }
        for (int gy = this.areaY + offY; gy < this.areaY + this.areaH; gy += step) {
            g.fill(this.areaX, gy, this.areaX + this.areaW, gy + 1, 150994943);
        }
    }
    
    private void beginBatch(GuiGraphics g) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader((Supplier)GameRenderer::getPositionColorShader);
        this.batchMatrix = g.pose().last().pose();
        this.batchBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        this.batchActive = true;
        this.deferredLabels.clear();
    }
    
    private void endBatch() {
        if (this.batchActive) {
            MeshData meshData = this.batchBuilder.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
            this.batchActive = false;
            this.batchBuilder = null;
        }
    }
    
    private void batchQuad(int x1, int y1, int x2, int y2, int color) {
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float green = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        this.batchBuilder.addVertex(this.batchMatrix, (float)x1, (float)y2, 0.0f).setColor(r, green, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, (float)x2, (float)y2, 0.0f).setColor(r, green, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, (float)x2, (float)y1, 0.0f).setColor(r, green, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, (float)x1, (float)y1, 0.0f).setColor(r, green, b, a);
    }
    
    private void deferLabel(int x, int y, int color, String text, boolean centered) {
        this.deferredLabels.add(new Object[] { x, y, color, text, centered });
    }
    
    private void renderDeferredLabels(GuiGraphics g) {
        for (Object[] l : this.deferredLabels) {
            final int x = (int)l[0];
            final int y = (int)l[1];
            final int color = (int)l[2];
            final String text = (String)l[3];
            final boolean centered = (boolean)l[4];
            if (centered) {
                g.drawCenteredString(this.font, text, x, y, color);
            }
            else {
                g.drawString(this.font, text, x, y, color, false);
            }
        }
    }
    
    private void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) {
            return;
        }
        if (r > 4) {
            final int fringeColor = (color & 0xFFFFFF) | 0x40000000;
            for (int fdy = -(r + 1); fdy <= r + 1; fdy += 2) {
                final int fdx = (int)Math.sqrt((r + 1) * (r + 1) - fdy * fdy);
                final int innerDx = (int)Math.sqrt((double)Math.max(0L, r * (long)r - fdy * (long)fdy));
                if (this.batchActive) {
                    this.batchQuad(cx - fdx, cy + fdy, cx - innerDx, cy + fdy + 2, fringeColor);
                    this.batchQuad(cx + innerDx, cy + fdy, cx + fdx + 1, cy + fdy + 2, fringeColor);
                }
                else {
                    g.fill(cx - fdx, cy + fdy, cx - innerDx, cy + fdy + 2, fringeColor);
                    g.fill(cx + innerDx, cy + fdy, cx + fdx + 1, cy + fdy + 2, fringeColor);
                }
            }
        }
        if (this.batchActive) {
            if (r <= 2) {
                this.batchQuad(cx - r, cy - r, cx + r + 1, cy + r + 1, color);
            }
            else {
                for (int dy = -r; dy <= r; dy += 2) {
                    final int dx = (int)Math.sqrt(r * r - dy * dy);
                    this.batchQuad(cx - dx, cy + dy, cx + dx + 1, cy + dy + 2, color);
                }
            }
        }
        else if (r <= 2) {
            g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, color);
        }
        else {
            for (int dy = -r; dy <= r; dy += 2) {
                final int dx = (int)Math.sqrt(r * r - dy * dy);
                g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 2, color);
            }
        }
    }
    
    private void batchRotatedLine(float x1, float y1, float x2, float y2, int color, float thickness) {
        final float ddx = x2 - x1;
        final float ddy = y2 - y1;
        final float len = (float)Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 0.5f) {
            this.batchQuad((int)(x1 - thickness * 0.5f), (int)(y1 - thickness * 0.5f), (int)(x1 + thickness * 0.5f + 1.0f), (int)(y1 + thickness * 0.5f + 1.0f), color);
            return;
        }
        final float h = thickness * 0.5f;
        final float nx = -ddy / len * h;
        final float ny = ddx / len * h;
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float gr = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        this.batchBuilder.addVertex(this.batchMatrix, x1 + nx, y1 + ny, 0.0f).setColor(r, gr, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, x2 + nx, y2 + ny, 0.0f).setColor(r, gr, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, x2 - nx, y2 - ny, 0.0f).setColor(r, gr, b, a);
        this.batchBuilder.addVertex(this.batchMatrix, x1 - nx, y1 - ny, 0.0f).setColor(r, gr, b, a);
    }
    
    private void drawThickLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (this.batchActive) {
            this.batchRotatedLine((float)x1, (float)y1, (float)x2, (float)y2, color, (float)thickness);
            return;
        }
        final float dx = (float)(x2 - x1);
        final float dy = (float)(y2 - y1);
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0f) {
            g.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }
        final int stepSize = Math.max(1, thickness / 2);
        final int steps = Math.max(1, (int)(len / stepSize));
        final int half = thickness / 2;
        for (int i = 0; i <= steps; ++i) {
            final float t = i / (float)steps;
            final int px = (int)(x1 + dx * t);
            final int py = (int)(y1 + dy * t);
            g.fill(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }
    
    private void drawInterchangeArc(GuiGraphics g, int cx, int cy, int arcR, int thickness, float startDeg, float endDeg, int color) {
        final float startRad = (float)Math.toRadians(startDeg - 90.0f);
        final float endRad = (float)Math.toRadians(endDeg - 90.0f);
        final float stepDeg = Math.max(3.0f, (float)(180.0 / (3.141592653589793 * arcR)));
        final float stepRad = (float)Math.toRadians(stepDeg);
        final int half = thickness / 2 + 1;
        for (float a = startRad; a <= endRad + 0.01f; a += stepRad) {
            final float ac = Math.min(a, endRad);
            final int x = cx + (int)(arcR * Math.cos(ac));
            final int y = cy + (int)(arcR * Math.sin(ac));
            if (this.batchActive) {
                this.batchQuad(x - half, y - half, x + half + 1, y + half + 1, color);
            }
            else {
                g.fill(x - half, y - half, x + half + 1, y + half + 1, color);
            }
        }
    }
    
    private void drawInterchangeCircle(GuiGraphics g, int cx, int cy, int stationR, List<Integer> routeIndices) {
        if (routeIndices == null || routeIndices.size() < 2) {
            return;
        }
        final int n = routeIndices.size();
        final int arcR = stationR + 5;
        final int thickness = 4;
        final float gapDeg = Math.min(8.0f, 360.0f / n * 0.15f);
        final float segDeg = (360.0f - n * gapDeg) / n;
        for (int i = 0; i < n; ++i) {
            final int ri = routeIndices.get(i);
            if (ri < this.routeColors.size()) {
                int color = this.routeColors.get(ri);
                if (this.selectedRoute != null && !this.selectedRoute.equals(ri)) {
                    color = ((color & 0xFFFFFF) | 0x30000000);
                }
                else {
                    color = ((color & 0xFFFFFF) | 0xDD000000);
                }
                final float startDeg = i * (segDeg + gapDeg);
                final float endDeg = startDeg + segDeg;
                this.drawInterchangeArc(g, cx, cy, arcR, thickness, startDeg, endDeg, color);
            }
        }
    }
    
    private void drawDirectionArrow(GuiGraphics g, int ax, int ay, int bx, int by, int color, int lineW) {
        final float edx = (float)(bx - ax);
        final float edy = (float)(by - ay);
        final float len = (float)Math.sqrt(edx * edx + edy * edy);
        if (len < 30.0f) {
            return;
        }
        final float udx = edx / len;
        final float udy = edy / len;
        final float pdx = -udy;
        final float pdy = udx;
        final int midX = ax + (int)(edx * 0.6f);
        final int midY = ay + (int)(edy * 0.6f);
        final int arrowLen = Math.max(4, lineW + 3);
        final int tx = midX + (int)(udx * arrowLen);
        final int ty = midY + (int)(udy * arrowLen);
        final int wx1 = midX - (int)(udx * arrowLen / 2.0f) + (int)(pdx * arrowLen);
        final int wy1 = midY - (int)(udy * arrowLen / 2.0f) + (int)(pdy * arrowLen);
        final int wx2 = midX - (int)(udx * arrowLen / 2.0f) - (int)(pdx * arrowLen);
        final int wy2 = midY - (int)(udy * arrowLen / 2.0f) - (int)(pdy * arrowLen);
        final int arrowThick = Math.max(1, lineW - 1);
        this.drawThickLine(g, wx1, wy1, tx, ty, color, arrowThick);
        this.drawThickLine(g, wx2, wy2, tx, ty, color, arrowThick);
    }
    
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }
    
    private int brighten(int color, float f) {
        final int a = color >> 24 & 0xFF;
        final int r = Math.min(255, (int)((color >> 16 & 0xFF) * f));
        final int gr = Math.min(255, (int)((color >> 8 & 0xFF) * f));
        final int b = Math.min(255, (int)((color & 0xFF) * f));
        return a << 24 | r << 16 | gr << 8 | b;
    }
    
    private int darken(int color, float f) {
        return this.brighten(color, f);
    }
    
    private float fdist(float x1, float y1, float x2, float y2) {
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }
    
    private void assignColors(TrainMapData data) {
        for (TrainMapData.TrainEntry t : data.getTrains()) {
            if (!this.trainColors.containsKey(t.id)) {
                this.trainColors.put(t.id, TrainMapScreen.PALETTE[this.nextColorIdx % TrainMapScreen.PALETTE.length]);
                ++this.nextColorIdx;
            }
        }
    }
    
    private int getColor(UUID id) {
        return this.trainColors.getOrDefault(id, -3355444);
    }

    private static final class TrainMotionState {
        String segmentKey = "";
        float progress;
        long lastFrameMs;
    }
    
    static {
        PALETTE = new int[] { -11549705, -36797, -10044566, -5552196, -13784, -1092784, -14235942, -1294214, -7508381, -8875876, -22746, -10720320 };
    }
}
