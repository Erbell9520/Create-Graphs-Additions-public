package com.createstats.blockentity;

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
import com.createstats.power.PortableFeBuffer;
import com.createstats.power.PortableFeLadder;
import com.createstats.powertrack.FeederPoint;
import com.createstats.powertrack.PowerTrack;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.TrackFeederValueBox;
import com.createstats.powertrack.core.FeederPlan;
import com.createstats.powertrack.core.RailPower;
import com.createstats.powertrack.core.SectionExchange;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

// a PPI whose far side is a rail. load while supplying, source while collecting, never both
public class TrackFeederBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, IHaveGoggleInformation {
    public static final BehaviourType<DialBehaviour> DIAL_TYPE = new BehaviourType<>("track_feeder_dial");
    public static final BehaviourType<FloorBehaviour> FLOOR_TYPE = new BehaviourType<>("track_feeder_floor");

    public static final int AUTO_ON_DARK = 0x6FD3FF;
    public static final int AUTO_ON_BRASS = 0x1D5E7A;

    public TrackTargetingBehaviour<FeederPoint> edgePoint;
    private DialBehaviour dial;
    private FloorBehaviour floor;

    private final PortableFeBuffer buffer = new PortableFeBuffer(this::setChanged);
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();

    private FeederPlan.Plan plan;
    private long plannedAt = Long.MIN_VALUE;

    private long offeredAt = Long.MIN_VALUE;
    private boolean floorHolding;
    private boolean autoSupplying = true;
    private FeederPlan.Reason reason = FeederPlan.Reason.NO_RAIL;

    private int section = -1;
    private long railAt = Long.MIN_VALUE;

    private int sectionWanted;
    private int ratedDraw;

    private int fromGrid;
    private int lastEnergy;

    private int railFlow;

    private int lamp;
    private SectionView sectionView = SectionView.NONE;
    private int ticksSinceSync;
    private int lastSyncedHash;

    private float needle;
    private float needlePrev;
    private float needleVelocity;
    private int lampHold;
    private int lampSign;
    private final com.createstats.powertrack.core.LampGlow lantern = new com.createstats.powertrack.core.LampGlow();

