package com.createstats.vccmk2;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.item.FlightCardData;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** [1] */
public class VccMk2BlockEntity extends BlockEntity implements MenuProvider, com.createstats.power.IFeConsumer {

    public static final int CARD_ROWS = 8;
    public static final int CARD_SLOTS = CARD_ROWS * 2;

    public static int propSlot(int row) {
        return row * 2;
    }

    public static int govSlot(int row) {
        return row * 2 + 1;
    }

    /** Input channels the VCC can receive on; each has a 2-item Redstone Link frequency. */
    public static final String[] CHANNELS = {
            "throttle_up", "throttle_dn", "yaw_left", "yaw_right", "lev_up", "lev_dn",
            "pitch_up", "pitch_dn", "roll_left", "roll_right", "brake",
            "arm", "mode", "stab", "aux0", "aux1", "bay", "drop"
    };

    public static int channelIndex(String id) {
        for (int i = 0; i < CHANNELS.length; i++) {
            if (CHANNELS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private VccMk2CraftProfile profile = VccMk2CraftProfile.AIRSHIP;
    private boolean profileChosen;
    private boolean armed;

    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_VCC_MK2));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }
    private boolean autoMode;
    /** Stabilization: true = Assisted (self-level via gimbal), false = Direct (raw). */
    private boolean assisted = true;

    /** Bitmask of {@link VccMk2Role} per engine row; 0 = idle. An engine can hold several roles. */
    private final int[] roleMasks = new int[CARD_ROWS];

    /** [2] */
    private final boolean[] redstoneEngine = new boolean[CARD_ROWS];

    // backend (control)
    /** Universal control-axis demands set by the cockpit (set-and-hold). Signed, -1..1. */
    public static final int AXIS_THROTTLE = 0;
    public static final int AXIS_YAW = 1;
    public static final int AXIS_VERTICAL = 2;
    public static final int AXIS_PITCH = 3;
    public static final int AXIS_ROLL = 4;
    public static final int AXIS_BRAKE = 5;
    private static final int AXIS_COUNT = 6;
    private static final int WHEEL_MAX_RPM = 256;

    /** Gear (ground vehicles): Park / Reverse / Neutral / Drive. Sets the drive direction. */
    public static final int GEAR_PARK = 0;
    public static final int GEAR_REVERSE = 1;
    public static final int GEAR_NEUTRAL = 2;
    public static final int GEAR_DRIVE = 3;
    /** Steering mode: Front-only, All-Wheel (4WS, rear steers opposite), Crab (rear steers the same). */
    public static final int STEER_FRONT = 0;
    public static final int STEER_4WS = 1;
    public static final int STEER_CRAB = 2;
    /** Drive mode: which axles are powered. */
    public static final int DRIVE_RWD = 0;
    public static final int DRIVE_FWD = 1;
    public static final int DRIVE_4X4 = 2;
    /** Differential: Open (wheels free), Rear lock, Full lock. */
    public static final int DIFF_OPEN = 0;
    public static final int DIFF_REAR = 1;
    public static final int DIFF_FULL = 2;

    /** Persistent identity for RPM Governor ownership (the setCommand/heartbeat handshake). */
    @Nullable
    private UUID uuid;
    /** Live axis demands; NOT persisted, so a reloaded craft never moves until a control is touched. */
    private final float[] axisDemand = new float[AXIS_COUNT];
    /** Governors this VCC currently commands, so they can be released on disarm / removal. */
    private final Set<BlockPos> ownedGovernors = new LinkedHashSet<>();
    private int gear = GEAR_DRIVE;
    private int steerMode = STEER_FRONT;
    private int driveMode = DRIVE_4X4;
    private boolean midAxleDrive = true;
    private int diffMode = DIFF_OPEN;
    private boolean steerInvert;
    private boolean handbrake;
    /** [3] */
    private boolean controlsActive;
    /** Live aux toggles (lights etc.); NOT persisted, default off. */
    private final boolean[] aux = new boolean[2];
    /** Active redstone-link output transmitters, keyed by engine row. */
    private final Map<Integer, VccMk2RedstoneTransmitter> redstoneTransmitters = new LinkedHashMap<>();
    /** Active Linked Controller input receivers, keyed by input channel. */
    private final Map<Integer, VccMk2LinkedControllerReceiver> linkedReceivers = new LinkedHashMap<>();
    /** Last 0-15 received per input channel (pushed by the redstone-link network). */
    private final int[] channelStrength = new int[CHANNELS.length];
    private final boolean[] prevToggle = new boolean[CHANNELS.length];
    /** Smoothed (ramped) Linked Controller axis values, -1..1. */
    private final float[] rampedCtl = new float[AXIS_COUNT];
    private int rampTicks = 8;

    // telemetry (synced to GUI/CRT/HUD)
    /** Live readouts, computed server-side, synced to the client (transient, paced). */
    private float telemSpeed;      // km/h ground speed
    private float telemHeading;    // 0..360 deg
    private float telemPitch;      // deg
    private float telemRoll;       // deg
    private float telemAltitude;   // blocks (worldY)
    private int telemRpm;          // average actual governor RPM
    private long lastTelemSync = Long.MIN_VALUE;

    // sensors (Display Link bound)
    /** Sensor slots the VCC can bind, via a Create Display Link aimed from the sensor to this block. */
    public static final int SENSOR_GIMBAL = 0;
    public static final int SENSOR_ALTITUDE = 1;
    public static final int SENSOR_VELOCITY = 2;
    public static final int SENSOR_NAVTABLE = 3;
    public static final int SENSOR_COUNT = 4;
    /** Connection state per sensor, synced to the GUI. */
    public static final int SENSOR_NONE = 0;
    public static final int SENSOR_CONNECTED = 1;
    public static final int SENSOR_LOST = 2;
    /** Player-bound (Display Link) sensor positions; persisted. Null = rely on auto-discovery. */
    private final BlockPos[] sensorManualPos = new BlockPos[SENSOR_COUNT];
    /** Auto-discovered sensor positions (transient, refreshed by the scan helper). */
    private final BlockPos[] sensorAutoPos = new BlockPos[SENSOR_COUNT];
    /** Live connection state per sensor, synced to the client. */
    private final byte[] sensorState = new byte[SENSOR_COUNT];
    /** Server-side scan + hysteresis helper; lazily created on first tick. */
    @Nullable
    private transient VccMk2Sensors sensors;

    // drivetrain + fuel telemetry
    /** Per-wheel commanded / actual drive RPM, synced for the cockpit wheel diagram. */
    private final int[] wheelCmdRpm = new int[CARD_ROWS];
    private final int[] wheelActRpm = new int[CARD_ROWS];
    /** Diesel fuel summed across the contraption's diesel engines (mB), synced for the fuel gauge. */
    private float telemFuelAmount;
    private float telemFuelCapacity;
    private long lastFuelScan = Long.MIN_VALUE;

    /** Player-defined craft forward (nose). Null until first resolved from the block facing. */
    @Nullable
    private Direction noseDir;

