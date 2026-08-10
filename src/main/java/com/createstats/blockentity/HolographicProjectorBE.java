package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.data.TimeSeriesStore;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.createstats.renderer.ProjectorTextureManager;
import net.minecraft.world.level.block.state.properties.Property;
import com.createstats.block.HolographicProjectorBlock;
import com.createstats.data.MetricType;
import com.createstats.util.StockpileSwitchMetrics;
import java.util.Map;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.createstats.display.HologramSettings;
import java.util.LinkedHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;

public class HolographicProjectorBE extends BlockEntity implements IAutoDataReceiver, RangeAttachment, com.createstats.power.IFeConsumer
{
    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_HOLOGRAPHIC_PROJECTOR));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    private final LinkedHashMap<String, DataChannel> channels;
    private final LinkedHashMap<String, SourceConfig> sourceRegistry;
    private int nextAutoSourceIndex;
    private String sourceType;
    private boolean needsSync;
    private HologramSettings hologramSettings;
    private float dataArrivalPulse;
    private boolean hasData;
    private int cachedRedstonePower;
    private boolean redstoneEnabled;
    private String redstoneChannelId;
    private float redstoneThreshold;
    private boolean redstoneAbove;
    private int redstoneMode;
    private boolean scanEnabled;
    private int scanCooldown;
    private static final int SCAN_INTERVAL = 100;
    private static final int SCAN_RADIUS = 5;
    @org.jetbrains.annotations.Nullable
    private UUID ownerUUID;
    private AntennaTier antennaTier;
    
    public HolographicProjectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType)type, pos, state);
        this.channels = new LinkedHashMap<String, DataChannel>();
        this.sourceRegistry = new LinkedHashMap<String, SourceConfig>();
        this.nextAutoSourceIndex = 0;
        this.sourceType = "";
        this.needsSync = false;
        this.hologramSettings = new HologramSettings();
        this.dataArrivalPulse = 0.0f;
        this.hasData = false;
        this.cachedRedstonePower = 0;
        this.redstoneEnabled = false;
        this.redstoneChannelId = "";
        this.redstoneThreshold = 0.0f;
        this.redstoneAbove = true;
        this.redstoneMode = 0;
        this.scanEnabled = false;
        this.scanCooldown = 0;
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
    
    public Map<String, DataChannel> getAllChannels() {
        return this.channels;
    }
    
    @Nullable
    public DataChannel getCurrentDisplayChannel() {
        for (DataChannel ch : this.channels.values()) {
            if (ch.getStore().getSampleCount() > 0) {
                return ch;
            }
        }
        return null;
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
    
    public void onAutoData(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        this.onAutoDataInternal(sourcePos, sourceType, metricId, label, value, type, false);
    }

    public void onAutoDataExact(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        this.onAutoDataInternal(sourcePos, sourceType, metricId, label, value, type, true);
    }

    private void onAutoDataInternal(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type, boolean exactSourceType) {
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
        final long gameTick = (this.level != null) ? this.level.getGameTime() : 0L;
        ch.getStore().addSample(value, gameTick);
        ch.lastText = type.formatValue(value);
        this.dataArrivalPulse = 1.0f;
        this.needsSync = true;
        this.setChanged();
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
    
    public void onReceiveData(BlockPos sourcePos, String sourceType, int line, float value, MetricType detectedType, String rawText) {
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
        this.dataArrivalPulse = 1.0f;
        this.needsSync = true;
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
    
    public boolean isScanEnabled() {
        return this.scanEnabled;
    }
    
    public void setScanEnabled(boolean enabled) {
        this.scanEnabled = enabled;
        this.scanCooldown = 0;
        this.needsSync = true;
        this.setChanged();
    }

    @org.jetbrains.annotations.Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(@org.jetbrains.annotations.Nullable UUID uuid) {
        this.ownerUUID = uuid;
        this.setChanged();
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
    
    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // FE Dependent Mode gate: a dead projector collapses its hologram (ACTIVE off) and
        // drops any redstone output until the buffer is charged again.
        final boolean fePowered = this.feConsumer.serviceTick(this.hasData);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            this.applyUnpoweredState();
            return;
        }
        if (this.dataArrivalPulse > 0.0f) {
            this.dataArrivalPulse = Math.max(0.0f, this.dataArrivalPulse - 0.1f);
        }
        final boolean currentlyHasData = !this.getActiveChannels().isEmpty();
        if (currentlyHasData != this.hasData) {
            this.hasData = currentlyHasData;
            final BlockState state = this.getBlockState();
            if (state.hasProperty(HolographicProjectorBlock.ACTIVE) && (boolean)state.getValue(HolographicProjectorBlock.ACTIVE) != this.hasData) {
                this.level.setBlock(this.worldPosition, (BlockState)state.setValue(HolographicProjectorBlock.ACTIVE, this.hasData), 3);
            }
            this.needsSync = true;
        }
        if (this.needsSync) {
            this.needsSync = false;
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        if (this.redstoneEnabled) {
            final int power = this.calculateRedstonePower();
            if (power != this.cachedRedstonePower) {
                this.cachedRedstonePower = power;
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
        }
        if (this.scanEnabled && --this.scanCooldown <= 0) {
            this.scanCooldown = 100;
            // FE burst: one nearby-block scan. Skipped (and retried next cooldown) if unaffordable.
            if (this.feConsumer.spendBurst()) {
                this.scanNearbyBlocks();
            }
        }
        if (!this.sourceRegistry.isEmpty()) {
            final long currentTick = this.level.getGameTime();
            if (currentTick % 100L == 0L) {
                for (SourceConfig config : this.sourceRegistry.values()) {
                    if (config.isActive() && currentTick - config.getLastDataTick() > 600L) {
                        config.setActive(false);
                        this.needsSync = true;
                        this.setChanged();
                    }
                }
            }
        }
    }
    
    /** Hard cutoff visual: hologram implodes (ACTIVE false), redstone drops, one sync. */
    private void applyUnpoweredState() {
        if (!this.hasData && this.cachedRedstonePower == 0) {
            return;
        }
        this.hasData = false;
        final BlockState state = this.getBlockState();
        if (state.hasProperty(HolographicProjectorBlock.ACTIVE) && (boolean) state.getValue(HolographicProjectorBlock.ACTIVE)) {
            this.level.setBlock(this.worldPosition, (BlockState) state.setValue(HolographicProjectorBlock.ACTIVE, false), 3);
        }
        if (this.cachedRedstonePower != 0) {
            this.cachedRedstonePower = 0;
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public void onRemoved() {
    }
    
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide()) {
            ProjectorTextureManager.cleanup(this.worldPosition);
        }
    }
    
    private void scanNearbyBlocks() {
        if (this.level == null) {
            return;
        }
        final int radius = AntennaRange.cappedScanRadius(SCAN_RADIUS, this.antennaTier, 12);
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-radius, -radius, -radius), this.worldPosition.offset(radius, radius, radius))) {
            final BlockEntity be = this.level.getBlockEntity(pos);
            if (be != null) {
                if (be == this) {
                    continue;
                }
                this.autoReadFromNearby(pos.immutable(), be);
            }
        }
    }
    
    private void autoReadFromNearby(BlockPos sourcePos, BlockEntity sourceBE) {
        try {
            final String className = sourceBE.getClass().getSimpleName();
            if (className.contains("StressGauge") && sourceBE instanceof StressGaugeBlockEntity) {
                final StressGaugeBlockEntity gauge = (StressGaugeBlockEntity)sourceBE;
                this.setSourceType("Stressometer");
                this.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT);
                final float networkStress = gauge.getNetworkStress();
                this.onAutoData(sourcePos, "Stressometer", "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
                final float networkCapacity = gauge.getNetworkCapacity();
                this.onAutoData(sourcePos, "Stressometer", "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
                this.onAutoData(sourcePos, "Stressometer", "remaining_su", "Remaining (SU)", Math.max(0.0f, networkCapacity - networkStress), MetricType.STRESS_SU);
                this.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", Math.abs(gauge.getSpeed()), MetricType.RPM);
                this.onAutoData(sourcePos, "Stressometer", "overstressed", "Overstressed", gauge.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            }
            else if (className.contains("SpeedGauge") && sourceBE instanceof SpeedGaugeBlockEntity) {
                final SpeedGaugeBlockEntity speed = (SpeedGaugeBlockEntity)sourceBE;
                this.setSourceType("Speedometer");
                this.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", speed.getSpeed(), MetricType.RPM);
                this.onAutoData(sourcePos, "Speedometer", "abs_rpm", "Speed (abs)", Math.abs(speed.getSpeed()), MetricType.RPM);
                this.onAutoData(sourcePos, "Speedometer", "speed_pct", "Speed %", speed.dialTarget * 100.0f, MetricType.GENERIC);
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
                this.onAutoData(sourcePos, "Stockpile Switch", "powered", "Signal Output", tsw.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            }
            else if (className.contains("FluidTank") && sourceBE instanceof FluidTankBlockEntity) {
                final FluidTankBlockEntity tank = (FluidTankBlockEntity)sourceBE;
                final String srcType = "Fluid Tank";
                this.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
                final FluidTank tankInv = tank.getTankInventory();
                if (tankInv != null) {
                    this.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", (float)tankInv.getFluidAmount(), MetricType.FLUID_MB);
                    this.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", (float)tankInv.getCapacity(), MetricType.FLUID_MB);
                }
                this.setSourceType("Fluid Tank");
            }
        }
        catch (Exception ex) {}
    }
    
    private int calculateRedstonePower() {
        if (this.redstoneChannelId == null || this.redstoneChannelId.isEmpty()) {
            return 0;
        }
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
    
    public void setRedstoneEnabled(boolean e) {
        this.redstoneEnabled = e;
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
    
    public void setRedstoneAbove(boolean a) {
        this.redstoneAbove = a;
        this.needsSync = true;
        this.setChanged();
    }
    
    public int getRedstoneMode() {
        return this.redstoneMode;
    }
    
    public void setRedstoneMode(int m) {
        this.redstoneMode = m;
        this.needsSync = true;
        this.setChanged();
    }
    
    public int getRedstonePower() {
        return this.cachedRedstonePower;
    }
    
    public HologramSettings getHologramSettings() {
        return this.hologramSettings;
    }
    
    public void setHologramSettings(HologramSettings settings) {
        this.hologramSettings = settings;
        this.needsSync = true;
        this.setChanged();
    }
    
    public boolean hasData() {
        return this.hasData;
    }
    
    public float getDataArrivalPulse() {
        return this.dataArrivalPulse;
    }
    
    public AABB getRenderBoundingBox() {
        final int w = this.hologramSettings.getHologramWidth();
        final int h = this.hologramSettings.getHologramHeight();
        final float half = Math.max(w, h) / 2.0f + 1.0f;
        return new AABB(this.worldPosition).expandTowards(0.0, (double)(h + 2), 0.0).inflate((double)half);
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
        tag.putInt("NextAutoSourceIdx", this.nextAutoSourceIndex);
        tag.putBoolean("StockpileMetricsV2", true);
        tag.put("HologramSettings", (Tag)this.hologramSettings.save());
        tag.putFloat("DataPulse", this.dataArrivalPulse);
        tag.putBoolean("HasData", this.hasData);
        tag.putString("SourceType", this.sourceType);
        tag.putBoolean("RedstoneEnabled", this.redstoneEnabled);
        tag.putString("RedstoneChannel", this.redstoneChannelId);
        tag.putFloat("RedstoneThreshold", this.redstoneThreshold);
        tag.putBoolean("RedstoneAbove", this.redstoneAbove);
        tag.putInt("RedstoneMode", this.redstoneMode);
        tag.putBoolean("ScanEnabled", this.scanEnabled);
        tag.putString("AntennaTier", this.antennaTier.name());
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
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
        tag.putInt("NextAutoSourceIdx", this.nextAutoSourceIndex);
        tag.putBoolean("StockpileMetricsV2", true);
        tag.put("HologramSettings", (Tag)this.hologramSettings.save());
        tag.putFloat("DataPulse", this.dataArrivalPulse);
        tag.putBoolean("HasData", this.hasData);
        tag.putString("SourceType", this.sourceType);
        tag.putBoolean("RedstoneEnabled", this.redstoneEnabled);
        tag.putString("RedstoneChannel", this.redstoneChannelId);
        tag.putFloat("RedstoneThreshold", this.redstoneThreshold);
        tag.putBoolean("RedstoneAbove", this.redstoneAbove);
        tag.putInt("RedstoneMode", this.redstoneMode);
        tag.putBoolean("ScanEnabled", this.scanEnabled);
        tag.putString("AntennaTier", this.antennaTier.name());
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
    }
    
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        this.sourceRegistry.clear();
        if (tag.contains("SourceRegistry")) {
            final CompoundTag srcReg = tag.getCompound("SourceRegistry");
            for (String key : srcReg.getAllKeys()) {
                this.sourceRegistry.put(key, SourceConfig.load(srcReg.getCompound(key)));
            }
        }
        this.nextAutoSourceIndex = tag.getInt("NextAutoSourceIdx");
        if (tag.contains("HologramSettings")) {
            this.hologramSettings = HologramSettings.load(tag.getCompound("HologramSettings"));
        }
        this.dataArrivalPulse = tag.getFloat("DataPulse");
        // Derive hasData from actual loaded channels rather than the persisted flag so that
        // stale saves (e.g. after a crash) cannot leave the projector in a dishonest state.
        this.hasData = !this.getActiveChannels().isEmpty();
        this.sourceType = tag.getString("SourceType");
        this.redstoneEnabled = tag.getBoolean("RedstoneEnabled");
        this.redstoneChannelId = tag.getString("RedstoneChannel");
        this.redstoneThreshold = tag.getFloat("RedstoneThreshold");
        this.redstoneAbove = (!tag.contains("RedstoneAbove") || tag.getBoolean("RedstoneAbove"));
        this.redstoneMode = tag.getInt("RedstoneMode");
        this.scanEnabled = tag.getBoolean("ScanEnabled");
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        this.ownerUUID = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        // Warm the redstone cache immediately so that connected circuits see the correct
        // power level as soon as the chunk loads, not only after the first tick.
        if (this.redstoneEnabled) {
            this.cachedRedstonePower = this.calculateRedstonePower();
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
    }
    
    public void loadChannelData(CompoundTag tag) {
        if (tag.contains("Channels", 9)) {
            this.channels.clear();
            final ListTag channelList = tag.getList("Channels", 10);
            for (int i = 0; i < channelList.size(); ++i) {
                final DataChannel ch = DataChannel.load(channelList.getCompound(i));
                this.channels.put(ch.getId(), ch);
            }
        }
        if (tag.contains("SourceRegistry")) {
            this.sourceRegistry.clear();
            final CompoundTag srcReg = tag.getCompound("SourceRegistry");
            for (String key : srcReg.getAllKeys()) {
                this.sourceRegistry.put(key, SourceConfig.load(srcReg.getCompound(key)));
            }
        }
        this.hasData = !this.getActiveChannels().isEmpty();
    }
    
    public static class DataChannel
    {
        private final String id;
        private String label;
        private TimeSeriesStore store;
        private String lastText;
        
        public DataChannel(String id, String label) {
            this.lastText = "";
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
        
        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Label", this.label);
            tag.put("Store", (Tag)this.store.save());
            tag.putString("LastText", this.lastText);
            return tag;
        }

        public CompoundTag savePreview(int maxSamples) {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Label", this.label);
            tag.put("Store", (Tag)this.store.savePreview(maxSamples));
            tag.putString("LastText", this.lastText);
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
            return ch;
        }
    }
}
