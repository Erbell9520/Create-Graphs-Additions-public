package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.ModConfiguration;
import com.createstats.data.MemoryCogData;
import com.createstats.data.MemoryCogData.SnapshotMetadata;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.registry.ModItems;
import com.createstats.util.CsvExporter;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.StatsDisplayChannelHelper;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StatsRecorderBE extends BlockEntity implements RangeAttachment {

    private static final String TAG_SOURCE_MODE = "SourceMode";
    private static final String TAG_SOURCE_LABEL = "SourceLabel";
    private static final String TAG_SOURCE_TYPE = "SourceType";
    private static final String TAG_SOURCE_POS = "SourcePos";
    private static final String TAG_BOUND_PANEL_POS = "BoundPanelPos";
    private static final String TAG_BOUND_CHANNEL_ID = "BoundChannelId";
    private static final String TAG_LAST_INPUT_TICK = "LastInputTick";
    private static final String TAG_LAST_LINK_INPUT_TICK = "LastLinkInputTick";
    private static final String TAG_LAST_PANEL_SAMPLE_TICK = "LastPanelSampleTick";
    private static final int DISPLAY_LINK_PRIORITY_TICKS = 40;
    private static final int NEARBY_PANEL_SCAN_INTERVAL_TICKS = 10;
    private static final int NEARBY_PANEL_RANGE = 8;

    private TimeSeriesStore store;
    private String lastDisplayText;
    private boolean needsSync;
    private int autoExportIntervalTicks;
    private int ticksSinceLastExport;
    private boolean isRecording;
    private int cachedRedstonePower;
    private RecorderSourceMode sourceMode;
    private String sourceLabel;
    private String sourceType;
    @Nullable
    private BlockPos sourcePos;
    @Nullable
    private BlockPos boundPanelPos;
    private String boundChannelId;
    private long lastInputTick;
    private long lastLinkInputTick;
    private long lastMirroredPanelSampleTick;
    private AntennaTier antennaTier;

    public StatsRecorderBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.lastDisplayText = "";
        this.ticksSinceLastExport = 0;
        this.isRecording = true;
        this.cachedRedstonePower = 0;
        this.sourceMode = RecorderSourceMode.NO_SOURCE;
        this.sourceLabel = "";
        this.sourceType = "";
        this.boundChannelId = "";
        this.store = TimeSeriesStore.forRecorder();
        this.antennaTier = AntennaTier.NONE;
        this.refreshAutoExportSettings();
    }

    public void onReceiveData(float value, MetricType detectedType, String rawText) {
        String label = !rawText.isEmpty() ? rawText : detectedType.getDisplayName();
        this.recordIncomingSample(value, detectedType, rawText, label, this.sourceType, this.sourcePos, RecorderSourceMode.DISPLAY_LINK_TEXT);
    }

    public void onReceiveAutoData(BlockPos sourcePos, String sourceType, InstrumentSourceHelper.SourceReadout readout) {
        InstrumentSourceHelper.MetricSample sample = this.choosePrimaryMetric(readout);
        if (sample == null) {
            this.markDisplayLinkSource(sourceType, sourcePos, sourceType);
            return;
        }
        this.recordIncomingSample(sample.value(), sample.type(), sample.label(),
            this.buildSourceLabel(sourceType, sample.label()), sourceType, sourcePos, RecorderSourceMode.DISPLAY_LINK_STATS_LINK);
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.refreshAutoExportSettings();
        long gameTick = this.level.getGameTime();
        this.tickNearbyPanelCapture(gameTick);

        if (this.needsSync) {
            this.needsSync = false;
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }

        int power = this.isRecording ? this.calculateRedstonePower() : 0;
        if (power != this.cachedRedstonePower) {
            this.cachedRedstonePower = power;
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }

        if (this.isRecording && this.autoExportIntervalTicks > 0 && this.store.getSampleCount() > 0) {
            ++this.ticksSinceLastExport;
            if (this.ticksSinceLastExport >= this.autoExportIntervalTicks) {
                this.ticksSinceLastExport = 0;
                CsvExporter.export(this.store, this.worldPosition);
            }
        }
    }

    public void onPlayerInteract(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            player.displayClientMessage(Component.literal(this.getStatusLine()), true);
            return;
        }
        if (!held.is(ModItems.MEMORY_COG.get())) {
            return;
        }
        if (this.store.getSampleCount() == 0) {
            player.displayClientMessage(Component.translatable("message.create_stats.stats_recorder.no_data").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (MemoryCogData.hasData(held)) {
            player.displayClientMessage(Component.translatable("message.create_stats.stats_recorder.memory_cog_full").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        MemoryCogData.writeToStack(held, this.store, this.createSnapshotMetadata());
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("message.create_stats.stats_recorder.write_success", this.store.getSampleCount()).withStyle(net.minecraft.ChatFormatting.GREEN), true);
        this.setChanged();
    }

    public TimeSeriesStore getStore() {
        return this.store;
    }

    public boolean isRecording() {
        return this.isRecording;
    }

    public void setRecording(boolean recording) {
        if (this.isRecording == recording) {
            return;
        }
        this.isRecording = recording;
        this.ticksSinceLastExport = 0;
        this.setChanged();
        this.needsSync = true;
        if (this.level != null && !this.level.isClientSide()) {
            int power = this.calculateRedstonePower();
            if (power != this.cachedRedstonePower) {
                this.cachedRedstonePower = power;
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getLastDisplayText() {
        return this.lastDisplayText;
    }

    public String getSourceLabel() {
        return this.sourceLabel;
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

    public String getSourceType() {
        return this.sourceType;
    }

    public String getStateDisplayName() {
        return switch (this.getRecorderState(this.level != null ? this.level.getGameTime() : 0L)) {
            case RECORDING -> Component.translatable("block.create_stats.stats_recorder.state.recording").getString();
            case ARMED -> Component.translatable("block.create_stats.stats_recorder.state.armed").getString();
            case PAUSED -> Component.translatable("block.create_stats.stats_recorder.state.paused").getString();
            case NO_SOURCE -> Component.translatable("block.create_stats.stats_recorder.state.no_source").getString();
        };
    }

    public String getSourceSummary() {
        if (!this.sourceLabel.isEmpty()) {
            return this.sourceLabel;
        }
        if (this.boundPanelPos != null) {
            return Component.translatable("block.create_stats.stats_recorder.source.nearby_panel").getString();
        }
        return Component.translatable("block.create_stats.stats_recorder.state.no_source").getString();
    }

    public int getRedstonePower() {
        return this.cachedRedstonePower;
    }

    public int getAnalogOutputSignal() {
        if (this.store.getSampleCount() == 0) {
            return 0;
        }
        float value = this.store.getLatestValue();
        float min = this.store.getMin();
        float max = this.store.getMax();
        float range = max - min;
        if (range <= 0.0f) {
            return value > 0.0f ? 1 : 0;
        }
        return Math.min(15, Math.max(0, Math.round((value - min) / range * 15.0f)));
    }

    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag beTag = new CompoundTag();
        this.saveAdditional(beTag, registries);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put("BlockEntityTag", beTag));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Store", this.store.save());
        tag.putString("LastText", this.lastDisplayText);
        tag.putBoolean("Recording", this.isRecording);
        tag.putInt("CachedRedstonePower", this.cachedRedstonePower);
        tag.putString(TAG_SOURCE_MODE, this.sourceMode.name());
        tag.putString(TAG_SOURCE_LABEL, this.sourceLabel);
        tag.putString(TAG_SOURCE_TYPE, this.sourceType);
        if (this.sourcePos != null) {
            tag.putLong(TAG_SOURCE_POS, this.sourcePos.asLong());
        }
        if (this.boundPanelPos != null) {
            tag.putLong(TAG_BOUND_PANEL_POS, this.boundPanelPos.asLong());
        }
        tag.putString(TAG_BOUND_CHANNEL_ID, this.boundChannelId);
        tag.putLong(TAG_LAST_INPUT_TICK, this.lastInputTick);
        tag.putLong(TAG_LAST_LINK_INPUT_TICK, this.lastLinkInputTick);
        tag.putLong(TAG_LAST_PANEL_SAMPLE_TICK, this.lastMirroredPanelSampleTick);
        tag.putString("AntennaTier", this.antennaTier.name());
    }

    private void saveSyncAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Store", this.store.savePreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES));
        tag.putString("LastText", this.lastDisplayText);
        tag.putBoolean("Recording", this.isRecording);
        tag.putInt("CachedRedstonePower", this.cachedRedstonePower);
        tag.putString(TAG_SOURCE_MODE, this.sourceMode.name());
        tag.putString(TAG_SOURCE_LABEL, this.sourceLabel);
        tag.putString(TAG_SOURCE_TYPE, this.sourceType);
        if (this.sourcePos != null) {
            tag.putLong(TAG_SOURCE_POS, this.sourcePos.asLong());
        }
        if (this.boundPanelPos != null) {
            tag.putLong(TAG_BOUND_PANEL_POS, this.boundPanelPos.asLong());
        }
        tag.putString(TAG_BOUND_CHANNEL_ID, this.boundChannelId);
        tag.putLong(TAG_LAST_INPUT_TICK, this.lastInputTick);
        tag.putLong(TAG_LAST_LINK_INPUT_TICK, this.lastLinkInputTick);
        tag.putLong(TAG_LAST_PANEL_SAMPLE_TICK, this.lastMirroredPanelSampleTick);
        tag.putString("AntennaTier", this.antennaTier.name());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Store")) {
            this.store = TimeSeriesStore.load(tag.getCompound("Store"));
        }
        this.lastDisplayText = tag.getString("LastText");
        this.isRecording = !tag.contains("Recording") || tag.getBoolean("Recording");
        this.cachedRedstonePower = tag.getInt("CachedRedstonePower");
        this.sourceLabel = tag.getString(TAG_SOURCE_LABEL);
        this.sourceType = tag.getString(TAG_SOURCE_TYPE);
        this.sourcePos = tag.contains(TAG_SOURCE_POS) ? BlockPos.of(tag.getLong(TAG_SOURCE_POS)) : null;
        this.boundPanelPos = tag.contains(TAG_BOUND_PANEL_POS) ? BlockPos.of(tag.getLong(TAG_BOUND_PANEL_POS)) : null;
        this.boundChannelId = tag.getString(TAG_BOUND_CHANNEL_ID);
        this.lastInputTick = tag.getLong(TAG_LAST_INPUT_TICK);
        this.lastLinkInputTick = tag.getLong(TAG_LAST_LINK_INPUT_TICK);
        this.lastMirroredPanelSampleTick = tag.getLong(TAG_LAST_PANEL_SAMPLE_TICK);
        this.sourceMode = RecorderSourceMode.byName(tag.getString(TAG_SOURCE_MODE));
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        this.refreshAutoExportSettings();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveSyncAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void refreshAutoExportSettings() {
        boolean enabled = ModConfiguration.COMMON.enableCsvExport.get();
        int intervalMinutes = ModConfiguration.COMMON.csvAutoExportIntervalMinutes.get();
        int intervalTicks = enabled ? Math.max(0, intervalMinutes * 1200) : 0;
        if (intervalTicks != this.autoExportIntervalTicks) {
            this.autoExportIntervalTicks = intervalTicks;
            this.ticksSinceLastExport = 0;
        }
    }

    private void tickNearbyPanelCapture(long gameTick) {
        if (gameTick % NEARBY_PANEL_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }
        if (this.isDisplayLinkPriorityActive(gameTick)) {
            return;
        }

        StatsDisplayPanelBE panel = this.findNearestPanel();
        if (panel == null) {
            if (this.boundPanelPos != null || this.sourceMode == RecorderSourceMode.NEARBY_PANEL) {
                this.boundPanelPos = null;
                this.boundChannelId = "";
                this.lastMirroredPanelSampleTick = 0L;
                if (this.sourceMode == RecorderSourceMode.NEARBY_PANEL) {
                    this.sourceMode = RecorderSourceMode.NO_SOURCE;
                }
                this.sourceLabel = "";
                this.sourceType = "";
                this.sourcePos = null;
                this.needsSync = true;
                this.setChanged();
            }
            return;
        }

        StatsDisplayPanelBE.DataChannel channel = StatsDisplayChannelHelper.selectPrimaryChannel(panel);
        BlockPos controllerPos = panel.getBlockPos();
        String channelId = channel != null ? channel.getId() : "";
        if (!controllerPos.equals(this.boundPanelPos) || !channelId.equals(this.boundChannelId)) {
            this.boundPanelPos = controllerPos;
            this.boundChannelId = channelId;
            this.needsSync = true;
            this.setChanged();
        }

        if (channel == null || channel.getStore().getSampleCount() == 0) {
            String waitingLabel = Component.translatable("block.create_stats.stats_recorder.source.nearby_panel_waiting").getString();
            if (this.sourceMode != RecorderSourceMode.NEARBY_PANEL
                || !waitingLabel.equals(this.sourceLabel)
                || !panel.getSourceType().equals(this.sourceType)
                || !controllerPos.equals(this.sourcePos)) {
                this.sourceMode = RecorderSourceMode.NEARBY_PANEL;
                this.sourceLabel = waitingLabel;
                this.sourceType = panel.getSourceType();
                this.sourcePos = controllerPos;
                this.needsSync = true;
                this.setChanged();
            }
            return;
        }

        TimeSeriesStore panelStore = channel.getStore();
        long panelSampleTick = panelStore.getLastSampleTick();
        if (panelSampleTick <= 0L || panelSampleTick == this.lastMirroredPanelSampleTick) {
            return;
        }

        this.lastMirroredPanelSampleTick = panelSampleTick;
        String rawText = channel.getLastText().isEmpty() ? channel.getLabel() : channel.getLastText();
        this.recordIncomingSample(panelStore.getLatestValue(), panelStore.getMetricType(), rawText, channel.getLabel(),
            panel.getSourceType(), controllerPos, RecorderSourceMode.NEARBY_PANEL);
    }

    @Nullable
    private StatsDisplayPanelBE findNearestPanel() {
        if (this.level == null) {
            return null;
        }
        StatsDisplayPanelBE nearest = null;
        double bestDistance = Double.MAX_VALUE;
        Set<BlockPos> seenControllers = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int range = AntennaRange.cappedScanRadius(NEARBY_PANEL_RANGE, this.antennaTier, 16);
        for (int dx = -range; dx <= range; ++dx) {
            for (int dy = -range; dy <= range; ++dy) {
                for (int dz = -range; dz <= range; ++dz) {
                    cursor.set(this.worldPosition.getX() + dx, this.worldPosition.getY() + dy, this.worldPosition.getZ() + dz);
                    BlockEntity blockEntity = this.level.getBlockEntity(cursor);
                    if (!(blockEntity instanceof StatsDisplayPanelBE panel)) {
                        continue;
                    }
                    StatsDisplayPanelBE controller = panel.getControllerBE();
                    if (!seenControllers.add(controller.getBlockPos())) {
                        continue;
                    }
                    double distance = this.getNearestPanelDistanceSqr(controller);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = controller;
                    }
                }
            }
        }
        return nearest;
    }

    private double getNearestPanelDistanceSqr(StatsDisplayPanelBE panel) {
        double centerX = this.worldPosition.getX() + 0.5;
        double centerY = this.worldPosition.getY() + 0.5;
        double centerZ = this.worldPosition.getZ() + 0.5;
        double best = Double.MAX_VALUE;
        for (BlockPos memberPos : panel.getMultiblockMembers()) {
            double dx = centerX - (memberPos.getX() + 0.5);
            double dy = centerY - (memberPos.getY() + 0.5);
            double dz = centerZ - (memberPos.getZ() + 0.5);
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    private boolean isDisplayLinkPriorityActive(long gameTick) {
        return this.sourceMode.isDisplayLink() && gameTick - this.lastLinkInputTick <= DISPLAY_LINK_PRIORITY_TICKS;
    }

    private void recordIncomingSample(float value, MetricType detectedType, String rawText, String sourceLabel, String sourceType,
                                      @Nullable BlockPos sourcePos, RecorderSourceMode mode) {
        if (!this.isRecording) {
            this.sourceMode = mode;
            this.sourceLabel = sourceLabel;
            this.sourceType = sourceType != null ? sourceType : "";
            this.sourcePos = sourcePos;
            this.needsSync = true;
            this.setChanged();
            return;
        }

        if (detectedType != MetricType.GENERIC && this.store.getMetricType() != detectedType) {
            this.store.setMetricType(detectedType);
        }

        long gameTick = this.level != null ? this.level.getGameTime() : 0L;
        this.store.addSample(value, gameTick);
        this.lastDisplayText = rawText;
        this.sourceMode = mode;
        this.sourceLabel = sourceLabel != null ? sourceLabel : "";
        this.sourceType = sourceType != null ? sourceType : "";
        this.sourcePos = sourcePos;
        this.lastInputTick = gameTick;
        if (mode.isDisplayLink()) {
            this.lastLinkInputTick = gameTick;
        }
        this.needsSync = true;
        this.setChanged();
    }

    private void markDisplayLinkSource(String sourceType, @Nullable BlockPos sourcePos, String sourceLabel) {
        this.sourceMode = RecorderSourceMode.DISPLAY_LINK_STATS_LINK;
        this.sourceType = sourceType != null ? sourceType : "";
        this.sourcePos = sourcePos;
        this.sourceLabel = sourceLabel != null ? sourceLabel : "";
        this.lastLinkInputTick = this.level != null ? this.level.getGameTime() : 0L;
        this.needsSync = true;
        this.setChanged();
    }

    @Nullable
    private InstrumentSourceHelper.MetricSample choosePrimaryMetric(InstrumentSourceHelper.SourceReadout readout) {
        if (readout.metrics().isEmpty()) {
            return null;
        }
        String[] preferredIds = switch (readout.sourceKind()) {
            case KINETIC_STRESS -> new String[]{"stress_pct", "stress_su", "remaining_su", "rpm"};
            case KINETIC_SPEED -> new String[]{"rpm", "abs_rpm", "running"};
            case ITEM_STORAGE -> new String[]{"fill_pct", "total_items", "item_capacity"};
            case FLUID_STORAGE -> new String[]{"fill_pct", "fluid_amount", "tank_capacity"};
            case ENERGY_STORAGE -> new String[]{"energy_fill_pct", "energy_stored", "energy_net_rate", "energy_output_rate", "energy_input_rate", "energy_capacity"};
            case POWER_GRID -> new String[]{"energy_fill_pct", "network_pushed", "network_pulled", "network_demand", "energy_shortfall", "network_buffer"};
            case BOILER -> new String[]{"boiler_gauge", "heat_pct", "fluid_amount"};
            default -> new String[]{"value", "fill_pct", "rpm", "stress_pct"};
        };
        for (String preferredId : preferredIds) {
            InstrumentSourceHelper.MetricSample preferred = readout.metrics().get(preferredId);
            if (preferred != null) {
                return preferred;
            }
        }
        return readout.metrics().values().iterator().next();
    }

    private String buildSourceLabel(String sourceType, String metricLabel) {
        if (sourceType == null || sourceType.isEmpty()) {
            return metricLabel;
        }
        if (metricLabel == null || metricLabel.isEmpty() || metricLabel.equals(sourceType)) {
            return sourceType;
        }
        return sourceType + " - " + metricLabel;
    }

    private int calculateRedstonePower() {
        if (!this.isRecording || this.store.getSampleCount() == 0) {
            return 0;
        }
        return 15;
    }

    private String getStatusLine() {
        String exportStatus = this.autoExportIntervalTicks > 0
            ? Component.translatable("block.create_stats.stats_recorder.csv_on", Math.max(1, this.autoExportIntervalTicks / 1200)).getString()
            : Component.translatable("block.create_stats.stats_recorder.csv_off").getString();
        return String.format("%s %s | %d %s | %s | %s",
            Component.translatable("block.create_stats.stats_recorder").getString(),
            this.store.getMetricType().getDisplayName(),
            this.store.getSampleCount(),
            Component.translatable("block.create_stats.stats_recorder.samples").getString(),
            this.getStateDisplayName(),
            exportStatus + " | " + this.getSourceSummary());
    }

    private RecorderState getRecorderState(long gameTick) {
        if (!this.isRecording) {
            return RecorderState.PAUSED;
        }
        if (this.isDisplayLinkPriorityActive(gameTick)) {
            return RecorderState.RECORDING;
        }
        if (this.boundPanelPos != null || this.sourceMode == RecorderSourceMode.NEARBY_PANEL) {
            if (this.lastInputTick > 0L && gameTick - this.lastInputTick <= this.store.getSampleIntervalTicks() * 2L
                && this.sourceMode == RecorderSourceMode.NEARBY_PANEL) {
                return RecorderState.RECORDING;
            }
            return RecorderState.ARMED;
        }
        return RecorderState.NO_SOURCE;
    }

    private SnapshotMetadata createSnapshotMetadata() {
        String dimensionId = "";
        if (this.level != null) {
            ResourceLocation dimension = this.level.dimension().location();
            dimensionId = dimension != null ? dimension.toString() : "";
        }
        return new SnapshotMetadata(1, this.getSourceSummary(), this.sourceType, dimensionId,
            this.sourcePos != null ? this.sourcePos : this.worldPosition,
            this.level != null ? this.level.getGameTime() : 0L, this.lastDisplayText);
    }

    private enum RecorderState {
        NO_SOURCE,
        ARMED,
        RECORDING,
        PAUSED
    }

    private enum RecorderSourceMode {
        NO_SOURCE,
        DISPLAY_LINK_TEXT,
        DISPLAY_LINK_STATS_LINK,
        NEARBY_PANEL;

        public boolean isDisplayLink() {
            return this == DISPLAY_LINK_TEXT || this == DISPLAY_LINK_STATS_LINK;
        }

        public static RecorderSourceMode byName(String name) {
            for (RecorderSourceMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return NO_SOURCE;
        }
    }
}
