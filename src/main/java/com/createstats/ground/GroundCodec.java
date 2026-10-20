package com.createstats.ground;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Body;
import com.createstats.ground.GroundSnapshot.Drive;
import com.createstats.ground.GroundSnapshot.Envelope;
import com.createstats.ground.GroundSnapshot.Event;
import com.createstats.ground.GroundSnapshot.EventKind;
import com.createstats.ground.GroundSnapshot.Events;
import com.createstats.ground.GroundSnapshot.Fix;
import com.createstats.ground.GroundSnapshot.Governor;
import com.createstats.ground.GroundSnapshot.History;
import com.createstats.ground.GroundSnapshot.Identity;
import com.createstats.ground.GroundSnapshot.Jet;
import com.createstats.ground.GroundSnapshot.Lamp;
import com.createstats.ground.GroundSnapshot.Lights;
import com.createstats.ground.GroundSnapshot.Log;
import com.createstats.ground.GroundSnapshot.Machine;
import com.createstats.ground.GroundSnapshot.Mass;
import com.createstats.ground.GroundSnapshot.Odometer;
import com.createstats.ground.GroundSnapshot.Setup;
import com.createstats.ground.GroundSnapshot.Shape;
import com.createstats.ground.GroundSnapshot.Supply;
import com.createstats.ground.GroundSnapshot.Terrain;
import com.createstats.ground.GroundSnapshot.Tie;
import com.createstats.ground.GroundSnapshot.Trail;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;

// aerial's sectioned codec again
public final class GroundCodec {
    // bump on any layout change, mismatches are dropped not misread
    public static final int VERSION = 1;

    public enum Section {
        HEADER, BODY, DRIVE, WHEEL_STATE, GOVERNORS, JETS, UNITS, TIES, LIGHTS, MACHINES, ODOMETER,
        SETUP, MASS, WHEEL_GEOMETRY, PROBLEMS, TERRAIN, TRAIL, HISTORY, EVENTS, ENVELOPE, SHAPES;

        public int bit() {
            return 1 << ordinal();
        }

        public boolean fast() {
            return ordinal() <= ODOMETER.ordinal();
        }
    }

    public static final int ALL = (1 << Section.values().length) - 1;

    public static final int RECORDED = Section.HEADER.bit() | Section.ODOMETER.bit() | Section.TRAIL.bit()
            | Section.HISTORY.bit() | Section.EVENTS.bit() | Section.ENVELOPE.bit();

    private GroundCodec() {
    }

    public static byte[] encode(GroundSnapshot s) {
        return encode(s, ALL);
    }

    public static byte[] encode(GroundSnapshot s, int mask) {
        final Map<Section, byte[]> sections = new EnumMap<>(Section.class);
        for (final Section sec : Section.values()) {
            if ((mask & sec.bit()) != 0) {
                sections.put(sec, section(s, sec));
            }
        }
        return join(sections);
    }

