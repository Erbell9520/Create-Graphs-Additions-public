package com.createstats.blockentity;

import com.createstats.block.DataDialBlock;
import com.createstats.data.MetricType;
import com.createstats.display.StatsDisplayTarget;
import com.createstats.util.ItemHandlerMetrics;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.InventoryLookupHelper;
import com.createstats.util.KineticNetworkMetrics;
import com.createstats.util.StockpileSwitchMetrics;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class DataDialBE extends BlockEntity implements IAutoDataReceiver,
        com.createstats.util.CopyableSettings
{
    private static final float EPSILON = 0.01f;
    private static final long LINK_TIMEOUT_TICKS = 60L;
    private static final long LINK_HOLD_TICKS = 100L;
    private static final float RPM_MAX = 256.0f;

    private DataDialSettings settings;
    private final Map<String, MetricSample> linkedMetrics;
    @Nullable
    private SourceConfig linkedSource;
    private int nextAutoSourceIndex;
    private long lastLinkedDataTick;

    private DataDialSettings.SourceKind activeSourceKind;
    @Nullable
    private BlockPos activeSourcePos;
    private String activeSourceType;
    private String activeMetricId;
    private String activeMetricLabel;
    private MetricType activeMetricType;
    private float activeMetricValue;
    private float normalizedValue;
    private boolean hasData;
    private boolean isStale;
    private int lastComparatorLevel;

    public float dialState;
    public float prevDialState;

    public DataDialBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new DataDialSettings();
        this.linkedMetrics = new LinkedHashMap<>();
        this.nextAutoSourceIndex = 1;
        this.lastLinkedDataTick = Long.MIN_VALUE;
        this.activeSourceKind = DataDialSettings.SourceKind.NO_SOURCE;
        this.activeSourceType = "";
        this.activeMetricId = "";
        this.activeMetricLabel = "";
        this.activeMetricType = MetricType.GENERIC;
        this.activeMetricValue = 0.0f;
        this.normalizedValue = 0.0f;
        this.hasData = false;
        this.isStale = false;
        this.lastComparatorLevel = 0;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.evaluateAndApply(this.level.getGameTime());
    }

    public void clientTick() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        this.prevDialState = this.dialState;
        if (this.isStale) {
            this.dialState += (0.0f - this.dialState) * 0.02f;
        } else {
            final float target = this.hasData ? this.normalizedValue : 0.0f;
            this.dialState += (target - this.dialState) * 0.18f;
            if (this.hasData) {
                final float shake = this.getShakeIntensity(target);
                if (shake > 0.0f) {
                    this.dialState += (this.level.random.nextFloat() - 0.5f) * shake;
                }
            }
        }
        this.dialState = Mth.clamp(this.dialState, 0.0f, 1.0f);
    }

    public int getAnalogOutputSignal() {
        return Math.min(15, Math.max(0, Math.round(this.normalizedValue * 15.0f)));
    }

    public float getParticleRPM() {
        if (!this.hasData || this.activeMetricType != MetricType.RPM) {
            return 0.0f;
        }
        return Math.abs(this.activeMetricValue);
    }

    public int getParticleColor() {
        float rpm = this.getParticleRPM();
        if (rpm <= 0.0f) {
            return 0;
        }
        float normalized = Mth.clamp(rpm / RPM_MAX, 0.0f, 1.0f);
        if (normalized < 0.5f) {
            return Color.mixColors(0x3F7BFF, 0x42C7A5, normalized * 2.0f);
        }
        return Color.mixColors(0xD9A441, 0xD8572A, normalized * 2.0f - 1.0f);
    }

    public Component cycleMetric() {
        final DataDialSettings.SourceKind sourceKind = this.getPreviewSourceKind(this.settings.getSourceMode());
        final DataDialSettings.Metric metric = this.settings.cycleMetric(sourceKind);
        if (this.level != null && !this.level.isClientSide()) {
            this.evaluateAndApply(this.level.getGameTime());
            this.syncToClient();
        } else {
            this.setChanged();
        }
        if (metric == null) {
            return Component.translatable("create_stats.data_dial.no_source");
        }
        return Component.translatable("create_stats.data_dial.metric", sourceKind.getDisplayName(), metric.getDisplayName());
    }

    public Component cycleSourceMode() {
        this.settings.cycleSourceMode();
        if (this.settings.getSourceMode() == DataDialSettings.SourceMode.LINKED) {
            this.linkedMetrics.clear();
            this.lastLinkedDataTick = Long.MIN_VALUE;
        }
        if (this.level != null && !this.level.isClientSide()) {
            this.evaluateAndApply(this.level.getGameTime());
            this.syncToClient();
        } else {
            this.setChanged();
        }
        return Component.literal(this.settings.getSourceMode().getDisplayName());
    }

    @Override
    public String settingsKey() {
        return "create_stats:data_dial";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Settings", this.settings.save());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        if (!tag.contains("Settings")) {
            return false;
        }
        if (simulate || this.alreadyHolds(tag, registries)) {
            return true;
        }
        final DataDialSettings.SourceMode before = this.settings.getSourceMode();
        this.settings = DataDialSettings.load(tag.getCompound("Settings"));
        if (before != DataDialSettings.SourceMode.LINKED
                && this.settings.getSourceMode() == DataDialSettings.SourceMode.LINKED) {
            this.linkedMetrics.clear();
            this.lastLinkedDataTick = Long.MIN_VALUE;
        }
        if (this.level != null && !this.level.isClientSide()) {
            this.evaluateAndApply(this.level.getGameTime());
            this.syncToClient();
        } else {
            this.setChanged();
        }
        return true;
    }

    private void evaluateAndApply(long gameTime) {
        final Evaluation evaluation = this.settings.getSourceMode() == DataDialSettings.SourceMode.LOCAL
                ? this.evaluateLocal()
                : this.evaluateLinked(gameTime);

        final boolean valueChanged = Math.abs(this.normalizedValue - evaluation.normalizedValue()) > 0.01f
                || Math.abs(this.activeMetricValue - evaluation.metricValue()) > 0.25f;
        final boolean infoChanged = this.activeSourceKind != evaluation.sourceKind()
                || !Objects.equals(this.activeSourcePos, evaluation.sourcePos())
                || !Objects.equals(this.activeSourceType, evaluation.sourceType())
                || !Objects.equals(this.activeMetricId, evaluation.metricId())
                || !Objects.equals(this.activeMetricLabel, evaluation.metricLabel())
                || this.activeMetricType != evaluation.metricType()
                || this.hasData != evaluation.hasData()
                || this.isStale != evaluation.stale();

        this.activeSourceKind = evaluation.sourceKind();
        this.activeSourcePos = evaluation.sourcePos();
        this.activeSourceType = evaluation.sourceType();
        this.activeMetricId = evaluation.metricId();
        this.activeMetricLabel = evaluation.metricLabel();
        this.activeMetricType = evaluation.metricType();
        this.activeMetricValue = evaluation.metricValue();
        this.normalizedValue = evaluation.normalizedValue();
        this.hasData = evaluation.hasData();
        this.isStale = evaluation.stale();

        this.updateBlockState();
        this.updateComparatorOutput();

        if (infoChanged || valueChanged) {
            this.syncToClient();
        } else {
            this.setChanged();
        }
    }

    private Evaluation evaluateLocal() {
        final LocalBinding binding = this.resolveLocalBinding();
        if (binding == null) {
            this.settings.ensureMetricSupported(DataDialSettings.SourceKind.NO_SOURCE);
            return Evaluation.noData(DataDialSettings.SourceKind.NO_SOURCE, null, "");
        }
        final SourceReadout readout = this.readLocalMetrics(binding.blockEntity(), binding.accessFace());
        this.settings.ensureMetricSupported(readout.sourceKind());
        return this.evaluateMetrics(readout.sourceKind(), binding.pos(), readout.sourceType(), readout.metrics());
    }

    private Evaluation evaluateLinked(long gameTime) {
        final String sourceType = this.linkedSource != null ? this.linkedSource.getSourceType() : "";
        final BlockPos sourcePos = this.linkedSource != null ? this.linkedSource.getSourcePos() : null;
        final DataDialSettings.SourceKind sourceKind = this.classifyLinkedSource(sourceType, this.linkedMetrics);
        this.settings.ensureMetricSupported(sourceKind);
        if (this.linkedSource == null || this.linkedMetrics.isEmpty() || gameTime - this.lastLinkedDataTick > LINK_TIMEOUT_TICKS) {
            return this.holdLinkedEvaluation(sourceKind, sourcePos, sourceType, gameTime);
        }
        final Evaluation evaluation = this.evaluateMetrics(sourceKind, sourcePos, sourceType, this.linkedMetrics);
        if (evaluation.hasData()) {
            return evaluation;
        }
        return this.holdLinkedEvaluation(sourceKind, sourcePos, sourceType, gameTime);
    }

    private Evaluation holdLinkedEvaluation(DataDialSettings.SourceKind sourceKind, @Nullable BlockPos sourcePos, String sourceType, long gameTime) {
        if (!this.hasData || this.activeMetricId.isEmpty() || gameTime - this.lastLinkedDataTick > LINK_HOLD_TICKS) {
            return Evaluation.noData(sourceKind, sourcePos, sourceType);
        }
        return new Evaluation(
                this.activeSourceKind != DataDialSettings.SourceKind.NO_SOURCE ? this.activeSourceKind : sourceKind,
                this.activeSourcePos != null ? this.activeSourcePos : sourcePos,
                !this.activeSourceType.isEmpty() ? this.activeSourceType : sourceType,
                this.activeMetricId,
                this.activeMetricLabel,
                this.activeMetricType,
                this.activeMetricValue,
                this.normalizedValue,
                true,
                true
        );
    }

    private Evaluation evaluateMetrics(DataDialSettings.SourceKind sourceKind, @Nullable BlockPos sourcePos, String sourceType,
                                       Map<String, MetricSample> metrics) {
        final DataDialSettings.Metric selectedMetric = this.settings.getMetric(sourceKind);
        if (selectedMetric == null) {
            return Evaluation.noData(sourceKind, sourcePos, sourceType);
        }
        final MetricSample sample = this.selectMetric(sourceKind, selectedMetric, metrics);
        if (sample == null) {
            return Evaluation.noData(sourceKind, sourcePos, sourceType);
        }
        final float normalized = this.normalizeMetric(selectedMetric, sample.value());
        return new Evaluation(sourceKind, sourcePos, sourceType, sample.metricId(), sample.label(), sample.type(),
                sample.value(), normalized, true, false);
    }

    @Nullable
    private MetricSample selectMetric(DataDialSettings.SourceKind sourceKind, DataDialSettings.Metric selectedMetric,
                                      Map<String, MetricSample> metrics) {
        return switch (selectedMetric) {
            case STRESS_PERCENT -> metrics.get("stress_pct");
            case RPM -> metrics.get("rpm");
            case FILL_PERCENT -> metrics.get("fill_pct");
            case ENERGY_FILL_PERCENT -> metrics.get("energy_fill_pct");
            case ENERGY_STORED -> metrics.get("energy_stored");
            case ENERGY_NET_RATE -> {
                final MetricSample net = metrics.get("energy_net_rate");
                yield net != null ? net : metrics.get("energy_output_rate");
            }
            case BOILER_PERCENT -> metrics.get(sourceKind == DataDialSettings.SourceKind.BOILER ? "boiler_gauge" : "fill_pct");
            case HEAT_PERCENT -> metrics.get("heat_pct");
        };
    }

    private float normalizeMetric(DataDialSettings.Metric metric, float value) {
        return switch (metric) {
            case STRESS_PERCENT, FILL_PERCENT, ENERGY_FILL_PERCENT, BOILER_PERCENT, HEAT_PERCENT -> Mth.clamp(value / 100.0f, 0.0f, 1.0f);
            case ENERGY_STORED -> Mth.clamp(value / 1_000_000.0f, 0.0f, 1.0f);
            case ENERGY_NET_RATE -> Mth.clamp(Math.abs(value) / 8192.0f, 0.0f, 1.0f);
            case RPM -> Mth.clamp(Math.abs(value) / RPM_MAX, 0.0f, 1.0f);
        };
    }

    @Nullable
    private LocalBinding resolveLocalBinding() {
        if (this.level == null) {
            return null;
        }
        final Direction wallDirection = this.getBlockState().getValue(DataDialBlock.FACING);
        final Direction accessFace = wallDirection.getOpposite();
        final BlockPos sourcePos = this.worldPosition.relative(wallDirection);
        final BlockEntity sourceBE = this.level.getBlockEntity(sourcePos);
        if (sourceBE == null) {
            return null;
        }
        return this.normalizeLocalBinding(sourcePos, sourceBE, accessFace);
    }

    private LocalBinding normalizeLocalBinding(BlockPos pos, BlockEntity sourceBE, Direction accessFace) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                return new LocalBinding(controller.getBlockPos(), controller, accessFace);
            }
        }
        if (sourceBE instanceof ItemVaultBlockEntity vault) {
            final ItemVaultBlockEntity controller = vault.getControllerBE() != null ? vault.getControllerBE() : vault;
            return new LocalBinding(controller.getBlockPos(), controller, accessFace);
        }
        return new LocalBinding(pos, sourceBE, accessFace);
    }

    private SourceReadout readLocalMetrics(BlockEntity sourceBE, Direction accessFace) {
        final Map<String, MetricSample> metrics = new LinkedHashMap<>();

        final InstrumentSourceHelper.SourceReadout sharedReadout = InstrumentSourceHelper.readSource(sourceBE, accessFace);
        if (sharedReadout.sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE) {
            for (InstrumentSourceHelper.MetricSample sample : sharedReadout.metrics().values()) {
                metrics.put(sample.metricId(), new MetricSample(sample.metricId(), sample.label(), sample.value(), sample.type()));
            }
            return new SourceReadout(sharedReadout.sourceType(), DataDialSettings.SourceKind.ENERGY_STORAGE, metrics);
        }

        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            metrics.put("stress_pct", new MetricSample("stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT));
            metrics.put("rpm", new MetricSample("rpm", "RPM", Math.abs(gauge.getSpeed()), MetricType.RPM));
            return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof SafetyClutchBE clutch) {
            metrics.put("stress_pct", new MetricSample("stress_pct", "Stress %", clutch.getLastStressPercent(), MetricType.STRESS_PERCENT));
            metrics.put("rpm", new MetricSample("rpm", "RPM", Math.abs(clutch.getSpeed()), MetricType.RPM));
            return new SourceReadout("Safety Clutch", DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof AutoTransmissionClutchBE transmission) {
            metrics.put("stress_pct", new MetricSample("stress_pct", "Stress %", transmission.getLastStressPercent(), MetricType.STRESS_PERCENT));
            metrics.put("rpm", new MetricSample("rpm", "RPM", Math.abs(transmission.getSpeed()), MetricType.RPM));
            return new SourceReadout("Auto Variable Clutch", DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            metrics.put("rpm", new MetricSample("rpm", "RPM", gauge.getSpeed(), MetricType.RPM));
            if (KineticNetworkMetrics.supportsStressReadout(gauge)) {
                final KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(gauge);
                metrics.put("stress_pct", new MetricSample("stress_pct", "Stress %", readout.stressPercent(), MetricType.STRESS_PERCENT));
                return new SourceReadout("Speedometer", DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
            }
            return new SourceReadout("Speedometer", DataDialSettings.SourceKind.KINETIC_SPEED, metrics);
        }

        if (sourceBE instanceof KineticFlywheelBE flywheel) {
            final float chargePct = flywheel.getChargePercent();

            metrics.put("stress_pct", new MetricSample("stress_pct", "Charge %", chargePct, MetricType.FILL_LEVEL));
            metrics.put("charge_pct", new MetricSample("charge_pct", "Charge %", chargePct, MetricType.FILL_LEVEL));
            metrics.put("stored_rpm", new MetricSample("stored_rpm", "Stored RPM", flywheel.getStoredRpm(), MetricType.RPM));
            metrics.put("rpm", new MetricSample("rpm", "RPM", Math.abs(flywheel.getStoredRpm()), MetricType.RPM));
            metrics.put("output_su", new MetricSample("output_su", "Output (SU)", flywheel.getOutputStress(), MetricType.STRESS_SU));
            metrics.put("charging", new MetricSample("charging", "Charging", flywheel.getMode() == KineticFlywheelBE.Mode.CHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG));
            metrics.put("discharging", new MetricSample("discharging", "Discharging", flywheel.getMode() == KineticFlywheelBE.Mode.DISCHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG));
            return new SourceReadout("Kinetic Flywheel", DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof KineticBlockEntity kinetic) {
            metrics.put("rpm", new MetricSample("rpm", "RPM", Math.abs(kinetic.getSpeed()), MetricType.RPM));
            if (KineticNetworkMetrics.supportsStressReadout(kinetic)) {
                final KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(kinetic);
                metrics.put("stress_pct", new MetricSample("stress_pct", "Stress %", readout.stressPercent(), MetricType.STRESS_PERCENT));
                return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.KINETIC_STRESS, metrics);
            }
            return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.KINETIC_SPEED, metrics);
        }

        if (sourceBE instanceof ThresholdSwitchBlockEntity thresholdSwitch) {
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(thresholdSwitch);
            metrics.put("fill_pct", new MetricSample("fill_pct", "Fill %", readout.fillPercent(), MetricType.FILL_LEVEL));
            final DataDialSettings.SourceKind sourceKind = readout.amountMetricType() == MetricType.FLUID_MB
                    ? DataDialSettings.SourceKind.FLUID_STORAGE
                    : DataDialSettings.SourceKind.ITEM_STORAGE;
            return new SourceReadout("Stockpile Switch", sourceKind, metrics);
        }

        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            metrics.put("fill_pct", new MetricSample("fill_pct", "Fill %", controller.getFillState() * 100.0f, MetricType.FILL_LEVEL));
            final BoilerData boiler = controller.boiler;
            if (boiler != null && boiler.isActive()) {
                metrics.put("boiler_gauge", new MetricSample("boiler_gauge", "Boiler %", boiler.gauge.getValue(1.0f) * 100.0f, MetricType.FILL_LEVEL));
                metrics.put("heat_pct", new MetricSample("heat_pct", "Heat %", this.getBoilerHeatPercent(controller, boiler), MetricType.FILL_LEVEL));
                return new SourceReadout("Steam Boiler", DataDialSettings.SourceKind.BOILER, metrics);
            }
            return new SourceReadout("Fluid Tank", DataDialSettings.SourceKind.FLUID_STORAGE, metrics);
        }

        final IItemHandler itemHandler = this.resolveItemHandler(sourceBE, accessFace);
        if (itemHandler != null) {
            final ItemHandlerMetrics.Readout readout = ItemHandlerMetrics.read(itemHandler);
            metrics.put("fill_pct", new MetricSample("fill_pct", "Fill %", readout.fillPercent(), MetricType.FILL_LEVEL));
            return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.ITEM_STORAGE, metrics);
        }

        final IFluidHandler fluidHandler = this.resolveFluidHandler(sourceBE, accessFace);
        if (fluidHandler != null) {
            float totalFluid = 0.0f;
            float totalCapacity = 0.0f;
            for (int tank = 0; tank < fluidHandler.getTanks(); ++tank) {
                totalFluid += fluidHandler.getFluidInTank(tank).getAmount();
                totalCapacity += fluidHandler.getTankCapacity(tank);
            }
            final float fillPercent = totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f;
            metrics.put("fill_pct", new MetricSample("fill_pct", "Fill %", fillPercent, MetricType.FILL_LEVEL));
            return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.FLUID_STORAGE, metrics);
        }

        return new SourceReadout(this.resolveSourceType(sourceBE), DataDialSettings.SourceKind.UNKNOWN, metrics);
    }

    private float getBoilerHeatPercent(FluidTankBlockEntity tank, BoilerData boiler) {
        final int maxHeat = Math.min(boiler.getMaxHeatLevelForBoilerSize(tank.getTotalTankSize()), boiler.getMaxHeatLevelForWaterSupply());
        if (maxHeat <= 0) {
            return 0.0f;
        }
        return Mth.clamp(boiler.activeHeat * 100.0f / maxHeat, 0.0f, 100.0f);
    }

    @Nullable
    private IItemHandler resolveItemHandler(BlockEntity sourceBE, Direction accessFace) {
        // vault controller cap gives the whole multiblock, getInventoryOfBlock under-reports

        return InventoryLookupHelper.resolveItemHandler(sourceBE.getLevel(), sourceBE.getBlockPos(), sourceBE, accessFace);
    }

    @Nullable
    private IFluidHandler resolveFluidHandler(BlockEntity sourceBE, Direction accessFace) {
        IFluidHandler fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), accessFace);
        if (fluidHandler == null) {
            fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), null);
        }
        return fluidHandler;
    }

    private DataDialSettings.SourceKind classifyLinkedSource(String sourceType, Map<String, MetricSample> metrics) {
        if (metrics.containsKey("boiler_gauge") || metrics.containsKey("heat_pct") || metrics.containsKey("heat_level")) {
            return DataDialSettings.SourceKind.BOILER;
        }
        if (metrics.containsKey("stress_pct")) {
            return DataDialSettings.SourceKind.KINETIC_STRESS;
        }
        if (metrics.containsKey("rpm")) {
            return DataDialSettings.SourceKind.KINETIC_SPEED;
        }
        if (metrics.containsKey("energy_fill_pct") || metrics.containsKey("energy_stored") || metrics.containsKey("energy_capacity")
                || metrics.containsKey("energy_net_rate") || metrics.containsKey("energy_output_rate") || metrics.containsKey("energy_input_rate")) {
            return DataDialSettings.SourceKind.ENERGY_STORAGE;
        }
        if (metrics.containsKey("fill_pct")) {
            final String normalized = sourceType.toLowerCase();
            if (normalized.contains("boiler") || normalized.contains("tank") || normalized.contains("fluid")) {
                return DataDialSettings.SourceKind.FLUID_STORAGE;
            }
            return DataDialSettings.SourceKind.ITEM_STORAGE;
        }
        final String normalized = sourceType.toLowerCase();
        if (normalized.contains("portable engine") || normalized.contains("steering wheel")) {
            return DataDialSettings.SourceKind.KINETIC_SPEED;
        }
        if (normalized.contains("energy") || normalized.contains("fe") || normalized.contains("accumulator")
                || normalized.contains("alternator") || normalized.contains("electric motor") || normalized.contains("connector")) {
            return DataDialSettings.SourceKind.ENERGY_STORAGE;
        }
        if (sourceType.isEmpty()) {
            return DataDialSettings.SourceKind.NO_SOURCE;
        }
        return DataDialSettings.SourceKind.UNKNOWN;
    }

    private String resolveSourceType(BlockEntity sourceBE) {
        final String mapped = StatsDisplayTarget.getStatsDisplayLinkSourceType(sourceBE);
        // generic container keeps its own block name

        if (!mapped.isEmpty() && !StatsDisplayTarget.isGenericContainerType(mapped)) {
            return mapped;
        }
        return sourceBE.getBlockState().getBlock().getName().getString();
    }

    private float getShakeIntensity(float normalized) {
        if (normalized < 0.4f) {
            return 0.0f;
        }
        if (normalized < 0.75f) {
            return 0.0035f;
        }
        if (normalized < 0.9f) {
            return 0.009f;
        }
        return 0.018f;
    }

    private void updateBlockState() {
        if (this.level == null || this.level.isClientSide() || !this.getBlockState().hasProperty(DataDialBlock.ACTIVE)) {
            return;
        }
        final BlockState currentState = this.getBlockState();
        if (currentState.getValue(DataDialBlock.ACTIVE) != this.hasData) {
            this.level.setBlock(this.worldPosition, currentState.setValue(DataDialBlock.ACTIVE, this.hasData), 3);
        }
    }

    private void updateComparatorOutput() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final int newLevel = this.getAnalogOutputSignal();
        if (newLevel != this.lastComparatorLevel) {
            this.lastComparatorLevel = newLevel;
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }
    }

    private void syncToClient() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public DataDialSettings.SourceKind getPreviewSourceKind(DataDialSettings.SourceMode mode) {
        if (mode == DataDialSettings.SourceMode.LOCAL) {
            final LocalBinding binding = this.resolveLocalBinding();
            if (binding == null) {
                return DataDialSettings.SourceKind.NO_SOURCE;
            }
            return this.readLocalMetrics(binding.blockEntity(), binding.accessFace()).sourceKind();
        }
        return this.classifyLinkedSource(this.linkedSource != null ? this.linkedSource.getSourceType() : "", this.linkedMetrics);
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

    public void replaceAutoData(BlockPos sourcePos, String sourceType, Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.registerOrUpdateSource(sourcePos, sourceType);
        this.linkedMetrics.clear();
        for (InstrumentSourceHelper.MetricSample sample : metrics.values()) {
            this.linkedMetrics.put(sample.metricId(), new MetricSample(sample.metricId(), sample.label(), sample.value(), sample.type()));
        }
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
        tag.put("Settings", this.settings.save());
        if (this.linkedSource != null) {
            tag.put("LinkedSource", this.linkedSource.save());
        }
        tag.putInt("NextAutoSourceIndex", this.nextAutoSourceIndex);
        tag.putLong("LastLinkedDataTick", this.lastLinkedDataTick);
        tag.putString("ActiveSourceKind", this.activeSourceKind.name());
        if (this.activeSourcePos != null) {
            tag.putLong("ActiveSourcePos", this.activeSourcePos.asLong());
        }
        tag.putString("ActiveSourceType", this.activeSourceType);
        tag.putString("ActiveMetricId", this.activeMetricId);
        tag.putString("ActiveMetricLabel", this.activeMetricLabel);
        tag.putString("ActiveMetricType", this.activeMetricType.name());
        tag.putFloat("ActiveMetricValue", this.activeMetricValue);
        tag.putFloat("NormalizedValue", this.normalizedValue);
        tag.putBoolean("HasData", this.hasData);
        tag.putBoolean("IsStale", this.isStale);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Settings")) {
            this.settings = DataDialSettings.load(tag.getCompound("Settings"));
        }
        this.linkedSource = tag.contains("LinkedSource") ? SourceConfig.load(tag.getCompound("LinkedSource")) : null;
        this.nextAutoSourceIndex = tag.contains("NextAutoSourceIndex") ? tag.getInt("NextAutoSourceIndex") : 1;
        this.lastLinkedDataTick = tag.contains("LastLinkedDataTick") ? tag.getLong("LastLinkedDataTick") : Long.MIN_VALUE;
        try {
            this.activeSourceKind = DataDialSettings.SourceKind.valueOf(tag.getString("ActiveSourceKind"));
        } catch (Exception ignored) {
            this.activeSourceKind = DataDialSettings.SourceKind.NO_SOURCE;
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
        this.normalizedValue = tag.contains("NormalizedValue") ? tag.getFloat("NormalizedValue") : 0.0f;
        this.hasData = tag.getBoolean("HasData");
        this.isStale = tag.getBoolean("IsStale");
        this.lastComparatorLevel = this.getAnalogOutputSignal();
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

    private record LocalBinding(BlockPos pos, BlockEntity blockEntity, Direction accessFace)
    {
    }

    private record SourceReadout(String sourceType, DataDialSettings.SourceKind sourceKind, Map<String, MetricSample> metrics)
    {
    }

    private record MetricSample(String metricId, String label, float value, MetricType type)
    {
    }

    private record Evaluation(DataDialSettings.SourceKind sourceKind, @Nullable BlockPos sourcePos, String sourceType,
                              String metricId, String metricLabel, MetricType metricType,
                              float metricValue, float normalizedValue, boolean hasData, boolean stale)
    {
        private static Evaluation noData(DataDialSettings.SourceKind sourceKind, @Nullable BlockPos sourcePos, String sourceType) {
            return new Evaluation(sourceKind, sourcePos, sourceType, "", "", MetricType.GENERIC, 0.0f, 0.0f, false, false);
        }
    }
}
