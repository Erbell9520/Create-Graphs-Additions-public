package com.createstats.vccmk4.network;

import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

// flat record, the client may not even have the craft's chunk
public record Vcc4Snapshot(
        boolean onCraft,
        boolean youDrive,
        String driver,
        int nose,
        float throttle,
        float steer,
        boolean braking,
        boolean boosting,
        boolean holding,
        boolean parked,
        char gear,
        float speed,
        int preset,
        boolean presetAuto,
        int[] roles,
        boolean[] driven,
        float[] axleZ,
        List<Wheel> wheels,
        List<Governor> governors,
        List<Jet> jets,
        List<Swivel> swivels,
        List<Machine> machines,
        List<Link> links,
        List<Body> bodies,
        List<Tie> ties,
        float comLateral,
        float comLongitudinal,
        boolean comMeasured,
        int[] keymap,
        int[] held,
        float[] controlValues,
        DriveModel.Options options,
        boolean jetsSteerWithoutBoost,
        boolean liftHold,
        boolean liftHoldAuto,
        boolean jetDrive,
        List<String> problems,
        boolean brakesAvailable,
        boolean greeting,
        java.util.UUID console,
        RouteSnapshot route) {
    private static final int MAX_LIST = 512;
    private static final int MAX_TEXT = 256;
    private static final int MAX_NAME = 128;

    public record Wheel(long pos, float lateral, float longitudinal, int axle, float angle, boolean commanded,
                        int governor, float rpm, int body) {
    }

    public record Governor(long pos, float lateral, float longitudinal, int applied, int commanded, boolean flipped,
                           boolean excluded, boolean foreign, int wheels, int jets, float ratio) {
    }

    public record Jet(long pos, float lateral, float longitudinal, float axisRight, float axisUp, float axisForward,
                      float armRight, float armForward, int role, int job, int governor, float rpm, float command,
                      boolean inverted, boolean signKnown, boolean sharesWheels, int body,
                      int kind, float force, float ceiling, float clear, int supply, float nozzleX, float nozzleY,
                      boolean foreign) {
        public Jet(long pos, float lateral, float longitudinal, float axisRight, float axisUp, float axisForward,
                   float armRight, float armForward, int role, int job, int governor, float rpm, float command,
                   boolean inverted, boolean signKnown, boolean sharesWheels, int body) {
            this(pos, lateral, longitudinal, axisRight, axisUp, axisForward, armRight, armForward, role, job, governor, rpm,
                    command, inverted, signKnown, sharesWheels, body, 0, 0f, 0f, 1f, 0, 0f, 0f, false);
        }

        public JetKind kindOf() {
            return JetKind.byOrdinal(kind);
        }

        public boolean direct() {
            return kindOf().direct();
        }

        public JetSupply supplyOf() {
            return JetSupply.byOrdinal(supply);
        }
    }

    public record Swivel(long pos, float lateral, float longitudinal, int input, float angle, int travel,
                         boolean latched, boolean inverted, boolean ready, int body, int carries, boolean assembled,
                         boolean locked) {
    }

    public record Machine(long pos, float lateral, float longitudinal, int body, int input, int rpm, boolean latched,
                          boolean inverted, float output, int applied, boolean foreign, boolean runner,
                          List<String> drives) {
    }

    public record Link(long pos, float lateral, float longitudinal, int body, int source, boolean toggle, int strength,
                       String first, String second, boolean coupler, int lamp, boolean proposed) {
        public Link(long pos, float lateral, float longitudinal, int body, int source, boolean toggle, int strength,
                    String first, String second, boolean coupler) {
            this(pos, lateral, longitudinal, body, source, toggle, strength, first, second, coupler, 0, false);
        }

        public Link(long pos, float lateral, float longitudinal, int body, int source, boolean toggle, int strength,
                    String first, String second) {
            this(pos, lateral, longitudinal, body, source, toggle, strength, first, second, false);
        }

        public int lampKind() {
            return lamp - 1;
        }
    }

    public record Body(int index, int parent, float lateral, float longitudinal, boolean locked, boolean foreign,
                       boolean placed, int wheels, float yaw, int role, int link, int mode, long idLow, float reading,
                       float force, float drive, int brake, float steer, float share, int flags) {
        public Body(int index, int parent, float lateral, float longitudinal, boolean locked, boolean foreign,
                    boolean placed, int wheels, float yaw) {
            this(index, parent, lateral, longitudinal, locked, foreign, placed, wheels, yaw,
                    foreign ? ROLE_TOWED : ROLE_PART, locked ? 1 : 0, 0, 0L, 0f, 0f, 0f, 0, 0f, -1f, 0);
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        public BodyRole roleOf() {
            return BodyRole.byOrdinal(role);
        }

        public boolean takesMode() {
            return roleOf().takesMode();
        }
    }

    public static final int ROLE_PART = BodyRole.PART.ordinal();
    public static final int ROLE_TRAILER = BodyRole.TRAILER.ordinal();
    public static final int ROLE_TOWED = BodyRole.TOWED.ordinal();
    public static final int ROLE_SEPARATE = BodyRole.SEPARATE.ordinal();
    public static final int ROLE_LOAD = BodyRole.LOAD.ordinal();

    public static final int FLAG_SWAYING = 1;
    public static final int FLAG_SNAPPING = 2;

    public static final int FLAG_POWERED = 4;
    public static final int FLAG_DOCKING = 8;

    public static final int FLAG_OVERRUN = 16;

    public static final int FLAG_LOST = 32;

    public record Tie(int kind, int a, float latA, float lonA, int b, float latB, float lonB, float reading) {
    }

    public static final Vcc4Snapshot EMPTY = new Builder().build();

    public HeldKeys heldKeys() {
        return HeldKeys.of(held);
    }

    public static final class Builder {
        public boolean onCraft;
        public boolean youDrive;
        public String driver = "";
        public int nose;
        public float throttle;
        public float steer;
        public boolean braking;
        public boolean boosting;
        public boolean holding;
        public boolean parked;
        public char gear = 'N';
        public float speed;
        public int preset;
        public boolean presetAuto;
        public int[] roles = new int[0];
        public boolean[] driven = new boolean[0];
        public float[] axleZ = new float[0];
        public List<Wheel> wheels = List.of();
        public List<Governor> governors = List.of();
        public List<Jet> jets = List.of();
        public List<Swivel> swivels = List.of();
        public List<Machine> machines = List.of();
        public List<Link> links = List.of();
        public List<Body> bodies = List.of();
        public List<Tie> ties = List.of();
        public float comLateral;
        public float comLongitudinal;
        public boolean comMeasured;
        public int[] keymap = new int[0];
        public int[] held = new int[0];
        public float[] controlValues = new float[0];
        public DriveModel.Options options = DriveModel.Options.DEFAULT;
        public boolean jetsSteerWithoutBoost;
        public boolean liftHold;
        public boolean liftHoldAuto = true;
        public boolean jetDrive;
        public List<String> problems = List.of();
        public boolean brakesAvailable = true;
        public boolean greeting = true;
        public java.util.UUID console = new java.util.UUID(0L, 0L);
        public RouteSnapshot route = RouteSnapshot.NONE;

        public Builder() {
        }

        public Builder(Vcc4Snapshot s) {
            onCraft = s.onCraft;
            youDrive = s.youDrive;
            driver = s.driver;
            nose = s.nose;
            throttle = s.throttle;
            steer = s.steer;
            braking = s.braking;
            boosting = s.boosting;
            holding = s.holding;
            parked = s.parked;
            gear = s.gear;
            speed = s.speed;
            preset = s.preset;
            presetAuto = s.presetAuto;
            roles = s.roles;
            driven = s.driven;
            axleZ = s.axleZ;
            wheels = s.wheels;
            governors = s.governors;
            jets = s.jets;
            swivels = s.swivels;
            machines = s.machines;
            links = s.links;
            bodies = s.bodies;
            ties = s.ties;
            comLateral = s.comLateral;
            comLongitudinal = s.comLongitudinal;
            comMeasured = s.comMeasured;
            keymap = s.keymap;
            held = s.held;
            controlValues = s.controlValues;
            options = s.options;
            jetsSteerWithoutBoost = s.jetsSteerWithoutBoost;
            liftHold = s.liftHold;
            liftHoldAuto = s.liftHoldAuto;
            jetDrive = s.jetDrive;
            problems = s.problems;
            brakesAvailable = s.brakesAvailable;
            greeting = s.greeting;
            console = s.console;
            route = s.route;
        }

        public Vcc4Snapshot build() {
            return new Vcc4Snapshot(onCraft, youDrive, driver == null ? "" : driver, nose, throttle, steer, braking,
                    boosting, holding, parked, gear, speed, preset, presetAuto, roles, driven, axleZ, List.copyOf(wheels),
                    List.copyOf(governors), List.copyOf(jets), List.copyOf(swivels), List.copyOf(machines),
                    List.copyOf(links), List.copyOf(bodies), List.copyOf(ties), comLateral, comLongitudinal, comMeasured, keymap, held,
                    controlValues, options == null ? DriveModel.Options.DEFAULT : options, jetsSteerWithoutBoost, liftHold,
                    liftHoldAuto, jetDrive, List.copyOf(problems), brakesAvailable, greeting,
                    console == null ? new java.util.UUID(0L, 0L) : console, route == null ? RouteSnapshot.NONE : route);
        }
    }

    public static void write(FriendlyByteBuf buf, Vcc4Snapshot s) {
        buf.writeBoolean(s.onCraft);
        buf.writeBoolean(s.youDrive);
        buf.writeUtf(cut(s.driver, 64), 64);
        buf.writeVarInt(s.nose);
        buf.writeFloat(s.throttle);
        buf.writeFloat(s.steer);
        buf.writeBoolean(s.braking);
        buf.writeBoolean(s.boosting);
        buf.writeBoolean(s.holding);
        buf.writeBoolean(s.parked);
        buf.writeVarInt(s.gear);
        buf.writeFloat(s.speed);
        buf.writeVarInt(s.preset);
        buf.writeBoolean(s.presetAuto);
        buf.writeVarIntArray(s.roles);
        buf.writeVarInt(s.driven.length);
        for (boolean b : s.driven) buf.writeBoolean(b);
        writeFloats(buf, s.axleZ);
        buf.writeVarInt(s.wheels.size());
        for (Wheel w : s.wheels) {
            buf.writeLong(w.pos());
            buf.writeFloat(w.lateral());
            buf.writeFloat(w.longitudinal());
            buf.writeVarInt(w.axle() + 1);
            buf.writeFloat(w.angle());
            buf.writeBoolean(w.commanded());
            buf.writeVarInt(w.governor() + 1);
            buf.writeFloat(w.rpm());
            buf.writeVarInt(w.body());
        }
        buf.writeVarInt(s.governors.size());
        for (Governor g : s.governors) {
            buf.writeLong(g.pos());
            buf.writeFloat(g.lateral());
            buf.writeFloat(g.longitudinal());
            buf.writeInt(g.applied());
            buf.writeInt(g.commanded());
            buf.writeBoolean(g.flipped());
            buf.writeBoolean(g.excluded());
            buf.writeBoolean(g.foreign());
            buf.writeVarInt(g.wheels());
            buf.writeVarInt(g.jets());
            buf.writeFloat(g.ratio());
        }
        buf.writeVarInt(s.jets.size());
        for (Jet j : s.jets) {
            buf.writeLong(j.pos());
            buf.writeFloat(j.lateral());
            buf.writeFloat(j.longitudinal());
            buf.writeFloat(j.axisRight());
            buf.writeFloat(j.axisUp());
            buf.writeFloat(j.axisForward());
            buf.writeFloat(j.armRight());
            buf.writeFloat(j.armForward());
            buf.writeVarInt(j.role());
            buf.writeVarInt(j.job() + 1);
            buf.writeVarInt(j.governor() + 1);
            buf.writeFloat(j.rpm());
            buf.writeFloat(j.command());
            buf.writeBoolean(j.inverted());
            buf.writeBoolean(j.signKnown());
            buf.writeBoolean(j.sharesWheels());
            buf.writeVarInt(j.body());
            buf.writeVarInt(j.kind());
            buf.writeFloat(j.force());
            buf.writeFloat(j.ceiling());
            buf.writeFloat(j.clear());
            buf.writeVarInt(j.supply());
            buf.writeFloat(j.nozzleX());
            buf.writeFloat(j.nozzleY());
            buf.writeBoolean(j.foreign());
        }
        buf.writeVarInt(s.swivels.size());
        for (Swivel a : s.swivels) {
            buf.writeLong(a.pos());
            buf.writeFloat(a.lateral());
            buf.writeFloat(a.longitudinal());
            buf.writeVarInt(a.input());
            buf.writeFloat(a.angle());
            buf.writeVarInt(a.travel());
            buf.writeBoolean(a.latched());
            buf.writeBoolean(a.inverted());
            buf.writeBoolean(a.ready());
            buf.writeVarInt(a.body());
            buf.writeVarInt(a.carries() + 1);
            buf.writeBoolean(a.assembled());
            buf.writeBoolean(a.locked());
        }
        buf.writeVarInt(s.machines.size());
        for (Machine m : s.machines) {
            buf.writeLong(m.pos());
            buf.writeFloat(m.lateral());
            buf.writeFloat(m.longitudinal());
            buf.writeVarInt(m.body());
            buf.writeVarInt(m.input());
            buf.writeVarInt(m.rpm());
            buf.writeBoolean(m.latched());
            buf.writeBoolean(m.inverted());
            buf.writeFloat(m.output());
            buf.writeInt(m.applied());
            buf.writeBoolean(m.foreign());
            buf.writeBoolean(m.runner());
            buf.writeVarInt(Math.min(8, m.drives().size()));
            for (int i = 0; i < Math.min(8, m.drives().size()); i++) buf.writeUtf(cut(m.drives().get(i), MAX_NAME), MAX_NAME);
        }
        buf.writeVarInt(s.links.size());
        for (Link l : s.links) {
            buf.writeLong(l.pos());
            buf.writeFloat(l.lateral());
            buf.writeFloat(l.longitudinal());
            buf.writeVarInt(l.body());
            buf.writeVarInt(l.source());
            buf.writeBoolean(l.toggle());
            buf.writeVarInt(l.strength());
            buf.writeUtf(cut(l.first(), MAX_NAME), MAX_NAME);
            buf.writeUtf(cut(l.second(), MAX_NAME), MAX_NAME);
            buf.writeBoolean(l.coupler());
            buf.writeVarInt(l.lamp());
            buf.writeBoolean(l.proposed());
        }
        buf.writeVarInt(s.bodies.size());
        for (Body b : s.bodies) {
            buf.writeVarInt(b.index());
            buf.writeVarInt(b.parent() + 1);
            buf.writeFloat(b.lateral());
            buf.writeFloat(b.longitudinal());
            buf.writeBoolean(b.locked());
            buf.writeBoolean(b.foreign());
            buf.writeBoolean(b.placed());
            buf.writeVarInt(b.wheels());
            buf.writeFloat(b.yaw());
            buf.writeVarInt(b.role());
            buf.writeVarInt(b.link());
            buf.writeVarInt(b.mode());
            buf.writeLong(b.idLow());
            buf.writeFloat(b.reading());
            buf.writeFloat(b.force());
            buf.writeFloat(b.drive());
            buf.writeVarInt(b.brake());
            buf.writeFloat(b.steer());
            buf.writeFloat(b.share());
            buf.writeVarInt(b.flags());
        }
        buf.writeVarInt(s.ties.size());
        for (Tie t : s.ties) {
            buf.writeVarInt(t.kind());
            buf.writeVarInt(t.a() + 1);
            buf.writeFloat(t.latA());
            buf.writeFloat(t.lonA());
            buf.writeVarInt(t.b() + 1);
            buf.writeFloat(t.latB());
            buf.writeFloat(t.lonB());
            buf.writeFloat(t.reading());
        }
        buf.writeFloat(s.comLateral);
        buf.writeFloat(s.comLongitudinal);
        buf.writeBoolean(s.comMeasured);
        buf.writeVarIntArray(s.keymap);
        buf.writeVarIntArray(s.held);
        writeFloats(buf, s.controlValues);
        DriveModel.Options o = s.options;
        buf.writeBoolean(o.brakeThenReverse());
        buf.writeBoolean(o.speedSteer());
        buf.writeVarInt(o.steerTicks());
        buf.writeVarInt(o.lockDeg());
        buf.writeVarInt(o.maxRpm());
        buf.writeVarInt(o.reversePercent());
        buf.writeVarInt(o.throttleTicks());
        buf.writeVarInt(o.releaseTicks());
        buf.writeBoolean(o.holdWhenStopped());
        buf.writeBoolean(o.parkBrake());
        buf.writeBoolean(s.jetsSteerWithoutBoost);
        buf.writeBoolean(s.liftHold);
        buf.writeBoolean(s.liftHoldAuto);
        buf.writeBoolean(s.jetDrive);
        buf.writeVarInt(Math.min(64, s.problems.size()));
        for (int i = 0; i < Math.min(64, s.problems.size()); i++) buf.writeUtf(cut(s.problems.get(i), MAX_TEXT), MAX_TEXT);
        buf.writeBoolean(s.brakesAvailable);
        buf.writeBoolean(s.greeting);
        buf.writeUUID(s.console);
        RouteSnapshot.write(buf, s.route);
    }

    public static Vcc4Snapshot read(FriendlyByteBuf buf) {
        Builder b = new Builder();
        b.onCraft = buf.readBoolean();
        b.youDrive = buf.readBoolean();
        b.driver = buf.readUtf(64);
        b.nose = buf.readVarInt();
        b.throttle = buf.readFloat();
        b.steer = buf.readFloat();
        b.braking = buf.readBoolean();
        b.boosting = buf.readBoolean();
        b.holding = buf.readBoolean();
        b.parked = buf.readBoolean();
        b.gear = (char) buf.readVarInt();
        b.speed = buf.readFloat();
        b.preset = buf.readVarInt();
        b.presetAuto = buf.readBoolean();
        b.roles = buf.readVarIntArray(MAX_LIST);
        int nDriven = count(buf);
        b.driven = new boolean[nDriven];
        for (int i = 0; i < nDriven; i++) b.driven[i] = buf.readBoolean();
        b.axleZ = readFloats(buf);
        int nWheels = count(buf);
        List<Wheel> wheels = new ArrayList<>(nWheels);
        for (int i = 0; i < nWheels; i++) {
            wheels.add(new Wheel(buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1, buf.readFloat(),
                    buf.readBoolean(), buf.readVarInt() - 1, buf.readFloat(), buf.readVarInt()));
        }
        b.wheels = wheels;
        int nGov = count(buf);
        List<Governor> governors = new ArrayList<>(nGov);
        for (int i = 0; i < nGov; i++) {
            governors.add(new Governor(buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat()));
        }
        b.governors = governors;
        int nJets = count(buf);
        List<Jet> jets = new ArrayList<>(nJets);
        for (int i = 0; i < nJets; i++) {
            jets.add(new Jet(buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt() - 1,
                    buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        b.jets = jets;
        int nSwivels = count(buf);
        List<Swivel> swivels = new ArrayList<>(nSwivels);
        for (int i = 0; i < nSwivels; i++) {
            swivels.add(new Swivel(buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt() - 1, buf.readBoolean(), buf.readBoolean()));
        }
        b.swivels = swivels;
        int nMachines = count(buf);
        List<Machine> machines = new ArrayList<>(nMachines);
        for (int i = 0; i < nMachines; i++) {
            long pos = buf.readLong();
            float lat = buf.readFloat();
            float lon = buf.readFloat();
            int body = buf.readVarInt();
            int input = buf.readVarInt();
            int rpm = buf.readVarInt();
            boolean latched = buf.readBoolean();
            boolean inverted = buf.readBoolean();
            float output = buf.readFloat();
            int applied = buf.readInt();
            boolean foreign = buf.readBoolean();
            boolean runner = buf.readBoolean();
            int nNames = Math.max(0, Math.min(8, buf.readVarInt()));
            List<String> names = new ArrayList<>(nNames);
            for (int k = 0; k < nNames; k++) names.add(buf.readUtf(MAX_NAME));
            machines.add(new Machine(pos, lat, lon, body, input, rpm, latched, inverted, output, applied, foreign, runner,
                    List.copyOf(names)));
        }
        b.machines = machines;
        int nLinks = count(buf);
        List<Link> links = new ArrayList<>(nLinks);
        for (int i = 0; i < nLinks; i++) {
            links.add(new Link(buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt(), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), buf.readBoolean(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        b.links = links;
        int nBodies = count(buf);
        List<Body> bodies = new ArrayList<>(nBodies);
        for (int i = 0; i < nBodies; i++) {
            bodies.add(new Body(buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt()));
        }
        b.bodies = bodies;
        int nTies = count(buf);
        List<Tie> ties = new ArrayList<>(nTies);
        for (int i = 0; i < nTies; i++) {
            ties.add(new Tie(buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1,
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        b.ties = ties;
        b.comLateral = buf.readFloat();
        b.comLongitudinal = buf.readFloat();
        b.comMeasured = buf.readBoolean();
        b.keymap = buf.readVarIntArray(MAX_LIST);
        b.held = buf.readVarIntArray(HeldKeys.MAX);
        b.controlValues = readFloats(buf);
        b.options = new DriveModel.Options(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()).sane();
        b.jetsSteerWithoutBoost = buf.readBoolean();
        b.liftHold = buf.readBoolean();
        b.liftHoldAuto = buf.readBoolean();
        b.jetDrive = buf.readBoolean();
        int nProblems = Math.max(0, Math.min(64, buf.readVarInt()));
        List<String> problems = new ArrayList<>(nProblems);
        for (int i = 0; i < nProblems; i++) problems.add(buf.readUtf(MAX_TEXT));
        b.problems = problems;
        b.brakesAvailable = buf.readBoolean();
        b.greeting = buf.readBoolean();
        b.console = buf.readUUID();
        b.route = RouteSnapshot.read(buf);
        return b.build();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int count(FriendlyByteBuf buf) {
        return Math.max(0, Math.min(MAX_LIST, buf.readVarInt()));
    }

    private static void writeFloats(FriendlyByteBuf buf, float[] values) {
        buf.writeVarInt(values.length);
        for (float f : values) buf.writeFloat(f);
    }

    private static float[] readFloats(FriendlyByteBuf buf) {
        float[] out = new float[count(buf)];
        for (int i = 0; i < out.length; i++) out[i] = buf.readFloat();
        return out;
    }
}
