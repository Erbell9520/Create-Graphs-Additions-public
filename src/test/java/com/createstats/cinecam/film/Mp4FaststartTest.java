package com.createstats.cinecam.film;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Mp4FaststartTest {
    @TempDir
    Path dir;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aRealFilmComesOutPlainWithEverySampleInPlace() throws IOException {
        assumeTrue(MediaFoundationWriter.available(), "no Media Foundation here");
        Path film = dir.resolve("take.mp4");
        int w = 256;
        int h = 144;
        int rate = 48000;
        byte[] frame = new byte[w * h * 3 / 2];
        MediaFoundationWriter mw = new MediaFoundationWriter(film, w, h, 30, 2_000_000, rate);
        boolean fragmented;
        try {
            long at = 0;
            for (int i = 0; i < 75; i++) {
                Arrays.fill(frame, 0, w * h, (byte) (16 + (i * 7) % 200));
                Arrays.fill(frame, w * h, frame.length, (byte) 128);
                mw.write(frame);
                int n = rate / 30;
                short[] pcm = new short[n * 2];
                for (int k = 0; k < n; k++) {
                    short v = (short) (Math.sin(2 * Math.PI * 440 * (at + k) / rate) * 8000);
                    pcm[2 * k] = v;
                    pcm[2 * k + 1] = v;
                }
                mw.writeAudio(pcm, n, at);
                at += n;
            }
            fragmented = mw.fragmented();
        } finally {
            mw.close();
        }
        assumeTrue(fragmented, "this system wrote a plain MP4 already");
        assertTrue(Mp4Faststart.fragmented(film));
        Map<Integer, List<Sample>> before = Reader.fragmentedSamples(film);
        Path plain = dir.resolve("plain.mp4");
        Mp4Faststart.rewrite(film, plain, () -> false);
        assertFalse(Mp4Faststart.fragmented(plain));
        assertEquals(List.of("ftyp", "moov", "mdat"), Reader.topLevel(plain), "the index in front of the pictures");
        Map<Integer, List<Sample>> after = Reader.plainSamples(plain);
        assertEquals(2, before.size(), "a picture and a sound track");
        assertEquals(before.keySet(), after.keySet());
        for (int id : before.keySet()) assertSame(before.get(id), after.get(id), "track " + id);
        assertTrue(after.values().stream().anyMatch(l -> l.size() == 75), "every frame of the picture");
    }

    @Test
    void everyFragmentRuleIsRead() throws IOException {
        Synthetic s = new Synthetic();
        Path film = dir.resolve("synthetic.mp4");
        Files.write(film, s.file());
        assertTrue(Mp4Faststart.fragmented(film));
        Map<Integer, List<Sample>> before = Reader.fragmentedSamples(film);
        assertEquals(s.expected, before, "the test's own reader reads the hand-built file as built");
        Path plain = dir.resolve("plain.mp4");
        Mp4Faststart.rewrite(film, plain, () -> false);
        assertEquals(List.of("ftyp", "moov", "mdat"), Reader.topLevel(plain));
        Map<Integer, List<Sample>> after = Reader.plainSamples(plain);
        for (int id : s.expected.keySet()) assertSame(s.expected.get(id), after.get(id), "track " + id);
        assertEquals(s.videoLength(), Reader.mediaDuration(plain, 1), "the picture's length in its own time scale");
    }

    @Test
    void theFilmIsReplacedInPlaceAndNoPartIsLeft() throws IOException {
        Path film = dir.resolve("take.mp4");
        Files.write(film, new Synthetic().file());
        Mp4Faststart.rewriteInPlace(film, () -> false);
        assertFalse(Mp4Faststart.fragmented(film));
        assertEquals(List.of("ftyp", "moov", "mdat"), Reader.topLevel(film));
        assertFalse(Files.exists(dir.resolve("take.mp4" + Mp4Faststart.PART)));
    }

    @Test
    void aStoppedRewriteLeavesTheFilmAsItWas() throws IOException {
        Path film = dir.resolve("take.mp4");
        byte[] original = new Synthetic().file();
        Files.write(film, original);
        assertThrows(IOException.class, () -> Mp4Faststart.rewriteInPlace(film, () -> true));
        assertArrayEquals(original, Files.readAllBytes(film));
        assertFalse(Files.exists(dir.resolve("take.mp4" + Mp4Faststart.PART)));
    }

    @Test
    void aRewriteStoppedJustBeforeTheSwapLeavesTheFilmAsItWas() throws IOException {
        byte[] original = new Synthetic().file();
        Path probe = dir.resolve("probe.mp4");
        Files.write(probe, original);
        int[] asked = {0};
        Mp4Faststart.rewrite(probe, dir.resolve("probe-plain.mp4"), () -> {
            asked[0]++;
            return false;
        });
        int during = asked[0];
        Path film = dir.resolve("take.mp4");
        Files.write(film, original);
        int[] seen = {0};
        assertThrows(IOException.class, () -> Mp4Faststart.rewriteInPlace(film, () -> seen[0]++ >= during));
        assertArrayEquals(original, Files.readAllBytes(film));
        assertFalse(Files.exists(dir.resolve("take.mp4" + Mp4Faststart.PART)));
    }

    @Test
    void aFileItCannotReadIsRefusedUntouched() throws IOException {
        Path film = dir.resolve("broken.mp4");
        byte[] junk = new byte[4096];
        Arrays.fill(junk, (byte) 0x7F);
        Files.write(film, junk);
        assertThrows(IOException.class, () -> Mp4Faststart.rewriteInPlace(film, () -> false));
        assertArrayEquals(junk, Files.readAllBytes(film));
        byte[] cut = Arrays.copyOf(new Synthetic().file(), 700);
        Files.write(film, cut);
        assertThrows(IOException.class, () -> Mp4Faststart.rewriteInPlace(film, () -> false), "a film cut off mid-box");
        assertArrayEquals(cut, Files.readAllBytes(film));
        assertFalse(Files.exists(dir.resolve("broken.mp4" + Mp4Faststart.PART)));
    }

    @Test
    void leftoversGoButARunningRewriteAndOtherFilesStay() throws IOException {
        Path dead = dir.resolve("a.mp4" + Mp4Faststart.PART);
        Path live = dir.resolve("b.mp4" + Mp4Faststart.PART);
        Path take = dir.resolve("c.mp4");
        for (Path p : new Path[]{dead, live, take}) Files.write(p, new byte[]{1});
        FileTime old = FileTime.fromMillis(System.currentTimeMillis() - Mp4Faststart.LEFTOVER_MS - 60_000);
        Files.setLastModifiedTime(dead, old);
        Files.setLastModifiedTime(take, old);
        assertEquals(1, Mp4Faststart.clearLeftovers(dir));
        assertFalse(Files.exists(dead));
        assertTrue(Files.exists(live), "a part file still being written is not touched");
        assertTrue(Files.exists(take), "nor is a film");
    }

    private static void assertSame(List<Sample> want, List<Sample> got, String what) {
        assertEquals(want.size(), got.size(), what + ": sample count");
        for (int i = 0; i < want.size(); i++) assertEquals(want.get(i), got.get(i), what + ": sample " + i);
    }

    record Sample(byte[] data, long duration, boolean sync) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Sample s && Arrays.equals(data, s.data) && duration == s.duration && sync == s.sync;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data) * 31 + Long.hashCode(duration) * 2 + (sync ? 1 : 0);
        }

        @Override
        public String toString() {
            return "Sample[" + data.length + " bytes, " + duration + (sync ? ", sync]" : "]");
        }
    }

    private static final class Reader {
        record Box(String type, int start, int body, int end) {
        }

        static List<Box> boxes(ByteBuffer b, int from, int to) {
            List<Box> out = new ArrayList<>();
            int at = from;
            while (at + 8 <= to) {
                long size = b.getInt(at) & 0xFFFFFFFFL;
                String type = new String(new byte[]{b.get(at + 4), b.get(at + 5), b.get(at + 6), b.get(at + 7)},
                        StandardCharsets.ISO_8859_1);
                int body = at + 8;
                if (size == 1) {
                    size = b.getLong(at + 8);
                    body = at + 16;
                }
                out.add(new Box(type, at, body, (int) (at + size)));
                at += (int) size;
            }
            return out;
        }

        static Box find(ByteBuffer b, Box in, String... path) {
            Box cur = in;
            for (String p : path) {
                Box next = null;
                for (Box c : boxes(b, cur.body, cur.end)) {
                    if (c.type.equals(p)) {
                        next = c;
                        break;
                    }
                }
                if (next == null) return null;
                cur = next;
            }
            return cur;
        }

        static ByteBuffer load(Path p) throws IOException {
            return ByteBuffer.wrap(Files.readAllBytes(p));
        }

        static List<String> topLevel(Path p) throws IOException {
            ByteBuffer b = load(p);
            List<String> out = new ArrayList<>();
            for (Box x : boxes(b, 0, b.limit())) out.add(x.type);
            return out;
        }

        static Box root(ByteBuffer b) {
            return new Box("", 0, 0, b.limit());
        }

        static int trackId(ByteBuffer b, Box trak) {
            Box tkhd = find(b, trak, "tkhd");
            return b.getInt(tkhd.body + (b.get(tkhd.body) == 1 ? 20 : 12));
        }

        static long mediaDuration(Path p, int track) throws IOException {
            ByteBuffer b = load(p);
            Box moov = find(b, root(b), "moov");
            for (Box t : boxes(b, moov.body, moov.end)) {
                if (!t.type.equals("trak") || trackId(b, t) != track) continue;
                Box mdhd = find(b, t, "mdia", "mdhd");
                return b.get(mdhd.body) == 1 ? b.getLong(mdhd.body + 24) : b.getInt(mdhd.body + 16) & 0xFFFFFFFFL;
            }
            return -1;
        }

        static byte[] slice(ByteBuffer b, long at, int n) {
            byte[] out = new byte[n];
            for (int i = 0; i < n; i++) out[i] = b.get((int) at + i);
            return out;
        }

        static Map<Integer, List<Sample>> plainSamples(Path p) throws IOException {
            ByteBuffer b = load(p);
            Box moov = find(b, root(b), "moov");
            assertTrue(find(b, moov, "mvex") == null, "no fragments declared");
            Map<Integer, List<Sample>> out = new LinkedHashMap<>();
            for (Box t : boxes(b, moov.body, moov.end)) {
                if (!t.type.equals("trak")) continue;
                Box stbl = find(b, t, "mdia", "minf", "stbl");
                Box stts = find(b, stbl, "stts");
                List<Long> durations = new ArrayList<>();
                for (int i = 0, n = b.getInt(stts.body + 4); i < n; i++) {
                    int count = b.getInt(stts.body + 8 + 8 * i);
                    long d = b.getInt(stts.body + 12 + 8 * i) & 0xFFFFFFFFL;
                    for (int k = 0; k < count; k++) durations.add(d);
                }
                Box stsz = find(b, stbl, "stsz");
                int count = b.getInt(stsz.body + 8);
                int[] sizes = new int[count];
                for (int i = 0; i < count; i++) sizes[i] = b.getInt(stsz.body + 12 + 4 * i);
                Box co64 = find(b, stbl, "co64");
                Box stco = find(b, stbl, "stco");
                Box offsBox = co64 != null ? co64 : stco;
                int chunks = b.getInt(offsBox.body + 4);
                long[] offs = new long[chunks];
                for (int i = 0; i < chunks; i++) {
                    offs[i] = co64 != null ? b.getLong(co64.body + 8 + 8 * i) : b.getInt(stco.body + 8 + 4 * i) & 0xFFFFFFFFL;
                }
                Box stsc = find(b, stbl, "stsc");
                int entries = b.getInt(stsc.body + 4);
                int[] per = new int[chunks];
                for (int e = 0; e < entries; e++) {
                    int first = b.getInt(stsc.body + 8 + 12 * e);
                    int n = b.getInt(stsc.body + 12 + 12 * e);
                    int last = e + 1 < entries ? b.getInt(stsc.body + 8 + 12 * (e + 1)) - 1 : chunks;
                    for (int c = first; c <= last; c++) per[c - 1] = n;
                }
                Box stss = find(b, stbl, "stss");
                Set<Integer> sync = null;
                if (stss != null) {
                    sync = new HashSet<>();
                    for (int i = 0, n = b.getInt(stss.body + 4); i < n; i++) sync.add(b.getInt(stss.body + 8 + 4 * i));
                }
                List<Sample> list = new ArrayList<>();
                int s = 0;
                for (int c = 0; c < chunks; c++) {
                    long at = offs[c];
                    for (int k = 0; k < per[c]; k++, s++) {
                        list.add(new Sample(slice(b, at, sizes[s]), durations.get(s), sync == null || sync.contains(s + 1)));
                        at += sizes[s];
                    }
                }
                assertEquals(count, s, "every sample in a chunk");
                assertEquals(count, durations.size(), "a duration for every sample");
                out.put(trackId(b, t), list);
            }
            return out;
        }

        static Map<Integer, List<Sample>> fragmentedSamples(Path p) throws IOException {
            ByteBuffer b = load(p);
            Map<Integer, int[]> trex = new LinkedHashMap<>();
            Box mvex = find(b, find(b, root(b), "moov"), "mvex");
            for (Box x : boxes(b, mvex.body, mvex.end)) {
                if (x.type.equals("trex")) {
                    trex.put(b.getInt(x.body + 4), new int[]{b.getInt(x.body + 12), b.getInt(x.body + 16), b.getInt(x.body + 20)});
                }
            }
            Map<Integer, List<Sample>> out = new LinkedHashMap<>();
            for (Box moof : boxes(b, 0, b.limit())) {
                if (!moof.type.equals("moof")) continue;
                long next = moof.start;
                for (Box traf : boxes(b, moof.body, moof.end)) {
                    if (!traf.type.equals("traf")) continue;
                    Box tfhd = find(b, traf, "tfhd");
                    int tf = b.getInt(tfhd.body) & 0xFFFFFF;
                    int id = b.getInt(tfhd.body + 4);
                    int[] d = trex.get(id).clone();
                    int q = tfhd.body + 8;
                    long base = (tf & 0x20000) != 0 ? moof.start : next;
                    if ((tf & 0x1) != 0) {
                        base = b.getLong(q);
                        q += 8;
                    }
                    if ((tf & 0x2) != 0) q += 4;
                    if ((tf & 0x8) != 0) {
                        d[0] = b.getInt(q);
                        q += 4;
                    }
                    if ((tf & 0x10) != 0) {
                        d[1] = b.getInt(q);
                        q += 4;
                    }
                    if ((tf & 0x20) != 0) d[2] = b.getInt(q);
                    long cursor = base;
                    for (Box trun : boxes(b, traf.body, traf.end)) {
                        if (!trun.type.equals("trun")) continue;
                        int rf = b.getInt(trun.body) & 0xFFFFFF;
                        int n = b.getInt(trun.body + 4);
                        int r = trun.body + 8;
                        long at = cursor;
                        if ((rf & 0x1) != 0) {
                            at = base + b.getInt(r);
                            r += 4;
                        }
                        int firstFlags = -1;
                        if ((rf & 0x4) != 0) {
                            firstFlags = b.getInt(r);
                            r += 4;
                        }
                        for (int i = 0; i < n; i++) {
                            long dur = d[0] & 0xFFFFFFFFL;
                            int size = d[1];
                            int flags = i == 0 && firstFlags != -1 ? firstFlags : d[2];
                            if ((rf & 0x100) != 0) {
                                dur = b.getInt(r) & 0xFFFFFFFFL;
                                r += 4;
                            }
                            if ((rf & 0x200) != 0) {
                                size = b.getInt(r);
                                r += 4;
                            }
                            if ((rf & 0x400) != 0) {
                                flags = b.getInt(r);
                                r += 4;
                            }
                            if ((rf & 0x800) != 0) r += 4;
                            out.computeIfAbsent(id, k -> new ArrayList<>())
                                    .add(new Sample(slice(b, at, size), dur, (flags & 0x10000) == 0));
                            at += size;
                        }
                        cursor = at;
                    }
                    next = cursor;
                }
            }
            return out;
        }
    }

    private static final class Synthetic {
        final Map<Integer, List<Sample>> expected = new LinkedHashMap<>();
        private int serial;

        long videoLength() {
            return expected.get(1).stream().mapToLong(Sample::duration).sum();
        }

        byte[] payload(int track, int size) {
            byte[] p = new byte[size];
            serial++;
            for (int i = 0; i < size; i++) p[i] = (byte) (track * 50 + serial * 7 + i);
            return p;
        }

        byte[] file() throws IOException {
            expected.clear();
            serial = 0;
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            f.write(box("ftyp", cat(ascii("iso6"), u32(0), ascii("iso6mp41"))));
            f.write(moov());

            fragment(f, 1, new int[]{900, 300, 310}, null, 0, new int[]{40, 40, 40, 40}, 0x20000, true, -1L);

            fragment(f, 2, new int[]{880, 290}, new long[]{1000, 2000}, 0, new int[]{44, 45, 46}, 0, false, -1L);

            fragment(f, 3, new int[]{870, 305, 306, 307}, null, 2, new int[]{47, 48}, 0x1, true, 0L);
            return f.toByteArray();
        }

        private byte[] moov() {
            byte[] mvhd = box("mvhd", cat(u32(0), u32(0), u32(0), u32(1000), u32(0), new byte[80]));
            byte[] video = trak(1, 30000, "vide", box("vmhd", cat(u32(1), new byte[8])), box("avc1", new byte[78]));
            byte[] sound = trak(2, 48000, "soun", box("smhd", cat(u32(0), new byte[4])), box("mp4a", new byte[28]));
            byte[] trex1 = box("trex", cat(u32(0), u32(1), u32(1), u32(1000), u32(0), u32(0x10000)));
            byte[] trex2 = box("trex", cat(u32(0), u32(2), u32(1), u32(1024), u32(0), u32(0)));
            return box("moov", cat(mvhd, video, sound, box("mvex", cat(trex1, trex2))));
        }

        private byte[] trak(int id, int scale, String handler, byte[] mediaHeader, byte[] entry) {
            byte[] tkhd = box("tkhd", cat(u32(3), u32(0), u32(0), u32(id), u32(0), u32(0), new byte[60]));
            byte[] mdhd = box("mdhd", cat(u32(0), u32(0), u32(0), u32(scale), u32(0), new byte[4]));
            byte[] hdlr = box("hdlr", cat(u32(0), u32(0), ascii(handler), new byte[12], new byte[]{0}));
            byte[] dinf = box("dinf", box("dref", cat(u32(0), u32(1), box("url ", u32(1)))));
            byte[] stsd = box("stsd", cat(u32(0), u32(1), entry));
            byte[] empty = cat(box("stts", cat(u32(0), u32(0))), box("stsc", cat(u32(0), u32(0))),
                    box("stsz", cat(u32(0), u32(0), u32(0))), box("stco", cat(u32(0), u32(0))));
            byte[] minf = box("minf", cat(mediaHeader, dinf, box("stbl", cat(stsd, empty))));
            return box("trak", cat(tkhd, box("mdia", cat(mdhd, hdlr, minf))));
        }

        private void fragment(ByteArrayOutputStream f, int seq, int[] vSizes, long[] vDurations, int split,
                              int[] aSizes, int aTfhd, boolean aOffset, long aBaseAt) throws IOException {
            List<byte[]> vData = new ArrayList<>();
            List<byte[]> aData = new ArrayList<>();
            for (int i = 0; i < vSizes.length; i++) {
                byte[] p = payload(1, vSizes[i]);
                vData.add(p);
                long d = vDurations != null ? vDurations[i] : 1000;
                expected.computeIfAbsent(1, k -> new ArrayList<>()).add(new Sample(p, d, i == 0));
            }
            int vTfhd = 0x20000;
            for (int aSize : aSizes) {
                byte[] p = payload(2, aSize);
                aData.add(p);
                expected.computeIfAbsent(2, k -> new ArrayList<>()).add(new Sample(p, 1024, true));
            }

            byte[] sized = moof(seq, vSizes, vDurations, vTfhd, split, aSizes, aTfhd, aOffset, 0, 0, 0);
            int moofAt = f.size();
            int dataAt = moofAt + sized.length + 8;
            int vLen = Arrays.stream(vSizes).sum();
            long aBase = aBaseAt == -1L ? moofAt : dataAt + vLen;
            byte[] moof = moof(seq, vSizes, vDurations, vTfhd, split, aSizes, aTfhd, aOffset, dataAt - moofAt,
                    (int) (dataAt + vLen - moofAt), aBase);
            assertEquals(sized.length, moof.length);
            f.write(moof);
            ByteArrayOutputStream mdat = new ByteArrayOutputStream();
            for (byte[] p : vData) mdat.write(p);
            for (byte[] p : aData) mdat.write(p);
            f.write(box("mdat", mdat.toByteArray()));
        }

        private byte[] moof(int seq, int[] vSizes, long[] vDurations, int vTfhd, int split, int[] aSizes, int aTfhd,
                            boolean aOffset, int vData, int aData, long aBase) {
            int first = split > 0 ? split : vSizes.length;
            int dur = vDurations != null ? 0x100 : 0;
            ByteArrayOutputStream v = new ByteArrayOutputStream();
            v.writeBytes(cat(u32(0x200 | 0x4 | 0x1 | dur), u32(first), u32(vData), u32(0)));
            for (int i = 0; i < first; i++) {
                if (vDurations != null) v.writeBytes(u32((int) vDurations[i]));
                v.writeBytes(u32(vSizes[i]));
            }
            byte[] runs = box("trun", v.toByteArray());
            if (first < vSizes.length) {
                ByteArrayOutputStream v2 = new ByteArrayOutputStream();
                v2.writeBytes(cat(u32(0x200 | dur), u32(vSizes.length - first)));
                for (int i = first; i < vSizes.length; i++) {
                    if (vDurations != null) v2.writeBytes(u32((int) vDurations[i]));
                    v2.writeBytes(u32(vSizes[i]));
                }
                runs = cat(runs, box("trun", v2.toByteArray()));
            }
            byte[] vTraf = box("traf", cat(box("tfhd", cat(u32(vTfhd), u32(1))), runs));

            boolean even = Arrays.stream(aSizes).distinct().count() == 1 && aSizes.length > 1;
            ByteArrayOutputStream tf = new ByteArrayOutputStream();
            int tfFlags = aTfhd | (even ? 0x10 : 0);
            tf.writeBytes(cat(u32(tfFlags), u32(2)));
            if ((aTfhd & 0x1) != 0) tf.writeBytes(ByteBuffer.allocate(8).putLong(aBase).array());
            if (even) tf.writeBytes(u32(aSizes[0]));
            int aRun = (aOffset ? 0x1 : 0) | (even ? 0 : 0x200);
            ByteArrayOutputStream a = new ByteArrayOutputStream();
            a.writeBytes(cat(u32(aRun), u32(aSizes.length)));
            if (aOffset) a.writeBytes(u32((aTfhd & 0x1) != 0 ? 0 : aData));
            if (!even) for (int sz : aSizes) a.writeBytes(u32(sz));
            byte[] aTraf = box("traf", cat(box("tfhd", tf.toByteArray()), box("trun", a.toByteArray())));
            return box("moof", cat(box("mfhd", cat(u32(0), u32(seq))), vTraf, aTraf));
        }
    }

    static byte[] box(String type, byte[] body) {
        return cat(u32(body.length + 8), ascii(type), body);
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    static byte[] u32(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
