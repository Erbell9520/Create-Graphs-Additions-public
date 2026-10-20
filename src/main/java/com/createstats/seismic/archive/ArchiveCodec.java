package com.createstats.seismic.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

// delta varints, most cells are one apart. ~35kB for a full void survey
public final class ArchiveCodec {
    static final int MAGIC = 0x53415243;
    static final int VERSION = 1;

    private ArchiveCodec() {
    }

    public static byte[] encode(SeismicArchive archive) {
        final Out out = new Out();
        out.int32(MAGIC);
        out.byte8(VERSION);
        out.varint(archive.nextId());
        final List<ArchiveBlow> blows = archive.blows();
        final Map<String, Integer> pool = new LinkedHashMap<>();
        for (final ArchiveBlow b : blows) {
            pool.putIfAbsent(b.target, pool.size());
        }
        out.varint(pool.size());
        for (final String t : pool.keySet()) {
            out.utf(t);
        }
        out.varint(blows.size());
        for (final ArchiveBlow b : blows) {
            out.varint(b.id);
            out.zigzag(b.x);
            out.zigzag(b.y);
            out.zigzag(b.z);
            out.varint(pool.get(b.target));
            out.varint(b.reach);
            out.varlong(b.at);
            out.zigzag(b.floor);
            out.int64(b.holeLo);
            out.int64(b.holeHi);
            out.float32((float) b.front);
            out.float32((float) b.recorded);
            out.byte8((b.done ? 1 : 0) | (b.full ? 2 : 0));
            out.varint(b.veinsFound);
            out.varint(b.blocksFound);
            out.float32(b.nearestFound);
            final int[] cells = b.cells;
            out.varint(cells.length);
            int prev = 0;
            for (final int c : cells) {
                out.varint(c - prev);
                prev = c;
            }
            out.zigzag(b.surfaceX0);
            out.zigzag(b.surfaceZ0);
            out.varint(b.surfaceN);

            int last = 0;
            for (final short h : b.surface) {
                out.zigzag(h - last);
                last = h;
            }
        }
        final CRC32 crc = new CRC32();
        crc.update(out.buf(), 0, out.size());
        out.int32((int) crc.getValue());
        return out.toByteArray();
    }

