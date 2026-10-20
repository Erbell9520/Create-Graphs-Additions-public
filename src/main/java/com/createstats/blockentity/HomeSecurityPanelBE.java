package com.createstats.blockentity;

import com.createstats.block.HomeSecurityPanelBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FixtureLinker;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.security.CoilDevice;
import com.createstats.security.SecurityDevices;
import com.createstats.security.GovernedDevice;
import com.createstats.security.HazardWatch;
import com.createstats.security.Presence;
import com.createstats.security.RelayDevice;
import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityEnrollment;
import com.createstats.security.SecurityEvent;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.SecurityKinds;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayContact;
import com.createstats.security.relay.RelayRules;
import com.createstats.security.relay.RelaySubscription;
import com.createstats.security.SecurityHouseholds;
import com.createstats.security.SecurityLog;
import com.createstats.security.PanelAccess;
import com.createstats.security.SecurityMode;
import com.createstats.security.SecurityRole;
import com.createstats.security.SecurityRoster;
import com.createstats.security.SecuritySegment;
import com.createstats.security.SensorKind;
import com.createstats.stormcoil.CoilRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// power buys discovery, never authority. lose the supply and you can still get into your own house
public class HomeSecurityPanelBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        FixtureLinker, BoreLinks.Socket,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int DRAIN = 1;

    private static final int BUFFER = 200;

    public static final int MAX_PROXIMITY = 8;

    private static final int RESCAN_TICKS = 40;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;

    private int sinceScan = RESCAN_TICKS;

    private final List<BlockPos> bores = new ArrayList<>(1);
    private final List<BlockPos> manual = new ArrayList<>();

    @Nullable
    private UUID householdId;

    @Nullable
    private CompoundTag legacy;

    @Nullable
    private transient SecurityHousehold resolved;

    @Nullable
    private transient PendingJoin pendingJoin;

    private record PendingJoin(UUID into, UUID by, long expires) {
    }

    @Nullable
    private transient PendingModeChange pendingMode;

    private record PendingModeChange(SecurityMode target, UUID by, long expires) {
    }

    private static final long JOIN_WINDOW = 20L * 60L;

    private List<SecuritySegment.Device> devices = List.of();

    private List<SecuritySegment.Device> union = List.of();

    private boolean wasReleased;

    private transient boolean unloading;

    private boolean awake;

    private int shownDevices;
    private int shownFaults;
    private int syncedBand = -1;

    private boolean shownReleased;
    private boolean shownHazard;

    private SecurityMode shownMode = SecurityMode.HOME;
    private boolean shownLockdown;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
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

    public HomeSecurityPanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        managed.setPriority(FePriority.CRITICAL);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return DRAIN;
    }

    @Nullable
    public SecurityHousehold household() {
        if (level == null || level.isClientSide()) {
            return null;
        }
        return ensureHousehold();
    }

    @Nullable
    public UUID householdId() {
        return householdId;
    }

    @Nullable
    private SecurityHousehold ensureHousehold() {
        if (resolved != null && !resolved.dropped() && householdId != null
                && householdId.equals(resolved.id())) {
            return resolved;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        if (store == null) {
            return null;
        }
        if (householdId != null) {
            SecurityHousehold known = store.find(householdId);
            if (known == null) {
                known = store.adopt(householdId);
            }
            known.addPanel(spot(worldPosition));
            resolved = known;
            return known;
        }
        final SecurityHousehold made = store.create();
        if (legacy != null) {
            made.loadLegacyPanel(legacy, dimension());
            legacy = null;
        }
        householdId = made.id();
        made.addPanel(spot(worldPosition));
        resolved = made;
        setChanged();
        return made;
    }

    private String dimension() {
        return SecurityIdentity.dimensionOf(level);
    }

    private SecurityEndpoint key(BlockPos device) {
        return SecurityIdentity.anchorOf(level, device);
    }

    private SecurityEndpoint spot(BlockPos pos) {
        return SecurityIdentity.at(level, pos);
    }

    private boolean inThisWorld(SecurityEndpoint key) {
        return key.isLegacy() || key.inDimension(dimension());
    }

    public void markScanDue() {
        sinceScan = RESCAN_TICKS;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
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

        final boolean wantsToRun = !managed.isSuppressed();

        ratedDraw = managed.isForcedOff() ? 0 : DRAIN;
        final boolean running = wantsToRun && energy >= DRAIN;
        if (running) {
            energy -= DRAIN;
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (running && ++sinceScan >= RESCAN_TICKS) {
            sinceScan = 0;
            rebuild();
        }

        final boolean nowReleased = released();
        if (wasReleased && !nowReleased) {
            pushToDevices();
        }
        wasReleased = nowReleased;

        final SecurityHousehold h = household();
        if (h != null) {
            h.advance(level.getGameTime());
        }

        refresh(running);
    }

    public void rebuild() {
        final SecurityHousehold h = household();
        if (level == null || level.isClientSide() || h == null) {
            return;
        }
        manual.removeIf(pos -> level.isLoaded(pos) && !SecurityDevices.isDevice(level.getBlockEntity(pos)));
        devices = SecuritySegment.scan(level, worldPosition, network, manual, h);

        observe(h, level.getGameTime());

        union = foldUnion(h);

        final java.util.Set<Long> anchors = new java.util.HashSet<>();
        for (SecuritySegment.Device d : unionDevices()) {
            anchors.add(SecuritySegment.policyAnchor(level, d.pos()).asLong());
        }

        final java.util.function.Predicate<Long> dead = key -> {
            final BlockPos at = BlockPos.of(key);
            return level.isLoaded(at) && !anchors.contains(key)
                    && !SecurityDevices.isDevice(level.getBlockEntity(at));
        };
        h.prune(dimension(), dead);

        // tell hand linked devices they're linked. only the repeller was told before and the rest obeyed nothing

        for (BlockPos pos : manual) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GovernedDevice governed) {
                governed.offerPanel(worldPosition);
            }
        }

        h.settleLegacy();

        final SecurityEndpoint door = h.entrance();
        if (door != null && inThisWorld(door) && level.isLoaded(BlockPos.of(door.pos()))
                && !anchors.contains(door.pos())) {
            final SecurityDevice there = SecurityDevices.of(
                    level.getBlockEntity(BlockPos.of(door.pos())));
            if (there != null && there.deviceClass() != SecurityDevice.DeviceClass.DOOR) {
                h.clearEntrance();
            }
        }
        setChanged();
    }

    private void refresh(boolean running) {
        this.awake = running;
        if (level == null) {
            return;
        }
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof HomeSecurityPanelBlock)) {
            return;
        }
        final HomeSecurityPanelBlock.Face want;
        if (!running) {
            want = HomeSecurityPanelBlock.Face.DARK;
        } else if (lockedDown() || released() || hazard() || hasFault() || unionDevices().isEmpty()) {
            want = HomeSecurityPanelBlock.Face.ALERT;
        } else {
            want = HomeSecurityPanelBlock.Face.LIVE;
        }
        if (state.getValue(HomeSecurityPanelBlock.FACE) != want) {
            level.setBlock(worldPosition, state.setValue(HomeSecurityPanelBlock.FACE, want), 3);
        }

        final int shown = unionDevices().size();
        final int band = want.ordinal() * 1000 + Math.min(999, shown);
        final int faults = faultCount();

        final boolean release = released();
        final boolean haz = hazard();
        if (band != syncedBand || faults != shownFaults || shownDevices != shown
                || release != shownReleased || haz != shownHazard) {
            syncedBand = band;
            shownFaults = faults;
            shownDevices = shown;
            shownReleased = release;
            shownHazard = haz;
            level.sendBlockUpdated(worldPosition, state, getBlockState(), 2);
        }
    }

    public boolean isAwake() {
        return awake;
    }

    public List<SecuritySegment.Device> devices() {
        return devices;
    }

    public List<SecuritySegment.Device> unionDevices() {
        return level != null && level.isClientSide() ? devices : union;
    }

    private List<SecuritySegment.Device> foldUnion(SecurityHousehold h) {
        if (h == null || level == null) {
            return devices;
        }
        final Map<Long, SecuritySegment.Device> byKey = new java.util.LinkedHashMap<>();
        for (SecuritySegment.Device d : devices) {
            byKey.put(d.key(), d);
        }
        final List<SecurityEndpoint> stale = new ArrayList<>(0);
        final SecurityEndpoint self = spot(worldPosition);
        for (SecurityEndpoint key : h.panels()) {
            if (key.equals(self) || !inThisWorld(key)) {
                continue;
            }
            final BlockPos at = BlockPos.of(key.pos());
            if (!level.isLoaded(at)) {
                continue;
            }
            if (!(level.getBlockEntity(at) instanceof HomeSecurityPanelBE sibling)
                    || !h.id().equals(sibling.householdId())) {
                stale.add(key);
                continue;
            }
            for (SecuritySegment.Device d : sibling.devices()) {
                byKey.putIfAbsent(d.key(), d);
            }
        }
        for (SecurityEndpoint key : stale) {
            h.removePanel(key);
        }
        addAbsent(h, byKey);
        return List.copyOf(byKey.values());
    }

    private void addAbsent(SecurityHousehold h, Map<Long, SecuritySegment.Device> byKey) {
        final java.util.Set<Long> listed = new java.util.HashSet<>();
        for (SecuritySegment.Device d : byKey.values()) {
            final BlockPos anchor = SecuritySegment.policyAnchor(level, d.pos());
            listed.add(anchor.asLong());

            final BlockPos twin = SecuritySegment.pairedWith(level, anchor);
            if (twin != null) {
                listed.add(twin.asLong());
            }
        }
        final String dim = dimension();
        for (Map.Entry<SecurityEndpoint, SecurityEnrollment> e : h.enrollments().entrySet()) {
            final SecurityEndpoint at = e.getKey();
            if (!at.inDimension(dim) || listed.contains(at.pos())) {
                continue;
            }
            final BlockPos pos = BlockPos.of(at.pos());
            final String kind = e.getValue().kind();
            byKey.putIfAbsent(at.pos(), new SecuritySegment.Device(pos, h.nameOf(at, kindName(kind)),
                    classOf(kind), SecurityDevice.DeviceState.OFFLINE, -1.0f, h.requiredLevel(at),
                    SecuritySegment.bearing(worldPosition, pos), false, h.hasName(at), 1));
        }
    }

    private void observe(SecurityHousehold h, long now) {
        final java.util.Set<SecurityEndpoint> seen = new java.util.HashSet<>();
        for (SecuritySegment.Device d : devices) {
            final BlockEntity be = level.getBlockEntity(d.pos());
            final SecurityDevice device = SecurityDevices.of(be);
            if (device == null) {
                continue;
            }
            final SecurityEndpoint at = key(d.pos());
            final String kind = kindOf(d.deviceClass(), be);
            if (h.id().equals(device.governingHousehold())) {
                h.enroll(at, kind, now);
                seedCoil(h, at, be);
            }
            if (h.isEnrolled(at)) {
                final boolean breached = d.state() == SecurityDevice.DeviceState.BREACHED;
                final boolean impaired = blindSensor(d) || silentRepeller(d) || coilFault(d)
                        || d.state() == SecurityDevice.DeviceState.OFFLINE;
                seen.add(at);
                h.sight(at, kind, Presence.PRESENT, breached, impaired, now);

                final BlockPos anchor = BlockPos.of(at.pos());
                final BlockPos twin = SecuritySegment.pairedWith(level, anchor);
                if (twin != null && !twin.equals(anchor)) {
                    final SecurityEndpoint leaf = spot(twin);
                    h.enrollLeaf(leaf, at, kind, now);
                    seen.add(leaf);
                    h.sight(leaf, kind, Presence.PRESENT, breached, impaired, now);
                }
            }
        }
        final String dim = dimension();
        for (Map.Entry<SecurityEndpoint, SecurityEnrollment> e : h.enrollments().entrySet()) {
            final SecurityEndpoint at = e.getKey();
            if (!at.inDimension(dim) || seen.contains(at) || e.getValue().missing()) {
                continue;
            }
            final BlockPos pos = BlockPos.of(at.pos());

            if (level.isLoaded(pos) && !SecuritySegment.devicePresent(level, pos)) {
                if (h.markMissing(at, now)) {
                    final String name = h.nameOf(at, kindName(e.getValue().kind()));
                    h.log().add(now, "panel", name, "gone from its place", SecurityLog.Verdict.FAULT);
                }
                continue;
            }

            if (h.presence(at, now) == Presence.PRESENT) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                h.sight(at, e.getValue().kind(), Presence.UNLOADED, false, false, now);
            } else {
                h.sight(at, e.getValue().kind(), Presence.UNREACHED, false, false, now);
            }
        }
    }

    static String kindOf(SecurityDevice.DeviceClass cls, @Nullable BlockEntity be) {
        return switch (cls) {
            case DOOR -> SecurityKinds.DOOR;
            case SENSOR -> be instanceof SecuritySensorBE sensor ? SecurityKinds.sensor(sensor.kind())
                    : SecurityKinds.SENSOR_MOTION;
            case LOCKER -> SecurityKinds.LOCKER;
            case SHUTTER -> SecurityKinds.SHUTTER;
            case REPELLER -> SecurityKinds.REPELLER;
            case RELAY -> SecurityKinds.RELAY;
            case COIL -> SecurityKinds.COIL;
        };
    }

    static SecurityDevice.DeviceClass classOf(String kind) {
        if (SecurityKinds.isSensor(kind)) {
            return SecurityDevice.DeviceClass.SENSOR;
        }
        return switch (kind) {
            case SecurityKinds.LOCKER -> SecurityDevice.DeviceClass.LOCKER;
            case SecurityKinds.SHUTTER -> SecurityDevice.DeviceClass.SHUTTER;
            case SecurityKinds.REPELLER -> SecurityDevice.DeviceClass.REPELLER;
            case SecurityKinds.RELAY -> SecurityDevice.DeviceClass.RELAY;
            case SecurityKinds.COIL -> SecurityDevice.DeviceClass.COIL;
            default -> SecurityDevice.DeviceClass.DOOR;
        };
    }

    static String kindName(String kind) {
        return switch (kind) {
            case SecurityKinds.SENSOR_MOTION -> "Motion Sensor";
            case SecurityKinds.SENSOR_TAMPER -> "Tamper Sensor";
            case SecurityKinds.SENSOR_FIRE -> "Fire Sensor";
            case SecurityKinds.SENSOR_WATER -> "Water Sensor";
            case SecurityKinds.LOCKER -> "Security Locker";
            case SecurityKinds.SHUTTER -> "Blast Shutter";
            case SecurityKinds.REPELLER -> "Mob Repeller";
            case SecurityKinds.RELAY -> "Security Relay";
            case SecurityKinds.COIL -> "Storm Coil";
            default -> "Door";
        };
    }

    public int sleepingPanels() {
        final SecurityHousehold h = household();
        if (h == null) {
            return 0;
        }
        int asleep = 0;
        for (SecurityEndpoint key : h.panels()) {
            if (!inThisWorld(key) || !level.isLoaded(BlockPos.of(key.pos()))) {
                asleep++;
            }
        }
        return asleep;
    }

    public int panelCount() {
        final SecurityHousehold h = household();
        return h == null ? 1 : Math.max(1, h.panels().size());
    }

    public SecurityRoster roster() {
        final SecurityHousehold h = household();
        return h == null ? new SecurityRoster() : h.roster();
    }

    public SecurityRoster rosterSnapshot() {
        final SecurityHousehold h = household();
        return h == null ? new SecurityRoster() : h.rosterCopy();
    }

    public SecurityLog log() {
        final SecurityHousehold h = household();
        return h == null ? new SecurityLog() : h.log();
    }

    public boolean failSecure() {
        final SecurityHousehold h = household();
        return h == null || h.failSecure();
    }

    public int proximity() {
        final SecurityHousehold h = household();
        return h == null ? 3 : h.proximity();
    }

    public SecurityMode mode() {
        if (level != null && level.isClientSide()) {
            return shownMode;
        }
        final SecurityHousehold h = household();
        return h == null ? SecurityMode.HOME : h.mode();
    }

    public boolean houseSealed() {
        return mode().sealsHouse();
    }

    public boolean lockedDown() {
        if (level != null && level.isClientSide()) {
            return shownLockdown;
        }
        final SecurityHousehold h = household();
        return h != null && h.lockdown();
    }

    public long lockdownAt() {
        final SecurityHousehold h = household();
        return h == null ? 0L : h.lockdownAt();
    }

    public String lockdownCause() {
        final SecurityHousehold h = household();
        return h == null ? "" : h.lockdownCause();
    }

    public boolean released() {
        if (level == null) {
            return false;
        }
        if (level.isClientSide()) {
            return shownReleased;
        }
        final SecurityHousehold h = household();
        return h != null && h.released(level.getGameTime());
    }

    public boolean hazard() {
        if (level == null) {
            return false;
        }
        if (level.isClientSide()) {
            return shownHazard;
        }
        final SecurityHousehold h = household();
        return h != null && h.hazard(level.getGameTime());
    }

    public String hazardCause() {
        final SecurityHousehold h = household();
        return h == null || level == null ? "" : h.hazardCause(level.getGameTime());
    }

    public boolean isEntrance(BlockPos device) {
        final SecurityHousehold h = household();
        return h != null && level != null && h.isEntrance(key(device));
    }

    @Nullable
    public BlockPos entrance() {
        final SecurityHousehold h = household();
        final SecurityEndpoint door = h == null ? null : h.entrance();
        return door == null || !inThisWorld(door) ? null : BlockPos.of(door.pos());
    }

    public boolean sensorArmed(SensorKind kind, BlockPos sensor) {
        if (!kind.family().armable()) {
            return true;
        }
        final SecurityHousehold h = household();
        return h != null && h.mode().armsIntrusion(h.sensorFlag(spot(sensor)));
    }

    public boolean sensorFlag(BlockPos sensor) {
        final SecurityHousehold h = household();
        return sensor != null && (h == null || h.sensorFlag(spot(sensor)));
    }

    public boolean shutterCommanded(BlockPos anchor) {
        final SecurityHousehold h = household();
        return anchor != null && h != null && h.shutterSealed(spot(anchor));
    }

    public boolean shutterDusk(BlockPos anchor) {
        final SecurityHousehold h = household();
        return anchor != null && h != null && h.shutterDusk(spot(anchor));
    }

    public boolean repellerOn(BlockPos repeller) {
        final SecurityHousehold h = household();
        return repeller == null || h == null || h.repellerOn(spot(repeller));
    }

    public boolean repellerLocked(BlockPos repeller) {
        final SecurityHousehold h = household();
        return repeller != null && h != null && h.repellerLocked(spot(repeller));
    }

    public boolean repellerHearsRedstone(BlockPos repeller) {
        final SecurityHousehold h = household();
        return repeller == null || h == null || h.repellerHearsRedstone(spot(repeller));
    }

    public boolean coilOn(BlockPos coil) {
        final SecurityHousehold h = household();
        return coil == null || h == null || h.coilOn(spot(coil));
    }

    public boolean coilLocked(BlockPos coil) {
        final SecurityHousehold h = household();
        return coil != null && h != null && h.coilLocked(spot(coil));
    }

    public boolean coilHearsRedstone(BlockPos coil) {
        final SecurityHousehold h = household();
        return coil == null || h == null || h.coilHearsRedstone(spot(coil));
    }

    @Nullable
    public CoilRules.Grid coilRules(BlockPos coil) {
        final SecurityHousehold h = household();
        return coil == null || h == null ? null : h.coilRules(spot(coil));
    }

    public CoilRules.HouseState houseState() {
        return CoilRules.HouseState.of(mode(), lockedDown());
    }

    public int sensorRadius(BlockPos sensor, SensorKind kind) {
        final SecurityHousehold h = household();
        return h == null || sensor == null
                ? SensorKind.clampRadius(kind.defaultRadius())
                : h.sensorRadius(spot(sensor), kind.defaultRadius());
    }

    public int requiredLevel(BlockPos device) {
        final SecurityHousehold h = household();
        return h == null ? Clearance.DEFAULT_REQUIRED
                : h.requiredLevel(key(device));
    }

    public int proximity(BlockPos device) {
        final SecurityHousehold h = household();
        if (h == null || device == null) {
            return proximity();
        }
        final int set = h.proximityOverride(key(device));
        return set < 0 ? h.proximity() : clampReach(set);
    }

    public int proximityOverride(BlockPos device) {
        final SecurityHousehold h = household();
        if (h == null || device == null) {
            return -1;
        }
        final int set = h.proximityOverride(key(device));
        return set < 0 ? -1 : clampReach(set);
    }

    public static int clampReach(int blocks) {
        return Math.max(0, Math.min(MAX_PROXIMITY, blocks));
    }

    public boolean hasFault() {
        if (level != null && level.isClientSide()) {
            return shownFaults > 0;
        }
        return faultCount() > 0;
    }

    private int faultCount() {
        int n = 0;
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.state().isFault() || blindSensor(d) || silentRepeller(d) || coilFault(d)) {
                n++;
            }
        }
        return n;
    }

    private boolean blindSensor(SecuritySegment.Device d) {
        return d.deviceClass() == SecurityDevice.DeviceClass.SENSOR
                && d.state() == SecurityDevice.DeviceState.UNPOWERED;
    }

    private boolean silentRepeller(SecuritySegment.Device d) {
        return d.deviceClass() == SecurityDevice.DeviceClass.REPELLER
                && d.state() == SecurityDevice.DeviceState.UNPOWERED;
    }

    private boolean coilFault(SecuritySegment.Device d) {
        return d.deviceClass() == SecurityDevice.DeviceClass.COIL
                && (d.state() == SecurityDevice.DeviceState.UNPOWERED
                || d.state() == SecurityDevice.DeviceState.DAMAGED);
    }

    private void seedCoil(SecurityHousehold h, SecurityEndpoint at, @Nullable BlockEntity be) {
        if (be instanceof CoilDevice coil && !h.hasCoilRules(at)) {
            h.setCoilRules(at, CoilRules.Grid.ofMode(coil.coilReading().targets()));
        }
    }

    public int shownDeviceCount() {
        return level != null && level.isClientSide() ? shownDevices : unionDevices().size();
    }

    public int countOf(SecurityDevice.DeviceClass cls) {
        int n = 0;
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.deviceClass() == cls) {
                n++;
            }
        }
        return n;
    }

    public Component houseName() {
        final SecurityHousehold h = household();
        final String name = h == null ? "" : h.house();
        return name.isEmpty()
                ? Component.translatable("gui.create_stats.security.house_unnamed")
                : Component.translatable("gui.create_stats.security.house", name);
    }

    public String houseRaw() {
        final SecurityHousehold h = household();
        return h == null ? "" : h.house();
    }

    public boolean hasHouseName() {
        final SecurityHousehold h = household();
        return h != null && !h.house().isEmpty();
    }

    public void claim(Player player) {
        final SecurityHousehold h = household();
        if (player == null || h == null) {
            return;
        }
        if (h.house().isEmpty()) {
            h.setHouse(player.getGameProfile().getName());
        }
        if (h.roster().owner() == null) {
            h.roster().setOwner(player.getUUID(), player.getGameProfile().getName());
            h.log().add(level.getGameTime(), player.getGameProfile().getName(), "panel",
                    "claimed the household", SecurityLog.Verdict.CHANGED);
        }
        setChanged();
    }

    public boolean isClaimed() {
        final SecurityHousehold h = household();
        return h != null && h.roster().owner() != null;
    }

    public boolean isOwnedBy(UUID player) {
        final SecurityHousehold h = household();
        return h != null && h.roster().isOwner(player);
    }

    public void openFor(net.minecraft.server.level.ServerPlayer sender) {
        final SecurityHousehold h = household();
        if (h == null || sender == null || level == null) {
            return;
        }
        if (!powered()) {
            sender.displayClientMessage(
                    Component.translatable("message.create_stats.security.panel_unpowered"), true);
            return;
        }

        forgetPendingJoin(sender.getUUID());
        forgetPendingMode(sender.getUUID());
        final long now = level.getGameTime();
        if (PanelAccess.demand(h.hasPassword(), h.alwaysAsk(), h.roster().isOwner(sender.getUUID()),
                h.roster().levelOf(sender.getUUID())) == PanelAccess.Entry.OPEN) {
            admit(sender, h, now);
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.ask(worldPosition, h.house()));
    }

    private void admit(net.minecraft.server.level.ServerPlayer sender, SecurityHousehold h, long now) {
        h.admit(sender.getUUID(), now);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.open(worldPosition));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.SecuritySyncPacket.of(this, sender));
    }

    public void offerPassword(net.minecraft.server.level.ServerPlayer sender, String digits) {
        final SecurityHousehold h = household();
        if (h == null || sender == null || level == null) {
            return;
        }

        if (!powered()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    com.createstats.network.OpenSecurityPanelPacket.shut(worldPosition));
            return;
        }
        final long now = level.getGameTime();

        final SecurityMode sealing = pendingModeChangeFor(sender, now);
        if (sealing != null) {
            answerModeChange(sender, sealing, digits, now);
            return;
        }

        final SecurityHousehold joining = pendingJoinFor(sender, now);
        if (joining != null) {
            answerJoin(sender, joining, digits, now);
            return;
        }
        final com.createstats.security.LockerCode.Result result = h.offerPassword(digits, now);
        if (result == com.createstats.security.LockerCode.Result.OPENED) {
            admit(sender, h, now);
            return;
        }
        if (result == com.createstats.security.LockerCode.Result.TAMPER) {
            // Routed through tripped rather than written straight to the log, so a house in AWAY

            tripped(worldPosition, SensorKind.TAMPER,
                    sender.getGameProfile().getName() + " failed the panel code");
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.verdict(worldPosition, result,
                        (h.passwordLockout(now) + 19) / 20, h.house(),
                        com.createstats.network.OpenSecurityPanelPacket.PLAIN));
    }

    @Nullable
    private SecurityHousehold pendingJoinFor(net.minecraft.server.level.ServerPlayer sender, long now) {
        final PendingJoin note = this.pendingJoin;
        if (note == null) {
            return null;
        }

        if (!note.by().equals(sender.getUUID())) {
            return null;
        }
        if (now >= note.expires()) {
            this.pendingJoin = null;
            return null;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        final SecurityHousehold from = household();
        final SecurityHousehold into = store == null ? null : store.find(note.into());
        if (store == null || from == null || into == null || into.id().equals(from.id())
                || !roleOf(sender).mayEditPanel()
                || !into.roster().roleOf(sender.getUUID()).mayEditPanel()
                || !into.hasPassword()) {
            this.pendingJoin = null;
            return null;
        }
        return into;
    }

    private void forgetPendingJoin(UUID player) {
        final PendingJoin note = this.pendingJoin;
        if (note != null && note.by().equals(player)) {
            this.pendingJoin = null;
        }
    }

    private void answerJoin(net.minecraft.server.level.ServerPlayer sender, SecurityHousehold into,
                            String digits, long now) {
        final com.createstats.security.LockerCode.Result result = into.offerPassword(digits, now);
        if (result == com.createstats.security.LockerCode.Result.OPENED) {
            final SecurityHouseholds store = SecurityHouseholds.of(level);
            final SecurityHousehold from = household();
            if (store == null || from == null) {
                return;
            }
            commitJoin(store, from, into, sender);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    com.createstats.network.OpenSecurityPanelPacket.open(worldPosition));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    com.createstats.network.SecuritySyncPacket.of(this, sender));
            return;
        }
        if (result == com.createstats.security.LockerCode.Result.TAMPER) {
            into.log().add(now, sender.getGameProfile().getName(), "panel",
                    "failed the code joining a panel to this household", SecurityLog.Verdict.DENIED);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.verdict(worldPosition, result,
                        (into.passwordLockout(now) + 19) / 20, into.house(),
                        com.createstats.network.OpenSecurityPanelPacket.JOIN));
    }

    public boolean powered() {
        return this.awake;
    }

    public boolean admits(net.minecraft.server.level.ServerPlayer sender) {
        final SecurityHousehold h = household();
        if (h == null || sender == null || level == null) {
            return h == null;
        }

        if (!powered()) {
            return false;
        }
        if (PanelAccess.demand(h.hasPassword(), h.alwaysAsk(), h.roster().isOwner(sender.getUUID()),
                h.roster().levelOf(sender.getUUID())) == PanelAccess.Entry.OPEN) {
            return true;
        }
        return h.admitted(sender.getUUID(), level.getGameTime());
    }

    public void refreshAdmission(net.minecraft.server.level.ServerPlayer sender) {
        final SecurityHousehold h = household();
        if (h != null && sender != null && level != null) {
            h.refreshAdmission(sender.getUUID(), level.getGameTime());
        }
    }

    public boolean setPassword(String digits, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        if (digits == null || digits.isBlank()) {
            if (h.mode().sealsHouse()) {
                if (actor != null) {
                    actor.displayClientMessage(Component.translatable(
                            "message.create_stats.security.seal_password_locked"), true);
                }
                return false;
            }
            h.clearPassword();
            note(actor, "panel", "password cleared", SecurityLog.Verdict.CHANGED);
        } else if (!h.setPassword(digits)) {
            return false;
        } else {
            note(actor, "panel", "password set", SecurityLog.Verdict.CHANGED);
        }
        setChanged();
        return true;
    }

    public boolean setAlwaysAsk(boolean value, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        h.setAlwaysAsk(value);
        note(actor, "panel", value ? "always asks for the password" : "password asked by clearance",
                SecurityLog.Verdict.CHANGED);
        setChanged();
        return true;
    }

    public boolean hasPassword() {
        final SecurityHousehold h = household();
        return h != null && h.hasPassword();
    }

    public boolean alwaysAsk() {
        final SecurityHousehold h = household();
        return h != null && h.alwaysAsk();
    }

    public boolean joinHousehold(UUID target, Player actor) {
        if (level == null || level.isClientSide() || target == null) {
            return false;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        final SecurityHousehold from = household();
        if (store == null || from == null || target.equals(from.id())) {
            return false;
        }
        if (!roleOf(actor).mayEditPanel()) {
            return false;
        }
        final SecurityHousehold into = store.find(target);

        if (into == null || actor == null || !into.roster().roleOf(actor.getUUID()).mayEditPanel()) {
            return false;
        }
        if (PanelAccess.demandOnJoin(into.hasPassword()) == PanelAccess.Entry.PASSWORD) {
            if (!(actor instanceof net.minecraft.server.level.ServerPlayer sender)) {
                return false;
            }

            this.pendingJoin = new PendingJoin(into.id(), sender.getUUID(),
                    level.getGameTime() + JOIN_WINDOW);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    com.createstats.network.OpenSecurityPanelPacket.askToJoin(worldPosition,
                            into.house()));
            return false;
        }
        commitJoin(store, from, into, actor);
        return true;
    }

    private void commitJoin(SecurityHouseholds store, SecurityHousehold from, SecurityHousehold into,
                            Player actor) {
        moveTo(store, from, into);
        into.log().add(level.getGameTime(), actor.getGameProfile().getName(), "panel",
                "a panel joined this household", SecurityLog.Verdict.CHANGED);
        into.admit(actor.getUUID(), level.getGameTime());

        from.forgetAdmission(actor.getUUID());
        forgetPendingJoin(actor.getUUID());
    }

    private void moveTo(SecurityHouseholds store, SecurityHousehold from, SecurityHousehold into) {
        from.removePanel(spot(worldPosition));
        householdId = into.id();
        into.addPanel(spot(worldPosition));
        if (from.panels().isEmpty() && from.isEmpty()) {
            store.drop(from.id());
        }
        setChanged();

        rebuild();
        pushToDevices();
    }

    public List<SecurityHousehold> joinable(Player viewer) {
        final List<SecurityHousehold> out = new ArrayList<>();
        if (level == null || level.isClientSide() || viewer == null) {
            return out;
        }

        final boolean mayJoin = roleOf(viewer).mayEditPanel();
        final boolean serverOperator = viewer.hasPermissions(2);
        if (!mayJoin && !serverOperator) {
            return out;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        final SecurityHousehold mine = household();
        if (store == null) {
            return out;
        }
        final Set<UUID> seen = new HashSet<>();
        if (mine != null) {
            seen.add(mine.id());
        }
        if (mayJoin && network != null) {
            for (long key : network.reachedPositions()) {
                final BlockPos at = BlockPos.of(key);
                if (!level.isLoaded(at) || at.equals(worldPosition)) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof HomeSecurityPanelBE other
                        && other.householdId() != null && seen.add(other.householdId())) {
                    final SecurityHousehold found = store.find(other.householdId());
                    if (found != null
                            && found.roster().roleOf(viewer.getUUID()).mayEditPanel()) {
                        out.add(found);
                    }
                }
            }
        }
        final List<UUID> orphans = new ArrayList<>(0);
        for (SecurityHousehold owned : mayJoin
                ? store.ownedBy(viewer.getUUID()) : List.<SecurityHousehold>of()) {
            if (!seen.add(owned.id())) {
                continue;
            }

            if (!owned.panels().isEmpty()) {
                continue;
            }

            if (owned.panels().isEmpty() && owned.isEmpty()) {
                orphans.add(owned.id());
                continue;
            }
            out.add(owned);
        }
        if (serverOperator) {
            for (SecurityHousehold removable : store.removableBy(viewer.getUUID(), true)) {
                if (!seen.add(removable.id())) {
                    continue;
                }
                if (removable.isEmpty()) {
                    orphans.add(removable.id());
                    continue;
                }
                out.add(removable);
            }
        }
        for (UUID dead : orphans) {
            store.drop(dead);
        }
        return out;
    }

    public boolean removeHousehold(UUID target, net.minecraft.server.level.ServerPlayer actor) {
        if (level == null || level.isClientSide() || target == null || actor == null) {
            return false;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        if (store == null || !store.removeOrphan(target, actor.getUUID(), actor.hasPermissions(2))) {
            return false;
        }
        final PendingJoin note = this.pendingJoin;
        if (note != null && target.equals(note.into())) {
            this.pendingJoin = null;
        }
        return true;
    }

    public boolean configured() {
        final SecurityHousehold h = household();
        return h != null && h.configured();
    }

    public boolean finishSetup(Player actor) {
        final SecurityHousehold h = household();
        if (h == null || h.configured() || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        h.setConfigured(true);
        note(actor, "panel", "set up", SecurityLog.Verdict.CHANGED);
        setChanged();
        return true;
    }

    public SecurityRole roleOf(Player player) {
        final SecurityHousehold h = household();
        return player == null || h == null ? SecurityRole.MEMBER : h.roster().roleOf(player.getUUID());
    }

    public boolean setRequiredLevel(BlockPos device, int wanted, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices()) {
            return false;
        }
        final int clearance = Clearance.clamp(wanted);
        final BlockPos anchor = SecuritySegment.policyAnchor(this.level, device);
        h.setRequiredLevel(spot(anchor), clearance);

        final BlockPos stale = SecuritySegment.pairedWith(this.level, anchor);
        if (stale != null) {
            h.clearRequiredLevel(spot(stale));
        }
        note(actor, nameOf(device), "set to " + clearance + " " + Clearance.plain(clearance),
                SecurityLog.Verdict.CHANGED);
        rebuild();
        pushToDevices();
        return true;
    }

    public boolean setDeviceName(BlockPos device, String name, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices()) {
            return false;
        }
        final BlockPos anchor = SecuritySegment.policyAnchor(this.level, device);
        h.setName(spot(anchor), name);
        final BlockPos stale = SecuritySegment.pairedWith(this.level, anchor);
        if (stale != null) {
            h.setName(spot(stale), null);
        }
        rebuild();
        return true;
    }

    public boolean addPlayer(UUID id, String name, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditRoster() || !h.roster().add(id, name)) {
            return false;
        }
        note(actor, "roster", name + " added", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean removePlayer(UUID id, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditRoster()) {
            return false;
        }
        final SecurityRoster.Entry entry = h.roster().find(id);
        if (entry == null || !mayTouch(actor, entry)) {
            return false;
        }
        if (!h.roster().remove(id)) {
            return false;
        }
        note(actor, "roster", entry.name() + " removed", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setPlayerLevel(UUID id, int wanted, Player actor) {
        final SecurityHousehold h = household();
        final SecurityRole role = roleOf(actor);
        if (h == null || !role.mayEditRoster()) {
            return false;
        }
        final SecurityRoster.Entry entry = h.roster().find(id);
        if (entry == null || !mayTouch(actor, entry)) {
            return false;
        }
        final int level = Clearance.clamp(wanted);
        if (!role.isOwner() && actor != null && level > h.roster().levelOf(actor.getUUID())) {
            return false;
        }
        if (!h.roster().setLevel(id, level)) {
            return false;
        }
        note(actor, "roster", entry.name() + " set to " + level + " " + Clearance.plain(level),
                SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setPlayerRole(UUID id, SecurityRole wanted, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).isOwner()) {
            return false;
        }
        final SecurityRoster.Entry entry = h.roster().find(id);
        if (entry == null || !h.roster().setRole(id, wanted)) {
            return false;
        }
        note(actor, "roster", entry.name() + " is now " + wanted.name(), SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean transferOwnership(UUID id, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).isOwner()) {
            return false;
        }
        final SecurityRoster.Entry entry = h.roster().find(id);
        if (entry == null) {
            return false;
        }
        h.roster().setOwner(id, entry.name());
        note(actor, "roster", entry.name() + " is now owner", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setFailSecure(boolean value, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        h.setFailSecure(value);
        note(actor, "panel", value ? "fail secure" : "fail open", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setProximity(int blocks, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        h.setProximity(clampReach(blocks));
        setChanged();

        pushToDevices();
        return true;
    }

    public boolean setDoorProximity(BlockPos device, int wanted, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices()) {
            return false;
        }
        final SecurityEndpoint key = key(device);
        if (wanted < 0) {
            if (h.proximityOverride(key) < 0) {
                return false;
            }
            h.setProximityOverride(key, -1);
            note(actor, nameOf(device), "opens with the house", SecurityLog.Verdict.CHANGED);
        } else {
            final int blocks = clampReach(wanted);
            h.setProximityOverride(key, blocks);
            note(actor, nameOf(device), "opens within " + blocks, SecurityLog.Verdict.CHANGED);
        }
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setSensorRadius(BlockPos device, int wanted, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices() || level == null) {
            return false;
        }
        if (!(level.getBlockEntity(device) instanceof SecuritySensorBE)) {
            return false;
        }
        final int blocks = SensorKind.clampRadius(wanted);
        h.setSensorRadius(spot(device), blocks);
        note(actor, nameOf(device), "range " + blocks, SecurityLog.Verdict.CHANGED);
        setChanged();

        pushToDevices();
        return true;
    }

    public boolean setSensorArmed(BlockPos device, boolean value, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices()) {
            return false;
        }

        if (level != null && !(level.getBlockEntity(device) instanceof SecuritySensorBE sensor
                && sensor.kind().family().armable())) {
            return false;
        }
        h.setSensorFlag(spot(device), value);
        note(actor, nameOf(device), value ? "armed" : "disarmed", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setLockerCode(BlockPos device, String digits, Player actor) {
        if (!onRoster(device) || !roleOf(actor).mayEditDevices() || level == null) {
            return false;
        }
        if (!(level.getBlockEntity(device) instanceof SecurityLockerBE locker)) {
            return false;
        }

        if (!governs(device)) {
            return false;
        }
        if (!locker.setCode(digits)) {
            return false;
        }

        note(actor, nameOf(device), digits == null || digits.isBlank() ? "code cleared" : "code set",
                SecurityLog.Verdict.CHANGED);
        setChanged();
        return true;
    }

    public boolean setShutterSealed(BlockPos device, boolean sealed, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices() || level == null) {
            return false;
        }
        if (!(level.getBlockEntity(device) instanceof BlastShutterBE)) {
            return false;
        }
        h.setShutterSealed(key(device), sealed);
        note(actor, nameOf(device), sealed ? "sealed" : "opened", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setShutterDusk(BlockPos device, boolean dusk, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !onRoster(device) || !roleOf(actor).mayEditDevices() || level == null) {
            return false;
        }
        if (!(level.getBlockEntity(device) instanceof BlastShutterBE)) {
            return false;
        }
        h.setShutterDusk(key(device), dusk);
        note(actor, nameOf(device), dusk ? "closes at dusk" : "dusk off", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean sealAllShutters(boolean sealed, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel() || level == null) {
            return false;
        }
        int touched = 0;
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.deviceClass() != SecurityDevice.DeviceClass.SHUTTER || !level.isLoaded(d.pos())) {
                continue;
            }
            final SecurityEndpoint anchor = key(d.pos());
            if (h.shutterSealed(anchor) != sealed) {
                h.setShutterSealed(anchor, sealed);
                touched++;
            }
        }
        if (touched == 0) {
            return false;
        }
        note(actor, "panel", sealed ? "all shutters sealed" : "all shutters opened",
                SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setRepellerOn(BlockPos device, boolean on, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditRepeller(h, device, actor)) {
            return false;
        }
        h.setRepellerOn(spot(device), on);
        note(actor, nameOf(device), on ? "switched on" : "switched off", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setRepellerLocked(BlockPos device, boolean locked, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditRepeller(h, device, actor)) {
            return false;
        }
        h.setRepellerLocked(spot(device), locked);
        note(actor, nameOf(device), locked ? "locked: panel only" : "unlocked", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setRepellerRedstone(BlockPos device, boolean hears, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditRepeller(h, device, actor)) {
            return false;
        }
        h.setRepellerHearsRedstone(spot(device), hears);
        note(actor, nameOf(device), hears ? "obeys redstone" : "ignores redstone", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean tuneRepeller(BlockPos device, String station, int range, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditRepeller(h, device, actor) || !governs(device)
                || !(level.getBlockEntity(device) instanceof MobRepellerBE repeller)) {
            return false;
        }
        if (!repeller.retuneFromPanel(station, range)) {
            return false;
        }
        note(actor, nameOf(device), repeller.tuningLine(), SecurityLog.Verdict.CHANGED);
        setChanged();
        return true;
    }

    public boolean switchAllRepellers(boolean on, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel() || level == null) {
            return false;
        }
        int touched = 0;
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.deviceClass() != SecurityDevice.DeviceClass.REPELLER || !level.isLoaded(d.pos())) {
                continue;
            }
            if (h.repellerOn(spot(d.pos())) != on) {
                h.setRepellerOn(spot(d.pos()), on);
                touched++;
            }
        }
        if (touched == 0) {
            return false;
        }
        note(actor, "panel", on ? "all repellers on" : "all repellers off", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean surveyRepeller(BlockPos device, net.minecraft.server.level.ServerPlayer viewer) {
        if (level == null || !onRoster(device) || !governs(device)
                || !(level.getBlockEntity(device) instanceof MobRepellerBE repeller)) {
            return false;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,
                new com.createstats.network.TunerStationsPacket(device, repeller.surveyStations(viewer)));
        return true;
    }

    private boolean mayEditRepeller(@Nullable SecurityHousehold h, BlockPos device, Player actor) {
        return h != null && level != null && device != null && onRoster(device)
                && roleOf(actor).mayEditDevices()
                && level.getBlockEntity(device) instanceof MobRepellerBE;
    }

    public boolean setCoilOn(BlockPos device, boolean on, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditCoil(h, device, actor)) {
            return false;
        }
        h.setCoilOn(spot(device), on);
        note(actor, nameOf(device), on ? "on guard" : "stood down", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setCoilLocked(BlockPos device, boolean locked, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditCoil(h, device, actor)) {
            return false;
        }
        h.setCoilLocked(spot(device), locked);
        note(actor, nameOf(device), locked ? "locked: panel only" : "unlocked", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setCoilRedstone(BlockPos device, boolean hears, Player actor) {
        final SecurityHousehold h = household();
        if (!mayEditCoil(h, device, actor)) {
            return false;
        }
        h.setCoilHearsRedstone(spot(device), hears);
        note(actor, nameOf(device), hears ? "obeys redstone" : "ignores redstone", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean setCoilRule(BlockPos device, @Nullable CoilRules.HouseState state, @Nullable CoilRules.Rule rule,
                               Player actor) {
        final SecurityHousehold h = household();
        if (state == null || rule == null || !mayEditCoil(h, device, actor)) {
            return false;
        }
        final SecurityEndpoint at = spot(device);
        CoilRules.Grid grid = h.coilRules(at);
        if (grid == null) {
            if (!(level.getBlockEntity(device) instanceof CoilDevice coil)) {
                return false;
            }
            grid = CoilRules.Grid.ofMode(coil.coilReading().targets());
        }
        if (grid.rule(state).equals(rule)) {
            return false;
        }
        h.setCoilRules(at, grid.with(state, rule));
        note(actor, nameOf(device), state.plain() + ": " + rule.plain(), SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    public boolean switchAllCoils(boolean on, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel() || level == null) {
            return false;
        }
        int touched = 0;
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.deviceClass() != SecurityDevice.DeviceClass.COIL || !level.isLoaded(d.pos())) {
                continue;
            }
            if (h.coilOn(spot(d.pos())) != on) {
                h.setCoilOn(spot(d.pos()), on);
                touched++;
            }
        }
        if (touched == 0) {
            return false;
        }
        note(actor, "panel", on ? "all coils on guard" : "all coils stood down", SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    private boolean mayEditCoil(@Nullable SecurityHousehold h, BlockPos device, Player actor) {
        return h != null && level != null && device != null && onRoster(device)
                && roleOf(actor).mayEditDevices()
                && level.getBlockEntity(device) instanceof CoilDevice;
    }

    public void coilFought(BlockPos coil, int targets, int volleys, int seconds) {
        final SecurityHousehold h = household();
        if (h == null || level == null || coil == null) {
            return;
        }
        h.log().add(level.getGameTime(), "coil", nameOf(coil),
                "fought " + targets + (targets == 1 ? " target, " : " targets, ") + volleys
                        + (volleys == 1 ? " volley in " : " volleys in ") + seconds + " s",
                SecurityLog.Verdict.FAULT);
        setChanged();
    }

    public void coilArcedAt(BlockPos coil, String player) {
        final SecurityHousehold h = household();
        if (h == null || level == null || coil == null) {
            return;
        }
        h.log().add(level.getGameTime(), "coil", nameOf(coil), "arced at " + player, SecurityLog.Verdict.DENIED);
        setChanged();
    }

    public void coilBanked(BlockPos coil, int banked, int held) {
        final SecurityHousehold h = household();
        if (h == null || level == null || coil == null) {
            return;
        }
        h.log().add(level.getGameTime(), "coil", nameOf(coil),
                "banked a lightning strike (" + banked + " of " + held + ")", SecurityLog.Verdict.CHANGED);
        setChanged();
    }

    public boolean setMode(SecurityMode wanted, Player actor) {
        final SecurityHousehold h = household();
        if (wanted == null || h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        if (h.mode() == wanted && !h.lockdown()) {
            return false;
        }
        if (wanted.sealsHouse() || h.mode().sealsHouse()) {
            return demandCodeForMode(h, wanted, actor);
        }
        commitMode(h, wanted, actor);
        return true;
    }

    private boolean demandCodeForMode(SecurityHousehold h, SecurityMode wanted, Player actor) {
        if (!(actor instanceof net.minecraft.server.level.ServerPlayer sender) || level == null) {
            return false;
        }

        if (h.mode() == wanted) {
            return false;
        }
        if (!h.roster().isOwner(sender.getUUID())) {
            sender.displayClientMessage(Component.translatable(
                    "message.create_stats.security.seal_owner_only"), true);
            return false;
        }

        if (!h.hasPassword()) {
            sender.displayClientMessage(Component.translatable(
                    "message.create_stats.security.seal_needs_password"), true);
            return false;
        }

        forgetPendingJoin(sender.getUUID());
        this.pendingMode = new PendingModeChange(wanted, sender.getUUID(),
                level.getGameTime() + JOIN_WINDOW);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.askToSeal(worldPosition, h.house(),
                        wanted.sealsHouse()));
        return false;
    }

    private void commitMode(SecurityHousehold h, SecurityMode wanted, @Nullable Player actor) {
        h.setMode(wanted);
        if (h.lockdown() && !wanted.isAway()) {
            releaseLockdown(actor);
        }
        note(actor, "panel", "mode " + wanted.plain(), SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
    }

    @Nullable
    private SecurityMode pendingModeChangeFor(net.minecraft.server.level.ServerPlayer sender,
                                              long now) {
        final PendingModeChange note = this.pendingMode;
        if (note == null) {
            return null;
        }

        if (!note.by().equals(sender.getUUID())) {
            return null;
        }
        if (now >= note.expires()) {
            this.pendingMode = null;
            return null;
        }
        final SecurityHousehold h = household();
        if (h == null || !h.roster().isOwner(sender.getUUID()) || !h.hasPassword()
                || h.mode() == note.target()
                || !(note.target().sealsHouse() || h.mode().sealsHouse())) {
            this.pendingMode = null;
            return null;
        }
        return note.target();
    }

    private void forgetPendingMode(UUID player) {
        final PendingModeChange note = this.pendingMode;
        if (note != null && note.by().equals(player)) {
            this.pendingMode = null;
        }
    }

    private void answerModeChange(net.minecraft.server.level.ServerPlayer sender,
                                  SecurityMode wanted, String digits, long now) {
        final SecurityHousehold h = household();
        if (h == null) {
            return;
        }
        final com.createstats.security.LockerCode.Result result = h.offerPassword(digits, now);
        if (result == com.createstats.security.LockerCode.Result.OPENED) {
            forgetPendingMode(sender.getUUID());
            commitMode(h, wanted, sender);
            admit(sender, h, now);
            return;
        }
        if (result == com.createstats.security.LockerCode.Result.TAMPER) {
            tripped(worldPosition, SensorKind.TAMPER,
                    sender.getGameProfile().getName() + " failed the panel code");
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                com.createstats.network.OpenSecurityPanelPacket.verdict(worldPosition, result,
                        (h.passwordLockout(now) + 19) / 20, h.house(),
                        wanted.sealsHouse()
                                ? com.createstats.network.OpenSecurityPanelPacket.SEAL_ARM
                                : com.createstats.network.OpenSecurityPanelPacket.SEAL_STAND_DOWN));
    }

    public boolean clearLockdown(Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !h.lockdown() || h.mode().sealsHouse()
                || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        releaseLockdown(actor);
        setChanged();
        pushToDevices();
        return true;
    }

    private void releaseLockdown(@Nullable Player actor) {
        final SecurityHousehold h = household();
        if (h == null) {
            return;
        }
        h.clearLockdown();
        note(actor, "panel", "lockdown cleared", SecurityLog.Verdict.CHANGED);
    }

    public boolean setEntrance(BlockPos device, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel() || level == null) {
            return false;
        }

        if (device != null && !isDoor(device)) {
            return false;
        }
        final SecurityEndpoint anchor = device == null ? null : key(device);
        if (anchor == null || h.isEntrance(anchor)) {
            h.clearEntrance();
        } else {
            h.setEntrance(anchor);
        }
        note(actor, h.hasEntrance() ? nameOf(device) : "panel",
                h.hasEntrance() ? "set as entrance" : "entrance cleared",
                SecurityLog.Verdict.CHANGED);
        setChanged();
        pushToDevices();
        return true;
    }

    private boolean isDoor(BlockPos pos) {
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.pos().equals(pos)) {
                return d.deviceClass() == SecurityDevice.DeviceClass.DOOR;
            }
        }
        return false;
    }

    public boolean setHouseName(String name, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || !roleOf(actor).mayEditPanel()) {
            return false;
        }
        h.setHouse(name);
        setChanged();
        return true;
    }

    public boolean tripped(BlockPos sensor, SensorKind kind, String cause) {
        final SecurityHousehold h = household();
        if (level == null || level.isClientSide() || h == null) {
            return false;
        }
        final long now = level.getGameTime();

        if (kind.isHazard()) {
            h.reportHazard(now, cause);
        }

        if (kind == SensorKind.FIRE) {
            final boolean alreadyReleased = h.released(now);
            h.reportFire(now);
            h.log().add(now, "sensor", nameOf(sensor), cause, SecurityLog.Verdict.FAULT);
            setChanged();
            if (!alreadyReleased) {
                pushToDevices();
            }
            return true;
        }

        if (kind.isHazard()) {
            h.log().add(now, "sensor", nameOf(sensor), cause, SecurityLog.Verdict.FAULT);
            setChanged();
            return true;
        }

        if (!sensorArmed(kind, sensor)) {
            h.log().add(now, "sensor", nameOf(sensor), cause, SecurityLog.Verdict.DENIED);
            setChanged();
            return false;
        }

        h.log().add(now, "sensor", nameOf(sensor), cause, SecurityLog.Verdict.FAULT);

        h.acceptEvent(kind == SensorKind.TAMPER ? SecurityEvent.TAMPER_ACCEPTED : SecurityEvent.INTRUSION_ACCEPTED,
                key(sensor), now);
        if (!h.lockdown()) {
            h.raiseLockdown(now, cause);
            pushToDevices();
        }
        setChanged();
        return true;
    }

    public void observeHazard(BlockPos sensor, SensorKind kind, HazardWatch.Report report, int sweepTicks,
                              int cooldownTicks) {
        final SecurityHousehold h = household();
        if (h == null || level == null || !kind.isHazard()) {
            return;
        }
        final HazardWatch.Hazard hazard = kind == SensorKind.FIRE ? HazardWatch.Hazard.FIRE : HazardWatch.Hazard.WATER;
        h.hazards().report(spot(sensor), hazard, report, level.getGameTime(), sweepTicks, cooldownTicks);
    }

    private void pushToDevices() {
        final SecurityHousehold h = household();
        if (level == null || level.isClientSide() || h == null) {
            return;
        }
        for (SecuritySegment.Device d : unionDevices()) {
            if (!level.isLoaded(d.pos())) {
                continue;
            }

            if (level.getBlockEntity(d.pos()) instanceof GovernedDevice governed) {
                governed.repoll();
            }
        }

        for (SecurityEndpoint key : h.panels()) {
            if (key.equals(spot(worldPosition)) || !inThisWorld(key)) {
                continue;
            }
            final BlockPos at = BlockPos.of(key.pos());
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof HomeSecurityPanelBE sibling) {
                sibling.markScanDue();
            }
        }
    }

    private boolean mayTouch(Player actor, SecurityRoster.Entry target) {
        final SecurityHousehold h = household();
        final SecurityRole role = roleOf(actor);
        if (h == null) {
            return false;
        }
        if (role.isOwner()) {
            return !h.roster().isOwner(target.id());
        }
        return role == SecurityRole.ADMIN && target.role() == SecurityRole.MEMBER;
    }

    private boolean governs(BlockPos pos) {
        final SecurityHousehold h = household();
        if (h == null || level == null || pos == null) {
            return false;
        }
        final SecurityDevice device = SecurityDevices.of(level.getBlockEntity(pos));
        if (device == null) {
            return false;
        }

        final UUID remembered = device.governingHousehold();
        if (remembered != null) {
            return remembered.equals(h.id()) || SecurityIdentity.free(level, remembered, pos);
        }
        final BlockPos governor = device.governingPanel();
        if (governor == null) {
            return true;
        }
        if (!level.isLoaded(governor)) {
            return false;
        }
        if (!(level.getBlockEntity(governor) instanceof HomeSecurityPanelBE panel)) {
            return true;
        }
        final UUID id = panel.householdId();
        return id == null || id.equals(h.id());
    }

    public boolean listsDevice(BlockPos pos) {
        return pos != null && onRoster(pos);
    }

    private boolean onRoster(BlockPos pos) {
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private String nameOf(BlockPos pos) {
        for (SecuritySegment.Device d : unionDevices()) {
            if (d.pos().equals(pos)) {
                return d.name();
            }
        }
        return "device";
    }

    private void note(Player actor, String target, String detail, SecurityLog.Verdict verdict) {
        final SecurityHousehold h = household();
        if (h == null) {
            return;
        }
        h.log().add(level == null ? 0L : level.getGameTime(),
                actor == null ? "console" : actor.getGameProfile().getName(),
                target, detail, verdict);
    }

    public void recordDevice(@Nullable Player actor, BlockPos device, String detail,
                             SecurityLog.Verdict verdict) {
        note(actor, nameOf(device), detail, verdict);
        setChanged();
    }

    public void recordAccess(Player player, String device, int required, boolean passed) {
        recordAccess(player, null, device, required, passed);
    }

    public void recordAccess(Player player, @Nullable BlockPos devicePos, String device, int required,
                             boolean passed) {
        final SecurityHousehold h = household();
        if (player == null || h == null) {
            return;
        }
        final UUID id = player.getUUID();
        final long now = level == null ? 0L : level.getGameTime();
        h.log().access(now, player.getGameProfile().getName(),
                device, h.roster().isOwner(id), h.roster().levelOf(id), required, passed);
        h.roster().touch(id, System.currentTimeMillis());
        if (!passed && level != null) {
            h.acceptEvent(SecurityEvent.ACCESS_DENIED, key(devicePos == null ? worldPosition : devicePos), now);
        }
        setChanged();
    }

    public void recordDenied(@Nullable Player actor, BlockPos device, String detail) {
        final SecurityHousehold h = household();
        if (h == null || level == null) {
            return;
        }
        note(actor, nameOf(device), detail, SecurityLog.Verdict.DENIED);
        h.acceptEvent(SecurityEvent.ACCESS_DENIED, key(device), level.getGameTime());
        setChanged();
    }

    @Nullable
    public RelaySubscription relayAt(BlockPos pos) {
        final SecurityHousehold h = household();
        if (h == null || level == null || pos == null) {
            return null;
        }
        final SecurityEndpoint at = spot(pos);
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RelayDevice standing) {
            final RelaySubscription own = h.relayStandingAt(standing.relayId(), at, level.getGameTime());
            return own != null && own.endpoint().equals(at) ? own : null;
        }
        for (RelaySubscription s : h.relays()) {
            if (s.endpoint().equals(at)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public Component relayAction(net.minecraft.server.level.ServerPlayer actor, BlockPos relayPos, int action,
                                 RelayConfig wanted, long expectedRevision) {
        final SecurityHousehold h = household();
        if (h == null || level == null || actor == null || relayPos == null || !onRoster(relayPos)) {
            return null;
        }
        final long now = level.getGameTime();
        final String who = actor.getGameProfile().getName();
        final RelayDevice device = level.isLoaded(relayPos)
                && level.getBlockEntity(relayPos) instanceof RelayDevice found ? found : null;
        final RelaySubscription sub = relayAt(relayPos);
        Component said = null;
        switch (action) {
            case com.createstats.network.SecurityRelayActionPacket.ACCEPT -> {
                final boolean ok = device != null && device.relayId() != null
                        && device.contact() == RelayContact.CANDIDATE
                        && h.claimRelay(device.relayId(), spot(relayPos), actor.getUUID(), who, now) != null;
                said = Component.translatable(ok ? "message.create_stats.security_relay.accepted"
                        : "message.create_stats.security_relay.not_accepted");
            }
            case com.createstats.network.SecurityRelayActionPacket.CONFIGURE -> {
                if (sub != null) {
                    said = com.createstats.network.SecurityRelayActionPacket.configVerdict(
                            h.configureRelay(sub.relayId(), wanted, expectedRevision, actor.getUUID(), who, now));
                }
            }
            case com.createstats.network.SecurityRelayActionPacket.RESET -> {
                if (sub != null) {
                    said = com.createstats.network.SecurityRelayActionPacket.resetVerdict(h.resetRelay(
                            sub.relayId(), actor.getUUID(), who,
                            device != null && device.contact() == RelayContact.LINKED, now));
                }
            }
            case com.createstats.network.SecurityRelayActionPacket.RETIRE -> {
                if (sub != null) {
                    said = com.createstats.network.SecurityRelayActionPacket.retireVerdict(
                            h.retireRelay(sub.relayId(), actor.getUUID(), who, now));
                }
            }
            default -> {
            }
        }
        if (device != null) {
            device.repoll();
        }
        setChanged();
        return said;
    }

    public boolean forgetDevice(BlockPos device, Player actor) {
        final SecurityHousehold h = household();
        if (h == null || level == null || actor == null || device == null) {
            return false;
        }
        final SecurityEndpoint at = key(device);
        final SecurityEnrollment e = h.enrollment(at);
        if (e == null) {
            return false;
        }
        final boolean done;
        if (SecurityKinds.RELAY.equals(e.kind())) {
            final RelaySubscription sub = relayAt(BlockPos.of(at.pos()));
            done = sub != null && h.retireRelay(sub.relayId(), actor.getUUID(), actor.getGameProfile().getName(),
                    level.getGameTime()) == RelayRules.RetireVerdict.RETIRED;
        } else {
            done = h.retire(at, actor.getUUID(), actor.getGameProfile().getName(), level.getGameTime());
        }
        if (done) {
            release(BlockPos.of(at.pos()));
            rebuild();
            setChanged();
        }
        return done;
    }

    public int legacyUnresolved() {
        final SecurityHousehold h = household();
        return h == null ? 0 : h.legacyUnresolved();
    }

    @Override
    public BlockPos linkerPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> linkedFixtures() {
        return List.copyOf(manual);
    }

    @Override
    public boolean accepts(BlockEntity fixture) {
        return SecurityDevices.isDevice(fixture);
    }

    // only ever unbinds. the old UNLINK packet could bind something that was never linked
    public void toggleLinkIfLinked(BlockPos fixture, Player actor) {
        if (fixture != null && manual.contains(fixture.immutable())) {
            link(fixture, actor);
        }
    }

    @Override
    public boolean mayLink(@Nullable Player actor) {
        return actor != null && level != null && !level.isClientSide() && roleOf(actor).mayEditDevices();
    }

    @Override
    @Nullable
    public UUID linkIdentity() {
        final SecurityHousehold h = household();
        return h == null ? null : h.id();
    }

    @Override
    public Outcome link(BlockPos fixture, @Nullable Player actor) {
        if (!mayLink(actor) || fixture == null) {
            return Outcome.REFUSED;
        }
        final BlockPos pos = fixture.immutable();

        final BlockPos twin = SecuritySegment.pairedWith(level, pos);
        if (manual.remove(pos) | (twin != null && manual.remove(twin))) {
            release(pos);
            if (twin != null) {
                release(twin);
            }
            rebuild();
            setChanged();
            return Outcome.UNLINKED;
        }
        if (!SecurityDevices.isDevice(level.getBlockEntity(pos)) || !governs(pos)) {
            return Outcome.REFUSED;
        }
        manual.add(pos);

        if (twin != null && !manual.contains(twin) && SecurityDevices.isDevice(level.getBlockEntity(twin))) {
            manual.add(twin.immutable());
        }
        rebuild();
        setChanged();
        return Outcome.LINKED;
    }

    private void release(BlockPos pos) {
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GovernedDevice governed) {
            governed.releasePanel(worldPosition);
        }
    }

    @Override
    public boolean toggleLink(BlockPos fixture) {
        return false;
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
        return 64;
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
        sinceScan = RESCAN_TICKS;
    }

    @Override
    public BlockPos borePosition() {
        return worldPosition;
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Home Security Panel"));
        if (lockedDown()) {
            tooltip.add(com.createstats.power.FeGoggle.bad("LOCKDOWN, owner must clear it"));
        } else if (released()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Fire release, every door is open"));
        } else if (hazard()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Hazard reported, see the panel"));
        } else if (!isAwake()) {
            tooltip.add(com.createstats.power.FeGoggle.bad("No power, discovery paused"));
        } else if (hasFault()) {
            tooltip.add(com.createstats.power.FeGoggle.bad(shownFaults + " needing attention"));
        } else if (shownDeviceCount() == 0) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Nothing found on this circuit"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.ok(shownDeviceCount() + " secured"));
        }
        tooltip.add(com.createstats.power.FeGoggle.dim("Mode: " + mode().plain()));
        tooltip.add(com.createstats.power.FeGoggle.draw(measuredDraw));
        return true;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
        if (unloading) {
            return;
        }

        if (level == null || level.isClientSide() || householdId == null) {
            return;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        final SecurityHousehold h = store == null ? null : store.find(householdId);
        if (h == null) {
            return;
        }
        h.removePanel(spot(worldPosition));
        if (h.panels().isEmpty() && h.isEmpty()) {
            store.drop(householdId);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());

        if (householdId != null) {
            tag.putUUID("Household", householdId);
        } else if (legacy != null) {
            tag.merge(legacy);
        }

        final ListTag linkList = new ListTag();
        for (BlockPos pos : manual) {
            final CompoundTag row = new CompoundTag();
            row.putLong("P", pos.asLong());
            linkList.add(row);
        }
        tag.put("Links", linkList);

        final ListTag boreList = new ListTag();
        for (BlockPos pos : bores) {
            final CompoundTag row = new CompoundTag();
            row.putLong("P", pos.asLong());
            boreList.add(row);
        }
        tag.put("Bores", boreList);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Awake", awake);
        tag.putInt("Draw", measuredDraw);

        tag.putInt("Shown", unionDevices().size());
        tag.putInt("Faults", faultCount());

        tag.putInt("Mode", mode().ordinal());
        tag.putBoolean("Lockdown", lockedDown());

        tag.putBoolean("Released", released());
        tag.putBoolean("Hazard", hazard());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        awake = tag.getBoolean("Awake");
        measuredDraw = tag.getInt("Draw");
        shownDevices = tag.getInt("Shown");
        shownFaults = tag.getInt("Faults");
        shownMode = SecurityMode.byOrdinal(tag.getInt("Mode"));

        shownLockdown = tag.getBoolean("Lockdown");
        shownReleased = tag.getBoolean("Released");
        shownHazard = tag.getBoolean("Hazard");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("Energy");

        if (tag.contains("Tier")) {
            managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }

        if (tag.hasUUID("Household")) {
            householdId = tag.getUUID("Household");
            legacy = null;
        } else {
            householdId = null;
            legacy = tag.copy();
        }

        manual.clear();
        final ListTag linkList = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < linkList.size(); i++) {
            manual.add(BlockPos.of(linkList.getCompound(i).getLong("P")));
        }

        bores.clear();
        final ListTag boreList = tag.getList("Bores", Tag.TAG_COMPOUND);
        for (int i = 0; i < boreList.size(); i++) {
            bores.add(BlockPos.of(boreList.getCompound(i).getLong("P")));
        }

        sinceScan = RESCAN_TICKS;
    }
}
