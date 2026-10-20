package com.createstats.flight.network;

import com.createstats.flight.FlightMode;
import com.createstats.flight.NoseAxis;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.workbench.FccDiagramProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public record FccGuiSnapshot(
    BlockPos fccPos,
    FlightMode mode,
    NoseAxis noseAxis,
    FccDiagramProfile diagramProfile,
    double targetAlt,
    double climbRate,
    double descendFloor,
    EnumSet<SensorRequirement> availableSensors,
    boolean sensorsWarmedUp,
    int boundRowCount,
    int resolvedBindingCount,
    int activeRowMask,
    int resolvedRowMask,
    int duplicateRowMask,
    String lastWarning,

    @Nullable BlockPos effectiveGimbalPos,
    @Nullable BlockPos effectiveAltitudePos,
    @Nullable BlockPos effectiveVelocityPos,
    @Nullable BlockPos effectiveNavPos,

    int manualSensorMask,

    int calStateOrdinal,
    int calProgressPercent,
    int calCurrentProp,

    String calResultSummary,
    int readinessFlags,
    int calibrationGainCount,
    int deviceCount,
    int groupCount,
    int controlCount,
    int ungroupedDeviceCount,
    List<String> overviewLines,
    List<String> deviceLines,
    List<String> groupLines,
    List<FccGuiDeviceEntry> deviceEntries,
    List<FccGuiGroupEntry> groupEntries,
    List<FccGuiControlEntry> controlEntries,
    List<FccGuiRouteEntry> routeEntries,
    int redstoneRowMask,
    int redstoneActiveRowMask,
    int redstonePoweredRowMask,
    int linkedControllerConfiguredMask,
    int linkedControllerInputMask,
    int linkedControllerSignalPack,
    int linkedControllerRampIntervalTicks,
    @Nullable BlockPos pathfinderComputerPos,
    String pathfinderRouteName,
    String pathfinderRouteStatus,
    boolean pathfinderRouteAccepted,
    float pathfinderRouteConfidence,
    int pathfinderRouteWaypointIndex,
    int pathfinderRouteWaypointCount,
    double pathfinderRouteDistance,
    int pathfinderRouteHazardFlags,
    List<BlockPos> pathfinderRoutePreview,
    boolean pathfinderSystemsEnabled,
    boolean battleBaselineReady,
    boolean battleBaselineStale,
    double battleBaselineMass,
    double battleCurrentMass,
    int battleIntegrityPercent,
    float navTableMaxSpeed,
    double airshipAutoRequestedSpeedCap,
    double airshipAutoReachableSpeedCap,
    double airshipAutoEffectiveSpeedCap,
    String airshipAutoReachabilityReason,
    float pathfinderLookaheadScale,

    boolean fePowered,

    float feBufferPct
) {
    private static final int MAX_SNAPSHOT_TEXT = 2048;
    private static final int MAX_ROUTE_PREVIEW_POINTS = 96;

    public FccGuiSnapshot {
        overviewLines = List.copyOf(overviewLines == null ? List.of() : overviewLines);
        deviceLines = List.copyOf(deviceLines == null ? List.of() : deviceLines);
        groupLines = List.copyOf(groupLines == null ? List.of() : groupLines);
        deviceEntries = List.copyOf(deviceEntries == null ? List.of() : deviceEntries);
        groupEntries = List.copyOf(groupEntries == null ? List.of() : groupEntries);
        controlEntries = List.copyOf(controlEntries == null ? List.of() : controlEntries);
        routeEntries = List.copyOf(routeEntries == null ? List.of() : routeEntries);
        lastWarning = lastWarning == null ? "" : lastWarning;
        pathfinderRouteName = pathfinderRouteName == null ? "" : pathfinderRouteName;
        pathfinderRouteStatus = pathfinderRouteStatus == null ? "" : pathfinderRouteStatus;
        pathfinderRouteWaypointCount = Math.max(0, pathfinderRouteWaypointCount);
        pathfinderRouteWaypointIndex = pathfinderRouteWaypointCount <= 0 ? 0
            : Math.max(0, Math.min(pathfinderRouteWaypointIndex, pathfinderRouteWaypointCount - 1));
        pathfinderRouteConfidence = Math.max(0f, Math.min(1f, pathfinderRouteConfidence));
        pathfinderRouteDistance = Math.max(0.0, pathfinderRouteDistance);
        pathfinderRoutePreview = copyRoutePreview(pathfinderRoutePreview);
        navTableMaxSpeed = Math.max(1.0f, Math.min(32.0f, navTableMaxSpeed));
        airshipAutoRequestedSpeedCap = sanitizeAirshipSpeed(airshipAutoRequestedSpeedCap);
        airshipAutoReachableSpeedCap = sanitizeAirshipSpeed(airshipAutoReachableSpeedCap);
        airshipAutoEffectiveSpeedCap = sanitizeAirshipSpeed(airshipAutoEffectiveSpeedCap);
        airshipAutoReachabilityReason = airshipAutoReachabilityReason == null ? "" : airshipAutoReachabilityReason;
        pathfinderLookaheadScale = Math.max(0.5f, Math.min(3.0f, pathfinderLookaheadScale));
        feBufferPct = Float.isFinite(feBufferPct) ? Math.max(0.0f, Math.min(100.0f, feBufferPct)) : 100.0f;
    }

    public static final int READY_NO_BINDINGS = 1;
    public static final int READY_BMATRIX_INVALID = 1 << 1;
    public static final int READY_ZERO_YAW_AUTHORITY = 1 << 2;
    public static final int READY_UNKNOWN_SPIN = 1 << 3;

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(fccPos);
        buf.writeEnum(mode);
        buf.writeEnum(noseAxis);
        buf.writeEnum(diagramProfile);
        buf.writeDouble(targetAlt);
        buf.writeDouble(climbRate);
        buf.writeDouble(descendFloor);
        int mask = 0;
        for (SensorRequirement r : availableSensors) mask |= 1 << r.ordinal();
        buf.writeVarInt(mask);
        buf.writeBoolean(sensorsWarmedUp);
        buf.writeVarInt(boundRowCount);
        buf.writeVarInt(resolvedBindingCount);
        buf.writeVarInt(activeRowMask);
        buf.writeVarInt(resolvedRowMask);
        buf.writeVarInt(duplicateRowMask);
        buf.writeUtf(lastWarning, MAX_SNAPSHOT_TEXT);
        writeNullablePos(buf, effectiveGimbalPos);
        writeNullablePos(buf, effectiveAltitudePos);
        writeNullablePos(buf, effectiveVelocityPos);
        writeNullablePos(buf, effectiveNavPos);
        buf.writeVarInt(manualSensorMask);
        buf.writeVarInt(calStateOrdinal);
        buf.writeVarInt(calProgressPercent);
        buf.writeVarInt(calCurrentProp);
        buf.writeUtf(calResultSummary == null ? "" : calResultSummary, MAX_SNAPSHOT_TEXT);
        buf.writeVarInt(readinessFlags);
        buf.writeVarInt(calibrationGainCount);
        buf.writeVarInt(deviceCount);
        buf.writeVarInt(groupCount);
        buf.writeVarInt(controlCount);
        buf.writeVarInt(ungroupedDeviceCount);
        writeStringList(buf, overviewLines);
        writeStringList(buf, deviceLines);
        writeStringList(buf, groupLines);
        buf.writeVarInt(deviceEntries.size());
        for (FccGuiDeviceEntry entry : deviceEntries) entry.encode(buf);
        buf.writeVarInt(groupEntries.size());
        for (FccGuiGroupEntry entry : groupEntries) entry.encode(buf);
        buf.writeVarInt(controlEntries.size());
        for (FccGuiControlEntry entry : controlEntries) entry.encode(buf);
        buf.writeVarInt(routeEntries.size());
        for (FccGuiRouteEntry entry : routeEntries) entry.encode(buf);
        buf.writeVarInt(redstoneRowMask);
        buf.writeVarInt(redstoneActiveRowMask);
        buf.writeVarInt(redstonePoweredRowMask);
        buf.writeVarInt(linkedControllerConfiguredMask);
        buf.writeVarInt(linkedControllerInputMask);
        buf.writeVarInt(linkedControllerSignalPack);
        buf.writeVarInt(linkedControllerRampIntervalTicks);
        writeNullablePos(buf, pathfinderComputerPos);
        buf.writeUtf(pathfinderRouteName == null ? "" : pathfinderRouteName, MAX_SNAPSHOT_TEXT);
        buf.writeUtf(pathfinderRouteStatus == null ? "" : pathfinderRouteStatus, MAX_SNAPSHOT_TEXT);
        buf.writeBoolean(pathfinderRouteAccepted);
        buf.writeFloat(pathfinderRouteConfidence);
        buf.writeVarInt(pathfinderRouteWaypointIndex);
        buf.writeVarInt(pathfinderRouteWaypointCount);
        buf.writeDouble(pathfinderRouteDistance);
        buf.writeVarInt(pathfinderRouteHazardFlags);
        writeBlockPosList(buf, pathfinderRoutePreview);
        buf.writeBoolean(pathfinderSystemsEnabled);
        buf.writeBoolean(battleBaselineReady);
        buf.writeBoolean(battleBaselineStale);
        buf.writeDouble(battleBaselineMass);
        buf.writeDouble(battleCurrentMass);
        buf.writeVarInt(battleIntegrityPercent);
        buf.writeFloat(navTableMaxSpeed);
        buf.writeDouble(airshipAutoRequestedSpeedCap);
        buf.writeDouble(airshipAutoReachableSpeedCap);
        buf.writeDouble(airshipAutoEffectiveSpeedCap);
        buf.writeUtf(airshipAutoReachabilityReason, MAX_SNAPSHOT_TEXT);
        buf.writeFloat(pathfinderLookaheadScale);
        buf.writeBoolean(fePowered);
        buf.writeFloat(feBufferPct);
    }

    public static FccGuiSnapshot decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        FlightMode mode = buf.readEnum(FlightMode.class);
        NoseAxis nose = buf.readEnum(NoseAxis.class);
        FccDiagramProfile diagramProfile = buf.readEnum(FccDiagramProfile.class);
        double tAlt = buf.readDouble();
        double cRate = buf.readDouble();
        double dFloor = buf.readDouble();
        int mask = buf.readVarInt();
        EnumSet<SensorRequirement> avail = EnumSet.noneOf(SensorRequirement.class);
        for (SensorRequirement r : SensorRequirement.values()) {
            if ((mask & (1 << r.ordinal())) != 0) avail.add(r);
        }
        boolean warm = buf.readBoolean();
        int rows = buf.readVarInt();
        int resolved = buf.readVarInt();
        int activeMask = buf.readVarInt();
        int resolvedMask = buf.readVarInt();
        int duplicateMask = buf.readVarInt();
        String warn = buf.readUtf(MAX_SNAPSHOT_TEXT);
        BlockPos gimbal  = readNullablePos(buf);
        BlockPos alt     = readNullablePos(buf);
        BlockPos vel     = readNullablePos(buf);
        BlockPos nav     = readNullablePos(buf);
        int manualMask   = buf.readVarInt();
        int calState     = buf.readVarInt();
        int calPct       = buf.readVarInt();
        int calProp      = buf.readVarInt();
        String calSummary = buf.readUtf(MAX_SNAPSHOT_TEXT);
        int readyFlags = buf.readVarInt();
        int calGains = buf.readVarInt();
        int deviceCount = buf.readVarInt();
        int groupCount = buf.readVarInt();
        int controlCount = buf.readVarInt();
        int ungroupedDeviceCount = buf.readVarInt();
        List<String> overviewLines = readStringList(buf);
        List<String> deviceLines = readStringList(buf);
        List<String> groupLines = readStringList(buf);
        int deviceEntryCount = buf.readVarInt();
        List<FccGuiDeviceEntry> deviceEntries = new ArrayList<>(deviceEntryCount);
        for (int i = 0; i < deviceEntryCount; i++) deviceEntries.add(FccGuiDeviceEntry.decode(buf));
        int groupEntryCount = buf.readVarInt();
        List<FccGuiGroupEntry> groupEntries = new ArrayList<>(groupEntryCount);
        for (int i = 0; i < groupEntryCount; i++) groupEntries.add(FccGuiGroupEntry.decode(buf));
        int controlEntryCount = buf.readVarInt();
        List<FccGuiControlEntry> controlEntries = new ArrayList<>(controlEntryCount);
        for (int i = 0; i < controlEntryCount; i++) controlEntries.add(FccGuiControlEntry.decode(buf));
        int routeEntryCount = buf.readVarInt();
        List<FccGuiRouteEntry> routeEntries = new ArrayList<>(routeEntryCount);
        for (int i = 0; i < routeEntryCount; i++) routeEntries.add(FccGuiRouteEntry.decode(buf));
        int redstoneRowMask = buf.readVarInt();
        int redstoneActiveRowMask = buf.readVarInt();
        int redstonePoweredRowMask = buf.readVarInt();
        int linkedControllerConfiguredMask = buf.readVarInt();
        int linkedControllerInputMask = buf.readVarInt();
        int linkedControllerSignalPack = buf.readVarInt();
        int linkedControllerRampIntervalTicks = buf.readVarInt();
        BlockPos pathfinderComputerPos = readNullablePos(buf);
        String pathfinderRouteName = buf.readUtf(MAX_SNAPSHOT_TEXT);
        String pathfinderRouteStatus = buf.readUtf(MAX_SNAPSHOT_TEXT);
        boolean pathfinderRouteAccepted = buf.readBoolean();
        float pathfinderRouteConfidence = buf.readFloat();
        int pathfinderRouteWaypointIndex = buf.readVarInt();
        int pathfinderRouteWaypointCount = buf.readVarInt();
        double pathfinderRouteDistance = buf.readDouble();
        int pathfinderRouteHazardFlags = buf.readVarInt();
        List<BlockPos> pathfinderRoutePreview = readBlockPosList(buf);
        boolean pathfinderSystemsEnabled = buf.readBoolean();
        boolean battleBaselineReady = buf.readBoolean();
        boolean battleBaselineStale = buf.readBoolean();
        double battleBaselineMass = buf.readDouble();
        double battleCurrentMass = buf.readDouble();
        int battleIntegrityPercent = buf.readVarInt();
        float navTableMaxSpeed = buf.readFloat();
        double airshipAutoRequestedSpeedCap = buf.readDouble();
        double airshipAutoReachableSpeedCap = buf.readDouble();
        double airshipAutoEffectiveSpeedCap = buf.readDouble();
        String airshipAutoReachabilityReason = buf.readUtf(MAX_SNAPSHOT_TEXT);
        float pathfinderLookaheadScale = buf.readFloat();
        boolean fePowered = buf.readBoolean();
        float feBufferPct = buf.readFloat();
        return new FccGuiSnapshot(pos, mode, nose, diagramProfile, tAlt, cRate, dFloor, avail, warm,
            rows, resolved, activeMask, resolvedMask, duplicateMask, warn, gimbal, alt, vel, nav, manualMask,
            calState, calPct, calProp, calSummary, readyFlags, calGains,
            deviceCount, groupCount, controlCount, ungroupedDeviceCount,
            overviewLines, deviceLines, groupLines, deviceEntries, groupEntries, controlEntries, routeEntries,
            redstoneRowMask, redstoneActiveRowMask, redstonePoweredRowMask,
            linkedControllerConfiguredMask, linkedControllerInputMask, linkedControllerSignalPack,
            linkedControllerRampIntervalTicks, pathfinderComputerPos, pathfinderRouteName, pathfinderRouteStatus,
                pathfinderRouteAccepted, pathfinderRouteConfidence, pathfinderRouteWaypointIndex, pathfinderRouteWaypointCount,
            pathfinderRouteDistance, pathfinderRouteHazardFlags, pathfinderRoutePreview, pathfinderSystemsEnabled,
            battleBaselineReady, battleBaselineStale, battleBaselineMass, battleCurrentMass, battleIntegrityPercent,
            navTableMaxSpeed, airshipAutoRequestedSpeedCap, airshipAutoReachableSpeedCap,
            airshipAutoEffectiveSpeedCap, airshipAutoReachabilityReason, pathfinderLookaheadScale,
            fePowered, feBufferPct);
    }

    private static double sanitizeAirshipSpeed(double value) {
        return Double.isFinite(value) && value > 0.0 ? Math.max(1.0, Math.min(32.0, value)) : Double.NaN;
    }

    private static List<BlockPos> copyRoutePreview(List<BlockPos> preview) {
        if (preview == null || preview.isEmpty()) return List.of();
        int limit = Math.min(MAX_ROUTE_PREVIEW_POINTS, preview.size());
        List<BlockPos> copy = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            BlockPos pos = preview.get(i);
            if (pos != null) copy.add(pos.immutable());
        }
        return List.copyOf(copy);
    }

    private static void writeNullablePos(RegistryFriendlyByteBuf buf, @Nullable BlockPos p) {
        buf.writeBoolean(p != null);
        if (p != null) buf.writeBlockPos(p);
    }

    @Nullable
    private static BlockPos readNullablePos(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> lines) {
        List<String> safeLines = lines == null ? List.of() : lines;
        buf.writeVarInt(safeLines.size());
        for (String line : safeLines) buf.writeUtf(line == null ? "" : line, MAX_SNAPSHOT_TEXT);
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) lines.add(buf.readUtf(MAX_SNAPSHOT_TEXT));
        return lines;
    }

    private static void writeBlockPosList(RegistryFriendlyByteBuf buf, List<BlockPos> positions) {
        List<BlockPos> safePositions = copyRoutePreview(positions);
        buf.writeVarInt(safePositions.size());
        for (BlockPos pos : safePositions) buf.writeBlockPos(pos);
    }

    private static List<BlockPos> readBlockPosList(RegistryFriendlyByteBuf buf) {
        int rawSize = Math.max(0, buf.readVarInt());
        int size = Math.min(MAX_ROUTE_PREVIEW_POINTS, rawSize);
        List<BlockPos> positions = new ArrayList<>(size);
        for (int i = 0; i < rawSize; i++) {
            BlockPos pos = buf.readBlockPos();
            if (i < size) positions.add(pos);
        }
        return positions;
    }
}
