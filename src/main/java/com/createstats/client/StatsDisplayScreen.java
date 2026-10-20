package com.createstats.client;

import com.createstats.CreateStats;
import java.lang.invoke.CallSite;
import java.lang.reflect.UndeclaredThrowableException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles;
import net.minecraft.network.chat.Style;
import java.util.Optional;
import com.createstats.data.AnnotationManager;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.createstats.network.PanelActivityPacket;
import com.createstats.network.SourceLabelPacket;
import com.createstats.network.ConfigSyncPacket;
import com.createstats.network.SafetyClutchConfigPacket;
import com.createstats.network.KineticFlywheelConfigPacket;
import com.createstats.network.AutoTransmissionClutchConfigPacket;
import com.createstats.network.LoadBalancerConfigPacket;
import com.createstats.network.MiniDisplayConfigPacket;
import com.createstats.network.FreightManifestDeliveryOrderActionPacket;
import com.createstats.topology.TopologyEdge;
import com.createstats.topology.TopologyNode;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import com.createstats.topology.NetworkGraph;
import com.createstats.topology.TopologyFlowLayout;
import com.createstats.efficiency.GradeHistory;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyDimension;
import com.createstats.data.MetricType;
import com.createstats.train.manifest.DeliveryOrderAction;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLine;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.TrainCargoSnapshot;
import com.createstats.renderer.DisplayTextureManager;
import com.createstats.renderer.SpecializedPanelRenderer;
import com.createstats.display.VehicleForwardAuthority;
import com.createstats.network.AnnotationActionPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Iterator;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import com.mojang.math.Axis;
import com.createstats.prediction.PredictionResult;
import java.util.LinkedHashMap;
import com.createstats.prediction.PredictionFormatter;
import com.createstats.renderer.GraphRenderer;
import com.createstats.renderer.SignalTraceRenderer;
import com.createstats.prediction.PredictionEngine;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.SafetyClutchSettings;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.KineticFlywheelSettings;
import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.AutoTransmissionClutchSettings;
import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.LoadBalancerSettings;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.MiniDisplaySettings;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.FlowMeterBE;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.network.FlowMeterTriggerPacket;
import com.createstats.network.StockpileThresholdPacket;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.createstats.blockentity.ScanSettings;
import com.createstats.util.InstrumentSourceHelper;
import java.util.Collection;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.data.TimeSeriesStore;
import com.createstats.prediction.LinearRegression;
import com.createstats.data.Annotation;
import com.createstats.train.TrainMapData;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import com.createstats.display.DisplayTheme;
import com.createstats.display.InstrumentDensityPreset;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentStyleFamily;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Random;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Set;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class StatsDisplayScreen extends Screen
{
    private final StatsDisplayPanelBE panel;
    private StatsDisplaySettings.GraphType viewType;
    private int selectedTab;
    private int tabScrollOffset;
    private final Set<Integer> pinnedChannels;
    private int optionPanelChannelScroll;
    private int channelListTop;
    private int channelListRowH;
    private int channelListVisibleRows;
    private final Map<String, ItemStack> itemIconCache;
    private int selectedSource;
    private List<String> sourceIds;
    private int sourceTabScrollOffset;
    private boolean sourceDropdownOpen;
    private boolean signalDropdownOpen;
    private int sourceDropdownScrollOffset;
    private int signalDropdownScrollOffset;
    private int timeScale;
    private static final int[] TIME_SCALES;
    private static final String[] TIME_SCALE_LABELS;
    private boolean showStats;
    private boolean showLog;
    private boolean redstoneConfigOpen;

    private boolean battleViewToggleVisible;
    private int battleViewToggleX;
    private int battleViewToggleY;
    private int battleViewToggleW;
    private int battleViewToggleH;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int graphLeft;
    private int graphTop;
    private int graphWidth;
    private int graphHeight;
    private final java.util.Map<String, Float> needleEase = new java.util.HashMap<>();
    private long needleEaseLastMs;
    private float singleGaugeEase = -1.0f;
    private long singleGaugeEaseMs;
    private long singleGaugeOpenMs;
    private long dashOpenMs;

    private final java.util.Map<String, float[]> lineRangeEase = new java.util.HashMap<>();
    private long lineTraceLastMs;
    private long lineTraceOpenMs;
    private String lineTraceKey = "";
    private DynamicTexture lineTraceTex;
    private ResourceLocation lineTraceTexId;
    private NativeImage lineTraceImg;
    private int lineTraceTexW = -1;
    private int lineTraceTexH = -1;
    private long lineTraceSig = Long.MIN_VALUE;
    private int optionPanelLeft;
    private int optionPanelTop;
    private int optionPanelWidth;
    private int optionPanelHeight;
    private int tabBarHeight;
    private int hoverDataIndex;
    private float hoverValue;
    private String hoverLabel;
    private String redstoneValueInput;
    private EditBox redstoneThresholdInput;
    private EditBox fmTriggerThresholdInput;
    private String fmTriggerThresholdDraft = "";
    private String fmTriggerLoadedSourceId;
    private boolean fmTriggerRedstone;
    private boolean fmTriggerAlarm;
    private int fmTriggerMode;
    private boolean fmTriggerAbove = true;
    private EditBox stockpileBelowInput;
    private EditBox stockpileAboveInput;
    private String stockpileBelowDraft = "";
    private String stockpileAboveDraft = "";
    private String stockpileLoadedSourceId;
    private EditBox manualYAxisMinInput;
    private EditBox manualYAxisMaxInput;
    private boolean sourceRenameOpen;
    private EditBox sourceRenameInput;
    private String renamingSourceId;
    private boolean manualYAxisEnabled;
    private String manualYAxisMinDraft;
    private String manualYAxisMaxDraft;
    private float manualYAxisMin;
    private float manualYAxisMax;
    private int productionScrollOffset;
    private int productionSortMode;
    private int capacityScrollOffset;
    private String capacityExpandedSource;
    private int inventoryScrollOffset;
    private int inventorySortMode;
    private int factoryStatusScrollOffset;
    private int factoryStatusSortMode;
    private List<ProductionRowHit> prodRowHits = new ArrayList<>();
    private List<InventoryStorageRowHit> inventoryStorageHits = new ArrayList<>();
    private final java.util.Set<String> inventoryExpandedSources = new java.util.HashSet<>();
    private int prodSortBtnX, prodSortBtnY, prodSortBtnW, prodSortBtnH;
    private boolean annotateMode;
    private boolean annotationInputOpen;
    private EditBox annotationLabelInput;
    private long pendingAnnotationTick;
    private int annotationInputX;
    private int annotationInputY;
    private int selectedAnnotationColor;
    private UUID editingAnnotationId;
    private boolean lastKnownTrainConnection;
    private long lastAnnotationClickTime;
    private UUID lastAnnotationClickId;
    private static final int[] ANNOTATION_COLOR_PRESETS;
    private int BG;
    private int BORDER;
    private int GRAPH_BG;
    private int GRID;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int TAB_BG;
    private int TAB_SEL;
    private int TAB_HOVER;
    private int WARN;
    private int THRESH_LINE;
    private static final String[] SPLASH_QUOTES;
    private String splashQuote;
    private long splashOpenTime;
    private static final int[] CHANNEL_COLORS;
    private int effFormulasBtnX;
    private int effFormulasBtnY;
    private int effFormulasBtnW;
    private int effFormulasBtnH;
    private int effLiveMathBtnX;
    private int effLiveMathBtnY;
    private int effLiveMathBtnW;
    private int effLiveMathBtnH;
    private int topologyExpandBtnX;
    private int topologyExpandBtnY;
    private int topologyExpandBtnW;
    private int topologyExpandBtnH;
    private int trainExpandBtnX;
    private int trainExpandBtnY;
    private int trainExpandBtnW;
    private int trainExpandBtnH;
    private int trainQualityBtnX;
    private int trainQualityBtnY;
    private int trainQualityBtnW;
    private int trainQualityBtnH;
    private int logScrollOffset;
    private int homeTrainPreviewScroll;
    private int homeStationPreviewScroll;
    private final List<String> trainDashboardDimensions;
    private String trainDashboardActiveDimension;
    private int trainDashboardDimPrevX;
    private int trainDashboardDimPrevY;
    private int trainDashboardDimPrevW;
    private int trainDashboardDimPrevH;
    private int trainDashboardDimNextX;
    private int trainDashboardDimNextY;
    private int trainDashboardDimNextW;
    private int trainDashboardDimNextH;
    private int trainDashboardTrainScroll;
    private int trainDashboardStationScroll;
    private UUID selectedTrainDashboardTrainId;
    private UUID selectedTrainDashboardStationId;
    private final Map<String, float[]> trainDashboardPrevWorld;
    private final Map<String, float[]> trainDashboardCurrWorld;
    private long trainDashboardLastUpdateMs;
    private long trainDashboardSnapshotTick;
    private int trainActivityPingCooldown;
    private RightPanelTab selectedRightPanelTab;
    private String homeSourceTypeFilter;
    private String homeFocusedSourceId;
    private int homeLeftScroll;
    private int homeRightScroll;
    private int homeLeftContentHeight;
    private int homeRightContentHeight;
    private int homeLeftPaneX;
    private int homeLeftPaneY;
    private int homeLeftPaneW;
    private int homeLeftPaneH;
    private int homeRightPaneX;
    private int homeRightPaneY;
    private int homeRightPaneW;
    private int homeRightPaneH;
    private int homeTrainPreviewX;
    private int homeTrainPreviewY;
    private int homeTrainPreviewW;
    private int homeTrainPreviewH;
    private int homeStationPreviewX;
    private int homeStationPreviewY;
    private int homeStationPreviewW;
    private int homeStationPreviewH;
    private final List<HomeClickTarget> homeClickTargets;
    private int factoryClutchScroll;
    private String factorySelectedClutchId;
    private boolean factoryDeliveryHistoryExpanded;
    private final List<FactoryClickTarget> factoryClickTargets;
    private final List<VehicleClickTarget> vehicleClickTargets;
    private final List<BattleBoardClickTarget> battleBoardClickTargets;
    private static final int[] TRAIN_ROUTE_PALETTE;
    private static final int SOURCE_PAGE_HOME = -1;
    private static final int SOURCE_PAGE_TRAIN = -2;
    private static final int SOURCE_PAGE_FACTORY = -3;
    private static final int SOURCE_PAGE_FLIGHT_DECK = -4;
    private static final int SOURCE_PAGE_GUIDANCE = -5;
    private static final int SOURCE_PAGE_VEHICLE_SETTINGS = -6;
    private static final int SOURCE_PAGE_INVENTORY_STATUS = -7;
    private static final int SOURCE_PAGE_FACTORY_STATUS = -8;
    private static final int SOURCE_PAGE_POWER = -9;
    private static final String SOURCE_GROUP_PREFIX = "__group__:";
    private static final String PRODUCTION_DETECTOR_GROUP_PAGE = "__group__:production_detector";
    private static final String REMEMBERED_HOME_PAGE = "__home__";
    private static final String REMEMBERED_TRAIN_PAGE = "__train__";
    private static final String REMEMBERED_FACTORY_PAGE = "__factory__";
    private static final String REMEMBERED_VEHICLE_PAGE = "__vehicle__";
    private static final String REMEMBERED_FLIGHT_DECK_PAGE = "__flight_deck__";
    private static final String REMEMBERED_GUIDANCE_PAGE = "__aeroline__";
    private static final String REMEMBERED_INVENTORY_STATUS_PAGE = "__inventory_status__";
    private static final String REMEMBERED_FACTORY_STATUS_PAGE = "__factory_status__";
    private static final String REMEMBERED_POWER_PAGE = "__power__";

    private final com.createstats.client.statspower.StatsPowerPage powerPage =
            new com.createstats.client.statspower.StatsPowerPage();
    private static String lastOpenedPageKey = REMEMBERED_HOME_PAGE;
    private static boolean openHomeOnNextOpen = false;
    private static final int PRODUCTION_RATE_WINDOW_SECONDS = 15;
    private static final long PRODUCTION_RATE_WINDOW_TICKS = PRODUCTION_RATE_WINDOW_SECONDS * 20L;
    private static final int PRODUCTION_SNAPSHOT_SAMPLE_TICKS = 20;
    private static final float MIN_VISIBLE_PRODUCTION_RATE = 0.01f;

    private enum RightPanelTab
    {
        DATA("Data", "Data"),
        TOOLS("Tools", "Tools");

        private final String label;
        private final String title;

        RightPanelTab(String label, String title) {
            this.label = label;
            this.title = title;
        }
    }

    public static void showHomeOnNextOpen() {
        openHomeOnNextOpen = true;
    }

    private enum HomeOutputMode
    {
        SINGLE("Single Channel"),
        DASHBOARD("Dashboard"),
        AUTO_DASHBOARD("Auto Dashboard"),
        SPECIALIZED("Specialized"),
        AERO("Is that a ship?"),
        AEROLINE("Aeroline Director"),
        ANALOG("Instruments");

        private final String label;

        HomeOutputMode(String label) {
            this.label = label;
        }
    }

    private enum HomeClickAction
    {
        FILTER_TYPE,
        FOCUS_SOURCE,
        INSPECT_OUTPUT,
        TOGGLE_SOURCE,
        SET_PRIMARY_SOURCE,
        CLEAR_PRIMARY_SOURCE,
        RENAME_SOURCE,
        TOGGLE_CHANNEL,
        SOLO_CHANNEL,
        SET_MODE,
        OPEN_INVENTORY_STATUS,
        OPEN_FACTORY_STATUS,
        SET_GRAPH_TYPE,
        SET_DASH_MAIN,
        CYCLE_RANGE,
        CYCLE_SPEED,
        TOGGLE_AUTO_SCALE,
        CYCLE_THEME,
        CYCLE_STYLE,
        CYCLE_DENSITY,
        CYCLE_EFFECT,
        TOGGLE_ANALOG_ZONES,
        TOGGLE_ANALOG_GLASS,
        TOGGLE_ANALOG_RIVETS,
        TOGGLE_PREDICTION,
        OPEN_SCANNER,
        EXPORT_SUMMARY,
        EXPORT_CSV;
    }

    private static final class HomeClickTarget
    {
        private final HomeClickAction action;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final String value;
        private final HomeOutputMode outputMode;
        private final StatsDisplaySettings.GraphType graphType;

        private HomeClickTarget(HomeClickAction action, int x, int y, int w, int h, String value, HomeOutputMode outputMode, StatsDisplaySettings.GraphType graphType) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.value = value;
            this.outputMode = outputMode;
            this.graphType = graphType;
        }

        private boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private enum FactoryClickAction
    {
        SELECT_CLUTCH,
        OPEN_CONTROLS,
        TOGGLE_ENABLED,
        CYCLE_MODE,
        MANUAL_RESET,
        MANUAL_TRIP,
        SELECT_TRANSMISSION,
        SELECT_LOAD_BALANCER,
        SELECT_MINI_DISPLAY,
        SELECT_FREIGHT,
        SELECT_FLYWHEEL,
        TOGGLE_FLYWHEEL_ARMED,
        FLYWHEEL_FORCE_PARK,
        FLYWHEEL_RELEASE,
        FLYWHEEL_LOW_RESERVE_DOWN,
        FLYWHEEL_LOW_RESERVE_UP,
        TOGGLE_DELIVERY_HISTORY,
        OPEN_TRANSMISSION_CONTROLS,
        TOGGLE_TRANSMISSION_ENABLED,
        CYCLE_TRANSMISSION_LIMIT_MODE,
        OPEN_LOAD_BALANCER_CONTROLS,
        TOGGLE_LOAD_BALANCER_ENABLED,
        CYCLE_LOAD_BALANCER_LIMIT_MODE,
        CYCLE_LOAD_BALANCER_SHARING_MODE,
        OPEN_MINI_DISPLAY_CONTROLS,
        CYCLE_MINI_DISPLAY_SOURCE_MODE,
        TOGGLE_MINI_DISPLAY_LIT,
        TOGGLE_MINI_DISPLAY_LABELS,
        HIDE_DONE_ORDERS,
        SHOW_HIDDEN_ORDERS,
        RESET_DONE_ORDERS;
    }

    private static final class FactoryClickTarget
    {
        private final FactoryClickAction action;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final String value;

        private FactoryClickTarget(FactoryClickAction action, int x, int y, int w, int h, String value) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.value = value;
        }

        private boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private enum VehicleClickAction
    {
        SET_NOSE,
        SET_PRESET;
    }

    private static final class VehicleClickTarget
    {
        private final VehicleClickAction action;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final String value;

        private VehicleClickTarget(VehicleClickAction action, int x, int y, int w, int h, String value) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.value = value;
        }

        private boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private enum BattleBoardClickAction
    {
        SET_PRESET;
    }

    private static final class BattleBoardClickTarget
    {
        private final BattleBoardClickAction action;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final String value;

        private BattleBoardClickTarget(BattleBoardClickAction action, int x, int y, int w, int h, String value) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.value = value;
        }

        private boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private enum IncomingCargoMatch {
        NONE(""),
        GOOD("GOOD"),
        PARTIAL("PART"),
        WRONG("WRONG");

        private final String label;

        IncomingCargoMatch(String label) {
            this.label = label;
        }
    }

    private static final class FactoryFreightEntry {
        private final String id;
        private StatsDisplayPanelBE.FreightManifestLink manifestLink;
        private StatsDisplayPanelBE.TrainStationLink stationLink;

        private FactoryFreightEntry(String id) {
            this.id = id;
        }

        private FreightManifest getManifest() {
            if (this.manifestLink != null) {
                return this.manifestLink.getManifest();
            }
            return this.stationLink != null ? this.stationLink.getManifest() : new FreightManifest();
        }

        private DeliveryOrderBoardView getDeliveryBoard() {
            DeliveryOrderBoardView stationBoard = this.stationLink != null ? this.stationLink.getDeliveryBoard() : DeliveryOrderBoardView.empty(0L, "No freight data.");
            DeliveryOrderBoardView manifestBoard = this.manifestLink != null ? this.manifestLink.getDeliveryBoard() : DeliveryOrderBoardView.empty(0L, "No freight data.");
            if (stationBoard.getTotalOrders() > 0 || !stationBoard.getLineViews().isEmpty()) {
                return stationBoard;
            }
            return manifestBoard;
        }

        private String getDisplayName() {
            if (this.stationLink != null) {
                String stationName = this.stationLink.getStationName();
                if (stationName != null && !stationName.isBlank()) {
                    return stationName;
                }
            }
            if (this.manifestLink != null) {
                String stationName = this.manifestLink.getStationLink().getStationName();
                if (stationName != null && !stationName.isBlank()) {
                    return stationName;
                }
                return "Clipboard " + this.manifestLink.getSourcePos().getX() + "," + this.manifestLink.getSourcePos().getY() + "," + this.manifestLink.getSourcePos().getZ();
            }
            return this.stationLink != null ? "Station " + this.stationLink.getSourcePos().getX() + "," + this.stationLink.getSourcePos().getY() + "," + this.stationLink.getSourcePos().getZ() : "Freight";
        }
    }

    private static final class ManifestProgressLine {
        private final FreightManifestLine line;
        private final int progress;
        private final boolean valid;

        private ManifestProgressLine(FreightManifestLine line, int progress, boolean valid) {
            this.line = line;
            this.progress = progress;
            this.valid = valid;
        }
    }

    private static final class ChartRange
    {
        private final float min;
        private final float max;
        private final float range;
        private final boolean autoZoomed;

        private ChartRange(float min, float max, float range, boolean autoZoomed) {
            this.min = min;
            this.max = max;
            this.range = range;
            this.autoZoomed = autoZoomed;
        }
    }

    public StatsDisplayScreen(StatsDisplayPanelBE panel) {
        super(Component.literal("Stats Display"));
        this.selectedTab = -1;
        this.tabScrollOffset = 0;
        this.pinnedChannels = new HashSet<Integer>();
        this.itemIconCache = new HashMap<String, ItemStack>();
        this.selectedSource = -1;
        this.sourceIds = new ArrayList<String>();
        this.sourceTabScrollOffset = 0;
        this.sourceDropdownOpen = false;
        this.signalDropdownOpen = false;
        this.sourceDropdownScrollOffset = 0;
        this.signalDropdownScrollOffset = 0;
        this.timeScale = 0;
        this.showStats = false;
        this.showLog = false;
        this.redstoneConfigOpen = false;
        this.tabBarHeight = 24;
        this.hoverDataIndex = -1;
        this.hoverValue = 0.0f;
        this.hoverLabel = "";
        this.redstoneValueInput = "";
        this.manualYAxisEnabled = false;
        this.manualYAxisMinDraft = "";
        this.manualYAxisMaxDraft = "";
        this.manualYAxisMin = 0.0f;
        this.manualYAxisMax = 100.0f;
        this.sourceRenameOpen = false;
        this.renamingSourceId = null;
        this.productionScrollOffset = 0;
        this.capacityScrollOffset = 0;
        this.capacityExpandedSource = null;
        this.inventoryScrollOffset = 0;
        this.inventorySortMode = 0;
        this.factoryStatusScrollOffset = 0;
        this.factoryStatusSortMode = 0;
        this.prodRowHits.clear();
        this.annotateMode = false;
        this.annotationInputOpen = false;
        this.pendingAnnotationTick = 0L;
        this.editingAnnotationId = null;
        this.lastKnownTrainConnection = false;
        this.lastAnnotationClickTime = 0L;
        this.lastAnnotationClickId = null;
        this.logScrollOffset = 0;
        this.homeTrainPreviewScroll = 0;
        this.homeStationPreviewScroll = 0;
        this.selectedRightPanelTab = RightPanelTab.DATA;
        this.homeSourceTypeFilter = "";
        this.homeFocusedSourceId = "";
        this.homeLeftScroll = 0;
        this.homeRightScroll = 0;
        this.homeLeftContentHeight = 0;
        this.homeRightContentHeight = 0;
        this.homeLeftPaneX = 0;
        this.homeLeftPaneY = 0;
        this.homeLeftPaneW = 0;
        this.homeLeftPaneH = 0;
        this.homeRightPaneX = 0;
        this.homeRightPaneY = 0;
        this.homeRightPaneW = 0;
        this.homeRightPaneH = 0;
        this.homeClickTargets = new ArrayList<HomeClickTarget>();
        this.factoryClutchScroll = 0;
        this.factorySelectedClutchId = "";
        this.factoryDeliveryHistoryExpanded = false;
        this.factoryClickTargets = new ArrayList<FactoryClickTarget>();
        this.vehicleClickTargets = new ArrayList<VehicleClickTarget>();
        this.battleBoardClickTargets = new ArrayList<BattleBoardClickTarget>();
        this.trainDashboardDimensions = new ArrayList<String>();
        this.trainDashboardActiveDimension = "";
        this.trainDashboardTrainScroll = 0;
        this.trainDashboardStationScroll = 0;
        this.selectedTrainDashboardTrainId = null;
        this.selectedTrainDashboardStationId = null;
        this.trainDashboardPrevWorld = new HashMap<String, float[]>();
        this.trainDashboardCurrWorld = new HashMap<String, float[]>();
        this.trainDashboardLastUpdateMs = 0L;
        this.trainDashboardSnapshotTick = Long.MIN_VALUE;
        this.trainActivityPingCooldown = 0;
        if (!panel.isController() && panel.getControllerPos() != null && panel.getLevel() != null) {
            final BlockEntity ctrlBE = panel.getLevel().getBlockEntity(panel.getControllerPos());
            if (ctrlBE instanceof StatsDisplayPanelBE) {
                final StatsDisplayPanelBE controller = panel = (StatsDisplayPanelBE)ctrlBE;
            }
        }
        this.panel = panel;
        this.timeScale = this.defaultTimeScale(panel.getSettings());
        this.viewType = panel.getSettings().getGraphType();
        if (this.viewType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            this.viewType = StatsDisplaySettings.GraphType.LINE_CHART;
        }
        this.applyTheme(panel.getSettings().getDisplayTheme());
        this.splashQuote = StatsDisplayScreen.SPLASH_QUOTES[new Random().nextInt(StatsDisplayScreen.SPLASH_QUOTES.length)];
        this.splashOpenTime = System.currentTimeMillis();
    }

    public void tick() {
        super.tick();
        this.pingPanelActivityIfNeeded();
    }

    private int defaultTimeScale(StatsDisplaySettings settings) {
        if (settings == null) {
            return 300;
        }
        final int seconds = settings.getTimeRange().getSeconds();
        return (seconds == Integer.MAX_VALUE) ? 0 : seconds;
    }

    private String currentTimeScaleLabel() {
        return (this.timeScale > 0) ? this.formatTimeDuration(this.timeScale) : "All";
    }

    private void cycleDisplayedTimeScale() {
        int currentIndex = StatsDisplayScreen.TIME_SCALES.length - 1;
        for (int i = 0; i < StatsDisplayScreen.TIME_SCALES.length; ++i) {
            if (StatsDisplayScreen.TIME_SCALES[i] == this.timeScale) {
                currentIndex = i;
                break;
            }
        }
        this.timeScale = StatsDisplayScreen.TIME_SCALES[(currentIndex + 1) % StatsDisplayScreen.TIME_SCALES.length];
        this.rebuildWidgets();
    }

    private String yScaleModeLabel() {
        if (this.manualYAxisEnabled) {
            return "Y Scale: Manual";
        }
        return this.panel.getSettings().isAutoScale() ? "Y Scale: Auto" : "Y Scale: Fixed";
    }

    private void cycleYAxisMode() {
        if (this.manualYAxisEnabled) {
            this.manualYAxisEnabled = false;
            this.panel.getSettings().setAutoScale(true);
            this.applyPanelSettingsChange();
            this.rebuildWidgets();
            return;
        }
        if (this.panel.getSettings().isAutoScale()) {
            this.panel.getSettings().setAutoScale(false);
            this.applyPanelSettingsChange();
            this.rebuildWidgets();
            return;
        }
        this.manualYAxisEnabled = true;
        this.captureManualYAxisFromVisibleData();
        this.rebuildWidgets();
    }

    private float[] getDisplayedData(TimeSeriesStore store) {
        return (this.timeScale > 0) ? store.getDataForTimeScale(this.timeScale) : store.getData();
    }

    private ChartRange buildChartRange(float[] data, boolean allowAutoZoom) {
        if (data.length == 0) {
            return new ChartRange(0.0f, 1.0f, 1.0f, false);
        }
        if (this.manualYAxisEnabled) {
            final float minManual = Math.min(this.manualYAxisMin, this.manualYAxisMax);
            final float maxManual = Math.max(this.manualYAxisMin, this.manualYAxisMax);
            final float rangeManual = Math.max(0.001f, maxManual - minManual);
            return new ChartRange(minManual, minManual + rangeManual, rangeManual, false);
        }
        final boolean autoScale = this.panel.getSettings().isAutoScale();
        final int n = data.length;
        final int recentStart = Math.max(0, n - Math.max(n / 4, 10));
        float allMin = Float.MAX_VALUE;
        float allMax = -3.4028235E38f;
        for (float v : data) {
            allMin = Math.min(allMin, v);
            allMax = Math.max(allMax, v);
        }
        float min = allMin;
        float max = allMax;
        boolean zoomed = false;
        if (allowAutoZoom && autoScale && n > 20) {
            float recMin = Float.MAX_VALUE;
            float recMax = -3.4028235E38f;
            for (int i = recentStart; i < n; ++i) {
                recMin = Math.min(recMin, data[i]);
                recMax = Math.max(recMax, data[i]);
            }
            final float allRange = allMax - allMin;
            final float recRange = recMax - recMin;
            if (allRange > 0.0f && recRange < allRange * 0.3f) {
                min = recMin;
                max = recMax;
                zoomed = true;
            }
        }
        float range = max - min;
        final float avgVal = (min + max) / 2.0f;
        final float minRange = Math.max(Math.abs(avgVal) * 0.05f, 1.0f);
        if (range < minRange) {
            range = minRange;
            min = avgVal - range / 2.0f;
            max = avgVal + range / 2.0f;
        }
        final float niceStep = niceNumber(range / 4.0f);
        min = (float)(Math.floor(min / niceStep) * niceStep);
        max = (float)(Math.ceil(max / niceStep) * niceStep);
        range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
            min -= 0.5f;
            max += 0.5f;
        }
        return new ChartRange(min, max, range, zoomed);
    }

    private void captureManualYAxisFromVisibleData() {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (this.selectedTab < 0 || this.selectedTab >= channels.size()) {
            return;
        }
        final float[] data = this.getDisplayedData(channels.get(this.selectedTab).getStore());
        if (data.length == 0) {
            return;
        }
        final ChartRange range = this.buildChartRange(data, true);
        this.manualYAxisMin = range.min;
        this.manualYAxisMax = range.max;
        this.manualYAxisMinDraft = this.formatManualAxisValue(this.manualYAxisMin);
        this.manualYAxisMaxDraft = this.formatManualAxisValue(this.manualYAxisMax);
        if (this.manualYAxisMinInput != null) {
            this.manualYAxisMinInput.setValue(this.manualYAxisMinDraft);
        }
        if (this.manualYAxisMaxInput != null) {
            this.manualYAxisMaxInput.setValue(this.manualYAxisMaxDraft);
        }
    }

    private void applyManualYAxisInputs() {
        final Float parsedMin = this.parseEditableFloat(this.manualYAxisMinDraft);
        final Float parsedMax = this.parseEditableFloat(this.manualYAxisMaxDraft);
        if (parsedMin == null || parsedMax == null) {
            this.showExportFeedback("\u00a7cEnter valid Y min/max values", false);
            return;
        }
        if (parsedMax <= parsedMin) {
            this.showExportFeedback("\u00a7cY max must be greater than Y min", false);
            return;
        }
        this.manualYAxisMin = parsedMin;
        this.manualYAxisMax = parsedMax;
        this.manualYAxisMinDraft = this.formatManualAxisValue(parsedMin);
        this.manualYAxisMaxDraft = this.formatManualAxisValue(parsedMax);
        if (this.manualYAxisMinInput != null) {
            this.manualYAxisMinInput.setValue(this.manualYAxisMinDraft);
        }
        if (this.manualYAxisMaxInput != null) {
            this.manualYAxisMaxInput.setValue(this.manualYAxisMaxDraft);
        }
        this.showExportFeedback("\u00a7aY range updated", true);
        this.rebuildWidgets();
    }

    private String formatManualAxisValue(float value) {
        if (Math.abs(value) >= 100.0f || value == (int)value) {
            return Integer.toString((int)value);
        }
        return String.format("%.2f", value);
    }

    private void applyTheme(DisplayTheme theme) {
        this.BG = theme.bg;
        this.TEXT = theme.text;
        this.GRID = theme.grid;
        this.ACCENT = theme.accent;
        this.DIM = theme.dim;
        this.BORDER = theme.border;
        this.GRAPH_BG = theme.graphBg;
        this.TAB_BG = theme.tabBg;
        this.TAB_SEL = theme.tabSel;
        this.TAB_HOVER = theme.tabHover;
        this.WARN = theme.warn;
        this.THRESH_LINE = theme.threshLine;
    }

    protected void init() {
        this.syncSourceIds();
        this.restoreRememberedSourcePage();
        this.validateGuiPageSelection();
        this.rememberCurrentPageSelection();
        this.normalizeHomeSelection(this.panel.getSourceRegistry());
        this.panelWidth = Math.min((int)(this.width * 0.92), 720);
        this.panelHeight = Math.min((int)(this.height * 0.88), 480);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        final boolean multiSource = this.getVisibleSourceTabCount() > 1;
        final int sourceBarH = multiSource ? 20 : 0;
        final int margin = 10;
        final boolean chartContext = this.shouldShowChartControls();
        final int sideInset = chartContext ? 36 : 0;
        this.graphLeft = this.panelLeft + margin + sideInset;
        final int tabReserve = chartContext ? this.tabBarHeight : 0;
        this.graphTop = this.panelTop + 30 + sourceBarH + tabReserve;
        this.graphWidth = this.panelWidth - margin * 2 - sideInset;
        final int bottomReserve = chartContext ? 110 : 58;
        this.graphHeight = this.panelHeight - bottomReserve - tabReserve - sourceBarH;
        this.optionPanelWidth = 0;
        this.optionPanelLeft = 0;
        this.optionPanelTop = 0;
        this.optionPanelHeight = 0;
        if (chartContext) {
            this.optionPanelWidth = Math.max(160, Math.min(210, (this.panelWidth * 27) / 100));
            this.graphWidth -= this.optionPanelWidth + 6;
        }
        final boolean sidePanelControls = chartContext && this.optionPanelWidth > 0;
        if (sidePanelControls) {
            this.graphHeight += 22;
        }
        if ((this.showStats || this.showLog) && chartContext) {
            this.graphHeight -= 80;
        }
        if (chartContext) {
            this.optionPanelLeft = this.graphLeft + this.graphWidth + 6;
            this.optionPanelTop = this.graphTop;
            this.optionPanelHeight = this.graphHeight;
        }
        final int btnY = this.panelTop + this.panelHeight - 28;
        int btnX = this.panelLeft + margin;
        String s = null;
        if (chartContext) {
            for (StatsDisplaySettings.GraphType t : StatsDisplaySettings.GraphType.values()) {
                if (t == StatsDisplaySettings.GraphType.TRAIN_MAP
                        || t == StatsDisplaySettings.GraphType.CAPACITY
                        || t == StatsDisplaySettings.GraphType.PRODUCTION
                        || t == StatsDisplaySettings.GraphType.INVENTORY_STATUS
                        || t == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
                    continue;
                }
                final StatsDisplaySettings.GraphType type = t;
                s = switch (type) {
                    default -> throw new IncompatibleClassChangeError();
                    case LINE_CHART -> ", Line";
                    case BAR_CHART -> "\u2581 Bar";
                    case GAUGE -> "\u25d4 Gauge";
                    case NUMERIC -> "# Num";
                    case CAPACITY -> "\u2261 Cap";
                    case PRODUCTION -> "\u2630 Prod";
                    case ANALOG_GAUGE -> "\u2609 Inst";
                    case EFFICIENCY -> "\u2605 Grade";
                    case TOPOLOGY -> "\u2b21 Topo";
                    case TRAIN_MAP -> "\ud83d\ude82 Train";
                    case INVENTORY_STATUS -> "\u2261 Inv";
                    case FACTORY_STATUS -> "\u2699 Factory";
                };
                final String label = s;
                final PanelButton btn = this.addPanelButton(btnX, btnY, 44, 18, Component.literal(label), b -> {
                    this.viewType = t;
                    this.rebuildWidgets();
                });
                btn.active = this.viewType != t;
                btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(this.viewTypeTooltip(type))));
                btnX += 47;
            }
            btnX = this.panelLeft + this.panelWidth - margin;
            for (int i = StatsDisplayScreen.TIME_SCALES.length - 1; i >= 0; --i) {
                final int ts = StatsDisplayScreen.TIME_SCALES[i];
                final String tsLabel = StatsDisplayScreen.TIME_SCALE_LABELS[i];
                final int bw = this.font.width(tsLabel) + 10;
                btnX -= bw + 3;
                final PanelButton tsBtn = this.addPanelButton(btnX, btnY, bw, 18, Component.literal(tsLabel), b -> {
                    this.timeScale = ts;
                    this.rebuildWidgets();
                });
                tsBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("All".equals(tsLabel) ? "Show all recorded data" : ("Show the last " + tsLabel))));
                if (this.timeScale == ts) {
                    tsBtn.active = false;
                }
            }
        }
        final int row2Y = btnY - 20;
        final int r2x = this.panelLeft + margin;
        final int hudButtonX = Math.max(4, this.panelLeft - 38);
        this.addRenderableWidget(new HudStudioEntryButton(
                hudButtonX, this.panelTop + 2, 36, 16,
                () -> this.panel.getSettings().getDisplayTheme(),
                () -> Boolean.FALSE,
                this::openStressometerHudSettings));
        final int customButtonX = Math.max(4, this.panelLeft - 68);
        final int customButtonW = Math.max(44, Math.min(66, this.panelLeft - customButtonX - 2));
        this.addRenderableWidget(Button.builder(Component.literal("Custom"), button -> this.openCustomScreenStudio())
            .bounds(customButtonX, this.panelTop + 20, customButtonW, 16)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal(this.customLayoutModeButtonLabel()), button -> this.toggleCustomLayoutMode())
            .bounds(customButtonX, this.panelTop + 38, customButtonW, 16)
            .build());
        final Button profileButton = Button.builder(Component.literal(this.customProfileButtonLabel()), button -> this.cycleCustomScreenProfile())
            .bounds(customButtonX, this.panelTop + 56, customButtonW, 16)
            .build();
        profileButton.active = this.panel.getSettings().hasCustomScreenLayout();
        profileButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(this.customProfileTooltip())));
        this.addRenderableWidget(profileButton);
        if (chartContext && !sidePanelControls) {
            this.addRenderableWidget(Button.builder(Component.literal(this.showStats ? "\u25bc Stats" : "\u25b6 Stats"), b -> this.toggleStatsPanel()).bounds(r2x, row2Y, 56, 16).build());
            final Button logBtn = Button.builder(Component.literal(this.showLog ? "\u25bc Log" : "\u25b6 Log"), b -> this.toggleDataLog()).bounds(r2x + 60, row2Y, 50, 16).build();
            this.addRenderableWidget(logBtn);
            final Button exportBtn = Button.builder(Component.literal("\u2398 Export"), b -> this.exportToClipboard(false)).bounds(r2x + 114, row2Y, 58, 16).build();
            this.addRenderableWidget(exportBtn);
            final Button csvBtn = Button.builder(Component.literal("CSV"), b -> this.exportToClipboard(true)).bounds(r2x + 176, row2Y, 32, 16).build();
            this.addRenderableWidget(csvBtn);
            final Button predictBtn = Button.builder(Component.literal(this.predictionButtonLabel()), b -> this.togglePrediction()).bounds(r2x + 212, row2Y, 70, 16).build();
            this.addRenderableWidget(predictBtn);
            if (this.selectedTab >= 0) {
                final Button rsBtn = Button.builder(Component.literal("\u26a1 Redstone"), b -> this.toggleRedstoneConfigPanel()).bounds(r2x + 286, row2Y, 62, 16).build();
                this.addRenderableWidget(rsBtn);
            }
            final int annotateX = (this.selectedTab >= 0) ? (r2x + 408) : (r2x + 286);
            final Button annotateBtn = Button.builder(Component.literal(this.annotateButtonLabel()), b -> this.toggleAnnotateMode()).bounds(annotateX, row2Y, 70, 16).build();
            this.addRenderableWidget(annotateBtn);
        }
        if (this.selectedTab == -1 && (this.isFlightDeckPage() || this.isGuidancePage())) {
            final PinnedAeroHudOverlay.Mode hudMode = this.isFlightDeckPage()
                    ? PinnedAeroHudOverlay.Mode.FLIGHT_DECK
                    : PinnedAeroHudOverlay.Mode.AEROLINE;
            final boolean alreadyPinned = PinnedAeroHudOverlay.isPinned(this.panel, hudMode);
            final int controlsY = this.panelTop + this.panelHeight - 20;
            final int controlsX = this.panelLeft + 8;
            final int pinW = 84;
            final int cornerW = 104;
            final int sliderW = Math.max(118, Math.min(156, this.panelWidth - 24 - pinW - cornerW - 12));
            final PinnedHudControlButton pinButton = this.addRenderableWidget(new PinnedHudControlButton(
                    controlsX,
                    controlsY,
                    pinW,
                    16,
                    () -> this.panel.getSettings().getDisplayTheme(),
                    Component.literal(alreadyPinned ? "Unpin HUD" : "Pin HUD"),
                    () -> {
                if (alreadyPinned) {
                    PinnedAeroHudOverlay.clear();
                    this.rebuildWidgets();
                    return;
                }
                PinnedAeroHudOverlay.pin(this.panel, hudMode);
                this.onClose();
                    }));
            pinButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Pin or unpin the current PiP HUD.")));

            final PinnedHudControlButton cornerButton = this.addRenderableWidget(new PinnedHudControlButton(
                    controlsX + pinW + 6,
                    controlsY,
                    cornerW,
                    16,
                    () -> this.panel.getSettings().getDisplayTheme(),
                    Component.literal(PinnedAeroHudOverlay.anchorLabel()),
                    () -> {
                        PinnedAeroHudOverlay.cycleAnchor();
                        this.rebuildWidgets();
                    }));
            cornerButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cycle the pinned HUD corner.")));

            final PinnedHudOpacitySlider opacitySlider = this.addRenderableWidget(new PinnedHudOpacitySlider(
                    controlsX + pinW + cornerW + 12,
                    controlsY,
                    sliderW,
                    16,
                    () -> this.panel.getSettings().getDisplayTheme(),
                    PinnedAeroHudOverlay.getHudOpacity(),
                    0.35f,
                    1.0f,
                    PinnedAeroHudOverlay::setHudOpacity));
            opacitySlider.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Adjust PiP HUD opacity.")));
        }
        this.channelListVisibleRows = 0;
        if (sidePanelControls) {
            if (this.isSpecializedFlowMeterView()) {
                this.addFlowMeterTriggerWidgets();
            } else {
                this.addOptionPanelWidgets();
            }
        }
        this.annotationLabelInput = null;
        if (this.annotationInputOpen) {
            final int popX = Math.max(this.panelLeft + 4, Math.min(this.annotationInputX, this.panelLeft + this.panelWidth - 160));
            final int popY = Math.max(this.panelTop + 30, Math.min(this.annotationInputY + 10, this.panelTop + this.panelHeight - 55));
            (this.annotationLabelInput = new EditBox(this.font, popX, popY, 140, 16, Component.literal("Label"))).setMaxLength(48);
            this.annotationLabelInput.setFocused(true);
            if (this.editingAnnotationId != null) {
                final Annotation existing = this.panel.getAnnotationManager().getById(this.editingAnnotationId);
                if (existing != null && this.annotationLabelInput.getValue().isEmpty()) {
                    this.annotationLabelInput.setValue(existing.getLabel());
                }
            }
            this.addRenderableWidget(this.annotationLabelInput);
            final int swatchX = popX;
            final int swatchY = popY + 20;
            for (int ci = 0; ci < StatsDisplayScreen.ANNOTATION_COLOR_PRESETS.length; ++ci) {
                final int presetColor = StatsDisplayScreen.ANNOTATION_COLOR_PRESETS[ci];
                final boolean isSelected = presetColor == this.selectedAnnotationColor;
                final String symbol = isSelected ? "\u25a3" : "\u25a0";
                this.addRenderableWidget(Button.builder(Component.literal(symbol).withStyle(style -> style.withColor(presetColor & 0xFFFFFF)), b -> {
                    this.selectedAnnotationColor = presetColor;
                    this.rebuildWidgets();
                }).bounds(swatchX + ci * 22, swatchY, 18, 14).build());
            }
            this.addRenderableWidget(Button.builder(Component.literal("\u2713"), b -> this.confirmAnnotation()).bounds(popX + 144, popY, 18, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("\u2715"), b -> {
                this.annotationInputOpen = false;
                this.annotateMode = false;
                this.editingAnnotationId = null;
                this.rebuildWidgets();
            }).bounds(popX + 144, popY + 20, 18, 14).build());
        }
        this.redstoneThresholdInput = null;
        if (this.redstoneConfigOpen && this.selectedTab >= 0) {
            final List<StatsDisplayPanelBE.DataChannel> rchs = this.getFilteredChannels();
            if (this.selectedTab < rchs.size()) {
                final StatsDisplayPanelBE.DataChannel rch = rchs.get(this.selectedTab);
                final int rpw = Math.max(135, this.optionPanelWidth);
                final int rpx = this.optionPanelLeft;
                final int rpy = this.graphTop;
                this.addPanelButton(rpx + rpw - 18, rpy + 2, 14, 14, Component.literal("\u2715"), b -> {
                    this.applyRedstoneThreshold();
                    this.redstoneConfigOpen = false;
                    this.redstoneValueInput = "";
                    this.rebuildWidgets();
                });
                final String currentChId = this.panel.getRedstoneChannelId();
                final String chDisplayLabel = this.truncate(this.channelLabel(currentChId), 82);
                this.addPanelButton(rpx + 4, rpy + 32, 90, 16, Component.literal(chDisplayLabel), b -> {
                    final List<StatsDisplayPanelBE.DataChannel> chs = this.getFilteredChannels();
                    final String curId = this.panel.getRedstoneChannelId();
                    int nextIdx = 0;
                    for (int ci = 0; ci < chs.size(); ++ci) {
                        if (chs.get(ci).getId().equals(curId)) {
                            nextIdx = (ci + 1) % chs.size();
                            break;
                        }
                    }
                    if (!chs.isEmpty()) {
                        this.panel.setRedstoneChannelId(chs.get(nextIdx).getId());
                        this.sendRedstonePacket();
                    }
                    this.rebuildWidgets();
                });
                this.addPanelButton(rpx + 97, rpy + 32, 34, 16, Component.literal("Clear"), b -> {
                    this.panel.setRedstoneChannelId("");
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                (this.redstoneThresholdInput = new EditBox(this.font, rpx + 4, rpy + 60, 55, 16, Component.literal("Threshold"))).setFilter(text -> text.isEmpty() || text.matches("-?[0-9]*\\.?[0-9]*"));
                if (this.redstoneValueInput.isEmpty()) {
                    this.redstoneValueInput = String.valueOf(this.panel.getRedstoneThreshold());
                }
                this.redstoneThresholdInput.setValue(this.redstoneValueInput);
                this.redstoneThresholdInput.setResponder(text -> this.redstoneValueInput = text);
                this.addRenderableWidget(this.redstoneThresholdInput);
                this.addPanelButton(rpx + 63, rpy + 60, 18, 16, Component.literal("\u25b2"), b -> this.adjustRedstoneThreshold(true));
                this.addPanelButton(rpx + 83, rpy + 60, 18, 16, Component.literal("\u25bc"), b -> this.adjustRedstoneThreshold(false));
                final int rsMode = this.panel.getRedstoneMode();
                final PanelButton binaryBtn = this.addPanelButton(rpx + 4, rpy + 88, 62, 16, Component.literal("Binary"), b -> {
                    this.panel.setRedstoneMode(0);
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                binaryBtn.active = rsMode != 0;
                final PanelButton propBtn = this.addPanelButton(rpx + 69, rpy + 88, 62, 16, Component.literal("Prop."), b -> {
                    this.panel.setRedstoneMode(1);
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                propBtn.active = rsMode != 1;
                final boolean rsAbove = this.panel.isRedstoneAbove();
                final PanelButton rsAboveBtn = this.addPanelButton(rpx + 4, rpy + 116, 62, 16, Component.literal("Above \u2191"), b -> {
                    this.panel.setRedstoneAbove(true);
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                rsAboveBtn.active = !rsAbove;
                final PanelButton rsBelowBtn = this.addPanelButton(rpx + 69, rpy + 116, 62, 16, Component.literal("Below \u2193"), b -> {
                    this.panel.setRedstoneAbove(false);
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                rsBelowBtn.active = rsAbove;
                final boolean rsEnabled = this.panel.isRedstoneEnabled();
                this.addPanelButton(rpx + 4, rpy + 138, 86, 16, Component.literal(rsEnabled ? "\u25cf Enabled" : "\u25cb Disabled"), b -> {
                    this.panel.setRedstoneEnabled(!this.panel.isRedstoneEnabled());
                    this.sendRedstonePacket();
                    this.rebuildWidgets();
                });
                final int gty2 = rpy + 162;
                int gtx2 = rpx + 4;
                int gtRowY2 = gty2;
                final int gtMaxX2 = rpx + rpw - 4;
                for (StatsDisplaySettings.GraphType t3 : StatsDisplaySettings.GraphType.values()) {
                    if (t3 == StatsDisplaySettings.GraphType.TRAIN_MAP
                            || t3 == StatsDisplaySettings.GraphType.CAPACITY
                            || t3 == StatsDisplaySettings.GraphType.PRODUCTION
                            || t3 == StatsDisplaySettings.GraphType.INVENTORY_STATUS
                            || t3 == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
                        continue;
                    }
                    final StatsDisplaySettings.GraphType type3 = t3;
                    final String gtLabel2 = switch (type3) {
                        case LINE_CHART -> ",";
                        case BAR_CHART -> "\u2581";
                        case GAUGE -> "\u25d4";
                        case NUMERIC -> "#";
                        case ANALOG_GAUGE -> "\u2609";
                        case EFFICIENCY -> "\u2605";
                        case TOPOLOGY -> "\u2b21";
                        default -> "?";
                    };
                    if (gtx2 + 20 > gtMaxX2) {
                        gtx2 = rpx + 4;
                        gtRowY2 += 18;
                    }
                    final PanelButton gtBtn2 = this.addPanelButton(gtx2, gtRowY2, 20, 16, Component.literal(gtLabel2), b -> {
                        this.viewType = t3;
                        this.ensureGuiViewTypeValid();
                        this.rebuildWidgets();
                    });
                    gtBtn2.active = this.viewType != type3;
                    gtx2 += 22;
                }
            }
        }
    }

    private void addOptionPanelWidgets() {
        if (this.redstoneConfigOpen) {
            return;
        }
        final int tabX = this.optionPanelLeft + 4;
        final int tabY = this.optionPanelTop + 6;
        final int tabGap = 2;
        final int tabCount = RightPanelTab.values().length;
        final int tabW = Math.max(30, (this.optionPanelWidth - 8 - tabGap * Math.max(0, tabCount - 1)) / tabCount);
        int drawX = tabX;
        for (RightPanelTab tab : RightPanelTab.values()) {
            final Button tabButton = Button.builder(Component.literal(tab.label), b -> {
                this.selectedRightPanelTab = tab;
                this.rebuildWidgets();
            }).bounds(drawX, tabY, tabW, 14).build();
            if (this.selectedRightPanelTab == tab) {
                tabButton.active = false;
            }
            this.addRenderableWidget(tabButton);
            drawX += tabW + tabGap;
        }
        final int contentX = this.optionPanelLeft + 6;
        final int contentY = this.optionPanelTop + 54;
        final int contentW = this.optionPanelWidth - 12;
        switch (this.selectedRightPanelTab) {
            case DATA -> {
                final int channelCount = this.getFilteredChannels().size();

                final int listBudget = this.optionPanelHeight - 54 - 11 - 30 - 160;
                final int maxRows = Math.max(0, Math.min(3, listBudget / 16));
                int listH = 0;
                if (channelCount >= 2 && maxRows >= 1) {
                    this.channelListVisibleRows = Math.min(channelCount, maxRows);
                    this.channelListRowH = 16;
                    this.channelListTop = contentY + 11;
                    listH = 11 + this.channelListVisibleRows * this.channelListRowH + 6;
                    final int maxScroll = Math.max(0, channelCount - this.channelListVisibleRows);
                    this.optionPanelChannelScroll = Math.max(0, Math.min(this.optionPanelChannelScroll, maxScroll));
                } else {
                    this.channelListVisibleRows = 0;
                    this.optionPanelChannelScroll = 0;
                }
                this.addDataOptionWidgets(contentX, contentY + listH, contentW);
            }
            case TOOLS -> {
                this.channelListVisibleRows = 0;
                if (this.isStockpileSourceView()) {
                    this.loadStockpileThresholdState();
                    this.addStockpileThresholdWidgets(contentX, contentY, contentW);
                    this.addToolOptionWidgets(contentX, contentY + 54, contentW);
                } else {
                    final int summaryH = (this.selectedTab >= 0 && !this.redstoneConfigOpen) ? 26 : 0;
                    this.addToolOptionWidgets(contentX, contentY + summaryH, contentW);
                }
            }
        }
    }

    private void addDataOptionWidgets(int x, int y, int w) {
        this.manualYAxisMinInput = null;
        this.manualYAxisMaxInput = null;
        final List<StatsDisplayPanelBE.DataChannel> capChannels = this.getFilteredChannels();
        final PanelCapabilities.ChannelKind focusKind = this.focusedChannelKind(capChannels);
        final PanelCapabilities.SourceCapabilities caps = this.currentSourceCapabilities(capChannels);
        final boolean allowYRange = (focusKind != null)
            ? PanelCapabilities.supportsManualYRange(focusKind)
            : (caps.has(PanelCapabilities.ChannelKind.CONTINUOUS) || caps.has(PanelCapabilities.ChannelKind.GENERIC));
        final boolean allowPrediction = (focusKind != null)
            ? PanelCapabilities.supportsPrediction(focusKind)
            : caps.has(PanelCapabilities.ChannelKind.CONTINUOUS);
        int rowY = y;
        this.addPanelButton(x, rowY, w, 16, Component.literal(this.statsPanelButtonLabel()), b -> this.toggleStatsPanel());
        rowY += 18;
        this.addPanelButton(x, rowY, w, 16, Component.literal(this.dataLogButtonLabel()), b -> this.toggleDataLog());
        rowY += 18;
        final PanelButton matchPageBtn = this.addPanelButton(x, rowY, w, 16, Component.literal("Match Current Page"), b -> this.setConfiguredPreferredSourceId(this.getCurrentSourcePageId()));
        matchPageBtn.active = this.getCurrentSourcePageId() != null;
        rowY += 18;
        this.addPanelButton(x, rowY, w, 16, Component.literal("Window: " + this.currentTimeScaleLabel()), b -> this.cycleDisplayedTimeScale());
        rowY += 18;
        if (allowYRange) {
            final PanelButton yScaleBtn = this.addPanelButton(x, rowY, w, 16, Component.literal(this.yScaleModeLabel()), b -> this.cycleYAxisMode());
            yScaleBtn.active = this.viewType == StatsDisplaySettings.GraphType.LINE_CHART || this.viewType == StatsDisplaySettings.GraphType.BAR_CHART;
            rowY += 18;
            if (this.manualYAxisEnabled) {
                final int inputW = Math.max(40, (w - 4) / 2);
                this.manualYAxisMinInput = new EditBox(this.font, x, rowY, inputW, 16, Component.literal("Y Min"));
                this.manualYAxisMinInput.setFilter(text -> text.isEmpty() || text.matches("-?[0-9]*\\.?[0-9]*"));
                if (this.manualYAxisMinDraft.isEmpty()) {
                    this.manualYAxisMinDraft = this.formatManualAxisValue(this.manualYAxisMin);
                }
                this.manualYAxisMinInput.setValue(this.manualYAxisMinDraft);
                this.manualYAxisMinInput.setResponder(text -> this.manualYAxisMinDraft = text);
                this.manualYAxisMinInput.setHint(Component.literal("Y min"));
                this.addRenderableWidget(this.manualYAxisMinInput);
                this.manualYAxisMaxInput = new EditBox(this.font, x + inputW + 4, rowY, inputW, 16, Component.literal("Y Max"));
                this.manualYAxisMaxInput.setFilter(text -> text.isEmpty() || text.matches("-?[0-9]*\\.?[0-9]*"));
                if (this.manualYAxisMaxDraft.isEmpty()) {
                    this.manualYAxisMaxDraft = this.formatManualAxisValue(this.manualYAxisMax);
                }
                this.manualYAxisMaxInput.setValue(this.manualYAxisMaxDraft);
                this.manualYAxisMaxInput.setResponder(text -> this.manualYAxisMaxDraft = text);
                this.manualYAxisMaxInput.setHint(Component.literal("Y max"));
                this.addRenderableWidget(this.manualYAxisMaxInput);
                rowY += 18;
                this.addPanelButton(x, rowY, w, 16, Component.literal("Apply Y Range"), b -> this.applyManualYAxisInputs());
                rowY += 18;
                final PanelButton captureBtn = this.addPanelButton(x, rowY, w, 16, Component.literal("Use Visible Range"), b -> this.captureManualYAxisFromVisibleData());
                captureBtn.active = this.selectedTab >= 0;
                rowY += 18;
            }
        }
        if (allowPrediction) {
            this.addPanelButton(x, rowY, w, 16, Component.literal(this.predictionButtonLabel()), b -> this.togglePrediction());
            rowY += 18;
        }
        final PanelButton annotateBtn = this.addPanelButton(x, rowY, w, 16, Component.literal(this.annotateButtonLabel()), b -> this.toggleAnnotateMode());
        annotateBtn.active = this.selectedTab >= 0;
        rowY += 18;
        this.addPanelButton(x, rowY, w, 16, Component.literal("\u2398 Export Summary"), b -> this.exportToClipboard(false));
        rowY += 18;
        this.addPanelButton(x, rowY, w, 16, Component.literal("CSV Export"), b -> this.exportToClipboard(true));
    }

    private void addToolOptionWidgets(int x, int y, int w) {
        if (this.redstoneConfigOpen) {
            return;
        }
        int rowY = y;
        final PanelCapabilities.ChannelKind toolKind = this.focusedChannelKind(this.getFilteredChannels());
        final boolean allowThreshold = toolKind == null || PanelCapabilities.supportsThreshold(toolKind);
        final PanelButton redstoneBtn = this.addPanelButton(x, rowY, w, 16, Component.literal("\u26a1 Redstone Trigger"), b -> this.toggleRedstoneConfigPanel());
        redstoneBtn.active = this.selectedTab >= 0 && allowThreshold;
        rowY += 18;
        final boolean glowOn = ClientGraphPrefs.signalGlow();
        this.addPanelButton(x, rowY, w, 16, Component.literal("Signal Glow: " + (glowOn ? "ON" : "OFF")), b -> this.toggleSignalGlow());
        rowY += 18;
        if (!this.pinnedChannels.isEmpty()) {
            this.addPanelButton(x, rowY, w, 16, Component.literal("Clear Channel Pins"), b -> {
                this.pinnedChannels.clear();
                this.rebuildWidgets();
            });
        }
    }

    private void toggleSignalGlow() {
        ClientGraphPrefs.toggleSignalGlow();
        this.lineTraceSig = Long.MIN_VALUE;
        this.rebuildWidgets();
    }

    private void renderOptionPanel(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        if (!this.shouldShowChartControls() || this.optionPanelWidth <= 0 || this.optionPanelHeight <= 0) {
            return;
        }

        if (this.redstoneConfigOpen) {
            return;
        }
        final int x = this.optionPanelLeft;
        final int y = this.optionPanelTop;
        final int w = this.optionPanelWidth;
        final int h = this.optionPanelHeight;
        g.fill(x, y, x + w, y + h, this.GRAPH_BG);
        this.border(g, x, y, w, h, this.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, this.optionPanelAccent());
        g.drawString(this.font, this.selectedRightPanelTab.title, x + 6, y + 24, this.TEXT);

        final boolean srcInterrupted = !this.panel.isStatsDisplayLinkOptInRequired() && this.isCurrentViewInterrupted();
        final boolean srcLive = !channels.isEmpty() && !srcInterrupted;
        final String statusChip = srcInterrupted ? "STALE" : (srcLive ? "LIVE" : "WAIT");
        final int chipColor = srcInterrupted ? this.WARN : (srcLive ? 0xFF2FB36D : this.DIM);
        final int chipTextW = this.font.width(statusChip);
        final int chipTextX = x + w - chipTextW - 6;
        g.fill(chipTextX - 7, y + 25, chipTextX - 3, y + 29, chipColor);
        g.drawString(this.font, statusChip, chipTextX, y + 24, chipColor);
        final String subtitle = switch (this.selectedRightPanelTab) {
            case DATA -> (this.selectedTab >= 0 && this.selectedTab < channels.size()) ? this.truncate("Channel: " + channels.get(this.selectedTab).getLabel(), w - 12) : "Inspect the view and follow this page";
            case TOOLS -> "Quick alert and trigger rules";
        };
        g.drawString(this.font, this.truncate(subtitle, w - 12), x + 6, y + 34, this.DIM);
        final PanelCapabilities.SourceCapabilities panelCaps = this.currentSourceCapabilities(channels);
        final PanelCapabilities.ChannelKind panelKind = this.focusedChannelKind(channels);
        final String famLabel = panelCaps.getFamily() != null ? panelCaps.getFamily().getDisplayName() : "";
        final String kindLabel = panelKind != null ? panelKind.getLabel() : (panelCaps.isMultiMetric() ? "Multi-metric" : "");
        final String caption = famLabel.isEmpty() ? kindLabel
            : (kindLabel.isEmpty() ? famLabel : famLabel + "  \u00b7  " + kindLabel);
        if (!caption.isEmpty()) {
            g.drawString(this.font, this.truncate(caption, w - 12), x + 6, y + 44, this.ACCENT);
        }
        if (this.selectedRightPanelTab == RightPanelTab.DATA) {
            this.renderOptionPanelChannelList(g, channels, mx, my);
            g.drawString(this.font, this.truncate(this.getConfiguredPreferredSourceStatusLabel(), w - 12), x + 6, y + h - 24, this.DIM);
            g.drawString(this.font, this.truncate(this.getViewedSourceStatusLabel(), w - 12), x + 6, y + h - 14, this.DIM);
        } else if (this.selectedRightPanelTab == RightPanelTab.TOOLS) {
            this.renderTriggersSummary(g, channels);
        }
    }

    private void renderOptionPanelChannelList(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        if (this.channelListVisibleRows <= 0 || channels.isEmpty()) {
            return;
        }
        final int x = this.optionPanelLeft + 6;
        final int w = this.optionPanelWidth - 12;
        final int top = this.channelListTop;
        final int rowH = this.channelListRowH;
        final int regionH = this.channelListVisibleRows * rowH;
        final int maxScroll = Math.max(0, channels.size() - this.channelListVisibleRows);
        final int contentRight = x + w - (maxScroll > 0 ? 6 : 3);
        this.drawPanelSectionHeader(g, x, top - 11, w, "Channels");
        g.fill(x - 2, top - 2, x + w + 2, top + regionH + 2, darken(this.GRAPH_BG, 0.82f));
        this.border(g, x - 2, top - 2, w + 4, regionH + 4, darken(this.BORDER, 0.8f));
        g.enableScissor(x - 2, top, x + w + 2, top + regionH);
        for (int i = 0; i < this.channelListVisibleRows; ++i) {
            final int index = this.optionPanelChannelScroll + i;
            if (index < 0 || index >= channels.size()) {
                break;
            }
            final StatsDisplayPanelBE.DataChannel ch = channels.get(index);
            final int rowY = top + i * rowH;
            final boolean selected = index == this.selectedTab;
            final boolean hover = mx >= x && mx < x + w && my >= rowY && my < rowY + rowH;
            if (selected || hover) {
                g.fill(x, rowY, x + w, rowY + rowH - 1, selected ? this.TAB_SEL : this.TAB_HOVER);
            }
            final int color = StatsDisplayScreen.CHANNEL_COLORS[index % StatsDisplayScreen.CHANNEL_COLORS.length];
            g.fill(x + 2, rowY + rowH / 2 - 3, x + 5, rowY + rowH / 2 + 3, color);
            String val = "";
            final TimeSeriesStore store = ch.getStore();
            if (store != null && store.getSampleCount() > 0 && store.getMetricType() != null) {
                val = store.getMetricType().formatValue(store.getLatestValue());
            } else if (ch.getLastText() != null && !ch.getLastText().isEmpty()) {
                val = ch.getLastText();
            }
            int rowRight = contentRight;
            if (ch.isAlertEnabled()) {
                final int dotX = rowRight - 4;
                g.fill(dotX, rowY + rowH / 2 - 2, dotX + 4, rowY + rowH / 2 + 2, ch.isAlertTriggered() ? this.WARN : darken(this.WARN, 0.6f));
                rowRight = dotX - 3;
            }
            if (!val.isEmpty()) {
                final int valW = this.font.width(val);
                final int valX = rowRight - valW;
                g.drawString(this.font, val, valX, rowY + (rowH - 8) / 2, selected ? this.TEXT : this.DIM);
                rowRight = valX - 4;
            }
            final int nameW = Math.max(16, rowRight - (x + 9));
            g.drawString(this.font, this.truncate(ch.getLabel(), nameW), x + 9, rowY + (rowH - 8) / 2, selected ? this.TEXT : this.DIM);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            final int trackX = x + w - 1;
            final int thumbH = Math.max(6, regionH * this.channelListVisibleRows / channels.size());
            final int thumbY = top + (regionH - thumbH) * this.optionPanelChannelScroll / maxScroll;
            g.fill(trackX, top, trackX + 2, top + regionH, darken(this.GRAPH_BG, 0.6f));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, this.ACCENT);
        }
    }

    private boolean handleOptionPanelChannelListClick(double mx, double my) {
        if (!this.shouldShowChartControls() || this.optionPanelWidth <= 0) {
            return false;
        }
        if (this.selectedRightPanelTab != RightPanelTab.DATA || this.channelListVisibleRows <= 0) {
            return false;
        }
        if (this.isSpecializedFlowMeterView()) {
            return false;
        }
        final int x = this.optionPanelLeft + 6;
        final int w = this.optionPanelWidth - 12;
        final int top = this.channelListTop;
        final int regionH = this.channelListVisibleRows * this.channelListRowH;
        if (mx < x - 2 || mx > x + w + 2 || my < top || my >= top + regionH) {
            return false;
        }
        final int row = (int) ((my - top) / this.channelListRowH);
        final int index = this.optionPanelChannelScroll + row;
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (index < 0 || index >= channels.size()) {
            return false;
        }
        this.selectChannelFromPicker(index);
        return true;
    }

    private boolean handleOptionPanelChannelListScroll(double mx, double my, double scrollY) {
        if (!this.shouldShowChartControls() || this.optionPanelWidth <= 0) {
            return false;
        }
        if (this.selectedRightPanelTab != RightPanelTab.DATA || this.channelListVisibleRows <= 0) {
            return false;
        }
        final int x = this.optionPanelLeft + 6;
        final int w = this.optionPanelWidth - 12;
        final int top = this.channelListTop;
        final int regionH = this.channelListVisibleRows * this.channelListRowH;
        if (mx < x - 2 || mx > x + w + 2 || my < top || my >= top + regionH) {
            return false;
        }
        final int maxScroll = Math.max(0, this.getFilteredChannels().size() - this.channelListVisibleRows);
        if (maxScroll <= 0) {
            return false;
        }
        this.optionPanelChannelScroll = Math.max(0, Math.min(maxScroll, this.optionPanelChannelScroll - (int) Math.signum(scrollY)));
        return true;
    }

    private void renderTriggersSummary(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (this.redstoneConfigOpen) {
            return;
        }
        final int x = this.optionPanelLeft + 6;
        final int w = this.optionPanelWidth - 12;
        final int y = this.optionPanelTop + 54;
        if (this.isStockpileSourceView()) {
            this.drawPanelSectionHeader(g, x, y, w, "Stockpile Thresholds");
            return;
        }
        if (this.selectedTab < 0 || this.selectedTab >= channels.size()) {
            g.drawString(this.font, this.truncate("Select a channel to arm triggers", w), x, y, this.DIM);
            return;
        }
        final StatsDisplayPanelBE.DataChannel ch = channels.get(this.selectedTab);
        final boolean hasType = ch.getStore() != null && ch.getStore().getMetricType() != null;
        final String alertStr;
        final int alertColor;
        if (ch.isAlertEnabled()) {
            final String thr = hasType ? ch.getStore().getMetricType().formatValue(ch.getAlertThreshold()) : String.valueOf(ch.getAlertThreshold());
            alertStr = "Alert  " + (ch.isAlertAbove() ? "\u2265 " : "\u2264 ") + thr;
            alertColor = 0xFF2FB36D;
        } else {
            alertStr = "Alert  off";
            alertColor = this.DIM;
        }
        g.drawString(this.font, this.truncate(alertStr, w), x, y, alertColor);
        final String rsStr;
        final int rsColor;
        if (this.panel.isRedstoneEnabled() && !this.panel.getRedstoneChannelId().isEmpty()) {
            final boolean boundHere = this.panel.getRedstoneChannelId().equals(ch.getId());
            final String modeStr = this.panel.getRedstoneMode() == 1 ? "prop" : "on";
            if (boundHere) {
                final String rthr = hasType ? ch.getStore().getMetricType().formatValue(this.panel.getRedstoneThreshold()) : String.valueOf(this.panel.getRedstoneThreshold());
                rsStr = "Redstone  " + modeStr + " " + (this.panel.isRedstoneAbove() ? "\u2265 " : "\u2264 ") + rthr;
            } else {
                rsStr = "Redstone  on: " + this.channelLabel(this.panel.getRedstoneChannelId());
            }
            rsColor = 0xFFE3A43B;
        } else {
            rsStr = "Redstone  off";
            rsColor = this.DIM;
        }
        g.drawString(this.font, this.truncate(rsStr, w), x, y + 12, rsColor);
    }

    private boolean isStockpileSourceView() {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) {
            return false;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (config == null) {
            return false;
        }
        return "factory.stockpile_switch".equals(
            CustomScreenSourceFamilyCatalog.familyForSourceType(config.getSourceType()).getFamilyId());
    }

    private ThresholdSwitchBlockEntity getLinkedThresholdSwitch() {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) {
            return null;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (config == null || config.getSourcePos() == null) {
            return null;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(config.getSourcePos());
        return be instanceof ThresholdSwitchBlockEntity tsw ? tsw : null;
    }

    private void loadStockpileThresholdState() {
        final String sourceId = this.getCurrentSourcePageId();
        if (java.util.Objects.equals(this.stockpileLoadedSourceId, sourceId)) {
            return;
        }
        this.stockpileLoadedSourceId = sourceId;
        final ThresholdSwitchBlockEntity tsw = this.getLinkedThresholdSwitch();
        this.stockpileBelowDraft = String.valueOf(tsw != null ? tsw.offWhenBelow : 0);
        this.stockpileAboveDraft = String.valueOf(tsw != null ? tsw.onWhenAbove : 0);
    }

    private int parseStockpileAmount(String draft) {
        if (draft != null && draft.matches("[0-9]+")) {
            try {
                return Integer.parseInt(draft);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private void applyStockpileThresholds() {
        final ThresholdSwitchBlockEntity tsw = this.getLinkedThresholdSwitch();
        if (tsw == null) {
            return;
        }
        final int below = Math.max(0, this.parseStockpileAmount(this.stockpileBelowDraft));
        final int above = Math.max(0, this.parseStockpileAmount(this.stockpileAboveDraft));
        PacketDistributor.sendToServer(new StockpileThresholdPacket(tsw.getBlockPos(), below, above));
    }

    private void addStockpileThresholdWidgets(int x, int baseY, int w) {
        final int halfW = Math.max(40, (w - 4) / 2);
        final int inY = baseY + 12;
        this.stockpileBelowInput = new EditBox(this.font, x, inY, halfW, 16, Component.literal("Off below"));
        this.stockpileBelowInput.setFilter(t -> t.isEmpty() || t.matches("[0-9]*"));
        this.stockpileBelowInput.setValue(this.stockpileBelowDraft);
        this.stockpileBelowInput.setResponder(t -> this.stockpileBelowDraft = t);
        this.stockpileBelowInput.setHint(Component.literal("Off below"));
        this.addRenderableWidget(this.stockpileBelowInput);
        this.stockpileAboveInput = new EditBox(this.font, x + halfW + 4, inY, halfW, 16, Component.literal("On above"));
        this.stockpileAboveInput.setFilter(t -> t.isEmpty() || t.matches("[0-9]*"));
        this.stockpileAboveInput.setValue(this.stockpileAboveDraft);
        this.stockpileAboveInput.setResponder(t -> this.stockpileAboveDraft = t);
        this.stockpileAboveInput.setHint(Component.literal("On above"));
        this.addRenderableWidget(this.stockpileAboveInput);
        this.addPanelButton(x, inY + 20, w, 16, Component.literal("Apply Thresholds"), b -> this.applyStockpileThresholds());
    }

    private void renderChannelRowTooltip(GuiGraphics g, int mx, int my) {
        if (!this.shouldShowChartControls() || this.channelListVisibleRows <= 0) {
            return;
        }
        if (this.selectedRightPanelTab != RightPanelTab.DATA || this.redstoneConfigOpen) {
            return;
        }
        if (this.sourceDropdownOpen || this.signalDropdownOpen) {
            return;
        }
        final int x = this.optionPanelLeft + 6;
        final int w = this.optionPanelWidth - 12;
        final int top = this.channelListTop;
        final int regionH = this.channelListVisibleRows * this.channelListRowH;
        if (mx < x || mx > x + w || my < top || my >= top + regionH) {
            return;
        }
        final int row = (int) ((my - top) / this.channelListRowH);
        final int index = this.optionPanelChannelScroll + row;
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (index < 0 || index >= channels.size()) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel ch = channels.get(index);
        final PanelCapabilities.ChannelKind kind = PanelCapabilities.classify(ch);
        final TimeSeriesStore store = ch.getStore();
        final boolean hasType = store != null && store.getMetricType() != null;
        final List<Component> lines = new ArrayList<Component>();
        lines.add(Component.literal(ch.getLabel()).withStyle(s -> s.withColor(this.ACCENT)));
        lines.add(Component.literal("Type: " + kind.getLabel() + (hasType ? "  (" + store.getMetricType().getDisplayName() + ")" : ""))
                .withStyle(s -> s.withColor(11184810)));
        if (store != null && store.getSampleCount() > 0 && hasType) {
            lines.add(Component.literal("Now: " + store.getMetricType().formatValue(store.getLatestValue()))
                    .withStyle(s -> s.withColor(this.TEXT & 0xFFFFFF)));
        } else if (ch.getLastText() != null && !ch.getLastText().isEmpty()) {
            lines.add(Component.literal("Text: " + this.truncate(ch.getLastText(), 140)).withStyle(s -> s.withColor(this.TEXT & 0xFFFFFF)));
        } else {
            lines.add(Component.literal("Waiting for data").withStyle(s -> s.withColor(8947848)));
        }
        if (ch.isAlertEnabled()) {
            final String thr = hasType ? store.getMetricType().formatValue(ch.getAlertThreshold()) : String.valueOf(ch.getAlertThreshold());
            lines.add(Component.literal((ch.isAlertTriggered() ? "\u26a0 Alert tripped " : "Alert armed ") + (ch.isAlertAbove() ? "\u2265 " : "\u2264 ") + thr)
                    .withStyle(s -> s.withColor(ch.isAlertTriggered() ? 0xE3A43B : 0x7F9F7F)));
        }
        lines.add(Component.literal("Click to inspect this signal").withStyle(s -> s.withColor(8947848)));
        g.renderTooltip(this.font, (List) lines, (Optional) Optional.empty(), mx, my);
    }

    private int optionPanelAccent() {
        return switch (this.selectedRightPanelTab) {
            case DATA -> this.TEXT;
            case TOOLS -> this.WARN;
        };
    }

    private PanelCapabilities.ChannelKind focusedChannelKind(List<StatsDisplayPanelBE.DataChannel> channels) {
        if (this.selectedTab >= 0 && channels != null && this.selectedTab < channels.size()) {
            return PanelCapabilities.classify(channels.get(this.selectedTab));
        }
        return null;
    }

    private PanelCapabilities.SourceCapabilities currentSourceCapabilities(List<StatsDisplayPanelBE.DataChannel> channels) {
        SourceFamilyDefinition family = null;
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId != null && !sourceId.isEmpty()) {
            final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
            if (config != null) {
                family = CustomScreenSourceFamilyCatalog.familyForSourceType(config.getSourceType());
            }
        }
        return PanelCapabilities.resolve(family, channels);
    }

    private boolean isSpecializedFlowMeterView() {
        if (this.selectedTab != -1) {
            return false;
        }
        if (this.panel.getSettings().getDisplayMode() != StatsDisplaySettings.DisplayMode.SPECIALIZED) {
            return false;
        }
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) {
            return false;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (config == null) {
            return false;
        }
        return "fluid.flow_meter".equals(
            CustomScreenSourceFamilyCatalog.familyForSourceType(config.getSourceType()).getFamilyId());
    }

    private FlowMeterBE getLinkedFlowMeter() {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) {
            return null;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (config == null || config.getSourcePos() == null) {
            return null;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(config.getSourcePos());
        return be instanceof FlowMeterBE meter ? meter : null;
    }

    private StatsDisplayPanelBE.DataChannel getFlowRateChannel() {
        for (final StatsDisplayPanelBE.DataChannel ch : this.getFilteredChannels()) {
            final String id = ch.getId();
            if (id != null && id.endsWith("_auto_flow_rate_mbt")) {
                return ch;
            }
        }
        return null;
    }

    private void loadFlowMeterTriggerState() {
        final String sourceId = this.getCurrentSourcePageId();
        if (java.util.Objects.equals(this.fmTriggerLoadedSourceId, sourceId)) {
            return;
        }
        this.fmTriggerLoadedSourceId = sourceId;
        final FlowMeterBE meter = this.getLinkedFlowMeter();
        final StatsDisplayPanelBE.DataChannel rateCh = this.getFlowRateChannel();
        this.fmTriggerRedstone = meter != null && meter.isTriggerEnabled();
        this.fmTriggerMode = (meter != null && meter.getTriggerMode() == 1) ? 1 : 0;
        this.fmTriggerAbove = meter == null || meter.isTriggerAbove();
        this.fmTriggerThresholdDraft = String.valueOf(meter != null ? meter.getTriggerThresholdMb() : 0);
        this.fmTriggerAlarm = rateCh != null && rateCh.isAlertEnabled();
    }

    private int fmThresholdValue() {
        if (this.fmTriggerThresholdDraft != null && this.fmTriggerThresholdDraft.matches("[0-9]+")) {
            try {
                return Integer.parseInt(this.fmTriggerThresholdDraft);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private void sendFlowMeterPipe() {
        final FlowMeterBE meter = this.getLinkedFlowMeter();
        if (meter != null) {
            PacketDistributor.sendToServer(new FlowMeterTriggerPacket(
                meter.getBlockPos(), this.fmTriggerRedstone,
                this.fmTriggerMode == 1 ? 1 : 0, Math.max(0, this.fmThresholdValue()), this.fmTriggerAbove));
        }
    }

    private void sendFlowMeterPanelRedstone() {
        final StatsDisplayPanelBE.DataChannel rateCh = this.getFlowRateChannel();
        if (rateCh == null) {
            return;
        }
        final boolean flowing = this.fmTriggerMode != 1;
        final float thr = flowing ? 1.0f : (float) Math.max(0, this.fmThresholdValue());
        final boolean abv = flowing || this.fmTriggerAbove;
        this.panel.setRedstoneChannelId(rateCh.getId());
        this.panel.setRedstoneMode(0);
        this.panel.setRedstoneThreshold(thr);
        this.panel.setRedstoneAbove(abv);
        this.panel.setRedstoneEnabled(this.fmTriggerRedstone);
        this.sendRedstonePacket();
    }

    private void sendFlowMeterAlarm() {
        final StatsDisplayPanelBE.DataChannel rateCh = this.getFlowRateChannel();
        if (rateCh == null) {
            return;
        }
        final boolean flowing = this.fmTriggerMode != 1;
        final float thr = flowing ? 1.0f : (float) Math.max(0, this.fmThresholdValue());
        final boolean abv = flowing || this.fmTriggerAbove;
        rateCh.setAlertThreshold(thr);
        rateCh.setAlertAbove(abv);
        rateCh.setAlertEnabled(this.fmTriggerAlarm);
        this.sendAlertPacket(rateCh);
    }

    private void onFlowMeterConditionChanged() {
        if (this.fmTriggerRedstone) {
            this.sendFlowMeterPipe();
            this.sendFlowMeterPanelRedstone();
        }
        if (this.fmTriggerAlarm) {
            this.sendFlowMeterAlarm();
        }
    }

    private void adjustFmThreshold(boolean up) {
        final int cur = Math.max(0, this.fmThresholdValue() + (up ? 1 : -1));
        this.fmTriggerThresholdDraft = String.valueOf(cur);
        if (this.fmTriggerThresholdInput != null) {
            this.fmTriggerThresholdInput.setValue(this.fmTriggerThresholdDraft);
        }
        this.onFlowMeterConditionChanged();
    }

    private void addFlowMeterTriggerWidgets() {
        this.fmTriggerThresholdInput = null;
        this.loadFlowMeterTriggerState();
        final int apx = this.optionPanelLeft;
        final int apy = this.optionPanelTop;
        final int apw = this.optionPanelWidth;
        final boolean amount = (this.fmTriggerMode == 1);

        final int leftX = apx + 6;
        final int fullW = apw - 12;
        final int halfW = (fullW - 4) / 2;
        int rowY = apy + 48;

        final Button flowingBtn = Button.builder(Component.literal("Flowing"), b -> {
            this.fmTriggerMode = 0;
            this.onFlowMeterConditionChanged();
            this.rebuildWidgets();
        }).bounds(leftX, rowY, halfW, 16).build();
        flowingBtn.active = amount;
        this.addRenderableWidget(flowingBtn);
        final Button amountBtn = Button.builder(Component.literal("Amount"), b -> {
            this.fmTriggerMode = 1;
            this.onFlowMeterConditionChanged();
            this.rebuildWidgets();
        }).bounds(leftX + halfW + 4, rowY, halfW, 16).build();
        amountBtn.active = !amount;
        this.addRenderableWidget(amountBtn);
        rowY += 22;

        if (amount) {
            (this.fmTriggerThresholdInput = new EditBox(this.font, leftX, rowY, halfW, 16, Component.literal("mB/t")))
                .setFilter(text -> text.isEmpty() || text.matches("[0-9]*"));
            this.fmTriggerThresholdInput.setValue(this.fmTriggerThresholdDraft);
            this.fmTriggerThresholdInput.setResponder(text -> {
                this.fmTriggerThresholdDraft = text;
                this.onFlowMeterConditionChanged();
            });
            this.addRenderableWidget(this.fmTriggerThresholdInput);
            this.addRenderableWidget(Button.builder(Component.literal("\u25b2"), b -> this.adjustFmThreshold(true))
                .bounds(leftX + halfW + 4, rowY, 18, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("\u25bc"), b -> this.adjustFmThreshold(false))
                .bounds(leftX + halfW + 24, rowY, 18, 16).build());
            rowY += 22;

            final Button aboveBtn = Button.builder(Component.literal("Above \u2191"), b -> {
                this.fmTriggerAbove = true;
                this.onFlowMeterConditionChanged();
                this.rebuildWidgets();
            }).bounds(leftX, rowY, halfW, 16).build();
            aboveBtn.active = !this.fmTriggerAbove;
            this.addRenderableWidget(aboveBtn);
            final Button belowBtn = Button.builder(Component.literal("Below \u2193"), b -> {
                this.fmTriggerAbove = false;
                this.onFlowMeterConditionChanged();
                this.rebuildWidgets();
            }).bounds(leftX + halfW + 4, rowY, halfW, 16).build();
            belowBtn.active = this.fmTriggerAbove;
            this.addRenderableWidget(belowBtn);
            rowY += 24;
        } else {
            rowY += 4;
        }

        this.addRenderableWidget(Button.builder(
            Component.literal(this.fmTriggerRedstone ? "\u26a1 Redstone: On" : "\u26a1 Redstone: Off"), b -> {
                this.fmTriggerRedstone = !this.fmTriggerRedstone;
                this.sendFlowMeterPipe();
                this.sendFlowMeterPanelRedstone();
                this.rebuildWidgets();
            }).bounds(leftX, rowY, fullW, 16).build());
        rowY += 20;
        this.addRenderableWidget(Button.builder(
            Component.literal(this.fmTriggerAlarm ? "\u26a0 Alarm: On" : "\u26a0 Alarm: Off"), b -> {
                this.fmTriggerAlarm = !this.fmTriggerAlarm;
                this.sendFlowMeterAlarm();
                this.rebuildWidgets();
            }).bounds(leftX, rowY, fullW, 16).build());
    }

    private void renderFlowMeterTriggerPanel(GuiGraphics g) {
        if (this.optionPanelWidth <= 0 || this.optionPanelHeight <= 0) {
            return;
        }
        this.loadFlowMeterTriggerState();
        final int x = this.optionPanelLeft;
        final int y = this.optionPanelTop;
        final int w = this.optionPanelWidth;
        final int h = this.optionPanelHeight;
        g.fill(x, y, x + w, y + h, this.GRAPH_BG);
        this.border(g, x, y, w, h, this.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, this.WARN);
        g.drawString(this.font, "Alarm & Redstone", x + 6, y + 24, this.TEXT);
        g.drawString(this.font, this.truncate(this.fmTriggerMode == 1 ? "Fire on flow amount" : "Fire while flowing", w - 12), x + 6, y + 34, this.DIM);
        final FlowMeterBE meter = this.getLinkedFlowMeter();
        if (meter != null) {
            final int rate = meter.getFlowRateMbPerTick();
            final boolean firing = meter.getEmittedRedstone() > 0;
            g.drawString(this.font, this.truncate("Flow now: " + rate + " mB/t", w - 12), x + 6, y + h - 26, this.DIM);
            g.drawString(this.font, firing ? "Signal: ON" : "Signal: off", x + 6, y + h - 14, firing ? 0xFF40D080 : this.DIM);
        } else {
            g.drawString(this.font, this.truncate("Flow meter not loaded", w - 12), x + 6, y + h - 14, this.DIM);
        }
    }

    private void toggleRedstoneConfigPanel() {
        this.selectedRightPanelTab = RightPanelTab.TOOLS;
        if (this.redstoneConfigOpen) {
            this.applyRedstoneThreshold();
        }
        this.redstoneConfigOpen = !this.redstoneConfigOpen;
        this.redstoneValueInput = "";
        this.rebuildWidgets();
    }

    private String getConfiguredPreferredSourceId() {
        final String preferred = this.panel.getSettings().getPreferredSourceId();
        if (preferred != null && !preferred.isEmpty() && this.panel.getSourceRegistry().containsKey(preferred)) {
            return preferred;
        }
        return "";
    }

    private void setConfiguredPreferredSourceId(String sourceId) {
        final String normalized = (sourceId != null && !sourceId.isEmpty() && this.panel.getSourceRegistry().containsKey(sourceId)) ? sourceId : "";
        if (normalized.equals(this.panel.getSettings().getPreferredSourceId())) {
            return;
        }
        this.panel.getSettings().setPreferredSourceId(normalized);
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private String getConfiguredPreferredSourceStatusLabel() {
        final String preferred = this.getConfiguredPreferredSourceId();
        if (preferred.isEmpty()) {
            return "Cycle focus: All visible sources";
        }
        return "Cycle focus: " + this.sourceLabel(preferred);
    }

    private String getCurrentSourcePageKey() {
        if (this.isNormalSourcePage() && this.selectedSource >= 0 && this.selectedSource < this.sourceIds.size()) {
            return this.sourceIds.get(this.selectedSource);
        }
        return null;
    }

    private String getCurrentSourcePageId() {
        final String sourcePageKey = this.getCurrentSourcePageKey();
        if (sourcePageKey == null || this.isGroupedSourcePage(sourcePageKey)) {
            return null;
        }
        return sourcePageKey;
    }

    private String getViewedSourceStatusLabel() {
        final String viewed = this.getCurrentSourcePageKey();
        if (viewed != null) {
            return "Viewing now: " + this.sourceLabel(viewed);
        }
        return "Viewing now: " + this.getCurrentPageTitle();
    }

    private String getCurrentPageTitle() {
        if (this.isHomePage()) {
            return "Home";
        }
        if (this.isVehicleSettingsPage()) {
            return "Vehicle Settings";
        }
        if (this.isFlightDeckPage()) {
            return "Flight Deck";
        }
        if (this.isGuidancePage()) {
            return "Aeroline Director";
        }
        if (this.isFactoryPage()) {
            return "Factory Controls";
        }
        if (this.isInventoryStatusPage()) {
            return "Inventory Status";
        }
        if (this.isFactoryStatusPage()) {
            return "Factory Status";
        }
        if (this.isPowerPage()) {
            return "StatsPower";
        }
        if (this.isTrainPage()) {
            return "Train Map";
        }
        if (this.viewType == StatsDisplaySettings.GraphType.TOPOLOGY) {
            return "Network Topology";
        }
        if (this.viewType == StatsDisplaySettings.GraphType.EFFICIENCY) {
            return "Efficiency Analysis";
        }
        final String sourcePageKey = this.getCurrentSourcePageKey();
        if (sourcePageKey != null) {
            return this.sourceLabel(sourcePageKey);
        }
        final String sourceType = this.panel.getNoDataSourceType();
        return sourceType.isEmpty() ? "Stats Display" : sourceType;
    }

    private boolean isCurrentViewInterrupted() {
        final String viewed = this.getCurrentSourcePageKey();
        if (viewed != null) {
            return this.isSourcePageInterrupted(viewed);
        }
        if (this.isFlightDeckPage()) {
            return this.isFlightDeckInterrupted();
        }
        if (this.isVehicleSettingsPage()) {
            return this.isFlightDeckInterrupted() || this.isGuidanceInterrupted();
        }
        return this.isGuidancePage() && this.isGuidanceInterrupted();
    }

    private boolean currentViewHasSnapshot() {
        final String viewed = this.getCurrentSourcePageKey();
        if (viewed != null) {
            return this.hasSourcePageSnapshot(viewed);
        }
        if (this.isFlightDeckPage()) {
            return this.hasFlightDeckSnapshot();
        }
        if (this.isVehicleSettingsPage()) {
            return this.hasFlightDeckSnapshot() || this.hasGuidanceSnapshot();
        }
        return this.isGuidancePage() && this.hasGuidanceSnapshot();
    }

    private String buildPanelStatusLabel(boolean isLive, boolean interrupted) {
        if (com.createstats.power.client.FePowerVisuals.isDead(this.panel.feConsumer())) {
            return "● NO POWER - display offline";
        }
        if (com.createstats.power.client.FePowerVisuals.isBooting(this.panel.getBlockPos(), this.panel.feConsumer())) {
            return "● Powering up...";
        }
        if (this.panel.feConsumer().isBrownout()) {
            return "● LOW POWER - display unstable";
        }
        final String hintSourceType = this.panel.getStatsDisplayLinkHintSourceType();
        if (this.panel.isStatsDisplayLinkOptInRequired()) {
            return hintSourceType.isEmpty()
                    ? "\u25cf Setup needed - select Stats Link on the source"
                    : "\u25cf Setup needed - select Stats Link on " + hintSourceType;
        }
        if (interrupted) {
            return this.currentViewHasSnapshot()
                    ? "\u25cf Live lost - showing snapshot"
                    : "\u25cf Live lost - reconnecting";
        }
        if (isLive) {
            return "\u25cf Receiving live data";
        }
        if (!this.panel.getSourceRegistry().isEmpty()) {
            return "\u25cf Waiting for first live sample";
        }
        return "\u25cf No source linked";
    }

    private String sourceLabel(String sourceId) {
        if (PRODUCTION_DETECTOR_GROUP_PAGE.equals(sourceId)) {
            return (this.getSourceIdsForPage(sourceId).size() > 1) ? "Production Detectors" : "Production Detector";
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        return (config != null) ? config.getDisplayLabel() : sourceId;
    }

    private String channelLabel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return "None";
        }
        for (StatsDisplayPanelBE.DataChannel channel : this.panel.getActiveChannels()) {
            if (channelId.equals(channel.getId())) {
                return channel.getLabel();
            }
        }
        return channelId;
    }

    private void closeInlineAutomationEditors() {
        if (this.redstoneConfigOpen) {
            this.applyRedstoneThreshold();
            this.redstoneConfigOpen = false;
            this.redstoneValueInput = "";
        }
    }

    private boolean resetPageScopedSelection() {
        final boolean changed = this.selectedTab != -1 || !this.pinnedChannels.isEmpty() || this.redstoneConfigOpen;
        this.selectedTab = -1;
        this.pinnedChannels.clear();
        this.closeInlineAutomationEditors();
        return changed;
    }

    private boolean sanitizeChannelSelection(List<StatsDisplayPanelBE.DataChannel> channels) {
        boolean changed = this.pinnedChannels.removeIf(pinIdx -> pinIdx < 0 || pinIdx >= channels.size());
        final boolean editorsWereOpen = this.redstoneConfigOpen;
        if (this.selectedTab >= channels.size()) {
            this.selectedTab = -1;
            changed = true;
        }
        if (this.selectedTab < 0) {
            this.closeInlineAutomationEditors();
            if (editorsWereOpen) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean isSelectedChannelHidden() {
        final List<StatsDisplayPanelBE.DataChannel> allChannels = this.getAllFilteredChannels();
        if (this.selectedTab < 0 || this.selectedTab >= allChannels.size()) {
            return false;
        }
        return this.panel.getSettings().isChannelHidden(allChannels.get(this.selectedTab).getId());
    }

    private void toggleSelectedChannelVisibility() {
        final List<StatsDisplayPanelBE.DataChannel> allChannels = this.getAllFilteredChannels();
        if (this.selectedTab < 0 || this.selectedTab >= allChannels.size()) {
            return;
        }
        this.panel.getSettings().toggleChannelVisibility(allChannels.get(this.selectedTab).getId());
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.panel.feConsumer());
        final LinkedHashMap<String, SourceConfig> registry = this.panel.getSourceRegistry();
        if (this.syncSourceIds()) {
            this.validateGuiPageSelection();
            this.rebuildWidgets();
        }
        if (this.panel.hasTrainConnection() != this.lastKnownTrainConnection) {
            this.lastKnownTrainConnection = this.panel.hasTrainConnection();
            this.rebuildWidgets();
        }
        final TrainMapData trainSnapshot = this.panel.getTrainMapData();
        if (trainSnapshot != null) {
            this.updateTrainDashboardSnapshot(trainSnapshot);
        }
        if (this.validateGuiPageSelection()) {
            this.rebuildWidgets();
        }
        final boolean multiSource = this.getVisibleSourceTabCount() > 1;
        final boolean chartContext = this.shouldShowChartControls();
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        final int controlLinkCount = this.panel.getSafetyClutchLinks().size()
                + this.panel.getAutoTransmissionClutchLinks().size()
                + this.panel.getLoadBalancerLinks().size()
                + this.panel.getMiniDisplayLinks().size()
                + this.panel.getFreightManifestLinks().size()
                + this.panel.getTrainStationLinks().size();
        final boolean hasControlLinks = controlLinkCount > 0;
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, this.BG);
        this.border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, this.BORDER);
        g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 2, 869576503);
        final String title = this.getCurrentPageTitle();
        g.drawString(this.font, title, this.panelLeft + 10, this.panelTop + 7, this.BORDER);
        final boolean trainLive = trainSnapshot != null && !trainSnapshot.isEmpty();
        final boolean isLive;
        if (this.isTrainPage()) {
            isLive = trainLive;
        }
        else if (this.viewType == StatsDisplaySettings.GraphType.TOPOLOGY) {
            isLive = this.hasTopologyGraphData();
        }
        else if (this.viewType == StatsDisplaySettings.GraphType.EFFICIENCY) {
            isLive = this.hasEfficiencyData();
        }
        else {
            isLive = !channels.isEmpty()
                    || hasControlLinks
                    || (this.isNormalSourcePage() && SpecializedPanelRenderer.canRenderSourceWithoutChannels(this.panel, this.getCurrentSourcePageId()))
                    || (this.isHomePage() && trainLive);
        }
        final boolean interrupted = !this.panel.isStatsDisplayLinkOptInRequired() && this.isCurrentViewInterrupted();
        final String status = this.truncate(this.buildPanelStatusLabel(isLive, interrupted), Math.max(140, this.panelWidth - 190));
        final boolean feDead = com.createstats.power.client.FePowerVisuals.isDead(this.panel.feConsumer());
        final boolean feLow = !feDead && this.panel.feConsumer().isBrownout();
        final int statusColor = feDead ? 0xFFFF4D4D
                : feLow ? 0xFFFFC24B
                : (this.panel.isStatsDisplayLinkOptInRequired() ? this.WARN : (interrupted ? this.WARN : (isLive ? -16724890 : this.DIM)));
        g.drawString(this.font, status, this.panelLeft + this.panelWidth - this.font.width(status) - 74, this.panelTop + 7, statusColor);
        if (this.panel.getScanSettings().getScanMode() == ScanSettings.ScanMode.AUTO) {
            final long activeCount = registry.values().stream().filter(SourceConfig::isActive).count();
            final String scanStatus = "\ud83d\udd0d " + activeCount + " sources" + (hasControlLinks ? ("  |  " + controlLinkCount + " controls") : "");
            g.drawString(this.font, scanStatus, this.panelLeft + 8, this.panelTop + this.panelHeight - 46, this.DIM);
        }
        if (multiSource) {
            this.renderSourceBar(g, registry, mouseX, mouseY);
        }
        if (chartContext) {
            this.renderTabBar(g, channels, mouseX, mouseY);
        }
        g.fill(this.graphLeft, this.graphTop, this.graphLeft + this.graphWidth, this.graphTop + this.graphHeight, this.GRAPH_BG);
        this.border(g, this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight, -14013884);
        if (chartContext) {
            for (int i = 1; i < 4; ++i) {
                final int gx = this.graphLeft + this.graphWidth * i / 4;
                final int gy = this.graphTop + this.graphHeight * i / 4;
                this.vline(g, gx, this.graphTop + 1, this.graphTop + this.graphHeight - 1, this.GRID);
                this.hline(g, this.graphLeft + 1, this.graphLeft + this.graphWidth - 1, gy, this.GRID);
            }
        }
        this.hoverDataIndex = -1;
        this.battleBoardClickTargets.clear();
        final StatsDisplaySettings.DisplayMode displayMode = this.panel.getSettings().getDisplayMode();
        if (this.isHomePage()) {
            this.renderHomePage(g, registry, mouseX, mouseY);
        }
        else if (this.isVehicleSettingsPage()) {
            this.renderVehicleSettingsPage(g, mouseX, mouseY);
        }
        else if (this.isFactoryPage()) {
            this.renderFactoryControlsPage(g, mouseX, mouseY);
        }
        else if (this.isFactoryStatusPage()) {
            this.renderFactoryStatus(g, channels, mouseX, mouseY);
        }
        else if (this.isInventoryStatusPage()) {
            this.renderInventoryStatus(g, channels, mouseX, mouseY);
        }
        else if (this.isPowerPage()) {
            this.renderPowerPage(g, mouseX, mouseY, partialTick);
        }
        else if (this.isTrainPage()) {
            this.renderTrainDashboard(g, mouseX, mouseY);
        }
        else if (this.isFlightDeckPage() && this.selectedTab == -1 && SpecializedPanelRenderer.renderAeroFlightDeckPanelGUI(g, this.font, this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight, this.panel, channels, this.getGuiRenderTick())) {
        }
        else if (this.isGuidancePage() && this.selectedTab == -1 && SpecializedPanelRenderer.renderAerolineDirectorPanelGUI(g, this.font, this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight, this.panel, channels, this.getGuiRenderTick(), partialTick)) {
        }
        else if (!this.isFlightDeckPage() && this.viewType == StatsDisplaySettings.GraphType.EFFICIENCY) {
            this.renderEfficiencyGUI(g);
        }
        else if (!this.isFlightDeckPage() && this.viewType == StatsDisplaySettings.GraphType.TOPOLOGY) {
            this.renderTopologyGUI(g);
        }
        else if (displayMode == StatsDisplaySettings.DisplayMode.SPECIALIZED
            && this.selectedTab == -1
            && this.tryRenderBattleTacticalV2Viewport3D(g, partialTick)) {
        }
        else if (displayMode == StatsDisplaySettings.DisplayMode.SPECIALIZED
            && this.selectedTab == -1
            && (!channels.isEmpty() || SpecializedPanelRenderer.canRenderSourceWithoutChannels(this.panel, this.getCurrentSourcePageId()))
            && SpecializedPanelRenderer.renderStandardSpecializedPanelGUI(g, this.font, this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight, this.panel, channels, this.getCurrentSourcePageId(), this.getGuiRenderTick())) {
            this.renderBattleBoardPresetOverlay(g, mouseX, mouseY);
        }
        else if (channels.isEmpty()) {
            if (this.panel.isStatsDisplayLinkOptInRequired()) {
                final String hintSourceType = this.panel.getStatsDisplayLinkHintSourceType();
                g.drawCenteredString(this.font, hintSourceType.isEmpty() ? "Source setup needed" : (hintSourceType + " detected"), this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
                g.drawCenteredString(this.font, "Open the source block and select Stats Link", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
                g.drawCenteredString(this.font, "The panel will bind live data after the first update", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 16, -12303258);
                g.drawCenteredString(this.font, "Use Home to choose sources and channels once it appears", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 28, -12303258);
            }
            else if (interrupted) {
                final String emptyTitle = this.getCurrentPageTitle();
                g.drawCenteredString(this.font, emptyTitle, this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
                g.drawCenteredString(this.font, "Live link interrupted", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, this.WARN);
                g.drawCenteredString(this.font, this.currentViewHasSnapshot() ? "Showing the last cached snapshot" : "Waiting for a fresh live update", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 16, -12303258);
                g.drawCenteredString(this.font, "Reconnect the Display Link or reopen the source mode", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 28, -12303258);
            }
            else {
                g.drawCenteredString(this.font, "Connect a Display Link from a supported source", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
                g.drawCenteredString(this.font, "If the source supports Stats Link, select that mode on the source", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
                g.drawCenteredString(this.font, "Then use Home to bind the source and choose channels", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 16, -12303258);
            }
        }
        else if (this.viewType == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
            this.renderAnalogGaugeGUI(g, channels);
        }
        else if (this.selectedTab == -1) {
            this.renderDashboard(g, channels);
        }
        else {
            final int idx = Math.min(this.selectedTab, channels.size() - 1);
            if (idx >= 0) {
                this.renderChannelGraph(g, channels.get(idx), StatsDisplayScreen.CHANNEL_COLORS[idx % StatsDisplayScreen.CHANNEL_COLORS.length], mouseX, mouseY);
                if (this.panel.getSettings().isPredictionEnabled() && this.viewType == StatsDisplaySettings.GraphType.LINE_CHART) {
                    final StatsDisplayPanelBE.DataChannel ch = channels.get(idx);
                    final float[] predData = (this.timeScale > 0) ? ch.getStore().getDataForTimeScale(this.timeScale) : ch.getStore().getData();
                    if (predData.length >= 10) {
                        final PredictionResult pred = PredictionEngine.computePrediction(ch.getId(), ch.getStore(), ch.getStore().getMetricType(), ch.getStore().getPredictionConfig(), this.panel.getSettings(), (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L, ch.isAlertEnabled() ? ((double)ch.getAlertThreshold()) : Double.NaN);
                        if (pred != null) {
                            final float[] allData = ch.getStore().getData();
                            float dMin = Float.MAX_VALUE;
                            float dMax = -3.4028235E38f;
                            for (float v : allData) {
                                dMin = Math.min(dMin, v);
                                dMax = Math.max(dMax, v);
                            }
                            GraphRenderer.drawPredictionOverlay(g, pred, predData, this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight, dMin, dMax, StatsDisplayScreen.CHANNEL_COLORS[idx % StatsDisplayScreen.CHANNEL_COLORS.length], ch.getStore().getPredictionConfig().getTCritical());
                            if (pred.hasTimeWarning()) {
                                final String warning = PredictionFormatter.formatWarning(pred);
                                final int warnColor = PredictionFormatter.getRSquaredColor(pred.rSquared());
                                g.drawCenteredString(this.font, warning, this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight + 2, warnColor);
                            }
                            if (pred.isRenderable()) {
                                final String r2Text = PredictionFormatter.formatRSquared(pred);
                                final int r2Color = PredictionFormatter.getRSquaredColor(pred.rSquared());
                                g.drawString(this.font, r2Text, this.graphLeft + this.graphWidth - this.font.width(r2Text) - 2, this.graphTop + 2, r2Color);
                            }
                        }
                    }
                }
            }
        }
        if (chartContext) {
            if (this.isSpecializedFlowMeterView()) {
                this.renderFlowMeterTriggerPanel(g);
            } else {
                this.renderOptionPanel(g, channels, mouseX, mouseY);
            }
        }
        if (chartContext && this.redstoneConfigOpen && this.selectedTab >= 0 && this.selectedTab < channels.size()) {
            this.renderRedstoneConfig(g, channels.get(this.selectedTab), mouseX, mouseY);
        }
        if (chartContext) {
            this.renderStatsBar(g, channels);
            if (this.selectedTab >= 0 && this.selectedTab < channels.size()) {
                this.renderTimeAxis(g, channels.get(this.selectedTab));
            }
            if (this.selectedTab >= 0 && this.selectedTab < channels.size()) {
                final StatsDisplayPanelBE.DataChannel ch2 = channels.get(this.selectedTab);
                if (this.showStats) {
                    this.renderStatsPanel(g, ch2);
                }
                else if (this.showLog) {
                    this.renderLogPanel(g, ch2);
                }
            }
        }
        if (chartContext && this.hoverDataIndex >= 0) {
            this.renderTooltip(g, mouseX, mouseY);

            final List<StatsDisplayPanelBE.DataChannel> hoverChs = this.getFilteredChannels();
            if (this.selectedTab >= 0 && this.selectedTab < hoverChs.size()) {
                final ItemStack hoverIcon = this.resolveItemIcon(hoverChs.get(this.selectedTab).getLabel());
                if (!hoverIcon.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate((float)(mouseX + 14), (float)(mouseY - 16), 200.0f);
                    g.pose().scale(0.75f, 0.75f, 1.0f);
                    g.renderItem(hoverIcon, 0, 0);
                    g.pose().popPose();
                }
            }
        }
        if (chartContext && !channels.isEmpty()) {
            this.renderAnnotationsGUI(g, channels, mouseX, mouseY);
        }
        this.renderBattleViewToggle(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderPickerDropdowns(g, channels, mouseX, mouseY);
        this.renderSplashQuote(g, partialTick);
        this.renderChannelRowTooltip(g, mouseX, mouseY);
    }

    private void renderVehicleSettingsPage(GuiGraphics g, int mouseX, int mouseY) {
        this.vehicleClickTargets.clear();
        final SpecializedPanelRenderer.VehicleSettingsStatus status = SpecializedPanelRenderer.describeVehicleSettings(this.panel, this.getFlightDeckChannels(false));
        final StatsDisplaySettings settings = this.panel.getSettings();
        final int pad = 10;
        final int cardGap = 8;
        final int topY = this.graphTop + pad;
        final int leftW = Math.max(140, this.graphWidth / 2 - pad - cardGap / 2);
        final int rightW = Math.max(140, this.graphWidth - leftW - pad * 2 - cardGap);
        final int leftX = this.graphLeft + pad;
        final int rightX = leftX + leftW + cardGap;
        final int topH = Math.max(116, this.graphHeight / 2 - 18);
        final int bottomY = topY + topH + cardGap;
        final int bottomH = Math.max(96, this.graphTop + this.graphHeight - bottomY - pad);
        this.renderVehicleCard(g, leftX, topY, leftW, topH, "Nose", "Choose the craft front used by shared heading logic.");
        this.renderVehicleCard(g, rightX, topY, rightW, topH, "Preset", this.describeVehiclePresetStatus(settings, status));
        this.renderVehicleCard(g, leftX, bottomY, this.graphWidth - pad * 2, bottomH, "Heading Source", status.headingDetail());
        final int centerX = leftX + leftW / 2;
        final int centerY = topY + topH / 2 + 6;
        final int noseSize = 34;
        this.renderVehicleSelectorButton(g, centerX - noseSize / 2, topY + 24, noseSize, noseSize, "\u2191", StatsDisplaySettings.NoseSetting.AHEAD, settings.getNoseSetting(), mouseX, mouseY);
        this.renderVehicleSelectorButton(g, centerX + 28, centerY - noseSize / 2, noseSize, noseSize, "\u2192", StatsDisplaySettings.NoseSetting.RIGHT, settings.getNoseSetting(), mouseX, mouseY);
        this.renderVehicleSelectorButton(g, centerX - noseSize / 2, topY + topH - 44, noseSize, noseSize, "\u2193", StatsDisplaySettings.NoseSetting.BEHIND, settings.getNoseSetting(), mouseX, mouseY);
        this.renderVehicleSelectorButton(g, centerX - 28 - noseSize, centerY - noseSize / 2, noseSize, noseSize, "\u2190", StatsDisplaySettings.NoseSetting.LEFT, settings.getNoseSetting(), mouseX, mouseY);
        this.renderVehicleSelectorButton(g, centerX - 24, centerY - 18, 48, 36, "AUTO", StatsDisplaySettings.NoseSetting.UNSET, settings.getNoseSetting(), mouseX, mouseY);
        g.drawCenteredString(this.font, "Preview", centerX, topY + topH - 62, this.DIM);
        g.drawCenteredString(this.font, status.headingAvailable() ? this.formatVehicleHeading(status.previewYaw()) : "--", centerX, topY + topH - 48, this.TEXT);
        g.drawCenteredString(this.font, settings.getNoseSetting().getDisplayName(), centerX, topY + topH - 34, this.ACCENT);
        final StatsDisplaySettings.VehiclePreset[] presets = StatsDisplaySettings.VehiclePreset.values();
        final int presetCols = 2;
        final int presetW = (rightW - 20 - 6) / presetCols;
        final int presetH = 18;
        for (int i = 0; i < presets.length; ++i) {
            final int col = i % presetCols;
            final int row = i / presetCols;
            final int bx = rightX + 10 + col * (presetW + 6);
            final int by = topY + 28 + row * (presetH + 6);
            final boolean selected = settings.getVehiclePreset() == presets[i];
            final boolean effective = status.effectivePreset() == presets[i];
            this.renderVehiclePresetButton(g, bx, by, presetW, presetH, presets[i].getDisplayName(), presets[i], selected, effective, mouseX, mouseY);
        }
        final int contentX = leftX + 12;
        int lineY = bottomY + 26;
        final String[] ladder = {
                "1. " + VehicleForwardAuthority.ForwardSource.EXPLICIT_NOSE.getLabel(),
                "2. " + VehicleForwardAuthority.ForwardSource.VELOCITY_AXIS.getLabel(),
                "3. " + VehicleForwardAuthority.ForwardSource.BODY_Z.getLabel(),
                "4. " + VehicleForwardAuthority.ForwardSource.MOTION_VECTOR.getLabel(),
                "5. " + VehicleForwardAuthority.ForwardSource.CACHED_LAST.getLabel(),
                "6. " + VehicleForwardAuthority.ForwardSource.NONE.getLabel()
        };
        final int activeStep = this.resolveVehicleHeadingStep(status.headingSource());
        for (int i = 0; i < ladder.length; ++i) {
            final boolean active = i == activeStep;
            g.fill(contentX, lineY - 1, contentX + this.graphWidth - pad * 2 - 24, lineY + 11, active ? this.TAB_SEL : this.TAB_BG);
            if (active) {
                this.border(g, contentX, lineY - 1, this.graphWidth - pad * 2 - 24, 12, this.ACCENT);
            }
            g.drawString(this.font, ladder[i], contentX + 6, lineY + 1, active ? this.TEXT : this.DIM);
            lineY += 14;
        }
        final int footerY = Math.min(bottomY + bottomH - 34, lineY + 6);
        final String authority = status.authorityLabel().isEmpty() ? "Authority auto-selects the nearest matching craft feeds." : "Authority anchor: " + status.authorityLabel();
        g.drawString(this.font, "Now using: " + status.headingSource(), contentX, footerY, status.headingAvailable() ? this.TEXT : this.WARN);
        g.drawString(this.font, authority, contentX, footerY + 12, this.DIM);
        g.drawString(this.font, status.velocityDetail(), contentX, footerY + 24, status.velocityAssistReady() ? this.ACCENT : this.DIM);
    }

    private void renderVehicleCard(GuiGraphics g, int x, int y, int w, int h, String title, String subtitle) {
        g.fill(x, y, x + w, y + h, this.BG);
        this.border(g, x, y, w, h, this.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, this.ACCENT);
        g.drawString(this.font, title, x + 8, y + 8, this.TEXT);
        g.drawString(this.font, this.truncate(subtitle, Math.max(40, w - 16)), x + 8, y + 20, this.DIM);
    }

    private void renderVehicleSelectorButton(GuiGraphics g, int x, int y, int w, int h, String label, StatsDisplaySettings.NoseSetting nose, StatsDisplaySettings.NoseSetting selected, int mouseX, int mouseY) {
        final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        final boolean active = selected == nose;
        g.fill(x, y, x + w, y + h, active ? this.TAB_SEL : (hover ? this.TAB_HOVER : this.TAB_BG));
        this.border(g, x, y, w, h, active ? this.ACCENT : this.BORDER);
        g.drawCenteredString(this.font, label, x + w / 2, y + h / 2 - 4, active ? this.TEXT : this.DIM);
        this.vehicleClickTargets.add(new VehicleClickTarget(VehicleClickAction.SET_NOSE, x, y, w, h, nose.name()));
    }

    private void renderVehiclePresetButton(GuiGraphics g, int x, int y, int w, int h, String label, StatsDisplaySettings.VehiclePreset preset, boolean selected, boolean effective, int mouseX, int mouseY) {
        final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : this.TAB_BG));
        this.border(g, x, y, w, h, selected ? this.ACCENT : this.BORDER);
        g.drawString(this.font, label, x + 6, y + 5, selected ? this.TEXT : this.DIM);
        if (effective) {
            g.drawString(this.font, "\u25cf", x + w - 10, y + 5, this.ACCENT);
        }
        this.vehicleClickTargets.add(new VehicleClickTarget(VehicleClickAction.SET_PRESET, x, y, w, h, preset.name()));
    }

    private String describeVehiclePresetStatus(StatsDisplaySettings settings, SpecializedPanelRenderer.VehicleSettingsStatus status) {
        if (settings.getVehiclePreset() != StatsDisplaySettings.VehiclePreset.AUTO) {
            return "Guidance trims locked to " + settings.getVehiclePreset().getDisplayName() + ".";
        }
        if (status.effectivePreset() == StatsDisplaySettings.VehiclePreset.AUTO) {
            return "Auto resolves when aero feeds begin reporting.";
        }
        return "Auto resolves to " + status.effectivePreset().getDisplayName() + ".";
    }

    private int resolveVehicleHeadingStep(String headingSource) {
        final String normalized = headingSource == null ? "" : headingSource.toLowerCase();
        if (normalized.contains("explicit")) {
            return 0;
        }
        if (normalized.contains("body")) {
            return 1;
        }
        if (normalized.contains("velocity")) {
            return 2;
        }
        if (normalized.contains("motion")) {
            return 3;
        }
        if (normalized.contains("last") || normalized.contains("cache")) {
            return 4;
        }
        return 5;
    }

    private String formatVehicleHeading(float yaw) {
        final String[] cardinals = { "E", "NE", "N", "NW", "W", "SW", "S", "SE" };
        final int index = Math.floorMod(Math.round(net.minecraft.util.Mth.wrapDegrees(yaw) / 45.0f), cardinals.length);
        return Math.round(net.minecraft.util.Mth.wrapDegrees(yaw)) + "\u00b0 " + cardinals[index];
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private void renderSplashQuote(GuiGraphics g, float partialTick) {
        if (this.splashQuote == null) {
            return;
        }
        final long elapsed = System.currentTimeMillis() - this.splashOpenTime;
        final float fadeAlpha = Math.min(1.0f, elapsed / 1500.0f);
        if (fadeAlpha <= 0.0f) {
            return;
        }
        final int alpha = (int)(fadeAlpha * 180.0f) & 0xFF;
        final int color = alpha << 24 | 0xFFDD44;
        final float time = System.currentTimeMillis() % 4000L / 4000.0f;
        final float wobble = 1.0f + 0.02f * (float)Math.sin(time * 3.141592653589793 * 2.0);
        final int textW = this.font.width(this.splashQuote);
        final float cx = this.width / 2.0f;
        final float cy = this.height - 10.0f;
        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0f);
        pose.scale(wobble, wobble, 1.0f);
        final Axis axis = Axis.ZP;
        final float tiltDeg = 1.2f * (float)Math.sin(time * 3.141592653589793 * 2.0 + 0.5);
        pose.mulPose(axis.rotationDegrees(tiltDeg));
        final Font font = this.font;
        final String splashQuote = this.splashQuote;
        final int n = 0;
        Objects.requireNonNull(this.font);
        g.drawCenteredString(font, splashQuote, n, -9 / 2, color);
        pose.popPose();
    }

    private void renderSourceBar(GuiGraphics g, LinkedHashMap<String, SourceConfig> registry, int mx, int my) {
        final int srcY = this.panelTop + 22;
        final int tabH = 18;
        final int leftEdge = this.panelLeft + 8;
        final int rightEdge = this.panelLeft + this.panelWidth - 8;
        int navX = leftEdge;
        navX = this.renderNavPill(g, mx, my, navX, srcY, tabH, "\u2302 Home", this.isHomePage(), -1);
        navX = this.renderNavPill(g, mx, my, navX, srcY, tabH, "\u2261 Inv", this.isInventoryStatusPage(), -1);
        navX = this.renderNavPill(g, mx, my, navX, srcY, tabH, "Factory", this.isFactoryStatusPage(), -1);
        final boolean showVehicle = this.hasVehicleSettingsPage();
        final boolean showFlightDeck = this.hasFlightDeckPage();
        final boolean showGuidance = this.hasGuidancePage();
        final boolean showTrain = this.hasTrainSourcePage();
        final boolean showPower = this.hasPowerPage();
        int systemX = rightEdge;
        systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Tools", this.isFactoryPage(), this.ACCENT);
        if (showPower) {
            systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Power", this.isPowerPage(), this.getPowerNavDotColor());
        }
        if (showTrain) {
            systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Train", this.isTrainPage(), this.ACCENT);
        }
        if (showGuidance) {
            systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Aero", this.isGuidancePage(), this.isGuidanceLive() ? -16724890 : (this.isGuidanceInterrupted() ? this.WARN : -10066330));
        }
        if (showFlightDeck) {
            systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Flight", this.isFlightDeckPage(), this.isFlightDeckLive() ? -16724890 : (this.isFlightDeckInterrupted() ? this.WARN : -10066330));
        }
        if (showVehicle) {
            systemX = this.renderRightNavPill(g, mx, my, systemX, srcY, tabH, "Vehicle", this.isVehicleSettingsPage(), this.isFlightDeckLive() || this.isGuidanceLive() ? -16724890 : ((this.isFlightDeckInterrupted() || this.isGuidanceInterrupted()) ? this.WARN : -10066330));
        }
        final int pickerX = navX + 5;
        final int pickerW = Math.max(0, systemX - pickerX - 5);
        this.renderSourcePicker(g, mx, my, pickerX, srcY, pickerW, tabH);
    }

    private int renderNavPill(GuiGraphics g, int mx, int my, int x, int y, int h, String label, boolean selected, int dotColor) {
        final int w = this.font.width(label) + (dotColor >= 0 ? 20 : 14);
        final boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        this.drawSoftPill(g, x, y, w, h, selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : darken(this.TAB_BG, 0.82f)), selected ? this.ACCENT : darken(this.BORDER, 0.72f));
        int textX = x + 7;
        if (dotColor >= 0) {
            g.fill(x + 7, y + h / 2 - 2, x + 11, y + h / 2 + 2, dotColor);
            textX += 8;
        }
        g.drawString(this.font, label, textX, y + (h - 8) / 2, selected ? this.TEXT : this.DIM);
        return x + w + 5;
    }

    private int renderRightNavPill(GuiGraphics g, int mx, int my, int right, int y, int h, String label, boolean selected, int dotColor) {
        final int w = this.font.width(label) + (dotColor >= 0 ? 20 : 14);
        final int x = right - w;
        this.renderNavPill(g, mx, my, x, y, h, label, selected, dotColor);
        return x - 5;
    }

    private void renderSourcePicker(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        if (w < 70 || this.sourceIds.isEmpty()) {
            return;
        }
        final int index = this.getSourcePickerIndex();
        final String sourcePageId = this.sourceIds.get(index);
        final boolean selected = this.isNormalSourcePage();
        final boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        final boolean active = this.isSourcePageActive(sourcePageId);
        final boolean interrupted = this.isSourcePageInterrupted(sourcePageId);
        final int dotColor = active ? -16724890 : (interrupted ? this.WARN : -10066330);
        this.drawSoftPill(g, x, y, w, h, selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : darken(this.TAB_BG, 0.72f)), selected ? this.ACCENT : this.BORDER);
        final boolean canCycle = this.sourceIds.size() > 1;
        final int arrowW = canCycle ? 18 : 0;
        if (canCycle) {
            g.drawCenteredString(this.font, "\u2039", x + 9, y + (h - 8) / 2, hover ? this.TEXT : this.DIM);
            g.drawCenteredString(this.font, "\u203a", x + w - 9, y + (h - 8) / 2, hover ? this.TEXT : this.DIM);
        }
        final int labelX = x + 8 + arrowW;
        final boolean renameable = this.isSourceRenameable(sourcePageId);
        final int editW = renameable ? 14 : 0;
        final int labelW = Math.max(20, w - 16 - arrowW * 2 - editW);
        g.fill(labelX, y + h / 2 - 2, labelX + 4, y + h / 2 + 2, dotColor);
        final String label = "Source: " + this.sourceLabel(sourcePageId) + "  " + (index + 1) + "/" + this.sourceIds.size();
        g.drawString(this.font, this.truncate(label, labelW - 8), labelX + 8, y + (h - 8) / 2, selected ? this.TEXT : this.DIM);
        if (renameable) {
            g.drawCenteredString(this.font, "\u270e", x + w - arrowW - 9, y + (h - 8) / 2, hover ? this.ACCENT : this.DIM);
        }
    }

    private void drawSoftPill(GuiGraphics g, int x, int y, int w, int h, int fill, int borderColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x + 3, y, x + w - 3, y + h, fill);
        g.fill(x, y + 3, x + w, y + h - 3, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        this.hline(g, x + 4, x + w - 4, y, borderColor);
        this.hline(g, x + 4, x + w - 4, y + h - 1, darken(borderColor, 0.72f));
        this.vline(g, x, y + 4, y + h - 4, borderColor);
        this.vline(g, x + w - 1, y + 4, y + h - 4, darken(borderColor, 0.72f));
    }

    private PanelButton addPanelButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
        return this.addRenderableWidget(new PanelButton(x, y, w, h, label, onPress));
    }

    private void drawPanelSectionHeader(GuiGraphics g, int x, int y, int w, String label) {
        final String up = label.toUpperCase(java.util.Locale.ROOT);
        g.drawString(this.font, up, x, y, this.ACCENT);
        final int lineX = x + this.font.width(up) + 6;
        if (lineX < x + w) {
            this.hline(g, lineX, x + w, y + 3, darken(this.BORDER, 0.7f));
        }
    }

    private String viewTypeTooltip(StatsDisplaySettings.GraphType type) {
        return switch (type) {
            case LINE_CHART -> "Line chart over time";
            case BAR_CHART -> "Bar chart over time";
            case GAUGE -> "Gauge (current value)";
            case NUMERIC -> "Large numeric readout";
            case ANALOG_GAUGE -> "Analog instrument dial";
            case EFFICIENCY -> "Efficiency grade";
            case TOPOLOGY -> "Network topology";
            default -> type.name();
        };
    }

    private final class PanelButton extends Button {
        private boolean pressed;

        private PanelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.pressed = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.pressed = false;
            super.onRelease(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            final int bx = this.getX();
            final int by = this.getY();
            final int bw = this.width;
            final int bh = this.height;
            final boolean enabled = this.active;
            final boolean hot = this.isHoveredOrFocused() && enabled;
            final boolean down = this.pressed && hot;

            final int base = !enabled
                    ? darken(StatsDisplayScreen.this.TAB_BG, 0.5f)
                    : (hot ? StatsDisplayScreen.this.TAB_HOVER : darken(StatsDisplayScreen.this.TAB_BG, 0.92f));
            int topFill = opaque(brighten(base, down ? 0.86f : 1.2f));
            int botFill = opaque(brighten(base, down ? 1.12f : 0.8f));
            final int borderC = !enabled
                    ? opaque(darken(StatsDisplayScreen.this.BORDER, 0.55f))
                    : (hot ? opaque(StatsDisplayScreen.this.ACCENT) : opaque(StatsDisplayScreen.this.BORDER));
            final int textC = !enabled
                    ? StatsDisplayScreen.this.DIM
                    : (hot ? 0xFFFFFFFF : StatsDisplayScreen.this.TEXT);

            g.fillGradient(bx + 1, by + 1, bx + bw - 1, by + bh - 1, topFill, botFill);

            StatsDisplayScreen.this.border(g, bx, by, bw, bh, borderC);

            StatsDisplayScreen.this.hline(g, bx + 1, bx + bw - 1, down ? by + bh - 2 : by + 1, opaque(brighten(topFill, 1.18f)));
            StatsDisplayScreen.this.hline(g, bx + 1, bx + bw - 1, down ? by + 1 : by + bh - 2, opaque(darken(botFill, 0.72f)));

            if (hot) {
                StatsDisplayScreen.this.border(g, bx + 1, by + 1, bw - 2, bh - 2, opaque(darken(StatsDisplayScreen.this.ACCENT, 0.55f)));
            }
            final int textY = by + Math.max(2, (bh - StatsDisplayScreen.this.font.lineHeight) / 2) + (down ? 1 : 0);
            g.drawCenteredString(StatsDisplayScreen.this.font, this.getMessage(), bx + bw / 2, textY, textC);
        }
    }

    private int getNavSectionLabelWidth(String label, int availableWidth, int minimumWidth) {
        if (availableWidth < minimumWidth) {
            return 0;
        }
        return this.font.width(label) + 8;
    }

    private void renderNavSectionLabel(GuiGraphics g, int x, int y, int w, int h, String label) {
        g.drawString(this.font, this.truncate(label, Math.max(12, w - 4)), x + 2, y + (h - 8) / 2, this.ACCENT);
    }

    private String truncateSourceTabLabel(String sourcePageId) {
        String label = this.sourceLabel(sourcePageId);
        if (label.length() > 18) {
            label = label.substring(0, 16) + "..";
        }
        return label;
    }

    private int getSourceTabsTotalWidth() {
        int totalWidth = 0;
        for (String sourcePageId : this.sourceIds) {
            if (totalWidth > 0) {
                totalWidth += 3;
            }
            totalWidth += this.font.width(this.truncateSourceTabLabel(sourcePageId)) + 14;
        }
        return totalWidth;
    }

    private List<Integer> getVisibleSourceTabIndices(int availableWidth) {
        final ArrayList<Integer> visible = new ArrayList<Integer>();
        if (availableWidth <= 0 || this.sourceIds.isEmpty()) {
            return visible;
        }
        this.sourceTabScrollOffset = Math.max(0, Math.min(this.sourceTabScrollOffset, Math.max(0, this.sourceIds.size() - 1)));
        int usedWidth = 0;
        for (int index = this.sourceTabScrollOffset; index < this.sourceIds.size(); ++index) {
            final int tabWidth = this.font.width(this.truncateSourceTabLabel(this.sourceIds.get(index))) + 14;
            final int neededWidth = visible.isEmpty() ? tabWidth : (tabWidth + 3);
            if (!visible.isEmpty() && usedWidth + neededWidth > availableWidth) {
                break;
            }
            if (visible.isEmpty() && tabWidth > availableWidth) {
                visible.add(index);
                break;
            }
            if (!visible.isEmpty()) {
                usedWidth += 3;
            }
            if (usedWidth + tabWidth > availableWidth) {
                break;
            }
            visible.add(index);
            usedWidth += tabWidth;
        }
        return visible;
    }

    private void ensureSelectedSourceTabVisible(int availableWidth) {
        if (availableWidth <= 0 || !this.isNormalSourcePage() || this.selectedSource < 0 || this.selectedSource >= this.sourceIds.size()) {
            return;
        }
        if (this.selectedSource < this.sourceTabScrollOffset) {
            this.sourceTabScrollOffset = this.selectedSource;
        }
        List<Integer> visible = this.getVisibleSourceTabIndices(availableWidth);
        while (!visible.isEmpty() && this.selectedSource > visible.get(visible.size() - 1) && this.sourceTabScrollOffset < this.sourceIds.size() - 1) {
            ++this.sourceTabScrollOffset;
            visible = this.getVisibleSourceTabIndices(availableWidth);
        }
        while (visible.isEmpty() && this.sourceTabScrollOffset > 0) {
            --this.sourceTabScrollOffset;
            visible = this.getVisibleSourceTabIndices(availableWidth);
        }
    }

    private void renderTabBar(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        final boolean multiSource = this.getVisibleSourceTabCount() > 1;
        final int sourceBarH = multiSource ? 20 : 0;
        final int tabY = this.panelTop + 22 + sourceBarH;
        final int tabH = this.tabBarHeight - 4;
        final int left = this.panelLeft + 8;
        final int right = this.panelLeft + this.panelWidth - 8;
        final int overviewW = Math.max(72, this.font.width("Overview") + 18);
        final boolean overviewHover = mx >= left && mx < left + overviewW && my >= tabY && my < tabY + tabH;
        this.drawSoftPill(g, left, tabY, overviewW, tabH, this.selectedTab == -1 ? this.TAB_SEL : (overviewHover ? this.TAB_HOVER : darken(this.TAB_BG, 0.78f)), this.selectedTab == -1 ? this.ACCENT : darken(this.BORDER, 0.72f));
        g.drawCenteredString(this.font, "Overview", left + overviewW / 2, tabY + (tabH - 8) / 2, this.selectedTab == -1 ? this.TEXT : this.DIM);
        final int pickerX = left + overviewW + 6;
        final int pickerW = Math.max(80, right - pickerX);
        final boolean pickerHover = mx >= pickerX && mx < pickerX + pickerW && my >= tabY && my < tabY + tabH;
        final boolean signalSelected = this.selectedTab >= 0 && this.selectedTab < channels.size();
        this.drawSoftPill(g, pickerX, tabY, pickerW, tabH, signalSelected ? this.TAB_SEL : (pickerHover ? this.TAB_HOVER : darken(this.TAB_BG, 0.7f)), signalSelected ? this.ACCENT : this.BORDER);
        if (channels.isEmpty()) {
            g.drawString(this.font, "Signals: none", pickerX + 10, tabY + (tabH - 8) / 2, this.DIM);
            return;
        }
        final int channelIndex = Math.max(0, Math.min(channels.size() - 1, signalSelected ? this.selectedTab : 0));
        final StatsDisplayPanelBE.DataChannel channel = channels.get(channelIndex);
        final int color = StatsDisplayScreen.CHANNEL_COLORS[channelIndex % StatsDisplayScreen.CHANNEL_COLORS.length];
        final boolean canCycle = channels.size() > 1;
        if (canCycle) {
            g.drawCenteredString(this.font, "\u2039", pickerX + 10, tabY + (tabH - 8) / 2, pickerHover ? this.TEXT : this.DIM);
            g.drawCenteredString(this.font, "\u203a", pickerX + pickerW - 10, tabY + (tabH - 8) / 2, pickerHover ? this.TEXT : this.DIM);
        }
        final int labelX = pickerX + (canCycle ? 24 : 10);
        final int labelW = pickerW - (canCycle ? 48 : 20);
        g.fill(labelX, tabY + tabH / 2 - 2, labelX + 4, tabY + tabH / 2 + 2, color);
        final String label = signalSelected
                ? "Signal: " + channel.getLabel() + "  " + (channelIndex + 1) + "/" + channels.size()
                : "Signals: " + channels.size() + " linked";
        g.drawString(this.font, this.truncate(label, labelW - 8), labelX + 8, tabY + (tabH - 8) / 2, signalSelected ? this.TEXT : this.DIM);
    }

    private void renderPickerDropdowns(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        if (!this.sourceDropdownOpen && !this.signalDropdownOpen) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 500.0f);
        if (this.sourceDropdownOpen) {
            this.renderSourceDropdown(g, mx, my);
        }
        if (this.signalDropdownOpen) {
            this.renderSignalDropdown(g, channels, mx, my);
        }
        g.pose().popPose();
    }

    private void renderSourceDropdown(GuiGraphics g, int mx, int my) {
        if (this.sourceIds.isEmpty() || this.getVisibleSourceTabCount() <= 1) {
            this.sourceDropdownOpen = false;
            return;
        }
        final int[] picker = this.getSourcePickerBounds();
        if (picker[2] < 80) {
            return;
        }
        final int rowH = 18;
        final int visibleRows = Math.min(7, this.sourceIds.size());
        this.sourceDropdownScrollOffset = this.clampDropdownScroll(this.sourceDropdownScrollOffset, this.sourceIds.size(), visibleRows);
        final int x = picker[0];
        final int y = picker[1] + picker[3] + 3;
        final int w = picker[2];
        final int h = visibleRows * rowH + 8;
        this.drawDropdownShell(g, x, y, w, h);
        for (int row = 0; row < visibleRows; ++row) {
            final int index = this.sourceDropdownScrollOffset + row;
            final String sourcePageId = this.sourceIds.get(index);
            final int rowY = y + 4 + row * rowH;
            final boolean selected = this.isNormalSourcePage() && this.selectedSource == index;
            final boolean hover = mx >= x + 3 && mx < x + w - 3 && my >= rowY && my < rowY + rowH;
            final boolean active = this.isSourcePageActive(sourcePageId);
            final boolean interrupted = this.isSourcePageInterrupted(sourcePageId);
            final int dotColor = active ? -16724890 : (interrupted ? this.WARN : -10066330);
            if (selected || hover) {
                this.drawSoftPill(g, x + 4, rowY + 1, w - 8, rowH - 2, selected ? this.TAB_SEL : this.TAB_HOVER, selected ? this.ACCENT : darken(this.BORDER, 0.85f));
            }
            g.fill(x + 12, rowY + rowH / 2 - 2, x + 16, rowY + rowH / 2 + 2, dotColor);
            final String label = this.sourceLabel(sourcePageId);
            final boolean renameable = this.isSourceRenameable(sourcePageId);
            g.drawString(this.font, this.truncate(label, w - (renameable ? 76 : 60)), x + 22, rowY + 5, selected ? this.TEXT : this.DIM);
            final String count = (index + 1) + "/" + this.sourceIds.size();
            final int countX = x + w - this.font.width(count) - 10;
            if (renameable) {
                g.drawString(this.font, "\u270e", countX - 14, rowY + 5, hover ? this.ACCENT : this.DIM);
            }
            g.drawString(this.font, count, countX, rowY + 5, this.DIM);
        }
    }

    private void renderSignalDropdown(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        if (channels.isEmpty() || !this.shouldShowChartControls()) {
            this.signalDropdownOpen = false;
            return;
        }
        final int[] picker = this.getSignalPickerBounds();
        final int rowH = 18;
        final int visibleRows = Math.min(7, channels.size());
        this.signalDropdownScrollOffset = this.clampDropdownScroll(this.signalDropdownScrollOffset, channels.size(), visibleRows);
        final int x = picker[0];
        final int y = picker[1] + picker[3] + 3;
        final int w = picker[2];
        final int h = visibleRows * rowH + 8;
        this.drawDropdownShell(g, x, y, w, h);
        for (int row = 0; row < visibleRows; ++row) {
            final int index = this.signalDropdownScrollOffset + row;
            final StatsDisplayPanelBE.DataChannel channel = channels.get(index);
            final int rowY = y + 4 + row * rowH;
            final boolean selected = this.selectedTab == index;
            final boolean hover = mx >= x + 3 && mx < x + w - 3 && my >= rowY && my < rowY + rowH;
            final int color = StatsDisplayScreen.CHANNEL_COLORS[index % StatsDisplayScreen.CHANNEL_COLORS.length];
            if (selected || hover) {
                this.drawSoftPill(g, x + 4, rowY + 1, w - 8, rowH - 2, selected ? this.TAB_SEL : this.TAB_HOVER, selected ? this.ACCENT : darken(this.BORDER, 0.85f));
            }
            g.fill(x + 12, rowY + rowH / 2 - 2, x + 16, rowY + rowH / 2 + 2, color);
            g.drawString(this.font, this.truncate(channel.getLabel(), w - 60), x + 22, rowY + 5, selected ? this.TEXT : this.DIM);
            final String count = (index + 1) + "/" + channels.size();
            g.drawString(this.font, count, x + w - this.font.width(count) - 10, rowY + 5, this.DIM);
        }
    }

    private void drawDropdownShell(GuiGraphics g, int x, int y, int w, int h) {
        final int shadow = 0xCC000000;
        final int base = opaque(darken(this.BG, 0.55f));
        final int inner = opaque(darken(this.GRAPH_BG, 0.72f));
        g.fill(x + 4, y + 5, x + w + 4, y + h + 5, shadow);
        g.fill(x, y, x + w, y + h, base);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, inner);
        this.hline(g, x + 2, x + w - 2, y, opaque(this.ACCENT));
        this.hline(g, x + 2, x + w - 2, y + h - 1, opaque(darken(this.ACCENT, 0.78f)));
        this.vline(g, x, y + 2, y + h - 2, opaque(this.ACCENT));
        this.vline(g, x + w - 1, y + 2, y + h - 2, opaque(darken(this.ACCENT, 0.78f)));
    }

    private int[] getSourcePickerBounds() {
        final int srcY = this.panelTop + 22;
        final int tabH = 18;
        final int leftEdge = this.panelLeft + 8;
        final int rightEdge = this.panelLeft + this.panelWidth - 8;
        int navX = leftEdge;
        navX += this.font.width("\u2302 Home") + 14 + 5;
        navX += this.font.width("\u2261 Inv") + 14 + 5;
        navX += this.font.width("Factory") + 14 + 5;
        int systemX = rightEdge;
        systemX -= this.font.width("Tools") + 20;
        systemX -= 5;
        if (this.hasTrainSourcePage()) {
            systemX -= this.font.width("Train") + 20;
            systemX -= 5;
        }
        if (this.hasGuidancePage()) {
            systemX -= this.font.width("Aero") + 20;
            systemX -= 5;
        }
        if (this.hasFlightDeckPage()) {
            systemX -= this.font.width("Flight") + 20;
            systemX -= 5;
        }
        if (this.hasVehicleSettingsPage()) {
            systemX -= this.font.width("Vehicle") + 20;
            systemX -= 5;
        }
        final int pickerX = navX + 5;
        return new int[] { pickerX, srcY, Math.max(0, systemX - pickerX - 5), tabH };
    }

    private int[] getSignalPickerBounds() {
        final boolean multiSource = this.getVisibleSourceTabCount() > 1;
        final int sourceBarH = multiSource ? 20 : 0;
        final int tabY = this.panelTop + 22 + sourceBarH;
        final int tabH = this.tabBarHeight - 4;
        final int left = this.panelLeft + 8;
        final int right = this.panelLeft + this.panelWidth - 8;
        final int overviewW = Math.max(72, this.font.width("Overview") + 18);
        final int pickerX = left + overviewW + 6;
        return new int[] { pickerX, tabY, Math.max(80, right - pickerX), tabH };
    }

    private int clampDropdownScroll(int offset, int size, int visibleRows) {
        return Math.max(0, Math.min(offset, Math.max(0, size - visibleRows)));
    }

    private void openSourceDropdown() {
        this.sourceDropdownOpen = true;
        this.signalDropdownOpen = false;
        this.sourceDropdownScrollOffset = this.clampDropdownScroll(this.getSourcePickerIndex() - 3, this.sourceIds.size(), Math.min(7, this.sourceIds.size()));
    }

    private void openSignalDropdown(List<StatsDisplayPanelBE.DataChannel> channels) {
        this.signalDropdownOpen = true;
        this.sourceDropdownOpen = false;
        final int channelIndex = this.selectedTab >= 0 ? this.selectedTab : 0;
        this.signalDropdownScrollOffset = this.clampDropdownScroll(channelIndex - 3, channels.size(), Math.min(7, channels.size()));
    }

    private void closePickerDropdowns() {
        this.sourceDropdownOpen = false;
        this.signalDropdownOpen = false;
    }

    private boolean handleSourceDropdownClick(double mx, double my, int button) {
        if (this.sourceIds.isEmpty()) {
            this.sourceDropdownOpen = false;
            return false;
        }
        final int[] picker = this.getSourcePickerBounds();
        final int rowH = 18;
        final int visibleRows = Math.min(7, this.sourceIds.size());
        final int x = picker[0];
        final int y = picker[1] + picker[3] + 3;
        final int w = picker[2];
        final int h = visibleRows * rowH + 8;
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        final int row = ((int)my - y - 4) / rowH;
        if (row >= 0 && row < visibleRows) {
            final int index = this.sourceDropdownScrollOffset + row;
            if (index >= 0 && index < this.sourceIds.size()) {
                final String sourcePageId = this.sourceIds.get(index);
                if (button == 1 && this.isSourceRenameable(sourcePageId)) {
                    final SourceConfig config = this.panel.getSourceRegistry().get(sourcePageId);
                    if (config != null) {
                        this.openSourceRename(sourcePageId, config, x, y + h + 2);
                    }
                } else if (button == 0) {
                    this.selectNormalSourcePage(index);
                    this.closePickerDropdowns();
                }
            }
        }
        return true;
    }

    private boolean isSourceRenameable(String sourcePageId) {
        return !this.isGroupedSourcePage(sourcePageId)
                && this.panel != null
                && this.panel.getSourceRegistry().get(sourcePageId) != null;
    }

    private boolean handleSignalDropdownClick(double mx, double my, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels.isEmpty()) {
            this.signalDropdownOpen = false;
            return false;
        }
        final int[] picker = this.getSignalPickerBounds();
        final int rowH = 18;
        final int visibleRows = Math.min(7, channels.size());
        final int x = picker[0];
        final int y = picker[1] + picker[3] + 3;
        final int w = picker[2];
        final int h = visibleRows * rowH + 8;
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }
        final int row = ((int)my - y - 4) / rowH;
        if (row >= 0 && row < visibleRows) {
            final int index = this.signalDropdownScrollOffset + row;
            if (index >= 0 && index < channels.size()) {
                this.selectChannelFromPicker(index);
                this.closePickerDropdowns();
            }
        }
        return true;
    }

    private boolean scrollPickerDropdown(double scrollY) {
        if (this.sourceDropdownOpen) {
            final int visibleRows = Math.min(7, this.sourceIds.size());
            this.sourceDropdownScrollOffset = this.clampDropdownScroll(this.sourceDropdownScrollOffset - (int)Math.signum(scrollY), this.sourceIds.size(), visibleRows);
            return true;
        }
        if (this.signalDropdownOpen) {
            final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
            final int visibleRows = Math.min(7, channels.size());
            this.signalDropdownScrollOffset = this.clampDropdownScroll(this.signalDropdownScrollOffset - (int)Math.signum(scrollY), channels.size(), visibleRows);
            return true;
        }
        return false;
    }

    private String getChannelTabScopeLabel() {
        final String sourcePageId = this.getCurrentSourcePageKey();
        if (sourcePageId == null || sourcePageId.isEmpty()) {
            return "Channels: " + this.getCurrentPageTitle();
        }
        return "Channels: " + this.sourceLabel(sourcePageId);
    }

    private int getChannelTabScopeWidth() {
        return Math.min(104, Math.max(58, this.font.width(this.getChannelTabScopeLabel()) + 6));
    }

    private ItemStack resolveItemIcon(String displayName) {
        final String clean = displayName.trim();
        final ItemStack cached = this.itemIconCache.get(clean);
        if (cached != null) {
            return cached;
        }
        for (String candidate : this.collectItemIconCandidates(clean)) {
            final ItemStack candidateCached = this.itemIconCache.get(candidate);
            if (candidateCached != null && !candidateCached.isEmpty()) {
                this.itemIconCache.put(clean, candidateCached);
                return candidateCached;
            }
            final ItemStack resolved = this.findExactItemIcon(candidate);
            if (!resolved.isEmpty()) {
                this.itemIconCache.put(candidate, resolved);
                this.itemIconCache.put(clean, resolved);
                return resolved;
            }
        }
        this.itemIconCache.put(clean, ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    private ItemStack findExactItemIcon(String displayName) {
        for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            final ItemStack test = new ItemStack((ItemLike)entry.getValue());
            if (test.getHoverName().getString().equalsIgnoreCase(displayName)) {
                return test;
            }
        }
        return ItemStack.EMPTY;
    }

    private List<String> collectItemIconCandidates(String displayName) {
        final LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        this.addItemIconCandidate(candidates, displayName);
        final int bulletIndex = displayName.lastIndexOf("\u2022");
        if (bulletIndex >= 0 && bulletIndex + 1 < displayName.length()) {
            this.addItemIconCandidate(candidates, displayName.substring(bulletIndex + 1));
        }
        final int parenIndex = displayName.lastIndexOf(" (");
        if (parenIndex > 0) {
            this.addItemIconCandidate(candidates, displayName.substring(0, parenIndex));
        }
        final String[] suffixes = new String[] { " Stock", " Target", " Promised" };
        for (String suffix : suffixes) {
            if (displayName.endsWith(suffix)) {
                this.addItemIconCandidate(candidates, displayName.substring(0, displayName.length() - suffix.length()));
            }
        }
        return new ArrayList<String>(candidates);
    }

    private void addItemIconCandidate(Set<String> candidates, String candidate) {
        if (candidate == null) {
            return;
        }
        final String clean = candidate.trim();
        if (!clean.isEmpty()) {
            candidates.add(clean);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && this.battleViewToggleVisible
                && mx >= this.battleViewToggleX && mx < this.battleViewToggleX + this.battleViewToggleW
                && my >= this.battleViewToggleY && my < this.battleViewToggleY + this.battleViewToggleH) {
            com.createstats.client.battle.BattleTacticalV2Viewport3D.toggleForce2D(this.panel.getBlockPos());
            this.rebuildWidgets();
            return true;
        }

        // Routed first so the viewport "owns" clicks inside its rect before the screen

        if (com.createstats.client.battle.BattleTacticalV2Viewport3D.mouseClicked(this.panel, mx, my, button)) {
            return true;
        }
        if (this.sourceRenameOpen && this.sourceRenameInput != null && button == 0 && !this.sourceRenameInput.isMouseOver(mx, my)) {
            this.applySourceRename();
            return true;
        }
        if ((button == 0 || button == 1) && (this.sourceDropdownOpen || this.signalDropdownOpen)) {
            final List<StatsDisplayPanelBE.DataChannel> dropdownChannels = this.shouldShowChartControls() ? this.getFilteredChannels() : List.of();
            if (this.sourceDropdownOpen && this.handleSourceDropdownClick(mx, my, button)) {
                return true;
            }
            if (button == 0 && this.signalDropdownOpen && this.handleSignalDropdownClick(mx, my, dropdownChannels)) {
                return true;
            }
            if (button == 0) {
                this.closePickerDropdowns();
                return true;
            }
        }
        if (button == 0 && this.handleOptionPanelChannelListClick(mx, my)) {
            return true;
        }
        if (button == 0 && this.isHomePage() && this.handleHomePageClick(mx, my)) {
            return true;
        }
        if (button == 0 && this.isFactoryPage() && this.handleFactoryPageClick(mx, my)) {
            return true;
        }
        if (button == 0 && this.isVehicleSettingsPage() && this.handleVehicleSettingsPageClick(mx, my)) {
            return true;
        }
        if (button == 0 && this.isPowerPage() && this.powerPage.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && this.handleBattleBoardPresetClick(mx, my)) {
            return true;
        }
        if (button == 0 || button == 1) {
            final boolean multiSource = this.getVisibleSourceTabCount() > 1;
            if (multiSource) {
                final int srcY = this.panelTop + 22;
                final int tabH = 18;
                if (my >= srcY && my < srcY + tabH) {
                    final int leftEdge = this.panelLeft + 8;
                    final int rightEdge = this.panelLeft + this.panelWidth - 8;
                    int tabX = leftEdge;
                    final int homeW = this.font.width("\u2302 Home") + 14;
                    if (mx >= tabX && mx < tabX + homeW) {
                        if (button == 0) {
                            this.selectHomePage();
                        }
                        return button == 0;
                    }
                    tabX += homeW + 5;
                    final int invTabW2 = this.font.width("\u2261 Inv") + 14;
                    if (mx >= tabX && mx < tabX + invTabW2) {
                        if (button == 0) {
                            this.selectInventoryStatusPage();
                        }
                        return button == 0;
                    }
                    tabX += invTabW2 + 5;
                    final int factoryStatusTabW = this.font.width("Factory") + 14;
                    if (mx >= tabX && mx < tabX + factoryStatusTabW) {
                        if (button == 0) {
                            this.selectFactoryStatusPage();
                        }
                        return button == 0;
                    }
                    tabX += factoryStatusTabW + 5;
                    final boolean showVehicle = this.hasVehicleSettingsPage();
                    final boolean showFlightDeck = this.hasFlightDeckPage();
                    final boolean showGuidance = this.hasGuidancePage();
                    final boolean showTrain = this.hasTrainSourcePage();
                    final boolean showPower = this.hasPowerPage();
                    final int vehicleW = this.font.width("Vehicle") + 20;
                    final int flightDeckW = this.font.width("Flight") + 20;
                    final int guidanceW = this.font.width("Aero") + 20;
                    final int trainW = this.font.width("Train") + 20;
                    final int powerW = this.font.width("Power") + 20;
                    final int factoryW = this.font.width("Tools") + 20;
                    final int factoryX = rightEdge - factoryW;
                    final int powerX = showPower ? (factoryX - 5 - powerW) : factoryX;
                    final int trainX = showTrain ? (powerX - 5 - trainW) : powerX;
                    final int guidanceX = showGuidance ? (trainX - 5 - guidanceW) : trainX;
                    final int flightDeckX = showFlightDeck ? (guidanceX - 5 - flightDeckW) : guidanceX;
                    final int vehicleX = showVehicle ? (flightDeckX - 5 - vehicleW) : flightDeckX;
                    final int[] sourcePicker = this.getSourcePickerBounds();
                    final int pickerX = sourcePicker[0];
                    final int pickerW = sourcePicker[2];
                    if (!this.sourceIds.isEmpty() && pickerW >= 70 && mx >= pickerX && mx < pickerX + pickerW) {
                        final int currentIndex = this.getSourcePickerIndex();
                        final String sourcePageId = this.sourceIds.get(currentIndex);
                        if (button == 1) {
                            if (this.isGroupedSourcePage(sourcePageId)) {
                                return false;
                            }
                            final SourceConfig config = this.panel.getSourceRegistry().get(sourcePageId);
                            if (config != null) {
                                this.openSourceRename(sourcePageId, config, pickerX, srcY + tabH + 2);
                                return true;
                            }
                            return false;
                        }
                        if (button == 0) {
                            if (this.sourceIds.size() > 1 && mx < pickerX + 20) {
                                this.closePickerDropdowns();
                                this.cycleSourcePicker(-1);
                            } else if (this.sourceIds.size() > 1 && mx >= pickerX + pickerW - 20) {
                                this.closePickerDropdowns();
                                this.cycleSourcePicker(1);
                            } else if (this.sourceDropdownOpen) {
                                this.closePickerDropdowns();
                            } else {
                                this.openSourceDropdown();
                            }
                            return true;
                        }
                    }
                    if (showVehicle && mx >= vehicleX && mx < vehicleX + vehicleW) {
                        if (button == 0) {
                            this.selectVehicleSettingsPage();
                            return true;
                        }
                        return false;
                    }
                    if (showFlightDeck && mx >= flightDeckX && mx < flightDeckX + flightDeckW) {
                        if (button == 0) {
                            this.selectFlightDeckPage();
                            return true;
                        }
                        return false;
                    }
                    if (showGuidance && mx >= guidanceX && mx < guidanceX + guidanceW) {
                        if (button == 0) {
                            this.selectGuidancePage();
                            return true;
                        }
                        return false;
                    }
                    if (showTrain && mx >= trainX && mx < trainX + trainW) {
                        if (button == 0) {
                            this.selectTrainPage();
                            return true;
                        }
                        return false;
                    }
                    if (showPower && mx >= powerX && mx < powerX + powerW) {
                        if (button == 0) {
                            this.selectPowerPage();
                            return true;
                        }
                        return false;
                    }
                    if (mx >= factoryX && mx < factoryX + factoryW) {
                        if (button == 0) {
                            this.selectFactoryPage();
                            return true;
                        }
                        return false;
                    }
                }
            }
            if (button == 0 && this.shouldShowChartControls()) {
                final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
                final int sourceBarH = multiSource ? 20 : 0;
                final int tabY = this.panelTop + 22 + sourceBarH;
                final int tabH2 = this.tabBarHeight - 4;
                final int left = this.panelLeft + 8;
                final int right = this.panelLeft + this.panelWidth - 8;
                final int overviewW = Math.max(72, this.font.width("Overview") + 18);
                if (mx >= left && mx < left + overviewW && my >= tabY && my < tabY + tabH2) {
                    this.closePickerDropdowns();
                    this.selectChannelFromPicker(-1);
                    return true;
                }
                final int pickerX = left + overviewW + 6;
                final int pickerW = Math.max(80, right - pickerX);
                if (mx >= pickerX && mx < pickerX + pickerW && my >= tabY && my < tabY + tabH2) {
                    if (!channels.isEmpty()) {
                        if (channels.size() > 1 && mx < pickerX + 22) {
                            this.closePickerDropdowns();
                            this.cycleChannelPicker(-1);
                        } else if (channels.size() > 1 && mx >= pickerX + pickerW - 22) {
                            this.closePickerDropdowns();
                            this.cycleChannelPicker(1);
                        } else if (this.signalDropdownOpen) {
                            this.closePickerDropdowns();
                        } else {
                            this.openSignalDropdown(channels);
                        }
                        return true;
                    }
                    return true;
                }
            }
        }
        if (this.shouldShowChartControls() && button == 1 && mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= this.graphTop && my < this.graphTop + this.graphHeight) {
            final Annotation clicked = this.findAnnotationAtPixel((int)mx);
            if (clicked != null) {
                PacketDistributor.sendToServer(AnnotationActionPacket.remove(this.panel.getBlockPos(), clicked.getId()));
                this.panel.getAnnotationManager().remove(clicked.getId());
                DisplayTextureManager.markDirty(this.panel.getBlockPos());
                return true;
            }
        }
        if (this.shouldShowChartControls() && this.annotateMode && button == 0 && !this.annotationInputOpen && mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= this.graphTop && my < this.graphTop + this.graphHeight) {
            this.handleAnnotationClick(mx, my);
            return true;
        }
        if (this.isInventoryStatusPage() && button == 0) {
            if (mx >= this.prodSortBtnX && mx <= this.prodSortBtnX + this.prodSortBtnW && my >= this.prodSortBtnY && my <= this.prodSortBtnY + this.prodSortBtnH) {
                this.inventorySortMode = (this.inventorySortMode + 1) % 2;
                return true;
            }

            if (mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth) {
                for (InventoryStorageRowHit shit : this.inventoryStorageHits) {
                    if (my >= shit.yMin && my < shit.yMax && shit.sourceId != null) {
                        if (this.inventoryExpandedSources.contains(shit.sourceId)) {
                            this.inventoryExpandedSources.remove(shit.sourceId);
                        } else {
                            this.inventoryExpandedSources.add(shit.sourceId);
                        }
                        return true;
                    }
                }
                final List<StatsDisplayPanelBE.DataChannel> filteredChannels2 = this.getFilteredChannels();
                for (ProductionRowHit hit : this.prodRowHits) {
                    if (my >= hit.yMin && my < hit.yMax && hit.entry != null && hit.entry.channel != null) {
                        for (int ci = 0; ci < filteredChannels2.size(); ++ci) {
                            if (filteredChannels2.get(ci).getId().equals(hit.entry.channel.getId())) {
                                this.selectedTab = ci;
                                this.viewType = StatsDisplaySettings.GraphType.LINE_CHART;
                                this.rebuildWidgets();
                                return true;
                            }
                        }
                    }
                }
            }
        }
        if (this.isFactoryStatusPage() && button == 0) {
            if (mx >= this.prodSortBtnX && mx <= this.prodSortBtnX + this.prodSortBtnW && my >= this.prodSortBtnY && my <= this.prodSortBtnY + this.prodSortBtnH) {
                this.factoryStatusSortMode = (this.factoryStatusSortMode + 1) % 3;
                return true;
            }
        }
        if (this.shouldShowChartControls() && this.viewType == StatsDisplaySettings.GraphType.TOPOLOGY && button == 0 && mx >= this.topologyExpandBtnX && mx <= this.topologyExpandBtnX + this.topologyExpandBtnW && my >= this.topologyExpandBtnY && my <= this.topologyExpandBtnY + this.topologyExpandBtnH + 2) {
            this.minecraft.setScreen((Screen)new TopologyPopupScreen(this.panel, this));
            return true;
        }
        if (this.isTrainPage()) {
            if (button == 0 && mx >= this.trainExpandBtnX && mx <= this.trainExpandBtnX + this.trainExpandBtnW && my >= this.trainExpandBtnY && my <= this.trainExpandBtnY + this.trainExpandBtnH + 2) {
                this.minecraft.setScreen((Screen)new TrainMapScreen(this.panel, this));
                return true;
            }
            if (button == 0 && mx >= this.trainQualityBtnX && mx <= this.trainQualityBtnX + this.trainQualityBtnW && my >= this.trainQualityBtnY && my <= this.trainQualityBtnY + this.trainQualityBtnH) {
                this.panel.getSettings().cycleTrainMapRenderQuality();
                this.applyPanelSettingsChange();
                this.rebuildWidgets();
                return true;
            }
            if (button == 0 && this.trainDashboardDimensions.size() > 1) {
                if (mx >= this.trainDashboardDimPrevX && mx < this.trainDashboardDimPrevX + this.trainDashboardDimPrevW && my >= this.trainDashboardDimPrevY && my < this.trainDashboardDimPrevY + this.trainDashboardDimPrevH) {
                    this.cycleTrainDashboardDimension(-1);
                    return true;
                }
                if (mx >= this.trainDashboardDimNextX && mx < this.trainDashboardDimNextX + this.trainDashboardDimNextW && my >= this.trainDashboardDimNextY && my < this.trainDashboardDimNextY + this.trainDashboardDimNextH) {
                    this.cycleTrainDashboardDimension(1);
                    return true;
                }
            }
            if (button == 0 && this.handleTrainDashboardClick(mx, my)) {
                return true;
            }
        }
        if (this.shouldShowChartControls() && this.viewType == StatsDisplaySettings.GraphType.EFFICIENCY && button == 0 && mx >= this.effFormulasBtnX && mx <= this.effFormulasBtnX + this.effFormulasBtnW && my >= this.effFormulasBtnY && my <= this.effFormulasBtnY + this.effFormulasBtnH + 2) {
            this.minecraft.setScreen((Screen)new EfficiencyFormulaScreen(this.panel, this, false));
            return true;
        }
        if (this.shouldShowChartControls() && this.viewType == StatsDisplaySettings.GraphType.EFFICIENCY && button == 0 && mx >= this.effLiveMathBtnX && mx <= this.effLiveMathBtnX + this.effLiveMathBtnW && my >= this.effLiveMathBtnY && my <= this.effLiveMathBtnY + this.effLiveMathBtnH + 2) {
            this.minecraft.setScreen((Screen)new EfficiencyFormulaScreen(this.panel, this, true));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (com.createstats.client.battle.BattleTacticalV2Viewport3D.mouseDragged(this.panel, mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (com.createstats.client.battle.BattleTacticalV2Viewport3D.mouseReleased(this.panel, mx, my, button)) {
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (com.createstats.client.battle.BattleTacticalV2Viewport3D.mouseScrolled(this.panel, mx, my, scrollY)) {
            return true;
        }
        if (this.scrollPickerDropdown(scrollY)) {
            return true;
        }
        if (this.handleFactoryPageScroll(mx, my, scrollY)) {
            return true;
        }
        if (this.handleFactoryStatusScroll(mx, my, scrollY)) {
            return true;
        }
        final boolean multiSource = this.getVisibleSourceTabCount() > 1;
        final int sourceRowY = this.panelTop + 22;
        if (multiSource && my >= sourceRowY && my < sourceRowY + 18.0) {
            if (scrollY > 0.0) {
                this.cycleSourcePicker(-1);
            }
            else if (scrollY < 0.0) {
                this.cycleSourcePicker(1);
            }
            return true;
        }
        final int sourceBarH = multiSource ? 20 : 0;
        final int tabY = this.panelTop + 22 + sourceBarH;
        if (this.shouldShowChartControls() && my >= tabY && my < tabY + this.tabBarHeight) {
            if (scrollY > 0.0) {
                this.cycleChannelPicker(-1);
            } else if (scrollY < 0.0) {
                this.cycleChannelPicker(1);
            }
            return true;
        }
        if (this.isHomePage() && this.handleHomePageScroll(mx, my, scrollY)) {
            return true;
        }
        if (this.isPowerPage() && this.powerPage.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (this.isTrainPage()) {
            if (this.handleTrainDashboardScroll(mx, my, scrollY)) {
                return true;
            }
        }
        if (this.isInventoryStatusPage()
                && my >= this.graphTop && my < this.graphTop + this.graphHeight) {
            this.inventoryScrollOffset = Math.max(0, this.inventoryScrollOffset - (int)scrollY);
            return true;
        }
        if (this.shouldShowChartControls() && this.showLog && my >= this.graphTop + this.graphHeight + 26) {
            this.logScrollOffset = Math.max(0, this.logScrollOffset - (int)scrollY);
            return true;
        }
        if (this.handleOptionPanelChannelListScroll(mx, my, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void renderDashboard(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels) {
        final int count = channels.size();
        if (count == 0) {
            return;
        }
        int cols;
        int rows;
        if (count <= 2) {
            cols = count;
            rows = 1;
        }
        else if (count <= 4) {
            cols = 2;
            rows = 2;
        }
        else if (count <= 6) {
            cols = 3;
            rows = 2;
        }
        else if (count <= 9) {
            cols = 3;
            rows = 3;
        }
        else {
            cols = 4;
            rows = (count + 3) / 4;
        }
        final int cellW = (this.graphWidth - (cols + 1) * 3) / cols;
        final int cellH = (this.graphHeight - (rows + 1) * 3) / rows;
        final int pad = 3;
        for (int i = 0; i < Math.min(count, cols * rows); ++i) {
            final StatsDisplayPanelBE.DataChannel ch = channels.get(i);
            final TimeSeriesStore store = ch.getStore();
            final int col = i % cols;
            final int row = i / cols;
            final int cx = this.graphLeft + pad + col * (cellW + pad);
            final int cy = this.graphTop + pad + row * (cellH + pad);
            final int color = StatsDisplayScreen.CHANNEL_COLORS[i % StatsDisplayScreen.CHANNEL_COLORS.length];
            g.fill(cx, cy, cx + cellW, cy + cellH, -15790304);
            this.border(g, cx, cy, cellW, cellH, -13421739);
            final String label = this.truncate(ch.getLabel(), cellW - 6);
            g.drawString(this.font, label, cx + 3, cy + 2, color);
            final ItemStack icon = this.resolveItemIcon(ch.getLabel());
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate((float)(cx + cellW - 14), (float)(cy + 1), 0.0f);
                g.pose().scale(0.625f, 0.625f, 1.0f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }
            if (store.getSampleCount() > 0) {
                final String val = store.getMetricType().formatValue(store.getLatestValue());
                int valX = cx + cellW - this.font.width(val) - 3;
                if (!icon.isEmpty()) {
                    valX -= 12;
                }
                g.drawString(this.font, val, valX, cy + 2, this.TEXT);
                float[] data = store.getDataForTimeScale(120);
                if (data.length >= 2) {
                    final int chartY = cy + 14;
                    final int chartH = cellH - 18;
                    if (chartH > 8) {
                        final int maxPts = cellW - 6;
                        if (data.length > maxPts) {
                            data = downsample(data, maxPts);
                        }
                        this.renderMiniLineChart(g, data, cx + 3, chartY, cellW - 6, chartH, color);
                    }
                }
            }
            else {
                g.drawCenteredString(this.font, "--", cx + cellW / 2, cy + cellH / 2 - 4, this.DIM);
            }
        }
        if (count > cols * rows) {
            g.drawString(this.font, "+" + (count - cols * rows) + " more...", this.graphLeft + 4, this.graphTop + this.graphHeight - 12, this.DIM);
        }
    }

    private void renderMiniLineChart(GuiGraphics g, float[] data, int x, int y, int w, int h, int color) {
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        final float avgVal = (min + max) / 2.0f;
        final float minRange = Math.max(Math.abs(avgVal) * 0.05f, 1.0f);
        if (range < minRange) {
            range = minRange;
            min = avgVal - range / 2.0f;
        }
        final int n = data.length;
        final int fillColor = (color & 0xFFFFFF) | 0x15000000;
        int prevPx = -1;
        int prevPy = -1;
        for (int i = 0; i < n; ++i) {
            final int px = x + (int)(i / (float)(n - 1) * (w - 1));
            int py = y + h - 1 - (int)((data[i] - min) / range * (h - 2));
            py = Math.max(y, Math.min(y + h - 1, py));
            if (prevPx >= 0) {
                this.drawThickLine(g, prevPx, prevPy, px, py, color, 1);
            }
            g.fill(px, py, px + 1, y + h, fillColor);
            prevPx = px;
            prevPy = py;
        }
    }

    private static float[] downsample(float[] data, int targetLen) {
        if (data.length <= targetLen || targetLen < 2) {
            return data;
        }
        final float[] out = new float[targetLen];
        final float bucketSize = data.length / (float)targetLen;
        for (int i = 0; i < targetLen; ++i) {
            final int start = (int)(i * bucketSize);
            final int end = Math.min((int)((i + 1) * bucketSize), data.length);
            final int mid = (start + end) / 2;
            out[i] = data[Math.min(mid, data.length - 1)];
        }
        return out;
    }

    private void renderChannelGraph(GuiGraphics g, StatsDisplayPanelBE.DataChannel channel, int color, int mx, int my) {
        final TimeSeriesStore store = channel.getStore();
        final MetricType mtype = store.getMetricType();
        if (channel.isAlertTriggered()) {
            final long tick = System.currentTimeMillis() / 50L;
            final int alpha = (int)(24.0 + 24.0 * Math.abs(Math.sin(tick * 0.1)));
            final int pulseColor = alpha << 24 | 0xFF0000;
            g.fill(this.graphLeft + 1, this.graphTop + 1, this.graphLeft + this.graphWidth - 1, this.graphTop + this.graphHeight - 1, pulseColor);
        }
        String headerLabel = channel.getLabel();
        if (channel.isAlertTriggered()) {
            headerLabel = "\u26a0 " + headerLabel;
        }
        g.drawString(this.font, headerLabel, this.graphLeft, this.panelTop + 28 + this.tabBarHeight - 14, channel.isAlertTriggered() ? this.WARN : this.DIM);
        final String val = (store.getSampleCount() > 0) ? mtype.formatValue(store.getLatestValue()) : "--";
        g.drawString(this.font, val, this.panelLeft + this.panelWidth - 10 - this.font.width(val), this.panelTop + 28 + this.tabBarHeight - 14, this.ACCENT);
        final float[] data = (this.timeScale > 0) ? store.getDataForTimeScale(this.timeScale) : store.getData();
        if (data.length < 2) {
            g.drawCenteredString(this.font, "Collecting data...", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 4, this.DIM);
            return;
        }
        final String tsLabel = (this.timeScale > 0) ? ("Window: " + this.formatTimeDuration(this.timeScale)) : ("Window: All (" + this.formatTimeDuration(store.getDurationSeconds()) + ")");
        g.drawString(this.font, tsLabel, this.panelLeft + this.panelWidth - this.font.width(tsLabel) - 10, this.panelTop + 28 + this.tabBarHeight - 2, -12303258);
        switch (this.viewType) {
            case LINE_CHART: {
                this.renderLineChart(g, data, color, channel, mx, my);
                final List<StatsDisplayPanelBE.DataChannel> allCh = this.getFilteredChannels();
                for (int pinIdx : this.pinnedChannels) {
                    if (pinIdx >= 0 && pinIdx < allCh.size() && pinIdx != this.selectedTab) {
                        final StatsDisplayPanelBE.DataChannel pinCh = allCh.get(pinIdx);
                        final float[] pinData = (this.timeScale > 0) ? pinCh.getStore().getDataForTimeScale(this.timeScale) : pinCh.getStore().getData();
                        if (pinData.length < 2) {
                            continue;
                        }
                        final int pinColor = StatsDisplayScreen.CHANNEL_COLORS[pinIdx % StatsDisplayScreen.CHANNEL_COLORS.length];
                        final int translucentColor = (pinColor & 0xFFFFFF) | 0x66000000;
                        this.renderOverlayLineChart(g, pinData, translucentColor, pinCh);
                    }
                }
                if (!this.pinnedChannels.isEmpty()) {
                    this.renderDualYAxis(g, channel, allCh);
                }
                break;
            }
            case BAR_CHART: {
                this.renderBarChart(g, data, color, mtype);
                final List<StatsDisplayPanelBE.DataChannel> allChBar = this.getFilteredChannels();
                for (int pinIdx : this.pinnedChannels) {
                    if (pinIdx >= 0 && pinIdx < allChBar.size() && pinIdx != this.selectedTab) {
                        final StatsDisplayPanelBE.DataChannel pinCh = allChBar.get(pinIdx);
                        final float[] pinData = (this.timeScale > 0) ? pinCh.getStore().getDataForTimeScale(this.timeScale) : pinCh.getStore().getData();
                        if (pinData.length < 2) {
                            continue;
                        }
                        final int pinColor = StatsDisplayScreen.CHANNEL_COLORS[pinIdx % StatsDisplayScreen.CHANNEL_COLORS.length];
                        final int translucentColor = (pinColor & 0xFFFFFF) | 0x55000000;
                        this.renderOverlayBarChart(g, pinData, translucentColor);
                    }
                }
                break;
            }
            case GAUGE: {
                this.renderGauge(g, store, color);
                break;
            }
            case NUMERIC: {
                this.renderNumeric(g, store, mtype);
            }
        }
        if (channel.isAlertEnabled() && (this.viewType == StatsDisplaySettings.GraphType.LINE_CHART || this.viewType == StatsDisplaySettings.GraphType.BAR_CHART)) {
            this.drawAlertThresholdLine(g, data, channel);
        }
    }

    private void renderAnalogGaugeGUI(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels.isEmpty()) {
            return;
        }
        final StatsDisplaySettings settings = this.panel.getSettings();
        final int count = channels.size();
        int cols = 1;
        int rows = count;
        int bestScore = Integer.MIN_VALUE;
        for (int candidateCols = 1; candidateCols <= Math.min(count, 4); ++candidateCols) {
            final int candidateRows = (count + candidateCols - 1) / candidateCols;
            final int cellW = Math.max(1, this.graphWidth / candidateCols);
            final int cellH = Math.max(1, this.graphHeight / candidateRows);
            final int ratioPenalty = Math.abs(cellW - cellH * 4 / 3);
            final int score = Math.min(cellW, cellH) * 4 + cellW - ratioPenalty - candidateRows * 8;
            if (score > bestScore) {
                bestScore = score;
                cols = candidateCols;
                rows = candidateRows;
            }
        }
        if (count == 5 && this.graphWidth >= this.graphHeight) {
            cols = 3;
            rows = 2;
        }
        final int gap = Math.max(5, Math.min(this.graphWidth, this.graphHeight) / 52);
        final int pad = Math.max(5, Math.min(this.graphWidth, this.graphHeight) / 44);
        final int gridX = this.graphLeft + pad;
        final int gridY = this.graphTop + pad;
        final int gridW = Math.max(1, this.graphWidth - pad * 2);
        final int gridH = Math.max(1, this.graphHeight - pad * 2);
        final int cellW = Math.max(24, (gridW - gap * (cols - 1)) / cols);
        final int cellH = Math.max(32, (gridH - gap * (rows - 1)) / rows);
        final int lastRowCount = count - (rows - 1) * cols;
        final long easeNowMs = System.currentTimeMillis();
        float easeDt = (this.needleEaseLastMs == 0L) ? 0.016f : (easeNowMs - this.needleEaseLastMs) / 1000.0f;
        easeDt = Math.max(0.0f, Math.min(0.1f, easeDt));
        this.needleEaseLastMs = easeNowMs;
        final float easeAlpha = 1.0f - (float)Math.exp(-easeDt / 0.12f);
        if (this.dashOpenMs == 0L) {
            this.dashOpenMs = easeNowMs;
        }

        final float introT = Math.min(1.0f, (easeNowMs - this.dashOpenMs) / 900.0f);
        for (int i = 0; i < channels.size(); ++i) {
            final StatsDisplayPanelBE.DataChannel ch = channels.get(i);
            final TimeSeriesStore store = ch.getStore();
            final int col = i % cols;
            final int row = i / cols;
            int rowOffset = 0;
            if (row == rows - 1 && lastRowCount > 0 && lastRowCount < cols) {
                rowOffset = (cols - lastRowCount) * (cellW + gap) / 2;
            }
            final int cardX = gridX + rowOffset + col * (cellW + gap);
            final int cardY = gridY + row * (cellH + gap);
            final int accentSafe = 0xFF2FB36D;
            final int accentWarn = 0xFFE3A43B;
            final int accentDanger = 0xFFD9594C;
            float min = settings.getAnalogGaugeMin();
            float max = settings.getAnalogGaugeMax();
            if (!(min < max)) {
                final float[] fixedRange = com.createstats.display.MetricContract.fixedRange(store.getMetricType());
                if (fixedRange != null) {
                    min = fixedRange[0];
                    max = fixedRange[1];
                } else {
                    min = store.getMin();
                    max = store.getMax();
                    float autoRange = max - min;
                    if (autoRange < 0.001f) {
                        autoRange = Math.max(1.0f, Math.abs(min) * 0.1f + 1.0f);
                    }
                    min = (store.getMin() >= 0.0f) ? 0.0f : (min - autoRange * 0.1f);
                    max += autoRange * 0.1f;
                }
            }
            final float latest = store.getLatestValue();
            float range = max - min;
            if (range < 0.001f) {
                max = min + 1.0f;
                range = 1.0f;
            }
            float normalized = (latest - min) / range;
            normalized = Math.max(0.0f, Math.min(1.0f, normalized));

            final String easeKey = ch.getId();
            final Float prevDisp = this.needleEase.get(easeKey);
            float displayNorm = (prevDisp == null) ? normalized : prevDisp + (normalized - prevDisp) * easeAlpha;
            if (Math.abs(displayNorm - normalized) < 0.0005f) {
                displayNorm = normalized;
            }
            this.needleEase.put(easeKey, displayNorm);
            float renderNorm = displayNorm;
            if (introT < 1.0f) {
                if (introT < 0.45f) {
                    final float u = introT / 0.45f;
                    renderNorm = 1.0f - (1.0f - u) * (1.0f - u);
                }
                else {
                    final float u = (introT - 0.45f) / 0.55f;
                    final float s = u * u * (3.0f - 2.0f * u);
                    renderNorm = 1.0f + (normalized - 1.0f) * s;
                }
            }
            int accent = ch.isAlertTriggered()
                    ? accentDanger
                    : (normalized < 0.68f ? this.blendColor(accentSafe, accentWarn, normalized / 0.68f) : this.blendColor(accentWarn, accentDanger, (normalized - 0.68f) / 0.32f));
            if (ch.isAlertTriggered()) {
                final float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 700.0 * Math.PI);
                accent = this.blendColor(accentDanger, 0xFFFF7A6A, pulse);
            }
            g.fill(cardX, cardY, cardX + cellW, cardY + cellH, darken(this.GRAPH_BG, 0.82f));
            this.drawGUIBorder(g, cardX, cardY, cellW, cellH, darken(this.BORDER, 0.9f));
            g.fill(cardX + 1, cardY + 1, cardX + cellW - 1, cardY + 4, accent);

            final boolean showSpark = cellW >= 130 && store.getSampleCount() >= 2 && range > 0.0f;
            final int sparkW = showSpark ? Math.min(cellW / 2 - 12, 70) : 0;
            final int labelMax = showSpark ? Math.max(8, cellW - sparkW - 20) : Math.max(8, cellW - 12);
            g.drawString(this.font, this.truncate(ch.getLabel(), labelMax), cardX + 6, cardY + 7, this.TEXT);
            if (showSpark && sparkW >= 24) {
                final int sparkH = 11;
                final int sparkX = cardX + cellW - 8 - sparkW;
                final int sparkY = cardY + 5;
                g.fill(sparkX, sparkY, sparkX + sparkW, sparkY + sparkH, darken(this.GRAPH_BG, 0.6f));
                this.drawGUIBorder(g, sparkX, sparkY, sparkW, sparkH, darken(this.BORDER, 0.8f));
                final float avgFrac = Math.max(0.0f, Math.min(1.0f, (store.getAverage() - min) / range));
                final int avgY = sparkY + sparkH - 1 - (int)(avgFrac * (sparkH - 2));
                for (int sx = sparkX + 1; sx < sparkX + sparkW - 1; sx += 2) {
                    g.fill(sx, avgY, sx + 1, avgY + 1, 0x553A7A8C);
                }
                final float[] sd = store.getDataWindow(sparkW - 2);
                final int sn = sd.length;
                if (sn >= 2) {
                    int psx = -1;
                    int psy = -1;
                    for (int j = 0; j < sn; ++j) {
                        final int xx = sparkX + 1 + (int)(j / (float)(sn - 1) * (sparkW - 3));
                        final float vv = Math.max(min, Math.min(max, sd[j]));
                        final int yy = sparkY + sparkH - 1 - (int)((vv - min) / range * (sparkH - 2));
                        if (psx >= 0) {
                            this.drawGaugeLine(g, psx, psy, xx, yy, brighten(accent, 1.05f));
                        }
                        psx = xx;
                        psy = yy;
                    }
                    g.fill(psx - 1, psy - 1, psx + 1, psy + 1, 0xFFFFFFFF);
                }
            }

            final int headerH = 20;
            final int readoutH = Math.max(20, Math.min(30, cellH / 5));
            final int dialH = Math.max(24, cellH - headerH - readoutH - 10);
            final int cx = cardX + cellW / 2;
            final int cy = cardY + headerH + dialH / 2 + Math.max(0, dialH / 14);
            final int gaugeR = Math.max(14, Math.min((cellW - 14) / 2, dialH / 2) - 1);
            final float startAngle = 215.0f;
            final float sweep = 250.0f;
            this.drawArc(g, cx + 1, cy + 1, gaugeR + 1, startAngle, startAngle - sweep, darken(this.GRAPH_BG, 0.52f), 3);
            this.drawArc(g, cx, cy, gaugeR, startAngle, startAngle - sweep, darken(this.BORDER, 0.55f), 2);
            if (settings.isAnalogShowZones()) {
                final float[] contractBands = com.createstats.display.MetricContract.bands(store.getMetricType());
                final float yellowFrac;
                final float redFrac;
                if (contractBands != null) {
                    yellowFrac = Math.max(0.0f, Math.min(1.0f, contractBands[0]));
                    redFrac = Math.max(yellowFrac, Math.min(1.0f, contractBands[1]));
                } else {
                    yellowFrac = Math.max(0.0f, Math.min(1.0f, settings.getAnalogZoneYellow()));
                    redFrac = Math.max(yellowFrac, Math.min(1.0f, settings.getAnalogZoneRed()));
                }
                this.drawArc(g, cx, cy, gaugeR, startAngle, startAngle - sweep * yellowFrac, darken(accentSafe, 0.82f), 3);
                this.drawArc(g, cx, cy, gaugeR, startAngle - sweep * yellowFrac, startAngle - sweep * redFrac, darken(accentWarn, 0.82f), 3);
                this.drawArc(g, cx, cy, gaugeR, startAngle - sweep * redFrac, startAngle - sweep, darken(accentDanger, 0.84f), 3);
            }
            else {
                this.drawArc(g, cx, cy, gaugeR, startAngle, startAngle - sweep, this.ACCENT, 3);
            }
            final int liveRadius = Math.max(1, gaugeR - 7);
            final float liveEnd = startAngle - sweep * renderNorm;
            this.drawArc(g, cx, cy, liveRadius, startAngle, liveEnd, brighten(accent, 1.08f), 3);
            this.drawArcCap(g, cx, cy, liveRadius, liveEnd, 3, accent);
            this.drawArcCap(g, cx, cy, gaugeR, startAngle, 3, darken(accentSafe, 0.92f));
            this.drawArcCap(g, cx, cy, gaugeR, startAngle - sweep, 3, darken(accentDanger, 0.92f));
            for (int t = 0; t <= 8; ++t) {
                final float tickAngle = (float)Math.toRadians(startAngle - sweep * t / 8.0f);
                final int outerX = cx + (int)(Math.cos(tickAngle) * (gaugeR + 1));
                final int outerY = cy - (int)(Math.sin(tickAngle) * (gaugeR + 1));
                final int innerX = cx + (int)(Math.cos(tickAngle) * (gaugeR - (t % 2 == 0 ? 9 : 6)));
                final int innerY = cy - (int)(Math.sin(tickAngle) * (gaugeR - (t % 2 == 0 ? 9 : 6)));
                this.drawGaugeLine(g, innerX, innerY, outerX, outerY, this.DIM);
            }

            final float avgVal = store.getAverage();
            final float avgN = Math.max(0.0f, Math.min(1.0f, (avgVal - min) / range));
            final float avgAngle = startAngle - sweep * avgN;
            this.drawArcCap(g, cx, cy, gaugeR + 3, avgAngle, 2, darken(this.GRAPH_BG, 0.5f));
            this.drawArcCap(g, cx, cy, gaugeR + 3, avgAngle, 1, 0xFF5AC8E0);
            final float peakVal = store.getMax();
            final float peakN = Math.max(0.0f, Math.min(1.0f, (peakVal - min) / range));
            if (peakN > renderNorm + 0.02f) {
                final float peakRad = (float)Math.toRadians(startAngle - sweep * peakN);
                final int pInner = gaugeR - 1;
                final int pOuter = gaugeR + 4;
                this.drawGaugeLine(g,
                        cx + (int)(Math.cos(peakRad) * pInner), cy - (int)(Math.sin(peakRad) * pInner),
                        cx + (int)(Math.cos(peakRad) * pOuter), cy - (int)(Math.sin(peakRad) * pOuter),
                        0xFFD9594C);
            }
            final float needleAngle = (float)Math.toRadians(startAngle - sweep * renderNorm);
            final int needleLen = Math.max(4, gaugeR - 9);
            final int tailLen = Math.max(3, gaugeR / 6);
            final int nx = cx + (int)(Math.cos(needleAngle) * needleLen);
            final int ny = cy - (int)(Math.sin(needleAngle) * needleLen);
            final int tx = cx - (int)(Math.cos(needleAngle) * tailLen);
            final int ty = cy + (int)(Math.sin(needleAngle) * tailLen);
            this.drawGaugeLine(g, cx + 1, cy + 1, nx + 1, ny + 1, darken(this.GRAPH_BG, 0.42f));
            this.drawGaugeLine(g, tx, ty, cx, cy, darken(accent, 0.72f));
            this.drawGaugeLine(g, cx, cy, nx, ny, brighten(accent, 1.12f));
            this.drawGaugeLine(g, cx, cy, nx, ny, accent);
            g.fill(cx - 3, cy - 3, cx + 4, cy + 4, darken(this.GRAPH_BG, 0.55f));
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, darken(this.BORDER, 0.68f));
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, accent);
            final int scaleY = Math.min(cardY + cellH - readoutH - 12, cy + gaugeR / 2 + 2);
            g.drawString(this.font, this.formatCompactGauge(min), cardX + 6, scaleY, this.DIM);
            g.drawString(this.font, this.formatCompactGauge(max), cardX + cellW - 6 - this.font.width(this.formatCompactGauge(max)), scaleY, this.DIM);

            final int readoutY = cardY + cellH - readoutH - 5;
            g.fill(cardX + 6, readoutY, cardX + cellW - 6, readoutY + readoutH, darken(this.GRAPH_BG, 0.68f));
            this.drawGUIBorder(g, cardX + 6, readoutY, cellW - 12, readoutH, accent);
            final String valueText = store.getMetricType().formatValue(latest);
            g.drawCenteredString(this.font, this.truncate(valueText, Math.max(8, cellW - 30)), cx, readoutY + readoutH / 2 - 4, accent);

            final float trendRate = store.getAvgRateOfChange(8);
            final float trendDead = Math.max(1.0E-4f, range * 0.004f);
            final String trendArrow = (trendRate > trendDead) ? "\u2191" : ((trendRate < -trendDead) ? "\u2193" : "\u2192");
            final int trendColor = (trendRate > trendDead) ? 0xFF6FC8E0 : ((trendRate < -trendDead) ? 0xFFE0A86F : this.DIM);
            g.drawString(this.font, trendArrow, cardX + 10, readoutY + readoutH / 2 - 4, trendColor);
            final MetricType mt = store.getMetricType();
            if (mt != MetricType.GENERIC && readoutH >= 26) {
                g.drawCenteredString(this.font, this.truncate(mt.getUnit(), Math.max(8, cellW - 18)), cx, readoutY + readoutH - 10, this.DIM);
            }
        }
    }

    private void drawArc(GuiGraphics g, int cx, int cy, int radius, float fromDeg, float toDeg, int color, int thickness) {
        final float step = (fromDeg > toDeg) ? -2.0f : 2.0f;
        final int steps = (int)(Math.abs(fromDeg - toDeg) / 2.0f);
        float angle = fromDeg;
        for (int i = 0; i <= steps; ++i) {
            final float rad = (float)Math.toRadians(angle);
            final int x = cx + (int)(Math.cos(rad) * radius);
            final int y = cy - (int)(Math.sin(rad) * radius);
            g.fill(x - thickness / 2, y - thickness / 2, x + thickness / 2 + 1, y + thickness / 2 + 1, color);
            angle += step;
        }
    }

    private void drawArcCap(GuiGraphics g, int cx, int cy, int radius, float angleDeg, int radiusPx, int color) {
        final float rad = (float)Math.toRadians(angleDeg);
        final int x = cx + (int)(Math.cos(rad) * radius);
        final int y = cy - (int)(Math.sin(rad) * radius);
        g.fill(x - radiusPx, y - radiusPx, x + radiusPx + 1, y + radiusPx + 1, color);
    }

    private void drawGaugeLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        final float dx = (float)(x2 - x1);
        final float dy = (float)(y2 - y1);
        final float steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps < 1.0f) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= (int)steps; ++i) {
            final int x3 = x1 + (int)(dx * i / steps);
            final int y3 = y1 + (int)(dy * i / steps);
            g.fill(x3, y3, x3 + 1, y3 + 1, color);
        }
    }

    private String formatCompactGauge(float value) {
        if (Math.abs(value) >= 1000000.0f) {
            return String.format("%.1fM", value / 1000000.0f);
        }
        if (Math.abs(value) >= 1000.0f) {
            return String.format("%.1fk", value / 1000.0f);
        }
        if (Math.abs(value) >= 10.0f) {
            return String.format("%.0f", value);
        }
        if (Math.abs(value) >= 1.0f) {
            return String.format("%.1f", value);
        }
        return String.format("%.2f", value);
    }

    private void renderEfficiencyGUI(GuiGraphics g) {
        final EfficiencyScore score = this.panel.getLatestEfficiencyScore();
        final GradeHistory history = this.panel.getGradeHistory();
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        if (score == null || "?".equals(score.grade())) {
            g.drawCenteredString(this.font, "\u00a7lNetwork Health", x + w / 2, y + 4, this.ACCENT);
            g.fill(x + 20, y + 20, x + w - 20, y + h - 20, -15658715);
            this.drawGUIBorder(g, x + 20, y + 20, w - 40, h - 40, -14013884);
            g.drawCenteredString(this.font, "Awaiting network data...", x + w / 2, y + h / 2 - 20, this.DIM);
            g.drawCenteredString(this.font, "\u00a77Requirements:", x + w / 2, y + h / 2, -10066296);
            g.drawCenteredString(this.font, "\u00a77\u2022 Link a Stressometer and pick Stats Link output", x + w / 2, y + h / 2 + 14, -11184777);
            g.drawCenteredString(this.font, "\u00a77\u2022 Give it a short warmup to learn the load", x + w / 2, y + h / 2 + 26, -11184777);
            g.drawCenteredString(this.font, "\u00a77\u2022 Health keeps updating in the background once live", x + w / 2, y + h / 2 + 38, -11184777);
            return;
        }
        final int zoneAW = (int)(w * 0.32f);
        final int zoneBX = x + zoneAW + 8;
        final int zoneBW = w - zoneAW - 12;
        final int gradeColor = score.gradeColor();
        final String gradeStr = score.grade();
        g.pose().pushPose();
        final float gradeScale = 4.0f;
        final float gradeX = x + zoneAW / 2.0f;
        final float gradeY = (float)(y + 10);
        g.pose().translate(gradeX, gradeY, 0.0f);
        g.pose().scale(gradeScale, gradeScale, 1.0f);
        g.drawCenteredString(this.font, gradeStr, 0, 0, gradeColor);
        g.pose().popPose();
        final String scoreText = String.format("%.1f / 100", score.compositeScore());
        g.drawCenteredString(this.font, scoreText, x + zoneAW / 2, y + 52, this.TEXT);
        final String desc = this.getGradeDescription(score.grade());
        g.drawCenteredString(this.font, "\u00a77" + desc, x + zoneAW / 2, y + 66, this.DIM);
        if (history != null) {
            final String changeText = history.getSessionChangeText();
            if (changeText != null) {
                final int changeColor = history.hasImproved() ? -12255352 : -39356;
                g.drawCenteredString(this.font, (history.hasImproved() ? "\u25b2 " : "\u25bc ") + changeText, x + zoneAW / 2, y + 80, changeColor);
            }
            if (history.getBestGrade() != null) {
                g.drawCenteredString(this.font, "\u00a77Best: \u00a7a" + history.getBestGrade() + "\u00a77  Worst: \u00a7c" + ((history.getCurrentGrade() != null) ? history.getCurrentGrade() : ","), x + zoneAW / 2, y + 94, this.DIM);
            }
        }
        g.drawString(this.font, "\u00a7lHealth Breakdown", zoneBX, y + 2, this.ACCENT);

        final String qualifier = score.isWarmingUp()
                ? String.format("\u00a78warming up %.0f%%", score.warmup() * 100.0f)
                : (score.isPartial()
                        ? String.format("\u00a78%.0f%% of model measured", score.observability() * 100.0f)
                        : "");
        if (!qualifier.isEmpty()) {
            g.drawString(this.font, qualifier, zoneBX + this.font.width("Health Breakdown") + 8, y + 2, -8355712);
        }
        final EfficiencyDimension[] dims = EfficiencyDimension.values();
        final int barStartY = y + 16;
        final int barHeight = 12;
        final int barSpacing = Math.min(28, (h - 40) / dims.length);
        final int maxBarW = zoneBW - 90;
        for (int i = 0; i < dims.length; ++i) {
            final EfficiencyDimension dim = dims[i];
            final float pct = score.getDimensionPercent(dim);
            final boolean known = score.isKnown(dim);
            final int barY = barStartY + i * barSpacing;
            final String dimLabel = dim.getDisplayName();
            final String weightLabel = String.format("(%.0f%%)", dim.getWeight() * 100.0f);
            g.drawString(this.font, dimLabel, zoneBX, barY + 1, known ? this.TEXT : this.DIM);
            g.drawString(this.font, weightLabel, zoneBX + this.font.width(dimLabel) + 3, barY + 1, this.DIM);
            final int barX = zoneBX + 80;
            g.fill(barX, barY, barX + maxBarW, barY + barHeight, -15066578);
            this.drawGUIBorder(g, barX, barY, maxBarW, barHeight, -14013884);
            if (!known) {
                g.drawString(this.font, "§8no data", barX + 4, barY + 1, -8355712);
                continue;
            }
            final int fillW = (int)(maxBarW * pct / 100.0f);
            int barColor = dim.getBarColor();
            if (pct < 30.0f) {
                barColor = this.blendColor(barColor, -12303292, 0.5f);
            }
            g.fill(barX, barY, barX + fillW, barY + barHeight, barColor);
            final String pctText = String.format("%.0f%%", pct);
            g.drawString(this.font, pctText, barX + maxBarW + 4, barY + 1, (pct >= 80.0f) ? -12255352 : ((pct >= 50.0f) ? -13312 : -39356));
        }
        final int sparkY = barStartY + dims.length * barSpacing + 4;
        final int sparkH = Math.max(20, y + h - sparkY - 4);
        if (history != null && sparkH > 15) {
            final float[] sparkData = history.getSparklineData(Math.min(w - 20, 200));
            if (sparkData != null && sparkData.length > 1) {
                g.drawString(this.font, "\u00a77Health History:", zoneBX, sparkY, this.DIM);
                final int sparkLineY = sparkY + 12;
                final int sparkLineH = Math.min(sparkH - 14, 30);
                g.fill(zoneBX, sparkLineY, zoneBX + zoneBW, sparkLineY + sparkLineH, -15921888);
                this.drawGUIBorder(g, zoneBX, sparkLineY, zoneBW, sparkLineH, -14013884);
                float sMin = Float.MAX_VALUE;
                float sMax = -3.4028235E38f;
                for (float v : sparkData) {
                    sMin = Math.min(sMin, v);
                    sMax = Math.max(sMax, v);
                }
                if (sMax - sMin < 1.0f) {
                    sMin -= 5.0f;
                    sMax += 5.0f;
                }
                for (int si = 1; si < sparkData.length; ++si) {
                    final float x1f = zoneBX + (si - 1) / (float)(sparkData.length - 1) * zoneBW;
                    final float y1f = sparkLineY + sparkLineH - (sparkData[si - 1] - sMin) / (sMax - sMin) * sparkLineH;
                    final float x2f = zoneBX + si / (float)(sparkData.length - 1) * zoneBW;
                    final float y2f = sparkLineY + sparkLineH - (sparkData[si] - sMin) / (sMax - sMin) * sparkLineH;
                    this.drawGaugeLine(g, (int)x1f, (int)y1f, (int)x2f, (int)y2f, this.ACCENT);
                }
            }
        }
        final int infoBtnY = y + 110;
        if (infoBtnY + 14 < y + h) {
            g.drawString(this.font, "\u00a77Built from load, margin, smoothness, and uptime.", x + 4, infoBtnY, -11184777);
            g.drawString(this.font, "\u00a77Healthy networks can run hard without flirting with overstress.", x + 4, infoBtnY + 10, -11184777);
            final String btnText = "\u00a7e\u00a7n[ \u2139 Health Guide ]";
            final int btnW = this.font.width("[ \u2139 Health Guide ]");
            final int btnX = x + 4;
            final int btnY = infoBtnY + 24;
            g.drawString(this.font, btnText, btnX, btnY, -13312);
            this.effFormulasBtnX = btnX;
            this.effFormulasBtnY = btnY;
            this.effFormulasBtnW = btnW;
            this.effFormulasBtnH = 10;
        }
    }

    private String getGradeDescription(String grade) {
        return com.createstats.efficiency.GradeScale.phraseFor(grade);
    }

    private int blendColor(int c1, int c2, float t) {
        final int a = (int)((c1 >> 24 & 0xFF) * (1.0f - t) + (c2 >> 24 & 0xFF) * t);
        final int r = (int)((c1 >> 16 & 0xFF) * (1.0f - t) + (c2 >> 16 & 0xFF) * t);
        final int gr = (int)((c1 >> 8 & 0xFF) * (1.0f - t) + (c2 >> 8 & 0xFF) * t);
        final int b = (int)((c1 & 0xFF) * (1.0f - t) + (c2 & 0xFF) * t);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    private void drawGUIBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderTopologyGUI(GuiGraphics g) {
        final Object graphObj = this.panel.getCachedNetworkGraph();
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        g.drawString(this.font, "\u00a7lNetwork Topology", x + 4, y + 4, this.ACCENT);
        if (graphObj instanceof NetworkGraph graph) {
            if (!graph.isEmpty()) {
                final int nodeCount = graph.getNodes().size();
                final int edgeCount = graph.getEdges().size();
                final boolean hasCapacity = graph.hasMeasuredCapacity();
                final float load = hasCapacity ? (graph.getTotalStress() / graph.getTotalCapacity()) : 0.0f;
                final int loadColor = hasCapacity ? ((load > 0.9f) ? -48060 : ((load > 0.7f) ? -21982 : -12255352)) : this.WARN;
                final int infoY = y + 18;
                g.drawString(this.font, String.format("\u00a77Nodes: \u00a7f%d", nodeCount), x + 4, infoY, this.TEXT);
                g.drawString(this.font, String.format("\u00a77Edges: \u00a7f%d", edgeCount), x + 80, infoY, this.TEXT);
                g.drawString(this.font, String.format("\u00a77Load: ", new Object[0]), x + 150, infoY, this.TEXT);
                g.drawString(this.font, hasCapacity ? String.format("%.0f%%", load * 100.0f) : "?", x + 177, infoY, loadColor);
                if (graph.isTruncated()) {
                    g.drawString(this.font, "\u00a76Truncated @" + nodeCount, x + 210, infoY, this.WARN);
                }
                else if (graph.isOverStressed()) {
                    g.drawString(this.font, "\u00a7c\u00a7lOVERSTRESSED", x + 210, infoY, -48060);
                }
                else if (!hasCapacity) {
                    g.drawString(this.font, graph.hasExplicitSource() ? "\u00a76Cap unknown" : "\u00a76Root inferred", x + 210, infoY, this.WARN);
                }
                final int previewX = x + 4;
                final int previewY = infoY + 16;
                final int previewW = w - 8;
                final int previewH = h - 58;
                g.fill(previewX, previewY, previewX + previewW, previewY + previewH, -15921888);
                this.drawGUIBorder(g, previewX, previewY, previewW, previewH, -14013884);
                final Map<BlockPos, TopologyNode> nodes = graph.getNodes();
                final TopologyFlowLayout.LayoutResult flowLayout = TopologyFlowLayout.layout(graph);
                final Map<BlockPos, int[]> posMap = new HashMap<BlockPos, int[]>();
                for (Map.Entry<BlockPos, float[]> entry : flowLayout.getPositions().entrySet()) {
                    final float[] pos = entry.getValue();
                    final int nx = previewX + 8 + (int)(pos[0] * Math.max(1, previewW - 16));
                    final int ny = previewY + 8 + (int)(pos[1] * Math.max(1, previewH - 16));
                    posMap.put(entry.getKey(), new int[] { nx, ny });
                }
                for (TopologyEdge edge : graph.getEdges()) {
                    final int[] from = posMap.get(edge.getFrom());
                    final int[] to = posMap.get(edge.getTo());
                    if (from != null && to != null) {
                        final boolean primary = flowLayout.isPrimaryEdge(edge);
                        final int edgeColor = primary ? ((Math.abs(edge.getSpeedRatio()) != 1.0f) ? -7838174 : -13417370) : 1719109782;
                        this.drawGaugeLine(g, from[0], from[1], to[0], to[1], edgeColor);
                    }
                }

                final float flowPhase = (System.currentTimeMillis() % 1400L) / 1400.0f;
                for (TopologyEdge edge : graph.getEdges()) {
                    if (!flowLayout.isPrimaryEdge(edge)) {
                        continue;
                    }
                    final int[] ffrom = posMap.get(edge.getFrom());
                    final int[] fto = posMap.get(edge.getTo());
                    if (ffrom == null || fto == null) {
                        continue;
                    }
                    for (int d = 0; d < 2; ++d) {
                        float t = flowPhase + d * 0.5f;
                        if (t >= 1.0f) {
                            t -= 1.0f;
                        }
                        final int fx = ffrom[0] + (int)((fto[0] - ffrom[0]) * t);
                        final int fy = ffrom[1] + (int)((fto[1] - ffrom[1]) * t);
                        g.fill(fx - 1, fy - 1, fx + 2, fy + 2, 0xCC8FE3FF);
                        g.fill(fx, fy, fx + 1, fy + 1, 0xFFEAF7FF);
                    }
                }
                for (Map.Entry<BlockPos, TopologyNode> entry : nodes.entrySet()) {
                    final TopologyNode node = entry.getValue();
                    final int[] pos = posMap.get(entry.getKey());
                    if (pos == null) {
                        continue;
                    }
                    int nColor;
                    if (node.isOverStressed()) {
                        nColor = -48060;
                    }
                    else if (node.isSource()) {
                        nColor = -12255352;
                    }
                    else if (node.isInferredRoot() || flowLayout.isPrimaryRoot(entry.getKey())) {
                        nColor = this.WARN;
                    }
                    else if (node.getType().isObserverLike()) {
                        nColor = -10040065;
                    }
                    else if (node.getType().isTransferOrgan()) {
                        nColor = (node.getType() == com.createstats.topology.NodeType.GEARBOX || node.getType() == com.createstats.topology.NodeType.GEARSHIFT || node.getType() == com.createstats.topology.NodeType.SPEED_CONTROLLER) ? -4487100 : -7829368;
                    }
                    else {
                        nColor = -12285697;
                    }
                    final int nr = (node.getType().getIconSize() >= 7) ? 4 : 3;
                    g.fill(pos[0] - nr, pos[1] - nr, pos[0] + nr, pos[1] + nr, nColor);
                    if (node.isOverStressed()) {
                        final float np = 0.5f + 0.5f * (float)Math.sin(System.currentTimeMillis() / 500.0 * Math.PI);
                        final int ring = ((int)(0x40 + np * 0xA0) << 24) | 0xD9594C;
                        final int rr = nr + 2;
                        g.fill(pos[0] - rr, pos[1] - rr, pos[0] + rr, pos[1] - rr + 1, ring);
                        g.fill(pos[0] - rr, pos[1] + rr - 1, pos[0] + rr, pos[1] + rr, ring);
                        g.fill(pos[0] - rr, pos[1] - rr, pos[0] - rr + 1, pos[1] + rr, ring);
                        g.fill(pos[0] + rr - 1, pos[1] - rr, pos[0] + rr, pos[1] + rr, ring);
                    }
                }
                final int btnY = previewY + previewH + 4;
                final String expandText = "\u00a7e\u00a7n[ Click here for full interactive view ]";
                final int expandW = this.font.width("[ Click here for full interactive view ]");
                final int expandX = x + w / 2 - expandW / 2;
                g.drawCenteredString(this.font, expandText, x + w / 2, btnY, -13312);
                this.topologyExpandBtnX = expandX;
                this.topologyExpandBtnY = btnY;
                this.topologyExpandBtnW = expandW;
                this.topologyExpandBtnH = 10;
                final int breakdownY = btnY + 14;
                if (breakdownY + 10 < y + h) {
                    int sources = 0;
                    int consumers = 0;
                    final float totalCap = graph.getTotalCapacity();
                    final float totalStress = graph.getTotalStress();
                    for (TopologyNode n : nodes.values()) {
                        if (n.isSourceLike()) {
                            ++sources;
                        }
                        else {
                            ++consumers;
                        }
                    }
                    if (graph.hasMeasuredCapacity()) {
                        g.drawCenteredString(this.font, String.format("\u00a7a%d sources \u00a77| \u00a7b%d consumers \u00a77| \u00a7f%.0f/%.0f SU", sources, consumers, totalStress, totalCap), x + w / 2, breakdownY, this.DIM);
                    }
                    else {
                        g.drawCenteredString(this.font, String.format("\u00a7a%d sources \u00a77| \u00a76%s \u00a77| \u00a7f%.0f SU", sources, graph.hasExplicitSource() ? "capacity unknown" : "root inferred", totalStress), x + w / 2, breakdownY, this.DIM);
                    }
                }
                return;
            }
        }
        g.fill(x + 20, y + 20, x + w - 20, y + h - 20, -15658715);
        this.drawGUIBorder(g, x + 20, y + 20, w - 40, h - 40, -14013884);
        g.drawCenteredString(this.font, "No kinetic network detected", x + w / 2, y + h / 2 - 24, this.DIM);
        g.drawCenteredString(this.font, "\u00a77Requirements:", x + w / 2, y + h / 2 - 8, -10066296);
        g.drawCenteredString(this.font, "\u00a77\u2022 Link a Stressometer/Speedometer with Stats Link", x + w / 2, y + h / 2 + 6, -11184777);
        g.drawCenteredString(this.font, "\u00a77\u2022 Nearby fallback scanning still works without a link", x + w / 2, y + h / 2 + 18, -11184777);
        g.drawCenteredString(this.font, "\u00a77\u2022 Raise Max Nodes if very large factories truncate", x + w / 2, y + h / 2 + 30, -11184777);
    }

    private void renderLineChart(GuiGraphics g, float[] data, int color, StatsDisplayPanelBE.DataChannel channel, int mx, int my) {
        final int n = data.length;
        final ChartRange chartRange = this.buildChartRange(data, true);

        final String key = channel.getId();
        final long now = System.currentTimeMillis();
        float dt = (this.lineTraceLastMs == 0L) ? 0.016f : (now - this.lineTraceLastMs) / 1000.0f;
        dt = Math.max(0.0f, Math.min(0.1f, dt));
        this.lineTraceLastMs = now;
        final float easeAlpha = 1.0f - (float) Math.exp(-dt / 0.18f);
        float[] er = this.lineRangeEase.get(key);
        if (er == null || !key.equals(this.lineTraceKey)) {
            er = new float[] { chartRange.min, chartRange.max };
            this.lineRangeEase.clear();
            this.lineRangeEase.put(key, er);
            this.lineTraceKey = key;
            this.lineTraceOpenMs = now;
        }
        else {
            er[0] += (chartRange.min - er[0]) * easeAlpha;
            er[1] += (chartRange.max - er[1]) * easeAlpha;
        }
        final float min = er[0];
        final float max = er[1];
        final float range = Math.max(1.0e-4f, max - min);

        final int plotX = this.graphLeft + 1;
        final int plotY = this.graphTop + 1;
        final int plotW = Math.max(2, this.graphWidth - 2);
        final int plotH = Math.max(2, this.graphHeight - 2);

        if (this.panel.getSourceType().equals("Stockpile Switch")) {
            this.drawThresholdLines(g, min, range, channel);
        }

        float lcSum = 0.0f;
        for (int a = 0; a < n; ++a) {
            lcSum += data[a];
        }
        final float lcAvg = lcSum / n;
        final float avgVal = (lcAvg > min && lcAvg < max) ? lcAvg : Float.NaN;
        final MetricType lcType = channel.getStore().getMetricType();
        final float[] lcFull = com.createstats.display.MetricContract.fixedRange(lcType);
        final float[] lcBands = com.createstats.display.MetricContract.bands(lcType);
        float warnVal = Float.NaN;
        float dangerVal = Float.NaN;
        int glowColor = color;
        if (lcFull != null && lcBands != null) {
            final float warnV = lcFull[0] + (lcFull[1] - lcFull[0]) * lcBands[0];
            final float dangerV = lcFull[0] + (lcFull[1] - lcFull[0]) * lcBands[1];
            warnVal = (warnV > min && warnV < max) ? warnV : Float.NaN;
            dangerVal = (dangerV > min && dangerV < max) ? dangerV : Float.NaN;
            final float latest = data[n - 1];
            glowColor = (latest >= dangerV) ? 0xD9594C : ((latest >= warnV) ? 0xE3A43B : 0x3FB36D);
        }

        // baked texture, one blit. per-pixel fill was tens of thousands of calls

        final float reveal = Math.min(1.0f, (now - this.lineTraceOpenMs) / 450.0f);
        final ResourceLocation traceTex = this.bakeLineTrace(data, plotW, plotH, min, max,
                color, glowColor, reveal, avgVal, warnVal, dangerVal);
        if (traceTex != null) {
            g.blit(traceTex, plotX, plotY, 0.0f, 0.0f, plotW, plotH, plotW, plotH);
        }

        if (!Float.isNaN(avgVal)) {
            final int avgY = plotY + Math.round((1.0f - (avgVal - min) / range) * (plotH - 1));
            g.drawString(this.font, "avg", plotX + 1, avgY - 9, 0xAAA0C4D4);
        }

        if (reveal >= 0.999f && ClientGraphPrefs.signalGlow()) {
            final float headPulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
            final int lastX = plotX + plotW - 1;
            final float lv = Math.max(min, Math.min(max, data[n - 1]));
            final int lastY = plotY + Math.round((1.0f - (lv - min) / range) * (plotH - 1));
            final int glowRgb = glowColor & 0xFFFFFF;
            final int a1 = (int) (0x30 + 0x40 * headPulse);
            g.fill(lastX - 3, lastY - 3, lastX + 4, lastY + 4, ((a1 / 2) << 24) | glowRgb);
            g.fill(lastX - 2, lastY - 2, lastX + 3, lastY + 3, (a1 << 24) | glowRgb);
        }

        this.hoverDataIndex = -1;
        int closestDist = Integer.MAX_VALUE;
        for (int k = 0; k < n; ++k) {
            final int x2 = plotX + (int) (k / (float) (n - 1) * (plotW - 1));
            final float clamped2 = Math.max(min, Math.min(max, data[k]));
            final int y2 = plotY + Math.round((1.0f - (clamped2 - min) / range) * (plotH - 1));
            final int dist = Math.abs(mx - x2) + Math.abs(my - y2);
            if (dist < closestDist && dist < 20 && mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= this.graphTop && my <= this.graphTop + this.graphHeight) {
                closestDist = dist;
                this.hoverDataIndex = k;
                this.hoverValue = data[k];
                this.hoverLabel = channel.getStore().getMetricType().formatValue(data[k]);
            }
        }

        for (int k = 0; k <= 4; ++k) {
            final float v2 = min + range * (1.0f - k / 4.0f);
            final int y3 = plotY + (plotH - 1) * k / 4;
            final String lbl = this.formatYAxis(v2, range);
            g.drawString(this.font, lbl, this.graphLeft - this.font.width(lbl) - 3, y3 - 4, this.DIM);
        }
        if (chartRange.autoZoomed) {
            final String zLabel = "\u26a1 Auto-zoom";
            g.drawString(this.font, zLabel, this.graphLeft + this.graphWidth - this.font.width(zLabel) - 2, this.graphTop + 3, -21965);
        }

        if (this.hoverDataIndex >= 0) {
            final int hx = plotX + (int) (this.hoverDataIndex / (float) (n - 1) * (plotW - 1));
            final int hy = plotY + Math.round((1.0f - (Math.max(min, Math.min(max, this.hoverValue)) - min) / range) * (plotH - 1));
            this.vline(g, hx, this.graphTop + 1, this.graphTop + this.graphHeight - 1, 1728053247);
            this.hline(g, this.graphLeft + 1, this.graphLeft + this.graphWidth - 1, hy, 1728053247);
            g.fill(hx - 4, hy - 4, hx + 5, hy + 5, -1);
            g.fill(hx - 3, hy - 3, hx + 4, hy + 4, color);
        }
    }

    private ResourceLocation bakeLineTrace(float[] data, int plotW, int plotH, float min, float max,
            int coreColor, int glowColor, float reveal, float avgVal, float warnVal, float dangerVal) {
        if (plotW < 2 || plotH < 2) {
            return null;
        }
        if (this.lineTraceTex == null || this.lineTraceTexW != plotW || this.lineTraceTexH != plotH) {
            this.disposeLineTrace();
            try {
                this.lineTraceImg = new NativeImage(NativeImage.Format.RGBA, plotW, plotH, true);
                this.lineTraceTex = new DynamicTexture(this.lineTraceImg);
                this.lineTraceTex.setFilter(false, false);
                this.lineTraceTexId = ResourceLocation.fromNamespaceAndPath("create_stats", "signal_trace_gui");
                Minecraft.getInstance().getTextureManager().register(this.lineTraceTexId, this.lineTraceTex);
                this.lineTraceTexW = plotW;
                this.lineTraceTexH = plotH;
                this.lineTraceSig = Long.MIN_VALUE;
            }
            catch (Throwable t) {
                this.disposeLineTrace();
                return null;
            }
        }
        final boolean drawGlow = ClientGraphPrefs.signalGlow();
        long sig = 1469598103934665603L;
        sig = fnv(sig, drawGlow ? 1 : 0);
        sig = fnv(sig, Float.floatToIntBits(min));
        sig = fnv(sig, Float.floatToIntBits(max));
        sig = fnv(sig, coreColor);
        sig = fnv(sig, glowColor);
        sig = fnv(sig, Math.round(reveal * 48.0f));
        sig = fnv(sig, data.length);
        sig = fnv(sig, Float.floatToIntBits(avgVal));
        sig = fnv(sig, Float.floatToIntBits(warnVal));
        sig = fnv(sig, Float.floatToIntBits(dangerVal));
        for (final float v : data) {
            sig = fnv(sig, Float.floatToIntBits(v));
        }
        if (sig != this.lineTraceSig && this.lineTraceImg != null && this.lineTraceTex != null) {
            final int minorCol = (this.GRID & 0xFFFFFF) | 0x30000000;
            final int tickCol = (this.GRID & 0xFFFFFF) | 0x80000000;
            final int baseCol = (this.ACCENT & 0xFFFFFF) | 0x66000000;
            final int bracketCol = (this.ACCENT & 0xFFFFFF) | 0x99000000;
            SignalTraceRenderer.bakeTrace(this.lineTraceImg, data, min, max,
                    coreColor, glowColor, coreColor, reveal, drawGlow,
                    minorCol, tickCol, baseCol, bracketCol,
                    avgVal, 0x99A0C4D4, warnVal, 0x66E3A43B, dangerVal, 0x66D9594C);
            this.lineTraceTex.upload();
            this.lineTraceSig = sig;
        }
        return this.lineTraceTexId;
    }

    private static long fnv(long h, int v) {
        h ^= (v & 0xFFFFFFFFL);
        h *= 1099511628211L;
        return h;
    }

    private void disposeLineTrace() {
        if (this.lineTraceTexId != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(this.lineTraceTexId);
            }
            catch (Throwable ignored) {
            }
            this.lineTraceTexId = null;
        }
        if (this.lineTraceTex != null) {
            this.lineTraceTex.close();
            this.lineTraceTex = null;
        }
        this.lineTraceImg = null;
        this.lineTraceTexW = -1;
        this.lineTraceTexH = -1;
        this.lineTraceSig = Long.MIN_VALUE;
    }

    @Override
    public void removed() {
        this.disposeLineTrace();
        super.removed();
    }

    private void renderOverlayLineChart(GuiGraphics g, float[] data, int color, StatsDisplayPanelBE.DataChannel channel) {
        final int n = data.length;
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        final float avgVal = (min + max) / 2.0f;
        final float minRange = Math.max(Math.abs(avgVal) * 0.05f, 1.0f);
        if (range < minRange) {
            range = minRange;
            min = avgVal - range / 2.0f;
            max = avgVal + range / 2.0f;
        }
        final float niceStep = niceNumber(range / 4.0f);
        min = (float)(Math.floor(min / niceStep) * niceStep);
        max = (float)(Math.ceil(max / niceStep) * niceStep);
        range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
            min -= 0.5f;
            max += 0.5f;
        }
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < n; ++i) {
            final int x = this.graphLeft + 1 + (int)(i / (float)(n - 1) * (this.graphWidth - 3));
            final float clamped = Math.max(min, Math.min(max, data[i]));
            final int y = this.clampY(this.graphTop + this.graphHeight - 2 - (int)((clamped - min) / range * (this.graphHeight - 4)));
            if (prevX >= 0) {
                this.drawThickLine(g, prevX, prevY, x, y, color, 1);
            }
            prevX = x;
            prevY = y;
        }
    }

    private void renderOverlayBarChart(GuiGraphics g, float[] data, int color) {
        final int n = data.length;
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        final float avgVal = (min + max) / 2.0f;
        final float minRange = Math.max(Math.abs(avgVal) * 0.05f, 1.0f);
        if (range < minRange) {
            range = minRange;
            min = avgVal - range / 2.0f;
            max = avgVal + range / 2.0f;
        }
        final float niceStep = niceNumber(range / 4.0f);
        min = (float)(Math.floor(min / niceStep) * niceStep);
        max = (float)(Math.ceil(max / niceStep) * niceStep);
        range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
            min -= 0.5f;
            max += 0.5f;
        }
        int maxBars = Math.min(n, (this.graphWidth - 8) / 9);
        maxBars = Math.max(1, Math.min(maxBars, 60));
        final int start = Math.max(0, n - maxBars);
        final int count = n - start;
        final int totalSpace = this.graphWidth - 8;
        final int barW = Math.max(3, (totalSpace - 3 * (count + 1)) / count);
        final int gap = Math.max(3, (totalSpace - barW * count) / (count + 1));
        final int totalUsed = count * barW + (count + 1) * gap;
        final int barStartX = this.graphLeft + 4 + (totalSpace - totalUsed) / 2;
        for (int i = 0; i < count; ++i) {
            final float val = data[start + i];
            final float normalized = Math.max(0.0f, Math.min(1.0f, (val - min) / range));
            final int barH = Math.max(2, (int)(normalized * (this.graphHeight - 6)));
            final int x = barStartX + gap + i * (barW + gap) + barW / 2;
            final int y = Math.max(this.graphTop + 1, this.graphTop + this.graphHeight - 2 - barH);
            if (x + barW / 2 <= this.graphLeft + this.graphWidth - 2) {
                final int hw = Math.max(1, barW / 4);
                g.fill(x - hw, y, x + hw, this.graphTop + this.graphHeight - 2, color);
            }
        }
    }

    private void renderDualYAxis(GuiGraphics g, StatsDisplayPanelBE.DataChannel primary, List<StatsDisplayPanelBE.DataChannel> allChannels) {
        StatsDisplayPanelBE.DataChannel secondary = null;
        final float primaryMax = primary.getStore().getMax();
        final float primaryMin = primary.getStore().getMin();
        final float primaryRange = Math.max(1.0f, primaryMax - primaryMin);
        for (int pinIdx : this.pinnedChannels) {
            if (pinIdx >= 0 && pinIdx < allChannels.size()) {
                final StatsDisplayPanelBE.DataChannel pinCh = allChannels.get(pinIdx);
                final float pinMax = pinCh.getStore().getMax();
                final float pinMin = pinCh.getStore().getMin();
                final float pinRange = Math.max(1.0f, pinMax - pinMin);
                final float scaleRatio = Math.max(primaryRange / pinRange, pinRange / primaryRange);
                if (scaleRatio > 5.0f) {
                    secondary = pinCh;
                    break;
                }
                continue;
            }
        }
        if (secondary == null) {
            return;
        }
        float secMin = secondary.getStore().getMin();
        float secMax = secondary.getStore().getMax();
        float secRange = secMax - secMin;
        final float secAvg = (secMin + secMax) / 2.0f;
        final float secMinR = Math.max(Math.abs(secAvg) * 0.05f, 1.0f);
        if (secRange < secMinR) {
            secRange = secMinR;
            secMin = secAvg - secRange / 2.0f;
        }
        final float niceStep = niceNumber(secRange / 4.0f);
        secMin = (float)(Math.floor(secMin / niceStep) * niceStep);
        secMax = (float)(Math.ceil(secMax / niceStep) * niceStep);
        secRange = secMax - secMin;
        if (secRange < 0.001f) {
            secRange = 1.0f;
        }
        final int rightX = this.graphLeft + this.graphWidth + 4;
        for (int i = 0; i <= 4; ++i) {
            final float v = secMin + secRange * (1.0f - i / 4.0f);
            final int y = this.graphTop + this.graphHeight * i / 4;
            final String lbl = this.formatYAxis(v, secRange);
            int pinColor = 0;
            final Iterator<Integer> iterator2 = this.pinnedChannels.iterator();
            if (iterator2.hasNext()) {
                final int pinIdx2 = iterator2.next();
                pinColor = StatsDisplayScreen.CHANNEL_COLORS[pinIdx2 % StatsDisplayScreen.CHANNEL_COLORS.length];
            }
            g.drawString(this.font, lbl, rightX, y - 4, (pinColor & 0xFFFFFF) | 0xAA000000);
        }
    }

    private void drawThresholdLines(GuiGraphics g, float min, float range, StatsDisplayPanelBE.DataChannel channel) {
        if (channel == null) {
            return;
        }
        final String channelId = channel.getId();
        final boolean fillGraph = channelId.contains("_auto_fill_pct");
        final boolean amountGraph = channelId.contains("_auto_stock_level");
        if (!fillGraph && !amountGraph) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel upperCh = this.findSiblingAutoChannel(channel, "upper_thresh");
        final StatsDisplayPanelBE.DataChannel lowerCh = this.findSiblingAutoChannel(channel, "lower_thresh");
        if (upperCh != null && upperCh.getStore().getSampleCount() > 0) {
            float thresh = upperCh.getStore().getLatestValue();
            String label = "ON\u2191 " + upperCh.getStore().getMetricType().formatValue(thresh);
            if (fillGraph) {
                final float minLevel = this.latestValueOr(this.findSiblingAutoChannel(channel, "min_level"), 0.0f);
                final float capacity = Math.max(this.latestValueOr(this.findSiblingAutoChannel(channel, "stock_capacity"), thresh), minLevel);
                thresh = this.percentOfRange(thresh, minLevel, capacity);
                label = "ON\u2191 " + String.format("%.0f%%", thresh);
            }
            final int y = this.clampY(this.graphTop + this.graphHeight - 2 - (int)((thresh - min) / range * (this.graphHeight - 4)));
            for (int x = this.graphLeft + 2; x < this.graphLeft + this.graphWidth - 2; x += 6) {
                this.hline(g, x, Math.min(x + 3, this.graphLeft + this.graphWidth - 2), y, this.THRESH_LINE);
            }
            g.drawString(this.font, label, this.graphLeft + this.graphWidth - 6 - this.font.width(label), y - 10, -39322);
        }
        if (lowerCh != null && lowerCh.getStore().getSampleCount() > 0) {
            float thresh = lowerCh.getStore().getLatestValue();
            String label = "OFF\u2193 " + lowerCh.getStore().getMetricType().formatValue(thresh);
            if (fillGraph) {
                final float minLevel = this.latestValueOr(this.findSiblingAutoChannel(channel, "min_level"), 0.0f);
                final float capacity = Math.max(this.latestValueOr(this.findSiblingAutoChannel(channel, "stock_capacity"), thresh), minLevel);
                thresh = this.percentOfRange(thresh, minLevel, capacity);
                label = "OFF\u2193 " + String.format("%.0f%%", thresh);
            }
            final int y = this.clampY(this.graphTop + this.graphHeight - 2 - (int)((thresh - min) / range * (this.graphHeight - 4)));
            for (int x = this.graphLeft + 2; x < this.graphLeft + this.graphWidth - 2; x += 6) {
                this.hline(g, x, Math.min(x + 3, this.graphLeft + this.graphWidth - 2), y, -2008765697);
            }
            g.drawString(this.font, label, this.graphLeft + this.graphWidth - 6 - this.font.width(label), y + 3, -10057473);
        }
    }

    private float latestValueOr(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() <= 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }

    private float percentOfRange(float value, float min, float max) {
        final float span = max - min;
        if (span <= 0.001f) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, (value - min) * 100.0f / span));
    }

    private void renderBarChart(GuiGraphics g, float[] data, int color, MetricType type) {
        final int n = data.length;
        final ChartRange chartRange = this.buildChartRange(data, true);
        final float min = chartRange.min;
        final float max = chartRange.max;
        final float range = chartRange.range;
        final int minBarW = 6;
        final int minGap = 3;
        int maxBars = Math.min(data.length, (this.graphWidth - 8) / (minBarW + minGap));
        maxBars = Math.max(1, Math.min(maxBars, 60));
        final int start = Math.max(0, data.length - maxBars);
        final int count = data.length - start;
        final int totalSpace = this.graphWidth - 8;
        final int barW = Math.max(minBarW, (totalSpace - minGap * (count + 1)) / count);
        final int gap = Math.max(minGap, (totalSpace - barW * count) / (count + 1));
        final int totalUsed = count * barW + (count + 1) * gap;
        final int barStartX = this.graphLeft + 4 + (totalSpace - totalUsed) / 2;
        final boolean barGlow = ClientGraphPrefs.signalGlow();
        for (int j = 0; j < count; ++j) {
            final float val = data[start + j];
            final float normalized = Math.max(0.0f, Math.min(1.0f, (val - min) / range));
            final int barH = Math.max(2, (int)(normalized * (this.graphHeight - 6)));
            final int x = barStartX + gap + j * (barW + gap);
            final int y = Math.max(this.graphTop + 1, this.graphTop + this.graphHeight - 2 - barH);
            final int barBottom = this.graphTop + this.graphHeight - 2;
            if (x + barW <= this.graphLeft + this.graphWidth - 2) {
                g.fillGradient(x, y, x + barW, barBottom, brighten(color, 1.35f), darken(color, 0.72f));
                g.fill(x, y, x + barW, y + 1, brighten(color, 1.75f));
                g.fill(x, y, x + 1, barBottom, 0x40FFFFFF);
                g.fill(x + barW - 1, y, x + barW, barBottom, 0x40000000);
                if (barGlow && y - 1 >= this.graphTop) {
                    g.fill(x, y - 1, x + barW, y, (0x55 << 24) | (brighten(color, 1.5f) & 0xFFFFFF));
                }
            }
        }

        float bcSum = 0.0f;
        for (int a = 0; a < n; ++a) {
            bcSum += data[a];
        }
        final float bcAvg = bcSum / n;
        final int barBottomY = this.graphTop + this.graphHeight - 2;
        if (bcAvg >= min && bcAvg <= max) {
            final int avgY = barBottomY - (int)(Math.max(0.0f, Math.min(1.0f, (bcAvg - min) / range)) * (this.graphHeight - 6));
            for (int dx = this.graphLeft + 2; dx < this.graphLeft + this.graphWidth - 2; dx += 5) {
                g.fill(dx, avgY, dx + 2, avgY + 1, 0x99A0C4D4);
            }
            g.drawString(this.font, "avg", this.graphLeft + 2, avgY - 9, 0xAAA0C4D4);
        }
        final float[] bcFull = com.createstats.display.MetricContract.fixedRange(type);
        final float[] bcBands = com.createstats.display.MetricContract.bands(type);
        if (bcFull != null && bcBands != null) {
            final float warnV = bcFull[0] + (bcFull[1] - bcFull[0]) * bcBands[0];
            final float dangerV = bcFull[0] + (bcFull[1] - bcFull[0]) * bcBands[1];
            if (warnV > min && warnV < max) {
                final int wy = barBottomY - (int)((warnV - min) / range * (this.graphHeight - 6));
                for (int dx = this.graphLeft + 2; dx < this.graphLeft + this.graphWidth - 2; dx += 6) {
                    g.fill(dx, wy, dx + 3, wy + 1, 0x66E3A43B);
                }
            }
            if (dangerV > min && dangerV < max) {
                final int dyb = barBottomY - (int)((dangerV - min) / range * (this.graphHeight - 6));
                for (int dx = this.graphLeft + 2; dx < this.graphLeft + this.graphWidth - 2; dx += 6) {
                    g.fill(dx, dyb, dx + 3, dyb + 1, 0x66D9594C);
                }
            }
        }
        for (int j = 0; j <= 4; ++j) {
            final float v2 = min + range * (1.0f - j / 4.0f);
            final int y2 = this.graphTop + this.graphHeight * j / 4;
            final String lbl = this.formatYAxis(v2, range);
            g.drawString(this.font, lbl, this.graphLeft - this.font.width(lbl) - 3, y2 - 4, this.DIM);
        }
        final String baseLbl = "Base: " + this.formatCompact(min);
        g.drawString(this.font, baseLbl, this.graphLeft + this.graphWidth - this.font.width(baseLbl) - 2, this.graphTop + this.graphHeight + 2, -12303258);
    }

    private void renderGauge(GuiGraphics g, TimeSeriesStore store, int color) {
        final float value = store.getLatestValue();
        final MetricType mtype = store.getMetricType();
        float maxVal;
        final float[] contractRange = com.createstats.display.MetricContract.fixedRange(mtype);
        if (contractRange != null) {
            maxVal = contractRange[1];
        }
        else if (mtype == MetricType.HEAT_LEVEL) {
            maxVal = 18.0f;
        }
        else {
            maxVal = Math.max(store.getMax() * 1.2f, 1.0f);
        }
        final float ratio = Math.min(Math.abs(value) / maxVal, 1.0f);
        final long sgNow = System.currentTimeMillis();
        float sgDt = (this.singleGaugeEaseMs == 0L) ? 0.016f : (sgNow - this.singleGaugeEaseMs) / 1000.0f;
        sgDt = Math.max(0.0f, Math.min(0.1f, sgDt));
        this.singleGaugeEaseMs = sgNow;
        final float sgAlpha = 1.0f - (float)Math.exp(-sgDt / 0.12f);
        float ratioEased = (this.singleGaugeEase < 0.0f) ? ratio : this.singleGaugeEase + (ratio - this.singleGaugeEase) * sgAlpha;
        if (Math.abs(ratioEased - ratio) < 0.0005f) {
            ratioEased = ratio;
        }
        this.singleGaugeEase = ratioEased;
        if (this.singleGaugeOpenMs == 0L) {
            this.singleGaugeOpenMs = sgNow;
        }

        final float sgIntro = Math.min(1.0f, (sgNow - this.singleGaugeOpenMs) / 900.0f);
        float renderRatio = ratioEased;
        if (sgIntro < 1.0f) {
            if (sgIntro < 0.45f) {
                final float u = sgIntro / 0.45f;
                renderRatio = 1.0f - (1.0f - u) * (1.0f - u);
            }
            else {
                final float u = (sgIntro - 0.45f) / 0.55f;
                final float s = u * u * (3.0f - 2.0f * u);
                renderRatio = 1.0f + (ratio - 1.0f) * s;
            }
        }
        final int cx = this.graphLeft + this.graphWidth / 2;
        final int cy = this.graphTop + this.graphHeight / 2 + 20;
        final int radius = Math.min(this.graphWidth, this.graphHeight) / 2 - 20;
        final double startA = 2.356194490192345;
        final double span = 4.71238898038469;
        final int outerR = radius;
        final int innerR = radius - Math.max(6, (int)(radius * 0.18));
        final int segments = 150;
        this.drawSmoothArc(g, cx, cy, innerR, outerR, startA, span, segments, -15066582);
        if (renderRatio > 0.001f) {
            final double filledSpan = renderRatio * span;
            for (int filledSegs = Math.max(4, (int)(renderRatio * segments)), i = 0; i < filledSegs; ++i) {
                final float t = i / (float)filledSegs;
                final double a1 = startA + i / (double)filledSegs * filledSpan;
                final double a2 = startA + (i + 1) / (double)filledSegs * filledSpan;
                int segColor;
                if (mtype == MetricType.STRESS_PERCENT) {
                    segColor = ((t < 0.5f) ? lerpColor(-16724890, -13312, t * 2.0f) : lerpColor(-13312, -52429, (t - 0.5f) * 2.0f));
                }
                else {
                    segColor = lerpColor(darken(color, 0.6f), color, t);
                }
                this.drawSmoothArc(g, cx, cy, innerR, outerR, a1, a2 - a1, 1, segColor);
            }
        }
        final double needleA = startA + renderRatio * span;
        final int nx = cx + (int)(Math.cos(needleA) * (radius - 8));
        final int ny = cy + (int)(Math.sin(needleA) * (radius - 8));
        this.drawThickLine(g, cx, cy, nx, ny, -1, 2);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, -1);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, -14540254);

        final float avgFrac = Math.max(0.0f, Math.min(1.0f, store.getAverage() / maxVal));
        final double avgA = startA + avgFrac * span;
        final int avgX = cx + (int)(Math.cos(avgA) * (radius + 4));
        final int avgY = cy + (int)(Math.sin(avgA) * (radius + 4));
        g.fill(avgX - 2, avgY - 2, avgX + 3, avgY + 3, darken(this.GRAPH_BG, 0.5f));
        g.fill(avgX - 1, avgY - 1, avgX + 2, avgY + 2, 0xFF5AC8E0);
        final float peakFrac = Math.max(0.0f, Math.min(1.0f, store.getMax() / maxVal));
        if (peakFrac > renderRatio + 0.02f) {
            final double peakA = startA + peakFrac * span;
            final int pOuter = radius + 4;
            this.drawThickLine(g,
                    cx + (int)(Math.cos(peakA) * innerR), cy + (int)(Math.sin(peakA) * innerR),
                    cx + (int)(Math.cos(peakA) * pOuter), cy + (int)(Math.sin(peakA) * pOuter),
                    0xFFD9594C, 2);
        }
        final String val = mtype.formatValue(value);
        g.drawCenteredString(this.font, val, cx, cy + radius / 4, this.TEXT);
        g.drawCenteredString(this.font, mtype.getDisplayName(), cx, cy + radius / 4 + 14, this.DIM);
        for (int pct = 0; pct <= 4; ++pct) {
            final float frac = pct / 4.0f;
            final double a3 = startA + frac * span;
            final int lx = cx + (int)(Math.cos(a3) * (radius + 14));
            final int ly = cy + (int)(Math.sin(a3) * (radius + 14));
            final String lbl = this.formatCompact(frac * maxVal);
            g.drawCenteredString(this.font, lbl, lx, ly - 4, this.DIM);
        }
    }

    private void renderNumeric(GuiGraphics g, TimeSeriesStore store, MetricType type) {
        if (store.getSampleCount() == 0) {
            return;
        }
        final float value = store.getLatestValue();
        final String val = type.formatValue(value);
        final int cx = this.graphLeft + this.graphWidth / 2;
        final int cy = this.graphTop + this.graphHeight / 2;
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)(cy - 20), 0.0f);
        g.pose().scale(3.5f, 3.5f, 1.0f);
        final Font font = this.font;
        final String s = val;
        final int n = 0;
        Objects.requireNonNull(this.font);
        g.drawCenteredString(font, s, n, -9 / 2, this.ACCENT);
        g.pose().popPose();
        g.drawCenteredString(this.font, type.getDisplayName(), cx, cy + 14, this.DIM);
        if (store.getSampleCount() >= 2) {
            final float[] data = store.getData();
            final float prev = data[data.length - 2];
            final float diff = value - prev;
            String trend;
            int trendColor;
            if (Math.abs(diff) < 0.01f) {
                trend = "\u2192 stable";
                trendColor = this.DIM;
            }
            else if (diff > 0.0f) {
                trend = "\u2191 +" + this.formatCompact(diff);
                trendColor = -12255352;
            }
            else {
                trend = "\u2193 " + this.formatCompact(diff);
                trendColor = this.WARN;
            }
            g.drawCenteredString(this.font, trend, cx, cy + 30, trendColor);
        }
        if (type == MetricType.BOOLEAN_FLAG) {
            final int stateColor = (value > 0.5f) ? -16711800 : -48060;
            final String stateText = (value > 0.5f) ? "\u25cf ON" : "\u25cb OFF";
            g.pose().pushPose();
            g.pose().translate((float)cx, (float)(cy - 20), 0.0f);
            g.pose().scale(3.5f, 3.5f, 1.0f);
            final Font font2 = this.font;
            final String s2 = stateText;
            final int n2 = 0;
            Objects.requireNonNull(this.font);
            g.drawCenteredString(font2, s2, n2, -9 / 2, stateColor);
            g.pose().popPose();
        }

        if (store.getSampleCount() >= 2 && this.graphHeight >= 160) {
            final float[] sd = store.getDataWindow(280);
            final int sn = sd.length;
            if (sn >= 2) {
                float smin;
                float smax;
                final float[] fr = com.createstats.display.MetricContract.fixedRange(type);
                if (fr != null) {
                    smin = fr[0];
                    smax = fr[1];
                }
                else {
                    smin = store.getMin();
                    smax = store.getMax();
                    final float pad = Math.max(0.001f, (smax - smin) * 0.1f);
                    smin = (smin >= 0.0f) ? 0.0f : (smin - pad);
                    smax += pad;
                }
                float srange = smax - smin;
                if (srange < 0.001f) {
                    srange = 1.0f;
                }
                final int sw = Math.min(this.graphWidth - 40, 280);
                final int sh = 34;
                final int sbx = this.graphLeft + (this.graphWidth - sw) / 2;
                final int sby = this.graphTop + this.graphHeight - sh - 6;
                g.fill(sbx, sby, sbx + sw, sby + sh, darken(this.GRAPH_BG, 0.55f));
                this.border(g, sbx, sby, sw, sh, darken(this.BORDER, 0.85f));
                g.drawString(this.font, "Recent history", sbx + 4, sby + 2, this.DIM);
                final int plotBot = sby + sh - 3;
                final int plotH = plotBot - (sby + 12);
                final float avgFrac = Math.max(0.0f, Math.min(1.0f, (store.getAverage() - smin) / srange));
                final int avgY = plotBot - (int)(avgFrac * plotH);
                for (int dx = sbx + 2; dx < sbx + sw - 2; dx += 3) {
                    g.fill(dx, avgY, dx + 1, avgY + 1, 0x553A7A8C);
                }
                int psx = -1;
                int psy = -1;
                for (int j = 0; j < sn; ++j) {
                    final int xx = sbx + 2 + (int)(j / (float)(sn - 1) * (sw - 4));
                    final float vv = Math.max(smin, Math.min(smax, sd[j]));
                    final int yy = plotBot - (int)((vv - smin) / srange * plotH);
                    if (psx >= 0) {
                        this.drawThickLine(g, psx, psy, xx, yy, this.ACCENT, 1);
                    }
                    psx = xx;
                    psy = yy;
                }
                g.fill(psx - 1, psy - 1, psx + 2, psy + 2, 0xFFFFFFFF);
            }
        }
    }

    private void renderStatsBar(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (this.selectedTab < 0 || this.selectedTab >= channels.size()) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel ch = channels.get(this.selectedTab);
        final TimeSeriesStore store = ch.getStore();
        if (store.getSampleCount() == 0) {
            return;
        }
        final int w = this.getWindowSampleLimit(store);
        final int sy = this.graphTop + this.graphHeight + 4;
        final int sx = this.graphLeft;
        final int spacing = this.graphWidth / 5;
        g.drawString(this.font, "Min: " + this.formatCompact(store.getMinWindow(w)), sx, sy, this.DIM);
        g.drawString(this.font, "Max: " + this.formatCompact(store.getMaxWindow(w)), sx + spacing, sy, this.DIM);
        g.drawString(this.font, "Avg: " + this.formatCompact(store.getAverageWindow(w)), sx + spacing * 2, sy, this.DIM);
        g.drawString(this.font, "Now: " + store.getMetricType().formatValue(store.getLatestValue()), sx + spacing * 3, sy, this.TEXT);
        final int displayed = this.getDisplayedSampleCount(store);
        g.drawString(this.font, "n=" + displayed, sx + spacing * 4, sy, this.DIM);
    }

    private void renderTimeAxis(GuiGraphics g, StatsDisplayPanelBE.DataChannel channel) {
        final TimeSeriesStore store = channel.getStore();
        final int totalSamples = store.getSampleCount();
        if (totalSamples < 2) {
            return;
        }
        final int y = this.graphTop + this.graphHeight + 14;
        final int displayedSamples = this.getDisplayedSampleCount(store);
        final int totalSec = this.getDisplayedDurationSeconds(store, displayedSamples);
        g.drawString(this.font, "now", this.graphLeft + this.graphWidth - this.font.width("now"), y, -12303258);
        if (totalSec >= 10) {
            final String midLabel = this.formatTimeDuration(totalSec / 2);
            g.drawCenteredString(this.font, midLabel + " ago", this.graphLeft + this.graphWidth / 2, y, -12303258);
        }
        final String startLabel = this.formatTimeDuration(totalSec);
        g.drawString(this.font, startLabel + " ago", this.graphLeft, y, -12303258);
    }

    private String formatTimeDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m" + seconds % 60 + "s";
        }
        return seconds / 3600 + "h" + seconds % 3600 / 60 + "m" + seconds % 60 + "s";
    }

    private int getWindowSampleLimit(TimeSeriesStore store) {
        if (this.timeScale <= 0) {
            return 0;
        }
        return store.getSampleCountForSeconds(this.timeScale);
    }

    private int getDisplayedSampleCount(TimeSeriesStore store) {
        final int windowSamples = this.getWindowSampleLimit(store);
        return (windowSamples > 0) ? windowSamples : store.getSampleCount();
    }

    private int getDisplayedDurationSeconds(TimeSeriesStore store, int displayedSamples) {
        return Math.max(0, displayedSamples - 1) * store.getSampleIntervalTicks() / 20;
    }

    private String getWindowTimeAgo(TimeSeriesStore store, int dataIndex, int displayedSamples) {
        if (dataIndex < 0 || dataIndex >= displayedSamples) {
            return "--";
        }
        final int samplesAgo = displayedSamples - 1 - dataIndex;
        return this.formatTimeDuration(samplesAgo * store.getSampleIntervalTicks() / 20) + " ago";
    }

    private int findMinIndex(float[] data) {
        if (data.length == 0) {
            return -1;
        }
        int minIdx = 0;
        for (int i = 1; i < data.length; ++i) {
            if (data[i] < data[minIdx]) {
                minIdx = i;
            }
        }
        return minIdx;
    }

    private int findMaxIndex(float[] data) {
        if (data.length == 0) {
            return -1;
        }
        int maxIdx = 0;
        for (int i = 1; i < data.length; ++i) {
            if (data[i] > data[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private void renderStatsPanel(GuiGraphics g, StatsDisplayPanelBE.DataChannel channel) {
        final TimeSeriesStore store = channel.getStore();
        if (store.getSampleCount() < 2) {
            return;
        }
        final int w = this.getWindowSampleLimit(store);
        final float[] windowData = (w > 0) ? store.getDataWindow(w) : store.getData();
        final int py = this.graphTop + this.graphHeight + 26;
        final int px = this.graphLeft;
        final int pw = this.graphWidth;
        final int ph = 72;
        g.fill(px, py, px + pw, py + ph, -15921894);
        this.border(g, px, py, pw, ph, -13421739);
        final String windowLabel = (this.timeScale > 0) ? ("\u2630 Stats (" + this.formatTimeDuration(this.timeScale) + " window)") : "\u2630 Stats (all-time)";
        g.drawString(this.font, windowLabel, px + 4, py + 3, this.ACCENT);
        final int col1 = px + 6;
        final int col2 = px + pw / 3;
        final int col3 = px + pw * 2 / 3;
        final int row1 = py + 16;
        final int row2 = py + 28;
        final int row3 = py + 40;
        final int row4 = py + 52;
        final MetricType mt = store.getMetricType();
        g.drawString(this.font, "Min: " + mt.formatValue(store.getMinWindow(w)), col1, row1, this.DIM);
        g.drawString(this.font, "Max: " + mt.formatValue(store.getMaxWindow(w)), col2, row1, this.DIM);
        g.drawString(this.font, "Avg: " + mt.formatValue(store.getAverageWindow(w)), col3, row1, this.DIM);
        g.drawString(this.font, "Median: " + mt.formatValue(store.getMedianWindow(w)), col1, row2, this.DIM);
        g.drawString(this.font, "StdDev: " + this.formatCompact(store.getStdDevWindow(w)), col2, row2, this.DIM);
        final int displayed = windowData.length;
        g.drawString(this.font, "Samples: " + displayed, col3, row2, this.DIM);
        g.drawString(this.font, "P90: " + mt.formatValue(store.getPercentileWindow(90.0f, w)), col1, row3, this.DIM);
        g.drawString(this.font, "P95: " + mt.formatValue(store.getPercentileWindow(95.0f, w)), col2, row3, this.DIM);
        g.drawString(this.font, "P99: " + mt.formatValue(store.getPercentileWindow(99.0f, w)), col3, row3, this.DIM);
        final float rate = store.getRateOfChange();
        final String rateStr = ((rate >= 0.0f) ? "+" : "") + this.formatCompact(rate) + "/s";
        g.drawString(this.font, "Rate: " + rateStr, col1, row4, (rate > 0.0f) ? -12255352 : ((rate < 0.0f) ? this.WARN : this.DIM));
        final int minIdx = this.findMinIndex(windowData);
        final int maxIdx = this.findMaxIndex(windowData);
        if (minIdx >= 0) {
            g.drawString(this.font, "Min at: " + this.getWindowTimeAgo(store, minIdx, windowData.length), col2, row4, this.DIM);
        }
        if (maxIdx >= 0) {
            g.drawString(this.font, "Max at: " + this.getWindowTimeAgo(store, maxIdx, windowData.length), col3, row4, this.DIM);
        }
        final String duration = this.formatTimeDuration(this.getDisplayedDurationSeconds(store, windowData.length));
        g.drawString(this.font, "Duration: " + duration, px + pw - this.font.width("Duration: " + duration) - 4, py + 3, -12303258);
    }

    private void renderLogPanel(GuiGraphics g, StatsDisplayPanelBE.DataChannel channel) {
        final TimeSeriesStore store = channel.getStore();
        if (store.getSampleCount() == 0) {
            return;
        }
        final int py = this.graphTop + this.graphHeight + 26;
        final int px = this.graphLeft;
        final int pw = this.graphWidth;
        final int ph = 72;
        g.fill(px, py, px + pw, py + ph, -15921894);
        this.border(g, px, py, pw, ph, -13421739);
        g.drawString(this.font, "\u2630 Data Log (latest first)", px + 4, py + 3, this.ACCENT);
        final float[] data = store.getData();
        final MetricType mt = store.getMetricType();
        final int lineH = 10;
        final int visibleLines = (ph - 16) / lineH;
        final int totalLines = data.length;
        this.logScrollOffset = Math.max(0, Math.min(this.logScrollOffset, totalLines - visibleLines));
        final int clipY = py + 15;
        final int clipH = ph - 16;
        for (int i = 0; i < visibleLines && i + this.logScrollOffset < totalLines; ++i) {
            final int dataIdx = totalLines - 1 - (i + this.logScrollOffset);
            final int drawY = clipY + i * lineH;
            if (drawY + lineH > py + ph) {
                break;
            }
            final String timeStr = store.getTimeAgo(dataIdx);
            final String valStr = mt.formatValue(data[dataIdx]);
            if (i % 2 == 0) {
                g.fill(px + 2, drawY, px + pw - 2, drawY + lineH, 301989887);
            }
            g.drawString(this.font, "#" + (totalLines - dataIdx), px + 6, drawY + 1, -12303258);
            g.drawString(this.font, timeStr, px + 50, drawY + 1, this.DIM);
            g.drawString(this.font, valStr, px + 140, drawY + 1, this.TEXT);
            if (dataIdx > 0) {
                final float delta = data[dataIdx] - data[dataIdx - 1];
                final String deltaStr = ((delta >= 0.0f) ? "+" : "") + this.formatCompact(delta);
                final int deltaColor = (delta > 0.0f) ? -12255352 : ((delta < 0.0f) ? this.WARN : this.DIM);
                g.drawString(this.font, deltaStr, px + 230, drawY + 1, deltaColor);
            }
        }
        if (totalLines > visibleLines) {
            final int scrollBarH = Math.max(8, clipH * visibleLines / totalLines);
            final int scrollBarY = clipY + (int)(this.logScrollOffset / (float)(totalLines - visibleLines) * (clipH - scrollBarH));
            g.fill(px + pw - 4, scrollBarY, px + pw - 1, scrollBarY + scrollBarH, -12303258);
        }
    }

    private void exportToClipboard(boolean csvMode) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (channels.isEmpty()) {
            this.showExportFeedback("\u00a7cNo data to export", false);
            return;
        }
        final StringBuilder sb = new StringBuilder();
        if (csvMode) {
            for (StatsDisplayPanelBE.DataChannel ch : channels) {
                final TimeSeriesStore store = ch.getStore();
                final MetricType mt = store.getMetricType();
                final int windowSamples = this.getWindowSampleLimit(store);
                sb.append("# ").append(ch.getLabel()).append(" (").append(mt.getDisplayName()).append(")\n");
                sb.append("time_ago,value\n");
                final float[] data = (windowSamples > 0) ? store.getDataWindow(windowSamples) : store.getData();
                for (int i = 0; i < data.length; ++i) {
                    sb.append(this.getWindowTimeAgo(store, i, data.length)).append(",").append(data[i]).append("\n");
                }
                sb.append("\n");
            }
        }
        else {
            sb.append("=== Create Stats Export ===\n");
            sb.append("Source: ").append(this.panel.getSourceType().isEmpty() ? "Unknown" : this.panel.getSourceType()).append("\n");
            sb.append("Panel: ").append(this.panel.getMultiblockWidth()).append("x").append(this.panel.getMultiblockHeight()).append("\n");
            sb.append("Channels: ").append(channels.size()).append("\n\n");
            for (StatsDisplayPanelBE.DataChannel ch : channels) {
                final TimeSeriesStore store = ch.getStore();
                final MetricType mt = store.getMetricType();
                sb.append("--- ").append(ch.getLabel()).append(" ---\n");
                sb.append("  Type: ").append(mt.getDisplayName()).append("\n");
                sb.append("  Samples: ").append(store.getSampleCount()).append("\n");
                sb.append("  Duration: ").append(this.formatTimeDuration(store.getDurationSeconds())).append("\n");
                if (store.getSampleCount() > 0) {
                    final int w = this.getWindowSampleLimit(store);
                    final String wLabel = (this.timeScale > 0) ? (" (window: " + this.timeScale + "s)") : " (all-time)";
                    sb.append("  Current: ").append(mt.formatValue(store.getLatestValue())).append("\n");
                    sb.append("  --- Statistics").append(wLabel).append(" ---\n");
                    sb.append("  Min: ").append(mt.formatValue(store.getMinWindow(w))).append("\n");
                    sb.append("  Max: ").append(mt.formatValue(store.getMaxWindow(w))).append("\n");
                    sb.append("  Average: ").append(mt.formatValue(store.getAverageWindow(w))).append("\n");
                    sb.append("  Median: ").append(mt.formatValue(store.getMedianWindow(w))).append("\n");
                    sb.append("  StdDev: ").append(this.formatCompact(store.getStdDevWindow(w))).append("\n");
                    sb.append("  P90: ").append(mt.formatValue(store.getPercentileWindow(90.0f, w))).append("\n");
                    sb.append("  P95: ").append(mt.formatValue(store.getPercentileWindow(95.0f, w))).append("\n");
                    sb.append("  Rate: ").append(this.formatCompact(store.getRateOfChange())).append("/s\n");
                    sb.append("  --- Raw Data (oldest first) ---\n");
                    final float[] data2 = (w > 0) ? store.getDataWindow(w) : store.getData();
                    for (int j = 0; j < data2.length; ++j) {
                        sb.append("  ").append(this.getWindowTimeAgo(store, j, data2.length)).append(": ").append(mt.formatValue(data2[j])).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        try {
            this.minecraft.keyboardHandler.setClipboard(sb.toString());
            final String mode = csvMode ? "CSV" : "stats";
            final int lines = sb.toString().split("\n").length;
            this.showExportFeedback("\u00a7a\u2714 Exported " + channels.size() + " channel(s) (" + lines + " lines " + mode + ") to clipboard", true);
        }
        catch (Exception e) {
            this.showExportFeedback("\u00a7c\u2718 Clipboard copy failed: " + e.getMessage(), false);
        }
    }

    private void showExportFeedback(String message, boolean success) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private void renderCapacity(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        final List<CapacitySourceInfo> sources = this.buildCapacitySources(channels);
        if (sources.isEmpty()) {
            g.drawCenteredString(this.font, "No capacity data found", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
            g.drawCenteredString(this.font, "Connect a Stressometer, Fluid Tank, or Stockpile Switch", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
            return;
        }

        g.drawString(this.font, "System Health", this.graphLeft + 4, this.graphTop + 4, this.ACCENT);
        final String countLabel = sources.size() + (sources.size() == 1 ? " source" : " sources");
        g.drawString(this.font, countLabel, this.graphLeft + this.graphWidth - this.font.width(countLabel) - 4, this.graphTop + 4, this.DIM);

        final int listY = this.graphTop + 18;
        final int listH = this.graphHeight - 18;
        final int expandedExtra = 30;
        final int baseCardH = Math.min(56, Math.max(26, listH / Math.max(1, sources.size())));
        final int totalContentH = sources.size() * (baseCardH + 2);
        final int maxScroll = Math.max(0, totalContentH - listH);
        this.capacityScrollOffset = Math.min(this.capacityScrollOffset, maxScroll);

        int rowY = listY - this.capacityScrollOffset;
        for (int i = 0; i < sources.size(); i++) {
            final CapacitySourceInfo src = sources.get(i);
            final boolean expanded = src.sourceId.equals(this.capacityExpandedSource);
            final int cardH = expanded ? baseCardH + expandedExtra : baseCardH;

            if (rowY + cardH <= listY) { rowY += cardH + 2; continue; }
            if (rowY >= listY + listH) break;

            final int clampedTop = Math.max(rowY, listY);
            final int clampedBot = Math.min(rowY + baseCardH, listY + listH);
            if (clampedBot <= clampedTop) { rowY += cardH + 2; continue; }

            if (i % 2 == 0) g.fill(this.graphLeft + 1, clampedTop, this.graphLeft + this.graphWidth - 1, clampedBot, 301989887);
            final boolean hovered = mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= rowY && my < rowY + baseCardH;
            if (hovered) g.fill(this.graphLeft + 1, clampedTop, this.graphLeft + this.graphWidth - 1, clampedBot, 587202559);

            if (src.pct > 90f) {
                g.fill(this.graphLeft + 1, clampedTop, this.graphLeft + 3, clampedBot, this.WARN);
            }

            final String icon = getCapacityTypeIcon(src.sourceTypeRaw);
            g.drawString(this.font, icon, this.graphLeft + 5, rowY + 4, src.pct > 80f ? this.WARN : this.ACCENT);
            final int labelMaxW = this.graphWidth / 3 - 22;
            g.drawString(this.font, this.truncate(src.displayLabel, labelMaxW), this.graphLeft + 20, rowY + 4, this.TEXT);

            final int barX2 = this.graphLeft + this.graphWidth / 3;
            final int barW2 = this.graphWidth / 3;
            final int barH2 = Math.max(6, baseCardH / 3);
            final int barY2 = rowY + baseCardH / 2 - barH2 / 2;
            final float ratio = Math.max(0f, Math.min(1f, src.pct / 100f));
            final int usedW2 = (int)(barW2 * ratio);
            final int barColor = src.pct > 80f ? -48060 : (src.pct > 50f ? -30652 : -21965);
            if (barY2 >= listY && barY2 + barH2 <= listY + listH) {
                g.fill(barX2, barY2, barX2 + barW2, barY2 + barH2, -15066582);
                this.border(g, barX2, barY2, barW2, barH2, this.DIM);
                if (usedW2 > 0) g.fill(barX2 + 1, barY2 + 1, barX2 + 1 + usedW2, barY2 + barH2 - 1, barColor);
            }

            final int rightX = this.graphLeft + (this.graphWidth * 2 / 3) + barW2 / 3 + 2;
            g.drawString(this.font, String.format("%.0f%%", src.pct), rightX, rowY + 4, barColor);
            final String badge; final int badgeColor;
            if (src.pct > 90f) { badge = "CRIT"; badgeColor = this.WARN; }
            else if (src.pct > 75f) { badge = "WARN"; badgeColor = -30652; }
            else { badge = "OK"; badgeColor = -21965; }
            g.drawString(this.font, "[" + badge + "]", this.graphLeft + this.graphWidth - this.font.width("[" + badge + "]") - 4, rowY + 4, badgeColor);

            if (baseCardH >= 24 && rowY + baseCardH - 11 < listY + listH && rowY + baseCardH - 11 >= listY) {
                final String valLine = this.formatMetricCompact(src.amountType, src.used) + src.usedSuffix
                        + " / " + this.formatMetricCompact(src.amountType, src.total);
                g.drawString(this.font, valLine, this.graphLeft + 20, rowY + baseCardH - 11, this.DIM);
                if (src.pctStore != null) {
                    final String eta = this.computeCapacityEta(src.pctStore, src.pct);
                    if (eta != null) {
                        g.drawString(this.font, eta, rightX, rowY + baseCardH - 11, this.DIM);
                    }
                }
            }

            if (expanded && src.pctStore != null && src.pctStore.getSampleCount() >= 2) {
                final int sparkY = rowY + baseCardH + 2;
                final int sparkH = expandedExtra - 4;
                if (sparkH > 8 && sparkY >= listY && sparkY + sparkH <= listY + listH) {
                    final float[] sparkData = this.timeScale > 0
                            ? src.pctStore.getDataForTimeScale(this.timeScale)
                            : src.pctStore.getData();
                    if (sparkData.length >= 2) {
                        this.renderMiniLineChart(g, sparkData, this.graphLeft + 4, sparkY, this.graphWidth - 8, sparkH, barColor);
                    }
                }
            }
            rowY += cardH + 2;
        }

        if (totalContentH > listH) {
            final int sbH = Math.max(8, listH * listH / totalContentH);
            final int sbY = listY + (maxScroll > 0 ? (int)((float)this.capacityScrollOffset / maxScroll * (listH - sbH)) : 0);
            g.fill(this.graphLeft + this.graphWidth - 3, sbY, this.graphLeft + this.graphWidth - 1, sbY + sbH, -12303258);
        }
    }

    private List<CapacitySourceInfo> buildCapacitySources(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = new LinkedHashMap<>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            bySource.computeIfAbsent(sid, k -> new ArrayList<>()).add(ch);
        }
        final List<CapacitySourceInfo> result = new ArrayList<>();
        for (Map.Entry<String, List<StatsDisplayPanelBE.DataChannel>> entry : bySource.entrySet()) {
            final String sid = entry.getKey();
            if ("legacy".equals(sid)) continue;
            final CapacitySourceInfo info = this.resolveCapacitySource(sid, entry.getValue());
            if (info != null) result.add(info);
        }
        return result;
    }

    private List<CapacitySourceInfo> buildInventoryCapacitySources(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, CapacitySourceInfo> merged = new LinkedHashMap<>();
        for (CapacitySourceInfo source : this.buildCapacitySources(channels)) {
            final String key = this.inventoryCapacityMergeKey(source);
            final CapacitySourceInfo previous = merged.get(key);
            merged.put(key, previous == null ? source : this.mergeInventoryCapacitySource(previous, source));
        }
        return new ArrayList<>(merged.values());
    }

    private List<CapacitySourceInfo> buildFlowMeterFluidEntries(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, CapacitySourceInfo> bySource = new LinkedHashMap<>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String id = ch.getId();
            if (id == null || !id.endsWith("_auto_flow_rate_mbt")) {
                continue;
            }
            final String sid = StatsDisplaySettings.extractSourceId(id);
            if (sid == null || sid.isEmpty() || bySource.containsKey(sid)) {
                continue;
            }
            final String prefix = "src_" + sid + "_auto_";
            StatsDisplayPanelBE.DataChannel totalCh = null;
            StatsDisplayPanelBE.DataChannel fluidCh = null;
            for (StatsDisplayPanelBE.DataChannel c : channels) {
                final String cid = c.getId();
                if (cid == null) {
                    continue;
                }
                if (cid.equals(prefix + "flow_total_mb")) {
                    totalCh = c;
                } else if (cid.equals(prefix + "fluid_id")) {
                    fluidCh = c;
                }
            }
            final int rate = Math.round(ch.getStore().getLatestValue());
            final long total = totalCh != null ? (long) totalCh.getStore().getLatestValue() : 0L;
            final String fluidId = (fluidCh != null && fluidCh.getLabel() != null) ? fluidCh.getLabel() : "";
            final boolean hasFluid = fluidId.contains(":");
            final String fluidName = hasFluid
                    ? com.createstats.renderer.FluidPalette.displayNameForFluidId(fluidId)
                    : "No fluid";
            final SourceConfig cfg = this.panel.getSourceRegistry().get(sid);
            final String meterLabel = (cfg != null && cfg.getObservedDisplayLabel() != null
                    && !cfg.getObservedDisplayLabel().isBlank()) ? cfg.getObservedDisplayLabel() : "Flow Meter";
            final CapacitySourceInfo info = new CapacitySourceInfo(sid, fluidName, meterLabel,
                    (float) total, 0.0f, 0.0f, MetricType.FLUID_MB, " total", null, false, false);
            info.subLine = rate + " mB/t \u00b7 " + (rate * 20) + " mB/s";
            bySource.put(sid, info);
        }
        return new ArrayList<>(bySource.values());
    }

    private String inventoryCapacityMergeKey(CapacitySourceInfo source) {
        if (source == null || source.amountType != MetricType.FLUID_MB) {
            return source != null ? source.sourceId : "unknown";
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(source.sourceId);
        final BlockPos pos = config != null && config.getObservedTargetPos() != null
                ? config.getObservedTargetPos()
                : (config != null ? config.getSourcePos() : null);
        return pos != null ? "fluid:" + pos.asLong() : "fluid:" + source.sourceId;
    }

    private CapacitySourceInfo mergeInventoryCapacitySource(CapacitySourceInfo first, CapacitySourceInfo second) {
        final boolean fuelReserve = first.fuelReserve || second.fuelReserve;
        final CapacitySourceInfo displaySource = this.preferInventoryCapacityDisplaySource(first, second, fuelReserve);
        final float used = Math.max(first.used, second.used);
        final boolean hasCapacity = first.hasCapacity || second.hasCapacity;
        final float total = hasCapacity ? Math.max(Math.max(first.total, second.total), used) : 0.0f;
        final float pct = hasCapacity && total > 0.0f ? Math.max(0.0f, Math.min(100.0f, used * 100.0f / total)) : 0.0f;
        final TimeSeriesStore pctStore = displaySource.pctStore != null ? displaySource.pctStore : (first.pctStore != null ? first.pctStore : second.pctStore);
        final String sourceType = fuelReserve ? "Diesel Fuel Reserve" : displaySource.sourceTypeRaw;
        return new CapacitySourceInfo(displaySource.sourceId, displaySource.displayLabel, sourceType, used, total, pct,
                displaySource.amountType, displaySource.usedSuffix, pctStore, hasCapacity, fuelReserve);
    }

    private CapacitySourceInfo preferInventoryCapacityDisplaySource(CapacitySourceInfo first, CapacitySourceInfo second, boolean fuelReserve) {
        if (fuelReserve) {
            if (second.fuelReserve && !first.fuelReserve) return second;
            if (first.fuelReserve && !second.fuelReserve) return first;
        }
        if (second.hasCapacity && !first.hasCapacity) return second;
        if (first.hasCapacity && !second.hasCapacity) return first;
        return second.used > first.used ? second : first;
    }

    private CapacitySourceInfo resolveCapacitySource(String sid, List<StatsDisplayPanelBE.DataChannel> group) {
        StatsDisplayPanelBE.DataChannel usedCh = null, totalCh = null, pctCh = null;
        MetricType amountType = MetricType.STRESS_SU;
        String usedSuffix = " used";
        final SourceConfig cfg = this.panel.getSourceRegistry().get(sid);
        final String label = cfg != null ? cfg.getObservedDisplayLabel() : sid;
        final String srcType = cfg != null ? cfg.getSourceType() : "";

        for (StatsDisplayPanelBE.DataChannel ch : group) {
            final String id = ch.getId();
            if (id.contains("stress_su")) usedCh = ch;
            else if (id.contains("capacity_su")) totalCh = ch;
            else if (id.contains("stress_pct")) pctCh = ch;
        }
        if (usedCh == null || totalCh == null) {
            usedCh = null; totalCh = null; pctCh = null;
            amountType = MetricType.FLUID_MB; usedSuffix = " stored";

            usedCh = this.findMetricChannel(group, "reserve_fuel_amount", "fuel_amount", "fluid_amount", "oil_remaining_mb");
            totalCh = this.findMetricChannel(group, "reserve_fuel_capacity", "fuel_capacity", "fluid_capacity", "tank_capacity");
            pctCh = this.findMetricChannel(group, "reserve_fuel_fill_pct", "fuel_fill_pct", "fluid_fill_pct", "tank_fill", "fill_pct");
        }
        if (usedCh == null || totalCh == null) {
            usedCh = null; totalCh = null; pctCh = null;
            usedSuffix = " stored";

            for (StatsDisplayPanelBE.DataChannel ch : group) {
                final String id = ch.getId();
                if (id.contains("stock_level")) usedCh = ch;
                else if (id.contains("stock_capacity")) totalCh = ch;
                else if (id.contains("fill_pct")) pctCh = ch;
            }
            if (usedCh != null) amountType = usedCh.getStore().getMetricType();
        }
        if (usedCh == null || totalCh == null) {
            usedCh = null; totalCh = null; pctCh = null;
            amountType = MetricType.ITEM_COUNT; usedSuffix = " items";

            for (StatsDisplayPanelBE.DataChannel ch : group) {
                final String id = ch.getId();
                if (id.contains("total_items")) usedCh = ch;
                else if (id.contains("slot_capacity")) totalCh = ch;
            }
            if (usedCh == null || totalCh == null || totalCh.getStore().getLatestValue() < 0.5f) {
                usedCh = null; totalCh = null;
            }
        }
        if (usedCh == null || totalCh == null) {
            return this.resolveInventorySnapshotSource(sid, group);
        }
        final float used = usedCh.getStore().getSampleCount() > 0 ? usedCh.getStore().getLatestValue() : 0f;
        final float total = Math.max(1f, totalCh.getStore().getSampleCount() > 0 ? totalCh.getStore().getLatestValue() : 1f);
        final float pct = (pctCh != null && pctCh.getStore().getSampleCount() > 0)
                ? pctCh.getStore().getLatestValue()
                : (total > 0f ? used / total * 100f : 0f);
        return new CapacitySourceInfo(sid, label, srcType, used, total, pct, amountType, usedSuffix,
            pctCh != null ? pctCh.getStore() : null, true, this.isFuelReserveGroup(label, srcType, group));
    }

    private StatsDisplayPanelBE.DataChannel findMetricChannel(List<StatsDisplayPanelBE.DataChannel> group, String... idFragments) {
        for (String idFragment : idFragments) {
            for (StatsDisplayPanelBE.DataChannel ch : group) {
                final String id = ch.getId();
                if (id != null && id.contains(idFragment)) {
                    return ch;
                }
            }
        }
        return null;
    }

    private CapacitySourceInfo resolveInventorySnapshotSource(String sid, List<StatsDisplayPanelBE.DataChannel> group) {
        final SourceConfig cfg = this.panel.getSourceRegistry().get(sid);
        final String label = cfg != null ? cfg.getObservedDisplayLabel() : sid;
        final String srcType = cfg != null ? cfg.getSourceType() : "";
        if (!this.isInventoryStorageSourceType(srcType)) {
            return null;
        }

        StatsDisplayPanelBE.DataChannel totalItemsCh = null;
        float itemSum = 0.0f;
        boolean hasItemDetail = false;
        float fluidSum = 0.0f;
        boolean hasFluidDetail = false;
        for (StatsDisplayPanelBE.DataChannel ch : group) {
            final TimeSeriesStore store = ch.getStore();
            if (store.getSampleCount() <= 0) {
                continue;
            }
            final String id = ch.getId().toLowerCase(java.util.Locale.ROOT);
            final MetricType metricType = store.getMetricType();
            if (metricType == MetricType.ITEM_COUNT) {
                if (id.contains("unique_types")) {
                    continue;
                }
                if (id.contains("total_items")) {
                    totalItemsCh = ch;
                    continue;
                }
                if (id.contains("_item_") || id.contains("auto_item_")) {
                    itemSum += store.getLatestValue();
                    hasItemDetail = true;
                }
            }
            else if (metricType == MetricType.FLUID_MB
                    && !id.contains("capacity")
                    && !id.contains("water_supply")
                    && !id.contains("lower_thresh")
                    && !id.contains("upper_thresh")
                    && !id.contains("min_level")) {
                fluidSum += store.getLatestValue();
                hasFluidDetail = true;
            }
        }
        if (totalItemsCh != null) {
            return new CapacitySourceInfo(sid, label, srcType, totalItemsCh.getStore().getLatestValue(), 0.0f, 0.0f,
                    MetricType.ITEM_COUNT, " observed", null, false, false);
        }
        if (hasItemDetail) {
            return new CapacitySourceInfo(sid, label, srcType, itemSum, 0.0f, 0.0f,
                    MetricType.ITEM_COUNT, " observed", null, false, false);
        }
        if (hasFluidDetail) {
            return new CapacitySourceInfo(sid, label, srcType, fluidSum, 0.0f, 0.0f,
                    MetricType.FLUID_MB, " observed", null, false, this.isFuelReserveGroup(label, srcType, group));
        }
        return null;
    }

    private boolean isFuelReserveGroup(String label, String sourceType, List<StatsDisplayPanelBE.DataChannel> group) {
        if (this.containsFuelReserveText(label) || this.containsFuelReserveText(sourceType)) {
            return true;
        }
        for (StatsDisplayPanelBE.DataChannel channel : group) {
            if (channel == null) {
                continue;
            }
            if (this.containsFuelReserveText(channel.getId()) || this.containsFuelReserveText(channel.getLabel())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsFuelReserveText(String text) {
        if (text == null) {
            return false;
        }
        final String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("reserve")
                || lower.contains("diesel")
                || lower.contains("gasoline")
                || lower.contains("biodiesel")
                || lower.contains("ethanol")
                || lower.contains("plantoil")
                || lower.contains("plant oil")
                || lower.contains("fuel_")
                || lower.contains("fuel ");
    }

    private boolean isInventoryStorageSourceType(String sourceType) {
        if (sourceType == null) {
            return false;
        }
        final String lower = sourceType.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("stockpile")
                || lower.contains("threshold")
                || lower.contains("observer")
                || lower.contains("vault")
                || lower.contains("barrel")
                || lower.contains("chest")
                || lower.contains("storage")
                || lower.contains("inventory")
                || lower.contains("tank")
                || lower.contains("fluid")
                || lower.contains("fuel")
                || lower.contains("reserve")
                || lower.contains("item")
                || lower.contains("production detector");
    }

    private String computeCapacityEta(TimeSeriesStore pctStore, float currentPct) {
        if (pctStore == null || pctStore.getSampleCount() < 3) return null;
        final float[] data = pctStore.getDataForTimeScale(60);
        if (data == null || data.length < 3) return null;
        final LinearRegression.RegressionCoefficients coeffs =
                LinearRegression.compute(data, 0, data.length);
        if (coeffs == null) return null;
        final double slopePerSample = coeffs.slope();
        if (Math.abs(slopePerSample) < 0.003) return null;
        final double slopePerMin = slopePerSample * 60.0;
        if (slopePerMin > 0) {
            final double mins = (100.0 - currentPct) / slopePerMin;
            if (mins <= 0 || mins > 120) return null;
            return "\u2191 " + formatEtaMinutes((float)mins);
        } else {
            final double mins = currentPct / (-slopePerMin);
            if (mins <= 0 || mins > 120) return null;
            return "\u2193 " + formatEtaMinutes((float)mins);
        }
    }

    private String formatEtaMinutes(float minutes) {
        if (minutes < 1f) return "~" + Math.max(1, (int)(minutes * 60)) + "s";
        if (minutes < 60f) return "~" + (int)minutes + "m";
        return ">1h";
    }

    private static String getCapacityTypeIcon(String sourceType) {
        if (sourceType == null) return "\u25a1";
        final String lower = sourceType.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("stress") || lower.contains("kinetic") || lower.contains("network")) return "\u26a1";
        if (lower.contains("fluid") || lower.contains("tank") || lower.contains("pipe")) return "\ud83e\udea3";
        if (lower.contains("stock") || lower.contains("chest") || lower.contains("item") || lower.contains("storage")) return "\ud83d\udce6";
        return "\u25a1";
    }

    private SourceConfig getSourceConfigForChannel(String channelId) {
        if (channelId == null) return null;
        final String sid = StatsDisplaySettings.extractSourceId(channelId);
        if (sid == null || sid.isEmpty()) return null;
        return this.panel.getSourceRegistry().get(sid);
    }

    private List<ProductionEntry> buildProductionEntries(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, ProductionAccumulator> aggregates = new LinkedHashMap<String, ProductionAccumulator>();
        final long currentTick = this.getGuiRenderTick();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final TimeSeriesStore store = channel.getStore();
            if (store.getSampleCount() <= 0) {
                continue;
            }
            final String sourceType = this.getSourceTypeForChannel(channel);
            final float rate;
            final float trend;
            if (this.isProductionRateMetric(store.getMetricType())) {
                rate = this.computeProductionRate(store);
                trend = this.computeTrend(store);
            }
            else {
                if (!this.isObserverProductionSnapshotChannel(channel, sourceType)) {
                    continue;
                }
                rate = this.computeObserverProductionRate(store, currentTick, PRODUCTION_RATE_WINDOW_SECONDS);
                trend = this.computeObserverProductionTrend(store, currentTick);
            }
            if (rate <= MIN_VISIBLE_PRODUCTION_RATE && !"production detector".equalsIgnoreCase(sourceType)) {
                continue;
            }
            final String label = this.resolveProductionEntryLabel(channel.getLabel());
            if (label.isEmpty()) {
                continue;
            }
            final String key = label.toLowerCase(java.util.Locale.ROOT);
            ProductionAccumulator accumulator = aggregates.get(key);
            if (accumulator == null) {
                accumulator = new ProductionAccumulator(label);
                aggregates.put(key, accumulator);
            }
            accumulator.add(channel, rate, trend, sourceType);
        }
        final ArrayList<ProductionEntry> entries = new ArrayList<ProductionEntry>(aggregates.size());
        for (ProductionAccumulator accumulator2 : aggregates.values()) {
            entries.add(accumulator2.toEntry());
        }
        entries.sort((a, b) -> Float.compare(b.rate, a.rate));
        return entries;
    }

    private boolean hasObserverProductionSnapshots(List<StatsDisplayPanelBE.DataChannel> channels) {
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel.getStore().getSampleCount() > 0 && this.isObserverProductionSnapshotChannel(channel, this.getSourceTypeForChannel(channel))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProductionRateChannels(List<StatsDisplayPanelBE.DataChannel> channels) {
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel.getStore().getSampleCount() > 0 && this.isProductionRateMetric(channel.getStore().getMetricType())) {
                return true;
            }
        }
        return false;
    }

    private void renderProductionView(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        this.prodRowHits.clear();

        final List<ProductionEntry> producing = this.buildProductionEntries(channels);
        final List<ProductionEntry> consuming = this.buildConsumingEntries(channels);

        final java.util.Comparator<ProductionEntry> sortComp = this.getProductionSortComparator(this.productionSortMode);
        producing.sort(sortComp);
        consuming.sort(sortComp);

        final java.util.Set<String> activeKeys = new java.util.HashSet<>();
        for (ProductionEntry e : producing) activeKeys.add(e.label.toLowerCase(java.util.Locale.ROOT));
        for (ProductionEntry e : consuming) activeKeys.add(e.label.toLowerCase(java.util.Locale.ROOT));
        final List<String> idleLabels = this.buildIdleProductionLabels(channels, activeKeys);

        if (producing.isEmpty() && consuming.isEmpty() && idleLabels.isEmpty()) {
            final boolean hasObserverSnapshots = this.hasObserverProductionSnapshots(channels);
            final boolean hasRateChannels = this.hasProductionRateChannels(channels);
            g.drawCenteredString(this.font, "No production data yet", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
            g.drawCenteredString(this.font, hasObserverSnapshots ? "Observer rates use the last 15s average delta" : (hasRateChannels ? "Linked rate sources are currently idle" : "Use a Content Observer, tunnel, or Production Detector"), this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
            return;
        }

        float net = 0f;
        for (ProductionEntry e : producing) net += e.rate;
        for (ProductionEntry e : consuming) net += e.rate;
        final int netColor = net > 0.1f ? -12255352 : net < -0.1f ? this.WARN : this.DIM;
        final String netStr = String.format("NET: %+.1f/m", net);

        final String[] sortNames = { "Rate", "A-Z", "Trend" };
        final String sortBtn = "[" + sortNames[this.productionSortMode % sortNames.length] + "]";
        this.prodSortBtnW = this.font.width(sortBtn) + 4;
        this.prodSortBtnH = 10;
        this.prodSortBtnX = this.graphLeft + this.graphWidth - this.prodSortBtnW - 2;
        this.prodSortBtnY = this.graphTop + 2;

        g.drawString(this.font, "Throughput Ledger", this.graphLeft + 4, this.graphTop + 4, this.ACCENT);
        g.drawString(this.font, netStr, this.graphLeft + this.graphWidth / 2 - this.font.width(netStr) / 2, this.graphTop + 4, netColor);
        final boolean sortHover = mx >= this.prodSortBtnX && mx <= this.prodSortBtnX + this.prodSortBtnW && my >= this.prodSortBtnY && my <= this.prodSortBtnY + this.prodSortBtnH;
        g.drawString(this.font, sortBtn, this.prodSortBtnX + 2, this.graphTop + 4, sortHover ? this.ACCENT : this.DIM);

        final List<ProductionEntry> dispEntries = new ArrayList<>();
        final List<String> dispLabels = new ArrayList<>();
        final List<Boolean> dispIsHeader = new ArrayList<>();
        if (!producing.isEmpty()) {
            dispEntries.add(null); dispLabels.add("\u25b6 PRODUCING (" + producing.size() + ")"); dispIsHeader.add(true);
            for (ProductionEntry e : producing) { dispEntries.add(e); dispLabels.add(null); dispIsHeader.add(false); }
        }
        if (!consuming.isEmpty()) {
            dispEntries.add(null); dispLabels.add("\u25bc CONSUMING (" + consuming.size() + ")"); dispIsHeader.add(true);
            for (ProductionEntry e : consuming) { dispEntries.add(e); dispLabels.add(null); dispIsHeader.add(false); }
        }
        if (!idleLabels.isEmpty()) {
            final String idleLine = "Idle: " + String.join(", ", idleLabels);
            dispEntries.add(null); dispLabels.add(idleLine); dispIsHeader.add(true);
        }

        final int rowH = 18;
        final int tableY = this.graphTop + 18;
        final int tableH = this.graphHeight - 22;
        final int visibleRows = Math.max(1, tableH / rowH);
        final int maxScroll = Math.max(0, dispEntries.size() - visibleRows);
        this.productionScrollOffset = Math.min(this.productionScrollOffset, maxScroll);

        float maxRate = 0.001f;
        for (ProductionEntry e : producing) maxRate = Math.max(maxRate, e.rate);
        for (ProductionEntry e : consuming) maxRate = Math.max(maxRate, Math.abs(e.rate));

        final int iconColW = 14;
        final int nameW = this.graphWidth / 3;
        final int barW = this.graphWidth / 4;

        ProductionEntry hoveredEntry = null;
        for (int i = 0; i < visibleRows && i + this.productionScrollOffset < dispEntries.size(); ++i) {
            final int dispIdx = i + this.productionScrollOffset;
            final ProductionEntry entry = dispEntries.get(dispIdx);
            final String label = dispLabels.get(dispIdx);
            final boolean isHeader = dispIsHeader.get(dispIdx);
            final int rowY2 = tableY + i * rowH;

            if (isHeader && label != null) {
                final boolean isIdle = label.startsWith("Idle:");
                final boolean isConsuming = label.startsWith("\u25bc");
                final int hdrColor = isIdle ? this.DIM : (isConsuming ? this.WARN : -12255352);
                g.fill(this.graphLeft + 1, rowY2, this.graphLeft + this.graphWidth - 1, rowY2 + rowH, isIdle ? 201326591 : 352321535);
                this.hline(g, this.graphLeft + 4, this.graphLeft + this.graphWidth - 4, rowY2 + rowH - 1, hdrColor & 0x55FFFFFF);
                g.drawString(this.font, label, this.graphLeft + 6, rowY2 + 5, hdrColor);
                continue;
            }

            if (entry == null) continue;

            final boolean isConsumeSection = consuming.contains(entry);
            final int rowColor = isConsumeSection ? this.WARN : -12255352;
            final boolean rowHovered = mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= rowY2 && my < rowY2 + rowH;
            if (i % 2 == 0) g.fill(this.graphLeft + 1, rowY2, this.graphLeft + this.graphWidth - 1, rowY2 + rowH, 301989887);
            if (rowHovered) {
                g.fill(this.graphLeft + 1, rowY2, this.graphLeft + this.graphWidth - 1, rowY2 + rowH, 587202559);
                hoveredEntry = entry;
            }
            this.prodRowHits.add(new ProductionRowHit(entry, rowY2, rowY2 + rowH));

            int colX = this.graphLeft + 4;
            final ItemStack icon = this.resolveItemIcon(entry.label);
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate((float)colX, (float)(rowY2 + 1), 0.0f);
                g.pose().scale(0.75f, 0.75f, 1.0f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }
            colX += iconColW;
            g.drawString(this.font, this.truncate(entry.label, nameW - 4), colX, rowY2 + 5, this.TEXT);

            final int barX2 = this.graphLeft + iconColW + nameW;
            final int barFillW = (int)(Math.abs(entry.rate) / maxRate * barW);
            g.fill(barX2, rowY2 + 4, barX2 + barW, rowY2 + rowH - 4, -15066582);
            if (barFillW > 0) g.fill(barX2, rowY2 + 4, barX2 + barFillW, rowY2 + rowH - 4, rowColor);

            final int rateX = barX2 + barW + 4;
            final String rateStr = String.format("%+.1f/m", entry.rate);
            g.drawString(this.font, rateStr, rateX, rowY2 + 5, rowColor);

            final int trendX = rateX + this.font.width(rateStr) + 4;
            final String trendStr;
            final int trendColor;
            if (Math.abs(entry.trend) < 2f) {
                trendStr = "\u2192"; trendColor = this.DIM;
            } else if (entry.trend > 0f) {
                trendStr = "\u2191" + String.format("%.0f%%", entry.trend); trendColor = -12255352;
            } else {
                trendStr = "\u2193" + String.format("%.0f%%", Math.abs(entry.trend)); trendColor = this.WARN;
            }
            g.drawString(this.font, trendStr, trendX, rowY2 + 5, trendColor);
        }

        if (dispEntries.size() > visibleRows) {
            final int scrollBarH = Math.max(8, tableH * visibleRows / dispEntries.size());
            final int scrollBarY = tableY + (maxScroll > 0 ? (int)(this.productionScrollOffset / (float)maxScroll * (tableH - scrollBarH)) : 0);
            g.fill(this.graphLeft + this.graphWidth - 3, scrollBarY, this.graphLeft + this.graphWidth - 1, scrollBarY + scrollBarH, -12303258);
        }

        if (hoveredEntry != null) {
            final ItemStack tipIcon = this.resolveItemIcon(hoveredEntry.label);
            final String tipRate = String.format("%.2f items/min", hoveredEntry.rate);
            final String tipType = this.describeProductionSource(hoveredEntry);
            final int tipW = Math.max(120, this.font.width(hoveredEntry.label) + (tipIcon.isEmpty() ? 6 : 26));
            final int tipH = tipIcon.isEmpty() ? 38 : 42;
            int tipX = mx + 10;
            int tipY = my - 6;
            if (tipX + tipW > this.graphLeft + this.graphWidth) tipX = mx - tipW - 4;
            if (tipY + tipH > this.graphTop + this.graphHeight) tipY = my - tipH;
            g.fill(tipX, tipY, tipX + tipW, tipY + tipH, -1072889856);
            this.border(g, tipX, tipY, tipW, tipH, this.BORDER);
            int tipCX = tipX + 4;
            int tipCY = tipY + 4;
            if (!tipIcon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate((float)tipCX, (float)tipCY, 200f);
                g.pose().scale(0.875f, 0.875f, 1f);
                g.renderItem(tipIcon, 0, 0);
                g.pose().popPose();
                tipCX += 16;
            }
            g.drawString(this.font, hoveredEntry.label, tipCX, tipCY + 2, this.ACCENT);
            tipCX = tipX + 4; tipCY += 14;
            g.drawString(this.font, tipRate, tipCX, tipCY, this.TEXT);
            tipCY += 10;
            g.drawString(this.font, tipType, tipCX, tipCY, this.DIM);
        }
    }

    private double computePctRatePerMin(TimeSeriesStore pctStore) {
        if (pctStore == null || pctStore.getSampleCount() < 3) return 0.0;
        final float[] data = pctStore.getDataForTimeScale(60);
        if (data == null || data.length < 3) return 0.0;
        final LinearRegression.RegressionCoefficients coeffs = LinearRegression.compute(data, 0, data.length);
        if (coeffs == null) return 0.0;
        return coeffs.slope() * 60.0;
    }

    private void renderInventoryStatus(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        this.prodRowHits.clear();
        this.inventoryStorageHits.clear();

        final List<CapacitySourceInfo> capSources = this.buildInventoryCapacitySources(channels);
        final List<CapacitySourceInfo> storageSrc = new ArrayList<>();
        final List<CapacitySourceInfo> reserveSrc = new ArrayList<>();
        final List<CapacitySourceInfo> fluidSrc = new ArrayList<>();
        for (CapacitySourceInfo src : capSources) {
            if (src.amountType == MetricType.STRESS_SU) continue;
            else if (src.amountType == MetricType.FLUID_MB && src.fuelReserve) reserveSrc.add(src);
            else if (src.amountType == MetricType.FLUID_MB) fluidSrc.add(src);
            else storageSrc.add(src);
        }
        fluidSrc.addAll(this.buildFlowMeterFluidEntries(channels));

        final List<ProductionEntry> producing = this.buildProductionEntries(channels);
        final List<ProductionEntry> consuming = this.buildConsumingEntries(channels);
        final java.util.Comparator<ProductionEntry> flowSort = this.inventorySortMode == 0
                ? Comparator.comparingDouble((ProductionEntry e) -> -Math.abs(e.rate))
                : Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT));
        producing.sort(flowSort);
        consuming.sort(flowSort);
        final java.util.Set<String> activeKeys = new java.util.HashSet<>();
        for (ProductionEntry e : producing) activeKeys.add(e.label.toLowerCase(java.util.Locale.ROOT));
        for (ProductionEntry e : consuming) activeKeys.add(e.label.toLowerCase(java.util.Locale.ROOT));
        final List<InventoryStableEntry> stableEntries = this.buildStableInventoryEntries(channels, activeKeys);

        final boolean noData = storageSrc.isEmpty() && reserveSrc.isEmpty() && fluidSrc.isEmpty() && producing.isEmpty() && consuming.isEmpty() && stableEntries.isEmpty();
        if (noData) {
            g.drawCenteredString(this.font, "\u2261 No inventory data", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
            g.drawCenteredString(this.font, "Connect: Stockpile Switch, Fluid Tank, Content Observer, Vault", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
            return;
        }

        final String[] sortNames = { "Rate", "A-Z" };
        final String sortBtn = "[" + sortNames[this.inventorySortMode] + "]";
        this.prodSortBtnW = this.font.width(sortBtn) + 4;
        this.prodSortBtnH = 10;
        this.prodSortBtnX = this.graphLeft + this.graphWidth - this.prodSortBtnW - 2;
        this.prodSortBtnY = this.graphTop + 2;

        g.drawString(this.font, "\u2261 Inventory Status", this.graphLeft + 4, this.graphTop + 4, this.ACCENT);
        final boolean sortHover = mx >= this.prodSortBtnX && mx <= this.prodSortBtnX + this.prodSortBtnW
                && my >= this.prodSortBtnY && my <= this.prodSortBtnY + this.prodSortBtnH;
        g.drawString(this.font, sortBtn, this.prodSortBtnX + 2, this.graphTop + 4, sortHover ? this.ACCENT : this.DIM);

        final int KIND_HEADER = 0, KIND_CAP = 1, KIND_FLOW = 2, KIND_IDLE = 3, KIND_ITEM = 4;
        final List<Integer> rowKind = new ArrayList<>();
        final List<String> rowText = new ArrayList<>();
        final List<CapacitySourceInfo> rowCap = new ArrayList<>();
        final List<ProductionEntry> rowFlow = new ArrayList<>();
        final List<int[]> rowItem = new ArrayList<>();

        if (!storageSrc.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\ud83d\udce6 STORAGE (" + storageSrc.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (CapacitySourceInfo s : storageSrc) {
                rowKind.add(KIND_CAP); rowText.add(null); rowCap.add(s); rowFlow.add(null); rowItem.add(null);
                if (this.inventoryExpandedSources.contains(s.sourceId)) {
                    final List<StatsDisplayPanelBE.DataChannel> itemChs = this.collectItemChannelsForSource(channels, s.sourceId);
                    for (StatsDisplayPanelBE.DataChannel ich : itemChs) {
                        rowKind.add(KIND_ITEM);
                        rowText.add(ich.getLabel() == null || ich.getLabel().isBlank() ? ich.getId() : ich.getLabel());
                        rowCap.add(null); rowFlow.add(null);
                        rowItem.add(new int[]{ Math.max(0, Math.round(ich.getStore().getLatestValue())) });
                    }
                    if (itemChs.isEmpty()) {
                        rowKind.add(KIND_ITEM); rowText.add("(no item data, Content Observer required for breakdown)"); rowCap.add(null); rowFlow.add(null); rowItem.add(new int[]{0});
                    }
                }
            }
        }
        if (!reserveSrc.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\u26fd FUEL RESERVE (" + reserveSrc.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (CapacitySourceInfo s : reserveSrc) { rowKind.add(KIND_CAP); rowText.add(null); rowCap.add(s); rowFlow.add(null); rowItem.add(null); }
        }
        if (!fluidSrc.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\ud83e\udea3 FLUID (" + fluidSrc.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (CapacitySourceInfo s : fluidSrc) { rowKind.add(KIND_CAP); rowText.add(null); rowCap.add(s); rowFlow.add(null); rowItem.add(null); }
        }
        if (!producing.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\u25b6 PRODUCING (" + producing.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (ProductionEntry e : producing) { rowKind.add(KIND_FLOW); rowText.add(null); rowCap.add(null); rowFlow.add(e); rowItem.add(null); }
        }
        if (!consuming.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\u25bc CONSUMING (" + consuming.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (ProductionEntry e : consuming) { rowKind.add(KIND_FLOW); rowText.add(null); rowCap.add(null); rowFlow.add(e); rowItem.add(null); }
        }
        if (!stableEntries.isEmpty()) {
            rowKind.add(KIND_HEADER); rowText.add("\u25a1 STABLE (" + stableEntries.size() + ")"); rowCap.add(null); rowFlow.add(null); rowItem.add(null);
            for (InventoryStableEntry entry : stableEntries) { rowKind.add(KIND_IDLE); rowText.add(entry.displayText()); rowCap.add(null); rowFlow.add(null); rowItem.add(null); }
        }

        final int rowH = 20;
        final int tableY = this.graphTop + 18;
        final int tableH = this.graphHeight - 22;
        final int visibleRows = Math.max(1, tableH / rowH);
        final int maxScroll = Math.max(0, rowKind.size() - visibleRows);
        this.inventoryScrollOffset = Math.min(this.inventoryScrollOffset, maxScroll);

        float maxFlowRate = 0.001f;
        for (ProductionEntry e : producing) maxFlowRate = Math.max(maxFlowRate, e.rate);
        for (ProductionEntry e : consuming) maxFlowRate = Math.max(maxFlowRate, Math.abs(e.rate));

        final int barAreaW = this.graphWidth - 12;
        final int nameW = barAreaW * 2 / 5;
        final int barW = barAreaW / 4;
        final int iconColW = 14;

        ProductionEntry hoveredEntry = null;
        for (int i = 0; i < visibleRows && i + this.inventoryScrollOffset < rowKind.size(); ++i) {
            final int ridx = i + this.inventoryScrollOffset;
            final int kind = rowKind.get(ridx);
            final int rowY = tableY + i * rowH;

            if (kind == KIND_HEADER) {
                final String hdrText = rowText.get(ridx);
                final boolean isConsuming = hdrText != null && hdrText.contains("CONSUMING");
                final int hdrColor = isConsuming ? this.WARN : -12255352;
                g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 352321535);
                this.hline(g, this.graphLeft + 4, this.graphLeft + this.graphWidth - 4, rowY + rowH - 1, hdrColor & 0x55FFFFFF);
                g.drawString(this.font, hdrText != null ? hdrText : "", this.graphLeft + 6, rowY + 4, hdrColor);
                continue;
            }

            if (kind == KIND_IDLE) {
                final String idleText = rowText.get(ridx);
                if (i % 2 == 0) g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 201326591);
                int cx = this.graphLeft + 4;
                if (idleText != null) {
                    final ItemStack idleIcon = this.resolveItemIcon(idleText);
                    if (!idleIcon.isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate((float)cx, (float)(rowY + 1), 0f);
                        g.pose().scale(0.75f, 0.75f, 1f);
                        g.renderItem(idleIcon, 0, 0);
                        g.pose().popPose();
                        cx += iconColW;
                    }
                    g.drawString(this.font, this.truncate(idleText, nameW - 4), cx, rowY + 4, this.DIM);
                }
                g.drawString(this.font, "stable", this.graphLeft + 4 + iconColW + nameW + 4, rowY + 4, -8355712);
                continue;
            }

            if (kind == KIND_CAP) {
                final CapacitySourceInfo src = rowCap.get(ridx);
                if (src == null) continue;
                final boolean stable = Math.abs(computePctRatePerMin(src.pctStore)) < 0.15;
                final int pct = Math.round(src.pct);
                if (i % 2 == 0) g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 301989887);
                final boolean rowHoveredCap = mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= rowY && my < rowY + rowH;
                if (rowHoveredCap) g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 587202559);
                if (src.subLine == null) {
                    this.inventoryStorageHits.add(new InventoryStorageRowHit(src.sourceId, rowY, rowY + rowH));
                }
                final int fillColor = src.hasCapacity
                        ? (src.pct > 90f ? this.WARN : src.pct < 15f ? -9699082 : (stable ? -8355712 : -12255352))
                        : -8355712;
                final boolean expanded = this.inventoryExpandedSources.contains(src.sourceId);
                final String chevron = src.subLine != null ? "" : (expanded ? "[-] " : "[+] ");
                g.drawString(this.font, chevron, this.graphLeft + 4, rowY + 2, this.ACCENT);
                final int chevW = this.font.width(chevron);
                g.drawString(this.font, this.truncate(src.displayLabel, nameW - 4 - chevW), this.graphLeft + 4 + chevW, rowY + 2, stable ? this.DIM : this.TEXT);
                final String sourceTag = src.sourceTypeRaw == null || src.sourceTypeRaw.isBlank() ? "Unknown source" : src.sourceTypeRaw;
                g.drawString(this.font, this.truncate(sourceTag, nameW - 4), this.graphLeft + 4, rowY + 11, this.DIM);

                final int fbX = this.graphLeft + 4 + nameW;
                final int fbY = rowY + 5;
                if (src.hasCapacity) {
                    g.fill(fbX, fbY, fbX + barW, fbY + 8, -15066582);
                    final int fillW = Math.max(0, Math.min(barW, (int)(src.pct / 100f * barW)));
                    if (fillW > 0) g.fill(fbX, fbY, fbX + fillW, fbY + 8, fillColor);
                    this.border(g, fbX, fbY, barW, 8, this.DIM);
                }
                else {
                    g.fill(fbX, fbY, fbX + barW, fbY + 8, 201326591);
                    g.drawCenteredString(this.font, "amount only", fbX + barW / 2, fbY, this.DIM);
                }

                final String amountLine = src.hasCapacity
                        ? this.formatMetricCompact(src.amountType, src.used) + " / " + this.formatMetricCompact(src.amountType, src.total)
                        : this.formatMetricCompact(src.amountType, src.used) + src.usedSuffix;
                final int amountX = fbX + barW + 6;
                g.drawString(this.font, this.truncate(amountLine, this.graphLeft + this.graphWidth - amountX - 4), amountX, rowY + 2, stable ? this.DIM : this.TEXT);
                final String fullnessLine = src.subLine != null ? src.subLine : (src.hasCapacity ? (pct + "% full") : "amount only");
                g.drawString(this.font, fullnessLine, amountX, rowY + 11, fillColor);
                continue;
            }

            if (kind == KIND_ITEM) {
                final String itemLabel = rowText.get(ridx);
                final int[] itemData = rowItem.get(ridx);
                final int count = itemData != null && itemData.length > 0 ? itemData[0] : 0;
                if (i % 2 == 0) g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 167772160);
                int cxItem = this.graphLeft + 18;
                if (itemLabel != null) {
                    final ItemStack itemIcon = this.resolveItemIcon(itemLabel);
                    if (!itemIcon.isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate((float)cxItem, (float)(rowY + 2), 0f);
                        g.pose().scale(0.75f, 0.75f, 1f);
                        g.renderItem(itemIcon, 0, 0);
                        g.pose().popPose();
                        cxItem += iconColW;
                    }
                    g.drawString(this.font, this.truncate(itemLabel, this.graphWidth - (cxItem - this.graphLeft) - 60), cxItem, rowY + 6, this.TEXT);
                }
                if (count > 0) {
                    g.drawString(this.font, "x " + count, this.graphLeft + this.graphWidth - 4 - this.font.width("x " + count), rowY + 6, this.ACCENT);
                }
                continue;
            }

            if (kind == KIND_FLOW) {
                final ProductionEntry entry = rowFlow.get(ridx);
                if (entry == null) continue;
                final boolean isConsumeEntry = consuming.contains(entry);
                final int rowColor = isConsumeEntry ? this.WARN : -12255352;
                final boolean rowHovered = mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= rowY && my < rowY + rowH;
                if (i % 2 == 0) g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 301989887);
                if (rowHovered) {
                    g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 587202559);
                    hoveredEntry = entry;
                }
                this.prodRowHits.add(new ProductionRowHit(entry, rowY, rowY + rowH));
                int colX = this.graphLeft + 4;
                final ItemStack icon = this.resolveItemIcon(entry.label);
                if (!icon.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate((float)colX, (float)(rowY + 1), 0f);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                }
                colX += iconColW;
                g.drawString(this.font, this.truncate(entry.label, nameW - 4), colX, rowY + 2, this.TEXT);
                final String originLabel = this.describeProductionOrigin(entry);
                if (originLabel != null && !originLabel.isBlank()) {
                    g.drawString(this.font, this.truncate(originLabel, nameW - 4), colX, rowY + 11, this.DIM);
                }
                final int barX = this.graphLeft + 4 + iconColW + nameW;
                final int barFillW = (int)(Math.abs(entry.rate) / maxFlowRate * barW);
                g.fill(barX, rowY + 3, barX + barW, rowY + rowH - 3, -15066582);
                if (barFillW > 0) g.fill(barX, rowY + 3, barX + barFillW, rowY + rowH - 3, rowColor);
                final int rateX = barX + barW + 4;
                final String rateStr = String.format("%+.1f/m", entry.rate);
                g.drawString(this.font, rateStr, rateX, rowY + 4, rowColor);
                final int trendX = rateX + this.font.width(rateStr) + 4;
                final String trendStr;
                final int trendColor;
                if (Math.abs(entry.trend) < 2f) { trendStr = "\u2192"; trendColor = this.DIM; }
                else if (entry.trend > 0f) { trendStr = "\u2191"; trendColor = -12255352; }
                else { trendStr = "\u2193"; trendColor = this.WARN; }
                g.drawString(this.font, trendStr, trendX, rowY + 4, trendColor);
            }
        }

        if (rowKind.size() > visibleRows) {
            final int scrollBarH = Math.max(8, tableH * visibleRows / rowKind.size());
            final int scrollBarY = tableY + (maxScroll > 0 ? (int)(this.inventoryScrollOffset / (float)maxScroll * (tableH - scrollBarH)) : 0);
            g.fill(this.graphLeft + this.graphWidth - 3, scrollBarY, this.graphLeft + this.graphWidth - 1, scrollBarY + scrollBarH, -12303258);
        }

        if (hoveredEntry != null) {
            final ItemStack tipIcon = this.resolveItemIcon(hoveredEntry.label);
            final String tipRate = String.format("%.2f items/min", hoveredEntry.rate);
            final String tipType = this.describeProductionSource(hoveredEntry);
            final int tipW = Math.max(120, this.font.width(hoveredEntry.label) + (tipIcon.isEmpty() ? 6 : 26));
            final int tipH = tipIcon.isEmpty() ? 38 : 42;
            int tipX = mx + 10;
            int tipY = my - 6;
            if (tipX + tipW > this.graphLeft + this.graphWidth) tipX = mx - tipW - 4;
            if (tipY + tipH > this.graphTop + this.graphHeight) tipY = my - tipH;
            g.fill(tipX, tipY, tipX + tipW, tipY + tipH, -1072889856);
            this.border(g, tipX, tipY, tipW, tipH, this.BORDER);
            int tipCX = tipX + 4;
            int tipCY = tipY + 4;
            if (!tipIcon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate((float)tipCX, (float)tipCY, 200f);
                g.pose().scale(0.875f, 0.875f, 1f);
                g.renderItem(tipIcon, 0, 0);
                g.pose().popPose();
                tipCX += 16;
            }
            g.drawString(this.font, hoveredEntry.label, tipCX, tipCY + 2, this.ACCENT);
            tipCX = tipX + 4; tipCY += 14;
            g.drawString(this.font, tipRate, tipCX, tipCY, this.TEXT);
            tipCY += 10;
            g.drawString(this.font, tipType, tipCX, tipCY, this.DIM);
        }
    }

    private void renderFactoryStatus(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mx, int my) {
        final List<FactoryStatusEntry> entries = this.buildFactoryStatusEntries(channels);
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font, "No factory status data", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 - 10, this.DIM);
            g.drawCenteredString(this.font, "Connect: Stressometer, Speedometer, Smart Gearshift, Auto Clutch, Load Balancer", this.graphLeft + this.graphWidth / 2, this.graphTop + this.graphHeight / 2 + 4, -12303258);
            return;
        }

        final String[] sortNames = { "Type", "Load", "A-Z" };
        final String sortBtn = "[" + sortNames[this.factoryStatusSortMode % sortNames.length] + "]";
        this.prodSortBtnW = this.font.width(sortBtn) + 4;
        this.prodSortBtnH = 10;
        this.prodSortBtnX = this.graphLeft + this.graphWidth - this.prodSortBtnW - 2;
        this.prodSortBtnY = this.graphTop + 2;

        g.drawString(this.font, "Factory Status", this.graphLeft + 4, this.graphTop + 4, this.ACCENT);
        final String countLabel = entries.size() + (entries.size() == 1 ? " source" : " sources");
        g.drawString(this.font, countLabel, this.graphLeft + this.graphWidth / 2 - this.font.width(countLabel) / 2, this.graphTop + 4, this.DIM);
        final boolean sortHover = mx >= this.prodSortBtnX && mx <= this.prodSortBtnX + this.prodSortBtnW
                && my >= this.prodSortBtnY && my <= this.prodSortBtnY + this.prodSortBtnH;
        g.drawString(this.font, sortBtn, this.prodSortBtnX + 2, this.graphTop + 4, sortHover ? this.ACCENT : this.DIM);

        final int KIND_HEADER = 0, KIND_ENTRY = 1;
        final List<Integer> rowKind = new ArrayList<Integer>();
        final List<String> rowText = new ArrayList<String>();
        final List<FactoryStatusEntry> rowEntry = new ArrayList<FactoryStatusEntry>();
        String currentGroup = "";
        for (FactoryStatusEntry entry : entries) {
            if (!entry.group.equals(currentGroup)) {
                currentGroup = entry.group;
                rowKind.add(KIND_HEADER);
                rowText.add(currentGroup + " (" + this.countFactoryStatusGroup(entries, currentGroup) + ")");
                rowEntry.add(null);
            }
            rowKind.add(KIND_ENTRY);
            rowText.add(null);
            rowEntry.add(entry);
        }

        final int rowH = 20;
        final int tableY = this.graphTop + 18;
        final int tableH = this.graphHeight - 22;
        final int visibleRows = Math.max(1, tableH / rowH);
        final int maxScroll = Math.max(0, rowKind.size() - visibleRows);
        this.factoryStatusScrollOffset = Math.min(this.factoryStatusScrollOffset, maxScroll);

        final int nameW = Math.max(120, this.graphWidth * 2 / 5);
        final int barW = Math.max(70, this.graphWidth / 5);
        for (int i = 0; i < visibleRows && i + this.factoryStatusScrollOffset < rowKind.size(); ++i) {
            final int ridx = i + this.factoryStatusScrollOffset;
            final int rowY = tableY + i * rowH;
            if (rowKind.get(ridx) == KIND_HEADER) {
                final String hdrText = rowText.get(ridx);
                g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 352321535);
                this.hline(g, this.graphLeft + 4, this.graphLeft + this.graphWidth - 4, rowY + rowH - 1, this.ACCENT & 0x55FFFFFF);
                g.drawString(this.font, hdrText == null ? "" : hdrText, this.graphLeft + 6, rowY + 5, this.ACCENT);
                continue;
            }
            final FactoryStatusEntry entry = rowEntry.get(ridx);
            if (entry == null) {
                continue;
            }
            if (i % 2 == 0) {
                g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 301989887);
            }
            final boolean hovered = mx >= this.graphLeft && mx <= this.graphLeft + this.graphWidth && my >= rowY && my < rowY + rowH;
            if (hovered) {
                g.fill(this.graphLeft + 1, rowY, this.graphLeft + this.graphWidth - 1, rowY + rowH, 587202559);
            }

            g.drawString(this.font, this.truncate(entry.label, nameW - 4), this.graphLeft + 4, rowY + 2, this.TEXT);
            g.drawString(this.font, this.truncate(entry.sourceInfo, nameW - 4), this.graphLeft + 4, rowY + 11, this.DIM);

            final int barX = this.graphLeft + 4 + nameW;
            if (entry.hasPercent) {
                g.fill(barX, rowY + 5, barX + barW, rowY + 13, -15066582);
                final int fillW = Math.max(0, Math.min(barW, (int)(entry.percent / 100.0f * barW)));
                if (fillW > 0) {
                    g.fill(barX, rowY + 5, barX + fillW, rowY + 13, entry.statusColor);
                }
                this.border(g, barX, rowY + 5, barW, 8, this.DIM);
                g.drawString(this.font, String.format("%.0f%%", entry.percent), barX + barW + 5, rowY + 6, entry.statusColor);
            }
            else {
                g.fill(barX, rowY + 5, barX + barW, rowY + 13, 201326591);
                g.drawCenteredString(this.font, "info", barX + barW / 2, rowY + 5, this.DIM);
            }

            final int detailX = barX + barW + 42;
            if (detailX < this.graphLeft + this.graphWidth - 20) {
                g.drawString(this.font, this.truncate(entry.primary, this.graphLeft + this.graphWidth - detailX - 4), detailX, rowY + 2, entry.statusColor);
                g.drawString(this.font, this.truncate(entry.secondary, this.graphLeft + this.graphWidth - detailX - 4), detailX, rowY + 11, this.DIM);
            }
        }

        if (rowKind.size() > visibleRows) {
            final int scrollBarH = Math.max(8, tableH * visibleRows / rowKind.size());
            final int scrollBarY = tableY + (maxScroll > 0 ? (int)(this.factoryStatusScrollOffset / (float)maxScroll * (tableH - scrollBarH)) : 0);
            g.fill(this.graphLeft + this.graphWidth - 3, scrollBarY, this.graphLeft + this.graphWidth - 1, scrollBarY + scrollBarH, -12303258);
        }
    }

    private int countFactoryStatusGroup(List<FactoryStatusEntry> entries, String group) {
        int count = 0;
        for (FactoryStatusEntry entry : entries) {
            if (entry.group.equals(group)) {
                ++count;
            }
        }
        return count;
    }

    private List<FactoryStatusEntry> buildFactoryStatusEntries(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>> bySource = new LinkedHashMap<String, List<StatsDisplayPanelBE.DataChannel>>();
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            bySource.computeIfAbsent(sid, ignored -> new ArrayList<StatsDisplayPanelBE.DataChannel>()).add(ch);
        }

        final List<FactoryStatusEntry> entries = new ArrayList<FactoryStatusEntry>();
        for (Map.Entry<String, List<StatsDisplayPanelBE.DataChannel>> entry : bySource.entrySet()) {
            final FactoryStatusEntry statusEntry = this.resolveFactoryStatusSource(entry.getKey(), entry.getValue());
            if (statusEntry != null) {
                entries.add(statusEntry);
            }
        }
        for (StatsDisplayPanelBE.SafetyClutchLink link : this.panel.getSafetyClutchLinks()) {
            entries.add(this.factoryStatusFromSafetyClutch(link));
        }
        for (StatsDisplayPanelBE.AutoTransmissionClutchLink link : this.panel.getAutoTransmissionClutchLinks()) {
            entries.add(this.factoryStatusFromAutoTransmission(link));
        }
        for (StatsDisplayPanelBE.LoadBalancerLink link : this.panel.getLoadBalancerLinks()) {
            entries.add(this.factoryStatusFromLoadBalancer(link));
        }

        final Comparator<FactoryStatusEntry> comparator = switch (this.factoryStatusSortMode % 3) {
            case 1 -> Comparator.comparing((FactoryStatusEntry e) -> e.group, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((FactoryStatusEntry e) -> -e.sortValue)
                    .thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparing((FactoryStatusEntry e) -> e.group, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing((FactoryStatusEntry e) -> e.group, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
        };
        entries.sort(comparator);
        return entries;
    }

    private FactoryStatusEntry resolveFactoryStatusSource(String sid, List<StatsDisplayPanelBE.DataChannel> group) {
        if ("legacy".equals(sid)) {
            return null;
        }
        final SourceConfig cfg = this.panel.getSourceRegistry().get(sid);
        final String sourceType = cfg != null ? cfg.getSourceType() : "";
        if (!this.isFactoryStatusSourceType(sourceType, group)) {
            return null;
        }
        final String label = cfg != null ? cfg.getDisplayLabel() : sid;
        final StatsDisplayPanelBE.DataChannel stressPctCh = this.findFactoryChannel(group, "stress_pct");
        final StatsDisplayPanelBE.DataChannel stressCh = this.findFactoryChannel(group, "stress_su");
        final StatsDisplayPanelBE.DataChannel capacityCh = this.findFactoryChannel(group, "capacity_su");
        final StatsDisplayPanelBE.DataChannel remainingCh = this.findFactoryChannel(group, "remaining_su");
        final StatsDisplayPanelBE.DataChannel rpmCh = this.findFactoryChannel(group, "rpm");
        final StatsDisplayPanelBE.DataChannel absRpmCh = this.findFactoryChannel(group, "abs_rpm");
        final StatsDisplayPanelBE.DataChannel generatedSpeedCh = this.findFactoryChannel(group, "generated_speed");
        final StatsDisplayPanelBE.DataChannel generatedRpmCh = this.findFactoryChannel(group, "generated_rpm");
        final StatsDisplayPanelBE.DataChannel producedStressCh = this.findFactoryChannel(group, "generated_stress_su");
        final StatsDisplayPanelBE.DataChannel engineCountCh = this.findFactoryChannel(group, "engine_count");
        final StatsDisplayPanelBE.DataChannel okEnginesCh = this.findFactoryChannel(group, "ok_engines");
        final StatsDisplayPanelBE.DataChannel stallingEnginesCh = this.findFactoryChannel(group, "stalling_engines");
        final StatsDisplayPanelBE.DataChannel offEnginesCh = this.findFactoryChannel(group, "off_engines");
        final StatsDisplayPanelBE.DataChannel fuelPctCh = this.findFactoryChannel(group, "fuel_fill_pct");
        final StatsDisplayPanelBE.DataChannel burnPctCh = this.findFactoryChannel(group, "burn_pct");
        final StatsDisplayPanelBE.DataChannel redstoneDisabledCh = this.findFactoryChannel(group, "redstone_disabled");
        final StatsDisplayPanelBE.DataChannel validFuelCh = this.findFactoryChannel(group, "valid_fuel");
        final StatsDisplayPanelBE.DataChannel speedPctCh = this.findFactoryChannel(group, "speed_pct");
        final StatsDisplayPanelBE.DataChannel runningCh = this.findFactoryChannel(group, "running");
        final StatsDisplayPanelBE.DataChannel overstressedCh = this.findFactoryChannel(group, "overstressed");

        final boolean hasStressCapacity = stressCh != null && capacityCh != null && this.latestOrDefault(capacityCh, 0.0f) > 0.5f;
        final float stressPct = stressPctCh != null
                ? this.latestOrDefault(stressPctCh, Float.NaN)
                : (hasStressCapacity ? this.latestOrDefault(stressCh, 0.0f) * 100.0f / Math.max(1.0f, this.latestOrDefault(capacityCh, 1.0f)) : Float.NaN);
        final float stress = this.latestOrDefault(stressCh, 0.0f);
        final float capacity = this.latestOrDefault(capacityCh, 0.0f);
        final float remaining = remainingCh != null ? this.latestOrDefault(remainingCh, 0.0f) : Math.max(0.0f, capacity - stress);
        float rpm = rpmCh != null ? this.latestOrDefault(rpmCh, 0.0f) : this.latestOrDefault(absRpmCh, 0.0f);
        if (Math.abs(rpm) <= 0.01f) {
            rpm = generatedSpeedCh != null ? this.latestOrDefault(generatedSpeedCh, 0.0f) : this.latestOrDefault(generatedRpmCh, 0.0f);
        }
        final float producedStress = this.latestOrDefault(producedStressCh, 0.0f);
        final float totalEngines = this.latestOrDefault(engineCountCh, 0.0f);
        final float okEngines = this.latestOrDefault(okEnginesCh, 0.0f);
        final float stallingEngines = this.latestOrDefault(stallingEnginesCh, 0.0f);
        final float offEngines = this.latestOrDefault(offEnginesCh, 0.0f);
        final boolean hasEngineCounts = totalEngines > 0.0f || okEnginesCh != null || stallingEnginesCh != null || offEnginesCh != null;
        final float engineHealthPct = hasEngineCounts ? okEngines * 100.0f / Math.max(1.0f, totalEngines) : Float.NaN;
        final boolean redstoneDisabled = redstoneDisabledCh != null && this.latestOrDefault(redstoneDisabledCh, 0.0f) > 0.5f;
        final boolean validFuel = validFuelCh == null || this.latestOrDefault(validFuelCh, 1.0f) > 0.5f;
        final boolean running = runningCh == null
                ? (Math.abs(rpm) > 0.01f || okEngines > 0.0f)
                : this.latestOrDefault(runningCh, Math.abs(rpm) > 0.01f ? 1.0f : 0.0f) > 0.5f;
        final boolean overstressed = overstressedCh != null && this.latestOrDefault(overstressedCh, 0.0f) > 0.5f;
        final boolean hasPercent = !Float.isNaN(stressPct) || hasEngineCounts;
        final float displayPercent = !Float.isNaN(stressPct) ? stressPct : (!Float.isNaN(engineHealthPct) ? engineHealthPct : 0.0f);
        final int color = overstressed || redstoneDisabled || !validFuel
                ? this.WARN
                : (!running ? this.DIM : (!Float.isNaN(stressPct) && stressPct > 90.0f ? this.WARN : this.ACCENT));
        final String groupName = this.factoryStatusGroupName(sourceType);
        final String sourceInfo = (sourceType == null || sourceType.isBlank()) ? "Kinetic source" : sourceType;
        final String engineSummary = hasEngineCounts
                ? Math.round(okEngines) + "/" + Math.max(1, Math.round(totalEngines)) + " engines"
                : "";
        final String primary = Math.abs(rpm) > 0.01f
                ? "RPM " + this.formatCompact(rpm) + (engineSummary.isEmpty() ? "" : "  |  " + engineSummary)
                : (!Float.isNaN(stressPct) ? "Stress " + this.formatCompact(stressPct) + "%" : (!engineSummary.isEmpty() ? engineSummary : "Kinetic info"));
        final String secondary;
        if (hasStressCapacity) {
            secondary = this.formatCompact(stress) + " / " + this.formatCompact(capacity) + " SU, " + this.formatCompact(remaining) + " free"
                    + (producedStress > 0.0f ? ", out " + this.formatCompact(producedStress) + " SU" : "");
        }
        else if (producedStress > 0.0f) {
            secondary = "Output " + this.formatCompact(producedStress) + " SU"
                    + (hasEngineCounts ? ", " + Math.round(stallingEngines) + " stall, " + Math.round(offEngines) + " off" : "");
        }
        else if (hasEngineCounts) {
            secondary = Math.round(stallingEngines) + " stalling, " + Math.round(offEngines) + " off";
        }
        else if (speedPctCh != null) {
            secondary = "Speed " + this.formatCompact(this.latestOrDefault(speedPctCh, 0.0f)) + "%";
        }
        else if (fuelPctCh != null || burnPctCh != null) {
            secondary = "Fuel " + this.formatCompact(this.latestOrDefault(fuelPctCh != null ? fuelPctCh : burnPctCh, 0.0f)) + "%";
        }
        else {
            secondary = running ? "Running" : "Idle";
        }
        return new FactoryStatusEntry(groupName, label, sourceInfo, primary, secondary, displayPercent, hasPercent, color, hasPercent ? displayPercent : Math.max(Math.abs(rpm), producedStress));
    }

    private FactoryStatusEntry factoryStatusFromSafetyClutch(StatsDisplayPanelBE.SafetyClutchLink link) {
        final float stressPct = link.getStressPercent();
        final float stress = link.getStressSU();
        final float capacity = link.getCapacitySU();
        final float remaining = Math.max(0.0f, capacity - stress);
        final int color = link.isTripped() ? this.WARN : (!link.isNetworkRunning() ? this.DIM : (stressPct > 90.0f ? this.WARN : this.ACCENT));
        final String label = "Auto Clutch @ " + link.getSourcePos().toShortString();
        final String primary = link.isTripped() ? "TRIPPED" : (link.isNetworkRunning() ? "Running" : "Idle");
        final String secondary = this.formatCompact(stress) + " / " + this.formatCompact(capacity) + " SU, " + this.formatCompact(remaining) + " free";
        return new FactoryStatusEntry("Auto Clutch", label, "Safety/auto clutch link", primary, secondary, stressPct, true, color, stressPct);
    }

    private FactoryStatusEntry factoryStatusFromAutoTransmission(StatsDisplayPanelBE.AutoTransmissionClutchLink link) {
        final float stressPct = link.getStressPercent();
        final float stress = link.getStressSU();
        final float capacity = link.getCapacitySU();
        final float remaining = Math.max(0.0f, capacity - stress);
        final int color = link.getAlarmState() != AutoTransmissionClutchBE.AlarmState.NORMAL ? this.WARN : (!link.isNetworkRunning() ? this.DIM : (stressPct > 90.0f ? this.WARN : this.ACCENT));
        final String label = "Auto Variable Clutch @ " + link.getSourcePos().toShortString();
        final String primary = "RPM " + this.formatCompact(link.getTransmittedSpeed()) + "  mod " + this.formatCompact(link.getCurrentModifier());
        final String secondary = this.formatCompact(stress) + " / " + this.formatCompact(capacity) + " SU, " + this.formatCompact(remaining) + " free";
        return new FactoryStatusEntry("Auto Variable Clutch", label, "Transmission clutch link", primary, secondary, stressPct, true, color, stressPct);
    }

    private FactoryStatusEntry factoryStatusFromLoadBalancer(StatsDisplayPanelBE.LoadBalancerLink link) {
        final float stressPct = link.getStressPercent();
        final float stress = link.getStressSU();
        final float capacity = link.getCapacitySU();
        final int color = !link.isNetworkRunning() ? this.DIM : (stressPct > 90.0f ? this.WARN : this.ACCENT);
        final String label = "Load Balancer @ " + link.getSourcePos().toShortString();
        final String primary = "L " + this.formatCompact(link.getTransmittedSpeedLeft()) + " / R " + this.formatCompact(link.getTransmittedSpeedRight()) + " RPM";
        final String secondary = this.formatCompact(stress) + " / " + this.formatCompact(capacity) + " SU, L/R " + this.formatCompact(link.getLeftStressSU()) + "/" + this.formatCompact(link.getRightStressSU());
        return new FactoryStatusEntry("Load Balancer", label, "Load balancer link", primary, secondary, stressPct, true, color, stressPct);
    }

    private StatsDisplayPanelBE.DataChannel findFactoryChannel(List<StatsDisplayPanelBE.DataChannel> channels, String metricId) {
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = channel.getId().toLowerCase(java.util.Locale.ROOT);
            if (metricId.equals(this.extractFactoryAutoMetricId(id))) {
                return channel;
            }
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = channel.getId().toLowerCase(java.util.Locale.ROOT);
            if (this.extractFactoryAutoMetricId(id) == null && id.endsWith("_" + metricId)) {
                return channel;
            }
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = channel.getId().toLowerCase(java.util.Locale.ROOT);
            if (this.extractFactoryAutoMetricId(id) == null && id.contains(metricId)) {
                return channel;
            }
        }
        return null;
    }

    private String extractFactoryAutoMetricId(String channelId) {
        if (channelId == null) {
            return null;
        }
        final int index = channelId.indexOf("_auto_");
        return index >= 0 ? channelId.substring(index + 6) : null;
    }

    private float latestOrDefault(StatsDisplayPanelBE.DataChannel channel, float fallback) {
        if (channel == null || channel.getStore().getSampleCount() <= 0) {
            return fallback;
        }
        return channel.getStore().getLatestValue();
    }

    private boolean isFactoryStatusSourceType(String sourceType, List<StatsDisplayPanelBE.DataChannel> group) {
        final String lower = sourceType == null ? "" : sourceType.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("stressometer")
                || lower.contains("speedometer")
                || lower.contains("clutch")
                || lower.contains("gearshift")
                || lower.contains("balancer")
                || lower.contains("kinetic")
                || lower.contains("shaft")
                || lower.contains("cog")
                || lower.contains("gearbox")
                || lower.contains("motor")
                || lower.contains("mechanical")) {
            return true;
        }
        for (StatsDisplayPanelBE.DataChannel channel : group) {
            final MetricType metricType = channel.getStore().getMetricType();
            if (metricType == MetricType.RPM || metricType == MetricType.STRESS_SU || metricType == MetricType.STRESS_PERCENT) {
                return true;
            }
        }
        return false;
    }

    private String factoryStatusGroupName(String sourceType) {
        final String lower = sourceType == null ? "" : sourceType.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("stressometer")) return "Stressometer";
        if (lower.contains("speedometer")) return "Speedometer";
        if (lower.contains("load balancer") || lower.contains("balancer")) return "Load Balancer";
        if (lower.contains("diesel engine group")) return "Diesel Engine Groups";
        if (lower.contains("diesel engine")) return "Diesel Engines";
        if (lower.contains("variable") || lower.contains("transmission")) return "Auto Variable Clutch";
        if (lower.contains("clutch")) return "Auto Clutch";
        if (lower.contains("gearshift")) return "Smart Gearshift";
        return "Kinetic Blocks";
    }

    private boolean handleFactoryStatusScroll(double mx, double my, double delta) {
        if (!this.isFactoryStatusPage()) {
            return false;
        }
        if (mx < this.graphLeft || mx >= this.graphLeft + this.graphWidth || my < this.graphTop || my >= this.graphTop + this.graphHeight) {
            return false;
        }
        final int rowCount = this.getFactoryStatusRowCount(this.buildFactoryStatusEntries(this.getFilteredChannels()));
        final int visibleRows = Math.max(1, (this.graphHeight - 22) / 20);
        final int maxScroll = Math.max(0, rowCount - visibleRows);
        if (maxScroll <= 0) {
            return false;
        }
        this.factoryStatusScrollOffset = Math.max(0, Math.min(this.factoryStatusScrollOffset - (int)delta, maxScroll));
        return true;
    }

    private int getFactoryStatusRowCount(List<FactoryStatusEntry> entries) {
        int rows = 0;
        String currentGroup = "";
        for (FactoryStatusEntry entry : entries) {
            if (!entry.group.equals(currentGroup)) {
                currentGroup = entry.group;
                ++rows;
            }
            ++rows;
        }
        return rows;
    }

    private List<ProductionEntry> buildConsumingEntries(List<StatsDisplayPanelBE.DataChannel> channels) {
        final LinkedHashMap<String, ProductionAccumulator> aggregates = new LinkedHashMap<>();
        final long currentTick = this.getGuiRenderTick();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final TimeSeriesStore store = channel.getStore();
            if (store.getSampleCount() <= 0) continue;
            final String sourceType = this.getSourceTypeForChannel(channel);
            final float rate;
            final float trend;
            if (this.isProductionRateMetric(store.getMetricType())) {
                rate = this.computeProductionRate(store);
                trend = this.computeTrend(store);
            } else if (this.isObserverProductionSnapshotChannel(channel, sourceType)) {
                rate = this.computeObserverProductionRate(store, currentTick, PRODUCTION_RATE_WINDOW_SECONDS);
                trend = this.computeObserverProductionTrend(store, currentTick);
            } else {
                continue;
            }
            if (rate >= -MIN_VISIBLE_PRODUCTION_RATE) continue;
            final String label = this.resolveProductionEntryLabel(channel.getLabel());
            if (label.isEmpty()) continue;
            final String key = label.toLowerCase(java.util.Locale.ROOT);
            aggregates.computeIfAbsent(key, k -> new ProductionAccumulator(label))
                    .add(channel, rate, trend, sourceType);
        }
        final List<ProductionEntry> result = new ArrayList<>(aggregates.size());
        for (ProductionAccumulator acc : aggregates.values()) result.add(acc.toEntry());
        result.sort((a, b) -> Float.compare(a.rate, b.rate));
        return result;
    }

    private List<String> buildIdleProductionLabels(List<StatsDisplayPanelBE.DataChannel> channels, java.util.Set<String> activeKeys) {
        final List<InventoryStableEntry> stableEntries = this.buildStableInventoryEntries(channels, activeKeys);
        final List<String> result = new ArrayList<>(stableEntries.size());
        for (InventoryStableEntry entry : stableEntries) {
            result.add(entry.label);
        }
        return result;
    }

    private List<InventoryStableEntry> buildStableInventoryEntries(List<StatsDisplayPanelBE.DataChannel> channels, java.util.Set<String> activeKeys) {
        final LinkedHashMap<String, InventoryStableAccumulator> stableByItem = new LinkedHashMap<>();
        final long currentTick = this.getGuiRenderTick();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final TimeSeriesStore store = channel.getStore();
            if (store.getSampleCount() <= 0) continue;
            final float rate;
            final String sourceType = this.getSourceTypeForChannel(channel);
            final boolean observerSnapshot = this.isObserverProductionSnapshotChannel(channel, sourceType);
            if (this.isProductionRateMetric(store.getMetricType())) {
                rate = this.computeProductionRate(store);
            } else if (observerSnapshot) {
                rate = this.computeObserverProductionRate(store, currentTick, PRODUCTION_RATE_WINDOW_SECONDS);
            } else {
                continue;
            }
            if (Math.abs(rate) > MIN_VISIBLE_PRODUCTION_RATE) continue;
            final String label = this.resolveProductionEntryLabel(channel.getLabel());
            if (label.isEmpty()) continue;
            if (activeKeys.contains(label.toLowerCase(java.util.Locale.ROOT))) continue;
            final String key = label.toLowerCase(java.util.Locale.ROOT);
            InventoryStableAccumulator accumulator = stableByItem.get(key);
            if (accumulator == null) {
                accumulator = new InventoryStableAccumulator(label);
                stableByItem.put(key, accumulator);
            }
            if (observerSnapshot) {
                accumulator.addAmount(store.getLatestValue(), sourceType);
            }
            else {
                accumulator.addSource(sourceType);
            }
        }
        final List<InventoryStableEntry> result = new ArrayList<>(stableByItem.size());
        for (InventoryStableAccumulator accumulator : stableByItem.values()) {
            result.add(accumulator.toEntry());
        }
        result.sort(Comparator.comparing(entry -> entry.label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private java.util.Comparator<ProductionEntry> getProductionSortComparator(int mode) {
        return switch (mode % 3) {
            case 1 -> java.util.Comparator.comparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
            case 2 -> (a, b) -> Float.compare(b.trend, a.trend);
            default -> (a, b) -> Float.compare(Math.abs(b.rate), Math.abs(a.rate));
        };
    }

    private boolean isObserverProductionSnapshotChannel(StatsDisplayPanelBE.DataChannel channel, String sourceType) {
        return "Content Observer".equals(sourceType) && channel.getStore().getMetricType() == MetricType.ITEM_COUNT && (channel.getId().contains("_auto_item_") || channel.getId().startsWith("auto_item_"));
    }

    private String resolveProductionEntryLabel(String rawLabel) {
        final String label = (rawLabel != null) ? rawLabel.trim() : "";
        if (label.isEmpty()) {
            return "";
        }
        final int bulletIndex = label.lastIndexOf('\u2022');
        if (bulletIndex >= 0 && bulletIndex + 1 < label.length()) {
            final String suffix = label.substring(bulletIndex + 1).trim();
            if (!suffix.isEmpty()) {
                return suffix;
            }
        }
        if (label.regionMatches(true, 0, "Throughput:", 0, 11)) {
            final String suffix2 = label.substring(11).trim();
            return suffix2.isEmpty() ? label : suffix2;
        }
        if (label.regionMatches(true, 0, "Rate:", 0, 5)) {
            final String suffix2 = label.substring(5).trim();
            return suffix2.isEmpty() ? label : suffix2;
        }
        return label;
    }

    private float computeObserverProductionRate(TimeSeriesStore store, long currentTick, int windowSeconds) {
        if (store.getSampleCount() < 2 || currentTick - store.getLastSampleTick() > PRODUCTION_RATE_WINDOW_TICKS) {
            return 0.0f;
        }
        final int windowSamples = Math.min(store.getSampleCount(), Math.max(2, (int)Math.ceil(windowSeconds * 20.0 / PRODUCTION_SNAPSHOT_SAMPLE_TICKS) + 1));
        final float[] data = store.getDataWindow(windowSamples);
        if (data.length < 2) {
            return 0.0f;
        }
        final float delta = data[data.length - 1] - data[0];
        if (delta == 0.0f) {
            return 0.0f;
        }
        final float elapsedSeconds = Math.max(1.0f, (data.length - 1) * PRODUCTION_SNAPSHOT_SAMPLE_TICKS / 20.0f);
        return delta * 60.0f / elapsedSeconds;
    }

    private float computeObserverProductionTrend(TimeSeriesStore store, long currentTick) {
        final float recent = this.computeObserverProductionRate(store, currentTick, PRODUCTION_RATE_WINDOW_SECONDS);
        final float older = this.computeObserverProductionRate(store, currentTick, PRODUCTION_RATE_WINDOW_SECONDS * 2);
        if (Math.abs(older) <= MIN_VISIBLE_PRODUCTION_RATE) {
            return 0.0f;
        }
        return (recent - older) / Math.abs(older) * 100.0f;
    }

    private String getProductionSourceBadge(String sourceType) {
        return switch (sourceType) {
            case "Production Detector" -> "\u25c6D";
            case "Tunnel" -> "\u25c6T";
            case "Content Observer" -> "\u25c6O";
            case "Mixed" -> "\u25c6M";
            default -> "\u25c6";
        };
    }

    private String describeProductionSource(ProductionEntry entry) {
        if (entry.sourceType.isEmpty()) {
            return (entry.contributorCount > 1) ? ("Sources x" + entry.contributorCount) : "Unknown source";
        }
        if ("Mixed".equals(entry.sourceType)) {
            return "Mixed sources x" + entry.contributorCount;
        }
        return (entry.contributorCount > 1) ? (entry.sourceType + " x" + entry.contributorCount) : entry.sourceType;
    }

    private String describeProductionOrigin(ProductionEntry entry) {
        if (entry == null) return "";
        final String src = entry.sourceType == null ? "" : entry.sourceType;
        final String prefix = "from ";

        String observedSuffix = "";
        if (entry.channel != null) {
            final SourceConfig cfg = this.getSourceConfigForChannel(entry.channel.getId());
            if (cfg != null) {
                final net.minecraft.core.BlockPos tp = cfg.getObservedTargetPos();
                final String tl = cfg.getObservedTargetLabel();
                if (tp != null && tl != null && !tl.isBlank()) {
                    observedSuffix = " " + tl + " @ " + tp.getX() + " " + tp.getY() + " " + tp.getZ();
                }
            }
        }
        if (src.isEmpty()) return prefix + "delta of connected storage" + observedSuffix;
        if ("Mixed".equals(src)) return prefix + entry.contributorCount + " mixed sources";
        final String low = src.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("tunnel") || low.contains("conveyor")) return prefix + src + " flow";
        if (low.contains("observer") || low.contains("stockpile") || low.contains("threshold") || low.contains("vault") || low.contains("chest") || low.contains("barrel")) {
            return prefix + src + (observedSuffix.isEmpty() ? " (delta of contents)" : " delta of" + observedSuffix);
        }
        return prefix + src + observedSuffix;
    }

    private List<StatsDisplayPanelBE.DataChannel> collectItemChannelsForSource(List<StatsDisplayPanelBE.DataChannel> channels, String sourceId) {
        final List<StatsDisplayPanelBE.DataChannel> out = new ArrayList<>();
        if (sourceId == null) return out;
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            final String sid = StatsDisplaySettings.extractSourceId(ch.getId());
            if (!sourceId.equals(sid)) continue;
            final String id = ch.getId();
            if (ch.getStore().getMetricType() != MetricType.ITEM_COUNT) continue;
            if (id.contains("total_items") || id.contains("unique_types") || id.contains("slot_capacity") || id.contains("auto_slot_capacity")) continue;
            if (!(id.contains("_item_") || id.contains("auto_item_"))) continue;
            if (ch.getStore().getLatestValue() < 0.5f) continue;
            out.add(ch);
        }
        out.sort((a, b) -> Float.compare(b.getStore().getLatestValue(), a.getStore().getLatestValue()));
        return out;
    }

    private String getSourceTypeForChannel(StatsDisplayPanelBE.DataChannel channel) {
        final SourceConfig source = this.getSourceConfigForChannel(channel.getId());
        return this.normalizeSourceTypeLabel((source != null) ? source.getSourceType() : null);
    }

    private boolean isProductionRateMetric(MetricType type) {
        return type == MetricType.ITEMS_PER_MIN || type == MetricType.ITEMS_PER_SEC || type == MetricType.ITEMS_PER_HR;
    }

    private float computeProductionRate(TimeSeriesStore store) {
        final MetricType metricType = store.getMetricType();
        final float latest = store.getLatestValue();
        return switch (metricType) {
            case ITEMS_PER_SEC -> latest * 60.0f;
            case ITEMS_PER_HR -> latest / 60.0f;
            case ITEMS_PER_MIN -> latest;
            default -> 0.0f;
        };
    }

    private float computeTrend(TimeSeriesStore store) {
        final float recentAvg = store.getAverageWindow(store.getSampleCountForSeconds(30));
        final float olderAvg = store.getAverageWindow(store.getSampleCountForSeconds(60));
        if (Math.abs(olderAvg) < 0.001f) {
            return 0.0f;
        }
        return (recentAvg - olderAvg) / Math.abs(olderAvg) * 100.0f;
    }

    private void drawAlertThresholdLine(GuiGraphics g, float[] data, StatsDisplayPanelBE.DataChannel channel) {
        if (!channel.isAlertEnabled()) {
            return;
        }
        final float thresh = channel.getAlertThreshold();
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        final float avgVal = (min + max) / 2.0f;
        final float minRange = Math.max(Math.abs(avgVal) * 0.05f, 1.0f);
        if (range < minRange) {
            range = minRange;
            min = avgVal - range / 2.0f;
            max = avgVal + range / 2.0f;
        }
        final float niceStep = niceNumber(range / 4.0f);
        min = (float)(Math.floor(min / niceStep) * niceStep);
        max = (float)(Math.ceil(max / niceStep) * niceStep);
        range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
            min -= 0.5f;
            max += 0.5f;
        }
        if (thresh < min || thresh > max) {
            return;
        }
        final int y = this.clampY(this.graphTop + this.graphHeight - 2 - (int)((thresh - min) / range * (this.graphHeight - 4)));
        final int alertColor = channel.isAlertAbove() ? -1426111420 : -1438349057;
        for (int x = this.graphLeft + 2; x < this.graphLeft + this.graphWidth - 2; x += 5) {
            this.hline(g, x, Math.min(x + 3, this.graphLeft + this.graphWidth - 2), y, alertColor);
        }
        final String label = "\u26a0 " + this.formatCompact(thresh) + (channel.isAlertAbove() ? " \u2191" : " \u2193");
        g.drawString(this.font, label, this.graphLeft + this.graphWidth - this.font.width(label) - 4, y - 10, alertColor | 0xFF000000);
    }

    private void renderRedstoneConfig(GuiGraphics g, StatsDisplayPanelBE.DataChannel channel, int mx, int my) {
        final int rpw = Math.max(135, this.optionPanelWidth);
        final int rpx = this.optionPanelLeft;
        final int rpy = this.graphTop;
        final int rph = this.graphHeight;
        g.fill(rpx, rpy, rpx + rpw, rpy + rph, this.GRAPH_BG);
        this.border(g, rpx, rpy, rpw, rph, this.BORDER);
        g.fill(rpx + 1, rpy + 1, rpx + rpw - 1, rpy + 3, this.ACCENT);
        g.drawString(this.font, "\u26a1 Redstone", rpx + 4, rpy + 6, this.ACCENT);
        final String channelLabel = this.truncate(this.channelLabel(this.panel.getRedstoneChannelId()), rpw - 40);
        g.drawString(this.font, channelLabel, rpx + rpw - this.font.width(channelLabel) - 4, rpy + 6, this.TEXT);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 18, this.GRID);
        g.drawString(this.font, "Channel:", rpx + 4, rpy + 21, this.DIM);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 50, this.GRID);
        g.drawString(this.font, "Threshold:", rpx + 4, rpy + 53, this.DIM);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 78, this.GRID);
        g.drawString(this.font, "Mode:", rpx + 4, rpy + 81, this.DIM);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 106, this.GRID);
        g.drawString(this.font, "When:", rpx + 4, rpy + 109, this.DIM);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 134, this.GRID);
        this.hline(g, rpx + 4, rpx + rpw - 4, rpy + 156, this.GRID);
        g.drawString(this.font, "Graph:", rpx + 4, rpy + 159, this.DIM);
    }

    private void renderTooltip(GuiGraphics g, int mx, int my) {
        final List<Component> lines = this.buildHoverTooltip();
        if (lines.isEmpty()) {
            return;
        }
        g.renderTooltip(this.font, (List)lines, (Optional)Optional.empty(), mx, my);
    }

    private List<Component> buildHoverTooltip() {
        final ArrayList<Component> lines = new ArrayList<Component>();
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (this.selectedTab < 0 || this.selectedTab >= channels.size()) {
            return lines;
        }
        final StatsDisplayPanelBE.DataChannel channel = channels.get(this.selectedTab);
        final TimeSeriesStore store = channel.getStore();
        final float[] data = this.getDisplayedData(store);
        if (this.hoverDataIndex < 0 || this.hoverDataIndex >= data.length) {
            return lines;
        }
        final MetricType metricType = store.getMetricType();
        final float value = data[this.hoverDataIndex];
        lines.add(Component.literal(channel.getLabel()).withStyle(style -> style.withColor(this.ACCENT)));
        lines.add(Component.literal("Value: " + metricType.formatValue(value)).withStyle(style -> style.withColor(this.TEXT & 0xFFFFFF)));
        lines.add(Component.literal("Time: " + this.getWindowTimeAgo(store, this.hoverDataIndex, data.length)).withStyle(style -> style.withColor(11184810)));
        if (this.hoverDataIndex > 0) {
            final float delta = value - data[this.hoverDataIndex - 1];
            final int deltaColor = (delta > 0.0f) ? 5635925 : ((delta < 0.0f) ? 16742444 : 11184810);
            lines.add(Component.literal("Delta: " + ((delta >= 0.0f) ? "+" : "") + metricType.formatValue(delta)).withStyle(style -> style.withColor(deltaColor)));
        }
        lines.add(Component.literal("Sample: " + (this.hoverDataIndex + 1) + "/" + data.length).withStyle(style -> style.withColor(8947848)));
        return lines;
    }

    private void applyRedstoneThreshold() {
        final Float parsed = this.parseEditableFloat(this.redstoneValueInput);
        if (parsed == null) {
            return;
        }
        this.panel.setRedstoneThreshold(parsed);
        this.sendRedstonePacket();
    }

    private void adjustRedstoneThreshold(boolean up) {
        final Float parsed = this.parseEditableFloat(this.redstoneValueInput);
        final float current = (parsed != null) ? parsed : 0.0f;
        final float step = Math.max(1.0f, Math.abs(current) * 0.05f);
        final float newVal = up ? (current + step) : (current - step);
        this.redstoneValueInput = String.valueOf(newVal);
        if (this.redstoneThresholdInput != null) {
            this.redstoneThresholdInput.setValue(this.redstoneValueInput);
        }
        this.applyRedstoneThreshold();
    }

    private Float parseEditableFloat(String text) {
        if (text == null || !text.matches("-?(?:[0-9]+\\.?[0-9]*|[0-9]*\\.[0-9]+)")) {
            return null;
        }
        return Float.parseFloat(text);
    }

    private void sendAlertPacket(StatsDisplayPanelBE.DataChannel ch) {
        PacketDistributor.sendToServer(ConfigSyncPacket.alertConfig(this.panel.getBlockPos(), ch.getId(), ch.getAlertThreshold(), ch.isAlertAbove(), ch.isAlertEnabled()));
    }

    private void sendRedstonePacket() {
        PacketDistributor.sendToServer(ConfigSyncPacket.redstoneConfig(this.panel.getBlockPos(), this.panel.getRedstoneChannelId(), this.panel.getRedstoneThreshold(), this.panel.isRedstoneAbove(), this.panel.isRedstoneEnabled(), this.panel.getRedstoneMode()));
    }

    private void openSourceRename(String sourceId, SourceConfig config, int x, int y) {
        if (this.sourceRenameInput != null) {
            this.removeWidget(this.sourceRenameInput);
            this.sourceRenameInput = null;
        }
        this.renamingSourceId = sourceId;
        this.sourceRenameOpen = true;
        final String currentLabel = (config.getUserLabel() != null) ? config.getUserLabel() : "";
        final int inputX = Math.max(this.panelLeft + 8, Math.min(x, this.panelLeft + this.panelWidth - 128));
        final int inputY = Math.max(this.panelTop + 28, Math.min(y, this.panelTop + this.panelHeight - 22));
        (this.sourceRenameInput = new EditBox(this.font, inputX, inputY, 120, 16, Component.literal("Rename"))).setValue(currentLabel);
        this.sourceRenameInput.setMaxLength(64);
        this.sourceRenameInput.setHint(Component.literal(config.getSourceType()));
        this.sourceRenameInput.setFocused(true);
        this.addRenderableWidget(this.sourceRenameInput);
    }

    private void applySourceRename() {
        if (!this.sourceRenameOpen || this.renamingSourceId == null) {
            return;
        }
        final String newLabel = (this.sourceRenameInput != null) ? this.sourceRenameInput.getValue().trim() : "";
        PacketDistributor.sendToServer(new SourceLabelPacket(this.panel.getBlockPos(), this.renamingSourceId, newLabel));
        final SourceConfig config = this.panel.getSourceRegistry().get(this.renamingSourceId);
        if (config != null) {
            config.setUserLabel(newLabel.isEmpty() ? null : newLabel);
        }
        this.sourceRenameOpen = false;
        this.renamingSourceId = null;
        if (this.sourceRenameInput != null) {
            this.removeWidget(this.sourceRenameInput);
            this.sourceRenameInput = null;
        }
    }

    private boolean syncSourceIds() {
        final ArrayList<String> currentIds = new ArrayList<String>();
        final boolean groupProductionDetectors = this.shouldGroupProductionDetectorSources();
        boolean productionDetectorGroupAdded = false;
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue())) {
                if (groupProductionDetectors && this.isProductionDetectorSource(entry.getValue())) {
                    if (!productionDetectorGroupAdded) {
                        currentIds.add(PRODUCTION_DETECTOR_GROUP_PAGE);
                        productionDetectorGroupAdded = true;
                    }
                    continue;
                }
                currentIds.add(entry.getKey());
            }
        }
        if (this.sourceIds.equals(currentIds)) {
            return false;
        }
        this.sourceIds.clear();
        this.sourceIds.addAll(currentIds);
        return true;
    }

    private List<StatsDisplayPanelBE.DataChannel> getAllFilteredChannels() {
        if (this.isInventoryStatusPage() || this.isFactoryStatusPage()) {
            return this.panel.getVisibleChannels();
        }
        if (this.isVehicleSettingsPage()) {
            return this.getFlightDeckChannels(false);
        }
        if (this.isFlightDeckPage()) {
            return this.getFlightDeckChannels(false);
        }
        if (this.isGuidancePage()) {
            return this.getGuidanceChannels(false);
        }
        if (this.isNormalSourcePage() && this.selectedSource >= 0 && this.selectedSource < this.sourceIds.size()) {
            return this.getRenderableChannelsForSourcePage(this.sourceIds.get(this.selectedSource), false);
        }
        return this.panel.getVisibleChannels();
    }

    private List<StatsDisplayPanelBE.DataChannel> getFilteredChannels() {
        return this.getAllFilteredChannels();
    }

    private boolean hasTrainSourcePage() {
        return this.panel.hasTrainConnection();
    }

    private boolean hasFlightDeckPage() {
        return !this.getFlightDeckSourceIds().isEmpty();
    }

    private boolean hasVehicleSettingsPage() {
        return this.hasFlightDeckPage() || this.hasGuidancePage();
    }

    private boolean hasGuidancePage() {
        boolean hasGimbal = false;
        boolean hasVelocity = false;
        boolean hasGuidanceAuthority = false;
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (!this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue()) || !this.isGuidanceSource(entry.getValue())) {
                continue;
            }
            final String sourceType = entry.getValue().getSourceType();
            if ("Gimbal Sensor".equals(sourceType)) {
                hasGimbal = true;
            }
            else if ("Velocity Sensor".equals(sourceType)) {
                hasVelocity = true;
            }
            else if ("Navigation Table".equals(sourceType) || "Docking Connector".equals(sourceType) || "Steering Wheel".equals(sourceType)) {
                hasGuidanceAuthority = true;
            }
        }
        return (hasGimbal || hasVelocity) && hasGuidanceAuthority;
    }

    private void pingPanelActivityIfNeeded() {
        final boolean trainVisible = this.panel.hasTrainConnection() && this.isTrainDataVisible();
        final boolean topologyVisible = this.viewType == StatsDisplaySettings.GraphType.TOPOLOGY;
        if (!trainVisible && !topologyVisible) {
            this.trainActivityPingCooldown = 0;
            return;
        }
        if (this.trainActivityPingCooldown > 0) {
            --this.trainActivityPingCooldown;
            return;
        }
        this.trainActivityPingCooldown = 20;
        PacketDistributor.sendToServer(new PanelActivityPacket(this.panel.getBlockPos(), trainVisible, topologyVisible));
    }

    private boolean isTrainDataVisible() {
        return this.isHomePage() || this.isTrainPage();
    }

    private boolean hasTopologyGraphData() {
        final Object graphObj = this.panel.getCachedNetworkGraph();
        return graphObj instanceof NetworkGraph graph && !graph.isEmpty();
    }

    private boolean hasEfficiencyData() {
        final EfficiencyScore score = this.panel.getLatestEfficiencyScore();
        return score != null && !"?".equals(score.grade());
    }

    private boolean isHomePage() {
        return this.selectedSource == SOURCE_PAGE_HOME;
    }

    private boolean isFlightDeckPage() {
        return this.selectedSource == SOURCE_PAGE_FLIGHT_DECK && this.hasFlightDeckPage();
    }

    private boolean isGuidancePage() {
        return this.selectedSource == SOURCE_PAGE_GUIDANCE && this.hasGuidancePage();
    }

    private boolean isVehicleSettingsPage() {
        return this.selectedSource == SOURCE_PAGE_VEHICLE_SETTINGS && this.hasVehicleSettingsPage();
    }

    private boolean isTrainPage() {
        return this.selectedSource == SOURCE_PAGE_TRAIN && this.hasTrainSourcePage();
    }

    private boolean isNormalSourcePage() {
        return !this.isHomePage() && !this.isVehicleSettingsPage() && !this.isFlightDeckPage() && !this.isGuidancePage() && !this.isTrainPage() && !this.isFactoryPage() && !this.isInventoryStatusPage() && !this.isFactoryStatusPage() && !this.isPowerPage();
    }

    private boolean hasPowerPage() {
        return com.createstats.client.statspower.StatsPowerPage.available(this.panel);
    }

    private boolean isPowerPage() {
        return this.selectedSource == SOURCE_PAGE_POWER && this.hasPowerPage();
    }

    private void selectPowerPage() {
        final boolean changed = this.selectedSource != SOURCE_PAGE_POWER;
        this.selectedSource = SOURCE_PAGE_POWER;
        this.powerPage.normalise(this.panel);
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    // world tick modulo a big period, raw game time loses float precision
    private void renderPowerPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(this.graphLeft, this.graphTop, this.graphLeft + this.graphWidth,
                this.graphTop + this.graphHeight, com.createstats.gui.pdu.PduTheme.BG_DEEP);
        final float phase = (float) (this.getGuiRenderTick() % 20000L) + partialTick;
        this.powerPage.render(g, this.font, this.panel,
                this.graphLeft + 5, this.graphTop + 5,
                Math.max(40, this.graphWidth - 10), Math.max(40, this.graphHeight - 10),
                mouseX, mouseY, phase, partialTick);
    }

    private int getPowerNavDotColor() {
        boolean any = false;
        boolean active = false;
        for (final SourceConfig config : this.panel.getSourceRegistry().values()) {
            if (config == null) {
                continue;
            }
            final String family = CustomScreenSourceFamilyCatalog
                    .familyForSourceType(config.getSourceType()).getFamilyId();
            if (!"energy.pdu".equals(family) && !"energy.battery_controller".equals(family)
                    && !"fluid.flow_meter".equals(family)) {
                continue;
            }
            any = true;
            if (config.isActive()) {
                active = true;
            }
        }
        return active ? -16724890 : (any ? this.WARN : -10066330);
    }

    private void rememberCurrentPageSelection() {
        if (this.selectedSource == SOURCE_PAGE_FLIGHT_DECK) {
            lastOpenedPageKey = REMEMBERED_FLIGHT_DECK_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_GUIDANCE) {
            lastOpenedPageKey = REMEMBERED_GUIDANCE_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_VEHICLE_SETTINGS) {
            lastOpenedPageKey = REMEMBERED_VEHICLE_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_TRAIN) {
            lastOpenedPageKey = REMEMBERED_TRAIN_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_FACTORY) {
            lastOpenedPageKey = REMEMBERED_FACTORY_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_INVENTORY_STATUS) {
            lastOpenedPageKey = REMEMBERED_INVENTORY_STATUS_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_FACTORY_STATUS) {
            lastOpenedPageKey = REMEMBERED_FACTORY_STATUS_PAGE;
            return;
        }
        if (this.selectedSource == SOURCE_PAGE_POWER) {
            lastOpenedPageKey = REMEMBERED_POWER_PAGE;
            return;
        }
        if (this.selectedSource >= 0 && this.selectedSource < this.sourceIds.size()) {
            lastOpenedPageKey = this.sourceIds.get(this.selectedSource);
            return;
        }
        lastOpenedPageKey = REMEMBERED_HOME_PAGE;
    }

    private void restoreRememberedSourcePage() {
        if (openHomeOnNextOpen) {
            openHomeOnNextOpen = false;
            this.selectedSource = SOURCE_PAGE_HOME;
            return;
        }
        if (REMEMBERED_FLIGHT_DECK_PAGE.equals(lastOpenedPageKey) && this.hasFlightDeckPage()) {
            this.selectedSource = SOURCE_PAGE_FLIGHT_DECK;
            return;
        }
        if (REMEMBERED_GUIDANCE_PAGE.equals(lastOpenedPageKey) && this.hasGuidancePage()) {
            this.selectedSource = SOURCE_PAGE_GUIDANCE;
            return;
        }
        if (REMEMBERED_VEHICLE_PAGE.equals(lastOpenedPageKey) && this.hasVehicleSettingsPage()) {
            this.selectedSource = SOURCE_PAGE_VEHICLE_SETTINGS;
            return;
        }
        if (REMEMBERED_TRAIN_PAGE.equals(lastOpenedPageKey)) {
            this.selectedSource = SOURCE_PAGE_TRAIN;
            return;
        }
        if (REMEMBERED_FACTORY_PAGE.equals(lastOpenedPageKey)) {
            this.selectedSource = SOURCE_PAGE_FACTORY;
            return;
        }
        if (REMEMBERED_INVENTORY_STATUS_PAGE.equals(lastOpenedPageKey)) {
            this.selectedSource = SOURCE_PAGE_INVENTORY_STATUS;
            return;
        }
        if (REMEMBERED_FACTORY_STATUS_PAGE.equals(lastOpenedPageKey)) {
            this.selectedSource = SOURCE_PAGE_FACTORY_STATUS;
            return;
        }
        if (REMEMBERED_POWER_PAGE.equals(lastOpenedPageKey) && this.hasPowerPage()) {
            this.selectedSource = SOURCE_PAGE_POWER;
            return;
        }
        if (!REMEMBERED_HOME_PAGE.equals(lastOpenedPageKey)) {
            int rememberedIndex = this.sourceIds.indexOf(lastOpenedPageKey);
            if (rememberedIndex >= 0) {
                this.selectedSource = rememberedIndex;
                return;
            }
        }
        this.selectedSource = SOURCE_PAGE_HOME;
    }

    private boolean shouldShowChartControls() {
        return this.isNormalSourcePage() && !this.isBattleTacticalV2Page();
    }

    private com.createstats.blockentity.StatsDisplayPanelBE.BattleScreenLink getCurrentBattleTacticalV2Link() {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) return null;
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (config == null) return null;
        final String family = com.createstats.display.CustomScreenSourceFamilyCatalog
                .familyForSourceType(config.getSourceType()).getFamilyId();
        if (!"battle.bridge".equals(family)) return null;
        com.createstats.blockentity.StatsDisplayPanelBE.BattleScreenLink link = null;
        for (com.createstats.blockentity.StatsDisplayPanelBE.BattleScreenLink l : this.panel.getBattleScreenLinks()) {
            if (l == null) continue;
            if (config.getSourcePos() != null && config.getSourcePos().equals(l.getSourcePos())) {
                link = l;
                break;
            }
            if (link == null) link = l;
        }
        if (link == null || link.getBattleViewMode() != 1) return null;
        return link;
    }

    private boolean isBattleTacticalV2Page() {
        return this.selectedTab == -1
                && this.panel.getSettings().getDisplayMode() == StatsDisplaySettings.DisplayMode.SPECIALIZED
                && this.getCurrentBattleTacticalV2Link() != null;
    }

    private void renderBattleViewToggle(GuiGraphics g, int mouseX, int mouseY) {
        this.battleViewToggleVisible = false;
        if (!this.isBattleTacticalV2Page()) {
            return;
        }
        final boolean is2D = com.createstats.client.battle.BattleTacticalV2Viewport3D.isForce2D(this.panel.getBlockPos());

        final int bw = 30;
        final int bh = 14;
        final int rightInset = is2D ? 6 : (4 + 16 + 3 + 16 + 6);
        final int bx = this.graphLeft + this.graphWidth - rightInset - bw;
        final int by = this.graphTop + 5;
        this.battleViewToggleVisible = true;
        this.battleViewToggleX = bx;
        this.battleViewToggleY = by;
        this.battleViewToggleW = bw;
        this.battleViewToggleH = bh;
        final boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        final int bg = hover ? 0xFF22344f : 0xFF152033;
        g.fill(bx, by, bx + bw, by + bh, bg);
        g.fill(bx, by, bx + bw, by + 1, 0xFF2a3a60);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF080d18);
        g.fill(bx, by, bx + 1, by + bh, 0xFF2a3a60);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF080d18);
        final String label = is2D ? "3D" : "2D";
        final int fg = is2D ? 0xFF9fd0ff : 0xFFffd070;
        g.drawString(this.font, Component.literal(label), bx + (bw - this.font.width(label)) / 2, by + 3, fg, false);
    }

    private int getSourcePickerIndex() {
        if (this.sourceIds.isEmpty()) {
            return 0;
        }
        if (this.isNormalSourcePage() && this.selectedSource >= 0 && this.selectedSource < this.sourceIds.size()) {
            return this.selectedSource;
        }
        return Math.max(0, Math.min(this.sourceIds.size() - 1, this.sourceTabScrollOffset));
    }

    private void cycleSourcePicker(int direction) {
        if (this.sourceIds.isEmpty()) {
            return;
        }
        final int next = Math.floorMod(this.getSourcePickerIndex() + direction, this.sourceIds.size());
        this.sourceTabScrollOffset = next;
        this.selectNormalSourcePage(next);
    }

    private void selectChannelFromPicker(int channelIndex) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (channelIndex < -1 || channelIndex >= channels.size()) {
            return;
        }
        if (channelIndex == this.selectedTab && this.selectedTab >= 0) {
            this.pinnedChannels.clear();
        }
        this.selectedTab = channelIndex;
        this.pinnedChannels.remove(this.selectedTab);
        if (this.redstoneConfigOpen) {
            this.applyRedstoneThreshold();
            this.redstoneValueInput = "";
        }
        this.rebuildWidgets();
    }

    private void cycleChannelPicker(int direction) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (channels.isEmpty()) {
            return;
        }
        final int current = this.selectedTab >= 0 && this.selectedTab < channels.size() ? this.selectedTab : 0;
        this.selectChannelFromPicker(Math.floorMod(current + direction, channels.size()));
    }

    private int getVisibleSourceTabCount() {
        return 1 + 1 + 1 + this.sourceIds.size() + (this.hasVehicleSettingsPage() ? 1 : 0) + (this.hasFlightDeckPage() ? 1 : 0) + (this.hasGuidancePage() ? 1 : 0) + (this.hasTrainSourcePage() ? 1 : 0) + (this.hasPowerPage() ? 1 : 0) + 1;
    }

    private void selectHomePage() {
        final boolean changed = this.selectedSource != SOURCE_PAGE_HOME;
        this.selectedSource = SOURCE_PAGE_HOME;
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectTrainPage() {
        if (!this.hasTrainSourcePage()) {
            this.selectHomePage();
            return;
        }
        final boolean changed = this.selectedSource != SOURCE_PAGE_TRAIN;
        this.selectedSource = SOURCE_PAGE_TRAIN;
        this.resetPageScopedSelection();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectFlightDeckPage() {
        if (!this.hasFlightDeckPage()) {
            this.selectHomePage();
            return;
        }
        final boolean changed = this.selectedSource != SOURCE_PAGE_FLIGHT_DECK;
        this.selectedSource = SOURCE_PAGE_FLIGHT_DECK;
        this.resetPageScopedSelection();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectVehicleSettingsPage() {
        if (!this.hasVehicleSettingsPage()) {
            this.selectHomePage();
            return;
        }
        final boolean changed = this.selectedSource != SOURCE_PAGE_VEHICLE_SETTINGS;
        this.selectedSource = SOURCE_PAGE_VEHICLE_SETTINGS;
        this.resetPageScopedSelection();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectGuidancePage() {
        if (!this.hasGuidancePage()) {
            this.selectHomePage();
            return;
        }
        final boolean changed = this.selectedSource != SOURCE_PAGE_GUIDANCE;
        this.selectedSource = SOURCE_PAGE_GUIDANCE;
        this.resetPageScopedSelection();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectNormalSourcePage(int sourceIndex) {
        final boolean changed = this.selectedSource != sourceIndex;
        this.selectedSource = sourceIndex;
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void openTrainPageSelection(UUID trainId, UUID stationId) {
        if (!this.hasTrainSourcePage()) {
            this.selectHomePage();
            return;
        }
        final boolean changed = this.selectedSource != SOURCE_PAGE_TRAIN;
        this.selectedSource = SOURCE_PAGE_TRAIN;
        this.resetPageScopedSelection();
        this.selectedTrainDashboardTrainId = trainId;
        this.selectedTrainDashboardStationId = stationId;
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void ensureGuiViewTypeValid() {
        if (this.isTrainPage()) {
            return;
        }
        if (this.viewType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            this.viewType = StatsDisplaySettings.GraphType.LINE_CHART;
        }
    }

    private boolean validateGuiPageSelection() {
        boolean changed = false;
        if (this.hasFlightDeckPage() && this.selectedSource == SOURCE_PAGE_FLIGHT_DECK) {
            return this.resetPageScopedSelection();
        }
        if (this.hasGuidancePage() && this.selectedSource == SOURCE_PAGE_GUIDANCE) {
            return this.resetPageScopedSelection();
        }
        if (this.hasVehicleSettingsPage() && this.selectedSource == SOURCE_PAGE_VEHICLE_SETTINGS) {
            return this.resetPageScopedSelection();
        }
        if (this.hasTrainSourcePage() && this.selectedSource == SOURCE_PAGE_TRAIN) {
            return this.resetPageScopedSelection();
        }
        if (this.selectedSource == SOURCE_PAGE_FACTORY) {
            return this.resetPageScopedSelection();
        }
        if (this.selectedSource == SOURCE_PAGE_INVENTORY_STATUS) {
            return this.resetPageScopedSelection();
        }
        if (this.selectedSource == SOURCE_PAGE_FACTORY_STATUS) {
            return this.resetPageScopedSelection();
        }
        if (this.hasPowerPage() && this.selectedSource == SOURCE_PAGE_POWER) {
            return this.resetPageScopedSelection();
        }
        if (this.selectedSource >= 0 && this.selectedSource < this.sourceIds.size()) {
            changed |= this.sanitizeChannelSelection(this.getFilteredChannels());
            this.ensureGuiViewTypeValid();
            return changed;
        }
        if (this.getVisibleSourceTabCount() <= 1 && !this.sourceIds.isEmpty()) {
            this.selectedSource = 0;
            changed = true;
            changed |= this.resetPageScopedSelection();
            this.ensureGuiViewTypeValid();
            return changed;
        }
        if (this.selectedSource != SOURCE_PAGE_HOME && this.selectedSource != SOURCE_PAGE_VEHICLE_SETTINGS && this.selectedSource != SOURCE_PAGE_FLIGHT_DECK && this.selectedSource != SOURCE_PAGE_GUIDANCE && this.selectedSource != SOURCE_PAGE_TRAIN && this.selectedSource != SOURCE_PAGE_FACTORY && this.selectedSource != SOURCE_PAGE_INVENTORY_STATUS && this.selectedSource != SOURCE_PAGE_FACTORY_STATUS && this.selectedSource != SOURCE_PAGE_POWER) {
            this.selectedSource = SOURCE_PAGE_HOME;
            changed = true;
        }
        if (!this.hasPowerPage() && this.selectedSource == SOURCE_PAGE_POWER) {
            this.selectedSource = SOURCE_PAGE_HOME;
            changed = true;
        }
        if (!this.hasVehicleSettingsPage() && this.selectedSource == SOURCE_PAGE_VEHICLE_SETTINGS) {
            this.selectedSource = this.sourceIds.isEmpty() ? SOURCE_PAGE_HOME : ((this.getVisibleSourceTabCount() <= 1) ? 0 : SOURCE_PAGE_HOME);
            changed = true;
        }
        if (!this.hasFlightDeckPage() && this.selectedSource == SOURCE_PAGE_FLIGHT_DECK) {
            this.selectedSource = this.sourceIds.isEmpty() ? SOURCE_PAGE_HOME : ((this.getVisibleSourceTabCount() <= 1) ? 0 : SOURCE_PAGE_HOME);
            changed = true;
        }
        if (!this.hasGuidancePage() && this.selectedSource == SOURCE_PAGE_GUIDANCE) {
            this.selectedSource = this.sourceIds.isEmpty() ? SOURCE_PAGE_HOME : ((this.getVisibleSourceTabCount() <= 1) ? 0 : SOURCE_PAGE_HOME);
            changed = true;
        }
        if (!this.hasTrainSourcePage() && this.selectedSource == SOURCE_PAGE_TRAIN) {
            this.selectedSource = this.sourceIds.isEmpty() ? SOURCE_PAGE_HOME : ((this.getVisibleSourceTabCount() <= 1) ? 0 : SOURCE_PAGE_HOME);
            changed = true;
        }
        if (this.isNormalSourcePage()) {
            changed |= this.sanitizeChannelSelection(this.getFilteredChannels());
        }
        else {
            changed |= this.resetPageScopedSelection();
        }
        this.ensureGuiViewTypeValid();
        return changed;
    }

    private void setPanelOutputGraphType(StatsDisplaySettings.GraphType graphType) {
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP && !this.hasTrainSourcePage()) {
            return;
        }
        final StatsDisplaySettings settings = this.panel.getSettings();
        settings.setGraphType(graphType);
        settings.setDisplayMode(StatsDisplaySettings.normalizeDisplayMode(settings.getDisplayMode(), graphType, this.panel.getMultiblockWidth(), this.panel.getMultiblockHeight()));
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void applyPanelSettingsChange() {
        this.panel.setSettings(this.panel.getSettings());
        this.viewType = this.panel.getSettings().getGraphType();
        PacketDistributor.sendToServer(ConfigSyncPacket.settingsChange(this.panel.getBlockPos(), this.panel.getSettings()));
        DisplayTextureManager.disposeTexture(this.panel.getBlockPos());
    }

    private void cyclePanelInstrumentStyle() {
        this.panel.getSettings().cycleInstrumentStyleFamily();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void cyclePanelInstrumentDensity() {
        this.panel.getSettings().cycleInstrumentDensityPreset();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void cyclePanelInstrumentEffect() {
        this.panel.getSettings().cycleInstrumentEffectPreset();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void cyclePanelDisplayTheme() {
        this.panel.getSettings().cycleDisplayTheme();
        final DisplayTheme newTheme = this.panel.getSettings().getDisplayTheme();
        this.applyTheme(newTheme);
        PacketDistributor.sendToServer(ConfigSyncPacket.themeChange(this.panel.getBlockPos(), newTheme));
        DisplayTextureManager.markDirty(this.panel.getBlockPos());
        this.rebuildWidgets();
    }

    private void cyclePanelDisplayMode() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        final StatsDisplaySettings.DisplayMode nextMode = StatsDisplaySettings.nextDisplayMode(settings.getDisplayMode(), settings.getGraphType(), this.hasTrainSourcePage(), this.panel.getMultiblockWidth(), this.panel.getMultiblockHeight());
        settings.setDisplayMode(nextMode);
        settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), nextMode, this.hasTrainSourcePage()));
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void cyclePanelCycleSpeed() {
        this.panel.getSettings().cycleCycleSpeed();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void togglePanelAutoScale() {
        this.panel.getSettings().setAutoScale(!this.panel.getSettings().isAutoScale());
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void toggleStatsPanel() {
        this.showStats = !this.showStats;
        this.showLog = false;
        this.rebuildWidgets();
    }

    private void toggleDataLog() {
        this.showLog = !this.showLog;
        this.showStats = false;
        this.rebuildWidgets();
    }

    private void togglePrediction() {
        this.panel.getSettings().togglePrediction();
        PacketDistributor.sendToServer(ConfigSyncPacket.predictionToggle(this.panel.getBlockPos(), this.panel.getSettings().isPredictionEnabled()));
        DisplayTextureManager.markDirty(this.panel.getBlockPos());
        PredictionEngine.clearAll();
        this.rebuildWidgets();
    }

    private void toggleAnnotateMode() {
        if (!(this.annotateMode = !this.annotateMode)) {
            this.annotationInputOpen = false;
            this.editingAnnotationId = null;
        }
        this.rebuildWidgets();
    }

    private void openScannerPopup() {
        this.minecraft.setScreen((Screen)new ScanPopupScreen(this.panel, this));
    }

    private void openStressometerHudSettings() {
        final ArrayList<String> scopedSourceIds = new ArrayList<String>();
        for (Map.Entry<String, SourceConfig> entry : this.getHomeSelectedSources(this.panel.getSourceRegistry())) {
            scopedSourceIds.add(entry.getKey());
        }
        this.minecraft.setScreen((Screen)new StressometerHudSettingsScreen(this.panel, this, this.homeFocusedSourceId, this.homeSourceTypeFilter, scopedSourceIds));
    }

    private void openCustomScreenStudio() {
        this.minecraft.setScreen((Screen)new CustomScreenStudioScreen(this.panel, this));
    }

    private void toggleCustomLayoutMode() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        if (!settings.hasCustomScreenLayout()) {
            this.openCustomScreenStudio();
            return;
        }
        settings.setCustomScreenLayoutEnabled(!settings.isCustomScreenLayoutEnabled());
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void cycleCustomScreenProfile() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        if (!settings.hasCustomScreenLayout()) {
            this.openCustomScreenStudio();
            return;
        }
        settings.cycleCustomScreenProfile();
        settings.setCustomScreenLayoutEnabled(true);
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private String panelThemeButtonLabel() {
        return "\u2728 " + this.panel.getSettings().getDisplayTheme().getDisplayName();
    }

    private String displayModeButtonLabel() {
        return "Mode: " + this.panel.getSettings().getDisplayMode().getDisplayName();
    }

    private String cycleSpeedButtonLabel() {
        return "\u21bb " + this.panel.getSettings().getCycleSpeedLabel();
    }

    private String autoZoomButtonLabel() {
        return this.panel.getSettings().isAutoScale() ? "\u2195 Zoom: Auto" : "\u2195 Zoom: Fixed";
    }

    private String statsPanelButtonLabel() {
        return this.showStats ? "\u25bc Stats Panel" : "\u25b6 Stats Panel";
    }

    private String dataLogButtonLabel() {
        return this.showLog ? "\u25bc Data Log" : "\u25b6 Data Log";
    }

    private String predictionButtonLabel() {
        return this.panel.getSettings().isPredictionEnabled() ? "Forecast: ON" : "Forecast: OFF";
    }

    private String customLayoutModeButtonLabel() {
        if (!this.panel.getSettings().hasCustomScreenLayout()) {
            return "C:NEW";
        }
        return this.panel.getSettings().isCustomScreenLayoutEnabled() ? "C:ON" : "C:OFF";
    }

    private String customProfileButtonLabel() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        if (!settings.hasCustomScreenLayout()) {
            return "Profile";
        }
        final int profileCount = settings.getCustomScreenProfileCount();
        if (profileCount <= 1) {
            return shortCustomProfileName(settings.activeCustomScreenProfileName());
        }
        return "P:" + this.customProfilePositionLabel();
    }

    private String customProfileTooltip() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        if (!settings.hasCustomScreenLayout()) {
            return "Create a named custom profile in Custom Studio.";
        }
        return "Custom profile: " + settings.activeCustomScreenProfileName() + " (" + this.customProfilePositionLabel() + ")";
    }

    private String customProfilePositionLabel() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        final java.util.List<com.createstats.display.CustomScreenProfile> profiles = settings.getCustomScreenProfiles();
        if (profiles.isEmpty()) {
            return "0/0";
        }
        final String activeId = settings.getActiveCustomScreenProfileId();
        int activeIndex = 0;
        for (int index = 0; index < profiles.size(); ++index) {
            if (profiles.get(index).getId().equals(activeId)) {
                activeIndex = index;
                break;
            }
        }
        return (activeIndex + 1) + "/" + profiles.size();
    }

    private static String shortCustomProfileName(String name) {
        final String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) {
            return "Profile";
        }
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 7) + ".";
    }

    private String annotateButtonLabel() {
        return this.annotateMode ? "\ud83d\udccc Annotating" : "\ud83d\udccc Annotate";
    }

    private String scannerButtonLabel() {
        final int srcCount = this.panel.getScanSettings().getDiscoveredSources().size();
        final long enabledCount = this.panel.getScanSettings().getDiscoveredSources().values().stream().filter(ds -> this.panel.getScanSettings().isSourceEnabled(ds.sourceId)).count();
        return "\ud83d\udd0d Scanner" + ((srcCount > 0) ? (" [" + enabledCount + "/" + srcCount + "]") : "");
    }

    private String quickStyleLabel() {
        return switch (this.panel.getSettings().getInstrumentStyleFamily()) {
            case LEGACY_ANALOG -> "Legacy Analog";
            case MECHANICAL_BRASS -> "Brass";
            case LOCOMOTIVE_CAB -> "Cab";
            case SCIENTIFIC_INSTRUMENT -> "Lab";
            case CONTROL_PANEL_WALL -> "Wall";
        };
    }

    private String quickDensityLabel() {
        return switch (this.panel.getSettings().getInstrumentDensityPreset()) {
            case MINIMAL -> "Min";
            case MEDIUM -> "Med";
            case DENSE -> "Dense";
        };
    }

    private String quickEffectLabel() {
        return switch (this.panel.getSettings().getInstrumentEffectPreset()) {
            case LOW -> "Low";
            case BALANCED -> "Bal";
            case RICH -> "Rich";
        };
    }

    private void normalizeHomeSelection(LinkedHashMap<String, SourceConfig> registry) {
        final List<String> types = this.getHomeSourceTypes(registry);
        if (types.isEmpty()) {
            this.homeSourceTypeFilter = "";
            this.homeFocusedSourceId = "";
            this.homeLeftScroll = 0;
            this.homeRightScroll = 0;
            return;
        }
        if (this.homeSourceTypeFilter.isEmpty() || !types.contains(this.homeSourceTypeFilter)) {
            final String preferred = this.panel.getSettings().getPreferredSourceId();
            final SourceConfig preferredConfig = (preferred != null) ? registry.get(preferred) : null;
            if (preferredConfig != null) {
                this.homeSourceTypeFilter = this.normalizeSourceTypeLabel(preferredConfig.getSourceType());
            }
            else {
                this.homeSourceTypeFilter = types.get(0);
            }
        }
        final List<Map.Entry<String, SourceConfig>> filteredSources = this.getHomeFilteredSources(registry);
        boolean focusValid = false;
        for (Map.Entry<String, SourceConfig> entry : filteredSources) {
            if (entry.getKey().equals(this.homeFocusedSourceId)) {
                focusValid = true;
                break;
            }
        }
        if (!focusValid) {
            final String preferred2 = this.panel.getSettings().getPreferredSourceId();
            if (preferred2 != null && !preferred2.isEmpty()) {
                final SourceConfig preferredConfig2 = registry.get(preferred2);
                if (preferredConfig2 != null && this.homeSourceTypeFilter.equals(this.normalizeSourceTypeLabel(preferredConfig2.getSourceType()))) {
                    this.homeFocusedSourceId = preferred2;
                    focusValid = true;
                }
            }
        }
        if (!focusValid) {
            final String currentPageId = this.getCurrentSourcePageId();
            if (currentPageId != null) {
                final SourceConfig currentConfig = registry.get(currentPageId);
                if (currentConfig != null && this.homeSourceTypeFilter.equals(this.normalizeSourceTypeLabel(currentConfig.getSourceType()))) {
                    this.homeFocusedSourceId = currentPageId;
                    focusValid = true;
                }
            }
        }
        if (!focusValid) {
            this.homeFocusedSourceId = filteredSources.isEmpty() ? "" : filteredSources.get(0).getKey();
        }
    }

    private String normalizeSourceTypeLabel(String sourceType) {
        final String trimmed = (sourceType != null) ? sourceType.trim() : "";
        return trimmed.isEmpty() ? "Misc" : trimmed;
    }

    private boolean isGroupedSourcePage(String sourcePageId) {
        return sourcePageId != null && sourcePageId.startsWith(SOURCE_GROUP_PREFIX);
    }

    private boolean isProductionDetectorSource(SourceConfig config) {
        return config != null && "Production Detector".equals(this.normalizeSourceTypeLabel(config.getSourceType()));
    }

    private boolean isFlightDeckSource(SourceConfig config) {
        return config != null && SpecializedPanelRenderer.isAeroSourceType(this.normalizeSourceTypeLabel(config.getSourceType()));
    }

    private boolean isBattleScreenSource(SourceConfig config) {
        return config != null && ("Battle Screen".equals(this.normalizeSourceTypeLabel(config.getSourceType()))
            || "Battle Bridge".equals(this.normalizeSourceTypeLabel(config.getSourceType())));
    }

    private boolean shouldGroupProductionDetectorSources() {
        int count = 0;
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (!this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue()) || !this.isProductionDetectorSource(entry.getValue())) {
                continue;
            }
            if (++count > 1) {
                return true;
            }
        }
        return false;
    }

    private List<String> getSourceIdsForPage(String sourcePageId) {
        final ArrayList<String> ids = new ArrayList<String>();
        if (sourcePageId == null || sourcePageId.isEmpty()) {
            return ids;
        }
        if (!this.isGroupedSourcePage(sourcePageId)) {
            if (this.panel.getSourceRegistry().containsKey(sourcePageId)) {
                ids.add(sourcePageId);
            }
            return ids;
        }
        if (PRODUCTION_DETECTOR_GROUP_PAGE.equals(sourcePageId)) {
            for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
                if (this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue()) && this.isProductionDetectorSource(entry.getValue())) {
                    ids.add(entry.getKey());
                }
            }
        }
        return ids;
    }

    private List<String> getFlightDeckSourceIds() {
        final ArrayList<String> ids = new ArrayList<String>();
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue()) && this.isFlightDeckSource(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private List<String> getGuidanceSourceIds() {
        final ArrayList<String> ids = new ArrayList<String>();
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (this.shouldShowSourceInSpecializedPanel(entry.getKey(), entry.getValue()) && this.isGuidanceSource(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private boolean isSourcePageActive(String sourcePageId) {
        for (String rawSourceId : this.getSourceIdsForPage(sourcePageId)) {
            final SourceConfig config = this.panel.getSourceRegistry().get(rawSourceId);
            if (config != null && config.isActive()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSourceLive(String sourceId) {
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        return config != null && config.isActive();
    }

    private boolean hasSnapshotFallbackForSource(String sourceId) {
        return !this.getScanSnapshotFallbackChannels(sourceId, true).isEmpty();
    }

    private boolean isSourcePageInterrupted(String sourcePageId) {
        final List<String> sourceIds = this.getSourceIdsForPage(sourcePageId);
        if (sourceIds.isEmpty()) {
            return false;
        }
        boolean sawSource = false;
        for (String sourceId : sourceIds) {
            final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
            if (config == null) {
                continue;
            }
            sawSource = true;
            if (config.isActive()) {
                return false;
            }
        }
        return sawSource;
    }

    private boolean hasSourcePageSnapshot(String sourcePageId) {
        for (String sourceId : this.getSourceIdsForPage(sourcePageId)) {
            if (this.hasSnapshotFallbackForSource(sourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFlightDeckLive() {
        for (String sourceId : this.getFlightDeckSourceIds()) {
            if (this.isSourceLive(sourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFlightDeckInterrupted() {
        final List<String> sourceIds = this.getFlightDeckSourceIds();
        return !sourceIds.isEmpty() && !this.isFlightDeckLive();
    }

    private boolean hasFlightDeckSnapshot() {
        for (String sourceId : this.getFlightDeckSourceIds()) {
            if (this.hasSnapshotFallbackForSource(sourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGuidanceLive() {
        for (String sourceId : this.getGuidanceSourceIds()) {
            if (this.isSourceLive(sourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGuidanceInterrupted() {
        final List<String> sourceIds = this.getGuidanceSourceIds();
        return !sourceIds.isEmpty() && !this.isGuidanceLive();
    }

    private boolean hasGuidanceSnapshot() {
        for (String sourceId : this.getGuidanceSourceIds()) {
            if (this.hasSnapshotFallbackForSource(sourceId)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getHomeSourceTypes(LinkedHashMap<String, SourceConfig> registry) {
        final ArrayList<String> types = new ArrayList<String>();
        for (Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            if (!this.shouldShowSourceInNavigation(entry.getKey(), entry.getValue())) {
                continue;
            }
            final SourceConfig config = entry.getValue();
            final String typeLabel = this.normalizeSourceTypeLabel(config.getSourceType());
            if (!types.contains(typeLabel)) {
                types.add(typeLabel);
            }
        }
        return types;
    }

    private LinkedHashMap<String, Integer> getHomeSourceTypeCounts(LinkedHashMap<String, SourceConfig> registry) {
        final LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            if (!this.shouldShowSourceInNavigation(entry.getKey(), entry.getValue())) {
                continue;
            }
            final SourceConfig config = entry.getValue();
            final String typeLabel = this.normalizeSourceTypeLabel(config.getSourceType());
            counts.put(typeLabel, counts.getOrDefault(typeLabel, 0) + 1);
        }
        return counts;
    }

    private List<Map.Entry<String, SourceConfig>> getHomeFilteredSources(LinkedHashMap<String, SourceConfig> registry) {
        final ArrayList<Map.Entry<String, SourceConfig>> sources = new ArrayList<Map.Entry<String, SourceConfig>>();
        for (Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            if (!this.shouldShowSourceInNavigation(entry.getKey(), entry.getValue())) {
                continue;
            }
            if (this.homeSourceTypeFilter.equals(this.normalizeSourceTypeLabel(entry.getValue().getSourceType()))) {
                sources.add(entry);
            }
        }
        return sources;
    }

    private List<StatsDisplayPanelBE.DataChannel> getRenderableChannelsForSourcePage(String sourcePageId, boolean includeHidden) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        for (String sourceId : this.getSourceIdsForPage(sourcePageId)) {
            channels.addAll(this.getRenderableChannelsForSource(sourceId, includeHidden));
        }
        final SourceConfig pageConfig = this.getPrimarySourceConfigForPage(sourcePageId);
        if (this.isDieselEngineSource(pageConfig)) {
            for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
                if (entry.getKey().equals(sourcePageId) || !this.isDieselFuelReserveSource(entry.getValue())) {
                    continue;
                }
                channels.addAll(this.getRenderableChannelsForSource(entry.getKey(), includeHidden));
            }
        }
        return channels;
    }

    private SourceConfig getPrimarySourceConfigForPage(String sourcePageId) {
        final List<String> ids = this.getSourceIdsForPage(sourcePageId);
        if (ids.isEmpty()) {
            return null;
        }
        return this.panel.getSourceRegistry().get(ids.get(0));
    }

    private boolean isDieselEngineSource(SourceConfig config) {
        if (config == null) {
            return false;
        }
        final String sourceType = this.normalizeSourceTypeLabel(config.getSourceType());
        return "Diesel Engine".equals(sourceType)
                || "Modular Diesel Engine".equals(sourceType)
                || "Huge Diesel Engine".equals(sourceType)
                || "Diesel Engine Group".equals(sourceType);
    }

    private boolean isDieselFuelReserveSource(SourceConfig config) {
        return config != null && "Diesel Fuel Reserve".equals(this.normalizeSourceTypeLabel(config.getSourceType()));
    }

    private List<StatsDisplayPanelBE.DataChannel> getFlightDeckChannels(boolean includeHidden) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        for (String sourceId : this.getFlightDeckSourceIds()) {
            channels.addAll(this.getRenderableChannelsForSource(sourceId, includeHidden));
        }
        return channels;
    }

    private List<StatsDisplayPanelBE.DataChannel> getGuidanceChannels(boolean includeHidden) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        for (String sourceId : this.getGuidanceSourceIds()) {
            channels.addAll(this.getRenderableChannelsForSource(sourceId, includeHidden));
        }
        return channels;
    }

    private List<StatsDisplayPanelBE.DataChannel> getRenderableChannelsForSource(String sourceId, boolean includeHidden) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        if (sourceId == null || sourceId.isEmpty()) {
            return channels;
        }
        for (StatsDisplayPanelBE.DataChannel channel : this.panel.getChannelsForSource(sourceId)) {
            if (channel.getStore().getSampleCount() <= 0) {
                continue;
            }
            if (!includeHidden && this.panel.getSettings().isChannelHidden(channel.getId())) {
                continue;
            }
            channels.add(channel);
        }
        if (!channels.isEmpty()) {
            return channels;
        }
        return this.getScanSnapshotFallbackChannels(sourceId, includeHidden);
    }

    private List<StatsDisplayPanelBE.DataChannel> getScanSnapshotFallbackChannels(String sourceId, boolean includeHidden) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        final ScanSettings.DiscoveredSource discovered = this.findSnapshotSourceForConfig(config);
        if (discovered == null) {
            return channels;
        }
        final String sourcePrefix = "src_" + sourceId + "_";
        for (ScanSettings.ChannelSnapshot snapshot : discovered.channels) {
            if (snapshot.channelId == null || !snapshot.channelId.startsWith(sourcePrefix)) {
                continue;
            }
            if (!includeHidden && this.panel.getSettings().isChannelHidden(snapshot.channelId)) {
                continue;
            }
            channels.add(this.createSnapshotFallbackChannel(sourceId, snapshot));
        }
        return channels;
    }

    private ScanSettings.DiscoveredSource findSnapshotSourceForConfig(SourceConfig config) {
        if (config == null || config.getSourcePos() == null) {
            return null;
        }
        ScanSettings.DiscoveredSource best = null;
        for (ScanSettings.DiscoveredSource discovered : this.panel.getScanSettings().getDiscoveredSources().values()) {
            if (discovered == null || discovered.pos == null || !discovered.active || !this.scanSnapshotMatchesConfig(config, discovered) || !this.panel.getScanSettings().isSourceEnabled(discovered.sourceId)) {
                continue;
            }
            if (best == null || discovered.lastSeenTick > best.lastSeenTick) {
                best = discovered;
            }
        }
        return best;
    }

    private boolean scanSnapshotMatchesConfig(SourceConfig config, ScanSettings.DiscoveredSource discovered) {
        if (config.getSourcePos().equals(discovered.pos)) {
            return true;
        }
        final BlockPos observedTargetPos = config.getObservedTargetPos();
        return observedTargetPos != null && observedTargetPos.equals(discovered.pos);
    }

    private StatsDisplayPanelBE.DataChannel createSnapshotFallbackChannel(String sourceId, ScanSettings.ChannelSnapshot snapshot) {
        final TimeSeriesStore store = TimeSeriesStore.forPanel();
        final StatsDisplayPanelBE.DataChannel template = this.findChannelForSource(sourceId, snapshot.channelId);
        if (template != null) {
            store.setMetricType(template.getStore().getMetricType());
            store.setSampleIntervalTicks(template.getStore().getSampleIntervalTicks());
        }
        else {
            store.setMetricType(this.inferSnapshotMetricType(snapshot));
        }
        store.addSampleDirect(snapshot.latestValue);
        store.addSampleDirect(snapshot.latestValue);
        return StatsDisplayPanelBE.DataChannel.ofStore(snapshot.channelId, snapshot.label, store);
    }

    private StatsDisplayPanelBE.DataChannel findChannelForSource(String sourceId, String channelId) {
        for (StatsDisplayPanelBE.DataChannel channel : this.panel.getChannelsForSource(sourceId)) {
            if (channelId.equals(channel.getId())) {
                return channel;
            }
        }
        return null;
    }

    private MetricType inferSnapshotMetricType(ScanSettings.ChannelSnapshot snapshot) {
        final String channelId = (snapshot.channelId != null) ? snapshot.channelId.toLowerCase() : "";
        final String label = (snapshot.label != null) ? snapshot.label.toLowerCase() : "";
        if (channelId.contains("stress_pct")) {
            return MetricType.STRESS_PERCENT;
        }
        if (channelId.contains("stress") || channelId.contains("capacity_su") || channelId.contains("remaining_su") || label.contains("(su)")) {
            return MetricType.STRESS_SU;
        }
        if (channelId.contains("rpm") || label.contains("rpm")) {
            return MetricType.RPM;
        }
        if (channelId.contains("fill") || label.contains("fill")) {
            return MetricType.FILL_LEVEL;
        }
        if (channelId.contains("fluid_") || label.contains("(mb)") || label.contains("fluid")) {
            return MetricType.FLUID_MB;
        }
        if (channelId.contains("item_") || channelId.endsWith("total_items") || channelId.endsWith("unique_types")) {
            return MetricType.ITEM_COUNT;
        }
        if (channelId.contains("heat")) {
            return MetricType.HEAT_LEVEL;
        }
        if (channelId.contains("engine")) {
            return MetricType.ENGINE_COUNT;
        }
        if (channelId.contains("powered") || channelId.contains("overstressed") || label.contains("signal output") || label.contains("overstressed")) {
            return MetricType.BOOLEAN_FLAG;
        }
        return MetricType.GENERIC;
    }

    private List<StatsDisplayPanelBE.DataChannel> getHomeSourceChannels(String sourceId, boolean includeHidden) {
        return this.getRenderableChannelsForSource(sourceId, includeHidden);
    }

    private boolean shouldShowSourceInNavigation(String sourceId, SourceConfig config) {
        return config != null && (config.isActive() || !this.getHomeSourceChannels(sourceId, true).isEmpty());
    }

    private boolean shouldShowSourceInSpecializedPanel(String sourceId, SourceConfig config) {
        if (config == null) {
            return false;
        }
        if (this.isDieselFuelReserveSource(config)) {
            return false;
        }
        if (sourceId != null && sourceId.equals(this.homeFocusedSourceId)) {
            return true;
        }
        if (this.isBattleScreenSource(config) && this.hasLinkedBattleScreenSource(config)) {
            return true;
        }
        return this.isHomeSourceSelected(sourceId);
    }

    private boolean hasLinkedBattleScreenSource(SourceConfig config) {
        if (config == null || config.getSourcePos() == null) {
            return false;
        }
        for (StatsDisplayPanelBE.BattleScreenLink link : this.panel.getBattleScreenLinks()) {
            if (config.getSourcePos().equals(link.getSourcePos())) {
                return true;
            }
        }
        return false;
    }

    private boolean isGuidanceSource(SourceConfig config) {
        return config != null && SpecializedPanelRenderer.isAeroSourceType(this.normalizeSourceTypeLabel(config.getSourceType()));
    }

    private boolean isHomeSourceSelected(String sourceId) {
        return !this.getHomeSourceChannels(sourceId, false).isEmpty();
    }

    private int countHomeSelectedSources(List<Map.Entry<String, SourceConfig>> sources) {
        int count = 0;
        for (Map.Entry<String, SourceConfig> entry : sources) {
            if (this.isHomeSourceSelected(entry.getKey())) {
                ++count;
            }
        }
        return count;
    }

    private List<Map.Entry<String, SourceConfig>> getHomeSelectedSources(LinkedHashMap<String, SourceConfig> registry) {
        final ArrayList<Map.Entry<String, SourceConfig>> selected = new ArrayList<Map.Entry<String, SourceConfig>>();
        for (Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            if (this.isHomeSourceSelected(entry.getKey())) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private int getHomeSelectedChannelCount() {
        return this.panel.getVisibleChannels().size();
    }

    private HomeOutputMode getHomeOutputMode() {
        if (this.panel.getSettings().getGraphType() == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
            return HomeOutputMode.ANALOG;
        }
        return switch (this.panel.getSettings().getDisplayMode()) {
            case SINGLE -> HomeOutputMode.SINGLE;
            case DASHBOARD -> HomeOutputMode.DASHBOARD;
            case AUTO -> HomeOutputMode.AUTO_DASHBOARD;
            case SPECIALIZED -> this.panel.getSettings().isAeroFlightDeckEnabled()
                    ? HomeOutputMode.AERO
                    : (this.panel.getSettings().isAerolineDirectorEnabled() ? HomeOutputMode.AEROLINE : HomeOutputMode.SPECIALIZED);
        };
    }

    private void setHomeOutputMode(HomeOutputMode mode) {
        final StatsDisplaySettings settings = this.panel.getSettings();
        settings.setAeroFlightDeckEnabled(mode == HomeOutputMode.AERO);
        settings.setAerolineDirectorEnabled(mode == HomeOutputMode.AEROLINE);
        switch (mode) {
            case SINGLE -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SINGLE);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case DASHBOARD -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.DASHBOARD);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case AUTO_DASHBOARD -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.AUTO);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case SPECIALIZED -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SPECIALIZED);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case AERO -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SPECIALIZED);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case AEROLINE -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SPECIALIZED);
                settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), settings.getDisplayMode(), this.hasTrainSourcePage()));
            }
            case ANALOG -> {
                settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SINGLE);
                settings.setGraphType(StatsDisplaySettings.GraphType.ANALOG_GAUGE);
            }
        }
        this.viewType = settings.getGraphType();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private boolean canInspectHomeOutput() {
        final StatsDisplaySettings.GraphType graphType = this.panel.getSettings().getGraphType();
        if (graphType == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            return true;
        }
        if (graphType == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            return true;
        }
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            return this.hasTrainSourcePage();
        }
        return switch (this.getHomeOutputMode()) {
            case AERO -> this.hasFlightDeckPage();
            case AEROLINE -> this.hasGuidancePage();
            default -> !this.resolveHomeInspectSourceId().isEmpty();
        };
    }

    private String homeInspectOutputLabel() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        final StatsDisplaySettings.GraphType graphType = settings.getGraphType();
        if (graphType == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            return "Inspect Inventory Status";
        }
        if (graphType == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            return "Inspect Factory Status";
        }
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            return "Inspect Train Map";
        }
        return switch (this.getHomeOutputMode()) {
            case AERO -> "Inspect Flight Deck";
            case AEROLINE -> "Inspect Aeroline Director";
            case ANALOG -> this.homeInspectSourceButtonLabel("Inspect Instruments");
            case SPECIALIZED -> this.homeInspectSourceButtonLabel("Inspect Specialized View");
            case DASHBOARD, AUTO_DASHBOARD -> this.homeInspectSourceButtonLabel("Inspect Dashboard");
            case SINGLE -> this.homeInspectSourceButtonLabel("Inspect Live Channel");
        };
    }

    private String homeInspectOutputHint() {
        if (!this.canInspectHomeOutput()) {
            return "Inspect opens after you bind a live source.";
        }
        final StatsDisplaySettings.GraphType graphType = this.panel.getSettings().getGraphType();
        if (graphType == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            return "Inspect opens: Inventory Status page";
        }
        if (graphType == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            return "Inspect opens: Factory Status page";
        }
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            return "Inspect opens: Train page";
        }
        final String sourceId = this.resolveHomeInspectSourceId();
        if (sourceId == null || sourceId.isEmpty()) {
            return "Inspect opens after you bind a live source.";
        }
        return switch (this.getHomeOutputMode()) {
            case AERO -> "Inspect opens: Flight Deck";
            case AEROLINE -> "Inspect opens: Aeroline Director";
            case ANALOG -> "Inspect opens: " + this.sourceLabel(sourceId) + " instruments";
            case SPECIALIZED -> "Inspect opens: " + this.sourceLabel(sourceId) + " specialized view";
            case DASHBOARD, AUTO_DASHBOARD -> "Inspect opens: " + this.sourceLabel(sourceId) + " dashboard";
            case SINGLE -> {
                final int channelIndex = this.resolveHomeInspectChannelIndex(sourceId);
                final List<StatsDisplayPanelBE.DataChannel> channels = this.getRenderableChannelsForSourcePage(sourceId, false);
                final String channelLabel = (channelIndex >= 0 && channelIndex < channels.size()) ? this.channelLabel(channels.get(channelIndex).getId()) : "first live channel";
                yield "Inspect opens: " + this.sourceLabel(sourceId) + " -> " + channelLabel;
            }
        };
    }

    private String homeInspectSourceButtonLabel(String prefix) {
        final String sourceId = this.resolveHomeInspectSourceId();
        if (sourceId == null || sourceId.isEmpty()) {
            return prefix;
        }
        return prefix + ": " + this.sourceLabel(sourceId);
    }

    private String resolveHomeInspectSourceId() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        final String preferredSourceId = settings.getPreferredSourceId();
        if (this.canInspectHomeSource(preferredSourceId)) {
            return preferredSourceId;
        }
        final String dashboardMainSourceId = this.extractSourceIdFromChannel(settings.getDashboardMainChannelId());
        if (this.canInspectHomeSource(dashboardMainSourceId)) {
            return dashboardMainSourceId;
        }
        if (this.isHomeSourceSelected(this.homeFocusedSourceId) && this.canInspectHomeSource(this.homeFocusedSourceId)) {
            return this.homeFocusedSourceId;
        }
        for (Map.Entry<String, SourceConfig> entry : this.panel.getSourceRegistry().entrySet()) {
            if (this.isHomeSourceSelected(entry.getKey()) && this.canInspectHomeSource(entry.getKey())) {
                return entry.getKey();
            }
        }
        if (this.canInspectHomeSource(this.homeFocusedSourceId)) {
            return this.homeFocusedSourceId;
        }
        return "";
    }

    private boolean canInspectHomeSource(String sourceId) {
        return sourceId != null
            && !sourceId.isEmpty()
            && this.sourceIds.contains(sourceId)
            && (!this.getRenderableChannelsForSourcePage(sourceId, false).isEmpty()
                || SpecializedPanelRenderer.canRenderSourceWithoutChannels(this.panel, sourceId));
    }

    private int resolveHomeInspectChannelIndex(String sourceId) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getRenderableChannelsForSourcePage(sourceId, false);
        if (channels.isEmpty()) {
            return -1;
        }
        final String dashboardMainChannelId = this.panel.getSettings().getDashboardMainChannelId();
        if (dashboardMainChannelId != null && !dashboardMainChannelId.isEmpty()) {
            for (int i = 0; i < channels.size(); ++i) {
                if (dashboardMainChannelId.equals(channels.get(i).getId())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void openHomeInspectOutput() {
        final StatsDisplaySettings settings = this.panel.getSettings();
        final StatsDisplaySettings.GraphType graphType = settings.getGraphType();
        if (graphType == StatsDisplaySettings.GraphType.INVENTORY_STATUS) {
            this.selectInventoryStatusPage();
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.FACTORY_STATUS) {
            this.selectFactoryStatusPage();
            return;
        }
        if (graphType == StatsDisplaySettings.GraphType.TRAIN_MAP) {
            this.selectTrainPage();
            return;
        }
        switch (this.getHomeOutputMode()) {
            case AERO -> {
                this.selectFlightDeckPage();
                return;
            }
            case AEROLINE -> {
                this.selectGuidancePage();
                return;
            }
        }
        final String sourceId = this.resolveHomeInspectSourceId();
        final int sourceIndex = this.sourceIds.indexOf(sourceId);
        if (sourceId.isEmpty() || sourceIndex < 0) {
            return;
        }
        final int targetTab = (this.getHomeOutputMode() == HomeOutputMode.SINGLE && graphType != StatsDisplaySettings.GraphType.ANALOG_GAUGE)
                ? this.resolveHomeInspectChannelIndex(sourceId)
                : -1;
        final boolean sourceChanged = this.selectedSource != sourceIndex;
        final boolean selectionChanged = this.selectedTab != targetTab;
        this.selectedSource = sourceIndex;
        this.resetPageScopedSelection();
        if (targetTab >= 0) {
            this.selectedTab = targetTab;
        }
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (sourceChanged || selectionChanged) {
            this.rebuildWidgets();
        }
    }

    private void setHomeSourceSelection(String sourceId, boolean selected) {
        if (sourceId == null || sourceId.isEmpty()) {
            return;
        }
        if (selected && !this.isSourceLive(sourceId)) {
            return;
        }
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getHomeSourceChannels(sourceId, true);
        if (channels.isEmpty()) {
            return;
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final boolean hidden = this.panel.getSettings().isChannelHidden(channel.getId());
            if (selected && hidden) {
                this.panel.getSettings().toggleChannelVisibility(channel.getId());
            }
            else if (!selected && !hidden) {
                this.panel.getSettings().toggleChannelVisibility(channel.getId());
            }
        }
        if (!selected) {
            if (sourceId.equals(this.panel.getSettings().getPreferredSourceId())) {
                this.panel.getSettings().setPreferredSourceId("");
            }
            final String dashboardMain = this.panel.getSettings().getDashboardMainChannelId();
            if (!dashboardMain.isEmpty() && sourceId.equals(this.extractSourceIdFromChannel(dashboardMain))) {
                this.panel.getSettings().setDashboardMainChannelId("");
            }
        }
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void toggleHomeSourceSelection(String sourceId) {
        this.setHomeSourceSelection(sourceId, !this.isHomeSourceSelected(sourceId));
    }

    private void setHomePrimarySource(String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) {
            return;
        }
        final boolean channelFreeSpecialized = SpecializedPanelRenderer.canRenderSourceWithoutChannels(this.panel, sourceId);
        if (!this.isSourceLive(sourceId) && !channelFreeSpecialized) {
            return;
        }
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getHomeSourceChannels(sourceId, true);
        if (channels.isEmpty() && !channelFreeSpecialized) {
            return;
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            this.setHomeChannelVisible(channel.getId(), true);
        }
        this.panel.getSettings().setPreferredSourceId(sourceId);
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void clearHomePrimarySource() {
        this.panel.getSettings().setPreferredSourceId("");
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void setHomeChannelVisible(String channelId, boolean visible) {
        final boolean hidden = this.panel.getSettings().isChannelHidden(channelId);
        if (visible == hidden) {
            this.panel.getSettings().toggleChannelVisibility(channelId);
        }
    }

    private void toggleHomeChannelSelection(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }
        this.panel.getSettings().toggleChannelVisibility(channelId);
        if (this.panel.getSettings().isChannelHidden(channelId)) {
            final String sourceId = this.extractSourceIdFromChannel(channelId);
            if (sourceId.equals(this.panel.getSettings().getPreferredSourceId()) && !this.canInspectHomeSource(sourceId)) {
                this.panel.getSettings().setPreferredSourceId("");
            }
            if (channelId.equals(this.panel.getSettings().getDashboardMainChannelId())) {
                this.panel.getSettings().setDashboardMainChannelId("");
            }
        }
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void soloHomeChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }
        for (StatsDisplayPanelBE.DataChannel channel : this.panel.getActiveChannels()) {
            this.setHomeChannelVisible(channel.getId(), channel.getId().equals(channelId));
        }
        this.panel.getSettings().setPreferredSourceId(this.extractSourceIdFromChannel(channelId));
        this.panel.getSettings().setDashboardMainChannelId(channelId);
        if (this.getHomeOutputMode() != HomeOutputMode.ANALOG) {
            this.panel.getSettings().setDisplayMode(StatsDisplaySettings.DisplayMode.SINGLE);
            if (this.panel.getSettings().getGraphType() == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
                this.panel.getSettings().setGraphType(StatsDisplaySettings.GraphType.LINE_CHART);
            }
        }
        this.viewType = this.panel.getSettings().getGraphType();
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private void setHomeDashboardMainChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return;
        }
        this.setHomeChannelVisible(channelId, true);
        this.panel.getSettings().setDashboardMainChannelId(channelId);
        this.applyPanelSettingsChange();
        this.rebuildWidgets();
    }

    private String extractSourceIdFromChannel(String channelId) {
        if (channelId == null || channelId.isEmpty() || !channelId.startsWith("src_")) {
            return "";
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

    private boolean homeCycleSupported() {
        final int selectedChannels = this.getHomeSelectedChannelCount();
        final int selectedSources = this.countHomeSelectedSources(this.getHomeFilteredSources(this.panel.getSourceRegistry()));
        return switch (this.getHomeOutputMode()) {
            case SINGLE -> selectedChannels > 1;
            case DASHBOARD -> selectedChannels > 1 || selectedSources > 1;
            default -> false;
        };
    }

    private boolean homeAutoScaleSupported() {
        return this.getHomeOutputMode() == HomeOutputMode.SINGLE || this.getHomeOutputMode() == HomeOutputMode.AUTO_DASHBOARD;
    }

    private boolean homePredictionSupported() {
        final HomeOutputMode mode = this.getHomeOutputMode();
        return (mode == HomeOutputMode.SINGLE || mode == HomeOutputMode.AUTO_DASHBOARD) && this.panel.getSettings().getGraphType() == StatsDisplaySettings.GraphType.LINE_CHART;
    }

    private boolean homeUsesInstrumentAppearance() {
        final HomeOutputMode mode = this.getHomeOutputMode();
        return mode == HomeOutputMode.SPECIALIZED || mode == HomeOutputMode.AERO || mode == HomeOutputMode.AEROLINE || mode == HomeOutputMode.ANALOG;
    }

    private String homeCycleStatusLabel() {
        if (this.homeCycleSupported()) {
            return this.cycleSpeedButtonLabel();
        }
        return switch (this.getHomeOutputMode()) {
            case SINGLE -> "\u21bb Cycle: select 2+ channels";
            case DASHBOARD -> "\u21bb Cycle: needs secondary items";
            case AUTO_DASHBOARD -> "\u21bb Cycle: Auto dashboard locks rotation";
            case SPECIALIZED -> "\u21bb Cycle: source decides";
            case AERO -> "\u21bb Cycle: flight deck is fixed";
            case AEROLINE -> "\u21bb Cycle: Aeroline is fixed";
            case ANALOG -> "\u21bb Cycle: analog uses one channel";
        };
    }

    private String homeGraphTypeLabel(StatsDisplaySettings.GraphType type) {
        return switch (type) {
            case LINE_CHART -> "Line";
            case BAR_CHART -> "Bar";
            case GAUGE -> "Gauge";
            case NUMERIC -> "Numeric";
            case CAPACITY -> "Capacity";
            case PRODUCTION -> "Production";
            case EFFICIENCY -> "Grade";
            case TOPOLOGY -> "Topology";
            case ANALOG_GAUGE -> "Instrument";
            case TRAIN_MAP -> "Train";
            case INVENTORY_STATUS -> "Inventory";
            case FACTORY_STATUS -> "Factory";
        };
    }

    private void addHomeClickTarget(HomeClickAction action, int x, int y, int w, int h, int clipTop, int clipBottom, String value, HomeOutputMode outputMode, StatsDisplaySettings.GraphType graphType) {
        final int top = Math.max(y, clipTop);
        final int bottom = Math.min(y + h, clipBottom);
        if (bottom <= top) {
            return;
        }
        this.homeClickTargets.add(new HomeClickTarget(action, x, top, w, bottom - top, value, outputMode, graphType));
    }

    private void renderHomeButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, boolean selected, int mouseX, int mouseY, int clipTop, int clipBottom, HomeClickAction action, String value, HomeOutputMode outputMode, StatsDisplaySettings.GraphType graphType) {
        if (y + h <= clipTop || y >= clipBottom) {
            return;
        }
        final boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + h, clipBottom);
        final int bg = selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : this.TAB_BG);
        g.fill(x, y, x + w, y + h, enabled ? bg : -15329770);
        this.drawGUIBorder(g, x, y, w, h, selected ? this.ACCENT : -13417370);
        final int textColor = selected ? this.TEXT : (enabled ? this.DIM : -7763575);
        g.drawCenteredString(this.font, this.truncate(label, Math.max(20, w - 8)), x + w / 2, y + (h - 8) / 2, textColor);
        if (enabled && action != null) {
            this.addHomeClickTarget(action, x, y, w, h, clipTop, clipBottom, value, outputMode, graphType);
        }
    }

    private void renderHomeSectionTitle(GuiGraphics g, int x, int y, int w, String title, String subtitle, int clipTop, int clipBottom) {
        if (y + 12 > clipTop && y < clipBottom) {
            g.drawString(this.font, title, x, y, this.ACCENT);
        }
        if (subtitle != null && !subtitle.isEmpty() && y + 23 > clipTop && y + 10 < clipBottom) {
            g.drawString(this.font, this.truncate(subtitle, w), x, y + 11, this.DIM);
        }
    }

    private void renderHomeMiniLabel(GuiGraphics g, int x, int y, int w, String label, int clipTop, int clipBottom) {
        if (y + 10 > clipTop && y < clipBottom) {
            g.drawString(this.font, this.truncate(label, w), x, y, this.ACCENT);
        }
    }

    private int renderHomeChannelInspectorSummary(GuiGraphics g, int x, int y, int w, int clipTop, int clipBottom, List<StatsDisplayPanelBE.DataChannel> channels, StatsDisplaySettings settings) {
        final int h = 34;
        if (y + h > clipTop && y < clipBottom) {
            int visible = 0;
            int alerting = 0;
            int alertArmed = 0;
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (!settings.isChannelHidden(channel.getId())) {
                    ++visible;
                }
                if (channel.isAlertTriggered()) {
                    ++alerting;
                }
                else if (channel.isAlertEnabled()) {
                    ++alertArmed;
                }
            }
            g.fill(x, y, x + w, y + h, -15198184);
            this.drawGUIBorder(g, x, y, w, h, -13417370);
            g.drawString(this.font, "Channel Inspector", x + 6, y + 5, this.TEXT);
            final String mainChannelId = settings.getDashboardMainChannelId();
            final String mainLabel = mainChannelId == null || mainChannelId.isEmpty() ? "Main: Auto" : ("Main: " + this.channelLabel(mainChannelId));
            final String alertLabel = alerting > 0 ? (alerting + " alerting") : (alertArmed > 0 ? (alertArmed + " armed") : "no alerts");
            final String summary = visible + "/" + channels.size() + " shown \u2022 " + alertLabel + " \u2022 " + mainLabel;
            g.drawString(this.font, this.truncate(summary, w - 12), x + 6, y + 18, alerting > 0 ? this.WARN : this.DIM);
        }
        return h;
    }

    private String formatHomeChannelValue(StatsDisplayPanelBE.DataChannel channel) {
        final TimeSeriesStore store = channel.getStore();
        if (store.getSampleCount() <= 0) {
            final String lastText = channel.getLastText();
            return lastText == null || lastText.isEmpty() ? "No samples" : lastText;
        }
        return store.getMetricType().formatValue(store.getLatestValue()) + " \u2022 " + store.getSampleCount() + " samples";
    }

    private String homeChannelRoleLabel(StatsDisplayPanelBE.DataChannel channel, boolean visible, boolean dashboardMain) {
        String label = visible ? "shown" : "hidden";
        if (dashboardMain) {
            label += " \u2022 main";
        }
        if (channel.isAlertTriggered()) {
            label += " \u2022 alerting";
        }
        else if (channel.isAlertEnabled()) {
            label += " \u2022 alert armed";
        }
        return label;
    }

    private void renderHomePaneScrollbar(GuiGraphics g, int x, int y, int h, int visibleHeight, int contentHeight, int scrollOffset) {
        if (contentHeight <= visibleHeight || visibleHeight <= 0) {
            return;
        }
        g.fill(x, y, x + 3, y + h, 1090519039);
        final int thumbH = Math.max(10, h * visibleHeight / contentHeight);
        final int maxScroll = Math.max(1, contentHeight - visibleHeight);
        final int thumbY = y + (int)(scrollOffset / (float)maxScroll * Math.max(0, h - thumbH));
        g.fill(x, thumbY, x + 3, thumbY + thumbH, -1426063361);
    }

    private void addHomeOutputButtons() {
        final int leftW = Math.max(220, this.graphWidth / 2 - 4);
        final int rightX = this.graphLeft + leftW + 8;
        final int maxX = this.graphLeft + this.graphWidth - 8;
        int bx = rightX + 6;
        int by = this.panelTop + this.panelHeight - 48;
        for (StatsDisplaySettings.GraphType type : StatsDisplaySettings.GraphType.values()) {
            if (type == StatsDisplaySettings.GraphType.TRAIN_MAP && !this.hasTrainSourcePage()) {
                continue;
            }
            if (type == StatsDisplaySettings.GraphType.CAPACITY || type == StatsDisplaySettings.GraphType.PRODUCTION) {
                continue;
            }
            final String label = switch (type) {
                case LINE_CHART -> "Line";
                case BAR_CHART -> "Bar";
                case GAUGE -> "Gauge";
                case NUMERIC -> "Num";
                case CAPACITY -> "Cap";
                case PRODUCTION -> "Prod";
                case ANALOG_GAUGE -> "Inst";
                case EFFICIENCY -> "Grade";
                case TOPOLOGY -> "Topo";
                case TRAIN_MAP -> "Train";
                case INVENTORY_STATUS -> "Inv";
                case FACTORY_STATUS -> "Factory";
            };
            final int width = Math.max(38, this.font.width(label) + 10);
            if (bx + width > maxX) {
                bx = rightX + 6;
                by += 18;
            }
            final Button button = Button.builder(Component.literal(label), b -> this.setPanelOutputGraphType(type)).bounds(bx, by, width, 16).build();
            if (this.panel.getSettings().getGraphType() == type) {
                button.active = false;
            }
            this.addRenderableWidget(button);
            bx += width + 4;
        }
    }

    private List<TrainMapData.TrainEntry> getHomePreviewTrains(TrainMapData data) {
        final List<TrainMapData.TrainEntry> trains = new ArrayList<TrainMapData.TrainEntry>();
        if (data == null) {
            return trains;
        }
        trains.addAll(data.getTrains());
        trains.sort(Comparator.comparing((TrainMapData.TrainEntry t) -> t.waitingForSignal ? 0 : (t.stopped ? 1 : 2)).thenComparing(t -> t.name.toLowerCase()));
        return trains;
    }

    private List<TrainMapData.StationEntry> getHomePreviewStations(TrainMapData data) {
        final List<TrainMapData.StationEntry> stations = new ArrayList<TrainMapData.StationEntry>();
        if (data == null) {
            return stations;
        }
        stations.addAll(data.getStations());
        stations.sort(Comparator.comparing((TrainMapData.StationEntry s) -> s.assembling ? 0 : 1).thenComparing(s -> s.name.toLowerCase()));
        return stations;
    }

    private String buildHomeStationStatusSummary(TrainMapData data) {
        final long assembling = data.getStations().stream().filter(s -> s.assembling).count();
        final long docked = data.getTrains().stream().filter(t -> !t.currentStationName.isEmpty()).count();
        final long incoming = data.getTrains().stream().filter(t -> !t.nextDestination.isEmpty()).count();
        return ((assembling > 0L) ? (assembling + " assembling | ") : "") + docked + " docked | " + incoming + " inbound";
    }

    private void renderHomeTrainPreview(GuiGraphics g, TrainMapData data, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, -15658715);
        this.drawGUIBorder(g, x, y, w, h, -14013884);
        g.drawString(this.font, "\u00a77Trains", x + 6, y + 4, this.ACCENT);
        g.drawString(this.font, this.truncate(this.buildHomeTrainStatusSummary(data), w - 12), x + 6, y + 14, this.DIM);
        final List<TrainMapData.TrainEntry> trains = this.getHomePreviewTrains(data);
        final int rowH = 16;
        final int listY = y + 28;
        final int listH = h - 30;
        final int visible = Math.max(1, listH / rowH);
        final int maxScroll = Math.max(0, trains.size() - visible);
        this.homeTrainPreviewScroll = Math.max(0, Math.min(this.homeTrainPreviewScroll, maxScroll));
        if (trains.isEmpty()) {
            g.drawCenteredString(this.font, "No trains yet", x + w / 2, y + h / 2 - 4, this.DIM);
            return;
        }
        for (int i = 0; i < visible && i + this.homeTrainPreviewScroll < trains.size(); ++i) {
            final int idx = i + this.homeTrainPreviewScroll;
            final TrainMapData.TrainEntry train = trains.get(idx);
            final int ry = listY + i * rowH;
            final boolean selected = train.id.equals(this.selectedTrainDashboardTrainId);
            final boolean hover = mouseX >= x + 1 && mouseX < x + w - 5 && mouseY >= ry && mouseY < ry + rowH;
            g.fill(x + 1, ry, x + w - 5, ry + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
            final int stateColor = train.waitingForSignal ? -13278 : (train.stopped ? -39356 : this.ACCENT);
            g.fill(x + 3, ry + 3, x + 6, ry + rowH - 3, stateColor);
            final String line = this.truncate(train.name + " - " + this.getTrainListStateText(train), w - 16);
            g.drawString(this.font, line, x + 10, ry + 4, selected ? this.TEXT : this.DIM);
        }
        if (maxScroll > 0) {
            this.renderTrainDashboardScrollbar(g, x + w - 4, listY, 3, listH, visible, trains.size(), this.homeTrainPreviewScroll);
        }
    }

    private void renderHomeStationPreview(GuiGraphics g, TrainMapData data, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, -15658715);
        this.drawGUIBorder(g, x, y, w, h, -14013884);
        g.drawString(this.font, "\u00a77Stations", x + 6, y + 4, this.ACCENT);
        g.drawString(this.font, this.truncate(this.buildHomeStationStatusSummary(data), w - 12), x + 6, y + 14, this.DIM);
        final List<TrainMapData.StationEntry> stations = this.getHomePreviewStations(data);
        final int rowH = 16;
        final int listY = y + 28;
        final int listH = h - 30;
        final int visible = Math.max(1, listH / rowH);
        final int maxScroll = Math.max(0, stations.size() - visible);
        this.homeStationPreviewScroll = Math.max(0, Math.min(this.homeStationPreviewScroll, maxScroll));
        if (stations.isEmpty()) {
            g.drawCenteredString(this.font, "No stations yet", x + w / 2, y + h / 2 - 4, this.DIM);
            return;
        }
        for (int i = 0; i < visible && i + this.homeStationPreviewScroll < stations.size(); ++i) {
            final int idx = i + this.homeStationPreviewScroll;
            final TrainMapData.StationEntry station = stations.get(idx);
            final int ry = listY + i * rowH;
            final boolean selected = station.id.equals(this.selectedTrainDashboardStationId);
            final boolean hover = mouseX >= x + 1 && mouseX < x + w - 5 && mouseY >= ry && mouseY < ry + rowH;
            g.fill(x + 1, ry, x + w - 5, ry + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
            g.fill(x + 3, ry + 6, x + 7, ry + 10, station.assembling ? -13244 : -2232577);
            final String line = this.truncate(station.name + " - " + this.getStationListStateText(station, data), w - 17);
            g.drawString(this.font, line, x + 11, ry + 4, selected ? this.TEXT : this.DIM);
        }
        if (maxScroll > 0) {
            this.renderTrainDashboardScrollbar(g, x + w - 4, listY, 3, listH, visible, stations.size(), this.homeStationPreviewScroll);
        }
    }

    private void renderHomePage(GuiGraphics g, LinkedHashMap<String, SourceConfig> registry, int mouseX, int mouseY) {
        this.normalizeHomeSelection(registry);
        this.homeClickTargets.clear();
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        final int gap = 8;
        int leftW = Math.max(230, Math.min(300, w * 2 / 5));
        if (leftW > w - 180) {
            leftW = Math.max(210, w / 2 - 4);
        }
        final int rightX = x + leftW + gap;
        final int rightW = w - leftW - gap;
        final int headerH = 28;
        final int contentInset = 6;
        final int leftContentX = x + contentInset;
        final int leftContentY = y + headerH + 2;
        final int leftContentW = leftW - contentInset * 2 - 4;
        final int leftContentH = h - headerH - 8;
        final int rightContentX = rightX + contentInset;
        final int rightContentY = y + headerH + 2;
        final int rightContentW = rightW - contentInset * 2 - 4;
        final int rightContentH = h - headerH - 8;
        this.homeLeftPaneX = x;
        this.homeLeftPaneY = y;
        this.homeLeftPaneW = leftW;
        this.homeLeftPaneH = h;
        this.homeRightPaneX = rightX;
        this.homeRightPaneY = y;
        this.homeRightPaneW = rightW;
        this.homeRightPaneH = h;
        this.homeLeftScroll = Math.max(0, Math.min(this.homeLeftScroll, Math.max(0, this.homeLeftContentHeight - leftContentH)));
        this.homeRightScroll = Math.max(0, Math.min(this.homeRightScroll, Math.max(0, this.homeRightContentHeight - rightContentH)));
        final List<String> sourceTypes = this.getHomeSourceTypes(registry);
        final LinkedHashMap<String, Integer> sourceTypeCounts = this.getHomeSourceTypeCounts(registry);
        final List<Map.Entry<String, SourceConfig>> filteredSources = this.getHomeFilteredSources(registry);
        final List<Map.Entry<String, SourceConfig>> boundSources = this.getHomeSelectedSources(registry);
        final StatsDisplaySettings settings = this.panel.getSettings();
        final HomeOutputMode outputMode = this.getHomeOutputMode();
        final int selectedSourceCount = boundSources.size();
        final int selectedSourcesInFamily = this.countHomeSelectedSources(filteredSources);
        final int selectedChannelCount = this.getHomeSelectedChannelCount();
        final SourceConfig focusedConfig = registry.get(this.homeFocusedSourceId);
        final List<StatsDisplayPanelBE.DataChannel> focusedChannels = this.getHomeSourceChannels(this.homeFocusedSourceId, true);
        final String preferredSourceId = settings.getPreferredSourceId();
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        this.homeTrainPreviewX = 0;
        this.homeTrainPreviewY = 0;
        this.homeTrainPreviewW = 0;
        this.homeTrainPreviewH = 0;
        this.homeStationPreviewX = 0;
        this.homeStationPreviewY = 0;
        this.homeStationPreviewW = 0;
        this.homeStationPreviewH = 0;
        g.fill(x, y, x + leftW, y + h, -15921888);
        this.drawGUIBorder(g, x, y, leftW, h, -14013884);
        g.fill(rightX, y, rightX + rightW, y + h, -15658715);
        this.drawGUIBorder(g, rightX, y, rightW, h, -14013884);
        g.drawString(this.font, "\u00a7lSource Browser", x + 6, y + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.homeSourceTypeFilter.isEmpty() ? "Pick a source family" : (this.homeSourceTypeFilter + " \u2022 " + filteredSources.size() + " sources"), leftW - 12), x + 6, y + 17, this.DIM);
        g.drawString(this.font, "\u00a7lOutput Editor", rightX + 6, y + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(outputMode.label + " \u2022 " + selectedSourceCount + " sources \u2022 " + selectedChannelCount + " channels", rightW - 12), rightX + 6, y + 17, this.DIM);
        g.enableScissor(leftContentX, leftContentY, leftContentX + leftContentW, leftContentY + leftContentH);
        int leftContent = 0;
        leftContent += this.renderHomeSetupCard(g, leftContentX, leftContentY + leftContent - this.homeLeftScroll, leftContentW, leftContentY, leftContentY + leftContentH, registry, boundSources) + 8;
        this.renderHomeSectionTitle(g, leftContentX, leftContentY + leftContent - this.homeLeftScroll, leftContentW, "Source Type", "Only one source family is visible here", leftContentY, leftContentY + leftContentH);
        leftContent += 24;
        int chipX = 0;
        int chipY = leftContent;
        for (String type : sourceTypes) {
            final String chipLabel = type + " " + sourceTypeCounts.getOrDefault(type, 0);
            final int chipW = Math.min(leftContentW, Math.max(56, this.font.width(chipLabel) + 18));
            if (chipX > 0 && chipX + chipW > leftContentW) {
                chipX = 0;
                chipY += 20;
            }
            this.renderHomeButton(g, leftContentX + chipX, leftContentY + chipY - this.homeLeftScroll, chipW, 16, chipLabel, true, type.equals(this.homeSourceTypeFilter), mouseX, mouseY, leftContentY, leftContentY + leftContentH, HomeClickAction.FILTER_TYPE, type, null, null);
            chipX += chipW + 4;
        }
        leftContent = chipY + 24;
        this.renderHomeSectionTitle(g, leftContentX, leftContentY + leftContent - this.homeLeftScroll, leftContentW, "Sources", filteredSources.isEmpty() ? "No sources in this family yet" : (selectedSourcesInFamily + " bound in this family"), leftContentY, leftContentY + leftContentH);
        leftContent += 24;
        if (filteredSources.isEmpty()) {
            final int msgY = leftContentY + leftContent - this.homeLeftScroll;
            if (msgY + 10 > leftContentY && msgY < leftContentY + leftContentH) {
                g.drawString(this.font, "Connect matching sources to populate this browser.", leftContentX, msgY, this.DIM);
            }
            leftContent += 14;
        }
        else {
            int colorIndex = 0;
            for (Map.Entry<String, SourceConfig> entry : filteredSources) {
                final String sourceId = entry.getKey();
                final SourceConfig config = entry.getValue();
                final int cardY = leftContentY + leftContent - this.homeLeftScroll;
                final int cardH = 34;
                final boolean focused = sourceId.equals(this.homeFocusedSourceId);
                final boolean selected = this.isHomeSourceSelected(sourceId);
                final boolean live = this.isSourceLive(sourceId);
                final boolean snapshot = this.hasSnapshotFallbackForSource(sourceId);
                final boolean hover = mouseX >= leftContentX && mouseX < leftContentX + leftContentW && mouseY >= cardY && mouseY < cardY + cardH;
                final int bg = focused ? -14671840 : (hover ? -15000717 : -15329770);
                if (cardY + cardH > leftContentY && cardY < leftContentY + leftContentH) {
                    g.fill(leftContentX, cardY, leftContentX + leftContentW, cardY + cardH, bg);
                    this.drawGUIBorder(g, leftContentX, cardY, leftContentW, cardH, focused ? this.ACCENT : -13417370);
                    final int accent = live ? StatsDisplayScreen.CHANNEL_COLORS[colorIndex % StatsDisplayScreen.CHANNEL_COLORS.length] : this.WARN;
                    g.fill(leftContentX + 4, cardY + 4, leftContentX + 7, cardY + cardH - 4, accent);
                    g.drawString(this.font, this.truncate(config.getDisplayLabel(), leftContentW - 92), leftContentX + 12, cardY + 4, live ? this.TEXT : this.DIM);
                    final String state = this.describeHomeSourceState(sourceId, selected) + (sourceId.equals(preferredSourceId) ? " \u2022 Primary" : "");
                    g.drawString(this.font, this.truncate(this.buildHomeSourceSummary(sourceId, this.getHomeSourceChannels(sourceId, false)) + " \u2022 " + state, leftContentW - 18), leftContentX + 12, cardY + 17, this.DIM);
                    this.addHomeClickTarget(HomeClickAction.FOCUS_SOURCE, leftContentX, cardY, leftContentW, cardH, leftContentY, leftContentY + leftContentH, sourceId, null, null);
                }
                leftContent += cardH + 4;
                ++colorIndex;
            }
        }
        if (this.hasTrainSourcePage()) {
            leftContent += 6;
            this.renderHomeSectionTitle(g, leftContentX, leftContentY + leftContent - this.homeLeftScroll, leftContentW, "Train Snapshot", "Click a row to jump into the full train page", leftContentY, leftContentY + leftContentH);
            leftContent += 24;
            if (visibleData == null || visibleData.isEmpty()) {
                final int msgY2 = leftContentY + leftContent - this.homeLeftScroll;
                if (msgY2 + 10 > leftContentY && msgY2 < leftContentY + leftContentH) {
                    g.drawString(this.font, "Train data is not available yet.", leftContentX, msgY2, this.DIM);
                }
                leftContent += 14;
            }
            else {
                final int previewH = 94;
                this.homeTrainPreviewX = leftContentX;
                this.homeTrainPreviewY = leftContentY + leftContent - this.homeLeftScroll;
                this.homeTrainPreviewW = leftContentW;
                this.homeTrainPreviewH = previewH;
                this.renderHomeTrainPreview(g, visibleData, this.homeTrainPreviewX, this.homeTrainPreviewY, this.homeTrainPreviewW, this.homeTrainPreviewH, mouseX, mouseY);
                leftContent += previewH + 8;
                this.renderHomeSectionTitle(g, leftContentX, leftContentY + leftContent - this.homeLeftScroll, leftContentW, "Station Snapshot", "Home keeps the short rail overview on the left", leftContentY, leftContentY + leftContentH);
                leftContent += 24;
                this.homeStationPreviewX = leftContentX;
                this.homeStationPreviewY = leftContentY + leftContent - this.homeLeftScroll;
                this.homeStationPreviewW = leftContentW;
                this.homeStationPreviewH = previewH;
                this.renderHomeStationPreview(g, visibleData, this.homeStationPreviewX, this.homeStationPreviewY, this.homeStationPreviewW, this.homeStationPreviewH, mouseX, mouseY);
                leftContent += previewH + 6;
            }
        }
        this.homeLeftContentHeight = leftContent + 6;
        g.disableScissor();
        this.homeLeftScroll = Math.max(0, Math.min(this.homeLeftScroll, Math.max(0, this.homeLeftContentHeight - leftContentH)));
        this.renderHomePaneScrollbar(g, x + leftW - 5, leftContentY, leftContentH, leftContentH, this.homeLeftContentHeight, this.homeLeftScroll);
        g.enableScissor(rightContentX, rightContentY, rightContentX + rightContentW, rightContentY + rightContentH);
        int rightContent = 0;
        final int summaryY = rightContentY + rightContent - this.homeRightScroll;
        if (summaryY + 38 > rightContentY && summaryY < rightContentY + rightContentH) {
            g.fill(rightContentX, summaryY, rightContentX + rightContentW, summaryY + 38, -15198184);
            this.drawGUIBorder(g, rightContentX, summaryY, rightContentW, 38, -13417370);
            g.drawString(this.font, this.truncate("Editing output: " + outputMode.label, rightContentW - 12), rightContentX + 6, summaryY + 5, this.TEXT);
            final String focusSummary = (focusedConfig != null) ? ("Focused source: " + focusedConfig.getDisplayLabel()) : "Focused source: none";
            g.drawString(this.font, this.truncate(focusSummary, rightContentW - 12), rightContentX + 6, summaryY + 18, this.DIM);
        }
        rightContent += 44;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.homeInspectOutputLabel(), this.canInspectHomeOutput(), false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.INSPECT_OUTPUT, "", null, null);
        rightContent += 20;
        final int inspectHintY = rightContentY + rightContent - this.homeRightScroll;
        if (inspectHintY + 10 > rightContentY && inspectHintY < rightContentY + rightContentH) {
            g.drawString(this.font, this.truncate(this.homeInspectOutputHint(), rightContentW), rightContentX, inspectHintY, this.DIM);
        }
        rightContent += 18;
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Mode", "Pick the output behavior first", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        final int modeW = (rightContentW - 4) / 2;
        final StatsDisplaySettings.GraphType dashboardGraph = StatsDisplaySettings.normalizeGraphForDisplayMode(settings.getGraphType(), StatsDisplaySettings.DisplayMode.DASHBOARD, this.hasTrainSourcePage());
        final boolean canUseExplicitDashboard = StatsDisplaySettings.canUseExplicitDashboard(dashboardGraph, this.panel.getMultiblockWidth(), this.panel.getMultiblockHeight());
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, HomeOutputMode.SINGLE.label, true, outputMode == HomeOutputMode.SINGLE, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.SINGLE, null);
        this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, HomeOutputMode.DASHBOARD.label, canUseExplicitDashboard, outputMode == HomeOutputMode.DASHBOARD, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.DASHBOARD, null);
        rightContent += 20;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, HomeOutputMode.AUTO_DASHBOARD.label, true, outputMode == HomeOutputMode.AUTO_DASHBOARD, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.AUTO_DASHBOARD, null);
        this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, HomeOutputMode.SPECIALIZED.label, true, outputMode == HomeOutputMode.SPECIALIZED, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.SPECIALIZED, null);
        rightContent += 20;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, HomeOutputMode.AERO.label, true, outputMode == HomeOutputMode.AERO, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.AERO, null);
        rightContent += 20;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, HomeOutputMode.AEROLINE.label, true, outputMode == HomeOutputMode.AEROLINE, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.AEROLINE, null);
        rightContent += 20;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, HomeOutputMode.ANALOG.label, true, outputMode == HomeOutputMode.ANALOG, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_MODE, null, HomeOutputMode.ANALOG, null);
        rightContent += 22;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, "Inventory Status", true, settings.getGraphType() == StatsDisplaySettings.GraphType.INVENTORY_STATUS, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.OPEN_INVENTORY_STATUS, "", null, null);
        this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, "Factory Status", true, settings.getGraphType() == StatsDisplaySettings.GraphType.FACTORY_STATUS, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.OPEN_FACTORY_STATUS, "", null, null);
        rightContent += 20;
        if (this.hasTrainSourcePage()) {
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Train Map", true, settings.getGraphType() == StatsDisplaySettings.GraphType.TRAIN_MAP, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.TRAIN_MAP);
            rightContent += 22;
        }
        else {
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Train Map: needs Train Station link", false, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, null, "", null, null);
            rightContent += 22;
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Bound Sources", boundSources.isEmpty() ? "Nothing feeds the panel yet" : "These sources are currently in play", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        if (boundSources.isEmpty()) {
            final int msgY3 = rightContentY + rightContent - this.homeRightScroll;
            if (msgY3 + 10 > rightContentY && msgY3 < rightContentY + rightContentH) {
                g.drawString(this.font, "Add a source below to populate the live binding list.", rightContentX, msgY3, this.DIM);
            }
            rightContent += 14;
        }
        else {
            for (Map.Entry<String, SourceConfig> entry2 : boundSources) {
                final String sourceId2 = entry2.getKey();
                final SourceConfig config2 = entry2.getValue();
                final String label = (sourceId2.equals(preferredSourceId) ? "Primary \u2022 " : "") + this.normalizeSourceTypeLabel(config2.getSourceType()) + " \u2022 " + config2.getDisplayLabel();
                this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, label, true, sourceId2.equals(this.homeFocusedSourceId), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.FOCUS_SOURCE, sourceId2, null, null);
                rightContent += 20;
            }
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Source Binding", (focusedConfig != null) ? "The right pane owns the live output binding" : "Focus a source on the left first", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        if (focusedConfig == null) {
            final int msgY4 = rightContentY + rightContent - this.homeRightScroll;
            if (msgY4 + 10 > rightContentY && msgY4 < rightContentY + rightContentH) {
                g.drawString(this.font, "Pick a source card on the left to edit binding.", rightContentX, msgY4, this.DIM);
            }
            rightContent += 14;
        }
        else {
            final boolean focusedSourceLive = this.isSourceLive(this.homeFocusedSourceId);
            final boolean focusedSourceSelected = this.isHomeSourceSelected(this.homeFocusedSourceId);
            final boolean canToggleFocusedSource = focusedSourceSelected || focusedSourceLive;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, focusedSourceSelected ? "Remove Source" : (focusedSourceLive ? "Add Source" : "Snapshot Only"), canToggleFocusedSource, focusedSourceSelected, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_SOURCE, this.homeFocusedSourceId, null, null);
            this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, focusedSourceLive ? (this.homeFocusedSourceId.equals(preferredSourceId) ? "Primary Source" : "Use as Primary") : "Needs Live Link", focusedSourceLive, this.homeFocusedSourceId.equals(preferredSourceId), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_PRIMARY_SOURCE, this.homeFocusedSourceId, null, null);
            rightContent += 20;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, preferredSourceId.isEmpty() ? "Cycling Selected Sources" : "Cycle Selected Sources", !preferredSourceId.isEmpty(), preferredSourceId.isEmpty(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CLEAR_PRIMARY_SOURCE, "", null, null);
            rightContent += 18;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Rename Focused Source", true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.RENAME_SOURCE, this.homeFocusedSourceId, null, null);
            rightContent += 22;
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Channels", (focusedConfig != null) ? "Latest values, visibility, and output role" : "", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        if (focusedChannels.isEmpty()) {
            final int msgY4 = rightContentY + rightContent - this.homeRightScroll;
            if (msgY4 + 10 > rightContentY && msgY4 < rightContentY + rightContentH) {
                g.drawString(this.font, "This source has no live channels yet.", rightContentX, msgY4, this.DIM);
            }
            rightContent += 14;
        }
        else {
            rightContent += this.renderHomeChannelInspectorSummary(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, rightContentY, rightContentY + rightContentH, focusedChannels, settings) + 6;
            for (StatsDisplayPanelBE.DataChannel channel3 : focusedChannels) {
                final int rowY2 = rightContentY + rightContent - this.homeRightScroll;
                final int rowH = 32;
                final boolean visible2 = !settings.isChannelHidden(channel3.getId());
                final boolean dashMain = channel3.getId().equals(settings.getDashboardMainChannelId());
                if (rowY2 + rowH > rightContentY && rowY2 < rightContentY + rightContentH) {
                    g.fill(rightContentX, rowY2, rightContentX + rightContentW, rowY2 + rowH, visible2 ? -15263977 : -15658715);
                    this.drawGUIBorder(g, rightContentX, rowY2, rightContentW, rowH, -13417370);
                    g.drawString(this.font, this.truncate(channel3.getLabel(), rightContentW - 116), rightContentX + 6, rowY2 + 4, visible2 ? this.TEXT : this.DIM);
                    final String channelState = this.formatHomeChannelValue(channel3) + " \u2022 " + this.homeChannelRoleLabel(channel3, visible2, dashMain);
                    g.drawString(this.font, this.truncate(channelState, rightContentW - 116), rightContentX + 6, rowY2 + 17, channel3.isAlertTriggered() ? this.WARN : this.DIM);
                }
                final int toggleX = rightContentX + rightContentW - 108;
                final int auxX = rightContentX + rightContentW - 54;
                this.renderHomeButton(g, toggleX, rowY2 + 8, 50, 16, visible2 ? "Shown" : "Hidden", true, visible2, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_CHANNEL, channel3.getId(), null, null);
                final boolean dashboardMode = outputMode == HomeOutputMode.DASHBOARD || outputMode == HomeOutputMode.AUTO_DASHBOARD;
                this.renderHomeButton(g, auxX, rowY2 + 8, 50, 16, dashboardMode ? (dashMain ? "Main" : "Set Main") : "Solo", dashboardMode ? visible2 : true, dashboardMode && dashMain, mouseX, mouseY, rightContentY, rightContentY + rightContentH, dashboardMode ? HomeClickAction.SET_DASH_MAIN : HomeClickAction.SOLO_CHANNEL, channel3.getId(), null, null);
                rightContent += rowH + 4;
            }
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Behavior", "Unavailable controls stay disabled on purpose", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Range: " + settings.getTimeRange().getDisplayName(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_RANGE, "", null, null);
        rightContent += 18;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.homeCycleStatusLabel(), this.homeCycleSupported(), false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_SPEED, "", null, null);
        rightContent += 18;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.autoZoomButtonLabel(), this.homeAutoScaleSupported(), settings.isAutoScale(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_AUTO_SCALE, "", null, null);
        rightContent += 18;
        if (this.homePredictionSupported()) {
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.predictionButtonLabel(), true, settings.isPredictionEnabled(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_PREDICTION, "", null, null);
            rightContent += 18;
        }
        if (outputMode == HomeOutputMode.SINGLE || outputMode == HomeOutputMode.AUTO_DASHBOARD) {
            this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, outputMode == HomeOutputMode.SINGLE ? "Single View Type" : "Fallback View Type", outputMode == HomeOutputMode.SINGLE ? "Choose from grouped renderer families" : "Used when auto dashboard falls back to a single view", rightContentY, rightContentY + rightContentH);
            rightContent += 24;
            this.renderHomeMiniLabel(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Trends", rightContentY, rightContentY + rightContentH);
            rightContent += 12;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.LINE_CHART), true, settings.getGraphType() == StatsDisplaySettings.GraphType.LINE_CHART, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.LINE_CHART);
            this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.BAR_CHART), true, settings.getGraphType() == StatsDisplaySettings.GraphType.BAR_CHART, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.BAR_CHART);
            rightContent += 20;
            this.renderHomeMiniLabel(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Meters", rightContentY, rightContentY + rightContentH);
            rightContent += 12;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.GAUGE), true, settings.getGraphType() == StatsDisplaySettings.GraphType.GAUGE, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.GAUGE);
            this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.NUMERIC), true, settings.getGraphType() == StatsDisplaySettings.GraphType.NUMERIC, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.NUMERIC);
            rightContent += 20;
            this.renderHomeMiniLabel(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Systems", rightContentY, rightContentY + rightContentH);
            rightContent += 12;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.EFFICIENCY), true, settings.getGraphType() == StatsDisplaySettings.GraphType.EFFICIENCY, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.EFFICIENCY);
            this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, this.homeGraphTypeLabel(StatsDisplaySettings.GraphType.TOPOLOGY), true, settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.SET_GRAPH_TYPE, "", null, StatsDisplaySettings.GraphType.TOPOLOGY);
            rightContent += 24;
        }
        else if (outputMode == HomeOutputMode.DASHBOARD) {
            this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Dashboard", settings.getDashboardMainChannelId().isEmpty() ? "Choose a main channel from the list above" : ("Main channel: " + this.channelLabel(settings.getDashboardMainChannelId())), rightContentY, rightContentY + rightContentH);
            rightContent += 24;
        }
        else if (outputMode == HomeOutputMode.ANALOG) {
            this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Instruments", "Panel family and gauge hardware settings live here", rightContentY, rightContentY + rightContentH);
            rightContent += 24;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, settings.isAnalogShowZones() ? "Zones: On" : "Zones: Off", true, settings.isAnalogShowZones(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_ANALOG_ZONES, "", null, null);
            rightContent += 18;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, settings.isAnalogShowGlass() ? "Glass: On" : "Glass: Off", true, settings.isAnalogShowGlass(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_ANALOG_GLASS, "", null, null);
            rightContent += 18;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, settings.isAnalogShowRivets() ? "Rivets: On" : "Rivets: Off", true, settings.isAnalogShowRivets(), mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.TOGGLE_ANALOG_RIVETS, "", null, null);
            rightContent += 18;
        }
        else if (outputMode == HomeOutputMode.SPECIALIZED || outputMode == HomeOutputMode.AERO || outputMode == HomeOutputMode.AEROLINE) {
            final String sectionTitle = outputMode == HomeOutputMode.AERO ? "Flight Deck" : (outputMode == HomeOutputMode.AEROLINE ? "Aeroline" : "Specialized");
            final String sectionBody = outputMode == HomeOutputMode.AERO
                    ? "Composite aeronautics deck fed by connected Simulated sources"
                    : (outputMode == HomeOutputMode.AEROLINE ? "Guidance-focused aeronautics director for target and motion cues" : "Renderer family and instrument styling matter here");
            this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, sectionTitle, sectionBody, rightContentY, rightContentY + rightContentH);
            rightContent += 24;
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Appearance", this.homeUsesInstrumentAppearance() ? "Theme plus instrument family controls" : "Theme is global; style families stay hidden here", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.panelThemeButtonLabel(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_THEME, "", null, null);
        rightContent += 18;
        if (this.homeUsesInstrumentAppearance()) {
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Style: " + this.quickStyleLabel(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_STYLE, "", null, null);
            rightContent += 18;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "Density: " + this.quickDensityLabel(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_DENSITY, "", null, null);
            rightContent += 18;
            this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, "FX: " + this.quickEffectLabel(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.CYCLE_EFFECT, "", null, null);
            rightContent += 18;
        }
        this.renderHomeSectionTitle(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, "Actions", "Old home-screen actions now live here", rightContentY, rightContentY + rightContentH);
        rightContent += 24;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, rightContentW, 16, this.scannerButtonLabel(), true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.OPEN_SCANNER, "", null, null);
        rightContent += 18;
        this.renderHomeButton(g, rightContentX, rightContentY + rightContent - this.homeRightScroll, modeW, 16, "\u2398 Export", true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.EXPORT_SUMMARY, "", null, null);
        this.renderHomeButton(g, rightContentX + modeW + 4, rightContentY + rightContent - this.homeRightScroll, modeW, 16, "CSV Export", true, false, mouseX, mouseY, rightContentY, rightContentY + rightContentH, HomeClickAction.EXPORT_CSV, "", null, null);
        rightContent += 22;
        this.homeRightContentHeight = rightContent + 6;
        g.disableScissor();
        this.homeRightScroll = Math.max(0, Math.min(this.homeRightScroll, Math.max(0, this.homeRightContentHeight - rightContentH)));
        this.renderHomePaneScrollbar(g, rightX + rightW - 5, rightContentY, rightContentH, rightContentH, this.homeRightContentHeight, this.homeRightScroll);
    }

    private String buildHomeSourceSummary(String sourceId, List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels.isEmpty()) {
            if (this.isSourceLive(sourceId)) {
                return "Live link detected | waiting for channels";
            }
            if (this.hasSnapshotFallbackForSource(sourceId)) {
                return "Snapshot cached | waiting for live";
            }
            return "Waiting for live data";
        }
        final StatsDisplayPanelBE.DataChannel channel = channels.get(0);
        final TimeSeriesStore store = channel.getStore();
        if (store.getSampleCount() == 0) {
            return channels.size() + " channels | waiting for samples";
        }
        if (!this.isSourceLive(sourceId)) {
            return channels.size() + " channels | snapshot " + channel.getStore().getMetricType().formatValue(store.getLatestValue());
        }
        return channels.size() + " channels | now " + channel.getStore().getMetricType().formatValue(store.getLatestValue());
    }

    private String describeHomeSourceState(String sourceId, boolean selected) {
        if (this.isSourceLive(sourceId)) {
            return selected ? "Bound \u2022 Live" : "Live \u2022 ready to bind";
        }
        if (this.hasSnapshotFallbackForSource(sourceId)) {
            return selected ? "Bound \u2022 Snapshot only" : "Snapshot cached \u2022 not bound";
        }
        return selected ? "Bound \u2022 waiting for live" : "Not bound \u2022 waiting for live";
    }

    private int renderHomeSetupCard(GuiGraphics g, int x, int y, int w, int clipTop, int clipBottom,
            LinkedHashMap<String, SourceConfig> registry, List<Map.Entry<String, SourceConfig>> boundSources) {
        final int cardH = 62;
        if (y + cardH <= clipTop || y >= clipBottom) {
            return cardH;
        }
        int liveSources = 0;
        int snapshotSources = 0;
        boolean boundInterrupted = false;
        boolean boundSnapshot = false;
        int boundLiveSources = 0;
        for (Map.Entry<String, SourceConfig> entry : registry.entrySet()) {
            final String sourceId = entry.getKey();
            if (this.isSourceLive(sourceId)) {
                ++liveSources;
            }
            if (this.hasSnapshotFallbackForSource(sourceId)) {
                ++snapshotSources;
            }
        }
        for (Map.Entry<String, SourceConfig> entry2 : boundSources) {
            final String sourceId2 = entry2.getKey();
            if (this.isSourceLive(sourceId2)) {
                ++boundLiveSources;
                continue;
            }
            boundInterrupted = true;
            if (this.hasSnapshotFallbackForSource(sourceId2)) {
                boundSnapshot = true;
            }
        }
        final String headline;
        final String line1;
        final String line2;
        final int accentColor;
        if (this.panel.isStatsDisplayLinkOptInRequired()) {
            final String hintSourceType = this.panel.getStatsDisplayLinkHintSourceType();
            headline = hintSourceType.isEmpty() ? "Stats Link setup needed" : (hintSourceType + " needs Stats Link");
            line1 = hintSourceType.isEmpty() ? "Open the detected source and select Stats Link." : ("Open the " + hintSourceType + " and select Stats Link.");
            line2 = "Live data will appear here after the first update.";
            accentColor = this.WARN;
        }
        else if (registry.isEmpty()) {
            headline = "No source linked yet";
            line1 = "Connect a Display Link from a supported source block.";
            line2 = "Then return here to bind the source and choose channels.";
            accentColor = this.ACCENT;
        }
        else if (boundSources.isEmpty()) {
            headline = "Sources found, nothing bound";
            line1 = "Select a source card on the left.";
            line2 = "Use Add Source on the right to feed this panel.";
            accentColor = this.ACCENT;
        }
        else if (boundInterrupted) {
            headline = boundSnapshot ? "Snapshot fallback active" : "Waiting for live updates";
            line1 = boundSnapshot ? "A bound source lost live updates, but cached data is still visible." : "A bound source exists but has not sent a fresh live update.";
            line2 = "Reconnect the Display Link or reopen the source if its mode changed.";
            accentColor = this.WARN;
        }
        else {
            headline = "Ready for live editing";
            line1 = (boundLiveSources > 0) ? (boundLiveSources + " bound source(s) are live now.") : "Bound sources are waiting for live data.";
            line2 = "Choose channels, output mode, and behavior on the right.";
            accentColor = -16724890;
        }
        final String detail = liveSources + " live \u2022 " + snapshotSources + " snapshot \u2022 " + boundSources.size() + " bound";
        g.fill(x, y, x + w, y + cardH, -15198184);
        this.drawGUIBorder(g, x, y, w, cardH, -13417370);
        g.fill(x + 4, y + 4, x + 7, y + cardH - 4, accentColor);
        g.drawString(this.font, "Setup Status", x + 12, y + 5, this.ACCENT);
        g.drawString(this.font, this.truncate(headline, w - 18), x + 12, y + 18, this.TEXT);
        g.drawString(this.font, this.truncate(line1, w - 18), x + 12, y + 30, this.DIM);
        g.drawString(this.font, this.truncate(line2, w - 18), x + 12, y + 41, this.DIM);
        g.drawString(this.font, this.truncate(detail, w - 18), x + 12, y + 52, accentColor == this.WARN ? this.WARN : this.DIM);
        return cardH;
    }

    private String buildHomeTrainStatusSummary(TrainMapData data) {
        final long moving = data.getTrains().stream().filter(t -> t.speed > 0.01f && !t.waitingForSignal && !t.stopped).count();
        final long waiting = data.getTrains().stream().filter(t -> t.waitingForSignal).count();
        if (waiting > 0L) {
            return moving + " moving | " + waiting + " signaling";
        }
        return moving + " moving";
    }

    private String getTrainListStateText(TrainMapData.TrainEntry train) {
        if (train.waitingForSignal) {
            return "signal " + Math.max(0L, train.ticksWaitingForSignal / 20L) + "s";
        }
        if (train.stopped) {
            return train.currentStationName.isEmpty() ? "stopped" : ("stopped @ " + train.currentStationName);
        }
        final String speed = String.format("%.1f m/s", Math.abs(train.speed) * 20.0f);
        return train.nextDestination.isEmpty() ? ("moving | " + speed) : ("moving | " + speed + " | " + train.nextDestination);
    }

    private String getStationListStateText(TrainMapData.StationEntry station, TrainMapData data) {
        int docked = 0;
        int incoming = 0;
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            if (station.name.equals(train.currentStationName)) {
                ++docked;
            }
            if (station.name.equals(train.nextDestination)) {
                ++incoming;
            }
        }
        final String state = station.assembling ? "assembling" : ((docked > 0) ? "occupied" : ((incoming > 0) ? "incoming" : "clear"));
        return String.format("%s | docked %d | incoming %d", state, docked, incoming);
    }

    private long getGuiRenderTick() {
        if (this.panel.getLevel() != null) {
            return this.panel.getLevel().getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private void drawThickLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thick) {
        final int dx = Math.abs(x2 - x1);
        final int dy = Math.abs(y2 - y1);
        final int sx = (x1 < x2) ? 1 : -1;
        final int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;
        final int h = thick / 2;
        while (true) {
            g.fill(x1 - h, y1 - h, x1 + h + 1, y1 + h + 1, color);
            if (x1 == x2 && y1 == y2) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 >= dx) {
                continue;
            }
            err += dx;
            y1 += sy;
        }
    }

    private void drawArc(GuiGraphics g, int cx, int cy, int innerR, int outerR, double start, double span, int segs, int color) {
        this.drawSmoothArc(g, cx, cy, innerR, outerR, start, span, segs, color);
    }

    private void drawSmoothArc(GuiGraphics g, int cx, int cy, int innerR, int outerR, double start, double span, int segs, int color) {
        segs = Math.max(1, segs);
        final int thickness = outerR - innerR;
        final int midR = (innerR + outerR) / 2;
        final int halfThick = Math.max(1, thickness / 2);
        for (int i = 0; i < segs; ++i) {
            final double a1 = start + i / (double)segs * span;
            final double a2 = start + (i + 1) / (double)segs * span;
            final int x1 = cx + (int)(Math.cos(a1) * midR);
            final int y1 = cy + (int)(Math.sin(a1) * midR);
            final int x2 = cx + (int)(Math.cos(a2) * midR);
            final int y2 = cy + (int)(Math.sin(a2) * midR);
            final int minX = Math.min(x1, x2) - halfThick;
            final int maxX = Math.max(x1, x2) + halfThick + 1;
            final int minY = Math.min(y1, y2) - halfThick;
            final int maxY = Math.max(y1, y2) + halfThick + 1;
            g.fill(minX, minY, maxX, maxY, color);
        }
    }

    private void drawArcSeg(GuiGraphics g, int cx, int cy, int innerR, int outerR, double a1, double a2, int color) {
        this.drawSmoothArc(g, cx, cy, innerR, outerR, a1, a2 - a1, 1, color);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void hline(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2), y + 1, color);
    }

    private void vline(GuiGraphics g, int x, int y1, int y2, int color) {
        g.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2), color);
    }

    private int clampY(int y) {
        return Math.max(this.graphTop + 1, Math.min(this.graphTop + this.graphHeight - 2, y));
    }

    private static float niceNumber(float value) {
        if (value <= 0.0f) {
            return 1.0f;
        }
        final float exp = (float)Math.floor(Math.log10(value));
        final float frac = value / (float)Math.pow(10.0, exp);
        float nice;
        if (frac <= 1.5f) {
            nice = 1.0f;
        }
        else if (frac <= 3.5f) {
            nice = 2.0f;
        }
        else if (frac <= 7.5f) {
            nice = 5.0f;
        }
        else {
            nice = 10.0f;
        }
        return nice * (float)Math.pow(10.0, exp);
    }

    private String truncate(String s, int maxPixels) {
        if (this.font.width(s) <= maxPixels) {
            return s;
        }
        while (s.length() > 1 && this.font.width(s + "..") > maxPixels) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private StatsDisplayPanelBE.DataChannel findChannelBySuffix(String suffix) {
        for (Map.Entry<String, StatsDisplayPanelBE.DataChannel> entry : this.panel.getAllChannels().entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private StatsDisplayPanelBE.DataChannel findSiblingAutoChannel(StatsDisplayPanelBE.DataChannel channel, String metricId) {
        if (channel == null) {
            return null;
        }
        final String sourceId = StatsDisplaySettings.extractSourceId(channel.getId());
        final StatsDisplayPanelBE.DataChannel sibling = this.panel.getChannel("src_" + sourceId + "_auto_" + metricId);
        if (sibling != null) {
            return sibling;
        }
        return this.findChannelBySuffix(metricId);
    }

    private String formatCompact(float val) {
        if (Math.abs(val) >= 1000000.0f) {
            return String.format("%.1fM", val / 1000000.0f);
        }
        if (Math.abs(val) >= 1000.0f) {
            return String.format("%.1fk", val / 1000.0f);
        }
        if (Math.abs(val) >= 100.0f) {
            return String.format("%.0f", val);
        }
        if (Math.abs(val) >= 10.0f) {
            return String.format("%.1f", val);
        }
        return String.format("%.2f", val);
    }

    private String formatMetricCompact(MetricType type, float value) {
        final MetricType metricType = (type != null) ? type : MetricType.GENERIC;
        return switch (metricType) {
            case STRESS_SU -> this.formatCompact(value) + " SU";
            case FLUID_MB -> this.formatCompact(value) + " mB";
            case ENERGY_FE -> this.formatCompact(value) + " FE";
            case ENERGY_FE_PER_TICK -> this.formatCompact(value) + " FE/t";
            case ITEM_COUNT, ENGINE_COUNT, REDSTONE_POWER -> this.formatCompact(value);
            default -> metricType.formatValue(value);
        };
    }

    private void updateTrainDashboardSnapshot(TrainMapData data) {
        if (data == null) {
            return;
        }
        if (data.getServerTick() == this.trainDashboardSnapshotTick) {
            return;
        }
        this.trainDashboardSnapshotTick = data.getServerTick();
        final long now = System.currentTimeMillis();
        final Set<String> liveIds = new HashSet<String>();
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final String tid = train.id.toString();
            liveIds.add(tid);
            final float[] curr = this.trainDashboardCurrWorld.get(tid);
            this.trainDashboardPrevWorld.put(tid, (curr != null) ? new float[] { curr[0], curr[1] } : new float[] { (float)train.x, (float)train.z });
            this.trainDashboardCurrWorld.put(tid, new float[] { (float)train.x, (float)train.z });
        }
        this.trainDashboardCurrWorld.keySet().removeIf(id -> !liveIds.contains(id));
        this.trainDashboardPrevWorld.keySet().removeIf(id -> !liveIds.contains(id));
        this.trainDashboardLastUpdateMs = now;
        this.refreshTrainDashboardDimensionSelection(data);
        this.validateTrainDashboardSelection(data);
        this.clampTrainDashboardScroll(data);
    }

    private void refreshTrainDashboardDimensionSelection(TrainMapData data) {
        this.trainDashboardDimensions.clear();
        this.trainDashboardDimensions.addAll(data.getDimensions());
        if (this.trainDashboardDimensions.isEmpty()) {
            this.trainDashboardActiveDimension = "";
            return;
        }
        if (!this.trainDashboardActiveDimension.isEmpty() && this.trainDashboardDimensions.contains(this.trainDashboardActiveDimension)) {
            return;
        }
        if (this.panel.getLevel() != null) {
            final String panelDimension = this.panel.getLevel().dimension().location().toString();
            if (this.trainDashboardDimensions.contains(panelDimension)) {
                this.trainDashboardActiveDimension = panelDimension;
                return;
            }
        }
        this.trainDashboardActiveDimension = this.trainDashboardDimensions.get(0);
    }

    private TrainMapData getTrainDashboardVisibleData() {
        final TrainMapData data = this.panel.getTrainMapData();
        if (data == null) {
            return null;
        }
        return this.trainDashboardActiveDimension.isEmpty() ? data : data.filterByDimension(this.trainDashboardActiveDimension);
    }

    private void cycleTrainDashboardDimension(int direction) {
        if (this.trainDashboardDimensions.size() <= 1) {
            return;
        }
        int idx = this.trainDashboardDimensions.indexOf(this.trainDashboardActiveDimension);
        if (idx < 0) {
            idx = 0;
        }
        idx = (idx + direction + this.trainDashboardDimensions.size()) % this.trainDashboardDimensions.size();
        this.trainDashboardActiveDimension = this.trainDashboardDimensions.get(idx);
        this.trainDashboardTrainScroll = 0;
        this.trainDashboardStationScroll = 0;
        this.selectedTrainDashboardTrainId = null;
        this.selectedTrainDashboardStationId = null;
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        if (visibleData != null) {
            this.clampTrainDashboardScroll(visibleData);
        }
    }

    private void validateTrainDashboardSelection(TrainMapData data) {
        final TrainMapData visibleData = this.trainDashboardActiveDimension.isEmpty() ? data : data.filterByDimension(this.trainDashboardActiveDimension);
        if (visibleData == null) {
            this.selectedTrainDashboardTrainId = null;
            this.selectedTrainDashboardStationId = null;
            return;
        }
        if (this.selectedTrainDashboardTrainId != null) {
            boolean foundTrain = false;
            for (TrainMapData.TrainEntry train : visibleData.getTrains()) {
                if (train.id.equals(this.selectedTrainDashboardTrainId)) {
                    foundTrain = true;
                    break;
                }
            }
            if (!foundTrain) {
                this.selectedTrainDashboardTrainId = null;
            }
        }
        if (this.selectedTrainDashboardStationId != null) {
            boolean foundStation = false;
            for (TrainMapData.StationEntry station : visibleData.getStations()) {
                if (station.id.equals(this.selectedTrainDashboardStationId)) {
                    foundStation = true;
                    break;
                }
            }
            if (!foundStation) {
                this.selectedTrainDashboardStationId = null;
            }
        }
    }

    private void clampTrainDashboardScroll(TrainMapData visibleData) {
        if (visibleData == null) {
            this.trainDashboardTrainScroll = 0;
            this.trainDashboardStationScroll = 0;
            return;
        }
        final int gap = 6;
        final int trainPanelH = Math.max(88, (this.graphHeight - gap) / 2);
        final int stationPanelH = this.graphHeight - trainPanelH - gap;
        final int trainVisible = Math.max(1, (trainPanelH - 22) / 22);
        final int stationVisible = Math.max(1, (stationPanelH - 22) / 20);
        this.trainDashboardTrainScroll = Math.max(0, Math.min(this.trainDashboardTrainScroll, Math.max(0, visibleData.getTrains().size() - trainVisible)));
        this.trainDashboardStationScroll = Math.max(0, Math.min(this.trainDashboardStationScroll, Math.max(0, visibleData.getStations().size() - stationVisible)));
    }

    private void ensureTrainDashboardTrainVisible(List<TrainMapData.TrainEntry> trains, int visibleRows, UUID trainId) {
        if (trainId == null || trains.isEmpty()) {
            return;
        }
        for (int i = 0; i < trains.size(); ++i) {
            if (!trains.get(i).id.equals(trainId)) {
                continue;
            }
            if (i < this.trainDashboardTrainScroll) {
                this.trainDashboardTrainScroll = i;
            }
            else if (i >= this.trainDashboardTrainScroll + visibleRows) {
                this.trainDashboardTrainScroll = Math.max(0, i - visibleRows + 1);
            }
            return;
        }
    }

    private void ensureTrainDashboardStationVisible(List<TrainMapData.StationEntry> stations, int visibleRows, UUID stationId) {
        if (stationId == null || stations.isEmpty()) {
            return;
        }
        for (int i = 0; i < stations.size(); ++i) {
            if (!stations.get(i).id.equals(stationId)) {
                continue;
            }
            if (i < this.trainDashboardStationScroll) {
                this.trainDashboardStationScroll = i;
            }
            else if (i >= this.trainDashboardStationScroll + visibleRows) {
                this.trainDashboardStationScroll = Math.max(0, i - visibleRows + 1);
            }
            return;
        }
    }

    private String shortTrainDashboardDimensionName(String dimension) {
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

    private boolean matchesTrainDashboardFilter(String filter, String name) {
        return TrainMapData.matchesStationFilter(filter, name);
    }

    private TrainMapData.StationEntry findTrainDashboardStation(Map<String, TrainMapData.StationEntry> byName, String filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        final TrainMapData.StationEntry exact = byName.get(filter);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, TrainMapData.StationEntry> entry : byName.entrySet()) {
            if (this.matchesTrainDashboardFilter(filter, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private TrainMapData.StationEntry findTrainDashboardRouteStation(Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, String token) {
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
        return this.findTrainDashboardStation(byName, token);
    }

    private List<TrainMapData.StationEntry> resolveTrainDashboardRoute(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId) {
        final ArrayList<TrainMapData.StationEntry> route = new ArrayList<TrainMapData.StationEntry>();
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            for (String token : train.routeKey.split("\\|")) {
                this.addTrainDashboardRouteStation(route, this.findTrainDashboardRouteStation(byName, byId, token));
            }
        }
        if (route.size() < 2) {
            route.clear();
            for (String stop : train.scheduleStops) {
                this.addTrainDashboardRouteStation(route, this.findTrainDashboardStation(byName, stop));
            }
        }
        if (route.size() < 2) {
            route.clear();
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
                to = this.findTrainDashboardStation(byName, train.nextDestination);
            }
            this.addTrainDashboardRouteStation(route, from);
            this.addTrainDashboardRouteStation(route, to);
        }
        return route;
    }

    private void addTrainDashboardRouteStation(List<TrainMapData.StationEntry> route, TrainMapData.StationEntry station) {
        if (station == null) {
            return;
        }
        if (!route.isEmpty() && route.get(route.size() - 1).id.equals(station.id)) {
            return;
        }
        route.add(station);
    }

    private UUID findTrainDashboardStationByName(TrainMapData data, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (TrainMapData.StationEntry station : data.getStations()) {
            if (name.equals(station.name)) {
                return station.id;
            }
        }
        return null;
    }

    private TrainMapData.StationEntry findTrainDashboardStationById(UUID id) {
        if (id == null) {
            return null;
        }
        final TrainMapData data = this.getTrainDashboardVisibleData();
        if (data == null) {
            return null;
        }
        for (TrainMapData.StationEntry station : data.getStations()) {
            if (id.equals(station.id)) {
                return station;
            }
        }
        return null;
    }

    private float[] getTrainDashboardInterpolatedWorld(TrainMapData.TrainEntry train) {
        final String tid = train.id.toString();
        final float[] curr = this.trainDashboardCurrWorld.get(tid);
        if (curr == null) {
            return new float[] { (float)train.x, (float)train.z };
        }
        final float[] prev = this.trainDashboardPrevWorld.get(tid);
        if (prev == null) {
            return curr;
        }
        final float alpha = Math.min(1.0f, (System.currentTimeMillis() - this.trainDashboardLastUpdateMs) / 100.0f);
        return new float[] { prev[0] + (curr[0] - prev[0]) * alpha, prev[1] + (curr[1] - prev[1]) * alpha };
    }

    private TrainMapData.StationEntry findTrainDashboardPrevStation(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName) {
        if (train.scheduleStops.isEmpty() || train.nextDestination.isEmpty()) {
            return null;
        }
        int destIdx = -1;
        for (int i = 0; i < train.scheduleStops.size(); ++i) {
            if (this.matchesTrainDashboardFilter(train.scheduleStops.get(i), train.nextDestination)) {
                destIdx = i;
                break;
            }
            final TrainMapData.StationEntry station = this.findTrainDashboardStation(byName, train.scheduleStops.get(i));
            if (station != null && train.nextDestination.equals(station.name)) {
                destIdx = i;
                break;
            }
        }
        if (destIdx < 0) {
            return null;
        }
        final int prevIdx = (destIdx - 1 + train.scheduleStops.size()) % train.scheduleStops.size();
        return this.findTrainDashboardStation(byName, train.scheduleStops.get(prevIdx));
    }

    private float estimateTrainDashboardProgress(float trainX, float trainZ, TrainMapData.StationEntry from, TrainMapData.StationEntry to) {
        final double totalDist = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        if (totalDist < 1.0) {
            return 0.5f;
        }
        final double trainDist = Math.sqrt((trainX - from.x) * (trainX - from.x) + (trainZ - from.z) * (trainZ - from.z));
        return Math.max(0.0f, Math.min(1.0f, (float)(trainDist / totalDist)));
    }

    private void renderTrainDashboard(GuiGraphics g, int mouseX, int mouseY) {
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        if (visibleData == null || visibleData.isEmpty()) {
            this.trainExpandBtnW = 0;
            this.renderTrainDashboardEmpty(g, mouseX, mouseY);
            return;
        }
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        final int gap = 6;
        final int rightW = Math.max(170, Math.min(240, w / 3));
        final int mapW = w - rightW - gap;
        final int trainPanelH = Math.max(88, (h - gap) / 2);
        final int stationPanelH = h - trainPanelH - gap;
        final int mapX = x;
        final int mapY = y;
        final int rightX = x + mapW + gap;
        final int trainY = y;
        final int stationY = trainY + trainPanelH + gap;
        final Map<String, TrainMapData.StationEntry> byName = new HashMap<String, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station : visibleData.getStations()) {
            byName.put(station.name, station);
        }
        final Map<UUID, float[]> layout = this.computeTrainDashboardLayout(visibleData, mapX + 8, mapY + 48, mapW - 16, h - 74);
        final Map<String, Integer> routeByKey = new LinkedHashMap<String, Integer>();
        final List<List<String>> routeStops = new ArrayList<List<String>>();
        final List<Integer> routeColors = new ArrayList<Integer>();
        final Map<String, Integer> trainToRoute = new HashMap<String, Integer>();
        final Map<String, List<Integer>> edgeRoutes = new LinkedHashMap<String, List<Integer>>();
        this.buildTrainDashboardRoutes(visibleData, byName, routeByKey, routeStops, routeColors, trainToRoute, edgeRoutes);
        g.drawString(this.font, "\u00a7lTrain Dashboard", x + 4, y + 4, this.ACCENT);
        this.renderTrainQualityToggle(g, mouseX, mouseY, x, y, w);
        this.renderTrainDashboardDimensionSelector(g, mouseX, mouseY, x, y, w);
        this.renderTrainDashboardSummary(g, visibleData, x, y + 18, w);
        this.renderTrainDashboardMap(g, visibleData, byName, layout, routeColors, trainToRoute, edgeRoutes, mapX, mapY, mapW, h);
        this.renderTrainDashboardTrainList(g, visibleData, routeStops, routeColors, trainToRoute, rightX, trainY, rightW, trainPanelH, mouseX, mouseY);
        this.renderTrainDashboardStationList(g, visibleData, rightX, stationY, rightW, stationPanelH, mouseX, mouseY);
    }

    private void renderTrainDashboardEmpty(GuiGraphics g, int mouseX, int mouseY) {
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        g.drawString(this.font, "\u00a7lTrain Dashboard", x + 4, y + 4, this.ACCENT);
        this.renderTrainQualityToggle(g, mouseX, mouseY, x, y, w);
        this.renderTrainDashboardDimensionSelector(g, mouseX, mouseY, x, y, w);
        g.fill(x + 12, y + 30, x + w - 12, y + h - 12, -15658715);
        this.drawGUIBorder(g, x + 12, y + 30, w - 24, h - 42, -14013884);
        g.drawCenteredString(this.font, "No train network data", x + w / 2, y + h / 2 - 16, this.DIM);
        g.drawCenteredString(this.font, "Connect a Display Link from a Train Station", x + w / 2, y + h / 2 + 2, -12303258);
        g.drawCenteredString(this.font, "The dashboard will show map, trains, and stations here", x + w / 2, y + h / 2 + 16, -11184777);
    }

    private void renderTrainQualityToggle(GuiGraphics g, int mx, int my, int x, int y, int w) {
        final StatsDisplaySettings.TrainMapRenderQuality quality = this.panel.getSettings().getTrainMapRenderQuality();
        final String label = "Panel: " + quality.getDisplayName();
        this.trainQualityBtnW = Math.max(64, this.font.width(label) + 12);
        this.trainQualityBtnH = 14;
        int rightLimit = x + w - this.trainQualityBtnW - 4;
        if (this.trainDashboardDimensions.size() > 1) {
            final String dimensionLabel = this.shortTrainDashboardDimensionName(this.trainDashboardActiveDimension);
            final int dimensionWidth = 14 * 2 + this.font.width(dimensionLabel) + 18 + 6;
            rightLimit = Math.min(rightLimit, x + w - dimensionWidth - this.trainQualityBtnW - 8);
        }
        if (rightLimit < x + 88) {
            this.trainQualityBtnX = -10000;
            this.trainQualityBtnY = -10000;
            this.trainQualityBtnW = 0;
            this.trainQualityBtnH = 0;
            return;
        }
        this.trainQualityBtnX = Math.max(x + 88, Math.min(rightLimit, x + w / 2 - this.trainQualityBtnW / 2));
        this.trainQualityBtnY = y + 2;
        final boolean hover = mx >= this.trainQualityBtnX && mx < this.trainQualityBtnX + this.trainQualityBtnW && my >= this.trainQualityBtnY && my < this.trainQualityBtnY + this.trainQualityBtnH;
        g.fill(this.trainQualityBtnX, this.trainQualityBtnY, this.trainQualityBtnX + this.trainQualityBtnW, this.trainQualityBtnY + this.trainQualityBtnH, hover ? -867941240 : -870178236);
        this.drawGUIBorder(g, this.trainQualityBtnX, this.trainQualityBtnY, this.trainQualityBtnW, this.trainQualityBtnH, quality == StatsDisplaySettings.TrainMapRenderQuality.HIGH ? this.ACCENT : -13417370);
        g.drawCenteredString(this.font, label, this.trainQualityBtnX + this.trainQualityBtnW / 2, this.trainQualityBtnY + 3, quality == StatsDisplaySettings.TrainMapRenderQuality.HIGH ? this.TEXT : this.DIM);
    }

    private void renderTrainDashboardDimensionSelector(GuiGraphics g, int mx, int my, int x, int y, int w) {
        if (this.trainDashboardDimensions.size() <= 1) {
            this.trainDashboardDimPrevW = 0;
            this.trainDashboardDimNextW = 0;
            return;
        }
        final String label = this.shortTrainDashboardDimensionName(this.trainDashboardActiveDimension);
        final int labelW = this.font.width(label) + 18;
        final int btnW = 14;
        final int btnH = 14;
        final int totalW = btnW * 2 + labelW + 6;
        final int sx = x + w - totalW - 4;
        final int sy = y + 2;
        this.trainDashboardDimPrevX = sx;
        this.trainDashboardDimPrevY = sy;
        this.trainDashboardDimPrevW = btnW;
        this.trainDashboardDimPrevH = btnH;
        this.trainDashboardDimNextX = sx + btnW + 6 + labelW;
        this.trainDashboardDimNextY = sy;
        this.trainDashboardDimNextW = btnW;
        this.trainDashboardDimNextH = btnH;
        final boolean prevHover = mx >= this.trainDashboardDimPrevX && mx < this.trainDashboardDimPrevX + this.trainDashboardDimPrevW && my >= this.trainDashboardDimPrevY && my < this.trainDashboardDimPrevY + this.trainDashboardDimPrevH;
        final boolean nextHover = mx >= this.trainDashboardDimNextX && mx < this.trainDashboardDimNextX + this.trainDashboardDimNextW && my >= this.trainDashboardDimNextY && my < this.trainDashboardDimNextY + this.trainDashboardDimNextH;
        final int labelX = this.trainDashboardDimPrevX + this.trainDashboardDimPrevW + 6;
        g.fill(labelX, sy, labelX + labelW, sy + btnH, -2012213216);
        this.drawGUIBorder(g, labelX, sy, labelW, btnH, -13417370);
        g.fill(this.trainDashboardDimPrevX, sy, this.trainDashboardDimPrevX + this.trainDashboardDimPrevW, sy + btnH, prevHover ? -867941240 : -870178236);
        g.fill(this.trainDashboardDimNextX, sy, this.trainDashboardDimNextX + this.trainDashboardDimNextW, sy + btnH, nextHover ? -867941240 : -870178236);
        this.drawGUIBorder(g, this.trainDashboardDimPrevX, sy, this.trainDashboardDimPrevW, btnH, -13417370);
        this.drawGUIBorder(g, this.trainDashboardDimNextX, sy, this.trainDashboardDimNextW, btnH, -13417370);
        g.drawCenteredString(this.font, "<", this.trainDashboardDimPrevX + this.trainDashboardDimPrevW / 2, sy + 3, -3355427);
        g.drawCenteredString(this.font, ">", this.trainDashboardDimNextX + this.trainDashboardDimNextW / 2, sy + 3, -3355427);
        g.drawCenteredString(this.font, label, labelX + labelW / 2, sy + 3, this.ACCENT);
    }

    private void renderTrainDashboardSummary(GuiGraphics g, TrainMapData data, int x, int y, int w) {
        final long moving = data.getTrains().stream().filter(t -> t.speed > 0.01f && !t.waitingForSignal && !t.stopped).count();
        final long waiting = data.getTrains().stream().filter(t -> t.waitingForSignal).count();
        final long docked = data.getTrains().stream().filter(t -> !t.currentStationName.isEmpty()).count();
        final String summary = String.format("\u00a77Stations: \u00a7f%d  \u00a77Trains: \u00a7f%d  \u00a77Moving: \u00a7a%d  \u00a77Docked: \u00a7f%d%s", data.getStations().size(), data.getTrains().size(), moving, docked, (waiting > 0L) ? ("  \u00a76Signal: " + waiting) : "");
        g.drawString(this.font, summary, x + 4, y, this.TEXT);
    }

    private Map<UUID, float[]> computeTrainDashboardLayout(TrainMapData data, int x, int y, int w, int h) {
        final Map<UUID, float[]> layout = new LinkedHashMap<UUID, float[]>();
        final List<TrainMapData.StationEntry> stations = data.getStations();
        if (stations.isEmpty()) {
            return layout;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -1.7976931348623157E308;
        double minZ = Double.MAX_VALUE;
        double maxZ = -1.7976931348623157E308;
        for (TrainMapData.StationEntry station : stations) {
            minX = Math.min(minX, station.x);
            maxX = Math.max(maxX, station.x);
            minZ = Math.min(minZ, station.z);
            maxZ = Math.max(maxZ, station.z);
        }
        double rangeX = Math.max(100.0, maxX - minX);
        double rangeZ = Math.max(100.0, maxZ - minZ);
        minX -= rangeX * 0.1;
        maxX += rangeX * 0.1;
        minZ -= rangeZ * 0.1;
        maxZ += rangeZ * 0.1;
        final Map<UUID, int[]> pixels = new LinkedHashMap<UUID, int[]>();
        for (TrainMapData.StationEntry station2 : stations) {
            final int sx = x + (int)((station2.x - minX) / Math.max(1.0, maxX - minX) * Math.max(1, w));
            final int sy = y + (int)((station2.z - minZ) / Math.max(1.0, maxZ - minZ) * Math.max(1, h));
            pixels.put(station2.id, new int[] { sx, sy });
        }
        final int minSep = 16;
        for (int iter = 0; iter < 24; ++iter) {
            boolean moved = false;
            for (int i = 0; i < stations.size(); ++i) {
                for (int j = i + 1; j < stations.size(); ++j) {
                    final int[] a = pixels.get(stations.get(i).id);
                    final int[] b = pixels.get(stations.get(j).id);
                    final int dx = b[0] - a[0];
                    final int dy = b[1] - a[1];
                    final int dist = (int)Math.sqrt(dx * dx + dy * dy);
                    if (dist < minSep && dist > 0) {
                        final int push = (minSep - dist) / 2 + 1;
                        final int px = (dx == 0) ? push : (push * Integer.signum(dx));
                        final int py = (dy == 0) ? push : (push * Integer.signum(dy));
                        a[0] -= px;
                        a[1] -= py;
                        b[0] += px;
                        b[1] += py;
                        moved = true;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        for (TrainMapData.StationEntry station3 : stations) {
            final int[] p = pixels.get(station3.id);
            p[0] = Math.max(x + 4, Math.min(x + w - 4, p[0]));
            p[1] = Math.max(y + 4, Math.min(y + h - 4, p[1]));
            layout.put(station3.id, new float[] { p[0], p[1] });
        }
        return layout;
    }

    private void buildTrainDashboardRoutes(TrainMapData data, Map<String, TrainMapData.StationEntry> byName, Map<String, Integer> routeByKey, List<List<String>> routeStops, List<Integer> routeColors, Map<String, Integer> trainToRoute, Map<String, List<Integer>> edgeRoutes) {
        final Map<UUID, TrainMapData.StationEntry> byId = new HashMap<UUID, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station : data.getStations()) {
            byId.put(station.id, station);
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final List<TrainMapData.StationEntry> route = this.resolveTrainDashboardRoute(train, byName, byId);
            if (route.size() < 2 && train.scheduleStops.isEmpty()) {
                continue;
            }
            final String key = (train.routeKey != null && !train.routeKey.isEmpty()) ? train.routeKey : (route.size() >= 2 ? this.trainDashboardRouteKey(route) : String.join("|", train.scheduleStops));
            Integer routeIndex = routeByKey.get(key);
            if (routeIndex == null) {
                routeIndex = routeStops.size();
                routeStops.add(this.trainDashboardRouteLabels(route, train.scheduleStops));
                routeColors.add(StatsDisplayScreen.TRAIN_ROUTE_PALETTE[routeIndex % StatsDisplayScreen.TRAIN_ROUTE_PALETTE.length]);
                routeByKey.put(key, routeIndex);
                if (route.size() >= 2) {
                    this.addTrainDashboardRouteEdges(edgeRoutes, route, routeIndex);
                }
            }
            trainToRoute.put(train.id.toString(), routeIndex);
        }
    }

    private String trainDashboardRouteKey(List<TrainMapData.StationEntry> route) {
        final ArrayList<String> ids = new ArrayList<String>();
        for (TrainMapData.StationEntry station : route) {
            ids.add(station.id.toString());
        }
        return String.join("|", ids);
    }

    private List<String> trainDashboardRouteLabels(List<TrainMapData.StationEntry> route, List<String> fallbackStops) {
        if (route.isEmpty()) {
            return new ArrayList<String>(fallbackStops);
        }
        final ArrayList<String> labels = new ArrayList<String>();
        for (TrainMapData.StationEntry station : route) {
            labels.add(station.name);
        }
        return labels;
    }

    private void addTrainDashboardRouteEdges(Map<String, List<Integer>> edgeRoutes, List<TrainMapData.StationEntry> route, int routeIndex) {
        TrainMapData.StationEntry previous = null;
        for (TrainMapData.StationEntry station : route) {
            if (previous != null && !previous.id.equals(station.id)) {
                this.addTrainDashboardEdge(edgeRoutes, previous.id, station.id, routeIndex);
            }
            previous = station;
        }
        if (route.size() >= 2) {
            final TrainMapData.StationEntry first = route.get(0);
            final TrainMapData.StationEntry last = route.get(route.size() - 1);
            if (!first.id.equals(last.id)) {
                this.addTrainDashboardEdge(edgeRoutes, last.id, first.id, routeIndex);
            }
        }
    }

    private void addTrainDashboardEdge(Map<String, List<Integer>> edgeRoutes, UUID a, UUID b, int routeIndex) {
        final String key = (a.compareTo(b) <= 0) ? (a.toString() + ":" + b.toString()) : (b.toString() + ":" + a.toString());
        final List<Integer> routes = edgeRoutes.computeIfAbsent(key, k -> new ArrayList<Integer>());
        if (!routes.contains(routeIndex)) {
            routes.add(routeIndex);
        }
    }

    private int[] getTrainDashboardTrainPixel(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, float[]> layout) {
        TrainMapData.StationEntry destination = this.findTrainDashboardStationById(train.activeSegmentToId);
        if (destination == null) {
            destination = this.findTrainDashboardStationById(train.destinationStationId);
        }
        if (destination == null) {
            destination = this.findTrainDashboardStation(byName, train.nextDestination);
        }
        if (train.stopped) {
            TrainMapData.StationEntry at = this.findTrainDashboardStationById(train.currentStationId);
            if (at == null) {
                at = train.currentStationName.isEmpty() ? destination : byName.get(train.currentStationName);
            }
            if (at == null) {
                at = destination;
            }
            if (at == null) {
                return null;
            }
            final float[] pos = layout.get(at.id);
            return (pos == null) ? null : new int[] { (int)pos[0], (int)pos[1] };
        }
        TrainMapData.StationEntry prev = this.findTrainDashboardStationById(train.activeSegmentFromId);
        if (prev == null) {
            prev = this.findTrainDashboardStationById(train.previousStationId);
        }
        if (prev == null) {
            prev = this.findTrainDashboardPrevStation(train, byName);
        }
        if (prev != null && destination != null && !prev.id.equals(destination.id)) {
            final float[] from = layout.get(prev.id);
            final float[] to = layout.get(destination.id);
            if (from == null || to == null) {
                return null;
            }
            final float progress;
            if (train.activeSegmentFromId != null && train.activeSegmentToId != null) {
                progress = train.segmentProgress;
            }
            else if (train.positionValid) {
                final float[] ipos = this.getTrainDashboardInterpolatedWorld(train);
                progress = this.estimateTrainDashboardProgress(ipos[0], ipos[1], prev, destination);
            }
            else {
                progress = 0.0f;
            }
            return new int[] { (int)(from[0] + (to[0] - from[0]) * progress), (int)(from[1] + (to[1] - from[1]) * progress) };
        }
        if (destination != null) {
            final float[] pos2 = layout.get(destination.id);
            return (pos2 == null) ? null : new int[] { (int)pos2[0], (int)pos2[1] };
        }
        return null;
    }

    private void renderTrainDashboardMap(GuiGraphics g, TrainMapData data, Map<String, TrainMapData.StationEntry> byName, Map<UUID, float[]> layout, List<Integer> routeColors, Map<String, Integer> trainToRoute, Map<String, List<Integer>> edgeRoutes, int x, int y, int w, int h) {
        g.fill(x, y + 28, x + w, y + h, -15921888);
        this.drawGUIBorder(g, x, y + 28, w, h - 28, -14013884);
        g.drawString(this.font, "\u00a77Map", x + 6, y + 32, this.DIM);
        for (Map.Entry<String, List<Integer>> entry : edgeRoutes.entrySet()) {
            final String[] parts = entry.getKey().split(":");
            if (parts.length != 2) {
                continue;
            }
            final UUID a;
            final UUID b;
            try {
                a = UUID.fromString(parts[0]);
                b = UUID.fromString(parts[1]);
            }
            catch (IllegalArgumentException ex) {
                CreateStats.LOGGER.warn("Skipping malformed train dashboard edge '{}'", entry.getKey());
                continue;
            }
            final float[] pa = layout.get(a);
            final float[] pb = layout.get(b);
            if (pa == null || pb == null) {
                continue;
            }
            final List<Integer> routes = entry.getValue();
            final float dx = pb[0] - pa[0];
            final float dy = pb[1] - pa[1];
            final float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0f) {
                continue;
            }
            final float nx = -dy / len;
            final float ny = dx / len;
            final float gap = 3.5f;
            final int selectedRoute = (this.selectedTrainDashboardTrainId != null) ? trainToRoute.getOrDefault(this.selectedTrainDashboardTrainId.toString(), -1) : -1;
            for (int i = 0; i < routes.size(); ++i) {
                final int routeIndex = routes.get(i);
                int color = routeColors.get(routeIndex);
                if (selectedRoute >= 0 && routeIndex != selectedRoute) {
                    color = (color & 0xFFFFFF) | 0x44000000;
                }
                final float offset = (i - (routes.size() - 1) / 2.0f) * gap;
                final int ox = (int)(nx * offset);
                final int oy = (int)(ny * offset);
                this.drawThickLine(g, (int)pa[0] + ox, (int)pa[1] + oy, (int)pb[0] + ox, (int)pb[1] + oy, color, (selectedRoute == routeIndex) ? 3 : 2);
            }
        }
        for (TrainMapData.StationEntry station : data.getStations()) {
            final float[] pos = layout.get(station.id);
            if (pos == null) {
                continue;
            }
            int color2 = station.assembling ? -13244 : -2232577;
            if (station.id.equals(this.selectedTrainDashboardStationId)) {
                color2 = this.ACCENT;
                g.fill((int)pos[0] - 7, (int)pos[1] - 7, (int)pos[0] + 8, (int)pos[1] + 8, (color2 & 0xFFFFFF) | 0x22000000);
            }
            g.fill((int)pos[0] - 3, (int)pos[1] - 3, (int)pos[0] + 4, (int)pos[1] + 4, color2);
            g.drawString(this.font, this.truncate(station.name, 70), (int)pos[0] + 6, (int)pos[1] - 4, station.id.equals(this.selectedTrainDashboardStationId) ? this.TEXT : this.DIM);
        }
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final int[] pos2 = this.getTrainDashboardTrainPixel(train, byName, layout);
            if (pos2 == null) {
                continue;
            }
            int color3 = routeColors.isEmpty() ? this.ACCENT : routeColors.get(trainToRoute.getOrDefault(train.id.toString(), 0) % routeColors.size());
            if (train.waitingForSignal) {
                color3 = -13278;
            }
            else if (train.stopped) {
                color3 = -39356;
            }
            if (train.id.equals(this.selectedTrainDashboardTrainId)) {
                g.fill(pos2[0] - 7, pos2[1] - 7, pos2[0] + 8, pos2[1] + 8, (color3 & 0xFFFFFF) | 0x22000000);
            }
            g.fill(pos2[0] - 2, pos2[1] - 2, pos2[0] + 3, pos2[1] + 3, color3);
        }
        final String expandText = "\u00a7e\u00a7n[ Open full train map ]";
        final int expandW = this.font.width("[ Open full train map ]");
        this.trainExpandBtnX = x + w / 2 - expandW / 2;
        this.trainExpandBtnY = y + h - 12;
        this.trainExpandBtnW = expandW;
        this.trainExpandBtnH = 10;
        g.drawCenteredString(this.font, expandText, x + w / 2, this.trainExpandBtnY, -13312);
    }

    private void renderTrainDashboardTrainList(GuiGraphics g, TrainMapData data, List<List<String>> routeStops, List<Integer> routeColors, Map<String, Integer> trainToRoute, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, -15658715);
        this.drawGUIBorder(g, x, y, w, h, -14013884);
        g.drawString(this.font, "\u00a77Trains", x + 6, y + 4, this.ACCENT);
        final List<TrainMapData.TrainEntry> trains = new ArrayList<TrainMapData.TrainEntry>(data.getTrains());
        trains.sort(Comparator.comparing((TrainMapData.TrainEntry t) -> t.waitingForSignal ? 0 : (t.stopped ? 1 : 2)).thenComparing(t -> t.name.toLowerCase()));
        final int rowH = 22;
        final int listY = y + 18;
        final int listH = h - 22;
        final int visible = Math.max(1, listH / rowH);
        final int maxScroll = Math.max(0, trains.size() - visible);
        this.ensureTrainDashboardTrainVisible(trains, visible, this.selectedTrainDashboardTrainId);
        this.trainDashboardTrainScroll = Math.max(0, Math.min(this.trainDashboardTrainScroll, maxScroll));
        for (int i = 0; i < visible && i + this.trainDashboardTrainScroll < trains.size(); ++i) {
            final int idx = i + this.trainDashboardTrainScroll;
            final TrainMapData.TrainEntry train = trains.get(idx);
            final int ry = listY + i * rowH;
            final boolean selected = train.id.equals(this.selectedTrainDashboardTrainId);
            final boolean hover = mouseX >= x + 1 && mouseX < x + w - 1 && mouseY >= ry && mouseY < ry + rowH;
            g.fill(x + 1, ry, x + w - 1, ry + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
            final int routeIndex = trainToRoute.getOrDefault(train.id.toString(), -1);
            final int routeColor = (routeIndex >= 0 && routeIndex < routeColors.size()) ? routeColors.get(routeIndex) : this.ACCENT;
            g.fill(x + 3, ry + 3, x + 6, ry + rowH - 4, routeColor);
            final String state = train.waitingForSignal ? ("Signal " + Math.max(0L, train.ticksWaitingForSignal / 20L) + "s") : (train.stopped ? (!train.currentStationName.isEmpty() ? ("Stopped @ " + train.currentStationName) : "Stopped") : "Moving");
            final String speed = String.format("%.1f m/s", Math.abs(train.speed) * 20.0f);
            g.drawString(this.font, this.truncate(train.name, w - 18), x + 10, ry + 3, selected ? this.TEXT : -2236929);
            g.drawString(this.font, this.truncate(state + " | " + speed + (train.nextDestination.isEmpty() ? "" : (" | " + train.nextDestination)), w - 18), x + 10, ry + 12, selected ? this.ACCENT : this.DIM);
        }
        if (maxScroll > 0) {
            this.renderTrainDashboardScrollbar(g, x + w - 4, listY, 3, listH, visible, trains.size(), this.trainDashboardTrainScroll);
        }
    }

    private void renderTrainDashboardStationList(GuiGraphics g, TrainMapData data, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, -15658715);
        this.drawGUIBorder(g, x, y, w, h, -14013884);
        g.drawString(this.font, "\u00a77Stations", x + 6, y + 4, this.ACCENT);
        final Map<String, Integer> docked = new HashMap<String, Integer>();
        final Map<String, Integer> incoming = new HashMap<String, Integer>();
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            if (!train.currentStationName.isEmpty()) {
                docked.merge(train.currentStationName, 1, Integer::sum);
            }
            if (!train.nextDestination.isEmpty()) {
                incoming.merge(train.nextDestination, 1, Integer::sum);
            }
        }
        final List<TrainMapData.StationEntry> stations = new ArrayList<TrainMapData.StationEntry>(data.getStations());
        stations.sort(Comparator.comparing((TrainMapData.StationEntry s) -> s.assembling ? 0 : 1).thenComparing(s -> s.name.toLowerCase()));
        final int rowH = 20;
        final int listY = y + 18;
        final int listH = h - 22;
        final int visible = Math.max(1, listH / rowH);
        final int maxScroll = Math.max(0, stations.size() - visible);
        this.ensureTrainDashboardStationVisible(stations, visible, this.selectedTrainDashboardStationId);
        this.trainDashboardStationScroll = Math.max(0, Math.min(this.trainDashboardStationScroll, maxScroll));
        for (int i = 0; i < visible && i + this.trainDashboardStationScroll < stations.size(); ++i) {
            final int idx = i + this.trainDashboardStationScroll;
            final TrainMapData.StationEntry station = stations.get(idx);
            final int ry = listY + i * rowH;
            final boolean selected = station.id.equals(this.selectedTrainDashboardStationId);
            final boolean hover = mouseX >= x + 1 && mouseX < x + w - 1 && mouseY >= ry && mouseY < ry + rowH;
            g.fill(x + 1, ry, x + w - 1, ry + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
            g.fill(x + 4, ry + 7, x + 8, ry + 11, station.assembling ? -13244 : -2232577);
            g.drawString(this.font, this.truncate(station.name, w - 18), x + 12, ry + 3, selected ? this.TEXT : -2236929);
            g.drawString(this.font, this.truncate(String.format("%s | docked %d | incoming %d", station.assembling ? "assembling" : "idle", docked.getOrDefault(station.name, 0), incoming.getOrDefault(station.name, 0)), w - 18), x + 12, ry + 11, selected ? this.ACCENT : this.DIM);
        }
        if (maxScroll > 0) {
            this.renderTrainDashboardScrollbar(g, x + w - 4, listY, 3, listH, visible, stations.size(), this.trainDashboardStationScroll);
        }
    }

    private void renderTrainDashboardScrollbar(GuiGraphics g, int x, int y, int w, int h, int visibleRows, int totalRows, int scrollOffset) {
        g.fill(x, y, x + w, y + h, 1090519039);
        final int thumbH = Math.max(10, h * visibleRows / Math.max(visibleRows, totalRows));
        final int maxScroll = Math.max(1, totalRows - visibleRows);
        final int thumbY = y + (int)(scrollOffset / (float)maxScroll * Math.max(0, h - thumbH));
        g.fill(x, thumbY, x + w, thumbY + thumbH, -1426063361);
    }

    private boolean handleTrainDashboardClick(double mx, double my) {
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        if (visibleData == null || visibleData.isEmpty()) {
            return false;
        }
        this.clampTrainDashboardScroll(visibleData);
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        final int gap = 6;
        final int rightW = Math.max(170, Math.min(240, w / 3));
        final int mapW = w - rightW - gap;
        final int rightX = x + mapW + gap;
        final int trainPanelH = Math.max(88, (h - gap) / 2);
        final int stationPanelH = h - trainPanelH - gap;
        final int stationY = y + trainPanelH + gap;
        final List<TrainMapData.TrainEntry> trains = new ArrayList<TrainMapData.TrainEntry>(visibleData.getTrains());
        trains.sort(Comparator.comparing((TrainMapData.TrainEntry t) -> t.waitingForSignal ? 0 : (t.stopped ? 1 : 2)).thenComparing(t -> t.name.toLowerCase()));
        final int trainRowH = 22;
        final int trainListY = y + 18;
        final int trainVisible = Math.max(1, (trainPanelH - 22) / trainRowH);
        if (mx >= rightX && mx < rightX + rightW && my >= trainListY && my < trainListY + trainVisible * trainRowH) {
            final int row = (int)((my - trainListY) / trainRowH) + this.trainDashboardTrainScroll;
            if (row >= 0 && row < trains.size()) {
                final TrainMapData.TrainEntry train = trains.get(row);
                this.selectedTrainDashboardTrainId = train.id;
                this.selectedTrainDashboardStationId = train.currentStationId != null ? train.currentStationId : this.findTrainDashboardStationByName(visibleData, train.currentStationName);
                this.ensureTrainDashboardTrainVisible(trains, trainVisible, train.id);
                return true;
            }
        }
        final List<TrainMapData.StationEntry> stations = new ArrayList<TrainMapData.StationEntry>(visibleData.getStations());
        stations.sort(Comparator.comparing((TrainMapData.StationEntry s) -> s.assembling ? 0 : 1).thenComparing(s -> s.name.toLowerCase()));
        final int stationRowH = 20;
        final int stationListY = stationY + 18;
        final int stationVisible = Math.max(1, (stationPanelH - 22) / stationRowH);
        if (mx >= rightX && mx < rightX + rightW && my >= stationListY && my < stationListY + stationVisible * stationRowH) {
            final int row2 = (int)((my - stationListY) / stationRowH) + this.trainDashboardStationScroll;
            if (row2 >= 0 && row2 < stations.size()) {
                final TrainMapData.StationEntry station = stations.get(row2);
                this.selectedTrainDashboardStationId = station.id;
                this.selectedTrainDashboardTrainId = null;
                this.ensureTrainDashboardStationVisible(stations, stationVisible, station.id);
                return true;
            }
        }
        final Map<String, TrainMapData.StationEntry> byName = new HashMap<String, TrainMapData.StationEntry>();
        for (TrainMapData.StationEntry station2 : visibleData.getStations()) {
            byName.put(station2.name, station2);
        }
        final Map<UUID, float[]> layout = this.computeTrainDashboardLayout(visibleData, x + 8, y + 48, mapW - 16, h - 74);
        for (TrainMapData.StationEntry station3 : visibleData.getStations()) {
            final float[] pos = layout.get(station3.id);
            if (pos != null && Math.sqrt((mx - pos[0]) * (mx - pos[0]) + (my - pos[1]) * (my - pos[1])) <= 8.0) {
                this.selectedTrainDashboardStationId = station3.id;
                this.selectedTrainDashboardTrainId = null;
                this.ensureTrainDashboardStationVisible(stations, stationVisible, station3.id);
                return true;
            }
        }
        for (TrainMapData.TrainEntry train2 : visibleData.getTrains()) {
            final int[] pos2 = this.getTrainDashboardTrainPixel(train2, byName, layout);
            if (pos2 != null && Math.sqrt((mx - pos2[0]) * (mx - pos2[0]) + (my - pos2[1]) * (my - pos2[1])) <= 7.0) {
                this.selectedTrainDashboardTrainId = train2.id;
                this.selectedTrainDashboardStationId = train2.currentStationId != null ? train2.currentStationId : this.findTrainDashboardStationByName(visibleData, train2.currentStationName);
                this.ensureTrainDashboardTrainVisible(trains, trainVisible, train2.id);
                if (this.selectedTrainDashboardStationId != null) {
                    this.ensureTrainDashboardStationVisible(stations, stationVisible, this.selectedTrainDashboardStationId);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleTrainDashboardScroll(double mx, double my, double delta) {
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        this.clampTrainDashboardScroll(visibleData);
        final int x = this.graphLeft;
        final int y = this.graphTop;
        final int w = this.graphWidth;
        final int h = this.graphHeight;
        final int gap = 6;
        final int rightW = Math.max(170, Math.min(240, w / 3));
        final int mapW = w - rightW - gap;
        final int rightX = x + mapW + gap;
        final int trainPanelH = Math.max(88, (h - gap) / 2);
        final int stationPanelH = h - trainPanelH - gap;
        final int stationY = y + trainPanelH + gap;
        if (mx >= rightX && mx < rightX + rightW && my >= y && my < y + trainPanelH) {
            final int trainVisible = Math.max(1, (trainPanelH - 22) / 22);
            final int maxTrainScroll = (visibleData == null) ? 0 : Math.max(0, visibleData.getTrains().size() - trainVisible);
            this.trainDashboardTrainScroll = Math.max(0, Math.min(this.trainDashboardTrainScroll - (int)delta, maxTrainScroll));
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= stationY && my < stationY + stationPanelH) {
            final int stationVisible = Math.max(1, (stationPanelH - 22) / 20);
            final int maxStationScroll = (visibleData == null) ? 0 : Math.max(0, visibleData.getStations().size() - stationVisible);
            this.trainDashboardStationScroll = Math.max(0, Math.min(this.trainDashboardStationScroll - (int)delta, maxStationScroll));
            return true;
        }
        return false;
    }

    private boolean isFactoryPage() {
        return this.selectedSource == SOURCE_PAGE_FACTORY;
    }

    private boolean isInventoryStatusPage() {
        return this.selectedSource == SOURCE_PAGE_INVENTORY_STATUS;
    }

    private boolean isFactoryStatusPage() {
        return this.selectedSource == SOURCE_PAGE_FACTORY_STATUS;
    }

    private void selectInventoryStatusPage() {
        final boolean changed = this.selectedSource != SOURCE_PAGE_INVENTORY_STATUS;
        this.selectedSource = SOURCE_PAGE_INVENTORY_STATUS;
        this.inventoryScrollOffset = 0;
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectFactoryStatusPage() {
        final boolean changed = this.selectedSource != SOURCE_PAGE_FACTORY_STATUS;
        this.selectedSource = SOURCE_PAGE_FACTORY_STATUS;
        this.factoryStatusScrollOffset = 0;
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private void selectFactoryPage() {
        final boolean changed = this.selectedSource != SOURCE_PAGE_FACTORY;
        this.selectedSource = SOURCE_PAGE_FACTORY;
        this.resetPageScopedSelection();
        this.ensureGuiViewTypeValid();
        this.rememberCurrentPageSelection();
        if (changed) {
            this.rebuildWidgets();
        }
    }

    private List<StatsDisplayPanelBE.SafetyClutchLink> getFactoryClutchLinks() {
        return new ArrayList<StatsDisplayPanelBE.SafetyClutchLink>(this.panel.getSafetyClutchLinks());
    }

    private List<StatsDisplayPanelBE.KineticFlywheelLink> getFactoryFlywheelLinks() {
        return new ArrayList<StatsDisplayPanelBE.KineticFlywheelLink>(this.panel.getKineticFlywheelLinks());
    }

    private List<StatsDisplayPanelBE.AutoTransmissionClutchLink> getFactoryTransmissionLinks() {
        return new ArrayList<StatsDisplayPanelBE.AutoTransmissionClutchLink>(this.panel.getAutoTransmissionClutchLinks());
    }

    private List<StatsDisplayPanelBE.LoadBalancerLink> getFactoryLoadBalancerLinks() {
        return new ArrayList<StatsDisplayPanelBE.LoadBalancerLink>(this.panel.getLoadBalancerLinks());
    }

    private List<StatsDisplayPanelBE.MiniDisplayLink> getFactoryMiniDisplayLinks() {
        return new ArrayList<StatsDisplayPanelBE.MiniDisplayLink>(this.panel.getMiniDisplayLinks());
    }

    private List<StatsDisplayPanelBE.FreightManifestLink> getFactoryManifestLinks() {
        return new ArrayList<StatsDisplayPanelBE.FreightManifestLink>(this.panel.getFreightManifestLinks());
    }

    private List<StatsDisplayPanelBE.TrainStationLink> getFactoryTrainStationLinks() {
        return new ArrayList<StatsDisplayPanelBE.TrainStationLink>(this.panel.getTrainStationLinks());
    }

    private List<FactoryFreightEntry> getFactoryFreightEntries(List<StatsDisplayPanelBE.FreightManifestLink> manifestLinks,
                                                               List<StatsDisplayPanelBE.TrainStationLink> stationLinks) {
        final LinkedHashMap<String, FactoryFreightEntry> merged = new LinkedHashMap<String, FactoryFreightEntry>();
        for (StatsDisplayPanelBE.FreightManifestLink manifestLink : manifestLinks) {
            final String key = this.getFactoryFreightKey(manifestLink);
            final FactoryFreightEntry entry = merged.computeIfAbsent(key, ignored -> new FactoryFreightEntry("freight:" + key));
            entry.manifestLink = manifestLink;
        }
        for (StatsDisplayPanelBE.TrainStationLink stationLink : stationLinks) {
            final String key = this.getFactoryFreightKey(stationLink);
            final FactoryFreightEntry entry = merged.computeIfAbsent(key, ignored -> new FactoryFreightEntry("freight:" + key));
            entry.stationLink = stationLink;
        }
        final List<FactoryFreightEntry> entries = new ArrayList<FactoryFreightEntry>(merged.values());
        entries.sort(Comparator.comparing(FactoryFreightEntry::getDisplayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.id, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private String getFactoryFreightKey(StatsDisplayPanelBE.FreightManifestLink manifestLink) {
        final FreightStationLink stationLink = manifestLink.getStationLink();
        if (stationLink.isLinked()) {
            return this.getFactoryFreightKey(stationLink.getStationId(), stationLink.getStationName(),
                stationLink.getStationDimension() == null ? "" : stationLink.getStationDimension().toString(),
                stationLink.getStationPos(), manifestLink.getSourcePos(), "clipboard");
        }
        return "clipboard:" + manifestLink.getSourcePos().asLong();
    }

    private String getFactoryFreightKey(StatsDisplayPanelBE.TrainStationLink stationLink) {
        return this.getFactoryFreightKey(stationLink.getStationId(), stationLink.getStationName(),
            stationLink.getStationDimension(), stationLink.getStationPos(), stationLink.getSourcePos(), "station");
    }

    private String getFactoryFreightKey(UUID stationId, String stationName, String dimension, BlockPos stationPos, BlockPos fallbackPos, String fallbackPrefix) {
        if (stationId != null) {
            return "id:" + stationId;
        }
        if (stationName != null && !stationName.isBlank()) {
            final String dimPart = dimension == null ? "" : dimension;
            final String posPart = stationPos != null ? stationPos.asLong() + "" : Long.toString(fallbackPos.asLong());
            return "name:" + stationName.toLowerCase(java.util.Locale.ROOT) + "|" + dimPart + "|" + posPart;
        }
        return fallbackPrefix + ":" + fallbackPos.asLong();
    }

    private void normalizeFactorySelection(List<StatsDisplayPanelBE.SafetyClutchLink> safetyLinks,
                                           List<StatsDisplayPanelBE.KineticFlywheelLink> flywheelLinks,
                                           List<StatsDisplayPanelBE.AutoTransmissionClutchLink> transLinks,
                                           List<StatsDisplayPanelBE.LoadBalancerLink> balancerLinks,
                                           List<StatsDisplayPanelBE.MiniDisplayLink> miniDisplayLinks,
                                           List<FactoryFreightEntry> freightEntries,
                                           int visibleRows) {
        final int totalRows = this.getFactoryListRowCount(safetyLinks, flywheelLinks, transLinks, balancerLinks, miniDisplayLinks, freightEntries);
        if (totalRows <= 0) {
            this.factorySelectedClutchId = "";
            this.factoryClutchScroll = 0;
            return;
        }

        String firstSelectableId = "";
        int selectedRow = -1;
        int rowIndex = 0;
        if (!safetyLinks.isEmpty()) {
            rowIndex++;
            firstSelectableId = safetyLinks.get(0).getId();
            for (int i = 0; i < safetyLinks.size(); ++i) {
                if (safetyLinks.get(i).getId().equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += safetyLinks.size();
        }
        if (!flywheelLinks.isEmpty()) {
            rowIndex++;
            if (firstSelectableId.isEmpty()) {
                firstSelectableId = flywheelLinks.get(0).getId();
            }
            for (int i = 0; i < flywheelLinks.size(); ++i) {
                if (flywheelLinks.get(i).getId().equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += flywheelLinks.size();
        }
        if (!transLinks.isEmpty()) {
            rowIndex++;
            if (firstSelectableId.isEmpty()) {
                firstSelectableId = transLinks.get(0).getId();
            }
            for (int i = 0; i < transLinks.size(); ++i) {
                if (transLinks.get(i).getId().equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += transLinks.size();
        }
        if (!balancerLinks.isEmpty()) {
            rowIndex++;
            if (firstSelectableId.isEmpty()) {
                firstSelectableId = balancerLinks.get(0).getId();
            }
            for (int i = 0; i < balancerLinks.size(); ++i) {
                if (balancerLinks.get(i).getId().equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += balancerLinks.size();
        }
        if (!miniDisplayLinks.isEmpty()) {
            rowIndex++;
            if (firstSelectableId.isEmpty()) {
                firstSelectableId = miniDisplayLinks.get(0).getId();
            }
            for (int i = 0; i < miniDisplayLinks.size(); ++i) {
                if (miniDisplayLinks.get(i).getId().equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += miniDisplayLinks.size();
        }
        if (!freightEntries.isEmpty()) {
            rowIndex++;
            if (firstSelectableId.isEmpty()) {
                firstSelectableId = freightEntries.get(0).id;
            }
            for (int i = 0; i < freightEntries.size(); ++i) {
                if (freightEntries.get(i).id.equals(this.factorySelectedClutchId)) {
                    selectedRow = rowIndex + i;
                }
            }
            rowIndex += freightEntries.size();
        }

        if (selectedRow < 0) {
            this.factorySelectedClutchId = firstSelectableId;
            final int safetyOffset = safetyLinks.isEmpty() ? 0 : safetyLinks.size() + 1;
            final int flywheelOffset = flywheelLinks.isEmpty() ? 0 : flywheelLinks.size() + 1;
            final int transOffset = transLinks.isEmpty() ? 0 : transLinks.size() + 1;
            final int balancerOffset = balancerLinks.isEmpty() ? 0 : balancerLinks.size() + 1;
            final int miniOffset = miniDisplayLinks.isEmpty() ? 0 : miniDisplayLinks.size() + 1;
            if (firstSelectableId.startsWith("flywheel_")) {
                selectedRow = safetyOffset + 1;
            } else if (firstSelectableId.startsWith("trans_")) {
                selectedRow = safetyOffset + flywheelOffset + 1;
            } else if (firstSelectableId.startsWith("bal_")) {
                selectedRow = safetyOffset + flywheelOffset + transOffset + 1;
            } else if (firstSelectableId.startsWith("mini_")) {
                selectedRow = safetyOffset + flywheelOffset + transOffset + balancerOffset + 1;
            } else if (firstSelectableId.startsWith("freight:")) {
                selectedRow = safetyOffset + flywheelOffset + transOffset + balancerOffset + miniOffset + 1;
            } else {
                selectedRow = 1;
            }
        }

        final int maxScroll = Math.max(0, totalRows - visibleRows);
        this.factoryClutchScroll = Math.max(0, Math.min(this.factoryClutchScroll, maxScroll));
        if (selectedRow < this.factoryClutchScroll) {
            this.factoryClutchScroll = selectedRow;
        }
        else if (selectedRow >= this.factoryClutchScroll + visibleRows) {
            this.factoryClutchScroll = Math.max(0, selectedRow - visibleRows + 1);
        }
    }

    private int getFactoryListRowCount(List<StatsDisplayPanelBE.SafetyClutchLink> safetyLinks,
                                       List<StatsDisplayPanelBE.KineticFlywheelLink> flywheelLinks,
                                       List<StatsDisplayPanelBE.AutoTransmissionClutchLink> transLinks,
                                       List<StatsDisplayPanelBE.LoadBalancerLink> balancerLinks,
                                       List<StatsDisplayPanelBE.MiniDisplayLink> miniDisplayLinks,
                                       List<FactoryFreightEntry> freightEntries) {
        int totalRows = 0;
        if (!safetyLinks.isEmpty()) {
            totalRows += 1 + safetyLinks.size();
        }
        if (!flywheelLinks.isEmpty()) {
            totalRows += 1 + flywheelLinks.size();
        }
        if (!transLinks.isEmpty()) {
            totalRows += 1 + transLinks.size();
        }
        if (!balancerLinks.isEmpty()) {
            totalRows += 1 + balancerLinks.size();
        }
        if (!miniDisplayLinks.isEmpty()) {
            totalRows += 1 + miniDisplayLinks.size();
        }
        if (!freightEntries.isEmpty()) {
            totalRows += 1 + freightEntries.size();
        }
        return totalRows;
    }

    private void addFactoryClickTarget(FactoryClickAction action, int x, int y, int w, int h, String value) {
        if (action == null || w <= 0 || h <= 0) {
            return;
        }
        this.factoryClickTargets.add(new FactoryClickTarget(action, x, y, w, h, value));
    }

    private void renderFactoryButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, boolean selected, int mouseX, int mouseY, FactoryClickAction action, String value) {
        final boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        final int bg = selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : this.TAB_BG);
        g.fill(x, y, x + w, y + h, enabled ? bg : -15329770);
        this.drawGUIBorder(g, x, y, w, h, selected ? this.ACCENT : -13417370);
        final int textColor = selected ? this.TEXT : (enabled ? this.DIM : -7763575);
        g.drawCenteredString(this.font, this.truncate(label, Math.max(14, w - 8)), x + w / 2, y + (h - 8) / 2, textColor);
        if (enabled) {
            this.addFactoryClickTarget(action, x, y, w, h, value);
        }
    }

    private void renderFactoryControlsPage(GuiGraphics g, int mouseX, int mouseY) {
        this.factoryClickTargets.clear();
        final int areaX = this.graphLeft;
        final int areaY = this.graphTop;
        final int areaW = this.graphWidth;
        final int areaH = this.graphHeight;
        final int margin = 10;
        final int contentX = areaX + margin;
        final int contentW = areaW - margin * 2;

        final List<StatsDisplayPanelBE.SafetyClutchLink> safetyLinks = this.getFactoryClutchLinks();
        final List<StatsDisplayPanelBE.KineticFlywheelLink> flywheelLinks = this.getFactoryFlywheelLinks();
        final List<StatsDisplayPanelBE.AutoTransmissionClutchLink> transLinks = this.getFactoryTransmissionLinks();
        final List<StatsDisplayPanelBE.LoadBalancerLink> balancerLinks = this.getFactoryLoadBalancerLinks();
        final List<StatsDisplayPanelBE.MiniDisplayLink> miniDisplayLinks = this.getFactoryMiniDisplayLinks();
        final List<StatsDisplayPanelBE.FreightManifestLink> manifestLinks = this.getFactoryManifestLinks();
        final List<StatsDisplayPanelBE.TrainStationLink> stationLinks = this.getFactoryTrainStationLinks();
        final List<FactoryFreightEntry> freightEntries = this.getFactoryFreightEntries(manifestLinks, stationLinks);

        final boolean hasAny = !safetyLinks.isEmpty() || !flywheelLinks.isEmpty() || !transLinks.isEmpty() || !balancerLinks.isEmpty()
                || !miniDisplayLinks.isEmpty() || !freightEntries.isEmpty();

        if (!hasAny) {
            g.drawString(this.font, "\u2699 Factory Controls", contentX, areaY + 4, this.ACCENT);
            g.drawCenteredString(this.font, "No controls linked", areaX + areaW / 2, areaY + areaH / 2 - 16, this.DIM);
            g.drawCenteredString(this.font, "Connect a Safety Clutch, Kinetic Flywheel, Auto Variable Clutch, Load Balancer, or Mini Display,", areaX + areaW / 2, areaY + areaH / 2, -12303258);
            g.drawCenteredString(this.font, "or a Train Station / Freight Manifest Clipboard via Display Link", areaX + areaW / 2, areaY + areaH / 2 + 14, -12303258);
            return;
        }

        g.drawString(this.font, "\u2699 Factory Controls", contentX, areaY + 4, this.ACCENT);

        final int listW = Math.min(Math.max(140, contentW / 3), 200);
        final int listTop = areaY + 18;
        final int listH = areaH - 24;
        final int rowH = 22;
        final int sectionHeaderH = rowH;
        final int visibleRows = Math.max(1, listH / rowH);
        this.normalizeFactorySelection(safetyLinks, flywheelLinks, transLinks, balancerLinks, miniDisplayLinks, freightEntries, visibleRows);

        final boolean hasSafety = !safetyLinks.isEmpty();
        final boolean hasFlywheels = !flywheelLinks.isEmpty();
        final boolean hasTrans = !transLinks.isEmpty();
        final boolean hasBalancers = !balancerLinks.isEmpty();
        final boolean hasMiniDisplays = !miniDisplayLinks.isEmpty();
        final boolean hasFreight = !freightEntries.isEmpty();
        g.fill(contentX, listTop, contentX + listW, listTop + listH, -15198184);
        this.drawGUIBorder(g, contentX, listTop, listW, listH, -13417370);

        int drawY = listTop - this.factoryClutchScroll * rowH;

        if (hasSafety) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\u26A0 Safety Clutches", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < safetyLinks.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final StatsDisplayPanelBE.SafetyClutchLink link = safetyLinks.get(i);
                    final SafetyClutchSettings linkSettings = link.getSettings();
                    final boolean selected = link.getId().equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4, this.getSafetyClutchStatusColor(linkSettings, link.isTripped(), link.isNetworkRunning()));
                    g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(link.getSourcePos()), listW - 50), contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    final String stressLabel = String.format("%.0f%%", link.getStressPercent());
                    g.drawString(this.font, stressLabel, contentX + listW - this.font.width(stressLabel) - 10, drawY + 3, selected ? this.ACCENT : this.DIM);
                    final String rowStatus = this.getSafetyClutchStatusLabel(linkSettings, link.isTripped(), link.isNetworkRunning());
                    g.drawString(this.font, rowStatus, contentX + 12, drawY + 13, this.getSafetyClutchStatusColor(linkSettings, link.isTripped(), link.isNetworkRunning()));
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_CLUTCH, contentX + 1, drawY, listW - 6, rowH - 1, link.getId());
                }
                drawY += rowH;
            }
        }

        if (hasFlywheels) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\uD83D\uDD4B Flywheel Banks", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < flywheelLinks.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final StatsDisplayPanelBE.KineticFlywheelLink fwLink = flywheelLinks.get(i);
                    final boolean selected = fwLink.getId().equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4, this.getFlywheelStatusColor(fwLink));
                    g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(fwLink.getSourcePos()), listW - 50), contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    final String chargeLabel = String.format("%.0f%%", fwLink.getChargePercent());
                    g.drawString(this.font, chargeLabel, contentX + listW - this.font.width(chargeLabel) - 10, drawY + 3, selected ? this.ACCENT : this.DIM);
                    g.drawString(this.font, this.getFlywheelStatusLabel(fwLink), contentX + 12, drawY + 13, this.getFlywheelStatusColor(fwLink));
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_FLYWHEEL, contentX + 1, drawY, listW - 6, rowH - 1, fwLink.getId());
                }
                drawY += rowH;
            }
        }

        if (hasTrans) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\u2699 Auto Variable", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < transLinks.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final StatsDisplayPanelBE.AutoTransmissionClutchLink tLink = transLinks.get(i);
                    final AutoTransmissionClutchSettings tSettings = tLink.getSettings();
                    final boolean selected = tLink.getId().equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4, this.getTransmissionStatusColor(tSettings, tLink.getAlarmState(), tLink.isNetworkRunning(), tLink.getCurrentModifier()));
                    g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(tLink.getSourcePos()), listW - 50), contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    final String modLabel = String.format("%.0f%%", tLink.getCurrentModifier() * 100.0f);
                    g.drawString(this.font, modLabel, contentX + listW - this.font.width(modLabel) - 10, drawY + 3, selected ? this.ACCENT : this.DIM);
                    final String rowStatus = this.getTransmissionStatusLabel(tSettings, tLink.getAlarmState(), tLink.isNetworkRunning(), tLink.getCurrentModifier());
                    g.drawString(this.font, rowStatus, contentX + 12, drawY + 13, this.getTransmissionStatusColor(tSettings, tLink.getAlarmState(), tLink.isNetworkRunning(), tLink.getCurrentModifier()));
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_TRANSMISSION, contentX + 1, drawY, listW - 6, rowH - 1, tLink.getId());
                }
                drawY += rowH;
            }
        }

        if (hasBalancers) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\u21C4 Load Balancers", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < balancerLinks.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final StatsDisplayPanelBE.LoadBalancerLink bLink = balancerLinks.get(i);
                    final LoadBalancerSettings bSettings = bLink.getSettings();
                    final boolean selected = bLink.getId().equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    final boolean balancing = Math.abs(bLink.getCurrentModifierLeft() - 1.0f) > 0.01f || Math.abs(bLink.getCurrentModifierRight() - 1.0f) > 0.01f;
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4,
                            this.getLoadBalancerStatusColor(bSettings, bLink.getTopologyStatus(), bLink.isNetworkRunning(), balancing));
                    g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(bLink.getSourcePos()), listW - 56), contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    final String splitLabel = String.format("%.0f/%.0f", bSettings.getLeftSharePct(), bSettings.getRightSharePct());
                    g.drawString(this.font, splitLabel, contentX + listW - this.font.width(splitLabel) - 10, drawY + 3, selected ? this.ACCENT : this.DIM);
                    final String rowStatus = this.getLoadBalancerStatusLabel(bSettings, bLink.getTopologyStatus(), bLink.isNetworkRunning(), balancing);
                    g.drawString(this.font, rowStatus, contentX + 12, drawY + 13,
                            this.getLoadBalancerStatusColor(bSettings, bLink.getTopologyStatus(), bLink.isNetworkRunning(), balancing));
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_LOAD_BALANCER, contentX + 1, drawY, listW - 6, rowH - 1, bLink.getId());
                }
                drawY += rowH;
            }
        }

        if (hasMiniDisplays) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\u25EB Stats Links", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < miniDisplayLinks.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final StatsDisplayPanelBE.MiniDisplayLink miniLink = miniDisplayLinks.get(i);
                    final MiniDisplaySettings miniSettings = miniLink.getSettings();
                    final boolean selected = miniLink.getId().equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    final int statusColor = this.getMiniDisplayStatusColor(miniLink.getDisplayState());
                    final String badgeLabel = miniSettings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED ? "LINK" : "LOCAL";
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4, statusColor);
                    g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(miniLink.getSourcePos()), Math.max(48, listW - this.font.width(badgeLabel) - 24)),
                            contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    g.drawString(this.font, badgeLabel, contentX + listW - this.font.width(badgeLabel) - 10, drawY + 3, selected ? this.ACCENT : this.DIM);
                    g.drawString(this.font, this.truncate(this.getMiniDisplayListSummary(miniLink), listW - 22), contentX + 12, drawY + 13, statusColor);
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_MINI_DISPLAY, contentX + 1, drawY, listW - 6, rowH - 1, miniLink.getId());
                }
                drawY += rowH;
            }
        }

        if (hasFreight) {
            if (drawY >= listTop - rowH && drawY < listTop + listH) {
                g.drawString(this.font, "\uD83D\uDE82 Freight", contentX + 4, Math.max(listTop + 2, drawY + 2), this.ACCENT);
            }
            drawY += sectionHeaderH;

            for (int i = 0; i < freightEntries.size(); ++i) {
                if (drawY >= listTop + listH) break;
                if (drawY + rowH > listTop) {
                    final FactoryFreightEntry freightEntry = freightEntries.get(i);
                    final boolean selected = freightEntry.id.equals(this.factorySelectedClutchId);
                    final boolean hover = mouseX >= contentX + 1 && mouseX < contentX + listW - 5 && mouseY >= drawY && mouseY < drawY + rowH;
                    final int statusColor = this.getFactoryFreightStatusColor(freightEntry);
                    final IncomingCargoMatch incomingMatch = this.getIncomingCargoMatch(freightEntry);
                    final String badgeLabel = incomingMatch != IncomingCargoMatch.NONE
                        ? incomingMatch.label
                        : this.getFactoryFreightTopRightLabel(freightEntry);
                    final int badgeColor = incomingMatch != IncomingCargoMatch.NONE
                        ? this.getIncomingCargoMatchColor(incomingMatch)
                        : (selected ? this.ACCENT : this.DIM);
                    g.fill(contentX + 1, drawY, contentX + listW - 5, drawY + rowH - 1, selected ? -14270682 : (hover ? -15000717 : -15329770));
                    g.fill(contentX + 4, drawY + 4, contentX + 8, drawY + rowH - 4, statusColor);
                    g.drawString(this.font,
                        this.truncate(freightEntry.getDisplayName(), Math.max(48, listW - this.font.width(badgeLabel) - 26)),
                        contentX + 12, drawY + 3, selected ? this.TEXT : this.DIM);
                    g.drawString(this.font, badgeLabel, contentX + listW - this.font.width(badgeLabel) - 10, drawY + 3, badgeColor);
                    g.drawString(this.font, this.truncate(this.getFactoryFreightStatusText(freightEntry), listW - 22), contentX + 12, drawY + 13, statusColor);
                    this.addFactoryClickTarget(FactoryClickAction.SELECT_FREIGHT, contentX + 1, drawY, listW - 6, rowH - 1, freightEntry.id);
                }
                drawY += rowH;
            }
        }

        final int detailGap = 10;
        final int detailX = contentX + listW + detailGap;
        final int detailW = contentW - listW - detailGap;
        if (detailW < 80) {
            return;
        }
        final int detailTop = listTop;
        final int detailH = listH;
        g.fill(detailX, detailTop, detailX + detailW, detailTop + detailH, -15198184);
        this.drawGUIBorder(g, detailX, detailTop, detailW, detailH, -13417370);

        if (this.factorySelectedClutchId.startsWith("flywheel_")) {
            this.renderKineticFlywheelDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, flywheelLinks);
        } else if (this.factorySelectedClutchId.startsWith("trans_")) {
            this.renderTransmissionDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, transLinks);
        } else if (this.factorySelectedClutchId.startsWith("bal_")) {
            this.renderLoadBalancerDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, balancerLinks);
        } else if (this.factorySelectedClutchId.startsWith("mini_")) {
            this.renderMiniDisplayDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, miniDisplayLinks);
        } else if (this.factorySelectedClutchId.startsWith("freight:")) {
            this.renderFactoryFreightDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, freightEntries);
        } else {
            this.renderSafetyClutchDetail(g, mouseX, mouseY, detailX, detailTop, detailW, detailH, safetyLinks);
        }
    }

    private void renderSafetyClutchDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH, List<StatsDisplayPanelBE.SafetyClutchLink> links) {
        StatsDisplayPanelBE.SafetyClutchLink selectedLink = this.panel.getSafetyClutchLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (links.isEmpty()) return;
            selectedLink = links.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final SafetyClutchBE liveClutch = this.getLoadedSafetyClutch(selectedLink.getSourcePos());
        final SafetyClutchSettings settings = (liveClutch != null) ? liveClutch.getSettings() : selectedLink.getSettings();
        final boolean tripped = (liveClutch != null) ? liveClutch.isTripped() : selectedLink.isTripped();
        final boolean networkRunning = (liveClutch != null) ? liveClutch.isNetworkRunning() : selectedLink.isNetworkRunning();
        final float stressPercent = (liveClutch != null) ? liveClutch.getLastStressPercent() : selectedLink.getStressPercent();
        final float stressSU = (liveClutch != null) ? liveClutch.getLastStressSU() : selectedLink.getStressSU();
        final float capacitySU = (liveClutch != null) ? liveClutch.getLastCapacitySU() : selectedLink.getCapacitySU();
        final float autoResetRetrySeconds = (liveClutch != null) ? liveClutch.getAutoResetRetrySeconds() : 5.0f;

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;

        g.drawString(this.font, "Safety Clutch", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 18, this.TEXT);

        final String statusText = this.getSafetyClutchStatusLabel(settings, tripped, networkRunning);
        final int statusColor = this.getSafetyClutchStatusColor(settings, tripped, networkRunning);
        g.drawString(this.font, "Status: " + statusText, dInnerX, detailTop + 34, statusColor);

        final int barY = detailTop + 48;
        final int barH = 8;
        g.fill(dInnerX, barY, dInnerX + dInnerW, barY + barH, -15329770);
        this.drawGUIBorder(g, dInnerX, barY, dInnerW, barH, -13417370);
        final int fillW = (int)(Math.min(1.0f, stressPercent / 100.0f) * (dInnerW - 2));
        final int barColor = stressPercent >= settings.getThreshold() ? this.WARN : this.ACCENT;
        if (fillW > 0) {
            g.fill(dInnerX + 1, barY + 1, dInnerX + 1 + fillW, barY + barH - 1, barColor);
        }
        final int threshX = dInnerX + 1 + (int)(settings.getThreshold() / 100.0f * (dInnerW - 2));
        g.fill(threshX, barY, threshX + 1, barY + barH, this.WARN);
        g.drawString(this.font, String.format("Live: %.1f%% / %.0f%% trip", stressPercent, settings.getThreshold()), dInnerX, barY + barH + 3, this.DIM);
        g.drawString(this.font,
                settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET
                        ? String.format("Retry: %.1fs", autoResetRetrySeconds)
                        : "Retry: Manual",
                dInnerX + dInnerW / 2, barY + barH + 3, this.DIM);

        g.drawString(this.font, this.truncate(String.format("Load: %.0f / %.0f SU", stressSU, capacitySU), dInnerW), dInnerX, barY + barH + 16, this.DIM);
        g.drawString(this.font, this.truncate(String.format("Trips when load crosses the threshold%s",
                settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET
                        ? String.format(", then retries a reconnect every %.1fs", autoResetRetrySeconds)
                        : " and stays open until manually reset"), dInnerW), dInnerX, barY + barH + 28, this.DIM);
        g.drawString(this.font, this.truncate(String.format("Mode: %s  |  Network: %s",
                settings.getMode().getDisplayName(), networkRunning ? "Live" : "Idle"), dInnerW), dInnerX, barY + barH + 40, this.DIM);

        int btnY = barY + barH + 58;
        final int btnW = Math.min(dInnerW, 200);
        final int btnX = dInnerX + (dInnerW - btnW) / 2;
        this.renderFactoryButton(g, btnX, btnY, btnW, 16, "Mode: " + settings.getMode().getDisplayName(), true, false, mouseX, mouseY, FactoryClickAction.CYCLE_MODE, selectedLink.getId());
        btnY += 20;
        final int halfW = (btnW - 4) / 2;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, settings.isEnabled() ? "Enabled" : "Disabled", true, settings.isEnabled(), mouseX, mouseY, FactoryClickAction.TOGGLE_ENABLED, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, "Open Settings", liveClutch != null, false, mouseX, mouseY, FactoryClickAction.OPEN_CONTROLS, selectedLink.getId());
        btnY += 20;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, "Reset", tripped, false, mouseX, mouseY, FactoryClickAction.MANUAL_RESET, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, "Trip", settings.isEnabled() && !tripped, false, mouseX, mouseY, FactoryClickAction.MANUAL_TRIP, selectedLink.getId());
    }

    private int getFlywheelStatusColor(StatsDisplayPanelBE.KineticFlywheelLink link) {
        if (link.isHalted()) {
            return this.WARN;
        }
        if (link.isParked()) {
            return -11163905;
        }
        if (link.isDepleted()) {
            return -22221;
        }
        if (link.getModeIndex() == 2) {
            return -11215531;
        }
        if (link.getModeIndex() == 1) {
            return -11163905;
        }
        if (!link.getSettings().isDischargeArmed()) {
            return this.DIM;
        }
        if (link.isLowReserve()) {
            return -22221;
        }
        return this.DIM;
    }

    private String getFlywheelStatusLabel(StatsDisplayPanelBE.KineticFlywheelLink link) {
        if (link.isHalted()) {
            return "Halted";
        }
        if (link.isParked()) {
            return "Parked";
        }
        if (link.isDepleted()) {
            return "Spent";
        }
        if (link.getModeIndex() == 2) {
            return "Discharging";
        }
        if (link.getModeIndex() == 1) {
            return "Charging";
        }
        if (!link.getSettings().isDischargeArmed()) {
            return "Idle (disarmed)";
        }
        if (link.isLowReserve()) {
            return "Low reserve";
        }
        return "Idle";
    }

    private void renderKineticFlywheelDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH,
                                             List<StatsDisplayPanelBE.KineticFlywheelLink> flywheelLinks) {
        StatsDisplayPanelBE.KineticFlywheelLink selectedLink = this.panel.getKineticFlywheelLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (flywheelLinks.isEmpty()) {
                return;
            }
            selectedLink = flywheelLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final KineticFlywheelBE liveFlywheel = this.getLoadedKineticFlywheel(selectedLink.getSourcePos());
        final KineticFlywheelSettings settings = (liveFlywheel != null) ? liveFlywheel.getSettings() : selectedLink.getSettings();
        final float chargePercent = (liveFlywheel != null) ? liveFlywheel.getChargePercent() : selectedLink.getChargePercent();
        final float storedRpm = (liveFlywheel != null) ? liveFlywheel.getStoredRpm() : selectedLink.getStoredRpm();
        final float outputSU = (liveFlywheel != null) ? liveFlywheel.getOutputStress() : selectedLink.getOutputSU();
        final float sustainSeconds = (liveFlywheel != null) ? liveFlywheel.getEstimatedSustainSeconds() : selectedLink.getSustainSeconds();
        final int tier = (liveFlywheel != null) ? liveFlywheel.getTier() : selectedLink.getTier();
        final int flywheelCount = (liveFlywheel != null) ? liveFlywheel.getFlywheelCount() : selectedLink.getFlywheelCount();
        final boolean halted = (liveFlywheel != null) ? liveFlywheel.isOverstressHalted() : selectedLink.isHalted();

        final boolean held = halted || ((liveFlywheel != null) ? liveFlywheel.isParked() : selectedLink.isParked());
        final float reArmSeconds = (liveFlywheel != null) ? liveFlywheel.getReArmSeconds() : 0.0f;

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;

        g.drawString(this.font, "Kinetic Flywheel", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 18, this.TEXT);

        final String statusText = this.getFlywheelStatusLabel(selectedLink);
        final int statusColor = this.getFlywheelStatusColor(selectedLink);
        g.drawString(this.font, "Status: " + statusText, dInnerX, detailTop + 34, statusColor);

        final int barY = detailTop + 48;
        final int barH = 8;
        g.fill(dInnerX, barY, dInnerX + dInnerW, barY + barH, -15329770);
        this.drawGUIBorder(g, dInnerX, barY, dInnerW, barH, -13417370);
        final int fillW = (int)(Math.min(1.0f, chargePercent / 100.0f) * (dInnerW - 2));
        final boolean lowReserve = chargePercent < settings.getLowReservePct();
        final int barColor = lowReserve ? -22221 : -11215531;
        if (fillW > 0) {
            g.fill(dInnerX + 1, barY + 1, dInnerX + 1 + fillW, barY + barH - 1, barColor);
        }
        final int reserveX = dInnerX + 1 + (int)(settings.getLowReservePct() / 100.0f * (dInnerW - 2));
        g.fill(reserveX, barY, reserveX + 1, barY + barH, -22221);
        g.drawString(this.font, String.format("Charge: %.0f%% / low %.0f%%", chargePercent, settings.getLowReservePct()), dInnerX, barY + barH + 3, this.DIM);

        g.drawString(this.font, this.truncate(String.format("Stored: %.0f RPM  |  Output: %.0f SU", storedRpm, outputSU), dInnerW), dInnerX, barY + barH + 16, this.DIM);
        final String sustainText = sustainSeconds >= 999.0f ? "\u221E" : String.format("%.0fs", sustainSeconds);
        g.drawString(this.font, this.truncate(String.format("Sustain: %s  |  Tier %d (%d flywheels)", sustainText, tier + 1, flywheelCount), dInnerW), dInnerX, barY + barH + 28, this.DIM);
        final boolean parked = (liveFlywheel != null) ? liveFlywheel.isParked() : selectedLink.isParked();
        final boolean spent = (liveFlywheel != null) ? liveFlywheel.isDepleted() : selectedLink.isDepleted();
        g.drawString(this.font, this.truncate(halted
                ? (reArmSeconds > 0.0f
                        ? String.format("Overstressed - RE-ARM in %.1fs (-%.0f%%)", reArmSeconds, KineticFlywheelBE.getReArmCostPercent())
                        : "Network overstressed - holding reserve until it recovers")
                : parked
                ? "Parked by hand - neither charging nor discharging until released"
                : spent
                ? String.format("Spent - recharges to %.0f%% before it will carry the network again", KineticFlywheelBE.getRecoveryPercent())
                : (settings.isDischargeArmed() ? "Armed: backs up the network when it goes idle" : "Disarmed: charges and holds, never discharges"), dInnerW),
                dInnerX, barY + barH + 40, this.DIM);

        int btnY = barY + barH + 58;
        final int btnW = Math.min(dInnerW, 200);
        final int btnX = dInnerX + (dInnerW - btnW) / 2;
        this.renderFactoryButton(g, btnX, btnY, btnW, 16,
                settings.isDischargeArmed() ? "Discharge: Armed" : "Discharge: Disarmed",
                true, settings.isDischargeArmed(), mouseX, mouseY, FactoryClickAction.TOGGLE_FLYWHEEL_ARMED, selectedLink.getId());
        btnY += 20;
        final int halfW = (btnW - 4) / 2;
        if (held) {
            this.renderFactoryButton(g, btnX, btnY, halfW, 16, "Release", true, false, mouseX, mouseY, FactoryClickAction.FLYWHEEL_RELEASE, selectedLink.getId());
        } else {
            this.renderFactoryButton(g, btnX, btnY, halfW, 16, "Force Park", true, false, mouseX, mouseY, FactoryClickAction.FLYWHEEL_FORCE_PARK, selectedLink.getId());
        }
        g.drawString(this.font, String.format("Low reserve: %.0f%%", settings.getLowReservePct()), btnX + halfW + 8, btnY + 4, this.DIM);
        btnY += 20;
        final int quarterW = (btnW - 4) / 2;
        this.renderFactoryButton(g, btnX, btnY, quarterW, 16, "Low \u2013 5%", settings.getLowReservePct() > 0.0f, false, mouseX, mouseY, FactoryClickAction.FLYWHEEL_LOW_RESERVE_DOWN, selectedLink.getId());
        this.renderFactoryButton(g, btnX + quarterW + 4, btnY, quarterW, 16, "Low + 5%", settings.getLowReservePct() < 90.0f, false, mouseX, mouseY, FactoryClickAction.FLYWHEEL_LOW_RESERVE_UP, selectedLink.getId());
    }

    private void renderTransmissionDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH, List<StatsDisplayPanelBE.AutoTransmissionClutchLink> transLinks) {
        StatsDisplayPanelBE.AutoTransmissionClutchLink selectedLink = this.panel.getAutoTransmissionClutchLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (transLinks.isEmpty()) return;
            selectedLink = transLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final AutoTransmissionClutchBE liveTrans = this.getLoadedAutoTransmission(selectedLink.getSourcePos());
        final AutoTransmissionClutchSettings settings = (liveTrans != null) ? liveTrans.getSettings() : selectedLink.getSettings();
        final AutoTransmissionClutchBE.AlarmState alarm = (liveTrans != null) ? liveTrans.getAlarmState() : selectedLink.getAlarmState();
        final boolean networkRunning = (liveTrans != null) ? liveTrans.isNetworkRunning() : selectedLink.isNetworkRunning();
        final float currentMod = (liveTrans != null) ? liveTrans.getCurrentModifier() : selectedLink.getCurrentModifier();
        final float targetMod = (liveTrans != null) ? liveTrans.getTargetModifier() : selectedLink.getTargetModifier();
        final float transmittedSpeed = (liveTrans != null) ? liveTrans.getTransmittedSpeed() : selectedLink.getTransmittedSpeed();
        final float stressPercent = (liveTrans != null) ? liveTrans.getLastStressPercent() : selectedLink.getStressPercent();
        final float stressSU = (liveTrans != null) ? liveTrans.getLastStressSU() : selectedLink.getStressSU();
        final float capacitySU = (liveTrans != null) ? liveTrans.getLastCapacitySU() : selectedLink.getCapacitySU();

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;

        g.drawString(this.font, "Auto Variable Clutch", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 18, this.TEXT);

        final String statusText = this.getTransmissionStatusLabel(settings, alarm, networkRunning, currentMod);
        final int statusColor = this.getTransmissionStatusColor(settings, alarm, networkRunning, currentMod);
        g.drawString(this.font, "Status: " + statusText, dInnerX, detailTop + 34, statusColor);

        final int barY = detailTop + 48;
        final int barH = 8;
        final float ratioScale = Math.max(1.0f, Math.max(currentMod, targetMod));
        g.fill(dInnerX, barY, dInnerX + dInnerW, barY + barH, -15329770);
        this.drawGUIBorder(g, dInnerX, barY, dInnerW, barH, -13417370);
        final int fillW = (int)(Math.min(1.0f, currentMod / ratioScale) * (dInnerW - 2));
        final int modBarColor = alarm == AutoTransmissionClutchBE.AlarmState.VERY_CONCERNING ? this.WARN :
                (alarm == AutoTransmissionClutchBE.AlarmState.CONCERNING ? -256 : this.ACCENT);
        if (fillW > 0) {
            g.fill(dInnerX + 1, barY + 1, dInnerX + 1 + fillW, barY + barH - 1, modBarColor);
        }

        final int targetX = dInnerX + 1 + (int)(Math.min(1.0f, targetMod / ratioScale) * (dInnerW - 2));
        g.fill(targetX, barY, targetX + 1, barY + barH, this.TEXT);

        g.drawString(this.font, String.format("Modifier: %.0f%%", currentMod * 100.0f), dInnerX, barY + barH + 3, this.DIM);
        g.drawString(this.font, String.format("Target: %.0f%%", targetMod * 100.0f), dInnerX + dInnerW / 2, barY + barH + 3, this.DIM);

        final float sourceSpeed = (liveTrans != null) ? liveTrans.getSourceSpeed() : (currentMod != 0.0f ? transmittedSpeed / currentMod : 0.0f);
        g.drawString(this.font, this.truncate(String.format("Source: %.0f RPM  Output: %.0f RPM", sourceSpeed, transmittedSpeed), dInnerW), dInnerX, barY + barH + 16, this.DIM);
        g.drawString(this.font, this.truncate(String.format("Stress: %.1f%%  (%.0f / %.0f SU)", stressPercent, stressSU, capacitySU), dInnerW), dInnerX, barY + barH + 28, this.DIM);

        final String rangeDesc;
        if (settings.getLimitMode() == AutoTransmissionClutchSettings.LimitMode.SU) {
            rangeDesc = String.format("Range: %.0f - %.0f SU", settings.getLowerBound(), settings.getUpperBound());
        } else {
            rangeDesc = String.format("Range: %.0f - %.0f%%", settings.getLowerBound(), settings.getUpperBound());
        }
        g.drawString(this.font, this.truncate(rangeDesc + "  |  Min RPM: " + String.format("%.0f", settings.getMinimumRPM()) + "  |  Max RPM: " + String.format("%.0f", settings.getMaximumRPM()), dInnerW), dInnerX, barY + barH + 40, this.DIM);

        int btnY = barY + barH + 58;
        final int btnW = Math.min(dInnerW, 200);
        final int btnX = dInnerX + (dInnerW - btnW) / 2;
        this.renderFactoryButton(g, btnX, btnY, btnW, 16, "Range: " + settings.getLimitMode().getDisplayName(), true, false, mouseX, mouseY, FactoryClickAction.CYCLE_TRANSMISSION_LIMIT_MODE, selectedLink.getId());
        btnY += 20;
        final int halfW = (btnW - 4) / 2;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, settings.isEnabled() ? "Enabled" : "Disabled", true, settings.isEnabled(), mouseX, mouseY, FactoryClickAction.TOGGLE_TRANSMISSION_ENABLED, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, "Open Controls", liveTrans != null, false, mouseX, mouseY, FactoryClickAction.OPEN_TRANSMISSION_CONTROLS, selectedLink.getId());
    }

    private void renderLoadBalancerDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH,
                                          List<StatsDisplayPanelBE.LoadBalancerLink> balancerLinks) {
        StatsDisplayPanelBE.LoadBalancerLink selectedLink = this.panel.getLoadBalancerLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (balancerLinks.isEmpty()) {
                return;
            }
            selectedLink = balancerLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final LoadBalancerBE liveBalancer = this.getLoadedLoadBalancer(selectedLink.getSourcePos());
        final LoadBalancerSettings settings = (liveBalancer != null) ? liveBalancer.getSettings() : selectedLink.getSettings();
        final LoadBalancerBE.TopologyStatus topologyStatus = (liveBalancer != null) ? liveBalancer.getTopologyStatus() : selectedLink.getTopologyStatus();
        final boolean networkRunning = (liveBalancer != null) ? liveBalancer.isNetworkRunning() : selectedLink.isNetworkRunning();
        final float currentLeft = (liveBalancer != null) ? liveBalancer.getCurrentModifierLeft() : selectedLink.getCurrentModifierLeft();
        final float currentRight = (liveBalancer != null) ? liveBalancer.getCurrentModifierRight() : selectedLink.getCurrentModifierRight();
        final float targetLeft = (liveBalancer != null) ? liveBalancer.getTargetModifierLeft() : selectedLink.getTargetModifierLeft();
        final float targetRight = (liveBalancer != null) ? liveBalancer.getTargetModifierRight() : selectedLink.getTargetModifierRight();
        final float transmittedLeft = (liveBalancer != null) ? liveBalancer.getTransmittedSpeedLeft() : selectedLink.getTransmittedSpeedLeft();
        final float transmittedRight = (liveBalancer != null) ? liveBalancer.getTransmittedSpeedRight() : selectedLink.getTransmittedSpeedRight();
        final float stressPercent = (liveBalancer != null) ? liveBalancer.getLastStressPercent() : selectedLink.getStressPercent();
        final float stressSU = (liveBalancer != null) ? liveBalancer.getLastStressSU() : selectedLink.getStressSU();
        final float capacitySU = (liveBalancer != null) ? liveBalancer.getLastCapacitySU() : selectedLink.getCapacitySU();
        final float leftStressSU = (liveBalancer != null) ? liveBalancer.getLastLeftStressSU() : selectedLink.getLeftStressSU();
        final float rightStressSU = (liveBalancer != null) ? liveBalancer.getLastRightStressSU() : selectedLink.getRightStressSU();
        final float baseStressSU = (liveBalancer != null) ? liveBalancer.getLastBaseStressSU() : selectedLink.getBaseStressSU();
        final float leftBudgetSU = (liveBalancer != null) ? liveBalancer.getLastLeftBudgetSU() : selectedLink.getLeftBudgetSU();
        final float rightBudgetSU = (liveBalancer != null) ? liveBalancer.getLastRightBudgetSU() : selectedLink.getRightBudgetSU();
        final boolean leftAttached = (liveBalancer != null) ? liveBalancer.isLeftBranchAttached() : selectedLink.isLeftBranchAttached();
        final boolean rightAttached = (liveBalancer != null) ? liveBalancer.isRightBranchAttached() : selectedLink.isRightBranchAttached();
        final boolean balancing = (liveBalancer != null)
                ? liveBalancer.isBalancing()
                : settings.isEnabled() && topologyStatus == LoadBalancerBE.TopologyStatus.OK
                && (Math.abs(currentLeft - 1.0f) > 0.01f || Math.abs(currentRight - 1.0f) > 0.01f);
        final float sourceSpeed = (liveBalancer != null)
                ? liveBalancer.getSourceSpeed()
                : this.estimateLoadBalancerSourceSpeed(currentLeft, transmittedLeft, currentRight, transmittedRight);

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;

        g.drawString(this.font, "Load Balancer", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 18, this.TEXT);

        final String statusText = this.getLoadBalancerStatusLabel(settings, topologyStatus, networkRunning, balancing);
        final int statusColor = this.getLoadBalancerStatusColor(settings, topologyStatus, networkRunning, balancing);
        g.drawString(this.font, "Status: " + statusText, dInnerX, detailTop + 34, statusColor);

        final String runtimeLabel = this.getLoadBalancerRuntimeLabel(settings, topologyStatus, capacitySU, baseStressSU,
                leftStressSU, rightStressSU, leftBudgetSU, rightBudgetSU);
        final float totalLowSU = this.getLoadBalancerBoundSU(settings.getLowerBound(), settings.getLimitMode(), capacitySU);
        final float totalHighSU = this.getLoadBalancerBoundSU(settings.getUpperBound(), settings.getLimitMode(), capacitySU);
        final float totalUsedSU = baseStressSU + leftStressSU + rightStressSU;
        final float bandSpareSU = Math.max(0.0f, totalHighSU - totalUsedSU);
        final float leftHeadroomSU = Math.max(0.0f, leftBudgetSU - leftStressSU);
        final float rightHeadroomSU = Math.max(0.0f, rightBudgetSU - rightStressSU);
        final String bandText = settings.getLimitMode() == LoadBalancerSettings.LimitMode.SU
                ? String.format("Band %.0f - %.0f SU", settings.getLowerBound(), settings.getUpperBound())
                : String.format("Band %.0f - %.0f%%", settings.getLowerBound(), settings.getUpperBound());
        final String splitLine = String.format("Split: %.0f / %.0f | %s | %s",
                settings.getLeftSharePct(), settings.getRightSharePct(), settings.getSharingMode().getDisplayName(), runtimeLabel);
        g.drawString(this.font, this.truncate(splitLine, dInnerW), dInnerX, detailTop + 46, this.DIM);

        final int btnW = Math.min(dInnerW, 220);
        final int btnX = dInnerX + (dInnerW - btnW) / 2;
        final int buttonsTop = detailTop + detailH - 64;

        final int cardX = dInnerX;
        final int cardY = detailTop + 60;
        final int cardW = dInnerW;
        final int cardH = Math.min(104, Math.max(92, buttonsTop - cardY - 52));
        this.drawFactoryPanelBox(g, cardX, cardY, cardW, cardH, -15329770, -13417370);
        g.drawString(this.font, this.truncate(bandText + " | Total " + String.format("%.1f%%", stressPercent), cardW - 12), cardX + 6, cardY + 6, this.DIM);
        g.drawString(this.font, this.truncate(String.format("Load %.0f/%.0f SU | Base %.0f | Band spare %.0f", stressSU, capacitySU, baseStressSU, bandSpareSU), cardW - 12), cardX + 6, cardY + 18, this.DIM);

        final int nodeH = 26;
        final int hubW = Math.min(84, Math.max(68, cardW / 3));
        final int branchW = Math.min(92, Math.max(60, (cardW - hubW) / 2 - 12));
        final int sourceW = Math.min(104, Math.max(78, cardW - 44));
        final int sourceX = cardX + (cardW - sourceW) / 2;
        final int sourceY = cardY + 30;
        final int hubX = cardX + (cardW - hubW) / 2;
        final int hubY = cardY + 62;
        final int leftX = cardX + 6;
        final int rightX = cardX + cardW - 6 - branchW;
        final int branchY = hubY;
        final int sourceCenterX = sourceX + sourceW / 2;
        final int connectorY = hubY + nodeH / 2;
        final int leftColor = this.getBranchLoadColor(leftAttached, leftStressSU, leftBudgetSU);
        final int rightColor = this.getBranchLoadColor(rightAttached, rightStressSU, rightBudgetSU);

        g.fill(sourceCenterX, sourceY + nodeH, sourceCenterX + 1, hubY, statusColor);
        g.fill(leftX + branchW, connectorY, hubX, connectorY + 1, leftColor);
        g.fill(hubX + hubW, connectorY, rightX, connectorY + 1, rightColor);
        g.fill(sourceCenterX - 1, hubY - 1, sourceCenterX + 2, hubY + 2, statusColor);

        g.drawCenteredString(this.font, this.truncate(String.format("x%.2f -> x%.2f", currentLeft, targetLeft), Math.max(36, hubX - (leftX + branchW) - 6)),
                (leftX + branchW + hubX) / 2, connectorY - 10, leftColor);
        g.drawCenteredString(this.font, this.truncate(String.format("x%.2f -> x%.2f", currentRight, targetRight), Math.max(36, rightX - (hubX + hubW) - 6)),
                (hubX + hubW + rightX) / 2, connectorY - 10, rightColor);

        this.drawLoadBalancerPanelNode(g, sourceX, sourceY, sourceW, nodeH, this.ACCENT,
                "SOURCE", String.format("%.0f RPM in", sourceSpeed), String.format("Cap %.0f SU", capacitySU));
        this.drawLoadBalancerPanelNode(g, hubX, hubY, hubW, nodeH, statusColor,
                "SPLIT", String.format("%.0f / %.0f", settings.getLeftSharePct(), settings.getRightSharePct()), runtimeLabel);
        this.drawLoadBalancerPanelNode(g, leftX, branchY, branchW, nodeH, leftColor,
                "LEFT", leftAttached ? String.format("%.0f RPM", transmittedLeft) : "No branch",
                leftAttached ? String.format("%.0f / %.0f SU", leftStressSU, leftBudgetSU) : "Attach load");
        this.drawLoadBalancerPanelNode(g, rightX, branchY, branchW, nodeH, rightColor,
                "RIGHT", rightAttached ? String.format("%.0f RPM", transmittedRight) : "No branch",
                rightAttached ? String.format("%.0f / %.0f SU", rightStressSU, rightBudgetSU) : "Attach load");

        final int barY = cardY + cardH + 8;
        final int barH = 8;
        final float loadFraction = Math.min(1.0f, Math.max(0.0f, stressPercent / 100.0f));
        final float lowerFraction = this.getLoadBalancerBandFraction(settings.getLowerBound(), settings.getLimitMode(), capacitySU);
        final float upperFraction = this.getLoadBalancerBandFraction(settings.getUpperBound(), settings.getLimitMode(), capacitySU);
        g.fill(dInnerX, barY, dInnerX + dInnerW, barY + barH, -15329770);
        this.drawGUIBorder(g, dInnerX, barY, dInnerW, barH, -13417370);
        final int fillW = (int)(loadFraction * (dInnerW - 2));
        final int barColor = !settings.isEnabled() ? this.DIM
                : (topologyStatus == LoadBalancerBE.TopologyStatus.OK && stressFractionExceedsUpper(loadFraction, upperFraction) ? this.WARN : statusColor);
        if (fillW > 0) {
            g.fill(dInnerX + 1, barY + 1, dInnerX + 1 + fillW, barY + barH - 1, barColor);
        }
        final int lowerX = dInnerX + 1 + (int)(lowerFraction * (dInnerW - 2));
        final int upperX = dInnerX + 1 + (int)(upperFraction * (dInnerW - 2));
        g.fill(lowerX, barY, lowerX + 1, barY + barH, this.ACCENT);
        g.fill(upperX, barY, upperX + 1, barY + barH, this.WARN);

        final String metricsLine = String.format("Input %.0f RPM | Capacity %.0f SU | Max %.0f RPM", sourceSpeed, capacitySU, settings.getMaximumRPM());
        final String headroomLine = String.format("Headroom L %.0f SU | R %.0f SU | Window %.0f - %.0f SU",
                leftHeadroomSU, rightHeadroomSU, totalLowSU, totalHighSU);
        g.drawString(this.font, this.truncate(metricsLine, dInnerW), dInnerX, barY + barH + 3, this.DIM);
        g.drawString(this.font, this.truncate(headroomLine, dInnerW), dInnerX, barY + barH + 15, this.DIM);
        final String hintText = this.getLoadBalancerHint(settings, topologyStatus, capacitySU, baseStressSU,
                leftStressSU, rightStressSU, leftBudgetSU, rightBudgetSU);
        g.drawString(this.font, this.truncate(hintText, dInnerW), dInnerX, barY + barH + 27,
                topologyStatus == LoadBalancerBE.TopologyStatus.OK ? this.ACCENT : statusColor);

        int btnY = buttonsTop;
        this.renderFactoryButton(g, btnX, btnY, btnW, 16, "Range: " + settings.getLimitMode().getDisplayName(),
                true, false, mouseX, mouseY, FactoryClickAction.CYCLE_LOAD_BALANCER_LIMIT_MODE, selectedLink.getId());
        btnY += 20;
        final int halfW = (btnW - 4) / 2;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, settings.isEnabled() ? "Enabled" : "Disabled",
                true, settings.isEnabled(), mouseX, mouseY, FactoryClickAction.TOGGLE_LOAD_BALANCER_ENABLED, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, "Sharing: " + settings.getSharingMode().getDisplayName(),
                true, settings.getSharingMode() == LoadBalancerSettings.SharingMode.ELASTIC, mouseX, mouseY,
                FactoryClickAction.CYCLE_LOAD_BALANCER_SHARING_MODE, selectedLink.getId());
        btnY += 20;
        this.renderFactoryButton(g, btnX, btnY, btnW, 16, "Open Controls", liveBalancer != null,
                false, mouseX, mouseY, FactoryClickAction.OPEN_LOAD_BALANCER_CONTROLS, selectedLink.getId());
    }

    private void renderMiniDisplayDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH,
                                         List<StatsDisplayPanelBE.MiniDisplayLink> miniDisplayLinks) {
        StatsDisplayPanelBE.MiniDisplayLink selectedLink = this.panel.getMiniDisplayLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (miniDisplayLinks.isEmpty()) {
                return;
            }
            selectedLink = miniDisplayLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final MiniDisplayBE liveMiniDisplay = this.getLoadedMiniDisplay(selectedLink.getSourcePos());
        final MiniDisplaySettings settings = (liveMiniDisplay != null) ? liveMiniDisplay.getSettings() : selectedLink.getSettings();
        final MiniDisplayBE.DisplayState displayState = (liveMiniDisplay != null) ? liveMiniDisplay.getDisplayState() : selectedLink.getDisplayState();
        final InstrumentSourceHelper.SourceKind sourceKind = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveSourceKind() : selectedLink.getSourceKind();
        final String sourceType = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveSourceType() : selectedLink.getSourceType();
        final float fillPercent = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveFillPercent() : selectedLink.getFillPercent();
        final float totalItems = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveTotalItems() : selectedLink.getTotalItems();
        final float fluidAmount = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveFluidAmount() : selectedLink.getFluidAmount();
        final float fluidCapacity = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveFluidCapacity() : selectedLink.getFluidCapacity();
        final float stressPercent = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveStressPercent() : selectedLink.getStressPercent();
        final float stressSU = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveStressUsed() : selectedLink.getStressSU();
        final float capacitySU = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveStressCapacity() : selectedLink.getCapacitySU();
        final float rpm = (liveMiniDisplay != null) ? liveMiniDisplay.getActiveRpm() : selectedLink.getRpm();
        final boolean firstDetailPresent = (liveMiniDisplay != null) ? liveMiniDisplay.hasFirstDetailRow() : selectedLink.hasFirstDetailRow();
        final String firstDetailLabel = (liveMiniDisplay != null) ? liveMiniDisplay.getFirstDetailLabel() : selectedLink.getFirstDetailLabel();
        final float firstDetailValue = (liveMiniDisplay != null) ? liveMiniDisplay.getFirstDetailValue() : selectedLink.getFirstDetailValue();
        final boolean secondDetailPresent = (liveMiniDisplay != null) ? liveMiniDisplay.hasSecondDetailRow() : selectedLink.hasSecondDetailRow();
        final String secondDetailLabel = (liveMiniDisplay != null) ? liveMiniDisplay.getSecondDetailLabel() : selectedLink.getSecondDetailLabel();
        final float secondDetailValue = (liveMiniDisplay != null) ? liveMiniDisplay.getSecondDetailValue() : selectedLink.getSecondDetailValue();
        final int displayWidth = (liveMiniDisplay != null) ? liveMiniDisplay.getDisplayWidth() : selectedLink.getDisplayWidth();

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;
        final int statusColor = this.getMiniDisplayStatusColor(displayState);

        g.drawString(this.font, "Stats Link", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 18, this.TEXT);
        g.drawString(this.font, this.truncate("Status: " + this.getMiniDisplayStatusLabel(displayState, settings), dInnerW), dInnerX, detailTop + 34, statusColor);
        g.drawString(this.font, this.truncate("Source: " + this.getMiniDisplaySourceLabel(displayState, sourceKind, sourceType), dInnerW), dInnerX, detailTop + 46, this.DIM);
        g.drawString(this.font, this.truncate("Mode: " + settings.getSourceMode().getDisplayName()
                        + "  |  Footprint: " + this.getMiniDisplayFootprintLabel(displayWidth)
                        + "  |  Accent: " + settings.getAccentColor().getDisplayName(), dInnerW),
                dInnerX, detailTop + 58, this.DIM);

        final int buttonsTop = detailTop + detailH - 44;
        final int cardY = detailTop + 74;
        final int cardH = Math.max(52, buttonsTop - cardY - 10);
        this.drawFactoryPanelBox(g, dInnerX, cardY, dInnerW, cardH, -15329770, -13417370);

        final List<String> detailLines = this.buildMiniDisplayDetailLines(displayState, settings, sourceKind, sourceType,
                fillPercent, totalItems, fluidAmount, fluidCapacity, stressPercent, stressSU, capacitySU, rpm,
                firstDetailPresent, firstDetailLabel, firstDetailValue, secondDetailPresent, secondDetailLabel, secondDetailValue);
        final int maxLines = Math.max(3, (cardH - 12) / 12);
        for (int i = 0; i < detailLines.size() && i < maxLines; ++i) {
            g.drawString(this.font, this.truncate(detailLines.get(i), dInnerW - 12), dInnerX + 6, cardY + 6 + i * 12, i == 0 ? this.TEXT : this.DIM);
        }

        final int btnW = Math.min(dInnerW, 220);
        final int btnX = dInnerX + (dInnerW - btnW) / 2;
        final int halfW = (btnW - 4) / 2;
        int btnY = buttonsTop;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, "Mode: " + settings.getSourceMode().getDisplayName(),
                true, settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED, mouseX, mouseY,
                FactoryClickAction.CYCLE_MINI_DISPLAY_SOURCE_MODE, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, "Open Controls", liveMiniDisplay != null,
                false, mouseX, mouseY, FactoryClickAction.OPEN_MINI_DISPLAY_CONTROLS, selectedLink.getId());
        btnY += 20;
        this.renderFactoryButton(g, btnX, btnY, halfW, 16, settings.isLit() ? "Face: Lit" : "Face: Dim",
                true, settings.isLit(), mouseX, mouseY, FactoryClickAction.TOGGLE_MINI_DISPLAY_LIT, selectedLink.getId());
        this.renderFactoryButton(g, btnX + halfW + 4, btnY, halfW, 16, settings.isShowLabels() ? "Labels: On" : "Labels: Off",
                true, settings.isShowLabels(), mouseX, mouseY, FactoryClickAction.TOGGLE_MINI_DISPLAY_LABELS, selectedLink.getId());
    }

    private void drawFactoryPanelBox(GuiGraphics g, int x, int y, int w, int h, int fillColor, int borderColor) {
        g.fill(x, y, x + w, y + h, fillColor);
        this.drawGUIBorder(g, x, y, w, h, borderColor);
    }

    private void drawLoadBalancerPanelNode(GuiGraphics g, int x, int y, int w, int h, int accentColor,
                                           String title, String line1, String line2) {
        this.drawFactoryPanelBox(g, x, y, w, h, -15198184, accentColor);
        g.drawCenteredString(this.font, this.truncate(title, Math.max(10, w - 8)), x + w / 2, y + 2, accentColor);
        g.drawCenteredString(this.font, this.truncate(line1, Math.max(12, w - 8)), x + w / 2, y + 11, this.TEXT);
        g.drawCenteredString(this.font, this.truncate(line2, Math.max(12, w - 8)), x + w / 2, y + 20, this.DIM);
    }

    private String getMiniDisplayStatusLabel(MiniDisplayBE.DisplayState displayState, MiniDisplaySettings settings) {
        return switch (displayState) {
            case LIVE -> "Live";
            case LINK_STALE -> "Linked stale";
            case LINK_LOST -> "Link lost";
            case NO_SOURCE -> settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED ? "No linked source" : "No local source";
            case UNSUPPORTED -> "Unsupported";
        };
    }

    private int getMiniDisplayStatusColor(MiniDisplayBE.DisplayState displayState) {
        return switch (displayState) {
            case LIVE -> -12255352;
            case LINK_STALE -> -256;
            case LINK_LOST -> this.WARN;
            case NO_SOURCE -> this.DIM;
            case UNSUPPORTED -> this.ACCENT;
        };
    }

    private String getMiniDisplaySourceLabel(MiniDisplayBE.DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind, String sourceType) {
        if (sourceType != null && !sourceType.isBlank()) {
            return sourceType;
        }
        if (sourceKind != null && sourceKind != InstrumentSourceHelper.SourceKind.NO_SOURCE && sourceKind != InstrumentSourceHelper.SourceKind.UNKNOWN) {
            return sourceKind.getDisplayName();
        }
        return displayState == MiniDisplayBE.DisplayState.NO_SOURCE ? "None" : "Unknown";
    }

    private String getMiniDisplayFootprintLabel(int displayWidth) {
        return displayWidth > 1 ? "Joined 2x1" : "Single";
    }

    private String getMiniDisplayListSummary(StatsDisplayPanelBE.MiniDisplayLink link) {
        String summary = switch (link.getDisplayState()) {
            case LIVE, LINK_STALE -> this.getMiniDisplayMetricSummary(link.getSourceKind(), link.getSourceType(),
                    link.getFillPercent(), link.getTotalItems(), link.getFluidAmount(), link.getStressPercent(),
                    link.getStressSU(), link.getCapacitySU(), link.getRpm());
            case LINK_LOST -> "Waiting for refresh";
            case NO_SOURCE -> link.getSettings().getSourceMode() == MiniDisplaySettings.SourceMode.LINKED
                    ? "Waiting for linked source"
                    : "Waiting for machine face";
            case UNSUPPORTED -> {
                final String detected = this.getMiniDisplaySourceLabel(link.getDisplayState(), link.getSourceKind(), link.getSourceType());
                yield "Unsupported" + (detected.equals("Unknown") ? "" : " \u2022 " + detected);
            }
        };
        if (link.getDisplayState() == MiniDisplayBE.DisplayState.LINK_STALE) {
            summary = "Stale \u2022 " + summary;
        }
        if (link.getDisplayWidth() > 1) {
            summary = summary + " \u2022 2x1";
        }
        return summary;
    }

    private String getMiniDisplayMetricSummary(InstrumentSourceHelper.SourceKind sourceKind, String sourceType,
                                               float fillPercent, float totalItems, float fluidAmount,
                                               float stressPercent, float stressSU, float capacitySU, float rpm) {
        return switch (sourceKind) {
            case ITEM_STORAGE -> String.format("Items %.0f%% \u2022 %s total", fillPercent, this.formatCompact(totalItems));
            case FLUID_STORAGE -> String.format("Fluid %.0f%% \u2022 %s mB", fillPercent, this.formatCompact(fluidAmount));
            case BOILER -> String.format("Boiler %.0f%% \u2022 %s mB", fillPercent, this.formatCompact(fluidAmount));
            case ENERGY_STORAGE -> String.format("Energy %.0f%% \u2022 %s FE", fillPercent, this.formatCompact(fluidAmount));
            case POWER_GRID -> String.format("Grid %.0f%% \u2022 %s FE", fillPercent, this.formatCompact(fluidAmount));
            case KINETIC_STRESS -> String.format("Stress %.0f%% \u2022 %s/%s SU", stressPercent, this.formatCompact(stressSU), this.formatCompact(capacitySU));
            case KINETIC_SPEED -> String.format("RPM %s", this.formatCompact(Math.abs(rpm)));
            case NO_SOURCE, UNKNOWN -> this.getMiniDisplaySourceLabel(MiniDisplayBE.DisplayState.LIVE, sourceKind, sourceType);
        };
    }

    private List<String> buildMiniDisplayDetailLines(MiniDisplayBE.DisplayState displayState, MiniDisplaySettings settings,
                                                     InstrumentSourceHelper.SourceKind sourceKind, String sourceType,
                                                     float fillPercent, float totalItems, float fluidAmount, float fluidCapacity,
                                                     float stressPercent, float stressSU, float capacitySU, float rpm,
                                                     boolean firstDetailPresent, String firstDetailLabel, float firstDetailValue,
                                                     boolean secondDetailPresent, String secondDetailLabel, float secondDetailValue) {
        final ArrayList<String> lines = new ArrayList<String>();
        switch (displayState) {
            case LIVE, LINK_STALE -> {
                lines.add(this.getMiniDisplayMetricSummary(sourceKind, sourceType, fillPercent, totalItems, fluidAmount, stressPercent, stressSU, capacitySU, rpm));
                lines.add("Detected: " + this.getMiniDisplaySourceLabel(displayState, sourceKind, sourceType));
                switch (sourceKind) {
                    case ITEM_STORAGE -> {
                        lines.add(String.format("Fill: %.1f%%  |  Total: %s items", fillPercent, this.formatCompact(totalItems)));
                        if (firstDetailPresent) {
                            lines.add(this.truncate(firstDetailLabel + ": " + this.formatCompact(firstDetailValue), 180));
                        }
                        if (secondDetailPresent) {
                            lines.add(this.truncate(secondDetailLabel + ": " + this.formatCompact(secondDetailValue), 180));
                        }
                        if (!firstDetailPresent) {
                            lines.add("View: " + settings.getStorageDetailMode().getDisplayName());
                        }
                    }
                    case FLUID_STORAGE, BOILER -> {
                        lines.add(String.format("Fill: %.1f%%  |  Amount: %s mB", fillPercent, this.formatCompact(fluidAmount)));
                        if (fluidCapacity > 0.0f) {
                            lines.add("Capacity: " + this.formatCompact(fluidCapacity) + " mB");
                        }
                        if (firstDetailPresent) {
                            lines.add(this.truncate(firstDetailLabel + ": " + String.format("%.1f%%", firstDetailValue), 180));
                        }
                    }
                    case ENERGY_STORAGE -> {
                        lines.add(String.format("Charge: %.1f%%  |  Stored: %s FE", fillPercent, this.formatCompact(fluidAmount)));
                        if (fluidCapacity > 0.0f) {
                            lines.add("Capacity: " + this.formatCompact(fluidCapacity) + " FE");
                        }
                        if (firstDetailPresent) {
                            lines.add(this.truncate(firstDetailLabel + ": " + this.formatCompact(firstDetailValue) + " FE/t", 180));
                        }
                        if (secondDetailPresent) {
                            lines.add(this.truncate(secondDetailLabel + ": " + this.formatCompact(secondDetailValue), 180));
                        }
                    }
                    case KINETIC_STRESS -> {
                        lines.add(String.format("Stress: %.1f%%  |  Used: %s / %s SU", stressPercent, this.formatCompact(stressSU), this.formatCompact(capacitySU)));
                        lines.add("RPM: " + this.formatCompact(Math.abs(rpm)));
                    }
                    case KINETIC_SPEED -> {
                        lines.add("RPM: " + this.formatCompact(Math.abs(rpm)));
                    }
                    case NO_SOURCE, UNKNOWN -> lines.add("Waiting for a supported source readout.");
                }
                lines.add("Face: " + (settings.isLit() ? "Lit" : "Dim") + "  |  Labels: " + (settings.isShowLabels() ? "On" : "Off"));
            }
            case LINK_LOST -> {
                lines.add("The panel is keeping this relay slot, but refreshes stopped.");
                lines.add("Check the Display Link source and the Mini Display's linked mode.");
                lines.add("Open Controls if you need to switch it back to local mode.");
            }
            case NO_SOURCE -> {
                if (settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED) {
                    lines.add("Waiting for a Stats Link source.");
                    lines.add("Set the Display Link output on the source block to Stats Link.");
                } else {
                    lines.add("Waiting for a supported machine on the mounted face.");
                    lines.add("Item, fluid, boiler, and kinetic sources are supported.");
                }
                lines.add("Open Controls to review filters, lighting, and labels.");
            }
            case UNSUPPORTED -> {
                lines.add("Detected: " + this.getMiniDisplaySourceLabel(displayState, sourceKind, sourceType));
                lines.add("Mini Display relays item, fluid, boiler, kinetic, and battle readouts.");
                lines.add("Mount it on a compatible source or switch the upstream link target.");
            }
        }
        return lines;
    }

    private FactoryFreightEntry getSelectedFactoryFreightEntry(List<FactoryFreightEntry> freightEntries) {
        for (FactoryFreightEntry entry : freightEntries) {
            if (entry.id.equals(this.factorySelectedClutchId)) {
                return entry;
            }
        }
        if (freightEntries.isEmpty()) {
            return null;
        }
        FactoryFreightEntry entry = freightEntries.get(0);
        this.factorySelectedClutchId = entry.id;
        return entry;
    }

    private void renderFactoryFreightDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH,
                                            List<FactoryFreightEntry> freightEntries) {
        final FactoryFreightEntry selectedEntry = this.getSelectedFactoryFreightEntry(freightEntries);
        if (selectedEntry == null) {
            return;
        }
        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;
        final DeliveryOrderBoardView deliveryBoard = selectedEntry.getDeliveryBoard();
        final FreightManifest manifest = selectedEntry.getManifest();
        final FreightStationCargoView currentCargoView = selectedEntry.stationLink != null
            ? selectedEntry.stationLink.getCurrentCargoView()
            : (selectedEntry.manifestLink != null ? selectedEntry.manifestLink.getCargoView() : FreightStationCargoView.unavailable("No live cargo data."));
        final FreightStationCargoView nextCargoView = selectedEntry.stationLink != null
            ? selectedEntry.stationLink.getNextCargoView()
            : FreightStationCargoView.unavailable("No inbound train detected.");
        final int statusColor = this.getFactoryFreightStatusColor(selectedEntry);
        final IncomingCargoMatch incomingMatch = this.getIncomingCargoMatch(selectedEntry);

        int loadLines = 0;
        int unloadLines = 0;
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getOperation() == ManifestOperation.LOAD) {
                loadLines++;
            } else {
                unloadLines++;
            }
        }

        g.drawString(this.font, "Freight", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(selectedEntry.getDisplayName(), dInnerW), dInnerX, detailTop + 18, this.TEXT);
        g.drawString(this.font, this.truncate("Status: " + this.getFactoryFreightStatusText(selectedEntry), dInnerW), dInnerX, detailTop + 34, statusColor);

        final String stationLabel = this.getFactoryFreightStationLabel(selectedEntry);
        if (!stationLabel.isEmpty()) {
            g.drawString(this.font, this.truncate(stationLabel, dInnerW), dInnerX, detailTop + 48, this.DIM);
        }

        final String clipboardLabel = this.getFactoryFreightClipboardLabel(selectedEntry);
        if (!clipboardLabel.isEmpty()) {
            g.drawString(this.font, this.truncate(clipboardLabel, dInnerW), dInnerX, detailTop + 60, this.DIM);
        }

        final boolean validManifest = this.isFactoryFreightManifestValid(selectedEntry);
        final String manifestLabel = manifest.isEmpty()
            ? "Manifest: none configured"
            : String.format("Manifest: %d lines | load %d | unload %d%s",
                manifest.getLines().size(), loadLines, unloadLines, validManifest ? "" : " | invalid");
        g.drawString(this.font, this.truncate(manifestLabel, dInnerW), dInnerX, detailTop + 74, validManifest ? this.DIM : this.WARN);

        int infoY = detailTop + 88;
        if (selectedEntry.stationLink != null) {
            final StatsDisplayPanelBE.TrainStationLink stationLink = selectedEntry.stationLink;
            final String dockedLabel = currentCargoView.isTrainPresent()
                ? ("Docked: " + stationLink.getCurrentTrainName() + " | Carriages: " + currentCargoView.getCarriageCount())
                : "Docked: none";
            g.drawString(this.font, this.truncate(dockedLabel, dInnerW), dInnerX, infoY, currentCargoView.isTrainPresent() ? this.TEXT : this.DIM);
            infoY += 14;
            g.drawString(this.font, this.truncate(this.getFactoryFreightIncomingLine(stationLink), dInnerW), dInnerX, infoY,
                !stationLink.getNextTrainName().isEmpty() ? this.TEXT : this.DIM);
            infoY += 14;
            if (incomingMatch != IncomingCargoMatch.NONE) {
                g.drawString(this.font, "Inbound match: " + incomingMatch.label, dInnerX, infoY, this.getIncomingCargoMatchColor(incomingMatch));
                infoY += 14;
            }
        } else {
            final String cargoLabel = currentCargoView.isTrainPresent()
                ? ("Train: " + currentCargoView.getTrainName() + " | Carriages: " + currentCargoView.getCarriageCount())
                : ("Station: " + currentCargoView.getStatusMessage());
            g.drawString(this.font, this.truncate(cargoLabel, dInnerW), dInnerX, infoY, currentCargoView.isTrainPresent() ? this.TEXT : this.DIM);
            infoY += 14;
        }

        if (selectedEntry.manifestLink != null) {
            final int btnW = Math.min(dInnerW, 210);
            final int btnX = dInnerX + (dInnerW - btnW) / 2;
            final int halfW = (btnW - 8) / 3;
            this.renderFactoryButton(g, btnX, infoY + 2, halfW, 16, "Hide Done", this.hasCompletedVisibleOrders(deliveryBoard),
                false, mouseX, mouseY, FactoryClickAction.HIDE_DONE_ORDERS, selectedEntry.id);
            this.renderFactoryButton(g, btnX + halfW + 4, infoY + 2, halfW, 16, "Show Hidden", this.hasHiddenOrders(deliveryBoard),
                false, mouseX, mouseY, FactoryClickAction.SHOW_HIDDEN_ORDERS, selectedEntry.id);
            this.renderFactoryButton(g, btnX + (halfW + 4) * 2, infoY + 2, halfW, 16, "Reset Done", this.hasCompletedOrders(deliveryBoard),
                false, mouseX, mouseY, FactoryClickAction.RESET_DONE_ORDERS, selectedEntry.id);
            infoY += 22;
        }

        final int listTop = infoY + 8;
        final FreightStationCargoView previewCargoView = nextCargoView.isTrainPresent() ? nextCargoView : currentCargoView;
        final String previewTitle = nextCargoView.isTrainPresent() ? "Incoming Cargo Preview"
            : (currentCargoView.isTrainPresent() ? "Docked Cargo Preview" : "Cargo Snapshot");
        int drawY = this.renderTrainCargoPreviewSection(g, dInnerX, detailX + detailW - dPad, dInnerW, listTop,
            Math.max(2, Math.min(4, Math.max(3, (detailH - 140) / 5))), previewTitle, previewCargoView);
        int remainingRows = Math.max(2, (detailTop + detailH - drawY - 8) / 16);
        this.renderDeliveryBoardSummary(g, mouseX, mouseY, dInnerX, detailX + detailW - dPad, dInnerW, drawY, remainingRows, deliveryBoard);
    }

    private String getFactoryFreightClipboardLabel(FactoryFreightEntry entry) {
        if (entry.manifestLink == null) {
            return "Clipboard: none linked";
        }
        return "Clipboard: " + this.safetyClutchLocationLabel(entry.manifestLink.getSourcePos());
    }

    private String getFactoryFreightStationLabel(FactoryFreightEntry entry) {
        if (entry.stationLink != null) {
            final BlockPos stationPos = entry.stationLink.getStationPos() != null ? entry.stationLink.getStationPos() : entry.stationLink.getSourcePos();
            return "Station: " + this.safetyClutchLocationLabel(stationPos);
        }
        if (entry.manifestLink != null) {
            final FreightStationLink stationLink = entry.manifestLink.getStationLink();
            if (stationLink.getStationPos() != null) {
                return "Station: " + this.safetyClutchLocationLabel(stationLink.getStationPos());
            }
        }
        return "";
    }

    private boolean isFactoryFreightManifestValid(FactoryFreightEntry entry) {
        if (entry.stationLink != null && !entry.stationLink.getManifest().isEmpty()) {
            return entry.stationLink.isValidManifest();
        }
        return entry.manifestLink == null || entry.manifestLink.isValidManifest();
    }

    private int getFactoryFreightStatusColor(FactoryFreightEntry entry) {
        if (entry.stationLink != null) {
            return this.getTrainStationStatusColor(entry.stationLink);
        }
        if (entry.manifestLink != null) {
            return this.getFreightManifestStatusColor(entry.manifestLink);
        }
        return this.DIM;
    }

    private String getFactoryFreightStatusText(FactoryFreightEntry entry) {
        if (entry.stationLink != null) {
            return this.getTrainStationStatusText(entry.stationLink);
        }
        if (entry.manifestLink == null) {
            return "No freight data";
        }
        if (!entry.manifestLink.getStationLink().isLinked()) {
            return entry.manifestLink.getSummaryStatus();
        }
        final DeliveryOrderBoardView board = entry.manifestLink.getDeliveryBoard();
        if (board.getTotalOrders() > 0) {
            return board.getSummaryStatus();
        }
        if (entry.manifestLink.getCargoView().isTrainPresent()) {
            return "Train docked";
        }
        return "No inbound train";
    }

    private String getFactoryFreightTopRightLabel(FactoryFreightEntry entry) {
        if (entry.stationLink != null) {
            return this.getTrainStationEtaLabel(entry.stationLink);
        }
        return Integer.toString(entry.getManifest().getLines().size());
    }

    private String getFactoryFreightIncomingLine(StatsDisplayPanelBE.TrainStationLink link) {
        if (!link.getNextTrainName().isEmpty()) {
            if (link.getNextTrainEtaSeconds() >= 0) {
                return "Incoming: " + link.getNextTrainName() + " | ETA " + this.formatEtaClock(link.getNextTrainEtaSeconds());
            }
            if (link.isNextTrainWaitingForSignal()) {
                return "Incoming: " + link.getNextTrainName() + " | signal hold";
            }
            return "Incoming: " + link.getNextTrainName();
        }
        return "Incoming: none";
    }

    private int getIncomingCargoMatchColor(IncomingCargoMatch match) {
        return switch (match) {
            case GOOD -> -12255352;
            case PARTIAL -> -256;
            case WRONG -> this.WARN;
            case NONE -> this.DIM;
        };
    }

    private IncomingCargoMatch getIncomingCargoMatch(FactoryFreightEntry entry) {
        if (entry.stationLink == null) {
            return IncomingCargoMatch.NONE;
        }
        final FreightStationCargoView inboundCargo = entry.stationLink.getNextCargoView();
        if (!inboundCargo.isTrainPresent()) {
            return IncomingCargoMatch.NONE;
        }

        final LinkedHashMap<FreightManifestResourceKey, Integer> remainingUnload = new LinkedHashMap<FreightManifestResourceKey, Integer>();
        final Map<UUID, FreightManifestLine> linesById = new HashMap<UUID, FreightManifestLine>();
        for (FreightManifestLine line : entry.getManifest().getLines()) {
            linesById.put(line.getLineId(), line);
        }
        for (DeliveryOrderLineView lineView : entry.getDeliveryBoard().getLineViews()) {
            if (lineView.getOperation() != ManifestOperation.UNLOAD || lineView.isComplete()) {
                continue;
            }
            final FreightManifestLine manifestLine = linesById.get(lineView.getLineId());
            if (manifestLine == null) {
                continue;
            }
            final FreightManifestResourceKey resourceKey = manifestLine.getResourceKey();
            if (resourceKey == null) {
                continue;
            }
            final int remaining = Math.max(0, lineView.getTargetAmount() - lineView.getDeliveredAmount());
            if (remaining > 0) {
                remainingUnload.merge(resourceKey, remaining, Integer::sum);
            }
        }
        if (remainingUnload.isEmpty()) {
            return IncomingCargoMatch.NONE;
        }

        int matchedTypes = 0;
        int fullyMatchedTypes = 0;
        for (Map.Entry<FreightManifestResourceKey, Integer> entrySet : remainingUnload.entrySet()) {
            final int available = inboundCargo.getFreightTotals().getAmount(entrySet.getKey());
            if (available > 0) {
                matchedTypes++;
            }
            if (available >= entrySet.getValue()) {
                fullyMatchedTypes++;
            }
        }
        if (fullyMatchedTypes >= remainingUnload.size()) {
            return IncomingCargoMatch.GOOD;
        }
        if (matchedTypes > 0) {
            return IncomingCargoMatch.PARTIAL;
        }
        return IncomingCargoMatch.WRONG;
    }

    private boolean hasCompletedOrders(DeliveryOrderBoardView board) {
        for (DeliveryOrderLineView row : board.getLineViews()) {
            if (row.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCompletedVisibleOrders(DeliveryOrderBoardView board) {
        for (DeliveryOrderLineView row : board.getLineViews()) {
            if (row.isComplete() && !row.isDismissed()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHiddenOrders(DeliveryOrderBoardView board) {
        for (DeliveryOrderLineView row : board.getLineViews()) {
            if (row.isDismissed()) {
                return true;
            }
        }
        return false;
    }

    private void renderFreightManifestDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH, List<StatsDisplayPanelBE.FreightManifestLink> manifestLinks) {
        StatsDisplayPanelBE.FreightManifestLink selectedLink = this.panel.getFreightManifestLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (manifestLinks.isEmpty()) {
                return;
            }
            selectedLink = manifestLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;
        final FreightStationCargoView cargoView = selectedLink.getCargoView();
        final FreightManifest manifest = selectedLink.getManifest();
        final DeliveryOrderBoardView deliveryBoard = selectedLink.getDeliveryBoard();
        final int statusColor = this.getFreightManifestStatusColor(selectedLink);

        g.drawString(this.font, "Freight Manifest", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.getFreightManifestRowLabel(selectedLink), dInnerW), dInnerX, detailTop + 18, this.TEXT);
        g.drawString(this.font, this.truncate("Status: " + selectedLink.getSummaryStatus(), dInnerW), dInnerX, detailTop + 34, statusColor);
        g.drawString(this.font, this.truncate("Clipboard: " + this.safetyClutchLocationLabel(selectedLink.getSourcePos()), dInnerW), dInnerX, detailTop + 48, this.DIM);

        final String stationLabel = this.getFreightManifestStationLabel(selectedLink);
        if (!stationLabel.isEmpty()) {
            g.drawString(this.font, this.truncate(stationLabel, dInnerW), dInnerX, detailTop + 60, this.DIM);
        }

        final String trainLabel = cargoView.isTrainPresent()
            ? ("Train: " + cargoView.getTrainName() + "  |  Carriages: " + cargoView.getCarriageCount())
            : ("Train: " + cargoView.getStatusMessage());
        g.drawString(this.font, this.truncate(trainLabel, dInnerW), dInnerX, detailTop + 74, cargoView.isTrainPresent() ? this.TEXT : this.DIM);
        g.drawString(this.font, String.format("Lines: %d  |  Orders: %d  |  Complete: %d  |  Hidden: %d",
            manifest.getLines().size(), deliveryBoard.getTotalOrders(), deliveryBoard.getCompletedOrders(), deliveryBoard.getDismissedOrders()),
            dInnerX, detailTop + 88, this.DIM);

        final int listTop = detailTop + 106;
        final int maxRows = Math.max(3, (detailH - 122) / 16);
        this.renderDeliveryBoardSummary(g, mouseX, mouseY, dInnerX, detailX + detailW - dPad, dInnerW, listTop, maxRows, deliveryBoard);
    }

    private String safetyClutchLocationLabel(BlockPos pos) {
        return "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
    }

    private String getFreightManifestRowLabel(StatsDisplayPanelBE.FreightManifestLink link) {
        final String stationName = link.getStationLink().getStationName();
        return stationName == null || stationName.isBlank() ? "Unlinked clipboard" : stationName;
    }

    private String getFreightManifestStationLabel(StatsDisplayPanelBE.FreightManifestLink link) {
        final BlockPos stationPos = link.getStationLink().getStationPos();
        if (stationPos == null) {
            return "";
        }
        return "Station: " + this.safetyClutchLocationLabel(stationPos);
    }

    private int getFreightManifestStatusColor(StatsDisplayPanelBE.FreightManifestLink link) {
        if (!link.getStationLink().isLinked()) {
            return this.DIM;
        }
        if (!link.isValidManifest()) {
            return this.WARN;
        }
        if (link.getManifest().isEmpty()) {
            return this.ACCENT;
        }
        return link.isManifestComplete() ? -12255352 : this.ACCENT;
    }

    private void renderTrainStationDetail(GuiGraphics g, int mouseX, int mouseY, int detailX, int detailTop, int detailW, int detailH, List<StatsDisplayPanelBE.TrainStationLink> stationLinks) {
        StatsDisplayPanelBE.TrainStationLink selectedLink = this.panel.getTrainStationLink(this.factorySelectedClutchId);
        if (selectedLink == null) {
            if (stationLinks.isEmpty()) {
                return;
            }
            selectedLink = stationLinks.get(0);
            this.factorySelectedClutchId = selectedLink.getId();
        }

        final int dPad = 8;
        final int dInnerX = detailX + dPad;
        final int dInnerW = detailW - dPad * 2;
        final FreightManifest manifest = selectedLink.getManifest();
        final DeliveryOrderBoardView deliveryBoard = selectedLink.getDeliveryBoard();
        int loadLines = 0;
        int unloadLines = 0;
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getOperation() == ManifestOperation.LOAD) {
                loadLines++;
            } else {
                unloadLines++;
            }
        }

        final FreightStationCargoView currentCargoView = selectedLink.getCurrentCargoView();
        final FreightStationCargoView nextCargoView = selectedLink.getNextCargoView();
        final int statusColor = this.getTrainStationStatusColor(selectedLink);
        final String stationLabel = selectedLink.getStationPos() != null
            ? this.safetyClutchLocationLabel(selectedLink.getStationPos())
            : this.safetyClutchLocationLabel(selectedLink.getSourcePos());

        g.drawString(this.font, "Train Station", dInnerX, detailTop + 6, this.ACCENT);
        g.drawString(this.font, this.truncate(this.getTrainStationRowLabel(selectedLink), dInnerW), dInnerX, detailTop + 18, this.TEXT);
        g.drawString(this.font, this.truncate("Status: " + selectedLink.getSummaryStatus(), dInnerW), dInnerX, detailTop + 34, statusColor);
        g.drawString(this.font, this.truncate("Station: " + stationLabel, dInnerW), dInnerX, detailTop + 48, this.DIM);

        final String manifestLabel;
        if (manifest.isEmpty()) {
            manifestLabel = "Manifest: none configured";
        } else {
            manifestLabel = String.format("Manifest: %d lines | load %d | unload %d%s",
                manifest.getLines().size(), loadLines, unloadLines, selectedLink.isValidManifest() ? "" : " | invalid");
        }
        g.drawString(this.font, this.truncate(manifestLabel, dInnerW), dInnerX, detailTop + 60, selectedLink.isValidManifest() ? this.DIM : this.WARN);

        final String currentTrainLabel = currentCargoView.isTrainPresent()
            ? ("Docked: " + selectedLink.getCurrentTrainName() + " | Carriages: " + currentCargoView.getCarriageCount())
            : "Docked: none";
        g.drawString(this.font, this.truncate(currentTrainLabel, dInnerW), dInnerX, detailTop + 74, currentCargoView.isTrainPresent() ? this.TEXT : this.DIM);
        g.drawString(this.font, this.truncate(selectedLink.getCurrentCargoSummary(), dInnerW), dInnerX, detailTop + 88,
            this.getTrainStationCargoSummaryColor(selectedLink.getCurrentCargoSummary(), currentCargoView.isTrainPresent(), selectedLink.isValidManifest()));

        final String nextArrivalLabel;
        if (!selectedLink.getNextTrainName().isEmpty()) {
            final String etaText = selectedLink.getNextTrainEtaSeconds() >= 0
                ? this.formatEtaClock(selectedLink.getNextTrainEtaSeconds())
                : (selectedLink.isNextTrainWaitingForSignal() ? "signal hold" : "--:--");
            final String distanceText = selectedLink.getNextTrainDistanceBlocks() >= 0
                ? (" | " + selectedLink.getNextTrainDistanceBlocks() + " blocks")
                : "";
            nextArrivalLabel = "Next arrival: " + selectedLink.getNextTrainName() + " | ETA " + etaText + distanceText;
        } else {
            nextArrivalLabel = "Next arrival: none";
        }
        g.drawString(this.font, this.truncate(nextArrivalLabel, dInnerW), dInnerX, detailTop + 102, nextCargoView.isTrainPresent() ? this.TEXT : this.DIM);
        g.drawString(this.font, this.truncate(selectedLink.getNextCargoSummary(), dInnerW), dInnerX, detailTop + 116,
            this.getTrainStationCargoSummaryColor(selectedLink.getNextCargoSummary(), nextCargoView.isTrainPresent(), selectedLink.isValidManifest()));

        final int listTop = detailTop + 134;
        final int maxRows = Math.max(4, (detailH - (listTop - detailTop) - 12) / 16);
        final FreightStationCargoView previewCargoView = nextCargoView.isTrainPresent() ? nextCargoView : currentCargoView;
        final String previewTitle = nextCargoView.isTrainPresent() ? "Arrival Cargo Preview" : "Docked Cargo Preview";
        int drawY = this.renderTrainCargoPreviewSection(g, dInnerX, detailX + detailW - dPad, dInnerW, listTop,
            Math.max(2, Math.min(4, maxRows / 2)), previewTitle, previewCargoView);
        int remainingRows = Math.max(2, (detailTop + detailH - drawY - 8) / 16);
        this.renderDeliveryBoardSummary(g, mouseX, mouseY, dInnerX, detailX + detailW - dPad, dInnerW, drawY, remainingRows, deliveryBoard);
    }

    private String getTrainStationRowLabel(StatsDisplayPanelBE.TrainStationLink link) {
        final String name = link.getStationName();
        return name == null || name.isBlank() ? this.safetyClutchLocationLabel(link.getSourcePos()) : name;
    }

    private int getTrainStationStatusColor(StatsDisplayPanelBE.TrainStationLink link) {
        if (!link.isValidManifest() && !link.getManifest().isEmpty()) {
            return this.WARN;
        }
        final DeliveryOrderBoardView deliveryBoard = link.getDeliveryBoard();
        if (deliveryBoard.getTotalOrders() > 0) {
            if (deliveryBoard.getCompletedOrders() >= deliveryBoard.getTotalOrders()) {
                return -12255352;
            }
            return this.ACCENT;
        }
        if (link.isAssembling()) {
            return this.WARN;
        }
        if (link.getCurrentCargoView().isTrainPresent()) {
            return -12255352;
        }
        if (!link.getNextTrainName().isEmpty()) {
            return this.ACCENT;
        }
        return this.DIM;
    }

    private String getTrainStationEtaLabel(StatsDisplayPanelBE.TrainStationLink link) {
        if (link.getCurrentCargoView().isTrainPresent()) {
            return "NOW";
        }
        if (link.getNextTrainEtaSeconds() >= 0) {
            if (link.getNextTrainEtaSeconds() <= 30) {
                return "SOON";
            }
            return this.formatEtaClock(link.getNextTrainEtaSeconds());
        }
        if (link.isNextTrainWaitingForSignal()) {
            return "SIG";
        }
        return "--:--";
    }

    private String getTrainStationStatusText(StatsDisplayPanelBE.TrainStationLink link) {
        final DeliveryOrderBoardView deliveryBoard = link.getDeliveryBoard();
        if (!link.isValidManifest() && !link.getManifest().isEmpty()) {
            return "Manifest issue";
        }
        if (deliveryBoard.getTotalOrders() > 0 && deliveryBoard.getCompletedOrders() >= deliveryBoard.getTotalOrders()) {
            return "Ready to complete";
        }
        if (link.isAssembling()) {
            return "Assembling";
        }
        if (link.getCurrentCargoView().isTrainPresent()) {
            return "Train docked";
        }
        if (!link.getNextTrainName().isEmpty()) {
            if (link.isNextTrainWaitingForSignal()) {
                return "Signal hold";
            }
            if (link.getNextTrainEtaSeconds() >= 0) {
                if (link.getNextTrainEtaSeconds() <= 30) {
                    return "Arriving soon";
                }
                return "Inbound | ETA " + this.formatEtaClock(link.getNextTrainEtaSeconds());
            }
            return "Inbound train";
        }
        if (deliveryBoard.getTotalOrders() > 0) {
            return "No inbound train";
        }
        return "No inbound train";
    }

    private int getTrainStationCargoSummaryColor(String summary, boolean hasTrain, boolean validManifest) {
        if (!hasTrain) {
            return this.DIM;
        }
        final String normalized = summary.toLowerCase();
        if (!validManifest || normalized.contains("missing") || normalized.contains("invalid")) {
            return this.WARN;
        }
        return this.ACCENT;
    }

    private String formatEtaClock(int totalSeconds) {
        final int safeSeconds = Math.max(0, totalSeconds);
        final int minutes = safeSeconds / 60;
        final int seconds = safeSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private List<Map.Entry<FreightManifestResourceKey, Integer>> getTrainStationCargoEntries(FreightStationCargoView cargoView) {
        final List<Map.Entry<FreightManifestResourceKey, Integer>> entries = new ArrayList<Map.Entry<FreightManifestResourceKey, Integer>>(cargoView.getFreightTotals().getAmounts().entrySet());
        entries.sort(Comparator.<Map.Entry<FreightManifestResourceKey, Integer>>comparingInt(entry -> entry.getValue()).reversed()
            .thenComparing(entry -> FreightManifestLogic.describeResource(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private int renderTrainCargoPreviewSection(GuiGraphics g, int left, int right, int width, int top, int maxRows,
                                               String previewTitle, FreightStationCargoView previewCargoView) {
        int drawY = top;
        g.drawString(this.font, previewTitle, left, drawY, this.ACCENT);
        drawY += 14;
        if (!previewCargoView.isTrainPresent()) {
            g.drawString(this.font, this.truncate(previewCargoView.getStatusMessage(), width), left, drawY, this.DIM);
            return drawY + 18;
        }

        final List<Map.Entry<FreightManifestResourceKey, Integer>> cargoEntries = this.getTrainStationCargoEntries(previewCargoView);
        if (cargoEntries.isEmpty()) {
            g.drawString(this.font, "No cargo detected.", left, drawY, this.DIM);
            return drawY + 18;
        }

        final int visibleRows = Math.max(1, Math.min(cargoEntries.size(), Math.max(1, maxRows - 1)));
        for (int i = 0; i < visibleRows; ++i) {
            final Map.Entry<FreightManifestResourceKey, Integer> entry = cargoEntries.get(i);
            final String amount = FreightManifestLogic.formatAmount(entry.getKey().getCargoKind(), entry.getValue());
            final String label = FreightManifestLogic.describeResource(entry.getKey());
            g.drawString(this.font, this.truncate(label, Math.max(50, width - this.font.width(amount) - 8)), left, drawY, this.TEXT);
            g.drawString(this.font, amount, right - this.font.width(amount), drawY, this.DIM);
            drawY += 16;
        }
        if (cargoEntries.size() > visibleRows) {
            g.drawString(this.font, "+" + (cargoEntries.size() - visibleRows) + " more cargo types", left, drawY, this.DIM);
            drawY += 16;
        }
        return drawY + 2;
    }

    private void renderDeliveryBoardSummary(GuiGraphics g, int mouseX, int mouseY, int left, int right, int width, int top, int maxRows, DeliveryOrderBoardView board) {
        final List<DeliveryOrderLineView> lineViews = board.getLineViews();
        final List<DeliveryOrderHistoryEntry> historyEntries = board.getHistoryEntries();
        final int bottom = top + maxRows * 16;
        int drawY = top;

        g.drawString(this.font, "Delivery Orders", left, drawY, this.ACCENT);
        drawY += 14;

        if (lineViews.isEmpty()) {
            g.drawString(this.font, this.truncate(board.getSummaryStatus(), width), left, drawY, this.DIM);
            drawY += 16;
        } else {
            int rowsLeft = Math.max(1, (bottom - drawY) / 16);
            int reservedHistoryRows = 0;
            if (!historyEntries.isEmpty()) {
                int previewRows = this.factoryDeliveryHistoryExpanded ? Math.min(6, historyEntries.size()) : Math.min(2, historyEntries.size());
                reservedHistoryRows = previewRows + 2;
            }
            int orderRows = Math.max(1, rowsLeft - reservedHistoryRows);
            for (int i = 0; i < lineViews.size() && i < orderRows; ++i) {
                final DeliveryOrderLineView lineView = lineViews.get(i);
                final String status = this.formatDeliveryBoardLineStatus(lineView);
                final int statusWidth = this.font.width(status);
                final String label = this.formatDeliveryBoardLineLabel(lineView);
                g.drawString(this.font, this.truncate(label, Math.max(50, width - statusWidth - 8)), left, drawY, this.getDeliveryBoardLineColor(lineView));
                g.drawString(this.font, status, right - statusWidth, drawY, lineView.isDismissed() ? this.DIM : this.ACCENT);
                drawY += 16;
            }
            if (lineViews.size() > orderRows && drawY < bottom) {
                g.drawString(this.font, "+" + (lineViews.size() - orderRows) + " more orders", left, drawY, this.DIM);
                drawY += 16;
            }
        }

        if (historyEntries.isEmpty() || drawY >= bottom - 14) {
            return;
        }

        final int toggleWidth = Math.min(132, Math.max(88, width / 3));
        this.renderFactoryButton(g, left, drawY, toggleWidth, 14,
            this.factoryDeliveryHistoryExpanded ? "Recent History \u25bc" : "Recent History \u25b6",
            true, this.factoryDeliveryHistoryExpanded, mouseX, mouseY, FactoryClickAction.TOGGLE_DELIVERY_HISTORY, this.factorySelectedClutchId);
        drawY += 18;

        int requestedHistoryRows = this.factoryDeliveryHistoryExpanded ? historyEntries.size() : 2;
        int historyRows = Math.max(1, Math.min(requestedHistoryRows, (bottom - drawY) / 16));
        for (int i = 0; i < historyEntries.size() && i < historyRows; ++i) {
            final DeliveryOrderHistoryEntry entry = historyEntries.get(i);
            final String age = FreightManifestLogic.formatTicks(Math.max(0L, board.getCurrentTick() - entry.getTick()));
            final String label = entry.getTitle() + ": " + entry.getDetail();
            g.drawString(this.font, this.truncate(label, Math.max(50, width - this.font.width(age) - 8)), left, drawY, this.getDeliveryHistoryColor(entry));
            g.drawString(this.font, age, right - this.font.width(age), drawY, this.DIM);
            drawY += 16;
        }
    }

    private String formatDeliveryBoardLineLabel(DeliveryOrderLineView lineView) {
        final String prefix = lineView.isComplete() ? "\u2714" : "\u2022";
        final String badge = lineView.getResetMode().getBadgeLabel(lineView.getResetIntervalValue());
        return prefix + " [" + badge + "] " + lineView.getOperation().getDisplayName() + " "
            + FreightManifestLogic.formatAmount(lineView.getCargoKind(), lineView.getTargetAmount()) + " " + lineView.getDisplayName();
    }

    private String formatDeliveryBoardLineStatus(DeliveryOrderLineView lineView) {
        String progress = lineView.getDeliveredAmount() + "/" + Math.max(1, lineView.getTargetAmount());
        if (lineView.isDismissed()) {
            return progress + " hidden";
        }
        if (lineView.isComplete()) {
            return progress + " done";
        }
        if (lineView.getTicksUntilReset() > 0L) {
            return progress + " | " + FreightManifestLogic.formatTicks(lineView.getTicksUntilReset());
        }
        return progress;
    }

    private int getDeliveryBoardLineColor(DeliveryOrderLineView lineView) {
        if (lineView.isDismissed()) {
            return this.DIM;
        }
        return lineView.isComplete() ? -12255352 : this.TEXT;
    }

    private int getDeliveryHistoryColor(DeliveryOrderHistoryEntry entry) {
        return switch (entry.getType()) {
            case COMPLETED, MANUAL_COMPLETE, SHOWN -> -12255352;
            case HIDDEN -> this.DIM;
            case AUTO_RESET, MANUAL_RESET -> this.ACCENT;
        };
    }

    private String getSafetyClutchStatusLabel(SafetyClutchSettings settings, boolean tripped, boolean networkRunning) {
        if (!settings.isEnabled()) {
            return "Disabled";
        }
        if (tripped) {
            return "Tripped";
        }
        return networkRunning ? "Armed" : "Idle";
    }

    private int getSafetyClutchStatusColor(SafetyClutchSettings settings, boolean tripped, boolean networkRunning) {
        if (!settings.isEnabled()) {
            return this.DIM;
        }
        if (tripped) {
            return this.WARN;
        }
        return networkRunning ? -12255352 : this.ACCENT;
    }

    private String getTransmissionStatusLabel(AutoTransmissionClutchSettings settings, AutoTransmissionClutchBE.AlarmState alarm, boolean networkRunning, float currentModifier) {
        if (!settings.isEnabled()) {
            return "Disabled";
        }
        if (alarm == AutoTransmissionClutchBE.AlarmState.VERY_CONCERNING) {
            return "VERY LOW RPM!";
        }
        if (alarm == AutoTransmissionClutchBE.AlarmState.CONCERNING) {
            return "Low RPM Warning";
        }
        if (!networkRunning) {
            return "Idle";
        }
        if (currentModifier > 1.01f) {
            return "Boosting";
        }
        if (currentModifier < 0.99f) {
            return "Throttling";
        }
        return "Pass-through";
    }

    private int getTransmissionStatusColor(AutoTransmissionClutchSettings settings, AutoTransmissionClutchBE.AlarmState alarm, boolean networkRunning, float currentModifier) {
        if (!settings.isEnabled()) {
            return this.DIM;
        }
        if (alarm == AutoTransmissionClutchBE.AlarmState.VERY_CONCERNING) {
            return this.WARN;
        }
        if (alarm == AutoTransmissionClutchBE.AlarmState.CONCERNING) {
            return -256;
        }
        if (!networkRunning) {
            return this.ACCENT;
        }
        if (Math.abs(currentModifier - 1.0f) > 0.01f) {
            return this.ACCENT;
        }
        return -12255352;
    }

    private String getLoadBalancerStatusLabel(LoadBalancerSettings settings, LoadBalancerBE.TopologyStatus topologyStatus,
                                              boolean networkRunning, boolean balancing) {
        if (!settings.isEnabled()) {
            return "Disabled";
        }
        return switch (topologyStatus) {
            case NO_SOURCE -> "Idle";
            case NO_BRANCHES -> "No branches";
            case SIDE_INPUT -> "Wrong input";
            case MERGED_BRANCHES -> "Merged branches";
            case BRANCH_SOURCE -> "Branch source";
            case TRUNCATED -> "Topology limit";
            case OK -> balancing ? "Balancing" : (networkRunning ? "Standby" : "Idle");
        };
    }

    private int getLoadBalancerStatusColor(LoadBalancerSettings settings, LoadBalancerBE.TopologyStatus topologyStatus,
                                           boolean networkRunning, boolean balancing) {
        if (!settings.isEnabled()) {
            return this.DIM;
        }
        return switch (topologyStatus) {
            case NO_SOURCE -> this.DIM;
            case NO_BRANCHES, TRUNCATED -> -256;
            case SIDE_INPUT, MERGED_BRANCHES, BRANCH_SOURCE -> this.WARN;
            case OK -> balancing ? this.ACCENT : (networkRunning ? -12255352 : this.ACCENT);
        };
    }

    private String getLoadBalancerHint(LoadBalancerSettings settings, LoadBalancerBE.TopologyStatus topologyStatus,
                                       float capacitySU, float baseStressSU, float leftStressSU, float rightStressSU,
                                       float leftBudgetSU, float rightBudgetSU) {
        if (!settings.isEnabled()) {
            return "Re-enable the balancer to resume branch control.";
        }
        return switch (topologyStatus) {
            case NO_SOURCE -> "Feed RPM into the rear shaft to wake it up.";
            case NO_BRANCHES -> "Attach machines to the left or right output shafts.";
            case SIDE_INPUT -> "The rear shaft must be the only power input.";
            case MERGED_BRANCHES -> "Keep left and right output networks separated.";
            case BRANCH_SOURCE -> "Remove motors or controllers from the output branches.";
            case TRUNCATED -> "Topology scan hit its limit; simplify the branch network.";
            case OK -> this.getLoadBalancerRuntimeHint(settings, capacitySU, baseStressSU, leftStressSU, rightStressSU,
                    leftBudgetSU, rightBudgetSU);
        };
    }

    private String getLoadBalancerRuntimeHint(LoadBalancerSettings settings, float capacitySU, float baseStressSU,
                                              float leftStressSU, float rightStressSU, float leftBudgetSU, float rightBudgetSU) {
        if (settings.getSharingMode() == LoadBalancerSettings.SharingMode.STRICT) {
            return "Strict keeps each branch inside its own share.";
        }

        final float totalHigh = settings.getLimitMode() == LoadBalancerSettings.LimitMode.PERCENTAGE
                ? capacitySU * settings.getUpperBound() / 100.0f
                : settings.getUpperBound();
        final float strictLeftHigh = totalHigh * settings.getLeftSharePct() / 100.0f;
        final float strictRightHigh = totalHigh - strictLeftHigh;
        final boolean leftBorrowing = leftBudgetSU > strictLeftHigh + 0.5f && leftStressSU > strictLeftHigh + 0.5f;
        final boolean rightBorrowing = rightBudgetSU > strictRightHigh + 0.5f && rightStressSU > strictRightHigh + 0.5f;
        final float totalUsed = baseStressSU + leftStressSU + rightStressSU;

        if (leftBorrowing && !rightBorrowing) {
            return "Elastic is borrowing unused headroom toward the left branch.";
        }
        if (rightBorrowing && !leftBorrowing) {
            return "Elastic is borrowing unused headroom toward the right branch.";
        }
        if (totalUsed < totalHigh - 0.5f) {
            return "Elastic is filling spare capacity toward the upper band.";
        }
        return "Elastic is holding both branches near the active band.";
    }

    private int getBranchLoadColor(boolean attached, float stressSU, float budgetSU) {
        if (!attached) {
            return this.WARN;
        }
        if (budgetSU <= 0.0f) {
            return -256;
        }
        final float ratio = stressSU / budgetSU;
        if (ratio >= 1.0f) {
            return this.WARN;
        }
        if (ratio >= 0.85f) {
            return -23296;
        }
        return this.ACCENT;
    }

    private String getLoadBalancerRuntimeLabel(LoadBalancerSettings settings, LoadBalancerBE.TopologyStatus topologyStatus,
                                               float capacitySU, float baseStressSU, float leftStressSU, float rightStressSU,
                                               float leftBudgetSU, float rightBudgetSU) {
        if (!settings.isEnabled()) {
            return "Disabled";
        }
        if (topologyStatus != LoadBalancerBE.TopologyStatus.OK) {
            return switch (topologyStatus) {
                case NO_SOURCE -> "Waiting for RPM";
                case NO_BRANCHES -> "Waiting for branch";
                case SIDE_INPUT -> "Wrong input";
                case MERGED_BRANCHES -> "Branches merged";
                case BRANCH_SOURCE -> "Branch source";
                case TRUNCATED -> "Topology limit";
                case OK -> "Standby";
            };
        }
        if (settings.getSharingMode() == LoadBalancerSettings.SharingMode.STRICT) {
            return "Strict share";
        }

        final float totalHigh = this.getLoadBalancerBoundSU(settings.getUpperBound(), settings.getLimitMode(), capacitySU);
        final float strictLeftHigh = totalHigh * settings.getLeftSharePct() / 100.0f;
        final float strictRightHigh = totalHigh - strictLeftHigh;
        final boolean leftBorrowing = leftBudgetSU > strictLeftHigh + 0.5f && leftStressSU > strictLeftHigh + 0.5f;
        final boolean rightBorrowing = rightBudgetSU > strictRightHigh + 0.5f && rightStressSU > strictRightHigh + 0.5f;
        final float totalUsed = baseStressSU + leftStressSU + rightStressSU;

        if (leftBorrowing && !rightBorrowing) {
            return "Borrowing left";
        }
        if (rightBorrowing && !leftBorrowing) {
            return "Borrowing right";
        }
        if (totalUsed < totalHigh - 0.5f) {
            return "Filling spare";
        }
        return "Elastic hold";
    }

    private float getLoadBalancerBoundSU(float bound, LoadBalancerSettings.LimitMode limitMode, float capacitySU) {
        if (limitMode == LoadBalancerSettings.LimitMode.PERCENTAGE) {
            return capacitySU * bound / 100.0f;
        }
        return bound;
    }

    private float getLoadBalancerBandFraction(float bound, LoadBalancerSettings.LimitMode limitMode, float capacitySU) {
        if (limitMode == LoadBalancerSettings.LimitMode.PERCENTAGE) {
            return Math.min(1.0f, Math.max(0.0f, bound / 100.0f));
        }
        final float safeCapacity = Math.max(1.0f, capacitySU);
        return Math.min(1.0f, Math.max(0.0f, bound / safeCapacity));
    }

    private boolean stressFractionExceedsUpper(float loadFraction, float upperFraction) {
        return loadFraction > Math.min(1.0f, Math.max(0.0f, upperFraction)) + 0.0001f;
    }

    private float estimateLoadBalancerSourceSpeed(float currentLeft, float transmittedLeft, float currentRight, float transmittedRight) {
        if (Math.abs(currentLeft) > 0.0001f && Math.abs(transmittedLeft) > 0.0001f) {
            return transmittedLeft / currentLeft;
        }
        if (Math.abs(currentRight) > 0.0001f && Math.abs(transmittedRight) > 0.0001f) {
            return transmittedRight / currentRight;
        }
        return 0.0f;
    }

    private AutoTransmissionClutchBE getLoadedAutoTransmission(BlockPos sourcePos) {
        if (sourcePos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(sourcePos);
        if (be instanceof AutoTransmissionClutchBE trans) {
            return trans;
        }
        return null;
    }

    private boolean openAutoTransmissionControls(String linkId) {
        final StatsDisplayPanelBE.AutoTransmissionClutchLink link = this.panel.getAutoTransmissionClutchLink(linkId);
        if (link == null) {
            this.showExportFeedback("\u00a7cSelected transmission link is missing", false);
            return true;
        }
        final AutoTransmissionClutchBE trans = this.getLoadedAutoTransmission(link.getSourcePos());
        if (trans == null) {
            this.showExportFeedback("\u00a7cSelected transmission is not client-loaded", false);
            return true;
        }
        ClientScreenHelper.openAutoTransmissionClutch(trans, this);
        return true;
    }

    private AutoTransmissionClutchSettings copyTransmissionSettings(String linkId) {
        final StatsDisplayPanelBE.AutoTransmissionClutchLink link = this.panel.getAutoTransmissionClutchLink(linkId);
        if (link == null) {
            return null;
        }
        final AutoTransmissionClutchBE trans = this.getLoadedAutoTransmission(link.getSourcePos());

        return (trans != null) ? trans.getSettings() : link.getSettings();
    }

    private boolean sendTransmissionSettings(String linkId, AutoTransmissionClutchSettings settings) {
        final StatsDisplayPanelBE.AutoTransmissionClutchLink link = this.panel.getAutoTransmissionClutchLink(linkId);
        if (link == null || settings == null) {
            return true;
        }
        PacketDistributor.sendToServer(new AutoTransmissionClutchConfigPacket(link.getSourcePos(), settings));
        return true;
    }

    private LoadBalancerBE getLoadedLoadBalancer(BlockPos sourcePos) {
        if (sourcePos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(sourcePos);
        if (be instanceof LoadBalancerBE balancer) {
            return balancer;
        }
        return null;
    }

    private boolean openLoadBalancerControls(String linkId) {
        final StatsDisplayPanelBE.LoadBalancerLink link = this.panel.getLoadBalancerLink(linkId);
        if (link == null) {
            this.showExportFeedback("\u00a7cSelected load balancer link is missing", false);
            return true;
        }
        final LoadBalancerBE balancer = this.getLoadedLoadBalancer(link.getSourcePos());
        if (balancer == null) {
            this.showExportFeedback("\u00a7cSelected load balancer is not client-loaded", false);
            return true;
        }
        ClientScreenHelper.openLoadBalancer(balancer, this);
        return true;
    }

    private LoadBalancerSettings copyLoadBalancerSettings(String linkId) {
        final StatsDisplayPanelBE.LoadBalancerLink link = this.panel.getLoadBalancerLink(linkId);
        if (link == null) {
            return null;
        }
        final LoadBalancerBE balancer = this.getLoadedLoadBalancer(link.getSourcePos());

        return (balancer != null) ? balancer.getSettings() : link.getSettings();
    }

    private boolean sendLoadBalancerSettings(String linkId, LoadBalancerSettings settings) {
        final StatsDisplayPanelBE.LoadBalancerLink link = this.panel.getLoadBalancerLink(linkId);
        if (link == null || settings == null) {
            return true;
        }
        PacketDistributor.sendToServer(new LoadBalancerConfigPacket(link.getSourcePos(), settings));
        return true;
    }

    private MiniDisplayBE getLoadedMiniDisplay(BlockPos sourcePos) {
        if (sourcePos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(sourcePos);
        if (be instanceof MiniDisplayBE miniDisplay) {
            return miniDisplay.getControllerBE();
        }
        return null;
    }

    private boolean openMiniDisplayControls(String linkId) {
        final StatsDisplayPanelBE.MiniDisplayLink link = this.panel.getMiniDisplayLink(linkId);
        if (link == null) {
            this.showExportFeedback("\u00a7cSelected stats link is missing", false);
            return true;
        }
        final MiniDisplayBE miniDisplay = this.getLoadedMiniDisplay(link.getSourcePos());
        if (miniDisplay == null) {
            this.showExportFeedback("\u00a7cSelected mini display is not client-loaded", false);
            return true;
        }
        ClientScreenHelper.openMiniDisplay(miniDisplay, this);
        return true;
    }

    private MiniDisplaySettings copyMiniDisplaySettings(String linkId) {
        final StatsDisplayPanelBE.MiniDisplayLink link = this.panel.getMiniDisplayLink(linkId);
        if (link == null) {
            return null;
        }
        final MiniDisplayBE miniDisplay = this.getLoadedMiniDisplay(link.getSourcePos());

        return (miniDisplay != null) ? miniDisplay.getSettings() : link.getSettings();
    }

    private boolean sendMiniDisplaySettings(String linkId, MiniDisplaySettings settings) {
        final StatsDisplayPanelBE.MiniDisplayLink link = this.panel.getMiniDisplayLink(linkId);
        if (link == null || settings == null) {
            return true;
        }
        PacketDistributor.sendToServer(new MiniDisplayConfigPacket(link.getSourcePos(), settings));
        return true;
    }

    private SafetyClutchBE getLoadedSafetyClutch(BlockPos sourcePos) {
        if (sourcePos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(sourcePos);
        if (be instanceof SafetyClutchBE clutch) {
            return clutch;
        }
        return null;
    }

    private KineticFlywheelBE getLoadedKineticFlywheel(BlockPos sourcePos) {
        if (sourcePos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        final BlockEntity be = this.minecraft.level.getBlockEntity(sourcePos);
        if (be instanceof KineticFlywheelBE flywheel) {
            return flywheel;
        }
        return null;
    }

    private KineticFlywheelSettings copyKineticFlywheelSettings(String linkId) {
        final StatsDisplayPanelBE.KineticFlywheelLink link = this.panel.getKineticFlywheelLink(linkId);
        if (link == null) {
            return null;
        }
        final KineticFlywheelBE flywheel = this.getLoadedKineticFlywheel(link.getSourcePos());

        return (flywheel != null) ? flywheel.getSettings() : link.getSettings();
    }

    private boolean sendKineticFlywheelSettings(String linkId, KineticFlywheelSettings settings) {
        final StatsDisplayPanelBE.KineticFlywheelLink link = this.panel.getKineticFlywheelLink(linkId);
        if (link == null || settings == null) {
            return true;
        }
        PacketDistributor.sendToServer(new KineticFlywheelConfigPacket(link.getSourcePos(), settings));
        return true;
    }

    private boolean sendKineticFlywheelAction(String linkId, KineticFlywheelConfigPacket.Action action) {
        final StatsDisplayPanelBE.KineticFlywheelLink link = this.panel.getKineticFlywheelLink(linkId);
        if (link == null) {
            return true;
        }
        PacketDistributor.sendToServer(new KineticFlywheelConfigPacket(link.getSourcePos(), action));
        return true;
    }

    private boolean openSafetyClutchControls(String linkId) {
        final StatsDisplayPanelBE.SafetyClutchLink link = this.panel.getSafetyClutchLink(linkId);
        if (link == null) {
            this.showExportFeedback("\u00a7cSelected clutch link is missing", false);
            return true;
        }
        final SafetyClutchBE clutch = this.getLoadedSafetyClutch(link.getSourcePos());
        if (clutch == null) {
            this.showExportFeedback("\u00a7cSelected clutch is not client-loaded", false);
            return true;
        }
        ClientScreenHelper.openSafetyClutch(clutch, this);
        return true;
    }

    private SafetyClutchSettings copySafetyClutchSettings(String linkId) {
        final StatsDisplayPanelBE.SafetyClutchLink link = this.panel.getSafetyClutchLink(linkId);
        if (link == null) {
            return null;
        }
        final SafetyClutchBE clutch = this.getLoadedSafetyClutch(link.getSourcePos());

        return (clutch != null) ? clutch.getSettings() : link.getSettings();
    }

    private boolean sendSafetyClutchSettings(String linkId, SafetyClutchSettings settings) {
        final StatsDisplayPanelBE.SafetyClutchLink link = this.panel.getSafetyClutchLink(linkId);
        if (link == null || settings == null) {
            return true;
        }
        PacketDistributor.sendToServer(new SafetyClutchConfigPacket(link.getSourcePos(), settings));
        return true;
    }

    private boolean sendSafetyClutchAction(String linkId, SafetyClutchConfigPacket.Action action) {
        final StatsDisplayPanelBE.SafetyClutchLink link = this.panel.getSafetyClutchLink(linkId);
        if (link == null) {
            return true;
        }
        PacketDistributor.sendToServer(new SafetyClutchConfigPacket(link.getSourcePos(), action));
        return true;
    }

    private void playPanelClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private FactoryFreightEntry findFactoryFreightEntry(String entryId) {
        for (FactoryFreightEntry entry : this.getFactoryFreightEntries(this.getFactoryManifestLinks(), this.getFactoryTrainStationLinks())) {
            if (entry.id.equals(entryId)) {
                return entry;
            }
        }
        return null;
    }

    private boolean sendFactoryFreightAction(String entryId, DeliveryOrderAction action) {
        final FactoryFreightEntry entry = this.findFactoryFreightEntry(entryId);
        if (entry == null || entry.manifestLink == null) {
            this.showExportFeedback("\u00a7cNo clipboard linked for this freight view", false);
            return true;
        }
        PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(entry.manifestLink.getSourcePos(), null, action));
        return true;
    }

    private boolean handleVehicleSettingsPageClick(double mx, double my) {
        for (int i = this.vehicleClickTargets.size() - 1; i >= 0; --i) {
            final VehicleClickTarget target = this.vehicleClickTargets.get(i);
            if (!target.contains(mx, my)) {
                continue;
            }
            switch (target.action) {
                case SET_NOSE -> {
                    this.panel.getSettings().setNoseSetting(StatsDisplaySettings.NoseSetting.valueOf(target.value));
                    this.playPanelClick(1.0f);
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
                case SET_PRESET -> {
                    this.panel.getSettings().setVehiclePreset(StatsDisplaySettings.VehiclePreset.valueOf(target.value));
                    this.playPanelClick(1.0f);
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleBattleBoardPresetClick(double mx, double my) {
        for (int i = this.battleBoardClickTargets.size() - 1; i >= 0; --i) {
            final BattleBoardClickTarget target = this.battleBoardClickTargets.get(i);
            if (!target.contains(mx, my)) {
                continue;
            }
            switch (target.action) {
                case SET_PRESET -> {
                    this.panel.getSettings().setBattleBoardPreset(StatsDisplaySettings.BattleBoardPreset.valueOf(target.value));
                    this.playPanelClick(1.0f);
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryRenderBattleTacticalV2Viewport3D(GuiGraphics g, float partialTick) {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null || sourceId.isEmpty()) return false;
        if (this.getCurrentBattleTacticalV2Link() == null) return false;

        if (com.createstats.client.battle.BattleTacticalV2Viewport3D.isForce2D(this.panel.getBlockPos())) {
            return false;
        }
        return com.createstats.client.battle.BattleTacticalV2Viewport3D.render(
                g, this.font,
                this.graphLeft, this.graphTop, this.graphWidth, this.graphHeight,
                this.panel, sourceId, this.getGuiRenderTick(), partialTick);
    }

    private void renderV2DispatchDiagnostic(GuiGraphics g) {
        final String sourceId = this.getCurrentSourcePageId();
        final SourceConfig config = (sourceId == null || sourceId.isEmpty())
                ? null : this.panel.getSourceRegistry().get(sourceId);
        final String typeDbg = config == null ? "null" : String.valueOf(config.getSourceType());
        int viewMode = -99;
        int linkCount = this.panel.getBattleScreenLinks().size();
        if (config != null) {
            for (com.createstats.blockentity.StatsDisplayPanelBE.BattleScreenLink l : this.panel.getBattleScreenLinks()) {
                if (l == null) continue;
                viewMode = l.getBattleViewMode();
                if (config.getSourcePos() != null && config.getSourcePos().equals(l.getSourcePos())) break;
            }
        }
        final StatsDisplaySettings.DisplayMode displayMode = this.panel.getSettings().getDisplayMode();
        final String dbg = "V2DBG src=" + (sourceId == null ? "-" : sourceId)
                + " type=" + typeDbg
                + " mode=" + displayMode
                + " tab=" + this.selectedTab
                + " links=" + linkCount
                + " viewMode=" + viewMode;
        g.drawString(this.font, dbg, this.graphLeft + 2, this.graphTop + 2, 0xFFff80ff, false);
    }

    private void renderBattleBoardPresetOverlay(GuiGraphics g, int mouseX, int mouseY) {
        final String sourceId = this.getCurrentSourcePageId();
        if (sourceId == null) {
            return;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
        if (!this.isBattleScreenSource(config)) {
            return;
        }
        final StatsDisplaySettings.BattleBoardPreset[] presets = StatsDisplaySettings.BattleBoardPreset.values();
        final StatsDisplaySettings.BattleBoardPreset selectedPreset = this.panel.getSettings().getBattleBoardPreset();
        final int gap = 4;
        final int overlayH = 22;
        final int chipH = 18;
        final int maxW = this.panelWidth - 20;
        final int chipW = Math.max(48, Math.min(78, (maxW - 104 - gap * Math.max(0, presets.length - 1)) / Math.max(1, presets.length)));
        final int chipAreaW = presets.length * chipW + Math.max(0, presets.length - 1) * gap;
        final int labelW = Math.max(72, maxW - chipAreaW - 20);
        final int overlayW = labelW + chipAreaW + 20;
        final int x = this.panelLeft + this.panelWidth - overlayW - 10;
        final int y = this.panelTop + this.panelHeight - overlayH - 32;
        g.fill(x, y, x + overlayW, y + overlayH, this.BG);
        this.border(g, x, y, overlayW, overlayH, this.BORDER);
        g.fill(x + 1, y + 1, x + overlayW - 1, y + 3, this.ACCENT);
        final String status = this.truncate("Battle Bridge: " + selectedPreset.getDisplayName(), Math.max(36, labelW - 6));
        g.drawString(this.font, status, x + 8, y + 7, this.TEXT);
        final int chipBaseX = x + overlayW - chipAreaW - 8;
        for (int i = 0; i < presets.length; ++i) {
            final int chipX = chipBaseX + i * (chipW + gap);
            final int chipY = y + 2;
            this.renderBattleBoardPresetChip(g, chipX, chipY, chipW, chipH, presets[i], presets[i] == selectedPreset, mouseX, mouseY);
        }
    }

    private void renderBattleBoardPresetChip(GuiGraphics g, int x, int y, int w, int h, StatsDisplaySettings.BattleBoardPreset preset, boolean selected, int mouseX, int mouseY) {
        final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, selected ? this.TAB_SEL : (hover ? this.TAB_HOVER : this.TAB_BG));
        this.border(g, x, y, w, h, selected ? this.ACCENT : this.BORDER);
        final String label = this.truncate(preset.getDisplayName(), Math.max(24, w - 12));
        g.drawString(this.font, label, x + 6, y + 5, selected ? this.TEXT : this.DIM);
        this.battleBoardClickTargets.add(new BattleBoardClickTarget(BattleBoardClickAction.SET_PRESET, x, y, w, h, preset.name()));
    }

    private boolean handleFactoryPageClick(double mx, double my) {
        for (int i = this.factoryClickTargets.size() - 1; i >= 0; --i) {
            final FactoryClickTarget target = this.factoryClickTargets.get(i);
            if (!target.contains(mx, my)) {
                continue;
            }
            switch (target.action) {
                case SELECT_CLUTCH -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case OPEN_CONTROLS -> {
                    this.playPanelClick(1.0f);
                    return this.openSafetyClutchControls(target.value);
                }
                case TOGGLE_ENABLED -> {
                    this.playPanelClick(1.0f);
                    final SafetyClutchSettings settings = this.copySafetyClutchSettings(target.value);
                    if (settings != null) {
                        settings.setEnabled(!settings.isEnabled());
                    }
                    return this.sendSafetyClutchSettings(target.value, settings);
                }
                case CYCLE_MODE -> {
                    this.playPanelClick(1.0f);
                    final SafetyClutchSettings settings = this.copySafetyClutchSettings(target.value);
                    if (settings != null) {
                        settings.cycleMode();
                    }
                    return this.sendSafetyClutchSettings(target.value, settings);
                }
                case MANUAL_RESET -> {
                    this.playPanelClick(0.9f);
                    return this.sendSafetyClutchAction(target.value, SafetyClutchConfigPacket.Action.MANUAL_RESET);
                }
                case MANUAL_TRIP -> {
                    this.playPanelClick(0.9f);
                    return this.sendSafetyClutchAction(target.value, SafetyClutchConfigPacket.Action.MANUAL_TRIP);
                }
                case SELECT_TRANSMISSION -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case SELECT_LOAD_BALANCER -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case SELECT_MINI_DISPLAY -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case SELECT_FREIGHT -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case SELECT_FLYWHEEL -> {
                    this.factorySelectedClutchId = (target.value != null) ? target.value : "";
                    this.factoryDeliveryHistoryExpanded = false;
                    this.playPanelClick(1.0f);
                    return true;
                }
                case TOGGLE_FLYWHEEL_ARMED -> {
                    this.playPanelClick(1.0f);
                    final KineticFlywheelSettings fwSettings = this.copyKineticFlywheelSettings(target.value);
                    if (fwSettings != null) {
                        fwSettings.setDischargeArmed(!fwSettings.isDischargeArmed());
                    }
                    return this.sendKineticFlywheelSettings(target.value, fwSettings);
                }
                case FLYWHEEL_FORCE_PARK -> {
                    this.playPanelClick(0.9f);
                    return this.sendKineticFlywheelAction(target.value, KineticFlywheelConfigPacket.Action.FORCE_PARK);
                }
                case FLYWHEEL_RELEASE -> {
                    this.playPanelClick(1.05f);
                    return this.sendKineticFlywheelAction(target.value, KineticFlywheelConfigPacket.Action.RELEASE);
                }
                case FLYWHEEL_LOW_RESERVE_DOWN -> {
                    this.playPanelClick(0.95f);
                    final KineticFlywheelSettings fwSettings = this.copyKineticFlywheelSettings(target.value);
                    if (fwSettings != null) {
                        fwSettings.setLowReservePct(fwSettings.getLowReservePct() - 5.0f);
                    }
                    return this.sendKineticFlywheelSettings(target.value, fwSettings);
                }
                case FLYWHEEL_LOW_RESERVE_UP -> {
                    this.playPanelClick(1.05f);
                    final KineticFlywheelSettings fwSettings = this.copyKineticFlywheelSettings(target.value);
                    if (fwSettings != null) {
                        fwSettings.setLowReservePct(fwSettings.getLowReservePct() + 5.0f);
                    }
                    return this.sendKineticFlywheelSettings(target.value, fwSettings);
                }
                case TOGGLE_DELIVERY_HISTORY -> {
                    this.factoryDeliveryHistoryExpanded = !this.factoryDeliveryHistoryExpanded;
                    this.playPanelClick(this.factoryDeliveryHistoryExpanded ? 1.05f : 0.95f);
                    return true;
                }
                case OPEN_TRANSMISSION_CONTROLS -> {
                    this.playPanelClick(1.0f);
                    return this.openAutoTransmissionControls(target.value);
                }
                case TOGGLE_TRANSMISSION_ENABLED -> {
                    this.playPanelClick(1.0f);
                    final AutoTransmissionClutchSettings tSettings = this.copyTransmissionSettings(target.value);
                    if (tSettings != null) {
                        tSettings.setEnabled(!tSettings.isEnabled());
                    }
                    return this.sendTransmissionSettings(target.value, tSettings);
                }
                case CYCLE_TRANSMISSION_LIMIT_MODE -> {
                    this.playPanelClick(1.0f);
                    final AutoTransmissionClutchSettings tSettings = this.copyTransmissionSettings(target.value);
                    if (tSettings != null) {
                        tSettings.cycleLimitMode();
                    }
                    return this.sendTransmissionSettings(target.value, tSettings);
                }
                case OPEN_LOAD_BALANCER_CONTROLS -> {
                    this.playPanelClick(1.0f);
                    return this.openLoadBalancerControls(target.value);
                }
                case TOGGLE_LOAD_BALANCER_ENABLED -> {
                    this.playPanelClick(1.0f);
                    final LoadBalancerSettings balancerSettings = this.copyLoadBalancerSettings(target.value);
                    if (balancerSettings != null) {
                        balancerSettings.setEnabled(!balancerSettings.isEnabled());
                    }
                    return this.sendLoadBalancerSettings(target.value, balancerSettings);
                }
                case CYCLE_LOAD_BALANCER_LIMIT_MODE -> {
                    this.playPanelClick(1.0f);
                    final LoadBalancerSettings balancerSettings = this.copyLoadBalancerSettings(target.value);
                    if (balancerSettings != null) {
                        balancerSettings.cycleLimitMode();
                    }
                    return this.sendLoadBalancerSettings(target.value, balancerSettings);
                }
                case CYCLE_LOAD_BALANCER_SHARING_MODE -> {
                    this.playPanelClick(1.0f);
                    final LoadBalancerSettings balancerSettings = this.copyLoadBalancerSettings(target.value);
                    if (balancerSettings != null) {
                        balancerSettings.cycleSharingMode();
                    }
                    return this.sendLoadBalancerSettings(target.value, balancerSettings);
                }
                case OPEN_MINI_DISPLAY_CONTROLS -> {
                    this.playPanelClick(1.0f);
                    return this.openMiniDisplayControls(target.value);
                }
                case CYCLE_MINI_DISPLAY_SOURCE_MODE -> {
                    this.playPanelClick(1.0f);
                    final MiniDisplaySettings miniDisplaySettings = this.copyMiniDisplaySettings(target.value);
                    if (miniDisplaySettings != null) {
                        miniDisplaySettings.cycleSourceMode();
                    }
                    return this.sendMiniDisplaySettings(target.value, miniDisplaySettings);
                }
                case TOGGLE_MINI_DISPLAY_LIT -> {
                    this.playPanelClick(1.0f);
                    final MiniDisplaySettings miniDisplaySettings = this.copyMiniDisplaySettings(target.value);
                    if (miniDisplaySettings != null) {
                        miniDisplaySettings.setLit(!miniDisplaySettings.isLit());
                    }
                    return this.sendMiniDisplaySettings(target.value, miniDisplaySettings);
                }
                case TOGGLE_MINI_DISPLAY_LABELS -> {
                    this.playPanelClick(1.0f);
                    final MiniDisplaySettings miniDisplaySettings = this.copyMiniDisplaySettings(target.value);
                    if (miniDisplaySettings != null) {
                        miniDisplaySettings.setShowLabels(!miniDisplaySettings.isShowLabels());
                    }
                    return this.sendMiniDisplaySettings(target.value, miniDisplaySettings);
                }
                case HIDE_DONE_ORDERS -> {
                    this.playPanelClick(0.95f);
                    return this.sendFactoryFreightAction(target.value, DeliveryOrderAction.HIDE_COMPLETED);
                }
                case SHOW_HIDDEN_ORDERS -> {
                    this.playPanelClick(1.0f);
                    return this.sendFactoryFreightAction(target.value, DeliveryOrderAction.SHOW_HIDDEN);
                }
                case RESET_DONE_ORDERS -> {
                    this.playPanelClick(0.9f);
                    return this.sendFactoryFreightAction(target.value, DeliveryOrderAction.RESET_COMPLETED);
                }
            }
        }
        return false;
    }

    private boolean handleFactoryPageScroll(double mx, double my, double delta) {
        if (!this.isFactoryPage()) {
            return false;
        }
        if (mx < this.graphLeft || mx >= this.graphLeft + this.graphWidth || my < this.graphTop || my >= this.graphTop + this.graphHeight) {
            return false;
        }
        final List<StatsDisplayPanelBE.SafetyClutchLink> safetyLinks = this.getFactoryClutchLinks();
        final List<StatsDisplayPanelBE.KineticFlywheelLink> flywheelLinks = this.getFactoryFlywheelLinks();
        final List<StatsDisplayPanelBE.AutoTransmissionClutchLink> transLinks = this.getFactoryTransmissionLinks();
        final List<StatsDisplayPanelBE.LoadBalancerLink> balancerLinks = this.getFactoryLoadBalancerLinks();
        final List<StatsDisplayPanelBE.MiniDisplayLink> miniDisplayLinks = this.getFactoryMiniDisplayLinks();
        final List<StatsDisplayPanelBE.FreightManifestLink> manifestLinks = this.getFactoryManifestLinks();
        final List<StatsDisplayPanelBE.TrainStationLink> stationLinks = this.getFactoryTrainStationLinks();
        final List<FactoryFreightEntry> freightEntries = this.getFactoryFreightEntries(manifestLinks, stationLinks);
        final int listH = this.graphHeight - 24;
        final int visibleRows = Math.max(1, listH / 22);
        final int maxScroll = Math.max(0, this.getFactoryListRowCount(safetyLinks, flywheelLinks, transLinks, balancerLinks, miniDisplayLinks, freightEntries) - visibleRows);
        if (maxScroll <= 0) {
            return false;
        }
        this.factoryClutchScroll = Math.max(0, Math.min(this.factoryClutchScroll - (int)delta, maxScroll));
        return true;
    }

    private int countCompletedManifestGoals(Map<FreightManifestResourceKey, Integer> expected, TrainCargoSnapshot actual) {
        int complete = 0;
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : expected.entrySet()) {
            if (actual.getAmount(entry.getKey()) >= entry.getValue()) {
                complete++;
            }
        }
        return complete;
    }

    private List<ManifestProgressLine> buildManifestProgressLines(List<FreightManifestLine> lines, FreightStationCargoView cargoView) {
        final List<ManifestProgressLine> progressLines = new ArrayList<ManifestProgressLine>();
        final Map<FreightManifestResourceKey, Integer> remainingToStation = new LinkedHashMap<FreightManifestResourceKey, Integer>(cargoView.getMovedToStation().getAmounts());
        final Map<FreightManifestResourceKey, Integer> remainingToTrain = new LinkedHashMap<FreightManifestResourceKey, Integer>(cargoView.getMovedToTrain().getAmounts());
        for (FreightManifestLine line : lines) {
            final FreightManifestResourceKey key = line.getResourceKey();
            if (key == null || !line.hasSelection() || line.getAmount() <= 0) {
                progressLines.add(new ManifestProgressLine(line, 0, false));
                continue;
            }
            final Map<FreightManifestResourceKey, Integer> remaining = line.getOperation() == ManifestOperation.UNLOAD ? remainingToStation : remainingToTrain;
            final int available = remaining.getOrDefault(key, 0);
            final int applied = Math.min(available, line.getAmount());
            if (applied > 0) {
                remaining.put(key, available - applied);
            }
            progressLines.add(new ManifestProgressLine(line, applied, true));
        }
        return progressLines;
    }

    private String formatManifestAmount(FreightManifestLine line) {
        return line.getAmount() + (line.getCargoKind() == com.createstats.train.manifest.ManifestCargoKind.FLUID ? "mB" : "");
    }

    private boolean handleHomePageClick(double mx, double my) {
        for (int i = this.homeClickTargets.size() - 1; i >= 0; --i) {
            final HomeClickTarget target = this.homeClickTargets.get(i);
            if (!target.contains(mx, my)) {
                continue;
            }
            switch (target.action) {
                case FILTER_TYPE -> {
                    this.homeSourceTypeFilter = (target.value != null) ? target.value : "";
                    this.homeFocusedSourceId = "";
                    this.homeLeftScroll = 0;
                    return true;
                }
                case FOCUS_SOURCE -> {
                    final String sourceId = (target.value != null) ? target.value : "";
                    final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
                    if (config != null) {
                        final String sourceType = this.normalizeSourceTypeLabel(config.getSourceType());
                        if (!sourceType.equals(this.homeSourceTypeFilter)) {
                            this.homeLeftScroll = 0;
                        }
                        this.homeSourceTypeFilter = sourceType;
                    }
                    this.homeFocusedSourceId = sourceId;
                    return true;
                }
                case INSPECT_OUTPUT -> {
                    this.openHomeInspectOutput();
                    return true;
                }
                case TOGGLE_SOURCE -> {
                    this.toggleHomeSourceSelection(target.value);
                    return true;
                }
                case SET_PRIMARY_SOURCE -> {
                    this.setHomePrimarySource(target.value);
                    return true;
                }
                case CLEAR_PRIMARY_SOURCE -> {
                    this.clearHomePrimarySource();
                    return true;
                }
                case RENAME_SOURCE -> {
                    final String sourceId = (target.value != null) ? target.value : "";
                    final SourceConfig config = this.panel.getSourceRegistry().get(sourceId);
                    if (config != null) {
                        this.openSourceRename(sourceId, config, target.x, target.y + target.h + 4);
                        return true;
                    }
                    return false;
                }
                case TOGGLE_CHANNEL -> {
                    this.toggleHomeChannelSelection(target.value);
                    return true;
                }
                case SOLO_CHANNEL -> {
                    this.soloHomeChannel(target.value);
                    return true;
                }
                case SET_MODE -> {
                    if (target.outputMode != null) {
                        this.setHomeOutputMode(target.outputMode);
                    }
                    return true;
                }
                case OPEN_INVENTORY_STATUS -> {
                    this.setPanelOutputGraphType(StatsDisplaySettings.GraphType.INVENTORY_STATUS);
                    return true;
                }
                case OPEN_FACTORY_STATUS -> {
                    this.setPanelOutputGraphType(StatsDisplaySettings.GraphType.FACTORY_STATUS);
                    return true;
                }
                case SET_GRAPH_TYPE -> {
                    if (target.graphType != null) {
                        this.setPanelOutputGraphType(target.graphType);
                    }
                    return true;
                }
                case SET_DASH_MAIN -> {
                    this.setHomeDashboardMainChannel(target.value);
                    return true;
                }
                case CYCLE_RANGE -> {
                    this.panel.getSettings().cycleTimeRange();
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
                case CYCLE_SPEED -> {
                    if (this.homeCycleSupported()) {
                        this.cyclePanelCycleSpeed();
                    }
                    return true;
                }
                case TOGGLE_AUTO_SCALE -> {
                    if (this.homeAutoScaleSupported()) {
                        this.togglePanelAutoScale();
                    }
                    return true;
                }
                case CYCLE_THEME -> {
                    this.cyclePanelDisplayTheme();
                    return true;
                }
                case CYCLE_STYLE -> {
                    if (this.homeUsesInstrumentAppearance()) {
                        this.cyclePanelInstrumentStyle();
                    }
                    return true;
                }
                case CYCLE_DENSITY -> {
                    if (this.homeUsesInstrumentAppearance()) {
                        this.cyclePanelInstrumentDensity();
                    }
                    return true;
                }
                case CYCLE_EFFECT -> {
                    if (this.homeUsesInstrumentAppearance()) {
                        this.cyclePanelInstrumentEffect();
                    }
                    return true;
                }
                case TOGGLE_ANALOG_ZONES -> {
                    this.panel.getSettings().setAnalogShowZones(!this.panel.getSettings().isAnalogShowZones());
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
                case TOGGLE_ANALOG_GLASS -> {
                    this.panel.getSettings().setAnalogShowGlass(!this.panel.getSettings().isAnalogShowGlass());
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
                case TOGGLE_ANALOG_RIVETS -> {
                    this.panel.getSettings().setAnalogShowRivets(!this.panel.getSettings().isAnalogShowRivets());
                    this.applyPanelSettingsChange();
                    this.rebuildWidgets();
                    return true;
                }
                case TOGGLE_PREDICTION -> {
                    this.togglePrediction();
                    return true;
                }
                case OPEN_SCANNER -> {
                    this.openScannerPopup();
                    return true;
                }
                case EXPORT_SUMMARY -> {
                    this.exportToClipboard(false);
                    return true;
                }
                case EXPORT_CSV -> {
                    this.exportToClipboard(true);
                    return true;
                }
            }
        }
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        if (this.handleHomeTrainPreviewClick(mx, my, visibleData)) {
            return true;
        }
        if (this.handleHomeStationPreviewClick(mx, my, visibleData)) {
            return true;
        }
        return false;
    }

    private boolean isWithinHomePreview(double mx, double my, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return false;
        }
        final int clipTop = this.homeLeftPaneY + 30;
        final int clipBottom = this.homeLeftPaneY + this.homeLeftPaneH - 6;
        final int top = Math.max(y, clipTop);
        final int bottom = Math.min(y + h, clipBottom);
        return bottom > top && mx >= x && mx < x + w && my >= top && my < bottom;
    }

    private boolean handleHomeTrainPreviewClick(double mx, double my, TrainMapData visibleData) {
        if (visibleData == null || visibleData.isEmpty() || !this.isWithinHomePreview(mx, my, this.homeTrainPreviewX, this.homeTrainPreviewY, this.homeTrainPreviewW, this.homeTrainPreviewH)) {
            return false;
        }
        final int listY = this.homeTrainPreviewY + 28;
        final int listH = this.homeTrainPreviewH - 30;
        final int clipTop = this.homeLeftPaneY + 30;
        final int clipBottom = this.homeLeftPaneY + this.homeLeftPaneH - 6;
        final int top = Math.max(listY, clipTop);
        final int bottom = Math.min(listY + listH, clipBottom);
        if (my < top || my >= bottom) {
            return false;
        }
        final List<TrainMapData.TrainEntry> trains = this.getHomePreviewTrains(visibleData);
        final int index = (int)((my - listY) / 16.0) + this.homeTrainPreviewScroll;
        if (index < 0 || index >= trains.size()) {
            return false;
        }
        final TrainMapData.TrainEntry train = trains.get(index);
        this.openTrainPageSelection(train.id, train.currentStationId != null ? train.currentStationId : this.findTrainDashboardStationByName(visibleData, train.currentStationName));
        return true;
    }

    private boolean handleHomeStationPreviewClick(double mx, double my, TrainMapData visibleData) {
        if (visibleData == null || visibleData.isEmpty() || !this.isWithinHomePreview(mx, my, this.homeStationPreviewX, this.homeStationPreviewY, this.homeStationPreviewW, this.homeStationPreviewH)) {
            return false;
        }
        final int listY = this.homeStationPreviewY + 28;
        final int listH = this.homeStationPreviewH - 30;
        final int clipTop = this.homeLeftPaneY + 30;
        final int clipBottom = this.homeLeftPaneY + this.homeLeftPaneH - 6;
        final int top = Math.max(listY, clipTop);
        final int bottom = Math.min(listY + listH, clipBottom);
        if (my < top || my >= bottom) {
            return false;
        }
        final List<TrainMapData.StationEntry> stations = this.getHomePreviewStations(visibleData);
        final int index = (int)((my - listY) / 16.0) + this.homeStationPreviewScroll;
        if (index < 0 || index >= stations.size()) {
            return false;
        }
        final TrainMapData.StationEntry station = stations.get(index);
        this.openTrainPageSelection(null, station.id);
        return true;
    }

    private boolean handleHomeTrainPreviewScroll(double mx, double my, double delta, TrainMapData visibleData) {
        if (delta == 0.0 || visibleData == null || visibleData.isEmpty() || !this.isWithinHomePreview(mx, my, this.homeTrainPreviewX, this.homeTrainPreviewY, this.homeTrainPreviewW, this.homeTrainPreviewH)) {
            return false;
        }
        final int visible = Math.max(1, (this.homeTrainPreviewH - 30) / 16);
        final int maxScroll = Math.max(0, this.getHomePreviewTrains(visibleData).size() - visible);
        if (maxScroll <= 0) {
            return false;
        }
        final int previewStep = (delta > 0.0) ? -1 : 1;
        this.homeTrainPreviewScroll = Math.max(0, Math.min(this.homeTrainPreviewScroll + previewStep, maxScroll));
        return true;
    }

    private boolean handleHomeStationPreviewScroll(double mx, double my, double delta, TrainMapData visibleData) {
        if (delta == 0.0 || visibleData == null || visibleData.isEmpty() || !this.isWithinHomePreview(mx, my, this.homeStationPreviewX, this.homeStationPreviewY, this.homeStationPreviewW, this.homeStationPreviewH)) {
            return false;
        }
        final int visible = Math.max(1, (this.homeStationPreviewH - 30) / 16);
        final int maxScroll = Math.max(0, this.getHomePreviewStations(visibleData).size() - visible);
        if (maxScroll <= 0) {
            return false;
        }
        final int previewStep = (delta > 0.0) ? -1 : 1;
        this.homeStationPreviewScroll = Math.max(0, Math.min(this.homeStationPreviewScroll + previewStep, maxScroll));
        return true;
    }

    private boolean handleHomePageScroll(double mx, double my, double delta) {
        if (delta == 0.0) {
            return false;
        }
        final TrainMapData visibleData = this.getTrainDashboardVisibleData();
        if (this.handleHomeTrainPreviewScroll(mx, my, delta, visibleData)) {
            return true;
        }
        if (this.handleHomeStationPreviewScroll(mx, my, delta, visibleData)) {
            return true;
        }
        final int scrollStep = (delta > 0.0) ? -18 : 18;
        if (mx >= this.homeLeftPaneX && mx < this.homeLeftPaneX + this.homeLeftPaneW && my >= this.homeLeftPaneY && my < this.homeLeftPaneY + this.homeLeftPaneH) {
            final int maxScroll = Math.max(0, this.homeLeftContentHeight - Math.max(1, this.homeLeftPaneH - 36));
            if (maxScroll <= 0) {
                return false;
            }
            this.homeLeftScroll = Math.max(0, Math.min(this.homeLeftScroll + scrollStep, maxScroll));
            return true;
        }
        if (mx >= this.homeRightPaneX && mx < this.homeRightPaneX + this.homeRightPaneW && my >= this.homeRightPaneY && my < this.homeRightPaneY + this.homeRightPaneH) {
            final int maxScroll2 = Math.max(0, this.homeRightContentHeight - Math.max(1, this.homeRightPaneH - 36));
            if (maxScroll2 <= 0) {
                return false;
            }
            this.homeRightScroll = Math.max(0, Math.min(this.homeRightScroll + scrollStep, maxScroll2));
            return true;
        }
        return false;
    }

    private String formatYAxis(float val, float range) {
        if (range < 0.01f) {
            return String.format("%.4f", val);
        }
        if (range < 0.1f) {
            return String.format("%.3f", val);
        }
        if (range < 1.0f) {
            return String.format("%.2f", val);
        }
        if (range < 10.0f) {
            return String.format("%.1f", val);
        }
        if (range < 100.0f) {
            return String.format("%.0f", val);
        }
        final float absVal = Math.abs(val);
        final float relRange = range / Math.max(absVal, 1.0f);
        if (absVal >= 1000000.0f) {
            if (relRange < 0.01f) {
                return String.format("%.3fM", val / 1000000.0f);
            }
            if (relRange < 0.1f) {
                return String.format("%.2fM", val / 1000000.0f);
            }
            return String.format("%.1fM", val / 1000000.0f);
        }
        else {
            if (absVal < 1000.0f) {
                return String.format("%.0f", val);
            }
            if (relRange < 0.01f) {
                return String.format("%.2fk", val / 1000.0f);
            }
            if (relRange < 0.1f) {
                return String.format("%.1fk", val / 1000.0f);
            }
            return String.format("%.0fk", val / 1000.0f);
        }
    }

    private static int brighten(int c, float f) {
        final int a = c >> 24 & 0xFF;
        final int r = Math.min(255, (int)((c >> 16 & 0xFF) * f));
        final int gg = Math.min(255, (int)((c >> 8 & 0xFF) * f));
        final int b = Math.min(255, (int)((c & 0xFF) * f));
        return a << 24 | r << 16 | gg << 8 | b;
    }

    private static int darken(int c, float f) {
        return brighten(c, f);
    }

    private static int opaque(int c) {
        return 0xFF000000 | (c & 0x00FFFFFF);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        final int a = (int)((c1 >> 24 & 0xFF) * (1.0f - t) + (c2 >> 24 & 0xFF) * t);
        final int r = (int)((c1 >> 16 & 0xFF) * (1.0f - t) + (c2 >> 16 & 0xFF) * t);
        final int gg = (int)((c1 >> 8 & 0xFF) * (1.0f - t) + (c2 >> 8 & 0xFF) * t);
        final int b = (int)((c1 & 0xFF) * (1.0f - t) + (c2 & 0xFF) * t);
        return a << 24 | r << 16 | gg << 8 | b;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.annotationInputOpen) {
            if (keyCode == 257) {
                this.confirmAnnotation();
                return true;
            }
            if (keyCode == 256) {
                this.annotationInputOpen = false;
                this.annotateMode = false;
                this.editingAnnotationId = null;
                this.rebuildWidgets();
                return true;
            }
            if (this.annotationLabelInput != null && this.annotationLabelInput.isFocused()) {
                return this.annotationLabelInput.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        if (keyCode != 256) {
            if (this.sourceRenameInput != null && this.sourceRenameInput.isFocused()) {
                if (keyCode == 257) {
                    this.applySourceRename();
                    return true;
                }
                return this.sourceRenameInput.keyPressed(keyCode, scanCode, modifiers);
            }
            else if (this.redstoneThresholdInput != null && this.redstoneThresholdInput.isFocused()) {
                if (keyCode == 257) {
                    this.applyRedstoneThreshold();
                    return true;
                }
                return this.redstoneThresholdInput.keyPressed(keyCode, scanCode, modifiers);
            }
            else if (this.manualYAxisMinInput != null && this.manualYAxisMinInput.isFocused()) {
                if (keyCode == 257) {
                    this.applyManualYAxisInputs();
                    return true;
                }
                return this.manualYAxisMinInput.keyPressed(keyCode, scanCode, modifiers);
            }
            else if (this.manualYAxisMaxInput != null && this.manualYAxisMaxInput.isFocused()) {
                if (keyCode == 257) {
                    this.applyManualYAxisInputs();
                    return true;
                }
                return this.manualYAxisMaxInput.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        if (keyCode == 256) {
            if (this.sourceRenameOpen) {
                this.applySourceRename();
                return true;
            }
            if (this.redstoneConfigOpen) {
                this.applyRedstoneThreshold();
                this.redstoneConfigOpen = false;
                this.redstoneValueInput = "";
                this.rebuildWidgets();
                return true;
            }
            if (!this.pinnedChannels.isEmpty()) {
                this.pinnedChannels.clear();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        if (this.annotationLabelInput != null && this.annotationLabelInput.isFocused()) {
            return this.annotationLabelInput.charTyped(c, modifiers);
        }
        if (this.sourceRenameInput != null && this.sourceRenameInput.isFocused()) {
            return this.sourceRenameInput.charTyped(c, modifiers);
        }
        if (this.redstoneThresholdInput != null && this.redstoneThresholdInput.isFocused()) {
            return this.redstoneThresholdInput.charTyped(c, modifiers);
        }
        if (this.manualYAxisMinInput != null && this.manualYAxisMinInput.isFocused()) {
            return this.manualYAxisMinInput.charTyped(c, modifiers);
        }
        if (this.manualYAxisMaxInput != null && this.manualYAxisMaxInput.isFocused()) {
            return this.manualYAxisMaxInput.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private void handleAnnotationClick(double mx, double my) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (channels.isEmpty()) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel ch = (this.selectedTab >= 0 && this.selectedTab < channels.size()) ? channels.get(this.selectedTab) : channels.get(0);
        final TimeSeriesStore store = ch.getStore();
        if (store.getSampleCount() < 2) {
            return;
        }
        final Annotation nearAnno = this.findAnnotationAtPixel((int)mx);
        if (nearAnno == null) {
            double normalizedX = (mx - this.graphLeft) / this.graphWidth;
            normalizedX = Math.max(0.0, Math.min(1.0, normalizedX));
            long windowEnd = store.getLastSampleTick();
            long windowStart = store.getOldestSampleTick();
            if (windowEnd <= 0L) {
                final long currentTick = windowEnd = ((this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L);
            }
            if (windowStart <= 0L) {
                final float[] data = (this.timeScale > 0) ? store.getDataForTimeScale(this.timeScale) : store.getData();
                windowStart = windowEnd - (data.length - 1) * (long)store.getSampleIntervalTicks();
            }
            final long span = windowEnd - windowStart;
            final long annotationTick = windowStart + (long)(normalizedX * span);
            this.openAnnotationInput((int)mx, (int)my, annotationTick);
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.lastAnnotationClickId != null && this.lastAnnotationClickId.equals(nearAnno.getId()) && now - this.lastAnnotationClickTime < 500L) {
            this.editingAnnotationId = nearAnno.getId();
            this.annotationInputOpen = true;
            this.annotationInputX = (int)mx;
            this.annotationInputY = (int)my;
            this.selectedAnnotationColor = nearAnno.getColor();
            this.rebuildWidgets();
            if (this.annotationLabelInput != null) {
                this.annotationLabelInput.setValue(nearAnno.getLabel());
            }
            this.lastAnnotationClickId = null;
            return;
        }
        this.lastAnnotationClickId = nearAnno.getId();
        this.lastAnnotationClickTime = now;
    }

    private void openAnnotationInput(int clickX, int clickY, long tick) {
        this.pendingAnnotationTick = tick;
        this.annotationInputX = clickX;
        this.annotationInputY = clickY;
        this.selectedAnnotationColor = this.panel.getSettings().getCustomAccentColor();
        if (this.selectedAnnotationColor == 0) {
            this.selectedAnnotationColor = StatsDisplayScreen.ANNOTATION_COLOR_PRESETS[0];
        }
        this.annotationInputOpen = true;
        this.editingAnnotationId = null;
        this.rebuildWidgets();
    }

    private void confirmAnnotation() {
        if (this.annotationLabelInput == null) {
            return;
        }
        String label = this.annotationLabelInput.getValue().trim();
        if (label.isEmpty()) {
            this.annotationInputOpen = false;
            this.annotateMode = false;
            this.editingAnnotationId = null;
            this.rebuildWidgets();
            return;
        }
        if (label.length() > 48) {
            label = label.substring(0, 48);
        }
        if (this.editingAnnotationId != null) {
            PacketDistributor.sendToServer(AnnotationActionPacket.edit(this.panel.getBlockPos(), this.editingAnnotationId, label, this.selectedAnnotationColor));
            this.panel.getAnnotationManager().edit(this.editingAnnotationId, label, this.selectedAnnotationColor);
        }
        else {
            PacketDistributor.sendToServer(AnnotationActionPacket.add(this.panel.getBlockPos(), this.pendingAnnotationTick, label, this.selectedAnnotationColor));
            final UUID localId = UUID.randomUUID();
            final Minecraft mc = Minecraft.getInstance();
            final UUID playerUUID = (mc.player != null) ? mc.player.getUUID() : UUID.randomUUID();
            final Annotation local = new Annotation(localId, this.pendingAnnotationTick, this.pendingAnnotationTick, label, playerUUID, this.selectedAnnotationColor, Annotation.AnnotationType.MARKER);
            this.panel.getAnnotationManager().add(local);
        }
        DisplayTextureManager.markDirty(this.panel.getBlockPos());
        this.annotationInputOpen = false;
        this.annotateMode = false;
        this.editingAnnotationId = null;
        this.rebuildWidgets();
    }

    private Annotation findAnnotationAtPixel(int pixelX) {
        final List<StatsDisplayPanelBE.DataChannel> channels = this.getFilteredChannels();
        if (channels.isEmpty()) {
            return null;
        }
        final StatsDisplayPanelBE.DataChannel ch = (this.selectedTab >= 0 && this.selectedTab < channels.size()) ? channels.get(this.selectedTab) : channels.get(0);
        final TimeSeriesStore store = ch.getStore();
        if (store.getSampleCount() < 2) {
            return null;
        }
        long windowEndTick = store.getLastSampleTick();
        long windowStartTick = store.getOldestSampleTick();
        if (windowEndTick <= 0L) {
            final long currentTick = windowEndTick = ((this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L);
        }
        if (windowStartTick <= 0L) {
            final float[] data = (this.timeScale > 0) ? store.getDataForTimeScale(this.timeScale) : store.getData();
            windowStartTick = windowEndTick - (data.length - 1) * (long)store.getSampleIntervalTicks();
        }
        final long span = windowEndTick - windowStartTick;
        if (span <= 0L) {
            return null;
        }
        final List<Annotation> visible = this.panel.getAnnotationManager().getInRange(windowStartTick, windowEndTick);
        for (Annotation a : visible) {
            final double normX = (a.getTickStamp() - windowStartTick) / (double)span;
            final int annotPixelX = this.graphLeft + (int)(normX * this.graphWidth);
            if (Math.abs(pixelX - annotPixelX) <= 3) {
                return a;
            }
        }
        return null;
    }

    private void renderAnnotationsGUI(GuiGraphics g, List<StatsDisplayPanelBE.DataChannel> channels, int mouseX, int mouseY) {
        final AnnotationManager mgr = this.panel.getAnnotationManager();
        if (mgr.size() == 0 && !this.annotateMode) {
            return;
        }
        if (channels.isEmpty()) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel ch = (this.selectedTab >= 0 && this.selectedTab < channels.size()) ? channels.get(this.selectedTab) : channels.get(0);
        final TimeSeriesStore store = ch.getStore();
        if (store.getSampleCount() < 2) {
            if (this.annotateMode && mouseX >= this.graphLeft && mouseX <= this.graphLeft + this.graphWidth && mouseY >= this.graphTop && mouseY < this.graphTop + this.graphHeight) {
                for (int py = this.graphTop; py < this.graphTop + this.graphHeight; py += 2) {
                    g.fill(mouseX, py, mouseX + 1, py + 1, -1996488705);
                }
            }
            return;
        }
        long windowEndTick = store.getLastSampleTick();
        long windowStartTick = store.getOldestSampleTick();
        if (windowEndTick <= 0L) {
            final long currentTick = windowEndTick = ((this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L);
        }
        if (windowStartTick <= 0L) {
            final float[] data = (this.timeScale > 0) ? store.getDataForTimeScale(this.timeScale) : store.getData();
            windowStartTick = windowEndTick - (data.length - 1) * (long)store.getSampleIntervalTicks();
        }
        final long windowSpan = windowEndTick - windowStartTick;
        if (windowSpan <= 0L) {
            return;
        }
        final List<Annotation> visible = mgr.getInRange(windowStartTick, windowEndTick);
        final List<int[]> labelSlots = new ArrayList<int[]>();
        Objects.requireNonNull(this.font);
        final int labelH = 9 + 2;
        final int maxRows = Math.max(1, this.graphHeight / 4 / labelH);
        Annotation hoveredAnnotation = null;
        final int dashLen = 4;
        final int gapLen = 3;
        for (Annotation a : visible) {
            final double normX = (a.getTickStamp() - windowStartTick) / (double)windowSpan;
            final int px = this.graphLeft + (int)(normX * this.graphWidth);
            if (px >= this.graphLeft) {
                if (px > this.graphLeft + this.graphWidth) {
                    continue;
                }
                for (int py2 = this.graphTop; py2 < this.graphTop + this.graphHeight; ++py2) {
                    final int phase = (py2 - this.graphTop) % (dashLen + gapLen);
                    if (phase < dashLen) {
                        g.fill(px, py2, px + 1, py2 + 1, a.getColor());
                    }
                }
                if (a.getType() == Annotation.AnnotationType.REGION) {
                    final double normX2 = (a.getEndTickStamp() - windowStartTick) / (double)windowSpan;
                    int px2 = this.graphLeft + (int)(normX2 * this.graphWidth);
                    px2 = Math.max(this.graphLeft, Math.min(this.graphLeft + this.graphWidth, px2));
                    for (int py3 = this.graphTop; py3 < this.graphTop + this.graphHeight; ++py3) {
                        final int phase2 = (py3 - this.graphTop) % (dashLen + gapLen);
                        if (phase2 < dashLen) {
                            g.fill(px2, py3, px2 + 1, py3 + 1, a.getColor());
                        }
                    }
                    final int left = Math.min(px, px2);
                    final int right = Math.max(px, px2);
                    final int regionColor = 0x22000000 | (a.getColor() & 0xFFFFFF);
                    g.fill(left, this.graphTop, right, this.graphTop + this.graphHeight, regionColor);
                }
                int row = 0;
                for (int[] prev : labelSlots) {
                    if (Math.abs(px - prev[0]) < 50 && row == prev[1] && ++row >= maxRows) {
                        row = maxRows - 1;
                    }
                }
                labelSlots.add(new int[] { px, row });
                final int labelY = this.graphTop + 2 + row * labelH;
                String displayLabel = a.getLabel();
                final int maxLabelW = this.graphLeft + this.graphWidth - px - 4;
                if (this.font.width(displayLabel) > maxLabelW) {
                    displayLabel = this.truncate(displayLabel, maxLabelW);
                }
                final int textW = this.font.width(displayLabel);
                final int n = px + 1;
                final int n2 = labelY - 1;
                final int n3 = px + textW + 3;
                final int n4 = labelY;
                Objects.requireNonNull(this.font);
                g.fill(n, n2, n3, n4 + 9 + 1, -872415232);
                g.drawString(this.font, displayLabel, px + 2, labelY, a.getColor(), false);
                if (Math.abs(mouseX - px) > 3 || mouseY < this.graphTop || mouseY >= this.graphTop + this.graphHeight) {
                    continue;
                }
                hoveredAnnotation = a;
            }
        }
        if (hoveredAnnotation != null) {
            this.renderAnnotationTooltip(g, hoveredAnnotation, mouseX, mouseY, windowStartTick, windowEndTick);
        }
        if (this.annotateMode && mouseX >= this.graphLeft && mouseX <= this.graphLeft + this.graphWidth && mouseY >= this.graphTop && mouseY < this.graphTop + this.graphHeight) {
            for (int py4 = this.graphTop; py4 < this.graphTop + this.graphHeight; py4 += 2) {
                g.fill(mouseX, py4, mouseX + 1, py4 + 1, -1996488705);
            }
        }
    }

    private void renderAnnotationTooltip(GuiGraphics g, Annotation a, int mouseX, int mouseY, long windowStartTick, long windowEndTick) {
        final List<Component> lines = new ArrayList<Component>();
        lines.add(Component.literal("\ud83d\udccc " + a.getLabel()).withStyle(style -> style.withColor(a.getColor() & 0xFFFFFF)));
        final long currentTick = (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L;
        final long ticksAgo = currentTick - a.getTickStamp();
        final int secondsAgo = (int)(ticksAgo / 20L);
        String timeStr;
        if (secondsAgo < 60) {
            timeStr = secondsAgo + "s ago";
        }
        else if (secondsAgo < 3600) {
            timeStr = secondsAgo / 60 + "m " + secondsAgo % 60 + "s ago";
        }
        else {
            timeStr = secondsAgo / 3600 + "h " + secondsAgo % 3600 / 60 + "m ago";
        }
        lines.add(Component.literal("Time: " + timeStr).withStyle(style -> style.withColor(11184810)));
        if (a.getType() == Annotation.AnnotationType.REGION) {
            final long durationTicks = a.getEndTickStamp() - a.getTickStamp();
            final int durationSec = (int)(durationTicks / 20L);
            lines.add(Component.literal("Duration: " + durationSec).withStyle(style -> style.withColor(11184810)));
        }
        lines.add(Component.literal("By: " + a.getPlayerUUID().toString().substring(0, 8) + "...").withStyle(style -> style.withColor(8947848)));
        lines.add(Component.literal("Right-click to delete").withStyle(style -> style.withColor(6710886).withItalic(Boolean.valueOf(true))));
        g.renderTooltip(this.font, (List)lines, (Optional)Optional.empty(), mouseX, mouseY);
    }

    static {
        TIME_SCALES = new int[] { 15, 30, 60, 120, 300, 600, 1800, 0 };
        TIME_SCALE_LABELS = new String[] { "15s", "30s", "1m", "2m", "5m", "10m", "30m", "All" };
        ANNOTATION_COLOR_PRESETS = new int[] { -48060, -22016, -188, -12255352, -12276993, -3390209 };
        SPLASH_QUOTES = new String[] { "Your kinetic network has a lot to say.", "Built something beautiful? Now measure it.", "Because great builds deserve great data.", "You built the machine. Now see it breathe.", "The heartbeat of your kinetic network, on display.", "For the ones who debug their redstone at 3 AM.", "Because eyeballing RPM was never enough.", "If you build it, you should measure it.", "Monitor everything. Regret nothing.", "Your contraptions are art. This is the gallery label.", "Your factory has feelings. Here's the proof.", "Finally, a reason to stare at your factory wall.", "You didn't build that for nothing. Prove it.", "Your network is alive. Watch it pulse.", "Efficiency isn't a feeling. It's a number.", "What happens after you place the last gear.", "Some people build. Some people measure. You do both." };
        CHANNEL_COLORS = new int[] { -16711800, -12276993, -39356, -13312, -47873, -12255284, -30652, -7798972, -7829249, -30516, -12255420, -3372801 };
        TRAIN_ROUTE_PALETTE = new int[] { -11549705, -36797, -10044566, -5552196, -13784, -1092784, -14235942, -1294214, -7508381, -8875876, -22746, -10720320 };
    }

    private static class FactoryStatusEntry
    {
        final String group;
        final String label;
        final String sourceInfo;
        final String primary;
        final String secondary;
        final float percent;
        final boolean hasPercent;
        final int statusColor;
        final float sortValue;

        FactoryStatusEntry(String group, String label, String sourceInfo, String primary, String secondary,
                           float percent, boolean hasPercent, int statusColor, float sortValue) {
            this.group = group;
            this.label = label;
            this.sourceInfo = sourceInfo;
            this.primary = primary;
            this.secondary = secondary;
            this.percent = percent;
            this.hasPercent = hasPercent;
            this.statusColor = statusColor;
            this.sortValue = sortValue;
        }
    }

    private static class InventoryStableEntry
    {
        final String label;
        final float amount;
        final boolean hasAmount;
        final String sourceType;

        InventoryStableEntry(String label, float amount, boolean hasAmount, String sourceType) {
            this.label = label;
            this.amount = amount;
            this.hasAmount = hasAmount;
            this.sourceType = sourceType;
        }

        String displayText() {
            final String amountText = this.hasAmount ? (" x" + String.format("%.0f", this.amount)) : "";
            final String sourceText = (this.sourceType == null || this.sourceType.isBlank()) ? "" : (" - " + this.sourceType);
            return this.label + amountText + sourceText;
        }
    }

    private static class InventoryStableAccumulator
    {
        final String label;
        float amount;
        boolean hasAmount;
        final LinkedHashSet<String> sourceTypes;

        InventoryStableAccumulator(String label) {
            this.label = label;
            this.sourceTypes = new LinkedHashSet<String>();
        }

        void addAmount(float value, String sourceType) {
            this.amount += value;
            this.hasAmount = true;
            this.addSource(sourceType);
        }

        void addSource(String sourceType) {
            if (sourceType != null && !sourceType.isBlank()) {
                this.sourceTypes.add(sourceType);
            }
        }

        InventoryStableEntry toEntry() {
            final String sourceType = this.sourceTypes.isEmpty()
                    ? ""
                    : (this.sourceTypes.size() == 1 ? this.sourceTypes.iterator().next() : "Merged");
            return new InventoryStableEntry(this.label, this.amount, this.hasAmount, sourceType);
        }
    }

    private static class ProductionEntry
    {
        final StatsDisplayPanelBE.DataChannel channel;
        final String label;
        final float rate;
        final float trend;
        final String sourceType;
        final int contributorCount;

        ProductionEntry(StatsDisplayPanelBE.DataChannel channel, String label, float rate, float trend, String sourceType, int contributorCount) {
            this.channel = channel;
            this.label = label;
            this.rate = rate;
            this.trend = trend;
            this.sourceType = sourceType;
            this.contributorCount = contributorCount;
        }
    }

    private static class ProductionRowHit {
        final ProductionEntry entry;
        final int yMin, yMax;
        ProductionRowHit(ProductionEntry entry, int yMin, int yMax) {
            this.entry = entry; this.yMin = yMin; this.yMax = yMax;
        }
    }

    private static class InventoryStorageRowHit {
        final String sourceId;
        final int yMin, yMax;
        InventoryStorageRowHit(String sourceId, int yMin, int yMax) {
            this.sourceId = sourceId; this.yMin = yMin; this.yMax = yMax;
        }
    }

    private static class CapacitySourceInfo {
        final String sourceId, displayLabel, sourceTypeRaw, usedSuffix;
        final float used, total, pct;
        final MetricType amountType;
        final TimeSeriesStore pctStore;
        final boolean hasCapacity;
        final boolean fuelReserve;
        String subLine;
        CapacitySourceInfo(String sourceId, String displayLabel, String sourceTypeRaw, float used, float total, float pct,
                MetricType amountType, String usedSuffix, TimeSeriesStore pctStore, boolean hasCapacity) {
            this(sourceId, displayLabel, sourceTypeRaw, used, total, pct, amountType, usedSuffix, pctStore, hasCapacity, false);
        }

        CapacitySourceInfo(String sourceId, String displayLabel, String sourceTypeRaw, float used, float total, float pct,
                MetricType amountType, String usedSuffix, TimeSeriesStore pctStore, boolean hasCapacity, boolean fuelReserve) {
            this.sourceId = sourceId; this.displayLabel = displayLabel; this.sourceTypeRaw = sourceTypeRaw;
            this.used = used; this.total = total; this.pct = pct;
            this.amountType = amountType; this.usedSuffix = usedSuffix; this.pctStore = pctStore; this.hasCapacity = hasCapacity;
            this.fuelReserve = fuelReserve;
        }
    }

    private static class ProductionAccumulator
    {
        private final String label;
        private StatsDisplayPanelBE.DataChannel primaryChannel;
        private float totalRate;
        private float weightedTrend;
        private float trendWeight;
        private float primaryRate;
        private final LinkedHashSet<String> sourceTypes;
        private int contributorCount;

        ProductionAccumulator(String label) {
            this.label = label;
            this.sourceTypes = new LinkedHashSet<String>();
        }

        void add(StatsDisplayPanelBE.DataChannel channel, float rate, float trend, String sourceType) {
            this.totalRate += rate;
            final float weight = Math.max(rate, 0.1f);
            this.weightedTrend += trend * weight;
            this.trendWeight += weight;
            if (this.primaryChannel == null || rate > this.primaryRate) {
                this.primaryChannel = channel;
                this.primaryRate = rate;
            }
            if (sourceType != null && !sourceType.isEmpty()) {
                this.sourceTypes.add(sourceType);
            }
            ++this.contributorCount;
        }

        ProductionEntry toEntry() {
            final String sourceType = this.sourceTypes.isEmpty() ? "" : ((this.sourceTypes.size() == 1) ? this.sourceTypes.iterator().next() : "Mixed");
            final float trend = (this.trendWeight > 0.0f) ? (this.weightedTrend / this.trendWeight) : 0.0f;
            return new ProductionEntry(this.primaryChannel, this.label, this.totalRate, trend, sourceType, this.contributorCount);
        }
    }
}
