package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeGridNode;
import com.createstats.power.FeManagerView;
import com.createstats.power.FePriority;
import com.createstats.power.FeRatedLoad;
import com.createstats.power.FeReserve;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IFeConsumer;
import com.createstats.power.WireTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BatteryControllerBE extends BlockEntity
        implements FeStudPanel, com.createstats.power.FeManagedLoad,
        com.createstats.power.FeBoardPeer, com.createstats.power.FeSupply,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public record PortLink(BlockPos to, WireTier tier) {
    }

    public static final int MAX_LINKS_PER_PORT = 3;

    private final List<List<PortLink>> portLinks = newPortLinks();

    private int energy;

    private final boolean[] outputEnabled = {true, true, true};
    private final BatteryOutputMode[] outputMode =
            {BatteryOutputMode.DIRECT, BatteryOutputMode.DIRECT, BatteryOutputMode.DIRECT};
    private final int[] outputLimit = new int[BatteryControllerPorts.OUTPUTS];
    private final int[] outputFloorPct = new int[BatteryControllerPorts.OUTPUTS];
    private final FePriority[] outputPriority =
            {FePriority.NORMAL, FePriority.NORMAL, FePriority.NORMAL};

    private final boolean[] outputHeld = new boolean[BatteryControllerPorts.OUTPUTS];

    private final boolean[] inputEnabled = {true, true, true};

    private final int[] inputFlow = new int[BatteryControllerPorts.INPUTS];
    private final int[] outputFlow = new int[BatteryControllerPorts.OUTPUTS];
    private final int[] outputDemand = new int[BatteryControllerPorts.OUTPUTS];
    private final int[] outputCeiling = new int[BatteryControllerPorts.OUTPUTS];
    private final int[] outputDevices = new int[BatteryControllerPorts.OUTPUTS];
    private final BatteryControllerPlan.Reason[] outputReason =
            new BatteryControllerPlan.Reason[BatteryControllerPorts.OUTPUTS];
    private final int[] inputTanks = new int[BatteryControllerPorts.INPUTS];
    private final int[] inputSources = new int[BatteryControllerPorts.INPUTS];

    private final boolean[] inputPanelWired = new boolean[BatteryControllerPorts.INPUTS];

    private final int[] outputPanels = new int[BatteryControllerPorts.OUTPUTS];

    private final List<List<FeWireNetwork>> networksByPort = newPortNetworkLists();

    private final int[] reserveMoveByPort = new int[BatteryControllerPorts.INPUTS];

    private final Map<Long, Integer> tankPort = new HashMap<>();
    private final int[] outputCriticalDemand = new int[BatteryControllerPorts.OUTPUTS];

    private final int[] outputBoardAsked = new int[BatteryControllerPorts.OUTPUTS];
    private final int[] outputBoardGranted = new int[BatteryControllerPorts.OUTPUTS];
    private final com.createstats.power.FeBoardDeal.Reason[] outputBoardReason =
            new com.createstats.power.FeBoardDeal.Reason[BatteryControllerPorts.OUTPUTS];

    private final int[] outputBurstRelief = new int[BatteryControllerPorts.OUTPUTS];

    private final List<List<FeGridNode>> burstsByOutput = newNodeLists(BatteryControllerPorts.OUTPUTS);

    private final Set<Long> burstLoads = new HashSet<>();

    private final Map<Long, Integer> costByNode = new HashMap<>();

    private final Set<Long> outputTanks = new HashSet<>();

    private final int[] outputBurstIn = new int[BatteryControllerPorts.OUTPUTS];

    private final List<List<FeGridNode>> storesByPort = newNodeLists(BatteryControllerPorts.COUNT);

    private final Map<PowerDistributionUnitBE, FeGridNode> boardNodes = new java.util.IdentityHashMap<>();

    private final Map<Long, PowerDistributionUnitBE> boardOfNode = new HashMap<>();

    private final int[] outputStoreFlow = new int[BatteryControllerPorts.OUTPUTS];

    private final int[] inputStoreFlow = new int[BatteryControllerPorts.INPUTS];

    private final int[] inputCeiling = new int[BatteryControllerPorts.INPUTS];

    private final int[] portStoreCount = new int[BatteryControllerPorts.COUNT];

    private int storeCount;
    private int storeStored;
    private int storeCapacity;
    private int storeCharged;

    private int storeAsk;

    private com.createstats.power.ReserveProjection.Result outlook =
            com.createstats.power.ReserveProjection.Result.NONE;

    private final List<List<com.createstats.power.FeForecastingSource>> forecastByInput = newForecastLists();
    private long outlookTick = Long.MIN_VALUE;
    private int outlookHash;
    private static final int OUTLOOK_INTERVAL_TICKS = 40;

    private int syncedHealthyPct = com.createstats.power.FeReserveBand.DEFAULT_HEALTHY_PCT;
    private int syncedRideTicks = com.createstats.power.FeReserveBand.DEFAULT_RIDE_TICKS;
    private int syncedClaimPct = com.createstats.power.FeChargePolicy.DEFAULT_MAX_CLAIM_PCT;

    private int lastStoreBudget;
    private int lastAbsorbed;

    private int lastBankDrawn;
    private int lastBankCharged;

    private final com.createstats.power.FeServePolicy servePolicy = new com.createstats.power.FeServePolicy() {
        @Override
        public int tierOrdinalOf(FeGridNode node) {
            return burstLoads.contains(node.nodeKey()) ? FePriority.values().length - 1
                    : com.createstats.power.FeServePolicy.DEFAULT.tierOrdinalOf(node);
        }

        @Override
        public void markShed(FeGridNode node, boolean shed) {
            com.createstats.power.FeServePolicy.DEFAULT.markShed(node, shed);
        }
    };

    private final com.createstats.power.FeHoldBook holdBook = new com.createstats.power.FeHoldBook();

    private int syncedHoldFe;
    private int syncedHoldPct;
    private int syncedConfiguredFloorPct = com.createstats.power.FeReserveBand.DEFAULT_FLOOR_PCT;

    private final com.createstats.power.FeReserveBand.WantPeak bandWant =
            new com.createstats.power.FeReserveBand.WantPeak();

    private int syncedBandWant;

    @Nullable
    private com.createstats.power.FeBandSettings bandSettings;

    @Nullable
    private com.createstats.power.FeBandSettings syncedBandSettings;
    private boolean syncedBandCustom;
    private int syncedFullRate;

    private com.createstats.power.FeReserveReport reserveState =
            com.createstats.power.FeReserveReport.NONE;

    private int reserveEnergy;
    private int reserveCapacity;
    private int generation;
    private int totalDelivered;

    private final int[] pushedInputAccumulator = new int[BatteryControllerPorts.INPUTS];

    private final int[] pushedInput = new int[BatteryControllerPorts.INPUTS];

    private final int[] pushSpent = new int[BatteryControllerPorts.INPUTS];
    private final long[] pushTick = new long[BatteryControllerPorts.INPUTS];

    private final int[] pulledNow = new int[BatteryControllerPorts.INPUTS];
    private long pulledTick = Long.MIN_VALUE;

    private final int[] inputBurstFlow = new int[BatteryControllerPorts.INPUTS];

    private final Map<Long, Integer> boardCouldGive = new HashMap<>();

    private final Map<Long, Integer> boardDelivered = new HashMap<>();

    private static final int SHED_GRACE_TICKS = 10;

    private FePriority upstreamTier = FePriority.DEFAULT;
    private boolean upstreamTierChosen;

    private com.createstats.power.FeLock upstreamLock = com.createstats.power.FeLock.NONE;

    private long managedShedUntil = Long.MIN_VALUE;
    private int reserveDrawn;
    private int reserveCharged;

    private int chargeClaim;

    private float reserveRateFiltered;
    private int reserveRate;

    private final java.util.Map<Long, Integer> portFlowByNode = new HashMap<>();

    private final java.util.Map<FeWireNetwork, Long> anchorByGrid = new java.util.IdentityHashMap<>();

    public BatteryControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        java.util.Arrays.fill(pushTick, Long.MIN_VALUE);
    }

    private static List<List<PortLink>> newPortLinks() {
        final List<List<PortLink>> lists = new ArrayList<>(BatteryControllerPorts.COUNT);
        for (int i = 0; i < BatteryControllerPorts.COUNT; i++) {
            lists.add(new ArrayList<>(MAX_LINKS_PER_PORT));
        }
        return lists;
    }

    private static List<List<FeGridNode>> newNodeLists(int count) {
        final List<List<FeGridNode>> lists = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lists.add(new ArrayList<>(1));
        }
        return lists;
    }

    private static List<List<com.createstats.power.FeForecastingSource>> newForecastLists() {
        final List<List<com.createstats.power.FeForecastingSource>> lists =
                new ArrayList<>(BatteryControllerPorts.INPUTS);
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (energy > busCapacity()) {
            energy = busCapacity();
        }
        pruneLinks();

        final long now = level.getGameTime();

        int pulled = 0;
        int reserve = 0;
        int reserveCap = 0;
        final List<FeGridNode> tanks = new ArrayList<>();
        final Set<Long> countedTanks = new HashSet<>();
        final Set<BlockPos> seenDevices = new HashSet<>();

        final Set<IEnergyStorage> seenHandlers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        final List<PowerDistributionUnitBE> fedPanels = new ArrayList<>(1);
        for (List<FeWireNetwork> grids : networksByPort) {
            grids.clear();
        }
        java.util.Arrays.fill(reserveMoveByPort, 0);
        tankPort.clear();
        portFlowByNode.clear();
        anchorByGrid.clear();
        for (List<FeGridNode> bursts : burstsByOutput) {
            bursts.clear();
        }
        burstLoads.clear();
        costByNode.clear();
        outputTanks.clear();
        java.util.Arrays.fill(outputBurstIn, 0);
        for (List<FeGridNode> stores : storesByPort) {
            stores.clear();
        }
        boardNodes.clear();
        boardOfNode.clear();
        java.util.Arrays.fill(outputStoreFlow, 0);
        java.util.Arrays.fill(inputStoreFlow, 0);
        pulledTick = now;
        for (int port = 0; port < BatteryControllerPorts.INPUTS; port++) {
            inputFlow[port] = 0;
            pulledNow[port] = 0;
            inputBurstFlow[port] = 0;
            inputTanks[port] = 0;
            inputSources[port] = 0;
            inputPanelWired[port] = false;
            forecastByInput.get(port).clear();
            if (!inputEnabled[port]) {
                continue;
            }
            final List<PortGrid> grids = portGrids(port);

            final int ceiling = Math.min(portCeiling(port), conduitCeilingOf(grids));
            inputCeiling[port] = ceiling;
            for (PortGrid grid : grids) {
                final FeWireNetwork network = grid.network();
                network.markPduManaged(now);

                network.noteManagerView(FeManagerView.input(reserveEnergy, reserveCapacity,
                        totalBurstRelief()).withStoresFunded(!upstreamHoldsCharging()), now);
                networksByPort.get(port).add(network);
                anchorByGrid.putIfAbsent(network, grid.anchorKey());
                for (FeGridMember node : network.servedMembers()) {
                    final IEnergyStorage attached = node.attachedStorage();
                    if (attached == null) {
                        continue;
                    }

                    final com.createstats.power.FeNodeRole role =
                            com.createstats.power.FeNodeRole.of(attached, node.transferRate());
                    if (attached instanceof com.createstats.power.StarterPort.View
                            && role.kind() != com.createstats.power.FeNodeRole.Kind.GENERATOR) {
                        noteStore(port, node, attached, seenHandlers, seenDevices);
                        continue;
                    }
                    final boolean tank =
                            role.kind() == com.createstats.power.FeNodeRole.Kind.STORAGE;
                    if (!tank && role.kind() != com.createstats.power.FeNodeRole.Kind.GENERATOR) {
                        continue;
                    }
                    if (!seenHandlers.add(attached)) {
                        continue;
                    }
                    final BlockPos devicePos = devicePosOf(node);
                    if (devicePos == null || worldPosition.equals(devicePos)
                            || !seenDevices.add(devicePos.immutable())) {
                        continue;
                    }
                    if (tank) {
                        if (attached instanceof FeReserve pooled) {
                            final long key = pooled.reservePoolKey();
                            if (key != FeReserve.NOT_POOLED && !countedTanks.add(key)) {
                                continue;
                            }
                        }

                        reserve = com.createstats.power.FeRates.plus(reserve,
                                Math.max(0, attached.getEnergyStored()));
                        reserveCap = com.createstats.power.FeRates.plus(reserveCap,
                                Math.max(0, attached.getMaxEnergyStored()));
                        inputTanks[port]++;
                        tanks.add(node);

                        tankPort.put(node.nodeKey(), port);
                        continue;
                    }

                    inputSources[port]++;
                    final BlockEntity sourceBlock = level.getBlockEntity(devicePos);

                    if (sourceBlock instanceof com.createstats.power.FeForecastingSource forecasting
                            && !forecasting.relays()) {
                        forecastByInput.get(port).add(forecasting);
                    }
                    final int room = Math.max(0, busCapacity() - energy - pulled);

                    final int pushedHere = pushTick[port] == now ? pushSpent[port] : 0;
                    final int portLeft = Math.max(0, ceiling - inputFlow[port] - pushedHere);
                    int budget = Math.min(Math.min(node.transferRate(), portLeft), room);

                    final PowerDistributionUnitBE feeding =
                            sourceBlock instanceof PowerDistributionUnitBE p ? p : null;
                    if (feeding != null) {
                        final com.createstats.power.FeExportReport report = feeding.exportReport();
                        acceptExportReport(report);
                        budget = report.capped(budget);
                    }
                    if (budget <= 0) {
                        continue;
                    }

                    final PowerDistributionUnitBE fed = feeding;
                    final int drawn = fed != null ? fed.extractForBoard(budget, false)
                            : attached.extractEnergy(budget, false);
                    if (drawn > 0) {
                        pulled += drawn;
                        inputFlow[port] += drawn;
                        pulledNow[port] = inputFlow[port];
                        if (sourceBlock instanceof com.createstats.power.FeBurstSource) {
                            inputBurstFlow[port] += drawn;
                        }

                        if (fed != null) {
                            fed.noteControllerDraw(worldPosition, drawn);
                            if (!fedPanels.contains(fed)) {
                                fedPanels.add(fed);
                            }
                        }

                        portFlowByNode.merge(node.nodeKey(), -drawn, Integer::sum);
                    }
                }
            }
        }
        energy += pulled;

        int pushedTotal = 0;
        for (int port = 0; port < BatteryControllerPorts.INPUTS; port++) {
            pushedInput[port] = pushedInputAccumulator[port];
            pushedInputAccumulator[port] = 0;
            pushedTotal += pushedInput[port];

            if (pushedInput[port] > 0) {
                inputFlow[port] += pushedInput[port];
            }
        }
        generation = pulled + pushedTotal;
        reserveEnergy = reserve;
        reserveCapacity = reserveCap;
        findStrayPanelInputs();

        final List<List<FeGridNode>> loadsByOutput = new ArrayList<>(BatteryControllerPorts.OUTPUTS);
        final List<List<PowerDistributionUnitBE>> panelsByOutput =
                new ArrayList<>(BatteryControllerPorts.OUTPUTS);
        final List<BatteryControllerPlan.Output> plan =
                new ArrayList<>(BatteryControllerPorts.OUTPUTS);

        final Set<Long> panelNodes = new HashSet<>();

        final Set<BlockPos> inputDevices = new HashSet<>(seenDevices);
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final int port = BatteryControllerPorts.outputPort(i);
            final List<FeGridNode> loads = new ArrayList<>();
            final List<PowerDistributionUnitBE> panels = new ArrayList<>(1);
            final Set<BlockPos> boardsHere = new HashSet<>(1);
            int demand = 0;
            int criticalDemand = 0;

            final FeManagerView outputView = FeManagerView.output(reserve, reserveCap,
                    upstreamHoldsCharging(), outputBurstRelief[i]).withStoresFunded(!upstreamHoldsCharging());
            final List<PortGrid> grids = portGrids(port);
            for (PortGrid grid : grids) {
                final FeWireNetwork network = grid.network();
                network.markPduManaged(now);
                network.noteManagerView(outputView, now);
                networksByPort.get(port).add(network);
                anchorByGrid.putIfAbsent(network, grid.anchorKey());
                for (FeGridMember node : network.servedMembers()) {
                    final IEnergyStorage attached = node.attachedStorage();
                    if (attached == null) {
                        continue;
                    }

                    final com.createstats.power.FeNodeRole.Kind kind =
                            com.createstats.power.FeNodeRole.of(attached, node.transferRate()).kind();
                    if (kind == com.createstats.power.FeNodeRole.Kind.GENERATOR) {
                        noteOutputBurst(i, node, attached, seenHandlers, seenDevices);
                        continue;
                    }
                    if (attached instanceof com.createstats.power.StarterPort.View) {
                        noteStore(port, node, attached, seenHandlers, seenDevices);
                        continue;
                    }
                    if (kind != com.createstats.power.FeNodeRole.Kind.LOAD
                            && kind != com.createstats.power.FeNodeRole.Kind.STORAGE) {
                        continue;
                    }
                    if (!seenHandlers.add(attached)) {
                        continue;
                    }
                    final BlockPos devicePos = devicePosOf(node);
                    if (devicePos == null || worldPosition.equals(devicePos)) {
                        continue;
                    }
                    final BlockEntity device = level.getBlockEntity(devicePos);

                    final boolean board = device instanceof PowerDistributionUnitBE;
                    final boolean fresh = board
                            ? !inputDevices.contains(devicePos) && boardsHere.add(devicePos.immutable())
                            : seenDevices.add(devicePos.immutable());
                    if (!fresh) {
                        continue;
                    }
                    seenDevices.add(devicePos.immutable());
                    loads.add(node);
                    if (device instanceof PowerDistributionUnitBE panel) {
                        panels.add(panel);
                        panelNodes.add(node.nodeKey());
                        boardNodes.put(panel, node);
                        boardOfNode.put(node.nodeKey(), panel);
                        criticalDemand += Math.max(0, panel.declaredCriticalDemandFePerTick());
                    }
                    if (device instanceof com.createstats.power.FeBurstSource) {
                        burstLoads.add(node.nodeKey());
                    }
                    if (kind == com.createstats.power.FeNodeRole.Kind.STORAGE) {
                        outputTanks.add(node.nodeKey());
                    }
                    final int cost = costOf(node, attached, device);
                    costByNode.put(node.nodeKey(), cost);
                    demand += cost;
                }
            }
            loadsByOutput.add(loads);
            panelsByOutput.add(panels);
            outputDemand[i] = demand;
            outputDevices[i] = loads.size();

            outputCeiling[i] = Math.min(portCeiling(port), conduitCeilingOf(grids));
            outputPanels[i] = panels.size();
            outputCriticalDemand[i] = criticalDemand;
            plan.add(new BatteryControllerPlan.Output(!portLinks.get(port).isEmpty(),
                    outputEnabled[i], outputMode[i], demand, outputCeiling[i], outputLimit[i],
                    outputFloorPct[i], outputPriority[i], outputHeld[i], !panels.isEmpty(),
                    criticalDemand));
        }

        final int burst = drawOutputBursts();
        energy += burst;
        generation += burst;

        final int criticalWant = BatteryControllerPlan.criticalWant(plan);
        bandWant.update(criticalWant, now);
        reserveState = com.createstats.power.FeReserveBand.assess(worldPosition.asLong(),
                new com.createstats.power.FeReserveBand.Params(reserve, reserveCap, generation,
                        reserveRate, Math.min(reserveDischargeRate(), reserve),
                        Math.max(1, BatteryControllerPlan.wiredCeiling(plan)), effectiveBand(), rideTicks(),

                        reserveState.stance(),

                        criticalWant));

        final int allowanceNow = Math.min(
                Math.max(reserveState.sustainedDraw(), reserveState.criticalDraw()), reserve);

        final int steadyGeneration = Math.max(0, generation - burst - inputBurstTotal());
        final int claimable = com.createstats.power.FeChargePolicy.claimable(energy, allowanceNow, criticalWant);

        chargeClaim = Math.min(claimable, com.createstats.power.FeChargePolicy.claim(
                steadyGeneration, reservePct(), reserveCap, sizedBand(), chargeClaimPct(),
                chargeHeadroom(tanks)));
        if (upstreamHoldsCharging()) {
            chargeClaim = 0;
        }
        reserveState = reserveState.withChargeClaim(chargeClaim);

        final com.createstats.power.FeExportReport holding = firstHoldingReport();
        if (holding != null) {
            reserveState = reserveState.limitedBy(holding.stance(), holding.allowance());
        }

        for (List<PowerDistributionUnitBE> panels : panelsByOutput) {
            for (PowerDistributionUnitBE panel : panels) {
                panel.acceptReserveReport(reserveState);
            }
        }

        for (PowerDistributionUnitBE panel : fedPanels) {
            panel.noteDownstreamReserve(reserveState);
        }

        answerPushingPanels();

        final int fromReserve = Math.min(
                Math.max(reserveState.sustainedDraw(), reserveState.criticalDraw()), reserve);

        final BatteryControllerPlan.Plan decided = BatteryControllerPlan.plan(
                plan, generation, Math.max(0, energy + fromReserve - chargeClaim), reservePct(),
                chargeClaim);

        int delivered = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final BatteryControllerPlan.Grant grant = decided.grants().get(i);
            outputReason[i] = grant.reason();
            outputHeld[i] = grant.held();
            outputFlow[i] = 0;
            if (grant.granted() <= 0) {
                markShed(loadsByOutput.get(i), grant.reason().isWithheld());
                continue;
            }
            if (energy < grant.granted()) {
                final int need = grant.granted() - energy;
                final int drawn = drainReserve(tanks, need);
                energy += drawn;
                reserveDrawn += drawn;
            }

            markShed(loadsByOutput.get(i), false);
            final int served = serveOutput(loadsByOutput.get(i), Math.min(grant.granted(), energy), i);
            energy -= served;
            outputFlow[i] = served;
            delivered += served;
        }
        totalDelivered = delivered;

        answerBoards(panelsByOutput, loadsByOutput, panelNodes, plan, decided,
                holding != null || upstreamHoldsCharging(), now);

        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            outputBurstRelief[i] = burstReliefOf(i, loadsByOutput.get(i));
        }

        final boolean claimAllowed = holding == null && !upstreamHoldsCharging();
        boolean anyShort = false;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            anyShort |= com.createstats.power.ReserveProjection.shortOf(decided.grants().get(i), plan.get(i));
        }
        chargeStores(tanks, Math.max(0, fromReserve - reserveDrawn), anyShort, claimAllowed, panelsByOutput, now);

        lastAbsorbed = 0;
        if (energy > 0 && !tanks.isEmpty()) {
            final int spare = Math.min(energy, reserveChargeRate());
            final int stored = chargeReserve(tanks, spare);
            energy -= stored;
            reserveCharged += stored;
            lastAbsorbed = stored;
        }

        if (outlookTick == Long.MIN_VALUE || now - outlookTick >= OUTLOOK_INTERVAL_TICKS || now < outlookTick) {
            outlookTick = now;
            refreshOutlook(plan, loadsByOutput, panelNodes, steadyGeneration, claimAllowed, holding);
        }
        tellBoardsTheOutlook(panelsByOutput, decided, plan, now);

        tellBanksTheOutlook(tanks, now);

        reportPortThroughput(now);

        final int netReserve = reserveCharged - reserveDrawn;
        reserveRateFiltered = reserveRateFiltered * 0.75f + netReserve * 0.25f;
        reserveRate = reserveRateFiltered > 0.0f ? (int) Math.ceil(reserveRateFiltered - 0.001f)
                : reserveRateFiltered < 0.0f ? (int) Math.floor(reserveRateFiltered + 0.001f) : 0;
        lastBankDrawn = reserveDrawn;
        lastBankCharged = reserveCharged;
        reserveDrawn = 0;
        reserveCharged = 0;

        recordHistory();
        syncIfDue();
    }

    // a panel output stud wired into an input looks like a feed and carries nothing
    private void findStrayPanelInputs() {
        for (int port = 0; port < BatteryControllerPorts.INPUTS; port++) {
            if (inputTanks[port] > 0 || inputSources[port] > 0 || inputFlow[port] > 0
                    || !inputEnabled[port]) {
                continue;
            }

            found:
            for (FeWireNetwork grid : networksByPort.get(port)) {
                for (FeGridMember node : grid.servedMembers()) {
                    if (!(node instanceof com.createstats.power.FeWireTerminal terminal)) {
                        continue;
                    }
                    for (BlockPos far : terminal.links()) {
                        if (level.isLoaded(far)
                                && level.getBlockEntity(far) instanceof PowerDistributionUnitBE) {
                            inputPanelWired[port] = true;
                            break found;
                        }
                    }
                }
            }
        }
    }

    // rated cost where we know it. headroom is room left, not spend, 10x off on a big buffer
    private int costOf(FeGridNode node, IEnergyStorage attached, @Nullable BlockEntity device) {
        int cost = -1;
        if (device instanceof PowerDistributionUnitBE panel) {
            cost = Math.max(0, panel.intakeCeilingFePerTick());
        } else if (device instanceof FeRatedLoad rated) {
            cost = Math.max(0, rated.ratedDrawFePerTick());
        } else if (node instanceof FeRatedLoad rated) {
            cost = Math.max(0, rated.ratedDrawFePerTick());
        } else if (node instanceof IFeConsumer consumer) {
            cost = Math.max(0, consumer.feConsumer().settings().activeDrain());
        }
        if (cost < 0) {
            return attached.receiveEnergy(node.transferRate(), true);
        }

        return Math.min(cost, attached.receiveEnergy(node.transferRate(), true));
    }

    private int inputBurstTotal() {
        int total = 0;
        for (int fe : inputBurstFlow) {
            total = com.createstats.power.FeRates.plus(total, fe);
        }
        return total;
    }

    private static int chargeHeadroom(List<FeGridNode> tanks) {
        int room = 0;
        for (FeGridNode tank : tanks) {
            final IEnergyStorage storage = tank.attachedStorage();
            if (storage == null || !storage.canReceive()) {
                continue;
            }
            room += storage.receiveEnergy(tank.transferRate(), true);
        }
        return room;
    }

    private int serveOutput(List<FeGridNode> loads, int budget, int index) {
        if (loads.isEmpty() || budget <= 0) {
            return 0;
        }
        return com.createstats.power.FeGridSolver.serveByPriority(
                loads, budget, (int) (level.getGameTime() + index), portFlowByNode, null, servePolicy)
                .delivered();
    }

    private void noteOutputBurst(int i, FeGridMember node, IEnergyStorage attached,
                                 Set<IEnergyStorage> seenHandlers, Set<BlockPos> seenDevices) {
        final BlockPos devicePos = devicePosOf(node);
        if (devicePos == null || worldPosition.equals(devicePos)
                || !(level.getBlockEntity(devicePos) instanceof com.createstats.power.FeBurstSource)) {
            return;
        }
        if (seenHandlers.add(attached) && seenDevices.add(devicePos.immutable())) {
            burstsByOutput.get(i).add(node);
        }
    }

    private void noteStore(int port, FeGridMember node, IEnergyStorage attached,
                           Set<IEnergyStorage> seenHandlers, Set<BlockPos> seenDevices) {
        final BlockPos devicePos = devicePosOf(node);
        if (devicePos == null || worldPosition.equals(devicePos)) {
            return;
        }
        if (seenHandlers.add(attached) && seenDevices.add(devicePos.immutable())) {
            storesByPort.get(port).add(node);
        }
    }

    private void chargeStores(List<FeGridNode> tanks, int allowanceLeft, boolean anyShort, boolean claimAllowed,
                              List<List<PowerDistributionUnitBE>> panelsByOutput, long now) {
        int count = 0;
        int stored = 0;
        int capacity = 0;
        int ask = 0;
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            portStoreCount[port] = storesByPort.get(port).size();
            for (FeGridNode node : storesByPort.get(port)) {
                final IEnergyStorage store = node.attachedStorage();
                if (store == null) {
                    continue;
                }
                count++;
                stored = com.createstats.power.FeRates.plus(stored, Math.max(0, store.getEnergyStored()));
                capacity = com.createstats.power.FeRates.plus(capacity, Math.max(0, store.getMaxEnergyStored()));
                ask = com.createstats.power.FeRates.plus(ask, Math.min(
                        com.createstats.power.CapacitorDischarge.CHARGE_FE_PER_TICK,
                        Math.max(0, store.getMaxEnergyStored() - store.getEnergyStored())));
            }
        }
        int boardCharged = 0;

        final Set<PowerDistributionUnitBE> countedBoards =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (List<PowerDistributionUnitBE> panels : panelsByOutput) {
            for (PowerDistributionUnitBE panel : panels) {
                if (!countedBoards.add(panel)) {
                    continue;
                }
                count += panel.storeCount();
                stored = com.createstats.power.FeRates.plus(stored, panel.storeStored());
                capacity = com.createstats.power.FeRates.plus(capacity, panel.storeCapacity());
                ask = com.createstats.power.FeRates.plus(ask, panel.storeAskFePerTick());
                boardCharged = com.createstats.power.FeRates.plus(boardCharged, panel.storeCharged());
            }
        }
        storeCount = count;
        storeStored = stored;
        storeCapacity = capacity;
        storeAsk = ask;
        storeCharged = boardCharged;

        final int budget = com.createstats.power.ReserveProjection.storeBudget(energy, allowanceLeft,
                reserveState.stance(), anyShort, claimAllowed);
        lastStoreBudget = budget;
        if (budget <= 0 || ask <= 0) {
            return;
        }
        int left = budget;
        storeBankLeft = Math.max(0, budget - Math.max(0, energy));
        final int cursor = (int) now;
        final Set<PowerDistributionUnitBE> grantedBoards =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (int i = 0; i < BatteryControllerPorts.OUTPUTS && left > 0; i++) {
            if (!outputEnabled[i]) {
                continue;
            }
            final int cap = outputMode[i] == BatteryOutputMode.LIMIT
                    ? Math.min(outputCeiling[i], outputLimit[i]) : outputCeiling[i];
            int wire = Math.max(0, cap - outputFlow[i] - outputBurstIn[i]);
            final List<FeGridNode> direct = storesByPort.get(BatteryControllerPorts.outputPort(i));
            if (!direct.isEmpty() && wire > 0) {
                final int got = serveStores(direct, Math.min(left, wire), tanks, cursor + i);
                left -= got;
                wire -= got;
                outputStoreFlow[i] += got;
                storeCharged = com.createstats.power.FeRates.plus(storeCharged, got);
            }
            for (PowerDistributionUnitBE panel : panelsByOutput.get(i)) {
                if (left <= 0 || wire <= 0) {
                    break;
                }
                if (grantedBoards.contains(panel)) {
                    continue;
                }
                final int grant = Math.min(panel.storeAskFePerTick(), Math.min(left, wire));
                if (grant <= 0) {
                    continue;
                }
                panel.acceptStoreGrant(grant, now + 2);
                grantedBoards.add(panel);
                left -= grant;
                wire -= grant;
                final FeGridNode node = boardNodes.get(panel);
                if (node != null) {
                    outputStoreFlow[i] += serveStores(List.of(node), grant, tanks, cursor + i);
                }
            }
        }
        for (int port = 0; port < BatteryControllerPorts.INPUTS && left > 0; port++) {
            final List<FeGridNode> here = storesByPort.get(port);
            if (!inputEnabled[port] || here.isEmpty()) {
                continue;
            }
            final int wire = Math.max(0, inputCeiling[port] - inputStoreFlow[port]);
            final int got = serveStores(here, Math.min(left, wire), tanks, cursor + port);
            left -= got;
            inputStoreFlow[port] += got;
            reserveMoveByPort[port] += got;
            storeCharged = com.createstats.power.FeRates.plus(storeCharged, got);
        }
    }

    private int storeBankLeft;

    private int serveStores(List<FeGridNode> nodes, int budget, List<FeGridNode> tanks, int cursor) {
        if (budget <= 0) {
            return 0;
        }
        if (energy < budget && storeBankLeft > 0) {
            final int drawn = drainReserve(tanks, Math.min(budget - energy, storeBankLeft));
            energy += drawn;
            reserveDrawn += drawn;
            storeBankLeft -= drawn;
        }
        final int offer = Math.min(budget, Math.max(0, energy));
        if (offer <= 0) {
            return 0;
        }
        final int got = com.createstats.power.FeGridSolver.serveByPriority(
                nodes, offer, cursor, portFlowByNode, null, servePolicy).delivered();
        energy -= got;
        return got;
    }

    private void refreshOutlook(List<BatteryControllerPlan.Output> plan, List<List<FeGridNode>> loadsByOutput,
                                Set<Long> panelNodes, int steadyGeneration, boolean claimAllowed,
                                @Nullable com.createstats.power.FeExportReport holding) {
        final List<BatteryControllerPlan.Output> outs = new ArrayList<>(plan.size());
        for (int i = 0; i < plan.size(); i++) {
            final BatteryControllerPlan.Output o = plan.get(i);
            if (!o.managed() || i >= loadsByOutput.size()) {
                outs.add(o);
                continue;
            }
            int other = 0;
            for (FeGridNode node : loadsByOutput.get(i)) {
                if (!panelNodes.contains(node.nodeKey())) {
                    other = com.createstats.power.FeRates.plus(other, Math.max(0, costByNode.getOrDefault(node.nodeKey(), 0)));
                }
            }
            final int demand = Math.min(o.ceiling(), com.createstats.power.FeRates.plus(other, outputBoardAsked[i]));
            outs.add(new BatteryControllerPlan.Output(o.wired(), o.enabled(), o.mode(), demand, o.ceiling(),
                    o.limit(), o.floorPct(), o.priority(), o.wasHeld(), o.managed(), o.criticalDemand()));
        }

        outlook = com.createstats.power.ReserveProjection.project(new com.createstats.power.ReserveProjection.Setup(
                reserveEnergy, reserveCapacity, steadyGeneration, outs, effectiveBand(), rideTicks(),
                reserveDischargeRate(), reserveChargeRate(), chargeClaimPct(), portRate(), claimAllowed,
                Math.max(0, storeCapacity - storeStored), storeAsk, supplyForecast(), sizedBand(),
                holding == null ? null : holding.stance(), holding == null ? 0 : holding.allowance()));
        outlookHash = (java.util.Arrays.hashCode(outlook.trace()) * 31
                + java.util.Arrays.hashCode(outlook.packMilestones())) * 31
                + java.util.Arrays.hashCode(outlook.supply());
    }

    private com.createstats.power.SupplyForecast.Result supplyForecast() {
        final com.createstats.power.SupplyForecast.Builder supply = new com.createstats.power.SupplyForecast.Builder(
                BatteryControllerPorts.INPUTS, com.createstats.power.ReserveProjection.HORIZON_TICKS);
        for (int port = 0; port < BatteryControllerPorts.INPUTS; port++) {
            if (!inputEnabled[port]) {
                continue;
            }

            supply.port(port, Math.max(0, inputFlow[port] - inputBurstFlow[port]), inputCeiling[port]);
            for (com.createstats.power.FeForecastingSource source : forecastByInput.get(port)) {
                final com.createstats.power.SupplyForecast.Kind kind = source.weatherSensitive()
                        ? com.createstats.power.SupplyForecast.Kind.SUN
                        : source.burnsFuel() ? com.createstats.power.SupplyForecast.Kind.FUEL
                        : com.createstats.power.SupplyForecast.Kind.STEADY;
                supply.add(port, kind, source::potentialFePerTickIn);
            }
        }
        return supply.build();
    }

    private void tellBanksTheOutlook(List<FeGridNode> tanks, long now) {
        if (tanks.isEmpty() || level == null) {
            return;
        }
        final com.createstats.power.ReserveProjection.Milestone rest =
                outlook.first(com.createstats.power.ReserveProjection.Kind.SETTLES);
        final com.createstats.power.ReserveProjection.Milestone full =
                outlook.first(com.createstats.power.ReserveProjection.Kind.FULL);
        final int elapsed = (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - outlookTick));
        final int settlePct = rest == null ? -1 : rest.value();
        final int fullIn = full == null ? -1 : Math.max(1, full.tick() - elapsed);
        for (FeGridNode tank : tanks) {
            if (!(tank instanceof FeGridMember member)) {
                continue;
            }
            final BlockPos pos = devicePosOf(member);
            if (pos != null && level.getBlockEntity(pos) instanceof CapacitorBankBE bank) {
                bank.acceptManagerOutlook(settlePct, fullIn, now + 10);
            }
        }
    }

    private void tellBoardsTheOutlook(List<List<PowerDistributionUnitBE>> panelsByOutput,
                                      BatteryControllerPlan.Plan decided, List<BatteryControllerPlan.Output> plan,
                                      long now) {
        final com.createstats.power.ReserveProjection.Milestone rest =
                outlook.first(com.createstats.power.ReserveProjection.Kind.SETTLES);
        final int elapsed = (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - outlookTick));
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (panelsByOutput.get(i).isEmpty()) {
                continue;
            }
            final boolean shortHere = com.createstats.power.ReserveProjection.shortOf(decided.grants().get(i),
                    plan.get(i)) || outputBoardGranted[i] < outputBoardAsked[i];
            final com.createstats.power.ReserveProjection.Milestone first =
                    outlook.first(com.createstats.power.ReserveProjection.Kind.SHORT, i);
            final int shortIn = shortHere ? 0 : first == null ? -1 : Math.max(1, first.tick() - elapsed);
            for (PowerDistributionUnitBE panel : panelsByOutput.get(i)) {
                panel.acceptReserveOutlook(shortIn, rest == null ? -1 : rest.value(), now + 10);
            }
        }
    }

    private int drawOutputBursts() {
        int drawn = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final List<FeGridNode> bursts = burstsByOutput.get(i);
            if (bursts.isEmpty() || !outputEnabled[i]) {
                continue;
            }
            for (FeGridNode node : bursts) {
                final IEnergyStorage source = node.attachedStorage();
                final int room = Math.max(0, busCapacity() - energy - drawn);
                final int wire = Math.max(0, outputCeiling[i] - outputBurstIn[i]);
                final int ask = Math.min(Math.min(node.transferRate(), wire), room);
                if (source == null || ask <= 0) {
                    continue;
                }
                final int got = source.extractEnergy(ask, false);
                if (got > 0) {
                    drawn += got;
                    outputBurstIn[i] += got;

                    portFlowByNode.merge(node.nodeKey(), -got, Integer::sum);
                }
            }
        }
        return drawn;
    }

    private int burstReliefOf(int i, List<FeGridNode> loads) {
        if (loads.isEmpty()) {
            return 0;
        }
        final BatteryControllerPlan.Reason reason = outputReason[i];
        final int cap = outputMode[i] == BatteryOutputMode.LIMIT
                ? Math.min(outputCeiling[i], outputLimit[i]) : outputCeiling[i];
        final boolean reachable = outputEnabled[i] && outputFlow[i] < cap
                && reason != BatteryControllerPlan.Reason.OFF
                && reason != BatteryControllerPlan.Reason.UNWIRED
                && reason != BatteryControllerPlan.Reason.FLOOR
                && reason != BatteryControllerPlan.Reason.RATIONED;
        final com.createstats.power.BurstRelief relief = new com.createstats.power.BurstRelief();
        for (FeGridNode node : loads) {
            final long key = node.nodeKey();

            relief.add(costByNode.getOrDefault(key, 0), portFlowByNode.getOrDefault(key, 0),
                    burstLoads.contains(key) || outputTanks.contains(key), reachable);
        }
        return relief.total();
    }

    private int totalBurstRelief() {
        int total = 0;
        for (int relief : outputBurstRelief) {
            total = com.createstats.power.FeRates.plus(total, relief);
        }
        return total;
    }

    private static void markShed(List<FeGridNode> loads, boolean shed) {
        for (FeGridNode node : loads) {
            if (node.attachedStorage() instanceof com.createstats.power.FeConsumer fe) {
                fe.setLoadShed(shed);
            } else if (node instanceof com.createstats.power.FeManagedLoad managed) {
                managed.setManagedShed(shed);
            }
        }
    }

    private int drainReserve(List<FeGridNode> tanks, int amount) {
        if (tanks.isEmpty() || amount <= 0) {
            return 0;
        }
        final List<FeGridNode> ordered = orderedByRank(tanks, false);
        int taken = 0;
        for (FeGridNode tank : ordered) {
            if (taken >= amount) {
                break;
            }
            final IEnergyStorage storage = tank.attachedStorage();
            if (storage == null || !storage.canExtract()) {
                continue;
            }
            final int drawn = storage.extractEnergy(
                    Math.min(amount - taken, tank.transferRate()), false);
            taken += drawn;
            noteReserveMove(tank, drawn, false);
        }
        return taken;
    }

    private int chargeReserve(List<FeGridNode> tanks, int amount) {
        if (tanks.isEmpty() || amount <= 0) {
            return 0;
        }
        final List<FeGridNode> ordered = orderedByRank(tanks, true);
        int placed = 0;
        for (FeGridNode tank : ordered) {
            if (placed >= amount) {
                break;
            }
            final IEnergyStorage storage = tank.attachedStorage();
            if (storage == null || !storage.canReceive()) {
                continue;
            }
            final int stored = storage.receiveEnergy(
                    Math.min(amount - placed, tank.transferRate()), false);
            placed += stored;
            noteReserveMove(tank, stored, true);
        }
        return placed;
    }

    private void noteReserveMove(FeGridNode tank, int moved, boolean charging) {
        if (moved <= 0) {
            return;
        }
        portFlowByNode.merge(tank.nodeKey(), charging ? moved : -moved, Integer::sum);
        final Integer port = tankPort.get(tank.nodeKey());
        if (port != null && port >= 0 && port < reserveMoveByPort.length) {
            reserveMoveByPort[port] += moved;
        }
    }

    private static List<FeGridNode> orderedByRank(List<FeGridNode> tanks, boolean coreFirst) {
        if (tanks.size() < 2) {
            return tanks;
        }
        final List<FeGridNode> ordered = new ArrayList<>(tanks);
        ordered.sort((a, b) -> {
            final int rankA = com.createstats.power.FeGridSolver.reserveRankOf(a);
            final int rankB = com.createstats.power.FeGridSolver.reserveRankOf(b);
            if (rankA != rankB) {
                return coreFirst ? Integer.compare(rankA, rankB) : Integer.compare(rankB, rankA);
            }
            return Long.compare(a.nodeKey(), b.nodeKey());
        });
        return ordered;
    }

    @Nullable
    private BlockPos devicePosOf(FeGridMember node) {
        if (node instanceof com.createstats.power.FeAttachment attachment) {
            return attachment.machine();
        }
        if (node instanceof PowerConnectorBE connector) {
            return connector.getBlockPos().relative(connector.mountedOn());
        }
        if (node instanceof BlockEntity be) {
            return be.getBlockPos();
        }
        return null;
    }

    private void reportPortThroughput(long now) {
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            final List<FeWireNetwork> grids = networksByPort.get(port);
            if (grids.isEmpty()) {
                continue;
            }

            final int moved = port < BatteryControllerPorts.INPUTS
                    ? inputFlow[port] + reserveMoveByPort[port]
                    : outputFlow[BatteryControllerPorts.outputIndex(port)]
                            + outputBurstIn[BatteryControllerPorts.outputIndex(port)];
            if (moved <= 0) {
                continue;
            }
            for (FeWireNetwork grid : grids) {
                grid.reportManagedThroughput(moved, now);
            }
        }
        reportGridFlows(now);
    }

    private void reportGridFlows(long now) {
        reportedGrids.clear();
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            final List<FeWireNetwork> grids = networksByPort.get(port);
            if (grids.isEmpty()) {
                continue;
            }
            for (FeWireNetwork grid : grids) {
                boolean already = false;
                for (FeWireNetwork seen : reportedGrids) {
                    if (seen == grid) {
                        already = true;
                        break;
                    }
                }
                if (already) {
                    continue;
                }
                reportedGrids.add(grid);
                final long anchor = anchorByGrid.getOrDefault(
                        grid, com.createstats.power.FeFlowGraph.NO_BOUNDARY);
                gridFlows.clear();
                for (FeGridMember member : grid.servedMembers()) {
                    final Integer fe = portFlowByNode.get(member.nodeKey());
                    if (fe != null && fe != 0) {
                        gridFlows.put(member.nodeKey(), fe);
                    }
                }
                grid.reportManagedFlows(gridFlows, anchor, now);
            }
        }
    }

    private final java.util.Map<Long, Integer> gridFlows = new HashMap<>();

    private final List<FeWireNetwork> reportedGrids = new ArrayList<>(4);

    private static List<List<FeWireNetwork>> newPortNetworkLists() {
        final List<List<FeWireNetwork>> lists = new ArrayList<>(BatteryControllerPorts.COUNT);
        for (int i = 0; i < BatteryControllerPorts.COUNT; i++) {
            lists.add(new ArrayList<>(2));
        }
        return lists;
    }

    private List<FeWireNetwork> portNetworks(int port) {
        final List<PortGrid> grids = portGrids(port);
        final List<FeWireNetwork> found = new ArrayList<>(grids.size());
        for (PortGrid grid : grids) {
            found.add(grid.network());
        }
        return found;
    }

    private record PortGrid(FeWireNetwork network, long anchorKey) {
    }

    private List<PortGrid> portGrids(int port) {
        if (level == null || !BatteryControllerPorts.isPortIndex(port)
                || portLinks.get(port).isEmpty()) {
            return List.of();
        }
        final List<PortGrid> found = new ArrayList<>(2);
        for (PortLink link : portLinks.get(port)) {
            if (!level.isLoaded(link.to())) {
                continue;
            }

            final FeWireNetwork network =
                    FeWireNetwork.liveNetworkOf(level.getBlockEntity(link.to()));
            if (network == null) {
                continue;
            }

            boolean seen = false;
            for (PortGrid other : found) {
                if (other.network() == network) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                found.add(new PortGrid(network, link.to().asLong()));
            }
        }
        return found;
    }

    private static int conduitCeilingOf(List<PortGrid> grids) {
        if (!FeWireNetwork.cableLimitEnabled() || grids.isEmpty()) {
            return com.createstats.power.FeGridSolver.NO_CEILING;
        }
        int total = 0;
        for (PortGrid grid : grids) {
            final int ceiling = grid.network().conduitCeilingFrom(grid.anchorKey());
            if (ceiling >= com.createstats.power.FeGridSolver.NO_CEILING) {
                return com.createstats.power.FeGridSolver.NO_CEILING;
            }
            total += ceiling;
        }
        return total;
    }

    public int portCeiling(int port) {
        if (!BatteryControllerPorts.isPortIndex(port) || portLinks.get(port).isEmpty()) {
            return 0;
        }
        if (BatteryControllerPorts.isInput(port)) {
            long wires = 0;
            for (PortLink link : portLinks.get(port)) {
                wires += (long) PowerConnectorBE.baseTransferRate() * link.tier().multiplier();
            }
            return (int) Math.min(inputRate(), wires);
        }
        WireTier best = null;
        for (PortLink link : portLinks.get(port)) {
            if (best == null || link.tier().multiplier() > best.multiplier()) {
                best = link.tier();
            }
        }
        final int wireRate = PowerConnectorBE.baseTransferRate()
                * (best == null ? 1 : best.multiplier());
        return Math.min(portRate(), wireRate);
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public int studLinkCount(int stud) {
        return BatteryControllerPorts.isPortIndex(stud) ? portLinks.get(stud).size() : 0;
    }

    @Override
    public int maxLinksPerStud() {
        return MAX_LINKS_PER_PORT;
    }

    @Override
    public boolean isLinkedToConnector(BlockPos connector) {
        for (List<PortLink> links : portLinks) {
            for (PortLink link : links) {
                if (link.to().equals(connector)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void linkStud(int stud, com.createstats.power.FeWireTerminal terminal, WireTier tier) {
        if (level == null || level.isClientSide() || !BatteryControllerPorts.isPortIndex(stud)) {
            return;
        }
        portLinks.get(stud).add(new PortLink(terminal.terminalPos().immutable(),
                tier == null ? WireTier.COPPER : tier));
        terminal.addLink(worldPosition, tier);
        syncLinks();
    }

    @Override
    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || !BatteryControllerPorts.isPortIndex(stud)) {
            return 0;
        }
        final List<PortLink> links = portLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (PortLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);

            if (level.getBlockEntity(link.to())
                    instanceof com.createstats.power.FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
        syncLinks();
        return removed;
    }

    @Override
    public void forgetLinksTo(BlockPos connector) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean changed = false;
        for (List<PortLink> links : portLinks) {
            changed |= links.removeIf(link -> link.to().equals(connector));
        }
        if (changed) {
            syncLinks();
        }
    }

    public void dropAllWires() {
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            dropStudWires(port);
        }
    }

    private void pruneLinks() {
        if (level == null) {
            return;
        }
        boolean changed = false;
        for (List<PortLink> links : portLinks) {
            changed |= links.removeIf(link -> level.isLoaded(link.to())
                    && !(level.getBlockEntity(link.to())
                    instanceof com.createstats.power.FeWireTerminal terminal
                    && terminal.links().contains(worldPosition)));
        }
        if (changed) {
            syncLinks();
        }
    }

    private void syncLinks() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public int inputPortLinkedTo(BlockPos connector) {
        for (int port = 0; port < BatteryControllerPorts.INPUTS; port++) {
            for (PortLink link : portLinks.get(port)) {
                if (link.to().equals(connector)) {
                    return port;
                }
            }
        }
        return -1;
    }

    public boolean hasInputLinkTo(BlockPos connector) {
        return inputPortLinkedTo(connector) >= 0;
    }

    public List<PortLink> links(int port) {
        return BatteryControllerPorts.isPortIndex(port) ? portLinks.get(port) : List.of();
    }

    public boolean isOutputEnabled(int index) {
        return inRange(index) && outputEnabled[index];
    }

    public void setOutputEnabled(int index, boolean enabled) {
        if (inRange(index) && outputEnabled[index] != enabled) {
            outputEnabled[index] = enabled;
            setChanged();
            syncNow();
        }
    }

    public BatteryOutputMode outputMode(int index) {
        return inRange(index) ? outputMode[index] : BatteryOutputMode.DIRECT;
    }

    public void setOutputMode(int index, BatteryOutputMode mode) {
        if (inRange(index) && mode != null && outputMode[index] != mode) {
            outputMode[index] = mode;
            setChanged();
            syncNow();
        }
    }

    public int outputLimit(int index) {
        return inRange(index) ? outputLimit[index] : 0;
    }

    public void setOutputLimit(int index, int limit) {
        if (inRange(index)) {
            final int clamped = Math.max(0, Math.min(limit, portRate()));
            if (outputLimit[index] != clamped) {
                outputLimit[index] = clamped;
                setChanged();
                syncNow();
            }
        }
    }

    public int outputFloorPct(int index) {
        return inRange(index) ? outputFloorPct[index] : 0;
    }

    public void setOutputFloorPct(int index, int pct) {
        if (inRange(index)) {
            final int clamped = Math.max(0, Math.min(95, pct));
            if (outputFloorPct[index] != clamped) {
                outputFloorPct[index] = clamped;
                setChanged();
                syncNow();
            }
        }
    }

    public FePriority outputPriority(int index) {
        return inRange(index) ? outputPriority[index] : FePriority.NORMAL;
    }

    public void setOutputPriority(int index, FePriority priority) {
        if (inRange(index) && priority != null && outputPriority[index] != priority) {
            outputPriority[index] = priority;
            setChanged();
            syncNow();
        }
    }

    public boolean isInputEnabled(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS && inputEnabled[index];
    }

    public void setInputEnabled(int index, boolean enabled) {
        if (index >= 0 && index < BatteryControllerPorts.INPUTS && inputEnabled[index] != enabled) {
            inputEnabled[index] = enabled;
            setChanged();
            syncNow();
        }
    }

    private static boolean inRange(int index) {
        return index >= 0 && index < BatteryControllerPorts.OUTPUTS;
    }

    public int busEnergy() {
        return energy;
    }

    public int reserveEnergy() {
        return reserveEnergy;
    }

    public int reserveCapacity() {
        return reserveCapacity;
    }

    public int reservePct() {
        return reserveCapacity <= 0 ? 0
                : (int) Math.max(0, Math.min(100, (long) reserveEnergy * 100L / reserveCapacity));
    }

    public int generationFePerTick() {
        return generation;
    }

    public int deliveredFePerTick() {
        return totalDelivered;
    }

    @Override
    public int potentialFePerTick() {
        return Math.max(0, totalDelivered);
    }

    public int reserveRateFePerTick() {
        return reserveRate;
    }

    public int enduranceTicks() {
        if (reserveRate > 0) {
            final int headroom = reserveCapacity - reserveEnergy;
            return headroom <= 0 ? -1 : headroom / reserveRate;
        }
        if (reserveRate < 0) {
            return reserveEnergy <= 0 ? -1 : reserveEnergy / -reserveRate;
        }
        return -1;
    }

    public int inputFlow(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS ? inputFlow[index] : 0;
    }

    public int inputTankCount(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS ? inputTanks[index] : 0;
    }

    public int inputSourceCount(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS ? inputSources[index] : 0;
    }

    public boolean inputHasStrayPanel(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS && inputPanelWired[index]
                && inputTanks[index] == 0 && inputSources[index] == 0;
    }

    public boolean hasStrayPanelFeed() {
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            if (inputHasStrayPanel(i)) {
                return true;
            }
        }
        return false;
    }

    public int outputFlow(int index) {
        return inRange(index) ? outputFlow[index] : 0;
    }

    public int outputDemand(int index) {
        return inRange(index) ? outputDemand[index] : 0;
    }

    public int outputCeiling(int index) {
        return inRange(index) ? outputCeiling[index] : 0;
    }

    public int outputDeviceCount(int index) {
        return inRange(index) ? outputDevices[index] : 0;
    }

    public BatteryControllerPlan.Reason outputReason(int index) {
        final BatteryControllerPlan.Reason reason = inRange(index) ? outputReason[index] : null;
        return reason == null ? BatteryControllerPlan.Reason.IDLE : reason;
    }

    private int lastSyncSignature = Integer.MIN_VALUE;
    private int ticksSinceSync;

    private static final int SYNC_GAP_TICKS = 4;

    private static final int SYNC_HEARTBEAT_TICKS = 40;

    private void syncIfDue() {
        ticksSinceSync++;
        final int signature = readoutSignature();
        if (signature == lastSyncSignature && ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            return;
        }
        if (signature != lastSyncSignature && ticksSinceSync < SYNC_GAP_TICKS) {
            return;
        }
        lastSyncSignature = signature;
        syncNow();
    }

    private void syncNow() {
        ticksSinceSync = 0;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private int readoutSignature() {
        int h = reserveCapacity <= 0 ? 0
                : (int) ((long) reserveEnergy * 1000L / Math.max(1, reserveCapacity));
        h = h * 31 + generation / 16;
        h = h * 31 + totalDelivered / 16;
        h = h * 31 + reserveRate / 16;
        h = h * 31 + chargeClaim / 16;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            h = h * 31 + outputFlow[i] / 16;
            h = h * 31 + outputDemand[i] / 16;
            h = h * 31 + outputReason(i).ordinal();
            h = h * 31 + outputDevices[i];
        }
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            h = h * 31 + inputFlow[i] / 16;
            h = h * 31 + inputTanks[i] * 7 + inputSources[i];
        }

        h = h * 31 + storeCharged / 16;
        h = h * 31 + (storeCapacity <= 0 ? 0 : (int) ((long) storeStored * 100L / storeCapacity));
        h = h * 31 + outlookHash;
        h = h * 31 + lastBankDrawn / 64;
        h = h * 31 + lastBankCharged / 64;
        return h;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);

        final ListTag ports = new ListTag();
        for (List<PortLink> links : portLinks) {
            final CompoundTag portTag = new CompoundTag();
            final long[] targets = new long[links.size()];
            final byte[] tiers = new byte[links.size()];
            for (int i = 0; i < links.size(); i++) {
                targets[i] = links.get(i).to().asLong();
                tiers[i] = (byte) links.get(i).tier().ordinal();
            }
            portTag.putLongArray("To", targets);
            portTag.putByteArray("Tiers", tiers);
            ports.add(portTag);
        }
        tag.put("Ports", ports);

        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final CompoundTag out = new CompoundTag();
            out.putBoolean("On", outputEnabled[i]);
            out.putInt("Mode", outputMode[i].ordinal());
            out.putInt("Limit", outputLimit[i]);
            out.putInt("Floor", outputFloorPct[i]);
            out.putInt("Prio", outputPriority[i].ordinal());
            out.putBoolean("Held", outputHeld[i]);

            out.putInt("Flow", outputFlow[i]);
            out.putInt("Want", outputDemand[i]);
            out.putInt("Ceil", outputCeiling[i]);
            out.putInt("Devs", outputDevices[i]);
            out.putInt("Why", outputReason(i).ordinal());
            out.putInt("Panels", outputPanels[i]);
            out.putInt("Crit", outputCriticalDemand[i]);
            out.putInt("BAsk", outputBoardAsked[i]);
            out.putInt("BGot", outputBoardGranted[i]);
            out.putInt("BWhy", outputBoardReason(i).ordinal());
            out.putInt("Stores", portStoreCount[BatteryControllerPorts.outputPort(i)]);
            out.putInt("SFlow", outputStoreFlow[i]);
            tag.put("Out" + i, out);
        }
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            final CompoundTag in = new CompoundTag();
            in.putBoolean("On", inputEnabled[i]);
            in.putInt("Flow", inputFlow[i]);
            in.putInt("Tanks", inputTanks[i]);
            in.putInt("Srcs", inputSources[i]);
            in.putBoolean("PanelX", inputPanelWired[i]);
            in.putInt("Stores", portStoreCount[i]);
            in.putInt("SFlow", inputStoreFlow[i]);
            in.putInt("BMove", reserveMoveByPort[i]);
            tag.put("In" + i, in);
        }

        tag.putInt("StoreN", storeCount);
        tag.putInt("StoreE", storeStored);
        tag.putInt("StoreC", storeCapacity);
        tag.putInt("StoreIn", storeCharged);
        tag.putInt("StoreAsk", storeAsk);
        tag.putInt("StoreBud", lastStoreBudget);
        tag.putInt("BankOut", lastBankDrawn);
        tag.putInt("BankIn", lastBankCharged);

        tag.putByteArray("FcTrace", outlook.trace());
        tag.putIntArray("FcMarks", outlook.packMilestones());
        tag.putBoolean("FcNow", outlook.shortNow());

        tag.putIntArray("FcSupply", outlook.supply());
        tag.putInt("BandHealthy", effectiveBand().healthyPct());
        tag.putInt("RideT", rideTicks());
        tag.putInt("ClaimPct", chargeClaimPct());
        tag.putBoolean("UpHold", upstreamHoldsCharging());
        tag.putInt("Reserve", reserveEnergy);
        tag.putInt("ReserveCap", reserveCapacity);
        tag.putInt("Gen", generation);
        tag.putInt("Delivered", totalDelivered);
        tag.putInt("Rate", reserveRate);
        tag.putInt("Claim", chargeClaim);

        tag.putInt("Stance", reserveState.stance().ordinal());
        tag.putInt("Sustain", reserveState.sustainedDraw());
        tag.putInt("CritDraw", reserveState.criticalDraw());
        tag.putInt("BandFloor", reserveState.floorPct());
        tag.putInt("BandCeil", reserveState.ceilingPct());

        tag.putInt("HoldFe", holdTargetFe());
        tag.putInt("HoldPct", holdTargetPct());

        tag.putInt("CfgFloor", sizedBand().floorPct());
        tag.putInt("BandWant", bandWant.want());
        tag.putLong("BandWantAt", bandWant.since());

        if (bandSettings != null) {
            tag.put("BandCfg", writeBand(bandSettings));
        }
        tag.put("BandShown", writeBand(bandSettings()));
        tag.putInt("FullRate", reserveDischargeRate());

        tag.putInt("UpTier", upstreamTier.ordinal());
        tag.putBoolean("UpTierSet", upstreamTierChosen);
        tag.putInt("UpLock", upstreamLock.ordinal());
        tag.putInt("PushIn", pushedInputTotal());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));

        for (List<PortLink> links : portLinks) {
            links.clear();
        }
        final ListTag ports = tag.getList("Ports", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int port = 0; port < Math.min(ports.size(), BatteryControllerPorts.COUNT); port++) {
            final CompoundTag portTag = ports.getCompound(port);
            final long[] targets = portTag.getLongArray("To");
            final byte[] tiers = portTag.getByteArray("Tiers");
            for (int i = 0; i < targets.length; i++) {
                portLinks.get(port).add(new PortLink(BlockPos.of(targets[i]),
                        i < tiers.length ? WireTier.byOrdinal(tiers[i]) : WireTier.COPPER));
            }
        }

        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (!tag.contains("Out" + i)) {
                continue;
            }
            final CompoundTag out = tag.getCompound("Out" + i);
            outputEnabled[i] = !out.contains("On") || out.getBoolean("On");
            outputMode[i] = BatteryOutputMode.byOrdinal(out.getInt("Mode"));
            outputLimit[i] = Math.max(0, out.getInt("Limit"));
            outputFloorPct[i] = Math.max(0, Math.min(95, out.getInt("Floor")));
            outputPriority[i] = priorityByOrdinal(out.getInt("Prio"));
            outputHeld[i] = out.getBoolean("Held");
            outputFlow[i] = out.getInt("Flow");
            outputDemand[i] = out.getInt("Want");
            outputCeiling[i] = out.getInt("Ceil");
            outputDevices[i] = out.getInt("Devs");
            outputReason[i] = reasonByOrdinal(out.getInt("Why"));
            outputPanels[i] = out.getInt("Panels");
            outputCriticalDemand[i] = out.getInt("Crit");
            outputBoardAsked[i] = Math.max(0, out.getInt("BAsk"));
            outputBoardGranted[i] = Math.max(0, out.getInt("BGot"));
            final com.createstats.power.FeBoardDeal.Reason[] reasons =
                    com.createstats.power.FeBoardDeal.Reason.values();
            outputBoardReason[i] = reasons[Math.floorMod(out.getInt("BWhy"), reasons.length)];
            portStoreCount[BatteryControllerPorts.outputPort(i)] = Math.max(0, out.getInt("Stores"));
            outputStoreFlow[i] = Math.max(0, out.getInt("SFlow"));
        }
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            if (!tag.contains("In" + i)) {
                continue;
            }
            final CompoundTag in = tag.getCompound("In" + i);
            inputEnabled[i] = !in.contains("On") || in.getBoolean("On");
            inputFlow[i] = in.getInt("Flow");
            inputTanks[i] = in.getInt("Tanks");
            inputSources[i] = in.getInt("Srcs");
            inputPanelWired[i] = in.getBoolean("PanelX");
            portStoreCount[i] = Math.max(0, in.getInt("Stores"));
            inputStoreFlow[i] = Math.max(0, in.getInt("SFlow"));
            reserveMoveByPort[i] = Math.max(0, in.getInt("BMove"));
        }
        storeCount = Math.max(0, tag.getInt("StoreN"));
        storeStored = Math.max(0, tag.getInt("StoreE"));
        storeCapacity = Math.max(0, tag.getInt("StoreC"));
        storeCharged = Math.max(0, tag.getInt("StoreIn"));
        storeAsk = Math.max(0, tag.getInt("StoreAsk"));
        lastStoreBudget = Math.max(0, tag.getInt("StoreBud"));
        lastBankDrawn = Math.max(0, tag.getInt("BankOut"));
        lastBankCharged = Math.max(0, tag.getInt("BankIn"));
        outlook = com.createstats.power.ReserveProjection.Result.unpack(tag.getByteArray("FcTrace"),
                tag.getIntArray("FcMarks"), tag.getBoolean("FcNow"), tag.getIntArray("FcSupply"));
        syncedHealthyPct = tag.contains("BandHealthy") ? tag.getInt("BandHealthy")
                : com.createstats.power.FeReserveBand.DEFAULT_HEALTHY_PCT;
        syncedRideTicks = tag.contains("RideT") ? Math.max(1, tag.getInt("RideT"))
                : com.createstats.power.FeReserveBand.DEFAULT_RIDE_TICKS;
        syncedClaimPct = tag.contains("ClaimPct") ? tag.getInt("ClaimPct")
                : com.createstats.power.FeChargePolicy.DEFAULT_MAX_CLAIM_PCT;
        syncedUpstreamHold = tag.getBoolean("UpHold");
        reserveEnergy = Math.max(0, tag.getInt("Reserve"));
        reserveCapacity = Math.max(0, tag.getInt("ReserveCap"));
        generation = Math.max(0, tag.getInt("Gen"));
        totalDelivered = Math.max(0, tag.getInt("Delivered"));
        reserveRate = tag.getInt("Rate");
        reserveRateFiltered = reserveRate;
        chargeClaim = Math.max(0, tag.getInt("Claim"));
        final com.createstats.power.FeReserveReport.Stance[] stances =
                com.createstats.power.FeReserveReport.Stance.values();
        final int ordinal = tag.getInt("Stance");
        reserveState = new com.createstats.power.FeReserveReport(worldPosition.asLong(),
                reserveEnergy, reserveCapacity, generation, reserveRate,
                tag.getInt("Sustain"), tag.getInt("CritDraw"), chargeClaim, portRate(),
                tag.contains("BandFloor") ? tag.getInt("BandFloor")
                        : com.createstats.power.FeReserveBand.DEFAULT_FLOOR_PCT,
                tag.contains("BandCeil") ? tag.getInt("BandCeil")
                        : com.createstats.power.FeReserveBand.DEFAULT_CEILING_PCT,
                ordinal >= 0 && ordinal < stances.length ? stances[ordinal]
                        : com.createstats.power.FeReserveReport.Stance.CONSERVE);
        syncedHoldFe = Math.max(0, tag.getInt("HoldFe"));
        syncedHoldPct = Math.max(0, tag.getInt("HoldPct"));
        syncedConfiguredFloorPct = tag.contains("CfgFloor") ? Math.max(0, tag.getInt("CfgFloor"))
                : com.createstats.power.FeReserveBand.DEFAULT_FLOOR_PCT;
        syncedBandWant = Math.max(0, tag.getInt("BandWant"));
        bandWant.restore(syncedBandWant, tag.getLong("BandWantAt"));
        bandSettings = tag.contains("BandCfg") ? readBand(tag.getCompound("BandCfg")) : null;
        syncedBandCustom = bandSettings != null;
        syncedBandSettings = tag.contains("BandShown") ? readBand(tag.getCompound("BandShown")) : null;
        syncedFullRate = Math.max(0, tag.getInt("FullRate"));
        upstreamTier = priorityByOrdinal(tag.getInt("UpTier"));
        upstreamTierChosen = tag.getBoolean("UpTierSet");
        final com.createstats.power.FeLock[] locks = com.createstats.power.FeLock.values();
        final int lock = tag.getInt("UpLock");
        upstreamLock = lock >= 0 && lock < locks.length ? locks[lock]
                : com.createstats.power.FeLock.NONE;

        pushedInput[0] = Math.max(0, tag.getInt("PushIn"));
    }

    private int pushedInputTotal() {
        int total = 0;
        for (int value : pushedInput) {
            total += value;
        }
        return total;
    }

    private static FePriority priorityByOrdinal(int ordinal) {
        final FePriority[] all = FePriority.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FePriority.NORMAL;
    }

    private static BatteryControllerPlan.Reason reasonByOrdinal(int ordinal) {
        final BatteryControllerPlan.Reason[] all = BatteryControllerPlan.Reason.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal]
                : BatteryControllerPlan.Reason.IDLE;
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

    private final com.createstats.gui.battery.BatteryHistory history1m =
            new com.createstats.gui.battery.BatteryHistory();
    private final com.createstats.gui.battery.BatteryHistory history5m =
            new com.createstats.gui.battery.BatteryHistory();
    private final com.createstats.gui.battery.BatteryHistory history15m =
            new com.createstats.gui.battery.BatteryHistory();

    private static final long HISTORY_TIMEOUT = 200L;

    private static final double HISTORY_RANGE_SQ = 128.0 * 128.0;

    private final Map<UUID, HistorySub> historySubs = new HashMap<>();

    private static final class HistorySub {
        int range;
        long lastSeen;
    }

    private void recordHistory() {
        if (level == null) {
            return;
        }
        final long t = level.getGameTime();
        final boolean a1 = t % com.createstats.gui.battery.BatteryHistory.CADENCE[0] == 0;
        final boolean a5 = t % com.createstats.gui.battery.BatteryHistory.CADENCE[1] == 0;
        final boolean a15 = t % com.createstats.gui.battery.BatteryHistory.CADENCE[2] == 0;
        if (!a1 && !a5 && !a15) {
            return;
        }

        final int soc = reservePct();
        if (a1) {
            history1m.record(soc, generation, totalDelivered);
        }
        if (a5) {
            history5m.record(soc, generation, totalDelivered);
        }
        if (a15) {
            history15m.record(soc, generation, totalDelivered);
        }
        if (!historySubs.isEmpty()) {
            broadcastHistory(a1, a5, a15);
        }
    }

    private void broadcastHistory(boolean a1, boolean a5, boolean a15) {
        final net.minecraft.server.MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) {
            return;
        }
        final long now = level.getGameTime();
        final Iterator<Map.Entry<UUID, HistorySub>> it = historySubs.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, HistorySub> entry = it.next();
            final net.minecraft.server.level.ServerPlayer player =
                    server.getPlayerList().getPlayer(entry.getKey());
            final HistorySub sub = entry.getValue();
            if (player == null || player.level() != level || now - sub.lastSeen > HISTORY_TIMEOUT
                    || player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5) > HISTORY_RANGE_SQ) {
                it.remove();
                continue;
            }
            final boolean advanced = sub.range == 0 ? a1 : sub.range == 1 ? a5 : a15;
            if (!advanced) {
                continue;
            }
            final com.createstats.gui.battery.BatteryHistory ring = historyFor(sub.range);
            final int last = ring.count() - 1;
            if (last < 0) {
                continue;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    com.createstats.network.BatteryControllerHistoryPacket.delta(worldPosition,
                            sub.range, ring.socAt(last), ring.inAt(last), ring.outAt(last)));
        }
    }

    private com.createstats.gui.battery.BatteryHistory historyFor(int range) {
        return range == com.createstats.gui.battery.BatteryHistory.RANGE_1M ? history1m
                : range == com.createstats.gui.battery.BatteryHistory.RANGE_5M ? history5m
                : history15m;
    }

    public void subscribeHistory(net.minecraft.server.level.ServerPlayer player, int range) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int r = com.createstats.gui.battery.BatteryHistory.clampRange(range);
        final HistorySub sub = historySubs.computeIfAbsent(player.getUUID(), k -> new HistorySub());
        sub.range = r;
        sub.lastSeen = level.getGameTime();
        final com.createstats.gui.battery.BatteryHistory ring = historyFor(r);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                com.createstats.network.BatteryControllerHistoryPacket.full(worldPosition, r,
                        ring.socChronological(), ring.inChronological(), ring.outChronological()));
    }

    public void keepAliveHistory(UUID id, int range) {
        final HistorySub sub = historySubs.get(id);
        if (sub != null && level != null) {
            sub.range = com.createstats.gui.battery.BatteryHistory.clampRange(range);
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

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Battery Controller"));

        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            if (inputHasStrayPanel(i)) {
                tooltip.add(com.createstats.power.FeGoggle.warn(
                        BatteryControllerPorts.describe(i)
                                + " is wired to a panel leg, which carries nothing"));
                tooltip.add(com.createstats.power.FeGoggle.dim(
                        "Run cable from the panel's back boss and feed this input off that"));
                break;
            }
        }
        if (reserveCapacity <= 0) {
            tooltip.add(com.createstats.power.FeGoggle.warn("No storage wired to any input"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.stored(reserveEnergy, reserveCapacity));
            tooltip.add(com.createstats.power.FeGoggle.chargeFlow(reserveRate));

            addOutlookLines(tooltip, isPlayerSneaking);
        }

        if (isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "In %,d FE/t, out %,d FE/t", generation, totalDelivered)));
        }
        if (reserveCapacity > 0) {
            final com.createstats.power.FeReserveReport.Stance stance = reserveState.stance();
            tooltip.add(Component.literal("    ").append(Component.literal(
                    stance.label() + ": " + stance.blurb()).withStyle(switch (stance) {
                        case SURPLUS -> ChatFormatting.GREEN;
                        case NOMINAL -> ChatFormatting.AQUA;
                        case CONSERVE -> ChatFormatting.GOLD;
                        case CRITICAL -> ChatFormatting.RED;
                    })));
        }
        if (reserveCapacity > 0 && holdTargetPct() > configuredFloorPct()) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Holding %d%% back for a board upstream (the floor is %d%% without it)",
                    holdTargetPct(), configuredFloorPct())).withStyle(ChatFormatting.AQUA)));
            if (isPlayerSneaking) {
                tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                        "That board expects to be %,d FE short before its supply is back",
                        holdTargetFe())));
            }
        }
        if (chargeClaim > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Holding %,d FE/t back to refill the banks", chargeClaim))
                    .withStyle(ChatFormatting.AQUA)));
        }
        if (storeCount > 0) {
            final int pct = storeCapacity <= 0 ? 0 : (int) Math.min(100, (long) storeStored * 100L / storeCapacity);
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Starter Capacitor%s %d%%%s", storeCount == 1 ? "" : "s x" + storeCount, pct,
                    storeCharged > 0 ? ", refilling " + com.createstats.power.ReserveWords.fe(storeCharged) + " FE/t"
                            : pct >= 100 ? ", full" : upstreamHoldShown() ? ", held: a board above is short"
                            : ", waiting for spare")).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }

        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (outputPanelCount(i) <= 0 || reserveCapacity <= 0) {
                continue;
            }
            final int asked = outputBoardAsked(i);
            final int granted = outputBoardGranted(i);
            final boolean shortOf = granted < asked;
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "%s board%s: asked %,d, granted %,d FE/t",
                    BatteryControllerPorts.describe(BatteryControllerPorts.outputPort(i)),
                    outputPanelCount(i) == 1 ? "" : "s", asked, granted))
                    .withStyle(shortOf ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
            if (shortOf) {
                tooltip.add(com.createstats.power.FeGoggle.dim(outputBoardReason(i).sentence()));
            }
        }

        int served = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final BatteryControllerPlan.Reason reason = outputReason(i);
            if (reason == BatteryControllerPlan.Reason.UNWIRED) {
                continue;
            }
            if (reason == BatteryControllerPlan.Reason.SERVED && !isPlayerSneaking) {
                served++;
                continue;
            }
            final ChatFormatting colour = switch (reason) {
                case SERVED -> ChatFormatting.GREEN;
                case PARTIAL, FLOOR, SHED, NO_SURPLUS -> ChatFormatting.GOLD;
                case OFF -> ChatFormatting.RED;
                default -> ChatFormatting.DARK_GRAY;
            };
            tooltip.add(Component.literal("    ").append(Component.literal(String.format(Locale.ROOT,
                    "Output %d: %s, %,d / %,d FE/t", i + 1, reason.label(),
                    outputFlow[i], outputDemand[i])).withStyle(colour)));
        }
        if (served > 0) {
            tooltip.add(com.createstats.power.FeGoggle.ok(String.format(Locale.ROOT,
                    "%d output%s fully served", served, served == 1 ? "" : "s")));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    private void addOutlookLines(List<Component> tooltip, boolean detailed) {
        final com.createstats.power.ReserveProjection.Result r = outlook;
        if (r.trace().length == 0) {
            return;
        }
        final com.createstats.power.ReserveProjection.Milestone first =
                r.first(com.createstats.power.ReserveProjection.Kind.SHORT);
        final com.createstats.power.ReserveProjection.Milestone full =
                r.first(com.createstats.power.ReserveProjection.Kind.FULL);
        final com.createstats.power.ReserveProjection.Milestone rest =
                r.first(com.createstats.power.ReserveProjection.Kind.SETTLES);
        final String line;
        final ChatFormatting colour;
        if (r.shortNow()) {
            line = "An output is short now";
            colour = ChatFormatting.GOLD;
        } else if (first != null) {
            line = "All served for about " + com.createstats.power.ReserveWords.span(first.tick()) + ", then "
                    + com.createstats.power.ReserveWords.outputName(first.output()) + " goes short";
            colour = first.tick() < 1_200 ? ChatFormatting.RED : first.tick() < 12_000 ? ChatFormatting.YELLOW
                    : ChatFormatting.GREEN;
        } else if (full != null) {
            line = "All served; banks full in about " + com.createstats.power.ReserveWords.span(full.tick());
            colour = ChatFormatting.GREEN;
        } else {
            line = reserveRate < 0 ? "All served for the next " + com.createstats.power.ReserveWords.horizon()
                    : "All served, banks holding";
            colour = ChatFormatting.GREEN;
        }
        tooltip.add(Component.literal("    ").append(Component.literal(line).withStyle(colour)));

        final String supply = com.createstats.power.ReserveWords.nextSupplyChange(r);
        if (supply != null) {
            tooltip.add(com.createstats.power.FeGoggle.dim(supply));
        }
        if (rest != null) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Banks settle near " + rest.value() + "%"));
        }
        if (detailed) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Solar follows the sun, burners their fuel"));
        }
    }

    public static int portRate() {
        try {
            return ModConfiguration.COMMON.batteryControllerPortRate.get();
        } catch (RuntimeException ignored) {
            return 65536;
        }
    }

    public static final int INPUT_LINES = 2;

    public static int inputRate() {
        return (int) Math.min(Integer.MAX_VALUE, (long) INPUT_LINES * portRate());
    }

    public static int busCapacity() {
        int configured;
        try {
            configured = ModConfiguration.COMMON.batteryControllerBuffer.get();
        } catch (RuntimeException ignored) {
            configured = 64000;
        }
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max((long) configured, (long) BatteryControllerPorts.INPUTS * inputRate()));
    }

    public IEnergyStorage terminalInputHandle(int port) {
        return port >= 0 && port < terminalInputs.length ? terminalInputs[port] : NULL_TERMINAL_INPUT;
    }

    private static final IEnergyStorage NULL_TERMINAL_INPUT = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final IEnergyStorage[] terminalInputs = newTerminalInputs();

    private IEnergyStorage[] newTerminalInputs() {
        final IEnergyStorage[] handles = new IEnergyStorage[BatteryControllerPorts.INPUTS];
        for (int i = 0; i < handles.length; i++) {
            final int port = i;
            handles[i] = new IEnergyStorage() {
                private void refill(long now) {
                    if (now != pushTick[port]) {
                        pushTick[port] = now;
                        pushSpent[port] = 0;
                    }
                }

                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    if (level == null || !inputEnabled[port] || isManagedShed()
                            || upstreamLock == com.createstats.power.FeLock.FORCE_OFF) {
                        return 0;
                    }
                    final long now = level.getGameTime();
                    refill(now);

                    final int rating = inputCeiling[port] > 0 ? inputCeiling[port] : inputRate();
                    final int pulled = pulledTick == now ? pulledNow[port] : 0;
                    final int room = Math.min(Math.min(maxReceive,
                                    Math.max(0, rating - pushSpent[port] - pulled)),
                            Math.max(0, busCapacity() - energy));
                    if (room <= 0) {
                        return 0;
                    }
                    if (!simulate) {
                        energy += room;
                        pushSpent[port] += room;
                        pushedInputAccumulator[port] += room;
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
                    return busCapacity();
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
        return handles;
    }

    private final Map<Long, com.createstats.power.FeExportReport> exportReports = new HashMap<>();
    private final Map<Long, Long> exportReportUntil = new HashMap<>();

    @Override
    public void acceptExportReport(com.createstats.power.FeExportReport report) {
        if (report == null || !report.isPresent() || level == null || level.isClientSide()) {
            return;
        }
        exportReports.put(report.panelPos(), report);
        exportReportUntil.put(report.panelPos(), level.getGameTime() + SHED_GRACE_TICKS);
    }

    public boolean upstreamHoldsCharging() {
        return firstHoldingReport() != null;
    }

    @Nullable
    public com.createstats.power.FeExportReport firstHoldingReport() {
        if (level == null || exportReports.isEmpty()) {
            return null;
        }
        final long now = level.getGameTime();
        exportReportUntil.entrySet().removeIf(entry -> now > entry.getValue());
        exportReports.keySet().retainAll(exportReportUntil.keySet());
        com.createstats.power.FeExportReport worst = null;
        for (com.createstats.power.FeExportReport report : exportReports.values()) {
            if (!report.holdCharging()) {
                continue;
            }
            if (worst == null || report.stance().ordinal() > worst.stance().ordinal()) {
                worst = report;
            }
        }
        return worst;
    }

    private final Map<Long, Long> pushingPanelsUntil = new HashMap<>();

    @Override
    public void notePushFrom(BlockPos feeder, int fe) {
        if (feeder == null || level == null || level.isClientSide()) {
            return;
        }

        pushingPanelsUntil.put(feeder.asLong(), level.getGameTime() + SHED_GRACE_TICKS);
    }

    @Override
    public com.createstats.power.FeReserveReport boardReserveReport() {
        return reserveState;
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

    public boolean isHoldTargetActive() {
        return level != null && holdBook.largest(level.getGameTime()).isPresent();
    }

    public int holdTargetFe() {
        if (level != null && level.isClientSide()) {
            return syncedHoldFe;
        }
        return level == null ? 0 : holdBook.largest(level.getGameTime()).fe();
    }

    public int configuredFloorPct() {
        return level != null && level.isClientSide() ? syncedConfiguredFloorPct : sizedBand().floorPct();
    }

    public int bandCriticalWant() {
        return level != null && level.isClientSide() ? syncedBandWant : bandWant.want();
    }

    public int holdTargetPct() {
        if (level != null && level.isClientSide()) {
            return syncedHoldPct;
        }
        final int fe = holdTargetFe();
        return fe <= 0 ? 0 : com.createstats.power.FeSupplyOutlook.holdPctFor(
                new com.createstats.power.FeSupplyOutlook.Result(0, fe, fe, 0, false),
                reserveCapacity());
    }

    private com.createstats.power.FeReserveBand.Band effectiveBand() {
        final com.createstats.power.FeReserveBand.Band sized = sizedBand();
        final int hold = holdTargetPct();
        if (hold <= sized.floorPct()) {
            return sized;
        }
        return new com.createstats.power.FeReserveBand.Band(hold, sized.ceilingPct(),
                sized.healthyPct());
    }

    private com.createstats.power.FeReserveBand.Band sizedBand() {
        return bandSettings().band(reserveCapacity, bandWant.want(), reserveDischargeRate(), rideTicks());
    }

    public com.createstats.power.FeBandSettings bandSettings() {
        if (level != null && level.isClientSide()) {
            return syncedBandSettings != null ? syncedBandSettings
                    : com.createstats.power.FeBandSettings.server(band(), bandMinutes());
        }
        return bandSettings != null ? bandSettings : com.createstats.power.FeBandSettings.server(band(), bandMinutes());
    }

    public boolean isBandCustom() {
        return level != null && level.isClientSide() ? syncedBandCustom : bandSettings != null;
    }

    public int bandFullRate() {
        return level != null && level.isClientSide() && syncedFullRate > 0 ? syncedFullRate : reserveDischargeRate();
    }

    public void setBandUnit(com.createstats.power.FeBandSettings.Unit unit) {
        final com.createstats.power.FeBandSettings now = bandSettings();
        applyBand(now.withUnit(unit, reserveCapacity, bandWant.want(), reserveDischargeRate(), rideTicks(),
                bandMinutes()));
    }

    public void setBandEdge(int edge, int value) {
        if (edge >= 0 && edge <= 2) {
            applyBand(bandSettings().withEdge(edge, value));
        }
    }

    public void setBandCap(int edge, int pct) {
        if (edge >= 0 && edge <= 2) {
            applyBand(bandSettings().withCap(edge, pct));
        }
    }

    public void resetBand() {
        if (bandSettings != null) {
            bandSettings = null;
            setChanged();
            syncNow();
        }
    }

    private void applyBand(com.createstats.power.FeBandSettings next) {
        if (!next.equals(bandSettings())) {
            bandSettings = next;
            setChanged();
            syncNow();
        }
    }

    private static CompoundTag writeBand(com.createstats.power.FeBandSettings b) {
        final CompoundTag t = new CompoundTag();
        t.putString("Unit", b.unit().name());
        t.putIntArray("Edges", new int[]{b.floor(), b.top(), b.healthy()});
        t.putIntArray("Caps", new int[]{b.capFloor(), b.capTop(), b.capHealthy()});
        return t;
    }

    @Nullable
    private static com.createstats.power.FeBandSettings readBand(CompoundTag t) {
        final int[] e = t.getIntArray("Edges");
        final int[] c = t.getIntArray("Caps");
        if (e.length != 3 || c.length != 3) {
            return null;
        }
        com.createstats.power.FeBandSettings.Unit unit = com.createstats.power.FeBandSettings.Unit.MINUTES;
        try {
            unit = com.createstats.power.FeBandSettings.Unit.valueOf(t.getString("Unit"));
        } catch (IllegalArgumentException ignored) {
        }
        return new com.createstats.power.FeBandSettings(unit, e[0], e[1], e[2], c[0], c[1], c[2]);
    }

    private void answerPushingPanels() {
        if (level == null || pushingPanelsUntil.isEmpty()) {
            return;
        }
        final long now = level.getGameTime();
        pushingPanelsUntil.entrySet().removeIf(entry -> now > entry.getValue());
        for (Long packed : pushingPanelsUntil.keySet()) {
            final BlockPos at = BlockPos.of(packed);
            if (level.isLoaded(at)
                    && level.getBlockEntity(at) instanceof PowerDistributionUnitBE panel) {
                panel.noteDownstreamReserve(reserveState);
            }
        }
    }

    // never price on bus headroom, a shed device's headroom only grows and it never comes back
    public int declaredDemandFePerTick() {
        int want = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (!outputEnabled[i]) {
                continue;
            }
            want = com.createstats.power.FeRates.plus(want,
                    Math.min(Math.max(0, outputDemand[i]), Math.max(0, outputCeiling[i])));
        }

        want = com.createstats.power.FeRates.plus(want, Math.max(0, chargeClaim));

        return Math.min(inputRate(), want);
    }

    @Override
    public int ratedDrawFePerTick() {
        return upstreamLock == com.createstats.power.FeLock.FORCE_OFF ? 0 : declaredDemandFePerTick();
    }

    @Override
    public int measuredDrawFePerTick() {
        return Math.max(0, pushedInputTotal());
    }

    @Override
    public int declaredCriticalDemandFePerTick() {
        int want = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (!outputEnabled[i] || portLinks.get(BatteryControllerPorts.outputPort(i)).isEmpty()) {
                continue;
            }
            final int ceiling = Math.max(0, outputCeiling[i]);
            final int here = outputPriority[i] == FePriority.CRITICAL
                    ? Math.min(Math.max(0, outputDemand[i]), ceiling)
                    : Math.min(Math.max(0, outputCriticalDemand[i]), ceiling);
            want = com.createstats.power.FeRates.plus(want, here);
        }
        return Math.min(inputRate(), want);
    }

    @Override
    public FePriority managedPriority() {
        if (upstreamTierChosen) {
            return upstreamTier;
        }
        FePriority top = upstreamTier;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            if (!outputEnabled[i] || outputPriority[i] == FePriority.CRITICAL
                    || portLinks.get(BatteryControllerPorts.outputPort(i)).isEmpty()) {
                continue;
            }
            if (outputPriority[i].ordinal() < top.ordinal()) {
                top = outputPriority[i];
            }
        }
        return top;
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        if (tier == null || upstreamTier == tier && upstreamTierChosen) {
            return;
        }
        upstreamTier = tier;
        upstreamTierChosen = true;
        setChanged();
    }

    @Override
    public com.createstats.power.FeLock managedLock() {
        return upstreamLock;
    }

    @Override
    public void setManagedLock(com.createstats.power.FeLock lock) {
        if (lock == null || upstreamLock == lock) {
            return;
        }
        upstreamLock = lock;
        setChanged();
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
        return level != null && level.getGameTime() <= managedShedUntil;
    }

    public static int reserveDischargeRate() {
        return portRate() * BatteryControllerPorts.OUTPUTS;
    }

    public static int reserveChargeRate() {
        return (int) Math.min(Integer.MAX_VALUE, (long) inputRate() * BatteryControllerPorts.INPUTS);
    }

    public static int rideTicks() {
        try {
            return ModConfiguration.COMMON.batteryControllerRideTicks.get();
        } catch (RuntimeException ignored) {
            return com.createstats.power.FeReserveBand.DEFAULT_RIDE_TICKS;
        }
    }

    public static int chargeClaimPct() {
        try {
            return ModConfiguration.COMMON.batteryControllerChargeClaimPct.get();
        } catch (RuntimeException ignored) {
            return com.createstats.power.FeChargePolicy.DEFAULT_MAX_CLAIM_PCT;
        }
    }

    public static com.createstats.power.FeReserveBand.Band band() {
        try {
            return new com.createstats.power.FeReserveBand.Band(
                    ModConfiguration.COMMON.batteryControllerBandFloorPct.get(),
                    ModConfiguration.COMMON.batteryControllerBandCeilingPct.get(),
                    ModConfiguration.COMMON.batteryControllerBandHealthyPct.get());
        } catch (RuntimeException ignored) {
            return com.createstats.power.FeReserveBand.Band.DEFAULT;
        }
    }

    public static com.createstats.power.FeReserveBand.Minutes bandMinutes() {
        try {
            return new com.createstats.power.FeReserveBand.Minutes(
                    ModConfiguration.COMMON.batteryControllerBandFloorMinutes.get(),
                    ModConfiguration.COMMON.batteryControllerBandCeilingMinutes.get(),
                    ModConfiguration.COMMON.batteryControllerBandHealthyMinutes.get());
        } catch (RuntimeException ignored) {
            return com.createstats.power.FeReserveBand.Minutes.DEFAULT;
        }
    }

    public com.createstats.power.FeReserveReport reserveState() {
        return reserveState;
    }

    public int chargeClaimFePerTick() {
        return chargeClaim;
    }

    public int outputPanelCount(int index) {
        return inRange(index) ? outputPanels[index] : 0;
    }

    public int outputCriticalDemand(int index) {
        return inRange(index) ? outputCriticalDemand[index] : 0;
    }

    public int outputBoardAsked(int index) {
        return inRange(index) ? outputBoardAsked[index] : 0;
    }

    public int outputBoardGranted(int index) {
        return inRange(index) ? outputBoardGranted[index] : 0;
    }

    public com.createstats.power.FeBoardDeal.Reason outputBoardReason(int index) {
        final com.createstats.power.FeBoardDeal.Reason r = inRange(index) ? outputBoardReason[index] : null;
        return r == null ? com.createstats.power.FeBoardDeal.Reason.GRANTED : r;
    }

    private void answerBoards(List<List<PowerDistributionUnitBE>> panelsByOutput,
                              List<List<FeGridNode>> loadsByOutput, Set<Long> panelNodes,
                              List<BatteryControllerPlan.Output> plan, BatteryControllerPlan.Plan decided,
                              boolean upstreamHold, long now) {
        boardCouldGive.clear();
        boardDelivered.clear();
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            outputBoardAsked[i] = 0;
            outputBoardGranted[i] = 0;
            outputBoardReason[i] = com.createstats.power.FeBoardDeal.Reason.GRANTED;

            for (FeGridNode node : loadsByOutput.get(i)) {
                final PowerDistributionUnitBE panel = boardOfNode.get(node.nodeKey());
                if (panel != null) {
                    boardDelivered.merge(panel.getBlockPos().asLong(),
                            Math.max(0, portFlowByNode.getOrDefault(node.nodeKey(), 0)),
                            com.createstats.power.FeRates::plus);
                }
            }
        }
        if (reserveCapacity <= 0) {
            return;
        }

        final List<PowerDistributionUnitBE> boards = new ArrayList<>(2);
        final Map<PowerDistributionUnitBE, Integer> indexOf = new java.util.IdentityHashMap<>();
        final List<com.createstats.power.FeBoardDeal.Request> requests = new ArrayList<>(2);
        for (List<PowerDistributionUnitBE> here : panelsByOutput) {
            for (PowerDistributionUnitBE panel : here) {
                if (!indexOf.containsKey(panel)) {
                    indexOf.put(panel, boards.size());
                    boards.add(panel);
                    requests.add(panel.boardRequest(worldPosition.asLong()));
                }
            }
        }
        if (boards.isEmpty()) {
            return;
        }
        final int n = boards.size();

        final int generationFree = Math.max(0, generation - chargeClaim);
        final int[] caps = new int[n];
        final int[][] capByOutput = new int[BatteryControllerPorts.OUTPUTS][n];
        final boolean[][] onOutput = new boolean[BatteryControllerPorts.OUTPUTS][n];
        int otherSpend = 0;
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            int nonBoard = 0;
            for (FeGridNode node : loadsByOutput.get(i)) {
                if (!panelNodes.contains(node.nodeKey())) {
                    nonBoard = com.createstats.power.FeRates.plus(nonBoard,
                            Math.max(0, costByNode.getOrDefault(node.nodeKey(), 0)));
                }
            }
            final BatteryControllerPlan.Grant grant = decided.grants().get(i);
            otherSpend = com.createstats.power.FeRates.plus(otherSpend,
                    Math.min(nonBoard, Math.max(0, grant.granted())));
            final List<PowerDistributionUnitBE> here = panelsByOutput.get(i);
            if (here.isEmpty()) {
                continue;
            }
            final int outputCap = BatteryControllerPlan.boardCap(plan.get(i), grant, generationFree, nonBoard);
            final int[] asks = new int[here.size()];
            for (int k = 0; k < asks.length; k++) {
                asks[k] = requests.get(indexOf.get(here.get(k))).total();
            }
            final int[] shares = com.createstats.power.FeBoardDeal.shareWire(asks, outputCap);
            for (int k = 0; k < asks.length; k++) {
                final int j = indexOf.get(here.get(k));
                onOutput[i][j] = true;
                capByOutput[i][j] = shares[k];
                caps[j] = com.createstats.power.FeRates.plus(caps[j], shares[k]);
            }
        }
        final boolean[] held = new boolean[n];
        for (int j = 0; j < n; j++) {
            held[j] = caps[j] <= 0;
        }

        final int[] tiers = new int[n];
        java.util.Arrays.fill(tiers, Integer.MAX_VALUE);
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            for (int j = 0; j < n; j++) {
                if (onOutput[i][j]) {
                    tiers[j] = Math.min(tiers[j], plan.get(i).priority().ordinal());
                }
            }
        }
        final List<com.createstats.power.FeBoardDeal.Answer> answers = com.createstats.power.FeBoardDeal.split(
                worldPosition.asLong(), false,
                com.createstats.power.FeBoardDeal.Offer.of(reserveState, otherSpend, upstreamHold),
                requests, caps, held, tiers, now + BOARD_ANSWER_GRACE_TICKS);
        final int[] worstShort = new int[BatteryControllerPorts.OUTPUTS];
        for (int j = 0; j < n; j++) {
            final com.createstats.power.FeBoardDeal.Answer a = answers.get(j);
            boards.get(j).acceptBoardAnswer(a);
            boardCouldGive.put(boards.get(j).getBlockPos().asLong(),
                    Math.min(caps[j], com.createstats.power.FeRates.plus(a.total(), a.spare())));

            final int[] weight = new int[BatteryControllerPorts.OUTPUTS];
            boolean anyCap = false;
            for (int i = 0; i < weight.length; i++) {
                weight[i] = onOutput[i][j] ? capByOutput[i][j] : 0;
                anyCap |= weight[i] > 0;
            }
            if (!anyCap) {
                for (int i = 0; i < weight.length; i++) {
                    weight[i] = onOutput[i][j] ? 1 : 0;
                }
            }
            final int[] askedHere = com.createstats.power.FeBoardDeal.shareWire(weight, a.asked().total());
            final int[] grantedHere = com.createstats.power.FeBoardDeal.shareWire(weight, a.total());
            for (int i = 0; i < weight.length; i++) {
                if (!onOutput[i][j]) {
                    continue;
                }
                outputBoardAsked[i] = com.createstats.power.FeRates.plus(outputBoardAsked[i], askedHere[i]);
                outputBoardGranted[i] = com.createstats.power.FeRates.plus(outputBoardGranted[i], grantedHere[i]);
                if (a.shortfall() > worstShort[i]) {
                    worstShort[i] = a.shortfall();
                    outputBoardReason[i] = a.reason();
                }
            }
        }
    }

    @Override
    public int potentialFePerTickFor(long consumerPos) {
        final Integer could = boardCouldGive.get(consumerPos);
        if (could != null) {
            return Math.max(0, could);
        }
        final Integer handed = boardDelivered.get(consumerPos);
        return handed != null ? Math.max(0, handed) : potentialFePerTick();
    }

    private static final int BOARD_ANSWER_GRACE_TICKS = 10;

    public boolean hasManagedOutput() {
        for (int panels : outputPanels) {
            if (panels > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean syncedUpstreamHold;

    public boolean upstreamHoldShown() {
        return level != null && level.isClientSide() ? syncedUpstreamHold : upstreamHoldsCharging();
    }

    public com.createstats.power.ReserveProjection.Result outlook() {
        return outlook;
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

    public int storeAsk() {
        return storeAsk;
    }

    public int storeBudget() {
        return lastStoreBudget;
    }

    public int portStoreCount(int port) {
        return port >= 0 && port < portStoreCount.length ? portStoreCount[port] : 0;
    }

    public int outputStoreFlow(int index) {
        return inRange(index) ? outputStoreFlow[index] : 0;
    }

    public int inputStoreFlow(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS ? inputStoreFlow[index] : 0;
    }

    public int inputBankMove(int index) {
        return index >= 0 && index < BatteryControllerPorts.INPUTS ? reserveMoveByPort[index] : 0;
    }

    public int bankDrawnFePerTick() {
        return lastBankDrawn;
    }

    public int bankChargedFePerTick() {
        return lastBankCharged;
    }

    public int bandHealthyPct() {
        return level != null && level.isClientSide() ? syncedHealthyPct : effectiveBand().healthyPct();
    }

    public int rideTicksShown() {
        return level != null && level.isClientSide() ? syncedRideTicks : rideTicks();
    }

    public int claimPctShown() {
        return level != null && level.isClientSide() ? syncedClaimPct : chargeClaimPct();
    }
}
