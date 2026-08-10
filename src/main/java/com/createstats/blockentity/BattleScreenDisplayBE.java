package com.createstats.blockentity;

import com.createstats.CreateStats;
import com.createstats.battle.BattleLightSnapshot;
import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.battle.BattleObserverSnapshotFactory;
import com.createstats.battle.BattleWeaponSnapshot;
import com.createstats.compat.CreateBigCannonsCompat;
import com.createstats.compat.CreateBigCannonsReflectionAdapter;
import com.createstats.flight.FlightConfig;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BattleScreenDisplayBE extends BlockEntity implements com.createstats.power.IFeConsumer {

    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_BATTLE_SCREEN));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    public static final int LINKED_CONTROLLER_BUTTON_COUNT = 6;
    public static final int LINKED_CONTROLLER_SLOT_COUNT = LINKED_CONTROLLER_BUTTON_COUNT * 2;
    public static final int LINKED_CONTROLLER_MANUAL_AIM_RPM_MIN = 16;
    public static final int LINKED_CONTROLLER_MANUAL_AIM_RPM_MAX = 128;
    public static final int LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT = 24;

    private static final int LINK_STALE_TICKS = 160;
    private static final int AUTO_FIRE_INTERVAL_TICKS = 4;
    private static final double AUTO_AIM_SCAN_RADIUS = 96.0;
    // firing solution / lead prediction tuning
    private static final int AUTO_AIM_PREDICTION_ITERATIONS = 4;        // intercept solver passes
    private static final double AUTO_AIM_MAX_LEAD_TICKS = 200.0;        // 10s flight-time cap
    private static final double AUTO_AIM_MAX_LEAD_DISTANCE = 512.0;     // clamp wild lead offsets (blocks)
    private static final double AUTO_AIM_MAX_SAMPLE_GAP_TICKS = 10.0;   // reject stale finite-diff samples
    private static final double AUTO_AIM_MAX_TARGET_SPEED = 8.0;        // blocks/tick sanity (~160 b/s)
    private static final double AUTO_AIM_MAX_TARGET_ACCEL = 4.0;        // blocks/tick^2 sanity
    private static final double AUTO_AIM_PROJECTILE_GRAVITY = 0.05;     // blocks/tick^2 drop compensation (legacy)
    // [1]
    private static final double AUTO_AIM_DEFAULT_PROJECTILE_GRAVITY = -0.05;  // CBC BallisticPropertiesComponent gravity default (downward, blocks/tick^2)
    private static final double AUTO_AIM_DEFAULT_PROJECTILE_DRAG = 0.0;       // CBC default: most shells declare no drag
    private static final boolean AUTO_AIM_DEFAULT_QUADRATIC_DRAG = false;
    private static final int AUTO_AIM_INTERCEPT_OUTER_ITERATIONS = 6;    // flight-time fixed-point passes
    private static final int AUTO_AIM_INTERCEPT_INNER_ITERATIONS = 8;    // launch-direction aim-point corrections
    private static final double AUTO_AIM_INTERCEPT_TIME_TOLERANCE = 0.5; // ticks: outer-loop convergence
    private static final double AUTO_AIM_INTERCEPT_MISS_TOLERANCE = 0.35; // blocks: inner-loop convergence
    private static final int AUTO_AIM_SIM_MAX_TICKS = 220;              // forward-sim horizon (lead cap + margin)
    private static final int AUTO_AIM_ADVANCED_STICKY_TICKS = 12;       // reuse last physics velocity across brief dropouts
    private static final double AUTO_AIM_ADVANCED_COM_VEL_EMA = 0.35;   // EMA weight for the physics COM velocity
    private static final double AUTO_AIM_ADVANCED_MAX_ROTATIONAL = 3.0; // cap omega x r contribution (blocks/tick)
    private static final double AUTO_AIM_ADVANCED_MAX_VEL_STEP = 0.6;   // max plausible COM velocity change (blocks/tick per tick)
    private static final int AUTO_AIM_VEL_WINDOW_TICKS = 4;             // position-difference velocity averaging window (shorter = reacts faster to maneuvers)
    private static final int AUTO_AIM_TRACK_HISTORY = 12;              // max tracked position samples kept per source
    private static final float AUTO_AIM_DEADBAND_DEGREES = 0.08f;
    private static final float AUTO_AIM_RESPONSE_GAIN = 0.2f;
    private static final float AUTO_AIM_MAX_STEP_DEGREES = 1.5f;
    private static final float AUTO_AIM_FIRE_ALIGNMENT_DEGREES = 2.5f;
    private static final float AUTO_AIM_GOVERNOR_RPM_PER_DEGREE = 4.0f;
    private static final int AUTO_AIM_GOVERNOR_MAX_RPM = 64;
    private static final int AUTO_AIM_GOVERNOR_MAX_RESPONSE_RPM = 256;
    private static final float AUTO_AIM_RESPONSE_MIN = 1.0f;
    private static final float AUTO_AIM_RESPONSE_MAX = 5.0f;
    private static final float AUTO_AIM_RESPONSE_DEFAULT = 1.0f;
    /** [2] */
    private static final float AUTO_AIM_ADVANCED_RESPONSE_BOOST = 2.0f;
    private static final float BIG_CANNON_AUTO_AIM_DEADBAND_DEGREES = 0.75f;
    private static final float BIG_CANNON_AUTO_AIM_FULL_AUTHORITY_ERROR_DEGREES = 90.0f;
    private static final float BIG_CANNON_AUTO_AIM_MIN_AUTHORITY_SCALE = 0.25f;
    private static final int BIG_CANNON_AUTO_AIM_GOVERNOR_BASE_RPM = 16;
    private static final int BIG_CANNON_AUTO_AIM_GOVERNOR_MAX_RPM = 64;
    private static final float BIG_CANNON_AUTO_AIM_RESPONSE_MIN = 0.25f;
    private static final float BIG_CANNON_AUTO_AIM_RESPONSE_MAX = 1.0f;
    private static final float BIG_CANNON_AUTO_AIM_RESPONSE_DEFAULT = 0.25f;
    private static final double RAW_SUBLEVEL_COORDINATE_THRESHOLD = 1_000_000.0;
    private static final int LINKED_CONTROLLER_PITCH_UP = 0;
    private static final int LINKED_CONTROLLER_PITCH_DOWN = 1;
    private static final int LINKED_CONTROLLER_YAW_LEFT = 2;
    private static final int LINKED_CONTROLLER_YAW_RIGHT = 3;
    private static final int LINKED_CONTROLLER_FIRE = 4;
    private static final int LINKED_CONTROLLER_LOCK = 5;
    private static final int LINKED_CONTROLLER_BIG_CANNON_FIRE_INTERVAL_TICKS = 4;
    private static final float LINKED_CONTROLLER_LOCK_MAX_ERROR_DEGREES = 9.0f;

    private final Map<BlockPos, LinkedObserver> linkedObservers = new LinkedHashMap<>();
    private final Set<BlockPos> autoFireObservers = new HashSet<>();
    private final Map<BlockPos, Long> autoFireAttemptTicks = new HashMap<>();
    private final Map<BlockPos, Long> autoFireLastDebugTicks = new HashMap<>();
    private final Map<BlockPos, Long> autoFirePulseReleaseTicks = new HashMap<>();
    private final Map<BlockPos, UUID> autoAimLockedTargets = new HashMap<>();
    private final Map<BlockPos, String> autoAimBlockedReasons = new HashMap<>();
    private final Map<BlockPos, Long> autoAimLastDriveFailureTicks = new HashMap<>();
    private final Set<BlockPos> autoAimHoldDisabledObservers = new HashSet<>();
    private final Set<BlockPos> autoAimPredictionObservers = new HashSet<>();
    private final Set<BlockPos> autoAimAdvancedPredictionObservers = new HashSet<>();
    private final Map<BlockPos, Vec3> autoAimSmoothedComVelocities = new HashMap<>();
    private final Map<BlockPos, Long> autoAimLastComVelocityTicks = new HashMap<>();
    private final Map<BlockPos, Vec3> autoAimLastGravityVectors = new HashMap<>();
    private final Map<BlockPos, ArrayDeque<double[]>> autoAimPositionHistory = new HashMap<>();
    private final Map<BlockPos, Long> autoAimPredictionResultTicks = new HashMap<>();
    private final Map<BlockPos, PredictionResolution> autoAimPredictionResults = new HashMap<>();
    private final Map<BlockPos, Float> autoAimResponseMultipliers = new HashMap<>();
    private final Map<BlockPos, UUID> autoAimLastPredictionTargetIds = new HashMap<>();
    private final Map<BlockPos, Vec3> autoAimLastTargetPoints = new HashMap<>();
    private final Map<BlockPos, Long> autoAimLastTargetTicks = new HashMap<>();
    private final Map<BlockPos, Vec3> autoAimSmoothedTargetVelocities = new HashMap<>();
    private final Map<BlockPos, Vec3> autoAimSmoothedTargetAccels = new HashMap<>();
    private final Map<BlockPos, List<BlockPos>> autoAimGovernorPositions = new HashMap<>();
    private final Map<BlockPos, Integer> autoAimLastGovernorTargets = new HashMap<>();
    private final Map<BlockPos, Long> autoAimLastGovernorCommandTicks = new HashMap<>();
    private final Map<BlockPos, ItemStackHandler> linkedControllerInventories = new LinkedHashMap<>();
    private final Map<BlockPos, Map<Integer, BattleLinkedControllerReceiver>> linkedControllerReceivers = new LinkedHashMap<>();
    private final Map<BlockPos, int[]> linkedControllerReceivedStrengths = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> linkedControllerLastInputMasks = new LinkedHashMap<>();
    private final Map<BlockPos, Long> linkedControllerLastFireTicks = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> linkedControllerManualAimRpms = new LinkedHashMap<>();

    // [3]
    @Nullable private volatile List<BattleObserverSnapshot> battleFeedSnapshotCache = null;

    public BattleScreenDisplayBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void acceptLinkedObserver(DamageObserverBE observer, BlockPos sourcePos) {
        observer.forceScanObservedVolume();
        long tick = this.level == null ? 0L : this.level.getGameTime();
        LinkedObserver linked = this.captureLinkedObserver(observer, sourcePos, tick);
        this.putLinkedObserver(linked);
    }

    private LinkedObserver captureLinkedObserver(DamageObserverBE observer, BlockPos sourcePos, long seenTick) {
        BattleObserverSnapshot snapshot = BattleObserverSnapshotFactory.capture(observer, this.worldPosition, sourcePos, seenTick);
        AutoAimSnapshot autoAim = this.captureAutoAimSnapshot(observer, sourcePos);
        return LinkedObserver.fromBattleSnapshot(snapshot, this.isAutoFireEnabled(sourcePos), autoAim);
    }

    private void putLinkedObserver(LinkedObserver linked) {
        LinkedObserver previous = this.linkedObservers.get(linked.sourcePos());
        if (previous != null && previous.sameDisplayData(linked)) {
            this.linkedObservers.put(linked.sourcePos().immutable(), linked);
            if (this.level instanceof ServerLevel serverLevel) {
                this.refreshLinkedControllerReceivers(serverLevel, linked.sourcePos());
            }
            return;
        }
        this.linkedObservers.put(linked.sourcePos().immutable(), linked);
        if (this.level instanceof ServerLevel serverLevel) {
            this.refreshLinkedControllerReceivers(serverLevel, linked.sourcePos());
        }
        this.syncLinkedObservers();
    }

    private void refreshObserverRuntimeScan(DamageObserverBE observer) {
        if (SableBridge.subLevelAccessOf(observer) != null) {
            observer.forceScanObservedVolumeIfDue(4);
            return;
        }
        observer.forceScanObservedVolume();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // FE Dependent Mode gate: a dead tactical screen halts auto-aim, auto-fire, and observer
        // runtime scans until the buffer is charged again.
        final boolean fePowered = this.feConsumer.serviceTick(!this.linkedObservers.isEmpty());
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        long now = this.level.getGameTime();
        this.processFirePulses(now);
        boolean changed = this.processAutoAimDrive(now);
        changed |= this.processAutoFire(now);
        changed |= this.processLinkedController(now);
        if (this.linkedObservers.isEmpty()) {
            if (changed) {
                this.syncLinkedObservers();
            }
            return;
        }
        if (now % 20L != 0L) {
            if (changed) {
                this.syncLinkedObservers();
            }
            return;
        }
        Iterator<Map.Entry<BlockPos, LinkedObserver>> iterator = this.linkedObservers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, LinkedObserver> entry = iterator.next();
            LinkedObserver linked = entry.getValue();
            if (linked.lastSeenTick() > 0L && now - linked.lastSeenTick() > LINK_STALE_TICKS) {
                this.clearAutoFireState(linked.sourcePos());
                changed |= this.clearAutoAimState(linked.sourcePos());
                iterator.remove();
                changed = true;
                continue;
            }
            if (!this.level.isLoaded(linked.sourcePos())) {
                continue;
            }
            BlockEntity blockEntity = this.level.getBlockEntity(linked.sourcePos());
            if (!(blockEntity instanceof DamageObserverBE observer)) {
                this.clearAutoFireState(linked.sourcePos());
                changed |= this.clearAutoAimState(linked.sourcePos());
                iterator.remove();
                changed = true;
                continue;
            }
            this.refreshObserverRuntimeScan(observer);
            LinkedObserver refreshed = this.captureLinkedObserver(observer, linked.sourcePos(), now);
            if (!linked.sameDisplayData(refreshed)) {
                entry.setValue(refreshed);
                changed = true;
            }
        }
        if (changed) {
            this.syncLinkedObservers();
        }
    }

    public void removeLinkedObserver(BlockPos sourcePos) {
        this.releaseAutocannonFireSignal(sourcePos);
        boolean removed = this.linkedObservers.remove(sourcePos) != null;
        removed |= this.clearAutoFireState(sourcePos);
        removed |= this.clearAutoAimState(sourcePos);
        removed |= this.clearLinkedControllerRuntimeState(sourcePos, true);
        this.removeLinkedControllerReceivers(sourcePos);
        if (removed) {
            this.syncLinkedObservers();
        }
    }

    public List<LinkedObserver> getLinkedObservers() {
        return List.copyOf(this.linkedObservers.values());
    }

    public List<BattleObserverSnapshot> getBattleFeedSnapshots() {
        List<BattleObserverSnapshot> cached = this.battleFeedSnapshotCache;
        if (cached != null) {
            return cached;
        }
        List<BattleObserverSnapshot> snapshots = new ArrayList<>(this.linkedObservers.size());
        for (LinkedObserver observer : this.linkedObservers.values()) {
            snapshots.add(observer.asBattleSnapshot());
        }
        List<BattleObserverSnapshot> immutable = List.copyOf(snapshots);
        this.battleFeedSnapshotCache = immutable;
        return immutable;
    }

    public boolean hasLinkedObservers() {
        return !this.linkedObservers.isEmpty();
    }

    @Nullable
    public LinkedObserver getLinkedObserver(BlockPos sourcePos) {
        return sourcePos == null ? null : this.linkedObservers.get(sourcePos);
    }

    public boolean hasLinkedObserver(BlockPos sourcePos) {
        return this.linkedObservers.containsKey(sourcePos);
    }

    public static int getLinkedControllerSlotIndex(int button, boolean secondFrequency) {
        return button * 2 + (secondFrequency ? 1 : 0);
    }

    public ItemStackHandler getLinkedControllerInventory(BlockPos sourcePos) {
        return this.linkedControllerInventory(sourcePos.immutable());
    }

    public int getLinkedControllerManualAimRpm(BlockPos sourcePos) {
        return this.linkedControllerManualAimRpm(sourcePos.immutable());
    }

    public int setLinkedControllerManualAimRpm(BlockPos sourcePos, int rpm) {
        BlockPos key = sourcePos.immutable();
        if (!this.linkedObservers.containsKey(key)) {
            return LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT;
        }
        int clamped = clampLinkedControllerManualAimRpm(rpm);
        if (clamped == LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT) {
            this.linkedControllerManualAimRpms.remove(key);
        } else {
            this.linkedControllerManualAimRpms.put(key, clamped);
        }
        this.setChanged();
        return clamped;
    }

    public boolean toggleAutoFire(BlockPos sourcePos) {
        if (!this.linkedObservers.containsKey(sourcePos)) {
            return false;
        }
        BlockPos key = sourcePos.immutable();
        boolean enabled;
        if (this.autoFireObservers.remove(key)) {
            this.releaseAutocannonFireSignal(key);
            this.autoFireAttemptTicks.remove(key);
            this.autoFireLastDebugTicks.remove(key);
            this.autoFirePulseReleaseTicks.remove(key);
            enabled = false;
        } else {
            this.autoFireObservers.add(key);
            this.autoFireAttemptTicks.put(key, Long.MIN_VALUE);
            this.autoFireLastDebugTicks.put(key, Long.MIN_VALUE / 4);
            this.autoFirePulseReleaseTicks.remove(key);
            this.armAutocannonFireSignal(key, true);
            enabled = true;
        }
        this.syncLinkedObservers();
        return enabled;
    }

    public boolean pulseAutocannonFire(DamageObserverBE observer) {
        if (!this.setObservedAutocannonFireSignal(observer, true, 15)) {
            return false;
        }
        if (this.level != null) {
            this.autoFirePulseReleaseTicks.put(observer.getBlockPos().immutable(), this.level.getGameTime() + 2L);
        }
        return true;
    }

    public boolean toggleAutoAimHold(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        if (!this.linkedObservers.containsKey(key)) {
            return false;
        }
        boolean enabled;
        if (this.autoAimHoldDisabledObservers.remove(key)) {
            enabled = true;
        } else {
            this.autoAimHoldDisabledObservers.add(key);
            enabled = false;
        }
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return enabled;
    }

    public boolean toggleAutoAimPrediction(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        if (!this.linkedObservers.containsKey(key)) {
            return false;
        }
        boolean enabled;
        if (this.autoAimPredictionObservers.remove(key)) {
            this.resetAutoAimPredictionTrack(key);
            enabled = false;
        } else {
            this.autoAimPredictionObservers.add(key);
            enabled = true;
        }
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return enabled;
    }

    public boolean toggleAutoAimAdvancedPrediction(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        if (!this.linkedObservers.containsKey(key)) {
            return false;
        }
        boolean enabled;
        if (this.autoAimAdvancedPredictionObservers.remove(key)) {
            enabled = false;
        } else {
            // Advanced mode is a refinement of prediction; ensure base prediction is on too.
            this.autoAimPredictionObservers.add(key);
            this.autoAimAdvancedPredictionObservers.add(key);
            enabled = true;
        }
        this.resetAutoAimPredictionTrack(key);
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return enabled;
    }

    public float setAutoAimResponseMultiplier(BlockPos sourcePos, float multiplier) {
        BlockPos key = sourcePos.immutable();
        if (!this.linkedObservers.containsKey(key)) {
            return AUTO_AIM_RESPONSE_DEFAULT;
        }
        LinkedObserver linked = this.linkedObservers.get(key);
        String weaponKind = linked == null ? "" : linked.weapon().kind();
        float defaultMultiplier = defaultAutoAimResponse(weaponKind);
        float clamped = clampAutoAimResponse(weaponKind, multiplier, this.isAutoAimAdvancedPredictionEnabled(sourcePos));
        if (Math.abs(clamped - defaultMultiplier) <= 0.001f) {
            this.autoAimResponseMultipliers.remove(key);
        } else {
            this.autoAimResponseMultipliers.put(key, clamped);
        }
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return clamped;
    }

    public boolean toggleAutoAimLock(BlockPos sourcePos) {
        if (!(this.level instanceof ServerLevel serverLevel) || !this.linkedObservers.containsKey(sourcePos)) {
            return false;
        }
        BlockPos key = sourcePos.immutable();
        if (this.autoAimLockedTargets.remove(key) != null) {
            this.releaseAutoAimGovernors(key);
            this.refreshLinkedObserverSnapshot(key);
            this.syncLinkedObservers();
            return false;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(key);
        if (!(blockEntity instanceof DamageObserverBE observer)) {
            return false;
        }
        observer.forceScanObservedVolume();
        if (!this.supportsAutoAimWeapon(observer)) {
            return false;
        }
        if (observer.getWeaponMissingBlocks() > 0) {
            return false;
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim = this.findObservedMountAim(serverLevel, observer);
        if (!mountAim.available()) {
            return false;
        }
        AutoAimCandidate candidate = this.findNearestAutoAimCandidate(serverLevel, mountAim, observer);
        if (candidate == null) {
            return false;
        }
        this.autoAimLockedTargets.put(key, candidate.targetId());
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return true;
    }

    public boolean lockAutoAimTarget(BlockPos sourcePos, UUID targetId) {
        if (!(this.level instanceof ServerLevel serverLevel) || targetId == null || !this.linkedObservers.containsKey(sourcePos)) {
            return false;
        }
        BlockPos key = sourcePos.immutable();
        BlockEntity blockEntity = serverLevel.getBlockEntity(key);
        if (!(blockEntity instanceof DamageObserverBE observer)) {
            return false;
        }
        observer.forceScanObservedVolume();
        if (!this.supportsAutoAimWeapon(observer) || observer.getWeaponMissingBlocks() > 0) {
            return false;
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim = this.findObservedMountAim(serverLevel, observer);
        if (!mountAim.available()) {
            return false;
        }
        AutoAimCandidate candidate = this.findLockedAutoAimCandidate(serverLevel, mountAim, observer, targetId);
        if (candidate == null) {
            return false;
        }
        this.autoAimLockedTargets.put(key, candidate.targetId());
        this.refreshLinkedObserverSnapshot(key);
        this.syncLinkedObservers();
        return true;
    }

    public boolean clearAutoAimLock(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        boolean changed = this.autoAimLockedTargets.remove(key) != null;
        this.releaseAutoAimGovernors(key);
        if (changed) {
            this.refreshLinkedObserverSnapshot(key);
            this.syncLinkedObservers();
        }
        return changed;
    }

    public boolean lockFacingAutoAimTarget(BlockPos sourcePos) {
        return this.lockFacingAutoAimCandidate(sourcePos) != null;
    }

    @Nullable
    private AutoAimCandidate lockFacingAutoAimCandidate(BlockPos sourcePos) {
        if (!(this.level instanceof ServerLevel serverLevel) || !this.linkedObservers.containsKey(sourcePos)) {
            return null;
        }
        BlockPos key = sourcePos.immutable();
        BlockEntity blockEntity = serverLevel.getBlockEntity(key);
        if (!(blockEntity instanceof DamageObserverBE observer)) {
            return null;
        }
        observer.forceScanObservedVolume();
        if (!this.supportsAutoAimWeapon(observer) || observer.getWeaponMissingBlocks() > 0) {
            return null;
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim = this.findObservedMountAim(serverLevel, observer);
        if (!mountAim.available()) {
            return null;
        }
        AutoAimCandidate candidate = this.findFacingAutoAimCandidate(serverLevel, mountAim, observer);
        if (candidate == null) {
            return null;
        }
        UUID previous = this.autoAimLockedTargets.put(key, candidate.targetId());
        if (!candidate.targetId().equals(previous)) {
            this.refreshLinkedObserverSnapshot(key);
            this.syncLinkedObservers();
        }
        return candidate;
    }

    public boolean tryFireObservedWeapon(DamageObserverBE observer) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!serverLevel.isLoaded(pos)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(pos);
            if (CreateBigCannonsCompat.classifyPart(state) != CreateBigCannonsCompat.WeaponPart.MOUNT) {
                continue;
            }
            if (CreateBigCannonsReflectionAdapter.tryFireMountedCannon(serverLevel.getBlockEntity(pos), serverLevel)) {
                return true;
            }
        }
        BlockPos anchor = observer.getObservedAnchorPos();
        return serverLevel.isLoaded(anchor) && CreateBigCannonsReflectionAdapter.tryFireMountedCannon(serverLevel.getBlockEntity(anchor), serverLevel);
    }

    private boolean isAutoFireEnabled(BlockPos sourcePos) {
        return this.autoFireObservers.contains(sourcePos);
    }

    private boolean processAutoFire(long now) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.autoFireObservers.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<BlockPos> iterator = this.autoFireObservers.iterator();
        while (iterator.hasNext()) {
            BlockPos sourcePos = iterator.next();
            LinkedObserver linked = this.linkedObservers.get(sourcePos);
            if (linked == null || linked.category() != DamageObserverSettings.ObserverCategory.WEAPONS || !"AUTOCANNON".equals(linked.weapon().kind())) {
                iterator.remove();
                this.autoFireAttemptTicks.remove(sourcePos);
                changed |= this.updateAutoAimBlockedReason(sourcePos, "");
                changed = true;
                continue;
            }
            if (!this.level.isLoaded(sourcePos)) {
                continue;
            }
            BlockEntity blockEntity = this.level.getBlockEntity(sourcePos);
            if (!(blockEntity instanceof DamageObserverBE observer)) {
                iterator.remove();
                this.autoFireAttemptTicks.remove(sourcePos);
                changed |= this.updateAutoAimBlockedReason(sourcePos, "");
                changed = true;
                continue;
            }
            observer.forceScanObservedVolume();
            if (!observer.isWeaponDetected() || !"AUTOCANNON".equals(observer.getWeaponKind())) {
                this.setObservedAutocannonFireSignal(observer, false, 0);
                boolean blockedChanged = this.updateAutoAimBlockedReason(sourcePos, "NO AUTOCANNON");
                if (blockedChanged) {
                    changed = true;
                    this.refreshLinkedObserverSnapshot(sourcePos);
                }
                continue;
            }
            int intervalTicks = this.autoFireIntervalTicks(observer);
            this.maybeLogAutoFireState(now, sourcePos, observer, intervalTicks);
            boolean blockedChanged = this.updateAutoAimBlockedReason(sourcePos, "");
            if (blockedChanged) {
                changed = true;
                this.refreshLinkedObserverSnapshot(sourcePos);
            }
            this.setObservedAutocannonFireSignal(observer, true, 15);
        }
        return changed;
    }

    private void processFirePulses(long now) {
        if (this.level == null || this.autoFirePulseReleaseTicks.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Long>> iterator = this.autoFirePulseReleaseTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (now < entry.getValue()) {
                continue;
            }
            if (!this.autoFireObservers.contains(entry.getKey())) {
                this.releaseAutocannonFireSignal(entry.getKey());
            }
            iterator.remove();
        }
    }

    private int autoFireIntervalTicks(DamageObserverBE observer) {
        int rpm = observer.getWeaponFireRateRpm();
        if (rpm <= 0) {
            return AUTO_FIRE_INTERVAL_TICKS;
        }
        return Math.max(1, (int) Math.ceil(1200.0 / rpm));
    }

    private void armAutocannonFireSignal(BlockPos sourcePos, boolean fire) {
        if (this.level == null || !this.level.isLoaded(sourcePos)) {
            return;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(sourcePos);
        if (blockEntity instanceof DamageObserverBE observer) {
            observer.forceScanObservedVolume();
            this.setObservedAutocannonFireSignal(observer, fire, fire ? 15 : 0);
        }
    }

    private void releaseAutocannonFireSignal(BlockPos sourcePos) {
        this.armAutocannonFireSignal(sourcePos, false);
    }

    private boolean setObservedAutocannonFireSignal(DamageObserverBE observer, boolean fire, int firePower) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean changed = false;
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!serverLevel.isLoaded(pos)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(pos);
            if (CreateBigCannonsCompat.classifyPart(state) != CreateBigCannonsCompat.WeaponPart.MOUNT) {
                continue;
            }
            if (CreateBigCannonsReflectionAdapter.setMountedFireSignal(serverLevel.getBlockEntity(pos), fire, firePower)) {
                changed = true;
            }
        }
        BlockPos anchor = observer.getObservedAnchorPos();
        if (serverLevel.isLoaded(anchor) && CreateBigCannonsReflectionAdapter.setMountedFireSignal(serverLevel.getBlockEntity(anchor), fire, firePower)) {
            changed = true;
        }
        return changed;
    }

    private void maybeLogAutoFireState(long now, BlockPos sourcePos, DamageObserverBE observer, int intervalTicks) {
        long lastDebug = this.autoFireLastDebugTicks.getOrDefault(sourcePos, Long.MIN_VALUE / 4);
        if (now - lastDebug < 20L) {
            return;
        }
        this.autoFireLastDebugTicks.put(sourcePos, now);
        CreateStats.LOGGER.info(
                "[BATTLE_AUTOFIRE] pos={} rpm={} interval={} canFireKnown={} canFire={} loaded={} ammo={} creative={} readiness={} mountMissing={}",
                sourcePos,
                observer.getWeaponFireRateRpm(),
                intervalTicks,
                observer.isWeaponCanFireKnown(),
                observer.canWeaponFire(),
                observer.getWeaponLoadedState(),
                observer.getWeaponAmmoRounds(),
                observer.isWeaponAmmoCreative(),
                observer.getWeaponReadiness(),
                observer.getWeaponMissingMountBlocks());
    }

    private boolean clearAutoFireState(BlockPos sourcePos) {
        boolean changed = this.autoFireObservers.remove(sourcePos);
        changed |= this.autoFireAttemptTicks.remove(sourcePos) != null;
        this.autoFireLastDebugTicks.remove(sourcePos);
        this.autoFirePulseReleaseTicks.remove(sourcePos);
        this.autoAimBlockedReasons.remove(sourcePos);
        return changed;
    }

    private boolean clearAutoAimState(BlockPos sourcePos) {
        this.releaseAutoAimGovernors(sourcePos);
        boolean changed = this.autoAimLockedTargets.remove(sourcePos) != null;
        changed |= this.autoAimHoldDisabledObservers.remove(sourcePos);
        changed |= this.autoAimPredictionObservers.remove(sourcePos);
        changed |= this.autoAimAdvancedPredictionObservers.remove(sourcePos);
        changed |= this.autoAimResponseMultipliers.remove(sourcePos) != null;
        this.autoAimBlockedReasons.remove(sourcePos);
        this.autoAimLastDriveFailureTicks.remove(sourcePos);
        this.resetAutoAimPredictionTrack(sourcePos);
        return changed;
    }

    private boolean clearLinkedControllerRuntimeState(BlockPos sourcePos, boolean releaseControls) {
        boolean changed = this.linkedControllerLastInputMasks.remove(sourcePos) != null;
        changed |= this.linkedControllerLastFireTicks.remove(sourcePos) != null;
        if (releaseControls) {
            if (!this.autoAimLockedTargets.containsKey(sourcePos)) {
                this.releaseAutoAimGovernors(sourcePos);
            }
            if (!this.autoFireObservers.contains(sourcePos) && !this.autoFirePulseReleaseTicks.containsKey(sourcePos)) {
                this.releaseAutocannonFireSignal(sourcePos);
            }
        }
        return changed;
    }

    private boolean updateAutoAimBlockedReason(BlockPos sourcePos, String blockedReason) {
        String normalized = blockedReason == null ? "" : blockedReason;
        if (normalized.isEmpty()) {
            return this.autoAimBlockedReasons.remove(sourcePos) != null;
        }
        String previous = this.autoAimBlockedReasons.put(sourcePos, normalized);
        return !normalized.equals(previous);
    }

    private boolean isAutoAimHoldEnabled(BlockPos sourcePos) {
        return !this.autoAimHoldDisabledObservers.contains(sourcePos);
    }

    private boolean isAutoAimPredictionEnabled(BlockPos sourcePos) {
        return this.autoAimPredictionObservers.contains(sourcePos);
    }

    private boolean isAutoAimAdvancedPredictionEnabled(BlockPos sourcePos) {
        return this.autoAimAdvancedPredictionObservers.contains(sourcePos);
    }

    private float autoAimResponseMultiplier(BlockPos sourcePos, @Nullable String weaponKind) {
        return clampAutoAimResponse(weaponKind,
                this.autoAimResponseMultipliers.getOrDefault(sourcePos, defaultAutoAimResponse(weaponKind)),
                this.isAutoAimAdvancedPredictionEnabled(sourcePos));
    }

    private static float clampAutoAimResponse(float multiplier) {
        if (!Float.isFinite(multiplier)) {
            return AUTO_AIM_RESPONSE_DEFAULT;
        }
        // [4]
        return Mth.clamp(multiplier, BIG_CANNON_AUTO_AIM_RESPONSE_MIN, AUTO_AIM_RESPONSE_MAX * AUTO_AIM_ADVANCED_RESPONSE_BOOST);
    }

    private static float clampAutoAimResponse(@Nullable String weaponKind, float multiplier) {
        return clampAutoAimResponse(weaponKind, multiplier, false);
    }

    private static float clampAutoAimResponse(@Nullable String weaponKind, float multiplier, boolean advanced) {
        if (!Float.isFinite(multiplier)) {
            return defaultAutoAimResponse(weaponKind);
        }
        return Mth.clamp(multiplier, autoAimResponseMin(weaponKind), autoAimResponseMax(weaponKind, advanced));
    }

    private static float defaultAutoAimResponse(@Nullable String weaponKind) {
        return isBigCannonWeaponKind(weaponKind) ? BIG_CANNON_AUTO_AIM_RESPONSE_DEFAULT : AUTO_AIM_RESPONSE_DEFAULT;
    }

    private static float autoAimResponseMin(@Nullable String weaponKind) {
        return isBigCannonWeaponKind(weaponKind) ? BIG_CANNON_AUTO_AIM_RESPONSE_MIN : AUTO_AIM_RESPONSE_MIN;
    }

    private static float autoAimResponseMax(@Nullable String weaponKind) {
        return autoAimResponseMax(weaponKind, false);
    }

    private static float autoAimResponseMax(@Nullable String weaponKind, boolean advanced) {
        float base = isBigCannonWeaponKind(weaponKind) ? BIG_CANNON_AUTO_AIM_RESPONSE_MAX : AUTO_AIM_RESPONSE_MAX;
        return advanced ? base * AUTO_AIM_ADVANCED_RESPONSE_BOOST : base;
    }

    private static float autoAimResponseNormalized(@Nullable String weaponKind, float responseMultiplier) {
        return autoAimResponseNormalized(weaponKind, responseMultiplier, false);
    }

    private static float autoAimResponseNormalized(@Nullable String weaponKind, float responseMultiplier, boolean advanced) {
        float min = autoAimResponseMin(weaponKind);
        // Denominator stays the NON-advanced span so a multiplier beyond the normal cap (only reachable when
        // advanced) yields normalized > 1, scaling gain and the RPM ceiling above their normal limits.
        float baseSpan = autoAimResponseMax(weaponKind, false) - min;
        return (clampAutoAimResponse(weaponKind, responseMultiplier, advanced) - min) / Math.max(0.001f, baseSpan);
    }

    private static int autoAimGovernorBaseRpm(@Nullable String weaponKind) {
        return isBigCannonWeaponKind(weaponKind) ? BIG_CANNON_AUTO_AIM_GOVERNOR_BASE_RPM : AUTO_AIM_GOVERNOR_MAX_RPM;
    }

    private static int autoAimGovernorResponseMaxRpm(@Nullable String weaponKind) {
        return isBigCannonWeaponKind(weaponKind) ? BIG_CANNON_AUTO_AIM_GOVERNOR_MAX_RPM : AUTO_AIM_GOVERNOR_MAX_RESPONSE_RPM;
    }

    private void resetAutoAimPredictionTrack(BlockPos sourcePos) {
        this.autoAimLastPredictionTargetIds.remove(sourcePos);
        this.autoAimLastTargetPoints.remove(sourcePos);
        this.autoAimLastTargetTicks.remove(sourcePos);
        this.autoAimSmoothedTargetVelocities.remove(sourcePos);
        this.autoAimSmoothedTargetAccels.remove(sourcePos);
        this.autoAimSmoothedComVelocities.remove(sourcePos);
        this.autoAimLastComVelocityTicks.remove(sourcePos);
        this.autoAimLastGravityVectors.remove(sourcePos);
        this.autoAimPositionHistory.remove(sourcePos);
        this.autoAimPredictionResultTicks.remove(sourcePos);
        this.autoAimPredictionResults.remove(sourcePos);
    }

    private void refreshLinkedObserverSnapshot(BlockPos sourcePos) {
        if (this.level == null || !this.level.isLoaded(sourcePos)) {
            return;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(sourcePos);
        if (!(blockEntity instanceof DamageObserverBE observer)) {
            return;
        }
        observer.forceScanObservedVolume();
        this.battleFeedSnapshotCache = null;
        this.linkedObservers.put(sourcePos.immutable(), this.captureLinkedObserver(observer, sourcePos, this.level.getGameTime()));
    }

    private boolean processLinkedController(long now) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.refreshLinkedControllerReceivers(serverLevel);
        if (this.linkedControllerInventories.isEmpty()
                && this.linkedControllerReceivers.isEmpty()
                && this.linkedControllerLastInputMasks.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Set<BlockPos> trackedObservers = new HashSet<>();
        trackedObservers.addAll(this.linkedControllerInventories.keySet());
        trackedObservers.addAll(this.linkedControllerReceivers.keySet());
        trackedObservers.addAll(this.linkedControllerLastInputMasks.keySet());
        trackedObservers.addAll(this.linkedControllerReceivedStrengths.keySet());
        for (BlockPos sourcePos : trackedObservers) {
            if (!this.linkedObservers.containsKey(sourcePos)) {
                changed |= this.clearLinkedControllerRuntimeState(sourcePos, true);
                this.removeLinkedControllerReceivers(sourcePos);
                continue;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(sourcePos);
            if (!(blockEntity instanceof DamageObserverBE observer)) {
                changed |= this.clearLinkedControllerRuntimeState(sourcePos, true);
                continue;
            }
            this.refreshObserverRuntimeScan(observer);
            if (!this.supportsLinkedControllerWeapon(observer)) {
                changed |= this.clearLinkedControllerRuntimeState(sourcePos, true);
                continue;
            }
            int inputMask = this.linkedControllerInputMask(sourcePos);
            int previousMask = this.linkedControllerLastInputMasks.getOrDefault(sourcePos, 0);
            this.processLinkedControllerAim(sourcePos, observer, inputMask);
            this.processLinkedControllerFire(sourcePos, observer, inputMask, now);
            boolean lockPressed = this.isLinkedControllerInputActive(inputMask, LINKED_CONTROLLER_LOCK)
                    && !this.isLinkedControllerInputActive(previousMask, LINKED_CONTROLLER_LOCK);
            if (lockPressed) {
                if (this.autoAimLockedTargets.containsKey(sourcePos)) {
                    if (this.clearAutoAimLock(sourcePos)) {
                        this.broadcastLinkedControllerActionBar(serverLevel, sourcePos, "Link released");
                        changed = true;
                    }
                } else {
                    AutoAimCandidate candidate = this.lockFacingAutoAimCandidate(sourcePos);
                    if (candidate != null) {
                        this.broadcastLinkedControllerActionBar(serverLevel, sourcePos, "Linked to " + this.linkedControllerTargetLabel(candidate));
                        changed = true;
                    }
                }
            }
            this.linkedControllerLastInputMasks.put(sourcePos, inputMask);
        }
        return changed;
    }

    private void processLinkedControllerAim(BlockPos sourcePos, DamageObserverBE observer, int inputMask) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.autoAimLockedTargets.containsKey(sourcePos)) {
            return;
        }
        int pitchDirection = this.inputDirection(inputMask, LINKED_CONTROLLER_PITCH_UP, LINKED_CONTROLLER_PITCH_DOWN);
        int yawDirection = this.inputDirection(inputMask, LINKED_CONTROLLER_YAW_RIGHT, LINKED_CONTROLLER_YAW_LEFT);
        if (pitchDirection == 0 && yawDirection == 0) {
            if (this.autoAimGovernorPositions.containsKey(sourcePos)) {
                this.releaseAutoAimGovernors(sourcePos);
            }
            return;
        }
        ObservedMountController mountController = this.findObservedMountController(serverLevel, observer);
        if (mountController == null || !mountController.inspection().available() || !mountController.inspection().running()) {
            return;
        }
        AutoAimGovernorPair governors = this.findObservedGovernorPair(serverLevel, observer);
        if (governors == null) {
            return;
        }
        UUID ownerId = observer.getOrCreateGovernorControlUuid();
        int manualAimRpm = this.linkedControllerManualAimRpm(sourcePos);
        int yawTargetRpm = observer.getSettings().mapYawGovernorRpm(yawDirection * manualAimRpm);
        int pitchTargetRpm = observer.getSettings().mapPitchGovernorRpm(pitchDirection * manualAimRpm);
        float responseMultiplier = defaultAutoAimResponse(observer.getWeaponKind());
        boolean advancedResponse = this.isAutoAimAdvancedPredictionEnabled(sourcePos);
        AutoAimGovernorCommand yawCommand = this.commandAutoAimGovernor(observer, ownerId, governors.yaw(), yawTargetRpm, responseMultiplier, advancedResponse);
        AutoAimGovernorCommand pitchCommand = this.commandAutoAimGovernor(observer, ownerId, governors.pitch(), pitchTargetRpm, responseMultiplier, advancedResponse);
        if (!yawCommand.accepted() || !pitchCommand.accepted()) {
            if (yawCommand.accepted()) {
                governors.yaw().governor().releaseOwnership(ownerId);
            }
            if (pitchCommand.accepted()) {
                governors.pitch().governor().releaseOwnership(ownerId);
            }
            this.releaseAutoAimGovernors(sourcePos);
            return;
        }
        this.autoAimGovernorPositions.put(sourcePos.immutable(), List.of(governors.yaw().pos(), governors.pitch().pos()));
    }

    private void broadcastLinkedControllerActionBar(ServerLevel serverLevel, BlockPos sourcePos, String message) {
        double x = sourcePos.getX() + 0.5;
        double y = sourcePos.getY() + 0.5;
        double z = sourcePos.getZ() + 0.5;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(x, y, z) <= 4096.0) {
                player.displayClientMessage(Component.literal(message), true);
            }
        }
    }

    private String linkedControllerTargetLabel(AutoAimCandidate candidate) {
        if (candidate.label() != null && !candidate.label().isBlank()) {
            return candidate.label();
        }
        return candidate.type() == null || candidate.type().isBlank() ? "target" : candidate.type();
    }

    private void processLinkedControllerFire(BlockPos sourcePos, DamageObserverBE observer, int inputMask, long now) {
        boolean fireHeld = this.isLinkedControllerInputActive(inputMask, LINKED_CONTROLLER_FIRE);
        if (this.isAutocannonWeapon(observer.getWeaponKind())) {
            if (fireHeld) {
                this.setObservedAutocannonFireSignal(observer, true, 15);
            } else if (!this.autoFireObservers.contains(sourcePos) && !this.autoFirePulseReleaseTicks.containsKey(sourcePos)) {
                this.setObservedAutocannonFireSignal(observer, false, 0);
            }
            return;
        }
        if (!fireHeld) {
            return;
        }
        long lastFire = this.linkedControllerLastFireTicks.getOrDefault(sourcePos, Long.MIN_VALUE / 4);
        if (now - lastFire < LINKED_CONTROLLER_BIG_CANNON_FIRE_INTERVAL_TICKS) {
            return;
        }
        if (this.tryFireObservedWeapon(observer)) {
            this.linkedControllerLastFireTicks.put(sourcePos, now);
        }
    }

    private boolean processAutoAimDrive(long now) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.autoAimLockedTargets.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<BlockPos, LinkedObserver> entry : this.linkedObservers.entrySet()) {
            BlockPos sourcePos = entry.getKey();
            LinkedObserver linked = entry.getValue();
            if (linked.category() != DamageObserverSettings.ObserverCategory.WEAPONS
                    || !this.supportsAutoAimWeapon(linked.weapon().kind())
                    || !this.autoAimLockedTargets.containsKey(sourcePos)) {
                continue;
            }
            if (!serverLevel.isLoaded(sourcePos)) {
                continue;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(sourcePos);
            if (!(blockEntity instanceof DamageObserverBE observer)) {
                continue;
            }
            this.refreshObserverRuntimeScan(observer);
            ObservedMountController mountController = this.findObservedMountController(serverLevel, observer);
            if (mountController == null || !mountController.inspection().available() || !mountController.inspection().running()) {
                continue;
            }
            ResolvedAutoAimTarget resolved = this.resolveAutoAimTarget(serverLevel, observer, sourcePos, mountController.inspection());
            if (resolved.stateChanged()) {
                changed = true;
                this.refreshLinkedObserverSnapshot(sourcePos);
            }
            if (!resolved.targetLocked() || resolved.candidate() == null) {
                continue;
            }
            this.driveObservedMount(sourcePos, observer, mountController, resolved.candidate().aimPoint());
        }
        return changed;
    }

    private void driveObservedMount(BlockPos sourcePos, DamageObserverBE observer, ObservedMountController mountController, Vec3 targetPoint) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim = mountController.inspection();
        AutoAimMeasurement measurement = this.measureAutoAim(mountAim, targetPoint);
        AutoAimGovernorPair governors = this.findObservedGovernorPair(serverLevel, observer);
        if (governors == null) {
            this.releaseAutoAimGovernors(sourcePos);
            this.updateAutoAimBlockedReason(sourcePos, "GOVERNORS");
            this.maybeLogAutoAimDriveFailure(sourcePos);
            return;
        }
        float responseMultiplier = this.autoAimResponseMultiplier(sourcePos, observer.getWeaponKind());
        boolean advancedResponse = this.isAutoAimAdvancedPredictionEnabled(sourcePos);
        int yawTargetRpm = observer.getSettings().mapYawGovernorRpm(this.autoAimGovernorRpm(observer.getWeaponKind(), measurement.yawErrorDegrees(), responseMultiplier, advancedResponse));
        int pitchTargetRpm = observer.getSettings().mapPitchGovernorRpm(this.autoAimGovernorRpm(observer.getWeaponKind(), measurement.pitchErrorDegrees(), responseMultiplier, advancedResponse));
        UUID ownerId = observer.getOrCreateGovernorControlUuid();
        AutoAimGovernorCommand yawCommand = this.commandAutoAimGovernor(observer, ownerId, governors.yaw(), yawTargetRpm, responseMultiplier, advancedResponse);
        AutoAimGovernorCommand pitchCommand = this.commandAutoAimGovernor(observer, ownerId, governors.pitch(), pitchTargetRpm, responseMultiplier, advancedResponse);
        if (!yawCommand.accepted() || !pitchCommand.accepted()) {
            if (yawCommand.accepted()) {
                governors.yaw().governor().releaseOwnership(ownerId);
            }
            if (pitchCommand.accepted()) {
                governors.pitch().governor().releaseOwnership(ownerId);
            }
            this.releaseAutoAimGovernors(sourcePos);
            this.updateAutoAimBlockedReason(sourcePos, "GOV OWNER");
            this.maybeLogAutoAimDriveFailure(sourcePos);
            return;
        }
        this.autoAimGovernorPositions.put(sourcePos.immutable(), List.of(governors.yaw().pos(), governors.pitch().pos()));
        this.updateAutoAimBlockedReason(sourcePos, "");
        this.autoAimLastDriveFailureTicks.remove(sourcePos);
    }

    private int autoAimGovernorRpm(@Nullable String weaponKind, float errorDegrees, float responseMultiplier, boolean advanced) {
        boolean bigCannon = isBigCannonWeaponKind(weaponKind);
        float deadband = bigCannon ? BIG_CANNON_AUTO_AIM_DEADBAND_DEGREES : AUTO_AIM_DEADBAND_DEGREES;
        if (Math.abs(errorDegrees) <= deadband) {
            return 0;
        }
        // [5]
        float gainNormalized = autoAimResponseNormalized(weaponKind, responseMultiplier, false);
        int maxRpm = this.autoAimGovernorMaxRpm(weaponKind, responseMultiplier, advanced);
        float gain;
        if (bigCannon) {
            float authorityScale = Mth.clamp(Math.abs(errorDegrees) / BIG_CANNON_AUTO_AIM_FULL_AUTHORITY_ERROR_DEGREES,
                    BIG_CANNON_AUTO_AIM_MIN_AUTHORITY_SCALE,
                    1.0f);
            gain = AUTO_AIM_GOVERNOR_RPM_PER_DEGREE
                    * clampAutoAimResponse(weaponKind, responseMultiplier, false)
                    * authorityScale;
            maxRpm = Math.max(BIG_CANNON_AUTO_AIM_GOVERNOR_BASE_RPM, Math.round(maxRpm * authorityScale));
        } else {
            gain = AUTO_AIM_GOVERNOR_RPM_PER_DEGREE * (1.0f + gainNormalized);
        }
        int rpm = Math.round(errorDegrees * gain);
        if (rpm == 0) {
            rpm = errorDegrees > 0.0f ? 1 : -1;
        }
        return Mth.clamp(rpm, -maxRpm, maxRpm);
    }

    private int autoAimGovernorMaxRpm(@Nullable String weaponKind, float responseMultiplier, boolean advanced) {
        float normalizedResponse = autoAimResponseNormalized(weaponKind, responseMultiplier, advanced);
        int baseRpm = autoAimGovernorBaseRpm(weaponKind);
        int maxResponseRpm = autoAimGovernorResponseMaxRpm(weaponKind);
        return Math.max(baseRpm,
                Math.round(baseRpm + (maxResponseRpm - baseRpm) * normalizedResponse));
    }

    @Nullable
    private AutoAimGovernorPair findObservedGovernorPair(ServerLevel serverLevel, DamageObserverBE observer) {
        List<ObservedGovernorController> governors = this.findObservedGovernors(serverLevel, observer);
        if (governors.size() != 2) {
            return null;
        }
        ObservedGovernorController first = governors.get(0);
        ObservedGovernorController second = governors.get(1);
        return observer.getSettings().getWeaponGovernorMapping() == DamageObserverSettings.WeaponGovernorMapping.A_YAW_B_PITCH
                ? new AutoAimGovernorPair(first, second)
                : new AutoAimGovernorPair(second, first);
    }

    private List<ObservedGovernorController> findObservedGovernors(ServerLevel serverLevel, DamageObserverBE observer) {
        List<ObservedGovernorController> governors = this.resolveObservedGovernors(serverLevel, observer.getWeaponGovernorPositions());
        if (governors.size() == 2) {
            return governors;
        }

        List<BlockPos> detectedGovernorPositions = new ArrayList<>();
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            ObservedGovernorController controller = this.resolveObservedGovernor(serverLevel, pos);
            if (controller != null) {
                detectedGovernorPositions.add(controller.pos());
            }
        }
        DamageObserverBE.sortGovernorPositions(serverLevel, detectedGovernorPositions);
        return this.resolveObservedGovernors(serverLevel, detectedGovernorPositions);
    }

    private List<ObservedGovernorController> resolveObservedGovernors(ServerLevel serverLevel, List<BlockPos> governorPositions) {
        List<ObservedGovernorController> governors = new ArrayList<>();
        for (BlockPos pos : governorPositions) {
            ObservedGovernorController controller = this.resolveObservedGovernor(serverLevel, pos);
            if (controller != null) {
                governors.add(controller);
            }
        }
        return governors;
    }

    @Nullable
    private ObservedGovernorController resolveObservedGovernor(ServerLevel serverLevel, BlockPos pos) {
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(serverLevel, pos);
        if (!(blockEntity instanceof RpmGovernorBE governor)) {
            blockEntity = serverLevel.isLoaded(pos) ? serverLevel.getBlockEntity(pos) : null;
        }
        if (blockEntity instanceof RpmGovernorBE governor) {
            return new ObservedGovernorController(pos.immutable(), governor);
        }
        return null;
    }

    private AutoAimGovernorCommand commandAutoAimGovernor(DamageObserverBE observer, UUID ownerId, ObservedGovernorController controller, int targetRpm, float responseMultiplier, boolean advanced) {
        RpmGovernorBE governor = controller.governor();
        if (governor.isOverStressed()) {
            if (ownerId.equals(governor.getCommanderUuid())) {
                governor.setCommand(observer.getBlockPos(), ownerId, 0);
            }
            return new AutoAimGovernorCommand(false, 0);
        }
        boolean ownedByObserver = ownerId.equals(governor.getCommanderUuid());
        int currentRpm = ownedByObserver ? governor.getCommandedRpm() : governor.getAppliedRpm();
            int safeTarget = this.stepGovernorRpm(observer.getWeaponKind(), currentRpm, targetRpm, responseMultiplier, advanced);
        long now = this.level == null ? 0L : this.level.getGameTime();
        Integer previousSent = this.autoAimLastGovernorTargets.get(controller.pos());
        long lastCommandTick = this.autoAimLastGovernorCommandTicks.getOrDefault(controller.pos(), Long.MIN_VALUE / 4);
        boolean commandChanged = previousSent == null || previousSent != safeTarget;
            int rebuildInterval = responseMultiplier > AUTO_AIM_RESPONSE_DEFAULT + 0.01f
                ? 1
                : Math.max(1, FlightConfig.GOVERNOR_REBUILD_INTERVAL_TICKS);
            boolean commandDue = now - lastCommandTick >= rebuildInterval;
        if (!commandChanged && ownedByObserver) {
            if (governor.heartbeat(observer.getBlockPos(), ownerId)) {
                return new AutoAimGovernorCommand(true, safeTarget);
            }
        }
        if (commandChanged && !commandDue && ownedByObserver) {
            if (governor.heartbeat(observer.getBlockPos(), ownerId)) {
                return new AutoAimGovernorCommand(true, previousSent == null ? governor.getCommandedRpm() : previousSent);
            }
        }
        if (governor.setCommand(observer.getBlockPos(), ownerId, safeTarget)) {
            this.autoAimLastGovernorTargets.put(controller.pos(), safeTarget);
            this.autoAimLastGovernorCommandTicks.put(controller.pos(), now);
            return new AutoAimGovernorCommand(true, safeTarget);
        }
        return new AutoAimGovernorCommand(false, safeTarget);
    }

    private int stepGovernorRpm(@Nullable String weaponKind, int currentRpm, int targetRpm, float responseMultiplier, boolean advanced) {
        int step;
        if (isBigCannonWeaponKind(weaponKind)) {
            step = Math.max(1, Math.round(FlightConfig.GOVERNOR_MAX_RPM_STEP_PER_TICK * clampAutoAimResponse(weaponKind, responseMultiplier, false)));
        } else {
            int baseStep = Math.max(1, FlightConfig.GOVERNOR_MAX_RPM_STEP_PER_TICK);
            float normalizedResponse = autoAimResponseNormalized(weaponKind, responseMultiplier, false);
            int maxStep = this.autoAimGovernorMaxRpm(weaponKind, responseMultiplier, false);
            step = Math.max(baseStep, Math.round(baseStep + (maxStep - baseStep) * normalizedResponse));
        }
        if (currentRpm < targetRpm) {
            return Math.min(targetRpm, currentRpm + step);
        }
        if (currentRpm > targetRpm) {
            return Math.max(targetRpm, currentRpm - step);
        }
        return currentRpm;
    }

    private void releaseAutoAimGovernors(BlockPos sourcePos) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            this.autoAimGovernorPositions.remove(sourcePos);
            return;
        }
        List<BlockPos> governorPositions = this.autoAimGovernorPositions.remove(sourcePos);
        if (governorPositions == null || governorPositions.isEmpty() || !serverLevel.isLoaded(sourcePos)) {
            return;
        }
        for (BlockPos governorPos : governorPositions) {
            this.autoAimLastGovernorTargets.remove(governorPos);
            this.autoAimLastGovernorCommandTicks.remove(governorPos);
        }
        BlockEntity source = serverLevel.getBlockEntity(sourcePos);
        if (!(source instanceof DamageObserverBE observer)) {
            return;
        }
        UUID ownerId = observer.getOrCreateGovernorControlUuid();
        for (BlockPos governorPos : governorPositions) {
            BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(serverLevel, governorPos);
            if (!(blockEntity instanceof RpmGovernorBE governor) && serverLevel.isLoaded(governorPos)) {
                blockEntity = serverLevel.getBlockEntity(governorPos);
            }
            if (blockEntity instanceof RpmGovernorBE governor) {
                governor.releaseOwnership(ownerId);
            }
        }
    }

    private AutoAimMeasurement measureAutoAim(CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim, Vec3 targetPoint) {
        Vec3 origin = this.autoAimOrigin(mountAim);
        double dx = targetPoint.x - origin.x;
        double dy = targetPoint.y - origin.y;
        double dz = targetPoint.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(dy, Math.max(1.0E-6, horizontal)));
        Vec3 axis = mountAim.aimForwardLocation().subtract(origin);
        double axisHorizontal = Math.sqrt(axis.x * axis.x + axis.z * axis.z);
        float currentYaw = axis.lengthSqr() > 1.0E-6
            ? (float) Math.toDegrees(Math.atan2(-axis.x, axis.z))
            : mountAim.yawDegrees();
        float currentPitch = axis.lengthSqr() > 1.0E-6
            ? (float) Math.toDegrees(Math.atan2(axis.y, Math.max(1.0E-6, axisHorizontal)))
            : mountAim.pitchDegrees();
        return new AutoAimMeasurement(
                Math.sqrt(dx * dx + dy * dy + dz * dz),
                currentYaw,
                currentPitch,
                targetYaw,
                targetPitch,
                Mth.wrapDegrees(targetYaw - currentYaw),
                Mth.wrapDegrees(targetPitch - currentPitch));
    }

    private Vec3 autoAimOrigin(CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim) {
        Vec3 origin = mountAim.aimOriginLocation();
        return origin == null ? mountAim.interactionLocation() : origin;
    }

    private float autoAimStep(float errorDegrees) {
        if (Math.abs(errorDegrees) <= AUTO_AIM_DEADBAND_DEGREES) {
            return 0.0f;
        }
        return Mth.clamp(errorDegrees * AUTO_AIM_RESPONSE_GAIN, -AUTO_AIM_MAX_STEP_DEGREES, AUTO_AIM_MAX_STEP_DEGREES);
    }

    private String evaluateAutoFireBlockedReason(DamageObserverBE observer,
                                                 CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                 ResolvedAutoAimTarget resolved) {
        if (!resolved.targetLocked() || resolved.candidate() == null) {
            return resolved.statusText().isEmpty() ? "NO TARGET" : resolved.statusText();
        }
        if (observer.getWeaponMissingBlocks() > 0) {
            return "STRUCTURE DAMAGE";
        }
        if (!observer.isWeaponAmmoCreative() && observer.getWeaponAmmoContainers() <= 0) {
            return "NO AMMO";
        }
        if (!observer.isWeaponAmmoCreative()
                && observer.getWeaponAmmoRounds() <= 0
                && !"LOADED".equals(observer.getWeaponLoadedState())) {
            return "NO AMMO";
        }
        AutoAimMeasurement measurement = this.measureAutoAim(mountAim, resolved.candidate().aimPoint());
        if (Math.abs(measurement.yawErrorDegrees()) > AUTO_AIM_FIRE_ALIGNMENT_DEGREES
                || Math.abs(measurement.pitchErrorDegrees()) > AUTO_AIM_FIRE_ALIGNMENT_DEGREES) {
            return "ALIGNING";
        }
        return "";
    }

    private void maybeLogAutoAimDriveFailure(BlockPos sourcePos) {
        if (this.level == null) {
            return;
        }
        long now = this.level.getGameTime();
        long lastFailure = this.autoAimLastDriveFailureTicks.getOrDefault(sourcePos, Long.MIN_VALUE / 4);
        if (now - lastFailure < 20L) {
            return;
        }
        this.autoAimLastDriveFailureTicks.put(sourcePos, now);
        CreateStats.LOGGER.warn("[BATTLE_GOV_AIM] governor drive unavailable for observer {}", sourcePos);
    }

    private AutoAimSnapshot captureAutoAimSnapshot(DamageObserverBE observer, BlockPos sourcePos) {
        if (!(this.level instanceof ServerLevel serverLevel)
                || !this.supportsAutoAimWeapon(observer)) {
            return AutoAimSnapshot.none();
        }
        ObservedMountController mountController = this.findObservedMountController(serverLevel, observer);
        if (mountController == null || !mountController.inspection().available()) {
            return AutoAimSnapshot.none();
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim = mountController.inspection();
        ResolvedAutoAimTarget resolved = this.resolveAutoAimTarget(serverLevel, observer, sourcePos, mountAim);
        AutoAimCandidate candidate = resolved.candidate();
        UUID lockedTargetId = this.autoAimLockedTargets.get(sourcePos);
        List<AutoAimTargetOption> targetOptions = this.listAutoAimTargetOptions(serverLevel, mountAim, observer, lockedTargetId);
        String blockedReason = this.autoAimBlockedReasons.getOrDefault(sourcePos, "");
        float responseMultiplier = this.autoAimResponseMultiplier(sourcePos, observer.getWeaponKind());
        if (candidate == null) {
            return AutoAimSnapshot.mountOnly(
                    mountAim.running(),
                    mountAim.fixedMount(),
                    mountAim.yawDegrees(),
                    mountAim.pitchDegrees(),
                    mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().x : Double.NaN,
                    mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().y : Double.NaN,
                    mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().z : Double.NaN,
                    resolved.targetLocked(),
                    resolved.holdEnabled(),
                    resolved.predictionEnabled(),
                    resolved.advancedPredictionEnabled(),
                    resolved.predictionActive(),
                    resolved.predictionStatusText(),
                responseMultiplier,
                    resolved.statusText(),
                    blockedReason,
                    targetOptions);
        }
        AutoAimMeasurement measurement = this.measureAutoAim(mountAim, candidate.aimPoint());
        return new AutoAimSnapshot(
                true,
                mountAim.running(),
                mountAim.fixedMount(),
                resolved.targetLocked(),
                resolved.holdEnabled(),
                resolved.predictionEnabled(),
                resolved.advancedPredictionEnabled(),
                resolved.predictionActive(),
                resolved.predictionStatusText(),
                responseMultiplier,
                true,
                "",
                blockedReason,
                candidate.type(),
                candidate.label(),
                measurement.targetDistance(),
                measurement.currentYawDegrees(),
                measurement.currentPitchDegrees(),
                mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().x : Double.NaN,
                mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().y : Double.NaN,
                mountAim.aimForwardLocation() != null ? mountAim.aimForwardLocation().z : Double.NaN,
                measurement.targetYawDegrees(),
                measurement.targetPitchDegrees(),
                measurement.yawErrorDegrees(),
                measurement.pitchErrorDegrees(),
                targetOptions);
    }

    private ResolvedAutoAimTarget resolveAutoAimTarget(ServerLevel level,
                                                       DamageObserverBE observer,
                                                       BlockPos sourcePos,
                                                       CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim) {
        boolean holdEnabled = this.isAutoAimHoldEnabled(sourcePos);
        boolean predictionEnabled = this.supportsAutoAimPrediction(observer) && this.isAutoAimPredictionEnabled(sourcePos);
        boolean advancedPredictionEnabled = predictionEnabled && this.isAutoAimAdvancedPredictionEnabled(sourcePos);
        UUID lockedTargetId = this.autoAimLockedTargets.get(sourcePos);
        boolean targetLocked = lockedTargetId != null;
        boolean stateChanged = false;
        boolean predictionActive = false;
        String predictionStatusText = "";
        AutoAimCandidate candidate;
        String statusText = "";
        if (targetLocked) {
            candidate = this.findLockedAutoAimCandidate(level, mountAim, observer, lockedTargetId);
            if (candidate == null) {
                statusText = this.describeLockedAutoAimStatus(level, mountAim, observer, lockedTargetId);
                if (!holdEnabled) {
                    this.autoAimLockedTargets.remove(sourcePos);
                    this.releaseAutoAimGovernors(sourcePos);
                    targetLocked = false;
                    stateChanged = true;
                    candidate = this.findNearestAutoAimCandidate(level, mountAim, observer);
                }
            }
        } else {
            candidate = this.findNearestAutoAimCandidate(level, mountAim, observer);
        }
        if (candidate == null && statusText.isEmpty()) {
            statusText = "NO TARGET";
        }
        if (candidate == null || !predictionEnabled) {
            this.resetAutoAimPredictionTrack(sourcePos);
        } else {
            PredictionResolution prediction = this.applyAutoAimPrediction(sourcePos, observer, mountAim, candidate);
            candidate = prediction.candidate();
            predictionActive = prediction.active();
            predictionStatusText = prediction.statusText();
        }
        return new ResolvedAutoAimTarget(targetLocked, holdEnabled, predictionEnabled, advancedPredictionEnabled, predictionActive, predictionStatusText, statusText, candidate, stateChanged);
    }

    private PredictionResolution cachePrediction(BlockPos sourcePos, long now, PredictionResolution resolution) {
        this.autoAimPredictionResultTicks.put(sourcePos, now);
        this.autoAimPredictionResults.put(sourcePos, resolution);
        return resolution;
    }

    private PredictionResolution applyAutoAimPrediction(BlockPos sourcePos,
                                                        DamageObserverBE observer,
                                                        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                        AutoAimCandidate candidate) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return new PredictionResolution(candidate, false, "");
        }
        long now = serverLevel.getGameTime();
        boolean advanced = this.isAutoAimAdvancedPredictionEnabled(sourcePos);

        // [6]
        Long cachedTick = this.autoAimPredictionResultTicks.get(sourcePos);
        if (cachedTick != null && cachedTick == now) {
            PredictionResolution cached = this.autoAimPredictionResults.get(sourcePos);
            if (cached != null && cached.candidate().targetId().equals(candidate.targetId())) {
                return cached;
            }
        }
        UUID previousTargetId = this.autoAimLastPredictionTargetIds.get(sourcePos);
        Vec3 previousPoint = this.autoAimLastTargetPoints.get(sourcePos);
        long previousTick = this.autoAimLastTargetTicks.getOrDefault(sourcePos, Long.MIN_VALUE);
        Vec3 smoothedVelocity = this.autoAimSmoothedTargetVelocities.getOrDefault(sourcePos, Vec3.ZERO);
        Vec3 smoothedAccel = this.autoAimSmoothedTargetAccels.getOrDefault(sourcePos, Vec3.ZERO);
        boolean freshTrack = !candidate.targetId().equals(previousTargetId);
        if (freshTrack) {
            previousPoint = null;
            previousTick = Long.MIN_VALUE;
            smoothedVelocity = Vec3.ZERO;
            smoothedAccel = Vec3.ZERO;
        }

        Vec3 aimPoint = candidate.aimPoint();
        double deltaTicks = (previousTick != Long.MIN_VALUE && now > previousTick) ? (double) (now - previousTick) : 0.0;

        // [7]
        ArrayDeque<double[]> history = this.autoAimPositionHistory.computeIfAbsent(sourcePos, k -> new ArrayDeque<>());
        if (freshTrack) {
            history.clear();
        }
        history.addLast(new double[]{(double) now, aimPoint.x, aimPoint.y, aimPoint.z});
        while (history.size() > AUTO_AIM_TRACK_HISTORY
                || (history.size() > 2 && now - (long) history.peekFirst()[0] > AUTO_AIM_TRACK_HISTORY)) {
            history.removeFirst();
        }

        double[] newestSample = history.peekLast();
        double[] velBaseSample = history.peekFirst();
        for (double[] s : history) {
            if ((long) newestSample[0] - (long) s[0] <= AUTO_AIM_VEL_WINDOW_TICKS) {
                velBaseSample = s;
                break;
            }
        }
        long velSpan = (long) newestSample[0] - (long) velBaseSample[0];
        if (velSpan >= 1) {
            Vec3 windowedVel = new Vec3(
                    (newestSample[1] - velBaseSample[1]) / velSpan,
                    (newestSample[2] - velBaseSample[2]) / velSpan,
                    (newestSample[3] - velBaseSample[3]) / velSpan);
            if (isFinite(windowedVel) && windowedVel.length() <= AUTO_AIM_MAX_TARGET_SPEED) {
                // Faster velocity EMA (0.65) so the lead reacts quickly when a target jukes; the windowed
                // base measurement still suppresses single-tick noise.
                double velAlpha = smoothedVelocity.lengthSqr() < 1.0E-9 ? 1.0 : 0.65;
                Vec3 newVelocity = smoothedVelocity.scale(1.0 - velAlpha).add(windowedVel.scale(velAlpha));
                if (!freshTrack && deltaTicks > 0.0 && deltaTicks <= AUTO_AIM_MAX_SAMPLE_GAP_TICKS) {
                    Vec3 sampledAccel = newVelocity.subtract(smoothedVelocity).scale(1.0 / deltaTicks);
                    if (isFinite(sampledAccel) && sampledAccel.length() <= AUTO_AIM_MAX_TARGET_ACCEL) {
                        // Heavier weight on the freshest acceleration (0.55) so hard vertical reversals are
                        // picked up sooner and the arc is re-led during a dodge.
                        smoothedAccel = smoothedAccel.scale(0.45).add(sampledAccel.scale(0.55));
                    }
                }
                smoothedVelocity = newVelocity;
            }
        }

        // [8]
        boolean advancedActive = advanced;

        this.autoAimLastPredictionTargetIds.put(sourcePos, candidate.targetId());
        this.autoAimLastTargetPoints.put(sourcePos, aimPoint);
        this.autoAimLastTargetTicks.put(sourcePos, now);
        this.autoAimSmoothedTargetVelocities.put(sourcePos, smoothedVelocity);
        this.autoAimSmoothedTargetAccels.put(sourcePos, smoothedAccel);

        // [9]
        boolean bigCannonWeapon = isBigCannonWeaponKind(observer.getWeaponKind());
        float projectileSpeed = bigCannonWeapon
                ? this.resolveBigCannonProjectileSpeed(serverLevel, observer)
                : this.resolveAutocannonProjectileSpeed(serverLevel, observer);
        if (projectileSpeed <= 0.01f) {
            return this.cachePrediction(sourcePos, now, new PredictionResolution(candidate, false, "NO BALLISTICS"));
        }
        Vec3 origin = this.autoAimOrigin(mountAim);

        // [10]
        BallisticParams ballistics = this.resolveBallisticParams(serverLevel, observer, bigCannonWeapon);
        boolean useAccel = advancedActive;
        BallisticSolution solution = this.solveBallisticIntercept(origin, aimPoint, smoothedVelocity,
                smoothedAccel, projectileSpeed, ballistics.gravity(), ballistics.drag(), ballistics.quadratic(),
                useAccel);
        if (!solution.solved()) {
            return this.cachePrediction(sourcePos, now, new PredictionResolution(candidate, false, "NO SOLUTION"));
        }

        // Place the aim marker at the target's range along the solved launch direction, so the mount
        // derives the correct yaw + pitch (arc included) from (predictedPoint - origin).
        double aimDistance = origin.distanceTo(solution.interceptPoint());
        if (!Double.isFinite(aimDistance) || aimDistance < 1.0E-3) {
            aimDistance = origin.distanceTo(aimPoint);
        }
        Vec3 predictedPoint = origin.add(solution.launchDirection().scale(aimDistance));

        // Clamp the total lead so a spurious velocity spike can't fling the aim off the map.
        Vec3 leadOffset = predictedPoint.subtract(aimPoint);
        double leadDistance = leadOffset.length();
        if (leadDistance > AUTO_AIM_MAX_LEAD_DISTANCE) {
            predictedPoint = aimPoint.add(leadOffset.scale(AUTO_AIM_MAX_LEAD_DISTANCE / leadDistance));
            leadDistance = AUTO_AIM_MAX_LEAD_DISTANCE;
        }

        String readout = (advancedActive ? "ADV " : "")
                + String.format(java.util.Locale.ROOT, "%.1fs \u2022 %.0fm", solution.flightTicks() / 20.0, leadDistance);

        return this.cachePrediction(sourcePos, now, new PredictionResolution(
                new AutoAimCandidate(candidate.targetId(), candidate.type(), candidate.label(), predictedPoint),
                true, readout));
    }

    /**
     * Per-shell ballistic parameters (per-tick gravity, drag coefficient and drag exponent) used by the
     * forward-simulation intercept solver. Mirrors {@code BallisticPropertiesComponent} in CBC.
     */
    private record BallisticParams(double gravity, double drag, boolean quadratic) {}

    /** [11] */
    private record SimClosest(Vec3 position, double ticks, double distance) {}

    /**
     * Result of the ballistic intercept solve: the unit launch direction (yaw + pitch, arc included),
     * the flight time in ticks, the simulated intercept point and the residual miss distance.
     */
    private record BallisticSolution(boolean solved, Vec3 launchDirection, double flightTicks,
                                     Vec3 interceptPoint, double miss) {
        static BallisticSolution failure() {
            return new BallisticSolution(false, new Vec3(0.0, 0.0, 1.0), 0.0, Vec3.ZERO, Double.MAX_VALUE);
        }
    }

    /** [12] */
    private BallisticParams resolveBallisticParams(ServerLevel level, DamageObserverBE observer, boolean bigCannonWeapon) {
        return new BallisticParams(AUTO_AIM_DEFAULT_PROJECTILE_GRAVITY, AUTO_AIM_DEFAULT_PROJECTILE_DRAG,
                AUTO_AIM_DEFAULT_QUADRATIC_DRAG);
    }

    /** [13] */
    private BallisticSolution solveBallisticIntercept(Vec3 origin, Vec3 targetNow, Vec3 targetVel, Vec3 targetAccel,
                                                      double muzzleSpeed, double gravity, double dragCoeff,
                                                      boolean quadraticDrag, boolean useAccel) {
        if (!(muzzleSpeed > 1.0E-4)) {
            return BallisticSolution.failure();
        }
        Vec3 toTarget = targetNow.subtract(origin);
        double range = toTarget.length();
        if (range < 1.0E-4) {
            return BallisticSolution.failure();
        }
        double flightTicks = range / muzzleSpeed;
        Vec3 launchDir = toTarget.scale(1.0 / range);
        Vec3 interceptPoint = targetNow;
        double bestMiss = Double.MAX_VALUE;
        for (int outer = 0; outer < AUTO_AIM_INTERCEPT_OUTER_ITERATIONS; outer++) {
            // Predicted target position after the current flight-time estimate.
            Vec3 future = targetNow.add(targetVel.scale(flightTicks));
            if (useAccel) {
                future = future.add(targetAccel.scale(0.5 * flightTicks * flightTicks));
            }
            Vec3 lead = future.subtract(targetNow);
            double leadLen = lead.length();
            if (leadLen > AUTO_AIM_MAX_LEAD_DISTANCE) {
                future = targetNow.add(lead.scale(AUTO_AIM_MAX_LEAD_DISTANCE / leadLen));
            }
            // Inner: aim-point iteration on the launch direction.
            Vec3 aimAt = future;
            SimClosest sim = null;
            for (int inner = 0; inner < AUTO_AIM_INTERCEPT_INNER_ITERATIONS; inner++) {
                Vec3 dir = aimAt.subtract(origin);
                double dl = dir.length();
                if (dl < 1.0E-6) {
                    break;
                }
                dir = dir.scale(1.0 / dl);
                launchDir = dir;
                sim = this.simulateProjectileClosest(origin, dir.scale(muzzleSpeed), future, gravity, dragCoeff,
                        quadraticDrag);
                Vec3 miss = future.subtract(sim.position());
                if (miss.length() < AUTO_AIM_INTERCEPT_MISS_TOLERANCE) {
                    break;
                }
                aimAt = aimAt.add(miss);
            }
            if (sim == null) {
                return BallisticSolution.failure();
            }
            bestMiss = future.subtract(sim.position()).length();
            interceptPoint = sim.position();
            double newFlight = sim.ticks();
            boolean converged = Math.abs(newFlight - flightTicks) < AUTO_AIM_INTERCEPT_TIME_TOLERANCE;
            if (newFlight > 0.0) {
                flightTicks = newFlight;
            }
            if (converged) {
                break;
            }
        }
        if (!Double.isFinite(flightTicks) || flightTicks <= 0.0 || flightTicks > AUTO_AIM_MAX_LEAD_TICKS) {
            return BallisticSolution.failure();
        }
        return new BallisticSolution(true, launchDir, flightTicks, interceptPoint, bestMiss);
    }

    /** [14] */
    private SimClosest simulateProjectileClosest(Vec3 origin, Vec3 initialVelocity, Vec3 target,
                                                 double gravity, double dragCoeff, boolean quadraticDrag) {
        Vec3 pos = origin;
        Vec3 vel = initialVelocity;
        double bestDist = pos.distanceTo(target);
        Vec3 bestPos = pos;
        double bestTick = 0.0;
        for (int t = 1; t <= AUTO_AIM_SIM_MAX_TICKS; t++) {
            Vec3 prevPos = pos;
            double speed = vel.length();
            Vec3 accel = new Vec3(0.0, gravity, 0.0);
            if (speed > 1.0E-9 && dragCoeff > 0.0) {
                double drag = dragCoeff * speed;
                if (quadraticDrag) {
                    drag *= speed;
                }
                drag = Math.min(drag, speed);
                accel = accel.subtract(vel.scale(drag / speed));
            }
            pos = pos.add(vel).add(accel.scale(0.5));
            vel = vel.add(accel);
            // [15]
            Vec3 seg = pos.subtract(prevPos);
            double segLen2 = seg.lengthSqr();
            double s = segLen2 < 1.0E-12
                    ? 0.0
                    : Mth.clamp(target.subtract(prevPos).dot(seg) / segLen2, 0.0, 1.0);
            Vec3 cp = prevPos.add(seg.scale(s));
            double d = cp.distanceTo(target);
            if (d < bestDist) {
                bestDist = d;
                bestPos = cp;
                bestTick = (t - 1) + s;
            } else if (t > bestTick + 8.0 && d > bestDist * 1.5) {
                // Clearly past closest approach and diverging; stop simulating.
                break;
            }
        }
        return new SimClosest(bestPos, bestTick, bestDist);
    }

    /** [16] */
    @Nullable
    private Object resolveTargetSubLevel(ServerLevel level, AutoAimCandidate candidate) {
        Entity entity = level.getEntity(candidate.targetId());
        if (entity != null) {
            Object sl = this.resolveContainingSubLevel(entity);
            if (sl != null) {
                return sl;
            }
        }
        return this.findActiveSubLevelByTargetId(level, candidate.targetId());
    }

    /** [17] */
    @Nullable
    private Vec3 measureTargetVelocity(ServerLevel level, AutoAimCandidate candidate, Vec3 aimPoint) {
        Entity entity = level.getEntity(candidate.targetId());
        if (entity != null && entity.isAlive() && !entity.isRemoved()
                && this.resolveContainingSubLevel(entity) == null) {
            Vec3 delta = entity.getDeltaMovement();
            if (isFinite(delta) && delta.lengthSqr() > 1.0E-7
                    && delta.length() <= AUTO_AIM_MAX_TARGET_SPEED) {
                return delta;
            }
        }
        return null;
    }

    private static boolean isFinite(@Nullable Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private float resolveAutocannonProjectileSpeed(ServerLevel level, DamageObserverBE observer) {
        float scannedSpeed = observer.getAutocannonProjectileSpeed();
        if (scannedSpeed > 0.01f) {
            return scannedSpeed;
        }
        int barrelCount = Math.max(0, observer.getWeaponBarrelBlocks());
        if (barrelCount <= 0) {
            return 0.0f;
        }
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            CreateBigCannonsReflectionAdapter.AutocannonBallisticsInspection inspection =
                    CreateBigCannonsReflectionAdapter.inspectAutocannonBallistics(level.getBlockState(pos), barrelCount);
            if (inspection.available()) {
                return inspection.projectileSpeed();
            }
        }
        return 0.0f;
    }

    /**
     * Resolves the effective muzzle velocity (blocks/tick) for a mounted big cannon from its loaded
     * propellant charge power. Returns 0 when the cannon is not assembled or carries no propellant.
     */
    private float resolveBigCannonProjectileSpeed(ServerLevel level, DamageObserverBE observer) {
        BlockEntity mount = this.findObservedMountBlockEntity(level, observer);
        if (mount == null) {
            return 0.0f;
        }
        return CreateBigCannonsReflectionAdapter.resolveBigCannonChargePower(mount);
    }

    /**
     * Locates the cannon mount block entity inside the weapon's observed region (mirrors the scan used
     * by {@link #tryFireObservedWeapon}). Falls back to the observed anchor position.
     */
    @Nullable
    private BlockEntity findObservedMountBlockEntity(ServerLevel level, DamageObserverBE observer) {
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (CreateBigCannonsCompat.classifyPart(level.getBlockState(pos)) != CreateBigCannonsCompat.WeaponPart.MOUNT) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                return be;
            }
        }
        BlockPos anchor = observer.getObservedAnchorPos();
        return anchor != null && level.isLoaded(anchor) ? level.getBlockEntity(anchor) : null;
    }

    private String describeLockedAutoAimStatus(ServerLevel level,
                                               CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                               DamageObserverBE observer,
                                               UUID targetId) {
        Entity entity = level.getEntity(targetId);
        if (entity != null && entity.isAlive() && !entity.isRemoved()) {
            Object sourceSubLevel = this.resolveContainingSubLevel(observer);
            if (!this.isAutoAimTargetCandidate(entity, mountAim, sourceSubLevel)) {
                return "TARGET INVALID";
            }
            Vec3 origin = this.autoAimOrigin(mountAim);
            Vec3 aimPoint = this.autoAimPoint(entity, sourceSubLevel);
            if (origin.distanceToSqr(aimPoint) > AUTO_AIM_SCAN_RADIUS * AUTO_AIM_SCAN_RADIUS) {
                return "OUT OF RANGE";
            }
            return "TARGET LOST";
        }
        Vec3 origin = this.autoAimOrigin(mountAim);
        AutoAimTargetListing subLevelTarget = this.buildAutoAimTargetFromSubLevel(level, origin, this.resolveContainingSubLevel(observer), this.findActiveSubLevelByTargetId(level, targetId), true);
        if (subLevelTarget == null) {
            return "TARGET LOST";
        }
        if (origin.distanceToSqr(subLevelTarget.aimPoint()) > AUTO_AIM_SCAN_RADIUS * AUTO_AIM_SCAN_RADIUS) {
            return "OUT OF RANGE";
        }
        return "TARGET LOST";
    }

    @Nullable
    private AutoAimCandidate findLockedAutoAimCandidate(ServerLevel level,
                                                        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                        DamageObserverBE observer,
                                                        UUID targetId) {
        for (AutoAimTargetListing target : this.collectAutoAimTargets(level, mountAim, observer)) {
            if (targetId.equals(target.targetId())) {
                return target.asCandidate();
            }
        }
        return null;
    }

    @Nullable
    private AutoAimCandidate findFacingAutoAimCandidate(ServerLevel level,
                                                        CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                        DamageObserverBE observer) {
        AutoAimTargetListing best = null;
        double bestScore = Double.MAX_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;
        for (AutoAimTargetListing target : this.collectAutoAimTargets(level, mountAim, observer)) {
            AutoAimMeasurement measurement = this.measureAutoAim(mountAim, target.aimPoint());
            double yawError = Math.abs(measurement.yawErrorDegrees());
            double pitchError = Math.abs(measurement.pitchErrorDegrees());
            if (Math.max(yawError, pitchError) > LINKED_CONTROLLER_LOCK_MAX_ERROR_DEGREES) {
                continue;
            }
            double score = yawError + pitchError * 1.25;
            double distanceSq = this.autoAimOrigin(mountAim).distanceToSqr(target.aimPoint());
            if (score < bestScore || (Math.abs(score - bestScore) <= 0.001 && distanceSq < bestDistanceSq)) {
                best = target;
                bestScore = score;
                bestDistanceSq = distanceSq;
            }
        }
        return best == null ? null : best.asCandidate();
    }

    private CreateBigCannonsReflectionAdapter.MountedAimInspection findObservedMountAim(ServerLevel level, DamageObserverBE observer) {
        ObservedMountController mountController = this.findObservedMountController(level, observer);
        return mountController == null ? CreateBigCannonsReflectionAdapter.MountedAimInspection.none() : mountController.inspection();
    }

    @Nullable
    private ObservedMountController findObservedMountController(ServerLevel level, DamageObserverBE observer) {
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity mountBlockEntity = SableBridge.getBlockEntityOnContraption(level, pos);
            if (mountBlockEntity == null && level.isLoaded(pos)) {
                mountBlockEntity = level.getBlockEntity(pos);
            }
            if (mountBlockEntity == null) {
                continue;
            }
            if (CreateBigCannonsCompat.classifyPart(mountBlockEntity.getBlockState()) != CreateBigCannonsCompat.WeaponPart.MOUNT) {
                continue;
            }
            CreateBigCannonsReflectionAdapter.MountedAimInspection inspection = CreateBigCannonsReflectionAdapter.inspectMountedAim(mountBlockEntity);
            if (inspection.available()) {
                CreateBigCannonsReflectionAdapter.MountedAimInspection resolved = this.resolveWorldMountedAim(mountBlockEntity, inspection);
                return new ObservedMountController(mountBlockEntity, resolved);
            }
        }
        BlockPos anchor = observer.getObservedAnchorPos();
        BlockEntity anchorBlockEntity = SableBridge.getBlockEntityOnContraption(level, anchor);
        if (anchorBlockEntity == null && !level.isLoaded(anchor)) {
            return null;
        }
        if (anchorBlockEntity == null) {
            anchorBlockEntity = level.getBlockEntity(anchor);
        }
        CreateBigCannonsReflectionAdapter.MountedAimInspection inspection = this.resolveWorldMountedAim(anchorBlockEntity, CreateBigCannonsReflectionAdapter.inspectMountedAim(anchorBlockEntity));
        return inspection.available() ? new ObservedMountController(anchorBlockEntity, inspection) : null;
    }

    @Nullable
    private AutoAimCandidate findNearestAutoAimCandidate(ServerLevel level, CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim, DamageObserverBE observer) {
        Vec3 origin = this.autoAimOrigin(mountAim);
        AutoAimTargetListing best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (AutoAimTargetListing target : this.collectAutoAimTargets(level, mountAim, observer)) {
            double distanceSq = origin.distanceToSqr(target.aimPoint());
            if (distanceSq < bestDistanceSq) {
                best = target;
                bestDistanceSq = distanceSq;
            }
        }
        if (best == null) {
            return null;
        }
        return best.asCandidate();
    }

    private List<AutoAimTargetOption> listAutoAimTargetOptions(ServerLevel level,
                                                              CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                              DamageObserverBE observer,
                                                              @Nullable UUID lockedTargetId) {
        List<AutoAimTargetOption> options = new ArrayList<>();
        for (AutoAimTargetListing target : this.collectAutoAimTargets(level, mountAim, observer)) {
            double distance = this.autoAimOrigin(mountAim).distanceTo(target.aimPoint());
            options.add(new AutoAimTargetOption(
                    target.targetId(),
                    target.type(),
                    target.label(),
                    distance,
                    target.health(),
                    target.maxHealth(),
                    target.speed(),
                    target.aimPoint().x,
                    target.aimPoint().y,
                    target.aimPoint().z,
                    lockedTargetId != null && lockedTargetId.equals(target.targetId())));
        }
        options.sort((first, second) -> {
            if (first.locked() != second.locked()) {
                return first.locked() ? -1 : 1;
            }
            int distanceCompare = Double.compare(first.distance(), second.distance());
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(first.label(), second.label());
        });
        return options.size() <= 24 ? List.copyOf(options) : List.copyOf(options.subList(0, 24));
    }

    private List<AutoAimTargetListing> collectAutoAimTargets(ServerLevel level,
                                                             CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                             DamageObserverBE observer) {
        Vec3 origin = this.autoAimOrigin(mountAim);
        Object sourceSubLevel = this.resolveContainingSubLevel(observer);
        List<AutoAimTargetListing> targets = new ArrayList<>();
        Set<UUID> seenTargetIds = new HashSet<>();

        AABB bounds = new AABB(origin, origin).inflate(AUTO_AIM_SCAN_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> this.isAutoAimTargetCandidate(candidate, mountAim, sourceSubLevel))) {
            AutoAimTargetListing target = this.buildAutoAimTargetFromEntity(level, mountAim, sourceSubLevel, entity);
            if (target != null && seenTargetIds.add(target.targetId())) {
                targets.add(target);
            }
        }

        for (Object targetSubLevel : this.listActiveSubLevels(level)) {
            AutoAimTargetListing target = this.buildAutoAimTargetFromSubLevel(level, origin, sourceSubLevel, targetSubLevel, false);
            if (target != null && seenTargetIds.add(target.targetId())) {
                targets.add(target);
            }
        }

        return targets;
    }

    @Nullable
    private AutoAimTargetListing buildAutoAimTargetFromEntity(ServerLevel level,
                                                              CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim,
                                                              @Nullable Object sourceSubLevel,
                                                              Entity entity) {
        Object targetSubLevel = this.resolveContainingSubLevel(entity);
        if (entity instanceof AbstractContraptionEntity && targetSubLevel != null) {
            return this.buildAutoAimTargetFromSubLevel(level, this.autoAimOrigin(mountAim), sourceSubLevel, targetSubLevel, false);
        }
        Vec3 aimPoint = this.autoAimPoint(entity, sourceSubLevel);
        if (this.autoAimOrigin(mountAim).distanceToSqr(aimPoint) > AUTO_AIM_SCAN_RADIUS * AUTO_AIM_SCAN_RADIUS) {
            return null;
        }
        double health = -1.0;
        double maxHealth = -1.0;
        if (entity instanceof LivingEntity living) {
            health = Math.max(0.0, living.getHealth());
            maxHealth = Math.max(0.0, living.getMaxHealth());
        }
        return new AutoAimTargetListing(
                entity.getUUID(),
                this.autoAimTargetType(entity, targetSubLevel),
                this.autoAimTargetLabel(entity),
                aimPoint,
                health,
                maxHealth,
                this.autoAimTargetSpeed(level, entity, targetSubLevel, aimPoint));
    }

    @Nullable
    private AutoAimTargetListing buildAutoAimTargetFromSubLevel(ServerLevel level,
                                                                Vec3 origin,
                                                                @Nullable Object sourceSubLevel,
                                                                @Nullable Object targetSubLevel,
                                                                boolean allowOutOfRange) {
        if (targetSubLevel == null || this.sameSubLevel(sourceSubLevel, targetSubLevel)) {
            return null;
        }
        UUID targetId = this.subLevelTargetId(targetSubLevel);
        Vec3 aimPoint = this.subLevelAimPoint(targetSubLevel);
        if (targetId == null || aimPoint == null) {
            return null;
        }
        if (!allowOutOfRange && origin.distanceToSqr(aimPoint) > AUTO_AIM_SCAN_RADIUS * AUTO_AIM_SCAN_RADIUS) {
            return null;
        }
        return new AutoAimTargetListing(
                targetId,
                "SUBLEVEL",
                this.subLevelLabel(targetSubLevel, targetId),
                aimPoint,
                -1.0,
                -1.0,
                this.autoAimTargetSpeed(level, null, targetSubLevel, aimPoint));
    }

    private List<Object> listActiveSubLevels(ServerLevel level) {
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Object container = this.invokeStaticSingleArg(containerClass, "getContainer", level);
            Object allSubLevels = this.invokeZeroArg(container, "getAllSubLevels");
            if (allSubLevels instanceof Iterable<?> iterable) {
                List<Object> results = new ArrayList<>();
                for (Object subLevel : iterable) {
                    if (subLevel != null) {
                        results.add(subLevel);
                    }
                }
                return results;
            }
            if (allSubLevels instanceof Object[] array) {
                List<Object> results = new ArrayList<>(array.length);
                for (Object subLevel : array) {
                    if (subLevel != null) {
                        results.add(subLevel);
                    }
                }
                return results;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return List.of();
    }

    @Nullable
    private Object findActiveSubLevelByTargetId(ServerLevel level, UUID targetId) {
        for (Object subLevel : this.listActiveSubLevels(level)) {
            if (targetId.equals(this.subLevelTargetId(subLevel))) {
                return subLevel;
            }
        }
        return null;
    }

    private boolean sameSubLevel(@Nullable Object first, @Nullable Object second) {
        if (first == null || second == null) {
            return false;
        }
        if (first == second) {
            return true;
        }
        UUID firstId = this.subLevelTargetId(first);
        UUID secondId = this.subLevelTargetId(second);
        return firstId != null && firstId.equals(secondId);
    }

    @Nullable
    private UUID subLevelTargetId(@Nullable Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object value = this.invokeZeroArg(subLevel, "getUniqueId");
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private String subLevelLabel(@Nullable Object subLevel, @Nullable UUID targetId) {
        if (subLevel != null) {
            try {
                Object name = this.invokeZeroArg(subLevel, "getName");
                if (name instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return targetId == null ? "Sub-Level" : "Sub-Level " + this.shortUuid(targetId);
    }

    @Nullable
    private Vec3 subLevelAimPoint(@Nullable Object subLevel) {
        Vector3d origin = SableBridge.originWorld(subLevel);
        if (origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z)) {
            return new Vec3(origin.x, origin.y, origin.z);
        }
        Vector3d localCenter = SableBridge.centerOfMassLocal(subLevel);
        Vector3d worldPoint = SableBridge.localToWorld(subLevel, localCenter);
        if (worldPoint != null && Double.isFinite(worldPoint.x) && Double.isFinite(worldPoint.y) && Double.isFinite(worldPoint.z)) {
            return new Vec3(worldPoint.x, worldPoint.y, worldPoint.z);
        }
        return null;
    }

    private String shortUuid(UUID uuid) {
        String text = uuid.toString();
        return text.length() > 8 ? text.substring(0, 8) : text;
    }

    private boolean isAutoAimTargetCandidate(Entity entity, CreateBigCannonsReflectionAdapter.MountedAimInspection mountAim, @Nullable Object sourceSubLevel) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity.getId() == mountAim.mountedEntityId()) {
            return false;
        }
        if (sourceSubLevel != null && entity instanceof AbstractContraptionEntity) {
            Object targetSubLevel = this.resolveContainingSubLevel(entity);
            if (targetSubLevel != null && targetSubLevel == sourceSubLevel) {
                return false;
            }
        }
        if (entity instanceof Player player) {
            return !player.isSpectator();
        }
        if (entity instanceof AbstractContraptionEntity) {
            return true;
        }
        return entity instanceof LivingEntity;
    }

    private Vec3 autoAimPoint(Entity entity, @Nullable Object sourceSubLevel) {
        Vec3 localPoint;
        if (entity instanceof LivingEntity living) {
            localPoint = living.getEyePosition();
        } else {
            localPoint = entity.getBoundingBox().getCenter();
        }
        Object targetSubLevel = this.resolveContainingSubLevel(entity);
        if (targetSubLevel != null) {
            return this.resolveWorldSpacePointForSubLevel(targetSubLevel, localPoint);
        }
        if (sourceSubLevel != null && this.looksLikeRawSubLevelCoordinate(localPoint)) {
            return this.resolveWorldSpacePointForSubLevel(sourceSubLevel, localPoint);
        }
        return localPoint;
    }

    private String autoAimTargetType(Entity entity, @Nullable Object targetSubLevel) {
        if (entity instanceof Player) {
            return "PLAYER";
        }
        if (entity instanceof AbstractContraptionEntity) {
            return targetSubLevel == null ? "CONTRAPTION" : "SUBLEVEL";
        }
        if (entity instanceof LivingEntity) {
            return "LIVING";
        }
        return "ENTITY";
    }

    private String autoAimTargetLabel(Entity entity) {
        String label = entity.getDisplayName().getString();
        if (label == null || label.isBlank()) {
            label = entity.getType().toShortString();
        }
        return label == null || label.isBlank() ? entity.getUUID().toString().substring(0, 8) : label;
    }

    private double autoAimTargetSpeed(ServerLevel level, @Nullable Entity entity, @Nullable Object targetSubLevel, Vec3 aimPoint) {
        Vec3 velocity = null;
        if (targetSubLevel != null) {
            velocity = SableBridge.velocityAt(level, targetSubLevel, aimPoint);
        }
        if (velocity == null && entity != null) {
            velocity = entity.getDeltaMovement();
        }
        double speed = velocity == null ? 0.0 : velocity.length() * 20.0;
        return Double.isFinite(speed) ? speed : 0.0;
    }

    @Nullable
    private Object resolveContainingSubLevel(Object owner) {
        if (owner == null) {
            return null;
        }
        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Object helper = sableClass.getField("HELPER").get(null);
            return this.invokeSingleArg(helper, "getContaining", owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private Object invokeSingleArg(Object owner, String methodName, Object argument) throws ReflectiveOperationException {
        Class<?> cursor = owner.getClass();
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(owner, argument);
                } catch (IllegalArgumentException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nullable
    private Object invokeZeroArg(@Nullable Object owner, String methodName) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        Class<?> cursor = owner.getClass();
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(owner);
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nullable
    private Object invokeStaticSingleArg(Class<?> ownerClass, String methodName, Object argument) throws ReflectiveOperationException {
        Class<?> cursor = ownerClass;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(null, argument);
                } catch (IllegalArgumentException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private void syncLinkedObservers() {
        this.battleFeedSnapshotCache = null;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private boolean hologramEnabled = true;

    /** Whether the in-world ship hologram should render for this bridge (read client-side by the renderer). */
    public boolean isHologramEnabled() {
        return this.hologramEnabled;
    }

    /** Server-side: flip the in-world hologram on/off, persist it, and sync to nearby clients. */
    public boolean toggleHologramEnabled() {
        this.hologramEnabled = !this.hologramEnabled;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return this.hologramEnabled;
    }

    // lighting scenes: save/recall the whole rig's Stage Light state

    public static final int LIGHT_SCENE_SLOTS = 3;

    /** Persisted scene slots (null = empty). Clients only ever get the filled-slots mask. */
    private final CompoundTag[] lightScenes = new CompoundTag[LIGHT_SCENE_SLOTS];
    private int clientLightSceneMask;

    /** Whether a scene slot holds a saved scene (works on both sides; clients use the synced mask). */
    public boolean isLightSceneSaved(int slot) {
        if (slot < 0 || slot >= LIGHT_SCENE_SLOTS) {
            return false;
        }
        if (this.level != null && this.level.isClientSide) {
            return (this.clientLightSceneMask & (1 << slot)) != 0;
        }
        return this.lightScenes[slot] != null;
    }

    private int lightSceneMask() {
        int mask = 0;
        for (int i = 0; i < LIGHT_SCENE_SLOTS; i++) {
            if (this.lightScenes[i] != null) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** Capture every linked observer's Stage Lights into a scene slot. Server-side. */
    public int saveLightScene(int slot, ServerLevel serverLevel) {
        if (slot < 0 || slot >= LIGHT_SCENE_SLOTS) {
            return 0;
        }
        ListTag list = new ListTag();
        for (LinkedObserver linked : this.getLinkedObservers()) {
            if (!(serverLevel.getBlockEntity(linked.sourcePos()) instanceof DamageObserverBE observer)
                    || observer.getLevel() == null) {
                continue;
            }
            observer.forceScanObservedVolume();
            for (BattleLightSnapshot.Entry entry : observer.getDetectedLights()) {
                if (!(observer.getLevel().getBlockEntity(BlockPos.of(entry.pos())) instanceof StageLightBE light)) {
                    continue;
                }
                CompoundTag t = new CompoundTag();
                t.putLong("Pos", entry.pos());
                t.putBoolean("Lit", light.isLit());
                t.putInt("Pan", light.getPan());
                t.putInt("Tilt", light.getTilt());
                t.putInt("Range", light.getRange());
                t.putInt("Brightness", light.getBrightness());
                t.putInt("Focus", light.getFocus());
                t.putInt("Tint", light.getTint());
                t.putBoolean("BeamVisible", light.isBeamVisible());
                t.putBoolean("Volumetric", light.isVolumetric());
                t.putInt("BeamWidth", light.getBeamWidth());
                t.putInt("LightSpread", light.getLightSpread());
                t.putInt("Effect", light.getEffect());
                t.putInt("EffectSpeed", light.getEffectSpeed());
                list.add(t);
            }
        }
        if (list.isEmpty()) {
            this.lightScenes[slot] = null;
        } else {
            CompoundTag scene = new CompoundTag();
            scene.put("Lights", list);
            this.lightScenes[slot] = scene;
        }
        this.syncLightScenes();
        return list.size();
    }

    /** Re-apply a saved scene onto every fixture still present; returns how many lights it reached. Server-side. */
    public int recallLightScene(int slot, ServerLevel serverLevel) {
        if (slot < 0 || slot >= LIGHT_SCENE_SLOTS || this.lightScenes[slot] == null) {
            return 0;
        }
        ListTag list = this.lightScenes[slot].getList("Lights", Tag.TAG_COMPOUND);
        int applied = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            StageLightBE light = this.resolveLinkedStageLight(serverLevel, BlockPos.of(t.getLong("Pos")));
            if (light == null) {
                continue;
            }
            light.applyConfig(t.getInt("Pan"), t.getInt("Tilt"), t.getInt("Range"), t.getInt("Brightness"),
                    t.getInt("Focus"), t.getInt("Tint"), t.getBoolean("BeamVisible"), t.getBoolean("Volumetric"),
                    t.getInt("BeamWidth"), t.getInt("LightSpread"), t.getInt("Effect"), t.getInt("EffectSpeed"));
            light.setLit(t.getBoolean("Lit"));
            applied++;
        }
        return applied;
    }

    /** Find a Stage Light at a stored position: the display's own level first, then each observer's level (sub-levels). */
    private StageLightBE resolveLinkedStageLight(ServerLevel serverLevel, BlockPos pos) {
        if (serverLevel.getBlockEntity(pos) instanceof StageLightBE light) {
            return light;
        }
        for (LinkedObserver linked : this.getLinkedObservers()) {
            if (serverLevel.getBlockEntity(linked.sourcePos()) instanceof DamageObserverBE observer
                    && observer.getLevel() != null
                    && observer.getLevel().getBlockEntity(pos) instanceof StageLightBE light) {
                return light;
            }
        }
        return null;
    }

    private void syncLightScenes() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.putBoolean("HologramEnabled", this.hologramEnabled);
        for (int i = 0; i < LIGHT_SCENE_SLOTS; i++) {
            if (this.lightScenes[i] != null) {
                tag.put("LightScene" + i, this.lightScenes[i]);
            }
        }
        this.saveLinkedObservers(tag);
        this.saveLinkedControllerConfigs(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.hologramEnabled = !tag.contains("HologramEnabled") || tag.getBoolean("HologramEnabled");
        for (int i = 0; i < LIGHT_SCENE_SLOTS; i++) {
            this.lightScenes[i] = tag.contains("LightScene" + i) ? tag.getCompound("LightScene" + i) : null;
        }
        this.clientLightSceneMask = tag.contains("LightSceneMask") ? tag.getInt("LightSceneMask") : this.lightSceneMask();
        this.loadLinkedObservers(tag);
        this.loadLinkedControllerConfigs(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("HologramEnabled", this.hologramEnabled);
        tag.putInt("LightSceneMask", this.lightSceneMask());
        this.saveLinkedObservers(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.hologramEnabled = !tag.contains("HologramEnabled") || tag.getBoolean("HologramEnabled");
            if (tag.contains("LightSceneMask")) {
                this.clientLightSceneMask = tag.getInt("LightSceneMask");
            }
            this.loadLinkedObservers(tag);
        }
    }

    private void saveLinkedObservers(CompoundTag tag) {
        ListTag list = new ListTag();
        for (LinkedObserver observer : this.linkedObservers.values()) {
            CompoundTag entry = observer.asBattleSnapshot().save();
            CompoundTag weaponTag = entry.getCompound("Weapon");
            weaponTag.putBoolean("AutoFireEnabled", observer.weapon().autoFireEnabled());
            weaponTag.put("AutoAim", observer.weapon().autoAim().save());
            entry.put("Weapon", weaponTag);
            list.add(entry);
        }
        tag.put("LinkedObservers", list);
        long[] autoFire = new long[this.autoFireObservers.size()];
        int index = 0;
        for (BlockPos pos : this.autoFireObservers) {
            autoFire[index++] = pos.asLong();
        }
        tag.putLongArray("AutoFireObservers", autoFire);
        ListTag lockedTargets = new ListTag();
        for (Map.Entry<BlockPos, UUID> entry : this.autoAimLockedTargets.entrySet()) {
            CompoundTag locked = new CompoundTag();
            locked.putLong("ObserverPos", entry.getKey().asLong());
            locked.putUUID("TargetId", entry.getValue());
            lockedTargets.add(locked);
        }
        tag.put("AutoAimLockedTargets", lockedTargets);
        long[] holdDisabled = new long[this.autoAimHoldDisabledObservers.size()];
        int holdIndex = 0;
        for (BlockPos pos : this.autoAimHoldDisabledObservers) {
            holdDisabled[holdIndex++] = pos.asLong();
        }
        tag.putLongArray("AutoAimHoldDisabledObservers", holdDisabled);
        long[] predictionEnabled = new long[this.autoAimPredictionObservers.size()];
        int predictionIndex = 0;
        for (BlockPos pos : this.autoAimPredictionObservers) {
            predictionEnabled[predictionIndex++] = pos.asLong();
        }
        tag.putLongArray("AutoAimPredictionObservers", predictionEnabled);
        long[] advancedPredictionEnabled = new long[this.autoAimAdvancedPredictionObservers.size()];
        int advancedPredictionIndex = 0;
        for (BlockPos pos : this.autoAimAdvancedPredictionObservers) {
            advancedPredictionEnabled[advancedPredictionIndex++] = pos.asLong();
        }
        tag.putLongArray("AutoAimAdvancedPredictionObservers", advancedPredictionEnabled);
        ListTag responseMultipliers = new ListTag();
        for (Map.Entry<BlockPos, Float> entry : this.autoAimResponseMultipliers.entrySet()) {
            CompoundTag response = new CompoundTag();
            response.putLong("ObserverPos", entry.getKey().asLong());
            response.putFloat("Multiplier", entry.getValue());
            responseMultipliers.add(response);
        }
        tag.put("AutoAimResponseMultipliers", responseMultipliers);
    }

    private void loadLinkedObservers(CompoundTag tag) {
        this.battleFeedSnapshotCache = null;
        this.linkedObservers.clear();
        this.autoFireObservers.clear();
        this.autoFireAttemptTicks.clear();
        this.autoAimLockedTargets.clear();
        this.autoAimBlockedReasons.clear();
        this.autoAimLastDriveFailureTicks.clear();
        this.autoAimHoldDisabledObservers.clear();
        this.autoAimPredictionObservers.clear();
        this.autoAimAdvancedPredictionObservers.clear();
        this.autoAimResponseMultipliers.clear();
        this.autoAimLastPredictionTargetIds.clear();
        this.autoAimLastTargetPoints.clear();
        this.autoAimLastTargetTicks.clear();
        this.autoAimSmoothedTargetVelocities.clear();
        this.removeAllLinkedControllerReceivers();
        this.linkedControllerLastFireTicks.clear();
        if (!tag.contains("LinkedObservers", Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList("LinkedObservers", Tag.TAG_COMPOUND);
        List<LinkedObserver> loaded = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BattleObserverSnapshot snapshot = BattleObserverSnapshot.load(entry);
            CompoundTag weaponTag = entry.contains("Weapon", Tag.TAG_COMPOUND) ? entry.getCompound("Weapon") : new CompoundTag();
            boolean autoFireEnabled = weaponTag.getBoolean("AutoFireEnabled");
            AutoAimSnapshot autoAim = weaponTag.contains("AutoAim", Tag.TAG_COMPOUND) ? AutoAimSnapshot.load(weaponTag.getCompound("AutoAim")) : AutoAimSnapshot.none();
            loaded.add(LinkedObserver.fromBattleSnapshot(snapshot, autoFireEnabled, autoAim));
        }
        loaded.sort((a, b) -> {
            int distanceCompare = Integer.compare(a.distance(), b.distance());
            return distanceCompare != 0 ? distanceCompare : String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
        });
        for (LinkedObserver observer : loaded) {
            this.linkedObservers.put(observer.sourcePos(), observer);
        }
        if (tag.contains("AutoFireObservers", Tag.TAG_LONG_ARRAY)) {
            for (long pos : tag.getLongArray("AutoFireObservers")) {
                BlockPos sourcePos = BlockPos.of(pos);
                if (this.linkedObservers.containsKey(sourcePos)) {
                    this.autoFireObservers.add(sourcePos);
                }
            }
        }
        if (tag.contains("AutoAimLockedTargets", Tag.TAG_LIST)) {
            ListTag lockedTargets = tag.getList("AutoAimLockedTargets", Tag.TAG_COMPOUND);
            for (int i = 0; i < lockedTargets.size(); i++) {
                CompoundTag locked = lockedTargets.getCompound(i);
                if (!locked.hasUUID("TargetId")) {
                    continue;
                }
                BlockPos sourcePos = BlockPos.of(locked.getLong("ObserverPos"));
                if (this.linkedObservers.containsKey(sourcePos)) {
                    this.autoAimLockedTargets.put(sourcePos, locked.getUUID("TargetId"));
                }
            }
        }
        if (tag.contains("AutoAimHoldDisabledObservers", Tag.TAG_LONG_ARRAY)) {
            for (long pos : tag.getLongArray("AutoAimHoldDisabledObservers")) {
                BlockPos sourcePos = BlockPos.of(pos);
                if (this.linkedObservers.containsKey(sourcePos)) {
                    this.autoAimHoldDisabledObservers.add(sourcePos);
                }
            }
        }
        if (tag.contains("AutoAimPredictionObservers", Tag.TAG_LONG_ARRAY)) {
            for (long pos : tag.getLongArray("AutoAimPredictionObservers")) {
                BlockPos sourcePos = BlockPos.of(pos);
                if (this.linkedObservers.containsKey(sourcePos)) {
                    this.autoAimPredictionObservers.add(sourcePos);
                }
            }
        }
        if (tag.contains("AutoAimAdvancedPredictionObservers", Tag.TAG_LONG_ARRAY)) {
            for (long pos : tag.getLongArray("AutoAimAdvancedPredictionObservers")) {
                BlockPos sourcePos = BlockPos.of(pos);
                if (this.linkedObservers.containsKey(sourcePos)) {
                    this.autoAimAdvancedPredictionObservers.add(sourcePos);
                    this.autoAimPredictionObservers.add(sourcePos);
                }
            }
        }
        if (tag.contains("AutoAimResponseMultipliers", Tag.TAG_LIST)) {
            ListTag responseMultipliers = tag.getList("AutoAimResponseMultipliers", Tag.TAG_COMPOUND);
            for (int i = 0; i < responseMultipliers.size(); i++) {
                CompoundTag response = responseMultipliers.getCompound(i);
                BlockPos sourcePos = BlockPos.of(response.getLong("ObserverPos"));
                if (this.linkedObservers.containsKey(sourcePos) && response.contains("Multiplier")) {
                    this.autoAimResponseMultipliers.put(sourcePos, clampAutoAimResponse(response.getFloat("Multiplier")));
                }
            }
        }
    }

    private ItemStackHandler linkedControllerInventory(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        return this.linkedControllerInventories.computeIfAbsent(key, this::createLinkedControllerInventory);
    }

    private ItemStackHandler createLinkedControllerInventory(BlockPos sourcePos) {
        BlockPos key = sourcePos.immutable();
        return new ItemStackHandler(LINKED_CONTROLLER_SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                BattleScreenDisplayBE.this.setChanged();
                if (BattleScreenDisplayBE.this.level instanceof ServerLevel serverLevel) {
                    BattleScreenDisplayBE.this.refreshLinkedControllerReceivers(serverLevel, key);
                }
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    private void refreshLinkedControllerReceivers(ServerLevel serverLevel) {
        Set<BlockPos> positions = new HashSet<>();
        positions.addAll(this.linkedControllerInventories.keySet());
        positions.addAll(this.linkedControllerReceivers.keySet());
        positions.addAll(this.linkedObservers.keySet());
        for (BlockPos sourcePos : positions) {
            this.refreshLinkedControllerReceivers(serverLevel, sourcePos);
        }
    }

    private void refreshLinkedControllerReceivers(ServerLevel serverLevel, BlockPos sourcePos) {
        if (!this.linkedObservers.containsKey(sourcePos)) {
            this.removeLinkedControllerReceivers(sourcePos);
            return;
        }
        Map<Integer, BattleLinkedControllerReceiver> activeReceivers =
                this.linkedControllerReceivers.computeIfAbsent(sourcePos.immutable(), ignored -> new LinkedHashMap<>());
        Set<Integer> keepButtons = new HashSet<>();
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            Couple<RedstoneLinkNetworkHandler.Frequency> key = this.linkedControllerNetworkKey(sourcePos, button);
            if (key == null) {
                this.removeLinkedControllerReceiver(sourcePos, button);
                continue;
            }
            keepButtons.add(button);
            BattleLinkedControllerReceiver existing = activeReceivers.get(button);
            if (existing != null && existing.matches(key)) {
                continue;
            }
            if (existing != null) {
                this.removeLinkedControllerReceiver(sourcePos, button);
            }
            BattleLinkedControllerReceiver receiver = new BattleLinkedControllerReceiver(sourcePos, button, key);
            activeReceivers.put(button, receiver);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, receiver);
            } catch (RuntimeException | LinkageError ex) {
                activeReceivers.remove(button);
                CreateStats.LOGGER.debug("[BATTLE@{}] Failed to add linked controller receiver {} for {}", this.getBlockPos(), button, sourcePos, ex);
            }
        }
        List<Integer> staleButtons = new ArrayList<>();
        for (Integer button : activeReceivers.keySet()) {
            if (!keepButtons.contains(button)) {
                staleButtons.add(button);
            }
        }
        for (Integer button : staleButtons) {
            this.removeLinkedControllerReceiver(sourcePos, button);
        }
        if (activeReceivers.isEmpty()) {
            this.linkedControllerReceivers.remove(sourcePos);
        }
    }

    private void removeLinkedControllerReceivers(BlockPos sourcePos) {
        Map<Integer, BattleLinkedControllerReceiver> receivers = this.linkedControllerReceivers.get(sourcePos);
        if (receivers == null || receivers.isEmpty()) {
            this.linkedControllerReceivedStrengths.remove(sourcePos);
            this.linkedControllerLastInputMasks.remove(sourcePos);
            return;
        }
        List<Integer> buttons = new ArrayList<>(receivers.keySet());
        for (Integer button : buttons) {
            this.removeLinkedControllerReceiver(sourcePos, button);
        }
        this.linkedControllerReceivers.remove(sourcePos);
        this.linkedControllerReceivedStrengths.remove(sourcePos);
        this.linkedControllerLastInputMasks.remove(sourcePos);
    }

    private void removeLinkedControllerReceiver(BlockPos sourcePos, int button) {
        Map<Integer, BattleLinkedControllerReceiver> receivers = this.linkedControllerReceivers.get(sourcePos);
        if (receivers == null) {
            return;
        }
        BattleLinkedControllerReceiver receiver = receivers.remove(button);
        int[] strengths = this.linkedControllerReceivedStrengths.get(sourcePos);
        if (strengths != null && button >= 0 && button < strengths.length) {
            strengths[button] = 0;
        }
        if (receiver == null) {
            return;
        }
        receiver.markDead();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, receiver);
        } catch (RuntimeException | LinkageError ex) {
            CreateStats.LOGGER.debug("[BATTLE@{}] Failed to remove linked controller receiver {} for {}", this.getBlockPos(), button, sourcePos, ex);
        }
    }

    private void removeAllLinkedControllerReceivers() {
        List<BlockPos> observers = new ArrayList<>(this.linkedControllerReceivers.keySet());
        for (BlockPos sourcePos : observers) {
            this.removeLinkedControllerReceivers(sourcePos);
        }
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> linkedControllerNetworkKey(BlockPos sourcePos, int button) {
        if (!this.isLinkedControllerButtonConfigured(sourcePos, button)) {
            return null;
        }
        ItemStackHandler handler = this.linkedControllerInventory(sourcePos);
        ItemStack firstFrequency = this.singleFrequencyStack(handler.getStackInSlot(getLinkedControllerSlotIndex(button, false)));
        ItemStack secondFrequency = this.singleFrequencyStack(handler.getStackInSlot(getLinkedControllerSlotIndex(button, true)));
        if (firstFrequency.isEmpty() && !secondFrequency.isEmpty()) {
            firstFrequency = secondFrequency;
            secondFrequency = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(firstFrequency),
                RedstoneLinkNetworkHandler.Frequency.of(secondFrequency));
    }

    private boolean isLinkedControllerButtonConfigured(BlockPos sourcePos, int button) {
        if (button < 0 || button >= LINKED_CONTROLLER_BUTTON_COUNT) {
            return false;
        }
        ItemStackHandler handler = this.linkedControllerInventory(sourcePos);
        return !handler.getStackInSlot(getLinkedControllerSlotIndex(button, false)).isEmpty()
                || !handler.getStackInSlot(getLinkedControllerSlotIndex(button, true)).isEmpty();
    }

    private ItemStack singleFrequencyStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private int[] linkedControllerStrengths(BlockPos sourcePos) {
        return this.linkedControllerReceivedStrengths.computeIfAbsent(sourcePos.immutable(), ignored -> new int[LINKED_CONTROLLER_BUTTON_COUNT]);
    }

    private int linkedControllerManualAimRpm(BlockPos sourcePos) {
        return clampLinkedControllerManualAimRpm(this.linkedControllerManualAimRpms.getOrDefault(sourcePos, LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT));
    }

    private static int clampLinkedControllerManualAimRpm(int rpm) {
        return Mth.clamp(rpm, LINKED_CONTROLLER_MANUAL_AIM_RPM_MIN, LINKED_CONTROLLER_MANUAL_AIM_RPM_MAX);
    }

    private int linkedControllerInputMask(BlockPos sourcePos) {
        int mask = 0;
        int[] strengths = this.linkedControllerStrengths(sourcePos);
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            if (strengths[button] > 0) {
                mask |= 1 << button;
            }
        }
        return mask;
    }

    private boolean isLinkedControllerInputActive(int inputMask, int button) {
        return (inputMask & (1 << button)) != 0;
    }

    private int inputDirection(int inputMask, int positiveButton, int negativeButton) {
        boolean positive = this.isLinkedControllerInputActive(inputMask, positiveButton);
        boolean negative = this.isLinkedControllerInputActive(inputMask, negativeButton);
        if (positive == negative) {
            return 0;
        }
        return positive ? 1 : -1;
    }

    private boolean supportsLinkedControllerWeapon(DamageObserverBE observer) {
        return observer.isWeaponDetected() && this.supportsLinkedControllerWeapon(observer.getWeaponKind());
    }

    private boolean supportsLinkedControllerWeapon(String kind) {
        return this.isAutocannonWeapon(kind) || this.isBigCannonWeapon(kind);
    }

    private boolean supportsAutoAimWeapon(DamageObserverBE observer) {
        return observer.isWeaponDetected() && this.supportsAutoAimWeapon(observer.getWeaponKind());
    }

    private boolean supportsAutoAimWeapon(String kind) {
        return this.supportsLinkedControllerWeapon(kind);
    }

    private boolean supportsAutoAimPrediction(DamageObserverBE observer) {
        return this.isAutocannonWeapon(observer.getWeaponKind());
    }

    private boolean isAutocannonWeapon(String kind) {
        return "AUTOCANNON".equals(kind);
    }

    private boolean isBigCannonWeapon(String kind) {
        return isBigCannonWeaponKind(kind);
    }

    private static boolean isBigCannonWeaponKind(@Nullable String kind) {
        return "BIG_CANNON".equals(kind);
    }

    private void saveLinkedControllerConfigs(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag configs = new ListTag();
        Set<BlockPos> configuredObservers = new HashSet<>();
        configuredObservers.addAll(this.linkedControllerInventories.keySet());
        configuredObservers.addAll(this.linkedControllerManualAimRpms.keySet());
        for (BlockPos sourcePos : configuredObservers) {
            ItemStackHandler handler = this.linkedControllerInventory(sourcePos);
            int manualAimRpm = this.linkedControllerManualAimRpm(sourcePos);
            boolean empty = true;
            for (int slot = 0; slot < LINKED_CONTROLLER_SLOT_COUNT; slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if (empty && manualAimRpm == LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT) {
                continue;
            }
            CompoundTag config = new CompoundTag();
            config.putLong("ObserverPos", sourcePos.asLong());
            config.put("Inventory", handler.serializeNBT(registries));
            config.putInt("ManualAimRpm", manualAimRpm);
            configs.add(config);
        }
        tag.put("LinkedControllerConfigs", configs);
    }

    private void loadLinkedControllerConfigs(CompoundTag tag, HolderLookup.Provider registries) {
        this.linkedControllerInventories.clear();
        this.linkedControllerReceivedStrengths.clear();
        this.linkedControllerLastInputMasks.clear();
        this.linkedControllerLastFireTicks.clear();
        this.linkedControllerManualAimRpms.clear();
        if (!tag.contains("LinkedControllerConfigs", Tag.TAG_LIST)) {
            return;
        }
        ListTag configs = tag.getList("LinkedControllerConfigs", Tag.TAG_COMPOUND);
        for (int i = 0; i < configs.size(); i++) {
            CompoundTag config = configs.getCompound(i);
            BlockPos sourcePos = BlockPos.of(config.getLong("ObserverPos"));
            ItemStackHandler handler = this.linkedControllerInventory(sourcePos);
            if (config.contains("Inventory", Tag.TAG_COMPOUND)) {
                handler.deserializeNBT(registries, config.getCompound("Inventory"));
            }
            if (config.contains("ManualAimRpm")) {
                int manualAimRpm = clampLinkedControllerManualAimRpm(config.getInt("ManualAimRpm"));
                if (manualAimRpm != LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT) {
                    this.linkedControllerManualAimRpms.put(sourcePos, manualAimRpm);
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        this.removeAllLinkedControllerReceivers();
        super.setRemoved();
    }

    private CreateBigCannonsReflectionAdapter.MountedAimInspection resolveWorldMountedAim(@Nullable BlockEntity mountBlockEntity,
                                                                                           CreateBigCannonsReflectionAdapter.MountedAimInspection inspection) {
        if (!inspection.available()) {
            return inspection;
        }
        Vec3 worldPoint = this.resolveWorldSpacePoint(mountBlockEntity, inspection.interactionLocation());
        Vec3 aimOrigin = this.resolveWorldSpacePoint(mountBlockEntity, inspection.aimOriginLocation());
        Vec3 aimForward = this.resolveWorldSpacePoint(mountBlockEntity, inspection.aimForwardLocation());
        return new CreateBigCannonsReflectionAdapter.MountedAimInspection(
                inspection.available(),
                inspection.running(),
                inspection.fixedMount(),
                inspection.mountedEntityId(),
                worldPoint,
                inspection.yawDegrees(),
                inspection.pitchDegrees(),
                inspection.yawSpeed(),
            inspection.pitchSpeed(),
            aimOrigin,
            aimForward);
    }

    private Vec3 resolveWorldSpacePoint(@Nullable Object owner, Vec3 localPoint) {
        Object subLevel = owner instanceof BlockEntity blockEntity
                ? SableBridge.subLevelAccessOf(blockEntity)
                : this.resolveContainingSubLevel(owner);
        return this.resolveWorldSpacePointForSubLevel(subLevel, localPoint);
    }

    private Vec3 resolveWorldSpacePointForSubLevel(@Nullable Object subLevel, Vec3 localPoint) {
        if (subLevel == null) {
            return localPoint;
        }
        Vector3d world = SableBridge.localToWorld(subLevel, new Vector3d(localPoint.x, localPoint.y, localPoint.z));
        if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
            return new Vec3(world.x, world.y, world.z);
        }
        return localPoint;
    }

    private boolean looksLikeRawSubLevelCoordinate(Vec3 point) {
        return Math.abs(point.x) >= RAW_SUBLEVEL_COORDINATE_THRESHOLD
                || Math.abs(point.y) >= RAW_SUBLEVEL_COORDINATE_THRESHOLD
                || Math.abs(point.z) >= RAW_SUBLEVEL_COORDINATE_THRESHOLD;
    }

    private static DamageObserverSettings.ObserverCategory safeCategory(String value) {
        try {
            return DamageObserverSettings.ObserverCategory.valueOf(value);
        } catch (Exception ignored) {
            return DamageObserverSettings.ObserverCategory.HULL;
        }
    }

    private static DamageObserverSettings.WatchMode safeMode(String value) {
        try {
            return DamageObserverSettings.WatchMode.valueOf(value);
        } catch (Exception ignored) {
            return DamageObserverSettings.WatchMode.SINGLE_BLOCK;
        }
    }

    public record LinkedObserver(
            String name,
            DamageObserverSettings.ObserverCategory category,
            DamageObserverSettings.WatchMode mode,
            BlockPos sourcePos,
            BlockPos anchor,
            int diameter,
            int volume,
            int distance,
            String dimension,
            long lastSeenTick,
            double healthPercent,
            int watchedBlocks,
            int baselineBlocks,
            int presentBaselineBlocks,
            int missingBlocks,
            int newBlocks,
            int cargoContainers,
            long cargoItems,
            long cargoItemCapacity,
            long cargoFluidAmount,
            long cargoFluidCapacity,
            long cargoEnergyStored,
            long cargoEnergyCapacity,
            WeaponSnapshot weapon,
            BattleLightSnapshot lights) {

        public static LinkedObserver fromBattleSnapshot(BattleObserverSnapshot snapshot, boolean autoFireEnabled, AutoAimSnapshot autoAim) {
            return new LinkedObserver(
                    snapshot.name(),
                    snapshot.category(),
                    snapshot.mode(),
                    snapshot.sourcePos(),
                    snapshot.anchor(),
                    snapshot.diameter(),
                    snapshot.volume(),
                    snapshot.distance(),
                    snapshot.dimension(),
                    snapshot.lastSeenTick(),
                    snapshot.healthPercent(),
                    snapshot.watchedBlocks(),
                    snapshot.baselineBlocks(),
                    snapshot.presentBaselineBlocks(),
                    snapshot.missingBlocks(),
                    snapshot.newBlocks(),
                    snapshot.cargoContainers(),
                    snapshot.cargoItems(),
                    snapshot.cargoItemCapacity(),
                    snapshot.cargoFluidAmount(),
                    snapshot.cargoFluidCapacity(),
                    snapshot.cargoEnergyStored(),
                    snapshot.cargoEnergyCapacity(),
                    WeaponSnapshot.fromBattleSnapshot(snapshot.weapon(), autoFireEnabled, autoAim),
                    snapshot.lights());
        }

        public BattleObserverSnapshot asBattleSnapshot() {
            return new BattleObserverSnapshot(
                    this.name,
                    this.category,
                    this.mode,
                    this.sourcePos,
                    this.anchor,
                    this.diameter,
                    this.volume,
                    this.distance,
                    this.dimension,
                    this.lastSeenTick,
                    this.healthPercent,
                    this.watchedBlocks,
                    this.baselineBlocks,
                    this.presentBaselineBlocks,
                    this.missingBlocks,
                    this.newBlocks,
                    this.cargoContainers,
                    this.cargoItems,
                    this.cargoItemCapacity,
                    this.cargoFluidAmount,
                    this.cargoFluidCapacity,
                    this.cargoEnergyStored,
                    this.cargoEnergyCapacity,
                    this.weapon.toBattleSnapshot(),
                    this.lights);
        }

        public boolean sameDisplayData(LinkedObserver other) {
            return other != null
                    && this.asBattleSnapshot().sameDisplayData(other.asBattleSnapshot())
                    && this.weapon.autoFireEnabled == other.weapon.autoFireEnabled
                    && this.weapon.autoAim.equals(other.weapon.autoAim);
        }
    }

    public record AutoAimSnapshot(
            boolean mountAvailable,
            boolean mountRunning,
            boolean fixedMount,
            boolean targetLocked,
            boolean holdEnabled,
            boolean predictionEnabled,
            boolean advancedPredictionEnabled,
            boolean predictionActive,
            String predictionStatusText,
            float responseMultiplier,
            boolean targetPresent,
            String statusText,
            String blockedReason,
            String targetType,
            String targetLabel,
            double targetDistance,
            float mountYawDegrees,
            float mountPitchDegrees,
            double aimForwardWorldX,
            double aimForwardWorldY,
            double aimForwardWorldZ,
            float targetYawDegrees,
            float targetPitchDegrees,
            float yawErrorDegrees,
            float pitchErrorDegrees,
            List<AutoAimTargetOption> targetOptions) {

        public static AutoAimSnapshot none() {
            return new AutoAimSnapshot(false, false, false, false, true, false, false, false, "", AUTO_AIM_RESPONSE_DEFAULT, false, "", "", "", "", 0.0, 0.0f, 0.0f, Double.NaN, Double.NaN, Double.NaN, 0.0f, 0.0f, 0.0f, 0.0f, List.of());
        }

        public static AutoAimSnapshot mountOnly(boolean mountRunning,
                                                boolean fixedMount,
                                                float mountYawDegrees,
                                                float mountPitchDegrees,
                                                double aimForwardWorldX,
                                                double aimForwardWorldY,
                                                double aimForwardWorldZ,
                                                boolean targetLocked,
                                                boolean holdEnabled,
                                                boolean predictionEnabled,
                                                boolean advancedPredictionEnabled,
                                                boolean predictionActive,
                                                String predictionStatusText,
                                                float responseMultiplier,
                                                String statusText,
                                                String blockedReason,
                                                List<AutoAimTargetOption> targetOptions) {
            return new AutoAimSnapshot(true, mountRunning, fixedMount, targetLocked, holdEnabled, predictionEnabled, advancedPredictionEnabled, predictionActive, predictionStatusText, responseMultiplier, false, statusText, blockedReason, "", "", 0.0, mountYawDegrees, mountPitchDegrees, aimForwardWorldX, aimForwardWorldY, aimForwardWorldZ, 0.0f, 0.0f, 0.0f, 0.0f, List.copyOf(targetOptions));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("MountAvailable", this.mountAvailable);
            tag.putBoolean("MountRunning", this.mountRunning);
            tag.putBoolean("FixedMount", this.fixedMount);
            tag.putBoolean("TargetLocked", this.targetLocked);
            tag.putBoolean("HoldEnabled", this.holdEnabled);
            tag.putBoolean("PredictionEnabled", this.predictionEnabled);
            tag.putBoolean("AdvancedPredictionEnabled", this.advancedPredictionEnabled);
            tag.putBoolean("PredictionActive", this.predictionActive);
            tag.putString("PredictionStatusText", this.predictionStatusText);
            tag.putFloat("ResponseMultiplier", this.responseMultiplier);
            tag.putBoolean("TargetPresent", this.targetPresent);
            tag.putString("StatusText", this.statusText);
            tag.putString("BlockedReason", this.blockedReason);
            tag.putString("TargetType", this.targetType);
            tag.putString("TargetLabel", this.targetLabel);
            tag.putDouble("TargetDistance", this.targetDistance);
            tag.putFloat("MountYawDegrees", this.mountYawDegrees);
            tag.putFloat("MountPitchDegrees", this.mountPitchDegrees);
            tag.putDouble("AimForwardWorldX", this.aimForwardWorldX);
            tag.putDouble("AimForwardWorldY", this.aimForwardWorldY);
            tag.putDouble("AimForwardWorldZ", this.aimForwardWorldZ);
            tag.putFloat("TargetYawDegrees", this.targetYawDegrees);
            tag.putFloat("TargetPitchDegrees", this.targetPitchDegrees);
            tag.putFloat("YawErrorDegrees", this.yawErrorDegrees);
            tag.putFloat("PitchErrorDegrees", this.pitchErrorDegrees);
            ListTag targets = new ListTag();
            for (AutoAimTargetOption option : this.targetOptions) {
                targets.add(option.save());
            }
            tag.put("TargetOptions", targets);
            return tag;
        }

        public static AutoAimSnapshot load(CompoundTag tag) {
            List<AutoAimTargetOption> targetOptions = new ArrayList<>();
            if (tag.contains("TargetOptions", Tag.TAG_LIST)) {
                ListTag targets = tag.getList("TargetOptions", Tag.TAG_COMPOUND);
                for (int i = 0; i < targets.size(); i++) {
                    AutoAimTargetOption option = AutoAimTargetOption.load(targets.getCompound(i));
                    if (option != null) {
                        targetOptions.add(option);
                    }
                }
            }
            return new AutoAimSnapshot(
                    tag.getBoolean("MountAvailable"),
                    tag.getBoolean("MountRunning"),
                    tag.getBoolean("FixedMount"),
                    tag.getBoolean("TargetLocked"),
                    !tag.contains("HoldEnabled") || tag.getBoolean("HoldEnabled"),
                    tag.getBoolean("PredictionEnabled"),
                    tag.getBoolean("AdvancedPredictionEnabled"),
                    tag.getBoolean("PredictionActive"),
                    tag.contains("PredictionStatusText") ? tag.getString("PredictionStatusText") : "",
                    tag.contains("ResponseMultiplier") ? clampAutoAimResponse(tag.getFloat("ResponseMultiplier")) : AUTO_AIM_RESPONSE_DEFAULT,
                    tag.getBoolean("TargetPresent"),
                    tag.contains("StatusText") ? tag.getString("StatusText") : "",
                    tag.contains("BlockedReason") ? tag.getString("BlockedReason") : "",
                    tag.contains("TargetType") ? tag.getString("TargetType") : "",
                    tag.contains("TargetLabel") ? tag.getString("TargetLabel") : "",
                    tag.contains("TargetDistance") ? tag.getDouble("TargetDistance") : 0.0,
                    tag.contains("MountYawDegrees") ? tag.getFloat("MountYawDegrees") : 0.0f,
                    tag.contains("MountPitchDegrees") ? tag.getFloat("MountPitchDegrees") : 0.0f,
                    tag.contains("AimForwardWorldX") ? tag.getDouble("AimForwardWorldX") : Double.NaN,
                    tag.contains("AimForwardWorldY") ? tag.getDouble("AimForwardWorldY") : Double.NaN,
                    tag.contains("AimForwardWorldZ") ? tag.getDouble("AimForwardWorldZ") : Double.NaN,
                    tag.contains("TargetYawDegrees") ? tag.getFloat("TargetYawDegrees") : 0.0f,
                    tag.contains("TargetPitchDegrees") ? tag.getFloat("TargetPitchDegrees") : 0.0f,
                    tag.contains("YawErrorDegrees") ? tag.getFloat("YawErrorDegrees") : 0.0f,
                    tag.contains("PitchErrorDegrees") ? tag.getFloat("PitchErrorDegrees") : 0.0f,
                    List.copyOf(targetOptions));
        }
    }

    public record AutoAimTargetOption(
            UUID targetId,
            String type,
            String label,
            double distance,
            double health,
            double maxHealth,
            double speed,
            double worldX,
            double worldY,
            double worldZ,
            boolean locked) {

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("TargetId", this.targetId);
            tag.putString("Type", this.type);
            tag.putString("Label", this.label);
            tag.putDouble("Distance", this.distance);
            tag.putDouble("Health", this.health);
            tag.putDouble("MaxHealth", this.maxHealth);
            tag.putDouble("Speed", this.speed);
            tag.putDouble("WorldX", this.worldX);
            tag.putDouble("WorldY", this.worldY);
            tag.putDouble("WorldZ", this.worldZ);
            tag.putBoolean("Locked", this.locked);
            return tag;
        }

        @Nullable
        private static AutoAimTargetOption load(CompoundTag tag) {
            if (!tag.hasUUID("TargetId")) {
                return null;
            }
            return new AutoAimTargetOption(
                    tag.getUUID("TargetId"),
                    tag.contains("Type") ? tag.getString("Type") : "ENTITY",
                    tag.contains("Label") ? tag.getString("Label") : "Target",
                    tag.contains("Distance") ? tag.getDouble("Distance") : 0.0,
                    tag.contains("Health") ? tag.getDouble("Health") : -1.0,
                    tag.contains("MaxHealth") ? tag.getDouble("MaxHealth") : -1.0,
                    tag.contains("Speed") ? tag.getDouble("Speed") : 0.0,
                    tag.contains("WorldX") ? tag.getDouble("WorldX") : 0.0,
                    tag.contains("WorldY") ? tag.getDouble("WorldY") : 0.0,
                    tag.contains("WorldZ") ? tag.getDouble("WorldZ") : 0.0,
                    tag.getBoolean("Locked"));
        }
    }

    private record AutoAimCandidate(
            UUID targetId,
            String type,
            String label,
            Vec3 aimPoint) {
    }

    private record AutoAimTargetListing(
            UUID targetId,
            String type,
            String label,
            Vec3 aimPoint,
            double health,
            double maxHealth,
            double speed) {

        private AutoAimCandidate asCandidate() {
            return new AutoAimCandidate(this.targetId, this.type, this.label, this.aimPoint);
        }
    }

        private record ResolvedAutoAimTarget(
            boolean targetLocked,
            boolean holdEnabled,
                boolean predictionEnabled,
                boolean advancedPredictionEnabled,
                boolean predictionActive,
                String predictionStatusText,
            String statusText,
            @Nullable AutoAimCandidate candidate,
            boolean stateChanged) {
        }

        private record AutoAimMeasurement(
                double targetDistance,
            float currentYawDegrees,
            float currentPitchDegrees,
                float targetYawDegrees,
                float targetPitchDegrees,
                float yawErrorDegrees,
                float pitchErrorDegrees) {
        }

            private record PredictionResolution(
                AutoAimCandidate candidate,
                    boolean active,
                    String statusText) {
            }

        private record ObservedMountController(
            BlockEntity blockEntity,
            CreateBigCannonsReflectionAdapter.MountedAimInspection inspection) {
        }

        private record ObservedGovernorController(
            BlockPos pos,
            RpmGovernorBE governor) {
        }

        private record AutoAimGovernorPair(
            ObservedGovernorController yaw,
            ObservedGovernorController pitch) {
        }

        private record AutoAimGovernorCommand(
            boolean accepted,
            int targetRpm) {
        }

    private final class BattleLinkedControllerReceiver implements IRedstoneLinkable {
        private final BlockPos observerPos;
        private final int button;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean alive = true;

        private BattleLinkedControllerReceiver(BlockPos observerPos, int button, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.observerPos = observerPos.immutable();
            this.button = button;
            this.key = key;
        }

        private boolean matches(Couple<RedstoneLinkNetworkHandler.Frequency> otherKey) {
            return this.key.equals(otherKey);
        }

        private void markDead() {
            this.alive = false;
        }

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int strength) {
            int[] strengths = BattleScreenDisplayBE.this.linkedControllerStrengths(this.observerPos);
            if (this.button < 0 || this.button >= strengths.length) {
                return;
            }
            strengths[this.button] = Mth.clamp(strength, 0, 15);
        }

        @Override
        public boolean isListening() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return this.alive
                    && !BattleScreenDisplayBE.this.isRemoved()
                    && BattleScreenDisplayBE.this.level != null
                    && !BattleScreenDisplayBE.this.level.isClientSide
                    && BattleScreenDisplayBE.this.linkedObservers.containsKey(this.observerPos)
                    && BattleScreenDisplayBE.this.isLinkedControllerButtonConfigured(this.observerPos, this.button);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return this.key;
        }

        @Override
        public BlockPos getLocation() {
            return BattleScreenDisplayBE.this.getBlockPos();
        }
    }

    public record WeaponSnapshot(
            boolean detected,
            String kind,
            String readiness,
            String loadedState,
            String material,
            String displayName,
            int blocks,
            int baselineBlocks,
            int presentBlocks,
            int missingBlocks,
            int barrelBlocks,
            int chamberBlocks,
            int breechBlocks,
            int muzzleBlocks,
            int mountBlocks,
            int loaderBlocks,
            int recoilBlocks,
            int ammoContainers,
            int ammoRounds,
            int ammoCapacity,
            int tracerRounds,
            boolean ammoCreative,
            int fireRateRpm,
            int projectileBlocks,
            int propellantBlocks,
            int barrelPayloadBlocks,
            int chamberPayloadBlocks,
            int breechPayloadBlocks,
            int unknownPayloadBlocks,
            int inspectedPayloadBlocks,
            int emptyPayloadBlocks,
            int missingBarrelBlocks,
            int missingBreechBlocks,
            int missingMountBlocks,
            int missingLoaderBlocks,
            boolean autoFireEnabled,
            AutoAimSnapshot autoAim) {

        public static WeaponSnapshot fromBattleSnapshot(BattleWeaponSnapshot snapshot, boolean autoFireEnabled, AutoAimSnapshot autoAim) {
            return new WeaponSnapshot(
                    snapshot.detected(),
                    snapshot.kind(),
                    snapshot.readiness(),
                    snapshot.loadedState(),
                    snapshot.material(),
                    snapshot.displayName(),
                    snapshot.blocks(),
                    snapshot.baselineBlocks(),
                    snapshot.presentBlocks(),
                    snapshot.missingBlocks(),
                    snapshot.barrelBlocks(),
                    snapshot.chamberBlocks(),
                    snapshot.breechBlocks(),
                    snapshot.muzzleBlocks(),
                    snapshot.mountBlocks(),
                    snapshot.loaderBlocks(),
                    snapshot.recoilBlocks(),
                    snapshot.ammoContainers(),
                    snapshot.ammoRounds(),
                    snapshot.ammoCapacity(),
                    snapshot.tracerRounds(),
                    snapshot.ammoCreative(),
                    snapshot.fireRateRpm(),
                    snapshot.projectileBlocks(),
                    snapshot.propellantBlocks(),
                    snapshot.barrelPayloadBlocks(),
                    snapshot.chamberPayloadBlocks(),
                    snapshot.breechPayloadBlocks(),
                    snapshot.unknownPayloadBlocks(),
                    snapshot.inspectedPayloadBlocks(),
                    snapshot.emptyPayloadBlocks(),
                    snapshot.missingBarrelBlocks(),
                    snapshot.missingBreechBlocks(),
                    snapshot.missingMountBlocks(),
                    snapshot.missingLoaderBlocks(),
                    autoFireEnabled,
                    autoAim);
        }

        public BattleWeaponSnapshot toBattleSnapshot() {
            return new BattleWeaponSnapshot(
                    this.detected,
                    this.kind,
                    this.readiness,
                    this.loadedState,
                    this.material,
                    this.displayName,
                    this.blocks,
                    this.baselineBlocks,
                    this.presentBlocks,
                    this.missingBlocks,
                    this.barrelBlocks,
                    this.chamberBlocks,
                    this.breechBlocks,
                    this.muzzleBlocks,
                    this.mountBlocks,
                    this.loaderBlocks,
                    this.recoilBlocks,
                    this.ammoContainers,
                    this.ammoRounds,
                    this.ammoCapacity,
                    this.tracerRounds,
                    this.ammoCreative,
                    this.fireRateRpm,
                    this.projectileBlocks,
                    this.propellantBlocks,
                    this.barrelPayloadBlocks,
                    this.chamberPayloadBlocks,
                    this.breechPayloadBlocks,
                    this.unknownPayloadBlocks,
                    this.inspectedPayloadBlocks,
                    this.emptyPayloadBlocks,
                    this.missingBarrelBlocks,
                    this.missingBreechBlocks,
                    this.missingMountBlocks,
                    this.missingLoaderBlocks);
        }

        public static WeaponSnapshot empty() {
            return fromBattleSnapshot(BattleWeaponSnapshot.empty(), false, AutoAimSnapshot.none());
        }

        public CompoundTag save() {
            CompoundTag tag = this.toBattleSnapshot().save();
            tag.putBoolean("AutoFireEnabled", this.autoFireEnabled);
            tag.put("AutoAim", this.autoAim.save());
            return tag;
        }

        public static WeaponSnapshot load(CompoundTag tag) {
            return fromBattleSnapshot(
                    BattleWeaponSnapshot.load(tag),
                    tag.getBoolean("AutoFireEnabled"),
                    tag.contains("AutoAim", Tag.TAG_COMPOUND) ? AutoAimSnapshot.load(tag.getCompound("AutoAim")) : AutoAimSnapshot.none());
        }
    }
}
