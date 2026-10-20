package com.createstats.holomk3.render;

import org.joml.Vector3f;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.holomk3.scene.HoloScene;

@OnlyIn(Dist.CLIENT)
public final class HoloPainter {
    private HoloPainter() {
    }

    public static void paint(HoloSink s, HoloScene scene, HoloMk3Settings cfg, float seconds) {
        final float glow = cfg.getGlow();
        final float boot = scene.boot;

        final float dataIn = smoothstep(0.55f, 1.0f, boot);
        if (boot < 1.0f) {
            calibration(s, scene, seconds, glow * (1.0f - dataIn));
        }
        if (cfg.isGraticule()) {
            graticule(s, scene, glow * Math.max(dataIn, 0.35f * boot));
        }
        if (dataIn <= 0.01f) {
            return;
        }

        if (scene.empty) {
            noiseFloor(s, scene, seconds, glow * dataIn);
            return;
        }

        final HoloMk3Settings.Style style = cfg.getStyle();

        for (int i = scene.lanes.size() - 1; i >= 0; i--) {
            trace(s, scene, scene.lanes.get(i), i, style, cfg, seconds, glow * dataIn);
        }
        if (cfg.isFloorShadows()) {
            shadows(s, scene, glow * dataIn);
        }
        if (scene.sweep >= 0.0f) {
            sweep(s, scene, style, glow * dataIn);
        }
        if (cfg.isCallouts()) {
            callouts(s, scene, glow * dataIn);
        }
    }

    private static void graticule(HoloSink s, HoloScene scene, float glow) {
        final float w = scene.w;
        final float h = scene.h;
        final float d = scene.d;
        final float hw = strokeFor(s, scene);
        final int cage = HoloDraw.fade(HoloPalette.STRUCTURE, 0.55f * glow);
        HoloDraw.cage(s, w, h, d, hw * 0.55f, cage);

        final int rule = HoloDraw.fade(HoloPalette.STRUCTURE_DIM, 0.34f * glow);
        final float gh = hw * 0.34f;
        final float x0 = -w * 0.5f;
        final float x1 = w * 0.5f;
        final float z0 = -d * 0.5f;
        final float z1 = d * 0.5f;

        for (int i = 1; i < scene.gridRows; i++) {
            final float y = h * i / scene.gridRows;
            HoloDraw.beam(s, x0, y, z0, x1, y, z0, gh, rule);
        }
        for (int i = 1; i < scene.gridCols; i++) {
            final float x = x0 + w * i / scene.gridCols;
            HoloDraw.beam(s, x, 0.0f, z0, x, h, z0, gh, rule);
        }

        for (int i = 1; i < scene.gridCols; i++) {
            final float x = x0 + w * i / scene.gridCols;
            HoloDraw.beam(s, x, 0.0f, z0, x, 0.0f, z1, gh, rule);
        }

        final int tickColor = HoloDraw.fade(HoloPalette.STRUCTURE, 0.7f * glow);
        final float len = Math.min(w, h) * 0.045f;
        final Vector3f down = new Vector3f(0.0f, -1.0f, 0.0f);
        final Vector3f out = new Vector3f(-1.0f, 0.0f, 0.0f);
        for (int i = 0; i <= scene.gridCols; i++) {
            final float x = x0 + w * i / scene.gridCols;
            HoloDraw.tick(s, x, 0.0f, z1, down, len * (i % 2 == 0 ? 1.5f : 1.0f), gh, tickColor);
        }
        for (int i = 0; i <= scene.gridRows; i++) {
            final float y = h * i / scene.gridRows;
            HoloDraw.tick(s, x0, y, z1, out, len * (i % 2 == 0 ? 1.5f : 1.0f), gh, tickColor);
        }

        final float micro = textScale(scene, MICRO);
        final int label = HoloDraw.fade(HoloPalette.LABEL, 0.8f * glow);

        if (!scene.axisValueHi.isEmpty()) {
            s.textRight(scene.axisValueHi, x0 - len * 2.0f, h - textHeight(scene, MICRO), z1,
                    label, micro);
            s.textRight(scene.axisValueLo, x0 - len * 2.0f, textHeight(scene, MICRO) * 0.2f, z1,
                    label, micro);
        }
        if (!scene.axisTimeSpan.isEmpty()) {
            s.text("-" + scene.axisTimeSpan, x0, -len * 3.0f, z1, label, micro);
            s.textRight("now", x1, -len * 3.0f, z1, label, micro);
        }
        if (!scene.axisDepth.isEmpty()) {
            s.text(scene.axisDepth, x1 + len, 0.0f, z0, HoloDraw.fade(label, 0.7f), micro);
        }
    }

