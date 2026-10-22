package com.createstats.compat;

import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// new age is a push network. this whole helper only makes sense once you know that
public final class NewAgeCompatHelper {
    private static final String CONNECTOR_BASE =
            "org.antarcticgardens.cna.content.electricity.connector.AbstractElectricalConnector";
    private static final String CONNECTOR_BE =
            "org.antarcticgardens.cna.content.electricity.connector.ElectricalConnectorBlockEntity";
    private static final String CARBON_BRUSHES_BE =
            "org.antarcticgardens.cna.content.electricity.generation.brushes.CarbonBrushesBlockEntity";
    private static final String GENERATOR_COIL_BE =
            "org.antarcticgardens.cna.content.electricity.generation.coil.GeneratorCoilBlockEntity";
    private static final String MOTOR_BE =
            "org.antarcticgardens.cna.content.motor.MotorBlockEntity";
    private static final String MOTOR_EXTENSION_BE =
            "org.antarcticgardens.cna.content.motor.extension.MotorExtensionBlockEntity";
    private static final String ENERGISER_BE =
            "org.antarcticgardens.cna.content.energising.EnergiserBlockEntity";
    private static final String STREET_LIGHT_BE =
            "org.antarcticgardens.cna.content.electricity.light.StreetLightBlockEntity";
    private static final String STIRLING_BE =
            "org.antarcticgardens.cna.content.heat.stirling.StirlingEngineBlockEntity";
    private static final String HEAT_INTERFACE =
            "org.antarcticgardens.cna.content.heat.HeatBlockEntity";
    private static final String REACTOR_ROD_BE =
            "org.antarcticgardens.cna.content.nuclear.reactor.rod.ReactorRodBlockEntity";
    private static final String REACTOR_VENT_BE =
            "org.antarcticgardens.cna.content.nuclear.reactor.vent.ReactorHeatVentBlockEntity";
    private static final String HEAT_PIPE_BE =
            "org.antarcticgardens.cna.content.heat.pipe.HeatPipeBlockEntity";
    private static final String HEAT_PUMP_BE =
            "org.antarcticgardens.cna.content.heat.pump.HeatPumpBlockEntity";
    private static final String SOLAR_PLATE_BE =
            "org.antarcticgardens.cna.content.heat.plate.SolarHeatingPlateBlockEntity";
    private static final String HEATER_BE =
            "org.antarcticgardens.cna.content.heat.heater.HeaterBlockEntity";

    private static final ResourceLocation ELECTRICAL_CONNECTOR =
            ResourceLocation.fromNamespaceAndPath(NewAgeCompat.MOD_ID, "electrical_connector");

    private static final Set<String> STATS_LINK_BLOCK_PATHS = Set.of(
            "electrical_connector", "carbon_brushes", "generator_coil",
            "basic_motor", "advanced_motor", "reinforced_motor",
            "basic_motor_extension", "advanced_motor_extension",
            "basic_energiser", "advanced_energiser", "reinforced_energiser",
            "street_light", "lamp_post", "stirling_engine",
            "heater", "heat_pipe", "heat_pump",
            "basic_solar_heating_plate", "advanced_solar_heating_plate",
            "reactor_rod", "reactor_heat_vent", "reactor_fuel_acceptor");

    private static final Map<String, String> SOURCE_TYPES = Map.ofEntries(
            Map.entry("electrical_connector", "Electrical Connector"),
            Map.entry("carbon_brushes", "Carbon Brushes"),
            Map.entry("generator_coil", "Generator Coil"),
            Map.entry("basic_motor", "Basic Motor"),
            Map.entry("advanced_motor", "Advanced Motor"),
            Map.entry("reinforced_motor", "Reinforced Motor"),
            Map.entry("basic_motor_extension", "Basic Motor Extension"),
            Map.entry("advanced_motor_extension", "Advanced Motor Extension"),
            Map.entry("basic_energiser", "Basic Energiser"),
            Map.entry("advanced_energiser", "Advanced Energiser"),
            Map.entry("reinforced_energiser", "Reinforced Energiser"),
            Map.entry("street_light", "Street Light"),
            Map.entry("lamp_post", "Lamp Post"),
            Map.entry("stirling_engine", "Stirling Engine"),
            Map.entry("heater", "Heater"),
            Map.entry("heat_pipe", "Heat Pipe"),
            Map.entry("heat_pump", "Heat Pump"),
            Map.entry("basic_solar_heating_plate", "Solar Heating Plate"),
            Map.entry("advanced_solar_heating_plate", "Advanced Solar Heating Plate"),
            Map.entry("reactor_rod", "Reactor Rod"),
            Map.entry("reactor_heat_vent", "Reactor Heat Vent"),
            Map.entry("reactor_fuel_acceptor", "Reactor Fuel Acceptor"));

