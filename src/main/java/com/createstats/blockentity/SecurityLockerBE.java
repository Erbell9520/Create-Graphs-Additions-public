package com.createstats.blockentity;

import com.createstats.block.LockerMotion;
import com.createstats.block.SecurityLockerBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.LockerPower;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.security.LockerAccess;
import com.createstats.security.LockerCode;
import com.createstats.security.LockerMode;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityLog;
import com.createstats.security.SecurityRoster;
import com.createstats.security.SensorKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class SecurityLockerBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        SecurityDevice, BoreLinks.Socket, MenuProvider, com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int COLUMNS = 9;

    public static final int BASE_ROWS = 3;

    public static final int SLOTS = BASE_ROWS * COLUMNS;

    private static final int POLICY_TICKS = 40;

    private static final int VIEWER_GRACE_TICKS = 5;

    private static final int RELEASE_TICKS = 200;

    private static final int MINING_REPORT_TICKS = 200;

    private static final int MINING_HOLD_TICKS = 40;

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int SYNC_STEP = LockerPower.BUFFER / 8;

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
    private LockerMode clientMode = LockerMode.UNSET;
    private String clientHolder = "";
    private boolean clientHasCode;
    private boolean clientLinked;
    private boolean clientDemand;

    private int clientRows = BASE_ROWS;
    private float clientStackMultiplier = 1.0f;
    private int clientSlotUpgrades;
    private int clientStackUpgrades;
    private boolean clientReportsUpgrades;

    private final List<BlockPos> bores = new java.util.ArrayList<>(1);

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, LockerPower.BUFFER - energy);
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
            return LockerPower.BUFFER;
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

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;

    @Nullable
    private SecurityRoster roster;

    private boolean linked;

    private boolean codeDemanded;

    // named for the house, "sealed" already means a mined locker
    private boolean houseSealed;

    private boolean readerWasLive;

    private int required = Clearance.DEFAULT_REQUIRED;

    private String ownerName = "";
    @Nullable
    private UUID ownerId;

    private LockerMode mode = LockerMode.UNSET;
    private String holderName = "";
    @Nullable
    private UUID holderId;

    private int sincePolicy = POLICY_TICKS;
    private boolean staggered;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private final com.createstats.upgrade.UpgradeRack upgrades = new com.createstats.upgrade.UpgradeRack();

    private final LockerCode code = new LockerCode();

    private boolean open;

    private long moveStart = Long.MIN_VALUE;

    private boolean locking;

    private long lastViewer = Long.MIN_VALUE;

    private long releaseUntil;

    private long lastMined = Long.MIN_VALUE;
    private long lastMiningReport = Long.MIN_VALUE;

    public SecurityLockerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.managed.setPriority(defaultTier());
    }

    public static FePriority defaultTier() {
        return FePriority.HIGH;
    }

    public IEnergyStorage energyHandle() {
        return this.energyView;
    }

    public static int drawPerTick() {
        return LockerPower.DRAIN;
    }

    public Container menuContainer() {
        return this.container;
    }

    private static int maxRows() {
        try {
            return com.createstats.ModConfiguration.COMMON.lockerMaxRows.get();
        } catch (RuntimeException ignored) {
            return 36;
        }
    }

    private static int maxStack() {
        try {
            return com.createstats.ModConfiguration.COMMON.lockerMaxStack.get();
        } catch (RuntimeException ignored) {
            return 768;
        }
    }

    public int rows() {
        if (this.level != null && this.level.isClientSide()) {
            return Math.max(BASE_ROWS, this.clientRows);
        }
        final int earned = com.createstats.upgrade.UpgradeScale.rows(BASE_ROWS,
                this.upgrades.multiplier(com.createstats.upgrade.UpgradeKind.SLOTS), maxRows());
        return Math.max(earned,
                com.createstats.upgrade.UpgradeScale.rowsToHold(highestOccupied(), COLUMNS));
    }

    public int slotCount() {
        return rows() * COLUMNS;
    }

    public int stackCapFor(ItemStack stack) {
        final int base = stack.isEmpty() ? 64 : stack.getMaxStackSize();
        return com.createstats.upgrade.UpgradeScale.stackCap(base, stackMultiplier(), maxStack());
    }

    public int stackCap() {
        return com.createstats.upgrade.UpgradeScale.stackCap(64, stackMultiplier(), maxStack());
    }

    public double stackMultiplier() {
        return this.level != null && this.level.isClientSide()
                ? Math.max(1.0, this.clientStackMultiplier)
                : this.upgrades.multiplier(com.createstats.upgrade.UpgradeKind.STACK);
    }

    public int maxStackCeiling() {
        return maxStack();
    }

    public Container rackContainer() {
        return this.rack;
    }

    private final Container rack = new Container() {
        @Override
        public int getContainerSize() {
            return upgrades.size();
        }

        @Override
        public boolean isEmpty() {
            return upgrades.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return upgrades.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            final ItemStack taken = ContainerHelper.removeItem(upgrades.contents(), slot, amount);
            if (!taken.isEmpty()) {
                upgradesChanged();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            final ItemStack taken = ContainerHelper.takeItem(upgrades.contents(), slot);
            if (!taken.isEmpty()) {
                upgradesChanged();
            }
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            upgrades.set(slot, stack);

            final ItemStack held = upgrades.get(slot);
            if (held.getCount() > 1) {
                held.setCount(1);
            }
            upgradesChanged();
        }

        @Override
        public void setChanged() {
            SecurityLockerBE.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void clearContent() {
            upgrades.contents().clear();
            upgradesChanged();
        }
    };

    public com.createstats.upgrade.UpgradeRack upgradeRack() {
        return this.upgrades;
    }

    public int upgradeCount(com.createstats.upgrade.UpgradeKind kind) {
        if (this.level != null && this.level.isClientSide()) {
            return kind == com.createstats.upgrade.UpgradeKind.SLOTS
                    ? this.clientSlotUpgrades : this.clientStackUpgrades;
        }
        return this.upgrades.count(kind);
    }

    public boolean reportsUpgrades() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientReportsUpgrades;
        }
        return !this.mode.personal();
    }

    public boolean mayRemoveUpgrade(int slot) {
        int deepest = 0;
        int deepestBase = 0;
        for (final ItemStack stack : this.items) {
            if (!stack.isEmpty() && stack.getCount() > deepest) {
                deepest = stack.getCount();
                deepestBase = stack.getMaxStackSize();
            }
        }
        return !this.upgrades.blocksRemoval(slot, BASE_ROWS, COLUMNS, maxRows(),
                highestOccupied(), deepest, deepestBase, maxStack());
    }

    private int highestOccupied() {
        for (int i = this.items.size() - 1; i >= 0; i--) {
            if (!this.items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void reshape() {
        final int want = slotCount();
        if (want == this.items.size()) {
            return;
        }
        final NonNullList<ItemStack> next = NonNullList.withSize(want, ItemStack.EMPTY);
        final int carry = Math.min(want, this.items.size());
        for (int i = 0; i < carry; i++) {
            next.set(i, this.items.get(i));
        }

        for (int i = carry; i < this.items.size(); i++) {
            final ItemStack orphan = this.items.get(i);
            if (orphan.isEmpty()) {
                continue;
            }
            int home = -1;
            for (int j = 0; j < next.size(); j++) {
                if (next.get(j).isEmpty()) {
                    home = j;
                    break;
                }
            }
            if (home < 0) {
                return;
            }
            next.set(home, orphan);
        }
        this.items = next;
    }

    public void upgradesChanged() {
        reshape();
        setChanged();

        sync();
    }

    private final Container container = new Container() {
        @Override
        public int getContainerSize() {
            return slotCount();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            final ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
            if (!taken.isEmpty()) {
                setChanged();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= items.size()) {
                return;
            }
            items.set(slot, stack);

            final int cap = stackCapFor(stack);
            if (stack.getCount() > cap) {
                stack.setCount(cap);
            }
            setChanged();
        }

        // vanilla takes min(this, stack size) which undoes every stack upgrade. menu slots bypass it
        @Override
        public int getMaxStackSize() {
            return stackCap();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stackCapFor(stack);
        }

        @Override
        public void setChanged() {
            SecurityLockerBE.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }

        @Override
        public void clearContent() {
            items.clear();
            setChanged();
        }
    };

    public NonNullList<ItemStack> contents() {
        return this.items;
    }

    public boolean isEmptyInside() {
        return this.container.isEmpty();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.createstats.menu.SecurityLockerMenu(containerId, inventory, this);
    }

    public boolean mayOpen(@Nullable Player player) {
        final boolean passes = player != null && this.roster != null
                && this.roster.passes(player.getUUID(), this.required);
        return LockerAccess.opensWithoutCode(this.mode, this.roster != null, hasCode(), readerLive(),
                this.codeDemanded, passes, isHolder(player), this.houseSealed);
    }

    public boolean readerLive() {
        return hasPower() && linkedHere();
    }

    public boolean linkedHere() {
        return this.level != null && this.level.isClientSide() ? this.clientLinked : this.linked;
    }

    public boolean demandsCode() {
        return this.level != null && this.level.isClientSide() ? this.clientDemand : this.codeDemanded;
    }

    public boolean mayClaimCode() {
        return LockerAccess.mayClaimCode(this.mode, this.roster != null, hasCode(), readerLive());
    }

    public boolean isOwner(@Nullable Player player) {
        return player != null && this.roster != null && this.roster.isOwner(player.getUUID());
    }

    public boolean governed() {
        return this.level != null && this.level.isClientSide() ? this.clientGoverned : this.roster != null;
    }

    public int requiredLevel() {
        return this.required;
    }

    public LockerMode mode() {
        return this.level != null && this.level.isClientSide() ? this.clientMode : this.mode;
    }

    public String holderName() {
        return this.level != null && this.level.isClientSide() ? this.clientHolder : this.holderName;
    }

    public boolean isHolder(@Nullable Player player) {
        return player != null && this.holderId != null && this.holderId.equals(player.getUUID());
    }

    public boolean mayChooseMode(@Nullable Player player) {
        return isHolder(player);
    }

    public boolean needsModeChoice(@Nullable Player player) {
        return this.mode == LockerMode.UNSET && isHolder(player);
    }

    public boolean chooseGeneral(@Nullable Player player) {
        if (this.level == null || this.level.isClientSide() || !mayChooseMode(player)) {
            return false;
        }
        this.mode = LockerMode.GENERAL;
        note(player, "set to household", SecurityLog.Verdict.PASSED);
        setChanged();
        sync();
        return true;
    }

    public boolean goPersonal(@Nullable Player player, String digits) {
        if (this.level == null || this.level.isClientSide() || !mayChooseMode(player)) {
            return false;
        }
        if (!this.code.set(digits)) {
            return false;
        }
        this.mode = LockerMode.PERSONAL;

        this.codeDemanded = false;
        note(player, "set to personal", SecurityLog.Verdict.PASSED);
        setOpen(true);
        setChanged();
        sync();
        return true;
    }

    public boolean hasCode() {
        return this.level != null && this.level.isClientSide() ? this.clientHasCode : this.code.isSet();
    }

    public boolean keypadLocked() {
        return this.level != null && this.code.lockedOut(this.level.getGameTime());
    }

    public int keypadLockoutRemaining() {
        return this.level == null ? 0 : this.code.lockoutRemaining(this.level.getGameTime());
    }

    public int keypadStrikes() {
        return this.code.strikes();
    }

    public boolean setCode(String digits) {
        if (this.mode.personal()) {
            return false;
        }
        if (digits == null || digits.isBlank()) {
            this.code.clear();
            setChanged();
            sync();
            return true;
        }
        if (!this.code.set(digits)) {
            return false;
        }

        this.codeDemanded = false;
        setChanged();
        sync();
        return true;
    }

    public boolean claimCode(@Nullable Player player, String digits) {
        if (this.level == null || this.level.isClientSide() || !mayClaimCode()) {
            return false;
        }
        if (!this.code.set(digits)) {
            return false;
        }
        this.codeDemanded = false;
        note(player, "code set at the block", SecurityLog.Verdict.FAULT);
        setOpen(true);
        setChanged();
        sync();
        return true;
    }

    public LockerCode.Result tryCode(Player player, String typed) {
        if (this.level == null || this.level.isClientSide()) {
            return LockerCode.Result.MALFORMED;
        }
        final LockerCode.Result result = this.code.offer(typed, this.level.getGameTime());
        switch (result) {
            case OPENED -> {
                note(player, "code", SecurityLog.Verdict.PASSED);

                this.codeDemanded = false;
                setOpen(true);
            }
            case WRONG -> denied(player, "bad code");
            case TAMPER -> {
                denied(player, "bad code");
                final HomeSecurityPanelBE panel = panel();
                if (panel != null) {
                    panel.tripped(this.worldPosition, SensorKind.TAMPER, "3 bad codes");
                }
            }
            default -> {
            }
        }
        setChanged();
        return result;
    }

    private void note(@Nullable Player actor, String detail, SecurityLog.Verdict verdict) {
        final HomeSecurityPanelBE panel = panel();
        if (panel != null) {
            panel.recordDevice(actor, this.worldPosition, detail, verdict);
        }
    }

    private void denied(@Nullable Player actor, String detail) {
        final HomeSecurityPanelBE panel = panel();
        if (panel != null) {
            panel.recordDenied(actor, this.worldPosition, detail);
        }
    }

    public void noteRefusal(@Nullable Player player, String detail) {
        note(player, detail, SecurityLog.Verdict.DENIED);
    }

    public void noteAccess(Player player, boolean passed) {
        final HomeSecurityPanelBE panel = panel();
        if (panel != null) {
            panel.recordAccess(player, this.worldPosition, deviceName().getString(), this.required, passed);
        }
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean value) {
        if (this.level == null) {
            return;
        }
        if (this.open == value) {
            if (value) {
                noteViewer();
            }
            return;
        }
        if (value) {
            this.open = true;
            this.locking = false;
            this.moveStart = this.level.getGameTime();

            noteViewer();
            setChanged();
            sync();
            return;
        }
        close(true);
    }

    private void close(boolean warm) {
        if (this.level == null || !this.open) {
            return;
        }
        this.open = false;
        this.locking = true;
        this.moveStart = this.level.getGameTime();
        this.lastViewer = Long.MIN_VALUE;
        if (warm) {
            this.releaseUntil = this.level.getGameTime() + RELEASE_TICKS;
        }
        setChanged();
        sync();
    }

    public void noteViewer() {
        if (this.level != null && !this.level.isClientSide()) {
            this.lastViewer = this.level.getGameTime();
        }
    }

    private boolean hasViewer() {
        return this.level != null && this.lastViewer != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastViewer <= VIEWER_GRACE_TICKS;
    }

    public boolean warm() {
        return this.level != null && this.level.getGameTime() < this.releaseUntil;
    }

    public float motionTicks(float partialTick) {
        if (this.level == null || this.moveStart == Long.MIN_VALUE) {
            return this.open ? LockerMotion.UNLOCK_TICKS : 0.0f;
        }
        final float elapsed = this.level.getGameTime() - this.moveStart + partialTick;
        return Math.min(elapsed, LockerMotion.UNLOCK_TICKS);
    }

    public boolean lockingNow() {
        return this.locking;
    }

    public boolean settled() {
        return this.level == null || this.moveStart == Long.MIN_VALUE
                || this.level.getGameTime() - this.moveStart >= LockerMotion.UNLOCK_TICKS;
    }

    public void noteMining(@Nullable Player player) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final long now = this.level.getGameTime();
        this.lastMined = now;

        if (this.open || warm()) {
            return;
        }
        if (this.lastMiningReport != Long.MIN_VALUE && now - this.lastMiningReport < MINING_REPORT_TICKS) {
            return;
        }
        this.lastMiningReport = now;
        final HomeSecurityPanelBE panel = panel();
        if (panel != null) {
            panel.tripped(this.worldPosition, SensorKind.TAMPER, "being mined");
        }
        note(player, "being mined", SecurityLog.Verdict.FAULT);
        sync();
    }

    public boolean beingMined() {
        return this.level != null && this.lastMined != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastMined < MINING_HOLD_TICKS;
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.LOCKER;
    }

    @Override
    public DeviceState deviceState() {
        if (beingMined()) {
            return DeviceState.DAMAGED;
        }
        if (this.open) {
            return DeviceState.ACTIVE;
        }
        return hasPower() ? DeviceState.IDLE : DeviceState.UNPOWERED;
    }

    @Override
    public float integrity() {
        return -1.0f;
    }

    public void claim(@Nullable Player player) {
        if (player == null || !this.ownerName.isEmpty()) {
            return;
        }
        this.ownerId = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        this.holderId = this.ownerId;
        this.holderName = this.ownerName;
        setChanged();
    }

    public String sealOwnerName() {
        return this.mode.personal() && !this.holderName.isEmpty() ? this.holderName : this.ownerName;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.lastIntake = this.intake;
        this.intake = 0;

        if (!this.staggered) {
            this.staggered = true;
            this.sincePolicy = POLICY_TICKS - (int) Math.floorMod(this.worldPosition.asLong(), POLICY_TICKS);
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

        this.ratedDraw = this.managed.isForcedOff() ? 0 : LockerPower.ratedDrawFor(this.energy);
        final boolean running = !this.managed.isSuppressed() && this.energy >= LockerPower.DRAIN;
        if (running) {
            this.energy -= LockerPower.DRAIN;
            this.measuredDraw = LockerPower.DRAIN;
            setChanged();
        } else {
            this.measuredDraw = 0;
        }

        if (++this.sincePolicy >= POLICY_TICKS) {
            this.sincePolicy = 0;
            refreshPolicy();
        }

        final boolean live = readerLive();
        if (this.readerWasLive && !live) {
            breach();
        }
        if (this.readerWasLive != live) {
            this.readerWasLive = live;
            setChanged();
        }

        if (this.open && settled() && !hasViewer()) {
            close(true);
        }

        pushReadout();
    }

    private void refreshPolicy() {
        if (this.level == null) {
            return;
        }
        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(
                panel(), this.panelPos != null,
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
        final boolean wasLinked = this.linked;
        this.linked = true;
        this.panelPos = found.getBlockPos();
        this.panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        this.offeredPanel = null;
        final boolean wasGoverned = this.roster != null;
        final int wasRequired = this.required;
        final boolean wasHouseSealed = this.houseSealed;
        this.roster = found.rosterSnapshot();
        this.required = found.requiredLevel(this.worldPosition);
        this.houseSealed = found.houseSealed();

        final UUID houseOwner = this.roster == null ? null : this.roster.owner();
        if (houseOwner != null && !houseOwner.equals(this.ownerId)) {
            final SecurityRoster.Entry entry = this.roster.find(houseOwner);
            if (entry != null) {
                this.ownerId = houseOwner;
                this.ownerName = entry.name();
            }
        }
        setChanged();
        if (!wasGoverned || !wasLinked || wasRequired != this.required
                || wasHouseSealed != this.houseSealed) {
            sync();
        }
    }

    private void loseLink() {
        this.panelPos = null;
        if (!this.linked) {
            return;
        }
        this.linked = false;
        setChanged();
        sync();
    }

    private void breach() {
        if (this.level == null || this.roster == null) {
            return;
        }
        final boolean wasOpen = this.open;
        close(false);
        if (wasOpen) {
            SecurityLockerBlock.thunk(this.level, this.worldPosition, false);
        }
        if (!this.codeDemanded) {
            this.codeDemanded = true;

            note(null, "reader lost, code required", SecurityLog.Verdict.FAULT);
        }
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

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (this.level == null || this.panelPos == null || !this.level.isLoaded(this.panelPos)) {
            return null;
        }
        return this.level.getBlockEntity(this.panelPos) instanceof HomeSecurityPanelBE panel
                && com.createstats.security.SecurityIdentity.accepts(this.level, this.panelHousehold,
                this.worldPosition, panel) ? panel : null;
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

    public boolean hasPower() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientPowered;
        }
        return this.energy >= LockerPower.DRAIN && !this.managed.isSuppressed();
    }

    public int storedFe() {
        return this.energy;
    }

    public int capacityFe() {
        return LockerPower.BUFFER;
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
        return LockerPower.TRANSFER_RATE;
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
        } else if (this.open) {
            tooltip.add(FeGoggle.warn("Open, somebody is inside it"));
        } else if (demandsCode()) {
            tooltip.add(FeGoggle.bad("Reader was lost, code required"));
        } else if (this.houseSealed && hasCode()) {
            tooltip.add(FeGoggle.bad("AWAY+, the code is the only way in"));
        } else if (!hasPower()) {
            tooltip.add(FeGoggle.warn("No power, code only"));
        } else if (governed() && !linkedHere()) {
            tooltip.add(FeGoggle.warn("No panel, code only"));
        } else {
            tooltip.add(FeGoggle.ok("Locked"));
        }
        tooltip.add(FeGoggle.stored(this.energy, LockerPower.BUFFER));
        tooltip.add(FeGoggle.supplyDemand(this.lastIntake, LockerPower.ratedDrawFor(this.energy)));
        if (!wiredHere()) {
            tooltip.add(FeGoggle.bad("Not on a circuit"));
        } else {
            tooltip.add(FeGoggle.dim("Circuit: " + circuitSizeHere() + " blocks"));
        }
        if (mode().personal()) {
            final String holder = holderName();
            tooltip.add(FeGoggle.dim("Personal"
                    + (holder.isEmpty() ? "" : ", " + holder + "'s")));
            tooltip.add(readerLive()
                    ? FeGoggle.dim("Opens for its holder, code for everybody else")
                    : FeGoggle.warn("Off the network, so the code is the only way in"));
        } else if (!governed()) {
            tooltip.add(hasCode()
                    ? FeGoggle.dim("Sealed, and no panel here to lift it. The code is the only way in")
                    : FeGoggle.dim("No security panel on this circuit, opens for anyone"));
        } else {
            tooltip.add(FeGoggle.dim("Needs " + Clearance.badge(this.required).getString()));
            if (!hasCode()) {
                tooltip.add(readerLive()
                        ? FeGoggle.warn("No code set, clearance is the only way in")
                        : FeGoggle.warn("No code set, and no reader. The next person here sets one"));
            } else {
                tooltip.add(FeGoggle.dim("Code set"));
            }
        }

        if (reportsUpgrades()) {
            final int slotUp = upgradeCount(com.createstats.upgrade.UpgradeKind.SLOTS);
            final int depthUp = upgradeCount(com.createstats.upgrade.UpgradeKind.STACK);
            if (slotUp > 0 || depthUp > 0) {
                tooltip.add(FeGoggle.dim("Upgraded: " + rows() * COLUMNS + " slots, "
                        + stackCap() + " to a stack"));
            }
        }

        tooltip.add(FeGoggle.dim("No hoppers, funnels or pipes, in or out"));
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
                || (this.energy >= LockerPower.DRAIN) != (this.lastSyncedEnergy >= LockerPower.DRAIN)
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
        tag.putInt("Required", this.required);

        tag.putLong("Release", this.releaseUntil);
        tag.putBoolean("Linked", this.linked);
        tag.putBoolean("Demand", this.codeDemanded);
        tag.putBoolean("HouseSealed", this.houseSealed);
        tag.putBoolean("ReaderLive", this.readerWasLive);
        tag.putLong("LastMined", this.lastMined);
        tag.put("Code", this.code.save());
        tag.putString("OwnerName", this.ownerName);
        if (this.ownerId != null) {
            tag.putUUID("OwnerId", this.ownerId);
        }
        tag.putInt("Mode", this.mode.ordinal());
        tag.putString("HolderName", this.holderName);
        if (this.holderId != null) {
            tag.putUUID("HolderId", this.holderId);
        }

        com.createstats.upgrade.DeepItems.save(tag, this.items, registries);
        tag.put("Upgrades", this.upgrades.save(registries));
        BoreLinks.save(tag, this.bores);
        if (this.panelPos != null) {
            tag.putLong("Panel", this.panelPos.asLong());
        }
        if (this.panelHousehold != null) {
            tag.putUUID("PanelHousehold", this.panelHousehold);
        }

        if (this.roster != null) {
            tag.put("Roster", this.roster.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy = Math.min(LockerPower.BUFFER, tag.getInt("Energy"));
        if (tag.contains("Tier")) {
            this.managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        this.required = Clearance.clamp(tag.contains("Required")
                ? tag.getInt("Required") : Clearance.DEFAULT_REQUIRED);

        this.open = false;
        this.locking = false;
        this.moveStart = Long.MIN_VALUE;
        this.lastViewer = Long.MIN_VALUE;
        this.releaseUntil = tag.getLong("Release");
        this.linked = tag.getBoolean("Linked");
        this.codeDemanded = tag.getBoolean("Demand");
        this.houseSealed = tag.getBoolean("HouseSealed");

        this.readerWasLive = tag.getBoolean("ReaderLive");
        this.lastMined = tag.contains("LastMined") ? tag.getLong("LastMined") : Long.MIN_VALUE;
        if (tag.contains("Code")) {
            this.code.load(tag.getCompound("Code"));
        }
        this.ownerName = tag.getString("OwnerName");
        this.ownerId = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        this.mode = LockerMode.fromOrdinal(tag.contains("Mode")
                ? tag.getInt("Mode") : LockerMode.UNSET.ordinal());

        this.holderName = tag.contains("HolderName") ? tag.getString("HolderName") : this.ownerName;
        this.holderId = tag.hasUUID("HolderId") ? tag.getUUID("HolderId")
                : (tag.contains("HolderName") ? null : this.ownerId);

        this.upgrades.load(tag.getCompound("Upgrades"), registries);
        loadItems(tag, registries);
        this.bores.clear();
        this.bores.addAll(BoreLinks.load(tag));
        this.panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        this.panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        if (tag.contains("Roster")) {
            final SecurityRoster loaded = new SecurityRoster();
            loaded.load(tag.getCompound("Roster"));
            this.roster = loaded;
        } else {
            this.roster = null;
        }
        this.sincePolicy = POLICY_TICKS;
    }

    private void loadItems(CompoundTag tag, HolderLookup.Provider registries) {
        final int forContents = com.createstats.upgrade.UpgradeScale.rowsToHold(
                com.createstats.upgrade.DeepItems.highestSlot(tag), COLUMNS);
        final int earned = com.createstats.upgrade.UpgradeScale.rows(BASE_ROWS,
                this.upgrades.multiplier(com.createstats.upgrade.UpgradeKind.SLOTS), maxRows());
        final int rows = Math.max(BASE_ROWS, Math.max(earned, forContents));
        this.items = NonNullList.withSize(rows * COLUMNS, ItemStack.EMPTY);
        com.createstats.upgrade.DeepItems.load(tag, this.items, registries);
    }

    public CompoundTag sealPayload(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        com.createstats.upgrade.DeepItems.save(tag, this.items, registries);
        // upgrades travel with the payload or nowhere. loose AND sealed = dupe

        tag.put("Upgrades", this.upgrades.save(registries));
        tag.put("Code", this.code.save());
        tag.putString("OwnerName", this.ownerName);
        if (this.ownerId != null) {
            tag.putUUID("OwnerId", this.ownerId);
        }

        tag.putInt("Mode", this.mode.ordinal());
        tag.putString("HolderName", this.holderName);
        if (this.holderId != null) {
            tag.putUUID("HolderId", this.holderId);
        }
        return tag;
    }

    public void loadSealPayload(CompoundTag payload, HolderLookup.Provider registries) {
        this.upgrades.load(payload.getCompound("Upgrades"), registries);
        loadItems(payload, registries);
        if (payload.contains("Code")) {
            this.code.load(payload.getCompound("Code"));
        }
        this.ownerName = payload.getString("OwnerName");
        this.ownerId = payload.hasUUID("OwnerId") ? payload.getUUID("OwnerId") : null;

        this.mode = LockerMode.fromOrdinal(payload.contains("Mode")
                ? payload.getInt("Mode") : LockerMode.UNSET.ordinal());
        this.holderName = payload.contains("HolderName")
                ? payload.getString("HolderName") : this.ownerName;
        this.holderId = payload.hasUUID("HolderId") ? payload.getUUID("HolderId")
                : (payload.contains("HolderName") ? null : this.ownerId);

        this.open = false;
        this.moveStart = Long.MIN_VALUE;
        setChanged();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Open", this.open);
        tag.putBoolean("Locking", this.locking);
        tag.putLong("MoveStart", this.moveStart);
        tag.putInt("Required", this.required);
        tag.putBoolean("Governed", this.roster != null);
        tag.putBoolean("HasCode", this.code.isSet());
        tag.putInt("Mode", this.mode.ordinal());
        tag.putString("HolderName", this.holderName);
        tag.putBoolean("Linked", this.linked);
        tag.putBoolean("Demand", this.codeDemanded);
        tag.putBoolean("HouseSealed", this.houseSealed);

        tag.putLong("Release", this.releaseUntil);
        tag.putInt("Energy", this.energy);
        tag.putInt("Intake", this.lastIntake);
        tag.putBoolean("Powered", this.energy >= LockerPower.DRAIN && !this.managed.isSuppressed());
        tag.putBoolean("Wired", this.network != null);
        tag.putBoolean("Suppressed", this.managed.isSuppressed());
        tag.putInt("Circuit", this.network == null ? 0 : this.network.size());
        tag.putLong("LastMined", this.lastMined);

        tag.putInt("UpRows", rows());
        tag.putFloat("UpStack", (float) this.upgrades.multiplier(
                com.createstats.upgrade.UpgradeKind.STACK));
        final boolean report = reportsUpgrades();
        tag.putBoolean("UpReport", report);
        if (report) {
            tag.putInt("UpSlots", this.upgrades.count(com.createstats.upgrade.UpgradeKind.SLOTS));
            tag.putInt("UpDepth", this.upgrades.count(com.createstats.upgrade.UpgradeKind.STACK));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.open = tag.getBoolean("Open");
        this.locking = tag.getBoolean("Locking");
        this.moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;
        this.required = Clearance.clamp(tag.getInt("Required"));
        this.clientGoverned = tag.getBoolean("Governed");
        this.clientHasCode = tag.getBoolean("HasCode");
        this.clientMode = LockerMode.fromOrdinal(tag.getInt("Mode"));
        this.clientHolder = tag.getString("HolderName");
        this.clientLinked = tag.getBoolean("Linked");
        this.clientDemand = tag.getBoolean("Demand");
        this.houseSealed = tag.getBoolean("HouseSealed");
        this.releaseUntil = tag.getLong("Release");
        this.energy = Math.min(LockerPower.BUFFER, tag.getInt("Energy"));
        this.lastIntake = tag.getInt("Intake");
        this.clientPowered = tag.getBoolean("Powered");
        this.clientWired = tag.getBoolean("Wired");
        this.clientSuppressed = tag.getBoolean("Suppressed");
        this.clientCircuitSize = tag.getInt("Circuit");
        this.lastMined = tag.getLong("LastMined");
        this.clientRows = Math.max(BASE_ROWS, tag.getInt("UpRows"));
        this.clientStackMultiplier = Math.max(1.0f, tag.getFloat("UpStack"));

        this.clientReportsUpgrades = tag.getBoolean("UpReport");
        this.clientSlotUpgrades = this.clientReportsUpgrades ? tag.getInt("UpSlots") : 0;
        this.clientStackUpgrades = this.clientReportsUpgrades ? tag.getInt("UpDepth") : 0;
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

    public static boolean isLocker(@Nullable BlockEntity be) {
        return be instanceof SecurityLockerBE;
    }

    @Nullable
    public static SecurityLockerBE at(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof SecurityLockerBE locker ? locker : null;
    }

    @Override
    @Nullable
    public net.minecraft.core.BlockPos governingPanel() {
        return this.panelPos;
    }
}