    private static void trace(HoloSink s, HoloScene scene, HoloScene.Lane lane, int index,
                              HoloMk3Settings.Style style, HoloMk3Settings cfg,
                              float seconds, float glow) {
        if (lane.count < 1) {
            return;
        }
        final float[] xyz = project(lane);

        final float weight = lane.focused ? 1.0f : 0.62f;
        final float intensity = glow * weight * (lane.stale ? 0.45f : 1.0f);
        final int color = lane.stale
                ? HoloDraw.mix(lane.color, HoloPalette.STRUCTURE_DIM, 0.55f)
                : lane.color;
        final float hw = strokeFor(s, scene) * (lane.focused ? 1.0f : 0.8f);

        switch (style) {
            case PARTICLE -> particle(s, xyz, lane, hw, color, intensity, seconds, index);
            case SLICE -> slice(s, scene, xyz, lane, hw, color, intensity);
            case SOLID -> solid(s, scene, xyz, lane, hw, color, intensity);
            case PRISM -> prism(s, scene, xyz, lane, hw, color, intensity);
            default -> vector(s, xyz, lane, hw, color, intensity);
        }

        if (lane.focused && lane.count > 0) {
            final float[] head = new float[]{xyz[(lane.count - 1) * 3], xyz[(lane.count - 1) * 3 + 1],
                    xyz[(lane.count - 1) * 3 + 2]};
            HoloDraw.star(s, head[0], head[1], head[2], hw * 2.4f,
                    HoloDraw.fade(HoloPalette.MARK, intensity));
        }
    }

    private static void vector(HoloSink s, float[] xyz, HoloScene.Lane lane, float hw,
                               int color, float intensity) {
        HoloDraw.polyline(s, xyz, lane.count, hw, color, 0.16f * intensity, 1.0f * intensity);
    }

    private static void particle(HoloSink s, float[] xyz, HoloScene.Lane lane, float hw,
                                 int color, float intensity, float seconds, int index) {
        final float wobble = hw * 0.9f;
        final int seed = index * 7919;
        for (int i = 0; i < lane.count; i++) {
            final float t = lane.count <= 1 ? 1.0f : i / (float) (lane.count - 1);
            final float a = intensity * (0.14f + 0.86f * t);
            final float phase = seconds * 1.7f + HoloDraw.hash(i, seed) * 6.283f;
            final float jx = (float) Math.sin(phase) * wobble;
            final float jy = (float) Math.cos(phase * 1.31f) * wobble;
            final float jz = (float) Math.sin(phase * 0.77f) * wobble;
            HoloDraw.point(s, xyz[i * 3] + jx, xyz[i * 3 + 1] + jy, xyz[i * 3 + 2] + jz,
                    hw * (1.1f + 0.7f * t), HoloDraw.fade(color, a));
        }
    }

    private static void slice(HoloSink s, HoloScene scene, float[] xyz, HoloScene.Lane lane,
                              float hw, int color, float intensity) {
        final int slices = 24;
        final float step = lane.planeH / slices;
        final float[] snapped = new float[lane.count * 3];
        for (int i = 0; i < lane.count; i++) {
            snapped[i * 3] = xyz[i * 3];
            snapped[i * 3 + 1] = Math.round(xyz[i * 3 + 1] / step) * step;
            snapped[i * 3 + 2] = xyz[i * 3 + 2];
        }
        HoloDraw.polyline(s, snapped, lane.count, hw, color, 0.2f * intensity, intensity);

        if (!lane.focused) {
            return;
        }
        final int band = HoloDraw.fade(HoloPalette.STRUCTURE_DIM, 0.09f * intensity);
        for (int i = 1; i < slices; i += 2) {
            HoloDraw.planeY(s, i * step, scene.w * 0.98f, scene.d * 0.98f, band,
                    HoloDraw.alpha(band, 0.0f));
        }
    }

