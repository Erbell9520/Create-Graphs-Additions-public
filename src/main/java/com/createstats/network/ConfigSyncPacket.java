package com.createstats.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.display.InstrumentStyleFamily;
import com.createstats.display.InstrumentStyleOptions;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentDensityPreset;
import com.createstats.display.CustomScreenLayout;
import com.createstats.display.StatsColorScheme;
import com.createstats.blockentity.StatsDisplayPanelBE;
import java.util.Iterator;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Collection;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.DisplayTheme;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ConfigSyncPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final ConfigType configType;
    private boolean scanEnabled;
    private String channelId;
    private float alertThreshold;
    private boolean alertAbove;
    private boolean alertEnabled;
    private String redstoneChannelId;
    private float redstoneThreshold;
    private boolean redstoneAbove;
    private boolean redstoneEnabled;
    private int redstoneMode;
    private int themeOrdinal;
    private int graphTypeOrdinal;
    private int colorSchemeOrdinal;
    private int timeRangeOrdinal;
    private boolean showGrid;
    private boolean showAxisLabels;
    private boolean showMinMaxAvg;
    private boolean doubleSided;
    private boolean showValueOverlay;
    private int cycleInterval;
    private Set<String> hiddenChannelIds;
    private int customAccentColor;
    private float predictionRatio;
    private int displayModeOrdinal;
    private boolean aeroFlightDeckEnabled;
    private boolean aerolineDirectorEnabled;
    private String dashboardMainChannelId;
    private String preferredSourceId;
    private int vehicleNoseOrdinal;
    private int vehiclePresetOrdinal;
    private boolean dashboardLayoutLocked;
    private int clockFormat;
    private float analogGaugeMin;
    private float analogGaugeMax;
    private float analogZoneYellow;
    private float analogZoneRed;
    private boolean analogShowZones;
    private boolean analogShowGlass;
    private boolean analogShowRivets;
    private String instrumentStyleFamily;
    private String instrumentDensityPreset;
    private String instrumentEffectPreset;
    private InstrumentStyleOptions instrumentStyleOptions;
    private boolean topologyShowLabels;
    private boolean topologyAnimateEdges;
    private boolean topologyColorByStress;
    private int topologyMaxNodes;
    private int trainMapQualityOrdinal;
    private boolean autoScale;
    private boolean predictionEnabled;
    private int battleBoardPresetOrdinal;
    private CompoundTag customScreenLayoutTag;
    private CompoundTag customScreenStateTag;
    private boolean customScreenLayoutEnabled;

    public static final CustomPacketPayload.Type<ConfigSyncPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "config_sync"));
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), ConfigSyncPacket::new);

    private ConfigSyncPacket(BlockPos pos, ConfigType configType) {
        this.hiddenChannelIds = new HashSet<String>();
        this.dashboardMainChannelId = "";
        this.preferredSourceId = "";
        this.instrumentStyleFamily = "";
        this.instrumentDensityPreset = "";
        this.instrumentEffectPreset = "";
        this.instrumentStyleOptions = new InstrumentStyleOptions();
        this.customScreenStateTag = null;
        this.customScreenLayoutEnabled = false;
        this.pos = pos;
        this.configType = configType;
    }

    public static ConfigSyncPacket scanToggle(BlockPos pos, boolean enabled) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.SCAN_TOGGLE);
        pkt.scanEnabled = enabled;
        return pkt;
    }

    public static ConfigSyncPacket alertConfig(BlockPos pos, String channelId, float threshold, boolean above, boolean enabled) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.ALERT_CONFIG);
        pkt.channelId = channelId;
        pkt.alertThreshold = threshold;
        pkt.alertAbove = above;
        pkt.alertEnabled = enabled;
        return pkt;
    }

    public static ConfigSyncPacket redstoneConfig(BlockPos pos, String channelId, float threshold, boolean above, boolean enabled, int mode) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.REDSTONE_CONFIG);
        pkt.redstoneChannelId = channelId;
        pkt.redstoneThreshold = threshold;
        pkt.redstoneAbove = above;
        pkt.redstoneEnabled = enabled;
        pkt.redstoneMode = mode;
        return pkt;
    }

    public static ConfigSyncPacket themeChange(BlockPos pos, DisplayTheme theme) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.THEME_CHANGE);
        pkt.themeOrdinal = theme.ordinal();
        return pkt;
    }

    public static ConfigSyncPacket settingsChange(BlockPos pos, StatsDisplaySettings settings) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.SETTINGS_CHANGE);
        pkt.graphTypeOrdinal = settings.getGraphType().ordinal();
        pkt.colorSchemeOrdinal = settings.getColorScheme().ordinal();
        pkt.timeRangeOrdinal = settings.getTimeRange().ordinal();
        pkt.themeOrdinal = settings.getDisplayTheme().ordinal();
        pkt.showGrid = settings.showGrid();
        pkt.showAxisLabels = settings.showAxisLabels();
        pkt.showMinMaxAvg = settings.showMinMaxAvg();
        pkt.doubleSided = settings.isDoubleSided();
        pkt.showValueOverlay = settings.showValueOverlay();
        pkt.cycleInterval = settings.getCycleInterval();
        pkt.hiddenChannelIds = new HashSet<String>(settings.getHiddenChannelIds());
        pkt.customAccentColor = settings.getCustomAccentColor();
        pkt.predictionRatio = settings.getPredictionRatio();
        pkt.predictionEnabled = settings.isPredictionEnabled();
        pkt.displayModeOrdinal = settings.getDisplayMode().ordinal();
        pkt.aeroFlightDeckEnabled = settings.isAeroFlightDeckEnabled();
        pkt.aerolineDirectorEnabled = settings.isAerolineDirectorEnabled();
        pkt.dashboardMainChannelId = settings.getDashboardMainChannelId();
        pkt.preferredSourceId = settings.getPreferredSourceId();
        pkt.vehicleNoseOrdinal = settings.getNoseSetting().ordinal();
        pkt.vehiclePresetOrdinal = settings.getVehiclePreset().ordinal();
        pkt.dashboardLayoutLocked = settings.isDashboardLayoutLocked();
        pkt.clockFormat = settings.getClockFormat();
        pkt.analogGaugeMin = settings.getAnalogGaugeMin();
        pkt.analogGaugeMax = settings.getAnalogGaugeMax();
        pkt.analogZoneYellow = settings.getAnalogZoneYellow();
        pkt.analogZoneRed = settings.getAnalogZoneRed();
        pkt.analogShowZones = settings.isAnalogShowZones();
        pkt.analogShowGlass = settings.isAnalogShowGlass();
        pkt.analogShowRivets = settings.isAnalogShowRivets();
        pkt.instrumentStyleFamily = settings.getInstrumentStyleFamily().name();
        pkt.instrumentDensityPreset = settings.getInstrumentDensityPreset().name();
        pkt.instrumentEffectPreset = settings.getInstrumentEffectPreset().name();
        pkt.instrumentStyleOptions = settings.getInstrumentStyleOptions().copy();
        pkt.topologyShowLabels = settings.topologyShowLabels();
        pkt.topologyAnimateEdges = settings.topologyAnimateEdges();
        pkt.topologyColorByStress = settings.topologyColorByStress();
        pkt.topologyMaxNodes = settings.getTopologyMaxNodes();
        pkt.trainMapQualityOrdinal = settings.getTrainMapRenderQuality().ordinal();
        pkt.autoScale = settings.isAutoScale();
        pkt.battleBoardPresetOrdinal = settings.getBattleBoardPreset().ordinal();
        pkt.customScreenStateTag = settings.hasCustomScreenLayout() ? settings.saveCustomScreenState() : null;
        pkt.customScreenLayoutEnabled = settings.isCustomScreenLayoutEnabled();
        return pkt;
    }

    public static ConfigSyncPacket predictionToggle(BlockPos pos, boolean enabled) {
        final ConfigSyncPacket pkt = new ConfigSyncPacket(pos, ConfigType.PREDICTION_TOGGLE);
        pkt.predictionEnabled = enabled;
        return pkt;
    }

    public ConfigSyncPacket(FriendlyByteBuf buf) {
        this.hiddenChannelIds = new HashSet<String>();
        this.dashboardMainChannelId = "";
        this.preferredSourceId = "";
        this.instrumentStyleFamily = "";
        this.instrumentDensityPreset = "";
        this.instrumentEffectPreset = "";
        this.instrumentStyleOptions = new InstrumentStyleOptions();
        this.customScreenStateTag = null;
        this.customScreenLayoutEnabled = false;
        this.pos = buf.readBlockPos();
        this.configType = (ConfigType)buf.readEnum((Class)ConfigType.class);
        switch (this.configType) {
            case SCAN_TOGGLE: {
                this.scanEnabled = buf.readBoolean();
                break;
            }
            case ALERT_CONFIG: {
                this.channelId = buf.readUtf(256);
                this.alertThreshold = buf.readFloat();
                this.alertAbove = buf.readBoolean();
                this.alertEnabled = buf.readBoolean();
                break;
            }
            case REDSTONE_CONFIG: {
                this.redstoneChannelId = buf.readUtf(256);
                this.redstoneThreshold = buf.readFloat();
                this.redstoneAbove = buf.readBoolean();
                this.redstoneEnabled = buf.readBoolean();
                this.redstoneMode = buf.readVarInt();
                break;
            }
            case THEME_CHANGE: {
                this.themeOrdinal = buf.readVarInt();
                break;
            }
            case PREDICTION_TOGGLE: {
                this.predictionEnabled = buf.readBoolean();
                break;
            }
            case SETTINGS_CHANGE: {
                this.graphTypeOrdinal = buf.readVarInt();
                this.colorSchemeOrdinal = buf.readVarInt();
                this.timeRangeOrdinal = buf.readVarInt();
                this.themeOrdinal = buf.readVarInt();
                this.showGrid = buf.readBoolean();
                this.showAxisLabels = buf.readBoolean();
                this.showMinMaxAvg = buf.readBoolean();
                this.doubleSided = buf.readBoolean();
                this.showValueOverlay = buf.readBoolean();
                this.cycleInterval = buf.readVarInt();
                final int hiddenCount = buf.readVarInt();
                this.hiddenChannelIds = new HashSet<String>();
                for (int i = 0; i < hiddenCount; ++i) {
                    this.hiddenChannelIds.add(buf.readUtf(256));
                }
                this.customAccentColor = buf.readInt();
                this.predictionRatio = buf.readFloat();
                this.predictionEnabled = buf.readBoolean();
                this.displayModeOrdinal = buf.readVarInt();
                this.aeroFlightDeckEnabled = buf.readBoolean();
                this.aerolineDirectorEnabled = buf.readBoolean();
                this.dashboardMainChannelId = buf.readUtf(256);
                this.preferredSourceId = buf.readUtf(256);
                this.vehicleNoseOrdinal = buf.readVarInt();
                this.vehiclePresetOrdinal = buf.readVarInt();
                this.dashboardLayoutLocked = buf.readBoolean();
                this.clockFormat = buf.readVarInt();
                this.analogGaugeMin = buf.readFloat();
                this.analogGaugeMax = buf.readFloat();
                this.analogZoneYellow = buf.readFloat();
                this.analogZoneRed = buf.readFloat();
                this.analogShowZones = buf.readBoolean();
                this.analogShowGlass = buf.readBoolean();
                this.analogShowRivets = buf.readBoolean();
                this.instrumentStyleFamily = buf.readUtf(64);
                this.instrumentDensityPreset = buf.readUtf(64);
                this.instrumentEffectPreset = buf.readUtf(64);
                this.instrumentStyleOptions = InstrumentStyleOptions.decode(buf);
                this.topologyShowLabels = buf.readBoolean();
                this.topologyAnimateEdges = buf.readBoolean();
                this.topologyColorByStress = buf.readBoolean();
                this.topologyMaxNodes = buf.readVarInt();
                this.trainMapQualityOrdinal = buf.readVarInt();
                this.autoScale = buf.readBoolean();
                this.battleBoardPresetOrdinal = buf.readVarInt();
                this.customScreenLayoutEnabled = buf.readBoolean();
                if (buf.readBoolean()) {
                    this.customScreenStateTag = buf.readNbt();
                }
                break;
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeEnum((Enum)this.configType);
        switch (this.configType) {
            case SCAN_TOGGLE: {
                buf.writeBoolean(this.scanEnabled);
                break;
            }
            case ALERT_CONFIG: {
                buf.writeUtf(this.channelId, 256);
                buf.writeFloat(this.alertThreshold);
                buf.writeBoolean(this.alertAbove);
                buf.writeBoolean(this.alertEnabled);
                break;
            }
            case REDSTONE_CONFIG: {
                buf.writeUtf(this.redstoneChannelId, 256);
                buf.writeFloat(this.redstoneThreshold);
                buf.writeBoolean(this.redstoneAbove);
                buf.writeBoolean(this.redstoneEnabled);
                buf.writeVarInt(this.redstoneMode);
                break;
            }
            case THEME_CHANGE: {
                buf.writeVarInt(this.themeOrdinal);
                break;
            }
            case PREDICTION_TOGGLE: {
                buf.writeBoolean(this.predictionEnabled);
                break;
            }
            case SETTINGS_CHANGE: {
                buf.writeVarInt(this.graphTypeOrdinal);
                buf.writeVarInt(this.colorSchemeOrdinal);
                buf.writeVarInt(this.timeRangeOrdinal);
                buf.writeVarInt(this.themeOrdinal);
                buf.writeBoolean(this.showGrid);
                buf.writeBoolean(this.showAxisLabels);
                buf.writeBoolean(this.showMinMaxAvg);
                buf.writeBoolean(this.doubleSided);
                buf.writeBoolean(this.showValueOverlay);
                buf.writeVarInt(this.cycleInterval);
                buf.writeVarInt(this.hiddenChannelIds.size());
                for (String id : this.hiddenChannelIds) {
                    buf.writeUtf(id, 256);
                }
                buf.writeInt(this.customAccentColor);
                buf.writeFloat(this.predictionRatio);
                buf.writeBoolean(this.predictionEnabled);
                buf.writeVarInt(this.displayModeOrdinal);
                buf.writeBoolean(this.aeroFlightDeckEnabled);
                buf.writeBoolean(this.aerolineDirectorEnabled);
                buf.writeUtf(this.dashboardMainChannelId, 256);
                buf.writeUtf(this.preferredSourceId, 256);
                buf.writeVarInt(this.vehicleNoseOrdinal);
                buf.writeVarInt(this.vehiclePresetOrdinal);
                buf.writeBoolean(this.dashboardLayoutLocked);
                buf.writeVarInt(this.clockFormat);
                buf.writeFloat(this.analogGaugeMin);
                buf.writeFloat(this.analogGaugeMax);
                buf.writeFloat(this.analogZoneYellow);
                buf.writeFloat(this.analogZoneRed);
                buf.writeBoolean(this.analogShowZones);
                buf.writeBoolean(this.analogShowGlass);
                buf.writeBoolean(this.analogShowRivets);
                buf.writeUtf(this.instrumentStyleFamily, 64);
                buf.writeUtf(this.instrumentDensityPreset, 64);
                buf.writeUtf(this.instrumentEffectPreset, 64);
                this.instrumentStyleOptions.encode(buf);
                buf.writeBoolean(this.topologyShowLabels);
                buf.writeBoolean(this.topologyAnimateEdges);
                buf.writeBoolean(this.topologyColorByStress);
                buf.writeVarInt(this.topologyMaxNodes);
                buf.writeVarInt(this.trainMapQualityOrdinal);
                buf.writeBoolean(this.autoScale);
                buf.writeVarInt(this.battleBoardPresetOrdinal);
                buf.writeBoolean(this.customScreenLayoutEnabled);
                buf.writeBoolean(this.customScreenStateTag != null);
                if (this.customScreenStateTag != null) {
                    buf.writeNbt(this.customScreenStateTag);
                }
                break;
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    final StatsDisplayPanelBE target = panel.getControllerBE();
                    if (!target.isPlayerInInteractionRange(player)) {
                        return;
                    }
                    else {
                        boolean changed = false;
                        switch (packet.configType) {
                            case SCAN_TOGGLE: {
                                target.setScanEnabled(packet.scanEnabled);
                                changed = true;
                                break;
                            }
                            case ALERT_CONFIG: {
                                final StatsDisplayPanelBE.DataChannel ch = target.getChannel(packet.channelId);
                                if (ch != null) {
                                    ch.setAlertThreshold(packet.alertThreshold);
                                    ch.setAlertAbove(packet.alertAbove);
                                    ch.setAlertEnabled(packet.alertEnabled);
                                    changed = true;
                                }
                                break;
                            }
                            case REDSTONE_CONFIG: {
                                target.setRedstoneChannelId(packet.redstoneChannelId);
                                target.setRedstoneThreshold(packet.redstoneThreshold);
                                target.setRedstoneAbove(packet.redstoneAbove);
                                target.setRedstoneEnabled(packet.redstoneEnabled);
                                target.setRedstoneMode(packet.redstoneMode);
                                changed = true;
                                break;
                            }
                            case THEME_CHANGE: {
                                final DisplayTheme[] themes = DisplayTheme.values();
                                if (packet.themeOrdinal >= 0 && packet.themeOrdinal < themes.length) {
                                    target.getSettings().setDisplayTheme(themes[packet.themeOrdinal]);
                                    changed = true;
                                }
                                break;
                            }
                            case PREDICTION_TOGGLE: {
                                target.getSettings().setPredictionEnabled(packet.predictionEnabled);
                                changed = true;
                                break;
                            }
                            case SETTINGS_CHANGE: {
                                final StatsDisplaySettings s = target.getSettings();
                                final StatsDisplaySettings.GraphType[] gts = StatsDisplaySettings.GraphType.values();
                                final StatsColorScheme[] css = StatsColorScheme.values();
                                final StatsDisplaySettings.TimeRange[] trs = StatsDisplaySettings.TimeRange.values();
                                final DisplayTheme[] themes2 = DisplayTheme.values();
                                final StatsDisplaySettings.DisplayMode[] dms = StatsDisplaySettings.DisplayMode.values();
                                if (packet.graphTypeOrdinal >= 0 && packet.graphTypeOrdinal < gts.length) {
                                    s.setGraphType(gts[packet.graphTypeOrdinal]);
                                }
                                if (packet.colorSchemeOrdinal >= 0 && packet.colorSchemeOrdinal < css.length) {
                                    s.setColorScheme(css[packet.colorSchemeOrdinal]);
                                }
                                if (packet.timeRangeOrdinal >= 0 && packet.timeRangeOrdinal < trs.length) {
                                    s.setTimeRange(trs[packet.timeRangeOrdinal]);
                                }
                                if (packet.themeOrdinal >= 0 && packet.themeOrdinal < themes2.length) {
                                    s.setDisplayTheme(themes2[packet.themeOrdinal]);
                                }
                                s.setShowGrid(packet.showGrid);
                                s.setShowAxisLabels(packet.showAxisLabels);
                                s.setShowMinMaxAvg(packet.showMinMaxAvg);
                                s.setDoubleSided(packet.doubleSided);
                                s.setShowValueOverlay(packet.showValueOverlay);
                                 s.setCycleInterval(packet.cycleInterval);
                                 s.setHiddenChannelIds(packet.hiddenChannelIds);
                                 s.setCustomAccentColor(packet.customAccentColor);
                                 s.setPredictionRatio(packet.predictionRatio);
                                 s.setPredictionEnabled(packet.predictionEnabled);
                                 if (packet.displayModeOrdinal >= 0 && packet.displayModeOrdinal < dms.length) {
                                     s.setDisplayMode(dms[packet.displayModeOrdinal]);
                                 }
                                 s.setAeroFlightDeckEnabled(packet.aeroFlightDeckEnabled);
                                 s.setAerolineDirectorEnabled(packet.aerolineDirectorEnabled);
                                 s.setDashboardMainChannelId(packet.dashboardMainChannelId);
                                 s.setPreferredSourceId(packet.preferredSourceId);
                                 final StatsDisplaySettings.NoseSetting[] noses = StatsDisplaySettings.NoseSetting.values();
                                 if (packet.vehicleNoseOrdinal >= 0 && packet.vehicleNoseOrdinal < noses.length) {
                                     s.setNoseSetting(noses[packet.vehicleNoseOrdinal]);
                                 }
                                 final StatsDisplaySettings.VehiclePreset[] presets = StatsDisplaySettings.VehiclePreset.values();
                                 if (packet.vehiclePresetOrdinal >= 0 && packet.vehiclePresetOrdinal < presets.length) {
                                     s.setVehiclePreset(presets[packet.vehiclePresetOrdinal]);
                                 }
                                 s.setDashboardLayoutLocked(packet.dashboardLayoutLocked);
                                s.setClockFormat(packet.clockFormat);
                                s.setAnalogGaugeMin(packet.analogGaugeMin);
                                s.setAnalogGaugeMax(packet.analogGaugeMax);
                                s.setAnalogZoneYellow(packet.analogZoneYellow);
                                s.setAnalogZoneRed(packet.analogZoneRed);
                                s.setAnalogShowZones(packet.analogShowZones);
                                s.setAnalogShowGlass(packet.analogShowGlass);
                                s.setAnalogShowRivets(packet.analogShowRivets);
                                if (!packet.instrumentStyleFamily.isEmpty()) {
                                    try {
                                        s.setInstrumentStyleFamily(InstrumentStyleFamily.valueOf(packet.instrumentStyleFamily));
                                    }
                                    catch (IllegalArgumentException ex) {}
                                }
                                if (!packet.instrumentDensityPreset.isEmpty()) {
                                    try {
                                        s.setInstrumentDensityPreset(InstrumentDensityPreset.valueOf(packet.instrumentDensityPreset));
                                    }
                                    catch (IllegalArgumentException ex2) {}
                                }
                                if (!packet.instrumentEffectPreset.isEmpty()) {
                                    try {
                                        s.setInstrumentEffectPreset(InstrumentEffectPreset.valueOf(packet.instrumentEffectPreset));
                                    }
                                    catch (IllegalArgumentException ex3) {}
                                }
                                s.setInstrumentStyleOptions(packet.instrumentStyleOptions);
                                s.setTopologyShowLabels(packet.topologyShowLabels);
                                 s.setTopologyAnimateEdges(packet.topologyAnimateEdges);
                                 s.setTopologyColorByStress(packet.topologyColorByStress);
                                 s.setTopologyMaxNodes(packet.topologyMaxNodes);
                                 final StatsDisplaySettings.TrainMapRenderQuality[] trainQualities = StatsDisplaySettings.TrainMapRenderQuality.values();
                                 if (packet.trainMapQualityOrdinal >= 0 && packet.trainMapQualityOrdinal < trainQualities.length) {
                                     s.setTrainMapRenderQuality(trainQualities[packet.trainMapQualityOrdinal]);
                                 }
                                 s.setAutoScale(packet.autoScale);
                                 final StatsDisplaySettings.BattleBoardPreset[] bbPresets = StatsDisplaySettings.BattleBoardPreset.values();
                                 if (packet.battleBoardPresetOrdinal >= 0 && packet.battleBoardPresetOrdinal < bbPresets.length) {
                                     s.setBattleBoardPreset(bbPresets[packet.battleBoardPresetOrdinal]);
                                 }
                                 s.loadCustomScreenState(packet.customScreenStateTag);
                                 s.setCustomScreenLayoutEnabled(packet.customScreenLayoutEnabled);
                                 target.normalizeSettingsState();
                                 target.refreshDerivedDataAfterSettingsChange();
                                  changed = true;
                                  break;
                              }
                          }
                        if (changed) {
                            target.markDirtyAndSync();
                        }
                        return;
                    }
                }
                else {
                    return;
                }
            }
        });
    }

    public enum ConfigType
    {
        SCAN_TOGGLE,
        ALERT_CONFIG,
        REDSTONE_CONFIG,
        THEME_CHANGE,
        SETTINGS_CHANGE,
        PREDICTION_TOGGLE;
    }
}
