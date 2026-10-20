package com.createstats.blockentity;

import com.createstats.block.DoorOpening;
import com.createstats.block.DoorProximity;
import com.createstats.block.DoorTravel;
import com.createstats.block.PoweredDoorBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PoweredDoorBE extends BlockEntity
        implements FeGridMember, com.createstats.power.FeManagedLoad, SecurityDevice, BoreLinks.Socket,
        com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int MOVE_TICKS = 18;

    public static final int REFUSE_TICKS = 15;

    private static final int IDLE_DRAIN = com.createstats.power.DoorPower.IDLE_DRAIN;
    private static final int CYCLE_COST = com.createstats.power.DoorPower.CYCLE_COST;
    private static final int BUFFER = com.createstats.power.DoorPower.BUFFER;
    private static final int TRANSFER_RATE = com.createstats.power.DoorPower.TRANSFER_RATE;
    private static final int WORKING_RESERVE = com.createstats.power.DoorPower.WORKING_RESERVE;

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int SYNC_STEP = CYCLE_COST / 8;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private int ratedDraw;
    private int measuredDraw;

    private int intake;
    private int lastIntake;

    private int lastSyncedEnergy = Integer.MIN_VALUE;
    private int ticksSinceSync;
    private boolean lastSyncedWired;
    private boolean lastSyncedSuppressed;
    private int lastSyncedCircuit;

    private boolean clientWired;
    private boolean clientSuppressed;
    private int clientCircuitSize;

    private final com.createstats.power.FeManagedState managed =
            new com.createstats.power.FeManagedState();

    {
        managed.setPriority(FePriority.CRITICAL);
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                intake += room;
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
            return BUFFER;
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

    private static final int POLICY_TICKS = 40;

    private static final int PROXIMITY_TICKS = 5;

    private static final int PROXIMITY_HOLD = 30;

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;

    @Nullable
    private SecurityRoster roster;

    private boolean linked;

    private int required = Clearance.DEFAULT_REQUIRED;

    private boolean failSecure = true;

    private int proximity;

    private boolean lockdown;

    private boolean entrance;

    private boolean released;

    private boolean sealed;

    private int sincePolicy = POLICY_TICKS;
    private int sinceProximity;
    private boolean staggered;

    private boolean clientGoverned;

    private boolean clientLinked;

    private boolean autoOpened;
    private long proximityHoldUntil;

    private boolean open;
    private long moveStart = Long.MIN_VALUE;

    private int moveTicks = DoorOpening.SWING.ticks();

    private long refuseAt = Long.MIN_VALUE;

    private long clientCueStamp = Long.MIN_VALUE;
    private int clientCuesFired;
    private boolean clientLoopRunning;

    private DoorOpening style = DoorOpening.SWING;

    private boolean seam;

    private boolean lastPowered;

    public PoweredDoorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public float progress(float partialTick) {
        if (level == null || moveStart == Long.MIN_VALUE) {
            return open ? 1.0f : 0.0f;
        }
        return DoorTravel.openness(level.getGameTime() - moveStart + partialTick, moveTicks, open);
    }

    public boolean settled() {
        return level == null || moveStart == Long.MIN_VALUE
                || DoorTravel.settled(level.getGameTime(), moveStart, moveTicks);
    }

    public int moveTicks() {
        return moveTicks;
    }

    public int elapsedTicks() {
        if (level == null || moveStart == Long.MIN_VALUE) {
            return moveTicks;
        }
        return (int) Mth.clamp(level.getGameTime() - moveStart, 0L, (long) moveTicks);
    }

    public int remainingTicks() {
        if (level == null || moveStart == Long.MIN_VALUE) {
            return 0;
        }
        return DoorTravel.remaining(level.getGameTime(), moveStart, moveTicks);
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean value) {
        if (open == value) {
            return;
        }
        final float at = progress(0.0f);
        open = value;
        if (level != null) {
            moveTicks = Math.max(1, effectiveStyle().ticks());
            moveStart = DoorTravel.resumeStart(level.getGameTime(), moveTicks, at, value);
        }
        sync();
    }

    public void markRefused() {
        if (level == null || level.isClientSide()) {
            return;
        }
        refuseAt = level.getGameTime();
        sync();
    }

    public float refusedFor(float partialTick) {
        if (level == null || refuseAt == Long.MIN_VALUE) {
            return -1.0f;
        }
        final float elapsed = level.getGameTime() - refuseAt + partialTick;
        if (elapsed < 0.0f || elapsed >= REFUSE_TICKS) {
            return -1.0f;
        }
        return elapsed / REFUSE_TICKS;
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return IDLE_DRAIN;
    }

    public static int cycleCost() {
        return CYCLE_COST;
    }

    public int storedFe() {
        return energy;
    }

    public int capacityFe() {
        return BUFFER;
    }

    public boolean hasPower() {
        return !managed.isSuppressed() && energy >= CYCLE_COST;
    }

    public boolean spendCycle() {
        if (managed.isSuppressed()) {
            return false;
        }
        if (energy >= CYCLE_COST) {
            energy -= CYCLE_COST;
            measuredDraw += CYCLE_COST;
            setChanged();
            return true;
        }
        return false;
    }

    public boolean mayReleaseByHand(@Nullable Player player) {
        if (released) {
            return !entrance;
        }

        if (sealed) {
            return isOwner(player);
        }
        if (lockdown) {
            return false;
        }
        return !governed() || !failSecure;
    }

    public boolean mayReleaseByHand() {
        if (released) {
            return !entrance;
        }
        if (sealed) {
            return false;
        }
        if (lockdown) {
            return false;
        }
        return !governed() || !failSecure;
    }

    private int intendedDraw() {
        return com.createstats.power.DoorPower.ratedDrawFor(energy);
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
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
    public com.createstats.power.FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(com.createstats.power.FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.DOOR;
    }

    @Override
    public DeviceState deviceState() {
        if (open) {
            return DeviceState.ACTIVE;
        }
        return hasPower() ? DeviceState.IDLE : DeviceState.UNPOWERED;
    }

    @Override
    public float integrity() {
        return -1.0f;
    }

    public boolean governed() {
        return level != null && level.isClientSide() ? clientGoverned : roster != null;
    }

    public boolean linkedHere() {
        return level != null && level.isClientSide() ? clientLinked : linked;
    }

    public int requiredLevel() {
        return required;
    }

    public int proximityRadius() {
        return proximity;
    }

    public boolean failSecure() {
        return failSecure;
    }

    @Nullable
    public BlockPos panelPos() {
        return panelPos;
    }

    @Override
    @Nullable
    public BlockPos governingPanel() {
        return panelPos;
    }

    public boolean mayOperate(@Nullable Player player) {
        if (roster == null) {
            return true;
        }

        if (released) {
            return !entrance || isOwner(player);
        }

        if (sealed) {
            return isOwner(player);
        }

        if (lockdown) {
            return entrance && isOwner(player);
        }
        return player != null && roster.passes(player.getUUID(), required);
    }

    private boolean isOwner(@Nullable Player player) {
        return player != null && roster != null && roster.isOwner(player.getUUID());
    }

    public boolean lockedDown() {
        return lockdown;
    }

    public boolean released() {
        return released;
    }

    public boolean isEntrance() {
        return entrance;
    }

    public void noteAccess(Player player, boolean passed) {
        final HomeSecurityPanelBE panel = panel();
        if (panel != null) {
            panel.recordAccess(player, worldPosition, deviceName().getString(), required, passed);
        }
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        return level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE panel
                && com.createstats.security.SecurityIdentity.accepts(level, panelHousehold, worldPosition, panel)
                ? panel : null;
    }

    private void refreshPolicy() {
        if (level == null) {
            return;
        }

        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(panel(),
                panelPos != null, panelPos != null && level.isLoaded(panelPos),
                network != null || offeredPanel != null, this::searchCircuit);
        if (contact.state() == SecurityLink.UNKNOWN) {
            return;
        }
        if (contact.state().isLost()) {
            loseLink();
            return;
        }
        final HomeSecurityPanelBE found = contact.panel();
        if (found == null) {
            return;
        }
        panelPos = found.getBlockPos();
        panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        offeredPanel = null;
        adopt(found);
    }

    private void loseLink() {
        panelPos = null;
        if (!linked && !lockdown && !released) {
            return;
        }
        linked = false;

        lockdown = false;
        released = false;
        setChanged();
        sync();
    }

    private void adopt(HomeSecurityPanelBE from) {
        final boolean wasLinked = linked;
        linked = true;
        final int wasRequired = required;
        final int wasProximity = proximity;
        final boolean wasSecure = failSecure;
        final boolean wasGoverned = roster != null;
        final boolean wasLocked = lockdown;
        final boolean wasEntrance = entrance;
        final boolean wasReleased = released;
        final boolean wasSealed = sealed;
        roster = from.rosterSnapshot();
        required = from.requiredLevel(worldPosition);
        failSecure = from.failSecure();
        proximity = from.proximity(worldPosition);
        lockdown = from.lockedDown();
        entrance = from.isEntrance(worldPosition);
        released = from.released();
        sealed = from.houseSealed();
        setChanged();

        if (((lockdown && !wasLocked) || (sealed && !wasSealed)) && open && !released) {
            PoweredDoorBlock.driveProximity(level, worldPosition, false);
        }

        if (!wasGoverned || !wasLinked || wasRequired != required || wasProximity != proximity
                || wasSecure != failSecure || wasLocked != lockdown
                || wasEntrance != entrance || wasReleased != released || wasSealed != sealed) {
            sync();
        }
    }

    @Nullable
    private HomeSecurityPanelBE searchCircuit() {
        final HomeSecurityPanelBE wired = com.createstats.security.PanelSweep.nearest(level, network,
                worldPosition, com.createstats.security.SecuritySegment.policyAnchor(level, worldPosition),
                panelHousehold);
        return wired != null ? wired : com.createstats.security.SecurityIdentity.offered(level, offeredPanel,
                worldPosition, panelHousehold);
    }

    // distance filter 4x a second, not a hitbox scan every tick in every door
    private void tickProximity() {
        // same AWAY+ entrance rule as the garage

        if (level == null || proximity <= 0 || (lockdown && !released)
                || (sealed && !released && !entrance)) {
            return;
        }
        if (++sinceProximity < PROXIMITY_TICKS) {
            return;
        }
        sinceProximity = 0;
        final BlockPos partner = PoweredDoorBlock.partnerOf(level, worldPosition, getBlockState());

        final BlockPos across = partner == null ? worldPosition : partner;
        final double cx = DoorProximity.axisCentre(worldPosition.getX(), across.getX());
        final double cy = worldPosition.getY() + 1.0;
        final double cz = DoorProximity.axisCentre(worldPosition.getZ(), across.getZ());
        boolean wanted = false;
        for (Player player : level.players()) {
            if (player.isSpectator() || !DoorProximity.inRange(
                    player.getX(), player.getY(), player.getZ(), cx, cy, cz, proximity)) {
                continue;
            }
            if (mayOperate(player)) {
                wanted = true;
                break;
            }
        }
        final long now = level.getGameTime();
        if (wanted) {
            proximityHoldUntil = now + PROXIMITY_HOLD;
            if (!open && hasPower()) {
                PoweredDoorBlock.driveProximity(level, worldPosition, true);
            }
        } else if (autoOpened && open && now >= proximityHoldUntil && settled()
                && !partnerHoldsOpen(partner)) {
            PoweredDoorBlock.driveProximity(level, worldPosition, false);
        }
    }

    private boolean partnerHoldsOpen(@Nullable BlockPos partner) {
        if (level == null || partner == null || !level.isLoaded(partner)) {
            return false;
        }
        return level.getBlockEntity(partner) instanceof PoweredDoorBE other
                && other.proximityWantsOpen(level.getGameTime());
    }

    private boolean proximityWantsOpen(long now) {
        return proximity > 0 && now < proximityHoldUntil;
    }

    public void repoll() {
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (panelPos == null && panel != null && !panel.equals(offeredPanel)) {
            offeredPanel = panel.immutable();
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public void releasePanel(BlockPos panel) {
        if (panel == null) {
            return;
        }
        if (panel.equals(offeredPanel)) {
            offeredPanel = null;
        }
        if (panel.equals(panelPos)) {
            panelPos = null;
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    @Nullable
    public java.util.UUID governingHousehold() {
        return panelHousehold;
    }

    public void clearAutoOpen() {
        autoOpened = false;
    }

    public void markAutoOpen() {
        autoOpened = true;
    }

    public void clientTick() {
        if (level == null || !level.isClientSide()) {
            return;
        }
        if (settled() && refusedFor(0.0f) < 0.0f) {
            return;
        }
        com.createstats.client.PoweredDoorEffects.tick(this);
    }

    public boolean claimCue(int cue) {
        if (clientCueStamp != moveStart) {
            clientCueStamp = moveStart;
            clientCuesFired = 0;
        }
        final int bit = 1 << cue;
        if ((clientCuesFired & bit) != 0) {
            return false;
        }
        clientCuesFired |= bit;
        return true;
    }

    public boolean loopRunning() {
        return clientLoopRunning;
    }

    public void setLoopRunning(boolean running) {
        this.clientLoopRunning = running;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        lastIntake = intake;
        intake = 0;
        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        ratedDraw = managed.isForcedOff() ? 0 : intendedDraw();
        measuredDraw = 0;
        if (!managed.isSuppressed() && energy >= IDLE_DRAIN) {
            energy -= IDLE_DRAIN;
            measuredDraw = IDLE_DRAIN;
            setChanged();
        }

        if (++sincePolicy >= POLICY_TICKS) {
            sincePolicy = staggered ? 0 : (int) Math.floorMod(worldPosition.asLong(), POLICY_TICKS);
            staggered = true;
            refreshPolicy();
        }
        tickProximity();
        pushReadout();
    }

    private void pushReadout() {
        if (ticksSinceSync < SYNC_INTERVAL_TICKS) {
            ticksSinceSync++;
            return;
        }
        final boolean wired = network != null;
        final boolean suppressed = managed.isSuppressed();
        final int circuit = wired ? network.size() : 0;
        final boolean moved = lastSyncedEnergy == Integer.MIN_VALUE
                || Math.abs(energy - lastSyncedEnergy) >= SYNC_STEP
                || (energy >= CYCLE_COST) != (lastSyncedEnergy >= CYCLE_COST)
                || wired != lastSyncedWired
                || suppressed != lastSyncedSuppressed
                || circuit != lastSyncedCircuit;
        if (!moved) {
            return;
        }
        ticksSinceSync = 0;
        lastSyncedEnergy = energy;
        lastSyncedWired = wired;
        lastSyncedSuppressed = suppressed;
        lastSyncedCircuit = circuit;
        sync();
    }

    public boolean wasPowered() {
        return lastPowered;
    }

    public boolean updatePowered(boolean powered) {
        if (lastPowered == powered) {
            return false;
        }
        lastPowered = powered;
        setChanged();
        return true;
    }

    public DoorOpening style() {
        return style;
    }

    public boolean paired() {
        return level != null && PoweredDoorBlock.isPaired(level, worldPosition, getBlockState());
    }

    public DoorOpening effectiveStyle() {
        return PoweredDoorBlock.offeredBy(getBlockState(), style).effective(paired());
    }

    public boolean seamDrawn() {
        return seam && paired();
    }

    public boolean hasSeam() {
        return seam;
    }

    public void setSeam(boolean value) {
        if (seam == value) {
            return;
        }
        seam = value;
        sync();
    }

    public DoorOpening cycleStyle(boolean paired, java.util.Set<DoorOpening> offered) {
        style = style.next(paired, offered);
        sync();
        return style;
    }

    public void setStyle(DoorOpening value) {
        if (style == value) {
            return;
        }
        style = value;
        sync();
    }

    private boolean suppressedHere() {
        return level != null && level.isClientSide() ? clientSuppressed : managed.isSuppressed();
    }

    private boolean wiredHere() {
        return level != null && level.isClientSide() ? clientWired : network != null;
    }

    private int circuitSizeHere() {
        if (level != null && level.isClientSide()) {
            return clientCircuitSize;
        }
        return network == null ? 0 : network.size();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(getBlockState().getBlock().getName()));
        if (suppressedHere()) {
            tooltip.add(FeGoggle.bad("Switched off from a distribution panel"));
        } else if (!hasPower()) {
            tooltip.add(FeGoggle.bad(sealed ? "No power, owner hand release only"
                    : mayReleaseByHand() ? "No power, hand release only" : "No power, locked"));
        } else if (open) {
            tooltip.add(FeGoggle.ok("Open"));
        } else {
            tooltip.add(FeGoggle.ok("Shut"));
        }
        tooltip.add(FeGoggle.stored(energy, BUFFER));

        tooltip.add(FeGoggle.supplyDemand(lastIntake, intendedDraw()));
        if (!wiredHere()) {
            tooltip.add(FeGoggle.bad("Not on a circuit"));
        } else {
            if (lastIntake <= 0 && energy < WORKING_RESERVE) {
                tooltip.add(FeGoggle.bad("On a circuit, but nothing is feeding it"));
            }
            tooltip.add(FeGoggle.dim("Circuit: " + circuitSizeHere() + " blocks"));
        }
        if (!governed()) {
            tooltip.add(FeGoggle.dim("No security panel on this circuit"));
        } else if (!linkedHere()) {
            tooltip.add(FeGoggle.warn("Security panel lost, holding the last clearance"));
        } else if (released) {
            tooltip.add(FeGoggle.warn(entrance
                    ? "Fire release, entrance still owner only" : "Fire release, open to all"));
        } else if (sealed) {
            tooltip.add(FeGoggle.bad(entrance
                    ? "AWAY+, entrance, opens for the owner"
                    : "AWAY+, owner only, on a click"));
        } else if (lockdown) {
            tooltip.add(FeGoggle.bad(entrance ? "Lockdown, entrance, owner only" : "Lockdown, sealed"));
        } else {
            tooltip.add(FeGoggle.dim("Needs " + Clearance.badge(required).getString()));
            if (entrance) {
                tooltip.add(FeGoggle.dim("Entrance: the way back in during a lockdown"));
            }
            if (proximity > 0) {
                tooltip.add(FeGoggle.dim("Opens within " + proximity + " blocks"));
            }
        }
        tooltip.add(FeGoggle.dim("Opening: " + effectiveStyle().key()));
        return true;
    }

    private final List<BlockPos> bores = new java.util.ArrayList<>(1);

    @Override
    public BlockPos borePosition() {
        return worldPosition;
    }

    @Override
    public BlockPos boreAnchorFor(BlockPos far) {
        return BoreLinks.anchorFor(far, worldPosition, worldPosition.above());
    }

    @Override
    public List<BlockPos> bores() {
        return bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (bores.contains(far)) {
            return false;
        }
        bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
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
        return TRANSFER_RATE;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    @javax.annotation.Nullable
    public FeWireNetwork network() {
        return this.network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
        sincePolicy = POLICY_TICKS;

        lastSyncedEnergy = Integer.MIN_VALUE;
        ticksSinceSync = SYNC_INTERVAL_TICKS;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Open", open);
        tag.putLong("MoveStart", moveStart);
        tag.putInt("MoveTicks", moveTicks);
        tag.putString("Style", style.name());
        tag.putBoolean("Seam", seam);
        tag.putBoolean("Powered", lastPowered);
        tag.putInt("Energy", energy);
        tag.putInt("Required", required);
        tag.putBoolean("FailSecure", failSecure);
        tag.putInt("Proximity", proximity);

        tag.putBoolean("Lockdown", lockdown);
        tag.putBoolean("Entrance", entrance);
        tag.putBoolean("Released", released);
        tag.putBoolean("Sealed", sealed);
        tag.putBoolean("Linked", linked);
        tag.putBoolean("AutoOpened", autoOpened);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        BoreLinks.save(tag, bores);
        if (panelPos != null) {
            tag.putLong("Panel", panelPos.asLong());
        }
        if (panelHousehold != null) {
            tag.putUUID("PanelHousehold", panelHousehold);
        }

        if (roster != null) {
            tag.put("Roster", roster.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("Open");
        moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;

        moveTicks = Math.max(1, tag.contains("MoveTicks") ? tag.getInt("MoveTicks") : MOVE_TICKS);
        style = DoorOpening.byName(tag.getString("Style"));
        seam = tag.getBoolean("Seam");
        lastPowered = tag.getBoolean("Powered");
        energy = Math.min(BUFFER, tag.getInt("Energy"));
        required = Clearance.clamp(tag.contains("Required")
                ? tag.getInt("Required") : Clearance.DEFAULT_REQUIRED);
        failSecure = !tag.contains("FailSecure") || tag.getBoolean("FailSecure");
        proximity = tag.getInt("Proximity");
        lockdown = tag.getBoolean("Lockdown");
        entrance = tag.getBoolean("Entrance");
        released = tag.getBoolean("Released");

        sealed = tag.getBoolean("Sealed");
        linked = tag.getBoolean("Linked");
        autoOpened = tag.getBoolean("AutoOpened");

        if (tag.contains("Tier")) {
            managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        bores.clear();
        bores.addAll(BoreLinks.load(tag));
        panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        if (tag.contains("Roster")) {
            final SecurityRoster loaded = new SecurityRoster();
            loaded.load(tag.getCompound("Roster"));
            roster = loaded;
        } else {
            roster = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Open", open);
        tag.putLong("MoveStart", moveStart);
        tag.putInt("MoveTicks", moveTicks);
        tag.putLong("RefuseAt", refuseAt);
        tag.putString("Style", style.name());
        tag.putBoolean("Seam", seam);
        tag.putInt("Energy", energy);

        tag.putBoolean("Governed", roster != null);
        tag.putInt("Required", required);
        tag.putInt("Proximity", proximity);
        tag.putBoolean("FailSecure", failSecure);

        tag.putBoolean("Lockdown", lockdown);
        tag.putBoolean("Entrance", entrance);
        tag.putBoolean("Released", released);
        tag.putBoolean("Sealed", sealed);

        tag.putBoolean("Linked", linked);

        tag.putInt("Intake", lastIntake);
        tag.putBoolean("Wired", network != null);
        tag.putBoolean("Suppressed", managed.isSuppressed());
        tag.putInt("Circuit", network == null ? 0 : network.size());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        open = tag.getBoolean("Open");
        moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;

        moveTicks = Math.max(1, tag.contains("MoveTicks") ? tag.getInt("MoveTicks") : MOVE_TICKS);
        style = DoorOpening.byName(tag.getString("Style"));
        seam = tag.getBoolean("Seam");
        energy = Math.min(BUFFER, tag.getInt("Energy"));
        required = Clearance.clamp(tag.getInt("Required"));
        proximity = tag.getInt("Proximity");
        failSecure = tag.getBoolean("FailSecure");
        lockdown = tag.getBoolean("Lockdown");
        entrance = tag.getBoolean("Entrance");
        released = tag.getBoolean("Released");
        sealed = tag.getBoolean("Sealed");
        clientLinked = tag.getBoolean("Linked");

        refuseAt = tag.contains("RefuseAt") ? tag.getLong("RefuseAt") : Long.MIN_VALUE;
        clientGoverned = tag.getBoolean("Governed");
        lastIntake = tag.getInt("Intake");
        clientWired = tag.getBoolean("Wired");
        clientSuppressed = tag.getBoolean("Suppressed");
        clientCircuitSize = tag.getInt("Circuit");
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
