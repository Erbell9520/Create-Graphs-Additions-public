package com.createstats.compat;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.KineticNetworkMetrics;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

public final class DieselCompatHelper {
    public static final String DISPLAY_LINK_MULTI_TAG = "CreateStatsDieselMulti";
    private static final String DISPLAY_LINK_FUEL_RESERVE_TAG = "CreateStatsDieselFuelReserve";
    private static final String DISPLAY_LINK_FUEL_RESERVE_TYPE_TAG = "CreateStatsDieselFuelReserveType";
    private static final String GROUP_SOURCE_TYPE = "Diesel Engine Group";
    private static final String FUEL_RESERVE_SOURCE_TYPE = "Diesel Fuel Reserve";
    private static final String FUEL_AUTO = "auto";
    private static final String FUEL_OFF = "off";
    private static final String[] FUEL_RESERVE_TYPES = {FUEL_AUTO, "diesel", "gasoline", "biodiesel", "ethanol", "plantoil", FUEL_OFF};
    private static final int MAX_GROUP_SCAN_BLOCKS = 96;
    private static final int PUMPJACK_PART_SCAN_RADIUS = 14;
    private static final int MAX_GROUP_DISTANCE = 12;
    private static final String NORMAL_ENGINE_BE = "com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity";
    private static final String MODULAR_ENGINE_BE = "com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity";
    private static final String HUGE_ENGINE_BE = "com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity";
    private static final String DISTILLATION_TANK_BE = "com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity";
    private static final String PUMPJACK_HOLE_BE = "com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity";
    private static final String BURNER_BE = "com.jesz.createdieselgenerators.content.burner.BurnerBlockEntity";
    private static final String OIL_BARREL_BE = "com.jesz.createdieselgenerators.content.oil_barrel.OilBarrelBlockEntity";
    private static final Set<String> DIESEL_BLOCK_PATHS = Set.of(
            "diesel_engine",
            "large_diesel_engine",
            "modular_diesel_engine",
            "huge_diesel_engine",
            "distillation_tank",
            "pumpjack_hole",
                "pumpjack_bearing",
                "pumpjack_bearing_b",
                "pumpjack_crank",
                "pumpjack_head",
            "burner",
            "oil_barrel"
    );
    private static final Map<BlockEntity, CachedReadout> READOUT_CACHE = new WeakHashMap<>();

    private DieselCompatHelper() {
    }

    public static boolean supportsCompactAutoRead(@Nullable BlockEntity sourceBE) {
        return DieselCompat.isAvailable() && isDieselSource(sourceBE);
    }

