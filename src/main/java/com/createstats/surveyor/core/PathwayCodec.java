package com.createstats.surveyor.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PathwayCodec {
    public static final int FORMAT = 2;

    private PathwayCodec() {
    }

    public static byte[] encode(Pathway p) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + p.nodes().size() * 48);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            write(out, p);
        } catch (IOException e) {
            throw new IllegalStateException("encoding a pathway into memory cannot fail", e);
        }
        return bytes.toByteArray();
    }

    public static Pathway decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return read(in);
        }
    }

    public static void write(DataOutputStream out, Pathway p) throws IOException {
        out.writeByte(FORMAT);
        uuid(out, p.id());
        out.writeUTF(p.name());
        out.writeByte(p.colour());
        out.writeByte(p.kind().ordinal());
        uuid(out, p.owner());
        out.writeBoolean(p.shared());
        out.writeDouble(p.cruise());
        out.writeByte(p.comfort().ordinal());
        out.writeLong(p.version());
        out.writeInt(p.nextNodeId());
        out.writeShort(p.nodes().size());
        for (PathNode n : p.nodes()) node(out, n);

        out.writeInt(p.flags());
    }

    public static Pathway read(DataInputStream in) throws IOException {
        int format = in.readUnsignedByte();
        if (format < 1 || format > FORMAT) throw new IOException("unknown pathway format " + format);
        UUID id = uuid(in);
        String name = in.readUTF();
        int colour = in.readUnsignedByte();
        PathKind kind = PathKind.byOrdinal(in.readUnsignedByte());
        UUID owner = uuid(in);
        boolean shared = in.readBoolean();
        double cruise = in.readDouble();
        Comfort comfort = Comfort.byOrdinal(in.readUnsignedByte());
        long version = in.readLong();
        int next = in.readInt();
        int count = in.readUnsignedShort();
        if (count > Pathway.MAX_NODES) throw new IOException("pathway with " + count + " nodes");
        List<PathNode> nodes = new ArrayList<>(count);
        int maxId = 0;
        for (int i = 0; i < count; i++) {
            PathNode n = node(in).sane();
            nodes.add(n);
            maxId = Math.max(maxId, n.id());
        }
        int flags = format >= 2 ? in.readInt() & Pathway.AMPHIBIOUS : 0;
        Pathway p = new Pathway(id, name.length() > Pathway.MAX_NAME ? name.substring(0, Pathway.MAX_NAME) : name,
                colour & 15, kind, owner, shared, cruise, comfort, version, Math.max(next, maxId + 1), nodes, flags);

        double c = Double.isFinite(cruise) ? Math.max(Pathway.MIN_CRUISE, Math.min(PathNode.MAX_SPEED, cruise)) : Pathway.DEFAULT_CRUISE;
        return c == cruise ? p : new Pathway(p.id(), p.name(), p.colour(), p.kind(), p.owner(), p.shared(), c, p.comfort(),
                p.version(), p.nextNodeId(), p.nodes(), p.flags());
    }

    private static void node(DataOutputStream out, PathNode n) throws IOException {
        out.writeInt(n.id());
        out.writeDouble(n.x());
        out.writeDouble(n.y());
        out.writeDouble(n.z());
        out.writeUTF(n.name());
        out.writeDouble(n.speedLimit());
        out.writeByte(n.corner().ordinal());
        Stop s = n.stop();
        out.writeBoolean(s != null);
        if (s != null) stop(out, s);
    }

    private static PathNode node(DataInputStream in) throws IOException {
        int id = in.readInt();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        String name = in.readUTF();
        double limit = in.readDouble();
        Corner corner = Corner.byOrdinal(in.readUnsignedByte());
        Stop stop = in.readBoolean() ? stop(in) : null;
        return new PathNode(id, x, y, z, name, limit, corner, stop);
    }

    public static void stop(DataOutputStream out, Stop s) throws IOException {
        out.writeByte(s.rules().size());
        for (List<StopCondition> alt : s.rules()) {
            out.writeByte(alt.size());
            for (StopCondition c : alt) condition(out, c);
        }
        out.writeInt(s.leaveAfter());
        out.writeUTF(s.dockFirst());
        out.writeUTF(s.dockSecond());
        out.writeBoolean(s.indicate());
        out.writeByte(s.hornAux());
    }

    public static Stop stop(DataInputStream in) throws IOException {
        int alts = in.readUnsignedByte();
        if (alts > Stop.MAX_ALTERNATIVES) throw new IOException("stop with " + alts + " alternatives");
        List<List<StopCondition>> rules = new ArrayList<>(alts);
        for (int i = 0; i < alts; i++) {
            int n = in.readUnsignedByte();
            if (n > Stop.MAX_CONDITIONS) throw new IOException("stop alternative with " + n + " conditions");
            List<StopCondition> alt = new ArrayList<>(n);
            for (int j = 0; j < n; j++) alt.add(condition(in));
            rules.add(alt);
        }
        int leave = in.readInt();
        String a = in.readUTF();
        String b = in.readUTF();
        boolean indicate = in.readBoolean();
        int horn = in.readByte();
        return new Stop(rules, leave, a, b, indicate, horn).sane();
    }

    private static void condition(DataOutputStream out, StopCondition c) throws IOException {
        switch (c) {
            case StopCondition.Wait w -> {
                out.writeByte(0);
                out.writeInt(w.seconds());
            }
            case StopCondition.Idle i -> {
                out.writeByte(1);
                out.writeInt(i.seconds());
            }
            case StopCondition.Items i -> {
                out.writeByte(2);
                out.writeByte(i.op().ordinal());
                out.writeInt(i.amount());
                out.writeBoolean(i.stacks());
                out.writeUTF(i.item());
            }
            case StopCondition.Fluid f -> {
                out.writeByte(3);
                out.writeByte(f.op().ordinal());
                out.writeInt(f.buckets());
                out.writeUTF(f.fluid());
            }
            case StopCondition.Link l -> {
                out.writeByte(4);
                out.writeUTF(l.first());
                out.writeUTF(l.second());
                out.writeBoolean(l.powered());
            }
            case StopCondition.TimeOfDay t -> {
                out.writeByte(5);
                out.writeByte(t.hour());
                out.writeByte(t.minute());
                out.writeByte(t.rotation());
            }
            case StopCondition.Players pl -> {
                out.writeByte(6);
                out.writeByte(pl.count());
                out.writeBoolean(pl.orMore());
            }
            case StopCondition.Powered pw -> out.writeByte(7);
            case StopCondition.Docked d -> out.writeByte(8);
        }
    }

    private static StopCondition condition(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case 0 -> new StopCondition.Wait(in.readInt());
            case 1 -> new StopCondition.Idle(in.readInt());
            case 2 -> new StopCondition.Items(StopCondition.Op.byOrdinal(in.readUnsignedByte()), in.readInt(), in.readBoolean(), in.readUTF());
            case 3 -> new StopCondition.Fluid(StopCondition.Op.byOrdinal(in.readUnsignedByte()), in.readInt(), in.readUTF());
            case 4 -> new StopCondition.Link(in.readUTF(), in.readUTF(), in.readBoolean());
            case 5 -> new StopCondition.TimeOfDay(in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte());
            case 6 -> new StopCondition.Players(in.readUnsignedByte(), in.readBoolean());
            case 7 -> new StopCondition.Powered();
            case 8 -> new StopCondition.Docked();
            default -> throw new IOException("unknown stop condition " + kind);
        };
    }

    public static byte[] encodeStop(Stop s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            stop(out, s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static Stop decodeStop(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return stop(in);
        }
    }

    static final int SCHEDULE_FORMAT = 1;

    public static byte[] encodeSchedule(RouteSchedule sch) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(SCHEDULE_FORMAT);
            out.writeBoolean(sch.cyclic());
            out.writeByte(sch.entries().size());
            for (RouteSchedule.Entry e : sch.entries()) {
                switch (e.instruction()) {
                    case RouteSchedule.TravelTo t -> {
                        out.writeByte(0);
                        out.writeUTF(t.station());
                    }
                    case RouteSchedule.Rename r -> {
                        out.writeByte(1);
                        out.writeUTF(r.title());
                    }
                    case RouteSchedule.Throttle t -> {
                        out.writeByte(2);
                        out.writeByte(t.percent());
                    }
                    case RouteSchedule.Unsupported u -> {
                        out.writeByte(3);
                        out.writeUTF(u.id());
                        out.writeUTF(u.why());
                    }
                }
                out.writeByte(e.conditions().size());
                for (List<StopCondition> col : e.conditions()) {
                    out.writeByte(col.size());
                    for (StopCondition c : col) condition(out, c);
                }
                out.writeByte(Math.min(8, e.declined().size()));
                for (int i = 0; i < Math.min(8, e.declined().size()); i++) out.writeUTF(RouteSchedule.cut(e.declined().get(i)));
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return bytes.toByteArray();
    }

    public static RouteSchedule decodeSchedule(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int format = in.readUnsignedByte();
            if (format != SCHEDULE_FORMAT) throw new IOException("schedule format " + format);
            boolean cyclic = in.readBoolean();
            int n = in.readUnsignedByte();
            if (n > RouteSchedule.MAX_ENTRIES) throw new IOException("schedule of " + n + " entries");
            List<RouteSchedule.Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int kind = in.readUnsignedByte();
                RouteSchedule.Instruction ins = switch (kind) {
                    case 0 -> new RouteSchedule.TravelTo(in.readUTF());
                    case 1 -> new RouteSchedule.Rename(in.readUTF());
                    case 2 -> new RouteSchedule.Throttle(in.readUnsignedByte());
                    case 3 -> new RouteSchedule.Unsupported(in.readUTF(), in.readUTF());
                    default -> throw new IOException("unknown schedule instruction " + kind);
                };
                int cols = in.readUnsignedByte();
                if (cols > Stop.MAX_ALTERNATIVES) throw new IOException("schedule entry with " + cols + " columns");
                List<List<StopCondition>> conditions = new ArrayList<>(cols);
                for (int c = 0; c < cols; c++) {
                    int m = in.readUnsignedByte();
                    if (m > Stop.MAX_CONDITIONS) throw new IOException("schedule column with " + m + " conditions");
                    List<StopCondition> col = new ArrayList<>(m);
                    for (int k = 0; k < m; k++) col.add(condition(in));
                    conditions.add(col);
                }
                int d = in.readUnsignedByte();
                List<String> declined = new ArrayList<>(d);
                for (int k = 0; k < Math.min(d, 8); k++) declined.add(in.readUTF());
                entries.add(new RouteSchedule.Entry(ins, conditions, declined));
            }
            return new RouteSchedule(entries, cyclic);
        }
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        UUID u = id == null ? new UUID(0L, 0L) : id;
        out.writeLong(u.getMostSignificantBits());
        out.writeLong(u.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
