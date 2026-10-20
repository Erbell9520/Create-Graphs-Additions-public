package com.createstats.vccmk4.air;

import com.createstats.CreateStats;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.aerial.AerialCommand;
import com.createstats.aerial.AerialRecorder;
import com.createstats.aerial.AerialSilhouette;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialTelemetrySource;
import com.createstats.aerial.Quat;
import com.createstats.aviatorhelmet.AerialHudPacket;
import com.createstats.aviatorhelmet.AviatorHelmets;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.HelmConsole;
import com.createstats.vccmk4.SeatReach;
import com.createstats.vccmk4.Vcc4Reflect;
import com.createstats.vccmk4.Vcc4Scanner;
import com.createstats.vccmk4.air.core.AerialCockpitMotion;
import com.createstats.vccmk4.air.core.AerialCockpitState;
import com.createstats.vccmk4.air.core.AerialCost;
import com.createstats.vccmk4.air.core.AerialParts;
import com.createstats.vccmk4.air.core.AerialPilot;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.AltitudeLaw;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.FlightIntent;
import com.createstats.vccmk4.air.core.HeadingLaw;
import com.createstats.vccmk4.air.core.HullShape;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.NavDestination;
import com.createstats.vccmk4.air.core.NoseRule;
import com.createstats.vccmk4.air.core.PressureModel;
import com.createstats.vccmk4.air.core.PropLearner;
import com.createstats.vccmk4.air.core.PropModel;
import com.createstats.vccmk4.air.core.ShapeWalk;
import com.createstats.vccmk4.air.core.Telegraph;
import com.createstats.vccmk4.air.core.TerrainGuard;
import com.createstats.vccmk4.air.core.ThrustSplit;
import com.createstats.vccmk4.air.core.VerticalObserver;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SeatRule;
import com.createstats.vccmk4.network.AerialActionPacket;
import com.createstats.vccmk4.network.AerialView;
import com.createstats.vccmk4.network.AerialViewPacket;
import com.createstats.vccmk4.network.Vcc4HelmPacket;
import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.V3;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AerialBE extends BlockEntity implements IHaveGoggleInformation, AerialTelemetrySource, HelmConsole,
        com.createstats.util.CopyableSettings {
    static final int SCAN_INTERVAL_FLYING = 60;
    static final int SCAN_INTERVAL_IDLE = 100;

    static final int INPUT_TIMEOUT_TICKS = 40;
    static final int STATUS_INTERVAL = 5;

    static final int WELCOME_TICKS = 60;
    static final int PUBLISH_INTERVAL = 20;

    static final int VIEW_INTERVAL = 5;

    static final int WATCH_TIMEOUT_TICKS = 120;

    static final int NBT_VERSION = 3;

    static final TagKey<Block> AIRTIGHT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("aeronautics", "airtight"));

    static final int LOG_INTERVAL = 20;

    static final int TERRAIN_INTERVAL = 10;

    static final int SNAPSHOT_INTERVAL = AerialRecorder.SAMPLE_TICKS;

    static final int PATCH_INTERVAL = 40;
    static final int PATCH_SIZE = 24;
    static final double PATCH_CELL = 8.0;

    static final int HUD_INTERVAL = 5;

    static final int TOOLTIP_WIDTH = 64;

    static final int TRAFFIC_INTERVAL = 20;

    static final int DEMAND_TICKS = 100;

    static final String PORTABLE_ENGINE = "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity";

    private static final Map<UUID, WeakReference<AerialBE>> FLYING = new ConcurrentHashMap<>();

    private UUID controllerId = UUID.randomUUID();
    private final AerialSetup setup = new AerialSetup();
    private final AerialPilot pilot = new AerialPilot(setup);
    private AerialRecorder recorder = new AerialRecorder();
    private final AerialLift lift = new AerialLift();
    private final AerialProps props = new AerialProps();
    private final AerialJets jets = new AerialJets();
    private final AerialTools tools = new AerialTools();
    private final AerialLinks links = new AerialLinks();
    private Vcc4Scanner.Scan scan = Vcc4Scanner.Scan.EMPTY;
    private int scanCooldown;
    @Nullable
    private BlockPos navTable;

    private NavDestination nav = NavDestination.NONE;

    private int staff = AerialReflect.STAFF_NONE;
    private final List<BlockPos> otherAerials = new ArrayList<>();

    @Nullable
    private BlockPos bridge;

    private final Set<Long> guns = new HashSet<>();
    private Map<Long, AerialSetup.Light> lampProposals = Map.of();

    @Nullable
    private UUID pilotId;
    private HeldKeys held = HeldKeys.NONE;
    private long lastInputTick;

    @Nullable
    private UUID coPilotId;
    private HeldKeys coHeld = HeldKeys.NONE;
    private long coLastInputTick;
    private int[] helmWatch = new int[0];
    private final double[] controls = new double[Control.values().length];
    private final Map<UUID, Long> watchers = new HashMap<>();
    private int lastRings = Integer.MIN_VALUE;

    private long statusFrom;

    private boolean unloading;

    @Nullable
    private AerialSnapshot snapshot;
    @Nullable
    private AerialPilot.Command command;
    @Nullable
    private Reading reading;
    private AerialPilot.Thrust thrust = AerialPilot.Thrust.NONE;
    private AerialProps.Applied applied = AerialProps.Applied.NONE;
    private final List<String> problems = new ArrayList<>();
    private AerialSnapshot.Silhouette silhouette = AerialSnapshot.Silhouette.NONE;

    private byte[] envSide = new byte[0];
    private byte[] envTop = new byte[0];

    @Nullable
    private int[] shapeBox;
    @Nullable
    private Vector3d shapeCom;

    @Nullable
    private ShapeWalk shapeWalk;
    @Nullable
    private HullShape shapeHull;

    @Nullable
    private double[][] keelPoints;
    private AerialSnapshot.Route route = AerialSnapshot.Route.NONE;
    private int blocks = -1;

    private int airtight = -1;

    private int proposedNose;
    private long lastLog = Long.MIN_VALUE;
    private double size = 8.0;

    private final AerialCost cost = new AerialCost();
    private long lastSnapshot = Long.MIN_VALUE;

    private AerialSnapshot.Terrain patch = AerialSnapshot.Terrain.NONE;
    private long lastPatch = Long.MIN_VALUE;

    private long lastDemand = Long.MIN_VALUE;

    private List<AerialSnapshot.Traffic> traffic = List.of();
    private long lastTraffic = Long.MIN_VALUE;

    private final List<BlockPos> portableEngines = new ArrayList<>();

    @Nullable
    private AerialPilot.Mode lastMode;
    private long modeSince = AerialSnapshot.NO_TICK;

    private long forcesNanos;

    private List<String> syncLines = List.of();
    private String syncPilot = "";
    private long lastPublish = Long.MIN_VALUE;

    static final String COCKPIT = "Cockpit";

    private AerialCockpitState cockpit = AerialCockpitState.IDLE;

    @Nullable
    private AerialCockpitState cockpitSent;

    private boolean cockpitWhole = true;

    private boolean cockpitOnly;

    private final AerialCockpitMotion.Pose cockpitPose = new AerialCockpitMotion.Pose();

    @Nullable
    private Object cockpitCraft;
    private int cockpitCraftAge;

    public AerialBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        Object craft = SableBridge.subLevelAccessOf(this);
        long t0 = System.nanoTime();
        if (--scanCooldown <= 0) {
            rescan(server, craft, false);
            scanCooldown = pilot.mode().flying() ? SCAN_INTERVAL_FLYING : SCAN_INTERVAL_IDLE;
        }
        walkShape(server);
        long t1 = System.nanoTime();
        cost.add(AerialCost.Phase.SCAN, t1 - t0);
        problems.clear();
        updatePilot(server, now, craft);
        readControls(now);
        Reading r = craft == null ? null : read(server, craft);
        long t2 = System.nanoTime();
        cost.add(AerialCost.Phase.READ, t2 - t1);

        String standDown = null;
        if (craft == null) {
            standDown = "Not on a craft. Build it into a physics craft (Physics Assembler) with the balloons and props it should fly.";
        } else if (r == null) {
            standDown = "Sable does not answer for this craft yet: its pose or mass cannot be read.";
        } else if (!scan.otherConsoles().isEmpty()) {
            standDown = "A ground VCC Mk IV is on this craft: two consoles would fight over the same props. Break one.";
        } else if (yields()) {
            standDown = "Another VCC Mk IV Aerial on this craft flies it. Break one of them.";
        }
        if (standDown != null) {
            problems.add(standDown);
            handsOff(server);
            tools.release(server, controllerId);
            links.darken(server);
            command = null;
        } else {
            pilot.rudders(tools.rudders(scan));
            if (airtight >= 0) pilot.dragPrior(VerticalObserver.physicsDrag(airtight, r.mass));

            staff = AerialReflect.staff(server, SableBridge.serverSubLevelOf(this));
            pilot.staff(staff != AerialReflect.STAFF_NONE);
            nav = navDestination(server);
            if (!nav.found() && (pilot.mode() == AerialPilot.Mode.GO_TO || setup.leave == LeaveMode.GO_TO)) {
                problems.add(nav.problem());
            }
            long t3 = System.nanoTime();
            if (now % TERRAIN_INTERVAL == 0) readTerrain(server, r);
            long t4 = System.nanoTime();
            cost.add(AerialCost.Phase.TERRAIN, t4 - t3);
            command = pilot.tick(r.sense, keys(), nav.xz());
            long t5 = System.nanoTime();
            cost.add(AerialCost.Phase.PILOT, (t3 - t2) + (t5 - t4));
            act(server, r, command, now);

            if (command.mode().flying()) AerialReflect.wake(server, SableBridge.serverSubLevelOf(this));
            problems.addAll(command.notes());
            links.tick(server, setup, lampProposals, signals(server, r, now));
            cost.add(AerialCost.Phase.ACT, System.nanoTime() - t5);
            cost.changes(props.takeChanges());
            logAnomaly(r, command, now);
        }
        reading = r;
        bell(server);

        if (lastMode == null) {
            lastMode = pilot.mode();
            if (modeSince == AerialSnapshot.NO_TICK) modeSince = now;
        } else if (pilot.mode() != lastMode) {
            lastMode = pilot.mode();
            modeSince = now;
            setChanged();
        }
        long t6 = System.nanoTime();
        forcesNanos = 0L;
        if (craft != null && demanded(now)) {
            AerialForces.keepTracking(SableBridge.serverSubLevelOf(this));
        }
        buildSnapshot(server, craft, r, now);
        long t7 = System.nanoTime();
        cost.add(AerialCost.Phase.FORCES, forcesNanos);
        cost.add(AerialCost.Phase.SNAPSHOT, Math.max(0L, t7 - t6 - forcesNanos));

        if (pilotId != null) {
            ServerPlayer co = coPilotId == null ? null : server.getServer().getPlayerList().getPlayer(coPilotId);
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(pilotId);
            if (co != null) syncHelm(co, false);
            if (p != null) syncHelm(p, true);

            if (now % STATUS_INTERVAL == 0 && now >= statusFrom) {
                if (p != null && !AviatorHelmets.hudShowing(p)) p.displayClientMessage(Component.literal(statusLine(r)), true);
                if (co != null && !AviatorHelmets.hudShowing(co)) co.displayClientMessage(Component.literal(statusLine(r)), true);
            }
        }
        if (now % HUD_INTERVAL == 0) sendHud(server, craft, r);
        if (now - lastPublish >= PUBLISH_INTERVAL) publish(server, now);
        if (!watchers.isEmpty() && now % VIEW_INTERVAL == 0) {
            watchers.values().removeIf(last -> now - last > WATCH_TIMEOUT_TICKS);
            sendViews(server, false);
        }

        cockpitTick(server, craft, r);
        cost.add(AerialCost.Phase.CONSOLE, System.nanoTime() - t7);
        cost.endTick();
    }

    // world heightmap, ships live in the plot grid. loaded chunks only
    private void readTerrain(ServerLevel server, Reading r) {
        if (!pilot.mode().flying() || r.bounds == null) {
            pilot.terrain(null);
            return;
        }
        AerialPilot.Sense s = r.sense;
        double[] u = TerrainGuard.track(pilot.mode() == AerialPilot.Mode.GO_TO, nav.xz(), s.x(), s.z(), s.vx(), s.vz(),
                s.heading());
        double[] b = r.bounds;
        double spanX = b[3] - b[0];
        double spanZ = b[5] - b[2];
        double halfLength = 0.5 * (Math.abs(u[0]) * spanX + Math.abs(u[1]) * spanZ);
        double halfWidth = 0.5 * (Math.abs(u[1]) * spanX + Math.abs(u[0]) * spanZ);
        double along = Math.max(0.0, s.vx() * u[0] + s.vz() * u[1]);

        double drop = Double.isFinite(r.lowest) ? s.y() - r.lowest : s.y() - b[1];
        pilot.terrain(TerrainGuard.read(s.x(), s.z(), u[0], u[1], drop, halfLength, halfWidth,
                TerrainGuard.reach(along), (x, z) -> groundAt(server, x, z)));
    }

    private static double groundAt(ServerLevel server, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!(server.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))
                instanceof LevelChunk chunk)) {
            return Double.NaN;
        }
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15) + 1;
    }

    private void rescan(ServerLevel server, @Nullable Object craft, boolean shape) {
        scan = Vcc4Scanner.scan(server, this);
        props.rescan(scan);
        jets.rescan(scan);
        int noseWas = noseTurns();
        proposedNose = proposeNose();

        if (noseTurns() != noseWas) pilot.heading().reset();
        List<BlockEntity> onBody = craft == null ? List.of() : Vcc4Scanner.onOwnBody(server, this);
        lift.rescan(onBody);
        navTable = null;
        bridge = null;
        otherAerials.clear();
        portableEngines.clear();
        for (BlockEntity be : onBody) {
            if (be instanceof AerialBE) otherAerials.add(be.getBlockPos().immutable());
            else if (bridge == null && be instanceof BattleScreenDisplayBE) bridge = be.getBlockPos().immutable();
            else if (navTable == null && SableBridge.matchesNavTable(be)) navTable = be.getBlockPos().immutable();
            else if (AerialReflect.is(PORTABLE_ENGINE, be)) portableEngines.add(be.getBlockPos().immutable());
        }
        guns.clear();
        if (bridge != null) {
            for (Vcc4Scanner.Machine m : scan.machines()) if (turnsAGun(m)) guns.add(m.governor().asLong());
        }
        tools.gunnery(guns::contains);
        lampProposals = lampProposals();
        links.sync(server, scan.links(), setup, lampProposals);
        measureShape(server, craft, shape);
    }

    static boolean turnsAGun(Vcc4Scanner.Machine m) {
        for (String id : m.drives()) if (id != null && id.contains("cannon_mount")) return true;
        return false;
    }

    private boolean yields() {
        for (BlockPos other : otherAerials) if (other.compareTo(worldPosition) < 0) return true;
        return false;
    }

    Direction noseDirection() {
        Direction d = facing();
        for (int i = 0; i < noseTurns(); i++) d = d.getClockWise();
        return d;
    }

    private Direction facing() {
        return getBlockState().hasProperty(AerialBlock.FACING) ? getBlockState().getValue(AerialBlock.FACING) : Direction.NORTH;
    }

    int noseTurns() {
        return Math.floorMod(setup.noseSet ? setup.noseTurn : proposedNose, 4);
    }

    private int proposeNose() {
        List<double[]> pushes = new ArrayList<>();
        double rpm = maxRpm();
        for (AerialProps.Governor g : props.governors()) {
            AerialSetup.EngineJob job = setup.engine(g.pos.asLong()).job();
            if (job == AerialSetup.EngineJob.OFF || job == AerialSetup.EngineJob.LIFT) continue;
            for (Vcc4Scanner.Prop prop : g.props) {
                PropLearner l = props.learnedAt(prop.pos());
                double w = l != null && l.known() ? l.gain : AerialProps.priorGain(prop);
                Vector3d a = prop.axis();

                pushes.add(new double[] {a.x, a.y, a.z, w * rpm});
            }
        }
        for (AerialJets.Jet j : jets.jets()) {
            AerialSetup.Engine e = setup.engines.get(j.pos.asLong());
            AerialSetup.EngineJob job = e != null ? e.job() : AerialJets.proposed(j);
            if (job == AerialSetup.EngineJob.OFF || job == AerialSetup.EngineJob.LIFT) continue;
            Vector3d a = j.prop.axis();
            pushes.add(new double[] {a.x, a.y, a.z, Math.max(0.0, j.prop.strength())});
        }
        Direction f = facing();
        return NoseRule.proposeTurns(f.getStepX(), f.getStepZ(), pushes);
    }

    private Vector3d noseBody() {
        Direction d = noseDirection();
        return new Vector3d(d.getStepX(), 0.0, d.getStepZ());
    }

    private record Reading(AerialPilot.Sense sense, Quaterniond orientation, Vector3d comLocal, Vector3d comWorld,
                           Vector3d velocity, Vector3d spin, Vector3d air, @Nullable Vector3d wind, boolean airMeasured,
                           double pressure, double pressureAbove, double seaLevel, Vector3d gravity, double mass,
                           @Nullable double[] inertia, Vector3d noseBody, Vector3d noseWorld, Vector3d rightWorld,
                           double bottom, double lowest, List<ThrustSplit.Actuator> actuators, @Nullable double[] bounds) {
    }

    @Nullable
    private Reading read(ServerLevel server, Object craft) {
        Quaterniond q = SableBridge.bodyOrientation(craft);
        Vector3d comLocal = SableBridge.centerOfMassLocal(craft);
        if (q == null || comLocal == null) return null;
        Vector3d comWorld = SableBridge.localToWorld(craft, comLocal);

        boolean posed = comWorld != null;
        if (comWorld == null) comWorld = SableBridge.originWorld(craft);
        double mass = SableBridge.mass(this);
        if (comWorld == null || !(mass > 0.0)) return null;
        Vector3d v = orZero(SableBridge.linearVelocityWorld(this));
        Vector3d w = orZero(SableBridge.angularVelocityWorld(this));

        Vec3 windRead = SableBridge.windWorldAt(server, new Vec3(comWorld.x, comWorld.y, comWorld.z));
        Vector3d wind = windRead == null ? null : orZero(new Vector3d(windRead.x, windRead.y, windRead.z));
        Vector3d air = new Vector3d(v);
        if (wind != null) air.sub(wind);
        Vector3d gravity = SableBridge.gravityWorld(this);
        if (gravity == null || !(gravity.length() > 0.0)) gravity = new Vector3d(0.0, -11.0, 0.0);
        double g = gravity.length();
        double sea = server.getSeaLevel();
        double p = AerialReflect.airPressure(server, comWorld);
        double pAbove = AerialReflect.airPressure(server, new Vector3d(comWorld).add(0.0, PressureModel.PROBE, 0.0));
        PressureModel curve = PressureModel.measured(comWorld.y, p, pAbove, PressureModel.PROBE, sea);
        double pressure = Double.isFinite(p) ? p : curve.at(comWorld.y);

        Vector3d noseBody = noseBody();
        Vector3d noseWorld = q.transform(new Vector3d(noseBody));
        Vector3d rightWorld = q.transform(new Vector3d(-noseBody.z, 0.0, noseBody.x));
        double level = Math.hypot(noseWorld.x, noseWorld.z);
        double nx = level > 1.0e-6 ? noseWorld.x / level : 0.0;
        double nz = level > 1.0e-6 ? noseWorld.z / level : -1.0;
        double heading = HeadingLaw.normalise(Math.toDegrees(Math.atan2(nx, -nz)));
        double airForward = air.x * nx + air.z * nz;
        double airSide = air.x * -nz + air.z * nx;

        double yawRate = -Math.toDegrees(w.y);
        double[] inertia = SableBridge.inertiaTensor(this);
        Vector3d upBody = q.transformInverse(new Vector3d(0.0, 1.0, 0.0));
        double yawInertia = inertia == null ? mass * size * size / 3.0 : quadratic(inertia, upBody);

        lift.read(server, craft, curve);
        lift.fillPressure(pressure);
        lift.choose(server, setup::balloonDriven);

        BodyFrame frame = BodyFrame.of(v3(noseBody), V3.UNIT_Y, v3(comLocal));
        V3 airBody = v3(q.transformInverse(new Vector3d(air)));
        List<ThrustSplit.Actuator> actuators = new ArrayList<>(props.actuators(frame, airBody, pressure, maxRpm(), setup));

        jets.read(server);
        actuators.addAll(jets.actuators(frame, setup));
        double[] up = ThrustSplit.authority(actuators, true);
        double[] down = ThrustSplit.authority(actuators, false);
        double[] fade = props.forwardFade(pressure);

        fade[0] += jets.forward();
        thrust = new AerialPilot.Thrust(up[Axis6.FORWARD.index()], down[Axis6.FORWARD.index()], fade[0], fade[1],
                Math.min(up[Axis6.RIGHT.index()], down[Axis6.RIGHT.index()]), up[Axis6.UP.index()],
                down[Axis6.UP.index()], Math.min(up[Axis6.YAW.index()], down[Axis6.YAW.index()]));

        double[] bounds = AerialReflect.worldBounds(craft);
        double boxBottom = bounds == null ? Double.NaN : bounds[1];
        HullShape.Keel hull = posed ? hullKeel(server, q, comLocal, comWorld) : HullShape.Keel.NONE;
        double keel;
        double bottom;
        if (Double.isFinite(hull.clearance())) {
            keel = hull.clearance();
            bottom = hull.pointY();
        } else if (keelPoints == null && shapeWalk != null) {
            keel = Double.NaN;
            bottom = Double.NaN;
        } else {
            keel = keelHeight(server, bounds);
            bottom = boxBottom;
        }
        double lowest = Double.isFinite(hull.lowestY()) ? hull.lowestY() : boxBottom;
        AerialPilot.Sense sense = new AerialPilot.Sense(comWorld.y, v.y, comWorld.x, comWorld.z, v.x, v.z, airForward,
                airSide, heading, yawRate, mass, g, yawInertia, curve, pressure,
                lift.cells(group -> lon(group.centre, comLocal, noseBody)), lift.passiveLift(g), keel,
                server.getMaxBuildHeight(), thrust);
        tools.assign(scan, setup, noseBody, comLocal);
        return new Reading(sense, q, comLocal, comWorld, v, w, air, wind, wind != null, pressure, pAbove, sea, gravity, mass,
                inertia, noseBody, noseWorld, rightWorld, bottom, lowest, actuators, bounds);
    }

    private HullShape.Keel hullKeel(ServerLevel server, Quaterniond q, Vector3d comLocal, Vector3d comWorld) {
        double[][] points = keelPoints;
        if (points == null || points.length == 0) return HullShape.Keel.NONE;
        Vector3d d = new Vector3d();
        return HullShape.keel(points, (x, y, z, out) -> {
            q.transform(d.set(x - comLocal.x, y - comLocal.y, z - comLocal.z));
            out[0] = comWorld.x + d.x;
            out[1] = comWorld.y + d.y;
            out[2] = comWorld.z + d.z;
        }, (x, z) -> groundAt(server, x, z));
    }

    private static double lon(@Nullable Vector3d at, Vector3d comLocal, Vector3d noseBody) {
        return at == null ? 0.0 : new Vector3d(at).sub(comLocal).dot(noseBody);
    }

    private static double keelHeight(ServerLevel server, @Nullable double[] b) {
        if (b == null) return Double.NaN;
        double ground = Double.NEGATIVE_INFINITY;
        double[] xs = {b[0], 0.5 * (b[0] + b[3]), b[3]};
        double[] zs = {b[2], 0.5 * (b[2] + b[5]), b[5]};
        for (double x : xs) {
            for (double z : zs) {
                BlockPos column = BlockPos.containing(x, b[1], z);
                if (!server.hasChunkAt(column)) continue;
                ground = Math.max(ground, server.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()));
            }
        }
        return Double.isFinite(ground) ? b[1] - ground : Double.NaN;
    }

    private void measureShape(ServerLevel server, @Nullable Object craft, boolean force) {
        int[] box = AerialReflect.plotBounds(craft);
        Vector3d com = craft == null ? null : SableBridge.centerOfMassLocal(craft);
        if (box == null || com == null) {
            silhouette = AerialSnapshot.Silhouette.NONE;
            envSide = new byte[0];
            envTop = new byte[0];
            blocks = -1;
            shapeBox = null;
            shapeCom = null;
            shapeWalk = null;
            shapeHull = null;
            keelPoints = null;
            return;
        }
        size = Math.max(4.0, 0.5 * Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2])));
        if (!force && Arrays.equals(box, shapeBox) && shapeCom != null && shapeCom.distance(com) < 0.5) return;
        shapeBox = box;
        shapeCom = new Vector3d(com);
        if (!HullShape.fits(box)) {
            silhouette = AerialSnapshot.Silhouette.NONE;
            envSide = new byte[0];
            envTop = new byte[0];
            blocks = -1;
            shapeWalk = null;
            shapeHull = null;
            keelPoints = null;
            return;
        }
        shapeWalk = new ShapeWalk(box);
        shapeHull = new HullShape(box);
    }

    private void walkShape(ServerLevel server) {
        ShapeWalk walk = shapeWalk;
        HullShape hull = shapeHull;
        Vector3d com = shapeCom;
        if (walk == null || hull == null || com == null) return;
        boolean done = walk.step(ShapeWalk.CELLS_PER_TICK,
                (sx, sy, sz, x0, y0, z0, x1, y1, z1) -> readSection(server, hull, sx, sy, sz, x0, y0, z0, x1, y1, z1));
        if (!done) return;
        shapeWalk = null;
        shapeHull = null;
        blocks = hull.blocks();
        airtight = hull.airtightBlocks();
        keelPoints = hull.keel();
        Direction nose = noseDirection();
        com.createstats.aerial.V3 f = new com.createstats.aerial.V3(nose.getStepX(), nose.getStepY(), nose.getStepZ());
        silhouette = AerialSilhouette.of(hull.centres(com.x, com.y, com.z), f);
        envelopeBits(hull.envelope(com.x, com.y, com.z), f);
    }

    // read the section palette, getBlockState looks the chunk up again per cell
    private static int readSection(ServerLevel server, HullShape hull, int sx, int sy, int sz,
                                   int x0, int y0, int z0, int x1, int y1, int z1) {
        if (!(server.getChunkSource().getChunkNow(sx, sz) instanceof LevelChunk chunk)) return 0;
        LevelChunkSection[] sections = chunk.getSections();
        int index = server.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= sections.length) return 0;
        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) return 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    if (state.isAir()) continue;
                    hull.add(x, y, z, state.is(AIRTIGHT));
                }
            }
        }
        return (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    }

    private void envelopeBits(Collection<com.createstats.aerial.V3> envelope, com.createstats.aerial.V3 nose) {
        AerialSnapshot.Silhouette sil = silhouette;
        if (sil.isEmpty() || envelope.isEmpty()) {
            envSide = new byte[0];
            envTop = new byte[0];
            return;
        }
        com.createstats.aerial.V3 fwd = nose.length() < 1.0e-6 ? new com.createstats.aerial.V3(0.0, 0.0, -1.0) : nose.normalized();
        com.createstats.aerial.V3 up = com.createstats.aerial.V3.UP;
        com.createstats.aerial.V3 right = fwd.cross(up).normalized();
        byte[] side = new byte[(sil.cols() * sil.sideRows() + 7) >> 3];
        byte[] top = new byte[(sil.cols() * sil.topRows() + 7) >> 3];
        for (com.createstats.aerial.V3 b : envelope) {
            int col = cellOf(b.dot(fwd), sil.originF(), sil.cell(), sil.cols());
            int row = cellOf(b.dot(up), sil.originU(), sil.cell(), sil.sideRows());
            int lat = cellOf(b.dot(right), sil.originR(), sil.cell(), sil.topRows());
            int i = row * sil.cols() + col;
            side[i >> 3] |= (byte) (1 << (i & 7));
            int j = lat * sil.cols() + col;
            top[j >> 3] |= (byte) (1 << (j & 7));
        }
        envSide = side;
        envTop = top;
    }

    private static int cellOf(double v, double min, double cell, int count) {
        return Math.max(0, Math.min(count - 1, (int) Math.floor((v - min) / cell)));
    }

    private void updatePilot(ServerLevel server, long now, @Nullable Object craft) {
        if (coPilotId != null) {
            ServerPlayer co = server.getServer().getPlayerList().getPlayer(coPilotId);
            if (co == null || seatScore(co, craft) <= 0.0) dropCoPilot(co, "You left the seat.");
        }
        ServerPlayer current = pilotId == null ? null : server.getServer().getPlayerList().getPlayer(pilotId);
        if (pilotId != null && (current == null || seatScore(current, craft) <= 0.0)) {
            if (coPilotId != null) handOver(current);
            else leaveHelm(current);
        }
        if (now % 10 != 0 || craft == null || coPilotId != null) return;
        ServerPlayer best = null;
        double bestScore = 0.0;
        for (ServerPlayer p : server.players()) {
            if (!p.isPassenger() || p.isSpectator() || p.getUUID().equals(pilotId) || flyingElsewhere(p)) continue;
            double score = seatScore(p, craft);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best == null) return;
        if (pilotId != null) {
            addCoPilot(best, now);
            return;
        }
        pilotId = best.getUUID();
        FLYING.put(pilotId, new WeakReference<>(this));
        held = HeldKeys.NONE;
        lastInputTick = now;
        helmWatch = setup.keymap.keysInUse();
        props.startCalibration();
        PacketDistributor.sendToPlayer(best, new Vcc4HelmPacket(worldPosition, true, helmWatch));
        best.displayClientMessage(welcome(), true);
        statusFrom = now + WELCOME_TICKS;
        setChanged();
    }

    private Component welcome() {
        Keymap km = setup.keymap;
        MutableComponent m = Component.literal("You have the helm.");
        List<Component> keys = new ArrayList<>();
        Component throttle = keyPair(km, Control.THROTTLE, false);
        Component steer = keyPair(km, Control.STEER, true);
        Component lift = keyPair(km, Control.LIFT, false);
        if (throttle != null) keys.add(Component.empty().append(throttle).append(" telegraph"));
        if (steer != null) keys.add(Component.empty().append(steer).append(" turn"));
        if (lift != null) keys.add(Component.empty().append(lift).append(" altitude"));
        if (!keys.isEmpty()) {
            m.append(" Keys: ");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) m.append(", ");
                m.append(keys.get(i));
            }
            m.append(".");
        }
        m.append(" Use the Aerial for its console.");
        return m.withStyle(ChatFormatting.GREEN);
    }

    @Nullable
    private static Component keyPair(Keymap km, Control c, boolean minusFirst) {
        Component a = keyName(minusFirst ? km.minus(c) : km.plus(c));
        Component b = keyName(minusFirst ? km.plus(c) : km.minus(c));
        if (a == null) return b;
        if (b == null) return a;
        return Component.empty().append(a).append("/").append(b);
    }

    @Nullable
    private static Component keyName(int id) {
        String bind = switch (Channel.byId(id)) {
            case FORWARD -> "key.forward";
            case BACK -> "key.back";
            case LEFT -> "key.left";
            case RIGHT -> "key.right";
            case JUMP -> "key.jump";
            case SPRINT -> "key.sprint";
            case AUX1 -> "key.create_stats.vcc4_aux1";
            case AUX2 -> "key.create_stats.vcc4_aux2";
            case AUX3 -> "key.create_stats.vcc4_aux3";
            case NONE -> null;
        };
        if (bind != null) return Component.keybind(bind);
        if (KeyIds.isKey(id)) {
            int code = KeyIds.glfwKey(id);
            if (code == 32) return Component.literal("Space");
            if (code > 32 && code < 127) return Component.literal(String.valueOf((char) code));
        }
        return null;
    }

    private void addCoPilot(ServerPlayer p, long now) {
        coPilotId = p.getUUID();
        FLYING.put(coPilotId, new WeakReference<>(this));
        coHeld = HeldKeys.NONE;
        coLastInputTick = now;
        PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, true, setup.keymap.keysInUse()));
        ServerPlayer pilotPlayer = p.getServer() == null || pilotId == null ? null : p.getServer().getPlayerList().getPlayer(pilotId);
        String with = pilotPlayer == null ? "the pilot" : pilotPlayer.getGameProfile().getName();
        p.displayClientMessage(Component.literal("You are co-pilot with " + with + ": your keys count with theirs.")
                .withStyle(ChatFormatting.GREEN), true);
        setChanged();
    }

    private void dropCoPilot(@Nullable ServerPlayer p, @Nullable String why) {
        if (coPilotId == null) return;
        WeakReference<AerialBE> ref = FLYING.get(coPilotId);
        if (ref != null && ref.get() == this) FLYING.remove(coPilotId, ref);
        coPilotId = null;
        coHeld = HeldKeys.NONE;
        if (p != null) {
            PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            if (why != null) p.displayClientMessage(Component.literal(why), true);
        }
        setChanged();
    }

    private void handOver(@Nullable ServerPlayer leaving) {
        if (pilotId != null) {
            WeakReference<AerialBE> ref = FLYING.get(pilotId);
            if (ref != null && ref.get() == this) FLYING.remove(pilotId, ref);
        }
        pilotId = coPilotId;
        held = coHeld;
        lastInputTick = coLastInputTick;
        coPilotId = null;
        coHeld = HeldKeys.NONE;
        if (leaving != null) {
            PacketDistributor.sendToPlayer(leaving, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            ServerPlayer next = leaving.getServer() == null || pilotId == null ? null
                    : leaving.getServer().getPlayerList().getPlayer(pilotId);
            leaving.displayClientMessage(Component.literal("You left the helm: "
                    + (next == null ? "your co-pilot" : next.getGameProfile().getName()) + " has it."), true);
        }
        setChanged();
    }

    private void leaveHelm(@Nullable ServerPlayer player) {
        if (coPilotId != null && level instanceof ServerLevel server) {
            dropCoPilot(server.getServer().getPlayerList().getPlayer(coPilotId), "The helm was let go: you are no longer co-pilot.");
        }
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            AerialPilot.Thrust t = thrust;
            boolean noPush = !(t.maxForward() + t.maxReverse() + t.maxSide() > 0.0);
            String why = pilot.grounded()
                    ? "You left the helm: the ship is moored and everything is handed back."
                    : pilot.leaveMode() == LeaveMode.ANCHOR && noPush
                    ? "You left the helm: the ship holds its height and heading, and with no engine to hold its spot it"
                            + " drifts with the wind."
                    : "You left the helm: the ship " + pilot.leaveMode().does + ". The console's Flight page changes that.";
            player.displayClientMessage(Component.literal(why), true);
        }
        if (pilotId != null) {
            WeakReference<AerialBE> ref = FLYING.get(pilotId);
            if (ref != null && ref.get() == this) FLYING.remove(pilotId, ref);
        }
        pilotId = null;
        held = HeldKeys.NONE;
        helmWatch = new int[0];
        setChanged();
    }

    private void syncHelm(ServerPlayer p, boolean remember) {
        int[] watch = setup.keymap.keysInUse();
        if (Arrays.equals(watch, helmWatch)) return;
        if (remember) helmWatch = watch;
        PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, true, watch));
    }

    private double seatScore(ServerPlayer p, @Nullable Object craft) {
        if (!p.isPassenger() || craft == null) return 0.0;
        return SeatRule.score(Vcc4Reflect.seatCraftId(p), SableBridge.subLevelId(craft), SeatReach.offset(p, worldPosition, craft));
    }

    private boolean flyingElsewhere(ServerPlayer p) {
        WeakReference<AerialBE> ref = FLYING.get(p.getUUID());
        AerialBE other = ref == null ? null : ref.get();
        if (other == null || other == this || other.isRemoved()
                || !(p.getUUID().equals(other.pilotId) || p.getUUID().equals(other.coPilotId))) {
            if (ref != null && other != this) FLYING.remove(p.getUUID(), ref);
            return false;
        }
        return true;
    }

    @Override
    public void acceptInput(ServerPlayer player, HeldKeys keys) {
        if (level == null) return;
        if (pilotId != null && pilotId.equals(player.getUUID())) {
            held = keys == null ? HeldKeys.NONE : keys;
            lastInputTick = level.getGameTime();
        } else if (coPilotId != null && coPilotId.equals(player.getUUID())) {
            coHeld = keys == null ? HeldKeys.NONE : keys;
            coLastInputTick = level.getGameTime();
        }
    }

    private HeldKeys keysHeld(long now) {
        HeldKeys mine = pilotId == null || now - lastInputTick > INPUT_TIMEOUT_TICKS ? HeldKeys.NONE : held;
        HeldKeys co = coPilotId == null || now - coLastInputTick > INPUT_TIMEOUT_TICKS ? HeldKeys.NONE : coHeld;
        return HeldKeys.union(mine, co);
    }

    private void readControls(long now) {
        HeldKeys k = keysHeld(now);
        Keymap km = setup.keymap;
        Set<Control> both = EnumSet.noneOf(Control.class);
        for (Control c : Control.values()) {
            controls[c.ordinal()] = c.isAxis() ? km.axis(c, k) : (km.held(c, k) ? 1.0 : 0.0);
            if (c.isAxis() && km.plus(c) != KeyIds.NONE && km.minus(c) != KeyIds.NONE) both.add(c);
        }
        tools.keysBound(both);
    }

    private AerialPilot.Keys keys() {
        if (pilotId == null) return AerialPilot.Keys.NONE;
        return new AerialPilot.Keys(true, controls[Control.THROTTLE.ordinal()], controls[Control.STEER.ordinal()],
                controls[Control.LIFT.ordinal()], controls[Control.SLIDE.ordinal()], controls[Control.BOOST.ordinal()] > 0.5,
                controls[Control.TRIM.ordinal()]);
    }

    private static final double PLOT_SPACE = 20_000_000.0;

    private NavDestination navDestination(ServerLevel server) {
        BlockEntity be = navTable == null ? null : SableBridge.getBlockEntityOnContraption(server, navTable);
        if (be == null || !SableBridge.matchesNavTable(be)) return NavDestination.choose(false, null, false, false, null);
        Vec3 table = SableBridge.navTableProjectedTarget(be);
        double[] tableAt = table == null || Math.abs(table.x) > PLOT_SPACE || Math.abs(table.z) > PLOT_SPACE
                ? null : new double[] {table.x, table.y, table.z};
        ItemStack held = SableBridge.navTableItem(be);
        LodestoneTracker tracker = held.get(DataComponents.LODESTONE_TRACKER);
        double[] lodestone = null;
        boolean lost = false;
        boolean elsewhere = false;
        if (tracker != null) {
            Optional<GlobalPos> at = tracker.target();
            if (at.isEmpty()) {
                lost = true;
            } else if (!at.get().dimension().equals(server.dimension())) {
                elsewhere = true;
            } else {
                BlockPos pos = at.get().pos();

                if (tracker.tracked() && server.isLoaded(pos) && !server.getBlockState(pos).is(Blocks.LODESTONE)) {
                    lost = true;
                } else {
                    Vec3 world = SableBridge.projectOutOfSubLevel(server, Vec3.atCenterOf(pos));
                    if (Math.abs(world.x) < PLOT_SPACE && Math.abs(world.z) < PLOT_SPACE) {
                        lodestone = new double[] {world.x, world.y, world.z};
                    }
                }
            }
        }
        return NavDestination.choose(true, lodestone, lost, elsewhere, tableAt);
    }

    LeaveMode cycleLeaveMode() {
        LeaveMode m = pilot.cycleLeaveMode();
        setChanged();
        return m;
    }

    LeaveMode leaveMode() {
        return pilot.leaveMode();
    }

    private void act(ServerLevel server, Reading r, AerialPilot.Command c, long now) {
        boolean piloted = pilotId != null;
        if (c.handsOff()) {
            if (c.mode() == AerialPilot.Mode.MOORED) moor(server, now);
            else handsOff(server);

            if (tools.drive(server, scan, controls, piloted, false, 0.0, 0.0, 1.0, worldPosition, controllerId, problems)) setChanged();
            return;
        }
        lift.drive(server, c.gasTargets(), now);
        double weight = r.mass * r.sense.g();
        int governors = props.governors().size();

        List<ThrustSplit.Actuator> reach = jets.windowed(r.actuators, governors, setup.feel);
        ThrustSplit.Result split = ThrustSplit.solve(reach, c.wrench(), c.care(), weight, weight * size);
        applied = props.drive(server, split, r.pressure, worldPosition, controllerId, problems);
        double[] pushes = split.force();
        jets.drive(server, pushes.length > governors ? Arrays.copyOfRange(pushes, governors, pushes.length) : new double[0],
                controllerId, setup.feel, problems);

        double[] lit = jets.measured();
        applied = new AerialProps.Applied(applied.forward() + lit[Axis6.FORWARD.index()], applied.up() + lit[Axis6.UP.index()],
                applied.yaw() + lit[Axis6.YAW.index()]);
        pilot.applied(applied.forward(), applied.up(), applied.yaw());
        if (props.learn(server)) setChanged();
        if (tools.drive(server, scan, controls, piloted, true, c.turn(), setup.trim, c.flow(), worldPosition, controllerId, problems)) {
            setChanged();
        }
    }

    private void handsOff(ServerLevel server) {
        lift.handBack(server);
        props.release(server, controllerId);
        jets.settle(server, controllerId, setup.feel);
        applied = AerialProps.Applied.NONE;
        pilot.applied(0.0, 0.0, 0.0);
    }

    private void moor(ServerLevel server, long now) {
        lift.bank(server, now);
        props.release(server, controllerId);
        jets.settle(server, controllerId, setup.feel);
        applied = AerialProps.Applied.NONE;
        pilot.applied(0.0, 0.0, 0.0);
        if (lift.redstoneWouldLight(server)) {
            problems.add("Moored with the burners banked: your own redstone would light them and lift the ship with nobody"
                    + " at the helm. Set a balloon By hand on the Lift page to fly it on your own redstone.");
        }
    }

    private void logAnomaly(Reading r, AerialPilot.Command c, long now) {
        if (!c.mode().flying() || now - lastLog < LOG_INTERVAL) return;
        AerialPilot.Sense s = r.sense;

        double aim = Double.isFinite(pilot.flownAltitude()) ? pilot.flownAltitude() : c.targetAltitude();
        double off = Double.isFinite(aim) ? s.y() - aim : 0.0;
        boolean fast = Math.abs(s.vy()) > setup.feel.climb + 1.5;
        if (Math.abs(off) < 4.0 && !fast && pilot.frozen() < 40) return;
        lastLog = now;
        double gas = 0.0;
        double target = 0.0;
        double capacity = 0.0;
        double maxGas = 0.0;
        int lit = 0;
        for (AerialLift.Group g : lift.groups()) {
            gas += g.gas;
            target += g.target;
            capacity += g.capacity;
            maxGas += g.maxGas;
            lit += g.dither.lit();
        }
        double[] th = pilot.vertical().state();
        double asked = 0.0;
        for (double t : c.gasTargets()) asked += t;
        CreateStats.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[VCC Mk IV Aerial] flight %s at %s: y %.2f vy %.2f held %.2f ceiling %.1f | gas %.1f target %.1f asked %.1f"
                        + " max %.1f capacity %.0f lit %d | mass %.1f g %.2f p %.3f | learned drag %.0f wing %.1f lean %.2f"
                        + " hidden %.0f | frozen %d nose %s%s | flown %.2f terrain %s | cost us %s, gov %d/s",
                c.mode().label, worldPosition.toShortString(), s.y(), s.vy(), c.targetAltitude(), c.ceiling(), gas, target,
                asked, maxGas, capacity, lit, s.mass(), s.g(), s.pressure(), th[VerticalObserver.DRAG],
                th[VerticalObserver.WING], th[VerticalObserver.THRUST], th[VerticalObserver.CONSTANT], pilot.frozen(),
                noseDirection().getSerializedName(), setup.noseSet ? "" : " (engines)", aim, pilot.guard().verdict(),
                cost.phases(), cost.changesPerSecond()));
    }

    static double maxRpm() {
        try {
            return AllConfigs.server().kinetics.maxRotationSpeed.get();
        } catch (RuntimeException e) {
            return PropModel.DEFAULT_MAX_RPM;
        }
    }

    private void bell(ServerLevel server) {
        int rings = pilot.telegraph().rings();
        if (lastRings == Integer.MIN_VALUE) {
            lastRings = rings;
            return;
        }
        if (rings == lastRings) return;
        lastRings = rings;
        if (!setup.bell) return;
        float pitch = (float) (1.2 + 0.3 * pilot.telegraph().position());
        server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.7f, pitch);
    }

    private AirLight.Signals signals(ServerLevel server, Reading r, long now) {
        AerialPilot.Command c = command;
        boolean flying = c != null && !c.handsOff();
        boolean low = Double.isFinite(r.sense.keelHeight()) && r.sense.keelHeight() < AirLight.LOW_HEIGHT;
        boolean astern = pilot.telegraph().position() < -1.0e-3 || (c != null && c.engine() < -1.0e-3);
        boolean alarm = c != null && (c.liftShort() || c.ceiling() == Double.NEGATIVE_INFINITY) || props.anyTrouble()
                || jets.anyTrouble();
        return new AirLight.Signals(flying, pilotId != null, pilot.mode() == AerialPilot.Mode.MOORED, low,
                server.isNight() || server.isThundering(), astern, r.velocity.y, alarm,
                controls[Control.BEAM.ordinal()] > 0.5, controls.clone(), now);
    }

    private Map<Long, AerialSetup.Light> lampProposals() {
        Map<Long, AerialSetup.Light> out = new HashMap<>();
        Vector3d nose = noseBody();
        for (Vcc4Scanner.Link l : scan.links()) {
            if (l.lamp() == null || l.facing() == null) continue;
            Direction f = l.facing();
            double forward = f.getStepX() * nose.x + f.getStepZ() * nose.z;
            AirLight job = AirLight.propose(AerialLinks.lampKind(l), f.getStepY(), forward);
            out.put(l.pos().asLong(), new AerialSetup.Light(job, false));
        }
        return out;
    }

    @Override
    public AerialSnapshot aerialSnapshot() {
        if (level != null) lastDemand = level.getGameTime();
        return snapshot;
    }

    private boolean demanded(long now) {
        return lastDemand != Long.MIN_VALUE && now >= lastDemand && now - lastDemand <= DEMAND_TICKS;
    }

    @Override
    public boolean onAerialCommand(AerialCommand command) {
        if (command == AerialCommand.SAVE_TRAIL_AS_ROUTE) {
            route = recorder.trailAsRoute(nameOf(SableBridge.subLevelAccessOf(this)));
            setChanged();
            return route != AerialSnapshot.Route.NONE;
        }
        boolean done = recorder.apply(command);
        if (done) setChanged();
        return done;
    }

    private void buildSnapshot(ServerLevel server, @Nullable Object craft, @Nullable Reading r, long now) {
        if (craft == null || r == null) {
            snapshot = null;
            return;
        }

        if (snapshot != null && now >= lastSnapshot && now - lastSnapshot < SNAPSHOT_INTERVAL) return;
        lastSnapshot = now;
        AerialSnapshot.Body body = new AerialSnapshot.Body(av(r.comWorld),
                new Quat(r.orientation.x, r.orientation.y, r.orientation.z, r.orientation.w), av(r.noseBody),
                av(r.velocity), av(r.spin));
        AerialSnapshot.Air air = new AerialSnapshot.Air(r.airMeasured, av(r.air), r.pressure, r.pressureAbove, r.seaLevel,
                av(r.gravity));
        com.createstats.aerial.V3 inertia = r.inertia == null ? com.createstats.aerial.V3.NAN
                : new com.createstats.aerial.V3(r.inertia[0], r.inertia[4], r.inertia[8]);

        AerialSnapshot.Mass mass = new AerialSnapshot.Mass(r.mass, inertia, blocks, pilot.loadMass(r.sense.g()));

        List<AerialSnapshot.Propeller> propellers = new ArrayList<>();
        for (AerialProps.Governor g : props.governors()) {
            int n = 0;
            for (Vcc4Scanner.Prop prop : g.props) {
                n++;
                BlockEntity be = SableBridge.getBlockEntityOnContraption(server, prop.pos());
                if (be == null) continue;
                PropLearner l = props.learnedAt(prop.pos());
                int sign = l != null && l.signSeen ? l.sign : 1;
                Vector3d dir = new Vector3d(prop.axis()).normalize().mul(sign);
                double stat = Math.abs(AerialReflect.staticThrust(be));
                double flow = Math.abs(AerialReflect.airflow(be));
                V3 airBody = v3(r.orientation.transformInverse(new Vector3d(r.air)));
                double along = airBody.dot(v3(dir));
                double scaling = flow > 1.0e-6 ? Math.max(0.0, Math.min(1.0, (flow - along) / flow)) : 1.0;
                double rpm = be instanceof KineticBlockEntity k ? Math.abs(k.getSpeed()) : Double.NaN;
                SableBridge.PropData bearing = SableBridge.readProp(be);
                String kind = bearing == null ? "small" : bearing.isGyro() ? "gyro" : "bearing";
                propellers.add(new AerialSnapshot.Propeller(g.name + (g.props.size() > 1 ? "." + n : ""), kind,
                        av(new Vector3d(prop.centre()).sub(r.comLocal)), av(dir), stat, flow, scaling, r.pressure, rpm,
                        shaftStress(be), stat > 1.0e-6));
            }
        }
        List<AerialSnapshot.Balloon> balloons = new ArrayList<>();
        int b = 0;
        for (AerialLift.Group g : lift.groups()) {
            b++;
            com.createstats.aerial.V3 at = g.centre == null ? com.createstats.aerial.V3.NAN
                    : av(new Vector3d(g.centre).sub(r.comLocal));
            balloons.add(new AerialSnapshot.Balloon("B" + b, g.kind, at, g.lift, g.gas, g.target, g.capacity,
                    Double.isFinite(g.change) ? g.change * 20.0 : Double.NaN, g.output, g.pressure, g.heaters.size(),
                    g.dither.lit(), g.driven));
        }
        Vec3 goal = nav.found() ? new Vec3(nav.x(), nav.y(), nav.z()) : null;
        String label = nav.source() == NavDestination.Source.LODESTONE ? "Lodestone" : "Navigation Table";
        AerialSnapshot.Target target = goal == null ? null
                : new AerialSnapshot.Target(label, new com.createstats.aerial.V3(goal.x, goal.y, goal.z), "nav_table");
        AerialSnapshot.Autopilot autopilot = autopilot(goal);

        List<AerialSnapshot.Force> forces = List.of();
        if (demanded(now)) {
            long tf = System.nanoTime();
            forces = AerialForces.read(server, SableBridge.serverSubLevelOf(this), r.orientation, r.comLocal, r.comWorld);
            forcesNanos += System.nanoTime() - tf;
        }
        if (lastTraffic == Long.MIN_VALUE || now < lastTraffic || now - lastTraffic >= TRAFFIC_INTERVAL) {
            lastTraffic = now;
            traffic = AerialTraffic.read(server, SableBridge.subLevelId(craft), r.comWorld, r.bounds);
        }
        AerialSnapshot measured = new AerialSnapshot(now, nameOf(craft), SableBridge.subLevelId(craft), AerialCommand.all(),
                body, air, mass, forces, propellers, balloons, engine(server), target, autopilot, null,
                traffic, terrainPatch(server, r, now), route, null, null, null,
                silhouette.withEnvelope(envSide, envTop), helm(server, r), parts(r), null);
        recorder.observe(measured);
        snapshot = recorder.recorded(measured);
    }

    private AerialSnapshot.Helm helm(ServerLevel server, Reading r) {
        AerialPilot.Command c = command;
        AerialPilot.Sense s = r.sense;
        String pilotName = "";
        if (pilotId != null) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(pilotId);
            pilotName = p == null ? "?" : p.getGameProfile().getName();
        }
        TerrainGuard.Result g = pilot.guard();
        AerialSnapshot.Guard guard = new AerialSnapshot.Guard(g.verdict().name(), finiteOrNaN(g.floor()),
                finiteOrNaN(g.speedCap()), g.groundY(), g.distance(), g.peakY(), g.peakAt(), g.seen());
        com.createstats.aerial.V3 anchor = com.createstats.aerial.V3.NAN;
        if (pilot.mode() == AerialPilot.Mode.ANCHOR) {
            AerialPilot.State st = pilot.state();
            if (Double.isFinite(st.anchorX()) && Double.isFinite(st.anchorZ())) {
                anchor = new com.createstats.aerial.V3(st.anchorX(), Double.isFinite(st.targetAltitude())
                        ? st.targetAltitude() : r.comWorld.y, st.anchorZ());
            }
        }
        FlightIntent.Line banner = banner(r);
        return new AerialSnapshot.Helm(pilot.mode().name(), setup.leave.name(), pilotName, modeSince, setup.feel.name(),
                pilot.telegraph().position(), c == null ? 0.0 : c.engine(), pilot.telegraph().rings(), c != null && c.braking(),
                c == null ? Double.NaN : c.targetAltitude(), c == null || c.handsOff() ? Double.NaN : pilot.flownAltitude(),
                c == null ? Double.NaN : c.targetHeading(), c == null ? Double.NaN : c.targetSpeed(), pilot.topSpeed(s),
                c == null ? Double.NaN : c.ceiling(), pilot.balloonCeiling(s), setup.altitudeCap, server.getMaxBuildHeight(),
                c == null ? Double.NaN : c.liftUse(), c != null && c.liftShort(), c != null && c.weighing(), setup.trim, staff,
                anchor, guard, banner.text(), banner.alert(), notes(c), thrust.maxForward(), thrust.maxReverse(),
                thrust.maxYaw());
    }

    private List<String> notes(@Nullable AerialPilot.Command c) {
        List<String> out = new ArrayList<>();
        if (c != null) out.addAll(c.notes());
        for (String p : problems) if (!out.contains(p)) out.add(p);
        if (!props.loose().isEmpty()) out.add(looseNote());
        return out;
    }

    private static double finiteOrNaN(double v) {
        return Double.isFinite(v) ? v : Double.NaN;
    }

    private AerialSnapshot.Parts parts(Reading r) {
        List<AerialSnapshot.Part> out = new ArrayList<>();
        Vector3d origin = r.comLocal;
        for (AerialProps.Governor g : props.governors()) {
            Vector3d mean = new Vector3d();
            for (Vcc4Scanner.Prop prop : g.props) mean.add(prop.centre());
            if (!g.props.isEmpty()) mean.div(g.props.size());
            mean.sub(origin);
            double range = g.push >= 0.0 ? g.hi : -g.lo;
            double push = range > 1.0e-9 ? clamp(g.push / range, -1.0, 1.0) : 0.0;
            out.add(new AerialSnapshot.Part(AerialSnapshot.Part.GOVERNOR, g.name, words(g.job.name()),
                    snapshotEngineState(engineState(g)), av(mean), av3(g.direction), quantise(push, 0.01),
                    quantise(g.push, Math.max(1.0, Math.abs(g.push) * 0.02))));
        }
        for (AerialJets.Jet j : jets.jets()) {
            Vector3d at = new Vector3d(j.prop.centre()).sub(origin);
            double force = j.reading == null ? 0.0 : Math.max(0.0, j.reading.force());
            double push = j.ceiling > 1.0e-9 ? clamp(force / j.ceiling, 0.0, 1.0) : 0.0;
            int state = switch (jetState(j)) {
                case AerialView.ENGINE_OFF -> AerialSnapshot.Part.OFF;
                case AerialView.ENGINE_FOREIGN -> AerialSnapshot.Part.FOREIGN;
                case AerialView.ENGINE_STARVED -> j.supply() == com.createstats.vccmk4.core.JetSupply.UNPOWERED
                        ? AerialSnapshot.Part.NO_POWER : AerialSnapshot.Part.NO_FUEL;
                default -> AerialSnapshot.Part.OK;
            };
            String name = (j.name.isEmpty() ? "T" : j.name) + " " + words(j.kind().name());
            out.add(new AerialSnapshot.Part(AerialSnapshot.Part.THRUSTER, name, words(j.job.name()), state, av(at),
                    av3(j.direction), quantise(push, 0.01), quantise(force, Math.max(1.0, force * 0.02))));
        }
        int fin = 0;
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            fin++;
            long key = s.pos().asLong();
            AerialSetup.Surface sf = tools.surface(key);
            Vector3d at = new Vector3d(Vcc4Scanner.centre(s.pos())).sub(origin);
            int state = !s.assembled() || !s.locked() ? AerialSnapshot.Part.STUCK
                    : sf.role() == AerialSetup.SurfaceRole.OFF ? AerialSnapshot.Part.OFF : AerialSnapshot.Part.OK;
            out.add(new AerialSnapshot.Part(AerialSnapshot.Part.FIN, "F" + fin, words(sf.role().name()), state, av(at),
                    av(new Vector3d(s.hingeAxis())), quantise(tools.angle(key), 0.5), sf.degrees()));
        }
        for (Vcc4Scanner.Link l : scan.links()) {
            long key = l.pos().asLong();
            AerialSetup.Light light = AerialLinks.lightFor(setup, key, lampProposals);
            Vector3d at = new Vector3d(Vcc4Scanner.centre(l.pos())).sub(origin);
            Direction f = l.facing();
            com.createstats.aerial.V3 dir = f == null ? com.createstats.aerial.V3.UP
                    : new com.createstats.aerial.V3(f.getStepX(), f.getStepY(), f.getStepZ());
            out.add(new AerialSnapshot.Part(AerialSnapshot.Part.LAMP, l.first(), words(light.job().name()),
                    AerialSnapshot.Part.OK, av(at), dir, links.strength(key), AerialLinks.lampKind(l)));
        }
        for (Vcc4Scanner.Machine m : scan.machines()) {
            long key = m.governor().asLong();
            boolean gun = tools.gunnery(key);
            Vector3d at = new Vector3d(Vcc4Scanner.centre(m.governor())).sub(origin);
            int state = tools.foreign(key) ? AerialSnapshot.Part.FOREIGN
                    : gun && tools.bridgeHolds(key) ? AerialSnapshot.Part.BRIDGE : AerialSnapshot.Part.OK;
            out.add(new AerialSnapshot.Part(gun ? AerialSnapshot.Part.GUN : AerialSnapshot.Part.TOOL, prettyId(toolName(m)),
                    gun ? "battle bridge" : words(tools.tool(key).input().name()), state, av(at),
                    com.createstats.aerial.V3.UP, quantise(tools.machineOutput(key), 0.01), 0.0));
        }
        double mass = r.mass;
        int known = 0;
        int total = 0;
        for (AerialProps.Governor g : props.governors()) {
            for (Vcc4Scanner.Prop prop : g.props) {
                total++;
                PropLearner l = props.learnedAt(prop.pos());
                if (l != null && l.signSeen) known++;
            }
        }
        AerialSnapshot.Learned learned = new AerialSnapshot.Learned(
                pilot.vertical().dragPerMass(Double.isFinite(mass) ? Math.max(1.0, mass) : 1.0),
                pilot.vertical().estimate(VerticalObserver.WING), pilot.vertical().estimate(VerticalObserver.THRUST),
                pilot.vertical().estimate(VerticalObserver.CONSTANT), pilot.heading().damping(), known, total);
        return new AerialSnapshot.Parts(out, learned, bridge != null);
    }

    static int engineState(AerialProps.Governor g) {
        return g.job == AerialSetup.EngineJob.OFF ? AerialView.ENGINE_OFF
                : g.foreign ? AerialView.ENGINE_FOREIGN
                : g.over ? AerialView.ENGINE_OVERSTRESSED
                : g.starvedLong ? AerialView.ENGINE_STARVED : AerialView.ENGINE_OK;
    }

    static int jetState(AerialJets.Jet j) {
        return j.job == AerialSetup.EngineJob.OFF ? AerialView.ENGINE_OFF
                : j.foreign ? AerialView.ENGINE_FOREIGN
                : j.out() ? AerialView.ENGINE_STARVED : AerialView.ENGINE_OK;
    }

    private static int snapshotEngineState(int viewState) {
        return switch (viewState) {
            case AerialView.ENGINE_OFF -> AerialSnapshot.Part.OFF;
            case AerialView.ENGINE_FOREIGN -> AerialSnapshot.Part.FOREIGN;
            case AerialView.ENGINE_OVERSTRESSED -> AerialSnapshot.Part.OVERSTRESSED;
            case AerialView.ENGINE_STARVED -> AerialSnapshot.Part.STARVED;
            default -> AerialSnapshot.Part.OK;
        };
    }

    private static double quantise(double v, double step) {
        return Double.isFinite(v) && step > 0.0 ? Math.round(v / step) * step : v;
    }

    private static String words(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static String prettyId(String id) {
        if (id == null || id.isEmpty()) return "machine";
        int colon = id.lastIndexOf(':');
        int dot = id.lastIndexOf('.');
        String tail = id.substring(Math.max(colon, dot) + 1);
        return tail.replace('_', ' ');
    }

    private static com.createstats.aerial.V3 av3(V3 v) {
        return new com.createstats.aerial.V3(v.x(), v.y(), v.z());
    }

    private AerialSnapshot.Engine engine(ServerLevel server) {
        if (portableEngines.isEmpty()) return AerialSnapshot.Engine.NONE;
        int count = 0;
        int burning = 0;
        boolean superHeated = false;
        double firstOut = Double.POSITIVE_INFINITY;
        for (BlockPos pos : portableEngines) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, pos);
            if (!AerialReflect.is(PORTABLE_ENGINE, be)) continue;
            count++;
            Object infinite = call(be, "isTotalFuelInfinite");
            Object total = call(be, "getTotalBurnTime");
            Object current = call(be, "getCurrentBurnTime");
            if (current instanceof Number n && n.intValue() > 0) burning++;
            if (call(be, "isSuperHeated") instanceof Boolean b && b) superHeated = true;
            if (!(infinite instanceof Boolean inf && inf) && total instanceof Number t) {
                firstOut = Math.min(firstOut, t.doubleValue() / 20.0);
            }
        }
        if (count == 0) return AerialSnapshot.Engine.NONE;
        return new AerialSnapshot.Engine(count, firstOut, burning / (double) count, superHeated);
    }

    @Nullable
    private static Object call(@Nullable Object target, String name) {
        if (target == null) return null;
        java.lang.reflect.Method m = com.createstats.flight.bridge.ReflectionAccessors.method(target.getClass(), name);
        return m == null ? null : com.createstats.flight.bridge.ReflectionAccessors.invoke(m, target);
    }

    static double shaftStress(@Nullable BlockEntity be) {
        if (!(be instanceof KineticBlockEntity k)) return Double.NaN;
        java.lang.reflect.Field cap = com.createstats.flight.bridge.ReflectionAccessors.field(KineticBlockEntity.class, "capacity");
        java.lang.reflect.Field st = com.createstats.flight.bridge.ReflectionAccessors.field(KineticBlockEntity.class, "stress");
        Object c = com.createstats.flight.bridge.ReflectionAccessors.read(cap, k);
        Object s = com.createstats.flight.bridge.ReflectionAccessors.read(st, k);
        double ratio = c instanceof Number cn && s instanceof Number sn && cn.doubleValue() > 1.0e-6
                ? sn.doubleValue() / cn.doubleValue() : Double.NaN;
        if (k.isOverStressed()) return Double.isFinite(ratio) ? Math.max(1.0, ratio) : 1.0;
        return ratio;
    }

    private AerialSnapshot.Terrain terrainPatch(ServerLevel server, Reading r, long now) {
        double x = r.comWorld.x;
        double z = r.comWorld.z;
        double half = 0.5 * PATCH_SIZE * PATCH_CELL;
        boolean off = patch.size() == 0 || Math.abs(x - (patch.originX() + half)) > 0.25 * half * 2.0
                || Math.abs(z - (patch.originZ() + half)) > 0.25 * half * 2.0;
        if (off || now - lastPatch >= PATCH_INTERVAL || now < lastPatch) {
            lastPatch = now;
            double ox = Math.floor((x - half) / PATCH_CELL) * PATCH_CELL;
            double oz = Math.floor((z - half) / PATCH_CELL) * PATCH_CELL;
            short[] heights = new short[PATCH_SIZE * PATCH_SIZE];
            for (int iz = 0; iz < PATCH_SIZE; iz++) {
                for (int ix = 0; ix < PATCH_SIZE; ix++) {
                    double h = groundAt(server, ox + (ix + 0.5) * PATCH_CELL, oz + (iz + 0.5) * PATCH_CELL);
                    heights[iz * PATCH_SIZE + ix] = Double.isFinite(h)
                            ? (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, h)) : AerialSnapshot.Terrain.UNKNOWN;
                }
            }
            patch = new AerialSnapshot.Terrain(ox, oz, PATCH_CELL, PATCH_SIZE, heights, Double.NaN, Double.NaN);
        }
        return new AerialSnapshot.Terrain(patch.originX(), patch.originZ(), patch.cell(), patch.size(), patch.heights(),
                groundAt(server, x, z), Double.NaN);
    }

    private AerialSnapshot.Autopilot autopilot(@Nullable Vec3 nav) {
        AerialPilot.Command c = command;
        if (c == null || c.handsOff()) {
            String mode = c == null ? "OFF" : c.mode().name();
            return new AerialSnapshot.Autopilot(mode, false, Double.NaN, Double.NaN, Double.NaN,
                    com.createstats.aerial.V3.NAN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        com.createstats.aerial.V3 look = c.mode() == AerialPilot.Mode.GO_TO && nav != null
                ? new com.createstats.aerial.V3(nav.x, nav.y, nav.z) : com.createstats.aerial.V3.NAN;
        double throttle = share(applied.forward(), applied.forward() >= 0.0 ? thrust.maxForward() : thrust.maxReverse());
        double yaw = share(applied.yaw(), thrust.maxYaw());
        return new AerialSnapshot.Autopilot(c.mode().name(), true, c.targetAltitude(), c.targetSpeed(), c.targetHeading(),
                look, throttle, yaw, Double.NaN, c.liftUse());
    }

    private static double share(double v, double max) {
        return max > 1.0e-9 ? Math.min(1.0, Math.abs(v) / max) : Double.NaN;
    }

    private static String nameOf(@Nullable Object craft) {
        return craft == null ? "VCC Mk IV Aerial" : SableBridge.subLevelName(craft);
    }

    void openFor(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        rescan(server, SableBridge.subLevelAccessOf(this), true);
        scanCooldown = SCAN_INTERVAL_IDLE;
        watchers.put(player.getUUID(), server.getGameTime());
        PacketDistributor.sendToPlayer(player, new AerialViewPacket(worldPosition, true, view(server, player)));
    }

    @Override
    public String settingsKey() {
        return "create_stats:vcc_mk4_aerial";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Setup", AerialNbt.tuning(setup));
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        final AerialNbt.Tuning tuning = AerialNbt.readTuning(tag.getCompound("Setup"));
        if (tuning == null) {
            return false;
        }
        if (simulate || alreadyHolds(tag, registries)) {
            return true;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        pilot.setFeel(tuning.feel());
        pilot.setLeaveMode(tuning.leave());
        setup.setCap(tuning.altitudeCap(), server.getMinBuildHeight(),
                server.getMaxBuildHeight() - AerialPilot.WORLD_TOP_MARGIN);
        setup.bell = tuning.bell();
        setup.keymap = tuning.keymap();
        setChanged();
        links.sync(server, scan.links(), setup, lampProposals);
        publish(server, server.getGameTime());
        sendViews(server, false);
        return true;
    }

    public void handleAction(ServerPlayer player, AerialActionPacket p) {
        if (!(level instanceof ServerLevel server)) return;
        if (p.action() == AerialActionPacket.CLOSE) {
            watchers.remove(player.getUUID());
            return;
        }

        if (!watchers.containsKey(player.getUUID()) || !SableBridge.isPlayerInInteractionRangeFor(this, player)) return;
        watchers.put(player.getUUID(), server.getGameTime());
        if (!apply(server, p)) return;
        setChanged();
        links.sync(server, scan.links(), setup, lampProposals);
        publish(server, server.getGameTime());
        sendViews(server, false);
    }

    private boolean apply(ServerLevel server, AerialActionPacket p) {
        long target = p.target();
        Reading r = reading;
        switch (p.action()) {
            case AerialActionPacket.KEEPALIVE -> {
                return false;
            }
            case AerialActionPacket.TELEGRAPH -> pilot.telegraph().ring(Telegraph.Notch.byOrdinal(p.a()));
            case AerialActionPacket.ALTITUDE -> {
                if (r == null) return false;
                pilot.nudgeAltitude(Math.max(-64, Math.min(64, p.a())), r.comWorld.y);
            }
            case AerialActionPacket.HEADING -> {
                if (r == null) return false;
                double from = Double.isFinite(pilot.heading().hold()) ? pilot.heading().hold() : r.sense.heading();
                pilot.holdHeading(p.b() == 1 ? r.sense.heading() : HeadingLaw.normalise(from + Math.max(-180, Math.min(180, p.a()))));
            }
            case AerialActionPacket.TRIM -> setup.setTrim(p.b() == 1 ? 0.0 : setup.trim + Math.max(-20, Math.min(20, p.a())) / 20.0);
            case AerialActionPacket.LEAVE -> pilot.setLeaveMode(LeaveMode.byOrdinal(p.a()));
            case AerialActionPacket.FEEL -> setup.feel = Feel.byOrdinal(p.a());
            case AerialActionPacket.NOSE -> {
                if (p.b() == 1) {
                    setup.noseSet = false;
                } else {
                    setup.noseTurn = Math.floorMod(noseTurns() + p.a(), 4);
                    setup.noseSet = true;
                }

                rescan(server, SableBridge.subLevelAccessOf(this), true);
                pilot.heading().reset();
            }
            case AerialActionPacket.CAP -> {
                double bottom = server.getMinBuildHeight();
                double top = server.getMaxBuildHeight() - AerialPilot.WORLD_TOP_MARGIN;
                if (p.b() == 1) setup.setCap(Double.NaN, bottom, top);
                else if (p.b() == 2) setup.setCap(r == null ? Double.NaN : r.comWorld.y, bottom, top);
                else {
                    double from = Double.isFinite(setup.altitudeCap) ? setup.altitudeCap : (r == null ? Double.NaN : r.comWorld.y);
                    setup.setCap(from + Math.max(-64, Math.min(64, p.a())), bottom, top);
                }
            }
            case AerialActionPacket.BELL -> setup.bell = !setup.bell;
            case AerialActionPacket.FORGET -> {
                double mass = r == null ? 1.0 : r.mass;
                double g = r == null ? 11.0 : r.sense.g();
                pilot.forget(mass, g);
            }
            case AerialActionPacket.RESCAN -> rescan(server, SableBridge.subLevelAccessOf(this), true);
            case AerialActionPacket.RELEARN -> {
                props.learned().clear();
                if (pilotId != null) props.startCalibration();
            }
            case AerialActionPacket.ENGINE_JOB, AerialActionPacket.ENGINE_REVERSE, AerialActionPacket.ENGINE_LIMIT -> {
                if (!isGovernor(target) && !jets.contains(target)) return false;
                AerialSetup.Engine e = setup.engine(target);
                e = switch (p.action()) {
                    case AerialActionPacket.ENGINE_JOB -> e.withJob(AerialSetup.EngineJob.byOrdinal(p.a()));
                    case AerialActionPacket.ENGINE_REVERSE -> e.withReverse(!e.reverse());
                    default -> e.withLimit(p.a());
                };

                if (e.equals(AerialSetup.Engine.DEFAULT) && !jets.contains(target)) setup.engines.remove(target);
                else setup.engines.put(target, e);
            }
            case AerialActionPacket.BALLOON_DRIVEN -> {
                if (!isBalloon(target)) return false;
                if (setup.balloonDriven(target)) setup.balloonOff.put(target, true);
                else setup.balloonOff.remove(target);
            }
            case AerialActionPacket.SURFACE_ROLE, AerialActionPacket.SURFACE_THROW, AerialActionPacket.SURFACE_INVERT,
                 AerialActionPacket.SURFACE_INPUT, AerialActionPacket.SURFACE_LATCH -> {
                if (!isSwivel(target)) return false;
                AerialSetup.Surface s = tools.surface(target);
                s = switch (p.action()) {
                    case AerialActionPacket.SURFACE_ROLE -> {
                        AerialSetup.SurfaceRole role = AerialSetup.SurfaceRole.byOrdinal(p.a());
                        AerialSetup.Surface next = s.withRole(role);

                        if (role == AerialSetup.SurfaceRole.TOOL && next.input() == PartInput.OFF) next = next.withInput(PartInput.AUX_A);
                        yield next;
                    }
                    case AerialActionPacket.SURFACE_THROW -> s.withDegrees(p.a());
                    case AerialActionPacket.SURFACE_INVERT -> s.withInvert(!s.invert());
                    case AerialActionPacket.SURFACE_INPUT -> s.withInput(PartInput.byOrdinal(p.a()));
                    default -> s.withLatched(!s.latched());
                };
                setup.surfaces.put(target, s);
            }
            case AerialActionPacket.TOOL_INPUT, AerialActionPacket.TOOL_RPM, AerialActionPacket.TOOL_LATCH,
                 AerialActionPacket.TOOL_INVERT -> {
                if (!isMachine(target)) return false;
                AerialSetup.Tool t = tools.tool(target);
                MachineAxis.Setting st = t.setting();
                t = switch (p.action()) {
                    case AerialActionPacket.TOOL_INPUT -> t.withInput(PartInput.byOrdinal(p.a()));
                    case AerialActionPacket.TOOL_RPM -> t.withSetting(st.withRpm(p.a()));
                    case AerialActionPacket.TOOL_LATCH -> t.withSetting(st.withLatched(!st.latched()));
                    default -> t.withSetting(st.withInverted(!st.inverted()));
                };
                setup.tools.put(target, t);
                if (p.action() != AerialActionPacket.TOOL_RPM) tools.resetMachine(target);
            }
            case AerialActionPacket.LIGHT_JOB, AerialActionPacket.LIGHT_TOGGLE -> {
                if (!isLink(target)) return false;
                AerialSetup.Light l = AerialLinks.lightFor(setup, target, lampProposals);
                l = p.action() == AerialActionPacket.LIGHT_JOB ? l.withJob(AirLight.byOrdinal(p.a())) : l.withToggle(!l.toggle());
                setup.lights.put(target, l);
            }
            case AerialActionPacket.SET_KEY -> {
                Control c = Control.byOrdinal(p.a());
                if (c == null || !AerialSetup.CONTROLS.contains(c) || (target != Keymap.PLUS && target != Keymap.MINUS)) return false;
                setup.keymap.set(c, (int) target, KeyIds.sane(p.b()));
            }
            case AerialActionPacket.KEY_INVERT -> {
                Control c = Control.byOrdinal(p.a());
                if (c == null || !AerialSetup.CONTROLS.contains(c)) return false;
                setup.keymap.setInverted(c, !setup.keymap.inverted(c));
            }
            case AerialActionPacket.KEYS_RESET -> setup.keymap = AerialSetup.defaultKeys();
            case AerialActionPacket.PARTS_RESET -> {
                setup.resetParts();
                lampProposals = lampProposals();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean isGovernor(long pos) {
        for (AerialProps.Governor g : props.governors()) if (g.pos.asLong() == pos) return true;
        return false;
    }

    private boolean isBalloon(long id) {
        for (AerialLift.Group g : lift.groups()) if (g.id == id) return true;
        return false;
    }

    private boolean isSwivel(long pos) {
        for (Vcc4Scanner.Swivel s : scan.swivels()) if (s.pos().asLong() == pos) return true;
        return false;
    }

    private boolean isMachine(long pos) {
        for (Vcc4Scanner.Machine m : scan.machines()) if (m.governor().asLong() == pos) return true;
        return false;
    }

    private boolean isLink(long pos) {
        for (Vcc4Scanner.Link l : scan.links()) if (l.pos().asLong() == pos) return true;
        return false;
    }

    private void sendViews(ServerLevel server, boolean open) {
        Iterator<UUID> it = watchers.keySet().iterator();
        while (it.hasNext()) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(it.next());
            if (p == null) {
                it.remove();
                continue;
            }
            PacketDistributor.sendToPlayer(p, new AerialViewPacket(worldPosition, open, view(server, p)));
        }
    }

    private AerialView view(ServerLevel server, ServerPlayer viewer) {
        Object craft = SableBridge.subLevelAccessOf(this);
        Reading r = reading;
        AerialPilot.Command c = command;
        String pilotName = "";
        if (pilotId != null) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(pilotId);
            pilotName = p == null ? "?" : p.getGameProfile().getName();
            if (coPilotId != null) {
                ServerPlayer co = server.getServer().getPlayerList().getPlayer(coPilotId);
                pilotName += " + " + (co == null ? "?" : co.getGameProfile().getName());
            }
        }
        boolean you = pilotId != null && (pilotId.equals(viewer.getUUID()) || viewer.getUUID().equals(coPilotId));
        String standDown = c == null && !problems.isEmpty() ? problems.get(0) : "";

        AerialView.Flight flight = AerialView.Flight.NONE;
        Vector3d com = r == null ? new Vector3d() : r.comLocal;
        Vector3d nose = r == null ? noseBody() : r.noseBody;
        Vector3d right = new Vector3d(-nose.z, 0.0, nose.x);
        if (r != null) {
            AerialPilot.Sense s = r.sense;
            double keel = s.keelHeight();
            double ground = Double.isFinite(keel) && Double.isFinite(r.bottom) ? r.bottom - keel : Double.NaN;
            double windSpeed = r.wind == null ? Double.NaN : Math.hypot(r.wind.x, r.wind.z);
            double windFrom = r.wind == null ? 0.0 : HeadingLaw.bearing(-r.wind.x, -r.wind.z);
            double pitch = Math.toDegrees(Math.asin(clamp(r.noseWorld.y, -1.0, 1.0)));
            double roll = -Math.toDegrees(Math.asin(clamp(r.rightWorld.y, -1.0, 1.0)));
            flight = new AerialView.Flight(f(r.comWorld.y), f(r.velocity.y), f(c == null ? Double.NaN : c.targetAltitude()),
                    f(c == null ? Double.NaN : c.ceiling()), f(pilot.balloonCeiling(s)), f(setup.altitudeCap), f(ground),
                    f(keel), f(server.getMaxBuildHeight()), f(s.airspeed()), f(s.airForward()), f(s.airSide()),
                    f(Math.hypot(s.vx(), s.vz())), f(s.heading()), f(c == null ? Double.NaN : c.targetHeading()),
                    f(s.yawRate()), f(pitch), f(roll), f(pilot.telegraph().position()), f(c == null ? 0.0 : c.engine()),
                    c != null && c.braking(), f(c == null ? Double.NaN : c.targetSpeed()), f(pilot.topSpeed(s)),
                    pilot.telegraph().rings(), f(c == null ? Double.NaN : c.liftUse()), c != null && c.liftShort(),
                    f(setup.trim), f(windSpeed), f(windFrom), f(r.pressure), f(thrust.maxForward()), f(thrust.maxReverse()),
                    f(thrust.maxYaw()), c != null && c.weighing());
        }

        AerialView.Side side = AerialView.Side.NONE;
        AerialSnapshot.Silhouette sil = silhouette;
        if (!sil.isEmpty()) {
            byte[] bits = new byte[(sil.cols() * sil.sideRows() + 7) >> 3];
            for (int row = 0; row < sil.sideRows(); row++) {
                for (int col = 0; col < sil.cols(); col++) {
                    if (!sil.sideAt(col, row)) continue;
                    int i = row * sil.cols() + col;
                    bits[i >> 3] |= (byte) (1 << (i & 7));
                }
            }
            byte[] topBits = new byte[(sil.cols() * sil.topRows() + 7) >> 3];
            for (int row = 0; row < sil.topRows(); row++) {
                for (int col = 0; col < sil.cols(); col++) {
                    if (!sil.topAt(col, row)) continue;
                    int i = row * sil.cols() + col;
                    topBits[i >> 3] |= (byte) (1 << (i & 7));
                }
            }
            side = new AerialView.Side((float) sil.cell(), sil.cols(), sil.sideRows(), (float) sil.originF(),
                    (float) sil.originU(), bits, sil.topRows(), (float) sil.originR(), topBits, envSide, envTop);
        }

        double weight = r == null ? 0.0 : r.mass * r.sense.g();
        List<AerialView.Balloon> balloons = new ArrayList<>();
        for (AerialLift.Group g : lift.groups()) {
            Vector3d at = g.centre == null ? null : new Vector3d(g.centre).sub(com);
            balloons.add(new AerialView.Balloon(g.id, g.kind, f(g.gas), f(g.target), f(g.capacity), f(g.maxGas),
                    g.heaters.size(), g.dither.lit(), f(g.pressure), g.driven, at == null ? 0f : f(at.dot(nose)),
                    at == null ? 0f : f(at.y), weight > 0.0 ? f(g.force(r.sense.g()) / weight) : 0f));
        }

        List<AerialView.Engine> engines = new ArrayList<>();
        for (AerialProps.Governor g : props.governors()) {
            Vector3d mean = new Vector3d();
            for (Vcc4Scanner.Prop prop : g.props) mean.add(prop.centre());
            if (!g.props.isEmpty()) mean.div(g.props.size());
            mean.sub(com);
            V3 d = g.direction;
            int where = direction(new Vector3d(d.x(), d.y(), d.z()), nose, right);
            int state = engineState(g);
            double range = g.push >= 0.0 ? g.hi : -g.lo;
            float push = range > 1.0e-9 ? f(clamp(g.push / range, -1.0, 1.0)) : 0f;
            engines.add(new AerialView.Engine(g.pos.asLong(), g.job.ordinal(), g.reverse, g.limit, g.commanded, push, f(g.push),
                    g.props.size(), f(mean.dot(nose)), f(mean.y), f(mean.dot(right)), where, state, props.known(g)));
        }
        for (AerialJets.Jet j : jets.jets()) {
            Vector3d at = new Vector3d(j.prop.centre()).sub(com);
            Vector3d dir = new Vector3d(j.direction.x(), j.direction.y(), j.direction.z());
            int state = jetState(j);
            int kind = switch (j.kind()) {
                case VECTOR -> AerialView.KIND_VECTOR;
                case BOOSTER -> AerialView.KIND_BOOSTER;
                default -> AerialView.KIND_THRUSTER;
            };
            double force = j.reading == null ? 0.0 : Math.max(0.0, j.reading.force());
            float push = j.ceiling > 1.0e-9 ? f(clamp(force / j.ceiling, 0.0, 1.0)) : 0f;
            engines.add(new AerialView.Engine(j.pos.asLong(), j.job.ordinal(), false, j.limit, (int) Math.round(j.command * 100.0), push,
                    f(force), 1, f(at.dot(nose)), f(at.y), f(at.dot(right)), direction(dir, nose, right), state,
                    j.reading != null && j.reading.lit(), kind, j.supply().ordinal(), f(j.aim)));
        }

        List<AerialView.Surface> surfaces = new ArrayList<>();
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            long key = s.pos().asLong();
            AerialSetup.Surface sf = tools.surface(key);
            Vector3d hinge = new Vector3d(s.hingeAxis());
            if (hinge.lengthSquared() > 1.0e-9) hinge.normalize();
            int hingeKind = Math.abs(hinge.y) > AerialParts.ALONG ? 0 : Math.abs(hinge.dot(right)) > AerialParts.ALONG ? 1 : 2;
            Vector3d at = new Vector3d(Vcc4Scanner.centre(s.pos())).sub(com);
            surfaces.add(new AerialView.Surface(key, sf.role().ordinal(), sf.degrees(), sf.invert(), sf.input().ordinal(),
                    sf.latched(), f(tools.angle(key)), s.assembled(), s.locked(), f(at.dot(nose)), hingeKind, f(at.y)));
        }

        List<AerialView.Tool> toolRows = new ArrayList<>();
        for (Vcc4Scanner.Machine m : scan.machines()) {
            long key = m.governor().asLong();
            AerialSetup.Tool t = tools.tool(key);
            toolRows.add(new AerialView.Tool(key, toolName(m), t.input().ordinal(), t.setting().rpm(), t.setting().latched(),
                    t.setting().inverted(), f(tools.machineOutput(key)), tools.foreign(key), tools.gunnery(key), tools.bridgeHolds(key)));
        }

        List<AerialView.Light> lights = new ArrayList<>();
        for (Vcc4Scanner.Link l : scan.links()) {
            long key = l.pos().asLong();
            AerialSetup.Light light = AerialLinks.lightFor(setup, key, lampProposals);
            Vector3d at = new Vector3d(Vcc4Scanner.centre(l.pos())).sub(com);
            lights.add(new AerialView.Light(key, AerialLinks.lampKind(l), l.first(), l.second(), light.job().ordinal(),
                    light.toggle(), links.strength(key), f(at.dot(nose)), f(at.y), f(at.dot(right))));
        }

        int known = 0;
        int propsTotal = 0;
        for (AerialProps.Governor g : props.governors()) {
            for (Vcc4Scanner.Prop prop : g.props) {
                propsTotal++;
                PropLearner l = props.learnedAt(prop.pos());
                if (l != null && l.signSeen) known++;
            }
        }
        double mass = r == null ? Double.NaN : r.mass;
        AerialView.Learned learned = new AerialView.Learned(
                f(pilot.vertical().dragPerMass(Double.isFinite(mass) ? Math.max(1.0, mass) : 1.0)),
                f(pilot.vertical().estimate(VerticalObserver.WING)), f(pilot.vertical().estimate(VerticalObserver.THRUST)),
                f(pilot.vertical().estimate(VerticalObserver.CONSTANT)), f(pilot.heading().damping()), known, propsTotal);

        List<String> notes = new ArrayList<>(problems);
        if (!props.loose().isEmpty()) notes.add(looseNote());
        int offBody = props.directJets() - jets.jets().size();
        if (offBody > 0) notes.add(offBody + " thruster(s) on a body a bearing turns: the Aerial flies only the ship's own.");

        AerialView.Nav goal = new AerialView.Nav(AerialView.NAV_NONE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                false, nav.problem());
        if (nav.found()) {
            double dx = r == null ? Double.NaN : nav.x() - r.comWorld.x;
            double dz = r == null ? Double.NaN : nav.z() - r.comWorld.z;
            goal = new AerialView.Nav(nav.source().ordinal(), f(nav.x()), f(nav.y()), f(nav.z()), f(Math.hypot(dx, dz)),
                    f(r == null ? Double.NaN : HeadingLaw.bearing(dx, dz)), nav.tableElsewhere(), "");
        }

        return new AerialView(nameOf(craft), pilotName, you, craft != null, standDown, pilot.mode().ordinal(),
                setup.leave.ordinal(), setup.feel.ordinal(), flight, side, balloons, engines, surfaces, toolRows, lights,
                setup.keymap.toInts(), you ? keysHeld(server.getGameTime()).toArray() : new int[0], noseTurns(), !setup.noseSet, setup.bell, blocks, f(mass),
                f(r == null ? Double.NaN : r.sense.g()), learned, notes, bridge != null, goal, staff);
    }

    private static float f(double v) {
        return (float) v;
    }

    private static int direction(Vector3d dir, Vector3d nose, Vector3d right) {
        double along = dir.dot(nose);
        double up = dir.y;
        double across = dir.dot(right);
        if (Math.abs(along) >= Math.abs(up) && Math.abs(along) >= Math.abs(across)) {
            return along >= 0.0 ? AerialView.DIR_AHEAD : AerialView.DIR_ASTERN;
        }
        if (Math.abs(up) >= Math.abs(across)) return up >= 0.0 ? AerialView.DIR_UP : AerialView.DIR_DOWN;
        return across >= 0.0 ? AerialView.DIR_RIGHT : AerialView.DIR_LEFT;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private void sendHud(ServerLevel server, @Nullable Object craft, @Nullable Reading r) {
        if (craft == null || r == null) return;
        AerialHudPacket packet = null;
        double[] b = r.bounds;
        for (ServerPlayer p : server.players()) {
            if (!AviatorHelmets.hudShowing(p)) continue;
            boolean aboard = p.getUUID().equals(pilotId) || p.getUUID().equals(coPilotId) || (b != null
                    && p.getX() >= b[0] - 2.0 && p.getX() <= b[3] + 2.0
                    && p.getY() >= b[1] - 2.0 && p.getY() <= b[4] + 3.0
                    && p.getZ() >= b[2] - 2.0 && p.getZ() <= b[5] + 2.0);
            if (!aboard) continue;
            if (packet == null) packet = hudPacket(r);
            PacketDistributor.sendToPlayer(p, packet);
        }
    }

    private FlightIntent.Line banner(Reading r) {
        AerialPilot.Command c = command;
        AerialPilot.Sense s = r.sense;
        AerialPilot.Mode mode = c == null ? AerialPilot.Mode.OFF : c.mode();
        boolean staffed = pilot.staffHeld() && mode.flying();
        double groundSpeed = Math.hypot(s.vx(), s.vz());
        boolean goTo = nav.found() && (mode == AerialPilot.Mode.GO_TO || pilot.leaveMode() == LeaveMode.GO_TO);
        double targetDist = Double.NaN;
        double targetBearing = Double.NaN;
        String name = nav.source() == NavDestination.Source.LODESTONE ? "LODESTONE" : "TABLE TARGET";
        if (goTo) {
            double dx = nav.x() - s.x();
            double dz = nav.z() - s.z();
            targetDist = Math.hypot(dx, dz);
            targetBearing = HeadingLaw.bearing(dx, dz);
        }
        double flying = c != null && !c.handsOff() ? pilot.flownAltitude() : Double.NaN;
        String note = c != null && !c.notes().isEmpty() ? c.notes().get(0) : problems.isEmpty() ? null : problems.get(0);
        return FlightIntent.banner(new FlightIntent.Now(mode, pilot.leaveMode(), staffed, s.y(),
                c == null ? Double.NaN : c.targetAltitude(), flying, s.vy(), s.heading(),
                c == null ? Double.NaN : c.targetHeading(), groundSpeed, targetDist, targetBearing, name, s.keelHeight()),
                note, pilot.guard());
    }

    private AerialHudPacket hudPacket(Reading r) {
        AerialPilot.Command c = command;
        AerialPilot.Sense s = r.sense;
        AerialPilot.Mode mode = c == null ? AerialPilot.Mode.OFF : c.mode();
        boolean staffed = pilot.staffHeld() && mode.flying();
        String[] lines = FlightIntent.modeLines(mode, pilot.leaveMode(), staffed);
        double groundSpeed = Math.hypot(s.vx(), s.vz());
        double track = groundSpeed > 0.5 ? HeadingLaw.bearing(s.vx(), s.vz()) : Double.NaN;
        double windSpeed = r.wind == null ? 0.0 : Math.hypot(r.wind.x, r.wind.z);
        double windFrom = windSpeed > 1.0e-3 ? HeadingLaw.bearing(-r.wind.x, -r.wind.z) : Double.NaN;

        boolean goTo = nav.found() && (mode == AerialPilot.Mode.GO_TO || pilot.leaveMode() == LeaveMode.GO_TO);
        double targetBearing = Double.NaN;
        String name = nav.source() == NavDestination.Source.LODESTONE ? "LODESTONE" : "TABLE TARGET";
        String label = "";
        if (goTo) {
            double dx = nav.x() - s.x();
            double dz = nav.z() - s.z();
            double targetDist = Math.hypot(dx, dz);
            targetBearing = HeadingLaw.bearing(dx, dz);
            double closing = targetDist > 1.0e-6 ? (s.vx() * dx + s.vz() * dz) / targetDist : 0.0;
            label = FlightIntent.targetLabel(name, targetDist, closing);
        }

        TerrainGuard.Result guard = pilot.guard();
        double under = Double.isFinite(s.keelHeight()) && Double.isFinite(r.bottom) ? r.bottom - s.keelHeight() : Double.NaN;
        double ahead = guard.acting() && Double.isFinite(guard.groundY()) ? guard.groundY() : guard.peakY();
        double terrain = !Double.isFinite(ahead) ? under : !Double.isFinite(under) ? ahead : Math.max(under, ahead);
        double flying = c != null && !c.handsOff() ? pilot.flownAltitude() : Double.NaN;

        FlightIntent.Line banner = banner(r);
        double pitch = Math.toDegrees(Math.asin(clamp(r.noseWorld.y, -1.0, 1.0)));
        double roll = -Math.toDegrees(Math.asin(clamp(r.rightWorld.y, -1.0, 1.0)));
        return new AerialHudPacket(lines[0], lines[1], f(s.heading()), f(c == null ? Double.NaN : c.targetHeading()), f(track),
                f(windFrom), f(windSpeed), f(targetBearing), label, f(s.y()), f(flying),
                f(c == null || !Double.isFinite(c.ceiling()) ? Double.NaN : c.ceiling()), f(terrain), f(s.vy()),
                f(s.keelHeight()), f(pilot.telegraph().position()), f(c == null ? 0.0 : c.engine()), f(s.airForward()),
                f(pilot.topSpeed(s)), f(c == null ? Double.NaN : c.liftUse()), f(pitch), f(roll), banner.text(),
                banner.alert());
    }

    private String statusLine(@Nullable Reading r) {
        AerialPilot.Command c = command;
        if (c == null || r == null) return problems.isEmpty() ? "VCC Mk IV Aerial: standing by" : "! " + AerialPilot.headline(problems.get(0));
        if (c.handsOff()) return "Moored: sit to fly";
        StringBuilder s = new StringBuilder();
        s.append(orderWords(pilot.telegraph())).append(' ')
                .append(String.format(java.util.Locale.ROOT, "%.1f b/s", r.sense.airForward()));
        s.append(String.format(java.util.Locale.ROOT, "  ALT %.0f", r.comWorld.y));
        if (Double.isFinite(c.targetAltitude()) && Math.abs(c.targetAltitude() - r.comWorld.y) >= 0.5) {
            s.append(String.format(java.util.Locale.ROOT, " > %.0f", c.targetAltitude()));
        }
        s.append(String.format(java.util.Locale.ROOT, " %+.1f", r.velocity.y));
        s.append(String.format(java.util.Locale.ROOT, "  HDG %03.0f", r.sense.heading()));
        if (Double.isFinite(c.liftUse())) s.append(String.format(java.util.Locale.ROOT, "  LIFT %.0f%%", 100.0 * c.liftUse()));

        String note = !c.notes().isEmpty() ? c.notes().get(0) : problems.isEmpty() ? null : problems.get(0);
        if (note != null) s.append("  ! ").append(AerialPilot.headline(note));
        return s.toString();
    }

    static String orderWords(Telegraph t) {
        Telegraph.Notch n = t.notch();
        if (n == null) return (t.position() > 0.0 ? "AHEAD " : "ASTERN ") + Math.round(Math.abs(t.position()) * 100.0) + "%";
        return switch (n) {
            case FULL_ASTERN -> "FULL ASTERN";
            case HALF_ASTERN -> "HALF ASTERN";
            case SLOW_ASTERN -> "SLOW ASTERN";
            case STOP -> "STOP";
            case SLOW -> "SLOW AHEAD";
            case HALF -> "HALF AHEAD";
            case FULL -> "FULL AHEAD";
        };
    }

    private List<String> reportLines() {
        List<String> out = new ArrayList<>();
        AerialPilot.Command c = command;
        out.add("Mode: " + (c == null ? "stood down" : c.mode().label) + ", leave: " + pilot.leaveMode().label
                + ", feel: " + pilot.feel().name().toLowerCase(java.util.Locale.ROOT));
        if (nav.found() && (pilot.mode() == AerialPilot.Mode.GO_TO || pilot.leaveMode() == LeaveMode.GO_TO)) {
            out.add(String.format(java.util.Locale.ROOT, "Go to: %s at %.0f, %.0f, %.0f",
                    nav.source() == NavDestination.Source.LODESTONE ? "the compass's lodestone" : "the table's target",
                    nav.x(), nav.y(), nav.z()));
        }
        if (snapshot != null) {
            AerialSnapshot s = snapshot;
            out.add(String.format(java.util.Locale.ROOT, "Altitude %.1f, climb %+.1f b/s, speed %.1f b/s",
                    s.body().position().y(), s.body().velocity().y(), s.air().velocity().horizontalLength()));
            if (c != null && !c.handsOff()) {
                String speed = Double.isFinite(c.targetSpeed())
                        ? String.format(java.util.Locale.ROOT, "speed %.1f b/s", c.targetSpeed())
                        : orderWords(pilot.telegraph()).toLowerCase(java.util.Locale.ROOT) + String.format(java.util.Locale.ROOT, ", engines %.0f%%", 100.0 * c.engine());
                out.add(String.format(java.util.Locale.ROOT, "Held: altitude %.0f, %s%s", c.targetAltitude(), speed,
                        Double.isFinite(c.targetHeading()) ? String.format(java.util.Locale.ROOT, ", heading %03.0f", c.targetHeading()) : ""));
            }
            if (c != null && Double.isFinite(c.ceiling())) out.add(String.format(java.util.Locale.ROOT, "Ceiling y %.0f", c.ceiling()));
            else if (c != null && c.ceiling() == Double.NEGATIVE_INFINITY) out.add("! Too heavy for its balloons");
        }
        int groups = lift.groups().size();
        out.add("Balloons " + groups + ", burners and vents " + lift.heaters().size() + " (lit " + lift.lit() + ")"
                + (lift.pending() > 0 ? ", " + lift.pending() + " finding their balloon" : ""));
        out.add("Engines: " + props.governors().size() + " governor(s), " + propCount() + " prop(s)"
                + (jets.jets().isEmpty() ? "" : ", " + jets.jets().size() + " thruster(s)"));
        double load = pilot.vertical().load();
        if (load > 0.0 && snapshot != null && snapshot.mass().mass() > 0.0) {
            double g = reading != null ? reading.sense.g() : 11.0;
            out.add(String.format(java.util.Locale.ROOT, "Load aboard: %.0f kpg (weighed; not in the ship's own blocks)", load / g));
        }
        if (!guns.isEmpty()) out.add("Guns: " + guns.size() + " cannon mount(s), aimed by the Battle Bridge");
        out.add(String.format(java.util.Locale.ROOT, "Learned: drag %.2f, wings %.1f, lean %.2f, hidden lift %.0f",
                pilot.vertical().dragPerMass(snapshot == null ? 1.0 : Math.max(1.0, snapshot.mass().mass())),
                pilot.vertical().estimate(VerticalObserver.WING),
                pilot.vertical().estimate(VerticalObserver.THRUST),
                pilot.vertical().estimate(VerticalObserver.CONSTANT)));

        if (c != null && !c.handsOff()) out.add(cost.summary());
        if (!props.loose().isEmpty()) out.add("! " + looseNote());
        for (String p : problems) out.add("! " + p);
        return out;
    }

    private String looseNote() {
        int n = props.loose().size();
        return n == 1 ? "A propeller has no RPM Governor in its shaft line, so the Aerial cannot fly it."
                : n + " propellers have no RPM Governor in their shaft line, so the Aerial cannot fly them.";
    }

    private int propCount() {
        int n = 0;
        for (AerialProps.Governor g : props.governors()) n += g.props.size();
        return n;
    }

    private void publish(ServerLevel server, long now) {
        lastPublish = now;
        String who = "";
        if (pilotId != null) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(pilotId);
            who = p == null ? "?" : p.getGameProfile().getName();
            if (coPilotId != null) {
                ServerPlayer co = server.getServer().getPlayerList().getPlayer(coPilotId);
                who += " + " + (co == null ? "?" : co.getGameProfile().getName());
            }
        }
        List<String> lines = List.copyOf(reportLines());
        if (lines.equals(syncLines) && who.equals(syncPilot)) return;
        syncLines = lines;
        syncPilot = who;
        setChanged();
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static String toolName(Vcc4Scanner.Machine m) {
        for (String id : m.drives()) if (id != null && id.endsWith("cannon_mount")) return id;
        return m.drives().isEmpty() ? "" : m.drives().get(0);
    }

    List<Component> report() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("block.create_stats.vcc_mk4_aerial").withStyle(ChatFormatting.WHITE));
        for (String line : reportLines()) {
            out.add(Component.literal(line).withStyle(line.startsWith("!") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return out;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_stats.vcc_mk4_aerial"))
                .withStyle(ChatFormatting.WHITE));
        String who = syncPilot.isEmpty() ? "nobody. Sit in a seat within 2 blocks of the Aerial to fly it" : syncPilot;
        tooltip.add(Component.literal("    Pilot: " + who).withStyle(syncPilot.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        for (String line : syncLines) {
            for (String piece : FlightIntent.wrap(line, TOOLTIP_WIDTH)) {
                tooltip.add(Component.literal("    " + piece).withStyle(line.startsWith("!") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
        }
        return true;
    }

    private void cockpitTick(ServerLevel server, @Nullable Object craft, @Nullable Reading r) {
        AerialCockpitState now = captureCockpit(craft, r);
        if (now.equals(cockpitSent)) return;
        cockpitSent = now;
        cockpit = now;
        cockpitOnly = true;
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private AerialCockpitState captureCockpit(@Nullable Object craft, @Nullable Reading r) {
        AerialPilot.Command c = command;
        AerialPilot.Mode mode = c != null ? c.mode() : pilot.mode();
        Direction f = facing();
        boolean piloted = pilotId != null || coPilotId != null;
        boolean caution = r != null ? banner(r).alert() : !problems.isEmpty();
        if (r == null) {
            double alt = craft == null ? worldPosition.getY() + 0.5 : Double.NaN;
            return AerialCockpitState.of(piloted, craft != null, caution, mode, pilot.leaveMode(),
                    pilot.telegraph().position(), 0.0, 0.0, 0.0, setup.trim, alt, Double.NaN, 0.0, 0.0,
                    AerialCockpitMotion.facingHeading(f.getStepX(), f.getStepZ()), Double.NaN, 0.0, 0.0, 0.0,
                    pilot.feel().turn);
        }
        AerialPilot.Sense s = r.sense;
        Quaterniond q = r.orientation;
        double[] att = AerialCockpitMotion.attitude(q.x, q.y, q.z, q.w, f.getStepX(), f.getStepZ());
        boolean flying = c != null && !c.handsOff();
        double flown = pilot.flownAltitude();
        double climb = 0.0;

        if (flying && Double.isFinite(flown) && !pilot.staffHeld()) {
            AltitudeLaw.Limits lim = pilot.feel().altitude();
            if (lim.climb() > 0.0) {
                climb = AltitudeLaw.referenceSpeed(AltitudeLaw.lookaheadError(s.y(), s.vy(), flown), lim) / lim.climb();
            }
        }
        return AerialCockpitState.of(piloted, true, caution, mode, pilot.leaveMode(),
                c != null ? c.order() : pilot.telegraph().position(), flying ? c.engine() : 0.0, flying ? c.turn() : 0.0,
                climb, setup.trim, s.y(), flying ? c.targetAltitude() : Double.NaN, s.vy(), s.airForward(), att[0],
                flying ? c.targetHeading() : Double.NaN, att[1], att[2], s.yawRate(), pilot.feel().turn);
    }

    @Override
    public void setChanged() {
        cockpitWhole = true;
        super.setChanged();
    }

    private CompoundTag cockpitTag() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(COCKPIT, cockpit.encode());
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag.size() == 1 && tag.contains(COCKPIT, Tag.TAG_BYTE_ARRAY)) {
            cockpit = AerialCockpitState.decode(tag.getByteArray(COCKPIT));
            return;
        }
        super.onDataPacket(net, pkt, registries);
    }

    public void clientTick() {
        if (level == null) return;
        Direction f = facing();
        cockpitPose.restingHeading(AerialCockpitMotion.facingHeading(f.getStepX(), f.getStepZ()));
        cockpitPose.tick(cockpit);
        if (--cockpitCraftAge <= 0) {
            cockpitCraftAge = 20;
            cockpitCraft = SableBridge.subLevelAccessOf(this);
        }
    }

    public AerialCockpitMotion.Frame cockpitFrame(float partial) {
        return cockpitPose.frame(partial);
    }

    @Nullable
    public Object cockpitCraft() {
        return cockpitCraft;
    }

    public AerialCockpitState cockpitState() {
        return cockpit;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloading = true;
    }

    @Override
    public void setRemoved() {
        try {
            if (level instanceof ServerLevel server) {
                if (pilotId != null) leaveHelm(server.getServer().getPlayerList().getPlayer(pilotId));
                if (!unloading) {
                    handsOff(server);

                    jets.release(server, controllerId);
                    tools.release(server, controllerId);
                }
                links.removeAll(server);
            }
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            CreateStats.LOGGER.error("[VCC Mk IV] Aerial at {}: hand-back on removal failed", worldPosition, e);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Version", NBT_VERSION);
        tag.putUUID("ControllerId", controllerId);
        tag.put("Pilot", AerialNbt.pilot(pilot.state()));
        tag.put("Setup", AerialNbt.setup(setup));
        tag.put("Angles", AerialNbt.angles(tools.angles()));
        tag.put("Props", AerialNbt.learned(props.learned()));
        tag.putByteArray("Recorder", recorder.save());
        tag.put("Route", AerialNbt.route(route));

        if (modeSince != AerialSnapshot.NO_TICK) tag.putLong("ModeSince", modeSince);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("ControllerId")) controllerId = tag.getUUID("ControllerId");
        boolean hasSetup = tag.contains("Setup", Tag.TAG_COMPOUND);
        if (hasSetup) AerialNbt.readSetup(tag.getCompound("Setup"), setup);

        else if (tag.contains("Keymap")) setup.keymap = Keymap.fromInts(tag.getIntArray("Keymap"));
        if (tag.contains("Pilot")) {
            pilot.restore(AerialNbt.readPilot(tag.getCompound("Pilot"), tag.getInt("Version") >= 3), !hasSetup);
        }
        if (tag.contains("Angles")) tools.restoreAngles(AerialNbt.readAngles(tag.getCompound("Angles")));
        if (tag.contains("Props")) {
            props.learned().clear();
            props.learned().putAll(AerialNbt.readLearned(tag.getCompound("Props")));
        }
        if (tag.contains("Recorder")) recorder = AerialRecorder.load(tag.getByteArray("Recorder"));
        if (tag.contains("Route")) route = AerialNbt.readRoute(tag.getCompound("Route"));
        if (tag.contains("ModeSince")) modeSince = tag.getLong("ModeSince");
        if (tag.contains("SyncLines")) {
            syncPilot = tag.getString("SyncPilot");
            List<String> lines = new ArrayList<>();
            for (Tag t : tag.getList("SyncLines", Tag.TAG_STRING)) lines.add(t.getAsString());
            syncLines = List.copyOf(lines);
        }
        if (tag.contains(COCKPIT, Tag.TAG_BYTE_ARRAY)) cockpit = AerialCockpitState.decode(tag.getByteArray(COCKPIT));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("SyncPilot", syncPilot);
        ListTag lines = new ListTag();
        for (String line : syncLines) lines.add(StringTag.valueOf(line));
        tag.put("SyncLines", lines);

        tag.putByteArray(COCKPIT, cockpit.encode());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        if (cockpitOnly && !cockpitWhole) {
            cockpitOnly = false;
            return ClientboundBlockEntityDataPacket.create(this, (be, registries) -> cockpitTag());
        }
        cockpitOnly = false;
        cockpitWhole = false;
        return ClientboundBlockEntityDataPacket.create(this);
    }

    static double quadratic(double[] m, Vector3d u) {
        double x = m[0] * u.x + m[3] * u.y + m[6] * u.z;
        double y = m[1] * u.x + m[4] * u.y + m[7] * u.z;
        double z = m[2] * u.x + m[5] * u.y + m[8] * u.z;
        return u.x * x + u.y * y + u.z * z;
    }

    private static Vector3d orZero(@Nullable Vector3d v) {
        return v == null || !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z) ? new Vector3d() : v;
    }

    private static V3 v3(Vector3d v) {
        return new V3(v.x, v.y, v.z);
    }

    private static com.createstats.aerial.V3 av(Vector3d v) {
        return new com.createstats.aerial.V3(v.x, v.y, v.z);
    }
}
