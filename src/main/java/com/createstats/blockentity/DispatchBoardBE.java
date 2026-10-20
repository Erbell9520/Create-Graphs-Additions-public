package com.createstats.blockentity;

import com.createstats.advancement.ModAdvancements;
import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.block.DispatchBoardBlock;
import com.createstats.dispatch.DispatchBoardSnapshot;
import com.createstats.train.egg.ManifestEgg;
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
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_DISPATCH_BOARD));

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
    private static final String TAG_MANIFEST_SHOW_UNTIL = "ManifestShowUntil";

    private static final int REFRESH_IDLE_TICKS = 40;
    private static final int REFRESH_ACTIVE_TICKS = 10;

    private static final int SCREEN_REFRESH_MAX_AGE_TICKS = 5;

    private static final int SCREEN_ROSTER_MAX_AGE_TICKS = 20;
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

    private FreightManifest manifest;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;

    private StationTrainRoster stationRoster;

    private DispatchBoardSnapshot boardSnapshot;

    private List<TrainVisitRecord> visitHistory = new ArrayList<>();
    private UUID lastSeenTrainId;

    private long manifestShowUntil;

    private boolean isController;
    private BlockPos controllerPos;
    private int multiblockWidth;
    private int multiblockHeight;

    private int refreshCooldown;
    private int rosterCooldown;
    private long lastRefreshTick = Long.MIN_VALUE;

    private int lastSyncSignature = Integer.MIN_VALUE;

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
        this.refreshCooldown = Math.floorMod(pos.hashCode(), REFRESH_IDLE_TICKS) + 1;
        this.rosterCooldown = 1;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!this.postLoadValidated) {
            this.runPostLoadValidation();
        }

        com.createstats.power.FePowerFx.chargeFromNeighbors(this.level, this.worldPosition, this.feConsumer());
        if (!this.isController) {
            return;
        }

        final boolean fePowered = this.feConsumer.serviceTick(true);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        if (--this.refreshCooldown > 0) {
            return;
        }

        if (!this.feConsumer.spendBurst()) {
            this.refreshCooldown = 1;
            return;
        }
        this.refreshSummary();
        this.refreshCooldown = this.trainPresent ? REFRESH_ACTIVE_TICKS : REFRESH_IDLE_TICKS;
    }

    // one capture per refresh window, the open screen polled a full capture several times a sec
    public void refreshSummaryIfStale() {
        if (this.level != null && this.lastRefreshTick != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastRefreshTick < SCREEN_REFRESH_MAX_AGE_TICKS) {
            return;
        }
        this.refreshSummary();
    }

    public void refreshRosterForScreenIfStale() {
        if (this.level == null || this.level.isClientSide() || this.level.getServer() == null
                || !this.stationLink.isLinked()) {
            return;
        }
        long now = this.level.getGameTime();
        if (this.stationRoster.getCaptureTick() > 0L
                && now - this.stationRoster.getCaptureTick() < SCREEN_ROSTER_MAX_AGE_TICKS) {
            return;
        }
        ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(
                this.level.getServer(), this.stationLink);
        if (stationLevel == null) {
            this.stationRoster = StationTrainRoster.empty(now);
            this.rebuildSnapshot();
            return;
        }
        GlobalStation station = FreightManifestStationResolver.resolveStation(stationLevel, this.stationLink);
        if (station == null) {
            this.stationRoster = StationTrainRoster.empty(now);
            this.rebuildSnapshot();
            return;
        }
        String liveStationName = station.name != null ? station.name : this.stationLink.getStationName();
        this.stationRoster = StationTrainRoster.capture(
                this.level.getServer(), station, liveStationName, this.visitHistory, now);
        this.rebuildSnapshot();
    }

    public boolean isController() {
        return this.isController;
    }

    public BlockPos getControllerPos() {
        return this.controllerPos;
    }

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

        BlockPos ctrlPos = inRect.stream()
                .min(Comparator.<BlockPos, Integer>comparing(Vec3i::getY)
                        .thenComparing(bp -> dotAlongAxis(bp, right)))
                .orElse(this.worldPosition);

        FreightStationLink inheritedLink = null;
        AntennaTier inheritedTier = AntennaTier.NONE;
        for (BlockPos p : inRect) {
            if (!(this.level.getBlockEntity(p) instanceof DispatchBoardBE panel)) {
                continue;
            }
            if (panel.antennaTier.ordinal() > inheritedTier.ordinal()) {
                inheritedTier = panel.antennaTier;
            }
            if (!panel.stationLink.isLinked()) {
                continue;
            }
            if (p.equals(ctrlPos)) {
                inheritedLink = panel.stationLink;
                break;
            }
            if (inheritedLink == null || panel.isController) {
                inheritedLink = panel.stationLink;
            }
        }

        for (BlockPos p : connected) {
            BlockEntity be = this.level.getBlockEntity(p);
            if (be instanceof DispatchBoardBE panel) {
                if (inRect.contains(p)) {
                    panel.isController = p.equals(ctrlPos);
                    panel.controllerPos = ctrlPos;
                    panel.multiblockWidth = w;
                    panel.multiblockHeight = h2;
                    if (panel.isController) {
                        if (inheritedLink != null) {
                            panel.stationLink = inheritedLink.copy();
                        }
                        if (inheritedTier.ordinal() > panel.antennaTier.ordinal()) {
                            panel.antennaTier = inheritedTier;
                        }

                        panel.refreshCooldown = 1;
                        panel.lastRefreshTick = Long.MIN_VALUE;
                        panel.lastSyncSignature = Integer.MIN_VALUE;
                    }
                } else {
                    panel.isController = true;
                    panel.controllerPos = p;
                    panel.multiblockWidth = 1;
                    panel.multiblockHeight = 1;
                }
                panel.setChanged();
                this.level.sendBlockUpdated(p, panel.getBlockState(), panel.getBlockState(), 3);

                this.level.invalidateCapabilities(p);
            }
        }

        if (this.level.getBlockEntity(ctrlPos) instanceof DispatchBoardBE controller) {
            for (BlockPos memberPos : inRect) {
                if (!memberPos.equals(ctrlPos)
                        && this.level.getBlockEntity(memberPos) instanceof DispatchBoardBE member) {
                    member.feConsumer.transferChargeTo(controller.feConsumer);
                }
            }
        }
    }

    public void onRemoved() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        List<BlockPos> members = this.getMultiblockMembers();

        FreightStationLink survivingLink = this.stationLink.isLinked() ? this.stationLink.copy() : null;
        this.isController = true;
        this.controllerPos = this.worldPosition;
        this.multiblockWidth = 1;
        this.multiblockHeight = 1;

        for (BlockPos pos : members) {
            if (pos.equals(this.worldPosition)) continue;
            BlockEntity be = this.level.getBlockEntity(pos);
            if (!(be instanceof DispatchBoardBE panel)) continue;
            if (survivingLink != null && !panel.stationLink.isLinked()) {
                panel.stationLink = survivingLink.copy();
            }
            panel.isController = true;
            panel.controllerPos = pos;
            panel.multiblockWidth = 1;
            panel.multiblockHeight = 1;
            panel.setChanged();
            this.level.sendBlockUpdated(pos, panel.getBlockState(), panel.getBlockState(), 3);

            this.level.invalidateCapabilities(pos);
        }

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

    public long getManifestShowUntil() {
        final DispatchBoardBE controller = this.getControllerBE();
        return controller == this ? this.manifestShowUntil : controller.manifestShowUntil;
    }

    public void setStationLink(FreightStationLink stationLink) {
        this.stationLink = stationLink.copy();

        if (this.isController) {
            this.lastRefreshTick = Long.MIN_VALUE;
            this.lastSyncSignature = Integer.MIN_VALUE;
            this.refreshSummary();
        }
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

            FreightManifestStationResolver.StationView stationView =
                    FreightManifestStationResolver.captureStationView(server, this.stationLink);
            boardView = stationView.deliveryBoard();
            capturedCargoView = stationView.cargoView();
            capturedManifest = stationView.manifest();
            ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(server, this.stationLink);
            if (stationLevel != null && this.stationLink.getStationId() != null) {
                resolvedStation = FreightManifestStationResolver.resolveStation(stationLevel, this.stationLink);
                if (resolvedStation != null) {
                    presentTrain = resolvedStation.getPresentTrain();
                }
            }
        }

        this.manifest = capturedManifest;
        this.cargoView = capturedCargoView;
        this.deliveryBoard = boardView;

        long gameTick = this.level != null ? this.level.getGameTime() : 0L;
        boolean historyChanged = this.trackTrainVisit(presentTrain, gameTick);

        if (resolvedStation == null && (this.stationRoster.getPresentTrain() != null
                || !this.stationRoster.getAssociatedTrains().isEmpty())) {
            this.stationRoster = StationTrainRoster.empty(gameTick);
        }

        long elapsed = this.lastRefreshTick == Long.MIN_VALUE
                ? ROSTER_REFRESH_INTERVAL_TICKS : Math.max(0L, gameTick - this.lastRefreshTick);
        this.rosterCooldown -= (int) Math.min(Integer.MAX_VALUE, elapsed);
        if (resolvedStation != null && this.level != null && this.level.getServer() != null
                && this.rosterCooldown <= 0 && this.showsRoster()) {
            this.rosterCooldown = ROSTER_REFRESH_INTERVAL_TICKS;
            String liveStationName = resolvedStation.name != null
                    ? resolvedStation.name : this.stationLink.getStationName();
            this.stationRoster = StationTrainRoster.capture(
                    this.level.getServer(), resolvedStation,
                    liveStationName, this.visitHistory, gameTick);
        }

        boolean summaryChanged = this.applySummary(boardView) || historyChanged;
        this.lastRefreshTick = gameTick;
        this.rebuildSnapshot();

        // only broadcast when it'd draw differently, it resent the whole board every second

        int signature = this.computeSyncSignature();
        if (signature != this.lastSyncSignature) {
            this.lastSyncSignature = signature;
            if (summaryChanged) {
                this.setChanged();
            }
            this.sendToClients();
        }
    }

    private boolean showsRoster() {
        return com.createstats.dispatch.BoardTier
                .fromSize(this.multiblockWidth, this.multiblockHeight).showsTrainRoster();
    }

    private int computeSyncSignature() {
        int h = this.stationLink.isLinked() ? this.stationLink.getStationName().hashCode() : 0;
        h = h * 31 + (this.trainPresent ? 1 : 0) + (this.arrivalTrackingActive ? 2 : 0);
        h = h * 31 + this.trainName.hashCode();
        h = h * 31 + this.summaryStatus.hashCode();
        h = h * 31 + this.totalOrders;
        h = h * 31 + this.completedOrders;
        h = h * 31 + this.dismissedOrders;
        h = h * 31 + this.multiblockWidth * 31 + this.multiblockHeight;
        h = h * 31 + this.antennaTier.ordinal();
        for (com.createstats.train.manifest.DeliveryOrderLineView line : this.deliveryBoard.getLineViews()) {
            h = h * 31 + line.getLineId().hashCode();
            h = h * 31 + line.getDeliveredAmount();
            h = h * 31 + line.getTargetAmount();
            h = h * 31 + line.getFreightAmount();
            h = h * 31 + (line.isDismissed() ? 1 : 0);
            h = h * 31 + line.getDisplayName().hashCode();
            h = h * 31 + line.getOperation().ordinal() * 31 + line.getResetMode().ordinal();
            h = h * 31 + line.getResetIntervalValue();
            h = h * 31 + (line.getTicksUntilReset() > 0L ? 1 : 0);
        }
        for (java.util.Map.Entry<com.createstats.train.manifest.FreightManifestResourceKey, Integer> entry
                : this.cargoView.getFreightTotals().getAmounts().entrySet()) {
            h += entry.getKey().hashCode() * 31 + entry.getValue();
        }
        List<com.createstats.train.manifest.DeliveryOrderHistoryEntry> history = this.deliveryBoard.getHistoryEntries();
        h = h * 31 + history.size();
        if (!history.isEmpty()) {
            com.createstats.train.manifest.DeliveryOrderHistoryEntry latest = history.get(0);
            h = h * 31 + (int) latest.getTick();
            h = h * 31 + latest.getType().ordinal();
            h = h * 31 + latest.getTitle().hashCode();
            h = h * 31 + latest.getDetail().hashCode();
        }
        com.createstats.train.manifest.TrainSummarySnapshot present = this.stationRoster.getPresentTrain();
        h = hashTrainSnapshot(h, present);
        for (com.createstats.train.manifest.TrainSummarySnapshot train
                : this.stationRoster.getAssociatedTrains()) {
            h = hashTrainSnapshot(h, train);
        }
        for (TrainVisitRecord visit : this.visitHistory) {
            h = h * 31 + visit.getTrainId().hashCode();
            h = h * 31 + visit.getTrainName().hashCode();
            h = h * 31 + Long.hashCode(visit.getArrivalTick());
            h = h * 31 + Long.hashCode(visit.getDepartureTick());
        }
        h = h * 31 + Long.hashCode(this.manifestShowUntil);
        return h;
    }

    private static int hashTrainSnapshot(int seed,
            @Nullable com.createstats.train.manifest.TrainSummarySnapshot train) {
        int h = seed * 31 + (train == null ? 0 : 1);
        if (train == null) {
            return h;
        }
        h = h * 31 + train.getTrainId().hashCode();
        h = h * 31 + train.getTrainName().hashCode();
        h = h * 31 + train.getCarriageCount();
        h = h * 31 + Float.floatToIntBits(train.getSpeed());
        h = h * 31 + train.getNextDestination().hashCode();
        h = h * 31 + (train.isStoppedAtStation() ? 1 : 0);
        h = h * 31 + (train.isWaitingForSignal() ? 1 : 0);
        h = h * 31 + (train.isInboundToStation() ? 1 : 0);
        for (java.util.Map.Entry<com.createstats.train.manifest.FreightManifestResourceKey, Integer> entry
                : train.getCargoSnapshot().getAmounts().entrySet()) {
            h += entry.getKey().hashCode() * 31 + entry.getValue();
        }
        return h;
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

    private void rebuildSnapshot() {
        this.boardSnapshot = DispatchBoardSnapshot.assemble(
                this.multiblockWidth, this.multiblockHeight,
                this.stationLink, this.deliveryBoard, this.manifest,
                this.cargoView, this.stationRoster, this.visitHistory);
    }

    private boolean trackTrainVisit(@Nullable Train presentTrain, long gameTick) {
        UUID currentTrainId = presentTrain != null ? presentTrain.id : null;
        boolean changed = false;

        if (this.lastSeenTrainId != null && !this.lastSeenTrainId.equals(currentTrainId)) {
            for (TrainVisitRecord visit : this.visitHistory) {
                if (visit.getTrainId().equals(this.lastSeenTrainId) && visit.isStillPresent()) {
                    visit.setDepartureTick(gameTick);
                    changed = true;
                    break;
                }
            }
        }

        if (currentTrainId != null && !currentTrainId.equals(this.lastSeenTrainId)) {
            String name = presentTrain.name != null ? presentTrain.name.getString() : "Train";
            this.visitHistory.add(0, new TrainVisitRecord(currentTrainId, name, gameTick, -1L));
            while (this.visitHistory.size() > MAX_VISIT_HISTORY) {
                this.visitHistory.remove(this.visitHistory.size() - 1);
            }
            this.greetManifest(name, presentTrain.carriages.size(), gameTick);
            changed = true;
        }

        this.lastSeenTrainId = currentTrainId;

        return changed;
    }

    // the six girls. powered only, unpowered is NO POWER over everything
    private void greetManifest(String trainName, int carriages, long gameTick) {
        if (!ManifestEgg.isManifest(trainName, carriages) || !(this.level instanceof ServerLevel serverLevel)
                || !this.feConsumer().isPowered()) {
            return;
        }
        this.manifestShowUntil = ManifestEgg.showUntil(gameTick);
        ModAdvancements.awardNearby(serverLevel, this.worldPosition,
                ManifestEgg.AUDIENCE_RADIUS + Math.max(this.multiblockWidth, this.multiblockHeight),
                ModAdvancements.MANIFEST_YAAA);
    }

    private void notifyUpdate() {
        this.setChanged();
        this.sendToClients();
    }

    private void sendToClients() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

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
    }

    private void writeSyncPayload(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.deliveryBoard != null) {
            tag.put("DeliveryBoard", this.deliveryBoard.save(registries));
        }
        if (this.cargoView != null) {
            tag.put("CargoView", this.cargoView.save());
        }
        if (this.stationRoster != null) {
            tag.put("StationRoster", this.stationRoster.save());
        }
        tag.putLong(TAG_MANIFEST_SHOW_UNTIL, this.manifestShowUntil);
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

        if (tag.contains("DeliveryBoard")) {
            this.deliveryBoard = DeliveryOrderBoardView.load(tag.getCompound("DeliveryBoard"), registries);
        }
        if (tag.contains("CargoView")) {
            this.cargoView = FreightStationCargoView.load(tag.getCompound("CargoView"));
        }
        if (tag.contains("StationRoster")) {
            this.stationRoster = StationTrainRoster.load(tag.getCompound("StationRoster"));
        }
        if (tag.contains(TAG_MANIFEST_SHOW_UNTIL)) {
            this.manifestShowUntil = tag.getLong(TAG_MANIFEST_SHOW_UNTIL);
        }

        this.boardSnapshot = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        this.writeSyncPayload(tag, registries);
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
