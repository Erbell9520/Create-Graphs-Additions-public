package com.createstats.util;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.InductionHeaterBE;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.compat.SimulatedCompat;
import com.createstats.compat.SimulatedCompatHelper;
import com.createstats.compat.CreateAdditionCompat;
import com.createstats.compat.CreateAdditionCompatHelper;
import com.createstats.compat.DieselCompat;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.data.MetricType;
import com.createstats.display.StatsDisplayTarget;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class InstrumentSourceHelper {

    private InstrumentSourceHelper() {
    }

    @Nullable
    public static LocalBinding resolveWallMountedBinding(Level level, BlockPos targetPos, Direction wallDirection) {
        final Direction accessFace = wallDirection.getOpposite();
        final BlockPos sourcePos = targetPos.relative(wallDirection);
        final BlockEntity sourceBE = level.getBlockEntity(sourcePos);
        if (sourceBE == null) {
            return null;
        }
        return normalizeLocalBinding(sourcePos, sourceBE, accessFace);
    }

    public static LocalBinding normalizeLocalBinding(BlockPos pos, BlockEntity sourceBE, Direction accessFace) {
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

    public static SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace) {
        return readSource(sourceBE, accessFace, List.of());
    }

    public static SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        if (DieselCompat.isAvailable()) {
            final SourceReadout dieselReadout = DieselCompatHelper.readSource(sourceBE, accessFace);
            if (dieselReadout != null) {
                return dieselReadout;
            }
        }
        if (CreateAdditionCompat.isAvailable()) {
            final SourceReadout createAdditionReadout = CreateAdditionCompatHelper.readSource(sourceBE, accessFace, trackedItems);
            if (createAdditionReadout != null) {
                return createAdditionReadout;
            }
        }
        final SourceReadout baseReadout = readBaseSource(sourceBE, accessFace, trackedItems);
        if (!SimulatedCompat.isAvailable()) {
            return baseReadout;
        }
        final SourceReadout compatReadout = SimulatedCompatHelper.readSource(sourceBE, accessFace, trackedItems);
        return compatReadout != null ? compatReadout : baseReadout;
    }

    public static SourceReadout readBaseSource(BlockEntity sourceBE, @Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        final Map<String, MetricSample> metrics = new LinkedHashMap<>();

        if (sourceBE instanceof StressGaugeBlockEntity gauge) {
            putSample(metrics, "stress_pct", "Stress %", gauge.dialTarget * 100.0f, MetricType.STRESS_PERCENT);
            putKineticMetrics(metrics, gauge);
            final float rpm = Math.abs(gauge.getSpeed());
            putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            putSample(metrics, "running", "Running", rpm > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return new SourceReadout("Stressometer", SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof SafetyClutchBE clutch) {
            putSample(metrics, "stress_pct", "Stress %", clutch.getLastStressPercent(), MetricType.STRESS_PERCENT);
            putSample(metrics, "stress_su", "Current Stress (SU)", clutch.getLastStressSU(), MetricType.STRESS_SU);
            putSample(metrics, "capacity_su", "Capacity (SU)", clutch.getLastCapacitySU(), MetricType.STRESS_SU);
            putSample(metrics, "remaining_su", "Remaining (SU)",
                    Math.max(0.0f, clutch.getLastCapacitySU() - clutch.getLastStressSU()), MetricType.STRESS_SU);
            putSample(metrics, "rpm", "RPM", Math.abs(clutch.getSpeed()), MetricType.RPM);
            putSample(metrics, "running", "Running", clutch.isNetworkRunning() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "overstressed", "Overstressed", clutch.isLastOverstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return new SourceReadout("Safety Clutch", SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof AutoTransmissionClutchBE transmission) {
            putSample(metrics, "stress_pct", "Stress %", transmission.getLastStressPercent(), MetricType.STRESS_PERCENT);
            putSample(metrics, "stress_su", "Current Stress (SU)", transmission.getLastStressSU(), MetricType.STRESS_SU);
            putSample(metrics, "capacity_su", "Capacity (SU)", transmission.getLastCapacitySU(), MetricType.STRESS_SU);
            putSample(metrics, "remaining_su", "Remaining (SU)",
                    Math.max(0.0f, transmission.getLastCapacitySU() - transmission.getLastStressSU()), MetricType.STRESS_SU);
            putSample(metrics, "rpm", "RPM", Math.abs(transmission.getSpeed()), MetricType.RPM);
            putSample(metrics, "running", "Running", transmission.isNetworkRunning() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "overstressed", "Overstressed", transmission.isLastOverstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return new SourceReadout("Auto Variable Clutch", SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof KineticFlywheelBE flywheel) {
            putSample(metrics, "charge_pct", "Charge %", flywheel.getChargePercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "stress_pct", "Charge %", flywheel.getChargePercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "stored_rpm", "Stored RPM", flywheel.getStoredRpm(), MetricType.RPM);
            putSample(metrics, "abs_rpm", "Speed (abs)", Math.abs(flywheel.getStoredRpm()), MetricType.RPM);
            putSample(metrics, "output_su", "Output (SU)", flywheel.getOutputStress(), MetricType.STRESS_SU);
            putSample(metrics, "charging", "Charging", flywheel.getMode() == KineticFlywheelBE.Mode.CHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "discharging", "Discharging", flywheel.getMode() == KineticFlywheelBE.Mode.DISCHARGING ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "rpm", "Network RPM", Math.abs(flywheel.getSpeed()), MetricType.RPM);
            putSample(metrics, "running", "Spinning", Math.abs(flywheel.getStoredRpm()) > 0.5f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putKineticMetrics(metrics, flywheel);
            return new SourceReadout("Kinetic Flywheel", SourceKind.KINETIC_STRESS, metrics);
        }

        if (sourceBE instanceof SpeedGaugeBlockEntity gauge) {
            final float rpm = gauge.getSpeed();
            putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            putSample(metrics, "abs_rpm", "Speed (abs)", Math.abs(rpm), MetricType.RPM);
            putSample(metrics, "running", "Running", Math.abs(rpm) > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            if (KineticNetworkMetrics.supportsStressReadout(gauge)) {
                putKineticMetrics(metrics, gauge);
                return new SourceReadout("Speedometer", SourceKind.KINETIC_STRESS, metrics);
            }
            return new SourceReadout("Speedometer", SourceKind.KINETIC_SPEED, metrics);
        }

        if (sourceBE instanceof InductionHeaterBE heater) {
            final float rpm = Math.abs(heater.getSpeed());
            putSample(metrics, "heat_level", "Heat Level", (float) heater.getHeatTierOrdinal(), MetricType.HEAT_LEVEL);
            putSample(metrics, "charge_pct", "Charge %", heater.getChargePercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "draw_su", "SU Draw", heater.getCurrentDrawSu(), MetricType.STRESS_SU);
            putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            putSample(metrics, "coil_present", "Coil", heater.hasCoil() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "coil_health_pct", "Coil Health", heater.getCoilHealthPercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "coil_seconds", "Coil Time", heater.getCoilSecondsRemaining(), MetricType.GENERIC);
            putSample(metrics, "cooking", "Cooking", heater.hasCookStack() && !heater.isCookDone() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "cook_pct", "Cook %", heater.getCookProgressPercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "cook_done", "Ready", heater.isCookDone() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "cook_seconds", "Cook Time", heater.getCookSecondsRemaining(), MetricType.GENERIC);
            return new SourceReadout("Induction Heater", SourceKind.BOILER, metrics);
        }

        if (sourceBE instanceof KineticBlockEntity kinetic) {
            final float rpm = Math.abs(kinetic.getSpeed());
            putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            putSample(metrics, "running", "Running", rpm > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            if (KineticNetworkMetrics.supportsStressReadout(kinetic)) {
                putKineticMetrics(metrics, kinetic);
                return new SourceReadout(resolveSourceType(sourceBE), SourceKind.KINETIC_STRESS, metrics);
            }
            putSample(metrics, "overstressed", "Overstressed", kinetic.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return new SourceReadout(resolveSourceType(sourceBE), SourceKind.KINETIC_SPEED, metrics);
        }

        if (sourceBE instanceof ThresholdSwitchBlockEntity thresholdSwitch) {
            final StockpileSwitchMetrics.Readout readout = StockpileSwitchMetrics.read(thresholdSwitch);
            final MetricType amountType = readout.amountMetricType();
            putSample(metrics, "fill_pct", "Fill %", readout.fillPercent(), MetricType.FILL_LEVEL);
            putSample(metrics, "stock_level", "Current Amount", readout.currentAmount(), amountType);
            putSample(metrics, "stock_capacity", "Capacity", readout.capacityAmount(), amountType);
            putSample(metrics, "powered", "Signal Output", thresholdSwitch.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            final SourceKind sourceKind = amountType == MetricType.FLUID_MB ? SourceKind.FLUID_STORAGE : SourceKind.ITEM_STORAGE;
            return new SourceReadout("Stockpile Switch", sourceKind, metrics);
        }

        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            final float fillPercent = controller.getFillState() * 100.0f;
            putSample(metrics, "fill_pct", "Fill %", fillPercent, MetricType.FILL_LEVEL);
            putSample(metrics, "tank_fill", "Tank Fill %", fillPercent, MetricType.FILL_LEVEL);
            final FluidTank tankInventory = controller.getTankInventory();
            if (tankInventory != null) {
                putSample(metrics, "fluid_amount", "Fluid Amount (mB)", tankInventory.getFluidAmount(), MetricType.FLUID_MB);
                putSample(metrics, "tank_capacity", "Tank Capacity (mB)", tankInventory.getCapacity(), MetricType.FLUID_MB);
                putSample(metrics, "fluid_capacity", "Fluid Capacity (mB)", tankInventory.getCapacity(), MetricType.FLUID_MB);
            }
            final BoilerData boiler = controller.boiler;
            if (boiler != null && boiler.isActive()) {
                putSample(metrics, "boiler_gauge", "Boiler %", boiler.gauge.getValue(1.0f) * 100.0f, MetricType.FILL_LEVEL);
                putSample(metrics, "heat_pct", "Heat %", getBoilerHeatPercent(controller, boiler), MetricType.FILL_LEVEL);
                putSample(metrics, "heat_level", "Heat Level", boiler.activeHeat, MetricType.HEAT_LEVEL);
                return new SourceReadout("Steam Boiler", SourceKind.BOILER, metrics);
            }
            return new SourceReadout("Fluid Tank", SourceKind.FLUID_STORAGE, metrics);
        }

        final IItemHandler itemHandler = resolveItemHandler(sourceBE, accessFace);
        if (itemHandler != null) {
            addItemMetrics(metrics, itemHandler, trackedItems);
            return new SourceReadout(resolveSourceType(sourceBE), SourceKind.ITEM_STORAGE, metrics);
        }

        final IFluidHandler fluidHandler = resolveFluidHandler(sourceBE, accessFace);
        if (fluidHandler != null) {
            float totalFluid = 0.0f;
            float totalCapacity = 0.0f;
            for (int tankIndex = 0; tankIndex < fluidHandler.getTanks(); ++tankIndex) {
                totalFluid += fluidHandler.getFluidInTank(tankIndex).getAmount();
                totalCapacity += fluidHandler.getTankCapacity(tankIndex);
            }
            final float fillPercent = totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f;
            putSample(metrics, "fill_pct", "Fill %", fillPercent, MetricType.FILL_LEVEL);
            putSample(metrics, "tank_fill", "Tank Fill %", fillPercent, MetricType.FILL_LEVEL);
            putSample(metrics, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
            putSample(metrics, "tank_capacity", "Tank Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
            putSample(metrics, "fluid_capacity", "Fluid Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
            return new SourceReadout(resolveSourceType(sourceBE), SourceKind.FLUID_STORAGE, metrics);
        }

        // [1]
        if (CreateStatsPowerMetrics.collect(sourceBE, metrics)) {
            return new SourceReadout(resolveSourceType(sourceBE), CreateStatsPowerMetrics.kindFor(sourceBE), metrics);
        }

        final IEnergyStorage energyStorage = resolveEnergyStorage(sourceBE, accessFace);
        if (energyStorage != null) {
            CreateAdditionCompatHelper.putGenericEnergyMetrics(metrics, sourceBE, energyStorage);
            return new SourceReadout(resolveSourceType(sourceBE), SourceKind.ENERGY_STORAGE, metrics);
        }

        return new SourceReadout(resolveSourceType(sourceBE), SourceKind.UNKNOWN, metrics);
    }

    public static boolean supportsAutoRead(@Nullable BlockEntity sourceBE) {
        return sourceBE instanceof StressGaugeBlockEntity
                || sourceBE instanceof SpeedGaugeBlockEntity
                || sourceBE instanceof ThresholdSwitchBlockEntity
                || sourceBE instanceof FluidTankBlockEntity
                || sourceBE instanceof ItemVaultBlockEntity
                || sourceBE instanceof SafetyClutchBE
                || sourceBE instanceof AutoTransmissionClutchBE
                || sourceBE instanceof KineticFlywheelBE
                || sourceBE instanceof InductionHeaterBE
                || resolveEnergyStorage(sourceBE, null) != null
                || (CreateAdditionCompat.isAvailable() && CreateAdditionCompatHelper.supportsCompactAutoRead(sourceBE))
                || (DieselCompat.isAvailable() && DieselCompatHelper.supportsCompactAutoRead(sourceBE))
                || (SimulatedCompat.isAvailable() && SimulatedCompatHelper.supportsCompactAutoRead(sourceBE));
    }

    public static boolean supportsCompactAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsAutoRead(sourceBE);
    }

    public static boolean supportsRichAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsAutoRead(sourceBE)
                || (SimulatedCompat.isAvailable() && SimulatedCompatHelper.supportsAutoRead(sourceBE));
    }

    public static boolean hasEnergyStorageCapability(@Nullable BlockEntity sourceBE) {
        return resolveEnergyStorage(sourceBE, null) != null;
    }

    public static SourceKind classifyLinkedSource(String sourceType, Map<String, MetricSample> metrics) {
        if (metrics.containsKey("boiler_gauge") || metrics.containsKey("heat_pct") || metrics.containsKey("heat_level")) {
            return SourceKind.BOILER;
        }
        if (metrics.containsKey("stress_pct") || metrics.containsKey("stress_su") || metrics.containsKey("capacity_su")) {
            return SourceKind.KINETIC_STRESS;
        }
        if (metrics.containsKey("rpm") || metrics.containsKey("abs_rpm")) {
            return SourceKind.KINETIC_SPEED;
        }
        if (metrics.containsKey("generated_speed")) {
            return SourceKind.KINETIC_SPEED;
        }
        if (hasEnergyMetrics(metrics)) {
            return SourceKind.ENERGY_STORAGE;
        }
        if (hasItemMetrics(metrics)) {
            return SourceKind.ITEM_STORAGE;
        }
        if (hasFluidMetrics(metrics)) {
            return SourceKind.FLUID_STORAGE;
        }

        final String normalized = sourceType == null ? "" : sourceType.toLowerCase();
        if (normalized.isEmpty()) {
            return SourceKind.NO_SOURCE;
        }
        if (normalized.contains("vault") || normalized.contains("item") || normalized.contains("observer")) {
            return SourceKind.ITEM_STORAGE;
        }
        if (normalized.contains("tank") || normalized.contains("fluid")) {
            return SourceKind.FLUID_STORAGE;
        }
        if (normalized.contains("energy") || normalized.contains("fe") || normalized.contains("accumulator")
                || normalized.contains("alternator") || normalized.contains("electric motor") || normalized.contains("connector")) {
            return SourceKind.ENERGY_STORAGE;
        }
        if (normalized.contains("boiler")) {
            return SourceKind.BOILER;
        }
        if (normalized.contains("stress") || normalized.contains("clutch") || normalized.contains("speedometer") || normalized.contains("kinetic")
            || normalized.contains("diesel engine")) {
            return SourceKind.KINETIC_STRESS;
        }
        if (normalized.contains("portable engine") || normalized.contains("steering wheel")) {
            return SourceKind.KINETIC_SPEED;
        }
        if (normalized.contains("auger") || normalized.contains("throughput") || normalized.contains("typewriter")) {
            return SourceKind.UNKNOWN;
        }
        return SourceKind.UNKNOWN;
    }

    public static String resolveSourceType(BlockEntity sourceBE) {
        final String mapped = StatsDisplayTarget.getStatsDisplayLinkSourceType(sourceBE);
        if (!mapped.isEmpty()) {
            return mapped;
        }
        return sourceBE.getBlockState().getBlock().getName().getString();
    }

    public static String sanitizeMetricId(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    public static String metricIdForItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        final var registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final String key = registryName != null ? registryName.toString() : stack.getHoverName().getString();
        return "item_" + sanitizeMetricId(key);
    }

    private static boolean hasItemMetrics(Map<String, MetricSample> metrics) {
        if (metrics.containsKey("total_items") || metrics.containsKey("unique_types")) {
            return true;
        }
        final MetricSample stockLevel = metrics.get("stock_level");
        if (stockLevel != null && stockLevel.type() == MetricType.ITEM_COUNT) {
            return true;
        }
        for (String metricId : metrics.keySet()) {
            if (metricId.startsWith("item_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluidMetrics(Map<String, MetricSample> metrics) {
        if (metrics.containsKey("fluid_amount") || metrics.containsKey("tank_capacity")) {
            return true;
        }
        final MetricSample stockLevel = metrics.get("stock_level");
        return stockLevel != null && stockLevel.type() == MetricType.FLUID_MB;
    }

    private static boolean hasEnergyMetrics(Map<String, MetricSample> metrics) {
        if (metrics.containsKey("energy_stored") || metrics.containsKey("energy_capacity") || metrics.containsKey("energy_fill_pct")
                || metrics.containsKey("energy_net_rate") || metrics.containsKey("energy_output_rate") || metrics.containsKey("energy_input_rate")) {
            return true;
        }
        for (MetricSample sample : metrics.values()) {
            if (sample.type() == MetricType.ENERGY_FE || sample.type() == MetricType.ENERGY_FE_PER_TICK) {
                return true;
            }
        }
        return false;
    }

    private static void addItemMetrics(Map<String, MetricSample> metrics, IItemHandler handler, Collection<ItemStack> trackedItems) {
        final ItemHandlerMetrics.Readout readout = ItemHandlerMetrics.read(handler);
        putSample(metrics, "fill_pct", "Fill %", readout.fillPercent(), MetricType.FILL_LEVEL);
        putSample(metrics, "total_items", "Total Items", readout.totalItems(), MetricType.ITEM_COUNT);
        putSample(metrics, "item_capacity", "Capacity", readout.totalCapacity(), MetricType.ITEM_COUNT);
        putSample(metrics, "unique_types", "Unique Types", 0.0f, MetricType.ITEM_COUNT);

        final LinkedHashMap<String, Integer> itemCounts = new LinkedHashMap<>();
        final LinkedHashMap<String, String> itemNames = new LinkedHashMap<>();
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            final var registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
            final String key = registryName != null ? registryName.toString() : sanitizeMetricId(stack.getHoverName().getString());
            itemCounts.merge(key, stack.getCount(), Integer::sum);
            itemNames.putIfAbsent(key, stack.getHoverName().getString());
        }

        putSample(metrics, "unique_types", "Unique Types", itemCounts.size(), MetricType.ITEM_COUNT);

        final List<Map.Entry<String, Integer>> sorted = new ArrayList<>(itemCounts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey));
        for (int index = 0; index < sorted.size(); ++index) {
            final Map.Entry<String, Integer> entry = sorted.get(index);
            putSample(metrics, "item_" + sanitizeMetricId(entry.getKey()), itemNames.get(entry.getKey()), entry.getValue(), MetricType.ITEM_COUNT);
        }

        final LinkedHashSet<String> trackedIds = new LinkedHashSet<>();
        for (ItemStack tracked : trackedItems) {
            if (tracked.isEmpty()) {
                continue;
            }
            final var registryName = BuiltInRegistries.ITEM.getKey(tracked.getItem());
            if (registryName == null) {
                continue;
            }
            if (!trackedIds.add(registryName.toString())) {
                continue;
            }
            final String metricId = "item_" + sanitizeMetricId(registryName.toString());
            final int count = itemCounts.getOrDefault(registryName.toString(), 0);
            putSample(metrics, metricId, tracked.getHoverName().getString(), count, MetricType.ITEM_COUNT);
        }
    }

    private static void putKineticMetrics(Map<String, MetricSample> metrics, KineticBlockEntity kinetic) {
        if (!KineticNetworkMetrics.supportsStressReadout(kinetic)) {
            putSample(metrics, "overstressed", "Overstressed", kinetic.isOverStressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return;
        }
        final KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(kinetic);
        putSample(metrics, "stress_pct", "Stress %", readout.stressPercent(), MetricType.STRESS_PERCENT);
        putSample(metrics, "stress_su", "Current Stress (SU)", readout.networkStress(), MetricType.STRESS_SU);
        putSample(metrics, "capacity_su", "Capacity (SU)", readout.networkCapacity(), MetricType.STRESS_SU);
        putSample(metrics, "remaining_su", "Remaining (SU)", readout.remainingCapacity(), MetricType.STRESS_SU);
        putSample(metrics, "overstressed", "Overstressed", readout.overstressed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    private static float getBoilerHeatPercent(FluidTankBlockEntity tank, BoilerData boiler) {
        final int maxHeat = Math.min(boiler.getMaxHeatLevelForBoilerSize(tank.getTotalTankSize()), boiler.getMaxHeatLevelForWaterSupply());
        if (maxHeat <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, boiler.activeHeat * 100.0f / maxHeat));
    }

    @Nullable
    private static IItemHandler resolveItemHandler(BlockEntity sourceBE, @Nullable Direction accessFace) {
        return InventoryLookupHelper.resolveItemHandler(sourceBE.getLevel(), sourceBE.getBlockPos(), sourceBE, accessFace);
    }

    @Nullable
    private static IFluidHandler resolveFluidHandler(BlockEntity sourceBE, @Nullable Direction accessFace) {
        IFluidHandler fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), accessFace);
        if (fluidHandler == null) {
            fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), null);
        }
        return fluidHandler;
    }

    @Nullable
    private static IEnergyStorage resolveEnergyStorage(@Nullable BlockEntity sourceBE, @Nullable Direction accessFace) {
        if (sourceBE == null || sourceBE.getLevel() == null) {
            return null;
        }
        IEnergyStorage energyStorage = sourceBE.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, sourceBE.getBlockPos(), accessFace);
        if (energyStorage == null) {
            energyStorage = sourceBE.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, sourceBE.getBlockPos(), null);
        }
        return energyStorage;
    }

    private static void putSample(Map<String, MetricSample> metrics, String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new MetricSample(metricId, label, value, type));
    }

    public record MetricSample(String metricId, String label, float value, MetricType type) {
    }

    public record SourceReadout(String sourceType, SourceKind sourceKind, Map<String, MetricSample> metrics) {
    }

    public record LocalBinding(BlockPos pos, BlockEntity blockEntity, Direction accessFace) {
    }

    public enum SourceKind {
        NO_SOURCE("No source"),
        KINETIC_STRESS("Kinetic"),
        KINETIC_SPEED("Speed"),
        ITEM_STORAGE("Item storage"),
        FLUID_STORAGE("Fluid storage"),
        ENERGY_STORAGE("Energy storage"),
        POWER_GRID("Power grid"),
        BOILER("Boiler"),
        UNKNOWN("Unknown");

        private final String displayName;

        SourceKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
