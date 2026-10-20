package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.PowerDistributionUnitBlock;
import com.createstats.gui.pdu.PduHistory;
import com.createstats.gui.pdu.PduTopology;
import com.createstats.network.PduHistoryPacket;
import com.createstats.registry.ModBlocks;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGridNode;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagerView;
import com.createstats.power.FePriority;
import com.createstats.power.FeProfiles;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.PduAutoController;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PowerDistributionUnitBE extends BlockEntity
        implements com.createstats.power.FeStudPanel, com.createstats.power.FeManagedLoad,
        com.createstats.power.FeBoardPeer, com.createstats.power.FeSupply,
        com.createstats.power.FeForecastingSource,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public int maxLinksPerStud() {
        return com.createstats.power.ConnectorTier.ADVANCED.maxLinks();
    }

    public static final int LEGS = com.createstats.power.PduStuds.COUNT + 1;

    public static final int BACK_LEG = com.createstats.power.PduStuds.COUNT;

    private int energy;
    private int cursor;

    private int legCursor;
    private FeGridSolver.BranchResult lastBranch = FeGridSolver.BranchResult.EMPTY;
    private int lastSyncedEnergy = -1;
    private int ticksSinceSync = 0;
    private int lastRosterSignature = Integer.MIN_VALUE;

    private static final long NEVER = PduAutoController.NEVER;

    private int inputAccumulator;

    private int measuredInput;

    private int exportAccumulator;

    private int backExport;

    private int observedInput;

    private float smoothExport = -1.0f;

    private int exportDemand;

    private final PduAutoController autoController = new PduAutoController();

    private final com.createstats.power.PduLoadModel loadModel = new com.createstats.power.PduLoadModel();

    private final Map<Long, com.createstats.power.FeDeviceDemand> demandByNode = new HashMap<>();

    private long lastSupplyReadTick = NEVER;

    private int rawGeneration;

    private boolean rawBankCharging;

    private com.createstats.power.PduSupplySurvey.Result survey =
            com.createstats.power.PduSupplySurvey.Result.EMPTY;

    private final com.createstats.power.FeSupplyMemory supplyMemory =
            new com.createstats.power.FeSupplyMemory();

    private final com.createstats.power.FeSupplyMemory weatherShareMemory =
            new com.createstats.power.FeSupplyMemory();

    private int lastProjectedFe;

    private int syncedForecastFloor = com.createstats.power.PduSupplySurvey.NO_FORECAST;
    private int syncedForecastDrop;
    private int syncedForecastSources;
    private int syncedDayWarmBuckets;

    @Nullable
    private com.createstats.blockentity.WeatherPanelBE branchWeatherPanel;

    private com.createstats.power.FeSupplyOutlook.Result outlook =
            com.createstats.power.FeSupplyOutlook.Result.NONE;

    private int syncedOutlookShortfall;
    private int syncedOutlookDarkTicks;
    private boolean syncedOutlookCertain;

    private com.createstats.power.PduSupplySurvey.Result legForecast =
            com.createstats.power.PduSupplySurvey.Result.EMPTY;

    private int learnedGeneration;

    private int ownHoldFe;
    private int peerHoldFe;

    private boolean ownHoldApplied;

    private com.createstats.power.FeSupplyOutlook.Series outlookSeries =
            com.createstats.power.FeSupplyOutlook.Series.NONE;
    private long outlookSeriesDayTime;
    private com.createstats.power.FeSupplyOutlook.Weather outlookWeather =
            com.createstats.power.FeSupplyOutlook.Weather.steady(com.createstats.power.SolarCurve.Sky.CLEAR);

    private int syncedOwnHoldFe;
    private boolean syncedOwnHeld;
    private int syncedPeerHoldFe;
    private int syncedOutlookCritical;
    private int syncedSeriesStep;
    private int[] syncedSeriesRate = new int[0];
    private int[] syncedSeriesClear = new int[0];
    private long syncedSeriesCold;
    private long syncedSeriesDayTime;
    private int syncedWeatherNow;
    private int syncedWeatherIn = com.createstats.power.FeSupplyOutlook.UNKNOWN_CHANGE;
    private int syncedWeatherThen;

    private final com.createstats.power.FeHoldBook holdBook = new com.createstats.power.FeHoldBook();

    private int syncedHoldFe;
    private int syncedEffectiveFloorPct;

    public static final int OUTLOOK_TICKS = com.createstats.power.FeSupplyMemory.DAY_TICKS;

    public static final int HOLD_GRACE_TICKS = 200;

    public static final int PROJECTION_TICKS = com.createstats.power.FeSupplyMemory.DAY_TICKS / 2;

    public static final int SERIES_STEP_TICKS = 500;

    private long lastSourceSignature;

    private boolean surveyPrimed;

    private int lastSourceCount;

    private boolean supplyShock;

    private boolean lastThroughputLimited;

    private int lastCeiling;

    private Map<Long, PduReason> lastReasons = Map.of();

    private int syncedCapacity;
    private int syncedFloorPct = 15;

    private int syncedUpkeep;

    private int syncedTankCount;
    private int syncedReserveDepth;
    private int syncedReserveTotal;
    private int syncedReserveCapacity;
    private int syncedCoreRank = com.createstats.power.FeReserve.DEFAULT_RANK;
    private boolean syncedCoreOnly;
    private int syncedSourceCount;

    private boolean rawFeedMisplaced;
    private boolean syncedFeedMisplaced;

    private long lastFeedCheckTick = NEVER;

    private int lastGeneration;
    private int lastDemand;
    private int lastBudget;

    private int lastAdmitBudget;
    private int lastCriticalBudget;
    private int lastSupplyDrop;
    private int lastStanding;
    private int lastChargeClaimUsed;
    private int lastRideBudget;
    private boolean lastBelowFloor;
    private boolean lastLinkedDecision;

    private int syncedSupPriced;
    private int syncedSupWeather;
    private int syncedSupObserved;
    private int syncedSupLinked;
    private int syncedSupLegs;

    private boolean syncedSupAllPriced;

    private boolean syncedSupControllerLinked;
    private int lastServedDemand;

    private int lastScreenedDemand;

    private int lastScreenedShed;

    private int lastDealSupply;
    private int lastEnduranceTicks = Integer.MAX_VALUE;
    private boolean lastSurplus;

    private static final float TELEMETRY_EMA = 0.1f;
    private float smoothGen = -1.0f;
    private float smoothDemand = -1.0f;
    private float smoothScreenedDemand = -1.0f;

    private IEnergyStorage makeBossInputView(int boss) {
        return new IEnergyStorage() {
            private int spent;

            private long tick = Long.MIN_VALUE;

            private void refill(long now) {
                if (now != tick) {
                    tick = now;
                    spent = 0;
                }
            }

            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (level != null) {
                    refill(level.getGameTime());
                }
                final int room = Math.min(Math.min(maxReceive, Math.max(0, inputRatePerBoss() - spent)),
                        bufferCapacity() - energy);
                if (room <= 0) {
                    return 0;
                }
                if (!simulate) {
                    energy += room;
                    spent += room;
                    inputAccumulator += room;
                    if (level != null) {
                        long now = level.getGameTime();
                        if (cabinetBossTick != now) {
                            java.util.Arrays.fill(cabinetBossIntake, 0);
                            cabinetBossTick = now;
                        }
                        cabinetBossIntake[boss] += room;
                    }
                    setChanged();
                }
                return room;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return energy;
            }

            @Override
            public int getMaxEnergyStored() {
                return bufferCapacity();
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    private final int[] cabinetBossIntake = new int[2];
    private long cabinetBossTick = Long.MIN_VALUE;
    private final IEnergyStorage inputViewClockwise = makeBossInputView(1);
    private final IEnergyStorage inputViewCounterClockwise = makeBossInputView(0);

    protected int[] completedBossIntakes() {
        return level != null && cabinetBossTick == level.getGameTime()
                ? cabinetBossIntake.clone() : new int[2];
    }

    protected int[][] completedPortTraffic() {
        return new int[][]{legOutflow.clone(), legIntake.clone()};
    }

    public PowerDistributionUnitBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private final IEnergyStorage backOutputView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return takeFromBackBoss(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return bufferCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private int takeFromBackBoss(int maxExtract, boolean simulate) {
        if (!isLegEnergised(BACK_LEG)) {
            return 0;
        }

        if (level != null) {
            refillLegBudgets(level.getGameTime());
            refillExportBudget(level.getGameTime());
        }
        // export policy was missing entirely. a foreign battery could drain us to zero and shed everything while we said "generation covers it"

        final int room = Math.min(energy, exportRoom());
        final int taken = Math.min(Math.min(maxExtract, legRoom(legOutflow, BACK_LEG)), room);
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            energy -= taken;
            spendLeg(legOutflow, BACK_LEG, taken);
            spendExport(taken);

            exportAccumulator += taken;
            setChanged();
        }
        return taken;
    }

    public int extractForBoard(int maxExtract, boolean simulate) {
        return takeFromBackBoss(maxExtract, simulate);
    }

    public com.createstats.power.FeExportReport exportReport() {
        if (level == null || level.isClientSide()) {
            return com.createstats.power.FeExportReport.NONE;
        }
        refillExportBudget(level.getGameTime());

        final boolean shedding = mode == PduMode.AUTO
                && (lastScreenedShed > 0
                    || energy < Math.round(bufferCapacity() * restoreSocFraction()));
        return new com.createstats.power.FeExportReport(worldPosition.asLong(),
                exportRoom(),
                com.createstats.power.PduExportPolicy.spareGeneration(
                        supplyForExport(), runningDemandForExport(),
                        FeProfiles.powerRequired() ? idleDrainPerTick() : 0,
                        Math.max(0, branchStorageSummary.chargeRate())),

                energy, bufferCapacity(), effectiveReserveFloorPct(),
                com.createstats.power.PduExportPolicy.stanceOf(energy, bufferCapacity(),
                        effectiveReserveFloorPct(), Math.round(restoreSocFraction() * 100.0f), shedding),
                shedding);
    }

    public com.createstats.power.FeExportReport exportReportFor(int leg) {
        final com.createstats.power.FeExportReport board = exportReport();
        if (!board.isPresent()) {
            return board;
        }
        final int allowance = exportBudget.allowance() >= FeGridSolver.NO_CEILING
                ? legCeiling(leg) : Math.min(exportBudget.allowance(), legCeiling(leg));
        return new com.createstats.power.FeExportReport(board.panelPos(), allowance,
                board.spareGeneration(), board.stored(), board.capacity(), board.floorPct(),
                board.stance(), board.holdCharging());
    }

    private void talkToBranchBoards() {
        if (level == null || branchBoards.isEmpty()) {
            return;
        }
        for (BoardLink link : branchBoards) {
            if (!level.isLoaded(link.at())
                    || !(level.getBlockEntity(link.at())
                            instanceof com.createstats.power.FeBoardPeer peer)) {
                continue;
            }
            peer.acceptExportReport(exportReportFor(link.leg()));
            peer.notePushFrom(worldPosition, branchFlowByNode.getOrDefault(link.nodeKey(), 0));

            final int share = peerHoldShares.getOrDefault(link.at().asLong(), 0);
            if (outlook.isShort() && share > 0) {
                peer.acceptHoldTarget(worldPosition.asLong(), share, level.getGameTime() + HOLD_GRACE_TICKS);
            }

            noteDownstreamReserve(peer.boardReserveReport());
        }
    }

    private final com.createstats.power.PduExportBudget exportBudget =
            new com.createstats.power.PduExportBudget();

    private int exportPolicyAllowance() {
        if (!exportPolicyEnabled()) {
            return FeGridSolver.NO_CEILING;
        }
        return com.createstats.power.PduExportPolicy.allowance(
                energy, bufferCapacity(), effectiveReserveFloorPct(),
                Math.round(restoreSocFraction() * 100.0f),
                com.createstats.power.PduExportPolicy.spareGeneration(
                        supplyForExport(), runningDemandForExport(),
                        FeProfiles.powerRequired() ? idleDrainPerTick() : 0,
                        Math.max(0, branchStorageSummary.chargeRate())),
                widestExportConductor());
    }

    private int widestExportConductor() {
        final long now = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (now == widestExportTick) {
            return widestExport;
        }
        int widest = legCeiling(BACK_LEG);
        for (int leg = 0; leg < com.createstats.power.PduStuds.COUNT; leg++) {
            if (legCarriesBoard[leg]) {
                widest = Math.max(widest, legCeiling(leg));
            }
        }
        widestExportTick = now;
        widestExport = widest;
        return widest;
    }

    private long widestExportTick = Long.MIN_VALUE;
    private int widestExport;

    private void refillExportBudget(long now) {
        exportBudget.refill(exportPolicyAllowance(), now);
    }

    private int exportRoom() {
        return level == null ? 0
                : exportBudget.remaining(exportPolicyAllowance(), level.getGameTime());
    }

    private void spendExport(int fe) {
        if (level != null) {
            exportBudget.spend(exportPolicyAllowance(), level.getGameTime(), fe);
        }
    }

    private int supplyForExport() {
        final int supply = lastDealSupply > 0 ? lastDealSupply : lastGeneration;
        return mode == PduMode.AUTO ? Math.max(supply, measuredInput) : measuredInput;
    }

    private int runningDemandForExport() {
        final int branch = mode == PduMode.AUTO
                ? Math.max(lastServedDemand, lastBranch.delivered()) : lastBranch.delivered();
        return Math.max(0, branch - Math.max(lastBoardDemand, boardFeedRate));
    }

    public IEnergyStorage energyHandle() {
        return inputViewClockwise;
    }

    @Nullable
    public IEnergyStorage inputHandle(Direction side) {
        if (!isInputFace(side)) {
            return null;
        }
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return side == facing.getClockWise() ? inputViewClockwise : inputViewCounterClockwise;
    }

    public IEnergyStorage backOutputHandle() {
        return backOutputView;
    }

    public int storedEnergy() {
        return energy;
    }

    public int capacity() {
        return syncedCapacity > 0 ? syncedCapacity : bufferCapacity();
    }

    public int upkeepFePerTick() {
        if (level != null && level.isClientSide()) {
            return syncedUpkeep;
        }
        return FeProfiles.powerRequired() ? idleDrainPerTick() : 0;
    }

    public float fillFraction() {
        final int cap = capacity();
        return cap <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, energy / (float) cap));
    }

    public FeGridSolver.BranchResult lastBranchResult() {
        return lastBranch;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (energy > bufferCapacity()) {
            energy = bufferCapacity();
        }

        syncedCapacity = bufferCapacity();
        syncedFloorPct = reserveFloorPct();
        syncedUpkeep = FeProfiles.powerRequired() ? idleDrainPerTick() : 0;

        final long now = level.getGameTime();
        if (lastFeedCheckTick == NEVER || now - lastFeedCheckTick >= supplyReadIntervalTicks()) {
            lastFeedCheckTick = now;

            rawFeedMisplaced =
                    com.createstats.power.NewAgeFeed.connectorWithoutCoupler(level, worldPosition);
        }
        syncedFeedMisplaced = rawFeedMisplaced;
        autoController.setTuning(new PduAutoController.Tuning(dwellTicks(), restoreIntervalTicks()));

        measuredInput = inputAccumulator;
        inputAccumulator = 0;
        backExport = exportAccumulator;
        exportAccumulator = 0;

        boardFeedRate = boardFeedAccumulator;
        boardFeedAccumulator = 0;
        controllerDraw = controllerDrawAccumulator;
        controllerDrawAccumulator = 0;
        snapshotControllerDraws();
        upstreamPanelFeed = upstreamPanelAccumulator;
        upstreamPanelAccumulator = 0;

        observedInput = Math.max(measuredInput - feedStorageIntake(measuredInput),
                Math.round(observedInput * observedDecay()));

        final int exportedTotal = com.createstats.power.FeRates.plus(backExport, boardFeedRate);
        smoothExport = smoothExport < 0.0f ? exportedTotal
                : smoothExport + (exportedTotal - smoothExport) * TELEMETRY_EMA;
        exportDemand = Math.max(0, Math.round(smoothExport));
        refillExportBudget(now);

        pruneStudLinks();

        for (int leg = 0; leg < LEGS; leg++) {
            if (isLegWired(leg)) {
                legsEverWired |= 1 << leg;
            }
        }

        final List<FeGridNode> managed = collectBranchLoads();

        publishManagerView(level.getGameTime());

        final List<FeGridNode> loads = new ArrayList<>(managed.size());
        for (FeGridNode node : managed) {
            if (lockOf(node) == FeLock.FORCE_OFF) {
                setShedFlag(node, true);
            } else {
                loads.add(node);
            }
        }

        refillLegBudgets(level.getGameTime());

        drainBranchSources();

        if (FeProfiles.powerRequired()) {
            energy = Math.max(0, energy - idleDrainPerTick());
        }

        if (!loads.isEmpty()) {
            int wanted = 0;
            for (FeGridNode node : loads) {
                if (mode == PduMode.AUTO && !autoController.isRunning(node.nodeKey())
                        && !autoController.isRunning(splitKey(node))) {
                    continue;
                }
                wanted += Math.max(0, steadyDemandOf(node));
            }

            if (mode != PduMode.AUTO) {
                currentRequest = new com.createstats.power.FeBoardDeal.Request(worldPosition.asLong(),
                        Math.max(0, lastCriticalDemand), com.createstats.power.FeRates.plus(wanted,
                                Math.max(0, exportDemand) + upkeepFePerTick()), 0);

                lastDeal = controllerAnswer(level.getGameTime());
            }

            final int shortfall = com.createstats.power.PduBankDraw.shortfall(wanted,
                    branchThroughputCeiling(), energy,
                    Math.round(bufferCapacity() * (reserveFloorPct() / 100.0f)));
            if (shortfall > 0) {
                dischargeBranchStorage(shortfall);
            }
        }

        if (loads.isEmpty()) {
            lastBranch = FeGridSolver.BranchResult.EMPTY;

            currentRequest = new com.createstats.power.FeBoardDeal.Request(worldPosition.asLong(), 0,
                    Math.max(0, exportDemand) + upkeepFePerTick(), 0);
            lastDeal = com.createstats.power.FeBoardDeal.Answer.NONE;

            lastCriticalDemand = 0;
            idleTelemetry();
        } else {
            if (mode != PduMode.AUTO) {
                for (FeGridNode node : loads) {
                    setShedFlag(node, false);
                }
            }
            lastBranch = switch (mode) {
                case OFF -> serveAcrossLegs(loads, false);
                case MANUAL -> serveAcrossLegs(loads, true);
                case AUTO -> serveActiveAuto(loads, Math.min(energy, branchThroughputCeiling()));
            };
            energy -= lastBranch.delivered();
            if (lastBranch.delivered() > 0) {
                setChanged();
            }
        }

        lastBurstRelief = loads.isEmpty() ? 0 : burstReliefOf(loads);

        for (Map.Entry<Long, Integer> flow : branchFlowByNode.entrySet()) {
            if (flow.getValue() > 0) {
                loadModel.delivered(flow.getKey(), flow.getValue());
            }
        }

        final int charged = chargeBranchStorage();

        final int storesCharged = chargeBranchStores(loads, level.getGameTime(), charged);

        final int moved = lastBranch.delivered()
                + Math.max(0, charged)
                + Math.max(0, storesCharged)
                + Math.max(0, branchSourceInput)
                + Math.max(0, branchStorageDrawn)

                + Math.max(0, backExport);
        cacheInstrumentFlows();
        for (int i = 0; i < managedNetworks.size(); i++) {
            final FeWireNetwork network = managedNetworks.get(i);
            network.reportManagedThroughput(moved, level.getGameTime());

            reportFlowsTo(network, i < managedAnchors.size()
                            ? managedAnchors.get(i) : com.createstats.power.FeFlowGraph.NO_BOUNDARY,
                    i == backLegNetwork ? backExport : 0);
        }
        reportInstruments();
        rebuildRoster(managed);
        if (--overridePruneCountdown <= 0) {
            overridePruneCountdown = OVERRIDE_PRUNE_INTERVAL;
            pruneOverrides();
        }
        findStrayBoardLegs();
        tellDownstreamPanels();

        for (BlockPos board : terminalBoards) {
            noteBoardFeed(board, branchFlowByNode.getOrDefault(board.asLong(), 0));
        }

        talkToBranchBoards();

        talkToStations(managed);
        recordHistory();
        syncIfDue();
    }

    private List<FeGridNode> collectBranchLoads() {
        final List<FeGridNode> loads = new ArrayList<>();
        legOfNode.clear();
        parallelFeeds.clear();
        stationLegNetworks.clear();
        branchStorage.clear();
        branchSources.clear();
        storageFlowByNode.clear();
        branchFlowByNode.clear();
        managedNetworks.clear();
        managedAnchors.clear();
        cappedNetworks.clear();
        terminalBoards.clear();
        terminalBoardOwnerByNode.clear();
        terminalBoardNetworkByNode.clear();
        branchBoards.clear();
        boardNodes.clear();

        branchWeatherPanel = null;
        java.util.Arrays.fill(legCarriesBoard, false);
        backLegNetwork = -1;
        branchInstruments.clear();
        instrumentFlowByPos.clear();
        capsOfNode.clear();
        demandByNode.clear();

        manualShed.clear();
        branchStorageDrawn = 0;
        branchTankStored = 0;
        branchTankCapacity = 0;
        loadModel.setTuning(loadModelTuning());
        final long walkTick = level.getGameTime();

        final java.util.Set<IEnergyStorage> storageSeen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final java.util.Set<Long> storagePools = new HashSet<>();
        int conductors = 0;
        int buffered = 0;
        int capacity = 0;
        final java.util.Set<BlockPos> seenDevices = new HashSet<>();

        final java.util.Set<IEnergyStorage> loadHandlers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int leg = 0; leg < LEGS; leg++) {
            final List<LegGrid> grids = legGrids(leg, branchInstruments);
            legConduitCeiling[leg] = conduitCeilingOf(grids);
            for (LegGrid grid : grids) {
                final FeWireNetwork network = grid.network();
                network.markPduManaged(level.getGameTime());

                if (!grid.instruments().isEmpty() && !containsIdentity(cappedNetworks, network)) {
                    cappedNetworks.add(network);
                }
                if (leg == BACK_LEG) {
                    backLegNetwork = indexOfIdentity(managedNetworks, network);
                    if (backLegNetwork < 0) {
                        backLegNetwork = managedNetworks.size();
                    }
                }

                if (!containsIdentity(managedNetworks, network)) {
                    managedNetworks.add(network);

                    managedAnchors.add(grid.anchorKey());
                }
                for (com.createstats.power.FeGridMember node : network.servedMembers()) {
                    if (node instanceof CableBE || node instanceof PowerConnectorBE
                            || node instanceof com.createstats.blockentity.IndoorCableBE) {
                        conductors++;
                        buffered += node.storedEnergy();
                        capacity += node.bufferCapacity();
                    }

                    if (node instanceof com.createstats.blockentity.WeatherPanelBE weather
                            && branchWeatherPanel == null) {
                        branchWeatherPanel = weather;
                    }
                    final IEnergyStorage attached = node.attachedStorage();
                    if (attached == null) {
                        continue;
                    }

                    final BlockPos devicePos = devicePosOf(node);
                    if (devicePos != null && worldPosition.equals(devicePos)) {
                        continue;
                    }

                    if (branchInstruments.contains(devicePos.asLong())) {
                        continue;
                    }

                    com.createstats.power.FeForeignHandlers.learn(level, devicePos, attached);

                    final com.createstats.power.FeNodeRole role =
                            com.createstats.power.FeNodeRole.of(attached, node.transferRate());
                    if (role.kind() == com.createstats.power.FeNodeRole.Kind.STORAGE) {
                        if (storageSeen.add(attached) && claimPool(attached, storagePools)) {
                            legOfNode.put(node.nodeKey(), leg);

                            if (!grid.instruments().isEmpty()) {
                                capsOfNode.put(node.nodeKey(), grid.instruments());
                            }
                            branchStorage.add(node);

                            branchTankStored = com.createstats.power.FeRates.plus(branchTankStored,
                                    Math.max(0, attached.getEnergyStored()));
                            branchTankCapacity = com.createstats.power.FeRates.plus(
                                    branchTankCapacity, Math.max(0, attached.getMaxEnergyStored()));
                        }
                        continue;
                    }
                    if (role.kind() == com.createstats.power.FeNodeRole.Kind.GENERATOR) {
                        legOfNode.put(node.nodeKey(), leg);

                        if (!grid.instruments().isEmpty()) {
                            capsOfNode.put(node.nodeKey(), grid.instruments());
                        }
                        branchSources.add(node);
                        continue;
                    }
                    if (role.kind() != com.createstats.power.FeNodeRole.Kind.LOAD) {
                        continue;
                    }
                    if (devicePos == null) {
                        continue;
                    }

                    if (!seenDevices.add(devicePos.immutable())) {
                        noteParallelFeed(node, leg, grid);
                        continue;
                    }

                    if (!loadHandlers.add(attached)) {
                        continue;
                    }
                    legOfNode.put(node.nodeKey(), leg);
                    noteStationLeg(node, leg, grid);

                    if (!grid.instruments().isEmpty()) {
                        capsOfNode.put(node.nodeKey(), grid.instruments());
                    }

                    loadModel.sample(node.nodeKey(), attached.getEnergyStored(),
                            attached.getMaxEnergyStored(), walkTick);
                    demandByNode.put(node.nodeKey(), profileOf(node));
                    loads.add(node);
                    noteBoardNode(node, leg, devicePos);
                }
            }
            collectTerminalBoards(leg, grids, loads, seenDevices, loadHandlers, walkTick);
        }
        wiring = new BranchWiring(conductors, buffered, capacity);

        loadModel.retain(demandByNode.keySet());
        return loads;
    }

    // controller isn't a grid member, so this walk is the only thing that finds it. stud to controller wire carried nothing before
    private void collectTerminalBoards(int leg, List<LegGrid> grids, List<FeGridNode> loads,
                                       java.util.Set<BlockPos> seenDevices,
                                       java.util.Set<IEnergyStorage> loadHandlers, long walkTick) {
        if (level == null || leg < 0 || leg >= com.createstats.power.PduStuds.COUNT) {
            return;
        }
        for (StudLink link : studLinks.get(leg)) {
            if (!level.isLoaded(link.to())) {
                continue;
            }
            final BlockEntity landed = level.getBlockEntity(link.to());

            if (landed instanceof BatteryControllerBE direct) {
                final int port = direct.inputPortLinkedTo(worldPosition);
                if (port >= 0) {
                    addTerminalBoard(direct, port, leg, linkCeiling(leg, link), loads, seenDevices,
                            loadHandlers, walkTick, null, com.createstats.power.FeFlowGraph.NO_BOUNDARY,
                            List.of());
                }
            }
        }

        int rootLinkCeiling = 0;
        for (LegGrid grid : grids) {
            if (!grid.series()) {
                rootLinkCeiling = linkCeilingTo(leg, grid.anchorKey());
            }
            final int boardRate = Math.min(rootLinkCeiling, grid.pathCeiling());
            for (com.createstats.power.FeGridMember gridNode : grid.network().servedMembers()) {
                if (!(gridNode instanceof com.createstats.power.FeWireTerminal terminal)) {
                    continue;
                }
                for (BlockPos far : terminal.links()) {
                    if (!level.isLoaded(far)) {
                        continue;
                    }
                    if (level.getBlockEntity(far) instanceof BatteryControllerBE controller) {
                        final int port = controller.inputPortLinkedTo(terminal.terminalPos());
                        if (port >= 0) {
                            addTerminalBoard(controller, port, leg, boardRate, loads, seenDevices,
                                    loadHandlers, walkTick, grid.network(), gridNode.nodeKey(),
                                    grid.instruments());
                        }
                    }
                }
            }
        }
    }

    private static int linkCeiling(int leg, StudLink link) {
        return com.createstats.power.PduStudWiring.ceiling(studRate(leg),
                PowerConnectorBE.baseTransferRate(), link.tier());
    }

    private int linkCeilingTo(int leg, long anchorKey) {
        for (StudLink link : studLinks.get(leg)) {
            if (link.to().asLong() == anchorKey) {
                return linkCeiling(leg, link);
            }
        }
        return legCeiling(leg);
    }

    private void addTerminalBoard(BatteryControllerBE controller, int port, int leg, int rate,
                                  List<FeGridNode> loads, java.util.Set<BlockPos> seenDevices,
                                  java.util.Set<IEnergyStorage> loadHandlers, long walkTick,
                                  @Nullable FeWireNetwork ownerNetwork, long ownerNodeKey,
                                  List<Long> instruments) {
        final BlockPos at = controller.getBlockPos().immutable();

        final IEnergyStorage terminal = controller.terminalInputHandle(port);
        if (!seenDevices.add(at) || !loadHandlers.add(terminal)) {
            return;
        }
        final FeGridNode node = new com.createstats.power.FeAttachment(at, terminal, Math.max(0, rate));
        legOfNode.put(node.nodeKey(), leg);
        if (ownerNetwork != null && ownerNodeKey != com.createstats.power.FeFlowGraph.NO_BOUNDARY) {
            terminalBoardOwnerByNode.put(node.nodeKey(), ownerNodeKey);
            terminalBoardNetworkByNode.put(node.nodeKey(), ownerNetwork);
        }
        if (!instruments.isEmpty()) {
            capsOfNode.put(node.nodeKey(), instruments);
        }
        loadModel.sample(node.nodeKey(), terminal.getEnergyStored(),
                terminal.getMaxEnergyStored(), walkTick);
        demandByNode.put(node.nodeKey(), profileOf(node));
        loads.add(node);
        terminalBoards.add(at);
        noteBoardNode(node, leg, at);
    }

    private void noteBoardNode(FeGridNode node, int leg, @Nullable BlockPos at) {
        if (at == null || boardPeerOf(node) == null) {
            return;
        }
        boardNodes.add(node.nodeKey());
        branchBoards.add(new BoardLink(node.nodeKey(), at.immutable(), leg));
        if (leg >= 0 && leg < legCarriesBoard.length) {
            legCarriesBoard[leg] = true;
        }
    }

    private final List<BlockPos> terminalBoards = new ArrayList<>();

    private final boolean[] legCarriesBoard = new boolean[com.createstats.power.PduStuds.COUNT];

    private record BoardLink(long nodeKey, BlockPos at, int leg) {
    }

    private final List<BoardLink> branchBoards = new ArrayList<>();

    private final Set<Long> boardNodes = new HashSet<>();

    private int boardFeedAccumulator;
    private int boardFeedRate;

    @Nullable
    private com.createstats.power.FeBoardPeer boardPeerOf(FeGridNode node) {
        if (node instanceof com.createstats.power.FeBoardPeer peer && peer != this) {
            return peer;
        }
        if (node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeBoardPeer peer
                && peer != this) {
            return peer;
        }
        return null;
    }

    private boolean isBoardNode(FeGridNode node) {
        return boardNodes.contains(node.nodeKey());
    }

    private void reportFlowsTo(FeWireNetwork network, long anchorKey, int anchorSupply) {
        gridFlows.clear();
        for (com.createstats.power.FeGridMember member : network.servedMembers()) {
            final Integer fe = branchFlowByNode.get(member.nodeKey());
            if (fe != null && fe != 0) {
                gridFlows.put(member.nodeKey(), fe);
            }
        }
        for (Map.Entry<Long, Long> terminal : terminalBoardOwnerByNode.entrySet()) {
            if (terminalBoardNetworkByNode.get(terminal.getKey()) != network) {
                continue;
            }
            final Integer fe = branchFlowByNode.get(terminal.getKey());
            if (fe != null && fe != 0) {
                gridFlows.merge(terminal.getValue(), fe, Integer::sum);
            }
        }
        reportInstrumentInputFlowsTo(network);
        network.reportManagedFlows(gridFlows, anchorKey, anchorSupply, level.getGameTime());
    }

    private int backLegNetwork = -1;

    private void reportInstrumentInputFlowsTo(FeWireNetwork network) {
        if (instrumentFlowByPos.isEmpty() || level == null) {
            return;
        }
        for (com.createstats.power.FeGridMember member : network.servedMembers()) {
            final BlockPos device = devicePosOf(member);
            if (device == null) {
                continue;
            }
            final Integer fe = instrumentFlowByPos.get(device.asLong());
            if (fe == null || fe <= 0) {
                continue;
            }
            if (!(level.getBlockEntity(device)
                    instanceof com.createstats.power.FeInlineInstrument box)) {
                continue;
            }
            if (network.knowsPosition(device.relative(box.inputFace()))) {
                gridFlows.merge(member.nodeKey(), fe, Integer::sum);
            }
        }
    }

    private final java.util.Map<Long, Integer> gridFlows = new HashMap<>();

    private void cacheInstrumentFlows() {
        instrumentFlowByPos.clear();
        if (branchInstruments.isEmpty() || level == null) {
            return;
        }
        for (long packed : branchInstruments) {
            final BlockPos at = BlockPos.of(packed);
            if (level.getBlockEntity(at) instanceof com.createstats.power.FeInlineInstrument box) {
                final int fe = deliveredBeyond(at, box);
                if (fe > 0) {
                    instrumentFlowByPos.put(packed, fe);
                }
            }
        }
    }

    private void reportInstruments() {
        if (branchInstruments.isEmpty() || level == null) {
            return;
        }
        for (long packed : branchInstruments) {
            final BlockPos at = BlockPos.of(packed);
            if (!(level.getBlockEntity(at) instanceof com.createstats.power.FeInlineInstrument box)) {
                continue;
            }
            box.recordManagedTransfer(instrumentFlowByPos.getOrDefault(packed, 0),
                    level != null && instrumentBudget.clipped(packed, level.getGameTime()));
        }
    }

    private int deliveredBeyond(BlockPos boxPos, com.createstats.power.FeInlineInstrument box) {
        final java.util.List<FeWireNetwork> seen = new ArrayList<>(2);
        final java.util.ArrayDeque<BlockPos> outlets = new java.util.ArrayDeque<>();
        outlets.add(boxPos.relative(box.outputFace()));
        int total = 0;
        while (!outlets.isEmpty() && seen.size() < MAX_LEG_GRIDS) {
            final BlockPos outlet = outlets.poll();
            if (!level.isLoaded(outlet)) {
                continue;
            }
            final FeWireNetwork grid = FeWireNetwork.liveNetworkOf(level.getBlockEntity(outlet));
            if (grid == null || containsIdentity(seen, grid)) {
                continue;
            }
            seen.add(grid);
            for (com.createstats.power.FeGridMember node : grid.servedMembers()) {
                final Integer fe = branchFlowByNode.get(node.nodeKey());
                if (fe != null && fe > 0) {
                    total += fe;
                }

                final BlockPos device = devicePosOf(node);
                if (device != null && branchInstruments.contains(device.asLong())
                        && level.getBlockEntity(device)
                        instanceof com.createstats.power.FeInlineInstrument next
                        && grid.knowsPosition(device.relative(next.inputFace()))) {
                    outlets.add(device.relative(next.outputFace()));
                }
            }

            for (Map.Entry<Long, FeWireNetwork> terminal : terminalBoardNetworkByNode.entrySet()) {
                if (terminal.getValue() == grid) {
                    total += Math.max(0, branchFlowByNode.getOrDefault(terminal.getKey(), 0));
                }
            }
        }
        return total;
    }

    private final java.util.Set<Long> branchInstruments = new HashSet<>();
    private final java.util.Map<Long, Integer> instrumentFlowByPos = new HashMap<>();

    private final java.util.Map<Long, Long> terminalBoardOwnerByNode = new HashMap<>();

    private final java.util.Map<Long, FeWireNetwork> terminalBoardNetworkByNode =
            new java.util.HashMap<>();

    private int strayControllerLegs;

    private int strayPanelStudLegs;
    private int syncedStrayPanelStudLegs;
    private int syncedStrayControllerLegs;

    private void findStrayBoardLegs() {
        strayControllerLegs = 0;
        strayPanelStudLegs = 0;
        if (level == null) {
            return;
        }
        final boolean[] hasDevice = new boolean[LEGS];
        for (BranchDevice device : roster) {
            if (device.leg() >= 0 && device.leg() < LEGS) {
                hasDevice[device.leg()] = true;
            }
        }
        for (int leg = 0; leg < com.createstats.power.PduStuds.COUNT; leg++) {
            if (hasDevice[leg] || studLinks.get(leg).isEmpty()) {
                continue;
            }
            for (StudLink link : studLinks.get(leg)) {
                if (!level.isLoaded(link.to())
                        || !(level.getBlockEntity(link.to())
                        instanceof com.createstats.power.FeWireTerminal terminal)) {
                    continue;
                }
                for (BlockPos far : terminal.links()) {
                    if (!level.isLoaded(far)) {
                        continue;
                    }
                    final BlockEntity be = level.getBlockEntity(far);
                    if (be instanceof BatteryControllerBE controller
                            && controller.hasInputLinkTo(link.to())) {
                        strayControllerLegs |= 1 << leg;
                        break;
                    }

                    if (be instanceof PowerDistributionUnitBE panel && panel != this
                            && panel.hasStudLinkTo(link.to())) {
                        strayPanelStudLegs |= 1 << leg;
                        break;
                    }
                }
            }
        }
        syncedStrayControllerLegs = strayControllerLegs;
        syncedStrayPanelStudLegs = strayPanelStudLegs;
    }

    public boolean hasStudLinkTo(BlockPos terminal) {
        for (int leg = 0; leg < com.createstats.power.PduStuds.COUNT; leg++) {
            for (StudLink link : studLinks.get(leg)) {
                if (link.to().equals(terminal)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void tellDownstreamPanels() {
        if (level == null || downstreamPanels.isEmpty()) {
            return;
        }
        for (BlockPos at : downstreamPanels) {
            if (isBranchBoardAt(at)) {
                continue;
            }
            if (level.getBlockEntity(at) instanceof PowerDistributionUnitBE panel && panel != this) {
                panel.noteUpstreamPanel(worldPosition, Math.max(0, drawShownFor(at)));
            }
        }
    }

    private boolean isBranchBoardAt(BlockPos at) {
        for (BoardLink link : branchBoards) {
            if (link.at().equals(at)) {
                return true;
            }
        }
        return false;
    }

    private int drawShownFor(BlockPos at) {
        for (BranchDevice device : roster) {
            if (device.pos().equals(at)) {
                return device.draw();
            }
        }
        return 0;
    }

    private final List<BlockPos> downstreamPanels = new ArrayList<>();

    private final Map<Long, Long> upstreamPanelUntil = new HashMap<>();
    private int upstreamPanelFeed;
    private int upstreamPanelAccumulator;
    private int syncedUpstreamPanels;
    private int syncedUpstreamPanelFeed;

    public void noteUpstreamPanel(BlockPos panelPos, int fe) {
        if (level == null || level.isClientSide() || panelPos == null) {
            return;
        }
        upstreamPanelUntil.put(panelPos.asLong(), level.getGameTime() + REPORT_GRACE_TICKS);
        upstreamPanelAccumulator += Math.max(0, fe);
    }

    public int upstreamPanelCount() {
        if (level != null && level.isClientSide()) {
            return syncedUpstreamPanels;
        }
        if (level == null || upstreamPanelUntil.isEmpty()) {
            return 0;
        }
        final long now = level.getGameTime();
        upstreamPanelUntil.values().removeIf(until -> now > until);
        return upstreamPanelUntil.size();
    }

    public int upstreamPanelFeed() {
        return level != null && level.isClientSide() ? syncedUpstreamPanelFeed : upstreamPanelFeed;
    }

    public boolean legFeedsControllerInput(int leg) {
        return leg >= 0 && leg < LEGS && (syncedStrayControllerLegs & (1 << leg)) != 0;
    }

    public boolean legFeedsPanelStud(int leg) {
        return leg >= 0 && leg < LEGS && (syncedStrayPanelStudLegs & (1 << leg)) != 0;
    }

    public int firstStrayPanelStudLeg() {
        for (int leg = 0; leg < LEGS; leg++) {
            if (legFeedsPanelStud(leg)) {
                return leg;
            }
        }
        return -1;
    }

    public int firstStrayControllerLeg() {
        for (int leg = 0; leg < LEGS; leg++) {
            if (legFeedsControllerInput(leg)) {
                return leg;
            }
        }
        return -1;
    }

    private int legOf(FeGridNode node) {
        return legOfNode.getOrDefault(node.nodeKey(), 0);
    }

    private static boolean claimPool(IEnergyStorage handler, java.util.Set<Long> claimed) {
        if (!(handler instanceof com.createstats.power.FeReserve pooled)) {
            return true;
        }
        final long key = pooled.reservePoolKey();
        return key == com.createstats.power.FeReserve.NOT_POOLED || claimed.add(key);
    }

    private int drainBranchSources() {
        branchSourceInput = 0;
        legFeedGeneration = 0;
        if (branchSources.isEmpty()) {
            return 0;
        }
        int taken = 0;

        final int[] legPriced = new int[LEGS];
        for (FeGridNode node : branchSources) {
            final int leg = legOf(node);
            if (!isLegEnergised(leg)) {
                continue;
            }

            final List<Long> caps = capsOfNode.get(node.nodeKey());
            if (caps != null && !caps.isEmpty()) {
                continue;
            }
            final IEnergyStorage source = node.attachedStorage();
            if (source == null) {
                continue;
            }

            if (boardPeerOf(node) == null) {
                final int cap = Math.min(node.transferRate(), legRoom(legIntake, leg));
                final BlockEntity attached = node instanceof com.createstats.power.FeGridMember member
                        ? attachedBlockEntity(member) : null;

                final boolean ownGenerator = attached instanceof com.createstats.power.FeForecastingSource fs
                        && !fs.relays()
                        && attached instanceof com.createstats.power.FeSupply;
                final int price = com.createstats.power.PduLegFeed.price(ownGenerator,
                        ownGenerator ? ((com.createstats.power.FeSupply) attached).potentialFePerTick() : 0,
                        ownGenerator ? 0 : source.extractEnergy(cap, true), cap);
                final int before = legPriced[leg];
                legPriced[leg] = com.createstats.power.PduLegFeed.addToLeg(before, price,
                        legCeiling(leg));
                legFeedGeneration = com.createstats.power.FeRates.plus(legFeedGeneration,
                        legPriced[leg] - before);
            }
            final int room = bufferCapacity() - energy - taken;
            if (room <= 0) {
                continue;
            }
            final int offer = Math.min(Math.min(node.transferRate(), legRoom(legIntake, leg)), room);
            if (offer <= 0) {
                continue;
            }
            final int got = source.extractEnergy(offer, false);
            if (got > 0) {
                taken += got;
                spendLeg(legIntake, leg, got);

                branchFlowByNode.merge(node.nodeKey(), -got, Integer::sum);
                if (node instanceof com.createstats.power.FeGridMember member) {
                    member.setLastFlow(got);
                }
            }
        }
        if (taken > 0) {
            energy += taken;
            setChanged();
        }
        branchSourceInput = taken;
        return taken;
    }

    private int dischargeBranchStorage(int shortfall) {
        if (branchStorage.isEmpty() || shortfall <= 0) {
            return 0;
        }
        final List<FeGridNode> order = new ArrayList<>(branchStorage);
        order.sort((a, b) -> {
            final int rankA = FeGridSolver.reserveRankOf(a);
            final int rankB = FeGridSolver.reserveRankOf(b);
            return rankA != rankB ? Integer.compare(rankB, rankA)
                    : Long.compare(a.nodeKey(), b.nodeKey());
        });

        int drawn = 0;
        for (FeGridNode node : order) {
            if (drawn >= shortfall) {
                break;
            }
            final int leg = legOf(node);
            if (!isLegEnergised(leg)) {
                continue;
            }

            final List<Long> caps = capsOfNode.get(node.nodeKey());
            if (caps != null && !caps.isEmpty()) {
                continue;
            }
            final IEnergyStorage tank = node.attachedStorage();
            if (tank == null) {
                continue;
            }
            final int ask = Math.min(Math.min(node.transferRate(), legRoom(legIntake, leg)),
                    shortfall - drawn);
            if (ask <= 0) {
                continue;
            }
            final int got = tank.extractEnergy(ask, false);
            if (got > 0) {
                drawn += got;
                spendLeg(legIntake, leg, got);
                storageFlowByNode.merge(node.nodeKey(), -got, Integer::sum);
                branchFlowByNode.merge(node.nodeKey(), -got, Integer::sum);
                if (node instanceof com.createstats.power.FeGridMember member) {
                    member.setLastFlow(got);
                }
            }
        }
        if (drawn > 0) {
            energy += drawn;
            branchStorageDrawn += drawn;
            setChanged();
        }
        return drawn;
    }

    private int chargeBranchStorage() {
        if (upstreamHoldsCharging()) {
            return 0;
        }
        if (branchStorage.isEmpty()) {
            branchStorageSummary = BranchStorage.NONE;
            return 0;
        }

        int stored = 0;
        int capacity = 0;
        for (FeGridNode node : branchStorage) {
            final IEnergyStorage tank = node.attachedStorage();
            if (tank != null) {
                stored = com.createstats.power.FeRates.plus(stored,
                        Math.max(0, tank.getEnergyStored()));
                capacity = com.createstats.power.FeRates.plus(capacity,
                        Math.max(0, tank.getMaxEnergyStored()));
            }
        }
        branchStorageSummary = new BranchStorage(branchStorage.size(), stored, capacity, 0);

        if (branchStorageDrawn > 0) {
            branchStorageSummary = new BranchStorage(branchStorage.size(), stored, capacity,
                    -branchStorageDrawn);
            return 0;
        }

        final int floorEnergy = Math.round(bufferCapacity() * (reserveFloorPct() / 100.0f));
        int spendable = Math.min(energy - floorEnergy,
                branchThroughputCeiling() - lastBranch.delivered());
        if (spendable <= 0) {
            return 0;
        }

        final List<FeGridNode> order = new ArrayList<>(branchStorage);
        order.sort((a, b) -> {
            final int rankA = FeGridSolver.reserveRankOf(a);
            final int rankB = FeGridSolver.reserveRankOf(b);
            return rankA != rankB ? Integer.compare(rankA, rankB)
                    : Long.compare(a.nodeKey(), b.nodeKey());
        });

        int moved = 0;
        for (FeGridNode node : order) {
            if (spendable <= 0) {
                break;
            }
            final int leg = legOf(node);
            if (!isLegEnergised(leg)) {
                continue;
            }
            final IEnergyStorage tank = node.attachedStorage();
            if (tank == null) {
                continue;
            }

            final int uncapped = Math.min(Math.min(node.transferRate(), legRoom(legOutflow, leg)),
                    spendable);
            final int offer = Math.min(uncapped, capRoomOf(node));
            if (offer <= 0) {
                spendCapOf(node, 0, 0, uncapped);
                continue;
            }
            final int taken = tank.receiveEnergy(offer, false);
            spendCapOf(node, taken, offer, uncapped);
            if (taken > 0) {
                moved += taken;
                spendable -= taken;
                spendLeg(legOutflow, leg, taken);
                storageFlowByNode.merge(node.nodeKey(), taken, Integer::sum);
                branchFlowByNode.merge(node.nodeKey(), taken, Integer::sum);
                if (node instanceof com.createstats.power.FeGridMember member) {
                    member.setLastFlow(taken);
                }
            }
        }
        if (moved > 0) {
            energy = Math.max(0, energy - moved);
            setChanged();
            branchStorageSummary = new BranchStorage(branchStorageSummary.tanks(),
                    branchStorageSummary.stored(), branchStorageSummary.capacity(), moved);
        }
        return moved;
    }

    private int storeGrant;
    private long storeGrantUntil = Long.MIN_VALUE;
    private int storeAsk;
    private int storeCount;
    private int storeStored;
    private int storeCapacity;
    private int storeCharged;

    public void acceptStoreGrant(int fe, long untilTick) {
        if (level == null || level.isClientSide()) {
            return;
        }
        storeGrant = Math.max(0, fe);
        storeGrantUntil = untilTick;
    }

    private boolean storeGrantActive(long now) {
        return storeGrant > 0 && now <= storeGrantUntil;
    }

    public int storeAskFePerTick() {
        return storeAsk;
    }

    public int storeRoomFe() {
        return Math.max(0, storeCapacity - storeStored);
    }

    public int storeCount() {
        return storeCount;
    }

    public int storeStored() {
        return storeStored;
    }

    public int storeCapacity() {
        return storeCapacity;
    }

    public int storeCharged() {
        return storeCharged;
    }

    private int chargeBranchStores(List<FeGridNode> loads, long now, int banksCharged) {
        int count = 0;
        int stored = 0;
        int capacity = 0;
        int ask = 0;
        final List<FeGridNode> stores = new ArrayList<>(1);
        for (FeGridNode node : loads) {
            if (!isBurstSource(node)) {
                continue;
            }
            final IEnergyStorage port = node.attachedStorage();
            if (port == null) {
                continue;
            }
            count++;
            stored = com.createstats.power.FeRates.plus(stored, Math.max(0, port.getEnergyStored()));
            capacity = com.createstats.power.FeRates.plus(capacity, Math.max(0, port.getMaxEnergyStored()));
            final BlockEntity device = node instanceof com.createstats.power.FeGridMember member
                    ? attachedBlockEntity(member) : null;
            final int rated = device instanceof com.createstats.power.FeRatedLoad r
                    ? Math.max(0, r.ratedDrawFePerTick()) : 0;
            final int want = Math.max(0, rated - Math.max(0, branchFlowByNode.getOrDefault(node.nodeKey(), 0)));
            if (want > 0) {
                ask = com.createstats.power.FeRates.plus(ask, want);
                stores.add(node);
            }
        }
        storeCount = count;
        storeStored = stored;
        storeCapacity = capacity;
        storeCharged = 0;

        storeAsk = upstreamHoldsCharging() ? 0 : ask;
        if (upstreamHoldsCharging() || !storeGrantActive(now) || stores.isEmpty()) {
            return 0;
        }
        final int floorEnergy = Math.round(bufferCapacity() * (reserveFloorPct() / 100.0f));
        int spendable = Math.min(Math.min(storeGrant, energy - floorEnergy),
                branchThroughputCeiling() - lastBranch.delivered() - Math.max(0, banksCharged));
        int moved = 0;
        for (FeGridNode node : stores) {
            if (spendable <= 0) {
                break;
            }
            final int leg = legOf(node);
            if (!isLegEnergised(leg)) {
                continue;
            }
            final IEnergyStorage port = node.attachedStorage();
            final int uncapped = Math.min(Math.min(node.transferRate(), legRoom(legOutflow, leg)), spendable);
            final int offer = Math.min(uncapped, capRoomOf(node));
            if (offer <= 0) {
                spendCapOf(node, 0, 0, uncapped);
                continue;
            }
            final int taken = port.receiveEnergy(offer, false);
            spendCapOf(node, taken, offer, uncapped);
            if (taken > 0) {
                moved += taken;
                spendable -= taken;
                spendLeg(legOutflow, leg, taken);
                branchFlowByNode.merge(node.nodeKey(), taken, Integer::sum);
                if (node instanceof com.createstats.power.FeGridMember member) {
                    member.setLastFlow(taken);
                }
            }
        }
        if (moved > 0) {
            energy = Math.max(0, energy - moved);
            setChanged();
        }
        storeCharged = moved;
        return moved;
    }

    public Direction outputFace() {
        return Direction.UP;
    }

    public Direction backFace() {
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return facing.getOpposite();
    }

    public static int backRate() {
        return studRate() * 2;
    }

    public boolean isInputFace(Direction face) {
        if (face == null || face.getAxis() == Direction.Axis.Y) {
            return false;
        }
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return face == facing.getClockWise() || face == facing.getCounterClockWise();
    }

    public static int inputRatePerBoss() {
        return PowerConnectorBE.baseTransferRate() * com.createstats.power.ConnectorTier.INDUSTRIAL.multiplier();
    }

    public Direction inputFace() {
        final Direction facing = getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return facing.getClockWise();
    }

    @Nullable
    private BlockEntity attachedBlockEntity(com.createstats.power.FeGridMember node) {
        if (level == null) {
            return null;
        }

        if (node instanceof com.createstats.power.FeAttachment attachment) {
            return level.getBlockEntity(attachment.machine());
        }

        if (node instanceof PowerConnectorBE connector) {
            final Direction facing = connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                    ? connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING)
                    : Direction.UP;
            return level.getBlockEntity(connector.getBlockPos().relative(facing.getOpposite()));
        }

        final com.createstats.blockentity.CableBE cable =
                node instanceof com.createstats.blockentity.CableBE direct ? direct : null;
        if (cable != null) {
            for (Direction dir : Direction.values()) {
                final BlockEntity be = level.getBlockEntity(cable.getBlockPos().relative(dir));
                if (be instanceof KineticDynamoBE || be instanceof CapacitorBankBE) {
                    return be;
                }
            }
        }
        if (node instanceof BlockEntity be) {
            return be;
        }
        return null;
    }

    public int backExportFePerTick() {
        return backExport;
    }

    @Override
    public int potentialFePerTickIn(int ticks) {
        final int export = potentialFePerTick();
        if (ticks <= 0 || export <= 0) {
            return export;
        }
        return com.createstats.power.FeForecastRelay.export(export, expectedSupplyDropFePerTick());
    }

    @Override
    public int relayedWeatherShareFePerTick() {
        final int legShare = Math.min(legForecast.weatherSensitiveNow(), Math.max(0, legFeedGeneration));
        return com.createstats.power.FeForecastRelay.exportWeatherShare(potentialFePerTick(),
                com.createstats.power.FeRates.plus(survey.weatherSensitiveNow(), legShare));
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public int potentialFePerTick() {
        return Math.max(0, backExport);
    }

    public int measuredInputFePerTick() {
        return measuredInput;
    }

    public int generationFePerTick() {
        return lastGeneration;
    }

    public int branchDemandFePerTick() {
        return lastDemand;
    }

    public int budgetFePerTick() {
        return lastBudget;
    }

    public int servedDemandFePerTick() {
        return lastServedDemand;
    }

    public int autoEnduranceTicks() {
        return lastEnduranceTicks;
    }

    public boolean isSurplus() {
        return lastSurplus;
    }

    public boolean isThroughputLimited() {
        return lastThroughputLimited;
    }

    public int branchCeilingFePerTick() {
        return lastCeiling;
    }

    private final Map<Long, com.createstats.power.FeReserveReport> reserveReports = new HashMap<>();

    private final Map<Long, Long> reserveReportUntil = new HashMap<>();

    private com.createstats.power.FeReserveReport combinedReport =
            com.createstats.power.FeReserveReport.NONE;
    private long combinedReportTick = Long.MIN_VALUE;

    private static final int REPORT_GRACE_TICKS = 10;

    private com.createstats.power.FeBoardDeal.Request currentRequest =
            com.createstats.power.FeBoardDeal.Request.NONE;

    private final Map<Long, com.createstats.power.FeBoardDeal.Answer> boardAnswers = new HashMap<>();

    private com.createstats.power.FeBoardDeal.Answer lastDeal = com.createstats.power.FeBoardDeal.Answer.NONE;

    @Nullable
    private com.createstats.power.FeReserveReport.Stance ownBankStance;

    private List<FeWireNetwork> feedNetworks = List.of();

    private int rawControllerGeneration;

    private int[] syncedDeal = new int[0];

    public com.createstats.power.FeBoardDeal.Request boardRequest() {
        return currentRequest;
    }

    public com.createstats.power.FeBoardDeal.Request boardRequest(long controllerPos) {
        if (level == null) {
            return currentRequest;
        }
        final long now = level.getGameTime();
        final List<Long> sources = new ArrayList<>(2);
        final List<Integer> capacity = new ArrayList<>(2);
        long known = 0;
        int knownCount = 0;
        for (Map.Entry<Long, com.createstats.power.FeBoardDeal.Answer> e : boardAnswers.entrySet()) {
            final com.createstats.power.FeBoardDeal.Answer a = e.getValue();
            if (a.untilTick() < now) {
                continue;
            }
            final int could = com.createstats.power.FeRates.plus(a.total(), a.spare());
            sources.add(e.getKey());
            capacity.add(could);
            known += could;
            knownCount++;
        }
        if (!sources.contains(controllerPos)) {
            sources.add(controllerPos);
            capacity.add(-1);
        }
        if (sources.size() == 1) {
            return currentRequest;
        }
        final int average = knownCount == 0 ? 1 : (int) Math.max(1L, known / knownCount);
        final int[] weights = new int[sources.size()];
        for (int i = 0; i < weights.length; i++) {
            final int w = capacity.get(i);
            weights[i] = w < 0 ? average : w;
        }
        return com.createstats.power.FeBoardDeal.splitRequest(currentRequest, weights)[sources.indexOf(controllerPos)];
    }

    public void acceptBoardAnswer(com.createstats.power.FeBoardDeal.Answer answer) {
        if (answer == null || level == null || level.isClientSide()) {
            return;
        }
        boardAnswers.put(answer.sourcePos(), answer);
    }

    private com.createstats.power.FeBoardDeal.Answer controllerAnswer(long now) {
        boardAnswers.values().removeIf(a -> a.untilTick() < now);
        return com.createstats.power.FeBoardDeal.combine(boardAnswers.values());
    }

    private final com.createstats.power.FeReserveBand.WantPeak bankWant =
            new com.createstats.power.FeReserveBand.WantPeak();

    private com.createstats.power.FeReserveBand.Band sizedBankBand(int capacity, int fullRate) {
        return com.createstats.power.FeReserveBand.sized(BatteryControllerBE.band(), BatteryControllerBE.bandMinutes(),
                capacity, bankWant.want(), fullRate, BatteryControllerBE.rideTicks());
    }

    @Nullable
    private com.createstats.power.FeBoardDeal.Answer ownBankAnswer(long now, int gen, int ceiling,
                                                                   com.createstats.power.FeBoardDeal.Request request) {
        final int stored = com.createstats.power.FeRates.plus(Math.max(0, survey.reserveTotal()),
                Math.max(0, branchTankStored));
        final int capacity = com.createstats.power.FeRates.plus(Math.max(0, survey.reserveCapacity()),
                Math.max(0, branchTankCapacity));
        if (capacity <= 0) {
            ownBankStance = null;
            ownHoldApplied = false;
            return null;
        }
        final int fullRate = Math.max(1, ceiling);
        final com.createstats.power.FeReserveReport own = com.createstats.power.FeReserveBand.assess(
                worldPosition.asLong(), new com.createstats.power.FeReserveBand.Params(stored, capacity,
                        gen, 0, fullRate, fullRate,

                        ownBankBand(capacity, fullRate),
                        BatteryControllerBE.rideTicks(), ownBankStance,

                        bankWant.want()));
        ownBankStance = own.stance();
        ownHoldApplied = ownHoldFe > 0 && ownBankBand(capacity, fullRate).floorPct()
                > sizedBankBand(capacity, fullRate).floorPct();
        return com.createstats.power.FeBoardDeal.split(worldPosition.asLong(), true,
                com.createstats.power.FeBoardDeal.Offer.of(own, 0, upstreamHoldsCharging()),
                List.of(request), null, null, now + REPORT_GRACE_TICKS).get(0);
    }

    @Nullable
    private com.createstats.power.FeReserveReport.Stance legBankStance;

    @Nullable
    private com.createstats.power.FeBoardDeal.Answer legBankAnswer(long now, int ceiling,
                                                                  com.createstats.power.FeBoardDeal.Request left) {
        if (branchTankCapacity <= 0 || left.total() <= 0) {
            legBankStance = null;
            return null;
        }
        final int fullRate = Math.max(1, ceiling);
        final com.createstats.power.FeReserveReport legs = com.createstats.power.FeReserveBand.assess(
                worldPosition.asLong(), new com.createstats.power.FeReserveBand.Params(
                        Math.max(0, branchTankStored), branchTankCapacity, 0, 0,
                        fullRate, fullRate,

                        sizedBankBand(branchTankCapacity, fullRate),
                        BatteryControllerBE.rideTicks(), legBankStance, bankWant.want()));
        legBankStance = legs.stance();
        return com.createstats.power.FeBoardDeal.split(worldPosition.asLong(), true,
                com.createstats.power.FeBoardDeal.Offer.of(legs, 0, upstreamHoldsCharging()),
                List.of(left), null, null, now + REPORT_GRACE_TICKS).get(0);
    }

    public com.createstats.power.FeBoardDeal.Answer dealShown() {
        if (level != null && level.isClientSide()) {
            final int[] d = syncedDeal;
            if (d.length < 11 || d[0] == 0) {
                return com.createstats.power.FeBoardDeal.Answer.NONE;
            }
            final com.createstats.power.FeBoardDeal.Reason[] reasons = com.createstats.power.FeBoardDeal.Reason.values();
            final com.createstats.power.FeReserveReport.Stance[] stances =
                    com.createstats.power.FeReserveReport.Stance.values();
            return new com.createstats.power.FeBoardDeal.Answer(0L, d[1] != 0,
                    new com.createstats.power.FeBoardDeal.Request(worldPosition.asLong(), d[2], d[3], d[4]),
                    d[5], d[6], d[7], reasons[Math.floorMod(d[8], reasons.length)],
                    stances[Math.floorMod(d[9], stances.length)], d[10], Long.MAX_VALUE,
                    d.length > 11 ? d[11] : 0);
        }
        return lastDeal;
    }

    private int feedStorageIntake(int intake) {
        int discharged = 0;
        int collected = 0;
        for (FeWireNetwork feed : feedNetworks) {
            final FeGridSolver.Result r = feed.lastResult();
            if (r != null) {
                discharged = com.createstats.power.FeRates.plus(discharged, Math.max(0, r.discharged()));
                collected = com.createstats.power.FeRates.plus(collected, Math.max(0, r.collected()));
            }
        }
        return com.createstats.power.FeBoardDeal.fromStorage(intake, discharged, collected);
    }

    private int[] dealTag() {
        final com.createstats.power.FeBoardDeal.Answer a = lastDeal;
        if (!a.isPresent()) {
            return new int[] {0};
        }
        return new int[] {1, a.own() ? 1 : 0, a.asked().critical(), a.asked().running(),
                a.asked().restore(), a.critical(), a.running(), a.restore(), a.reason().ordinal(),
                a.stance().ordinal(), a.socPct(), a.spare()};
    }

    private record SyncedLink(int socPct, int stanceOrdinal, int sustainedDraw, int generation,
                              int floorPct, int ceilingPct, int criticalDraw, int chargeClaim,
                              int stored, int capacity) {
        com.createstats.power.FeReserveReport.Stance stance() {
            final com.createstats.power.FeReserveReport.Stance[] all =
                    com.createstats.power.FeReserveReport.Stance.values();
            return stanceOrdinal >= 0 && stanceOrdinal < all.length ? all[stanceOrdinal]
                    : com.createstats.power.FeReserveReport.Stance.CONSERVE;
        }
    }

    @Nullable
    private SyncedLink syncedLink;

    private int syncedLinkCount;

    public boolean isReserveLinked() {
        return level != null && level.isClientSide() ? syncedLink != null : hasReserveLink();
    }

    public int linkedReserveSocPct() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? -1 : syncedLink.socPct();
        }
        return hasReserveLink() ? reserveReport().socPct() : -1;
    }

    @Nullable
    public com.createstats.power.FeReserveReport.Stance linkedStance() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? null : syncedLink.stance();
        }
        return hasReserveLink() ? reserveReport().stance() : null;
    }

    public int linkedSustainedDraw() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.sustainedDraw();
        }
        return reserveReport().sustainedDraw();
    }

    public int linkedBandFloorPct() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.floorPct();
        }
        return reserveReport().floorPct();
    }

    public int linkedBandCeilingPct() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.ceilingPct();
        }
        return reserveReport().ceilingPct();
    }

    public int linkedGeneration() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.generation();
        }
        return reserveReport().generation();
    }

    public int linkedCriticalDraw() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.criticalDraw();
        }
        return reserveReport().criticalDraw();
    }

    public int linkedChargeClaim() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.chargeClaim();
        }
        return reserveReport().chargeClaim();
    }

    public int linkedReserveStored() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.stored();
        }
        return reserveReport().stored();
    }

    public int linkedReserveCapacity() {
        if (level != null && level.isClientSide()) {
            return syncedLink == null ? 0 : syncedLink.capacity();
        }
        return reserveReport().capacity();
    }

    public void acceptReserveReport(com.createstats.power.FeReserveReport report) {
        if (report == null || level == null || level.isClientSide()) {
            return;
        }
        final long key = report.controllerPos();
        reserveReports.put(key, report);
        reserveReportUntil.put(key, level.getGameTime() + REPORT_GRACE_TICKS);
        combinedReportTick = Long.MIN_VALUE;
    }

    private int outlookShortTicks = -1;
    private int outlookSettlePct = -1;
    private long outlookTick = Long.MIN_VALUE;
    private long outlookUntil = Long.MIN_VALUE;
    private int syncedOutlookShort = -1;
    private int syncedOutlookSettle = -1;
    private int syncedLinkRate;

    public void acceptReserveOutlook(int shortInTicks, int settlePct, long untilTick) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (outlookTick != now) {
            outlookTick = now;
            outlookShortTicks = shortInTicks;
            outlookSettlePct = settlePct;
        } else {
            outlookShortTicks = outlookShortTicks < 0 ? shortInTicks
                    : shortInTicks < 0 ? outlookShortTicks : Math.min(outlookShortTicks, shortInTicks);
            outlookSettlePct = outlookSettlePct < 0 ? settlePct
                    : settlePct < 0 ? outlookSettlePct : Math.min(outlookSettlePct, settlePct);
        }
        outlookUntil = untilTick;
    }

    public int linkedShortInTicks() {
        if (level != null && level.isClientSide()) {
            return syncedOutlookShort;
        }
        return level != null && level.getGameTime() <= outlookUntil ? outlookShortTicks : -1;
    }

    public int linkedSettlePct() {
        if (level != null && level.isClientSide()) {
            return syncedOutlookSettle;
        }
        return level != null && level.getGameTime() <= outlookUntil ? outlookSettlePct : -1;
    }

    public int linkedReserveRate() {
        if (level != null && level.isClientSide()) {
            return syncedLinkRate;
        }
        return reserveReport().netRate();
    }

    public com.createstats.power.FeReserveReport reserveReport() {
        if (level == null) {
            return combinedReport;
        }
        final long now = level.getGameTime();
        if (now == combinedReportTick) {
            return combinedReport;
        }
        combinedReportTick = now;
        if (reserveReports.isEmpty()) {
            combinedReport = com.createstats.power.FeReserveReport.NONE;
            return combinedReport;
        }

        reserveReportUntil.entrySet().removeIf(entry -> {
            if (now <= entry.getValue()) {
                return false;
            }
            reserveReports.remove(entry.getKey());
            return true;
        });
        combinedReport = com.createstats.power.FeReserveReport.combine(reserveReports.values());
        return combinedReport;
    }

    private final Map<Long, Long> controllerDrawUntil = new HashMap<>();

    private int controllerDrawAccumulator;
    private int controllerDraw;
    private int syncedControllerCount;
    private int syncedControllerDraw;

    private final Map<Long, Integer> controllerDrawAccumulatorByPos = new HashMap<>();
    private final Map<Long, Integer> controllerDrawByPos = new HashMap<>();

    private final Map<Long, com.createstats.power.FeReserveReport> downstreamReports = new HashMap<>();
    private final Map<Long, Long> downstreamReportUntil = new HashMap<>();
    private int syncedDownstreamSocPct = -1;

    public void noteControllerDraw(BlockPos controllerPos, int fe) {
        if (level == null || level.isClientSide() || controllerPos == null || fe <= 0) {
            return;
        }
        controllerDrawUntil.put(controllerPos.asLong(), level.getGameTime() + REPORT_GRACE_TICKS);
        controllerDrawAccumulator += fe;
        controllerDrawAccumulatorByPos.merge(controllerPos.asLong(), fe, Integer::sum);
    }

    public void noteBoardFeed(BlockPos controllerPos, int fe) {
        if (level == null || level.isClientSide() || controllerPos == null || fe <= 0) {
            return;
        }
        controllerDrawUntil.put(controllerPos.asLong(), level.getGameTime() + REPORT_GRACE_TICKS);
        controllerDrawAccumulatorByPos.merge(controllerPos.asLong(), fe, Integer::sum);
    }

    public void noteDownstreamReserve(com.createstats.power.FeReserveReport report) {
        if (report == null || level == null || level.isClientSide()) {
            return;
        }
        downstreamReports.put(report.controllerPos(), report);
        downstreamReportUntil.put(report.controllerPos(), level.getGameTime() + REPORT_GRACE_TICKS);
    }

    private final Map<Long, com.createstats.power.FeExportReport> upstreamExportReports =
            new HashMap<>();
    private final Map<Long, Long> upstreamExportUntil = new HashMap<>();

    @Override
    public void acceptExportReport(com.createstats.power.FeExportReport report) {
        if (report == null || !report.isPresent() || level == null || level.isClientSide()
                || report.panelPos() == worldPosition.asLong()) {
            return;
        }
        upstreamExportReports.put(report.panelPos(), report);
        upstreamExportUntil.put(report.panelPos(), level.getGameTime() + REPORT_GRACE_TICKS);
    }

    @Nullable
    public com.createstats.power.FeExportReport upstreamHoldingReport() {
        if (level == null || upstreamExportReports.isEmpty()) {
            return null;
        }
        final long now = level.getGameTime();
        upstreamExportUntil.entrySet().removeIf(entry -> now > entry.getValue());
        upstreamExportReports.keySet().retainAll(upstreamExportUntil.keySet());
        com.createstats.power.FeExportReport worst = null;
        for (com.createstats.power.FeExportReport report : upstreamExportReports.values()) {
            if (!report.holdCharging()) {
                continue;
            }
            if (worst == null || report.stance().ordinal() > worst.stance().ordinal()) {
                worst = report;
            }
        }
        return worst;
    }

    public boolean upstreamHoldsCharging() {
        return upstreamHoldingReport() != null;
    }

    @Override
    public com.createstats.power.FeReserveReport boardReserveReport() {
        final int capacity = bufferCapacity();
        return new com.createstats.power.FeReserveReport(worldPosition.asLong(), energy, capacity,
                Math.max(0, lastGeneration), measuredInput - lastBranch.delivered(),
                Math.max(0, lastServedDemand), Math.max(0, declaredCriticalDemandFePerTick()),
                0, Math.max(0, widestExportConductor()), effectiveReserveFloorPct(),
                Math.round(restoreSocFraction() * 100.0f),
                com.createstats.power.PduExportPolicy.stanceOf(energy, capacity,
                        effectiveReserveFloorPct(), Math.round(restoreSocFraction() * 100.0f),
                        mode == PduMode.AUTO && lastScreenedShed > 0));
    }

    @Override
    public void notePushFrom(BlockPos feeder, int fe) {
        if (feeder == null || level == null || level.isClientSide()) {
            return;
        }
        noteUpstreamPanel(feeder, Math.max(0, fe));
    }

    @Override
    public void acceptHoldTarget(int holdFe, long untilTick) {
        acceptHoldTarget(com.createstats.power.FeHoldBook.UNNAMED, holdFe, untilTick);
    }

    @Override
    public void acceptHoldTarget(long fromPos, int holdFe, long untilTick) {
        if (level == null || level.isClientSide()) {
            return;
        }
        holdBook.accept(fromPos, Math.max(0, holdFe), untilTick);
    }

    public int holdTargetFe() {
        if (level != null && level.isClientSide()) {
            return syncedHoldFe;
        }
        return level == null ? 0 : holdBook.largest(level.getGameTime()).fe();
    }

    public com.createstats.gui.pdu.PduFlowModel.Budgets decisionBudgets() {
        return new com.createstats.gui.pdu.PduFlowModel.Budgets(lastBudget, lastAdmitBudget,
                lastCriticalBudget, lastSupplyDrop, lastStanding, lastChargeClaimUsed, lastRideBudget,
                lastBelowFloor, lastLinkedDecision, lastSurplus, lastThroughputLimited, lastCeiling);
    }

    public com.createstats.gui.pdu.PduFlowModel.SupplyParts supplyBreakdown() {
        if (level != null && level.isClientSide()) {
            return new com.createstats.gui.pdu.PduFlowModel.SupplyParts(syncedSupPriced,
                    syncedSupWeather, syncedSupObserved, syncedSupLinked, syncedSupLegs,
                    syncedSupAllPriced, syncedSupControllerLinked);
        }

        final boolean controllerLinked = hasReserveLink();
        final int priced = controllerLinked ? Math.max(0, rawGeneration - rawControllerGeneration)
                : Math.max(0, rawGeneration);
        return new com.createstats.gui.pdu.PduFlowModel.SupplyParts(priced,
                Math.max(0, survey.weatherSensitiveNow()), Math.max(0, observedInput),
                Math.max(0, reserveReport().generation() - circularGeneration()),
                Math.max(0, legFeedGeneration), surveyPricedEverySource(), controllerLinked);
    }

    private boolean surveyPricedEverySource() {
        return survey.sourceCount() > 0 && survey.unpricedSources() == 0;
    }

    public int forecastDropFePerTick() {
        return level != null && level.isClientSide() ? syncedForecastDrop : expectedSupplyDropFePerTick();
    }

    public com.createstats.gui.pdu.PduDayOutlook dayOutlook() {
        final com.createstats.power.SolarCurve.Sky[] skies = com.createstats.power.SolarCurve.Sky.values();
        if (level != null && level.isClientSide()) {
            return new com.createstats.gui.pdu.PduDayOutlook(syncedSeriesStep, syncedSeriesRate,
                    syncedSeriesClear, syncedSeriesCold, syncedSeriesDayTime, syncedOutlookCritical,
                    syncedOutlookShortfall, syncedOutlookCertain, syncedOwnHoldFe, syncedOwnHeld,
                    syncedPeerHoldFe, syncedDayWarmBuckets, skies[Math.floorMod(syncedWeatherNow, skies.length)],
                    syncedWeatherIn, skies[Math.floorMod(syncedWeatherThen, skies.length)]);
        }
        return new com.createstats.gui.pdu.PduDayOutlook(outlookSeries.stepTicks(), outlookSeries.rate(),
                outlookSeries.clear(), outlookSeries.coldMask(), outlookSeriesDayTime,
                outlook.neededFe() <= 0 ? 0 : outlook.neededFe() / OUTLOOK_TICKS, outlook.shortfallFe(),
                outlook.certain(), ownHoldFe, ownHoldApplied, peerHoldFe, supplyMemory.warmBuckets(),
                outlookWeather.now(), outlookWeather.ticksToChange(), outlookWeather.then());
    }

    public int outlookShortfallFe() {
        return level != null && level.isClientSide() ? syncedOutlookShortfall : outlook.shortfallFe();
    }

    public int effectiveFloorPctShown() {
        return level != null && level.isClientSide() ? syncedEffectiveFloorPct : effectiveReserveFloorPct();
    }

    private int effectiveReserveFloorPct() {
        final int configured = reserveFloorPct();
        final int fe = holdTargetFe();
        if (fe <= 0 || bufferCapacity() <= 0) {
            return configured;
        }
        final long pct = (long) fe * 100L / bufferCapacity();
        return (int) Math.max(configured,
                Math.min(com.createstats.power.FeSupplyOutlook.MAX_HOLD_PCT, pct));
    }

    private void snapshotControllerDraws() {
        controllerDrawByPos.clear();
        controllerDrawByPos.putAll(controllerDrawAccumulatorByPos);
        controllerDrawAccumulatorByPos.clear();
        if (level != null && !downstreamReportUntil.isEmpty()) {
            final long now = level.getGameTime();
            downstreamReportUntil.entrySet().removeIf(entry -> {
                if (now <= entry.getValue()) {
                    return false;
                }
                downstreamReports.remove(entry.getKey());
                return true;
            });
        }
        syncedDownstreamSocPct = downstreamReports.isEmpty() ? -1
                : com.createstats.power.FeReserveReport.combine(downstreamReports.values()).socPct();
    }

    private int circularGeneration() {
        if (controllerDrawByPos.isEmpty() || reserveReports.isEmpty()) {
            return 0;
        }
        int circular = 0;
        for (Map.Entry<Long, Integer> drawn : controllerDrawByPos.entrySet()) {
            final com.createstats.power.FeReserveReport upstream = reserveReports.get(drawn.getKey());
            if (upstream != null) {
                circular += Math.min(Math.max(0, drawn.getValue()), upstream.generation());
            }
        }
        return circular;
    }

    public boolean isExportHeld() {
        if (level != null && level.isClientSide()) {
            return syncedExportHeld;
        }
        if (!exportPolicyEnabled()) {
            return false;
        }
        refillExportBudget(level == null ? 0L : level.getGameTime());
        return exportBudget.isHeld(widestExportConductor())
                && downstreamControllerCount() + backExport + boardFeedRate > 0;
    }

    private boolean syncedExportHeld;

    public int downstreamReserveSocPct() {
        if (level != null && level.isClientSide()) {
            return syncedDownstreamSocPct;
        }
        return downstreamReports.isEmpty() ? -1
                : com.createstats.power.FeReserveReport.combine(downstreamReports.values()).socPct();
    }

    public int downstreamControllerCount() {
        if (level != null && level.isClientSide()) {
            return syncedControllerCount;
        }
        if (level == null || controllerDrawUntil.isEmpty()) {
            return 0;
        }
        final long now = level.getGameTime();
        controllerDrawUntil.values().removeIf(until -> now > until);
        return controllerDrawUntil.size();
    }

    public int downstreamControllerDraw() {
        return level != null && level.isClientSide() ? syncedControllerDraw : controllerDraw;
    }

    public int reserveLinkCount() {
        if (level != null && level.isClientSide()) {
            return syncedLinkCount;
        }
        reserveReport();
        return reserveReports.size();
    }

    public boolean hasReserveLink() {
        return reserveReport().isPresent();
    }

    public int declaredDemandFePerTick() {
        final int demand = mode == PduMode.AUTO ? lastScreenedDemand : currentRosterDemand();

        return Math.max(0, demand) + upkeepFePerTick() + Math.max(0, exportDemand);
    }

    public int exportDemandFePerTick() {
        return exportDemand;
    }

    @Override
    public int declaredCriticalDemandFePerTick() {
        if (mode == PduMode.AUTO && lastCriticalDemand >= 0) {
            return lastCriticalDemand;
        }
        int critical = 0;
        for (BranchDevice device : roster()) {
            if (device.priority() == FePriority.CRITICAL && device.lock() != FeLock.FORCE_OFF) {
                critical += Math.max(0, device.cost());
            }
        }
        return critical;
    }

    private int lastBoardDemand;

    private int lastBurstRelief;

    public int burstReliefFePerTick() {
        return lastBurstRelief;
    }

    private int lastCriticalDemand = -1;

    public float reserveFloorFraction() {
        return syncedFloorPct / 100.0f;
    }

    private FePriority upstreamTier = FePriority.DEFAULT;

    private boolean upstreamTierChosen;

    private FeLock upstreamLock = FeLock.NONE;

    private long managedShedUntil = Long.MIN_VALUE;

    private static final int SHED_GRACE_TICKS = REPORT_GRACE_TICKS;

    @Override
    public int ratedDrawFePerTick() {
        if (upstreamLock == FeLock.FORCE_OFF) {
            return 0;
        }
        return Math.max(0, declaredDemandFePerTick());
    }

    public int intakeCeilingFePerTick() {
        if (upstreamLock == FeLock.FORCE_OFF) {
            return 0;
        }
        return Math.max(0, Math.min(inputRatePerBoss(), bufferCapacity() - energy));
    }

    @Override
    public int measuredDrawFePerTick() {
        return Math.max(0, measuredInput);
    }

    @Override
    public FePriority managedPriority() {
        if (upstreamTierChosen) {
            return upstreamTier;
        }
        final FePriority carried = branchTopSheddablePriority();
        return carried.ordinal() < upstreamTier.ordinal() ? carried : upstreamTier;
    }

    public FePriority branchTopSheddablePriority() {
        FePriority top = FePriority.DEFAULT;
        for (BranchDevice device : roster) {
            if (device.isStorage() || device.lock() == FeLock.FORCE_OFF
                    || device.priority() == FePriority.CRITICAL) {
                continue;
            }
            if (device.priority().ordinal() < top.ordinal()) {
                top = device.priority();
            }
        }
        return top;
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        if (tier != null && (tier != upstreamTier || !upstreamTierChosen)) {
            upstreamTier = tier;

            upstreamTierChosen = true;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    @Override
    public FeLock managedLock() {
        return upstreamLock;
    }

    @Override
    public void setManagedLock(FeLock lock) {
        if (lock != null && lock != upstreamLock) {
            upstreamLock = lock;
            if (lock == FeLock.NONE) {
                managedShedUntil = Long.MIN_VALUE;
            }
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (level == null || level.isClientSide()) {
            return;
        }
        managedShedUntil = shed ? level.getGameTime() + SHED_GRACE_TICKS : Long.MIN_VALUE;
    }

    @Override
    public boolean isManagedShed() {
        if (upstreamLock == FeLock.FORCE_OFF) {
            return true;
        }
        if (level != null && level.isClientSide()) {
            return syncedManagedShed;
        }
        return level != null && level.getGameTime() <= managedShedUntil;
    }

    private boolean syncedManagedShed;

    public boolean isShedFromUpstream() {
        return isManagedShed();
    }

    public FeLock upstreamLock() {
        return upstreamLock;
    }

    public int reserveTankCount() {
        return syncedTankCount;
    }

    public int reserveDepth() {
        return syncedReserveDepth;
    }

    public int reserveStored() {
        return syncedReserveTotal;
    }

    public int reserveCapacity() {
        return syncedReserveCapacity;
    }

    public int reserveCoreRank() {
        return syncedCoreRank;
    }

    public boolean isCoreProtected() {
        return syncedCoreOnly;
    }

    public int upstreamSourceCount() {
        return syncedSourceCount;
    }

    public boolean foreignFeedMisplaced() {
        return syncedFeedMisplaced;
    }

    public float reserveFillFraction() {
        return syncedReserveCapacity <= 0 ? 0.0f
                : Math.min(1.0f, syncedReserveTotal / (float) syncedReserveCapacity);
    }

    public int measuredGeneration() {
        final int reserveGen = Math.max(0, reserveReport().generation() - circularGeneration());

        return com.createstats.power.FeRates.plus(Math.max(Math.max(rawGeneration, observedInput),
                reserveGen), legFeedGeneration);
    }

    private void publishManagerView(long now) {
        if (managedNetworks.isEmpty()) {
            return;
        }
        if (mode != PduMode.AUTO) {
            readUpstreamIfDue(now);
        }
        final com.createstats.power.FeReserveReport report = reserveReport();
        final int upstreamStored = report.isPresent() ? report.stored() : survey.reserveTotal();
        final int upstreamCapacity = report.isPresent() ? report.capacity() : survey.reserveCapacity();

        final FeManagerView view = FeManagerView.leg(
                com.createstats.power.FeRates.plus(branchTankStored, upstreamStored),
                com.createstats.power.FeRates.plus(branchTankCapacity, upstreamCapacity),
                upstreamHoldsCharging(), lastBurstRelief).withStoresFunded(storeGrantActive(now));

        final FeManagerView capped = view.refusingSources();
        for (FeWireNetwork network : managedNetworks) {
            network.noteManagerView(containsIdentity(cappedNetworks, network) ? capped : view, now);
        }
    }

    private void readUpstreamIfDue(long now) {
        if (lastSupplyReadTick == NEVER || now - lastSupplyReadTick >= supplyReadIntervalTicks()) {
            lastSupplyReadTick = now;
            readUpstream();
        }
    }

    private void readUpstream() {
        final com.createstats.power.PduSupplySurvey.Builder builder =
                new com.createstats.power.PduSupplySurvey.Builder();
        final Direction out = outputFace();
        final List<FeWireNetwork> counted = new ArrayList<>(2);
        rawControllerGeneration = 0;

        final java.util.Set<Integer> foreignFeeds = new java.util.HashSet<>(2);

        for (Direction face : Direction.values()) {
            if (face == out || !isInputFace(face)) {
                continue;
            }
            if (level != null && level.getBlockEntity(worldPosition.relative(face))
                    instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler
                    && coupler.mode() == com.createstats.power.PowerCouplerMode.PULL
                    && coupler.gridFace() == face.getOpposite()) {
                com.createstats.power.NewAgeFeed.surveyCouplerSeam(level, coupler.getBlockPos(),
                        coupler.foreignFace(), builder, foreignFeeds);
            }
        }

        for (Direction face : Direction.values()) {
            if (face == out || !isInputFace(face)) {
                continue;
            }
            final FeWireNetwork network = feedNetworkOn(face);
            if (network == null) {
                continue;
            }

            if (containsIdentity(managedNetworks, network)) {
                continue;
            }
            boolean alreadyCounted = false;
            for (FeWireNetwork seen : counted) {
                if (seen == network) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (alreadyCounted) {
                continue;
            }
            counted.add(network);
            for (com.createstats.power.FeGridMember node : network.members()) {
                surveyMember(builder, node, foreignFeeds);
            }
        }

        final com.createstats.power.PduSupplySurvey.Result read = builder.build();

        supplyShock = surveyPrimed && read.sourceSignature() != lastSourceSignature;
        lastSourceSignature = read.sourceSignature();
        surveyPrimed = true;
        survey = read;
        feedNetworks = List.copyOf(counted);
        rawGeneration = read.knownGeneration();
        rawBankCharging = read.bankCharging();
        legForecast = surveyLegForecasts();

        final int legLearned = Math.min(legForecast.knownGeneration(), Math.max(0, legFeedGeneration));
        learnedGeneration = com.createstats.power.FeRates.plus(read.knownGeneration(), legLearned);

        if (level != null) {
            if (!level.isRaining() && !level.isThundering()) {
                supplyMemory.observe(level.getDayTime(), learnedGeneration);
                weatherShareMemory.observe(level.getDayTime(), Math.min(learnedGeneration,
                        com.createstats.power.FeRates.plus(read.weatherSensitiveNow(),
                                Math.min(legForecast.weatherSensitiveNow(), legLearned))));
            }

            outlookWeather = currentWeather();
            lastProjectedFe = supplyMemory.isWarm()
                    ? com.createstats.power.FeSupplyOutlook.project(supplyMemory, weatherShareMemory,
                            unpricedSupplyFePerTick(), level.getDayTime(), PROJECTION_TICKS,
                            outlookWeather, 0).projectedFe()
                    : 0;
            outlook = projectSupply();

            outlookSeries = com.createstats.power.FeSupplyOutlook.series(supplyMemory, weatherShareMemory,
                    unpricedSupplyFePerTick(), level.getDayTime(), OUTLOOK_TICKS, SERIES_STEP_TICKS,
                    outlookWeather);
            outlookSeriesDayTime = level.getDayTime();
            splitNightHold();
        }

        if (supplyShock) {
            if (read.sourceCount() < lastSourceCount) {
                observedInput = measuredInput;
            }

            smoothGen = measuredGeneration();
        }
        lastSourceCount = read.sourceCount();
    }

    private void surveyMember(com.createstats.power.PduSupplySurvey.Builder builder,
                              com.createstats.power.FeGridMember node,
                              java.util.Set<Integer> foreignFeeds) {
        final BlockEntity attached = attachedBlockEntity(node);

        if (attached instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler
                && coupler.mode() == com.createstats.power.PowerCouplerMode.PULL) {
            com.createstats.power.NewAgeFeed.surveyCouplerSeam(level, coupler.getBlockPos(),
                    coupler.foreignFace(), builder, foreignFeeds);
            return;
        }
        if (attached instanceof CapacitorBankBE bank) {
            builder.addTank(bank.structureMin().asLong(), bank.groupReserveRank(),
                    bank.groupEnergy(), bank.groupCapacity());
            if (bank.chargeRatePerTick() > 0) {
                builder.markBankCharging();
            }
            return;
        }
        if (attached instanceof KineticDynamoBE dynamo) {
            builder.addSource(dynamo.getBlockPos().asLong(), dynamo.potentialFePerTick());
            noteForecast(builder, dynamo.getBlockPos().asLong(), dynamo);
            return;
        }

        final IEnergyStorage handler = node.attachedStorage();
        if (handler == null) {
            return;
        }

        com.createstats.power.FeForeignHandlers.learn(level, memberPos(node, attached), handler);
        switch (com.createstats.power.FeNodeRole.of(handler, node.transferRate()).kind()) {
            case GENERATOR -> {
                final int foreign = foreignGenerationOf(attached);

                if (attached instanceof com.createstats.power.FeSupply || foreign > 0) {
                    final int price = Math.max(ownGenerationOf(attached, worldPosition.asLong()), foreign);
                    builder.addSource(sourceKeyFor(node, attached), price);

                    if (attached instanceof BatteryControllerBE) {
                        rawControllerGeneration = com.createstats.power.FeRates.plus(
                                rawControllerGeneration, price);
                    }
                } else {
                    builder.addUnpricedSource(sourceKeyFor(node, attached));
                }
                noteForecast(builder, sourceKeyFor(node, attached), attached);
            }

            case STORAGE -> builder.addForeignTank(handler, foreignTankKey(node, attached),
                    FeGridSolver.reserveRankOf(node),
                    handler.getEnergyStored(), handler.getMaxEnergyStored());
            default -> {
            }
        }
    }

    private com.createstats.power.FeSupplyOutlook.Result projectSupply() {
        if (level == null || !supplyMemory.isWarm()) {
            return com.createstats.power.FeSupplyOutlook.Result.NONE;
        }

        return com.createstats.power.FeSupplyOutlook.project(supplyMemory, weatherShareMemory,
                unpricedSupplyFePerTick(), level.getDayTime(), OUTLOOK_TICKS, outlookWeather,
                declaredCriticalDemandFePerTick());
    }

    private int unpricedSupplyFePerTick() {
        return Math.max(0, measuredGeneration() - Math.max(0, learnedGeneration));
    }

    private com.createstats.power.PduSupplySurvey.Result surveyLegForecasts() {
        if (level == null || branchSources.isEmpty()) {
            return com.createstats.power.PduSupplySurvey.Result.EMPTY;
        }
        final com.createstats.power.PduSupplySurvey.Builder legs =
                new com.createstats.power.PduSupplySurvey.Builder();
        for (FeGridNode node : branchSources) {
            if (!isLegEnergised(legOf(node)) || boardPeerOf(node) != null
                    || !(node instanceof com.createstats.power.FeGridMember member)) {
                continue;
            }
            final List<Long> caps = capsOfNode.get(node.nodeKey());
            if (caps != null && !caps.isEmpty()) {
                continue;
            }
            final BlockEntity attached = attachedBlockEntity(member);
            if (!(attached instanceof com.createstats.power.FeForecastingSource source) || source.relays()
                    || !(attached instanceof com.createstats.power.FeSupply supply)) {
                continue;
            }
            final long key = attached.getBlockPos().asLong();
            legs.addSource(key, Math.max(0, supply.potentialFePerTick()));
            noteForecast(legs, key, attached);
        }
        return legs.build();
    }

    public int expectedSupplyDropFePerTick() {
        final int legDrop = Math.min(legForecast.expectedDropFePerTick(), Math.max(0, legFeedGeneration));
        return com.createstats.power.FeRates.plus(survey.expectedDropFePerTick(), legDrop);
    }

    private void splitNightHold() {
        peerHoldShares.clear();
        if (mode != PduMode.AUTO) {
            ownHoldApplied = false;
        }
        if (level == null || !outlook.isShort()) {
            ownHoldFe = 0;
            peerHoldFe = 0;
            ownHoldApplied = false;
            return;
        }
        final List<BlockPos> peers = new ArrayList<>(branchBoards.size());
        final List<Integer> critical = new ArrayList<>(branchBoards.size());
        for (BoardLink link : branchBoards) {
            if (peers.contains(link.at()) || !level.isLoaded(link.at())
                    || !(level.getBlockEntity(link.at())
                            instanceof com.createstats.power.FeBoardPeer peer)) {
                continue;
            }
            peers.add(link.at());
            critical.add(Math.max(0, peer.declaredCriticalDemandFePerTick()));
        }
        final int[] crit = new int[critical.size()];
        for (int i = 0; i < crit.length; i++) {
            crit[i] = critical.get(i);
        }
        final int[] shares = com.createstats.power.FeSupplyOutlook.splitHold(outlook.shortfallFe(),
                declaredCriticalDemandFePerTick(), crit);
        int asked = 0;
        for (int i = 0; i < peers.size(); i++) {
            if (shares[i] > 0) {
                peerHoldShares.put(peers.get(i).asLong(), shares[i]);
                asked = com.createstats.power.FeRates.plus(asked, shares[i]);
            }
        }
        peerHoldFe = asked;
        ownHoldFe = shares[shares.length - 1];
    }

    private final Map<Long, Integer> peerHoldShares = new HashMap<>();

    private com.createstats.power.FeReserveBand.Band ownBankBand(int capacity, int fullRate) {
        final com.createstats.power.FeReserveBand.Band standard = sizedBankBand(capacity, fullRate);
        if (ownHoldFe <= 0 || capacity <= 0) {
            return standard;
        }
        final int hold = com.createstats.power.FeSupplyOutlook.holdPctFor(
                new com.createstats.power.FeSupplyOutlook.Result(0, 0, ownHoldFe, 0, false), capacity);
        return hold <= standard.floorPct() ? standard
                : new com.createstats.power.FeReserveBand.Band(hold, standard.ceilingPct(), standard.healthyPct());
    }

    private com.createstats.power.FeSupplyOutlook.Weather currentWeather() {
        if (level == null) {
            return com.createstats.power.FeSupplyOutlook.Weather.steady(
                    com.createstats.power.SolarCurve.Sky.CLEAR);
        }
        final com.createstats.power.SolarCurve.Sky now = level.isThundering()
                ? com.createstats.power.SolarCurve.Sky.THUNDER
                : level.isRaining() ? com.createstats.power.SolarCurve.Sky.RAIN
                : com.createstats.power.SolarCurve.Sky.CLEAR;
        com.createstats.power.FeSupplyOutlook.Weather weather =
                com.createstats.power.FeSupplyOutlook.Weather.steady(now);
        final com.createstats.blockentity.WeatherPanelBE panel = branchWeatherPanel;
        if (panel != null && !panel.isRemoved()) {
            final int toChange = panel.ticksToNextChangeFor(level.getGameTime());
            if (toChange >= 0) {
                weather = new com.createstats.power.FeSupplyOutlook.Weather(now, toChange,
                        now == com.createstats.power.SolarCurve.Sky.CLEAR
                                ? com.createstats.power.SolarCurve.Sky.RAIN
                                : com.createstats.power.SolarCurve.Sky.CLEAR);
            }
        }
        return weather;
    }

    private void addForecastTooltip(List<Component> tooltip, boolean detailed) {
        if (syncedForecastDrop > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Supply falling: down %,d FE/t within the minute, to %,d",
                    syncedForecastDrop, Math.max(0, lastGeneration - syncedForecastDrop)))
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (lastProjectedFe > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Expecting %s FE over the next half day", compactFe(lastProjectedFe)))
                    .withStyle(ChatFormatting.AQUA)));
        } else if (detailed && syncedDayWarmBuckets > 0) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "Learning the day: %d of %d hours watched", syncedDayWarmBuckets,
                    com.createstats.power.FeSupplyMemory.BUCKETS)));
        }

        if (syncedOutlookShortfall > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Short %s FE before the sun is back%s",
                    compactFe(syncedOutlookShortfall),
                    syncedOutlookCertain ? ", weather allowed for" : ""))
                    .withStyle(ChatFormatting.GOLD)));

            if (syncedOwnHoldFe > 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT, syncedOwnHeld
                        ? "Holding %s of it in its own banks"
                        : "%s of it is its own critical load, and no bank of its own holds it",
                        compactFe(syncedOwnHoldFe))));
            }
            if (syncedPeerHoldFe > 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                        "Asking the boards on its legs to hold %s", compactFe(syncedPeerHoldFe))));
            }
        }
        if (detailed && syncedOutlookDarkTicks > 0) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "%d:%02d of the next day generating nothing",
                    syncedOutlookDarkTicks / 20 / 60, syncedOutlookDarkTicks / 20 % 60)));
        }
        if (holdTargetFe() > 0) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "Holding %s FE back for a board upstream", compactFe(holdTargetFe()))));
        }
    }

    private static String compactFe(int fe) {
        if (fe >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", fe / 1_000_000.0);
        }
        if (fe >= 10_000) {
            return String.format(Locale.ROOT, "%,dk", fe / 1_000);
        }
        return String.format(Locale.ROOT, "%,d", fe);
    }

    private static void noteForecast(com.createstats.power.PduSupplySurvey.Builder builder, long key,
                                     @Nullable BlockEntity attached) {
        if (!(attached instanceof com.createstats.power.FeForecastingSource source)) {
            return;
        }
        final int[] offsets = com.createstats.power.PduSupplySurvey.FORECAST_OFFSETS;
        final int[] samples = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            samples[i] = Math.max(0, source.potentialFePerTickIn(offsets[i]));
        }

        builder.addForecast(key, samples, source.weatherSensitive(), source.relayedWeatherShareFePerTick());
    }

    private static BlockPos memberPos(com.createstats.power.FeGridMember node,
                                      @Nullable BlockEntity attached) {
        if (node instanceof BlockEntity be) {
            return be.getBlockPos();
        }
        if (attached != null) {
            return attached.getBlockPos();
        }
        return BlockPos.of(node.nodeKey());
    }

    private static int ownGenerationOf(@Nullable BlockEntity attached, long consumerPos) {
        return attached instanceof com.createstats.power.FeSupply supply
                ? Math.max(0, supply.potentialFePerTickFor(consumerPos))
                : 0;
    }

    private static int foreignGenerationOf(@Nullable BlockEntity attached) {
        final int createAddition = com.createstats.compat.CreateAdditionCompatHelper
                .generationPotentialFePerTick(attached);
        if (createAddition > 0) {
            return createAddition;
        }
        return com.createstats.compat.NewAgeCompatHelper.generationPotentialFePerTick(attached);
    }

    private static long foreignTankKey(com.createstats.power.FeGridMember node,
                                       @Nullable BlockEntity attached) {
        final BlockPos controller =
                com.createstats.compat.CreateAdditionCompatHelper.controllerPosOf(attached);
        if (controller != null) {
            return controller.asLong();
        }
        return attached != null ? attached.getBlockPos().asLong() : node.nodeKey();
    }

    private static long sourceKeyFor(com.createstats.power.FeGridMember node, @Nullable BlockEntity attached) {
        return attached != null ? attached.getBlockPos().asLong() : node.nodeKey();
    }

    @Nullable
    private FeWireNetwork feedNetworkOn(Direction face) {
        final BlockEntity be = level.getBlockEntity(worldPosition.relative(face));
        if (be instanceof PowerConnectorBE connector) {
            return connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                    && connector.getBlockState().getValue(
                            com.createstats.block.PowerConnectorBlock.FACING) == face
                    ? FeWireNetwork.liveNetworkOf(connector) : null;
        }
        if (be instanceof CableBE || be instanceof com.createstats.blockentity.IndoorCableBE) {
            return FeWireNetwork.liveNetworkOf(be);
        }

        if (be instanceof PowerMeterBE meter) {
            return meter.outputFace() == face.getOpposite()
                    ? gridBehind(meter.getBlockPos(), meter.inputFace()) : null;
        }
        if (be instanceof PowerLimiterBE limiter) {
            return limiter.outputFace() == face.getOpposite()
                    ? gridBehind(limiter.getBlockPos(), limiter.inputFace()) : null;
        }
        return null;
    }

    @Nullable
    private FeWireNetwork gridBehind(BlockPos instrument, Direction face) {
        final BlockEntity behind = level.getBlockEntity(instrument.relative(face));
        return behind == null ? null : FeWireNetwork.liveNetworkOf(behind);
    }

    public static int studRate() {
        return PowerConnectorBE.baseTransferRate() * com.createstats.power.ConnectorTier.ADVANCED.multiplier();
    }

    public static final int HV_STUDS = 2;

    public static int studRate(int leg) {
        return leg >= 0 && leg < HV_STUDS ? hvStudRate() : studRate();
    }

    public static int hvStudRate() {
        return PowerConnectorBE.baseTransferRate() * com.createstats.power.ConnectorTier.INDUSTRIAL.multiplier();
    }

    public int legCeiling(int leg) {
        if (leg == BACK_LEG) {
            final int conduit = backConduitRate();
            return conduit <= 0 ? 0 : Math.min(backRate(), conduit);
        }
        return com.createstats.power.PduStudWiring.ceiling(studRate(leg),
                PowerConnectorBE.baseTransferRate(), bestStudTier(leg));
    }

    private int lastBackConduitRate;

    private int backConduitRate() {
        if (level == null) {
            return lastBackConduitRate;
        }
        final BlockPos back = worldPosition.relative(backFace());
        if (!level.hasChunkAt(back)) {
            return lastBackConduitRate;
        }
        final BlockEntity be = level.getBlockEntity(back);

        if (be instanceof com.createstats.blockentity.CableBE cable) {
            lastBackConduitRate = cable.tier().rate(PowerConnectorBE.baseTransferRate());
        } else if (be instanceof com.createstats.blockentity.IndoorCableBE) {
            lastBackConduitRate = com.createstats.power.IndoorCable.rate(
                    PowerConnectorBE.baseTransferRate());
        } else {
            lastBackConduitRate = 0;
        }
        return lastBackConduitRate;
    }

    @Nullable
    private com.createstats.power.WireTier bestStudTier(int leg) {
        if (leg < 0 || leg >= com.createstats.power.PduStuds.COUNT) {
            return null;
        }
        final List<com.createstats.power.WireTier> tiers = new ArrayList<>(studLinks.get(leg).size());
        for (StudLink link : studLinks.get(leg)) {
            tiers.add(link.tier());
        }
        return com.createstats.power.PduStudWiring.best(tiers);
    }

    public int branchThroughputCeiling() {
        int total = 0;
        for (int leg = 0; leg < LEGS; leg++) {
            total += legCeiling(leg);
        }
        return total > 0 ? total : studRate();
    }

    private static boolean containsIdentity(List<FeWireNetwork> found, FeWireNetwork network) {
        return indexOfIdentity(found, network) >= 0;
    }

    private static int indexOfIdentity(List<FeWireNetwork> found, FeWireNetwork network) {
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i) == network) {
                return i;
            }
        }
        return -1;
    }

    private List<FeWireNetwork> legNetworks(int leg) {
        final List<LegGrid> grids = legGrids(leg);
        final List<FeWireNetwork> found = new ArrayList<>(grids.size());
        for (LegGrid grid : grids) {
            found.add(grid.network());
        }
        return found;
    }

    private record LegGrid(FeWireNetwork network, long anchorKey, int conduitCeiling, boolean series,
                           List<Long> instruments, int pathCeiling) {
        LegGrid(FeWireNetwork network, long anchorKey, int conduitCeiling, boolean series) {
            this(network, anchorKey, conduitCeiling, series, List.of(), conduitCeiling);
        }

        LegGrid {
            instruments = List.copyOf(instruments);
        }
    }

    private List<LegGrid> legGrids(int leg) {
        return legGrids(leg, null);
    }

    private List<LegGrid> legGrids(int leg, @Nullable java.util.Set<Long> walkedInstruments) {
        if (level == null || leg < 0 || leg >= LEGS) {
            return List.of();
        }
        if (leg == BACK_LEG) {
            final BlockPos at = worldPosition.relative(backFace());

            final BlockEntity be = level.getBlockEntity(at);
            if (!(be instanceof com.createstats.blockentity.CableBE)
                    && !(be instanceof com.createstats.blockentity.IndoorCableBE)) {
                return List.of();
            }
            final FeWireNetwork network = FeWireNetwork.liveNetworkOf(be);
            if (network == null) {
                return List.of();
            }
            final List<LegGrid> back = new ArrayList<>(2);
            back.add(new LegGrid(network, at.asLong(),
                    network.conduitCeilingFrom(at.asLong()), false));
            followInstruments(back, 0, walkedInstruments);
            return back;
        }
        if (studLinks.get(leg).isEmpty()) {
            return List.of();
        }
        final List<LegGrid> found = new ArrayList<>(2);
        for (StudLink link : studLinks.get(leg)) {
            if (!level.isLoaded(link.to())) {
                continue;
            }

            final FeWireNetwork network =
                    FeWireNetwork.liveNetworkOf(level.getBlockEntity(link.to()));
            if (network == null) {
                continue;
            }

            boolean seen = false;
            for (LegGrid other : found) {
                if (other.network() == network) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                final int root = found.size();
                found.add(new LegGrid(network, link.to().asLong(),
                        network.conduitCeilingFrom(link.to().asLong()), false));
                followInstruments(found, root, walkedInstruments);
            }
        }
        return found;
    }

    private void followInstruments(List<LegGrid> found, int from,
                                   @Nullable java.util.Set<Long> walkedInstruments) {
        if (level == null) {
            return;
        }

        for (int at = from; at < found.size() && found.size() < MAX_LEG_GRIDS; at++) {
            for (com.createstats.power.FeGridMember node : found.get(at).network().servedMembers()) {
                if (found.size() >= MAX_LEG_GRIDS) {
                    return;
                }

                final LegGrid here = found.get(at);
                final BlockPos device = devicePosOf(node);
                if (device == null || !(level.getBlockEntity(device)
                        instanceof com.createstats.power.FeInlineInstrument box)) {
                    continue;
                }

                final BlockPos inlet = device.relative(box.inputFace());
                if (!here.network().knowsPosition(inlet)) {
                    continue;
                }
                final BlockPos outlet = device.relative(box.outputFace());
                if (!level.isLoaded(outlet)) {
                    continue;
                }
                final FeWireNetwork beyond = FeWireNetwork.liveNetworkOf(level.getBlockEntity(outlet));
                if (beyond == null) {
                    continue;
                }
                boolean seen = false;
                for (LegGrid other : found) {
                    if (other.network() == beyond) {
                        seen = true;
                        break;
                    }
                }
                if (seen) {
                    continue;
                }
                if (walkedInstruments != null) {
                    walkedInstruments.add(device.asLong());
                }

                final int path = Math.min(here.pathCeiling(),
                        beyond.conduitCeilingFrom(outlet.asLong()));
                final LegGrid root = found.get(from);
                found.set(from, new LegGrid(root.network(), root.anchorKey(),
                        Math.min(root.conduitCeiling(), path), root.series(), root.instruments(),
                        root.pathCeiling()));
                found.add(new LegGrid(beyond, outlet.asLong(), FeGridSolver.NO_CEILING, true,
                        withInstrument(here.instruments(), device, box), path));
            }
        }
    }

    private static List<Long> withInstrument(List<Long> chain, BlockPos at,
                                             com.createstats.power.FeInlineInstrument box) {
        if (!box.capsThroughput()) {
            return chain;
        }
        final List<Long> next = new ArrayList<>(chain.size() + 1);
        next.addAll(chain);
        next.add(at.asLong());
        return next;
    }

    private static final int MAX_LEG_GRIDS = 16;

    public record StudLink(BlockPos to, com.createstats.power.WireTier tier) {
    }

    private final List<List<StudLink>> studLinks = newStudLinks();

    private static List<List<StudLink>> newStudLinks() {
        final List<List<StudLink>> lists = new ArrayList<>(com.createstats.power.PduStuds.COUNT);
        for (int i = 0; i < com.createstats.power.PduStuds.COUNT; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    public List<StudLink> studLinks(int stud) {
        return stud >= 0 && stud < com.createstats.power.PduStuds.COUNT
                ? studLinks.get(stud) : List.of();
    }

    public boolean hasAnyOutput() {
        return wiresOnStuds() > 0 || legRating(BACK_LEG) > 0;
    }

    public int wiresOnStuds() {
        int total = 0;
        for (List<StudLink> links : studLinks) {
            total += links.size();
        }
        return total;
    }

    public int studLinkCount(int stud) {
        return studLinks(stud).size();
    }

    public boolean isLinkedToConnector(BlockPos connector) {
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                if (link.to().equals(connector)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void linkStud(int stud, com.createstats.power.FeWireTerminal terminal,
                         com.createstats.power.WireTier tier) {
        if (level == null || level.isClientSide() || stud < 0
                || stud >= com.createstats.power.PduStuds.COUNT) {
            return;
        }
        studLinks.get(stud).add(new StudLink(terminal.terminalPos().immutable(),
                tier == null ? com.createstats.power.WireTier.COPPER : tier));
        terminal.addLink(worldPosition, tier);
        syncStudLinks();
    }

    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || stud < 0
                || stud >= com.createstats.power.PduStuds.COUNT) {
            return 0;
        }
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final java.util.Map<com.createstats.power.WireTier, Integer> refund =
                new java.util.EnumMap<>(com.createstats.power.WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);

            if (level.getBlockEntity(link.to())
                    instanceof com.createstats.power.FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        for (java.util.Map.Entry<com.createstats.power.WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    public void dropAllStudWires() {
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            dropStudWires(stud);
        }
    }

    public void forgetLinksTo(BlockPos connector) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> link.to().equals(connector));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void pruneStudLinks() {
        if (level == null) {
            return;
        }
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> level.isLoaded(link.to())
                    && !(level.getBlockEntity(link.to())
                    instanceof com.createstats.power.FeWireTerminal terminal
                    && terminal.links().contains(worldPosition)));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void syncStudLinks() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private final boolean[] legClosed = newAllClosed();
    private final boolean[] legTripped = new boolean[LEGS];

    private final FePriority[] legPriority = newLegPriorities();

    private static FePriority[] newLegPriorities() {
        final FePriority[] tiers = new FePriority[LEGS];
        java.util.Arrays.fill(tiers, FePriority.DEFAULT);
        return tiers;
    }

    private final java.util.Map<Long, Integer> legOfNode = new HashMap<>();

    private final List<FeGridNode> branchStorage = new ArrayList<>();

    private int branchTankStored;
    private int branchTankCapacity;

    private record ParallelFeed(int leg, FeGridNode node) {
    }

    private final java.util.Map<Long, List<ParallelFeed>> parallelFeeds = new HashMap<>(2);

    private final java.util.Map<Long, java.util.Map<Integer, FeWireNetwork>> stationLegNetworks = new HashMap<>(2);

    private Map<Long, int[]> lastStationShares = Map.of();

    private final java.util.Map<Long, Integer> storageFlowByNode = new HashMap<>();

    private final java.util.Map<Long, Integer> branchFlowByNode = new HashMap<>();

    private final List<Long> managedAnchors = new ArrayList<>();

    private final List<FeWireNetwork> cappedNetworks = new ArrayList<>();

    private final int[] legConduitCeiling = new int[LEGS];

    {
        java.util.Arrays.fill(legConduitCeiling, FeGridSolver.NO_CEILING);
    }

    private final List<FeWireNetwork> managedNetworks = new ArrayList<>();

    private final List<FeGridNode> branchSources = new ArrayList<>();

    private int branchSourceInput;

    private int legFeedGeneration;

    private int syncedBranchSources;
    private int syncedBranchSourceInput;

    private int branchStorageDrawn;

    public record BranchStorage(int tanks, int stored, int capacity, int chargeRate) {
        public static final BranchStorage NONE = new BranchStorage(0, 0, 0, 0);

        public float fill() {
            return capacity <= 0 ? 0.0f : Math.min(1.0f, stored / (float) capacity);
        }
    }

    private BranchStorage branchStorageSummary = BranchStorage.NONE;

    public int branchSourceCount() {
        return syncedBranchSources;
    }

    public int branchSourceInputFePerTick() {
        return syncedBranchSourceInput;
    }

    public BranchStorage branchStorageSummary() {
        return branchStorageSummary;
    }

    private final int[] syncedLegCeiling = new int[LEGS];
    private final int[] syncedLegDemand = new int[LEGS];
    private final int[] syncedLegAdmitted = new int[LEGS];

    public int legRating(int leg) {
        if (leg < 0 || leg >= syncedLegCeiling.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegCeiling[leg] : legCeiling(leg);
    }

    public boolean isLegWired(int leg) {
        return legRating(leg) > 0;
    }

    public int legShownDemand(int leg) {
        if (leg < 0 || leg >= syncedLegDemand.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegDemand[leg] : legDemand(leg);
    }

    public int legShownAdmitted(int leg) {
        if (leg < 0 || leg >= syncedLegAdmitted.length) {
            return 0;
        }
        return level != null && level.isClientSide() ? syncedLegAdmitted[leg] : legAdmitted(leg);
    }

    public boolean isLegOverCapacity(int leg) {
        return legShownDemand(leg) > legShownAdmitted(leg);
    }

    private int legsEverWired;

    public boolean legHasChannels(int leg) {
        return isLegWired(leg) || (leg >= 0 && leg < LEGS && (legsEverWired & (1 << leg)) != 0);
    }

    public int legDeviceCount(int leg) {
        int count = 0;
        for (BranchDevice d : roster) {
            if (d.leg() == leg) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    public BranchDevice legBoardRow(int leg) {
        for (BranchDevice d : roster) {
            if (d.leg() == leg && d.kind().isBoard()) {
                return d;
            }
        }
        return null;
    }

    private final int[] legOverload = new int[LEGS];

    private final int[] legIntake = new int[LEGS];
    private final int[] legOutflow = new int[LEGS];

    private long legBudgetTick = Long.MIN_VALUE;

    private void refillLegBudgets(long now) {
        if (now == legBudgetTick) {
            return;
        }
        legBudgetTick = now;
        java.util.Arrays.fill(legIntake, 0);
        java.util.Arrays.fill(legOutflow, 0);
    }

    private final java.util.Map<Long, List<Long>> capsOfNode = new HashMap<>();

    private FeGridSolver.BranchResult serveLegLoads(List<FeGridNode> here, int leg, int pool,
                                                    boolean byPriority) {
        return serveLegLoads(here, leg, pool, byPriority, null);
    }

    private FeGridSolver.BranchResult serveLegLoads(List<FeGridNode> here, int leg, int pool,
                                                    boolean byPriority,
                                                    @Nullable Map<Long, Integer> allowance) {
        if (capsOfNode.isEmpty()) {
            return serveGroup(here, pool, byPriority, allowance);
        }
        final Map<List<Long>, List<FeGridNode>> groups = new java.util.LinkedHashMap<>();
        final List<FeGridNode> free = new ArrayList<>(here.size());
        for (FeGridNode node : here) {
            final List<Long> caps = capsOfNode.get(node.nodeKey());
            if (caps == null || caps.isEmpty()) {
                free.add(node);
            } else {
                groups.computeIfAbsent(caps, key -> new ArrayList<>()).add(node);
            }
        }
        if (groups.isEmpty()) {
            return serveGroup(here, pool, byPriority, allowance);
        }
        int delivered = 0;
        int served = 0;
        int shed = 0;
        int remaining = pool;
        for (Map.Entry<List<Long>, List<FeGridNode>> group : groups.entrySet()) {
            final int give = Math.min(remaining, capRoom(group.getKey()));
            final FeGridSolver.BranchResult result =
                    serveGroup(group.getValue(), give, byPriority, allowance);
            spendCap(group.getKey(), result.delivered(), give, remaining);
            delivered += result.delivered();
            served += result.served();
            shed += result.shed();
            remaining = Math.max(0, remaining - result.delivered());
        }
        if (!free.isEmpty()) {
            final FeGridSolver.BranchResult result = serveGroup(free, remaining, byPriority, allowance);
            delivered += result.delivered();
            served += result.served();
            shed += result.shed();
        }
        return new FeGridSolver.BranchResult(delivered, served, shed);
    }

    private FeGridSolver.BranchResult serveGroup(List<FeGridNode> nodes, int pool,
                                                 boolean byPriority,
                                                 @Nullable Map<Long, Integer> allowance) {
        return byPriority
                ? FeGridSolver.serveByPriority(nodes, pool, cursor++, branchFlowByNode, allowance,
                        servePolicy)
                : FeGridSolver.serveFlat(nodes, pool, cursor++, branchFlowByNode, servePolicy);
    }

    private final com.createstats.power.FeServePolicy servePolicy =
            new com.createstats.power.FeServePolicy() {
                @Override
                public int tierOrdinalOf(FeGridNode node) {
                    return priorityOrdinalOf(node);
                }

                @Override
                public void markShed(FeGridNode node, boolean shed) {
                    setShedFlag(node, shed);
                    if (shed) {
                        manualShed.add(node.nodeKey());
                    } else {
                        manualShed.remove(node.nodeKey());
                    }
                }
            };

    private final java.util.Set<Long> manualShed = new HashSet<>();

    private int capRoomOf(FeGridNode node) {
        final List<Long> caps = capsOfNode.get(node.nodeKey());
        return caps == null || caps.isEmpty() ? FeGridSolver.NO_CEILING : capRoom(caps);
    }

    private int capRoom(List<Long> caps) {
        if (level == null) {
            return FeGridSolver.NO_CEILING;
        }
        return instrumentBudget.remaining(caps, this::managedCeilingAt, level.getGameTime());
    }

    private int managedCeilingAt(long packed) {
        return level != null && level.getBlockEntity(BlockPos.of(packed))
                instanceof com.createstats.power.FeInlineInstrument box
                ? Math.max(0, box.managedThroughputCeiling()) : FeGridSolver.NO_CEILING;
    }

    private void spendCapOf(FeGridNode node, int delivered, int offered, int available) {
        final List<Long> caps = capsOfNode.get(node.nodeKey());
        if (caps != null && !caps.isEmpty()) {
            spendCap(caps, delivered, offered, available);
        }
    }

    private void spendCap(List<Long> caps, int delivered, int offered, int available) {
        if (level == null) {
            return;
        }
        instrumentBudget.spend(caps, delivered, available > offered && delivered >= offered,
                level.getGameTime());
    }

    private final com.createstats.power.FeInstrumentBudget instrumentBudget =
            new com.createstats.power.FeInstrumentBudget();

    private int legRoom(int[] spent, int leg) {
        final int ceiling = legCeiling(leg);
        if (!cableLimitEnabled()) {
            return ceiling;
        }

        final int limited = leg >= 0 && leg < legConduitCeiling.length
                ? Math.min(ceiling, legConduitCeiling[leg]) : ceiling;
        return Math.max(0, limited - spent[leg]);
    }

    private static int conduitCeilingOf(List<LegGrid> grids) {
        if (grids.isEmpty()) {
            return FeGridSolver.NO_CEILING;
        }
        int total = 0;
        for (LegGrid grid : grids) {
            if (grid.series()) {
                continue;
            }
            final int ceiling = grid.conduitCeiling();
            if (ceiling >= FeGridSolver.NO_CEILING) {
                return FeGridSolver.NO_CEILING;
            }
            total += ceiling;
        }
        return total;
    }

    private void spendLeg(int[] spent, int leg, int fe) {
        if (fe > 0 && leg >= 0 && leg < spent.length) {
            spent[leg] += fe;
        }
    }

    private static boolean cableLimitEnabled() {
        return FeWireNetwork.cableLimitEnabled();
    }

    private static final int TRIP_TICKS = 60;

    @Nullable
    private com.createstats.power.PduLegPlan.Screened lastScreened;

    public int legDemand(int leg) {
        return lastScreened != null && leg >= 0 && leg < lastScreened.legDemand().length
                ? lastScreened.legDemand()[leg] : 0;
    }

    public int legAdmitted(int leg) {
        return lastScreened != null && leg >= 0 && leg < lastScreened.legAdmitted().length
                ? lastScreened.legAdmitted()[leg] : 0;
    }

    public boolean isLegAtCapacity(int leg) {
        return lastScreened != null && lastScreened.isAtCapacity(leg);
    }

    private static boolean[] newAllClosed() {
        final boolean[] closed = new boolean[LEGS];
        java.util.Arrays.fill(closed, true);
        return closed;
    }

    public boolean isLegClosed(int leg) {
        return leg >= 0 && leg < legClosed.length && legClosed[leg];
    }

    public boolean isLegTripped(int leg) {
        return leg >= 0 && leg < legTripped.length && legTripped[leg];
    }

    public boolean isLegEnergised(int leg) {
        return isLegClosed(leg) && !isLegTripped(leg);
    }

    public FePriority legPriority(int leg) {
        return leg >= 0 && leg < legPriority.length ? legPriority[leg] : FePriority.DEFAULT;
    }

    private int[] legPriorityOrdinals() {
        final int[] tiers = new int[LEGS];
        for (int leg = 0; leg < LEGS; leg++) {
            tiers[leg] = legPriority[leg].ordinal();
        }
        return tiers;
    }

    public void setLegPriority(int leg, FePriority tier) {
        if (leg < 0 || leg >= legPriority.length || tier == null || level == null
                || level.isClientSide() || legPriority[leg] == tier) {
            return;
        }
        legPriority[leg] = tier;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public void cycleLegPriority(int leg) {
        setLegPriority(leg, legPriority(leg).next());
    }

    public void setLegClosed(int leg, boolean closed) {
        if (leg < 0 || leg >= legClosed.length || level == null || level.isClientSide()) {
            return;
        }
        legClosed[leg] = closed;
        if (closed) {
            legTripped[leg] = false;
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    private boolean isUpstreamSurplus() {
        if (branchTankCapacity > 0
                && branchTankStored > Math.round(branchTankCapacity * restoreSocFraction())) {
            return true;
        }
        final com.createstats.power.FeReserveReport report = reserveReport();
        if (report.isPresent()) {
            // trust the controller about its reserve. our full buffer isn't surplus while it's rationing

            return report.sustainedDraw() > 0 || report.netRate() > 0;
        }
        return energy > Math.round(bufferCapacity() * restoreSocFraction())
                || rawBankCharging
                || (survey.hasReserve() && survey.reserveFill() > restoreSocFraction());
    }

    private FeLock lockOf(FeGridNode node) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            return fe.lock();
        }
        final com.createstats.power.FeManagedLoad managed = managedLoadOf(node);
        if (managed != null) {
            return managed.managedLock();
        }

        final BlockPos pos = devicePosOf(node);
        return pos == null ? FeLock.NONE : overrides.lock(pos.asLong());
    }

    private int priorityOrdinalOf(FeGridNode node) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            return fe.priority().ordinal();
        }
        final com.createstats.power.FeManagedLoad managed = managedLoadOf(node);
        if (managed != null) {
            return managed.managedPriority().ordinal();
        }
        final BlockPos pos = devicePosOf(node);
        return pos == null ? FePriority.DEFAULT.ordinal() : overrides.priority(pos.asLong()).ordinal();
    }

    @Nullable
    private com.createstats.power.FeManagedLoad managedLoadOf(FeGridNode node) {
        if (node instanceof com.createstats.power.FeManagedLoad managed) {
            return managed;
        }
        if (node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeManagedLoad managed) {
            return managed;
        }
        return null;
    }

    private static long splitKey(FeGridNode node) {
        return node.nodeKey() ^ Long.MIN_VALUE;
    }

    private int criticalDemandOf(FeGridNode node) {
        final com.createstats.power.FeBoardPeer peer = boardPeerOf(node);
        return peer == null ? 0 : Math.max(0, peer.declaredCriticalDemandFePerTick());
    }

    @Nullable
    private com.createstats.power.FeRatedLoad ratedLoadOf(FeGridNode node) {
        if (node instanceof com.createstats.power.FeRatedLoad rated) {
            return rated;
        }
        if (node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeRatedLoad rated) {
            return rated;
        }
        return null;
    }

    private com.createstats.power.FeLoadCost costOf(FeGridNode node) {
        return demandOf(node).cost();
    }

    private void noteParallelFeed(FeGridNode node, int leg, LegGrid grid) {
        if (!feedsInParallel(node) || !grid.instruments().isEmpty()) {
            return;
        }
        final Integer home = legOfNode.get(node.nodeKey());
        if (home == null || home == leg) {
            return;
        }
        final List<ParallelFeed> feeds = parallelFeeds.computeIfAbsent(node.nodeKey(), k -> new ArrayList<>(1));
        for (ParallelFeed feed : feeds) {
            if (feed.leg() == leg) {
                return;
            }
        }
        feeds.add(new ParallelFeed(leg, node));
        noteStationLeg(node, leg, grid);
    }

    private void noteStationLeg(FeGridNode node, int leg, LegGrid grid) {
        if (stationOf(node) == null) {
            return;
        }
        stationLegNetworks.computeIfAbsent(node.nodeKey(), k -> new HashMap<>(2)).putIfAbsent(leg, grid.network());
    }

    private void talkToStations(List<FeGridNode> managed) {
        if (stationLegNetworks.isEmpty() || level == null) {
            return;
        }
        final long now = level.getGameTime();
        final boolean auto = mode == PduMode.AUTO && lastScreened != null;
        int[] otherLoad = null;
        for (FeGridNode node : managed) {
            final long key = node.nodeKey();
            final java.util.Map<Integer, FeWireNetwork> legs = stationLegNetworks.get(key);
            final com.createstats.power.FeStationLoad station = stationOf(node);
            if (legs == null || station == null) {
                continue;
            }
            final int[] studLeg = stationStudLegs(station, legs);
            final int[] live = liveLegsOf(legs.keySet());
            int carried;
            int cost;
            int[] share;
            PduReason reason;
            final com.createstats.power.PduLegPlan.StationFit fit = auto ? lastScreened.stations().get(key) : null;
            if (fit != null) {
                carried = fit.carried();
                cost = fit.demand();
                share = lastScreened.shares().getOrDefault(key, new int[LEGS]);
                reason = lastReasons.getOrDefault(key, PduReason.RUNNING);
            } else {
                if (otherLoad == null) {
                    otherLoad = new int[LEGS];
                    for (FeGridNode other : managed) {
                        if (stationOf(other) == null && lockOf(other) != FeLock.FORCE_OFF) {
                            final int leg = legOf(other);
                            if (leg >= 0 && leg < LEGS) {
                                otherLoad[leg] = com.createstats.power.FeRates.plus(otherLoad[leg],
                                        Math.max(0, steadyDemandOf(other)));
                            }
                        }
                    }
                }
                final int[] spare = new int[LEGS];
                long sum = 0;
                for (int leg : live) {
                    spare[leg] = Math.max(0, legCeiling(leg) - otherLoad[leg]);
                    sum += spare[leg];
                }
                carried = (int) Math.min(Integer.MAX_VALUE, sum);
                cost = Math.max(0, steadyDemandOf(node));
                share = com.createstats.power.PduLegPlan.split(cost, live, spare);
                reason = live.length == 0 ? PduReason.BREAKER_OPEN
                        : carried < cost ? PduReason.WIRE_SHORT : PduReason.RUNNING;
            }
            if (lockOf(node) == FeLock.FORCE_OFF) {
                share = new int[LEGS];
                reason = PduReason.LOCKED_OFF;
            }
            station.acceptBoardFeed(new com.createstats.power.FeStationFeed(worldPosition.asLong(),
                    studLeg[0], studLeg[1], studLeg[0] >= 0 && isLegEnergised(studLeg[0]),
                    studLeg[1] >= 0 && isLegEnergised(studLeg[1]),
                    studLeg[0] >= 0 && studLeg[0] < share.length ? share[studLeg[0]] : 0,
                    studLeg[1] >= 0 && studLeg[1] < share.length && studLeg[1] != studLeg[0] ? share[studLeg[1]] : 0,
                    carried, cost, reason, linkedShortInTicks(), linkedReserveRate(),
                    now + com.createstats.power.FeStationFeed.HOLD_TICKS));
        }
    }

    private int[] stationStudLegs(com.createstats.power.FeStationLoad station,
                                  java.util.Map<Integer, FeWireNetwork> legs) {
        final int[] studLeg = {-1, -1};
        final java.util.Set<Integer> used = new HashSet<>(2);
        for (int stud = 0; stud < 2; stud++) {
            final BlockPos far = station.studFarEnd(stud);
            if (far == null || far.equals(worldPosition) || !level.isLoaded(far)) {
                continue;
            }
            final FeWireNetwork net = FeWireNetwork.liveNetworkOf(level.getBlockEntity(far));
            if (net == null) {
                continue;
            }
            for (Map.Entry<Integer, FeWireNetwork> e : new java.util.TreeMap<>(legs).entrySet()) {
                if (e.getValue() == net && !used.contains(e.getKey())) {
                    studLeg[stud] = e.getKey();
                    used.add(e.getKey());
                    break;
                }
            }
        }
        for (int stud = 0; stud < 2; stud++) {
            if (studLeg[stud] >= 0 || !worldPosition.equals(station.studFarEnd(stud))) {
                continue;
            }
            for (int leg : new java.util.TreeSet<>(legs.keySet())) {
                if (!used.contains(leg)) {
                    studLeg[stud] = leg;
                    used.add(leg);
                    break;
                }
            }
        }
        return studLeg;
    }

    private int[] liveLegsOf(java.util.Set<Integer> legs) {
        return legs.stream().filter(this::isLegEnergised).sorted().mapToInt(Integer::intValue).toArray();
    }

    @Nullable
    private com.createstats.power.FeStationLoad stationOf(FeGridNode node) {
        if (node instanceof com.createstats.power.FeStationLoad station) {
            return station;
        }
        return node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeStationLoad station ? station : null;
    }

    private boolean feedsInParallel(FeGridNode node) {
        if (node instanceof com.createstats.power.FeWireTerminal terminal) {
            return terminal.feedsInParallel();
        }
        return node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeWireTerminal terminal
                && terminal.feedsInParallel();
    }

    private int[] alsoLegsOf(FeGridNode node) {
        final List<ParallelFeed> feeds = parallelFeeds.get(node.nodeKey());
        if (feeds == null || feeds.isEmpty()) {
            return new int[0];
        }
        final int[] out = new int[feeds.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = feeds.get(i).leg();
        }
        return out;
    }

    private int serveParallelFeeds(int tier, @Nullable Map<Long, Integer> costs, int pool) {
        if (parallelFeeds.isEmpty() || pool <= 0) {
            return 0;
        }
        int moved = 0;
        for (Map.Entry<Long, List<ParallelFeed>> entry : parallelFeeds.entrySet()) {
            final long key = entry.getKey();
            final Integer cost = costs == null ? Integer.valueOf(FeGridSolver.NO_CEILING) : costs.get(key);
            if (cost == null) {
                continue;
            }
            for (ParallelFeed feed : entry.getValue()) {
                if (pool - moved <= 0) {
                    return moved;
                }
                if (tier >= 0 && priorityOrdinalOf(feed.node()) != tier) {
                    break;
                }
                if (!isLegEnergised(feed.leg())) {
                    continue;
                }
                final int owed = cost >= FeGridSolver.NO_CEILING ? FeGridSolver.NO_CEILING
                        : cost - branchFlowByNode.getOrDefault(key, 0);
                if (owed <= 0) {
                    break;
                }

                final int[] share = costs == null ? null : lastStationShares.get(key);
                final int legShare = share == null || feed.leg() >= share.length ? FeGridSolver.NO_CEILING
                        : share[feed.leg()];
                final int give = Math.min(Math.min(Math.min(owed, legShare), legRoom(legOutflow, feed.leg())),
                        pool - moved);
                if (give <= 0) {
                    continue;
                }
                final int got = FeGridSolver.deliver(List.of(feed.node()), give, 0, branchFlowByNode, null);
                if (got > 0) {
                    spendLeg(legOutflow, feed.leg(), got);
                    moved += got;
                }
            }
        }
        return moved;
    }

    private boolean parallelRoom(FeGridNode node) {
        final List<ParallelFeed> feeds = parallelFeeds.get(node.nodeKey());
        if (feeds == null) {
            return false;
        }
        for (ParallelFeed feed : feeds) {
            if (isLegEnergised(feed.leg()) && legRoom(legOutflow, feed.leg()) > 0) {
                return true;
            }
        }
        return false;
    }

    private com.createstats.power.FeDeviceDemand demandOf(FeGridNode node) {
        final com.createstats.power.FeDeviceDemand cached = demandByNode.get(node.nodeKey());
        return cached != null ? cached : profileOf(node);
    }

    private int steadyDemandOf(FeGridNode node) {
        return demandOf(node).steady();
    }

    private int burstReliefOf(List<FeGridNode> loads) {
        final com.createstats.power.BurstRelief relief = new com.createstats.power.BurstRelief();

        final Map<Long, PduReason> blocked = mode == PduMode.AUTO && lastScreened != null
                ? lastScreened.blocked() : Map.of();
        for (FeGridNode node : loads) {
            final int leg = legOf(node);
            final List<Long> caps = capsOfNode.get(node.nodeKey());
            final boolean reachable = (isLegEnergised(leg) && legRoom(legOutflow, leg) > 0 || parallelRoom(node))
                    && (caps == null || caps.isEmpty() || capRoom(caps) > 0)
                    && !blocked.containsKey(node.nodeKey())
                    && !blocked.containsKey(splitKey(node));
            relief.add(steadyDemandOf(node), branchFlowByNode.getOrDefault(node.nodeKey(), 0),
                    isBurstSource(node), reachable);
        }
        return relief.total();
    }

    private boolean isBurstSource(FeGridNode node) {
        return node instanceof com.createstats.power.FeGridMember member
                && attachedBlockEntity(member) instanceof com.createstats.power.FeBurstSource;
    }

    private com.createstats.power.FeDeviceDemand profileOf(FeGridNode node) {
        final IEnergyStorage attached = node.attachedStorage();
        if (attached == null) {
            return com.createstats.power.FeDeviceDemand.NONE;
        }
        final int stored = Math.max(0, attached.getEnergyStored());
        final int capacity = Math.max(0, attached.getMaxEnergyStored());
        final int fill = fillTicks();
        final com.createstats.power.FeRatedLoad rated = ratedLoadOf(node);
        if (rated != null) {
            return com.createstats.power.FeDeviceDemand.rated(rated.ratedDrawFePerTick(),
                    rated.measuredDrawFePerTick(), stored, capacity, fill);
        }
        if (attached instanceof FeConsumer fe) {
            return com.createstats.power.FeDeviceDemand.consumer(fe.measuredDrainFePerTick(),
                    fe.settings().activeDrain(), fe.isPowered(), stored, capacity, fill);
        }
        if (node instanceof com.createstats.power.FeGridMember member) {
            final BlockEntity foreign = attachedBlockEntity(member);
            int foreignDraw = com.createstats.compat.CreateAdditionCompatHelper
                    .consumptionFePerTick(foreign);
            if (foreignDraw < 0) {
                foreignDraw = com.createstats.compat.NewAgeCompatHelper.consumptionFePerTick(foreign);
            }
            if (foreignDraw >= 0) {
                return com.createstats.power.FeDeviceDemand.rated(foreignDraw, foreignDraw,
                        stored, capacity, fill);
            }
        }
        final int learned = loadModel.steady(node.nodeKey());
        if (learned >= 0) {
            return com.createstats.power.FeDeviceDemand.learned(learned, node.transferRate(), stored,
                    capacity, fill);
        }
        return com.createstats.power.FeDeviceDemand.opaque(
                attached.receiveEnergy(node.transferRate(), true), stored, capacity, fill);
    }

    private int rowDrawOf(FeGridNode node) {
        final com.createstats.power.FeDeviceDemand demand = demandOf(node);
        if (demand.metered() && demand.reportedDraw() > 0) {
            return demand.reportedDraw();
        }
        return Math.max(0, branchFlowByNode.getOrDefault(node.nodeKey(), 0));
    }

    private void setShedFlag(FeGridNode node, boolean shed) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            fe.setLoadShed(shed);
            return;
        }

        final com.createstats.power.FeManagedLoad managed = managedLoadOf(node);
        if (managed != null) {
            managed.setManagedShed(shed);
        }
    }

    private FeGridSolver.BranchResult serveAcrossLegs(List<FeGridNode> loads, boolean byPriority) {
        final List<List<FeGridNode>> byLeg = new ArrayList<>();
        for (int i = 0; i < LEGS; i++) {
            byLeg.add(new ArrayList<>());
        }
        for (FeGridNode node : loads) {
            byLeg.get(legOf(node)).add(node);
        }

        for (int leg = 0; leg < byLeg.size(); leg++) {
            if (!isLegEnergised(leg)) {
                for (FeGridNode node : byLeg.get(leg)) {
                    setShedFlag(node, true);
                }
            }
        }

        final int rotation = legCursor++;

        final Map<Long, Integer> boardAllowance =
                branchBoards.isEmpty() ? null : new HashMap<>(branchBoards.size() * 2);
        if (!byPriority) {
            final List<FeGridNode> boards = gateBoardsToSpare(byLeg, boardAllowance, rotation, true);
            final Map<Long, Integer> before = boardFlowSnapshot(boards);
            final FeGridSolver.BranchResult flat = runRound(byLeg, energy, rotation, boardAllowance,
                    false, legPriorityOrdinals());
            bookBoardExport(boards, before, true);

            final int fed = serveParallelFeeds(-1, null, Math.max(0, energy - flat.delivered()));
            return new FeGridSolver.BranchResult(flat.delivered() + fed, flat.served(), flat.shed());
        }

        final List<List<FeGridNode>> criticalByLeg = new ArrayList<>();
        final List<List<FeGridNode>> restByLeg = new ArrayList<>();
        for (int i = 0; i < LEGS; i++) {
            criticalByLeg.add(new ArrayList<>());
            restByLeg.add(new ArrayList<>());
        }
        for (int leg = 0; leg < byLeg.size(); leg++) {
            for (FeGridNode node : byLeg.get(leg)) {
                (priorityOrdinalOf(node) == FePriority.CRITICAL.ordinal()
                        ? criticalByLeg : restByLeg).get(leg).add(node);
            }
        }
        final int[] legTiers = legPriorityOrdinals();

        final FeGridSolver.BranchResult critical =
                runRound(criticalByLeg, energy, rotation, null, true, legTiers);

        final int criticalFed = serveParallelFeeds(FePriority.CRITICAL.ordinal(), null,
                Math.max(0, energy - critical.delivered()));
        final List<FeGridNode> boards = gateBoardsToSpare(restByLeg, boardAllowance, rotation, true);
        final Map<Long, Integer> before = boardFlowSnapshot(boards);
        final FeGridSolver.BranchResult rest = runRound(restByLeg,
                Math.max(0, energy - critical.delivered() - criticalFed), rotation, boardAllowance, true, legTiers);
        bookBoardExport(boards, before, true);
        int restFed = 0;
        for (int t = 0; t < FePriority.values().length; t++) {
            if (t != FePriority.CRITICAL.ordinal()) {
                restFed += serveParallelFeeds(t, null,
                        Math.max(0, energy - critical.delivered() - criticalFed - rest.delivered() - restFed));
            }
        }
        return new FeGridSolver.BranchResult(critical.delivered() + criticalFed + rest.delivered() + restFed,
                critical.served() + rest.served(), critical.shed() + rest.shed());
    }

    private void idleTelemetry() {
        if (lastSupplyReadTick == NEVER
                || level.getGameTime() - lastSupplyReadTick >= supplyReadIntervalTicks()) {
            lastSupplyReadTick = level.getGameTime();
            readUpstream();
        }
        final int rawGen = measuredGeneration();
        smoothGen = smoothGen < 0.0f ? rawGen : smoothGen + (rawGen - smoothGen) * TELEMETRY_EMA;
        lastGeneration = Math.max(0, Math.round(smoothGen));
        lastCeiling = branchThroughputCeiling();
        lastSurplus = isUpstreamSurplus();

        lastThroughputLimited = false;

        smoothDemand = -1.0f;
        lastDemand = 0;
        smoothScreenedDemand = -1.0f;
        lastScreenedDemand = 0;
        lastScreenedShed = 0;
        lastServedDemand = 0;
        lastDealSupply = 0;
        lastBoardDemand = 0;
        lastBudget = 0;
        lastEnduranceTicks = Integer.MAX_VALUE;
        lastReasons = Map.of();
    }

    private List<FeGridNode> gateBoardsToSpare(List<List<FeGridNode>> byLeg,
                                               @Nullable Map<Long, Integer> allowance, int rotation) {
        return gateBoardsToSpare(byLeg, allowance, rotation, false);
    }

    private List<FeGridNode> gateBoardsToSpare(List<List<FeGridNode>> byLeg,
                                               @Nullable Map<Long, Integer> allowance, int rotation,
                                               boolean criticalRidesFree) {
        if (branchBoards.isEmpty()) {
            return List.of();
        }
        final List<FeGridNode> boards = new ArrayList<>(2);
        for (List<FeGridNode> here : byLeg) {
            for (FeGridNode node : here) {
                if (isBoardNode(node)) {
                    boards.add(node);
                }
            }
        }
        if (boards.isEmpty() || allowance == null) {
            return boards;
        }
        final int room = exportRoom();
        if (room >= FeGridSolver.NO_CEILING) {
            return boards;
        }
        final int[] want = new int[boards.size()];
        for (int i = 0; i < boards.size(); i++) {
            final FeGridNode node = boards.get(i);
            want[i] = Math.min(node.transferRate(),
                    allowance.getOrDefault(node.nodeKey(), FeGridSolver.NO_CEILING));
        }
        final int[] share = com.createstats.power.PduLegShare.split(want, room, rotation);
        for (int i = 0; i < boards.size(); i++) {
            final FeGridNode node = boards.get(i);
            final long key = node.nodeKey();
            final int free = criticalRidesFree ? criticalDemandOf(node) : 0;
            allowance.put(key, Math.min(allowance.getOrDefault(key, FeGridSolver.NO_CEILING),
                    com.createstats.power.FeRates.plus(Math.max(0, share[i]), free)));
        }
        return boards;
    }

    private Map<Long, Integer> boardFlowSnapshot(List<FeGridNode> boards) {
        if (boards.isEmpty()) {
            return Map.of();
        }
        final Map<Long, Integer> before = new HashMap<>(boards.size() * 2);
        for (FeGridNode node : boards) {
            before.put(node.nodeKey(), branchFlowByNode.getOrDefault(node.nodeKey(), 0));
        }
        return before;
    }

    private int bookBoardExport(List<FeGridNode> boards, Map<Long, Integer> before) {
        return bookBoardExport(boards, before, false);
    }

    private int bookBoardExport(List<FeGridNode> boards, Map<Long, Integer> before,
                                boolean criticalRidesFree) {
        if (boards.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (FeGridNode node : boards) {
            final int now = branchFlowByNode.getOrDefault(node.nodeKey(), 0);
            final int here = Math.max(0, now - before.getOrDefault(node.nodeKey(), 0));
            moved += criticalRidesFree ? Math.max(0, here - criticalDemandOf(node)) : here;
        }
        if (moved > 0) {
            spendExport(moved);

            boardFeedAccumulator += moved;
        }
        return moved;
    }

    private FeGridSolver.BranchResult runRound(List<List<FeGridNode>> byLeg, int pool, int rotation,
                                               @Nullable Map<Long, Integer> allowance,
                                               boolean byPriority, @Nullable int[] legTiers) {
        if (pool <= 0) {
            return FeGridSolver.BranchResult.EMPTY;
        }
        final int[] want = new int[byLeg.size()];
        for (int leg = 0; leg < byLeg.size(); leg++) {
            final List<FeGridNode> here = byLeg.get(leg);
            if (here.isEmpty() || !isLegEnergised(leg)) {
                continue;
            }
            int legTotal = 0;
            for (FeGridNode node : here) {
                final Integer cap = allowance == null ? null : allowance.get(node.nodeKey());
                legTotal = com.createstats.power.PduLegShare.addRates(legTotal,
                        Math.min(node.transferRate(), cap == null ? FeGridSolver.NO_CEILING : cap));
            }
            want[leg] = Math.min(legTotal, legRoom(legOutflow, leg));
        }
        final int[] budget = com.createstats.power.PduLegShare.split(want, legTiers, pool, rotation);
        int delivered = 0;
        int served = 0;
        int shed = 0;
        int remaining = pool;
        int carry = 0;
        for (int i = 0; i < byLeg.size(); i++) {
            final int leg = Math.floorMod(rotation + i, byLeg.size());
            final List<FeGridNode> here = byLeg.get(leg);
            if (here.isEmpty() || !isLegEnergised(leg)) {
                continue;
            }
            final int give = Math.min(Math.min(budget[leg] + carry, remaining),
                    legRoom(legOutflow, leg));
            if (give <= 0) {
                continue;
            }
            final FeGridSolver.BranchResult result =
                    serveLegLoads(here, leg, give, byPriority, allowance);
            delivered += result.delivered();
            served += result.served();
            shed += result.shed();
            spendLeg(legOutflow, leg, result.delivered());
            remaining = Math.max(0, remaining - result.delivered());
            carry = Math.max(0, give - result.delivered());
        }
        return new FeGridSolver.BranchResult(delivered, served, shed);
    }

    private FeGridSolver.BranchResult serveActiveAuto(List<FeGridNode> loads, int pool) {
        final long now = level.getGameTime();
        final int floorEnergy = Math.round(bufferCapacity() * (reserveFloorPct() / 100.0f));
        final boolean belowFloor = energy < floorEnergy;
        final int rideThrough = Math.max(0, energy - floorEnergy) / rideHorizonTicks();

        final List<com.createstats.power.PduLegPlan.Device> tagged = new ArrayList<>(loads.size());
        final Map<Long, FeGridNode> byKey = new HashMap<>();
        for (FeGridNode node : loads) {
            final int leg = legOf(node);
            final FeLock lock = lockOf(node);

            final int total = steadyDemandOf(node);

            final int critical = Math.min(total, criticalDemandOf(node));
            if (critical > 0) {
                tagged.add(new com.createstats.power.PduLegPlan.Device(node.nodeKey(), leg,
                        FePriority.CRITICAL.ordinal(), critical, lock));
                byKey.put(node.nodeKey(), node);
                if (total > critical) {
                    tagged.add(new com.createstats.power.PduLegPlan.Device(splitKey(node), leg,
                            priorityOrdinalOf(node), total - critical, lock));
                    byKey.put(splitKey(node), node);
                }
                continue;
            }

            final com.createstats.power.PduLegPlan.Device device = stationOf(node) != null
                    ? com.createstats.power.PduLegPlan.Device.station(node.nodeKey(), leg,
                            priorityOrdinalOf(node), total, lock, alsoLegsOf(node))
                    : new com.createstats.power.PduLegPlan.Device(node.nodeKey(), leg,
                            priorityOrdinalOf(node), total, lock, alsoLegsOf(node));
            tagged.add(device);
            byKey.put(node.nodeKey(), node);
        }

        final com.createstats.power.PduLegPlan.Leg[] legs =
                new com.createstats.power.PduLegPlan.Leg[LEGS];
        for (int leg = 0; leg < legs.length; leg++) {
            legs[leg] = new com.createstats.power.PduLegPlan.Leg(isLegEnergised(leg),
                    legCeiling(leg), legPriority[leg].ordinal());
        }
        final com.createstats.power.PduLegPlan.Screened screened =
                com.createstats.power.PduLegPlan.screen(tagged, legs);
        lastScreened = screened;
        final List<PduAutoController.Device> devices = screened.survivors();
        final int totalDemand = screened.totalDemand();

        int survivorDemand = 0;
        int survivorCritical = 0;

        int survivorRunningOrdinary = 0;
        for (PduAutoController.Device d : devices) {
            survivorDemand = com.createstats.power.FeRates.plus(survivorDemand, Math.max(0, d.demand()));
            if (d.tierOrdinal() == FePriority.CRITICAL.ordinal()) {
                survivorCritical = com.createstats.power.FeRates.plus(survivorCritical, Math.max(0, d.demand()));
            } else if (autoController.isRunning(d.key())) {
                survivorRunningOrdinary = com.createstats.power.FeRates.plus(survivorRunningOrdinary,
                        Math.max(0, d.demand()));
            }
        }

        lastCriticalDemand = survivorCritical;

        for (int leg = 0; leg < legs.length; leg++) {
            if (screened.isAtCapacity(leg) && isLegEnergised(leg)) {
                legOverload[leg] = Math.min(TRIP_TICKS, legOverload[leg] + 1);
                if (legOverload[leg] >= TRIP_TICKS) {
                    legTripped[leg] = true;
                    legOverload[leg] = 0;
                }
            } else {
                legOverload[leg] = 0;
            }
        }

        if (lastSupplyReadTick == NEVER || now - lastSupplyReadTick >= supplyReadIntervalTicks()) {
            lastSupplyReadTick = now;
            readUpstream();
        }

        final int rawGen = measuredGeneration();
        smoothGen = smoothGen < 0.0f ? rawGen : smoothGen + (rawGen - smoothGen) * TELEMETRY_EMA;
        final int gen = Math.max(0, Math.round(smoothGen));
        final int ceiling = branchThroughputCeiling();
        final boolean surplus = isUpstreamSurplus();

        // count the upstream banks too. we shed machines with tens of millions sitting one wire away

        final int horizon = rideHorizonTicks();

        final com.createstats.power.FeReserveReport report = reserveReport();
        final boolean linked = report.isPresent();

        final int branchRide = branchTankCapacity > 0 ? branchTankStored / horizon : 0;
        final int fullRide = (linked ? report.criticalDraw() : survey.rideThrough(horizon)) + branchRide;
        final boolean reserveLow = linked
                ? report.stance().isRationing()
                : coreProtectionEnabled() && survey.reserveLow(coreProtectionPct());

        final int reserveRide = (linked ? report.sustainedDraw() : reserveLow ? 0 : fullRide - branchRide)
                + branchRide;

        final int chargeClaim = linked ? report.chargeClaim() : 0;

        final int exportCharge = Math.max(0, exportDemand);

        final int upkeep = upkeepFePerTick();
        final int standing = exportCharge + upkeep;
        final int spendableGen = Math.max(0, gen - chargeClaim - standing);

        final int supplyDrop = expectedSupplyDropFePerTick();
        final int planGen = Math.max(0, gen - supplyDrop);

        final com.createstats.power.PduOwnBudget.Budgets ownBudget = com.createstats.power.PduOwnBudget.of(
                new com.createstats.power.PduOwnBudget.Inputs(gen, planGen, standing, ceiling, energy, floorEnergy,
                        horizon, surplus, fullRide, reserveRide, totalDemand));
        final int runBudget = linked ? Math.min(ceiling, spendableGen + reserveRide) : ownBudget.run();
        final int admitBudget = linked ? Math.min(ceiling, spendableGen + reserveRide) : ownBudget.admit();

        final int criticalBudget = linked ? Math.min(ceiling, gen + fullRide) : ownBudget.critical();

        final boolean throughputLimited = linked ? totalDemand > ceiling : ownBudget.throughputLimited();

        final int survivorOrdinary = Math.max(0, survivorDemand - survivorCritical);
        final int askRunning = Math.min(survivorOrdinary, survivorRunningOrdinary);
        currentRequest = new com.createstats.power.FeBoardDeal.Request(worldPosition.asLong(),
                survivorCritical, com.createstats.power.FeRates.plus(askRunning, standing),
                survivorOrdinary - askRunning);

        com.createstats.power.FeBoardDeal.Answer deal = com.createstats.power.FeBoardDeal.Answer.NONE;

        bankWant.update(currentRequest.critical(), now);
        if (linked) {
            ownHoldApplied = false;
            final com.createstats.power.FeBoardDeal.Answer answered = controllerAnswer(now);
            if (answered.isPresent() && answered.asked().total() > 0) {
                deal = com.createstats.power.FeBoardDeal.topUp(answered, legBankAnswer(now, ceiling,
                        com.createstats.power.FeBoardDeal.residual(answered.asked(), answered)));
            }
        } else {
            final com.createstats.power.FeBoardDeal.Answer own =
                    ownBankAnswer(now, gen, ceiling, currentRequest);
            if (own != null) {
                deal = own;
            }
        }
        final boolean dealt = deal.isPresent();

        final int dealSupply = !dealt ? 0 : deal.own() ? deal.total()
                : com.createstats.power.FeRates.plus(Math.max(0, deal.total() - circularGeneration()),
                        com.createstats.power.FeRates.plus(
                                Math.max(0, rawGeneration - rawControllerGeneration), legFeedGeneration));
        final int dealRun = Math.min(ceiling, Math.max(0, dealSupply - standing));

        final int dropUncovered = dealt && deal.own() ? Math.max(0, supplyDrop - deal.spare()) : 0;
        final int dealSpare = !dealt ? 0 : deal.own() ? Math.max(0, deal.spare() - supplyDrop) : deal.spare();

        final int useRun = dealt ? com.createstats.power.FeBoardDeal.runBudget(dealRun,
                deal.own() ? rideThrough : 0, deal.spare(), ceiling) : runBudget;
        final int useAdmit = dealt ? Math.min(useRun, Math.max(0, dealRun - dropUncovered)) : admitBudget;

        final int useCritical = dealt ? Math.min(ceiling, Math.max(useRun, dealRun + rideThrough)) : criticalBudget;

        final boolean useSurplus = dealt ? deal.restore() > 0 : surplus;

        final boolean useReserveLow = dealt ? deal.restore() <= 0 && deal.stance().isRationing() : reserveLow;

        final int useDrop = dealt ? dropUncovered : linked ? 0 : supplyDrop;
        final boolean useLimited = dealt ? totalDemand > ceiling : throughputLimited;
        lastDeal = dealt ? deal : com.createstats.power.FeBoardDeal.Answer.NONE;
        lastDealSupply = dealt ? dealSupply : 0;

        final int margin = Math.round((dealt ? dealSupply : gen) * burstMarginFraction());
        final long restoreSupply = dealt ? (long) useAdmit + dealSpare
                : linked ? (long) spendableGen + reserveRide
                : ownBudget.restoreSupply();
        final int useMargin = PduAutoController.restoreMargin(margin, useAdmit, restoreSupply);

        final PduAutoController.Supply supply = new PduAutoController.Supply(
                useRun, useAdmit, useMargin,
                belowFloor, useSurplus, useLimited, useCritical,
                survey.reserveDepth(), useReserveLow, supplyShock,

                useDrop);
        lastThroughputLimited = useLimited;
        lastCeiling = ceiling;

        lastAdmitBudget = useAdmit;
        lastCriticalBudget = Math.max(useCritical, useRun);
        lastSupplyDrop = useDrop;
        lastStanding = standing;
        lastChargeClaimUsed = chargeClaim;
        lastRideBudget = Math.max(0, useRun - Math.min(ceiling, Math.max(0, gen - standing)));
        lastBelowFloor = belowFloor;
        lastLinkedDecision = linked;

        supplyShock = false;

        final PduAutoController.Plan plan = autoController.decide(devices, supply, now);

        final Map<Long, PduReason> reasons = new HashMap<>(plan.reasons());
        reasons.putAll(screened.blocked());

        for (Map.Entry<Long, com.createstats.power.PduLegPlan.StationFit> e : screened.stations().entrySet()) {
            if (e.getValue().wireShort() && plan.isRunning(e.getKey())) {
                reasons.put(e.getKey(), PduReason.WIRE_SHORT);
            }
        }
        lastReasons = reasons;

        final List<FeGridNode> admitted = new ArrayList<>(plan.admitted().size());
        final java.util.Set<Long> admittedNodes = new HashSet<>();
        for (long key : plan.admitted()) {
            final FeGridNode node = byKey.get(key);

            if (node != null && admittedNodes.add(node.nodeKey())) {
                admitted.add(node);
            }
        }

        final boolean anyAdmitted = !admitted.isEmpty();
        int shed = 0;
        for (FeGridNode node : loads) {
            if (!plan.isRunning(node.nodeKey()) && !plan.isRunning(splitKey(node))) {
                setShedFlag(node, anyAdmitted);
                if (anyAdmitted) {
                    shed++;
                }
            }
        }

        final List<List<FeGridNode>> admittedByLeg = new ArrayList<>();
        for (int i = 0; i < LEGS; i++) {
            admittedByLeg.add(new ArrayList<>());
        }
        for (FeGridNode node : admitted) {
            admittedByLeg.get(legOf(node)).add(node);
        }

        final Map<Long, Integer> runAllowance = new HashMap<>(admitted.size() * 2);
        int boardDemand = 0;
        for (FeGridNode node : admitted) {
            if (isBoardNode(node) && plan.isRunning(splitKey(node))) {
                boardDemand = com.createstats.power.FeRates.plus(boardDemand,
                        Math.max(0, steadyDemandOf(node) - criticalDemandOf(node)));
            }
            final com.createstats.power.FeDeviceDemand demand = demandOf(node);

            runAllowance.put(node.nodeKey(),
                    demand.capacity() > 0 ? demand.steady() : FeGridSolver.NO_CEILING);
        }

        final Map<Long, Integer> parallelCost = screened.stations().isEmpty() ? Map.of() : new HashMap<>(2);
        for (FeGridNode node : admitted) {
            final int[] share = screened.shares().get(node.nodeKey());
            final com.createstats.power.PduLegPlan.StationFit fit = screened.stations().get(node.nodeKey());
            if (share == null || fit == null || !runAllowance.containsKey(node.nodeKey())) {
                continue;
            }
            parallelCost.put(node.nodeKey(), fit.fed());
            runAllowance.put(node.nodeKey(), share[legOf(node)]);
        }
        lastStationShares = screened.shares();

        final int rotation = legCursor++;

        final FePriority[] tiers = FePriority.values();
        final List<List<List<FeGridNode>>> byTier = new ArrayList<>(tiers.length);
        for (int t = 0; t < tiers.length; t++) {
            final List<List<FeGridNode>> tierLegs = new ArrayList<>(LEGS);
            for (int i = 0; i < LEGS; i++) {
                tierLegs.add(new ArrayList<>());
            }
            byTier.add(tierLegs);
        }
        final Map<Long, Integer> criticalAllowance = new HashMap<>(4);
        for (int leg = 0; leg < admittedByLeg.size(); leg++) {
            for (FeGridNode node : admittedByLeg.get(leg)) {
                final int tier = Math.max(0, Math.min(tiers.length - 1, priorityOrdinalOf(node)));
                if (tier == FePriority.CRITICAL.ordinal()) {
                    byTier.get(tier).get(leg).add(node);
                    continue;
                }
                final int cap = runAllowance.getOrDefault(node.nodeKey(), FeGridSolver.NO_CEILING);
                final int critical = Math.min(cap, criticalDemandOf(node));
                if (critical > 0) {
                    byTier.get(FePriority.CRITICAL.ordinal()).get(leg).add(node);
                    criticalAllowance.put(node.nodeKey(), critical);
                    if (cap > critical) {
                        byTier.get(tier).get(leg).add(node);
                        runAllowance.put(node.nodeKey(),
                                cap == FeGridSolver.NO_CEILING ? cap : cap - critical);
                    }
                    continue;
                }
                byTier.get(tier).get(leg).add(node);
            }
        }

        for (List<FeGridNode> here : byTier.get(FePriority.CRITICAL.ordinal())) {
            for (FeGridNode node : here) {
                criticalAllowance.putIfAbsent(node.nodeKey(),
                        runAllowance.getOrDefault(node.nodeKey(), FeGridSolver.NO_CEILING));
            }
        }
        final int[] legTiers = legPriorityOrdinals();
        int deliveredTotal = 0;
        for (int t = 0; t < tiers.length; t++) {
            final boolean critical = t == FePriority.CRITICAL.ordinal();
            final Map<Long, Integer> allowance = critical ? criticalAllowance : runAllowance;

            final List<FeGridNode> boards = critical
                    ? List.of() : gateBoardsToSpare(byTier.get(t), allowance, rotation);
            final Map<Long, Integer> before = boardFlowSnapshot(boards);
            deliveredTotal += runRound(byTier.get(t), Math.max(0, pool - deliveredTotal), rotation,
                    allowance, true, legTiers).delivered();
            bookBoardExport(boards, before);
            deliveredTotal += serveParallelFeeds(t, parallelCost, Math.max(0, pool - deliveredTotal));
        }
        final int remaining = Math.max(0, pool - deliveredTotal);

        deliveredTotal += fillBuffers(admittedByLeg, remaining, deliveredTotal, rotation);
        final FeGridSolver.BranchResult served =
                new FeGridSolver.BranchResult(deliveredTotal, admitted.size(), 0);

        for (FeGridNode node : admitted) {
            setShedFlag(node, false);
        }

        smoothDemand = smoothDemand < 0.0f ? totalDemand : smoothDemand + (totalDemand - smoothDemand) * TELEMETRY_EMA;

        smoothScreenedDemand = smoothScreenedDemand < 0.0f ? survivorDemand
                : smoothScreenedDemand + (survivorDemand - smoothScreenedDemand) * TELEMETRY_EMA;
        lastScreenedDemand = Math.max(0, Math.round(smoothScreenedDemand));
        lastGeneration = gen;
        lastDemand = Math.max(0, Math.round(smoothDemand));
        lastBoardDemand = boardDemand;
        lastBudget = supply.runBudget();
        lastServedDemand = plan.admittedDemand();

        lastScreenedShed = Math.max(0, survivorDemand - plan.admittedDemand());
        lastSurplus = supply.surplus();

        syncedTankCount = survey.tanks().size();
        syncedReserveDepth = survey.reserveDepth();
        syncedReserveTotal = survey.reserveTotal();
        syncedReserveCapacity = survey.reserveCapacity();
        syncedCoreRank = survey.coreRank();
        syncedCoreOnly = supply.reserveLow();
        syncedSourceCount = survey.sourceCount();

        final int netDrain = Math.max(0,
                plan.admittedDemand() + standing - gen);
        lastEnduranceTicks = netDrain > 0 ? Math.max(0, energy - floorEnergy) / netDrain : Integer.MAX_VALUE;

        return new FeGridSolver.BranchResult(served.delivered(), admitted.size(), shed);
    }

    private int fillBuffers(List<List<FeGridNode>> admittedByLeg, int poolLeft, int alreadySpent,
                            int rotation) {
        if (poolLeft <= 0) {
            return 0;
        }
        if (upstreamHoldsCharging()) {
            return 0;
        }
        final int spareMark = Math.round(bufferCapacity()
                * Math.max(restoreSocFraction(), reserveFloorPct() / 100.0f));
        int budget = Math.min(poolLeft, energy - Math.max(0, alreadySpent) - spareMark);
        if (budget <= 0) {
            return 0;
        }
        final Map<Long, Integer> fillAllowance = new HashMap<>();

        final List<List<FeGridNode>> fillLegs = withParallelFeeds(admittedByLeg);
        final Map<Long, Integer> fillGiven = parallelFeeds.isEmpty() ? null : new HashMap<>(2);
        int moved = 0;

        for (int i = 0; i < fillLegs.size() && budget > 0; i++) {
            final int leg = Math.floorMod(rotation + i, fillLegs.size());
            final List<FeGridNode> here = fillLegs.get(leg);
            if (here.isEmpty() || !isLegEnergised(leg)) {
                continue;
            }
            final List<FeGridNode> wanting = new ArrayList<>(here.size());
            fillAllowance.clear();
            for (FeGridNode node : here) {
                final int want = demandOf(node).fill()
                        - (fillGiven == null ? 0 : fillGiven.getOrDefault(node.nodeKey(), 0));
                if (want > 0) {
                    fillAllowance.put(node.nodeKey(), want);
                    wanting.add(node);
                }
            }
            if (wanting.isEmpty()) {
                continue;
            }

            final List<FeGridNode> fillBoards =
                    gateBoardsToSpare(List.of(wanting), fillAllowance, rotation);
            final Map<Long, Integer> beforeFill = boardFlowSnapshot(fillBoards);

            wanting.sort((a, b) -> Float.compare(demandOf(a).fillFraction(), demandOf(b).fillFraction()));
            int legBudget = Math.min(budget, legRoom(legOutflow, leg));
            if (legBudget <= 0) {
                continue;
            }

            for (List<FeGridNode> group : byCapGroup(wanting)) {
                if (legBudget <= 0) {
                    break;
                }
                final int pool = Math.min(legBudget, capRoomOf(group.get(0)));
                if (pool <= 0) {
                    spendCapOf(group.get(0), 0, 0, legBudget);
                    continue;
                }

                final Map<Long, Integer> flowBefore = fillGiven == null ? null : flowOf(group);
                final int got = FeGridSolver.deliver(group, pool, 0, branchFlowByNode, fillAllowance);
                if (flowBefore != null) {
                    for (FeGridNode node : group) {
                        final int delta = branchFlowByNode.getOrDefault(node.nodeKey(), 0)
                                - flowBefore.getOrDefault(node.nodeKey(), 0);
                        if (delta > 0) {
                            fillGiven.merge(node.nodeKey(), delta, Integer::sum);
                        }
                    }
                }
                spendCapOf(group.get(0), got, pool, legBudget);
                if (got > 0) {
                    spendLeg(legOutflow, leg, got);
                    moved += got;
                    budget -= got;
                    legBudget -= got;
                }
            }
            bookBoardExport(fillBoards, beforeFill);
        }
        return moved;
    }

    private List<List<FeGridNode>> withParallelFeeds(List<List<FeGridNode>> admittedByLeg) {
        if (parallelFeeds.isEmpty()) {
            return admittedByLeg;
        }
        final java.util.Set<Long> admitted = new java.util.HashSet<>();
        for (List<FeGridNode> here : admittedByLeg) {
            for (FeGridNode node : here) {
                admitted.add(node.nodeKey());
            }
        }
        final List<List<FeGridNode>> out = new ArrayList<>(admittedByLeg.size());
        for (List<FeGridNode> here : admittedByLeg) {
            out.add(new ArrayList<>(here));
        }
        for (Map.Entry<Long, List<ParallelFeed>> entry : parallelFeeds.entrySet()) {
            if (!admitted.contains(entry.getKey())) {
                continue;
            }
            for (ParallelFeed feed : entry.getValue()) {
                if (feed.leg() >= 0 && feed.leg() < out.size()) {
                    out.get(feed.leg()).add(feed.node());
                }
            }
        }
        return out;
    }

    private Map<Long, Integer> flowOf(List<FeGridNode> group) {
        final Map<Long, Integer> out = new HashMap<>(group.size() * 2);
        for (FeGridNode node : group) {
            out.put(node.nodeKey(), branchFlowByNode.getOrDefault(node.nodeKey(), 0));
        }
        return out;
    }

    private List<List<FeGridNode>> byCapGroup(List<FeGridNode> nodes) {
        if (capsOfNode.isEmpty()) {
            return List.of(nodes);
        }
        final Map<List<Long>, List<FeGridNode>> capped = new java.util.LinkedHashMap<>();
        final List<FeGridNode> free = new ArrayList<>(nodes.size());
        for (FeGridNode node : nodes) {
            final List<Long> caps = capsOfNode.get(node.nodeKey());
            if (caps == null || caps.isEmpty()) {
                free.add(node);
            } else {
                capped.computeIfAbsent(caps, key -> new ArrayList<>()).add(node);
            }
        }
        if (capped.isEmpty()) {
            return List.of(nodes);
        }
        final List<List<FeGridNode>> groups = new ArrayList<>(capped.size() + 1);
        groups.addAll(capped.values());
        if (!free.isEmpty()) {
            groups.add(free);
        }
        return groups;
    }

    public record BranchDevice(BlockPos pos, String name, int id, int leg, FePriority priority,
                               int draw, boolean shed, FeLock lock, PduReason reason,
                               boolean lighting, com.createstats.power.PduDeviceKind kind,
                               int stored, int capacity, int cost) {
        public BranchDevice(BlockPos pos, String name, int id, int leg, FePriority priority,
                            int draw, boolean shed, FeLock lock, PduReason reason,
                            boolean lighting) {
            this(pos, name, id, leg, priority, draw, shed, lock, reason, lighting,
                    com.createstats.power.PduDeviceKind.MACHINE, 0, 0, draw);
        }

        public BranchDevice(BlockPos pos, String name, int id, int leg, FePriority priority,
                            int draw, boolean shed, FeLock lock, PduReason reason,
                            boolean lighting, com.createstats.power.PduDeviceKind kind,
                            int stored, int capacity) {
            this(pos, name, id, leg, priority, draw, shed, lock, reason, lighting, kind,
                    stored, capacity, draw);
        }

        public BranchDevice {
            kind = kind == null ? com.createstats.power.PduDeviceKind.MACHINE : kind;
            stored = Math.max(0, stored);
            capacity = Math.max(0, capacity);
            cost = Math.max(0, cost);
        }

        public String label() {
            return com.createstats.power.PduDeviceIds.label(name, id, id <= 0);
        }

        public boolean isStorage() {
            return kind == com.createstats.power.PduDeviceKind.STORAGE;
        }

        public boolean isDark() {
            return shed || reason == PduReason.LOCKED_OFF || reason == PduReason.BREAKER_OPEN;
        }

        public float fill() {
            return capacity <= 0 ? 0.0f : Math.min(1.0f, stored / (float) capacity);
        }
    }

    public record BranchWiring(int segments, int buffered, int capacity) {
        public static final BranchWiring NONE = new BranchWiring(0, 0, 0);
    }

    private BranchWiring wiring = BranchWiring.NONE;

    private final com.createstats.power.PduDeviceIds deviceIds = new com.createstats.power.PduDeviceIds();

    private final com.createstats.power.PduDeviceOverrides overrides =
            new com.createstats.power.PduDeviceOverrides();

    private final Set<Long> panelOwnedDevices = new HashSet<>();

    private int overridePruneCountdown = OVERRIDE_PRUNE_INTERVAL;

    private static final int OVERRIDE_PRUNE_INTERVAL = 200;

    private record RowMeta(net.minecraft.world.level.block.Block block, String nameKey,
                           boolean lighting) {
    }

    private final Map<Long, RowMeta> rowMeta = new HashMap<>();

    public BranchWiring wiring() {
        return wiring;
    }

    private final List<BranchDevice> roster = new ArrayList<>();
    private PduMode mode = PduMode.MANUAL;

    private final PduHistory history1m = new PduHistory();
    private final PduHistory history5m = new PduHistory();
    private final PduHistory history15m = new PduHistory();

    private final Map<UUID, HistorySub> historySubs = new HashMap<>();

    private static final class HistorySub {
        int range;
        long lastSeen;
    }

    private static final long HISTORY_TIMEOUT = 100L;

    private static final double HISTORY_RANGE_SQ = 128.0 * 128.0;

    private List<PduTopology.BranchNode> topology = List.of();
    private boolean topologyTruncated;

    public List<BranchDevice> roster() {
        return roster;
    }

    public PduMode mode() {
        return mode;
    }

    public void setMode(PduMode newMode) {
        this.mode = newMode == null ? PduMode.MANUAL : newMode;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void cycleMode() {
        setMode(mode.next());
    }

    private interface DeviceSettings {
        FePriority priority();

        void setPriority(FePriority tier);

        FeLock lock();

        void setLock(FeLock lock);
    }

    @Nullable
    private DeviceSettings settingsAt(BlockPos pos) {
        if (level == null) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.createstats.power.IFeConsumer owner) {
            final FeConsumer fe = owner.feConsumer();
            return new DeviceSettings() {
                @Override
                public FePriority priority() {
                    return fe.priority();
                }

                @Override
                public void setPriority(FePriority tier) {
                    fe.setPriority(tier);
                }

                @Override
                public FeLock lock() {
                    return fe.lock();
                }

                @Override
                public void setLock(FeLock lock) {
                    fe.setLock(lock);

                    if (lock == FeLock.NONE) {
                        fe.setLoadShed(false);
                    }
                }
            };
        }
        if (be instanceof com.createstats.power.FeManagedLoad managed) {
            return new DeviceSettings() {
                @Override
                public FePriority priority() {
                    return managed.managedPriority();
                }

                @Override
                public void setPriority(FePriority tier) {
                    managed.setManagedPriority(tier);
                }

                @Override
                public FeLock lock() {
                    return managed.managedLock();
                }

                @Override
                public void setLock(FeLock lock) {
                    managed.setManagedLock(lock);
                }
            };
        }

        if (panelOwnedDevices.contains(pos.asLong())) {
            final long key = pos.asLong();
            return new DeviceSettings() {
                @Override
                public FePriority priority() {
                    return overrides.priority(key);
                }

                @Override
                public void setPriority(FePriority tier) {
                    overrides.setPriority(key, tier);
                    setChanged();
                }

                @Override
                public FeLock lock() {
                    return overrides.lock(key);
                }

                @Override
                public void setLock(FeLock lock) {
                    overrides.setLock(key, lock);
                    setChanged();
                }
            };
        }
        return null;
    }

    private void pruneOverrides() {
        if (level == null || (overrides.size() == 0 && rowMeta.isEmpty())) {
            return;
        }
        final Set<BlockPos> present = branchPositions();
        for (long packed : overrides.positions()) {
            final BlockPos pos = BlockPos.of(packed);
            if (level.isLoaded(pos) && !present.contains(pos)) {
                overrides.forget(packed);
            }
        }

        rowMeta.keySet().removeIf(packed -> {
            final BlockPos pos = BlockPos.of(packed);
            return level.isLoaded(pos) && !present.contains(pos);
        });
    }

    private boolean isSettable(BlockPos pos) {
        return pos != null && settablePositions().contains(pos);
    }

    public void cyclePriority(BlockPos devicePos) {
        if (level == null || level.isClientSide() || !isSettable(devicePos)) {
            return;
        }
        final DeviceSettings settings = settingsAt(devicePos);
        if (settings != null) {
            settings.setPriority(settings.priority().next());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void cycleLock(BlockPos devicePos) {
        if (level == null || level.isClientSide() || !isSettable(devicePos)) {
            return;
        }
        final DeviceSettings settings = settingsAt(devicePos);
        if (settings != null) {
            settings.setLock(settings.lock().next());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private Set<BlockPos> branchPositions() {
        final Set<BlockPos> positions = new HashSet<>(roster.size() * 2);
        for (BranchDevice d : roster) {
            positions.add(d.pos());
        }
        return positions;
    }

    private Set<BlockPos> settablePositions() {
        final Set<BlockPos> positions = new HashSet<>(roster.size() * 2);
        for (BranchDevice d : roster) {
            if (!d.isStorage()) {
                positions.add(d.pos());
            }
        }
        return positions;
    }

    public void bulkSetPriority(List<BlockPos> targets, int tierOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FePriority tier = FePriority.byOrdinal(tierOrdinal);
        boolean changed = false;
        for (BlockPos pos : com.createstats.power.PduBranchGuard.retainOnBranch(targets, settablePositions())) {
            final DeviceSettings settings = settingsAt(pos);
            if (settings != null) {
                settings.setPriority(tier);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void bulkSetLock(List<BlockPos> targets, int lockOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FeLock lock = FeLock.byOrdinal(lockOrdinal);
        boolean changed = false;
        for (BlockPos pos : com.createstats.power.PduBranchGuard.retainOnBranch(targets, settablePositions())) {
            final DeviceSettings settings = settingsAt(pos);
            if (settings != null) {
                settings.setLock(lock);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void setAllLightingTier(int tierOrdinal) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final FePriority tier = FePriority.byOrdinal(tierOrdinal);
        boolean changed = false;
        for (BranchDevice d : roster) {
            if (!d.lighting() || d.isStorage()) {
                continue;
            }
            final DeviceSettings settings = settingsAt(d.pos());
            if (settings != null) {
                settings.setPriority(tier);
                changed = true;
            }
        }
        if (changed) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void assignAutoPriorities() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (BranchDevice device : new ArrayList<>(roster)) {
            if (device.isStorage()) {
                continue;
            }
            final DeviceSettings settings = settingsAt(device.pos());
            if (settings != null) {
                settings.setPriority(autoTierFor(level.getBlockState(device.pos()).getBlock()));
            }
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    private static FePriority autoTierFor(net.minecraft.world.level.block.Block block) {
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !"create_stats".equals(id.getNamespace())) {
            return FePriority.DEFAULT;
        }
        final String path = id.getPath();

        if (path.endsWith("_powered_door")) {
            return FePriority.CRITICAL;
        }

        if (block instanceof com.createstats.block.BlastShutterBlock) {
            return com.createstats.blockentity.BlastShutterBE.defaultTier();
        }

        if (block instanceof com.createstats.block.SecurityLockerBlock) {
            return com.createstats.blockentity.SecurityLockerBE.defaultTier();
        }
        if (block instanceof com.createstats.block.SecuritySensorBlock sensor) {
            return com.createstats.blockentity.SecuritySensorBE.defaultTier(sensor.kind());
        }

        if (path.endsWith("_lamp")) {
            return FePriority.OPTIONAL;
        }
        return AUTO_TIERS.getOrDefault(path, FePriority.DEFAULT);
    }

    private static final Map<String, FePriority> AUTO_TIERS = Map.ofEntries(

            Map.entry("home_security_panel", FePriority.CRITICAL),

            Map.entry("security_relay", FePriority.CRITICAL),

            Map.entry("spark_crown", FePriority.CRITICAL),

            Map.entry("flight_control_computer", FePriority.CRITICAL),
            Map.entry("vcc_mk2", FePriority.CRITICAL),
            Map.entry("pathfinder_computer", FePriority.CRITICAL),

            Map.entry("power_distribution_unit", FePriority.HIGH),

            Map.entry("path_scanner", FePriority.HIGH),
            Map.entry("stats_alarm", FePriority.HIGH),
            Map.entry("battle_screen_display", FePriority.HIGH),

            Map.entry("mob_repeller", FePriority.HIGH),

            Map.entry("storm_coil", FePriority.HIGH),

            Map.entry("factory_logic_controller", FePriority.NORMAL),
            Map.entry("production_detector", FePriority.NORMAL),
            Map.entry("stats_display_panel", FePriority.NORMAL),
            Map.entry("stats_display_mk2", FePriority.NORMAL),
            Map.entry("stats_display_mk3", FePriority.NORMAL),
            Map.entry("stats_display_mk3_console", FePriority.NORMAL),
            Map.entry("vcc_mk3", FePriority.CRITICAL),
            Map.entry("pa_brain", FePriority.HIGH),
            Map.entry("klaxon_alarm", FePriority.HIGH),
            Map.entry("sentry_alarm", FePriority.HIGH),
            Map.entry("smart_home_panel", FePriority.NORMAL),
            Map.entry("topology_scanner_probe", FePriority.NORMAL),
            Map.entry("advanced_topology_scanner_probe", FePriority.NORMAL),
            Map.entry("industrial_topology_scanner_probe", FePriority.NORMAL),

            Map.entry("power_outlet", FePriority.HIGH),
            Map.entry("duplex_power_outlet", FePriority.HIGH),
            Map.entry("pa_speaker", FePriority.LOW),
            Map.entry("pa_megaphone", FePriority.LOW),

            Map.entry("starter_capacitor", FePriority.LOW),
            Map.entry("portable_power_interface", FePriority.LOW),

            Map.entry("track_feeder", FePriority.LOW),

            Map.entry("seismic_scanner", FePriority.LOW),

            Map.entry("arc_furnace", FePriority.LOW),
            Map.entry("induction_smelter", FePriority.LOW),
            Map.entry("fe_motor_mk1", FePriority.LOW),
            Map.entry("fe_motor_mk2", FePriority.LOW),
            Map.entry("fluid_pump_mk1", FePriority.LOW),
            Map.entry("fluid_pump_mk2", FePriority.LOW),
            Map.entry("bulk_washer", FePriority.LOW),

            Map.entry("vacuum_collector", FePriority.LOW),
            Map.entry("vacuum_sniffer", FePriority.LOW),
            Map.entry("vacuum_cleaner", FePriority.OPTIONAL),

            Map.entry("induction_range", FePriority.OPTIONAL),
            Map.entry("coffee_machine", FePriority.OPTIONAL),
            Map.entry("water_cooler", FePriority.OPTIONAL),
            Map.entry("air_fryer", FePriority.OPTIONAL),
            Map.entry("toaster", FePriority.OPTIONAL),
            Map.entry("bread_slicer", FePriority.OPTIONAL),

            Map.entry("oven", FePriority.OPTIONAL),
            Map.entry("stats_recorder", FePriority.NORMAL),
            Map.entry("dispatch_board", FePriority.NORMAL),
            Map.entry("mini_display", FePriority.NORMAL),

            Map.entry("holographic_projector", FePriority.LOW),

            Map.entry("status_tower_light", FePriority.OPTIONAL),
            Map.entry("stage_light", FePriority.OPTIONAL),
            Map.entry("stage_light_mk2", FePriority.OPTIONAL),
            Map.entry("projected_light", FePriority.OPTIONAL));

    private PduReason reasonFor(FeGridNode node, BlockPos devicePos) {
        final FeLock lock = lockOf(node);
        if (lock == FeLock.FORCE_OFF) {
            return PduReason.LOCKED_OFF;
        }

        if (!isLegEnergised(legOf(node))) {
            return PduReason.BREAKER_OPEN;
        }
        if (mode == PduMode.AUTO) {
            return lastReasons.getOrDefault(node.nodeKey(), PduReason.RUNNING);
        }
        if (lock == FeLock.FORCE_ON) {
            return PduReason.LOCKED_ON;
        }

        return manualShed.contains(node.nodeKey()) ? PduReason.TIER_SHED : PduReason.RUNNING;
    }

    private static final Set<String> LIGHTING_IDS = Set.of(
            "status_tower_light", "stage_light", "stage_light_mk2", "projected_light");

    private RowMeta metaOf(BlockPos at) {
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
        final net.minecraft.world.level.block.Block block = state.getBlock();
        final RowMeta cached = rowMeta.get(at.asLong());
        if (cached != null && cached.block() == block) {
            return cached;
        }
        final RowMeta fresh = new RowMeta(block, block.getDescriptionId(), isLighting(at, state));
        rowMeta.put(at.asLong(), fresh);
        return fresh;
    }

    private boolean isLighting(BlockPos at, net.minecraft.world.level.block.state.BlockState state) {
        if (level == null) {
            return false;
        }
        if (level.getBlockEntity(at) instanceof com.createstats.blockentity.LampBE) {
            return true;
        }
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "create_stats".equals(id.getNamespace()) && LIGHTING_IDS.contains(id.getPath());
    }

    private BlockPos devicePosOf(FeGridNode node) {
        if (node instanceof com.createstats.power.FeAttachment attachment) {
            return attachment.machine();
        }
        if (node instanceof PowerConnectorBE connector) {
            return connector.getBlockPos().relative(connector.getBlockState()
                    .getValue(com.createstats.block.PowerConnectorBlock.FACING).getOpposite());
        }
        if (node instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        return null;
    }

    private void rebuildRoster(List<FeGridNode> loads) {
        roster.clear();
        cachedRollCall = null;

        final Map<BlockPos, String> names = new HashMap<>(loads.size() * 2);
        final Map<String, Integer> nameCounts = new HashMap<>();
        downstreamPanels.clear();
        for (FeGridNode node : loads) {
            final BlockPos devicePos = devicePosOf(node);
            if (devicePos == null || node.attachedStorage() == null) {
                continue;
            }

            final RowMeta meta = metaOf(devicePos);
            if (meta.block() == ModBlocks.POWER_DISTRIBUTION_UNIT.get()) {
                downstreamPanels.add(devicePos.immutable());
            }

            final String name = meta.nameKey();
            names.put(devicePos, name);
            nameCounts.merge(name, 1, Integer::sum);
        }
        deviceIds.assignAll(names);

        panelOwnedDevices.clear();
        for (FeGridNode node : loads) {
            final BlockPos devicePos = devicePosOf(node);
            if (devicePos == null || node.attachedStorage() == null) {
                continue;
            }
            if (!(node.attachedStorage() instanceof FeConsumer) && managedLoadOf(node) == null) {
                panelOwnedDevices.add(devicePos.asLong());
            }
        }
        for (FeGridNode node : loads) {
            final IEnergyStorage attached = node.attachedStorage();

            final BlockPos devicePos = devicePosOf(node);
            if (devicePos == null || attached == null) {
                continue;
            }
            final String name = names.getOrDefault(devicePos, "");

            final PduReason reason = reasonFor(node, devicePos);

            roster.add(new BranchDevice(devicePos, name,
                    numberFor(devicePos, name, names, nameCounts), legOf(node),
                    FePriority.byOrdinal(priorityOrdinalOf(node)), rowDrawOf(node),
                    reason.isShed(), lockOf(node), reason, metaOf(devicePos).lighting(),
                    isBoardNode(node) ? com.createstats.power.PduDeviceKind.BOARD
                            : com.createstats.power.PduDeviceKind.MACHINE,
                    0, 0, steadyDemandOf(node)));
        }
        addStorageRows();

        roster.sort((a, b) -> {
            if (a.isStorage() != b.isStorage()) {
                return a.isStorage() ? 1 : -1;
            }
            return Integer.compare(a.priority().ordinal(), b.priority().ordinal());
        });
    }

    private void addStorageRows() {
        if (branchStorage.isEmpty()) {
            return;
        }
        final Map<BlockPos, String> names = new HashMap<>(branchStorage.size() * 2);
        final Map<String, Integer> nameCounts = new HashMap<>();
        for (FeGridNode node : branchStorage) {
            final BlockPos devicePos = storagePosOf(node);
            if (devicePos == null) {
                continue;
            }
            final String name = metaOf(devicePos).nameKey();
            names.put(devicePos, name);
            nameCounts.merge(name, 1, Integer::sum);
        }
        deviceIds.assignAll(names);
        for (FeGridNode node : branchStorage) {
            final BlockPos devicePos = storagePosOf(node);
            final IEnergyStorage tank = node.attachedStorage();
            if (devicePos == null || tank == null) {
                continue;
            }
            final String name = names.getOrDefault(devicePos, "");
            final int flow = storageFlowByNode.getOrDefault(node.nodeKey(), 0);

            roster.add(new BranchDevice(devicePos, name,
                    numberFor(devicePos, name, names, nameCounts), legOf(node),
                    FePriority.NORMAL, flow, false, FeLock.NONE,
                    storageReason(node, tank, flow), false,
                    com.createstats.power.PduDeviceKind.STORAGE,
                    Math.max(0, tank.getEnergyStored()), Math.max(0, tank.getMaxEnergyStored())));
        }
    }

    @Nullable
    private BlockPos storagePosOf(FeGridNode node) {
        return devicePosOf(node);
    }

    private com.createstats.power.PduReason storageReason(FeGridNode node, IEnergyStorage tank,
                                                          int flow) {
        if (!isLegEnergised(legOf(node))) {
            return com.createstats.power.PduReason.BREAKER_OPEN;
        }
        if (flow > 0) {
            return com.createstats.power.PduReason.STORAGE_CHARGING;
        }
        if (flow < 0) {
            return com.createstats.power.PduReason.STORAGE_DISCHARGING;
        }

        if (tank.getMaxEnergyStored() > 0 && tank.getEnergyStored() >= tank.getMaxEnergyStored()) {
            return com.createstats.power.PduReason.STORAGE_FULL;
        }
        return com.createstats.power.PduReason.STORAGE_IDLE;
    }

    private int numberFor(BlockPos pos, String name, Map<BlockPos, String> present,
                          Map<String, Integer> counts) {
        return counts.getOrDefault(name, 0) > 1 ? deviceIds.idFor(pos, name, present) : 0;
    }

    private int rosterSignature() {
        int h = mode.ordinal();
        h = h * 31 + lastBranch.served();
        h = h * 31 + lastBranch.shed();
        h = h * 31 + (lastSurplus ? 1 : 0);
        h = h * 31 + (lastThroughputLimited ? 1 : 0);

        h = h * 31 + syncedTankCount;
        h = h * 31 + syncedReserveDepth;
        h = h * 31 + (syncedCoreOnly ? 1 : 0);
        h = h * 31 + syncedSourceCount;
        h = h * 31 + (syncedFeedMisplaced ? 1 : 0);

        h = h * 31 + (lastDeal.isPresent() ? (lastDeal.own() ? 2 : 1) : 0);
        h = h * 31 + lastDeal.reason().ordinal();

        h = h * 31 + branchStorageSummary.tanks();
        h = h * 31 + branchStorageSummary.chargeRate() / 64;
        h = h * 31 + Math.round(branchStorageSummary.fill() * 64.0f);
        for (int leg = 0; leg < legClosed.length; leg++) {
            h = h * 31 + (legClosed[leg] ? 1 : 0);
            h = h * 31 + (legTripped[leg] ? 2 : 0);
            h = h * 31 + legPriority[leg].ordinal();

            h = h * 31 + (leg < com.createstats.power.PduStuds.COUNT ? studLinks.get(leg).size() : 0);
        }
        for (BranchDevice d : roster) {
            h = h * 31 + d.pos().hashCode();
            h = h * 31 + d.priority().ordinal();
            h = h * 31 + (d.shed() ? 1 : 0);
            h = h * 31 + d.lock().ordinal();
            h = h * 31 + d.reason().ordinal();
        }
        return h;
    }

    private void syncIfDue() {
        final int bucket = Math.max(1, bufferCapacity() / 64);
        ticksSinceSync++;
        final int signature = rosterSignature();
        final boolean rosterMoved = signature != lastRosterSignature;
        if (!rosterMoved && Math.abs(energy - lastSyncedEnergy) < bucket && ticksSinceSync < 20) {
            return;
        }
        if (rosterMoved) {
            rebuildTopology();
        }
        lastRosterSignature = signature;
        lastSyncedEnergy = energy;
        ticksSinceSync = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Served", lastBranch.served());
        tag.putInt("Shed", lastBranch.shed());
        tag.putInt("Delivered", lastBranch.delivered());
        tag.putIntArray("BranchStore", new int[] {
                branchStorageSummary.tanks(), branchStorageSummary.stored(),
                branchStorageSummary.capacity(), branchStorageSummary.chargeRate() });

        tag.putInt("BankWant", bankWant.want());
        tag.putLong("BankWantAt", bankWant.since());
        tag.putInt("Mode", mode.ordinal());

        tag.putInt("UpTier", upstreamTier.ordinal());
        tag.putBoolean("UpTierSet", upstreamTierChosen);
        tag.putInt("UpLock", upstreamLock.ordinal());
        tag.putBoolean("UpShed", isManagedShed());

        overrides.save(tag, "Ovr");
        tag.putInt("Gen", lastGeneration);

        tag.putIntArray("DayRates", supplyMemory.snapshotRates());
        tag.putIntArray("DaySamples", supplyMemory.snapshotSamples());
        tag.putIntArray("DayWxRates", weatherShareMemory.snapshotRates());
        tag.putIntArray("DayWxSamples", weatherShareMemory.snapshotSamples());
        tag.putInt("FcDrop", expectedSupplyDropFePerTick());
        tag.putInt("FcFloor", survey.hasForecast() ? survey.forecastFloor()
                : com.createstats.power.PduSupplySurvey.NO_FORECAST);
        tag.putInt("FcSources", survey.forecastSources());
        tag.putInt("DayWarm", supplyMemory.warmBuckets());
        tag.putInt("DayAhead", lastProjectedFe);
        tag.putInt("OutShort", outlook.shortfallFe());
        tag.putInt("OutDark", outlook.darkTicks());
        tag.putBoolean("OutSure", outlook.certain());

        tag.putInt("OutOwn", ownHoldFe);
        tag.putBoolean("OutOwnHeld", ownHoldApplied);
        tag.putInt("OutPeers", peerHoldFe);
        tag.putInt("OutCrit", outlook.neededFe() <= 0 ? 0 : outlook.neededFe() / OUTLOOK_TICKS);
        tag.putInt("SerStep", outlookSeries.stepTicks());
        tag.putIntArray("SerRate", outlookSeries.rate());
        tag.putIntArray("SerClr", outlookSeries.clear());
        tag.putLong("SerCold", outlookSeries.coldMask());
        tag.putLong("SerDay", outlookSeriesDayTime);
        tag.putInt("WxNow", outlookWeather.now().ordinal());
        tag.putInt("WxIn", outlookWeather.ticksToChange());
        tag.putInt("WxThen", outlookWeather.then().ordinal());

        tag.putInt("HoldFe", holdTargetFe());
        tag.putInt("EffFlr", effectiveReserveFloorPct());
        tag.putInt("Dmd", lastDemand);
        tag.putInt("Bud", lastBudget);

        tag.putInt("BudAdm", lastAdmitBudget);
        tag.putInt("BudCrit", lastCriticalBudget);
        tag.putInt("BudDrop", lastSupplyDrop);
        tag.putInt("BudStand", lastStanding);
        tag.putInt("BudClaim", lastChargeClaimUsed);
        tag.putInt("BudRide", lastRideBudget);
        tag.putBoolean("BudLow", lastBelowFloor);
        tag.putBoolean("BudLnk", lastLinkedDecision);

        final com.createstats.gui.pdu.PduFlowModel.SupplyParts parts = supplyBreakdown();
        tag.putInt("SupPri", parts.priced());
        tag.putInt("SupWx", parts.weatherShare());
        tag.putInt("SupObs", parts.observed());
        tag.putInt("SupLnk", parts.linked());
        tag.putInt("SupLeg", parts.legs());
        tag.putBoolean("SupAll", parts.allPriced());
        tag.putBoolean("SupCtl", parts.controllerLinked());
        tag.putIntArray("Deal", dealTag());
        tag.putInt("Svd", lastServedDemand);
        tag.putInt("End", lastEnduranceTicks);
        tag.putBoolean("Srp", lastSurplus);
        tag.putBoolean("Thr", lastThroughputLimited);
        tag.putInt("Ceil", lastCeiling);
        tag.putInt("LegsSeen", legsEverWired);
        tag.putInt("StrayCtl", strayControllerLegs);
        tag.putInt("StrayStud", strayPanelStudLegs);

        final com.createstats.power.FeReserveReport upstream = reserveReport();
        tag.putBoolean("LnkOn", upstream.isPresent());
        if (upstream.isPresent()) {
            tag.putInt("LnkSoc", upstream.socPct());
            tag.putInt("LnkStance", upstream.stance().ordinal());
            tag.putInt("LnkSust", upstream.sustainedDraw());
            tag.putInt("LnkGen", upstream.generation());
            tag.putInt("LnkFloor", upstream.floorPct());
            tag.putInt("LnkCeil", upstream.ceilingPct());
            tag.putInt("LnkCrit", upstream.criticalDraw());
            tag.putInt("LnkClaim", upstream.chargeClaim());
            tag.putInt("LnkStore", upstream.stored());
            tag.putInt("LnkCap", upstream.capacity());
            tag.putInt("LnkN", reserveReports.size());

            tag.putInt("LnkRate", upstream.netRate());
            tag.putInt("LnkShortT", linkedShortInTicks());
            tag.putInt("LnkSettle", linkedSettlePct());
        }

        tag.putInt("StoreN", storeCount);
        tag.putInt("StoreE", storeStored);
        tag.putInt("StoreC", storeCapacity);
        tag.putInt("StoreIn", storeCharged);

        tag.putInt("Cap", syncedCapacity);
        tag.putInt("Flr", syncedFloorPct);
        tag.putInt("Upk", syncedUpkeep);

        tag.putInt("RsvN", syncedTankCount);
        tag.putInt("RsvD", syncedReserveDepth);
        tag.putInt("RsvE", syncedReserveTotal);
        tag.putInt("RsvC", syncedReserveCapacity);
        tag.putInt("RsvR", syncedCoreRank);
        tag.putBoolean("RsvP", syncedCoreOnly);
        tag.putInt("Src", syncedSourceCount);
        tag.putBoolean("FeedX", syncedFeedMisplaced);
        final net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (BranchDevice d : roster) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", d.pos().asLong());

            e.putString("Nk", d.name());
            e.putInt("Id", d.id());
            e.putInt("Leg", d.leg());
            e.putInt("T", d.priority().ordinal());
            e.putInt("D", d.draw());

            if (d.cost() != d.draw()) {
                e.putInt("C", d.cost());
            }
            e.putBoolean("S", d.shed());
            e.putInt("L", d.lock().ordinal());
            e.putInt("R", d.reason().ordinal());
            e.putBoolean("Lt", d.lighting());

            if (d.isStorage()) {
                e.putInt("K", d.kind().ordinal());
                e.putInt("St", d.stored());
                e.putInt("Cp", d.capacity());
            } else if (d.kind() != com.createstats.power.PduDeviceKind.MACHINE) {
                e.putInt("K", d.kind().ordinal());
            }
            list.add(e);
        }
        tag.put("Roster", list);

        final int legCount = LEGS;
        final int[] legState = new int[legCount * 4];
        for (int leg = 0; leg < legCount; leg++) {
            legState[leg * 4] = (legClosed[leg] ? 1 : 0) | (legTripped[leg] ? 2 : 0)
                    | (legPriority[leg].ordinal() << 2);
            legState[leg * 4 + 1] = legCeiling(leg);
            legState[leg * 4 + 2] = legDemand(leg);
            legState[leg * 4 + 3] = legAdmitted(leg);
        }
        tag.putIntArray("Legs", legState);
        tag.putInt("WireN", wiring.segments());
        tag.putInt("WireE", wiring.buffered());
        tag.putInt("WireC", wiring.capacity());
        tag.putInt("FeedN", branchSources.size());
        tag.putInt("FeedE", branchSourceInput);

        tag.putInt("InFe", measuredInput);
        tag.putInt("OutFe", backExport);

        tag.putInt("OutDmd", exportDemand);
        tag.putInt("DnCtl", downstreamControllerCount());
        tag.putInt("DnFe", controllerDraw);
        tag.putInt("DnSoc", syncedDownstreamSocPct);
        tag.putBoolean("OutHeld", isExportHeld());
        tag.putInt("UpPan", upstreamPanelCount());
        tag.putInt("UpPanFe", upstreamPanelFeed);

        final net.minecraft.nbt.ListTag spans = new net.minecraft.nbt.ListTag();
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                final CompoundTag e = new CompoundTag();
                e.putByte("S", (byte) stud);
                e.putLong("P", link.to().asLong());
                e.putByte("T", (byte) link.tier().ordinal());
                spans.add(e);
            }
        }
        tag.put("StudWires", spans);

        final net.minecraft.nbt.ListTag topo = new net.minecraft.nbt.ListTag();
        for (PduTopology.BranchNode node : topology) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", node.pos().asLong());
            e.putByte("K", node.kind());
            e.putByte("T", node.tier());
            e.putBoolean("O", node.open());
            e.putInt("Pa", node.parentIndex());
            topo.add(e);
        }
        tag.put("Topo", topo);
        tag.putBoolean("TopoTrunc", topologyTruncated);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));
        lastBranch = new FeGridSolver.BranchResult(
                tag.getInt("Delivered"), tag.getInt("Served"), tag.getInt("Shed"));
        final int[] branchStore = tag.getIntArray("BranchStore");
        branchStorageSummary = branchStore.length == 4
                ? new BranchStorage(branchStore[0], branchStore[1], branchStore[2], branchStore[3])
                : BranchStorage.NONE;
        bankWant.restore(Math.max(0, tag.getInt("BankWant")), tag.getLong("BankWantAt"));
        if (tag.contains("Mode")) {
            mode = PduMode.byOrdinal(tag.getInt("Mode"));
        } else {
            mode = !tag.contains("Shedding") || tag.getBoolean("Shedding") ? PduMode.MANUAL : PduMode.OFF;
        }

        upstreamTier = tag.contains("UpTier")
                ? FePriority.byOrdinal(tag.getInt("UpTier")) : FePriority.DEFAULT;

        upstreamTierChosen = tag.contains("UpTierSet") ? tag.getBoolean("UpTierSet")
                : tag.contains("UpTier") && upstreamTier != FePriority.DEFAULT;
        upstreamLock = FeLock.byOrdinal(tag.getInt("UpLock"));
        overrides.load(tag, "Ovr");
        syncedManagedShed = tag.getBoolean("UpShed");
        lastGeneration = Math.max(0, tag.getInt("Gen"));
        supplyMemory.restore(tag.getIntArray("DayRates"), tag.getIntArray("DaySamples"));
        weatherShareMemory.restore(tag.getIntArray("DayWxRates"), tag.getIntArray("DayWxSamples"));

        syncedForecastFloor = tag.contains("FcFloor")
                ? tag.getInt("FcFloor") : com.createstats.power.PduSupplySurvey.NO_FORECAST;
        syncedForecastDrop = Math.max(0, tag.getInt("FcDrop"));
        syncedForecastSources = Math.max(0, tag.getInt("FcSources"));
        syncedDayWarmBuckets = Math.max(0, tag.getInt("DayWarm"));
        lastProjectedFe = Math.max(0, tag.getInt("DayAhead"));
        syncedOutlookShortfall = Math.max(0, tag.getInt("OutShort"));
        syncedOutlookDarkTicks = Math.max(0, tag.getInt("OutDark"));
        syncedOutlookCertain = tag.getBoolean("OutSure");
        syncedOwnHoldFe = Math.max(0, tag.getInt("OutOwn"));
        syncedOwnHeld = tag.getBoolean("OutOwnHeld");
        syncedPeerHoldFe = Math.max(0, tag.getInt("OutPeers"));
        syncedOutlookCritical = Math.max(0, tag.getInt("OutCrit"));
        syncedSeriesStep = Math.max(0, tag.getInt("SerStep"));
        syncedSeriesRate = tag.getIntArray("SerRate");
        syncedSeriesClear = tag.getIntArray("SerClr");
        syncedSeriesCold = tag.getLong("SerCold");
        syncedSeriesDayTime = tag.getLong("SerDay");
        syncedWeatherNow = tag.getInt("WxNow");
        syncedWeatherIn = tag.contains("WxIn") ? tag.getInt("WxIn")
                : com.createstats.power.FeSupplyOutlook.UNKNOWN_CHANGE;
        syncedWeatherThen = tag.getInt("WxThen");
        syncedHoldFe = Math.max(0, tag.getInt("HoldFe"));
        syncedEffectiveFloorPct = Math.max(0, tag.getInt("EffFlr"));
        lastDemand = Math.max(0, tag.getInt("Dmd"));
        lastBudget = Math.max(0, tag.getInt("Bud"));
        lastAdmitBudget = Math.max(0, tag.getInt("BudAdm"));
        lastCriticalBudget = Math.max(0, tag.getInt("BudCrit"));
        lastSupplyDrop = Math.max(0, tag.getInt("BudDrop"));
        lastStanding = Math.max(0, tag.getInt("BudStand"));
        lastChargeClaimUsed = Math.max(0, tag.getInt("BudClaim"));
        lastRideBudget = Math.max(0, tag.getInt("BudRide"));
        lastBelowFloor = tag.getBoolean("BudLow");
        lastLinkedDecision = tag.getBoolean("BudLnk");
        syncedSupPriced = Math.max(0, tag.getInt("SupPri"));
        syncedSupWeather = Math.max(0, tag.getInt("SupWx"));
        syncedSupObserved = Math.max(0, tag.getInt("SupObs"));
        syncedSupLinked = Math.max(0, tag.getInt("SupLnk"));
        syncedSupLegs = Math.max(0, tag.getInt("SupLeg"));
        syncedSupAllPriced = tag.getBoolean("SupAll");
        syncedSupControllerLinked = tag.getBoolean("SupCtl");
        syncedDeal = tag.getIntArray("Deal");
        lastServedDemand = Math.max(0, tag.getInt("Svd"));
        legsEverWired = tag.getInt("LegsSeen");
        syncedStrayControllerLegs = tag.getInt("StrayCtl");
        syncedStrayPanelStudLegs = tag.getInt("StrayStud");
        lastEnduranceTicks = tag.contains("End") ? tag.getInt("End") : Integer.MAX_VALUE;
        lastSurplus = tag.getBoolean("Srp");
        lastThroughputLimited = tag.getBoolean("Thr");
        lastCeiling = Math.max(0, tag.getInt("Ceil"));
        syncedLink = tag.getBoolean("LnkOn")
                ? new SyncedLink(tag.getInt("LnkSoc"), tag.getInt("LnkStance"),
                        tag.getInt("LnkSust"), tag.getInt("LnkGen"),
                        tag.getInt("LnkFloor"), tag.getInt("LnkCeil"),
                        tag.getInt("LnkCrit"), tag.getInt("LnkClaim"),
                        tag.getInt("LnkStore"), tag.getInt("LnkCap"))
                : null;
        syncedLinkCount = Math.max(syncedLink == null ? 0 : 1, tag.getInt("LnkN"));
        syncedLinkRate = tag.getInt("LnkRate");
        syncedOutlookShort = tag.contains("LnkShortT") ? tag.getInt("LnkShortT") : -1;
        syncedOutlookSettle = tag.contains("LnkSettle") ? tag.getInt("LnkSettle") : -1;
        storeCount = Math.max(0, tag.getInt("StoreN"));
        storeStored = Math.max(0, tag.getInt("StoreE"));
        storeCapacity = Math.max(0, tag.getInt("StoreC"));
        storeCharged = Math.max(0, tag.getInt("StoreIn"));
        syncedCapacity = Math.max(0, tag.getInt("Cap"));
        syncedUpkeep = Math.max(0, tag.getInt("Upk"));
        syncedFloorPct = tag.contains("Flr") ? Math.max(0, Math.min(90, tag.getInt("Flr"))) : 15;
        syncedTankCount = Math.max(0, tag.getInt("RsvN"));
        syncedReserveDepth = Math.max(0, tag.getInt("RsvD"));
        syncedReserveTotal = Math.max(0, tag.getInt("RsvE"));
        syncedReserveCapacity = Math.max(0, tag.getInt("RsvC"));
        syncedCoreRank = tag.contains("RsvR")
                ? com.createstats.power.FeReserve.clamp(tag.getInt("RsvR"))
                : com.createstats.power.FeReserve.DEFAULT_RANK;
        syncedCoreOnly = tag.getBoolean("RsvP");
        syncedSourceCount = Math.max(0, tag.getInt("Src"));
        syncedFeedMisplaced = tag.getBoolean("FeedX");
        roster.clear();
        cachedRollCall = null;
        final net.minecraft.nbt.ListTag list = tag.getList("Roster", 10);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag e = list.getCompound(i);
            roster.add(new BranchDevice(BlockPos.of(e.getLong("P")), rowName(e), e.getInt("Id"),
                    e.getInt("Leg"),
                    FePriority.byOrdinal(e.getInt("T")), e.getInt("D"), e.getBoolean("S"),
                    FeLock.byOrdinal(e.getInt("L")), PduReason.byOrdinal(e.getInt("R")),
                    e.getBoolean("Lt"),
                    com.createstats.power.PduDeviceKind.byOrdinal(e.getInt("K")),
                    e.getInt("St"), e.getInt("Cp"),
                    e.contains("C") ? e.getInt("C") : e.getInt("D")));
        }
        final int[] legState = tag.getIntArray("Legs");
        for (int leg = 0; leg < LEGS; leg++) {
            final int base = leg * 4;
            if (base + 3 >= legState.length) {
                break;
            }
            legClosed[leg] = (legState[base] & 1) != 0;
            legTripped[leg] = (legState[base] & 2) != 0;
            legPriority[leg] = FePriority.byOrdinal(legState[base] >> 2);
            syncedLegCeiling[leg] = legState[base + 1];
            syncedLegDemand[leg] = legState[base + 2];
            syncedLegAdmitted[leg] = legState[base + 3];
        }
        wiring = new BranchWiring(tag.getInt("WireN"), tag.getInt("WireE"), tag.getInt("WireC"));
        syncedBranchSources = Math.max(0, tag.getInt("FeedN"));
        syncedBranchSourceInput = Math.max(0, tag.getInt("FeedE"));
        measuredInput = Math.max(0, tag.getInt("InFe"));
        backExport = Math.max(0, tag.getInt("OutFe"));
        exportDemand = Math.max(0, tag.getInt("OutDmd"));
        syncedControllerCount = Math.max(0, tag.getInt("DnCtl"));
        syncedControllerDraw = Math.max(0, tag.getInt("DnFe"));
        syncedDownstreamSocPct = tag.contains("DnSoc") ? tag.getInt("DnSoc") : -1;
        syncedExportHeld = tag.getBoolean("OutHeld");
        syncedUpstreamPanels = Math.max(0, tag.getInt("UpPan"));
        syncedUpstreamPanelFeed = Math.max(0, tag.getInt("UpPanFe"));

        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        final net.minecraft.nbt.ListTag spans = tag.getList("StudWires", 10);
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag e = spans.getCompound(i);
            final int stud = e.getByte("S");
            if (stud < 0 || stud >= com.createstats.power.PduStuds.COUNT) {
                continue;
            }
            studLinks.get(stud).add(new StudLink(BlockPos.of(e.getLong("P")),
                    com.createstats.power.WireTier.byOrdinal(e.getByte("T"))));
        }

        final List<PduTopology.BranchNode> topo = new ArrayList<>();
        final net.minecraft.nbt.ListTag topoList = tag.getList("Topo", 10);
        for (int i = 0; i < topoList.size(); i++) {
            final CompoundTag e = topoList.getCompound(i);
            topo.add(new PduTopology.BranchNode(BlockPos.of(e.getLong("P")), e.getByte("K"), e.getByte("T"),
                    e.getBoolean("O"), e.getInt("Pa")));
        }
        topology = topo;
        topologyTruncated = tag.getBoolean("TopoTrunc");
    }

    private static String rowName(CompoundTag e) {
        final String key = e.getString("Nk");
        final String legacy = e.getString("N");
        if (key.isEmpty()) {
            return legacy;
        }
        final String resolved = Component.translatable(key).getString();
        return resolved.equals(key) && !legacy.isEmpty() ? legacy : resolved;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    private void recordHistory() {
        final long t = level.getGameTime();
        final boolean a1 = t % 4 == 0;
        final boolean a5 = t % 20 == 0;
        final boolean a15 = t % 60 == 0;
        if (!a1 && !a5 && !a15) {
            return;
        }
        final int supply = mode == PduMode.AUTO ? lastGeneration : measuredInput;

        final int demand = (mode == PduMode.AUTO ? lastDemand : currentRosterDemand())
                + exportDemand + upkeepFePerTick();
        final int shed = lastBranch.shed();
        if (a1) {
            history1m.record(supply, demand, shed);
        }
        if (a5) {
            history5m.record(supply, demand, shed);
        }
        if (a15) {
            history15m.record(supply, demand, shed);
        }
        if (!historySubs.isEmpty()) {
            broadcastHistory(a1, a5, a15);
        }
    }

    private int currentRosterDemand() {
        int d = 0;
        for (BranchDevice device : roster) {
            if (device.lock() != FeLock.FORCE_OFF && !device.isStorage()) {
                d += device.cost();
            }
        }
        return d;
    }

    private void broadcastHistory(boolean a1, boolean a5, boolean a15) {
        final MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        final long now = level.getGameTime();
        final Iterator<Map.Entry<UUID, HistorySub>> it = historySubs.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, HistorySub> entry = it.next();
            final ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            final HistorySub sub = entry.getValue();
            if (player == null || player.level() != level || now - sub.lastSeen > HISTORY_TIMEOUT
                    || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5) > HISTORY_RANGE_SQ) {
                it.remove();
                continue;
            }
            final boolean advanced = sub.range == 0 ? a1 : sub.range == 1 ? a5 : a15;
            if (advanced) {
                final PduHistory h = historyFor(sub.range);
                final int last = h.count() - 1;
                PacketDistributor.sendToPlayer(player, PduHistoryPacket.delta(worldPosition, sub.range,
                        h.supplyAt(last), h.demandAt(last), h.shedAt(last)));
            }
        }
    }

    private PduHistory historyFor(int range) {
        return range == 0 ? history1m : range == 1 ? history5m : history15m;
    }

    public void subscribeHistory(ServerPlayer player, int range) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int r = Mth.clamp(range, 0, 2);
        final HistorySub sub = historySubs.computeIfAbsent(player.getUUID(), k -> new HistorySub());
        sub.range = r;
        sub.lastSeen = level.getGameTime();
        final PduHistory h = historyFor(r);
        PacketDistributor.sendToPlayer(player, PduHistoryPacket.full(worldPosition, r,
                h.supplyChronological(), h.demandChronological(), h.shedChronological()));
    }

    public void keepAliveHistory(UUID id, int range) {
        final HistorySub sub = historySubs.get(id);
        if (sub != null && level != null) {
            sub.range = Mth.clamp(range, 0, 2);
            sub.lastSeen = level.getGameTime();
        }
    }

    public void unsubscribeHistory(UUID id) {
        historySubs.remove(id);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        historySubs.clear();
    }

    private void rebuildTopology() {
        if (level == null) {
            return;
        }
        final List<PduTopology.RawNode> raws = new ArrayList<>();
        final Set<BlockPos> added = new HashSet<>();
        final BlockPos root = worldPosition.immutable();
        final List<BlockPos> studWires = new ArrayList<>(wiresOnStuds() + 1);
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                studWires.add(link.to());
            }
        }

        studWires.add(worldPosition.relative(backFace()));
        raws.add(new PduTopology.RawNode(root, (byte) PduTopology.Kind.PDU.ordinal(), (byte) 0, false,
                studWires));
        added.add(root);

        final List<FeWireNetwork> networks = new ArrayList<>();
        for (int leg = 0; leg < LEGS; leg++) {
            for (FeWireNetwork network : legNetworks(leg)) {
                boolean seen = false;
                for (FeWireNetwork other : networks) {
                    if (other == network) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    networks.add(network);
                }
            }
        }
        for (FeWireNetwork network : networks) {
            for (com.createstats.power.FeGridMember member : network.members()) {
                if (member instanceof com.createstats.power.FeAttachment attachment) {
                    final BlockPos ap = attachment.machine().immutable();
                    if (added.add(ap)) {
                        raws.add(new PduTopology.RawNode(ap, (byte) PduTopology.Kind.DEVICE.ordinal(),
                                deviceTierByte(ap), false, List.of()));
                    }
                    continue;
                }
                if (!(member instanceof BlockEntity be)) {
                    continue;
                }
                final BlockPos p = be.getBlockPos().immutable();
                final PduTopology.Kind kind;
                byte tier = 0;
                List<BlockPos> links = List.of();
                if (member instanceof PowerConnectorBE connector) {
                    kind = PduTopology.Kind.CONNECTOR;
                    links = new ArrayList<>(connector.links());
                } else if (member instanceof com.createstats.power.FeWireTerminal terminal) {
                    kind = PduTopology.Kind.DEVICE;
                    tier = deviceTierByte(p);
                    links = new ArrayList<>(terminal.links());
                } else if (member instanceof CableBE cableNode) {
                    kind = switch (cableNode.tier()) {
                        case SURFACE -> PduTopology.Kind.CABLE;
                        case TRAY -> PduTopology.Kind.TRAY;
                        case DUCT -> PduTopology.Kind.DUCT;
                        case TRUNK -> PduTopology.Kind.TRUNK;
                    };
                } else if (member instanceof com.createstats.blockentity.IndoorCableBE) {
                    kind = PduTopology.Kind.CABLE;
                } else if (member instanceof SwitchBE) {
                    kind = PduTopology.Kind.SWITCH;
                } else {
                    kind = PduTopology.Kind.DEVICE;
                    tier = deviceTierByte(p);
                }
                if (added.add(p)) {
                    raws.add(new PduTopology.RawNode(p, (byte) kind.ordinal(), tier, false, links));
                }

                if (member instanceof PowerConnectorBE connector) {
                    final BlockPos machine = attachedMachinePos(connector);
                    if (machine != null && added.contains(machine)) {
                        continue;
                    }
                    if (machine != null && isPowerBlock(machine) && added.add(machine)) {
                        raws.add(new PduTopology.RawNode(machine, (byte) PduTopology.Kind.DEVICE.ordinal(),
                                deviceTierByte(machine), false, List.of()));
                    }
                }

                for (Direction dir : Direction.values()) {
                    final BlockPos np = p.relative(dir).immutable();
                    if (level.getBlockEntity(np) instanceof SwitchBE sw && !sw.isClosed() && added.add(np)) {
                        raws.add(new PduTopology.RawNode(np, (byte) PduTopology.Kind.SWITCH.ordinal(),
                                (byte) 0, true, List.of()));
                    }
                }
            }
        }

        final PduTopology.Result result = PduTopology.build(raws, root, PduTopology.MAX_NODES);
        topology = result.nodes();
        topologyTruncated = result.truncated();
    }

    private boolean isPowerBlock(BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        if (be instanceof com.createstats.power.IFeConsumer) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    pos, dir) != null) {
                return true;
            }
        }
        return false;
    }

    private byte deviceTierByte(BlockPos pos) {
        final DeviceSettings settings = settingsAt(pos);
        return (byte) (settings != null ? settings.priority().ordinal() : FePriority.DEFAULT.ordinal());
    }

    @Nullable
    private BlockPos attachedMachinePos(PowerConnectorBE connector) {
        if (!connector.getBlockState().hasProperty(com.createstats.block.PowerConnectorBlock.FACING)) {
            return null;
        }
        final Direction facing = connector.getBlockState().getValue(com.createstats.block.PowerConnectorBlock.FACING);
        return connector.getBlockPos().relative(facing.getOpposite()).immutable();
    }

    public List<PduTopology.BranchNode> topology() {
        return topology;
    }

    public boolean topologyTruncated() {
        return topologyTruncated;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Power Distribution Unit"));
        addPduTooltip(tooltip, isPlayerSneaking);
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    public void addPduTooltip(List<Component> tooltip, boolean detailed) {
        if (syncedFeedMisplaced) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal("New Age connector with no Coupler: fit a Universal Power Coupler between them")
                    .withStyle(ChatFormatting.RED)));
        }

        final int stray = firstStrayControllerLeg();
        if (stray >= 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(
                    com.createstats.gui.pdu.PduLegRow.name(stray)
                            + " runs to a Battery Controller input and carries nothing")
                    .withStyle(ChatFormatting.RED)));
            tooltip.add(com.createstats.power.FeGoggle.dim(
                    "Wire the stud to the connector that controller's input already holds"));
        }

        final int strayStud = firstStrayPanelStudLeg();
        if (strayStud >= 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(
                    com.createstats.gui.pdu.PduLegRow.name(strayStud)
                            + " runs to another panel's stud and carries nothing")
                    .withStyle(ChatFormatting.RED)));
            tooltip.add(com.createstats.power.FeGoggle.dim(
                    "A stud is an output on both boards. Land this leg on its side boss instead"));
        }
        tooltip.add(Component.literal("    ").append(Component
                .literal("Mode: " + mode.getDisplayName())
                .withStyle(mode == PduMode.AUTO ? ChatFormatting.GREEN
                        : mode == PduMode.MANUAL ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)));
        if (detailed) {
            tooltip.add(com.createstats.power.FeGoggle.amount("Buffer", energy, capacity()));
        }
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Branch: %d served, %d shed", lastBranch.served(), lastBranch.shed()))
                .withStyle(lastBranch.shed() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));

        if (detailed && branchStorageSummary.tanks() > 0) {
            final String charging = branchStorageSummary.chargeRate() > 0
                    ? String.format(Locale.ROOT, ", +%,d FE/t", branchStorageSummary.chargeRate())
                    : "";
            tooltip.add(Component.literal("    ").append(Component
                    .literal(String.format(Locale.ROOT, "Branch storage: %d %s, %,d of %,d FE (%d%%)%s",
                            branchStorageSummary.tanks(),
                            branchStorageSummary.tanks() == 1 ? "tank" : "tanks",
                            branchStorageSummary.stored(), branchStorageSummary.capacity(),
                            Math.round(branchStorageSummary.fill() * 100.0f), charging))
                    .withStyle(branchStorageSummary.chargeRate() > 0 ? ChatFormatting.AQUA
                            : ChatFormatting.GRAY)));
        }
        if (mode != PduMode.AUTO) {
            return;
        }

        if (isReserveLinked()) {
            addLinkedSupplyLines(tooltip);
        } else {
            tooltip.add(Component.literal("    ").append(Component
                    .literal(String.format(Locale.ROOT, "Gen %,d FE/t, running %,d of %,d",
                            lastGeneration, lastServedDemand, lastDemand))
                    .withStyle(lastDemand > lastGeneration ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
            tooltip.add(Component.literal("    ").append(Component
                    .literal("Reserve: " + rideText())
                    .withStyle(lastEnduranceTicks == Integer.MAX_VALUE ? ChatFormatting.GREEN
                            : lastEnduranceTicks < 200 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        }
        addForecastTooltip(tooltip, detailed);

        if (isReserveLinked()) {
            final com.createstats.power.FeReserveReport.Stance stance = linkedStance();
            final int controllers = Math.max(1, reserveLinkCount());
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Battery Controller%s: banks %d%%, %s",
                    controllers == 1 ? "" : " x" + controllers, linkedReserveSocPct(),
                    stance == null ? "linked" : stance.label()))
                    .withStyle(stance == null ? ChatFormatting.AQUA : switch (stance) {
                        case SURPLUS -> ChatFormatting.GREEN;
                        case NOMINAL -> ChatFormatting.AQUA;
                        case CONSERVE -> ChatFormatting.GOLD;
                        case CRITICAL -> ChatFormatting.RED;
                    })));
            if (linkedChargeClaim() > 0) {
                tooltip.add(Component.literal("    ").append(Component.literal(String.format(
                        Locale.ROOT, "Upstream is holding %,d FE/t back to refill the banks",
                        linkedChargeClaim())).withStyle(ChatFormatting.AQUA)));
            }
        }

        final com.createstats.power.FeBoardDeal.Answer deal = dealShown();
        if (deal.isPresent()) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "%s: asked %,d, granted %,d FE/t", deal.own() ? "Own banks" : "Controller",
                    deal.asked().total(), deal.total()))
                    .withStyle(deal.shortfall() > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
            if (deal.shortfall() > 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim(deal.reason().sentence()));
            }
            if (deal.own() && detailed) {
                tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                        "Banks %d%%, %s, default band. A Battery Controller sets your own",
                        Math.max(0, deal.socPct()), deal.stance().label())));
            }
        }
        if (upstreamPanelCount() > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Fed by %d Power Distribution Unit%s, %,d FE/t", upstreamPanelCount(),
                    upstreamPanelCount() == 1 ? "" : "s", upstreamPanelFeed()))
                    .withStyle(ChatFormatting.AQUA)));
        }
        if (backExport > 0 || exportDemand > 0) {
            final int soc = downstreamReserveSocPct();
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Exporting %,d FE/t out of the back boss%s", backExport,
                    soc >= 0 ? ", banks " + soc + "%" : ""))
                    .withStyle(ChatFormatting.AQUA)));

            if (exportDemand > 0 && lastGeneration > 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                        "Counted as demand: %,d FE/t of generation is spoken for", exportDemand)));
            }
        }
        if (isExportHeld()) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal("Back boss held: the buffer is below the mark, the branch comes first")
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (isManagedShed() && upstreamLock != FeLock.FORCE_OFF) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal("An upstream panel is not feeding this board right now")
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (upstreamLock == FeLock.FORCE_OFF) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal("Locked off from the panel upstream")
                    .withStyle(ChatFormatting.RED)));
        }

        if (syncedTankCount == 1) {
            final int pct = Math.round(reserveFillFraction() * 100.0f);
            tooltip.add(Component.literal("    ").append(Component
                    .literal(String.format(Locale.ROOT, "Bank: %,d of %,d FE upstream (%d%%)",
                            syncedReserveTotal, syncedReserveCapacity, pct))
                    .withStyle(syncedCoreOnly ? ChatFormatting.RED
                            : pct >= 40 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        }
        if (syncedTankCount > 1) {
            final int pct = Math.round(reserveFillFraction() * 100.0f);
            tooltip.add(Component.literal("    ").append(Component
                    .literal(String.format(Locale.ROOT, "Cascade: %,d FE upstream (%d%%), %d of %d tanks holding",
                            syncedReserveTotal, pct, syncedReserveDepth, syncedTankCount))
                    .withStyle(syncedCoreOnly ? ChatFormatting.RED
                            : pct >= 40 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
            if (syncedCoreOnly) {
                tooltip.add(Component.literal("    ").append(Component
                        .literal("Reserve low: what is left is held for Critical")
                        .withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    private void addLinkedSupplyLines(List<Component> tooltip) {
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Fed %,d FE/t, running %,d of %,d",
                        lastGeneration, lastServedDemand, lastDemand))
                .withStyle(lastDemand > lastGeneration ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
        final int rate = linkedReserveRate();
        final String banks = rate < 0 ? "banks covering " + com.createstats.power.ReserveWords.fe(-rate) + " FE/t"
                : rate > 0 ? "banks charging " + com.createstats.power.ReserveWords.fe(rate) + " FE/t"
                : "banks steady";
        tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                "Controller: generating %s FE/t, %s", com.createstats.power.ReserveWords.fe(linkedGeneration()),
                banks)).withStyle(rate < 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
        final int shortIn = linkedShortInTicks();
        final int settle = linkedSettlePct();
        final String text = com.createstats.power.ReserveWords.boardOutlook(shortIn, settle, rate);
        tooltip.add(Component.literal("    ").append(Component.literal("Reserve: " + text)
                .withStyle(shortIn == 0 ? ChatFormatting.GOLD
                        : shortIn < 0 ? ChatFormatting.GREEN
                        : shortIn < 1_200 ? ChatFormatting.RED
                        : shortIn < 12_000 ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
        if (storeCount > 0 && storeCharged > 0) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "Starter Capacitor%s refilling %s FE/t from the controller's spare",
                    storeCount == 1 ? "" : "s", com.createstats.power.ReserveWords.fe(storeCharged))));
        }
    }

    private String rideText() {
        if (lastEnduranceTicks == Integer.MAX_VALUE) {
            return "stable, generation covers the running set";
        }
        final int seconds = lastEnduranceTicks / 20;
        return String.format(Locale.ROOT, "%d:%02d remaining", seconds / 60, seconds % 60);
    }

    public TierRollCall tierRollCall() {
        if (cachedRollCall != null) {
            return cachedRollCall;
        }
        final int tiers = FePriority.values().length;
        final int[] total = new int[tiers];
        final int[] served = new int[tiers];
        for (BranchDevice device : roster) {
            if (device.lock() == FeLock.FORCE_OFF) {
                continue;
            }
            if (device.isStorage()) {
                continue;
            }
            final int i = device.priority().ordinal();
            total[i]++;
            if (!device.isDark()) {
                served[i]++;
            }
        }
        cachedRollCall = new TierRollCall(total, served);
        return cachedRollCall;
    }

    @Nullable
    private TierRollCall cachedRollCall;

    public record TierRollCall(int[] total, int[] served) {
    }

    public static int bufferCapacity() {
        try {
            return ModConfiguration.COMMON.pduBufferCapacity.get();
        } catch (RuntimeException ignored) {
            return 512000;
        }
    }

    public static int idleDrainPerTick() {
        try {
            return Math.max(0, ModConfiguration.COMMON.pduIdleDrain.get() / 20);
        } catch (RuntimeException ignored) {
            return 51;
        }
    }

    private static int reserveFloorPct() {
        try {
            return ModConfiguration.COMMON.pduReserveFloorPct.get();
        } catch (RuntimeException ignored) {
            return 15;
        }
    }

    private static float restoreSocFraction() {
        try {
            return ModConfiguration.COMMON.pduRestoreSocPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.45f;
        }
    }

    private static float burstMarginFraction() {
        try {
            return ModConfiguration.COMMON.pduBurstMarginPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.10f;
        }
    }

    private static int dwellTicks() {
        try {
            return ModConfiguration.COMMON.pduDwellTicks.get();
        } catch (RuntimeException ignored) {
            return 60;
        }
    }

    private static int restoreIntervalTicks() {
        try {
            return ModConfiguration.COMMON.pduRestoreIntervalTicks.get();
        } catch (RuntimeException ignored) {
            return 20;
        }
    }

    private static int rideHorizonTicks() {
        try {
            return Math.max(1, ModConfiguration.COMMON.pduRideHorizonTicks.get());
        } catch (RuntimeException ignored) {
            return 600;
        }
    }

    private static int supplyReadIntervalTicks() {
        try {
            return Math.max(1, ModConfiguration.COMMON.pduSupplyReadIntervalTicks.get());
        } catch (RuntimeException ignored) {
            return 10;
        }
    }

    private static float observedDecay() {
        try {
            return ModConfiguration.COMMON.pduObservedDecayPct.get() / 100.0f;
        } catch (RuntimeException ignored) {
            return 0.98f;
        }
    }

    private static int fillTicks() {
        try {
            return Math.max(1, ModConfiguration.COMMON.pduFillTicks.get());
        } catch (RuntimeException ignored) {
            return com.createstats.power.FeDeviceDemand.FILL_TICKS;
        }
    }

    private static com.createstats.power.PduLoadModel.Tuning loadModelTuning() {
        final com.createstats.power.PduLoadModel.Tuning defaults =
                com.createstats.power.PduLoadModel.Tuning.DEFAULT;
        try {
            return new com.createstats.power.PduLoadModel.Tuning(
                    ModConfiguration.COMMON.pduLearnTicks.get(), defaults.emaAlpha(),
                    defaults.peakDecay(), defaults.maxTracked());
        } catch (RuntimeException ignored) {
            return defaults;
        }
    }

    private static boolean exportPolicyEnabled() {
        try {
            return ModConfiguration.COMMON.pduExportPolicy.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static boolean coreProtectionEnabled() {
        try {
            return ModConfiguration.COMMON.pduCoreProtection.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static int coreProtectionPct() {
        try {
            return ModConfiguration.COMMON.pduCoreProtectionPct.get();
        } catch (RuntimeException ignored) {
            return 20;
        }
    }
}
