package com.createstats.seismic.archive;

import java.util.Collections;
import java.util.List;

public final class SurveyScene {
    public static final SurveyScene EMPTY = new SurveyScene(0L, 0L, List.of(), List.of(), List.of(), Surface.NONE);

    public final long stamp;

    public final long builtAt;

    public final List<Layer> layers;

    public final List<Bowl> bowls;

    public final List<Rig> rigs;

    public final Surface surface;

    public SurveyScene(long stamp, long builtAt, List<Layer> layers, List<Bowl> bowls, List<Rig> rigs, Surface surface) {
        this.stamp = stamp;
        this.builtAt = builtAt;
        this.layers = Collections.unmodifiableList(layers);
        this.bowls = Collections.unmodifiableList(bowls);
        this.rigs = Collections.unmodifiableList(rigs);
        this.surface = surface == null ? Surface.NONE : surface;
    }

    public record Surface(int x0, int z0, int nx, int nz, short[] heights) {
        public static final Surface NONE = new Surface(0, 0, 0, 0, new short[0]);

        public boolean isEmpty() {
            return nx == 0 || nz == 0;
        }

        public int at(int i, int j) {
            if (i < 0 || j < 0 || i >= nx || j >= nz) {
                return ArchiveBlow.NO_SURFACE;
            }
            return heights[j * nx + i];
        }

        public int worldX(int i) {
            return (x0 + i) * ArchiveBlow.SURFACE_STEP;
        }

        public int worldZ(int j) {
            return (z0 + j) * ArchiveBlow.SURFACE_STEP;
        }
    }

    public boolean isEmpty() {
        return bowls.isEmpty();
    }

    public SurveyScene heardAt(long now) {
        boolean waiting = false;
        for (final Layer l : layers) {
            for (final Body b : l.bodies) {
                if (unheard(b, now)) {
                    waiting = true;
                    break;
                }
            }
            if (waiting) {
                break;
            }
        }
        if (!waiting) {
            return this;
        }
        final List<Layer> out = new java.util.ArrayList<>(layers.size());
        for (final Layer l : layers) {
            final List<Body> kept = new java.util.ArrayList<>(l.bodies.size());
            for (final Body b : l.bodies) {
                if (!unheard(b, now)) {
                    kept.add(b);
                }
            }
            if (!kept.isEmpty()) {
                out.add(new Layer(l.target, kept));
            }
        }
        return new SurveyScene(stamp, builtAt, out, bowls, rigs, surface);
    }

    public static final int FACE_SCAN = 0;
    public static final int FACE_BOARD = 1;
    public static final int FACE_ECHO = 2;

    public SurveyScene faceView(int what, java.util.Set<String> hidden, long pin, double refX, double refZ) {
        final long last = bowls.isEmpty() ? Long.MIN_VALUE : bowls.get(bowls.size() - 1).at;
        final int px = pin == Long.MIN_VALUE ? 0 : ArchiveBlow.keyX(pin);
        final int py = pin == Long.MIN_VALUE ? 0 : ArchiveBlow.keyY(pin);
        final int pz = pin == Long.MIN_VALUE ? 0 : ArchiveBlow.keyZ(pin);
        final List<Layer> out = new java.util.ArrayList<>(layers.size());
        for (final Layer l : layers) {
            if (hidden.contains(l.target)) {
                continue;
            }
            final List<Body> kept = new java.util.ArrayList<>();

            final java.util.Map<Integer, Body> nearest = new java.util.HashMap<>();
            for (final Body b : l.bodies) {
                final Body was = nearest.get(b.kind);
                if (was == null || ground(b, refX, refZ) < ground(was, refX, refZ)) {
                    nearest.put(b.kind, b);
                }
            }
            for (final Body b : l.bodies) {
                final boolean pinned = pin != Long.MIN_VALUE && px >= b.minX - 2 && px <= b.maxX + 2 && py >= b.minY - 2
                        && py <= b.maxY + 2 && pz >= b.minZ - 2 && pz <= b.maxZ + 2;
                final boolean keep = what == FACE_SCAN || pinned || nearest.get(b.kind) == b
                        || (what == FACE_ECHO && b.newest == last);
                if (keep) {
                    kept.add(b);
                }
            }
            if (!kept.isEmpty()) {
                out.add(new Layer(l.target, kept));
            }
        }
        return new SurveyScene(stamp, builtAt, out, bowls, rigs, surface);
    }

    private static double ground(Body b, double refX, double refZ) {
        final double dx = b.nearX + 0.5 - refX;
        final double dz = b.nearZ + 0.5 - refZ;
        return dx * dx + dz * dz;
    }

    public static boolean unheard(Body b, long now) {
        return b.oldest == b.newest && b.newest != Long.MIN_VALUE && now >= b.newest && now - b.newest < b.echo;
    }

    public Layer layer(String target) {
        for (final Layer l : layers) {
            if (l.target.equals(target)) {
                return l;
            }
        }
        return null;
    }

    public List<String> targetsSurveyed() {
        final java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (final Bowl b : bowls) {
            if (!out.contains(b.target)) {
                out.add(b.target);
            }
        }
        out.sort(com.createstats.seismic.SeismicCatalog::compare);
        return out;
    }

    public long newestBlow() {
        long t = Long.MIN_VALUE;
        for (final Bowl b : bowls) {
            t = Math.max(t, b.at);
        }
        return t;
    }

