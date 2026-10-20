package com.createstats.vccmk4.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record AerialView(
        String name, String pilot, boolean youPilot, boolean onCraft, String standDown,
        int mode, int leave, int feel,
        Flight flight, Side side,
        List<Balloon> balloons, List<Engine> engines, List<Surface> surfaces, List<Tool> tools, List<Light> lights,
        int[] keymap, int[] held,
        int noseTurn, boolean noseAuto, boolean bell, int blocks, float mass, float gravity,
        Learned learned, List<String> notes, boolean bridge, Nav nav, int staff) {
    public AerialView(String name, String pilot, boolean youPilot, boolean onCraft, String standDown, int mode, int leave,
                      int feel, Flight flight, Side side, List<Balloon> balloons, List<Engine> engines, List<Surface> surfaces,
                      List<Tool> tools, List<Light> lights, int[] keymap, int[] held, int noseTurn, boolean noseAuto, boolean bell,
                      int blocks, float mass, float gravity, Learned learned, List<String> notes, boolean bridge) {
        this(name, pilot, youPilot, onCraft, standDown, mode, leave, feel, flight, side, balloons, engines, surfaces, tools,
                lights, keymap, held, noseTurn, noseAuto, bell, blocks, mass, gravity, learned, notes, bridge, Nav.NONE, STAFF_NONE);
    }

    public AerialView(String name, String pilot, boolean youPilot, boolean onCraft, String standDown, int mode, int leave,
                      int feel, Flight flight, Side side, List<Balloon> balloons, List<Engine> engines, List<Surface> surfaces,
                      List<Tool> tools, List<Light> lights, int[] keymap, int[] held, int noseTurn, boolean noseAuto, boolean bell,
                      int blocks, float mass, float gravity, Learned learned, List<String> notes) {
        this(name, pilot, youPilot, onCraft, standDown, mode, leave, feel, flight, side, balloons, engines, surfaces, tools,
                lights, keymap, held, noseTurn, noseAuto, bell, blocks, mass, gravity, learned, notes, false);
    }

    public AerialView(String name, String pilot, boolean youPilot, boolean onCraft, String standDown, int mode, int leave,
                      int feel, Flight flight, Side side, List<Balloon> balloons, List<Engine> engines, List<Surface> surfaces,
                      List<Tool> tools, List<Light> lights, int[] keymap, int[] held, int noseTurn, boolean bell, int blocks,
                      float mass, float gravity, Learned learned, List<String> notes) {
        this(name, pilot, youPilot, onCraft, standDown, mode, leave, feel, flight, side, balloons, engines, surfaces, tools,
                lights, keymap, held, noseTurn, false, bell, blocks, mass, gravity, learned, notes, false);
    }

    public static final int ENGINE_OK = 0;
    public static final int ENGINE_STARVED = 1;
    public static final int ENGINE_OVERSTRESSED = 2;
    public static final int ENGINE_FOREIGN = 3;
    public static final int ENGINE_OFF = 4;

    public static final int KIND_GOVERNOR = 0;
    public static final int KIND_THRUSTER = 1;
    public static final int KIND_VECTOR = 2;
    public static final int KIND_BOOSTER = 3;

    public static final int STAFF_NONE = 0;
    public static final int STAFF_DRAGGED = 1;
    public static final int STAFF_LOCKED = 2;

    public static final int NAV_NONE = 0;
    public static final int NAV_LODESTONE = 1;
    public static final int NAV_TABLE = 2;

    public record Nav(int source, float x, float y, float z, float distance, float bearing, boolean tableElsewhere,
                      String problem) {
        public static final Nav NONE = new Nav(NAV_NONE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, false, "");

        public Nav {
            problem = problem == null ? "" : problem;
        }

        public boolean found() {
            return source != NAV_NONE;
        }
    }

    public static final int DIR_AHEAD = 0;
    public static final int DIR_ASTERN = 1;
    public static final int DIR_UP = 2;
    public static final int DIR_DOWN = 3;
    public static final int DIR_RIGHT = 4;
    public static final int DIR_LEFT = 5;

    public record Flight(float altitude, float climb, float targetAltitude, float ceiling, float balloonCeiling, float cap,
                         float ground, float keel, float worldTop,
                         float airspeed, float airForward, float airSide, float groundSpeed, float heading,
                         float targetHeading, float yawRate, float pitch, float roll,
                         float order, float engine, boolean braking, float targetSpeed, float topSpeed, int rings,
                         float liftUse, boolean liftShort, float trim, float windSpeed, float windFrom, float pressure,
                         float thrustAhead, float thrustAstern, float thrustTurn, boolean weighing) {
        public static final Flight NONE = new Flight(Float.NaN, 0f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, 320f, 0f, 0f, 0f, 0f, 0f, Float.NaN, 0f, 0f, 0f, 0f, 0f, false, Float.NaN, Float.NaN, 0, Float.NaN,
                false, 0f, Float.NaN, 0f, 1f, 0f, 0f, 0f, false);

        public Flight(float altitude, float climb, float targetAltitude, float ceiling, float balloonCeiling, float cap,
                      float ground, float keel, float worldTop, float airspeed, float airForward, float airSide, float groundSpeed,
                      float heading, float targetHeading, float yawRate, float pitch, float roll, float order, float engine,
                      boolean braking, float targetSpeed, float topSpeed, int rings, float liftUse, boolean liftShort, float trim,
                      float windSpeed, float windFrom, float pressure, float thrustAhead, float thrustAstern, float thrustTurn) {
            this(altitude, climb, targetAltitude, ceiling, balloonCeiling, cap, ground, keel, worldTop, airspeed, airForward,
                    airSide, groundSpeed, heading, targetHeading, yawRate, pitch, roll, order, engine, braking, targetSpeed,
                    topSpeed, rings, liftUse, liftShort, trim, windSpeed, windFrom, pressure, thrustAhead, thrustAstern,
                    thrustTurn, false);
        }
    }

    public record Side(float cell, int cols, int rows, float originLon, float originUp, byte[] bits, int topRows,
                       float originLat, byte[] topBits, byte[] envBits, byte[] topEnvBits) {
        public static final Side NONE = new Side(1f, 0, 0, 0f, 0f, new byte[0], 0, 0f, new byte[0]);

        public Side(float cell, int cols, int rows, float originLon, float originUp, byte[] bits, int topRows, float originLat,
                    byte[] topBits) {
            this(cell, cols, rows, originLon, originUp, bits, topRows, originLat, topBits, new byte[0], new byte[0]);
        }

        public Side {
            bits = bits == null ? new byte[0] : bits;
            topBits = topBits == null ? new byte[0] : topBits;
            envBits = envBits == null ? new byte[0] : envBits;
            topEnvBits = topEnvBits == null ? new byte[0] : topEnvBits;
        }

        public boolean at(int col, int row) {
            return bit(bits, col, row, rows);
        }

        public boolean topAt(int col, int row) {
            return bit(topBits, col, row, topRows);
        }

        public boolean env(int col, int row) {
            return bit(envBits, col, row, rows);
        }

        public boolean topEnv(int col, int row) {
            return bit(topEnvBits, col, row, topRows);
        }

        private boolean bit(byte[] b, int col, int row, int count) {
            if (col < 0 || row < 0 || col >= cols || row >= count) return false;
            int i = row * cols + col;
            return i >> 3 < b.length && (b[i >> 3] & (1 << (i & 7))) != 0;
        }
    }

    public record Balloon(long id, String kind, float gas, float target, float capacity, float maxGas, int heaters, int lit,
                          float pressure, boolean driven, float lon, float up, float lift) {
    }

    public record Engine(long pos, int job, boolean reverse, int limit, int rpm, float push, float thrust, int props,
                         float lon, float up, float lat, int dir, int state, boolean known, int kind, int supply, float aim) {
        public Engine(long pos, int job, boolean reverse, int limit, int rpm, float push, float thrust, int props, float lon,
                      float up, float lat, int dir, int state, boolean known) {
            this(pos, job, reverse, limit, rpm, push, thrust, props, lon, up, lat, dir, state, known, KIND_GOVERNOR, 0, Float.NaN);
        }

        public Engine(long pos, int job, boolean reverse, int limit, int rpm, float push, float thrust, int props, float lon,
                      float up, float lat, int dir, int state, boolean known, int kind, int supply) {
            this(pos, job, reverse, limit, rpm, push, thrust, props, lon, up, lat, dir, state, known, kind, supply, Float.NaN);
        }

        public boolean thruster() {
            return kind != KIND_GOVERNOR;
        }
    }

    public record Surface(long pos, int role, int degrees, boolean invert, int input, boolean latched, float angle,
                          boolean assembled, boolean locked, float lon, int hinge, float up) {
        public Surface(long pos, int role, int degrees, boolean invert, int input, boolean latched, float angle, boolean assembled,
                       boolean locked, float lon, int hinge) {
            this(pos, role, degrees, invert, input, latched, angle, assembled, locked, lon, hinge, Float.NaN);
        }
    }

    public record Tool(long pos, String first, int input, int rpm, boolean latched, boolean invert, float output,
                       boolean foreign, boolean gunnery, boolean bridge) {
        public Tool(long pos, String first, int input, int rpm, boolean latched, boolean invert, float output, boolean foreign) {
            this(pos, first, input, rpm, latched, invert, output, foreign, false, false);
        }
    }

    public record Light(long pos, int lamp, String first, String second, int job, boolean toggle, int strength,
                        float lon, float up, float lat) {
    }

    public record Learned(float drag, float wing, float lean, float hidden, float turnDrag, int propsKnown, int props) {
        public static final Learned NONE = new Learned(Float.NaN, 0f, 0f, 0f, Float.NaN, 0, 0);
    }

    public AerialView {
        name = name == null ? "" : name;
        pilot = pilot == null ? "" : pilot;
        standDown = standDown == null ? "" : standDown;
        flight = flight == null ? Flight.NONE : flight;
        side = side == null ? Side.NONE : side;
        balloons = balloons == null ? List.of() : List.copyOf(balloons);
        engines = engines == null ? List.of() : List.copyOf(engines);
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
        tools = tools == null ? List.of() : List.copyOf(tools);
        lights = lights == null ? List.of() : List.copyOf(lights);
        keymap = keymap == null ? new int[0] : keymap;
        held = held == null ? new int[0] : held;
        learned = learned == null ? Learned.NONE : learned;
        notes = notes == null ? List.of() : List.copyOf(notes);
        nav = nav == null ? Nav.NONE : nav;
    }

    public static final int MAX_PARTS = 64;
    public static final int MAX_NOTES = 12;
    private static final int MAX_STRING = 256;

    public static void write(FriendlyByteBuf buf, AerialView v) {
        buf.writeUtf(cut(v.name()), MAX_STRING);
        buf.writeUtf(cut(v.pilot()), MAX_STRING);
        buf.writeBoolean(v.youPilot());
        buf.writeBoolean(v.onCraft());
        buf.writeUtf(cut(v.standDown()), MAX_STRING);
        buf.writeVarInt(v.mode());
        buf.writeVarInt(v.leave());
        buf.writeVarInt(v.feel());
        Flight f = v.flight();
        float[] nums = {f.altitude(), f.climb(), f.targetAltitude(), f.ceiling(), f.balloonCeiling(), f.cap(), f.ground(),
                f.keel(), f.worldTop(), f.airspeed(), f.airForward(), f.airSide(), f.groundSpeed(), f.heading(),
                f.targetHeading(), f.yawRate(), f.pitch(), f.roll(), f.order(), f.engine(), f.targetSpeed(), f.topSpeed(),
                f.liftUse(), f.trim(), f.windSpeed(), f.windFrom(), f.pressure(), f.thrustAhead(), f.thrustAstern(),
                f.thrustTurn()};
        for (float n : nums) buf.writeFloat(n);
        buf.writeBoolean(f.braking());
        buf.writeVarInt(f.rings());
        buf.writeBoolean(f.liftShort());
        buf.writeBoolean(f.weighing());
        Side s = v.side();
        buf.writeFloat(s.cell());
        buf.writeVarInt(s.cols());
        buf.writeVarInt(s.rows());
        buf.writeFloat(s.originLon());
        buf.writeFloat(s.originUp());
        buf.writeByteArray(s.bits());
        buf.writeVarInt(s.topRows());
        buf.writeFloat(s.originLat());
        buf.writeByteArray(s.topBits());
        buf.writeByteArray(s.envBits());
        buf.writeByteArray(s.topEnvBits());
        writeList(buf, v.balloons(), (b, x) -> {
            b.writeLong(x.id());
            b.writeUtf(cut(x.kind()), MAX_STRING);
            for (float n : new float[] {x.gas(), x.target(), x.capacity(), x.maxGas(), x.pressure(), x.lon(), x.up(), x.lift()}) b.writeFloat(n);
            b.writeVarInt(x.heaters());
            b.writeVarInt(x.lit());
            b.writeBoolean(x.driven());
        });
        writeList(buf, v.engines(), (b, x) -> {
            b.writeLong(x.pos());
            b.writeVarInt(x.job());
            b.writeBoolean(x.reverse());
            b.writeVarInt(x.limit());
            b.writeVarInt(x.rpm());
            for (float n : new float[] {x.push(), x.thrust(), x.lon(), x.up(), x.lat()}) b.writeFloat(n);
            b.writeVarInt(x.props());
            b.writeVarInt(x.dir());
            b.writeVarInt(x.state());
            b.writeBoolean(x.known());
            b.writeVarInt(x.kind());
            b.writeVarInt(x.supply());
            b.writeFloat(x.aim());
        });
        writeList(buf, v.surfaces(), (b, x) -> {
            b.writeLong(x.pos());
            b.writeVarInt(x.role());
            b.writeVarInt(x.degrees());
            b.writeBoolean(x.invert());
            b.writeVarInt(x.input());
            b.writeBoolean(x.latched());
            b.writeFloat(x.angle());
            b.writeBoolean(x.assembled());
            b.writeBoolean(x.locked());
            b.writeFloat(x.lon());
            b.writeVarInt(x.hinge());
            b.writeFloat(x.up());
        });
        writeList(buf, v.tools(), (b, x) -> {
            b.writeLong(x.pos());
            b.writeUtf(cut(x.first()), MAX_STRING);
            b.writeVarInt(x.input());
            b.writeVarInt(x.rpm());
            b.writeBoolean(x.latched());
            b.writeBoolean(x.invert());
            b.writeFloat(x.output());
            b.writeBoolean(x.foreign());
            b.writeBoolean(x.gunnery());
            b.writeBoolean(x.bridge());
        });
        writeList(buf, v.lights(), (b, x) -> {
            b.writeLong(x.pos());
            b.writeVarInt(x.lamp() + 1);
            b.writeUtf(cut(x.first()), MAX_STRING);
            b.writeUtf(cut(x.second()), MAX_STRING);
            b.writeVarInt(x.job());
            b.writeBoolean(x.toggle());
            b.writeVarInt(x.strength());
            b.writeFloat(x.lon());
            b.writeFloat(x.up());
            b.writeFloat(x.lat());
        });
        buf.writeVarIntArray(v.keymap());
        buf.writeVarIntArray(v.held());
        buf.writeVarInt(v.noseTurn());
        buf.writeBoolean(v.noseAuto());
        buf.writeBoolean(v.bell());
        buf.writeVarInt(v.blocks() + 1);
        buf.writeFloat(v.mass());
        buf.writeFloat(v.gravity());
        Learned l = v.learned();
        for (float n : new float[] {l.drag(), l.wing(), l.lean(), l.hidden(), l.turnDrag()}) buf.writeFloat(n);
        buf.writeVarInt(l.propsKnown());
        buf.writeVarInt(l.props());
        List<String> notes = v.notes().size() > MAX_NOTES ? v.notes().subList(0, MAX_NOTES) : v.notes();
        buf.writeVarInt(notes.size());
        for (String n : notes) buf.writeUtf(cut(n), MAX_STRING);
        buf.writeBoolean(v.bridge());
        Nav nav = v.nav();
        buf.writeVarInt(nav.source());
        for (float n : new float[] {nav.x(), nav.y(), nav.z(), nav.distance(), nav.bearing()}) buf.writeFloat(n);
        buf.writeBoolean(nav.tableElsewhere());
        buf.writeUtf(cut(nav.problem()), MAX_STRING);
        buf.writeVarInt(v.staff());
    }

    public static AerialView read(FriendlyByteBuf buf) {
        String name = buf.readUtf(MAX_STRING);
        String pilot = buf.readUtf(MAX_STRING);
        boolean you = buf.readBoolean();
        boolean onCraft = buf.readBoolean();
        String standDown = buf.readUtf(MAX_STRING);
        int mode = buf.readVarInt();
        int leave = buf.readVarInt();
        int feel = buf.readVarInt();
        float[] n = new float[30];
        for (int i = 0; i < n.length; i++) n[i] = buf.readFloat();
        boolean braking = buf.readBoolean();
        int rings = buf.readVarInt();
        boolean liftShort = buf.readBoolean();
        boolean weighing = buf.readBoolean();
        Flight flight = new Flight(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9], n[10], n[11], n[12], n[13],
                n[14], n[15], n[16], n[17], n[18], n[19], braking, n[20], n[21], rings, n[22], liftShort, n[23], n[24], n[25],
                n[26], n[27], n[28], n[29], weighing);
        float cell = buf.readFloat();
        int cols = buf.readVarInt();
        int rows = buf.readVarInt();
        float oLon = buf.readFloat();
        float oUp = buf.readFloat();
        byte[] bits = buf.readByteArray(1 << 14);
        int topRows = buf.readVarInt();
        float oLat = buf.readFloat();
        byte[] topBits = buf.readByteArray(1 << 14);
        byte[] envBits = buf.readByteArray(1 << 14);
        byte[] topEnvBits = buf.readByteArray(1 << 14);
        Side side = new Side(cell, Math.min(cols, 128), Math.min(rows, 128), oLon, oUp, bits, Math.min(topRows, 128), oLat, topBits,
                envBits, topEnvBits);
        List<Balloon> balloons = readList(buf, b -> {
            long id = b.readLong();
            String kind = b.readUtf(MAX_STRING);
            float[] f = new float[8];
            for (int i = 0; i < f.length; i++) f[i] = b.readFloat();
            int heaters = b.readVarInt();
            int lit = b.readVarInt();
            boolean driven = b.readBoolean();
            return new Balloon(id, kind, f[0], f[1], f[2], f[3], heaters, lit, f[4], driven, f[5], f[6], f[7]);
        });
        List<Engine> engines = readList(buf, b -> {
            long pos = b.readLong();
            int job = b.readVarInt();
            boolean reverse = b.readBoolean();
            int limit = b.readVarInt();
            int rpm = b.readVarInt();
            float[] f = new float[5];
            for (int i = 0; i < f.length; i++) f[i] = b.readFloat();
            int props = b.readVarInt();
            int dir = b.readVarInt();
            int state = b.readVarInt();
            boolean known = b.readBoolean();
            int kind = b.readVarInt();
            int supply = b.readVarInt();
            float aim = b.readFloat();
            return new Engine(pos, job, reverse, limit, rpm, f[0], f[1], props, f[2], f[3], f[4], dir, state, known, kind, supply,
                    aim);
        });
        List<Surface> surfaces = readList(buf, b -> new Surface(b.readLong(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                b.readVarInt(), b.readBoolean(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readVarInt(),
                b.readFloat()));
        List<Tool> tools = readList(buf, b -> new Tool(b.readLong(), b.readUtf(MAX_STRING), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readBoolean(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
        List<Light> lights = readList(buf, b -> new Light(b.readLong(), b.readVarInt() - 1, b.readUtf(MAX_STRING),
                b.readUtf(MAX_STRING), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat()));
        int[] keymap = buf.readVarIntArray(256);
        int[] held = buf.readVarIntArray(256);
        int noseTurn = buf.readVarInt();
        boolean noseAuto = buf.readBoolean();
        boolean bell = buf.readBoolean();
        int blocks = buf.readVarInt() - 1;
        float mass = buf.readFloat();
        float gravity = buf.readFloat();
        float[] l = new float[5];
        for (int i = 0; i < l.length; i++) l[i] = buf.readFloat();
        Learned learned = new Learned(l[0], l[1], l[2], l[3], l[4], buf.readVarInt(), buf.readVarInt());
        int count = Math.min(buf.readVarInt(), MAX_NOTES);
        List<String> notes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) notes.add(buf.readUtf(MAX_STRING));
        boolean bridge = buf.readBoolean();
        int source = buf.readVarInt();
        float[] at = new float[5];
        for (int i = 0; i < at.length; i++) at[i] = buf.readFloat();
        boolean elsewhere = buf.readBoolean();
        Nav nav = new Nav(source, at[0], at[1], at[2], at[3], at[4], elsewhere, buf.readUtf(MAX_STRING));
        int staff = buf.readVarInt();
        return new AerialView(name, pilot, you, onCraft, standDown, mode, leave, feel, flight, side, balloons, engines,
                surfaces, tools, lights, keymap, held, noseTurn, noseAuto, bell, blocks, mass, gravity, learned, notes, bridge,
                nav, staff);
    }

    private interface Writer<T> {
        void write(FriendlyByteBuf buf, T value);
    }

    private interface Reader<T> {
        T read(FriendlyByteBuf buf);
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, Writer<T> w) {
        int n = Math.min(list.size(), MAX_PARTS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) w.write(buf, list.get(i));
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Reader<T> r) {
        int n = Math.min(buf.readVarInt(), MAX_PARTS);
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(r.read(buf));
        return out;
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
    }
}
