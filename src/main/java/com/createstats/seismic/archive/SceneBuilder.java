package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SceneBuilder {
    public static final int DEFAULT_BUDGET = 20_000;
    public static final int FINE_LIMIT = 1_500;
    public static final int MEDIUM_LIMIT = 12_000;
    public static final int MAX_GRAIN = 8;

    public static final int ORE_CELLS = 60_000;

    public static final int MAX_BODIES = 20_000;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] ALL = all();

    private SceneBuilder() {
    }

    public static SurveyScene build(SeismicArchive archive, Collection<Long> known, Collection<Long> linked, long since,
                                    int refX, int refY, int refZ, int budget, long now, long stamp) {
        return build(archive, known, linked, since, refX, refY, refZ, budget, now, stamp, ORE_CELLS, MAX_BODIES);
    }

    static SurveyScene build(SeismicArchive archive, Collection<Long> known, Collection<Long> linked, long since,
                             int refX, int refY, int refZ, int budget, long now, long stamp, int oreCells,
                             int maxBodies) {
        return build(archive.blows(), known, linked, since, refX, refY, refZ, budget, now, stamp, oreCells, maxBodies);
    }

    public static SurveyScene build(List<ArchiveBlow> blows, Collection<Long> known, Collection<Long> linked,
                                    long since, int refX, int refY, int refZ, int budget, long now, long stamp) {
        return build(blows, known, linked, since, refX, refY, refZ, budget, now, stamp, ORE_CELLS, MAX_BODIES);
    }

    private static SurveyScene build(List<ArchiveBlow> blows, Collection<Long> known, Collection<Long> linked,
                                     long since, int refX, int refY, int refZ, int budget, long now, long stamp,
                                     int oreCells, int maxBodies) {
        final Set<Long> knownSet = new HashSet<>(known);
        final Set<Long> linkedSet = new HashSet<>(linked);
        final List<ArchiveBlow> shown = new ArrayList<>();
        for (final ArchiveBlow b : blows) {
            if (knownSet.contains(b.rigKey()) && b.at >= since) {
                shown.add(b);
            }
        }
        final List<SurveyScene.Bowl> bowls = new ArrayList<>(shown.size());
        for (final ArchiveBlow b : shown) {
            bowls.add(new SurveyScene.Bowl(b.id, b.x, b.y, b.z, b.target, b.reach, (float) b.recorded, (float) b.front,
                    b.at, b.floor, b.holeLo, b.holeHi, b.done, b.full, b.veinsFound, b.blocksFound, b.nearestFound));
        }
        final List<SurveyScene.Rig> rigs = new ArrayList<>();
        for (final Long key : known) {
            String target = "";
            long last = Long.MIN_VALUE;
            int count = 0;
            for (final ArchiveBlow b : shown) {
                if (b.rigKey() == key) {
                    count++;
                    if (b.at >= last) {
                        last = b.at;
                        target = b.target;
                    }
                }
            }
            rigs.add(new SurveyScene.Rig(ArchiveBlow.keyX(key), ArchiveBlow.keyY(key), ArchiveBlow.keyZ(key),
                    linkedSet.contains(key), target, last, count));
        }

        final Map<String, Map<Integer, Group>> groups = new LinkedHashMap<>();
        for (int bi = 0; bi < shown.size(); bi++) {
            final ArchiveBlow b = shown.get(bi);
            final Map<Integer, Group> byKind = groups.computeIfAbsent(b.target, t -> new LinkedHashMap<>());
            for (final int c : b.cells) {
                byKind.computeIfAbsent(ArchiveBlow.kindOf(c), k -> new Group()).add(b.cellX(c), b.cellY(c), b.cellZ(c), bi);
            }
        }

        final List<Proto> protos = new ArrayList<>();
        final double rx = refX + 0.5;
        final double ry = refY + 0.5;
        final double rz = refZ + 0.5;
        for (final Map.Entry<String, Map<Integer, Group>> e : groups.entrySet()) {
            final boolean diagonal = SeismicCatalog.isOre(e.getKey());
            for (final Map.Entry<Integer, Group> k : e.getValue().entrySet()) {
                protos.addAll(bodies(e.getKey(), k.getKey(), k.getValue(), shown, diagonal, rx, ry, rz));
            }
        }

        int total = 0;
        for (final Proto p : protos) {
            p.grain = SeismicCatalog.isOre(p.target) || p.count <= FINE_LIMIT ? 1 : p.count <= MEDIUM_LIMIT ? 2 : 4;
            p.fold();
            total += p.folded.length;
        }

        for (int round = 0; total > budget && round < 3; round++) {
            final List<Proto> order = new ArrayList<>();
            for (final Proto p : protos) {
                if (p.grain < MAX_GRAIN && !SeismicCatalog.isOre(p.target)) {
                    order.add(p);
                }
            }
            order.sort((a, b) -> Integer.compare(b.folded.length, a.folded.length));
            for (final Proto p : order) {
                if (total <= budget) {
                    break;
                }
                total -= p.folded.length;
                p.grain *= 2;
                p.fold();
                total += p.folded.length;
            }
        }

        bound(protos, oreCells, maxBodies);

        final Map<String, List<SurveyScene.Body>> byTarget = new LinkedHashMap<>();
        for (final Proto p : protos) {
            byTarget.computeIfAbsent(p.target, t -> new ArrayList<>()).add(p.body());
        }
        final List<String> targets = new ArrayList<>(byTarget.keySet());
        targets.sort(SeismicCatalog::compare);
        final List<SurveyScene.Layer> layers = new ArrayList<>();
        for (final String t : targets) {
            final List<SurveyScene.Body> list = byTarget.get(t);
            list.sort((a, b) -> a.count != b.count ? Integer.compare(b.count, a.count)
                    : Float.compare(a.nearDist, b.nearDist));
            layers.add(new SurveyScene.Layer(t, list));
        }
        return new SurveyScene(stamp, now, layers, bowls, rigs, surface(shown));
    }

    static void bound(List<Proto> protos, int oreCells, int maxBodies) {
        final java.util.Comparator<Proto> giveUp = (a, b) -> {
            final int byTarget = SeismicCatalog.compare(a.target, b.target);
            return byTarget != 0 ? byTarget : Double.compare(b.nearD2, a.nearD2);
        };
        int ore = 0;
        for (final Proto p : protos) {
            if (SeismicCatalog.isOre(p.target)) {
                ore += p.folded.length;
            }
        }
        if (ore > oreCells) {
            final List<Proto> order = new ArrayList<>();
            for (final Proto p : protos) {
                if (SeismicCatalog.isOre(p.target) && p.folded.length > 1) {
                    order.add(p);
                }
            }
            order.sort(giveUp);
            for (final Proto p : order) {
                if (ore <= oreCells) {
                    break;
                }
                ore -= p.folded.length - 1;
                p.folded = new int[] {SurveyScene.cell(p.nearX - p.minX, p.nearY - p.minY, p.nearZ - p.minZ)};
            }
        }
        if (protos.size() > maxBodies) {
            protos.sort(giveUp.reversed());
            protos.subList(maxBodies, protos.size()).clear();
        }
    }

    static SurveyScene.Surface surface(List<ArchiveBlow> shown) {
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (final ArchiveBlow b : shown) {
            if (b.surfaceN == 0) {
                continue;
            }
            x0 = Math.min(x0, b.surfaceX0);
            z0 = Math.min(z0, b.surfaceZ0);
            x1 = Math.max(x1, b.surfaceX0 + b.surfaceN - 1);
            z1 = Math.max(z1, b.surfaceZ0 + b.surfaceN - 1);
        }
        if (x0 > x1 || (long) (x1 - x0 + 1) * (z1 - z0 + 1) > 65_536L) {
            return SurveyScene.Surface.NONE;
        }
        final int nx = x1 - x0 + 1;
        final int nz = z1 - z0 + 1;
        final short[] h = new short[nx * nz];
        Arrays.fill(h, ArchiveBlow.NO_SURFACE);
        final List<ArchiveBlow> byAge = new ArrayList<>(shown);
        byAge.sort((a, b) -> a.newerThan(b) ? 1 : b.newerThan(a) ? -1 : 0);
        for (final ArchiveBlow b : byAge) {
            for (int j = 0; j < b.surfaceN; j++) {
                for (int i = 0; i < b.surfaceN; i++) {
                    final short v = b.surface[j * b.surfaceN + i];
                    if (v != ArchiveBlow.NO_SURFACE) {
                        h[(b.surfaceZ0 + j - z0) * nx + (b.surfaceX0 + i - x0)] = v;
                    }
                }
            }
        }
        return new SurveyScene.Surface(x0, z0, nx, nz, h);
    }

    private static List<Proto> bodies(String target, int kind, Group g, List<ArchiveBlow> shown, boolean diagonal,
                                      double rx, double ry, double rz) {
        final int n = g.n;
        final LongIndex index = new LongIndex(n);
        for (int i = 0; i < n; i++) {
            index.put(ArchiveBlow.key(g.x[i], g.y[i], g.z[i]), i);
        }
        final int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        final int[][] nb = diagonal ? ALL : FACES;
        for (int i = 0; i < n; i++) {
            for (final int[] o : nb) {
                final int j = index.get(ArchiveBlow.key(g.x[i] + o[0], g.y[i] + o[1], g.z[i] + o[2]));
                if (j >= 0) {
                    union(parent, i, j);
                }
            }
        }
        final List<Proto> out = new ArrayList<>();
        final int[] protoOfRoot = new int[n];
        Arrays.fill(protoOfRoot, -1);
        final int[] protoOf = new int[n];
        for (int i = 0; i < n; i++) {
            final int r = find(parent, i);
            int s = protoOfRoot[r];
            if (s < 0) {
                s = out.size();
                protoOfRoot[r] = s;
                out.add(new Proto(target, kind));
            }
            protoOf[i] = s;
            out.get(s).take(g.x[i], g.y[i], g.z[i], shown.get(g.blow[i]), rx, ry, rz);
        }

        for (int i = 0; i < n; i++) {
            final Proto p = out.get(protoOf[i]);
            final ArchiveBlow b = shown.get(g.blow[i]);
            if (b.at == p.newest && b.id == p.newestId) {
                p.nearNewest = Math.min(p.nearNewest, b.distanceTo(g.x[i], g.y[i], g.z[i]));
            }
        }
        final int[] fill = new int[out.size()];
        for (final Proto p : out) {
            p.members = new int[p.count * 3];
        }
        for (int i = 0; i < n; i++) {
            final int s = protoOf[i];
            final Proto p = out.get(s);
            final int k = fill[s]++;
            p.members[k * 3] = g.x[i];
            p.members[k * 3 + 1] = g.y[i];
            p.members[k * 3 + 2] = g.z[i];
        }
        return out;
    }

    private static int find(int[] parent, int i) {
        int r = i;
        while (parent[r] != r) {
            r = parent[r];
        }
        while (parent[i] != r) {
            final int up = parent[i];
            parent[i] = r;
            i = up;
        }
        return r;
    }

    private static void union(int[] parent, int a, int b) {
        final int ra = find(parent, a);
        final int rb = find(parent, b);
        if (ra != rb) {
            if (ra < rb) {
                parent[rb] = ra;
            } else {
                parent[ra] = rb;
            }
        }
    }

    private static int[][] all() {
        final int[][] out = new int[26][];
        int k = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        out[k++] = new int[] {x, y, z};
                    }
                }
            }
        }
        return out;
    }

    private static final class Group {
        int n;
        int[] x = new int[64];
        int[] y = new int[64];
        int[] z = new int[64];
        int[] blow = new int[64];

        void add(int px, int py, int pz, int b) {
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2);
                y = Arrays.copyOf(y, n * 2);
                z = Arrays.copyOf(z, n * 2);
                blow = Arrays.copyOf(blow, n * 2);
            }
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            blow[n] = b;
            n++;
        }
    }

    private static final class Proto {
        final String target;
        final int kind;
        int count;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double sx;
        double sy;
        double sz;
        long newest = Long.MIN_VALUE;
        int newestId = -1;
        long oldest = Long.MAX_VALUE;
        double nearNewest = Double.MAX_VALUE;
        int nearX;
        int nearY;
        int nearZ;
        double nearD2 = Double.MAX_VALUE;
        int[] members;
        int grain = 1;
        int[] folded = new int[0];

        Proto(String target, int kind) {
            this.target = target;
            this.kind = kind;
        }

        void take(int x, int y, int z, ArchiveBlow b, double rx, double ry, double rz) {
            count++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            sx += x + 0.5;
            sy += y + 0.5;
            sz += z + 0.5;
            if (b.at > newest || (b.at == newest && b.id > newestId)) {
                newest = b.at;
                newestId = b.id;
            }
            oldest = Math.min(oldest, b.at);
            final double dx = x + 0.5 - rx;
            final double dy = y + 0.5 - ry;
            final double dz = z + 0.5 - rz;
            final double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < nearD2) {
                nearD2 = d2;
                nearX = x;
                nearY = y;
                nearZ = z;
            }
        }

        void fold() {
            final int[] cells = new int[count];
            int k = 0;
            for (int i = 0; i < count; i++) {
                final int u = (members[i * 3] - minX) / grain;
                final int v = (members[i * 3 + 1] - minY) / grain;
                final int w = (members[i * 3 + 2] - minZ) / grain;
                if (u > 0x3FF || v > 0x3FF || w > 0x3FF) {
                    continue;
                }
                cells[k++] = SurveyScene.cell(u, v, w);
            }
            Arrays.sort(cells, 0, k);
            int m = 0;
            for (int i = 0; i < k; i++) {
                if (i == 0 || cells[i] != cells[i - 1]) {
                    cells[m++] = cells[i];
                }
            }
            folded = Arrays.copyOf(cells, m);
        }

        SurveyScene.Body body() {
            final int echo = nearNewest == Double.MAX_VALUE ? 0 : SeismicWave.echoTick(nearNewest);
            return new SurveyScene.Body(target, kind, count, grain, minX, minY, minZ, folded, minX, minY, minZ, maxX,
                    maxY, maxZ, (float) (sx / count), (float) (sy / count), (float) (sz / count), newest, oldest, echo,
                    nearX, nearY, nearZ, (float) Math.sqrt(nearD2));
        }
    }

    static final class LongIndex {
        private long[] keys;
        private int[] values;
        private final int mask;
        private static final long EMPTY = Long.MIN_VALUE;

        LongIndex(int expected) {
            int cap = 16;
            while (cap < expected * 2) {
                cap <<= 1;
            }
            keys = new long[cap];
            values = new int[cap];
            Arrays.fill(keys, EMPTY);
            mask = cap - 1;
        }

        void put(long key, int value) {
            int s = mix(key) & mask;
            while (keys[s] != EMPTY && keys[s] != key) {
                s = (s + 1) & mask;
            }
            keys[s] = key;
            values[s] = value;
        }

        int get(long key) {
            int s = mix(key) & mask;
            while (keys[s] != EMPTY) {
                if (keys[s] == key) {
                    return values[s];
                }
                s = (s + 1) & mask;
            }
            return -1;
        }

        private static int mix(long v) {
            final long h = v * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 31));
        }
    }
}
