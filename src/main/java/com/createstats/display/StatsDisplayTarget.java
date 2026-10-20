package com.createstats.display;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.FlowMeterBE;
import com.createstats.blockentity.InductionHeaterBE;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.content.fluids.tank.BoilerData;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.regex.Matcher;
import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.DataDialBE;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.ProductionDetectorBE;

import com.createstats.compat.SimulatedCompat;
import com.createstats.compat.SimulatedCompatHelper;
import com.createstats.compat.CreateAdditionCompat;
import com.createstats.compat.CreateAdditionCompatHelper;
import com.createstats.compat.DieselCompat;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.compat.RefinedStorageCompat;
import com.createstats.compat.RefinedStorageCompatHelper;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.data.MetricType;
import com.createstats.data.MetricParser;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.util.KineticNetworkMetrics;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.StockpileSwitchMetrics;
import com.createstats.CreateStats;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;
import java.util.regex.Pattern;
import com.simibubi.create.api.behaviour.display.DisplayTarget;

public class StatsDisplayTarget extends DisplayTarget
{
    public static final String STATS_DISPLAY_LINK_KEY = "StatsDisplayLink";
    private long lastLogTick;
    private static final Pattern TUNNEL_ITEM_PATTERN;

    private final Map<Long, Map<Long, Double>> tunnelItemAccum = new LinkedHashMap<>();

    public StatsDisplayTarget() {
        this.lastLogTick = 0L;
    }

    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        final BlockEntity targetBE = context.getTargetBlockEntity();
        final BlockPos sourcePos = context.getSourcePos();
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        final StringBuilder combined = new StringBuilder();
        for (MutableComponent component : text) {
            if (combined.length() > 0) {
                combined.append(" ");
            }
            combined.append(component.getString());
        }
        final String displayText = combined.toString().trim();
        final long gameTick = (targetBE.getLevel() != null) ? targetBE.getLevel().getGameTime() : 0L;
        if (gameTick - this.lastLogTick >= 20L) {
            this.lastLogTick = gameTick;
            CreateStats.LOGGER.debug("[DisplayTarget] line={} text='{}' target={}", new Object[] { line, displayText, targetBE.getClass().getSimpleName() });
        }
        StatsDisplayPanelBE panelTarget = null;
        StatusTowerLightBE towerLightTarget = null;
        DataDialBE dataDialTarget = null;
        MiniDisplayBE miniDisplayTarget = null;
        if (targetBE instanceof StatsDisplayPanelBE) {
            panelTarget = ((StatsDisplayPanelBE)targetBE).getControllerBE();
            final String supportedSourceType = getStatsDisplayLinkSourceType(sourceBE);
            if (!supportedSourceType.isEmpty() && !isStatsDisplayLinkSelected(context)) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.removeSafetyClutchByPosition(sourcePos);
                panelTarget.removeKineticFlywheelByPosition(sourcePos);
                panelTarget.removeAutoTransmissionClutchByPosition(sourcePos);
                panelTarget.removeLoadBalancerByPosition(sourcePos);
                panelTarget.removeMiniDisplayByPosition(sourcePos);
                panelTarget.removeFreightManifestByPosition(sourcePos);
                panelTarget.removeTrainStationByPosition(sourcePos);
                panelTarget.aerialLinks().remove(sourcePos);
                panelTarget.groundLinks().remove(sourcePos);
                panelTarget.seismicLinks().remove(sourcePos);

                panelTarget.markStatsDisplayLinkOptInRequired(
                        isGenericContainerType(supportedSourceType)
                                ? sourceBE.getBlockState().getBlock().getName().getString()
                                : supportedSourceType);
                return;
            }
            panelTarget.clearStatsDisplayLinkOptInRequired();
            if (sourceBE instanceof com.createstats.aerial.AerialTelemetrySource) {
                panelTarget.aerialLinks().heartbeat(sourcePos, gameTick);
                return;
            }
            if (sourceBE instanceof com.createstats.ground.GroundTelemetrySource) {
                panelTarget.groundLinks().heartbeat(sourcePos, gameTick);
                return;
            }