    private static void solid(HoloSink s, HoloScene scene, float[] xyz, HoloScene.Lane lane,
                              float hw, int color, float intensity) {
        final int skin = HoloDraw.alpha(HoloDraw.mix(color, 0xFF000000, 0.82f),
                0.60f * Math.min(intensity, 1.0f));
        final float floorY = 0.0f;
        for (int i = 0; i < lane.count - 1; i++) {
            final float ax = xyz[i * 3];
            final float ay = xyz[i * 3 + 1];
            final float az = xyz[i * 3 + 2];
            final float bx = xyz[(i + 1) * 3];
            final float by = xyz[(i + 1) * 3 + 1];
            final float bz = xyz[(i + 1) * 3 + 2];
            s.solidQuad(ax, floorY, az, skin, bx, floorY, bz, skin, bx, by, bz, skin, ax, ay, az, skin);
        }
        HoloDraw.polyline(s, xyz, lane.count, hw * 1.15f, HoloPalette.lift(color, 0.2f),
                0.45f * intensity, intensity);
    }

    private static void prism(HoloSink s, HoloScene scene, float[] xyz, HoloScene.Lane lane,
                              float hw, int color, float intensity) {
        final float depth = scene.d <= 1.0e-4f ? 0.0f : (lane.z + scene.d * 0.5f) / scene.d;
        final float split = hw * (0.7f + 2.6f * (1.0f - depth));
        final Vector3f right = s.right();
        final int[] channels = {color & 0xFFFF0000, color & 0xFF00FF00, color & 0xFF0000FF};
        for (int c = 0; c < 3; c++) {
            final float off = (c - 1) * split;
            final float[] shifted = new float[lane.count * 3];
            for (int i = 0; i < lane.count; i++) {
                shifted[i * 3] = xyz[i * 3] + right.x * off;
                shifted[i * 3 + 1] = xyz[i * 3 + 1] + right.y * off;
                shifted[i * 3 + 2] = xyz[i * 3 + 2] + right.z * off;
            }
            HoloDraw.polyline(s, shifted, lane.count, hw * 0.85f, channels[c],
                    0.2f * intensity, intensity);
        }

        if (s.feather() <= 0.2f) {
            return;
        }
        final int grains = Math.min(90, lane.count * 2);
        for (int g = 0; g < grains; g++) {
            final float t = HoloDraw.hash(g, 17);
            final int i = Math.min(lane.count - 1, (int) (t * lane.count));
            final float sx = (HoloDraw.hash(g, 31) - 0.5f) * hw * 5.0f;
            final float sy = (HoloDraw.hash(g, 53) - 0.5f) * hw * 5.0f;
            HoloDraw.point(s, xyz[i * 3] + right.x * sx, xyz[i * 3 + 1] + sy,
                    xyz[i * 3 + 2] + right.z * sx, hw * 0.5f,
                    HoloDraw.fade(HoloPalette.EMPHASIS, 0.35f * intensity));
        }
    }

    private static void shadows(HoloSink s, HoloScene scene, float glow) {
        final float hw = strokeFor(s, scene) * 0.5f;
        final float backZ = -scene.d * 0.5f + hw;
        final float floorY = hw;
        for (final HoloScene.Lane lane : scene.lanes) {
            if (lane.count < 2) {
                continue;
            }
            final float[] xyz = project(lane);
            final int dim = HoloDraw.fade(lane.color, 0.20f * glow * (lane.focused ? 1.3f : 1.0f));
            final float[] onBack = new float[lane.count * 3];
            final float[] onFloor = new float[lane.count * 3];
            for (int i = 0; i < lane.count; i++) {
                onBack[i * 3] = xyz[i * 3];
                onBack[i * 3 + 1] = xyz[i * 3 + 1];
                onBack[i * 3 + 2] = backZ;
                onFloor[i * 3] = xyz[i * 3];
                onFloor[i * 3 + 1] = floorY;
                onFloor[i * 3 + 2] = xyz[i * 3 + 2];
            }
            HoloDraw.polyline(s, onBack, lane.count, hw, dim, 0.25f, 1.0f);
            HoloDraw.polyline(s, onFloor, lane.count, hw * 0.8f, dim, 0.15f, 0.8f);
        }
    }

