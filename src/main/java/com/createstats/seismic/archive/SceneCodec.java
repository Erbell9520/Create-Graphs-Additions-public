package com.createstats.seismic.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class SceneCodec {
    static final int MAGIC = 0x5353434E;
    static final int VERSION = 1;

    public static final int MAX_INFLATED = 2 * 1024 * 1024;
    private static final int MAX_BODIES = 50_000;
    private static final int MAX_CELLS = 200_000;

    private SceneCodec() {
    }

    public static byte[] encode(SurveyScene s) {
        final ArchiveCodec.Out out = new ArchiveCodec.Out();
        out.int32(MAGIC);
        out.byte8(VERSION);
        out.varlong(s.stamp);
        out.varlong(s.builtAt);
        final Map<String, Integer> pool = new LinkedHashMap<>();
        for (final SurveyScene.Bowl b : s.bowls) {
            pool.putIfAbsent(b.target, pool.size());
        }
        for (final SurveyScene.Rig r : s.rigs) {
            pool.putIfAbsent(r.target, pool.size());
        }
        for (final SurveyScene.Layer l : s.layers) {
            pool.putIfAbsent(l.target, pool.size());
        }
        out.varint(pool.size());
        for (final String t : pool.keySet()) {
            out.utf(t);
        }
        out.varint(s.bowls.size());
        for (final SurveyScene.Bowl b : s.bowls) {
            out.varint(b.id);
            out.zigzag(b.x);
            out.zigzag(b.y);
            out.zigzag(b.z);
            out.varint(pool.get(b.target));
            out.varint(b.reach);
            out.float32(b.recorded);
            out.float32(b.front);
            out.varlong(b.at);
            out.zigzag(b.floor);
            out.int64(b.holeLo);
            out.int64(b.holeHi);
            out.byte8((b.done ? 1 : 0) | (b.full ? 2 : 0));
            out.varint(b.veins);
            out.varint(b.blocks);
            out.float32(b.nearest);
        }
        out.varint(s.rigs.size());
        for (final SurveyScene.Rig r : s.rigs) {
            out.zigzag(r.x);
            out.zigzag(r.y);
            out.zigzag(r.z);
            out.byte8(r.linked ? 1 : 0);
            out.varint(pool.get(r.target));
            out.varlong(r.lastAt);
            out.varint(r.blows);
        }
        out.varint(s.layers.size());
        for (final SurveyScene.Layer l : s.layers) {
            out.varint(pool.get(l.target));
            out.varint(l.bodies.size());
            for (final SurveyScene.Body b : l.bodies) {
                out.varint(b.kind);
                out.varint(b.count);
                out.byte8(b.grain);
                out.zigzag(b.ox);
                out.zigzag(b.oy);
                out.zigzag(b.oz);
                out.varint(b.maxX - b.minX);
                out.varint(b.maxY - b.minY);
                out.varint(b.maxZ - b.minZ);
                out.zigzag(b.minX - b.ox);
                out.zigzag(b.minY - b.oy);
                out.zigzag(b.minZ - b.oz);
                out.float32(b.cx);
                out.float32(b.cy);
                out.float32(b.cz);
                out.varlong(b.newest);
                out.varlong(b.oldest);
                out.varint(b.echo);
                out.zigzag(b.nearX);
                out.zigzag(b.nearY);
                out.zigzag(b.nearZ);
                out.float32(b.nearDist);
                out.varint(b.cells.length);
                int prev = 0;
                for (final int c : b.cells) {
                    out.varint(c - prev);
                    prev = c;
                }
            }
        }
        final SurveyScene.Surface sf = s.surface;
        out.zigzag(sf.x0());
        out.zigzag(sf.z0());
        out.varint(sf.nx());
        out.varint(sf.nz());
        int last = 0;
        for (final short h : sf.heights()) {
            out.zigzag(h - last);
            last = h;
        }
        final byte[] raw = out.toByteArray();
        final Deflater d = new Deflater(Deflater.BEST_SPEED);
        try {
            d.setInput(raw);
            d.finish();
            final ByteArrayOutputStream z = new ByteArrayOutputStream(raw.length / 2 + 64);
            final byte[] buf = new byte[8_192];
            while (!d.finished()) {
                final int n = d.deflate(buf);
                z.write(buf, 0, n);
            }
            return z.toByteArray();
        } finally {
            d.end();
        }
    }

    public static SurveyScene decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return SurveyScene.EMPTY;
        }
        final byte[] raw = inflate(data);
        final ArchiveCodec.In in = new ArchiveCodec.In(raw, raw.length);
        if (in.int32() != MAGIC) {
            throw new IOException("not a scene");
        }
        final int version = in.byte8();
        if (version != VERSION) {
            throw new IOException("scene version " + version + " is not " + VERSION);
        }
        final long stamp = in.varlong();
        final long builtAt = in.varlong();
        final int poolSize = in.count(4_096);
        final List<String> pool = new ArrayList<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.add(in.utf());
        }
        final int nb = in.count(SeismicArchive.MAX_BLOWS * 2);
        final List<SurveyScene.Bowl> bowls = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) {
            final int id = in.varint();
            final int x = in.zigzag();
            final int y = in.zigzag();
            final int z = in.zigzag();
            final String target = pooled(pool, in.varint());
            final int reach = in.varint();
            final float recorded = in.float32();
            final float front = in.float32();
            final long at = in.varlong();
            final int floor = in.zigzag();
            final long holeLo = in.int64();
            final long holeHi = in.int64();
            final int flags = in.byte8();
            final int veins = in.varint();
            final int blocks = in.varint();
            final float nearest = in.float32();
            bowls.add(new SurveyScene.Bowl(id, x, y, z, target, reach, recorded, front, at, floor, holeLo, holeHi,
                    (flags & 1) != 0, (flags & 2) != 0, veins, blocks, nearest));
        }
        final int nr = in.count(4_096);
        final List<SurveyScene.Rig> rigs = new ArrayList<>(nr);
        for (int i = 0; i < nr; i++) {
            final int x = in.zigzag();
            final int y = in.zigzag();
            final int z = in.zigzag();
            final boolean linked = in.byte8() != 0;
            final String target = pooled(pool, in.varint());
            final long lastAt = in.varlong();
            final int blows = in.varint();
            rigs.add(new SurveyScene.Rig(x, y, z, linked, target, lastAt, blows));
        }
        final int nl = in.count(4_096);
        final List<SurveyScene.Layer> layers = new ArrayList<>(nl);
        int bodies = 0;
        int cellsSeen = 0;
        for (int i = 0; i < nl; i++) {
            final String target = pooled(pool, in.varint());
            final int n = in.count(MAX_BODIES);
            bodies += n;
            if (bodies > MAX_BODIES) {
                throw new IOException("too many bodies");
            }
            final List<SurveyScene.Body> list = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                final int kind = in.varint();
                final int count = in.varint();
                final int grain = Math.max(1, Math.min(SceneBuilder.MAX_GRAIN, in.byte8()));
                final int ox = in.zigzag();
                final int oy = in.zigzag();
                final int oz = in.zigzag();
                final int ex = in.varint();
                final int ey = in.varint();
                final int ez = in.varint();
                final int minX = ox + in.zigzag();
                final int minY = oy + in.zigzag();
                final int minZ = oz + in.zigzag();
                final float cx = in.float32();
                final float cy = in.float32();
                final float cz = in.float32();
                final long newest = in.varlong();
                final long oldest = in.varlong();
                final int echo = in.varint();
                final int nearX = in.zigzag();
                final int nearY = in.zigzag();
                final int nearZ = in.zigzag();
                final float nearDist = in.float32();
                final int nc = in.count(MAX_CELLS);
                cellsSeen += nc;
                if (cellsSeen > MAX_CELLS) {
                    throw new IOException("too many cells");
                }
                final int[] cells = new int[nc];
                int prev = 0;
                for (int c = 0; c < nc; c++) {
                    prev += in.varint();
                    cells[c] = prev;
                }
                list.add(new SurveyScene.Body(target, kind, count, grain, ox, oy, oz, cells, minX, minY, minZ,
                        minX + ex, minY + ey, minZ + ez, cx, cy, cz, newest, oldest, echo, nearX, nearY, nearZ,
                        nearDist));
            }
            layers.add(new SurveyScene.Layer(target, list));
        }
        final int sx0 = in.zigzag();
        final int sz0 = in.zigzag();
        final int snx = in.count(256);
        final int snz = in.count(256);
        final short[] heights = new short[snx * snz];
        int last = 0;
        for (int i = 0; i < heights.length; i++) {
            last += in.zigzag();
            heights[i] = (short) last;
        }
        if (in.remaining() != 0) {
            throw new IOException("scene has " + in.remaining() + " bytes left over");
        }
        return new SurveyScene(stamp, builtAt, layers, bowls, rigs, new SurveyScene.Surface(sx0, sz0, snx, snz, heights));
    }

    private static String pooled(List<String> pool, int i) throws IOException {
        if (i < 0 || i >= pool.size()) {
            throw new IOException("target index out of range");
        }
        return pool.get(i);
    }

    private static byte[] inflate(byte[] data) throws IOException {
        final Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            final ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
            final byte[] buf = new byte[8_192];
            while (!inf.finished()) {
                final int n;
                try {
                    n = inf.inflate(buf);
                } catch (DataFormatException e) {
                    throw new IOException("scene is not deflated: " + e.getMessage());
                }
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    throw new IOException("scene truncated");
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_INFLATED) {
                    throw new IOException("scene inflates past " + MAX_INFLATED + " bytes");
                }
            }
            return out.toByteArray();
        } finally {
            inf.end();
        }
    }
}
