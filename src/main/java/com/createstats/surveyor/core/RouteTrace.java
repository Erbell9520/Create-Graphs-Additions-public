package com.createstats.surveyor.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

// every tick for a minute, the twitching lived between half second samples
public final class RouteTrace {
    public static final int CAPACITY = 1200;

    public static final int MAX_ROWS = 72_000;

    public static final int MAX_NOTES = 64;

    public static final double G = 11.0;

    public static final double JOLT_G = 2.0;

    public static final double SURGE_BAND = 0.3;

    public static final double STRAIGHT_KAPPA = 0.01;

    static final int MAGIC = 0x52545243;
    static final int FORMAT = 1;

    static final short NAN = Short.MIN_VALUE;

    public enum Ch {
        STATE(1),

        MODE(1),

        S(0),
        X(0),
        Z(0),

        LATERAL(1000),

        HEADING(100),

        SPEED(500),

        ACCEL(100),

        YAW_RAW(1000),
        YAW(1000),

        PLAN(500),
        TARGET(500),
        TARGET_ACCEL(100),

        CMD_THROTTLE(10000),
        CMD_BRAKE(10000),
        CMD_STEER(10000),

        OUT_THROTTLE(10000),
        OUT_BRAKE(10000),
        OUT_STEER(10000),

        WHEEL_WANT(100),
        WHEEL_SENT(100),

        KAPPA(10000),
        PATH_KAPPA(10000),

        OBSTACLE(100),
        GAP(100),

        PITCH(100),
        ROLL(100);

        public final int scale;

        Ch(int scale) {
            this.scale = scale;
        }

        public boolean isFloat() {
            return scale == 0;
        }
    }

    static final Ch[] CH = Ch.values();

    public record Note(long tick, String text) {
    }

    private final int capacity;
    private final float[][] data;
    private final long[] ticks;
    private final ArrayDeque<Note> notes = new ArrayDeque<>();
    private int head;
    private int count;
    private final float[] row = new float[CH.length];
    private long rowTick = Long.MIN_VALUE;

    public RouteTrace() {
        this(CAPACITY);
    }

    public RouteTrace(int capacity) {
        this.capacity = Math.max(1, Math.min(MAX_ROWS, capacity));
        this.data = new float[CH.length][this.capacity];
        this.ticks = new long[this.capacity];
    }

    public int capacity() {
        return capacity;
    }

    public void begin(long tick) {
        rowTick = tick;
        Arrays.fill(row, Float.NaN);
    }

    public void put(Ch c, double v) {
        row[c.ordinal()] = (float) v;
    }

    public void end() {
        if (rowTick == Long.MIN_VALUE) return;
        for (int c = 0; c < CH.length; c++) data[c][head] = row[c];
        ticks[head] = rowTick;
        head = (head + 1) % capacity;
        count = Math.min(capacity, count + 1);
        rowTick = Long.MIN_VALUE;
    }

    public void note(long tick, String text) {
        if (text == null || text.isEmpty()) return;
        while (notes.size() >= MAX_NOTES) notes.removeFirst();
        notes.addLast(new Note(tick, text.length() > 200 ? text.substring(0, 200) : text));
    }

    public void clear() {
        head = 0;
        count = 0;
        notes.clear();
        rowTick = Long.MIN_VALUE;
    }

    public int size() {
        return count;
    }

    private int index(int i) {
        return Math.floorMod(head - count + i, capacity);
    }

    public double get(Ch c, int i) {
        return data[c.ordinal()][index(i)];
    }

    public long tick(int i) {
        return ticks[index(i)];
    }

    public List<Note> notes() {
        return List.copyOf(notes);
    }

    public String csv() {
        StringBuilder b = new StringBuilder("tick");
        for (Ch c : CH) b.append(',').append(c.name().toLowerCase(Locale.ROOT));
        b.append('\n');
        for (int i = 0; i < count; i++) {
            b.append(tick(i));
            for (Ch c : CH) {
                double v = get(c, i);
                b.append(',');
                if (!Double.isNaN(v)) b.append(String.format(Locale.ROOT, c.isFloat() ? "%.3f" : "%.4f", v));
            }
            b.append('\n');
        }
        for (Note n : notes) b.append("# ").append(n.tick()).append(' ').append(n.text()).append('\n');
        return b.toString();
    }