    private static final Map<String, Boolean> ANCESTRY = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

    private static final Method NO_METHOD;
    private static final Field NO_FIELD;

    static {
        Method method = null;
        Field field = null;
        try {
            method = NewAgeCompatHelper.class.getDeclaredMethod("noMember");
            field = NewAgeCompatHelper.class.getDeclaredField("NO_MEMBER_MARKER");
        } catch (ReflectiveOperationException ignored) {
        }
        NO_METHOD = method;
        NO_FIELD = field;
    }

    @SuppressWarnings("unused")
    private static final int NO_MEMBER_MARKER = 0;

    private NewAgeCompatHelper() {
    }

    @SuppressWarnings("unused")
    private static void noMember() {
    }

    public static boolean isNewAgeBlockEntity(@Nullable BlockEntity be) {
        return be != null && be.getClass().getName().startsWith("org.antarcticgardens.cna.");
    }

    public static boolean isElectricalConnector(@Nullable BlockEntity be) {
        return isKind(be, CONNECTOR_BASE);
    }

    public static boolean isNetworkConduit(@Nullable BlockEntity be) {
        if (be == null) {
            return false;
        }
        return ELECTRICAL_CONNECTOR.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()))
                || CONNECTOR_BE.equals(be.getClass().getName());
    }

    // zero stored, MAX_VALUE capacity, takes but never gives. never count it as a reserve
    public static boolean isNetworkMouthHandler(@Nullable IEnergyStorage handler) {
        return handler != null
                && handler.getMaxEnergyStored() == Integer.MAX_VALUE
                && handler.getEnergyStored() == 0
                && handler.canReceive();
    }

    public static boolean isPullMode(@Nullable BlockState state) {
        if (state == null) {
            return false;
        }
        for (net.minecraft.world.level.block.state.properties.Property<?> property
                : state.getProperties()) {
            if ("mode".equals(property.getName())) {
                return "pull".equalsIgnoreCase(String.valueOf(state.getValue(property)));
            }
        }
        return false;
    }

    @Nullable
    public static BlockPos supportingPosOf(@Nullable BlockEntity connector) {
        final Object pos = invoke(connector, "getSupportingBlockPos");
        return pos instanceof BlockPos blockPos ? blockPos : null;
    }

    @Nullable
    public static Direction facingOf(@Nullable BlockEntity connector) {
        final Object facing = invoke(connector, "getFacing");
        return facing instanceof Direction direction ? direction : null;
    }

    public static int conductivityOf(@Nullable BlockEntity connector) {
        final Object wires = invoke(connector, "getConnectorPositions");
        if (!(wires instanceof Map<?, ?> map) || map.isEmpty()) {
            return 0;
        }
        long best = 0L;
        for (Object wire : map.values()) {
            final Object rate = invoke(wire, "getConductivity");
            if (rate instanceof Number number) {
                best = Math.max(best, number.longValue());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, best));
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null || !isNewAgeBlockEntity(sourceBE)) {
            return "";
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(sourceBE.getBlockState().getBlock());
        if (id != null && NewAgeCompat.MOD_ID.equals(id.getNamespace())) {
            final String named = SOURCE_TYPES.get(id.getPath());
            if (named != null) {
                return named;
            }
        }
        return sourceBE.getBlockState().getBlock().getName().getString();
    }

    public static boolean supportsStatsLinkBlock(@Nullable Block block) {
        if (!NewAgeCompat.isAvailable() || block == null) {
            return false;
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && NewAgeCompat.MOD_ID.equals(id.getNamespace())
                && STATS_LINK_BLOCK_PATHS.contains(id.getPath());
    }

    public static boolean supportsCompactAutoRead(@Nullable BlockEntity sourceBE) {
        if (!NewAgeCompat.isAvailable() || !isNewAgeBlockEntity(sourceBE)) {
            return false;
        }
        return isElectricalConnector(sourceBE)
                || isKind(sourceBE, HEAT_INTERFACE)
                || energyOf(sourceBE, null) != null
                || sourceBE instanceof KineticBlockEntity;
    }

    public static boolean supportsAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsCompactAutoRead(sourceBE);
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(@Nullable BlockEntity sourceBE,
                                                                  @Nullable Direction accessFace,
                                                                  Collection<ItemStack> trackedItems) {
        if (!NewAgeCompat.isAvailable() || !isNewAgeBlockEntity(sourceBE)) {
            return null;
        }
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final IEnergyStorage energy = energyOf(sourceBE, accessFace);

        if (energy != null && !isNetworkConduit(sourceBE)) {
            putEnergyMetrics(metrics, energy);
        }
        putKinetic(metrics, sourceBE);
        putHeat(metrics, sourceBE);
        putMachine(metrics, sourceBE);
        putNetwork(metrics, sourceBE);
        if (metrics.isEmpty()) {
            return null;
        }
        return new InstrumentSourceHelper.SourceReadout(getSourceType(sourceBE),
                kindOf(sourceBE, metrics), metrics);
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos,
                                       BlockEntity sourceBE) {
        final InstrumentSourceHelper.SourceReadout readout = readSource(sourceBE, null, List.of());
        if (readout == null || readout.metrics().isEmpty()) {
            return false;
        }
        emitReadout(receiver, sourcePos, readout);
        return true;
    }

    public static void emitReadout(IAutoDataReceiver receiver, BlockPos sourcePos,
                                   InstrumentSourceHelper.SourceReadout readout) {
        receiver.setSourceType(readout.sourceType());
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            receiver.onAutoData(sourcePos, readout.sourceType(), sample.metricId(), sample.label(),
                    sample.value(), sample.type());
        }
    }

    private static InstrumentSourceHelper.SourceKind kindOf(BlockEntity sourceBE,
                                                            Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        if (isElectricalConnector(sourceBE)) {
            return InstrumentSourceHelper.SourceKind.POWER_GRID;
        }
        if (metrics.containsKey("energy_stored") || metrics.containsKey("energy_output_rate")) {
            return InstrumentSourceHelper.SourceKind.ENERGY_STORAGE;
        }
        if (metrics.containsKey("heat")) {
            return InstrumentSourceHelper.SourceKind.BOILER;
        }
        if (metrics.containsKey("stress_su")) {
            return InstrumentSourceHelper.SourceKind.KINETIC_STRESS;
        }
        return metrics.containsKey("rpm")
                ? InstrumentSourceHelper.SourceKind.KINETIC_SPEED
                : InstrumentSourceHelper.SourceKind.UNKNOWN;
    }

    private static void putKinetic(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                   BlockEntity sourceBE) {
        if (!(sourceBE instanceof KineticBlockEntity kinetic)) {
            return;
        }
        final float rpm = Math.abs(kinetic.getSpeed());
        putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
        putSample(metrics, "running", "Running", rpm > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    private static void putHeat(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                BlockEntity sourceBE) {
        if (!isKind(sourceBE, HEAT_INTERFACE)) {
            return;
        }
        final float heat = floatOf(invoke(sourceBE, "getHeat"));
        final float max = floatOf(invoke(sourceBE, "maxHeat"));
        putSample(metrics, "heat", "Temperature", heat, MetricType.GENERIC);
        if (max > 0.0f) {
            putSample(metrics, "heat_max", "Overheat At", max, MetricType.GENERIC);
            putSample(metrics, "heat_pct", "Heat %",
                    Math.min(100.0f, heat * 100.0f / max), MetricType.FILL_LEVEL);
        }
    }

    private static void putMachine(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                   BlockEntity sourceBE) {
        if (isKind(sourceBE, CARBON_BRUSHES_BE)) {
            putSample(metrics, "energy_output_rate", "Generated FE/t",
                    intField(sourceBE, "lastOutput"), MetricType.ENERGY_FE_PER_TICK);
            putStorage(metrics, invoke(sourceBE, "getEnergyStorage"));
            return;
        }
        if (isKind(sourceBE, GENERATOR_COIL_BE)) {
            putSample(metrics, "energy_output_rate", "Generated FE/t",
                    intField(sourceBE, "generatedEnergy"), MetricType.ENERGY_FE_PER_TICK);
            final float total = floatField(sourceBE, "lastStressApplied");
            final float plain = floatField(sourceBE, "plainStress");
            putSample(metrics, "stress_su", "Stress (SU)", total, MetricType.STRESS_SU);
            if (total > 0.0f) {
                putSample(metrics, "efficiency_pct", "Magnet Coverage",
                        Math.max(0.0f, Math.min(100.0f, (total - plain) * 100.0f / total)),
                        MetricType.FILL_LEVEL);
            }
            return;
        }
        if (isKind(sourceBE, MOTOR_BE)) {
            putSample(metrics, "energy_input_rate", "Consumed FE/t",
                    longField(sourceBE, "e"), MetricType.ENERGY_FE_PER_TICK);
            putSample(metrics, "rpm", "RPM",
                    Math.abs(floatField(sourceBE, "actualSpeed")), MetricType.RPM);
            putSample(metrics, "stress_su", "Generated Stress (SU)",
                    floatField(sourceBE, "actualStress"), MetricType.STRESS_SU);
            putSample(metrics, "tier", "Tier", intField(sourceBE, "tier"), MetricType.ITEM_COUNT);
            return;
        }
        if (isKind(sourceBE, MOTOR_EXTENSION_BE)) {
            putSample(metrics, "multiplier", "Power Multiplier",
                    floatOf(invoke(sourceBE, "getMultiplier")), MetricType.GENERIC);
            return;
        }
        if (isKind(sourceBE, ENERGISER_BE)) {
            putStorage(metrics, invoke(sourceBE, "getEnergyStorage"));
            final long charged = longField(sourceBE, "lastCharged");
            if (charged > 0L) {
                putSample(metrics, "energy_output_rate", "Energising FE/t", charged,
                        MetricType.ENERGY_FE_PER_TICK);
            }
            putSample(metrics, "tier", "Tier", intField(sourceBE, "tier"), MetricType.ITEM_COUNT);
            return;
        }
        if (isKind(sourceBE, STREET_LIGHT_BE)) {
            final int level = scrollValue(sourceBE, "lightLevelBehaviour");
            putSample(metrics, "light_level", "Light Level", level, MetricType.ITEM_COUNT);
            putSample(metrics, "energy_input_rate", "Consumed FE/t",
                    streetLightDrawPerTick(level), MetricType.ENERGY_FE_PER_TICK);
            return;
        }
        if (isKind(sourceBE, STIRLING_BE)) {
            putSample(metrics, "rpm", "Generated RPM",
                    Math.abs(floatField(sourceBE, "speed")), MetricType.RPM);
            return;
        }
        if (isKind(sourceBE, REACTOR_ROD_BE)) {
            putSample(metrics, "fuel", "Fuel", intField(sourceBE, "fuel"), MetricType.ITEM_COUNT);
            putSample(metrics, "running", "Reacting",
                    booleanField(sourceBE, "working") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
            return;
        }
        if (isKind(sourceBE, REACTOR_VENT_BE)) {
            putSample(metrics, "heat_extract", "Venting /t",
                    floatField(sourceBE, "extract"), MetricType.GENERIC);
            return;
        }
        if (isKind(sourceBE, HEAT_PIPE_BE)) {
            putSample(metrics, "heat_generating", "Absorbing /s",
                    floatField(sourceBE, "generating"), MetricType.GENERIC);
            return;
        }
        if (isKind(sourceBE, HEAT_PUMP_BE)) {
            putSample(metrics, "heat_pumped", "Pumped /s",
                    floatField(sourceBE, "lastPump"), MetricType.GENERIC);
            return;
        }
        if (isKind(sourceBE, SOLAR_PLATE_BE)) {
            putSample(metrics, "heat_generating", "Collecting /s",
                    floatField(sourceBE, "last"), MetricType.GENERIC);
            return;
        }
        if (isKind(sourceBE, HEATER_BE)) {
            putSample(metrics, "running", "Heating",
                    floatOf(invoke(sourceBE, "getHeat")) > 0.0f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        }
    }

    private static void putNetwork(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                   BlockEntity sourceBE) {
        if (!isElectricalConnector(sourceBE)) {
            return;
        }
        putSample(metrics, "conductivity", "Wire Rating", conductivityOf(sourceBE),
                MetricType.ENERGY_FE_PER_TICK);
        final NetworkView view = surveyNetwork(sourceBE);
        if (view == null) {
            return;
        }
        putSample(metrics, "wire_nodes", "Connectors", view.nodeCount(), MetricType.ITEM_COUNT);
        putSample(metrics, "network_sources", "Generators", view.sourceCount(), MetricType.ITEM_COUNT);
        putSample(metrics, "network_consumers", "Consumers", view.consumerCount(), MetricType.ITEM_COUNT);
        putSample(metrics, "energy_output_rate", "Network Generation", view.generation(),
                MetricType.ENERGY_FE_PER_TICK);
        putSample(metrics, "network_demand", "Network Demand", view.demand(),
                MetricType.ENERGY_FE_PER_TICK);
        putSample(metrics, "network_buffer", "Network Buffer", view.stored(), MetricType.ENERGY_FE);
        if (view.capacity() > 0) {
            putSample(metrics, "energy_fill_pct", "Buffer %",
                    Math.min(100.0f, view.stored() * 100.0f / view.capacity()), MetricType.FILL_LEVEL);
        }
    }

    private static void putStorage(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                   @Nullable Object storage) {
        if (storage == null) {
            return;
        }
        final long stored = longOf(invoke(storage, "getStoredEnergy"));
        final long capacity = longOf(invoke(storage, "getCapacity"));
        if (capacity <= 0L) {
            return;
        }
        putSample(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
        putSample(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
        putSample(metrics, "energy_fill_pct", "Energy %",
                Math.min(100.0f, stored * 100.0f / capacity), MetricType.FILL_LEVEL);
    }

    private static void putEnergyMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                         IEnergyStorage energy) {
        final int stored = Math.max(0, energy.getEnergyStored());
        final int capacity = Math.max(0, energy.getMaxEnergyStored());
        putSample(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
        putSample(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
        putSample(metrics, "energy_fill_pct", "Energy %",
                capacity > 0 ? stored * 100.0f / capacity : 0.0f, MetricType.FILL_LEVEL);
        putSample(metrics, "energy_can_receive", "Can Receive",
                energy.canReceive() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "energy_can_extract", "Can Extract",
                energy.canExtract() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    public static boolean isNewAgeGenerator(@Nullable BlockEntity sourceBE) {
        return NewAgeCompat.isAvailable()
                && (isKind(sourceBE, CARBON_BRUSHES_BE) || isKind(sourceBE, GENERATOR_COIL_BE));
    }

    public static int generationPotentialFePerTick(@Nullable BlockEntity sourceBE) {
        if (!NewAgeCompat.isAvailable() || !isNewAgeBlockEntity(sourceBE)) {
            return 0;
        }
        if (isKind(sourceBE, CARBON_BRUSHES_BE)) {
            return Math.max(0, intField(sourceBE, "lastOutput"));
        }
        if (isKind(sourceBE, GENERATOR_COIL_BE)) {
            return Math.max(0, intField(sourceBE, "generatedEnergy"));
        }
        return 0;
    }

    public static int consumptionFePerTick(@Nullable BlockEntity sourceBE) {
        if (!NewAgeCompat.isAvailable() || !isNewAgeBlockEntity(sourceBE)) {
            return -1;
        }
        if (isKind(sourceBE, MOTOR_BE)) {
            return (int) Math.max(0L, longField(sourceBE, "e"));
        }
        if (isKind(sourceBE, STREET_LIGHT_BE)) {
            return streetLightDrawPerTick(scrollValue(sourceBE, "lightLevelBehaviour"));
        }
        return -1;
    }

    public record Source(BlockPos pos, int fePerTick) {
    }

    public record Tank(BlockPos pos, int stored, int capacity, boolean spendable) {
    }

    public record NetworkView(int networkId, int nodeCount, int sourceCount, int consumerCount,
                              int generation, int demand, int stored, int capacity,
                              int conductivity, List<Source> sources, List<Tank> tanks,
                              List<BlockPos> supportedPositions, int pulledGeneration,
                              List<Source> pulledSources, List<Source> consumers) {
        public NetworkView {
            sources = List.copyOf(sources);
            tanks = List.copyOf(tanks);
            supportedPositions = List.copyOf(supportedPositions);
            pulledSources = List.copyOf(pulledSources);
            consumers = List.copyOf(consumers);
        }

        public int demandExcluding(@Nullable BlockPos exclude) {
            long total = 0L;
            for (Source c : this.consumers) {
                if (exclude == null || !exclude.equals(c.pos())) {
                    total += Math.max(0, c.fePerTick());
                }
            }
            return (int) Math.min(Integer.MAX_VALUE, total);
        }
    }

    private static final int MAX_NODES = 512;

    @Nullable
    public static NetworkView surveyNetwork(@Nullable BlockEntity connector) {
        if (!NewAgeCompat.isAvailable() || connector == null || connector.getLevel() == null
                || connector.getLevel().isClientSide() || !isElectricalConnector(connector)) {
            return null;
        }

        final long tick = connector.getLevel().getGameTime();
        final CachedSurvey cached = SURVEYS.get(connector);
        if (cached != null && cached.tick == tick) {
            return cached.view;
        }
        final NetworkView fresh = walkNetwork(connector);
        SURVEYS.put(connector, new CachedSurvey(tick, fresh));
        return fresh;
    }

    @Nullable
    private static NetworkView walkNetwork(BlockEntity connector) {
        final Object network = invoke(connector, "getNetwork");
        if (network == null) {
            return null;
        }
        final Object nodes = invoke(network, "getNodes");
        if (!(nodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            return null;
        }
        final Level level = connector.getLevel();
        final int probe = probeSize(conductivityOf(connector));
        final List<Source> sources = new ArrayList<>(2);
        final List<Source> pulled = new ArrayList<>(2);
        final List<Source> consumerDemands = new ArrayList<>(2);
        final List<Tank> tanks = new ArrayList<>(2);
        final Set<BlockPos> seenSupports = new java.util.HashSet<>();
        int generation = 0;
        int pulledGeneration = 0;
        int demand = 0;
        int stored = 0;
        int capacity = 0;
        int consumers = 0;
        int conductivity = 0;
        int walked = 0;

        for (Object node : nodeList) {
            if (walked >= MAX_NODES) {
                break;
            }
            walked++;
            if (!(node instanceof BlockEntity nodeBE) || nodeBE.isRemoved()) {
                continue;
            }
            conductivity = Math.max(conductivity, conductivityOf(nodeBE));
            final BlockPos support = supportingPosOf(nodeBE);

            if (support == null || !level.isLoaded(support) || !seenSupports.add(support)) {
                continue;
            }
            final BlockEntity machine = level.getBlockEntity(support);
            if (machine == null) {
                continue;
            }

            if (isNewAgeGenerator(machine)) {
                final int made = generationPotentialFePerTick(machine);
                generation += made;
                sources.add(new Source(support, made));

                if (isPullMode(nodeBE.getBlockState())) {
                    pulledGeneration += made;
                    pulled.add(new Source(support, made));
                }
                continue;
            }

            if (isKind(machine, ENERGISER_BE)) {
                final Object storage = invoke(machine, "getEnergyStorage");
                final int held = (int) Math.min(Integer.MAX_VALUE, longOf(invoke(storage, "getStoredEnergy")));
                final int room = (int) Math.min(Integer.MAX_VALUE, longOf(invoke(storage, "getCapacity")));
                if (room > 0) {
                    stored += held;
                    capacity += room;
                    tanks.add(new Tank(support, held, room, isPullMode(nodeBE.getBlockState())));
                    continue;
                }
            }

            final IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    support, facingOf(nodeBE));

            if (handler == null || !handler.canReceive() || isNetworkMouthHandler(handler)) {
                continue;
            }
            consumers++;
            final int wants = Math.max(0, handler.receiveEnergy(probe, true));
            demand += wants;
            consumerDemands.add(new Source(support, wants));
        }
        return new NetworkView(System.identityHashCode(network), Math.min(walked, nodeList.size()),
                sources.size(), consumers, generation, demand, stored, capacity, conductivity,
                sources, tanks, new ArrayList<>(seenSupports), pulledGeneration, pulled, consumerDemands);
    }

    public static int acceptanceFePerTick(@Nullable BlockEntity connector, @Nullable BlockPos exclude) {
        if (!NewAgeCompat.isAvailable() || connector == null || connector.getLevel() == null
                || connector.getLevel().isClientSide() || !isElectricalConnector(connector)) {
            return 0;
        }

        if (exclude == null) {
            final long tick = connector.getLevel().getGameTime();
            final CachedAcceptance cached = ACCEPTANCE.get(connector);
            if (cached != null && cached.tick == tick) {
                return cached.value;
            }
            final int fresh = walkAcceptance(connector, null);
            ACCEPTANCE.put(connector, new CachedAcceptance(tick, fresh));
            return fresh;
        }
        return walkAcceptance(connector, exclude);
    }

    private static int walkAcceptance(BlockEntity connector, @Nullable BlockPos exclude) {
        final Object network = invoke(connector, "getNetwork");
        final Object nodes = network == null ? null : invoke(network, "getNodes");
        if (!(nodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            return 0;
        }
        final Level level = connector.getLevel();
        final int wire = conductivityOf(connector);
        final int probe = probeSize(wire);
        final Set<BlockPos> seen = new java.util.HashSet<>();
        long room = 0L;
        int walked = 0;
        for (Object node : nodeList) {
            if (walked >= MAX_NODES) {
                break;
            }
            walked++;
            if (!(node instanceof BlockEntity nodeBE) || nodeBE.isRemoved()) {
                continue;
            }
            final BlockPos support = supportingPosOf(nodeBE);

            if (support == null || support.equals(exclude) || !level.isLoaded(support)
                    || !seen.add(support) || level.getBlockEntity(support) == null) {
                continue;
            }
            final IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    support, facingOf(nodeBE));

            if (handler == null || !handler.canReceive() || isNetworkMouthHandler(handler)) {
                continue;
            }
            room += Math.max(0, handler.receiveEnergy(probe, true));
            if (room >= Integer.MAX_VALUE) {
                break;
            }
        }
        final int accepted = (int) Math.min(Integer.MAX_VALUE, room);
        return wire <= 0 ? accepted : Math.min(wire, accepted);
    }

    public static int drainNetwork(@Nullable BlockEntity connector, int maxFe, boolean simulate) {
        if (!NewAgeCompat.isAvailable() || maxFe <= 0 || connector == null
                || connector.getLevel() == null || connector.getLevel().isClientSide()
                || !isElectricalConnector(connector)) {
            return 0;
        }
        final Object network = invoke(connector, "getNetwork");
        final Object nodes = network == null ? null : invoke(network, "getNodes");
        if (!(nodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            return 0;
        }
        final Level level = connector.getLevel();
        final Set<BlockPos> seen = new java.util.HashSet<>();
        int drawn = 0;
        int walked = 0;
        for (Object node : nodeList) {
            if (walked >= MAX_NODES || drawn >= maxFe) {
                break;
            }
            walked++;
            if (!(node instanceof BlockEntity nodeBE) || nodeBE.isRemoved()
                    || !isPullMode(nodeBE.getBlockState())) {
                continue;
            }
            final BlockPos support = supportingPosOf(nodeBE);

            if (support == null || !level.isLoaded(support) || !seen.add(support)
                    || level.getBlockEntity(support) == null) {
                continue;
            }

            if (!com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, support)) {
                continue;
            }
            final IEnergyStorage handler = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    support, facingOf(nodeBE));
            if (handler == null || !handler.canExtract() || isNetworkMouthHandler(handler)) {
                continue;
            }

            final boolean own = nodeBE == connector;
            final Object path = own ? null : PathAccess.find(network, nodeBE, connector);
            final long room;
            if (own) {
                room = maxFe - drawn;
            } else if (path != null) {
                room = Math.min(maxFe - drawn, PathAccess.conductivity(network, path));
            } else if (PathAccess.available()) {
                continue;
            } else {
                room = Math.min(maxFe - drawn, Math.max(0, conductivityOf(connector) - drawn));
            }
            if (room <= 0) {
                continue;
            }
            final int took = Math.max(0, handler.extractEnergy((int) room, simulate));
            drawn += took;
            if (!simulate && took > 0 && path != null) {
                PathAccess.spend(network, path, took);
            }
        }
        return Math.min(maxFe, Math.max(0, drawn));
    }

    private static final class PathAccess {
        private static volatile boolean resolved;
        @Nullable
        private static Method getPathManager;
        @Nullable
        private static Method findPath;
        @Nullable
        private static Method getContext;
        @Nullable
        private static Method conductivity;
        @Nullable
        private static Method decrease;

        private PathAccess() {
        }

        private static synchronized void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                final ClassLoader loader = NewAgeCompatHelper.class.getClassLoader();
                final String pkg = "org.antarcticgardens.cna.content.electricity.network.";
                final Class<?> networkType = Class.forName(pkg + "ElectricalNetwork", false, loader);
                final Class<?> managerType = Class.forName(pkg + "NetworkPathManager", false, loader);
                final Class<?> contextType = Class.forName(pkg + "NetworkPathConductivityContext", false, loader);
                final Class<?> pathType = Class.forName(pkg + "NetworkPath", false, loader);
                final Class<?> connectorType = Class.forName(CONNECTOR_BASE, false, loader);
                final Method pm = networkType.getDeclaredMethod("getPathManager");
                final Method fp = managerType.getDeclaredMethod("findConductiblePath", connectorType, connectorType);
                final Method gc = managerType.getDeclaredMethod("getConductivityContext");
                final Method cc = contextType.getDeclaredMethod("calculatePathConductivity", pathType);
                final Method dc = contextType.getDeclaredMethod("decreasePathConductivity", pathType, long.class);
                for (Method m : new Method[] {pm, fp, gc, cc, dc}) {
                    m.setAccessible(true);
                }
                findPath = fp;
                getContext = gc;
                conductivity = cc;
                decrease = dc;
                getPathManager = pm;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError missing) {
                getPathManager = null;
            }
        }

        static boolean available() {
            resolve();
            return getPathManager != null;
        }

        @Nullable
        static Object find(Object network, Object from, Object to) {
            if (!available()) {
                return null;
            }
            try {
                final Object manager = getPathManager.invoke(network);
                return manager == null ? null : findPath.invoke(manager, from, to);
            } catch (ReflectiveOperationException | RuntimeException failed) {
                return null;
            }
        }

        static long conductivity(Object network, Object path) {
            try {
                final Object context = getContext.invoke(getPathManager.invoke(network));
                final Object left = conductivity.invoke(context, path);
                return left instanceof Number n ? Math.max(0L, Math.min(Integer.MAX_VALUE, n.longValue())) : 0L;
            } catch (ReflectiveOperationException | RuntimeException failed) {
                return 0L;
            }
        }

        static void spend(Object network, Object path, long fe) {
            try {
                decrease.invoke(getContext.invoke(getPathManager.invoke(network)), path, fe);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    public static long bestWireConductivity() {
        if (!NewAgeCompat.isAvailable()) {
            return 0L;
        }
        try {
            final Class<?> wireType = Class.forName("org.antarcticgardens.cna.content.electricity.wire.WireType",
                    false, NewAgeCompatHelper.class.getClassLoader());
            long best = 0L;
            for (Object constant : wireType.getEnumConstants()) {
                final Object rate = invoke(constant, "getConductivity");
                if (rate instanceof Number n) {
                    best = Math.max(best, n.longValue());
                }
            }
            return Math.max(0L, best);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError missing) {
            return 0L;
        }
    }

    public static int pullSourceCount(@Nullable BlockEntity connector) {
        if (!NewAgeCompat.isAvailable() || connector == null || connector.getLevel() == null
                || connector.getLevel().isClientSide() || !isElectricalConnector(connector)) {
            return 0;
        }
        final Object network = invoke(connector, "getNetwork");
        final Object nodes = network == null ? null : invoke(network, "getNodes");
        if (!(nodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            return 0;
        }
        final Level level = connector.getLevel();
        final Set<BlockPos> seen = new java.util.HashSet<>();
        int found = 0;
        int walked = 0;
        for (Object node : nodeList) {
            if (walked >= MAX_NODES) {
                break;
            }
            walked++;
            if (!(node instanceof BlockEntity nodeBE) || nodeBE.isRemoved()
                    || !isPullMode(nodeBE.getBlockState())) {
                continue;
            }
            final BlockPos support = supportingPosOf(nodeBE);
            if (support == null || !level.isLoaded(support) || !seen.add(support)
                    || level.getBlockEntity(support) == null
                    || !com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, support)) {
                continue;
            }
            found++;
        }
        return found;
    }

    private static int streetLightDrawPerTick(int lightLevel) {
        return Math.max(0, lightLevel) * serverConfigInt("streetLightLevelExtraction", 1);
    }

    private static int serverConfigInt(String field, int fallback) {
        try {
            final Class<?> config = Class.forName("org.antarcticgardens.cna.config.CNAConfig");
            final Object server = config.getMethod("getServer").invoke(null);
            final Object spec = readField(server, field);
            final Object value = invoke(spec, "get");
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static int probeSize(int wireRating) {
        return wireRating > 0 ? wireRating : 1_000_000;
    }

    private record CachedSurvey(long tick, @Nullable NetworkView view) {
    }

    private record CachedAcceptance(long tick, int value) {
    }

    private static final Map<BlockEntity, CachedSurvey> SURVEYS = new java.util.WeakHashMap<>();
    private static final Map<BlockEntity, CachedAcceptance> ACCEPTANCE = new java.util.WeakHashMap<>();

    @Nullable
    private static IEnergyStorage energyOf(@Nullable BlockEntity be, @Nullable Direction face) {
        if (be == null || be.getLevel() == null) {
            return null;
        }
        final IEnergyStorage sided = be.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                be.getBlockPos(), face);
        return sided != null ? sided
                : be.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), null);
    }

    private static boolean isKind(@Nullable BlockEntity be, String className) {
        return be != null
                && ANCESTRY.computeIfAbsent(be.getClass().getName() + "|" + className,
                        ignored -> hasAncestor(be.getClass(), className));
    }

    private static boolean hasAncestor(Class<?> type, String className) {
        for (Class<?> step = type; step != null && step != Object.class; step = step.getSuperclass()) {
            if (step.getName().equals(className)) {
                return true;
            }
            for (Class<?> face : step.getInterfaces()) {
                if (face.getName().equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void putSample(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                  String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId, label, value, type));
    }

    private static int scrollValue(@Nullable Object target, String fieldName) {
        final Object behaviour = readField(target, fieldName);
        final Object value = invoke(behaviour, "getValue");
        return value instanceof Number number ? number.intValue() : 0;
    }

    @Nullable
    private static Object invoke(@Nullable Object target, String name) {
        if (target == null) {
            return null;
        }
        final Method method = METHODS.computeIfAbsent(target.getClass().getName() + "#" + name,
                ignored -> {
                    try {
                        final Method found = target.getClass().getMethod(name);
                        found.setAccessible(true);
                        return found;
                    } catch (ReflectiveOperationException | RuntimeException failed) {
                        return NO_METHOD;
                    }
                });
        if (method == NO_METHOD) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object readField(@Nullable Object target, String name) {
        if (target == null) {
            return null;
        }
        final Field field = FIELDS.computeIfAbsent(target.getClass().getName() + "#" + name,
                ignored -> findField(target.getClass(), name));
        if (field == NO_FIELD) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> step = type; step != null && step != Object.class; step = step.getSuperclass()) {
            try {
                final Field found = step.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return NO_FIELD;
    }

    private static int intField(Object target, String name) {
        final Object value = readField(target, name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longField(Object target, String name) {
        final Object value = readField(target, name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static float floatField(Object target, String name) {
        final Object value = readField(target, name);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static boolean booleanField(Object target, String name) {
        return readField(target, name) instanceof Boolean flag && flag;
    }

    private static float floatOf(@Nullable Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static long longOf(@Nullable Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