    private final ItemStackHandler cards = new ItemStackHandler(CARD_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            return (slot % 2 == 0)
                    ? stack.getItem() instanceof PropellerCardItem
                    : stack.getItem() instanceof GovernorCardItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }
    };

    /** Redstone Link frequency items, 2 per input channel (channelIndex * 2 + which). */
    private final ItemStackHandler frequencies = new ItemStackHandler(CHANNELS.length * 2) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }
    };

    /** Per-engine output frequency for redstone engines: 2 items per row (row * 2 + which). */
    private final ItemStackHandler engineFreq = new ItemStackHandler(CARD_ROWS * 2) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }
    };

    /** Dedicated 2-item frequency for the LIGHTS output (transmits 0/15 driven by the lights switch). */
    private final ItemStackHandler lightsFreq = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markUpdated();
        }
    };

    public VccMk2BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // state
    public VccMk2CraftProfile profile() {
        return profile;
    }

    public boolean profileChosen() {
        return profileChosen;
    }

    public boolean armed() {
        return armed;
    }

    public boolean autoMode() {
        return autoMode;
    }

    public boolean assisted() {
        return assisted;
    }

    public ItemStackHandler cards() {
        return cards;
    }

    public void setProfile(VccMk2CraftProfile newProfile) {
        if (newProfile == null) {
            return;
        }
        this.profile = newProfile;
        this.profileChosen = true;
        markUpdated();
    }

    public void toggleArmed() {
        this.armed = !this.armed;
        if (!armed) {
            this.autoMode = false;
            releaseOutputs();
        }
        syncVisualState();
        markUpdated();
    }

    public void toggleAutoMode() {
        if (!armed) {
            return;
        }
        this.autoMode = !this.autoMode;
        markUpdated();
    }

    public void toggleStabilization() {
        this.assisted = !this.assisted;
        markUpdated();
    }

    /** Server-side: keep the block's CRT VISUAL_STATE in step with armed state. */
    private void syncVisualState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof VccMk2Block)) {
            return;
        }
        VccMk2VisualState want = armed ? VccMk2VisualState.ACTIVE : VccMk2VisualState.STANDBY;
        if (state.getValue(VccMk2Block.VISUAL_STATE) != want) {
            level.setBlock(worldPosition, state.setValue(VccMk2Block.VISUAL_STATE, want), Block.UPDATE_ALL);
        }
    }

    // detection
    public int boundPropellerCount() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(propSlot(r), PropellerCardItem.TAG_KEY) != null) {
                n++;
            }
        }
        return n;
    }

    public int boundGovernorCount() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(govSlot(r), GovernorCardItem.TAG_KEY) != null) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public BlockPos linkedPos(int slot, String tagKey) {
        ItemStack stack = cards.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return null;
        }
        return FlightCardData.get(stack, tagKey).getLinkedPos();
    }

    /** True if this block sits on an assembled contraption / Sable sub-level. */
    public boolean isOnContraption() {
        if (level == null) {
            return false;
        }
        try {
            return SableBridge.subLevelAccessOf(this) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public int boundRowCount() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (!cards.getStackInSlot(propSlot(r)).isEmpty() || !cards.getStackInSlot(govSlot(r)).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    // mixer roles
    public int rowRoleMask(int row) {
        return (row < 0 || row >= CARD_ROWS) ? 0 : roleMasks[row];
    }

    public boolean hasRole(int row, VccMk2Role role) {
        return role != null && role != VccMk2Role.UNASSIGNED && (rowRoleMask(row) & role.bit()) != 0;
    }

    /** Toggle a single role on an engine row. Engines may hold several roles at once. */
    public void toggleRole(int row, VccMk2Role role) {
        if (row < 0 || row >= CARD_ROWS || role == null || role == VccMk2Role.UNASSIGNED) {
            return;
        }
        roleMasks[row] ^= role.bit();
        markUpdated();
    }

    public void clearRoles(int row) {
        if (row >= 0 && row < CARD_ROWS && roleMasks[row] != 0) {
            roleMasks[row] = 0;
            markUpdated();
        }
    }

    /** Count of engine rows (with a bound propeller) that hold the given role. */
    public int roleCount(VccMk2Role role) {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(propSlot(r), PropellerCardItem.TAG_KEY) != null && hasRole(r, role)) {
                n++;
            }
        }
        return n;
    }

    /** Count of bound engines that have at least one role assigned. */
    public int assignedEngineCount() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(propSlot(r), PropellerCardItem.TAG_KEY) != null && roleMasks[r] != 0) {
                n++;
            }
        }
        return n;
    }

    /** True if this engine row has a linked governor (without one it cannot be driven). */
    public boolean hasGovernor(int row) {
        return linkedPos(govSlot(row), GovernorCardItem.TAG_KEY) != null;
    }

    /** Engines that have a propeller + a role but no linked governor (assigned but cannot move). */
    public int enginesMissingGovernor() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(propSlot(r), PropellerCardItem.TAG_KEY) != null && roleMasks[r] != 0 && !hasGovernor(r)) {
                n++;
            }
        }
        return n;
    }

    // engine type (kinetic / redstone)
    public boolean isRedstoneEngine(int row) {
        return row >= 0 && row < CARD_ROWS && redstoneEngine[row];
    }

    /** Toggle an engine row between kinetic (governor) and redstone (frequency) output. */
    public void setRedstoneEngine(Player player, int row, boolean redstone) {
        if (row < 0 || row >= CARD_ROWS || redstoneEngine[row] == redstone) {
            return;
        }
        redstoneEngine[row] = redstone;
        // eject whatever the new type can't use, so nothing gets stuck in a hidden slot
        if (redstone) {
            ItemStack gov = cards.getStackInSlot(govSlot(row));
            if (!gov.isEmpty()) {
                cards.setStackInSlot(govSlot(row), ItemStack.EMPTY);
                giveBack(player, gov);
            }
        } else {
            for (int which = 0; which < 2; which++) {
                ItemStack f = engineFreq.getStackInSlot(row * 2 + which);
                if (!f.isEmpty()) {
                    engineFreq.setStackInSlot(row * 2 + which, ItemStack.EMPTY);
                    giveBack(player, f);
                }
            }
        }
        markUpdated();
    }

    public ItemStackHandler engineFreq() {
        return engineFreq;
    }

    public ItemStack engineFreqStack(int row, int which) {
        int slot = row * 2 + which;
        if (slot < 0 || slot >= engineFreq.getSlots()) {
            return ItemStack.EMPTY;
        }
        return engineFreq.getStackInSlot(slot);
    }

    /** A redstone engine is configured once it has at least one frequency item set. */
    public boolean hasEngineFreq(int row) {
        return !engineFreqStack(row, 0).isEmpty() || !engineFreqStack(row, 1).isEmpty();
    }

    /** True if this engine has a working output driver: a governor (kinetic) or a frequency (redstone). */
    public boolean hasOutput(int row) {
        return isRedstoneEngine(row) ? hasEngineFreq(row) : hasGovernor(row);
    }

    /** Engines that have a propeller + a role but no output driver (assigned but cannot move). */
    public int enginesMissingOutput() {
        int n = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            if (linkedPos(propSlot(r), PropellerCardItem.TAG_KEY) != null && roleMasks[r] != 0 && !hasOutput(r)) {
                n++;
            }
        }
        return n;
    }

    // backend control loop
    /** Lazily-created persistent identity used to own RPM governors. */
    public UUID getOrCreateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
            setChanged();
        }
        return uuid;
    }

    /** Cockpit set-and-hold: store a control-axis demand (clamped to -1..1). */
    public void setAxisDemand(int axis, float value) {
        if (axis < 0 || axis >= AXIS_COUNT) {
            return;
        }
        axisDemand[axis] = Math.max(-1f, Math.min(1f, value));
        controlsActive = true;
        markUpdated();
    }

    public float axisDemand(int axis) {
        return (axis >= 0 && axis < AXIS_COUNT) ? axisDemand[axis] : 0f;
    }

    public boolean handbrake() {
        return handbrake;
    }

    public void setHandbrake(boolean on) {
        if (on != handbrake) {
            handbrake = on;
            markUpdated();
        }
    }

    public int gear() {
        return gear;
    }

    public void setGear(int g) {
        if (g >= GEAR_PARK && g <= GEAR_DRIVE && g != gear) {
            gear = g;
            markUpdated();
        }
    }

    public int steerMode() {
        return steerMode;
    }

    /** Set the steer mode, clamped to what the bound wheels can actually do (4WS/Crab need 2+ axles). */
    public void setSteerMode(int m) {
        int max = steerableAxleCount() >= 2 ? STEER_CRAB : STEER_FRONT;
        int v = Math.max(STEER_FRONT, Math.min(m, max));
        if (v != steerMode) {
            steerMode = v;
            markUpdated();
        }
    }

    /** Number of distinct fore/aft axles among the bound, Steer-tagged wheels. */
    public int steerableAxleCount() {
        java.util.Set<Integer> levels = new java.util.HashSet<>();
        BlockPos o = getBlockPos();
        Direction nose = noseDirection();
        for (int r = 0; r < CARD_ROWS; r++) {
            if ((roleMasks[r] & VccMk2Role.STEER.bit()) == 0) {
                continue;
            }
            BlockPos p = linkedPos(propSlot(r), PropellerCardItem.TAG_KEY);
            if (p != null) {
                levels.add(forwardOffset(p, o, nose));
            }
        }
        return levels.size();
    }

    public int driveMode() {
        return driveMode;
    }

    public void setDriveMode(int m) {
        int v = Math.max(DRIVE_RWD, Math.min(m, DRIVE_4X4));
        if (v != driveMode) {
            driveMode = v;
            markUpdated();
        }
    }

    public boolean midAxleDrive() {
        return midAxleDrive;
    }

    public void setMidAxleDrive(boolean on) {
        if (on != midAxleDrive) {
            midAxleDrive = on;
            markUpdated();
        }
    }

    public int diffMode() {
        return diffMode;
    }

    public void setDiffMode(int m) {
        int v = Math.max(DIFF_OPEN, Math.min(m, DIFF_FULL));
        if (v != diffMode) {
            diffMode = v;
            markUpdated();
        }
    }

    public boolean steerInvert() {
        return steerInvert;
    }

    public void setSteerInvert(boolean on) {
        if (on != steerInvert) {
            steerInvert = on;
            markUpdated();
        }
    }

    /** Rightward (lateral) block offset of a position from the console, in the craft's nose frame. */
    private static int lateralOffset(BlockPos pos, BlockPos origin, Direction nose) {
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        return dx * (-nose.getStepZ()) + dz * nose.getStepX();
    }

    /** [4] */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // FE Dependent Mode gate: an unpowered cockpit releases its actuators (the existing
        // disarmed-safe path) and does nothing else until the buffer is charged again.
        final boolean fePowered = feConsumer.serviceTick(armed);
        com.createstats.power.FePowerFx.handleTransition(level, getBlockPos(), feConsumer);
        if (!fePowered) {
            if (!ownedGovernors.isEmpty()) {
                releaseOutputs();
            }
            return;
        }
        // Sensor discovery runs every tick (cadenced internally) so the Hangar shows live
        // connections whether or not the craft is armed; it only pushes an update on a change.
        if (sensors == null) {
            sensors = new VccMk2Sensors(this);
        }
        sensors.maybeScan(serverLevel.getGameTime());
        updateFuel(serverLevel);
        if (!armed) {
            if (!ownedGovernors.isEmpty()) {
                releaseOutputs();
            }
            return;
        }
        // Live telemetry only when armed (gauges read real values; disarmed shows a calm idle).
        updateTelemetry(serverLevel);
        if (profile == VccMk2CraftProfile.OTOMOBIL) {
            tickOtomobil(serverLevel);
        } else if (!ownedGovernors.isEmpty()) {
            // other profiles aren't wired yet (mode by mode); make sure nothing is left driving
            releaseOutputs();
        }
    }

    private void tickOtomobil(ServerLevel serverLevel) {
        UUID id = getOrCreateUuid();
        java.util.Arrays.fill(wheelCmdRpm, 0);
        java.util.Arrays.fill(wheelActRpm, 0);
        double throttle = effectiveAxis(AXIS_THROTTLE);
        double yaw = effectiveAxis(AXIS_YAW);
        boolean park = gear == GEAR_PARK;
        double brakeAmt = Math.max(Math.max(0.0, effectiveAxis(AXIS_BRAKE)),
                Math.max(handbrake ? 1.0 : 0.0, park ? 1.0 : 0.0));
        int gearSign = gear == GEAR_DRIVE ? 1 : gear == GEAR_REVERSE ? -1 : 0;
        // [5]
        int baseRpm;
        boolean braking = brakeAmt > 0.05;
        if (braking) {
            baseRpm = 0;
        } else {
            baseRpm = (int) Math.round(throttle * gearSign * WHEEL_MAX_RPM);
        }
        long gameTime = serverLevel.getGameTime();
        Direction nose = noseDirection();
        BlockPos o = getBlockPos();

        // Fore/aft extents for steering wheels and for drive wheels (they can differ).
        int steerFront = Integer.MIN_VALUE;
        int steerRear = Integer.MAX_VALUE;
        int driveFront = Integer.MIN_VALUE;
        int driveRear = Integer.MAX_VALUE;
        for (int r = 0; r < CARD_ROWS; r++) {
            BlockPos p = linkedPos(propSlot(r), PropellerCardItem.TAG_KEY);
            if (p == null) {
                continue;
            }
            int f = forwardOffset(p, o, nose);
            if ((roleMasks[r] & VccMk2Role.STEER.bit()) != 0) {
                steerFront = Math.max(steerFront, f);
                steerRear = Math.min(steerRear, f);
            }
            if ((roleMasks[r] & VccMk2Role.DRIVE.bit()) != 0) {
                driveFront = Math.max(driveFront, f);
                driveRear = Math.min(driveRear, f);
            }
        }
        int axles = steerableAxleCount();
        int effSteerMode = axles >= 2 ? steerMode : STEER_FRONT;

        Set<BlockPos> nowOwned = new LinkedHashSet<>();
        for (int row = 0; row < CARD_ROWS; row++) {
            BlockPos partPos = linkedPos(propSlot(row), PropellerCardItem.TAG_KEY);
            if (partPos == null) {
                continue;
            }
            int mask = roleMasks[row];
            if (mask == 0) {
                continue;
            }
            boolean drive = (mask & VccMk2Role.DRIVE.bit()) != 0;
            boolean steer = (mask & VccMk2Role.STEER.bit()) != 0;
            int f = forwardOffset(partPos, o, nose);

            if (drive && !redstoneEngine[row]) {
                BlockPos govPos = linkedPos(govSlot(row), GovernorCardItem.TAG_KEY);
                if (govPos != null) {
                    BlockEntity gbe = SableBridge.getBlockEntityOnContraption(serverLevel, govPos);
                    if (gbe instanceof RpmGovernorBE governor) {
                        int rpm = driveWheelRpm(baseRpm, partPos, f, driveFront, driveRear, yaw, o, nose);
                        wheelCmdRpm[row] = rpm;
                        if (governor.setCommand(getBlockPos(), id, rpm)) {
                            nowOwned.add(govPos);
                            wheelActRpm[row] = governor.getAppliedRpm();
                        }
                    }
                }
            }

            if (steer) {
                int sign;
                if (effSteerMode == STEER_CRAB) {
                    sign = 1;
                } else if (effSteerMode == STEER_4WS) {
                    sign = (f >= steerFront - 1) ? 1 : (f <= steerRear + 1 ? -1 : 0);
                } else {
                    sign = (f >= steerFront - 1) ? 1 : 0;
                }
                double signed = (steerInvert ? -1.0 : 1.0) * yaw * sign;
                int strength = (int) Math.round(Math.min(1.0, Math.abs(signed)) * 15.0);
                int left = signed > 0 ? strength : 0;
                int right = signed < 0 ? strength : 0;
                WheelMountSteeringOverrides.setForTick(partPos, left, right, gameTime);
            }
        }

        // Release any governor we owned last tick but no longer command.
        for (BlockPos g : ownedGovernors) {
            if (!nowOwned.contains(g)) {
                releaseGovernor(serverLevel, g, id);
            }
        }
        ownedGovernors.clear();
        ownedGovernors.addAll(nowOwned);
    }

    /** Resolve a single drive wheel's commanded RPM from drive mode (which axles power) + differential. */
    private int driveWheelRpm(int baseRpm, BlockPos partPos, int f, int driveFront, int driveRear,
                              double yaw, BlockPos o, Direction nose) {
        boolean isFront = f >= driveFront - 1;
        boolean isRear = f <= driveRear + 1;
        boolean isMid = !isFront && !isRear;
        boolean driven = switch (driveMode) {
            case DRIVE_RWD -> isRear;
            case DRIVE_FWD -> isFront;
            default -> !isMid || midAxleDrive; // 4x4: front + rear always, mid only if enabled
        };
        if (!driven) {
            return 0;
        }
        int rpm = baseRpm;
        // Open / unlocked axles let the inside wheel slow during a turn (a soft differential).
        boolean axleLocked = diffMode == DIFF_FULL || (diffMode == DIFF_REAR && isRear);
        if (!axleLocked && rpm != 0 && Math.abs(yaw) > 0.1) {
            int lat = lateralOffset(partPos, o, nose);
            boolean rightSide = lat > 0;
            boolean inner = (yaw > 0 && rightSide) || (yaw < 0 && !rightSide);
            if (inner && lat != 0) {
                rpm = (int) Math.round(rpm * (1.0 - 0.35 * Math.min(1.0, Math.abs(yaw))));
            }
        }
        return rpm;
    }

    private void releaseGovernor(ServerLevel serverLevel, BlockPos govPos, UUID id) {
        BlockEntity gbe = SableBridge.getBlockEntityOnContraption(serverLevel, govPos);
        if (gbe instanceof RpmGovernorBE governor) {
            governor.releaseOwnership(id);
        }
    }

    /** Release every actuator this VCC commands (disarm / removal / disassemble safety). */
    public void releaseOutputs() {
        if (level instanceof ServerLevel serverLevel && uuid != null) {
            for (BlockPos g : ownedGovernors) {
                releaseGovernor(serverLevel, g, uuid);
            }
        }
        ownedGovernors.clear();
        removeAllRedstoneTransmitters();
    }

    // telemetry
    public float telemSpeed() {
        return telemSpeed;
    }

    public float telemHeading() {
        return telemHeading;
    }

    public float telemPitch() {
        return telemPitch;
    }

    public float telemRoll() {
        return telemRoll;
    }

    public float telemAltitude() {
        return telemAltitude;
    }

    public int telemRpm() {
        return telemRpm;
    }

    // drivetrain + fuel API
    public int wheelCmdRpm(int row) {
        return row >= 0 && row < CARD_ROWS ? wheelCmdRpm[row] : 0;
    }

    public int wheelActRpm(int row) {
        return row >= 0 && row < CARD_ROWS ? wheelActRpm[row] : 0;
    }

    public float fuelAmount() {
        return telemFuelAmount;
    }

    public float fuelCapacity() {
        return telemFuelCapacity;
    }

    /** [6] */
    private void updateFuel(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        if (lastFuelScan != Long.MIN_VALUE && now - lastFuelScan < 20) {
            return;
        }
        lastFuelScan = now;
        float amt = 0f;
        float cap = 0f;
        try {
            if (com.createstats.compat.DieselCompat.isAvailable() && level != null) {
                java.util.List<BlockPos> engines = new ArrayList<>();
                int cxc = worldPosition.getX() >> 4;
                int czc = worldPosition.getZ() >> 4;
                int r = 5;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        var chunk = level.getChunkSource().getChunk(cxc + dx, czc + dz, false);
                        if (chunk == null) {
                            continue;
                        }
                        for (BlockPos bp : chunk.getBlockEntitiesPos()) {
                            BlockEntity be = chunk.getBlockEntity(bp);
                            if (com.createstats.compat.DieselCompatHelper.isDieselEngineBlockEntity(be)) {
                                engines.add(bp.immutable());
                            }
                        }
                    }
                }
                Set<BlockPos> covered = new LinkedHashSet<>();
                for (BlockPos ep : engines) {
                    if (covered.contains(ep)) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(ep);
                    if (be == null) {
                        continue;
                    }
                    AABB bounds = com.createstats.compat.DieselCompatHelper.getDieselGroupBounds(be);
                    var readout = com.createstats.compat.DieselCompatHelper.readSource(be, null, true);
                    if (readout != null) {
                        amt += fuelMetric(readout, "fuel_amount");
                        cap += fuelMetric(readout, "fuel_capacity");
                    }
                    if (bounds != null) {
                        for (BlockPos other : engines) {
                            if (bounds.contains(other.getX() + 0.5, other.getY() + 0.5, other.getZ() + 0.5)) {
                                covered.add(other);
                            }
                        }
                    } else {
                        covered.add(ep);
                    }
                }
            }
        } catch (Throwable ignored) {
            // diesel mod missing / scan glitch -> show no fuel
        }
        if (Math.abs(amt - telemFuelAmount) > 1f || Math.abs(cap - telemFuelCapacity) > 1f) {
            telemFuelAmount = amt;
            telemFuelCapacity = cap;
            markUpdated();
        }
    }

    private static float fuelMetric(com.createstats.util.InstrumentSourceHelper.SourceReadout readout, String id) {
        var sample = readout.metrics().get(id);
        return sample != null ? sample.value() : 0f;
    }

    // sensors API
    /** Connection state of a sensor slot: {@link #SENSOR_NONE} / {@link #SENSOR_CONNECTED} / {@link #SENSOR_LOST}. */
    public int sensorState(int which) {
        return which >= 0 && which < SENSOR_COUNT ? sensorState[which] : SENSOR_NONE;
    }

    /** The player-bound (Display Link) position for a sensor slot, or {@code null} if relying on auto-discovery. */
    @Nullable
    public BlockPos sensorManualPos(int which) {
        return which >= 0 && which < SENSOR_COUNT ? sensorManualPos[which] : null;
    }

    /** The position actually feeding a sensor slot: the manual bind if set, else the auto-discovered block. */
    @Nullable
    public BlockPos sensorEffectivePos(int which) {
        if (which < 0 || which >= SENSOR_COUNT) {
            return null;
        }
        return sensorManualPos[which] != null ? sensorManualPos[which] : sensorAutoPos[which];
    }

    /** True if any sensor slot reports a live connection (used to badge telemetry as sensor-backed). */
    public boolean hasSensor(int which) {
        return sensorState(which) == SENSOR_CONNECTED;
    }

    public void setManualGimbalPos(@Nullable BlockPos pos) {
        bindSensor(SENSOR_GIMBAL, pos);
    }

    public void setManualAltitudePos(@Nullable BlockPos pos) {
        bindSensor(SENSOR_ALTITUDE, pos);
    }

    public void setManualVelocityPos(@Nullable BlockPos pos) {
        bindSensor(SENSOR_VELOCITY, pos);
    }

    public void setManualNavTablePos(@Nullable BlockPos pos) {
        bindSensor(SENSOR_NAVTABLE, pos);
    }

    /** Clear the manual binding for a sensor slot, returning it to auto-discovery. */
    public void clearSensor(int which) {
        bindSensor(which, null);
    }

    private void bindSensor(int which, @Nullable BlockPos pos) {
        if (which < 0 || which >= SENSOR_COUNT) {
            return;
        }
        sensorManualPos[which] = pos;
        if (sensors != null && level instanceof ServerLevel serverLevel) {
            sensors.forceScanNow();
            sensors.maybeScan(serverLevel.getGameTime());
        }
        markUpdated();
    }

    /**
     * Called by {@link VccMk2Sensors} after a scan. Writes the resolved state + auto positions back
     * and only pushes a client update when a connection state actually changed (keeps the wire quiet).
     */
    void applySensorScan(byte[] states, BlockPos[] autoPos) {
        boolean changed = false;
        for (int i = 0; i < SENSOR_COUNT; i++) {
            if (sensorState[i] != states[i]) {
                sensorState[i] = states[i];
                changed = true;
            }
            sensorAutoPos[i] = autoPos[i];
        }
        if (changed) {
            markUpdated();
        }
    }

    /** Recompute live telemetry from the contraption + governors; sync to the client at a gentle cadence. */
    private void updateTelemetry(ServerLevel serverLevel) {
        float speed = 0f;
        float heading = telemHeading;
        float pitch = 0f;
        float roll = 0f;
        try {
            org.joml.Vector3d v = SableBridge.linearVelocityWorld(this);
            if (v != null) {
                double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
                // blocks/tick -> km/h: * 20 ticks/s * 3.6 = * 72
                speed = (float) (horiz * 72.0);
                if (horiz > 0.02) {
                    heading = (float) ((Math.toDegrees(Math.atan2(v.x, -v.z)) % 360 + 360) % 360);
                }
            }
            org.joml.Quaterniond q = SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(this));
            if (q != null) {
                org.joml.Vector3d fwd = new org.joml.Vector3d(0, 0, 1);
                q.transform(fwd);
                pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, fwd.y))));
                org.joml.Vector3d up = new org.joml.Vector3d(0, 1, 0);
                q.transform(up);
                roll = (float) Math.toDegrees(Math.atan2(up.x, up.y));
            }
        } catch (Throwable ignored) {
            // not assembled / bridge unavailable -> keep last values at rest
        }
        // average actual governor RPM across owned, kinetic engines
        int rpmSum = 0;
        int rpmN = 0;
        for (BlockPos g : ownedGovernors) {
            BlockEntity gbe = SableBridge.getBlockEntityOnContraption(serverLevel, g);
            if (gbe instanceof RpmGovernorBE gov) {
                rpmSum += Math.abs(gov.getAppliedRpm());
                rpmN++;
            }
        }
        int rpm = rpmN > 0 ? rpmSum / rpmN : 0;
        float alt = (float) worldPosition.getY();

        telemSpeed = speed;
        telemHeading = heading;
        telemPitch = pitch;
        telemRoll = roll;
        telemAltitude = alt;
        telemRpm = rpm;

        // sync to client at ~5 Hz while running
        long now = serverLevel.getGameTime();
        if (lastTelemSync == Long.MIN_VALUE || now - lastTelemSync >= 4) {
            lastTelemSync = now;
            markUpdated();
        }
    }

    // redstone outputs (lights / burner / jet)
    public boolean aux(int i) {
        return i >= 0 && i < aux.length && aux[i];
    }

    public void setAux(int i, boolean on) {
        if (i >= 0 && i < aux.length && aux[i] != on) {
            aux[i] = on;
            markUpdated();
        }
    }

    /** The dedicated LIGHTS output frequency (2 items). The lights switch transmits 0/15 here. */
    public ItemStackHandler lightsFreq() {
        return lightsFreq;
    }

    public ItemStack lightsFreqStack(int which) {
        return (which >= 0 && which < lightsFreq.getSlots()) ? lightsFreq.getStackInSlot(which) : ItemStack.EMPTY;
    }

    public boolean hasLightsFreq() {
        return !lightsFreqStack(0).isEmpty() || !lightsFreqStack(1).isEmpty();
    }

    /** Place one inventory item into a lights frequency slot. */
    public void assignLightsFreq(Player player, int which, int invIndex) {
        if (which < 0 || which >= lightsFreq.getSlots() || !lightsFreq.getStackInSlot(which).isEmpty()) {
            return;
        }
        var items = player.getInventory().items;
        if (invIndex < 0 || invIndex >= items.size()) {
            return;
        }
        ItemStack src = items.get(invIndex);
        if (src.isEmpty()) {
            return;
        }
        ItemStack one = src.copyWithCount(1);
        src.shrink(1);
        lightsFreq.setStackInSlot(which, one);
    }

    public void returnLightsFreq(Player player, int which) {
        if (which < 0 || which >= lightsFreq.getSlots()) {
            return;
        }
        ItemStack cur = lightsFreq.getStackInSlot(which);
        if (cur.isEmpty()) {
            return;
        }
        lightsFreq.setStackInSlot(which, ItemStack.EMPTY);
        giveBack(player, cur);
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> lightsNetworkKey() {
        ItemStack a = lightsFreqStack(0);
        ItemStack b = lightsFreqStack(1);
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            a = b;
            b = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(a),
                RedstoneLinkNetworkHandler.Frequency.of(b));
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> engineRedstoneKey(int row) {
        if (!isRedstoneEngine(row)) {
            return null;
        }
        ItemStack a = engineFreqStack(row, 0);
        ItemStack b = engineFreqStack(row, 1);
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            a = b;
            b = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(a),
                RedstoneLinkNetworkHandler.Frequency.of(b));
    }

    /** Register / refresh / drop redstone transmitters so each configured redstone engine has one. */
    private void refreshRedstoneTransmitters(ServerLevel serverLevel) {
        Set<Integer> keep = new LinkedHashSet<>();
        // dedicated LIGHTS output uses row index -1
        registerTransmitter(serverLevel, -1, lightsNetworkKey(), keep);
        for (int row = 0; row < CARD_ROWS; row++) {
            registerTransmitter(serverLevel, row, engineRedstoneKey(row), keep);
        }
        List<Integer> stale = new ArrayList<>();
        for (Integer row : redstoneTransmitters.keySet()) {
            if (!keep.contains(row)) {
                stale.add(row);
            }
        }
        for (Integer row : stale) {
            removeRedstoneTransmitter(row);
        }
    }

    private void registerTransmitter(ServerLevel serverLevel, int row,
                                     @Nullable Couple<RedstoneLinkNetworkHandler.Frequency> key, Set<Integer> keep) {
        if (key == null) {
            return;
        }
        keep.add(row);
        VccMk2RedstoneTransmitter existing = redstoneTransmitters.get(row);
        if (existing != null && existing.matches(key)) {
            return;
        }
        if (existing != null) {
            removeRedstoneTransmitter(row);
        }
        VccMk2RedstoneTransmitter tx = new VccMk2RedstoneTransmitter(row, key);
        redstoneTransmitters.put(row, tx);
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, tx);
        } catch (RuntimeException | LinkageError ex) {
            redstoneTransmitters.remove(row);
        }
    }

    /** Drive each redstone transmitter's 0-15 strength from its engine's role demand (or aux toggle). */
    private void applyRedstoneOutputs(ServerLevel serverLevel) {
        for (VccMk2RedstoneTransmitter tx : new ArrayList<>(redstoneTransmitters.values())) {
            if (!tx.setTransmittedStrength(redstoneDemandStrength(tx.row()))) {
                continue;
            }
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(serverLevel, tx);
            } catch (RuntimeException | LinkageError ignored) {
                // network glitch; next tick retries
            }
        }
    }

    private int redstoneDemandStrength(int row) {
        if (row < 0) {
            // dedicated LIGHTS output, driven by the lights switch (aux0)
            return aux[0] ? 15 : 0;
        }
        int mask = roleMasks[row];
        double d;
        if (mask == 0) {
            // no role: a plain aux output (lights), toggled by the first aux switch
            d = aux[0] ? 1.0 : 0.0;
        } else {
            d = 0.0;
            if ((mask & VccMk2Role.LIFT.bit()) != 0) {
                d = Math.max(d, Math.abs(effectiveAxis(AXIS_VERTICAL)));
            }
            if ((mask & (VccMk2Role.THRUST.bit() | VccMk2Role.DRIVE.bit())) != 0) {
                d = Math.max(d, Math.abs(effectiveAxis(AXIS_THROTTLE)));
            }
            if ((mask & (VccMk2Role.YAW.bit() | VccMk2Role.STEER.bit())) != 0) {
                d = Math.max(d, Math.abs(effectiveAxis(AXIS_YAW)));
            }
            if ((mask & VccMk2Role.PITCH.bit()) != 0) {
                d = Math.max(d, Math.abs(effectiveAxis(AXIS_PITCH)));
            }
            if ((mask & VccMk2Role.ROLL.bit()) != 0) {
                d = Math.max(d, Math.abs(effectiveAxis(AXIS_ROLL)));
            }
        }
        return (int) Math.round(Math.min(1.0, Math.max(0.0, d)) * 15.0);
    }

    private void removeRedstoneTransmitter(int row) {
        VccMk2RedstoneTransmitter tx = redstoneTransmitters.remove(row);
        if (tx == null) {
            return;
        }
        tx.markDead();
        if (level == null || level.isClientSide) {
            return;
        }
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, tx);
        } catch (RuntimeException | LinkageError ignored) {
            // already gone
        }
    }

    private void removeAllRedstoneTransmitters() {
        for (Integer row : new ArrayList<>(redstoneTransmitters.keySet())) {
            removeRedstoneTransmitter(row);
        }
    }

    /** Self-contained redstone-link transmitter for one redstone engine. Never shares FCC code. */
    private final class VccMk2RedstoneTransmitter implements IRedstoneLinkable {
        private final int row;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private int strength;
        private boolean alive = true;

        VccMk2RedstoneTransmitter(int row, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.row = row;
            this.key = key;
        }

        int row() {
            return row;
        }

        boolean matches(Couple<RedstoneLinkNetworkHandler.Frequency> other) {
            return key.equals(other);
        }

        boolean setTransmittedStrength(int s) {
            int clamped = Math.max(0, Math.min(15, s));
            if (strength == clamped) {
                return false;
            }
            strength = clamped;
            return true;
        }

        void markDead() {
            alive = false;
        }

        @Override
        public int getTransmittedStrength() {
            return strength;
        }

        @Override
        public void setReceivedStrength(int s) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return alive && !isRemoved() && level != null && !level.isClientSide
                    && (row < 0 ? hasLightsFreq() : isRedstoneEngine(row));
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return getBlockPos();
        }
    }

    // nose / forward
    /** Craft forward (nose) direction; defaults to the console's facing until the player sets it. */
    public Direction noseDirection() {
        if (noseDir != null) {
            return noseDir;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof VccMk2Block) {
            return state.getValue(VccMk2Block.FACING);
        }
        return Direction.NORTH;
    }

    /** Rotate the nose 90 degrees clockwise (N -> E -> S -> W). */
    public void rotateNose() {
        noseDir = noseDirection().getClockWise();
        markUpdated();
    }

    /** [7] */
    public void autoAssignRoles() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean ground = profile.isGround();
        for (int row = 0; row < CARD_ROWS; row++) {
            BlockPos pos = linkedPos(propSlot(row), PropellerCardItem.TAG_KEY);
            if (pos == null) {
                roleMasks[row] = 0;
                continue;
            }
            roleMasks[row] = ground ? VccMk2Role.DRIVE.bit() : maskFor(detectAircraftRole(pos));
        }
        if (ground) {
            assignFrontAxleSteer();
        }
        markUpdated();
    }

    private static int maskFor(VccMk2Role role) {
        return (role == null || role == VccMk2Role.UNASSIGNED) ? 0 : role.bit();
    }

    private VccMk2Role detectAircraftRole(BlockPos propPos) {
        try {
            BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, propPos);
            SableBridge.PropData pd = SableBridge.readProp(propBe);
            if (pd != null && pd.thrustDirLocal() != null) {
                Vector3d d = pd.thrustDirLocal();
                double ax = Math.abs(d.x);
                double ay = Math.abs(d.y);
                double az = Math.abs(d.z);
                return (ay >= ax && ay >= az) ? VccMk2Role.LIFT : VccMk2Role.THRUST;
            }
        } catch (Throwable ignored) {
            // not assembled / not a readable propeller -> fall through to profile default
        }
        return switch (profile) {
            case MULTIROTOR -> VccMk2Role.LIFT;
            case PLANE, BOAT, VTOL -> VccMk2Role.THRUST;
            default -> VccMk2Role.UNASSIGNED;
        };
    }

    /** Add Steer to the front-most wheels (furthest along the nose direction); the rest stay Drive. */
    private void assignFrontAxleSteer() {
        BlockPos o = getBlockPos();
        Direction nose = noseDirection();
        int maxFwd = Integer.MIN_VALUE;
        for (int row = 0; row < CARD_ROWS; row++) {
            BlockPos pos = linkedPos(propSlot(row), PropellerCardItem.TAG_KEY);
            if (pos != null) {
                maxFwd = Math.max(maxFwd, forwardOffset(pos, o, nose));
            }
        }
        if (maxFwd == Integer.MIN_VALUE) {
            return;
        }
        for (int row = 0; row < CARD_ROWS; row++) {
            BlockPos pos = linkedPos(propSlot(row), PropellerCardItem.TAG_KEY);
            if (pos != null && forwardOffset(pos, o, nose) >= maxFwd - 1) {
                roleMasks[row] |= VccMk2Role.STEER.bit();
            }
        }
    }

    private static int forwardOffset(BlockPos pos, BlockPos origin, Direction nose) {
        return (pos.getX() - origin.getX()) * nose.getStepX() + (pos.getZ() - origin.getZ()) * nose.getStepZ();
    }

    // card moves
    /** Click a card cell: empty -> pull a matching card from the player's inventory; filled -> return it. */
    public void toggleCardSlot(Player player, int slot) {
        if (slot < 0 || slot >= CARD_SLOTS) {
            return;
        }
        ItemStack inSlot = cards.getStackInSlot(slot);
        if (!inSlot.isEmpty()) {
            cards.setStackInSlot(slot, ItemStack.EMPTY);
            if (slot % 2 == 0) {
                roleMasks[slot / 2] = 0;
            }
            giveBack(player, inSlot);
        } else {
            ItemStack pulled = pullCard(player, slot % 2 == 0);
            if (!pulled.isEmpty()) {
                cards.setStackInSlot(slot, pulled);
            }
        }
    }

    /** Manual place: put one specific inventory card into a chosen slot (player picks position + pairing). */
    public void placeCardInSlot(Player player, int slot, int invIndex) {
        if (slot < 0 || slot >= CARD_SLOTS || !cards.getStackInSlot(slot).isEmpty()) {
            return;
        }
        var items = player.getInventory().items;
        if (invIndex < 0 || invIndex >= items.size()) {
            return;
        }
        ItemStack src = items.get(invIndex);
        if (src.isEmpty()) {
            return;
        }
        boolean match = slot % 2 == 0
                ? src.getItem() instanceof PropellerCardItem
                : src.getItem() instanceof GovernorCardItem;
        if (!match) {
            return;
        }
        ItemStack one = src.copyWithCount(1);
        src.shrink(1);
        cards.setStackInSlot(slot, one);
    }

    /** Fill every empty cell with a matching card pulled from the player's inventory. */
    public void bindAllCards(Player player) {
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            // skip the governor slot of redstone rows: those drive by frequency, not a governor
            if (slot % 2 == 1 && redstoneEngine[slot / 2]) {
                continue;
            }
            if (cards.getStackInSlot(slot).isEmpty()) {
                ItemStack pulled = pullCard(player, slot % 2 == 0);
                if (!pulled.isEmpty()) {
                    cards.setStackInSlot(slot, pulled);
                }
            }
        }
    }

    /** Return every card in the rack to the player's inventory. */
    public void clearAllCards(Player player) {
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            ItemStack s = cards.getStackInSlot(slot);
            if (!s.isEmpty()) {
                cards.setStackInSlot(slot, ItemStack.EMPTY);
                giveBack(player, s);
            }
        }
        for (int slot = 0; slot < engineFreq.getSlots(); slot++) {
            ItemStack s = engineFreq.getStackInSlot(slot);
            if (!s.isEmpty()) {
                engineFreq.setStackInSlot(slot, ItemStack.EMPTY);
                giveBack(player, s);
            }
        }
        for (int row = 0; row < CARD_ROWS; row++) {
            roleMasks[row] = 0;
            redstoneEngine[row] = false;
        }
    }

    private ItemStack pullCard(Player player, boolean propeller) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean match = propeller ? s.getItem() instanceof PropellerCardItem : s.getItem() instanceof GovernorCardItem;
            if (match) {
                ItemStack one = s.copyWithCount(1);
                s.shrink(1);
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    private void giveBack(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // input channels (frequencies)
    public ItemStackHandler frequencies() {
        return frequencies;
    }

    public ItemStack freqStack(int channelIndex, int which) {
        int slot = channelIndex * 2 + which;
        if (slot < 0 || slot >= frequencies.getSlots()) {
            return ItemStack.EMPTY;
        }
        return frequencies.getStackInSlot(slot);
    }

    /** A channel is paired once both frequency items are set. */
    public boolean channelPaired(int channelIndex) {
        return !freqStack(channelIndex, 0).isEmpty() && !freqStack(channelIndex, 1).isEmpty();
    }

    /** Place one of an inventory item into a frequency slot (defines the channel frequency). */
    public void assignFreq(Player player, int slot, int invIndex) {
        if (slot < 0 || slot >= frequencies.getSlots() || !frequencies.getStackInSlot(slot).isEmpty()) {
            return;
        }
        var items = player.getInventory().items;
        if (invIndex < 0 || invIndex >= items.size()) {
            return;
        }
        ItemStack src = items.get(invIndex);
        if (src.isEmpty()) {
            return;
        }
        ItemStack one = src.copyWithCount(1);
        src.shrink(1);
        frequencies.setStackInSlot(slot, one);
    }

    /** Return a frequency item to the player. */
    public void returnFreq(Player player, int slot) {
        if (slot < 0 || slot >= frequencies.getSlots()) {
            return;
        }
        ItemStack cur = frequencies.getStackInSlot(slot);
        if (cur.isEmpty()) {
            return;
        }
        frequencies.setStackInSlot(slot, ItemStack.EMPTY);
        giveBack(player, cur);
    }

    // linked Controller (real-time input)
    private int ch(int index) {
        return (index >= 0 && index < channelStrength.length) ? channelStrength[index] : 0;
    }

    /** Effective axis demand: the Linked Controller overrides the held cockpit value while it is driving. */
    private float effectiveAxis(int axis) {
        boolean engaged = controllerButtonForAxis(axis) || Math.abs(rampedCtl[axis]) > 0.02f;
        if (engaged) {
            return rampedCtl[axis];
        }
        // Held cockpit demand only applies once activated this session (re-touch gate after load).
        return controlsActive ? axisDemand[axis] : 0f;
    }

    private boolean controllerButtonForAxis(int axis) {
        boolean ground = profile.isGround();
        return switch (axis) {
            case AXIS_THROTTLE -> ground ? ch(0) > 0 : ch(0) > 0 || ch(1) > 0;
            case AXIS_BRAKE -> ground ? ch(1) > 0 : ch(10) > 0;
            case AXIS_YAW -> ch(2) > 0 || ch(3) > 0;
            case AXIS_VERTICAL -> ch(4) > 0 || ch(5) > 0;
            case AXIS_PITCH -> ch(6) > 0 || ch(7) > 0;
            case AXIS_ROLL -> ch(8) > 0 || ch(9) > 0;
            default -> false;
        };
    }

    private void updateControllerRamps() {
        float step = 1f / Math.max(1, rampTicks);
        boolean ground = profile.isGround();
        if (ground) {
            rampToward(AXIS_THROTTLE, ch(0) / 15f, step);       // W = forward pedal (gear sets direction)
            rampToward(AXIS_BRAKE, ch(1) / 15f, step);          // S = brake
            rampToward(AXIS_YAW, (ch(3) - ch(2)) / 15f, step);  // D - A
        } else {
            rampToward(AXIS_THROTTLE, (ch(0) - ch(1)) / 15f, step);
            rampToward(AXIS_YAW, (ch(3) - ch(2)) / 15f, step);
            rampToward(AXIS_VERTICAL, (ch(4) - ch(5)) / 15f, step);
            rampToward(AXIS_PITCH, (ch(6) - ch(7)) / 15f, step);
            rampToward(AXIS_ROLL, (ch(9) - ch(8)) / 15f, step);
            rampToward(AXIS_BRAKE, ch(10) / 15f, step);
        }
    }

    private void rampToward(int axis, float target, float step) {
        float v = rampedCtl[axis];
        if (v < target) {
            v = Math.min(target, v + step);
        } else if (v > target) {
            v = Math.max(target, v - step);
        }
        rampedCtl[axis] = Math.max(-1f, Math.min(1f, v));
    }

    /** Rising-edge toggle / momentary channels (arm, mode, stabilization, aux). */
    private void processControllerToggles() {
        if (risingEdge(11)) {
            toggleArmed();
        }
        if (risingEdge(12) && armed) {
            toggleAutoMode();
        }
        if (risingEdge(13)) {
            toggleStabilization();
        }
        if (risingEdge(14)) {
            setAux(0, !aux(0));
        }
        if (risingEdge(15)) {
            setAux(1, !aux(1));
        }
    }

    private boolean risingEdge(int channel) {
        boolean now = ch(channel) > 0;
        boolean prev = prevToggle[channel];
        prevToggle[channel] = now;
        return now && !prev;
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> channelNetworkKey(int channel) {
        ItemStack a = freqStack(channel, 0);
        ItemStack b = freqStack(channel, 1);
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            a = b;
            b = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(a),
                RedstoneLinkNetworkHandler.Frequency.of(b));
    }

    private void refreshLinkedControllerReceivers(ServerLevel serverLevel) {
        Set<Integer> keep = new LinkedHashSet<>();
        for (int channel = 0; channel < CHANNELS.length; channel++) {
            Couple<RedstoneLinkNetworkHandler.Frequency> key = channelNetworkKey(channel);
            if (key == null) {
                continue;
            }
            keep.add(channel);
            VccMk2LinkedControllerReceiver existing = linkedReceivers.get(channel);
            if (existing != null && existing.matches(key)) {
                continue;
            }
            if (existing != null) {
                removeLinkedControllerReceiver(channel);
            }
            VccMk2LinkedControllerReceiver rx = new VccMk2LinkedControllerReceiver(channel, key);
            linkedReceivers.put(channel, rx);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, rx);
            } catch (RuntimeException | LinkageError ex) {
                linkedReceivers.remove(channel);
            }
        }
        List<Integer> stale = new ArrayList<>();
        for (Integer channel : linkedReceivers.keySet()) {
            if (!keep.contains(channel)) {
                stale.add(channel);
            }
        }
        for (Integer channel : stale) {
            removeLinkedControllerReceiver(channel);
        }
    }

    private void removeLinkedControllerReceiver(int channel) {
        VccMk2LinkedControllerReceiver rx = linkedReceivers.remove(channel);
        if (channel >= 0 && channel < channelStrength.length) {
            channelStrength[channel] = 0;
        }
        if (rx == null) {
            return;
        }
        rx.markDead();
        if (level == null || level.isClientSide) {
            return;
        }
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, rx);
        } catch (RuntimeException | LinkageError ignored) {
            // already gone
        }
    }

    private void removeAllLinkedControllerReceivers() {
        for (Integer channel : new ArrayList<>(linkedReceivers.keySet())) {
            removeLinkedControllerReceiver(channel);
        }
        for (int i = 0; i < channelStrength.length; i++) {
            channelStrength[i] = 0;
        }
    }

    /** Self-contained Linked Controller input receiver for one channel. Never shares FCC code. */
    private final class VccMk2LinkedControllerReceiver implements IRedstoneLinkable {
        private final int channel;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean alive = true;

        VccMk2LinkedControllerReceiver(int channel, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.channel = channel;
            this.key = key;
        }

        boolean matches(Couple<RedstoneLinkNetworkHandler.Frequency> other) {
            return key.equals(other);
        }

        void markDead() {
            alive = false;
        }

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int strength) {
            if (channel >= 0 && channel < channelStrength.length) {
                channelStrength[channel] = Math.max(0, Math.min(15, strength));
            }
        }

        @Override
        public boolean isListening() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return alive && !isRemoved() && level != null && !level.isClientSide;
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return getBlockPos();
        }
    }

    // persistence
    @Override
    public void setRemoved() {
        removeAllRedstoneTransmitters();
        removeAllLinkedControllerReceivers();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Profile", profile.getSerializedName());
        tag.putBoolean("ProfileChosen", profileChosen);
        tag.putBoolean("Armed", armed);
        tag.putBoolean("AutoMode", autoMode);
        tag.putBoolean("Assisted", assisted);
        tag.putIntArray("RoleMasks", roleMasks.clone());
        long redMask = 0;
        for (int i = 0; i < CARD_ROWS; i++) {
            if (redstoneEngine[i]) {
                redMask |= 1L << i;
            }
        }
        tag.putLong("EngineRedstone", redMask);
        tag.putInt("Gear", gear);
        tag.putInt("SteerMode", steerMode);
        tag.putInt("DriveMode", driveMode);
        tag.putBoolean("MidAxle", midAxleDrive);
        tag.putInt("DiffMode", diffMode);
        tag.putBoolean("SteerInvert", steerInvert);
        tag.putBoolean("Handbrake", handbrake);
        float[] saveAxis = axisDemand.clone();
        tag.putFloat("AxThr", saveAxis[AXIS_THROTTLE]);
        tag.putFloat("AxYaw", saveAxis[AXIS_YAW]);
        tag.putFloat("AxVert", saveAxis[AXIS_VERTICAL]);
        tag.putFloat("AxBrk", saveAxis[AXIS_BRAKE]);
        if (uuid != null) {
            tag.putUUID("Uuid", uuid);
        }
        if (noseDir != null) {
            tag.putByte("Nose", (byte) noseDir.get2DDataValue());
        }
        tag.put("Cards", cards.serializeNBT(registries));
        tag.put("Freq", frequencies.serializeNBT(registries));
        tag.put("EngineFreq", engineFreq.serializeNBT(registries));
        tag.put("LightsFreq", lightsFreq.serializeNBT(registries));
        // transient telemetry (synced so the client gauges read real values; harmless if saved to disk)
        tag.putFloat("TSpd", telemSpeed);
        tag.putFloat("THdg", telemHeading);
        tag.putFloat("TPit", telemPitch);
        tag.putFloat("TRol", telemRoll);
        tag.putFloat("TAlt", telemAltitude);
        tag.putInt("TRpm", telemRpm);
        // sensors: manual binds persist; state + auto pos sync for the GUI (auto is re-derived on load)
        for (int i = 0; i < SENSOR_COUNT; i++) {
            if (sensorManualPos[i] != null) {
                tag.putLong("SenM" + i, sensorManualPos[i].asLong());
            }
            if (sensorAutoPos[i] != null) {
                tag.putLong("SenA" + i, sensorAutoPos[i].asLong());
            }
        }
        tag.putByteArray("SenState", sensorState.clone());
        tag.putFloat("FuelA", telemFuelAmount);
        tag.putFloat("FuelC", telemFuelCapacity);
        tag.putIntArray("WheelCmd", wheelCmdRpm.clone());
        tag.putIntArray("WheelAct", wheelActRpm.clone());
        feConsumer.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feConsumer.load(tag);
        this.profile = profileFromName(tag.getString("Profile"));
        this.profileChosen = tag.getBoolean("ProfileChosen");
        this.armed = tag.getBoolean("Armed");
        this.autoMode = tag.getBoolean("AutoMode");
        this.assisted = !tag.contains("Assisted") || tag.getBoolean("Assisted");
        if (tag.contains("RoleMasks")) {
            int[] saved = tag.getIntArray("RoleMasks");
            for (int i = 0; i < CARD_ROWS; i++) {
                roleMasks[i] = i < saved.length ? saved[i] : 0;
            }
        }
        long redMask = tag.getLong("EngineRedstone");
        for (int i = 0; i < CARD_ROWS; i++) {
            redstoneEngine[i] = (redMask & (1L << i)) != 0;
        }
        this.gear = tag.contains("Gear") ? tag.getInt("Gear") : GEAR_DRIVE;
        this.steerMode = tag.getInt("SteerMode");
        this.driveMode = tag.contains("DriveMode") ? tag.getInt("DriveMode") : DRIVE_4X4;
        this.midAxleDrive = !tag.contains("MidAxle") || tag.getBoolean("MidAxle");
        this.diffMode = tag.getInt("DiffMode");
        this.steerInvert = tag.getBoolean("SteerInvert");
        this.handbrake = tag.getBoolean("Handbrake");
        axisDemand[AXIS_THROTTLE] = tag.getFloat("AxThr");
        axisDemand[AXIS_YAW] = tag.getFloat("AxYaw");
        axisDemand[AXIS_VERTICAL] = tag.getFloat("AxVert");
        axisDemand[AXIS_BRAKE] = tag.getFloat("AxBrk");
        // Held controls stay inert after a world load unless the config opts in (no surprise movement).
        this.controlsActive = com.createstats.ModConfiguration.COMMON.vccPersistControlsOnLoad.get();
        this.uuid = tag.hasUUID("Uuid") ? tag.getUUID("Uuid") : null;
        this.noseDir = tag.contains("Nose") ? Direction.from2DDataValue(tag.getByte("Nose")) : null;
        if (tag.contains("Cards")) {
            cards.deserializeNBT(registries, tag.getCompound("Cards"));
        }
        if (tag.contains("Freq")) {
            frequencies.deserializeNBT(registries, tag.getCompound("Freq"));
        }
        if (tag.contains("EngineFreq")) {
            engineFreq.deserializeNBT(registries, tag.getCompound("EngineFreq"));
        }
        if (tag.contains("LightsFreq")) {
            lightsFreq.deserializeNBT(registries, tag.getCompound("LightsFreq"));
        }
        this.telemSpeed = tag.getFloat("TSpd");
        this.telemHeading = tag.getFloat("THdg");
        this.telemPitch = tag.getFloat("TPit");
        this.telemRoll = tag.getFloat("TRol");
        this.telemAltitude = tag.getFloat("TAlt");
        this.telemRpm = tag.getInt("TRpm");
        for (int i = 0; i < SENSOR_COUNT; i++) {
            sensorManualPos[i] = tag.contains("SenM" + i) ? BlockPos.of(tag.getLong("SenM" + i)) : null;
            sensorAutoPos[i] = tag.contains("SenA" + i) ? BlockPos.of(tag.getLong("SenA" + i)) : null;
        }
        if (tag.contains("SenState")) {
            byte[] st = tag.getByteArray("SenState");
            for (int i = 0; i < SENSOR_COUNT; i++) {
                sensorState[i] = i < st.length ? st[i] : 0;
            }
        }
        this.telemFuelAmount = tag.getFloat("FuelA");
        this.telemFuelCapacity = tag.getFloat("FuelC");
        if (tag.contains("WheelCmd")) {
            int[] a = tag.getIntArray("WheelCmd");
            for (int i = 0; i < CARD_ROWS; i++) {
                wheelCmdRpm[i] = i < a.length ? a[i] : 0;
            }
        }
        if (tag.contains("WheelAct")) {
            int[] a = tag.getIntArray("WheelAct");
            for (int i = 0; i < CARD_ROWS; i++) {
                wheelActRpm[i] = i < a.length ? a[i] : 0;
            }
        }
    }

    private static VccMk2CraftProfile profileFromName(String name) {
        for (VccMk2CraftProfile p : VccMk2CraftProfile.values()) {
            if (p.getSerializedName().equals(name)) {
                return p;
            }
        }
        return VccMk2CraftProfile.AIRSHIP;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < cards.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    cards.getStackInSlot(i));
        }
        for (int i = 0; i < frequencies.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    frequencies.getStackInSlot(i));
        }
    }

    // menu / render
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_stats.vcc_mk2");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VccMk2Menu(containerId, inventory, this);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.25);
    }
}
