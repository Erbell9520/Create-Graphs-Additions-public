package com.createstats.compat;

import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.data.MetricType;
import com.createstats.power.CouplerForeign;
import com.createstats.power.CreateAdditionFeed;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class CreateAdditionCompatHelper {
    private static final String ALTERNATOR_BE = "com.mrh0.createaddition.blocks.alternator.AlternatorBlockEntity";
    private static final String ELECTRIC_MOTOR_BE = "com.mrh0.createaddition.blocks.electric_motor.ElectricMotorBlockEntity";
    private static final String MODULAR_ACCUMULATOR_BE = "com.mrh0.createaddition.blocks.modular_accumulator.ModularAccumulatorBlockEntity";
    private static final String PORTABLE_ENERGY_INTERFACE_BE = "com.mrh0.createaddition.blocks.portable_energy_interface.PortableEnergyInterfaceBlockEntity";
    private static final Set<String> STATS_LINK_BLOCK_PATHS = Set.of(
            "alternator",
            "electric_motor",
            "modular_accumulator",
            "portable_energy_interface",
            "connector",
            "small_light_connector",
            "large_connector",
            "creative_energy",
            "digital_adapter",
            "tesla_coil",
            "redstone_relay"
    );
    private static final Map<BlockEntity, CachedReadout> READOUT_CACHE = new WeakHashMap<>();
    private static final Map<BlockEntity, EnergyAccumulatorState> ENERGY_STATES = new WeakHashMap<>();

    private CreateAdditionCompatHelper() {
    }

    @Nullable
    public static IEnergyStorage seamMouth(@Nullable BlockEntity be) {
        if (be == null || !CouplerForeign.isCreateAdditionConnector(be.getClass().getName())) {
            return null;
        }
        final Method[] calls = CONNECTOR_CALLS.get(be.getClass());
        return calls == null ? null : CreateAdditionFeed.mouth(new SeamConnector(be, calls));
    }

    private static final ClassValue<Method[]> CONNECTOR_CALLS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            return methods(type, new String[]{"getNetwork", "getMaxIn", "getMaxOut"},
                    new Class<?>[][]{{int.class}, {}, {}});
        }
    };

    private static final ClassValue<Method[]> NETWORK_CALLS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            return methods(type, new String[]{"isValid", "pull", "push", "demand"},
                    new Class<?>[][]{{}, {int.class, boolean.class}, {int.class, boolean.class}, {int.class}});
        }
    };

    @Nullable
    private static Method[] methods(Class<?> type, String[] names, Class<?>[][] parameters) {
        final Method[] out = new Method[names.length];
        try {
            for (int i = 0; i < names.length; i++) {
                out[i] = type.getMethod(names[i], parameters[i]);
                out[i].setAccessible(true);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError missing) {
            return null;
        }
    }

    @Nullable
    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return null;
        }
    }

    private record SeamConnector(BlockEntity be, Method[] calls) implements CreateAdditionFeed.Connector {
        @Nullable
        @Override
        public CreateAdditionFeed.Network network() {
            if (this.be.isRemoved()) {
                return null;
            }

            final Object network = call(this.calls[0], this.be, 0);
            if (network == null) {
                return null;
            }
            final Method[] on = NETWORK_CALLS.get(network.getClass());
            if (on == null || !(call(on[0], network) instanceof Boolean valid) || !valid) {
                return null;
            }
            return new SeamNetwork(network, on);
        }

        @Override
        public int maxIn() {
            return call(this.calls[1], this.be) instanceof Integer v ? v : 0;
        }

        @Override
        public int maxOut() {
            return call(this.calls[2], this.be) instanceof Integer v ? v : 0;
        }
    }

    private record SeamNetwork(Object network, Method[] calls) implements CreateAdditionFeed.Network {
        @Override
        public int pull(int energy, boolean simulate) {
            return call(this.calls[1], this.network, energy, simulate) instanceof Integer v ? v : 0;
        }

        @Override
        public int push(int energy, boolean simulate) {
            return call(this.calls[2], this.network, energy, simulate) instanceof Integer v ? v : 0;
        }

        @Override
        public void demand(int energy) {
            call(this.calls[3], this.network, energy);
        }
    }

    public static boolean supportsCompactAutoRead(@Nullable BlockEntity sourceBE) {
        if (!CreateAdditionCompat.isAvailable() || sourceBE == null || !isCreateAdditionBlockEntity(sourceBE)) {
            return false;
        }
        return isKnownCreateAdditionEnergySource(sourceBE) || resolveEnergyStorage(sourceBE, null) != null || isWireNode(sourceBE);
    }

    public static boolean supportsAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsCompactAutoRead(sourceBE);
    }

    public static boolean supportsStatsLinkBlock(@Nullable Block block) {
        if (!CreateAdditionCompat.isAvailable() || block == null) {
            return false;
        }
        final var key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null && CreateAdditionCompat.MOD_ID.equals(key.getNamespace())) {
            return STATS_LINK_BLOCK_PATHS.contains(key.getPath()) || key.getPath().contains("connector");
        }
        final String className = block.getClass().getName();
        return className.startsWith("com.mrh0.createaddition.")
                && (className.contains("Alternator")
                || className.contains("ElectricMotor")
                || className.contains("Accumulator")
                || className.contains("EnergyInterface")
                || className.contains("Connector")
                || className.contains("TeslaCoil")
                || className.contains("CreativeEnergy"));
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null || !isCreateAdditionBlockEntity(sourceBE)) {
            return "";
        }
        if (matches(sourceBE, MODULAR_ACCUMULATOR_BE)) {
            return "Modular Accumulator";
        }
        if (matches(sourceBE, ALTERNATOR_BE)) {
            return "Alternator";
        }
        if (matches(sourceBE, ELECTRIC_MOTOR_BE)) {
            return "Electric Motor";
        }
        if (matches(sourceBE, PORTABLE_ENERGY_INTERFACE_BE)) {
            return "Portable Energy Interface";
        }
        final String simpleName = sourceBE.getClass().getSimpleName();
        if (simpleName.contains("Connector")) {
            return simpleName.contains("Large") ? "Large Energy Connector" : "Energy Connector";
        }
        if (simpleName.contains("TeslaCoil")) {
            return "Tesla Coil";
        }
        if (simpleName.contains("CreativeEnergy")) {
            return "Creative Energy Source";
        }
        return sourceBE.getBlockState().getBlock().getName().getString();
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        if (!CreateAdditionCompat.isAvailable() || sourceBE == null || !isCreateAdditionBlockEntity(sourceBE)) {
            return null;
        }
        if (accessFace == null && trackedItems.isEmpty()) {
            final InstrumentSourceHelper.SourceReadout cached = getCachedReadout(sourceBE);
            if (cached != null) {
                return cached;
            }
        }
        final InstrumentSourceHelper.SourceReadout readout = readSourceUncached(sourceBE, accessFace);
        if (readout != null && accessFace == null && trackedItems.isEmpty()) {
            cacheReadout(sourceBE, readout);
        }
        return readout;
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE) {
        final InstrumentSourceHelper.SourceReadout readout = readSource(sourceBE, null, java.util.List.of());
        if (readout == null || readout.metrics().isEmpty()) {
            return false;
        }
        emitReadout(receiver, sourcePos, readout);
        return true;
    }

    public static void emitReadout(IAutoDataReceiver receiver, BlockPos sourcePos, InstrumentSourceHelper.SourceReadout readout) {
        receiver.setSourceType(readout.sourceType());
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            receiver.onAutoData(sourcePos, readout.sourceType(), sample.metricId(), sample.label(), sample.value(), sample.type());
        }
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readSourceUncached(BlockEntity sourceBE, @Nullable Direction accessFace) {
        final BlockEntity controller = resolveController(sourceBE);
        final IEnergyStorage energy = resolveEnergyStorage(controller, accessFace);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        if (energy != null) {
            putEnergyMetrics(metrics, controller, energy);
        }
        putCreateAdditionSpecificMetrics(metrics, sourceBE, controller, energy);
        if (metrics.isEmpty()) {
            return null;
        }
        return new InstrumentSourceHelper.SourceReadout(getSourceType(sourceBE), InstrumentSourceHelper.SourceKind.ENERGY_STORAGE, metrics);
    }

    private static void putCreateAdditionSpecificMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity sourceBE,
                                                         BlockEntity controller, @Nullable IEnergyStorage energy) {
        if (sourceBE instanceof KineticBlockEntity kinetic) {
            final float rpm = Math.abs(kinetic.getSpeed());
            putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
            putSample(metrics, "running", "Running", rpm > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
        if (matches(sourceBE, MODULAR_ACCUMULATOR_BE)) {
            final float fillState = invokeFloat(controller, "getFillState");
            if (fillState > 0.0f || !metrics.containsKey("energy_fill_pct")) {
                putSample(metrics, "energy_fill_pct", "Energy %", Math.max(0.0f, Math.min(100.0f, fillState * 100.0f)), MetricType.FILL_LEVEL);
            }
            putSample(metrics, "accumulator_size", "Accumulator Blocks", invokeFloat(controller, "getTotalAccumulatorSize"), MetricType.ITEM_COUNT);
            putSample(metrics, "accumulator_width", "Width", invokeFloat(controller, "getWidth"), MetricType.ITEM_COUNT);
            putSample(metrics, "accumulator_height", "Height", invokeFloat(controller, "getHeight"), MetricType.ITEM_COUNT);
            putSample(metrics, "connector_count", "Connectors", countNearbyConnectors(controller), MetricType.ITEM_COUNT);
        }
        if (matches(sourceBE, ALTERNATOR_BE)) {
            final int rpm = Math.round(Math.abs(resolveKineticSpeed(sourceBE)));
            putSample(metrics, "energy_output_rate", "Generated FE/t", invokeFloat(sourceBE, "getEnergyProductionRate", rpm), MetricType.ENERGY_FE_PER_TICK);
        }
        if (matches(sourceBE, ELECTRIC_MOTOR_BE)) {
            final float rpm = invokeFloat(sourceBE, "getRPM");
            if (rpm != 0.0f) {
                putSample(metrics, "rpm", "RPM", Math.abs(rpm), MetricType.RPM);
            }
            final float consumed = Math.max(invokeFloat(sourceBE, "getEnergyConsumption"), invokeFloat(sourceBE, "getEnergyConsumptionRate", rpm));
            putSample(metrics, "energy_input_rate", "Consumed FE/t", consumed, MetricType.ENERGY_FE_PER_TICK);
            putSample(metrics, "stress_su", "Generated Stress (SU)", invokeFloat(sourceBE, "getGeneratedStress"), MetricType.STRESS_SU);
        }
        if (matches(sourceBE, PORTABLE_ENERGY_INTERFACE_BE)) {
            putSample(metrics, "connected", "Connected", invokeBoolean(sourceBE, "isConnected") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            if (energy == null) {
                final float stored = invokeFloat(sourceBE, "getEnergy");
                final float capacity = invokeFloat(sourceBE, "getCapacity");
                putSample(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
                putSample(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
                putSample(metrics, "energy_fill_pct", "Energy %", capacity > 0.0f ? stored * 100.0f / capacity : 0.0f, MetricType.FILL_LEVEL);
            }
        }
        if (isWireNode(sourceBE)) {
            final int nodeCount = Math.max(0, Math.round(invokeFloat(sourceBE, "getNodeCount")));
            int connected = 0;
            for (int node = 0; node < nodeCount; node++) {
                if (invokeBoolean(sourceBE, "hasConnection", node)) {
                    connected++;
                    final Object network = invoke(sourceBE, "getNetwork", node);
                    if (network != null) {
                        putSample(metrics, "network_buffer", "Network Buffer", invokeFloat(network, "getBuff"), MetricType.ENERGY_FE);
                        putSample(metrics, "network_demand", "Network Demand", invokeFloat(network, "getDemand"), MetricType.ENERGY_FE_PER_TICK);
                        putSample(metrics, "network_pulled", "Network Pulled", invokeFloat(network, "getPulled"), MetricType.ENERGY_FE_PER_TICK);
                        putSample(metrics, "network_pushed", "Network Pushed", invokeFloat(network, "getPushed"), MetricType.ENERGY_FE_PER_TICK);
                    }
                }
            }
            putSample(metrics, "wire_nodes", "Wire Nodes", nodeCount, MetricType.ITEM_COUNT);
            putSample(metrics, "connector_count", "Connections", connected, MetricType.ITEM_COUNT);
        }
    }

    public static void putGenericEnergyMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity sourceBE, IEnergyStorage energy) {
        putEnergyMetrics(metrics, sourceBE, energy);
    }

    public static int generationPotentialFePerTick(@Nullable BlockEntity sourceBE) {
        if (!CreateAdditionCompat.isAvailable() || sourceBE == null
                || !isCreateAdditionBlockEntity(sourceBE)) {
            return 0;
        }
        if (matches(sourceBE, ALTERNATOR_BE)) {
            final int rpm = Math.round(Math.abs(resolveKineticSpeed(sourceBE)));
            return Math.max(0, Math.round(invokeFloat(sourceBE, "getEnergyProductionRate", rpm)));
        }
        return 0;
    }

    public static int consumptionFePerTick(@Nullable BlockEntity sourceBE) {
        if (!CreateAdditionCompat.isAvailable() || sourceBE == null
                || !isCreateAdditionBlockEntity(sourceBE)) {
            return -1;
        }
        if (matches(sourceBE, ELECTRIC_MOTOR_BE)) {
            if (invokeBoolean(sourceBE, "isPoweredState")) {
                return 0;
            }
            final float rpm = invokeFloat(sourceBE, "getRPM");
            final float consumed = Math.max(invokeFloat(sourceBE, "getEnergyConsumption"),
                    invokeFloat(sourceBE, "getEnergyConsumptionRate", rpm));
            return Math.max(0, Math.round(consumed));
        }
        return -1;
    }

    @Nullable
    public static BlockPos controllerPosOf(@Nullable BlockEntity sourceBE) {
        if (!CreateAdditionCompat.isAvailable() || sourceBE == null
                || !isCreateAdditionBlockEntity(sourceBE)) {
            return null;
        }
        final BlockEntity controller = resolveController(sourceBE);
        return controller == null ? null : controller.getBlockPos();
    }

    private static void putEnergyMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics, BlockEntity sourceBE, IEnergyStorage energy) {
        final int stored = Math.max(0, energy.getEnergyStored());
        final int capacity = Math.max(0, energy.getMaxEnergyStored());
        final float fillPct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;
        putSample(metrics, "energy_fill_pct", "Energy %", fillPct, MetricType.FILL_LEVEL);
        putSample(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
        putSample(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
        putSample(metrics, "energy_can_receive", "Can Receive", energy.canReceive() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "energy_can_extract", "Can Extract", energy.canExtract() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        final EnergyFlowSnapshot flow = sampleEnergyFlow(sourceBE, energy, stored);
        putSample(metrics, "energy_net_rate", "Net FE/t", flow.netRate(), MetricType.ENERGY_FE_PER_TICK);
        putSample(metrics, "energy_input_rate", "Input FE/t", flow.inputRate(), MetricType.ENERGY_FE_PER_TICK);
        putSample(metrics, "energy_output_rate", "Output FE/t", flow.outputRate(), MetricType.ENERGY_FE_PER_TICK);
        putSample(metrics, "energy_total_in", "Total FE In", flow.totalIn(), MetricType.ENERGY_FE);
        putSample(metrics, "energy_total_out", "Total FE Out", flow.totalOut(), MetricType.ENERGY_FE);
    }

    @Nullable
    private static IEnergyStorage resolveEnergyStorage(BlockEntity sourceBE, @Nullable Direction accessFace) {
        if (sourceBE == null || sourceBE.getLevel() == null) {
            return null;
        }
        IEnergyStorage storage = sourceBE.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, sourceBE.getBlockPos(), accessFace);
        if (storage == null) {
            storage = sourceBE.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, sourceBE.getBlockPos(), null);
        }
        return storage;
    }

    private static EnergyFlowSnapshot sampleEnergyFlow(BlockEntity sourceBE, IEnergyStorage energy, int stored) {
        final long tick = sourceBE.getLevel() != null ? sourceBE.getLevel().getGameTime() : 0L;
        EnergyAccumulatorState state = ENERGY_STATES.get(sourceBE);
        if (state == null || tick <= state.lastTick) {
            if (state == null) {
                state = new EnergyAccumulatorState(tick, stored, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
                ENERGY_STATES.put(sourceBE, state);
            }
            return new EnergyFlowSnapshot(state.netRate, state.inputRate, state.outputRate, state.totalIn, state.totalOut);
        }
        final int delta = stored - state.lastStored;
        final long elapsed = Math.max(1L, tick - state.lastTick);
        final float netRate = delta / (float) elapsed;
        final float estimatedOutputRate = estimateAccumulatorOutputRate(sourceBE, energy);
        final float outputRate = Math.max(estimatedOutputRate, Math.max(0.0f, -netRate));
        final float inputRate = Math.max(0.0f, netRate + outputRate);
        state.lastTick = tick;
        state.lastStored = stored;
        state.netRate = netRate;
        state.inputRate = inputRate;
        state.outputRate = outputRate;
        state.totalIn += inputRate * elapsed;
        state.totalOut += outputRate * elapsed;
        return new EnergyFlowSnapshot(state.netRate, state.inputRate, state.outputRate, state.totalIn, state.totalOut);
    }

    private static float estimateAccumulatorOutputRate(BlockEntity sourceBE, IEnergyStorage energy) {
        if (!matches(sourceBE, MODULAR_ACCUMULATOR_BE) || sourceBE.getLevel() == null || sourceBE.getLevel().isClientSide()) {
            return 0.0f;
        }
        int available = Math.max(0, energy.getEnergyStored());
        int output = 0;
        final int width = Math.max(1, Math.round(invokeFloat(sourceBE, "getWidth")));
        final int height = Math.max(1, Math.round(invokeFloat(sourceBE, "getHeight")));
        final int horizontalRadius = Math.max(2, width + 1);
        final int verticalRadius = Math.max(2, height + 1);
        final BlockPos center = sourceBE.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius), center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (available <= 0) {
                break;
            }
            final BlockEntity candidate = sourceBE.getLevel().getBlockEntity(pos);
            if (!isSameAccumulatorController(candidate, sourceBE)) {
                continue;
            }
            for (Direction direction : new Direction[] { Direction.UP, Direction.DOWN }) {
                if (available <= 0 || !isAccumulatorOutputSide(candidate, direction)) {
                    continue;
                }
                final int extracted = estimateAccumulatorOutputSide(candidate, direction, energy, available);
                output += extracted;
                available -= extracted;
            }
        }
        return output;
    }

    private static int estimateAccumulatorOutputSide(BlockEntity outputBE, Direction direction, IEnergyStorage energy, int available) {
        if (outputBE.getLevel() == null) {
            return 0;
        }
        final BlockPos targetPos = outputBE.getBlockPos().relative(direction);
        final IEnergyStorage target = outputBE.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, direction.getOpposite());
        if (target == null || !target.canReceive()) {
            return 0;
        }
        final int request = Math.max(0, Math.min(available, energy.extractEnergy(Integer.MAX_VALUE, true)));
        if (request <= 0) {
            return 0;
        }
        final int accepted = Math.max(0, target.receiveEnergy(request, true));
        if (accepted <= 0) {
            return 0;
        }
        return Math.max(0, energy.extractEnergy(Math.min(accepted, request), true));
    }

    private static boolean isSameAccumulatorController(@Nullable BlockEntity candidate, BlockEntity controller) {
        if (!matches(candidate, MODULAR_ACCUMULATOR_BE)) {
            return false;
        }
        final BlockEntity candidateController = resolveController(candidate);
        return candidateController == controller || candidateController.getBlockPos().equals(controller.getBlockPos());
    }

    private static boolean isAccumulatorOutputSide(BlockEntity sourceBE, Direction direction) {
        if (direction != Direction.UP && direction != Direction.DOWN) {
            return false;
        }
        final String propertyName = direction == Direction.UP ? "top" : "bottom";
        final BlockState state = sourceBE.getBlockState();
        for (Property<?> property : state.getProperties()) {
            if (!propertyName.equals(property.getName())) {
                continue;
            }
            final Comparable<?> value = state.getValue(property);
            return value instanceof Boolean bool && bool;
        }
        return false;
    }

    private static int countNearbyConnectors(BlockEntity sourceBE) {
        final Level level = sourceBE.getLevel();
        if (level == null) {
            return 0;
        }
        int count = 0;
        final BlockPos center = sourceBE.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (pos.equals(center)) {
                continue;
            }
            final BlockEntity candidate = level.getBlockEntity(pos);
            if (candidate != null && isCreateAdditionBlockEntity(candidate) && candidate.getClass().getSimpleName().contains("Connector")) {
                count++;
            }
        }
        return count;
    }

    private static boolean isKnownCreateAdditionEnergySource(BlockEntity sourceBE) {
        return matches(sourceBE, ALTERNATOR_BE)
                || matches(sourceBE, ELECTRIC_MOTOR_BE)
                || matches(sourceBE, MODULAR_ACCUMULATOR_BE)
                || matches(sourceBE, PORTABLE_ENERGY_INTERFACE_BE)
                || sourceBE.getClass().getSimpleName().contains("Connector")
                || sourceBE.getClass().getSimpleName().contains("Energy");
    }

    private static boolean isCreateAdditionBlockEntity(BlockEntity sourceBE) {
        final var key = BuiltInRegistries.BLOCK.getKey(sourceBE.getBlockState().getBlock());
        return (key != null && CreateAdditionCompat.MOD_ID.equals(key.getNamespace()))
                || sourceBE.getClass().getName().startsWith("com.mrh0.createaddition.");
    }

    private static boolean isWireNode(BlockEntity sourceBE) {
        return hasMethod(sourceBE, "getNodeCount") && hasMethod(sourceBE, "hasConnection", int.class);
    }

    private static BlockEntity resolveController(BlockEntity sourceBE) {
        final Object controller = invoke(sourceBE, "getControllerBE");
        return controller instanceof BlockEntity blockEntity ? blockEntity : sourceBE;
    }

    private static float resolveKineticSpeed(BlockEntity sourceBE) {
        if (sourceBE instanceof KineticBlockEntity kinetic) {
            return kinetic.getSpeed();
        }
        return invokeFloat(sourceBE, "getSpeed");
    }

    private static boolean matches(@Nullable BlockEntity sourceBE, String className) {
        return sourceBE != null && sourceBE.getClass().getName().equals(className);
    }

    private static void putSample(Map<String, InstrumentSourceHelper.MetricSample> metrics, String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId, label, value, type));
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

    private static boolean hasMethod(Object target, String name, Class<?>... parameterTypes) {
        if (target == null) {
            return false;
        }
        try {
            target.getClass().getMethod(name, parameterTypes);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Nullable
    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        final Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = primitiveType(args[i]);
        }
        try {
            final Method method = target.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
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
            } catch (ReflectiveOperationException ignored) {
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

    private record EnergyFlowSnapshot(float netRate, float inputRate, float outputRate, float totalIn, float totalOut) {
    }

    private static final class EnergyAccumulatorState {
        long lastTick;
        int lastStored;
        float netRate;
        float inputRate;
        float outputRate;
        float totalIn;
        float totalOut;

        EnergyAccumulatorState(long lastTick, int lastStored, float netRate, float inputRate, float outputRate, float totalIn, float totalOut) {
            this.lastTick = lastTick;
            this.lastStored = lastStored;
            this.netRate = netRate;
            this.inputRate = inputRate;
            this.outputRate = outputRate;
            this.totalIn = totalIn;
            this.totalOut = totalOut;
        }
    }
}