    private static void sweep(HoloSink s, HoloScene scene, HoloMk3Settings.Style style, float glow) {
        final int core = HoloDraw.fade(HoloPalette.EMITTER, 0.15f * glow);
        final int edge = HoloDraw.alpha(core, 0.0f);
        if (style == HoloMk3Settings.Style.SLICE) {
            HoloDraw.planeY(s, scene.h * scene.sweep, scene.w, scene.d, core, edge);
            return;
        }

        final float x = -scene.w * 0.5f + scene.w * scene.sweep;
        final float back = -scene.d * 0.5f;
        final float hw = strokeFor(s, scene) * 0.5f;
        HoloDraw.beam(s, x, 0.0f, back, x, scene.h, back, hw, core, edge);
        HoloDraw.beam(s, x, 0.0f, back, x, 0.0f, scene.d * 0.5f, hw, core, edge);
    }

    private static void callouts(HoloSink s, HoloScene scene, float glow) {
        final HoloScene.Lane lane = scene.focus;
        if (lane == null || lane.count < 1) {
            return;
        }
        final float w = scene.w;
        final float h = scene.h;
        final float front = scene.d * 0.5f;
        final float hw = strokeFor(s, scene);
        final float micro = textScale(scene, MICRO);
        final float hero = textScale(scene, HERO);

        final float[] xyz = project(lane);
        final float hx = xyz[(lane.count - 1) * 3];
        final float hy = xyz[(lane.count - 1) * 3 + 1];
        final float hz = xyz[(lane.count - 1) * 3 + 2];

        final float heroX = w * 0.5f;
        final float heroY = h + textHeight(scene, HERO) * 1.05f;
        final int heroInk = HoloDraw.fade(HoloPalette.EMPHASIS, glow);
        s.textRight(lane.valueText, heroX, heroY, front, heroInk, hero);
        s.textRight(lane.label, heroX, heroY - textHeight(scene, HERO) * 0.98f, front,
                HoloDraw.fade(HoloPalette.LABEL, 0.75f * glow), micro);
        final int leader = HoloDraw.fade(HoloPalette.MARK, 0.5f * glow);
        HoloDraw.beam(s, hx, hy, hz, heroX - w * 0.05f, h + textHeight(scene, HERO) * 0.1f, front,
                hw * 0.4f, HoloDraw.alpha(leader, 0.0f), leader);

        if (lane.stale) {
            s.textRight("STALE", heroX, heroY - textHeight(scene, HERO) * 1.9f, front,
                    HoloDraw.fade(HoloPalette.CAUTION, glow), micro);
        }

        final float span = lane.hi - lane.lo;
        if (span > 1.0e-6f) {
            reference(s, scene, lane, lane.max, "max", HoloPalette.STRUCTURE, glow, hw, micro);
            reference(s, scene, lane, lane.mean, "mean", HoloPalette.STRUCTURE_DIM, glow, hw, micro);
            reference(s, scene, lane, lane.min, "min", HoloPalette.STRUCTURE, glow, hw, micro);
        }

        for (final HoloScene.Lane other : scene.lanes) {
            if (other.focused || other.count < 1) {
                continue;
            }
            final float[] pts = project(other);
            final int last = (other.count - 1) * 3;
            s.textRight(other.label, pts[last] - hw * 3.0f,
                    pts[last + 1] + textHeight(scene, MICRO) * 0.35f, pts[last + 2],
                    HoloDraw.fade(other.color, 0.75f * glow), micro);
        }

        if (!scene.subtitle.isEmpty()) {
            s.text(scene.subtitle, -w * 0.5f, heroY, front,
                    HoloDraw.fade(HoloPalette.LABEL, 0.55f * glow), micro);
        }
    }