    public static SeismicArchive decode(byte[] data) throws IOException {
        if (data == null || data.length < 9) {
            throw new IOException("archive too short");
        }
        final CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - 4);
        final int stored = ((data[data.length - 4] & 0xFF) << 24) | ((data[data.length - 3] & 0xFF) << 16)
                | ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
        if ((int) crc.getValue() != stored) {
            throw new IOException("archive checksum mismatch");
        }
        final In in = new In(data, data.length - 4);
        if (in.int32() != MAGIC) {
            throw new IOException("not an archive");
        }
        final int version = in.byte8();
        if (version != VERSION) {
            throw new IOException("archive version " + version + " is not " + VERSION);
        }
        final int nextId = in.varint();
        final int poolSize = in.count(4_096);
        final List<String> pool = new ArrayList<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.add(in.utf());
        }
        final int n = in.count(SeismicArchive.MAX_BLOWS * 2);
        final List<ArchiveBlow> blows = new ArrayList<>(n);
        long total = 0L;
        for (int i = 0; i < n; i++) {
            final int id = in.varint();
            final int x = in.zigzag();
            final int y = in.zigzag();
            final int z = in.zigzag();
            final int t = in.varint();
            if (t < 0 || t >= pool.size()) {
                throw new IOException("target index out of range");
            }
            final int reach = in.varint();
            final long at = in.varlong();
            final int floor = in.zigzag();
            final long holeLo = in.int64();
            final long holeHi = in.int64();
            final float front = in.float32();
            final float recorded = in.float32();
            final int flags = in.byte8();
            final int veins = in.varint();
            final int blocks = in.varint();
            final float nearest = in.float32();
            final int count = in.count(65_536);
            total += count;
            if (total > SeismicArchive.MAX_CELLS * 2L) {
                throw new IOException("archive holds more cells than any archive can");
            }
            final int[] cells = new int[count];
            int prev = 0;
            for (int k = 0; k < count; k++) {
                prev += in.varint();
                cells[k] = prev;
            }
            final int sx0 = in.zigzag();
            final int sz0 = in.zigzag();
            final int sn = in.count(64);
            final short[] surface = new short[sn * sn];
            int last = 0;
            for (int k = 0; k < surface.length; k++) {
                last += in.zigzag();
                surface[k] = (short) last;
            }
            blows.add(ArchiveBlow.restored(id, x, y, z, pool.get(t), reach, at, floor, holeLo, holeHi, front, recorded,
                    (flags & 1) != 0, (flags & 2) != 0, veins, blocks, nearest, cells, sx0, sz0, sn, surface));
        }
        if (in.remaining() != 0) {
            throw new IOException("archive has " + in.remaining() + " bytes left over");
        }
        return SeismicArchive.restore(blows, nextId);
    }

    static final class Out {
        private final ByteArrayOutputStream b = new ByteArrayOutputStream(4_096);

        byte[] buf() {
            return b.toByteArray();
        }

        int size() {
            return b.size();
        }

        byte[] toByteArray() {
            return b.toByteArray();
        }

        void byte8(int v) {
            b.write(v & 0xFF);
        }

        void int32(int v) {
            b.write(v >>> 24);
            b.write(v >>> 16 & 0xFF);
            b.write(v >>> 8 & 0xFF);
            b.write(v & 0xFF);
        }

        void int64(long v) {
            int32((int) (v >>> 32));
            int32((int) v);
        }

        void float32(float v) {
            int32(Float.floatToIntBits(v));
        }

        void varint(int v) {
            while ((v & ~0x7F) != 0) {
                b.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            b.write(v);
        }

        void varlong(long v) {
            final long z = (v << 1) ^ (v >> 63);
            long u = z;
            while ((u & ~0x7FL) != 0L) {
                b.write((int) ((u & 0x7F) | 0x80));
                u >>>= 7;
            }
            b.write((int) u);
        }

        void zigzag(int v) {
            varint((v << 1) ^ (v >> 31));
        }

        void utf(String s) {
            final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            varint(bytes.length);
            b.write(bytes, 0, bytes.length);
        }
    }

    static final class In {
        private final byte[] d;
        private final int end;
        private int p;

        In(byte[] d, int end) {
            this.d = d;
            this.end = end;
        }

        int remaining() {
            return end - p;
        }

        int byte8() throws IOException {
            if (p >= end) {
                throw new IOException("truncated");
            }
            return d[p++] & 0xFF;
        }

        int int32() throws IOException {
            return byte8() << 24 | byte8() << 16 | byte8() << 8 | byte8();
        }

        long int64() throws IOException {
            return ((long) int32() << 32) | (int32() & 0xFFFFFFFFL);
        }

        float float32() throws IOException {
            return Float.intBitsToFloat(int32());
        }

        int varint() throws IOException {
            int v = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                final int b = byte8();
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
            }
            throw new IOException("varint too long");
        }

        long varlong() throws IOException {
            long u = 0L;
            for (int shift = 0; shift < 70; shift += 7) {
                final int b = byte8();
                u |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return (u >>> 1) ^ -(u & 1L);
                }
            }
            throw new IOException("varlong too long");
        }

        int zigzag() throws IOException {
            final int u = varint();
            return (u >>> 1) ^ -(u & 1);
        }

        int count(int max) throws IOException {
            final int n = varint();
            if (n < 0 || n > max) {
                throw new IOException("count " + n + " out of range");
            }
            return n;
        }

        String utf() throws IOException {
            final int n = count(1_024);
            if (p + n > end) {
                throw new IOException("truncated");
            }
            final String s = new String(d, p, n, StandardCharsets.UTF_8);
            p += n;
            return s;
        }
    }
}
