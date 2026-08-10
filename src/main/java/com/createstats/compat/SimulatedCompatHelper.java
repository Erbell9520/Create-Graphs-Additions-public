package com.createstats.compat;

import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.display.VehicleForwardAuthority;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SimulatedCompatHelper {
    private static final String CONTRAPTION_INFO_SOURCE = "Contraption Info";
    private static final Set<String> STATS_LINK_BLOCK_PATHS = Set.of(
            "adjustable_burner",
            "altitude_sensor",
            "auger_shaft",
            "docking_connector",
            "paired_docking_connector",
            "gimbal_sensor",
            "laser_sensor",
            "linked_typewriter",
            "navigation_table",
            "optical_sensor",
            "steering_wheel",
            "steam_vent",
            "velocity_sensor"
    );
    private static final Set<String> RESPONSIVE_SOURCE_TYPES = Set.of(
            "Portable Engine",
            "Velocity Sensor",
            "Gimbal Sensor",
            "Altitude Sensor",
            "Docking Connector",
            "Steering Wheel",
            "Laser Sensor",
            "Optical Sensor",
            "Navigation Table",
            "Linked Typewriter",
            "Hot Air Burner",
            "Steam Vent",
            CONTRAPTION_INFO_SOURCE
    );
    private static final Map<BlockEntity, CachedReadout> READOUT_CACHE = new WeakHashMap<>();
    private static final Map<BlockEntity, CachedForwardAxis> FORWARD_AXIS_CACHE = new WeakHashMap<>();
    private static final double BODY_FORWARD_MIN_HORIZONTAL_SQ = 0.0225d;
    private static final double MOTION_FORWARD_MIN_HORIZONTAL_SQ = 1.0E-4d;

    private static final String PORTABLE_ENGINE_BE = "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity";
    private static final String VELOCITY_SENSOR_BE = "dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity";
    private static final String GIMBAL_SENSOR_BE = "dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity";
    private static final String ALTITUDE_SENSOR_BE = "dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity";
    private static final String DOCKING_CONNECTOR_BE = "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity";
    private static final String STEERING_WHEEL_BE = "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity";
    private static final String LASER_SENSOR_BE = "dev.simulated_team.simulated.content.blocks.lasers.laser_sensor.LaserSensorBlockEntity";
    private static final String OPTICAL_SENSOR_BE = "dev.simulated_team.simulated.content.blocks.lasers.optical_sensor.OpticalSensorBlockEntity";
    private static final String NAV_TABLE_BE = "dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity";
    private static final String LINKED_TYPEWRITER_BE = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity";
    private static final String AUGER_SHAFT_BE = "dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlockEntity";
    private static final String HOT_AIR_BURNER_BE = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity";
    private static final String STEAM_VENT_BE = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity";
    private SimulatedCompatHelper() {
    }

    public static boolean supportsCompactAutoRead(@Nullable BlockEntity sourceBE) {
        return matches(sourceBE, PORTABLE_ENGINE_BE)
                || matches(sourceBE, VELOCITY_SENSOR_BE)
                || matches(sourceBE, GIMBAL_SENSOR_BE)
                || matches(sourceBE, ALTITUDE_SENSOR_BE)
                || matches(sourceBE, DOCKING_CONNECTOR_BE)
                || matches(sourceBE, STEERING_WHEEL_BE)
                || matches(sourceBE, LASER_SENSOR_BE)
                || matches(sourceBE, OPTICAL_SENSOR_BE)
                || matches(sourceBE, NAV_TABLE_BE)
                || matches(sourceBE, HOT_AIR_BURNER_BE)
                || matches(sourceBE, STEAM_VENT_BE);
    }

    public static boolean supportsAutoRead(@Nullable BlockEntity sourceBE) {
        return supportsCompactAutoRead(sourceBE)
                || matches(sourceBE, LINKED_TYPEWRITER_BE)
                || matches(sourceBE, AUGER_SHAFT_BE);
    }

    public static boolean supportsStatsLinkBlock(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        final var key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null && (SimulatedCompat.SIMULATED_MOD_ID.equals(key.getNamespace())
                || SimulatedCompat.AERONAUTICS_MOD_ID.equals(key.getNamespace()))) {
            final String path = key.getPath();
            return path.endsWith("_portable_engine") || STATS_LINK_BLOCK_PATHS.contains(path);
        }
        final String className = block.getClass().getName();
        return (className.startsWith("dev.simulated_team.simulated.content.blocks.")
                || className.startsWith("dev.eriksonn.aeronautics.content.blocks."))
                && (className.endsWith("PortableEngineBlock")
                || className.endsWith("VelocitySensorBlock")
                || className.endsWith("GimbalSensorBlock")
                || className.endsWith("AltitudeSensorBlock")
                || className.endsWith("DockingConnectorBlock")
                || className.endsWith("PairedDockingConnectorBlock")
                || className.endsWith("SteeringWheelBlock")
                || className.endsWith("LaserSensorBlock")
                || className.endsWith("OpticalSensorBlock")
                || className.endsWith("NavTableBlock")
                || className.endsWith("LinkedTypewriterBlock")
                || className.endsWith("AugerShaftBlock")
                || className.endsWith("HotAirBurnerBlock")
                || className.endsWith("SteamVentBlock"));
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        if (matches(sourceBE, PORTABLE_ENGINE_BE)) {
            return "Portable Engine";
        }
        if (matches(sourceBE, VELOCITY_SENSOR_BE)) {
            return "Velocity Sensor";
        }
        if (matches(sourceBE, GIMBAL_SENSOR_BE)) {
            return "Gimbal Sensor";
        }
        if (matches(sourceBE, ALTITUDE_SENSOR_BE)) {
            return "Altitude Sensor";
        }
        if (matches(sourceBE, DOCKING_CONNECTOR_BE)) {
            return "Docking Connector";
        }
        if (matches(sourceBE, STEERING_WHEEL_BE)) {
            return "Steering Wheel";
        }
        if (matches(sourceBE, LASER_SENSOR_BE)) {
            return "Laser Sensor";
        }
        if (matches(sourceBE, OPTICAL_SENSOR_BE)) {
            return "Optical Sensor";
        }
        if (matches(sourceBE, NAV_TABLE_BE)) {
            return "Navigation Table";
        }
        if (matches(sourceBE, LINKED_TYPEWRITER_BE)) {
            return "Linked Typewriter";
        }
        if (matches(sourceBE, AUGER_SHAFT_BE)) {
            return "Auger Throughput";
        }
        if (matches(sourceBE, HOT_AIR_BURNER_BE)) {
            return "Hot Air Burner";
        }
        if (matches(sourceBE, STEAM_VENT_BE)) {
            return "Steam Vent";
        }
        return "";
    }

    public static boolean isResponsiveSourceType(String sourceType) {
        return RESPONSIVE_SOURCE_TYPES.contains(sourceType);
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(BlockEntity sourceBE, @Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        if (canUseReadoutCache(accessFace, trackedItems)) {
            final InstrumentSourceHelper.SourceReadout cached = getCachedReadout(sourceBE);
            if (cached != null) {
                return cached;
            }
        }
        final InstrumentSourceHelper.SourceReadout readout = readSourceUncached(sourceBE, accessFace, trackedItems);
        if (readout != null && canUseReadoutCache(accessFace, trackedItems)) {
            cacheReadout(sourceBE, readout);
        }
        return readout;
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readSourceUncached(BlockEntity sourceBE, @Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        if (matches(sourceBE, PORTABLE_ENGINE_BE)) {
            return readPortableEngine(sourceBE);
        }
        if (matches(sourceBE, VELOCITY_SENSOR_BE)) {
            return readVelocitySensor(sourceBE);
        }
        if (matches(sourceBE, GIMBAL_SENSOR_BE)) {
            return readGimbalSensor(sourceBE);
        }
        if (matches(sourceBE, ALTITUDE_SENSOR_BE)) {
            return readAltitudeSensor(sourceBE);
        }
        if (matches(sourceBE, DOCKING_CONNECTOR_BE)) {
            return readDockingConnector(sourceBE);
        }
        if (matches(sourceBE, STEERING_WHEEL_BE)) {
            return readSteeringWheel(sourceBE);
        }
        if (matches(sourceBE, LASER_SENSOR_BE)) {
            return readLaserSensor(sourceBE);
        }
        if (matches(sourceBE, OPTICAL_SENSOR_BE)) {
            return readOpticalSensor(sourceBE);
        }
        if (matches(sourceBE, NAV_TABLE_BE)) {
            return readNavigationTable(sourceBE);
        }
        if (matches(sourceBE, LINKED_TYPEWRITER_BE)) {
            return readLinkedTypewriter(sourceBE);
        }
        if (matches(sourceBE, AUGER_SHAFT_BE)) {
            return readAugerShaft(sourceBE);
        }
        if (matches(sourceBE, HOT_AIR_BURNER_BE)) {
            return readHotAirBurner(sourceBE);
        }
        if (matches(sourceBE, STEAM_VENT_BE)) {
            return readSteamVent(sourceBE);
        }
        return null;
    }

    public static boolean isItemThroughputSource(@Nullable BlockEntity sourceBE) {
        return matches(sourceBE, AUGER_SHAFT_BE);
    }

    public static ItemStack resolveItemThroughputDisplayStack(@Nullable BlockEntity sourceBE) {
        if (!matches(sourceBE, AUGER_SHAFT_BE)) {
            return ItemStack.EMPTY;
        }
        final ItemStack movingStack = extractContainedStack(readField(sourceBE, "inventory"));
        if (!movingStack.isEmpty()) {
            return movingStack;
        }
        return extractContainedStack(readField(sourceBE, "actorInventory"));
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos, BlockEntity sourceBE) {
        final StatsDisplaySettings settings = (receiver instanceof StatsDisplayPanelBE panel) ? panel.getSettings() : null;
        final InstrumentSourceHelper.SourceReadout readout = readSourceForDisplay(sourceBE, settings);
        if (readout == null) {
            return false;
        }
        emitReadout(receiver, sourcePos, readout, false, true);
        if (shouldEmitContraptionInfo(receiver, sourceBE)) {
            final InstrumentSourceHelper.SourceReadout contraptionInfo = readContraptionInfo(sourceBE, settings);
            if (contraptionInfo != null) {
                emitReadout(receiver, sourcePos, contraptionInfo, true, false);
            }
        }
        return true;
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readSourceForDisplay(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings) {
        if (matches(sourceBE, VELOCITY_SENSOR_BE)) {
            return readVelocitySensor(sourceBE, settings);
        }
        if (matches(sourceBE, GIMBAL_SENSOR_BE)) {
            return readGimbalSensor(sourceBE, settings);
        }
        return readSource(sourceBE, null, java.util.List.of());
    }

    private static boolean matches(@Nullable BlockEntity sourceBE, String className) {
        return sourceBE != null && sourceBE.getClass().getName().equals(className);
    }

    private static boolean canUseReadoutCache(@Nullable Direction accessFace, Collection<ItemStack> trackedItems) {
        return accessFace == null && trackedItems.isEmpty();
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout getCachedReadout(BlockEntity sourceBE) {
        final long currentTick = getCurrentTick(sourceBE);
        if (currentTick < 0L) {
            return null;
        }
        final CachedReadout cached = READOUT_CACHE.get(sourceBE);
        if (cached == null || cached.tick != currentTick) {
            if (cached != null) {
                READOUT_CACHE.remove(sourceBE);
            }
            return null;
        }
        return cached.readout;
    }

    private static void cacheReadout(BlockEntity sourceBE, InstrumentSourceHelper.SourceReadout readout) {
        final long currentTick = getCurrentTick(sourceBE);
        if (currentTick < 0L) {
            return;
        }
        READOUT_CACHE.put(sourceBE, new CachedReadout(currentTick, readout));
    }

    private static long getCurrentTick(@Nullable BlockEntity sourceBE) {
        return sourceBE != null && sourceBE.getLevel() != null ? sourceBE.getLevel().getGameTime() : -1L;
    }

    private static InstrumentSourceHelper.SourceReadout readPortableEngine(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float currentBurn = invokeFloat(sourceBE, "getCurrentBurnTime");
        final float totalBurn = Math.max(currentBurn, invokeFloat(sourceBE, "getTotalBurnTime"));
        final boolean infiniteFuel = invokeBoolean(sourceBE, "isCurrentFuelInfinite");
        final boolean superHeated = invokeBoolean(sourceBE, "isSuperHeated");
        final float generatedSpeed = invokeFloat(sourceBE, "getGeneratedSpeed");
        final float rpm = Math.abs(generatedSpeed);
        final float burnPercent = infiniteFuel ? 100.0f : (totalBurn > 0.0f ? currentBurn * 100.0f / totalBurn : 0.0f);
        final boolean burning = infiniteFuel || currentBurn > 0.0f;
        putSample(metrics, "burn_time", "Current Burn", currentBurn, MetricType.BURN_TIME);
        putSample(metrics, "burn_total", "Total Burn", totalBurn, MetricType.BURN_TIME);
        putSample(metrics, "burn_pct", "Burn %", burnPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "fuel_infinite", "Infinite Fuel", infiniteFuel ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "superheated", "Superheated", superHeated ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "generated_speed", "Generated Speed", generatedSpeed, MetricType.RPM);
        putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
        putSample(metrics, "running", "Running", burning ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Portable Engine", InstrumentSourceHelper.SourceKind.KINETIC_SPEED, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readVelocitySensor(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        putSample(metrics, "adjusted_velocity", "Adjusted Velocity", invokeFloat(sourceBE, "getAdjustedVelocity"), MetricType.VELOCITY);
        putSample(metrics, "horizontal_velocity", "Horizontal Velocity", resolveHorizontalVelocity(sourceBE), MetricType.VELOCITY);
        putSample(metrics, "redstone_strength", "Redstone Strength", invokeFloat(sourceBE, "getRedstoneStrength"), MetricType.REDSTONE_POWER);
        putSample(metrics, "signed_redstone", "Signed Redstone", readFloatField(sourceBE, "signedRedstoneStrength"), MetricType.REDSTONE_POWER);
        return new InstrumentSourceHelper.SourceReadout("Velocity Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readVelocitySensor(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float adjustedVelocity = invokeFloat(sourceBE, "getAdjustedVelocity");
        putSample(metrics, "adjusted_velocity", "Adjusted Velocity", adjustedVelocity, MetricType.VELOCITY);
        putSample(metrics, "horizontal_velocity", "Horizontal Velocity", resolveHorizontalVelocity(sourceBE), MetricType.VELOCITY);
        putSample(metrics, "redstone_strength", "Redstone Strength", invokeFloat(sourceBE, "getRedstoneStrength"), MetricType.REDSTONE_POWER);
        putSample(metrics, "signed_redstone", "Signed Redstone", readFloatField(sourceBE, "signedRedstoneStrength"), MetricType.REDSTONE_POWER);
        final VehicleForwardAuthority.Resolution heading = resolveVehicleHeading(sourceBE, settings, false, true);
        if (heading.hasForward()) {
            putSample(metrics, "heading_yaw", "Heading Yaw", heading.yawDegrees(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "forward_x", "Forward X", heading.forwardX(), MetricType.GENERIC);
            putSample(metrics, "forward_z", "Forward Z", heading.forwardZ(), MetricType.GENERIC);
        }
        putSample(metrics, "heading_source", "Heading Source: " + heading.sourceLabel(), heading.confidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "heading_confidence", "Heading Confidence", heading.confidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "velocity_fallback", heading.velocityAssistAvailable() || heading.source() == VehicleForwardAuthority.ForwardSource.VELOCITY_AXIS ? "Velocity Assist Ready" : "Velocity Assist Offline",
                heading.velocityAssistAvailable() || heading.source() == VehicleForwardAuthority.ForwardSource.VELOCITY_AXIS ? 100.0f : 0.0f, MetricType.FILL_LEVEL);
        return new InstrumentSourceHelper.SourceReadout("Velocity Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readGimbalSensor(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float xAngle = invokeFloat(sourceBE, "getXAngle");
        final float zAngle = invokeFloat(sourceBE, "getZAngle");
        putSample(metrics, "x_angle", "X Angle", xAngle, MetricType.ANGLE_DEGREES);
        putSample(metrics, "z_angle", "Z Angle", zAngle, MetricType.ANGLE_DEGREES);
        final AttitudeSample attitude = resolvePoseAttitude(sourceBE, xAngle, zAngle);
        if (attitude != null) {
            putSample(metrics, "attitude_pitch", "Attitude Pitch", attitude.pitch(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "attitude_bank", "Attitude Bank", attitude.bank(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "attitude_yaw", "Attitude Yaw", attitude.yaw(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "forward_x", "Forward X", attitude.forwardX(), MetricType.GENERIC);
            putSample(metrics, "forward_z", "Forward Z", attitude.forwardZ(), MetricType.GENERIC);
        }
        putSample(metrics, "power_down", "Power Down", invokeFloat(sourceBE, "getPower", Direction.DOWN), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_up", "Power Up", invokeFloat(sourceBE, "getPower", Direction.UP), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_north", "Power North", invokeFloat(sourceBE, "getPower", Direction.NORTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_south", "Power South", invokeFloat(sourceBE, "getPower", Direction.SOUTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_west", "Power West", invokeFloat(sourceBE, "getPower", Direction.WEST), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_east", "Power East", invokeFloat(sourceBE, "getPower", Direction.EAST), MetricType.REDSTONE_POWER);
        return new InstrumentSourceHelper.SourceReadout("Gimbal Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readGimbalSensor(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float xAngle = invokeFloat(sourceBE, "getXAngle");
        final float zAngle = invokeFloat(sourceBE, "getZAngle");
        putSample(metrics, "x_angle", "X Angle", xAngle, MetricType.ANGLE_DEGREES);
        putSample(metrics, "z_angle", "Z Angle", zAngle, MetricType.ANGLE_DEGREES);
        final AttitudeSample attitude = resolvePoseAttitude(sourceBE, xAngle, zAngle, settings);
        if (attitude != null) {
            putSample(metrics, "attitude_pitch", "Attitude Pitch", attitude.pitch(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "attitude_bank", "Attitude Bank", attitude.bank(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "attitude_yaw", "Attitude Yaw", attitude.yaw(), MetricType.ANGLE_DEGREES);
            putSample(metrics, "forward_x", "Forward X", attitude.forwardX(), MetricType.GENERIC);
            putSample(metrics, "forward_z", "Forward Z", attitude.forwardZ(), MetricType.GENERIC);
        }
        final VehicleForwardAuthority.Resolution heading = resolveVehicleHeading(sourceBE, settings, true, false);
        putSample(metrics, "heading_source", "Heading Source: " + heading.sourceLabel(), heading.confidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "heading_confidence", "Heading Confidence", heading.confidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "power_down", "Power Down", invokeFloat(sourceBE, "getPower", Direction.DOWN), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_up", "Power Up", invokeFloat(sourceBE, "getPower", Direction.UP), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_north", "Power North", invokeFloat(sourceBE, "getPower", Direction.NORTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_south", "Power South", invokeFloat(sourceBE, "getPower", Direction.SOUTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_west", "Power West", invokeFloat(sourceBE, "getPower", Direction.WEST), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_east", "Power East", invokeFloat(sourceBE, "getPower", Direction.EAST), MetricType.REDSTONE_POWER);
        return new InstrumentSourceHelper.SourceReadout("Gimbal Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readContraptionInfo(BlockEntity sourceBE) {
        if (!matches(sourceBE, GIMBAL_SENSOR_BE)) {
            return null;
        }
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        if (subLevel == null) {
            return null;
        }
        invoke(subLevel, "enableIndividualQueuedForcesTracking", true);
        final float totalMass = Math.max(0.0f, resolveContraptionMass(subLevel));
        final ContraptionForceSnapshot snapshot = resolveContraptionForceSnapshot(sourceBE, subLevel, totalMass);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        putSample(metrics, "propulsion", "Propulsion Force", snapshot.magnitudes().getOrDefault("propulsion", 0.0f), MetricType.GENERIC);
        putSample(metrics, "gravity", "Weight Load", snapshot.gravityMagnitude(), MetricType.GENERIC);
        putSample(metrics, "drag", "Drag Force", snapshot.magnitudes().getOrDefault("drag", 0.0f), MetricType.GENERIC);
        putSample(metrics, "levitation", "Levitation Force", snapshot.magnitudes().getOrDefault("levitation", 0.0f), MetricType.GENERIC);
        putSample(metrics, "balloon_lift", "Balloon Buoyancy", snapshot.magnitudes().getOrDefault("balloon_lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "lift", "Aerodynamic Lift", snapshot.magnitudes().getOrDefault("lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "magnetic_force", "Magnetic Force", snapshot.magnitudes().getOrDefault("magnetic_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "other_force", "Other Force", snapshot.magnitudes().getOrDefault("other_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "support", "Support Force", snapshot.support(), MetricType.GENERIC);
        putSample(metrics, "forward_thrust", "Forward Thrust", snapshot.thrust(), MetricType.GENERIC);
        putSample(metrics, "vertical_margin", "Net Vertical", snapshot.verticalMargin(), MetricType.GENERIC);
        putSample(metrics, "forward_margin", "Forward Margin", snapshot.forwardMargin(), MetricType.GENERIC);
        putSample(metrics, "reverse_force", "Braking Force", snapshot.reverseForce(), MetricType.GENERIC);
        putSample(metrics, "downforce", "Downforce", snapshot.downforce(), MetricType.GENERIC);
        putSample(metrics, "lateral_force", "Lateral Force", snapshot.lateralForce(), MetricType.GENERIC);
        putSample(metrics, "propulsion_support", "Prop Support", snapshot.supportShares().getOrDefault("propulsion", 0.0f), MetricType.GENERIC);
        putSample(metrics, "levitation_support", "Levitation Support", snapshot.supportShares().getOrDefault("levitation", 0.0f), MetricType.GENERIC);
        putSample(metrics, "balloon_support", "Balloon Support", snapshot.supportShares().getOrDefault("balloon_lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "aero_support", "Aero Support", snapshot.supportShares().getOrDefault("lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "magnetic_support", "Magnetic Support", snapshot.supportShares().getOrDefault("magnetic_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "other_support", "Other Support", snapshot.supportShares().getOrDefault("other_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "classification_coverage", "Classified Share", snapshot.classifiedPercent(), MetricType.FILL_LEVEL);
        putSample(metrics, "directional_coverage", "Directional Share", snapshot.directionalPercent(), MetricType.FILL_LEVEL);
        putSample(metrics, "axis_mode", "Axis: " + snapshot.axisMode(), snapshot.axisConfidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "heading_source", "Heading Source: " + snapshot.headingSource(), snapshot.axisConfidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "total_mass", "Contraption Mass", totalMass, MetricType.GENERIC);
        return new InstrumentSourceHelper.SourceReadout(CONTRAPTION_INFO_SOURCE, InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    @Nullable
    private static InstrumentSourceHelper.SourceReadout readContraptionInfo(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings) {
        if (!matches(sourceBE, GIMBAL_SENSOR_BE)) {
            return null;
        }
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        if (subLevel == null) {
            return null;
        }
        invoke(subLevel, "enableIndividualQueuedForcesTracking", true);
        final float totalMass = Math.max(0.0f, resolveContraptionMass(subLevel));
        final ContraptionForceSnapshot snapshot = resolveContraptionForceSnapshot(sourceBE, subLevel, totalMass, settings);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        putSample(metrics, "propulsion", "Propulsion Force", snapshot.magnitudes().getOrDefault("propulsion", 0.0f), MetricType.GENERIC);
        putSample(metrics, "gravity", "Weight Load", snapshot.gravityMagnitude(), MetricType.GENERIC);
        putSample(metrics, "drag", "Drag Force", snapshot.magnitudes().getOrDefault("drag", 0.0f), MetricType.GENERIC);
        putSample(metrics, "levitation", "Levitation Force", snapshot.magnitudes().getOrDefault("levitation", 0.0f), MetricType.GENERIC);
        putSample(metrics, "balloon_lift", "Balloon Buoyancy", snapshot.magnitudes().getOrDefault("balloon_lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "lift", "Aerodynamic Lift", snapshot.magnitudes().getOrDefault("lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "magnetic_force", "Magnetic Force", snapshot.magnitudes().getOrDefault("magnetic_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "other_force", "Other Force", snapshot.magnitudes().getOrDefault("other_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "support", "Support Force", snapshot.support(), MetricType.GENERIC);
        putSample(metrics, "forward_thrust", "Forward Thrust", snapshot.thrust(), MetricType.GENERIC);
        putSample(metrics, "vertical_margin", "Net Vertical", snapshot.verticalMargin(), MetricType.GENERIC);
        putSample(metrics, "forward_margin", "Forward Margin", snapshot.forwardMargin(), MetricType.GENERIC);
        putSample(metrics, "reverse_force", "Braking Force", snapshot.reverseForce(), MetricType.GENERIC);
        putSample(metrics, "downforce", "Downforce", snapshot.downforce(), MetricType.GENERIC);
        putSample(metrics, "lateral_force", "Lateral Force", snapshot.lateralForce(), MetricType.GENERIC);
        putSample(metrics, "propulsion_support", "Prop Support", snapshot.supportShares().getOrDefault("propulsion", 0.0f), MetricType.GENERIC);
        putSample(metrics, "levitation_support", "Levitation Support", snapshot.supportShares().getOrDefault("levitation", 0.0f), MetricType.GENERIC);
        putSample(metrics, "balloon_support", "Balloon Support", snapshot.supportShares().getOrDefault("balloon_lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "aero_support", "Aero Support", snapshot.supportShares().getOrDefault("lift", 0.0f), MetricType.GENERIC);
        putSample(metrics, "magnetic_support", "Magnetic Support", snapshot.supportShares().getOrDefault("magnetic_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "other_support", "Other Support", snapshot.supportShares().getOrDefault("other_force", 0.0f), MetricType.GENERIC);
        putSample(metrics, "classification_coverage", "Classified Share", snapshot.classifiedPercent(), MetricType.FILL_LEVEL);
        putSample(metrics, "directional_coverage", "Directional Share", snapshot.directionalPercent(), MetricType.FILL_LEVEL);
        putSample(metrics, "axis_mode", "Axis: " + snapshot.axisMode(), snapshot.axisConfidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "heading_source", "Heading Source: " + snapshot.headingSource(), snapshot.axisConfidence(), MetricType.FILL_LEVEL);
        putSample(metrics, "total_mass", "Contraption Mass", totalMass, MetricType.GENERIC);
        return new InstrumentSourceHelper.SourceReadout(CONTRAPTION_INFO_SOURCE, InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readAltitudeSensor(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float lowSignal = readFloatField(sourceBE, "lowSignal");
        final float highSignal = readFloatField(sourceBE, "highSignal");
        final float bandMin = Math.min(invokeFloat(sourceBE, "toWorldHeight", lowSignal), invokeFloat(sourceBE, "toWorldHeight", highSignal));
        final float bandMax = Math.max(invokeFloat(sourceBE, "toWorldHeight", lowSignal), invokeFloat(sourceBE, "toWorldHeight", highSignal));
        putSample(metrics, "world_height", "World Height", invokeFloat(sourceBE, "getWorldHeight"), MetricType.GENERIC);
        putSample(metrics, "normalized_height", "Normalized Height", invokeFloat(sourceBE, "getNormalHeight") * 100.0f, MetricType.FILL_LEVEL);
        putSample(metrics, "band_min_height", "Band Min Height", bandMin, MetricType.GENERIC);
        putSample(metrics, "band_max_height", "Band Max Height", bandMax, MetricType.GENERIC);
        putSample(metrics, "air_pressure", "Air Pressure", invokeFloat(sourceBE, "getAirPressure") * 100.0f, MetricType.FILL_LEVEL);
        putSample(metrics, "signal", "Signal", invokeFloat(sourceBE, "getSignal"), MetricType.REDSTONE_POWER);
        return new InstrumentSourceHelper.SourceReadout("Altitude Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readDockingConnector(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final Object otherConnector = invoke(sourceBE, "getOtherConnector");
        final BlockPos otherPos = readField(sourceBE, "otherConnectorPosition", BlockPos.class);
        final UUID otherSubLevelId = readField(sourceBE, "otherConnectorSubLevelId", UUID.class);
        final boolean powered = readBooleanField(sourceBE, "powered");
        final boolean paired = otherPos != null;
        final boolean pairLoaded = otherConnector instanceof BlockEntity;
        final boolean locked = invokeBoolean(sourceBE, "isLocked");
        final boolean extended = invokeBoolean(sourceBE, "isExtended");
        final float closestPairDistance = Math.max(0.0f, (float) readDoubleField(sourceBE, "closestPairDistance"));
        final String stateText = formatEnumLabel(readField(sourceBE, "state"));
        putSample(metrics, "paired", "Paired", paired ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "locked", "Locked", locked ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "pair_loaded", "Pair Loaded", pairLoaded ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "extended", "Extended", extended ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "powered", "Powered", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "closest_pair_distance", "Closest Pair Distance", closestPairDistance, MetricType.GENERIC);
        putSample(metrics, "pair_x", "Pair X", otherPos != null ? otherPos.getX() : 0.0f, MetricType.GENERIC);
        putSample(metrics, "pair_y", "Pair Y", otherPos != null ? otherPos.getY() : 0.0f, MetricType.GENERIC);
        putSample(metrics, "pair_z", "Pair Z", otherPos != null ? otherPos.getZ() : 0.0f, MetricType.GENERIC);
        putSample(metrics, "connector_state", "State: " + (stateText.isBlank() ? "--" : stateText), !stateText.isBlank() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "pair_sublevel", "Sublevel: " + resolveDockingSubLevel(otherConnector, otherSubLevelId), paired ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "pair_position", "Position: " + (otherPos != null ? formatBlockPos(otherPos) : "--"), paired ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Docking Connector", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readSteeringWheel(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float generatedSpeed = invokeFloat(sourceBE, "getGeneratedSpeed");
        final float logicalSpeed = invokeFloat(sourceBE, "getLogicalSpeed");
        final float rpm = Math.max(Math.abs(generatedSpeed), Math.abs(logicalSpeed));
        final boolean held = readBooleanField(sourceBE, "held");
        final boolean inUse = readFloatField(sourceBE, "inUse") > 0.0f;
        putSample(metrics, "angle", "Angle", invokeFloat(sourceBE, "getAngle"), MetricType.ANGLE_DEGREES);
        putSample(metrics, "target_angle", "Target", readFloatField(sourceBE, "targetAngle"), MetricType.ANGLE_DEGREES);
        putSample(metrics, "pending_target", "Pending Target", readFloatField(sourceBE, "targetAngleToUpdate"), MetricType.ANGLE_DEGREES);
        putSample(metrics, "generated_speed", "Generated Speed", generatedSpeed, MetricType.RPM);
        putSample(metrics, "logical_speed", "Logical Speed", logicalSpeed, MetricType.RPM);
        putSample(metrics, "rpm", "RPM", rpm, MetricType.RPM);
        putSample(metrics, "held", "Held", held ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "in_use", "In Use", inUse ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "running", "Running", rpm > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Steering Wheel", InstrumentSourceHelper.SourceKind.KINETIC_SPEED, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readLaserSensor(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final double rawDistance = readDoubleField(sourceBE, "closestHitDistance");
        final boolean hitPresent = Double.isFinite(rawDistance) && rawDistance < 1.0E9;
        final float closestHitDistance = hitPresent ? (float) rawDistance : 0.0f;
        final float currentPower = readFloatField(sourceBE, "currentPower");
        final boolean powered = currentPower > 0.5f;
        putSample(metrics, "current_power", "Current Power", currentPower, MetricType.REDSTONE_POWER);
        putSample(metrics, "closest_hit_distance", "Closest Hit Distance", closestHitDistance, MetricType.GENERIC);
        putSample(metrics, "hit_present", "Hit Present", hitPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "powered", "Powered", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Laser Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readOpticalSensor(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final Object hitBlockValue = invoke(sourceBE, "getHitBlock");
        final Block hitBlock = hitBlockValue instanceof Block block ? block : Blocks.AIR;
        final boolean hitPresent = hitBlock != Blocks.AIR && !hitBlock.defaultBlockState().isAir();
        final float rayDistance = invokeFloat(sourceBE, "getRayDistance");
        final float hitDistance = hitPresent ? invokeFloat(sourceBE, "getHitBlockDistance") : 0.0f;
        final boolean powered = sourceBE.getBlockState().hasProperty(BlockStateProperties.POWERED)
                && sourceBE.getBlockState().getValue(BlockStateProperties.POWERED);
        putSample(metrics, "ray_distance", "Ray Distance", rayDistance, MetricType.GENERIC);
        putSample(metrics, "hit_block_distance", "Hit Distance", hitDistance, MetricType.GENERIC);
        putSample(metrics, "hit_present", "Hit Present", hitPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "powered", "Powered", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "hit_block", "Block: " + resolveBlockName(hitBlock), hitPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Optical Sensor", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readNavigationTable(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float distanceToTarget = invokeFloat(sourceBE, "distanceToTarget");
        final float lastDistanceToTarget = invokeFloat(sourceBE, "lastDistanceToTarget");
        putSample(metrics, "distance_to_target", "Distance to Target", distanceToTarget, MetricType.GENERIC);
        putSample(metrics, "last_distance_to_target", "Last Distance", lastDistanceToTarget, MetricType.GENERIC);
        putSample(metrics, "distance_delta", "Distance Delta", distanceToTarget - lastDistanceToTarget, MetricType.GENERIC);
        putSample(metrics, "relative_angle", "Relative Angle", invokeFloat(sourceBE, "getRelativeAngle"), MetricType.ANGLE_DEGREES);
        final Object selfPosObj = invoke(sourceBE, "getProjectedSelfPos");
        final Object targetPosObj = invoke(sourceBE, "getTargetPosition", true);
        if (selfPosObj instanceof Vec3 selfPos && targetPosObj instanceof Vec3 targetPos) {
            putSample(metrics, "target_dx", "Target Delta X", (float) (targetPos.x - selfPos.x), MetricType.GENERIC);
            putSample(metrics, "target_dz", "Target Delta Z", (float) (targetPos.z - selfPos.z), MetricType.GENERIC);
        }
        putSample(metrics, "power_down", "Power Down", invokeFloat(sourceBE, "getRedstoneStrength", Direction.DOWN), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_up", "Power Up", invokeFloat(sourceBE, "getRedstoneStrength", Direction.UP), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_north", "Power North", invokeFloat(sourceBE, "getRedstoneStrength", Direction.NORTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_south", "Power South", invokeFloat(sourceBE, "getRedstoneStrength", Direction.SOUTH), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_west", "Power West", invokeFloat(sourceBE, "getRedstoneStrength", Direction.WEST), MetricType.REDSTONE_POWER);
        putSample(metrics, "power_east", "Power East", invokeFloat(sourceBE, "getRedstoneStrength", Direction.EAST), MetricType.REDSTONE_POWER);
        return new InstrumentSourceHelper.SourceReadout("Navigation Table", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readLinkedTypewriter(BlockEntity sourceBE) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final Object typedValue = invoke(sourceBE, "getTypedEntry");
        final String typedEntry = sanitizeLiveText(typedValue instanceof String text ? text : null);
        final boolean hasText = !typedEntry.isBlank();
        final boolean powered = readBooleanField(sourceBE, "powered");
        final int activeKeys = resolvePressedKeyCount(sourceBE);
        putSample(metrics, "typed_text", "Text: " + (hasText ? truncateLabelText(typedEntry, 20) : "--"), hasText ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "typed_length", "Typed Length", typedEntry.length(), MetricType.GENERIC);
        putSample(metrics, "active_keys", "Active Keys", activeKeys, MetricType.GENERIC);
        putSample(metrics, "powered", "Powered", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Linked Typewriter", InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readAugerShaft(BlockEntity sourceBE) {
        final InstrumentSourceHelper.SourceReadout baseReadout = InstrumentSourceHelper.readBaseSource(sourceBE, null, java.util.List.of());
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>(baseReadout.metrics());
        final float absSpeed = Math.abs(invokeFloat(sourceBE, "getSpeed"));
        putSample(metrics, "generated_speed", "Generated Speed", absSpeed, MetricType.RPM);
        putSample(metrics, "abs_speed", "Speed (abs)", absSpeed, MetricType.RPM);
        putSample(metrics, "item_speed", "Items/sec", absSpeed / 102.4f, MetricType.ITEMS_PER_SEC);
        putSample(metrics, "actor_items", "Actor Items", resolveActorItemCount(sourceBE), MetricType.ITEM_COUNT);
        putSample(metrics, "max_speed", "Max Speed", readBooleanField(sourceBE, "maxSpeed") ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "running", "Running", absSpeed > 0.01f ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        return new InstrumentSourceHelper.SourceReadout("Auger Throughput", baseReadout.sourceKind(), metrics);
    }

    private static InstrumentSourceHelper.SourceReadout readHotAirBurner(BlockEntity sourceBE) {
        return readLiftingGasEmitter(sourceBE, "Hot Air Burner");
    }

    private static InstrumentSourceHelper.SourceReadout readSteamVent(BlockEntity sourceBE) {
        return readLiftingGasEmitter(sourceBE, "Steam Vent");
    }

    private static InstrumentSourceHelper.SourceReadout readLiftingGasEmitter(BlockEntity sourceBE, String sourceType) {
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final float signalStrength = invokeFloat(sourceBE, "getSignalStrength");
        final float gasOutput = invokeFloat(sourceBE, "getGasOutput");
        final float targetVolume = resolveConfiguredGasVolume(sourceBE);
        final float maxCapacity = Math.max(0.0f, readFloatField(sourceBE, "maxCapacity"));
        final boolean powered = readBooleanField(sourceBE, "powered") || signalStrength > 0.0f;
        final boolean canOutput = invokeBoolean(sourceBE, "canOutputGas");
        final float predictedVolume = invokeFloat(sourceBE, "getClientPredictedVolume");
        final float efficiencyPercent = Math.max(0.0f, Math.min(100.0f, readFloatField(sourceBE, "efficiency") * 100.0f));
        final Object balloon = invoke(sourceBE, "getBalloon");
        final boolean balloonPresent = balloon != null;
        final float outputPercent = maxCapacity > 0.0f ? Math.min(100.0f, gasOutput * 100.0f / maxCapacity) : 0.0f;
        final float targetPercent = maxCapacity > 0.0f ? Math.min(100.0f, targetVolume * 100.0f / maxCapacity) : 0.0f;
        final float predictedPercent = maxCapacity > 0.0f ? Math.min(100.0f, predictedVolume * 100.0f / maxCapacity) : 0.0f;
        final Integer balloonCapacityValue = invokeInt(balloon, "getCapacity");
        final float balloonCapacity = balloonCapacityValue != null ? balloonCapacityValue.floatValue() : 0.0f;
        final float balloonFill = invokeFloat(balloon, "getTotalFilledVolume");
        final float balloonTarget = invokeFloat(balloon, "getTotalTargetVolume");
        final float balloonLift = invokeFloat(balloon, "getTotalLift");
        final float balloonFillPercent = balloonCapacity > 0.0f ? Math.min(100.0f, balloonFill * 100.0f / balloonCapacity) : 0.0f;
        final float balloonTargetPercent = balloonCapacity > 0.0f ? Math.min(100.0f, balloonTarget * 100.0f / balloonCapacity) : 0.0f;
        final Object gasType = invoke(sourceBE, "getLiftingGasType");

        putSample(metrics, "signal_strength", "Signal Strength", signalStrength, MetricType.REDSTONE_POWER);
        putSample(metrics, "gas_output", "Gas Output", gasOutput, MetricType.GENERIC);
        putSample(metrics, "gas_target_volume", "Target Volume", targetVolume, MetricType.GENERIC);
        putSample(metrics, "gas_capacity_volume", "Capacity", maxCapacity, MetricType.GENERIC);
        putSample(metrics, "output_pct", "Output %", outputPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "target_pct", "Target %", targetPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "powered", "Powered", powered ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "can_output", "Can Output", canOutput ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "predicted_volume", "Predicted Volume", predictedVolume, MetricType.GENERIC);
        putSample(metrics, "predicted_pct", "Predicted Fill %", predictedPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "emitter_efficiency", "Emitter Efficiency", efficiencyPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "gas_type", "Gas: " + resolveLiftingGasTypeName(gasType), gasType != null ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "gas_lift_strength", "Gas Lift Strength", invokeFloat(gasType, "getLiftStrength"), MetricType.GENERIC);
        putSample(metrics, "balloon_present", "Balloon Attached", balloonPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        putSample(metrics, "balloon_fill_pct", "Balloon Fill %", balloonFillPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "balloon_target_pct", "Balloon Target %", balloonTargetPercent, MetricType.FILL_LEVEL);
        putSample(metrics, "balloon_lift", "Balloon Lift", balloonLift, MetricType.GENERIC);
        return new InstrumentSourceHelper.SourceReadout(sourceType, InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    private static boolean shouldEmitContraptionInfo(IAutoDataReceiver receiver, @Nullable BlockEntity sourceBE) {
        return matches(sourceBE, GIMBAL_SENSOR_BE)
                && (receiver instanceof StatsDisplayPanelBE || receiver instanceof HolographicProjectorBE);
    }

    private static void emitReadout(IAutoDataReceiver receiver, BlockPos sourcePos, InstrumentSourceHelper.SourceReadout readout, boolean exactSourceType, boolean updateActiveSourceType) {
        if (updateActiveSourceType) {
            receiver.setSourceType(readout.sourceType());
        }
        if (readout.metrics().isEmpty()) {
            if (exactSourceType) {
                registerExactSource(receiver, sourcePos, readout.sourceType());
            } else {
                receiver.registerOrUpdateSource(sourcePos, readout.sourceType());
            }
            return;
        }
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            if (exactSourceType) {
                emitExactMetric(receiver, sourcePos, readout.sourceType(), sample);
            } else {
                receiver.onAutoData(sourcePos, readout.sourceType(), sample.metricId(), sample.label(), sample.value(), sample.type());
            }
        }
    }

    private static void registerExactSource(IAutoDataReceiver receiver, BlockPos sourcePos, String sourceType) {
        if (receiver instanceof StatsDisplayPanelBE panel) {
            panel.registerOrUpdateSourceExact(sourcePos, sourceType);
        } else if (receiver instanceof HolographicProjectorBE projector) {
            projector.registerOrUpdateSourceExact(sourcePos, sourceType);
        } else {
            receiver.registerOrUpdateSource(sourcePos, sourceType);
        }
    }

    private static void emitExactMetric(IAutoDataReceiver receiver, BlockPos sourcePos, String sourceType, InstrumentSourceHelper.MetricSample sample) {
        if (receiver instanceof StatsDisplayPanelBE panel) {
            panel.onAutoDataExact(sourcePos, sourceType, sample.metricId(), sample.label(), sample.value(), sample.type());
        } else if (receiver instanceof HolographicProjectorBE projector) {
            projector.onAutoDataExact(sourcePos, sourceType, sample.metricId(), sample.label(), sample.value(), sample.type());
        } else {
            receiver.onAutoData(sourcePos, sourceType, sample.metricId(), sample.label(), sample.value(), sample.type());
        }
    }

    @Nullable
    private static Object resolveContainingSubLevel(Object sourceBE) {
        try {
            final Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            final Object helper = sableClass.getField("HELPER").get(null);
            return invoke(helper, "getContaining", sourceBE);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static float resolveContraptionMass(Object subLevel) {
        final Object massTracker = invoke(subLevel, "getMassTracker");
        return invokeFloat(massTracker, "getMass");
    }

    private static final class CachedReadout {
        private final long tick;
        private final InstrumentSourceHelper.SourceReadout readout;

        private CachedReadout(long tick, InstrumentSourceHelper.SourceReadout readout) {
            this.tick = tick;
            this.readout = readout;
        }
    }

    private static ContraptionForceSnapshot resolveContraptionForceSnapshot(BlockEntity sourceBE, Object subLevel, float totalMass) {
        return resolveContraptionForceSnapshot(sourceBE, subLevel, totalMass, null);
    }

    private static ContraptionForceSnapshot resolveContraptionForceSnapshot(BlockEntity sourceBE, Object subLevel, float totalMass, @Nullable StatsDisplaySettings settings) {
        final Map<String, ForceFamilyAccumulator> families = new LinkedHashMap<>();
        final float divisor = Math.max(1.0E-4f, resolvePhysicsStepDuration(subLevel));
        final Object logicalPose = invoke(subLevel, "logicalPose");
        final Vector3d poseOrigin = transformPosePosition(logicalPose, new Vector3d());
        float totalMagnitude = 0.0f;
        float classifiedMagnitude = 0.0f;
        float directionalMagnitude = 0.0f;
        final Object queuedForceGroups = invoke(subLevel, "getQueuedForceGroups");
        if (queuedForceGroups instanceof Map<?, ?> forceMap) {
            for (Map.Entry<?, ?> entry : forceMap.entrySet()) {
                String metricId = normalizeContraptionForceKey(entry.getKey());
                if ("gravity".equals(metricId)) {
                    continue;
                }
                if (metricId.isEmpty()) {
                    metricId = "other_force";
                }
                final ForceFamilyAccumulator family = families.computeIfAbsent(metricId, key -> new ForceFamilyAccumulator());
                final Object recordedPointForces = invoke(entry.getValue(), "getRecordedPointForces");
                if (recordedPointForces instanceof Collection<?> forces && !forces.isEmpty()) {
                    for (Object pointForce : forces) {
                        final Object vector = invoke(pointForce, "force");
                        final ForceContribution contribution = resolveContraptionForceContribution(vector, divisor, true, logicalPose, poseOrigin);
                        family.add(contribution);
                        totalMagnitude += contribution.magnitude();
                        if (contribution.directionKnown()) {
                            directionalMagnitude += contribution.magnitude();
                        }
                        if (!"other_force".equals(metricId)) {
                            classifiedMagnitude += contribution.magnitude();
                        }
                    }
                    continue;
                }
                final Object forceTotal = invoke(entry.getValue(), "getForceTotal");
                Object vector = invoke(forceTotal, "lastLocalForce");
                if (vector == null) {
                    vector = readField(forceTotal, "lastLocalForce");
                }
                final ForceContribution contribution = resolveContraptionForceContribution(vector, divisor, true, logicalPose, poseOrigin);
                family.add(contribution);
                totalMagnitude += contribution.magnitude();
                if (contribution.directionKnown()) {
                    directionalMagnitude += contribution.magnitude();
                }
                if (!"other_force".equals(metricId)) {
                    classifiedMagnitude += contribution.magnitude();
                }
            }
        }
        final Map<String, Float> magnitudes = new LinkedHashMap<>();
        for (Map.Entry<String, ForceFamilyAccumulator> entry : families.entrySet()) {
            magnitudes.put(entry.getKey(), entry.getValue().magnitude());
        }

        final float gravityMagnitude = resolveGravityForce(subLevel, totalMass);
        final Vector3d gravityVector = resolveGravityVector(subLevel);
        final ForceAxes axes = resolveForceAxes(sourceBE, subLevel, logicalPose, gravityVector, settings);
        final Vector3d up = axes.up();
        final Vector3d forward = axes.forward();
        final Vector3d right = axes.right();

        float support = 0.0f;
        float thrust = 0.0f;
        float verticalSum = 0.0f;
        float forwardSum = 0.0f;
        float reverseForce = 0.0f;
        float downforce = 0.0f;
        float lateralForce = 0.0f;
        final Map<String, Float> supportShares = new LinkedHashMap<>();

        for (Map.Entry<String, ForceFamilyAccumulator> entry : families.entrySet()) {
            final Vector3d force = entry.getValue().resolvedVector();
            final float verticalComponent = (float) force.dot(up);
            verticalSum += verticalComponent;
            if (verticalComponent > 0.0f) {
                support += verticalComponent;
                supportShares.put(entry.getKey(), verticalComponent);
            }
            if (verticalComponent < 0.0f) {
                downforce += -verticalComponent;
            }
            if (forward != null) {
                final float forwardComponent = (float) force.dot(forward);
                forwardSum += forwardComponent;
                if (forwardComponent > 0.0f) {
                    thrust += forwardComponent;
                } else if (forwardComponent < 0.0f) {
                    reverseForce += -forwardComponent;
                }
                if (right != null) {
                    lateralForce += Math.abs((float) force.dot(right));
                }
            }
        }

        final float classifiedPercent = totalMagnitude > 0.01f
                ? Math.min(100.0f, classifiedMagnitude * 100.0f / totalMagnitude)
                : 100.0f;
        final float directionalPercent = totalMagnitude > 0.01f
                ? Math.min(100.0f, directionalMagnitude * 100.0f / totalMagnitude)
                : 100.0f;
        return new ContraptionForceSnapshot(
                magnitudes,
                supportShares,
                gravityMagnitude,
                support,
                thrust,
                verticalSum - gravityMagnitude,
                forward != null ? forwardSum : 0.0f,
                reverseForce,
                downforce,
                lateralForce,
                axes.mode(),
                axes.confidence(),
                axes.headingSource(),
                classifiedPercent,
                directionalPercent
        );
    }

    private static ForceContribution resolveContraptionForceContribution(@Nullable Object vector, float divisor, boolean localSpace,
                                                                         @Nullable Object pose, @Nullable Vector3d poseOrigin) {
        final Vector3d scaledForce = readVector(vector, divisor);
        final float magnitude = (float) scaledForce.length();
        if (magnitude <= 1.0E-6f) {
            return new ForceContribution(null, 0.0f, true);
        }
        if (!localSpace) {
            return new ForceContribution(scaledForce, magnitude, true);
        }
        final Vector3d worldForce = transformPoseVector(pose, scaledForce, poseOrigin);
        return new ForceContribution(worldForce, magnitude, worldForce != null);
    }

    private static float resolveGravityForce(Object subLevel, float totalMass) {
        if (totalMass <= 0.0f) {
            return 0.0f;
        }
        final Vector3d gravity = resolveGravityVector(subLevel);
        return gravity.lengthSquared() > 0.0d ? (float) gravity.length() * totalMass : 0.0f;
    }

    private static float resolvePhysicsStepDuration(Object subLevel) {
        final Object level = invoke(subLevel, "getLevel");
        if (!(level instanceof Level minecraftLevel)) {
            return 0.05f;
        }
        try {
            final Class<?> systemClass = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            final Method get = systemClass.getMethod("get", Level.class);
            final Object system = get.invoke(null, minecraftLevel);
            final Object config = invoke(system, "getConfig");
            final Object substeps = readField(config, "substepsPerTick");
            final float substepsPerTick = substeps instanceof Number number ? Math.max(1.0f, number.floatValue()) : 1.0f;
            return 0.05f / substepsPerTick;
        } catch (ReflectiveOperationException ignored) {
            return 0.05f;
        }
    }

    private static ForceAxes resolveForceAxes(BlockEntity sourceBE, Object subLevel, @Nullable Object logicalPose, Vector3d gravityVector, @Nullable StatsDisplaySettings settings) {
        final Vector3d up = VehicleForwardAuthority.normalizeUp(gravityVector.lengthSquared() > 1.0E-8d ? new Vector3d(gravityVector).negate() : new Vector3d(0.0d, 1.0d, 0.0d));
        final Vector3d localOrigin = new Vector3d(
                sourceBE.getBlockPos().getX() + 0.5d,
                sourceBE.getBlockPos().getY() + 0.5d,
                sourceBE.getBlockPos().getZ() + 0.5d
        );
        final Vector3d bodyForward = transformBasis(logicalPose, localOrigin, 0.0d, 0.0d, 1.0d, transformPosePosition(logicalPose, localOrigin));
        final Vector3d motionHorizontal = resolveMotionVector(sourceBE, subLevel, up);
        final VehicleForwardAuthority.Resolution heading = resolveVehicleHeading(sourceBE, settings, bodyForward, null, motionHorizontal);
        final Vector3d resolvedForward = heading.hasForward() ? new Vector3d(heading.forward()) : null;
        Vector3d right = null;
        if (resolvedForward != null) {
            final Vector3d candidate = new Vector3d(up).cross(resolvedForward);
            if (candidate.lengthSquared() > 1.0E-8d) {
                right = candidate.normalize();
            }
        }
        return new ForceAxes(up, resolvedForward, right, heading.sourceTag(), heading.confidence(), heading.sourceLabel());
    }

    private static float resolveHorizontalVelocity(BlockEntity sourceBE) {
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        if (subLevel == null) {
            return 0.0f;
        }
        final Vector3d localPos = new Vector3d(
                sourceBE.getBlockPos().getX() + 0.5d,
                sourceBE.getBlockPos().getY() + 0.5d,
                sourceBE.getBlockPos().getZ() + 0.5d
        );
        final Object logicalPose = invoke(subLevel, "logicalPose");
        final Object lastPose = invoke(subLevel, "lastPose");
        final Object currentPos = invoke(logicalPose, "transformPosition", localPos, new Vector3d());
        final Object previousPos = invoke(lastPose, "transformPosition", localPos, new Vector3d());
        if (currentPos == null || previousPos == null) {
            return 0.0f;
        }
        final double dx = readCoordinate(currentPos, "x") - readCoordinate(previousPos, "x");
        final double dz = readCoordinate(currentPos, "z") - readCoordinate(previousPos, "z");
        return (float) (Math.sqrt(dx * dx + dz * dz) * 20.0d);
    }

    private static VehicleForwardAuthority.Resolution resolveVehicleHeading(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings, boolean allowBodyPose, boolean allowVelocityAxis) {
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        if (subLevel == null) {
            return VehicleForwardAuthority.resolve(new Vector3d(0.0d, 1.0d, 0.0d), null, null, null, resolveCachedForward(sourceBE, "no_sublevel"), resolveNoseSetting(settings));
        }
        final Vector3d gravity = resolveGravityVector(subLevel);
        final Vector3d up = VehicleForwardAuthority.normalizeUp(gravity.lengthSquared() > 1.0E-8d ? new Vector3d(gravity).negate() : new Vector3d(0.0d, 1.0d, 0.0d));
        return resolveVehicleHeading(sourceBE, settings, allowBodyPose ? resolvePoseForwardBasis(sourceBE, subLevel) : null,
                allowVelocityAxis ? resolvePoseForwardBasis(sourceBE, subLevel) : null, resolveMotionVector(sourceBE, subLevel, up));
    }

    private static VehicleForwardAuthority.Resolution resolveVehicleHeading(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings,
                                                                           @Nullable Vector3d bodyForward, @Nullable Vector3d velocityAxis,
                                                                           @Nullable Vector3d motionVector) {
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        final Vector3d gravity = (subLevel != null) ? resolveGravityVector(subLevel) : new Vector3d();
        final Vector3d up = VehicleForwardAuthority.normalizeUp(gravity.lengthSquared() > 1.0E-8d ? new Vector3d(gravity).negate() : new Vector3d(0.0d, 1.0d, 0.0d));
        final String signature = buildForwardCacheSignature(sourceBE, settings, bodyForward != null, velocityAxis != null, subLevel);
        final VehicleForwardAuthority.Resolution resolution = VehicleForwardAuthority.resolve(up, bodyForward, velocityAxis, motionVector, resolveCachedForward(sourceBE, signature), resolveNoseSetting(settings));
        if (resolution.hasForward()) {
            FORWARD_AXIS_CACHE.put(sourceBE, new CachedForwardAxis(new Vector3d(resolution.forward()), signature));
        }
        return resolution;
    }

    @Nullable
    private static Vector3d resolvePoseForwardBasis(BlockEntity sourceBE, Object subLevel) {
        final Vector3d localOrigin = new Vector3d(
                sourceBE.getBlockPos().getX() + 0.5d,
                sourceBE.getBlockPos().getY() + 0.5d,
                sourceBE.getBlockPos().getZ() + 0.5d
        );
        final Object logicalPose = invoke(subLevel, "logicalPose");
        final Vector3d worldOrigin = transformPosePosition(logicalPose, localOrigin);
        return transformBasis(logicalPose, localOrigin, 0.0d, 0.0d, 1.0d, worldOrigin);
    }

    @Nullable
    private static Vector3d resolveMotionVector(BlockEntity sourceBE, Object subLevel, Vector3d up) {
        final Vector3d localOrigin = new Vector3d(
                sourceBE.getBlockPos().getX() + 0.5d,
                sourceBE.getBlockPos().getY() + 0.5d,
                sourceBE.getBlockPos().getZ() + 0.5d
        );
        final Object logicalPose = invoke(subLevel, "logicalPose");
        final Object lastPose = invoke(subLevel, "lastPose");
        if (logicalPose == null || lastPose == null) {
            return null;
        }
        final Vector3d currentPos = transformPosePosition(logicalPose, localOrigin);
        final Vector3d previousPos = transformPosePosition(lastPose, localOrigin);
        if (currentPos == null || previousPos == null) {
            return null;
        }
        return projectOntoPlane(currentPos.sub(previousPos, new Vector3d()), up);
    }

    @Nullable
    private static Vector3d resolveCachedForward(BlockEntity sourceBE, String signature) {
        final CachedForwardAxis cached = FORWARD_AXIS_CACHE.get(sourceBE);
        if (cached == null) {
            return null;
        }
        if (!cached.signature.equals(signature)) {
            FORWARD_AXIS_CACHE.remove(sourceBE);
            return null;
        }
        return cached.axis;
    }

    private static String buildForwardCacheSignature(BlockEntity sourceBE, @Nullable StatsDisplaySettings settings, boolean hasBody, boolean hasVelocityAxis, @Nullable Object subLevel) {
        return resolveNoseSetting(settings).name() + "|" + hasBody + "|" + hasVelocityAxis + "|" + sourceBE.getBlockPos().asLong() + "|" + ((subLevel != null) ? System.identityHashCode(subLevel) : 0);
    }

    private static StatsDisplaySettings.NoseSetting resolveNoseSetting(@Nullable StatsDisplaySettings settings) {
        return settings != null ? settings.getNoseSetting() : StatsDisplaySettings.NoseSetting.UNSET;
    }

    @Nullable
    private static AttitudeSample resolvePoseAttitude(BlockEntity sourceBE, float pitchHint, float bankHint) {
        return resolvePoseAttitude(sourceBE, pitchHint, bankHint, null);
    }

    @Nullable
    private static AttitudeSample resolvePoseAttitude(BlockEntity sourceBE, float pitchHint, float bankHint, @Nullable StatsDisplaySettings settings) {
        final Object subLevel = resolveContainingSubLevel(sourceBE);
        if (subLevel == null) {
            return null;
        }
        final Object logicalPose = invoke(subLevel, "logicalPose");
        if (logicalPose == null) {
            return null;
        }
        final Vector3d gravity = resolveGravityVector(subLevel);
        if (gravity.lengthSquared() < 1.0E-8d) {
            return null;
        }

        final Vector3d localOrigin = new Vector3d(
                sourceBE.getBlockPos().getX() + 0.5d,
                sourceBE.getBlockPos().getY() + 0.5d,
                sourceBE.getBlockPos().getZ() + 0.5d
        );
        final Vector3d origin = transformPosePosition(logicalPose, localOrigin);
        final Vector3d right = transformBasis(logicalPose, localOrigin, 1.0d, 0.0d, 0.0d, origin);
        final Vector3d up = transformBasis(logicalPose, localOrigin, 0.0d, 1.0d, 0.0d, origin);
        final Vector3d forward = transformBasis(logicalPose, localOrigin, 0.0d, 0.0d, 1.0d, origin);
        if (origin == null || right == null || up == null || forward == null) {
            return null;
        }

        final VehicleForwardAuthority.PoseAxes adjustedAxes = VehicleForwardAuthority.applyNoseToPoseAxes(forward, right, resolveNoseSetting(settings));
        final Vector3d adjustedForward = adjustedAxes.forward();
        final Vector3d adjustedRight = adjustedAxes.right();

        gravity.normalize();
        final double gravityRight = gravity.dot(adjustedRight);
        final double gravityUp = gravity.dot(up);
        final double gravityForward = gravity.dot(adjustedForward);
        float pitch = (float) Math.toDegrees(Math.atan2(gravityForward, -gravityUp));
        float bank = (float) Math.toDegrees(Math.atan2(gravityRight, -gravityUp));
        final float yaw = normalizeAngle((float) Math.toDegrees(Math.atan2(adjustedForward.z, adjustedForward.x)));
        final double horizontalLength = Math.sqrt(adjustedForward.x * adjustedForward.x + adjustedForward.z * adjustedForward.z);
        final float forwardX = horizontalLength > 1.0E-6d ? (float) (adjustedForward.x / horizontalLength) : 0.0f;
        final float forwardZ = horizontalLength > 1.0E-6d ? (float) (adjustedForward.z / horizontalLength) : 0.0f;
        pitch = alignAttitudeSign(pitch, pitchHint);
        bank = alignAttitudeSign(bank, bankHint);
        return new AttitudeSample(normalizeAngle(pitch), normalizeAngle(bank), yaw, forwardX, forwardZ);
    }

    @Nullable
    private static Vector3d transformPosePosition(@Nullable Object pose, Vector3d localPosition) {
        final Object transformed = invoke(pose, "transformPosition", localPosition, new Vector3d());
        if (transformed == null) {
            return null;
        }
        return new Vector3d(
                readCoordinate(transformed, "x"),
                readCoordinate(transformed, "y"),
                readCoordinate(transformed, "z")
        );
    }

    @Nullable
    private static Vector3d transformPoseVector(@Nullable Object pose, Vector3d localVector, @Nullable Vector3d worldOrigin) {
        if (worldOrigin == null) {
            return null;
        }
        final Vector3d transformed = transformPosePosition(pose, localVector);
        if (transformed == null) {
            return null;
        }
        return transformed.sub(worldOrigin, new Vector3d());
    }

    @Nullable
    private static Vector3d transformBasis(@Nullable Object pose, Vector3d localOrigin, double dx, double dy, double dz, @Nullable Vector3d worldOrigin) {
        if (worldOrigin == null) {
            return null;
        }
        final Vector3d transformedPoint = transformPosePosition(pose, new Vector3d(localOrigin).add(dx, dy, dz));
        if (transformedPoint == null) {
            return null;
        }
        final Vector3d basis = transformedPoint.sub(worldOrigin, new Vector3d());
        return basis.lengthSquared() > 1.0E-8d ? basis.normalize() : null;
    }

    private static float alignAttitudeSign(float derivedAngle, float hintAngle) {
        if (Math.abs(hintAngle) > 1.0f && Math.abs(derivedAngle) > 1.0f && Math.signum(hintAngle) != Math.signum(derivedAngle)) {
            return -derivedAngle;
        }
        return derivedAngle;
    }

    private static float normalizeAngle(float angle) {
        float normalized = angle;
        while (normalized <= -180.0f) {
            normalized += 360.0f;
        }
        while (normalized > 180.0f) {
            normalized -= 360.0f;
        }
        return normalized;
    }

    private static Vector3d resolveGravityVector(Object subLevel) {
        try {
            final Object level = invoke(subLevel, "getLevel");
            if (!(level instanceof Level minecraftLevel)) {
                return new Vector3d();
            }
            final Class<?> gravityClass = Class.forName("dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData");
            final Method getGravity = gravityClass.getMethod("getGravity", Level.class);
            final Object gravityVector = getGravity.invoke(null, minecraftLevel);
            return new Vector3d(
                    readCoordinate(gravityVector, "x"),
                    readCoordinate(gravityVector, "y"),
                    readCoordinate(gravityVector, "z")
            );
        } catch (ReflectiveOperationException ignored) {
            return new Vector3d();
        }
    }

    private static String normalizeContraptionForceKey(@Nullable Object forceGroup) {
        final String registryKey = resolveForceGroupRegistryKey(forceGroup).toLowerCase();
        final String displayName = resolveForceGroupName(forceGroup).toLowerCase();
        final String text = registryKey + " " + displayName;
        if (text.contains("propulsion") || text.contains("thrust")) {
            return "propulsion";
        }
        if (text.contains("gravity")) {
            return "gravity";
        }
        if (text.contains("drag")) {
            return "drag";
        }
        if (text.contains("levitation")) {
            return "levitation";
        }
        if (text.contains("balloon") && text.contains("lift")) {
            return "balloon_lift";
        }
        if (text.contains("magnetic")) {
            return "magnetic_force";
        }
        if (text.contains("lift")) {
            return "lift";
        }
        return "";
    }

    @Nullable
    private static Vector3d projectOntoPlane(@Nullable Vector3d vector, Vector3d planeNormal) {
        if (vector == null || planeNormal.lengthSquared() < 1.0E-8d) {
            return null;
        }
        final double alongNormal = vector.dot(planeNormal);
        final Vector3d projected = new Vector3d(vector).sub(new Vector3d(planeNormal).mul(alongNormal));
        return projected.lengthSquared() > 1.0E-8d ? projected : null;
    }

    private static String resolveForceGroupRegistryKey(@Nullable Object forceGroup) {
        if (forceGroup == null) {
            return "";
        }
        try {
            final Class<?> groupsClass = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroups");
            final Object registry = groupsClass.getField("REGISTRY").get(null);
            final Object key = invoke(registry, "getKey", forceGroup);
            return key != null ? key.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String resolveForceGroupName(@Nullable Object forceGroup) {
        final Object component = invoke(forceGroup, "name");
        final Object text = invoke(component, "getString");
        return text instanceof String string ? string : String.valueOf(component);
    }

    private static float resolveConfiguredGasVolume(Object sourceBE) {
        final Object behaviour = readField(sourceBE, "hotAirAmountBehaviour") != null
                ? readField(sourceBE, "hotAirAmountBehaviour")
                : readField(sourceBE, "steamAmountBehaviour");
        if (behaviour == null) {
            return 0.0f;
        }
        final Integer configuredValue = invokeInt(behaviour, "getValue");
        if (configuredValue != null) {
            return configuredValue.floatValue();
        }
        return readFloatField(behaviour, "value");
    }

    private static String resolveLiftingGasTypeName(@Nullable Object gasType) {
        final Object component = invoke(gasType, "getName");
        final Object value = invoke(component, "getString");
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return formatEnumLabel(gasType);
    }

    private static String formatEnumLabel(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        final Object name = invoke(value, "name");
        final String raw = name instanceof String text && !text.isBlank()
                ? text
                : value.toString();
        if (raw == null || raw.isBlank()) {
            return "";
        }
        final String[] words = raw.replace('-', '_').split("_+");
        final StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            final String lower = word.toLowerCase();
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.toString();
    }

    private static float vectorMagnitude(@Nullable Object vector) {
        if (vector == null) {
            return 0.0f;
        }
        final Vector3d resolved = readVector(vector, 1.0f);
        final double x = resolved.x;
        final double y = resolved.y;
        final double z = resolved.z;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static Vector3d readVector(@Nullable Object vector, float divisor) {
        if (vector == null) {
            return new Vector3d();
        }
        final double scale = divisor <= 1.0E-8f ? 1.0d : 1.0d / divisor;
        return new Vector3d(
                readCoordinate(vector, "x") * scale,
                readCoordinate(vector, "y") * scale,
                readCoordinate(vector, "z") * scale
        );
    }

    private static double readCoordinate(Object vector, String axis) {
        final Object value = invoke(vector, axis);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        final Object field = readField(vector, axis);
        return field instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static void putSample(Map<String, InstrumentSourceHelper.MetricSample> metrics, String metricId, String label, float value, MetricType type) {
        metrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId, label, value, type));
    }

    private record AttitudeSample(float pitch, float bank, float yaw, float forwardX, float forwardZ) {}

    private record ForceAxes(Vector3d up, @Nullable Vector3d forward, @Nullable Vector3d right, String mode, float confidence, String headingSource) {}

    private record ContraptionForceSnapshot(Map<String, Float> magnitudes, Map<String, Float> supportShares, float gravityMagnitude,
                                            float support, float thrust, float verticalMargin, float forwardMargin, float reverseForce,
                                            float downforce, float lateralForce, String axisMode, float axisConfidence, String headingSource,
                                            float classifiedPercent, float directionalPercent) {}

    private record ForceContribution(@Nullable Vector3d worldVector, float magnitude, boolean directionKnown) {}

    private static final class ForceFamilyAccumulator {
        private final Vector3d resolvedVector = new Vector3d();
        private float unresolvedMagnitude;

        private void add(ForceContribution contribution) {
            if (contribution.worldVector() != null) {
                this.resolvedVector.add(contribution.worldVector());
            } else {
                this.unresolvedMagnitude += contribution.magnitude();
            }
        }

        private Vector3d resolvedVector() {
            return this.resolvedVector;
        }

        private float magnitude() {
            return (float) this.resolvedVector.length() + this.unresolvedMagnitude;
        }

        private float unresolvedMagnitude() {
            return this.unresolvedMagnitude;
        }
    }

    private static final class CachedForwardAxis {
        private final Vector3d axis;
        private final String signature;

        private CachedForwardAxis(Vector3d axis, String signature) {
            this.axis = axis;
            this.signature = signature;
        }
    }

    private static float invokeFloat(Object target, String methodName, Object... args) {
        final Object value = invoke(target, methodName, args);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static double invokeDouble(Object target, String methodName, Object... args) {
        final Object value = invoke(target, methodName, args);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static boolean invokeBoolean(Object target, String methodName, Object... args) {
        final Object value = invoke(target, methodName, args);
        return value instanceof Boolean bool && bool;
    }

    private static double readDoubleField(Object target, String fieldName) {
        final Object value = readField(target, fieldName);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static boolean readBooleanField(Object target, String fieldName) {
        final Object value = readField(target, fieldName);
        return value instanceof Boolean bool && bool;
    }

    private static float readFloatField(Object target, String fieldName) {
        final Object value = readField(target, fieldName);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    @Nullable
    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                final Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        final Object value = readField(target, fieldName);
        return fieldType.isInstance(value) ? (T) value : null;
    }

    @Nullable
    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = findMethod(target.getClass(), methodName, args);
            if (method == null) {
                return null;
            }
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer invokeInt(Object target, String methodName, Object... args) {
        final Object value = invoke(target, methodName, args);
        return value instanceof Number number ? number.intValue() : null;
    }

    @Nullable
    private static Method findMethod(Class<?> type, String methodName, Object... args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            final Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; ++index) {
                if (!isCompatible(parameterTypes[index], args[index])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameterType, @Nullable Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        final Class<?> actualType = arg.getClass();
        return box(parameterType).isAssignableFrom(actualType);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }

    private static String resolveDockingSubLevel(@Nullable Object otherConnector, @Nullable UUID subLevelId) {
        if (otherConnector != null) {
            try {
                final Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
                final Object helper = sableClass.getField("HELPER").get(null);
                final Object subLevel = invoke(helper, "getContaining", otherConnector);
                final Object name = invoke(subLevel, "getName");
                if (name instanceof String text && !text.isBlank()) {
                    return text;
                }
                final Object uniqueId = invoke(subLevel, "getUniqueId");
                if (uniqueId instanceof UUID uuid) {
                    return shortUuid(uuid);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return subLevelId != null ? shortUuid(subLevelId) : "--";
    }

    private static String shortUuid(UUID uuid) {
        final String text = uuid.toString();
        return text.length() > 8 ? text.substring(0, 8) : text;
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String resolveBlockName(Block block) {
        if (block == null || block.defaultBlockState().isAir()) {
            return "--";
        }
        final String translatedName = block.getName().getString();
        if (translatedName != null && !translatedName.isBlank()) {
            return translatedName;
        }
        final var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null ? key.toString() : "--";
    }

    private static String sanitizeLiveText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private static String truncateLabelText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(1, maxChars - 2)) + "..";
    }

    private static int resolvePressedKeyCount(Object sourceBE) {
        final Object pressedKeys = invoke(sourceBE, "getPressedKeys");
        if (pressedKeys instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    private static int resolveActorItemCount(Object sourceBE) {
        final Object actorInventory = readField(sourceBE, "actorInventory");
        final Object storedItemCount = readField(actorInventory, "storedItemCount");
        return storedItemCount instanceof Number number ? number.intValue() : 0;
    }

    private static ItemStack extractContainedStack(@Nullable Object container) {
        if (container == null) {
            return ItemStack.EMPTY;
        }
        final Object directValue = invoke(container, "getItem", 0);
        if (directValue instanceof ItemStack stack && !stack.isEmpty()) {
            return stack;
        }
        final Object slot = readField(container, "slot");
        if (slot != null) {
            final Object stackValue = invoke(slot, "getStack");
            if (stackValue instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
            final ItemStack stack = readField(slot, "stack", ItemStack.class);
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        final Integer size = invokeInt(container, "getContainerSize");
        if (size != null) {
            for (int slotIndex = 0; slotIndex < size; ++slotIndex) {
                final Object value = invoke(container, "getItem", slotIndex);
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
