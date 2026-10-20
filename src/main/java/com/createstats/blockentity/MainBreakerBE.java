package com.createstats.blockentity;

import com.createstats.block.MainBreakerBlock;
import com.createstats.power.BreakerTrip;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeRates;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.LimiterBudget;
import com.createstats.power.MainBreakerStuds;
import com.createstats.power.WireTier;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

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

// two circuits, this block is the only way across. a member would be one grid with a decorative handle
public class MainBreakerBE extends BlockEntity
        implements FeStudPanel, IHaveGoggleInformation, com.createstats.power.FeSupply,
        com.createstats.power.FeForecastingSource {
    private static final int MIN_BUFFER_FE = 16_384;

    private static final int CREATIVE_RATE = Integer.MAX_VALUE / 2;

    private static final int MAX_LINKS_PER_STUD = 3;

    private static final int SLOW_BEAT_TICKS = 20;

    private static final int WINDOW_TICKS = 20;

    private static final int SYNC_INTERVAL = 10;

    private static final int HEARTBEAT_TICKS = 40;

    public record StudLink(BlockPos to, WireTier tier) {
    }

    private final List<List<StudLink>> studLinks = newStudLinks();

    private static List<List<StudLink>> newStudLinks() {
        final List<List<StudLink>> lists = new ArrayList<>(MainBreakerStuds.COUNT);
        for (int i = 0; i < MainBreakerStuds.COUNT; i++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    private final LimiterBudget budget = new LimiterBudget(MIN_BUFFER_FE);

    private final IEnergyStorage inputPort = new InputPort();
    private final IEnergyStorage outputPort = new OutputPort();

    private final BreakerTrip thermal = new BreakerTrip();

    private boolean looped;

    private boolean tripPending;

    private int lastComparatorLevel = -1;

    private final int[] outWindow = new int[WINDOW_TICKS];

    private final int[] inWindow = new int[WINDOW_TICKS];
    private int windowHead;
    private int windowFilled;

    private int rateFePerTick;

    private int inRateFePerTick;
    private long totalFe;

    private int outDemandFe;

    private float overload;

    private int syncedRating;
    private int syncedRate = Integer.MIN_VALUE;
    private int syncedInRate;

    private float syncedHeat;
    private float syncedOverload;
    private boolean syncedLooped;
    private long lastReadingSyncTick = Long.MIN_VALUE;

    public MainBreakerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isCreative() {
        return getBlockState().getBlock() instanceof MainBreakerBlock block && block.isCreative();
    }

    public boolean isClosed() {
        final BlockState state = getBlockState();
        return state.getBlock() instanceof MainBreakerBlock
                && state.getValue(MainBreakerBlock.POWERED)
                && !state.getValue(MainBreakerBlock.TRIPPED);
    }

    public boolean isTripped() {
        final BlockState state = getBlockState();
        return state.getBlock() instanceof MainBreakerBlock && state.getValue(MainBreakerBlock.TRIPPED);
    }

    public boolean isLocked() {
        final BlockState state = getBlockState();
        return state.getBlock() instanceof MainBreakerBlock && state.getValue(MainBreakerBlock.LOCKED);
    }

    public boolean isPassthrough() {
        final BlockState state = getBlockState();
        return state.getBlock() instanceof MainBreakerBlock
                && state.getValue(MainBreakerBlock.PASSTHROUGH);
    }

    public boolean isMetering() {
        return isClosed() && !isPassthrough();
    }

    public boolean toggleMode() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide() || !(state.getBlock() instanceof MainBreakerBlock)) {
            return false;
        }
        final boolean now = !isPassthrough();

        thermal.reset();
        overload = 0.0f;
        level.setBlock(worldPosition, state.setValue(MainBreakerBlock.PASSTHROUGH, now),
                Block.UPDATE_ALL);
        onContactsMoved();
        return now;
    }

    public boolean isLooped() {
        return level != null && level.isClientSide() ? syncedLooped : looped;
    }

    public enum Throw {
        REFUSED,

        RESET,

        CLOSE,

        OPEN
    }

    public Throw nextThrow() {
        if (isLocked()) {
            return Throw.REFUSED;
        }
        if (isTripped()) {
            return Throw.RESET;
        }
        return isClosed() ? Throw.OPEN : Throw.CLOSE;
    }

    public Throw toggle() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide() || !(state.getBlock() instanceof MainBreakerBlock)) {
            return Throw.REFUSED;
        }
        final Throw action = nextThrow();
        switch (action) {
            case REFUSED -> {
                return action;
            }
            case RESET -> {
                thermal.reset();
                level.setBlock(worldPosition, state.setValue(MainBreakerBlock.POWERED, false)
                        .setValue(MainBreakerBlock.TRIPPED, false), Block.UPDATE_ALL);
            }
            case CLOSE -> {
                thermal.reset();
                level.setBlock(worldPosition, state.setValue(MainBreakerBlock.POWERED, true)
                        .setValue(MainBreakerBlock.TRIPPED, false), Block.UPDATE_ALL);
            }
            case OPEN -> level.setBlock(worldPosition,
                    state.setValue(MainBreakerBlock.POWERED, false), Block.UPDATE_ALL);
        }
        onContactsMoved();
        return action;
    }

    public boolean toggleLock() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide() || !(state.getBlock() instanceof MainBreakerBlock)) {
            return false;
        }
        final boolean now = !isLocked();

        level.setBlock(worldPosition, state.setValue(MainBreakerBlock.LOCKED, now), Block.UPDATE_ALL);
        setChanged();
        return now;
    }

    private void trip() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide() || !(state.getBlock() instanceof MainBreakerBlock)) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(MainBreakerBlock.POWERED, false)
                .setValue(MainBreakerBlock.TRIPPED, true), Block.UPDATE_ALL);
        MainBreakerBlock.tripEffects(level, worldPosition, state.getValue(MainBreakerBlock.FACING));
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            com.createstats.advancement.ModAdvancements.awardNearby(server, worldPosition,
                    com.createstats.advancement.ModAdvancements.TRIP_EARSHOT,
                    com.createstats.advancement.ModAdvancements.BREAKER_TRIP);
        }
        onContactsMoved();
    }

    // dirty the far end of every wire. otherwise the protected side kept running on the old discovery
    private void onContactsMoved() {
        invalidateLinkedTerminals();

        looped = detectLoop();

        MainBreakerBlock.updateNeighbours(level, worldPosition, getBlockState());
        setChanged();
        forceSync();
    }

    private void invalidateLinkedTerminals() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (List<StudLink> links : studLinks) {
            for (StudLink link : links) {
                if (level.isLoaded(link.to())
                        && level.getBlockEntity(link.to()) instanceof FeGridMember member) {
                    member.invalidateNetwork();
                }
            }
        }
    }

    public IEnergyStorage inputHandle() {
        return inputPort;
    }

    public IEnergyStorage outputHandle() {
        return outputPort;
    }

    public IEnergyStorage portFor(int stud) {
        return stud == MainBreakerStuds.Stud.OUTPUT.index() ? outputPort : inputPort;
    }

    public int ratingFePerTick() {
        if (isCreative()) {
            return CREATIVE_RATE;
        }
        if (level != null && level.isClientSide()) {
            return syncedRating;
        }
        return Math.min(studRate(MainBreakerStuds.Stud.INPUT.index()),
                studRate(MainBreakerStuds.Stud.OUTPUT.index()));
    }

    @Nullable
    public MainBreakerStuds.Stud bindingTerminal() {
        if (isCreative()) {
            return null;
        }
        final int in = studRate(MainBreakerStuds.Stud.INPUT.index());
        final int out = studRate(MainBreakerStuds.Stud.OUTPUT.index());
        if (in == out) {
            return null;
        }
        return in < out ? MainBreakerStuds.Stud.INPUT : MainBreakerStuds.Stud.OUTPUT;
    }

    public int studRate(int stud) {
        if (level != null && level.isClientSide()) {
            int total = 0;
            for (StudLink link : studLinks(stud)) {
                total = FeRates.plus(total, FeRates.wire(link.tier()));
            }
            return total;
        }
        int total = 0;
        for (StudLink link : studLinks(stud)) {
            total = FeRates.plus(total, FeRates.wire(link.tier()));
        }
        return total;
    }

    public int getRateFePerTick() {
        return level != null && level.isClientSide() ? syncedRate() : rateFePerTick;
    }

    @Override
    public int potentialFePerTick() {
        return isClosed() ? Math.max(0, Math.min(getRateFePerTick(), ratingFePerTick())) : 0;
    }

    private final com.createstats.power.FeForecastWalk.Memo forecastMemo =
            new com.createstats.power.FeForecastWalk.Memo();

    @Override
    public int potentialFePerTickIn(int ticks) {
        final int passing = this.potentialFePerTick();
        if (ticks <= 0 || passing <= 0) {
            return passing;
        }
        final com.createstats.power.FeForecastRelay.Upstream up = this.forecastBehind();
        return up.isPresent()
                ? com.createstats.power.FeForecastRelay.through(passing, up.now(), up.at(ticks))
                : passing;
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public int relayedWeatherShareFePerTick() {
        final int passing = this.potentialFePerTick();
        if (passing <= 0) {
            return 0;
        }
        final com.createstats.power.FeForecastRelay.Upstream up = this.forecastBehind();
        return com.createstats.power.FeForecastRelay.weatherShareThrough(passing, up.now(), up.sensitiveNow());
    }

    private com.createstats.power.FeForecastRelay.Upstream forecastBehind() {
        return com.createstats.power.FeForecastWalk.behind(this.level, this.forecastMemo, this,
                () -> this.networksOn(MainBreakerStuds.Stud.INPUT.index()),
                () -> java.util.List.<BlockEntity>of());
    }

    public int getIntakeFePerTick() {
        return level != null && level.isClientSide() ? syncedInRate : inRateFePerTick;
    }

    private int syncedRate() {
        return Math.max(0, syncedRate);
    }

    public long totalFe() {
        return totalFe;
    }

    public int inTransitFe() {
        return budget.energy();
    }

    public int transitCapacityFe() {
        return Math.max(MIN_BUFFER_FE, ratingFePerTick());
    }

    public float thermalHeat() {
        if (level != null && level.isClientSide()) {
            return syncedHeat;
        }
        return thermal.heat(tripTicks());
    }

    public float overload() {
        return level != null && level.isClientSide() ? syncedOverload : overload;
    }

    private int tripTicks() {
        if (isCreative()) {
            return 0;
        }
        try {
            return com.createstats.ModConfiguration.COMMON.mainBreakerTripSeconds.get() * 20;
        } catch (RuntimeException ignored) {
            return 100;
        }
    }

    private int intakeRoom(int wanted) {
        if (looped || isPassthrough() || isCreative() || !isClosed() || wanted <= 0
                || level == null || level.isClientSide()) {
            return 0;
        }
        rollTick(level.getGameTime());
        return budget.intakeRoom(wanted, ratingFePerTick());
    }

    private void bookIntake(int fe) {
        if (fe > 0 && budget.bookIntake(fe) > 0) {
            setChanged();
        }
    }

    private int outflow(int wanted) {
        if (looped || isPassthrough() || !isClosed() || wanted <= 0
                || level == null || level.isClientSide()) {
            return 0;
        }
        rollTick(level.getGameTime());
        final int cap = ratingFePerTick();
        return isCreative()
                ? Math.max(0, Math.min(wanted, cap - budget.given()))
                : budget.outflowAvailable(wanted, cap);
    }

    private void bookOutflow(int fe) {
        if (fe <= 0) {
            return;
        }
        final int booked = isCreative()
                ? budget.bookPassThrough(fe, ratingFePerTick())
                : budget.bookOutflow(fe);
        if (booked > 0) {
            setChanged();
        }
    }

    private void noteDemand(int fe) {
        if (fe > 0) {
            outDemandFe = FeRates.plus(outDemandFe, fe);
        }
    }

    private final class InputPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = intakeRoom(maxReceive);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                budget.noteClipped(budget.room(maxReceive), room);
                bookIntake(room);
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!isCreative()) {
                return 0;
            }
            final int give = outflow(maxExtract);
            if (give > 0 && !simulate) {
                bookOutflow(give);
            }
            return give;
        }

        @Override
        public int getEnergyStored() {
            return isCreative() ? CREATIVE_RATE : budget.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return isCreative() ? CREATIVE_RATE : budget.capacity();
        }

        @Override
        public boolean canExtract() {
            return isCreative() && isMetering() && !looped;
        }

        @Override
        public boolean canReceive() {
            return !isCreative() && isMetering() && !looped && ratingFePerTick() > 0;
        }
    }

    private final class OutputPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int give = outflow(maxExtract);
            if (!simulate) {
                if (isCreative() || budget.energy() > 0) {
                    noteDemand(maxExtract);
                }
                final int uncapped = isCreative() ? maxExtract : budget.stored(maxExtract);
                budget.noteClipped(uncapped, give);
                bookOutflow(give);
            }
            return give;
        }

        @Override
        public int getEnergyStored() {
            return isCreative() ? CREATIVE_RATE : budget.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return isCreative() ? CREATIVE_RATE : budget.capacity();
        }

        @Override
        public boolean canExtract() {
            return isMetering() && !looped && ratingFePerTick() > 0;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    // ---------------------------- Tick ----------------------------

    public void serverTick() {
        final Level lvl = level;
        if (lvl == null || lvl.isClientSide()) {
            return;
        }

        rollTick(lvl.getGameTime());
        if (tripPending) {
            tripPending = false;
            trip();
        }

        if ((lvl.getGameTime() + Math.floorMod(worldPosition.hashCode(), SLOW_BEAT_TICKS))
                % SLOW_BEAT_TICKS == 0) {
            looped = detectLoop();
            pruneStudLinks();
        }
        updateComparator();
        maybeSyncReading();
    }

    private void rollTick(long now) {
        budget.setCapacity(transitCapacityFe());

        final int takenBefore = budget.taken();
        final int demandBefore = outDemandFe;
        final LimiterBudget.Tick closed = budget.roll(now);
        if (closed == null) {
            return;
        }
        outDemandFe = 0;
        closeWindow(closed.moved(), takenBefore);

        final int rating = ratingFePerTick();
        overload = isMetering() && rating > 0 ? demandBefore / (float) rating : 0.0f;
        if (thermal.note(overload, tripTicks())) {
            tripPending = true;
        }
    }

    private void closeWindow(int out, int in) {
        outWindow[windowHead] = out;
        inWindow[windowHead] = in;
        windowHead = (windowHead + 1) % WINDOW_TICKS;
        if (windowFilled < WINDOW_TICKS) {
            windowFilled++;
        }
        long outSum = 0;
        long inSum = 0;
        for (int i = 0; i < windowFilled; i++) {
            outSum += outWindow[i];
            inSum += inWindow[i];
        }
        rateFePerTick = windowFilled == 0 ? 0 : (int) (outSum / windowFilled);
        inRateFePerTick = windowFilled == 0 ? 0 : (int) (inSum / windowFilled);
        totalFe += out;
    }

    private void updateComparator() {
        if (level == null) {
            return;
        }
        final int next = getBlockState().getAnalogOutputSignal(level, worldPosition);
        if (next != lastComparatorLevel) {
            lastComparatorLevel = next;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private boolean detectLoop() {
        if (isPassthrough()) {
            return false;
        }
        final List<FeWireNetwork> in = networksOn(MainBreakerStuds.Stud.INPUT.index());
        if (in.isEmpty()) {
            return false;
        }
        for (FeWireNetwork far : networksOn(MainBreakerStuds.Stud.OUTPUT.index())) {
            for (FeWireNetwork near : in) {
                if (near == far) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<FeWireNetwork> networksOn(int stud) {
        final List<FeWireNetwork> out = new ArrayList<>(MAX_LINKS_PER_STUD);
        if (level == null) {
            return out;
        }
        for (StudLink link : studLinks(stud)) {
            if (!level.isLoaded(link.to())) {
                continue;
            }
            if (level.getBlockEntity(link.to()) instanceof FeGridMember member
                    && member.network() != null) {
                out.add(member.network());
            }
        }
        return out;
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    public List<StudLink> studLinks(int stud) {
        return MainBreakerStuds.isStudIndex(stud) ? studLinks.get(stud) : List.of();
    }

    public int wiresOnStuds() {
        int total = 0;
        for (List<StudLink> links : studLinks) {
            total += links.size();
        }
        return total;
    }

    @Override
    public int studLinkCount(int stud) {
        return studLinks(stud).size();
    }

    @Override
    public int maxLinksPerStud() {
        return MAX_LINKS_PER_STUD;
    }

    @Override
    public boolean isLinkedToConnector(BlockPos connector) {
        return studOf(connector) >= 0;
    }

    public int studOf(BlockPos far) {
        for (int stud = 0; stud < MainBreakerStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                if (link.to().equals(far)) {
                    return stud;
                }
            }
        }
        return -1;
    }

    @Override
    public void linkStud(int stud, FeWireTerminal terminal, WireTier tier) {
        if (level == null || level.isClientSide() || !MainBreakerStuds.isStudIndex(stud)) {
            return;
        }
        final WireTier grade = tier == null ? WireTier.COPPER : tier;
        studLinks.get(stud).add(new StudLink(terminal.terminalPos().immutable(), grade));
        terminal.addLink(worldPosition, grade);
        syncStudLinks();
    }

    @Override
    public int dropStudWires(int stud) {
        if (level == null || level.isClientSide() || !MainBreakerStuds.isStudIndex(stud)) {
            return 0;
        }
        final List<StudLink> links = studLinks.get(stud);
        if (links.isEmpty()) {
            return 0;
        }
        final int removed = links.size();
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (StudLink link : new ArrayList<>(links)) {
            refund.merge(link.tier(), 1, Integer::sum);
            if (level.getBlockEntity(link.to()) instanceof FeWireTerminal terminal) {
                terminal.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        for (Map.Entry<WireTier, Integer> entry : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(com.createstats.registry.ModItems.wireItem(entry.getKey()),
                            entry.getValue()));
        }
        syncStudLinks();
        return removed;
    }

    public void dropAllStudWires() {
        for (int stud = 0; stud < MainBreakerStuds.COUNT; stud++) {
            dropStudWires(stud);
        }
    }

    @Override
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
                    && !(level.getBlockEntity(link.to()) instanceof FeWireTerminal terminal
                    && terminal.links().contains(worldPosition)));
        }
        if (changed) {
            syncStudLinks();
        }
    }

    private void syncStudLinks() {
        setChanged();

        invalidateLinkedTerminals();
        looped = detectLoop();
        forceSync();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(isCreative() ? "Creative Main Breaker" : "Main Breaker"));

        if (isLooped()) {
            tooltip.add(FeGoggle.bad("Both terminals on one circuit"));
            tooltip.add(FeGoggle.dim("A breaker separates two circuits; this one bridges one to itself."));
            return true;
        }

        final int rating = ratingFePerTick();
        final boolean bare = !isCreative() && rating <= 0;

        if (isTripped()) {
            tooltip.add(FeGoggle.bad("Tripped on overload"));
            tooltip.add(FeGoggle.dim(isLocked()
                    ? "Take the padlock off, then throw the handle twice to reset."
                    : "Throw once to reset, once more to close. Heavier wire raises the rating."));
        } else if (!isClosed()) {
            tooltip.add(FeGoggle.warn("Open, circuit broken"));
            if (isPassthrough()) {
                tooltip.add(FeGoggle.dim("Set to passthrough: closed, it will not meter or trip."));
            }
        } else if (isPassthrough()) {
            tooltip.add(Component.literal("    Closed, passing straight through")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(FeGoggle.dim("Both terminals are one circuit. Nothing rated, metered or tripped."));
        } else if (bare) {
            tooltip.add(FeGoggle.warn(bareTerminalText()));
        } else {
            tooltip.add(FeGoggle.ok(isCreative()
                    ? "Closed, supplying both terminals" : "Closed, contacts made"));

            tooltip.add(isCreative()
                    ? FeGoggle.output(getRateFePerTick())
                    : FeGoggle.carrying(getRateFePerTick(), rating));
            appendOverload(tooltip, rating);
        }

        if (isLocked()) {
            tooltip.add(FeGoggle.warn("Padlocked " + (isClosed() ? "closed" : "open")));
        }

        if (!isPlayerSneaking) {
            tooltip.add(FeGoggle.more());
            return true;
        }
        appendDetail(tooltip, rating, bare);
        return true;
    }

    private String bareTerminalText() {
        final boolean in = studLinkCount(MainBreakerStuds.Stud.INPUT.index()) <= 0;
        final boolean out = studLinkCount(MainBreakerStuds.Stud.OUTPUT.index()) <= 0;
        if (in && out) {
            return "Closed, no wire on either terminal";
        }
        return "Closed, nothing wired to " + (in ? "INPUT" : "OUTPUT");
    }

    private void appendOverload(List<Component> tooltip, int rating) {
        final float over = overload();
        final float heat = thermalHeat();
        if (over >= BreakerTrip.MAGNETIC_MULTIPLE) {
            tooltip.add(FeGoggle.bad(String.format(Locale.ROOT,
                    "Fault: %.1fx the rating, tripping now", over)));
            return;
        }
        if (heat <= 0.0f && over <= 1.0f) {
            if (over > 0.0f && getRateFePerTick() >= rating && rating > 0) {
                tooltip.add(FeGoggle.warn("At its rating: the circuit wants everything it passes"));
            }
            return;
        }
        final int ticks = BreakerTrip.ticksToTrip(over, tripTicksForDisplay(),
                heat * tripTicksForDisplay());
        if (over > 1.0f && ticks > 0) {
            tooltip.add(FeGoggle.warn(String.format(Locale.ROOT,
                    "Overloaded %.1fx, trips in %s", over, FeGoggle.time(ticks))));
        } else if (over > 1.0f) {
            tooltip.add(FeGoggle.warn(String.format(Locale.ROOT, "Overloaded %.1fx", over)));
        } else {
            tooltip.add(FeGoggle.dim(Math.round(heat * 100.0f) + "% of the element's heat, cooling"));
        }
    }

    private int tripTicksForDisplay() {
        try {
            return Math.max(0, com.createstats.ModConfiguration.COMMON.mainBreakerTripSeconds.get()) * 20;
        } catch (RuntimeException ignored) {
            return 100;
        }
    }

    private void appendDetail(List<Component> tooltip, int rating, boolean bare) {
        if (isPassthrough()) {
            tooltip.add(Component.literal("    Mode: PASSTHROUGH").withStyle(ChatFormatting.AQUA));
            tooltip.add(spanLine(MainBreakerStuds.Stud.INPUT));
            tooltip.add(spanLine(MainBreakerStuds.Stud.OUTPUT));
            tooltip.add(FeGoggle.dim("Reaches every machine on the protected side and stops at the"));
            tooltip.add(FeGoggle.dim("next breaker, distribution panel or junction box."));
            tooltip.add(FeGoggle.dim("Use the paddle to go back to metering."));
            return;
        }
        if (isCreative()) {
            tooltip.add(FeGoggle.dim("Supplies both terminals; the wire on each is what clamps it."));
        } else {
            final MainBreakerStuds.Stud binding = bindingTerminal();
            tooltip.add(FeGoggle.capacity(rating));
            tooltip.add(FeGoggle.dim(bare
                    ? "A bare terminal rates the breaker at nothing."
                    : binding == null
                    ? "Both terminals wired alike, so neither holds it back."
                    : "Held there by the " + binding.label() + " terminal."));
        }
        tooltip.add(spanLine(MainBreakerStuds.Stud.INPUT));
        tooltip.add(spanLine(MainBreakerStuds.Stud.OUTPUT));

        if (!isCreative() && isClosed()) {
            tooltip.add(FeGoggle.supplyDemand(getIntakeFePerTick(), getRateFePerTick()));
            tooltip.add(FeGoggle.amount("In transit", inTransitFe(), transitCapacityFe()));
        }
        if (totalFe > 0L) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Passed in total: %,d FE", totalFe)));
        }
        tooltip.add(FeGoggle.dim(isLocked()
                ? "Sneak and use with an empty hand to take the padlock off."
                : "Sneak and use with an empty hand to padlock the handle."));
    }

    private Component spanLine(MainBreakerStuds.Stud stud) {
        final int wires = studLinkCount(stud.index());
        final int rate = studRate(stud.index());
        if (wires <= 0) {
            return Component.literal("    " + stud.label() + ": no wire")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.literal(String.format(Locale.ROOT, "    %s: %,d FE/t on %d wire%s",
                        stud.label(), rate, wires, wires == 1 ? "" : "s"))
                .withStyle(stud == MainBreakerStuds.Stud.INPUT
                        ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }

    private void maybeSyncReading() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (now - lastReadingSyncTick < SYNC_INTERVAL) {
            return;
        }
        if (now - lastReadingSyncTick >= HEARTBEAT_TICKS) {
            forceSync();
            return;
        }
        final int rating = ratingFePerTick();
        final float heat = thermal.heat(tripTicks());
        final boolean moved = movedEnough(rateFePerTick, syncedRate)
                || movedEnough(inRateFePerTick, syncedInRate)
                || rating != syncedRating
                || Math.abs(heat - syncedHeat) >= 0.02f
                || Math.abs(overload - syncedOverload) >= 0.1f
                || looped != syncedLooped;
        if (moved) {
            forceSync();
        }
    }

    private static boolean movedEnough(int now, int synced) {
        return Math.abs(now - synced) > Math.max(1, Math.abs(synced) / 10);
    }

    private void forceSync() {
        if (level == null || level.isClientSide()) {
            return;
        }
        syncedRating = ratingFePerTick();
        syncedRate = rateFePerTick;
        syncedInRate = inRateFePerTick;
        syncedHeat = thermal.heat(tripTicks());
        syncedOverload = overload;
        syncedLooped = looped;
        lastReadingSyncTick = level.getGameTime();
        setChanged();

        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", budget.energy());
        tag.putFloat("Heat", thermal.heatUnits());
        tag.putInt("Rate", rateFePerTick);
        tag.putInt("InRate", inRateFePerTick);
        tag.putFloat("Overload", overload);
        tag.putLong("Total", totalFe);
        tag.putBoolean("Looped", looped);
        tag.putInt("Rating", syncedRating);
        tag.putFloat("HeatFrac", syncedHeat);
        final ListTag spans = new ListTag();
        for (int stud = 0; stud < MainBreakerStuds.COUNT; stud++) {
            for (StudLink link : studLinks.get(stud)) {
                final CompoundTag span = new CompoundTag();
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

        thermal.setHeatUnits(tag.getFloat("Heat"));
        rateFePerTick = Math.max(0, tag.getInt("Rate"));
        inRateFePerTick = Math.max(0, tag.getInt("InRate"));
        overload = Math.max(0.0f, tag.getFloat("Overload"));
        totalFe = Math.max(0L, tag.getLong("Total"));
        looped = tag.getBoolean("Looped");
        syncedLooped = looped;
        syncedRating = Math.max(0, tag.getInt("Rating"));
        syncedRate = rateFePerTick;
        syncedInRate = inRateFePerTick;
        syncedHeat = tag.getFloat("HeatFrac");
        syncedOverload = overload;
        for (List<StudLink> links : studLinks) {
            links.clear();
        }
        final ListTag spans = tag.getList("StudWires", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag span = spans.getCompound(i);
            final int stud = span.getByte("S");
            if (!MainBreakerStuds.isStudIndex(stud)) {
                continue;
            }
            studLinks.get(stud).add(new StudLink(BlockPos.of(span.getLong("P")),
                    WireTier.byOrdinal(span.getByte("T"))));
        }

        budget.setCapacity(transitCapacityFe());
        budget.setEnergy(tag.getInt("Energy"));
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

    @Override
    public void setRemoved() {
        super.setRemoved();

        invalidateLinkedTerminals();
    }

    public static final float TRIP_DETENT = 0.5f;

    private float throwT;
    private long lastNanos;

    private long impactNanos = Long.MIN_VALUE;
    private int impactDir;
    private boolean started;

    public float throwTarget() {
        if (isTripped()) {
            return TRIP_DETENT;
        }
        return getBlockState().getBlock() instanceof MainBreakerBlock
                && getBlockState().getValue(MainBreakerBlock.POWERED) ? 1.0f : 0.0f;
    }

    // wall clock, the throw is 1/5s and a tick is a quarter of that. clamped at 80ms for hitches
    public float advanceThrow(float target, float throwSeconds) {
        final long now = System.nanoTime();
        if (!started) {
            started = true;
            throwT = target;
            lastNanos = now;
            return throwT;
        }
        final double dt = Math.max(0.0D, Math.min(0.08D, (now - lastNanos) / 1_000_000_000.0D));
        lastNanos = now;

        if (throwT != target) {
            final float step = (float) (dt / throwSeconds);
            throwT = throwT < target ? Math.min(target, throwT + step) : Math.max(target, throwT - step);
            if (throwT == target && (target >= 1.0f || target <= 0.0f)) {
                impactNanos = now;
                impactDir = target >= 1.0f ? 1 : -1;
            }
        }
        return throwT;
    }

    private float selectorT;
    private long selectorNanos;
    private boolean selectorStarted;

    public float advanceSelector(float target, float seconds) {
        final long now = System.nanoTime();
        if (!selectorStarted) {
            selectorStarted = true;
            selectorT = target;
            selectorNanos = now;
            return selectorT;
        }
        final double dt = Math.max(0.0D, Math.min(0.08D, (now - selectorNanos) / 1_000_000_000.0D));
        selectorNanos = now;
        if (selectorT != target) {
            final float step = (float) (dt * 2.0D / seconds);
            selectorT = selectorT < target
                    ? Math.min(target, selectorT + step) : Math.max(target, selectorT - step);
        }
        return selectorT;
    }

    public float sinceImpact() {
        return impactNanos == Long.MIN_VALUE
                ? 1000.0f
                : (float) ((System.nanoTime() - impactNanos) / 1_000_000_000.0D);
    }

    public int impactDirection() {
        return impactDir;
    }
}
