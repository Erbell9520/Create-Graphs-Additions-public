package com.createstats.cinecam.film;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

// same samples byte for byte, one moov instead of a moof every 0.3s
public final class Mp4Faststart {
    public static final String PART = ".indexing";

    static final long LEFTOVER_MS = 10 * 60 * 1000L;
    private static final int MOVE_TRIES = 8;

    private Mp4Faststart() {
    }

    public static boolean fragmented(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            for (Box b : boxes(ch, 0, ch.size())) {
                if (b.is("moof")) return true;
                if (b.is("moov")) {
                    ByteBuffer moov = read(ch, b.body, b.end);
                    for (Box c : boxes(moov, 0, moov.limit())) if (c.is("mvex")) return true;
                }
            }
        }
        return false;
    }

    public static void rewriteInPlace(Path film, BooleanSupplier stop) throws IOException {
        Path part = film.resolveSibling(film.getFileName() + PART);
        try {
            rewrite(film, part, stop);
            if (stop.getAsBoolean()) throw new IOException("stopped before the film was replaced");
            replace(part, film);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static void replace(Path part, Path film) throws IOException {
        for (int i = 1; ; i++) {
            try {
                try {
                    Files.move(part, film, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(part, film, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                if (i >= MOVE_TRIES) throw e;
            } catch (FileSystemException e) {
                if (i >= MOVE_TRIES || e instanceof java.nio.file.NoSuchFileException) throw e;
            }
            try {
                Thread.sleep(100L * i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while replacing " + film, e);
            }
        }
    }

    public static int clearLeftovers(Path dir) {
        int n = 0;
        if (!Files.isDirectory(dir)) return 0;
        long now = System.currentTimeMillis();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*" + PART)) {
            for (Path p : ds) {
                try {
                    if (now - Files.getLastModifiedTime(p).toMillis() > LEFTOVER_MS && Files.deleteIfExists(p)) n++;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return n;
    }

    public static void rewrite(Path in, Path out, BooleanSupplier stop) throws IOException {
        try (FileChannel src = FileChannel.open(in, StandardOpenOption.READ)) {
            Movie movie = parse(src);
            byte[] ftyp = box("ftyp", cat(ascii("isom"), u32(0x200), ascii("isomiso2avc1mp41")));
            byte[] sized = movie.moov(null);
            long dataStart = ftyp.length + sized.length + 16L;
            long[] newAt = new long[movie.runs.size()];
            long at = dataStart;
            for (int i = 0; i < newAt.length; i++) {
                newAt[i] = at;
                at += movie.runs.get(i).length;
            }
            byte[] moov = movie.moov(newAt);
            if (moov.length != sized.length) throw new IOException("the index changed size between passes");
            try (FileChannel dst = FileChannel.open(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writeFully(dst, ByteBuffer.wrap(ftyp));
                writeFully(dst, ByteBuffer.wrap(moov));
                ByteBuffer mdat = ByteBuffer.allocate(16);
                mdat.putInt(1).put(ascii("mdat")).putLong(16 + at - dataStart).flip();
                writeFully(dst, mdat);
                for (Run r : movie.runs) {
                    if (stop.getAsBoolean()) throw new IOException("stopped");
                    long done = 0;
                    while (done < r.length) {
                        long n = src.transferTo(r.from + done, r.length - done, dst);
                        if (n <= 0) throw new IOException("the film ended inside a sample run at " + (r.from + done));
                        done += n;
                    }
                }
                dst.force(false);
            }
        }
    }

    private record Box(String type, long start, long body, long end) {
        boolean is(String t) {
            return type.equals(t);
        }
    }

    private record Run(int track, long from, long length, int samples) {
    }

    private static final class Track {
        final int id;
        byte[] tkhd;
        byte[] mdhd;
        byte[] hdlr;
        byte[] edts;
        byte[] minfHead = new byte[0];
        byte[] stsd;
        long timescale;
        int trexDuration;
        int trexSize;
        int trexFlags;
        Ints durations = new Ints();
        Ints sizes = new Ints();
        Ints compositions = new Ints();
        BitSet nonSync = new BitSet();
        boolean signedCompositions;
        long length;

        long clock;

        Track(int id) {
            this.id = id;
        }

        int count() {
            return durations.size;
        }
    }

    private static final class Movie {
        byte[] mvhd;
        long movieScale;
        final Map<Integer, Track> tracks = new LinkedHashMap<>();
        final List<Run> runs = new ArrayList<>();

        byte[] moov(long[] at) throws IOException {
            ByteArrayOutputStream traks = new ByteArrayOutputStream();
            long longest = 0;
            for (Track t : tracks.values()) {
                long movieLength = t.timescale == 0 ? 0 : t.length * movieScale / t.timescale;
                longest = Math.max(longest, movieLength);
                byte[] stbl = box("stbl", cat(t.stsd, stts(t), ctts(t), stss(t), stsc(t), stsz(t), co64(t, at)));
                byte[] minf = box("minf", cat(t.minfHead, stbl));
                byte[] mdia = box("mdia", cat(box("mdhd", withDuration(t.mdhd, false, t.length)), t.hdlr, minf));
                traks.write(box("trak", cat(box("tkhd", withDuration(t.tkhd, true, movieLength)),
                        t.edts == null ? new byte[0] : t.edts, mdia)));
            }
            return box("moov", cat(box("mvhd", withDuration(mvhd, false, longest)), traks.toByteArray()));
        }

        byte[] stts(Track t) {
            Ints counts = new Ints();
            Ints values = new Ints();
            runLengths(t.durations, counts, values);
            return table("stts", 0, counts, values);
        }

        byte[] ctts(Track t) {
            boolean any = false;
            for (int i = 0; i < t.compositions.size && !any; i++) any = t.compositions.get(i) != 0;
            if (!any) return new byte[0];
            Ints counts = new Ints();
            Ints values = new Ints();
            runLengths(t.compositions, counts, values);
            return table("ctts", t.signedCompositions ? 1 : 0, counts, values);
        }

        byte[] stss(Track t) {
            if (t.nonSync.isEmpty()) return new byte[0];
            Ints sync = new Ints();
            for (int i = 0; i < t.count(); i++) if (!t.nonSync.get(i)) sync.add(i + 1);
            ByteBuffer b = ByteBuffer.allocate(8 + 4 * sync.size);
            b.putInt(0).putInt(sync.size);
            for (int i = 0; i < sync.size; i++) b.putInt(sync.get(i));
            return box("stss", b.array());
        }

        byte[] stsc(Track t) {
            Ints first = new Ints();
            Ints per = new Ints();
            int chunk = 0;
            for (Run r : runs) {
                if (r.track != t.id) continue;
                chunk++;
                if (per.size == 0 || per.get(per.size - 1) != r.samples) {
                    first.add(chunk);
                    per.add(r.samples);
                }
            }
            ByteBuffer b = ByteBuffer.allocate(8 + 12 * first.size);
            b.putInt(0).putInt(first.size);
            for (int i = 0; i < first.size; i++) b.putInt(first.get(i)).putInt(per.get(i)).putInt(1);
            return box("stsc", b.array());
        }

        byte[] stsz(Track t) {
            ByteBuffer b = ByteBuffer.allocate(12 + 4 * t.sizes.size);
            b.putInt(0).putInt(0).putInt(t.sizes.size);
            for (int i = 0; i < t.sizes.size; i++) b.putInt(t.sizes.get(i));
            return box("stsz", b.array());
        }

        byte[] co64(Track t, long[] at) {
            int chunks = 0;
            for (Run r : runs) if (r.track == t.id) chunks++;
            ByteBuffer b = ByteBuffer.allocate(8 + 8 * chunks);
            b.putInt(0).putInt(chunks);
            for (int i = 0; i < runs.size(); i++) if (runs.get(i).track == t.id) b.putLong(at == null ? 0L : at[i]);
            return box("co64", b.array());
        }
    }

    private static Movie parse(FileChannel ch) throws IOException {
        Movie m = new Movie();
        boolean sawMoov = false;
        for (Box b : boxes(ch, 0, ch.size())) {
            if (b.is("moov")) {
                readMoov(m, read(ch, b.body, b.end));
                sawMoov = true;
            } else if (b.is("moof")) {
                if (!sawMoov) throw new IOException("a fragment before the movie box");
                readMoof(m, read(ch, b.start, b.end), b.start, (int) (b.body - b.start));
            }
        }
        if (!sawMoov || m.mvhd == null) throw new IOException("no movie box");
        if (m.tracks.isEmpty()) throw new IOException("no tracks");
        for (Track t : m.tracks.values()) {
            if (t.tkhd == null || t.mdhd == null || t.hdlr == null || t.stsd == null) {
                throw new IOException("track " + t.id + " is missing its headers");
            }
            if (t.count() == 0) throw new IOException("track " + t.id + " has no samples");
        }
        return m;
    }

    private static void readMoov(Movie m, ByteBuffer moov) throws IOException {
        for (Box b : boxes(moov, 0, moov.limit())) {
            if (b.is("mvhd")) {
                m.mvhd = bytes(moov, b.body, b.end);
                m.movieScale = timescale(m.mvhd);
            } else if (b.is("trak")) {
                Track t = readTrak(moov, b);
                m.tracks.put(t.id, t);
            } else if (b.is("mvex")) {
                for (Box c : boxes(moov, b.body, b.end)) {
                    if (!c.is("trex")) continue;
                    int id = moov.getInt((int) c.body + 4);
                    Track t = m.tracks.get(id);
                    if (t == null) continue;
                    t.trexDuration = moov.getInt((int) c.body + 12);
                    t.trexSize = moov.getInt((int) c.body + 16);
                    t.trexFlags = moov.getInt((int) c.body + 20);
                }
            }
        }
    }

    private static Track readTrak(ByteBuffer moov, Box trak) throws IOException {
        Track t = null;
        byte[] tkhd = null;
        byte[] edts = null;
        for (Box b : boxes(moov, trak.body, trak.end)) {
            if (b.is("tkhd")) {
                tkhd = bytes(moov, b.body, b.end);
                int v = tkhd[0];
                t = new Track(ByteBuffer.wrap(tkhd).getInt(v == 1 ? 20 : 12));
            } else if (b.is("edts")) {
                edts = bytes(moov, b.start, b.end);
            }
        }
        if (t == null) throw new IOException("a track without its header");
        t.tkhd = tkhd;
        t.edts = edts;
        for (Box b : boxes(moov, trak.body, trak.end)) {
            if (!b.is("mdia")) continue;
            for (Box c : boxes(moov, b.body, b.end)) {
                if (c.is("mdhd")) {
                    t.mdhd = bytes(moov, c.body, c.end);
                    t.timescale = timescale(t.mdhd);
                } else if (c.is("hdlr")) {
                    t.hdlr = bytes(moov, c.start, c.end);
                } else if (c.is("minf")) {
                    ByteArrayOutputStream head = new ByteArrayOutputStream();
                    for (Box d : boxes(moov, c.body, c.end)) {
                        if (d.is("stbl")) {
                            for (Box e : boxes(moov, d.body, d.end)) if (e.is("stsd")) t.stsd = bytes(moov, e.start, e.end);
                        } else {
                            head.write(bytes(moov, d.start, d.end));
                        }
                    }
                    t.minfHead = head.toByteArray();
                }
            }
        }
        return t;
    }

    private static void readMoof(Movie m, ByteBuffer moof, long moofAt, int header) throws IOException {
        long nextBase = moofAt;
        for (Box traf : boxes(moof, header, moof.limit())) {
            if (!traf.is("traf")) continue;
            Track t = null;
            long base = moofAt;
            int duration = 0;
            int size = 0;
            int flags = 0;
            boolean explicitBase = false;
            for (Box b : boxes(moof, traf.body, traf.end)) {
                if (!b.is("tfhd")) continue;
                int p = (int) b.body;
                int tf = moof.getInt(p) & 0xFFFFFF;
                int id = moof.getInt(p + 4);
                t = m.tracks.get(id);
                if (t == null) throw new IOException("a fragment of unknown track " + id);
                duration = t.trexDuration;
                size = t.trexSize;
                flags = t.trexFlags;
                p += 8;
                if ((tf & 0x1) != 0) {
                    base = moof.getLong(p);
                    explicitBase = true;
                    p += 8;
                }
                if ((tf & 0x2) != 0) p += 4;
                if ((tf & 0x8) != 0) {
                    duration = moof.getInt(p);
                    p += 4;
                }
                if ((tf & 0x10) != 0) {
                    size = moof.getInt(p);
                    p += 4;
                }
                if ((tf & 0x20) != 0) flags = moof.getInt(p);
                // no explicit base: first fragment starts at moof, later ones where the last ended

                if (!explicitBase) base = (tf & 0x20000) != 0 ? moofAt : nextBase;
            }
            if (t == null) throw new IOException("a track fragment without its header");
            long cursor = base;
            for (Box b : boxes(moof, traf.body, traf.end)) {
                if (b.is("tfdt")) {
                    int v = moof.get((int) b.body) & 0xFF;
                    long start = v == 1 ? moof.getLong((int) b.body + 4) : moof.getInt((int) b.body + 4) & 0xFFFFFFFFL;
                    if (start != t.clock) throw new IOException("track " + t.id + " fragment starts at " + start + ", not " + t.clock);
                } else if (b.is("trun")) {
                    cursor = readTrun(m, t, moof, b, base, cursor, duration, size, flags);
                }
            }
            nextBase = cursor;
        }
    }

    private static long readTrun(Movie m, Track t, ByteBuffer moof, Box trun, long base, long cursor, int dDuration,
                                 int dSize, int dFlags) throws IOException {
        int p = (int) trun.body;
        int vf = moof.getInt(p);
        int version = vf >>> 24;
        int tf = vf & 0xFFFFFF;
        int n = moof.getInt(p + 4);
        p += 8;
        long at = cursor;
        if ((tf & 0x1) != 0) {
            at = base + moof.getInt(p);
            p += 4;
        }
        Integer first = null;
        if ((tf & 0x4) != 0) {
            first = moof.getInt(p);
            p += 4;
        }
        long length = 0;
        for (int i = 0; i < n; i++) {
            int d = dDuration;
            int s = dSize;
            int f = i == 0 && first != null ? first : dFlags;
            int c = 0;
            if ((tf & 0x100) != 0) {
                d = moof.getInt(p);
                p += 4;
            }
            if ((tf & 0x200) != 0) {
                s = moof.getInt(p);
                p += 4;
            }
            if ((tf & 0x400) != 0) {
                f = moof.getInt(p);
                p += 4;
            }
            if ((tf & 0x800) != 0) {
                c = moof.getInt(p);
                p += 4;
                if (version == 1 && c < 0) t.signedCompositions = true;
            }
            if (s < 0) throw new IOException("a sample larger than an MP4 can say");
            int index = t.count();
            t.durations.add(d);
            t.sizes.add(s);
            t.compositions.add(c);

            if ((f & 0x10000) != 0) t.nonSync.set(index);
            t.length += d & 0xFFFFFFFFL;
            t.clock += d & 0xFFFFFFFFL;
            length += s;
        }
        if (n > 0) m.runs.add(new Run(t.id, at, length, n));
        return at + length;
    }

    private static List<Box> boxes(FileChannel ch, long from, long to) throws IOException {
        List<Box> out = new ArrayList<>();
        ByteBuffer h = ByteBuffer.allocate(16);
        long at = from;
        while (at + 8 <= to) {
            h.clear().limit(8);
            readFully(ch, h, at);
            h.flip();
            long size = h.getInt() & 0xFFFFFFFFL;
            String type = new String(new byte[]{h.get(), h.get(), h.get(), h.get()}, StandardCharsets.ISO_8859_1);
            long body = at + 8;
            if (size == 1) {
                h.clear().limit(8);
                readFully(ch, h, at + 8);
                h.flip();
                size = h.getLong();
                body = at + 16;
            } else if (size == 0) {
                size = to - at;
            }
            if (size < body - at || at + size > to) throw new IOException("a broken " + type + " box at " + at);
            out.add(new Box(type, at, body, at + size));
            at += size;
        }
        return out;
    }

    private static List<Box> boxes(ByteBuffer buf, long from, long to) throws IOException {
        List<Box> out = new ArrayList<>();
        int at = (int) from;
        while (at + 8 <= to) {
            long size = buf.getInt(at) & 0xFFFFFFFFL;
            String type = new String(new byte[]{buf.get(at + 4), buf.get(at + 5), buf.get(at + 6), buf.get(at + 7)},
                    StandardCharsets.ISO_8859_1);
            int body = at + 8;
            if (size == 1) {
                size = buf.getLong(at + 8);
                body = at + 16;
            } else if (size == 0) {
                size = to - at;
            }
            if (size < body - at || at + size > to) throw new IOException("a broken " + type + " box at " + at);
            out.add(new Box(type, at, body, at + size));
            at += (int) size;
        }
        return out;
    }

    private static ByteBuffer read(FileChannel ch, long from, long to) throws IOException {
        if (to - from > Integer.MAX_VALUE - 64) throw new IOException("a header box too large to read");
        ByteBuffer b = ByteBuffer.allocate((int) (to - from));
        readFully(ch, b, from);
        b.flip();
        return b;
    }

    private static void readFully(FileChannel ch, ByteBuffer b, long at) throws IOException {
        while (b.hasRemaining()) {
            int n = ch.read(b, at);
            if (n < 0) throw new IOException("the film ends early at " + at);
            at += n;
        }
    }

    private static void writeFully(FileChannel ch, ByteBuffer b) throws IOException {
        while (b.hasRemaining()) ch.write(b);
    }

    private static byte[] bytes(ByteBuffer buf, long from, long to) {
        byte[] out = new byte[(int) (to - from)];
        for (int i = 0; i < out.length; i++) out[i] = buf.get((int) from + i);
        return out;
    }

    private static long timescale(byte[] body) {
        return ByteBuffer.wrap(body).getInt(body[0] == 1 ? 20 : 12) & 0xFFFFFFFFL;
    }

    private static byte[] withDuration(byte[] body, boolean tkhd, long duration) {
        byte[] out = body.clone();
        ByteBuffer b = ByteBuffer.wrap(out);
        if (out[0] == 1) {
            b.putLong(tkhd ? 28 : 24, duration);
        } else {
            b.putInt(tkhd ? 20 : 16, (int) Math.min(duration, 0xFFFFFFFFL));
        }
        return out;
    }

    private static void runLengths(Ints values, Ints counts, Ints of) {
        for (int i = 0; i < values.size; i++) {
            int v = values.get(i);
            if (of.size > 0 && of.get(of.size - 1) == v) {
                counts.set(counts.size - 1, counts.get(counts.size - 1) + 1);
            } else {
                counts.add(1);
                of.add(v);
            }
        }
    }

    private static byte[] table(String type, int version, Ints counts, Ints values) {
        ByteBuffer b = ByteBuffer.allocate(8 + 8 * counts.size);
        b.putInt(version << 24).putInt(counts.size);
        for (int i = 0; i < counts.size; i++) b.putInt(counts.get(i)).putInt(values.get(i));
        return box(type, b.array());
    }

    private static byte[] box(String type, byte[] body) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(body.length + 8);
        try {
            DataOutputStream d = new DataOutputStream(o);
            d.writeInt(body.length + 8);
            d.write(ascii(type));
            d.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return o.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    private static byte[] u32(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static final class Ints {
        int[] a = new int[256];
        int size;

        void add(int v) {
            if (size == a.length) a = Arrays.copyOf(a, size * 2);
            a[size++] = v;
        }

        int get(int i) {
            return a[i];
        }

        void set(int i, int v) {
            a[i] = v;
        }
    }
}
