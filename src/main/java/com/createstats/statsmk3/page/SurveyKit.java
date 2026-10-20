package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.archive.ScanCamera;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// ore = crisp voxels, caves = clouds. the eye has to tell them apart
@OnlyIn(Dist.CLIENT)
public final class SurveyKit {
    private SurveyKit() {
    }

    static final int ORE = 0;
    static final int VOID = 1;
    static final int WATER = 2;
    static final int LAVA = 3;

    static final int MAX_GLOWS = 64;
    static final int MAX_LIP_SEGMENTS = 192;
    static final int MAX_TERRAIN_LINES = 300;

    public static final int VOID_HAZE = 0xFFC3C8D0;

    public static int colour(String target, int kind) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return VOID_HAZE;
        }
        return 0xFF000000 | SeismicCatalog.colour(target, kind);
    }

    public static void swatch(PanelSurface s, Rect r, String target, int kind) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            Shapes.roundedRectOutline(s, r, 1.0f, Math.max(1.0f, r.w * 0.18f), VOID_HAZE);
        } else if (SeismicCatalog.LIQUIDS.equals(target)) {
            Shapes.roundedRect(s, new Rect(r.x, r.y, r.w * 0.6f, r.h), 1.0f, colour(target, SeismicCatalog.KIND_LAVA));
            Shapes.roundedRect(s, new Rect(r.x + r.w * 0.5f, r.y, r.w * 0.5f, r.h), 1.0f,
                    colour(target, SeismicCatalog.KIND_WATER));
        } else {
            Shapes.roundedRect(s, r, 1.0f, colour(target, kind));
        }
    }

    static int classOf(String target, int kind) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return VOID;
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return kind == SeismicCatalog.KIND_LAVA ? LAVA : WATER;
        }
        return ORE;
    }

    public static final class Scan {
        public double yaw = 32.0;
        public double pitch = 30.0;
        public double zoom = 1.0;

        public int budget = 2_400;
        public int cloud = 1_400;
        public boolean room = true;
        public boolean bowls = true;
        public boolean rigs = true;
        public boolean compass = true;
        public boolean ruler = true;
        public boolean callouts = true;
        public int maxCallouts = 4;

        public float rail = 0.0f;

        public Set<String> hidden = Set.of();

        public String focus = "";

        public double sliceLo = Double.NaN;
        public double sliceHi = Double.NaN;

        public SurveyScene.Body selected;

        public long now = Long.MIN_VALUE;

        public double refX = Double.NaN;
        public double refY;
        public double refZ;

        public float micro = Text.MICRO;
        public float label = Text.MICRO;

        public int ground = Tokens.SURFACE;

        public float pointFill = 0.86f;
        public float minPoint = 1.8f;

        public boolean terrain = true;
        public int terrainStep = 1;

        public double[] frame;

        public double[] centre;

        public SurveyScene.Bowl dome;

        public boolean glow = true;

        public double[] toward;

        public java.util.Map<Long, Integer> rigNumbers = java.util.Map.of();

        public boolean here = true;

        public int[] dig;

        final List<Rect> labels = new ArrayList<>();
    }

    static final class Points {
        int n;
        float[] x;
        float[] y;
        float[] z;
        byte[] cls;
        float[] size;
        int[] colour;
        int[] body;
        int ores;
        int cloudGrain;
        final List<SurveyScene.Body> bodies = new ArrayList<>();
    }

    private static final Map<SurveyScene, Map<String, Points>> CACHE = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SurveyScene, Map<String, Points>> eldest) {
            return size() > 4;
        }
    };

    static synchronized Points points(SurveyScene scene, int budget, int cloud, Set<String> hidden) {
        final Map<String, Points> forScene = CACHE.computeIfAbsent(scene, k -> new HashMap<>());
        final String key = budget + "/" + cloud + "/" + new java.util.TreeSet<>(hidden);
        Points p = forScene.get(key);
        if (p == null) {
            p = build(scene, budget, cloud, hidden);
            if (forScene.size() > 16) {
                forScene.clear();
            }
            forScene.put(key, p);
        }
        return p;
    }

    private static Points build(SurveyScene scene, int budget, int cloud, Set<String> hidden) {
        final Points p = new Points();
        int ores = 0;
        final List<SurveyScene.Body> clouds = new ArrayList<>();
        for (final SurveyScene.Layer l : scene.layers) {
            if (hidden.contains(l.target)) {
                continue;
            }
            for (final SurveyScene.Body b : l.bodies) {
                if (classOf(b.target, b.kind) == ORE) {
                    ores += b.cells.length;
                } else {
                    clouds.add(b);
                }
            }
        }

        int grain = 2;
        List<int[]> folded = fold(clouds, grain);
        while (count(folded) > cloud && grain < 16) {
            grain *= 2;
            folded = fold(clouds, grain);
        }
        p.cloudGrain = grain;
        final int capacity = Math.min(ores, budget) + count(folded);
        p.x = new float[Math.max(1, capacity)];
        p.y = new float[p.x.length];
        p.z = new float[p.x.length];
        p.cls = new byte[p.x.length];
        p.size = new float[p.x.length];
        p.colour = new int[p.x.length];
        p.body = new int[p.x.length];
        for (int i = 0; i < clouds.size(); i++) {
            final SurveyScene.Body b = clouds.get(i);
            final int bi = p.bodies.size();
            p.bodies.add(b);
            final int cls = classOf(b.target, b.kind);
            final int[] cells = folded.get(i);
            for (int k = 0; k + 2 < cells.length; k += 3) {
                final int j = p.n++;
                p.x[j] = cells[k] + grain * 0.5f;
                p.y[j] = cells[k + 1] + grain * 0.5f;
                p.z[j] = cells[k + 2] + grain * 0.5f;
                p.cls[j] = (byte) cls;
                p.size[j] = grain;
                p.colour[j] = colour(b.target, b.kind);
                p.body[j] = bi;
            }
        }

        final List<SurveyScene.Layer> byValue = new ArrayList<>(scene.layers);
        byValue.sort((a, b) -> SeismicCatalog.compare(b.target, a.target));
        for (final SurveyScene.Layer l : byValue) {
            if (hidden.contains(l.target) || classOf(l.target, 0) != ORE) {
                continue;
            }
            for (final SurveyScene.Body b : l.bodies) {
                final int bi = p.bodies.size();
                p.bodies.add(b);
                for (final int c : b.cells) {
                    if (p.n >= p.x.length) {
                        break;
                    }
                    final int j = p.n++;
                    p.x[j] = b.cellX(c) + 0.5f;
                    p.y[j] = b.cellY(c) + 0.5f;
                    p.z[j] = b.cellZ(c) + 0.5f;
                    p.cls[j] = ORE;
                    p.size[j] = 1.0f;
                    p.colour[j] = colour(b.target, b.kind);
                    p.body[j] = bi;
                    p.ores++;
                }
            }
        }
        return p;
    }

    private static List<int[]> fold(List<SurveyScene.Body> bodies, int grain) {
        final List<int[]> out = new ArrayList<>(bodies.size());
        for (final SurveyScene.Body b : bodies) {
            final Set<Long> seen = new HashSet<>();
            final int[] cells = new int[b.cells.length * 3];
            int n = 0;
            for (final int c : b.cells) {
                final int x = Math.floorDiv(b.cellX(c), grain) * grain;
                final int y = Math.floorDiv(b.cellY(c), grain) * grain;
                final int z = Math.floorDiv(b.cellZ(c), grain) * grain;
                if (seen.add(((long) x << 40) ^ ((long) (y & 0xFFFFF) << 20) ^ (z & 0xFFFFF))) {
                    cells[n++] = x;
                    cells[n++] = y;
                    cells[n++] = z;
                }
            }
            out.add(Arrays.copyOf(cells, n));
        }
        return out;
    }

    private static int count(List<int[]> folded) {
        int n = 0;
        for (final int[] f : folded) {
            n += f.length / 3;
        }
        return n;
    }

    public static double[] box(SurveyScene scene) {
        final int[] e = scene.extent();
        return new double[] {e[0] - 2.0, e[1] - 1.0, e[2] - 2.0, e[3] + 3.0, e[4] + 2.0, e[5] + 3.0};
    }

    public static ScanCamera camera(SurveyScene scene, Rect r, Scan o) {
        final double[] b = o.frame != null ? o.frame : box(scene);
        final ScanCamera fit = ScanCamera.fit(o.yaw, o.pitch, b[0], b[1], b[2], b[3], b[4], b[5], r.x, r.y, r.w, r.h,
                0.06, o.zoom);
        if (o.centre == null || o.centre.length < 3 || !Double.isFinite(o.centre[0]) || !Double.isFinite(o.centre[1])
                || !Double.isFinite(o.centre[2])) {
            return fit;
        }

        return new ScanCamera(o.yaw, o.pitch, fit.scale, o.centre[0], o.centre[1], o.centre[2], r.centerX(),
                r.centerY());
    }

    public static SurveyScene.Body pick(SurveyScene scene, Rect r, Scan o, double mx, double my, double reach) {
        final Rect field = o.rail > 0.0f ? new Rect(r.x, r.y, r.w - o.rail, r.h) : r;
        final ScanCamera cam = camera(scene, field, o);
        final Points pts = points(scene, o.budget, o.cloud, o.hidden);
        final double[] p = new double[3];
        SurveyScene.Body best = null;
        double bestD = reach * reach;
        boolean bestOre = false;
        for (int i = 0; i < pts.n; i++) {
            final boolean ore = pts.cls[i] == ORE;
            if (bestOre && !ore) {
                continue;
            }
            if (!Double.isNaN(o.sliceLo) && (pts.y[i] < o.sliceLo || pts.y[i] > o.sliceHi + 1.0)) {
                continue;
            }
            cam.project(pts.x[i], pts.y[i], pts.z[i], p);
            final double dx = p[0] - mx;
            final double dy = p[1] - my;
            final double d = dx * dx + dy * dy;
            if (d <= bestD || (ore && !bestOre && d <= reach * reach)) {
                bestD = d;
                best = pts.bodies.get(pts.body[i]);
                bestOre = ore;
            }
        }
        return best;
    }

    public static double[] boxOf(SurveyScene.Bowl b) {
        final double r = Math.max(4.0, b.recorded);
        return new double[] {b.x + 0.5 - r, Math.max(b.floor, b.y - r), b.z + 0.5 - r, b.x + 0.5 + r, b.y + 1.0,
                b.z + 0.5 + r};
    }

    public static ScanCamera scan(PanelSurface s, Rect r, SurveyScene scene, Scan o) {
        o.labels.clear();
        final Rect field = o.rail > 0.0f ? new Rect(r.x, r.y, r.w - o.rail, r.h) : r;
        final ScanCamera cam = camera(scene, field, o);
        final double[] box = o.frame != null ? o.frame : box(scene);
        if (o.room) {
            room(s, r, cam, box, o);
        }
        s.advanceLayer();
        if (o.terrain) {
            terrain(s, r, cam, scene.surface, o);
        }
        if (o.dome != null) {
            dome(s, r, cam, o.dome, o);
        }
        if (o.bowls) {
            bowls(s, r, cam, scene, o);
        }
        s.advanceLayer();
        finds(s, r, cam, scene, o);
        s.advanceLayer();
        if (o.rigs) {
            rigs(s, r, cam, scene, o);
        }
        if (o.here) {
            here(s, field, cam, o);
        }
        if (o.dig != null) {
            dig(s, field, cam, scene.surface, o);
        }
        if (o.ruler && o.room && cam.pitch < 75.0) {
            ruler(s, r, cam, box, o);
        }
        if (o.callouts) {
            callouts(s, r, cam, scene, o);
        }
        if (o.compass) {
            compass(s, field, cam, o);
        }
        return cam;
    }

    static void room(PanelSurface s, Rect r, ScanCamera cam, double[] b, Scan o) {
        final double x0 = b[0];
        final double y0 = b[1];
        final double z0 = b[2];
        final double x1 = b[3];
        final double y1 = b[4];
        final double z1 = b[5];
        final double fwdX = Math.sin(Math.toRadians(cam.yaw));
        final double fwdZ = -Math.cos(Math.toRadians(cam.yaw));
        final double wx = fwdX >= 0.0 ? x1 : x0;
        final double wz = fwdZ <= 0.0 ? z0 : z1;
        final double ox = wx == x1 ? x0 : x1;
        final double oz = wz == z0 ? z1 : z0;
        final int edge = Tokens.alpha(Tokens.LINE_HI, 0.85f);
        final int grid = Tokens.alpha(Tokens.LINE_HI, 0.30f);
        final float hair = hairWidth(o);
        if (cam.fromAbove()) {
            for (int gx = ceil16(x0); gx <= x1; gx += 16) {
                hair(s, r, cam, gx, y0, z0, gx, y0, z1, hair, grid);
            }
            for (int gz = ceil16(z0); gz <= z1; gz += 16) {
                hair(s, r, cam, x0, y0, gz, x1, y0, gz, hair, grid);
            }
            hair(s, r, cam, x0, y0, z0, x1, y0, z0, hair, edge);
            hair(s, r, cam, x0, y0, z1, x1, y0, z1, hair, edge);
            hair(s, r, cam, x0, y0, z0, x0, y0, z1, hair, edge);
            hair(s, r, cam, x1, y0, z0, x1, y0, z1, hair, edge);
        }
        for (int gy = ceil16(y0); gy <= y1; gy += 16) {
            hair(s, r, cam, wx, gy, z0, wx, gy, z1, hair, grid);
            hair(s, r, cam, x0, gy, wz, x1, gy, wz, hair, grid);
        }
        hair(s, r, cam, wx, y0, wz, wx, y1, wz, hair, edge);
        hair(s, r, cam, wx, y0, oz, wx, y1, oz, hair, grid);
        hair(s, r, cam, ox, y0, wz, ox, y1, wz, hair, grid);
    }

    private static int ceil16(double v) {
        return (int) Math.ceil(v / 16.0) * 16;
    }

    static float hairWidth(Scan o) {
        return Math.max(0.6f, o.micro * 0.85f);
    }

    static void ruler(PanelSurface s, Rect r, ScanCamera cam, double[] b, Scan o) {
        final double fwdX = Math.sin(Math.toRadians(cam.yaw));
        final double fwdZ = -Math.cos(Math.toRadians(cam.yaw));
        final double wx = fwdX >= 0.0 ? b[3] : b[0];
        final double wz = fwdZ <= 0.0 ? b[2] : b[5];
        final double ox = wx == b[3] ? b[0] : b[3];
        final double oz = wz == b[2] ? b[5] : b[2];
        final double[] p = new double[3];
        final double[] q = new double[3];
        cam.project(wx, b[1], oz, p);
        cam.project(ox, b[1], wz, q);
        final double lx = p[0] < q[0] ? wx : ox;
        final double lz = p[0] < q[0] ? oz : wz;
        final int ink = Tokens.INK_3;
        final float lh = Text.lineHeight(o.micro);
        float lastY = -Float.MAX_VALUE;
        for (int gy = ceil16(b[1]); gy <= b[4]; gy += 16) {
            cam.project(lx, gy, lz, p);
            final String t = "Y " + gy;
            final float w = s.textWidth(t, o.micro);
            final float tx = (float) p[0] - w - 4.0f;
            final float ty = (float) p[1] - lh * 0.5f;
            if (tx < r.x || ty < r.y || ty + lh > r.bottom() || Math.abs(ty - lastY) < lh + 1.0f) {
                continue;
            }
            s.text(t, tx, ty, ink, o.micro);
            lastY = ty;
        }
    }

    static void bowls(PanelSurface s, Rect r, ScanCamera cam, SurveyScene scene, Scan o) {
        final Map<String, Float> widest = new HashMap<>();
        final Map<String, SurveyScene.Bowl> at = new HashMap<>();
        for (final SurveyScene.Bowl b : scene.bowls) {
            if (o.hidden.contains(b.target) || b.recorded < 1.0f) {
                continue;
            }
            final String key = b.x + "," + b.y + "," + b.z;
            if (b.recorded > widest.getOrDefault(key, 0.0f)) {
                widest.put(key, b.recorded);
                at.put(key, b);
            }
        }
        final float hair = hairWidth(o);
        final double[] c = new double[3];
        final double[] d = new double[3];
        final double squash = Math.sin(Math.toRadians(cam.pitch));
        final int lip = Tokens.alpha(Tokens.INK_2, 0.40f);

        final int share = Math.max(12, MAX_LIP_SEGMENTS * 2 / Math.max(1, at.size()));
        for (final Map.Entry<String, SurveyScene.Bowl> e : at.entrySet()) {
            final SurveyScene.Bowl b = e.getValue();
            final double reach = widest.get(e.getKey());
            final int segs = Math.min(share, Math.max(24, Math.min(96, (int) (reach * cam.scale * 0.5))));
            double px = Double.NaN;
            double py = Double.NaN;
            for (int i = 0; i <= segs; i++) {
                final double a = Math.PI * 2.0 * i / segs;
                final double wx = b.x + 0.5 + Math.cos(a) * reach;
                final double wz = b.z + 0.5 + Math.sin(a) * reach;
                final double wy = groundAt(scene.surface, wx, wz, b.y);
                cam.project(wx, wy + 0.5, wz, d);
                if (i > 0 && (i / 2) % 2 == 0) {
                    hairLine(s, r, (float) px, (float) py, (float) d[0], (float) d[1], hair, lip);
                }
                px = d[0];
                py = d[1];
            }
        }

        if (o.now != Long.MIN_VALUE) {
            for (final SurveyScene.Bowl b : scene.bowls) {
                final long age = o.now - b.at;
                final int listen = SeismicWave.listenTicks(b.reach);
                if (age < 0 || age > listen + 30 || o.hidden.contains(b.target)) {
                    continue;
                }
                final double front = Math.min(b.reach, SeismicWave.frontAt(age));
                final float fade = (float) Math.max(0.0, 1.0 - Math.max(0.0, age - SeismicWave.scanTicks(b.reach))
                        / (listen + 30.0 - SeismicWave.scanTicks(b.reach)));
                cam.project(b.x + 0.5, b.y, b.z + 0.5, c);
                final double rad = front * cam.scale;
                ellipse(s, r, c[0], c[1], rad, rad * squash, 0.0, Math.PI * 2.0, hair * 1.5f,
                        Tokens.alpha(Tokens.INK, 0.70f * fade), 0);
                ellipse(s, r, c[0], c[1], rad, rad, 0.0, Math.PI, hair * 1.2f, Tokens.alpha(Tokens.INK, 0.32f * fade), 0);
            }
        }
    }

    // unread ground leaves a gap
    static void terrain(PanelSurface s, Rect r, ScanCamera cam, SurveyScene.Surface sf, Scan o) {
        if (sf.isEmpty()) {
            return;
        }

        int step = Math.max(1, o.terrainStep);
        while (2L * ((sf.nx() + step - 1) / step) * ((sf.nz() + step - 1) / step) > MAX_TERRAIN_LINES) {
            step *= 2;
        }
        final float hair = hairWidth(o);
        final double[] a = new double[3];
        final double[] b = new double[3];
        final int line = Tokens.alpha(Tokens.EARTH_EDGE, 0.42f);
        for (int j = 0; j < sf.nz(); j += step) {
            for (int i = 0; i < sf.nx(); i += step) {
                final int h = sf.at(i, j);
                if (h == com.createstats.seismic.archive.ArchiveBlow.NO_SURFACE) {
                    continue;
                }
                cam.project(sf.worldX(i), h, sf.worldZ(j), a);
                final int east = sf.at(i + step, j);
                if (east != com.createstats.seismic.archive.ArchiveBlow.NO_SURFACE) {
                    cam.project(sf.worldX(i + step), east, sf.worldZ(j), b);
                    hairLine(s, r, (float) a[0], (float) a[1], (float) b[0], (float) b[1], hair, line);
                }
                final int south = sf.at(i, j + step);
                if (south != com.createstats.seismic.archive.ArchiveBlow.NO_SURFACE) {
                    cam.project(sf.worldX(i), south, sf.worldZ(j + step), b);
                    hairLine(s, r, (float) a[0], (float) a[1], (float) b[0], (float) b[1], hair, line);
                }
            }
        }
    }

    static void dome(PanelSurface s, Rect r, ScanCamera cam, SurveyScene.Bowl b, Scan o) {
        final float hair = hairWidth(o);
        final double[] c = new double[3];
        final double squash = Math.sin(Math.toRadians(cam.pitch));
        final double reach = Math.max(1.0, b.recorded);
        cam.project(b.x + 0.5, b.y, b.z + 0.5, c);
        ellipse(s, r, c[0], c[1], reach * cam.scale, reach * cam.scale * squash, 0.0, Math.PI * 2.0, hair * 1.2f,
                Tokens.alpha(Tokens.INK_2, 0.55f), 0);
        ellipse(s, r, c[0], c[1], reach * cam.scale, reach * cam.scale, 0.0, Math.PI, hair,
                Tokens.alpha(Tokens.INK_3, 0.5f), 0);
        for (int d = 16; d < reach; d += 16) {
            final double ring = Math.sqrt(reach * reach - d * (double) d);
            cam.project(b.x + 0.5, b.y - d, b.z + 0.5, c);
            ellipse(s, r, c[0], c[1], ring * cam.scale, ring * cam.scale * squash, 0.0, Math.PI * 2.0, hair,
                    Tokens.alpha(Tokens.INK_3, 0.32f), 2);
            final String t = "Y " + (b.y - d);
            final float tx = (float) (c[0] + ring * cam.scale) + 3.0f;
            final float ty = (float) c[1] - Text.lineHeight(o.micro) * 0.5f;
            if (tx + s.textWidth(t, o.micro) < r.right() && ty > r.y && ty + Text.lineHeight(o.micro) < r.bottom()) {
                s.text(t, tx, ty, Tokens.INK_3, o.micro);
            }
        }
    }

    static double groundAt(SurveyScene.Surface sf, double wx, double wz, double fallback) {
        if (sf.isEmpty()) {
            return fallback;
        }
        final double gx = wx / com.createstats.seismic.archive.ArchiveBlow.SURFACE_STEP - sf.x0();
        final double gz = wz / com.createstats.seismic.archive.ArchiveBlow.SURFACE_STEP - sf.z0();
        final int i = (int) Math.floor(gx);
        final int j = (int) Math.floor(gz);
        final double u = gx - i;
        final double v = gz - j;
        double sum = 0.0;
        double weight = 0.0;
        final int[][] corners = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (final int[] k : corners) {
            final int h = sf.at(i + k[0], j + k[1]);
            if (h == com.createstats.seismic.archive.ArchiveBlow.NO_SURFACE) {
                continue;
            }
            final double w = (k[0] == 0 ? 1.0 - u : u) * (k[1] == 0 ? 1.0 - v : v);
            sum += h * w;
            weight += w;
        }
        return weight < 1.0e-6 ? fallback : sum / weight;
    }

    static void ellipse(PanelSurface s, Rect r, double cx, double cy, double rx, double ry, double from, double to,
                        float width, int colour, int dash) {
        ellipse(s, r, cx, cy, rx, ry, from, to, width, colour, dash, 96);
    }

    static void ellipse(PanelSurface s, Rect r, double cx, double cy, double rx, double ry, double from, double to,
                        float width, int colour, int dash, int most) {
        final int segs = Math.max(Math.min(16, most), Math.min(Math.min(96, most), (int) (Math.max(rx, ry) * 0.6)));
        double px = cx + Math.cos(from) * rx;
        double py = cy + Math.sin(from) * ry;
        for (int i = 1; i <= segs; i++) {
            final double a = from + (to - from) * i / segs;
            final double qx = cx + Math.cos(a) * rx;
            final double qy = cy + Math.sin(a) * ry;
            if (dash == 0 || ((i - 1) / dash) % 2 == 0) {
                hairLine(s, r, (float) px, (float) py, (float) qx, (float) qy, width, colour);
            }
            px = qx;
            py = qy;
        }
    }

    static void finds(PanelSurface s, Rect r, ScanCamera cam, SurveyScene scene, Scan o) {
        final Points pts = points(scene, o.budget, o.cloud, o.hidden);
        if (pts.n == 0) {
            return;
        }
        final float[] sx = new float[pts.n];
        final float[] sy = new float[pts.n];
        final float[] depth = new float[pts.n];
        final long[] order = new long[pts.n];
        final double[] p = new double[3];
        double dmin = Double.MAX_VALUE;
        double dmax = -Double.MAX_VALUE;
        for (int i = 0; i < pts.n; i++) {
            cam.project(pts.x[i], pts.y[i], pts.z[i], p);
            sx[i] = (float) p[0];
            sy[i] = (float) p[1];
            depth[i] = (float) p[2];
            dmin = Math.min(dmin, p[2]);
            dmax = Math.max(dmax, p[2]);
        }
        for (int i = 0; i < pts.n; i++) {
            order[i] = ((long) sortable(-depth[i]) << 32) | i;
        }
        Arrays.sort(order);

        if (o.glow && o.focus != null && !o.focus.isEmpty() && classOf(o.focus, 0) == ORE) {
            final double[] q = new double[3];
            int glows = 0;

            for (final SurveyScene.Body b : pts.bodies) {
                if (!b.target.equals(o.focus)) {
                    continue;
                }
                if (glows >= MAX_GLOWS) {
                    break;
                }
                if (o.frame != null && (b.cx < o.frame[0] || b.cx > o.frame[3] || b.cy < o.frame[1]
                        || b.cy > o.frame[4] || b.cz < o.frame[2] || b.cz > o.frame[5])) {
                    continue;
                }
                if (!Double.isNaN(o.sliceLo) && (b.maxY < o.sliceLo || b.minY > o.sliceHi)) {
                    continue;
                }
                cam.project(b.cx, b.cy, b.cz, q);
                final float g = (float) Math.max(3.0, Math.min(28.0, Math.cbrt(b.count) * cam.scale * 1.5 + 3.0));
                final float cx = (float) q[0];
                final float cy = (float) q[1];
                if (cx - g < r.x || cy - g < r.y || cx + g > r.right() || cy + g > r.bottom()) {
                    continue;
                }
                glows++;
                final int col = colour(b.target, b.kind);
                final int gc = Tokens.alpha(col, 0.22f);
                final int clear = Tokens.alpha(col, 0.0f);
                s.tri(cx, cy, gc, cx - g, cy - g, clear, cx + g, cy - g, clear);
                s.tri(cx, cy, gc, cx + g, cy - g, clear, cx + g, cy + g, clear);
                s.tri(cx, cy, gc, cx + g, cy + g, clear, cx - g, cy + g, clear);
                s.tri(cx, cy, gc, cx - g, cy + g, clear, cx - g, cy - g, clear);
            }
        }
        final double span = Math.max(1.0e-6, dmax - dmin);
        final boolean sliced = !Double.isNaN(o.sliceLo);
        final float stack = cam.pitch > 60.0 ? (float) (1.0 - 0.55 * (cam.pitch - 60.0) / 30.0) : 1.0f;
        final int sel = o.selected == null ? -1 : pts.bodies.indexOf(o.selected);
        final boolean focusing = o.focus != null && !o.focus.isEmpty();
        final double[] fb = o.frame;
        for (final long key : order) {
            final int i = (int) key;
            if (fb != null && (pts.x[i] < fb[0] || pts.x[i] > fb[3] || pts.y[i] < fb[1] || pts.y[i] > fb[4]
                    || pts.z[i] < fb[2] || pts.z[i] > fb[5])) {
                continue;
            }
            final float t = (float) ((depth[i] - dmin) / span);
            final int cls = pts.cls[i];
            final SurveyScene.Body body = pts.bodies.get(pts.body[i]);
            float alpha;
            float size;
            int col = pts.colour[i];
            if (cls == ORE) {
                alpha = 1.0f;
                size = (float) Math.max(o.minPoint, cam.scale * o.pointFill);
                col = Tokens.mix(col, o.ground, 0.55f * t);
            } else {
                // lava densest, aquifers faintest or they fill the whole scan

                alpha = switch (cls) {
                    case LAVA -> 0.26f;
                    case WATER -> 0.065f;
                    default -> 0.075f;
                };
                size = (float) Math.max(o.minPoint * 1.5f, pts.size[i] * cam.scale * 1.08f);
                alpha *= (1.0f - 0.45f * t) * stack;
                if (o.zoom > 4.0) {
                    alpha *= (float) Math.max(0.25, 1.0 - (o.zoom - 4.0) / 6.0);
                }
            }
            if (focusing) {
                if (!body.target.equals(o.focus)) {
                    alpha *= cls == ORE ? 0.30f : 0.38f;
                    if (cls == ORE) {
                        size *= 0.8f;
                    }
                } else if (cls == ORE) {
                    size *= 1.12f;
                }
            }
            if (sliced && (pts.y[i] < o.sliceLo || pts.y[i] > o.sliceHi + 1.0)) {
                alpha *= 0.08f;
            }
            if (sel >= 0 && pts.body[i] != sel) {
                alpha *= 0.5f;
            }

            if (o.now != Long.MIN_VALUE && body.newest != Long.MIN_VALUE) {
                final long since = o.now - (body.newest + body.echo);
                if (since < 0 && o.now >= body.newest && body.oldest == body.newest) {
                    continue;
                }
                if (since >= 0 && since < 12 && cls == ORE) {
                    final float u = since / 12.0f;
                    size *= 1.0f + 0.9f * (1.0f - u) * (1.0f - u);
                    col = Tokens.mix(Tokens.INK, col, u);
                }
            }
            if (cls != ORE && alpha < 0.02f) {
                continue;
            }
            final float h = size * 0.5f;
            final float x0 = sx[i] - h;
            final float y0 = sy[i] - h;
            if (x0 < r.x || y0 < r.y || x0 + size > r.right() || y0 + size > r.bottom()) {
                continue;
            }
            final int c = Tokens.alpha(col, alpha * ((col >>> 24) / 255.0f));
            s.tri(x0, y0, c, x0 + size, y0, c, x0 + size, y0 + size, c);
            s.tri(x0, y0, c, x0 + size, y0 + size, c, x0, y0 + size, c);
        }
        if (sel >= 0) {
            final SurveyScene.Body b = o.selected;
            cam.project(b.cx, b.cy, b.cz, p);
            final float ring = (float) Math.max(5.0, Math.max(b.maxX - b.minX, b.maxZ - b.minZ) * cam.scale * 0.7 + 4.0);
            ellipse(s, r, p[0], p[1], ring, ring, 0.0, Math.PI * 2.0, Math.max(0.8f, o.micro),
                    Tokens.alpha(Tokens.INK, 0.85f), 0);
        }
    }

    private static int sortable(float f) {
        final int bits = Float.floatToIntBits(f);
        return bits ^ ((bits >> 31) & 0x7FFFFFFF) ^ 0x80000000;
    }

    static void dig(PanelSurface s, Rect r, ScanCamera cam, SurveyScene.Surface sf, Scan o) {
        final double x = o.dig[0] + 0.5;
        final double z = o.dig[2] + 0.5;
        final double top = groundAt(sf, x, z, o.dig[1] + 1.0);
        final double[] a = new double[3];
        final double[] b = new double[3];
        cam.project(x, top, z, a);
        cam.project(x, o.dig[1] + 0.5, z, b);
        final float w = Math.max(1.0f, hairWidth(o) * 1.2f);

        final double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
        final int dashes = Math.max(1, (int) (len / 6.0));
        for (int i = 0; i < dashes; i += 2) {
            final double u0 = (double) i / dashes;
            final double u1 = Math.min(1.0, (double) (i + 1) / dashes);
            hairLine(s, r, (float) (a[0] + (b[0] - a[0]) * u0), (float) (a[1] + (b[1] - a[1]) * u0),
                    (float) (a[0] + (b[0] - a[0]) * u1), (float) (a[1] + (b[1] - a[1]) * u1), w,
                    Tokens.alpha(Tokens.INK, 0.75f));
        }
        if (r.inset(3.0f).contains(a[0], a[1])) {
            Shapes.rect(s, (float) a[0] - 3.0f, (float) a[1] - 0.5f, 6.0f, Math.max(1.0f, w), Tokens.INK);
        }
        final String t = "Y " + o.dig[1];
        final float tx = (float) b[0] + 4.0f;
        final float ty = (float) b[1] - Text.lineHeight(o.micro) * 0.5f;
        if (r.contains(tx, ty) && tx + s.textWidth(t, o.micro) < r.right() && ty + Text.lineHeight(o.micro) < r.bottom()) {
            s.text(t, tx, ty, Tokens.INK, o.micro);
        }
    }

    static void here(PanelSurface s, Rect r, ScanCamera cam, Scan o) {
        if (Double.isNaN(o.refX)) {
            return;
        }
        final double[] p = new double[3];
        cam.project(o.refX, o.refY, o.refZ, p);
        if (!r.inset(4.0f).contains(p[0], p[1])) {
            return;
        }
        if (o.toward != null) {
            final double[] q = new double[3];
            cam.project(o.toward[0], o.toward[1], o.toward[2], q);
            hairLine(s, r, (float) p[0], (float) p[1], (float) q[0], (float) q[1], hairWidth(o),
                    Tokens.alpha(Tokens.ACCENT, 0.35f));
        }
        ellipse(s, r, p[0], p[1], 3.0, 3.0, 0.0, Math.PI * 2.0, Math.max(1.0f, hairWidth(o)), Tokens.ACCENT, 0);
        final String t = "this panel";
        final float tx = (float) p[0] + 5.0f;
        final float ty = (float) p[1] - Text.lineHeight(o.micro) - 2.0f;
        final Rect box = new Rect(tx, ty, s.textWidth(t, o.micro), Text.lineHeight(o.micro));
        boolean clear = tx + box.w < r.right() && ty > r.y;
        for (final Rect q : o.labels) {
            clear &= q.intersect(box.inset(-2.0f)).isEmpty();
        }
        if (clear) {
            s.text(t, tx, ty, Tokens.alpha(Tokens.ACCENT, 0.9f), o.micro);
            o.labels.add(box);
        }
    }

    static void rigs(PanelSurface s, Rect r, ScanCamera cam, SurveyScene scene, Scan o) {
        final double[] p = new double[3];
        final float tall = Math.max(7.0f, Text.lineHeight(o.micro) * 1.3f);
        final float head = Math.max(2.5f, tall * 0.36f);
        final List<float[]> groups = new ArrayList<>();
        final List<StringBuilder> names = new ArrayList<>();
        final List<Boolean> linked = new ArrayList<>();
        int n = 0;
        for (final SurveyScene.Rig rig : scene.rigs) {
            n++;
            cam.project(rig.x + 0.5, rig.y, rig.z + 0.5, p);
            final float x = (float) p[0];
            final float y = (float) p[1];
            final int ink = rig.linked ? Tokens.INK : Tokens.INK_3;
            if (x - head < r.x || x + head > r.right() || y - tall - head < r.y || y + 2 > r.bottom()) {
                continue;
            }
            Shapes.rect(s, x - 0.5f * hairWidth(o), y - tall, hairWidth(o), tall, ink);
            Shapes.rect(s, x - head * 0.5f, y - tall - head * 0.5f, head, head, ink);

            final int number = o.rigNumbers.getOrDefault(
                    com.createstats.seismic.archive.ArchiveBlow.key(rig.x, rig.y, rig.z), n);
            boolean joined = false;
            for (int g = 0; g < groups.size(); g++) {
                final float[] at = groups.get(g);
                if (Math.abs(at[0] - x) < s.textWidth("R0 ", o.micro) + head && Math.abs(at[1] - y) < tall) {
                    names.get(g).append(' ').append('R').append(number);
                    linked.set(g, linked.get(g) || rig.linked);
                    at[0] = Math.max(at[0], x);
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                groups.add(new float[] {x, y});
                names.add(new StringBuilder("R").append(number));
                linked.add(rig.linked);
            }
        }
        for (int g = 0; g < groups.size(); g++) {
            final float[] at = groups.get(g);
            final String label = names.get(g).toString();
            final float w = s.textWidth(label, o.micro);
            final float lx = at[0] + head * 0.5f + 2.0f;
            final float ly = at[1] - tall - Text.lineHeight(o.micro) * 0.5f;
            if (lx + w <= r.right() && ly >= r.y) {
                s.text(label, lx, ly, Tokens.alpha(linked.get(g) ? Tokens.INK : Tokens.INK_3, 0.9f), o.micro);
                o.labels.add(new Rect(at[0] - head, ly, lx + w - at[0] + head, at[1] - ly));
            }
        }
    }

    static SurveyFinds.Find nearestInBand(SurveyScene.Layer l, Scan o) {
        SurveyScene.Body best = null;
        for (final SurveyScene.Body b : l.bodies) {
            if (b.maxY >= o.sliceLo && b.minY <= o.sliceHi && (best == null || b.nearDist < best.nearDist)) {
                best = b;
            }
        }
        return best == null ? null : inBand(best, o.sliceLo, o.sliceHi, o.refX, o.refY, o.refZ);
    }

    static SurveyFinds.Find inBand(SurveyScene.Body b, double lo, double hi, double refX, double refY, double refZ) {
        int bx = b.nearX;
        int by = b.nearY;
        int bz = b.nearZ;
        double best = Double.MAX_VALUE;
        for (final int c : b.cells) {
            for (int k = 0; k < b.grain; k++) {
                final int y = b.cellY(c) + k;
                if (y < lo || y > hi) {
                    continue;
                }
                final int x = b.cellX(c) + b.grain / 2;
                final int z = b.cellZ(c) + b.grain / 2;
                final double dx = x + 0.5 - refX;
                final double dy = y + 0.5 - refY;
                final double dz = z + 0.5 - refZ;
                final double d = dx * dx + dy * dy + dz * dz;
                if (d < best) {
                    best = d;
                    bx = x;
                    by = y;
                    bz = z;
                }
            }
        }
        return SurveyFinds.place(b, bx, by, bz, refX, refZ);
    }

    record Callout(String target, SurveyFinds.Find find, float px, float py) {
    }

    static void callouts(PanelSurface s, Rect r, ScanCamera cam, SurveyScene scene, Scan o) {
        if (Double.isNaN(o.refX)) {
            return;
        }
        final List<SurveyScene.Layer> layers = new ArrayList<>(scene.layers);
        layers.sort((a, b) -> SeismicCatalog.compare(b.target, a.target));
        final List<Callout> picks = new ArrayList<>();
        final double[] p = new double[3];
        if (o.selected != null && !o.hidden.contains(o.selected.target)) {
            final SurveyScene.Body b = o.selected;
            final SurveyFinds.Find f = SurveyFinds.place(b, b.nearX, b.nearY, b.nearZ, o.refX, o.refZ);
            cam.project(b.nearX + 0.5, b.nearY + 0.5, b.nearZ + 0.5, p);
            if (r.contains(p[0], p[1])) {
                picks.add(new Callout(b.target, f, (float) p[0], (float) p[1]));
            }
        }
        for (final SurveyScene.Layer l : layers) {
            if (picks.size() >= o.maxCallouts || o.hidden.contains(l.target) || classOf(l.target, 0) == VOID) {
                continue;
            }
            if (!o.focus.isEmpty() && !o.focus.equals(l.target) && picks.size() >= Math.max(1, o.maxCallouts - 2)) {
                continue;
            }
            if (!picks.isEmpty() && picks.get(0).target().equals(l.target) && o.selected != null) {
                continue;
            }
            final SurveyFinds.Find f = Double.isNaN(o.sliceLo) ? SurveyFinds.nearest(scene, l.target, -1, o.refX, o.refZ)
                    : nearestInBand(l, o);
            if (f == null) {
                continue;
            }
            cam.project(f.x() + 0.5, f.y() + 0.5, f.z() + 0.5, p);
            if (!r.contains(p[0], p[1])) {
                continue;
            }
            picks.add(new Callout(l.target, f, (float) p[0], (float) p[1]));
        }
        if (picks.isEmpty()) {
            return;
        }
        final float lh = Text.lineHeight(o.label);
        final float h = lh + Text.lineHeight(o.micro) + 5.0f;
        if (o.rail > 0.0f) {
            picks.sort((a, b) -> Float.compare(a.py(), b.py()));
            final float gap = 4.0f;
            final float total = picks.size() * h + (picks.size() - 1) * gap;
            float y = Math.max(r.y + 2.0f, Math.min(r.bottom() - total - 2.0f, picks.get(0).py() - h * 0.5f));
            final float x = r.right() - o.rail + 6.0f;
            final float w = o.rail - 8.0f;
            for (final Callout c : picks) {
                label(s, r, c, new Rect(x, y, w, h), o);
                y += h + gap;
            }
            return;
        }

        final List<Rect> placed = new ArrayList<>(o.labels);
        for (final Callout c : picks) {
            final String name = SeismicCatalog.veinLabel(c.target(), c.find().body().kind);
            final float w = Math.max(s.textWidth(name, o.label), s.textWidth(c.find().where(), o.micro)) + 12.0f
                    + Math.max(3.0f, lh * 0.55f);
            Rect box = null;
            for (final float[] off : new float[][] {{14, -h - 10}, {14, 10}, {-w - 14, -h - 10}, {-w - 14, 10},
                    {22, -h * 0.5f}, {-w - 22, -h * 0.5f}}) {
                final Rect cand = new Rect(c.px() + off[0], c.py() + off[1], w, h);
                if (cand.x < r.x + 2 || cand.y < r.y + 2 || cand.right() > r.right() - 2 || cand.bottom() > r.bottom() - 2) {
                    continue;
                }
                boolean clear = true;
                for (final Rect q : placed) {
                    if (!q.intersect(cand.inset(-3.0f)).isEmpty()) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    box = cand;
                    break;
                }
            }
            if (box != null) {
                placed.add(box);
                label(s, r, c, box, o);
            }
        }
    }

    private static void label(PanelSurface s, Rect r, Callout c, Rect box, Scan o) {
        final int col = colour(c.target(), c.find().body().kind);
        final float lh = Text.lineHeight(o.label);
        final float ax = box.x > c.px() ? box.x : box.right();
        final float ay = box.y + box.h * 0.5f;
        final float hair = hairWidth(o);
        if (o.rail > 0.0f) {
            final float elbow = Math.max(c.px() + 6.0f, ax - 10.0f);
            hairLine(s, r, c.px(), c.py(), elbow, ay, hair, Tokens.alpha(Tokens.INK_2, 0.6f));
            hairLine(s, r, elbow, ay, ax, ay, hair, Tokens.alpha(Tokens.INK_2, 0.6f));
        } else {
            hairLine(s, r, c.px(), c.py(), ax, ay, hair, Tokens.alpha(Tokens.INK_2, 0.6f));
        }
        Shapes.roundedRect(s, box, 2.0f, Tokens.alpha(Tokens.CANVAS, 0.88f));

        final float sw = Math.max(3.0f, lh * 0.55f);
        Shapes.roundedRect(s, new Rect(box.x + 3.0f, box.y + 2.5f + (lh - sw) * 0.5f, sw, sw), 1.0f, col);
        final String name = SeismicCatalog.veinLabel(c.target(), c.find().body().kind);
        final float tx = box.x + 6.0f + sw;
        final float tw = box.right() - tx - 3.0f;
        s.text(Text.truncate(s, name, tw, o.label), tx, box.y + 2.5f, Tokens.INK, o.label);
        s.text(Text.truncate(s, c.find().where(), tw, o.micro), tx, box.y + 3.0f + lh, Tokens.INK_2, o.micro);
        ellipse(s, r, c.px(), c.py(), 2.6, 2.6, 0.0, Math.PI * 2.0, hair, Tokens.alpha(Tokens.INK, 0.9f), 0);
    }

    static void compass(PanelSurface s, Rect r, ScanCamera cam, Scan o) {
        final double[] d = cam.groundDirection(0.0);
        final float rad = Math.max(6.0f, Text.lineHeight(o.micro) * 1.15f);
        final float cx = r.right() - rad - 4.0f;
        final float cy = r.y + rad + 4.0f + Text.lineHeight(o.micro);
        ellipse(s, r, cx, cy, rad, rad, 0.0, Math.PI * 2.0, hairWidth(o), Tokens.alpha(Tokens.LINE_HI, 0.9f), 0);
        if (d[0] == 0.0 && d[1] == 0.0) {
            s.textCenterV("N", cx, cy, Tokens.INK_2, o.micro);
            return;
        }
        final float tx = cx + (float) d[0] * rad * 0.8f;
        final float ty = cy + (float) d[1] * rad * 0.8f;
        final float bx = cx - (float) d[0] * rad * 0.55f;
        final float by = cy - (float) d[1] * rad * 0.55f;
        final float nx = -(float) d[1] * rad * 0.3f;
        final float ny = (float) d[0] * rad * 0.3f;
        s.tri(tx, ty, Tokens.INK, bx + nx, by + ny, Tokens.INK, cx, cy, Tokens.INK);
        s.tri(tx, ty, Tokens.INK_3, cx, cy, Tokens.INK_3, bx - nx, by - ny, Tokens.INK_3);
        final float off = rad + Text.lineHeight(o.micro) * 0.7f;
        final float lx = cx + (float) d[0] * off;
        final float ly = cy + (float) d[1] * off;
        if (r.contains(lx - 3, ly - 4) && r.contains(lx + 3, ly + 4)) {
            s.textCenterV("N", lx, ly, Tokens.INK_2, o.micro);
        }
    }

    private static void hair(PanelSurface s, Rect r, ScanCamera cam, double x0, double y0, double z0, double x1,
                             double y1, double z1, float width, int colour) {
        final double[] a = new double[3];
        final double[] b = new double[3];
        cam.project(x0, y0, z0, a);
        cam.project(x1, y1, z1, b);
        hairLine(s, r, (float) a[0], (float) a[1], (float) b[0], (float) b[1], width, colour);
    }

    static void hairLine(PanelSurface s, Rect r, float x0, float y0, float x1, float y1, float width, int colour) {
        final float[] c = clip(r, width, x0, y0, x1, y1);
        if (c == null) {
            return;
        }
        final float dx = c[2] - c[0];
        final float dy = c[3] - c[1];
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) {
            return;
        }
        final float nx = -dy / len * width * 0.5f;
        final float ny = dx / len * width * 0.5f;
        s.tri(c[0] + nx, c[1] + ny, colour, c[2] + nx, c[3] + ny, colour, c[2] - nx, c[3] - ny, colour);
        s.tri(c[0] + nx, c[1] + ny, colour, c[2] - nx, c[3] - ny, colour, c[0] - nx, c[1] - ny, colour);
    }

    static void clippedLine(PanelSurface s, Rect r, float x0, float y0, float x1, float y1, float width, int colour) {
        final float[] c = clip(r, width + s.feather(), x0, y0, x1, y1);
        if (c != null) {
            Shapes.line(s, c[0], c[1], c[2], c[3], width, colour);
        }
    }

    private static float[] clip(Rect r, float margin, float x0, float y0, float x1, float y1) {
        final float lx = r.x + margin;
        final float ly = r.y + margin;
        final float hx = r.right() - margin;
        final float hy = r.bottom() - margin;
        final float dx = x1 - x0;
        final float dy = y1 - y0;
        final float[] pp = {-dx, dx, -dy, dy};
        final float[] qq = {x0 - lx, hx - x0, y0 - ly, hy - y0};
        float t0 = 0.0f;
        float t1 = 1.0f;
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0.0f) {
                if (qq[i] < 0.0f) {
                    return null;
                }
                continue;
            }
            final float t = qq[i] / pp[i];
            if (pp[i] < 0.0f) {
                if (t > t1) {
                    return null;
                }
                t0 = Math.max(t0, t);
            } else {
                if (t < t0) {
                    return null;
                }
                t1 = Math.min(t1, t);
            }
        }
        return new float[] {x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
    }

    static String n(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }
}
