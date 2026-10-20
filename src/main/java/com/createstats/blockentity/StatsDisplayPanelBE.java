package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.display.BattleCompactTargetHandler;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.blockentity.LoadBalancerBE.TopologyStatus;
import com.createstats.train.manifest.FreightManifestEvaluationResult;
import com.createstats.train.manifest.FreightManifestLine;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightManifestValidationResult;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.TrainCargoSnapshot;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import com.createstats.topology.NetworkGraph;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import com.createstats.renderer.DisplayTextureManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.createstats.data.TimeSeriesStore;
import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyInputs;
import com.createstats.efficiency.EfficiencyNbt;
import com.createstats.efficiency.EfficiencySampler;
import com.createstats.efficiency.EfficiencyTrace;
import com.simibubi.create.content.fluids.tank.BoilerData;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import com.createstats.topology.NetworkDiscovery;
import com.createstats.ModConfiguration;
import com.createstats.CreateStats;
import java.util.Queue;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.Comparator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import com.createstats.train.TrainNetworkScanner;
import com.createstats.data.MetricType;
import com.createstats.compat.SimulatedCompat;
import com.createstats.compat.SimulatedCompatHelper;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.util.StockpileSwitchMetrics;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.Map;
import java.util.Iterator;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import com.createstats.train.TrainMapData;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.GradeHistory;
import com.createstats.data.AnnotationManager;
import net.minecraft.core.BlockPos;
import com.createstats.display.StatsDisplaySettings;
import java.util.LinkedHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Objects;
import java.util.UUID;

