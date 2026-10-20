package com.createstats.blockentity;

import com.createstats.block.DoorEase;
import com.createstats.block.DoorTravel;
import com.createstats.block.GarageDoorApproach;
import com.createstats.block.GarageDoorBodyBlock;
import com.createstats.block.GarageDoorCurl;
import com.createstats.block.GarageDoorHeaderBlock;
import com.createstats.block.GarageDoorSite;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.GarageDoorPower;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityRoster;
import com.createstats.sound.DoorClip;
import com.createstats.sound.DoorSound;
import com.createstats.sound.GarageDoorSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// one BE per door however big
public class GarageDoorBE extends BlockEntity
        implements FeGridMember, FeManagedLoad, SecurityDevice, BoreLinks.Socket,
        com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int POLICY_TICKS = 40;

    private static final int PROXIMITY_TICKS = 5;

    private static final int TRIGGER_HOLD = 30;

    private static final int AUTO_CLOSE_TICKS = 100;

    private static final int SYNC_INTERVAL_TICKS = 10;

    private int width;
    private int height;

    private int origin;

    private boolean open;
    private long moveStart = Long.MIN_VALUE;
    private int moveTicks = GarageDoorCurl.moveTicks(2);

    private int openRows;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;
    private int intake;
    private int lastIntake;

    private final List<BlockPos> bores = new ArrayList<>(1);

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;
    @Nullable
    private SecurityRoster roster;
    private int required;
    private int proximity;
    private boolean failSecure;
    private boolean lockdown;
    private boolean released;

    private boolean sealed;

    private boolean linked;
    private boolean entrance;

    private boolean allowUnmanned;

    private long holdUntil;

    private long autoCloseAt = Long.MAX_VALUE;

    private int sincePolicy = POLICY_TICKS;
    private int sinceProximity;

    private boolean lastPowered;
    private boolean staggered;

    @Nullable
    private GarageDoorSite.Detected cachedSite;
    private long cachedSiteAt = Long.MIN_VALUE;

    private boolean clientGoverned;

    private boolean clientLinked;
    private boolean clientWired;
    private boolean clientSuppressed;
    private int clientCircuitSize;

    private long clientCueStamp = Long.MIN_VALUE;
    private int clientCuesFired;
    private boolean clientLoopRunning;

    private int lastSyncedEnergy = Integer.MIN_VALUE;
    private int ticksSinceSync;
    private boolean lastSyncedWired;
    private boolean lastSyncedSuppressed;
    private int lastSyncedCircuit;
    private boolean lastSyncedAfford;

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

    public GarageDoorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.managed.setPriority(defaultTier());
    }

    public static FePriority defaultTier() {
        return FePriority.HIGH;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int origin() {
        return this.origin;
    }

    public BlockPos originPos() {
        final BlockState state = getBlockState();
        if (!state.hasProperty(GarageDoorHeaderBlock.FACING)) {
            return this.worldPosition;
        }
        return this.worldPosition.relative(
                GarageDoorSite.widthAxis(state.getValue(GarageDoorHeaderBlock.FACING)),
                -this.origin);
    }

    public int area() {
        return this.width * this.height;
    }

    public boolean formed() {
        return this.width > 0 && this.height > 0;
    }

    public GarageDoorSite.Detected site() {
        if (this.level == null) {
            return GarageDoorSite.detect(this.level, this.worldPosition);
        }
        final long now = this.level.getGameTime();
        if (this.cachedSite == null || this.cachedSiteAt != now) {
            this.cachedSite = GarageDoorSite.detect(this.level, this.worldPosition);
            this.cachedSiteAt = now;
        }
        return this.cachedSite;
    }

    private void forgetSite() {
        this.cachedSite = null;
    }

    public boolean isOpen() {
        return this.open;
    }

    public int moveTicks() {
        return this.moveTicks;
    }

    public float progress(float partialTick) {
        if (this.level == null || this.moveStart == Long.MIN_VALUE) {
            return this.open ? 1.0f : 0.0f;
        }
        final float elapsed = this.level.getGameTime() - this.moveStart + partialTick;
        return DoorTravel.openness(elapsed, this.moveTicks, this.open);
    }

    public int elapsedTicks() {
        if (this.level == null || this.moveStart == Long.MIN_VALUE) {
            return this.moveTicks;
        }
        return (int) net.minecraft.util.Mth.clamp(
                this.level.getGameTime() - this.moveStart, 0L, (long) this.moveTicks);
    }

    public boolean settled() {
        return this.level == null || this.moveStart == Long.MIN_VALUE
                || DoorTravel.settled(this.level.getGameTime(), this.moveStart, this.moveTicks);
    }

    public boolean setOpen(boolean value, boolean charge) {
        if (this.level == null || this.level.isClientSide() || !formed()) {
            return false;
        }
        if (value == this.open && settled()) {
            return false;
        }
        if (charge && value != this.open && !spendCycle()) {
            refuse();
            return false;
        }
        final long now = this.level.getGameTime();
        final float where = progress(0.0f);
        this.moveTicks = GarageDoorCurl.moveTicks(this.height);
        this.open = value;
        this.moveStart = DoorTravel.resumeStart(now, this.moveTicks, where, value);
        this.autoCloseAt = value ? now + AUTO_CLOSE_TICKS : Long.MAX_VALUE;
        setChanged();
        sync();
        depart();
        return true;
    }

    private Vec3 soundPos() {
        final GarageDoorSite.Detected site = site();
        if (site.ok()) {
            return openingCentre(site);
        }
        return Vec3.atCenterOf(this.worldPosition);
    }

    private void depart() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final Vec3 at = soundPos();
        final int area = area();
        this.level.playSound(null, at.x, at.y, at.z,
                DoorSound.event(GarageDoorSound.kit().depart()), SoundSource.BLOCKS,
                GarageDoorSound.volume(area), GarageDoorSound.pitch(area));
    }

    private void refuse() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final Vec3 at = soundPos();
        this.level.playSound(null, at.x, at.y, at.z, DoorSound.event(DoorClip.REFUSE),
                SoundSource.BLOCKS, 0.55f, 1.0f);
    }

    public boolean toggleByHand(Player player) {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }
        if (!mayOperate(player)) {
            noteAccess(player, false);
            refuse();
            player.displayClientMessage(lockedDown()
                    ? Component.translatable("message.create_stats.garage_door.lockdown")
                    : Component.translatable("message.create_stats.garage_door.clearance",
                            Clearance.badge(this.required)), true);
            return false;
        }
        noteAccess(player, true);
        return setOpen(!this.open, true);
    }

    public IEnergyStorage energyHandle() {
        return this.energyView;
    }

    public int storedFe() {
        return this.energy;
    }

    public int capacityFe() {
        return GarageDoorPower.buffer(area());
    }

    public int cycleCost() {
        return GarageDoorPower.cycleCost(area());
    }

    public boolean hasPower() {
        return !this.managed.isSuppressed() && GarageDoorPower.canAffordCycle(this.energy, area());
    }

    private boolean spendCycle() {
        if (this.managed.isSuppressed()) {
            return false;
        }
        final int cost = cycleCost();
        if (this.energy < cost) {
            return false;
        }
        this.energy -= cost;
        this.measuredDraw += cost;
        setChanged();
        return true;
    }

    public boolean governed() {
        return this.roster != null;
    }

    public int requiredLevel() {
        return this.required;
    }

    public int proximityRadius() {
        return this.proximity;
    }

    public boolean failSecure() {
        return this.failSecure;
    }

    public boolean lockedDown() {
        return this.lockdown;
    }

    public boolean released() {
        return this.released;
    }

    public boolean isEntrance() {
        return this.entrance;
    }

    public boolean allowsUnmanned() {
        return this.allowUnmanned;
    }

    public void setAllowUnmanned(boolean value) {
        this.allowUnmanned = value;
        setChanged();
        sync();
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

    public boolean toggleUnmanned() {
        setAllowUnmanned(!this.allowUnmanned);
        return this.allowUnmanned;
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

    public boolean mayOperate(@Nullable Player player) {
        if (this.roster == null) {
            return true;
        }
        if (this.released) {
            return !this.entrance || isOwner(player);
        }

        if (this.sealed) {
            return isOwner(player);
        }
        if (this.lockdown) {
            return this.entrance && isOwner(player);
        }
        return player != null && this.roster.passes(player.getUUID(), this.required);
    }

    private boolean isOwner(@Nullable Player player) {
        return player != null && this.roster != null && this.roster.isOwner(player.getUUID());
    }

    public void noteAccess(Player player, boolean passed) {
        final HomeSecurityPanelBE found = panel();
        if (found != null) {
            found.recordAccess(player, this.worldPosition, deviceName().getString(), this.required, passed);
        }
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

        this.ratedDraw = this.managed.isForcedOff() ? 0
                : GarageDoorPower.ratedDrawFor(this.energy, area());
        this.measuredDraw = 0;
        final int idle = GarageDoorPower.idleDraw(area());
        if (formed() && !this.managed.isSuppressed() && this.energy >= idle) {
            this.energy -= idle;
            this.measuredDraw = idle;
            setChanged();
        }

        if (++this.sincePolicy >= POLICY_TICKS) {
            this.sincePolicy = 0;
            remeasure();
            refreshPolicy();
        }

        pushReadout();

        if (!formed()) {
            return;
        }

        settleRows();
        photoEye();

        if (++this.sinceProximity >= PROXIMITY_TICKS) {
            this.sinceProximity = 0;
            sense();
        }

        autoClose();
    }

    private void pushReadout() {
        if (this.ticksSinceSync < SYNC_INTERVAL_TICKS) {
            this.ticksSinceSync++;
            return;
        }
        final boolean wired = this.network != null;
        final boolean suppressed = this.managed.isSuppressed();
        final int circuit = wired ? this.network.size() : 0;

        final int step = Math.max(1, cycleCost() / 12);
        final boolean afford = GarageDoorPower.canAffordCycle(this.energy, area());
        final boolean moved = this.lastSyncedEnergy == Integer.MIN_VALUE
                || Math.abs(this.energy - this.lastSyncedEnergy) >= step
                || afford != this.lastSyncedAfford
                || wired != this.lastSyncedWired
                || suppressed != this.lastSyncedSuppressed
                || circuit != this.lastSyncedCircuit;
        if (!moved) {
            return;
        }
        this.ticksSinceSync = 0;
        this.lastSyncedEnergy = this.energy;
        this.lastSyncedAfford = afford;
        this.lastSyncedWired = wired;
        this.lastSyncedSuppressed = suppressed;
        this.lastSyncedCircuit = circuit;
        sync();
    }

    public void clientTick() {
        if (settled()) {
            return;
        }
        com.createstats.client.GarageDoorEffects.tick(this);
    }

    public boolean claimCue(int cue) {
        if (this.clientCueStamp != this.moveStart) {
            this.clientCueStamp = this.moveStart;
            this.clientCuesFired = 0;
        }
        final int bit = 1 << cue;
        if ((this.clientCuesFired & bit) != 0) {
            return false;
        }
        this.clientCuesFired |= bit;
        return true;
    }

    public boolean loopRunning() {
        return this.clientLoopRunning;
    }

    public void setLoopRunning(boolean running) {
        this.clientLoopRunning = running;
    }

    public Vec3 audiblePos() {
        return soundPos();
    }

    private void remeasure() {
        final GarageDoorSite.Detected site = site();
        if (!site.ok()) {
            standDown();
            return;
        }
        final int w = site.frame().width();
        final int h = site.frame().height();

        final int o = site.columnOf(this.worldPosition);
        if (w != this.width || h != this.height || o != this.origin) {
            if (this.level != null && this.width > 0 && this.height > 0) {
                GarageDoorSite.clearCurtain(this.level, ownCurtain());
            }
            this.width = w;
            this.height = h;
            this.origin = o;
            this.openRows = Math.min(this.openRows, h);
            this.moveTicks = GarageDoorCurl.moveTicks(h);
            this.energy = Math.min(this.energy, capacityFe());
            forgetSite();
            setChanged();
            sync();
        }

        GarageDoorSite.form(this.level, this.worldPosition);
        forgetSite();
        applyRows(this.openRows, true);
    }

    private void standDown() {
        if (this.width > 0 && this.height > 0 && this.level != null) {
            GarageDoorSite.clearCurtain(this.level, ownCurtain());
        }
        this.width = 0;
        this.height = 0;
        this.openRows = 0;
        setChanged();
        sync();
    }

    private List<BlockPos> ownCurtain() {
        final List<BlockPos> out = new ArrayList<>();
        final BlockState state = getBlockState();
        if (!state.hasProperty(GarageDoorHeaderBlock.FACING)) {
            return out;
        }
        final Direction axis =
                GarageDoorSite.widthAxis(state.getValue(GarageDoorHeaderBlock.FACING));
        final BlockPos start = originPos();
        for (int col = 0; col < this.width; col++) {
            for (int row = 0; row < this.height; row++) {
                out.add(start.relative(axis, col).below(this.height - row));
            }
        }
        return out;
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

        final boolean wasGoverned = this.roster != null;
        final boolean wasLinked = this.linked;
        final int wasRequired = this.required;
        final int wasProximity = this.proximity;
        final boolean wasSecure = this.failSecure;
        final boolean wasEntrance = this.entrance;
        this.linked = true;
        this.panelPos = found.getBlockPos();
        this.panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        this.offeredPanel = null;
        final boolean wasLockdown = this.lockdown;
        final boolean wasReleased = this.released;
        final boolean wasSealed = this.sealed;
        this.roster = found.rosterSnapshot();
        this.required = found.requiredLevel(this.worldPosition);
        this.proximity = found.proximity(worldPosition);
        this.failSecure = found.failSecure();
        this.lockdown = found.lockedDown();
        this.released = found.released();
        this.sealed = found.houseSealed();
        this.entrance = found.isEntrance(this.worldPosition);
        setChanged();
        if (wasLockdown != this.lockdown || wasReleased != this.released
                || wasSealed != this.sealed) {
            if ((this.lockdown || this.sealed) && this.open && !this.entrance) {
                setOpen(false, false);
            } else if (this.released && !this.open && !this.entrance) {
                setOpen(true, false);
            }
            sync();
        } else if (!wasGoverned || !wasLinked || wasRequired != this.required
                || wasProximity != this.proximity || wasSecure != this.failSecure
                || wasEntrance != this.entrance) {
            sync();
        }
    }

    private void loseLink() {
        this.panelPos = null;
        if (!this.linked && !this.lockdown && !this.released) {
            return;
        }
        this.linked = false;

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

    private Vec3 openingCentre(GarageDoorSite.Detected site) {
        final BlockPos a = site.at(site.frame().interiorMinH(), site.frame().bottomV());
        final BlockPos b = site.at(site.frame().interiorMaxH(), site.frame().topV());
        return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5,
                (a.getY() + b.getY()) / 2.0 + 0.5,
                (a.getZ() + b.getZ()) / 2.0 + 0.5);
    }

    private AABB openingBox(GarageDoorSite.Detected site) {
        final BlockPos a = site.at(site.frame().interiorMinH(), site.frame().bottomV());
        final BlockPos b = site.at(site.frame().interiorMaxH(), site.frame().topV());
        return new AABB(a).minmax(new AABB(b));
    }

    private double[] localOf(Vec3 point, Vec3 centre, Direction facing) {
        final Vec3 delta = point.subtract(centre);
        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        final Direction axis = GarageDoorSite.widthAxis(facing);
        final Vec3 across = Vec3.atLowerCornerOf(axis.getNormal());
        return new double[]{delta.dot(normal), delta.dot(across), delta.y};
    }

    private void sense() {
        if (this.level == null) {
            return;
        }
        final GarageDoorSite.Detected site = site();
        if (!site.ok()) {
            return;
        }
        if (this.lockdown && !this.released) {
            return;
        }
        // AWAY+: only the entrance scans, and mayOperate under a seal is owner only

        if (this.sealed && !this.released && !this.entrance) {
            return;
        }

        if (this.proximity <= 0) {
            return;
        }

        final Vec3 centre = openingCentre(site);
        final Direction facing = site.facing();
        final long now = this.level.getGameTime();
        boolean sawSomething = false;

        final AABB opening = openingBox(site);
        final AABB search = opening.inflate(this.proximity);
        for (Player player : this.level.getEntitiesOfClass(Player.class, search)) {
            if (player.isSpectator()) {
                continue;
            }
            if (com.createstats.block.DoorProximity.nearBox(
                    player.getX(), player.getY(), player.getZ(),
                    opening.minX, opening.minY, opening.minZ,
                    opening.maxX, opening.maxY, opening.maxZ, this.proximity)
                    && mayOperate(player)) {
                sawSomething = true;
                break;
            }
        }

        if (!sawSomething) {
            final int depth = GarageDoorApproach.DEFAULT_DEPTH;
            final AABB box = new AABB(centre, centre)
                    .inflate(Math.max(this.width, this.height) / 2.0 + depth + 1);
            for (Entity entity : this.level.getEntities((Entity) null, box, this::isVehicle)) {
                final double[] local = localOf(entity.position(), centre, facing);
                final Vec3 velocity = entity.getDeltaMovement();
                final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
                if (GarageDoorApproach.arriving(local[0], local[1], local[2],
                        velocity.dot(normal), this.width, this.height, depth)
                        && vehiclePasses(entity)) {
                    sawSomething = true;
                    break;
                }
            }
        }

        if (sawSomething) {
            this.holdUntil = now + TRIGGER_HOLD;
            if (!this.open) {
                setOpen(true, true);
            } else {
                this.autoCloseAt = now + AUTO_CLOSE_TICKS;
            }
        }
    }

    private boolean isVehicle(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        return entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity
                || entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity
                || entity.isVehicle();
    }

    private boolean vehiclePasses(Entity vehicle) {
        for (Entity rider : vehicle.getPassengers()) {
            if (rider instanceof Player player) {
                return mayOperate(player);
            }
        }
        return this.allowUnmanned && mayOperate(null);
    }

    private void photoEye() {
        if (this.level == null || this.open || settled()) {
            return;
        }
        final GarageDoorSite.Detected site = site();
        if (!site.ok()) {
            return;
        }
        final Vec3 centre = openingCentre(site);
        final Direction facing = site.facing();
        final AABB box = new AABB(centre, centre)
                .inflate(Math.max(this.width, this.height) / 2.0 + 1.0);
        for (Entity entity : this.level.getEntities((Entity) null, box,
                e -> e instanceof LivingEntity || e.isVehicle() || !e.getPassengers().isEmpty())) {
            final double[] local = localOf(entity.position(), centre, facing);
            if (GarageDoorApproach.inDoorway(local[0], local[1], local[2], this.width, this.height)) {
                setOpen(true, false);
                this.holdUntil = this.level.getGameTime() + TRIGGER_HOLD;
                return;
            }
        }
    }

    private void autoClose() {
        if (this.level == null || !this.open || !settled()) {
            return;
        }
        final long now = this.level.getGameTime();
        if (now < this.holdUntil || now < this.autoCloseAt) {
            return;
        }
        if (this.released) {
            return;
        }
        if (this.lastPowered) {
            return;
        }
        setOpen(false, true);
    }

    private void settleRows() {
        if (this.level == null) {
            return;
        }
        final float shaped = DoorEase.DRUM.curve(progress(0.0f), this.open);
        final int wanted = Math.max(0, Math.min(this.height, (int) Math.floor(shaped * this.height)));
        if (wanted == this.openRows) {
            return;
        }
        applyRows(wanted, false);
    }

    private void applyRows(int rows, boolean all) {
        if (this.level == null) {
            return;
        }
        final GarageDoorSite.Detected site = site();
        if (!site.ok()) {
            return;
        }
        final int from = all ? 0 : Math.min(this.openRows, rows);
        final int to = all ? this.height : Math.max(this.openRows, rows);
        for (int row = from; row < to; row++) {
            final boolean rowOpen = row < rows;
            for (BlockPos pos : site.bodyRow(row)) {
                final BlockState state = this.level.getBlockState(pos);
                if (state.getBlock() instanceof GarageDoorBodyBlock
                        && state.getValue(GarageDoorBodyBlock.OPEN) != rowOpen) {
                    // not UPDATE_ALL, 144 curtain blocks would all poke this door to re-read redstone. vanilla doors do the same

                    this.level.setBlock(pos, state.setValue(GarageDoorBodyBlock.OPEN, rowOpen),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        if (this.openRows != rows) {
            this.openRows = rows;
            setChanged();
        }
    }

    public void onRedstone(boolean powered) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!updatePowered(powered)) {
            return;
        }

        if (governed()) {
            return;
        }
        if (powered == this.open) {
            return;
        }
        setOpen(powered, true);
    }

    public boolean wasPowered() {
        return this.lastPowered;
    }

    public boolean updatePowered(boolean powered) {
        if (this.lastPowered == powered) {
            return false;
        }
        this.lastPowered = powered;
        setChanged();
        return true;
    }

    public boolean frameHasSignal() {
        if (this.level == null) {
            return false;
        }
        final GarageDoorSite.Detected site = GarageDoorSite.detect(this.level, this.worldPosition);
        if (!site.ok()) {
            return this.level.hasNeighborSignal(this.worldPosition);
        }
        for (BlockPos pos : site.hoods()) {
            if (this.level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component deviceName() {
        if (!formed()) {
            return Component.translatable("device.create_stats.garage_door.name");
        }
        return Component.translatable("device.create_stats.garage_door.sized",
                this.width, this.height);
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.DOOR;
    }

    @Override
    public DeviceState deviceState() {
        if (!formed()) {
            return DeviceState.OFFLINE;
        }
        if (this.open) {
            return DeviceState.ACTIVE;
        }
        return hasPower() ? DeviceState.IDLE : DeviceState.UNPOWERED;
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
        return GarageDoorPower.transferRate(area());
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

    @Override
    public BlockPos borePosition() {
        return this.worldPosition;
    }

    @Override
    public BlockPos boreAnchorFor(BlockPos far) {
        final GarageDoorSite.Detected site = site();
        if (!site.ok()) {
            return this.worldPosition;
        }
        BlockPos best = this.worldPosition;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : frameBlocks(site)) {
            if (BoreLinks.axisBetween(candidate, far) == null) {
                continue;
            }
            final double d = far.distSqr(candidate);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return best;
    }

    private List<BlockPos> frameBlocks(GarageDoorSite.Detected site) {
        return site.hoods();
    }

    public List<BlockPos> frame() {
        final GarageDoorSite.Detected site = site();
        return site.ok() ? frameBlocks(site) : List.of(this.worldPosition);
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
        sync();
        invalidateNetwork();
    }

    private boolean governedHere() {
        return this.level != null && this.level.isClientSide() ? this.clientGoverned : governed();
    }

    public boolean linkedHere() {
        return this.level != null && this.level.isClientSide() ? this.clientLinked : this.linked;
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

    private int intendedDraw() {
        return GarageDoorPower.ratedDrawFor(this.energy, area());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(getBlockState().getBlock().getName()));
        if (!formed()) {
            // unformed door first, it's the only question someone with goggles is asking

            tooltip.add(FeGoggle.bad(Component.translatable("message.create_stats.garage_door."
                    + site().frame().fault().key())));
            return true;
        }
        tooltip.add(FeGoggle.dim("Opening: " + this.width + " by " + this.height));
        if (suppressedHere()) {
            tooltip.add(FeGoggle.bad("Switched off from a distribution panel"));
        } else if (!hasPower()) {
            tooltip.add(FeGoggle.bad("No power, will not move"));
        } else if (this.open) {
            tooltip.add(FeGoggle.ok("Open"));
        } else {
            tooltip.add(FeGoggle.ok("Shut"));
        }
        tooltip.add(FeGoggle.stored(this.energy, capacityFe()));
        tooltip.add(FeGoggle.supplyDemand(this.lastIntake, intendedDraw()));
        if (!wiredHere()) {
            tooltip.add(FeGoggle.bad("Not on a circuit"));
        } else {
            if (this.lastIntake <= 0 && this.energy < GarageDoorPower.workingReserve(area())) {
                tooltip.add(FeGoggle.bad("On a circuit, but nothing is feeding it"));
            }
            tooltip.add(FeGoggle.dim("Circuit: " + circuitSizeHere() + " blocks"));
        }
        if (!governedHere()) {
            tooltip.add(FeGoggle.dim("No security panel on this circuit"));
        } else if (!linkedHere()) {
            tooltip.add(FeGoggle.warn("Security panel lost, holding the last clearance"));
        } else if (this.released) {
            tooltip.add(FeGoggle.warn(this.entrance
                    ? "Fire release, entrance still owner only" : "Fire release, open to all"));
        } else if (this.sealed) {
            tooltip.add(FeGoggle.bad(this.entrance
                    ? "AWAY+, entrance, opens for the owner"
                    : "AWAY+, owner only, on a click"));
        } else if (this.lockdown) {
            tooltip.add(FeGoggle.bad(this.entrance
                    ? "Lockdown, entrance, owner only" : "Lockdown, sealed"));
        } else {
            tooltip.add(FeGoggle.dim("Needs " + Clearance.badge(this.required).getString()));
            if (this.entrance) {
                tooltip.add(FeGoggle.dim("Entrance: the way back in during a lockdown"));
            }
            if (this.proximity > 0) {
                tooltip.add(FeGoggle.dim("Opens within " + this.proximity + " blocks"));
            }
        }
        tooltip.add(this.allowUnmanned
                ? FeGoggle.warn("Unmanned contraptions may open it")
                : FeGoggle.dim("Unmanned contraptions may not open it"));
        return true;
    }

    @Nullable
    @Override
    public FeWireNetwork network() {
        return this.network;
    }

    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Origin", this.origin);
        tag.putBoolean("Open", this.open);
        tag.putInt("OpenRows", this.openRows);
        tag.putInt("Energy", this.energy);
        tag.putBoolean("AllowUnmanned", this.allowUnmanned);
        tag.putBoolean("Powered", this.lastPowered);
        tag.putLong("AutoCloseAt", this.autoCloseAt);
        if (this.panelPos != null) {
            tag.putLong("Panel", this.panelPos.asLong());
        }
        if (this.panelHousehold != null) {
            tag.putUUID("PanelHousehold", this.panelHousehold);
        }
        tag.putInt("Tier", this.managed.priorityOrdinal());
        tag.putInt("Lock", this.managed.lockOrdinal());

        tag.putInt("Required", this.required);
        tag.putInt("Proximity", this.proximity);
        tag.putBoolean("FailSecure", this.failSecure);
        tag.putBoolean("Lockdown", this.lockdown);
        tag.putBoolean("Entrance", this.entrance);
        tag.putBoolean("Released", this.released);
        tag.putBoolean("Sealed", this.sealed);
        tag.putBoolean("Linked", this.linked);

        if (this.roster != null) {
            tag.put("Roster", this.roster.save());
        }
        BoreLinks.save(tag, this.bores);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.width = tag.getInt("Width");
        this.height = tag.getInt("Height");
        this.origin = tag.getInt("Origin");
        this.open = tag.getBoolean("Open");
        this.openRows = tag.getInt("OpenRows");
        this.energy = tag.getInt("Energy");
        this.allowUnmanned = tag.getBoolean("AllowUnmanned");
        this.lastPowered = tag.getBoolean("Powered");
        this.autoCloseAt = tag.contains("AutoCloseAt") ? tag.getLong("AutoCloseAt") : Long.MAX_VALUE;
        this.panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        this.panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        this.moveTicks = GarageDoorCurl.moveTicks(Math.max(2, this.height));

        this.moveStart = Long.MIN_VALUE;
        this.managed.load(tag.getInt("Tier"), tag.getInt("Lock"));

        this.required = Clearance.clamp(tag.contains("Required")
                ? tag.getInt("Required") : Clearance.DEFAULT_REQUIRED);
        this.proximity = tag.getInt("Proximity");
        this.failSecure = !tag.contains("FailSecure") || tag.getBoolean("FailSecure");
        this.lockdown = tag.getBoolean("Lockdown");
        this.entrance = tag.getBoolean("Entrance");
        this.released = tag.getBoolean("Released");
        this.sealed = tag.getBoolean("Sealed");
        this.linked = tag.getBoolean("Linked");
        if (tag.contains("Roster")) {
            final SecurityRoster loaded = new SecurityRoster();
            loaded.load(tag.getCompound("Roster"));
            this.roster = loaded;
        } else {
            this.roster = null;
        }
        this.bores.clear();
        this.bores.addAll(BoreLinks.load(tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Origin", this.origin);
        tag.putBoolean("Open", this.open);
        tag.putLong("MoveStart", this.moveStart);
        tag.putInt("MoveTicks", this.moveTicks);
        tag.putInt("Energy", this.energy);
        tag.putBoolean("AllowUnmanned", this.allowUnmanned);
        tag.putBoolean("Governed", this.roster != null);
        tag.putInt("Required", this.required);
        tag.putInt("Proximity", this.proximity);
        tag.putBoolean("Lockdown", this.lockdown);
        tag.putBoolean("Entrance", this.entrance);
        tag.putBoolean("Released", this.released);
        tag.putBoolean("Sealed", this.sealed);
        tag.putBoolean("Linked", this.linked);
        tag.putInt("Intake", this.lastIntake);
        tag.putBoolean("Wired", this.network != null);
        tag.putBoolean("Suppressed", this.managed.isSuppressed());
        tag.putInt("Circuit", this.network == null ? 0 : this.network.size());
        BoreLinks.save(tag, this.bores);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.width = tag.getInt("Width");
        this.height = tag.getInt("Height");
        this.origin = tag.getInt("Origin");
        this.open = tag.getBoolean("Open");
        this.moveStart = tag.contains("MoveStart") ? tag.getLong("MoveStart") : Long.MIN_VALUE;
        this.moveTicks = Math.max(1, tag.contains("MoveTicks") ? tag.getInt("MoveTicks")
                : GarageDoorCurl.moveTicks(Math.max(2, this.height)));
        this.energy = tag.getInt("Energy");
        this.allowUnmanned = tag.getBoolean("AllowUnmanned");
        this.clientGoverned = tag.getBoolean("Governed");
        this.required = Clearance.clamp(tag.getInt("Required"));
        this.proximity = tag.getInt("Proximity");
        this.lockdown = tag.getBoolean("Lockdown");
        this.entrance = tag.getBoolean("Entrance");
        this.released = tag.getBoolean("Released");
        this.sealed = tag.getBoolean("Sealed");
        this.clientLinked = tag.getBoolean("Linked");
        this.lastIntake = tag.getInt("Intake");
        this.clientWired = tag.getBoolean("Wired");
        this.clientSuppressed = tag.getBoolean("Suppressed");
        this.clientCircuitSize = tag.getInt("Circuit");
        this.bores.clear();
        this.bores.addAll(BoreLinks.load(tag));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    public static GarageDoorBE at(Level level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof GarageDoorHeaderBlock
                ? GarageDoorHeaderBlock.controllerAt(level, pos, state) : null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }

    @Override
    @Nullable
    public net.minecraft.core.BlockPos governingPanel() {
        return this.panelPos;
    }
}
