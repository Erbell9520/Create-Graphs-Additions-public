package com.createstats.flight.blockentity;

import com.createstats.CreateStats;
import com.createstats.DebugLogging;
import com.createstats.flight.FccBinding;
import com.createstats.flight.FlightConfig;
import com.createstats.flight.FlightMode;
import com.createstats.flight.NoseAxis;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.block.FlightControlComputerBlock;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.item.FlightCardData;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.flight.network.FccGuiControlEntry;
import com.createstats.flight.network.FccGuiDeviceEntry;
import com.createstats.flight.network.FccGuiGroupEntry;
import com.createstats.flight.network.FccGuiRouteEntry;
import com.createstats.flight.network.FccOverrideCommandPacket;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import com.createstats.flight.runtime.FccHudSubscriptionState;
import com.createstats.flight.workbench.FccCompositeControl;
import com.createstats.flight.workbench.FccControlRole;
import com.createstats.flight.workbench.FccControlRoute;
import com.createstats.flight.workbench.FccCurveType;
import com.createstats.flight.workbench.FccDevice;
import com.createstats.flight.workbench.FccDeviceCategory;
import com.createstats.flight.workbench.FccDiagramProfile;
import com.createstats.flight.workbench.FccGroup;
import com.createstats.flight.workbench.FccOutputType;
import com.createstats.flight.workbench.FccRouteKind;
import com.createstats.flight.workbench.FccWidgetType;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.model.PlannerJobStage;
import com.createstats.pathfinder.model.RouteGuidance;
import com.createstats.pathfinder.util.PathfinderNbt;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class FlightControlComputerBE extends BlockEntity implements MenuProvider, IHaveGoggleInformation, com.createstats.power.IFeConsumer {
    public static final int CURRENT_NBT_VERSION = 13;
    private static final int DIAGRAM_METADATA_VERSION = 1;

    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_FCC));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    public static final int ROW_COUNT = FlightConfig.MAX_PROPELLER_BINDINGS;
    public static final int SLOT_COUNT = ROW_COUNT * 2;
    public static final int MIN_VISIBLE_CARD_ROWS = 5;
    public static final int LINKED_CONTROLLER_FLIGHT_BUTTON_COUNT = 6;
    public static final int LINKED_CONTROLLER_BUTTON_COUNT = 8;
    public static final int LINKED_CONTROLLER_SLOT_COUNT = LINKED_CONTROLLER_BUTTON_COUNT * 2;
    public static final int LINKED_CONTROLLER_MIN_INTERVAL_TICKS = 1;
    public static final int LINKED_CONTROLLER_MAX_INTERVAL_TICKS = 60;

    public static int getPropellerSlotIndex(int row) { return row * 2; }
    public static int getGovernorSlotIndex(int row)  { return row * 2 + 1; }
    public static int getLinkedControllerSlotIndex(int button, boolean secondFrequency) {
        return button * 2 + (secondFrequency ? 1 : 0);
    }

    @Nullable private UUID fccUuid;

    private final ItemStackHandler inventory;
    private final ItemStackHandler linkedControllerInventory;

    private final List<FccBinding> bindings = new ArrayList<>();
    private final List<FccDevice> devices = new ArrayList<>();
    private final List<FccGroup> groups = new ArrayList<>();
    private final List<FccCompositeControl> controls = new ArrayList<>();
    private final Map<UUID, Double> controlValues = new LinkedHashMap<>();
    private FccDiagramProfile diagramProfile = FccDiagramProfile.BALLOON_ZEPPELIN;
    private long lastWorkbenchRuntimeLogTick = Long.MIN_VALUE;
    private long lastRuntimeGateLogTick = Long.MIN_VALUE;
    private long lastWheelProbeDebugTick = Long.MIN_VALUE;
    private String lastVehicleRuntimeDebug = "vehicle runtime idle";
    private String lastVehicleAutoDebug = "auto=off";
    private double lastVehicleAutoTargetDistance = Double.NaN;
    private double lastVehicleAutoSteeringInput = 0.0;
    private long lastVehicleAutoDebugLogTick = Long.MIN_VALUE;

    private static final long VEHICLE_AUTO_DEBUG_LOG_TICKS = 5L;
    private long vehicleAutoStuckSinceTick = Long.MIN_VALUE;
    private long vehicleAutoReverseUnstickUntilTick = Long.MIN_VALUE;
    private long vehicleAutoStraightEscapeUntilTick = Long.MIN_VALUE;
    private String lastAirshipAutoDebug = "airship=off";
    private double lastAirshipAutoTargetDistance = Double.NaN;
    private double lastAirshipAutoSteeringInput = 0.0;
    private double lastAirshipAutoHeadingDeg = Double.NaN;
    private double lastAirshipAutoHeadingErrorDeg = Double.NaN;
    private long lastAirshipAutoHeadingTick = Long.MIN_VALUE;
    private double airshipAutoOverturnScore = 0.0;
    private int airshipAutoSteeringPolarity = 1;
    private long airshipAutoWrongWayTicks = 0L;
    private double airshipAutoSpeedIntegral = 0.0;
    private double airshipAutoApproachWindowBlocks = 90.0;
    private double lastAirshipAutoApproachWindowBlocks = 90.0;
    private long airshipAutoStuckSinceTick = Long.MIN_VALUE;
    private long airshipAutoReverseUntilTick = Long.MIN_VALUE;
    private long airshipAutoReacquireUntilTick = Long.MIN_VALUE;
    private int airshipAutoAvoidSide = 1;
    private boolean airshipAutoFlipWasActive;
    private long airshipAutoPostFlipHoldUntilTick = Long.MIN_VALUE;
    private long airshipAutoPostFlipRampStartTick = Long.MIN_VALUE;
    private long airshipAutoPostFlipRampUntilTick = Long.MIN_VALUE;
    private double airshipAutoPreDangerThrust = 0.0;
    private double airshipAutoPreDangerYaw = 0.0;
    private double airshipAutoPreDangerSpeed = Double.NaN;
    private double airshipAutoPreDangerTargetSpeed = Double.NaN;
    private double airshipAutoPreDangerTiltDeg = Double.NaN;
    private long airshipAutoPreDangerTick = Long.MIN_VALUE;
    private double airshipAutoIncidentThrustLimit = AIRSHIP_AUTO_RECOVERY_MIN_THRUST_LIMIT;
    private double airshipAutoIncidentYawLimit = AIRSHIP_AUTO_RECOVERY_MIN_YAW_LIMIT;
    private double lastAirshipAutoRecoveryTiltDeg = Double.NaN;
    private double airshipAutoReachableSpeedCap = Double.NaN;
    private double lastAirshipAutoRequestedSpeedCap = Double.NaN;
    private double lastAirshipAutoEffectiveSpeedCap = Double.NaN;
    private String lastAirshipAutoReachabilityReason = "measuring";
    private long airshipAutoReachableCalmTicks = 0L;
    private String lastAutoAssistantTargetKey = "";
    private String lastAutoAssistantPhase = "";
    private int lastAutoAssistantDistanceBucket = Integer.MAX_VALUE;
    private int lastAutoAssistantMilestone = Integer.MAX_VALUE;
    private long lastAutoAssistantMessageTick = Long.MIN_VALUE;
    private static final int VEHICLE_WHEEL_TELEMETRY_COUNT = 6;
    private final int[] lastVehicleWheelRpm = new int[VEHICLE_WHEEL_TELEMETRY_COUNT];
    private final int[] lastVehicleWheelSteer = new int[VEHICLE_WHEEL_TELEMETRY_COUNT];
    private final int[] lastVehicleWheelActualRpm = new int[VEHICLE_WHEEL_TELEMETRY_COUNT];

    private int lastVehicleSteerModeOrdinal = -1;
    private static final long GOVERNOR_COMMAND_SYNC_TICKS = 2L;
    private long lastGovernorCommandSyncTick = Long.MIN_VALUE;
    private final Map<BlockPos, Integer> lastSentGovernorTargets = new LinkedHashMap<>();

    private final BlockPos[] lastBoundGovernors = new BlockPos[ROW_COUNT];

    private final List<Integer> bindingRows = new ArrayList<>();
    private final boolean[] redstonePairRows = new boolean[ROW_COUNT];
    private final Map<Integer, FccRedstoneTransmitter> redstoneTransmitters = new LinkedHashMap<>();
    private final Map<Integer, FccLinkedControllerReceiver> linkedControllerReceivers = new LinkedHashMap<>();
    private final int[] linkedControllerReceivedStrengths = new int[LINKED_CONTROLLER_BUTTON_COUNT];
    private final boolean[] rowCalibrationValid = new boolean[ROW_COUNT];
    private final double[] rowCalibratedK = new double[ROW_COUNT];
    private final double[] rowYawFactor = new double[ROW_COUNT];
    private final BlockPos[] rowCalibratedProps = new BlockPos[ROW_COUNT];
    private final BlockPos[] rowCalibratedGovernors = new BlockPos[ROW_COUNT];
    private double battleBaselineMass = Double.NaN;
    private long battleBaselineAssemblyFingerprint = 0L;
    private int duplicateBindingRowMask = 0;

    private static final int SNAPSHOT_LINE_LIMIT = 8;
    private static final String FLIP_PREVENTION_LABEL = "Flip Prevention Switch";
    private static final String GAS_SIGNAL_LABEL = "Gas Signal Lever";
    private static final String BAY_OPEN_LABEL = "Bay Open Switch";
    private static final String BOMB_SAFETY_LABEL = "Bomb Safety Switch";
    private static final String BOMB_DROP_LABEL = "Bomb Drop Button";
    private static final String PAYLOAD_BAY_KEY = "PAYLOAD_BAY";
    private static final String PAYLOAD_DROP_KEY = "PAYLOAD_DROP";
    private static final String PAYLOAD_BAY_LABEL = "Bomb Bay Doors";
    private static final String PAYLOAD_DROP_LABEL = "Bomb Drop Link";
    private static final String DRIVE_MODE_4X4_LABEL = "Drive Mode 4x4 Switch";
    private static final String DRIVE_MODE_2X4_FRONT_LABEL = "Drive Mode 2x4 Front Switch";
    private static final String DRIVE_MODE_2X4_REAR_LABEL = "Drive Mode 2x4 Rear Switch";
    private static final String STEER_MODE_FRONT_LABEL = "Steer Mode Front Switch";
    private static final String STEER_MODE_ALL_WHEEL_LABEL = "Steer Mode All-Wheel Switch";
    private static final String STEER_MODE_CRAB_LABEL = "Steer Mode Crab Switch";
    private static final String DIFF_LOCK_REAR_LABEL = "Diff Lock Rear Switch";
    private static final String DIFF_LOCK_FULL_LABEL = "Diff Lock Full Switch";
    private static final String MID_AXLE_DRIVE_LABEL = "Mid Axle Drive Switch";
    private static final String STEERING_INVERT_LABEL = "Steering Invert Switch";
    private static final String AUTO_DRIVE_LABEL = "Auto Drive Switch";
    private static final String AUTO_DRIVE_PATHFINDER_LABEL = "Pathfinder Auto Drive Switch";
    private static final String AUTO_DRIVE_NAV_LABEL = "Nav Table Auto Drive Switch";
    private static final String AUTO_EVADE_RADIUS_LABEL = "Evade Radius Dial";
    private static final String AUTO_APPROACH_DISTANCE_LABEL = "Approach Distance Dial";
    private static final String AUTO_ANNOUNCE_INTERVAL_LABEL = "Announcement Interval Dial";
    private static final String AUTO_REVERSE_TIME_LABEL = "Reverse Time Dial";
    private static final int VEHICLE_MAX_WHEEL_RPM = 256;
    private static final double VEHICLE_FRONT_STEER_SIGNAL_GAIN = 1.0;
    private static final double VEHICLE_ALL_WHEEL_FRONT_SIGNAL_GAIN = 0.85;
    private static final double VEHICLE_ALL_WHEEL_REAR_SIGNAL_GAIN = 0.70;
    private static final double VEHICLE_CRAB_SIGNAL_GAIN = 0.80;
    private static final double VEHICLE_AUTO_STEER_FULL_ERROR_DEG = 40.0;
    private static final double VEHICLE_AUTO_STEER_DEADZONE_DEG = 3.0;
    private static final double VEHICLE_AUTO_ARRIVAL_RADIUS = 3.5;
    private static final double VEHICLE_AUTO_OVERSHOOT_HEADING_ERROR_DEG = 110.0;
    private static final double VEHICLE_AUTO_REVERSE_REACQUIRE_RADIUS = 12.0;
    private static final double VEHICLE_AUTO_REVERSE_ENTRY_SPEED = 2.5;
    private static final double VEHICLE_AUTO_REVERSE_TARGET_SPEED = 2.0;
    private static final double VEHICLE_AUTO_REVERSE_STEER_LIMIT = 0.65;
    private static final double VEHICLE_AUTO_STUCK_SPEED = 0.35;
    private static final double VEHICLE_AUTO_STUCK_THROTTLE = 0.28;
    private static final long VEHICLE_AUTO_STUCK_TICKS = 40L;
    private static final long VEHICLE_AUTO_REVERSE_UNSTICK_MIN_TICKS = 48L;
    private static final long VEHICLE_AUTO_REVERSE_UNSTICK_MAX_TICKS = 96L;
    private static final double VEHICLE_AUTO_REVERSE_UNSTICK_THROTTLE = -0.34;
    private static final long VEHICLE_AUTO_STRAIGHT_ESCAPE_MIN_TICKS = 24L;
    private static final long VEHICLE_AUTO_STRAIGHT_ESCAPE_MAX_TICKS = 64L;
    private static final double VEHICLE_AUTO_STRAIGHT_ESCAPE_TARGET_SPEED = 3.0;
    private static final double VEHICLE_AUTO_EVADE_RADIUS_MIN = 10.0;
    private static final double VEHICLE_AUTO_EVADE_RADIUS_MAX = 36.0;
    private static final double VEHICLE_AUTO_EVADE_RADIUS_DEFAULT = 22.0;
    private static final double AUTO_APPROACH_DISTANCE_MIN = 50.0;
    private static final double AUTO_APPROACH_DISTANCE_MAX = 800.0;
    private static final double AUTO_APPROACH_DISTANCE_DEFAULT = 200.0;
    private static final double AUTO_ANNOUNCE_INTERVAL_MIN = 50.0;
    private static final double AUTO_ANNOUNCE_INTERVAL_MAX = 1000.0;
    private static final double AUTO_ANNOUNCE_INTERVAL_DEFAULT = 250.0;
    private static final double AUTO_REVERSE_TIME_MIN_SECONDS = 2.0;
    private static final double AUTO_REVERSE_TIME_MAX_SECONDS = 10.0;
    private static final double AUTO_REVERSE_TIME_DEFAULT_SECONDS = 4.0;
    private static final double VEHICLE_AUTO_CRAWL_RADIUS = 14.0;
    private static final double VEHICLE_AUTO_APPROACH_RADIUS = 34.0;
    private static final double VEHICLE_AUTO_CRUISE_SPEED = 13.0;
    private static final double VEHICLE_AUTO_APPROACH_SPEED = 7.0;
    private static final double VEHICLE_AUTO_CRAWL_SPEED = 3.0;
    private static final double VEHICLE_AUTO_SPEED_GAIN = 0.12;
    // heavy vehicles only yaw while rolling. keep a floor speed through turns or it never comes round

    private static final double VEHICLE_AUTO_TURN_FLOOR_LATERAL_ACCEL = 3.6;
    private static final double VEHICLE_AUTO_TURN_FLOOR_SPEED_MIN = 4.0;
    private static final double VEHICLE_AUTO_TURN_FLOOR_SPEED_MAX = 6.5;
    private static final double VEHICLE_AUTO_HOLD_BRAKE_SPEED = 0.75;
    private static final double VEHICLE_AUTO_HOLD_BRAKE_GAIN = 0.10;
    private static final double VEHICLE_AUTO_HOLD_BRAKE_MAX = 0.28;
    private static final double AIRSHIP_AUTO_ARRIVAL_RADIUS = 8.0;
    private static final double AIRSHIP_AUTO_STEER_FULL_ERROR_DEG = 90.0;
    private static final double AIRSHIP_AUTO_STEER_DEADZONE_DEG = 7.0;
    private static final double AIRSHIP_AUTO_STEER_MAX = 0.58;
    private static final double AIRSHIP_AUTO_STEER_SLEW = 0.035;
    private static final double AIRSHIP_AUTO_THRUST_SLEW = 0.018;
    private static final double AIRSHIP_AUTO_YAW_DAMPING = 0.55;
    private static final double AIRSHIP_AUTO_STUCK_SPEED = 0.35;
    private static final double AIRSHIP_AUTO_STUCK_THRUST = 0.28;
    private static final long AIRSHIP_AUTO_STUCK_TICKS = 45L;
    private static final long AIRSHIP_AUTO_REACQUIRE_TICKS = 70L;
    private static final double AIRSHIP_AUTO_SPEED_GAIN = 0.14;
    private static final double AIRSHIP_AUTO_SPEED_INTEGRAL_GAIN = 0.006;
    private static final double AIRSHIP_AUTO_SPEED_INTEGRAL_MAX = 0.42;
    private static final double AIRSHIP_AUTO_APPROACH_WINDOW_DEFAULT = 90.0;
    private static final double AIRSHIP_AUTO_APPROACH_WINDOW_MIN = 34.0;
    private static final double AIRSHIP_AUTO_APPROACH_WINDOW_MAX = 260.0;
    private static final double AIRSHIP_AUTO_APPROACH_MIN_SPEED_FRACTION = 0.34;
    private static final double AIRSHIP_AUTO_REVERSE_MIN_THRUST = 0.18;
    private static final double AIRSHIP_AUTO_REVERSE_MAX_THRUST = 0.55;
    private static final double AIRSHIP_AUTO_REACQUIRE_SPEED = 3.0;
    private static final double AIRSHIP_AUTO_FLIP_WARNING_MARGIN_DEG = 5.0;
    private static final long AIRSHIP_AUTO_POST_FLIP_HOLD_TICKS = 40L;
    private static final long AIRSHIP_AUTO_POST_FLIP_RAMP_TICKS = 110L;
    private static final double AIRSHIP_AUTO_RECOVERY_MIN_THRUST_LIMIT = 0.12;
    private static final double AIRSHIP_AUTO_RECOVERY_MIN_YAW_LIMIT = 0.18;
    private static final double AIRSHIP_AUTO_REACHABLE_CAP_MIN = 1.5;
    private static final double AIRSHIP_AUTO_REACHABLE_CAP_MARGIN = 0.92;
    private static final long AIRSHIP_AUTO_REACHABLE_PROMOTE_TICKS = 20L * 12L;
    private static final double AIRSHIP_AUTO_REACHABLE_PROMOTE_STEP = 0.5;
    private static final long AUTO_ASSIST_CHAT_MIN_INTERVAL_TICKS = 35L;
    private static final double PATHFINDER_ELASTIC_LOOKAHEAD_MIN = 6.0;
    private static final double PATHFINDER_ELASTIC_LOOKAHEAD_MAX = 18.0;
    private static final double PATHFINDER_ELASTIC_LOOKAHEAD_SPEED_GAIN = 0.75;
    private static final double PATHFINDER_TURN_LOOKAHEAD_MIN = 4.0;
    private static final double PATHFINDER_TURN_LOOKAHEAD_MAX = 10.0;
    private static final double PATHFINDER_TURN_LOOKAHEAD_ANGLE_DEG = 28.0;
    private static final long PATHFINDER_GUIDANCE_STALE_TICKS = 20L * 5L;
    private static final long PATHFINDER_PROGRESS_REPORT_INTERVAL_TICKS = 10L;
    private static final float PATHFINDER_RETAINED_ROUTE_SPEED_CAP = 5.0f;

    private enum VehicleSteeringMode {
        FRONT,
        ALL_WHEEL,
        CRAB
    }

    private enum VehicleAutoDriveSource {
        NO_TARGET,
        PATHFINDER,
        NAV_TABLE
    }

    private record VehicleAutoDriveTarget(Vec3 position, double speedLimit, VehicleAutoDriveSource source,
                                          int waypointIndex, int waypointCount) {
    }

    private record PathfinderRouteProjection(int segmentIndex, double segmentT, Vec3 point, double distanceSq) {
    }

    private record PathfinderElasticTarget(Vec3 point, int waypointIndex, double speedLimit) {
    }

    private record VehicleAutoDriveAssist(boolean active, double steeringInput, double throttleInput,
                                          double distanceToTarget, double targetSpeed, double currentSpeed,
                                          VehicleAutoDriveSource source, int waypointIndex, int waypointCount) {
        private static VehicleAutoDriveAssist inactive() {
            return new VehicleAutoDriveAssist(false, 0.0, 0.0, Double.NaN, 0.0, 0.0, null, 0, 0);
        }

        private static VehicleAutoDriveAssist hold(VehicleAutoDriveSource source) {
            return new VehicleAutoDriveAssist(true, 0.0, 0.0, Double.NaN, 0.0, 0.0, source, 0, 0);
        }

        private static VehicleAutoDriveAssist hold(VehicleAutoDriveSource source, double throttleInput, double currentSpeed) {
            return new VehicleAutoDriveAssist(true, 0.0, throttleInput, Double.NaN, 0.0, currentSpeed, source, 0, 0);
        }
    }

    private record AirshipAutoDriveAssist(double steeringInput, double thrustInput,
                                          double distanceToTarget, double targetSpeed, double currentSpeed,
                                          String phase, Vec3 target) {
    }

    public record RouteArrowSnapshot(Vec3 origin, Vec3 control, Vec3 end, boolean ready, boolean hold) {
    }

    private static final int LINKED_THRUST_UP = 0;
    private static final int LINKED_THRUST_DOWN = 1;
    private static final int LINKED_WHEEL_LEFT = 2;
    private static final int LINKED_WHEEL_RIGHT = 3;
    private static final int LINKED_LEVITATION_UP = 4;
    private static final int LINKED_LEVITATION_DOWN = 5;
    private static final int LINKED_BAY_OPEN = 6;
    private static final int LINKED_BOMB_DROP = 7;
    private static final double FLIP_PREVENTION_ENGAGE_DEG = 25.0;
    private static final double FLIP_PREVENTION_RELEASE_DEG = 10.0;
    private static final double FLIP_PREVENTION_LIFT_POWER = 0.72;
    private static final long FLIP_PREVENTION_SOUND_INTERVAL_TICKS = 20L;

    @Nullable private BlockPos gimbalPos;
    @Nullable private BlockPos altitudeSensorPos;
    @Nullable private BlockPos velocitySensorPos;
    @Nullable private BlockPos navTablePos;
    private float navTableMaxSpeed = 8.0f;
    private float pathfinderLookaheadScale = 1.0f;
    @Nullable private BlockPos pathfinderComputerPos;
    @Nullable private UUID pathfinderComputerId;
    @Nullable private UUID pathfinderRouteId;
    private String pathfinderRouteName = "";
    private String pathfinderRouteStatus = "No Pathfinder linked";
    private boolean pathfinderRouteAccepted;
    private float pathfinderRouteConfidence;
    private int pathfinderRouteWaypointCount;
    private double pathfinderRouteDistance;
    private int pathfinderRouteHazardFlags;
    private long pathfinderRouteLastSeenTick;
    private final List<Vec3> pathfinderGuidanceWaypoints = new ArrayList<>();
    private final List<Float> pathfinderGuidanceSpeeds = new ArrayList<>();
    private int pathfinderRouteWaypointIndex;
    private long lastPathfinderProgressReportTick = Long.MIN_VALUE;
    private String lastPathfinderProgressStatus = "";
    private boolean pathfinderSystemsEnabled = true;

    private double lastKnownHeadingDeg = Double.NaN;

    private final List<CompoundTag> burnerLinks = new ArrayList<>();
    private NoseAxis noseAxis = NoseAxis.POS_Z;
    private FlightMode mode = FlightMode.OFF;

    private CompoundTag modeParams = new CompoundTag();

    private long assemblyFingerprint = 0L;

    private long serverTickCount = 0L;

    private final List<Integer> emanuelOverrideRpm = new ArrayList<>();

    private final List<Boolean> emanuelOverrideActive = new ArrayList<>();

    private boolean derivedDirty = true;
    private boolean workbenchReconcileDirty = true;
    @Nullable private com.createstats.flight.control.SensorDiscovery sensors;
    private boolean flipPreventionActive;
    private double lastFlipPreventionTiltDeg = Double.NaN;
    private long lastFlipPreventionSoundTick = Long.MIN_VALUE;
    @Nullable private String lastFlipPreventionWarning;
    private int linkedControllerRampIntervalTicks = 2;
    private int linkedControllerYawLevel;
    private int linkedControllerThrustLevel;
    private int linkedControllerLevitationLevel;
    private boolean linkedControllerSignalDirty;
    private int lastLoggedLinkedControllerInputMask = -1;
    private int lastLoggedLinkedControllerSignalPack = -1;
    private int lastLinkedControllerActionMask;

    public FlightControlComputerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = new ItemStackHandler(SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (stack.isEmpty()) return true;
                int row = slot / 2;
                if (isRedstonePairRow(row)) return true;

                if (slot % 2 == 0) return stack.getItem() instanceof PropellerCardItem;
                return stack.getItem() instanceof GovernorCardItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (level == null || level.isClientSide) return;
                setChanged();
                refreshDerivedBindings();
            }
        };
        this.linkedControllerInventory = new ItemStackHandler(LINKED_CONTROLLER_SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (level == null || level.isClientSide) return;
                setChanged();
                refreshLinkedControllerReceivers((ServerLevel) level);
                broadcastSnapshot();
            }
        };
    }

    public UUID getOrCreateUuid() {
        if (fccUuid == null) {
            fccUuid = UUID.randomUUID();
            setChanged();
        }
        return fccUuid;
    }

    @Nullable
    public UUID peekUuid() {
        return fccUuid;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStackHandler getLinkedControllerInventory() {
        return linkedControllerInventory;
    }

    public List<FccBinding> getBindings() {
        return Collections.unmodifiableList(bindings);
    }

    public int calibratedBindingCount() {
        int count = 0;
        for (FccBinding b : bindings) if (b.calibratedGainValid()) count++;
        return count;
    }

    public int getMaxBindings() {
        return ROW_COUNT;
    }

    public boolean isCardRowUsed(int row) {
        if (row < 0 || row >= ROW_COUNT) return false;
        return !inventory.getStackInSlot(getPropellerSlotIndex(row)).isEmpty()
                || !inventory.getStackInSlot(getGovernorSlotIndex(row)).isEmpty();
    }

    public boolean isRedstonePairRow(int row) {
        return row >= 0 && row < ROW_COUNT && redstonePairRows[row];
    }

    public boolean isRedstonePairComplete(int row) {
        if (!isRedstonePairRow(row)) return false;
        return !inventory.getStackInSlot(getPropellerSlotIndex(row)).isEmpty()
                || !inventory.getStackInSlot(getGovernorSlotIndex(row)).isEmpty();
    }

    public int getRedstoneRowMask() {
        int mask = 0;
        for (int row = 0; row < ROW_COUNT; row++) {
            if (redstonePairRows[row]) mask |= 1 << row;
        }
        return mask;
    }

    private int getRedstoneActiveRowMask() {
        int mask = 0;
        for (int row = 0; row < ROW_COUNT; row++) {
            if (isRedstonePairComplete(row)) mask |= 1 << row;
        }
        return mask;
    }

    private int getRedstonePoweredRowMask() {
        int mask = 0;
        for (Map.Entry<Integer, FccRedstoneTransmitter> entry : redstoneTransmitters.entrySet()) {
            int row = entry.getKey();
            if (row >= 0 && row < ROW_COUNT && entry.getValue().getTransmittedStrength() > 0) mask |= 1 << row;
        }
        return mask;
    }

    private boolean isLinkedControllerButtonConfigured(int button) {
        if (button < 0 || button >= LINKED_CONTROLLER_BUTTON_COUNT) return false;
        return !linkedControllerInventory.getStackInSlot(getLinkedControllerSlotIndex(button, false)).isEmpty()
                || !linkedControllerInventory.getStackInSlot(getLinkedControllerSlotIndex(button, true)).isEmpty();
    }

    private int getLinkedControllerConfiguredMask() {
        int mask = 0;
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            if (isLinkedControllerButtonConfigured(button)) mask |= 1 << button;
        }
        return mask;
    }

    private int getLinkedControllerInputMask() {
        int mask = 0;
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            if (linkedControllerReceivedStrengths[button] > 0) mask |= 1 << button;
        }
        return mask;
    }

    private int getLinkedControllerSignalPack() {
        int packed = 0;
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            packed |= (clampSignal(linkedControllerReceivedStrengths[button]) & 0xF) << (button * 4);
        }
        return packed;
    }

    public int getLinkedControllerRampIntervalTicks() {
        return linkedControllerRampIntervalTicks;
    }

    public void setLinkedControllerRampInterval(@Nullable String text) {
        int value;
        try {
            value = text == null || text.isBlank() ? 2 : Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        value = Math.max(LINKED_CONTROLLER_MIN_INTERVAL_TICKS, Math.min(LINKED_CONTROLLER_MAX_INTERVAL_TICKS, value));
        if (linkedControllerRampIntervalTicks == value) return;
        linkedControllerRampIntervalTicks = value;
        setChanged();
        broadcastSnapshot();
    }

    public void toggleRackRowMode(int row) {
        if (row < 0 || row >= ROW_COUNT) return;
        redstonePairRows[row] = !redstonePairRows[row];
        if (!redstonePairRows[row]) removeRedstoneTransmitter(row);
        refreshDerivedBindings();
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public int getVisibleCardRowCount() {
        int highestUsedRow = -1;
        for (int row = 0; row < ROW_COUNT; row++) {
            if (isCardRowUsed(row)) highestUsedRow = row;
        }
        return Math.max(MIN_VISIBLE_CARD_ROWS, Math.min(ROW_COUNT, highestUsedRow + 3));
    }

    public List<FccDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    public List<FccGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public List<FccCompositeControl> getControls() {
        return Collections.unmodifiableList(controls);
    }

    private void markWorkbenchReconcileDirty() {
        workbenchReconcileDirty = true;
    }

    public void renameWorkbenchDevice(UUID deviceId, @Nullable String newLabel) {
        if (deviceId == null) return;
        String label = sanitizeWorkbenchLabel(newLabel, "Device");
        boolean changed = false;
        for (int i = 0; i < devices.size(); i++) {
            FccDevice device = devices.get(i);
            if (!device.id().equals(deviceId)) continue;
            devices.set(i, new FccDevice(device.id(), label, device.category(), device.primaryPos(), device.secondaryPos(),
                    device.enabled(), device.colorArgb(), device.config()));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void cycleWorkbenchDeviceCategory(UUID deviceId) {
        if (deviceId == null) return;
        FccDeviceCategory[] values = {
                FccDeviceCategory.REGULAR_PROP,
            FccDeviceCategory.WHEEL_MOUNT,
                FccDeviceCategory.PROP_BEARING,
                FccDeviceCategory.FIN
        };
        boolean changed = false;
        for (int i = 0; i < devices.size(); i++) {
            FccDevice device = devices.get(i);
            if (!device.id().equals(deviceId)) continue;
            if (device.category() == FccDeviceCategory.REDSTONE_LINK) return;
            int current = 0;
            for (int idx = 0; idx < values.length; idx++) {
                if (values[idx] == device.category()) current = idx;
            }
            FccDeviceCategory next = values[(current + 1) % values.length];
            devices.set(i, device.withCategory(next));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void createWorkbenchGroup(@Nullable String requestedLabel) {
        if (groups.size() >= FlightConfig.MAX_WORKBENCH_GROUPS) {
            lastWarning = "Group limit reached (" + FlightConfig.MAX_WORKBENCH_GROUPS + ")";
            broadcastSnapshot();
            return;
        }
        String base = sanitizeWorkbenchLabel(requestedLabel, "Group");
        String label = uniqueWorkbenchGroupLabel(base, null);
        groups.add(new FccGroup(UUID.randomUUID(), label, 0xFFD4AF37, List.of(), false, ""));
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void renameWorkbenchGroup(UUID groupId, @Nullable String requestedLabel) {
        if (groupId == null) return;
        String base = sanitizeWorkbenchLabel(requestedLabel, "Group");
        String label = uniqueWorkbenchGroupLabel(base, groupId);
        boolean changed = false;
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!group.id().equals(groupId)) continue;
                groups.set(i, new FccGroup(group.id(), label, group.colorArgb(), group.deviceIds(),
                    group.hidden(), group.note(), group.zoneKey(), group.systemOwned()));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void deleteWorkbenchGroup(UUID groupId) {
        if (groupId == null) return;
        boolean changed = false;
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!group.id().equals(groupId)) continue;
            if (!group.deviceIds().isEmpty()) return;
            groups.remove(i);
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void assignWorkbenchDeviceToGroup(UUID deviceId, UUID groupId) {
        if (deviceId == null || groupId == null) return;
        boolean deviceExists = devices.stream().anyMatch(device -> device.id().equals(deviceId));
        if (!deviceExists) return;
        boolean changed = false;
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!group.id().equals(groupId)) continue;
            if (group.deviceIds().contains(deviceId)) return;
            List<UUID> ids = new ArrayList<>(group.deviceIds());
            ids.add(deviceId);
            groups.set(i, group.withDeviceIds(ids));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void unassignWorkbenchDeviceFromGroup(UUID deviceId, UUID groupId) {
        if (deviceId == null || groupId == null) return;
        boolean changed = false;
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!group.id().equals(groupId)) continue;
            if (!group.deviceIds().contains(deviceId)) return;
            List<UUID> ids = new ArrayList<>(group.deviceIds());
            ids.remove(deviceId);
            groups.set(i, group.withDeviceIds(ids));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void assignWorkbenchPayloadLink(UUID deviceId, @Nullable String payloadRole) {
        if (deviceId == null) return;
        FccDevice device = findDevice(deviceId);
        if (device == null || device.category() != FccDeviceCategory.REDSTONE_LINK) return;
        String roleToken = normalizeDiagramToken(payloadRole);
        boolean bayRole = roleToken.contains("bay") || roleToken.contains("door");
        boolean dropRole = roleToken.contains("drop") || roleToken.contains("release");
        if (!bayRole && !dropRole) return;
        boolean bay = bayRole && !dropRole;
        UUID groupId = ensurePayloadLinkGroup(deviceId, bay);
        int controlIndex = bay ? ensureBayOpenControl() : ensureBombDropControl();
        ensureBombSafetyControl();
        ensureBinaryRedstoneRoute(controlIndex, groupId, bay ? FccOutputType.TOGGLE_STATE : FccOutputType.PULSE_EVENT);
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    private UUID ensurePayloadLinkGroup(UUID deviceId, boolean bay) {
        String key = bay ? PAYLOAD_BAY_KEY : PAYLOAD_DROP_KEY;
        String label = bay ? PAYLOAD_BAY_LABEL : PAYLOAD_DROP_LABEL;
        String note = bay ? "Payload link: bomb bay doors" : "Payload link: bomb drop";
        int color = bay ? 0xFF39C8FF : 0xFFFF6B6B;
        UUID targetGroupId = null;
        for (FccGroup group : groups) {
            if (!payloadGroupMatches(group, bay)) continue;
            targetGroupId = group.id();
            break;
        }
        if (targetGroupId == null) {
            targetGroupId = UUID.randomUUID();
            groups.add(new FccGroup(targetGroupId, label, color, List.of(deviceId), false, note, key, true));
        }
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            boolean target = group.id().equals(targetGroupId);
            if (target) {
                groups.set(i, new FccGroup(group.id(), label, color, List.of(deviceId), false, note, key, true));
                continue;
            }
            List<UUID> ids = new ArrayList<>(group.deviceIds());
            boolean changed = ids.remove(deviceId);
            if (payloadGroupMatches(group, bay) && !ids.isEmpty()) {
                ids.clear();
                changed = true;
            }
            if (changed) groups.set(i, group.withDeviceIds(ids));
        }
        return targetGroupId;
    }

    private static boolean payloadGroupMatches(FccGroup group, boolean bay) {
        if (bay && PAYLOAD_BAY_KEY.equalsIgnoreCase(group.zoneKey())) return true;
        if (!bay && PAYLOAD_DROP_KEY.equalsIgnoreCase(group.zoneKey())) return true;
        String token = normalizeDiagramToken(group.label() + " " + group.note() + " " + group.zoneKey());
        return bay
            ? token.contains("payloadbay") || looksBayOpenLabel(token)
            : token.contains("payloaddrop") || looksBombDropLabel(token);
    }

    public void assignWorkbenchDeviceToDiagramZone(UUID deviceId, @Nullable String zoneKey) {
        if (deviceId == null) return;
        if (findDevice(deviceId) == null) return;
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return;
        if (hasMixedDiagramFamilies()) {
            recordLayoutConflictWarning("Reset the active layout before assigning more diagram zones.");
            broadcastSnapshot();
            return;
        }
        if (!canAssignDiagramZone(zone)) {
            recordLayoutConflictWarning(layoutConflictReasonFor(zone));
            broadcastSnapshot();
            return;
        }
        ensureDiagramGroup(zone, List.of(deviceId));
        clearLayoutConflictWarning();
        syncDiagramCockpitControls();
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void syncWorkbenchDiagramCockpit() {
        if (hasMixedDiagramFamilies()) {
            recordLayoutConflictWarning("Mixed Airship and Ground layouts detected. Reset one layout before syncing controls.");
            broadcastSnapshot();
            return;
        }
        clearLayoutConflictWarning();
        syncDiagramCockpitControls();
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void resetWorkbenchDiagram() {
        Set<UUID> diagramGroupIds = new LinkedHashSet<>();
        for (FccGroup group : groups) {
            for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
                if (diagramGroupMatches(zone, group)) {
                    diagramGroupIds.add(group.id());
                    break;
                }
            }

            if (payloadGroupMatches(group, true) || payloadGroupMatches(group, false)) {
                diagramGroupIds.add(group.id());
            }
        }
        if (diagramGroupIds.isEmpty()) {
            clearLayoutConflictWarning();
            for (FccCompositeControl control : controls) {
                if (isDiagramZoneSwitchControl(control)) controlValues.put(control.id(), control.inputMin());
            }
            setChanged();
            broadcastSnapshot();
            return;
        }
        groups.removeIf(group -> diagramGroupIds.contains(group.id()));
        Set<UUID> remainingGroupIds = new LinkedHashSet<>();
        for (FccGroup g : groups) remainingGroupIds.add(g.id());
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            List<FccControlRoute> routes = new ArrayList<>();
            for (FccControlRoute route : control.routes()) {
                boolean inDiagramGroup = diagramGroupIds.contains(route.targetGroupId());
                boolean systemRoute = route.systemOwned() && route.routeKind() != FccRouteKind.CUSTOM;
                if (!inDiagramGroup || (!systemRoute && remainingGroupIds.contains(route.targetGroupId()))) {
                    routes.add(route);
                }
            }
            if (routes.size() != control.routes().size()) {
                controls.set(i, copyControl(control, control.label(), control.widgetType(), routes));
            }
            if (isDiagramZoneSwitchControl(control)) controlValues.put(control.id(), control.inputMin());
        }
        clearLayoutConflictWarning();
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void createWorkbenchControl(@Nullable String requestedLabel) {
        if (controls.size() >= FlightConfig.MAX_WORKBENCH_CONTROLS) {
            lastWarning = "Control limit reached (" + FlightConfig.MAX_WORKBENCH_CONTROLS + ")";
            broadcastSnapshot();
            return;
        }
        String base = sanitizeWorkbenchLabel(requestedLabel, nextCustomControlLabel());
        String label = uniqueWorkbenchControlLabel(base, null);
        FccWidgetType widgetType = defaultWidgetForControlLabel(label);
        UUID id = UUID.randomUUID();
        controls.add(new FccCompositeControl(id, label, widgetType,
            widgetType == FccWidgetType.WHEEL || widgetType == FccWidgetType.DIAL,
            widgetType == FccWidgetType.BUTTON || widgetType == FccWidgetType.TOGGLE ? 0.0 : -1.0,
            1.0, 0.0, 0.08, FccCurveType.SOFT, widgetType.name().toLowerCase(java.util.Locale.ROOT), false, List.of()));
        controlValues.put(id, 0.0);
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void renameWorkbenchControl(UUID controlId, @Nullable String requestedLabel) {
        if (controlId == null) return;
        String base = sanitizeWorkbenchLabel(requestedLabel, "Control");
        String label = uniqueWorkbenchControlLabel(base, controlId);
        boolean changed = false;
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            if (!control.id().equals(controlId)) continue;
            controls.set(i, copyControl(control, label, control.widgetType(), control.routes()));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void cycleWorkbenchControlWidget(UUID controlId) {
        if (controlId == null) return;
        boolean changed = false;
        FccWidgetType[] values = FccWidgetType.values();
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            if (!control.id().equals(controlId)) continue;
            FccWidgetType next = values[(control.widgetType().ordinal() + 1) % values.length];
            controls.set(i, copyControlWithWidgetDefaults(control, next, control.routes()));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void deleteWorkbenchControl(UUID controlId) {
        if (controlId == null) return;
        boolean changed = controls.removeIf(control -> control.id().equals(controlId));
        if (!changed) return;
        controlValues.remove(controlId);
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void adjustWorkbenchControlValue(UUID controlId, @Nullable String deltaText) {
        if (controlId == null) return;
        FccCompositeControl control = findControl(controlId);
        if (control == null) return;
        double delta;
        try {
            delta = deltaText == null || deltaText.isBlank() ? 0.0 : Double.parseDouble(deltaText.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        if (!Double.isFinite(delta) || Math.abs(delta) < 1.0e-9) return;
        double current = controlValues.getOrDefault(controlId, control.neutralValue());
        double next = Math.max(control.inputMin(), Math.min(control.inputMax(), current + delta));
        controlValues.put(controlId, next);
        setChanged();
        broadcastSnapshot();
    }

    public void setWorkbenchControlValue(UUID controlId, @Nullable String valueText) {
        if (controlId == null) return;
        FccCompositeControl control = findControl(controlId);
        if (control == null) return;
        double value;
        try {
            value = valueText == null || valueText.isBlank() ? control.neutralValue() : Double.parseDouble(valueText.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        if (!Double.isFinite(value)) return;
        double next = Math.max(control.inputMin(), Math.min(control.inputMax(), value));
        controlValues.put(controlId, next);
        setChanged();
        broadcastSnapshot();
    }

    public void centerWorkbenchControlValue(UUID controlId) {
        if (controlId == null) return;
        FccCompositeControl control = findControl(controlId);
        if (control == null) return;
        controlValues.put(controlId, control.neutralValue());
        setChanged();
        broadcastSnapshot();
    }

    public void addWorkbenchRoute(UUID controlId, UUID groupId, @Nullable String requestedOutputType) {
        if (controlId == null || groupId == null) return;
        FccCompositeControl targetControl = findControl(controlId);
        boolean groupExists = groups.stream().anyMatch(group -> group.id().equals(groupId));
        if (!groupExists) return;
        if (targetControl != null && targetControl.routes().size() >= FlightConfig.MAX_ROUTES_PER_CONTROL) {
            lastWarning = "Route limit reached (" + FlightConfig.MAX_ROUTES_PER_CONTROL + " per control)";
            broadcastSnapshot();
            return;
        }
        FccOutputType outputType = FccOutputType.safeValueOf(requestedOutputType, FccOutputType.RPM_DELTA);
        if ((requestedOutputType == null || requestedOutputType.isBlank() || outputType == FccOutputType.RPM_DELTA)
                && groupHasRedstoneLinkDevice(groupId)) {
            outputType = defaultRedstoneOutputType(targetControl);
        }
        if (targetControl != null) {
            for (FccControlRoute existing : targetControl.routes()) {
                if (existing.targetGroupId().equals(groupId) && existing.outputType() == outputType) {
                    lastWarning = "Route already exists for that group and output";
                    broadcastSnapshot();
                    return;
                }
            }
        }
        boolean changed = false;
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            if (!control.id().equals(controlId)) continue;
            List<FccControlRoute> routes = new ArrayList<>(control.routes());
                routes.add(new FccControlRoute(UUID.randomUUID(), groupId, outputType,
                    defaultRouteScale(outputType), 0.0, defaultMinClamp(outputType), defaultMaxClamp(outputType),
                    defaultRouteInvert(groupId, outputType), false, null, null, ""));
            controls.set(i, copyControl(control, control.label(), control.widgetType(), routes));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void deleteWorkbenchRoute(UUID routeId) {
        if (routeId == null) return;
        boolean changed = false;
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            List<FccControlRoute> routes = new ArrayList<>(control.routes());
            if (!routes.removeIf(route -> route.id().equals(routeId))) continue;
            controls.set(i, copyControl(control, control.label(), control.widgetType(), routes));
            changed = true;
            break;
        }
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void cycleWorkbenchRouteOutput(UUID routeId) {
        if (routeId == null) return;
        FccOutputType[] values = FccOutputType.values();
        boolean changed = mutateWorkbenchRoute(routeId, route -> {
            FccOutputType outputType = values[(route.outputType().ordinal() + 1) % values.length];
            FccControlRoute defaults = copyRouteWithOutputDefaults(route, outputType);
            return defaultRouteInvert(route.targetGroupId(), outputType)
                ? copyRoute(defaults, outputType, defaults.scale(), true)
                : defaults;
        });
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void toggleWorkbenchRouteInvert(UUID routeId) {
        if (routeId == null) return;
        boolean changed = mutateWorkbenchRoute(routeId, route -> copyRoute(route,
                route.outputType(), route.scale(), !route.invert()));
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void adjustWorkbenchRouteScale(UUID routeId, @Nullable String deltaText) {
        if (routeId == null) return;
        double delta;
        try {
            delta = deltaText == null || deltaText.isBlank() ? 0.0 : Double.parseDouble(deltaText.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        if (!Double.isFinite(delta) || Math.abs(delta) < 1.0e-9) return;
        boolean changed = mutateWorkbenchRoute(routeId, route -> copyRoute(route,
                route.outputType(), Math.max(-100.0, Math.min(100.0, route.scale() + delta)), route.invert()));
        if (!changed) return;
        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    public void applyZeppelinTemplate() {
        TemplatePropGroups propGroups = buildTemplatePropGroups();
        UUID leftGroup = ensureTemplateGroup("Left Thrust", propGroups.left(), 0xFF39C8FF, "Zeppelin left thrust");
        UUID rightGroup = ensureTemplateGroup("Right Thrust", propGroups.right(), 0xFFFFB454, "Zeppelin right thrust");
        UUID liftGroup = ensureTemplateGroup("Lift Props", propGroups.lift(), 0xFF55E0A0, "Zeppelin lift props");
        UUID bearingGroup = ensureTemplateGroup("Vector Bearings", collectTemplateDeviceIds(FccDeviceCategory.PROP_BEARING), 0xFFD4AF37, "Zeppelin vector bearings");
        ensureTemplateGroup("Fins", collectTemplateDeviceIds(FccDeviceCategory.FIN), 0xFF55E0A0, "Zeppelin fins");

        int thrustControl = ensureTemplateControl("Thrust Lever", new String[] { "Thrust" },
            FccWidgetType.LEVER, true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "thrust",
            FccControlRole.THRUST, "", true);
        int yawControl = ensureTemplateControl("Yaw Wheel", new String[] { "Yaw" },
            FccWidgetType.WHEEL, true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "yaw",
            FccControlRole.YAW, "", true);
        int levitationControl = ensureTemplateControl("Levitation Dial", new String[] { "Lift", "Fin Trim", "Levitation Trim" },
            FccWidgetType.DIAL, true, -1.0, 1.0, 0.0, FccCurveType.LINEAR, "levitation",
            FccControlRole.LIFT, "", true);
        ensureTemplateControl("Custom Button 1", new String[] { "Aux 1", "Switch 1" },
                FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "switch");
        ensureTemplateControl("Custom Button 2", new String[] { "Aux 2", "Switch 2" },
                FccWidgetType.BUTTON, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "button");
        ensureFlipPreventionControl();

        upsertTemplateRoute(thrustControl, leftGroup, FccOutputType.RPM_SCALE, 0.45, 0.0, -1.0, 1.0, true);
        upsertTemplateRoute(thrustControl, rightGroup, FccOutputType.RPM_SCALE, 0.45, 0.0, -1.0, 1.0, true);
        upsertTemplateRoute(levitationControl, liftGroup, FccOutputType.RPM_SCALE, 1.0, 0.0, 0.0, 1.0, false);
        upsertTemplateRoute(yawControl, leftGroup, FccOutputType.RPM_DELTA, 0.55, 0.0, -0.65, 0.65, true);
        upsertTemplateRoute(yawControl, rightGroup, FccOutputType.RPM_DELTA, -0.55, 0.0, -0.65, 0.65, true);
        upsertTemplateRoute(yawControl, bearingGroup, FccOutputType.ANGLE_OFFSET, 22.0, 0.0, -45.0, 45.0, false);
        upsertTemplateRoute(levitationControl, bearingGroup, FccOutputType.ANGLE_OFFSET, 12.0, 0.0, -35.0, 35.0, false);

        markWorkbenchReconcileDirty();
        setChanged();
        broadcastSnapshot();
    }

    private String nextCustomControlLabel() {
        int suffix = 1;
        while (true) {
            String label = "Custom Button " + suffix;
            boolean exists = false;
            for (FccCompositeControl control : controls) {
                if (control.label().equalsIgnoreCase(label)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return label;
            suffix++;
        }
    }

    private static FccWidgetType defaultWidgetForControlLabel(String label) {
        String lower = label == null ? "" : label.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("yaw") || lower.contains("wheel")) return FccWidgetType.WHEEL;
        if (lower.contains("dial") || lower.contains("trim") || lower.contains("levitation")) return FccWidgetType.DIAL;
        if (lower.contains("switch") || lower.contains("toggle")) return FccWidgetType.TOGGLE;
        if (lower.contains("button")) return FccWidgetType.BUTTON;
        if (lower.contains("thrust") || lower.contains("trst") || lower.contains("throttle") || lower.contains("lift") || lower.contains("lever")) return FccWidgetType.LEVER;
        return FccWidgetType.BUTTON;
    }

    private static String sanitizeWorkbenchLabel(@Nullable String label, String fallback) {
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) return fallback;
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private String uniqueWorkbenchGroupLabel(String base, @Nullable UUID ignoredGroupId) {
        String candidate = base;
        int suffix = 2;
        Set<String> existing = new LinkedHashSet<>();
        for (FccGroup group : groups) {
            if (ignoredGroupId != null && ignoredGroupId.equals(group.id())) continue;
            existing.add(group.label().toLowerCase(java.util.Locale.ROOT));
        }
        while (existing.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
            String suffixText = " " + suffix;
            int maxBase = Math.max(1, 32 - suffixText.length());
            candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + suffixText;
            suffix++;
        }
        return candidate;
    }

    private String uniqueWorkbenchControlLabel(String base, @Nullable UUID ignoredControlId) {
        String candidate = base;
        int suffix = 2;
        Set<String> existing = new LinkedHashSet<>();
        for (FccCompositeControl control : controls) {
            if (ignoredControlId != null && ignoredControlId.equals(control.id())) continue;
            existing.add(control.label().toLowerCase(java.util.Locale.ROOT));
        }
        while (existing.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
            String suffixText = " " + suffix;
            int maxBase = Math.max(1, 32 - suffixText.length());
            candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + suffixText;
            suffix++;
        }
        return candidate;
    }

    private static FccCompositeControl copyControl(FccCompositeControl control, String label,
                                                   FccWidgetType widgetType, List<FccControlRoute> routes) {
        return new FccCompositeControl(control.id(), label, widgetType, control.springCentered(),
                control.inputMin(), control.inputMax(), control.neutralValue(), control.deadzone(),
            control.curveType(), control.animationTheme(), control.advancedMode(),
            control.role(), control.zoneKey(), control.systemOwned(), routes);
    }

    private static FccCompositeControl copyControlWithWidgetDefaults(FccCompositeControl control,
                                                                     FccWidgetType widgetType,
                                                                     List<FccControlRoute> routes) {
        return switch (widgetType) {
            case WHEEL -> copyControlWithSettings(control, control.label(), widgetType, true,
                    -1.0, 1.0, 0.0, FccCurveType.SOFT, "yaw", routes);
            case DIAL -> copyControlWithSettings(control, control.label(), widgetType, true,
                    -1.0, 1.0, 0.0, FccCurveType.LINEAR, "dial", routes);
            case LEVER -> copyControlWithSettings(control, control.label(), widgetType, false,
                    0.0, 1.0, 0.0, FccCurveType.SOFT, "thrust", routes);
            case TOGGLE -> copyControlWithSettings(control, control.label(), widgetType, false,
                    0.0, 1.0, 0.0, FccCurveType.LINEAR, "switch", routes);
            case BUTTON -> copyControlWithSettings(control, control.label(), widgetType, false,
                    0.0, 1.0, 0.0, FccCurveType.LINEAR, "button", routes);
        };
    }

    private static FccCompositeControl copyControlWithSettings(FccCompositeControl control, String label,
                                                               FccWidgetType widgetType, boolean springCentered,
                                                               double inputMin, double inputMax, double neutralValue,
                                                               FccCurveType curveType, String animationTheme,
                                                               List<FccControlRoute> routes) {
        return new FccCompositeControl(control.id(), label, widgetType, springCentered,
                inputMin, inputMax, neutralValue, control.deadzone(), curveType, animationTheme,
            control.advancedMode(), control.role(), control.zoneKey(), control.systemOwned(), routes);
        }

        private static FccCompositeControl copyControlWithMetadata(FccCompositeControl control,
                                       FccControlRole role,
                                       @Nullable String zoneKey,
                                       boolean systemOwned,
                                       List<FccControlRoute> routes) {
        return new FccCompositeControl(control.id(), control.label(), control.widgetType(), control.springCentered(),
            control.inputMin(), control.inputMax(), control.neutralValue(), control.deadzone(),
            control.curveType(), control.animationTheme(), control.advancedMode(),
            role, zoneKey == null ? "" : zoneKey, systemOwned, routes);
    }

    private record DiagramZoneSpec(String key, String label, int colorArgb, String note, String... aliases) {
        private String switchLabel() {
            return label + " Switch";
        }
    }

    private static final List<DiagramZoneSpec> DIAGRAM_ZONE_SPECS = List.of(
            new DiagramZoneSpec("FRONT_MAIN_THRUST", "Front Main Thrust", 0xFF62D5FF, "Diagram zone: forward main thrust", "front thrust", "fore thrust", "nose thrust"),
            new DiagramZoneSpec("BACK_MAIN_THRUST", "Back Main Thrust", 0xFFFFB454, "Diagram zone: rear main thrust", "rear thrust", "back thrust", "aft thrust"),
            new DiagramZoneSpec("MAIN_THRUST", "Main Thrust", 0xFFFFDD55, "Diagram zone: centerline thrust", "thrust", "thrust props", "main engine"),
            new DiagramZoneSpec("LEFT_THRUST", "Left Thrust", 0xFF39C8FF, "Diagram zone: left-side thrust", "left props", "left propellers", "port thrust"),
            new DiagramZoneSpec("RIGHT_THRUST", "Right Thrust", 0xFFFFB454, "Diagram zone: right-side thrust", "right props", "right propellers", "starboard thrust"),
            new DiagramZoneSpec("LEFT_PROPELLERS", "Left Propellers", 0xFF58A6FF, "Diagram zone: left propeller bank", "left propeller bank", "port propellers"),
            new DiagramZoneSpec("RIGHT_PROPELLERS", "Right Propellers", 0xFFFF9F6E, "Diagram zone: right propeller bank", "right propeller bank", "starboard propellers"),
            new DiagramZoneSpec("UPWARD_LEVITATION", "Upward / Levitation", 0xFF6EE7A8, "Diagram zone: lift and levitation", "lift props", "levitation", "upward propellers", "up thrust"),
            new DiagramZoneSpec("DOWNWARD_DESCENSION", "Downward / Descension", 0xFFB48CFF, "Diagram zone: downward or descent thrust", "downward propellers", "descension", "down thrust"),
            new DiagramZoneSpec("STEAM_BURNER", "Steam Vent / Burner", 0xFFFF6B6B, "Diagram zone: vent or burner hardware", "steam vent", "hot air burner", "burner"),
            new DiagramZoneSpec("FINS", "Fins", 0xFF55E0A0, "Diagram zone: fins or angle bearings", "control surfaces", "vector bearings", "bearing fins"),
            new DiagramZoneSpec("AUX_BOOST", "Aux / Boost", 0xFFD4AF37, "Diagram zone: auxiliary boost hardware", "aux thrust", "boost", "auxiliary thrust"),
            new DiagramZoneSpec("LEFT_MAIN_FLAP", "Left Main Flap", 0xFF82D9A0, "Diagram zone: left main wing flap", "left flap", "port flap", "left wing flap", "main flap left"),
            new DiagramZoneSpec("RIGHT_MAIN_FLAP", "Right Main Flap", 0xFFFFB88A, "Diagram zone: right main wing flap", "right flap", "starboard flap", "right wing flap", "main flap right"),
            new DiagramZoneSpec("LEFT_ELEVATOR", "Left Elevator", 0xFFAED6F1, "Diagram zone: left tail elevator", "left elevator", "port elevator", "left tail flap", "elevator left"),
                new DiagramZoneSpec("RIGHT_ELEVATOR", "Right Elevator", 0xFFFFD59E, "Diagram zone: right tail elevator", "right elevator", "starboard elevator", "right tail flap", "elevator right"),
                new DiagramZoneSpec("FRONT_LEFT_WHEEL", "Front Left Wheel", 0xFF62D5FF, "Vehicle zone: front-left wheel", "front left", "frontleft", "fl wheel"),
                new DiagramZoneSpec("FRONT_RIGHT_WHEEL", "Front Right Wheel", 0xFFFFB454, "Vehicle zone: front-right wheel", "front right", "frontright", "fr wheel"),
                new DiagramZoneSpec("MID_LEFT_WHEEL", "Mid Left Wheel", 0xFF58A6FF, "Vehicle zone: mid-left wheel", "mid left", "middle left", "ml wheel"),
                new DiagramZoneSpec("MID_RIGHT_WHEEL", "Mid Right Wheel", 0xFFFF9F6E, "Vehicle zone: mid-right wheel", "mid right", "middle right", "mr wheel"),
                new DiagramZoneSpec("REAR_LEFT_WHEEL", "Rear Left Wheel", 0xFF6EE7A8, "Vehicle zone: rear-left wheel", "rear left", "back left", "rl wheel"),
                new DiagramZoneSpec("REAR_RIGHT_WHEEL", "Rear Right Wheel", 0xFFFFD59E, "Vehicle zone: rear-right wheel", "rear right", "back right", "rr wheel")
    );

    @Nullable
    private static DiagramZoneSpec diagramZoneSpec(@Nullable String keyOrLabel) {
        String token = normalizeDiagramToken(keyOrLabel);
        if (token.isEmpty()) return null;
        for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
            if (token.equals(normalizeDiagramToken(zone.key())) || token.equals(normalizeDiagramToken(zone.label()))) return zone;
            for (String alias : zone.aliases()) {
                if (token.equals(normalizeDiagramToken(alias))) return zone;
            }
        }
        return null;
    }

    private static boolean isOtomobilDiagramZone(DiagramZoneSpec zone) {
        return zone.key().endsWith("_WHEEL");
    }

    @Nullable
    private static DiagramZoneSpec resolveDiagramZoneForGroup(FccGroup group) {
        if (!group.zoneKey().isEmpty()) {
            return diagramZoneSpec(group.zoneKey());
        }
        if (group.systemOwned()) {
            return diagramZoneSpec(group.label());
        }
        return null;
    }

    private boolean hasDiagramFamilyPopulation(boolean otomobil) {
        for (FccGroup group : groups) {
            if (group.deviceIds().isEmpty()) continue;
            DiagramZoneSpec zone = resolveDiagramZoneForGroup(group);
            if (zone == null) continue;
            if (isOtomobilDiagramZone(zone) == otomobil) return true;
        }
        return false;
    }

    private boolean hasMixedDiagramFamilies() {
        return hasDiagramFamilyPopulation(false) && hasDiagramFamilyPopulation(true);
    }

    private boolean canAssignDiagramZone(DiagramZoneSpec zone) {
        boolean otomobilZone = isOtomobilDiagramZone(zone);
        return !hasDiagramFamilyPopulation(!otomobilZone);
    }

    private String layoutConflictReasonFor(DiagramZoneSpec zone) {
        return isOtomobilDiagramZone(zone)
                ? "Airship Layout is already populated. Reset it before assigning Ground zones."
                : "Ground Layout is already populated. Reset it before assigning Airship zones.";
    }

    private void recordLayoutConflictWarning(String detail) {
        lastWarning = "Diagram conflict: " + detail;
    }

    private void clearLayoutConflictWarning() {
        if (lastWarning != null && lastWarning.startsWith("Diagram conflict:")) {
            lastWarning = null;
        }
    }

    private static String normalizeDiagramToken(@Nullable String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) builder.append(c);
        }
        return builder.toString();
    }

    private UUID ensureDiagramGroup(DiagramZoneSpec zone, List<UUID> deviceIds) {
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!diagramGroupMatches(zone, group)) continue;
            List<UUID> merged = new ArrayList<>(group.deviceIds());
            for (UUID id : deviceIds) {
                if (!merged.contains(id)) merged.add(id);
            }
            String resolvedZoneKey = group.systemOwned() ? zone.key() : group.zoneKey();
            boolean resolvedSystemOwned = group.systemOwned() || zone.key().equalsIgnoreCase(group.zoneKey());
            groups.set(i, new FccGroup(group.id(), group.label(), group.colorArgb(), merged,
                    group.hidden(), group.note().isEmpty() ? zone.note() : group.note(),
                    resolvedZoneKey, resolvedSystemOwned));
            return group.id();
        }
        UUID id = UUID.randomUUID();
        groups.add(new FccGroup(id, zone.label(), zone.colorArgb(), deviceIds, false, zone.note(), zone.key(), true));
        return id;
    }

    private static boolean diagramGroupMatches(DiagramZoneSpec zone, FccGroup group) {
        if (!group.zoneKey().isEmpty() && group.zoneKey().equalsIgnoreCase(zone.key())) return true;
        return diagramGroupMatches(zone, group.label());
    }

    private static boolean diagramGroupMatches(DiagramZoneSpec zone, String label) {
        String token = normalizeDiagramToken(label);
        if (token.equals(normalizeDiagramToken(zone.key())) || token.equals(normalizeDiagramToken(zone.label()))) return true;
        for (String alias : zone.aliases()) {
            if (token.equals(normalizeDiagramToken(alias))) return true;
        }
        return false;
    }

    private UUID ensureDiagramGroup(String key) {
        DiagramZoneSpec zone = diagramZoneSpec(key);
        if (zone == null) throw new IllegalArgumentException("Unknown diagram zone " + key);
        return ensureDiagramGroup(zone, List.of());
    }

    private void syncDiagramCockpitControls() {
        int thrustControl = ensureTemplateControl("Thrust Lever", new String[] { "Thrust", "Throttle" },
            FccWidgetType.LEVER, true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "thrust",
            FccControlRole.THRUST, "", true);
        int liftControl = ensureTemplateControl("Levitation Dial", new String[] { "Lift", "Levitation Trim" },
            FccWidgetType.DIAL, true, -1.0, 1.0, 0.0, FccCurveType.LINEAR, "levitation",
            FccControlRole.LIFT, "", true);
        int yawControl = ensureTemplateControl("Yaw Wheel", new String[] { "Yaw", "Wheel" },
            FccWidgetType.WHEEL, true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "yaw",
            FccControlRole.YAW, "", true);
        removeLegacyGasSignalControl();
        ensureFlipPreventionControl();
        ensureBayOpenControl();
        ensureBombSafetyControl();
        ensureBombDropControl();
        ensureVehicleModeControls();

        for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
            ensureDiagramZoneSwitchControl(zone);
            UUID groupId = findDiagramGroupId(zone);
            if (groupId == null) continue;
            syncDiagramRoutesForZone(zone, groupId, thrustControl, liftControl, yawControl);
        }
    }

    private boolean removeLegacyGasSignalControl() {
        boolean removed = false;
        for (Iterator<FccCompositeControl> it = controls.iterator(); it.hasNext();) {
            FccCompositeControl control = it.next();
            if (!GAS_SIGNAL_LABEL.equalsIgnoreCase(control.label())) continue;
            controlValues.remove(control.id());
            it.remove();
            removed = true;
        }
        return removed;
    }

    @Nullable
    private UUID findDiagramGroupId(DiagramZoneSpec zone) {
        for (FccGroup group : groups) {
            if (diagramGroupMatches(zone, group)) return group.id();
        }
        return null;
    }

    private int ensureDiagramZoneSwitchControl(DiagramZoneSpec zone) {
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            if (control.role() != FccControlRole.ZONE_SWITCH) continue;
            if (!zone.key().equalsIgnoreCase(control.zoneKey())) continue;
            return ensureTemplateControl(zone.switchLabel(), new String[] {
                "Switch " + zone.label(),
                zone.label() + " Toggle"
            }, FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
                FccControlRole.ZONE_SWITCH, zone.key(), true);
        }
        return ensureTemplateControl(zone.switchLabel(), new String[] {
                "Switch " + zone.label(),
            zone.label() + " Toggle"
        }, FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
                FccControlRole.ZONE_SWITCH, zone.key(), true);
    }

    private int ensureFlipPreventionControl() {
        return ensureTemplateControl(FLIP_PREVENTION_LABEL, new String[] {
                "Flip Prevention",
                "Flip Preventation",
                "Anti Flip Switch",
                "Stability Guard"
        }, FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "warning_switch",
            FccControlRole.SYSTEM, "", true);
    }

    private int ensureBayOpenControl() {
        return ensureTemplateControl(BAY_OPEN_LABEL, new String[] {
                "Bay Open",
                "Bomb Bay",
                "Bomb Bay Door",
                "Bay Door Switch"
        }, FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "bay_switch",
            FccControlRole.SYSTEM, "", true);
    }

    private int ensureBombSafetyControl() {
        return ensureTemplateControl(BOMB_SAFETY_LABEL, new String[] {
            "Bomb Safety",
            "Bomb Arm Safety",
            "Drop Safety",
            "Release Safety"
        }, FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "warning_switch",
            FccControlRole.SYSTEM, "", true);
    }

    private int ensureBombDropControl() {
        return ensureTemplateControl(BOMB_DROP_LABEL, new String[] {
                "Bomb Drop",
                "Drop Bomb",
                "Bomb Release",
                "Release Button"
        }, FccWidgetType.BUTTON, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "drop_button",
            FccControlRole.SYSTEM, "", true);
    }

    private void ensureVehicleModeControls() {
        ensureTemplateControl(DRIVE_MODE_4X4_LABEL, new String[] { "4x4", "AWD", "All Wheel Drive" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(DRIVE_MODE_2X4_FRONT_LABEL, new String[] { "2x4 Front", "Front Drive" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(DRIVE_MODE_2X4_REAR_LABEL, new String[] { "2x4 Rear", "Rear Drive" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(STEER_MODE_FRONT_LABEL, new String[] { "Front Steer", "Normal Steering" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(STEER_MODE_ALL_WHEEL_LABEL, new String[] { "All Wheel Steer", "4WS" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(STEER_MODE_CRAB_LABEL, new String[] { "Crab Turn", "Crab Steering" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(DIFF_LOCK_REAR_LABEL, new String[] { "Diff Rear", "Rear Diff Lock" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(DIFF_LOCK_FULL_LABEL, new String[] { "Diff Full", "Full Diff Lock" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(MID_AXLE_DRIVE_LABEL, new String[] { "Mid Axle Drive", "Mid Axle" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(STEERING_INVERT_LABEL, new String[] { "Steering Invert", "Steer Invert", "Invert Steering" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_DRIVE_LABEL, new String[] { "Auto Drive", "Self Drive", "Self Driving" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_DRIVE_PATHFINDER_LABEL,
            new String[] { "Pathfinder Auto Drive", "Pathfinder Drive", "Pathfinder Mode", "Pathfinder Auto" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_DRIVE_NAV_LABEL,
            new String[] { "Nav Table Auto Drive", "Nav Auto Drive", "Nav Table Drive", "Nav Auto" },
            FccWidgetType.TOGGLE, false, 0.0, 1.0, 0.0, FccCurveType.LINEAR, "overhead_switch",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_EVADE_RADIUS_LABEL,
            new String[] { "Evade Radius", "Auto Evade Radius", "Avoid Radius" },
            FccWidgetType.DIAL, false, VEHICLE_AUTO_EVADE_RADIUS_MIN, VEHICLE_AUTO_EVADE_RADIUS_MAX,
            VEHICLE_AUTO_EVADE_RADIUS_DEFAULT, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_APPROACH_DISTANCE_LABEL,
            new String[] { "Approach Distance", "Slowdown Distance", "Arrival Slowdown" },
            FccWidgetType.DIAL, false, AUTO_APPROACH_DISTANCE_MIN, AUTO_APPROACH_DISTANCE_MAX,
            AUTO_APPROACH_DISTANCE_DEFAULT, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_ANNOUNCE_INTERVAL_LABEL,
            new String[] { "Announcement Interval", "Announce Every", "Assistant Interval" },
            FccWidgetType.DIAL, false, AUTO_ANNOUNCE_INTERVAL_MIN, AUTO_ANNOUNCE_INTERVAL_MAX,
            AUTO_ANNOUNCE_INTERVAL_DEFAULT, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_REVERSE_TIME_LABEL,
            new String[] { "Reverse Time", "Reverse Duration", "Unstick Reverse Time" },
            FccWidgetType.DIAL, false, AUTO_REVERSE_TIME_MIN_SECONDS, AUTO_REVERSE_TIME_MAX_SECONDS,
            AUTO_REVERSE_TIME_DEFAULT_SECONDS, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        }

    private void ensureAutoAssistTuningControls() {
        ensureTemplateControl(AUTO_APPROACH_DISTANCE_LABEL,
            new String[] { "Approach Distance", "Slowdown Distance", "Arrival Slowdown" },
            FccWidgetType.DIAL, false, AUTO_APPROACH_DISTANCE_MIN, AUTO_APPROACH_DISTANCE_MAX,
            AUTO_APPROACH_DISTANCE_DEFAULT, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_ANNOUNCE_INTERVAL_LABEL,
            new String[] { "Announcement Interval", "Announce Every", "Assistant Interval" },
            FccWidgetType.DIAL, false, AUTO_ANNOUNCE_INTERVAL_MIN, AUTO_ANNOUNCE_INTERVAL_MAX,
            AUTO_ANNOUNCE_INTERVAL_DEFAULT, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
        ensureTemplateControl(AUTO_REVERSE_TIME_LABEL,
            new String[] { "Reverse Time", "Reverse Duration", "Unstick Reverse Time" },
            FccWidgetType.DIAL, false, AUTO_REVERSE_TIME_MIN_SECONDS, AUTO_REVERSE_TIME_MAX_SECONDS,
            AUTO_REVERSE_TIME_DEFAULT_SECONDS, FccCurveType.LINEAR, "tuning_dial",
            FccControlRole.SYSTEM, "", true);
    }

    @Nullable
    private FccCompositeControl findFlipPreventionControl() {
        for (FccCompositeControl control : controls) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("flip") && (token.contains("prevent") || token.contains("guard"))) return control;
        }
        return null;
    }

    @Nullable
    private FccCompositeControl findBayOpenControl() {
        for (FccCompositeControl control : controls) {
            if (looksBayOpenLabel(control.label())) return control;
        }
        return null;
    }

    @Nullable
    private FccCompositeControl findBombDropControl() {
        for (FccCompositeControl control : controls) {
            if (looksBombDropLabel(control.label())) return control;
        }
        return null;
    }

    @Nullable
    private FccCompositeControl findBombSafetyControl() {
        for (FccCompositeControl control : controls) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("bomb") && (token.contains("safety") || token.contains("arm"))) return control;
            if (token.contains("drop") && token.contains("safety")) return control;
        }
        return null;
    }

    private static boolean looksBayOpenLabel(@Nullable String label) {
        String token = normalizeDiagramToken(label);
        return token.contains("bombbay") || (token.contains("bay") && (token.contains("open") || token.contains("door")));
    }

    private static boolean looksBombDropLabel(@Nullable String label) {
        String token = normalizeDiagramToken(label);
        return (token.contains("bomb") && (token.contains("drop") || token.contains("release")))
                || token.contains("dropper") || token.equals("drop");
    }

    private static FccOutputType defaultRedstoneOutputType(@Nullable FccCompositeControl control) {
        if (control != null) {
            if (looksBayOpenLabel(control.label())) return FccOutputType.TOGGLE_STATE;
            if (looksBombDropLabel(control.label())) return FccOutputType.PULSE_EVENT;
        }
        return FccOutputType.SIGNAL_LEVEL;
    }

    private boolean isFlipPreventionEnabled() {
        FccCompositeControl control = findFlipPreventionControl();
        if (control == null) return false;
        double value = controlValues.getOrDefault(control.id(), control.neutralValue());
        return value >= control.inputMin() + (control.inputMax() - control.inputMin()) * 0.5;
    }

    private boolean isDiagramZoneSwitchControl(FccCompositeControl control) {
        if (control.role() == FccControlRole.ZONE_SWITCH) return true;
        for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
            if (isDiagramZoneSwitchControl(control, zone)) return true;
        }
        return false;
    }

    private boolean isDiagramZoneSwitchControl(FccCompositeControl control, DiagramZoneSpec zone) {
        if (control.role() == FccControlRole.ZONE_SWITCH && zone.key().equalsIgnoreCase(control.zoneKey())) return true;
        String token = normalizeDiagramToken(control.label());
        return token.equals(normalizeDiagramToken(zone.switchLabel()))
                || token.equals(normalizeDiagramToken("Switch " + zone.label()))
                || (token.contains("switch") && diagramGroupMatches(zone, control.label()));
    }

    @Nullable
    private FccCompositeControl findDiagramZoneSwitchControl(DiagramZoneSpec zone) {
        for (FccCompositeControl control : controls) {
            if (isDiagramZoneSwitchControl(control, zone)) return control;
        }
        return null;
    }

    private boolean isZoneRouteEnabled(FccGroup group) {
        DiagramZoneSpec zone = diagramZoneSpec(group.label());
        if (zone == null) return true;
        FccCompositeControl switchControl = findDiagramZoneSwitchControl(zone);
        if (switchControl == null) return true;
        double value = controlValues.getOrDefault(switchControl.id(), switchControl.neutralValue());
        return value >= switchControl.inputMin() + (switchControl.inputMax() - switchControl.inputMin()) * 0.5;
    }

    private void syncDiagramRoutesForZone(DiagramZoneSpec zone, UUID groupId, int thrustControl, int liftControl, int yawControl) {
        String key = zone.key();
        switch (key) {
            case "BACK_MAIN_THRUST" -> upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.65, 0.0, -1.0, 1.0, true);
            case "FRONT_MAIN_THRUST" -> upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.45, 0.0, -1.0, 1.0, true);
            case "MAIN_THRUST" -> upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.55, 0.0, -1.0, 1.0, true);
            case "LEFT_THRUST", "LEFT_PROPELLERS" -> {
                upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.45, 0.0, -1.0, 1.0, true);
                upsertTemplateRoute(yawControl, groupId, FccOutputType.RPM_DELTA, 0.55, 0.0, -0.65, 0.65, true);
            }
            case "RIGHT_THRUST", "RIGHT_PROPELLERS" -> {
                upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.45, 0.0, -1.0, 1.0, true);
                upsertTemplateRoute(yawControl, groupId, FccOutputType.RPM_DELTA, -0.55, 0.0, -0.65, 0.65, true);
            }
            case "UPWARD_LEVITATION" -> upsertTemplateRoute(liftControl, groupId, FccOutputType.RPM_SCALE, 1.0, 0.0, 0.0, 1.0, false);
            case "DOWNWARD_DESCENSION" -> upsertTemplateRoute(liftControl, groupId, FccOutputType.RPM_SCALE, -0.50, 0.0, 0.0, 1.0, false);
            case "FINS" -> {
                upsertTemplateRoute(yawControl, groupId, FccOutputType.ANGLE_OFFSET, 24.0, 0.0, -45.0, 45.0, false);
                upsertTemplateRoute(liftControl, groupId, FccOutputType.ANGLE_OFFSET, 12.0, 0.0, -35.0, 35.0, false);
            }
            case "STEAM_BURNER" -> upsertTemplateRoute(liftControl, groupId, FccOutputType.SIGNAL_LEVEL, 15.0, 0.0, 0.0, 15.0, false);
            case "FRONT_LEFT_WHEEL" -> {
                upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.55, 0.0, -1.0, 1.0, true, true);
                upsertTemplateRoute(yawControl, groupId, FccOutputType.RPM_DELTA, 0.30, 0.0, -0.45, 0.45, true);
            }
            case "FRONT_RIGHT_WHEEL" -> {
                upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.55, 0.0, -1.0, 1.0, true, true);
                upsertTemplateRoute(yawControl, groupId, FccOutputType.RPM_DELTA, -0.30, 0.0, -0.45, 0.45, true);
            }
            case "MID_LEFT_WHEEL", "MID_RIGHT_WHEEL" ->
                    upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.42, 0.0, -1.0, 1.0, true, true);
            case "REAR_LEFT_WHEEL", "REAR_RIGHT_WHEEL" ->
                    upsertTemplateRoute(thrustControl, groupId, FccOutputType.RPM_SCALE, 0.52, 0.0, -1.0, 1.0, true, true);
            default -> {
                if (groupHasRedstoneLinkDevice(groupId)) {
                    upsertTemplateRoute(liftControl, groupId, FccOutputType.SIGNAL_LEVEL, 15.0, 0.0, 0.0, 15.0, false);
                }
            }
        }
        syncDiagramSwitchRoute(zone, groupId);
    }

    private void syncDiagramSwitchRoute(DiagramZoneSpec zone, UUID groupId) {
        syncDiagramSwitchMetadataRoute(zone, groupId);
    }

    private boolean repairDiagramSwitchMetadataRoutes() {
        boolean changed = false;
        for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
            UUID groupId = findDiagramGroupId(zone);
            if (groupId == null) continue;
            changed |= syncDiagramSwitchMetadataRoute(zone, groupId);
        }
        return changed;
    }

    private boolean syncDiagramSwitchMetadataRoute(DiagramZoneSpec zone, UUID groupId) {
        int controlCountBefore = controls.size();
        int switchControl = ensureDiagramZoneSwitchControl(zone);
        if (switchControl < 0 || switchControl >= controls.size()) return false;
        FccCompositeControl control = controls.get(switchControl);
        List<FccControlRoute> routes = new ArrayList<>();
        boolean changed = controls.size() != controlCountBefore;
        boolean hasGateRoute = false;
        for (FccControlRoute route : control.routes()) {
            if (!route.targetGroupId().equals(groupId)) {
                routes.add(route);
                continue;
            }
            if ((route.routeKind() == FccRouteKind.ZONE_GATE || route.outputType() == FccOutputType.TOGGLE_STATE) && !hasGateRoute) {
                FccControlRoute normalized = switchGateRoute(route.id(), groupId);
                routes.add(normalized);
                hasGateRoute = true;
                changed |= !sameSwitchGateRoute(route, normalized);
            } else {
                changed = true;
            }
        }
        if (!hasGateRoute) {
            routes.add(switchGateRoute(UUID.randomUUID(), groupId));
            changed = true;
        }
        if (changed) {
            controls.set(switchControl, copyControl(control, control.label(), control.widgetType(), routes));
        }
        return changed;
    }

    private static FccControlRoute switchGateRoute(UUID routeId, UUID groupId) {
        return new FccControlRoute(routeId, groupId, FccOutputType.TOGGLE_STATE,
                1.0, 0.0, 0.0, 1.0, false, false, null, null,
                "Zone enable gate", FccRouteKind.ZONE_GATE, true);
    }

    private static boolean sameSwitchGateRoute(FccControlRoute first, FccControlRoute second) {
        return first.outputType() == second.outputType()
                && first.targetGroupId().equals(second.targetGroupId())
                && Math.abs(first.scale() - second.scale()) < 1.0e-9
                && Math.abs(first.offset() - second.offset()) < 1.0e-9
                && Math.abs(first.minClamp() - second.minClamp()) < 1.0e-9
                && Math.abs(first.maxClamp() - second.maxClamp()) < 1.0e-9
                && first.invert() == second.invert()
                && first.reverseAllowed() == second.reverseAllowed()
                && first.routeKind() == second.routeKind()
                && first.systemOwned() == second.systemOwned();
    }

    private boolean repairCockpitControlRoles() {
        Map<UUID, String> groupLabels = new LinkedHashMap<>();
        for (FccGroup group : groups) groupLabels.put(group.id(), group.label());
        boolean changed = false;
        for (int controlIndex = 0; controlIndex < controls.size(); controlIndex++) {
            FccCompositeControl control = controls.get(controlIndex);
            if (!control.systemOwned()) continue;

            if (isDiagramZoneSwitchControl(control)) continue;
            boolean differentialYaw = hasDifferentialYawRoutes(control, groupLabels);
            boolean thrustControl = isThrustControl(control);
            List<FccControlRoute> routes = new ArrayList<>(control.routes().size());
            boolean routeChanged = false;
            for (FccControlRoute route : control.routes()) {
                boolean shouldAllowReverse = (differentialYaw && isYawRpmRoute(route, groupLabels))
                        || (thrustControl && isThrustRpmRoute(route, groupLabels))
                        || canRouteCommandReverse(control, route);
                boolean shouldWidenThrustRoute = thrustControl && isThrustRpmRoute(route, groupLabels)
                        && (Math.abs(route.minClamp() + 1.0) > 1.0e-6
                        || Math.abs(route.maxClamp() - 1.0) > 1.0e-6);
                if ((shouldAllowReverse && !route.reverseAllowed()) || shouldWidenThrustRoute) {
                    FccControlRoute updated = route;
                    if (shouldWidenThrustRoute) {
                        updated = new FccControlRoute(updated.id(), updated.targetGroupId(), updated.outputType(),
                                updated.scale(), updated.offset(), -1.0, 1.0, updated.invert(), updated.reverseAllowed(),
                                updated.angleLimitOverride(), updated.rateLimitOverride(), updated.note(),
                                updated.routeKind(), updated.systemOwned());
                    }
                    if (shouldAllowReverse && !updated.reverseAllowed()) updated = copyRouteReverseAllowed(updated, true);
                    routes.add(updated);
                    routeChanged = true;
                } else {
                    routes.add(route);
                }
            }
            boolean controlChanged = differentialYaw && (control.widgetType() != FccWidgetType.WHEEL
                    || !control.springCentered()
                    || Math.abs(control.inputMin() + 1.0) > 1.0e-6
                    || Math.abs(control.inputMax() - 1.0) > 1.0e-6
                    || Math.abs(control.neutralValue()) > 1.0e-6
                    || control.curveType() != FccCurveType.SOFT
                    || !control.animationTheme().equals("yaw"));
            boolean thrustControlChanged = thrustControl && (control.widgetType() != FccWidgetType.LEVER
                    || !control.springCentered()
                    || Math.abs(control.inputMin() + 1.0) > 1.0e-6
                    || Math.abs(control.inputMax() - 1.0) > 1.0e-6
                    || Math.abs(control.neutralValue()) > 1.0e-6
                    || control.curveType() != FccCurveType.SOFT
                    || !control.animationTheme().equals("thrust"));
            if (!controlChanged && !thrustControlChanged && !routeChanged) continue;
            if (controlChanged) {
                controls.set(controlIndex, copyControlWithSettings(control, control.label(), FccWidgetType.WHEEL,
                        true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "yaw", routes));
                controlValues.put(control.id(), 0.0);
            } else if (thrustControlChanged) {
                controls.set(controlIndex, copyControlWithSettings(control, "Thrust Lever", FccWidgetType.LEVER,
                        true, -1.0, 1.0, 0.0, FccCurveType.SOFT, "thrust", routes));
                controlValues.put(control.id(), 0.0);
            } else {
                controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
            }
            changed = true;
        }
        return changed;
    }

    private boolean runWorkbenchReconcilePass() {
        boolean changed = false;
        if (findFlipPreventionControl() == null) {
            ensureFlipPreventionControl();
            changed = true;
        }
        if (findBayOpenControl() == null) {
            ensureBayOpenControl();
            changed = true;
        }
        if (findBombSafetyControl() == null) {
            ensureBombSafetyControl();
            changed = true;
        }
        if (findBombDropControl() == null) {
            ensureBombDropControl();
            changed = true;
        }
        changed |= repairDiagramSwitchMetadataRoutes();
        changed |= repairCockpitControlRoles();
        changed |= repairRedstoneLinkRoutes();
        changed |= pruneWorkbenchIntegrity();
        return changed;
    }

    private boolean repairRedstoneLinkRoutes() {
        boolean changed = false;
        for (int controlIndex = 0; controlIndex < controls.size(); controlIndex++) {
            FccCompositeControl control = controls.get(controlIndex);
            FccOutputType binaryType = null;
            if (looksBayOpenLabel(control.label())) binaryType = FccOutputType.TOGGLE_STATE;
            else if (looksBombDropLabel(control.label())) binaryType = FccOutputType.PULSE_EVENT;
            if (binaryType == null) continue;
            List<FccControlRoute> routes = new ArrayList<>(control.routes().size());
            boolean routeChanged = false;
            for (FccControlRoute route : control.routes()) {
                if (groupHasRedstoneLinkDevice(route.targetGroupId()) && route.outputType() != binaryType) {
                    routes.add(copyRouteWithOutputDefaults(route, binaryType));
                    routeChanged = true;
                } else {
                    routes.add(route);
                }
            }
            if (!routeChanged) continue;
            controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
            changed = true;
        }

        int bayControl = ensureBayOpenControl();
        int dropControl = ensureBombDropControl();
        for (FccGroup group : groups) {
            if (!groupHasRedstoneLinkDevice(group.id())) continue;
            String token = normalizeDiagramToken(group.label() + " " + group.note() + " " + group.zoneKey());
            if (looksBayOpenLabel(token)) {
                changed |= ensureBinaryRedstoneRoute(bayControl, group.id(), FccOutputType.TOGGLE_STATE);
            }
            if (looksBombDropLabel(token)) {
                changed |= ensureBinaryRedstoneRoute(dropControl, group.id(), FccOutputType.PULSE_EVENT);
            }
        }
        return changed;
    }

    private boolean ensureBinaryRedstoneRoute(int controlIndex, UUID groupId, FccOutputType outputType) {
        if (controlIndex < 0 || controlIndex >= controls.size()) return false;
        FccCompositeControl control = controls.get(controlIndex);
        List<FccControlRoute> routes = new ArrayList<>(control.routes().size() + 1);
        boolean found = false;
        boolean changed = false;
        for (FccControlRoute route : control.routes()) {
            if (!route.targetGroupId().equals(groupId)) {
                routes.add(route);
                continue;
            }
            if (found) {
                changed = true;
                continue;
            }
            FccControlRoute normalized = binaryRedstoneRoute(route.id(), groupId, outputType);
            routes.add(normalized);
            found = true;
            changed |= !sameBinaryRedstoneRoute(route, normalized);
        }
        if (!found) {
            routes.add(binaryRedstoneRoute(UUID.randomUUID(), groupId, outputType));
            changed = true;
        }
        if (changed) {
            controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
        }
        return changed;
    }

    private static FccControlRoute binaryRedstoneRoute(UUID routeId, UUID groupId, FccOutputType outputType) {
        return new FccControlRoute(routeId, groupId, outputType,
                1.0, 0.0, 0.0, 1.0, false, false, null, null,
                "Redstone binary link", FccRouteKind.TEMPLATE_ROUTE, true);
    }

    private static boolean sameBinaryRedstoneRoute(FccControlRoute first, FccControlRoute second) {
        return first.outputType() == second.outputType()
                && first.targetGroupId().equals(second.targetGroupId())
                && Math.abs(first.scale() - second.scale()) < 1.0e-9
                && Math.abs(first.offset() - second.offset()) < 1.0e-9
                && Math.abs(first.minClamp() - second.minClamp()) < 1.0e-9
                && Math.abs(first.maxClamp() - second.maxClamp()) < 1.0e-9
                && first.invert() == second.invert()
                && first.routeKind() == second.routeKind()
                && first.systemOwned() == second.systemOwned();
    }

    private boolean pruneWorkbenchIntegrity() {
        boolean changed = false;
        Map<String, UUID> zoneSwitchByZone = new LinkedHashMap<>();
        Set<UUID> groupIds = new LinkedHashSet<>();
        for (FccGroup group : groups) groupIds.add(group.id());
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            List<FccControlRoute> filtered = new ArrayList<>();
            boolean routeChanged = false;
            for (FccControlRoute route : control.routes()) {
                if (groupIds.contains(route.targetGroupId())) {
                    filtered.add(route);
                } else {
                    routeChanged = true;
                }
            }
            if (control.role() == FccControlRole.ZONE_SWITCH) {
                String zoneKey = control.zoneKey().trim().toUpperCase(java.util.Locale.ROOT);
                if (!zoneKey.isEmpty()) {
                    UUID existing = zoneSwitchByZone.putIfAbsent(zoneKey, control.id());
                    if (existing != null) {
                        controlValues.remove(control.id());
                        controls.remove(i);
                        i--;
                        changed = true;
                        continue;
                    }
                }
            }
            if (routeChanged) {
                controls.set(i, copyControl(control, control.label(), control.widgetType(), filtered));
                changed = true;
            }
        }
        return changed;
    }

    private boolean migrateLegacyDiagramMetadata() {
        boolean changed = false;
        Map<UUID, DiagramZoneSpec> groupZoneMap = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            DiagramZoneSpec zone = diagramZoneSpec(group.label());
            if (zone == null) continue;
            groupZoneMap.put(group.id(), zone);
            if (zone.key().equalsIgnoreCase(group.zoneKey()) && group.systemOwned()) continue;
            groups.set(i, new FccGroup(group.id(), group.label(), group.colorArgb(), group.deviceIds(),
                    group.hidden(), group.note(), zone.key(), true));
            changed = true;
        }

        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            FccControlRole role = control.role();
            String zoneKey = control.zoneKey();
            boolean systemOwned = control.systemOwned();

            if (role == FccControlRole.CUSTOM) {
                DiagramZoneSpec switchZone = null;
                for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
                    if (isDiagramZoneSwitchControl(control, zone)) {
                        switchZone = zone;
                        break;
                    }
                }
                if (switchZone != null) {
                    role = FccControlRole.ZONE_SWITCH;
                    zoneKey = switchZone.key();
                    systemOwned = true;
                } else {
                    String token = normalizeDiagramToken(control.label());

                    boolean hasThrustRoutes = control.routes().stream()
                            .anyMatch(r -> r.outputType() == FccOutputType.RPM_SCALE || r.outputType() == FccOutputType.RPM_DELTA);
                    if (hasThrustRoutes && (token.equals("thrustlever") || token.equals("throttle"))) {
                        role = FccControlRole.THRUST;
                        systemOwned = true;
                    } else if (hasThrustRoutes && (token.equals("yawwheel") || token.equals("yaw"))) {
                        role = FccControlRole.YAW;
                        systemOwned = true;
                    } else if (hasThrustRoutes && (token.equals("levitationdial") || token.equals("lift") || token.equals("levitation"))) {
                        role = FccControlRole.LIFT;
                        systemOwned = true;
                    }
                }
            } else if (!systemOwned && (role == FccControlRole.THRUST || role == FccControlRole.YAW
                    || role == FccControlRole.LIFT || role == FccControlRole.ZONE_SWITCH
                    || role == FccControlRole.SYSTEM)) {
                systemOwned = true;
                if (role == FccControlRole.ZONE_SWITCH && zoneKey.isEmpty()) {
                    for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
                        if (isDiagramZoneSwitchControl(control, zone)) {
                            zoneKey = zone.key();
                            break;
                        }
                    }
                }
            }

            List<FccControlRoute> migratedRoutes = new ArrayList<>(control.routes().size());
            boolean routeChanged = false;
            for (FccControlRoute route : control.routes()) {
                FccRouteKind routeKind = route.routeKind();
                boolean routeSystemOwned = route.systemOwned();
                if (role == FccControlRole.ZONE_SWITCH
                        && route.outputType() == FccOutputType.TOGGLE_STATE
                        && groupZoneMap.containsKey(route.targetGroupId())) {
                    routeKind = FccRouteKind.ZONE_GATE;
                    routeSystemOwned = true;
                } else if (systemOwned && "Zeppelin template".equalsIgnoreCase(route.note())) {
                    routeKind = FccRouteKind.TEMPLATE_ROUTE;
                    routeSystemOwned = true;
                }
                if (routeKind != route.routeKind() || routeSystemOwned != route.systemOwned()) {
                    migratedRoutes.add(new FccControlRoute(route.id(), route.targetGroupId(), route.outputType(),
                            route.scale(), route.offset(), route.minClamp(), route.maxClamp(),
                            route.invert(), route.reverseAllowed(), route.angleLimitOverride(), route.rateLimitOverride(),
                            route.note(), routeKind, routeSystemOwned));
                    routeChanged = true;
                } else {
                    migratedRoutes.add(route);
                }
            }

            boolean controlChanged = role != control.role()
                    || !zoneKey.equals(control.zoneKey())
                    || systemOwned != control.systemOwned()
                    || routeChanged;
            if (!controlChanged) continue;
            controls.set(i, new FccCompositeControl(control.id(), control.label(), control.widgetType(), control.springCentered(),
                    control.inputMin(), control.inputMax(), control.neutralValue(), control.deadzone(),
                    control.curveType(), control.animationTheme(), control.advancedMode(),
                    role, zoneKey, systemOwned, migratedRoutes));
            changed = true;
        }
        return changed;
    }

    private static boolean hasDifferentialYawRoutes(FccCompositeControl control, Map<UUID, String> groupLabels) {
        boolean leftPositive = false;
        boolean leftNegative = false;
        boolean rightPositive = false;
        boolean rightNegative = false;
        for (FccControlRoute route : control.routes()) {
            if (!isRpmRoute(route.outputType())) continue;
            String label = groupLabels.getOrDefault(route.targetGroupId(), "");
            double scale = effectiveRouteScale(route);
            if (Math.abs(scale) < 1.0e-6) continue;
            if (looksLeftLabel(label)) {
                leftPositive |= scale > 0.0;
                leftNegative |= scale < 0.0;
            }
            if (looksRightLabel(label)) {
                rightPositive |= scale > 0.0;
                rightNegative |= scale < 0.0;
            }
        }
        return (leftPositive && rightNegative) || (leftNegative && rightPositive);
    }

    private static boolean isYawRpmRoute(FccControlRoute route, Map<UUID, String> groupLabels) {
        if (route.outputType() != FccOutputType.RPM_DELTA) return false;
        String label = groupLabels.getOrDefault(route.targetGroupId(), "");
        return looksLeftLabel(label) || looksRightLabel(label);
    }

    private static boolean isThrustControl(FccCompositeControl control) {
        if (control.role() == FccControlRole.THRUST) return true;
        String token = normalizeDiagramToken(control.label());
        return token.contains("thrust") || token.contains("throttle");
    }

    private static boolean isThrustRpmRoute(FccControlRoute route, Map<UUID, String> groupLabels) {
        if (!isRpmRoute(route.outputType())) return false;
        return looksThrustLabel(groupLabels.getOrDefault(route.targetGroupId(), ""));
    }

    private static boolean isRpmRoute(FccOutputType outputType) {
        return outputType == FccOutputType.RPM_DELTA || outputType == FccOutputType.RPM_SCALE;
    }

    private static boolean canRouteCommandReverse(FccCompositeControl control, FccControlRoute route) {
        if (!isRpmRoute(route.outputType())) return false;
        double lowInput = control.inputMin() < control.neutralValue() ? -1.0 : 0.0;
        double highInput = control.inputMax() > control.neutralValue() ? 1.0 : 0.0;
        return Math.min(routeValue(lowInput, route), routeValue(highInput, route)) < -1.0e-6;
    }

    private static double effectiveRouteScale(FccControlRoute route) {
        return route.scale() * (route.invert() ? -1.0 : 1.0);
    }

    private static boolean looksLeftLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("left") || lower.contains("lft") || lower.contains("port");
    }

    private static boolean looksRightLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("right") || lower.contains("rght") || lower.contains("rgt") || lower.contains("starboard");
    }

    private static boolean looksThrustLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("thrust") || lower.contains("prop") || lower.contains("engine")
                || lower.contains("front main") || lower.contains("back main") || lower.contains("main");
    }

    private static boolean looksLiftLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("lift") || lower.contains("levitat") || lower.contains("upward") || lower.contains("hover");
    }

    @Nullable
    private FccCompositeControl findControl(UUID controlId) {
        for (FccCompositeControl control : controls) {
            if (control.id().equals(controlId)) return control;
        }
        return null;
    }

    @Nullable
    private FccGroup findGroup(UUID groupId) {
        for (FccGroup group : groups) {
            if (group.id().equals(groupId)) return group;
        }
        return null;
    }

    @Nullable
    private FccDevice findDevice(UUID deviceId) {
        for (FccDevice device : devices) {
            if (device.id().equals(deviceId)) return device;
        }
        return null;
    }

    private boolean groupHasRedstoneLinkDevice(UUID groupId) {
        FccGroup group = findGroup(groupId);
        if (group == null) return false;
        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device != null && device.category() == FccDeviceCategory.REDSTONE_LINK) return true;
        }
        return false;
    }

    private double shapedControlInput(FccCompositeControl control) {
        double raw = controlValues.getOrDefault(control.id(), control.neutralValue());
        raw = Math.max(control.inputMin(), Math.min(control.inputMax(), raw));
        double centered = raw - control.neutralValue();
        double span = centered >= 0.0
                ? Math.max(1.0e-6, control.inputMax() - control.neutralValue())
                : Math.max(1.0e-6, control.neutralValue() - control.inputMin());
        double normalized = centered / span;
        double deadzone = Math.max(0.0, Math.min(0.95, control.deadzone()));
        double abs = Math.abs(normalized);
        if (abs <= deadzone) return 0.0;
        double remapped = (abs - deadzone) / Math.max(1.0e-6, 1.0 - deadzone);
        double shaped = switch (control.curveType()) {
            case LINEAR -> remapped;
            case SOFT -> remapped * remapped;
            case AGGRESSIVE -> Math.sqrt(remapped);
            case S_CURVE -> remapped * remapped * (3.0 - 2.0 * remapped);
        };
        return Math.copySign(Math.min(1.0, shaped), normalized);
    }

    private static double routeValue(double input, FccControlRoute route) {
        double value = (route.invert() ? -input : input) * route.scale() + route.offset();
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(route.minClamp(), Math.min(route.maxClamp(), value));
    }

    private boolean writeGyroRoute(ServerLevel serverLevel, BlockPos propPos, FccOutputType outputType, double angleDeg) {
        BlockEntity propBe = SableBridge.getBlockEntityOnContraption(serverLevel, propPos);
        SableBridge.PropData propData = SableBridge.readProp(propBe);
        if (propData == null || !propData.isGyro()) return false;

        double radians = Math.toRadians(Math.max(-80.0, Math.min(80.0, angleDeg)));
        org.joml.Vector3d base = new org.joml.Vector3d(0.0, 0.0, 1.0);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = base.x * cos + base.z * sin;
        double y = base.y;
        double z = base.z * cos - base.x * sin;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-9 || !Double.isFinite(length)) return false;
        return SableBridge.writeGyroTiltVector(propBe, x / length, y / length, z / length);
    }

    private static int clampRpm(int value) {
        return Math.max(-FlightConfig.RPM_CLAMP, Math.min(FlightConfig.RPM_CLAMP, value));
    }

    private static int clampSignal(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private boolean applyRedstoneLinkSignals(ServerLevel serverLevel, Map<Integer, Integer> commands) {
        refreshRedstoneTransmitters(serverLevel);
        int beforePoweredMask = getRedstonePoweredRowMask();
        boolean changed = false;
        for (FccRedstoneTransmitter transmitter : new ArrayList<>(redstoneTransmitters.values())) {
            int target = clampSignal(commands.getOrDefault(transmitter.row(), 0));
            if (!transmitter.setTransmittedStrength(target)) continue;
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(serverLevel, transmitter);
            } catch (RuntimeException | LinkageError ex) {
                DebugLogging.fccDebug("[FCC@{}] Failed to update redstone link row {}", getBlockPos(), transmitter.row(), ex);
            }
            changed = true;
        }
        if (beforePoweredMask != getRedstonePoweredRowMask()) {
            broadcastSnapshot();
        }
        return changed;
    }

    private void refreshRedstoneTransmitters(ServerLevel serverLevel) {
        Set<Integer> keepRows = new LinkedHashSet<>();
        for (int row = 0; row < ROW_COUNT; row++) {
            Couple<RedstoneLinkNetworkHandler.Frequency> key = redstoneNetworkKeyForRow(row);
            if (key == null) continue;
            keepRows.add(row);
            FccRedstoneTransmitter existing = redstoneTransmitters.get(row);
            if (existing != null && existing.matches(key)) continue;
            if (existing != null) removeRedstoneTransmitter(row);
            FccRedstoneTransmitter transmitter = new FccRedstoneTransmitter(row, key);
            redstoneTransmitters.put(row, transmitter);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, transmitter);
            } catch (RuntimeException | LinkageError ex) {
                redstoneTransmitters.remove(row);
                DebugLogging.fccDebug("[FCC@{}] Failed to add redstone link row {}", getBlockPos(), row, ex);
            }
        }
        List<Integer> staleRows = new ArrayList<>();
        for (Integer row : redstoneTransmitters.keySet()) {
            if (!keepRows.contains(row)) staleRows.add(row);
        }
        for (Integer row : staleRows) removeRedstoneTransmitter(row);
    }

    private void removeRedstoneTransmitter(int row) {
        FccRedstoneTransmitter transmitter = redstoneTransmitters.remove(row);
        if (transmitter == null) return;
        transmitter.markDead();
        if (level == null || level.isClientSide) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, transmitter);
        } catch (RuntimeException | LinkageError ex) {
            DebugLogging.fccDebug("[FCC@{}] Failed to remove redstone link row {}", getBlockPos(), row, ex);
        }
    }

    private void removeAllRedstoneTransmitters() {
        List<Integer> rows = new ArrayList<>(redstoneTransmitters.keySet());
        for (Integer row : rows) removeRedstoneTransmitter(row);
    }

    private void refreshLinkedControllerReceivers(ServerLevel serverLevel) {
        Set<Integer> keepButtons = new LinkedHashSet<>();
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            Couple<RedstoneLinkNetworkHandler.Frequency> key = linkedControllerNetworkKey(button);
            if (key == null) continue;
            keepButtons.add(button);
            FccLinkedControllerReceiver existing = linkedControllerReceivers.get(button);
            if (existing != null && existing.matches(key)) continue;
            if (existing != null) removeLinkedControllerReceiver(button);
            FccLinkedControllerReceiver receiver = new FccLinkedControllerReceiver(button, key);
            linkedControllerReceivers.put(button, receiver);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, receiver);
            } catch (RuntimeException | LinkageError ex) {
                linkedControllerReceivers.remove(button);
                DebugLogging.fccDebug("[FCC@{}] Failed to add linked controller receiver {}", getBlockPos(), button, ex);
            }
        }
        List<Integer> staleButtons = new ArrayList<>();
        for (Integer button : linkedControllerReceivers.keySet()) {
            if (!keepButtons.contains(button)) staleButtons.add(button);
        }
        for (Integer button : staleButtons) removeLinkedControllerReceiver(button);
    }

    private void removeLinkedControllerReceiver(int button) {
        FccLinkedControllerReceiver receiver = linkedControllerReceivers.remove(button);
        if (button >= 0 && button < linkedControllerReceivedStrengths.length && linkedControllerReceivedStrengths[button] != 0) {
            linkedControllerReceivedStrengths[button] = 0;
            linkedControllerSignalDirty = true;
        }
        if (receiver == null) return;
        receiver.markDead();
        if (level == null || level.isClientSide) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, receiver);
        } catch (RuntimeException | LinkageError ex) {
            DebugLogging.fccDebug("[FCC@{}] Failed to remove linked controller receiver {}", getBlockPos(), button, ex);
        }
    }

    private void removeAllLinkedControllerReceivers() {
        List<Integer> buttons = new ArrayList<>(linkedControllerReceivers.keySet());
        for (Integer button : buttons) removeLinkedControllerReceiver(button);
        for (int button = 0; button < linkedControllerReceivedStrengths.length; button++) {
            linkedControllerReceivedStrengths[button] = 0;
        }
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> redstoneNetworkKeyForRow(int row) {
        if (!isRedstonePairComplete(row)) return null;
        ItemStack first = inventory.getStackInSlot(getPropellerSlotIndex(row));
        ItemStack second = inventory.getStackInSlot(getGovernorSlotIndex(row));
        ItemStack firstFrequency = singleFrequencyStack(first);
        ItemStack secondFrequency = singleFrequencyStack(second);
        if (firstFrequency.isEmpty() && !secondFrequency.isEmpty()) {
            firstFrequency = secondFrequency;
            secondFrequency = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(firstFrequency),
                RedstoneLinkNetworkHandler.Frequency.of(secondFrequency));
    }

    private static ItemStack singleFrequencyStack(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> linkedControllerNetworkKey(int button) {
        if (!isLinkedControllerButtonConfigured(button)) return null;
        ItemStack firstFrequency = singleFrequencyStack(linkedControllerInventory.getStackInSlot(getLinkedControllerSlotIndex(button, false)));
        ItemStack secondFrequency = singleFrequencyStack(linkedControllerInventory.getStackInSlot(getLinkedControllerSlotIndex(button, true)));
        if (firstFrequency.isEmpty() && !secondFrequency.isEmpty()) {
            firstFrequency = secondFrequency;
            secondFrequency = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(firstFrequency),
                RedstoneLinkNetworkHandler.Frequency.of(secondFrequency));
    }

    private int redstoneDeviceRow(FccDevice device) {
        if (device.category() != FccDeviceCategory.REDSTONE_LINK) return -1;
        CompoundTag config = device.config();
        return config.contains("Row") ? config.getInt("Row") : -1;
    }

    private boolean mergeRedstoneRouteCommand(FccDevice device, FccOutputType outputType, double routed,
                                              Map<Integer, Integer> redstoneCommands) {
        int row = redstoneDeviceRow(device);
        if (row < 0 || row >= ROW_COUNT || !isRedstonePairComplete(row)) return false;
        int signal = redstoneSignalForRoute(outputType, routed);
        if (signal < 0) return false;
        redstoneCommands.merge(row, signal, (a, b) -> clampSignal(a + b));
        return true;
    }

    private static int redstoneSignalForRoute(FccOutputType outputType, double routed) {
        return switch (outputType) {
            case SIGNAL_LEVEL -> clampSignal((int) Math.round(routed));
            case TOGGLE_STATE, PULSE_EVENT -> routed >= 0.5 ? 15 : 0;
            case RPM_DELTA, RPM_SCALE -> clampSignal((int) Math.round(Math.max(0.0, Math.min(1.0, routed)) * 15.0));
            case ANGLE_TARGET, ANGLE_OFFSET -> -1;
        };
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean hasServerTickElapsed(long lastTick, long intervalTicks) {
        return lastTick == Long.MIN_VALUE || serverTickCount - lastTick >= intervalTicks;
    }

    private interface RouteMutation {
        FccControlRoute apply(FccControlRoute route);
    }

    private boolean mutateWorkbenchRoute(UUID routeId, RouteMutation mutation) {
        for (int controlIndex = 0; controlIndex < controls.size(); controlIndex++) {
            FccCompositeControl control = controls.get(controlIndex);
            List<FccControlRoute> routes = new ArrayList<>(control.routes());
            for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
                FccControlRoute route = routes.get(routeIndex);
                if (!route.id().equals(routeId)) continue;
                routes.set(routeIndex, mutation.apply(route));
                controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
                return true;
            }
        }
        return false;
    }

    private static FccControlRoute copyRoute(FccControlRoute route, FccOutputType outputType, double scale, boolean invert) {
        return new FccControlRoute(route.id(), route.targetGroupId(), outputType, scale, route.offset(),
                route.minClamp(), route.maxClamp(), invert, route.reverseAllowed(),
                route.angleLimitOverride(), route.rateLimitOverride(), route.note(),
                route.routeKind(), route.systemOwned());
    }

    private static FccControlRoute copyRouteWithOutputDefaults(FccControlRoute route, FccOutputType outputType) {
        return new FccControlRoute(route.id(), route.targetGroupId(), outputType,
                defaultRouteScale(outputType), 0.0, defaultMinClamp(outputType), defaultMaxClamp(outputType),
                false, false, null, null, route.note(), route.routeKind(), route.systemOwned());
    }

    private static FccControlRoute copyRouteReverseAllowed(FccControlRoute route, boolean reverseAllowed) {
        return new FccControlRoute(route.id(), route.targetGroupId(), route.outputType(), route.scale(), route.offset(),
                route.minClamp(), route.maxClamp(), route.invert(), reverseAllowed,
                route.angleLimitOverride(), route.rateLimitOverride(), route.note(),
                route.routeKind(), route.systemOwned());
    }

    private List<UUID> collectTemplateDeviceIds(FccDeviceCategory category) {
        List<UUID> deviceIds = new ArrayList<>();
        for (FccDevice device : devices) {
            if (device.category() == category && !deviceIds.contains(device.id())) deviceIds.add(device.id());
        }
        return deviceIds;
    }

    private TemplatePropGroups buildTemplatePropGroups() {
        List<FccDevice> regularProps = new ArrayList<>();
        for (FccDevice device : devices) {
            if (device.category() == FccDeviceCategory.REGULAR_PROP
                    || device.category() == FccDeviceCategory.WHEEL_MOUNT) {
                regularProps.add(device);
            }
        }
        regularProps.sort(Comparator.comparing(FccDevice::label, String.CASE_INSENSITIVE_ORDER));

        List<UUID> left = new ArrayList<>();
        List<UUID> right = new ArrayList<>();
        List<UUID> lift = new ArrayList<>();
        for (FccDevice device : regularProps) {
            int side = templateLateralSide(device);
            if (side < 0) left.add(device.id());
            else if (side > 0) right.add(device.id());
            else lift.add(device.id());
        }

        if ((left.isEmpty() || right.isEmpty()) && regularProps.size() >= 2) {
            left.clear();
            right.clear();
            lift.clear();
            int half = regularProps.size() / 2;
            for (int i = 0; i < regularProps.size(); i++) {
                if (i < half) left.add(regularProps.get(i).id());
                else right.add(regularProps.get(i).id());
            }
        }
        return new TemplatePropGroups(left, right, lift);
    }

    private int templateLateralSide(FccDevice device) {
        Direction forward = noseAxis.asDirection();
        int rightX = -forward.getStepZ();
        int rightZ = forward.getStepX();
        BlockPos origin = getBlockPos();
        int dx = device.primaryPos().getX() - origin.getX();
        int dz = device.primaryPos().getZ() - origin.getZ();
        return Integer.compare(dx * rightX + dz * rightZ, 0);
    }

    private UUID ensureTemplateGroup(String label, List<UUID> deviceIds, int colorArgb, String note) {
        for (int i = 0; i < groups.size(); i++) {
            FccGroup group = groups.get(i);
            if (!group.label().equalsIgnoreCase(label)) continue;
            List<UUID> merged = new ArrayList<>(group.deviceIds());
            for (UUID id : deviceIds) {
                if (!merged.contains(id)) merged.add(id);
            }
            groups.set(i, new FccGroup(group.id(), group.label(), group.colorArgb(), merged,
                    group.hidden(), group.note(), group.zoneKey(), group.systemOwned()));
            return group.id();
        }
        UUID id = UUID.randomUUID();
        groups.add(new FccGroup(id, label, colorArgb, deviceIds, false, note));
        return id;
    }

    private record TemplatePropGroups(List<UUID> left, List<UUID> right, List<UUID> lift) {}

    private int ensureTemplateControl(String label, String[] aliases, FccWidgetType widgetType, boolean springCentered,
                                      double inputMin, double inputMax, double neutralValue,
                                      FccCurveType curveType, String animationTheme) {
        return ensureTemplateControl(label, aliases, widgetType, springCentered, inputMin, inputMax, neutralValue,
                curveType, animationTheme, FccControlRole.CUSTOM, "", false);
    }

    private int ensureTemplateControl(String label, String[] aliases, FccWidgetType widgetType, boolean springCentered,
                                      double inputMin, double inputMax, double neutralValue,
                                      FccCurveType curveType, String animationTheme,
                                      FccControlRole role, @Nullable String zoneKey, boolean systemOwned) {
        for (int i = 0; i < controls.size(); i++) {
            FccCompositeControl control = controls.get(i);
            if (!control.label().equalsIgnoreCase(label)) continue;
            if (systemOwned && !control.systemOwned() && control.role() != role) continue;
            FccCompositeControl updated = copyControlWithSettings(control, label, widgetType, springCentered,
                    inputMin, inputMax, neutralValue, curveType, animationTheme, control.routes());
            updated = copyControlWithMetadata(updated, role, zoneKey, systemOwned, updated.routes());
            controls.set(i, updated);
            controlValues.put(control.id(),
                    clampDouble(controlValues.getOrDefault(control.id(), neutralValue), inputMin, inputMax));
            return i;
        }
        for (String alias : aliases) {
            for (int i = 0; i < controls.size(); i++) {
                FccCompositeControl control = controls.get(i);
                if (!control.label().equalsIgnoreCase(alias)) continue;
                if (systemOwned && !control.systemOwned() && control.role() != role) continue;
                FccCompositeControl updated = copyControlWithSettings(control, label, widgetType, springCentered,
                        inputMin, inputMax, neutralValue, curveType, animationTheme, control.routes());
                updated = copyControlWithMetadata(updated, role, zoneKey, systemOwned, updated.routes());
                controls.set(i, updated);
                controlValues.put(control.id(),
                        clampDouble(controlValues.getOrDefault(control.id(), neutralValue), inputMin, inputMax));
                return i;
            }
        }
        controls.add(new FccCompositeControl(UUID.randomUUID(), label, widgetType, springCentered,
                inputMin, inputMax, neutralValue, 0.08, curveType, animationTheme, false,
                role, zoneKey == null ? "" : zoneKey, systemOwned, List.of()));
        controlValues.put(controls.get(controls.size() - 1).id(), neutralValue);
        return controls.size() - 1;
    }

    private void upsertTemplateRoute(int controlIndex, UUID groupId, FccOutputType outputType,
                                     double scale, double offset, double minClamp, double maxClamp,
                                     boolean reverseAllowed) {
        upsertTemplateRoute(controlIndex, groupId, outputType, scale, offset, minClamp, maxClamp,
            reverseAllowed, false);
    }

    private void upsertTemplateRoute(int controlIndex, UUID groupId, FccOutputType outputType,
                         double scale, double offset, double minClamp, double maxClamp,
                         boolean reverseAllowed, boolean defaultInvert) {
        if (controlIndex < 0 || controlIndex >= controls.size()) return;
        FccCompositeControl control = controls.get(controlIndex);
        List<FccControlRoute> routes = new ArrayList<>(control.routes());
        for (int i = 0; i < routes.size(); i++) {
            FccControlRoute route = routes.get(i);
            if (!route.targetGroupId().equals(groupId) || route.outputType() != outputType) continue;
            routes.set(i, new FccControlRoute(route.id(), groupId, outputType, templateRouteScale(route, scale),
                route.offset(), route.minClamp(), route.maxClamp(), route.invert(), reverseAllowed, route.angleLimitOverride(),
                    route.rateLimitOverride(), "Zeppelin template", FccRouteKind.TEMPLATE_ROUTE, true));
            controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
            return;
        }
        routes.add(new FccControlRoute(UUID.randomUUID(), groupId, outputType, scale, offset,
                minClamp, maxClamp, defaultInvert, reverseAllowed, null, null,
                "Zeppelin template", FccRouteKind.TEMPLATE_ROUTE, true));
        controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
    }

    private static double templateRouteScale(FccControlRoute route, double defaultScale) {
        boolean legacyLiftDefault = Math.abs(defaultScale - 1.0) < 1.0e-9
                && route.outputType() == FccOutputType.RPM_SCALE
                && route.routeKind() == FccRouteKind.TEMPLATE_ROUTE
                && route.systemOwned()
                && Math.abs(route.minClamp()) < 1.0e-9
                && Math.abs(route.maxClamp() - 1.0) < 1.0e-9
                && (Math.abs(route.scale() - 0.60) < 1.0e-9 || Math.abs(route.scale() - 0.55) < 1.0e-9);
        return legacyLiftDefault ? defaultScale : route.scale();
    }

    private boolean defaultRouteInvert(UUID groupId, FccOutputType outputType) {
        if (outputType != FccOutputType.RPM_SCALE) return false;
        FccGroup group = findGroup(groupId);
        return group != null && isVehicleWheelGroup(group);
    }

    private static double defaultRouteScale(FccOutputType outputType) {
        return switch (outputType) {
            case ANGLE_TARGET, ANGLE_OFFSET -> 15.0;
            case SIGNAL_LEVEL -> 15.0;
            case TOGGLE_STATE, PULSE_EVENT -> 1.0;
            case RPM_DELTA, RPM_SCALE -> 0.25;
        };
    }

    private static double defaultMinClamp(FccOutputType outputType) {
        return switch (outputType) {
            case ANGLE_TARGET, ANGLE_OFFSET -> -45.0;
            case SIGNAL_LEVEL -> 0.0;
            case TOGGLE_STATE, PULSE_EVENT -> 0.0;
            case RPM_DELTA, RPM_SCALE -> -1.0;
        };
    }

    private static double defaultMaxClamp(FccOutputType outputType) {
        return switch (outputType) {
            case ANGLE_TARGET, ANGLE_OFFSET -> 45.0;
            case SIGNAL_LEVEL -> 15.0;
            case TOGGLE_STATE, PULSE_EVENT -> 1.0;
            case RPM_DELTA, RPM_SCALE -> 1.0;
        };
    }

    public void refreshDerivedBindings() {
        if (level == null || level.isClientSide) return;
        bindings.clear();
        bindingRows.clear();
        duplicateBindingRowMask = 0;
        net.minecraft.resources.ResourceLocation myDim = level.dimension().location();

        java.util.Set<BlockPos> oldGovs = new java.util.HashSet<>();
        for (BlockPos p : lastBoundGovernors) if (p != null) oldGovs.add(p);
        java.util.Set<BlockPos> newGovs = new java.util.HashSet<>();
        java.util.Set<BlockPos> seenProps = new java.util.HashSet<>();
        java.util.Set<BlockPos> seenGovs = new java.util.HashSet<>();

        for (int row = 0; row < ROW_COUNT; row++) {
            if (isRedstonePairRow(row)) {
                lastBoundGovernors[row] = null;
                continue;
            }
            ItemStack propStack = inventory.getStackInSlot(getPropellerSlotIndex(row));
            ItemStack govStack  = inventory.getStackInSlot(getGovernorSlotIndex(row));
            FlightCardData propData = (propStack.getItem() instanceof PropellerCardItem)
                ? FlightCardData.get(propStack, PropellerCardItem.TAG_KEY) : null;
            FlightCardData govData  = (govStack.getItem() instanceof GovernorCardItem)
                ? FlightCardData.get(govStack, GovernorCardItem.TAG_KEY) : null;

            BlockPos propPos = (propData != null && propData.isLinked()) ? propData.getLinkedPos() : null;
            BlockPos govPos  = (govData  != null && govData.isLinked())  ? govData.getLinkedPos()  : null;

            if (propPos != null && govPos != null) {
                net.minecraft.resources.ResourceLocation pd = propData.getDimension();
                net.minecraft.resources.ResourceLocation gd = govData.getDimension();
                boolean dimOk = (pd == null || pd.equals(myDim)) && (gd == null || gd.equals(myDim))
                                && (pd == null || gd == null || pd.equals(gd));
                if (!dimOk) {
                    propPos = null;
                    govPos = null;
                } else {
                    propPos = resolveLinkedPropPos(propData);
                    govPos = resolveLinkedGovernorPos(govData);
                    if (propPos != null && govPos != null) {
                        BlockPos propKey = propPos.immutable();
                        BlockPos govKey = govPos.immutable();
                        boolean duplicate = seenProps.contains(propKey) || seenGovs.contains(govKey);
                        if (duplicate) {
                            duplicateBindingRowMask |= 1 << row;
                            propPos = null;
                            govPos = null;
                        } else {
                            seenProps.add(propKey);
                            seenGovs.add(govKey);
                            propPos = propKey;
                            govPos = govKey;
                        }
                    }
                }
            }

            BlockPos currentGov = (propPos != null && govPos != null) ? govPos : null;
            lastBoundGovernors[row] = currentGov;
            if (currentGov != null) newGovs.add(currentGov);

            if (propPos != null && govPos != null) {
                boolean calMatches = rowCalibrationValid[row]
                        && propPos.equals(rowCalibratedProps[row])
                        && govPos.equals(rowCalibratedGovernors[row])
                        && Double.isFinite(rowCalibratedK[row])
                        && Math.abs(rowCalibratedK[row]) > 1.0e-6;
                double yaw = (propPos.equals(rowCalibratedProps[row])
                        && govPos.equals(rowCalibratedGovernors[row])
                        && Double.isFinite(rowYawFactor[row]))
                        ? rowYawFactor[row] : 0.0;
                bindings.add(new FccBinding(propPos, govPos, calMatches, rowCalibratedK[row], yaw));
                bindingRows.add(row);
            }
        }

        for (BlockPos p : oldGovs) {
            if (!newGovs.contains(p)) releaseGovernor(p);
        }

        reconcileWorkbenchDevices();
        if (level instanceof ServerLevel serverLevel) {
            refreshRedstoneTransmitters(serverLevel);
        }

        broadcastSnapshot();
    }

    private void reconcileWorkbenchDevices() {
        Map<String, FccDevice> existingByKey = new LinkedHashMap<>();
        Map<Integer, FccDevice> existingByRow = new LinkedHashMap<>();
        for (FccDevice device : devices) {
            existingByKey.put(workbenchDeviceKey(device), device);
            CompoundTag config = device.config();
            if (config.contains("Row")) {
                int row = config.getInt("Row");
                if (row >= 0 && row < ROW_COUNT) existingByRow.putIfAbsent(row, device);
            }
        }

        List<RowSeed> seeds = collectWorkbenchSeeds();
        List<FccDevice> rebuilt = new ArrayList<>(seeds.size() + ROW_COUNT);
        for (int i = 0; i < seeds.size(); i++) {
            RowSeed seed = seeds.get(i);
            String key = mechanicalWorkbenchKey(seed.propPos(), seed.governorPos());
            FccDevice existing = existingByKey.get(key);
            if (existing == null) existing = existingByRow.get(seed.row());
            FccDeviceCategory category = inferWorkbenchCategory(seed, existing);
            boolean sameMode = existing != null && existing.category() != FccDeviceCategory.REDSTONE_LINK;
            CompoundTag config = sameMode ? existing.config().copy() : new CompoundTag();
            config.putString("Mode", "MECHANICAL_PAIR");
            config.putLong("Prop", seed.propPos().asLong());
            config.putLong("Governor", seed.governorPos().asLong());
            config.putInt("Row", seed.row());
            FccDevice device = new FccDevice(
                    existing != null ? existing.id() : UUID.randomUUID(),
                    sameMode ? existing.label() : defaultWorkbenchLabel(category, rebuilt.size() + 1),
                    category,
                    seed.propPos(),
                    seed.governorPos(),
                    true,
                    existing != null ? existing.colorArgb() : defaultWorkbenchColor(category),
                    config);
            rebuilt.add(device);
        }
        for (int row = 0; row < ROW_COUNT; row++) {
            if (!isRedstonePairRow(row) || !isCardRowUsed(row)) continue;
            String key = redstoneWorkbenchKey(row);
            FccDevice existing = existingByKey.get(key);
            if (existing == null) existing = existingByRow.get(row);
            boolean sameMode = existing != null && existing.category() == FccDeviceCategory.REDSTONE_LINK;
            CompoundTag config = sameMode ? existing.config().copy() : new CompoundTag();
            config.putString("Mode", "REDSTONE_PAIR");
            config.putInt("Row", row);
            config.putBoolean("FrequencyReady", isRedstonePairComplete(row));
            FccDevice device = new FccDevice(
                    existing != null ? existing.id() : UUID.randomUUID(),
                    sameMode ? existing.label() : "Link " + (row + 1),
                    FccDeviceCategory.REDSTONE_LINK,
                    getBlockPos(),
                    null,
                    true,
                    existing != null ? existing.colorArgb() : defaultWorkbenchColor(FccDeviceCategory.REDSTONE_LINK),
                    config);
            rebuilt.add(device);
        }
        rebuilt.sort(Comparator.comparing(FccDevice::label, String.CASE_INSENSITIVE_ORDER));
        devices.clear();
        devices.addAll(rebuilt);
        cleanupWorkbenchGroups();
    }

    private List<RowSeed> collectWorkbenchSeeds() {
        List<RowSeed> seeds = new ArrayList<>();
        if (level == null) return seeds;
        net.minecraft.resources.ResourceLocation myDim = level.dimension().location();
        for (int row = 0; row < ROW_COUNT; row++) {
            if (isRedstonePairRow(row)) continue;
            ItemStack propStack = inventory.getStackInSlot(getPropellerSlotIndex(row));
            ItemStack govStack = inventory.getStackInSlot(getGovernorSlotIndex(row));
            FlightCardData propData = (propStack.getItem() instanceof PropellerCardItem)
                    ? FlightCardData.get(propStack, PropellerCardItem.TAG_KEY) : null;
            FlightCardData govData = (govStack.getItem() instanceof GovernorCardItem)
                    ? FlightCardData.get(govStack, GovernorCardItem.TAG_KEY) : null;
            BlockPos propPos = (propData != null && propData.isLinked()) ? propData.getLinkedPos() : null;
            BlockPos govPos = (govData != null && govData.isLinked()) ? govData.getLinkedPos() : null;
            if (propPos == null || govPos == null) continue;
            net.minecraft.resources.ResourceLocation pd = propData.getDimension();
            net.minecraft.resources.ResourceLocation gd = govData.getDimension();
            boolean dimOk = (pd == null || pd.equals(myDim)) && (gd == null || gd.equals(myDim))
                    && (pd == null || gd == null || pd.equals(gd));
            if (!dimOk) continue;
            BlockPos resolvedProp = resolveLinkedPropPos(propData);
            BlockPos resolvedGov = resolveLinkedGovernorPos(govData);
            seeds.add(new RowSeed(row,
                    resolvedProp != null ? resolvedProp : propPos.immutable(),
                    resolvedGov != null ? resolvedGov : govPos.immutable(),
                    resolvedProp != null,
                    resolvedGov != null));
        }
        return seeds;
    }

    private void cleanupWorkbenchGroups() {
        Set<UUID> deviceIds = new LinkedHashSet<>();
        for (FccDevice device : devices) deviceIds.add(device.id());
        List<FccGroup> cleaned = new ArrayList<>(groups.size());
        for (FccGroup group : groups) {
            List<UUID> kept = new ArrayList<>();
            for (UUID id : group.deviceIds()) {
                if (deviceIds.contains(id) && !kept.contains(id)) kept.add(id);
            }
            cleaned.add(group.withDeviceIds(kept));
        }
        groups.clear();
        groups.addAll(cleaned);
    }

    private FccDeviceCategory inferWorkbenchCategory(RowSeed seed, @Nullable FccDevice existing) {
        if (level != null && seed.propResolved()) {
            BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, seed.propPos());
            SableBridge.PropData pd = propBe != null ? SableBridge.readProp(propBe) : null;
            if (pd != null && pd.isGyro()) return FccDeviceCategory.PROP_BEARING;
            if (PropellerCardItem.isWheelMountTarget(level, seed.propPos())) return FccDeviceCategory.WHEEL_MOUNT;
            if (PropellerCardItem.isFinBearingTarget(level, seed.propPos())) return FccDeviceCategory.FIN;
        }
        return existing != null ? existing.category() : FccDeviceCategory.REGULAR_PROP;
    }

    private static String defaultWorkbenchLabel(FccDeviceCategory category, int index) {
        return category.shortLabel() + " " + index;
    }

    private static int defaultWorkbenchColor(FccDeviceCategory category) {
        return switch (category) {
            case REGULAR_PROP -> 0xFF39C8FF;
            case WHEEL_MOUNT -> 0xFF9ACD32;
            case PROP_BEARING -> 0xFFD4AF37;
            case FIN -> 0xFF55E0A0;
            case REDSTONE_LINK -> 0xFFFF6B6B;
        };
    }

    private static String mechanicalWorkbenchKey(BlockPos primary, @Nullable BlockPos secondary) {
        return "M:" + primary.asLong() + ":" + (secondary != null ? secondary.asLong() : 0L);
    }

    private static String redstoneWorkbenchKey(int row) {
        return "R:" + row;
    }

    private static String workbenchDeviceKey(FccDevice device) {
        CompoundTag config = device.config();
        if (device.category() == FccDeviceCategory.REDSTONE_LINK || "REDSTONE_PAIR".equals(config.getString("Mode"))) {
            return redstoneWorkbenchKey(config.contains("Row") ? config.getInt("Row") : -1);
        }
        return mechanicalWorkbenchKey(device.primaryPos(), device.secondaryPos());
    }

    private record RowSeed(int row, BlockPos propPos, BlockPos governorPos, boolean propResolved, boolean governorResolved) {}

    @Nullable
    private BlockPos resolveLinkedPropPos(@Nullable FlightCardData data) {
        BlockPos linkedPos = data != null && data.isLinked() ? data.getLinkedPos() : null;
        return resolveLinkedPropCardPos(linkedPos);
    }

    @Nullable
    private BlockPos resolveLinkedPropCardPos(@Nullable BlockPos rawPos) {
        if (level == null || rawPos == null) return null;

        if (PropellerCardItem.isSupportedTarget(level, rawPos)) {
            return rawPos.immutable();
        }

        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel == null) {
            return null;
        }

        org.joml.Vector3d worldCenter = new org.joml.Vector3d(
            rawPos.getX() + 0.5,
            rawPos.getY() + 0.5,
            rawPos.getZ() + 0.5);
        org.joml.Vector3d localCenter = SableBridge.worldToLocal(subLevel, worldCenter);
        if (localCenter == null
                || !Double.isFinite(localCenter.x)
                || !Double.isFinite(localCenter.y)
                || !Double.isFinite(localCenter.z)) {
            return null;
        }

        BlockPos localPos = BlockPos.containing(localCenter.x, localCenter.y, localCenter.z);
        return PropellerCardItem.isSupportedTarget(level, localPos) ? localPos.immutable() : null;
    }

    @Nullable
    private BlockPos resolveLinkedGovernorPos(@Nullable FlightCardData data) {
        BlockPos linkedPos = data != null && data.isLinked() ? data.getLinkedPos() : null;
        return resolveLinkedPos(linkedPos, be -> be instanceof RpmGovernorBE);
    }

    @Nullable
    private BlockPos resolveLinkedPos(@Nullable BlockPos rawPos, Predicate<BlockEntity> matcher) {
        if (level == null || rawPos == null) return null;

        BlockEntity direct = SableBridge.getBlockEntityOnContraption(level, rawPos);
        if (matchesLinkedTarget(direct, matcher)) {
            return rawPos.immutable();
        }

        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel == null) {
            return null;
        }

        org.joml.Vector3d worldCenter = new org.joml.Vector3d(
            rawPos.getX() + 0.5,
            rawPos.getY() + 0.5,
            rawPos.getZ() + 0.5);
        org.joml.Vector3d localCenter = SableBridge.worldToLocal(subLevel, worldCenter);
        if (localCenter == null
                || !Double.isFinite(localCenter.x)
                || !Double.isFinite(localCenter.y)
                || !Double.isFinite(localCenter.z)) {
            return null;
        }

        BlockPos localPos = BlockPos.containing(localCenter.x, localCenter.y, localCenter.z);
        BlockEntity local = SableBridge.getBlockEntityOnContraption(level, localPos);
        return matchesLinkedTarget(local, matcher) ? localPos.immutable() : null;
    }

    private static boolean matchesLinkedTarget(@Nullable BlockEntity be, Predicate<BlockEntity> matcher) {
        if (be == null) return false;
        try {
            return matcher.test(be);
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    private void releaseGovernor(BlockPos govPos) {
        if (level == null || level.isClientSide || fccUuid == null) return;
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, govPos);
        if (be instanceof RpmGovernorBE rg) {
            try {
                rg.releaseOwnership(fccUuid);
            } catch (Exception e) {
                DebugLogging.fccDebug("[FCC] Failed to release governor at {}", govPos, e);
            }
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    stack);
            }
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public NoseAxis getNoseAxis() { return noseAxis; }
    public FlightMode getMode()   { return mode; }
    @Nullable public BlockPos getGimbalPos()        { return gimbalPos; }
    @Nullable public BlockPos getAltitudeSensorPos(){ return altitudeSensorPos; }
    @Nullable public BlockPos getVelocitySensorPos(){ return velocitySensorPos; }
    @Nullable public BlockPos getNavTablePos()      { return navTablePos; }
    @Nullable public BlockPos getPathfinderComputerPos() { return pathfinderComputerPos; }
    public boolean isPathfinderSystemsEnabled() { return pathfinderSystemsEnabled; }
    public String getPathfinderRouteStatus() { return pathfinderRouteStatus; }
    public boolean isPathfinderRouteAccepted() { return pathfinderRouteAccepted; }
    public String getPathfinderRouteName() { return pathfinderRouteName == null ? "" : pathfinderRouteName; }
    public float getPathfinderRouteConfidence() { return pathfinderRouteConfidence; }
    public int getPathfinderRouteWaypointCount() { return pathfinderRouteWaypointCount; }
    public int getPathfinderRouteWaypointIndex() { return pathfinderRouteWaypointIndex; }
    public double getPathfinderRouteDistance() { return pathfinderRouteDistance; }
    public int getPathfinderRouteHazardFlags() { return pathfinderRouteHazardFlags; }

    public void setManualGimbalPos(@Nullable BlockPos pos) {
        gimbalPos = pos;
        if (sensors != null) {
            sensors.forceScanNow();
            if (level != null) sensors.maybeScan(level.getGameTime());
        }
        setChanged(); broadcastSnapshot();
    }
    public void setManualAltitudePos(@Nullable BlockPos pos) {
        altitudeSensorPos = pos;
        if (sensors != null) {
            sensors.forceScanNow();
            if (level != null) sensors.maybeScan(level.getGameTime());
        }
        setChanged(); broadcastSnapshot();
    }
    public void setManualVelocityPos(@Nullable BlockPos pos) {
        velocitySensorPos = pos;
        if (sensors != null) {
            sensors.forceScanNow();
            if (level != null) sensors.maybeScan(level.getGameTime());
        }
        setChanged(); broadcastSnapshot();
    }
    public void setManualNavTablePos(@Nullable BlockPos pos) {
        navTablePos = pos;
        if (sensors != null) {
            sensors.forceScanNow();
            if (level != null) sensors.maybeScan(level.getGameTime());
        }
        setChanged(); broadcastSnapshot();
    }

    public void ingestPathfinderGuidance(@Nullable PathfinderComputerBE computer, @Nullable BlockPos sourcePos) {
        if (computer == null || sourcePos == null) return;
        if (!this.pathfinderSystemsEnabled) {
            computer.setRemotePaused(true);
            this.clearPathfinderGuidance("Pathfinder paused from FCC");
            return;
        }
        computer.setRemotePaused(false);
        RouteGuidance guidance = computer.activeGuidance();
        boolean pathfinderMode = this.isPathfinderModeActive();
        boolean anyAutoDriveActive = pathfinderMode || isAnyAutoDriveActive();
        boolean hasNavTarget = this.pathfinderNavTarget() != null;
        boolean scannerLive = computer.hasLinkedScanner() && !computer.isLinkedScannerStale();
        boolean accepted = guidance != null && guidance.confidence() > 0f && guidance.waypoints().size() >= 2
            && scannerLive && anyAutoDriveActive && hasNavTarget;
        String computerPlannerStatus = computer.plannerStatus();
        boolean retainedRoute = accepted && computer.plannerStage() == PlannerJobStage.HOLD
            && computerPlannerStatus != null && computerPlannerStatus.startsWith("Keeping last route");
        String nextStatus;
        if (guidance == null || guidance.waypoints().isEmpty()) {
            nextStatus = "Rejected: no route candidate";
        } else if (!anyAutoDriveActive) {
            nextStatus = "Rejected: Auto drive off";
        } else if (!hasNavTarget) {
            nextStatus = "Rejected: no Nav Table target";
        } else if (!computer.hasLinkedScanner()) {
            nextStatus = "Rejected: Pathfinder has no scanner";
        } else if (computer.isLinkedScannerStale()) {
            nextStatus = "Rejected: scanner stale";
        } else if (guidance.confidence() <= 0f) {
            nextStatus = "Rejected: corridor blocked";
        } else if (guidance.waypoints().size() < 2) {
            nextStatus = "Rejected: route too short";
        } else if (retainedRoute) {
            nextStatus = "Accepted: retaining scanner route";
        } else {
            nextStatus = "Accepted: scanner route ready";
        }

        UUID nextComputerId = computer.computerId();
        UUID nextRouteId = accepted ? guidance.routeId() : null;
        String nextRouteName = accepted ? guidance.routeName() : "";
        float nextConfidence = accepted ? guidance.confidence() : 0f;
        int nextWaypointCount = accepted ? guidance.waypoints().size() : 0;
        double nextDistance = accepted ? guidance.estimatedDistance() : 0.0;
        int nextHazards = accepted ? guidance.hazardFlags() : 0;
        List<Vec3> nextWaypoints = accepted ? guidance.waypoints() : List.of();
        List<Float> nextSpeeds = accepted ? pathfinderSpeedsForIngest(guidance.targetSpeeds(), retainedRoute) : List.of();
        boolean routeGeometryChanged = accepted && !this.pathfinderGuidanceWaypoints.equals(nextWaypoints);
        boolean routeChanged = !Objects.equals(nextRouteId, this.pathfinderRouteId) || routeGeometryChanged;
        String storedStatus = accepted && !retainedRoute && !routeChanged && isRuntimePathfinderStatus(this.pathfinderRouteStatus)
            ? this.pathfinderRouteStatus : nextStatus;
        boolean changed = !sourcePos.equals(this.pathfinderComputerPos)
            || !Objects.equals(nextComputerId, this.pathfinderComputerId)
            || !Objects.equals(nextRouteId, this.pathfinderRouteId)
            || !Objects.equals(nextRouteName, this.pathfinderRouteName)
            || !Objects.equals(storedStatus, this.pathfinderRouteStatus)
            || this.pathfinderRouteAccepted != accepted
            || Float.compare(this.pathfinderRouteConfidence, nextConfidence) != 0
            || this.pathfinderRouteWaypointCount != nextWaypointCount
            || Double.compare(this.pathfinderRouteDistance, nextDistance) != 0
            || this.pathfinderRouteHazardFlags != nextHazards
            || !this.pathfinderGuidanceWaypoints.equals(nextWaypoints)
            || !this.pathfinderGuidanceSpeeds.equals(nextSpeeds);

        this.pathfinderComputerPos = sourcePos.immutable();
        this.pathfinderComputerId = nextComputerId;
        this.pathfinderRouteId = nextRouteId;
        this.pathfinderRouteName = nextRouteName == null ? "" : nextRouteName;
        this.pathfinderRouteStatus = storedStatus;
        this.pathfinderRouteAccepted = accepted;
        this.pathfinderRouteConfidence = nextConfidence;
        this.pathfinderRouteWaypointCount = nextWaypointCount;
        this.pathfinderRouteDistance = nextDistance;
        this.pathfinderRouteHazardFlags = nextHazards;
        this.pathfinderRouteLastSeenTick = level == null ? 0L : level.getGameTime();
        this.pathfinderGuidanceWaypoints.clear();
        this.pathfinderGuidanceWaypoints.addAll(nextWaypoints);
        this.pathfinderGuidanceSpeeds.clear();
        this.pathfinderGuidanceSpeeds.addAll(nextSpeeds);
        int previousIndex = this.pathfinderRouteWaypointIndex;
        if (!accepted || this.pathfinderGuidanceWaypoints.isEmpty()) {
            this.pathfinderRouteWaypointIndex = 0;
        } else if (routeChanged) {
            this.pathfinderRouteWaypointIndex = nearestPathfinderWaypointIndex();
        } else {
            this.pathfinderRouteWaypointIndex = Math.max(0, Math.min(this.pathfinderRouteWaypointIndex,
                this.pathfinderGuidanceWaypoints.size() - 1));
        }
        changed |= previousIndex != this.pathfinderRouteWaypointIndex;
        if (level instanceof ServerLevel) {
            computer.markFccGuidanceLink(this.worldPosition, accepted, storedStatus,
                this.pathfinderRouteWaypointIndex, this.pathfinderRouteWaypointCount);
        }
        if (changed) {
            setChanged();
            syncClientVisualState();
            broadcastSnapshot();
        } else {
            setChanged();
        }
    }

    private static List<Float> pathfinderSpeedsForIngest(List<Float> speeds, boolean retainedRoute) {
        if (speeds == null || speeds.isEmpty()) return List.of();
        if (!retainedRoute) return speeds;
        List<Float> capped = new ArrayList<>(speeds.size());
        for (Float speed : speeds) {
            if (speed != null && Float.isFinite(speed) && speed > 0f) {
                capped.add(Math.min(speed, PATHFINDER_RETAINED_ROUTE_SPEED_CAP));
            } else {
                capped.add(speed);
            }
        }
        return List.copyOf(capped);
    }

    private static boolean isRuntimePathfinderStatus(String status) {
        return status != null && (status.startsWith("Executing scanner route") || status.startsWith("Hold:")
            || status.startsWith("Arrived scanner route") || status.startsWith("Reversing scanner route"));
    }

    public void setPathfinderSystemsEnabled(boolean enabled) {
        if (this.pathfinderSystemsEnabled == enabled) {
            this.syncPathfinderRemotePause();
            return;
        }
        this.pathfinderSystemsEnabled = enabled;
        if (!enabled) {
            this.clearPathfinderGuidance("Pathfinder paused from FCC");
        } else if (this.pathfinderComputerPos != null) {
            this.pathfinderRouteStatus = "Pathfinder resumed";
            this.pathfinderRouteLastSeenTick = level == null ? 0L : level.getGameTime();
        }
        this.syncPathfinderRemotePause();
        setChanged();
        broadcastSnapshot();
    }

    private void clearPathfinderGuidance(String status) {
        this.pathfinderRouteId = null;
        this.pathfinderRouteName = "";
        this.pathfinderRouteStatus = status == null ? "Pathfinder paused" : status;
        this.pathfinderRouteAccepted = false;
        this.pathfinderRouteConfidence = 0f;
        this.pathfinderRouteWaypointCount = 0;
        this.pathfinderRouteDistance = 0.0;
        this.pathfinderRouteHazardFlags = 0;
        this.pathfinderRouteWaypointIndex = 0;
        this.pathfinderRouteLastSeenTick = level == null ? 0L : level.getGameTime();
        this.pathfinderGuidanceWaypoints.clear();
        this.pathfinderGuidanceSpeeds.clear();
    }

    private void syncPathfinderRemotePause() {
        PathfinderComputerBE computer = this.resolveLinkedPathfinderComputer();
        if (computer != null) {
            computer.setRemotePaused(!this.pathfinderSystemsEnabled);
        }
    }

    @Nullable
    private PathfinderComputerBE resolveLinkedPathfinderComputer() {
        if (!(level instanceof ServerLevel serverLevel) || pathfinderComputerPos == null) return null;
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(serverLevel, pathfinderComputerPos);
        if (!(blockEntity instanceof PathfinderComputerBE) && serverLevel.isLoaded(pathfinderComputerPos)) {
            blockEntity = serverLevel.getBlockEntity(pathfinderComputerPos);
        }
        return blockEntity instanceof PathfinderComputerBE computer ? computer : null;
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState cur = getBlockState();
        if (!cur.hasProperty(FlightControlComputerBlock.VISUAL_STATE)) return;

        FlightControlComputerBlock.VisualState desired;
        if (mode == FlightMode.OFF) {
            desired = FlightControlComputerBlock.VisualState.OFF;
        } else if (mode == FlightMode.STANDBY) {
            desired = FlightControlComputerBlock.VisualState.STANDBY;
        } else if (sensors == null || !sensors.hasGimbal()) {
            desired = FlightControlComputerBlock.VisualState.WARNING;
        } else {
            desired = FlightControlComputerBlock.VisualState.ACTIVE;
        }

        if (cur.getValue(FlightControlComputerBlock.VISUAL_STATE) != desired) {
            level.setBlock(getBlockPos(),
                    cur.setValue(FlightControlComputerBlock.VISUAL_STATE, desired),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------- Tick ----------------------------

    @Nullable private java.util.Set<SensorRequirement> lastEmittedAvail = null;

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        serverTickCount++;

        if (!mode.isEnabledInCurrentBuild()) {
            mode = FlightMode.STANDBY;
            lastWarning = "Mode disabled in this build";
            setChanged();
        }

        if (derivedDirty) {
            refreshDerivedBindings();
            derivedDirty = false;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        com.createstats.power.FePowerFx.chargeFromNeighbors(level, getBlockPos(), feConsumer);
        final boolean fePowered = feConsumer.serviceTick(mode != FlightMode.OFF);
        final int feTransition = feConsumer.pollTransition();
        com.createstats.power.FePowerFx.playTransition(level, getBlockPos(), feTransition);
        if (feTransition != 0) {
            broadcastSnapshot();
        }
        if (!fePowered) {
            stopWorkbenchGovernors(serverLevel);
            if ((serverTickCount & 3) == 0) {
                broadcastTelemetry();
            }
            return;
        }

        refreshLinkedControllerReceivers(serverLevel);
        boolean linkedSignalChanged = consumeLinkedControllerSignalChange();
        boolean linkedControllerChanged = tickLinkedControllerControls();
        if (linkedSignalChanged || linkedControllerChanged) {
            broadcastSnapshot();
        }

        if (sensors == null) sensors = new com.createstats.flight.control.SensorDiscovery(this);
        sensors.maybeScan(level.getGameTime());
        if (pathfinderComputerPos != null && serverTickCount % 20 == 0) {
            syncPathfinderRemotePause();
        }

        if (mode != FlightMode.OFF && sensors.isWarmedUp()) {
            java.util.Set<SensorRequirement> avail = sensors.validatedSet();
            if (!mode.isAvailable(avail)) {
                sensorLossPersistTicks++;
                if (sensorLossPersistTicks >= SENSOR_LOSS_HYSTERESIS_TICKS) {
                    FlightMode safe = avail.contains(SensorRequirement.GIMBAL)
                        ? FlightMode.STANDBY
                        : FlightMode.OFF;
                    DebugLogging.fccInfo("[FCC@{}] sensor lost (mode {} -> {}); validated={}",
                            getBlockPos(), mode, safe, avail);
                    mode = safe;
                    lastWarning = "Sensor lost, fell back to " + safe;
                    sensorLossPersistTicks = 0;
                    setChanged();
                }
            } else {
                sensorLossPersistTicks = 0;
            }
        } else {
            sensorLossPersistTicks = 0;
        }

        if (sensors != null) {
            java.util.Set<SensorRequirement> nowAvail = sensors.validatedSet();
            if (lastEmittedAvail == null || !lastEmittedAvail.equals(nowAvail)) {
                lastEmittedAvail = java.util.EnumSet.copyOf(nowAvail);
                broadcastSnapshot();
            }
        }

        syncVisualState();
        if (workbenchReconcileDirty) {
            boolean cockpitChanged = runWorkbenchReconcilePass();
            workbenchReconcileDirty = false;
            if (cockpitChanged) {
                setChanged();
                broadcastSnapshot();
            }
        }
        boolean vehicleWorkbenchActive = hasDiagramFamilyPopulation(true);
        if (vehicleWorkbenchActive) {
            int controlCountBefore = controls.size();
            ensureVehicleModeControls();
            if (controls.size() != controlCountBefore) {
                setChanged();
                broadcastSnapshot();
            }
        }
        boolean workbenchHandled = false;
        if (mode != FlightMode.OFF && (mode == FlightMode.STANDBY || vehicleWorkbenchActive) && !controls.isEmpty()) {
            try {
                workbenchHandled = tickWorkbenchRuntime(serverLevel);
            } catch (Throwable t) {
                CreateStats.LOGGER.error("[FCC@{}] Workbench runtime threw - stopping governors for this tick", getBlockPos(), t);
                stopWorkbenchGovernors(serverLevel);
                workbenchHandled = false;
            }
        }
        if (mode == FlightMode.OFF) {
            stopWorkbenchGovernors(serverLevel);
        }
        if (!workbenchHandled && !redstoneTransmitters.isEmpty()) {
            applyRedstoneLinkSignals(serverLevel, Collections.emptyMap());
        }

        if (hasServerTickElapsed(lastRuntimeGateLogTick, 20)) {
            lastRuntimeGateLogTick = serverTickCount;
            DebugLogging.fccDebug("[FCC@{}] runtime gate mode={} controls={} groups={} devices={} vehicleFamily={} workbenchHandled={} redstoneTx={} warning={}",
                    getBlockPos(), mode, controls.size(), groups.size(), devices.size(), vehicleWorkbenchActive,
                    workbenchHandled, redstoneTransmitters.size(), lastWarning);
        }

        if ((serverTickCount & 3) == 0) {
            broadcastTelemetry();
        }
    }

    @Nullable
    public com.createstats.flight.control.SensorDiscovery sensors() { return sensors; }

    private boolean consumeLinkedControllerSignalChange() {
        int inputMask = getLinkedControllerInputMask();
        int signalPack = getLinkedControllerSignalPack();
        boolean changed = linkedControllerSignalDirty
                || inputMask != lastLoggedLinkedControllerInputMask
                || signalPack != lastLoggedLinkedControllerSignalPack;
        if (!changed) return false;
        linkedControllerSignalDirty = false;
        lastLoggedLinkedControllerInputMask = inputMask;
        lastLoggedLinkedControllerSignalPack = signalPack;
        DebugLogging.fccInfo("[FCC@{}] linked-controller rx configuredMask={} inputMask={} signals={} interval={} levels(yaw/thrust/lev)={}/{}/{}",
                getBlockPos(), getLinkedControllerConfiguredMask(), inputMask, linkedControllerSignalSummary(),
                linkedControllerRampIntervalTicks, linkedControllerYawLevel, linkedControllerThrustLevel, linkedControllerLevitationLevel);
        return true;
    }

    private boolean tickLinkedControllerControls() {
        boolean changed = false;
        int inputMask = getLinkedControllerInputMask();
        boolean vehicleMode = isControlActiveAny(DRIVE_MODE_4X4_LABEL, DRIVE_MODE_2X4_FRONT_LABEL, DRIVE_MODE_2X4_REAR_LABEL,
                STEER_MODE_FRONT_LABEL, STEER_MODE_ALL_WHEEL_LABEL, STEER_MODE_CRAB_LABEL,
                DIFF_LOCK_REAR_LABEL, DIFF_LOCK_FULL_LABEL, MID_AXLE_DRIVE_LABEL, STEERING_INVERT_LABEL);
        changed |= tickLinkedControllerActionControls(inputMask, vehicleMode);
        boolean rampTick = serverTickCount % Math.max(1, linkedControllerRampIntervalTicks) == 0;
        if (!rampTick) {
            lastLinkedControllerActionMask = inputMask;
            return changed;
        }
        boolean wheelConfigured = isLinkedControllerButtonConfigured(LINKED_WHEEL_LEFT)
                || isLinkedControllerButtonConfigured(LINKED_WHEEL_RIGHT);
        boolean thrustConfigured = isLinkedControllerButtonConfigured(LINKED_THRUST_UP)
                || isLinkedControllerButtonConfigured(LINKED_THRUST_DOWN);
        boolean levitationConfigured = isLinkedControllerButtonConfigured(LINKED_LEVITATION_UP)
                || isLinkedControllerButtonConfigured(LINKED_LEVITATION_DOWN);

        if (wheelConfigured) {
            int direction = inputDirection(LINKED_WHEEL_RIGHT, LINKED_WHEEL_LEFT);
            int next = stepSpringLevel(linkedControllerYawLevel, direction);
            if (next != linkedControllerYawLevel) {
                linkedControllerYawLevel = next;
                changed |= applyLinkedControllerLevel("Yaw Wheel", linkedControllerYawLevel);
            }
        }
        boolean thrustLockHeld = vehicleMode && isLinkedControllerInputActive(LINKED_LEVITATION_DOWN);
        if (thrustConfigured && !thrustLockHeld) {
            int direction = inputDirection(LINKED_THRUST_UP, LINKED_THRUST_DOWN);
            int next = stepSpringLevel(linkedControllerThrustLevel, direction);
            if (next != linkedControllerThrustLevel) {
                linkedControllerThrustLevel = next;
                changed |= applyLinkedControllerLevel("Thrust Lever", linkedControllerThrustLevel);
            }
        }
        if (levitationConfigured) {
            int direction = inputDirection(LINKED_LEVITATION_UP, LINKED_LEVITATION_DOWN);
            int next = stepStickyLevel(linkedControllerLevitationLevel, direction);
            if (next != linkedControllerLevitationLevel) {
                linkedControllerLevitationLevel = next;
                changed |= applyLinkedControllerLevel("Levitation Dial", linkedControllerLevitationLevel);
            }
        }
        if (changed) {
            DebugLogging.fccInfo("[FCC@{}] linked-controller ramp inputs={} levels(yaw/thrust/lev)={}/{}/{} values(yaw/thrust/lev/bay/drop)={}/{}/{}/{}/{}",
                    getBlockPos(), linkedControllerSignalSummary(), linkedControllerYawLevel, linkedControllerThrustLevel,
                    linkedControllerLevitationLevel, debugControlValue("Yaw Wheel"), debugControlValue("Thrust Lever"),
                    debugControlValue("Levitation Dial"), debugControlValue(BAY_OPEN_LABEL), debugControlValue(BOMB_DROP_LABEL));
        }
        lastLinkedControllerActionMask = inputMask;
        return changed;
    }

    private boolean tickLinkedControllerActionControls(int inputMask, boolean vehicleMode) {
        boolean changed = false;
        if (vehicleMode && isLinkedControllerButtonConfigured(LINKED_LEVITATION_UP)) {
            boolean active = (inputMask & (1 << LINKED_LEVITATION_UP)) != 0;
            boolean wasActive = (lastLinkedControllerActionMask & (1 << LINKED_LEVITATION_UP)) != 0;
            if (active && !wasActive) changed |= toggleControlByLabel(MID_AXLE_DRIVE_LABEL);
        }
        if (isLinkedControllerButtonConfigured(LINKED_BAY_OPEN)) {
            boolean active = (inputMask & (1 << LINKED_BAY_OPEN)) != 0;
            boolean wasActive = (lastLinkedControllerActionMask & (1 << LINKED_BAY_OPEN)) != 0;
            if (active && !wasActive) changed |= toggleControlByLabel(BAY_OPEN_LABEL);
        }
        if (isLinkedControllerButtonConfigured(LINKED_BOMB_DROP)) {
            double target = ((inputMask & (1 << LINKED_BOMB_DROP)) != 0 && canReleaseBombDrop()) ? 1.0 : 0.0;
            changed |= setControlValueByLabel(BOMB_DROP_LABEL, target);
        }
        return changed;
    }

    private boolean canReleaseBombDrop() {
        FccCompositeControl safety = findBombSafetyControl();
        boolean armed = safety != null && controlValues.getOrDefault(safety.id(), safety.neutralValue()) >= 0.5;
        if (!armed) return false;
        FccCompositeControl bay = findBayOpenControl();
        if (bay == null || bay.routes().isEmpty()) return true;
        return controlValues.getOrDefault(bay.id(), bay.neutralValue()) >= 0.5;
    }

    private String linkedControllerSignalSummary() {
        StringBuilder builder = new StringBuilder();
        for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            if (button > 0) builder.append('/');
            builder.append(linkedControllerButtonLabel(button)).append('=').append(clampSignal(linkedControllerReceivedStrengths[button]));
        }
        return builder.toString();
    }

    private String debugControlValue(String label) {
        FccCompositeControl control = findControlByLabel(label);
        if (control == null) return "missing";
        return String.format(java.util.Locale.ROOT, "%.3f", controlValues.getOrDefault(control.id(), control.neutralValue()));
    }

    private static String linkedControllerButtonLabel(int button) {
        return switch (button) {
            case LINKED_THRUST_UP -> "W";
            case LINKED_THRUST_DOWN -> "S";
            case LINKED_WHEEL_LEFT -> "A";
            case LINKED_WHEEL_RIGHT -> "D";
            case LINKED_LEVITATION_UP -> "SPACE";
            case LINKED_LEVITATION_DOWN -> "SHIFT";
            case LINKED_BAY_OPEN -> "BAY";
            case LINKED_BOMB_DROP -> "DROP";
            default -> "B" + button;
        };
    }

    private int inputDirection(int positiveButton, int negativeButton) {
        int direction = 0;
        if (isLinkedControllerInputActive(positiveButton)) direction++;
        if (isLinkedControllerInputActive(negativeButton)) direction--;
        return Integer.compare(direction, 0);
    }

    private boolean isLinkedControllerInputActive(int button) {
        return button >= 0 && button < linkedControllerReceivedStrengths.length
                && linkedControllerReceivedStrengths[button] > 0;
    }

    private static int stepSpringLevel(int current, int direction) {
        if (direction > 0) return Math.min(15, current + 1);
        if (direction < 0) return Math.max(-15, current - 1);
        return approachZero(current);
    }

    private static int stepStickyLevel(int current, int direction) {
        if (direction > 0) return Math.min(15, current + 1);
        if (direction < 0) return Math.max(-15, current - 1);
        return current;
    }

    private static int approachZero(int value) {
        if (value > 0) return value - 1;
        if (value < 0) return value + 1;
        return 0;
    }

    private boolean applyLinkedControllerLevel(String controlLabel, int level) {
        FccCompositeControl control = findControlByLabel(controlLabel);
        if (control == null) return false;
        double normalized = Math.max(-15, Math.min(15, level)) / 15.0;
        double target = normalized >= 0.0
                ? control.neutralValue() + normalized * (control.inputMax() - control.neutralValue())
                : control.neutralValue() + normalized * (control.neutralValue() - control.inputMin());
        target = Math.max(control.inputMin(), Math.min(control.inputMax(), target));
        double current = controlValues.getOrDefault(control.id(), control.neutralValue());
        if (Math.abs(current - target) < 1.0e-6) return false;
        controlValues.put(control.id(), target);
        setChanged();
        return true;
    }

    private boolean setControlValueByLabel(String controlLabel, double value) {
        FccCompositeControl control = findControlByLabel(controlLabel);
        if (control == null) return false;
        double target = clampDouble(value, control.inputMin(), control.inputMax());
        double current = controlValues.getOrDefault(control.id(), control.neutralValue());
        if (Math.abs(current - target) < 1.0e-6) return false;
        controlValues.put(control.id(), target);
        setChanged();
        return true;
    }

    private boolean toggleControlByLabel(String controlLabel) {
        FccCompositeControl control = findControlByLabel(controlLabel);
        if (control == null) return false;
        double current = controlValues.getOrDefault(control.id(), control.neutralValue());
        double midpoint = control.inputMin() + (control.inputMax() - control.inputMin()) * 0.5;
        double target = current >= midpoint ? control.inputMin() : control.inputMax();
        controlValues.put(control.id(), target);
        setChanged();
        return true;
    }

    @Nullable
    private FccCompositeControl findControlByLabel(String label) {
        String token = normalizeDiagramToken(label);
        for (FccCompositeControl control : controls) {
            if (normalizeDiagramToken(control.label()).equals(token)) return control;
        }
        return null;
    }

    @Nullable
    private FccCompositeControl findControlByAnyLabel(String... labels) {
        for (String label : labels) {
            FccCompositeControl exact = findControlByLabel(label);
            if (exact != null) return exact;
        }
        List<String> needles = new ArrayList<>();
        for (String label : labels) {
            needles.add(normalizeDiagramToken(label));
        }
        for (FccCompositeControl control : controls) {
            String token = normalizeDiagramToken(control.label());
            for (String needle : needles) {
                if (!needle.isEmpty() && token.contains(needle)) return control;
            }
        }
        return null;
    }

    @Nullable
    private FccCompositeControl findControlByRole(FccControlRole role) {
        for (FccCompositeControl control : controls) {
            if (control.role() == role) return control;
        }
        return null;
    }

    private boolean tickWorkbenchRuntime(ServerLevel serverLevel) {
        Map<BlockPos, Integer> rpmCommands = new LinkedHashMap<>();
        Map<Integer, Integer> redstoneCommands = new LinkedHashMap<>();
        Set<BlockPos> reverseAllowedGovernors = new LinkedHashSet<>();
        boolean flipOverride = updateFlipPrevention(serverLevel);
        boolean vehicleRuntime = hasDiagramFamilyPopulation(true);
        boolean airshipRuntime = hasDiagramFamilyPopulation(false) && !vehicleRuntime;
        if (airshipRuntime) {
            applyAirshipAutoDriveAssist(flipOverride);
        } else {
            lastAirshipAutoTargetDistance = Double.NaN;
        }
        int activeRoutes = 0;
        int tiltWrites = 0;
        int signalWrites = 0;
        int unsupportedRoutes = 0;

        for (FccCompositeControl control : controls) {
            if (isDiagramZoneSwitchControl(control)) continue;
            if (looksBombDropLabel(control.label()) && !canReleaseBombDrop()) continue;
            double input = shapedControlInput(control);
            if (Math.abs(input) < 1.0e-6) continue;
            for (FccControlRoute route : control.routes()) {
                FccGroup group = findGroup(route.targetGroupId());
                if (group == null) continue;
                if (vehicleRuntime && isVehicleWheelGroup(group)) continue;
                if (!isZoneRouteEnabled(group)) continue;
                if (flipOverride && !isFlipPreventionLiftRoute(group, route)) continue;
                double routed = routeValue(input, route);
                if (Math.abs(routed) < 1.0e-6 && route.outputType() != FccOutputType.TOGGLE_STATE) continue;
                activeRoutes++;
                for (UUID deviceId : group.deviceIds()) {
                    FccDevice device = findDevice(deviceId);
                    if (device == null || !device.enabled()) continue;
                    switch (route.outputType()) {
                        case RPM_DELTA, RPM_SCALE -> {
                            if (device.category() == FccDeviceCategory.REDSTONE_LINK) {
                                if (mergeRedstoneRouteCommand(device, route.outputType(), routed, redstoneCommands)) {
                                    signalWrites++;
                                } else {
                                    unsupportedRoutes++;
                                }
                                continue;
                            }
                            BlockPos governorPos = device.secondaryPos();
                            if (governorPos == null) {
                                unsupportedRoutes++;
                                continue;
                            }
                            int target = clampRpm((int) Math.round(routed * FlightConfig.RPM_CLAMP));
                            rpmCommands.merge(governorPos, target, (a, b) -> clampRpm(a + b));
                            if (route.reverseAllowed()) reverseAllowedGovernors.add(governorPos);
                        }
                        case ANGLE_TARGET, ANGLE_OFFSET -> {
                            if (device.category() != FccDeviceCategory.PROP_BEARING || device.primaryPos() == null) {
                                unsupportedRoutes++;
                                continue;
                            }
                            if (writeGyroRoute(serverLevel, device.primaryPos(), route.outputType(), routed)) {
                                tiltWrites++;
                            } else {
                                unsupportedRoutes++;
                            }
                        }
                        case SIGNAL_LEVEL, TOGGLE_STATE, PULSE_EVENT -> {
                            if (mergeRedstoneRouteCommand(device, route.outputType(), routed, redstoneCommands)) {
                                signalWrites++;
                            } else {
                                unsupportedRoutes++;
                            }
                        }
                    }
                }
            }
        }

        if (vehicleRuntime) {
            activeRoutes += applyVehicleWheelCommands(rpmCommands, redstoneCommands, reverseAllowedGovernors);
        }

        if (flipOverride) {
            activeRoutes += forceFlipPreventionLift(rpmCommands);
        }

        boolean redstoneChanged = applyRedstoneLinkSignals(serverLevel, redstoneCommands);

        int sent = 0;
        int rejected = 0;
        UUID id = getOrCreateUuid();
        List<FccBinding> bindingList = getBindings();
        Map<BlockPos, Integer> finalGovernorTargets = new LinkedHashMap<>();
        for (BlockPos governorPos : collectWorkbenchGovernorPositions()) {
            finalGovernorTargets.put(governorPos, 0);
        }
        for (Map.Entry<BlockPos, Integer> entry : rpmCommands.entrySet()) {
            finalGovernorTargets.put(entry.getKey(), entry.getValue());
        }
        for (int i = 0; i < bindingList.size(); i++) {
            FccBinding binding = bindingList.get(i);
            BlockPos govPos = binding.governor();
            int target = finalGovernorTargets.getOrDefault(govPos, 0);
            int overrideTarget = getEffectiveRpmCommand(i);
            if (overrideTarget >= 0) {
                target = overrideTarget;
            }
            finalGovernorTargets.put(govPos, target);
        }

        boolean syncTick = lastGovernorCommandSyncTick == Long.MIN_VALUE
                || serverTickCount - lastGovernorCommandSyncTick >= GOVERNOR_COMMAND_SYNC_TICKS;
        for (Map.Entry<BlockPos, Integer> entry : finalGovernorTargets.entrySet()) {
            BlockPos govPos = entry.getKey();
            BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, govPos);
            if (!(be instanceof RpmGovernorBE governor)) continue;
            int target = entry.getValue();
            if (target < 0 && !reverseAllowedGovernors.contains(govPos)) target = 0;

            Integer previous = lastSentGovernorTargets.get(govPos);
            boolean changed = previous == null || previous != target;
            if (!changed && !syncTick) {
                continue;
            }

            if (governor.setCommand(getBlockPos(), id, target)) {
                sent++;
                lastSentGovernorTargets.put(govPos, target);
            } else {
                rejected++;
            }
        }
        if (syncTick) {
            lastGovernorCommandSyncTick = serverTickCount;
        }
        lastSentGovernorTargets.keySet().retainAll(finalGovernorTargets.keySet());

        if (hasServerTickElapsed(lastWorkbenchRuntimeLogTick, 20)) {
            lastWorkbenchRuntimeLogTick = serverTickCount;
            DebugLogging.fccDebug("[FCC@{}] workbench runtime routes={} governors={} tiltWrites={} signalWrites={} unsupported={} rejected={} flipOverride={} controls(yaw/thrust/lev)={}/{}/{} rpmCommands={} redstoneCommands={} reverseAllowed={}",
                getBlockPos(), activeRoutes, sent, tiltWrites, signalWrites, unsupportedRoutes, rejected, flipOverride,
                debugControlValue("Yaw Wheel"), debugControlValue("Thrust Lever"), debugControlValue("Levitation Dial"),
                rpmCommands, redstoneCommands, reverseAllowedGovernors.size());
            if (vehicleRuntime) {
                DebugLogging.fccDebug("[FCC@{}] vehicle runtime {}", getBlockPos(), lastVehicleRuntimeDebug);
            }
            if (unsupportedRoutes > 0 || rejected > 0) {
                lastWarning = "Workbench runtime: " + unsupportedRoutes + " unsupported, " + rejected + " rejected";
                broadcastSnapshot();
            }
        }
        return true;
    }

    private int applyVehicleWheelCommands(Map<BlockPos, Integer> rpmCommands,
                                          Map<Integer, Integer> redstoneCommands,
                                          Set<BlockPos> reverseAllowedGovernors) {
        clearVehicleWheelTelemetry();
        FccCompositeControl thrustControl = findControlByRole(FccControlRole.THRUST);
        if (thrustControl == null) {
            thrustControl = findControlByAnyLabel("Thrust Lever", "Thrust", "Throttle", "Pedal");
        }
        FccCompositeControl yawControl = findControlByRole(FccControlRole.YAW);
        if (yawControl == null) {
            yawControl = findControlByAnyLabel("Yaw Wheel", "Yaw", "Steering Wheel", "Steering");
        }
        if (thrustControl == null || yawControl == null) {
            lastVehicleRuntimeDebug = "controls missing thrust=" + (thrustControl != null) + " yaw=" + (yawControl != null);
            return 0;
        }

        double thrustInput = normalizeControlSigned(thrustControl);
        double steeringInput = normalizeControlSigned(yawControl);
        VehicleAutoDriveAssist autoDriveAssist = computeVehicleAutoDriveAssist();
        if (autoDriveAssist.active()) {
            steeringInput = autoDriveAssist.steeringInput();
            thrustInput = autoDriveAssist.throttleInput();
            lastVehicleAutoTargetDistance = autoDriveAssist.distanceToTarget();
        } else {
            lastVehicleAutoTargetDistance = Double.NaN;
        }
        if (isVehicleControlActive(STEERING_INVERT_LABEL, "Steering Invert", "Steer Invert", "Invert Steering")) {
            steeringInput = -steeringInput;
        }
        int baseRpm = clampVehicleRpm((int) Math.round(thrustInput * VEHICLE_MAX_WHEEL_RPM));

        boolean driveFrontMode = isVehicleControlActive(DRIVE_MODE_2X4_FRONT_LABEL,
                "2x4 Front", "Front Drive", "FWD", "Front Wheel Drive");
        boolean driveRearMode = isVehicleControlActive(DRIVE_MODE_2X4_REAR_LABEL,
                "2x4 Rear", "Rear Drive", "RWD", "Rear Wheel Drive");
        boolean drive4x4Mode = isVehicleControlActive(DRIVE_MODE_4X4_LABEL,
                "4x4", "AWD", "All Wheel Drive", "Four Wheel Drive");
        boolean driveFrontOnly = !drive4x4Mode && driveFrontMode && !driveRearMode;
        boolean driveRearOnly = !drive4x4Mode && driveRearMode && !driveFrontMode;
        boolean midDriveEnabled = isVehicleControlActive(MID_AXLE_DRIVE_LABEL,
                "Mid Axle Drive", "Mid Axle", "Middle Axle", "Middle Drive");
        boolean frontSteerMode = isVehicleControlActive(STEER_MODE_FRONT_LABEL,
            "Front Steer", "Normal Steering", "Front Steering");
        boolean allWheelSteer = isVehicleControlActive(STEER_MODE_ALL_WHEEL_LABEL,
                "All Wheel Steer", "4WS", "Rear Steer", "Four Wheel Steer");
        boolean crabSteer = isVehicleControlActive(STEER_MODE_CRAB_LABEL,
                "Crab Turn", "Crab Steering", "Crab");
        boolean rearDiffLock = isVehicleControlActive(DIFF_LOCK_REAR_LABEL,
                "Diff Rear", "Rear Diff Lock", "DIFF R");
        boolean fullDiffLock = isVehicleControlActive(DIFF_LOCK_FULL_LABEL,
                "Diff Full", "Full Diff Lock", "DIFF F");

        boolean driveFront = !driveRearOnly;
        boolean driveRear = !driveFrontOnly;
        boolean driveMid = midDriveEnabled && (driveFront || driveRear);

        VehicleSteeringMode steeringMode;
        if (crabSteer && !allWheelSteer && !frontSteerMode) {
            steeringMode = VehicleSteeringMode.CRAB;
        } else if (allWheelSteer && !crabSteer && !frontSteerMode) {
            steeringMode = VehicleSteeringMode.ALL_WHEEL;
        } else if (frontSteerMode && !allWheelSteer && !crabSteer) {
            steeringMode = VehicleSteeringMode.FRONT;
        } else if (crabSteer) {
            steeringMode = VehicleSteeringMode.CRAB;
        } else if (allWheelSteer) {
            steeringMode = VehicleSteeringMode.ALL_WHEEL;
        } else {
            steeringMode = VehicleSteeringMode.FRONT;
        }
        lastVehicleSteerModeOrdinal = steeringMode.ordinal();

        double frontSteerSigned;
        double rearSteerSigned;
        if (steeringMode == VehicleSteeringMode.CRAB) {
            frontSteerSigned = steeringInput * VEHICLE_CRAB_SIGNAL_GAIN;
            rearSteerSigned = steeringInput * VEHICLE_CRAB_SIGNAL_GAIN;
        } else if (steeringMode == VehicleSteeringMode.ALL_WHEEL) {
            frontSteerSigned = steeringInput * VEHICLE_ALL_WHEEL_FRONT_SIGNAL_GAIN;
            rearSteerSigned = -steeringInput * VEHICLE_ALL_WHEEL_REAR_SIGNAL_GAIN;
        } else {
            frontSteerSigned = steeringInput * VEHICLE_FRONT_STEER_SIGNAL_GAIN;
            rearSteerSigned = 0.0;
        }

        boolean midSteerable = steeringMode == VehicleSteeringMode.CRAB;
        double midSteerSigned = midSteerable ? frontSteerSigned : 0.0;

        int applied = 0;
        applied += applyVehicleWheelZoneCommand("FRONT_LEFT_WHEEL", thrustControl, driveFront, baseRpm, frontSteerSigned, true,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);
        applied += applyVehicleWheelZoneCommand("FRONT_RIGHT_WHEEL", thrustControl, driveFront, baseRpm, frontSteerSigned, true,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);
        applied += applyVehicleWheelZoneCommand("MID_LEFT_WHEEL", thrustControl, driveMid, baseRpm, midSteerSigned, midSteerable,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);
        applied += applyVehicleWheelZoneCommand("MID_RIGHT_WHEEL", thrustControl, driveMid, baseRpm, midSteerSigned, midSteerable,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);
        applied += applyVehicleWheelZoneCommand("REAR_LEFT_WHEEL", thrustControl, driveRear, baseRpm, rearSteerSigned, true,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);
        applied += applyVehicleWheelZoneCommand("REAR_RIGHT_WHEEL", thrustControl, driveRear, baseRpm, rearSteerSigned, true,
            rpmCommands, redstoneCommands, reverseAllowedGovernors);

        boolean frontSteerReady = vehicleZoneSteeringReady("FRONT_LEFT_WHEEL") || vehicleZoneSteeringReady("FRONT_RIGHT_WHEEL");
        boolean rearSteerReady = vehicleZoneSteeringReady("REAR_LEFT_WHEEL") || vehicleZoneSteeringReady("REAR_RIGHT_WHEEL");
        boolean steerAssist = Math.abs(steeringInput) > 0.05;
        boolean steerFallback = steerAssist && !frontSteerReady && !rearSteerReady;
        boolean applyRpmSteerAssist = steerFallback;
        if (applyRpmSteerAssist) {
            int steerDelta = clampVehicleRpm((int) Math.round(steeringInput * VEHICLE_MAX_WHEEL_RPM * 0.35));
            if (!fullDiffLock) {
                applied += applyVehicleWheelZoneDelta("FRONT_LEFT_WHEEL", -steerDelta, rpmCommands, reverseAllowedGovernors);
                applied += applyVehicleWheelZoneDelta("FRONT_RIGHT_WHEEL", steerDelta, rpmCommands, reverseAllowedGovernors);
            }
            if (!rearDiffLock && !fullDiffLock) {
                applied += applyVehicleWheelZoneDelta("REAR_LEFT_WHEEL", -steerDelta, rpmCommands, reverseAllowedGovernors);
                applied += applyVehicleWheelZoneDelta("REAR_RIGHT_WHEEL", steerDelta, rpmCommands, reverseAllowedGovernors);
            }
        }

        if (fullDiffLock) {
            applied += equalizeVehicleWheelPair("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL", rpmCommands, reverseAllowedGovernors);
            applied += equalizeVehicleWheelPair("REAR_LEFT_WHEEL", "REAR_RIGHT_WHEEL", rpmCommands, reverseAllowedGovernors);
        } else if (rearDiffLock) {
            applied += equalizeVehicleWheelPair("REAR_LEFT_WHEEL", "REAR_RIGHT_WHEEL", rpmCommands, reverseAllowedGovernors);
        }

        int fallbackGovernors = 0;
        if (applied == 0 && baseRpm != 0) {
            for (FccBinding binding : getBindings()) {
                rpmCommands.put(binding.governor(), baseRpm);
                reverseAllowedGovernors.add(binding.governor());
                fallbackGovernors++;
            }
            applied = fallbackGovernors;
        }

        captureVehicleWheelTelemetry("FRONT_LEFT_WHEEL", rpmCommands, frontSteerSigned, true);
        captureVehicleWheelTelemetry("FRONT_RIGHT_WHEEL", rpmCommands, frontSteerSigned, true);
        captureVehicleWheelTelemetry("MID_LEFT_WHEEL", rpmCommands, midSteerSigned, midSteerable);
        captureVehicleWheelTelemetry("MID_RIGHT_WHEEL", rpmCommands, midSteerSigned, midSteerable);
        captureVehicleWheelTelemetry("REAR_LEFT_WHEEL", rpmCommands, rearSteerSigned, true);
        captureVehicleWheelTelemetry("REAR_RIGHT_WHEEL", rpmCommands, rearSteerSigned, true);

        int flGov = vehicleZoneGovernorCount("FRONT_LEFT_WHEEL");
        int frGov = vehicleZoneGovernorCount("FRONT_RIGHT_WHEEL");
        int mlGov = vehicleZoneGovernorCount("MID_LEFT_WHEEL");
        int mrGov = vehicleZoneGovernorCount("MID_RIGHT_WHEEL");
        int rlGov = vehicleZoneGovernorCount("REAR_LEFT_WHEEL");
        int rrGov = vehicleZoneGovernorCount("REAR_RIGHT_WHEEL");
        UUID flGroup = findVehicleZoneGroupId("FRONT_LEFT_WHEEL");
        UUID frGroup = findVehicleZoneGroupId("FRONT_RIGHT_WHEEL");
        UUID mlGroup = findVehicleZoneGroupId("MID_LEFT_WHEEL");
        UUID mrGroup = findVehicleZoneGroupId("MID_RIGHT_WHEEL");
        UUID rlGroup = findVehicleZoneGroupId("REAR_LEFT_WHEEL");
        UUID rrGroup = findVehicleZoneGroupId("REAR_RIGHT_WHEEL");
        lastVehicleRuntimeDebug = String.format(java.util.Locale.ROOT,
            "thrust=%.2f yaw=%.2f rpm=%d applied=%d fallback=%d steerAssist=%s steerFallback=%s auto=%s autoDbg={%s} mode=%s modeSrc[front=%s all=%s crab=%s] drive[4x4=%s frontOnly=%s rearOnly=%s mid=%s front=%s rear=%s] diff[rear=%s full=%s] rpmCmd=%d redstoneCmd=%d gov[FL=%d FR=%d ML=%d MR=%d RL=%d RR=%d] steerReady[front=%s rear=%s] invert[FL=%s FR=%s ML=%s MR=%s RL=%s RR=%s] zone[FL:%s FR:%s ML:%s MR:%s RL:%s RR:%s]",
            thrustInput, steeringInput, baseRpm, applied, fallbackGovernors, steerAssist, steerFallback,
                autoDriveAssist.active() && autoDriveAssist.source() != null ? autoDriveAssist.source().name() : "manual",
                lastVehicleAutoDebug,
                steeringMode.name(), frontSteerMode, allWheelSteer, crabSteer,
                drive4x4Mode, driveFrontOnly, driveRearOnly, midDriveEnabled, driveFront, driveRear,
                rearDiffLock, fullDiffLock,
                rpmCommands.size(), redstoneCommands.size(), flGov, frGov, mlGov, mrGov, rlGov, rrGov, frontSteerReady, rearSteerReady,
                isVehicleWheelInvertedNullable(flGroup, thrustControl),
                isVehicleWheelInvertedNullable(frGroup, thrustControl),
                isVehicleWheelInvertedNullable(mlGroup, thrustControl),
                isVehicleWheelInvertedNullable(mrGroup, thrustControl),
                isVehicleWheelInvertedNullable(rlGroup, thrustControl),
                isVehicleWheelInvertedNullable(rrGroup, thrustControl),
                vehicleZoneDebugLine("FRONT_LEFT_WHEEL", thrustControl),
                vehicleZoneDebugLine("FRONT_RIGHT_WHEEL", thrustControl),
                vehicleZoneDebugLine("MID_LEFT_WHEEL", thrustControl),
                vehicleZoneDebugLine("MID_RIGHT_WHEEL", thrustControl),
                vehicleZoneDebugLine("REAR_LEFT_WHEEL", thrustControl),
                vehicleZoneDebugLine("REAR_RIGHT_WHEEL", thrustControl));
        return applied;
    }

    private double vehicleMinTurnRadiusBlocks() {
        double wheelbase = vehicleWheelbaseBlocks();
        double track = vehicleTrackWidthBlocks();
        double base = Double.isFinite(wheelbase) && wheelbase > 0.5 ? wheelbase * 1.3 : 4.0;
        if (Double.isFinite(track) && track > 0.5) base = Math.max(base, track * 0.5 + 2.0);
        return clampDouble(base, 3.0, 8.0);
    }

    private double pathfinderPurePursuitSteering(double headingErrorDeg, double lookahead) {
        return com.createstats.flight.runtime.PurePursuit.steer(headingErrorDeg, lookahead,
            vehicleMinTurnRadiusBlocks(), VEHICLE_AUTO_STEER_DEADZONE_DEG);
    }
    private VehicleAutoDriveAssist computeVehicleAutoDriveAssist() {
        boolean masterAuto = isVehicleControlActive(AUTO_DRIVE_LABEL, "Auto Drive", "Self Drive", "Self Driving");
        boolean pathfinderAuto = isPathfinderModeActive();
        boolean navAuto = isNavTableModeActive();
        boolean autoEnabled = masterAuto || pathfinderAuto || navAuto;
        if (!autoEnabled) {
            lastVehicleAutoSteeringInput = 0.0;
            resetVehicleAutoStuckState();
            updateVehicleAutoDebug("auto=off computer=" + formatBlockPos(pathfinderComputerPos)
                + " nav=" + formatBlockPos(sensors != null ? sensors.effectiveNavTablePos() : navTablePos)
                + " pfAccepted=" + pathfinderRouteAccepted + " pfStatus=" + pathfinderRouteStatus);
            return VehicleAutoDriveAssist.inactive();
        }
        VehicleAutoDriveTarget target = resolveAutoDriveTarget();
        if (target == null) {
            String holdReason = autoHoldReason();
            updateVehicleAutoDebug("auto=no-target reason=" + holdReason
                + " current=" + formatVector(currentContraptionWorldPosition())
                + " computer=" + formatBlockPos(pathfinderComputerPos)
                + " nav=" + formatBlockPos(sensors != null ? sensors.effectiveNavTablePos() : navTablePos)
                + " pfAccepted=" + pathfinderRouteAccepted
                + " pfWp=" + pathfinderGuidanceWaypoints.size()
                + " pfAge=" + pathfinderGuidanceAgeTicks()
                + " pfStatus=" + pathfinderRouteStatus);
            if (pathfinderAuto) reportPathfinderSafetyHold("Hold: " + holdReason);
            lastVehicleAutoSteeringInput = 0.0;
            resetVehicleAutoStuckState();
            return VehicleAutoDriveAssist.inactive();
        }

        org.joml.Vector3d currentPos = vehicleNavigationWorldPosition();
        double dx = target.position().x - currentPos.x;
        double dz = target.position().z - currentPos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= VEHICLE_AUTO_ARRIVAL_RADIUS) {
            double holdSpeed = currentHorizontalSpeed();
            double holdForwardSpeed = currentForwardSpeed(currentHeadingDeg());
            double holdThrottle = autoHoldBrakeThrottle(holdSpeed, holdForwardSpeed);
            updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
                "auto=hold-arrived source=%s current=%s target=%s dist=%.2f speed=%.2f forwardSpeed=%.2f brake=%.2f wp=%d/%d route=%s",
                target.source(), formatVector(currentPos), formatVec(target.position()), distance,
                holdSpeed, holdForwardSpeed, holdThrottle, target.waypointIndex() + 1, Math.max(1, target.waypointCount()), pathfinderRouteName));
            if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
                reportPathfinderRouteArrived(target, distance);
            }
            lastVehicleAutoSteeringInput = 0.0;
            resetVehicleAutoStuckState();
            maybeAnnounceAutoAssistant("arrived", distance, holdSpeed, 0.0, target.position(), 0L);
            return VehicleAutoDriveAssist.hold(target.source(), holdThrottle, holdSpeed);
        }

        double targetBearing = yawDegreesFromVector(dx, dz);
        double heading = currentHeadingDeg();
        if (!Double.isFinite(heading)) heading = targetBearing;
        double headingError = wrapDegrees(targetBearing - heading);
        if (distance <= VEHICLE_AUTO_REVERSE_REACQUIRE_RADIUS
            && Math.abs(headingError) >= VEHICLE_AUTO_OVERSHOOT_HEADING_ERROR_DEG) {
            double holdSpeed = currentHorizontalSpeed();
            double holdForwardSpeed = currentForwardSpeed(heading);
            if (holdSpeed <= VEHICLE_AUTO_REVERSE_ENTRY_SPEED) {
                double reverseHeading = wrapDegrees(heading + 180.0);
                double reverseError = wrapDegrees(targetBearing - reverseHeading);
                double reverseSteering = Math.abs(reverseError) <= VEHICLE_AUTO_STEER_DEADZONE_DEG ? 0.0
                    : clampDouble(reverseError / VEHICLE_AUTO_STEER_FULL_ERROR_DEG,
                        -VEHICLE_AUTO_REVERSE_STEER_LIMIT, VEHICLE_AUTO_REVERSE_STEER_LIMIT);
                double speedForReverse = Double.isFinite(holdForwardSpeed) ? holdForwardSpeed : 0.0;
                double reverseThrottle = clampDouble((-VEHICLE_AUTO_REVERSE_TARGET_SPEED - speedForReverse) * VEHICLE_AUTO_SPEED_GAIN,
                    -0.35, 0.20);
                updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
                    "auto=reverse-reacquire source=%s current=%s target=%s dist=%.2f heading=%.1f err=%.1f reverseErr=%.1f steer=%.2f throttle=%.2f currentSpeed=%.2f forwardSpeed=%.2f wp=%d/%d route=%s",
                    target.source(), formatVector(currentPos), formatVec(target.position()), distance, heading, headingError,
                    reverseError, reverseSteering, reverseThrottle, holdSpeed, holdForwardSpeed,
                    target.waypointIndex() + 1, Math.max(1, target.waypointCount()), pathfinderRouteName));
                if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
                    reportPathfinderRouteReverse(target, distance, reverseError);
                }
                lastVehicleAutoSteeringInput = reverseSteering;
                resetVehicleAutoStuckState();
                maybeAnnounceAutoAssistant("reverse", distance, holdSpeed,
                    holdSpeed > 0.1 ? distance / Math.max(0.1, holdSpeed) : Double.NaN,
                    target.position(), autoAssistReverseTicks());
                return new VehicleAutoDriveAssist(true, reverseSteering, reverseThrottle, distance,
                    -VEHICLE_AUTO_REVERSE_TARGET_SPEED, holdSpeed, target.source(), target.waypointIndex(), target.waypointCount());
            }
            double holdThrottle = autoHoldBrakeThrottle(holdSpeed, holdForwardSpeed);
            updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
                "auto=hold-overshot source=%s current=%s target=%s dist=%.2f heading=%.1f err=%.1f speed=%.2f forwardSpeed=%.2f brake=%.2f wp=%d/%d route=%s",
                target.source(), formatVector(currentPos), formatVec(target.position()), distance, heading, headingError,
                holdSpeed, holdForwardSpeed, holdThrottle, target.waypointIndex() + 1, Math.max(1, target.waypointCount()), pathfinderRouteName));
            if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
                reportPathfinderSafetyHold("Hold: overshot route target");
            }
            lastVehicleAutoSteeringInput = 0.0;
            resetVehicleAutoStuckState();
            return VehicleAutoDriveAssist.hold(target.source(), holdThrottle, holdSpeed);
        }
        double steering;
        if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
            steering = pathfinderPurePursuitSteering(headingError, distance);
        } else {
            steering = Math.abs(headingError) <= VEHICLE_AUTO_STEER_DEADZONE_DEG ? 0.0
                : clampDouble(headingError / VEHICLE_AUTO_STEER_FULL_ERROR_DEG, -1.0, 1.0);
        }
        if (Math.abs(Math.abs(headingError) - 180.0) < 8.0 && Math.abs(lastVehicleAutoSteeringInput) > 0.05) {
            steering = Math.copySign(Math.max(Math.abs(steering), 0.65), lastVehicleAutoSteeringInput);
        }

        double targetSpeed = target.speedLimit();
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0) {
            targetSpeed = autoTargetSpeedForDistance(distance);
        }
        if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
            // the old crawl cap stalled exactly when it had to turn and it spiralled off. trust the planner's corner speed

            if (distance > VEHICLE_AUTO_CRAWL_RADIUS) {
                double turnFloor = clampDouble(
                    Math.sqrt(VEHICLE_AUTO_TURN_FLOOR_LATERAL_ACCEL * vehicleMinTurnRadiusBlocks()),
                    VEHICLE_AUTO_TURN_FLOOR_SPEED_MIN, VEHICLE_AUTO_TURN_FLOOR_SPEED_MAX);
                targetSpeed = Math.max(targetSpeed, turnFloor);
            }
        } else if (Math.abs(headingError) > 55.0) {
            targetSpeed = Math.min(targetSpeed, VEHICLE_AUTO_CRAWL_SPEED);
        } else if (Math.abs(headingError) > 30.0) {
            targetSpeed = Math.min(targetSpeed, VEHICLE_AUTO_APPROACH_SPEED);
        }
        double currentSpeed = currentHorizontalSpeed();
        double forwardSpeed = currentForwardSpeed(heading);
        double speedForThrottle = Double.isFinite(forwardSpeed) ? Math.max(0.0, forwardSpeed) : currentSpeed;
        double throttle = clampDouble((targetSpeed - speedForThrottle) * VEHICLE_AUTO_SPEED_GAIN, -0.25, 0.82);
        if (Double.isFinite(forwardSpeed) && forwardSpeed < -VEHICLE_AUTO_HOLD_BRAKE_SPEED) {
            throttle = Math.max(throttle, Math.min(0.35, -forwardSpeed * VEHICLE_AUTO_HOLD_BRAKE_GAIN));
        }
        if (distance < VEHICLE_AUTO_CRAWL_RADIUS) {
            throttle = Math.min(throttle, 0.35);
        }
        VehicleAutoDriveAssist reverseUnstick = autoReverseUnstickAssist(target, currentPos, distance,
            targetBearing, heading, headingError, currentSpeed, forwardSpeed, throttle);
        if (reverseUnstick != null) {
            double etaSeconds = currentSpeed > 0.1 ? distance / Math.max(0.1, currentSpeed) : Double.NaN;
            maybeAnnounceAutoAssistant(reverseUnstick.throttleInput() < 0.0 ? "reverse" : "reacquire",
                distance, currentSpeed, etaSeconds, target.position(), vehicleReverseTicksLeft());
            return reverseUnstick;
        }
        lastVehicleAutoSteeringInput = steering;
        if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
            reportPathfinderRouteProgress(target, distance, targetSpeed);
        }
        updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
            "auto=track source=%s current=%s target=%s dist=%.2f bearing=%.1f heading=%.1f err=%.1f steer=%.2f minR=%.2f throttle=%.2f targetSpeed=%.2f currentSpeed=%.2f forwardSpeed=%.2f wp=%d/%d route=%s computer=%s",
            target.source(), formatVector(currentPos), formatVec(target.position()), distance, targetBearing, heading,
            headingError, steering, vehicleMinTurnRadiusBlocks(), throttle, targetSpeed, currentSpeed, forwardSpeed, target.waypointIndex() + 1,
            Math.max(1, target.waypointCount()), pathfinderRouteName, formatBlockPos(pathfinderComputerPos)));
        maybeAnnounceAutoAssistant(distance <= autoAssistApproachDistance() ? "approach" : "cruise",
            distance, currentSpeed, currentSpeed > 0.1 ? distance / Math.max(0.1, currentSpeed) : Double.NaN,
            target.position(), 0L);
        return new VehicleAutoDriveAssist(true, steering, throttle, distance, targetSpeed, currentSpeed,
            target.source(), target.waypointIndex(), target.waypointCount());
    }

    private boolean applyAirshipAutoDriveAssist(boolean flipOverride) {
        boolean autoEnabled = isAnyAutoDriveActive();
        FccCompositeControl thrustControl = findControlByRole(FccControlRole.THRUST);
        if (thrustControl == null) thrustControl = findControlByAnyLabel("Thrust Lever", "Thrust", "Throttle");
        FccCompositeControl yawControl = findControlByRole(FccControlRole.YAW);
        if (yawControl == null) yawControl = findControlByAnyLabel("Yaw Wheel", "Yaw", "Steering Wheel", "Steering");
        if (!autoEnabled) {
            resetAirshipAutoState();
            lastAirshipAutoDebug = "airship=off";
            lastAirshipAutoTargetDistance = Double.NaN;
            return false;
        }
        if (thrustControl == null || yawControl == null) {
            resetAirshipAutoState();
            lastAirshipAutoDebug = "airship=hold missingControls thrust=" + (thrustControl != null) + " yaw=" + (yawControl != null);
            lastAirshipAutoTargetDistance = Double.NaN;
            return false;
        }
        VehicleAutoDriveTarget target = resolveNavTableAutoDriveTarget();
        if (target == null) {
            resetAirshipAutoState();
            lastAirshipAutoDebug = "airship=hold noNavTarget reason=" + autoHoldReason();
            lastAirshipAutoTargetDistance = Double.NaN;
            boolean changed = setSignedControlValue(thrustControl, 0.0, AIRSHIP_AUTO_THRUST_SLEW);
            changed |= setSignedControlValue(yawControl, 0.0, AIRSHIP_AUTO_STEER_SLEW);
            return changed;
        }
        long now = serverTickCount;
        double tiltDeg = lastFlipPreventionTiltDeg;
        if (flipOverride) {
            beginAirshipAutoFlipLock(thrustControl, yawControl, target, tiltDeg);
            double distance = airshipAutoDistanceToTarget(target);
            lastAirshipAutoTargetDistance = distance;
            boolean changed = setSignedControlValue(thrustControl, 0.0);
            changed |= setSignedControlValue(yawControl, 0.0);
            lastAirshipAutoDebug = String.format(java.util.Locale.ROOT,
                "airship=flip-lock target=%s dist=%.1f tilt=%s incidentThrust=%.2f incidentYaw=%.2f",
                formatVec(target.position()), distance, formatDegrees(tiltDeg), airshipAutoIncidentThrustLimit,
                airshipAutoIncidentYawLimit);
            return changed;
        }
        if (airshipAutoFlipWasActive) {
            beginAirshipAutoPostFlipRecovery(now, tiltDeg);
        }
        finishAirshipAutoPostFlipRecoveryIfDone(now);
        if (isAirshipAutoPostFlipHold(now)) {
            clearAirshipAutoBlockedMotionState();
            double distance = airshipAutoDistanceToTarget(target);
            lastAirshipAutoTargetDistance = distance;
            boolean changed = setSignedControlValue(thrustControl, 0.0);
            changed |= setSignedControlValue(yawControl, 0.0);
            lastAirshipAutoDebug = String.format(java.util.Locale.ROOT,
                "airship=flip-hold target=%s dist=%.1f holdTicks=%d tilt=%s incidentThrust=%.2f incidentYaw=%.2f",
                formatVec(target.position()), distance, Math.max(0L, airshipAutoPostFlipHoldUntilTick - now),
                formatDegrees(tiltDeg), airshipAutoIncidentThrustLimit, airshipAutoIncidentYawLimit);
            return changed;
        }
        AirshipAutoDriveAssist assist = computeAirshipAutoDriveAssist(target);
        boolean recoveryRamp = isAirshipAutoPostFlipRamp(now);
        if (recoveryRamp && shouldRestartAirshipAutoRecoveryHold(tiltDeg)) {
            beginAirshipAutoPostFlipRecovery(now, tiltDeg);
            double distance = assist.distanceToTarget();
            lastAirshipAutoTargetDistance = distance;
            boolean changed = setSignedControlValue(thrustControl, 0.0);
            changed |= setSignedControlValue(yawControl, 0.0);
            lastAirshipAutoDebug = String.format(java.util.Locale.ROOT,
                "airship=flip-hold target=%s dist=%.1f tilt=%s reason=tilt-worsened",
                formatVec(target.position()), distance, formatDegrees(tiltDeg));
            return changed;
        }
        if (recoveryRamp) {
            assist = clampAirshipAutoRecoveryRamp(assist, now);
        }
        lastAirshipAutoTargetDistance = assist.distanceToTarget();
        boolean changed = setSignedControlValue(thrustControl, assist.thrustInput(), AIRSHIP_AUTO_THRUST_SLEW);
        changed |= setSignedControlValue(yawControl, assist.steeringInput(), AIRSHIP_AUTO_STEER_SLEW);
        if (!recoveryRamp) {
            rememberAirshipAutoPreDangerCommand(thrustControl, yawControl, assist, tiltDeg);
            updateAirshipAutoReachableCapDuringCruise(assist, tiltDeg);
        }
        double etaSeconds = assist.currentSpeed() > 0.1 && Double.isFinite(assist.distanceToTarget())
            ? assist.distanceToTarget() / Math.max(0.1, assist.currentSpeed())
            : Double.NaN;
        maybeAnnounceAutoAssistant(assist.phase(), assist.distanceToTarget(), assist.currentSpeed(), etaSeconds, assist.target());
        lastAirshipAutoDebug = String.format(java.util.Locale.ROOT,
            "airship=%s target=%s dist=%.1f thrust=%.2f yaw=%.2f targetSpeed=%.2f speed=%.2f eta=%s",
            assist.phase(), formatVec(assist.target()), assist.distanceToTarget(), assist.thrustInput(),
            assist.steeringInput(), assist.targetSpeed(), assist.currentSpeed(), formatEta(etaSeconds));
        return changed;
    }

    private void beginAirshipAutoFlipLock(FccCompositeControl thrustControl, FccCompositeControl yawControl,
                                          VehicleAutoDriveTarget target, double tiltDeg) {
        if (!airshipAutoFlipWasActive) {
            freezeAirshipAutoIncidentSnapshot(thrustControl, yawControl, target, tiltDeg);
            DebugLogging.fccInfo("[FCC@{}] airship auto flip lock tilt={} preDangerTilt={} thrustLimit={} yawLimit={}",
                getBlockPos(), formatDegrees(tiltDeg), formatDegrees(airshipAutoPreDangerTiltDeg),
                String.format(java.util.Locale.ROOT, "%.2f", airshipAutoIncidentThrustLimit),
                String.format(java.util.Locale.ROOT, "%.2f", airshipAutoIncidentYawLimit));
        }
        airshipAutoFlipWasActive = true;
        clearAirshipAutoBlockedMotionState();
        lastAirshipAutoRecoveryTiltDeg = tiltDeg;
    }

    private void beginAirshipAutoPostFlipRecovery(long now, double tiltDeg) {
        airshipAutoFlipWasActive = false;
        airshipAutoPostFlipHoldUntilTick = now + AIRSHIP_AUTO_POST_FLIP_HOLD_TICKS;
        airshipAutoPostFlipRampStartTick = airshipAutoPostFlipHoldUntilTick;
        airshipAutoPostFlipRampUntilTick = airshipAutoPostFlipRampStartTick + AIRSHIP_AUTO_POST_FLIP_RAMP_TICKS;
        lastAirshipAutoRecoveryTiltDeg = tiltDeg;
        clearAirshipAutoBlockedMotionState();
        DebugLogging.fccInfo("[FCC@{}] airship auto post-flip recovery hold={} ramp={} tilt={} thrustLimit={} yawLimit={}",
            getBlockPos(), AIRSHIP_AUTO_POST_FLIP_HOLD_TICKS, AIRSHIP_AUTO_POST_FLIP_RAMP_TICKS,
            formatDegrees(tiltDeg), String.format(java.util.Locale.ROOT, "%.2f", airshipAutoIncidentThrustLimit),
            String.format(java.util.Locale.ROOT, "%.2f", airshipAutoIncidentYawLimit));
    }

    private void finishAirshipAutoPostFlipRecoveryIfDone(long now) {
        if (airshipAutoPostFlipRampUntilTick != Long.MIN_VALUE && now >= airshipAutoPostFlipRampUntilTick) {
            airshipAutoPostFlipHoldUntilTick = Long.MIN_VALUE;
            airshipAutoPostFlipRampStartTick = Long.MIN_VALUE;
            airshipAutoPostFlipRampUntilTick = Long.MIN_VALUE;
            lastAirshipAutoRecoveryTiltDeg = Double.NaN;
        }
    }

    private boolean isAirshipAutoPostFlipHold(long now) {
        return airshipAutoPostFlipHoldUntilTick != Long.MIN_VALUE && now < airshipAutoPostFlipHoldUntilTick;
    }

    private boolean isAirshipAutoPostFlipRamp(long now) {
        return airshipAutoPostFlipRampStartTick != Long.MIN_VALUE
            && airshipAutoPostFlipRampUntilTick != Long.MIN_VALUE
            && now >= airshipAutoPostFlipRampStartTick
            && now < airshipAutoPostFlipRampUntilTick;
    }

    private boolean isAirshipAutoFlipRecoveryActive(long now) {
        return airshipAutoFlipWasActive || isAirshipAutoPostFlipHold(now) || isAirshipAutoPostFlipRamp(now);
    }

    private void freezeAirshipAutoIncidentSnapshot(FccCompositeControl thrustControl, FccCompositeControl yawControl,
                                                   VehicleAutoDriveTarget target, double tiltDeg) {
        boolean recentPreDanger = airshipAutoPreDangerTick != Long.MIN_VALUE
            && serverTickCount - airshipAutoPreDangerTick <= 20L * 8L;
        double thrust = recentPreDanger ? airshipAutoPreDangerThrust : normalizeControlSigned(thrustControl);
        double yaw = recentPreDanger ? airshipAutoPreDangerYaw : normalizeControlSigned(yawControl);
        double positiveThrust = Math.max(0.0, thrust);
        airshipAutoIncidentThrustLimit = positiveThrust <= 1.0e-4
            ? 0.0
            : clampDouble(positiveThrust, AIRSHIP_AUTO_RECOVERY_MIN_THRUST_LIMIT, 1.0);
        airshipAutoIncidentYawLimit = clampDouble(Math.abs(yaw), AIRSHIP_AUTO_RECOVERY_MIN_YAW_LIMIT, AIRSHIP_AUTO_STEER_MAX);
        if (!recentPreDanger) {
            airshipAutoPreDangerThrust = thrust;
            airshipAutoPreDangerYaw = yaw;
            airshipAutoPreDangerSpeed = currentHorizontalSpeed();
            airshipAutoPreDangerTargetSpeed = target.speedLimit();
            airshipAutoPreDangerTiltDeg = tiltDeg;
            airshipAutoPreDangerTick = serverTickCount;
        }
        updateAirshipAutoReachableCapFromIncident();
    }

    private void rememberAirshipAutoPreDangerCommand(FccCompositeControl thrustControl, FccCompositeControl yawControl,
                                                     AirshipAutoDriveAssist assist, double tiltDeg) {
        double warningTilt = Math.max(0.0, FLIP_PREVENTION_ENGAGE_DEG - AIRSHIP_AUTO_FLIP_WARNING_MARGIN_DEG);
        if (Double.isFinite(tiltDeg) && tiltDeg > warningTilt) return;
        airshipAutoPreDangerThrust = normalizeControlSigned(thrustControl);
        airshipAutoPreDangerYaw = normalizeControlSigned(yawControl);
        airshipAutoPreDangerSpeed = assist.currentSpeed();
        airshipAutoPreDangerTargetSpeed = assist.targetSpeed();
        airshipAutoPreDangerTiltDeg = tiltDeg;
        airshipAutoPreDangerTick = serverTickCount;
    }

    private AirshipAutoDriveAssist clampAirshipAutoRecoveryRamp(AirshipAutoDriveAssist assist, long now) {
        long rampTicks = Math.max(1L, airshipAutoPostFlipRampUntilTick - airshipAutoPostFlipRampStartTick);
        double progress = clampDouble((now - airshipAutoPostFlipRampStartTick) / (double) rampTicks, 0.0, 1.0);
        double eased = progress * progress;
        double thrustLimit = airshipAutoIncidentThrustLimit * eased;
        double yawLimit = clampDouble(airshipAutoIncidentYawLimit * (0.35 + eased * 0.65),
            AIRSHIP_AUTO_RECOVERY_MIN_YAW_LIMIT, AIRSHIP_AUTO_STEER_MAX);
        double thrust = clampDouble(assist.thrustInput(), 0.0, thrustLimit);
        double yaw = clampDouble(assist.steeringInput(), -yawLimit, yawLimit);
        clearAirshipAutoBlockedMotionState();
        lastAirshipAutoSteeringInput = yaw;
        return new AirshipAutoDriveAssist(yaw, thrust, assist.distanceToTarget(), assist.targetSpeed(),
            assist.currentSpeed(), "flip-ramp", assist.target());
    }

    private boolean shouldRestartAirshipAutoRecoveryHold(double tiltDeg) {
        if (!Double.isFinite(tiltDeg)) return false;
        double warningTilt = Math.max(0.0, FLIP_PREVENTION_ENGAGE_DEG - AIRSHIP_AUTO_FLIP_WARNING_MARGIN_DEG);
        boolean nearDanger = tiltDeg >= warningTilt;
        boolean worsening = Double.isFinite(lastAirshipAutoRecoveryTiltDeg)
            && tiltDeg > lastAirshipAutoRecoveryTiltDeg + 0.25
            && tiltDeg > FLIP_PREVENTION_RELEASE_DEG + 3.0;
        lastAirshipAutoRecoveryTiltDeg = tiltDeg;
        return nearDanger || worsening;
    }

    private void clearAirshipAutoBlockedMotionState() {
        airshipAutoStuckSinceTick = Long.MIN_VALUE;
        airshipAutoReverseUntilTick = Long.MIN_VALUE;
        airshipAutoReacquireUntilTick = Long.MIN_VALUE;
        airshipAutoSpeedIntegral = 0.0;
        lastAirshipAutoSteeringInput = 0.0;
    }

    private double airshipAutoDistanceToTarget(VehicleAutoDriveTarget target) {
        return horizontalDistance(target.position(), currentContraptionWorldPosition());
    }

    private AirshipAutoDriveAssist computeAirshipAutoDriveAssist(VehicleAutoDriveTarget target) {
        org.joml.Vector3d currentPos = currentContraptionWorldPosition();
        double dx = target.position().x - currentPos.x;
        double dz = target.position().z - currentPos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double currentSpeed = currentHorizontalSpeed();
        double heading = currentHeadingDeg();
        double bearing = distance > 1.0e-4 ? yawDegreesFromVector(dx, dz) : heading;
        if (!Double.isFinite(heading)) heading = bearing;
        double headingError = wrapDegrees(bearing - heading);
        double yawRate = sampleAirshipYawRate(heading, headingError);
        double learnedGain = airshipAutoLearnedGain();
        double forwardSpeed = currentForwardSpeed(heading);
        double approachDistance = autoAssistApproachDistance();
        if (distance <= AIRSHIP_AUTO_ARRIVAL_RADIUS) {
            resetAirshipAutoMotionState();
            double thrust = autoHoldBrakeThrottle(currentSpeed, forwardSpeed);
            return new AirshipAutoDriveAssist(0.0, thrust, distance, 0.0, currentSpeed, "arrived", target.position());
        }

        double steering = Math.abs(headingError) <= AIRSHIP_AUTO_STEER_DEADZONE_DEG ? 0.0
            : (headingError / AIRSHIP_AUTO_STEER_FULL_ERROR_DEG)
                - clampDouble(yawRate / 85.0, -0.75, 0.75) * AIRSHIP_AUTO_YAW_DAMPING;
        steering *= learnedGain * airshipAutoSteeringPolarity;
        double maxSteering = clampDouble(AIRSHIP_AUTO_STEER_MAX * learnedGain, 0.22, AIRSHIP_AUTO_STEER_MAX);
        if (currentSpeed > 6.0) maxSteering *= 0.78;
        steering = clampDouble(steering, -maxSteering, maxSteering);
        if (Math.abs(Math.abs(headingError) - 180.0) < 8.0 && Math.abs(lastAirshipAutoSteeringInput) > 0.05) {
            steering = Math.copySign(Math.min(maxSteering, Math.max(Math.abs(steering), 0.35)), lastAirshipAutoSteeringInput);
        }
        double targetSpeed = airshipAutoTargetSpeed(distance, approachDistance, target.speedLimit(), currentSpeed);
        boolean brakingApproach = distance <= lastAirshipAutoApproachWindowBlocks;
        if (Math.abs(headingError) > 25.0) {
            targetSpeed *= clampDouble(learnedGain + 0.15, 0.48, 1.0);
        }
        if (Math.abs(headingError) > 65.0) targetSpeed = Math.min(targetSpeed, 2.5);
        else if (Math.abs(headingError) > 35.0) targetSpeed = Math.min(targetSpeed, 5.0);

        double speedForThrust = Double.isFinite(forwardSpeed) ? Math.max(0.0, forwardSpeed) : currentSpeed;
        double thrust = airshipAutoCruiseThrust(targetSpeed - speedForThrust, headingError, brakingApproach);
        if (brakingApproach) {
            thrust = Math.min(thrust, 0.65);
        }
        AirshipAutoDriveAssist reverse = airshipReverseOrReacquireAssist(target, distance, bearing, heading,
            headingError, steering, currentSpeed, forwardSpeed, thrust);
        if (reverse != null) return reverse;
        lastAirshipAutoSteeringInput = steering;
        String phase = distance <= approachDistance ? "approach" : "cruise";
        return new AirshipAutoDriveAssist(steering, thrust, distance, targetSpeed, currentSpeed, phase, target.position());
    }

    private double sampleAirshipYawRate(double heading, double headingError) {
        long now = serverTickCount;
        double yawRate = 0.0;
        if (Double.isFinite(lastAirshipAutoHeadingDeg) && lastAirshipAutoHeadingTick != Long.MIN_VALUE) {
            long dt = Math.max(1L, now - lastAirshipAutoHeadingTick);
            yawRate = wrapDegrees(heading - lastAirshipAutoHeadingDeg) * 20.0 / dt;
        }
        if (Double.isFinite(lastAirshipAutoHeadingErrorDeg)) {
            boolean crossedCenter = Math.signum(headingError) != Math.signum(lastAirshipAutoHeadingErrorDeg)
                && Math.abs(headingError) > AIRSHIP_AUTO_STEER_DEADZONE_DEG
                && Math.abs(lastAirshipAutoHeadingErrorDeg) > AIRSHIP_AUTO_STEER_DEADZONE_DEG;
            if (crossedCenter) {
                airshipAutoOverturnScore = Math.min(6.0, airshipAutoOverturnScore + 1.0);
            } else if (Math.abs(headingError) < Math.abs(lastAirshipAutoHeadingErrorDeg)) {
                airshipAutoOverturnScore = Math.max(0.0, airshipAutoOverturnScore - 0.08);
            } else {
                airshipAutoOverturnScore = Math.max(0.0, airshipAutoOverturnScore - 0.025);
            }
        }
        if (Math.abs(lastAirshipAutoSteeringInput) > 0.12
            && Math.abs(headingError) > 18.0
            && Math.abs(yawRate) > 2.0) {
            boolean turningTowardTarget = Math.signum(yawRate) == Math.signum(headingError);
            if (turningTowardTarget) {
                airshipAutoWrongWayTicks = Math.max(0L, airshipAutoWrongWayTicks - 3L);
            } else {
                airshipAutoWrongWayTicks++;
                if (airshipAutoWrongWayTicks >= 50L) {
                    airshipAutoSteeringPolarity *= -1;
                    airshipAutoWrongWayTicks = 0L;
                    airshipAutoOverturnScore = Math.min(6.0, airshipAutoOverturnScore + 2.0);
                }
            }
        } else {
            airshipAutoWrongWayTicks = Math.max(0L, airshipAutoWrongWayTicks - 1L);
        }
        lastAirshipAutoHeadingDeg = heading;
        lastAirshipAutoHeadingErrorDeg = headingError;
        lastAirshipAutoHeadingTick = now;
        return yawRate;
    }

    private double airshipAutoLearnedGain() {
        return clampDouble(1.0 - airshipAutoOverturnScore * 0.11, 0.35, 1.0);
    }

    private double airshipAutoCruiseThrust(double speedError, double headingError, boolean approach) {
        boolean aligned = Math.abs(headingError) <= 22.0;
        if (aligned && speedError > -0.75) {
            double integralMax = approach ? 0.22 : AIRSHIP_AUTO_SPEED_INTEGRAL_MAX;
            airshipAutoSpeedIntegral = clampDouble(airshipAutoSpeedIntegral * 0.995
                + speedError * AIRSHIP_AUTO_SPEED_INTEGRAL_GAIN, -0.14, integralMax);
        } else {
            airshipAutoSpeedIntegral *= 0.92;
        }
        double gain = aligned ? AIRSHIP_AUTO_SPEED_GAIN : AIRSHIP_AUTO_SPEED_GAIN * 0.72;
        double maxForward = approach ? 0.50 : 1.0;
        double minReverse = approach ? -0.24 : -0.30;
        return clampDouble(speedError * gain + airshipAutoSpeedIntegral, minReverse, maxForward);
    }

    @Nullable
    private AirshipAutoDriveAssist airshipReverseOrReacquireAssist(VehicleAutoDriveTarget target, double distance,
                                                                   double targetBearing, double heading,
                                                                   double headingError, double steering,
                                                                   double currentSpeed, double forwardSpeed,
                                                                   double requestedThrust) {
        long now = serverTickCount;
        if (isAirshipAutoFlipRecoveryActive(now)) {
            clearAirshipAutoBlockedMotionState();
            return null;
        }
        long reverseTicks = autoAssistReverseTicks();
        if (airshipAutoReverseUntilTick != Long.MIN_VALUE && now >= airshipAutoReverseUntilTick) {
            airshipAutoReverseUntilTick = Long.MIN_VALUE;
            airshipAutoReacquireUntilTick = now + AIRSHIP_AUTO_REACQUIRE_TICKS;
        }
        boolean reacquire = airshipAutoReacquireUntilTick != Long.MIN_VALUE && now < airshipAutoReacquireUntilTick;
        if (reacquire) {
            airshipAutoSpeedIntegral = 0.0;
            airshipAutoStuckSinceTick = Long.MIN_VALUE;
            double targetSpeed = Math.min(AIRSHIP_AUTO_REACQUIRE_SPEED, Math.max(1.5, target.speedLimit()));
            double speedForThrust = Double.isFinite(forwardSpeed) ? Math.max(0.0, forwardSpeed) : currentSpeed;
            double thrust = clampDouble((targetSpeed - speedForThrust) * AIRSHIP_AUTO_SPEED_GAIN, 0.16, 0.42);
            double biasedSteering = clampDouble(steering + airshipAutoAvoidSide * 0.25, -1.0, 1.0);
            lastAirshipAutoSteeringInput = biasedSteering;
            return new AirshipAutoDriveAssist(biasedSteering, thrust, distance, targetSpeed, currentSpeed,
                "reacquire", target.position());
        } else if (airshipAutoReacquireUntilTick != Long.MIN_VALUE) {
            airshipAutoReacquireUntilTick = Long.MIN_VALUE;
        }

        boolean reversing = airshipAutoReverseUntilTick != Long.MIN_VALUE && now < airshipAutoReverseUntilTick;
        boolean stuckCandidate = requestedThrust >= AIRSHIP_AUTO_STUCK_THRUST
            && currentSpeed <= AIRSHIP_AUTO_STUCK_SPEED
            && distance > AIRSHIP_AUTO_ARRIVAL_RADIUS;
        if (!reversing && stuckCandidate) {
            if (airshipAutoStuckSinceTick == Long.MIN_VALUE) {
                airshipAutoStuckSinceTick = now;
            } else if (now - airshipAutoStuckSinceTick >= AIRSHIP_AUTO_STUCK_TICKS) {
                airshipAutoAvoidSide = -airshipAutoAvoidSide;
                airshipAutoReverseUntilTick = now + reverseTicks;
                airshipAutoStuckSinceTick = Long.MIN_VALUE;
                reversing = true;
            }
        } else if (!reversing) {
            airshipAutoStuckSinceTick = Long.MIN_VALUE;
        }
        if (!reversing) return null;
        airshipAutoSpeedIntegral = 0.0;

        long started = airshipAutoReverseUntilTick - reverseTicks;
        double progress = reverseTicks <= 0L ? 1.0 : clampDouble((now - started) / (double) reverseTicks, 0.0, 1.0);
        double reverseHeading = wrapDegrees(heading + 180.0);
        double reverseError = wrapDegrees(targetBearing - reverseHeading);
        double reverseSteering = Math.abs(reverseError) <= AIRSHIP_AUTO_STEER_DEADZONE_DEG ? 0.0
            : clampDouble(reverseError / AIRSHIP_AUTO_STEER_FULL_ERROR_DEG, -0.75, 0.75);
        if (Math.abs(reverseSteering) < 0.18) reverseSteering = airshipAutoAvoidSide * 0.38;
        else reverseSteering = clampDouble(reverseSteering + airshipAutoAvoidSide * 0.18, -0.85, 0.85);
        double reverseThrust = -(AIRSHIP_AUTO_REVERSE_MIN_THRUST
            + progress * (AIRSHIP_AUTO_REVERSE_MAX_THRUST - AIRSHIP_AUTO_REVERSE_MIN_THRUST));
        lastAirshipAutoSteeringInput = reverseSteering;
        return new AirshipAutoDriveAssist(reverseSteering, reverseThrust, distance,
            -AIRSHIP_AUTO_REACQUIRE_SPEED, currentSpeed, "reverse", target.position());
    }

    private double airshipAutoTargetSpeed(double distance, double approachDistance, double sourceLimit, double currentSpeed) {
        double requestedCap = Double.isFinite(sourceLimit) && sourceLimit > 0.0 ? sourceLimit : navTableMaxSpeed;
        requestedCap = clampDouble(requestedCap, 1.0, 32.0);
        double cap = airshipAutoEffectiveSpeedCap(requestedCap, currentSpeed);
        double window = airshipAutoApproachWindow(distance, currentSpeed, cap, approachDistance);
        lastAirshipAutoApproachWindowBlocks = window;
        if (distance >= window) return cap;
        double fraction = clampDouble(distance / Math.max(1.0, window), 0.0, 1.0);
        double floor = Math.min(cap, Math.max(2.4, cap * AIRSHIP_AUTO_APPROACH_MIN_SPEED_FRACTION));
        double target = floor + (cap - floor) * Math.sqrt(fraction);
        if (distance <= AIRSHIP_AUTO_ARRIVAL_RADIUS * 2.0) {
            double terminal = clampDouble((distance - AIRSHIP_AUTO_ARRIVAL_RADIUS) / AIRSHIP_AUTO_ARRIVAL_RADIUS, 0.0, 1.0);
            target = Math.min(target, 1.2 + (floor - 1.2) * terminal);
        }
        return clampDouble(target, 1.2, cap);
    }

    private double airshipAutoEffectiveSpeedCap(double requestedCap, double currentSpeed) {
        lastAirshipAutoRequestedSpeedCap = requestedCap;
        double reachableCap = Double.isFinite(airshipAutoReachableSpeedCap)
            ? clampDouble(airshipAutoReachableSpeedCap, AIRSHIP_AUTO_REACHABLE_CAP_MIN, 32.0)
            : Double.NaN;
        double effectiveCap = requestedCap;
        if (Double.isFinite(reachableCap) && requestedCap > reachableCap + 0.05) {
            effectiveCap = reachableCap;
            lastAirshipAutoReachabilityReason = "limited by last calm speed before Flip Prevention";
        } else if (Double.isFinite(reachableCap)) {
            lastAirshipAutoReachabilityReason = "selected speed is inside measured safe range";
        } else if (Double.isFinite(currentSpeed) && currentSpeed > requestedCap * 0.85) {
            lastAirshipAutoReachabilityReason = "selected speed currently reachable";
        } else {
            lastAirshipAutoReachabilityReason = "measuring ship response";
        }
        lastAirshipAutoEffectiveSpeedCap = effectiveCap;
        return effectiveCap;
    }

    private void updateAirshipAutoReachableCapFromIncident() {
        double measuredSpeed = Double.isFinite(airshipAutoPreDangerSpeed) ? airshipAutoPreDangerSpeed : currentHorizontalSpeed();
        if (!Double.isFinite(measuredSpeed) || measuredSpeed <= 0.5) {
            measuredSpeed = Math.min(navTableMaxSpeed, 3.0);
        }
        double incidentCap = clampDouble(measuredSpeed * AIRSHIP_AUTO_REACHABLE_CAP_MARGIN,
            AIRSHIP_AUTO_REACHABLE_CAP_MIN, 32.0);
        if (Double.isFinite(airshipAutoReachableSpeedCap)) {
            airshipAutoReachableSpeedCap = Math.min(airshipAutoReachableSpeedCap, incidentCap);
        } else {
            airshipAutoReachableSpeedCap = incidentCap;
        }
        airshipAutoReachableCalmTicks = 0L;
        lastAirshipAutoReachabilityReason = "learned from last Flip Prevention incident";
    }

    private void updateAirshipAutoReachableCapDuringCruise(AirshipAutoDriveAssist assist, double tiltDeg) {
        if (!Double.isFinite(airshipAutoReachableSpeedCap)) return;
        double requestedCap = Double.isFinite(lastAirshipAutoRequestedSpeedCap) ? lastAirshipAutoRequestedSpeedCap : navTableMaxSpeed;
        if (requestedCap <= airshipAutoReachableSpeedCap + 0.1) {
            lastAirshipAutoReachabilityReason = "selected speed is inside measured safe range";
            airshipAutoReachableCalmTicks = 0L;
            return;
        }
        boolean calmTilt = !Double.isFinite(tiltDeg) || tiltDeg <= FLIP_PREVENTION_RELEASE_DEG + 2.0;
        boolean nearCap = Double.isFinite(assist.currentSpeed())
            && assist.currentSpeed() >= airshipAutoReachableSpeedCap * 0.72;
        if (calmTilt && nearCap && assist.thrustInput() >= 0.0) {
            airshipAutoReachableCalmTicks++;
        } else {
            airshipAutoReachableCalmTicks = 0L;
        }
        if (airshipAutoReachableCalmTicks >= AIRSHIP_AUTO_REACHABLE_PROMOTE_TICKS) {
            airshipAutoReachableSpeedCap = Math.min(requestedCap,
                airshipAutoReachableSpeedCap + AIRSHIP_AUTO_REACHABLE_PROMOTE_STEP);
            airshipAutoReachableCalmTicks = 0L;
        }
        lastAirshipAutoReachabilityReason = "testing higher speed after calm cruise";
    }

    private double airshipAutoApproachWindow(double distance, double currentSpeed, double cap, double approachDistance) {
        double maxWindow = clampDouble(Math.min(approachDistance, AIRSHIP_AUTO_APPROACH_WINDOW_MAX),
            AIRSHIP_AUTO_APPROACH_WINDOW_MIN, AIRSHIP_AUTO_APPROACH_WINDOW_MAX);
        if (!Double.isFinite(airshipAutoApproachWindowBlocks) || airshipAutoApproachWindowBlocks <= 0.0) {
            airshipAutoApproachWindowBlocks = Math.min(AIRSHIP_AUTO_APPROACH_WINDOW_DEFAULT, maxWindow);
        }
        if (distance <= approachDistance && Double.isFinite(currentSpeed)) {
            double tooSlowFarOut = Math.max(2.0, cap * 0.45);
            double tooFastNearTarget = Math.max(2.0, cap * 0.24);
            if (distance > airshipAutoApproachWindowBlocks + 18.0 && currentSpeed < tooSlowFarOut) {
                airshipAutoApproachWindowBlocks -= 0.45;
            } else if (distance <= AIRSHIP_AUTO_ARRIVAL_RADIUS * 3.0 && currentSpeed > tooFastNearTarget) {
                airshipAutoApproachWindowBlocks += 0.85;
            } else if (distance < airshipAutoApproachWindowBlocks * 0.35 && currentSpeed < Math.max(1.35, cap * 0.14)) {
                airshipAutoApproachWindowBlocks -= 0.18;
            }
        }
        airshipAutoApproachWindowBlocks = clampDouble(airshipAutoApproachWindowBlocks,
            AIRSHIP_AUTO_APPROACH_WINDOW_MIN, maxWindow);
        return airshipAutoApproachWindowBlocks;
    }

    private boolean setSignedControlValue(FccCompositeControl control, double signedValue) {
        return setSignedControlValue(control, signedValue, 0.0);
    }

    private boolean setSignedControlValue(FccCompositeControl control, double signedValue, double maxSignedDelta) {
        double signed = clampDouble(signedValue, -1.0, 1.0);
        if (maxSignedDelta > 0.0) {
            double currentSigned = normalizeControlSigned(control);
            signed = currentSigned + clampDouble(signed - currentSigned, -maxSignedDelta, maxSignedDelta);
        }
        double target = signed >= 0.0
            ? control.neutralValue() + signed * (control.inputMax() - control.neutralValue())
            : control.neutralValue() + signed * (control.neutralValue() - control.inputMin());
        target = clampDouble(target, control.inputMin(), control.inputMax());
        double current = controlValues.getOrDefault(control.id(), control.neutralValue());
        if (Math.abs(current - target) < 1.0e-6) return false;
        controlValues.put(control.id(), target);
        setChanged();
        return true;
    }

    private void resetAirshipAutoState() {
        resetAirshipAutoMotionState();
        resetAirshipAutoFlipRecoveryState();
        lastAutoAssistantTargetKey = "";
        lastAutoAssistantPhase = "";
        lastAutoAssistantDistanceBucket = Integer.MAX_VALUE;
        lastAutoAssistantMilestone = Integer.MAX_VALUE;
    }

    private void resetAirshipAutoMotionState() {
        airshipAutoStuckSinceTick = Long.MIN_VALUE;
        airshipAutoReverseUntilTick = Long.MIN_VALUE;
        airshipAutoReacquireUntilTick = Long.MIN_VALUE;
        lastAirshipAutoSteeringInput = 0.0;
        lastAirshipAutoHeadingDeg = Double.NaN;
        lastAirshipAutoHeadingErrorDeg = Double.NaN;
        lastAirshipAutoHeadingTick = Long.MIN_VALUE;
        airshipAutoOverturnScore = 0.0;
        airshipAutoSteeringPolarity = 1;
        airshipAutoWrongWayTicks = 0L;
        airshipAutoSpeedIntegral = 0.0;
    }

    private void resetAirshipAutoFlipRecoveryState() {
        airshipAutoFlipWasActive = false;
        airshipAutoPostFlipHoldUntilTick = Long.MIN_VALUE;
        airshipAutoPostFlipRampStartTick = Long.MIN_VALUE;
        airshipAutoPostFlipRampUntilTick = Long.MIN_VALUE;
        airshipAutoPreDangerThrust = 0.0;
        airshipAutoPreDangerYaw = 0.0;
        airshipAutoPreDangerSpeed = Double.NaN;
        airshipAutoPreDangerTargetSpeed = Double.NaN;
        airshipAutoPreDangerTiltDeg = Double.NaN;
        airshipAutoPreDangerTick = Long.MIN_VALUE;
        airshipAutoIncidentThrustLimit = AIRSHIP_AUTO_RECOVERY_MIN_THRUST_LIMIT;
        airshipAutoIncidentYawLimit = AIRSHIP_AUTO_RECOVERY_MIN_YAW_LIMIT;
        lastAirshipAutoRecoveryTiltDeg = Double.NaN;
        airshipAutoReachableSpeedCap = Double.NaN;
        lastAirshipAutoRequestedSpeedCap = Double.NaN;
        lastAirshipAutoEffectiveSpeedCap = Double.NaN;
        lastAirshipAutoReachabilityReason = "measuring";
        airshipAutoReachableCalmTicks = 0L;
    }

    private void maybeAnnounceAutoAssistant(String phase, double distance, double speed, double etaSeconds, Vec3 target) {
        long reverseTicksLeft = phase.equals("reverse") && airshipAutoReverseUntilTick != Long.MIN_VALUE
            ? Math.max(0L, airshipAutoReverseUntilTick - serverTickCount)
            : autoAssistReverseTicks();
        maybeAnnounceAutoAssistant(phase, distance, speed, etaSeconds, target, reverseTicksLeft);
    }

    private void maybeAnnounceAutoAssistant(String phase, double distance, double speed, double etaSeconds, Vec3 target,
                                            long reverseTicksLeft) {
        if (!(level instanceof ServerLevel serverLevel) || target == null || !Double.isFinite(distance)) return;
        String targetKey = BlockPos.containing(target.x, target.y, target.z).toShortString();
        if (!targetKey.equals(lastAutoAssistantTargetKey)) {
            lastAutoAssistantTargetKey = targetKey;
            lastAutoAssistantPhase = "";
            lastAutoAssistantDistanceBucket = Integer.MAX_VALUE;
            lastAutoAssistantMilestone = Integer.MAX_VALUE;
        }
        long now = serverLevel.getGameTime();
        if (lastAutoAssistantMessageTick != Long.MIN_VALUE
            && now - lastAutoAssistantMessageTick < AUTO_ASSIST_CHAT_MIN_INTERVAL_TICKS) {
            return;
        }
        double interval = autoAssistAnnounceIntervalBlocks();
        int bucket = (int) Math.floor(distance / Math.max(1.0, interval));
        int milestone = autoAssistantMilestone(distance);
        boolean phaseChanged = !phase.equals(lastAutoAssistantPhase)
            && (phase.equals("reverse") || phase.equals("reacquire") || phase.equals("approach") || phase.equals("arrived"));
        boolean distanceStep = bucket < lastAutoAssistantDistanceBucket;
        boolean milestoneStep = milestone < lastAutoAssistantMilestone;
        boolean firstMessage = lastAutoAssistantPhase.isEmpty();
        if (!firstMessage && !phaseChanged && !distanceStep && !milestoneStep) return;

        String message;
        if (phase.equals("arrived")) {
            message = "Arrival reached. Holding position.";
        } else if (phase.equals("reverse")) {
            message = String.format(java.util.Locale.ROOT,
                "Reacquire: reversing for %.1fs, %.0fm left.", Math.max(0L, reverseTicksLeft) / 20.0, distance);
        } else if (phase.equals("reacquire")) {
            message = String.format(java.util.Locale.ROOT,
                "Reacquire: trying a new line, %.0fm left, speed %.1fm/s.", distance, speed);
        } else if (phase.equals("approach")) {
            message = String.format(java.util.Locale.ROOT,
                "Approach phase: slowing down, %.0fm left, speed %.1fm/s, ETA %s.", distance, speed, formatEta(etaSeconds));
        } else {
            message = String.format(java.util.Locale.ROOT,
                "Cruise engaged: %.0fm left, speed %.1fm/s, ETA %s.", distance, speed, formatEta(etaSeconds));
        }
        sendAutoAssistantMessage(serverLevel, message);
        lastAutoAssistantMessageTick = now;
        lastAutoAssistantPhase = phase;
        lastAutoAssistantDistanceBucket = bucket;
        lastAutoAssistantMilestone = milestone;
    }

    private int autoAssistantMilestone(double distance) {
        double approach = autoAssistApproachDistance();
        if (distance <= AIRSHIP_AUTO_ARRIVAL_RADIUS) return 0;
        if (distance <= 50.0) return 50;
        if (distance <= 100.0) return 100;
        if (distance <= approach) return (int) Math.round(approach);
        return Integer.MAX_VALUE;
    }

    private void sendAutoAssistantMessage(ServerLevel serverLevel, String message) {
        Component component = Component.literal("[FCC] " + message).withStyle(ChatFormatting.AQUA);
        Set<UUID> delivered = new LinkedHashSet<>();
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (isPlayerInInteractionRange(player)) {
                player.displayClientMessage(component, false);
                delivered.add(player.getUUID());
            }
        }
        for (ServerPlayerWithMenu viewer : openMenuPlayers()) {
            if (delivered.add(viewer.player().getUUID())) {
                viewer.player().displayClientMessage(component, false);
            }
        }
    }

    private static String formatEta(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) return "--:--";
        int rounded = (int) Math.round(seconds);
        int minutes = rounded / 60;
        int rest = rounded % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    private static String formatDegrees(double degrees) {
        return Double.isFinite(degrees) ? String.format(java.util.Locale.ROOT, "%.1f", degrees) : "n/a";
    }

    @Nullable
    private VehicleAutoDriveAssist autoReverseUnstickAssist(VehicleAutoDriveTarget target, org.joml.Vector3d currentPos,
                                                            double distance, double targetBearing, double heading,
                                                            double headingError, double currentSpeed,
                                                            double forwardSpeed, double requestedThrottle) {
        long now = serverTickCount;
        double evadeRadius = vehicleAutoEvadeRadius();
        if (vehicleAutoReverseUnstickUntilTick != Long.MIN_VALUE && now >= vehicleAutoReverseUnstickUntilTick) {
            vehicleAutoReverseUnstickUntilTick = Long.MIN_VALUE;
            vehicleAutoStraightEscapeUntilTick = now + vehicleAutoStraightEscapeTicks(evadeRadius);
        }
        boolean straightEscape = vehicleAutoStraightEscapeUntilTick != Long.MIN_VALUE
            && now < vehicleAutoStraightEscapeUntilTick;
        if (straightEscape) {
            vehicleAutoStuckSinceTick = Long.MIN_VALUE;
            double speedForThrottle = Double.isFinite(forwardSpeed) ? Math.max(0.0, forwardSpeed) : currentSpeed;
            double throttle = clampDouble((VEHICLE_AUTO_STRAIGHT_ESCAPE_TARGET_SPEED - speedForThrottle) * VEHICLE_AUTO_SPEED_GAIN,
                0.22, 0.46);

            double escapeSteering = Math.abs(headingError) <= VEHICLE_AUTO_STEER_DEADZONE_DEG ? 0.0
                : clampDouble(headingError / VEHICLE_AUTO_STEER_FULL_ERROR_DEG, -1.0, 1.0);
            updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
                "auto=escape-straight source=%s current=%s target=%s dist=%.2f heading=%.1f err=%.1f steer=%.2f throttle=%.2f currentSpeed=%.2f forwardSpeed=%.2f evadeRadius=%.1f until=%d wp=%d/%d route=%s",
                target.source(), formatVector(currentPos), formatVec(target.position()), distance, heading, headingError,
                escapeSteering, throttle, currentSpeed, forwardSpeed, evadeRadius,
                Math.max(0L, vehicleAutoStraightEscapeUntilTick - now), target.waypointIndex() + 1,
                Math.max(1, target.waypointCount()), pathfinderRouteName));
            if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
                reportPathfinderRouteProgress(target, distance, VEHICLE_AUTO_STRAIGHT_ESCAPE_TARGET_SPEED);
            }
            lastVehicleAutoSteeringInput = escapeSteering;
            return new VehicleAutoDriveAssist(true, escapeSteering, throttle, distance,
                VEHICLE_AUTO_STRAIGHT_ESCAPE_TARGET_SPEED, currentSpeed, target.source(), target.waypointIndex(), target.waypointCount());
        } else if (vehicleAutoStraightEscapeUntilTick != Long.MIN_VALUE) {
            vehicleAutoStraightEscapeUntilTick = Long.MIN_VALUE;
        }
        boolean reversing = vehicleAutoReverseUnstickUntilTick != Long.MIN_VALUE
            && now < vehicleAutoReverseUnstickUntilTick;
        boolean stuckCandidate = requestedThrottle >= VEHICLE_AUTO_STUCK_THROTTLE
            && currentSpeed <= VEHICLE_AUTO_STUCK_SPEED
            && distance > VEHICLE_AUTO_ARRIVAL_RADIUS;
        if (!reversing && stuckCandidate) {
            if (vehicleAutoStuckSinceTick == Long.MIN_VALUE) {
                vehicleAutoStuckSinceTick = now;
            } else if (now - vehicleAutoStuckSinceTick >= VEHICLE_AUTO_STUCK_TICKS) {
                vehicleAutoReverseUnstickUntilTick = now + vehicleAutoReverseUnstickTicks(evadeRadius);
                vehicleAutoStuckSinceTick = Long.MIN_VALUE;
                reversing = true;
            }
        } else if (!reversing) {
            vehicleAutoStuckSinceTick = Long.MIN_VALUE;
        }
        if (!reversing) return null;

        double reverseHeading = wrapDegrees(heading + 180.0);
        double reverseError = wrapDegrees(targetBearing - reverseHeading);
        double reverseSteering = Math.abs(reverseError) <= VEHICLE_AUTO_STEER_DEADZONE_DEG ? 0.0
            : clampDouble(reverseError / VEHICLE_AUTO_STEER_FULL_ERROR_DEG,
                -VEHICLE_AUTO_REVERSE_STEER_LIMIT, VEHICLE_AUTO_REVERSE_STEER_LIMIT);
        double reverseThrottle = VEHICLE_AUTO_REVERSE_UNSTICK_THROTTLE;
        updateVehicleAutoDebug(String.format(java.util.Locale.ROOT,
            "auto=reverse-unstick source=%s current=%s target=%s dist=%.2f heading=%.1f err=%.1f reverseErr=%.1f steer=%.2f throttle=%.2f currentSpeed=%.2f forwardSpeed=%.2f evadeRadius=%.1f until=%d wp=%d/%d route=%s",
            target.source(), formatVector(currentPos), formatVec(target.position()), distance, heading, headingError,
            reverseError, reverseSteering, reverseThrottle, currentSpeed, forwardSpeed, evadeRadius,
            Math.max(0L, vehicleAutoReverseUnstickUntilTick - now), target.waypointIndex() + 1,
            Math.max(1, target.waypointCount()), pathfinderRouteName));
        if (target.source() == VehicleAutoDriveSource.PATHFINDER) {
            reportPathfinderRouteReverse(target, distance, reverseError);
        }
        lastVehicleAutoSteeringInput = reverseSteering;
        return new VehicleAutoDriveAssist(true, reverseSteering, reverseThrottle, distance,
            -VEHICLE_AUTO_REVERSE_TARGET_SPEED, currentSpeed, target.source(), target.waypointIndex(), target.waypointCount());
    }

    private void resetVehicleAutoStuckState() {
        vehicleAutoStuckSinceTick = Long.MIN_VALUE;
        vehicleAutoReverseUnstickUntilTick = Long.MIN_VALUE;
        vehicleAutoStraightEscapeUntilTick = Long.MIN_VALUE;
    }

    private double vehicleAutoEvadeRadius() {
        FccCompositeControl control = findControlByAnyLabel(AUTO_EVADE_RADIUS_LABEL,
            "Evade Radius", "Auto Evade Radius", "Avoid Radius");
        double value = control == null ? VEHICLE_AUTO_EVADE_RADIUS_DEFAULT
            : controlValues.getOrDefault(control.id(), control.neutralValue());
        double wheelbase = vehicleWheelbaseBlocks();
        if (Double.isFinite(wheelbase)) {
            value = Math.max(value, wheelbase * 2.25 + 4.0);
        }
        double track = vehicleTrackWidthBlocks();
        if (Double.isFinite(track)) {
            value = Math.max(value, track * 2.0 + 6.0);
        }
        return clampDouble(value, VEHICLE_AUTO_EVADE_RADIUS_MIN, VEHICLE_AUTO_EVADE_RADIUS_MAX);
    }

    private long vehicleAutoReverseUnstickTicks(double evadeRadius) {
        long userTicks = autoAssistReverseTicks();
        if (userTicks > 0L) return userTicks;
        double fraction = (clampDouble(evadeRadius, VEHICLE_AUTO_EVADE_RADIUS_MIN, VEHICLE_AUTO_EVADE_RADIUS_MAX)
            - VEHICLE_AUTO_EVADE_RADIUS_MIN) / (VEHICLE_AUTO_EVADE_RADIUS_MAX - VEHICLE_AUTO_EVADE_RADIUS_MIN);
        return Math.round(VEHICLE_AUTO_REVERSE_UNSTICK_MIN_TICKS
            + fraction * (VEHICLE_AUTO_REVERSE_UNSTICK_MAX_TICKS - VEHICLE_AUTO_REVERSE_UNSTICK_MIN_TICKS));
    }

    private double autoAssistApproachDistance() {
        FccCompositeControl control = findControlByAnyLabel(AUTO_APPROACH_DISTANCE_LABEL,
            "Approach Distance", "Slowdown Distance", "Arrival Slowdown");
        double value = control == null ? AUTO_APPROACH_DISTANCE_DEFAULT
            : controlValues.getOrDefault(control.id(), control.neutralValue());
        return clampDouble(value, AUTO_APPROACH_DISTANCE_MIN, AUTO_APPROACH_DISTANCE_MAX);
    }

    private double autoAssistAnnounceIntervalBlocks() {
        FccCompositeControl control = findControlByAnyLabel(AUTO_ANNOUNCE_INTERVAL_LABEL,
            "Announcement Interval", "Announce Every", "Assistant Interval");
        double value = control == null ? AUTO_ANNOUNCE_INTERVAL_DEFAULT
            : controlValues.getOrDefault(control.id(), control.neutralValue());
        return clampDouble(value, AUTO_ANNOUNCE_INTERVAL_MIN, AUTO_ANNOUNCE_INTERVAL_MAX);
    }

    private long autoAssistReverseTicks() {
        FccCompositeControl control = findControlByAnyLabel(AUTO_REVERSE_TIME_LABEL,
            "Reverse Time", "Reverse Duration", "Unstick Reverse Time");
        double seconds = control == null ? AUTO_REVERSE_TIME_DEFAULT_SECONDS
            : controlValues.getOrDefault(control.id(), control.neutralValue());
        seconds = clampDouble(seconds, AUTO_REVERSE_TIME_MIN_SECONDS, AUTO_REVERSE_TIME_MAX_SECONDS);
        return Math.max(1L, Math.round(seconds * 20.0));
    }

    private long vehicleReverseTicksLeft() {
        if (vehicleAutoReverseUnstickUntilTick == Long.MIN_VALUE) return autoAssistReverseTicks();
        return Math.max(0L, vehicleAutoReverseUnstickUntilTick - serverTickCount);
    }

    private long vehicleAutoStraightEscapeTicks(double evadeRadius) {
        double fraction = (clampDouble(evadeRadius, VEHICLE_AUTO_EVADE_RADIUS_MIN, VEHICLE_AUTO_EVADE_RADIUS_MAX)
            - VEHICLE_AUTO_EVADE_RADIUS_MIN) / (VEHICLE_AUTO_EVADE_RADIUS_MAX - VEHICLE_AUTO_EVADE_RADIUS_MIN);
        return Math.round(VEHICLE_AUTO_STRAIGHT_ESCAPE_MIN_TICKS
            + fraction * (VEHICLE_AUTO_STRAIGHT_ESCAPE_MAX_TICKS - VEHICLE_AUTO_STRAIGHT_ESCAPE_MIN_TICKS));
    }

    private void updateVehicleAutoDebug(String debug) {
        lastVehicleAutoDebug = debug == null || debug.isBlank() ? "auto=unknown" : debug;
        if (pathfinderDebugLoggingEnabled() && hasServerTickElapsed(lastVehicleAutoDebugLogTick, VEHICLE_AUTO_DEBUG_LOG_TICKS)) {
            lastVehicleAutoDebugLogTick = serverTickCount;
            CreateStats.LOGGER.info("[PFDEBUG/FCC] pos={} {}", getBlockPos(), lastVehicleAutoDebug);
        }
    }

    private static boolean pathfinderDebugLoggingEnabled() {
        return DebugLogging.fccVerboseEnabled();
    }

    private String autoHoldReason() {
        if (pathfinderComputerPos != null) {
            if (!pathfinderRouteAccepted) {
                return "pathfinder rejected status=" + basePathfinderStatus(pathfinderRouteStatus);
            }
            if (pathfinderGuidanceWaypoints.size() < 2) {
                return "pathfinder route too short wp=" + pathfinderGuidanceWaypoints.size();
            }
            if (isPathfinderGuidanceStale()) {
                return "pathfinder stale age=" + pathfinderGuidanceAgeTicks() + "t";
            }
            return "pathfinder projection failed wp=" + pathfinderRouteWaypointIndex + "/" + pathfinderGuidanceWaypoints.size();
        }
        BlockPos navPos = sensors != null ? sensors.effectiveNavTablePos() : navTablePos;
        if (navPos == null) {
            return "no nav table target source";
        }
        BlockEntity navBe = SableBridge.getBlockEntityOnContraption(level, navPos);
        if (navBe == null) {
            return "nav table block missing pos=" + navPos.toShortString();
        }
        Vec3 navTarget = SableBridge.navTableTarget(navBe);
        if (navTarget == null) {
            return "nav table has no target pos=" + navPos.toShortString();
        }
        return "target resolver returned null navTarget=" + formatVec(navTarget);
    }

    private static String compactPathfinderStatus(String status) {
        String compact = basePathfinderStatus(status);
        return compact.length() <= 220 ? compact : compact.substring(0, 217) + "...";
    }

    private static String basePathfinderStatus(String status) {
        if (status == null || status.isBlank()) return "unknown";
        String compact = status;
        String prefix = "Hold: pathfinder rejected status=";
        while (compact.startsWith(prefix)) {
            compact = compact.substring(prefix.length());
        }
        return compact;
    }

    private double autoTargetSpeedForDistance(double distance) {
        if (distance <= VEHICLE_AUTO_CRAWL_RADIUS) return VEHICLE_AUTO_CRAWL_SPEED;
        if (distance <= autoAssistApproachDistance()) return VEHICLE_AUTO_APPROACH_SPEED;
        return VEHICLE_AUTO_CRUISE_SPEED;
    }

    @Nullable
    private VehicleAutoDriveTarget resolveAutoDriveTarget() {
        boolean pathfinderMode = isPathfinderModeActive();
        boolean navMode = isNavTableModeActive();
        if (pathfinderMode) {
            VehicleAutoDriveTarget pathfinderTarget = resolvePathfinderAutoDriveTarget();
            if (pathfinderTarget != null) return pathfinderTarget;
            return navMode ? resolveNavTableAutoDriveTarget() : null;
        }
        if (navMode) {
            return resolveNavTableAutoDriveTarget();
        }
        VehicleAutoDriveTarget pathfinderTarget = resolvePathfinderAutoDriveTarget();
        if (pathfinderTarget != null) return pathfinderTarget;
        return resolveNavTableAutoDriveTarget();
    }

    @Nullable
    private VehicleAutoDriveTarget resolveNavTableAutoDriveTarget() {
        BlockPos navPos = sensors != null ? sensors.effectiveNavTablePos() : navTablePos;
        if (navPos == null) return null;
        BlockEntity navBe = SableBridge.getBlockEntityOnContraption(level, navPos);
        if (navBe == null) return null;
        Vec3 navTarget = SableBridge.navTableTarget(navBe);
        if (navTarget == null) return null;
        return new VehicleAutoDriveTarget(navTarget, navTableMaxSpeed, VehicleAutoDriveSource.NAV_TABLE, 0, 0);
    }

    public void setNavTableMaxSpeed(String text) {
        try {
            float v = Float.parseFloat(text.trim());
            this.navTableMaxSpeed = Math.max(1.0f, Math.min(32.0f, v));
            this.setChanged();
        } catch (NumberFormatException ignored) {}
    }

    public void setPathfinderLookaheadScale(String text) {
        try {
            float v = Float.parseFloat(text.trim());
            this.pathfinderLookaheadScale = Math.max(0.5f, Math.min(3.0f, v));
            this.setChanged();
        } catch (NumberFormatException ignored) {}
    }

    @Nullable
    private VehicleAutoDriveTarget resolvePathfinderAutoDriveTarget() {
        if (!pathfinderRouteAccepted || pathfinderGuidanceWaypoints.size() < 2 || isPathfinderGuidanceStale()) {
            return null;
        }
        org.joml.Vector3d currentPos = vehicleNavigationWorldPosition();
        PathfinderRouteProjection projection = projectOntoPathfinderRoute(currentPos);
        if (projection == null) {
            return null;
        }
        PathfinderElasticTarget elasticTarget = pathfinderElasticTarget(projection);
        if (elasticTarget == null) {
            return null;
        }
        int previousIndex = pathfinderRouteWaypointIndex;
        pathfinderRouteWaypointIndex = pathfinderProgressIndexForProjection(projection);
        if (previousIndex != pathfinderRouteWaypointIndex) {
            setChanged();
            broadcastSnapshot();
        }
        return new VehicleAutoDriveTarget(elasticTarget.point(), elasticTarget.speedLimit(),
            VehicleAutoDriveSource.PATHFINDER, pathfinderRouteWaypointIndex, pathfinderGuidanceWaypoints.size());
    }

    @Nullable
    private PathfinderElasticTarget pathfinderElasticTarget(PathfinderRouteProjection projection) {
        double lookahead = pathfinderTrackingLookahead();
        lookahead = pathfinderTurnLimitedLookahead(projection, lookahead);
        int maxIndex = pathfinderGuidanceWaypoints.size() - 1;
        int segment = Math.max(0, Math.min(projection.segmentIndex(), maxIndex - 1));
        Vec3 cursor = projection.point();
        double remaining = lookahead;
        for (int i = segment; i < maxIndex; i++) {
            Vec3 next = pathfinderGuidanceWaypoints.get(i + 1);
            double segmentLength = horizontalDistance(cursor, next);
            if (segmentLength > 1.0e-4 && remaining <= segmentLength) {
                double t = remaining / segmentLength;
                Vec3 point = lerp(cursor, next, t);
                return new PathfinderElasticTarget(point, i + 1, pathfinderSpeedLimitRange(segment + 1, i + 1));
            }
            remaining -= segmentLength;
            cursor = next;
        }
        return new PathfinderElasticTarget(pathfinderGuidanceWaypoints.get(maxIndex), maxIndex,
            pathfinderSpeedLimitRange(segment + 1, maxIndex));
    }

    private double pathfinderTrackingLookahead() {
        double wheelbase = vehicleWheelbaseBlocks();
        double track = vehicleTrackWidthBlocks();
        double scale = pathfinderLookaheadScale;
        double minLookahead = PATHFINDER_ELASTIC_LOOKAHEAD_MIN * scale;
        double lookahead = minLookahead + currentHorizontalSpeed() * PATHFINDER_ELASTIC_LOOKAHEAD_SPEED_GAIN * scale;
        if (Double.isFinite(wheelbase)) {
            lookahead = Math.max(lookahead, (wheelbase * 0.75 + 2.0) * scale);
        }
        if (Double.isFinite(track)) {
            lookahead = Math.max(lookahead, (track + 2.5) * scale);
        }
        double maxLookahead = PATHFINDER_ELASTIC_LOOKAHEAD_MAX * scale;
        if (Double.isFinite(wheelbase)) {
            maxLookahead = Math.max(maxLookahead, (wheelbase * 1.35 + 4.0) * scale);
        }
        return clampDouble(lookahead, minLookahead, maxLookahead);
    }

    private double pathfinderTurnLimitedLookahead(PathfinderRouteProjection projection, double requestedLookahead) {
        int maxIndex = pathfinderGuidanceWaypoints.size() - 1;
        if (maxIndex < 2) return requestedLookahead;
        int segment = Math.max(0, Math.min(projection.segmentIndex(), maxIndex - 1));
        Vec3 cursor = projection.point();
        double distanceToTurn = 0.0;
        Vec3 previousWaypoint = pathfinderGuidanceWaypoints.get(segment);
        for (int cornerIndex = segment + 1; cornerIndex < maxIndex; cornerIndex++) {
            Vec3 corner = pathfinderGuidanceWaypoints.get(cornerIndex);
            Vec3 next = pathfinderGuidanceWaypoints.get(cornerIndex + 1);
            distanceToTurn += horizontalDistance(cursor, corner);
            double angle = pathfinderTurnAngleDegrees(previousWaypoint, corner, next);
            if (angle >= PATHFINDER_TURN_LOOKAHEAD_ANGLE_DEG) {
                double wheelbase = vehicleWheelbaseBlocks();
                double turnCarry = 3.0 + currentHorizontalSpeed() * 0.22;
                if (Double.isFinite(wheelbase)) {
                    turnCarry = Math.max(turnCarry, wheelbase * 0.55);
                }
                turnCarry = clampDouble(turnCarry, PATHFINDER_TURN_LOOKAHEAD_MIN, PATHFINDER_TURN_LOOKAHEAD_MAX);
                double turnBound = distanceToTurn + turnCarry;
                return Math.max(PATHFINDER_TURN_LOOKAHEAD_MIN, Math.min(requestedLookahead, turnBound));
            }
            if (distanceToTurn > requestedLookahead) {
                break;
            }
            cursor = corner;
            previousWaypoint = corner;
        }
        return requestedLookahead;
    }

    private static double pathfinderTurnAngleDegrees(Vec3 previous, Vec3 corner, Vec3 next) {
        double ax = corner.x - previous.x;
        double az = corner.z - previous.z;
        double bx = next.x - corner.x;
        double bz = next.z - corner.z;
        double al = Math.sqrt(ax * ax + az * az);
        double bl = Math.sqrt(bx * bx + bz * bz);
        if (al < 1.0e-4 || bl < 1.0e-4) return 0.0;
        double dot = (ax * bx + az * bz) / (al * bl);
        dot = clampDouble(dot, -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    @Nullable
    private PathfinderRouteProjection projectOntoPathfinderRoute(org.joml.Vector3d currentPos) {
        if (pathfinderGuidanceWaypoints.size() < 2) return null;
        int maxSegment = pathfinderGuidanceWaypoints.size() - 2;
        int hint = Math.max(0, Math.min(pathfinderRouteWaypointIndex, maxSegment));
        PathfinderRouteProjection best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= maxSegment; i++) {
            Vec3 a = pathfinderGuidanceWaypoints.get(i);
            Vec3 b = pathfinderGuidanceWaypoints.get(i + 1);
            double t = projectionT(currentPos, a, b);
            Vec3 point = lerp(a, b, t);
            double dx = point.x - currentPos.x;
            double dz = point.z - currentPos.z;
            double distanceSq = dx * dx + dz * dz;
            double behindPenalty = i < hint ? (hint - i) * 18.0 : 0.0;
            double score = distanceSq + behindPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = new PathfinderRouteProjection(i, t, point, distanceSq);
            }
        }
        return best;
    }

    private double currentHorizontalSpeed() {
        org.joml.Vector3d velocity = SableBridge.linearVelocityWorld(this);
        return velocity == null ? 0.0 : Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private double currentForwardSpeed(double headingDeg) {
        org.joml.Vector3d velocity = SableBridge.linearVelocityWorld(this);
        if (velocity == null || !Double.isFinite(headingDeg)) return Double.NaN;
        double radians = Math.toRadians(headingDeg);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        return velocity.x * forwardX + velocity.z * forwardZ;
    }

    private static double autoHoldBrakeThrottle(double currentSpeed, double forwardSpeed) {
        if (!Double.isFinite(currentSpeed) || currentSpeed <= VEHICLE_AUTO_HOLD_BRAKE_SPEED) return 0.0;
        if (!Double.isFinite(forwardSpeed) || Math.abs(forwardSpeed) <= VEHICLE_AUTO_HOLD_BRAKE_SPEED) return 0.0;
        double magnitude = clampDouble(Math.abs(forwardSpeed) * VEHICLE_AUTO_HOLD_BRAKE_GAIN, 0.05, VEHICLE_AUTO_HOLD_BRAKE_MAX);
        return -Math.signum(forwardSpeed) * magnitude;
    }

    private double pathfinderSpeedLimitRange(int fromWaypoint, int toWaypoint) {
        if (pathfinderGuidanceSpeeds.isEmpty()) return Double.NaN;
        double limit = Double.POSITIVE_INFINITY;
        int from = Math.max(0, Math.min(fromWaypoint, pathfinderGuidanceSpeeds.size() - 1));
        int to = Math.max(from, Math.min(toWaypoint, pathfinderGuidanceSpeeds.size() - 1));
        for (int i = from; i <= to; i++) {
            float speed = pathfinderGuidanceSpeeds.get(i);
            if (Float.isFinite(speed) && speed > 0f) limit = Math.min(limit, speed);
        }
        return Double.isFinite(limit) ? limit : Double.NaN;
    }

    private int pathfinderProgressIndexForProjection(PathfinderRouteProjection projection) {
        int maxIndex = pathfinderGuidanceWaypoints.size() - 1;
        if (maxIndex <= 0) return 0;
        int progress = Math.max(0, Math.min(projection.segmentIndex(), maxIndex));
        if (projection.segmentIndex() >= maxIndex - 1 && projection.segmentT() > 0.85) {
            progress = maxIndex;
        }
        if (pathfinderRouteWaypointIndex > 0 && progress < pathfinderRouteWaypointIndex) {
            progress = Math.max(0, pathfinderRouteWaypointIndex - 1);
        }
        return Math.max(0, Math.min(progress, maxIndex));
    }

    private boolean hasUsablePathfinderRoute() {
        return pathfinderRouteAccepted && pathfinderGuidanceWaypoints.size() >= 2 && !isPathfinderGuidanceStale();
    }

    @Nullable
    public RouteArrowSnapshot routeArrowSnapshot() {
        boolean anyAuto = isVehicleControlActive(AUTO_DRIVE_LABEL, "Auto Drive", "Self Drive", "Self Driving")
            || isPathfinderModeActive() || isNavTableModeActive();
        if (!anyAuto) return null;
        if (!hasUsablePathfinderRoute()) return null;
        Vec3 origin = frontWheelArrowOrigin();
        PathfinderRouteProjection projection = projectOntoPathfinderRoute(new org.joml.Vector3d(origin.x, origin.y, origin.z));
        if (projection == null) return null;
        PathfinderElasticTarget target = pathfinderElasticTarget(projection);
        if (target == null) return null;
        Vec3 end = clampArrowEnd(origin, target.point(), 2.0, 3.0);
        Vec3 control = arrowControlPoint(origin, end);
        return new RouteArrowSnapshot(origin, control, end, true, false);
    }

    private Vec3 frontWheelArrowOrigin() {
        org.joml.Vector3d front = averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL");
        if (front == null) front = currentContraptionWorldPosition();
        return new Vec3(front.x, front.y + 0.35, front.z);
    }

    private Vec3 clampArrowEnd(Vec3 origin, Vec3 target, double minLength, double maxLength) {
        double dx = target.x - origin.x;
        double dz = target.z - origin.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) {
            double heading = currentHeadingDeg();
            dx = Math.sin(Math.toRadians(heading));
            dz = Math.cos(Math.toRadians(heading));
            length = 1.0;
        }
        double clamped = clampDouble(length, minLength, maxLength);
        double scale = clamped / length;
        return new Vec3(origin.x + dx * scale, origin.y, origin.z + dz * scale);
    }

    private Vec3 arrowControlPoint(Vec3 origin, Vec3 end) {
        Vec3 midpoint = lerp(origin, end, 0.55);
        int nextIndex = Math.max(0, Math.min(pathfinderRouteWaypointIndex + 1, pathfinderGuidanceWaypoints.size() - 1));
        Vec3 bendTarget = pathfinderGuidanceWaypoints.get(nextIndex);
        double dx = bendTarget.x - midpoint.x;
        double dz = bendTarget.z - midpoint.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) return midpoint;
        double bend = Math.min(0.65, length * 0.18);
        return new Vec3(midpoint.x + dx / length * bend, midpoint.y, midpoint.z + dz / length * bend);
    }

    private boolean isPathfinderGuidanceStale() {
        return pathfinderRouteAccepted && level != null && pathfinderRouteLastSeenTick > 0
            && level.getGameTime() - pathfinderRouteLastSeenTick > PATHFINDER_GUIDANCE_STALE_TICKS;
    }

    private long pathfinderGuidanceAgeTicks() {
        return level == null || pathfinderRouteLastSeenTick <= 0 ? -1L : level.getGameTime() - pathfinderRouteLastSeenTick;
    }

    private int nearestPathfinderWaypointIndex() {
        if (pathfinderGuidanceWaypoints.isEmpty()) return 0;
        org.joml.Vector3d currentPos = vehicleNavigationWorldPosition();
        int bestIndex = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < pathfinderGuidanceWaypoints.size(); i++) {
            double distance = horizontalDistance(pathfinderGuidanceWaypoints.get(i), currentPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static double horizontalDistance(Vec3 target, org.joml.Vector3d currentPos) {
        double dx = target.x - currentPos.x;
        double dz = target.z - currentPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(org.joml.Vector3d first, org.joml.Vector3d second) {
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String formatVec(@Nullable Vec3 point) {
        if (point == null) return "none";
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", point.x, point.y, point.z);
    }

    private static String formatVector(@Nullable org.joml.Vector3d point) {
        if (point == null) return "none";
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", point.x, point.y, point.z);
    }

    private static String formatBlockPos(@Nullable BlockPos pos) {
        return pos == null ? "none" : pos.toShortString();
    }

    private static double projectionT(org.joml.Vector3d point, Vec3 a, Vec3 b) {
        double ax = b.x - a.x;
        double az = b.z - a.z;
        double lengthSq = ax * ax + az * az;
        if (lengthSq < 1.0e-8) return 0.0;
        double px = point.x - a.x;
        double pz = point.z - a.z;
        return clampDouble((px * ax + pz * az) / lengthSq, 0.0, 1.0);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        double clamped = clampDouble(t, 0.0, 1.0);
        return new Vec3(a.x + (b.x - a.x) * clamped, a.y + (b.y - a.y) * clamped, a.z + (b.z - a.z) * clamped);
    }

    private void reportPathfinderRouteProgress(VehicleAutoDriveTarget target, double distance, double targetSpeed) {
        String status = String.format(java.util.Locale.ROOT, "Executing scanner route: WP %d/%d %.0fm @ %.1fm/s target=%s",
            target.waypointIndex() + 1, Math.max(1, target.waypointCount()), distance, targetSpeed, formatVec(target.position()));
        reportPathfinderStatusToComputer(status, true);
    }

    private void reportPathfinderRouteReverse(VehicleAutoDriveTarget target, double distance, double reverseError) {
        String status = String.format(java.util.Locale.ROOT, "Reversing scanner route: WP %d/%d %.0fm err %.0f target=%s",
            target.waypointIndex() + 1, Math.max(1, target.waypointCount()), distance, reverseError, formatVec(target.position()));
        reportPathfinderStatusToComputer(status, true);
    }

    private void reportPathfinderRouteArrived(VehicleAutoDriveTarget target, double distance) {
        String status = String.format(java.util.Locale.ROOT, "Arrived scanner route: WP %d/%d %.1fm target=%s",
            target.waypointIndex() + 1, Math.max(1, target.waypointCount()), distance, formatVec(target.position()));
        reportPathfinderStatusToComputer(status, true);
    }

    private void reportPathfinderSafetyHold(String status) {
        reportPathfinderStatusToComputer(status, false);
    }

    private void reportPathfinderStatusToComputer(String status, boolean accepted) {
        if (status == null || !(level instanceof ServerLevel serverLevel)) return;
        status = compactPathfinderStatus(status);
        long now = serverLevel.getGameTime();
        boolean sameStatus = status.equals(lastPathfinderProgressStatus);
        if (lastPathfinderProgressReportTick != Long.MIN_VALUE
            && now - lastPathfinderProgressReportTick < PATHFINDER_PROGRESS_REPORT_INTERVAL_TICKS) {
            if (sameStatus) return;
            if (!isImportantPathfinderStatus(status)) return;
        }
        if (!status.equals(this.pathfinderRouteStatus)) {
            this.pathfinderRouteStatus = status;
            setChanged();
            syncClientVisualState();
            broadcastSnapshot();
        }
        if (pathfinderComputerPos == null) {
            lastPathfinderProgressStatus = status;
            lastPathfinderProgressReportTick = now;
            return;
        }
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(serverLevel, pathfinderComputerPos);
        if (!(blockEntity instanceof PathfinderComputerBE) && serverLevel.isLoaded(pathfinderComputerPos)) {
            blockEntity = serverLevel.getBlockEntity(pathfinderComputerPos);
        }
        if (blockEntity instanceof PathfinderComputerBE computer) {
            computer.markFccGuidanceLink(this.worldPosition, accepted, status,
                this.pathfinderRouteWaypointIndex, this.pathfinderRouteWaypointCount);
            lastPathfinderProgressStatus = status;
            lastPathfinderProgressReportTick = now;
        }
    }

    private static boolean isImportantPathfinderStatus(String status) {
        return status.startsWith("Hold:") || status.startsWith("Accepted") || status.startsWith("Rejected")
            || status.startsWith("Arrived scanner route") || status.startsWith("Reversing scanner route");
    }

    private org.joml.Vector3d currentContraptionWorldPosition() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            org.joml.Vector3d localCom = SableBridge.centerOfMassLocal(subLevel);
            org.joml.Vector3d worldCom = SableBridge.localToWorld(subLevel, localCom);
            if (worldCom != null && Double.isFinite(worldCom.x) && Double.isFinite(worldCom.y) && Double.isFinite(worldCom.z)) {
                return worldCom;
            }
            org.joml.Vector3d origin = SableBridge.originWorld(subLevel);
            if (origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z)) {
                return origin;
            }
        }
        return new org.joml.Vector3d(
                getBlockPos().getX() + 0.5,
                getBlockPos().getY() + 0.5,
                getBlockPos().getZ() + 0.5);
    }

    private org.joml.Vector3d vehicleNavigationWorldPosition() {
        org.joml.Vector3d front = averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL");
        return front != null ? front : currentContraptionWorldPosition();
    }

    private double vehicleWheelbaseBlocks() {
        org.joml.Vector3d front = averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL");
        org.joml.Vector3d rear = averageVehicleZoneWorldPosition("REAR_LEFT_WHEEL", "REAR_RIGHT_WHEEL");
        if (front != null && rear != null) {
            double distance = horizontalDistance(front, rear);
            if (distance > 0.5) return distance;
        }
        org.joml.Vector3d mid = averageVehicleZoneWorldPosition("MID_LEFT_WHEEL", "MID_RIGHT_WHEEL");
        if (front != null && mid != null) {
            double distance = horizontalDistance(front, mid) * 2.0;
            if (distance > 0.5) return distance;
        }
        return Double.NaN;
    }

    private double vehicleTrackWidthBlocks() {
        org.joml.Vector3d frontLeft = averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL");
        org.joml.Vector3d frontRight = averageVehicleZoneWorldPosition("FRONT_RIGHT_WHEEL");
        if (frontLeft != null && frontRight != null) {
            double distance = horizontalDistance(frontLeft, frontRight);
            if (distance > 0.5) return distance;
        }
        org.joml.Vector3d rearLeft = averageVehicleZoneWorldPosition("REAR_LEFT_WHEEL");
        org.joml.Vector3d rearRight = averageVehicleZoneWorldPosition("REAR_RIGHT_WHEEL");
        if (rearLeft != null && rearRight != null) {
            double distance = horizontalDistance(rearLeft, rearRight);
            if (distance > 0.5) return distance;
        }
        return Double.NaN;
    }

    public BlockPos pathfinderPlanningOrigin() {
        org.joml.Vector3d pos = vehicleNavigationWorldPosition();
        return BlockPos.containing(pos.x, pos.y, pos.z);
    }

    public float pathfinderPlanningYawDeg() {
        double heading = currentHeadingDeg();
        return (float) (Double.isFinite(heading) ? wrapDegrees(heading) : 0.0);
    }

    public double pathfinderVehicleSpeedMs() {
        org.joml.Vector3d velocity = SableBridge.linearVelocityWorld(this);
        if (velocity == null) return Double.NaN;
        return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
    }

    public boolean pathfinderHasFrontWheelZones() {
        return averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL") != null;
    }

    public String pathfinderPlanningDebugLine() {
        return String.format(java.util.Locale.ROOT, "origin=%s yaw=%.1f nav=%s wheelbase=%.1f track=%.1f clearance=%.1f evade=%.1f",
            pathfinderPlanningOrigin().toShortString(), pathfinderPlanningYawDeg(), formatVec(pathfinderNavTarget()),
            vehicleWheelbaseBlocks(), vehicleTrackWidthBlocks(), pathfinderVehicleClearanceRadius(), vehicleAutoEvadeRadius());
    }

    public double pathfinderVehicleClearanceRadius() {
        double track = vehicleTrackWidthBlocks();
        double radius = 1.75;
        if (Double.isFinite(track)) {
            radius = Math.max(radius, track * 0.5 + 0.75);
        }
        return clampDouble(radius, 1.25, 4.0);
    }

    @Nullable
    public Vec3 pathfinderNavTarget() {
        BlockPos navPos = sensors != null ? sensors.effectiveNavTablePos() : navTablePos;
        if (navPos == null) return null;
        BlockEntity navBe = SableBridge.getBlockEntityOnContraption(level, navPos);
        if (navBe == null) return null;
        return SableBridge.navTableTarget(navBe);
    }

    public boolean isPathfinderModeActive() {
        return isVehicleControlActive(AUTO_DRIVE_PATHFINDER_LABEL,
            "Pathfinder Auto Drive", "Pathfinder Drive", "Pathfinder Mode", "Pathfinder Auto");
    }

    public boolean isAnyAutoDriveActive() {
        return isVehicleControlActive(AUTO_DRIVE_LABEL, "Auto Drive", "Self Drive", "Self Driving")
            || isPathfinderModeActive() || isNavTableModeActive();
    }

    public boolean isNavTableModeActive() {
        return isVehicleControlActive(AUTO_DRIVE_NAV_LABEL,
            "Nav Table Auto Drive", "Nav Auto Drive", "Nav Table Drive", "Nav Auto");
    }

    private double currentHeadingDeg() {
        double wheelNoseHeading = vehicleNoseHeadingDeg();
        if (Double.isFinite(wheelNoseHeading)) {
            lastKnownHeadingDeg = wheelNoseHeading;
            return wheelNoseHeading;
        }
        double airshipNoseHeading = airshipNoseHeadingDeg();
        if (Double.isFinite(airshipNoseHeading)) {
            lastKnownHeadingDeg = airshipNoseHeading;
            return airshipNoseHeading;
        }
        org.joml.Vector3d velocity = SableBridge.linearVelocityWorld(this);
        if (velocity != null) {
            double horiz = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horiz > 1.0e-6) {
                double heading = yawDegreesFromVector(velocity.x, velocity.z);
                lastKnownHeadingDeg = heading;
                return heading;
            }
        }

        return Double.isFinite(lastKnownHeadingDeg) ? lastKnownHeadingDeg : 0.0;
    }

    private double vehicleNoseHeadingDeg() {
        org.joml.Vector3d front = averageVehicleZoneWorldPosition("FRONT_LEFT_WHEEL", "FRONT_RIGHT_WHEEL");
        org.joml.Vector3d rear = averageVehicleZoneWorldPosition("REAR_LEFT_WHEEL", "REAR_RIGHT_WHEEL");
        if (front != null && rear != null) {
            double dx = front.x - rear.x;
            double dz = front.z - rear.z;
            if (dx * dx + dz * dz > 0.25) {
                return yawDegreesFromVector(dx, dz);
            }
        }
        org.joml.Vector3d mid = averageVehicleZoneWorldPosition("MID_LEFT_WHEEL", "MID_RIGHT_WHEEL");
        if (front != null && mid != null) {
            double dx = front.x - mid.x;
            double dz = front.z - mid.z;
            if (dx * dx + dz * dz > 0.25) {
                return yawDegreesFromVector(dx, dz);
            }
        }
        if (front != null) {
            org.joml.Vector3d current = currentContraptionWorldPosition();
            double dx = front.x - current.x;
            double dz = front.z - current.z;
            if (dx * dx + dz * dz > 0.25) {
                return yawDegreesFromVector(dx, dz);
            }
        }
        return Double.NaN;
    }

    private double airshipNoseHeadingDeg() {
        org.joml.Vector3d front = averageVehicleZoneWorldPosition("FRONT_MAIN_THRUST");
        org.joml.Vector3d rear = averageVehicleZoneWorldPosition("BACK_MAIN_THRUST");
        if (front != null && rear != null) {
            double dx = front.x - rear.x;
            double dz = front.z - rear.z;
            if (dx * dx + dz * dz > 0.25) {
                return yawDegreesFromVector(dx, dz);
            }
        }
        return Double.NaN;
    }

    private static double yawDegreesFromVector(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    @Nullable
    private org.joml.Vector3d averageVehicleZoneWorldPosition(String... zoneKeys) {
        org.joml.Vector3d sum = new org.joml.Vector3d();
        int count = 0;
        for (String zoneKey : zoneKeys) {
            FccGroup group = vehicleZoneGroup(zoneKey);
            if (group == null) continue;
            int wheelCount = 0;
            org.joml.Vector3d wheelSum = new org.joml.Vector3d();
            for (UUID deviceId : group.deviceIds()) {
                FccDevice device = findDevice(deviceId);
                if (device == null || !device.enabled() || device.category() != FccDeviceCategory.WHEEL_MOUNT) continue;
                org.joml.Vector3d world = deviceWorldCenter(device.primaryPos());
                if (world == null) continue;
                wheelSum.add(world);
                wheelCount++;
            }
            if (wheelCount > 0) {
                sum.add(wheelSum.div(wheelCount));
                count++;
                continue;
            }
            org.joml.Vector3d fallbackSum = new org.joml.Vector3d();
            int fallbackCount = 0;
            for (UUID deviceId : group.deviceIds()) {
                FccDevice device = findDevice(deviceId);
                if (device == null || !device.enabled() || device.category() == FccDeviceCategory.REDSTONE_LINK) continue;
                org.joml.Vector3d world = deviceWorldCenter(device.primaryPos());
                if (world == null) continue;
                fallbackSum.add(world);
                fallbackCount++;
            }
            if (fallbackCount > 0) {
                sum.add(fallbackSum.div(fallbackCount));
                count++;
            }
        }
        if (count <= 0) return null;
        return sum.div(count);
    }

    @Nullable
    private FccGroup vehicleZoneGroup(String zoneKey) {
        UUID groupId = findVehicleZoneGroupId(zoneKey);
        return groupId == null ? null : findGroup(groupId);
    }

    @Nullable
    private org.joml.Vector3d deviceWorldCenter(@Nullable BlockPos pos) {
        if (pos == null) return null;
        org.joml.Vector3d localCenter = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            org.joml.Vector3d world = SableBridge.localToWorld(subLevel, localCenter);
            if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
                return world;
            }
        }
        return localCenter;
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    @Nullable
    private UUID findVehicleZoneGroupId(String zoneKey) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        return zone == null ? null : findDiagramGroupId(zone);
    }

    private boolean isVehicleWheelInvertedNullable(@Nullable UUID groupId, @Nullable FccCompositeControl thrustControl) {
        return groupId != null && isVehicleWheelInverted(groupId, thrustControl);
    }

    private String vehicleZoneDebugLine(String zoneKey, @Nullable FccCompositeControl thrustControl) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return "zone?";
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return "group?";
        FccGroup group = findGroup(groupId);
        if (group == null) return "groupMissing";
        SteeringRows rows = steeringRowsForZone(zoneKey, group);
        int devices = group.deviceIds().size();
        int governors = vehicleZoneGovernorCount(zoneKey);
        boolean inverted = isVehicleWheelInverted(groupId, thrustControl);
        return (isVehicleZoneActive(group) ? "on" : "off")
                + ",d=" + devices
                + ",g=" + governors
                + ",inv=" + inverted
                + ",rows=" + rows.leftRow() + "/" + rows.rightRow() + ",ready=" + rows.ready();
    }

    private int applyVehicleWheelZoneDelta(String zoneKey,
                                           int delta,
                                           Map<BlockPos, Integer> rpmCommands,
                                           Set<BlockPos> reverseAllowedGovernors) {
        if (delta == 0) return 0;
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return 0;
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return 0;
        FccGroup group = findGroup(groupId);
        if (group == null || !isVehicleZoneActive(group)) return 0;

        int applied = 0;
        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device == null || !device.enabled()) continue;
            BlockPos governorPos = governorPosForDevice(device);
            if (governorPos == null) continue;
            int merged = clampVehicleRpm(rpmCommands.getOrDefault(governorPos, 0) + delta);
            rpmCommands.put(governorPos, merged);
            reverseAllowedGovernors.add(governorPos);
            applied++;
        }
        return applied;
    }

    private int vehicleZoneGovernorCount(String zoneKey) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return 0;
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return 0;
        FccGroup group = findGroup(groupId);
        if (group == null) return 0;
        int count = 0;
        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device == null || !device.enabled() || governorPosForDevice(device) == null) continue;
            count++;
        }
        return count;
    }

    @Nullable
    private BlockPos governorPosForDevice(FccDevice device) {
        if (device.secondaryPos() != null) return device.secondaryPos();
        for (FccBinding binding : getBindings()) {
            if (binding.prop().equals(device.primaryPos())) return binding.governor();
        }
        return null;
    }

    private boolean vehicleZoneSteeringReady(String zoneKey) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return false;
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return false;
        FccGroup group = findGroup(groupId);
        if (group == null) return false;
        return steeringRowsForZone(zoneKey, group).ready();
    }

    private int applyVehicleWheelZoneCommand(String zoneKey,
                                             @Nullable FccCompositeControl thrustControl,
                                             boolean driven,
                                             int baseRpm,
                                             double steerSigned,
                                             boolean steerable,
                                             Map<BlockPos, Integer> rpmCommands,
                                             Map<Integer, Integer> redstoneCommands,
                                             Set<BlockPos> reverseAllowedGovernors) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return 0;
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return 0;
        FccGroup group = findGroup(groupId);
        if (group == null || !isVehicleZoneActive(group)) return 0;

        boolean inverted = isVehicleWheelInverted(group.id(), thrustControl);
        int target = driven ? (inverted ? -baseRpm : baseRpm) : 0;

        int applied = 0;
        SteeringRows steeringRows = null;
        if (steerable) {
            steeringRows = steeringRowsForZone(zoneKey, group);
        }

        int leftSignal = 0;
        int rightSignal = 0;
        if (steerable) {
            int strength = clampSignal((int) Math.round(Math.abs(steerSigned) * 15.0));
            if (strength > 0) {
                if (steerSigned > 0.0) {
                    leftSignal = strength;
                } else {
                    rightSignal = strength;
                }
            }
            if (inverted) {
                int tmp = leftSignal;
                leftSignal = rightSignal;
                rightSignal = tmp;
            }
        }

        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device == null || !device.enabled()) continue;
            BlockPos governorPos = governorPosForDevice(device);
            if (governorPos == null) continue;
            rpmCommands.put(governorPos, target);
            reverseAllowedGovernors.add(governorPos);
            if (steerable) {
                if (device.primaryPos() != null && isWheelMountAt(device.primaryPos())) {
                    WheelMountSteeringOverrides.setForTick(device.primaryPos(), leftSignal, rightSignal, level.getGameTime());
                }
                applyWheelMountOverridesNearPosition(governorPos, leftSignal, rightSignal);
                applyWheelMountOverridesNearPosition(device.primaryPos(), leftSignal, rightSignal);
                applyWheelMountOverridesNearPosition(device.secondaryPos(), leftSignal, rightSignal);
            }
            applied++;
        }

        if (steerable && steeringRows != null && steeringRows.ready()) {
            redstoneCommands.put(steeringRows.leftRow(), leftSignal);
            redstoneCommands.put(steeringRows.rightRow(), rightSignal);
        }

        return applied;
    }

    private boolean isWheelMountAt(BlockPos worldPos) {
        if (!(level instanceof ServerLevel serverLevel) || worldPos == null) return false;
        BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, worldPos);
        return be != null && be.getClass().getName().endsWith("WheelMountBlockEntity");
    }

    private void applyWheelMountOverridesNearPosition(@Nullable BlockPos anchorPos, int leftSignal, int rightSignal) {
        if (!(level instanceof ServerLevel serverLevel) || anchorPos == null) return;
        java.util.Set<BlockPos> candidates = new java.util.LinkedHashSet<>();
        candidates.add(anchorPos);
        for (Direction dir : Direction.values()) {
            candidates.add(anchorPos.relative(dir));
        }
        int hits = 0;
        for (BlockPos candidatePos : candidates) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, candidatePos);
            if (be == null || !be.getClass().getName().endsWith("WheelMountBlockEntity")) continue;
            WheelMountSteeringOverrides.setForTick(candidatePos, leftSignal, rightSignal, level.getGameTime());
            hits++;
        }
        if ((leftSignal > 0 || rightSignal > 0)
                && (lastWheelProbeDebugTick == Long.MIN_VALUE || serverTickCount - lastWheelProbeDebugTick >= 20)) {
            lastWheelProbeDebugTick = serverTickCount;
            DebugLogging.fccDebug("[FCC/WHEEL_PROBE] governor={} tick={} left={} right={} candidates={} hits={}",
                    anchorPos, serverTickCount, leftSignal, rightSignal, candidates.size(), hits);
        }
    }

    private boolean isVehicleWheelInverted(UUID groupId, @Nullable FccCompositeControl thrustControl) {
        if (thrustControl != null) {
            for (FccControlRoute route : thrustControl.routes()) {
                if (!groupId.equals(route.targetGroupId())) continue;
                if (route.outputType() != FccOutputType.RPM_SCALE) continue;
                return route.invert();
            }
        }

        FccControlRoute fallback = null;
        for (FccCompositeControl control : controls) {
            for (FccControlRoute route : control.routes()) {
                if (!groupId.equals(route.targetGroupId())) continue;
                if (route.outputType() != FccOutputType.RPM_SCALE) continue;
                if (control.role() == FccControlRole.THRUST) return route.invert();
                if (fallback == null) fallback = route;
            }
        }
        return fallback == null || fallback.invert();
    }

    private boolean migrateVehicleWheelRouteInversionDefaults() {
        if (controls.isEmpty() || groups.isEmpty()) return false;
        Set<UUID> wheelGroupIds = new LinkedHashSet<>();
        for (FccGroup group : groups) {
            if (isVehicleWheelGroup(group)) wheelGroupIds.add(group.id());
        }
        if (wheelGroupIds.isEmpty()) return false;
        boolean changed = false;
        for (int controlIndex = 0; controlIndex < controls.size(); controlIndex++) {
            FccCompositeControl control = controls.get(controlIndex);
            List<FccControlRoute> routes = new ArrayList<>(control.routes().size());
            boolean routeChanged = false;
            for (FccControlRoute route : control.routes()) {
                if (route.outputType() == FccOutputType.RPM_SCALE
                        && wheelGroupIds.contains(route.targetGroupId())
                        && !route.invert()) {
                    routes.add(copyRoute(route, route.outputType(), route.scale(), true));
                    routeChanged = true;
                } else {
                    routes.add(route);
                }
            }
            if (!routeChanged) continue;
            controls.set(controlIndex, copyControl(control, control.label(), control.widgetType(), routes));
            changed = true;
        }
        return changed;
    }

    private SteeringRows steeringRowsForGroup(FccGroup group) {
        int left = -1;
        int right = -1;
        List<Integer> fallback = new ArrayList<>();
        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device == null || !device.enabled() || device.category() != FccDeviceCategory.REDSTONE_LINK) continue;
            int row = redstoneDeviceRow(device);
            if (row < 0 || !isRedstonePairComplete(row)) continue;
            fallback.add(row);
            String label = normalizeDiagramToken(device.label());
            if (left < 0 && (label.contains("left") || label.endsWith("l"))) {
                left = row;
                continue;
            }
            if (right < 0 && (label.contains("right") || label.endsWith("r"))) {
                right = row;
            }
        }
        fallback.sort(Integer::compareTo);
        if (left < 0 && !fallback.isEmpty()) left = fallback.get(0);
        if (right < 0) {
            for (Integer row : fallback) {
                if (row != left) {
                    right = row;
                    break;
                }
            }
        }
        return new SteeringRows(left, right);
    }

    private SteeringRows steeringRowsForZone(String zoneKey, FccGroup group) {
        SteeringRows grouped = steeringRowsForGroup(group);
        if (grouped.ready()) {
            return grouped;
        }

        List<Integer> globalRows = collectActiveRedstoneRows();
        if (globalRows.size() < 2) {
            return grouped;
        }

        if (zoneKey.startsWith("FRONT_")) {
            return new SteeringRows(globalRows.get(0), globalRows.get(1));
        }
        if (zoneKey.startsWith("REAR_")) {
            if (globalRows.size() >= 4) {
                return new SteeringRows(globalRows.get(2), globalRows.get(3));
            }
            return new SteeringRows(globalRows.get(0), globalRows.get(1));
        }
        return grouped;
    }

    private List<Integer> collectActiveRedstoneRows() {
        List<Integer> rows = new ArrayList<>();
        for (FccDevice device : devices) {
            if (!device.enabled() || device.category() != FccDeviceCategory.REDSTONE_LINK) continue;
            int row = redstoneDeviceRow(device);
            if (row < 0 || !isRedstonePairComplete(row) || rows.contains(row)) continue;
            rows.add(row);
        }
        rows.sort(Integer::compareTo);
        return rows;
    }

    private int equalizeVehicleWheelPair(String leftZoneKey,
                                         String rightZoneKey,
                                         Map<BlockPos, Integer> rpmCommands,
                                         Set<BlockPos> reverseAllowedGovernors) {
        List<BlockPos> governors = new ArrayList<>();
        governors.addAll(vehicleZoneGovernorPositions(leftZoneKey));
        governors.addAll(vehicleZoneGovernorPositions(rightZoneKey));
        if (governors.isEmpty()) return 0;

        int sum = 0;
        int count = 0;
        for (BlockPos governorPos : governors) {
            sum += rpmCommands.getOrDefault(governorPos, 0);
            count++;
        }
        if (count == 0) return 0;

        int equalized = clampVehicleRpm((int) Math.round(sum / (double) count));
        for (BlockPos governorPos : governors) {
            rpmCommands.put(governorPos, equalized);
            reverseAllowedGovernors.add(governorPos);
        }
        return governors.size();
    }

    private List<BlockPos> vehicleZoneGovernorPositions(String zoneKey) {
        DiagramZoneSpec zone = diagramZoneSpec(zoneKey);
        if (zone == null) return List.of();
        UUID groupId = findDiagramGroupId(zone);
        if (groupId == null) return List.of();
        FccGroup group = findGroup(groupId);
        if (group == null || !isVehicleZoneActive(group)) return List.of();

        List<BlockPos> positions = new ArrayList<>();
        for (UUID deviceId : group.deviceIds()) {
            FccDevice device = findDevice(deviceId);
            if (device == null || !device.enabled()) continue;
            BlockPos governorPos = governorPosForDevice(device);
            if (governorPos == null || positions.contains(governorPos)) continue;
            positions.add(governorPos);
        }
        return positions;
    }

    private void clearVehicleWheelTelemetry() {
        java.util.Arrays.fill(lastVehicleWheelRpm, 0);
        java.util.Arrays.fill(lastVehicleWheelSteer, 0);
        java.util.Arrays.fill(lastVehicleWheelActualRpm, 0);
        lastVehicleSteerModeOrdinal = -1;
    }

    private void captureVehicleWheelTelemetry(String zoneKey,
                                              Map<BlockPos, Integer> rpmCommands,
                                              double steerSigned,
                                              boolean steerable) {
        int index = vehicleWheelTelemetryIndex(zoneKey);
        if (index < 0) return;
        lastVehicleWheelRpm[index] = vehicleZoneCommandAverage(zoneKey, rpmCommands);
        lastVehicleWheelSteer[index] = steerable ? vehicleSteerSignalForTelemetry(steerSigned) : 0;
        lastVehicleWheelActualRpm[index] = vehicleZoneActualRpmAverage(zoneKey);
    }

    private int vehicleZoneActualRpmAverage(String zoneKey) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return 0;
        List<BlockPos> governors = vehicleZoneGovernorPositions(zoneKey);
        if (governors.isEmpty()) return 0;
        long sum = 0L;
        int count = 0;
        for (BlockPos governorPos : governors) {
            net.minecraft.world.level.block.entity.BlockEntity be =
                com.createstats.flight.bridge.SableBridge.getBlockEntityOnContraption(serverLevel, governorPos);
            if (!(be instanceof com.createstats.flight.blockentity.RpmGovernorBE)
                && serverLevel.isLoaded(governorPos)) {
                be = serverLevel.getBlockEntity(governorPos);
            }
            if (be instanceof com.createstats.flight.blockentity.RpmGovernorBE governor) {
                sum += governor.getAppliedRpm();
                count++;
            }
        }
        return count <= 0 ? 0 : clampVehicleRpm((int) Math.round((double) sum / count));
    }

    private int vehicleZoneCommandAverage(String zoneKey, Map<BlockPos, Integer> rpmCommands) {
        List<BlockPos> governors = vehicleZoneGovernorPositions(zoneKey);
        if (governors.isEmpty()) return 0;
        int sum = 0;
        int count = 0;
        for (BlockPos governorPos : governors) {
            sum += rpmCommands.getOrDefault(governorPos, 0);
            count++;
        }
        return count <= 0 ? 0 : clampVehicleRpm((int) Math.round(sum / (double) count));
    }

    private static int vehicleSteerSignalForTelemetry(double steerSigned) {
        int strength = clampSignal((int) Math.round(Math.abs(steerSigned) * 15.0));
        if (strength == 0) return 0;
        return steerSigned > 0.0 ? strength : -strength;
    }

    private static int vehicleWheelTelemetryIndex(String zoneKey) {
        return switch (zoneKey) {
            case "FRONT_LEFT_WHEEL" -> 0;
            case "FRONT_RIGHT_WHEEL" -> 1;
            case "MID_LEFT_WHEEL" -> 2;
            case "MID_RIGHT_WHEEL" -> 3;
            case "REAR_LEFT_WHEEL" -> 4;
            case "REAR_RIGHT_WHEEL" -> 5;
            default -> -1;
        };
    }

    private record SteeringRows(int leftRow, int rightRow) {
        private boolean ready() {
            return leftRow >= 0 && rightRow >= 0;
        }
    }

    private boolean isVehicleWheelGroup(FccGroup group) {
        DiagramZoneSpec zone = resolveDiagramZoneForGroup(group);
        return zone != null && isOtomobilDiagramZone(zone);
    }

    private boolean isVehicleZoneActive(FccGroup group) {
        return isZoneRouteEnabled(group) || isVehicleWheelGroup(group);
    }

    private boolean isControlActive(String label) {
        FccCompositeControl control = findControlByLabel(label);
        if (control == null) return false;
        double value = controlValues.getOrDefault(control.id(), control.neutralValue());
        return value >= control.inputMin() + (control.inputMax() - control.inputMin()) * 0.5;
    }

    private boolean isControlActiveAny(String... labels) {
        List<String> needles = new ArrayList<>();
        for (String label : labels) {
            String token = normalizeDiagramToken(label);
            if (!token.isEmpty()) needles.add(token);
        }
        if (needles.isEmpty()) return false;

        for (FccCompositeControl control : controls) {
            String token = normalizeDiagramToken(control.label());
            if (token.isEmpty()) continue;
            boolean matches = false;
            for (String needle : needles) {
                if (token.equals(needle) || token.contains(needle) || needle.contains(token)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;
            double value = controlValues.getOrDefault(control.id(), control.neutralValue());
            if (value >= control.inputMin() + (control.inputMax() - control.inputMin()) * 0.5) {
                return true;
            }
        }
        return false;
    }

    private boolean isVehicleControlActive(String preferredLabel, String... aliases) {
        if (isControlActiveAny(preferredLabel)) return true;
        for (String alias : aliases) {
            if (isControlActiveAny(alias)) return true;
        }
        for (FccCompositeControl control : controls) {
            String token = normalizeDiagramToken(control.label());
            if (token.isEmpty()) continue;
            boolean matches = false;
            for (String alias : aliases) {
                String aliasToken = normalizeDiagramToken(alias);
                if (!aliasToken.isEmpty() && (token.contains(aliasToken) || aliasToken.contains(token))) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;
            double value = controlValues.getOrDefault(control.id(), control.neutralValue());
            if (value >= control.inputMin() + (control.inputMax() - control.inputMin()) * 0.5) return true;
        }
        return false;
    }

    private double normalizeControlSigned(FccCompositeControl control) {
        double current = controlValues.getOrDefault(control.id(), control.neutralValue());
        if (current >= control.neutralValue()) {
            double span = control.inputMax() - control.neutralValue();
            return span <= 1.0e-9 ? 0.0 : clampDouble((current - control.neutralValue()) / span, -1.0, 1.0);
        }
        double span = control.neutralValue() - control.inputMin();
        return span <= 1.0e-9 ? 0.0 : clampDouble((current - control.neutralValue()) / span, -1.0, 1.0);
    }

    private static int clampVehicleRpm(int rpm) {
        return Math.max(-VEHICLE_MAX_WHEEL_RPM, Math.min(VEHICLE_MAX_WHEEL_RPM, rpm));
    }

    private Set<BlockPos> collectWorkbenchGovernorPositions() {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (FccBinding binding : getBindings()) {
            positions.add(binding.governor());
        }
        for (FccDevice device : devices) {
            if (!device.enabled() || device.secondaryPos() == null) continue;
            positions.add(device.secondaryPos());
        }
        return positions;
    }

    private void stopWorkbenchGovernors(ServerLevel serverLevel) {
        UUID id = getOrCreateUuid();
        for (BlockPos governorPos : collectWorkbenchGovernorPositions()) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, governorPos);
            if (be instanceof RpmGovernorBE governor) {
                governor.setCommand(getBlockPos(), id, 0);
            }
        }
    }

    private boolean updateFlipPrevention(ServerLevel serverLevel) {
        if (!isFlipPreventionEnabled()) {
            clearFlipPreventionState();
            return false;
        }

        double tiltDeg = sampleContraptionTiltDeg();
        if (!Double.isFinite(tiltDeg)) return flipPreventionActive;

        double previousTiltDeg = lastFlipPreventionTiltDeg;
        boolean worsening = Double.isFinite(previousTiltDeg)
            && tiltDeg > previousTiltDeg + 0.15;
        lastFlipPreventionTiltDeg = tiltDeg;

        if (flipPreventionActive) {
            if (tiltDeg <= FLIP_PREVENTION_RELEASE_DEG) {
            DebugLogging.fccInfo("[FCC@{}] flip-prevention release tiltDeg={}", getBlockPos(),
                        String.format(java.util.Locale.ROOT, "%.2f", tiltDeg));
                clearFlipPreventionState();
                return false;
            }
            emitFlipPreventionWarning(serverLevel, tiltDeg, false);
            return true;
        }

        if (tiltDeg > FLIP_PREVENTION_ENGAGE_DEG && worsening) {
            flipPreventionActive = true;
            DebugLogging.fccInfo("[FCC@{}] flip-prevention engage tiltDeg={} previousTiltDeg={}", getBlockPos(),
                    String.format(java.util.Locale.ROOT, "%.2f", tiltDeg),
                    String.format(java.util.Locale.ROOT, "%.2f", previousTiltDeg));
            emitFlipPreventionWarning(serverLevel, tiltDeg, true);
            return true;
        }
        return false;
    }

    private void clearFlipPreventionState() {
        flipPreventionActive = false;
        lastFlipPreventionTiltDeg = Double.NaN;
        lastFlipPreventionSoundTick = Long.MIN_VALUE;
        if (lastFlipPreventionWarning != null) {
            clearSafetyWarning(lastFlipPreventionWarning);
            lastFlipPreventionWarning = null;
        }
    }

    private double sampleContraptionTiltDeg() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel == null) return Double.NaN;
        org.joml.Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
        org.joml.Vector3d gravityWorld = SableBridge.gravityWorld(this);
        if (bodyToWorld == null || gravityWorld == null || gravityWorld.length() < 1.0e-6) return Double.NaN;
        org.joml.Vector3d worldUp = new org.joml.Vector3d(gravityWorld).normalize().mul(-1.0);
        org.joml.Vector3d bodyUpWorld = new org.joml.Quaterniond(bodyToWorld).transform(new org.joml.Vector3d(0.0, 1.0, 0.0));
        if (bodyUpWorld.length() < 1.0e-6) return Double.NaN;
        bodyUpWorld.normalize();
        double dot = clampDouble(bodyUpWorld.dot(worldUp), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private void emitFlipPreventionWarning(ServerLevel serverLevel, double tiltDeg, boolean forceSound) {
        long now = serverLevel.getGameTime();
        boolean soundDue = forceSound || lastFlipPreventionSoundTick == Long.MIN_VALUE
                || now - lastFlipPreventionSoundTick >= FLIP_PREVENTION_SOUND_INTERVAL_TICKS;
        if (soundDue) {
            lastFlipPreventionSoundTick = now;
            serverLevel.playSound(null, getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.85f, 1.55f);
        }
        if (!soundDue && lastFlipPreventionWarning != null) return;
        String warning = String.format(java.util.Locale.ROOT,
                "FLIP PREVENTION active (%.0f deg tilt), levitation only.", tiltDeg);
        lastFlipPreventionWarning = warning;
        recordSafetyWarning(warning);
    }

    private int forceFlipPreventionLift(Map<BlockPos, Integer> rpmCommands) {
        int forced = 0;
        int target = clampRpm((int) Math.round(FLIP_PREVENTION_LIFT_POWER * FlightConfig.RPM_CLAMP));
        for (FccGroup group : groups) {
            if (!looksLiftLabel(group.label())) continue;
            for (UUID deviceId : group.deviceIds()) {
                FccDevice device = findDevice(deviceId);
                if (device == null || !device.enabled() || device.secondaryPos() == null) continue;
                rpmCommands.merge(device.secondaryPos(), target, Math::max);
                forced++;
            }
        }
        if (forced > 0 && hasServerTickElapsed(lastWorkbenchRuntimeLogTick, 20)) {
            DebugLogging.fccInfo("[FCC@{}] flip-prevention forced lift target={} devices={}", getBlockPos(), target, forced);
        }
        return forced;
    }

    private static boolean isFlipPreventionLiftRoute(FccGroup group, FccControlRoute route) {
        return isRpmRoute(route.outputType()) && looksLiftLabel(group.label());
    }

    @Nullable private String lastWarning = null;

    private int sensorLossPersistTicks = 0;

    private static final int SENSOR_LOSS_HYSTERESIS_TICKS = 3;

    public void requestModeChange(FlightMode newMode, NoseAxis newNose,
                                  double tAlt, double cRate, double dFloor) {
        if (level == null || level.isClientSide) return;
        boolean changed = false;
        boolean modeAccepted = false;
        if (newNose != null && newNose != noseAxis) {
            noseAxis = newNose;
            changed = true;
        }

        if (newMode != null && !newMode.isEnabledInCurrentBuild()) {
            if (lastWarning == null || !lastWarning.equals("Mode disabled in this build")) {
                lastWarning = "Mode disabled in this build";
                changed = true;
            }
            newMode = null;
        }

        if (newMode != null && newMode != mode) {
            java.util.Set<SensorRequirement> avail = sensors != null
                ? sensors.validatedSet()
                : java.util.EnumSet.noneOf(SensorRequirement.class);
            if (newMode == FlightMode.OFF || newMode.isAvailable(avail)) {
                mode = newMode;
                lastWarning = null;
                changed = true;
                modeAccepted = true;
            } else {
                lastWarning = "Mode unavailable: missing sensor(s)";
                changed = true;
            }
        } else if (newMode != null && newMode == mode) {
            if (lastWarning != null) { lastWarning = null; changed = true; }
        }

        double oldT = getTargetAlt(), oldC = getClimbRate(), oldD = getDescendFloor();
        boolean paramsChanged = (oldT != tAlt) || (oldC != cRate) || (oldD != dFloor);
        if (paramsChanged) {
            modeParams.putDouble("TargetAlt",   tAlt);
            modeParams.putDouble("ClimbRate",   cRate);
            modeParams.putDouble("DescendFloor", dFloor);
            changed = true;
        }

        if (changed) {
            setChanged();
            broadcastSnapshot();
        }
    }

    public double getTargetAlt()    { return modeParams.contains("TargetAlt")   ? modeParams.getDouble("TargetAlt")   : worldPosition.getY(); }
    public double getClimbRate()    { return modeParams.contains("ClimbRate")   ? modeParams.getDouble("ClimbRate")   : 1.0; }
    public double getDescendFloor() { return modeParams.contains("DescendFloor") ? modeParams.getDouble("DescendFloor") : worldPosition.getY() - 32; }

    @Nullable private String lastWarningSeen = null;

    private long lastSafetyAlertTick = Long.MIN_VALUE;

    public void recordSafetyWarning(String msg) {
        if (msg == null || msg.isEmpty()) return;
        if (msg.equals(lastWarning)) return;
        lastWarning = msg;
        lastWarningSeen = msg;
        setChanged();
        broadcastSnapshot();
    }

    public void clearSafetyWarning(@Nullable String expectedMsg) {
        if (expectedMsg == null) return;
        if (!expectedMsg.equals(lastWarning)) return;
        lastWarning = null;
        setChanged();
        broadcastSnapshot();
    }

    public void triggerOverrideStandby(BlockPos governorPos) {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        boolean firstAlert = lastSafetyAlertTick == Long.MIN_VALUE;
        boolean throttled = !firstAlert && (now - lastSafetyAlertTick) < FlightConfig.SAFETY_ALERT_THROTTLE_TICKS;
        String reason = "External input detected at governor "
                + governorPos.getX() + "," + governorPos.getY() + "," + governorPos.getZ()
                + ", Standby mode enabled.";

        requestModeChange(FlightMode.STANDBY, getNoseAxis(),
                getTargetAlt(), getClimbRate(), getDescendFloor());
        recordSafetyWarning(reason);
        if (!throttled) {
            lastSafetyAlertTick = now;
            Player nearest = level.getNearestPlayer(
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    32.0,  false);
            if (nearest != null) {
                nearest.displayClientMessage(Component.literal("[FCC] " + reason), false);
            }
        }
    }

    @Nullable public String getLastWarning() { return lastWarning; }

    private void clearCalibrationRows() {
        for (int row = 0; row < ROW_COUNT; row++) {
            rowCalibrationValid[row] = false;
            rowCalibratedK[row] = 0.0;
            rowYawFactor[row] = 0.0;
            rowCalibratedProps[row] = null;
            rowCalibratedGovernors[row] = null;
        }
    }

    public void recalibrateBattleBaseline() {
        double mass = SableBridge.mass(this);
        if (!Double.isFinite(mass) || mass <= 1.0e-6) {
            recordSafetyWarning("Battle calibration needs a loaded Aeronautics craft mass.");
            broadcastSnapshot();
            return;
        }
        battleBaselineMass = mass;
        battleBaselineAssemblyFingerprint = assemblyFingerprint;
        setChanged();
        broadcastSnapshot();
    }

    private boolean hasBattleBaseline() {
        return Double.isFinite(battleBaselineMass) && battleBaselineMass > 1.0e-6;
    }

    private double currentBattleMass() {
        double mass = SableBridge.mass(this);
        return Double.isFinite(mass) && mass > 1.0e-6 ? mass : Double.NaN;
    }

    private boolean isBattleBaselineStale() {
        return hasBattleBaseline()
                && battleBaselineAssemblyFingerprint != 0L
                && assemblyFingerprint != 0L
                && battleBaselineAssemblyFingerprint != assemblyFingerprint;
    }

    private int battleIntegrityPercent(double currentMass) {
        if (!hasBattleBaseline() || !Double.isFinite(currentMass) || currentMass <= 0.0) return 0;
        double ratio = currentMass / battleBaselineMass;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 100.0);
    }

    public com.createstats.flight.network.FccGuiSnapshot buildSnapshot() {
        ensureAutoAssistTuningControls();
        java.util.EnumSet<SensorRequirement> avail = (sensors != null)
            ? sensors.validatedSet()
            : java.util.EnumSet.noneOf(SensorRequirement.class);
        boolean warm = sensors != null && sensors.isWarmedUp();
        int rows = bindings.size();
        int resolved = 0;
        int activeRowMask = 0;
        int resolvedRowMask = 0;
        for (int row : bindingRows) {
            if (row >= 0 && row < ROW_COUNT) activeRowMask |= 1 << row;
        }
        if (level != null) {
            for (int i = 0; i < bindings.size(); i++) {
                FccBinding b = bindings.get(i);
                int row = i < bindingRows.size() ? bindingRows.get(i) : -1;
                if (!level.isLoaded(b.prop()) || !level.isLoaded(b.governor())) continue;
                net.minecraft.world.level.block.entity.BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, b.prop());
                net.minecraft.world.level.block.entity.BlockEntity govBe  = SableBridge.getBlockEntityOnContraption(level, b.governor());
                if (propBe != null && govBe instanceof com.createstats.flight.blockentity.RpmGovernorBE) {
                    resolved++;
                    if (row >= 0 && row < ROW_COUNT) resolvedRowMask |= 1 << row;
                }
            }
        }

        BlockPos effGimbal  = sensors != null ? sensors.effectiveGimbalPos()   : gimbalPos;
        BlockPos effAlt     = sensors != null ? sensors.effectiveAltitudePos() : altitudeSensorPos;
        BlockPos effVel     = sensors != null ? sensors.effectiveVelocityPos() : velocitySensorPos;
        BlockPos effNav     = sensors != null ? sensors.effectiveNavTablePos() : navTablePos;
        int manualMask = 0;
        if (sensors != null) {
            if (sensors.isGimbalManual())   manualMask |= 1 << SensorRequirement.GIMBAL.ordinal();
            if (sensors.isAltitudeManual()) manualMask |= 1 << SensorRequirement.ALTITUDE.ordinal();
            if (sensors.isVelocityManual()) manualMask |= 1 << SensorRequirement.VELOCITY.ordinal();
            if (sensors.isNavTableManual()) manualMask |= 1 << SensorRequirement.NAV_TABLE.ordinal();
        }

        int calStateOrd = 0;
        int calPct      = 0;
        int calProp     = -1;
        String calSummary = "";
        int readinessFlags = buildReadinessFlags();
        int calGainCount = calibratedBindingCount();
        int deviceCount = devices.size();
        int groupCount = groups.size();
        int controlCount = controls.size();
        int ungroupedDeviceCount = computeUngroupedDeviceCount();
        double currentBattleMass = currentBattleMass();
        boolean battleBaselineReady = hasBattleBaseline();
        boolean battleBaselineStale = isBattleBaselineStale();
        int battleIntegrityPercent = battleIntegrityPercent(currentBattleMass);
        List<FccGuiDeviceEntry> deviceEntries = buildDeviceEntries();
        List<FccGuiGroupEntry> groupEntries = buildGroupEntries();
        List<FccGuiControlEntry> controlEntries = buildControlEntries();
        List<FccGuiRouteEntry> routeEntries = buildRouteEntries();
        return new com.createstats.flight.network.FccGuiSnapshot(
            getBlockPos(), mode, noseAxis, diagramProfile,
            getTargetAlt(), getClimbRate(), getDescendFloor(),
            avail, warm, rows, resolved, activeRowMask, resolvedRowMask, duplicateBindingRowMask,
            lastWarning == null ? "" : lastWarning,
            effGimbal, effAlt, effVel, effNav, manualMask,
            calStateOrd, calPct, calProp, calSummary, readinessFlags, calGainCount,
            deviceCount, groupCount, controlCount, ungroupedDeviceCount,
            buildOverviewLines(deviceCount, groupCount, controlCount, ungroupedDeviceCount),
            buildDeviceLines(),
            buildGroupLines(),
            deviceEntries,
            groupEntries,
            controlEntries,
            routeEntries,
            getRedstoneRowMask(),
            getRedstoneActiveRowMask(),
                getRedstonePoweredRowMask(),
                getLinkedControllerConfiguredMask(),
                getLinkedControllerInputMask(),
                getLinkedControllerSignalPack(),
                linkedControllerRampIntervalTicks,
                pathfinderComputerPos,
                getPathfinderRouteName(),
                getPathfinderRouteStatus(),
                isPathfinderRouteAccepted(),
                getPathfinderRouteConfidence(),
                getPathfinderRouteWaypointIndex(),
                getPathfinderRouteWaypointCount(),
                getPathfinderRouteDistance(),
                getPathfinderRouteHazardFlags(),
                buildPathfinderRoutePreview(),
                isPathfinderSystemsEnabled(),
                battleBaselineReady,
                battleBaselineStale,
                battleBaselineMass,
                currentBattleMass,
                battleIntegrityPercent,
                navTableMaxSpeed,
                lastAirshipAutoRequestedSpeedCap,
                airshipAutoReachableSpeedCap,
                lastAirshipAutoEffectiveSpeedCap,
                lastAirshipAutoReachabilityReason,
                pathfinderLookaheadScale,
                feConsumer.isPowered(),
                feConsumer.hudBufferPct());
    }

    private List<BlockPos> buildPathfinderRoutePreview() {
        if (this.pathfinderGuidanceWaypoints.isEmpty()) return List.of();
        int limit = Math.min(96, this.pathfinderGuidanceWaypoints.size());
        List<BlockPos> preview = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Vec3 point = this.pathfinderGuidanceWaypoints.get(i);
            preview.add(BlockPos.containing(point.x, point.y, point.z));
        }
        return preview;
    }

    private List<FccGuiDeviceEntry> buildDeviceEntries() {
        Set<UUID> groupedIds = new LinkedHashSet<>();
        Map<UUID, String> assignmentLabels = new LinkedHashMap<>();
        for (FccGroup group : groups) {
            boolean diagramGroup = isDiagramGroupLabel(group.label());
            for (UUID deviceId : group.deviceIds()) {
                groupedIds.add(deviceId);
                if (diagramGroup || !assignmentLabels.containsKey(deviceId)) assignmentLabels.put(deviceId, group.label());
            }
        }
        List<FccGuiDeviceEntry> entries = new ArrayList<>(devices.size());
        for (int i = 0; i < devices.size(); i++) {
            FccDevice device = devices.get(i);
            entries.add(new FccGuiDeviceEntry(
                    device.id(),
                    device.label(),
                    device.category().shortLabel(),
                    workbenchDeviceNumber(device, i),
                    assignmentLabels.getOrDefault(device.id(), ""),
                    isWorkbenchDeviceResolved(device),
                    groupedIds.contains(device.id())));
        }
        return entries;
    }

    private int workbenchDeviceNumber(FccDevice device, int fallbackIndex) {
        CompoundTag config = device.config();
        if (config.contains("Row")) return config.getInt("Row") + 1;
        return fallbackIndex + 1;
    }

    private boolean isDiagramGroupLabel(String label) {
        for (DiagramZoneSpec zone : DIAGRAM_ZONE_SPECS) {
            if (diagramGroupMatches(zone, label)) return true;
        }
        return false;
    }

    private List<FccGuiGroupEntry> buildGroupEntries() {
        List<FccGuiGroupEntry> entries = new ArrayList<>(groups.size());
        for (FccGroup group : groups) {
            entries.add(new FccGuiGroupEntry(group.id(), group.label(), group.deviceIds().size(), group.zoneKey()));
        }
        return entries;
    }

    private List<FccGuiControlEntry> buildControlEntries() {
        List<FccGuiControlEntry> entries = new ArrayList<>(controls.size());
        for (FccCompositeControl control : controls) {
            entries.add(new FccGuiControlEntry(
                    control.id(),
                    control.label(),
                    control.widgetType().name(),
                    control.springCentered(),
                    control.curveType().name(),
                    control.routes().size(),
                    controlValues.getOrDefault(control.id(), control.neutralValue()),
                    control.inputMin(),
                    control.inputMax(),
                    control.neutralValue()));
        }
        return entries;
    }

    private List<FccGuiRouteEntry> buildRouteEntries() {
        Map<UUID, String> groupLabels = new LinkedHashMap<>();
        for (FccGroup group : groups) groupLabels.put(group.id(), group.label());
        List<FccGuiRouteEntry> entries = new ArrayList<>();
        for (FccCompositeControl control : controls) {
            for (FccControlRoute route : control.routes()) {
                String targetLabel = groupLabels.getOrDefault(route.targetGroupId(), "Missing Group");
                entries.add(new FccGuiRouteEntry(
                        route.id(),
                        control.id(),
                        route.targetGroupId(),
                        targetLabel,
                        route.outputType().name(),
                        route.scale(),
                        route.invert()));
            }
        }
        return entries;
    }

    private int computeUngroupedDeviceCount() {
        Set<UUID> groupedIds = new LinkedHashSet<>();
        for (FccGroup group : groups) groupedIds.addAll(group.deviceIds());
        int count = 0;
        for (FccDevice device : devices) if (!groupedIds.contains(device.id())) count++;
        return count;
    }

    private List<String> buildOverviewLines(int deviceCount, int groupCount, int controlCount, int ungroupedDeviceCount) {
        List<String> lines = new ArrayList<>();
        if (deviceCount == 0) {
            lines.add("No linked devices yet.");
            lines.add("Start by linking prop and governor cards.");
        } else if (groupCount == 0) {
            if (ungroupedDeviceCount > 0) {
                lines.add(ungroupedDeviceCount + " devices still need group assignment.");
            }
            lines.add("No groups yet.");
            lines.add("Next: create groups for left, right, lift, and tail.");
        } else if (controlCount == 0) {
            if (ungroupedDeviceCount > 0) {
                lines.add(ungroupedDeviceCount + " devices are still ungrouped.");
            }
            lines.add(groupCount + " groups are ready.");
            lines.add("Next: create the first cockpit control.");
        } else {
            lines.add(groupCount + " groups are ready.");
            lines.add(controlCount + " controls are already defined.");
            lines.add("Workbench foundation is ready for control authoring.");
        }
        return limitSnapshotLines(lines);
    }

    private List<String> buildDeviceLines() {
        List<String> lines = new ArrayList<>();
        if (devices.isEmpty()) {
            lines.add("No devices yet");
            lines.add("Link a propeller card and governor card in one row");
            return lines;
        }
        Set<UUID> groupedIds = new LinkedHashSet<>();
        for (FccGroup group : groups) groupedIds.addAll(group.deviceIds());
        for (FccDevice device : devices) {
            String status = isWorkbenchDeviceResolved(device) ? "LIVE" : "PEND";
            String grouped = groupedIds.contains(device.id()) ? "grouped" : "ungrouped";
            lines.add(device.category().shortLabel() + " | " + device.label() + " | " + status + " | " + grouped);
        }
        return limitSnapshotLines(lines);
    }

    private List<String> buildGroupLines() {
        List<String> lines = new ArrayList<>();
        if (groups.isEmpty()) {
            lines.add("No groups defined");
            lines.add("Phase 1 foundation is ready for group authoring");
            return lines;
        }
        for (FccGroup group : groups) {
            lines.add(group.label() + " | " + group.deviceIds().size() + " devices");
        }
        return limitSnapshotLines(lines);
    }

    private static List<String> limitSnapshotLines(List<String> lines) {
        if (lines.size() <= SNAPSHOT_LINE_LIMIT) return lines;
        return new ArrayList<>(lines.subList(0, SNAPSHOT_LINE_LIMIT));
    }

    private boolean isWorkbenchDeviceResolved(FccDevice device) {
        if (device.category() == FccDeviceCategory.REDSTONE_LINK) {
            int row = redstoneDeviceRow(device);
            return row >= 0 && isRedstonePairComplete(row);
        }
        if (level == null) return false;
        if (!level.isLoaded(device.primaryPos())) return false;
        if (device.secondaryPos() == null || !level.isLoaded(device.secondaryPos())) return false;
        BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, device.primaryPos());
        BlockEntity govBe = SableBridge.getBlockEntityOnContraption(level, device.secondaryPos());
        if (!(govBe instanceof RpmGovernorBE)) return false;
        if (SableBridge.readProp(propBe) != null) return true;
        return PropellerCardItem.isSupportedTarget(level, device.primaryPos());
    }

    private int buildReadinessFlags() {
        int flags = 0;
        if (bindings.isEmpty()) return com.createstats.flight.network.FccGuiSnapshot.READY_NO_BINDINGS;
        if (level == null) return com.createstats.flight.network.FccGuiSnapshot.READY_BMATRIX_INVALID;
        try {
            com.createstats.flight.control.BMatrix b =
                    com.createstats.flight.control.BMatrixBuilder.build(level, this, bindings);
            if (!b.usable()) return com.createstats.flight.network.FccGuiSnapshot.READY_BMATRIX_INVALID;
            double yawAuthority = b.maxRowAuthority(com.createstats.flight.control.Matrix6xN.TY,
                    FlightConfig.RPM_CLAMP);
            boolean anyYawFactor = false;
            for (com.createstats.flight.control.BMatrix.Diagnostic d : b.perPropDiagnostics()) {
                if (d.valid() && Math.abs(d.yawFactor()) > 1.0e-6) {
                    anyYawFactor = true;
                    break;
                }
            }
            if (yawAuthority < 1.0e-6) flags |= com.createstats.flight.network.FccGuiSnapshot.READY_ZERO_YAW_AUTHORITY;
            if (!anyYawFactor) flags |= com.createstats.flight.network.FccGuiSnapshot.READY_UNKNOWN_SPIN;
        } catch (Exception ignored) {
            flags |= com.createstats.flight.network.FccGuiSnapshot.READY_BMATRIX_INVALID;
        }
        return flags;
    }

    public com.createstats.flight.network.FccTelemetryPacket buildTelemetry(int containerId) {
        double altitude;
        float climb = 0f, heading = 0f, pitch = 0f, roll = 0f;
        float velX = 0f, velY = 0f, velZ = 0f;
        float autoTargetDistance = Double.isFinite(lastVehicleAutoTargetDistance)
            ? (float) lastVehicleAutoTargetDistance
            : Double.isFinite(lastAirshipAutoTargetDistance) ? (float) lastAirshipAutoTargetDistance : Float.NaN;
        int size = bindings.size();
        int[] bindingRow = new int[size];
        int[] commanded = new int[size];
        int[] actual = new int[size];
        float[] thrustSigned = new float[size];
        float[] thrustVertical = new float[size];
        float[] thrustHorizontal = new float[size];
        float[] sailPower = new float[size];
        float[] tiltAngleDeg = new float[size];
        int gyroMask = 0;

        java.util.Arrays.fill(bindingRow, -1);

        altitude = worldPosition.getY();

        DirectTelemetrySample directTelemetry = this.sampleDirectTelemetry();
        if (directTelemetry != null) {
            altitude = directTelemetry.altitude();
            climb = directTelemetry.climbRate();
            heading = directTelemetry.headingDeg();
            pitch = directTelemetry.pitchDeg();
            roll = directTelemetry.rollDeg();
        }

        org.joml.Vector3d worldVelocity = SableBridge.linearVelocityWorld(this);
        if (worldVelocity != null) {
            velX = (float) worldVelocity.x;
            velY = (float) worldVelocity.y;
            velZ = (float) worldVelocity.z;
            climb = (float) worldVelocity.y;
        }
        double vehicleHeading = currentHeadingDeg();
        if (Double.isFinite(vehicleHeading)) {
            heading = (float) wrapDegrees(vehicleHeading);
        }

        if (level != null) {
            for (int i = 0; i < size; i++) {
                FccBinding b = bindings.get(i);
                if (i < bindingRows.size()) {
                    bindingRow[i] = bindingRows.get(i);
                }
                if (!level.isLoaded(b.prop())) {
                    continue;
                }
                BlockEntity propBe = SableBridge.getBlockEntityOnContraption(level, b.prop());
                BlockEntity govBe = level.isLoaded(b.governor())
                        ? SableBridge.getBlockEntityOnContraption(level, b.governor())
                        : null;
                if (govBe instanceof RpmGovernorBE governor) {
                    actual[i] = governor.getAppliedRpm();
                }

                SableBridge.PropData pd = propBe != null ? SableBridge.readProp(propBe) : null;
                if (pd == null) {
                    if (PropellerCardItem.isWheelMountTarget(level, b.prop())) {
                        Float steeringAngle = readWheelMountSteeringAngleDeg(propBe);
                        if (steeringAngle != null && Float.isFinite(steeringAngle)) {
                            tiltAngleDeg[i] = steeringAngle;
                        }
                    }
                    continue;
                }

                if (Double.isFinite(pd.rpm())) {
                    actual[i] = (int) Math.round(pd.rpm());
                }
                if (Double.isFinite(pd.thrustSigned())) {
                    thrustSigned[i] = (float) pd.thrustSigned();
                }
                if (Double.isFinite(pd.sailPower())) {
                    sailPower[i] = (float) pd.sailPower();
                }

                org.joml.Vector3d localDir = pd.thrustDirLocal();
                if (localDir == null || !Double.isFinite(localDir.x) || !Double.isFinite(localDir.y) || !Double.isFinite(localDir.z)) {
                    continue;
                }

                org.joml.Vector3d localNorm = new org.joml.Vector3d(localDir);
                double localLenSq = localNorm.lengthSquared();
                if (localLenSq < 1.0e-8) {
                    continue;
                }
                localNorm.mul(1.0 / Math.sqrt(localLenSq));

                double dotForward = Math.max(-1.0, Math.min(1.0, localNorm.z));
                tiltAngleDeg[i] = (float) Math.toDegrees(Math.acos(dotForward));

                Object subLevel = SableBridge.subLevelAccessOf(propBe);
                org.joml.Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
                org.joml.Vector3d worldNorm = bodyToWorld != null
                        ? new org.joml.Quaterniond(bodyToWorld).transform(new org.joml.Vector3d(localNorm))
                        : new org.joml.Vector3d(localNorm);
                double worldLenSq = worldNorm.lengthSquared();
                if (worldLenSq < 1.0e-8) {
                    continue;
                }
                worldNorm.mul(1.0 / Math.sqrt(worldLenSq));

                float thrust = thrustSigned[i];
                float vertical = (float) (thrust * worldNorm.y);
                float horizontalMag = (float) (Math.hypot(worldNorm.x, worldNorm.z) * Math.abs(thrust));
                thrustVertical[i] = vertical;
                thrustHorizontal[i] = Math.copySign(horizontalMag, thrust == 0f ? 1f : thrust);

                if (pd.isGyro() && i < Integer.SIZE) {
                    gyroMask |= (1 << i);
                }
            }
        }

        int resolved = 0;
        if (level != null) {
            for (FccBinding b : bindings) {
                if (!level.isLoaded(b.prop()) || !level.isLoaded(b.governor())) continue;
                if (SableBridge.getBlockEntityOnContraption(level, b.governor()) instanceof com.createstats.flight.blockentity.RpmGovernorBE) resolved++;
            }
        }

        return new com.createstats.flight.network.FccTelemetryPacket(
            containerId,
            getBlockPos().asLong(),
            altitude,
            climb,
            heading,
            pitch,
            roll,
            velX,
            velZ,
            velY,
            autoTargetDistance,
            size,
            resolved,
            bindingRow,
            commanded,
            actual,
            thrustSigned,
            thrustVertical,
            thrustHorizontal,
            sailPower,
            tiltAngleDeg,
            gyroMask,
            java.util.Arrays.copyOf(lastVehicleWheelRpm, lastVehicleWheelRpm.length),
            java.util.Arrays.copyOf(lastVehicleWheelSteer, lastVehicleWheelSteer.length),
            java.util.Arrays.copyOf(lastVehicleWheelActualRpm, lastVehicleWheelActualRpm.length),
            lastVehicleSteerModeOrdinal
        );
    }

    @Nullable
    private DirectTelemetrySample sampleDirectTelemetry() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel == null) {
            return null;
        }
        org.joml.Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
        org.joml.Vector3d gravityWorld = SableBridge.gravityWorld(this);
        if (bodyToWorld == null || gravityWorld == null || gravityWorld.length() < 1.0e-6) {
            return null;
        }
        org.joml.Vector3d worldUp = new org.joml.Vector3d(gravityWorld).normalize().mul(-1.0);
        org.joml.Quaterniond worldToBody = new org.joml.Quaterniond(bodyToWorld).invert();
        org.joml.Vector3d gUpBody = worldToBody.transform(new org.joml.Vector3d(worldUp));
        org.joml.Vector3d forward = switch (this.getNoseAxis()) {
            case POS_X -> new org.joml.Vector3d(1.0, 0.0, 0.0);
            case NEG_X -> new org.joml.Vector3d(-1.0, 0.0, 0.0);
            case POS_Z -> new org.joml.Vector3d(0.0, 0.0, 1.0);
            case NEG_Z -> new org.joml.Vector3d(0.0, 0.0, -1.0);
        };
        org.joml.Vector3d up = new org.joml.Vector3d(0.0, 1.0, 0.0);
        org.joml.Vector3d right = new org.joml.Vector3d(up).cross(forward).normalize();
        double upComponent = gUpBody.dot(up);
        float pitch = (float) Math.toDegrees(Math.atan2(-gUpBody.dot(forward), upComponent));
        float roll = (float) Math.toDegrees(Math.atan2(gUpBody.dot(right), upComponent));

        org.joml.Vector3d worldCom = currentContraptionWorldPosition();
        org.joml.Vector3d worldVelocity = SableBridge.linearVelocityWorld(this);
        float climbRate = worldVelocity != null ? (float) worldVelocity.y : 0.0f;

        org.joml.Vector3d noseWorld = new org.joml.Quaterniond(bodyToWorld).transform(new org.joml.Vector3d(forward));
        double horizontalDot = noseWorld.dot(worldUp);
        org.joml.Vector3d horizontalNose = new org.joml.Vector3d(noseWorld).sub(new org.joml.Vector3d(worldUp).mul(horizontalDot));
        float heading = horizontalNose.lengthSquared() < 1.0e-8
            ? 0.0f
            : (float) wrapDegrees(Math.toDegrees(Math.atan2(horizontalNose.x, horizontalNose.z)));

        return new DirectTelemetrySample(worldCom.y, climbRate, heading, pitch, roll);
    }

    private record DirectTelemetrySample(
        double altitude,
        float climbRate,
        float headingDeg,
        float pitchDeg,
        float rollDeg
    ) {}

    @Nullable
    private static Float readWheelMountSteeringAngleDeg(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        String className = blockEntity.getClass().getName();
        if (!className.contains("dev.ryanhcode.offroad.content.blocks.wheel_mount")) return null;
        try {
            java.lang.reflect.Method method = blockEntity.getClass().getMethod("getLerpedAngle", float.class);
            Object value = method.invoke(blockEntity, 1.0f);
            if (value instanceof Number number) {
                return (float) number.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public void broadcastSnapshot() {
        if (!(level instanceof ServerLevel)) return;
        syncClientVisualState();
        List<ServerPlayerWithMenu> viewers = openMenuPlayers();
        List<net.minecraft.server.level.ServerPlayer> subscribers = new ArrayList<>();
        for (Player player : level.players()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && FccHudSubscriptionState.isSubscribed(serverPlayer.getUUID(), getBlockPos())) {
                subscribers.add(serverPlayer);
            }
        }
        if (viewers.isEmpty() && subscribers.isEmpty()) return;
        com.createstats.flight.network.FccGuiSnapshot snap = buildSnapshot();
        for (ServerPlayerWithMenu w : viewers) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                w.player, new com.createstats.flight.network.FccGuiSnapshotPacket(w.containerId, snap));
        }
        for (net.minecraft.server.level.ServerPlayer serverPlayer : subscribers) {
            boolean alreadyCovered = false;
            for (ServerPlayerWithMenu w : viewers) {
                if (w.player == serverPlayer) {
                    alreadyCovered = true;
                    break;
                }
            }
            if (!alreadyCovered) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer, new com.createstats.flight.network.FccGuiSnapshotPacket(0, snap));
            }
        }
    }

    private void syncClientVisualState() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private void broadcastTelemetry() {
        if (!(level instanceof ServerLevel)) return;
        List<ServerPlayerWithMenu> viewers = openMenuPlayers();
        List<net.minecraft.server.level.ServerPlayer> subscribers = new ArrayList<>();
        for (Player player : level.players()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && FccHudSubscriptionState.isSubscribed(sp.getUUID(), getBlockPos())) {
                subscribers.add(sp);
            }
        }
        if (viewers.isEmpty() && subscribers.isEmpty()) return;
        for (ServerPlayerWithMenu w : viewers) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                w.player, buildTelemetry(w.containerId));
        }
        for (net.minecraft.server.level.ServerPlayer sp : subscribers) {
            boolean alreadyCovered = false;
            for (ServerPlayerWithMenu w : viewers) {
                if (w.player == sp) {
                    alreadyCovered = true;
                    break;
                }
            }
            if (!alreadyCovered) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    sp, buildTelemetry(0));
            }
        }
    }

    public void applyOverrideCommands(FccOverrideCommandPacket packet) {
        int[] rows = packet.bindingRowIndices();
        int[] rpms = packet.overrideRpmCommands();
        boolean[] active = packet.overrideActive();
        if (rows.length != rpms.length || rpms.length != active.length) {
            return;
        }

        while (emanuelOverrideActive.size() < rows.length) {
            emanuelOverrideActive.add(false);
            emanuelOverrideRpm.add(0);
        }
        while (emanuelOverrideActive.size() > rows.length) {
            emanuelOverrideActive.remove(emanuelOverrideActive.size() - 1);
            emanuelOverrideRpm.remove(emanuelOverrideRpm.size() - 1);
        }

        for (int i = 0; i < rows.length; i++) {
            int bindingIndex = rows[i];
            if (bindingIndex >= 0 && bindingIndex < emanuelOverrideActive.size()) {
                emanuelOverrideActive.set(bindingIndex, active[i]);
                emanuelOverrideRpm.set(bindingIndex, clampRpm(rpms[i]));
            }
        }
    }

    public int getEffectiveRpmCommand(int bindingIndex) {
        if (bindingIndex >= 0 && bindingIndex < emanuelOverrideActive.size() && emanuelOverrideActive.get(bindingIndex)) {
            return emanuelOverrideRpm.get(bindingIndex);
        }
        return -1;
    }

    private record ServerPlayerWithMenu(net.minecraft.server.level.ServerPlayer player, int containerId) {}

    private List<ServerPlayerWithMenu> openMenuPlayers() {
        if (!(level instanceof ServerLevel sl)) return Collections.emptyList();
        List<ServerPlayerWithMenu> out = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
            if (sp.containerMenu instanceof com.createstats.flight.menu.FlightControlComputerMenu m
                && m.getController() == this) {
                out.add(new ServerPlayerWithMenu(sp, m.containerId));
            }
        }
        return out;
    }

    public void releaseAllOwnedGovernors() {
        if (level == null || level.isClientSide || fccUuid == null) return;
        for (int row = 0; row < ROW_COUNT; row++) {
            BlockPos govPos = lastBoundGovernors[row];
            if (govPos == null) continue;
            releaseGovernor(govPos);
            lastBoundGovernors[row] = null;
        }
        if (sensors != null) sensors.reset();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
            Component.literal("Flight Control Computer").withStyle(ChatFormatting.GRAY)));

        if (feConsumer.requiresPower()) {
            final int fePct = Math.round(feConsumer.fillPct());
            final ChatFormatting feColor = !feConsumer.isPowered() ? ChatFormatting.RED
                : feConsumer.isBrownout() ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            final String feLabel = feConsumer.isPowered()
                ? String.format(java.util.Locale.ROOT, "Power: %d %% (%,d / %,d FE)",
                    fePct, feConsumer.getEnergyStored(), feConsumer.getMaxEnergyStored())
                : "Power: OFFLINE (buffer empty)";
            tooltip.add(Component.literal("    ").append(Component.literal(feLabel).withStyle(feColor)));
        }
        // Route status
        String status = pathfinderRouteStatus;
        if (status == null || status.isBlank()) status = "No Pathfinder linked";
        tooltip.add(Component.literal("    ").append(
            Component.literal(status).withStyle(ChatFormatting.WHITE)));

        int wpCount = pathfinderRouteWaypointCount;
        int wpIndex = pathfinderRouteWaypointIndex;
        if (wpCount > 0) {
            tooltip.add(Component.literal("    ").append(
                Component.literal(String.format(java.util.Locale.ROOT, "Waypoint: %d / %d", wpIndex + 1, wpCount))
                    .withStyle(ChatFormatting.AQUA)));
        }

        if (pathfinderRouteDistance > 0.0) {
            tooltip.add(Component.literal("    ").append(
                Component.literal(String.format(java.util.Locale.ROOT, "Distance: %.0f m", pathfinderRouteDistance))
                    .withStyle(ChatFormatting.YELLOW)));
        }
        // Route confidence
        if (pathfinderRouteConfidence > 0f) {
            int pct = Math.round(pathfinderRouteConfidence * 100f);
            tooltip.add(Component.literal("    ").append(
                Component.literal(String.format(java.util.Locale.ROOT, "Confidence: %d %%", pct))
                    .withStyle(pct >= 75 ? ChatFormatting.GREEN : pct >= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED)));
        }
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_stats.flight_control_computer");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        com.createstats.flight.menu.FlightControlComputerMenu m =
            new com.createstats.flight.menu.FlightControlComputerMenu(containerId, playerInventory, this);

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                new com.createstats.flight.network.FccGuiSnapshotPacket(containerId, buildSnapshot()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                buildTelemetry(containerId));
        }
        return m;
    }

    public boolean isPlayerInInteractionRange(Player player) {
        return com.createstats.flight.bridge.SableBridge.isPlayerInInteractionRangeFor(this, player);
    }

    private void loadWorkbenchControlValues(CompoundTag tag) {
        controlValues.clear();
        Map<UUID, Double> savedValues = new LinkedHashMap<>();
        if (tag.contains("WorkbenchControlValues", Tag.TAG_LIST)) {
            ListTag valueTags = tag.getList("WorkbenchControlValues", Tag.TAG_COMPOUND);
            for (int i = 0; i < valueTags.size(); i++) {
                CompoundTag valueTag = valueTags.getCompound(i);
                if (!valueTag.hasUUID("Id") || !valueTag.contains("Value")) continue;
                double value = valueTag.getDouble("Value");
                if (Double.isFinite(value)) savedValues.put(valueTag.getUUID("Id"), value);
            }
        }
        for (FccCompositeControl control : controls) {
            double value = savedValues.getOrDefault(control.id(), control.neutralValue());
            if (shouldResetWorkbenchControlValueOnLoad(control)) value = control.neutralValue();
            controlValues.put(control.id(), clampDouble(value, control.inputMin(), control.inputMax()));
        }
        restoreLinkedControllerLevelsFromControls();
    }

    private boolean shouldResetWorkbenchControlValueOnLoad(FccCompositeControl control) {
        if (control.widgetType() == FccWidgetType.BUTTON) return true;
        if (control.role() == FccControlRole.LIFT) return false;
        return control.springCentered();
    }

    private void restoreLinkedControllerLevelsFromControls() {
        linkedControllerYawLevel = 0;
        linkedControllerThrustLevel = 0;
        linkedControllerLevitationLevel = linkedControllerLevelFromControlValue("Levitation Dial");
    }

    private int linkedControllerLevelFromControlValue(String controlLabel) {
        FccCompositeControl control = findControlByLabel(controlLabel);
        if (control == null) return 0;
        double signed = normalizeControlSigned(control);
        return Math.max(-15, Math.min(15, (int) Math.round(signed * 15.0)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FccDataVersion", CURRENT_NBT_VERSION);
        tag.putInt("DiagramMetadataVersion", DIAGRAM_METADATA_VERSION);
        tag.putString("DiagramProfile", diagramProfile.name());
        if (fccUuid != null) tag.putUUID("FccUuid", fccUuid);

        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("RedstonePairRows", getRedstoneRowMask());
        tag.put("LinkedControllerInventory", linkedControllerInventory.serializeNBT(registries));
        tag.putInt("LinkedControllerRampInterval", linkedControllerRampIntervalTicks);

        if (gimbalPos != null)         tag.putLong("Gimbal", gimbalPos.asLong());
        if (altitudeSensorPos != null) tag.putLong("AltSensor", altitudeSensorPos.asLong());
        if (velocitySensorPos != null) tag.putLong("VelSensor", velocitySensorPos.asLong());
        if (navTablePos != null)       tag.putLong("NavTable", navTablePos.asLong());
        tag.putFloat("NavTableMaxSpeed", navTableMaxSpeed);
        tag.putFloat("PathfinderLookaheadScale", pathfinderLookaheadScale);
        tag.putBoolean("PathfinderSystemsEnabled", pathfinderSystemsEnabled);
        if (pathfinderComputerPos != null) tag.putLong("PathfinderComputer", pathfinderComputerPos.asLong());
        if (pathfinderComputerId != null) tag.putUUID("PathfinderComputerId", pathfinderComputerId);
        if (pathfinderRouteId != null) tag.putUUID("PathfinderRouteId", pathfinderRouteId);
        tag.putString("PathfinderRouteName", pathfinderRouteName == null ? "" : pathfinderRouteName);
        tag.putString("PathfinderRouteStatus", pathfinderRouteStatus == null ? "" : pathfinderRouteStatus);
        tag.putBoolean("PathfinderRouteAccepted", pathfinderRouteAccepted);
        tag.putFloat("PathfinderRouteConfidence", pathfinderRouteConfidence);
        tag.putInt("PathfinderRouteWaypoints", pathfinderRouteWaypointCount);
        tag.putInt("PathfinderRouteWaypointIndex", pathfinderRouteWaypointIndex);
        tag.putDouble("PathfinderRouteDistance", pathfinderRouteDistance);
        tag.putInt("PathfinderRouteHazards", pathfinderRouteHazardFlags);
        tag.putLong("PathfinderRouteLastSeen", pathfinderRouteLastSeenTick);
        if (!pathfinderGuidanceWaypoints.isEmpty()) {
            tag.put("PathfinderGuidanceWaypoints", PathfinderNbt.writeVec3List(pathfinderGuidanceWaypoints,
                PathfinderNbt.MAX_ROUTE_POINTS));
        }
        if (!pathfinderGuidanceSpeeds.isEmpty()) {
            tag.put("PathfinderGuidanceSpeeds", PathfinderNbt.writeFloatList(pathfinderGuidanceSpeeds,
                PathfinderNbt.MAX_ROUTE_POINTS));
        }

        if (!burnerLinks.isEmpty()) {
            ListTag burners = new ListTag();
            burners.addAll(burnerLinks);
            tag.put("BurnerLinks", burners);
        }

        tag.putString("NoseAxis", noseAxis.name());
        tag.putString("Mode", mode.name());
        if (!modeParams.isEmpty()) tag.put("ModeParams", modeParams.copy());
        tag.putLong("AssemblyFingerprint", assemblyFingerprint);

        if (!devices.isEmpty()) {
            ListTag deviceTags = new ListTag();
            for (FccDevice device : devices) deviceTags.add(device.toNbt());
            tag.put("WorkbenchDevices", deviceTags);
        }
        if (!groups.isEmpty()) {
            ListTag groupTags = new ListTag();
            for (FccGroup group : groups) groupTags.add(group.toNbt());
            tag.put("WorkbenchGroups", groupTags);
        }
        if (!controls.isEmpty()) {
            ListTag controlTags = new ListTag();
            for (FccCompositeControl control : controls) controlTags.add(control.toNbt());
            tag.put("WorkbenchControls", controlTags);
            ListTag valueTags = new ListTag();
            for (FccCompositeControl control : controls) {
                if (!controlValues.containsKey(control.id())) continue;
                CompoundTag valueTag = new CompoundTag();
                valueTag.putUUID("Id", control.id());
                valueTag.putDouble("Value", controlValues.get(control.id()));
                valueTags.add(valueTag);
            }
            if (!valueTags.isEmpty()) tag.put("WorkbenchControlValues", valueTags);
        }

        ListTag calRows = new ListTag();
        for (int row = 0; row < ROW_COUNT; row++) {
            if (!rowCalibrationValid[row] && rowYawFactor[row] == 0.0) continue;
            if (rowCalibratedProps[row] == null || rowCalibratedGovernors[row] == null) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("Row", row);
            c.putLong("Prop", rowCalibratedProps[row].asLong());
            c.putLong("Governor", rowCalibratedGovernors[row].asLong());
            if (rowCalibrationValid[row]) c.putDouble("CommandK", rowCalibratedK[row]);
            if (rowYawFactor[row] != 0.0) c.putDouble("YawFactor", rowYawFactor[row]);
            calRows.add(c);
        }
        if (!calRows.isEmpty()) tag.put("Calibration", calRows);
        if (hasBattleBaseline()) tag.putDouble("BattleBaselineMass", battleBaselineMass);
        if (battleBaselineAssemblyFingerprint != 0L) tag.putLong("BattleBaselineAssemblyFingerprint", battleBaselineAssemblyFingerprint);
        feConsumer.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        int version = tag.contains("FccDataVersion") ? tag.getInt("FccDataVersion") : 1;
        diagramProfile = FccDiagramProfile.safeValueOf(tag.getString("DiagramProfile"), FccDiagramProfile.BALLOON_ZEPPELIN);

        fccUuid = tag.hasUUID("FccUuid") ? tag.getUUID("FccUuid") : null;
        feConsumer.load(tag);

        int redstoneMask = tag.getInt("RedstonePairRows");
        for (int row = 0; row < ROW_COUNT; row++) {
            redstonePairRows[row] = (redstoneMask & (1 << row)) != 0;
        }

        devices.clear();
        groups.clear();
        controls.clear();
        removeAllRedstoneTransmitters();
        removeAllLinkedControllerReceivers();

        if (version < CURRENT_NBT_VERSION && tag.contains("Bindings", Tag.TAG_LIST)) {
            DebugLogging.fccInfo("[FCC@{}] migrating from v{} to v{} - legacy bindings dropped, please re-bind via cards",
                getBlockPos(), version, CURRENT_NBT_VERSION);
        }

        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            CompoundTag inventoryTag = tag.getCompound("Inventory").copy();
            inventoryTag.putInt("Size", SLOT_COUNT);
            inventory.deserializeNBT(registries, inventoryTag);
        } else {
            for (int s = 0; s < inventory.getSlots(); s++) inventory.setStackInSlot(s, ItemStack.EMPTY);
        }
        if (tag.contains("LinkedControllerInventory", Tag.TAG_COMPOUND)) {
            CompoundTag linkedInventoryTag = tag.getCompound("LinkedControllerInventory").copy();
            linkedInventoryTag.putInt("Size", LINKED_CONTROLLER_SLOT_COUNT);
            linkedControllerInventory.deserializeNBT(registries, linkedInventoryTag);
        } else {
            for (int s = 0; s < linkedControllerInventory.getSlots(); s++) linkedControllerInventory.setStackInSlot(s, ItemStack.EMPTY);
        }
        linkedControllerRampIntervalTicks = Math.max(LINKED_CONTROLLER_MIN_INTERVAL_TICKS,
                Math.min(LINKED_CONTROLLER_MAX_INTERVAL_TICKS, tag.contains("LinkedControllerRampInterval")
                        ? tag.getInt("LinkedControllerRampInterval") : 2));
        pathfinderSystemsEnabled = !tag.contains("PathfinderSystemsEnabled") || tag.getBoolean("PathfinderSystemsEnabled");

        if (tag.contains("WorkbenchDevices", Tag.TAG_LIST)) {
            ListTag deviceTags = tag.getList("WorkbenchDevices", Tag.TAG_COMPOUND);
            for (int i = 0; i < deviceTags.size(); i++) {
                FccDevice device = FccDevice.fromNbt(deviceTags.getCompound(i));
                if (device != null) devices.add(device);
            }
        }
        if (tag.contains("WorkbenchGroups", Tag.TAG_LIST)) {
            ListTag groupTags = tag.getList("WorkbenchGroups", Tag.TAG_COMPOUND);
            for (int i = 0; i < groupTags.size(); i++) {
                FccGroup group = FccGroup.fromNbt(groupTags.getCompound(i));
                if (group != null) groups.add(group);
            }
        }
        if (tag.contains("WorkbenchControls", Tag.TAG_LIST)) {
            ListTag controlTags = tag.getList("WorkbenchControls", Tag.TAG_COMPOUND);
            for (int i = 0; i < controlTags.size(); i++) {
                FccCompositeControl control = FccCompositeControl.fromNbt(controlTags.getCompound(i));
                if (control != null) controls.add(control);
            }
        }
        loadWorkbenchControlValues(tag);
        int metadataVersion = tag.contains("DiagramMetadataVersion") ? tag.getInt("DiagramMetadataVersion") : 0;
        if (metadataVersion < DIAGRAM_METADATA_VERSION) {
            if (migrateLegacyDiagramMetadata()) {
                DebugLogging.fccInfo("[FCC@{}] migrated legacy diagram metadata to v{}", getBlockPos(), DIAGRAM_METADATA_VERSION);
            }
            workbenchReconcileDirty = true;
            setChanged();
        }
        if (version < 12 && migrateVehicleWheelRouteInversionDefaults()) {
            DebugLogging.fccInfo("[FCC@{}] migrated Otomobil wheel RPM routes to inverted defaults", getBlockPos());
            workbenchReconcileDirty = true;
            setChanged();
        }

        gimbalPos         = tag.contains("Gimbal")    ? BlockPos.of(tag.getLong("Gimbal"))    : null;
        altitudeSensorPos = tag.contains("AltSensor") ? BlockPos.of(tag.getLong("AltSensor")) : null;
        velocitySensorPos = tag.contains("VelSensor") ? BlockPos.of(tag.getLong("VelSensor")) : null;
        navTablePos       = tag.contains("NavTable")  ? BlockPos.of(tag.getLong("NavTable"))  : null;
        navTableMaxSpeed  = tag.contains("NavTableMaxSpeed") ? Math.max(1.0f, Math.min(32.0f, tag.getFloat("NavTableMaxSpeed"))) : 8.0f;
        pathfinderLookaheadScale = tag.contains("PathfinderLookaheadScale") ? Math.max(0.5f, Math.min(3.0f, tag.getFloat("PathfinderLookaheadScale"))) : 1.0f;
        pathfinderComputerPos = tag.contains("PathfinderComputer") ? BlockPos.of(tag.getLong("PathfinderComputer")) : null;
        pathfinderComputerId = tag.hasUUID("PathfinderComputerId") ? tag.getUUID("PathfinderComputerId") : null;
        pathfinderRouteId = tag.hasUUID("PathfinderRouteId") ? tag.getUUID("PathfinderRouteId") : null;
        pathfinderRouteName = tag.contains("PathfinderRouteName", Tag.TAG_STRING) ? tag.getString("PathfinderRouteName") : "";
        pathfinderRouteStatus = tag.contains("PathfinderRouteStatus", Tag.TAG_STRING) ? tag.getString("PathfinderRouteStatus") : "No Pathfinder linked";
        pathfinderRouteAccepted = tag.getBoolean("PathfinderRouteAccepted");
        pathfinderRouteConfidence = tag.getFloat("PathfinderRouteConfidence");
        pathfinderRouteWaypointCount = tag.getInt("PathfinderRouteWaypoints");
        pathfinderRouteWaypointIndex = Math.max(0, tag.getInt("PathfinderRouteWaypointIndex"));
        pathfinderRouteDistance = tag.getDouble("PathfinderRouteDistance");
        pathfinderRouteHazardFlags = tag.getInt("PathfinderRouteHazards");
        pathfinderRouteLastSeenTick = tag.getLong("PathfinderRouteLastSeen");
        pathfinderGuidanceWaypoints.clear();
        pathfinderGuidanceWaypoints.addAll(PathfinderNbt.readVec3List(tag, "PathfinderGuidanceWaypoints",
            PathfinderNbt.MAX_ROUTE_POINTS));
        pathfinderGuidanceSpeeds.clear();
        pathfinderGuidanceSpeeds.addAll(PathfinderNbt.readFloatList(tag, "PathfinderGuidanceSpeeds",
            PathfinderNbt.MAX_ROUTE_POINTS));
        if (pathfinderGuidanceWaypoints.isEmpty()) {
            pathfinderRouteWaypointIndex = 0;
        } else {
            pathfinderRouteWaypointIndex = Math.max(0, Math.min(pathfinderRouteWaypointIndex,
                pathfinderGuidanceWaypoints.size() - 1));
        }

        burnerLinks.clear();
        clearCalibrationRows();
        battleBaselineMass = tag.contains("BattleBaselineMass") ? tag.getDouble("BattleBaselineMass") : Double.NaN;
        battleBaselineAssemblyFingerprint = tag.contains("BattleBaselineAssemblyFingerprint")
            ? tag.getLong("BattleBaselineAssemblyFingerprint")
            : 0L;
        if (tag.contains("BurnerLinks", Tag.TAG_LIST)) {
            ListTag burners = tag.getList("BurnerLinks", Tag.TAG_COMPOUND);
            int max = FlightConfig.MAX_BURNER_LINKS;
            for (int i = 0; i < burners.size() && burnerLinks.size() < max; i++) {
                burnerLinks.add(burners.getCompound(i).copy());
            }
        }
        if (version < 3) {
            // v3 flipped the thrust sign, old calibration would dive the craft. drop it and recalibrate

            if (tag.contains("Calibration", Tag.TAG_LIST)) {
                CreateStats.LOGGER.warn("[FCC@{}] dropping stale CommandK from save v{} (<3): sign convention changed, recalibration required",
                        getBlockPos(), version);
            }
        } else if (tag.contains("Calibration", Tag.TAG_LIST)) {
            ListTag calRows = tag.getList("Calibration", Tag.TAG_COMPOUND);
            for (int i = 0; i < calRows.size(); i++) {
                CompoundTag c = calRows.getCompound(i);
                int row = c.getInt("Row");
                if (row < 0 || row >= ROW_COUNT) continue;
                if (!c.contains("Prop") || !c.contains("Governor")) continue;
                rowCalibratedProps[row] = BlockPos.of(c.getLong("Prop"));
                rowCalibratedGovernors[row] = BlockPos.of(c.getLong("Governor"));
                if (c.contains("CommandK")) {
                    double k = c.getDouble("CommandK");
                    if (Double.isFinite(k) && Math.abs(k) > 1.0e-6) {
                        rowCalibrationValid[row] = true;
                        rowCalibratedK[row] = k;
                    }
                }
                if (c.contains("YawFactor")) {
                    double yaw = c.getDouble("YawFactor");
                    rowYawFactor[row] = Double.isFinite(yaw) ? Math.max(-1.0, Math.min(1.0, yaw)) : 0.0;
                }
            }
        }

        noseAxis = NoseAxis.safeValueOf(tag.getString("NoseAxis"), NoseAxis.POS_Z);
        mode     = FlightMode.sanitizeForCurrentBuild(FlightMode.safeValueOf(tag.getString("Mode"), FlightMode.OFF));
        modeParams = tag.contains("ModeParams", Tag.TAG_COMPOUND)
            ? tag.getCompound("ModeParams").copy() : new CompoundTag();
        assemblyFingerprint = tag.getLong("AssemblyFingerprint");

        derivedDirty = true;
        workbenchReconcileDirty = true;
    }

    @Override
    public void setRemoved() {
        removeAllRedstoneTransmitters();
        removeAllLinkedControllerReceivers();
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null && tag.contains("Mode")) {
            mode = FlightMode.sanitizeForCurrentBuild(FlightMode.safeValueOf(tag.getString("Mode"), FlightMode.OFF));
        }
    }

    private final class FccRedstoneTransmitter implements IRedstoneLinkable {
        private final int row;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private int transmittedStrength;
        private boolean alive = true;

        private FccRedstoneTransmitter(int row, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.row = row;
            this.key = key;
        }

        private int row() {
            return row;
        }

        private boolean matches(Couple<RedstoneLinkNetworkHandler.Frequency> otherKey) {
            return key.equals(otherKey);
        }

        private boolean setTransmittedStrength(int strength) {
            int clamped = clampSignal(strength);
            if (transmittedStrength == clamped) return false;
            transmittedStrength = clamped;
            return true;
        }

        private void markDead() {
            alive = false;
        }

        @Override
        public int getTransmittedStrength() {
            return transmittedStrength;
        }

        @Override
        public void setReceivedStrength(int strength) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return alive && !FlightControlComputerBE.this.isRemoved() && level != null && !level.isClientSide
                    && isRedstonePairComplete(row);
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

    private final class FccLinkedControllerReceiver implements IRedstoneLinkable {
        private final int button;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private boolean alive = true;

        private FccLinkedControllerReceiver(int button, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.button = button;
            this.key = key;
        }

        private boolean matches(Couple<RedstoneLinkNetworkHandler.Frequency> otherKey) {
            return key.equals(otherKey);
        }

        private void markDead() {
            alive = false;
        }

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int strength) {
            if (button < 0 || button >= linkedControllerReceivedStrengths.length) return;
            int clamped = clampSignal(strength);
            if (linkedControllerReceivedStrengths[button] == clamped) return;
            linkedControllerReceivedStrengths[button] = clamped;
            linkedControllerSignalDirty = true;
        }

        @Override
        public boolean isListening() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return alive && !FlightControlComputerBE.this.isRemoved() && level != null && !level.isClientSide
                    && isLinkedControllerButtonConfigured(button);
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
}
