package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.advancement.ModAdvancements;
import com.createstats.driverhelmet.DriverTelemetry;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import com.createstats.ground.GroundCommand;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundTelemetrySource;
import com.createstats.vccmk4.core.AuxAxis;
import com.createstats.vccmk4.core.AxleLayout;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.GroundSteer;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.LampProposal;
import com.createstats.vccmk4.core.Lever;
import com.createstats.vccmk4.core.LinkLogic;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SeatRule;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.ThrustCommand;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VectorGimbal;
import com.createstats.vccmk4.core.VehicleSetup;
import com.createstats.vccmk4.network.Vcc4ActionPacket;
import com.createstats.vccmk4.network.Vcc4HelmPacket;
import com.createstats.vccmk4.network.Vcc4Snapshot;
import com.createstats.vccmk4.network.RouteSnapshot;
import com.createstats.vccmk4.network.Vcc4SnapshotPacket;
import com.createstats.vccmk4.route.RouteHost;
import com.createstats.vccmk4.route.Vcc4Route;
import com.createstats.vehiclelight.HailSequence;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// auto proposes, the player decides
public class Vcc4BE extends BlockEntity implements IHaveGoggleInformation, HelmConsole, GroundTelemetrySource, RouteHost,
        com.createstats.util.CopyableSettings {
    static final int MAX_PROP_RPM = 256;

    static final int INPUT_TIMEOUT_TICKS = 40;

    static final int CALIBRATION_TICKS = 20;
    static final int CALIBRATION_RPM = 3;
    static final int SCAN_INTERVAL_DRIVING = 60;
    static final int SCAN_INTERVAL_IDLE = 100;
    static final int SNAPSHOT_INTERVAL = 4;

    static final int WATCH_TIMEOUT_TICKS = 120;

    static final int FULL_BRAKE = 15;

    static final int STARVED_TICKS = 20;

    static final String GROUND_RECORDER = "GroundRecorder";

    static final String ROUTE = "Route";

    private static final Control[] ROUTE_AUX = {Control.AUX_A, Control.AUX_B, Control.AUX_C, Control.AUX_D};

    // one console per player, weak and checked on read
    private static final Map<UUID, WeakReference<Vcc4BE>> DRIVING = new ConcurrentHashMap<>();

    private static final Map<UUID, WeakReference<Vcc4BE>> DRIVEN_CRAFTS = new ConcurrentHashMap<>();

    private UUID controllerId = UUID.randomUUID();
    private final VehicleSetup setup = new VehicleSetup();

    private final Map<Long, Double> wheelRatio = new HashMap<>();

    private final Map<Long, Integer> propSign = new HashMap<>();

    private final Map<Long, Double> auxAngle = new HashMap<>();

    private final Map<Long, Double> propGain = new HashMap<>();

    private Vcc4Scanner.Scan scan = Vcc4Scanner.Scan.EMPTY;

    private List<AxleLayout.WheelSpec> wheelSpecs = List.of();

    private int[] specOf = new int[0];
    private List<AxleLayout.Axle> axles = List.of();

    private Frame[] frames = {Frame.IDENTITY};

    private Map<Long, PartInput> inputs = Map.of();

    @Nullable private BlockPos joint;
    private boolean articulated;

    private Vector3d com = new Vector3d();
    private boolean comMeasured;
    private int scanCooldown = 5;
    private int overridesDropped;
    @Nullable private UUID driver;
    private HeldKeys held = HeldKeys.NONE;
    private HeldKeys heldThisTick = HeldKeys.NONE;
    private long lastInputTick = Long.MIN_VALUE;

    @Nullable private UUID coDriver;
    private HeldKeys coHeld = HeldKeys.NONE;
    private long coLastInputTick = Long.MIN_VALUE;
    private int[] helmWatch = new int[0];
    private final DriveModel drive = new DriveModel();
    private DriveModel.Output lastOutput = DriveModel.Output.idle(DriveModel.Options.DEFAULT.lockDeg());
    private double forwardSpeed;
    private int calibrationTicks;
    private boolean parked;
    private final double[] controlValue = new double[Control.values().length];
    private final Map<Long, AuxAxis> auxAxes = new HashMap<>();
    private final Map<Long, MachineAxis> machineAxes = new HashMap<>();
    private final Map<Long, Double> machineOutput = new HashMap<>();
    private final Lever liftLever = new Lever();
    private final Vcc4Links links = new Vcc4Links();
    private final Map<Long, Double> wheelAngle = new HashMap<>();
    private final Map<Long, Double> jetCommand = new HashMap<>();

    private final Map<Long, Vcc4Jets.Reading> jetReadings = new HashMap<>();
    private long jetReadingsTick = Long.MIN_VALUE;

    private final Set<Long> foreignJets = new HashSet<>();
    private final Set<BlockPos> ownedGovernors = new LinkedHashSet<>();
    private final Set<BlockPos> foreignGovernors = new HashSet<>();

    private final Map<BlockPos, Integer> starved = new HashMap<>();
    private final Map<UUID, Long> watchers = new HashMap<>();
    private final List<String> runtimeProblems = new ArrayList<>();
    private String status = "";

    private final Vcc4Train train = new Vcc4Train(this);

    private final Vcc4Telemetry telemetry = new Vcc4Telemetry(this);

    private long helmTick = Long.MIN_VALUE;
    private boolean helmStill;

    private boolean towed;

    private final Vcc4Route route = new Vcc4Route(this);

    private boolean routeDriving;

    private boolean unloading;

    private List<BlockEntity> cargoBlocks = List.of();
    private boolean cargoFresh;

    private String syncDriver = "";
    private List<String> syncLines = List.of();

    public Vcc4BE(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private record Frame(Quaterniond q, Vector3d from, Vector3d to, boolean placed) {
        static final Frame IDENTITY = new Frame(new Quaterniond(), new Vector3d(), new Vector3d(), true);

        Vector3d point(Vector3d p) {
            return q.transform(new Vector3d(p).sub(from)).add(to);
        }

        Vector3d dir(Vector3d d) {
            return q.transform(new Vector3d(d));
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();

        if (route.awaitingVehicle()) scanCooldown = Math.min(scanCooldown, 5);
        if (--scanCooldown <= 0) {
            rescan(server);
            scanCooldown = driver != null ? SCAN_INTERVAL_DRIVING : SCAN_INTERVAL_IDLE;
        }
        updateFrames();
        train.updateRates();
        updateDriver(server, now);

        boolean driverSilent = now - lastInputTick > INPUT_TIMEOUT_TICKS;
        boolean coSilent = coDriver == null || now - coLastInputTick > INPUT_TIMEOUT_TICKS;

        boolean silentKeys = driverSilent && coSilent;
        HeldKeys keysNow = HeldKeys.union(driverSilent ? HeldKeys.NONE : held, coSilent ? HeldKeys.NONE : coHeld);
        route.resume(server);
        route.tick(server, now, driver == null ? HeldKeys.NONE : keysNow, setup.keymap);
        setRouteDriving(route.drives());
        if (driver != null) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(driver);
            heldThisTick = keysNow;

            if (silentKeys && !routeDriving) drive.closeThrottle();
            driveTick(server, now, heldThisTick);
            if (calibrationTicks > 0) calibrationTicks--;
            ServerPlayer co = coDriver == null ? null : server.getServer().getPlayerList().getPlayer(coDriver);
            if (co != null) {
                syncHelm(co, false);
                if (now % 5 == 0) co.displayClientMessage(Component.literal(status), true);
            }
            if (p != null) {
                syncHelm(p, true);
                if (now % 5 == 0) p.displayClientMessage(Component.literal(status), true);
                // imported, the `com` field would shadow the package name

                DriverTelemetry.tick(p, this, now,
                        () -> scan.wheels().stream().map(Vcc4Scanner.Wheel::pos).toList(), scan::governorFor,
                        nose(), forwardSpeed);
            }
        } else if (routeDriving) {
            heldThisTick = HeldKeys.NONE;
            driveTick(server, now, HeldKeys.NONE);
            if (calibrationTicks > 0) calibrationTicks--;
        } else {
            heldThisTick = HeldKeys.NONE;
            idleTick(now);
        }

        if (route.assigned()) {
            boolean drove = routeDriving || driver != null;
            route.afterDrive(server, now, routeDriving ? lastOutput : null, drove ? forwardSpeed : measureForwardSpeed(),
                    drove ? traceWheelWant : Double.NaN, drove ? traceWheelSent : Double.NaN);
        }
        links.tick(server, setup, signals(now));

        telemetry.tick(server, now);
        if (!watchers.isEmpty() && now % SNAPSHOT_INTERVAL == 0) {
            watchers.values().removeIf(last -> now - last > WATCH_TIMEOUT_TICKS);
            sendSnapshots(server, false);
        }
    }

    private void rescan(ServerLevel server) {
        scan = Vcc4Scanner.scan(server, this);
        List<AxleLayout.WheelSpec> specs = new ArrayList<>();
        specOf = new int[scan.wheels().size()];
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            if (w.body() != 0) {
                specOf[i] = -1;
                continue;
            }
            specOf[i] = specs.size();
            specs.add(new AxleLayout.WheelSpec(lateral(w.contact()), longitudinal(w.contact())));
        }
        wheelSpecs = specs;
        axles = AxleLayout.cluster(specs);
        updateFrames();
        findCentreOfMass();
        findJoint();
        setup.proposePreset(proposal());
        train.rescan(server, articulatedHalf(), server.getGameTime());
        setup.couplers.clear();
        scan.links().forEach(l -> {
            if (l.coupler()) setup.couplers.add(l.pos().asLong());
        });
        setup.proposeLamps(lampProposals());
        Set<Long> present = new HashSet<>();
        scan.props().forEach(p -> present.add(p.pos().asLong()));
        scan.swivels().forEach(s -> present.add(s.pos().asLong()));
        scan.usedGovernors().forEach(g -> present.add(g.asLong()));
        scan.machines().forEach(m -> present.add(m.governor().asLong()));
        scan.links().forEach(l -> present.add(l.pos().asLong()));
        if (scan.onCraft()) {
            int dropped = setup.forgetMissing(present);
            if (dropped > 0) {
                overridesDropped += dropped;
                setChanged();
            }
            auxAxes.keySet().removeIf(k -> !present.contains(k));
            machineAxes.keySet().removeIf(k -> !present.contains(k));
        }
        refreshInputs();
        wheelAngle.keySet().retainAll(scan.wheels().stream().map(w -> w.pos().asLong()).toList());
        jetCommand.keySet().retainAll(present);
        machineOutput.keySet().retainAll(present);
        links.sync(server, scan.links(), setup);
        cargoFresh = false;
        publish();
    }

    private List<BlockEntity> cargoOf(ServerLevel server) {
        if (!scan.onCraft()) return List.of();
        Set<BlockPos> parts = new HashSet<>();
        scan.wheels().forEach(w -> parts.add(w.pos()));
        scan.props().forEach(p -> parts.add(p.pos()));
        scan.swivels().forEach(s -> parts.add(s.pos()));
        scan.links().forEach(l -> parts.add(l.pos()));
        parts.addAll(scan.usedGovernors());
        scan.machines().forEach(m -> parts.add(m.governor()));
        List<BlockEntity> out = new ArrayList<>();
        for (BlockEntity be : Vcc4Scanner.onBodies(server, scan, this)) {
            if (!parts.contains(be.getBlockPos()) && !(be instanceof Vcc4BE)) out.add(be);
        }
        return out;
    }

    private void refreshInputs() {
        List<VehicleSetup.InputPart> parts = new ArrayList<>();
        for (Vcc4Scanner.Swivel s : scan.swivels()) parts.add(new VehicleSetup.InputPart(s.pos().asLong(), false, swivelProposal(s)));
        for (Vcc4Scanner.Machine m : scan.machines()) parts.add(new VehicleSetup.InputPart(m.governor().asLong(), true, null));
        inputs = setup.inputs(parts);
        setup.proposeCouplers(inputs);
    }

    @Nullable
    private PartInput swivelProposal(Vcc4Scanner.Swivel s) {
        if (joint != null && joint.equals(s.pos())) return PartInput.STEER;
        int other = otherBody(s);
        if (other > 0 && !s.locked() && hasWheels(other)) return PartInput.OFF;
        for (Vcc4Scanner.Machine m : scan.machines()) {
            if (m.swivels().contains(s.pos())) return PartInput.OFF;
        }
        return null;
    }

    private int otherBody(Vcc4Scanner.Swivel s) {
        if (s.body() == 0) return s.carries();
        return s.carries() == 0 ? s.body() : -1;
    }

    private boolean hasWheels(int body) {
        for (Vcc4Scanner.Wheel w : scan.wheels()) if (w.body() == body) return true;
        return false;
    }

    private SteerPreset proposal() {
        return SteerPreset.proposal(axles.size(), articulated, sidesOnOwnGovernors());
    }

    private boolean sidesOnOwnGovernors() {
        Map<BlockPos, int[]> sides = new HashMap<>();
        for (int i = 0; i < scan.wheels().size(); i++) {
            if (specOf[i] < 0) continue;
            BlockPos g = scan.governorFor(scan.wheels().get(i).pos());
            if (g == null || setup.governorExcluded(g.asLong())) continue;
            double lat = wheelSpecs.get(specOf[i]).lateral();
            int[] count = sides.computeIfAbsent(g, k -> new int[2]);
            if (lat < -0.25) count[0]++;
            else if (lat > 0.25) count[1]++;
            else return false;
        }
        boolean left = false;
        boolean right = false;
        for (int[] count : sides.values()) {
            if (count[0] > 0 && count[1] > 0) return false;
            left |= count[0] > 0;
            right |= count[1] > 0;
        }
        return left && right;
    }

    private void findJoint() {
        joint = null;
        articulated = false;
        if (axles.size() > 1) return;
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            int other = otherBody(s);
            if (other <= 0 || !s.assembled() || !s.locked() || !hasWheels(other)) continue;
            joint = s.pos();
            articulated = true;
            return;
        }
    }

    private void findCentreOfMass() {
        Vector3d measured = SableBridge.centerOfMassLocal(SableBridge.subLevelAccessOf(this));
        if (measured != null && finite(measured)) {
            com = measured;
            comMeasured = true;
            return;
        }
        comMeasured = false;
        Vector3d sum = new Vector3d();
        int n = 0;
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            if (w.body() != 0) continue;
            sum.add(w.contact());
            n++;
        }
        com = n > 0 ? sum.div(n) : Vcc4Scanner.centre(worldPosition);
    }

    private void updateFrames() {
        List<Vcc4Scanner.Body> bodies = scan.bodies();
        int n = Math.max(1, bodies.size());
        Frame[] next = new Frame[n];
        next[0] = Frame.IDENTITY;
        Object main = SableBridge.subLevelAccessOf(this);
        for (int b = 1; b < n; b++) {
            Vcc4Scanner.Body body = bodies.get(b);
            Frame f = null;
            Vector3d anchor = Vcc4Scanner.centre(body.anchor());
            if (main != null && body.handle() != null) {
                Quaterniond qa = SableBridge.bodyOrientation(main);
                Quaterniond qb = SableBridge.bodyOrientation(body.handle());
                Vector3d world = SableBridge.localToWorld(body.handle(), anchor);
                Vector3d here = world == null ? null : SableBridge.worldToLocal(main, world);
                if (qa != null && qb != null && here != null && finite(here)) {
                    Quaterniond rel = new Quaterniond(qa).conjugate().mul(qb).normalize();
                    if (Double.isFinite(rel.w)) f = new Frame(rel, anchor, here, true);
                }
            }
            if (f == null) {
                int parent = body.parent() >= 0 && body.parent() < b ? body.parent() : 0;
                Frame pf = next[parent];
                Vector3d at = body.joint() == null ? Vcc4Scanner.centre(worldPosition) : pf.point(Vcc4Scanner.centre(body.joint()));
                f = new Frame(new Quaterniond(pf.q()), anchor, at, false);
            }
            next[b] = f;
        }
        frames = next;
    }

    private Frame frame(int body) {
        return body >= 0 && body < frames.length && frames[body] != null ? frames[body] : Frame.IDENTITY;
    }

    Vector3d onMain(int body, Vector3d p) {
        return body == 0 ? p : frame(body).point(p);
    }

    private int articulatedHalf() {
        if (!articulated || joint == null) return -1;
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            if (s.pos().equals(joint)) return otherBody(s);
        }
        return -1;
    }

    String driverName() {
        if (driver == null || !(level instanceof ServerLevel server)) return "";
        ServerPlayer p = server.getServer().getPlayerList().getPlayer(driver);
        return p == null ? "?" : p.getGameProfile().getName();
    }

    Vcc4Scanner.Scan scan() {
        return scan;
    }

    VehicleSetup setup() {
        return setup;
    }

    boolean driving() {
        return driver != null;
    }

    DriveModel.Output lastOutput() {
        return lastOutput;
    }

    double forwardSpeed() {
        return forwardSpeed;
    }

    Vector3d com() {
        return com;
    }

    Vector3d dirOnMain(int body, Vector3d d) {
        return body == 0 ? new Vector3d(d) : frame(body).dir(d);
    }

    boolean framePlaced(int body) {
        return frame(body).placed();
    }

    double wheelRatio(long wheel) {
        return wheelRatio.getOrDefault(wheel, 1.0);
    }

    double propGain(long prop) {
        return propGain.getOrDefault(prop, 0.0);
    }

    void requestRescan() {
        scanCooldown = Math.min(scanCooldown, 1);
    }

    void publishNow() {
        publish();
    }

    private void updateDriver(ServerLevel server, long now) {
        if (coDriver != null) {
            ServerPlayer co = server.getServer().getPlayerList().getPlayer(coDriver);
            if (co == null || seatScore(co) <= 0.0) dropCoDriver(co, "You left the seat.");
        }
        ServerPlayer current = driver == null ? null : server.getServer().getPlayerList().getPlayer(driver);
        if (driver != null && (current == null || seatScore(current) <= 0.0)) {
            if (coDriver != null) {
                handOver(current);
            } else {
                release(server, current, current == null ? null : routeDriving
                        ? "You left the seat: the route drives on."
                        : "You left the seat: the vehicle is stopped and parked.");
            }
        }
        if (now % 10 != 0 || !scan.onCraft() || coDriver != null) return;
        ServerPlayer best = null;
        double bestScore = 0.0;
        for (ServerPlayer p : server.players()) {
            if (!p.isPassenger() || p.isSpectator() || p.getUUID().equals(driver) || drivingElsewhere(p)) continue;
            double score = seatScore(p);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best == null) return;
        if (driver != null) {
            addCoDriver(best, now);
            return;
        }
        driver = best.getUUID();
        DRIVING.put(driver, new WeakReference<>(this));
        UUID craft = craftId();
        if (craft != null) DRIVEN_CRAFTS.put(craft, new WeakReference<>(this));
        held = HeldKeys.NONE;
        lastInputTick = now;

        if (!routeDriving) {
            drive.reset();
            liftLever.reset();
        }
        calibrationTicks = CALIBRATION_TICKS;
        foreignGovernors.clear();
        parked = false;
        towed = false;
        helmTick = now;
        helmStill = Math.abs(measureForwardSpeed()) <= HailSequence.MAX_SPEED;
        helmWatch = setup.keymap.keysInUse();
        PacketDistributor.sendToPlayer(best, new Vcc4HelmPacket(worldPosition, true, helmWatch));
        best.displayClientMessage(Component.literal("You are driving. Right-click the console for the keys.")
                .withStyle(ChatFormatting.GREEN), true);
        final boolean greeted = hail(server, now);
        if (greeted) ModAdvancements.award(best, ModAdvancements.HAIL);
        publish();
    }

    private boolean hail(ServerLevel server, long now) {
        if (!setup.greeting || Math.abs(measureForwardSpeed()) > HailSequence.MAX_SPEED) return false;
        List<Vcc4Scanner.Link> lamps = scan.links().stream().filter(Vcc4Scanner.Link::isLamp).toList();
        if (lamps.isEmpty()) return false;
        double[] lat = new double[lamps.size()];
        double[] lon = new double[lamps.size()];
        for (int i = 0; i < lamps.size(); i++) {
            Vector3d c = onMain(lamps.get(i).body(), Vcc4Scanner.centre(lamps.get(i).pos()));
            lat[i] = lateral(c);
            lon[i] = longitudinal(c);
        }
        HailSequence.Place[] places = HailSequence.place(lat, lon);
        Map<Long, HailSequence.Role> roles = new HashMap<>();
        Map<Long, HailSequence.Place> where = new HashMap<>();
        for (int i = 0; i < lamps.size(); i++) {
            long key = lamps.get(i).pos().asLong();
            HailSequence.Role role = HailSequence.Role.of(setup.linkSourceFor(key));
            if (role == null) continue;
            roles.put(key, role);
            where.put(key, places[i]);
        }
        links.hail(server, now, roles, where);
        return !roles.isEmpty();
    }

    private void addCoDriver(ServerPlayer p, long now) {
        coDriver = p.getUUID();
        DRIVING.put(coDriver, new WeakReference<>(this));
        coHeld = HeldKeys.NONE;
        coLastInputTick = now;
        PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, true, setup.keymap.keysInUse()));
        p.displayClientMessage(Component.literal("You are co-driving with " + driverName()
                + ": your keys count with theirs.").withStyle(ChatFormatting.GREEN), true);
        publish();
    }

    private void dropCoDriver(@Nullable ServerPlayer p, @Nullable String why) {
        if (coDriver == null) return;
        WeakReference<Vcc4BE> ref = DRIVING.get(coDriver);
        if (ref != null && ref.get() == this) DRIVING.remove(coDriver, ref);
        coDriver = null;
        coHeld = HeldKeys.NONE;
        if (p != null) {
            PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            if (why != null) p.displayClientMessage(Component.literal(why), true);
        }
        publish();
    }

    private void handOver(@Nullable ServerPlayer leaving) {
        if (driver != null) {
            WeakReference<Vcc4BE> ref = DRIVING.get(driver);
            if (ref != null && ref.get() == this) DRIVING.remove(driver, ref);
        }
        driver = coDriver;
        held = coHeld;
        lastInputTick = coLastInputTick;
        coDriver = null;
        coHeld = HeldKeys.NONE;
        if (leaving != null) {
            PacketDistributor.sendToPlayer(leaving, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            leaving.displayClientMessage(Component.literal("You left the seat: " + driverName() + " drives on."), true);
        }
        publish();
    }

    private double seatScore(ServerPlayer p) {
        if (!p.isPassenger()) return 0.0;
        return SeatRule.score(Vcc4Reflect.seatCraftId(p), craftId(),
                SeatReach.offset(p, worldPosition, SableBridge.subLevelAccessOf(this)));
    }

    @Nullable
    @Override
    public UUID craftId() {
        return SableBridge.subLevelId(SableBridge.subLevelAccessOf(this));
    }

    private boolean drivingElsewhere(ServerPlayer p) {
        WeakReference<Vcc4BE> ref = DRIVING.get(p.getUUID());
        Vcc4BE other = ref == null ? null : ref.get();
        if (other == null || other == this || other.isRemoved()
                || !(p.getUUID().equals(other.driver) || p.getUUID().equals(other.coDriver))) {
            if (ref != null && other != this) DRIVING.remove(p.getUUID(), ref);
            return false;
        }
        return true;
    }

    private boolean drivenByAnother() {
        UUID craft = craftId();
        if (craft == null) return false;
        WeakReference<Vcc4BE> ref = DRIVEN_CRAFTS.get(craft);
        Vcc4BE other = ref == null ? null : ref.get();
        if (other == null || other.isRemoved() || (other.driver == null && !other.routeDriving)) {
            if (ref != null) DRIVEN_CRAFTS.remove(craft, ref);
            return false;
        }
        return other != this;
    }

    private void syncHelm(ServerPlayer p, boolean remember) {
        int[] watch = setup.keymap.keysInUse();
        if (Arrays.equals(watch, helmWatch)) return;
        if (remember) helmWatch = watch;
        PacketDistributor.sendToPlayer(p, new Vcc4HelmPacket(worldPosition, true, watch));
    }

    private void release(ServerLevel server, @Nullable ServerPlayer player, @Nullable String why) {
        if (coDriver != null) {
            dropCoDriver(server.getServer().getPlayerList().getPlayer(coDriver),
                    why == null ? null : "The vehicle was let go: you are no longer co-driving.");
        }
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new Vcc4HelmPacket(worldPosition, false, new int[0]));
            if (why != null) player.displayClientMessage(Component.literal(why), true);
        }
        if (driver != null) {
            WeakReference<Vcc4BE> ref = DRIVING.get(driver);
            if (ref != null && ref.get() == this) DRIVING.remove(driver, ref);
        }
        UUID craft = craftId();
        if (craft != null) {
            WeakReference<Vcc4BE> ref = DRIVEN_CRAFTS.get(craft);
            if (ref != null && ref.get() == this) DRIVEN_CRAFTS.remove(craft, ref);
        }
        driver = null;
        held = HeldKeys.NONE;
        heldThisTick = HeldKeys.NONE;
        helmWatch = new int[0];
        if (routeDriving && player != null) {
            publish();
            return;
        }
        drive.reset();
        liftLever.reset();
        lastOutput = DriveModel.Output.idle(setup.options.lockDeg());
        Arrays.fill(controlValue, 0.0);
        jetCommand.clear();
        machineOutput.clear();
        starved.clear();
        train.release();

        Vcc4ThrustLeases.releaseAll(server, controllerId);
        foreignJets.clear();
        machineAxes.values().forEach(MachineAxis::reset);
        releaseHardware(server);
        publish();
    }

    private void releaseHardware(ServerLevel server) {
        for (BlockPos pos : ownedGovernors) {
            RpmGovernorBE g = governorAt(server, pos);
            if (g != null) g.releaseOwnership(controllerId);
        }
        ownedGovernors.clear();
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            long key = s.pos().asLong();
            PartInput input = inputOf(key);
            if (input == PartInput.OFF || setup.auxSettingFor(key, input).latched()) continue;
            AuxAxis axis = auxAxes.get(key);
            if (axis != null) axis.set(0.0);
            auxAngle.put(key, 0.0);
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, s.pos());
            if (be != null && s.assembled() && s.locked()) Vcc4Reflect.setSwivelAngle(server, be, 0.0);
        }
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            Vcc4BrakeOverrides.clear(w.pos());
            Vcc4YawOverrides.clear(w.pos());
        }
        wheelAngle.clear();
    }

    private void idleTick(long now) {
        towed = train.towedNow(now);
        train.idle(now);
        boolean hold = setup.options.parkBrake() && scan.onCraft() && !scan.wheels().isEmpty()
                && Vcc4Reflect.brakeMixinApplied() && !drivenByAnother() && !towed;
        if (hold) {
            for (Vcc4Scanner.Wheel w : scan.wheels()) {
                if (!train.leftAlone(w.body())) Vcc4BrakeOverrides.setForTick(w.pos(), FULL_BRAKE, now);
            }
        }
        if (hold != parked) {
            parked = hold;
            publish();
        }
    }

    @Override
    public void acceptInput(ServerPlayer player, HeldKeys keys) {
        if (level == null) return;
        if (driver != null && driver.equals(player.getUUID())) {
            held = keys == null ? HeldKeys.NONE : keys;
            lastInputTick = level.getGameTime();
        } else if (coDriver != null && coDriver.equals(player.getUUID())) {
            coHeld = keys == null ? HeldKeys.NONE : keys;
            coLastInputTick = level.getGameTime();
        }
    }

    private void driveTick(ServerLevel server, long now, HeldKeys keys) {
        runtimeProblems.clear();
        readJets(server);
        Keymap km = setup.keymap;
        forwardSpeed = measureForwardSpeed();
        boolean jetDrive = jetDrive();
        double steerKey = km.axis(Control.STEER, keys);
        double lift = liftLever.step(setup.liftHoldFor(jetDrive), km.axis(Control.LIFT, keys));
        double slide = km.axis(Control.SLIDE, keys);
        boolean otherDemand = (setup.preset == SteerPreset.SKID && steerKey != 0.0)
                || (lift != 0.0 && hasJob(JetMixer.Job.LIFT)) || (slide != 0.0 && hasJob(JetMixer.Job.SLIDE))
                || auxJetDemand(keys);
        DriveModel.Output out;
        if (routeDriving) {
            out = route.output(setup.options, forwardSpeed);
            drive.follow(out);
        } else {
            DriveModel.Inputs in = new DriveModel.Inputs(km.axis(Control.THROTTLE, keys), steerKey, km.held(Control.BRAKE, keys),
                    km.held(Control.BOOST, keys), forwardSpeed, otherDemand, !jetDrive && !scan.wheels().isEmpty());
            out = drive.step(setup.options, in);
        }
        lastOutput = out;
        for (Control c : Control.values()) {
            controlValue[c.ordinal()] = switch (c) {
                case THROTTLE -> out.throttle();
                case STEER -> out.steer();
                case BRAKE -> out.braking() ? 1.0 : 0.0;
                case BOOST -> out.boosting() ? 1.0 : 0.0;
                case LIFT -> lift;
                default -> km.axis(c, keys);
            };
        }

        int pulsed = routeDriving ? route.pulsedAux() : -1;
        if (pulsed >= 0 && pulsed < ROUTE_AUX.length) controlValue[ROUTE_AUX[pulsed].ordinal()] = 1.0;
        Map<BlockPos, double[]> governorDemand = new HashMap<>();
        train.tick(server, now, out, GroundSteer.turnCentre(wheelSpecs, axles, setup.rolesFor(axles.size()), setup.preset,
                out.steer(), out.lockDeg()));
        driveWheels(now, out, governorDemand);
        driveJets(server, out, keys, governorDemand, jetDrive, lift, slide);
        moveSwivels(server);
        driveMachines(governorDemand);
        commandGovernors(server, governorDemand);
        learn(server);
        status = statusLine(out);
    }

    private boolean auxJetDemand(HeldKeys keys) {
        for (Vcc4Scanner.Prop p : scan.props()) {
            Control c = setup.jetRoleFor(p.pos().asLong()).auxControl();
            if (c != null && setup.keymap.axis(c, keys) != 0.0) return true;
        }
        return false;
    }

    private void driveWheels(long now, DriveModel.Output out, Map<BlockPos, double[]> governorDemand) {
        if (scan.wheels().isEmpty()) return;
        int n = axles.size();
        List<GroundSteer.Command> commands = GroundSteer.solve(wheelSpecs, axles, setup.rolesFor(n), setup.drivenFor(n),
                setup.preset, out.steer(), out.throttle(), out.lockDeg());

        int brakeSignal = out.brakeSignal();
        traceWheelWant = Double.NaN;
        traceWheelSent = Double.NaN;
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            Vcc4Train.Plan plan = w.body() > 0 ? train.planFor(w.body()) : null;
            if (plan != null) {
                train.driveWheel(now, w, plan, governorDemand, wheelAngle);
                continue;
            }
            if (train.leftAlone(w.body())) {
                wheelAngle.remove(w.pos().asLong());
                continue;
            }

            GroundSteer.Command c = specOf[i] >= 0 && specOf[i] < commands.size() ? commands.get(specOf[i])
                    : new GroundSteer.Command(0.0, out.throttle(), true);
            if (c.commanded()) {
                int steps = GroundSteer.signalSteps(c.angleDeg());
                int left = c.angleDeg() > 0 ? steps : 0;
                int right = c.angleDeg() < 0 ? steps : 0;
                WheelMountSteeringOverrides.setForTick(w.pos(), left, right, now);

                Vcc4YawOverrides.setForTick(w.pos(), c.angleDeg(), now);

                if (Double.isNaN(traceWheelWant) && steeredSpec(specOf[i], n)) {
                    traceWheelWant = c.angleDeg();
                    traceWheelSent = Math.max(-Vcc4YawOverrides.MAX_DEG, Math.min(Vcc4YawOverrides.MAX_DEG, c.angleDeg()));
                }
                Vcc4BrakeOverrides.setForTick(w.pos(), brakeSignal, now);
                wheelAngle.put(w.pos().asLong(), c.angleDeg());
            } else {
                wheelAngle.remove(w.pos().asLong());
            }
            BlockPos governor = scan.governorFor(w.pos());
            if (governor == null || setup.governorExcluded(governor.asLong())) continue;
            double forward = wheelForwardSign(w);
            if (forward == 0.0) continue;
            double mountSpeed = c.drive() * setup.options.maxRpm() * forward;
            double ratio = wheelRatio.getOrDefault(w.pos().asLong(), 1.0);
            if (setup.governorFlipped(governor.asLong())) ratio = -ratio;
            accumulate(governorDemand, governor, mountSpeed / ratio);
        }
    }

    private double traceWheelWant = Double.NaN;
    private double traceWheelSent = Double.NaN;

    private boolean steeredSpec(int spec, int axleCount) {
        if (spec < 0 || spec >= wheelSpecs.size()) return false;
        int[] of = AxleLayout.axleOf(wheelSpecs.size(), axles);
        AxleRole[] roles = setup.rolesFor(axleCount);
        int a = of[spec];
        return a >= 0 && a < roles.length && roles[a] == AxleRole.STEER;
    }

    double wheelForwardSign(Vcc4Scanner.Wheel w) {
        Vector3d push = w.push();
        if (w.body() != 0) push = frame(w.body()).dir(push);
        double along = push.dot(nose());
        return Math.abs(along) < 0.5 ? 0.0 : Math.signum(along);
    }

    private void driveJets(ServerLevel server, DriveModel.Output out, HeldKeys keys, Map<BlockPos, double[]> governorDemand,
                           boolean jetDrive, double lift, double slide) {
        jetCommand.clear();
        foreignJets.clear();
        if (scan.props().isEmpty()) return;
        Keymap km = setup.keymap;
        JetMixer.Demand d = new JetMixer.Demand(km.held(Control.BOOST, keys), out.pushDir(), out.steer(),
                forwardSpeed < -DriveModel.STOPPED_SPEED, out.throttle(), km.axis(Control.THROTTLE, keys),
                out.braking(), km.held(Control.BRAKE, keys), lift, slide,
                new double[]{km.axis(Control.AUX_A, keys), km.axis(Control.AUX_B, keys),
                        km.axis(Control.AUX_C, keys), km.axis(Control.AUX_D, keys)});
        JetMixer.Mode mode = new JetMixer.Mode(setup.jetsSteerWithoutBoost, jetDrive);
        for (Vcc4Scanner.Prop p : scan.props()) {
            long key = p.pos().asLong();
            if (train.leftAlone(p.body())) {
                jetCommand.put(key, 0.0);
                continue;
            }

            double planned = train.jetCommand(p);
            double u = !Double.isNaN(planned) ? planned : JetMixer.command(jetSpec(p), d, mode);
            if (p.direct()) {
                jetCommand.put(key, driveDirect(server, p, u, d, mode));
                continue;
            }
            jetCommand.put(key, u);
            BlockPos governor = scan.governorFor(p.pos());
            if (governor == null || setup.governorExcluded(governor.asLong())) continue;

            if (setup.jetRoleFor(key) == JetRole.OFF) continue;
            int sign = propSign.getOrDefault(key, 1);
            if (setup.governorFlipped(governor.asLong())) sign = -sign;
            if (setup.jetInverted(key)) sign = -sign;
            accumulate(governorDemand, governor, u * MAX_PROP_RPM * sign);
        }
    }

    private double driveDirect(ServerLevel server, Vcc4Scanner.Prop p, double asked, JetMixer.Demand d, JetMixer.Mode mode) {
        long key = p.pos().asLong();
        if (setup.jetRoleFor(key) == JetRole.OFF) return 0.0;
        double u = ThrustCommand.forKind(p.kind(), asked);
        BlockEntity be = SableBridge.getBlockEntityOnContraption(server, p.pos());
        Vcc4Jets.Type type = Vcc4Jets.of(be);
        if (type == null) return 0.0;
        if (!Vcc4ThrustLeases.renew(server, p.pos(), controllerId, be, type)) {
            foreignJets.add(key);
            runtimeProblems.add("J" + (scan.props().indexOf(p) + 1) + " is held by another console, so this one cannot fire it.");
            return 0.0;
        }
        float[] nozzle = null;
        if (p.kind() == JetKind.VECTOR) {
            nozzle = new float[]{nozzleSteer(p, u, d, mode), 0f};
            float[] target = type.nozzleTarget(be);

            if (target != null && (Math.abs(target[0] - nozzle[0]) > 1.0e-4f || Math.abs(target[1] - nozzle[1]) > 1.0e-4f)) {
                Vcc4ThrustLeases.nozzleMoved(server, p.pos(), controllerId);
            }
        }
        type.command(server, be, u, nozzle);
        return u;
    }

    private float nozzleSteer(Vcc4Scanner.Prop p, double pushing, JetMixer.Demand d, JetMixer.Mode mode) {
        if (!(pushing > 0.0) || train.ownFrame(p.body()) || train.rootOf(p.body()) != 0) return 0f;
        if (Math.abs(forwardSpeed) < 1.0 && train.towsArticulatingAssist()) return 0f;
        double turn = JetMixer.turnDemand(d, mode);
        if (turn == 0.0) return 0f;
        JetMixer.Jet spec = jetSpec(p);
        if (Math.abs(spec.axisUp()) >= JetMixer.VERTICAL) return 0f;

        double[] r = VectorGimbal.right(new double[]{p.axis().x, p.axis().y, p.axis().z});
        Vector3d sideways = dirOnMain(p.body(), new Vector3d(-r[0], -r[1], -r[2]));
        double arm = spec.forward() * sideways.dot(right()) - spec.right() * sideways.dot(nose());
        return VectorGimbal.steer(turn, pushing, arm);
    }

    private void readJets(ServerLevel server) {
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (p.direct()) jetReading(server, p);
        }
    }

    @Nullable
    Vcc4Jets.Reading jetReadingNow(Vcc4Scanner.Prop p) {
        return p.direct() ? jetReadings.get(p.pos().asLong()) : null;
    }

    @Nullable
    Vcc4Jets.Reading jetReading(ServerLevel server, Vcc4Scanner.Prop p) {
        if (!p.direct()) return null;
        long now = server.getGameTime();
        if (now != jetReadingsTick) {
            jetReadings.clear();
            jetReadingsTick = now;
        }
        long key = p.pos().asLong();
        if (jetReadings.containsKey(key)) return jetReadings.get(key);
        Vcc4Jets.Reading r = Vcc4Jets.read(SableBridge.getBlockEntityOnContraption(server, p.pos()));
        jetReadings.put(key, r);
        return r;
    }

    private JetMixer.Jet jetSpec(Vcc4Scanner.Prop p) {
        JetRole role = setup.jetRoleFor(p.pos().asLong());
        JetMixer.Jet own = train.trailerJetSpec(p, role);
        if (own != null) return own;
        Vector3d c = onMain(p.body(), p.centre());
        Vector3d axis = p.body() == 0 ? p.axis() : frame(p.body()).dir(p.axis());
        Vector3d centre = train.jetCom();
        return new JetMixer.Jet(lateral(c) - lateral(centre), c.y - centre.y, longitudinal(c) - longitudinal(centre),
                axis.dot(right()), axis.y, axis.dot(nose()), role);
    }

    private boolean hasJob(JetMixer.Job job) {
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (setup.jetRoleFor(p.pos().asLong()) != JetRole.OFF && JetMixer.jobOf(jetSpec(p)) == job) return true;
        }
        return false;
    }

    private boolean jetDrive() {
        if (scan.props().isEmpty()) return false;
        boolean[] drivenAxles = setup.drivenFor(axles.size());
        int[] axleOf = AxleLayout.axleOf(wheelSpecs.size(), axles);
        for (int i = 0; i < scan.wheels().size(); i++) {
            BlockPos g = scan.governorFor(scan.wheels().get(i).pos());
            if (g == null || setup.governorExcluded(g.asLong())) continue;
            int spec = specOf[i];
            if (spec < 0) return false;
            int a = spec < axleOf.length ? axleOf[spec] : -1;
            if (a < 0 || a >= drivenAxles.length || drivenAxles[a]) return false;
        }
        return true;
    }

    private void moveSwivels(ServerLevel server) {
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            long key = s.pos().asLong();
            PartInput input = inputOf(key);
            if (input == PartInput.OFF) continue;
            AuxAxis axis = auxAxes.computeIfAbsent(key, k -> {
                AuxAxis a = new AuxAxis();
                a.set(auxAngle.getOrDefault(k, 0.0));
                return a;
            });
            double angle = axis.step(setup.auxSettingFor(key, input), controlValue[input.control().ordinal()]);
            Double before = auxAngle.put(key, angle);
            if (before == null || Math.abs(before - angle) > 1.0e-6) setChanged();
            if (!s.assembled() || !s.locked()) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, s.pos());
            if (be != null && SableBridge.matchesSwivelBearing(be)) Vcc4Reflect.setSwivelAngle(server, be, angle);
        }
    }

    private void driveMachines(Map<BlockPos, double[]> governorDemand) {
        Keymap km = setup.keymap;
        for (Vcc4Scanner.Machine m : scan.machines()) {
            long key = m.governor().asLong();
            PartInput input = inputOf(key);
            if (input == PartInput.OFF) {
                machineOutput.remove(key);
                continue;
            }
            Control c = input.control();
            MachineAxis.Setting setting = setup.machineSettingFor(key, m.runner());
            boolean twoKeys = !c.isAxis() || (km.plus(c) != KeyIds.NONE && km.minus(c) != KeyIds.NONE);
            double out = machineAxes.computeIfAbsent(key, k -> new MachineAxis())
                    .step(setting, controlValue[c.ordinal()], twoKeys);
            machineOutput.put(key, out);
            governorDemand.put(m.governor(), new double[]{out * setting.rpm(), 1.0});
        }
    }

    private PartInput inputOf(long pos) {
        return inputs.getOrDefault(pos, PartInput.OFF);
    }

    private void commandGovernors(ServerLevel server, Map<BlockPos, double[]> governorDemand) {
        int index = 0;
        for (BlockPos pos : scan.usedGovernors()) {
            index++;
            command(server, pos, governorDemand, setup.governorExcluded(pos.asLong()) || train.handsOff().contains(pos), true, "G" + index);
        }
        index = 0;
        for (Vcc4Scanner.Machine m : scan.machines()) {
            index++;
            command(server, m.governor(), governorDemand, inputOf(m.governor().asLong()) == PartInput.OFF, false, "M" + index);
        }
    }

    private void command(ServerLevel server, BlockPos pos, Map<BlockPos, double[]> demand, boolean off, boolean calibrate,
                         String name) {
        RpmGovernorBE governor = governorAt(server, pos);
        if (governor == null) return;
        if (off) {
            if (ownedGovernors.remove(pos)) governor.releaseOwnership(controllerId);
            starved.remove(pos);
            return;
        }
        double[] acc = demand.get(pos);
        int rpm = acc == null ? 0 : (int) Math.round(acc[0] / acc[1]);
        if (calibrate && rpm == 0 && calibrationTicks > 0 && needsCalibration(pos)) rpm = CALIBRATION_RPM;
        if (governor.setCommand(worldPosition, controllerId, Math.max(-MAX_PROP_RPM, Math.min(MAX_PROP_RPM, rpm)))) {
            ownedGovernors.add(pos);
            foreignGovernors.remove(pos);
        } else {
            foreignGovernors.add(pos);
            runtimeProblems.add(name + " is held by another controller (a second console, or a Flight Control Computer), so this one cannot drive it.");
            return;
        }
        checkSupply(governor, pos, rpm, name);
    }

    private void checkSupply(RpmGovernorBE governor, BlockPos pos, int rpm, String name) {
        boolean noSource = !governor.hasSource() || Math.abs(governor.getSpeed()) < 1.0e-4f;
        boolean over = governor.isOverStressed();
        if (rpm == 0 || !(noSource || over)) {
            starved.remove(pos);
            return;
        }
        int ticks = starved.merge(pos, 1, Integer::sum);
        if (ticks < STARVED_TICKS) return;
        runtimeProblems.add(over
                ? name + "'s shaft network is overstressed, so it turns nothing: it needs more stress capacity or less load."
                : name + " gets no rotation from its engine: is the engine running and fuelled, and its shaft connected?");
    }

    private boolean needsCalibration(BlockPos governor) {
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            if (governor.equals(scan.governorFor(w.pos())) && !wheelRatio.containsKey(w.pos().asLong())) return true;
        }
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (governor.equals(scan.governorFor(p.pos())) && !propSign.containsKey(p.pos().asLong())) return true;
        }
        return false;
    }

    private void learn(ServerLevel server) {
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            RpmGovernorBE g = governorAt(server, scan.governorFor(w.pos()));
            if (g == null || Math.abs(g.getAppliedRpm()) < 2) continue;
            if (!(SableBridge.getBlockEntityOnContraption(server, w.pos()) instanceof KineticBlockEntity mount)) continue;
            float speed = mount.getSpeed();
            if (Math.abs(speed) < 0.5f) continue;
            double ratio = speed / (double) g.getAppliedRpm();
            Double old = wheelRatio.put(w.pos().asLong(), ratio);
            if (old == null || Math.abs(old - ratio) > 1.0e-3) setChanged();
        }
        for (Vcc4Scanner.Prop p : scan.props()) {
            RpmGovernorBE g = governorAt(server, scan.governorFor(p.pos()));
            if (g == null || Math.abs(g.getAppliedRpm()) < 2) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, p.pos());
            double along = thrustAlongAxis(be, p);
            if (Math.abs(along) < 1.0e-4) continue;
            int sign = (int) Math.signum(along * g.getAppliedRpm());
            Integer old = propSign.put(p.pos().asLong(), sign);
            if (old == null || old != sign) setChanged();

            double gain = Math.abs(along) / Math.abs(g.getAppliedRpm());
            Double oldGain = propGain.put(p.pos().asLong(), gain);
            if (oldGain == null || Math.abs(oldGain - gain) > gain * 0.05) setChanged();
        }
    }

    static double thrustAlongAxis(@Nullable BlockEntity be, Vcc4Scanner.Prop p) {
        if (be == null) return 0.0;
        SableBridge.PropData data = SableBridge.readProp(be);
        if (data != null) {
            Vector3d dir = new Vector3d(data.thrustDirLocal());
            if (dir.length() < 0.5) dir = new Vector3d(p.axis());
            return dir.normalize().mul(data.thrustSigned()).dot(p.axis());
        }
        Double raw = SableBridge.propellerThrust(be);
        return raw == null ? 0.0 : -raw;
    }

    @Nullable
    private RpmGovernorBE governorAt(ServerLevel server, @Nullable BlockPos pos) {
        if (pos == null) return null;
        return SableBridge.getBlockEntityOnContraption(server, pos) instanceof RpmGovernorBE g ? g : null;
    }

    static void accumulate(Map<BlockPos, double[]> acc, BlockPos governor, double value) {
        double[] a = acc.computeIfAbsent(governor, g -> new double[2]);
        a[0] += value;
        a[1] += 1.0;
    }

    private double measureForwardSpeed() {
        Vector3d v = SableBridge.linearVelocityWorld(this);
        if (v == null) return 0.0;
        Vector3d noseWorld = new Vector3d(nose());
        Quaterniond q = SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(this));
        if (q != null) q.transform(noseWorld);
        double s = v.dot(noseWorld);
        return Double.isFinite(s) ? s : 0.0;
    }

    private Map<Long, LinkSource> lampProposals() {
        List<Vcc4Scanner.Link> lamps = scan.links().stream().filter(Vcc4Scanner.Link::isLamp).toList();
        if (lamps.isEmpty()) return Map.of();
        double[] wheels = scan.wheels().stream().filter(w -> w.body() == 0).mapToDouble(w -> lateral(w.contact())).toArray();
        double[] sides = lamps.stream().mapToDouble(l -> lateral(onMain(l.body(), Vcc4Scanner.centre(l.pos())))).toArray();
        double mid = LampProposal.centreLine(wheels, sides);
        Vector3d nose = nose();
        Vector3d right = right();
        Map<Long, LinkSource> out = new HashMap<>();
        for (int i = 0; i < lamps.size(); i++) {
            Vcc4Scanner.Link l = lamps.get(i);
            Direction f = l.facing();
            Vector3d dir = new Vector3d(f.getStepX(), f.getStepY(), f.getStepZ());
            if (l.body() != 0) dir = frame(l.body()).dir(dir);
            out.put(l.pos().asLong(), LampProposal.propose(l.lamp(), dir.dot(nose), dir.dot(right), sides[i] - mid));
        }
        return out;
    }

    private LinkLogic.Signals signals(long now) {
        if (driver == null && !routeDriving) return LinkLogic.Signals.NONE;
        DriveModel.Output out = lastOutput;
        boolean reversing = out.throttle() < -1.0e-6 || forwardSpeed < -DriveModel.STOPPED_SPEED;
        int indicate = routeDriving ? route.indicate() : 0;

        boolean braking = out.braking() || (routeDriving && route.slowing());
        return new LinkLogic.Signals(true, braking, reversing, out.boosting(), indicate != 0 ? indicate : out.steer(),
                new double[]{controlValue[Control.AUX_A.ordinal()], controlValue[Control.AUX_B.ordinal()],
                        controlValue[Control.AUX_C.ordinal()], controlValue[Control.AUX_D.ordinal()]}, now,
                controlValue[Control.BEAM.ordinal()] > 0.5);
    }

    Direction noseDirection() {
        return getBlockState().getValue(Vcc4Block.FACING);
    }

    Vector3d nose() {
        Direction d = noseDirection();
        return new Vector3d(d.getStepX(), 0, d.getStepZ());
    }

    Vector3d right() {
        Direction d = noseDirection();

        return new Vector3d(-d.getStepZ(), 0, d.getStepX());
    }

    double lateral(Vector3d p) {
        return offset(p).dot(right());
    }

    double longitudinal(Vector3d p) {
        return offset(p).dot(nose());
    }

    private Vector3d offset(Vector3d p) {
        return new Vector3d(p).sub(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static boolean finite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public void openFor(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        rescan(server);
        scanCooldown = SCAN_INTERVAL_IDLE;
        watchers.put(player.getUUID(), server.getGameTime());
        PacketDistributor.sendToPlayer(player, new Vcc4SnapshotPacket(worldPosition, true, snapshot(server, player)));
    }

    public void handleAction(ServerPlayer player, Vcc4ActionPacket p) {
        if (!(level instanceof ServerLevel server)) return;
        if (p.action() == Vcc4ActionPacket.CLOSE) {
            watchers.remove(player.getUUID());
            return;
        }

        if (!watchers.containsKey(player.getUUID()) || !SableBridge.isPlayerInInteractionRangeFor(this, player)) return;
        watchers.put(player.getUUID(), server.getGameTime());
        int n = axles.size();
        long target = p.target();
        switch (p.action()) {
            case Vcc4ActionPacket.KEEPALIVE -> {
                return;
            }
            case Vcc4ActionPacket.SET_PRESET -> {
                if (p.a() == Vcc4ActionPacket.PRESET_AUTO) {
                    setup.presetAuto = true;
                    setup.proposePreset(proposal());
                } else {
                    setup.applyPreset(SteerPreset.byOrdinal(p.a()), n);
                }
            }
            case Vcc4ActionPacket.SET_AXLE_ROLE -> setup.setAxleRole(p.a(), AxleRole.byOrdinal(p.b()), n);
            case Vcc4ActionPacket.TOGGLE_AXLE_DRIVE -> {
                if (p.a() < 0 || p.a() >= n) return;
                setup.setAxleDriven(p.a(), !setup.drivenFor(n)[p.a()], n);
            }
            case Vcc4ActionPacket.SET_KEY -> {
                Control c = Control.byOrdinal(p.a());
                if (c == null || (target != Keymap.PLUS && target != Keymap.MINUS)) return;
                setup.keymap.set(c, (int) target, KeyIds.sane(p.b()));
            }
            case Vcc4ActionPacket.TOGGLE_INVERT -> {
                Control c = Control.byOrdinal(p.a());
                if (c == null) return;
                setup.keymap.setInverted(c, !setup.keymap.inverted(c));
            }
            case Vcc4ActionPacket.RESET_KEYS -> setup.keymap.reset();
            case Vcc4ActionPacket.SET_OPTION -> setOption(p.a(), p.b());
            case Vcc4ActionPacket.SET_JET_ROLE -> {
                if (!isProp(target)) return;
                JetRole role = JetRole.byOrdinal(p.a());
                if (role == JetRole.AUTO) setup.jetRoles.remove(target);
                else setup.jetRoles.put(target, role);
            }
            case Vcc4ActionPacket.TOGGLE_JET_INVERT -> {
                if (!isProp(target)) return;
                setup.jetInverted.put(target, !setup.jetInverted(target));
            }
            case Vcc4ActionPacket.SET_SWIVEL_INPUT -> {
                if (!isSwivel(target)) return;
                setup.swivelInputs.put(target, PartInput.byOrdinal(p.a()));
            }
            case Vcc4ActionPacket.SET_SWIVEL_TRAVEL -> {
                if (!isSwivel(target)) return;
                setup.auxSettings.put(target, currentAuxSetting(target).withTravel(p.a()));
            }
            case Vcc4ActionPacket.TOGGLE_SWIVEL_LATCHED -> {
                if (!isSwivel(target)) return;
                AuxAxis.Setting s = currentAuxSetting(target);
                setup.auxSettings.put(target, s.withLatched(!s.latched()));
            }
            case Vcc4ActionPacket.TOGGLE_SWIVEL_INVERT -> {
                if (!isSwivel(target)) return;
                AuxAxis.Setting s = currentAuxSetting(target);
                setup.auxSettings.put(target, s.withInverted(!s.inverted()));
            }
            case Vcc4ActionPacket.SET_MACHINE_INPUT -> {
                if (machineAt(target) == null) return;
                setup.machineInputs.put(target, PartInput.byOrdinal(p.a()));
                resetMachine(target);
            }
            case Vcc4ActionPacket.SET_MACHINE_RPM -> {
                Vcc4Scanner.Machine m = machineAt(target);
                if (m == null) return;
                setup.machineSettings.put(target, setup.machineSettingFor(target, m.runner()).withRpm(p.a()));
            }
            case Vcc4ActionPacket.TOGGLE_MACHINE_LATCHED -> {
                Vcc4Scanner.Machine m = machineAt(target);
                if (m == null) return;
                MachineAxis.Setting s = setup.machineSettingFor(target, m.runner());
                setup.machineSettings.put(target, s.withLatched(!s.latched()));
                resetMachine(target);
            }
            case Vcc4ActionPacket.TOGGLE_MACHINE_INVERT -> {
                Vcc4Scanner.Machine m = machineAt(target);
                if (m == null) return;
                MachineAxis.Setting s = setup.machineSettingFor(target, m.runner());
                setup.machineSettings.put(target, s.withInverted(!s.inverted()));
            }
            case Vcc4ActionPacket.SET_LINK_SOURCE -> {
                if (!isLink(target)) return;
                setup.setLinkSource(target, LinkSource.byOrdinal(p.a()));
            }
            case Vcc4ActionPacket.TOGGLE_LINK_TOGGLE -> {
                if (!isLink(target)) return;
                setup.linkToggle.put(target, !setup.linkToggle(target));
            }
            case Vcc4ActionPacket.SET_TRAILER_MODE -> {
                int b = p.a();
                if (b <= 0 || b >= scan.bodies().size() || !train.takesMode(b)) return;
                UUID id = scan.bodies().get(b).id();

                if (id == null || id.getLeastSignificantBits() != target) return;
                setup.setTrailerMode(id, TrailerMode.byOrdinal(p.b()));
            }
            case Vcc4ActionPacket.TOGGLE_GOVERNOR_FLIP -> {
                if (!isGovernor(target)) return;
                setup.governorFlipped.put(target, !setup.governorFlipped(target));
            }
            case Vcc4ActionPacket.TOGGLE_GOVERNOR_EXCLUDE -> {
                if (!isGovernor(target)) return;
                setup.governorExcluded.put(target, !setup.governorExcluded(target));
            }
            case Vcc4ActionPacket.FLIP_NOSE, Vcc4ActionPacket.TURN_NOSE -> {
                BlockState state = getBlockState();
                Direction now = state.getValue(Vcc4Block.FACING);
                Direction next = p.action() == Vcc4ActionPacket.FLIP_NOSE ? now.getOpposite() : now.getClockWise();
                server.setBlock(worldPosition, state.setValue(Vcc4Block.FACING, next), Block.UPDATE_ALL);

                rescan(server);
            }
            case Vcc4ActionPacket.RESET_AUTO -> {
                setup.resetToAuto();
                wheelRatio.clear();
                propSign.clear();
                propGain.clear();
                machineAxes.clear();
                overridesDropped = 0;
                calibrationTicks = driver != null || routeDriving ? CALIBRATION_TICKS : 0;
                setup.proposePreset(proposal());
            }
            case Vcc4ActionPacket.RESCAN -> {
                overridesDropped = 0;
                rescan(server);
            }
            case Vcc4ActionPacket.RELEARN -> {
                wheelRatio.clear();
                propSign.clear();
                propGain.clear();
                calibrationTicks = driver != null || routeDriving ? CALIBRATION_TICKS : 0;
            }
            default -> {
                return;
            }
        }
        refreshInputs();
        links.sync(server, scan.links(), setup);
        setChanged();
        publish();
        sendSnapshots(server, false);
    }

    private void setOption(int id, int value) {
        DriveModel.Options o = setup.options;
        switch (id) {
            case Vcc4ActionPacket.OPTION_JETS_STEER_ALONE -> setup.jetsSteerWithoutBoost = value != 0;
            case Vcc4ActionPacket.OPTION_LIFT_HOLD -> {
                setup.liftHold = value == 2 ? null : value != 0;
                liftLever.reset();
            }
            case Vcc4ActionPacket.OPTION_BRAKE_THEN_REVERSE -> setup.options = o.withBrakeThenReverse(value != 0);
            case Vcc4ActionPacket.OPTION_SPEED_STEER -> setup.options = o.withSpeedSteer(value != 0);
            case Vcc4ActionPacket.OPTION_STEER_TICKS -> setup.options = o.withSteerTicks(value);
            case Vcc4ActionPacket.OPTION_LOCK -> setup.options = o.withLockDeg(value);
            case Vcc4ActionPacket.OPTION_MAX_RPM -> setup.options = o.withMaxRpm(value);
            case Vcc4ActionPacket.OPTION_REVERSE -> setup.options = o.withReversePercent(value);
            case Vcc4ActionPacket.OPTION_THROTTLE_TICKS -> setup.options = o.withThrottleTicks(value);
            case Vcc4ActionPacket.OPTION_RELEASE_TICKS -> setup.options = o.withReleaseTicks(value);
            case Vcc4ActionPacket.OPTION_HOLD -> setup.options = o.withHoldWhenStopped(value != 0);
            case Vcc4ActionPacket.OPTION_PARK_BRAKE -> setup.options = o.withParkBrake(value != 0);
            case Vcc4ActionPacket.OPTION_GREETING -> setup.greeting = value != 0;
            default -> {
            }
        }
    }

    @Override
    public String settingsKey() {
        return "create_stats:vcc_mk4";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Setup", Vcc4Nbt.tuning(setup));
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        final CompoundTag tuning = tag.getCompound("Setup");
        if (!tuning.contains("Preset") || !tuning.contains("Keymap")) {
            return false;
        }
        if (simulate || alreadyHolds(tag, registries)) {
            return true;
        }
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        final Boolean liftBefore = setup.liftHold;
        Vcc4Nbt.applyTuning(tuning, setup);
        if (!java.util.Objects.equals(liftBefore, setup.liftHold)) {
            liftLever.reset();
        }
        if (setup.presetAuto) {
            setup.proposePreset(proposal());
        }
        refreshInputs();
        links.sync(server, scan.links(), setup);
        setChanged();
        publish();
        sendSnapshots(server, false);
        return true;
    }

    private void resetMachine(long pos) {
        MachineAxis axis = machineAxes.get(pos);
        if (axis != null) axis.reset();
    }

    private boolean isProp(long pos) {
        for (Vcc4Scanner.Prop p : scan.props()) if (p.pos().asLong() == pos) return true;
        return false;
    }

    private boolean isSwivel(long pos) {
        for (Vcc4Scanner.Swivel s : scan.swivels()) if (s.pos().asLong() == pos) return true;
        return false;
    }

    private boolean isGovernor(long pos) {
        for (BlockPos g : scan.usedGovernors()) if (g.asLong() == pos) return true;
        return false;
    }

    private boolean isLink(long pos) {
        for (Vcc4Scanner.Link l : scan.links()) if (l.pos().asLong() == pos) return true;
        return false;
    }

    @Nullable
    private Vcc4Scanner.Machine machineAt(long pos) {
        for (Vcc4Scanner.Machine m : scan.machines()) if (m.governor().asLong() == pos) return m;
        return null;
    }

    private AuxAxis.Setting currentAuxSetting(long pos) {
        return setup.auxSettingFor(pos, inputOf(pos));
    }

    private void sendSnapshots(ServerLevel server, boolean open) {
        Iterator<UUID> it = watchers.keySet().iterator();
        while (it.hasNext()) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(it.next());
            if (p == null) {
                it.remove();
                continue;
            }
            PacketDistributor.sendToPlayer(p, new Vcc4SnapshotPacket(worldPosition, open, snapshot(server, p)));
        }
    }

    private Vcc4Snapshot snapshot(ServerLevel server, ServerPlayer viewer) {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder();
        int n = axles.size();
        AxleRole[] roles = setup.rolesFor(n);
        b.roles = new int[n];
        b.axleZ = new float[n];
        for (int i = 0; i < n; i++) {
            b.roles[i] = roles[i].ordinal();
            b.axleZ[i] = (float) axles.get(i).longitudinal();
        }
        b.driven = setup.drivenFor(n);
        int[] axleOf = AxleLayout.axleOf(wheelSpecs.size(), axles);

        List<BlockPos> governors = new ArrayList<>(scan.usedGovernors());
        Map<BlockPos, Integer> governorIndex = new HashMap<>();
        for (int i = 0; i < governors.size(); i++) governorIndex.put(governors.get(i), i);
        int[] wheelCount = new int[governors.size()];
        int[] jetCount = new int[governors.size()];
        double[] ratioSum = new double[governors.size()];
        int[] ratioN = new int[governors.size()];

        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>(scan.wheels().size());
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            BlockPos g = scan.governorFor(w.pos());
            int gi = g == null ? -1 : governorIndex.getOrDefault(g, -1);
            if (gi >= 0) {
                wheelCount[gi]++;
                Double r = wheelRatio.get(w.pos().asLong());
                if (r != null) {
                    ratioSum[gi] += r;
                    ratioN[gi]++;
                }
            }
            float rpm = 0f;
            if (SableBridge.getBlockEntityOnContraption(server, w.pos()) instanceof KineticBlockEntity mount) rpm = mount.getSpeed();
            Double angle = wheelAngle.get(w.pos().asLong());
            Vector3d at = onMain(w.body(), w.contact());
            int spec = specOf.length > i ? specOf[i] : -1;
            wheels.add(new Vcc4Snapshot.Wheel(w.pos().asLong(), (float) lateral(at), (float) longitudinal(at),
                    spec >= 0 && spec < axleOf.length ? axleOf[spec] : -1, angle == null ? 0f : angle.floatValue(),
                    angle != null, gi, rpm, w.body()));
        }
        b.wheels = wheels;
        for (Vcc4Scanner.Prop p : scan.props()) {
            BlockPos g = scan.governorFor(p.pos());
            int gi = g == null ? -1 : governorIndex.getOrDefault(g, -1);
            if (gi >= 0) jetCount[gi]++;
        }
        List<Vcc4Snapshot.Jet> jets = new ArrayList<>(scan.props().size());
        for (Vcc4Scanner.Prop p : scan.props()) {
            long key = p.pos().asLong();
            BlockPos g = scan.governorFor(p.pos());
            int gi = g == null ? -1 : governorIndex.getOrDefault(g, -1);
            float rpm = 0f;
            SableBridge.PropData data = SableBridge.readProp(SableBridge.getBlockEntityOnContraption(server, p.pos()));
            if (data != null) rpm = (float) data.rpm();
            JetMixer.Jet spec = jetSpec(p);
            JetMixer.Job job = JetMixer.jobOf(spec);
            Vector3d at = onMain(p.body(), p.centre());
            Vcc4Jets.Reading r = p.direct() ? jetReading(server, p) : null;
            boolean foreign = foreignJets.contains(key) || (driver == null && p.direct() && Vcc4ThrustLeases.heldByAnother(server, p.pos(), controllerId));
            jets.add(new Vcc4Snapshot.Jet(key, (float) lateral(at), (float) longitudinal(at),
                    (float) spec.axisRight(), (float) spec.axisUp(), (float) spec.axisForward(),
                    (float) spec.right(), (float) spec.forward(), setup.jetRoleFor(key).ordinal(),
                    job == null ? -1 : job.ordinal(), gi, rpm, jetCommand.getOrDefault(key, 0.0).floatValue(),
                    setup.jetInverted(key), propSign.containsKey(key) || p.direct(), gi >= 0 && wheelCount[gi] > 0, p.body(),
                    p.kind().ordinal(), r == null ? 0f : (float) r.force(),
                    r == null ? (Double.isFinite(p.strength()) ? (float) p.strength() : 0f) : (float) r.ceiling(),
                    r == null ? 1f : (float) r.clear(), (r == null ? JetSupply.NONE : r.supply()).ordinal(),
                    r == null ? 0f : r.nozzleX(), r == null ? 0f : r.nozzleY(), foreign));
        }
        b.jets = jets;
        List<Vcc4Snapshot.Governor> govs = new ArrayList<>(governors.size());
        for (int i = 0; i < governors.size(); i++) {
            BlockPos g = governors.get(i);
            RpmGovernorBE be = governorAt(server, g);
            Vector3d c = onMain(bodyOfGovernor(g), Vcc4Scanner.centre(g));
            govs.add(new Vcc4Snapshot.Governor(g.asLong(), (float) lateral(c), (float) longitudinal(c),
                    be == null ? 0 : be.getAppliedRpm(), be == null ? 0 : be.getCommandedRpm(),
                    setup.governorFlipped(g.asLong()), setup.governorExcluded(g.asLong()), foreignGovernors.contains(g),
                    wheelCount[i], jetCount[i], ratioN[i] == 0 ? 0f : (float) (ratioSum[i] / ratioN[i])));
        }
        b.governors = govs;
        List<Vcc4Snapshot.Swivel> swivels = new ArrayList<>(scan.swivels().size());
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            long key = s.pos().asLong();
            PartInput input = inputOf(key);
            AuxAxis.Setting st = setup.auxSettingFor(key, input);
            Vector3d c = onMain(s.body(), Vcc4Scanner.centre(s.pos()));
            swivels.add(new Vcc4Snapshot.Swivel(key, (float) lateral(c), (float) longitudinal(c), input.ordinal(),
                    auxAngle.getOrDefault(key, 0.0).floatValue(), st.travelDeg(), st.latched(), st.inverted(),
                    s.assembled() && s.locked(), s.body(), s.carries(), s.assembled(), s.locked()));
        }
        b.swivels = swivels;
        List<Vcc4Snapshot.Machine> machines = new ArrayList<>(scan.machines().size());
        for (Vcc4Scanner.Machine m : scan.machines()) {
            long key = m.governor().asLong();
            PartInput input = inputOf(key);
            MachineAxis.Setting st = setup.machineSettingFor(key, m.runner());
            RpmGovernorBE be = governorAt(server, m.governor());
            Vector3d c = onMain(m.body(), Vcc4Scanner.centre(m.governor()));
            machines.add(new Vcc4Snapshot.Machine(key, (float) lateral(c), (float) longitudinal(c), m.body(), input.ordinal(),
                    st.rpm(), st.latched(), st.inverted(), machineOutput.getOrDefault(key, 0.0).floatValue(),
                    be == null ? 0 : be.getAppliedRpm(), foreignGovernors.contains(m.governor()), m.runner(), m.drives()));
        }
        b.machines = machines;
        List<Vcc4Snapshot.Link> linkRows = new ArrayList<>(scan.links().size());
        for (Vcc4Scanner.Link l : scan.links()) {
            long key = l.pos().asLong();
            Vector3d c = onMain(l.body(), Vcc4Scanner.centre(l.pos()));
            linkRows.add(new Vcc4Snapshot.Link(key, (float) lateral(c), (float) longitudinal(c), l.body(),
                    setup.linkSourceFor(key).ordinal(), setup.linkToggle(key), links.strength(key), l.first(), l.second(),
                    l.coupler(), l.isLamp() ? l.lamp().ordinal() + 1 : 0, l.isLamp() && !setup.linkSources.containsKey(key)));
        }
        b.links = linkRows;
        Map<Integer, Vcc4Train.Plan> shown = new HashMap<>();
        b.bodies = train.snapshotBodies(server, shown);
        b.ties = train.snapshotTies(shown);

        b.onCraft = scan.onCraft();
        if (driver != null) {
            ServerPlayer d = server.getServer().getPlayerList().getPlayer(driver);
            b.driver = d == null ? "?" : d.getGameProfile().getName();
            if (coDriver != null) {
                ServerPlayer co = server.getServer().getPlayerList().getPlayer(coDriver);
                b.driver += " + " + (co == null ? "?" : co.getGameProfile().getName());
            }
            b.youDrive = driver.equals(viewer.getUUID()) || viewer.getUUID().equals(coDriver);
        }
        b.nose = noseDirection().get2DDataValue();
        b.throttle = (float) lastOutput.throttle();
        b.steer = (float) lastOutput.steer();
        b.braking = lastOutput.braking() || parked;
        b.boosting = lastOutput.boosting();
        b.holding = lastOutput.holding();
        b.parked = parked;
        b.gear = driver == null && !routeDriving ? (parked ? 'P' : '-') : lastOutput.gear();
        b.speed = (float) forwardSpeed;
        b.preset = setup.preset.ordinal();
        b.presetAuto = setup.presetAuto;
        b.comLateral = (float) lateral(com);
        b.comLongitudinal = (float) longitudinal(com);
        b.comMeasured = comMeasured;
        b.keymap = setup.keymap.toInts();
        b.held = heldThisTick.toArray();
        b.controlValues = new float[controlValue.length];
        for (int i = 0; i < controlValue.length; i++) b.controlValues[i] = (float) controlValue[i];
        b.options = setup.options;
        b.jetsSteerWithoutBoost = setup.jetsSteerWithoutBoost;
        b.jetDrive = jetDrive();
        b.liftHold = setup.liftHoldFor(b.jetDrive);
        b.liftHoldAuto = setup.liftHold == null;
        b.brakesAvailable = Vcc4Reflect.brakeMixinApplied();
        b.greeting = setup.greeting;
        b.problems = problems(b.brakesAvailable, jets, swivels, machines);
        b.console = controllerId;
        b.route = RouteSnapshot.of(route.view(server), RouteSnapshot.choices(server, viewer));
        return b.build();
    }

    private int bodyOfGovernor(BlockPos governor) {
        for (Vcc4Scanner.Wheel w : scan.wheels()) if (governor.equals(scan.governorFor(w.pos()))) return w.body();
        for (Vcc4Scanner.Prop p : scan.props()) if (governor.equals(scan.governorFor(p.pos()))) return p.body();
        return 0;
    }

    private List<String> problems(boolean brakes, List<Vcc4Snapshot.Jet> jets, List<Vcc4Snapshot.Swivel> swivels,
                                  List<Vcc4Snapshot.Machine> machines) {
        List<String> out = new ArrayList<>();
        if (!SableBridge.isAvailable()) out.add("Sable physics not found: nothing on this craft can be driven.");
        out.addAll(scan.problems());
        out.addAll(runtimeProblems);
        boolean anyMain = false;
        boolean anyRolls = false;
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            if (w.body() != 0) continue;
            anyMain = true;
            anyRolls |= wheelForwardSign(w) != 0.0;
        }
        if (anyMain && !anyRolls) out.add("The console faces across the vehicle, so no wheel can drive it forward. Press Turn nose on the Parts page.");
        skidProblems(out);
        train.problems(out);
        Keymap km = setup.keymap;
        Map<Control, List<String>> waiting = new EnumMap<>(Control.class);
        for (int i = 0; i < jets.size(); i++) {
            Control needs = controlFor(jets.get(i));
            if (needs != null && !km.bound(needs)) waiting.computeIfAbsent(needs, c -> new ArrayList<>()).add("J" + (i + 1));
        }
        for (int i = 0; i < swivels.size(); i++) {
            Control needs = PartInput.byOrdinal(swivels.get(i).input()).control();
            if (needs != null && !km.bound(needs)) waiting.computeIfAbsent(needs, c -> new ArrayList<>()).add("S" + (i + 1));
        }
        for (int i = 0; i < machines.size(); i++) {
            Control needs = PartInput.byOrdinal(machines.get(i).input()).control();
            if (needs != null && !km.bound(needs)) waiting.computeIfAbsent(needs, c -> new ArrayList<>()).add("M" + (i + 1));
        }
        for (int i = 0; i < scan.links().size(); i++) {
            Vcc4Scanner.Link l = scan.links().get(i);
            Control needs = setup.linkSourceFor(l.pos().asLong()).control();

            if (l.coupler() && needs != null && !km.bound(needs)) waiting.computeIfAbsent(needs, c -> new ArrayList<>()).add("L" + (i + 1));
        }
        waiting.forEach((c, parts) -> out.add(String.join(" ", parts) + " wait" + (parts.size() == 1 ? "s" : "")
                + " for " + controlName(c) + ", which has no key yet. Bind one on the Keys page."));
        for (int i = 0; i < swivels.size(); i++) {
            Vcc4Snapshot.Swivel s = swivels.get(i);
            Control c = PartInput.byOrdinal(s.input()).control();
            String name = "S" + (i + 1);
            if (c == null) continue;
            if (!s.assembled()) out.add(name + " has nothing attached, so it moves nothing. Assemble it.");
            else if (!s.locked()) out.add(name + " is unlocked (redstone or its value panel), so it cannot hold an angle. Lock it, or set it Off to let it swing free.");
            if (s.latched() && c.isAxis() && km.bound(c) && (km.plus(c) == KeyIds.NONE || km.minus(c) == KeyIds.NONE)) {
                out.add(name + " is latched on " + controlName(c) + ", which has one key, so it can only move one way. "
                        + "Bind the second key or set " + name + " to Sprung.");
            }
        }
        for (int i = 0; i < scan.swivels().size(); i++) {
            if (scan.swivels().get(i).cogTurning() && inputOf(scan.swivels().get(i).pos().asLong()) != PartInput.OFF) {
                out.add("S" + (i + 1) + " has a turning cog, which fights the console. Leave its cog unpowered.");
            }
        }
        for (int i = 0; i < jets.size(); i++) {
            Vcc4Snapshot.Jet j = jets.get(i);
            if (j.sharesWheels() && j.role() != JetRole.OFF.ordinal()) {
                out.add("J" + (i + 1) + " shares G" + (j.governor() + 1) + " with wheels, so it spins with them whatever its job.");
            }
        }
        for (int i = 0; i < jets.size(); i++) {
            Vcc4Snapshot.Jet j = jets.get(i);
            if (!j.direct() || j.role() == JetRole.OFF.ordinal()) continue;
            String name = "J" + (i + 1);
            switch (j.supplyOf()) {
                case EMPTY -> out.add(name + (j.kindOf() == JetKind.BOOSTER
                        ? " has no fuel it can burn, so it cannot fire: load it with a solid fuel."
                        : " has no fuel it can burn, so it cannot fire: pipe in a thruster fuel, or drain a wrong one."));
                case UNPOWERED -> out.add(name + " has no energy stored, so it cannot fire: connect it to an energy supply.");
                case LOW -> out.add(name + " is nearly out of fuel: top it up.");
                default -> {
                }
            }

            if (j.clear() < 0.995f && (j.kindOf() != JetKind.VECTOR || Math.abs(j.nozzleX()) < 0.02f)) {
                out.add(name + "'s exhaust is blocked behind its nozzle, so it makes at most " + Math.round(j.clear() * 100f)
                        + "% of its thrust: clear the blocks behind it.");
            }
        }
        if (level instanceof ServerLevel server) exhaustProblems(server, out);
        if (!jets.isEmpty() && !comMeasured) out.add("Centre of mass unknown: jets are judged from the middle of the wheels.");
        if (overridesDropped > 0) out.add(overridesDropped + " setting(s) for parts no longer on the craft were dropped.");
        if (!brakes) out.add("Brakes unavailable: the wheel mount brake hook did not load (Offroad version?).");
        return out;
    }

    private void exhaustProblems(ServerLevel server, List<String> out) {
        List<Vcc4Scanner.Prop> props = scan.props();
        for (int i = 0; i < props.size(); i++) {
            Vcc4Scanner.Prop p = props.get(i);
            if (!p.direct() || setup.jetRoleFor(p.pos().asLong()) == JetRole.OFF) continue;
            Vcc4Jets.Reading r = jetReading(server, p);
            Vector3d exhaust = dirOnMain(p.body(), r == null ? p.axis() : r.direction()).negate();
            if (exhaust.lengthSquared() < 1.0e-6) continue;
            Vector3d from = onMain(p.body(), p.centre());
            for (Vcc4Scanner.Body body : scan.bodies()) {
                int b = body.index();
                if (b == p.body() || !riderOn(server, b)) continue;
                Vector3d v = new Vector3d(train.centreOf(b)).sub(from);
                double along = v.dot(exhaust);
                if (along <= 0.0 || along > 10.0) continue;
                if (v.fma(-along, exhaust).length() > 2.5) continue;
                out.add("J" + (i + 1) + "'s exhaust runs through B" + (b + 1) + ", about " + Math.max(1, Math.round(along))
                        + " block(s) behind it, where someone is riding: above a third throttle the plume burns them. "
                        + "Move the seat out of line, or aim J" + (i + 1) + " clear of B" + (b + 1) + ".");
                break;
            }
        }
    }

    private boolean riderOn(ServerLevel server, int body) {
        List<Vcc4Scanner.Body> bodies = scan.bodies();
        UUID id = body == 0 ? craftId() : (body > 0 && body < bodies.size() ? bodies.get(body).id() : null);
        if (id == null) return false;
        for (ServerPlayer p : server.players()) {
            if (p.isPassenger() && id.equals(Vcc4Reflect.seatCraftId(p))) return true;
        }
        return false;
    }

    private void skidProblems(List<String> out) {
        if (setup.preset != SteerPreset.SKID) return;
        Map<BlockPos, int[]> sides = new LinkedHashMap<>();
        for (int i = 0; i < scan.wheels().size(); i++) {
            Vcc4Scanner.Wheel w = scan.wheels().get(i);
            if (specOf[i] < 0) continue;
            BlockPos g = scan.governorFor(w.pos());
            if (g == null || setup.governorExcluded(g.asLong())) continue;
            double lat = wheelSpecs.get(specOf[i]).lateral();
            int[] count = sides.computeIfAbsent(g, k -> new int[2]);
            if (lat < -1.0e-6) count[0]++;
            else if (lat > 1.0e-6) count[1]++;
        }
        List<BlockPos> governors = new ArrayList<>(scan.usedGovernors());
        List<String> both = new ArrayList<>();
        sides.forEach((g, count) -> {
            if (count[0] > 0 && count[1] > 0) both.add("G" + (governors.indexOf(g) + 1));
        });
        if (!both.isEmpty()) {
            out.add("Skid steer needs each side on its own governor: " + String.join(" ", both)
                    + (both.size() == 1 ? " turns" : " turn") + " wheels on both sides, so they cannot turn the vehicle.");
        }
    }

    @Nullable
    private static Control controlFor(Vcc4Snapshot.Jet j) {
        JetRole role = JetRole.byOrdinal(j.role());
        if (role.auxControl() != null) return role.auxControl();
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (job == JetMixer.Job.LIFT) return Control.LIFT;
        if (job == JetMixer.Job.SLIDE) return Control.SLIDE;
        return null;
    }

    private static String controlName(Control c) {
        return switch (c) {
            case THROTTLE -> "Throttle";
            case STEER -> "Steer";
            case BRAKE -> "Brake";
            case BOOST -> "Boost";
            case LIFT -> "Lift";
            case SLIDE -> "Slide";
            case AUX_A -> "Aux A";
            case AUX_B -> "Aux B";
            case AUX_C -> "Aux C";
            case AUX_D -> "Aux D";
            case BEAM -> "Full beam";
            case TRIM -> "Trim";
        };
    }

    private String statusLine(DriveModel.Output out) {
        StringBuilder line = new StringBuilder("VCC  ").append(out.gear())
                .append(String.format(Locale.ROOT, "  %.1f b/s", forwardSpeed))
                .append("  drive ").append(pct(out.throttle()))
                .append("  steer ").append(pct(out.steer()));
        if (out.boosting()) line.append("  BOOST");
        double lift = controlValue[Control.LIFT.ordinal()];
        if (lift != 0.0) line.append("  lift ").append(pct(lift));
        for (Control c : new Control[]{Control.SLIDE, Control.AUX_A, Control.AUX_B, Control.AUX_C, Control.AUX_D}) {
            double v = controlValue[c.ordinal()];
            if (v != 0.0) line.append("  ").append(controlName(c)).append(v > 0 ? " +" : " -");
        }
        int running = 0;
        for (double v : machineOutput.values()) if (v != 0.0) running++;
        if (running > 0) line.append("  ").append(running).append(running == 1 ? " machine" : " machines");
        String alert = train.alert();
        if (alert != null) line.append("  ").append(alert);
        if (route.assigned() || route.recording()) line.append("  | ").append(route.statusLine());
        if (!runtimeProblems.isEmpty()) line.append("  | ").append(runtimeProblems.get(0));
        return line.toString();
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%+d%%", Math.round(v * 100.0));
    }

    private String profileLine() {
        int wheels = 0;
        int driven = 0;
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            if (w.body() != 0) continue;
            wheels++;
            if (scan.governorFor(w.pos()) != null) driven++;
        }
        String steering = switch (setup.preset) {
            case FRONT -> "front steer";
            case REAR -> "rear steer";
            case TWIN_FRONT -> "twin front steer";
            case FRONT_REAR -> "front and rear steer";
            case ALL -> "all-wheel steer";
            case CRAB -> "crab steer";
            case SKID -> "skid steer";
            case NONE -> articulated ? "joint steer" : "no wheel steers";
            case CUSTOM -> "custom steering";
        };
        StringBuilder line = new StringBuilder();
        if (wheels == 0) line.append(scan.props().isEmpty() ? "no wheels" : "jets drive");
        else line.append(wheels).append('x').append(driven).append(", ").append(steering);
        if (setup.presetAuto && wheels > 0) line.append(" (auto)");
        long jointed = scan.bodies().stream().filter(b -> b.index() > 0 && train.roleNow(b.index()) == BodyRole.PART).count();
        long trailers = scan.bodies().stream().filter(b -> train.takesMode(b.index())).count();
        if (articulated) line.append(", articulated");
        else if (jointed > 0) line.append(", + ").append(jointed).append(jointed == 1 ? " jointed body" : " jointed bodies");
        if (trailers > 0) line.append(", + ").append(trailers).append(trailers == 1 ? " trailer" : " trailers");
        return line.toString();
    }

    private List<String> reportLines() {
        List<String> lines = new ArrayList<>();
        if (!scan.onCraft()) {
            lines.addAll(scan.problems());
            return lines;
        }
        lines.add(profileLine());
        long drivenWheels = scan.wheels().stream().filter(w -> scan.governorFor(w.pos()) != null).count();
        lines.add(String.format(Locale.ROOT, "%d wheel(s) on %d axle(s), %d driven; %d governor(s); %d jet(s)",
                scan.wheels().size(), axles.size(), drivenWheels, scan.usedGovernors().size(), scan.props().size()));
        if (!scan.swivels().isEmpty() || !scan.machines().isEmpty() || !scan.links().isEmpty()) {
            lines.add(String.format(Locale.ROOT, "%d swivel bearing(s), %d machine(s), %d light(s)",
                    scan.swivels().size(), scan.machines().size(), scan.links().size()));
        }
        if (!scan.props().isEmpty()) {
            int[] jobs = new int[JetMixer.Job.values().length];
            for (Vcc4Scanner.Prop p : scan.props()) {
                JetMixer.Job job = JetMixer.jobOf(jetSpec(p));
                if (job != null) jobs[job.ordinal()]++;
            }
            lines.add(String.format(Locale.ROOT, "Jets: %d push, %d turn, %d lift, %d slide", jobs[0], jobs[1], jobs[2], jobs[3]));
        }
        if (driver == null && parked) lines.add("Parked: the park brake holds the wheels");
        String towing = train.towingLine();
        if (!towing.isEmpty()) lines.add(towing);
        if (!train.towedBy().isEmpty()) lines.add("Towed by " + train.towedBy() + ": that vehicle drives these wheels, the park brake is off");
        String cost = telemetry.costLine();
        if (!cost.isEmpty()) lines.add(cost);
        if (route.assigned() || route.recording() || route.recordArmed()) lines.add(route.gogglesLine());
        int shown = 0;
        for (String problem : scan.problems()) {
            if (shown++ >= 4) break;
            lines.add("! " + problem);
        }
        return lines;
    }

    @Override
    public GroundSnapshot groundSnapshot() {
        return telemetry.snapshot();
    }

    @Override
    public boolean onGroundCommand(GroundCommand command) {
        boolean done = level != null && telemetry.command(command, level.getGameTime());
        if (done) setChanged();
        return done;
    }

    boolean parked() {
        return parked;
    }

    boolean towed() {
        return towed;
    }

    boolean comMeasured() {
        return comMeasured;
    }

    boolean articulated() {
        return articulated;
    }

    boolean jetDriveNow() {
        return jetDrive();
    }

    long helmTick() {
        return helmTick;
    }

    boolean helmStill() {
        return helmStill;
    }

    String profile() {
        return profileLine();
    }

    Vcc4Train train() {
        return train;
    }

    Vcc4Links links() {
        return links;
    }

    List<AxleLayout.Axle> axles() {
        return axles;
    }

    int axleOfWheel(int i) {
        int spec = i >= 0 && i < specOf.length ? specOf[i] : -1;
        if (spec < 0) return -1;
        int[] axleOf = AxleLayout.axleOf(wheelSpecs.size(), axles);
        return spec < axleOf.length ? axleOf[spec] : -1;
    }

    @Nullable
    Double learnedRatio(long wheel) {
        return wheelRatio.get(wheel);
    }

    @Nullable
    Double wheelAngleOf(long wheel) {
        return wheelAngle.get(wheel);
    }

    double jetCommandOf(long prop) {
        return jetCommand.getOrDefault(prop, 0.0);
    }

    boolean jetForeign(long prop) {
        return foreignJets.contains(prop);
    }

    JetMixer.Jet jetSpecOf(Vcc4Scanner.Prop p) {
        return jetSpec(p);
    }

    PartInput inputFor(long pos) {
        return inputOf(pos);
    }

    double machineOutputOf(long governor) {
        return machineOutput.getOrDefault(governor, 0.0);
    }

    int runningMachines() {
        int running = 0;
        for (double v : machineOutput.values()) if (v != 0.0) running++;
        return running;
    }

    boolean governorForeign(BlockPos governor) {
        return foreignGovernors.contains(governor);
    }

    boolean governorExcluded(BlockPos governor) {
        return setup.governorExcluded(governor.asLong()) || train.handsOff().contains(governor);
    }

    boolean governorStarved(BlockPos governor) {
        return starved.getOrDefault(governor, 0) >= STARVED_TICKS;
    }

    float[] controlValues() {
        float[] out = new float[controlValue.length];
        for (int i = 0; i < controlValue.length; i++) out[i] = (float) controlValue[i];
        return out;
    }

    LinkLogic.Signals signalsNow(long now) {
        return signals(now);
    }

    List<String> displayProblems() {
        List<String> out = new ArrayList<>();
        if (!SableBridge.isAvailable()) out.add("Sable physics not found: nothing on this craft can be driven.");
        out.addAll(runtimeProblems);
        train.problems(out);
        out.addAll(scan.problems());
        skidProblems(out);
        if (!Vcc4Reflect.brakeMixinApplied()) out.add("Brakes unavailable: the wheel mount brake hook did not load (Offroad version?).");
        return out;
    }

    public Vcc4Route route() {
        return route;
    }

    private void setRouteDriving(boolean on) {
        if (on == routeDriving) return;
        routeDriving = on;
        if (on) {
            parked = false;
            towed = false;

            route.takeOver(lastOutput);
            UUID craft = craftId();
            if (craft != null) DRIVEN_CRAFTS.put(craft, new WeakReference<>(this));
            return;
        }
        if (driver == null && level instanceof ServerLevel server) release(server, null, null);
    }

    @Override
    public ServerLevel routeLevel() {
        return (ServerLevel) level;
    }

    @Override
    public UUID consoleId() {
        return controllerId;
    }

    @Override
    public BlockPos consolePos() {
        return worldPosition;
    }

    @Override
    public BlockEntity consoleEntity() {
        return this;
    }

    @Nullable
    @Override
    public Object craft() {
        return SableBridge.subLevelAccessOf(this);
    }

    @Nullable
    @Override
    public Object serverSubLevel() {
        return SableBridge.serverSubLevelOf(this);
    }

    @Override
    public Vector3d referenceLocal() {
        double pivot = axles.isEmpty() ? 0.0 : GroundSteer.pivot(axles, setup.rolesFor(axles.size()));
        Vector3d ref = Vcc4Scanner.centre(worldPosition).fma(pivot, nose());
        double tyres = Double.POSITIVE_INFINITY;
        for (Vcc4Scanner.Wheel w : scan.wheels()) if (w.body() == 0) tyres = Math.min(tyres, w.contact().y);
        if (Double.isFinite(tyres)) ref.y = tyres - 0.5;
        return ref;
    }

    @Override
    public Vector3d noseLocal() {
        return nose();
    }

    @Override
    public Vector3d rightLocal() {
        return right();
    }

    @Override
    public double kappaPerStick(double lockDeg) {
        if (wheelSpecs.isEmpty()) return 0.0;
        double[] c = GroundSteer.turnCentre(wheelSpecs, axles, setup.rolesFor(axles.size()), setup.preset, 1.0, lockDeg);
        return c == null || Math.abs(c[0]) < 1.0e-6 ? 0.0 : 1.0 / Math.abs(c[0]);
    }

    @Override
    public double lockAt(double speed) {
        DriveModel.Options o = setup.options.sane();
        double lock = o.lockDeg();
        if (o.speedSteer()) {
            double f = 1.0 - (Math.abs(speed) - DriveModel.SPEED_STEER_FROM) / (DriveModel.SPEED_STEER_TO - DriveModel.SPEED_STEER_FROM);
            lock *= clamp(f, DriveModel.SPEED_STEER_FLOOR, 1.0);
        }
        return lock;
    }

    @Override
    public boolean skidSteer() {
        return setup.preset == SteerPreset.SKID;
    }

    @Override
    public boolean returnReady() {
        return scan.onCraft() && craftId() != null && !scan.wheels().isEmpty();
    }

    @Override
    public String cannotFollow() {
        if (!scan.onCraft()) return "not on a vehicle";
        if (scan.wheels().isEmpty()) return "no wheels: routes are driven on wheels";
        if (jetDrive()) return "its jets drive it: routes need driven wheels";
        if (setup.preset == SteerPreset.CRAB) return "crab steering cannot follow a line: pick another on the Wheels page";
        if (!skidSteer() && !articulated && kappaPerStick(setup.options.lockDeg()) <= 0.0) return "no wheel steers";
        return "";
    }

    @Override
    public boolean jointSteered() {
        return articulated;
    }

    @Override
    public double halfTrack() {
        double w = 0.0;
        for (AxleLayout.WheelSpec s : wheelSpecs) w = Math.max(w, Math.abs(s.lateral()));
        return Math.max(0.5, w);
    }

    @Override
    public double measuredSpeed() {
        return measureForwardSpeed();
    }

    @Override
    public DriveModel.Output appliedOutput() {
        return lastOutput;
    }

    @Override
    public boolean calibrated() {
        for (BlockPos g : scan.usedGovernors()) {
            if (!setup.governorExcluded(g.asLong()) && needsCalibration(g)) return false;
        }
        return true;
    }

    @Override
    public void armCalibration() {
        if (calibrationTicks <= 0) calibrationTicks = CALIBRATION_TICKS;
    }

    @Override
    public double wheelDrive() {
        if (!(level instanceof ServerLevel server) || scan.wheels().isEmpty()) return Double.NaN;
        double sum = 0.0;
        int n = 0;
        for (Vcc4Scanner.Wheel w : scan.wheels()) {
            if (w.body() != 0) continue;
            if (SableBridge.getBlockEntityOnContraption(server, w.pos()) instanceof KineticBlockEntity mount) {
                sum += Math.abs(mount.getSpeed());
                n++;
            }
        }
        int max = setup.options.sane().maxRpm();
        return n == 0 || max <= 0 ? Double.NaN : sum / n / max;
    }

    @Override
    public void wake() {
        if (level instanceof ServerLevel server) Vcc4Reflect.wake(server, SableBridge.serverSubLevelOf(this));
    }

    @Override
    public int maxRpm() {
        return setup.options.maxRpm();
    }

    @Override
    public String vehicleName() {
        Object craft = SableBridge.subLevelAccessOf(this);
        if (craft == null) return "Mk IV";
        String name = SableBridge.subLevelName(craft);
        return name == null || name.isBlank() ? "Mk IV" : name;
    }

    @Override
    public List<UUID> ownBodies() {
        List<UUID> out = new ArrayList<>();
        UUID own = craftId();
        if (own != null) out.add(own);
        for (Vcc4Scanner.Body b : scan.bodies()) if (b.id() != null && !out.contains(b.id())) out.add(b.id());
        return out;
    }

    @Override
    public List<BlockEntity> cargoBlocks() {
        if (!cargoFresh && level instanceof ServerLevel server) {
            cargoBlocks = cargoOf(server);
            cargoFresh = true;
        }
        return cargoBlocks;
    }

    @Override
    public boolean seatedDriver() {
        return driver != null;
    }

    @Override
    public void routeChanged() {
        setChanged();
        publish();
    }

    private void publish() {
        syncDriver = "";
        if (driver != null && level instanceof ServerLevel server) {
            ServerPlayer d = server.getServer().getPlayerList().getPlayer(driver);
            syncDriver = d == null ? "?" : d.getGameProfile().getName();
            if (coDriver != null) {
                ServerPlayer co = server.getServer().getPlayerList().getPlayer(coDriver);
                syncDriver += " + " + (co == null ? "?" : co.getGameProfile().getName());
            }
        }
        syncLines = List.copyOf(reportLines());
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_stats.vcc_mk4"))
                .withStyle(ChatFormatting.WHITE));
        String who = syncDriver.isEmpty() ? "nobody. Sit in a seat within 2 blocks of the console to drive" : syncDriver;
        tooltip.add(Component.literal("    Driver: " + who).withStyle(syncDriver.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        for (String line : syncLines) {
            tooltip.add(Component.literal("    " + line).withStyle(line.startsWith("!") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return true;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        if (level instanceof ServerLevel server) route.unloaded(server);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        try {
            if (level instanceof ServerLevel server) {
                if (!unloading) route.removed(server);
                if (driver != null) {
                    ServerPlayer d = server.getServer().getPlayerList().getPlayer(driver);
                    release(server, d, "The console was removed.");
                }
                links.removeAll(server);
            }
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            CreateStats.LOGGER.error("[VCC Mk IV] console at {}: hand-back on removal failed", worldPosition, e);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("ControllerId", controllerId);
        tag.put("Setup", Vcc4Nbt.write(setup));
        tag.put("WheelRatio", Vcc4Nbt.doubles(wheelRatio));
        tag.put("PropSign", Vcc4Nbt.ints(propSign));
        tag.put("AuxAngle", Vcc4Nbt.doubles(auxAngle));
        tag.put("PropGain", Vcc4Nbt.doubles(propGain));
        tag.put("Couplers", Vcc4Nbt.couplers(links.couplerStates(setup)));

        tag.putByteArray(GROUND_RECORDER, telemetry.saveRecorder());

        tag.put(ROUTE, route.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("ControllerId")) controllerId = tag.getUUID("ControllerId");
        Vcc4Nbt.read(tag.getCompound("Setup"), setup);
        wheelRatio.clear();
        wheelRatio.putAll(Vcc4Nbt.readDoubles(tag.getCompound("WheelRatio")));
        propSign.clear();
        propSign.putAll(Vcc4Nbt.readInts(tag.getCompound("PropSign")));
        auxAngle.clear();
        auxAngle.putAll(Vcc4Nbt.readDoubles(tag.getCompound("AuxAngle")));
        propGain.clear();
        propGain.putAll(Vcc4Nbt.readDoubles(tag.getCompound("PropGain")));
        links.restoreCouplers(Vcc4Nbt.readCouplers(tag.getCompound("Couplers")));
        if (tag.contains(GROUND_RECORDER, Tag.TAG_BYTE_ARRAY)) telemetry.loadRecorder(tag.getByteArray(GROUND_RECORDER));
        if (tag.contains(ROUTE, Tag.TAG_COMPOUND)) route.load(tag.getCompound(ROUTE));
        auxAxes.clear();
        if (tag.contains("SyncLines")) {
            syncDriver = tag.getString("SyncDriver");
            List<String> lines = new ArrayList<>();
            for (Tag t : tag.getList("SyncLines", Tag.TAG_STRING)) lines.add(t.getAsString());
            syncLines = List.copyOf(lines);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);

        tag.remove(GROUND_RECORDER);
        tag.remove(ROUTE);
        tag.putString("SyncDriver", syncDriver);
        ListTag lines = new ListTag();
        for (String line : syncLines) lines.add(StringTag.valueOf(line));
        tag.put("SyncLines", lines);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