    public record Measures(int jolts, double joltPeakG, double surgesPerMinute, double cruiseSeconds, double speedErrorRms,
                           double worstSlowing, int brakeApplications, double wheelHuntPerSecond, double straightSeconds,
                           double sideChangesPerSecond, double crossTrackRms, double crossTrackMax, double runningSeconds) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "jolts %d (peak %.1f g), surges %.1f/min over %.0f s cruise, speed err %.2f, slowing %.2f b/s2, "
                            + "brakes %d, wheel hunting %.2f/s over %.0f s straight, sides %.3f/s, xtrack rms %.2f max %.2f, running %.0f s",
                    jolts, joltPeakG, surgesPerMinute, cruiseSeconds, speedErrorRms, worstSlowing, brakeApplications,
                    wheelHuntPerSecond, straightSeconds, sideChangesPerSecond, crossTrackRms, crossTrackMax, runningSeconds);
        }
    }

    public static final double HUNT_BAND = 0.25;

    static boolean running(double state) {
        int s = (int) Math.round(state);
        return s == RouteRunner.State.RUNNING.ordinal() || s == RouteRunner.State.APPROACHING.ordinal()
                || s == RouteRunner.State.DEPARTING.ordinal();
    }

    static boolean normal(double state) {
        int s = (int) Math.round(state);
        return running(state) || s == RouteRunner.State.JOINING.ordinal() || s == RouteRunner.State.WAITING.ordinal();
    }

    public Measures measures() {
        return measures(60);
    }

    public Measures measures(int settleTicks) {
        int n = count;
        double[] v = column(Ch.SPEED);
        double[] state = column(Ch.STATE);
        double[] plan = column(Ch.PLAN);
        double[] yaw = column(Ch.YAW);
        double[] yawRaw = column(Ch.YAW_RAW);
        double[] lat = column(Ch.LATERAL);
        double[] brake = column(Ch.OUT_BRAKE);
        double[] steer = column(Ch.OUT_STEER);
        double[] sent = column(Ch.WHEEL_SENT);
        double[] pk = column(Ch.PATH_KAPPA);
        double[] targetAccel = column(Ch.TARGET_ACCEL);

        double[] target = column(Ch.TARGET);
        double[] aim = new double[n];
        for (int i = 0; i < n; i++) aim[i] = finite(target[i]) ? target[i] : plan[i];

        double[] raw = new double[n];
        for (int i = 1; i < n; i++) {
            raw[i] = consecutive(i) && finite(v[i]) && finite(v[i - 1]) ? (v[i] - v[i - 1]) * 20.0 : Double.NaN;
        }
        double[] held = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            int k = 0;
            for (int j = Math.max(1, i - 2); j <= Math.min(n - 1, i + 2); j++) {
                if (finite(raw[j])) {
                    sum += raw[j];
                    k++;
                }
            }
            held[i] = k == 0 ? Double.NaN : sum / k;
        }

        int jolts = 0;
        double peak = 0.0;
        int runTicks = 0;
        int since = 0;
        double xtSq = 0.0;
        int xtN = 0;
        double xtMax = 0.0;
        double cruise = 0.0;
        double errSq = 0.0;
        int errN = 0;
        int surges = 0;
        int surgeSign = 0;
        double worstSlowing = 0.0;
        int brakes = 0;
        boolean braked = false;
        double straight = 0.0;
        int hunts = 0;
        int huntDir = 0;
        double extreme = Double.NaN;
        int sides = 0;
        int lastSide = 0;
        for (int i = 0; i < n; i++) {
            boolean run = finite(state[i]) && running(state[i]);
            since = run ? since + 1 : 0;
            if (finite(state[i]) && normal(state[i]) && finite(raw[i])) {
                double w = finite(yaw[i]) ? yaw[i] : (finite(yawRaw[i]) ? yawRaw[i] : 0.0);
                double lateralAccel = finite(v[i]) ? v[i] * w : 0.0;
                double g = Math.hypot(raw[i], lateralAccel) / G;
                if (g >= JOLT_G) {
                    jolts++;
                    peak = Math.max(peak, g);
                }
            }
            if (!run) {
                braked = false;
                surgeSign = 0;
                extreme = Double.NaN;
                huntDir = 0;
                continue;
            }
            runTicks++;
            boolean b = finite(brake[i]) && brake[i] > 1.0e-3;
            if (b && !braked) brakes++;
            braked = b;
            if (since < settleTicks) continue;

            if (finite(lat[i])) {
                double e = Math.abs(lat[i]);
                xtSq += e * e;
                xtN++;
                xtMax = Math.max(xtMax, e);
            }
            double st = finite(steer[i]) ? steer[i] : 0.0;
            int side = st > 0.05 ? 1 : (st < -0.05 ? -1 : 0);
            if (side != 0 && lastSide != 0 && side != lastSide) sides++;
            if (side != 0) lastSide = side;

            boolean settled = !finite(targetAccel[i]) || Math.abs(targetAccel[i]) < 0.15;
            if (settled && steady(aim, i) && finite(v[i]) && finite(aim[i])) {
                cruise += 0.05;
                errSq += (v[i] - aim[i]) * (v[i] - aim[i]);
                errN++;
                double a = held[i];
                if (finite(a)) {
                    if (a > SURGE_BAND) {
                        if (surgeSign < 0) surges++;
                        surgeSign = 1;
                    } else if (a < -SURGE_BAND) {
                        if (surgeSign > 0) surges++;
                        surgeSign = -1;
                    }
                }
            } else {
                surgeSign = 0;
            }
            if (slowing(plan, targetAccel, i) && finite(held[i])) worstSlowing = Math.max(worstSlowing, -held[i]);

            if (isStraight(pk, i) && finite(sent[i])) {
                straight += 0.05;
                double a = sent[i];
                if (Double.isNaN(extreme)) {
                    extreme = a;
                } else if (huntDir >= 0 && a < extreme - HUNT_BAND) {
                    if (huntDir > 0) hunts++;
                    huntDir = -1;
                    extreme = a;
                } else if (huntDir <= 0 && a > extreme + HUNT_BAND) {
                    if (huntDir < 0) hunts++;
                    huntDir = 1;
                    extreme = a;
                } else if (huntDir > 0) {
                    extreme = Math.max(extreme, a);
                } else if (huntDir < 0) {
                    extreme = Math.min(extreme, a);
                }
            } else {
                extreme = Double.NaN;
                huntDir = 0;
            }
        }
        double runSeconds = runTicks * 0.05;
        double judged = Math.max(1.0e-9, xtN * 0.05);
        return new Measures(jolts, peak, cruise > 0 ? surges / (cruise / 60.0) : 0.0, cruise,
                errN > 0 ? Math.sqrt(errSq / errN) : 0.0, worstSlowing, brakes,
                straight > 0 ? hunts / straight : 0.0, straight, xtN > 0 ? sides / judged : 0.0,
                xtN > 0 ? Math.sqrt(xtSq / xtN) : 0.0, xtMax, runSeconds);
    }

    private boolean steady(double[] plan, int i) {
        if (!finite(plan[i]) || plan[i] < 0.5) return false;
        double lo = plan[i];
        double hi = plan[i];
        for (int j = Math.max(0, i - 40); j <= Math.min(count - 1, i + 40); j++) {
            if (!finite(plan[j])) return false;
            lo = Math.min(lo, plan[j]);
            hi = Math.max(hi, plan[j]);
        }
        return hi - lo <= 0.05 * hi;
    }

    private boolean slowing(double[] plan, double[] targetAccel, int i) {
        if (finite(targetAccel[i])) return targetAccel[i] < -0.2;
        if (i < 10 || !finite(plan[i]) || !finite(plan[i - 10])) return false;
        return (plan[i] - plan[i - 10]) * 2.0 < -0.2;
    }

    private boolean isStraight(double[] pk, int i) {
        for (int j = Math.max(0, i - 20); j <= Math.min(count - 1, i + 20); j++) {
            if (!finite(pk[j]) || Math.abs(pk[j]) >= STRAIGHT_KAPPA) return false;
        }
        return true;
    }

    private boolean consecutive(int i) {
        return tick(i) - tick(i - 1) == 1L;
    }

    private double[] column(Ch c) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = get(c, i);
        return out;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    public byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeInt(CH.length);
            out.writeInt(count);
            long prev = count > 0 ? tick(0) : 0L;
            out.writeLong(prev);
            for (int i = 1; i < count; i++) {
                long t = tick(i);
                out.writeInt((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, t - prev)));
                prev = t;
            }
            for (Ch c : CH) {
                out.writeInt(c.scale);
                for (int i = 0; i < count; i++) {
                    double v = get(c, i);
                    if (c.isFloat()) out.writeFloat((float) v);
                    else out.writeShort(quantise(v, c.scale));
                }
            }
            out.writeInt(notes.size());
            for (Note note : notes) {
                out.writeLong(note.tick());
                out.writeUTF(note.text());
            }
            out.flush();
            byte[] body = deflate(bytes.toByteArray());
            byte[] framed = Arrays.copyOf(body, body.length + 4);
            int crc = crc(body, body.length);
            for (int i = 0; i < 4; i++) framed[body.length + i] = (byte) (crc >>> (24 - 8 * i));
            return framed;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static RouteTrace load(byte[] framed) {
        RouteTrace empty = new RouteTrace();
        if (framed == null || framed.length < 5) return empty;
        int n = framed.length - 4;
        int stored = 0;
        for (int i = 0; i < 4; i++) stored = (stored << 8) | (framed[n + i] & 0xFF);
        if (stored != crc(framed, n)) return empty;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(inflate(Arrays.copyOf(framed, n))));
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) return empty;
            int channels = in.readInt();
            int rows = in.readInt();
            if (rows < 0 || rows > MAX_ROWS || channels < 0 || channels > 256) return empty;
            RouteTrace t = new RouteTrace(Math.max(CAPACITY, rows));
            long[] ts = new long[rows];
            if (rows > 0) ts[0] = in.readLong();
            else in.readLong();
            for (int i = 1; i < rows; i++) ts[i] = ts[i - 1] + in.readInt();
            float[][] cols = new float[CH.length][rows];
            for (float[] col : cols) Arrays.fill(col, Float.NaN);
            for (int c = 0; c < channels; c++) {
                boolean known = c < CH.length;
                int scale = in.readInt();
                for (int i = 0; i < rows; i++) {
                    float v = scale == 0 ? in.readFloat() : dequantise(in.readShort(), scale);
                    if (known) cols[c][i] = v;
                }
            }
            int noteCount = in.readInt();
            for (int k = 0; k < noteCount && k < MAX_NOTES; k++) t.note(in.readLong(), in.readUTF());
            for (int i = 0; i < rows; i++) {
                t.begin(ts[i]);
                for (int c = 0; c < CH.length; c++) t.row[c] = cols[c][i];
                t.end();
            }
            return t;
        } catch (IOException | DataFormatException | RuntimeException e) {
            return new RouteTrace();
        }
    }

    static short quantise(double v, int scale) {
        if (Double.isNaN(v)) return NAN;
        double q = Math.rint(v * scale);
        if (q >= Short.MAX_VALUE) return Short.MAX_VALUE;
        if (q <= -Short.MAX_VALUE) return (short) -Short.MAX_VALUE;
        return (short) q;
    }

    static float dequantise(short s, int scale) {
        if (s == NAN) return Float.NaN;
        return (float) (s / (double) Math.max(1, scale));
    }

    private static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                int k = d.deflate(buf);
                out.write(buf, 0, k);
            }
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    private static byte[] inflate(byte[] packed) throws DataFormatException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4 + 64);
            byte[] buf = new byte[8192];
            int guard = 0;
            while (!inf.finished()) {
                int k = inf.inflate(buf);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, k);

                if ((guard += k) > 4_000_000) throw new DataFormatException("too large");
            }
            return out.toByteArray();
        } finally {
            inf.end();
        }
    }

    private static int crc(byte[] data, int len) {
        CRC32 c = new CRC32();
        c.update(data, 0, len);
        return (int) c.getValue();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("usage: RouteTrace <trace file>");
            return;
        }
        RouteTrace t = load(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0])));
        System.out.print(t.csv());
        System.out.println("# " + t.measures());
    }

    static List<Ch> channels() {
        return new ArrayList<>(List.of(CH));
    }
}
