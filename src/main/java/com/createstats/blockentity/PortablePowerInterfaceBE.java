package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.PortablePowerValueBox;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FeManagerView;
import com.createstats.power.FePriority;
import com.createstats.power.FeRates;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.LimiterLadder;
import com.createstats.power.PortableFeColors;
import com.createstats.power.PortableFeDirectionLock;
import com.createstats.power.PortableFeBuffer;
import com.createstats.power.PortableFeLadder;
import com.createstats.power.PortableFeTransfer;
import com.createstats.power.PortableFeTransfer.Mode;
import com.createstats.power.PortableFeTransfer.Reason;
import com.createstats.power.TrainBankLedger;
import com.createstats.train.power.ContraptionFeStores;
import com.createstats.train.power.TrainChargeLedger;
import com.createstats.train.power.TrainChargeProbe;
import com.createstats.train.power.TrainChargeReading;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class PortablePowerInterfaceBE extends PortableStorageInterfaceBlockEntity
        implements FeGridMember, FeManagedLoad, IHaveGoggleInformation {
    public static final BehaviourType<DialBehaviour> DIAL_TYPE = new BehaviourType<>("portable_power_dial");
    public static final BehaviourType<FloorBehaviour> FLOOR_TYPE = new BehaviourType<>("portable_power_floor");

    private static final Map<Level, PortableFeDirectionLock> LOCKS = new WeakHashMap<>();

    public static PortableFeDirectionLock directionLock(Level level) {
        return LOCKS.computeIfAbsent(level, l -> new PortableFeDirectionLock());
    }

    private static final int LEDGER_INTERVAL = 20;

    private static final int KEEP_ALIVE_SLACK = 10;

    private DialBehaviour dial;
    private FloorBehaviour floor;

    private final PortableFeBuffer buffer = new PortableFeBuffer(this::setChanged);
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();

    @Nullable
    private Contraption contraption;
    private boolean floorHolding;
    private Reason reason = Reason.DISCONNECTED;

    private int moved;

    private int ratedDraw;

    private int trainStored;
    private int trainCapacity;
    private String dockedName = "";
    private int lastComparator = -1;
    private int ticksSinceSync;
    private int lastSyncedHash;

    public PortablePowerInterfaceBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        managed.setPriority(FePriority.LOW);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        dial = new DialBehaviour(this);
        floor = new FloorBehaviour(this);
        behaviours.add(dial);
        behaviours.add(floor);
    }

    public static int ceiling() {
        try {
            return ModConfiguration.COMMON.portableInterfaceMaxRate.get();
        } catch (RuntimeException notLoaded) {
            return PortableFeLadder.DEFAULT_CEILING_FE;
        }
    }

    public static int defaultFloorPct() {
        try {
            return ModConfiguration.COMMON.portableInterfaceFloorPct.get();
        } catch (RuntimeException notLoaded) {
            return PortableFeTransfer.DEFAULT_FLOOR_PCT;
        }
    }

    public Mode mode() {
        return dial == null ? Mode.CHARGE : dial.mode();
    }

    public int rate() {
        return dial == null ? PortableFeLadder.DEFAULT_FE : PortableFeLadder.feAt(dial.column(), ceiling());
    }

    public int floorPct() {
        return floor == null ? defaultFloorPct() : floor.getValue() * 10;
    }

    private void onSettingsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            buffer.close();
        }
    }

    @Override
    public void startTransferringTo(Contraption contraption, float distance) {
        this.contraption = contraption;
        super.startTransferringTo(contraption, distance);
    }

    @Override
    protected void stopTransferring() {
        this.contraption = null;
        super.stopTransferring();
    }

    @Override
    protected void invalidateCapability() {
    }

    public Direction front() {
        final BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        if (network == null || !network.isPduManaged(now)) {
            setManagedShed(false);
        }
        step(now);
        syncIfDue();
    }

    private void step(long now) {
        final Mode mode = mode();
        final int rate = rate();
        final AbstractContraptionEntity docked = connectedEntity instanceof AbstractContraptionEntity e ? e : null;
        final boolean connected = canTransfer() && contraption != null && docked != null;

        final ContraptionFeStores stores = connected ? TrainChargeProbe.storesOf(contraption) : null;
        final TrainBankLedger ledger = stores != null ? stores.ledger()
                : new TrainBankLedger(new int[0], new int[0]);
        final long key = docked != null ? docked.getId() : 0L;
        final PortableFeDirectionLock lock = LOCKS.computeIfAbsent(level, l -> new PortableFeDirectionLock());
        final boolean locked = connected && !lock.mayMove(key, mode, now);
        final boolean off = isPowered() || (mode == Mode.CHARGE && managed.isSuppressed());

        // use what the panel stamped, the circuit's own reading only counts its members

        final FeManagerView view = network != null ? network.managerView(now) : null;
        long sourceStored = 0L;
        long sourceCapacity = 0L;
        if (view != null) {
            sourceStored = view.stored();
            sourceCapacity = view.capacity();
        } else if (network != null) {
            sourceStored = network.lastResult().reserve();
            sourceCapacity = network.lastResult().reserveCapacity();
        }
        final boolean stationHolds = view != null && mode == Mode.CHARGE && view.holdsCharging();
        final boolean wrongTerminal = view != null
                && (mode == Mode.CHARGE ? !view.servesLoads() : !view.takesSources());

        final PortableFeTransfer.Plan plan = PortableFeTransfer.plan(new PortableFeTransfer.Input(
                mode, connected, off, locked, rate, buffer.energy(), ledger.stored(), ledger.capacity(),
                sourceStored, sourceCapacity, floorPct(), floorHolding, stationHolds, wrongTerminal));
        floorHolding = plan.floorHolding();
        reason = plan.reason();

        int crossed = 0;
        if (plan.trainDelivery() > 0) {
            crossed = ledger.fill(plan.trainDelivery());
            buffer.remove(crossed);
        } else if (plan.trainPull() > 0) {
            crossed = ledger.drain(plan.trainPull());
            buffer.add(crossed);
        }
        if (crossed > 0) {
            if (stores.commit(ledger)) {
                com.createstats.train.power.CarriedDeviceSync.touched(docked);
            }
            lock.stamp(key, mode, now);
            keepAlive();
            setChanged();
        }
        moved = crossed;

        final int trainRoom = ledger.room();
        buffer.configure(rate, mode == Mode.CHARGE && reason == Reason.READY,
                mode == Mode.DRAW && buffer.energy() > 0 && !isPowered(), trainRoom);

        ratedDraw = mode == Mode.CHARGE && connected && !isPowered() && !managed.isForcedOff()
                && !floorHolding && !locked && !stationHolds && !wrongTerminal
                ? Math.min(rate, trainRoom) : 0;

        if (connected) {
            trainStored = ledger.stored();
            trainCapacity = ledger.capacity();
            dockedName = docked.getContraptionName().getString();
            if (now % LEDGER_INTERVAL == 0) {
                recordTrain(docked, now);
            }
        } else {
            trainStored = 0;
            trainCapacity = 0;
            dockedName = "";
        }
    }

    private void keepAlive() {
        if (transferTimer <= getTransferTimeout() + ANIMATION - KEEP_ALIVE_SLACK) {
            onContentTransferred();
        }
    }

    private void recordTrain(AbstractContraptionEntity docked, long now) {
        final UUID trainId = TrainChargeProbe.trainOf(docked);
        if (trainId == null) {
            return;
        }
        final Train train = Create.RAILWAYS.trains.get(trainId);
        final TrainChargeLedger ledger = TrainChargeLedger.get(level.getServer());
        if (train == null || ledger == null) {
            return;
        }
        final GlobalStation station = train.getCurrentStation();
        final TrainChargeReading reading = TrainChargeProbe.read(train, now, station == null ? "" : station.name);
        if (reading != null) {
            ledger.record(trainId, reading);
        }
    }

    private void syncIfDue() {
        final int comparator = comparatorLevel();
        if (comparator != lastComparator) {
            lastComparator = comparator;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        ticksSinceSync++;

        final int hash = java.util.Objects.hash(reason, floorHolding, dockedName, trainCapacity,
                trainStored / Math.max(1, trainCapacity / 64), moved > 0);
        if (hash != lastSyncedHash || ticksSinceSync >= (moved > 0 ? 10 : 40)) {
            lastSyncedHash = hash;
            ticksSinceSync = 0;
            sendData();
        }
    }

    public int comparatorLevel() {
        return trainCapacity > 0 ? TrainBankLedger.comparatorLevel(trainStored, trainCapacity) : 0;
    }

    public IEnergyStorage energyHandle() {
        return buffer;
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return rate();
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return buffer;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() != shed) {
            managed.setShed(shed);
            setChanged();
        }
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return mode() == Mode.CHARGE ? moved : 0;
    }

    public float extension(float partialTicks) {
        return (float) (Math.pow(connectionAnimation.getValue(partialTicks), 2) * distance / 2);
    }

    public boolean headLit() {
        return transferTimer >= ANIMATION && connectionAnimation.getValue() >= 1.0f;
    }

    public float connectionDistance() {
        return distance;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final Mode mode = mode();
        tooltip.add(FeGoggle.title(Component.translatable(getBlockState().getBlock().getDescriptionId())));
        tooltip.add(pad(Component.literal(PortableFeColors.glyph(mode) + " ")
                .append(Component.translatable(mode == Mode.CHARGE
                        ? "create_stats.portable_power_interface.mode.charge"
                        : "create_stats.portable_power_interface.mode.draw"))
                .append(" " + FeRates.perTick(rate()))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(PortableFeColors.onDark(mode))))));
        tooltip.add(stateLine(mode));
        if (trainCapacity > 0) {
            tooltip.add(FeGoggle.dim(dockedName.isEmpty() ? "Docked" : dockedName));
            tooltip.add(FeGoggle.stored(trainStored, trainCapacity));
        }
        tooltip.add(FeGoggle.dim((mode == Mode.CHARGE
                ? "Keeps station charge above "
                : "Keeps train charge above ") + floorPct() + "%"));
        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.amount("Buffer", buffer.energy(),
                    Math.max(PortableFeTransfer.bufferCapacity(rate()), buffer.energy())));
            tooltip.add(FeGoggle.dim("Panel tier: " + managed.priority().getDisplayName()));
        }
        return true;
    }

    private Component stateLine(Mode mode) {
        return switch (reason) {
            case READY -> moved > 0
                    ? FeGoggle.ok((mode == Mode.CHARGE ? "Charging " : "Drawing ") + FeRates.perTick(moved))
                    : FeGoggle.warn(mode == Mode.CHARGE ? "Docked, waiting for supply"
                            : "Docked, waiting for the circuit to take it");
            case SWITCHED_OFF -> FeGoggle.warn(isPowered() ? "Off (redstone)" : "Shed at the panel");
            case DISCONNECTED -> FeGoggle.dim("Nothing docked");
            case NO_STORAGE -> FeGoggle.warn("Nothing aboard stores FE");
            case LOCKED -> FeGoggle.warn(mode == Mode.CHARGE
                    ? "Another interface is drawing from this train"
                    : "Another interface is charging this train");
            case FLOOR_HELD -> FeGoggle.warn((mode == Mode.CHARGE
                    ? "Paused: station charge is down to "
                    : "Paused: train charge is down to ") + floorPct() + "%");
            case TRAIN_FULL -> FeGoggle.ok("Train full");
            case TRAIN_EMPTY -> FeGoggle.warn("Train empty");
            case STATION_HELD -> FeGoggle.warn("Paused: the board feeding this station is short");
            case WRONG_TERMINAL -> FeGoggle.warn(mode == Mode.CHARGE
                    ? "Nothing feeds this circuit: charge from a controller output"
                    : "Nothing reads this circuit: draw into a controller input, not past a limiter");
        };
    }

    private static Component pad(Component line) {
        return Component.literal("    ").append(line);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", buffer.energy());
        tag.putBoolean("FloorHolding", floorHolding);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());

        tag.putInt("Reason", reason.ordinal());
        tag.putInt("Moved", moved);
        tag.putInt("TrainStored", trainStored);
        tag.putInt("TrainCapacity", trainCapacity);
        tag.putString("Docked", dockedName);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        buffer.setEnergy(tag.getInt("Energy"));
        floorHolding = tag.getBoolean("FloorHolding");
        if (tag.contains("Priority")) {
            managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        }
        final Reason[] reasons = Reason.values();
        reason = reasons[Math.floorMod(tag.getInt("Reason"), reasons.length)];
        moved = Math.max(0, tag.getInt("Moved"));
        trainStored = Math.max(0, tag.getInt("TrainStored"));
        trainCapacity = Math.max(0, tag.getInt("TrainCapacity"));
        dockedName = tag.getString("Docked");
    }

    private static MutableComponent tinted(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static class DialBehaviour extends ScrollValueBehaviour {
        private final PortablePowerInterfaceBE owner;

        DialBehaviour(PortablePowerInterfaceBE owner) {
            super(Component.translatable("create_stats.portable_power_interface.dial"), owner,
                    new PortablePowerValueBox(true));
            this.owner = owner;
            final int max = PortableFeLadder.maxDial(ceiling());
            between(-(max + 1), max + 1);

            value = PortableFeLadder.defaultDial(ceiling()) + 1;

            withFormatter(v -> {
                final Mode m = v < 0 ? Mode.DRAW : Mode.CHARGE;
                return PortableFeColors.code(m) + PortableFeColors.glyph(m)
                        + LimiterLadder.compact(PortableFeLadder.feAt(Math.abs(v) - 1, ceiling()));
            });
            withCallback(v -> owner.onSettingsChanged());
        }

        @Override
        public BehaviourType<?> getType() {
            return DIAL_TYPE;
        }

        public Mode mode() {
            return value < 0 ? Mode.DRAW : Mode.CHARGE;
        }

        public int column() {
            return Math.max(0, Math.abs(value) - 1);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            final ImmutableList<Component> rows = ImmutableList.of(
                    tinted(PortableFeColors.CHARGE_GLYPH + " ", PortableFeColors.CHARGE_ON_BRASS)
                            .append(Component.translatable("create_stats.portable_power_interface.mode.charge")),
                    tinted(PortableFeColors.DRAW_GLYPH + " ", PortableFeColors.DRAW_ON_BRASS)
                            .append(Component.translatable("create_stats.portable_power_interface.mode.draw")));

            final ValueSettingsFormatter formatter = new ValueSettingsFormatter(settings -> tinted(
                    PortableFeLadder.cursorLabel(PortableFeLadder.feAt(settings.value(), ceiling())),
                    PortableFeColors.onBrass(Mode.byRow(settings.row()))));
            return new ValueSettingsBoard(label, PortableFeLadder.maxDial(ceiling()),
                    PortableFeLadder.MILESTONE, rows, formatter);
        }

        @Override
        public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
            final int column = PortableFeLadder.clamp(settings.value(), ceiling());
            final int encoded = settings.row() == 1 ? -(column + 1) : column + 1;
            if (encoded != value) {
                playFeedbackSound(this);
            }
            setValue(encoded);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(mode().row(), column());
        }

        @Override
        public String getClipboardKey() {
            return "PortablePowerDial";
        }
    }

    public static class FloorBehaviour extends ScrollValueBehaviour {
        private final PortablePowerInterfaceBE owner;

        FloorBehaviour(PortablePowerInterfaceBE owner) {
            super(Component.translatable("create_stats.portable_power_interface.floor"), owner,
                    new PortablePowerValueBox(false));
            this.owner = owner;
            between(0, 9);
            value = Math.max(0, Math.min(9, defaultFloorPct() / 10));
            withFormatter(v -> (v * 10) + "%");
            withCallback(v -> owner.onSettingsChanged());
        }

        @Override
        public BehaviourType<?> getType() {
            return FLOOR_TYPE;
        }

        @Override
        public int netId() {
            return 1;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("FloorSetting", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            if (nbt.contains("FloorSetting")) {
                value = Math.max(0, Math.min(9, nbt.getInt("FloorSetting")));
            }
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            final Component row = Component.translatable(owner.mode() == Mode.CHARGE
                    ? "create_stats.portable_power_interface.floor.station"
                    : "create_stats.portable_power_interface.floor.train");
            return new ValueSettingsBoard(label, 9, 5, ImmutableList.of(row),
                    new ValueSettingsFormatter(settings -> Component.literal((settings.value() * 10) + "%")));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
            final int v = Math.max(0, Math.min(9, settings.value()));
            if (v != value) {
                playFeedbackSound(this);
            }
            setValue(v);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, value);
        }

        @Override
        public String getClipboardKey() {
            return "PortablePowerFloor";
        }
    }
}