    private static void reference(HoloSink s, HoloScene scene, HoloScene.Lane lane, float value,
                                  String name, int color, float glow, float hw, float scale) {
        final float span = lane.hi - lane.lo;
        final float t = (value - lane.lo) / span;
        if (t < -0.02f || t > 1.02f) {
            return;
        }
        final float y = lane.planeH * Math.max(0.0f, Math.min(1.0f, t));
        final float x0 = -lane.planeW * 0.5f;
        final float x1 = lane.planeW * 0.5f;
        final int line = HoloDraw.fade(color, 0.3f * glow);

        final int dashes = 11;
        for (int i = 0; i < dashes; i += 2) {
            final float a = x0 + (x1 - x0) * i / dashes;
            final float b = x0 + (x1 - x0) * (i + 1) / dashes;
            HoloDraw.beam(s, laneX(lane, a), y, laneZ(lane, a),
                    laneX(lane, b), y, laneZ(lane, b), hw * 0.3f, line);
        }
        final float labelX = x1 + lane.planeW * 0.02f;
        s.text(name, laneX(lane, labelX), y, laneZ(lane, labelX),
                HoloDraw.fade(color, 0.6f * glow), scale);
    }

    private static float laneX(HoloScene.Lane lane, float lx) {
        return lane.originX + lx * (float) Math.cos(Math.toRadians(lane.yawDeg));
    }

    private static float laneZ(HoloScene.Lane lane, float lx) {
        return lane.originZ - lx * (float) Math.sin(Math.toRadians(lane.yawDeg));
    }

    private static void noiseFloor(HoloSink s, HoloScene scene, float seconds, float glow) {
        final float w = scene.w;
        final float h = scene.h;
        final float hw = strokeFor(s, scene) * 0.7f;
        final int n = 48;
        final float[] xyz = new float[n * 3];
        final int frame = (int) (seconds * 6.0f);
        for (int i = 0; i < n; i++) {
            final float noise = HoloDraw.hash(i, frame) - 0.5f;
            xyz[i * 3] = -w * 0.5f + w * i / (n - 1.0f);
            xyz[i * 3 + 1] = h * 0.12f + noise * h * 0.035f;
            xyz[i * 3 + 2] = 0.0f;
        }
        HoloDraw.polyline(s, xyz, n, hw, HoloDraw.fade(HoloPalette.STRUCTURE_DIM, 0.7f * glow),
                0.8f, 0.8f);
        final float body = textScale(scene, 0.075f);
        final float micro = textScale(scene, MICRO);
        s.textCenter(scene.title, 0.0f, h * 0.55f, 0.0f,
                HoloDraw.fade(HoloPalette.CAUTION, 0.9f * glow), body);
        s.textCenter(scene.subtitle, 0.0f, h * 0.55f - h * 0.06f, 0.0f,
                HoloDraw.fade(HoloPalette.LABEL, 0.55f * glow), micro);
    }

