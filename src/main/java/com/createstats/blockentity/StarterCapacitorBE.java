package com.createstats.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.createstats.block.StarterCapacitorBlock;
import com.createstats.block.StarterModeValueBox;
import com.createstats.power.CapacitorDischarge;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FeManagerView;
import com.createstats.power.FePriority;
import com.createstats.power.FeSupply;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.StarterClaims;
import com.createstats.power.StarterMode;
import com.createstats.power.StarterPort;
import com.createstats.power.StarterTrigger;
import com.createstats.registry.ModSounds;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class StarterCapacitorBE extends SmartBlockEntity implements IHaveGoggleInformation, FeSupply, FeManagedLoad,
        com.createstats.power.FeBurstSource {
    private static final StarterClaims CLAIMS = new StarterClaims();

    public enum ModeOption implements INamedIconOptions {
        AUTO(AllIcons.I_ACTIVE),
        REDSTONE(AllIcons.I_TARGET),
        HOLD(AllIcons.I_PAUSE);

        private final AllIcons icon;

        ModeOption(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return "create_stats.starter_capacitor.mode." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final StarterPort port;
    private final StarterTrigger trigger = new StarterTrigger();
    private final FeManagedState managed = new FeManagedState();
    private ScrollOptionBehaviour<ModeOption> mode;

    private long firingSince;
    private boolean onManagedGrid;
    private boolean onAnyGrid;

    private Reach reach = Reach.OK;

    private enum Reach {
        OK,

        CANNOT_FIRE,

        CANNOT_CHARGE
    }

    private record BoardFeed(FeWireNetwork net, long tick, int intake, List<BlockPos> boards) {
    }

    private static final int BOARD_SCAN_TICKS = 20;
    private final List<BoardFeed> boardFeeds = new ArrayList<>(2);
    private int measuredDraw;
    private int lastSyncedStored = -1;
    private int syncCooldown;
    private boolean redstonePrimed;

    private int clientDissipated;

    private int clientAge;
    private boolean humPlaying;
    private boolean whinePlaying;

    public StarterCapacitorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.port = new StarterPort(() -> level == null ? 0L : level.getGameTime(), this::setChanged);

        this.managed.setPriority(FePriority.LOW);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(ModeOption.class, Component.literal("Mode"), this,
                new StarterModeValueBox());
        mode.withCallback(i -> sendData());
        behaviours.add(mode);
    }

    public StarterPort port() {
        return port;
    }

    public StarterMode mode() {
        return mode == null ? StarterMode.AUTO : StarterMode.byOrdinal(mode.get().ordinal());
    }

    public StarterTrigger trigger() {
        return trigger;
    }

    public int clientAge() {
        return clientAge;
    }

    public float burnShare() {
        final int a = CapacitorDischarge.allowanceAt(trigger.phaseTick());
        return a <= 0 ? 0.0f : Math.min(1.0f, clientDissipated / (float) a);
    }

    private long dumpedShown;

    public Direction glandFace() {
        return facing().getCounterClockWise();
    }

    public Direction lugFace() {
        return facing().getClockWise();
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(StarterCapacitorBlock.FACING) ? state.getValue(StarterCapacitorBlock.FACING)
                : Direction.NORTH;
    }

    public void loadFromItem(int stored, int modeOrdinal) {
        port.setStored(stored);
        if (mode != null) {
            mode.setValue(Math.max(0, Math.min(ModeOption.values().length - 1, modeOrdinal)));
        }
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            clientTick();
            return;
        }
        final long now = level.getGameTime();
        final List<FeWireNetwork> nets = networks();
        onAnyGrid = !nets.isEmpty();
        onManagedGrid = false;
        boolean shortNow = false;
        boolean sibling = false;
        boolean fires = false;
        boolean charges = false;
        boolean refillFunded = false;
        for (FeWireNetwork net : nets) {
            sibling |= CLAIMS.heldByOther(net, this, now);
            if (net.isPduManaged(now)) {
                onManagedGrid = true;
                final FeManagerView view = net.managerView(now);
                refillFunded |= view != null && view.fundsStores();

                fires |= view == null || view.drawsBursts();

                charges |= view == null || view.servesLoads() || view.fundsStores();
                shortNow |= StarterPort.managedShort(view);
                continue;
            }
            fires = true;
            charges = true;
            shortNow |= othersShort(net, now);
        }
        final Reach nowReach = !onAnyGrid || (fires && charges) ? Reach.OK
                : !fires ? Reach.CANNOT_FIRE : Reach.CANNOT_CHARGE;
        if (nowReach != reach) {
            reach = nowReach;
            sendData();
        }

        if (port.firing()) {
            port.settle();
        }
        if (!redstonePrimed) {
            trigger.primeRedstone(level.hasNeighborSignal(worldPosition));
            redstonePrimed = true;
        }
        final StarterTrigger.Phase before = trigger.phase();
        final int stored = port.stored();
        trigger.tick(new StarterTrigger.Input(mode(), shortNow, stored >= CapacitorDischarge.CAPACITY,
                stored > 0, level.hasNeighborSignal(worldPosition), sibling));
        if (trigger.claiming()) {
            for (FeWireNetwork net : nets) {
                CLAIMS.claim(net, this, now, now + 2);
            }
        }
        final StarterTrigger.Phase after = trigger.phase();

        if (after == StarterTrigger.Phase.CLOSING && trigger.phaseTick() == StarterTrigger.SWING_TICKS) {
            play(ModSounds.STARTER_CAPACITOR_CLUNK.get(), 1.0f, 1.0f);
        }
        if (before != after) {
            if (after == StarterTrigger.Phase.FIRING) {
                firingSince = now;
                port.setFiring(true, now);
                play(ModSounds.STARTER_CAPACITOR_CRACK.get(), 1.4f, 1.0f);
                reflood(nets);
                setFiringState(true);
            } else if (before == StarterTrigger.Phase.FIRING) {
                port.setFiring(false, 0);
                reflood(nets);
                setFiringState(false);
            }
            if (after == StarterTrigger.Phase.RELEASING) {
                play(ModSounds.STARTER_CAPACITOR_RELEASE.get(), 0.8f, 1.0f);
            }
            sendData();
        }

        final boolean may = trigger.accepting() && !managed.isForcedOff()
                && (!managed.isSuppressed() || refillFunded);
        port.setChargeOpen(StarterPort.GLAND, may && gateFor(networkAt(glandFace()), now));
        port.setChargeOpen(StarterPort.LUG, may && gateFor(networkAt(lugFace()), now));
        measuredDraw = Math.max(0, port.movedThisTick());

        final boolean busy = after != StarterTrigger.Phase.IDLE || trigger.shortTicks() > 0;
        if (--syncCooldown <= 0 && (busy || port.stored() != lastSyncedStored)) {
            syncCooldown = busy ? 5 : 20;
            lastSyncedStored = port.stored();
            sendData();
        }
    }

    private static void reflood(List<FeWireNetwork> nets) {
        for (FeWireNetwork net : nets) {
            net.markDirty();
        }
    }

    private void setFiringState(boolean firing) {
        final BlockState state = getBlockState();
        if (level != null && state.hasProperty(StarterCapacitorBlock.FIRING)
                && state.getValue(StarterCapacitorBlock.FIRING) != firing) {
            level.setBlock(worldPosition, state.setValue(StarterCapacitorBlock.FIRING, firing), Block.UPDATE_CLIENTS);
        }
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    private boolean othersShort(FeWireNetwork net, long now) {
        final FeGridSolver.Result r = net.lastResult();
        if (r == null) {
            return false;
        }
        final BoardFeed feed = boardFeedOf(net, now);
        boolean boardShort = false;
        for (BlockPos at : feed.boards()) {
            if (level.getBlockEntity(at) instanceof PowerDistributionUnitBE board
                    && board.burstReliefFePerTick() > 0) {
                boardShort = true;
                break;
            }
        }
        return StarterPort.feedShort(r.shortfall(), port.unservedAtLastSolve(), feed.intake(), boardShort);
    }

    private BoardFeed boardFeedOf(FeWireNetwork net, long now) {
        for (BoardFeed feed : boardFeeds) {
            if (feed.net() == net && now - feed.tick() < BOARD_SCAN_TICKS) {
                return feed;
            }
        }
        boardFeeds.removeIf(feed -> feed.net() == net || now - feed.tick() >= BOARD_SCAN_TICKS);
        int intake = 0;
        final List<BlockPos> boards = new ArrayList<>(1);
        for (FeGridMember member : net.servedMembers()) {
            final BlockPos at = mountedOn(member);
            if (at == null) {
                continue;
            }
            if (!boards.contains(at)) {
                if (!(level.getBlockEntity(at) instanceof PowerDistributionUnitBE)) {
                    continue;
                }
                boards.add(at);
            }

            intake = com.createstats.power.FeRates.plus(intake, Math.max(0, member.transferRate()));
        }
        final BoardFeed feed = new BoardFeed(net, now, intake, List.copyOf(boards));
        boardFeeds.add(feed);
        return feed;
    }

    @Nullable
    private static BlockPos mountedOn(FeGridMember member) {
        if (member instanceof com.createstats.power.FeAttachment attachment) {
            return attachment.machine();
        }
        if (member instanceof PowerConnectorBE connector) {
            final BlockState state = connector.getBlockState();
            final Direction facing = state.hasProperty(com.createstats.block.PowerConnectorBlock.FACING)
                    ? state.getValue(com.createstats.block.PowerConnectorBlock.FACING) : Direction.UP;
            return connector.getBlockPos().relative(facing.getOpposite());
        }
        return null;
    }

    private boolean gateFor(@Nullable FeWireNetwork net, long now) {
        if (net == null) {
            return StarterPort.chargeGate(false, false, false, false);
        }
        if (net.isPduManaged(now)) {
            final FeManagerView view = net.managerView(now);

            return StarterPort.chargeGate(true, true, view != null && view.holdsCharging(),
                    view != null && view.unserved() > 0, view != null && view.fundsStores());
        }
        return StarterPort.chargeGate(true, false, false, othersShort(net, now));
    }

    @Nullable
    public IEnergyStorage handleFor(@Nullable Direction side) {
        if (side == null || side == glandFace()) {
            return port.face(StarterPort.GLAND);
        }
        return side == lugFace() ? port.face(StarterPort.LUG) : null;
    }

    private List<FeWireNetwork> networks() {
        final List<FeWireNetwork> out = new ArrayList<>(2);
        for (Direction face : new Direction[]{glandFace(), lugFace()}) {
            final FeWireNetwork net = networkAt(face);
            if (net != null && !out.contains(net)) {
                out.add(net);
            }
        }
        return out;
    }

    @Nullable
    private FeWireNetwork networkAt(Direction face) {
        if (level == null) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(worldPosition.relative(face));
        return be instanceof FeGridMember member ? member.network() : null;
    }

    private void clientTick() {
        clientAge++;
        final StarterTrigger.Phase phase = trigger.phase();
        if (phase != StarterTrigger.Phase.IDLE) {
            trigger.restore(phase, trigger.phaseTick() + 1, trigger.shortTicks());
        }
        final boolean counting = phase == StarterTrigger.Phase.IDLE && trigger.shortTicks() > 0
                && mode() == StarterMode.AUTO && port.stored() >= CapacitorDischarge.CAPACITY;
        if (counting && !humPlaying) {
            humPlaying = true;
            com.createstats.client.StarterCapacitorSoundInstance.start(this, false);
        }
        if (phase == StarterTrigger.Phase.FIRING && !whinePlaying) {
            whinePlaying = true;
            com.createstats.client.StarterCapacitorSoundInstance.start(this, true);
        }
        if (phase == StarterTrigger.Phase.FIRING && level != null) {
            final float r = CapacitorDischarge.rampAt(trigger.phaseTick());
            final int n = level.random.nextFloat() < r ? 1 + (int) (2 * r) : 0;

            for (int i = 0; i < n; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.18,
                        worldPosition.getY() + 14.5 / 16.0 + (level.random.nextDouble() - 0.5) * 0.1,
                        worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.3, 0.05 + level.random.nextDouble() * 0.15,
                        (level.random.nextDouble() - 0.5) * 0.3);
            }
        }
    }

    public boolean isHumPlaying() {
        return humPlaying;
    }

    public void setHumPlaying(boolean playing) {
        humPlaying = playing;
    }

    public boolean isWhinePlaying() {
        return whinePlaying;
    }

    public void setWhinePlaying(boolean playing) {
        whinePlaying = playing;
    }

    @Override
    public int potentialFePerTick() {
        return port.firing() ? Math.min(port.allowanceLeft(), port.stored()) : 0;
    }

    @Override
    public int ratedDrawFePerTick() {
        if (!trigger.accepting() || managed.isForcedOff()) {
            return 0;
        }
        return Math.min(CapacitorDischarge.CHARGE_FE_PER_TICK,
                Math.max(0, CapacitorDischarge.CAPACITY - port.stored()));
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
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
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title("Starter Capacitor"));
        final int stored = port.stored();
        tooltip.add(FeGoggle.stored(stored, CapacitorDischarge.CAPACITY));
        final StarterTrigger.Phase phase = trigger.phase();
        switch (phase) {
            case FIRING -> {
                final int t = trigger.phaseTick();
                tooltip.add(FeGoggle.bad(String.format("Discharging  %,d FE/t, %.1f s left",
                        CapacitorDischarge.allowanceAt(t), (CapacitorDischarge.BURST_TICKS - t) / 20.0f)));
                if (dumpedShown > 0) {
                    tooltip.add(FeGoggle.dim(String.format("Burned off in the gap: %,d FE", dumpedShown)));
                }
            }
            case CLOSING -> tooltip.add(FeGoggle.warn("Arm closing"));
            case RELEASING -> tooltip.add(FeGoggle.dim("Arm releasing"));
            default -> {
                if (mode() == StarterMode.HOLD) {
                    tooltip.add(FeGoggle.dim("Holding: charges, never fires"));
                } else if (trigger.shortTicks() > 0 && stored >= CapacitorDischarge.CAPACITY) {
                    tooltip.add(FeGoggle.warn(String.format("Grid short: firing in %.1f s",
                            (StarterTrigger.SUSTAIN_TICKS - trigger.shortTicks()) / 20.0f)));
                } else if (stored >= CapacitorDischarge.CAPACITY) {
                    tooltip.add(FeGoggle.ok(mode() == StarterMode.REDSTONE ? "Ready: fires on a redstone pulse"
                            : "Armed"));
                } else {
                    tooltip.add(FeGoggle.dim("Charging, full in " + FeGoggle.time(CapacitorDischarge.ticksToFull(stored))));
                }
            }
        }
        tooltip.add(FeGoggle.dim("Mode: " + switch (mode()) {
            case AUTO -> "Auto";
            case REDSTONE -> "Redstone";
            case HOLD -> "Hold";
        }));
        if (!onAnyGrid) {
            tooltip.add(FeGoggle.warn("No circuit on either terminal"));
        } else if (reach == Reach.CANNOT_FIRE) {
            tooltip.add(FeGoggle.warn("Behind a Power Limiter: a burst cannot reach the panel"));
        } else if (reach == Reach.CANNOT_CHARGE) {
            tooltip.add(FeGoggle.warn("On a controller input: fires into it, cannot charge there"));
        } else if (onManagedGrid && isPlayerSneaking) {
            tooltip.add(FeGoggle.dim("Managed: fires when the board or controller comes up short"));
        }
        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.dim(String.format("Burst %,d FE/t falling to 0 over %d s",
                    CapacitorDischarge.PEAK_FE_PER_TICK, CapacitorDischarge.BURST_TICKS / 20)));
            tooltip.add(FeGoggle.dim(String.format("Charges %,d FE/t, from surplus only",
                    CapacitorDischarge.CHARGE_FE_PER_TICK)));
        } else {
            tooltip.add(FeGoggle.more());
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Stored", port.stored());
        tag.putInt("Phase", trigger.phase().ordinal());
        tag.putInt("PhaseTick", trigger.phaseTick());
        tag.putInt("ShortTicks", trigger.shortTicks());
        tag.putLong("FiringSince", firingSince);
        tag.putLong("Dumped", port.dissipatedThisBurst());
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        if (clientPacket) {
            tag.putInt("Burning", port.lastDissipated());
            tag.putBoolean("OnGrid", onAnyGrid);
            tag.putBoolean("Managed", onManagedGrid);
            tag.putInt("Reach", reach.ordinal());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        port.setStored(tag.getInt("Stored"));
        final StarterTrigger.Phase[] phases = StarterTrigger.Phase.values();
        final int p = tag.getInt("Phase");
        final StarterTrigger.Phase phase = p >= 0 && p < phases.length ? phases[p] : StarterTrigger.Phase.IDLE;
        trigger.restore(phase, tag.getInt("PhaseTick"), tag.getInt("ShortTicks"));
        firingSince = tag.getLong("FiringSince");
        port.setFiring(phase == StarterTrigger.Phase.FIRING, firingSince);
        managed.load(tag.contains("Priority") ? tag.getInt("Priority") : FePriority.LOW.ordinal(), tag.getInt("Lock"));
        dumpedShown = tag.getLong("Dumped");
        if (clientPacket) {
            clientDissipated = tag.getInt("Burning");
            onAnyGrid = tag.getBoolean("OnGrid");
            onManagedGrid = tag.getBoolean("Managed");
            final int r = tag.getInt("Reach");
            reach = r >= 0 && r < Reach.values().length ? Reach.values()[r] : Reach.OK;
        }
    }
}
