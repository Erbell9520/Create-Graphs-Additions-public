package com.createstats.aerial;

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

import com.createstats.aerial.AerialSnapshot.Air;
import com.createstats.aerial.AerialSnapshot.Autopilot;
import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Body;
import com.createstats.aerial.AerialSnapshot.Docking;
import com.createstats.aerial.AerialSnapshot.Engine;
import com.createstats.aerial.AerialSnapshot.Envelope;
import com.createstats.aerial.AerialSnapshot.Event;
import com.createstats.aerial.AerialSnapshot.Fix;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Guard;
import com.createstats.aerial.AerialSnapshot.Helm;
import com.createstats.aerial.AerialSnapshot.History;
import com.createstats.aerial.AerialSnapshot.Learned;
import com.createstats.aerial.AerialSnapshot.Leg;
import com.createstats.aerial.AerialSnapshot.Mass;
import com.createstats.aerial.AerialSnapshot.Part;
import com.createstats.aerial.AerialSnapshot.Parts;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Route;
import com.createstats.aerial.AerialSnapshot.Silhouette;
import com.createstats.aerial.AerialSnapshot.Target;
import com.createstats.aerial.AerialSnapshot.Terrain;
import com.createstats.aerial.AerialSnapshot.Traffic;
import com.createstats.aerial.AerialSnapshot.Trail;
import com.createstats.aerial.AerialSnapshot.Voyage;
import com.createstats.aerial.AerialSnapshot.Waypoint;

// sectioned packets. fast stuff every pull, the heavy stuff only when its crc changes (~20x less)
public final class AerialCodec {
    public static final int VERSION = 2;

    static final int V1 = 1;

    public enum Section {
        HEADER, BODY, AIR, MASS, FORCES, PROPELLERS, BALLOONS, ENGINE, TARGET, AUTOPILOT, DOCKING, TRAFFIC,
        TERRAIN, ROUTE, TRAIL, HISTORY, ENVELOPE, SILHOUETTE, HELM, PARTS, VOYAGE;

        public int bit() {
            return 1 << ordinal();
        }

        // traffic got moved to slow, it was the biggest thing in the packet at 24 ships and resent 5x a sec for nothing
        public boolean fast() {
            return switch (this) {
                case HEADER, BODY, AIR, MASS, FORCES, PROPELLERS, BALLOONS, ENGINE, TARGET, AUTOPILOT, DOCKING, HELM -> true;
                default -> false;
            };
        }
    }

    public static final int ALL = (1 << Section.values().length) - 1;

    static final int V1_READABLE = Section.HEADER.bit() | Section.TRAIL.bit() | Section.HISTORY.bit()
            | Section.ENVELOPE.bit();

    private AerialCodec() {
    }

    public static byte[] encode(AerialSnapshot s) {
        return encode(s, ALL);
    }

    public static byte[] encode(AerialSnapshot s, int mask) {
        final Map<Section, byte[]> sections = new EnumMap<>(Section.class);
        for (final Section sec : Section.values()) {
            if ((mask & sec.bit()) != 0) {
                sections.put(sec, section(s, sec));
            }
        }
        return join(sections);
    }

