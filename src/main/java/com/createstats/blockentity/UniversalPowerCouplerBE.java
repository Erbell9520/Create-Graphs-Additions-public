package com.createstats.blockentity;

import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.compat.CreateAdditionCompatHelper;
import com.createstats.compat.NewAgeCompat;
import com.createstats.compat.NewAgeCompatHelper;
import com.createstats.power.CouplerBuffer;
import com.createstats.power.CouplerEndpoint;
import com.createstats.power.CouplerFault;
import com.createstats.power.CouplerForeign;
import com.createstats.power.CouplerLoop;
import com.createstats.power.CouplerPrice;
import com.createstats.power.CouplerPushPlan;
import com.createstats.power.CouplerRating;
import com.createstats.power.CouplerSockets;
import com.createstats.power.CouplerSpanWatch;
import com.createstats.power.CouplerSyncGate;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.ForeignTerminalNode;
import com.createstats.power.NewAgeFeed;
import com.createstats.power.PowerCouplerMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModDataComponents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class UniversalPowerCouplerBE extends SmartBlockEntity
        implements FeManagedLoad, FeWireTerminal, IHaveGoggleInformation {
    public static final int RATE_FE_PER_TICK = 8192;

    public static final int BUFFER_FE = 16384;

    private static final int SURVEY_INTERVAL = 20;

    private static final int SYNC_INTERVAL_TICKS = 10;

    private static final int SYNC_THRESHOLD_FE = 64;

    private static final int SYNC_THRESHOLD_STORE = BUFFER_FE / 64;

    private static final int MAX_GRID_LINKS = com.createstats.power.ConnectorTier.POWER.maxLinks();

    private static final Map<Level, CouplerLoop.Registry> LOOPS = new WeakHashMap<>();

    private final CouplerBuffer buffer = new CouplerBuffer(BUFFER_FE);
    private final IEnergyStorage newAgePort = new NewAgePort();
    private final IEnergyStorage strictPort = new StrictPort();
    private final IEnergyStorage gridPort = new GridPort();
    private final FeManagedState managed = new FeManagedState();

    private int importFePerTick;
    private int exportFePerTick;
    private boolean looped;
    private CouplerFault fault = CouplerFault.NONE;
    private long faultChangedAt = CouplerSyncGate.NEVER;
    private CouplerEndpoint endpoint = CouplerEndpoint.NONE;
    private boolean gridFaceLive;
    private int ratingFePerTick = RATE_FE_PER_TICK;

    private int theirNodes = -1;
    private int theirSources = -1;
    private int theirGeneration = -1;
    private int theirWire = -1;
    private int theirPullSources = -1;

    private long modeChangedAt = CouplerSyncGate.NEVER;

    private PowerCouplerMode rememberedDirection = PowerCouplerMode.PULL;

    private final List<BlockPos> links = new ArrayList<>(2);

    private final Map<Long, WireTier> linkTiers = new HashMap<>(4);

    @Nullable
    private BlockPos seamLink;

    private CouplerForeign.Verdict faceVerdict = CouplerForeign.Verdict.EMPTY;
    private boolean faceVerdictValid;
    @Nullable
    private PowerCouplerMode seenMode;
    private boolean seenForcedOff;
    private int seamMisses;
    private final Map<Long, Integer> gridMisses = new HashMap<>(4);
    private final CouplerPrice.Smoothed accepts = new CouplerPrice.Smoothed();
    private int supplyFePerTick;
    private boolean wrongFace;
    private boolean ownBlockThere;
    private boolean facesAway;
    private boolean theirInert;
    private boolean connectorOpposes;
    @Nullable
    private NewAgeCompatHelper.NetworkView theirSurvey;
    private List<FeWireNetwork> circuits = List.of();
    private boolean surveyed;

    private int syncedImport;
    private int syncedExport;
    private int syncedStored;
    private boolean syncedLooped;
    private CouplerFault syncedFault = CouplerFault.NONE;
    private CouplerEndpoint syncedEndpoint = CouplerEndpoint.NONE;
    private boolean syncedGridFace;
    private int syncedRating = RATE_FE_PER_TICK;
    private long syncedModeChangedAt = CouplerSyncGate.NEVER;
    private int syncedLinks;
    private long lastSyncTick = CouplerSyncGate.NEVER;

    public UniversalPowerCouplerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public PowerCouplerMode mode() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(UniversalPowerCouplerBlock.MODE)
                ? state.getValue(UniversalPowerCouplerBlock.MODE) : PowerCouplerMode.PULL;
    }

    public Direction foreignFace() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(UniversalPowerCouplerBlock.FACING)
                ? state.getValue(UniversalPowerCouplerBlock.FACING) : Direction.NORTH;
    }

    public Direction gridFace() {
        return this.foreignFace().getOpposite();
    }

    public int importFePerTick() {
        return this.importFePerTick;
    }

    public int exportFePerTick() {
        return this.exportFePerTick;
    }

    public int crossingFePerTick() {
        return this.mode() == PowerCouplerMode.PULL ? this.importFePerTick : this.exportFePerTick;
    }

    public boolean isLooped() {
        return this.looped;
    }

    public int storedFe() {
        return this.buffer.energy();
    }

    public long modeChangedAt() {
        return this.modeChangedAt;
    }

    public CouplerFault fault() {
        return this.fault;
    }

    public long faultChangedAt() {
        return this.faultChangedAt;
    }

    public CouplerEndpoint foreignEndpoint() {
        return this.endpoint;
    }

    public boolean gridFaceLive() {
        return this.gridFaceLive;
    }

    public boolean gridConnected() {
        return this.gridFaceLive || this.gridLinkCount() > 0;
    }

    public int ratingFePerTick() {
        return this.ratingFePerTick;
    }

    public CouplerSockets.End socketOf(@Nullable BlockPos link) {
        return CouplerSockets.endOf(this.isSeamLink(link));
    }

    public int theirNodes() {
        return this.theirNodes;
    }

    public int theirSources() {
        return this.theirSources;
    }

    public int theirGeneration() {
        return this.theirGeneration;
    }

    public int theirWire() {
        return this.theirWire;
    }

    public int theirPullSources() {
        return this.theirPullSources;
    }

    @Nullable
    public NewAgeCompatHelper.NetworkView theirSurvey() {
        return this.theirSurvey;
    }

    public int supplyFePerTick() {
        return this.supplyFePerTick;
    }

    @Nullable
    public IEnergyStorage seamHandle() {
        if (this.seamLink != null) {
            return null;
        }
        if (!this.faceVerdictValid && this.level != null) {
            this.faceVerdictValid = true;
            this.faceVerdict = CouplerForeign.Verdict.EMPTY;
            this.faceVerdict = NewAgeFeed.foreignVerdict(this.level, this.worldPosition, this.foreignFace());
        }
        return switch (this.faceVerdict) {
            case NEW_AGE_CONNECTOR -> this.newAgePort;
            case CREATE_ADDITION, GENERIC -> this.strictPort;
            default -> null;
        };
    }

    public void invalidateForeignSeam() {
        this.faceVerdictValid = false;
    }

    @Nullable
    public BlockPos wiredSeamPos() {
        return this.seamLink;
    }

    public boolean isSeamLink(@Nullable BlockPos link) {
        return link != null && link.equals(this.seamLink);
    }

    public int gridLinkCount() {
        return this.links.size() - (this.seamLink == null ? 0 : 1);
    }

    public static boolean isSeamTarget(@Nullable Level level, @Nullable BlockPos pos) {
        return NewAgeFeed.farVerdict(level, pos).isEndpoint();
    }

    private int seamWireRate() {
        return this.seamLink == null ? -1
                : PowerConnectorBE.baseTransferRate() * this.tierOf(this.seamLink).multiplier();
    }

    @Override
    public BlockPos terminalPos() {
        return this.worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return this.links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return this.linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        this.links.add(at);
        this.linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        final boolean seam = isSeamTarget(this.level, at);
        if (seam) {
            this.seamLink = at;
            this.seamMisses = 0;
        }
        this.linksChanged(seam);
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        this.linkTiers.remove(far.asLong());
        if (this.links.remove(far)) {
            final boolean seam = far.equals(this.seamLink);
            if (seam) {
                this.seamLink = null;
            }
            this.gridMisses.remove(far.asLong());
            this.linksChanged(seam);
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (this.level == null || this.level.isClientSide() || this.links.isEmpty()) {
            return;
        }
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        final boolean hadSeam = this.seamLink != null;
        for (BlockPos link : new ArrayList<>(this.links)) {
            refund.merge(this.tierOf(link), 1, Integer::sum);

            if (link.equals(this.seamLink)) {
                continue;
            }
            if (this.level.isLoaded(link) && this.level.getBlockEntity(link) instanceof FeWireTerminal far) {
                far.removeDeadLink(this.worldPosition);
            }
        }
        this.links.clear();
        this.linkTiers.clear();
        this.gridMisses.clear();
        this.seamLink = null;
        this.linksChanged(hadSeam);
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(this.level, this.worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void letGo(BlockPos far) {
        if (this.level == null || !this.links.contains(far)) {
            return;
        }
        final WireTier tier = this.tierOf(far);
        this.removeDeadLink(far);
        net.minecraft.world.level.block.Block.popResource(this.level, this.worldPosition,
                new net.minecraft.world.item.ItemStack(com.createstats.registry.ModItems.wireItem(tier), 1));
    }

    @Override
    public int maxLinks() {
        return 1 + MAX_GRID_LINKS;
    }

    @Nullable
    public Component refuseSocket(CouplerSockets.End socket) {
        if (socket == CouplerSockets.End.FOREIGN) {
            return this.seamLink == null ? null
                    : Component.translatable("message.create_stats.coupler.seam_socket_full");
        }
        return this.gridLinkCount() < MAX_GRID_LINKS ? null
                : Component.translatable("message.create_stats.coupler.grid_socket_full", MAX_GRID_LINKS);
    }

    @Override
    @Nullable
    public Component refuseLink(@Nullable Level world, BlockPos far) {
        final Level here = world == null ? this.level : world;
        if (isSeamTarget(here, far)) {
            return this.refuseSocket(CouplerSockets.End.FOREIGN);
        }
        final BlockEntity there = here == null ? null : here.getBlockEntity(far);
        if (there instanceof UniversalPowerCouplerBE) {
            return Component.translatable("message.create_stats.coupler.no_coupler_chain");
        }
        if (there instanceof com.createstats.power.FeStudPanel && !(there instanceof FeWireTerminal)) {
            return Component.translatable("message.create_stats.coupler.grid_no_stud");
        }
        return this.refuseSocket(CouplerSockets.End.GRID);
    }

    private void linksChanged(boolean seamChanged) {
        this.setChanged();
        this.invalidateForeignSeam();

        this.surveyed = false;
        if (this.level != null && !this.level.isClientSide()) {
            if (seamChanged) {
                this.tellNeighbours();
            }
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    private void tellNeighbours() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.level.invalidateCapabilities(this.worldPosition);
        this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
    }

    public IEnergyStorage gridHandle() {
        return this.gridPort;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final long now = this.level.getGameTime();

        this.faceVerdictValid = false;

        boolean roleChanged = false;

        final PowerCouplerMode mode = this.mode();
        if (this.seenMode == null) {
            this.seenMode = mode;
            this.remember(mode);
        } else if (this.seenMode != mode) {
            this.seenMode = mode;
            this.modeChangedAt = now;
            this.remember(mode);
            this.accepts.reset();
            roleChanged = true;
        }
        final boolean forcedOff = this.managed.isForcedOff();
        if (forcedOff != this.seenForcedOff) {
            this.seenForcedOff = forcedOff;
            roleChanged = true;
        }

        final CouplerBuffer.Tick closed = this.buffer.roll(now);
        if (closed != null) {
            this.importFePerTick = Math.max(0, closed.seamMoved());
            this.exportFePerTick = Math.max(0, -closed.seamMoved());
        }

        if (!this.surveyed || now % SURVEY_INTERVAL == 0) {
            this.surveyed = true;
            roleChanged |= this.survey(now);
        }
        if (roleChanged) {
            this.reflood();
            this.tellNeighbours();
        }

        this.pumpSeamWire(now);
        this.pumpBolted();
        this.pumpThrough(now);

        final CouplerFault judged = CouplerFault.judge(new CouplerFault.Inputs(
                this.mode() == PowerCouplerMode.PULL, this.wrongFace, this.looped, this.endpoint,
                this.ownBlockThere, this.facesAway, this.gridConnected(), forcedOff, this.managed.isShed(),
                this.theirInert, this.theirPullSources, this.connectorOpposes, this.crossingFePerTick(),
                this.buffer.energy(), BUFFER_FE, !this.mode().isDirection()));
        if (judged != this.fault) {
            this.fault = judged;
            this.faultChangedAt = now;
            this.setChanged();
        }
        this.maybeSync(now);
    }

    private boolean survey(long now) {
        final Level level = this.level;

        final BlockPos far = this.seamLink;
        final CouplerForeign.Verdict verdict;
        final BlockPos endpointPos;
        if (far != null) {
            endpointPos = far;
            if (level.isLoaded(far)) {
                verdict = NewAgeFeed.farVerdict(level, far);
                this.seamMisses = CouplerSpanWatch.next(this.seamMisses, verdict.isEndpoint());
                if (CouplerSpanWatch.drop(this.seamMisses)) {
                    this.letGo(far);
                    return this.survey(now);
                }
            } else {
                verdict = CouplerForeign.Verdict.EMPTY;
            }
        } else {
            endpointPos = this.worldPosition.relative(this.foreignFace());
            verdict = NewAgeFeed.foreignVerdict(level, this.worldPosition, this.foreignFace());
        }
        final boolean wired = far != null;
        this.endpoint = verdict.isEndpoint() ? CouplerEndpoint.of(wired, verdict.family()) : CouplerEndpoint.NONE;

        if (wired && !level.isLoaded(far) && this.syncedEndpoint.wired()) {
            this.endpoint = this.syncedEndpoint;
        }
        this.ownBlockThere = verdict == CouplerForeign.Verdict.OURS;
        this.facesAway = verdict == CouplerForeign.Verdict.NEW_AGE_ELSEWHERE;
        this.wrongFace = !wired && NewAgeFeed.connectorOnNonForeignFace(level, this.worldPosition, this.foreignFace());

        final BlockEntity endpointBe = level.isLoaded(endpointPos) ? level.getBlockEntity(endpointPos) : null;
        this.theirSurvey = this.endpoint.isNewAge() ? NewAgeCompatHelper.surveyNetwork(endpointBe) : null;
        final NewAgeCompatHelper.NetworkView view = this.theirSurvey;
        this.theirNodes = view == null ? -1 : view.nodeCount();
        this.theirSources = view == null ? -1 : view.sourceCount();
        this.theirGeneration = view == null ? -1 : view.generation();
        this.theirWire = view == null ? -1 : view.conductivity();
        this.theirPullSources = this.endpoint.isNewAge() ? NewAgeCompatHelper.pullSourceCount(endpointBe) : -1;
        this.theirInert = this.endpoint == CouplerEndpoint.BOLTED_NEW_AGE
                && !NewAgeCompatHelper.isPullMode(level.getBlockState(endpointPos));
        this.connectorOpposes = this.endpoint == CouplerEndpoint.BOLTED_CREATE_ADDITION
                && CouplerForeign.createAdditionOpposes(connectorMode(level.getBlockState(endpointPos)),
                        this.mode() == PowerCouplerMode.PULL);

        final BlockPos gridAt = this.worldPosition.relative(this.gridFace());
        final BlockEntity gridBe = level.isLoaded(gridAt) ? level.getBlockEntity(gridAt) : null;
        this.gridFaceLive = gridBe instanceof FeGridMember
                || (level.isLoaded(gridAt) && level.getCapability(Capabilities.EnergyStorage.BLOCK, gridAt,
                        this.gridFace().getOpposite()) != null);
        this.pruneGridLinks();
        this.circuits = this.ourCircuits();

        final boolean wasLooped = this.looped;
        if (this.mode().isDirection()) {
            this.looped = this.loopRegistry().publishAndAsk(this.worldPosition.asLong(),
                    this.loopNode(endpointPos, endpointBe), now);
        } else {
            this.loopRegistry().forget(this.worldPosition.asLong());
            this.looped = false;
        }

        final long theirBest = NewAgeCompatHelper.bestWireConductivity();
        final int rating = CouplerRating.rating(PowerConnectorBE.baseTransferRate(), theirBest, BUFFER_FE);
        this.ratingFePerTick = CouplerRating.binding(rating, wired ? this.seamWireRate() : -1);
        if (this.mode() == PowerCouplerMode.PUSH) {
            this.accepts.observe(Math.max(this.exportFePerTick, this.theirAcceptance(endpointPos, endpointBe)), now);
            this.supplyFePerTick = 0;
        } else if (this.mode() == PowerCouplerMode.PULL) {
            this.supplyFePerTick = this.looped || this.managed.isForcedOff() ? 0 : this.theirSupply(endpointPos);
        } else {
            this.supplyFePerTick = 0;
        }
        if (wasLooped != this.looped) {
            this.setChanged();
            return true;
        }
        return false;
    }

    private void pruneGridLinks() {
        for (BlockPos link : new ArrayList<>(this.links)) {
            if (link.equals(this.seamLink) || !this.level.isLoaded(link)) {
                continue;
            }
            final boolean present = this.level.getBlockEntity(link) instanceof FeWireTerminal;
            final int misses = CouplerSpanWatch.next(this.gridMisses.getOrDefault(link.asLong(), 0), present);
            if (CouplerSpanWatch.drop(misses)) {
                this.letGo(link);
            } else if (misses == 0) {
                this.gridMisses.remove(link.asLong());
            } else {
                this.gridMisses.put(link.asLong(), misses);
            }
        }
    }

    private List<FeWireNetwork> ourCircuits() {
        final List<FeWireNetwork> out = new ArrayList<>(2);
        final BlockPos gridAt = this.worldPosition.relative(this.gridFace());
        if (this.level.isLoaded(gridAt) && this.level.getBlockEntity(gridAt) instanceof FeGridMember member
                && member.network() != null) {
            out.add(member.network());
        }
        for (BlockPos link : this.links) {
            if (link.equals(this.seamLink) || !this.level.isLoaded(link)) {
                continue;
            }
            if (this.level.getBlockEntity(link) instanceof FeGridMember member && member.network() != null
                    && !out.contains(member.network())) {
                out.add(member.network());
            }
        }
        return out;
    }

    private void reflood() {
        final Set<FeWireNetwork> all = new java.util.LinkedHashSet<>(this.circuits);
        all.addAll(this.ourCircuits());
        for (FeWireNetwork net : all) {
            net.markDirty();
        }
    }

    public void beforeTurn() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.reflood();
    }

    public void afterTurn() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.invalidateForeignSeam();
        this.surveyed = false;
        this.tellNeighbours();
        this.lastSyncTick = CouplerSyncGate.NEVER;
    }

    public void leverThrown() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final PowerCouplerMode mode = this.mode();
        if (this.seenMode != mode) {
            this.seenMode = mode;
            this.modeChangedAt = this.level.getGameTime();
            this.remember(mode);
            this.accepts.reset();
            this.reflood();
            this.tellNeighbours();
        }
        this.sendNow();
    }

    public PowerCouplerMode rememberedDirection() {
        return this.rememberedDirection;
    }

    private void remember(PowerCouplerMode mode) {
        if (mode.isDirection() && mode != this.rememberedDirection) {
            this.rememberedDirection = mode;
            this.setChanged();
        }
    }

    private CouplerLoop.Registry loopRegistry() {
        synchronized (LOOPS) {
            return LOOPS.computeIfAbsent(this.level, ignored -> new CouplerLoop.Registry());
        }
    }

    private CouplerLoop.Node loopNode(BlockPos endpointPos, @Nullable BlockEntity endpointBe) {
        final Set<Object> ours = new HashSet<>(this.circuits);
        final Set<Object> theirs = new HashSet<>(2);
        boolean onOurs = false;
        if (this.endpoint.present()) {
            theirs.add(endpointPos.asLong());
            if (this.theirSurvey != null) {
                theirs.add("cna:" + this.theirSurvey.networkId());
            }
            final Object foreignNet = foreignNetworkOf(endpointBe);
            if (foreignNet != null) {
                theirs.add(foreignNet);
            }
            for (FeWireNetwork net : this.circuits) {
                if (net.reachedPositions().contains(endpointPos.asLong())) {
                    onOurs = true;
                    break;
                }
            }
        }
        return new CouplerLoop.Node(this.mode() == PowerCouplerMode.PULL, ours, theirs, onOurs);
    }

    @Nullable
    private static Object foreignNetworkOf(@Nullable BlockEntity be) {
        if (be == null || CouplerForeign.isOurs(be.getClass().getName(), null)) {
            return null;
        }
        try {
            final java.lang.reflect.Method m = be.getClass().getMethod("getNetwork");
            final Object net = m.invoke(be);
            return net == null ? null : new IdentityKey(net);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private record IdentityKey(Object of) {
        @Override
        public boolean equals(Object o) {
            return o instanceof IdentityKey k && k.of == this.of;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.of);
        }
    }

    @Nullable
    private static String connectorMode(@Nullable BlockState state) {
        if (state == null) {
            return null;
        }
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if ("mode".equals(property.getName())) {
                return String.valueOf(state.getValue(property)).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private int theirAcceptance(BlockPos endpointPos, @Nullable BlockEntity endpointBe) {
        final int rating = this.ratingFePerTick;
        switch (this.endpoint) {
            case BOLTED_NEW_AGE -> {
                return this.theirInert ? 0
                        : Math.min(rating, NewAgeCompatHelper.acceptanceFePerTick(endpointBe, this.worldPosition));
            }
            case WIRED_NEW_AGE -> {
                final BlockPos support = NewAgeCompatHelper.supportingPosOf(endpointBe);
                final IEnergyStorage own = this.ownBlockOf(endpointBe);
                final int ownRoom = own == null ? 0 : Math.max(0, own.receiveEnergy(rating, true));
                final int netRoom = NewAgeCompatHelper.acceptanceFePerTick(endpointBe, support);
                return Math.min(rating, ownRoom + netRoom);
            }
            case NONE -> {
                return 0;
            }
            default -> {
                final IEnergyStorage theirs = this.endpoint.wired() ? this.farHandler(true)
                        : this.faceHandler();
                return theirs == null ? 0 : Math.min(rating, Math.max(0, theirs.receiveEnergy(rating, true)));
            }
        }
    }

    private int theirSupply(BlockPos endpointPos) {
        final int rating = this.ratingFePerTick;
        if (this.endpoint.isNewAge()) {
            final NewAgeCompatHelper.NetworkView view = this.theirSurvey;
            if (view == null) {
                return 0;
            }

            final BlockPos self = this.endpoint.wired() ? null : this.worldPosition;
            return CouplerPrice.newAgeSupply(rating, view.pulledGeneration(), view.demandExcluding(self));
        }
        if (!this.endpoint.present()) {
            return 0;
        }
        final IEnergyStorage theirs = this.endpoint.wired() ? this.farHandler(false) : this.faceHandler();
        final int could = theirs == null ? 0 : Math.max(0, theirs.extractEnergy(rating, true));
        return CouplerPrice.feedSupply(rating, Math.max(could, this.importFePerTick));
    }

    @Nullable
    private IEnergyStorage farHandler(boolean wantsReceive) {
        final BlockPos at = this.seamLink;
        if (this.level == null || at == null || !this.level.isLoaded(at)) {
            return null;
        }
        final BlockEntity endpointBe = this.level.getBlockEntity(at);

        final IEnergyStorage additionNode = CreateAdditionCompatHelper.seamMouth(endpointBe);
        if (additionNode != null) {
            return additionNode;
        }

        final boolean probe = !NewAgeCompatHelper.isElectricalConnector(endpointBe);
        IEnergyStorage handler = null;
        IEnergyStorage flagged = null;
        for (Direction side : SIDES) {
            final IEnergyStorage h = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, side);
            if (h == null) {
                continue;
            }
            if (handler == null) {
                handler = h;
            }
            if (flagged == null && (wantsReceive ? h.canReceive() : h.canExtract())) {
                flagged = h;
                if (!probe) {
                    break;
                }
            }

            if (probe && (wantsReceive ? h.receiveEnergy(this.ratingFePerTick, true)
                    : h.extractEnergy(this.ratingFePerTick, true)) > 0) {
                flagged = h;
                break;
            }
        }
        if (flagged != null) {
            handler = flagged;
        }
        if (handler == null) {
            return null;
        }

        if (NewAgeFeed.isNetworkMouth(endpointBe, handler)) {
            return NewAgeFeed.seamWireMouth(endpointBe, handler);
        }
        return ForeignTerminalNode.asMouth(this.level, at, handler);
    }

    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};

    @Nullable
    private IEnergyStorage faceHandler() {
        final BlockPos at = this.worldPosition.relative(this.foreignFace());
        if (this.level == null || !this.level.isLoaded(at)) {
            return null;
        }
        final IEnergyStorage sided = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at,
                this.foreignFace().getOpposite());
        return sided != null ? sided : this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, null);
    }

    @Nullable
    private IEnergyStorage ownBlockOf(@Nullable BlockEntity connector) {
        final BlockPos support = NewAgeCompatHelper.supportingPosOf(connector);
        if (this.level == null || support == null || !this.level.isLoaded(support)) {
            return null;
        }
        final BlockEntity machine = this.level.getBlockEntity(support);
        if (machine == null || CouplerForeign.isOurs(machine.getClass().getName(),
                this.level.getBlockState(support).getBlock().getClass().getName())) {
            return null;
        }
        final IEnergyStorage handler = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, support,
                NewAgeCompatHelper.facingOf(connector));
        return handler == null || NewAgeCompatHelper.isNetworkMouthHandler(handler) ? null : handler;
    }

    private void pumpSeamWire(long now) {
        if (!this.mode().isDirection() || this.looped || this.managed.isForcedOff() || this.level == null
                || this.seamLink == null || !this.level.isLoaded(this.seamLink) || !this.endpoint.wired()) {
            return;
        }
        final IEnergyStorage foreign = this.farHandler(this.mode() == PowerCouplerMode.PUSH);
        if (foreign == null) {
            return;
        }
        final int rate = this.ratingFePerTick;
        if (rate <= 0) {
            return;
        }
        if (this.mode() == PowerCouplerMode.PULL) {
            if (!foreign.canExtract()) {
                return;
            }
            final int room = this.buffer.seamReceive(rate, true);
            final int taken = room > 0 ? foreign.extractEnergy(room, false) : 0;
            if (taken > 0) {
                this.buffer.seamReceive(taken, false);
            }
            return;
        }

        final int offer = this.buffer.seamExtract(rate, true);
        if (offer <= 0) {
            return;
        }
        if (this.endpoint == CouplerEndpoint.WIRED_NEW_AGE) {
            final BlockEntity connector = this.level.getBlockEntity(this.seamLink);
            final IEnergyStorage own = this.ownBlockOf(connector);
            final int ownRoom = own == null ? 0 : Math.max(0, own.receiveEnergy(offer, true));
            final int netRoom = foreign.canReceive() ? Math.max(0, foreign.receiveEnergy(offer, true)) : 0;
            final CouplerPushPlan plan = CouplerPushPlan.of(offer, ownRoom, netRoom);
            if (plan.direct() > 0 && own != null) {
                final int moved = own.receiveEnergy(plan.direct(), false);
                this.buffer.seamExtract(Math.max(0, moved), false);
            }
            if (plan.network() > 0) {
                final int moved = foreign.receiveEnergy(plan.network(), false);
                this.buffer.seamExtract(Math.max(0, moved), false);
            }
            return;
        }
        if (!foreign.canReceive()) {
            return;
        }
        final int wanted = foreign.receiveEnergy(offer, true);
        if (wanted > 0) {
            final int moved = foreign.receiveEnergy(wanted, false);
            this.buffer.seamExtract(Math.max(0, moved), false);
        }
    }

    private void pumpBolted() {
        if (!this.mode().isDirection() || this.looped || this.managed.isForcedOff() || this.level == null
                || this.endpoint.wired()) {
            return;
        }
        if (this.endpoint == CouplerEndpoint.BOLTED_CREATE_ADDITION) {
            final BlockPos at = this.worldPosition.relative(this.foreignFace());
            if (CouplerForeign.createAdditionDrives(connectorMode(this.level.getBlockState(at)))) {
                return;
            }
        } else if (this.endpoint != CouplerEndpoint.BOLTED_GENERIC) {
            return;
        }
        final IEnergyStorage theirs = this.faceHandler();
        if (theirs == null) {
            return;
        }
        final int rate = this.ratingFePerTick;
        if (this.mode() == PowerCouplerMode.PULL) {
            if (!theirs.canExtract()) {
                return;
            }
            final int room = this.buffer.seamReceive(rate, true);
            final int taken = room > 0 ? theirs.extractEnergy(room, false) : 0;
            if (taken > 0) {
                this.buffer.seamReceive(taken, false);
            }
            return;
        }
        if (!theirs.canReceive()) {
            return;
        }
        final int offer = this.buffer.seamExtract(rate, true);
        final int wanted = offer > 0 ? theirs.receiveEnergy(offer, true) : 0;
        if (wanted > 0) {
            final int moved = theirs.receiveEnergy(wanted, false);
            this.buffer.seamExtract(Math.max(0, moved), false);
        }
    }

    private void pumpThrough(long now) {
        if (this.looped || this.managed.isForcedOff() || this.level == null) {
            return;
        }
        final Direction face = this.gridFace();
        final BlockPos at = this.worldPosition.relative(face);
        if (!this.level.isLoaded(at)) {
            return;
        }
        final BlockEntity there = this.level.getBlockEntity(at);

        if (there instanceof FeGridMember || PowerMeterBE.isWiring(there)) {
            return;
        }
        final IEnergyStorage neighbour =
                this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, face.getOpposite());
        if (neighbour == null) {
            return;
        }
        final int rate = this.ratingFePerTick;
        if (this.canGridGive() && neighbour.canReceive()) {
            final int offer = this.buffer.gridExtract(rate, true, rate, now);
            final int wanted = offer > 0 ? neighbour.receiveEnergy(offer, true) : 0;
            if (wanted > 0) {
                final int moved = neighbour.receiveEnergy(wanted, false);
                this.buffer.gridExtract(Math.max(0, moved), false, rate, now);
            }
        } else if (this.canGridReceive() && neighbour.canExtract()) {
            final int room = this.buffer.gridReceive(rate, true, rate, now);
            final int taken = room > 0 ? neighbour.extractEnergy(room, false) : 0;
            if (taken > 0) {
                this.buffer.gridReceive(taken, false, rate, now);
            }
        }
    }

    private void maybeSync(long now) {
        final boolean event = this.syncedFault != this.fault
                || this.syncedLooped != this.looped
                || this.syncedModeChangedAt != this.modeChangedAt
                || this.syncedEndpoint != this.endpoint
                || this.syncedGridFace != this.gridFaceLive
                || this.syncedRating != this.ratingFePerTick
                || this.syncedLinks != this.links.size();
        if (!event) {
            if (!CouplerSyncGate.due(now, this.lastSyncTick, SYNC_INTERVAL_TICKS)) {
                return;
            }
            final boolean moved = CouplerSyncGate.moved(this.importFePerTick, this.syncedImport, SYNC_THRESHOLD_FE, 0)
                    || CouplerSyncGate.moved(this.exportFePerTick, this.syncedExport, SYNC_THRESHOLD_FE, 0)
                    || CouplerSyncGate.moved(this.buffer.energy(), this.syncedStored, SYNC_THRESHOLD_STORE, BUFFER_FE);
            if (!moved && this.lastSyncTick != CouplerSyncGate.NEVER) {
                return;
            }
        }
        this.sendNow();
    }

    private void sendNow() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.syncedImport = this.importFePerTick;
        this.syncedExport = this.exportFePerTick;
        this.syncedStored = this.buffer.energy();
        this.syncedLooped = this.looped;
        this.syncedFault = this.fault;
        this.syncedEndpoint = this.endpoint;
        this.syncedGridFace = this.gridFaceLive;
        this.syncedRating = this.ratingFePerTick;
        this.syncedModeChangedAt = this.modeChangedAt;
        this.syncedLinks = this.links.size();
        this.lastSyncTick = this.level.getGameTime();
        this.sendData();
    }

    private boolean canGridGive() {
        return this.mode().gridGives() && !this.looped && !this.managed.isForcedOff();
    }

    private boolean canGridReceive() {
        return this.mode().gridReceives() && !this.looped && !this.managed.isSuppressed();
    }

    private boolean seamReceives() {
        return this.mode().seamReceives() && !this.looped && !this.managed.isForcedOff();
    }

    private boolean seamGives() {
        return this.mode().seamGives() && !this.looped && !this.managed.isForcedOff();
    }

    private long now() {
        return this.level == null ? 0L : this.level.getGameTime();
    }

    private final class NewAgePort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return UniversalPowerCouplerBE.this.buffer.seamReceive(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return UniversalPowerCouplerBE.this.buffer.seamExtract(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return UniversalPowerCouplerBE.this.buffer.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE;
        }

        @Override
        public boolean canExtract() {
            return UniversalPowerCouplerBE.this.seamGives();
        }

        @Override
        public boolean canReceive() {
            return UniversalPowerCouplerBE.this.seamReceives();
        }
    }

    private final class StrictPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return UniversalPowerCouplerBE.this.seamReceives()
                    ? UniversalPowerCouplerBE.this.buffer.seamReceive(maxReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return UniversalPowerCouplerBE.this.seamGives()
                    ? UniversalPowerCouplerBE.this.buffer.seamExtract(maxExtract, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return UniversalPowerCouplerBE.this.buffer.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE;
        }

        @Override
        public boolean canExtract() {
            return UniversalPowerCouplerBE.this.seamGives();
        }

        @Override
        public boolean canReceive() {
            return UniversalPowerCouplerBE.this.seamReceives();
        }
    }

    private final class GridPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!UniversalPowerCouplerBE.this.canGridReceive()) {
                return 0;
            }
            final UniversalPowerCouplerBE be = UniversalPowerCouplerBE.this;
            return be.buffer.gridReceive(maxReceive, simulate, be.ratingFePerTick, be.now());
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!UniversalPowerCouplerBE.this.canGridGive()) {
                return 0;
            }
            final UniversalPowerCouplerBE be = UniversalPowerCouplerBE.this;
            return be.buffer.gridExtract(maxExtract, simulate, be.ratingFePerTick, be.now());
        }

        @Override
        public int getEnergyStored() {
            return UniversalPowerCouplerBE.this.buffer.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE;
        }

        @Override
        public boolean canExtract() {
            return UniversalPowerCouplerBE.this.canGridGive();
        }

        @Override
        public boolean canReceive() {
            return UniversalPowerCouplerBE.this.canGridReceive();
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        if (!this.mode().gridReceives()) {
            return 0;
        }
        return CouplerPrice.pushPrice(this.ratingFePerTick, this.accepts.value(), this.managed.isForcedOff(),
                this.looped);
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.mode().gridReceives() ? this.exportFePerTick : 0;
    }

    @Override
    public FePriority managedPriority() {
        return this.managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        this.managed.setPriority(tier);
        this.setChanged();
    }

    @Override
    public FeLock managedLock() {
        return this.managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        this.managed.setLock(lock);
        this.setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (this.managed.isShed() == shed) {
            return;
        }
        this.managed.setShed(shed);

        this.setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return this.managed.isShed();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (this.level != null && !this.level.isClientSide()) {
            this.loopRegistry().forget(this.worldPosition.asLong());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        final int fe = this.buffer.energy();
        if (fe > 0) {
            builder.set(ModDataComponents.STORED_FE.get(), fe);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        final Integer fe = input.get(ModDataComponents.STORED_FE.get());
        if (fe != null && fe > 0) {
            this.buffer.setEnergy(fe);
        }
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Priority", FePriority.DEFAULT.ordinal());

        tag.putString("Direction", this.rememberedDirection.getSerializedName());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putInt("Energy", this.buffer.energy());
        tag.putInt("Priority", this.managed.priorityOrdinal());
        tag.putInt("Lock", this.managed.lockOrdinal());
        tag.putBoolean("Looped", this.looped);
        tag.putInt("ImportFe", this.importFePerTick);
        tag.putInt("ExportFe", this.exportFePerTick);
        tag.putLong("ModeChangedAt", this.modeChangedAt);
        tag.putString("Direction", this.rememberedDirection.getSerializedName());
        tag.putInt("Fault", this.fault.ordinal());
        tag.putLong("FaultAt", this.faultChangedAt);
        tag.putInt("Endpoint", this.endpoint.ordinal());
        tag.putBoolean("GridFace", this.gridFaceLive);
        tag.putInt("Rating", this.ratingFePerTick);
        tag.putInt("TheirNodes", this.theirNodes);
        tag.putInt("TheirSources", this.theirSources);
        tag.putInt("TheirGen", this.theirGeneration);
        tag.putInt("TheirWire", this.theirWire);
        tag.putInt("TheirPull", this.theirPullSources);
        final net.minecraft.nbt.ListTag wires = new net.minecraft.nbt.ListTag();
        for (BlockPos link : this.links) {
            final CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", link.asLong());
            entry.putInt("Tier", this.tierOf(link).ordinal());

            entry.putBoolean("Seam", link.equals(this.seamLink));
            wires.add(entry);
        }
        tag.put("SeamWires", wires);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.buffer.setEnergy(tag.getInt("Energy"));

        this.managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        this.looped = tag.getBoolean("Looped");
        this.importFePerTick = tag.getInt("ImportFe");
        this.exportFePerTick = tag.getInt("ExportFe");
        this.modeChangedAt = tag.contains("ModeChangedAt") ? tag.getLong("ModeChangedAt") : CouplerSyncGate.NEVER;

        this.rememberedDirection = PowerCouplerMode.PUSH.getSerializedName().equals(tag.getString("Direction"))
                ? PowerCouplerMode.PUSH : PowerCouplerMode.PULL;
        this.fault = CouplerFault.byOrdinal(tag.getInt("Fault"));
        this.faultChangedAt = tag.contains("FaultAt") ? tag.getLong("FaultAt") : CouplerSyncGate.NEVER;
        this.endpoint = CouplerEndpoint.byOrdinal(tag.getInt("Endpoint"));
        this.gridFaceLive = tag.getBoolean("GridFace");
        this.ratingFePerTick = tag.contains("Rating") ? Math.max(1, tag.getInt("Rating")) : RATE_FE_PER_TICK;
        this.theirNodes = tag.contains("TheirNodes") ? tag.getInt("TheirNodes") : -1;
        this.theirSources = tag.contains("TheirSources") ? tag.getInt("TheirSources") : -1;
        this.theirGeneration = tag.contains("TheirGen") ? tag.getInt("TheirGen") : -1;
        this.theirWire = tag.contains("TheirWire") ? tag.getInt("TheirWire") : -1;
        this.theirPullSources = tag.contains("TheirPull") ? tag.getInt("TheirPull") : -1;
        this.links.clear();
        this.linkTiers.clear();
        this.seamLink = null;
        final net.minecraft.nbt.ListTag wires = tag.getList("SeamWires", net.minecraft.nbt.Tag.TAG_COMPOUND);
        final WireTier[] grades = WireTier.values();
        for (int i = 0; i < wires.size(); i++) {
            final CompoundTag entry = wires.getCompound(i);
            final BlockPos at = BlockPos.of(entry.getLong("Pos"));
            final int grade = entry.getInt("Tier");
            this.links.add(at);
            this.linkTiers.put(at.asLong(), grade >= 0 && grade < grades.length ? grades[grade] : WireTier.COPPER);

            if (entry.getBoolean("Seam") || (!entry.contains("Seam") && wires.size() == 1)) {
                this.seamLink = at;
            }
        }
        if (!clientPacket) {
            this.faceVerdictValid = false;
            this.surveyed = false;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.universal_power_coupler")));
        final PowerCouplerMode mode = this.mode();
        tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.mode." + mode.getSerializedName()),
                mode.isDirection() ? ChatFormatting.AQUA : ChatFormatting.GRAY));

        if (this.endpoint.present()) {
            tooltip.add(pad(Component.translatable(
                    "create_stats.universal_power_coupler.endpoint." + this.endpoint.key()), ChatFormatting.DARK_GRAY));
            final BlockPos wired = this.seamLink;
            if (wired != null && this.level != null) {
                tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.wired_seam",
                        this.level.getBlockState(wired).getBlock().getName(),
                        Mth.ceil(Math.sqrt(this.worldPosition.distSqr(wired)))), ChatFormatting.DARK_GRAY));
            }
        }

        if (this.fault.isFault()) {
            tooltip.add(pad(Component.translatable(this.fault.langKey()),
                    this.fault.blocksFlow() ? ChatFormatting.RED : ChatFormatting.GOLD));
        }

        if (!this.fault.blocksFlow()) {
            final int crossing = this.crossingFePerTick();
            tooltip.add(pad(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE/t", crossing,
                    this.ratingFePerTick)), crossing > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        }

        if (this.gridLinkCount() > 0) {
            tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.grid_wires",
                    this.gridLinkCount()), ChatFormatting.DARK_GRAY));
        }

        if (isPlayerSneaking) {
            tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.store",
                    String.format(Locale.ROOT, "%,d", this.buffer.energy()),
                    String.format(Locale.ROOT, "%,d", BUFFER_FE)), ChatFormatting.AQUA));
            if (this.theirNodes >= 0) {
                tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.their_network",
                        this.theirNodes, this.theirSources,
                        String.format(Locale.ROOT, "%,d", Math.max(0, this.theirGeneration))),
                        ChatFormatting.DARK_GRAY));
                tooltip.add(pad(Component.translatable("create_stats.universal_power_coupler.their_wire",
                        String.format(Locale.ROOT, "%,d", Math.max(0, this.theirWire))), ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(FeGoggle.more());
        }
        return true;
    }

    private static Component pad(Component text, ChatFormatting colour) {
        return Component.literal("    ").append(text.copy().withStyle(colour));
    }
}