            final com.createstats.blockentity.SeismicScannerBE seismicRig = sourceBE == null
                    || sourceBE instanceof com.createstats.blockentity.SeismicScannerBE
                    ? com.createstats.blockentity.SeismicLinks.rigAt(targetBE.getLevel(), sourcePos) : null;
            if (seismicRig != null) {
                panelTarget.seismicLinks().heartbeat(seismicRig.getBlockPos(), gameTick);
                return;
            }
            if (sourceBE instanceof BattleScreenDisplayBE battleScreen) {
                panelTarget.setSourceType(BattleCompactTargetHandler.SOURCE_TYPE);

                final int selectedMode = BattleScreenDisplaySource.readMode(context);
                panelTarget.registerOrUpdateBattleScreen(sourcePos, battleScreen, selectedMode);
                return;
            }
            if (sourceBE instanceof SafetyClutchBE clutch) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateSafetyClutch(sourcePos, clutch);
                return;
            }
            if (sourceBE instanceof KineticFlywheelBE flywheel) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateKineticFlywheel(sourcePos, flywheel);
                return;
            }
            if (sourceBE instanceof AutoTransmissionClutchBE transmission) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateAutoTransmissionClutch(sourcePos, transmission);
                return;
            }
            if (sourceBE instanceof LoadBalancerBE balancer) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateLoadBalancer(sourcePos, balancer);
                return;
            }
            if (sourceBE instanceof MiniDisplayBE miniDisplay) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateMiniDisplay(sourcePos, miniDisplay);
                return;
            }
            if (sourceBE instanceof FreightManifestClipboardBE clipboard) {
                panelTarget.removeSourceByPosition(sourcePos);
                panelTarget.registerOrUpdateFreightManifest(sourcePos, clipboard);
                return;
            }
        }
        else if (targetBE instanceof StatusTowerLightBE) {
            towerLightTarget = (StatusTowerLightBE)targetBE;
            if (sourceBE instanceof BattleScreenDisplayBE battleScreen) {
                BattleCompactTargetHandler.handleForTowerLight(towerLightTarget, sourcePos, battleScreen);
                return;
            }
            final boolean hasAutoRead = this.isAutoReadSource(sourceBE) || sourceBE instanceof ProductionDetectorBE;
            if (hasAutoRead) {
                this.autoReadSource(towerLightTarget, sourcePos, context);
                if (displayText.isEmpty()) {
                    return;
                }
            }
        }
        else if (targetBE instanceof DataDialBE) {
            dataDialTarget = (DataDialBE)targetBE;
            if (CargoLinkTargetHandler.isCargoLinkSource(context)) {
                CargoLinkTargetHandler.handleForDataDial(dataDialTarget, sourcePos, sourceBE);
                return;
            }
            if (this.isDataDialAutoReadSource(sourceBE)) {
                this.autoReadSource(dataDialTarget, sourcePos, context);
            }
            return;
        }
        else if (targetBE instanceof MiniDisplayBE) {
            miniDisplayTarget = ((MiniDisplayBE)targetBE).getControllerBE();
            if (sourceBE instanceof BattleScreenDisplayBE battleScreen) {
                BattleCompactTargetHandler.handleForMiniDisplay(miniDisplayTarget, sourcePos, battleScreen);
                return;
            }
            if (CargoLinkTargetHandler.isCargoLinkSource(context)) {
                CargoLinkTargetHandler.handleForMiniDisplay(miniDisplayTarget, sourcePos, sourceBE);
                return;
            }
            if (InstrumentSourceHelper.supportsCompactAutoRead(sourceBE)) {
                this.autoReadSource(miniDisplayTarget, sourcePos, context);
            }
            return;
        }

        if (CargoLinkTargetHandler.isCargoLinkSource(context)) {
            if (panelTarget != null) {
                if (CargoLinkTargetHandler.handleForPanel(panelTarget, sourcePos, sourceBE)) {
                    return;
                }
            }
            else if (towerLightTarget != null) {
                CargoLinkTargetHandler.handleForTowerLight(towerLightTarget, sourcePos, sourceBE);
                return;
            }
            else if (targetBE instanceof StatsRecorderBE) {
                CargoLinkTargetHandler.rejectForRecorder(sourcePos);
                return;
            }
            else if (targetBE instanceof HolographicProjectorBE projector) {
                CargoLinkTargetHandler.handleForProjector(projector, sourcePos, sourceBE);
                return;
            }
        }

        if (sourceBE instanceof StationBlockEntity stationBlockEntity && panelTarget != null) {
            StatsDisplayPanelBE panel = panelTarget;
            panel.setSourceType("Train Station");
            panel.registerOrUpdateSource(sourcePos, "Train Station");
            panel.registerOrUpdateTrainStation(sourcePos, stationBlockEntity);
            panel.enableTrainConnection();
            CreateStats.LOGGER.debug("[DisplayTarget] Train station detected: {} -> enabling train connection", sourceBE.getClass().getSimpleName());
        }

        if (targetBE instanceof StatsRecorderBE recorder
                && (isStatsDisplayLinkSelected(context)
                        || com.createstats.compat.ElectroEnergeticsCompatHelper.isMeter(sourceBE))
                && InstrumentSourceHelper.supportsRichAutoRead(sourceBE)) {
            this.autoReadSource(recorder, sourcePos, context);
            return;
        }
        if (panelTarget != null && isStatsDisplayLinkSelected(context) && this.isAutoReadSource(sourceBE)) {
            this.autoReadSource(panelTarget, sourcePos, context);
            if (displayText.isEmpty()) {
                return;
            }
        }
        if (displayText.isEmpty()) {
            return;
        }
        final boolean autoReadableSource = this.isAutoReadSource(sourceBE) || sourceBE instanceof ProductionDetectorBE;
        final MetricParser.ParseResult result = MetricParser.parseWithType(displayText);
        final float value = result.value();
        if (value == 0.0f && !displayText.contains("0")) {
            if (autoReadableSource) {
                if (panelTarget != null) {
                    this.autoReadSource(panelTarget, sourcePos, context);
                } else if (towerLightTarget != null) {
                    this.autoReadSource(towerLightTarget, sourcePos, context);
                } else if (targetBE instanceof HolographicProjectorBE projector) {
                    this.autoReadSource(projector, sourcePos, context);
                }
            }
            return;
        }
        if (panelTarget != null) {
            final StatsDisplayPanelBE panel2 = panelTarget;
            final String sourceType = this.detectSourceType(sourceBE);
            MetricType finalType = result.type();
            final boolean isThroughputSource = this.isItemThroughputSource(sourceBE);
            if (isThroughputSource && finalType == MetricType.GENERIC) {
                finalType = this.detectItemThroughputMetricType(context);
            }
            final boolean hasAutoRead = autoReadableSource;
            if (!hasAutoRead) {
                panel2.onReceiveData(sourcePos, sourceType, line, value, finalType, displayText);
                if (sourceBE instanceof ProductionDetectorBE detector) {
                    this.applyProductionDetectorChannelLabel(panel2, sourcePos, line, detector, displayText);
                }
                if (isThroughputSource && !displayText.isEmpty()) {
                    final String itemName = this.resolveItemThroughputName(sourceBE, displayText);
                    if (itemName != null && !itemName.isEmpty()) {
                        final SourceConfig src = panel2.registerOrUpdateSource(sourcePos, sourceType);
                        final String channelId = "src_" + src.getSourceId() + "_line" + line;
                        final StatsDisplayPanelBE.DataChannel ch = panel2.getChannel(channelId);
                        if (ch != null && !ch.getLabel().equals(itemName)) {
                            ch.setLabel(itemName);
                        }
                    }
                }
                if ("Tunnel".equals(sourceType) && isThroughputSource && value > 0.0f) {
                    double increment = 0.0;
                    if (finalType == MetricType.ITEMS_PER_MIN) {
                        increment = value / 60.0;
                    } else if (finalType == MetricType.ITEMS_PER_SEC) {
                        increment = value * 0.1;
                    } else if (finalType == MetricType.ITEMS_PER_HR) {
                        increment = value / 360.0;
                    }
                    if (increment > 0.0) {
                        final long panelPosL = panel2.getBlockPos().asLong();
                        final long srcPosL = sourcePos.asLong();
                        final double newTotal = tunnelItemAccum
                                .computeIfAbsent(panelPosL, k -> new LinkedHashMap<>())
                                .merge(srcPosL, increment, Double::sum);
                        panel2.onAutoData(sourcePos, sourceType, "total_items", "Passed Items", (float) newTotal, MetricType.ITEM_COUNT);
                    }
                }
            }
            this.autoReadSource(panel2, sourcePos, context);
        }
        else if (targetBE instanceof StatsRecorderBE recorder) {
            recorder.onReceiveData(value, result.type(), displayText);
        }
        else if (towerLightTarget != null) {
            final String sourceType = this.detectSourceType(sourceBE);
            MetricType finalType = result.type();
            final boolean isThroughputSource = this.isItemThroughputSource(sourceBE);
            if (isThroughputSource && finalType == MetricType.GENERIC) {
                finalType = this.detectItemThroughputMetricType(context);
            }
            final boolean hasAutoRead = autoReadableSource;
            if (!hasAutoRead) {
                towerLightTarget.onReceiveData(sourcePos, sourceType, line, value, finalType, displayText);
            }
        }
        else if (targetBE instanceof HolographicProjectorBE projector) {
            final String sourceType = this.detectSourceType(context.getSourceBlockEntity());
            MetricType finalType = result.type();
            final boolean isThroughputSource = this.isItemThroughputSource(context.getSourceBlockEntity());
            if (isThroughputSource && finalType == MetricType.GENERIC) {
                finalType = this.detectItemThroughputMetricType(context);
            }
            final boolean hasAutoRead = autoReadableSource;
            if (!hasAutoRead) {
                projector.onReceiveData(sourcePos, sourceType, line, value, finalType, displayText);
            }
            this.autoReadSource(projector, sourcePos, context);
        }
    }

    public static boolean isStatsDisplayLinkSelected(@Nullable DisplayLinkContext context) {
        if (context != null && context.blockEntity() != null && context.blockEntity().activeSource instanceof StatsLinkSource) {
            return true;
        }
        return isLegacyStatsDisplayLinkEnabled((context != null) ? context.sourceConfig() : null);
    }

    private static boolean isLegacyStatsDisplayLinkEnabled(@Nullable CompoundTag sourceConfig) {
        if (sourceConfig == null) {
            return false;
        }
        if (sourceConfig.contains(STATS_DISPLAY_LINK_KEY, 3)) {
            return sourceConfig.getInt(STATS_DISPLAY_LINK_KEY) != 0;
        }
        if (sourceConfig.contains(STATS_DISPLAY_LINK_KEY, 1)) {
            return sourceConfig.getBoolean(STATS_DISPLAY_LINK_KEY);
        }
        return false;
    }

    public static boolean supportsStatsDisplayLink(@Nullable BlockEntity sourceBE) {
        return !getStatsDisplayLinkSourceType(sourceBE).isEmpty();
    }

    public static String getStatsDisplayLinkSourceType(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return "";
        }
        if (sourceBE instanceof StressGaugeBlockEntity) {
            return "Stressometer";
        }
        if (sourceBE instanceof SpeedGaugeBlockEntity) {
            return "Speedometer";
        }
        if (sourceBE instanceof ThresholdSwitchBlockEntity) {
            return "Stockpile Switch";
        }
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final BoilerData boiler = tank.boiler;
            return (boiler != null && boiler.isActive()) ? "Steam Boiler" : "Fluid Tank";
        }
        if (sourceBE instanceof SmartObserverBlockEntity) {
            return "Content Observer";
        }
        if (sourceBE instanceof ProductionDetectorBE) {
            return "Production Detector";
        }
        if (sourceBE instanceof BattleScreenDisplayBE) {
            return BattleCompactTargetHandler.SOURCE_TYPE;
        }
        if (sourceBE instanceof com.createstats.aerial.AerialTelemetrySource) {
            return com.createstats.blockentity.AerialLinks.SOURCE_TYPE;
        }
        if (sourceBE instanceof com.createstats.ground.GroundTelemetrySource) {
            return com.createstats.blockentity.GroundLinks.SOURCE_TYPE;
        }
        if (sourceBE instanceof com.createstats.blockentity.SeismicScannerBE) {
            return com.createstats.blockentity.SeismicLinks.SOURCE_TYPE;
        }
        if (sourceBE instanceof SafetyClutchBE) {
            return "Safety Clutch";
        }
        if (sourceBE instanceof AutoTransmissionClutchBE) {
            return "Auto Variable Clutch";
        }
        if (sourceBE instanceof KineticFlywheelBE) {
            return "Kinetic Flywheel";
        }
        if (sourceBE instanceof FlowMeterBE) {
            return "Flow Meter";
        }
        if (sourceBE instanceof LoadBalancerBE) {
            return "Load Balancer";
        }
        if (sourceBE instanceof MiniDisplayBE) {
            return "Mini Display";
        }
        if (sourceBE instanceof FreightManifestClipboardBE) {
            return "Freight Manifest";
        }
        if (sourceBE instanceof DispatchBoardBE) {
            return "Dispatch Board";
        }
        // name our own hardware. the fall-through returns a translated name and breaks in every other language

        if (sourceBE instanceof PowerDistributionUnitBE) {
            return "Power Distribution Unit";
        }
        if (sourceBE instanceof BatteryControllerBE) {
            return "FE Battery Controller";
        }
        if (sourceBE instanceof PowerConnectorBE) {
            return "Power Connector";
        }
        if (sourceBE instanceof CapacitorBankBE) {
            return "Capacitor Bank";
        }
        if (sourceBE instanceof KineticDynamoBE) {
            return "Kinetic Dynamo";
        }

        if (sourceBE instanceof com.createstats.blockentity.FeMotorBE) {
            return "FE Motor";
        }
        if (sourceBE instanceof com.createstats.blockentity.PowerMeterBE) {
            return "Power Meter";
        }
        if (sourceBE instanceof com.createstats.blockentity.PowerLimiterBE) {
            return "Power Limiter";
        }
        // before the energy fall-through, RS controllers have FE and would get filed under a translated name

        if (RefinedStorageCompat.isAvailable()) {
            final String rsSourceType = RefinedStorageCompatHelper.getSourceType(sourceBE);
            if (!rsSourceType.isEmpty()) {
                return rsSourceType;
            }
        }
        if (SimulatedCompat.isAvailable()) {
            final String compatSourceType = SimulatedCompatHelper.getSourceType(sourceBE);
            if (!compatSourceType.isEmpty()) {
                return compatSourceType;
            }
        }
        if (CreateAdditionCompat.isAvailable()) {
            final String createAdditionSourceType = CreateAdditionCompatHelper.getSourceType(sourceBE);
            if (!createAdditionSourceType.isEmpty()) {
                return createAdditionSourceType;
            }
        }
        if (DieselCompat.isAvailable()) {
            final String dieselSourceType = DieselCompatHelper.getSourceType(sourceBE);
            if (!dieselSourceType.isEmpty()) {
                return dieselSourceType;
            }
        }
        if (com.createstats.compat.NewAgeCompat.isAvailable()) {
            final String newAgeSourceType =
                    com.createstats.compat.NewAgeCompatHelper.getSourceType(sourceBE);
            if (!newAgeSourceType.isEmpty()) {
                return newAgeSourceType;
            }
        }
        if (sourceBE instanceof InductionHeaterBE) {
            return "Induction Heater";
        }
        if (InstrumentSourceHelper.hasEnergyStorageCapability(sourceBE)) {
            return sourceBE.getBlockState().getBlock().getName().getString();
        }
        final String className = sourceBE.getClass().getSimpleName();
        if (className.contains("Tunnel") || className.contains("Chute")) {
            return "Tunnel";
        }
        if (className.contains("Station")) {
            return "Train Station";
        }
        if (className.contains("FactoryPanel")) {
            return "Factory Panel";
        }
        // last resort: a link straight at the box that holds the stuff

        if (holdsSomething(sourceBE)) {
            return CONTAINER_SOURCE_TYPE;
        }
        return "";
    }

    public static final String CONTAINER_SOURCE_TYPE = "Container";

    public static boolean holdsSomething(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null || sourceBE.getLevel() == null) {
            return false;
        }
        final Level level = sourceBE.getLevel();
        final BlockPos pos = sourceBE.getBlockPos();
        if (com.createstats.util.InventoryLookupHelper.resolveItemHandler(level, pos, sourceBE, null)
                != null) {
            return true;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
    }

    public static boolean isGenericContainerType(String sourceType) {
        return CONTAINER_SOURCE_TYPE.equals(sourceType);
    }

    private static boolean isPlainContainer(@Nullable BlockEntity sourceBE) {
        return CONTAINER_SOURCE_TYPE.equals(getStatsDisplayLinkSourceType(sourceBE));
    }

    private String detectSourceType(BlockEntity sourceBE) {
        final String supportedSourceType = getStatsDisplayLinkSourceType(sourceBE);
        if (!supportedSourceType.isEmpty()) {
            return supportedSourceType;
        }
        if (sourceBE == null) {
            return "";
        }

        final String meter = com.createstats.compat.ElectroEnergeticsCompatHelper.getSourceType(sourceBE);
        if (!meter.isEmpty()) {
            return meter;
        }
        final String className = sourceBE.getClass().getSimpleName();
        return className.replace("BlockEntity", "");
    }

    private boolean isAutoReadSource(BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        if (sourceBE instanceof FlowMeterBE) {
            return true;
        }
        if (InstrumentSourceHelper.supportsRichAutoRead(sourceBE)) {
            return true;
        }
        if (isPlainContainer(sourceBE)) {
            return true;
        }

        if (RefinedStorageCompat.isAvailable()
                && RefinedStorageCompatHelper.isNetworkBlockEntity(sourceBE)) {
            return true;
        }
        final String className = sourceBE.getClass().getSimpleName();
        return className.contains("StressGauge") || className.contains("SpeedGauge") || className.contains("ThresholdSwitch") || className.contains("FluidTank") || className.contains("SmartObserver") || className.contains("Station") || className.contains("FactoryPanel");
    }

    private boolean isDataDialAutoReadSource(BlockEntity sourceBE) {
        return InstrumentSourceHelper.supportsCompactAutoRead(sourceBE);
    }

    private void autoReadSource(StatsDisplayPanelBE panel, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            if (DieselCompat.isAvailable() && DieselCompatHelper.emitAutoData(panel, sourcePos, null, context.sourceConfig(), panel.getLevel())) {
                return;
            }
            return;
        }
        try {
            this.autoReadStressGauge(panel, sourcePos, sourceBE);
            this.autoReadSpeedGauge(panel, sourcePos, sourceBE);
            this.autoReadThresholdSwitch(panel, sourcePos, sourceBE);
            this.autoReadBoiler(panel, sourcePos, sourceBE);
            this.autoReadContentObserver(panel, sourcePos, sourceBE);
            this.autoReadContainer(panel, sourcePos, sourceBE);
            this.autoReadProductionDetector(panel, sourcePos, sourceBE);
            this.autoReadTrainStation(panel, sourcePos, sourceBE);
            this.autoReadFactoryPanel(panel, sourcePos, sourceBE);
            this.autoReadFlowMeter(panel, sourcePos, sourceBE);
            this.autoReadInductionHeater(panel, sourcePos, sourceBE);
            this.autoReadTopologyScanner(panel, sourcePos, sourceBE);

            if (com.createstats.rs.RsLinkReader.emit(panel, sourcePos, sourceBE)) {
                return;
            }
            this.autoReadCompat(panel, sourcePos, sourceBE, context.sourceConfig());
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Auto-read failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadSource(DataDialBE dial, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            return;
        }
        try {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null);
            dial.replaceAutoData(sourcePos, readout.sourceType(), readout.metrics());
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Data dial auto-read failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadSource(MiniDisplayBE miniDisplay, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            return;
        }
        try {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null, miniDisplay.getTrackedFilterItems());
            miniDisplay.replaceAutoData(sourcePos, readout.sourceType(), readout.metrics());
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Mini display auto-read failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadSource(StatsRecorderBE recorder, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            return;
        }
        try {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null);
            recorder.onReceiveAutoData(sourcePos, this.detectSourceType(sourceBE), readout);
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Recorder auto-read failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadSource(HolographicProjectorBE projector, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            return;
        }
        try {
            this.autoReadStressGauge(projector, sourcePos, sourceBE);
            this.autoReadSpeedGauge(projector, sourcePos, sourceBE);
            this.autoReadThresholdSwitch(projector, sourcePos, sourceBE);
            this.autoReadBoiler(projector, sourcePos, sourceBE);
            this.autoReadContentObserver(projector, sourcePos, sourceBE);
            this.autoReadTrainStation(projector, sourcePos, sourceBE);
            this.autoReadCompat(projector, sourcePos, sourceBE, context.sourceConfig());
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Auto-read (projector) failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadSource(StatusTowerLightBE towerLight, BlockPos sourcePos, DisplayLinkContext context) {
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE == null) {
            return;
        }
        try {
            this.autoReadStressGauge(towerLight, sourcePos, sourceBE);
            this.autoReadSpeedGauge(towerLight, sourcePos, sourceBE);
            this.autoReadThresholdSwitch(towerLight, sourcePos, sourceBE);
            this.autoReadBoiler(towerLight, sourcePos, sourceBE);
            this.autoReadContentObserver(towerLight, sourcePos, sourceBE);
            this.autoReadProductionDetector(towerLight, sourcePos, sourceBE);
            this.autoReadTrainStation(towerLight, sourcePos, sourceBE);
            this.autoReadFactoryPanel(towerLight, sourcePos, sourceBE);
            this.autoReadCompat(towerLight, sourcePos, sourceBE, context.sourceConfig());
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Auto-read (tower light) failed for {}: {}", sourceBE.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void autoReadCompat(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE, @Nullable CompoundTag sourceConfig) {
        if (com.createstats.rs.RsLinkReader.emit(receiver, sourcePos, sourceBE)) {
            return;
        }

        if (com.createstats.compat.ElectroEnergeticsCompat.isAvailable()
                && com.createstats.compat.ElectroEnergeticsCompatHelper.emitAutoData(receiver, sourcePos, sourceBE)) {
            return;
        }
        if (CreateAdditionCompat.isAvailable() && CreateAdditionCompatHelper.emitAutoData(receiver, sourcePos, sourceBE)) {
            return;
        }
        if (com.createstats.compat.NewAgeCompat.isAvailable()
                && com.createstats.compat.NewAgeCompatHelper.emitAutoData(receiver, sourcePos, sourceBE)) {
            return;
        }
        if (SimulatedCompat.isAvailable() && SimulatedCompatHelper.emitAutoData(receiver, sourcePos, sourceBE)) {
            return;
        }
        if (DieselCompat.isAvailable() && DieselCompatHelper.emitAutoData(receiver, sourcePos, sourceBE, sourceConfig)) {
            return;
        }
        final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null);

        final InstrumentSourceHelper.SourceKind kind = readout.sourceKind();
        final boolean energyLike = kind == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE
                || kind == InstrumentSourceHelper.SourceKind.POWER_GRID;
        if (energyLike && !readout.metrics().isEmpty()) {
            CreateAdditionCompatHelper.emitReadout(receiver, sourcePos, readout);
        }
    }

    private boolean isItemThroughputSource(BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        if (SimulatedCompat.isAvailable() && SimulatedCompatHelper.isItemThroughputSource(sourceBE)) {
            return true;
        }
        final String name = sourceBE.getClass().getSimpleName();
        return name.contains("Tunnel") || name.contains("Chute");
    }

    private MetricType detectItemThroughputMetricType(DisplayLinkContext context) {
        try {
            final int interval = context.sourceConfig().getInt("Interval");
            return switch (interval) {
                case 0 -> MetricType.ITEMS_PER_SEC;
                case 2 -> MetricType.ITEMS_PER_HR;
                default -> MetricType.ITEMS_PER_MIN;
            };
        }
        catch (Exception e) {
            return MetricType.ITEMS_PER_MIN;
        }
    }

    private String parseItemThroughputName(String text) {
        final String cleaned = text.replaceAll("§[0-9a-fk-or]", "").trim();
        final Matcher m = StatsDisplayTarget.TUNNEL_ITEM_PATTERN.matcher(cleaned);
        if (!m.matches()) {
            return null;
        }
        final String name = m.group(1).trim();
        if (name.equalsIgnoreCase("items") || name.equalsIgnoreCase("item") || name.equalsIgnoreCase("total") || name.isEmpty()) {
            return null;
        }
        return name;
    }

    @Nullable
    private String resolveItemThroughputName(@Nullable BlockEntity sourceBE, String text) {
        if (SimulatedCompat.isAvailable()) {
            final ItemStack compatStack = SimulatedCompatHelper.resolveItemThroughputDisplayStack(sourceBE);
            if (!compatStack.isEmpty()) {
                return compatStack.getHoverName().getString();
            }
        }
        final ItemStack liveStack = this.getTunnelLiveStack(sourceBE);
        if (!liveStack.isEmpty()) {
            return liveStack.getHoverName().getString();
        }
        final ItemStack filterStack = this.getTunnelFilterStack(sourceBE);
        if (!filterStack.isEmpty()) {
            return filterStack.getHoverName().getString();
        }
        return this.parseItemThroughputName(text);
    }

    private ItemStack getTunnelLiveStack(@Nullable BlockEntity sourceBE) {
        if (sourceBE instanceof BrassTunnelBlockEntity brass) {
            final ItemStack distributed = brass.getStackToDistribute();
            if (!distributed.isEmpty()) {
                return distributed;
            }
        }
        return this.findFirstHandlerStack(sourceBE);
    }

    private ItemStack getTunnelFilterStack(@Nullable BlockEntity sourceBE) {
        if (!(sourceBE instanceof SmartBlockEntity smart)) {
            return ItemStack.EMPTY;
        }
        for (BlockEntityBehaviour behaviour : smart.getAllBehaviours()) {
            if (behaviour instanceof SidedFilteringBehaviour sided) {
                for (Direction direction : Direction.values()) {
                    final ItemStack filter = sided.getFilter(direction);
                    if (!filter.isEmpty()) {
                        return filter;
                    }
                }
            }
            if (behaviour instanceof FilteringBehaviour filtering) {
                final ItemStack filter = filtering.getFilter();
                if (!filter.isEmpty()) {
                    return filter;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findFirstHandlerStack(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return ItemStack.EMPTY;
        }
        final IItemHandler handler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                sourceBE.getLevel(), sourceBE.getBlockPos(), sourceBE, null);
        if (handler != null) {
            final ItemStack stack = this.findFirstHandlerStack(handler);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        for (Direction direction : Direction.values()) {
            final IItemHandler sided = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                    sourceBE.getLevel(), sourceBE.getBlockPos(), sourceBE, direction);
            if (sided == null) {
                continue;
            }
            final ItemStack stack = this.findFirstHandlerStack(sided);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findFirstHandlerStack(@Nullable IItemHandler handler) {
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void applyProductionDetectorChannelLabel(StatsDisplayPanelBE panel, BlockPos sourcePos, int line, ProductionDetectorBE detector, String rawText) {
        final SourceConfig src = panel.registerOrUpdateSource(sourcePos, "Production Detector");
        final String channelId = "src_" + src.getSourceId() + "_line" + line;
        final StatsDisplayPanelBE.DataChannel channel = panel.getChannel(channelId);
        if (channel == null) {
            return;
        }
        final String label = this.resolveProductionDetectorChannelLabel(detector, rawText);
        if (label != null && !label.isEmpty() && !label.equals(channel.getLabel())) {
            channel.setLabel(label);
        }
    }

    @Nullable
    private String resolveProductionDetectorChannelLabel(ProductionDetectorBE detector, String rawText) {
        final String cleaned = this.stripFormatting(rawText);
        if (cleaned.isEmpty()) {
            return null;
        }
        final String liveRateLabel = this.extractProductionDetectorRateLabel(cleaned);
        if (liveRateLabel != null) {
            return liveRateLabel;
        }
        final String prefix = this.extractTextPrefix(cleaned);
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        if (prefix.equalsIgnoreCase("Seen")) {
            return "Total Seen";
        }
        if (prefix.equalsIgnoreCase("Types")) {
            return "Tracked Types";
        }
        final String summary = detector.getTrackingSummaryLabel();
        final boolean hasSummary = summary != null && !summary.isEmpty() && !"No target".equals(summary) && !"Unknown target".equals(summary);
        if (prefix.equalsIgnoreCase("Rate")) {
            return hasSummary ? ("Throughput • " + summary) : "Throughput";
        }
        if (prefix.equalsIgnoreCase("Total")) {
            return hasSummary ? ("Total • " + summary) : "Total";
        }
        return prefix;
    }

    @Nullable
    private String extractTextPrefix(String text) {
        final String cleaned = this.stripFormatting(text);
        final int idx = cleaned.indexOf(58);
        if (idx <= 0) {
            return null;
        }
        final String prefix = cleaned.substring(0, idx).trim();
        return prefix.isEmpty() ? null : prefix;
    }

    private String stripFormatting(String text) {
        return (text != null) ? text.replaceAll("§[0-9a-fk-or]", "").trim() : "";
    }

    @Nullable
    private String extractProductionDetectorRateLabel(String cleanedText) {
        if (!cleanedText.regionMatches(true, 0, "Rate:", 0, 5)) {
            return null;
        }
        final int bulletIndex = cleanedText.lastIndexOf('\u2022');
        if (bulletIndex < 0 || bulletIndex + 1 >= cleanedText.length()) {
            return null;
        }
        final String suffix = cleanedText.substring(bulletIndex + 1).trim();
        return suffix.isEmpty() ? null : ("Throughput • " + suffix);
    }

    private void autoReadStressGauge(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            panel.setSourceType("Stressometer");
            final float stressPct = gauge.dialTarget * 100.0f;
            panel.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", stressPct, MetricType.STRESS_PERCENT);
            final float networkStress = gauge.getNetworkStress();
            panel.onAutoData(sourcePos, "Stressometer", "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
            final float networkCapacity = gauge.getNetworkCapacity();
            panel.onAutoData(sourcePos, "Stressometer", "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
            final float remaining = networkCapacity - networkStress;
            panel.onAutoData(sourcePos, "Stressometer", "remaining_su", "Remaining (SU)", Math.max(0.0f, remaining), MetricType.STRESS_SU);
            final float rpm = Math.abs(gauge.getSpeed());
            panel.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", rpm, MetricType.RPM);
            final boolean overstressed = gauge.isOverStressed();
            panel.onAutoData(sourcePos, "Stressometer", "overstressed", "Overstressed", overstressed ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadFlowMeter(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof FlowMeterBE meter) {
            panel.setSourceType("Flow Meter");
            panel.onAutoData(sourcePos, "Flow Meter", "flow_rate_mbt", "Flow Rate", (float) meter.getFlowRateMbPerTick(), MetricType.FLUID_MB_PER_TICK);
            panel.onAutoData(sourcePos, "Flow Meter", "flow_total_mb", "Total Moved", (float) meter.getTotalMovedMb(), MetricType.FLUID_MB);
            panel.onAutoData(sourcePos, "Flow Meter", "flow_direction", "Direction", (float) meter.getFlowDirectionSign(), MetricType.GENERIC);
            panel.onAutoData(sourcePos, "Flow Meter", "fluid_id", meter.getFluidId(), 0.0f, MetricType.GENERIC);
        }
    }

    private void autoReadSpeedGauge(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            panel.setSourceType("Speedometer");
            final float rpm = gauge.getSpeed();
            panel.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", rpm, MetricType.RPM);
            final float absRpm = Math.abs(rpm);
            panel.onAutoData(sourcePos, "Speedometer", "abs_rpm", "Speed (abs)", absRpm, MetricType.RPM);
            final float dialPct = gauge.dialTarget * 100.0f;
            panel.onAutoData(sourcePos, "Speedometer", "speed_pct", "Speed %", dialPct, MetricType.GENERIC);
        }
    }

    private void autoReadThresholdSwitch(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof ThresholdSwitchBlockEntity tsw) {
            panel.setSourceType("Stockpile Switch");
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(tsw);
            final MetricType amountType = readout.amountMetricType();
            panel.onAutoData(sourcePos, "Stockpile Switch", "fill_pct", "Fill Level %", readout.fillPercent(), MetricType.FILL_LEVEL);
            panel.onAutoData(sourcePos, "Stockpile Switch", "stock_level", "Current Amount", readout.currentAmount(), amountType);
            panel.onAutoData(sourcePos, "Stockpile Switch", "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
            panel.onAutoData(sourcePos, "Stockpile Switch", "min_level", "Minimum Level", readout.minAmount(), amountType);
            panel.onAutoData(sourcePos, "Stockpile Switch", "upper_thresh", "Upper Threshold", readout.upperThreshold(), amountType);
            panel.onAutoData(sourcePos, "Stockpile Switch", "lower_thresh", "Lower Threshold", readout.lowerThreshold(), amountType);
            final boolean powered = tsw.isPowered();
            panel.onAutoData(sourcePos, "Stockpile Switch", "powered", "Signal Output", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadBoiler(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                tank = controller;
            }
            String srcType = "Fluid Tank";
            final float fillPct = tank.getFillState() * 100.0f;
            panel.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", fillPct, MetricType.FILL_LEVEL);
            final FluidTank tankInv = tank.getTankInventory();
            if (tankInv != null) {
                final float fluidAmount = (float)tankInv.getFluidAmount();
                panel.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", fluidAmount, MetricType.FLUID_MB);
                final float tankCapacity = (float)tankInv.getCapacity();
                panel.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", tankCapacity, MetricType.FLUID_MB);
            }
            final BoilerData boiler = tank.boiler;
            if (boiler != null && boiler.isActive()) {
                srcType = "Steam Boiler";
                panel.setSourceType("Steam Boiler");
                final int heatLevel = boiler.activeHeat;
                panel.onAutoData(sourcePos, srcType, "heat_level", "Heat Level", (float)heatLevel, MetricType.HEAT_LEVEL);
                final int theoreticalHeat = boiler.getTheoreticalHeatLevel();
                panel.onAutoData(sourcePos, srcType, "theoretical_heat", "Max Heat Capacity", (float)theoreticalHeat, MetricType.HEAT_LEVEL);
                final float waterSupply = boiler.waterSupply;
                panel.onAutoData(sourcePos, srcType, "water_supply", "Water Supply", waterSupply, MetricType.WATER_SUPPLY);
                final int engines = boiler.attachedEngines;
                panel.onAutoData(sourcePos, srcType, "engines", "Engines", (float)engines, MetricType.ENGINE_COUNT);
                final int whistles = boiler.attachedWhistles;
                panel.onAutoData(sourcePos, srcType, "whistles", "Whistles", (float)whistles, MetricType.GENERIC);
                final float gaugeVal = boiler.gauge.getValue(1.0f) * 100.0f;
                panel.onAutoData(sourcePos, srcType, "boiler_gauge", "Boiler Gauge %", gaugeVal, MetricType.FILL_LEVEL);
                final boolean passive = boiler.passiveHeat;
                panel.onAutoData(sourcePos, srcType, "passive_heat", "Passive Heat", passive ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            }
            else {
                panel.setSourceType("Fluid Tank");
                this.recordObservedTarget(panel, sourcePos, srcType, tank.getLevel(), tank.getBlockPos());
            }
        }
    }

    private void autoReadInductionHeater(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof InductionHeaterBE heater)) {
            return;
        }
        final String srcType = "Induction Heater";
        panel.setSourceType(srcType);
        panel.registerOrUpdateSource(sourcePos, srcType);
        panel.onAutoData(sourcePos, srcType, "heat_level", "Heat Level", (float) heater.getHeatTierOrdinal(), MetricType.HEAT_LEVEL);
        panel.onAutoData(sourcePos, srcType, "charge_pct", "Charge %", heater.getChargePercent(), MetricType.FILL_LEVEL);
        panel.onAutoData(sourcePos, srcType, "draw_su", "SU Draw", heater.getCurrentDrawSu(), MetricType.STRESS_SU);
        panel.onAutoData(sourcePos, srcType, "rpm", "RPM", Math.abs(heater.getSpeed()), MetricType.RPM);
        panel.onAutoData(sourcePos, srcType, "coil_present", "Coil", heater.hasCoil() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        panel.onAutoData(sourcePos, srcType, "coil_health_pct", "Coil Health", heater.getCoilHealthPercent(), MetricType.FILL_LEVEL);
        panel.onAutoData(sourcePos, srcType, "coil_seconds", "Coil Time", heater.getCoilSecondsRemaining(), MetricType.GENERIC);
        panel.onAutoData(sourcePos, srcType, "cooking", "Cooking", heater.hasCookStack() && !heater.isCookDone() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        panel.onAutoData(sourcePos, srcType, "cook_pct", "Cook %", heater.getCookProgressPercent(), MetricType.FILL_LEVEL);
        panel.onAutoData(sourcePos, srcType, "cook_done", "Ready", heater.isCookDone() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        panel.onAutoData(sourcePos, srcType, "cook_seconds", "Cook Time", heater.getCookSecondsRemaining(), MetricType.GENERIC);
        final ItemStack cook = heater.getCookStack();
        panel.onAutoData(sourcePos, srcType, "cook_item", cook.isEmpty() ? "Empty" : cook.getHoverName().getString(),
                cook.getCount(), MetricType.ITEM_COUNT);
    }

    private void autoReadTopologyScanner(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof com.createstats.netscan.NetscanHost host)) {
            return;
        }
        final String srcType = com.createstats.netscan.NetscanLink.SOURCE_TYPE;
        panel.setSourceType(srcType);
        panel.registerOrUpdateSource(sourcePos, srcType);
        final com.createstats.netscan.scan.ScanGraph graph = host.scanEngine().graph();
        panel.onAutoData(sourcePos, srcType, "elements", "Elements Surveyed", graph.size(), MetricType.GENERIC);
        panel.onAutoData(sourcePos, srcType, "sources", "Sources", graph.sourceCount(), MetricType.GENERIC);
        panel.onAutoData(sourcePos, srcType, "machines", "Machines", graph.machineCount(), MetricType.GENERIC);
        panel.onAutoData(sourcePos, srcType, "stress_su", "Current Stress (SU)", graph.totalStress(), MetricType.STRESS_SU);
        panel.onAutoData(sourcePos, srcType, "capacity_su", "Capacity (SU)", graph.totalCapacity(), MetricType.STRESS_SU);
        panel.onAutoData(sourcePos, srcType, "stress_pct", "Stress %", graph.loadFraction() * 100.0f, MetricType.STRESS_PERCENT);
        panel.onAutoData(sourcePos, srcType, "overstressed", "Overstressed", graph.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        panel.onAutoData(sourcePos, srcType, "truncated", "Survey Truncated", graph.truncated() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    private void autoReadContentObserver(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof SmartObserverBlockEntity)) {
            return;
        }
        final SmartObserverBlockEntity observer = (SmartObserverBlockEntity)sourceBE;
        panel.setSourceType("Content Observer");
        final String srcType = "Content Observer";
        final BlockState observerState = observer.getBlockState();
        final Direction facing = DirectedDirectionalBlock.getTargetDirection(observerState);
        if (facing == null) {
            return;
        }
        final BlockPos observedPos = observer.getBlockPos().relative(facing);
        final Level level = observer.getLevel();
        if (level == null) {
            return;
        }
        final BlockEntity observedBE = level.getBlockEntity(observedPos);
        if (observedBE == null) {
            return;
        }
        final SourceConfig observerConfig = panel.registerOrUpdateSource(sourcePos, srcType);
        if (observerConfig != null) {
            final String observedLabel = level.getBlockState(observedPos).getBlock().getName().getString();
            if (observerConfig.setObservedTarget(observedPos, observedLabel == null || observedLabel.isEmpty() ? "Inventory" : observedLabel)) {
                panel.markDirtyAndSync();
            }
        }
        final IItemHandler handler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                level, observedPos, observedBE, null);
        boolean readAnything = false;
        if (handler != null) {
            this.readItemHandler(panel, sourcePos, srcType, handler);
            readAnything = true;
        }
        if (observedBE instanceof FluidTankBlockEntity observedTank) {
            final FluidTankBlockEntity controller = observedTank.getControllerBE();
            final FluidTankBlockEntity tank = controller != null ? controller : observedTank;
            this.recordObservedTarget(panel, sourcePos, srcType, level, tank.getBlockPos());
            if (DieselCompat.isAvailable()) {
                DieselCompatHelper.emitAutoData(panel, tank.getBlockPos(), tank, null);
            }
            final FluidTank tankInv = tank.getTankInventory();
            if (tankInv != null) {
                panel.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
                panel.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", (float)tankInv.getFluidAmount(), MetricType.FLUID_MB);
                panel.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", (float)tankInv.getCapacity(), MetricType.FLUID_MB);
                final FluidStack stack = tankInv.getFluid();
                if (!stack.isEmpty()) {
                    final String fluidName = stack.getHoverName().getString();
                    panel.onAutoData(sourcePos, srcType, "fluid_" + this.sanitizeId(fluidName), fluidName + " (mB)", (float)stack.getAmount(), MetricType.FLUID_MB);
                }
                readAnything = true;
            }
        } else {
            final IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, observedPos, null);
            if (fluidHandler != null) {
                float totalFluid = 0.0f;
                float totalCapacity = 0.0f;
                for (int t = 0; t < fluidHandler.getTanks(); ++t) {
                    totalCapacity += Math.max(0.0f, (float)fluidHandler.getTankCapacity(t));
                    final FluidStack stack = fluidHandler.getFluidInTank(t);
                    if (!stack.isEmpty()) {
                        final String fluidName = stack.getHoverName().getString();
                        final String metricId = "fluid_" + this.sanitizeId(fluidName);
                        final float amount = (float)stack.getAmount();
                        totalFluid += amount;
                        panel.onAutoData(sourcePos, srcType, metricId, fluidName + " (mB)", amount, MetricType.FLUID_MB);
                        readAnything = true;
                    }
                }
                if (totalCapacity > 0.0f || totalFluid > 0.0f) {
                    panel.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
                    panel.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
                    panel.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f, MetricType.FILL_LEVEL);
                    if (DieselCompat.isAvailable()) {
                        DieselCompatHelper.emitAutoData(panel, observedPos, observedBE, null);
                    }
                    this.recordObservedTarget(panel, sourcePos, srcType, level, observedPos);
                    readAnything = true;
                }
            }
        }

        if (!readAnything) {
            ItemStack filter = this.getTunnelFilterStack(observedBE);
            if (filter.isEmpty()) {
                filter = this.getTunnelFilterStack(observer);
            }
            if (filter.isEmpty()) {
                final BlockPos belowObserved = observedPos.below();
                final BlockEntity beltBE = level.getBlockEntity(belowObserved);
                if (beltBE != null) {
                    filter = this.getTunnelFilterStack(beltBE);
                    final IItemHandler beltHandler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                            level, belowObserved, beltBE, null);
                    if (beltHandler != null) {
                        this.readItemHandler(panel, sourcePos, srcType, beltHandler);
                    }
                }
            }
            if (!filter.isEmpty()) {
                final String filterName = filter.getHoverName().getString();
                final String registryName = BuiltInRegistries.ITEM.getKey(filter.getItem()).toString();
                final String metricId = "observer_filter_" + this.sanitizeId(registryName);
                panel.onAutoData(sourcePos, srcType, metricId, "Filter: " + filterName, (float)Math.max(1, filter.getCount()), MetricType.ITEM_COUNT);
            }
            panel.onAutoData(sourcePos, srcType, "observer_active", "Observer Active", 1.0f, MetricType.ITEM_COUNT);
        }
    }

    private void autoReadContainer(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!isPlainContainer(sourceBE)) {
            return;
        }
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return;
        }
        final BlockPos pos = sourceBE.getBlockPos();
        panel.setSourceType(CONTAINER_SOURCE_TYPE);
        this.recordObservedTarget(panel, sourcePos, CONTAINER_SOURCE_TYPE, level, pos);

        final IItemHandler handler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                level, pos, sourceBE, null);
        if (handler != null) {
            this.readItemHandler(panel, sourcePos, CONTAINER_SOURCE_TYPE, handler);
        }
        final IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluids != null) {
            this.readFluidHandler(panel, sourcePos, CONTAINER_SOURCE_TYPE, fluids);
        }
    }

    private void readFluidHandler(StatsDisplayPanelBE panel, BlockPos sourcePos, String srcType,
                                  IFluidHandler handler) {
        float totalFluid = 0.0f;
        float totalCapacity = 0.0f;
        for (int tank = 0; tank < handler.getTanks(); ++tank) {
            totalCapacity += Math.max(0.0f, (float) handler.getTankCapacity(tank));
            final FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }
            totalFluid += stack.getAmount();
            final String fluidName = stack.getHoverName().getString();
            panel.onAutoData(sourcePos, srcType, "fluid_" + this.sanitizeId(fluidName),
                    fluidName + " (mB)", (float) stack.getAmount(), MetricType.FLUID_MB);
        }
        if (totalCapacity <= 0.0f && totalFluid <= 0.0f) {
            return;
        }
        panel.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
        panel.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
        panel.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %",
                totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f, MetricType.FILL_LEVEL);
    }

    private void readItemHandler(StatsDisplayPanelBE panel, BlockPos sourcePos, String srcType, IItemHandler handler) {
        final Map<String, Integer> itemCounts = new LinkedHashMap<String, Integer>();
        final Map<String, String> itemNames = new LinkedHashMap<String, String>();
        int totalItems = 0;
        int slotCapacity = 0;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            slotCapacity += Math.max(0, handler.getSlotLimit(slot));
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                final String registryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                final String displayName = stack.getHoverName().getString();
                itemCounts.merge(registryName, stack.getCount(), Integer::sum);
                itemNames.putIfAbsent(registryName, displayName);
                totalItems += stack.getCount();
            }
        }
        panel.onAutoData(sourcePos, srcType, "total_items", "Total Items", (float)totalItems, MetricType.ITEM_COUNT);

        panel.onAutoData(sourcePos, srcType, "slot_capacity", "Slot Capacity", (float)slotCapacity, MetricType.ITEM_COUNT);
        final LinkedHashSet<String> emitted = new LinkedHashSet<String>();
        itemCounts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12L).forEach(entry -> {
            String regName = entry.getKey();
            final String displayName2 = itemNames.get(regName);
            final String metricId = "item_" + this.sanitizeId(regName);
            emitted.add(metricId);
            panel.onAutoData(sourcePos, srcType, metricId, displayName2, entry.getValue(), MetricType.ITEM_COUNT);
            return;
        });
        panel.onAutoData(sourcePos, srcType, "unique_types", "Unique Types", (float)itemCounts.size(), MetricType.ITEM_COUNT);
        this.zeroDepartedItems(panel, sourcePos, srcType, emitted);
    }

    private void zeroDepartedItems(StatsDisplayPanelBE panel, BlockPos sourcePos, String srcType,
                                   Set<String> emitted) {
        final SourceConfig config = panel.registerOrUpdateSource(sourcePos, srcType);
        if (config == null) {
            return;
        }
        for (StatsDisplayPanelBE.DataChannel channel
                : new ArrayList<>(panel.getChannelsForSource(config.getSourceId()))) {
            final String metricId = this.extractAutoMetricId(channel.getId());
            if (metricId == null || !metricId.startsWith("item_") || emitted.contains(metricId)) {
                continue;
            }
            if (channel.getStore().getLatestValue() <= 0.0f) {
                continue;
            }
            panel.onAutoData(sourcePos, srcType, metricId, channel.getLabel(), 0.0f,
                    MetricType.ITEM_COUNT);
        }
    }

    private void recordObservedTarget(StatsDisplayPanelBE panel, BlockPos sourcePos, String sourceType, Level level, BlockPos targetPos) {
        if (panel == null || level == null || targetPos == null) {
            return;
        }
        final SourceConfig config = panel.registerOrUpdateSource(sourcePos, sourceType);
        if (config == null) {
            return;
        }
        String label = level.getBlockState(targetPos).getBlock().getName().getString();
        if (label == null || label.isEmpty()) {
            label = "Inventory";
        }
        if (config.setObservedTarget(targetPos, label)) {
            panel.markDirtyAndSync();
        }
    }

    private void readItemHandler(StatusTowerLightBE towerLight, BlockPos sourcePos, String srcType, IItemHandler handler) {
        final Map<String, Integer> itemCounts = new LinkedHashMap<String, Integer>();
        int totalItems = 0;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                final String registryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                itemCounts.merge(registryName, stack.getCount(), Integer::sum);
                totalItems += stack.getCount();
            }
        }
        towerLight.onAutoData(sourcePos, srcType, "total_items", "Total Items", (float)totalItems, MetricType.ITEM_COUNT);
        towerLight.onAutoData(sourcePos, srcType, "unique_types", "Unique Types", (float)itemCounts.size(), MetricType.ITEM_COUNT);
    }

    private void autoReadProductionDetector(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof ProductionDetectorBE detector)) {
            return;
        }
        panel.setSourceType("Production Detector");
        final SourceConfig src = panel.registerOrUpdateSource(sourcePos, "Production Detector");
        this.removeLegacyProductionDetectorLineChannels(panel, src.getSourceId());
        final LinkedHashSet<String> activeItemMetricIds = new LinkedHashSet<String>();
        final LinkedHashMap<String, Integer> counts = detector.getItemCounts();
        final LinkedHashMap<String, String> names = detector.getItemDisplayNames();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            final String itemId = e.getKey();
            final String displayName = names.getOrDefault(itemId, itemId);
            final String metricId = "item_" + this.sanitizeId(itemId);
            activeItemMetricIds.add(metricId);
            final int amount = Math.max(0, e.getValue() == null ? 0 : e.getValue().intValue());
            panel.onAutoData(sourcePos, "Production Detector", metricId, displayName, (float) amount, MetricType.ITEM_COUNT);
            this.updateAutoChannelLabel(panel, src.getSourceId(), metricId, displayName);
        }
        panel.onAutoData(sourcePos, "Production Detector", "total_items", "Total Items", (float) detector.getTotalItemsSeen(), MetricType.ITEM_COUNT);
        this.updateAutoChannelLabel(panel, src.getSourceId(), "total_items", "Total Items");
        panel.onAutoData(sourcePos, "Production Detector", "unique_types", "Unique Types", (float) counts.size(), MetricType.ITEM_COUNT);
        this.updateAutoChannelLabel(panel, src.getSourceId(), "unique_types", "Unique Types");
        for (StatsDisplayPanelBE.DataChannel channel : panel.getChannelsForSource(src.getSourceId())) {
            final String metricId2 = this.extractAutoMetricId(channel.getId());
            if (metricId2 == null || !metricId2.startsWith("item_") || activeItemMetricIds.contains(metricId2)) {
                continue;
            }
            if (channel.getStore().getLatestValue() <= 0.0f) continue;
            panel.onAutoData(sourcePos, "Production Detector", metricId2, channel.getLabel(), 0.0f, MetricType.ITEM_COUNT);
        }

        for (StatsDisplayPanelBE.DataChannel channel : panel.getChannelsForSource(src.getSourceId())) {
            final String metricId3 = this.extractAutoMetricId(channel.getId());
            if (metricId3 == null || !metricId3.startsWith("count_")) continue;
            if (channel.getStore().getLatestValue() <= 0.0f) continue;
            panel.onAutoData(sourcePos, "Production Detector", metricId3, channel.getLabel(), 0.0f, MetricType.ITEM_COUNT);
        }
    }

    private void removeLegacyProductionDetectorLineChannels(StatsDisplayPanelBE panel, String sourceId) {
        final String prefix = "src_" + sourceId + "_line";
        boolean removed = false;
        final Iterator<Map.Entry<String, StatsDisplayPanelBE.DataChannel>> iterator = panel.getAllChannels().entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, StatsDisplayPanelBE.DataChannel> entry = iterator.next();
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            iterator.remove();
            removed = true;
        }
        if (removed) {
            panel.normalizeSettingsState();
        }
    }

    private void updateAutoChannelLabel(StatsDisplayPanelBE panel, String sourceId, String metricId, String label) {
        final StatsDisplayPanelBE.DataChannel channel = panel.getChannel("src_" + sourceId + "_auto_" + metricId);
        if (channel != null && !label.equals(channel.getLabel())) {
            channel.setLabel(label);
        }
    }

    @Nullable
    private String extractAutoMetricId(String channelId) {
        final int idx = channelId.indexOf("_auto_");
        if (idx < 0 || idx + 6 >= channelId.length()) {
            return null;
        }
        return channelId.substring(idx + 6);
    }

    private void autoReadTrainStation(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof StationBlockEntity stationBlockEntity)) {
            return;
        }
        panel.setSourceType("Train Station");
        panel.registerOrUpdateSource(sourcePos, "Train Station");
        panel.registerOrUpdateTrainStation(sourcePos, stationBlockEntity);
        panel.enableTrainConnection();
    }

    private void autoReadFactoryPanel(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        final String className = sourceBE.getClass().getSimpleName();
        if (!className.contains("FactoryPanel")) {
            return;
        }
        panel.setSourceType("Factory Panel");
        try {
            if (sourceBE instanceof SmartBlockEntity sbe) {
                final Collection<BlockEntityBehaviour> behaviours = sbe.getAllBehaviours();
                int panelIdx = 0;
                for (BlockEntityBehaviour behaviour : behaviours) {
                    if (behaviour instanceof FilteringBehaviour fb) {
                        final ItemStack filter = fb.getFilter();
                        if (filter.isEmpty()) {
                            continue;
                        }
                        final String itemName = filter.getHoverName().getString();
                        final String metricId = "fpanel_" + panelIdx + "_" + this.sanitizeId(itemName);
                        try {
                            final Method getLevelMethod = behaviour.getClass().getMethod("getLevelInStorage", (Class<?>[])new Class[0]);
                            final int level = (int)getLevelMethod.invoke(behaviour, new Object[0]);
                            panel.onAutoData(sourcePos, "Factory Panel", metricId, itemName + " Stock", (float)level, MetricType.ITEM_COUNT);
                            final Method getAmountMethod = behaviour.getClass().getMethod("getAmount", (Class<?>[])new Class[0]);
                            final int amount = (int)getAmountMethod.invoke(behaviour, new Object[0]);
                            panel.onAutoData(sourcePos, "Factory Panel", metricId + "_target", itemName + " Target", (float)amount, MetricType.ITEM_COUNT);
                            final Method getPromisedMethod = behaviour.getClass().getMethod("getPromised", (Class<?>[])new Class[0]);
                            final int promised = (int)getPromisedMethod.invoke(behaviour, new Object[0]);
                            panel.onAutoData(sourcePos, "Factory Panel", metricId + "_promised", itemName + " Promised", (float)promised, MetricType.ITEM_COUNT);
                        }
                        catch (NoSuchMethodException ex) {}
                        ++panelIdx;
                    }
                }
            }
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Factory panel auto-read: {}", e.getMessage());
        }
    }

    private String sanitizeId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private void autoReadStressGauge(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            projector.setSourceType("Stressometer");
            projector.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT);
            final float networkStress = gauge.getNetworkStress();
            projector.onAutoData(sourcePos, "Stressometer", "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
            final float networkCapacity = gauge.getNetworkCapacity();
            projector.onAutoData(sourcePos, "Stressometer", "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
            projector.onAutoData(sourcePos, "Stressometer", "remaining_su", "Remaining (SU)", Math.max(0.0f, networkCapacity - networkStress), MetricType.STRESS_SU);
            projector.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", Math.abs(gauge.getSpeed()), MetricType.RPM);
            projector.onAutoData(sourcePos, "Stressometer", "overstressed", "Overstressed", gauge.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadSpeedGauge(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            projector.setSourceType("Speedometer");
            projector.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", gauge.getSpeed(), MetricType.RPM);
            projector.onAutoData(sourcePos, "Speedometer", "abs_rpm", "Speed (abs)", Math.abs(gauge.getSpeed()), MetricType.RPM);
            projector.onAutoData(sourcePos, "Speedometer", "speed_pct", "Speed %", gauge.dialTarget * 100.0f, MetricType.GENERIC);
        }
    }

    private void autoReadThresholdSwitch(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof ThresholdSwitchBlockEntity tsw) {
            projector.setSourceType("Stockpile Switch");
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(tsw);
            final MetricType amountType = readout.amountMetricType();
            projector.onAutoData(sourcePos, "Stockpile Switch", "fill_pct", "Fill Level %", readout.fillPercent(), MetricType.FILL_LEVEL);
            projector.onAutoData(sourcePos, "Stockpile Switch", "stock_level", "Current Amount", readout.currentAmount(), amountType);
            projector.onAutoData(sourcePos, "Stockpile Switch", "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
            projector.onAutoData(sourcePos, "Stockpile Switch", "min_level", "Minimum Level", readout.minAmount(), amountType);
            projector.onAutoData(sourcePos, "Stockpile Switch", "upper_thresh", "Upper Threshold", readout.upperThreshold(), amountType);
            projector.onAutoData(sourcePos, "Stockpile Switch", "lower_thresh", "Lower Threshold", readout.lowerThreshold(), amountType);
            projector.onAutoData(sourcePos, "Stockpile Switch", "powered", "Signal Output", tsw.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadBoiler(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            String srcType = "Fluid Tank";
            projector.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
            final FluidTank tankInv = tank.getTankInventory();
            if (tankInv != null) {
                projector.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", (float)tankInv.getFluidAmount(), MetricType.FLUID_MB);
                projector.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", (float)tankInv.getCapacity(), MetricType.FLUID_MB);
            }
            final BoilerData boiler = tank.boiler;
            if (boiler != null && boiler.isActive()) {
                srcType = "Steam Boiler";
                projector.setSourceType("Steam Boiler");
                projector.onAutoData(sourcePos, srcType, "heat_level", "Heat Level", (float)boiler.activeHeat, MetricType.HEAT_LEVEL);
                projector.onAutoData(sourcePos, srcType, "water_supply", "Water Supply", boiler.waterSupply, MetricType.WATER_SUPPLY);
                projector.onAutoData(sourcePos, srcType, "engines", "Engines", (float)boiler.attachedEngines, MetricType.ENGINE_COUNT);
            }
            else {
                projector.setSourceType("Fluid Tank");
            }
        }
    }

    private void autoReadContentObserver(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof SmartObserverBlockEntity observer) {
            projector.setSourceType("Content Observer");
            projector.registerOrUpdateSource(sourcePos, "Content Observer");
        }
    }

    private void autoReadTrainStation(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        final String className = sourceBE.getClass().getSimpleName();
        if (!className.contains("Station")) {
            return;
        }
        projector.setSourceType("Train Station");
        projector.registerOrUpdateSource(sourcePos, "Train Station");
    }

    private void autoReadStressGauge(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            towerLight.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT);
            final float networkStress = gauge.getNetworkStress();
            towerLight.onAutoData(sourcePos, "Stressometer", "stress_su", "Current Stress (SU)", networkStress, MetricType.STRESS_SU);
            final float networkCapacity = gauge.getNetworkCapacity();
            towerLight.onAutoData(sourcePos, "Stressometer", "capacity_su", "Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
            towerLight.onAutoData(sourcePos, "Stressometer", "remaining_su", "Remaining (SU)", Math.max(0.0f, networkCapacity - networkStress), MetricType.STRESS_SU);
            towerLight.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", Math.abs(gauge.getSpeed()), MetricType.RPM);
            towerLight.onAutoData(sourcePos, "Stressometer", "running", "Running", Math.abs(gauge.getSpeed()) > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            towerLight.onAutoData(sourcePos, "Stressometer", "overstressed", "Overstressed", gauge.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadSpeedGauge(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            towerLight.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", gauge.getSpeed(), MetricType.RPM);
            towerLight.onAutoData(sourcePos, "Speedometer", "abs_rpm", "Speed (abs)", Math.abs(gauge.getSpeed()), MetricType.RPM);
            towerLight.onAutoData(sourcePos, "Speedometer", "running", "Running", Math.abs(gauge.getSpeed()) > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadStressGauge(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof StressGaugeBlockEntity gauge)) {
            return;
        }
        dial.onAutoData(sourcePos, "Stressometer", "stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT);
        dial.onAutoData(sourcePos, "Stressometer", "rpm", "RPM", Math.abs(gauge.getSpeed()), MetricType.RPM);
    }

    private void autoReadSpeedGauge(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof SpeedGaugeBlockEntity gauge)) {
            return;
        }
        dial.onAutoData(sourcePos, "Speedometer", "rpm", "RPM", gauge.getSpeed(), MetricType.RPM);
        if (KineticNetworkMetrics.supportsStressReadout(gauge)) {
            final KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(gauge);
            dial.onAutoData(sourcePos, "Speedometer", "stress_pct", "Stress %", readout.stressPercent(), MetricType.STRESS_PERCENT);
        }
    }

    private void autoReadSafetyClutch(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof SafetyClutchBE clutch)) {
            return;
        }
        dial.onAutoData(sourcePos, "Safety Clutch", "stress_pct", "Stress %", clutch.getLastStressPercent(), MetricType.STRESS_PERCENT);
        dial.onAutoData(sourcePos, "Safety Clutch", "rpm", "RPM", Math.abs(clutch.getSpeed()), MetricType.RPM);
    }

    private void autoReadAutoTransmissionClutch(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof AutoTransmissionClutchBE transmission)) {
            return;
        }
        dial.onAutoData(sourcePos, "Auto Variable Clutch", "stress_pct", "Stress %", transmission.getLastStressPercent(), MetricType.STRESS_PERCENT);
        dial.onAutoData(sourcePos, "Auto Variable Clutch", "rpm", "RPM", Math.abs(transmission.getSpeed()), MetricType.RPM);
    }

    private void autoReadThresholdSwitch(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof ThresholdSwitchBlockEntity tsw) {
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(tsw);
            final MetricType amountType = readout.amountMetricType();
            towerLight.onAutoData(sourcePos, "Stockpile Switch", "fill_pct", "Fill Level %", readout.fillPercent(), MetricType.FILL_LEVEL);
            towerLight.onAutoData(sourcePos, "Stockpile Switch", "stock_level", "Current Amount", readout.currentAmount(), amountType);
            towerLight.onAutoData(sourcePos, "Stockpile Switch", "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
            towerLight.onAutoData(sourcePos, "Stockpile Switch", "powered", "Signal Output", tsw.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private void autoReadThresholdSwitch(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof ThresholdSwitchBlockEntity tsw)) {
            return;
        }
        final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(tsw);
        final String sourceType = readout.amountMetricType() == MetricType.FLUID_MB ? "Fluid Storage" : "Item Storage";
        dial.onAutoData(sourcePos, sourceType, "fill_pct", "Fill %", readout.fillPercent(), MetricType.FILL_LEVEL);
    }

    private void autoReadBoiler(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                tank = controller;
            }
            String srcType = "Fluid Tank";
            towerLight.onAutoData(sourcePos, srcType, "tank_fill", "Tank Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
            final FluidTank tankInv = tank.getTankInventory();
            if (tankInv != null) {
                towerLight.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", (float)tankInv.getFluidAmount(), MetricType.FLUID_MB);
                towerLight.onAutoData(sourcePos, srcType, "tank_capacity", "Tank Capacity (mB)", (float)tankInv.getCapacity(), MetricType.FLUID_MB);
            }
            final BoilerData boiler = tank.boiler;
            if (boiler != null && boiler.isActive()) {
                srcType = "Steam Boiler";
                towerLight.onAutoData(sourcePos, srcType, "heat_level", "Heat Level", (float)boiler.activeHeat, MetricType.HEAT_LEVEL);
                towerLight.onAutoData(sourcePos, srcType, "water_supply", "Water Supply", boiler.waterSupply, MetricType.WATER_SUPPLY);
                towerLight.onAutoData(sourcePos, srcType, "engines", "Engines", (float)boiler.attachedEngines, MetricType.ENGINE_COUNT);
                towerLight.onAutoData(sourcePos, srcType, "boiler_gauge", "Boiler Gauge %", boiler.gauge.getValue(1.0f) * 100.0f, MetricType.FILL_LEVEL);
            }
        }
    }

    private void autoReadBoiler(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof FluidTankBlockEntity tank)) {
            return;
        }
        final FluidTankBlockEntity controller = tank.getControllerBE();
        if (controller != null) {
            tank = controller;
        }
        final BoilerData boiler = tank.boiler;
        if (boiler != null && boiler.isActive()) {
            final int maxHeat = Math.min(boiler.getMaxHeatLevelForBoilerSize(tank.getTotalTankSize()), boiler.getMaxHeatLevelForWaterSupply());
            final float heatPercent = maxHeat > 0 ? Mth.clamp(boiler.activeHeat * 100.0f / maxHeat, 0.0f, 100.0f) : 0.0f;
            dial.onAutoData(sourcePos, "Steam Boiler", "boiler_gauge", "Boiler %", boiler.gauge.getValue(1.0f) * 100.0f, MetricType.FILL_LEVEL);
            dial.onAutoData(sourcePos, "Steam Boiler", "heat_pct", "Heat %", heatPercent, MetricType.FILL_LEVEL);
            return;
        }
        dial.onAutoData(sourcePos, "Fluid Tank", "fill_pct", "Fill %", tank.getFillState() * 100.0f, MetricType.FILL_LEVEL);
    }

    private void autoReadContentObserver(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof SmartObserverBlockEntity observer)) {
            return;
        }
        final String srcType = "Content Observer";
        towerLight.setSourceType(srcType);
        towerLight.registerOrUpdateSource(sourcePos, srcType);
        final BlockState observerState = observer.getBlockState();
        final Direction facing = DirectedDirectionalBlock.getTargetDirection(observerState);
        if (facing == null) {
            return;
        }
        final BlockPos observedPos = observer.getBlockPos().relative(facing);
        final Level level = observer.getLevel();
        if (level == null) {
            return;
        }
        final BlockEntity observedBE = level.getBlockEntity(observedPos);
        if (observedBE == null) {
            return;
        }
        final IItemHandler towerHandler = com.createstats.util.InventoryLookupHelper.resolveItemHandler(
                level, observedPos, observedBE, null);
        if (towerHandler != null) {
            this.readItemHandler(towerLight, sourcePos, srcType, towerHandler);
        }
        final IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, observedPos, null);
        if (fluidHandler != null) {
            float totalFluid = 0.0f;
            for (int t = 0; t < fluidHandler.getTanks(); ++t) {
                totalFluid += fluidHandler.getFluidInTank(t).getAmount();
            }
            towerLight.onAutoData(sourcePos, srcType, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
        }
    }

    private void autoReadProductionDetector(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        if (!(sourceBE instanceof ProductionDetectorBE detector)) {
            return;
        }
        towerLight.onAutoData(sourcePos, "Production Detector", "total_items", "Total Items", (float) detector.getTotalItemsSeen(), MetricType.ITEM_COUNT);
        towerLight.onAutoData(sourcePos, "Production Detector", "unique_types", "Unique Types", (float) detector.getTrackedItemTypeCount(), MetricType.ITEM_COUNT);
    }

    private void autoReadTrainStation(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        final String className = sourceBE.getClass().getSimpleName();
        if (!className.contains("Station")) {
            return;
        }
        towerLight.setSourceType("Train Station");
        towerLight.registerOrUpdateSource(sourcePos, "Train Station");
    }

    private void autoReadFactoryPanel(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        final String className = sourceBE.getClass().getSimpleName();
        if (!className.contains("FactoryPanel")) {
            return;
        }
        try {
            if (sourceBE instanceof SmartBlockEntity sbe) {
                final Collection<BlockEntityBehaviour> behaviours = sbe.getAllBehaviours();
                int panelIdx = 0;
                for (BlockEntityBehaviour behaviour : behaviours) {
                    if (!(behaviour instanceof FilteringBehaviour fb)) {
                        continue;
                    }
                    final ItemStack filter = fb.getFilter();
                    if (filter.isEmpty()) {
                        continue;
                    }
                    final String itemName = filter.getHoverName().getString();
                    final String metricId = "fpanel_" + panelIdx + "_" + this.sanitizeId(itemName);
                    try {
                        final Method getLevelMethod = behaviour.getClass().getMethod("getLevelInStorage", (Class<?>[])new Class[0]);
                        final int level = (int)getLevelMethod.invoke(behaviour, new Object[0]);
                        towerLight.onAutoData(sourcePos, "Factory Panel", metricId, itemName + " Stock", (float)level, MetricType.ITEM_COUNT);
                    }
                    catch (NoSuchMethodException ex) {}
                    ++panelIdx;
                }
            }
        }
        catch (Exception e) {
            CreateStats.LOGGER.debug("Factory panel auto-read (tower light): {}", e.getMessage());
        }
    }

    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(4, 1, (DisplayTarget)this);
    }

    static {
        TUNNEL_ITEM_PATTERN = Pattern.compile("^\\s*[\\d,.]+\\s+(.+?)\\s*(?:/\\s*(?:s|sec|min|hr|hour))?\\s*$", 2);
    }
}
