package com.createstats.display;

import com.createstats.ModConfiguration;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StatsDisplaySettings
{
    private GraphType graphType;
    private StatsColorScheme colorScheme;
    private TimeRange timeRange;
    private int customAccentColor;
    private boolean showGrid;
    private boolean showAxisLabels;
    private boolean showMinMaxAvg;
    private boolean showValueOverlay;
    private boolean doubleSided;
    private boolean autoScale;
    private boolean predictionEnabled;
    private float predictionRatio;
    private DisplayTheme displayTheme;
    private int cycleInterval;
    private final Set<String> hiddenChannelIds;
    private DisplayMode displayMode;
    private boolean aeroFlightDeckEnabled;
    private boolean aerolineDirectorEnabled;
    private String dashboardMainChannelId;
    private String preferredSourceId;
    private NoseSetting noseSetting;
    private VehiclePreset vehiclePreset;
    private BattleBoardPreset battleBoardPreset;
    private boolean dashboardLayoutLocked;
    private int clockFormat;
    private PanelHudSettings panelHudSettings;
    private CustomScreenLayout customScreenLayout;
    private boolean customScreenLayoutEnabled;
    private final ArrayList<CustomScreenProfile> customScreenProfiles;
    private String activeCustomScreenProfileId;
    private float analogGaugeMin;
    private float analogGaugeMax;
    private float analogZoneYellow;
    private float analogZoneRed;
    private boolean analogShowZones;
    private boolean analogShowGlass;
    private boolean analogShowRivets;
    private InstrumentStyleFamily instrumentStyleFamily;
    private InstrumentDensityPreset instrumentDensityPreset;
    private InstrumentEffectPreset instrumentEffectPreset;
    private InstrumentStyleOptions instrumentStyleOptions;
    private boolean topologyShowLabels;
    private boolean topologyAnimateEdges;
    private boolean topologyColorByStress;
    private int topologyMaxNodes;
    private TrainMapRenderQuality trainMapRenderQuality;
    private static final int[] CYCLE_SPEEDS;
    private static final String[] CYCLE_LABELS;
    
    public StatsDisplaySettings() {
        this.graphType = GraphType.LINE_CHART;
        this.colorScheme = StatsColorScheme.DARK;
        this.timeRange = TimeRange.FIVE_MIN;
        this.customAccentColor = -16711800;
        this.showGrid = true;
        this.showAxisLabels = true;
        this.showMinMaxAvg = true;
        this.showValueOverlay = false;
        this.doubleSided = false;
        this.autoScale = true;
        this.predictionEnabled = false;
        this.predictionRatio = 0.5f;
        this.displayTheme = DisplayTheme.DARK;
        this.cycleInterval = 60;
        this.hiddenChannelIds = new HashSet<String>();
        this.displayMode = DisplayMode.AUTO;
        this.aeroFlightDeckEnabled = false;
        this.aerolineDirectorEnabled = false;
        this.dashboardMainChannelId = "";
        this.preferredSourceId = "";
        this.noseSetting = NoseSetting.UNSET;
        this.vehiclePreset = VehiclePreset.AUTO;
        this.battleBoardPreset = BattleBoardPreset.STRATEGIC_OVERVIEW;
        this.dashboardLayoutLocked = false;
        this.clockFormat = 0;
        this.panelHudSettings = new PanelHudSettings();
        this.customScreenLayout = null;
        this.customScreenLayoutEnabled = false;
        this.customScreenProfiles = new ArrayList<CustomScreenProfile>();
        this.activeCustomScreenProfileId = "";
        this.analogGaugeMin = 0.0f;
        this.analogGaugeMax = 0.0f;
        this.analogZoneYellow = 0.6f;
        this.analogZoneRed = 0.8f;
        this.analogShowZones = true;
        this.analogShowGlass = true;
        this.analogShowRivets = true;
        this.instrumentStyleFamily = InstrumentStyleFamily.LEGACY_ANALOG;
        this.instrumentDensityPreset = InstrumentDensityPreset.MEDIUM;
        this.instrumentEffectPreset = InstrumentEffectPreset.BALANCED;
        this.instrumentStyleOptions = new InstrumentStyleOptions();
        this.topologyShowLabels = true;
        this.topologyAnimateEdges = true;
        this.topologyColorByStress = true;
        this.topologyMaxNodes = defaultTopologyMaxNodes();
        this.trainMapRenderQuality = TrainMapRenderQuality.LOW;
    }
    
    public GraphType getGraphType() {
        return this.graphType;
    }
    
    public void setGraphType(GraphType graphType) {
        this.graphType = ((graphType != null) ? graphType : GraphType.LINE_CHART);
    }
    
    public StatsColorScheme getColorScheme() {
        return this.colorScheme;
    }
    
    public void setColorScheme(StatsColorScheme colorScheme) {
        this.colorScheme = ((colorScheme != null) ? colorScheme : StatsColorScheme.DARK);
    }
    
    public TimeRange getTimeRange() {
        return this.timeRange;
    }
    
    public void setTimeRange(TimeRange timeRange) {
        this.timeRange = ((timeRange != null) ? timeRange : TimeRange.FIVE_MIN);
    }
    
    public int getCustomAccentColor() {
        return this.customAccentColor;
    }
    
    public void setCustomAccentColor(int color) {
        this.customAccentColor = color;
    }
    
    public boolean showGrid() {
        return this.showGrid;
    }
    
    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }
    
    public boolean showAxisLabels() {
        return this.showAxisLabels;
    }
    
    public void setShowAxisLabels(boolean show) {
        this.showAxisLabels = show;
    }
    
    public boolean showMinMaxAvg() {
        return this.showMinMaxAvg;
    }
    
    public void setShowMinMaxAvg(boolean show) {
        this.showMinMaxAvg = show;
    }
    
    public boolean showValueOverlay() {
        return this.showValueOverlay;
    }
    
    public void setShowValueOverlay(boolean show) {
        this.showValueOverlay = show;
    }
    
    public boolean isDoubleSided() {
        return this.doubleSided;
    }
    
    public void setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
    }
    
    public boolean isAutoScale() {
        return this.autoScale;
    }
    
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }
    
    public boolean isPredictionEnabled() {
        return this.predictionEnabled;
    }
    
    public void setPredictionEnabled(boolean enabled) {
        this.predictionEnabled = enabled;
    }
    
    public void togglePrediction() {
        this.predictionEnabled = !this.predictionEnabled;
    }
    
    public float getPredictionRatio() {
        return this.predictionRatio;
    }
    
    public void setPredictionRatio(float ratio) {
        this.predictionRatio = Math.max(0.1f, Math.min(2.0f, ratio));
    }
    
    public DisplayTheme getDisplayTheme() {
        return this.displayTheme;
    }
    
    public void setDisplayTheme(DisplayTheme theme) {
        this.displayTheme = ((theme != null) ? theme : DisplayTheme.DARK);
    }
    
    public void cycleDisplayTheme() {
        this.displayTheme = this.displayTheme.next();
    }
    
    public int getCycleInterval() {
        return this.cycleInterval;
    }
    
    public void setCycleInterval(int ticks) {
        this.cycleInterval = ticks;
    }
    
    public void cycleCycleSpeed() {
        for (int i = 0; i < StatsDisplaySettings.CYCLE_SPEEDS.length; ++i) {
            if (this.cycleInterval == StatsDisplaySettings.CYCLE_SPEEDS[i]) {
                this.cycleInterval = StatsDisplaySettings.CYCLE_SPEEDS[(i + 1) % StatsDisplaySettings.CYCLE_SPEEDS.length];
                return;
            }
        }
        this.cycleInterval = 60;
    }
    
    public String getCycleSpeedLabel() {
        for (int i = 0; i < StatsDisplaySettings.CYCLE_SPEEDS.length; ++i) {
            if (this.cycleInterval == StatsDisplaySettings.CYCLE_SPEEDS[i]) {
                return StatsDisplaySettings.CYCLE_LABELS[i];
            }
        }
        return this.cycleInterval + " ticks";
    }
    
    public boolean isChannelHidden(String channelId) {
        return this.hiddenChannelIds.contains(channelId);
    }
    
    public void toggleChannelVisibility(String channelId) {
        if (!this.hiddenChannelIds.remove(channelId)) {
            this.hiddenChannelIds.add(channelId);
        }
    }
    
    public Set<String> getHiddenChannelIds() {
        return this.hiddenChannelIds;
    }
    
    public void setHiddenChannelIds(Set<String> ids) {
        this.hiddenChannelIds.clear();
        this.hiddenChannelIds.addAll(ids);
    }
    
    public DisplayMode getDisplayMode() {
        return this.displayMode;
    }
    
    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = ((mode != null) ? mode : DisplayMode.AUTO);
    }

    public boolean isAeroFlightDeckEnabled() {
        return this.aeroFlightDeckEnabled;
    }

    public void setAeroFlightDeckEnabled(boolean enabled) {
        this.aeroFlightDeckEnabled = enabled;
        if (enabled) {
            this.aerolineDirectorEnabled = false;
        }
    }

    public boolean isAerolineDirectorEnabled() {
        return this.aerolineDirectorEnabled;
    }

    public void setAerolineDirectorEnabled(boolean enabled) {
        this.aerolineDirectorEnabled = enabled;
        if (enabled) {
            this.aeroFlightDeckEnabled = false;
        }
    }

    public boolean normalizeSelections(Collection<String> knownSourceIds, Collection<String> existingChannelIds, Collection<String> visibleChannelIds, Collection<String> retainPreferredSourceIds, boolean hasTrainConnection, int multiblockWidth, int multiblockHeight) {
        boolean changed = false;
        final HashSet<String> validChannelIds = new HashSet<String>();
        if (existingChannelIds != null) {
            validChannelIds.addAll(existingChannelIds);
        }
        if (this.hiddenChannelIds.retainAll(validChannelIds)) {
            changed = true;
        }
        GraphType normalizedGraphType = normalizeGraphType(this.graphType, hasTrainConnection);
        final GraphType normalizedGraphForMode = normalizeGraphForDisplayMode(normalizedGraphType, this.displayMode, hasTrainConnection);
        if (normalizedGraphForMode != normalizedGraphType) {
            normalizedGraphType = normalizedGraphForMode;
            changed = true;
        }
        if (normalizedGraphType != this.graphType) {
            this.graphType = normalizedGraphType;
            changed = true;
        }
        final DisplayMode normalizedDisplayMode = normalizeDisplayMode(this.displayMode, this.graphType, multiblockWidth, multiblockHeight);
        if (normalizedDisplayMode != this.displayMode) {
            this.displayMode = normalizedDisplayMode;
            changed = true;
        }
        final HashSet<String> visibleIds = new HashSet<String>();
        final HashSet<String> visibleSourceIds = new HashSet<String>();
        final HashSet<String> retainedPreferredIds = new HashSet<String>();
        if (visibleChannelIds != null) {
            for (String channelId : visibleChannelIds) {
                visibleIds.add(channelId);
                visibleSourceIds.add(extractSourceId(channelId));
            }
        }
        if (retainPreferredSourceIds != null) {
            retainedPreferredIds.addAll(retainPreferredSourceIds);
        }
        if (!this.dashboardMainChannelId.isEmpty() && !visibleIds.contains(this.dashboardMainChannelId)) {
            this.dashboardMainChannelId = "";
            changed = true;
        }
        if (!this.preferredSourceId.isEmpty()) {
            final HashSet<String> validSourceIds = new HashSet<String>();
            if (knownSourceIds != null) {
                validSourceIds.addAll(knownSourceIds);
            }
            if (!validSourceIds.contains(this.preferredSourceId)
                    || (!visibleSourceIds.contains(this.preferredSourceId) && !retainedPreferredIds.contains(this.preferredSourceId))) {
                this.preferredSourceId = "";
                changed = true;
            }
        }
        return changed;
    }

    public static GraphType normalizeGraphType(GraphType graphType, boolean hasTrainConnection) {
        final GraphType normalizedGraphType = (graphType != null) ? graphType : GraphType.LINE_CHART;
        if (normalizedGraphType == GraphType.TRAIN_MAP && !hasTrainConnection) {
            return GraphType.LINE_CHART;
        }
        return normalizedGraphType;
    }

    public static GraphType normalizeGraphForDisplayMode(GraphType graphType, DisplayMode displayMode, boolean hasTrainConnection) {
        final GraphType normalizedGraphType = normalizeGraphType(graphType, hasTrainConnection);
        final DisplayMode normalizedDisplayMode = (displayMode != null) ? displayMode : DisplayMode.AUTO;
        if (normalizedGraphType == GraphType.ANALOG_GAUGE) {
            return (normalizedDisplayMode == DisplayMode.SINGLE) ? GraphType.ANALOG_GAUGE : GraphType.LINE_CHART;
        }
        if (normalizedDisplayMode == DisplayMode.SPECIALIZED) {
            return normalizedGraphType;
        }
        if (isDedicatedGraphType(normalizedGraphType)) {
            return GraphType.LINE_CHART;
        }
        return normalizedGraphType;
    }

    public static DisplayMode normalizeDisplayMode(DisplayMode displayMode, GraphType graphType, int multiblockWidth, int multiblockHeight) {
        final GraphType normalizedGraphType = (graphType != null) ? graphType : GraphType.LINE_CHART;
        if (normalizedGraphType == GraphType.ANALOG_GAUGE) {
            return DisplayMode.SINGLE;
        }
        if (isDedicatedGraphType(normalizedGraphType)) {
            return DisplayMode.SPECIALIZED;
        }
        final DisplayMode normalizedDisplayMode = (displayMode != null) ? displayMode : DisplayMode.AUTO;
        if (normalizedDisplayMode == DisplayMode.DASHBOARD && !canUseExplicitDashboard(normalizedGraphType, multiblockWidth, multiblockHeight)) {
            return DisplayMode.SINGLE;
        }
        return normalizedDisplayMode;
    }

    public static boolean isDedicatedGraphType(GraphType graphType) {
        final GraphType normalizedGraphType = (graphType != null) ? graphType : GraphType.LINE_CHART;
        return normalizedGraphType == GraphType.TRAIN_MAP
                || normalizedGraphType == GraphType.EFFICIENCY
                || normalizedGraphType == GraphType.TOPOLOGY
                || normalizedGraphType == GraphType.INVENTORY_STATUS
                || normalizedGraphType == GraphType.FACTORY_STATUS;
    }

    public static boolean canUseExplicitDashboard(GraphType graphType, int multiblockWidth, int multiblockHeight) {
        final GraphType normalizedGraphType = (graphType != null) ? graphType : GraphType.LINE_CHART;
        return normalizedGraphType != GraphType.ANALOG_GAUGE && !isDedicatedGraphType(normalizedGraphType) && multiblockWidth * multiblockHeight >= 4;
    }

    public static DisplayMode nextDisplayMode(DisplayMode currentMode, GraphType currentGraphType, boolean hasTrainConnection, int multiblockWidth, int multiblockHeight) {
        final DisplayMode[] values = DisplayMode.values();
        final DisplayMode normalizedCurrentMode = normalizeDisplayMode(currentMode, normalizeGraphType(currentGraphType, hasTrainConnection), multiblockWidth, multiblockHeight);
        final int startIndex = normalizedCurrentMode.ordinal();
        for (int i = 1; i <= values.length; ++i) {
            final DisplayMode candidate = values[(startIndex + i) % values.length];
            final GraphType candidateGraph = normalizeGraphForDisplayMode(currentGraphType, candidate, hasTrainConnection);
            if (candidate != DisplayMode.DASHBOARD || canUseExplicitDashboard(candidateGraph, multiblockWidth, multiblockHeight)) {
                return candidate;
            }
        }
        return normalizedCurrentMode;
    }

    public static GraphType nextGraphType(GraphType currentGraphType, boolean hasTrainConnection) {
        final GraphType[] values = GraphType.values();
        final GraphType normalizedCurrentGraph = normalizeGraphType(currentGraphType, hasTrainConnection);
        final int startIndex = normalizedCurrentGraph.ordinal();
        for (int i = 1; i <= values.length; ++i) {
            final GraphType candidate = values[(startIndex + i) % values.length];
            if (candidate != GraphType.TRAIN_MAP || hasTrainConnection) {
                return candidate;
            }
        }
        return normalizedCurrentGraph;
    }

    public static String extractSourceId(String channelId) {
        if (channelId == null || channelId.isEmpty() || !channelId.startsWith("src_")) {
            return "legacy";
        }
        final String rest = channelId.substring(4);
        int idx = rest.indexOf("_auto_");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        idx = rest.indexOf("_line");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        return rest;
    }
    
    public void cycleDisplayMode() {
        final DisplayMode[] values = DisplayMode.values();
        this.displayMode = values[(this.displayMode.ordinal() + 1) % values.length];
    }
    
    public String getDashboardMainChannelId() {
        return this.dashboardMainChannelId;
    }
    
    public void setDashboardMainChannelId(String id) {
        this.dashboardMainChannelId = id;
    }

    public String getPreferredSourceId() {
        return this.preferredSourceId;
    }

    public void setPreferredSourceId(String sourceId) {
        this.preferredSourceId = ((sourceId != null) ? sourceId : "");
    }

    public NoseSetting getNoseSetting() {
        return this.noseSetting;
    }

    public void setNoseSetting(NoseSetting noseSetting) {
        this.noseSetting = ((noseSetting != null) ? noseSetting : NoseSetting.UNSET);
    }

    public VehiclePreset getVehiclePreset() {
        return this.vehiclePreset;
    }

    public void setVehiclePreset(VehiclePreset vehiclePreset) {
        this.vehiclePreset = ((vehiclePreset != null) ? vehiclePreset : VehiclePreset.AUTO);
    }

    public BattleBoardPreset getBattleBoardPreset() {
        return this.battleBoardPreset;
    }

    public void setBattleBoardPreset(BattleBoardPreset battleBoardPreset) {
        this.battleBoardPreset = ((battleBoardPreset != null) ? battleBoardPreset : BattleBoardPreset.STRATEGIC_OVERVIEW);
    }
    
    public boolean isDashboardLayoutLocked() {
        return this.dashboardLayoutLocked;
    }
    
    public void setDashboardLayoutLocked(boolean locked) {
        this.dashboardLayoutLocked = locked;
    }
    
    public int getClockFormat() {
        return this.clockFormat;
    }

    public void setClockFormat(int fmt) {
        this.clockFormat = fmt;
    }

    public PanelHudSettings getPanelHudSettings() {
        return this.panelHudSettings;
    }

    public void setPanelHudSettings(PanelHudSettings panelHudSettings) {
        this.panelHudSettings = (panelHudSettings != null) ? panelHudSettings.copy() : new PanelHudSettings();
    }

    public boolean hasCustomScreenLayout() {
        return this.customScreenLayout != null || !this.customScreenProfiles.isEmpty();
    }

    public boolean isCustomScreenLayoutEnabled() {
        return this.customScreenLayoutEnabled && this.customScreenLayout != null;
    }

    public void setCustomScreenLayoutEnabled(boolean customScreenLayoutEnabled) {
        this.customScreenLayoutEnabled = customScreenLayoutEnabled && this.customScreenLayout != null;
    }

    public CustomScreenLayout getCustomScreenLayout() {
        return this.customScreenLayout;
    }

    public CustomScreenLayout getOrCreateCustomScreenLayout() {
        if (this.customScreenLayout == null) {
            this.setCustomScreenLayout(new CustomScreenLayout());
        }
        return this.customScreenLayout;
    }

    public void setCustomScreenLayout(CustomScreenLayout customScreenLayout) {
        final CustomScreenLayout normalizedLayout = customScreenLayout != null ? CustomScreenMigrationCoordinator.normalizeLayout(customScreenLayout, null) : null;
        this.customScreenLayout = normalizedLayout;
        if (this.customScreenLayout == null) {
            this.customScreenLayoutEnabled = false;
            this.activeCustomScreenProfileId = "";
            this.customScreenProfiles.clear();
            return;
        }
        final CustomScreenProfile activeProfile = this.findCustomScreenProfile(this.activeCustomScreenProfileId);
        final String profileName = activeProfile != null ? activeProfile.getName() : profileNameFromLayout(normalizedLayout);
        this.saveActiveCustomScreenProfile(profileName, normalizedLayout);
    }

    public List<CustomScreenProfile> getCustomScreenProfiles() {
        this.normalizeCustomScreenProfiles();
        final ArrayList<CustomScreenProfile> profiles = new ArrayList<CustomScreenProfile>();
        for (CustomScreenProfile profile : this.customScreenProfiles) {
            if (profile != null) {
                profiles.add(profile.copy());
            }
        }
        return Collections.unmodifiableList(profiles);
    }

    public int getCustomScreenProfileCount() {
        this.normalizeCustomScreenProfiles();
        return this.customScreenProfiles.size();
    }

    public String getActiveCustomScreenProfileId() {
        this.normalizeCustomScreenProfiles();
        return this.activeCustomScreenProfileId;
    }

    public String activeCustomScreenProfileName() {
        this.normalizeCustomScreenProfiles();
        final CustomScreenProfile profile = this.findCustomScreenProfile(this.activeCustomScreenProfileId);
        return profile != null ? profile.getName() : "Custom Layout";
    }

    public boolean setActiveCustomScreenProfileId(String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return false;
        }
        this.normalizeCustomScreenProfiles();
        final CustomScreenProfile profile = this.findCustomScreenProfile(profileId);
        if (profile == null) {
            return false;
        }
        this.activeCustomScreenProfileId = profile.getId();
        this.customScreenLayout = profile.getLayout().copy();
        return true;
    }

    public boolean cycleCustomScreenProfile() {
        this.normalizeCustomScreenProfiles();
        if (this.customScreenProfiles.size() <= 1) {
            return false;
        }
        int currentIndex = 0;
        for (int index = 0; index < this.customScreenProfiles.size(); ++index) {
            final CustomScreenProfile profile = this.customScreenProfiles.get(index);
            if (profile != null && profile.getId().equals(this.activeCustomScreenProfileId)) {
                currentIndex = index;
                break;
            }
        }
        final CustomScreenProfile nextProfile = this.customScreenProfiles.get((currentIndex + 1) % this.customScreenProfiles.size());
        this.activeCustomScreenProfileId = nextProfile.getId();
        this.customScreenLayout = nextProfile.getLayout().copy();
        return true;
    }

    public CustomScreenProfile createCustomScreenProfile(String name, CustomScreenLayout layout) {
        final CustomScreenLayout profileLayout = layout != null ? CustomScreenMigrationCoordinator.normalizeLayout(layout, null) : CustomScreenModuleCatalog.createStarterLayout();
        final String profileName = normalizeProfileName(name, "Custom Layout " + (this.customScreenProfiles.size() + 1));
        final CustomScreenProfile profile = CustomScreenProfile.create(profileName, withProfileName(profileLayout, profileName));
        this.customScreenProfiles.add(profile);
        this.activeCustomScreenProfileId = profile.getId();
        this.customScreenLayout = profile.getLayout().copy();
        return profile.copy();
    }

    public CustomScreenProfile duplicateActiveCustomScreenProfile(String name) {
        this.normalizeCustomScreenProfiles();
        final CustomScreenProfile activeProfile = this.findCustomScreenProfile(this.activeCustomScreenProfileId);
        final String fallbackName = activeProfile != null ? activeProfile.getName() + " Copy" : "Custom Layout Copy";
        return this.createCustomScreenProfile(normalizeProfileName(name, fallbackName), activeProfile != null ? activeProfile.getLayout() : this.customScreenLayout);
    }

    public boolean deleteCustomScreenProfile(String profileId) {
        this.normalizeCustomScreenProfiles();
        if (profileId == null || profileId.isEmpty() || this.customScreenProfiles.size() <= 1) {
            return false;
        }
        final boolean removed = this.customScreenProfiles.removeIf(profile -> profile != null && profileId.equals(profile.getId()));
        if (!removed) {
            return false;
        }
        if (profileId.equals(this.activeCustomScreenProfileId)) {
            final CustomScreenProfile firstProfile = this.customScreenProfiles.get(0);
            this.activeCustomScreenProfileId = firstProfile.getId();
            this.customScreenLayout = firstProfile.getLayout().copy();
        }
        return true;
    }

    public void saveActiveCustomScreenProfile(String name, CustomScreenLayout layout) {
        final CustomScreenLayout normalizedLayout = layout != null ? CustomScreenMigrationCoordinator.normalizeLayout(layout, null) : null;
        if (normalizedLayout == null) {
            this.customScreenLayout = null;
            this.customScreenLayoutEnabled = false;
            return;
        }
        final String profileName = normalizeProfileName(name, profileNameFromLayout(normalizedLayout));
        final CustomScreenLayout namedLayout = withProfileName(normalizedLayout, profileName);
        CustomScreenProfile profile = this.findCustomScreenProfile(this.activeCustomScreenProfileId);
        if (profile == null) {
            profile = CustomScreenProfile.create(profileName, namedLayout);
            this.customScreenProfiles.add(profile);
            this.activeCustomScreenProfileId = profile.getId();
        }
        else {
            profile.setName(profileName);
            profile.setLayout(namedLayout);
            profile.normalize();
        }
        this.customScreenLayout = namedLayout.copy();
    }

    private void normalizeCustomScreenProfiles() {
        final ArrayList<CustomScreenProfile> normalizedProfiles = new ArrayList<CustomScreenProfile>();
        final LinkedHashSet<String> seenIds = new LinkedHashSet<String>();
        for (CustomScreenProfile profile : this.customScreenProfiles) {
            if (profile == null) {
                continue;
            }
            profile.normalize();
            if (seenIds.add(profile.getId())) {
                normalizedProfiles.add(profile);
            }
        }
        this.customScreenProfiles.clear();
        this.customScreenProfiles.addAll(normalizedProfiles);
        if (this.customScreenLayout != null && this.customScreenProfiles.isEmpty()) {
            final String profileName = normalizeProfileName(profileNameFromLayout(this.customScreenLayout), "Default Custom");
            final CustomScreenProfile profile = new CustomScreenProfile(CustomScreenProfile.DEFAULT_PROFILE_ID, profileName, withProfileName(this.customScreenLayout, profileName));
            this.customScreenProfiles.add(profile);
            this.activeCustomScreenProfileId = profile.getId();
        }
        if (!this.customScreenProfiles.isEmpty()) {
            CustomScreenProfile activeProfile = this.findCustomScreenProfile(this.activeCustomScreenProfileId);
            if (activeProfile == null) {
                activeProfile = this.customScreenProfiles.get(0);
                this.activeCustomScreenProfileId = activeProfile.getId();
            }
            this.customScreenLayout = activeProfile.getLayout().copy();
        }
        if (this.customScreenProfiles.isEmpty() && this.customScreenLayout == null) {
            this.activeCustomScreenProfileId = "";
            this.customScreenLayoutEnabled = false;
        }
    }

    private CustomScreenProfile findCustomScreenProfile(String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return null;
        }
        for (CustomScreenProfile profile : this.customScreenProfiles) {
            if (profile != null && profileId.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }

    private static String normalizeProfileName(String name, String fallback) {
        final String trimmed = name != null ? name.trim() : "";
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        final String fallbackTrimmed = fallback != null ? fallback.trim() : "";
        return fallbackTrimmed.isEmpty() ? "Custom Layout" : fallbackTrimmed;
    }

    private static String profileNameFromLayout(CustomScreenLayout layout) {
        if (layout != null && layout.getLayoutMeta() != null && !layout.getLayoutMeta().getName().isEmpty()) {
            return layout.getLayoutMeta().getName();
        }
        return "Custom Layout";
    }

    private static CustomScreenLayout withProfileName(CustomScreenLayout layout, String profileName) {
        final CustomScreenLayout namedLayout = layout != null ? layout.copy() : new CustomScreenLayout();
        final CustomScreenLayout.LayoutMeta meta = namedLayout.getLayoutMeta() != null ? namedLayout.getLayoutMeta().copy() : new CustomScreenLayout.LayoutMeta();
        meta.setName(normalizeProfileName(profileName, profileNameFromLayout(namedLayout)));
        namedLayout.setLayoutMeta(meta);
        return namedLayout;
    }
    
    public int getEffectiveLineColor() {
        return (this.customAccentColor != 0) ? this.customAccentColor : this.colorScheme.getLineColor();
    }
    
    public float getAnalogGaugeMin() {
        return this.analogGaugeMin;
    }
    
    public void setAnalogGaugeMin(float v) {
        this.analogGaugeMin = v;
    }
    
    public float getAnalogGaugeMax() {
        return this.analogGaugeMax;
    }
    
    public void setAnalogGaugeMax(float v) {
        this.analogGaugeMax = v;
    }
    
    public float getAnalogZoneYellow() {
        return this.analogZoneYellow;
    }
    
    public void setAnalogZoneYellow(float v) {
        this.analogZoneYellow = v;
    }
    
    public float getAnalogZoneRed() {
        return this.analogZoneRed;
    }
    
    public void setAnalogZoneRed(float v) {
        this.analogZoneRed = v;
    }
    
    public boolean isAnalogShowZones() {
        return this.analogShowZones;
    }
    
    public void setAnalogShowZones(boolean v) {
        this.analogShowZones = v;
    }
    
    public boolean isAnalogShowGlass() {
        return this.analogShowGlass;
    }
    
    public void setAnalogShowGlass(boolean v) {
        this.analogShowGlass = v;
    }
    
    public boolean isAnalogShowRivets() {
        return this.analogShowRivets;
    }
    
    public void setAnalogShowRivets(boolean v) {
        this.analogShowRivets = v;
    }

    public InstrumentStyleFamily getInstrumentStyleFamily() {
        return this.instrumentStyleFamily;
    }

    public void setInstrumentStyleFamily(InstrumentStyleFamily family) {
        this.instrumentStyleFamily = (family != null) ? family : InstrumentStyleFamily.LEGACY_ANALOG;
    }

    public InstrumentDensityPreset getInstrumentDensityPreset() {
        return this.instrumentDensityPreset;
    }

    public void setInstrumentDensityPreset(InstrumentDensityPreset preset) {
        this.instrumentDensityPreset = (preset != null) ? preset : InstrumentDensityPreset.MEDIUM;
    }

    public InstrumentEffectPreset getInstrumentEffectPreset() {
        return this.instrumentEffectPreset;
    }

    public void setInstrumentEffectPreset(InstrumentEffectPreset preset) {
        this.instrumentEffectPreset = (preset != null) ? preset : InstrumentEffectPreset.BALANCED;
    }

    public InstrumentStyleOptions getInstrumentStyleOptions() {
        return this.instrumentStyleOptions;
    }

    public void setInstrumentStyleOptions(InstrumentStyleOptions options) {
        this.instrumentStyleOptions = (options != null) ? options : new InstrumentStyleOptions();
    }
     
    public boolean topologyShowLabels() {
        return this.topologyShowLabels;
    }
    
    public void setTopologyShowLabels(boolean show) {
        this.topologyShowLabels = show;
    }
    
    public boolean topologyAnimateEdges() {
        return this.topologyAnimateEdges;
    }
    
    public void setTopologyAnimateEdges(boolean animate) {
        this.topologyAnimateEdges = animate;
    }
    
    public boolean topologyColorByStress() {
        return this.topologyColorByStress;
    }
    
    public void setTopologyColorByStress(boolean color) {
        this.topologyColorByStress = color;
    }
    
    public int getTopologyMaxNodes() {
        return this.topologyMaxNodes;
    }
    
    public void setTopologyMaxNodes(int max) {
        this.topologyMaxNodes = Math.max(10, Math.min(1000, max));
    }

    public TrainMapRenderQuality getTrainMapRenderQuality() {
        return this.trainMapRenderQuality;
    }

    public void setTrainMapRenderQuality(TrainMapRenderQuality quality) {
        this.trainMapRenderQuality = (quality != null) ? quality : TrainMapRenderQuality.LOW;
    }

    public void cycleTrainMapRenderQuality() {
        final TrainMapRenderQuality[] values = TrainMapRenderQuality.values();
        this.trainMapRenderQuality = values[(this.trainMapRenderQuality.ordinal() + 1) % values.length];
    }

    public void cycleInstrumentStyleFamily() {
        final InstrumentStyleFamily[] values = InstrumentStyleFamily.values();
        this.instrumentStyleFamily = values[(this.instrumentStyleFamily.ordinal() + 1) % values.length];
    }

    public void cycleInstrumentDensityPreset() {
        final InstrumentDensityPreset[] values = InstrumentDensityPreset.values();
        this.instrumentDensityPreset = values[(this.instrumentDensityPreset.ordinal() + 1) % values.length];
    }

    public void cycleInstrumentEffectPreset() {
        final InstrumentEffectPreset[] values = InstrumentEffectPreset.values();
        this.instrumentEffectPreset = values[(this.instrumentEffectPreset.ordinal() + 1) % values.length];
    }
    
    public void cycleGraphType() {
        final GraphType[] values = GraphType.values();
        this.graphType = values[(this.graphType.ordinal() + 1) % values.length];
    }
    
    public void cycleColorScheme() {
        final StatsColorScheme[] values = StatsColorScheme.values();
        this.colorScheme = values[(this.colorScheme.ordinal() + 1) % values.length];
    }
    
    public void cycleTimeRange() {
        final TimeRange[] values = TimeRange.values();
        this.timeRange = values[(this.timeRange.ordinal() + 1) % values.length];
    }

    public CompoundTag saveCustomScreenState() {
        final CompoundTag tag = new CompoundTag();
        this.normalizeCustomScreenProfiles();
        if (this.customScreenLayout != null) {
            tag.put("CustomScreenLayout", this.customScreenLayout.save());
            tag.putBoolean("CustomScreenLayoutEnabled", this.customScreenLayoutEnabled);
        }
        if (!this.customScreenProfiles.isEmpty()) {
            final ListTag profileList = new ListTag();
            for (CustomScreenProfile profile : this.customScreenProfiles) {
                if (profile != null) {
                    profileList.add(profile.save());
                }
            }
            tag.put("CustomScreenProfiles", profileList);
            tag.putString("ActiveCustomScreenProfile", this.activeCustomScreenProfileId);
        }
        return tag;
    }

    public void loadCustomScreenState(CompoundTag tag) {
        this.customScreenLayout = null;
        this.customScreenLayoutEnabled = false;
        this.customScreenProfiles.clear();
        this.activeCustomScreenProfileId = "";
        if (tag == null || tag.isEmpty()) {
            return;
        }
        if (tag.contains("CustomScreenLayout", 10)) {
            this.customScreenLayout = CustomScreenMigrationCoordinator.loadLayout(tag.getCompound("CustomScreenLayout"), null);
        }
        else if (!tag.contains("CustomScreenProfiles", 9) && !tag.contains("ActiveCustomScreenProfile") && !tag.contains("CustomScreenLayoutEnabled")) {
            this.customScreenLayout = CustomScreenMigrationCoordinator.loadLayout(tag, null);
        }
        if (tag.contains("CustomScreenProfiles", 9)) {
            final ListTag profileList = tag.getList("CustomScreenProfiles", 10);
            for (int index = 0; index < profileList.size(); ++index) {
                this.customScreenProfiles.add(CustomScreenProfile.load(profileList.getCompound(index)));
            }
        }
        if (tag.contains("ActiveCustomScreenProfile")) {
            this.activeCustomScreenProfileId = tag.getString("ActiveCustomScreenProfile");
        }
        this.normalizeCustomScreenProfiles();
        if (tag.contains("CustomScreenLayoutEnabled")) {
            this.customScreenLayoutEnabled = tag.getBoolean("CustomScreenLayoutEnabled") && this.customScreenLayout != null;
        }
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("GraphType", this.graphType.name());
        tag.putString("ColorScheme", this.colorScheme.name());
        tag.putString("TimeRange", this.timeRange.name());
        tag.putInt("AccentColor", this.customAccentColor);
        tag.putBoolean("ShowGrid", this.showGrid);
        tag.putBoolean("ShowAxisLabels", this.showAxisLabels);
        tag.putBoolean("ShowMinMaxAvg", this.showMinMaxAvg);
        tag.putBoolean("ShowValueOverlay", this.showValueOverlay);
        tag.putBoolean("DoubleSided", this.doubleSided);
        tag.putBoolean("AutoScale", this.autoScale);
        tag.putBoolean("PredictionEnabled", this.predictionEnabled);
        tag.putFloat("PredictionRatio", this.predictionRatio);
        tag.putString("DisplayTheme", this.displayTheme.name());
        tag.putInt("CycleInterval", this.cycleInterval);
        if (!this.hiddenChannelIds.isEmpty()) {
            final ListTag hiddenList = new ListTag();
            for (String id : this.hiddenChannelIds) {
                hiddenList.add(StringTag.valueOf(id));
            }
            tag.put("HiddenChannels", (Tag)hiddenList);
        }
        tag.putFloat("AnalogMin", this.analogGaugeMin);
        tag.putFloat("AnalogMax", this.analogGaugeMax);
        tag.putFloat("AnalogZoneYellow", this.analogZoneYellow);
        tag.putFloat("AnalogZoneRed", this.analogZoneRed);
        tag.putBoolean("AnalogShowZones", this.analogShowZones);
        tag.putBoolean("AnalogShowGlass", this.analogShowGlass);
        tag.putBoolean("AnalogShowRivets", this.analogShowRivets);
        tag.putString("InstrumentStyleFamily", this.instrumentStyleFamily.name());
        tag.putString("InstrumentDensityPreset", this.instrumentDensityPreset.name());
        tag.putString("InstrumentEffectPreset", this.instrumentEffectPreset.name());
        if (!this.instrumentStyleOptions.isEmpty()) {
            tag.put("InstrumentStyleOptions", (Tag)this.instrumentStyleOptions.save());
        }
        tag.putBoolean("TopoShowLabels", this.topologyShowLabels);
        tag.putBoolean("TopoAnimateEdges", this.topologyAnimateEdges);
        tag.putBoolean("TopoColorByStress", this.topologyColorByStress);
        tag.putInt("TopoMaxNodes", this.topologyMaxNodes);
        tag.putString("TrainMapRenderQuality", this.trainMapRenderQuality.name());
        tag.putString("DisplayMode", this.displayMode.name());
        tag.putBoolean("AeroFlightDeck", this.aeroFlightDeckEnabled);
        tag.putBoolean("AerolineDirector", this.aerolineDirectorEnabled);
        tag.putString("DashMainChannel", this.dashboardMainChannelId);
        tag.putString("PreferredSourceId", this.preferredSourceId);
        tag.putString("VehicleNose", this.noseSetting.name());
        tag.putString("VehiclePreset", this.vehiclePreset.name());
        tag.putString("BattleBoardPreset", this.battleBoardPreset.name());
        tag.putBoolean("DashLocked", this.dashboardLayoutLocked);
        tag.putInt("ClockFormat", this.clockFormat);
        tag.put("HudSettings", this.panelHudSettings.save());
        final CompoundTag customScreenState = this.saveCustomScreenState();
        for (String key : customScreenState.getAllKeys()) {
            final Tag value = customScreenState.get(key);
            if (value != null) {
                tag.put(key, value.copy());
            }
        }
        return tag;
    }
    
    public static StatsDisplaySettings load(CompoundTag tag) {
        final StatsDisplaySettings settings = new StatsDisplaySettings();
        if (tag.contains("GraphType")) {
            settings.graphType = parseEnum(tag.getString("GraphType"), GraphType.class, settings.graphType);
        }
        if (tag.contains("ColorScheme")) {
            settings.colorScheme = parseEnum(tag.getString("ColorScheme"), StatsColorScheme.class, settings.colorScheme);
        }
        if (tag.contains("TimeRange")) {
            settings.timeRange = parseEnum(tag.getString("TimeRange"), TimeRange.class, settings.timeRange);
        }
        if (tag.contains("AccentColor")) {
            settings.customAccentColor = tag.getInt("AccentColor");
        }
        if (tag.contains("ShowGrid")) {
            settings.showGrid = tag.getBoolean("ShowGrid");
        }
        if (tag.contains("ShowAxisLabels")) {
            settings.showAxisLabels = tag.getBoolean("ShowAxisLabels");
        }
        if (tag.contains("ShowMinMaxAvg")) {
            settings.showMinMaxAvg = tag.getBoolean("ShowMinMaxAvg");
        }
        if (tag.contains("ShowValueOverlay")) {
            settings.showValueOverlay = tag.getBoolean("ShowValueOverlay");
        }
        if (tag.contains("DoubleSided")) {
            settings.doubleSided = tag.getBoolean("DoubleSided");
        }
        if (tag.contains("AutoScale")) {
            settings.autoScale = tag.getBoolean("AutoScale");
        }
        if (tag.contains("PredictionEnabled")) {
            settings.predictionEnabled = tag.getBoolean("PredictionEnabled");
        }
        if (tag.contains("PredictionRatio")) {
            settings.setPredictionRatio(tag.getFloat("PredictionRatio"));
        }
        if (tag.contains("DisplayTheme")) {
            settings.displayTheme = parseEnum(tag.getString("DisplayTheme"), DisplayTheme.class, settings.displayTheme);
        }
        if (tag.contains("CycleInterval")) {
            settings.cycleInterval = tag.getInt("CycleInterval");
        }
        if (tag.contains("HiddenChannels", 9)) {
            final ListTag hiddenList = tag.getList("HiddenChannels", 8);
            for (int i = 0; i < hiddenList.size(); ++i) {
                settings.hiddenChannelIds.add(hiddenList.getString(i));
            }
        }
        if (tag.contains("AnalogMin")) {
            settings.analogGaugeMin = tag.getFloat("AnalogMin");
        }
        if (tag.contains("AnalogMax")) {
            settings.analogGaugeMax = tag.getFloat("AnalogMax");
        }
        if (tag.contains("AnalogZoneYellow")) {
            settings.analogZoneYellow = tag.getFloat("AnalogZoneYellow");
        }
        if (tag.contains("AnalogZoneRed")) {
            settings.analogZoneRed = tag.getFloat("AnalogZoneRed");
        }
        if (tag.contains("AnalogShowZones")) {
            settings.analogShowZones = tag.getBoolean("AnalogShowZones");
        }
        if (tag.contains("AnalogShowGlass")) {
            settings.analogShowGlass = tag.getBoolean("AnalogShowGlass");
        }
        if (tag.contains("AnalogShowRivets")) {
            settings.analogShowRivets = tag.getBoolean("AnalogShowRivets");
        }
        if (tag.contains("InstrumentStyleFamily")) {
            settings.instrumentStyleFamily = parseEnum(tag.getString("InstrumentStyleFamily"), InstrumentStyleFamily.class, settings.instrumentStyleFamily);
        }
        else {
            settings.instrumentStyleFamily = InstrumentStyleFamily.LEGACY_ANALOG;
        }
        if (tag.contains("InstrumentDensityPreset")) {
            settings.instrumentDensityPreset = parseEnum(tag.getString("InstrumentDensityPreset"), InstrumentDensityPreset.class, settings.instrumentDensityPreset);
        }
        if (tag.contains("InstrumentEffectPreset")) {
            settings.instrumentEffectPreset = parseEnum(tag.getString("InstrumentEffectPreset"), InstrumentEffectPreset.class, settings.instrumentEffectPreset);
        }
        if (tag.contains("InstrumentStyleOptions", 10)) {
            settings.instrumentStyleOptions = InstrumentStyleOptions.load(tag.getCompound("InstrumentStyleOptions"));
        }
        if (tag.contains("TopoShowLabels")) {
            settings.topologyShowLabels = tag.getBoolean("TopoShowLabels");
        }
        if (tag.contains("TopoAnimateEdges")) {
            settings.topologyAnimateEdges = tag.getBoolean("TopoAnimateEdges");
        }
        if (tag.contains("TopoColorByStress")) {
            settings.topologyColorByStress = tag.getBoolean("TopoColorByStress");
        }
        if (tag.contains("TopoMaxNodes")) {
            settings.setTopologyMaxNodes(tag.getInt("TopoMaxNodes"));
        }
        if (tag.contains("TrainMapRenderQuality")) {
            settings.trainMapRenderQuality = parseEnum(tag.getString("TrainMapRenderQuality"), TrainMapRenderQuality.class, settings.trainMapRenderQuality);
        }
        if (tag.contains("DisplayMode")) {
            settings.displayMode = parseEnum(tag.getString("DisplayMode"), DisplayMode.class, settings.displayMode);
        }
        if (tag.contains("AeroFlightDeck")) {
            settings.aeroFlightDeckEnabled = tag.getBoolean("AeroFlightDeck");
        }
        if (tag.contains("AerolineDirector")) {
            settings.aerolineDirectorEnabled = tag.getBoolean("AerolineDirector");
        }
        if (settings.aerolineDirectorEnabled) {
            settings.aeroFlightDeckEnabled = false;
        }
        if (tag.contains("DashMainChannel")) {
            settings.dashboardMainChannelId = tag.getString("DashMainChannel");
        }
        if (tag.contains("PreferredSourceId")) {
            settings.preferredSourceId = tag.getString("PreferredSourceId");
        }
        if (tag.contains("VehicleNose")) {
            settings.noseSetting = parseEnum(tag.getString("VehicleNose"), NoseSetting.class, settings.noseSetting);
        }
        if (tag.contains("VehiclePreset")) {
            settings.vehiclePreset = parseEnum(tag.getString("VehiclePreset"), VehiclePreset.class, settings.vehiclePreset);
        }
        if (tag.contains("BattleBoardPreset")) {
            settings.battleBoardPreset = parseEnum(tag.getString("BattleBoardPreset"), BattleBoardPreset.class, settings.battleBoardPreset);
        }
        if (tag.contains("DashLocked")) {
            settings.dashboardLayoutLocked = tag.getBoolean("DashLocked");
        }
        if (tag.contains("ClockFormat")) {
            settings.clockFormat = tag.getInt("ClockFormat");
        }
        if (tag.contains("HudSettings", 10)) {
            settings.panelHudSettings = PanelHudSettings.load(tag.getCompound("HudSettings"));
        }
        settings.loadCustomScreenState(tag);
        return settings;
    }
    
    private static int defaultTopologyMaxNodes() {
        try {
            return Math.max(10, Math.min(1000, (int)ModConfiguration.COMMON.topologyMaxNodes.get()));
        }
        catch (Exception ex) {
            return 300;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass, E fallback) {
        try {
            return Enum.valueOf(enumClass, value);
        }
        catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
    
    static {
        CYCLE_SPEEDS = new int[] { 0, 30, 60, 120 };
        CYCLE_LABELS = new String[] { "Off", "Fast (1.5s)", "Normal (3s)", "Slow (6s)" };
    }
    
    public enum GraphType
    {
        LINE_CHART("Line Chart"), 
        BAR_CHART("Bar Chart"), 
        GAUGE("Gauge"), 
        NUMERIC("Numeric"), 
        CAPACITY("Capacity"), 
        PRODUCTION("Production"), 
        ANALOG_GAUGE("Instrument Panel"), 
        EFFICIENCY("Efficiency"), 
        TOPOLOGY("Topology Map"), 
        TRAIN_MAP("Train Map"),
        INVENTORY_STATUS("Inventory Status"),
        FACTORY_STATUS("Factory Status");
        
        private final String displayName;
        
        private GraphType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    public enum DisplayMode
    {
        SINGLE("Single Channel"), 
        DASHBOARD("Dashboard"), 
        AUTO("Auto"), 
        SPECIALIZED("Specialized");
        
        private final String displayName;
        
        private DisplayMode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    public enum TimeRange
    {
        ONE_MIN("1 min", 60), 
        FIVE_MIN("5 min", 300), 
        TEN_MIN("10 min", 600), 
        FULL("Full", Integer.MAX_VALUE);
        
        private final String displayName;
        private final int seconds;
        
        private TimeRange(String displayName, int seconds) {
            this.displayName = displayName;
            this.seconds = seconds;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
        
        public int getSeconds() {
            return this.seconds;
        }
    }

    public enum TrainMapRenderQuality
    {
        LOW("Low"),
        HIGH("High");

        private final String displayName;

        TrainMapRenderQuality(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum NoseSetting
    {
        UNSET("Auto"), 
        AHEAD("Ahead"), 
        RIGHT("Right"), 
        BEHIND("Behind"), 
        LEFT("Left");
        
        private final String displayName;
        
        private NoseSetting(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum VehiclePreset
    {
        AUTO("Auto"), 
        PLANE("Plane"), 
        HELICOPTER("Helicopter"), 
        AIRSHIP("Airship"), 
        HYBRID("Hybrid"), 
        CUSTOM("Custom");
        
        private final String displayName;
        
        private VehiclePreset(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum BattleBoardPreset
    {
        STRATEGIC_OVERVIEW("Strategic Overview"),
        WEAPONS_BOARD("Weapons Board"),
        DAMAGE_TRENDS("Integrity Trends"),
        CARGO_AND_SYSTEMS("Cargo and Systems");

        private final String displayName;

        private BattleBoardPreset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
