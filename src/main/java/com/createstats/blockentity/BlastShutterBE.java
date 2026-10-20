package com.createstats.blockentity;

import com.createstats.block.BlastShutterBlock;
import com.createstats.block.DoorTravel;
import com.createstats.block.ShutterTier;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SecurityDevice;
import com.createstats.power.ShutterPower;
import com.createstats.security.SecurityLink;
import com.createstats.security.ShutterBank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlastShutterBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        SecurityDevice, BoreLinks.Socket, com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int POLICY_TICKS = 40;

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int SYNC_STEP = ShutterPower.CYCLE_COST / 8;

    public static final long DUSK = 13000L;
    public static final long DAWN = 23000L;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
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
    private boolean clientPowered;
    private boolean clientGoverned;

    private final List<BlockPos> bores = new java.util.ArrayList<>(1);

    private boolean sealed;
    private long moveStart = Long.MIN_VALUE;
    private int moveTicks = ShutterTier.WINDOW.travelTicks();

    // stored not walked, the renderer asks every frame (256 lookups for a 32 wide bank)
    private int columnHeight = com.createstats.block.BlastShutterBlock.MIN_HEIGHT;

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;
    private boolean governed;
    private boolean lockdown;
    private boolean released;

    private boolean commanded;

    private boolean houseSealed;

    private boolean dusk;

    private int sincePolicy = POLICY_TICKS;
    private boolean staggered;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, capacityFe() - energy);
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
            return capacityFe();
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

    public BlastShutterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.managed.setPriority(defaultTier());
        this.moveTicks = tier().travelTicksFor(this.columnHeight);
    }

    public static FePriority defaultTier() {
        return FePriority.HIGH;
    }

    public IEnergyStorage energyHandle() {
        return this.energyView;
    }

    public static int drawPerTick() {
        return ShutterPower.IDLE_DRAIN;
    }

    public static int cycleCost() {
        return ShutterPower.CYCLE_COST;
    }

    public ShutterTier tier() {
        final ShutterTier fromBlock = BlastShutterBlock.tierOf(getBlockState());
        return fromBlock == null ? ShutterTier.WINDOW : fromBlock;
    }

    public boolean isSealed() {
        return this.sealed;
    }

    public float progress(float partialTick) {
        if (this.level == null || this.moveStart == Long.MIN_VALUE) {
            return this.sealed ? 1.0f : 0.0f;
        }
        return DoorTravel.openness(this.level.getGameTime() - this.moveStart + partialTick,
                this.moveTicks, this.sealed);
    }

    public boolean settled() {
        return this.level == null || this.moveStart == Long.MIN_VALUE
                || DoorTravel.settled(this.level.getGameTime(), this.moveStart, this.moveTicks);
    }

    public int remainingTicks() {
        if (this.level == null || this.moveStart == Long.MIN_VALUE) {
            return 0;
        }
        return DoorTravel.remaining(this.level.getGameTime(), this.moveStart, this.moveTicks);
    }

    public int moveTicks() {
        return this.moveTicks;
    }

    public int columnHeight() {
        return this.columnHeight;
    }

    public List<BlockPos> columnPositions() {
        final List<BlockPos> out = new java.util.ArrayList<>(this.columnHeight);
        for (int step = 0; step < Math.max(1, this.columnHeight); step++) {
            out.add(this.worldPosition.above(step));
        }
        return out;
    }

    // resume progress, don't restart the move
    public void setColumnHeight(int height) {
        final int clamped = clampHeight(height);
        if (clamped == this.columnHeight) {
            return;
        }
        final float at = progress(0.0f);
        this.columnHeight = clamped;
        this.moveTicks = Math.max(1, tier().travelTicksFor(clamped));
        if (this.level != null && this.moveStart != Long.MIN_VALUE) {
            this.moveStart = DoorTravel.resumeStart(this.level.getGameTime(), this.moveTicks, at,
                    this.sealed);
        }
        this.energy = Math.min(this.energy, capacityFe());
        invalidateNetwork();
        sync();
    }

    private static int clampHeight(int height) {
        return Math.max(com.createstats.block.BlastShutterBlock.MIN_HEIGHT,
                Math.min(com.createstats.block.BlastShutterBlock.MAX_HEIGHT, height));
    }

    private void refreshColumnHeight() {
        if (this.level == null) {
            return;
        }
        final int measured = com.createstats.block.BlastShutterBlock.heightOf(this.level,
                this.worldPosition);
        if (measured >= com.createstats.block.BlastShutterBlock.MIN_HEIGHT) {
            setColumnHeight(measured);
        }
    }

    public void adoptFrom(BlastShutterBE source) {
        this.energy = source.energy;
        this.managed.load(source.managed.priorityOrdinal(), source.managed.lockOrdinal());
        this.sealed = source.sealed;
        this.moveStart = source.moveStart;
        this.moveTicks = source.moveTicks;
        this.columnHeight = source.columnHeight;
        this.governed = source.governed;
        this.lockdown = source.lockdown;
        this.released = source.released;
        this.commanded = source.commanded;
        this.dusk = source.dusk;
        this.panelPos = source.panelPos;

        this.panelHousehold = source.panelHousehold;
        this.bores.clear();
        this.bores.addAll(source.bores);

        this.sincePolicy = POLICY_TICKS;
        this.staggered = false;
        invalidateNetwork();
        sync();
    }

    public void setSealed(boolean value) {
        if (this.sealed == value) {
            return;
        }
        final float at = progress(0.0f);
        this.sealed = value;
        if (this.level != null) {
            this.moveTicks = Math.max(1, tier().travelTicksFor(this.columnHeight));
            this.moveStart = DoorTravel.resumeStart(this.level.getGameTime(), this.moveTicks, at, value);
        }
        sync();
    }

    public boolean hasPower() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientPowered;
        }
        return !this.managed.isSuppressed() && ShutterPower.canAffordCycle(this.energy, this.columnHeight);
    }

    public boolean canAffordCycle() {
        return !this.managed.isSuppressed() && ShutterPower.canAffordCycle(this.energy, this.columnHeight);
    }

    public boolean spendCycle() {
        if (!canAffordCycle()) {
            return false;
        }
        this.energy -= ShutterPower.cycleCostFor(this.columnHeight);
        this.measuredDraw += ShutterPower.cycleCostFor(this.columnHeight);
        setChanged();
        return true;
    }

    public int storedFe() {
        return this.energy;
    }

    public int capacityFe() {
        return ShutterPower.bufferFor(this.columnHeight);
    }

    public boolean governed() {
        return this.level != null && this.level.isClientSide() ? this.clientGoverned : this.governed;
    }

    public boolean lockedDown() {
        return this.lockdown;
    }

    public boolean released() {
        return this.released;
    }

    public boolean commanded() {
        return this.commanded;
    }

    public boolean commandedFlag() {
        return this.commanded;
    }

    public boolean duskFlag() {
        return this.dusk;
    }

    public void repoll() {
        this.sincePolicy = POLICY_TICKS;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (this.panelPos == null && panel != null && !panel.equals(this.offeredPanel)) {
            this.offeredPanel = panel.immutable();
            this.sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public void releasePanel(BlockPos panel) {
        if (panel == null) {
            return;
        }
        if (panel.equals(this.offeredPanel)) {
            this.offeredPanel = null;
        }
        if (panel.equals(this.panelPos)) {
            this.panelPos = null;
            this.sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    @Nullable
    public java.util.UUID governingHousehold() {
        return this.panelHousehold;
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (this.level == null || this.panelPos == null || !this.level.isLoaded(this.panelPos)) {
            return null;
        }
        return this.level.getBlockEntity(this.panelPos) instanceof HomeSecurityPanelBE panel
                && com.createstats.security.SecurityIdentity.accepts(this.level, this.panelHousehold,
                this.worldPosition, panel) ? panel : null;
    }

    private void refreshPolicy() {
        if (this.level == null) {
            return;
        }
        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(panel(),
                this.panelPos != null,
                this.panelPos != null && this.level.isLoaded(this.panelPos),
                this.network != null || this.offeredPanel != null, this::searchCircuit);
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
        this.panelPos = found.getBlockPos();
        this.panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        this.offeredPanel = null;
        final boolean wasGoverned = this.governed;
        final boolean wasLockdown = this.lockdown;
        final boolean wasReleased = this.released;
        final boolean wasCommanded = this.commanded;
        final boolean wasHouseSealed = this.houseSealed;
        final boolean wasDusk = this.dusk;

        final BlockPos anchor = ShutterBank.anchorOf(this.level, this.worldPosition, getBlockState());
        this.governed = true;
        this.lockdown = found.lockedDown();
        this.released = found.released();
        this.commanded = found.shutterCommanded(anchor);
        this.houseSealed = found.houseSealed();
        this.dusk = found.shutterDusk(anchor);
        setChanged();
        if (!wasGoverned || wasLockdown != this.lockdown || wasReleased != this.released
                || wasCommanded != this.commanded || wasHouseSealed != this.houseSealed
                || wasDusk != this.dusk) {
            sync();
        }
    }

    private void loseLink() {
        this.panelPos = null;
        if (!this.governed && !this.lockdown && !this.released) {
            return;
        }
        this.governed = false;
        this.lockdown = false;
        this.released = false;
        setChanged();
        sync();
    }

    @Nullable
    private HomeSecurityPanelBE searchCircuit() {
        final HomeSecurityPanelBE wired = com.createstats.security.PanelSweep.nearest(this.level,
                this.network, this.worldPosition,
                com.createstats.security.SecuritySegment.policyAnchor(this.level, this.worldPosition),
                this.panelHousehold);
        return wired != null ? wired : com.createstats.security.SecurityIdentity.offered(this.level,
                this.offeredPanel, this.worldPosition, this.panelHousehold);
    }

    public boolean wantsSealed() {
        return com.createstats.security.ShutterSeal.wantsSealed(this.released, this.houseSealed,
                this.lockdown, this.commanded, this.dusk && isNight());
    }

    public boolean isNight() {
        if (this.level == null) {
            return false;
        }
        final long time = this.level.getDayTime() % 24000L;
        return time >= DUSK && time < DAWN;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.lastIntake = this.intake;
        this.intake = 0;

        if (!this.staggered) {
            this.staggered = true;
            this.sincePolicy = POLICY_TICKS
                    - (int) Math.floorMod(this.worldPosition.asLong(), POLICY_TICKS);
        }

        if (this.network == null || this.network.isDirty()) {
            if (BoreLinks.prune(this.level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (this.network != null) {
            this.network.solveOnce(this.level);
        }
        if (this.network == null || !this.network.isPduManaged(this.level.getGameTime())) {
            setManagedShed(false);
        }

        this.ratedDraw = this.managed.isForcedOff() ? 0 : ShutterPower.ratedDrawFor(this.energy, this.columnHeight);
        this.measuredDraw = 0;
        if (!this.managed.isSuppressed() && this.energy >= ShutterPower.IDLE_DRAIN) {
            this.energy -= ShutterPower.IDLE_DRAIN;
            this.measuredDraw = ShutterPower.IDLE_DRAIN;
            setChanged();
        }

        if (++this.sincePolicy >= POLICY_TICKS) {
            this.sincePolicy = 0;
            refreshPolicy();
            refreshColumnHeight();
            reconcileCollision();
            driveBankIfAnchor();
        }

        pushReadout();
    }

    private void reconcileCollision() {
        if (this.level == null || !settled()) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BlastShutterBlock.SEALED)
                && state.getValue(BlastShutterBlock.SEALED) != this.sealed) {
            BlastShutterBlock.settleCollision(this.level, this.worldPosition, this.sealed);
        }
    }

    private void driveBankIfAnchor() {
        if (this.level == null || !this.governed) {
            return;
        }
        if (!ShutterBank.isAnchor(this.level, this.worldPosition, getBlockState())) {
            return;
        }
        final boolean want = wantsSealed();
        if (want == this.sealed || !settled()) {
            return;
        }
        BlastShutterBlock.driveBank(this.level, this.worldPosition, want);
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.SHUTTER;
    }

    @Override
    public DeviceState deviceState() {
        if (this.sealed) {
            return DeviceState.ACTIVE;
        }
        return hasPower() ? DeviceState.IDLE : DeviceState.UNPOWERED;
    }

    @Override
    public float integrity() {
        return tier().integrity();
    }

    @Override
    public Component deviceName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public long nodeKey() {
        return this.worldPosition.asLong();
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
        return ShutterPower.TRANSFER_RATE;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return this.energyView;
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
    @Nullable
    public FeWireNetwork network() {
        return this.network;
    }

    @Override
    public void invalidateNetwork() {
        if (this.network != null) {
            this.network.markDirty();
            this.network = null;
        }
        this.sincePolicy = POLICY_TICKS;
        this.lastSyncedEnergy = Integer.MIN_VALUE;
        this.ticksSinceSync = SYNC_INTERVAL_TICKS;
    }

    @Override
    public BlockPos borePosition() {
        return this.worldPosition;
    }

    @Override
    public BlockPos boreAnchorFor(BlockPos far) {
        BlockPos best = this.worldPosition;
        for (BlockPos at : columnPositions()) {
            best = BoreLinks.anchorFor(far, best, at);
        }
        return best;
    }

    @Override
    public List<BlockPos> bores() {
        return this.bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (this.bores.contains(far)) {
            return false;
        }
        this.bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return this.bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        return this.ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.measuredDraw;
    }

    @Override
    public FePriority managedPriority() {
        return this.managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        this.managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return this.managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        this.managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (this.managed.isShed() == shed) {
            return;
        }
        this.managed.setShed(shed);
        setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return this.managed.isShed();
    }

    private boolean suppressedHere() {
        return this.level != null && this.level.isClientSide()
                ? this.clientSuppressed : this.managed.isSuppressed();
    }

    private boolean wiredHere() {
        return this.level != null && this.level.isClientSide()
                ? this.clientWired : this.network != null;
    }

    private int circuitSizeHere() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientCircuitSize;
        }
        return this.network == null ? 0 : this.network.size();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(getBlockState().getBlock().getName()));
        if (suppressedHere()) {
            tooltip.add(FeGoggle.bad("Switched off from a distribution panel"));
        } else if (this.sealed) {
            tooltip.add(FeGoggle.ok("Sealed"));
        } else if (!hasPower()) {
            tooltip.add(FeGoggle.bad("No power, holding position"));
        } else {
            tooltip.add(FeGoggle.ok("Open"));
        }
        tooltip.add(FeGoggle.stored(this.energy, capacityFe()));
        tooltip.add(FeGoggle.supplyDemand(this.lastIntake, ShutterPower.ratedDrawFor(this.energy, this.columnHeight)));
        if (!wiredHere()) {
            tooltip.add(FeGoggle.bad("Not on a circuit"));
        } else {
            tooltip.add(FeGoggle.dim("Circuit: " + circuitSizeHere() + " blocks"));
        }
        if (!governed()) {
            tooltip.add(this.commanded
                    ? FeGoggle.warn("No security panel, holding sealed on the last order")
                    : FeGoggle.dim("No security panel on this circuit, nothing drives it"));
        } else if (this.released) {
            tooltip.add(FeGoggle.warn("Fire release, held open"));
        } else if (this.houseSealed) {
            tooltip.add(FeGoggle.bad("AWAY+, sealed until the mode stands down"));
        } else if (this.lockdown) {
            tooltip.add(FeGoggle.bad("Lockdown, sealed"));
        } else if (this.dusk) {
            tooltip.add(FeGoggle.dim("Closes at dusk, opens at dawn"));
        }

        if (this.level != null) {
            final int width = ShutterBank.membersOf(this.level, this.worldPosition,
                    getBlockState()).size();
            tooltip.add(FeGoggle.dim((width <= 1 ? "Single unit" : "Bank of " + width)
                    + ", " + this.columnHeight + " blocks tall"));
        }
        return true;
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.ticksSinceSync = 0;
            this.lastSyncedEnergy = this.energy;
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void pushReadout() {
        final boolean wired = this.network != null;
        final boolean suppressed = this.managed.isSuppressed();
        final int circuit = wired ? this.network.size() : 0;
        final boolean crossed = this.lastSyncedEnergy == Integer.MIN_VALUE
                || ShutterPower.canAffordCycle(this.energy, this.columnHeight)
                        != ShutterPower.canAffordCycle(this.lastSyncedEnergy, this.columnHeight)
                || wired != this.lastSyncedWired
                || suppressed != this.lastSyncedSuppressed
                || circuit != this.lastSyncedCircuit;
        if (!crossed) {
            if (this.ticksSinceSync < SYNC_INTERVAL_TICKS) {
                this.ticksSinceSync++;
                return;
            }
            if (Math.abs(this.energy - this.lastSyncedEnergy) < SYNC_STEP) {
                return;
            }
        }
        this.lastSyncedWired = wired;
        this.lastSyncedSuppressed = suppressed;
        this.lastSyncedCircuit = circuit;
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", this.energy);
        tag.putInt("Tier", this.managed.priorityOrdinal());
        tag.putInt("Lock", this.managed.lockOrdinal());
        tag.putBoolean("Sealed", this.sealed);
        tag.putLong("MoveStart", this.moveStart);
        tag.putInt("MoveTicks", this.moveTicks);
        tag.putInt("Height", this.columnHeight);

        tag.putBoolean("Governed", this.governed);
        tag.putBoolean("Lockdown", this.lockdown);
        tag.putBoolean("Released", this.released);
        tag.putBoolean("Commanded", this.commanded);
        tag.putBoolean("HouseSealed", this.houseSealed);
        tag.putBoolean("Dusk", this.dusk);
        BoreLinks.save(tag, this.bores);
        if (this.panelPos != null) {
            tag.putLong("Panel", this.panelPos.asLong());
        }
        if (this.panelHousehold != null) {
            tag.putUUID("PanelHousehold", this.panelHousehold);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        this.columnHeight = clampHeight(tag.contains("Height")
                ? tag.getInt("Height") : com.createstats.block.BlastShutterBlock.MIN_HEIGHT);
        this.energy = Math.min(capacityFe(), tag.getInt("Energy"));
        if (tag.contains("Tier")) {
            this.managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        this.sealed = tag.getBoolean("Sealed");
        this.moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;
        this.moveTicks = Math.max(1, tag.contains("MoveTicks")
                ? tag.getInt("MoveTicks") : tier().travelTicksFor(this.columnHeight));
        this.governed = tag.getBoolean("Governed");
        this.lockdown = tag.getBoolean("Lockdown");
        this.released = tag.getBoolean("Released");
        this.commanded = tag.getBoolean("Commanded");
        this.houseSealed = tag.getBoolean("HouseSealed");
        this.dusk = tag.getBoolean("Dusk");
        this.bores.clear();
        this.bores.addAll(BoreLinks.load(tag));
        this.panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        this.panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        this.sincePolicy = POLICY_TICKS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Sealed", this.sealed);
        tag.putLong("MoveStart", this.moveStart);
        tag.putInt("MoveTicks", this.moveTicks);

        tag.putInt("Height", this.columnHeight);
        tag.putInt("Energy", this.energy);
        tag.putInt("Intake", this.lastIntake);
        tag.putBoolean("Powered", !this.managed.isSuppressed()
                && ShutterPower.canAffordCycle(this.energy, this.columnHeight));
        tag.putBoolean("Governed", this.governed);
        tag.putBoolean("Wired", this.network != null);
        tag.putBoolean("Suppressed", this.managed.isSuppressed());
        tag.putInt("Circuit", this.network == null ? 0 : this.network.size());
        tag.putBoolean("Lockdown", this.lockdown);
        tag.putBoolean("Released", this.released);
        tag.putBoolean("HouseSealed", this.houseSealed);

        tag.putBoolean("Commanded", this.commanded);
        tag.putBoolean("Dusk", this.dusk);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.sealed = tag.getBoolean("Sealed");
        this.moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;
        this.moveTicks = Math.max(1, tag.getInt("MoveTicks"));
        this.columnHeight = clampHeight(tag.contains("Height")
                ? tag.getInt("Height") : com.createstats.block.BlastShutterBlock.MIN_HEIGHT);
        this.energy = Math.min(capacityFe(), tag.getInt("Energy"));
        this.lastIntake = tag.getInt("Intake");
        this.clientPowered = tag.getBoolean("Powered");
        this.clientGoverned = tag.getBoolean("Governed");
        this.clientWired = tag.getBoolean("Wired");
        this.clientSuppressed = tag.getBoolean("Suppressed");
        this.clientCircuitSize = tag.getInt("Circuit");
        this.lockdown = tag.getBoolean("Lockdown");
        this.released = tag.getBoolean("Released");
        this.houseSealed = tag.getBoolean("HouseSealed");
        this.commanded = tag.getBoolean("Commanded");
        this.dusk = tag.getBoolean("Dusk");
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

    @Nullable
    public static BlastShutterBE at(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof BlastShutterBE shutter ? shutter : null;
    }

    @Override
    @Nullable
    public net.minecraft.core.BlockPos governingPanel() {
        return this.panelPos;
    }
}
