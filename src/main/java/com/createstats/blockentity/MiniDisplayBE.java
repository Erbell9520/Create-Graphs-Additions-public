package com.createstats.blockentity;

import com.createstats.block.MiniDisplayBlock;
import com.createstats.data.MetricType;
import com.createstats.display.BattleCompactTargetHandler;
import com.createstats.renderer.instrument.InstrumentRenderPrimitives;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MiniDisplayBE extends BlockEntity implements IAutoDataReceiver {
    private static final long LINK_TIMEOUT_TICKS = 60L;
    private static final long LINK_HOLD_TICKS = 100L;
    private static final float EPSILON = 0.01f;

    private MiniDisplaySettings settings;
    private final Map<String, InstrumentSourceHelper.MetricSample> linkedMetrics;
    @Nullable
    private SourceConfig linkedSource;
    private int nextAutoSourceIndex;
    private long lastLinkedDataTick;

    private DisplayState displayState;
    private InstrumentSourceHelper.SourceKind activeSourceKind;
    @Nullable
    private BlockPos activeSourcePos;
    private String activeSourceType;
    private float activeFillPercent;
    private float activeTotalItems;
    private float activeFluidAmount;
    private float activeFluidCapacity;
    private float activeStressPercent;
    private float activeStressUsed;
    private float activeStressCapacity;
    private float activeRpm;
    private boolean firstDetailPresent;
    private String firstDetailLabel;
    private float firstDetailValue;
    private boolean secondDetailPresent;
    private String secondDetailLabel;
    private float secondDetailValue;

    private boolean postLoadValidated = false;
    private int postLoadRetry = 0;
    private static final int POST_LOAD_RETRY_MAX = 20;

    public MiniDisplayBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new MiniDisplaySettings();
        this.linkedMetrics = new LinkedHashMap<>();
        this.nextAutoSourceIndex = 1;
        this.lastLinkedDataTick = Long.MIN_VALUE;
        this.displayState = DisplayState.NO_SOURCE;
        this.activeSourceKind = InstrumentSourceHelper.SourceKind.NO_SOURCE;
        this.activeSourceType = "";
        this.firstDetailLabel = "";
        this.secondDetailLabel = "";
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!this.postLoadValidated) {
            this.runPostLoadSegmentValidation();
        }
        if (this.getControllerBE() != this) {
            return;
        }
        this.evaluateAndApply(this.level.getGameTime());
    }

    private void runPostLoadSegmentValidation() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof MiniDisplayBlock)) {
            this.postLoadValidated = true;
            return;
        }
        final Direction facing = state.getValue(MiniDisplayBlock.FACING);
        final Direction right = facing.getClockWise();
        final BlockPos leftNeighbor = this.worldPosition.relative(right.getOpposite());
        final BlockPos rightNeighbor = this.worldPosition.relative(right);
        if (!this.level.hasChunkAt(leftNeighbor) || !this.level.hasChunkAt(rightNeighbor)) {
            if (++this.postLoadRetry > POST_LOAD_RETRY_MAX) {
                this.postLoadValidated = true;
            }
            return;
        }
        final MiniDisplayBlock.Segment current = state.getValue(MiniDisplayBlock.SEGMENT);
        final MiniDisplayBlock.Segment correct = MiniDisplayBlock.computeSegment(this.level, this.worldPosition, state);
        if (current != correct) {
            this.level.setBlock(this.worldPosition, state.setValue(MiniDisplayBlock.SEGMENT, correct), 3);
            if (this.level.isClientSide()) {
                com.createstats.renderer.MiniDisplayTextureManager.disposeFor(this.worldPosition);
            }
        }
        this.postLoadValidated = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide()) {
            com.createstats.renderer.MiniDisplayTextureManager.disposeFor(this.worldPosition);
        }
    }

    public MiniDisplaySettings getSettings() {
        return this.settings;
    }

    public void setSettings(MiniDisplaySettings settings) {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            controller.setSettings(settings);
            return;
        }
        this.settings = settings.copy();
        if (this.level != null && !this.level.isClientSide()) {
            if (this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED) {
                this.refreshLinkedSnapshot();
            }
            this.evaluateAndApply(this.level.getGameTime());
            this.syncToClient();
        } else {
            this.setChanged();
        }
    }

    public void replaceAutoData(BlockPos sourcePos, String sourceType, Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            controller.replaceAutoData(sourcePos, sourceType, metrics);
            return;
        }
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.registerOrUpdateSource(sourcePos, sourceType);
        this.linkedMetrics.clear();
        this.linkedMetrics.putAll(metrics);
        this.lastLinkedDataTick = this.level.getGameTime();
        this.setChanged();
    }

    public List<ItemStack> getTrackedFilterItems() {
        return this.settings.getTrackedFilterItems();
    }

    public void inheritStateFrom(MiniDisplayBE other) {
        if (other == this) {
            return;
        }
        this.settings = other.getSettings().copy();
        this.linkedMetrics.clear();
        this.linkedMetrics.putAll(other.linkedMetrics);
        this.linkedSource = other.linkedSource != null ? SourceConfig.load(other.linkedSource.save()) : null;
        this.nextAutoSourceIndex = other.nextAutoSourceIndex;
        this.lastLinkedDataTick = other.lastLinkedDataTick;
        this.displayState = other.displayState;
        this.activeSourceKind = other.activeSourceKind;
        this.activeSourcePos = other.activeSourcePos != null ? other.activeSourcePos.immutable() : null;
        this.activeSourceType = other.activeSourceType;
        this.activeFillPercent = other.activeFillPercent;
        this.activeTotalItems = other.activeTotalItems;
        this.activeFluidAmount = other.activeFluidAmount;
        this.activeFluidCapacity = other.activeFluidCapacity;
        this.activeStressPercent = other.activeStressPercent;
        this.activeStressUsed = other.activeStressUsed;
        this.activeStressCapacity = other.activeStressCapacity;
        this.activeRpm = other.activeRpm;
        this.firstDetailPresent = other.firstDetailPresent;
        this.firstDetailLabel = other.firstDetailLabel;
        this.firstDetailValue = other.firstDetailValue;
        this.secondDetailPresent = other.secondDetailPresent;
        this.secondDetailLabel = other.secondDetailLabel;
        this.secondDetailValue = other.secondDetailValue;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public MiniDisplayBE getControllerBE() {
        if (!(this.getBlockState().getBlock() instanceof MiniDisplayBlock)) {
            return this;
        }
        if (this.getBlockState().getValue(MiniDisplayBlock.SEGMENT) != MiniDisplayBlock.Segment.RIGHT || this.level == null) {
            return this;
        }
        final Direction facing = this.getBlockState().getValue(MiniDisplayBlock.FACING);
        final BlockEntity be = this.level.getBlockEntity(this.worldPosition.relative(facing.getCounterClockWise()));
        if (be instanceof MiniDisplayBE controller && controller.getBlockState().getBlock() instanceof MiniDisplayBlock
                && controller.getBlockState().getValue(MiniDisplayBlock.SEGMENT) == MiniDisplayBlock.Segment.LEFT
                && controller.getBlockState().getValue(MiniDisplayBlock.FACING) == facing) {
            return controller;
        }
        return this;
    }

    public boolean isController() {
        return this.getControllerBE() == this;
    }

    public boolean isExpanded() {
        return this.getDisplayWidth() > 1;
    }

    public int getDisplayWidth() {
        if (!(this.getBlockState().getBlock() instanceof MiniDisplayBlock)) {
            return 1;
        }
        if (this.getBlockState().getValue(MiniDisplayBlock.SEGMENT) == MiniDisplayBlock.Segment.RIGHT) {
            final MiniDisplayBE controller = this.getControllerBE();
            return controller != this ? controller.getDisplayWidth() : 1;
        }
        final BlockPos partnerPos = this.getPartnerPos();
        return partnerPos != null ? 2 : 1;
    }

    public String getFootprintSummary() {
        return this.isExpanded() ? "Joined 2x1" : "Single";
    }

    public String getModeSummary() {
        return this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LOCAL ? "Local readout" : "Linked relay";
    }

    public String getRelaySummary() {
        if (this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LOCAL) {
            return "Direct source";
        }
        return this.displayState == DisplayState.LIVE || this.displayState == DisplayState.LINK_STALE
                ? "Relay active"
                : "Relay waiting";
    }

    public boolean isRelayActive() {
        return this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED
                && (this.displayState == DisplayState.LIVE || this.displayState == DisplayState.LINK_STALE);
    }

    @Nullable
    public BlockPos getPartnerPos() {
        if (!(this.getBlockState().getBlock() instanceof MiniDisplayBlock) || this.level == null) {
            return null;
        }
        final Direction facing = this.getBlockState().getValue(MiniDisplayBlock.FACING);
        final MiniDisplayBlock.Segment segment = this.getBlockState().getValue(MiniDisplayBlock.SEGMENT);
        final BlockPos partnerPos = switch (segment) {
            case LEFT -> this.worldPosition.relative(facing.getClockWise());
            case RIGHT -> this.worldPosition.relative(facing.getCounterClockWise());
            case SINGLE -> null;
        };
        if (partnerPos == null) {
            return null;
        }
        final BlockState partnerState = this.level.getBlockState(partnerPos);
        if (!(partnerState.getBlock() instanceof MiniDisplayBlock) || partnerState.getValue(MiniDisplayBlock.FACING) != facing) {
            return null;
        }
        final MiniDisplayBlock.Segment expected = segment == MiniDisplayBlock.Segment.LEFT
                ? MiniDisplayBlock.Segment.RIGHT
                : MiniDisplayBlock.Segment.LEFT;
        return partnerState.getValue(MiniDisplayBlock.SEGMENT) == expected ? partnerPos : null;
    }

    public DisplayState getDisplayState() {
        return this.displayState;
    }

    public InstrumentSourceHelper.SourceKind getActiveSourceKind() {
        return this.activeSourceKind;
    }

    public String getActiveSourceType() {
        return this.activeSourceType;
    }

    public float getActiveFillPercent() {
        return this.activeFillPercent;
    }

    public float getActiveTotalItems() {
        return this.activeTotalItems;
    }

    public float getActiveFluidAmount() {
        return this.activeFluidAmount;
    }

    public float getActiveFluidCapacity() {
        return this.activeFluidCapacity;
    }

    public float getActiveStressPercent() {
        return this.activeStressPercent;
    }

    public float getActiveStressUsed() {
        return this.activeStressUsed;
    }

    public float getActiveStressCapacity() {
        return this.activeStressCapacity;
    }

    public float getActiveRpm() {
        return this.activeRpm;
    }

    public boolean hasFirstDetailRow() {
        return this.firstDetailPresent;
    }

    public String getFirstDetailLabel() {
        return this.firstDetailLabel;
    }

    public float getFirstDetailValue() {
        return this.firstDetailValue;
    }

    public boolean hasSecondDetailRow() {
        return this.secondDetailPresent;
    }

    public String getSecondDetailLabel() {
        return this.secondDetailLabel;
    }

    public float getSecondDetailValue() {
        return this.secondDetailValue;
    }

    public InstrumentSourceHelper.SourceKind getPreviewSourceKind(MiniDisplaySettings.SourceMode mode) {
        if (mode == MiniDisplaySettings.SourceMode.LOCAL) {
            final LocalPreview preview = this.resolveLocalPreview();
            if (preview == null) {
                return InstrumentSourceHelper.SourceKind.NO_SOURCE;
            }
            return preview.readout().sourceKind();
        }
        if (this.linkedSource == null) {
            return InstrumentSourceHelper.SourceKind.NO_SOURCE;
        }
        return InstrumentSourceHelper.classifyLinkedSource(this.linkedSource.getSourceType(), this.linkedMetrics);
    }

    public String getPreviewSourceSummary(MiniDisplaySettings.SourceMode mode) {
        if (mode == MiniDisplaySettings.SourceMode.LOCAL) {
            final LocalPreview preview = this.resolveLocalPreview();
            if (preview == null) {
                return "No adjacent source";
            }
            return preview.readout().sourceType() + " @ " + preview.binding().pos().toShortString();
        }
        if (this.linkedSource == null) {
            return "No linked source";
        }
        return this.linkedSource.getSourceType() + " @ " + this.linkedSource.getSourcePos().toShortString();
    }

    public String getLiveSummary() {
        return switch (this.displayState) {
            case NO_SOURCE -> this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LOCAL ? "No adjacent source" : "No linked source";
            case UNSUPPORTED -> "Unsupported source";
            case LINK_LOST -> "Linked source timed out";
            case LINK_STALE -> this.describeLiveValue() + " (stale)";
            case LIVE -> this.describeLiveValue();
        };
    }

    private String describeLiveValue() {
        if (BattleCompactTargetHandler.isBattleSource(this.activeSourceType)) {
            final String hull = "Hull " + String.format("%.0f%%", this.activeFillPercent);
            final String alert = "Alert " + BattleCompactTargetHandler.alertLabel(this.activeRpm);
            if (this.activeFluidCapacity > 0.0f) {
                return hull + " | " + alert + " | Ready " + String.format("%.0f/%.0f", this.firstDetailValue, this.activeFluidCapacity);
            }
            return hull + " | " + alert + " | Breach " + String.format("%.0f", this.secondDetailValue);
        }
        return switch (this.activeSourceKind) {
            case ITEM_STORAGE -> "Fill " + String.format("%.1f%%", this.activeFillPercent) + " | " + String.format("%.0f", this.activeTotalItems) + " items";
            case FLUID_STORAGE, BOILER -> "Fill " + String.format("%.1f%%", this.activeFillPercent) + " | " + MetricType.FLUID_MB.formatValue(this.activeFluidAmount);

            case ENERGY_STORAGE, POWER_GRID -> "Energy " + String.format("%.1f%%", this.activeFillPercent) + " | " + MetricType.ENERGY_FE.formatValue(this.activeFluidAmount);
            case KINETIC_STRESS -> "Stress " + String.format("%.1f%%", this.activeStressPercent) + " | "
                    + InstrumentRenderPrimitives.formatCompact(this.activeStressUsed) + " / "
                    + InstrumentRenderPrimitives.formatCompact(this.activeStressCapacity) + " SU";
            case KINETIC_SPEED -> "RPM " + InstrumentRenderPrimitives.formatCompact(Math.abs(this.activeRpm));
            case NO_SOURCE, UNKNOWN -> this.activeSourceType.isEmpty() ? "Waiting for data" : this.activeSourceType;
        };
    }

    private void evaluateAndApply(long gameTime) {
        final Evaluation evaluation = this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LOCAL
                ? this.evaluateLocal()
                : this.evaluateLinked(gameTime);

        final boolean changed = this.displayState != evaluation.displayState()
                || this.activeSourceKind != evaluation.sourceKind()
                || !Objects.equals(this.activeSourcePos, evaluation.sourcePos())
                || !Objects.equals(this.activeSourceType, evaluation.sourceType())
                || Math.abs(this.activeFillPercent - evaluation.fillPercent()) > EPSILON
                || Math.abs(this.activeTotalItems - evaluation.totalItems()) > EPSILON
                || Math.abs(this.activeFluidAmount - evaluation.fluidAmount()) > EPSILON
                || Math.abs(this.activeFluidCapacity - evaluation.fluidCapacity()) > EPSILON
                || Math.abs(this.activeStressPercent - evaluation.stressPercent()) > EPSILON
                || Math.abs(this.activeStressUsed - evaluation.stressUsed()) > EPSILON
                || Math.abs(this.activeStressCapacity - evaluation.stressCapacity()) > EPSILON
                || Math.abs(this.activeRpm - evaluation.rpm()) > EPSILON
                || this.firstDetailPresent != evaluation.firstDetailPresent()
                || this.secondDetailPresent != evaluation.secondDetailPresent()
                || !Objects.equals(this.firstDetailLabel, evaluation.firstDetailLabel())
                || !Objects.equals(this.secondDetailLabel, evaluation.secondDetailLabel())
                || Math.abs(this.firstDetailValue - evaluation.firstDetailValue()) > EPSILON
                || Math.abs(this.secondDetailValue - evaluation.secondDetailValue()) > EPSILON;

        this.displayState = evaluation.displayState();
        this.activeSourceKind = evaluation.sourceKind();
        this.activeSourcePos = evaluation.sourcePos();
        this.activeSourceType = evaluation.sourceType();
        this.activeFillPercent = evaluation.fillPercent();
        this.activeTotalItems = evaluation.totalItems();
        this.activeFluidAmount = evaluation.fluidAmount();
        this.activeFluidCapacity = evaluation.fluidCapacity();
        this.activeStressPercent = evaluation.stressPercent();
        this.activeStressUsed = evaluation.stressUsed();
        this.activeStressCapacity = evaluation.stressCapacity();
        this.activeRpm = evaluation.rpm();
        this.firstDetailPresent = evaluation.firstDetailPresent();
        this.firstDetailLabel = evaluation.firstDetailLabel();
        this.firstDetailValue = evaluation.firstDetailValue();
        this.secondDetailPresent = evaluation.secondDetailPresent();
        this.secondDetailLabel = evaluation.secondDetailLabel();
        this.secondDetailValue = evaluation.secondDetailValue();

        this.updateBlockState();

        if (changed) {
            this.syncToClient();
        } else {
            this.setChanged();
        }
    }

    private Evaluation evaluateLocal() {
        final List<InstrumentSourceHelper.LocalBinding> bindings = this.resolveLocalBindings();
        if (bindings.isEmpty()) {
            return Evaluation.empty(DisplayState.NO_SOURCE, InstrumentSourceHelper.SourceKind.NO_SOURCE, null, "");
        }
        Evaluation firstUnsupported = null;
        for (InstrumentSourceHelper.LocalBinding binding : bindings) {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(
                    binding.blockEntity(),
                    binding.accessFace(),
                    this.settings.getTrackedFilterItems());
            final Evaluation evaluation = this.buildEvaluation(DisplayState.LIVE, readout.sourceKind(), binding.pos(), readout.sourceType(), readout.metrics());
            if (evaluation.hasRenderableData()) {
                return evaluation;
            }
            if (firstUnsupported == null && readout.sourceKind() != InstrumentSourceHelper.SourceKind.NO_SOURCE) {
                firstUnsupported = evaluation;
            }
        }
        return firstUnsupported != null
                ? firstUnsupported
                : Evaluation.empty(DisplayState.NO_SOURCE, InstrumentSourceHelper.SourceKind.NO_SOURCE, null, "");
    }

    private Evaluation evaluateLinked(long gameTime) {
        if (this.linkedSource == null) {
            return Evaluation.empty(DisplayState.NO_SOURCE, InstrumentSourceHelper.SourceKind.NO_SOURCE, null, "");
        }

        final BlockPos sourcePos = this.linkedSource.getSourcePos();
        final String sourceType = this.linkedSource.getSourceType();
        final InstrumentSourceHelper.SourceKind sourceKind = InstrumentSourceHelper.classifyLinkedSource(sourceType, this.linkedMetrics);
        final long age = gameTime - this.lastLinkedDataTick;

        if (!this.linkedMetrics.isEmpty()) {
            if (age <= LINK_TIMEOUT_TICKS) {
                final Evaluation live = this.buildEvaluation(DisplayState.LIVE, sourceKind, sourcePos, sourceType, this.linkedMetrics);
                if (live.hasRenderableData()) {
                    return live;
                }
            } else if (age <= LINK_HOLD_TICKS) {
                final Evaluation stale = this.buildEvaluation(DisplayState.LINK_STALE, sourceKind, sourcePos, sourceType, this.linkedMetrics);
                if (stale.hasRenderableData()) {
                    return stale;
                }
            }
        }

        if (age <= LINK_HOLD_TICKS && this.hasRenderableState()) {
            return new Evaluation(DisplayState.LINK_STALE,
                    this.activeSourceKind != InstrumentSourceHelper.SourceKind.NO_SOURCE ? this.activeSourceKind : sourceKind,
                    this.activeSourcePos != null ? this.activeSourcePos : sourcePos,
                    !this.activeSourceType.isEmpty() ? this.activeSourceType : sourceType,
                    this.activeFillPercent,
                    this.activeTotalItems,
                    this.activeFluidAmount,
                    this.activeFluidCapacity,
                    this.activeStressPercent,
                    this.activeStressUsed,
                    this.activeStressCapacity,
                    this.activeRpm,
                    this.firstDetailPresent,
                    this.firstDetailLabel,
                    this.firstDetailValue,
                    this.secondDetailPresent,
                    this.secondDetailLabel,
                    this.secondDetailValue,
                    true
            );
        }

        return Evaluation.empty(DisplayState.LINK_LOST, sourceKind, sourcePos, sourceType);
    }

    private Evaluation buildEvaluation(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                       @Nullable BlockPos sourcePos, String sourceType,
                                       Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        return switch (sourceKind) {
            case ITEM_STORAGE -> this.evaluateItemStorage(displayState, sourceKind, sourcePos, sourceType, metrics);
            case FLUID_STORAGE, BOILER -> this.evaluateFluidStorage(displayState, sourceKind, sourcePos, sourceType, metrics);
            case ENERGY_STORAGE, POWER_GRID -> this.evaluateEnergyStorage(displayState, sourceKind, sourcePos, sourceType, metrics);
            case KINETIC_STRESS -> this.evaluateKineticStress(displayState, sourceKind, sourcePos, sourceType, metrics);
            case KINETIC_SPEED -> this.evaluateKineticSpeed(displayState, sourceKind, sourcePos, sourceType, metrics);
            case NO_SOURCE -> Evaluation.empty(DisplayState.NO_SOURCE, sourceKind, sourcePos, sourceType);
            case UNKNOWN -> this.isBattleLinkedSource(sourceType, metrics)
                    ? this.evaluateBattle(displayState, sourceKind, sourcePos, sourceType, metrics)
                    : (metrics.isEmpty()
                    ? Evaluation.empty(DisplayState.UNSUPPORTED, sourceKind, sourcePos, sourceType)
                    : Evaluation.empty(displayState, sourceKind, sourcePos, sourceType));
        };
    }

    private boolean isBattleLinkedSource(String sourceType, Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        return BattleCompactTargetHandler.isBattleSource(sourceType) && BattleCompactTargetHandler.hasBattleMetrics(metrics);
    }

    private Evaluation evaluateBattle(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                      @Nullable BlockPos sourcePos, String sourceType,
                                      Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample integrity = this.sample(metrics, BattleCompactTargetHandler.INTEGRITY_METRIC);
        final InstrumentSourceHelper.MetricSample alert = this.sample(metrics, BattleCompactTargetHandler.ALERT_METRIC);
        final InstrumentSourceHelper.MetricSample readiness = this.sample(metrics, BattleCompactTargetHandler.READINESS_METRIC);
        final InstrumentSourceHelper.MetricSample observers = this.sample(metrics, BattleCompactTargetHandler.OBSERVER_COUNT_METRIC);
        final InstrumentSourceHelper.MetricSample weapons = this.sample(metrics, BattleCompactTargetHandler.WEAPON_COUNT_METRIC);
        final InstrumentSourceHelper.MetricSample readyWeapons = this.sample(metrics, BattleCompactTargetHandler.READY_WEAPONS_METRIC);
        final InstrumentSourceHelper.MetricSample breaches = this.sample(metrics, BattleCompactTargetHandler.MISSING_BLOCKS_METRIC);
        final InstrumentSourceHelper.MetricSample rebuild = this.sample(metrics, BattleCompactTargetHandler.REBUILD_BLOCKS_METRIC);
        final boolean hasData = integrity != null || alert != null || observers != null || breaches != null || rebuild != null;
        if (!hasData) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }

        final float weaponCount = weapons != null ? weapons.value() : 0.0f;
        final float readyCount = readyWeapons != null ? readyWeapons.value() : 0.0f;
        final float readinessPercent = readiness != null
                ? readiness.value()
                : (weaponCount > 0.0f ? readyCount * 100.0f / weaponCount : 100.0f);
        final String firstLabel = weaponCount > 0.0f ? "Ready Guns" : "Observers";
        final float firstValue = weaponCount > 0.0f ? readyCount : (observers != null ? observers.value() : 0.0f);

        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                integrity != null ? integrity.value() : 0.0f,
                observers != null ? observers.value() : 0.0f,
                breaches != null ? breaches.value() : 0.0f,
                weaponCount,
                readinessPercent,
                0.0f,
                0.0f,
                alert != null ? alert.value() : 0.0f,
                true,
                firstLabel,
                firstValue,
                true,
                "Breaches",
                breaches != null ? breaches.value() : 0.0f,
                true
        );
    }

    private Evaluation evaluateItemStorage(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                           @Nullable BlockPos sourcePos, String sourceType,
                                           Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample fill = this.sample(metrics, "fill_pct", "tank_fill");
        final InstrumentSourceHelper.MetricSample total = this.sampleOfType(metrics, MetricType.ITEM_COUNT, "total_items", "stock_level");
        final List<ItemRow> rows = this.resolveItemRows(metrics);
        final boolean hasData = fill != null || total != null || !rows.isEmpty();
        if (!hasData) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                fill != null ? fill.value() : 0.0f,
                total != null ? total.value() : 0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                rows.size() > 0,
                rows.size() > 0 ? rows.get(0).label() : "",
                rows.size() > 0 ? rows.get(0).value() : 0.0f,
                rows.size() > 1,
                rows.size() > 1 ? rows.get(1).label() : "",
                rows.size() > 1 ? rows.get(1).value() : 0.0f,
                true
        );
    }

    private Evaluation evaluateFluidStorage(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                            @Nullable BlockPos sourcePos, String sourceType,
                                            Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample fill = this.sample(metrics, "boiler_gauge", "fill_pct", "tank_fill");
        final InstrumentSourceHelper.MetricSample amount = this.sampleOfType(metrics, MetricType.FLUID_MB, "fluid_amount", "stock_level");
        final InstrumentSourceHelper.MetricSample capacity = this.sampleOfType(metrics, MetricType.FLUID_MB, "tank_capacity", "stock_capacity");
        final InstrumentSourceHelper.MetricSample heat = this.sample(metrics, "heat_pct", "heat_level");
        final boolean hasData = fill != null || amount != null || heat != null;
        if (!hasData) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                fill != null ? fill.value() : 0.0f,
                0.0f,
                amount != null ? amount.value() : 0.0f,
                capacity != null ? capacity.value() : 0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                heat != null,
                heat != null ? heat.label() : "",
                heat != null ? heat.value() : 0.0f,
                false,
                "",
                0.0f,
                true
        );
    }

    private Evaluation evaluateEnergyStorage(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                             @Nullable BlockPos sourcePos, String sourceType,
                                             Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample fill = this.sample(metrics, "energy_fill_pct", "fill_pct");
        final InstrumentSourceHelper.MetricSample stored = this.sampleOfType(metrics, MetricType.ENERGY_FE, "energy_stored");
        final InstrumentSourceHelper.MetricSample capacity = this.sampleOfType(metrics, MetricType.ENERGY_FE, "energy_capacity");
        final InstrumentSourceHelper.MetricSample rate = this.sampleOfType(metrics, MetricType.ENERGY_FE_PER_TICK, "energy_net_rate", "energy_output_rate", "energy_input_rate");
        final InstrumentSourceHelper.MetricSample connectors = this.sample(metrics, "connector_count");
        final boolean hasData = fill != null || stored != null || capacity != null || rate != null || connectors != null;
        if (!hasData) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                fill != null ? fill.value() : (stored != null && capacity != null && capacity.value() > 0.0f ? stored.value() * 100.0f / capacity.value() : 0.0f),
                0.0f,
                stored != null ? stored.value() : 0.0f,
                capacity != null ? capacity.value() : 0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                rate != null,
                rate != null ? rate.label() : "",
                rate != null ? rate.value() : 0.0f,
                connectors != null,
                connectors != null ? connectors.label() : "",
                connectors != null ? connectors.value() : 0.0f,
                true
        );
    }

    private Evaluation evaluateKineticStress(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                             @Nullable BlockPos sourcePos, String sourceType,
                                             Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample stressPct = this.sample(metrics, "stress_pct");
        final InstrumentSourceHelper.MetricSample used = this.sample(metrics, "stress_su");
        final InstrumentSourceHelper.MetricSample capacity = this.sample(metrics, "capacity_su");
        final InstrumentSourceHelper.MetricSample rpm = this.sample(metrics, "abs_rpm", "rpm");
        final boolean hasData = stressPct != null || used != null || capacity != null || rpm != null;
        if (!hasData) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                stressPct != null ? stressPct.value() : 0.0f,
                used != null ? used.value() : 0.0f,
                capacity != null ? capacity.value() : 0.0f,
                rpm != null ? Math.abs(rpm.value()) : 0.0f,
                false,
                "",
                0.0f,
                false,
                "",
                0.0f,
                true
        );
    }

    private Evaluation evaluateKineticSpeed(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                            @Nullable BlockPos sourcePos, String sourceType,
                                            Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        final InstrumentSourceHelper.MetricSample rpm = this.sample(metrics, "abs_rpm", "rpm");
        if (rpm == null) {
            return Evaluation.empty(displayState == DisplayState.LIVE ? DisplayState.UNSUPPORTED : displayState, sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                Math.abs(rpm.value()),
                false,
                "",
                0.0f,
                false,
                "",
                0.0f,
                true
        );
    }

    private boolean hasRenderableState() {
        return this.displayState == DisplayState.LIVE || this.displayState == DisplayState.LINK_STALE;
    }

    private List<ItemRow> resolveItemRows(Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        if (this.settings.getStorageDetailMode() != MiniDisplaySettings.StorageDetailMode.SELECTED_ITEMS) {
            return List.of();
        }

        final Map<String, ItemRow> available = new LinkedHashMap<>();
        for (InstrumentSourceHelper.MetricSample sample : metrics.values()) {
            if (!sample.metricId().startsWith("item_") || sample.type() != MetricType.ITEM_COUNT) {
                continue;
            }
            available.put(sample.metricId(), new ItemRow(sample.metricId(), sample.label(), sample.value()));
        }

        final List<ItemRow> sorted = new ArrayList<>(available.values());
        sorted.sort((first, second) -> {
            final int valueCompare = Float.compare(second.value(), first.value());
            if (valueCompare != 0) {
                return valueCompare;
            }
            return first.label().compareToIgnoreCase(second.label());
        });

        final List<ItemRow> rows = new ArrayList<>(2);
        final List<ItemStack> trackedFilters = this.settings.getTrackedFilterItems();
        final List<String> usedMetricIds = new ArrayList<>(2);
        for (ItemStack tracked : trackedFilters) {
            if (tracked.isEmpty()) {
                continue;
            }
            final String metricId = InstrumentSourceHelper.metricIdForItem(tracked);
            if (metricId.isEmpty() || usedMetricIds.contains(metricId)) {
                continue;
            }
            usedMetricIds.add(metricId);
            final ItemRow existing = available.get(metricId);
            rows.add(existing != null ? existing : new ItemRow(metricId, tracked.getHoverName().getString(), 0.0f));
            if (rows.size() >= 2) {
                return rows;
            }
        }

        for (ItemRow row : sorted) {
            if (usedMetricIds.contains(row.metricId())) {
                continue;
            }
            rows.add(row);
            if (rows.size() >= 2) {
                break;
            }
        }
        return rows;
    }

    @Nullable
    private InstrumentSourceHelper.MetricSample sample(Map<String, InstrumentSourceHelper.MetricSample> metrics, String... ids) {
        for (String id : ids) {
            final InstrumentSourceHelper.MetricSample sample = metrics.get(id);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    @Nullable
    private InstrumentSourceHelper.MetricSample sampleOfType(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                                             MetricType type, String... ids) {
        for (String id : ids) {
            final InstrumentSourceHelper.MetricSample sample = metrics.get(id);
            if (sample != null && sample.type() == type) {
                return sample;
            }
        }
        return null;
    }

    @Nullable
    private InstrumentSourceHelper.LocalBinding resolveLocalBinding() {
        final List<InstrumentSourceHelper.LocalBinding> bindings = this.resolveLocalBindings();
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    private List<InstrumentSourceHelper.LocalBinding> resolveLocalBindings() {
        final List<InstrumentSourceHelper.LocalBinding> bindings = new ArrayList<>(2);
        final InstrumentSourceHelper.LocalBinding selfBinding = this.resolveLocalBindingAt(this.worldPosition);
        if (selfBinding != null) {
            bindings.add(selfBinding);
        }
        final BlockPos partnerPos = this.getPartnerPos();
        if (partnerPos != null) {
            final InstrumentSourceHelper.LocalBinding partnerBinding = this.resolveLocalBindingAt(partnerPos);
            if (partnerBinding != null) {
                bindings.add(partnerBinding);
            }
        }
        return bindings;
    }

    @Nullable
    private InstrumentSourceHelper.LocalBinding resolveLocalBindingAt(BlockPos pos) {
        if (this.level == null || !(this.getBlockState().getBlock() instanceof MiniDisplayBlock)) {
            return null;
        }
        return InstrumentSourceHelper.resolveWallMountedBinding(this.level, pos, this.getBlockState().getValue(MiniDisplayBlock.FACING));
    }

    @Nullable
    private LocalPreview resolveLocalPreview() {
        for (InstrumentSourceHelper.LocalBinding binding : this.resolveLocalBindings()) {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(
                    binding.blockEntity(),
                    binding.accessFace(),
                    this.settings.getTrackedFilterItems());
            if (readout.sourceKind() != InstrumentSourceHelper.SourceKind.NO_SOURCE) {
                return new LocalPreview(binding, readout);
            }
        }
        return null;
    }

    private void refreshLinkedSnapshot() {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            controller.refreshLinkedSnapshot();
            return;
        }
        if (this.level == null || this.level.isClientSide() || this.linkedSource == null) {
            return;
        }
        final BlockEntity sourceBE = this.level.getBlockEntity(this.linkedSource.getSourcePos());
        if (sourceBE == null || !InstrumentSourceHelper.supportsCompactAutoRead(sourceBE)) {
            return;
        }
        final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null, this.settings.getTrackedFilterItems());
        this.replaceAutoData(this.linkedSource.getSourcePos(), readout.sourceType(), readout.metrics());
    }

    private void updateBlockState() {
        if (this.level == null || this.level.isClientSide() || !this.getBlockState().hasProperty(MiniDisplayBlock.ACTIVE)) {
            return;
        }
        final boolean shouldGlow = this.settings.isLit() && (this.displayState == DisplayState.LIVE || this.displayState == DisplayState.LINK_STALE);
        this.applyActiveState(this.worldPosition, shouldGlow);
        final BlockPos partnerPos = this.getPartnerPos();
        if (partnerPos != null) {
            this.applyActiveState(partnerPos, shouldGlow);
        }
    }

    private void applyActiveState(BlockPos pos, boolean shouldGlow) {
        final BlockState state = this.level.getBlockState(pos);
        if (!(state.getBlock() instanceof MiniDisplayBlock) || !state.hasProperty(MiniDisplayBlock.ACTIVE)) {
            return;
        }
        if (state.getValue(MiniDisplayBlock.ACTIVE) != shouldGlow) {
            this.level.setBlock(pos, state.setValue(MiniDisplayBlock.ACTIVE, shouldGlow), 3);
        }
    }

    private void syncToClient() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public void onAutoData(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            controller.onAutoData(sourcePos, sourceType, metricId, label, value, type);
            return;
        }
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.registerOrUpdateSource(sourcePos, sourceType);
        this.linkedMetrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId, label, value, type));
        this.lastLinkedDataTick = this.level.getGameTime();
        this.setChanged();
    }

    @Override
    public void setSourceType(String sourceType) {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            controller.setSourceType(sourceType);
            return;
        }
        if (this.linkedSource != null) {
            this.linkedSource.setSourceType(sourceType);
        }
    }

    @Override
    public SourceConfig registerOrUpdateSource(BlockPos sourcePos, String sourceType) {
        final MiniDisplayBE controller = this.getControllerBE();
        if (controller != this) {
            return controller.registerOrUpdateSource(sourcePos, sourceType);
        }
        final long gameTime = this.level != null ? this.level.getGameTime() : 0L;
        if (this.linkedSource == null || !this.linkedSource.getSourcePos().equals(sourcePos)) {
            this.linkedSource = new SourceConfig(String.valueOf(this.nextAutoSourceIndex++), sourcePos, sourceType);
            this.linkedSource.setFirstConnectTick(gameTime);
            this.linkedMetrics.clear();
        } else {
            if (!Objects.equals(this.linkedSource.getSourceType(), sourceType)) {
                this.linkedMetrics.clear();
            }
            this.linkedSource.setSourceType(sourceType);
            this.linkedSource.setSourcePos(sourcePos);
        }
        this.linkedSource.setLastDataTick(gameTime);
        return this.linkedSource;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Settings", this.settings.save(registries));
        if (this.linkedSource != null) {
            tag.put("LinkedSource", this.linkedSource.save());
        }
        tag.putInt("NextAutoSourceIndex", this.nextAutoSourceIndex);
        tag.putLong("LastLinkedDataTick", this.lastLinkedDataTick);
        tag.putString("DisplayState", this.displayState.name());
        tag.putString("ActiveSourceKind", this.activeSourceKind.name());
        if (this.activeSourcePos != null) {
            tag.putLong("ActiveSourcePos", this.activeSourcePos.asLong());
        }
        tag.putString("ActiveSourceType", this.activeSourceType);
        tag.putFloat("ActiveFillPercent", this.activeFillPercent);
        tag.putFloat("ActiveTotalItems", this.activeTotalItems);
        tag.putFloat("ActiveFluidAmount", this.activeFluidAmount);
        tag.putFloat("ActiveFluidCapacity", this.activeFluidCapacity);
        tag.putFloat("ActiveStressPercent", this.activeStressPercent);
        tag.putFloat("ActiveStressUsed", this.activeStressUsed);
        tag.putFloat("ActiveStressCapacity", this.activeStressCapacity);
        tag.putFloat("ActiveRpm", this.activeRpm);
        tag.putBoolean("FirstDetailPresent", this.firstDetailPresent);
        tag.putString("FirstDetailLabel", this.firstDetailLabel);
        tag.putFloat("FirstDetailValue", this.firstDetailValue);
        tag.putBoolean("SecondDetailPresent", this.secondDetailPresent);
        tag.putString("SecondDetailLabel", this.secondDetailLabel);
        tag.putFloat("SecondDetailValue", this.secondDetailValue);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.settings = tag.contains("Settings") ? MiniDisplaySettings.load(tag.getCompound("Settings"), registries) : new MiniDisplaySettings();
        this.linkedSource = tag.contains("LinkedSource") ? SourceConfig.load(tag.getCompound("LinkedSource")) : null;
        this.nextAutoSourceIndex = tag.contains("NextAutoSourceIndex") ? tag.getInt("NextAutoSourceIndex") : 1;
        this.lastLinkedDataTick = tag.contains("LastLinkedDataTick") ? tag.getLong("LastLinkedDataTick") : Long.MIN_VALUE;
        try {
            this.displayState = DisplayState.valueOf(tag.getString("DisplayState"));
        } catch (Exception ignored) {
            this.displayState = DisplayState.NO_SOURCE;
        }
        try {
            this.activeSourceKind = InstrumentSourceHelper.SourceKind.valueOf(tag.getString("ActiveSourceKind"));
        } catch (Exception ignored) {
            this.activeSourceKind = InstrumentSourceHelper.SourceKind.NO_SOURCE;
        }
        this.activeSourcePos = tag.contains("ActiveSourcePos") ? BlockPos.of(tag.getLong("ActiveSourcePos")) : null;
        this.activeSourceType = tag.getString("ActiveSourceType");
        this.activeFillPercent = tag.getFloat("ActiveFillPercent");
        this.activeTotalItems = tag.getFloat("ActiveTotalItems");
        this.activeFluidAmount = tag.getFloat("ActiveFluidAmount");
        this.activeFluidCapacity = tag.getFloat("ActiveFluidCapacity");
        this.activeStressPercent = tag.getFloat("ActiveStressPercent");
        this.activeStressUsed = tag.getFloat("ActiveStressUsed");
        this.activeStressCapacity = tag.getFloat("ActiveStressCapacity");
        this.activeRpm = tag.getFloat("ActiveRpm");
        this.firstDetailPresent = tag.getBoolean("FirstDetailPresent");
        this.firstDetailLabel = tag.getString("FirstDetailLabel");
        this.firstDetailValue = tag.getFloat("FirstDetailValue");
        this.secondDetailPresent = tag.getBoolean("SecondDetailPresent");
        this.secondDetailLabel = tag.getString("SecondDetailLabel");
        this.secondDetailValue = tag.getFloat("SecondDetailValue");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    public enum DisplayState {
        LIVE,
        LINK_STALE,
        LINK_LOST,
        NO_SOURCE,
        UNSUPPORTED
    }

    private record ItemRow(String metricId, String label, float value) {
    }

    private record LocalPreview(InstrumentSourceHelper.LocalBinding binding, InstrumentSourceHelper.SourceReadout readout) {
    }

    private record Evaluation(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                              @Nullable BlockPos sourcePos, String sourceType,
                              float fillPercent, float totalItems, float fluidAmount, float fluidCapacity,
                              float stressPercent, float stressUsed, float stressCapacity, float rpm,
                              boolean firstDetailPresent, String firstDetailLabel, float firstDetailValue,
                              boolean secondDetailPresent, String secondDetailLabel, float secondDetailValue,
                              boolean hasRenderableData) {
        private static Evaluation empty(DisplayState displayState, InstrumentSourceHelper.SourceKind sourceKind,
                                        @Nullable BlockPos sourcePos, String sourceType) {
            return new Evaluation(displayState, sourceKind, sourcePos, sourceType,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                    false, "", 0.0f, false, "", 0.0f, false);
        }
    }
}
