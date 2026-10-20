package com.createstats.blockentity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.createstats.block.PowerMeterBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import com.createstats.gui.meter.MeterHistory;
import com.createstats.network.PowerMeterHistoryPacket;

// splits the circuit so the number is true. "how much went through this cable" has no answer on a pooled grid
public class PowerMeterBE extends BlockEntity
        implements IHaveGoggleInformation, com.createstats.power.FeRatedLoad,
        com.createstats.power.FeInlineInstrument, com.createstats.power.FeSupply,
        com.createstats.power.FeForecastingSource {
    private static final int WINDOW_TICKS = 20;

    private static final int IDLE_THRESHOLD_FE = 0;

    private static final int MIN_BUFFER_FE = 8000;

    private int bufferFe = MIN_BUFFER_FE;

    private final IEnergyStorage inputPort = new InputPort();
    private final IEnergyStorage outputPort = new OutputPort();

    private int energy;

    private int movedThisTick;

    private long accumulatorTick = Long.MIN_VALUE;
    private final int[] window = new int[WINDOW_TICKS];
    private int windowHead;
    private int windowFilled;

    private int rateFePerTick;

    private int lastTickFe;
    private long totalFe;
    private int peakFePerTick;

    private int lastComparatorLevel;

    private boolean looped;
    private static final int LOOP_CHECK_INTERVAL = 20;

    @Nullable
    private Status cachedStatus;
    private long statusTick = Long.MIN_VALUE;

    private int syncedRate = Integer.MIN_VALUE;
    private long syncedTotal = Long.MIN_VALUE;
    private long lastReadingSyncTick;

    public PowerMeterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage inputHandle() {
        return this.inputPort;
    }

    public IEnergyStorage outputHandle() {
        return this.outputPort;
    }

    public void recordManagedTransfer(int fe, boolean clipped) {
        this.recordTransfer(fe);
    }

    @Override
    public int ratedDrawFePerTick() {
        if (this.looped) {
            return 0;
        }
        if (this.rateFePerTick > 0) {
            return this.rateFePerTick;
        }
        final IEnergyStorage load = this.storageAt(this.outputFace());
        return load == null ? 0 : Math.max(0, load.receiveEnergy(this.bufferFe, true));
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.rateFePerTick;
    }

    public void tick() {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        final long now = lvl.getGameTime();
        if (this.accumulatorTick != now) {
            this.closeAccumulator();
            this.accumulatorTick = now;
        }
        if (now % LOOP_CHECK_INTERVAL == 0) {
            this.looped = this.detectLoop();
        }
        this.refreshBuffer();
        this.pumpThrough();
        this.updateLitState();
        this.updateComparator();
        this.maybeSyncReading();
        this.recordHistory();
    }

    private void refreshBuffer() {
        final int wiring = Math.max(this.faceRate(this.inputFace()), this.faceRate(this.outputFace()));
        this.bufferFe = Math.max(MIN_BUFFER_FE, wiring);
    }

    private int faceRate(net.minecraft.core.Direction face) {
        final Level lvl = this.level;
        if (lvl == null) {
            return 0;
        }
        return lvl.getBlockEntity(this.worldPosition.relative(face))
                instanceof com.createstats.power.FeGridMember member
                ? Math.max(0, member.transferRate()) : 0;
    }

    private void pumpThrough() {
        if (this.looped) {
            return;
        }
        final IEnergyStorage supply = this.portStorage(this.inputFace());
        if (supply != null && supply.canExtract() && this.energy < this.bufferFe) {
            final int wanted = this.bufferFe - this.energy;
            final int available = supply.extractEnergy(wanted, true);
            if (available > 0) {
                this.energy += supply.extractEnergy(available, false);
                this.setChanged();
            }
        }

        final IEnergyStorage load = this.portStorage(this.outputFace());
        if (load != null && load.canReceive() && this.energy > 0) {
            final int offered = load.receiveEnergy(this.energy, true);
            if (offered > 0) {
                final int taken = load.receiveEnergy(offered, false);
                this.energy -= taken;
                this.recordTransfer(taken);
                this.setChanged();
            }
        }
    }

    @Nullable
    private IEnergyStorage portStorage(Direction face) {
        final Level lvl = this.level;
        if (lvl == null) {
            return null;
        }
        final BlockPos at = this.getBlockPos().relative(face);
        if (isWiring(lvl.getBlockEntity(at))) {
            return null;
        }
        return lvl.getCapability(Capabilities.EnergyStorage.BLOCK, at, face.getOpposite());
    }

    private boolean detectLoop() {
        final com.createstats.power.FeWireNetwork in = networkAt(this.inputFace());
        return in != null && in == networkAt(this.outputFace());
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

    // only on change, blockstate write = section rebake. UPDATE_CLIENTS since it's just a texture
    private void updateLitState() {
        final Level lvl = this.level;
        if (lvl == null) {
            return;
        }
        final BlockState state = this.getBlockState();
        if (!state.hasProperty(PowerMeterBlock.LIT)) {
            return;
        }
        final boolean shouldBeLit = this.hasFlow();
        if (state.getValue(PowerMeterBlock.LIT) != shouldBeLit) {
            lvl.setBlock(this.getBlockPos(), state.setValue(PowerMeterBlock.LIT, shouldBeLit),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void closeAccumulator() {
        final int moved = this.movedThisTick;
        this.movedThisTick = 0;
        this.lastTickFe = moved;

        if (moved > 0) {
            this.totalFe += moved;
            if (moved > this.peakFePerTick) {
                this.peakFePerTick = moved;
            }
            this.setChanged();
        }

        this.window[this.windowHead] = moved;
        this.windowHead = (this.windowHead + 1) % WINDOW_TICKS;
        if (this.windowFilled < WINDOW_TICKS) {
            ++this.windowFilled;
        }

        long sum = 0L;
        for (int i = 0; i < this.windowFilled; ++i) {
            sum += this.window[i];
        }
        this.rateFePerTick = this.windowFilled == 0 ? 0 : (int) (sum / this.windowFilled);
    }

    private void recordTransfer(int amount) {
        final Level lvl = this.level;
        if (lvl == null || amount <= 0) {
            return;
        }
        final long now = lvl.getGameTime();
        if (this.accumulatorTick != now) {
            this.closeAccumulator();
            this.accumulatorTick = now;
        }
        this.movedThisTick += amount;
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

    public static boolean isWiring(@Nullable BlockEntity be) {
        return be instanceof CableBE
                || be instanceof IndoorCableBE
                || be instanceof PowerConnectorBE
                || be instanceof SwitchBE
                || be instanceof DiagnosticJunctionBE
                || be instanceof PowerMeterBE;
    }

    public Direction inputFace() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(PowerMeterBlock.FACING)
                ? state.getValue(PowerMeterBlock.FACING)
                : Direction.NORTH;
    }

    public Direction outputFace() {
        return this.inputFace().getOpposite();
    }

    public int getRateFePerTick() {
        return this.rateFePerTick;
    }

    @Override
    public int potentialFePerTick() {
        return Math.max(0, getRateFePerTick());
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

    public int getAbsRateFePerTick() {
        return this.rateFePerTick;
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

    public boolean hasFlow() {
        return this.rateFePerTick > IDLE_THRESHOLD_FE;
    }

    public int getBufferedFe() {
        return this.energy;
    }

    public int getBufferCapacity() {
        return Math.max(this.bufferFe, this.energy);
    }

    public boolean isLooped() {
        return this.looped;
    }

    public boolean hasSupply() {
        final IEnergyStorage in = this.storageAt(this.inputFace());
        return in != null && (in.canExtract() || in.canReceive());
    }

    public boolean hasLoad() {
        final IEnergyStorage out = this.storageAt(this.outputFace());
        return out != null && out.canReceive();
    }

    public boolean isConnected() {
        return this.hasSupply() && this.hasLoad();
    }

    public enum Status {
        LOOPED,

        NO_SUPPLY,

        NO_LOAD,

        FLOWING,

        BACKED_UP,

        IDLE
    }

    public Status status() {
        final Level lvl = this.level;
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
        if (this.hasFlow()) {
            return Status.FLOWING;
        }
        return this.energy >= this.bufferFe ? Status.BACKED_UP : Status.IDLE;
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

    public int[] getLoadBuffer() {
        final IEnergyStorage load = this.storageAt(this.outputFace());
        if (load == null) {
            return new int[]{-1, -1};
        }
        final int capacity = load.getMaxEnergyStored();
        return capacity <= 0 ? new int[]{-1, -1} : new int[]{load.getEnergyStored(), capacity};
    }

    public void resetTotals() {
        this.totalFe = 0L;
        this.peakFePerTick = 0;
        this.syncedTotal = 0L;
        this.history1m.clear();
        this.history5m.clear();
        this.history15m.clear();
        this.resendHistoryToWatchers();
        this.setChanged();
        this.sync();
    }

    public int getAnalogOutputSignal() {
        final int magnitude = this.getAbsRateFePerTick();
        if (magnitude <= 0 || this.peakFePerTick <= 0) {
            return 0;
        }

        return Math.max(1, Math.min(15, Math.round(15.0f * magnitude / this.peakFePerTick)));
    }

    private void sync() {
        final Level lvl = this.level;
        if (lvl != null && !lvl.isClientSide) {
            lvl.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public static final long READING_SYNC_FLOOR_TICKS = 10L;

    private void maybeSyncReading() {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        final boolean flowingChanged = (this.rateFePerTick != 0) != (this.syncedRate != 0);
        final boolean rateChanged = Math.abs(this.rateFePerTick - this.syncedRate)
                > Math.max(1, Math.abs(this.syncedRate) / 10);
        final boolean totalChanged = this.totalFe != this.syncedTotal;
        if (!(flowingChanged || rateChanged || totalChanged)) {
            return;
        }
        if (lvl.getGameTime() - this.lastReadingSyncTick < READING_SYNC_FLOOR_TICKS) {
            return;
        }
        this.syncedRate = this.rateFePerTick;
        this.syncedTotal = this.totalFe;
        this.lastReadingSyncTick = lvl.getGameTime();
        this.sync();
    }

    private final MeterHistory history1m = new MeterHistory();
    private final MeterHistory history5m = new MeterHistory();
    private final MeterHistory history15m = new MeterHistory();

    private static final long HISTORY_TIMEOUT = 200L;
    private static final double HISTORY_RANGE_SQ = 128.0 * 128.0;

    private final Map<UUID, HistorySub> historySubs = new HashMap<>();

    private static final class HistorySub {
        int range;
        long lastSeen;
    }

    private void recordHistory() {
        final Level lvl = this.level;
        if (lvl == null) {
            return;
        }
        final long t = lvl.getGameTime();
        final boolean a1 = t % MeterHistory.CADENCE[0] == 0;
        final boolean a5 = t % MeterHistory.CADENCE[1] == 0;
        final boolean a15 = t % MeterHistory.CADENCE[2] == 0;
        if (!a1 && !a5 && !a15) {
            return;
        }

        final int sample = this.rateFePerTick;
        if (a1) {
            this.history1m.record(sample);
        }
        if (a5) {
            this.history5m.record(sample);
        }
        if (a15) {
            this.history15m.record(sample);
        }
        if (!this.historySubs.isEmpty()) {
            this.broadcastHistory(a1, a5, a15);
        }
    }

    private void broadcastHistory(boolean a1, boolean a5, boolean a15) {
        final Level lvl = this.level;
        final MinecraftServer server = lvl == null ? null : lvl.getServer();
        if (server == null) {
            return;
        }
        final long now = lvl.getGameTime();
        final Iterator<Map.Entry<UUID, HistorySub>> it = this.historySubs.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, HistorySub> entry = it.next();
            final ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            final HistorySub sub = entry.getValue();
            if (player == null || player.level() != lvl || now - sub.lastSeen > HISTORY_TIMEOUT
                    || player.distanceToSqr(this.worldPosition.getX() + 0.5,
                            this.worldPosition.getY() + 0.5,
                            this.worldPosition.getZ() + 0.5) > HISTORY_RANGE_SQ) {
                it.remove();
                continue;
            }
            final boolean advanced = sub.range == 0 ? a1 : sub.range == 1 ? a5 : a15;
            if (advanced) {
                PacketDistributor.sendToPlayer(player, PowerMeterHistoryPacket.delta(
                        this.worldPosition, sub.range, this.historyFor(sub.range).latest()));
            }
        }
    }

    private MeterHistory historyFor(int range) {
        return range == MeterHistory.RANGE_1M ? this.history1m
                : range == MeterHistory.RANGE_5M ? this.history5m : this.history15m;
    }

    public void subscribeHistory(ServerPlayer player, int range) {
        final Level lvl = this.level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        final int r = MeterHistory.clampRange(range);
        final HistorySub sub = this.historySubs.computeIfAbsent(player.getUUID(), k -> new HistorySub());
        sub.range = r;
        sub.lastSeen = lvl.getGameTime();
        PacketDistributor.sendToPlayer(player, PowerMeterHistoryPacket.full(
                this.worldPosition, r, this.historyFor(r).chronological()));
    }

    public void keepAliveHistory(UUID id, int range) {
        final HistorySub sub = this.historySubs.get(id);
        if (sub != null && this.level != null) {
            sub.range = MeterHistory.clampRange(range);
            sub.lastSeen = this.level.getGameTime();
        }
    }

    public void unsubscribeHistory(UUID id) {
        this.historySubs.remove(id);
    }

    private void resendHistoryToWatchers() {
        final Level lvl = this.level;
        final MinecraftServer server = lvl == null ? null : lvl.getServer();
        if (server == null || this.historySubs.isEmpty()) {
            return;
        }
        for (final Map.Entry<UUID, HistorySub> entry : this.historySubs.entrySet()) {
            final ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                PacketDistributor.sendToPlayer(player, PowerMeterHistoryPacket.full(this.worldPosition,
                        entry.getValue().range, this.historyFor(entry.getValue().range).chronological()));
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        this.historySubs.clear();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
                Component.translatable("block.create_stats.power_meter").withStyle(ChatFormatting.GRAY)));

        final Status state = this.status();
        if (state == Status.LOOPED || state == Status.NO_SUPPLY || state == Status.NO_LOAD) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable(faultKey(state)).withStyle(ChatFormatting.RED)));
            return true;
        }

        final int rate = this.rateFePerTick;
        if (state == Status.FLOWING) {
            tooltip.add(Component.literal("    ").append(
                    Component.literal(String.format(Locale.ROOT, "%,d FE/t · %,d FE/s", rate, rate * 20L))
                            .withStyle(ChatFormatting.AQUA)));
        } else if (state == Status.BACKED_UP) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_meter.goggle.backed_up")
                            .withStyle(ChatFormatting.GOLD)));
        } else {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_meter.goggle.idle")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (isPlayerSneaking) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_meter.goggle.peak",
                                    String.format(Locale.ROOT, "%,d", this.peakFePerTick))
                            .withStyle(ChatFormatting.DARK_AQUA)));
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_meter.goggle.total",
                                    String.format(Locale.ROOT, "%,d", this.totalFe))
                            .withStyle(ChatFormatting.DARK_AQUA)));
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.power_meter.goggle.comparator",
                                    this.getAnalogOutputSignal())
                            .withStyle(ChatFormatting.YELLOW)));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }

        return true;
    }

    public static String faultKey(Status state) {
        return switch (state) {
            case LOOPED -> "block.create_stats.power_meter.fault.looped";
            case NO_SUPPLY -> "block.create_stats.power_meter.fault.no_supply";
            case NO_LOAD -> "block.create_stats.power_meter.fault.no_load";
            default -> "block.create_stats.power_meter.goggle.idle";
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("TotalFe", this.totalFe);
        tag.putInt("PeakFe", this.peakFePerTick);
        tag.putInt("Rate", this.rateFePerTick);

        tag.putInt("Energy", this.energy);
        tag.putBoolean("Looped", this.looped);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.totalFe = tag.getLong("TotalFe");
        this.peakFePerTick = tag.getInt("PeakFe");
        this.rateFePerTick = tag.getInt("Rate");

        this.energy = Math.max(0, tag.getInt("Energy"));
        this.looped = tag.getBoolean("Looped");
        this.lastComparatorLevel = this.getAnalogOutputSignal();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private final class InputPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (PowerMeterBE.this.looped) {
                return 0;
            }
            final int room = Math.min(maxReceive,
                    Math.max(0, PowerMeterBE.this.bufferFe - PowerMeterBE.this.energy));
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                PowerMeterBE.this.energy += room;
                PowerMeterBE.this.setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return PowerMeterBE.this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return PowerMeterBE.this.getBufferCapacity();
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
            if (PowerMeterBE.this.looped) {
                return 0;
            }
            final int taken = Math.min(maxExtract, PowerMeterBE.this.energy);
            if (taken <= 0) {
                return 0;
            }
            if (!simulate) {
                PowerMeterBE.this.energy -= taken;
                PowerMeterBE.this.recordTransfer(taken);
                PowerMeterBE.this.setChanged();
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return PowerMeterBE.this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return PowerMeterBE.this.getBufferCapacity();
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
