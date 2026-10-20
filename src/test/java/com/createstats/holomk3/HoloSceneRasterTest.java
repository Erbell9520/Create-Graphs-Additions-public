package com.createstats.holomk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.createstats.blockentity.HolographicProjectorBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.holomk3.render.HoloEmitter;
import com.createstats.holomk3.render.HoloPainter;
import com.createstats.holomk3.render.HoloSink;
import com.createstats.holomk3.scene.HoloScene;
import com.createstats.holomk3.scene.SceneBuilder;
import com.createstats.util.PreviewImages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoloSceneRasterTest {
    private static final File OUT = new File("fixture_preview");
    private static final int TILE_W = 470;
    private static final int TILE_H = 420;
    private static final int PAD = 12;
    private static final int LABEL = 20;
    private static final int GROUND = 0x0B0D11;

    @Test
    void styleSheet() throws IOException {
        final HoloMk3Settings cfg = settings();
        final List<Tile> tiles = new ArrayList<>();
        for (final HoloMk3Settings.Style style : HoloMk3Settings.Style.values()) {
            cfg.setStyle(style);
            tiles.add(new Tile(style.getDisplayName(), draw(cfg, channels(3), -26.0f, 14.0f, 6.1f,
                    1.0f, 3.0f)));
        }
        write("holo_mk3_styles.png", tiles, 5);
    }

    @Test
    void parallaxSheet() throws IOException {
        final HoloMk3Settings cfg = settings();
        final List<DataChannel> data = channels(4);
        final List<Tile> tiles = new ArrayList<>();
        final float[][] views = {
                {-58.0f, 10.0f, 6.2f},
                {-4.0f, 8.0f, 6.0f},
                {36.0f, 10.0f, 6.2f},
                {12.0f, 46.0f, 6.6f},
        };
        final String[] names = {"from the left", "head on", "from the right", "from above"};
        for (int i = 0; i < views.length; i++) {
            tiles.add(new Tile(names[i], draw(cfg, data, views[i][0], views[i][1], views[i][2],
                    1.0f, 3.0f)));
        }
        write("holo_mk3_parallax.png", tiles, 4);
    }

    @Test
    void stateSheet() throws IOException {
        final List<Tile> tiles = new ArrayList<>();

        final HoloMk3Settings booting = settings();
        tiles.add(new Tile("calibrating", draw(booting, channels(3), -26.0f, 14.0f, 6.1f, 0.5f, 3.0f)));
        tiles.add(new Tile("locked", draw(booting, channels(3), -26.0f, 14.0f, 6.1f, 0.88f, 3.0f)));

        tiles.add(new Tile("no signal", draw(settings(), List.of(), -26.0f, 14.0f, 6.1f, 1.0f, 3.0f)));

        final HoloMk3Settings array = settings();
        array.setLayout(HoloMk3Settings.Layout.ARRAY);
        tiles.add(new Tile("array layout", draw(array, channels(4), -22.0f, 16.0f, 6.6f, 1.0f, 3.0f)));

        final HoloMk3Settings single = settings();
        single.setLaneLimit(1);
        tiles.add(new Tile("one channel", draw(single, channels(1), -26.0f, 12.0f, 5.5f, 1.0f, 3.0f)));

        final HoloMk3Settings flat = settings();
        flat.setDepthCue(0.0f);
        tiles.add(new Tile("depth cue off", draw(flat, channels(4), -46.0f, 12.0f, 6.2f, 1.0f, 3.0f)));

        write("holo_mk3_states.png", tiles, 3);
    }

    @Test
    void everyMarkStaysInsideTheVolume() {
        for (final HoloMk3Settings.Style style : HoloMk3Settings.Style.values()) {
            for (final HoloMk3Settings.Layout layout : HoloMk3Settings.Layout.values()) {
                for (final float boot : new float[]{0.2f, 0.6f, 1.0f}) {
                    for (final int lanes : new int[]{0, 1, 4}) {
                        final HoloMk3Settings cfg = settings();
                        cfg.setStyle(style);
                        cfg.setLayout(layout);
                        final HoloScene scene = SceneBuilder.build(channels(lanes), Set.of(), cfg,
                                4000L, 3.0f, boot, 96);
                        final Bounds b = new Bounds();
                        HoloPainter.paint(b, scene, cfg, 3.0f);
                        final String where = style + "/" + layout + " boot=" + boot + " lanes=" + lanes;
                        assertFalse(b.empty, "nothing was drawn at all for " + where);
                        assertTrue(b.maxX <= scene.w * 0.9f, "x ran past the volume for " + where
                                + " (" + b.maxX + " > " + scene.w * 0.9f + ")");
                        assertTrue(b.minX >= -scene.w * 0.9f, "x ran past the volume for " + where);
                        assertTrue(b.maxZ <= scene.d * 0.9f, "z ran past the volume for " + where);
                        assertTrue(b.minZ >= -scene.d * 0.9f, "z ran past the volume for " + where);
                        assertTrue(b.maxY <= scene.h * 1.5f, "y ran past the volume for " + where
                                + " (" + b.maxY + " > " + scene.h * 1.5f + ")");
                        assertTrue(b.minY >= -scene.h * 0.4f, "y ran below the volume for " + where);
                    }
                }
            }
        }
    }

    @Test
    void anEmptyProjectorSaysSoRatherThanReadingZero() {
        final HoloMk3Settings cfg = settings();
        final HoloScene scene = SceneBuilder.build(List.of(), Set.of(), cfg, 0L, 3.0f, 1.0f, 96);
        assertTrue(scene.empty);
        assertTrue(scene.title.contains("NO SIGNAL"), "expected the absence to be named, got "
                + scene.title);
        final Bounds b = new Bounds();
        HoloPainter.paint(b, scene, cfg, 3.0f);

        assertTrue(b.maxY > 0.05f * scene.h, "the noise floor collapsed onto the baseline");
    }

    private static HoloMk3Settings settings() {
        final HoloMk3Settings cfg = new HoloMk3Settings();
        cfg.setStyle(HoloMk3Settings.Style.VECTOR);
        cfg.setSweepSeconds(7.0f);
        return cfg;
    }

    private static List<DataChannel> channels(int count) {
        final String[][] spec = {
                {"src_stress_1_auto_stress_pct", "Stress %"},
                {"src_speed_2_auto_rpm", "RPM"},
                {"src_tank_3_auto_tank_fill", "Tank Fill %"},
                {"src_belt_4_auto_items_min", "Items/min"},
        };
        final MetricType[] kinds = {MetricType.STRESS_PERCENT, MetricType.RPM,
                MetricType.FILL_LEVEL, MetricType.ITEMS_PER_MIN};
        final List<DataChannel> out = new ArrayList<>();
        for (int c = 0; c < Math.min(count, spec.length); c++) {
            final DataChannel ch = new DataChannel(spec[c][0], spec[c][1]);
            ch.getStore().setMetricType(kinds[c]);
            for (int i = 0; i < 240; i++) {
                ch.getStore().addSample(sample(c, i), 100L + i * 20L);
            }
            out.add(ch);
        }
        return out;
    }

    private static float sample(int channel, int i) {
        final float t = i / 240.0f;
        return switch (channel) {
            case 0 -> 34.0f + 44.0f * t + (i == 158 ? 26.0f : 0.0f)
                    + 3.2f * (float) Math.sin(i * 0.41);

            case 1 -> 16.0f * (1 + (i / 60));

            case 2 -> i > 150 && i < 195 ? 88.0f
                    : 52.0f + 34.0f * (float) Math.sin(i * 0.026);

            default -> i > 190 && i < 215 ? 4.0f
                    : 240.0f + 70.0f * (float) Math.sin(i * 0.09) + 30.0f * (float) Math.sin(i * 0.7);
        };
    }

    private record Tile(String label, BufferedImage image) {
    }

    private static BufferedImage draw(HoloMk3Settings cfg, List<DataChannel> data,
                                      float yaw, float pitch, float dist, float boot, float seconds) {
        final float floorY = 1.0f + cfg.getLift();
        final Vector3f centre = new Vector3f(0.0f, floorY + cfg.getVolumeHeight() * 0.45f, 0.0f);
        final double ry = Math.toRadians(yaw);
        final double rp = Math.toRadians(pitch);
        final Vector3f eye = new Vector3f(
                centre.x + (float) (Math.sin(ry) * Math.cos(rp)) * dist,
                centre.y + (float) Math.sin(rp) * dist,
                centre.z + (float) (Math.cos(ry) * Math.cos(rp)) * dist);

        final HoloRasterSink sink = new HoloRasterSink(TILE_W, TILE_H, eye, centre, 52.0f, GROUND);
        final HoloScene scene = SceneBuilder.build(data, Set.of(), cfg, 5000L, seconds, boot, 128);

        HoloEmitter.paint(sink, cfg.getVolumeWidth() * 0.5f, cfg.getVolumeDepth() * 0.5f, floorY,
                boot > 0.0f, 0.6f, cfg.getGlow(), seconds);
        HoloPainter.paint(new Lifted(sink, floorY), scene, cfg, seconds);
        sink.flushSolids();
        return sink.finish();
    }

    private static void write(String name, List<Tile> tiles, int columns) throws IOException {
        OUT.mkdirs();
        final int rows = (tiles.size() + columns - 1) / columns;
        final BufferedImage sheet = new BufferedImage(
                columns * TILE_W + PAD * (columns + 1),
                rows * (TILE_H + LABEL) + PAD * (rows + 1),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161A));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < tiles.size(); i++) {
            final int cx = PAD + (i % columns) * (TILE_W + PAD);
            final int cy = PAD + (i / columns) * (TILE_H + LABEL + PAD);
            g.drawImage(tiles.get(i).image(), cx, cy, null);
            g.setColor(new Color(0xA8B2BE));
            g.drawString(tiles.get(i).label(), cx + 2, cy + TILE_H + 14);
        }
        g.dispose();
        final File file = new File(OUT, name);
        PreviewImages.write(sheet, "PNG", file);
        System.out.println("wrote " + file.getPath() + "  (" + tiles.size() + " views)");
    }

    private record Lifted(HoloSink inner, float dy) implements HoloSink {
        @Override
        public void tri(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                        float x3, float y3, float z3, int c3) {
            this.inner.tri(x1, y1 + this.dy, z1, c1, x2, y2 + this.dy, z2, c2,
                    x3, y3 + this.dy, z3, c3);
        }

        @Override
        public void solid(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                          float x3, float y3, float z3, int c3) {
            this.inner.solid(x1, y1 + this.dy, z1, c1, x2, y2 + this.dy, z2, c2,
                    x3, y3 + this.dy, z3, c3);
        }

        @Override
        public void text(String s, float x, float y, float z, int color, float scale) {
            this.inner.text(s, x, y + this.dy, z, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return this.inner.textWidth(s, scale);
        }

        @Override
        public Vector3f eye() {
            final Vector3f e = this.inner.eye();
            return new Vector3f(e.x, e.y - this.dy, e.z);
        }

        @Override
        public Vector3f right() {
            return this.inner.right();
        }

        @Override
        public Vector3f up() {
            return this.inner.up();
        }

        @Override
        public float feather() {
            return this.inner.feather();
        }

        @Override
        public float pixel() {
            return this.inner.pixel();
        }
    }

    private static final class Bounds implements HoloSink {
        private final Vector3f eye = new Vector3f(0.0f, 1.2f, -7.0f);
        private final Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        private final Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);

        boolean empty = true;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        private void note(float x, float y, float z) {
            this.empty = false;
            this.minX = Math.min(this.minX, x);
            this.maxX = Math.max(this.maxX, x);
            this.minY = Math.min(this.minY, y);
            this.maxY = Math.max(this.maxY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxZ = Math.max(this.maxZ, z);
        }

        @Override
        public void tri(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                        float x3, float y3, float z3, int c3) {
            note(x1, y1, z1);
            note(x2, y2, z2);
            note(x3, y3, z3);
        }

        @Override
        public void solid(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                          float x3, float y3, float z3, int c3) {
            tri(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
        }

        @Override
        public void text(String s, float x, float y, float z, int color, float scale) {
            note(x, y, z);
            note(x + textWidth(s, scale), y - scale * 9.0f, z);
        }

        @Override
        public float textWidth(String s, float scale) {
            return s == null ? 0.0f : s.length() * 6.0f * scale;
        }

        @Override
        public Vector3f eye() {
            return this.eye;
        }

        @Override
        public Vector3f right() {
            return this.right;
        }

        @Override
        public Vector3f up() {
            return this.up;
        }

        @Override
        public float feather() {
            return 1.0f;
        }

        @Override
        public float pixel() {
            return 0.004f;
        }
    }
}
