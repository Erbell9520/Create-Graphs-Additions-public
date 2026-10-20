package com.createstats.blockentity;

import com.createstats.block.DiagnosticJunctionBlock;
import com.createstats.power.DiagnosticJunctionStuds;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DiagnosticJunctionBE extends BlockEntity implements FeGridMember, FeWireTerminal,
        com.createstats.power.FeStudPanel,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public int maxLinksPerStud() {
        return 1;
    }

    public enum Fault {
        OK("J00", ChatFormatting.GREEN),
        NO_BUS("J10", ChatFormatting.DARK_GRAY),
        OPEN_CIRCUIT("J11", ChatFormatting.YELLOW),
        ROUTE_BLOCKED("J12", ChatFormatting.RED),
        SOURCE_STARVED("J20", ChatFormatting.YELLOW),
        BACKFEED("J21", ChatFormatting.AQUA),
        OVERLOAD("J30", ChatFormatting.RED),
        BUFFER_FULL("J31", ChatFormatting.GOLD),
        UNSTABLE("J32", ChatFormatting.YELLOW),

        ISOLATED("J40", ChatFormatting.RED);

        private final String code;
        private final ChatFormatting color;

        Fault(String code, ChatFormatting color) {
            this.code = code;
            this.color = color;
        }

        public String code() {
            return code;
        }

        public ChatFormatting color() {
            return color;
        }
    }

    public record StudLink(BlockPos to, WireTier tier) {
    }

    private record StudSample(int flow, int rate, int stored, int capacity, boolean full) {
    }

    private static final int SYNC_INTERVAL = 10;

    public static final int JUNCTION_BUFFER = 64;

    @Nullable
    private FeWireNetwork network;
    private int energy;
    // always zero here, the solver only records flow for nodes with storage. kept because the interface wants it
    private int lastFlow;

    private int flow;
    private int capacity;
    private int buffer;
    private int maxBuffer;
    private Fault fault = Fault.NO_BUS;
    private final Fault[] channels = {Fault.NO_BUS, Fault.NO_BUS, Fault.NO_BUS};

    private final int[] channelFlow = new int[DiagnosticJunctionStuds.CHANNELS];
    private final int[] channelCapacity = new int[DiagnosticJunctionStuds.CHANNELS];
    private final int[] channelBuffer = new int[DiagnosticJunctionStuds.CHANNELS];
    private int lastSignature = Integer.MIN_VALUE;
    private boolean merged;
    private boolean cutOff;

    private int spliceRate = PowerConnectorBE.baseTransferRate();

    private final List<List<StudLink>> studLinks = newStudLinks();

    // needs a cap or runs draw a pixel short of the box
    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(Math.min(maxReceive, transferRate()), bufferCapacity() - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int taken = Math.min(Math.min(maxExtract, transferRate()), energy);
            if (taken <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= taken;
                setChanged();
            }
            return FeWireNetwork.noteSeamTransfer(network, level, simulate, taken, nodeKey());
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
            return true;
        }
    };

    public DiagnosticJunctionBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public IEnergyStorage energyHandle() {
        return isCableSplice() && !cutOff ? energyView : null;
    }

    public boolean isCableSplice() {
        return kind() == DiagnosticJunctionBlock.Kind.CABLE;
    }

    private static List<List<StudLink>> newStudLinks() {
        List<List<StudLink>> lists = new ArrayList<>(DiagnosticJunctionStuds.COUNT);
        for (int i = 0; i < DiagnosticJunctionStuds.COUNT; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (isCableSplice()) {
            if (network == null || network.isDirty()) {
                FeWireNetwork.buildFrom(this);
            }
            if (network != null) {
                network.solveOnce(level);
            }
        } else if (wiresOnStuds() > 0) {
            pruneStudLinks();
        }
        if ((level.getGameTime() + Math.floorMod(worldPosition.hashCode(), SYNC_INTERVAL)) % SYNC_INTERVAL != 0) {
            return;
        }
        if (isCableSplice()) {
            scanCableRun(level);
        } else {
            scanWireStuds(level);
        }
        syncIfChanged();
    }

    public DiagnosticJunctionBlock.Kind kind() {
        Block block = getBlockState().getBlock();
        return block instanceof DiagnosticJunctionBlock junction
                ? junction.kind()
                : DiagnosticJunctionBlock.Kind.CABLE;
    }

    public int flow() {
        return flow;
    }

    public int capacity() {
        return capacity;
    }

    public int buffer() {
        return buffer;
    }

    public int maxBuffer() {
        return maxBuffer;
    }

    public Fault fault() {
        return fault;
    }

    public Fault channel(int index) {
        return index >= 0 && index < channels.length ? channels[index] : Fault.NO_BUS;
    }

    public float loadFraction() {
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, flow / (float) capacity));
    }

    public boolean isOpen() {
        return getBlockState().hasProperty(DiagnosticJunctionBlock.OPEN)
                && getBlockState().getValue(DiagnosticJunctionBlock.OPEN);
    }

    public boolean isMerged() {
        return merged;
    }

    public boolean isCutOff() {
        return cutOff;
    }

    public void toggleMerged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        merged = !merged;
        syncStudLinks();
    }

    public void toggleCutOff() {
        if (level == null || level.isClientSide()) {
            return;
        }
        cutOff = !cutOff;
        syncStudLinks();
        invalidateAdjacentConductors();
    }

    private void scanCableRun(Level level) {
        int legs = 0;
        int best = 0;
        int sumBuffer = 0;
        int sumMaxBuffer = 0;
        boolean neighbourHot = false;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof CableBE cable) {
                legs++;
                best = Math.max(best, cable.transferRate());
                sumBuffer += cable.storedEnergy();
                sumMaxBuffer += cable.bufferCapacity();
                neighbourHot |= cable.isOverloaded();
            }
        }

        spliceRate = best > 0 ? best : PowerConnectorBE.baseTransferRate();
        if (legs == 0) {
            scanConnectorFallback(level);
            return;
        }
        final int carried = circuitThroughput(level);
        flow = cutOff ? 0 : carried;
        capacity = spliceRate;
        readCircuitBuffer(energy + sumBuffer, bufferCapacity() + sumMaxBuffer);
        fault = cutOff ? Fault.ISOLATED
            : neighbourHot || carried >= Math.max(1, spliceRate) ? Fault.OVERLOAD
                : legs < 2 ? Fault.OPEN_CIRCUIT
                : circuitStarved() ? Fault.SOURCE_STARVED
                : Fault.OK;
        channels[0] = fault;
        channels[1] = Fault.NO_BUS;
        channels[2] = Fault.NO_BUS;
    }

    // this used to be abs(lastFlow), which is always 0 for a junction. it showed 0 everywhere for months
    private int circuitThroughput(Level level) {
        if (network == null) {
            return 0;
        }
        final long now = level.getGameTime();
        final int mine = network.flowThrough(nodeKey(), now);
        return mine == com.createstats.power.FeFlowGraph.UNKNOWN
                ? network.throughputFePerTick(now) : mine;
    }

    private boolean circuitStarved() {
        if (network == null || level == null || network.isPduManaged(level.getGameTime())) {
            return false;
        }
        final com.createstats.power.FeGridSolver.Result grid = network.lastResult();
        return grid.shortfall() > 0 && grid.available() <= 0;
    }

    private void readCircuitBuffer(int localBuffer, int localCapacity) {
        final com.createstats.power.FeGridSolver.Result grid = network == null ? null : network.lastResult();
        if (grid != null && grid.capacity() > 0) {
            buffer = Math.max(0, grid.stored());
            maxBuffer = grid.capacity();
            return;
        }
        buffer = localBuffer;
        maxBuffer = localCapacity;
    }

    private void scanConnectorFallback(Level level) {
        List<PowerConnectorBE> terminals = adjacentConnectors(level);
        if (terminals.isEmpty()) {
            setEmpty(Fault.NO_BUS);
            return;
        }
        spliceRate = Math.max(spliceRate, terminals.get(0).transferRate());
        int sumCapacity = 0;
        int sumBuffer = 0;
        int sumMaxBuffer = 0;
        boolean full = false;
        for (PowerConnectorBE connector : terminals) {
            sumCapacity += Math.max(1, connector.transferRate());
            sumBuffer += connector.storedEnergy();
            sumMaxBuffer += connector.bufferCapacity();
            full |= connector.storedEnergy() >= connector.bufferCapacity();
        }

        flow = cutOff ? 0 : circuitThroughput(level);
        capacity = sumCapacity;
        readCircuitBuffer(sumBuffer, sumMaxBuffer);
        fault = cutOff ? Fault.ISOLATED : full ? Fault.BUFFER_FULL
                : circuitStarved() ? Fault.SOURCE_STARVED : Fault.OK;
        channels[0] = fault;
        channels[1] = Fault.NO_BUS;
        channels[2] = Fault.NO_BUS;
    }

    private void scanWireStuds(Level level) {
        int sumFlow = 0;
        int sumCapacity = 0;
        int sumBuffer = 0;
        int sumMaxBuffer = 0;
        Fault worst = wiresOnStuds() == 0 ? Fault.NO_BUS : Fault.OK;
        for (int channel = 0; channel < DiagnosticJunctionStuds.CHANNELS; channel++) {
            Fault channelFault = classifyChannel(level, channel);
            channels[channel] = channelFault;
            if (severity(channelFault) > severity(worst)) {
                worst = channelFault;
            }

            int throughFlow = 0;
            int throughRate = 0;
            int held = 0;
            int room = 0;
            boolean any = false;
            for (int stud : new int[]{channel, channel + DiagnosticJunctionStuds.CHANNELS}) {
                for (StudLink link : studLinks.get(stud)) {
                    if (!level.isLoaded(link.to())
                            || !(level.getBlockEntity(link.to())
                            instanceof com.createstats.power.FeGridMember far)) {
                        continue;
                    }
                    any = true;
                    final StudSample sample = sampleStudTerminal(level, far, link);
                    throughFlow = Math.max(throughFlow, sample.flow());
                    throughRate = throughRate == 0 ? sample.rate() : Math.min(throughRate, sample.rate());
                    held += sample.stored();
                    room += sample.capacity();
                    if (sample.full()
                            && severity(worst) < severity(Fault.BUFFER_FULL)) {
                        worst = Fault.BUFFER_FULL;
                    }
                }
            }
            channelFlow[channel] = any && !cutOff ? throughFlow : 0;
            channelCapacity[channel] = any ? throughRate : 0;
            channelBuffer[channel] = held;
            if (cutOff && any) {
                channels[channel] = Fault.ISOLATED;
                if (severity(worst) < severity(Fault.ISOLATED)) {
                    worst = Fault.ISOLATED;
                }
            } else if (any && channelFlow[channel] >= Math.max(1, channelCapacity[channel])) {
                channels[channel] = Fault.OVERLOAD;
                worst = Fault.OVERLOAD;
            }
            sumFlow += channelFlow[channel];
            sumCapacity += channelCapacity[channel];
            sumBuffer += held;
            sumMaxBuffer += room;
        }
        flow = sumFlow;
        capacity = sumCapacity;
        buffer = sumBuffer;
        maxBuffer = sumMaxBuffer;
        fault = worst;
    }

    private static StudSample sampleStudTerminal(Level level,
                                                 com.createstats.power.FeGridMember far,
                                                 StudLink link) {
        final int rate = Math.max(1,
                Math.min(far.transferRate(),
                        PowerConnectorBE.baseTransferRate() * link.tier().multiplier()));
        final FeWireNetwork circuit = far.network();

        final int through = circuit == null ? com.createstats.power.FeFlowGraph.UNKNOWN
                : circuit.flowThrough(far.nodeKey(), level.getGameTime());
        int flow = Math.abs(far.lastFlow());
        if (through != com.createstats.power.FeFlowGraph.UNKNOWN) {
            flow = Math.max(flow, through);
        } else if (circuit != null) {
            flow = Math.max(flow, circuit.throughputFePerTick(level.getGameTime()));
        }
        int stored = far.storedEnergy();
        int capacity = far.bufferCapacity();
        boolean full = far.storedEnergy() >= far.bufferCapacity();

        final IEnergyStorage attached = far.attachedStorage();
        if (attached != null) {
            stored = com.createstats.power.FeRates.plus(stored,
                    Math.max(0, attached.getEnergyStored()));
            capacity = com.createstats.power.FeRates.plus(capacity,
                    Math.max(0, attached.getMaxEnergyStored()));
            full |= attached.getMaxEnergyStored() > 0
                    && attached.getEnergyStored() >= attached.getMaxEnergyStored();
            if (attached instanceof com.createstats.power.FeConsumer fe) {
                flow = Math.max(flow, fe.measuredDrainFePerTick());
            }
        }

        final BlockEntity served = far instanceof PowerConnectorBE connector
                ? level.getBlockEntity(connector.getBlockPos().relative(connector.mountedOn()))
                : (far instanceof BlockEntity be ? be : null);

        if (served instanceof PowerDistributionUnitBE pdu) {
            flow = Math.max(flow, Math.max(pdu.measuredInputFePerTick(), pdu.lastBranchResult().delivered()));
        } else if (served instanceof com.createstats.power.FeRatedLoad rated) {
            flow = Math.max(flow, rated.measuredDrawFePerTick());
        }
        return new StudSample(flow, rate, stored, capacity, full);
    }

    public int channelFlow(int channel) {
        return channel >= 0 && channel < channelFlow.length ? channelFlow[channel] : 0;
    }

    public int channelCapacity(int channel) {
        return channel >= 0 && channel < channelCapacity.length ? channelCapacity[channel] : 0;
    }

    public int channelBuffer(int channel) {
        return channel >= 0 && channel < channelBuffer.length ? channelBuffer[channel] : 0;
    }

    private Fault classifyChannel(Level level, int channel) {
        boolean top = !studLinks.get(channel).isEmpty();
        boolean bottom = !studLinks.get(channel + DiagnosticJunctionStuds.CHANNELS).isEmpty();
        if (cutOff) {
            return top || bottom ? Fault.ISOLATED : Fault.NO_BUS;
        }
        if (merged) {
            return top || bottom ? Fault.OK : Fault.NO_BUS;
        }
        if (!top && !bottom) {
            return Fault.NO_BUS;
        }
        if (top != bottom) {
            return Fault.OPEN_CIRCUIT;
        }
        for (int stud : new int[]{channel, channel + DiagnosticJunctionStuds.CHANNELS}) {
            for (StudLink link : studLinks.get(stud)) {
                if (level.isLoaded(link.to())
                        && !(level.getBlockEntity(link.to())
                        instanceof com.createstats.power.FeWireTerminal)) {
                    return Fault.ROUTE_BLOCKED;
                }
            }
        }
        return Fault.OK;
    }

    private List<PowerConnectorBE> adjacentConnectors(Level level) {
        List<PowerConnectorBE> found = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof PowerConnectorBE connector) {
                found.add(connector);
            }
        }
        return found;
    }

    private void setEmpty(Fault emptyFault) {
        flow = 0;
        capacity = 0;
        buffer = 0;
        maxBuffer = 0;
        fault = emptyFault;
        for (int i = 0; i < channels.length; i++) {
            channels[i] = Fault.NO_BUS;
            channelFlow[i] = 0;
            channelCapacity[i] = 0;
            channelBuffer[i] = 0;
        }
    }

    private static int severity(Fault fault) {
        return switch (fault) {
            case ISOLATED, OVERLOAD, ROUTE_BLOCKED -> 4;
            case BUFFER_FULL, BACKFEED, UNSTABLE -> 3;
            case OPEN_CIRCUIT, SOURCE_STARVED -> 2;
            case NO_BUS -> 1;
            default -> 0;
        };
    }

    public List<StudLink> studLinks(int stud) {
        return DiagnosticJunctionStuds.isStudIndex(stud) ? studLinks.get(stud) : List.of();
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

    public List<BlockPos> reachableTerminalsFrom(BlockPos from) {
        if (cutOff) {
            return List.of();
        }
        if (!isLinkedToConnector(from)) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        if (merged) {
            for (List<StudLink> links : studLinks) {
                appendLinkedPositionsExcept(out, links, from);
            }
            return out;
        }
        for (int stud = 0; stud < DiagnosticJunctionStuds.COUNT; stud++) {
            if (!studHasConnector(stud, from)) {
                continue;
            }
            appendLinkedPositionsExcept(out, studLinks.get(DiagnosticJunctionStuds.pairedStud(stud)), from);
        }
        return out;
    }

    private boolean studHasConnector(int stud, BlockPos connector) {
        for (StudLink link : studLinks(stud)) {
            if (link.to().equals(connector)) {
                return true;
            }
        }
        return false;
    }

    private static void appendLinkedPositionsExcept(List<BlockPos> out, List<StudLink> links, BlockPos except) {
        for (StudLink link : links) {
            if (!link.to().equals(except) && !out.contains(link.to())) {
                out.add(link.to());
            }
        }
    }

    @Override
    public void linkStud(int stud, com.createstats.power.FeWireTerminal terminal, WireTier tier) {
        if (level == null || level.isClientSide() || !DiagnosticJunctionStuds.isStudIndex(stud)) {
            return;
        }
        WireTier actualTier = tier == null ? WireTier.COPPER : tier;
        studLinks.get(stud).add(new StudLink(terminal.terminalPos().immutable(), actualTier));
        terminal.addLink(worldPosition, actualTier);
        syncStudLinks();
    }

    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || !DiagnosticJunctionStuds.isStudIndex(stud)) {
            return 0;
        }
        List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        int removed = links.size();
        Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);

            if (level.getBlockEntity(link.to())
                    instanceof com.createstats.power.FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        for (Map.Entry<WireTier, Integer> entry : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(com.createstats.registry.ModItems.wireItem(entry.getKey()), entry.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    public void dropAllStudWires() {
        for (int stud = 0; stud < DiagnosticJunctionStuds.COUNT; stud++) {
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
        invalidateNetwork();
        invalidateLinkedConnectors();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void invalidateLinkedConnectors() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                if (level.getBlockEntity(link.to())
                        instanceof com.createstats.power.FeGridMember member) {
                    member.invalidateNetwork();
                }
            }
        }
    }

    private void invalidateAdjacentConductors() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof FeGridMember member) {
                member.invalidateNetwork();
            }
        }
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return energy;
    }

    @Override
    public void setStoredEnergy(int amount) {
        int clamped = Math.max(0, Math.min(amount, bufferCapacity()));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    @Override
    public int bufferCapacity() {
        return JUNCTION_BUFFER;
    }

    @Override
    public int transferRate() {
        if (cutOff) {
            return 0;
        }
        final int base = PowerConnectorBE.baseTransferRate();
        if (isCableSplice()) {
            return Math.max(base, spliceRate);
        }
        int sum = 0;
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                sum += base * link.tier().multiplier();
            }
        }
        return sum <= 0 ? base : sum;
    }

    @Nullable
    @Override
    public IEnergyStorage attachedStorage() {
        return null;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
        lastFlow = flow;
    }

    public void adoptNetwork(FeWireNetwork adopted) {
        network = adopted;
    }

    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
        }
        network = null;
    }

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        List<BlockPos> out = new ArrayList<>();
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                out.add(link.to());
            }
        }
        return out;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        for (List<StudLink> links : studLinks) {
            for (StudLink studLink : links) {
                if (studLink.to().equals(link)) {
                    return studLink.tier();
                }
            }
        }
        return WireTier.COPPER;
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        for (int stud = 0; stud < DiagnosticJunctionStuds.COUNT; stud++) {
            if (studLinks.get(stud).isEmpty()) {
                studLinks.get(stud).add(new StudLink(far.immutable(), tier == null ? WireTier.COPPER : tier));
                syncStudLinks();
                return;
            }
        }
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        boolean changed = false;
        for (List<StudLink> links : studLinks) {
            changed |= links.removeIf(link -> link.to().equals(far));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        dropAllStudWires();
    }

    @Override
    public int maxLinks() {
        return DiagnosticJunctionStuds.COUNT;
    }

    private void syncIfChanged() {
        int signature = flow;
        signature = signature * 31 + capacity;
        signature = signature * 31 + buffer;
        signature = signature * 31 + maxBuffer;
        signature = signature * 31 + energy;
        signature = signature * 31 + fault.ordinal();
        for (Fault channel : channels) {
            signature = signature * 31 + channel.ordinal();
        }
        for (int i = 0; i < channelFlow.length; i++) {
            signature = signature * 31 + channelFlow[i];
            signature = signature * 31 + channelCapacity[i];
            signature = signature * 31 + channelBuffer[i];
        }
        signature = signature * 31 + wiresOnStuds();
        signature = signature * 31 + (merged ? 1 : 0);
        signature = signature * 31 + (cutOff ? 1 : 0);
        if (signature == lastSignature) {
            return;
        }
        lastSignature = signature;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final boolean wire = !isCableSplice();
        tooltip.add(goggleLine(Component.literal(wire ? "Wire Terminal Junction" : "Cable Diagnostic Junction")
                .withStyle(ChatFormatting.GRAY)));
        if (wire && !merged) {
            tooltip.add(goggleLine(Component.literal(cutOff ? "Power: cut off" : "Power: conducting")
                .withStyle(cutOff ? ChatFormatting.RED : ChatFormatting.GREEN)));
            tooltip.add(goggleLine(Component.literal("Mode: separate A, B, C")
                    .withStyle(ChatFormatting.DARK_AQUA)));

            for (int channel = 0; channel < DiagnosticJunctionStuds.CHANNELS; channel++) {
                final Fault state = channels[channel];
                final String row = isPlayerSneaking
                        ? String.format(Locale.ROOT, "%s: %s, %s, %,d / %,d FE/t, %,d FE held",
                                (char) ('A' + channel), channelStatus(state), channelWiring(channel),
                                channelFlow[channel], channelCapacity[channel], channelBuffer[channel])
                        : String.format(Locale.ROOT, "%s: %s, %,d / %,d FE/t",
                                (char) ('A' + channel), channelStatus(state),
                                channelFlow[channel], channelCapacity[channel]);
                tooltip.add(goggleLine(Component.literal(row).withStyle(state.color())));
            }

            if (isPlayerSneaking || fault != Fault.OK) {
                tooltip.add(goggleLine(Component.literal("Overall: " + channelStatus(fault) + " " + fault.code())
                        .withStyle(fault.color())));
            }
            if (!isPlayerSneaking) {
                tooltip.add(com.createstats.power.FeGoggle.more());
            }
            return true;
        }
        if (wire) {
            tooltip.add(goggleLine(Component.literal(cutOff ? "Power: cut off" : "Power: conducting")
                .withStyle(cutOff ? ChatFormatting.RED : ChatFormatting.GREEN)));
            tooltip.add(goggleLine(Component.literal("Mode: merged bus, all six studs")
                    .withStyle(ChatFormatting.GOLD)));
        } else {
            tooltip.add(goggleLine(Component.literal(cutOff ? "Power: cut off" : "Power: conducting")
                .withStyle(cutOff ? ChatFormatting.RED : ChatFormatting.GREEN)));
        }
        tooltip.add(goggleLine(Component
                .literal(String.format(Locale.ROOT, "Flow: %,d / %,d FE/t", flow, capacity))
                .withStyle(fault.color())));
        if (isPlayerSneaking && maxBuffer > 0) {
            tooltip.add(goggleLine(Component
                    .literal(String.format(Locale.ROOT, "Buffer: %,d / %,d FE", buffer, maxBuffer))
                    .withStyle(ChatFormatting.AQUA)));
        }

        if (isPlayerSneaking || fault != Fault.OK) {
            tooltip.add(goggleLine(Component.literal("Code: " + fault.code()).withStyle(fault.color())));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    private static Component goggleLine(Component text) {
        return Component.literal("    ").append(text);
    }

    private String channelWiring(int channel) {
        final boolean top = !studLinks.get(channel).isEmpty();
        final boolean bottom = !studLinks.get(channel + DiagnosticJunctionStuds.CHANNELS).isEmpty();
        if (top && bottom) {
            return "top and bottom wired";
        }
        if (top) {
            return "top only wired";
        }
        if (bottom) {
            return "bottom only wired";
        }
        return "no wires";
    }

    public static String channelStatus(Fault fault) {
        return switch (fault) {
            case OK -> "OK";
            case NO_BUS -> "No bus";
            case OPEN_CIRCUIT -> "Open circuit";
            case SOURCE_STARVED -> "Source starved";
            case OVERLOAD -> "Overload";
            case BUFFER_FULL -> "Buffer full";
            case BACKFEED -> "Backfeed";
            case ROUTE_BLOCKED -> "Route blocked";
            case UNSTABLE -> "Unstable";
            case ISOLATED -> "Isolated";
        };
    }

    public static String shortCode(Fault fault) {
        return switch (fault) {
            case OK -> "OK";
            case NO_BUS -> "--";
            case OPEN_CIRCUIT -> "OP";
            case SOURCE_STARVED -> "ST";
            case OVERLOAD -> "OV";
            case BUFFER_FULL -> "BF";
            case BACKFEED -> "BK";
            case ROUTE_BLOCKED -> "RB";
            case UNSTABLE -> "UN";
            case ISOLATED -> "IS";
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("LastFlow", lastFlow);
        tag.putInt("Flow", flow);
        tag.putInt("Capacity", capacity);
        tag.putInt("Buffer", buffer);
        tag.putInt("MaxBuffer", maxBuffer);
        tag.putByte("Fault", (byte) fault.ordinal());
        tag.putBoolean("Merged", merged);
        tag.putBoolean("CutOff", cutOff);
        tag.putInt("SpliceRate", spliceRate);
        tag.putIntArray("ChFlow", channelFlow.clone());
        tag.putIntArray("ChRate", channelCapacity.clone());
        tag.putIntArray("ChBuffer", channelBuffer.clone());
        byte[] channelBytes = new byte[channels.length];
        for (int i = 0; i < channels.length; i++) {
            channelBytes[i] = (byte) channels[i].ordinal();
        }
        tag.putByteArray("Channels", channelBytes);
        ListTag spans = new ListTag();
        for (int stud = 0; stud < DiagnosticJunctionStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                CompoundTag span = new CompoundTag();
                span.putByte("S", (byte) stud);
                span.putLong("P", link.to().asLong());
                span.putByte("T", (byte) link.tier().ordinal());
                spans.add(span);
            }
        }
        tag.put("StudWires", spans);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(bufferCapacity(), tag.getInt("Energy")));
        lastFlow = tag.getInt("LastFlow");
        flow = Math.max(0, tag.getInt("Flow"));
        capacity = Math.max(0, tag.getInt("Capacity"));
        buffer = Math.max(0, tag.getInt("Buffer"));
        maxBuffer = Math.max(0, tag.getInt("MaxBuffer"));
        fault = faultByOrdinal(tag.getByte("Fault"));
        merged = tag.getBoolean("Merged");
        cutOff = tag.getBoolean("CutOff");
        spliceRate = Math.max(1, tag.contains("SpliceRate")
                ? tag.getInt("SpliceRate") : PowerConnectorBE.baseTransferRate());
        readChannelInts(tag.getIntArray("ChFlow"), channelFlow);
        readChannelInts(tag.getIntArray("ChRate"), channelCapacity);
        readChannelInts(tag.getIntArray("ChBuffer"), channelBuffer);
        byte[] channelBytes = tag.getByteArray("Channels");
        for (int i = 0; i < channels.length; i++) {
            channels[i] = i < channelBytes.length ? faultByOrdinal(channelBytes[i]) : Fault.NO_BUS;
        }
        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        ListTag spans = tag.getList("StudWires", 10);
        for (int i = 0; i < spans.size(); i++) {
            CompoundTag span = spans.getCompound(i);
            int stud = span.getByte("S");
            if (!DiagnosticJunctionStuds.isStudIndex(stud)) {
                continue;
            }
            studLinks.get(stud).add(new StudLink(BlockPos.of(span.getLong("P")),
                    WireTier.byOrdinal(span.getByte("T"))));
        }
    }

    private static void readChannelInts(int[] saved, int[] into) {
        for (int i = 0; i < into.length; i++) {
            into[i] = i < saved.length ? Math.max(0, saved[i]) : 0;
        }
    }

    private static Fault faultByOrdinal(int ordinal) {
        Fault[] values = Fault.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Fault.NO_BUS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