    public static byte[] section(AerialSnapshot s, Section sec) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            switch (sec) {
                case HEADER -> writeHeader(out, s);
                case BODY -> writeBody(out, s.body());
                case AIR -> writeAir(out, s.air());
                case MASS -> writeMass(out, s.mass());
                case FORCES -> writeForces(out, s.forces());
                case PROPELLERS -> writePropellers(out, s.propellers());
                case BALLOONS -> writeBalloons(out, s.balloons());
                case ENGINE -> writeEngine(out, s.engine());
                case TARGET -> writeTarget(out, s.target());
                case AUTOPILOT -> writeAutopilot(out, s.autopilot());
                case DOCKING -> writeDocking(out, s.docking());
                case TRAFFIC -> writeTraffic(out, s.traffic());
                case TERRAIN -> writeTerrain(out, s.terrain());
                case ROUTE -> writeRoute(out, s.route());
                case TRAIL -> writeTrail(out, s.trail());
                case HISTORY -> writeHistory(out, s.history());
                case ENVELOPE -> writeEnvelope(out, s.envelope());
                case SILHOUETTE -> writeSilhouette(out, s.silhouette());
                case HELM -> writeHelm(out, s.helm());
                case PARTS -> writeParts(out, s.parts());
                case VOYAGE -> writeVoyage(out, s.voyage());
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

    public static AerialSnapshot decode(byte[] data, AerialSnapshot previous) throws IOException {
        if (data == null || data.length < 5) {
            throw new IOException("aerial packet too short");
        }
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        final int version = in.readUnsignedByte();
        final int mask = in.readInt();
        if (version != VERSION && !(version == V1 && (mask & ~V1_READABLE) == 0)) {
            throw new IOException("aerial packet version " + version + ", expected " + VERSION);
        }
        final AerialSnapshot base = previous != null ? previous : new AerialSnapshot(0L, "", null, 0,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        long gameTime = base.gameTime();
        String name = base.name();
        UUID shipId = base.shipId();
        int commands = base.commands();
        Body body = base.body();
        Air air = base.air();
        Mass mass = base.mass();
        List<Force> forces = base.forces();
        List<Propeller> propellers = base.propellers();
        List<Balloon> balloons = base.balloons();
        Engine engine = base.engine();
        Target target = base.target();
        Autopilot autopilot = base.autopilot();
        Docking docking = base.docking();
        List<Traffic> traffic = base.traffic();
        Terrain terrain = base.terrain();
        Route route = base.route();
        Trail trail = base.trail();
        History history = base.history();
        Envelope envelope = base.envelope();
        Silhouette silhouette = base.silhouette();
        Helm helm = base.helm();
        Parts parts = base.parts();
        Voyage voyage = base.voyage();

        for (final Section sec : Section.values()) {
            if ((mask & sec.bit()) == 0) {
                continue;
            }
            final int length = in.readInt();
            if (length < 0 || length > in.available()) {
                throw new IOException("aerial section " + sec + " claims " + length + " bytes");
            }
            final byte[] body0 = new byte[length];
            in.readFully(body0);
            final ByteArrayInputStream raw = new ByteArrayInputStream(body0);
            final DataInputStream s = new DataInputStream(raw);
            switch (sec) {
                case HEADER -> {
                    gameTime = s.readLong();
                    name = s.readUTF();
                    shipId = s.readBoolean() ? new UUID(s.readLong(), s.readLong()) : null;
                    commands = s.readInt();
                }
                case BODY -> body = readBody(s);
                case AIR -> air = readAir(s);
                case MASS -> mass = readMass(s);
                case FORCES -> forces = readForces(s);
                case PROPELLERS -> propellers = readPropellers(s);
                case BALLOONS -> balloons = readBalloons(s);
                case ENGINE -> engine = readEngine(s);
                case TARGET -> target = readTarget(s);
                case AUTOPILOT -> autopilot = readAutopilot(s);
                case DOCKING -> docking = readDocking(s);
                case TRAFFIC -> traffic = readTraffic(s);
                case TERRAIN -> terrain = readTerrain(s);
                case ROUTE -> route = readRoute(s);
                case TRAIL -> trail = readTrail(s);
                case HISTORY -> history = readHistory(s);
                case ENVELOPE -> envelope = readEnvelope(s);
                case SILHOUETTE -> silhouette = readSilhouette(s);
                case HELM -> helm = readHelm(s);
                case PARTS -> parts = readParts(s);
                case VOYAGE -> voyage = readVoyage(s);
            }
            if (raw.available() != 0) {
                throw new IOException("aerial section " + sec + " left " + raw.available() + " bytes unread");
            }
        }
        if (in.available() != 0) {
            throw new IOException("aerial packet has " + in.available() + " trailing bytes");
        }
        return new AerialSnapshot(gameTime, name, shipId, commands, body, air, mass, forces, propellers, balloons,
                engine, target, autopilot, docking, traffic, terrain, route, trail, history, envelope, silhouette,
                helm, parts, voyage);
    }

    private static void writeHeader(DataOutputStream out, AerialSnapshot s) throws IOException {
        out.writeLong(s.gameTime());
        out.writeUTF(s.name());
        out.writeBoolean(s.shipId() != null);
        if (s.shipId() != null) {
            out.writeLong(s.shipId().getMostSignificantBits());
            out.writeLong(s.shipId().getLeastSignificantBits());
        }
        out.writeInt(s.commands());
    }

    private static void writeBody(DataOutputStream out, Body b) throws IOException {
        v3(out, b.position());
        out.writeDouble(b.orientation().x());
        out.writeDouble(b.orientation().y());
        out.writeDouble(b.orientation().z());
        out.writeDouble(b.orientation().w());
        f3(out, b.nose());
        f3(out, b.velocity());
        f3(out, b.spin());
    }

    private static Body readBody(DataInputStream in) throws IOException {
        final V3 position = v3(in);
        final Quat q = new Quat(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
        return new Body(position, q, f3(in), f3(in), f3(in));
    }

    private static void writeAir(DataOutputStream out, Air a) throws IOException {
        out.writeBoolean(a.measured());
        f3(out, a.velocity());
        out.writeFloat((float) a.pressure());
        out.writeFloat((float) a.pressureAbove());
        out.writeFloat((float) a.seaLevel());
        f3(out, a.gravity());
    }

    private static Air readAir(DataInputStream in) throws IOException {
        return new Air(in.readBoolean(), f3(in), in.readFloat(), in.readFloat(), in.readFloat(), f3(in));
    }

    private static void writeMass(DataOutputStream out, Mass m) throws IOException {
        out.writeDouble(m.mass());
        f3(out, m.inertia());
        out.writeInt(m.blocks());
        out.writeFloat((float) m.load());
    }

    private static Mass readMass(DataInputStream in) throws IOException {
        return new Mass(in.readDouble(), f3(in), in.readInt(), in.readFloat());
    }

    private static void writeForces(DataOutputStream out, List<Force> forces) throws IOException {
        out.writeByte(forces.size());
        for (final Force f : forces) {
            out.writeUTF(f.group());
            f3(out, f.force());
            v3(out, f.centre());
        }
    }

    private static List<Force> readForces(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Force> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Force(in.readUTF(), f3(in), v3(in)));
        }
        return out;
    }

    private static void writePropellers(DataOutputStream out, List<Propeller> props) throws IOException {
        out.writeByte(props.size());
        for (final Propeller p : props) {
            out.writeUTF(p.name());
            out.writeUTF(p.kind());
            f3(out, p.position());
            f3(out, p.direction());
            out.writeFloat((float) p.staticThrust());
            out.writeFloat((float) p.airflow());
            out.writeFloat((float) p.airflowScaling());
            out.writeFloat((float) p.pressure());
            out.writeFloat((float) p.rpm());
            out.writeFloat((float) p.shaftStress());
            out.writeBoolean(p.active());
        }
    }

    private static List<Propeller> readPropellers(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Propeller> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Propeller(in.readUTF(), in.readUTF(), f3(in), f3(in), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readBoolean()));
        }
        return out;
    }

    private static void writeBalloons(DataOutputStream out, List<Balloon> balloons) throws IOException {
        out.writeByte(balloons.size());
        for (final Balloon b : balloons) {
            out.writeUTF(b.name());
            out.writeUTF(b.kind());
            f3(out, b.position());
            out.writeFloat((float) b.lift());
            out.writeFloat((float) b.filled());
            out.writeFloat((float) b.target());
            out.writeFloat((float) b.capacity());
            out.writeFloat((float) b.fillRate());
            out.writeFloat((float) b.gasOutput());
            out.writeFloat((float) b.pressure());
            out.writeShort(b.heaters());
            out.writeShort(b.lit());
            out.writeBoolean(b.driven());
        }
    }

    private static List<Balloon> readBalloons(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Balloon> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Balloon(in.readUTF(), in.readUTF(), f3(in), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readShort(), in.readShort(),
                    in.readBoolean()));
        }
        return out;
    }

    private static void writeEngine(DataOutputStream out, Engine e) throws IOException {
        out.writeShort(e.count());
        out.writeFloat((float) e.fuelSeconds());
        out.writeFloat((float) e.burnFraction());
        out.writeBoolean(e.superHeated());
    }

    private static Engine readEngine(DataInputStream in) throws IOException {
        return new Engine(in.readShort(), in.readFloat(), in.readFloat(), in.readBoolean());
    }

    private static void writeTarget(DataOutputStream out, Target t) throws IOException {
        out.writeBoolean(t != null);
        if (t != null) {
            out.writeUTF(t.label());
            v3(out, t.position());
            out.writeUTF(t.source());
        }
    }

    private static Target readTarget(DataInputStream in) throws IOException {
        return in.readBoolean() ? new Target(in.readUTF(), v3(in), in.readUTF()) : null;
    }

    private static void writeAutopilot(DataOutputStream out, Autopilot a) throws IOException {
        out.writeUTF(a.mode());
        out.writeBoolean(a.engaged());
        out.writeFloat((float) a.targetAltitude());
        out.writeFloat((float) a.targetSpeed());
        out.writeFloat((float) a.targetHeading());
        v3(out, a.lookahead());
        out.writeFloat((float) a.authThrottle());
        out.writeFloat((float) a.authYaw());
        out.writeFloat((float) a.authPitch());
        out.writeFloat((float) a.authLift());
    }

    private static Autopilot readAutopilot(DataInputStream in) throws IOException {
        return new Autopilot(in.readUTF(), in.readBoolean(), in.readFloat(), in.readFloat(), in.readFloat(), v3(in),
                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void writeDocking(DataOutputStream out, Docking d) throws IOException {
        out.writeBoolean(d != null);
        if (d != null) {
            out.writeUTF(d.partner());
            out.writeBoolean(d.connected());
            f3(out, d.offset());
            f3(out, d.relative());
        }
    }

    private static Docking readDocking(DataInputStream in) throws IOException {
        return in.readBoolean() ? new Docking(in.readUTF(), in.readBoolean(), f3(in), f3(in)) : null;
    }

    private static void writeTraffic(DataOutputStream out, List<Traffic> traffic) throws IOException {
        out.writeByte(traffic.size());
        for (final Traffic t : traffic) {
            out.writeUTF(t.name());
            out.writeBoolean(t.id() != null);
            if (t.id() != null) {
                out.writeLong(t.id().getMostSignificantBits());
                out.writeLong(t.id().getLeastSignificantBits());
            }
            v3(out, t.position());
            f3(out, t.velocity());
            out.writeFloat((float) t.radius());
        }
    }

    private static List<Traffic> readTraffic(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Traffic> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final String name = in.readUTF();
            final UUID id = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
            out.add(new Traffic(name, id, v3(in), f3(in), in.readFloat()));
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
        out.writeFloat((float) t.clearance());
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
        return new Terrain(ox, oz, cell, size, heights, in.readFloat(), in.readFloat());
    }

    private static void writeRoute(DataOutputStream out, Route r) throws IOException {
        out.writeUTF(r.name());
        out.writeShort(r.points().size());
        for (final Waypoint w : r.points()) {
            v3(out, w.position());
            out.writeFloat((float) w.speed());
        }
        out.writeShort(r.active());
    }

    private static Route readRoute(DataInputStream in) throws IOException {
        final String name = in.readUTF();
        final int n = in.readUnsignedShort();
        final List<Waypoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            points.add(new Waypoint(v3(in), in.readFloat()));
        }
        return new Route(name, points, in.readUnsignedShort());
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

    private static void writeHistory(DataOutputStream out, History h) throws IOException {
        out.writeShort(h.periodTicks());
        out.writeLong(h.lastTick());
        out.writeShort(h.size());
        floats(out, h.altitude());
        floats(out, h.airspeed());
        floats(out, h.verticalSpeed());
        floats(out, h.support());
        floats(out, h.crossTrack());
    }

    private static History readHistory(DataInputStream in) throws IOException {
        final int period = in.readUnsignedShort();
        final long last = in.readLong();
        final int n = in.readUnsignedShort();
        return new History(period, last, floats(in, n), floats(in, n), floats(in, n), floats(in, n), floats(in, n));
    }

    private static void writeEnvelope(DataOutputStream out, Envelope e) throws IOException {
        out.writeFloat((float) e.seconds());
        out.writeFloat((float) e.topSpeed());
        out.writeFloat((float) e.bestClimb());
        out.writeFloat((float) e.maxSink());
        out.writeFloat((float) e.maxYawRate());
        out.writeFloat((float) e.maxAccel());
        out.writeFloat((float) e.maxDecel());
        out.writeFloat((float) e.bucketWidth());
        floats(out, e.turnRadius());
    }

    private static Envelope readEnvelope(DataInputStream in) throws IOException {
        final double seconds = in.readFloat();
        final double topSpeed = in.readFloat();
        final double bestClimb = in.readFloat();
        final double maxSink = in.readFloat();
        final double maxYawRate = in.readFloat();
        final double maxAccel = in.readFloat();
        final double maxDecel = in.readFloat();
        final double width = in.readFloat();
        final float[] buckets = floats(in, Envelope.BUCKETS);
        return new Envelope(seconds, topSpeed, bestClimb, maxSink, maxYawRate, maxAccel, maxDecel, buckets, width);
    }

    private static void writeSilhouette(DataOutputStream out, Silhouette s) throws IOException {
        out.writeFloat((float) s.cell());
        out.writeByte(s.cols());
        out.writeByte(s.sideRows());
        out.writeByte(s.topRows());
        out.writeFloat((float) s.originF());
        out.writeFloat((float) s.originU());
        out.writeFloat((float) s.originR());
        out.write(s.side());
        out.write(s.top());
        out.writeBoolean(s.hasEnvelope());
        if (s.hasEnvelope()) {
            out.write(fit(s.envSide(), s.side().length));
            out.write(fit(s.envTop(), s.top().length));
        }
    }

    private static Silhouette readSilhouette(DataInputStream in) throws IOException {
        final double cell = in.readFloat();
        final int cols = in.readUnsignedByte();
        final int sideRows = in.readUnsignedByte();
        final int topRows = in.readUnsignedByte();
        final double f = in.readFloat();
        final double u = in.readFloat();
        final double r = in.readFloat();
        final byte[] side = new byte[(cols * sideRows + 7) >> 3];
        in.readFully(side);
        final byte[] top = new byte[(cols * topRows + 7) >> 3];
        in.readFully(top);
        byte[] envSide = null;
        byte[] envTop = null;
        if (in.readBoolean()) {
            envSide = new byte[side.length];
            in.readFully(envSide);
            envTop = new byte[top.length];
            in.readFully(envTop);
        }
        return new Silhouette(cell, cols, sideRows, topRows, f, u, r, side, top, envSide, envTop);
    }

    private static byte[] fit(byte[] bits, int length) {
        final byte[] out = new byte[length];
        System.arraycopy(bits, 0, out, 0, Math.min(bits.length, length));
        return out;
    }

    private static void writeHelm(DataOutputStream out, Helm h) throws IOException {
        out.writeUTF(h.mode());
        out.writeUTF(h.leave());
        out.writeUTF(h.pilot());
        out.writeLong(h.since());
        out.writeUTF(h.feel());
        out.writeFloat((float) h.order());
        out.writeFloat((float) h.engine());
        out.writeInt(h.rings());
        out.writeBoolean(h.braking());
        out.writeFloat((float) h.heldAltitude());
        out.writeFloat((float) h.flownAltitude());
        out.writeFloat((float) h.heldHeading());
        out.writeFloat((float) h.heldSpeed());
        out.writeFloat((float) h.fullAhead());
        out.writeFloat((float) h.ceiling());
        out.writeFloat((float) h.balloonCeiling());
        out.writeFloat((float) h.cap());
        out.writeFloat((float) h.worldTop());
        out.writeFloat((float) h.liftUse());
        out.writeBoolean(h.liftShort());
        out.writeBoolean(h.weighing());
        out.writeFloat((float) h.trim());
        out.writeByte(h.staff());
        v3(out, h.anchor());
        final Guard g = h.guard();
        out.writeUTF(g.verdict());
        out.writeFloat((float) g.floor());
        out.writeFloat((float) g.speedCap());
        out.writeFloat((float) g.groundY());
        out.writeFloat((float) g.distance());
        out.writeFloat((float) g.peakY());
        out.writeFloat((float) g.peakAt());
        out.writeFloat((float) g.seen());
        out.writeUTF(h.intent());
        out.writeBoolean(h.intentAlert());
        out.writeByte(h.notes().size());
        for (final String n : h.notes()) {
            out.writeUTF(n);
        }
        out.writeFloat((float) h.thrustAhead());
        out.writeFloat((float) h.thrustAstern());
        out.writeFloat((float) h.thrustTurn());
    }

    private static Helm readHelm(DataInputStream in) throws IOException {
        final String mode = in.readUTF();
        final String leave = in.readUTF();
        final String pilot = in.readUTF();
        final long since = in.readLong();
        final String feel = in.readUTF();
        final double order = in.readFloat();
        final double engine = in.readFloat();
        final int rings = in.readInt();
        final boolean braking = in.readBoolean();
        final double held = in.readFloat();
        final double flown = in.readFloat();
        final double heading = in.readFloat();
        final double speed = in.readFloat();
        final double full = in.readFloat();
        final double ceiling = in.readFloat();
        final double balloonCeiling = in.readFloat();
        final double cap = in.readFloat();
        final double worldTop = in.readFloat();
        final double liftUse = in.readFloat();
        final boolean liftShort = in.readBoolean();
        final boolean weighing = in.readBoolean();
        final double trim = in.readFloat();
        final int staff = in.readUnsignedByte();
        final V3 anchor = v3(in);
        final Guard guard = new Guard(in.readUTF(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                in.readFloat(), in.readFloat(), in.readFloat());
        final String intent = in.readUTF();
        final boolean alert = in.readBoolean();
        final int n = in.readUnsignedByte();
        final List<String> notes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            notes.add(in.readUTF());
        }
        return new Helm(mode, leave, pilot, since, feel, order, engine, rings, braking, held, flown, heading, speed,
                full, ceiling, balloonCeiling, cap, worldTop, liftUse, liftShort, weighing, trim, staff, anchor, guard,
                intent, alert, notes, in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void writeParts(DataOutputStream out, Parts p) throws IOException {
        out.writeByte(p.parts().size());
        for (final Part part : p.parts()) {
            out.writeByte(part.kind());
            out.writeUTF(part.name());
            out.writeUTF(part.job());
            out.writeByte(part.state());
            f3(out, part.at());
            f3(out, part.dir());
            out.writeFloat((float) part.value());
            out.writeFloat((float) part.extra());
        }
        final Learned l = p.learned();
        out.writeFloat((float) l.drag());
        out.writeFloat((float) l.wing());
        out.writeFloat((float) l.lean());
        out.writeFloat((float) l.hidden());
        out.writeFloat((float) l.turnDrag());
        out.writeShort(l.propsKnown());
        out.writeShort(l.props());
        out.writeBoolean(p.bridge());
    }

    private static Parts readParts(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final List<Part> parts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parts.add(new Part(in.readUnsignedByte(), in.readUTF(), in.readUTF(), in.readUnsignedByte(), f3(in), f3(in),
                    in.readFloat(), in.readFloat()));
        }
        final Learned learned = new Learned(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                in.readShort(), in.readShort());
        return new Parts(parts, learned, in.readBoolean());
    }

    private static void writeVoyage(DataOutputStream out, Voyage v) throws IOException {
        out.writeLong(v.takeoff());
        out.writeFloat((float) v.seconds());
        out.writeFloat((float) v.distance());
        out.writeFloat((float) v.highest());
        out.writeInt(v.tracePeriod());
        out.writeLong(v.traceLast());
        out.writeShort(v.trace().length);
        floats(out, v.trace());
        out.writeByte(v.events().size());
        for (final Event e : v.events()) {
            out.writeLong(e.tick());
            out.writeByte(e.kind());
            out.writeByte(e.level());
            out.writeUTF(e.text());
            out.writeFloat(e.altitude());
        }
        out.writeBoolean(v.last() != null);
        if (v.last() != null) {
            final Leg l = v.last();
            out.writeLong(l.takeoff());
            out.writeLong(l.landed());
            out.writeFloat((float) l.seconds());
            out.writeFloat((float) l.distance());
            out.writeFloat((float) l.highest());
            out.writeShort(l.alerts());
            out.writeShort(l.orders());
        }
    }

    private static Voyage readVoyage(DataInputStream in) throws IOException {
        final long takeoff = in.readLong();
        final double seconds = in.readFloat();
        final double distance = in.readFloat();
        final double highest = in.readFloat();
        final int period = in.readInt();
        final long last = in.readLong();
        final float[] trace = floats(in, in.readUnsignedShort());
        final int n = in.readUnsignedByte();
        final List<Event> events = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            events.add(new Event(in.readLong(), in.readUnsignedByte(), in.readUnsignedByte(), in.readUTF(), in.readFloat()));
        }
        final Leg leg = in.readBoolean() ? new Leg(in.readLong(), in.readLong(), in.readFloat(), in.readFloat(),
                in.readFloat(), in.readShort(), in.readShort()) : null;
        return new Voyage(takeoff, seconds, distance, highest, period, last, trace, events, leg);
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

    private static void floats(DataOutputStream out, float[] a) throws IOException {
        for (final float f : a) {
            out.writeFloat(f);
        }
    }

    private static float[] floats(DataInputStream in, int n) throws IOException {
        final float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = in.readFloat();
        }
        return a;
    }
}