    public int[] extent() {
        if (bowls.isEmpty()) {
            return new int[] {0, 0, 0, 0, 0, 0};
        }
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (final Bowl b : bowls) {
            final int r = (int) Math.ceil(b.front);
            x0 = Math.min(x0, b.x - r);
            x1 = Math.max(x1, b.x + r);
            z0 = Math.min(z0, b.z - r);
            z1 = Math.max(z1, b.z + r);
            y0 = Math.min(y0, Math.max(b.floor, b.y - r));
            y1 = Math.max(y1, b.y);
        }

        for (final short h : surface.heights()) {
            if (h != ArchiveBlow.NO_SURFACE) {
                y1 = Math.max(y1, h);
            }
        }
        return new int[] {x0, y0, z0, x1, y1, z1};
    }

    public static final class Layer {
        public final String target;

        public final List<Body> bodies;

        public final int blocks;

        public Layer(String target, List<Body> bodies) {
            this.target = target;
            this.bodies = Collections.unmodifiableList(bodies);
            int n = 0;
            for (final Body b : bodies) {
                n += b.count;
            }
            this.blocks = n;
        }
    }

    public static final class Body {
        public final String target;
        public final int kind;

        public final int count;

        public final int grain;

        public final int ox;
        public final int oy;
        public final int oz;

        public final int[] cells;

        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;

        public final float cx;
        public final float cy;
        public final float cz;

        public final long newest;
        public final long oldest;

        public final int echo;

        public final int nearX;
        public final int nearY;
        public final int nearZ;
        public final float nearDist;

        public Body(String target, int kind, int count, int grain, int ox, int oy, int oz, int[] cells, int minX,
                    int minY, int minZ, int maxX, int maxY, int maxZ, float cx, float cy, float cz, long newest,
                    long oldest, int echo, int nearX, int nearY, int nearZ, float nearDist) {
            this.target = target;
            this.kind = kind;
            this.count = count;
            this.grain = grain;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.cells = cells;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.newest = newest;
            this.oldest = oldest;
            this.echo = echo;
            this.nearX = nearX;
            this.nearY = nearY;
            this.nearZ = nearZ;
            this.nearDist = nearDist;
        }

        public int cellX(int c) {
            return ox + cellU(c) * grain;
        }

        public int cellY(int c) {
            return oy + cellV(c) * grain;
        }

        public int cellZ(int c) {
            return oz + cellW(c) * grain;
        }
    }

    public static final class Bowl {
        public final int id;
        public final int x;
        public final int y;
        public final int z;
        public final String target;
        public final int reach;

        public final float recorded;
        public final float front;
        public final long at;
        public final int floor;
        public final long holeLo;
        public final long holeHi;
        public final boolean done;
        public final boolean full;

        public final int veins;
        public final int blocks;
        public final float nearest;

        public Bowl(int id, int x, int y, int z, String target, int reach, float recorded, float front, long at,
                    int floor, long holeLo, long holeHi, boolean done, boolean full, int veins, int blocks,
                    float nearest) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = target;
            this.reach = reach;
            this.recorded = recorded;
            this.front = front;
            this.at = at;
            this.floor = floor;
            this.holeLo = holeLo;
            this.holeHi = holeHi;
            this.done = done;
            this.full = full;
            this.veins = veins;
            this.blocks = blocks;
            this.nearest = nearest;
        }

        public boolean hole(int bx, int bz) {
            final int dx = (bx >> 4) - (x >> 4) + ArchiveBlow.HOLE_SPAN;
            final int dz = (bz >> 4) - (z >> 4) + ArchiveBlow.HOLE_SPAN;
            final int side = ArchiveBlow.HOLE_SPAN * 2 + 1;
            if (dx < 0 || dz < 0 || dx >= side || dz >= side) {
                return false;
            }
            final int bit = dz * side + dx;
            return bit < 64 ? (holeLo >>> bit & 1L) != 0L : (holeHi >>> (bit - 64) & 1L) != 0L;
        }

        public boolean covers(double px, double py, double pz) {
            if (py > y || py < floor) {
                return false;
            }
            final double dx = px - (x + 0.5);
            final double dy = py - y;
            final double dz = pz - (z + 0.5);
            return dx * dx + dy * dy + dz * dz <= (double) recorded * recorded
                    && !hole((int) Math.floor(px), (int) Math.floor(pz));
        }

        public double radiusAt(double depthY) {
            final double d = y - depthY;
            if (d < 0.0 || depthY < floor) {
                return 0.0;
            }
            final double r2 = (double) recorded * recorded - d * d;
            return r2 <= 0.0 ? 0.0 : Math.sqrt(r2);
        }
    }

    public static final class Rig {
        public final int x;
        public final int y;
        public final int z;

        public final boolean linked;

        public final String target;
        public final long lastAt;
        public final int blows;

        public Rig(int x, int y, int z, boolean linked, String target, long lastAt, int blows) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.linked = linked;
            this.target = target;
            this.lastAt = lastAt;
            this.blows = blows;
        }
    }

    public static int cell(int u, int v, int w) {
        return (u & 0x3FF) << 20 | (v & 0x3FF) << 10 | (w & 0x3FF);
    }

    public static int cellU(int c) {
        return c >>> 20 & 0x3FF;
    }

    public static int cellV(int c) {
        return c >>> 10 & 0x3FF;
    }

    public static int cellW(int c) {
        return c & 0x3FF;
    }
}