    public static byte[] section(GroundSnapshot s, Section sec) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            switch (sec) {
                case HEADER -> writeHeader(out, s);
                case BODY -> writeBody(out, s.body(), s.log());
                case DRIVE -> writeDrive(out, s.drive());
                case WHEEL_STATE -> writeWheelState(out, s.wheels());
                case GOVERNORS -> writeGovernors(out, s.governors());
                case JETS -> writeJets(out, s.jets());
                case UNITS -> writeUnits(out, s.units());
                case TIES -> writeTies(out, s.ties());
                case LIGHTS -> writeLights(out, s.lights());
                case MACHINES -> writeMachines(out, s.machines());
                case ODOMETER -> writeOdometer(out, s.odometer());
                case SETUP -> writeSetup(out, s.setup());
                case MASS -> writeMass(out, s.mass());
                case WHEEL_GEOMETRY -> writeWheelGeometry(out, s.wheels());
                case PROBLEMS -> writeProblems(out, s.problems());
                case TERRAIN -> writeTerrain(out, s.terrain());
                case TRAIL -> writeTrail(out, s.trail());
                case HISTORY -> writeHistory(out, s.history());
                case EVENTS -> writeEvents(out, s.events());
                case ENVELOPE -> writeEnvelope(out, s.envelope());
                case SHAPES -> writeShapes(out, s.shapes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static byte[] join(Map<Section, byte[]> sections) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            int mask = 0;
            for (final Section sec : sections.keySet()) {
                mask |= sec.bit();
            }
            out.writeByte(VERSION);
            out.writeInt(mask);
            for (final Section sec : Section.values()) {
                final byte[] body = sections.get(sec);
                if (body == null) {
                    continue;
                }
                out.writeInt(body.length);
                out.write(body);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static int maskOf(byte[] data) {
        if (data == null || data.length < 5 || data[0] != VERSION) {
            return 0;
        }
        return ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
    }

    public static GroundSnapshot decode(byte[] data, GroundSnapshot previous) throws IOException {
        if (data == null || data.length < 5) {
            throw new IOException("ground packet too short");
        }
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        final int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("ground packet version " + version + ", expected " + VERSION);
        }
        final int mask = in.readInt();
        final GroundSnapshot base = previous != null ? previous : GroundSnapshot.empty(0L);

        long gameTime = base.gameTime();
        Identity identity = base.identity();
        Body body = base.body();
        Log log = base.log();
        Drive drive = base.drive();
        Setup setup = base.setup();
        Mass mass = base.mass();
        List<WheelGeometry> geometry = null;
        List<WheelState> state = null;
        List<Governor> governors = base.governors();
        List<Jet> jets = base.jets();
        List<Unit> units = base.units();
        List<Tie> ties = base.ties();
        Lights lights = base.lights();
        List<Machine> machines = base.machines();
        List<String> problems = base.problems();
        Terrain terrain = base.terrain();
        Odometer odometer = base.odometer();
        Trail trail = base.trail();
        History history = base.history();
        Events events = base.events();
        Envelope envelope = base.envelope();
        List<Shape> shapes = base.shapes();

        for (final Section sec : Section.values()) {
            if ((mask & sec.bit()) == 0) {
                continue;
            }
            final int length = in.readInt();
            if (length < 0 || length > in.available()) {
                throw new IOException("ground section " + sec + " claims " + length + " bytes");
            }
            final byte[] raw = new byte[length];
            in.readFully(raw);
            final ByteArrayInputStream bytes = new ByteArrayInputStream(raw);
            final DataInputStream s = new DataInputStream(bytes);
            switch (sec) {
                case HEADER -> {
                    gameTime = s.readLong();
                    identity = readIdentity(s);
                }
                case BODY -> {
                    body = readBody(s);
                    log = readLog(s);
                }
                case DRIVE -> drive = readDrive(s);
                case WHEEL_STATE -> state = readWheelState(s);
                case GOVERNORS -> governors = readGovernors(s);
                case JETS -> jets = readJets(s);
                case UNITS -> units = readUnits(s);
                case TIES -> ties = readTies(s);
                case LIGHTS -> lights = readLights(s);
                case MACHINES -> machines = readMachines(s);
                case ODOMETER -> odometer = readOdometer(s);
                case SETUP -> setup = readSetup(s);
                case MASS -> mass = readMass(s);
                case WHEEL_GEOMETRY -> geometry = readWheelGeometry(s);
                case PROBLEMS -> problems = readProblems(s);
                case TERRAIN -> terrain = readTerrain(s);
                case TRAIL -> trail = readTrail(s);
                case HISTORY -> history = readHistory(s);
                case EVENTS -> events = readEvents(s);
                case ENVELOPE -> envelope = readEnvelope(s);
                case SHAPES -> shapes = readShapes(s);
            }
            if (bytes.available() != 0) {
                throw new IOException("ground section " + sec + " left " + bytes.available() + " bytes unread");
            }
        }
        if (in.available() != 0) {
            throw new IOException("ground packet has " + in.available() + " trailing bytes");
        }
        final List<Wheel> wheels = joinWheels(geometry != null ? geometry : geometryOf(base.wheels()),
                state != null ? state : stateOf(base.wheels()));
        return new GroundSnapshot(gameTime, identity, body, log, drive, setup, mass, wheels, governors, jets, units,
                ties, lights, machines, problems, terrain, odometer, trail, history, events, envelope, shapes);
    }

    private static void writeHeader(DataOutputStream out, GroundSnapshot s) throws IOException {
        out.writeLong(s.gameTime());
        final Identity i = s.identity();
        out.writeUTF(i.name());
        uuid(out, i.vehicleId());
        out.writeUTF(i.profile());
        out.writeByte(i.kind().ordinal());
        out.writeInt(i.commands());
        out.writeBoolean(i.onCraft());
        out.writeBoolean(i.brakesAvailable());
        out.writeUTF(i.towedBy());
    }

    private static Identity readIdentity(DataInputStream in) throws IOException {
        return new Identity(in.readUTF(), uuid(in), in.readUTF(), VehicleKind.byOrdinal(in.readUnsignedByte()),
                in.readInt(), in.readBoolean(), in.readBoolean(), in.readUTF());
    }

    private static void writeBody(DataOutputStream out, Body b, Log l) throws IOException {
        v3(out, b.position());
        out.writeDouble(b.orientation().x());
        out.writeDouble(b.orientation().y());
        out.writeDouble(b.orientation().z());
        out.writeDouble(b.orientation().w());
        f3(out, b.nose());
        f3(out, b.velocity());
        out.writeFloat((float) b.yawRate());
        out.writeFloat((float) b.forwardSpeed());
        f3(out, b.accel());
        f3(out, b.gravity());
        out.writeFloat((float) l.peak());
        out.writeFloat((float) l.lastImpact());
        out.writeInt(l.impactAge());
        out.writeFloat((float) l.tripImpact());
        out.writeFloat((float) l.airNow());
        out.writeFloat((float) l.lastAir());
    }

    private static Body readBody(DataInputStream in) throws IOException {
        final V3 position = v3(in);
        final Quat q = new Quat(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
        return new Body(position, q, f3(in), f3(in), in.readFloat(), in.readFloat(), f3(in), f3(in));
    }

    private static Log readLog(DataInputStream in) throws IOException {
        return new Log(in.readFloat(), in.readFloat(), in.readInt(), in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void writeDrive(DataOutputStream out, Drive d) throws IOException {
        out.writeUTF(d.driver());
        out.writeLong(d.helmTick());
        out.writeBoolean(d.helmStill());
        out.writeBoolean(d.driving());
        out.writeBoolean(d.parked());
        out.writeBoolean(d.towed());
        out.writeFloat((float) d.throttle());
        out.writeFloat((float) d.steer());
        out.writeBoolean(d.braking());
        out.writeBoolean(d.boosting());
        out.writeBoolean(d.holding());
        out.writeChar(d.gear());
        out.writeFloat((float) d.lockDeg());
        out.writeFloat((float) d.lift());
        out.writeUTF(d.alert());
        out.writeByte(Math.min(255, Math.max(0, d.machines())));
        out.writeByte(d.controls().length);
        for (final float c : d.controls()) {
            out.writeFloat(c);
        }
    }

    private static Drive readDrive(DataInputStream in) throws IOException {
        final String driver = in.readUTF();
        final long helm = in.readLong();
        final boolean still = in.readBoolean();
        final boolean driving = in.readBoolean();
        final boolean parked = in.readBoolean();
        final boolean towed = in.readBoolean();
        final double throttle = in.readFloat();
        final double steer = in.readFloat();
        final boolean braking = in.readBoolean();
        final boolean boosting = in.readBoolean();
        final boolean holding = in.readBoolean();
        final char gear = in.readChar();
        final double lock = in.readFloat();
        final double lift = in.readFloat();
        final String alert = in.readUTF();
        final int machines = in.readUnsignedByte();
        final float[] controls = new float[in.readUnsignedByte()];
        for (int i = 0; i < controls.length; i++) {
            controls[i] = in.readFloat();
        }
        return new Drive(driver, helm, still, driving, parked, towed, throttle, steer, braking, boosting, holding,
                gear, lock, lift, alert, machines, controls);
    }

    private static void writeSetup(DataOutputStream out, Setup s) throws IOException {
        out.writeByte(s.preset().ordinal());
        out.writeBoolean(s.presetAuto());
        out.writeBoolean(s.articulated());
        out.writeBoolean(s.jetDrive());
        final DriveModel.Options o = s.options();
        out.writeBoolean(o.brakeThenReverse());
        out.writeBoolean(o.speedSteer());
        out.writeByte(o.steerTicks());
        out.writeByte(o.lockDeg());
        out.writeShort(o.maxRpm());
        out.writeByte(o.reversePercent());
        out.writeByte(o.throttleTicks());
        out.writeByte(o.releaseTicks());
        out.writeBoolean(o.holdWhenStopped());
        out.writeBoolean(o.parkBrake());
        out.writeByte(Math.min(255, Math.max(0, s.axles())));
        out.writeBoolean(s.greeting());
    }

    private static Setup readSetup(DataInputStream in) throws IOException {
        final SteerPreset[] presets = SteerPreset.values();
        final int p = in.readUnsignedByte();
        final SteerPreset preset = p < presets.length ? presets[p] : SteerPreset.FRONT;
        final boolean auto = in.readBoolean();
        final boolean articulated = in.readBoolean();
        final boolean jetDrive = in.readBoolean();
        final DriveModel.Options o = new DriveModel.Options(in.readBoolean(), in.readBoolean(), in.readUnsignedByte(),
                in.readUnsignedByte(), in.readUnsignedShort(), in.readUnsignedByte(), in.readUnsignedByte(),
                in.readByte(), in.readBoolean(), in.readBoolean());
        return new Setup(preset, auto, articulated, jetDrive, o, in.readUnsignedByte(), in.readBoolean());
    }

    private static void writeMass(DataOutputStream out, Mass m) throws IOException {
        out.writeDouble(m.mass());
        out.writeDouble(m.trainMass());
        out.writeFloat((float) m.yawInertia());
        out.writeBoolean(m.comMeasured());
        f3(out, m.console());
        out.writeInt(m.blocks());
    }

    private static Mass readMass(DataInputStream in) throws IOException {
        return new Mass(in.readDouble(), in.readDouble(), in.readFloat(), in.readBoolean(), f3(in), in.readInt());
    }

    record WheelGeometry(int unit, int axle, int governor, float x, float y, float z, boolean driven, boolean steered,
                         float radius, float capMass, float strength) {
    }

    record WheelState(float angle, float applied, float actual, float slip, float grip, float load, boolean airborne,
                      int brake) {
        static final WheelState UNKNOWN = new WheelState(0f, 0f, Float.NaN, Float.NaN, Float.NaN, 0f, false, 0);
    }

    static List<WheelGeometry> geometryOf(List<Wheel> wheels) {
        final List<WheelGeometry> out = new ArrayList<>(wheels.size());
        for (final Wheel w : wheels) {
            out.add(new WheelGeometry(w.unit(), w.axle(), w.governor(), w.x(), w.y(), w.z(), w.driven(), w.steered(),
                    w.radius(), w.capMass(), w.strength()));
        }
        return out;
    }

    static List<WheelState> stateOf(List<Wheel> wheels) {
        final List<WheelState> out = new ArrayList<>(wheels.size());
        for (final Wheel w : wheels) {
            out.add(new WheelState(w.angle(), w.applied(), w.actual(), w.slip(), w.grip(), w.load(), w.airborne(),
                    w.brake()));
        }
        return out;
    }

    // geometry count wins, missing wheel state reads unknown
    static List<Wheel> joinWheels(List<WheelGeometry> geometry, List<WheelState> state) {
        final List<Wheel> out = new ArrayList<>(geometry.size());
        for (int i = 0; i < geometry.size(); i++) {
            final WheelGeometry g = geometry.get(i);
            final WheelState s = i < state.size() ? state.get(i) : WheelState.UNKNOWN;
            out.add(new Wheel(g.unit(), g.axle(), g.governor(), g.x(), g.y(), g.z(), g.driven(), g.steered(),
                    g.radius(), g.capMass(), g.strength(), s.angle(), s.applied(), s.actual(), s.slip(), s.grip(),
                    s.load(), s.airborne(), s.brake()));
        }
        return out;
    }

    private static void writeWheelGeometry(DataOutputStream out, List<Wheel> wheels) throws IOException {
        out.writeByte(wheels.size());
        for (final Wheel w : wheels) {
            out.writeByte(w.unit());
            out.writeByte(w.axle());
            out.writeByte(w.governor());
            out.writeFloat(w.x());
            out.writeFloat(w.y());
            out.writeFloat(w.z());
            out.writeBoolean(w.driven());
            out.writeBoolean(w.steered());
            out.writeFloat(w.radius());
            out.writeFloat(w.capMass());
            out.writeFloat(w.strength());
        }
    }

    private static List<WheelGeometry> readWheelGeometry(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<WheelGeometry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new WheelGeometry(in.readUnsignedByte(), in.readByte(), in.readByte(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean(), in.readFloat(), in.readFloat(),
                    in.readFloat()));
        }
        return out;
    }

    private static void writeWheelState(DataOutputStream out, List<Wheel> wheels) throws IOException {
        out.writeByte(wheels.size());
        for (final Wheel w : wheels) {
            out.writeFloat(w.angle());
            out.writeFloat(w.applied());
            out.writeFloat(w.actual());
            out.writeFloat(w.slip());
            out.writeFloat(w.grip());
            out.writeFloat(w.load());
            out.writeBoolean(w.airborne());
            out.writeByte(w.brake());
        }
    }

    private static List<WheelState> readWheelState(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<WheelState> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new WheelState(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readBoolean(), in.readUnsignedByte()));
        }
        return out;
    }

    private static void writeGovernors(DataOutputStream out, List<Governor> governors) throws IOException {
        out.writeByte(governors.size());
        for (final Governor g : governors) {
            out.writeUTF(g.name());
            out.writeByte(g.unit());
            out.writeFloat(g.x());
            out.writeFloat(g.z());
            out.writeInt(g.applied());
            out.writeInt(g.commanded());
            out.writeFloat(g.ratio());
            out.writeByte(g.wheels());
            out.writeByte(g.jets());
            out.writeBoolean(g.machine());
            out.writeByte(g.supply().ordinal());
            out.writeBoolean(g.starved());
        }
    }

    private static List<Governor> readGovernors(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Governor> out = new ArrayList<>(n);
        final Supply[] supplies = Supply.values();
        for (int i = 0; i < n; i++) {
            final String name = in.readUTF();
            final int unit = in.readUnsignedByte();
            final float x = in.readFloat();
            final float z = in.readFloat();
            final int applied = in.readInt();
            final int commanded = in.readInt();
            final float ratio = in.readFloat();
            final int wheels = in.readUnsignedByte();
            final int jets = in.readUnsignedByte();
            final boolean machine = in.readBoolean();
            final int supply = in.readUnsignedByte();
            out.add(new Governor(name, unit, x, z, applied, commanded, ratio, wheels, jets, machine,
                    supply < supplies.length ? supplies[supply] : Supply.IDLE, in.readBoolean()));
        }
        return out;
    }

    private static void writeJets(DataOutputStream out, List<Jet> jets) throws IOException {
        out.writeByte(jets.size());
        for (final Jet j : jets) {
            out.writeUTF(j.name());
            out.writeByte(j.unit());
            out.writeFloat(j.x());
            out.writeFloat(j.y());
            out.writeFloat(j.z());
            f3(out, j.axis());
            out.writeByte(j.kind().ordinal());
            out.writeByte(j.job());
            out.writeFloat(j.command());
            out.writeFloat(j.force());
            out.writeFloat(j.ceiling());
            out.writeFloat(j.clear());
            out.writeByte(j.supply().ordinal());
            out.writeBoolean(j.lit());
            out.writeBoolean(j.foreign());
            out.writeFloat(j.rpm());
        }
    }

    private static List<Jet> readJets(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Jet> out = new ArrayList<>(n);
        final JetKind[] kinds = JetKind.values();
        final JetSupply[] supplies = JetSupply.values();
        for (int i = 0; i < n; i++) {
            final String name = in.readUTF();
            final int unit = in.readUnsignedByte();
            final float x = in.readFloat();
            final float y = in.readFloat();
            final float z = in.readFloat();
            final V3 axis = f3(in);
            final int kind = in.readUnsignedByte();
            final int job = in.readByte();
            final float command = in.readFloat();
            final float force = in.readFloat();
            final float ceiling = in.readFloat();
            final float clear = in.readFloat();
            final int supply = in.readUnsignedByte();
            out.add(new Jet(name, unit, x, y, z, axis, kind < kinds.length ? kinds[kind] : JetKind.PROPELLER, job,
                    command, force, ceiling, clear, supply < supplies.length ? supplies[supply] : JetSupply.NONE,
                    in.readBoolean(), in.readBoolean(), in.readFloat()));
        }
        return out;
    }

    private static void writeUnits(DataOutputStream out, List<Unit> units) throws IOException {
        out.writeByte(units.size());
        for (final Unit u : units) {
            out.writeByte(u.index());
            out.writeByte(u.parent());
            out.writeByte(u.role().ordinal());
            out.writeByte(u.link().ordinal());
            out.writeByte(u.mode().ordinal());
            out.writeBoolean(u.placed());
            out.writeBoolean(u.foreign());
            out.writeFloat(u.x());
            out.writeFloat(u.z());
            out.writeFloat(u.yaw());
            out.writeFloat(u.articulation());
            out.writeFloat(u.rate());
            out.writeFloat(u.reading());
            out.writeFloat(u.force());
            out.writeFloat(u.snapShare());
            out.writeByte(u.flags());
            out.writeByte(u.wheels());
            out.writeFloat(u.mass());
            out.writeUTF(u.name());
        }
    }

    private static List<Unit> readUnits(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Unit> out = new ArrayList<>(n);
        final BodyRole[] roles = BodyRole.values();
        final LinkKind[] links = LinkKind.values();
        final TrailerMode[] modes = TrailerMode.values();
        for (int i = 0; i < n; i++) {
            final int index = in.readUnsignedByte();
            final int parent = in.readByte();
            final int role = in.readUnsignedByte();
            final int link = in.readUnsignedByte();
            final int mode = in.readUnsignedByte();
            out.add(new Unit(index, parent, role < roles.length ? roles[role] : BodyRole.PART,
                    link < links.length ? links[link] : LinkKind.JOINT, mode < modes.length ? modes[mode] : TrailerMode.OFF,
                    in.readBoolean(), in.readBoolean(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readUnsignedByte(),
                    in.readUnsignedByte(), in.readFloat(), in.readUTF()));
        }
        return out;
    }

    private static void writeTies(DataOutputStream out, List<Tie> ties) throws IOException {
        out.writeByte(ties.size());
        for (final Tie t : ties) {
            out.writeByte(t.kind().ordinal());
            out.writeByte(t.a());
            out.writeFloat(t.ax());
            out.writeFloat(t.az());
            out.writeByte(t.b());
            out.writeFloat(t.bx());
            out.writeFloat(t.bz());
            out.writeFloat(t.reading());
        }
    }

    private static List<Tie> readTies(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Tie> out = new ArrayList<>(n);
        final LinkKind[] links = LinkKind.values();
        for (int i = 0; i < n; i++) {
            final int kind = in.readUnsignedByte();
            out.add(new Tie(kind < links.length ? links[kind] : LinkKind.ROPE, in.readByte(), in.readFloat(),
                    in.readFloat(), in.readByte(), in.readFloat(), in.readFloat(), in.readFloat()));
        }
        return out;
    }

    private static void writeLights(DataOutputStream out, Lights l) throws IOException {
        out.writeBoolean(l.driving());
        out.writeBoolean(l.braking());
        out.writeBoolean(l.reversing());
        out.writeBoolean(l.boosting());
        out.writeFloat(l.steer());
        out.writeBoolean(l.beam());
        out.writeByte(l.lamps().size());
        for (final Lamp lamp : l.lamps()) {
            out.writeByte(lamp.unit());
            out.writeFloat(lamp.x());
            out.writeFloat(lamp.y());
            out.writeFloat(lamp.z());
            out.writeByte(lamp.colour());
            out.writeByte(lamp.source().ordinal());
            out.writeByte(lamp.strength());
            out.writeBoolean(lamp.proposed());
        }
    }

    private static Lights readLights(DataInputStream in) throws IOException {
        final boolean driving = in.readBoolean();
        final boolean braking = in.readBoolean();
        final boolean reversing = in.readBoolean();
        final boolean boosting = in.readBoolean();
        final float steer = in.readFloat();
        final boolean beam = in.readBoolean();
        final int n = in.readUnsignedByte();
        final List<Lamp> lamps = new ArrayList<>(n);
        final LinkSource[] sources = LinkSource.values();
        for (int i = 0; i < n; i++) {
            final int unit = in.readUnsignedByte();
            final float x = in.readFloat();
            final float y = in.readFloat();
            final float z = in.readFloat();
            final int colour = in.readUnsignedByte();
            final int source = in.readUnsignedByte();
            lamps.add(new Lamp(unit, x, y, z, colour, source < sources.length ? sources[source] : LinkSource.OFF,
                    in.readUnsignedByte(), in.readBoolean()));
        }
        return new Lights(driving, braking, reversing, boosting, steer, beam, lamps);
    }

    private static void writeMachines(DataOutputStream out, List<Machine> machines) throws IOException {
        out.writeByte(machines.size());
        for (final Machine m : machines) {
            out.writeUTF(m.name());
            out.writeByte(m.unit());
            out.writeFloat(m.x());
            out.writeFloat(m.z());
            out.writeUTF(m.drives());
            out.writeBoolean(m.runner());
            out.writeByte(m.input());
            out.writeFloat(m.output());
            out.writeInt(m.applied());
            out.writeBoolean(m.foreign());
        }
    }

    private static List<Machine> readMachines(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Machine> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Machine(in.readUTF(), in.readUnsignedByte(), in.readFloat(), in.readFloat(), in.readUTF(),
                    in.readBoolean(), in.readUnsignedByte(), in.readFloat(), in.readInt(), in.readBoolean()));
        }
        return out;
    }

    private static void writeProblems(DataOutputStream out, List<String> problems) throws IOException {
        out.writeByte(problems.size());
        for (final String p : problems) {
            out.writeUTF(p);
        }
    }

    private static List<String> readProblems(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(in.readUTF());
        }
        return out;
    }

