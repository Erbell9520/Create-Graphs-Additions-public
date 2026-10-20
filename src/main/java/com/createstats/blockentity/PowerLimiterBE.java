package com.createstats.blockentity;

import java.util.List;
import java.util.Locale;

import com.createstats.block.PowerLimiterBlock;
import com.createstats.power.LimiterLadder;
import com.createstats.power.LimiterMode;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

// splits the circuit, that's the only way a cap means anything on a pooled solve
public class PowerLimiterBE extends SmartBlockEntity
        implements IHaveGoggleInformation, com.createstats.power.FeRatedLoad,
        com.createstats.power.FeInlineInstrument, com.createstats.power.FeSupply,
        com.createstats.power.FeForecastingSource, com.createstats.util.CopyableSettings {
    private static final int WINDOW_TICKS = 20;

    private static final int MIN_BUFFER_FE = 8000;

    private static final int LOOP_CHECK_INTERVAL = 20;

    private final IEnergyStorage inputPort = new InputPort();
    private final IEnergyStorage outputPort = new OutputPort();

    private final com.createstats.power.LimiterBudget budget =
            new com.createstats.power.LimiterBudget(MIN_BUFFER_FE);

    @Nullable
    private ScrollValueBehaviour dial;

    private LimiterMode mode = LimiterMode.ALWAYS;

    private int redstoneStrength;

    private final int[] window = new int[WINDOW_TICKS];
    private final boolean[] clipWindow = new boolean[WINDOW_TICKS];
    private int windowHead;
    private int windowFilled;

    private int rateFePerTick;

    private int lastTickFe;

    private int limitedTicks;
    private long totalFe;
    private int peakFePerTick;
    private int lastComparatorLevel;

    private boolean looped;

    @Nullable
    private Status cachedStatus;
    private long statusTick = Long.MIN_VALUE;

    @Nullable
    private Status clientStatus;

    private int syncedRate = Integer.MIN_VALUE;
    private long syncedTotal = Long.MIN_VALUE;
    private int syncedStatus = Integer.MIN_VALUE;
    private long lastReadingSyncTick;

    public PowerLimiterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.dial = new LadderDial(
                Component.translatable("block.create_stats.power_limiter.dial"), this,
                new com.createstats.block.PowerLimiterValueBoxTransform());
        this.dial.between(0, LimiterLadder.MAX_NOTCH);
        this.dial.withFormatter(v -> LimiterLadder.format(LimiterLadder.feAt(v)));
        this.dial.withCallback(v -> this.onDialChanged());

        this.dial.requiresWrench();
        this.dial.value = LimiterLadder.DEFAULT_NOTCH;
        behaviours.add(this.dial);
    }

    private static final class LadderDial extends ScrollValueBehaviour {
        private LadderDial(Component label, SmartBlockEntity be,
                           com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform slot) {
            super(label, be, slot);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, LimiterLadder.MAX_NOTCH, 6,
                    List.of(Component.translatable("block.create_stats.power_limiter.dial.row")),
                    new ValueSettingsFormatter(v -> Component.literal(
                            LimiterLadder.format(LimiterLadder.feAt(v.value())))));
        }
    }

    public int notch() {
        return this.dial == null ? LimiterLadder.DEFAULT_NOTCH : LimiterLadder.clamp(this.dial.getValue());
    }

    public void setNotch(int notch) {
        if (this.dial != null) {
            this.dial.setValue(LimiterLadder.clamp(notch));
        }
    }

    private void onDialChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.updateStateProperty();
            this.forceSync();
        }
    }

    public int limitFePerTick() {
        return Math.min(LimiterLadder.feAt(this.notch()), maxConfiguredLimit());
    }

    public int effectiveLimitFePerTick() {
        return this.mode.apply(this.limitFePerTick(), this.redstoneStrength);
    }

    public LimiterMode mode() {
        return this.mode;
    }

    public void setMode(LimiterMode next) {
        if (next == this.mode) {
            return;
        }
        this.mode = next;
        this.refreshRedstone();
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.updateStateProperty();
            this.forceSync();
        }
    }

    @Override
    public String settingsKey() {
        return "create_stats:power_limiter";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Mode", this.mode.ordinal());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        if (!tag.contains("Mode")) {
            return false;
        }
        if (!simulate) {
            this.setMode(LimiterMode.byOrdinal(tag.getInt("Mode")));
        }
        return true;
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFreshDefaults(tag);
        writeSettings(tag, registries);
    }

    public int redstoneStrength() {
        return this.redstoneStrength;
    }

    private static int maxConfiguredLimit() {
        try {
            return com.createstats.ModConfiguration.COMMON.limiterMaxRate.get();
        } catch (RuntimeException ignored) {
            return LimiterLadder.feAt(LimiterLadder.MAX_NOTCH);
        }
    }

    public IEnergyStorage inputHandle() {
        return this.inputPort;
    }

    public IEnergyStorage outputHandle() {
        return this.outputPort;
    }

    private int intakeRoom(int wanted) {
        if (this.looped || wanted <= 0 || this.level == null || this.level.isClientSide) {
            return 0;
        }
        this.rollTick(this.now());
        return this.budget.intakeRoom(wanted, this.effectiveLimitFePerTick());
    }

    private void bookIntake(int fe) {
        if (fe > 0 && this.budget.bookIntake(fe) > 0) {
            this.setChanged();
        }
    }

    private int outflowAvailable(int wanted) {
        if (this.looped || wanted <= 0 || this.level == null || this.level.isClientSide) {
            return 0;
        }
        this.rollTick(this.now());
        return this.budget.outflowAvailable(wanted, this.effectiveLimitFePerTick());
    }

    private void bookOutflow(int fe) {
        if (fe > 0 && this.budget.bookOutflow(fe) > 0) {
            this.setChanged();
        }
    }

    private void noteClipped(int wanted, int allowed) {
        if (!this.looped) {
            this.budget.noteClipped(wanted, allowed);
        }
    }

    // ---------------------------- Tick ----------------------------

    @Override
    public void tick() {
        super.tick();
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        this.rollTick(lvl.getGameTime());
        this.refreshRedstone();
        if (lvl.getGameTime() % LOOP_CHECK_INTERVAL == 0) {
            this.looped = this.detectLoop();
        }
        this.pumpThrough();
        this.updateStateProperty();
        this.updateComparator();
        this.maybeSyncReading();
    }

    private void refreshRedstone() {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        final int next = this.mode == LimiterMode.ALWAYS ? 0 : lvl.getBestNeighborSignal(this.worldPosition);
        if (next != this.redstoneStrength) {
            this.redstoneStrength = next;
            this.setChanged();
        }
    }

    private void pumpThrough() {
        if (this.looped) {
            return;
        }
        final IEnergyStorage supply = this.portStorage(this.inputFace());
        if (supply != null && supply.canExtract()) {
            final int room = this.intakeRoom(this.bufferFe());
            final int available = supply.extractEnergy(this.probe(this.budget.room(this.bufferFe()), room),
                    true);
            this.noteClipped(available, room);
            if (available > 0 && room > 0) {
                this.bookIntake(supply.extractEnergy(Math.min(available, room), false));
            }
        }

        final IEnergyStorage load = this.portStorage(this.outputFace());
        if (load != null && load.canReceive()) {
            final int offer = this.outflowAvailable(this.bufferFe());
            final int wanted = load.receiveEnergy(this.probe(this.budget.stored(this.bufferFe()), offer),
                    true);
            this.noteClipped(wanted, offer);
            if (wanted > 0 && offer > 0) {
                this.bookOutflow(load.receiveEnergy(Math.min(wanted, offer), false));
            }
        }
    }

    // ask for one FE over the cap, that's the whole question. asking for the full buffer was silly
    private int probe(int ceiling, int allowed) {
        return Math.max(0, Math.min(ceiling, allowed + 1));
    }

    @Nullable
    private IEnergyStorage portStorage(Direction face) {
        final Level lvl = this.level;
        if (lvl == null) {
            return null;
        }
        final BlockPos at = this.getBlockPos().relative(face);
        if (PowerMeterBE.isWiring(lvl.getBlockEntity(at))) {
            return null;
        }
        return lvl.getCapability(Capabilities.EnergyStorage.BLOCK, at, face.getOpposite());
    }

    @Nullable
    private IEnergyStorage storageAt(Direction face) {
        final Level lvl = this.level;
        if (lvl == null) {
            return null;
        }
        return lvl.getCapability(Capabilities.EnergyStorage.BLOCK,
                this.getBlockPos().relative(face), face.getOpposite());
    }

    private boolean detectLoop() {
        final com.createstats.power.FeWireNetwork in = this.networkAt(this.inputFace());
        return in != null && in == this.networkAt(this.outputFace());
    }

    @Nullable
    private com.createstats.power.FeWireNetwork networkAt(Direction face) {
        final Level lvl = this.level;
        if (lvl == null) {
            return null;
        }
        final BlockEntity be = lvl.getBlockEntity(this.getBlockPos().relative(face));
        return be instanceof com.createstats.power.FeGridMember member ? member.network() : null;
    }

    private long now() {
        return this.level == null ? 0L : this.level.getGameTime();
    }

    private void rollTick(long now) {
        this.budget.setCapacity(this.bufferFe());
        final com.createstats.power.LimiterBudget.Tick closed = this.budget.roll(now);
        if (closed != null) {
            this.closeWindow(closed);
        }
    }

    private void closeWindow(com.createstats.power.LimiterBudget.Tick closed) {
        final int moved = closed.moved();
        this.lastTickFe = moved;
        if (moved > 0) {
            this.totalFe += moved;
            if (moved > this.peakFePerTick) {
                this.peakFePerTick = moved;
            }
            this.setChanged();
        }

        this.window[this.windowHead] = moved;
        this.clipWindow[this.windowHead] = closed.limited();
        this.windowHead = (this.windowHead + 1) % WINDOW_TICKS;
        if (this.windowFilled < WINDOW_TICKS) {
            ++this.windowFilled;
        }

        long sum = 0L;
        int clipped = 0;
        for (int i = 0; i < this.windowFilled; ++i) {
            sum += this.window[i];
            if (this.clipWindow[i]) {
                ++clipped;
            }
        }
        this.rateFePerTick = this.windowFilled == 0 ? 0 : (int) (sum / this.windowFilled);
        this.limitedTicks = clipped;
    }

    private void updateStateProperty() {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        final BlockState state = this.getBlockState();
        if (!state.hasProperty(PowerLimiterBlock.STATE)) {
            return;
        }
        final PowerLimiterBlock.LampState next = this.lampState();
        if (state.getValue(PowerLimiterBlock.STATE) != next) {
            lvl.setBlock(this.getBlockPos(), state.setValue(PowerLimiterBlock.STATE, next),
                    Block.UPDATE_CLIENTS);
        }
    }

    private PowerLimiterBlock.LampState lampState() {
        if (this.looped || this.effectiveLimitFePerTick() <= 0) {
            return PowerLimiterBlock.LampState.SHUT;
        }
        return this.rateFePerTick > 0 ? PowerLimiterBlock.LampState.FLOWING
                : PowerLimiterBlock.LampState.IDLE;
    }

    private void updateComparator() {
        final Level lvl = this.level;
        if (lvl == null) {
            return;
        }
        final int next = this.getAnalogOutputSignal();
        if (next != this.lastComparatorLevel) {
            this.lastComparatorLevel = next;
            lvl.updateNeighbourForOutputSignal(this.getBlockPos(), this.getBlockState().getBlock());
        }
    }

    @Override
    public boolean capsThroughput() {
        return true;
    }

    @Override
    public int throughputCeiling() {
        return this.looped ? 0 : Math.max(0, this.effectiveLimitFePerTick());
    }

    @Override
    public int managedThroughputCeiling() {
        if (this.looped) {
            return 0;
        }
        final int cap = this.effectiveLimitFePerTick();
        if (cap <= 0) {
            return 0;
        }
        this.rollTick(this.now());
        return Math.max(0, cap - this.budget.given());
    }

    @Override
    public void recordManagedTransfer(int fe, boolean clipped) {
        if (fe <= 0 && !clipped) {
            return;
        }
        final int cap = this.effectiveLimitFePerTick();
        if (cap <= 0) {
            return;
        }
        this.rollTick(this.now());
        final int booked = this.budget.bookPassThrough(Math.max(0, fe), cap);

        this.budget.noteClipped(fe, booked);
        this.budget.noteClipped(clipped);
    }

    public int getRateFePerTick() {
        return this.rateFePerTick;
    }

    @Override
    public int potentialFePerTick() {
        return Math.max(0, Math.min(getRateFePerTick(), effectiveLimitFePerTick()));
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
                () -> java.util.Collections.singletonList(this.networkAt(this.inputFace())),
                () -> this.bareInput());
    }

    private java.util.List<BlockEntity> bareInput() {
        final Level lvl = this.level;
        if (lvl == null) {
            return java.util.List.of();
        }
        final BlockEntity be = lvl.getBlockEntity(this.getBlockPos().relative(this.inputFace()));
        return be == null || be instanceof com.createstats.power.FeGridMember
                ? java.util.List.of() : java.util.List.of(be);
    }

    public int getLastTickFe() {
        return this.lastTickFe;
    }

    public long getTotalFe() {
        return this.totalFe;
    }

    public int getPeakFePerTick() {
        return this.peakFePerTick;
    }

    public int getBufferedFe() {
        return this.budget.energy();
    }

    public int getBufferCapacity() {
        return this.bufferFe();
    }

    private int bufferFe() {
        return Math.max(MIN_BUFFER_FE, this.effectiveLimitFePerTick());
    }

    public boolean isLooped() {
        return this.looped;
    }

    public boolean isLimiting() {
        return this.limitedTicks > 0 && this.effectiveLimitFePerTick() > 0;
    }

    public float saturation() {
        final int cap = this.effectiveLimitFePerTick();
        return cap <= 0 ? 0.0f : Math.min(1.0f, this.rateFePerTick / (float) cap);
    }

    public boolean hasSupply() {
        final IEnergyStorage in = this.storageAt(this.inputFace());
        return in != null && (in.canExtract() || in.canReceive());
    }

    public boolean hasLoad() {
        final IEnergyStorage out = this.storageAt(this.outputFace());
        return out != null && out.canReceive();
    }

    public enum Status {
        LOOPED,

        NO_SUPPLY,

        NO_LOAD,

        SHUT,

        LIMITING,

        PASSING,

        IDLE
    }

    public Status status() {
        final Level lvl = this.level;
        if (lvl != null && lvl.isClientSide && this.clientStatus != null) {
            return this.clientStatus;
        }
        final long now = lvl == null ? 0L : lvl.getGameTime();
        if (this.statusTick != now || this.cachedStatus == null) {
            this.statusTick = now;
            this.cachedStatus = this.computeStatus();
        }
        return this.cachedStatus;
    }

    private Status computeStatus() {
        if (this.looped) {
            return Status.LOOPED;
        }

        if (!this.hasSupply()) {
            return Status.NO_SUPPLY;
        }
        if (!this.hasLoad()) {
            return Status.NO_LOAD;
        }
        if (this.effectiveLimitFePerTick() <= 0) {
            return Status.SHUT;
        }
        if (this.isLimiting()) {
            return Status.LIMITING;
        }
        return this.rateFePerTick > 0 ? Status.PASSING : Status.IDLE;
    }

    public static String statusKey(Status state) {
        return switch (state) {
            case LOOPED -> "block.create_stats.power_limiter.state.looped";
            case NO_SUPPLY -> "block.create_stats.power_limiter.state.no_supply";
            case NO_LOAD -> "block.create_stats.power_limiter.state.no_load";
            case SHUT -> "block.create_stats.power_limiter.state.shut";
            case LIMITING -> "block.create_stats.power_limiter.state.limiting";
            case PASSING -> "block.create_stats.power_limiter.state.passing";
            case IDLE -> "block.create_stats.power_limiter.state.idle";
        };
    }

    @Nullable
    public Component getLoadName() {
        final Level lvl = this.level;
        if (lvl == null || !this.hasLoad()) {
            return null;
        }
        return lvl.getBlockState(this.getBlockPos().relative(this.outputFace())).getBlock().getName();
    }

    @Nullable
    public Component getSupplyName() {
        final Level lvl = this.level;
        if (lvl == null || !this.hasSupply()) {
            return null;
        }
        return lvl.getBlockState(this.getBlockPos().relative(this.inputFace())).getBlock().getName();
    }

    public void resetTotals() {
        this.totalFe = 0L;
        this.peakFePerTick = 0;
        this.syncedTotal = 0L;
        this.setChanged();
        this.forceSync();
    }

    public Direction inputFace() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(PowerLimiterBlock.FACING)
                ? state.getValue(PowerLimiterBlock.FACING)
                : Direction.NORTH;
    }

    public Direction outputFace() {
        return this.inputFace().getOpposite();
    }

    @Override
    public int ratedDrawFePerTick() {
        if (this.looped) {
            return 0;
        }
        final int cap = this.effectiveLimitFePerTick();
        if (cap <= 0) {
            return 0;
        }
        if (this.rateFePerTick > 0) {
            return Math.min(cap, this.rateFePerTick);
        }
        final IEnergyStorage load = this.storageAt(this.outputFace());
        return load == null ? 0 : Math.min(cap, Math.max(0, load.receiveEnergy(cap, true)));
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.rateFePerTick;
    }

    public int getAnalogOutputSignal() {
        final int cap = this.effectiveLimitFePerTick();
        if (cap <= 0 || this.rateFePerTick <= 0) {
            return 0;
        }

        return Math.max(1, Math.min(15, Math.round(15.0f * this.rateFePerTick / cap)));
    }

    private void forceSync() {
        this.syncedRate = this.rateFePerTick;
        this.syncedTotal = this.totalFe;
        this.syncedStatus = this.status().ordinal();
        this.lastReadingSyncTick = this.now();
        this.sendData();
    }

    private void maybeSyncReading() {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        final int state = this.status().ordinal();
        final boolean stateChanged = state != this.syncedStatus;
        final boolean rateChanged = Math.abs(this.rateFePerTick - this.syncedRate)
                > Math.max(1, Math.abs(this.syncedRate) / 10);
        final boolean totalChanged = this.totalFe != this.syncedTotal;
        if (!(stateChanged || rateChanged || totalChanged)) {
            return;
        }
        if (lvl.getGameTime() - this.lastReadingSyncTick < 10L) {
            return;
        }
        this.forceSync();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Power Limiter"));

        final Status state = this.status();
        final int cap = this.effectiveLimitFePerTick();
        switch (state) {
            case LOOPED, NO_SUPPLY, NO_LOAD -> {
                tooltip.add(Component.literal("    ").append(
                        Component.translatable(statusKey(state)).withStyle(ChatFormatting.RED)));
                return true;
            }
            case SHUT -> tooltip.add(Component.literal("    ").append(
                    Component.translatable(statusKey(state)).withStyle(ChatFormatting.GOLD)));
            case LIMITING -> tooltip.add(Component.literal("    ").append(
                    Component.literal(String.format(Locale.ROOT, "%,d / %,d FE/t",
                            this.rateFePerTick, cap)).withStyle(ChatFormatting.GOLD)));
            case PASSING -> tooltip.add(Component.literal("    ").append(
                    Component.literal(String.format(Locale.ROOT, "%,d / %,d FE/t",
                            this.rateFePerTick, cap)).withStyle(ChatFormatting.AQUA)));
            default -> tooltip.add(Component.literal("    ").append(
                    Component.translatable(statusKey(state)).withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (isPlayerSneaking) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_limiter.goggle.mode",
                                    Component.translatable(this.mode.translationKey()))
                            .withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_limiter.goggle.peak",
                                    String.format(Locale.ROOT, "%,d", this.peakFePerTick))
                            .withStyle(ChatFormatting.DARK_AQUA)));
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_limiter.goggle.total",
                                    String.format(Locale.ROOT, "%,d", this.totalFe))
                            .withStyle(ChatFormatting.DARK_AQUA)));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putInt("Energy", this.budget.energy());
        tag.putInt("Mode", this.mode.ordinal());
        tag.putLong("TotalFe", this.totalFe);
        tag.putInt("PeakFe", this.peakFePerTick);
        tag.putInt("Rate", this.rateFePerTick);
        tag.putBoolean("Looped", this.looped);
        tag.putInt("Signal", this.redstoneStrength);
        if (clientPacket) {
            tag.putInt("Status", this.status().ordinal());
            tag.putInt("Limited", this.limitedTicks);
            tag.putInt("LastTick", this.lastTickFe);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.mode = LimiterMode.byOrdinal(tag.getInt("Mode"));
        this.totalFe = tag.getLong("TotalFe");
        this.peakFePerTick = tag.getInt("PeakFe");
        this.rateFePerTick = tag.getInt("Rate");
        this.looped = tag.getBoolean("Looped");
        this.redstoneStrength = tag.getInt("Signal");

        this.budget.setCapacity(this.bufferFe());
        this.budget.setEnergy(tag.getInt("Energy"));
        if (clientPacket) {
            this.limitedTicks = tag.getInt("Limited");
            this.lastTickFe = tag.getInt("LastTick");
            this.clientStatus =
                    Status.values()[Math.floorMod(tag.getInt("Status"), Status.values().length)];
        } else {
            this.lastComparatorLevel = this.getAnalogOutputSignal();
        }
    }

    private final class InputPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = PowerLimiterBE.this.intakeRoom(maxReceive);
            if (!simulate) {
                PowerLimiterBE.this.noteClipped(PowerLimiterBE.this.budget.room(maxReceive), room);
                PowerLimiterBE.this.bookIntake(room);
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return PowerLimiterBE.this.budget.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return PowerLimiterBE.this.bufferFe();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private final class OutputPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int available = PowerLimiterBE.this.outflowAvailable(maxExtract);
            if (!simulate) {
                PowerLimiterBE.this.noteClipped(PowerLimiterBE.this.budget.stored(maxExtract),
                        available);
                PowerLimiterBE.this.bookOutflow(available);
            }
            return available;
        }

        @Override
        public int getEnergyStored() {
            return PowerLimiterBE.this.budget.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return PowerLimiterBE.this.bufferFe();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