    public TrackFeederBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        managed.setPriority(FePriority.LOW);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        edgePoint = new TrackTargetingBehaviour<>(this, PowerTrack.FEEDER);
        dial = new DialBehaviour(this);
        floor = new FloorBehaviour(this);
        behaviours.add(edgePoint);
        behaviours.add(dial);
        behaviours.add(floor);
    }

    public static int ceiling() {
        return RailPower.sectionCeiling(FeRates.base());
    }

    public FeederPlan.Mode mode() {
        return dial == null ? FeederPlan.Mode.SUPPLY : dial.mode();
    }

    public int rate() {
        return dial == null ? ceiling() : PortableFeLadder.feAt(dial.column(), ceiling());
    }

    public int floorPct() {
        return floor == null ? 50 : floor.getValue() * 10;
    }

    private void onSettingsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            buffer.close();
        }
    }

    public Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            animate();
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
        final FeederPlan.Mode mode = mode();
        final int rate = rate();

        fromGrid = Math.max(0, buffer.energy() - lastEnergy);
        lastEnergy = buffer.energy();

        final FeManagerView view = network != null ? network.managerView(now) : null;
        long stored = 0L;
        long capacity = 0L;
        if (view != null) {
            stored = view.stored();
            capacity = view.capacity();
        } else if (network != null) {
            stored = network.lastResult().reserve();
            capacity = network.lastResult().reserveCapacity();
        }
        final boolean wouldSupply = mode == FeederPlan.Mode.SUPPLY
                || (mode == FeederPlan.Mode.AUTO && autoSupplying);
        final boolean redstone = level.hasNeighborSignal(worldPosition);
        final boolean off = redstone || (wouldSupply && managed.isSuppressed());
        final boolean onRail = section >= 0 && railAt != Long.MIN_VALUE && now - railAt <= 2;

        plan = FeederPlan.plan(new FeederPlan.Input(mode, onRail, off, rate, buffer.energy(), stored, capacity,
                floorPct(), floorHolding, autoSupplying,
                view != null && view.holdsCharging(),
                view != null && !view.servesLoads(),
                view != null && !view.takesSources()));
        plannedAt = now;
        floorHolding = plan.floorHolding();
        autoSupplying = plan.autoSupplying();
        reason = plan.reason();
        if (!onRail) {
            railFlow = 0;
            lamp = 0;
            sectionView = section >= 0 ? sectionView : SectionView.NONE;
        }

        final boolean supplying = plan.role() == SectionExchange.Role.SUPPLY;
        buffer.configure(rate, plan.chargeOpen(), plan.offersToGrid() && !supplying,
                FeederPlan.bufferCapacity(rate));
        // keep the rated draw through a shed so the panel knows what bringing it back costs

        ratedDraw = supplying && onRail && !redstone && !managed.isForcedOff()
                && reason != FeederPlan.Reason.FLOOR_HELD && reason != FeederPlan.Reason.STATION_HELD
                && reason != FeederPlan.Reason.WRONG_TERMINAL
                ? Math.min(rate, sectionWanted) : 0;
    }

    @Nullable
    public SectionExchange.Feeder railOffer(long now) {
        if (plan == null || plannedAt != now || offeredAt == now) {
            return null;
        }
        offeredAt = now;
        final int capacity = FeederPlan.bufferCapacity(rate());
        final int available = Math.max(0, Math.min(plan.railAvailable(), buffer.energy()));
        final int room = Math.max(0, Math.min(plan.railRoom(), capacity - buffer.energy()));

        lamp = com.createstats.powertrack.core.FeederLamp.of(plan.role(), available, room);
        final long grid = network == null ? -1L - worldPosition.asLong() : System.identityHashCode(network);
        return new SectionExchange.Feeder(grid, plan.role(), available, room, plan.trainFloorPct());
    }

    public void railResult(int delta, int sectionIndex, SectionView view, int wanted, long now) {
        if (delta > 0) {
            buffer.add(delta);
        } else if (delta < 0) {
            buffer.remove(-delta);
        }
        railFlow = -delta;
        section = sectionIndex;
        sectionView = view;
        sectionWanted = Math.max(0, wanted);
        railAt = now;
        lastEnergy = buffer.energy();
    }

    public void offRail(long now) {
        section = -1;
        railFlow = 0;
        lamp = 0;
        sectionWanted = 0;
        sectionView = SectionView.NONE;
        railAt = now;
        lastEnergy = buffer.energy();
    }

    public int railFlow() {
        return railFlow;
    }

    public SectionView sectionView() {
        return sectionView;
    }

    public FeederPlan.Reason reason() {
        return reason;
    }

    private void syncIfDue() {
        ticksSinceSync++;

        final int rate = Math.max(1, rate());
        final int hash = Objects.hash(reason, mode(), sectionView.state(), sectionView.cars(),
                sectionView.feeders(), Integer.signum(railFlow), railFlow / Math.max(1, rate / 32), floorHolding, lamp);
        if (hash != lastSyncedHash || ticksSinceSync >= (railFlow != 0 ? 10 : 40)) {
            lastSyncedHash = hash;
            ticksSinceSync = 0;
            sendData();
        }
    }

    private void animate() {
        needlePrev = needle;
        final float target = Math.max(-1f, Math.min(1f, railFlow / (float) Math.max(1, rate())));

        needleVelocity += (target - needle) * 0.32f;
        needleVelocity *= 0.62f;
        needle += needleVelocity;

        if (Math.abs(needle) > NEEDLE_STOP) {
            needle = Math.copySign(NEEDLE_STOP, needle);
            if (needleVelocity * needle > 0) {
                needleVelocity *= -0.4f;
            }
        }

        if (lamp != 0) {
            lampSign = lamp;
            lampHold = 8;
        } else if (lampHold > 0) {
            lampHold--;
        } else {
            lampSign = 0;
        }
        lantern.tick(lampSign);

        if (lantern.colour() != 0 && lantern.glow(1f) > com.createstats.powertrack.client.PowerTrackLights.LIT) {
            com.createstats.powertrack.client.PowerTrackLights.lamp(this, worldPosition.above());
        }
    }

    public static final float NEEDLE_STOP = 1.06f;

    public float needle(float partialTicks) {
        return needlePrev + (needle - needlePrev) * partialTicks;
    }

    public int lamp() {
        return lantern.colour();
    }

    public float lampGlow(float partialTicks) {
        return lantern.glow(partialTicks);
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
        return plan != null && plan.role() == SectionExchange.Role.SUPPLY ? fromGrid : 0;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final FeederPlan.Mode mode = mode();
        tooltip.add(FeGoggle.title(Component.translatable(getBlockState().getBlock().getDescriptionId())));
        tooltip.add(Component.literal("    " + glyph(mode) + " ")
                .append(Component.translatable(modeKey(mode)))
                .append(" " + FeRates.perTick(rate()))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(onDark(mode)))));
        tooltip.add(stateLine(mode));
        if (reason != FeederPlan.Reason.NO_RAIL) {
            tooltip.add(sectionView.stateLine("    Section: "));
            if (isPlayerSneaking) {
                tooltip.add(sectionView.countsLine());
            }
        }
        tooltip.add(FeGoggle.dim(switch (mode) {
            case SUPPLY -> "    Keeps station charge above " + floorPct() + "%";
            case COLLECT -> "    Leaves every train " + floorPct() + "% charged";
            case AUTO -> "    Supplies above " + floorPct() + "% station charge, collects below";
        }));
        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.amount("Buffer", buffer.energy(),
                    Math.max(FeederPlan.bufferCapacity(rate()), buffer.energy())));
            tooltip.add(FeGoggle.dim("    Section rated " + FeRates.perTick(RailPower.sectionCeiling(FeRates.base()))
                    + ", " + FeRates.perTick(RailPower.collectorRate(FeRates.base())) + " a shoe"));
            tooltip.add(FeGoggle.dim("    Panel tier: " + managed.priority().getDisplayName()));
        }
        return true;
    }

    private Component stateLine(FeederPlan.Mode mode) {
        return switch (reason) {
            case READY -> railFlow > 0
                    ? FeGoggle.ok("    Feeding the rail " + FeRates.perTick(railFlow))
                    : railFlow < 0
                    ? FeGoggle.ok("    Collecting " + FeRates.perTick(-railFlow) + " from the rail")
                    : FeGoggle.dim(mode == FeederPlan.Mode.COLLECT || (mode == FeederPlan.Mode.AUTO && !autoSupplying)
                    ? "    Ready to collect: no train giving" : "    Rail live: no train drawing");
            case SWITCHED_OFF -> FeGoggle.warn(level != null && level.hasNeighborSignal(worldPosition)
                    ? "    Off (redstone)" : "    Shed at the panel");
            case NO_RAIL -> FeGoggle.warn(edgePoint != null && edgePoint.getEdgePoint() != null
                    ? "    Not on Power Track: tap a Power Track section" : "    Not on a track yet");
            case FLOOR_HELD -> FeGoggle.warn("    Paused: station charge is down to " + floorPct() + "%");
            case STATION_HELD -> FeGoggle.warn("    Paused: the board feeding this station is short");
            case WRONG_TERMINAL -> FeGoggle.warn(mode == FeederPlan.Mode.COLLECT
                    ? "    Nothing reads this circuit: collect into a controller input"
                    : "    Nothing feeds this circuit: supply from a controller output");
        };
    }

    public static String glyph(FeederPlan.Mode mode) {
        return switch (mode) {
            case SUPPLY -> "▲";
            case COLLECT -> "▼";
            case AUTO -> "◆";
        };
    }

    public static String modeKey(FeederPlan.Mode mode) {
        return switch (mode) {
            case SUPPLY -> "create_stats.track_feeder.mode.supply";
            case COLLECT -> "create_stats.track_feeder.mode.collect";
            case AUTO -> "create_stats.track_feeder.mode.auto";
        };
    }

    public static int onDark(FeederPlan.Mode mode) {
        return switch (mode) {
            case SUPPLY -> com.createstats.power.PortableFeColors.CHARGE_ON_DARK;
            case COLLECT -> com.createstats.power.PortableFeColors.DRAW_ON_DARK;
            case AUTO -> AUTO_ON_DARK;
        };
    }

    public static int onBrass(FeederPlan.Mode mode) {
        return switch (mode) {
            case SUPPLY -> com.createstats.power.PortableFeColors.CHARGE_ON_BRASS;
            case COLLECT -> com.createstats.power.PortableFeColors.DRAW_ON_BRASS;
            case AUTO -> AUTO_ON_BRASS;
        };
    }

    public static String code(FeederPlan.Mode mode) {
        return switch (mode) {
            case SUPPLY -> com.createstats.power.PortableFeColors.CHARGE_CODE;
            case COLLECT -> com.createstats.power.PortableFeColors.DRAW_CODE;
            case AUTO -> "§b";
        };
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", buffer.energy());
        tag.putBoolean("FloorHolding", floorHolding);
        tag.putBoolean("AutoSupplying", autoSupplying);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());

        tag.putInt("Reason", reason.ordinal());
        tag.putInt("RailFlow", railFlow);
        tag.putInt("Lamp", lamp);
        tag.put("Section", sectionView.write());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        buffer.setEnergy(tag.getInt("Energy"));
        lastEnergy = buffer.energy();
        floorHolding = tag.getBoolean("FloorHolding");
        autoSupplying = !tag.contains("AutoSupplying") || tag.getBoolean("AutoSupplying");
        if (tag.contains("Priority")) {
            managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        }
        final FeederPlan.Reason[] reasons = FeederPlan.Reason.values();
        reason = reasons[Math.floorMod(tag.getInt("Reason"), reasons.length)];
        railFlow = tag.getInt("RailFlow");
        lamp = Integer.signum(tag.getInt("Lamp"));
        sectionView = SectionView.read(tag.getCompound("Section"));
    }

    private static MutableComponent tinted(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static class DialBehaviour extends ScrollValueBehaviour {
        private final TrackFeederBE owner;

        DialBehaviour(TrackFeederBE owner) {
            super(Component.translatable("create_stats.track_feeder.dial"), owner, new TrackFeederValueBox(true));
            this.owner = owner;
            final int width = PortableFeLadder.maxDial(ceiling()) + 1;
            between(0, 3 * width - 1);

            value = PortableFeLadder.defaultDial(ceiling());
            withFormatter(v -> {
                final FeederPlan.Mode m = FeederPlan.Mode.byRow(v / width);
                return code(m) + glyph(m) + LimiterLadder.compact(PortableFeLadder.feAt(v % width, ceiling()));
            });
            withCallback(v -> owner.onSettingsChanged());
        }

        @Override
        public BehaviourType<?> getType() {
            return DIAL_TYPE;
        }

        private static int width() {
            return PortableFeLadder.maxDial(ceiling()) + 1;
        }

        public FeederPlan.Mode mode() {
            return FeederPlan.Mode.byRow(Math.max(0, value) / width());
        }

        public int column() {
            return Math.max(0, value) % width();
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            final ImmutableList<Component> rows = ImmutableList.of(
                    tinted(glyph(FeederPlan.Mode.SUPPLY) + " ", onBrass(FeederPlan.Mode.SUPPLY))
                            .append(Component.translatable(modeKey(FeederPlan.Mode.SUPPLY))),
                    tinted(glyph(FeederPlan.Mode.COLLECT) + " ", onBrass(FeederPlan.Mode.COLLECT))
                            .append(Component.translatable(modeKey(FeederPlan.Mode.COLLECT))),
                    tinted(glyph(FeederPlan.Mode.AUTO) + " ", onBrass(FeederPlan.Mode.AUTO))
                            .append(Component.translatable(modeKey(FeederPlan.Mode.AUTO))));

            final ValueSettingsFormatter formatter = new ValueSettingsFormatter(settings -> tinted(
                    PortableFeLadder.cursorLabel(PortableFeLadder.feAt(settings.value(), ceiling())),
                    onBrass(FeederPlan.Mode.byRow(settings.row()))));
            return new ValueSettingsBoard(label, PortableFeLadder.maxDial(ceiling()), PortableFeLadder.MILESTONE,
                    rows, formatter);
        }

        @Override
        public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
            final int column = PortableFeLadder.clamp(settings.value(), ceiling());
            final int encoded = Math.floorMod(settings.row(), 3) * width() + column;
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
            return "TrackFeederDial";
        }
    }

    public static class FloorBehaviour extends ScrollValueBehaviour {
        private final TrackFeederBE owner;

        FloorBehaviour(TrackFeederBE owner) {
            super(Component.translatable("create_stats.track_feeder.floor"), owner, new TrackFeederValueBox(false));
            this.owner = owner;
            between(0, 9);
            value = 5;
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
            final Component row = Component.translatable(switch (owner.mode()) {
                case SUPPLY -> "create_stats.track_feeder.floor.station";
                case COLLECT -> "create_stats.track_feeder.floor.train";
                case AUTO -> "create_stats.track_feeder.floor.switch";
            });
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
            return "TrackFeederFloor";
        }
    }
}
