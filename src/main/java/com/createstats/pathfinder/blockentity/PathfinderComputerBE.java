package com.createstats.pathfinder.blockentity;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import com.createstats.pathfinder.menu.PathfinderComputerMenu;
import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.HazardFlags;
import com.createstats.pathfinder.model.Passability;
import com.createstats.pathfinder.model.PlannerJobStage;
import com.createstats.pathfinder.model.RouteCandidate;
import com.createstats.pathfinder.model.RouteGuidance;
import com.createstats.pathfinder.planner.PathRoutePlanner;
import com.createstats.pathfinder.settings.PathScanSettings;
import com.createstats.pathfinder.util.PathPayloadDecoder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PathfinderComputerBE extends BlockEntity implements MenuProvider, IHaveGoggleInformation, com.createstats.power.IFeConsumer {
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_PATHFINDER_COMPUTER));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }
    public static final int SCHEMA_VERSION = 3;
    private static final int STALE_AFTER_TICKS = 20 * 60;
    private static final int LINK_REFRESH_INTERVAL_TICKS = 20;
    private static final int HEARTBEAT_SYNC_INTERVAL_TICKS = 20 * 10;
    private static final int TRANSIENT_NO_ROUTE_GRACE_TICKS = 20 * 8;
    private static final int PLANNER_MIN_INTERVAL_TICKS = 60;
    private static final double ARRIVED_NAV_TARGET_REPLAN_RADIUS_BLOCKS = 4.0;
    private static final int ROUTE_SCAN_MIN_SIDE_CHUNKS = 3;
    private static final int ROUTE_MONITOR_FORWARD_CHUNKS = 8;
    private static final double PROVISIONAL_ROUTE_RECHECK_DISTANCE_BLOCKS = 16.0 * 3.0;
    private static final long PROVISIONAL_ROUTE_RECHECK_INTERVAL_TICKS = 20L * 8L;
    private static final int[] ROUTE_SCAN_SIDE_STEPS = { 3 };
    public static final float MIN_GUIDANCE_SPEED = 1.0f;
    public static final float MAX_GUIDANCE_SPEED = 32.0f;
    public static final float MIN_CORNER_CLEARANCE_EXTRA_BLOCKS = 0.0f;
    public static final float MAX_CORNER_CLEARANCE_EXTRA_BLOCKS = 4.0f;
    public static final float CORNER_CLEARANCE_STEP_BLOCKS = 0.5f;
    public static final int CORNER_CLEARANCE_STEPS = Math.round(
        (MAX_CORNER_CLEARANCE_EXTRA_BLOCKS - MIN_CORNER_CLEARANCE_EXTRA_BLOCKS) / CORNER_CLEARANCE_STEP_BLOCKS);

    private UUID computerId;
    @Nullable
    private BlockPos linkedScannerPos;
    @Nullable
    private UUID linkedScannerId;
    private long linkedScannerLastSeenTick;
    private long linkedScannerScanTick;
    private int linkedExpectedChunks;
    private int linkedLoadedChunks;
    private int linkedUnknownChunks;
    private int linkedPassableCells;
    private int linkedMarginalCells;
    private int linkedBlockedCells;
    private int linkedQueueDepth;
    private int linkedLastProcessedChunks;
    @Nullable
    private RouteGuidance activeGuidance;
    private String plannerStatus = "Waiting for scanner";
    private int plannerInspectedCells;
    private int plannerUsableCells;
    private int plannerBlockedCells;
    private int plannerHazardFlags;
    private PlannerJobStage plannerStage = PlannerJobStage.WAITING_FOR_SCAN;
    private int plannerProgressPercent;
    private int plannerExpandedStates;
    private long plannerLastComputedTick;
    private boolean plannerJobActive;
    private boolean plannerSettingsDirty;
    private int plannerJobPhase;
    @Nullable
    private Vec3 plannerJobNavTarget;
    private double plannerJobHardClearance;
    private double plannerJobPreferredClearance;
    private float plannerJobMaxSpeed;
    @Nullable
    private PathRoutePlanner.PlannedRoute plannerJobPlannedRoute;
    private long routeGeneration;
    private double routeBufferPreservedDistance;
    private double routeBufferFreshDistance;
    private double routeBufferTotalDistance;
    private boolean routeBufferMerged;
    private float maxGuidanceSpeed = 8.0f;
    private float cornerClearanceExtraBlocks = MIN_CORNER_CLEARANCE_EXTRA_BLOCKS;
    private long nextLinkRefreshTick;
    private long lastHeartbeatSyncTick;
    @Nullable
    private BlockPos linkedFccPos;
    private long linkedFccLastSeenTick;
    private String linkedFccStatus = "No VCC linked";
    private int linkedFccRouteWaypointIndex;
    private int linkedFccRouteWaypointCount;
    private String linkedFccFrameDebug = "frame=scanner";
    private float linkedFccHeadingDeg = Float.NaN;
    private float linkedFccSpeedMs = Float.NaN;
    private boolean linkedFccHasFrontWheels = false;
    private long noRouteSinceTick = -1L;
    private long lastDebugLogTick = Long.MIN_VALUE;
    private long lastProvisionalRouteRecheckTick = Long.MIN_VALUE;
    private boolean remotePaused;
    private int routeScanSideChunks = ROUTE_SCAN_MIN_SIDE_CHUNKS;

    public PathfinderComputerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.computerId = UUID.randomUUID();
    }

    public UUID computerId() {
        return computerId;
    }

    @Nullable
    public BlockPos linkedScannerPos() {
        return linkedScannerPos;
    }

    @Nullable
    public UUID linkedScannerId() {
        return linkedScannerId;
    }

    public boolean hasLinkedScanner() {
        return linkedScannerPos != null && linkedScannerId != null;
    }

    public long linkedScannerLastSeenTick() {
        return linkedScannerLastSeenTick;
    }

    public long linkedScannerScanTick() {
        return linkedScannerScanTick;
    }

    public int linkedExpectedChunks() {
        return linkedExpectedChunks;
    }

    public int linkedLoadedChunks() {
        return linkedLoadedChunks;
    }

    public int linkedUnknownChunks() {
        return linkedUnknownChunks;
    }

    public int linkedPassableCells() {
        return linkedPassableCells;
    }

    public int linkedMarginalCells() {
        return linkedMarginalCells;
    }

    public int linkedBlockedCells() {
        return linkedBlockedCells;
    }

    public int linkedQueueDepth() {
        return linkedQueueDepth;
    }

    public int linkedLastProcessedChunks() {
        return linkedLastProcessedChunks;
    }

    @Nullable
    public RouteGuidance activeGuidance() {
        return activeGuidance;
    }

    @Nullable
    public RouteCandidate activeCandidate() {
        return activeGuidance == null ? null : activeGuidance.toCandidate();
    }

    public String plannerStatus() {
        return plannerStatus;
    }

    public int plannerInspectedCells() {
        return plannerInspectedCells;
    }

    public int plannerUsableCells() {
        return plannerUsableCells;
    }

    public int plannerBlockedCells() {
        return plannerBlockedCells;
    }

    public int plannerHazardFlags() {
        return plannerHazardFlags;
    }

    public PlannerJobStage plannerStage() {
        return plannerStage;
    }

    public int plannerProgressPercent() {
        return plannerProgressPercent;
    }

    public int plannerExpandedStates() {
        return plannerExpandedStates;
    }

    public long plannerLastComputedTick() {
        return plannerLastComputedTick;
    }

    public long routeGeneration() {
        return routeGeneration;
    }

    public double routeBufferPreservedDistance() {
        return routeBufferPreservedDistance;
    }

    public double routeBufferFreshDistance() {
        return routeBufferFreshDistance;
    }

    public double routeBufferTotalDistance() {
        return routeBufferTotalDistance;
    }

    public boolean routeBufferMerged() {
        return routeBufferMerged;
    }

    public float maxGuidanceSpeed() {
        return maxGuidanceSpeed;
    }

    public void adjustMaxGuidanceSpeed(float delta) {
        float next = clamp(this.maxGuidanceSpeed + delta, MIN_GUIDANCE_SPEED, MAX_GUIDANCE_SPEED);
        if (Float.compare(next, this.maxGuidanceSpeed) == 0) return;
        this.maxGuidanceSpeed = next;
        this.plannerSettingsDirty = true;
        this.plannerLastComputedTick = 0L;
        this.refreshLinkedScanner();
        this.markDirtyAndSync();
    }

    public float cornerClearanceExtraBlocks() {
        return cornerClearanceExtraBlocks;
    }

    public int cornerClearanceStep() {
        return cornerClearanceStepFor(this.cornerClearanceExtraBlocks);
    }

    public void setCornerClearanceStep(int step) {
        float next = cornerClearanceExtraForStep(step);
        if (Float.compare(next, this.cornerClearanceExtraBlocks) == 0) return;
        this.cornerClearanceExtraBlocks = next;
        this.plannerSettingsDirty = true;
        this.plannerLastComputedTick = 0L;
        this.refreshLinkedScanner();
        this.markDirtyAndSync();
    }

    public static float cornerClearanceExtraForStep(int step) {
        int safeStep = Math.max(0, Math.min(CORNER_CLEARANCE_STEPS, step));
        return clamp(MIN_CORNER_CLEARANCE_EXTRA_BLOCKS + safeStep * CORNER_CLEARANCE_STEP_BLOCKS,
            MIN_CORNER_CLEARANCE_EXTRA_BLOCKS, MAX_CORNER_CLEARANCE_EXTRA_BLOCKS);
    }

    public static int cornerClearanceStepFor(float value) {
        float clamped = clamp(value, MIN_CORNER_CLEARANCE_EXTRA_BLOCKS, MAX_CORNER_CLEARANCE_EXTRA_BLOCKS);
        return Math.max(0, Math.min(CORNER_CLEARANCE_STEPS,
            Math.round((clamped - MIN_CORNER_CLEARANCE_EXTRA_BLOCKS) / CORNER_CLEARANCE_STEP_BLOCKS)));
    }

    @Nullable
    public BlockPos linkedFccPos() {
        return linkedFccPos;
    }

    public String linkedFccStatus() {
        return linkedFccStatus;
    }

    public int linkedFccRouteWaypointIndex() {
        return linkedFccRouteWaypointIndex;
    }

    public int linkedFccRouteWaypointCount() {
        return linkedFccRouteWaypointCount;
    }

    public boolean hasLinkedFcc() {
        return linkedFccPos != null && linkedFccLastSeenTick > 0;
    }

    public String debugLine() {
        String scanner = linkedScannerPos == null ? "none" : linkedScannerPos.toShortString();
        String route = "none";
        if (activeGuidance != null) {
            Vec3 first = activeGuidance.waypoints().isEmpty() ? null : activeGuidance.waypoints().get(0);
            Vec3 last = activeGuidance.waypoints().isEmpty() ? null : activeGuidance.waypoints().get(activeGuidance.waypoints().size() - 1);
            route = String.format(java.util.Locale.ROOT, "%s id=%s conf=%.2f wp=%d dist=%.1f first=%s last=%s provisional=%s hazards=%d",
                activeGuidance.routeName(), shortId(activeGuidance.routeId()), activeGuidance.confidence(),
                activeGuidance.waypoints().size(), activeGuidance.estimatedDistance(), formatVec(first), formatVec(last),
                activeGuidance.nextReplanTick() > 0L, activeGuidance.hazardFlags());
        }
        return String.format(java.util.Locale.ROOT,
            "scanner=%s paused=%s stale=%s scanTick=%d coverage=%d/%d unknown=%d queue=%d stage=%s %d%% expanded=%d status=%s speed=%.1f corner=+%.1f scanSide=%d gen=%d buffer=%.0f+%.0f/%.0f merged=%s route=%s fcc=%s %s wp=%d/%d",
            scanner, remotePaused, isLinkedScannerStale(), linkedScannerScanTick, linkedLoadedChunks, Math.max(1, linkedExpectedChunks),
            linkedUnknownChunks, linkedQueueDepth, plannerStage.label(), plannerProgressPercent, plannerExpandedStates,
            plannerStatus, maxGuidanceSpeed, cornerClearanceExtraBlocks, routeScanSideChunks, routeGeneration,
            routeBufferPreservedDistance, routeBufferFreshDistance, routeBufferTotalDistance,
            routeBufferMerged, route, linkedFccStatus, linkedFccFrameDebug,
            linkedFccRouteWaypointIndex + 1, Math.max(1, linkedFccRouteWaypointCount));
    }

    public boolean isLinkedScannerStale() {
        Level level = this.level;
        return this.hasLinkedScanner() && level != null
            && (this.linkedScannerLastSeenTick <= 0 || level.getGameTime() - this.linkedScannerLastSeenTick > STALE_AFTER_TICKS);
    }

    public void tick() {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) return;

        final boolean fePowered = this.feConsumer.serviceTick(this.plannerJobActive);
        com.createstats.power.FePowerFx.handleTransition(level, this.worldPosition, this.feConsumer);
        if (!fePowered) return;
        if (!this.hasLinkedScanner()) return;
        long gameTime = serverLevel.getGameTime();
        if (gameTime < this.nextLinkRefreshTick) return;
        this.nextLinkRefreshTick = gameTime + LINK_REFRESH_INTERVAL_TICKS;
        if (!this.refreshLinkedScanner()) {
            boolean changed = this.linkedScannerPos != null || this.linkedScannerId != null;
            this.linkedScannerLastSeenTick = 0L;
            if (changed || this.isLinkedScannerStale()) {
                this.markDirtyAndSync();
            }
        }
    }

    public void clearLinkedScanner() {
        this.linkedScannerPos = null;
        this.linkedScannerId = null;
        this.linkedScannerLastSeenTick = 0L;
        this.linkedScannerScanTick = 0L;
        this.linkedExpectedChunks = 0;
        this.linkedLoadedChunks = 0;
        this.linkedUnknownChunks = 0;
        this.linkedPassableCells = 0;
        this.linkedMarginalCells = 0;
        this.linkedBlockedCells = 0;
        this.linkedQueueDepth = 0;
        this.linkedLastProcessedChunks = 0;
        this.activeGuidance = null;
        this.plannerStatus = "Waiting for scanner";
        this.plannerInspectedCells = 0;
        this.plannerUsableCells = 0;
        this.plannerBlockedCells = 0;
        this.plannerHazardFlags = 0;
        this.plannerStage = PlannerJobStage.WAITING_FOR_SCAN;
        this.plannerProgressPercent = 0;
        this.plannerExpandedStates = 0;
        this.plannerLastComputedTick = 0L;
        this.plannerJobActive = false;
        this.plannerSettingsDirty = false;
        this.plannerJobPhase = 0;
        this.plannerJobNavTarget = null;
        this.plannerJobHardClearance = 0.0;
        this.plannerJobPreferredClearance = 0.0;
        this.plannerJobMaxSpeed = this.maxGuidanceSpeed;
        this.plannerJobPlannedRoute = null;
        this.routeGeneration = 0L;
        this.routeBufferPreservedDistance = 0.0;
        this.routeBufferFreshDistance = 0.0;
        this.routeBufferTotalDistance = 0.0;
        this.routeBufferMerged = false;
        this.linkedFccRouteWaypointIndex = 0;
        this.linkedFccRouteWaypointCount = 0;
        this.linkedFccFrameDebug = "frame=scanner";
        this.linkedFccHeadingDeg = Float.NaN;
        this.linkedFccSpeedMs = Float.NaN;
        this.linkedFccHasFrontWheels = false;
        this.noRouteSinceTick = -1L;
        this.lastProvisionalRouteRecheckTick = Long.MIN_VALUE;
        this.routeScanSideChunks = ROUTE_SCAN_MIN_SIDE_CHUNKS;
        this.markDirtyAndSync();
    }

    public void ingestScannerSummary(PathScannerBE scanner, BlockPos scannerPos) {
        if (scanner == null || scannerPos == null) return;
        if (this.remotePaused) {
            scanner.setRemotePaused(true);
            return;
        }
        boolean wasStale = this.isLinkedScannerStale();
        UUID scannerUuid = scanner.scannerId();
        boolean changed = !scannerPos.equals(this.linkedScannerPos)
            || !Objects.equals(scannerUuid, this.linkedScannerId)
            || this.linkedScannerScanTick != scanner.lastScanTick()
            || this.linkedExpectedChunks != scanner.expectedChunkCount()
            || this.linkedLoadedChunks != scanner.loadedChunkCount()
            || this.linkedUnknownChunks != scanner.unknownChunkCount()
            || this.linkedPassableCells != scanner.totalPassableCells()
            || this.linkedMarginalCells != scanner.totalMarginalCells()
            || this.linkedBlockedCells != scanner.totalBlockedCells()
            || this.linkedQueueDepth != scanner.queueDepth()
            || this.linkedLastProcessedChunks != scanner.lastProcessedChunks();
        boolean scannerChanged = changed;
        this.linkedScannerPos = scannerPos.immutable();
        this.linkedScannerId = scannerUuid;
        Level level = this.level;
        long seenTick = level == null ? scanner.lastScanTick() : level.getGameTime();
        boolean heartbeatSyncDue = level != null && seenTick - this.lastHeartbeatSyncTick >= HEARTBEAT_SYNC_INTERVAL_TICKS;
        this.linkedScannerLastSeenTick = seenTick;
        this.linkedScannerScanTick = scanner.lastScanTick();
        this.linkedExpectedChunks = scanner.expectedChunkCount();
        this.linkedLoadedChunks = scanner.loadedChunkCount();
        this.linkedUnknownChunks = scanner.unknownChunkCount();
        this.linkedPassableCells = scanner.totalPassableCells();
        this.linkedMarginalCells = scanner.totalMarginalCells();
        this.linkedBlockedCells = scanner.totalBlockedCells();
        this.linkedQueueDepth = scanner.queueDepth();
        this.linkedLastProcessedChunks = scanner.lastProcessedChunks();
        boolean plannerDue = this.plannerLastComputedTick <= 0L
            || seenTick - this.plannerLastComputedTick >= PLANNER_MIN_INTERVAL_TICKS
            || !isUsableGuidance(this.activeGuidance);
        FlightControlComputerBE linkedFcc = this.resolveLinkedFcc();
        if (linkedFcc != null) {
            this.linkedFccHeadingDeg = linkedFcc.pathfinderPlanningYawDeg();
            double spd = linkedFcc.pathfinderVehicleSpeedMs();
            this.linkedFccSpeedMs = Double.isNaN(spd) ? Float.NaN : (float) spd;
            this.linkedFccHasFrontWheels = linkedFcc.pathfinderHasFrontWheelZones();
        } else {
            this.linkedFccHeadingDeg = Float.NaN;
            this.linkedFccSpeedMs = Float.NaN;
            this.linkedFccHasFrontWheels = false;
        }
        PlanGate planGate = this.planGateFor(linkedFcc);
        if (!planGate.allowed()) {
            changed |= this.holdForPlanGate(planGate.status());
            if (level instanceof ServerLevel serverLevel) {
                this.logDebug(serverLevel, scanner, false, changed || wasStale || heartbeatSyncDue);
            }
            if (changed || wasStale || heartbeatSyncDue) {
                this.markDirtyAndSync();
            } else {
                this.setChanged();
            }
            return;
        }
        boolean provisionalRouteRecheckDue = this.shouldRequestProvisionalRouteRecheck(linkedFcc, seenTick);
        if (linkedFcc != null && level instanceof ServerLevel) {
            boolean routeMonitor = isRouteMonitorMode();
            BlockPos planningOrigin = linkedFcc.pathfinderPlanningOrigin();
            BlockPos origin = routeMonitor ? activeRouteMonitorOrigin(planningOrigin) : planningOrigin;
            float planningYaw = linkedFcc.pathfinderPlanningYawDeg();
            float scanYaw = planningScanYaw(linkedFcc, origin, planningYaw, routeMonitor);
            VehicleScanRequest scanRequest = vehicleScanRequest(scanner, linkedFcc, origin, scanYaw, routeMonitor);
            scanner.setVehicleScanFrame(origin, scanYaw, seenTick, scanRequest.forwardChunks(), scanRequest.sideChunks());
            String frameDebug = String.format(java.util.Locale.ROOT, "frame=fcc %s scanYaw=%.1f routeMonitor=%s",
                linkedFcc.pathfinderPlanningDebugLine(), scanYaw, routeMonitor);
            changed |= !Objects.equals(this.linkedFccFrameDebug, frameDebug);
            this.linkedFccFrameDebug = frameDebug;
        } else if (!Objects.equals(this.linkedFccFrameDebug, "frame=scanner")) {
            this.linkedFccFrameDebug = "frame=scanner";
            changed = true;
        }
        if (!this.isPlannerScanReady(scanner)) {
            changed |= this.holdForPlanGate("Waiting for loaded 5x5 scan");
            if (level instanceof ServerLevel serverLevel) {
                this.logDebug(serverLevel, scanner, false, changed || wasStale || heartbeatSyncDue);
            }
            if (changed || wasStale || heartbeatSyncDue) {
                this.markDirtyAndSync();
            } else {
                this.setChanged();
            }
            return;
        }
        boolean activeGuidanceUsable = isUsableGuidance(this.activeGuidance);
        boolean reactiveRouteDue = this.shouldRequestReactiveReplan(scanner);
        boolean rollingRouteDue = this.shouldRequestRollingReplan(linkedFcc);
        boolean scannerChangeRequiresPlan = scannerChanged && (!activeGuidanceUsable || reactiveRouteDue);
        boolean shouldReplan = plannerDue && (this.plannerSettingsDirty || scannerChangeRequiresPlan
            || !activeGuidanceUsable || reactiveRouteDue || rollingRouteDue || provisionalRouteRecheckDue);
        if (shouldReplan && !this.plannerJobActive) {
            if (provisionalRouteRecheckDue) this.lastProvisionalRouteRecheckTick = seenTick;
            changed |= this.startPlannerJob(scanner, linkedFcc, seenTick);
        } else if (this.plannerJobActive) {
            changed |= this.advancePlannerJob(scanner, scannerPos, seenTick);
        }
        if (level instanceof ServerLevel serverLevel) {
            this.logDebug(serverLevel, scanner, shouldReplan, changed || wasStale || heartbeatSyncDue);
        }
        if (changed || wasStale || heartbeatSyncDue) {
            this.markDirtyAndSync();
        } else {
            this.setChanged();
        }
    }

    private boolean startPlannerJob(PathScannerBE scanner, @Nullable FlightControlComputerBE linkedFcc, long now) {
        if (this.remotePaused) return false;
        PlanGate gate = this.planGateFor(linkedFcc);
        if (!gate.allowed()) return this.holdForPlanGate(gate.status());
        this.plannerJobActive = true;
        this.plannerJobPhase = 1;
        this.plannerJobNavTarget = linkedFcc != null ? linkedFcc.pathfinderNavTarget() : null;
        double baseClearance = linkedFcc != null ? linkedFcc.pathfinderVehicleClearanceRadius() : 0.0;
        this.plannerJobHardClearance = baseClearance;
        this.plannerJobPreferredClearance = baseClearance + this.cornerClearanceExtraBlocks;
        this.plannerJobMaxSpeed = this.maxGuidanceSpeed;
        this.plannerJobPlannedRoute = null;
        this.plannerSettingsDirty = false;
        this.plannerLastComputedTick = now;
        String status = String.format(java.util.Locale.ROOT, "A* 1/4 Detection: scan %d/%d chunks",
            this.linkedLoadedChunks, Math.max(1, this.linkedExpectedChunks));
        return this.updatePlannerPhaseFromScanner(scanner, status, PlannerJobStage.BUILDING_GRAPH, 20, 0);
    }

    private boolean advancePlannerJob(PathScannerBE scanner, BlockPos scannerPos, long now) {
        if (!this.plannerJobActive) return false;
        this.plannerLastComputedTick = now;
        if (this.plannerJobPhase == 1) {
            if (!this.isPlannerScanReady(scanner)) {
                String status = String.format(java.util.Locale.ROOT, "A* 1/4 Detection: scan %d/%d chunks",
                    this.linkedLoadedChunks, Math.max(1, this.linkedExpectedChunks));
                int progress = 20 + (int) Math.round(20.0 * this.linkedLoadedChunks / Math.max(1, this.linkedExpectedChunks));
                return this.updatePlannerPhaseFromScanner(scanner, status, PlannerJobStage.BUILDING_GRAPH,
                    Math.max(20, Math.min(40, progress)), 0);
            }
            this.plannerJobPhase = 2;
            String status = String.format(java.util.Locale.ROOT, "A* 2/4 Understanding: clearance %.1fb hard / %.1fb preferred target=%s scanSide=%d",
                this.plannerJobHardClearance, this.plannerJobPreferredClearance, formatPlanningTarget(this.plannerJobNavTarget), this.routeScanSideChunks);
            return this.updatePlannerPhaseFromScanner(scanner, status, PlannerJobStage.COARSE_SEARCH, 45, 0);
        }
        if (this.plannerJobPhase == 2) {
            PathRoutePlanner.PlannedRoute plannedRoute = PathRoutePlanner.planLocalCorridor(scanner, scannerPos,
                this.plannerJobMaxSpeed, this.plannerJobNavTarget, this.plannerJobHardClearance,
                this.plannerJobPreferredClearance);
            this.plannerJobPlannedRoute = plannedRoute;
            this.plannerJobPhase = 3;
            int expanded = plannedRoute == null ? 0 : plannedRoute.expandedStates();
            String status = String.format(java.util.Locale.ROOT, "A* 3/4 Pathway routing: expanded %d", expanded);
            return this.updatePlannerPhaseFromRoute(plannedRoute, status, PlannerJobStage.FINE_SEARCH, 75);
        }
        if (this.plannerJobPhase == 3) {
            this.plannerJobPhase = 4;
            PathRoutePlanner.PlannedRoute plannedRoute = this.plannerJobPlannedRoute;
            String result = plannedRoute == null || plannedRoute.guidance().confidence() <= 0f ? "hold" : "route found";
            String status = "A* 4/4 Confirmation: " + result;
            return this.updatePlannerPhaseFromRoute(plannedRoute, status, PlannerJobStage.SMOOTHING, 90);
        }
        this.plannerJobActive = false;
        this.plannerJobPhase = 0;
        PathRoutePlanner.PlannedRoute plannedRoute = this.plannerJobPlannedRoute;
        this.plannerJobPlannedRoute = null;
        if (plannedRoute != null) {
            this.applyPlannedRoute(plannedRoute);
        }
        return false;
    }

    private boolean isPlannerScanReady(PathScannerBE scanner) {
        int expected = Math.max(1, this.linkedExpectedChunks);

        return this.linkedLoadedChunks >= expected && this.linkedUnknownChunks <= 3;
    }

    private boolean isRouteMonitorMode() {
        return isUsableGuidance(this.activeGuidance) && !this.isFccRouteArrived()
            && !this.isFccPathfinderStalledOrReversing();
    }

    private BlockPos activeRouteMonitorOrigin(BlockPos fallback) {
        if (this.activeGuidance == null || this.activeGuidance.waypoints().isEmpty()) return fallback;
        int index = Math.max(0, Math.min(this.linkedFccRouteWaypointIndex, this.activeGuidance.waypoints().size() - 1));
        Vec3 waypoint = this.activeGuidance.waypoints().get(index);
        return BlockPos.containing(waypoint.x, waypoint.y, waypoint.z);
    }

    private VehicleScanRequest vehicleScanRequest(PathScannerBE scanner, FlightControlComputerBE linkedFcc,
                                                  BlockPos origin, float yawDeg, boolean routeMonitor) {
        Vec3 navTarget = linkedFcc.pathfinderNavTarget();
        int forwardChunks = Math.max(scanner.settings().forwardChunks(), PathScanSettings.DEFAULT_FORWARD_CHUNKS);
        int sideChunks = Math.max(Math.max(scanner.settings().sideChunks(), ROUTE_SCAN_MIN_SIDE_CHUNKS), routeScanSideChunks);
        if (!routeMonitor && navTarget != null) {
            double originX = origin.getX() + 0.5;
            double originZ = origin.getZ() + 0.5;
            double dx = navTarget.x - originX;
            double dz = navTarget.z - originZ;
            double distance = Math.hypot(dx, dz);
            forwardChunks = Math.max(forwardChunks, (int) Math.ceil((distance + 32.0) / 16.0));
            double radians = Math.toRadians(yawDeg);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            double rightX = -forwardZ;
            double rightZ = forwardX;
            double sideBlocks = Math.abs(dx * rightX + dz * rightZ);
            sideChunks = Math.max(sideChunks, (int) Math.ceil((sideBlocks + 16.0) / 16.0));
        }
        return clampRouteWindow(forwardChunks, sideChunks);
    }

    private PlanGate planGateFor(@Nullable FlightControlComputerBE linkedFcc) {
        if (this.remotePaused) return new PlanGate(false, "Pathfinder paused from VCC");
        if (linkedFcc == null) return new PlanGate(false, "Waiting for VCC link");
        if (!linkedFcc.isPathfinderSystemsEnabled()) return new PlanGate(false, "Pathfinder systems closed");
        if (!linkedFcc.isAnyAutoDriveActive()) return new PlanGate(false, "Waiting for VCC auto drive");
        if (linkedFcc.pathfinderNavTarget() == null) return new PlanGate(false, "Waiting for Nav Table target");
        return new PlanGate(true, "Ready");
    }

    private boolean holdForPlanGate(String status) {
        String nextStatus = status == null || status.isBlank() ? "Waiting for VCC" : status;
        boolean changed = this.activeGuidance != null
            || this.plannerJobActive
            || !Objects.equals(this.plannerStatus, nextStatus)
            || this.plannerStage != PlannerJobStage.HOLD
            || this.plannerProgressPercent != 0
            || this.plannerExpandedStates != 0
            || this.routeBufferPreservedDistance != 0.0
            || this.routeBufferFreshDistance != 0.0
            || this.routeBufferTotalDistance != 0.0
            || this.routeBufferMerged;
        this.activeGuidance = null;
        this.plannerJobActive = false;
        this.plannerJobPhase = 0;
        this.plannerJobNavTarget = null;
        this.plannerJobPlannedRoute = null;
        this.plannerStatus = nextStatus;
        this.plannerStage = PlannerJobStage.HOLD;
        this.plannerProgressPercent = 0;
        this.plannerExpandedStates = 0;
        this.plannerHazardFlags = 0;
        this.routeBufferPreservedDistance = 0.0;
        this.routeBufferFreshDistance = 0.0;
        this.routeBufferTotalDistance = 0.0;
        this.routeBufferMerged = false;
        return changed;
    }

    private boolean updatePlannerPhaseFromScanner(PathScannerBE scanner, String status, PlannerJobStage stage,
                                                  int progressPercent, int expandedStates) {
        int inspected = scanner.totalPassableCells() + scanner.totalMarginalCells() + scanner.totalBlockedCells();
        boolean changed = !Objects.equals(this.plannerStatus, status)
            || this.plannerInspectedCells != inspected
            || this.plannerUsableCells != scanner.totalPassableCells()
            || this.plannerBlockedCells != scanner.totalBlockedCells()
            || this.plannerStage != stage
            || this.plannerProgressPercent != progressPercent
            || this.plannerExpandedStates != expandedStates;
        this.plannerStatus = status;
        this.plannerInspectedCells = inspected;
        this.plannerUsableCells = scanner.totalPassableCells();
        this.plannerBlockedCells = scanner.totalBlockedCells();
        this.plannerStage = stage;
        this.plannerProgressPercent = progressPercent;
        this.plannerExpandedStates = expandedStates;
        return changed;
    }

    private boolean updatePlannerPhaseFromRoute(@Nullable PathRoutePlanner.PlannedRoute plannedRoute, String status,
                                                PlannerJobStage stage, int progressPercent) {
        int inspected = plannedRoute == null ? this.plannerInspectedCells : plannedRoute.inspectedCells();
        int usable = plannedRoute == null ? this.plannerUsableCells : plannedRoute.usableCells();
        int blocked = plannedRoute == null ? this.plannerBlockedCells : plannedRoute.blockedCells();
        int hazards = plannedRoute == null ? this.plannerHazardFlags : plannedRoute.hazardFlags();
        int expanded = plannedRoute == null ? 0 : plannedRoute.expandedStates();
        boolean changed = !Objects.equals(this.plannerStatus, status)
            || this.plannerInspectedCells != inspected
            || this.plannerUsableCells != usable
            || this.plannerBlockedCells != blocked
            || this.plannerHazardFlags != hazards
            || this.plannerStage != stage
            || this.plannerProgressPercent != progressPercent
            || this.plannerExpandedStates != expanded;
        this.plannerStatus = status;
        this.plannerInspectedCells = inspected;
        this.plannerUsableCells = usable;
        this.plannerBlockedCells = blocked;
        this.plannerHazardFlags = hazards;
        this.plannerStage = stage;
        this.plannerProgressPercent = progressPercent;
        this.plannerExpandedStates = expanded;
        return changed;
    }

    private static String formatPlanningTarget(@Nullable Vec3 target) {
        if (target == null) return "corridor";
        return String.format(java.util.Locale.ROOT, "%.0f,%.0f", target.x, target.z);
    }

    private boolean applyPlannedRoute(PathRoutePlanner.PlannedRoute plannedRoute) {
        if (plannedRoute == null) return false;
        RouteGuidance rawGuidance = plannedRoute.guidance();
        MergedRoute mergedRoute = this.mergeContinuousRoute(rawGuidance);
        RouteGuidance nextGuidance = mergedRoute.guidance();
        Level level = this.level;
        long now = level == null ? this.linkedScannerScanTick : level.getGameTime();
        boolean nextUsable = isUsableGuidance(nextGuidance);
        boolean activeUsable = isUsableGuidance(this.activeGuidance);
        int previousRouteScanSideChunks = this.routeScanSideChunks;
        boolean provisionalGuidance = isProvisionalGuidance(nextGuidance);
        if (nextUsable) {
            if (provisionalGuidance) {
                advanceRouteScanSideChunks();
            } else {
                this.routeScanSideChunks = ROUTE_SCAN_MIN_SIDE_CHUNKS;
            }
        } else if (plannedRoute.guidance().confidence() <= 0f) {
            if (!advanceRouteScanSideChunks()) {
                this.plannerLastComputedTick = 0L;
            } else {
                this.plannerSettingsDirty = true;
                this.plannerLastComputedTick = 0L;
            }
        }
        boolean activeRouteConsumed = this.fccRouteProgressFraction() >= 0.95 || this.linkedFccStatus.startsWith("Arrived scanner route");
        PlannerJobStage nextStage = mergedRoute.merged() ? PlannerJobStage.MERGING
            : plannedRoute.stage() == null ? PlannerJobStage.HOLD : plannedRoute.stage();
        if (!nextUsable && activeUsable && shouldKeepActiveGuidanceDuringTransientMiss(now)) {
            if (this.noRouteSinceTick < 0L) this.noRouteSinceTick = now;
            String retainedStatus = "Keeping last route: " + (plannedRoute.status() == null ? "planner miss" : plannedRoute.status());
            boolean changed = !Objects.equals(this.plannerStatus, retainedStatus)
                || this.plannerInspectedCells != plannedRoute.inspectedCells()
                || this.plannerUsableCells != plannedRoute.usableCells()
                || this.plannerBlockedCells != plannedRoute.blockedCells()
                || this.plannerHazardFlags != plannedRoute.hazardFlags()
                || this.plannerStage != PlannerJobStage.HOLD
                || this.plannerProgressPercent != plannedRoute.progressPercent()
                || this.plannerExpandedStates != plannedRoute.expandedStates();
            this.plannerStatus = retainedStatus;
            this.plannerInspectedCells = plannedRoute.inspectedCells();
            this.plannerUsableCells = plannedRoute.usableCells();
            this.plannerBlockedCells = plannedRoute.blockedCells();
            this.plannerHazardFlags = plannedRoute.hazardFlags();
            this.plannerStage = PlannerJobStage.HOLD;
            this.plannerProgressPercent = plannedRoute.progressPercent();
            this.plannerExpandedStates = plannedRoute.expandedStates();
            this.plannerLastComputedTick = now;
            return changed;
        }
        this.noRouteSinceTick = nextUsable ? -1L : this.noRouteSinceTick < 0L ? now : this.noRouteSinceTick;
        long nextGeneration = this.routeGeneration;
        if (nextUsable && !sameGuidance(this.activeGuidance, nextGuidance)) {
            nextGeneration = Math.max(1L, this.routeGeneration + 1L);
        }
        String nextStatus = mergedRoute.merged()
            ? String.format(java.util.Locale.ROOT, "Merged continuous route: %.0fm kept + %.0fm fresh", mergedRoute.preservedDistance(), mergedRoute.freshDistance())
            : plannedRoute.status() == null ? "Planner idle" : plannedRoute.status();
        boolean changed = !sameGuidance(this.activeGuidance, nextGuidance)
            || !Objects.equals(this.plannerStatus, nextStatus)
            || this.plannerInspectedCells != plannedRoute.inspectedCells()
            || this.plannerUsableCells != plannedRoute.usableCells()
            || this.plannerBlockedCells != plannedRoute.blockedCells()
            || this.plannerHazardFlags != plannedRoute.hazardFlags()
            || this.plannerStage != nextStage
            || this.plannerProgressPercent != plannedRoute.progressPercent()
            || this.plannerExpandedStates != plannedRoute.expandedStates()
            || this.routeScanSideChunks != previousRouteScanSideChunks
            || this.routeGeneration != nextGeneration
            || Double.compare(this.routeBufferPreservedDistance, mergedRoute.preservedDistance()) != 0
            || Double.compare(this.routeBufferFreshDistance, mergedRoute.freshDistance()) != 0
            || Double.compare(this.routeBufferTotalDistance, mergedRoute.totalDistance()) != 0
            || this.routeBufferMerged != mergedRoute.merged();
        this.activeGuidance = nextGuidance;
        this.plannerStatus = nextStatus;
        this.plannerInspectedCells = plannedRoute.inspectedCells();
        this.plannerUsableCells = plannedRoute.usableCells();
        this.plannerBlockedCells = plannedRoute.blockedCells();
        this.plannerHazardFlags = plannedRoute.hazardFlags();
        this.plannerStage = nextStage;
        this.plannerProgressPercent = plannedRoute.progressPercent();
        this.plannerExpandedStates = plannedRoute.expandedStates();
        this.routeGeneration = nextGeneration;
        this.routeBufferPreservedDistance = mergedRoute.preservedDistance();
        this.routeBufferFreshDistance = mergedRoute.freshDistance();
        this.routeBufferTotalDistance = mergedRoute.totalDistance();
        this.routeBufferMerged = mergedRoute.merged();
        this.plannerLastComputedTick = now;
        return changed;
    }

    private MergedRoute mergeContinuousRoute(@Nullable RouteGuidance plannedGuidance) {
        if (!isUsableGuidance(plannedGuidance)) {
            return new MergedRoute(plannedGuidance, 0.0, 0.0, 0.0, false);
        }
        if (!isUsableGuidance(this.activeGuidance) || this.linkedFccRouteWaypointIndex <= 0) {
            return new MergedRoute(plannedGuidance, 0.0, plannedGuidance.estimatedDistance(),
                plannedGuidance.estimatedDistance(), false);
        }
        List<Vec3> activePoints = this.activeGuidance.waypoints();
        List<Vec3> plannedPoints = plannedGuidance.waypoints();
        int preserveEnd = Math.max(1, Math.min(this.linkedFccRouteWaypointIndex + 1, activePoints.size()));
        List<Vec3> mergedPoints = new ArrayList<>(activePoints.subList(0, preserveEnd));
        List<Float> mergedSpeeds = new ArrayList<>();
        for (int i = 0; i < preserveEnd; i++) {
            mergedSpeeds.add(speedAt(this.activeGuidance.targetSpeeds(), i));
        }
        Vec3 splice = mergedPoints.get(mergedPoints.size() - 1);
        int plannedStart = nearestWaypointIndex(plannedPoints, splice);
        for (int i = plannedStart; i < plannedPoints.size(); i++) {
            Vec3 point = plannedPoints.get(i);
            if (!mergedPoints.isEmpty() && horizontalDistance(mergedPoints.get(mergedPoints.size() - 1), point) < 0.35) {
                continue;
            }
            mergedPoints.add(point);
            mergedSpeeds.add(speedAt(plannedGuidance.targetSpeeds(), i));
        }
        if (mergedPoints.size() < 2) {
            return new MergedRoute(plannedGuidance, 0.0, plannedGuidance.estimatedDistance(),
                plannedGuidance.estimatedDistance(), false);
        }
        double preservedDistance = pathDistance(mergedPoints, 0, Math.max(0, preserveEnd - 1));
        double freshDistance = pathDistance(mergedPoints, Math.max(0, preserveEnd - 1), mergedPoints.size() - 1);
        RouteGuidance mergedGuidance = new RouteGuidance(mergedRouteId(this.activeGuidance.routeId(), plannedGuidance.routeId(),
                this.routeGeneration + 1L, preserveEnd, mergedPoints), plannedGuidance.routeName(), plannedGuidance.sourceScannerId(),
            Math.max(0.05f, Math.min(this.activeGuidance.confidence(), plannedGuidance.confidence())), mergedPoints,
            mergedSpeeds, plannedGuidance.safetyStops(), plannedGuidance.routeCost() + preservedDistance,
            plannedGuidance.nextReplanTick(), this.activeGuidance.hazardFlags() | plannedGuidance.hazardFlags());
        return new MergedRoute(mergedGuidance, preservedDistance, freshDistance,
            mergedGuidance.estimatedDistance(), true);
    }

    private static int nearestWaypointIndex(List<Vec3> points, Vec3 target) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            double distance = horizontalDistance(points.get(i), target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static float speedAt(List<Float> speeds, int index) {
        if (speeds == null || speeds.isEmpty()) return 0.0f;
        int safeIndex = Math.max(0, Math.min(index, speeds.size() - 1));
        Float speed = speeds.get(safeIndex);
        return speed == null ? 0.0f : speed;
    }

    private static double pathDistance(List<Vec3> points, int startIndex, int endIndex) {
        if (points == null || points.size() < 2) return 0.0;
        int start = Math.max(0, Math.min(startIndex, points.size() - 1));
        int end = Math.max(start, Math.min(endIndex, points.size() - 1));
        double distance = 0.0;
        Vec3 previous = points.get(start);
        for (int i = start + 1; i <= end; i++) {
            Vec3 next = points.get(i);
            distance += horizontalDistance(previous, next);
            previous = next;
        }
        return distance;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static UUID mergedRouteId(UUID activeId, UUID plannedId, long generation, int preserveEnd, List<Vec3> points) {
        Vec3 first = points.isEmpty() ? Vec3.ZERO : points.get(0);
        Vec3 last = points.isEmpty() ? Vec3.ZERO : points.get(points.size() - 1);
        String seed = "simple-merge:" + activeId + ':' + plannedId + ':' + generation + ':' + preserveEnd
            + ':' + points.size() + ':' + Math.round(first.x * 10.0) + ',' + Math.round(first.z * 10.0)
            + ':' + Math.round(last.x * 10.0) + ',' + Math.round(last.z * 10.0);
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private double fccRouteProgressFraction() {
        if (this.linkedFccRouteWaypointCount <= 1) return 0.0;
        return Math.max(0.0, Math.min(1.0, this.linkedFccRouteWaypointIndex / (double) (this.linkedFccRouteWaypointCount - 1)));
    }

    private boolean shouldRequestRollingReplan(@Nullable FlightControlComputerBE linkedFcc) {
        if (!isUsableGuidance(this.activeGuidance)) return false;
        Vec3 routeEnd = this.activeGuidance == null || this.activeGuidance.waypoints().isEmpty()
            ? null : this.activeGuidance.waypoints().get(this.activeGuidance.waypoints().size() - 1);
        if (linkedFcc != null && routeEnd != null && isFccStuckOnTerminalWaypoint(linkedFcc, routeEnd)) {
            return true;
        }
        if (!this.isFccRouteArrived()) return false;
        Vec3 navTarget = linkedFcc == null ? null : linkedFcc.pathfinderNavTarget();
        if (navTarget == null || routeEnd == null) return true;
        double dx = routeEnd.x - navTarget.x;
        double dz = routeEnd.z - navTarget.z;
        double radius = ARRIVED_NAV_TARGET_REPLAN_RADIUS_BLOCKS;
        return dx * dx + dz * dz > radius * radius;
    }

    private boolean shouldRequestProvisionalRouteRecheck(@Nullable FlightControlComputerBE linkedFcc, long now) {
        if (!isUsableGuidance(this.activeGuidance)) return false;
        if (this.activeGuidance.nextReplanTick() <= 0L) return false;
        if (linkedFcc == null || linkedFcc.pathfinderNavTarget() == null) return false;
        if (this.lastProvisionalRouteRecheckTick != Long.MIN_VALUE
            && now - this.lastProvisionalRouteRecheckTick < PROVISIONAL_ROUTE_RECHECK_INTERVAL_TICKS) return false;
        return this.activeRouteRemainingDistance() <= PROVISIONAL_ROUTE_RECHECK_DISTANCE_BLOCKS;
    }

    private boolean shouldRequestReactiveReplan(PathScannerBE scanner) {
        if (!isUsableGuidance(this.activeGuidance)) return false;
        return isFccPathfinderStalledOrReversing() || isActiveRouteObstructed(scanner);
    }

    private boolean isFccStuckOnTerminalWaypoint(FlightControlComputerBE linkedFcc, Vec3 routeEnd) {
        if (this.linkedFccRouteWaypointCount <= 0) return false;
        if (this.linkedFccRouteWaypointIndex < this.linkedFccRouteWaypointCount - 1) return false;
        if (this.linkedFccStatus == null || !this.linkedFccStatus.startsWith("Executing scanner route")) return false;
        BlockPos vehiclePos = linkedFcc.pathfinderPlanningOrigin();
        double dx = routeEnd.x - (vehiclePos.getX() + 0.5);
        double dz = routeEnd.z - (vehiclePos.getZ() + 0.5);
        double staleRadius = Math.max(12.0, ARRIVED_NAV_TARGET_REPLAN_RADIUS_BLOCKS * 2.0);
        return dx * dx + dz * dz > staleRadius * staleRadius;
    }

    private boolean isFccRouteArrived() {
        return this.linkedFccStatus != null && this.linkedFccStatus.startsWith("Arrived scanner route");
    }

    private boolean isFccPathfinderStalledOrReversing() {
        if (this.linkedFccStatus == null) return false;
        return this.linkedFccStatus.startsWith("Reversing scanner route") || this.linkedFccStatus.startsWith("Hold:");
    }

    private double activeRouteRemainingDistance() {
        if (this.activeGuidance == null || this.activeGuidance.waypoints().size() < 2) return Double.POSITIVE_INFINITY;
        int startIndex = Math.max(0, Math.min(this.linkedFccRouteWaypointIndex, this.activeGuidance.waypoints().size() - 1));
        double remaining = 0.0;
        Vec3 previous = this.activeGuidance.waypoints().get(startIndex);
        for (int i = startIndex + 1; i < this.activeGuidance.waypoints().size(); i++) {
            Vec3 next = this.activeGuidance.waypoints().get(i);
            remaining += Math.hypot(next.x - previous.x, next.z - previous.z);
            previous = next;
        }
        return remaining;
    }

    private boolean isActiveRouteObstructed(PathScannerBE scanner) {
        if (scanner == null || this.activeGuidance == null || this.activeGuidance.waypoints().isEmpty()) return false;
        int startIndex = Math.max(0, Math.min(this.linkedFccRouteWaypointIndex, this.activeGuidance.waypoints().size() - 1));
        int endIndex = Math.min(this.activeGuidance.waypoints().size(), startIndex + 8);
        for (int i = startIndex; i < endIndex; i++) {
            Vec3 waypoint = this.activeGuidance.waypoints().get(i);
            int x = (int) Math.floor(waypoint.x);
            int z = (int) Math.floor(waypoint.z);
            ChunkScanCacheEntry entry = scanner.getCacheEntry(x >> 4, z >> 4);
            if (entry == null || !entry.loaded()) continue;
            byte[] payload = entry.compressedCellPayload();
            if (payload == null || payload.length < 16 * 16) continue;
            int localX = Math.floorMod(x, 16);
            int localZ = Math.floorMod(z, 16);
            int index = localZ * 16 + localX;
            byte packed = payload[index];
            Passability passability = PathPayloadDecoder.passability(packed);
            int hazards = PathPayloadDecoder.hazards(packed);
            if ((hazards & (HazardFlags.LAVA | HazardFlags.UNSTABLE | HazardFlags.UNKNOWN_CHUNK)) != 0) return true;
            if (passability == Passability.BLOCKED || passability == Passability.UNKNOWN) return true;
        }
        return false;
    }

    private boolean shouldKeepActiveGuidanceDuringTransientMiss(long now) {
        if (this.noRouteSinceTick < 0L) return true;
        return now - this.noRouteSinceTick <= TRANSIENT_NO_ROUTE_GRACE_TICKS;
    }

    private boolean advanceRouteScanSideChunks() {
        int current = Math.max(ROUTE_SCAN_MIN_SIDE_CHUNKS, this.routeScanSideChunks);
        for (int step : ROUTE_SCAN_SIDE_STEPS) {
            int safeStep = Math.min(step, PathScanSettings.MAX_SIDE_CHUNKS);
            if (safeStep > current) {
                this.routeScanSideChunks = safeStep;
                return true;
            }
        }
        int max = PathScanSettings.MAX_SIDE_CHUNKS;
        if (current < max) {
            this.routeScanSideChunks = max;
            return true;
        }
        return false;
    }

    private static boolean isProvisionalGuidance(@Nullable RouteGuidance guidance) {
        return guidance != null && guidance.nextReplanTick() > 0L;
    }

    private static float planningScanYaw(@Nullable FlightControlComputerBE linkedFcc, BlockPos origin,
                                         float fallbackYaw, boolean routeMonitor) {
        if (routeMonitor || linkedFcc == null || origin == null) return fallbackYaw;
        Vec3 navTarget = linkedFcc.pathfinderNavTarget();
        if (navTarget == null) return fallbackYaw;
        double dx = navTarget.x - (origin.getX() + 0.5);
        double dz = navTarget.z - (origin.getZ() + 0.5);
        if (dx * dx + dz * dz < 1.0e-4) return fallbackYaw;
        return wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private VehicleScanRequest clampRouteWindow(int forwardChunks, int sideChunks) {
        int safeForward = clamp(forwardChunks, 1, PathScanSettings.MAX_FORWARD_CHUNKS);
        int safeSide = clamp(sideChunks, 0, PathScanSettings.MAX_SIDE_CHUNKS);
        while (safeForward * (safeSide * 2 + 1) > PathScanSettings.MAX_PATHFINDER_ROUTE_CHUNKS) {
            if (safeSide > ROUTE_SCAN_MIN_SIDE_CHUNKS) {
                safeSide--;
            } else if (safeForward > 1) {
                safeForward--;
            } else {
                break;
            }
        }
        return new VehicleScanRequest(safeForward, safeSide);
    }

    private static boolean isUsableGuidance(@Nullable RouteGuidance guidance) {
        return guidance != null && guidance.confidence() > 0f && guidance.waypoints().size() >= 2;
    }

    public void markFccGuidanceLink(BlockPos fccPos, boolean accepted, String status) {
        this.markFccGuidanceLink(fccPos, accepted, status, 0, 0);
    }

    public void markFccGuidanceLink(BlockPos fccPos, boolean accepted, String status, int waypointIndex, int waypointCount) {
        if (fccPos == null) return;
        String resolvedStatus = status == null || status.isBlank()
            ? (accepted ? "VCC accepted guidance" : "VCC rejected guidance") : status;
        int safeCount = Math.max(0, waypointCount);
        int safeIndex = safeCount <= 0 ? 0 : Math.max(0, Math.min(waypointIndex, safeCount - 1));
        boolean changed = !fccPos.equals(this.linkedFccPos) || !Objects.equals(this.linkedFccStatus, resolvedStatus)
            || this.linkedFccRouteWaypointIndex != safeIndex || this.linkedFccRouteWaypointCount != safeCount;
        this.linkedFccPos = fccPos.immutable();
        Level level = this.level;
        this.linkedFccLastSeenTick = level == null ? 0L : level.getGameTime();
        this.linkedFccStatus = resolvedStatus;
        this.linkedFccRouteWaypointIndex = safeIndex;
        this.linkedFccRouteWaypointCount = safeCount;
        if (changed) {
            this.markDirtyAndSync();
        } else {
            this.setChanged();
        }
    }

    private static boolean sameGuidance(@Nullable RouteGuidance first, @Nullable RouteGuidance second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return Objects.equals(first.routeId(), second.routeId())
            && Objects.equals(first.routeName(), second.routeName())
            && Objects.equals(first.waypoints(), second.waypoints())
            && Objects.equals(first.targetSpeeds(), second.targetSpeeds())
            && Objects.equals(first.safetyStops(), second.safetyStops())
            && Float.compare(first.confidence(), second.confidence()) == 0
            && Double.compare(first.routeCost(), second.routeCost()) == 0
            && first.hazardFlags() == second.hazardFlags();
    }

    public boolean refreshLinkedScanner() {
        if (this.level == null || this.linkedScannerPos == null) return false;
        PathScannerBE scanner = this.resolveLinkedScanner();
        if (scanner == null) return false;
        if (this.linkedScannerId != null && !this.linkedScannerId.equals(scanner.scannerId())) {
            this.clearLinkedScanner();
            return false;
        }
        scanner.setRemotePaused(this.remotePaused);
        if (this.remotePaused) return true;
        this.ingestScannerSummary(scanner, this.linkedScannerPos);
        return true;
    }

    public boolean isRemotePaused() {
        return remotePaused;
    }

    public void setRemotePaused(boolean paused) {
        if (this.remotePaused == paused) {
            PathScannerBE scanner = this.resolveLinkedScanner();
            if (scanner != null) scanner.setRemotePaused(paused);
            return;
        }
        this.remotePaused = paused;
        this.plannerJobActive = false;
        this.plannerJobPhase = 0;
        this.plannerJobNavTarget = null;
        this.plannerJobPlannedRoute = null;
        if (paused) {
            this.activeGuidance = null;
            this.plannerStatus = "Paused by VCC";
            this.plannerStage = PlannerJobStage.HOLD;
            this.plannerProgressPercent = 0;
            this.plannerExpandedStates = 0;
            this.routeBufferPreservedDistance = 0.0;
            this.routeBufferFreshDistance = 0.0;
            this.routeBufferTotalDistance = 0.0;
            this.routeBufferMerged = false;
        } else {
            this.plannerSettingsDirty = true;
            this.plannerLastComputedTick = 0L;
            this.plannerStatus = "Waiting for scanner";
            this.plannerStage = PlannerJobStage.WAITING_FOR_SCAN;
        }
        PathScannerBE scanner = this.resolveLinkedScanner();
        if (scanner != null) scanner.setRemotePaused(paused);
        this.markDirtyAndSync();
    }

    @Nullable
    private PathScannerBE resolveLinkedScanner() {
        if (this.level == null || this.linkedScannerPos == null) return null;
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(this.level, this.linkedScannerPos);
        if (!(blockEntity instanceof PathScannerBE) && this.level.isLoaded(this.linkedScannerPos)) {
            blockEntity = this.level.getBlockEntity(this.linkedScannerPos);
        }
        return blockEntity instanceof PathScannerBE scanner ? scanner : null;
    }

    @Nullable
    private FlightControlComputerBE resolveLinkedFcc() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.linkedFccPos == null) return null;
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(serverLevel, this.linkedFccPos);
        if (!(blockEntity instanceof FlightControlComputerBE) && serverLevel.isLoaded(this.linkedFccPos)) {
            blockEntity = serverLevel.getBlockEntity(this.linkedFccPos);
        }
        return blockEntity instanceof FlightControlComputerBE fcc ? fcc : null;
    }

    private void logDebug(ServerLevel serverLevel, PathScannerBE scanner, boolean replanned, boolean changed) {
        if (!pathfinderDebugLoggingEnabled()) return;
        boolean active = replanned || changed || linkedQueueDepth > 0 || plannerStage != PlannerJobStage.READY;
        if (!active && lastDebugLogTick != Long.MIN_VALUE && serverLevel.getGameTime() - lastDebugLogTick < 80L) {
            return;
        }
        if (active && lastDebugLogTick != Long.MIN_VALUE && serverLevel.getGameTime() - lastDebugLogTick < 40L) {
            return;
        }
        lastDebugLogTick = serverLevel.getGameTime();
        CreateStats.LOGGER.info("[PFDEBUG/COMPUTER] pos={} replanned={} changed={} {} scanner={} routePts={}",
            this.worldPosition, replanned, changed, this.debugLine(), scanner.debugLine(), routeWaypointSample(this.activeGuidance));
    }

    private static boolean pathfinderDebugLoggingEnabled() {
        return true;
    }

    public boolean isPlayerInInteractionRange(Player player) {
        return com.createstats.flight.bridge.SableBridge.isPlayerInInteractionRangeFor(this, player);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
            Component.literal("Pathfinder Computer").withStyle(ChatFormatting.GRAY)));
        if (!hasLinkedFcc()) {
            tooltip.add(Component.literal("    ").append(
                Component.literal("No VCC linked").withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }

        if (Float.isNaN(linkedFccHeadingDeg)) {
            tooltip.add(Component.literal("    ").append(
                Component.literal("Front: unknown").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            String arrow = goggleHeadingArrow(linkedFccHeadingDeg);
            String cardinal = goggleCardinalName(linkedFccHeadingDeg);
            tooltip.add(Component.literal("    ").append(
                Component.literal(String.format("Front: %s %s (%.0f\u00b0)", arrow, cardinal, linkedFccHeadingDeg))
                    .withStyle(ChatFormatting.AQUA)));
        }

        if (Float.isFinite(linkedFccSpeedMs)) {
            tooltip.add(Component.literal("    ").append(
                Component.literal(String.format("Speed: %.1f m/s", linkedFccSpeedMs))
                    .withStyle(ChatFormatting.WHITE)));
        }

        tooltip.add(Component.literal("    ").append(
            Component.literal(linkedFccHasFrontWheels ? "Wheels: front set \u2714" : "Wheels: front not set \u26a0")
                .withStyle(linkedFccHasFrontWheels ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        return true;
    }

    private static String goggleHeadingArrow(float yaw) {
        int sector = Math.round(((yaw % 360f) + 360f) % 360f / 45f) % 8;
        return new String[]{"↓", "↙", "←", "↖", "↑", "↗", "→", "↘"}[sector];
    }

    private static String goggleCardinalName(float yaw) {
        int sector = Math.round(((yaw % 360f) + 360f) % 360f / 45f) % 8;
        return new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"}[sector];
    }

    private org.joml.Vector3d interactionWorldCenter() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            org.joml.Vector3d world = SableBridge.localToWorld(subLevel, new org.joml.Vector3d(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5));
            if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
                return world;
            }
        }
        return new org.joml.Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
            this.worldPosition.getZ() + 0.5);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_stats.pathfinder_computer");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PathfinderComputerMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putUUID("ComputerId", computerId);
        if (linkedScannerPos != null) tag.putLong("LinkedScannerPos", linkedScannerPos.asLong());
        if (linkedScannerId != null) tag.putUUID("LinkedScannerId", linkedScannerId);
        tag.putLong("LinkedLastSeen", linkedScannerLastSeenTick);
        tag.putLong("LinkedScanTick", linkedScannerScanTick);
        tag.putInt("LinkedExpected", linkedExpectedChunks);
        tag.putInt("LinkedLoaded", linkedLoadedChunks);
        tag.putInt("LinkedUnknown", linkedUnknownChunks);
        tag.putInt("LinkedPassable", linkedPassableCells);
        tag.putInt("LinkedMarginal", linkedMarginalCells);
        tag.putInt("LinkedBlocked", linkedBlockedCells);
        tag.putInt("LinkedQueue", linkedQueueDepth);
        tag.putInt("LinkedProcessed", linkedLastProcessedChunks);
        tag.putString("PlannerStatus", plannerStatus);
        tag.putInt("PlannerInspected", plannerInspectedCells);
        tag.putInt("PlannerUsable", plannerUsableCells);
        tag.putInt("PlannerBlocked", plannerBlockedCells);
        tag.putInt("PlannerHazards", plannerHazardFlags);
        tag.putString("PlannerStage", plannerStage.name());
        tag.putInt("PlannerProgress", plannerProgressPercent);
        tag.putInt("PlannerExpanded", plannerExpandedStates);
        tag.putLong("PlannerComputed", plannerLastComputedTick);
        tag.putLong("RouteGeneration", routeGeneration);
        tag.putDouble("RouteBufferPreserved", routeBufferPreservedDistance);
        tag.putDouble("RouteBufferFresh", routeBufferFreshDistance);
        tag.putDouble("RouteBufferTotal", routeBufferTotalDistance);
        tag.putBoolean("RouteBufferMerged", routeBufferMerged);
        tag.putLong("NoRouteSince", noRouteSinceTick);
        tag.putFloat("MaxGuidanceSpeed", maxGuidanceSpeed);
        tag.putFloat("CornerClearanceExtra", cornerClearanceExtraBlocks);
        tag.putInt("RouteScanSideChunks", routeScanSideChunks);
        tag.putBoolean("RemotePaused", remotePaused);
        if (linkedFccPos != null) tag.putLong("LinkedFccPos", linkedFccPos.asLong());
        tag.putLong("LinkedFccSeen", linkedFccLastSeenTick);
        tag.putString("LinkedFccStatus", linkedFccStatus);
        tag.putInt("LinkedFccWaypointIndex", linkedFccRouteWaypointIndex);
        tag.putInt("LinkedFccWaypointCount", linkedFccRouteWaypointCount);
        if (Float.isFinite(linkedFccHeadingDeg)) tag.putFloat("LinkedFccHeadingDeg", linkedFccHeadingDeg);
        if (Float.isFinite(linkedFccSpeedMs)) tag.putFloat("LinkedFccSpeedMs", linkedFccSpeedMs);
        tag.putBoolean("LinkedFccHasFrontWheels", linkedFccHasFrontWheels);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.computerId = tag.hasUUID("ComputerId") ? tag.getUUID("ComputerId") : UUID.randomUUID();
        this.linkedScannerPos = tag.contains("LinkedScannerPos") ? BlockPos.of(tag.getLong("LinkedScannerPos")) : null;
        this.linkedScannerId = tag.hasUUID("LinkedScannerId") ? tag.getUUID("LinkedScannerId") : null;
        this.linkedScannerLastSeenTick = tag.getLong("LinkedLastSeen");
        this.linkedScannerScanTick = tag.getLong("LinkedScanTick");
        this.linkedExpectedChunks = tag.getInt("LinkedExpected");
        this.linkedLoadedChunks = tag.getInt("LinkedLoaded");
        this.linkedUnknownChunks = tag.getInt("LinkedUnknown");
        this.linkedPassableCells = tag.getInt("LinkedPassable");
        this.linkedMarginalCells = tag.getInt("LinkedMarginal");
        this.linkedBlockedCells = tag.getInt("LinkedBlocked");
        this.linkedQueueDepth = tag.getInt("LinkedQueue");
        this.linkedLastProcessedChunks = tag.getInt("LinkedProcessed");
        this.activeGuidance = tag.contains("ActiveGuidance") ? RouteGuidance.load(tag.getCompound("ActiveGuidance")) : null;
        this.plannerStatus = tag.contains("PlannerStatus", Tag.TAG_STRING) ? tag.getString("PlannerStatus") : "Waiting for scanner";
        this.plannerInspectedCells = tag.getInt("PlannerInspected");
        this.plannerUsableCells = tag.getInt("PlannerUsable");
        this.plannerBlockedCells = tag.getInt("PlannerBlocked");
        this.plannerHazardFlags = tag.getInt("PlannerHazards");
        this.plannerStage = PlannerJobStage.safeValueOf(tag.getString("PlannerStage"), PlannerJobStage.WAITING_FOR_SCAN);
        this.plannerProgressPercent = Math.max(0, Math.min(100, tag.getInt("PlannerProgress")));
        this.plannerExpandedStates = Math.max(0, tag.getInt("PlannerExpanded"));
        this.plannerLastComputedTick = tag.getLong("PlannerComputed");
        this.routeGeneration = Math.max(0L, tag.getLong("RouteGeneration"));
        this.routeBufferPreservedDistance = Math.max(0.0, tag.getDouble("RouteBufferPreserved"));
        this.routeBufferFreshDistance = Math.max(0.0, tag.getDouble("RouteBufferFresh"));
        this.routeBufferTotalDistance = Math.max(0.0, tag.getDouble("RouteBufferTotal"));
        this.routeBufferMerged = tag.getBoolean("RouteBufferMerged");
        this.noRouteSinceTick = tag.contains("NoRouteSince") ? tag.getLong("NoRouteSince") : -1L;
        this.maxGuidanceSpeed = tag.contains("MaxGuidanceSpeed")
            ? clamp(tag.getFloat("MaxGuidanceSpeed"), MIN_GUIDANCE_SPEED, MAX_GUIDANCE_SPEED) : 8.0f;
        this.cornerClearanceExtraBlocks = tag.contains("CornerClearanceExtra")
            ? clamp(tag.getFloat("CornerClearanceExtra"), MIN_CORNER_CLEARANCE_EXTRA_BLOCKS, MAX_CORNER_CLEARANCE_EXTRA_BLOCKS)
            : MIN_CORNER_CLEARANCE_EXTRA_BLOCKS;
        this.routeScanSideChunks = tag.contains("RouteScanSideChunks")
            ? clamp(tag.getInt("RouteScanSideChunks"), ROUTE_SCAN_MIN_SIDE_CHUNKS, PathScanSettings.MAX_SIDE_CHUNKS)
            : ROUTE_SCAN_MIN_SIDE_CHUNKS;
        this.remotePaused = tag.getBoolean("RemotePaused");
        this.plannerSettingsDirty = false;
        this.linkedFccPos = tag.contains("LinkedFccPos") ? BlockPos.of(tag.getLong("LinkedFccPos")) : null;
        this.linkedFccLastSeenTick = tag.getLong("LinkedFccSeen");
        this.linkedFccStatus = tag.contains("LinkedFccStatus", Tag.TAG_STRING) ? tag.getString("LinkedFccStatus") : "No VCC linked";
        this.linkedFccRouteWaypointCount = Math.max(0, tag.getInt("LinkedFccWaypointCount"));
        this.linkedFccRouteWaypointIndex = this.linkedFccRouteWaypointCount <= 0 ? 0
            : Math.max(0, Math.min(tag.getInt("LinkedFccWaypointIndex"), this.linkedFccRouteWaypointCount - 1));
        this.linkedFccHeadingDeg = tag.contains("LinkedFccHeadingDeg") ? tag.getFloat("LinkedFccHeadingDeg") : Float.NaN;
        this.linkedFccSpeedMs = tag.contains("LinkedFccSpeedMs") ? tag.getFloat("LinkedFccSpeedMs") : Float.NaN;
        this.linkedFccHasFrontWheels = tag.getBoolean("LinkedFccHasFrontWheels");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);

        if (activeGuidance != null && activeGuidance.confidence() > 0f) {
            tag.put("ActiveGuidance", activeGuidance.save());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    public void markDirtyAndSync() {
        this.setChanged();
        Level level = this.level;
        if (level instanceof ServerLevel serverLevel) {
            this.lastHeartbeatSyncTick = serverLevel.getGameTime();
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortId(@Nullable UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }

    private static String formatVec(@Nullable Vec3 point) {
        if (point == null) return "none";
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", point.x, point.y, point.z);
    }

    private static String routeWaypointSample(@Nullable RouteGuidance guidance) {
        if (guidance == null || guidance.waypoints().isEmpty()) return "none";
        int size = guidance.waypoints().size();
        int preview = Math.min(4, size);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < preview; i++) {
            if (i > 0) builder.append(" | ");
            builder.append(i).append(':').append(formatVec(guidance.waypoints().get(i)));
        }
        if (size > preview) {
            builder.append(" | ... | ").append(size - 1).append(':').append(formatVec(guidance.waypoints().get(size - 1)));
        }
        return builder.toString();
    }

    private record VehicleScanRequest(int forwardChunks, int sideChunks) {
    }

    private record PlanGate(boolean allowed, String status) {
    }

    private record MergedRoute(@Nullable RouteGuidance guidance, double preservedDistance, double freshDistance,
                               double totalDistance, boolean merged) {
    }
}
