package com.createstats.blockentity;

import com.createstats.block.StatusTowerLightBlock;
import com.createstats.data.MetricType;
import com.createstats.display.BattleCompactTargetHandler;
import com.createstats.display.StatsDisplayTarget;
import com.createstats.topology.NodeType;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.KineticNetworkMetrics;
import com.createstats.util.StockpileSwitchMetrics;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class StatusTowerLightBE extends BlockEntity implements IAutoDataReceiver, com.createstats.power.IFeConsumer {

    private static final float EPSILON = 0.01f;
    private static final long LINK_TIMEOUT_TICKS = 60L;

    private StatusTowerLightSettings settings;
    private final Map<String, MetricSample> linkedMetrics;
    @Nullable
    private SourceConfig linkedSource;
    private int nextAutoSourceIndex;
    private long lastLinkedDataTick;

    private SignalState signalState;
    private SourceState sourceState;

    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_STATUS_TOWER_LIGHT));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }
    @Nullable
    private BlockPos activeSourcePos;
    private String activeSourceType;
    private String activeMetricId;
    private String activeMetricLabel;
    private MetricType activeMetricType;
    private float activeMetricValue;

    public StatusTowerLightBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new StatusTowerLightSettings();
        this.linkedMetrics = new LinkedHashMap<>();
        this.nextAutoSourceIndex = 1;
        this.lastLinkedDataTick = Long.MIN_VALUE;
        this.signalState = SignalState.NO_DATA;
        this.sourceState = SourceState.MISSING;
        this.activeSourceType = "";
        this.activeMetricId = "";
        this.activeMetricLabel = "";
        this.activeMetricType = MetricType.GENERIC;
        this.activeMetricValue = 0.0f;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // FE Dependent Mode gate: an unpowered tower light snaps dark instead of evaluating.
        final boolean feActive = this.signalState != SignalState.OFF && this.signalState != SignalState.NO_DATA;
        final boolean fePowered = this.feConsumer.serviceTick(feActive);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            this.applyUnpoweredVisual();
            return;
        }
        this.evaluateAndApply(this.level.getGameTime());
    }

    /** Hard cutoff: force the lamp dark, drop any critical redstone output, sync once. */
    private void applyUnpoweredVisual() {
        if (this.signalState == SignalState.OFF) {
            return;
        }
        final boolean oldCritical = this.isCriticalPowered();
        this.signalState = SignalState.OFF;
        this.updateSteadyBlockState();
        if (oldCritical && this.level != null) {
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }
        this.syncToClient();
    }

    public void clientTick() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        this.updateClientVisual(this.level.getGameTime());
    }

    public StatusTowerLightSettings getSettings() {
        return this.settings;
    }

    public void setSettings(StatusTowerLightSettings settings) {
        this.settings = StatusTowerLightSettings.load(settings.save());
        if (this.level != null && !this.level.isClientSide()) {
            this.evaluateAndApply(this.level.getGameTime());
            this.syncToClient();
        } else {
            this.setChanged();
        }
    }

    public boolean isCriticalPowered() {
        return this.signalState == SignalState.CRITICAL;
    }

    public SignalState getSignalState() {
        return this.signalState;
    }

    public SourceState getSourceState() {
        return this.sourceState;
    }

    public String getSourceSummary() {
        return this.getPreviewSourceSummary(this.settings.getSourceMode());
    }

    public String getMetricSummary() {
        if (this.activeMetricLabel.isEmpty()) {
            return "No live metric";
        }
        return this.activeMetricLabel + ": " + this.activeMetricType.formatValue(this.activeMetricValue);
    }

    public String getRuleSummary() {
        if (this.settings.getLogicMode() == StatusTowerLightSettings.LogicMode.BINARY) {
            return this.settings.isBinaryInvert() ? "Binary: true = off" : "Binary: true = green";
        }
        return this.settings.getThresholdDirection().getDisplayName()
                + " | W " + formatNumber(this.settings.getWarningThreshold())
                + " | C " + formatNumber(this.settings.getCriticalThreshold());
    }

    private void evaluateAndApply(long gameTime) {
        final Evaluation evaluation = this.settings.getSourceMode() == StatusTowerLightSettings.SourceMode.LOCAL
                ? this.evaluateLocal()
                : this.evaluateLinked(gameTime);

        final boolean oldCritical = this.isCriticalPowered();
        final boolean valueChanged = Math.abs(this.activeMetricValue - evaluation.metricValue()) > 0.5f;
        final boolean infoChanged = !Objects.equals(this.activeSourcePos, evaluation.sourcePos())
                || !Objects.equals(this.activeSourceType, evaluation.sourceType())
                || !Objects.equals(this.activeMetricId, evaluation.metricId())
                || !Objects.equals(this.activeMetricLabel, evaluation.metricLabel())
                || this.activeMetricType != evaluation.metricType()
                || valueChanged
                || this.signalState != evaluation.signalState()
                || this.sourceState != evaluation.sourceState();

        this.signalState = evaluation.signalState();
        this.sourceState = evaluation.sourceState();
        this.activeSourcePos = evaluation.sourcePos();
        this.activeSourceType = evaluation.sourceType();
        this.activeMetricId = evaluation.metricId();
        this.activeMetricLabel = evaluation.metricLabel();
        this.activeMetricType = evaluation.metricType();
        this.activeMetricValue = evaluation.metricValue();

        this.updateSteadyBlockState();

        if (oldCritical != this.isCriticalPowered()) {
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }

        if (infoChanged) {
            this.syncToClient();
        }
    }

    private void updateClientVisual(long gameTime) {
        if (this.level == null || !this.getBlockState().hasProperty(StatusTowerLightBlock.VISUAL)) {
            return;
        }
        final StatusTowerLightBlock.VisualState visualState = this.computeVisualState(gameTime);
        if (this.getBlockState().getValue(StatusTowerLightBlock.VISUAL) != visualState) {
            this.level.setBlock(this.worldPosition, this.getBlockState().setValue(StatusTowerLightBlock.VISUAL, visualState), 2);
        }
    }

    private void updateSteadyBlockState() {
        if (this.level == null || !this.getBlockState().hasProperty(StatusTowerLightBlock.VISUAL) || !this.getBlockState().hasProperty(StatusTowerLightBlock.GLOW)) {
            return;
        }
        final StatusTowerLightBlock.VisualState steadyVisual = this.computeSteadyVisualState();
        final BlockState currentState = this.getBlockState();
        final BlockState updatedState = currentState
                .setValue(StatusTowerLightBlock.VISUAL, steadyVisual)
                .setValue(StatusTowerLightBlock.GLOW, steadyVisual);
        if (updatedState != currentState) {
            this.level.setBlock(this.worldPosition, updatedState, 3);
        }
    }

    private Evaluation evaluateLocal() {
        final LocalBinding binding = this.resolveLocalBinding();
        if (binding.state() != SourceState.BOUND || binding.blockEntity() == null || binding.pos() == null) {
            return Evaluation.noData(binding.state(), null, "");
        }

        final Map<String, MetricSample> metrics = new LinkedHashMap<>();
        final String sourceType = this.readLocalMetrics(binding.blockEntity(), metrics);
        if (metrics.isEmpty()) {
            return Evaluation.noData(binding.state(), binding.pos(), sourceType);
        }
        return this.evaluateMetrics(metrics, binding.state(), binding.pos(), sourceType);
    }

    private Evaluation evaluateLinked(long gameTime) {
        if (this.linkedSource == null || this.linkedMetrics.isEmpty() || gameTime - this.lastLinkedDataTick > LINK_TIMEOUT_TICKS) {
            return Evaluation.noData(SourceState.MISSING,
                    this.linkedSource != null ? this.linkedSource.getSourcePos() : null,
                    this.linkedSource != null ? this.linkedSource.getSourceType() : "");
        }
        return this.evaluateMetrics(this.linkedMetrics, SourceState.BOUND, this.linkedSource.getSourcePos(), this.linkedSource.getSourceType());
    }

    private Evaluation evaluateMetrics(Map<String, MetricSample> metrics, SourceState sourceState, BlockPos sourcePos, String sourceType) {
        final MetricSample resolvedSample = this.selectMetric(metrics);
        if (resolvedSample == null) {
            return Evaluation.noData(sourceState, sourcePos, sourceType);
        }

        final boolean thresholdBattleAlert = BattleCompactTargetHandler.ALERT_METRIC.equals(resolvedSample.metricId());
        final boolean binaryMetric = !thresholdBattleAlert && (resolvedSample.type() == MetricType.BOOLEAN_FLAG
                || this.settings.getMetric().isBinaryMetric()
                || (this.settings.getMetric() == StatusTowerLightSettings.Metric.AUTO
            && this.settings.getLogicMode() == StatusTowerLightSettings.LogicMode.BINARY));

        if (binaryMetric) {
            boolean active = resolvedSample.value() > 0.5f;
            if (this.settings.isBinaryInvert()) {
                active = !active;
            }
            // "Overstressed" means trouble when true, so map active→CRITICAL rather than NORMAL,
            // and inactive→NORMAL rather than OFF, respecting any binaryInvert the player set.
            if ("overstressed".equals(resolvedSample.metricId())) {
                return new Evaluation(active ? SignalState.CRITICAL : SignalState.NORMAL, sourceState, sourcePos, sourceType,
                        resolvedSample.metricId(), resolvedSample.label(), resolvedSample.type(), resolvedSample.value());
            }
            return new Evaluation(active ? SignalState.NORMAL : SignalState.OFF, sourceState, sourcePos, sourceType,
                    resolvedSample.metricId(), resolvedSample.label(), resolvedSample.type(), resolvedSample.value());
        }

        final SignalState thresholdState = this.evaluateThreshold(resolvedSample.value());
        return new Evaluation(thresholdState, sourceState, sourcePos, sourceType,
                resolvedSample.metricId(), resolvedSample.label(), resolvedSample.type(), resolvedSample.value());
    }

    private SignalState evaluateThreshold(float value) {
        if (this.settings.getThresholdDirection() == StatusTowerLightSettings.ThresholdDirection.HIGHER_IS_WORSE) {
            if (value >= this.settings.getCriticalThreshold()) {
                return SignalState.CRITICAL;
            }
            if (value >= this.settings.getWarningThreshold()) {
                return SignalState.WARNING;
            }
            return SignalState.NORMAL;
        }
        if (value <= this.settings.getCriticalThreshold()) {
            return SignalState.CRITICAL;
        }
        if (value <= this.settings.getWarningThreshold()) {
            return SignalState.WARNING;
        }
        return SignalState.NORMAL;
    }

    @Nullable
    private MetricSample selectMetric(Map<String, MetricSample> metrics) {
        return switch (this.settings.getMetric()) {
            case AUTO -> this.selectAutoMetric(metrics);
            case RUNNING -> this.sampleByIds(metrics, "running");
            case OVERSTRESSED -> this.sampleByIds(metrics, "overstressed");
            case RPM -> this.sampleByIds(metrics, "rpm", "abs_rpm");
            case STRESS_PERCENT -> this.sampleByIds(metrics, "stress_pct");
            case SPARE_CAPACITY -> this.sampleByIds(metrics, "remaining_su", "capacity_su");
            case FILL_LEVEL -> this.sampleByIds(metrics, "fill_pct", "tank_fill", "boiler_gauge");
            case ITEM_COUNT -> this.sampleByIds(metrics, "stock_level", "total_items", "total_seen");
            case ITEMS_PER_MIN -> this.sampleByIds(metrics, "items_per_min", "rate_per_min");
            case FLUID_MB -> this.sampleByIds(metrics, "fluid_amount", "tank_capacity");
            case ENERGY_FILL -> this.sampleByIds(metrics, "energy_fill_pct");
            case ENERGY_STORED -> this.sampleByIds(metrics, "energy_stored", "energy_capacity");
            case ENERGY_RATE -> this.sampleByIds(metrics, "energy_net_rate", "energy_output_rate", "energy_input_rate", "network_pushed", "network_pulled");
            case HEAT_LEVEL -> this.sampleByIds(metrics, "heat_level");
            case BOOLEAN -> this.sampleByIds(metrics, "powered", "running", "connected", "energy_can_receive", "energy_can_extract", "passive_heat", "overstressed");
        };
    }

    @Nullable
    private MetricSample selectAutoMetric(Map<String, MetricSample> metrics) {
        final MetricSample battleAlert = metrics.get(BattleCompactTargetHandler.ALERT_METRIC);
        if (battleAlert != null) {
            return battleAlert;
        }
        if (this.settings.getLogicMode() == StatusTowerLightSettings.LogicMode.BINARY) {
            final MetricSample binaryFirst = this.sampleByIds(metrics, "running", "powered", "connected", "energy_can_receive", "energy_can_extract", "overstressed", "rpm", "stress_pct", "energy_fill_pct", "fill_pct", "tank_fill");
            if (binaryFirst != null) {
                return binaryFirst;
            }
        }
        final MetricSample preferred = this.sampleByIds(metrics,
                "stress_pct", "energy_fill_pct", "energy_net_rate", "energy_stored", "fill_pct", "tank_fill", "boiler_gauge",
                "items_per_min", "rate_per_min", "rpm",
                "stock_level", "total_items", "total_seen", "fluid_amount",
                "running", "powered");
        if (preferred != null) {
            return preferred;
        }
        for (MetricSample sample : metrics.values()) {
            return sample;
        }
        return null;
    }

    @Nullable
    private MetricSample sampleByIds(Map<String, MetricSample> metrics, String... ids) {
        for (String id : ids) {
            final MetricSample sample = metrics.get(id);
            if (sample != null) {
                return sample;
            }
        }
        return null;
    }

    private LocalBinding resolveLocalBinding() {
        if (this.level == null) {
            return new LocalBinding(null, null, SourceState.MISSING);
        }

        final BlockPos belowPos = this.worldPosition.below();
        final BlockEntity below = this.level.getBlockEntity(belowPos);
        if (this.isSupportedLocalSource(below)) {
            return this.normalizeLocalBinding(belowPos, below);
        }

        int matches = 0;
        BlockPos matchedPos = null;
        BlockEntity matchedBe = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockPos candidatePos = this.worldPosition.relative(direction);
            final BlockEntity candidate = this.level.getBlockEntity(candidatePos);
            if (!this.isSupportedLocalSource(candidate)) {
                continue;
            }
            matches++;
            if (matches == 1) {
                matchedPos = candidatePos;
                matchedBe = candidate;
            } else {
                return new LocalBinding(null, null, SourceState.AMBIGUOUS);
            }
        }

        if (matches == 1) {
            return this.normalizeLocalBinding(matchedPos, matchedBe);
        }
        return new LocalBinding(null, null, SourceState.MISSING);
    }

    private LocalBinding normalizeLocalBinding(BlockPos pos, BlockEntity sourceBE) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                return new LocalBinding(controller.getBlockPos(), controller, SourceState.BOUND);
            }
        }
        return new LocalBinding(pos, sourceBE, SourceState.BOUND);
    }

    private boolean isSupportedLocalSource(@Nullable BlockEntity sourceBE) {
        return sourceBE instanceof KineticBlockEntity
                || sourceBE instanceof ThresholdSwitchBlockEntity
                || sourceBE instanceof FluidTankBlockEntity
                || sourceBE instanceof ProductionDetectorBE
                || this.isEnergyLocalSource(sourceBE);
    }

    private boolean isEnergyLocalSource(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        try {
            return InstrumentSourceHelper.readSource(sourceBE, null).sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String readLocalMetrics(BlockEntity sourceBE, Map<String, MetricSample> metrics) {
        final InstrumentSourceHelper.SourceReadout sharedReadout = InstrumentSourceHelper.readSource(sourceBE, null);
        if (sharedReadout.sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE) {
            for (InstrumentSourceHelper.MetricSample sample : sharedReadout.metrics().values()) {
                this.putSample(metrics, sample.metricId(), sample.label(), sample.value(), sample.type());
            }
            return sharedReadout.sourceType();
        }

        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            final float stressPct = gauge.dialTarget * 100.0f;
            this.putSample(metrics, "stress_pct", "Stress %", stressPct, MetricType.STRESS_PERCENT);
            final float networkStress = gauge.getNetworkStress();
            this.putSample(metrics, "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
            final float networkCapacity = gauge.getNetworkCapacity();
            this.putSample(metrics, "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
            this.putSample(metrics, "remaining_su", "Remaining (SU)", Math.max(0.0f, networkCapacity - networkStress), MetricType.STRESS_SU);
            final float rpm = Math.abs(gauge.getSpeed());
            this.putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            this.putSample(metrics, "running", "Running", rpm > EPSILON ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putSample(metrics, "overstressed", "Overstressed", gauge.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return "Stressometer";
        }

        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            final float rpm = gauge.getSpeed();
            this.putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            this.putSample(metrics, "abs_rpm", "Speed (abs)", Math.abs(rpm), MetricType.RPM);
            this.putSample(metrics, "running", "Running", Math.abs(rpm) > EPSILON ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putGenericKineticMetrics(metrics, gauge);
            return "Speedometer";
        }

        if (sourceBE instanceof SafetyClutchBE clutch) {
            this.putSample(metrics, "stress_pct", "Stress %", clutch.getLastStressPercent(), MetricType.STRESS_PERCENT);
            this.putSample(metrics, "stress_su", "Current Stress (SU)", clutch.getLastStressSU(), MetricType.STRESS_SU);
            this.putSample(metrics, "capacity_su", "Capacity (SU)", clutch.getLastCapacitySU(), MetricType.STRESS_SU);
            this.putSample(metrics, "remaining_su", "Remaining (SU)",
                    Math.max(0.0f, clutch.getLastCapacitySU() - clutch.getLastStressSU()), MetricType.STRESS_SU);
            this.putSample(metrics, "rpm", "RPM", Math.abs(clutch.getSpeed()), MetricType.RPM);
            this.putSample(metrics, "running", "Running", clutch.isNetworkRunning() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putSample(metrics, "overstressed", "Overstressed", clutch.isLastOverstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return "Safety Clutch";
        }

        if (sourceBE instanceof AutoTransmissionClutchBE transmission) {
            this.putSample(metrics, "stress_pct", "Stress %", transmission.getLastStressPercent(), MetricType.STRESS_PERCENT);
            this.putSample(metrics, "stress_su", "Current Stress (SU)", transmission.getLastStressSU(), MetricType.STRESS_SU);
            this.putSample(metrics, "capacity_su", "Capacity (SU)", transmission.getLastCapacitySU(), MetricType.STRESS_SU);
            this.putSample(metrics, "remaining_su", "Remaining (SU)",
                    Math.max(0.0f, transmission.getLastCapacitySU() - transmission.getLastStressSU()), MetricType.STRESS_SU);
            this.putSample(metrics, "rpm", "RPM", Math.abs(transmission.getSpeed()), MetricType.RPM);
            this.putSample(metrics, "running", "Running", transmission.isNetworkRunning() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putSample(metrics, "overstressed", "Overstressed", transmission.isLastOverstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return "Auto Variable Clutch";
        }

        if (sourceBE instanceof ThresholdSwitchBlockEntity thresholdSwitch) {
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(thresholdSwitch);
            final MetricType amountType = readout.amountMetricType();
            this.putSample(metrics, "fill_pct", "Fill Level %", readout.fillPercent(), MetricType.FILL_LEVEL);
            this.putSample(metrics, "stock_level", "Current Amount", readout.currentAmount(), amountType);
            this.putSample(metrics, "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
            this.putSample(metrics, "powered", "Signal Output", thresholdSwitch.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return "Stockpile Switch";
        }

        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                tank = controller;
            }
            this.putSample(metrics, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
            final FluidTank tankInventory = tank.getTankInventory();
            if (tankInventory != null) {
                this.putSample(metrics, "fluid_amount", "Fluid Amount (mB)", tankInventory.getFluidAmount(), MetricType.FLUID_MB);
                this.putSample(metrics, "tank_capacity", "Tank Capacity (mB)", tankInventory.getCapacity(), MetricType.FLUID_MB);
            }
            final BoilerData boiler = tank.boiler;
            if (boiler != null && boiler.isActive()) {
                this.putSample(metrics, "heat_level", "Heat Level", boiler.activeHeat, MetricType.HEAT_LEVEL);
                this.putSample(metrics, "boiler_gauge", "Boiler Gauge %", boiler.gauge.getValue(1.0f) * 100.0f, MetricType.FILL_LEVEL);
                return "Steam Boiler";
            }
            return "Fluid Tank";
        }

        if (sourceBE instanceof ProductionDetectorBE detector) {
            this.putSample(metrics, "total_items", "Total Items", (float) detector.getTotalItemsSeen(), MetricType.ITEM_COUNT);
            this.putSample(metrics, "unique_types", "Unique Types", (float) detector.getTrackedItemTypeCount(), MetricType.ITEM_COUNT);
            return "Production Detector";
        }

        if (sourceBE instanceof KineticFlywheelBE flywheel) {
            final float chargePct = flywheel.getChargePercent();
            // Map the "stress" slot to charge so AUTO/STRESS thresholds track the reserve.
            this.putSample(metrics, "stress_pct", "Charge %", chargePct, MetricType.FILL_LEVEL);
            this.putSample(metrics, "charge_pct", "Charge %", chargePct, MetricType.FILL_LEVEL);
            this.putSample(metrics, "stored_rpm", "Stored RPM", flywheel.getStoredRpm(), MetricType.RPM);
            this.putSample(metrics, "rpm", "RPM", Math.abs(flywheel.getStoredRpm()), MetricType.RPM);
            this.putSample(metrics, "output_su", "Output (SU)", flywheel.getOutputStress(), MetricType.STRESS_SU);
            this.putSample(metrics, "charging", "Charging", flywheel.getMode() == KineticFlywheelBE.Mode.CHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putSample(metrics, "discharging", "Discharging", flywheel.getMode() == KineticFlywheelBE.Mode.DISCHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putSample(metrics, "running", "Running", Math.abs(flywheel.getStoredRpm()) > EPSILON ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putGenericKineticMetrics(metrics, flywheel);
            return "Kinetic Flywheel";
        }

        if (sourceBE instanceof KineticBlockEntity kinetic) {
            final float rpm = Math.abs(kinetic.getSpeed());
            this.putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            this.putSample(metrics, "running", "Running", rpm > EPSILON ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            this.putGenericKineticMetrics(metrics, kinetic);
            return this.resolveSourceType(sourceBE);
        }

        return this.resolveSourceType(sourceBE);
    }

    private void putSample(Map<String, MetricSample> metrics, String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new MetricSample(metricId, label, value, type));
    }

    private void putGenericKineticMetrics(Map<String, MetricSample> metrics, KineticBlockEntity kinetic) {
        if (!KineticNetworkMetrics.supportsStressReadout(kinetic)) {
            this.putSample(metrics, "overstressed", "Overstressed", kinetic.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return;
        }
        final KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(kinetic);
        this.putSample(metrics, "stress_pct", "Stress %", readout.stressPercent(), MetricType.STRESS_PERCENT);
        this.putSample(metrics, "stress_su", "Current Stress (SU)", readout.networkStress(), MetricType.STRESS_SU);
        this.putSample(metrics, "capacity_su", "Capacity (SU)", readout.networkCapacity(), MetricType.STRESS_SU);
        this.putSample(metrics, "remaining_su", "Remaining (SU)", readout.remainingCapacity(), MetricType.STRESS_SU);
        this.putSample(metrics, "overstressed", "Overstressed", readout.overstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    private String resolveSourceType(BlockEntity sourceBE) {
        final String mapped = StatsDisplayTarget.getStatsDisplayLinkSourceType(sourceBE);
        if (!mapped.isEmpty()) {
            return mapped;
        }
        return sourceBE.getBlockState().getBlock().getName().getString();
    }

    public StatusTowerLightSettings.SourceKind getPreviewSourceKind(StatusTowerLightSettings.SourceMode mode) {
        return this.buildSourcePreview(mode).sourceKind();
    }

    public String getPreviewSourceSummary(StatusTowerLightSettings.SourceMode mode) {
        final SourcePreview preview = this.buildSourcePreview(mode);
        if (preview.sourcePos() != null && !preview.sourceType().isEmpty()) {
            return preview.sourceType() + " @ " + preview.sourcePos().toShortString();
        }
        return switch (preview.sourceState()) {
            case AMBIGUOUS -> "Multiple nearby sources";
            case MISSING -> mode == StatusTowerLightSettings.SourceMode.LOCAL ? "No nearby source" : "No linked source";
            case BOUND -> preview.sourceType().isEmpty() ? "Source connected" : preview.sourceType();
        };
    }

    private SourcePreview buildSourcePreview(StatusTowerLightSettings.SourceMode mode) {
        return mode == StatusTowerLightSettings.SourceMode.LOCAL ? this.buildLocalPreview() : this.buildLinkedPreview();
    }

    private SourcePreview buildLocalPreview() {
        final LocalBinding binding = this.resolveLocalBinding();
        if (binding.state() != SourceState.BOUND || binding.blockEntity() == null || binding.pos() == null) {
            final StatusTowerLightSettings.SourceKind sourceKind = binding.state() == SourceState.AMBIGUOUS
                    ? StatusTowerLightSettings.SourceKind.AMBIGUOUS
                    : StatusTowerLightSettings.SourceKind.NO_SOURCE;
            return new SourcePreview(binding.state(), null, "", sourceKind);
        }
        final String sourceType = this.resolveSourceType(binding.blockEntity());
        return new SourcePreview(binding.state(), binding.pos(), sourceType, this.classifySource(binding.blockEntity(), binding.pos(), sourceType));
    }

    private SourcePreview buildLinkedPreview() {
        if (this.linkedSource == null) {
            return new SourcePreview(SourceState.MISSING, null, "", StatusTowerLightSettings.SourceKind.NO_SOURCE);
        }
        final BlockPos sourcePos = this.linkedSource.getSourcePos();
        final String sourceType = this.linkedSource.getSourceType();
        final BlockEntity sourceBE = this.level != null ? this.level.getBlockEntity(sourcePos) : null;
        return new SourcePreview(SourceState.BOUND, sourcePos, sourceType, this.classifySource(sourceBE, sourcePos, sourceType));
    }

    private StatusTowerLightSettings.SourceKind classifySource(@Nullable BlockEntity sourceBE, @Nullable BlockPos sourcePos, String sourceType) {
        if (sourceBE instanceof StressGaugeBlockEntity
                || sourceBE instanceof SafetyClutchBE
                || sourceBE instanceof AutoTransmissionClutchBE) {
            return StatusTowerLightSettings.SourceKind.KINETIC_STRESS;
        }
        if (sourceBE instanceof SpeedGaugeBlockEntity || sourceBE instanceof KineticBlockEntity) {
            return KineticNetworkMetrics.supportsStressReadout(sourceBE)
                    ? StatusTowerLightSettings.SourceKind.KINETIC_STRESS
                    : StatusTowerLightSettings.SourceKind.KINETIC_SPEED;
        }
        if (sourceBE instanceof FluidTankBlockEntity) {
            return StatusTowerLightSettings.SourceKind.FLUID_STORAGE;
        }
        if (sourceBE instanceof ProductionDetectorBE) {
            return StatusTowerLightSettings.SourceKind.PRODUCTION;
        }
        if (sourceBE != null && this.isEnergyLocalSource(sourceBE)) {
            return StatusTowerLightSettings.SourceKind.ENERGY_STORAGE;
        }
        if (sourceBE == null && InstrumentSourceHelper.classifyLinkedSource(sourceType, convertMetrics(this.linkedMetrics)) == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE) {
            return StatusTowerLightSettings.SourceKind.ENERGY_STORAGE;
        }
        if (sourceBE instanceof ThresholdSwitchBlockEntity thresholdSwitch) {
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(thresholdSwitch);
            return readout.amountMetricType() == MetricType.FLUID_MB
                    ? StatusTowerLightSettings.SourceKind.FLUID_STORAGE
                    : StatusTowerLightSettings.SourceKind.ITEM_STORAGE;
        }
        final String normalized = sourceType.toLowerCase();
        final NodeType kineticType = KineticNetworkMetrics.classifySourceType(sourceType);
        if (kineticType != null) {
            return KineticNetworkMetrics.supportsStressReadout(kineticType)
                    ? StatusTowerLightSettings.SourceKind.KINETIC_STRESS
                    : StatusTowerLightSettings.SourceKind.KINETIC_SPEED;
        }
        if (normalized.contains("stress") || normalized.contains("clutch") || normalized.contains("speedometer") || normalized.contains("kinetic")) {
            return StatusTowerLightSettings.SourceKind.KINETIC_STRESS;
        }
        if (normalized.contains("portable engine") || normalized.contains("steering wheel")) {
            return StatusTowerLightSettings.SourceKind.KINETIC_SPEED;
        }
        if (normalized.contains("fluid") || normalized.contains("boiler")) {
            return StatusTowerLightSettings.SourceKind.FLUID_STORAGE;
        }
        if (normalized.contains("energy") || normalized.contains("fe") || normalized.contains("accumulator")
                || normalized.contains("alternator") || normalized.contains("electric motor") || normalized.contains("connector")) {
            return StatusTowerLightSettings.SourceKind.ENERGY_STORAGE;
        }
        if (normalized.contains("production") || normalized.contains("factory panel") || normalized.contains("throughput") || normalized.contains("auger")) {
            return StatusTowerLightSettings.SourceKind.PRODUCTION;
        }
        if (normalized.contains("stockpile") || normalized.contains("observer")) {
            return this.activeMetricType == MetricType.FLUID_MB
                    ? StatusTowerLightSettings.SourceKind.FLUID_STORAGE
                    : StatusTowerLightSettings.SourceKind.ITEM_STORAGE;
        }
        if (normalized.contains("station") || normalized.contains("signal") || normalized.contains("linked typewriter")) {
            return StatusTowerLightSettings.SourceKind.SIGNAL;
        }
        if (sourcePos != null && this.level != null && sourceBE == null) {
            final BlockEntity lookedUp = this.level.getBlockEntity(sourcePos);
            if (lookedUp != null) {
                return this.classifySource(lookedUp, null, sourceType);
            }
        }
        return StatusTowerLightSettings.SourceKind.UNKNOWN;
    }

    private static Map<String, InstrumentSourceHelper.MetricSample> convertMetrics(Map<String, MetricSample> metrics) {
        final Map<String, InstrumentSourceHelper.MetricSample> converted = new LinkedHashMap<>();
        for (MetricSample sample : metrics.values()) {
            converted.put(sample.metricId(), new InstrumentSourceHelper.MetricSample(sample.metricId(), sample.label(), sample.value(), sample.type()));
        }
        return converted;
    }

    private StatusTowerLightBlock.VisualState computeVisualState(long gameTime) {
        return switch (this.signalState) {
            case OFF -> StatusTowerLightBlock.VisualState.OFF;
            case NORMAL -> StatusTowerLightBlock.VisualState.GREEN;
            case WARNING -> StatusTowerLightBlock.VisualState.YELLOW;
            case CRITICAL -> ((gameTime / 10L) % 2L == 0L) ? StatusTowerLightBlock.VisualState.RED : StatusTowerLightBlock.VisualState.OFF;
            case NO_DATA -> ((gameTime / 5L) % 2L == 0L) ? StatusTowerLightBlock.VisualState.YELLOW : StatusTowerLightBlock.VisualState.OFF;
        };
    }

    private StatusTowerLightBlock.VisualState computeSteadyVisualState() {
        return switch (this.signalState) {
            case OFF -> StatusTowerLightBlock.VisualState.OFF;
            case NORMAL -> StatusTowerLightBlock.VisualState.GREEN;
            case WARNING, NO_DATA -> StatusTowerLightBlock.VisualState.YELLOW;
            case CRITICAL -> StatusTowerLightBlock.VisualState.RED;
        };
    }

    private void syncToClient() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void onReceiveData(BlockPos sourcePos, String sourceType, int line, float value, MetricType type, String rawText) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.registerOrUpdateSource(sourcePos, sourceType);
        final String metricId = "line_" + line;
        final String cleaned = rawText == null ? "" : rawText.replaceAll("§[0-9a-fk-or]", "").trim();
        final int separator = cleaned.indexOf(':');
        final String label = separator > 0 ? cleaned.substring(0, separator).trim()
                : (!cleaned.isEmpty() ? cleaned : type.getDisplayName());
        this.linkedMetrics.put(metricId, new MetricSample(metricId, label, value, type));
        this.lastLinkedDataTick = this.level.getGameTime();
        this.setChanged();
    }

    @Override
    public void onAutoData(BlockPos sourcePos, String sourceType, String metricId, String label, float value, MetricType type) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.registerOrUpdateSource(sourcePos, sourceType);
        this.linkedMetrics.put(metricId, new MetricSample(metricId, label, value, type));
        this.lastLinkedDataTick = this.level.getGameTime();
        this.setChanged();
    }

    @Override
    public void setSourceType(String sourceType) {
        if (this.linkedSource != null) {
            this.linkedSource.setSourceType(sourceType);
        }
    }

    @Override
    public SourceConfig registerOrUpdateSource(BlockPos sourcePos, String sourceType) {
        final long gameTime = this.level != null ? this.level.getGameTime() : 0L;
        if (this.linkedSource == null || !this.linkedSource.getSourcePos().equals(sourcePos)) {
            this.linkedSource = new SourceConfig(String.valueOf(this.nextAutoSourceIndex++), sourcePos, sourceType);
            this.linkedSource.setFirstConnectTick(gameTime);
            this.linkedMetrics.clear();
        } else {
            this.linkedSource.setSourceType(sourceType);
            this.linkedSource.setSourcePos(sourcePos);
        }
        this.linkedSource.setLastDataTick(gameTime);
        return this.linkedSource;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Settings", this.settings.save());
        if (this.linkedSource != null) {
            tag.put("LinkedSource", this.linkedSource.save());
        }
        tag.putInt("NextAutoSourceIndex", this.nextAutoSourceIndex);
        tag.putLong("LastLinkedDataTick", this.lastLinkedDataTick);
        tag.putString("SignalState", this.signalState.name());
        tag.putString("SourceState", this.sourceState.name());
        if (this.activeSourcePos != null) {
            tag.putLong("ActiveSourcePos", this.activeSourcePos.asLong());
        }
        tag.putString("ActiveSourceType", this.activeSourceType);
        tag.putString("ActiveMetricId", this.activeMetricId);
        tag.putString("ActiveMetricLabel", this.activeMetricLabel);
        tag.putString("ActiveMetricType", this.activeMetricType.name());
        tag.putFloat("ActiveMetricValue", this.activeMetricValue);
        this.feConsumer.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Settings")) {
            this.settings = StatusTowerLightSettings.load(tag.getCompound("Settings"));
        }
        this.linkedSource = tag.contains("LinkedSource") ? SourceConfig.load(tag.getCompound("LinkedSource")) : null;
        this.nextAutoSourceIndex = tag.contains("NextAutoSourceIndex") ? tag.getInt("NextAutoSourceIndex") : 1;
        this.lastLinkedDataTick = tag.contains("LastLinkedDataTick") ? tag.getLong("LastLinkedDataTick") : Long.MIN_VALUE;
        try {
            this.signalState = SignalState.valueOf(tag.getString("SignalState"));
        } catch (Exception ignored) {
            this.signalState = SignalState.NO_DATA;
        }
        try {
            this.sourceState = SourceState.valueOf(tag.getString("SourceState"));
        } catch (Exception ignored) {
            this.sourceState = SourceState.MISSING;
        }
        this.activeSourcePos = tag.contains("ActiveSourcePos") ? BlockPos.of(tag.getLong("ActiveSourcePos")) : null;
        this.activeSourceType = tag.getString("ActiveSourceType");
        this.activeMetricId = tag.getString("ActiveMetricId");
        this.activeMetricLabel = tag.getString("ActiveMetricLabel");
        try {
            this.activeMetricType = MetricType.valueOf(tag.getString("ActiveMetricType"));
        } catch (Exception ignored) {
            this.activeMetricType = MetricType.GENERIC;
        }
        this.activeMetricValue = tag.contains("ActiveMetricValue") ? tag.getFloat("ActiveMetricValue") : 0.0f;
        this.feConsumer.load(tag);
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

    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    private record MetricSample(String metricId, String label, float value, MetricType type) {
    }

    private record LocalBinding(@Nullable BlockPos pos, @Nullable BlockEntity blockEntity, SourceState state) {
    }

    private record SourcePreview(SourceState sourceState, @Nullable BlockPos sourcePos, String sourceType,
                                 StatusTowerLightSettings.SourceKind sourceKind) {
    }

    private record Evaluation(SignalState signalState, SourceState sourceState, @Nullable BlockPos sourcePos,
                              String sourceType, String metricId, String metricLabel,
                              MetricType metricType, float metricValue) {

        private static Evaluation noData(SourceState sourceState, @Nullable BlockPos sourcePos, String sourceType) {
            return new Evaluation(SignalState.NO_DATA, sourceState, sourcePos, sourceType, "", "", MetricType.GENERIC, 0.0f);
        }
    }

    public enum SignalState {
        OFF("Off"),
        NORMAL("Normal"),
        WARNING("Warning"),
        CRITICAL("Critical"),
        NO_DATA("No Data");

        private final String displayName;

        SignalState(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    public enum SourceState {
        BOUND("Bound"),
        MISSING("Missing"),
        AMBIGUOUS("Ambiguous");

        private final String displayName;

        SourceState(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
