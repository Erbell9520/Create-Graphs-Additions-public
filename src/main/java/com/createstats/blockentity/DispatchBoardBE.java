package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.block.DispatchBoardBlock;
import com.createstats.dispatch.DispatchBoardSnapshot;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.StationTrainRoster;
import com.createstats.train.manifest.TrainVisitRecord;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DispatchBoardBE extends BlockEntity implements RangeAttachment, com.createstats.power.IFeConsumer {

    /** FE Dependent Mode buffer (drained by the multiblock controller only). Receive-only. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_DISPATCH_BOARD));

    /** [1] */
    public com.createstats.power.FeConsumer feConsumer() {
        if (!this.isController) {
            final DispatchBoardBE controller = this.getControllerBE();
            if (controller != null && controller != this) {
                return controller.feConsumer;
            }
        }
        return this.feConsumer;
    }

    public static final String TAG_STATION_LINK = "StationLink";
    private static final String TAG_TRAIN_PRESENT = "TrainPresent";
    private static final String TAG_ARRIVAL_TRACKING_ACTIVE = "ArrivalTrackingActive";
    private static final String TAG_TRAIN_NAME = "TrainName";
    private static final String TAG_SUMMARY_STATUS = "SummaryStatus";
    private static final String TAG_TOTAL_ORDERS = "TotalOrders";
    private static final String TAG_COMPLETED_ORDERS = "CompletedOrders";
    private static final String TAG_DISMISSED_ORDERS = "DismissedOrders";
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final String UNLINKED_STATUS = "Dispatch board is not linked.";

    public static final int MAX_WALL_SIZE = 8;
    public static final int MAX_VISIT_HISTORY = 24;
    private static final int ROSTER_REFRESH_INTERVAL_TICKS = 100;

    private FreightStationLink stationLink;
    private boolean trainPresent;
    private boolean arrivalTrackingActive;
    private String trainName;
    private String summaryStatus;
    private int totalOrders;
    private int completedOrders;
    private int dismissedOrders;

    // Full snapshot state (transient, recaptured every tick, not persisted to disk)
    private FreightManifest manifest;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;

    // Station train roster (transient, recaptured periodically from Create's train graph)
    private StationTrainRoster stationRoster;

    // Assembled board snapshot (transient, rebuilt after each refresh from raw state)
    private DispatchBoardSnapshot boardSnapshot;

    // Visit history (persisted, tracks train arrivals/departures at this station)
    private List<TrainVisitRecord> visitHistory = new ArrayList<>();
    private UUID lastSeenTrainId;

    // Multiblock controller/member state
    private boolean isController;
    private BlockPos controllerPos;
    private int multiblockWidth;
    private int multiblockHeight;

    private boolean postLoadValidated = false;
    private int postLoadRetryTicks = 0;
    private static final int POST_LOAD_RETRY_MAX = 20;
    private AntennaTier antennaTier;

    public DispatchBoardBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.stationLink = new FreightStationLink();
        this.trainName = "";
        this.summaryStatus = UNLINKED_STATUS;
        this.manifest = new FreightManifest();
        this.cargoView = FreightStationCargoView.unavailable(UNLINKED_STATUS);
        this.deliveryBoard = DeliveryOrderBoardView.empty(0L, UNLINKED_STATUS);
        this.stationRoster = StationTrainRoster.empty(0L);
        this.isController = true;
        this.controllerPos = pos;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;
        this.antennaTier = AntennaTier.NONE;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!this.postLoadValidated) {
            this.runPostLoadValidation();
        }
        // Any board in the assembly is a valid charging point for the shared bank, even though
        // only the controller ever spends from it.
        com.createstats.power.FePowerFx.chargeFromNeighbors(this.level, this.worldPosition, this.feConsumer());
        if (!this.isController) {
            return;
        }
        // FE Dependent Mode gate: the controller pays the drain; a dead board stops refreshing
        // its train and cargo summaries. Each refresh also pays a burst.
        final boolean fePowered = this.feConsumer.serviceTick(true);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        if (this.level.getGameTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        if (!this.feConsumer.spendBurst()) {
            return;
        }
        this.refreshSummary();
    }

    // multiblock accessors

    public boolean isController() {
        return this.isController;
    }

    public BlockPos getControllerPos() {
        return this.controllerPos;
    }

    /**
     * Returns the controller block entity. If this is already the controller, returns itself.
     * For members, resolves the controller from controllerPos.
     */
    public DispatchBoardBE getControllerBE() {
        if (this.isController) {
            return this;
        }
        if (this.level != null && this.controllerPos != null) {
            BlockEntity be = this.level.getBlockEntity(this.controllerPos);
            if (be instanceof DispatchBoardBE controller && controller.isController()) {
                return controller;
            }
        }
        return this;
    }

    public int getMultiblockWidth() {
        return this.multiblockWidth;
    }

    public int getMultiblockHeight() {
        return this.multiblockHeight;
    }

    public List<BlockPos> getMultiblockMembers() {
        List<BlockPos> members = new ArrayList<>();
        BlockPos origin = this.controllerPos != null ? this.controllerPos : this.worldPosition;
        if (this.level == null || this.multiblockWidth <= 0 || this.multiblockHeight <= 0) {
            members.add(this.worldPosition);
            return members;
        }
        BlockState myState = this.getBlockState();
        if (!myState.hasProperty(DispatchBoardBlock.FACING)) {
            members.add(this.worldPosition);
            return members;
        }
        Direction facing = myState.getValue(DispatchBoardBlock.FACING);
        // DispatchBoard FACING = display direction (opposite of StatsDisplayPanel).
        // CounterClockWise places the controller at the correct corner so the
        // BER quad extends over the multiblock in the right direction.
        Direction right = facing.getCounterClockWise();
        for (int dy = 0; dy < this.multiblockHeight; dy++) {
            for (int dx = 0; dx < this.multiblockWidth; dx++) {
                members.add(origin.relative(right, dx).above(dy));
            }
        }
        return members;
    }

    public boolean isPlayerInInteractionRange(Player player) {
        DispatchBoardBE controller = this.getControllerBE();
        double range = AntennaRange.directRange(controller.getLevel() != null ? controller.getLevel().dimension() : null,
                64.0, controller.getAntennaTier());
        for (BlockPos memberPos : controller.getMultiblockMembers()) {
            if (player.distanceToSqr(memberPos.getX() + 0.5, memberPos.getY() + 0.5, memberPos.getZ() + 0.5) <= range * range) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolvedTier) {
            return;
        }
        this.antennaTier = resolvedTier;
        this.notifyUpdate();
    }

    // multiblock formation

    public void detectAndFormMultiblock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState myState = this.getBlockState();
        if (!myState.hasProperty(DispatchBoardBlock.FACING)) {
            return;
        }
        Direction facing = myState.getValue(DispatchBoardBlock.FACING);
        Direction right = facing.getCounterClockWise();

        // Flood-fill to find all connected dispatch board blocks with the same facing
        Set<BlockPos> connected = new HashSet<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        toCheck.add(this.worldPosition);
        connected.add(this.worldPosition);
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            for (Direction dir : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos neighbor = current.relative(dir);
                if (!connected.contains(neighbor)) {
                    BlockEntity nbe = this.level.getBlockEntity(neighbor);
                    if (nbe instanceof DispatchBoardBE) {
                        BlockState nState = nbe.getBlockState();
                        if (nState.hasProperty(DispatchBoardBlock.FACING)
                                && nState.getValue(DispatchBoardBlock.FACING) == facing) {
                            connected.add(neighbor);
                            toCheck.add(neighbor);
                        }
                    }
                }
            }
        }

        // Project onto (h, v) coordinate space relative to a reference point
        BlockPos ref = connected.iterator().next();
        int refH = dotAlongAxis(ref, right);
        int refV = ref.getY();
        Set<Long> occupiedHV = new HashSet<>();
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (BlockPos p : connected) {
            int h = dotAlongAxis(p, right) - refH;
            int v = p.getY() - refV;
            occupiedHV.add(packHV(h, v));
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        // Find largest rectangle, capped at MAX_WALL_SIZE x MAX_WALL_SIZE
        int bestArea = 0, bestRH = minH, bestRV = minV, bestRW = 1, bestRHt = 1;
        for (int sv = minV; sv <= maxV; sv++) {
            for (int sh = minH; sh <= maxH; sh++) {
                if (!occupiedHV.contains(packHV(sh, sv))) continue;
                int maxW = Math.min(maxH - sh + 1, MAX_WALL_SIZE);
                for (int rh = 1; rh <= Math.min(maxV - sv + 1, MAX_WALL_SIZE); rh++) {
                    int rowW = 0;
                    for (int dh = 0; dh < maxW && occupiedHV.contains(packHV(sh + dh, sv + rh - 1)); dh++) {
                        rowW = dh + 1;
                    }
                    maxW = Math.min(maxW, rowW);
                    if (maxW == 0) break;
                    int area = maxW * rh;
                    if (area > bestArea) {
                        bestArea = area;
                        bestRH = sh;
                        bestRV = sv;
                        bestRW = maxW;
                        bestRHt = rh;
                    }
                }
            }
        }

        int w = bestRW;
        int h2 = bestRHt;
        Set<BlockPos> inRect = new HashSet<>();
        for (BlockPos p : connected) {
            int ph = dotAlongAxis(p, right) - refH;
            int pv = p.getY() - refV;
            if (ph >= bestRH && ph < bestRH + w && pv >= bestRV && pv < bestRV + h2) {
                inRect.add(p);
            }
        }

        // Controller is the bottom-left block (lowest Y, then leftmost along right axis)
        BlockPos ctrlPos = inRect.stream()
                .min(Comparator.<BlockPos, Integer>comparing(Vec3i::getY)
                        .thenComparing(bp -> dotAlongAxis(bp, right)))
                .orElse(this.worldPosition);

        // Apply multiblock state to all connected blocks
        for (BlockPos p : connected) {
            BlockEntity be = this.level.getBlockEntity(p);
            if (be instanceof DispatchBoardBE panel) {
                if (inRect.contains(p)) {
                    panel.isController = p.equals(ctrlPos);
                    panel.controllerPos = ctrlPos;
                    panel.multiblockWidth = w;
                    panel.multiblockHeight = h2;
                } else {
                    panel.isController = true;
                    panel.controllerPos = p;
                    panel.multiblockWidth = 1;
                    panel.multiblockHeight = 1;
                }
                panel.setChanged();
                this.level.sendBlockUpdated(p, panel.getBlockState(), panel.getBlockState(), 3);
                // feConsumer() resolves differently once the controller changes, so the cached
                // energy capability at this position has to be dropped.
                this.level.invalidateCapabilities(p);
            }
        }
        // Pool member charge into the new controller so a merge never strands FE in a buffer that
        // is about to go dormant behind the shared bank.
        if (this.level.getBlockEntity(ctrlPos) instanceof DispatchBoardBE controller) {
            for (BlockPos memberPos : inRect) {
                if (!memberPos.equals(ctrlPos)
                        && this.level.getBlockEntity(memberPos) instanceof DispatchBoardBE member) {
                    member.feConsumer.transferChargeTo(controller.feConsumer);
                }
            }
        }
    }

    /**
     * Called when this block is removed from the world.
     * Resets all members to independent 1x1 controllers, then re-forms the remaining group.
     */
    public void onRemoved() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        List<BlockPos> members = this.getMultiblockMembers();
        this.isController = true;
        this.controllerPos = this.worldPosition;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;

        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) continue;
            BlockEntity be = this.level.getBlockEntity(pos);
            if (!(be instanceof DispatchBoardBE panel)) continue;
            panel.isController = true;
            panel.controllerPos = pos;
            panel.multiblockWidth = 1;
            panel.multiblockHeight = 1;
            panel.setChanged();
            this.level.sendBlockUpdated(pos, panel.getBlockState(), panel.getBlockState(), 3);
            // Each board owns its own buffer again, so re-resolve the energy capability.
            this.level.invalidateCapabilities(pos);
        }

        // Trigger re-formation for remaining blocks
        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) continue;
            BlockEntity be = this.level.getBlockEntity(pos);
            if (be instanceof DispatchBoardBE panel) {
                panel.detectAndFormMultiblock();
                break;
            }
        }
    }

    private void runPostLoadValidation() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.isController) {
            List<BlockPos> members = this.getMultiblockMembers();
            for (BlockPos pos : members) {
                if (!this.level.hasChunkAt(pos)) {
                    if (++this.postLoadRetryTicks > POST_LOAD_RETRY_MAX) {
                        this.detectAndFormMultiblock();
                        this.postLoadValidated = true;
                    }
                    return;
                }
            }
            boolean mismatch = false;
            Direction facing = this.getBlockState().hasProperty(DispatchBoardBlock.FACING)
                    ? this.getBlockState().getValue(DispatchBoardBlock.FACING) : null;
            for (BlockPos pos : members) {
                BlockEntity be = this.level.getBlockEntity(pos);
                if (!(be instanceof DispatchBoardBE panel)) {
                    mismatch = true;
                    break;
                }
                BlockState state = panel.getBlockState();
                if (facing != null && (!state.hasProperty(DispatchBoardBlock.FACING)
                        || state.getValue(DispatchBoardBlock.FACING) != facing)) {
                    mismatch = true;
                    break;
                }
                if (pos.equals(this.worldPosition)) {
                    if (!panel.isController) { mismatch = true; break; }
                } else {
                    if (panel.isController || panel.controllerPos == null
                            || !panel.controllerPos.equals(this.worldPosition)) {
                        mismatch = true;
                        break;
                    }
                }
            }
            if (mismatch) {
                this.detectAndFormMultiblock();
            }
            this.postLoadValidated = true;
            return;
        }
        // Member validation
        if (this.controllerPos == null) {
            this.postLoadValidated = true;
            return;
        }
        if (!this.level.hasChunkAt(this.controllerPos)) {
            if (++this.postLoadRetryTicks > POST_LOAD_RETRY_MAX) {
                this.detectAndFormMultiblock();
                this.postLoadValidated = true;
            }
            return;
        }
        BlockEntity controllerBE = this.level.getBlockEntity(this.controllerPos);
        boolean ok = false;
        if (controllerBE instanceof DispatchBoardBE controller && controller.isController()) {
            for (BlockPos pos : controller.getMultiblockMembers()) {
                if (pos.equals(this.worldPosition)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            this.detectAndFormMultiblock();
        }
        this.postLoadValidated = true;
    }

    private static long packHV(int h, int v) {
        return (long) h << 32 | ((long) v & 0xFFFFFFFFL);
    }

    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }

    // freight data accessors

    public FreightStationLink getStationLink() {
        return this.stationLink.copy();
    }

    public boolean isLinked() {
        return this.stationLink.isLinked();
    }

    public boolean isTrainPresent() {
        return this.trainPresent;
    }

    public boolean isArrivalTrackingActive() {
        return this.arrivalTrackingActive;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public String getSummaryStatus() {
        return this.summaryStatus;
    }

    public int getTotalOrders() {
        return this.totalOrders;
    }

    public int getCompletedOrders() {
        return this.completedOrders;
    }

    public int getDismissedOrders() {
        return this.dismissedOrders;
    }

    public FreightManifest getManifest() {
        return this.manifest.copy();
    }

    public FreightStationCargoView getCargoView() {
        return this.cargoView.copy();
    }

    public DeliveryOrderBoardView getDeliveryBoard() {
        return this.deliveryBoard;
    }

    public StationTrainRoster getStationRoster() {
        return this.stationRoster.copy();
    }

    public List<TrainVisitRecord> getVisitHistory() {
        List<TrainVisitRecord> copy = new ArrayList<>();
        for (TrainVisitRecord visit : this.visitHistory) {
            copy.add(visit.copy());
        }
        return copy;
    }

    /** [2] */
    public DispatchBoardSnapshot getBoardSnapshot() {
        if (!this.isController) {
            DispatchBoardBE controller = this.getControllerBE();
            if (controller != this) {
                return controller.getBoardSnapshot();
            }
        }
        if (this.boardSnapshot == null) {
            this.rebuildSnapshot();
        }
        return this.boardSnapshot;
    }

    public void setStationLink(FreightStationLink stationLink) {
        this.stationLink = stationLink.copy();
        this.refreshSummary();
        this.notifyUpdate();
    }

    public void refreshSummary() {
        DeliveryOrderBoardView boardView;
        FreightManifest capturedManifest = new FreightManifest();
        FreightStationCargoView capturedCargoView = FreightStationCargoView.unavailable(UNLINKED_STATUS);
        GlobalStation resolvedStation = null;
        Train presentTrain = null;

        if (this.level == null) {
            boardView = DeliveryOrderBoardView.empty(0L, this.stationLink.isLinked() ? this.summaryStatus : UNLINKED_STATUS);
        } else if (!this.stationLink.isLinked()) {
            boardView = DeliveryOrderBoardView.empty(this.level.getGameTime(), UNLINKED_STATUS);
        } else if (this.level.getServer() == null) {
            boardView = DeliveryOrderBoardView.empty(this.level.getGameTime(), this.summaryStatus);
        } else {
            MinecraftServer server = this.level.getServer();
            boardView = FreightManifestStationResolver.captureDeliveryBoard(server, this.stationLink);
            capturedCargoView = FreightManifestStationResolver.captureCargoView(server, this.stationLink);
            ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(server, this.stationLink);
            if (stationLevel != null && this.stationLink.getStationId() != null) {
                FreightManifest resolved = FreightManifestSavedData.get(stationLevel).getManifest(this.stationLink.getStationId());
                if (resolved != null) {
                    capturedManifest = resolved.copy();
                }
                // Resolve station for train tracking and roster capture
                StationBlockEntity stationBE = FreightManifestStationResolver.resolveStationBlockEntity(stationLevel, this.stationLink);
                if (stationBE != null) {
                    resolvedStation = stationBE.getStation();
                    if (resolvedStation != null) {
                        presentTrain = resolvedStation.getPresentTrain();
                    }
                }
            }
        }

        this.manifest = capturedManifest;
        this.cargoView = capturedCargoView;
        this.deliveryBoard = boardView;

        // Track train arrivals/departures for visit history
        long gameTick = this.level != null ? this.level.getGameTime() : 0L;
        this.trackTrainVisit(presentTrain, gameTick);

        // Refresh station roster on a slower interval
        if (resolvedStation != null && this.level != null && this.level.getServer() != null
                && gameTick % ROSTER_REFRESH_INTERVAL_TICKS == 0) {
            String liveStationName = resolvedStation.name != null
                    ? resolvedStation.name : this.stationLink.getStationName();
            this.stationRoster = StationTrainRoster.capture(
                    this.level.getServer(), resolvedStation,
                    liveStationName, this.visitHistory, gameTick);
        }

        this.applySummary(boardView);
        // Always sync to client, line details, cargo, and roster can change
        // even when scalar summary counts stay the same.
        this.notifyUpdate();
        this.rebuildSnapshot();
    }

    private boolean applySummary(DeliveryOrderBoardView boardView) {
        boolean changed = this.trainPresent != boardView.isTrainPresent()
            || this.arrivalTrackingActive != boardView.isArrivalTrackingActive()
            || !this.trainName.equals(boardView.getTrainName())
            || !this.summaryStatus.equals(boardView.getSummaryStatus())
            || this.totalOrders != boardView.getTotalOrders()
            || this.completedOrders != boardView.getCompletedOrders()
            || this.dismissedOrders != boardView.getDismissedOrders();
        this.trainPresent = boardView.isTrainPresent();
        this.arrivalTrackingActive = boardView.isArrivalTrackingActive();
        this.trainName = boardView.getTrainName();
        this.summaryStatus = boardView.getSummaryStatus();
        this.totalOrders = boardView.getTotalOrders();
        this.completedOrders = boardView.getCompletedOrders();
        this.dismissedOrders = boardView.getDismissedOrders();
        return changed;
    }

    /**
     * Rebuilds the cached board snapshot from the current raw state.
     * Called after every successful refreshSummary cycle and lazily on first access.
     */
    private void rebuildSnapshot() {
        this.boardSnapshot = DispatchBoardSnapshot.assemble(
                this.multiblockWidth, this.multiblockHeight,
                this.stationLink, this.deliveryBoard, this.manifest,
                this.cargoView, this.stationRoster, this.visitHistory);
    }

    /**
     * Detects train arrivals and departures by comparing the present train UUID
     * across refresh ticks. Creates visit records on arrival and closes them on departure.
     */
    private void trackTrainVisit(@Nullable Train presentTrain, long gameTick) {
        UUID currentTrainId = presentTrain != null ? presentTrain.id : null;

        // Detect departure (previously present train is gone)
        if (this.lastSeenTrainId != null && !this.lastSeenTrainId.equals(currentTrainId)) {
            for (TrainVisitRecord visit : this.visitHistory) {
                if (visit.getTrainId().equals(this.lastSeenTrainId) && visit.isStillPresent()) {
                    visit.setDepartureTick(gameTick);
                    break;
                }
            }
        }

        // Detect arrival (new train that wasn't here before)
        if (currentTrainId != null && !currentTrainId.equals(this.lastSeenTrainId)) {
            String name = presentTrain.name != null ? presentTrain.name.getString() : "Train";
            this.visitHistory.add(0, new TrainVisitRecord(currentTrainId, name, gameTick, -1L));
            while (this.visitHistory.size() > MAX_VISIT_HISTORY) {
                this.visitHistory.remove(this.visitHistory.size() - 1);
            }
        }

        this.lastSeenTrainId = currentTrainId;
    }

    private void notifyUpdate() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // NBT persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        if (this.stationLink.isLinked()) {
            tag.put(TAG_STATION_LINK, this.stationLink.save());
        }
        tag.putBoolean(TAG_TRAIN_PRESENT, this.trainPresent);
        tag.putBoolean(TAG_ARRIVAL_TRACKING_ACTIVE, this.arrivalTrackingActive);
        tag.putString(TAG_TRAIN_NAME, this.trainName);
        tag.putString(TAG_SUMMARY_STATUS, this.summaryStatus);
        tag.putInt(TAG_TOTAL_ORDERS, this.totalOrders);
        tag.putInt(TAG_COMPLETED_ORDERS, this.completedOrders);
        tag.putInt(TAG_DISMISSED_ORDERS, this.dismissedOrders);
        ListTag visitHistoryTag = new ListTag();
        for (TrainVisitRecord visit : this.visitHistory) {
            visitHistoryTag.add(visit.save());
        }
        tag.put("VisitHistory", visitHistoryTag);
        tag.putBoolean("IsController", this.isController);
        tag.putLong("ControllerPos", (this.controllerPos != null ? this.controllerPos : this.worldPosition).asLong());
        tag.putInt("MBWidth", this.multiblockWidth);
        tag.putInt("MBHeight", this.multiblockHeight);
        tag.putString("AntennaTier", this.antennaTier.name());
        // Sync full board data so the client can build a real snapshot
        if (this.deliveryBoard != null) {
            tag.put("DeliveryBoard", this.deliveryBoard.save(registries));
        }
        if (this.cargoView != null) {
            tag.put("CargoView", this.cargoView.save());
        }
        if (this.stationRoster != null) {
            tag.put("StationRoster", this.stationRoster.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        if (tag.contains(TAG_STATION_LINK)) {
            this.stationLink = FreightStationLink.load(tag.getCompound(TAG_STATION_LINK));
        } else {
            this.stationLink = new FreightStationLink();
        }
        this.trainPresent = tag.getBoolean(TAG_TRAIN_PRESENT);
        this.arrivalTrackingActive = tag.getBoolean(TAG_ARRIVAL_TRACKING_ACTIVE);
        this.trainName = tag.getString(TAG_TRAIN_NAME);
        this.summaryStatus = tag.contains(TAG_SUMMARY_STATUS) ? tag.getString(TAG_SUMMARY_STATUS) : UNLINKED_STATUS;
        this.totalOrders = tag.getInt(TAG_TOTAL_ORDERS);
        this.completedOrders = tag.getInt(TAG_COMPLETED_ORDERS);
        this.dismissedOrders = tag.getInt(TAG_DISMISSED_ORDERS);
        this.visitHistory.clear();
        if (tag.contains("VisitHistory")) {
            ListTag visitHistoryTag = tag.getList("VisitHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < visitHistoryTag.size(); i++) {
                this.visitHistory.add(TrainVisitRecord.load(visitHistoryTag.getCompound(i)));
            }
        }
        // Recover lastSeenTrainId from any open (still-present) visit record
        this.lastSeenTrainId = null;
        for (TrainVisitRecord visit : this.visitHistory) {
            if (visit.isStillPresent()) {
                this.lastSeenTrainId = visit.getTrainId();
                break;
            }
        }
        this.isController = tag.contains("IsController") ? tag.getBoolean("IsController") : true;
        if (tag.contains("ControllerPos")) {
            this.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        } else {
            this.controllerPos = this.worldPosition;
        }
        this.multiblockWidth = tag.getInt("MBWidth");
        this.multiblockHeight = tag.getInt("MBHeight");
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        if (this.multiblockWidth <= 0) this.multiblockWidth = 1;
        if (this.multiblockHeight <= 0) this.multiblockHeight = 1;
        // Restore full board data for client-side snapshot assembly
        if (tag.contains("DeliveryBoard")) {
            this.deliveryBoard = DeliveryOrderBoardView.load(tag.getCompound("DeliveryBoard"), registries);
        }
        if (tag.contains("CargoView")) {
            this.cargoView = FreightStationCargoView.load(tag.getCompound("CargoView"));
        }
        if (tag.contains("StationRoster")) {
            this.stationRoster = StationTrainRoster.load(tag.getCompound("StationRoster"));
        }
        // Invalidate cached snapshot so it rebuilds from the newly loaded data
        this.boardSnapshot = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
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
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }
}