    private static void writeTerrain(DataOutputStream out, Terrain t) throws IOException {
        out.writeDouble(t.originX());
        out.writeDouble(t.originZ());
        out.writeFloat((float) t.cell());
        out.writeByte(t.size());
        for (final short h : t.heights()) {
            out.writeShort(h);
        }
        out.writeFloat((float) t.groundY());
    }

    private static Terrain readTerrain(DataInputStream in) throws IOException {
        final double ox = in.readDouble();
        final double oz = in.readDouble();
        final double cell = in.readFloat();
        final int size = in.readUnsignedByte();
        final short[] heights = new short[size * size];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = in.readShort();
        }
        return new Terrain(ox, oz, cell, size, heights, in.readFloat());
    }

    private static void writeOdometer(DataOutputStream out, Odometer o) throws IOException {
        out.writeDouble(o.odometer());
        out.writeDouble(o.trip());
        out.writeLong(o.tripTicks());
        out.writeFloat((float) o.topSpeed());
        out.writeFloat((float) o.tripTop());
        out.writeLong(o.tripStart());
    }

    private static Odometer readOdometer(DataInputStream in) throws IOException {
        return new Odometer(in.readDouble(), in.readDouble(), in.readLong(), in.readFloat(), in.readFloat(),
                in.readLong());
    }

    private static void writeTrail(DataOutputStream out, Trail t) throws IOException {
        final List<Fix> fixes = t.fixes();
        out.writeShort(fixes.size());
        if (fixes.isEmpty()) {
            return;
        }
        final Fix first = fixes.get(0);
        v3(out, first.position());
        out.writeLong(first.gameTime());
        for (final Fix f : fixes) {
            out.writeFloat((float) (f.position().x() - first.position().x()));
            out.writeFloat((float) (f.position().y() - first.position().y()));
            out.writeFloat((float) (f.position().z() - first.position().z()));
            out.writeInt((int) Math.min(Integer.MAX_VALUE, Math.max(0L, f.gameTime() - first.gameTime())));
        }
    }

    private static Trail readTrail(DataInputStream in) throws IOException {
        final int n = in.readUnsignedShort();
        if (n == 0) {
            return Trail.EMPTY;
        }
        final V3 origin = v3(in);
        final long t0 = in.readLong();
        final List<Fix> fixes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final V3 p = new V3(origin.x() + in.readFloat(), origin.y() + in.readFloat(), origin.z() + in.readFloat());
            fixes.add(new Fix(p, t0 + in.readInt()));
        }
        return new Trail(fixes);
    }

    static final float Q_SPEED = 100.0f;
    static final float Q_UNIT = 30000.0f;
    static final float Q_G = 1000.0f;
    static final float Q_DEG = 100.0f;

    private static void writeHistory(DataOutputStream out, History h) throws IOException {
        out.writeShort(h.periodTicks());
        out.writeLong(h.lastTick());
        out.writeShort(h.size());
        quantised(out, h.speed(), Q_SPEED);
        quantised(out, h.throttle(), Q_UNIT);
        quantised(out, h.brake(), Q_UNIT);
        quantised(out, h.steer(), Q_UNIT);
        quantised(out, h.latG(), Q_G);
        quantised(out, h.lonG(), Q_G);
        quantised(out, h.slip(), Q_UNIT);
        quantised(out, h.pitch(), Q_DEG);
    }

    private static History readHistory(DataInputStream in) throws IOException {
        final int period = in.readUnsignedShort();
        final long last = in.readLong();
        final int n = in.readUnsignedShort();
        return new History(period, last, quantised(in, n, Q_SPEED), quantised(in, n, Q_UNIT), quantised(in, n, Q_UNIT),
                quantised(in, n, Q_UNIT), quantised(in, n, Q_G), quantised(in, n, Q_G), quantised(in, n, Q_UNIT),
                quantised(in, n, Q_DEG));
    }

    private static void writeEvents(DataOutputStream out, Events e) throws IOException {
        out.writeByte(e.list().size());
        for (final Event ev : e.list()) {
            out.writeLong(ev.tick());
            out.writeByte(ev.kind().ordinal());
            out.writeFloat(ev.value());
            out.writeUTF(ev.text());
            v3(out, ev.position());
        }
    }

    private static Events readEvents(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Event> out = new ArrayList<>(n);
        final EventKind[] kinds = EventKind.values();
        for (int i = 0; i < n; i++) {
            final long tick = in.readLong();
            final int kind = in.readUnsignedByte();
            out.add(new Event(tick, kind < kinds.length ? kinds[kind] : EventKind.PROBLEM, in.readFloat(), in.readUTF(),
                    v3(in)));
        }
        return new Events(out);
    }

    private static void writeEnvelope(DataOutputStream out, Envelope e) throws IOException {
        out.writeFloat((float) e.seconds());
        out.writeFloat((float) e.topSpeed());
        out.writeFloat((float) e.maxAccel());
        out.writeFloat((float) e.maxDecel());
        out.writeFloat((float) e.maxLatG());
        out.writeFloat((float) e.bucketWidth());
        for (final float f : e.turnRadius()) {
            out.writeFloat(f);
        }
    }

    private static Envelope readEnvelope(DataInputStream in) throws IOException {
        final double seconds = in.readFloat();
        final double top = in.readFloat();
        final double accel = in.readFloat();
        final double decel = in.readFloat();
        final double lat = in.readFloat();
        final double width = in.readFloat();
        final float[] buckets = new float[Envelope.BUCKETS];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = in.readFloat();
        }
        return new Envelope(seconds, top, accel, decel, lat, buckets, width);
    }

    private static void writeShapes(DataOutputStream out, List<Shape> shapes) throws IOException {
        out.writeByte(shapes.size());
        for (final Shape s : shapes) {
            out.writeByte(s.unit());
            out.writeFloat((float) s.cell());
            out.writeByte(s.cols());
            out.writeByte(s.topRows());
            out.writeByte(s.sideRows());
            out.writeFloat((float) s.originZ());
            out.writeFloat((float) s.originX());
            out.writeFloat((float) s.originY());
            out.write(s.top());
            out.write(s.side());
        }
    }

    private static List<Shape> readShapes(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Shape> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int unit = in.readUnsignedByte();
            final double cell = in.readFloat();
            final int cols = in.readUnsignedByte();
            final int topRows = in.readUnsignedByte();
            final int sideRows = in.readUnsignedByte();
            if (cols > Shape.MAX_CELLS || topRows > Shape.MAX_CELLS || sideRows > Shape.MAX_CELLS) {
                throw new IOException("ground shape " + cols + "x" + topRows + "x" + sideRows + " past the cap");
            }
            final double oz = in.readFloat();
            final double ox = in.readFloat();
            final double oy = in.readFloat();
            final byte[] top = new byte[(cols * topRows + 7) >> 3];
            in.readFully(top);
            final byte[] side = new byte[(cols * sideRows + 7) >> 3];
            in.readFully(side);
            out.add(new Shape(unit, cell, cols, topRows, sideRows, oz, ox, oy, top, side));
        }
        return out;
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeBoolean(id != null);
        if (id != null) {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
        }
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
    }

    private static void v3(DataOutputStream out, V3 v) throws IOException {
        out.writeDouble(v.x());
        out.writeDouble(v.y());
        out.writeDouble(v.z());
    }

    private static V3 v3(DataInputStream in) throws IOException {
        return new V3(in.readDouble(), in.readDouble(), in.readDouble());
    }

    private static void f3(DataOutputStream out, V3 v) throws IOException {
        out.writeFloat((float) v.x());
        out.writeFloat((float) v.y());
        out.writeFloat((float) v.z());
    }

    private static V3 f3(DataInputStream in) throws IOException {
        return new V3(in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void quantised(DataOutputStream out, float[] a, float scale) throws IOException {
        for (final float f : a) {
            if (!Float.isFinite(f)) {
                out.writeShort(Short.MIN_VALUE);
            } else {
                out.writeShort((short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, Math.round(f * scale))));
            }
        }
    }

    private static float[] quantised(DataInputStream in, int n, float scale) throws IOException {
        final float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            final short v = in.readShort();
            a[i] = v == Short.MIN_VALUE ? Float.NaN : v / scale;
        }
        return a;
    }
}