    public static boolean supportsAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsCompactAutoRead(sourceBE);
    }

    public static boolean supportsStatsLinkBlock(@Nullable Block block) {
        if (!DieselCompat.isAvailable() || block == null) {
            return false;
        }
        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null && DieselCompat.MOD_ID.equals(key.getNamespace())) {
            final String path = key.getPath();
            return DIESEL_BLOCK_PATHS.contains(path)
                    || path.contains("pumpjack")
                    || path.contains("diesel_engine")
                    || path.contains("distillation")
                    || path.contains("oil_barrel")
                    || path.equals("burner");
        }
        final String className = block.getClass().getName();
        return className.startsWith("com.jesz.createdieselgenerators.")
                && (className.contains("DieselEngine") || className.contains("DistillationTank")
            || className.contains("Pumpjack") || className.contains("Burner") || className.contains("OilBarrel"));
    }

    public static boolean isFuelReserveCandidate(@Nullable BlockEntity sourceBE) {
        return sourceBE != null && !isDieselSource(sourceBE) && readFuelReserve(sourceBE, FUEL_AUTO, false) != null;
    }

    public static boolean hasFuelReserveConfig(@Nullable CompoundTag sourceConfig) {
        return sourceConfig != null && (sourceConfig.contains(DISPLAY_LINK_FUEL_RESERVE_TAG) || sourceConfig.contains(DISPLAY_LINK_FUEL_RESERVE_TYPE_TAG));
    }

    public static boolean isFuelReserveEnabled(@Nullable CompoundTag sourceConfig, @Nullable BlockEntity sourceBE) {
        if (sourceConfig != null && sourceConfig.contains(DISPLAY_LINK_FUEL_RESERVE_TAG)) {
            return sourceConfig.getBoolean(DISPLAY_LINK_FUEL_RESERVE_TAG) && !FUEL_OFF.equals(getFuelReserveType(sourceConfig, sourceBE));
        }
        return isFuelReserveCandidate(sourceBE);
    }

    public static String getFuelReserveType(@Nullable CompoundTag sourceConfig, @Nullable BlockEntity sourceBE) {
        if (sourceConfig != null && sourceConfig.contains(DISPLAY_LINK_FUEL_RESERVE_TYPE_TAG)) {
            return normalizeFuelReserveType(sourceConfig.getString(DISPLAY_LINK_FUEL_RESERVE_TYPE_TAG));
        }
        final FuelReserveInfo reserve = sourceBE != null ? readFuelReserve(sourceBE, FUEL_AUTO, false) : null;
        return reserve != null && !reserve.fuelKind().isEmpty() ? reserve.fuelKind() : FUEL_AUTO;
    }

    public static String nextFuelReserveType(String currentType) {
        final String current = normalizeFuelReserveType(currentType);
        for (int index = 0; index < FUEL_RESERVE_TYPES.length; index++) {
            if (FUEL_RESERVE_TYPES[index].equals(current)) {
                return FUEL_RESERVE_TYPES[(index + 1) % FUEL_RESERVE_TYPES.length];
            }
        }
        return FUEL_AUTO;
    }

    public static void setFuelReserveMode(CompoundTag sourceConfig, boolean enabled, String fuelType) {
        final String normalized = enabled ? normalizeFuelReserveType(fuelType) : FUEL_OFF;
        sourceConfig.putBoolean(DISPLAY_LINK_FUEL_RESERVE_TAG, enabled && !FUEL_OFF.equals(normalized));
        sourceConfig.putString(DISPLAY_LINK_FUEL_RESERVE_TYPE_TAG, normalized);
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return "";
        }
        if (isDieselEngine(sourceBE)) {
            if (matches(sourceBE, MODULAR_ENGINE_BE)) {
                return "Modular Diesel Engine";
            }
            if (matches(sourceBE, HUGE_ENGINE_BE)) {
                return "Huge Diesel Engine";
            }
            final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(sourceBE.getBlockState().getBlock());
            if (key != null) {
                if ("large_diesel_engine".equals(key.getPath()) || "modular_diesel_engine".equals(key.getPath())) {
                    return "Modular Diesel Engine";
                }
                if ("huge_diesel_engine".equals(key.getPath())) {
                    return "Huge Diesel Engine";
                }
            }
            return "Diesel Engine";
        }
        if (isDistillationTank(sourceBE)) {
            return "Diesel Distillation Tower";
        }
        if (isPumpjackHole(sourceBE) || resolvePumpjackHole(sourceBE) != null) {
            return "Oil Pumpjack";
        }
        if (isDieselBurner(sourceBE)) {
            return "Diesel Burner";
        }
        if (isOilBarrel(sourceBE)) {
            return "Oil Barrel";
        }
        return "";
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace) {
        final BlockEntity effectiveSourceBE = resolveDieselSource(sourceBE != null ? sourceBE.getLevel() : null, sourceBE != null ? sourceBE.getBlockPos() : null, sourceBE);
        if (!DieselCompat.isAvailable() || !isDieselSource(effectiveSourceBE)) {
            return null;
        }
        final InstrumentSourceHelper.SourceReadout cached = getCachedReadout(effectiveSourceBE);
        if (cached != null) {
            return cached;
        }
        if (!isDieselEngine(effectiveSourceBE)) {
            final InstrumentSourceHelper.SourceReadout processReadout = readProcessSource(effectiveSourceBE, accessFace);
            if (processReadout != null) {
                cacheReadout(effectiveSourceBE, processReadout);
            }
            return processReadout;
        }
        final BlockEntity controller = resolveController(effectiveSourceBE);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        putFuelMetrics(metrics, controller, accessFace);
        putEngineMetrics(metrics, effectiveSourceBE, controller);
        if (metrics.isEmpty()) {
            return null;
        }
        final InstrumentSourceHelper.SourceReadout readout = new InstrumentSourceHelper.SourceReadout(
                getSourceType(effectiveSourceBE), InstrumentSourceHelper.SourceKind.KINETIC_STRESS, metrics);
        cacheReadout(effectiveSourceBE, readout);
        return readout;
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(@Nullable Level level, @Nullable BlockPos sourcePos, @Nullable Direction accessFace) {
        final BlockEntity sourceBE = level != null && sourcePos != null ? level.getBlockEntity(sourcePos) : null;
        final BlockEntity effectiveSourceBE = resolveDieselSource(level, sourcePos, sourceBE);
        return effectiveSourceBE == null ? null : readSource(effectiveSourceBE, accessFace);
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace, boolean multiStructure) {
        if (multiStructure && isDieselEngine(sourceBE)) {
            final GroupedEngineReadout groupedReadout = readGroupedEngineSource(sourceBE);
            if (groupedReadout != null) {
                return groupedReadout.readout;
            }
        }
        return readSource(sourceBE, accessFace);
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE) {
        return emitAutoData(receiver, sourcePos, sourceBE, null);
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE, @Nullable CompoundTag sourceConfig) {
        return emitAutoData(receiver, sourcePos, sourceBE, sourceConfig, sourceBE != null ? sourceBE.getLevel() : null);
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos, @Nullable BlockEntity sourceBE, @Nullable CompoundTag sourceConfig, @Nullable Level level) {
        final BlockEntity effectiveSourceBE = resolveDieselSource(level, sourcePos, sourceBE);
        if (effectiveSourceBE == null) {
            return false;
        }
        if (isFuelReserveEnabled(sourceConfig, effectiveSourceBE) && emitFuelReserveData(receiver, sourcePos, effectiveSourceBE, sourceConfig)) {
            return true;
        }
        if (isDieselEngine(effectiveSourceBE) && isMultiStructureMode(sourceConfig, effectiveSourceBE)) {
            final GroupedEngineReadout groupedReadout = readGroupedEngineSource(effectiveSourceBE);
            if (groupedReadout != null && !groupedReadout.readout.metrics().isEmpty()) {
                receiver.setSourceType(groupedReadout.readout.sourceType());
                final Map<String, InstrumentSourceHelper.MetricSample> emittedMetrics = shouldShowRawMetrics()
                        ? groupedReadout.readout.metrics()
                        : curatedMetrics(groupedReadout.readout);
                for (InstrumentSourceHelper.MetricSample sample : emittedMetrics.values()) {
                    receiver.onAutoData(groupedReadout.anchor, groupedReadout.readout.sourceType(), sample.metricId(), sample.label(), sample.value(), sample.type());
                }
                return true;
            }
        }
        final InstrumentSourceHelper.SourceReadout readout = readSource(effectiveSourceBE, null);
        if (readout == null || readout.metrics().isEmpty()) {
            return false;
        }
        receiver.setSourceType(readout.sourceType());
        final Map<String, InstrumentSourceHelper.MetricSample> emittedMetrics = shouldShowRawMetrics()
            ? readout.metrics()
            : curatedMetrics(readout);
        for (InstrumentSourceHelper.MetricSample sample : emittedMetrics.values()) {
            receiver.onAutoData(sourcePos, readout.sourceType(), sample.metricId(), sample.label(), sample.value(), sample.type());
        }
        return true;
    }

    private static boolean emitFuelReserveData(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE, @Nullable CompoundTag sourceConfig) {
        final String selectedType = getFuelReserveType(sourceConfig, sourceBE);
        final FuelReserveInfo reserve = readFuelReserve(sourceBE, selectedType, hasFuelReserveConfig(sourceConfig));
        if (reserve == null) {
            return false;
        }
        receiver.setSourceType(FUEL_RESERVE_SOURCE_TYPE);
        receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_fuel_amount", reserve.label() + " Reserve (mB)", reserve.amount(), MetricType.FLUID_MB);
        receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_fuel_capacity", "Reserve Capacity (mB)", reserve.capacity(), MetricType.FLUID_MB);
        receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_fuel_fill_pct", "Reserve Fuel %", reserve.fillPercent(), MetricType.FILL_LEVEL);
        receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_fuel_mismatch", "Reserve Mismatch", reserve.mismatch() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_links", "Reserve Links", 1.0f, MetricType.GENERIC);
        final String metricKind = reserve.fuelKind().isEmpty() ? FUEL_AUTO : reserve.fuelKind();
        if (!FUEL_AUTO.equals(metricKind) && !"mixed".equals(metricKind)) {
            receiver.onAutoData(sourcePos, FUEL_RESERVE_SOURCE_TYPE, "reserve_" + metricKind + "_amount", reserve.label() + " (mB)", reserve.amount(), MetricType.FLUID_MB);
        }
        return true;
    }

    public static boolean isDieselSourceBlockEntity(@Nullable BlockEntity sourceBE) {
        return DieselCompat.isAvailable() && isDieselSource(sourceBE);
    }

    public static boolean isDieselEngineBlockEntity(@Nullable BlockEntity sourceBE) {
        return DieselCompat.isAvailable() && isDieselEngine(sourceBE);
    }

    public static boolean isMultiStructureMode(@Nullable CompoundTag sourceConfig, @Nullable BlockEntity sourceBE) {
        if (sourceConfig != null && sourceConfig.contains(DISPLAY_LINK_MULTI_TAG)) {
            return sourceConfig.getBoolean(DISPLAY_LINK_MULTI_TAG);
        }
        return isDefaultMultiEngine(sourceBE);
    }

    public static void setMultiStructureMode(CompoundTag sourceConfig, boolean multi) {
        sourceConfig.putBoolean(DISPLAY_LINK_MULTI_TAG, multi);
    }

    @Nullable
    public static AABB getDieselGroupBounds(@Nullable BlockEntity sourceBE) {
        if (!isDieselEngine(sourceBE)) {
            return sourceBE == null ? null : new AABB(sourceBE.getBlockPos());
        }
        final DieselEngineGroup group = detectEngineGroup(sourceBE);
        return group != null ? group.bounds : new AABB(sourceBE.getBlockPos());
    }

    private static void putFuelMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity controller, @Nullable Direction accessFace) {
        final IFluidHandler fluidHandler = resolveFluidHandler(controller, accessFace);
        if (fluidHandler == null) {
            return;
        }
        float totalFluid = 0.0f;
        float totalCapacity = 0.0f;
        String fuelName = "";
        for (int tankIndex = 0; tankIndex < fluidHandler.getTanks(); tankIndex++) {
            final FluidStack stack = fluidHandler.getFluidInTank(tankIndex);
            totalFluid += stack.getAmount();
            totalCapacity += fluidHandler.getTankCapacity(tankIndex);
            if (fuelName.isEmpty() && !stack.isEmpty()) {
                fuelName = stack.getHoverName().getString();
            }
        }
        final float fuelFillPercent = totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f;
        putSample(metrics, "fuel_fill_pct", "Fuel %", fuelFillPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "burn_pct", "Burn %", fuelFillPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "fuel_amount", "Fuel Amount (mB)", totalFluid, MetricType.FLUID_MB);
        putSample(metrics, "fuel_capacity", "Fuel Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
        putSample(metrics, "fuel_infinite", "Infinite Fuel", 0.0f, MetricType.BOOLEAN_FLAG);
        if (!fuelName.isEmpty()) {
            putSample(metrics, "fuel_present", "Fuel " + fuelName, 1.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readProcessSource(BlockEntity sourceBE, @Nullable Direction accessFace) {
        final BlockEntity controller = resolveController(sourceBE);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        putProcessFluidMetrics(metrics, controller, accessFace);
        putProcessMetrics(metrics, sourceBE, controller);
        if (metrics.isEmpty()) {
            return null;
        }
        final String sourceType = getSourceType(sourceBE);
        final InstrumentSourceHelper.SourceKind kind = isDieselBurner(sourceBE)
                ? InstrumentSourceHelper.SourceKind.BOILER
                : InstrumentSourceHelper.SourceKind.FLUID_STORAGE;
        return new InstrumentSourceHelper.SourceReadout(sourceType, kind, metrics);
    }

    private static void putProcessFluidMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity controller, @Nullable Direction accessFace) {
        final IFluidHandler fluidHandler = resolveFluidHandler(controller, accessFace);
        if (fluidHandler == null) {
            return;
        }
        float totalFluid = 0.0f;
        float totalCapacity = 0.0f;
        String fluidName = "";
        for (int tankIndex = 0; tankIndex < fluidHandler.getTanks(); tankIndex++) {
            final FluidStack stack = fluidHandler.getFluidInTank(tankIndex);
            totalFluid += stack.getAmount();
            totalCapacity += fluidHandler.getTankCapacity(tankIndex);
            if (fluidName.isEmpty() && !stack.isEmpty()) {
                fluidName = stack.getHoverName().getString();
            }
        }
        final float fillPercent = totalCapacity > 0.0f ? totalFluid * 100.0f / totalCapacity : 0.0f;
        putSample(metrics, "fluid_fill_pct", "Fluid %", fillPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "tank_fill", "Tank Fill %", fillPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "fluid_amount", "Fluid Amount (mB)", totalFluid, MetricType.FLUID_MB);
        putSample(metrics, "fluid_capacity", "Fluid Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
        putSample(metrics, "tank_capacity", "Tank Capacity (mB)", totalCapacity, MetricType.FLUID_MB);
        if (!fluidName.isEmpty()) {
            putSample(metrics, "fluid_present", "Fluid " + fluidName, 1.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private static void putProcessMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity sourceBE, BlockEntity controller) {
        if (isDistillationTank(sourceBE)) {
            final float rawProgress = Math.max(0.0f, Math.min(1.0f, readFieldFloat(controller, "progress")));
            final float processingTime = readFieldFloat(controller, "processingTime");
            final boolean processing = processingTime >= 0.0f || rawProgress > 0.0f;
            putSample(metrics, "processing", "Processing", processing ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "process_progress_pct", "Process %", processing ? (1.0f - rawProgress) * 100.0f : 0.0f, MetricType.FILL_LEVEL);
            putSample(metrics, "tower_width", "Tower Width", Math.max(1.0f, invokeFloat(controller, "getWidth")), MetricType.GENERIC);
            putSample(metrics, "tower_height", "Tower Height", Math.max(1.0f, invokeFloat(controller, "getHeight")), MetricType.GENERIC);
            putSample(metrics, "tower_blocks", "Tower Tanks", Math.max(1.0f, invokeFloat(controller, "getTotalTankSize")), MetricType.GENERIC);
            final Object heat = invoke(controller, "getHeat");
            final float heatLevel = heat != null ? enumOrdinal(heat) : enumOrdinal(readField(controller, "highestHeatLevel"));
            if (heatLevel >= 0.0f) {
                putSample(metrics, "heat_level", "Heat Level", heatLevel, MetricType.HEAT_LEVEL);
            }
        } else if (isPumpjackHole(sourceBE)) {
            final float oilAmount = readFieldFloat(sourceBE, "oilAmount");
            putSample(metrics, "oil_remaining_mb", "Oil Remaining (mB)", oilAmount, MetricType.FLUID_MB);
            putSample(metrics, "pumpjack_started", "Pumpjack Started", readFieldBoolean(sourceBE, "started") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "pumpjack_valid", "Pipe Valid", readFieldBoolean(sourceBE, "valid") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "pipe_length", "Pipe Length", Math.max(0.0f, readFieldFloat(sourceBE, "pipeLength")), MetricType.GENERIC);
        } else if (isDieselBurner(sourceBE)) {
            final float heat = Math.max(0.0f, readFieldFloat(sourceBE, "heat"));
            final float valve = Math.max(0.0f, Math.min(1.0f, readFieldFloat(sourceBE, "valveState")));
            putSample(metrics, "burner_heat", "Burner Heat", heat, MetricType.HEAT_LEVEL);
            putSample(metrics, "valve_pct", "Valve %", valve * 100.0f, MetricType.FILL_LEVEL);
            putSample(metrics, "burner_lit", "Burner Lit", heat > 0.0f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            putSample(metrics, "redstone_power", "Redstone Power", readFieldBoolean(sourceBE, "redstonePower") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        } else if (isOilBarrel(sourceBE)) {
            putSample(metrics, "barrel_width", "Barrel Width", Math.max(1.0f, invokeFloat(controller, "getWidth")), MetricType.GENERIC);
            putSample(metrics, "barrel_height", "Barrel Height", Math.max(1.0f, invokeFloat(controller, "getHeight")), MetricType.GENERIC);
            putSample(metrics, "barrel_blocks", "Barrel Blocks", Math.max(1.0f, invokeFloat(controller, "getTotalTankSize")), MetricType.GENERIC);
        }
    }

    private static void putEngineMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity sourceBE, BlockEntity controller) {
        final BlockEntity metricSource = resolveEngineMetricSource(controller);
        final float fuelSpeed = Math.abs(invokeFloat(controller, "getFuelSpeed"));
        final float engineGeneratedSpeed = Math.abs(invokeFloat(metricSource, "getGeneratedSpeed"));
        final float networkSpeed = metricSource instanceof KineticBlockEntity kinetic ? Math.abs(kinetic.getSpeed()) : 0.0f;
        final float generatedSpeed = networkSpeed > 0.01f ? networkSpeed : Math.max(engineGeneratedSpeed, fuelSpeed);
        final float stressImpact = Math.max(invokeFloat(metricSource, "calculateAddedStressCapacity"), invokeFloat(controller, "getFuelCapacity"));
        final float stressSpeed = networkSpeed > 0.01f ? networkSpeed : engineGeneratedSpeed;
        final float generatedStress = stressImpact > 0.0f && stressSpeed > 0.01f ? stressImpact * stressSpeed : 0.0f;
        final float burnRate = invokeFloat(controller, "getFuelBurnRate");
        final float remainingTicks = invokeFloat(controller, "getRemainingTicks");
        final boolean enabled = invokeBoolean(controller, "enabled");
        final boolean validFuel = invokeBoolean(controller, "validFS");
        final boolean redstoneDisabled = readPowered(sourceBE.getBlockState()) || readPowered(controller.getBlockState());
        final boolean running = networkSpeed > 0.01f || (enabled && validFuel && remainingTicks > 0.0f);

        putSample(metrics, "running", "Running", running ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "enabled", "Enabled", enabled ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "valid_fuel", "Valid Fuel", validFuel ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "redstone_disabled", "Redstone Disabled", redstoneDisabled ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        if (networkSpeed > 0.0f) {
            putSample(metrics, "rpm", "Network RPM", networkSpeed, MetricType.RPM);
        }
        if (generatedSpeed > 0.0f) {
            putSample(metrics, "generated_rpm", "Generated RPM", generatedSpeed, MetricType.RPM);
            putSample(metrics, "generated_speed", "Generated Speed", generatedSpeed, MetricType.RPM);
        }
        if (generatedStress > 0.0f) {
            putSample(metrics, "generated_stress_su", "Produced SU", generatedStress, MetricType.STRESS_SU);
        }
        if (controller instanceof KineticBlockEntity kinetic && KineticNetworkMetrics.supportsStressReadout(kinetic)) {
            final KineticNetworkMetrics.Readout network = KineticNetworkMetrics.read(kinetic);
            putSample(metrics, "stress_su", "Network Load (SU)", network.networkStress(), MetricType.STRESS_SU);
            putSample(metrics, "capacity_su", "Network Capacity (SU)", network.networkCapacity(), MetricType.STRESS_SU);
            putSample(metrics, "stress_pct", "Network Stress %", network.stressPercent(), MetricType.STRESS_PERCENT);
        }
        if (burnRate > 0.0f) {
            putSample(metrics, "fuel_burn_rate", "Burn Rate", burnRate, MetricType.WATER_SUPPLY);
        }
        if (remainingTicks > 0.0f) {
            putSample(metrics, "remaining_burn_ticks", "Remaining Burn", remainingTicks, MetricType.BURN_TIME);
            putSample(metrics, "burn_time", "Current Burn", remainingTicks, MetricType.BURN_TIME);
            putSample(metrics, "burn_total", "Total Burn", Math.max(remainingTicks, 1.0f), MetricType.BURN_TIME);
        }
        putSample(metrics, "superheated", "Superheated", 0.0f, MetricType.BOOLEAN_FLAG);
        final float engineCount = resolveEngineCount(controller);
        if (engineCount > 1.0f) {
            putSample(metrics, "engine_count", "Engine Blocks", engineCount, MetricType.ENGINE_COUNT);
        }
        final String upgradeName = resolveUpgradeName(controller);
        if (!upgradeName.isEmpty()) {
            putSample(metrics, "upgrade_installed", "Upgrade " + upgradeName, 1.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    @Nullable
    private static GroupedEngineReadout readGroupedEngineSource(BlockEntity sourceBE) {
        final DieselEngineGroup group = detectEngineGroup(sourceBE);
        if (group == null || group.enginePositions.isEmpty()) {
            return null;
        }
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return null;
        }
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        float fuelAmount = 0.0f;
        float fuelCapacity = 0.0f;
        float generatedSpeed = 0.0f;
        float generatedStress = 0.0f;
        float networkLoad = 0.0f;
        float networkCapacity = 0.0f;
        float fuelWeightedPercent = 0.0f;
        float weightedEngines = 0.0f;
        float okEngines = 0.0f;
        float stallingEngines = 0.0f;
        float offEngines = 0.0f;
        boolean anyRedstoneDisabled = false;
        boolean allValidFuel = true;

        final Set<BlockPos> countedControllers = new LinkedHashSet<>();
        for (BlockPos enginePos : group.enginePositions) {
            final BlockEntity engineBE = level.getBlockEntity(enginePos);
            if (!isDieselEngine(engineBE)) {
                continue;
            }
            final BlockEntity controller = resolveController(engineBE);
            if (!countedControllers.add(controller.getBlockPos().immutable())) {
                continue;
            }
            final InstrumentSourceHelper.SourceReadout single = readSource(controller, null);
            if (single == null || single.metrics().isEmpty()) {
                continue;
            }
            final Map<String, InstrumentSourceHelper.MetricSample> singleMetrics = single.metrics();
            final float engineUnits = Math.max(1.0f, sampleValue(singleMetrics, "engine_count", 1.0f));
            final float singleFuelAmount = sampleValue(singleMetrics, "fuel_amount", 0.0f);
            final float singleFuelCapacity = sampleValue(singleMetrics, "fuel_capacity", 0.0f);
            final float singleFuelPercent = sampleValue(singleMetrics, "fuel_fill_pct", sampleValue(singleMetrics, "burn_pct", singleFuelCapacity > 0.0f ? singleFuelAmount * 100.0f / singleFuelCapacity : 0.0f));
            final boolean running = sampleValue(singleMetrics, "running", 0.0f) > 0.5f || sampleValue(singleMetrics, "generated_speed", 0.0f) > 0.01f;
            final boolean validFuel = sampleValue(singleMetrics, "valid_fuel", singleFuelPercent > 0.0f ? 1.0f : 0.0f) > 0.5f;
            final boolean redstoneDisabled = sampleValue(singleMetrics, "redstone_disabled", 0.0f) > 0.5f;

            fuelAmount += singleFuelAmount;
            fuelCapacity += singleFuelCapacity;
            generatedSpeed = Math.max(generatedSpeed, sampleValue(singleMetrics, "generated_speed", sampleValue(singleMetrics, "generated_rpm", 0.0f)));
            generatedStress += sampleValue(singleMetrics, "generated_stress_su", 0.0f);
            networkLoad = Math.max(networkLoad, sampleValue(singleMetrics, "stress_su", 0.0f));
            networkCapacity = Math.max(networkCapacity, sampleValue(singleMetrics, "capacity_su", 0.0f));
            fuelWeightedPercent += singleFuelPercent * engineUnits;
            weightedEngines += engineUnits;
            anyRedstoneDisabled |= redstoneDisabled;
            allValidFuel &= validFuel;

            if (running && validFuel && !redstoneDisabled) {
                okEngines += engineUnits;
            } else if (validFuel && !redstoneDisabled && singleFuelPercent > 0.0f) {
                stallingEngines += engineUnits;
            } else {
                offEngines += engineUnits;
            }
        }

        final float engineCount = Math.max(1.0f, weightedEngines > 0.0f ? weightedEngines : group.enginePositions.size());
        final float fuelPercent = fuelCapacity > 0.0f
                ? Math.max(0.0f, Math.min(100.0f, fuelAmount * 100.0f / fuelCapacity))
                : (weightedEngines > 0.0f ? fuelWeightedPercent / weightedEngines : 0.0f);
        putSample(metrics, "burn_pct", "Fuel %", fuelPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "fuel_amount", "Fuel Amount (mB)", fuelAmount, MetricType.FLUID_MB);
        putSample(metrics, "fuel_capacity", "Fuel Capacity (mB)", fuelCapacity, MetricType.FLUID_MB);
        putSample(metrics, "rpm", "Group RPM", generatedSpeed, MetricType.RPM);
        putSample(metrics, "generated_speed", "Group RPM", generatedSpeed, MetricType.RPM);
        putSample(metrics, "generated_stress_su", "Produced SU", generatedStress, MetricType.STRESS_SU);
        putSample(metrics, "stress_su", "Network Load (SU)", networkLoad, MetricType.STRESS_SU);
        putSample(metrics, "capacity_su", "Network Capacity (SU)", networkCapacity, MetricType.STRESS_SU);
        putSample(metrics, "remaining_su", "Remaining (SU)", Math.max(0.0f, networkCapacity - networkLoad), MetricType.STRESS_SU);
        if (networkCapacity > 0.0f) {
            putSample(metrics, "stress_pct", "Network Stress %", Math.max(0.0f, Math.min(100.0f, networkLoad * 100.0f / networkCapacity)), MetricType.STRESS_PERCENT);
        }
        putSample(metrics, "engine_count", "Engines", engineCount, MetricType.ENGINE_COUNT);
        putSample(metrics, "ok_engines", "OK Engines", okEngines, MetricType.ENGINE_COUNT);
        putSample(metrics, "stalling_engines", "Stalling Engines", stallingEngines, MetricType.ENGINE_COUNT);
        putSample(metrics, "off_engines", "Off Engines", offEngines, MetricType.ENGINE_COUNT);
        putSample(metrics, "running", "Group Running", okEngines > 0.0f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "valid_fuel", "All Fuel Valid", allValidFuel ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "redstone_disabled", "Any Redstone Stop", anyRedstoneDisabled ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        final InstrumentSourceHelper.SourceReadout readout = new InstrumentSourceHelper.SourceReadout(
                GROUP_SOURCE_TYPE, InstrumentSourceHelper.SourceKind.KINETIC_STRESS, metrics);
        return new GroupedEngineReadout(group.anchor, group.bounds, readout);
    }

    @Nullable
    private static DieselEngineGroup detectEngineGroup(BlockEntity sourceBE) {
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return null;
        }
        final BlockEntity start = resolveController(sourceBE);
        final BlockPos origin = start.getBlockPos();
        final Queue<BlockPos> open = new ArrayDeque<>();
        final Set<BlockPos> visited = new LinkedHashSet<>();
        final Set<BlockPos> engines = new LinkedHashSet<>();
        open.add(origin);
        while (!open.isEmpty() && visited.size() < MAX_GROUP_SCAN_BLOCKS) {
            final BlockPos current = open.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (current.distManhattan(origin) > MAX_GROUP_DISTANCE) {
                continue;
            }
            if (!isEngineGroupConnector(level, current)) {
                continue;
            }
            final BlockEntity currentBE = level.getBlockEntity(current);
            if (isDieselEngine(currentBE)) {
                engines.add(current.immutable());
            }
            for (Direction direction : Direction.values()) {
                final BlockPos next = current.relative(direction);
                if (!visited.contains(next) && next.distManhattan(origin) <= MAX_GROUP_DISTANCE && isEngineGroupConnector(level, next)) {
                    open.add(next.immutable());
                }
            }
        }
        if (engines.isEmpty()) {
            engines.add(origin.immutable());
        }
        BlockPos anchor = null;
        AABB bounds = null;
        for (BlockPos enginePos : engines) {
            anchor = minPos(anchor, enginePos);
            final AABB blockBounds = new AABB(enginePos);
            bounds = bounds == null ? blockBounds : bounds.minmax(blockBounds);
        }
        if (anchor == null || bounds == null) {
            return null;
        }
        return new DieselEngineGroup(anchor, bounds, engines);
    }

    private static boolean isEngineGroupConnector(Level level, BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (isDieselEngine(blockEntity)) {
            return true;
        }
        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (key != null && "shaft".equals(key.getPath())) {
            return true;
        }
        final String blockClass = level.getBlockState(pos).getBlock().getClass().getSimpleName();
        final String beClass = blockEntity != null ? blockEntity.getClass().getSimpleName() : "";
        return blockClass.contains("Shaft") || beClass.contains("Shaft");
    }

    private static boolean isDefaultMultiEngine(@Nullable BlockEntity sourceBE) {
        return matches(sourceBE, MODULAR_ENGINE_BE) || matches(sourceBE, HUGE_ENGINE_BE)
                || "Modular Diesel Engine".equals(getSourceType(sourceBE))
                || "Huge Diesel Engine".equals(getSourceType(sourceBE))
                || hasMultipleEngineControllers(sourceBE);
    }

    private static boolean hasMultipleEngineControllers(@Nullable BlockEntity sourceBE) {
        if (!isDieselEngine(sourceBE)) {
            return false;
        }
        final DieselEngineGroup group = detectEngineGroup(sourceBE);
        if (group == null || group.enginePositions.size() < 2) {
            return false;
        }
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return false;
        }
        final Set<BlockPos> controllers = new LinkedHashSet<>();
        for (BlockPos enginePos : group.enginePositions) {
            final BlockEntity engineBE = level.getBlockEntity(enginePos);
            if (isDieselEngine(engineBE)) {
                controllers.add(resolveController(engineBE).getBlockPos().immutable());
            }
        }
        return controllers.size() > 1;
    }

    private static BlockPos minPos(@Nullable BlockPos current, BlockPos candidate) {
        if (current == null) {
            return candidate.immutable();
        }
        if (candidate.getX() != current.getX()) {
            return candidate.getX() < current.getX() ? candidate.immutable() : current;
        }
        if (candidate.getY() != current.getY()) {
            return candidate.getY() < current.getY() ? candidate.immutable() : current;
        }
        return candidate.getZ() < current.getZ() ? candidate.immutable() : current;
    }

    private static float sampleValue(Map<String, InstrumentSourceHelper.MetricSample> metrics, String metricId, float fallback) {
        final InstrumentSourceHelper.MetricSample sample = metrics.get(metricId);
        return sample != null ? sample.value() : fallback;
    }

    @Nullable
    private static IFluidHandler resolveFluidHandler(BlockEntity sourceBE, @Nullable Direction accessFace) {
        if (sourceBE instanceof FluidTankBlockEntity tank) {
            final FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                sourceBE = controller;
            }
            final IFluidHandler tankInventory = ((FluidTankBlockEntity)sourceBE).getTankInventory();
            if (tankInventory != null) {
                return tankInventory;
            }
        }
        if (sourceBE.getLevel() != null) {
            IFluidHandler fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), accessFace);
            if (fluidHandler == null) {
                fluidHandler = sourceBE.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, sourceBE.getBlockPos(), null);
            }
            if (fluidHandler != null) {
                return fluidHandler;
            }
        }
        final Object tank = invoke(sourceBE, "getTank");
        if (tank instanceof IFluidHandler fluidHandler) {
            return fluidHandler;
        }
        return null;
    }

    @Nullable
    private static FuelReserveInfo readFuelReserve(@Nullable BlockEntity sourceBE, String selectedType, boolean explicitReserve) {
        if (sourceBE == null || FUEL_OFF.equals(normalizeFuelReserveType(selectedType))) {
            return null;
        }
        final IFluidHandler fluidHandler = resolveFluidHandler(sourceBE, null);
        if (fluidHandler == null) {
            return null;
        }
        final String selected = normalizeFuelReserveType(selectedType);
        final boolean automatic = FUEL_AUTO.equals(selected);
        float amount = 0.0f;
        float capacity = 0.0f;
        boolean foundRecognizedFuel = false;
        boolean mismatch = false;
        String detectedKind = "";
        for (int tankIndex = 0; tankIndex < fluidHandler.getTanks(); tankIndex++) {
            capacity += Math.max(0.0f, fluidHandler.getTankCapacity(tankIndex));
            final FluidStack stack = fluidHandler.getFluidInTank(tankIndex);
            if (stack.isEmpty()) {
                continue;
            }
            final String kind = fuelKind(stack);
            if (kind.isEmpty()) {
                mismatch |= !automatic;
                continue;
            }
            foundRecognizedFuel = true;
            if (detectedKind.isEmpty()) {
                detectedKind = kind;
            } else if (!detectedKind.equals(kind)) {
                detectedKind = "mixed";
            }
            if (automatic || kind.equals(selected)) {
                amount += stack.getAmount();
            } else {
                mismatch = true;
            }
        }
        if (automatic && !foundRecognizedFuel) {
            return null;
        }
        if (!explicitReserve && !foundRecognizedFuel) {
            return null;
        }
        final String fuelKind = automatic ? (detectedKind.isEmpty() ? FUEL_AUTO : detectedKind) : selected;
        final float fillPercent = capacity > 0.0f ? Math.max(0.0f, Math.min(100.0f, amount * 100.0f / capacity)) : 0.0f;
        return new FuelReserveInfo(fuelKind, fuelLabel(fuelKind), amount, capacity, fillPercent, mismatch);
    }

    private static String fuelKind(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        final ResourceLocation key = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        final String path = key != null ? key.getPath().toLowerCase(Locale.ROOT) : "";
        final String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT).replace(" ", "_");
        final String value = path + "_" + displayName;
        if (value.contains("biodiesel")) {
            return "biodiesel";
        }
        if (value.contains("gasoline")) {
            return "gasoline";
        }
        if (value.contains("ethanol")) {
            return "ethanol";
        }
        if (value.contains("plantoil") || value.contains("plant_oil")) {
            return "plantoil";
        }
        if (value.contains("diesel")) {
            return "diesel";
        }
        return "";
    }

    private static String normalizeFuelReserveType(String fuelType) {
        final String normalized = fuelType == null ? FUEL_AUTO : fuelType.toLowerCase(Locale.ROOT).replace(" ", "_").replace("plant_oil", "plantoil");
        for (String candidate : FUEL_RESERVE_TYPES) {
            if (candidate.equals(normalized)) {
                return candidate;
            }
        }
        return FUEL_AUTO;
    }

    private static String fuelLabel(String fuelKind) {
        return switch (normalizeFuelReserveType(fuelKind)) {
            case "diesel" -> "Diesel";
            case "gasoline" -> "Gasoline";
            case "biodiesel" -> "Biodiesel";
            case "ethanol" -> "Ethanol";
            case "plantoil" -> "Plant Oil";
            default -> "Fuel";
        };
    }

    private static float resolveEngineCount(BlockEntity controller) {
        final float height = invokeFloat(controller, "getHeight");
        if (height > 0.0f) {
            return height;
        }
        final Object length = readField(controller, "length");
        return length instanceof Number number ? number.floatValue() : 1.0f;
    }

    private static String resolveUpgradeName(BlockEntity controller) {
        final Object upgrade = invoke(controller, "getUpgrade");
        if (upgrade == null) {
            return "";
        }
        final Object id = readField(upgrade, "id");
        final String raw = id != null ? safeObjectString(id) : safeObjectString(upgrade);
        if (raw.isBlank() || raw.endsWith(":empty") || raw.equalsIgnoreCase("empty")) {
            return "";
        }
        final int namespaceSeparator = raw.lastIndexOf(':');
        final String path = namespaceSeparator >= 0 ? raw.substring(namespaceSeparator + 1) : raw;
        final String[] parts = path.replace('-', '_').split("_");
        final StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static boolean isDieselEngine(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        if (matches(sourceBE, NORMAL_ENGINE_BE) || matches(sourceBE, MODULAR_ENGINE_BE) || matches(sourceBE, HUGE_ENGINE_BE)) {
            return true;
        }
        final String className = sourceBE.getClass().getName();
        return className.startsWith("com.jesz.createdieselgenerators.") && className.endsWith("DieselEngineBlockEntity");
    }

    private static boolean isDieselSource(@Nullable BlockEntity sourceBE) {
        return isDieselEngine(sourceBE) || isDistillationTank(sourceBE) || isPumpjackHole(sourceBE) || resolvePumpjackHole(sourceBE) != null || isDieselBurner(sourceBE) || isOilBarrel(sourceBE);
    }

    private static boolean isDistillationTank(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        final String className = sourceBE.getClass().getName();
        return matches(sourceBE, DISTILLATION_TANK_BE) || className.endsWith("DistillationTankBlockEntity");
    }

    private static boolean isPumpjackHole(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        final String className = sourceBE.getClass().getName();
        return matches(sourceBE, PUMPJACK_HOLE_BE) || className.endsWith("PumpjackHoleBlockEntity");
    }

    private static boolean isPumpjackPart(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null || isPumpjackHole(sourceBE)) {
            return false;
        }
        final String className = sourceBE.getClass().getName();
        return className.startsWith("com.jesz.createdieselgenerators.")
                && className.contains(".pumpjack.")
                && className.contains("Pumpjack");
    }

    private static boolean isPumpjackBlock(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null && DieselCompat.MOD_ID.equals(key.getNamespace()) && key.getPath().contains("pumpjack")) {
            return true;
        }
        final String className = block.getClass().getName();
        return className.startsWith("com.jesz.createdieselgenerators.") && className.contains("Pumpjack");
    }

    @Nullable
    private static BlockEntity resolveDieselSource(@Nullable Level level, @Nullable BlockPos sourcePos, @Nullable BlockEntity sourceBE) {
        if (sourceBE != null) {
            if (isPumpjackHole(sourceBE)) {
                return sourceBE;
            }
            if (isPumpjackPart(sourceBE)) {
                final BlockEntity linkedHole = resolvePumpjackHole(sourceBE);
                if (linkedHole != null) {
                    return linkedHole;
                }
            }
            return sourceBE;
        }
        if (level == null || sourcePos == null || !isPumpjackBlock(level.getBlockState(sourcePos).getBlock())) {
            return null;
        }
        return findNearestPumpjackHole(level, sourcePos, PUMPJACK_PART_SCAN_RADIUS);
    }

    @Nullable
    private static BlockEntity resolvePumpjackHole(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return null;
        }
        if (isPumpjackHole(sourceBE)) {
            return sourceBE;
        }
        if (!isPumpjackPart(sourceBE)) {
            return null;
        }
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return null;
        }
        final String[] linkedPosFields = {"bearingPos", "bearingBPos", "crankPos"};
        for (String fieldName : linkedPosFields) {
            final Object value = readField(sourceBE, fieldName);
            if (value instanceof BlockPos linkedPos) {
                final BlockEntity direct = level.getBlockEntity(linkedPos);
                if (isPumpjackHole(direct)) {
                    return direct;
                }
                final BlockEntity nearby = findNearestPumpjackHole(level, linkedPos, 6);
                if (nearby != null) {
                    return nearby;
                }
            }
        }
        return findNearestPumpjackHole(level, sourceBE.getBlockPos(), PUMPJACK_PART_SCAN_RADIUS);
    }

    @Nullable
    private static BlockEntity findNearestPumpjackHole(Level level, BlockPos origin, int radius) {
        BlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        final BlockPos min = origin.offset(-radius, -radius, -radius);
        final BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos candidatePos : BlockPos.betweenClosed(min, max)) {
            final BlockEntity candidate = level.getBlockEntity(candidatePos);
            if (!isPumpjackHole(candidate)) {
                continue;
            }
            final double distance = origin.distSqr(candidatePos);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isDieselBurner(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        final String className = sourceBE.getClass().getName();
        return matches(sourceBE, BURNER_BE) || className.endsWith("BurnerBlockEntity") && className.startsWith("com.jesz.createdieselgenerators.");
    }

    private static boolean isOilBarrel(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null) {
            return false;
        }
        final String className = sourceBE.getClass().getName();
        return matches(sourceBE, OIL_BARREL_BE) || className.endsWith("OilBarrelBlockEntity");
    }

    private static BlockEntity resolveController(BlockEntity sourceBE) {
        final Object controller = invoke(sourceBE, "getControllerBE");
        return controller instanceof BlockEntity blockEntity ? blockEntity : sourceBE;
    }

    private static BlockEntity resolveEngineMetricSource(BlockEntity controller) {
        if (matches(controller, HUGE_ENGINE_BE) || "Huge Diesel Engine".equals(getSourceType(controller))) {
            final Object shaft = invoke(controller, "getShaft");
            if (shaft instanceof BlockEntity shaftBE) {
                return shaftBE;
            }
        }
        return controller;
    }

    private static boolean readPowered(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!"powered".equals(property.getName())) {
                continue;
            }
            final Comparable<?> value = state.getValue(property);
            return value instanceof Boolean bool && bool;
        }
        return false;
    }

    private static boolean matches(@Nullable BlockEntity sourceBE, String className) {
        return sourceBE != null && sourceBE.getClass().getName().equals(className);
    }

    private static void putSample(Map<String, InstrumentSourceHelper.MetricSample> metrics, String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId, label, value, type));
    }

    private static boolean shouldShowRawMetrics() {
        return ModConfiguration.COMMON.showRawDieselMetrics.get();
    }

    private static Map<String, InstrumentSourceHelper.MetricSample> curatedMetrics(InstrumentSourceHelper.SourceReadout readout) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = readout.metrics();
        final Map<String, InstrumentSourceHelper.MetricSample> curated = new LinkedHashMap<>();
        final String sourceType = readout.sourceType();
        if (isDieselEngineSourceType(sourceType)) {
            putIfPresent(curated, metrics, "burn_pct");
            putIfPresent(curated, metrics, "fuel_amount");
            putIfPresent(curated, metrics, "fuel_capacity");
            putIfPresent(curated, metrics, "rpm");
            putIfPresent(curated, metrics, "generated_speed");
            putIfPresent(curated, metrics, "generated_stress_su");
            putIfPresent(curated, metrics, "stress_su");
            putIfPresent(curated, metrics, "capacity_su");
            putIfPresent(curated, metrics, "remaining_su");
            putIfPresent(curated, metrics, "stress_pct");
            putIfPresent(curated, metrics, "engine_count");
            putIfPresent(curated, metrics, "ok_engines");
            putIfPresent(curated, metrics, "stalling_engines");
            putIfPresent(curated, metrics, "off_engines");
            putIfPresent(curated, metrics, "running");
            putIfPresent(curated, metrics, "valid_fuel");
            putIfPresent(curated, metrics, "redstone_disabled");
            putIfPresent(curated, metrics, "upgrade_installed");
            return curated.isEmpty() ? metrics : curated;
        }
        if ("Diesel Distillation Tower".equals(sourceType)) {
            putIfPresent(curated, metrics, "fluid_fill_pct");
            putIfPresent(curated, metrics, "fluid_amount");
            putIfPresent(curated, metrics, "fluid_capacity");
            putIfPresent(curated, metrics, "processing");
            putIfPresent(curated, metrics, "process_progress_pct");
            putIfPresent(curated, metrics, "heat_level");
            putIfPresent(curated, metrics, "tower_width");
            putIfPresent(curated, metrics, "tower_height");
            putIfPresent(curated, metrics, "tower_blocks");
            return curated.isEmpty() ? metrics : curated;
        }
        if ("Oil Pumpjack".equals(sourceType)) {
            putIfPresent(curated, metrics, "oil_remaining_mb");
            putIfPresent(curated, metrics, "pumpjack_started");
            putIfPresent(curated, metrics, "pumpjack_valid");
            putIfPresent(curated, metrics, "pipe_length");
            return curated.isEmpty() ? metrics : curated;
        }
        if ("Diesel Burner".equals(sourceType)) {
            putIfPresent(curated, metrics, "burner_heat");
            putIfPresent(curated, metrics, "valve_pct");
            putIfPresent(curated, metrics, "burner_lit");
            return curated.isEmpty() ? metrics : curated;
        }
        if ("Oil Barrel".equals(sourceType)) {
            putIfPresent(curated, metrics, "fluid_fill_pct");
            putIfPresent(curated, metrics, "fluid_amount");
            putIfPresent(curated, metrics, "fluid_capacity");
            putIfPresent(curated, metrics, "barrel_blocks");
            return curated.isEmpty() ? metrics : curated;
        }
        return metrics;
    }

    private static boolean isDieselEngineSourceType(String sourceType) {
        return "Diesel Engine".equals(sourceType) || "Modular Diesel Engine".equals(sourceType) || "Huge Diesel Engine".equals(sourceType)
            || GROUP_SOURCE_TYPE.equals(sourceType);
    }

    private static void putIfPresent(Map<String, InstrumentSourceHelper.MetricSample> target, Map<String, InstrumentSourceHelper.MetricSample> source, String metricId) {
        final InstrumentSourceHelper.MetricSample sample = source.get(metricId);
        if (sample != null) {
            target.put(metricId, sample);
        }
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout getCachedReadout(BlockEntity sourceBE) {
        final long tick = sourceBE.getLevel() != null ? sourceBE.getLevel().getGameTime() : -1L;
        if (tick < 0L) {
            return null;
        }
        final CachedReadout cached = READOUT_CACHE.get(sourceBE);
        if (cached == null || cached.tick != tick) {
            if (cached != null) {
                READOUT_CACHE.remove(sourceBE);
            }
            return null;
        }
        return cached.readout;
    }

    private static void cacheReadout(BlockEntity sourceBE, InstrumentSourceHelper.SourceReadout readout) {
        final long tick = sourceBE.getLevel() != null ? sourceBE.getLevel().getGameTime() : -1L;
        if (tick >= 0L) {
            READOUT_CACHE.put(sourceBE, new CachedReadout(tick, readout));
        }
    }

    @Nullable
    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        final Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            parameterTypes[index] = primitiveType(args[index]);
        }
        try {
            final Method method = target.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String safeObjectString(Object value) {
        try {
            return value == null ? "" : value.toString();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static float invokeFloat(Object target, String name, Object... args) {
        final Object value = invoke(target, name, args);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static boolean invokeBoolean(Object target, String name, Object... args) {
        final Object value = invoke(target, name, args);
        return value instanceof Boolean bool && bool;
    }

    private static float readFieldFloat(Object target, String name) {
        final Object value = readField(target, name);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static boolean readFieldBoolean(Object target, String name) {
        final Object value = readField(target, name);
        return value instanceof Boolean bool && bool;
    }

    private static float enumOrdinal(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.ordinal() : -1.0f;
    }

    @Nullable
    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> primitiveType(Object arg) {
        if (arg instanceof Integer) {
            return int.class;
        }
        if (arg instanceof Float) {
            return float.class;
        }
        if (arg instanceof Boolean) {
            return boolean.class;
        }
        if (arg instanceof Direction) {
            return Direction.class;
        }
        return arg != null ? arg.getClass() : Object.class;
    }

    private record CachedReadout(long tick, InstrumentSourceHelper.SourceReadout readout) {
    }

    private record FuelReserveInfo(String fuelKind, String label, float amount, float capacity, float fillPercent, boolean mismatch) {
    }

    private record DieselEngineGroup(BlockPos anchor, AABB bounds, Set<BlockPos> enginePositions) {
    }

    private record GroupedEngineReadout(BlockPos anchor, AABB bounds, InstrumentSourceHelper.SourceReadout readout) {
    }
}