    private static void calibration(HoloSink s, HoloScene scene, float seconds, float glow) {
        if (glow <= 0.01f) {
            return;
        }
        final float boot = scene.boot;
        final float w = scene.w;
        final float h = scene.h;
        final float d = scene.d;
        final float hw = strokeFor(s, scene);
        final int ink = HoloDraw.fade(HoloPalette.EMITTER, glow);

        final float climbed = Math.min(1.0f, boot / 0.4f);
        final float y = h * climbed;
        HoloDraw.rect(s, -w * 0.5f, 0.0f, -d * 0.5f, w * 0.5f, d * 0.5f, hw * 0.6f, ink);
        if (climbed > 0.02f) {
            HoloDraw.rect(s, -w * 0.5f, y, -d * 0.5f, w * 0.5f, d * 0.5f, hw * 0.6f,
                    HoloDraw.fade(ink, 0.9f));
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    HoloDraw.beam(s, sx * w * 0.5f, 0.0f, sz * d * 0.5f,
                            sx * w * 0.5f, y, sz * d * 0.5f, hw * 0.5f, ink,
                            HoloDraw.fade(ink, 0.4f));
                }
            }
        }

        if (boot > 0.32f && boot < 0.80f) {
            final float t = Math.min(1.0f, Math.min((boot - 0.32f) / 0.22f, (0.80f - boot) / 0.10f));
            final float r = Math.min(w, Math.min(h, d)) * 0.22f * t;
            final float spin = seconds * 42.0f;
            referenceCube(s, 0.0f, h * 0.5f, 0.0f, r, spin, hw * 0.7f,
                    HoloDraw.fade(HoloPalette.EMPHASIS, glow * t));
            final float cross = Math.min(w, d) * 0.14f;
            HoloDraw.beam(s, -cross, h * 0.5f, 0.0f, cross, h * 0.5f, 0.0f, hw * 0.4f,
                    HoloDraw.fade(HoloPalette.MARK, glow * t));
            HoloDraw.beam(s, 0.0f, h * 0.5f - cross, 0.0f, 0.0f, h * 0.5f + cross, 0.0f, hw * 0.4f,
                    HoloDraw.fade(HoloPalette.MARK, glow * t));
        }

        final float micro = textScale(scene, MICRO * 1.15f);
        final String word = boot < 0.72f ? "CALIBRATING" : "LOCKED";
        final int wordInk = boot < 0.72f ? HoloPalette.CAUTION : HoloPalette.GOOD;
        s.textCenter(word, 0.0f, h * 0.5f - Math.min(w, d) * 0.22f, 0.0f,
                HoloDraw.fade(wordInk, glow), micro);
    }

    private static void referenceCube(HoloSink s, float cx, float cy, float cz, float r,
                                      float degrees, float hw, int color) {
        final double a = Math.toRadians(degrees);
        final float cos = (float) Math.cos(a);
        final float sin = (float) Math.sin(a);
        final float[][] corner = new float[8][3];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    final float lx = sx * r;
                    final float lz = sz * r;
                    corner[i][0] = cx + lx * cos + lz * sin;
                    corner[i][1] = cy + sy * r;
                    corner[i][2] = cz - lx * sin + lz * cos;
                    i++;
                }
            }
        }

        for (int a1 = 0; a1 < 8; a1++) {
            for (int b = a1 + 1; b < 8; b++) {
                if (Integer.bitCount(a1 ^ b) != 1) {
                    continue;
                }
                HoloDraw.beam(s, corner[a1][0], corner[a1][1], corner[a1][2],
                        corner[b][0], corner[b][1], corner[b][2], hw, color);
            }
        }
    }

    private static float[] project(HoloScene.Lane lane) {
        final double a = Math.toRadians(lane.yawDeg);
        final float cos = (float) Math.cos(a);
        final float sin = (float) Math.sin(a);
        final float[] out = new float[lane.count * 3];
        for (int i = 0; i < lane.count; i++) {
            final float lx = lane.xy[i * 2];
            out[i * 3] = lane.originX + lx * cos;
            out[i * 3 + 1] = lane.xy[i * 2 + 1];
            out[i * 3 + 2] = lane.originZ - lx * sin;
        }
        return out;
    }

    private static float strokeFor(HoloSink s, HoloScene scene) {
        return Math.max(scene.h * 0.011f, s.pixel() * 0.75f);
    }

    private static final float MICRO = 0.046f;
    private static final float HERO = 0.145f;

    private static float textScale(HoloScene scene, float fraction) {
        return scene.h * fraction / 9.0f;
    }

    private static float textHeight(HoloScene scene, float fraction) {
        return scene.h * fraction;
    }

    private static float smoothstep(float lo, float hi, float x) {
        if (hi - lo < 1.0e-6f) {
            return x >= hi ? 1.0f : 0.0f;
        }
        final float t = Math.max(0.0f, Math.min(1.0f, (x - lo) / (hi - lo)));
        return t * t * (3.0f - 2.0f * t);
    }
}