public class StatsDisplayPanelBE extends BlockEntity implements IAutoDataReceiver, RangeAttachment, com.createstats.power.IFeConsumer
{
    protected final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles
                    .pooled(com.createstats.power.FeProfiles.KEY_STATS_DISPLAY_PANEL, this::panelCount));

    // controller's buffer, the whole display is one device. boots and browns out together
    public com.createstats.power.FeConsumer feConsumer() {
        if (!this.isController) {
            final StatsDisplayPanelBE controller = this.getControllerBE();

            if (controller != this) {
                return controller.feConsumer;
            }
        }
        return this.feConsumer;
    }

    public int panelCount() {
        return Math.max(1, this.multiblockWidth * this.multiblockHeight);
    }

    public static final int MAX_LINE_CHANNELS = 4;
    private final LinkedHashMap<String, DataChannel> channels;
    private StatsDisplaySettings settings;
    private boolean needsSync;
    private String sourceType;
    private boolean statsDisplayLinkOptInRequired;
    private String statsDisplayLinkHintSourceType;
    private long statsDisplayLinkHintTick;
    private final LinkedHashMap<String, SourceConfig> sourceRegistry;
    private final LinkedHashMap<String, SafetyClutchLink> safetyClutchLinks;
    private final LinkedHashMap<String, KineticFlywheelLink> kineticFlywheelLinks;
    private final LinkedHashMap<String, AutoTransmissionClutchLink> autoTransmissionClutchLinks;
    private final LinkedHashMap<String, LoadBalancerLink> loadBalancerLinks;
    private final LinkedHashMap<String, BattleScreenLink> battleScreenLinks;
    private final LinkedHashMap<String, FreightManifestLink> freightManifestLinks;
    private final LinkedHashMap<String, TrainStationLink> trainStationLinks;
    private final LinkedHashMap<String, MiniDisplayLink> miniDisplayLinks;

    private final AerialLinks aerialLinks = new AerialLinks(this);

    private final GroundLinks groundLinks = new GroundLinks(this);

    private final SeismicLinks seismicLinks = new SeismicLinks(this);
    private int nextAutoSourceIndex;
    private boolean isController;
    private BlockPos controllerPos;
    private int multiblockWidth;
    private int multiblockHeight;
    private boolean multiblockMergeDisabled;
    private int cachedRedstonePower;
    private boolean redstoneEnabled;
    private String redstoneChannelId;
    private float redstoneThreshold;
    private boolean redstoneAbove;
    private int redstoneMode;
    private AnnotationManager annotationManager;
    private ScanSettings scanSettings;
    private int scanCooldown;
    private int dataPollCooldown;
    private static final int DATA_POLL_INTERVAL = 20;
    private GradeHistory gradeHistory;
    private EfficiencyScore latestEfficiencyScore;

    private EfficiencyTrace latestEfficiencyTrace;
    private int efficiencyRecalcCooldown;
    private static final int EFFICIENCY_RECALC_INTERVAL = 40;
    private Object cachedNetworkGraph;
    private long lastTopologyScanTick;
    private static final int TOPOLOGY_SCAN_INTERVAL = 100;
    private boolean hasTrainConnection;
    private TrainMapData trainMapData;
    private long lastTrainScanTick;
    private long lastTrainConnectionTick;
    private long lastTrainActivityTick;
    private long lastTopologyActivityTick;
    private static final int TRAIN_SCAN_INTERVAL = 2;
    private static final int TRAIN_CONNECTION_TIMEOUT = 6000;
    private static final int TRAIN_ACTIVITY_WINDOW = 100;
    private static final int TOPOLOGY_ACTIVITY_WINDOW = 100;
    private static final long SOURCE_IDLE_TIMEOUT = 600L;
    private static final long SOURCE_PRUNE_TIMEOUT = 1200L;
    private static final long REMOVED_SOURCE_PRUNE_DELAY = 40L;
    private static final long STATS_DISPLAY_LINK_HINT_TIMEOUT = 60L;
    private final List<String> trainMapGroups;
    private static final int DEFAULT_SYNC_DEBOUNCE_TICKS = 10;
    private int syncDebounce;
    private boolean postLoadTopologyValidated = false;
    private int postLoadRetryTicks = 0;
    private static final int POST_LOAD_RETRY_MAX = 20;
    private AntennaTier antennaTier;

    private final Map<String, com.createstats.rs.RsNetworkState> rsNetworks =
            new LinkedHashMap<String, com.createstats.rs.RsNetworkState>();

    public StatsDisplayPanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType)type, pos, state);
        this.channels = new LinkedHashMap<String, DataChannel>();
        this.needsSync = false;
        this.sourceType = "";
        this.statsDisplayLinkOptInRequired = false;
        this.statsDisplayLinkHintSourceType = "";
        this.statsDisplayLinkHintTick = 0L;
        this.sourceRegistry = new LinkedHashMap<String, SourceConfig>();
        this.safetyClutchLinks = new LinkedHashMap<String, SafetyClutchLink>();
        this.kineticFlywheelLinks = new LinkedHashMap<String, KineticFlywheelLink>();
        this.autoTransmissionClutchLinks = new LinkedHashMap<String, AutoTransmissionClutchLink>();
        this.loadBalancerLinks = new LinkedHashMap<String, LoadBalancerLink>();
        this.battleScreenLinks = new LinkedHashMap<String, BattleScreenLink>();
        this.freightManifestLinks = new LinkedHashMap<String, FreightManifestLink>();
        this.trainStationLinks = new LinkedHashMap<String, TrainStationLink>();
        this.miniDisplayLinks = new LinkedHashMap<String, MiniDisplayLink>();
        this.nextAutoSourceIndex = 0;
        this.isController = true;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        this.multiblockMergeDisabled = false;
        this.cachedRedstonePower = 0;
        this.redstoneEnabled = false;
        this.redstoneChannelId = "";
        this.redstoneThreshold = 0.0f;
        this.redstoneAbove = true;
        this.redstoneMode = 0;
        this.annotationManager = new AnnotationManager();
        this.scanSettings = new ScanSettings();
        this.scanCooldown = 0;
        this.dataPollCooldown = 0;
        this.gradeHistory = new GradeHistory();
        this.latestEfficiencyScore = null;
        this.latestEfficiencyTrace = null;
        this.efficiencyRecalcCooldown = 0;
        this.cachedNetworkGraph = null;
        this.lastTopologyScanTick = 0L;
        this.hasTrainConnection = false;
        this.trainMapData = null;
        this.lastTrainScanTick = 0L;
        this.lastTrainConnectionTick = 0L;
        this.lastTrainActivityTick = 0L;
        this.lastTopologyActivityTick = 0L;
        this.trainMapGroups = new ArrayList<String>();
        this.syncDebounce = 0;
        this.settings = new StatsDisplaySettings();
        this.controllerPos = pos;
        this.antennaTier = AntennaTier.NONE;
    }

    public DataChannel getOrCreateChannel(String channelId, String defaultLabel) {
        return this.channels.computeIfAbsent(channelId, id -> new DataChannel(id, defaultLabel));
    }

    @Nullable
    public DataChannel getChannel(String channelId) {
        return this.channels.get(channelId);
    }

    public List<DataChannel> getActiveChannels() {
        final List<DataChannel> active = new ArrayList<DataChannel>();
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() > 0) {
                active.add(ch);
            }
        }
        return active;
    }

    public List<DataChannel> getVisibleChannels() {
        final List<DataChannel> visible = new ArrayList<DataChannel>();
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() > 0 && !this.settings.isChannelHidden(ch.getId())) {
                visible.add(ch);
            }
        }
        return visible;
    }

    public Map<String, DataChannel> getAllChannels() {
        return this.channels;
    }

    public Map<String, com.createstats.rs.RsNetworkState> getRsNetworks() {
        return this.rsNetworks;
    }

    @Nullable
    public com.createstats.rs.RsNetworkState getRsNetwork(String sourceId) {
        return sourceId == null ? null : this.rsNetworks.get(sourceId);
    }

    public com.createstats.rs.RsNetworkState getOrCreateRsNetwork(String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) {
            return new com.createstats.rs.RsNetworkState();
        }
        if (!this.rsNetworks.containsKey(sourceId) && this.rsNetworks.size() >= 16) {
            final Iterator<String> it = this.rsNetworks.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        return this.rsNetworks.computeIfAbsent(sourceId,
                ignored -> new com.createstats.rs.RsNetworkState());
    }

    public void removeRsNetwork(String sourceId) {
        if (sourceId != null) {
            this.rsNetworks.remove(sourceId);
        }
    }

    public String rsFocusedItemId() {
        return "";
    }

    public SourceConfig registerOrUpdateSource(BlockPos sourcePos, String sourceType) {
        return this.registerOrUpdateSource(sourcePos, sourceType, false);
    }

    public SourceConfig registerOrUpdateSourceExact(BlockPos sourcePos, String sourceType) {
        return this.registerOrUpdateSource(sourcePos, sourceType, true);
    }

    private SourceConfig registerOrUpdateSource(BlockPos sourcePos, String sourceType, boolean exactTypeMatch) {
        for (SourceConfig config : this.sourceRegistry.values()) {
            if (!config.getSourcePos().equals(sourcePos)) {
                continue;
            }
            if (exactTypeMatch && !sourceType.isEmpty() && !config.getSourceType().equals(sourceType)) {
                continue;
            }
            if (!exactTypeMatch || sourceType.isEmpty() || config.getSourceType().equals(sourceType)) {
                if (!config.getSourceType().equals(sourceType) && !sourceType.isEmpty()) {
                    config.setSourceType(sourceType);
                }
                final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
                config.setLastDataTick(tick);
                return config;
            }
        }
        String baseId = sourceType.toLowerCase().replaceAll("[^a-z0-9]", "_");
        if (baseId.isEmpty()) {
            baseId = "source";
        }
        final int idx = ++this.nextAutoSourceIndex;
        final String sourceId = baseId + "_" + idx;
        final SourceConfig config2 = new SourceConfig(sourceId, sourcePos, sourceType);
        final long tick2 = (this.level != null) ? this.level.getGameTime() : 0L;
        config2.setFirstConnectTick(tick2);
        config2.setLastDataTick(tick2);
        this.sourceRegistry.put(sourceId, config2);
        this.needsSync = true;
        this.setChanged();
        return config2;
    }

    public LinkedHashMap<String, SourceConfig> getSourceRegistry() {
        return this.sourceRegistry;
    }

    public SafetyClutchLink registerOrUpdateSafetyClutch(BlockPos sourcePos, SafetyClutchBE clutch) {
        final String linkId = createSafetyClutchLinkId(sourcePos);
        SafetyClutchLink link = this.safetyClutchLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new SafetyClutchLink(linkId, sourcePos.immutable());
            this.safetyClutchLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(clutch, tick)) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeSafetyClutchByPosition(BlockPos sourcePos) {
        final String linkId = createSafetyClutchLinkId(sourcePos);
        if (this.safetyClutchLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<SafetyClutchLink> getSafetyClutchLinks() {
        return this.safetyClutchLinks.values();
    }

    @Nullable
    public SafetyClutchLink getSafetyClutchLink(String linkId) {
        return this.safetyClutchLinks.get(linkId);
    }

    public boolean hasSafetyClutchLinks() {
        return !this.safetyClutchLinks.isEmpty();
    }

    public KineticFlywheelLink registerOrUpdateKineticFlywheel(BlockPos sourcePos, KineticFlywheelBE flywheel) {
        final String linkId = createKineticFlywheelLinkId(sourcePos);
        KineticFlywheelLink link = this.kineticFlywheelLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new KineticFlywheelLink(linkId, sourcePos.immutable());
            this.kineticFlywheelLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(flywheel, tick)) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeKineticFlywheelByPosition(BlockPos sourcePos) {
        final String linkId = createKineticFlywheelLinkId(sourcePos);
        if (this.kineticFlywheelLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<KineticFlywheelLink> getKineticFlywheelLinks() {
        return this.kineticFlywheelLinks.values();
    }

    @Nullable
    public KineticFlywheelLink getKineticFlywheelLink(String linkId) {
        return this.kineticFlywheelLinks.get(linkId);
    }

    public boolean hasKineticFlywheelLinks() {
        return !this.kineticFlywheelLinks.isEmpty();
    }

    public AutoTransmissionClutchLink registerOrUpdateAutoTransmissionClutch(BlockPos sourcePos, AutoTransmissionClutchBE transmission) {
        final String linkId = createAutoTransmissionClutchLinkId(sourcePos);
        AutoTransmissionClutchLink link = this.autoTransmissionClutchLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new AutoTransmissionClutchLink(linkId, sourcePos.immutable());
            this.autoTransmissionClutchLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(transmission, tick)) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeAutoTransmissionClutchByPosition(BlockPos sourcePos) {
        final String linkId = createAutoTransmissionClutchLinkId(sourcePos);
        if (this.autoTransmissionClutchLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<AutoTransmissionClutchLink> getAutoTransmissionClutchLinks() {
        return this.autoTransmissionClutchLinks.values();
    }

    @Nullable
    public AutoTransmissionClutchLink getAutoTransmissionClutchLink(String linkId) {
        return this.autoTransmissionClutchLinks.get(linkId);
    }

    public boolean hasAutoTransmissionClutchLinks() {
        return !this.autoTransmissionClutchLinks.isEmpty();
    }

    public LoadBalancerLink registerOrUpdateLoadBalancer(BlockPos sourcePos, LoadBalancerBE balancer) {
        final String linkId = createLoadBalancerLinkId(sourcePos);
        LoadBalancerLink link = this.loadBalancerLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new LoadBalancerLink(linkId, sourcePos.immutable());
            this.loadBalancerLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(balancer, tick)) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeLoadBalancerByPosition(BlockPos sourcePos) {
        final String linkId = createLoadBalancerLinkId(sourcePos);
        if (this.loadBalancerLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<LoadBalancerLink> getLoadBalancerLinks() {
        return this.loadBalancerLinks.values();
    }

    @Nullable
    public LoadBalancerLink getLoadBalancerLink(String linkId) {
        return this.loadBalancerLinks.get(linkId);
    }

    public boolean hasLoadBalancerLinks() {
        return !this.loadBalancerLinks.isEmpty();
    }

    public BattleScreenLink registerOrUpdateBattleScreen(BlockPos sourcePos, BattleScreenDisplayBE battleScreen) {
        return this.registerOrUpdateBattleScreen(sourcePos, battleScreen, -1);
    }

    public BattleScreenLink registerOrUpdateBattleScreen(BlockPos sourcePos, BattleScreenDisplayBE battleScreen, int viewMode) {
        final String sourceType = BattleCompactTargetHandler.SOURCE_TYPE;
        this.registerOrUpdateSource(sourcePos, sourceType, true);
        if (!sourceType.equals(this.sourceType)) {
            this.sourceType = sourceType;
        }
        final String linkId = createBattleScreenLinkId(sourcePos);
        BattleScreenLink link = this.battleScreenLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new BattleScreenLink(linkId, sourcePos.immutable());
            this.battleScreenLinks.put(linkId, link);
            changed = true;
        }
        if (viewMode >= 0 && link.setBattleViewMode(viewMode)) {
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(battleScreen, tick)) {
            changed = true;
            link.setLastForcedSyncTick(tick);
            this.publishBattleScreenChannels(sourcePos, sourceType, link.getObservers());
        } else if (tick - link.getLastForcedSyncTick() >= 40L) {
            link.setLastForcedSyncTick(tick);
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeBattleScreenByPosition(BlockPos sourcePos) {
        final String linkId = createBattleScreenLinkId(sourcePos);
        if (this.battleScreenLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<BattleScreenLink> getBattleScreenLinks() {
        return this.battleScreenLinks.values();
    }

    @Nullable
    public BattleScreenLink getBattleScreenLink(String linkId) {
        return this.battleScreenLinks.get(linkId);
    }

    public boolean hasBattleScreenLinks() {
        return !this.battleScreenLinks.isEmpty();
    }

    public MiniDisplayLink registerOrUpdateMiniDisplay(BlockPos sourcePos, MiniDisplayBE miniDisplay) {
        final MiniDisplayBE controller = miniDisplay.getControllerBE();
        final BlockPos controllerPos = controller.getBlockPos().immutable();
        final String linkId = createMiniDisplayLinkId(controllerPos);
        MiniDisplayLink link = this.miniDisplayLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new MiniDisplayLink(linkId, controllerPos);
            this.miniDisplayLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(controller, tick, this.level.registryAccess())) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeMiniDisplayByPosition(BlockPos sourcePos) {
        if (sourcePos == null) {
            return false;
        }
        BlockPos normalizedPos = sourcePos.immutable();
        if (this.level != null) {
            final BlockEntity sourceBE = this.level.getBlockEntity(sourcePos);
            if (sourceBE instanceof MiniDisplayBE miniDisplay) {
                normalizedPos = miniDisplay.getControllerBE().getBlockPos().immutable();
            }
        }
        boolean removed = false;
        final Iterator<Map.Entry<String, MiniDisplayLink>> iterator = this.miniDisplayLinks.entrySet().iterator();
        while (iterator.hasNext()) {
            final MiniDisplayLink link = iterator.next().getValue();
            if (link == null || link.getSourcePos() == null) {
                iterator.remove();
                removed = true;
                continue;
            }
            if (link.getSourcePos().equals(sourcePos) || link.getSourcePos().equals(normalizedPos)) {
                iterator.remove();
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<MiniDisplayLink> getMiniDisplayLinks() {
        return this.miniDisplayLinks.values();
    }

    @Nullable
    public MiniDisplayLink getMiniDisplayLink(String linkId) {
        return this.miniDisplayLinks.get(linkId);
    }

    public boolean hasMiniDisplayLinks() {
        return !this.miniDisplayLinks.isEmpty();
    }

    public FreightManifestLink registerOrUpdateFreightManifest(BlockPos sourcePos, FreightManifestClipboardBE clipboard) {
        final String linkId = createFreightManifestLinkId(sourcePos);
        FreightManifestLink link = this.freightManifestLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new FreightManifestLink(linkId, sourcePos.immutable());
            this.freightManifestLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(clipboard, tick, this.level.registryAccess())) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeFreightManifestByPosition(BlockPos sourcePos) {
        final String linkId = createFreightManifestLinkId(sourcePos);
        if (this.freightManifestLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<FreightManifestLink> getFreightManifestLinks() {
        return this.freightManifestLinks.values();
    }

    @Nullable
    public FreightManifestLink getFreightManifestLink(String linkId) {
        return this.freightManifestLinks.get(linkId);
    }

    public boolean hasFreightManifestLinks() {
        return !this.freightManifestLinks.isEmpty();
    }

    public TrainStationLink registerOrUpdateTrainStation(BlockPos sourcePos, StationBlockEntity station) {
        final String linkId = createTrainStationLinkId(sourcePos);
        TrainStationLink link = this.trainStationLinks.get(linkId);
        boolean changed = false;
        if (link == null) {
            link = new TrainStationLink(linkId, sourcePos.immutable());
            this.trainStationLinks.put(linkId, link);
            changed = true;
        }
        final long tick = (this.level != null) ? this.level.getGameTime() : 0L;
        if (link.updateFrom(station, this.trainMapData, tick, this.level.registryAccess())) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return link;
    }

    public boolean removeTrainStationByPosition(BlockPos sourcePos) {
        final String linkId = createTrainStationLinkId(sourcePos);
        if (this.trainStationLinks.remove(linkId) == null) {
            return false;
        }
        this.needsSync = true;
        this.setChanged();
        return true;
    }

    public Collection<TrainStationLink> getTrainStationLinks() {
        return this.trainStationLinks.values();
    }

    @Nullable
    public TrainStationLink getTrainStationLink(String linkId) {
        return this.trainStationLinks.get(linkId);
    }

    public boolean hasTrainStationLinks() {
        return !this.trainStationLinks.isEmpty();
    }

    public AerialLinks aerialLinks() {
        return this.aerialLinks;
    }

    void markAerialLinksChanged() {
        this.needsSync = true;
        this.setChanged();
    }

    public GroundLinks groundLinks() {
        return this.groundLinks;
    }

    void markGroundLinksChanged() {
        this.needsSync = true;
        this.setChanged();
    }

    public SeismicLinks seismicLinks() {
        return this.seismicLinks;
    }

    void markSeismicLinksChanged() {
        this.needsSync = true;
        this.setChanged();
    }

    public boolean removeSourceByPosition(BlockPos sourcePos) {
        boolean changed = false;
        final Set<String> removedSourceIds = new HashSet<String>();
        final Iterator<Map.Entry<String, SourceConfig>> sourceIterator = this.sourceRegistry.entrySet().iterator();
        while (sourceIterator.hasNext()) {
            final Map.Entry<String, SourceConfig> entry = sourceIterator.next();
            final SourceConfig config = entry.getValue();
            if (config == null || !config.getSourcePos().equals(sourcePos)) {
                continue;
            }
            removedSourceIds.add(entry.getKey());
            sourceIterator.remove();
            changed = true;
        }
        if (!removedSourceIds.isEmpty()) {
            final Iterator<Map.Entry<String, DataChannel>> channelIterator = this.channels.entrySet().iterator();
            while (channelIterator.hasNext()) {
                final Map.Entry<String, DataChannel> entry = channelIterator.next();
                final String channelId = entry.getKey();
                for (String sourceId : removedSourceIds) {
                    if (!channelId.startsWith("src_" + sourceId)) {
                        continue;
                    }
                    if (channelId.equals(this.redstoneChannelId)) {
                        this.redstoneChannelId = "";
                    }
                    channelIterator.remove();
                    changed = true;
                    break;
                }
            }
        }
        if (this.removeSafetyClutchByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeAutoTransmissionClutchByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeLoadBalancerByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeBattleScreenByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeMiniDisplayByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeFreightManifestByPosition(sourcePos)) {
            changed = true;
        }
        if (this.removeTrainStationByPosition(sourcePos)) {
            changed = true;
        }
        if (!changed) {
            return false;
        }
        final boolean hasTrainSource = this.sourceRegistry.values().stream().anyMatch(config -> config != null && "Train Station".equals(config.getSourceType()));
        if (!hasTrainSource && this.clearTrainConnection()) {
            changed = true;
        }
        if (this.sourceRegistry.isEmpty() && this.statsDisplayLinkHintSourceType.isEmpty() && !this.sourceType.isEmpty()) {
            this.sourceType = "";
            changed = true;
        }
        if (this.normalizeSettingsState()) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
        return changed;
    }

    public List<DataChannel> getChannelsForSource(String sourceId) {
        final String prefix = "src_" + sourceId + "_";
        final List<DataChannel> result = new ArrayList<DataChannel>();
        for (DataChannel ch : this.channels.values()) {
            if (ch.getId().startsWith(prefix)) {
                result.add(ch);
            }
        }
        return result;
    }

    public List<DataChannel> getVisibleChannelsForSource(String sourceId) {
        final String prefix = "src_" + sourceId + "_";
        final List<DataChannel> result = new ArrayList<DataChannel>();
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() > 0 && !this.settings.isChannelHidden(ch.getId()) && ch.getId().startsWith(prefix)) {
                result.add(ch);
            }
        }
        return result;
    }

    public boolean normalizeSettingsState() {
        final ArrayList<String> existingChannelIds = new ArrayList<String>(this.channels.size());
        final ArrayList<String> visibleChannelIds = new ArrayList<String>();
        final ArrayList<String> retainedPreferredSourceIds = new ArrayList<String>();
        for (DataChannel channel : this.channels.values()) {
            existingChannelIds.add(channel.getId());
            if (channel.getStore().getSampleCount() > 0 && !this.settings.isChannelHidden(channel.getId())) {
                visibleChannelIds.add(channel.getId());
            }
        }
        for (Map.Entry<String, SourceConfig> entry : this.sourceRegistry.entrySet()) {
            final SourceConfig config = entry.getValue();
            if (config == null || config.getSourcePos() == null || !BattleCompactTargetHandler.isBattleSource(config.getSourceType())) {
                continue;
            }
            if (this.battleScreenLinks.containsKey(createBattleScreenLinkId(config.getSourcePos()))) {
                retainedPreferredSourceIds.add(entry.getKey());
            }
        }
        boolean changed = this.settings.normalizeSelections(this.sourceRegistry.keySet(), existingChannelIds, visibleChannelIds, retainedPreferredSourceIds, this.hasTrainConnection, this.multiblockWidth, this.multiblockHeight);
        if (this.settings.getPanelHudSettings().normalize(this.sourceRegistry)) {
            changed = true;
        }
        return changed;
    }

    private void recoverNextAutoSourceIndex() {
        int recoveredIndex = Math.max(0, this.nextAutoSourceIndex);
        for (String sourceId : this.sourceRegistry.keySet()) {
            final int lastUnderscore = sourceId.lastIndexOf(95);
            if (lastUnderscore < 0 || lastUnderscore >= sourceId.length() - 1) {
                continue;
            }
            try {
                recoveredIndex = Math.max(recoveredIndex, Integer.parseInt(sourceId.substring(lastUnderscore + 1)));
            }
            catch (NumberFormatException ex) {}
        }
        this.nextAutoSourceIndex = recoveredIndex;
    }

    private boolean clearTrainConnection() {
        boolean changed = false;
        final long currentTick = this.level != null ? this.level.getGameTime() : 0L;
        if (this.hasTrainConnection) {
            this.hasTrainConnection = false;
            changed = true;
        }
        if (this.trainMapData != null) {
            this.trainMapData = null;
            changed = true;
        }
        if (this.level != null && !this.level.isClientSide() && !this.trainStationLinks.isEmpty()) {
            for (TrainStationLink link : this.trainStationLinks.values()) {
                if (link == null || link.getSourcePos() == null) {
                    continue;
                }
                final BlockEntity sourceBE = this.level.getBlockEntity(link.getSourcePos());
                if (!(sourceBE instanceof StationBlockEntity stationBlockEntity)) {
                    continue;
                }
                if (link.updateFrom(stationBlockEntity, null, currentTick, this.level.registryAccess())) {
                    changed = true;
                }
            }
        }
        if (this.lastTrainConnectionTick != 0L) {
            this.lastTrainConnectionTick = 0L;
            changed = true;
        }
        if (this.lastTrainActivityTick != 0L) {
            this.lastTrainActivityTick = 0L;
            changed = true;
        }
        if (this.lastTrainScanTick != 0L) {
            this.lastTrainScanTick = 0L;
            changed = true;
        }
        if (changed && this.normalizeSettingsState()) {
            changed = true;
        }
        return changed;
    }

    private void pruneStaleSources(long currentTick) {
        if (this.level == null) {
            return;
        }
        boolean changed = false;
        final Set<String> removedSourceIds = new HashSet<String>();
        final Set<BlockPos> removedSourcePositions = new HashSet<BlockPos>();
        final Iterator<Map.Entry<String, SourceConfig>> sourceIterator = this.sourceRegistry.entrySet().iterator();
        while (sourceIterator.hasNext()) {
            final Map.Entry<String, SourceConfig> entry = sourceIterator.next();
            final SourceConfig config = entry.getValue();
            if (config == null) {
                removedSourceIds.add(entry.getKey());
                sourceIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData = Math.max(0L, currentTick - config.getLastDataTick());
            if (config.isActive() && ticksSinceData > SOURCE_IDLE_TIMEOUT) {
                config.setActive(false);
                changed = true;
            }
            final boolean sourceLoaded = this.level.isLoaded(config.getSourcePos());
            final BlockEntity sourceBE = sourceLoaded ? this.level.getBlockEntity(config.getSourcePos()) : null;
            final boolean missingSource = sourceLoaded && sourceBE == null;
            final long pruneDelay = missingSource ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData > pruneDelay) {
                removedSourceIds.add(entry.getKey());
                removedSourcePositions.add(config.getSourcePos().immutable());
                sourceIterator.remove();
                changed = true;
            }
        }
        if (!removedSourceIds.isEmpty()) {
            final Iterator<Map.Entry<String, DataChannel>> channelIterator = this.channels.entrySet().iterator();
            while (channelIterator.hasNext()) {
                final Map.Entry<String, DataChannel> entry = channelIterator.next();
                final String channelId = entry.getKey();
                for (String sourceId : removedSourceIds) {
                    if (!channelId.startsWith("src_" + sourceId)) {
                        continue;
                    }
                    if (channelId.equals(this.redstoneChannelId)) {
                        this.redstoneChannelId = "";
                    }
                    channelIterator.remove();
                    changed = true;
                    break;
                }
            }
        }
        final Iterator<Map.Entry<String, SafetyClutchLink>> clutchIterator = this.safetyClutchLinks.entrySet().iterator();
        while (clutchIterator.hasNext()) {
            final Map.Entry<String, SafetyClutchLink> entry2 = clutchIterator.next();
            final SafetyClutchLink link = entry2.getValue();
            if (link == null || link.getSourcePos() == null) {
                clutchIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData2 = Math.max(0L, currentTick - link.getLastDataTick());
            final boolean clutchLoaded = this.level.isLoaded(link.getSourcePos());
            final BlockEntity sourceBE2 = clutchLoaded ? this.level.getBlockEntity(link.getSourcePos()) : null;
            final boolean missingClutch = clutchLoaded && !(sourceBE2 instanceof SafetyClutchBE);
            final long pruneDelay2 = missingClutch ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData2 > pruneDelay2) {
                clutchIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, KineticFlywheelLink>> flywheelIterator = this.kineticFlywheelLinks.entrySet().iterator();
        while (flywheelIterator.hasNext()) {
            final Map.Entry<String, KineticFlywheelLink> flywheelEntry = flywheelIterator.next();
            final KineticFlywheelLink fwLink = flywheelEntry.getValue();
            if (fwLink == null || fwLink.getSourcePos() == null) {
                flywheelIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceDataFw = Math.max(0L, currentTick - fwLink.getLastDataTick());
            final boolean flywheelLoaded = this.level.isLoaded(fwLink.getSourcePos());
            final BlockEntity sourceBEFw = flywheelLoaded ? this.level.getBlockEntity(fwLink.getSourcePos()) : null;
            final boolean missingFlywheel = flywheelLoaded && !(sourceBEFw instanceof KineticFlywheelBE);
            final long pruneDelayFw = missingFlywheel ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceDataFw > pruneDelayFw) {
                flywheelIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, AutoTransmissionClutchLink>> transIterator = this.autoTransmissionClutchLinks.entrySet().iterator();
        while (transIterator.hasNext()) {
            final Map.Entry<String, AutoTransmissionClutchLink> entry3 = transIterator.next();
            final AutoTransmissionClutchLink tLink = entry3.getValue();
            if (tLink == null || tLink.getSourcePos() == null) {
                transIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData3 = Math.max(0L, currentTick - tLink.getLastDataTick());
            final boolean transmissionLoaded = this.level.isLoaded(tLink.getSourcePos());
            final BlockEntity sourceBE3 = transmissionLoaded ? this.level.getBlockEntity(tLink.getSourcePos()) : null;
            final boolean missingTransmission = transmissionLoaded && !(sourceBE3 instanceof AutoTransmissionClutchBE);
            final long pruneDelay3 = missingTransmission ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData3 > pruneDelay3) {
                transIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, LoadBalancerLink>> balancerIterator = this.loadBalancerLinks.entrySet().iterator();
        while (balancerIterator.hasNext()) {
            final Map.Entry<String, LoadBalancerLink> entry4 = balancerIterator.next();
            final LoadBalancerLink bLink = entry4.getValue();
            if (bLink == null || bLink.getSourcePos() == null) {
                balancerIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData4 = Math.max(0L, currentTick - bLink.getLastDataTick());
            final boolean balancerLoaded = this.level.isLoaded(bLink.getSourcePos());
            final BlockEntity sourceBE4 = balancerLoaded ? this.level.getBlockEntity(bLink.getSourcePos()) : null;
            final boolean missingBalancer = balancerLoaded && !(sourceBE4 instanceof LoadBalancerBE);
            final long pruneDelay4 = missingBalancer ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData4 > pruneDelay4) {
                balancerIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, BattleScreenLink>> battleScreenIterator = this.battleScreenLinks.entrySet().iterator();
        while (battleScreenIterator.hasNext()) {
            final Map.Entry<String, BattleScreenLink> battleEntry = battleScreenIterator.next();
            final BattleScreenLink battleLink = battleEntry.getValue();
            if (battleLink == null || battleLink.getSourcePos() == null) {
                battleScreenIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceBattleData = Math.max(0L, currentTick - battleLink.getLastDataTick());
            final boolean battleLoaded = this.level.isLoaded(battleLink.getSourcePos());
            final BlockEntity battleSourceBE = battleLoaded ? this.level.getBlockEntity(battleLink.getSourcePos()) : null;
            final boolean missingBattleScreen = battleLoaded && !(battleSourceBE instanceof BattleScreenDisplayBE);
            final long battlePruneDelay = missingBattleScreen ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceBattleData > battlePruneDelay) {
                battleScreenIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, MiniDisplayLink>> miniDisplayIterator = this.miniDisplayLinks.entrySet().iterator();
        while (miniDisplayIterator.hasNext()) {
            final Map.Entry<String, MiniDisplayLink> entry5 = miniDisplayIterator.next();
            final MiniDisplayLink miniDisplayLink = entry5.getValue();
            if (miniDisplayLink == null || miniDisplayLink.getSourcePos() == null) {
                miniDisplayIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData5 = Math.max(0L, currentTick - miniDisplayLink.getLastDataTick());
            final boolean miniDisplayLoaded = this.level.isLoaded(miniDisplayLink.getSourcePos());
            final BlockEntity sourceBE5 = miniDisplayLoaded ? this.level.getBlockEntity(miniDisplayLink.getSourcePos()) : null;
            final boolean missingMiniDisplay = miniDisplayLoaded && !(sourceBE5 instanceof MiniDisplayBE);
            final long pruneDelay5 = missingMiniDisplay ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData5 > pruneDelay5) {
                miniDisplayIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, FreightManifestLink>> manifestIterator = this.freightManifestLinks.entrySet().iterator();
        while (manifestIterator.hasNext()) {
            final Map.Entry<String, FreightManifestLink> entry6 = manifestIterator.next();
            final FreightManifestLink manifestLink = entry6.getValue();
            if (manifestLink == null || manifestLink.getSourcePos() == null) {
                manifestIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData6 = Math.max(0L, currentTick - manifestLink.getLastDataTick());
            final boolean manifestLoaded = this.level.isLoaded(manifestLink.getSourcePos());
            final BlockEntity sourceBE6 = manifestLoaded ? this.level.getBlockEntity(manifestLink.getSourcePos()) : null;
            final boolean missingClipboard = manifestLoaded && !(sourceBE6 instanceof FreightManifestClipboardBE);
            final long pruneDelay6 = missingClipboard ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData6 > pruneDelay6) {
                manifestIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, TrainStationLink>> trainStationIterator = this.trainStationLinks.entrySet().iterator();
        while (trainStationIterator.hasNext()) {
            final Map.Entry<String, TrainStationLink> entry7 = trainStationIterator.next();
            final TrainStationLink stationLink = entry7.getValue();
            if (stationLink == null || stationLink.getSourcePos() == null) {
                trainStationIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceData7 = Math.max(0L, currentTick - stationLink.getLastDataTick());
            final boolean stationLoaded = this.level.isLoaded(stationLink.getSourcePos());
            final BlockEntity sourceBE7 = stationLoaded ? this.level.getBlockEntity(stationLink.getSourcePos()) : null;
            final boolean missingStation = stationLoaded && !(sourceBE7 instanceof StationBlockEntity);
            final long pruneDelay7 = missingStation ? REMOVED_SOURCE_PRUNE_DELAY : SOURCE_PRUNE_TIMEOUT;
            if (ticksSinceData7 > pruneDelay7) {
                trainStationIterator.remove();
                changed = true;
            }
        }
        final Iterator<Map.Entry<String, ScanSettings.DiscoveredSource>> discoveredIterator = this.scanSettings.getDiscoveredSources().entrySet().iterator();
        while (discoveredIterator.hasNext()) {
            final Map.Entry<String, ScanSettings.DiscoveredSource> entry = discoveredIterator.next();
            final ScanSettings.DiscoveredSource discovered = entry.getValue();
            if (discovered == null) {
                discoveredIterator.remove();
                changed = true;
                continue;
            }
            if (removedSourcePositions.contains(discovered.pos)) {
                discoveredIterator.remove();
                changed = true;
                continue;
            }
            final long ticksSinceSeen = Math.max(0L, currentTick - discovered.lastSeenTick);
            final boolean discoveredLoaded = this.level.isLoaded(discovered.pos);
            final BlockEntity discoveredBE = discoveredLoaded ? this.level.getBlockEntity(discovered.pos) : null;
            final boolean validDiscoveredSource = !discoveredLoaded || discoveredBE != null && !this.detectSourceTypeFromBE(discoveredBE).isEmpty();
            if (!discovered.active && ((!validDiscoveredSource && ticksSinceSeen > REMOVED_SOURCE_PRUNE_DELAY) || ticksSinceSeen > SOURCE_PRUNE_TIMEOUT)) {
                discoveredIterator.remove();
                changed = true;
            }
        }
        if (this.sourceRegistry.isEmpty() && !this.sourceType.isEmpty()) {
            this.sourceType = "";
            changed = true;
        }
        if (!this.redstoneChannelId.isEmpty() && !this.channels.containsKey(this.redstoneChannelId)) {
            this.redstoneChannelId = "";
            changed = true;
        }
        final boolean hasTrainSource = this.sourceRegistry.values().stream().anyMatch(config -> config != null && "Train Station".equals(config.getSourceType()));
        if (!hasTrainSource && this.clearTrainConnection()) {
            changed = true;
        }
        if (this.normalizeSettingsState()) {
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    private void migrateLegacyStockpileChannels() {
        boolean changed = false;
        for (DataChannel channel : this.channels.values()) {
            if (!isStockpileSemanticChannel(channel.getId())) {
                continue;
            }
            if (channel.getStore().getSampleCount() > 0) {
                channel.getStore().clear();
                changed = true;
            }
            if (!channel.lastText.isEmpty()) {
                channel.lastText = "";
                changed = true;
            }
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    private static boolean isStockpileSemanticChannel(String channelId) {
        return channelId.endsWith("_auto_fill_pct") || channelId.endsWith("_auto_stock_level") || channelId.endsWith("_auto_stock_capacity") || channelId.endsWith("_auto_min_level") || channelId.endsWith("_auto_upper_thresh") || channelId.endsWith("_auto_lower_thresh");
    }

    public LinkedHashMap<String, List<DataChannel>> getChannelsBySource() {
        final LinkedHashMap<String, List<DataChannel>> map = new LinkedHashMap<String, List<DataChannel>>();
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() == 0) {
                continue;
            }
            final String srcId = this.extractSourceId(ch.getId());
            map.computeIfAbsent(srcId, k -> new ArrayList()).add(ch);
        }
        return map;
    }

    public SourceConfig getSourceForChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return null;
        }
        return this.sourceRegistry.get(this.extractSourceId(channelId));
    }

    private String extractSourceId(String channelId) {
        if (!channelId.startsWith("src_")) {
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

    public void onReceiveData(BlockPos sourcePos, String sourceType, int line, float value, MetricType detectedType, String rawText) {
        this.clearStatsDisplayLinkOptInRequired();
        final SourceConfig src = this.registerOrUpdateSource(sourcePos, sourceType);
        final String channelId = "src_" + src.getSourceId() + "_line" + line;
        final String label = (detectedType != MetricType.GENERIC) ? detectedType.getDisplayName() : ("Line " + line);
        final DataChannel ch = this.getOrCreateChannel(channelId, label);
        if (detectedType != MetricType.GENERIC && ch.getStore().getMetricType() != detectedType) {
            ch.getStore().setMetricType(detectedType);
            ch.setLabel(detectedType.getDisplayName());
        }
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        ch.getStore().addSample(value, gameTick);
        ch.lastText = rawText;
        this.needsSync = true;
        this.setChanged();
    }

    public void onReceiveData(int line, float value, MetricType detectedType, String rawText) {
        final String channelId = "line" + line;
        final String label = (detectedType != MetricType.GENERIC) ? detectedType.getDisplayName() : ("Line " + line);
        final DataChannel ch = this.getOrCreateChannel(channelId, label);
        if (detectedType != MetricType.GENERIC && ch.getStore().getMetricType() != detectedType) {
            ch.getStore().setMetricType(detectedType);
            ch.setLabel(detectedType.getDisplayName());
        }
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        ch.getStore().addSample(value, gameTick);
        ch.lastText = rawText;
        this.needsSync = true;
        this.setChanged();
    }

    public void onAutoData(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        this.onAutoDataInternal(sourcePos, sourceType, metricId, label, value, type, false);
    }

    public void onAutoDataExact(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        this.onAutoDataInternal(sourcePos, sourceType, metricId, label, value, type, true);
    }

    private void onAutoDataInternal(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type, boolean exactSourceType) {
        this.clearStatsDisplayLinkOptInRequired();
        final SourceConfig src = exactSourceType
                ? this.registerOrUpdateSourceExact(sourcePos, sourceType)
                : this.registerOrUpdateSource(sourcePos, sourceType);
        final String channelId = "src_" + src.getSourceId() + "_auto_" + metricId;
        final DataChannel ch = this.getOrCreateChannel(channelId, label);
        if (ch.getStore().getMetricType() != type) {
            ch.getStore().setMetricType(type);
        }
        if (!ch.getLabel().equals(label)) {
            ch.setLabel(label);
        }
        this.applyResponsiveAeroChannelTuning(ch, sourceType);
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        ch.getStore().addSample(value, gameTick);
        ch.lastText = type.formatValue(value);
        this.requestSync(this.isResponsiveAeroSourceType(sourceType) ? this.getResponsiveAeroSyncTicks() : DEFAULT_SYNC_DEBOUNCE_TICKS);
    }

    public void onAutoData(String channelId, String label, float value, MetricType type) {
        final DataChannel ch = this.getOrCreateChannel(channelId, label);
        if (ch.getStore().getMetricType() != type) {
            ch.getStore().setMetricType(type);
        }
        if (!ch.getLabel().equals(label)) {
            ch.setLabel(label);
        }
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        ch.getStore().addSample(value, gameTick);
        ch.lastText = type.formatValue(value);
        this.requestSync(DEFAULT_SYNC_DEBOUNCE_TICKS);
    }

    private void applyResponsiveAeroChannelTuning(DataChannel channel, String sourceType) {
        if (!this.isResponsiveAeroSourceType(sourceType)) {
            return;
        }
        final int responsiveSampleTicks = this.getResponsiveAeroSampleTicks();
        if (channel.getStore().getSampleIntervalTicks() > responsiveSampleTicks) {
            channel.getStore().setSampleIntervalTicks(responsiveSampleTicks);
        }
    }

    private boolean isResponsiveAeroSourceType(String sourceType) {
        return SimulatedCompat.isAvailable() && SimulatedCompatHelper.isResponsiveSourceType(sourceType);
    }

    private int getResponsiveAeroSampleTicks() {
        return Math.max(1, ModConfiguration.getEffectiveStatsLinkRefreshTicks());
    }

    private int getResponsiveAeroSyncTicks() {
        return Math.max(1, Math.min(DEFAULT_SYNC_DEBOUNCE_TICKS, this.getResponsiveAeroSampleTicks()));
    }

    private void requestSync(int requestedDebounceTicks) {
        this.needsSync = true;
        if (this.syncDebounce <= 0) {
            this.syncDebounce = 1;
        } else {
            this.syncDebounce = Math.min(this.syncDebounce, Math.max(1, requestedDebounceTicks));
        }
        this.setChanged();
    }

    public void setSourceType(String sourceType) {
        if (!this.sourceType.equals(sourceType)) {
            this.sourceType = sourceType;
            this.needsSync = true;
            this.setChanged();
        }
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public void markStatsDisplayLinkOptInRequired(String sourceType) {
        final String normalizedSourceType = (sourceType != null) ? sourceType : "";
        final long currentTick = (this.level != null) ? this.level.getGameTime() : 0L;
        boolean changed = false;
        if (!this.statsDisplayLinkOptInRequired) {
            this.statsDisplayLinkOptInRequired = true;
            changed = true;
        }
        if (!this.statsDisplayLinkHintSourceType.equals(normalizedSourceType)) {
            this.statsDisplayLinkHintSourceType = normalizedSourceType;
            changed = true;
        }
        if (this.statsDisplayLinkHintTick != currentTick) {
            this.statsDisplayLinkHintTick = currentTick;
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    public void clearStatsDisplayLinkOptInRequired() {
        if (!this.statsDisplayLinkOptInRequired && this.statsDisplayLinkHintSourceType.isEmpty() && this.statsDisplayLinkHintTick == 0L) {
            return;
        }
        this.statsDisplayLinkOptInRequired = false;
        this.statsDisplayLinkHintSourceType = "";
        this.statsDisplayLinkHintTick = 0L;
        this.needsSync = true;
        this.setChanged();
    }

    public boolean isStatsDisplayLinkOptInRequired() {
        return this.statsDisplayLinkOptInRequired && !this.statsDisplayLinkHintSourceType.isEmpty();
    }

    public String getStatsDisplayLinkHintSourceType() {
        return this.statsDisplayLinkHintSourceType;
    }

    public String getNoDataSourceType() {
        return this.isStatsDisplayLinkOptInRequired() ? this.statsDisplayLinkHintSourceType : this.sourceType;
    }

    private void migrateLegacySafetyClutchSources() {
        if (this.level == null || this.level.isClientSide() || this.sourceRegistry.isEmpty()) {
            return;
        }
        final ArrayList<BlockPos> legacyPositions = new ArrayList<BlockPos>();
        for (SourceConfig config : this.sourceRegistry.values()) {
            if (config == null || config.getSourcePos() == null) {
                continue;
            }
            final BlockEntity sourceBE = this.level.getBlockEntity(config.getSourcePos());
            if (!(sourceBE instanceof SafetyClutchBE clutch)) {
                continue;
            }
            this.registerOrUpdateSafetyClutch(config.getSourcePos(), clutch);
            legacyPositions.add(config.getSourcePos().immutable());
        }
        for (BlockPos pos : legacyPositions) {
            this.removeSourceByPosition(pos);
        }
    }

    private void refreshTrainStationLinks(long currentTick) {
        if (this.level == null || this.level.isClientSide() || this.trainStationLinks.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (TrainStationLink link : this.trainStationLinks.values()) {
            if (link == null || link.getSourcePos() == null) {
                continue;
            }
            final BlockEntity sourceBE = this.level.getBlockEntity(link.getSourcePos());
            if (!(sourceBE instanceof StationBlockEntity stationBlockEntity)) {
                continue;
            }
            if (link.updateFrom(stationBlockEntity, this.trainMapData, currentTick, this.level.registryAccess())) {
                changed = true;
            }
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    public int countLiveFeSources() {
        int count = 0;
        for (final SourceConfig config : this.sourceRegistry.values()) {
            if (config != null && config.isActive()) {
                count++;
            }
        }
        return count;
    }

    private boolean feServiceTick() {
        final com.createstats.power.FeConsumer bank = this.feConsumer();

        com.createstats.power.FePowerFx.chargeFromNeighbors(this.level, this.worldPosition, bank);
        if (bank != this.feConsumer) {
            final boolean memberPowered = bank.isPowered();
            this.syncLitState(memberPowered);
            return memberPowered;
        }

        final int liveSources = this.countLiveFeSources();
        final boolean powered = bank.serviceTick(liveSources > 0,
                com.createstats.power.FeLoadCurve.multiplier(liveSources));
        com.createstats.power.FePowerFx.playTransition(this.level, this.worldPosition, bank.pollTransition());
        this.syncLitState(powered);
        return powered;
    }

    private void syncLitState(boolean powered) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final net.minecraft.world.level.block.state.BlockState state = this.getBlockState();
        final net.minecraft.world.level.block.state.properties.BooleanProperty lit =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
        if (!state.hasProperty(lit) || state.getValue(lit) == powered) {
            return;
        }
        this.level.setBlock(this.worldPosition, state.setValue(lit, powered),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    public void tick() {
        if (this.level != null && !this.level.isClientSide() && !this.feServiceTick()) {
            return;
        }
        if (this.level != null && !this.level.isClientSide() && !this.postLoadTopologyValidated) {
            this.runPostLoadTopologyValidation();
        }
        if (this.needsSync && this.level != null && !this.level.isClientSide() && --this.syncDebounce <= 0) {
            this.needsSync = false;
            this.syncDebounce = DEFAULT_SYNC_DEBOUNCE_TICKS;
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        if (this.redstoneEnabled && this.level != null && !this.level.isClientSide()) {
            final int power = this.calculateRedstonePower();
            if (power != this.cachedRedstonePower) {
                this.cachedRedstonePower = power;
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
        }

        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            this.aerialLinks.serverTick(serverLevel, serverLevel.getGameTime());

            this.groundLinks.serverTick(serverLevel, serverLevel.getGameTime());

            this.seismicLinks.serverTick(serverLevel, serverLevel.getGameTime());
        }
        if (this.scanSettings.getScanMode() == ScanSettings.ScanMode.AUTO && this.level != null && !this.level.isClientSide() && --this.scanCooldown <= 0) {
            this.scanCooldown = this.scanSettings.getAutoIntervalTicks();
            this.executeScan();
        }
        if (this.scanSettings.getScanMode() == ScanSettings.ScanMode.AUTO && this.level != null && !this.level.isClientSide() && !this.scanSettings.getDiscoveredSources().isEmpty() && --this.dataPollCooldown <= 0) {
            this.dataPollCooldown = 20;
            this.pollDiscoveredSources();
        }
        if (this.level != null && !this.level.isClientSide() && !this.sourceRegistry.isEmpty()) {
            this.migrateLegacySafetyClutchSources();
        }
        if (this.level != null && !this.level.isClientSide() && (!this.sourceRegistry.isEmpty() || !this.safetyClutchLinks.isEmpty() || !this.autoTransmissionClutchLinks.isEmpty() || !this.loadBalancerLinks.isEmpty() || !this.battleScreenLinks.isEmpty() || !this.miniDisplayLinks.isEmpty() || !this.freightManifestLinks.isEmpty() || !this.trainStationLinks.isEmpty() || !this.scanSettings.getDiscoveredSources().isEmpty() || this.hasTrainConnection)) {
            final long currentTick = this.level.getGameTime();
            if (currentTick % 100L == 0L) {
                this.pruneStaleSources(currentTick);
            }
        }
        if (this.level != null && !this.level.isClientSide() && this.statsDisplayLinkOptInRequired) {
            final long currentTick = this.level.getGameTime();
            if (currentTick - this.statsDisplayLinkHintTick > STATS_DISPLAY_LINK_HINT_TIMEOUT) {
                this.clearStatsDisplayLinkOptInRequired();
            }
        }
        if (this.level != null && !this.level.isClientSide()) {
            if (this.hasEfficiencyInputs() && --this.efficiencyRecalcCooldown <= 0) {
                this.efficiencyRecalcCooldown = EfficiencyGrader.DEFAULT_INTERVAL_TICKS;
                this.recalculateEfficiency();
            }
            else if (!this.hasEfficiencyInputs()) {
                this.efficiencyRecalcCooldown = 0;
                this.clearEfficiencyScore();
            }
        }
        if (this.level != null && !this.level.isClientSide()) {
            final long currentTick = this.level.getGameTime();
            if (this.shouldScanTopology(currentTick) && currentTick - this.lastTopologyScanTick >= 100L) {
                this.lastTopologyScanTick = currentTick;
                this.scanTopologyNetwork();
            }
        }
        if (this.hasTrainConnection && this.level != null && !this.level.isClientSide()) {
            final long currentTick = this.level.getGameTime();
            if (this.lastTrainConnectionTick > 0L && currentTick - this.lastTrainConnectionTick > 6000L) {
                if (this.clearTrainConnection()) {
                    this.needsSync = true;
                    this.setChanged();
                }
            }
            else if (this.shouldScanTrainMap(currentTick) && currentTick - this.lastTrainScanTick >= TRAIN_SCAN_INTERVAL) {
                this.lastTrainScanTick = currentTick;
                try {
                    this.trainMapData = TrainNetworkScanner.scan(this.level);
                    this.lastTrainConnectionTick = currentTick;
                    this.refreshTrainStationLinks(currentTick);
                    this.requestSync(this.getTrainMapSyncDebounce(currentTick));
                    this.setChanged();
                }
                catch (LinkageError | RuntimeException ex) {
                    CreateStats.LOGGER.warn("Train map scan failed for panel at {}. Disabling train connection until refreshed.", this.worldPosition, ex);
                    if (this.clearTrainConnection()) {
                        this.needsSync = true;
                        this.setChanged();
                    }
                }
            }
        }
    }

    private void runPostLoadTopologyValidation() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.isController) {
            final List<BlockPos> members = this.getMultiblockMembers();
            for (BlockPos pos : members) {
                if (!this.level.hasChunkAt(pos)) {
                    if (++this.postLoadRetryTicks > POST_LOAD_RETRY_MAX) {
                        this.detectAndFormMultiblock();
                        this.postLoadTopologyValidated = true;
                    }
                    return;
                }
            }
            boolean mismatch = false;
            final BlockState myState = this.getBlockState();
            final Direction facing = myState.hasProperty(HorizontalDirectionalBlock.FACING)
                    ? (Direction)myState.getValue(HorizontalDirectionalBlock.FACING) : null;
            for (BlockPos pos : members) {
                final BlockEntity be = this.level.getBlockEntity(pos);
                if (!(be instanceof StatsDisplayPanelBE panel)) {
                    mismatch = true;
                    break;
                }
                final BlockState state = panel.getBlockState();
                if (facing != null && (!state.hasProperty(HorizontalDirectionalBlock.FACING)
                        || state.getValue(HorizontalDirectionalBlock.FACING) != facing)) {
                    mismatch = true;
                    break;
                }
                if (pos.equals(this.worldPosition)) {
                    if (!panel.isController) {
                        mismatch = true;
                        break;
                    }
                } else {
                    if (panel.isController || panel.controllerPos == null
                            || !panel.controllerPos.equals(this.worldPosition)) {
                        mismatch = true;
                        break;
                    }
                }
            }
            if (mismatch) {
                this.detectAndFormMultiblock();
            }
            this.postLoadTopologyValidated = true;
            return;
        }
        if (this.controllerPos == null) {
            this.postLoadTopologyValidated = true;
            return;
        }
        if (!this.level.hasChunkAt(this.controllerPos)) {
            if (++this.postLoadRetryTicks > POST_LOAD_RETRY_MAX) {
                this.detectAndFormMultiblock();
                this.postLoadTopologyValidated = true;
            }
            return;
        }
        final BlockEntity controllerBE = this.level.getBlockEntity(this.controllerPos);
        boolean ok = false;
        if (controllerBE instanceof StatsDisplayPanelBE controller && controller.isController()) {
            for (BlockPos pos : controller.getMultiblockMembers()) {
                if (pos.equals(this.worldPosition)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            this.detectAndFormMultiblock();
        }
        this.postLoadTopologyValidated = true;
    }

    public void onRemoved() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final List<BlockPos> members = this.getMultiblockMembers();
        this.isController = true;
        this.controllerPos = this.worldPosition;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (!(be instanceof StatsDisplayPanelBE)) {
                continue;
            }
            final StatsDisplayPanelBE panel = (StatsDisplayPanelBE)be;
            panel.isController = true;
            panel.controllerPos = pos;
            panel.multiblockWidth = 1;
            panel.multiblockHeight = 1;
            panel.setChanged();
            this.level.sendBlockUpdated(pos, panel.getBlockState(), panel.getBlockState(), 3);
        }
        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (be instanceof StatsDisplayPanelBE panel) {
                panel.detectAndFormMultiblock();
                break;
            }
        }
    }

    public void refreshPlacementMultiblock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.multiblockMergeDisabled) {
            if (this.resetToStandaloneMultiblock()) {
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
            return;
        }
        this.detectAndFormMultiblock();
    }

    public boolean isMultiblockMergeDisabled() {
        return this.multiblockMergeDisabled;
    }

    public void setMultiblockMergeDisabled(boolean multiblockMergeDisabled) {
        if (this.multiblockMergeDisabled == multiblockMergeDisabled) {
            return;
        }
        this.multiblockMergeDisabled = multiblockMergeDisabled;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void detectAndFormMultiblock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.multiblockMergeDisabled) {
            if (this.resetToStandaloneMultiblock()) {
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
            return;
        }
        final BlockState myState = this.getBlockState();
        if (!myState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        final Direction facing = (Direction)myState.getValue(HorizontalDirectionalBlock.FACING);
        final Direction right = facing.getClockWise();
        final Set<BlockPos> connected = new HashSet<BlockPos>();
        final Queue<BlockPos> toCheck = new LinkedList<BlockPos>();
        toCheck.add(this.worldPosition);
        connected.add(this.worldPosition);
        while (!toCheck.isEmpty()) {
            final BlockPos current = toCheck.poll();
            for (Direction dir : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
                final BlockPos neighbor = current.relative(dir);
                if (!connected.contains(neighbor)) {
                    final BlockEntity nbe = this.level.getBlockEntity(neighbor);
                    if (nbe instanceof StatsDisplayPanelBE panel && !panel.isMultiblockMergeDisabled()) {
                        final BlockState nState = panel.getBlockState();
                        if (nState.hasProperty(HorizontalDirectionalBlock.FACING) && nState.getValue(HorizontalDirectionalBlock.FACING) == facing && nState.getBlock() == myState.getBlock()) {
                            connected.add(neighbor);
                            toCheck.add(neighbor);
                        }
                    }
                }
            }
        }
        final Direction rightDir = right;
        final BlockPos ref = connected.iterator().next();
        final int refH = dotAlongAxis(ref, rightDir);
        final int refV = ref.getY();
        final Set<Long> occupiedHV = new HashSet<Long>();
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        final Iterator<BlockPos> iterator = connected.iterator();
        BlockPos p = null;
        while (iterator.hasNext()) {
            p = iterator.next();
            final int h = dotAlongAxis(p, rightDir) - refH;
            final int v = p.getY() - refV;
            occupiedHV.add(packHV(h, v));
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        int bestArea = 0;
        int bestRH = minH;
        int bestRV = minV;
        int bestRW = 1;
        int bestRHt = 1;
        for (int sv = minV; sv <= maxV; ++sv) {
            for (int sh = minH; sh <= maxH; ++sh) {
                if (occupiedHV.contains(packHV(sh, sv))) {
                    int maxW = maxH - sh + 1;
                    for (int rh = 1; rh <= maxV - sv + 1; ++rh) {
                        int rowW = 0;
                        for (int dh = 0; dh < maxW && occupiedHV.contains(packHV(sh + dh, sv + rh - 1)); ++dh) {
                            rowW = dh + 1;
                        }
                        maxW = Math.min(maxW, rowW);
                        if (maxW == 0) {
                            break;
                        }
                        final int area = maxW * rh;
                        if (area > bestArea) {
                            bestArea = area;
                            bestRH = sh;
                            bestRV = sv;
                            bestRW = maxW;
                            bestRHt = rh;
                        }
                    }
                }
            }
        }
        final int w = bestRW;
        final int h2 = bestRHt;
        final Set<BlockPos> inRect = new HashSet<BlockPos>();
        for (BlockPos p2 : connected) {
            final int ph = dotAlongAxis(p2, rightDir) - refH;
            final int pv = p2.getY() - refV;
            if (ph >= bestRH && ph < bestRH + w && pv >= bestRV && pv < bestRV + h2) {
                inRect.add(p2);
            }
        }
        final BlockPos ctrlPos = inRect.stream().min(Comparator.<BlockPos, Integer>comparing(Vec3i::getY).thenComparing(bp -> dotAlongAxis(bp, rightDir))).orElse(this.worldPosition);
        for (BlockPos p3 : connected) {
            final BlockEntity be = this.level.getBlockEntity(p3);
            if (be instanceof StatsDisplayPanelBE panel) {
                if (inRect.contains(p3)) {
                    panel.isController = p3.equals(ctrlPos);
                    panel.controllerPos = ctrlPos;
                    panel.multiblockWidth = w;
                    panel.multiblockHeight = h2;
                }
                else {
                    panel.isController = true;
                    panel.controllerPos = p3;
                    panel.multiblockWidth = 1;
                    panel.multiblockHeight = 1;
                }
                panel.setChanged();
                this.level.sendBlockUpdated(p3, panel.getBlockState(), panel.getBlockState(), 3);

                this.level.invalidateCapabilities(p3);
            }
        }

        if (this.level.getBlockEntity(ctrlPos) instanceof StatsDisplayPanelBE controller) {
            for (BlockPos memberPos : inRect) {
                if (memberPos.equals(ctrlPos)) {
                    continue;
                }
                if (this.level.getBlockEntity(memberPos) instanceof StatsDisplayPanelBE member) {
                    member.feConsumer.transferChargeTo(controller.feConsumer);
                }
            }
        }
    }

    private boolean resetToStandaloneMultiblock() {
        boolean changed = !this.isController
                || this.controllerPos == null
                || !this.controllerPos.equals(this.worldPosition)
                || this.multiblockWidth != 1
                || this.multiblockHeight != 1;
        this.isController = true;
        this.controllerPos = this.worldPosition;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        if (changed && this.level != null && !this.level.isClientSide()) {
            this.level.invalidateCapabilities(this.worldPosition);
        }
        return changed;
    }

    private static long packHV(int h, int v) {
        return (long)h << 32 | ((long)v & 0xFFFFFFFFL);
    }

    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }

    public List<BlockPos> getMultiblockMembers() {
        final List<BlockPos> members = new ArrayList<BlockPos>();
        final BlockPos origin = (this.controllerPos != null) ? this.controllerPos : this.worldPosition;
        if (this.level == null || this.multiblockWidth <= 0 || this.multiblockHeight <= 0) {
            members.add(this.worldPosition);
            return members;
        }
        final BlockState myState = this.getBlockState();
        if (!myState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            members.add(this.worldPosition);
            return members;
        }
        final Direction facing = (Direction)myState.getValue(HorizontalDirectionalBlock.FACING);
        final Direction right = facing.getClockWise();
        for (int dy = 0; dy < this.multiblockHeight; ++dy) {
            for (int dx = 0; dx < this.multiblockWidth; ++dx) {
                final BlockPos pos = origin.relative(right, dx).above(dy);
                members.add(pos);
            }
        }
        return members;
    }

    public ScanSettings getScanSettings() {
        return this.scanSettings;
    }

    public boolean isScanEnabled() {
        return this.scanSettings.getScanMode() == ScanSettings.ScanMode.AUTO;
    }

    public void setScanEnabled(boolean enabled) {
        this.scanSettings.setScanMode(enabled ? ScanSettings.ScanMode.AUTO : ScanSettings.ScanMode.MANUAL);
        this.scanCooldown = 0;
        this.dataPollCooldown = 0;
        if (!enabled) {
            for (ScanSettings.DiscoveredSource ds : this.scanSettings.getDiscoveredSources().values()) {
                ds.active = false;
                for (SourceConfig config : this.sourceRegistry.values()) {
                    if (config.getSourcePos().equals(ds.pos)) {
                        config.setActive(false);
                    }
                }
            }
        }
        this.needsSync = true;
        this.setChanged();
    }

    public void triggerManualScan() {
        this.executeScan();
    }

    public void markDirtyAndSync() {
        this.needsSync = true;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private static final long SETTINGS_HOLD_NANOS = 1_000_000_000L;

    private long settingsHoldUntil;

    // hold local edits, the once a second update would revert a toggle you just flipped
    public void holdClientSettings() {
        if (this.level != null && this.level.isClientSide()) {
            this.settingsHoldUntil = System.nanoTime() + SETTINGS_HOLD_NANOS;
        }
    }

    private boolean clientSettingsHeld() {
        return this.level != null && this.level.isClientSide()
                && System.nanoTime() - this.settingsHoldUntil < 0L;
    }

    private void executeScan() {
        if (this.level == null) {
            return;
        }
        final long tick = this.level.getGameTime();
        this.scanSettings.setLastScanTick(tick);
        final int r = this.scanSettings.getScanRadius();
        for (ScanSettings.DiscoveredSource ds : this.scanSettings.getDiscoveredSources().values()) {
            ds.active = false;
        }
        final HashSet<String> seenSourceIds = new HashSet<String>();
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-r, -r, -r), this.worldPosition.offset(r, r, r))) {
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (be != null) {
                if (be == this) {
                    continue;
                }
                final BlockEntity sourceBE = this.normalizeScannedSourceBE(be);
                if (sourceBE == this) {
                    continue;
                }
                final String sourceType = this.detectSourceTypeFromBE(sourceBE);
                if (sourceType.isEmpty()) {
                    continue;
                }
                final BlockPos sourcePos = sourceBE.getBlockPos().immutable();
                final String sourceId = this.createScanSourceId(sourceType, sourcePos);
                if (!seenSourceIds.add(sourceId)) {
                    continue;
                }
                final ScanSettings.DiscoveredSource ds2 = this.scanSettings.getOrCreateSource(sourceId, sourcePos, sourceType);
                ds2.active = true;
                ds2.lastSeenTick = tick;
                if (!this.scanSettings.isSourceEnabled(sourceId)) {
                    continue;
                }
                this.autoReadFromNearby(sourcePos, sourceBE);
                this.refreshDiscoveredSourceChannels(ds2);
            }
        }
        this.removeSupersededDiscoveredSources(seenSourceIds);
        this.needsSync = true;
        this.setChanged();
    }

    private void pollDiscoveredSources() {
        if (this.level == null) {
            return;
        }
        boolean changed = false;
        for (ScanSettings.DiscoveredSource ds : this.scanSettings.getDiscoveredSources().values()) {
            if (ds.active) {
                if (!this.scanSettings.isSourceEnabled(ds.sourceId)) {
                    continue;
                }
                final BlockEntity be = this.level.getBlockEntity(ds.pos);
                if (be == null) {
                    continue;
                }
                final BlockEntity sourceBE = this.normalizeScannedSourceBE(be);
                final String sourceType = this.detectSourceTypeFromBE(sourceBE);
                if (sourceType.isEmpty()) {
                    continue;
                }
                ds.pos = sourceBE.getBlockPos().immutable();
                ds.sourceType = sourceType;
                this.autoReadFromNearby(ds.pos, sourceBE);
                this.refreshDiscoveredSourceChannels(ds);
                changed = true;
            }
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    private String detectSourceTypeFromBE(BlockEntity be) {
        final String dieselSourceType = DieselCompatHelper.getSourceType(be);
        if (!dieselSourceType.isEmpty()) {
            return dieselSourceType;
        }
        final String className = be.getClass().getSimpleName();
        if (className.contains("StressGauge")) {
            return "Stressometer";
        }
        if (className.contains("SpeedGauge")) {
            return "Speedometer";
        }
        if (className.contains("ThresholdSwitch")) {
            return "Stockpile Switch";
        }
        if (className.contains("FluidTank")) {
            return "Fluid Tank";
        }
        if (className.contains("SmartObserver")) {
            return "Content Observer";
        }
        return "";
    }

    private BlockEntity normalizeScannedSourceBE(BlockEntity be) {
        if (be instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                return controller;
            }
        }
        return be;
    }

    private String createScanSourceId(String sourceType, BlockPos sourcePos) {
        return sourceType.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_" + sourcePos.toShortString().replaceAll("[^0-9\\-]", "_");
    }

    private void removeSupersededDiscoveredSources(Set<String> activeSourceIds) {
        if (this.level == null || activeSourceIds.isEmpty()) {
            return;
        }
        final Iterator<Map.Entry<String, ScanSettings.DiscoveredSource>> iterator = this.scanSettings.getDiscoveredSources().entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, ScanSettings.DiscoveredSource> entry = iterator.next();
            if (activeSourceIds.contains(entry.getKey())) {
                continue;
            }
            final ScanSettings.DiscoveredSource discovered = entry.getValue();
            if (discovered == null || discovered.pos == null || !this.level.isLoaded(discovered.pos)) {
                continue;
            }
            final BlockEntity be = this.level.getBlockEntity(discovered.pos);
            if (be == null) {
                continue;
            }
            final BlockEntity sourceBE = this.normalizeScannedSourceBE(be);
            final String sourceType = this.detectSourceTypeFromBE(sourceBE);
            if (sourceType.isEmpty()) {
                continue;
            }
            final String normalizedSourceId = this.createScanSourceId(sourceType, sourceBE.getBlockPos().immutable());
            if (!activeSourceIds.contains(normalizedSourceId)) {
                continue;
            }
            if (this.scanSettings.getDisabledSources().remove(entry.getKey())) {
                this.scanSettings.getDisabledSources().add(normalizedSourceId);
            }
            iterator.remove();
        }
    }

    private void scanTopologyNetwork() {
        if (this.level == null) {
            return;
        }
        final int radius = (int)ModConfiguration.COMMON.topologyScanRadius.get();
        final BlockPos seed = NetworkDiscovery.findSeed(this.level, this.worldPosition, radius, this.getTopologySeedCandidates());
        if (seed == null) {
            if (!(this.cachedNetworkGraph instanceof NetworkGraph graph) || !graph.isEmpty()) {
                this.cachedNetworkGraph = NetworkGraph.EMPTY;
                this.needsSync = true;
                this.setChanged();
            }
            return;
        }
        this.cachedNetworkGraph = NetworkDiscovery.discover(this.level, seed, this.settings.getTopologyMaxNodes());
        this.needsSync = true;
        this.setChanged();
    }

    public Object getCachedNetworkGraph() {
        return this.cachedNetworkGraph;
    }

    public boolean hasTrainConnection() {
        return this.hasTrainConnection;
    }

    public TrainMapData getTrainMapData() {
        return this.trainMapData;
    }

    public List<String> getTrainMapGroups() {
        return this.trainMapGroups;
    }

    public void setTrainMapGroups(List<String> groups) {
        this.trainMapGroups.clear();
        this.trainMapGroups.addAll(groups);
        this.setChanged();
    }

    public void enableTrainConnection() {
        this.lastTrainConnectionTick = ((this.level != null) ? this.level.getGameTime() : 0L);
        boolean changed = false;
        if (!this.hasTrainConnection) {
            this.hasTrainConnection = true;
            this.lastTrainScanTick = 0L;
            changed = true;
        }
        // train stations never fill DataChannels so pick the train map page, rechecked every call for old saves

        if (this.settings.getGraphType() == StatsDisplaySettings.GraphType.LINE_CHART && this.getActiveChannels().isEmpty()) {
            this.settings.setGraphType(StatsDisplaySettings.GraphType.TRAIN_MAP);
            this.settings.setDisplayMode(StatsDisplaySettings.DisplayMode.SPECIALIZED);
            changed = true;
        }
        if (changed) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    public void markTrainMapActive() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final long currentTick = this.level.getGameTime();
        this.lastTrainActivityTick = currentTick;
        if (this.hasTrainConnection) {
            this.lastTrainConnectionTick = currentTick;
        }
    }

    public void markTopologyActive() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final long currentTick = this.level.getGameTime();
        this.lastTopologyActivityTick = currentTick;
        final boolean missingGraph = !(this.cachedNetworkGraph instanceof NetworkGraph graph) || graph.isEmpty();
        if (missingGraph && currentTick - this.lastTopologyScanTick >= 20L) {
            this.lastTopologyScanTick = currentTick;
            this.scanTopologyNetwork();
        }
    }

    private boolean shouldScanTrainMap(long currentTick) {
        return this.settings.getGraphType() == StatsDisplaySettings.GraphType.TRAIN_MAP || currentTick - this.lastTrainActivityTick <= TRAIN_ACTIVITY_WINDOW;
    }

    private int getTrainMapSyncDebounce(long currentTick) {
        return this.settings.getGraphType() == StatsDisplaySettings.GraphType.TRAIN_MAP || currentTick - this.lastTrainActivityTick <= TRAIN_ACTIVITY_WINDOW ? TRAIN_SCAN_INTERVAL : DEFAULT_SYNC_DEBOUNCE_TICKS;
    }

    private boolean shouldScanTopology(long currentTick) {
        return this.settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY || currentTick - this.lastTopologyActivityTick <= TOPOLOGY_ACTIVITY_WINDOW;
    }

    private void autoReadFromNearby(BlockPos sourcePos, BlockEntity sourceBE) {
        try {
            if (DieselCompatHelper.emitAutoData(this, sourcePos, sourceBE) && !(sourceBE instanceof FluidTankBlockEntity)) {
                return;
            }
            final String className = sourceBE.getClass().getSimpleName();
            if (className.contains("StressGauge") && sourceBE instanceof StressGaugeBlockEntity) {
                final StressGaugeBlockEntity gauge = (StressGaugeBlockEntity)sourceBE;
                this.setSourceType("Stressometer");
                final float stressPct = gauge.dialTarget * 100.0f;
                this.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", stressPct, MetricType.STRESS_PERCENT);
                final float networkStress = gauge.getNetworkStress();
                this.onAutoData(sourcePos, "Stressometer", "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
                final float networkCapacity = gauge.getNetworkCapacity();
                this.onAutoData(sourcePos, "Stressometer", "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
                final float remaining = networkCapacity - networkStress;
                this.onAutoData(sourcePos, "Stressometer", "remaining_su", "Remaining (SU)", Math.max(0.0f, remaining), MetricType.STRESS_SU);
                final float rpm = Math.abs(gauge.getSpeed());
                this.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", rpm, MetricType.RPM);
                final boolean overstressed = gauge.isOverStressed();
                this.onAutoData(sourcePos, "Stressometer", "overstressed", "Overstressed", overstressed ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            }
            else if (className.contains("SpeedGauge") && sourceBE instanceof SpeedGaugeBlockEntity) {
                final SpeedGaugeBlockEntity speed = (SpeedGaugeBlockEntity)sourceBE;
                this.setSourceType("Speedometer");
                final float rpm2 = speed.getSpeed();
                this.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", rpm2, MetricType.RPM);
                final float absRpm = Math.abs(rpm2);
                this.onAutoData(sourcePos, "Speedometer", "abs_rpm", "Speed (abs)", absRpm, MetricType.RPM);
                final float dialPct = speed.dialTarget * 100.0f;
                this.onAutoData(sourcePos, "Speedometer", "speed_pct", "Speed %", dialPct, MetricType.GENERIC);
            }
            else if (className.contains("ThresholdSwitch") && sourceBE instanceof ThresholdSwitchBlockEntity) {
                final ThresholdSwitchBlockEntity tsw = (ThresholdSwitchBlockEntity)sourceBE;
                this.setSourceType("Stockpile Switch");
                final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(tsw);
                final MetricType amountType = readout.amountMetricType();
                this.onAutoData(sourcePos, "Stockpile Switch", "fill_pct", "Fill Level %", readout.fillPercent(), MetricType.FILL_LEVEL);
                this.onAutoData(sourcePos, "Stockpile Switch", "stock_level", "Current Amount", readout.currentAmount(), amountType);
                this.onAutoData(sourcePos, "Stockpile Switch", "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
                this.onAutoData(sourcePos, "Stockpile Switch", "min_level", "Minimum Level", readout.minAmount(), amountType);
                this.onAutoData(sourcePos, "Stockpile Switch", "upper_thresh", "Upper Threshold", readout.upperThreshold(), amountType);
                this.onAutoData(sourcePos, "Stockpile Switch", "lower_thresh", "Lower Threshold", readout.lowerThreshold(), amountType);
                final boolean powered = tsw.isPowered();
                this.onAutoData(sourcePos, "Stockpile Switch", "powered", "Signal Output", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
                final Level tswLevel = tsw.getLevel();
                if (tswLevel != null) {
                    final Direction tswFacing = DirectedDirectionalBlock.getTargetDirection(tsw.getBlockState());
                    if (tswFacing != null) {
                        final BlockPos targetPos = tsw.getBlockPos().relative(tswFacing);
                        if (tswLevel.getBlockEntity(targetPos) != null) {
                            this.recordObservedTarget(sourcePos, "Stockpile Switch", tswLevel, targetPos);
                        } else {
                            this.clearObservedTargetIfPresent(sourcePos, "Stockpile Switch");
                        }
                    } else {
                        this.clearObservedTargetIfPresent(sourcePos, "Stockpile Switch");
                    }
                }
            }
            else if (className.contains("FluidTank") && sourceBE instanceof FluidTankBlockEntity) {
                FluidTankBlockEntity tank = (FluidTankBlockEntity)sourceBE;
                final FluidTankBlockEntity controller = tank.getControllerBE();
                if (controller != null) {
                    tank = controller;
                }
                String srcType = "Fluid Tank";
                final float fillPct2 = tank.getFillState() * 100.0f;
                this.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", fillPct2, MetricType.FILL_LEVEL);
                final FluidTank tankInv = tank.getTankInventory();
                if (tankInv != null) {
                    final float fluidAmount = (float)tankInv.getFluidAmount();
                    this.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", fluidAmount, MetricType.FLUID_MB);
                    final float tankCapacity = (float)tankInv.getCapacity();
                    this.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", tankCapacity, MetricType.FLUID_MB);
                }
                final BoilerData boiler = tank.boiler;
                if (boiler != null && boiler.isActive()) {
                    srcType = "Steam Boiler";
                    this.setSourceType("Steam Boiler");
                    final int heatLevel = boiler.activeHeat;
                    this.onAutoData(sourcePos, srcType, "heat_level", "Heat Level", (float)heatLevel, MetricType.HEAT_LEVEL);
                    final float waterSupply = boiler.waterSupply;
                    this.onAutoData(sourcePos, srcType, "water_supply", "Water Supply", waterSupply, MetricType.WATER_SUPPLY);
                    final int engines = boiler.attachedEngines;
                    this.onAutoData(sourcePos, srcType, "engines", "Engines", (float)engines, MetricType.ENGINE_COUNT);
                }
                else {
                    this.setSourceType("Fluid Tank");
                    this.recordObservedTarget(sourcePos, srcType, tank.getLevel(), tank.getBlockPos());
                }
            }
            else if (className.contains("SmartObserver")) {
                if (sourceBE instanceof SmartObserverBlockEntity observer) {
                    this.setSourceType("Content Observer");
                    final String srcType = "Content Observer";
                    final Direction facing = DirectedDirectionalBlock.getTargetDirection(observer.getBlockState());
                    if (facing == null) {
                        this.clearObservedTargetIfPresent(sourcePos, srcType);
                        this.registerOrUpdateSource(sourcePos, srcType);
                    }
                    else {
                        final Level level = observer.getLevel();
                        if (level == null) {
                            this.registerOrUpdateSource(sourcePos, srcType);
                        }
                        else {
                            final BlockEntity observedBE = level.getBlockEntity(observer.getBlockPos().relative(facing));
                            if (observedBE == null) {
                                this.clearObservedTargetIfPresent(sourcePos, srcType);
                                this.registerOrUpdateSource(sourcePos, srcType);
                            }
                            else {
                                boolean foundData = false;
                                final IItemHandler itemHandler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(level, observedBE.getBlockPos(), observedBE, null);
                                if (itemHandler != null) {
                                    this.readObservedItemHandler(sourcePos, srcType, itemHandler);
                                    this.recordObservedTarget(sourcePos, srcType, level, observedBE.getBlockPos());
                                    foundData = true;
                                }
                                if (observedBE instanceof FluidTankBlockEntity observedTank) {
                                    final FluidTankBlockEntity controller = observedTank.getControllerBE();
                                    final FluidTankBlockEntity tank = controller != null ? controller : observedTank;
                                    this.recordObservedTarget(sourcePos, srcType, level, tank.getBlockPos());
                                    DieselCompatHelper.emitAutoData(this, tank.getBlockPos(), tank, null);
                                    final FluidTank tankInv = tank.getTankInventory();
                                    if (tankInv != null) {
                                        this.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
                                        this.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", (float)tankInv.getFluidAmount(), MetricType.FLUID_MB);
                                        this.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", (float)tankInv.getCapacity(), MetricType.FLUID_MB);
                                        final FluidStack stack = tankInv.getFluid();
                                        if (!stack.isEmpty()) {
                                            final String fluidName = stack.getHoverName().getString();
                                            final String metricId = "fluid_" + this.sanitizeMetricId(fluidName);
                                            this.onAutoData(sourcePos, srcType, metricId, fluidName + " (mB)", (float)stack.getAmount(), MetricType.FLUID_MB);
                                        }
                                        foundData = true;
                                    }
                                }
                                else {
                                    final IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, observedBE.getBlockPos(), null);
                                    if (fluidHandler != null) {
                                    boolean fluidEmitted = false;
                                    float totalFluid = 0.0f;
                                    float totalCapacity = 0.0f;
                                    for (int tankIdx = 0; tankIdx < fluidHandler.getTanks(); ++tankIdx) {
                                        totalCapacity += Math.max(0.0f, (float)fluidHandler.getTankCapacity(tankIdx));
                                        final FluidStack stack = fluidHandler.getFluidInTank(tankIdx);
                                        if (stack.isEmpty()) {
                                            continue;
                                        }
                                        final String fluidName = stack.getHoverName().getString();
                                        final String metricId = "fluid_" + this.sanitizeMetricId(fluidName);
                                        final float amount = (float)stack.getAmount();
                                        totalFluid += amount;
                                        this.onAutoData(sourcePos, srcType, metricId, fluidName + " (mB)", amount, MetricType.FLUID_MB);
                                        foundData = true;
                                        fluidEmitted = true;
                                    }
                                    if (fluidEmitted) {
                                        this.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
                                        this.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
                                        this.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f, MetricType.FILL_LEVEL);
                                        DieselCompatHelper.emitAutoData(this, observedBE.getBlockPos(), observedBE, null);
                                        this.recordObservedTarget(sourcePos, srcType, level, observedBE.getBlockPos());
                                    }
                                    }
                                }
                                if (!foundData) {
                                    this.clearObservedTargetIfPresent(sourcePos, srcType);
                                    this.registerOrUpdateSource(sourcePos, srcType);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex) {}
    }

    private void refreshDiscoveredSourceChannels(ScanSettings.DiscoveredSource discoveredSource) {
        if (discoveredSource == null) {
            return;
        }
        discoveredSource.channels.clear();
        if (discoveredSource.pos == null) {
            return;
        }
        final ArrayList<String> matchingSourceIds = new ArrayList<String>();
        for (SourceConfig config : this.sourceRegistry.values()) {
            if (config != null && discoveredSource.pos.equals(config.getSourcePos())) {
                matchingSourceIds.add(config.getSourceId());
            }
        }
        if (matchingSourceIds.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DataChannel> chEntry : this.channels.entrySet()) {
            final String channelId = chEntry.getKey();
            if (!channelId.contains("auto_")) {
                continue;
            }
            for (String sourceId : matchingSourceIds) {
                if (!channelId.startsWith("src_" + sourceId + "_")) {
                    continue;
                }
                final DataChannel channel = chEntry.getValue();
                if (channel.getStore().getSampleCount() > 0) {
                    discoveredSource.channels.add(new ScanSettings.ChannelSnapshot(channelId, channel.getLabel(), channel.getStore().getLatestValue()));
                }
                break;
            }
        }
    }

    private void readObservedItemHandler(BlockPos sourcePos, String sourceType, IItemHandler handler) {
        final LinkedHashMap<String, Integer> itemCounts = new LinkedHashMap<String, Integer>();
        final LinkedHashMap<String, String> itemNames = new LinkedHashMap<String, String>();
        int totalItems = 0;
        int slotCapacity = 0;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            slotCapacity += handler.getSlotLimit(slot);
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            final String displayName = stack.getHoverName().getString();
            final String registryName = (BuiltInRegistries.ITEM.getKey(stack.getItem()) != null) ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : this.sanitizeMetricId(displayName);
            itemCounts.merge(registryName, stack.getCount(), Integer::sum);
            itemNames.putIfAbsent(registryName, displayName);
            totalItems += stack.getCount();
        }
        this.onAutoData(sourcePos, sourceType, "total_items", "Total Items", (float)totalItems, MetricType.ITEM_COUNT);
        this.onAutoData(sourcePos, sourceType, "slot_capacity", "Slot Capacity", (float)slotCapacity, MetricType.ITEM_COUNT);

        final java.util.HashSet<String> emittedItemMetricIds = new java.util.HashSet<String>();
        itemCounts.forEach((registryName, count) -> {
            final String metricId = "item_" + this.sanitizeMetricId(registryName);
            emittedItemMetricIds.add(metricId);
            this.onAutoData(sourcePos, sourceType, metricId, itemNames.get(registryName), (float)count.intValue(), MetricType.ITEM_COUNT);
        });

        final SourceConfig srcCfg = this.registerOrUpdateSource(sourcePos, sourceType);
        if (srcCfg != null) {
            final String prefix = "src_" + srcCfg.getSourceId() + "_auto_";
            for (String chId : new java.util.ArrayList<>(this.channels.keySet())) {
                if (!chId.startsWith(prefix)) continue;
                final String metricId = chId.substring(prefix.length());
                if (!metricId.startsWith("item_")) continue;
                if (emittedItemMetricIds.contains(metricId)) continue;
                final DataChannel existing = this.channels.get(chId);
                if (existing == null || existing.getStore().getLatestValue() <= 0.0f) continue;
                this.onAutoData(sourcePos, sourceType, metricId, existing.getLabel(), 0.0f, MetricType.ITEM_COUNT);
            }
        }
        this.onAutoData(sourcePos, sourceType, "unique_types", "Unique Types", (float)itemCounts.size(), MetricType.ITEM_COUNT);
    }

    private void recordObservedTarget(BlockPos sourcePos, String sourceType, Level level, BlockPos targetPos) {
        if (level == null || targetPos == null) {
            return;
        }
        try {
            final SourceConfig cfg = this.registerOrUpdateSource(sourcePos, sourceType);
            if (cfg == null) {
                return;
            }
            String label = level.getBlockState(targetPos).getBlock().getName().getString();
            if (label == null || label.isEmpty()) {
                label = "Inventory";
            }
            if (cfg.setObservedTarget(targetPos, label)) {
                this.needsSync = true;
                this.setChanged();
            }
        }
        catch (Exception ignored) {}
    }

    private void clearObservedTargetIfPresent(BlockPos sourcePos, String sourceType) {
        try {
            final SourceConfig cfg = this.registerOrUpdateSource(sourcePos, sourceType);
            if (cfg == null) {
                return;
            }
            if (cfg.clearObservedTarget()) {
                this.needsSync = true;
                this.setChanged();
            }
        }
        catch (Exception ignored) {}
    }

    private void publishBattleScreenChannels(BlockPos sourcePos, String sourceType, List<BattleObserverSnapshot> observers) {
        final SourceConfig src = this.registerOrUpdateSourceExact(sourcePos, sourceType);
        if (src == null) {
            return;
        }

        final Set<String> emittedMetricIds = new HashSet<String>();
        int observerCount = observers.size();
        int weaponObserverCount = 0;
        int totalMissingBlocks = 0;
        int totalNewBlocks = 0;
        double totalHealthPercent = 0.0;

        for (BattleObserverSnapshot observer : observers) {
            totalHealthPercent += observer.healthPercent();
            totalMissingBlocks += observer.missingBlocks();
            totalNewBlocks += observer.newBlocks();
            if (observer.weapon().detected()) {
                weaponObserverCount++;
            }
        }

        final float overallHealthPercent = observerCount == 0 ? 0.0f : (float)(totalHealthPercent / observerCount);
        this.emitBattleScreenChannel(src, emittedMetricIds, "battle_overall_health_pct", "Battle Overall Integrity %", overallHealthPercent, MetricType.FILL_LEVEL);
        this.emitBattleScreenChannel(src, emittedMetricIds, "battle_total_missing_blocks", "Battle Lost Blocks", totalMissingBlocks, MetricType.ITEM_COUNT);
        this.emitBattleScreenChannel(src, emittedMetricIds, "battle_total_new_blocks", "Battle New Blocks", totalNewBlocks, MetricType.ITEM_COUNT);
        this.emitBattleScreenChannel(src, emittedMetricIds, "battle_observer_count", "Battle Observer Count", observerCount, MetricType.ITEM_COUNT);
        this.emitBattleScreenChannel(src, emittedMetricIds, "battle_weapon_observer_count", "Battle Weapon Observers", weaponObserverCount, MetricType.ITEM_COUNT);

        for (BattleObserverSnapshot observer : observers) {
            final String observerMetricBase = this.buildBattleObserverMetricBase(observer);
            final String observerLabelBase = this.buildBattleObserverLabel(observer);
            this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_health_pct", observerLabelBase + " Integrity %", (float)observer.healthPercent(), MetricType.FILL_LEVEL);
            this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_missing_blocks", observerLabelBase + " Lost Blocks", observer.missingBlocks(), MetricType.ITEM_COUNT);
            this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_new_blocks", observerLabelBase + " New Blocks", observer.newBlocks(), MetricType.ITEM_COUNT);

            if (observer.category() == DamageObserverSettings.ObserverCategory.CARGO) {
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_cargo_items_pct", observerLabelBase + " Cargo Items %", percent(observer.cargoItems(), observer.cargoItemCapacity()), MetricType.FILL_LEVEL);
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_cargo_fluids_pct", observerLabelBase + " Cargo Fluids %", percent(observer.cargoFluidAmount(), observer.cargoFluidCapacity()), MetricType.FILL_LEVEL);
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_cargo_energy_pct", observerLabelBase + " Cargo Energy %", percent(observer.cargoEnergyStored(), observer.cargoEnergyCapacity()), MetricType.FILL_LEVEL);
            }

            if (observer.category() == DamageObserverSettings.ObserverCategory.WEAPONS || observer.weapon().detected()) {
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_weapon_ready_state", observerLabelBase + " Weapon Readiness", weaponReadinessValue(observer.weapon().readiness()), MetricType.FILL_LEVEL);
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_weapon_loaded_state", observerLabelBase + " Weapon Loaded State", weaponLoadedValue(observer.weapon().loadedState()), MetricType.FILL_LEVEL);
                this.emitBattleScreenChannel(src, emittedMetricIds, observerMetricBase + "_weapon_integrity_pct", observerLabelBase + " Weapon Integrity %", percent(observer.weapon().presentBlocks(), observer.weapon().baselineBlocks()), MetricType.FILL_LEVEL);
            }
        }

        this.zeroStaleBattleScreenChannels(src, emittedMetricIds);
    }

    private void emitBattleScreenChannel(SourceConfig sourceConfig, Set<String> emittedMetricIds, String metricId, String label, float value, MetricType type) {
        emittedMetricIds.add(metricId);
        this.onAutoData("src_" + sourceConfig.getSourceId() + "_auto_" + metricId, label, value, type);
    }

    private void zeroStaleBattleScreenChannels(SourceConfig sourceConfig, Set<String> emittedMetricIds) {
        final String prefix = "src_" + sourceConfig.getSourceId() + "_auto_";
        for (String channelId : new ArrayList<String>(this.channels.keySet())) {
            if (!channelId.startsWith(prefix)) {
                continue;
            }
            final String metricId = channelId.substring(prefix.length());
            if (!metricId.startsWith("battle_") || emittedMetricIds.contains(metricId)) {
                continue;
            }
            final DataChannel existing = this.channels.get(channelId);
            if (existing == null || existing.getStore().getSampleCount() == 0 || existing.getStore().getLatestValue() == 0.0f) {
                continue;
            }
            this.onAutoData(channelId, existing.getLabel(), 0.0f, existing.getStore().getMetricType());
        }
    }

    private String buildBattleObserverMetricBase(BattleObserverSnapshot observer) {
        final String nameKey = this.sanitizeMetricId(observer.name());
        final String categoryKey = this.sanitizeMetricId(observer.category().getDisplayName());
        final String posKey = this.sanitizeMetricId(observer.sourcePos().getX() + "_" + observer.sourcePos().getY() + "_" + observer.sourcePos().getZ());
        final String baseKey = nameKey.isEmpty() ? categoryKey : (nameKey + "_" + categoryKey);
        return "battle_obs_" + baseKey + "_" + posKey;
    }

    private String buildBattleObserverLabel(BattleObserverSnapshot observer) {
        final String category = observer.category().getDisplayName();
        final String name = observer.name();
        if (name == null || name.isBlank()) {
            return category;
        }
        return name + " - " + category;
    }

    private static float percent(long amount, long capacity) {
        if (capacity <= 0L) {
            return 0.0f;
        }
        final double percent = amount * 100.0 / capacity;
        return (float)Math.max(0.0, Math.min(100.0, percent));
    }

    private static float percent(int amount, int capacity) {
        if (capacity <= 0) {
            return 0.0f;
        }
        final double percent = amount * 100.0 / capacity;
        return (float)Math.max(0.0, Math.min(100.0, percent));
    }

    private static float weaponReadinessValue(String readiness) {
        return switch (readiness) {
            case "READY" -> 100.0f;
            case "LOADING" -> 60.0f;
            case "UNKNOWN" -> 40.0f;
            case "DAMAGED" -> 15.0f;
            case "OFFLINE" -> 0.0f;
            default -> 25.0f;
        };
    }

    private static float weaponLoadedValue(String loadedState) {
        return switch (loadedState) {
            case "LOADED" -> 100.0f;
            case "LOADING" -> 60.0f;
            case "UNKNOWN" -> 30.0f;
            case "EMPTY", "NOT_APPLICABLE" -> 0.0f;
            default -> 20.0f;
        };
    }

    private String sanitizeMetricId(String name) {
        return (name == null) ? "" : name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private int calculateRedstonePower() {
        final DataChannel ch = this.channels.get(this.redstoneChannelId);
        if (ch == null || ch.getStore().getSampleCount() == 0) {
            return 0;
        }
        final float value = ch.getStore().getLatestValue();
        if (this.redstoneMode == 0) {
            return (this.redstoneAbove ? (value >= this.redstoneThreshold) : (value <= this.redstoneThreshold)) ? 15 : 0;
        }
        final float min = ch.getStore().getMin();
        final float max = ch.getStore().getMax();
        final float range = max - min;
        if (range <= 0.0f) {
            return 0;
        }
        return Math.round(Math.max(0.0f, Math.min(15.0f, (value - min) / range * 15.0f)));
    }

    public boolean isRedstoneEnabled() {
        return this.redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean enabled) {
        this.redstoneEnabled = enabled;
        this.needsSync = true;
        this.setChanged();
    }

    public String getRedstoneChannelId() {
        return this.redstoneChannelId;
    }

    public void setRedstoneChannelId(String id) {
        this.redstoneChannelId = id;
        this.needsSync = true;
        this.setChanged();
    }

    public float getRedstoneThreshold() {
        return this.redstoneThreshold;
    }

    public void setRedstoneThreshold(float t) {
        this.redstoneThreshold = t;
        this.needsSync = true;
        this.setChanged();
    }

    public boolean isRedstoneAbove() {
        return this.redstoneAbove;
    }

    public void setRedstoneAbove(boolean above) {
        this.redstoneAbove = above;
        this.needsSync = true;
        this.setChanged();
    }

    public int getRedstoneMode() {
        return this.redstoneMode;
    }

    public void setRedstoneMode(int mode) {
        this.redstoneMode = mode;
        this.needsSync = true;
        this.setChanged();
    }

    public int getRedstonePower() {
        return this.cachedRedstonePower;
    }

    private void recalculateEfficiency() {
        final EfficiencyInputs snapshot = EfficiencySampler.fromPanel(this);
        if (snapshot == null) {
            this.clearEfficiencyScore();
            return;
        }
        final String previousGrade = (this.latestEfficiencyScore != null) ? this.latestEfficiencyScore.grade() : null;
        final float previousScore = (this.latestEfficiencyScore != null) ? this.latestEfficiencyScore.compositeScore() : 0.0f;

        final int elapsedTicks = this.gradeHistory.consumeElapsedTicks(
                this.level != null ? this.level.getGameTime() : 0L);
        final EfficiencyGrader.Evaluation evaluation = EfficiencyGrader.evaluate(
                snapshot, this.gradeHistory.getODEState(), elapsedTicks);
        this.latestEfficiencyScore = evaluation.score();
        this.latestEfficiencyTrace = evaluation.trace();
        if (this.level != null) {
            this.gradeHistory.recordScore(this.latestEfficiencyScore, this.level.getGameTime());
        }
        final DataChannel effChannel = this.getOrCreateChannel("auto_efficiency_score", "Efficiency Score");
        if (effChannel.getStore().getMetricType() != MetricType.EFFICIENCY_SCORE) {
            effChannel.getStore().setMetricType(MetricType.EFFICIENCY_SCORE);
        }
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        effChannel.getStore().addSample(this.latestEfficiencyScore.compositeScore(), gameTick);
        if (previousGrade != null && !previousGrade.equals(this.latestEfficiencyScore.grade())) {
            final float scoreDelta = this.latestEfficiencyScore.compositeScore() - previousScore;
            if (scoreDelta < -5.0f) {
                effChannel.setAlertEnabled(true);
                effChannel.setAlertThreshold(previousScore - 5.0f);
                effChannel.setAlertAbove(false);
            }
        }
        this.needsSync = true;
        this.setChanged();
    }

    private void clearEfficiencyScore() {
        if (this.latestEfficiencyScore != null && "?".equals(this.latestEfficiencyScore.grade())) {
            return;
        }
        this.latestEfficiencyScore = EfficiencyScore.noNetwork();
        this.latestEfficiencyTrace = null;
        this.needsSync = true;
        this.setChanged();
    }

    private boolean hasEfficiencyInputs() {
        return this.hasChannelWithSuffix("_auto_stress_su") && this.hasChannelWithSuffix("_auto_capacity_su");
    }

    private boolean hasChannelWithSuffix(String suffix) {
        for (String channelId : this.channels.keySet()) {
            if (channelId.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> getTopologySeedCandidates() {
        final LinkedHashSet<BlockPos> candidates = new LinkedHashSet<BlockPos>();
        this.collectTopologySeedCandidates(candidates, true, true);
        this.collectDiscoveredTopologySeedCandidates(candidates, true, true);
        this.collectTopologySeedCandidates(candidates, true, false);
        this.collectDiscoveredTopologySeedCandidates(candidates, true, false);
        this.collectTopologySeedCandidates(candidates, false, true);
        this.collectDiscoveredTopologySeedCandidates(candidates, false, true);
        this.collectTopologySeedCandidates(candidates, false, false);
        this.collectDiscoveredTopologySeedCandidates(candidates, false, false);
        return new ArrayList<BlockPos>(candidates);
    }

    private void collectTopologySeedCandidates(Set<BlockPos> candidates, boolean active, boolean gaugeLike) {
        for (SourceConfig config : this.sourceRegistry.values()) {
            if (config == null || config.getSourcePos() == null || config.isActive() != active) {
                continue;
            }
            if (this.isTopologyPreferredSourceType(config.getSourceType()) != gaugeLike) {
                continue;
            }
            candidates.add(config.getSourcePos().immutable());
        }
    }

    private void collectDiscoveredTopologySeedCandidates(Set<BlockPos> candidates, boolean active, boolean gaugeLike) {
        for (ScanSettings.DiscoveredSource discoveredSource : this.scanSettings.getDiscoveredSources().values()) {
            if (discoveredSource == null || discoveredSource.pos == null || discoveredSource.active != active || !this.scanSettings.isSourceEnabled(discoveredSource.sourceId)) {
                continue;
            }
            if (this.isTopologyPreferredSourceType(discoveredSource.sourceType) != gaugeLike) {
                continue;
            }
            candidates.add(discoveredSource.pos.immutable());
        }
    }

    private boolean isTopologyPreferredSourceType(String sourceType) {
        return "Stressometer".equals(sourceType) || "Speedometer".equals(sourceType);
    }

    public void refreshDerivedDataAfterSettingsChange() {
        if (this.efficiencyRecalcCooldown <= 0) {
            this.efficiencyRecalcCooldown = EfficiencyGrader.DEFAULT_INTERVAL_TICKS;
        }
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.settings.getGraphType() == StatsDisplaySettings.GraphType.EFFICIENCY) {
            this.recalculateEfficiency();
        }
        if (this.settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY) {
            this.scanTopologyNetwork();
            this.lastTopologyScanTick = this.level.getGameTime();
        }
    }

    public EfficiencyScore getLatestEfficiencyScore() {
        return this.latestEfficiencyScore;
    }

    public EfficiencyTrace getLatestEfficiencyTrace() {
        return this.latestEfficiencyTrace;
    }

    public GradeHistory getGradeHistory() {
        return this.gradeHistory;
    }

    public TimeSeriesStore getStore() {
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() > 0) {
                return ch.getStore();
            }
        }
        return this.getOrCreateChannel("line0", "Line 0").getStore();
    }

    public AnnotationManager getAnnotationManager() {
        return this.annotationManager;
    }

    public StatsDisplaySettings getSettings() {
        return this.settings;
    }

    public boolean isController() {
        return this.isController;
    }

    public BlockPos getControllerPos() {
        return this.controllerPos;
    }

    public StatsDisplayPanelBE getControllerBE() {
        if (this.isController) {
            return this;
        }
        if (this.level != null && this.controllerPos != null) {
            final BlockEntity be = this.level.getBlockEntity(this.controllerPos);
            if (be instanceof StatsDisplayPanelBE controller && controller.isController()) {
                return controller;
            }
        }
        return this;
    }

    public boolean isPlayerInInteractionRange(Player player) {
        final StatsDisplayPanelBE controller = this.getControllerBE();
        final double range = AntennaRange.directRange(controller.getLevel() != null ? controller.getLevel().dimension() : null,
                64.0, controller.getAntennaTier());
        for (BlockPos memberPos : controller.getMultiblockMembers()) {
            // world point, not block pos. edits on an aero ship got dropped silently. sable tax #4

            final Vec3 member = SableBridge.worldPointOf(controller, Vec3.atCenterOf(memberPos));
            if (member != null && player.distanceToSqr(member) <= range * range) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolvedTier) {
            return;
        }
        this.antennaTier = resolvedTier;
        this.needsSync = true;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public int getMultiblockWidth() {
        return this.multiblockWidth;
    }

    public int getMultiblockHeight() {
        return this.multiblockHeight;
    }

    public AABB getRenderBoundingBox() {
        final BlockPos origin = (this.controllerPos != null) ? this.controllerPos : this.worldPosition;
        AABB base;
        if (this.isController && (this.multiblockWidth > 1 || this.multiblockHeight > 1)) {
            final BlockState state = this.getBlockState();
            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                final Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
                final Direction right = facing.getClockWise();
                final BlockPos corner = origin.relative(right, this.multiblockWidth - 1).above(this.multiblockHeight - 1);
                base = new AABB(origin).minmax(new AABB(corner));
            }
            else {
                base = new AABB(this.worldPosition);
            }
        }
        else {
            base = new AABB(this.worldPosition);
        }
        return base.inflate(64.0);
    }

    public void setSettings(StatsDisplaySettings settings) {
        this.settings = ((settings != null) ? settings : new StatsDisplaySettings());
        this.normalizeSettingsState();
        this.refreshDerivedDataAfterSettingsChange();
        this.needsSync = true;
        this.setChanged();
        if (this.level != null && this.level.isClientSide()) {
            DisplayTextureManager.markDirty(this.worldPosition);
        }
    }

    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide()) {
            DisplayTextureManager.disposeTexture(this.worldPosition);
        }
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        final ListTag channelList = new ListTag();
        for (DataChannel ch : this.channels.values()) {
            channelList.add(ch.save());
        }
        tag.put("Channels", (Tag)channelList);
        final CompoundTag srcReg = new CompoundTag();
        for (Map.Entry<String, SourceConfig> entry : this.sourceRegistry.entrySet()) {
            srcReg.put((String)entry.getKey(), (Tag)entry.getValue().save());
        }
        tag.put("SourceRegistry", (Tag)srcReg);
        final ListTag clutchList = new ListTag();
        for (SafetyClutchLink link : this.safetyClutchLinks.values()) {
            clutchList.add(link.save());
        }
        tag.put("SafetyClutches", (Tag)clutchList);
        final ListTag flywheelList = new ListTag();
        for (KineticFlywheelLink flywheelLink : this.kineticFlywheelLinks.values()) {
            flywheelList.add(flywheelLink.save());
        }
        tag.put("KineticFlywheels", (Tag)flywheelList);
        final ListTag transClutchList = new ListTag();
        for (AutoTransmissionClutchLink tLink : this.autoTransmissionClutchLinks.values()) {
            transClutchList.add(tLink.save());
        }
        tag.put("AutoTransmissionClutches", (Tag)transClutchList);
        final ListTag loadBalancerList = new ListTag();
        for (LoadBalancerLink loadBalancerLink : this.loadBalancerLinks.values()) {
            loadBalancerList.add(loadBalancerLink.save());
        }
        tag.put("LoadBalancers", (Tag)loadBalancerList);
        final ListTag battleScreenLinkList = new ListTag();
        for (BattleScreenLink battleScreenLink : this.battleScreenLinks.values()) {
            battleScreenLinkList.add(battleScreenLink.save());
        }
        tag.put("BattleScreenLinks", (Tag)battleScreenLinkList);
        final ListTag miniDisplayLinkList = new ListTag();
        for (MiniDisplayLink miniDisplayLink : this.miniDisplayLinks.values()) {
            miniDisplayLinkList.add(miniDisplayLink.save(registries));
        }
        tag.put("MiniDisplayLinks", (Tag)miniDisplayLinkList);
        final ListTag manifestLinkList = new ListTag();
        for (FreightManifestLink manifestLink : this.freightManifestLinks.values()) {
            manifestLinkList.add(manifestLink.save(registries));
        }
        tag.put("FreightManifestLinks", (Tag)manifestLinkList);
        final ListTag trainStationLinkList = new ListTag();
        for (TrainStationLink trainStationLink : this.trainStationLinks.values()) {
            trainStationLinkList.add(trainStationLink.save(registries));
        }
        tag.put("TrainStationLinks", (Tag)trainStationLinkList);
        this.aerialLinks.save(tag, false);
        this.groundLinks.save(tag, false);
        this.seismicLinks.save(tag, false);
        tag.putInt("NextAutoSourceIdx", this.nextAutoSourceIndex);
        tag.putBoolean("StockpileMetricsV2", true);
        tag.put("Settings", (Tag)this.settings.save());
        tag.putString("SourceType", this.sourceType);
        tag.putBoolean("StatsLinkOptInRequired", this.statsDisplayLinkOptInRequired);
        tag.putString("StatsLinkHintSourceType", this.statsDisplayLinkHintSourceType);
        tag.putLong("StatsLinkHintTick", this.statsDisplayLinkHintTick);
        tag.putBoolean("IsController", this.isController);
        tag.putLong("ControllerPos", ((this.controllerPos != null) ? this.controllerPos : this.worldPosition).asLong());
        tag.putInt("MBWidth", this.multiblockWidth);
        tag.putInt("MBHeight", this.multiblockHeight);
        tag.putBoolean("MergeDisabled", this.multiblockMergeDisabled);
        tag.putString("AntennaTier", this.antennaTier.name());
        tag.putBoolean("RedstoneEnabled", this.redstoneEnabled);
        tag.putString("RedstoneChannel", this.redstoneChannelId);
        tag.putFloat("RedstoneThreshold", this.redstoneThreshold);
        tag.putBoolean("RedstoneAbove", this.redstoneAbove);
        tag.putInt("RedstoneMode", this.redstoneMode);
        tag.put("ScanSettings", (Tag)this.scanSettings.save());
        tag.put("Annotations", (Tag)this.annotationManager.save());
        final Object cachedNetworkGraph = this.cachedNetworkGraph;
        if (cachedNetworkGraph instanceof NetworkGraph ng) {
            if (!ng.isEmpty()) {
                tag.put("TopologyGraph", (Tag)ng.save());
            }
        }
        tag.put("EfficiencyGrade", (Tag)this.gradeHistory.save());
        if (this.latestEfficiencyScore != null) {
            tag.put("LatestEffScore", (Tag)EfficiencyNbt.saveScore(this.latestEfficiencyScore));
        }
        tag.putBoolean("HasTrainConn", this.hasTrainConnection);
        tag.putLong("TrainConnTick", this.lastTrainConnectionTick);
        if (this.trainMapData != null && !this.trainMapData.isEmpty()) {
            tag.put("TrainMap", (Tag)this.trainMapData.write());
        }
        if (!this.trainMapGroups.isEmpty()) {
            final ListTag grpList = new ListTag();
            for (String g : this.trainMapGroups) {
                final CompoundTag gt = new CompoundTag();
                gt.putString("p", g);
                grpList.add(gt);
            }
            tag.put("TrainMapGroups", (Tag)grpList);
        }
        this.writeRsNetworks(tag, false);
    }

    private void writeRsNetworks(CompoundTag tag, boolean forSync) {
        if (this.rsNetworks.isEmpty()) {
            return;
        }
        final String focus = forSync ? this.rsFocusedItemId() : "";
        final CompoundTag networks = new CompoundTag();
        for (Map.Entry<String, com.createstats.rs.RsNetworkState> entry : this.rsNetworks.entrySet()) {
            networks.put(entry.getKey(), (Tag)(forSync
                    ? entry.getValue().saveSync(focus)
                    : entry.getValue().save()));
        }
        tag.put(forSync ? "RsNetSync" : "RsNet", (Tag)networks);
    }

    private void readRsNetworks(CompoundTag tag) {
        this.rsNetworks.clear();
        final boolean sync = tag.contains("RsNetSync");
        if (!sync && !tag.contains("RsNet")) {
            return;
        }
        final CompoundTag networks = tag.getCompound(sync ? "RsNetSync" : "RsNet");
        for (String key : networks.getAllKeys()) {
            this.rsNetworks.put(key, sync
                    ? com.createstats.rs.RsNetworkState.loadSync(networks.getCompound(key))
                    : com.createstats.rs.RsNetworkState.load(networks.getCompound(key)));
        }
    }

    private void saveSyncAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        final ListTag channelList = new ListTag();
        for (DataChannel ch : this.channels.values()) {
            channelList.add(ch.savePreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES));
        }
        tag.put("Channels", (Tag)channelList);
        final CompoundTag srcReg = new CompoundTag();
        for (Map.Entry<String, SourceConfig> entry : this.sourceRegistry.entrySet()) {
            srcReg.put((String)entry.getKey(), (Tag)entry.getValue().save());
        }
        tag.put("SourceRegistry", (Tag)srcReg);
        final ListTag clutchList = new ListTag();
        for (SafetyClutchLink link : this.safetyClutchLinks.values()) {
            clutchList.add(link.save());
        }
        tag.put("SafetyClutches", (Tag)clutchList);
        final ListTag flywheelList = new ListTag();
        for (KineticFlywheelLink flywheelLink : this.kineticFlywheelLinks.values()) {
            flywheelList.add(flywheelLink.save());
        }
        tag.put("KineticFlywheels", (Tag)flywheelList);
        final ListTag transClutchList = new ListTag();
        for (AutoTransmissionClutchLink tLink : this.autoTransmissionClutchLinks.values()) {
            transClutchList.add(tLink.save());
        }
        tag.put("AutoTransmissionClutches", (Tag)transClutchList);
        final ListTag loadBalancerList = new ListTag();
        for (LoadBalancerLink loadBalancerLink : this.loadBalancerLinks.values()) {
            loadBalancerList.add(loadBalancerLink.save());
        }
        tag.put("LoadBalancers", (Tag)loadBalancerList);
        final ListTag battleScreenLinkList = new ListTag();
        for (BattleScreenLink battleScreenLink : this.battleScreenLinks.values()) {
            battleScreenLinkList.add(battleScreenLink.save());
        }
        tag.put("BattleScreenLinks", (Tag)battleScreenLinkList);
        final ListTag miniDisplayLinkList = new ListTag();
        for (MiniDisplayLink miniDisplayLink : this.miniDisplayLinks.values()) {
            miniDisplayLinkList.add(miniDisplayLink.save(registries));
        }
        tag.put("MiniDisplayLinks", (Tag)miniDisplayLinkList);
        final ListTag manifestLinkList = new ListTag();
        for (FreightManifestLink manifestLink : this.freightManifestLinks.values()) {
            manifestLinkList.add(manifestLink.save(registries));
        }
        tag.put("FreightManifestLinks", (Tag)manifestLinkList);
        final ListTag trainStationLinkList = new ListTag();
        for (TrainStationLink trainStationLink : this.trainStationLinks.values()) {
            trainStationLinkList.add(trainStationLink.save(registries));
        }
        tag.put("TrainStationLinks", (Tag)trainStationLinkList);
        this.aerialLinks.save(tag, true);
        this.groundLinks.save(tag, true);
        this.seismicLinks.save(tag, true);
        tag.putInt("NextAutoSourceIdx", this.nextAutoSourceIndex);
        tag.putBoolean("StockpileMetricsV2", true);
        tag.put("Settings", (Tag)this.settings.save());
        tag.putString("SourceType", this.sourceType);
        tag.putBoolean("StatsLinkOptInRequired", this.statsDisplayLinkOptInRequired);
        tag.putString("StatsLinkHintSourceType", this.statsDisplayLinkHintSourceType);
        tag.putLong("StatsLinkHintTick", this.statsDisplayLinkHintTick);
        tag.putBoolean("IsController", this.isController);
        tag.putLong("ControllerPos", ((this.controllerPos != null) ? this.controllerPos : this.worldPosition).asLong());
        tag.putInt("MBWidth", this.multiblockWidth);
        tag.putInt("MBHeight", this.multiblockHeight);
        tag.putBoolean("MergeDisabled", this.multiblockMergeDisabled);
        tag.putString("AntennaTier", this.antennaTier.name());
        tag.putBoolean("RedstoneEnabled", this.redstoneEnabled);
        tag.putString("RedstoneChannel", this.redstoneChannelId);
        tag.putFloat("RedstoneThreshold", this.redstoneThreshold);
        tag.putBoolean("RedstoneAbove", this.redstoneAbove);
        tag.putInt("RedstoneMode", this.redstoneMode);
        tag.put("ScanSettings", (Tag)this.scanSettings.save());
        tag.put("Annotations", (Tag)this.annotationManager.save());
        final Object cachedNetworkGraph = this.cachedNetworkGraph;
        if (cachedNetworkGraph instanceof NetworkGraph ng) {
            if (!ng.isEmpty()) {
                tag.put("TopologyGraph", (Tag)ng.save());
            }
        }
        tag.put("EfficiencyGrade", (Tag)this.gradeHistory.savePreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES));
        if (this.latestEfficiencyScore != null) {
            tag.put("LatestEffScore", (Tag)EfficiencyNbt.saveScore(this.latestEfficiencyScore));
        }

        if (this.latestEfficiencyTrace != null) {
            tag.put("LatestEffTrace", (Tag)EfficiencyNbt.saveTrace(this.latestEfficiencyTrace));
        }
        tag.putBoolean("HasTrainConn", this.hasTrainConnection);
        tag.putLong("TrainConnTick", this.lastTrainConnectionTick);
        if (this.trainMapData != null && !this.trainMapData.isEmpty()) {
            tag.put("TrainMap", (Tag)this.trainMapData.write());
        }
        if (!this.trainMapGroups.isEmpty()) {
            final ListTag grpList = new ListTag();
            for (String g : this.trainMapGroups) {
                final CompoundTag gt = new CompoundTag();
                gt.putString("p", g);
                grpList.add(gt);
            }
            tag.put("TrainMapGroups", (Tag)grpList);
        }
        this.writeRsNetworks(tag, true);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.readRsNetworks(tag);
        this.feConsumer.load(tag);
        this.channels.clear();
        if (tag.contains("Channels", 9)) {
            final ListTag channelList = tag.getList("Channels", 10);
            for (int i = 0; i < channelList.size(); ++i) {
                final DataChannel ch = DataChannel.load(channelList.getCompound(i));
                this.channels.put(ch.getId(), ch);
            }
        }
        if (!tag.getBoolean("StockpileMetricsV2")) {
            this.migrateLegacyStockpileChannels();
        }
        if (this.channels.isEmpty() && tag.contains("Store")) {
            final DataChannel ch2 = new DataChannel("line0", "Line 0");
            ch2.store = TimeSeriesStore.load(tag.getCompound("Store"));
            ch2.lastText = tag.getString("LastText");
            this.channels.put("line0", ch2);
        }
        this.sourceRegistry.clear();
        if (tag.contains("SourceRegistry")) {
            final CompoundTag srcReg = tag.getCompound("SourceRegistry");
            for (String key : srcReg.getAllKeys()) {
                final SourceConfig config = SourceConfig.load(srcReg.getCompound(key));
                this.sourceRegistry.put(key, config);
            }
        }
        this.safetyClutchLinks.clear();
        if (tag.contains("SafetyClutches", 9)) {
            final ListTag clutchList = tag.getList("SafetyClutches", 10);
            for (int j = 0; j < clutchList.size(); ++j) {
                final SafetyClutchLink link = SafetyClutchLink.load(clutchList.getCompound(j));
                this.safetyClutchLinks.put(link.getId(), link);
            }
        }
        this.kineticFlywheelLinks.clear();
        if (tag.contains("KineticFlywheels", 9)) {
            final ListTag flywheelList = tag.getList("KineticFlywheels", 10);
            for (int j = 0; j < flywheelList.size(); ++j) {
                final KineticFlywheelLink flywheelLink = KineticFlywheelLink.load(flywheelList.getCompound(j));
                this.kineticFlywheelLinks.put(flywheelLink.getId(), flywheelLink);
            }
        }
        this.autoTransmissionClutchLinks.clear();
        if (tag.contains("AutoTransmissionClutches", 9)) {
            final ListTag transClutchList = tag.getList("AutoTransmissionClutches", 10);
            for (int k = 0; k < transClutchList.size(); ++k) {
                final AutoTransmissionClutchLink tLink = AutoTransmissionClutchLink.load(transClutchList.getCompound(k));
                this.autoTransmissionClutchLinks.put(tLink.getId(), tLink);
            }
        }
        this.loadBalancerLinks.clear();
        if (tag.contains("LoadBalancers", 9)) {
            final ListTag loadBalancerList = tag.getList("LoadBalancers", 10);
            for (int m = 0; m < loadBalancerList.size(); ++m) {
                final LoadBalancerLink loadBalancerLink = LoadBalancerLink.load(loadBalancerList.getCompound(m));
                this.loadBalancerLinks.put(loadBalancerLink.getId(), loadBalancerLink);
            }
        }
        this.battleScreenLinks.clear();
        if (tag.contains("BattleScreenLinks", 9)) {
            final ListTag battleScreenLinkList = tag.getList("BattleScreenLinks", 10);
            for (int battleIndex = 0; battleIndex < battleScreenLinkList.size(); ++battleIndex) {
                final BattleScreenLink battleScreenLink = BattleScreenLink.load(battleScreenLinkList.getCompound(battleIndex));
                this.battleScreenLinks.put(battleScreenLink.getId(), battleScreenLink);
            }
        }
        this.miniDisplayLinks.clear();
        if (tag.contains("MiniDisplayLinks", 9)) {
            final ListTag miniDisplayLinkList = tag.getList("MiniDisplayLinks", 10);
            for (int n = 0; n < miniDisplayLinkList.size(); ++n) {
                final MiniDisplayLink miniDisplayLink = MiniDisplayLink.load(miniDisplayLinkList.getCompound(n), registries);
                this.miniDisplayLinks.put(miniDisplayLink.getId(), miniDisplayLink);
            }
        }
        this.freightManifestLinks.clear();
        if (tag.contains("FreightManifestLinks", 9)) {
            final ListTag manifestLinkList = tag.getList("FreightManifestLinks", 10);
            for (int p = 0; p < manifestLinkList.size(); ++p) {
                final FreightManifestLink manifestLink = FreightManifestLink.load(manifestLinkList.getCompound(p), registries);
                this.freightManifestLinks.put(manifestLink.getId(), manifestLink);
            }
        }
        this.trainStationLinks.clear();
        if (tag.contains("TrainStationLinks", 9)) {
            final ListTag trainStationLinkList = tag.getList("TrainStationLinks", 10);
            for (int q = 0; q < trainStationLinkList.size(); ++q) {
                final TrainStationLink trainStationLink = TrainStationLink.load(trainStationLinkList.getCompound(q), registries);
                this.trainStationLinks.put(trainStationLink.getId(), trainStationLink);
            }
        }
        this.aerialLinks.load(tag, this.level != null && this.level.isClientSide());
        this.groundLinks.load(tag, this.level != null && this.level.isClientSide());
        this.seismicLinks.load(tag, this.level != null && this.level.isClientSide());
        this.nextAutoSourceIndex = Math.max(0, tag.getInt("NextAutoSourceIdx"));
        this.recoverNextAutoSourceIndex();
        if (tag.contains("Settings") && !this.clientSettingsHeld()) {
            this.settings = StatsDisplaySettings.load(tag.getCompound("Settings"));
        }
        this.sourceType = tag.getString("SourceType");
        this.statsDisplayLinkOptInRequired = tag.getBoolean("StatsLinkOptInRequired");
        this.statsDisplayLinkHintSourceType = tag.getString("StatsLinkHintSourceType");
        this.statsDisplayLinkHintTick = tag.getLong("StatsLinkHintTick");
        this.isController = tag.getBoolean("IsController");
        if (tag.contains("ControllerPos")) {
            this.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        }
        else if (this.controllerPos == null) {
            this.controllerPos = this.worldPosition;
        }
        this.multiblockMergeDisabled = tag.getBoolean("MergeDisabled");
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        this.multiblockWidth = tag.getInt("MBWidth");
        this.multiblockHeight = tag.getInt("MBHeight");
        if (this.multiblockWidth <= 0) {
            this.multiblockWidth = 1;
        }
        if (this.multiblockHeight <= 0) {
            this.multiblockHeight = 1;
        }
        this.redstoneEnabled = tag.getBoolean("RedstoneEnabled");
        this.redstoneChannelId = tag.getString("RedstoneChannel");
        this.redstoneThreshold = tag.getFloat("RedstoneThreshold");
        this.redstoneAbove = (!tag.contains("RedstoneAbove") || tag.getBoolean("RedstoneAbove"));
        this.redstoneMode = tag.getInt("RedstoneMode");
        if (tag.contains("ScanSettings")) {
            this.scanSettings = ScanSettings.load(tag.getCompound("ScanSettings"));
        }
        else if (tag.getBoolean("ScanEnabled")) {
            (this.scanSettings = new ScanSettings()).setScanMode(ScanSettings.ScanMode.AUTO);
        }
        if (tag.contains("Annotations")) {
            this.annotationManager = AnnotationManager.load(tag.getCompound("Annotations"));
        }
        else {
            this.annotationManager = new AnnotationManager();
        }
        if (tag.contains("TopologyGraph")) {
            this.cachedNetworkGraph = NetworkGraph.load(tag.getCompound("TopologyGraph"));
        }
        else {
            this.cachedNetworkGraph = NetworkGraph.EMPTY;
        }
        if (tag.contains("EfficiencyGrade")) {
            (this.gradeHistory = GradeHistory.load(tag.getCompound("EfficiencyGrade"))).detectNewSession((this.level != null) ? this.level.getGameTime() : 0L);
        }
        else {
            this.gradeHistory = new GradeHistory();
        }
        if (tag.contains("LatestEffScore")) {
            this.latestEfficiencyScore = EfficiencyNbt.loadScore(tag.getCompound("LatestEffScore"));
        }
        this.latestEfficiencyTrace = tag.contains("LatestEffTrace")
                ? EfficiencyNbt.loadTrace(tag.getCompound("LatestEffTrace"))
                : null;
        this.hasTrainConnection = tag.getBoolean("HasTrainConn");
        this.lastTrainConnectionTick = tag.getLong("TrainConnTick");
        if (tag.contains("TrainMap")) {
            this.trainMapData = TrainMapData.read(tag.getCompound("TrainMap"));
        }
        else {
            this.trainMapData = null;
        }
        this.trainMapGroups.clear();
        if (tag.contains("TrainMapGroups")) {
            final ListTag grpList = tag.getList("TrainMapGroups", 10);
            for (int i = 0; i < grpList.size(); ++i) {
                this.trainMapGroups.add(grpList.getCompound(i).getString("p"));
            }
        }
        if (this.normalizeSettingsState()) {
            this.needsSync = true;
            this.setChanged();
        }
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveSyncAdditional(tag, registries);
        return tag;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return (Packet<ClientGamePacketListener>)ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }

    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
        if (this.level != null && this.level.isClientSide()) {
            DisplayTextureManager.markDirty(this.worldPosition);
        }
    }

    private static String createSafetyClutchLinkId(BlockPos sourcePos) {
        return "clutch_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createKineticFlywheelLinkId(BlockPos sourcePos) {
        return "flywheel_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createAutoTransmissionClutchLinkId(BlockPos sourcePos) {
        return "trans_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createLoadBalancerLinkId(BlockPos sourcePos) {
        return "bal_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createBattleScreenLinkId(BlockPos sourcePos) {
        return "battle_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createMiniDisplayLinkId(BlockPos sourcePos) {
        return "mini_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createFreightManifestLinkId(BlockPos sourcePos) {
        return "manifest_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    private static String createTrainStationLinkId(BlockPos sourcePos) {
        return "station_" + sourcePos.getX() + "_" + sourcePos.getY() + "_" + sourcePos.getZ();
    }

    public static class DataChannel
    {
        private final String id;
        private String label;
        private TimeSeriesStore store;
        private String lastText;
        private float alertThreshold;
        private boolean alertAbove;
        private boolean alertEnabled;

        public DataChannel(String id, String label) {
            this.lastText = "";
            this.alertThreshold = 0.0f;
            this.alertAbove = true;
            this.alertEnabled = false;
            this.id = id;
            this.label = label;
            this.store = TimeSeriesStore.forPanel();
        }

        public String getId() {
            return this.id;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public TimeSeriesStore getStore() {
            return this.store;
        }

        public String getLastText() {
            return this.lastText;
        }

        public float getAlertThreshold() {
            return this.alertThreshold;
        }

        public void setAlertThreshold(float threshold) {
            this.alertThreshold = threshold;
        }

        public boolean isAlertAbove() {
            return this.alertAbove;
        }

        public void setAlertAbove(boolean above) {
            this.alertAbove = above;
        }

        public boolean isAlertEnabled() {
            return this.alertEnabled;
        }

        public void setAlertEnabled(boolean enabled) {
            this.alertEnabled = enabled;
        }

        public boolean isAlertTriggered() {
            if (!this.alertEnabled || this.store.getSampleCount() == 0) {
                return false;
            }
            final float latest = this.store.getLatestValue();
            return this.alertAbove ? (latest >= this.alertThreshold) : (latest <= this.alertThreshold);
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Label", this.label);
            tag.put("Store", (Tag)this.store.save());
            tag.putString("LastText", this.lastText);
            tag.putFloat("AlertThreshold", this.alertThreshold);
            tag.putBoolean("AlertAbove", this.alertAbove);
            tag.putBoolean("AlertEnabled", this.alertEnabled);
            return tag;
        }

        public CompoundTag savePreview(int maxSamples) {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Label", this.label);
            tag.put("Store", (Tag)this.store.savePreview(maxSamples));
            tag.putString("LastText", this.lastText);
            tag.putFloat("AlertThreshold", this.alertThreshold);
            tag.putBoolean("AlertAbove", this.alertAbove);
            tag.putBoolean("AlertEnabled", this.alertEnabled);
            return tag;
        }

        public static DataChannel load(CompoundTag tag) {
            final String id = tag.getString("Id");
            final String label = tag.getString("Label");
            final DataChannel ch = new DataChannel(id, label);
            if (tag.contains("Store")) {
                ch.store = TimeSeriesStore.load(tag.getCompound("Store"));
            }
            ch.lastText = tag.getString("LastText");
            if (tag.contains("AlertThreshold")) {
                ch.alertThreshold = tag.getFloat("AlertThreshold");
            }
            if (tag.contains("AlertAbove")) {
                ch.alertAbove = tag.getBoolean("AlertAbove");
            }
            if (tag.contains("AlertEnabled")) {
                ch.alertEnabled = tag.getBoolean("AlertEnabled");
            }
            return ch;
        }

        public static DataChannel ofStore(String id, String label, TimeSeriesStore existingStore) {
            final DataChannel ch = new DataChannel(id, label);
            ch.store = existingStore;
            return ch;
        }
    }

    public static class SafetyClutchLink
    {
        private final String id;
        private final BlockPos sourcePos;
        private SafetyClutchSettings settings;
        private boolean tripped;
        private boolean networkRunning;
        private float stressPercent;
        private float stressSU;
        private float capacitySU;
        private long lastDataTick;

        public SafetyClutchLink(String id, BlockPos sourcePos) {
            this.settings = new SafetyClutchSettings();
            this.id = id;
            this.sourcePos = sourcePos;
        }

        public String getId() {
            return this.id;
        }

        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        public SafetyClutchSettings getSettings() {
            return this.settings;
        }

        public boolean isTripped() {
            return this.tripped;
        }

        public boolean isNetworkRunning() {
            return this.networkRunning;
        }

        public float getStressPercent() {
            return this.stressPercent;
        }

        public float getStressSU() {
            return this.stressSU;
        }

        public float getCapacitySU() {
            return this.capacitySU;
        }

        public long getLastDataTick() {
            return this.lastDataTick;
        }

        public boolean updateFrom(SafetyClutchBE clutch, long tick) {
            boolean changed = false;
            final SafetyClutchSettings newSettings = SafetyClutchSettings.load(clutch.getSettings().save());
            if (!sameSettings(this.settings, newSettings)) {
                this.settings = newSettings;
                changed = true;
            }
            if (this.tripped != clutch.isTripped()) {
                this.tripped = clutch.isTripped();
                changed = true;
            }
            if (this.networkRunning != clutch.isNetworkRunning()) {
                this.networkRunning = clutch.isNetworkRunning();
                changed = true;
            }
            if (Float.compare(this.stressPercent, clutch.getLastStressPercent()) != 0) {
                this.stressPercent = clutch.getLastStressPercent();
                changed = true;
            }
            if (Float.compare(this.stressSU, clutch.getLastStressSU()) != 0) {
                this.stressSU = clutch.getLastStressSU();
                changed = true;
            }
            if (Float.compare(this.capacitySU, clutch.getLastCapacitySU()) != 0) {
                this.capacitySU = clutch.getLastCapacitySU();
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("Settings", (Tag)this.settings.save());
            tag.putBoolean("Tripped", this.tripped);
            tag.putBoolean("NetworkRunning", this.networkRunning);
            tag.putFloat("StressPercent", this.stressPercent);
            tag.putFloat("StressSU", this.stressSU);
            tag.putFloat("CapacitySU", this.capacitySU);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static SafetyClutchLink load(CompoundTag tag) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createSafetyClutchLinkId(pos);
            final SafetyClutchLink link = new SafetyClutchLink(id, pos);
            if (tag.contains("Settings")) {
                link.settings = SafetyClutchSettings.load(tag.getCompound("Settings"));
            }
            link.tripped = tag.getBoolean("Tripped");
            link.networkRunning = tag.getBoolean("NetworkRunning");
            link.stressPercent = tag.getFloat("StressPercent");
            link.stressSU = tag.getFloat("StressSU");
            link.capacitySU = tag.getFloat("CapacitySU");
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static boolean sameSettings(SafetyClutchSettings current, SafetyClutchSettings other) {
            return Float.compare(current.getThreshold(), other.getThreshold()) == 0
                    && Float.compare(current.getHysteresis(), other.getHysteresis()) == 0
                    && current.getMode() == other.getMode()
                    && current.isEnabled() == other.isEnabled();
        }
    }

    public static class KineticFlywheelLink
    {
        private final String id;
        private final BlockPos sourcePos;
        private KineticFlywheelSettings settings;
        private int modeIndex;
        private float chargePercent;
        private float storedRpm;
        private float outputSU;
        private float sustainSeconds;
        private int tier;
        private int flywheelCount;
        private boolean halted;
        private boolean parked;
        private boolean depleted;
        private boolean lowReserve;
        private long lastDataTick;

        public KineticFlywheelLink(String id, BlockPos sourcePos) {
            this.settings = new KineticFlywheelSettings();
            this.id = id;
            this.sourcePos = sourcePos;
        }

        public String getId() {
            return this.id;
        }

        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        public KineticFlywheelSettings getSettings() {
            return this.settings;
        }

        public int getModeIndex() {
            return this.modeIndex;
        }

        public float getChargePercent() {
            return this.chargePercent;
        }

        public float getStoredRpm() {
            return this.storedRpm;
        }

        public float getOutputSU() {
            return this.outputSU;
        }

        public float getSustainSeconds() {
            return this.sustainSeconds;
        }

        public int getTier() {
            return this.tier;
        }

        public int getFlywheelCount() {
            return this.flywheelCount;
        }

        public boolean isHalted() {
            return this.halted;
        }

        public boolean isParked() {
            return this.parked;
        }

        public boolean isDepleted() {
            return this.depleted;
        }

        public boolean isLowReserve() {
            return this.lowReserve;
        }

        public long getLastDataTick() {
            return this.lastDataTick;
        }

        public boolean updateFrom(KineticFlywheelBE flywheel, long tick) {
            boolean changed = false;
            final KineticFlywheelSettings newSettings = KineticFlywheelSettings.load(flywheel.getSettings().save());
            if (!sameSettings(this.settings, newSettings)) {
                this.settings = newSettings;
                changed = true;
            }
            if (this.modeIndex != flywheel.getModeIndex()) {
                this.modeIndex = flywheel.getModeIndex();
                changed = true;
            }
            if (Float.compare(this.chargePercent, flywheel.getChargePercent()) != 0) {
                this.chargePercent = flywheel.getChargePercent();
                changed = true;
            }
            if (Float.compare(this.storedRpm, flywheel.getStoredRpm()) != 0) {
                this.storedRpm = flywheel.getStoredRpm();
                changed = true;
            }
            if (Float.compare(this.outputSU, flywheel.getOutputStress()) != 0) {
                this.outputSU = flywheel.getOutputStress();
                changed = true;
            }
            if (Float.compare(this.sustainSeconds, flywheel.getEstimatedSustainSeconds()) != 0) {
                this.sustainSeconds = flywheel.getEstimatedSustainSeconds();
                changed = true;
            }
            if (this.tier != flywheel.getTier()) {
                this.tier = flywheel.getTier();
                changed = true;
            }
            if (this.flywheelCount != flywheel.getFlywheelCount()) {
                this.flywheelCount = flywheel.getFlywheelCount();
                changed = true;
            }
            if (this.halted != flywheel.isOverstressHalted()) {
                this.halted = flywheel.isOverstressHalted();
                changed = true;
            }
            if (this.parked != flywheel.isParked()) {
                this.parked = flywheel.isParked();
                changed = true;
            }
            if (this.depleted != flywheel.isDepleted()) {
                this.depleted = flywheel.isDepleted();
                changed = true;
            }
            if (this.lowReserve != flywheel.isLowReserve()) {
                this.lowReserve = flywheel.isLowReserve();
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("Settings", (Tag)this.settings.save());
            tag.putInt("ModeIndex", this.modeIndex);
            tag.putFloat("ChargePercent", this.chargePercent);
            tag.putFloat("StoredRpm", this.storedRpm);
            tag.putFloat("OutputSU", this.outputSU);
            tag.putFloat("SustainSeconds", this.sustainSeconds);
            tag.putInt("Tier", this.tier);
            tag.putInt("FlywheelCount", this.flywheelCount);
            tag.putBoolean("Halted", this.halted);
            tag.putBoolean("Parked", this.parked);
            tag.putBoolean("Depleted", this.depleted);
            tag.putBoolean("LowReserve", this.lowReserve);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static KineticFlywheelLink load(CompoundTag tag) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createKineticFlywheelLinkId(pos);
            final KineticFlywheelLink link = new KineticFlywheelLink(id, pos);
            if (tag.contains("Settings")) {
                link.settings = KineticFlywheelSettings.load(tag.getCompound("Settings"));
            }
            link.modeIndex = tag.getInt("ModeIndex");
            link.chargePercent = tag.getFloat("ChargePercent");
            link.storedRpm = tag.getFloat("StoredRpm");
            link.outputSU = tag.getFloat("OutputSU");
            link.sustainSeconds = tag.getFloat("SustainSeconds");
            link.tier = tag.getInt("Tier");
            link.flywheelCount = tag.getInt("FlywheelCount");
            link.halted = tag.getBoolean("Halted");
            link.parked = tag.getBoolean("Parked");
            link.depleted = tag.getBoolean("Depleted");
            link.lowReserve = tag.getBoolean("LowReserve");
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static boolean sameSettings(KineticFlywheelSettings current, KineticFlywheelSettings other) {
            return current.isDischargeArmed() == other.isDischargeArmed()
                    && Float.compare(current.getLowReservePct(), other.getLowReservePct()) == 0;
        }
    }

    public static class AutoTransmissionClutchLink
    {
        private final String id;
        private final BlockPos sourcePos;
        private AutoTransmissionClutchSettings settings;
        private float currentModifier;
        private float targetModifier;
        private AutoTransmissionClutchBE.AlarmState alarmState;
        private float transmittedSpeed;
        private boolean networkRunning;
        private float stressPercent;
        private float stressSU;
        private float capacitySU;
        private long lastDataTick;

        public AutoTransmissionClutchLink(String id, BlockPos sourcePos) {
            this.settings = new AutoTransmissionClutchSettings();
            this.alarmState = AutoTransmissionClutchBE.AlarmState.NORMAL;
            this.id = id;
            this.sourcePos = sourcePos;
        }

        public String getId() { return this.id; }
        public BlockPos getSourcePos() { return this.sourcePos; }
        public AutoTransmissionClutchSettings getSettings() { return this.settings; }
        public float getCurrentModifier() { return this.currentModifier; }
        public float getTargetModifier() { return this.targetModifier; }
        public AutoTransmissionClutchBE.AlarmState getAlarmState() { return this.alarmState; }
        public float getTransmittedSpeed() { return this.transmittedSpeed; }
        public boolean isNetworkRunning() { return this.networkRunning; }
        public float getStressPercent() { return this.stressPercent; }
        public float getStressSU() { return this.stressSU; }
        public float getCapacitySU() { return this.capacitySU; }
        public long getLastDataTick() { return this.lastDataTick; }

        public boolean updateFrom(AutoTransmissionClutchBE transmission, long tick) {
            boolean changed = false;
            final AutoTransmissionClutchSettings newSettings = AutoTransmissionClutchSettings.load(transmission.getSettings().save());
            if (!sameSettings(this.settings, newSettings)) {
                this.settings = newSettings;
                changed = true;
            }
            if (Float.compare(this.currentModifier, transmission.getCurrentModifier()) != 0) {
                this.currentModifier = transmission.getCurrentModifier();
                changed = true;
            }
            if (Float.compare(this.targetModifier, transmission.getTargetModifier()) != 0) {
                this.targetModifier = transmission.getTargetModifier();
                changed = true;
            }
            if (this.alarmState != transmission.getAlarmState()) {
                this.alarmState = transmission.getAlarmState();
                changed = true;
            }
            if (Float.compare(this.transmittedSpeed, transmission.getTransmittedSpeed()) != 0) {
                this.transmittedSpeed = transmission.getTransmittedSpeed();
                changed = true;
            }
            if (this.networkRunning != transmission.isNetworkRunning()) {
                this.networkRunning = transmission.isNetworkRunning();
                changed = true;
            }
            if (Float.compare(this.stressPercent, transmission.getLastStressPercent()) != 0) {
                this.stressPercent = transmission.getLastStressPercent();
                changed = true;
            }
            if (Float.compare(this.stressSU, transmission.getLastStressSU()) != 0) {
                this.stressSU = transmission.getLastStressSU();
                changed = true;
            }
            if (Float.compare(this.capacitySU, transmission.getLastCapacitySU()) != 0) {
                this.capacitySU = transmission.getLastCapacitySU();
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("Settings", (Tag)this.settings.save());
            tag.putFloat("CurrentModifier", this.currentModifier);
            tag.putFloat("TargetModifier", this.targetModifier);
            tag.putString("AlarmState", this.alarmState.name());
            tag.putFloat("TransmittedSpeed", this.transmittedSpeed);
            tag.putBoolean("NetworkRunning", this.networkRunning);
            tag.putFloat("StressPercent", this.stressPercent);
            tag.putFloat("StressSU", this.stressSU);
            tag.putFloat("CapacitySU", this.capacitySU);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static AutoTransmissionClutchLink load(CompoundTag tag) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createAutoTransmissionClutchLinkId(pos);
            final AutoTransmissionClutchLink link = new AutoTransmissionClutchLink(id, pos);
            if (tag.contains("Settings")) {
                link.settings = AutoTransmissionClutchSettings.load(tag.getCompound("Settings"));
            }
            link.currentModifier = tag.getFloat("CurrentModifier");
            link.targetModifier = tag.getFloat("TargetModifier");
            try {
                link.alarmState = AutoTransmissionClutchBE.AlarmState.valueOf(tag.getString("AlarmState"));
            } catch (IllegalArgumentException e) {
                link.alarmState = AutoTransmissionClutchBE.AlarmState.NORMAL;
            }
            link.transmittedSpeed = tag.getFloat("TransmittedSpeed");
            link.networkRunning = tag.getBoolean("NetworkRunning");
            link.stressPercent = tag.getFloat("StressPercent");
            link.stressSU = tag.getFloat("StressSU");
            link.capacitySU = tag.getFloat("CapacitySU");
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static boolean sameSettings(AutoTransmissionClutchSettings current, AutoTransmissionClutchSettings other) {
            return current.getLimitMode() == other.getLimitMode()
                    && Float.compare(current.getLowerBound(), other.getLowerBound()) == 0
                    && Float.compare(current.getUpperBound(), other.getUpperBound()) == 0
                    && Float.compare(current.getMinimumRPM(), other.getMinimumRPM()) == 0
                    && Float.compare(current.getMaximumRPM(), other.getMaximumRPM()) == 0
                    && current.isEnabled() == other.isEnabled();
        }
    }

    public static class LoadBalancerLink {
        private final String id;
        private final BlockPos sourcePos;
        private LoadBalancerSettings settings;
        private float currentModifierLeft;
        private float currentModifierRight;
        private float targetModifierLeft;
        private float targetModifierRight;
        private float transmittedSpeedLeft;
        private float transmittedSpeedRight;
        private boolean networkRunning;
        private float stressPercent;
        private float stressSU;
        private float capacitySU;
        private float leftStressSU;
        private float rightStressSU;
        private float baseStressSU;
        private float leftBudgetSU;
        private float rightBudgetSU;
        private boolean leftBranchAttached;
        private boolean rightBranchAttached;
        private TopologyStatus topologyStatus;
        private long lastDataTick;

        public LoadBalancerLink(String id, BlockPos sourcePos) {
            this.id = id;
            this.sourcePos = sourcePos;
            this.settings = new LoadBalancerSettings();
            this.topologyStatus = TopologyStatus.NO_SOURCE;
        }

        public String getId() { return this.id; }
        public BlockPos getSourcePos() { return this.sourcePos; }
        public LoadBalancerSettings getSettings() { return this.settings; }
        public float getCurrentModifierLeft() { return this.currentModifierLeft; }
        public float getCurrentModifierRight() { return this.currentModifierRight; }
        public float getTargetModifierLeft() { return this.targetModifierLeft; }
        public float getTargetModifierRight() { return this.targetModifierRight; }
        public float getTransmittedSpeedLeft() { return this.transmittedSpeedLeft; }
        public float getTransmittedSpeedRight() { return this.transmittedSpeedRight; }
        public boolean isNetworkRunning() { return this.networkRunning; }
        public float getStressPercent() { return this.stressPercent; }
        public float getStressSU() { return this.stressSU; }
        public float getCapacitySU() { return this.capacitySU; }
        public float getLeftStressSU() { return this.leftStressSU; }
        public float getRightStressSU() { return this.rightStressSU; }
        public float getBaseStressSU() { return this.baseStressSU; }
        public float getLeftBudgetSU() { return this.leftBudgetSU; }
        public float getRightBudgetSU() { return this.rightBudgetSU; }
        public boolean isLeftBranchAttached() { return this.leftBranchAttached; }
        public boolean isRightBranchAttached() { return this.rightBranchAttached; }
        public TopologyStatus getTopologyStatus() { return this.topologyStatus; }
        public long getLastDataTick() { return this.lastDataTick; }

        public boolean updateFrom(LoadBalancerBE balancer, long tick) {
            boolean changed = false;
            final LoadBalancerSettings newSettings = LoadBalancerSettings.load(balancer.getSettings().save());
            if (!sameSettings(this.settings, newSettings)) {
                this.settings = newSettings;
                changed = true;
            }
            if (Float.compare(this.currentModifierLeft, balancer.getCurrentModifierLeft()) != 0) {
                this.currentModifierLeft = balancer.getCurrentModifierLeft();
                changed = true;
            }
            if (Float.compare(this.currentModifierRight, balancer.getCurrentModifierRight()) != 0) {
                this.currentModifierRight = balancer.getCurrentModifierRight();
                changed = true;
            }
            if (Float.compare(this.targetModifierLeft, balancer.getTargetModifierLeft()) != 0) {
                this.targetModifierLeft = balancer.getTargetModifierLeft();
                changed = true;
            }
            if (Float.compare(this.targetModifierRight, balancer.getTargetModifierRight()) != 0) {
                this.targetModifierRight = balancer.getTargetModifierRight();
                changed = true;
            }
            if (Float.compare(this.transmittedSpeedLeft, balancer.getTransmittedSpeedLeft()) != 0) {
                this.transmittedSpeedLeft = balancer.getTransmittedSpeedLeft();
                changed = true;
            }
            if (Float.compare(this.transmittedSpeedRight, balancer.getTransmittedSpeedRight()) != 0) {
                this.transmittedSpeedRight = balancer.getTransmittedSpeedRight();
                changed = true;
            }
            if (this.networkRunning != balancer.isNetworkRunning()) {
                this.networkRunning = balancer.isNetworkRunning();
                changed = true;
            }
            if (Float.compare(this.stressPercent, balancer.getLastStressPercent()) != 0) {
                this.stressPercent = balancer.getLastStressPercent();
                changed = true;
            }
            if (Float.compare(this.stressSU, balancer.getLastStressSU()) != 0) {
                this.stressSU = balancer.getLastStressSU();
                changed = true;
            }
            if (Float.compare(this.capacitySU, balancer.getLastCapacitySU()) != 0) {
                this.capacitySU = balancer.getLastCapacitySU();
                changed = true;
            }
            if (Float.compare(this.leftStressSU, balancer.getLastLeftStressSU()) != 0) {
                this.leftStressSU = balancer.getLastLeftStressSU();
                changed = true;
            }
            if (Float.compare(this.rightStressSU, balancer.getLastRightStressSU()) != 0) {
                this.rightStressSU = balancer.getLastRightStressSU();
                changed = true;
            }
            if (Float.compare(this.baseStressSU, balancer.getLastBaseStressSU()) != 0) {
                this.baseStressSU = balancer.getLastBaseStressSU();
                changed = true;
            }
            if (Float.compare(this.leftBudgetSU, balancer.getLastLeftBudgetSU()) != 0) {
                this.leftBudgetSU = balancer.getLastLeftBudgetSU();
                changed = true;
            }
            if (Float.compare(this.rightBudgetSU, balancer.getLastRightBudgetSU()) != 0) {
                this.rightBudgetSU = balancer.getLastRightBudgetSU();
                changed = true;
            }
            if (this.leftBranchAttached != balancer.isLeftBranchAttached()) {
                this.leftBranchAttached = balancer.isLeftBranchAttached();
                changed = true;
            }
            if (this.rightBranchAttached != balancer.isRightBranchAttached()) {
                this.rightBranchAttached = balancer.isRightBranchAttached();
                changed = true;
            }
            if (this.topologyStatus != balancer.getTopologyStatus()) {
                this.topologyStatus = balancer.getTopologyStatus();
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("Settings", (Tag)this.settings.save());
            tag.putFloat("CurrentModifierLeft", this.currentModifierLeft);
            tag.putFloat("CurrentModifierRight", this.currentModifierRight);
            tag.putFloat("TargetModifierLeft", this.targetModifierLeft);
            tag.putFloat("TargetModifierRight", this.targetModifierRight);
            tag.putFloat("TransmittedSpeedLeft", this.transmittedSpeedLeft);
            tag.putFloat("TransmittedSpeedRight", this.transmittedSpeedRight);
            tag.putBoolean("NetworkRunning", this.networkRunning);
            tag.putFloat("StressPercent", this.stressPercent);
            tag.putFloat("StressSU", this.stressSU);
            tag.putFloat("CapacitySU", this.capacitySU);
            tag.putFloat("LeftStressSU", this.leftStressSU);
            tag.putFloat("RightStressSU", this.rightStressSU);
            tag.putFloat("BaseStressSU", this.baseStressSU);
            tag.putFloat("LeftBudgetSU", this.leftBudgetSU);
            tag.putFloat("RightBudgetSU", this.rightBudgetSU);
            tag.putBoolean("LeftBranchAttached", this.leftBranchAttached);
            tag.putBoolean("RightBranchAttached", this.rightBranchAttached);
            tag.putString("TopologyStatus", this.topologyStatus.name());
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static LoadBalancerLink load(CompoundTag tag) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createLoadBalancerLinkId(pos);
            final LoadBalancerLink link = new LoadBalancerLink(id, pos);
            if (tag.contains("Settings")) {
                link.settings = LoadBalancerSettings.load(tag.getCompound("Settings"));
            }
            link.currentModifierLeft = tag.getFloat("CurrentModifierLeft");
            link.currentModifierRight = tag.getFloat("CurrentModifierRight");
            link.targetModifierLeft = tag.getFloat("TargetModifierLeft");
            link.targetModifierRight = tag.getFloat("TargetModifierRight");
            link.transmittedSpeedLeft = tag.getFloat("TransmittedSpeedLeft");
            link.transmittedSpeedRight = tag.getFloat("TransmittedSpeedRight");
            link.networkRunning = tag.getBoolean("NetworkRunning");
            link.stressPercent = tag.getFloat("StressPercent");
            link.stressSU = tag.getFloat("StressSU");
            link.capacitySU = tag.getFloat("CapacitySU");
            link.leftStressSU = tag.getFloat("LeftStressSU");
            link.rightStressSU = tag.getFloat("RightStressSU");
            link.baseStressSU = tag.getFloat("BaseStressSU");
            link.leftBudgetSU = tag.getFloat("LeftBudgetSU");
            link.rightBudgetSU = tag.getFloat("RightBudgetSU");
            link.leftBranchAttached = tag.getBoolean("LeftBranchAttached");
            link.rightBranchAttached = tag.getBoolean("RightBranchAttached");
            try {
                link.topologyStatus = TopologyStatus.valueOf(tag.getString("TopologyStatus"));
            } catch (IllegalArgumentException e) {
                link.topologyStatus = TopologyStatus.NO_SOURCE;
            }
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static boolean sameSettings(LoadBalancerSettings current, LoadBalancerSettings other) {
            return current.getLimitMode() == other.getLimitMode()
                    && Float.compare(current.getLowerBound(), other.getLowerBound()) == 0
                    && Float.compare(current.getUpperBound(), other.getUpperBound()) == 0
                    && Float.compare(current.getMinimumRPM(), other.getMinimumRPM()) == 0
                    && Float.compare(current.getMaximumRPM(), other.getMaximumRPM()) == 0
                    && Float.compare(current.getLeftSharePct(), other.getLeftSharePct()) == 0
                    && current.getSharingMode() == other.getSharingMode()
                    && current.isEnabled() == other.isEnabled();
        }
    }

    public static class MiniDisplayLink {
        private final String id;
        private final BlockPos sourcePos;
        private MiniDisplaySettings settings;
        private MiniDisplayBE.DisplayState displayState;
        private InstrumentSourceHelper.SourceKind sourceKind;
        private String sourceType;
        private float fillPercent;
        private float totalItems;
        private float fluidAmount;
        private float fluidCapacity;
        private float stressPercent;
        private float stressSU;
        private float capacitySU;
        private float rpm;
        private boolean firstDetailPresent;
        private String firstDetailLabel;
        private float firstDetailValue;
        private boolean secondDetailPresent;
        private String secondDetailLabel;
        private float secondDetailValue;
        private int displayWidth;
        private long lastDataTick;

        public MiniDisplayLink(String id, BlockPos sourcePos) {
            this.id = id;
            this.sourcePos = sourcePos;
            this.settings = new MiniDisplaySettings();
            this.displayState = MiniDisplayBE.DisplayState.NO_SOURCE;
            this.sourceKind = InstrumentSourceHelper.SourceKind.NO_SOURCE;
            this.sourceType = "";
            this.firstDetailLabel = "";
            this.secondDetailLabel = "";
            this.displayWidth = 1;
        }

        public String getId() { return this.id; }
        public BlockPos getSourcePos() { return this.sourcePos; }
        public MiniDisplaySettings getSettings() { return this.settings; }
        public MiniDisplayBE.DisplayState getDisplayState() { return this.displayState; }
        public InstrumentSourceHelper.SourceKind getSourceKind() { return this.sourceKind; }
        public String getSourceType() { return this.sourceType; }
        public float getFillPercent() { return this.fillPercent; }
        public float getTotalItems() { return this.totalItems; }
        public float getFluidAmount() { return this.fluidAmount; }
        public float getFluidCapacity() { return this.fluidCapacity; }
        public float getStressPercent() { return this.stressPercent; }
        public float getStressSU() { return this.stressSU; }
        public float getCapacitySU() { return this.capacitySU; }
        public float getRpm() { return this.rpm; }
        public boolean hasFirstDetailRow() { return this.firstDetailPresent; }
        public String getFirstDetailLabel() { return this.firstDetailLabel; }
        public float getFirstDetailValue() { return this.firstDetailValue; }
        public boolean hasSecondDetailRow() { return this.secondDetailPresent; }
        public String getSecondDetailLabel() { return this.secondDetailLabel; }
        public float getSecondDetailValue() { return this.secondDetailValue; }
        public int getDisplayWidth() { return this.displayWidth; }
        public long getLastDataTick() { return this.lastDataTick; }

        public boolean updateFrom(MiniDisplayBE miniDisplay, long tick, HolderLookup.Provider registries) {
            final MiniDisplayBE controller = miniDisplay.getControllerBE();
            boolean changed = false;
            final MiniDisplaySettings newSettings = MiniDisplaySettings.load(controller.getSettings().save(registries), registries);
            if (!sameSettings(this.settings, newSettings)) {
                this.settings = newSettings;
                changed = true;
            }
            if (this.displayState != controller.getDisplayState()) {
                this.displayState = controller.getDisplayState();
                changed = true;
            }
            if (this.sourceKind != controller.getActiveSourceKind()) {
                this.sourceKind = controller.getActiveSourceKind();
                changed = true;
            }
            if (!Objects.equals(this.sourceType, controller.getActiveSourceType())) {
                this.sourceType = controller.getActiveSourceType();
                changed = true;
            }
            if (Float.compare(this.fillPercent, controller.getActiveFillPercent()) != 0) {
                this.fillPercent = controller.getActiveFillPercent();
                changed = true;
            }
            if (Float.compare(this.totalItems, controller.getActiveTotalItems()) != 0) {
                this.totalItems = controller.getActiveTotalItems();
                changed = true;
            }
            if (Float.compare(this.fluidAmount, controller.getActiveFluidAmount()) != 0) {
                this.fluidAmount = controller.getActiveFluidAmount();
                changed = true;
            }
            if (Float.compare(this.fluidCapacity, controller.getActiveFluidCapacity()) != 0) {
                this.fluidCapacity = controller.getActiveFluidCapacity();
                changed = true;
            }
            if (Float.compare(this.stressPercent, controller.getActiveStressPercent()) != 0) {
                this.stressPercent = controller.getActiveStressPercent();
                changed = true;
            }
            if (Float.compare(this.stressSU, controller.getActiveStressUsed()) != 0) {
                this.stressSU = controller.getActiveStressUsed();
                changed = true;
            }
            if (Float.compare(this.capacitySU, controller.getActiveStressCapacity()) != 0) {
                this.capacitySU = controller.getActiveStressCapacity();
                changed = true;
            }
            if (Float.compare(this.rpm, controller.getActiveRpm()) != 0) {
                this.rpm = controller.getActiveRpm();
                changed = true;
            }
            if (this.firstDetailPresent != controller.hasFirstDetailRow()) {
                this.firstDetailPresent = controller.hasFirstDetailRow();
                changed = true;
            }
            if (!Objects.equals(this.firstDetailLabel, controller.getFirstDetailLabel())) {
                this.firstDetailLabel = controller.getFirstDetailLabel();
                changed = true;
            }
            if (Float.compare(this.firstDetailValue, controller.getFirstDetailValue()) != 0) {
                this.firstDetailValue = controller.getFirstDetailValue();
                changed = true;
            }
            if (this.secondDetailPresent != controller.hasSecondDetailRow()) {
                this.secondDetailPresent = controller.hasSecondDetailRow();
                changed = true;
            }
            if (!Objects.equals(this.secondDetailLabel, controller.getSecondDetailLabel())) {
                this.secondDetailLabel = controller.getSecondDetailLabel();
                changed = true;
            }
            if (Float.compare(this.secondDetailValue, controller.getSecondDetailValue()) != 0) {
                this.secondDetailValue = controller.getSecondDetailValue();
                changed = true;
            }
            if (this.displayWidth != controller.getDisplayWidth()) {
                this.displayWidth = controller.getDisplayWidth();
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save(HolderLookup.Provider registries) {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("Settings", this.settings.save(registries));
            tag.putString("DisplayState", this.displayState.name());
            tag.putString("SourceKind", this.sourceKind.name());
            tag.putString("SourceType", this.sourceType);
            tag.putFloat("FillPercent", this.fillPercent);
            tag.putFloat("TotalItems", this.totalItems);
            tag.putFloat("FluidAmount", this.fluidAmount);
            tag.putFloat("FluidCapacity", this.fluidCapacity);
            tag.putFloat("StressPercent", this.stressPercent);
            tag.putFloat("StressSU", this.stressSU);
            tag.putFloat("CapacitySU", this.capacitySU);
            tag.putFloat("RPM", this.rpm);
            tag.putBoolean("FirstDetailPresent", this.firstDetailPresent);
            tag.putString("FirstDetailLabel", this.firstDetailLabel);
            tag.putFloat("FirstDetailValue", this.firstDetailValue);
            tag.putBoolean("SecondDetailPresent", this.secondDetailPresent);
            tag.putString("SecondDetailLabel", this.secondDetailLabel);
            tag.putFloat("SecondDetailValue", this.secondDetailValue);
            tag.putInt("DisplayWidth", this.displayWidth);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static MiniDisplayLink load(CompoundTag tag, HolderLookup.Provider registries) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createMiniDisplayLinkId(pos);
            final MiniDisplayLink link = new MiniDisplayLink(id, pos);
            if (tag.contains("Settings")) {
                link.settings = MiniDisplaySettings.load(tag.getCompound("Settings"), registries);
            }
            try {
                link.displayState = MiniDisplayBE.DisplayState.valueOf(tag.getString("DisplayState"));
            } catch (IllegalArgumentException ignored) {
                link.displayState = MiniDisplayBE.DisplayState.NO_SOURCE;
            }
            try {
                link.sourceKind = InstrumentSourceHelper.SourceKind.valueOf(tag.getString("SourceKind"));
            } catch (IllegalArgumentException ignored) {
                link.sourceKind = InstrumentSourceHelper.SourceKind.NO_SOURCE;
            }
            link.sourceType = tag.getString("SourceType");
            link.fillPercent = tag.getFloat("FillPercent");
            link.totalItems = tag.getFloat("TotalItems");
            link.fluidAmount = tag.getFloat("FluidAmount");
            link.fluidCapacity = tag.getFloat("FluidCapacity");
            link.stressPercent = tag.getFloat("StressPercent");
            link.stressSU = tag.getFloat("StressSU");
            link.capacitySU = tag.getFloat("CapacitySU");
            link.rpm = tag.getFloat("RPM");
            link.firstDetailPresent = tag.getBoolean("FirstDetailPresent");
            link.firstDetailLabel = tag.getString("FirstDetailLabel");
            link.firstDetailValue = tag.getFloat("FirstDetailValue");
            link.secondDetailPresent = tag.getBoolean("SecondDetailPresent");
            link.secondDetailLabel = tag.getString("SecondDetailLabel");
            link.secondDetailValue = tag.getFloat("SecondDetailValue");
            link.displayWidth = Math.max(1, tag.getInt("DisplayWidth"));
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static boolean sameSettings(MiniDisplaySettings current, MiniDisplaySettings other) {
            return current.getSourceMode() == other.getSourceMode()
                    && current.getStorageDetailMode() == other.getStorageDetailMode()
                    && current.getAccentColor() == other.getAccentColor()
                    && current.isLit() == other.isLit()
                    && current.isShowLabels() == other.isShowLabels()
                    && ItemStack.matches(current.getFilterA(), other.getFilterA())
                    && ItemStack.matches(current.getFilterB(), other.getFilterB());
        }
    }

    public static class FreightManifestLink {
        private final String id;
        private final BlockPos sourcePos;
        private FreightStationLink stationLink;
        private FreightManifest manifest;
        private FreightStationCargoView cargoView;
        private com.createstats.train.manifest.DeliveryOrderBoardView deliveryBoard;
        private boolean validManifest;
        private boolean manifestComplete;
        private String summaryStatus;
        private int loadLineCount;
        private int unloadLineCount;
        private long lastDataTick;

        public FreightManifestLink(String id, BlockPos sourcePos) {
            this.id = id;
            this.sourcePos = sourcePos;
            this.stationLink = new FreightStationLink();
            this.manifest = new FreightManifest();
            this.cargoView = FreightStationCargoView.unavailable("Clipboard is not linked to a station.");
            this.deliveryBoard = com.createstats.train.manifest.DeliveryOrderBoardView.empty(0L, "Clipboard is not linked to a station.");
            this.validManifest = true;
            this.manifestComplete = false;
            this.summaryStatus = "Clipboard is not linked to a station.";
        }

        public String getId() {
            return this.id;
        }

        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        public FreightStationLink getStationLink() {
            return this.stationLink.copy();
        }

        public FreightManifest getManifest() {
            return this.manifest.copy();
        }

        public FreightStationCargoView getCargoView() {
            return this.cargoView.copy();
        }

        public com.createstats.train.manifest.DeliveryOrderBoardView getDeliveryBoard() {
            return this.deliveryBoard.copy();
        }

        public boolean isValidManifest() {
            return this.validManifest;
        }

        public boolean isManifestComplete() {
            return this.manifestComplete;
        }

        public String getSummaryStatus() {
            return this.summaryStatus;
        }

        public int getLoadLineCount() {
            return this.loadLineCount;
        }

        public int getUnloadLineCount() {
            return this.unloadLineCount;
        }

        public long getLastDataTick() {
            return this.lastDataTick;
        }

        public boolean updateFrom(FreightManifestClipboardBE clipboard, long tick, HolderLookup.Provider registries) {
            boolean changed = false;
            FreightStationLink newStationLink = clipboard.getStationLink();
            FreightManifest newManifest = new FreightManifest();
            FreightStationCargoView newCargoView = FreightStationCargoView.unavailable("Clipboard is not linked to a station.");
            com.createstats.train.manifest.DeliveryOrderBoardView newDeliveryBoard =
                com.createstats.train.manifest.DeliveryOrderBoardView.empty(tick, "Clipboard is not linked to a station.");
            boolean newValidManifest = true;
            boolean newManifestComplete = false;
            String newSummaryStatus = "Clipboard is not linked to a station.";
            int newLoadLineCount = 0;
            int newUnloadLineCount = 0;

            if (newStationLink.isLinked()) {
                ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(clipboard.getLevel() != null ? clipboard.getLevel().getServer() : null, newStationLink);
                if (stationLevel == null) {
                    newCargoView = FreightStationCargoView.unavailable("The linked station dimension is not available right now.");
                    newDeliveryBoard = com.createstats.train.manifest.DeliveryOrderBoardView.empty(tick, newCargoView.getStatusMessage());
                    newSummaryStatus = newCargoView.getStatusMessage();
                } else {
                    if (newStationLink.getStationId() != null) {
                        FreightManifest stored = FreightManifestSavedData.get(stationLevel).getManifest(newStationLink.getStationId());
                        if (stored != null) {
                            newManifest = stored;
                        }
                    }
                    if (clipboard.getLevel() != null && clipboard.getLevel().getServer() != null) {
                        newCargoView = FreightManifestStationResolver.captureCargoView(clipboard.getLevel().getServer(), newStationLink);
                        newDeliveryBoard = FreightManifestStationResolver.captureDeliveryBoard(clipboard.getLevel().getServer(), newStationLink);
                    }
                    for (FreightManifestLine line : newManifest.getLines()) {
                        if (line.getOperation() == com.createstats.train.manifest.ManifestOperation.LOAD) {
                            newLoadLineCount++;
                        } else {
                            newUnloadLineCount++;
                        }
                    }
                    FreightManifestValidationResult validation = FreightManifestLogic.validate(newManifest.getLines());
                    newValidManifest = validation.isValid();
                    if (newManifest.isEmpty()) {
                        newSummaryStatus = "No manifest lines configured.";
                    } else if (!validation.isValid()) {
                        newSummaryStatus = validation.getMessage();
                    } else {
                        newManifestComplete = newDeliveryBoard.getTotalOrders() > 0
                            && newDeliveryBoard.getCompletedOrders() >= newDeliveryBoard.getTotalOrders();
                        newSummaryStatus = newDeliveryBoard.getSummaryStatus();
                    }
                }
            }

            if (!this.stationLink.save().equals(newStationLink.save())) {
                this.stationLink = newStationLink.copy();
                changed = true;
            }
            if (!this.manifest.save(registries).equals(newManifest.save(registries))) {
                this.manifest = newManifest.copy();
                changed = true;
            }
            if (!sameCargoView(this.cargoView, newCargoView)) {
                this.cargoView = newCargoView.copy();
                changed = true;
            }
            if (!sameDeliveryBoard(this.deliveryBoard, newDeliveryBoard, registries)) {
                this.deliveryBoard = newDeliveryBoard.copy();
                changed = true;
            }
            if (this.validManifest != newValidManifest) {
                this.validManifest = newValidManifest;
                changed = true;
            }
            if (this.manifestComplete != newManifestComplete) {
                this.manifestComplete = newManifestComplete;
                changed = true;
            }
            if (!this.summaryStatus.equals(newSummaryStatus)) {
                this.summaryStatus = newSummaryStatus;
                changed = true;
            }
            if (this.loadLineCount != newLoadLineCount) {
                this.loadLineCount = newLoadLineCount;
                changed = true;
            }
            if (this.unloadLineCount != newUnloadLineCount) {
                this.unloadLineCount = newUnloadLineCount;
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save(HolderLookup.Provider registries) {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.put("StationLink", this.stationLink.save());
            tag.put("Manifest", this.manifest.save(registries));
            tag.put("CargoView", saveCargoView(this.cargoView));
            tag.put("DeliveryBoard", this.deliveryBoard.save(registries));
            tag.putBoolean("ValidManifest", this.validManifest);
            tag.putBoolean("ManifestComplete", this.manifestComplete);
            tag.putString("SummaryStatus", this.summaryStatus);
            tag.putInt("LoadLineCount", this.loadLineCount);
            tag.putInt("UnloadLineCount", this.unloadLineCount);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static FreightManifestLink load(CompoundTag tag, HolderLookup.Provider registries) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createFreightManifestLinkId(pos);
            final FreightManifestLink link = new FreightManifestLink(id, pos);
            if (tag.contains("StationLink")) {
                link.stationLink = FreightStationLink.load(tag.getCompound("StationLink"));
            }
            if (tag.contains("Manifest")) {
                link.manifest = FreightManifest.load(tag.getCompound("Manifest"), registries);
            }
            if (tag.contains("CargoView")) {
                link.cargoView = loadCargoView(tag.getCompound("CargoView"));
            }
            if (tag.contains("DeliveryBoard")) {
                link.deliveryBoard = com.createstats.train.manifest.DeliveryOrderBoardView.load(tag.getCompound("DeliveryBoard"), registries);
            }
            link.validManifest = !tag.contains("ValidManifest") || tag.getBoolean("ValidManifest");
            link.manifestComplete = tag.getBoolean("ManifestComplete");
            link.summaryStatus = tag.contains("SummaryStatus") ? tag.getString("SummaryStatus") : "";
            link.loadLineCount = tag.getInt("LoadLineCount");
            link.unloadLineCount = tag.getInt("UnloadLineCount");
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static CompoundTag saveCargoView(FreightStationCargoView cargoView) {
            return cargoView.save();
        }

        private static FreightStationCargoView loadCargoView(CompoundTag tag) {
            return FreightStationCargoView.load(tag);
        }

        private static boolean sameCargoView(FreightStationCargoView current, FreightStationCargoView other) {
            return current.save().equals(other.save());
        }

        private static boolean sameDeliveryBoard(com.createstats.train.manifest.DeliveryOrderBoardView current,
                                                 com.createstats.train.manifest.DeliveryOrderBoardView other,
                                                 HolderLookup.Provider registries) {
            return current.save(registries).equals(other.save(registries));
        }
    }

    public static class TrainStationLink {
        private final String id;
        private final BlockPos sourcePos;
        @Nullable
        private UUID stationId;
        private String stationName;
        private String stationDimension;
        @Nullable
        private BlockPos stationPos;
        private boolean assembling;
        private FreightManifest manifest;
        private boolean validManifest;
        private String summaryStatus;
        private com.createstats.train.manifest.DeliveryOrderBoardView deliveryBoard;
        private String currentTrainName;
        private FreightStationCargoView currentCargoView;
        private String currentCargoSummary;
        private String nextTrainName;
        private int nextTrainEtaSeconds;
        private int nextTrainDistanceBlocks;
        private boolean nextTrainWaitingForSignal;
        private FreightStationCargoView nextCargoView;
        private String nextCargoSummary;
        private long lastDataTick;

        public TrainStationLink(String id, BlockPos sourcePos) {
            this.id = id;
            this.sourcePos = sourcePos;
            this.stationId = null;
            this.stationName = "Train Station";
            this.stationDimension = "";
            this.stationPos = sourcePos;
            this.assembling = false;
            this.manifest = new FreightManifest();
            this.validManifest = true;
            this.summaryStatus = "Awaiting station data.";
            this.deliveryBoard = com.createstats.train.manifest.DeliveryOrderBoardView.empty(0L, "Awaiting station data.");
            this.currentTrainName = "";
            this.currentCargoView = FreightStationCargoView.unavailable("No train currently docked.");
            this.currentCargoSummary = "No train currently docked.";
            this.nextTrainName = "";
            this.nextTrainEtaSeconds = -1;
            this.nextTrainDistanceBlocks = -1;
            this.nextTrainWaitingForSignal = false;
            this.nextCargoView = FreightStationCargoView.unavailable("No inbound train detected.");
            this.nextCargoSummary = "No inbound train detected.";
        }

        public String getId() {
            return this.id;
        }

        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        @Nullable
        public UUID getStationId() {
            return this.stationId;
        }

        public String getStationName() {
            return this.stationName;
        }

        public String getStationDimension() {
            return this.stationDimension;
        }

        @Nullable
        public BlockPos getStationPos() {
            return this.stationPos;
        }

        public boolean isAssembling() {
            return this.assembling;
        }

        public FreightManifest getManifest() {
            return this.manifest.copy();
        }

        public boolean isValidManifest() {
            return this.validManifest;
        }

        public String getSummaryStatus() {
            return this.summaryStatus;
        }

        public com.createstats.train.manifest.DeliveryOrderBoardView getDeliveryBoard() {
            return this.deliveryBoard.copy();
        }

        public String getCurrentTrainName() {
            return this.currentTrainName;
        }

        public FreightStationCargoView getCurrentCargoView() {
            return this.currentCargoView.copy();
        }

        public String getCurrentCargoSummary() {
            return this.currentCargoSummary;
        }

        public String getNextTrainName() {
            return this.nextTrainName;
        }

        public int getNextTrainEtaSeconds() {
            return this.nextTrainEtaSeconds;
        }

        public int getNextTrainDistanceBlocks() {
            return this.nextTrainDistanceBlocks;
        }

        public boolean isNextTrainWaitingForSignal() {
            return this.nextTrainWaitingForSignal;
        }

        public FreightStationCargoView getNextCargoView() {
            return this.nextCargoView.copy();
        }

        public String getNextCargoSummary() {
            return this.nextCargoSummary;
        }

        public long getLastDataTick() {
            return this.lastDataTick;
        }

        public boolean updateFrom(StationBlockEntity stationBlockEntity, @Nullable TrainMapData trainMapData, long tick, HolderLookup.Provider registries) {
            boolean changed = false;
            final Level level = stationBlockEntity.getLevel();
            final GlobalStation station = stationBlockEntity.getStation();
            final UUID newStationId = station != null ? station.getId() : null;
            final String newStationName = station != null && station.name != null && !station.name.isBlank() ? station.name : "Train Station";
            final String newStationDimension = station != null && station.getBlockEntityDimension() != null
                ? station.getBlockEntityDimension().location().toString()
                : (level != null ? level.dimension().location().toString() : "");
            final BlockPos newStationPos = station != null && station.getBlockEntityPos() != null
                ? station.getBlockEntityPos().immutable()
                : stationBlockEntity.getBlockPos().immutable();
            final boolean newAssembling = station != null && station.assembling;

            FreightManifest newManifest = new FreightManifest();
            FreightManifestValidationResult validation = FreightManifestValidationResult.ok();
            boolean newValidManifest = true;
            com.createstats.train.manifest.DeliveryOrderBoardView newDeliveryBoard =
                com.createstats.train.manifest.DeliveryOrderBoardView.empty(tick, "No delivery orders configured.");
            if (level instanceof ServerLevel serverLevel && newStationId != null) {
                final FreightManifest storedManifest = FreightManifestSavedData.get(serverLevel).getManifest(newStationId);
                if (storedManifest != null) {
                    newManifest = storedManifest.copy();
                }
                validation = FreightManifestLogic.validate(newManifest.getLines());
                newValidManifest = validation.isValid();
                final net.minecraft.resources.ResourceLocation stationDimensionId = net.minecraft.resources.ResourceLocation.tryParse(newStationDimension);
                if (stationDimensionId != null && newStationPos != null) {
                    final FreightStationLink tempLink = new FreightStationLink(newStationId, newStationName, stationDimensionId, newStationPos);
                    newDeliveryBoard = FreightManifestStationResolver.captureDeliveryBoard(serverLevel, tempLink);
                }
            }

            final Train presentTrain = station != null ? station.getPresentTrain() : null;
            String newCurrentTrainName = "";
            FreightStationCargoView newCurrentCargoView = FreightStationCargoView.unavailable("No train currently docked.");
            String newCurrentCargoSummary = "No train currently docked.";
            if (presentTrain != null) {
                newCurrentTrainName = resolveTrainName(presentTrain);
                final com.createstats.train.manifest.FreightManifestStationState presentStationState =
                    level instanceof ServerLevel presentLevel && newStationId != null
                        ? FreightManifestSavedData.get(presentLevel).getStationState(newStationId)
                        : null;
                newCurrentCargoView = FreightStationCargoView.capture(presentTrain,
                    FreightManifestLogic.captureVisitProgress(presentStationState, presentTrain));
                newCurrentCargoSummary = buildCargoConditionSummary(newManifest, validation, newCurrentCargoView);
            }

            final InboundTrainPreview incomingPreview = findBestInboundPreview(newStationName, newStationDimension, newStationPos, trainMapData);
            String newNextTrainName = "";
            int newNextTrainEtaSeconds = -1;
            int newNextTrainDistanceBlocks = -1;
            boolean newNextTrainWaitingForSignal = false;
            FreightStationCargoView newNextCargoView = FreightStationCargoView.unavailable("No inbound train detected.");
            String newNextCargoSummary = "No inbound train detected.";
            if (incomingPreview != null) {
                newNextTrainName = incomingPreview.trainName;
                newNextTrainEtaSeconds = incomingPreview.etaSeconds;
                newNextTrainDistanceBlocks = incomingPreview.distanceBlocks;
                newNextTrainWaitingForSignal = incomingPreview.waitingForSignal;
                newNextCargoView = incomingPreview.cargoView.copy();
                newNextCargoSummary = buildCargoConditionSummary(newManifest, validation, newNextCargoView);
            }

            final String newSummaryStatus = buildStationSummary(newAssembling, presentTrain != null, incomingPreview);

            if (!Objects.equals(this.stationId, newStationId)) {
                this.stationId = newStationId;
                changed = true;
            }
            if (!this.stationName.equals(newStationName)) {
                this.stationName = newStationName;
                changed = true;
            }
            if (!this.stationDimension.equals(newStationDimension)) {
                this.stationDimension = newStationDimension;
                changed = true;
            }
            if (!Objects.equals(this.stationPos, newStationPos)) {
                this.stationPos = newStationPos;
                changed = true;
            }
            if (this.assembling != newAssembling) {
                this.assembling = newAssembling;
                changed = true;
            }
            if (!this.manifest.save(registries).equals(newManifest.save(registries))) {
                this.manifest = newManifest.copy();
                changed = true;
            }
            if (this.validManifest != newValidManifest) {
                this.validManifest = newValidManifest;
                changed = true;
            }
            if (!sameDeliveryBoard(this.deliveryBoard, newDeliveryBoard, registries)) {
                this.deliveryBoard = newDeliveryBoard.copy();
                changed = true;
            }
            if (!this.summaryStatus.equals(newSummaryStatus)) {
                this.summaryStatus = newSummaryStatus;
                changed = true;
            }
            if (!this.currentTrainName.equals(newCurrentTrainName)) {
                this.currentTrainName = newCurrentTrainName;
                changed = true;
            }
            if (!sameCargoView(this.currentCargoView, newCurrentCargoView)) {
                this.currentCargoView = newCurrentCargoView.copy();
                changed = true;
            }
            if (!this.currentCargoSummary.equals(newCurrentCargoSummary)) {
                this.currentCargoSummary = newCurrentCargoSummary;
                changed = true;
            }
            if (!this.nextTrainName.equals(newNextTrainName)) {
                this.nextTrainName = newNextTrainName;
                changed = true;
            }
            if (this.nextTrainEtaSeconds != newNextTrainEtaSeconds) {
                this.nextTrainEtaSeconds = newNextTrainEtaSeconds;
                changed = true;
            }
            if (this.nextTrainDistanceBlocks != newNextTrainDistanceBlocks) {
                this.nextTrainDistanceBlocks = newNextTrainDistanceBlocks;
                changed = true;
            }
            if (this.nextTrainWaitingForSignal != newNextTrainWaitingForSignal) {
                this.nextTrainWaitingForSignal = newNextTrainWaitingForSignal;
                changed = true;
            }
            if (!sameCargoView(this.nextCargoView, newNextCargoView)) {
                this.nextCargoView = newNextCargoView.copy();
                changed = true;
            }
            if (!this.nextCargoSummary.equals(newNextCargoSummary)) {
                this.nextCargoSummary = newNextCargoSummary;
                changed = true;
            }
            if (this.lastDataTick != tick) {
                this.lastDataTick = tick;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save(HolderLookup.Provider registries) {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            if (this.stationId != null) {
                tag.putUUID("StationId", this.stationId);
            }
            tag.putString("StationName", this.stationName);
            tag.putString("StationDimension", this.stationDimension);
            if (this.stationPos != null) {
                tag.putLong("StationPos", this.stationPos.asLong());
            }
            tag.putBoolean("Assembling", this.assembling);
            tag.put("Manifest", this.manifest.save(registries));
            tag.putBoolean("ValidManifest", this.validManifest);
            tag.putString("SummaryStatus", this.summaryStatus);
            tag.put("DeliveryBoard", this.deliveryBoard.save(registries));
            tag.putString("CurrentTrainName", this.currentTrainName);
            tag.put("CurrentCargoView", this.currentCargoView.save());
            tag.putString("CurrentCargoSummary", this.currentCargoSummary);
            tag.putString("NextTrainName", this.nextTrainName);
            tag.putInt("NextTrainEtaSeconds", this.nextTrainEtaSeconds);
            tag.putInt("NextTrainDistanceBlocks", this.nextTrainDistanceBlocks);
            tag.putBoolean("NextTrainWaitingForSignal", this.nextTrainWaitingForSignal);
            tag.put("NextCargoView", this.nextCargoView.save());
            tag.putString("NextCargoSummary", this.nextCargoSummary);
            tag.putLong("LastDataTick", this.lastDataTick);
            return tag;
        }

        public static TrainStationLink load(CompoundTag tag, HolderLookup.Provider registries) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createTrainStationLinkId(pos);
            final TrainStationLink link = new TrainStationLink(id, pos);
            if (tag.hasUUID("StationId")) {
                link.stationId = tag.getUUID("StationId");
            }
            link.stationName = tag.contains("StationName") ? tag.getString("StationName") : link.stationName;
            link.stationDimension = tag.contains("StationDimension") ? tag.getString("StationDimension") : "";
            if (tag.contains("StationPos")) {
                link.stationPos = BlockPos.of(tag.getLong("StationPos"));
            }
            link.assembling = tag.getBoolean("Assembling");
            if (tag.contains("Manifest")) {
                link.manifest = FreightManifest.load(tag.getCompound("Manifest"), registries);
            }
            link.validManifest = !tag.contains("ValidManifest") || tag.getBoolean("ValidManifest");
            link.summaryStatus = tag.contains("SummaryStatus") ? tag.getString("SummaryStatus") : link.summaryStatus;
            if (tag.contains("DeliveryBoard")) {
                link.deliveryBoard = com.createstats.train.manifest.DeliveryOrderBoardView.load(tag.getCompound("DeliveryBoard"), registries);
            }
            link.currentTrainName = tag.contains("CurrentTrainName") ? tag.getString("CurrentTrainName") : "";
            if (tag.contains("CurrentCargoView")) {
                link.currentCargoView = FreightStationCargoView.load(tag.getCompound("CurrentCargoView"));
            }
            link.currentCargoSummary = tag.contains("CurrentCargoSummary") ? tag.getString("CurrentCargoSummary") : link.currentCargoSummary;
            link.nextTrainName = tag.contains("NextTrainName") ? tag.getString("NextTrainName") : "";
            link.nextTrainEtaSeconds = tag.contains("NextTrainEtaSeconds") ? tag.getInt("NextTrainEtaSeconds") : -1;
            link.nextTrainDistanceBlocks = tag.contains("NextTrainDistanceBlocks") ? tag.getInt("NextTrainDistanceBlocks") : -1;
            link.nextTrainWaitingForSignal = tag.getBoolean("NextTrainWaitingForSignal");
            if (tag.contains("NextCargoView")) {
                link.nextCargoView = FreightStationCargoView.load(tag.getCompound("NextCargoView"));
            }
            link.nextCargoSummary = tag.contains("NextCargoSummary") ? tag.getString("NextCargoSummary") : link.nextCargoSummary;
            link.lastDataTick = tag.getLong("LastDataTick");
            return link;
        }

        private static String resolveTrainName(Train train) {
            return train.name != null ? train.name.getString() : "Train";
        }

        private static String buildStationSummary(boolean assembling, boolean hasDockedTrain, @Nullable InboundTrainPreview incomingPreview) {
            if (assembling) {
                return "Assembling";
            }
            if (hasDockedTrain) {
                return "Train docked";
            }
            if (incomingPreview != null) {
                if (incomingPreview.etaSeconds >= 0) {
                    return "Next arrival in " + incomingPreview.etaSeconds + "s";
                }
                if (incomingPreview.waitingForSignal) {
                    return "Inbound train waiting for signal";
                }
                return "Inbound train detected";
            }
            return "No trains incoming";
        }

        private static String buildCargoConditionSummary(FreightManifest manifest, FreightManifestValidationResult validation, FreightStationCargoView cargoView) {
            if (cargoView == null || !cargoView.isTrainPresent()) {
                return cargoView != null ? cargoView.getStatusMessage() : "Cargo data unavailable.";
            }
            if (manifest == null || manifest.isEmpty()) {
                return cargoView.getFreightTotals().getAmounts().size() + " cargo types onboard";
            }
            if (!validation.isValid()) {
                return validation.getMessage();
            }
            final Map<com.createstats.train.manifest.FreightManifestResourceKey, Integer> unloadGoals = FreightManifestLogic.getExpectedAmounts(manifest, com.createstats.train.manifest.ManifestOperation.UNLOAD);
            final Map<com.createstats.train.manifest.FreightManifestResourceKey, Integer> loadGoals = FreightManifestLogic.getExpectedAmounts(manifest, com.createstats.train.manifest.ManifestOperation.LOAD);
            final TrainCargoSnapshot totals = cargoView.getFreightTotals();
            int unloadReady = 0;
            String missingLabel = "";
            for (Map.Entry<com.createstats.train.manifest.FreightManifestResourceKey, Integer> entry : unloadGoals.entrySet()) {
                final int available = totals.getAmount(entry.getKey());
                if (available >= entry.getValue()) {
                    unloadReady++;
                    continue;
                }
                if (missingLabel.isEmpty()) {
                    final int missing = entry.getValue() - available;
                    missingLabel = "missing " + FreightManifestLogic.formatAmount(entry.getKey().getCargoKind(), missing)
                        + " " + FreightManifestLogic.describeResource(entry.getKey());
                }
            }
            if (unloadGoals.isEmpty()) {
                return loadGoals.isEmpty()
                    ? "No cargo actions configured"
                    : ("No unload cargo required | load lines " + loadGoals.size());
            }
            String summary = "Unload ready " + unloadReady + "/" + unloadGoals.size();
            if (!missingLabel.isEmpty()) {
                summary += " | " + missingLabel;
            }
            if (!loadGoals.isEmpty()) {
                summary += " | load lines " + loadGoals.size();
            }
            return summary;
        }

        @Nullable
        private static InboundTrainPreview findBestInboundPreview(String stationName, String stationDimension, @Nullable BlockPos stationPos, @Nullable TrainMapData trainMapData) {
            if (trainMapData == null || stationName == null || stationName.isBlank()) {
                return null;
            }
            InboundTrainPreview best = null;
            for (TrainMapData.TrainEntry trainEntry : trainMapData.getTrains()) {
                if (!TrainMapData.matchesStationFilter(trainEntry.nextDestination, stationName)) {
                    continue;
                }
                if (!stationDimension.isEmpty() && !stationDimension.equals(trainEntry.dimension)) {
                    continue;
                }
                if (trainEntry.stopped && stationName.equals(trainEntry.currentStationName)) {
                    continue;
                }
                final int distanceBlocks = estimateDistanceBlocks(trainEntry, stationPos);
                final int etaSeconds = estimateEtaSeconds(trainEntry, stationPos);
                final Train train = Create.RAILWAYS != null ? Create.RAILWAYS.trains.get(trainEntry.id) : null;
                final FreightStationCargoView cargoView = train != null
                    ? FreightStationCargoView.preview(train, "Arrival cargo preview")
                    : FreightStationCargoView.unavailable("Inbound train cargo preview unavailable.");
                final InboundTrainPreview candidate = new InboundTrainPreview(resolveTrainName(train, trainEntry.name), etaSeconds, distanceBlocks, trainEntry.waitingForSignal, cargoView);
                if (best == null || isBetterInboundCandidate(candidate, best)) {
                    best = candidate;
                }
            }
            return best;
        }

        private static String resolveTrainName(@Nullable Train train, String fallbackName) {
            if (train != null) {
                return resolveTrainName(train);
            }
            return fallbackName == null || fallbackName.isBlank() ? "Train" : fallbackName;
        }

        private static int estimateDistanceBlocks(TrainMapData.TrainEntry trainEntry, @Nullable BlockPos stationPos) {
            if (stationPos == null) {
                return -1;
            }
            final double dx = trainEntry.x - stationPos.getX();
            final double dz = trainEntry.z - stationPos.getZ();
            return (int)Math.round(Math.sqrt(dx * dx + dz * dz));
        }

        private static int estimateEtaSeconds(TrainMapData.TrainEntry trainEntry, @Nullable BlockPos stationPos) {
            if (stationPos == null || trainEntry.waitingForSignal || trainEntry.stopped || Math.abs(trainEntry.speed) <= 0.01f) {
                return -1;
            }
            final int distanceBlocks = estimateDistanceBlocks(trainEntry, stationPos);
            if (distanceBlocks < 0) {
                return -1;
            }
            final double speedBlocksPerSecond = Math.abs(trainEntry.speed) * 20.0;
            if (speedBlocksPerSecond <= 0.0) {
                return -1;
            }
            return Math.max(0, (int)Math.ceil(distanceBlocks / speedBlocksPerSecond));
        }

        private static boolean isBetterInboundCandidate(InboundTrainPreview candidate, InboundTrainPreview currentBest) {
            final boolean candidateKnown = candidate.etaSeconds >= 0;
            final boolean currentKnown = currentBest.etaSeconds >= 0;
            if (candidateKnown != currentKnown) {
                return candidateKnown;
            }
            if (candidateKnown && candidate.etaSeconds != currentBest.etaSeconds) {
                return candidate.etaSeconds < currentBest.etaSeconds;
            }
            if (candidate.distanceBlocks >= 0 && currentBest.distanceBlocks >= 0 && candidate.distanceBlocks != currentBest.distanceBlocks) {
                return candidate.distanceBlocks < currentBest.distanceBlocks;
            }
            if (candidate.waitingForSignal != currentBest.waitingForSignal) {
                return !candidate.waitingForSignal;
            }
            return candidate.trainName.compareToIgnoreCase(currentBest.trainName) < 0;
        }

        private static boolean sameCargoView(FreightStationCargoView current, FreightStationCargoView other) {
            return current.save().equals(other.save());
        }

        private static boolean sameDeliveryBoard(com.createstats.train.manifest.DeliveryOrderBoardView current,
                                                 com.createstats.train.manifest.DeliveryOrderBoardView other,
                                                 HolderLookup.Provider registries) {
            return current.save(registries).equals(other.save(registries));
        }
    }

    public static class BattleScreenLink {
        private final String id;
        private final BlockPos sourcePos;
        private String displayName;
        private int observerCount;
        private int weaponObserverCount;
        private double overallHealthPercent;
        private int totalMissingBlocks;
        private int totalNewBlocks;
        private List<BattleObserverSnapshot> observers;
        private long lastDataTick;

        private long lastForcedSyncTick;

        private int battleViewMode;

        public BattleScreenLink(String id, BlockPos sourcePos) {
            this.id = id;
            this.sourcePos = sourcePos;
            this.displayName = BattleCompactTargetHandler.SOURCE_TYPE;
            this.observerCount = 0;
            this.weaponObserverCount = 0;
            this.overallHealthPercent = 0.0;
            this.totalMissingBlocks = 0;
            this.totalNewBlocks = 0;
            this.observers = List.of();
            this.lastDataTick = 0L;
            this.lastForcedSyncTick = 0L;
            this.battleViewMode = 0;
        }

        public String getId() {
            return this.id;
        }

        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getObserverCount() {
            return this.observerCount;
        }

        public int getWeaponObserverCount() {
            return this.weaponObserverCount;
        }

        public double getOverallHealthPercent() {
            return this.overallHealthPercent;
        }

        public int getTotalMissingBlocks() {
            return this.totalMissingBlocks;
        }

        public int getTotalNewBlocks() {
            return this.totalNewBlocks;
        }

        public List<BattleObserverSnapshot> getObservers() {
            return this.observers;
        }

        public long getLastDataTick() {
            return this.lastDataTick;
        }

        public long getLastForcedSyncTick() {
            return this.lastForcedSyncTick;
        }

        public void setLastForcedSyncTick(long tick) {
            this.lastForcedSyncTick = tick;
        }

        public int getBattleViewMode() {
            return this.battleViewMode;
        }

        public boolean setBattleViewMode(int mode) {
            final int clamped = mode <= 0 ? 0 : 1;
            if (this.battleViewMode == clamped) {
                return false;
            }
            this.battleViewMode = clamped;
            return true;
        }

        public boolean updateFrom(BattleScreenDisplayBE battleScreen, long tick) {
            boolean changed = false;
            final String newDisplayName = battleScreen.getBlockState().getBlock().getName().getString();
            final List<BattleObserverSnapshot> newObservers = battleScreen.getBattleFeedSnapshots();
            int newWeaponObserverCount = 0;
            int newTotalMissingBlocks = 0;
            int newTotalNewBlocks = 0;
            double totalHealth = 0.0;
            for (BattleObserverSnapshot observer : newObservers) {
                totalHealth += observer.healthPercent();
                newTotalMissingBlocks += observer.missingBlocks();
                newTotalNewBlocks += observer.newBlocks();
                if (observer.weapon().detected()) {
                    newWeaponObserverCount++;
                }
            }
            final int newObserverCount = newObservers.size();
            final double newOverallHealthPercent = newObserverCount == 0 ? 0.0 : totalHealth / newObserverCount;
            if (!this.displayName.equals(newDisplayName)) {
                this.displayName = newDisplayName;
                changed = true;
            }
            if (this.observerCount != newObserverCount) {
                this.observerCount = newObserverCount;
                changed = true;
            }
            if (this.weaponObserverCount != newWeaponObserverCount) {
                this.weaponObserverCount = newWeaponObserverCount;
                changed = true;
            }
            if (Double.compare(this.overallHealthPercent, newOverallHealthPercent) != 0) {
                this.overallHealthPercent = newOverallHealthPercent;
                changed = true;
            }
            if (this.totalMissingBlocks != newTotalMissingBlocks) {
                this.totalMissingBlocks = newTotalMissingBlocks;
                changed = true;
            }
            if (this.totalNewBlocks != newTotalNewBlocks) {
                this.totalNewBlocks = newTotalNewBlocks;
                changed = true;
            }
            if (!sameObservers(this.observers, newObservers)) {
                this.observers = List.copyOf(newObservers);
                changed = true;
            }
            this.lastDataTick = tick;
            return changed;
        }

        private static boolean sameObservers(List<BattleObserverSnapshot> current, List<BattleObserverSnapshot> updated) {
            if (current.size() != updated.size()) {
                return false;
            }
            for (int i = 0; i < current.size(); ++i) {
                if (!current.get(i).sameDisplayData(updated.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putLong("Pos", this.sourcePos.asLong());
            tag.putString("DisplayName", this.displayName);
            tag.putInt("ObserverCount", this.observerCount);
            tag.putInt("WeaponObserverCount", this.weaponObserverCount);
            tag.putDouble("OverallHealthPercent", this.overallHealthPercent);
            tag.putInt("TotalMissingBlocks", this.totalMissingBlocks);
            tag.putInt("TotalNewBlocks", this.totalNewBlocks);
            final ListTag observerList = new ListTag();
            for (BattleObserverSnapshot observer : this.observers) {
                observerList.add(observer.save());
            }
            tag.put("Observers", observerList);
            tag.putLong("LastDataTick", this.lastDataTick);
            tag.putInt("BattleViewMode", this.battleViewMode);
            return tag;
        }

        public static BattleScreenLink load(CompoundTag tag) {
            final BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            final String id = tag.contains("Id") ? tag.getString("Id") : createBattleScreenLinkId(pos);
            final BattleScreenLink link = new BattleScreenLink(id, pos);
            link.displayName = tag.contains("DisplayName") ? tag.getString("DisplayName") : link.displayName;
            link.observerCount = tag.getInt("ObserverCount");
            link.weaponObserverCount = tag.getInt("WeaponObserverCount");
            link.overallHealthPercent = tag.contains("OverallHealthPercent") ? tag.getDouble("OverallHealthPercent") : 0.0;
            link.totalMissingBlocks = tag.getInt("TotalMissingBlocks");
            link.totalNewBlocks = tag.getInt("TotalNewBlocks");
            if (tag.contains("Observers", Tag.TAG_LIST)) {
                final ListTag observerList = tag.getList("Observers", Tag.TAG_COMPOUND);
                final List<BattleObserverSnapshot> loadedObservers = new ArrayList<BattleObserverSnapshot>(observerList.size());
                for (int i = 0; i < observerList.size(); ++i) {
                    loadedObservers.add(BattleObserverSnapshot.load(observerList.getCompound(i)));
                }
                link.observers = List.copyOf(loadedObservers);
            }
            link.lastDataTick = tag.getLong("LastDataTick");
            link.battleViewMode = tag.contains("BattleViewMode") ? (tag.getInt("BattleViewMode") <= 0 ? 0 : 1) : 0;
            return link;
        }
    }

    private static final class InboundTrainPreview {
        private final String trainName;
        private final int etaSeconds;
        private final int distanceBlocks;
        private final boolean waitingForSignal;
        private final FreightStationCargoView cargoView;

        private InboundTrainPreview(String trainName, int etaSeconds, int distanceBlocks, boolean waitingForSignal, FreightStationCargoView cargoView) {
            this.trainName = trainName;
            this.etaSeconds = etaSeconds;
            this.distanceBlocks = distanceBlocks;
            this.waitingForSignal = waitingForSignal;
            this.cargoView = cargoView.copy();
        }
    }
}
