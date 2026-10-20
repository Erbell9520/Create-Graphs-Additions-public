package com.createstats.gui.battery;

import com.createstats.util.PreviewImages;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PngCanvas implements BatteryCanvas {
    static final Path DEFAULT_JAR = Path.of(System.getProperty("user.home"),
            "AppData/Roaming/ModrinthApp/meta/versions/1.21.1-21.1.251/1.21.1-21.1.251.jar");

    record TextRun(String text, int x, int y, int w) {
    }

    private final BufferedImage image;
    private final BufferedImage font;
    private final int[] advance = new int[256];
    final List<TextRun> runs = new ArrayList<>();

    private PngCanvas(int w, int h, BufferedImage font) {
        this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        this.font = font;
        for (int ch = 0; ch < 256; ch++) {
            advance[ch] = glyphAdvance(ch);
        }
    }

    static PngCanvas open(int w, int h) throws IOException {
        final Path jar = Path.of(System.getProperty("createstats.clientJar", DEFAULT_JAR.toString()));
        if (!Files.isRegularFile(jar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final ZipEntry entry = zip.getEntry("assets/minecraft/textures/font/ascii.png");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new PngCanvas(w, h, ImageIO.read(in));
            }
        }
    }

    private int glyphAdvance(int ch) {
        if (ch == ' ') {
            return 4;
        }
        final int cell = font.getWidth() / 16;
        final int gx = (ch % 16) * cell;
        final int gy = (ch / 16) * cell;
        for (int col = cell - 1; col >= 0; col--) {
            for (int row = 0; row < cell; row++) {
                if ((font.getRGB(gx + col, gy + row) >>> 24) != 0) {
                    return (col + 1) * 8 / cell + 1;
                }
            }
        }
        return 0;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        final int a = argb >>> 24;
        if (a == 0) {
            return;
        }
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                blend(x, y, argb);
            }
        }
    }

    private void blend(int x, int y, int argb) {
        final int a = argb >>> 24;
        if (a == 255) {
            image.setRGB(x, y, argb);
            return;
        }
        final int under = image.getRGB(x, y);
        final int r = mix((argb >> 16) & 0xFF, (under >> 16) & 0xFF, a);
        final int g = mix((argb >> 8) & 0xFF, (under >> 8) & 0xFF, a);
        final int b = mix(argb & 0xFF, under & 0xFF, a);
        image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private static int mix(int top, int bottom, int a) {
        return (top * a + bottom * (255 - a)) / 255;
    }

    @Override
    public void text(String s, int x, int y, int argb) {
        runs.add(new TextRun(s, x, y, width(s)));
        draw(s, x, y, argb);
    }

    private void draw(String s, int x, int y, int argb) {
        final int cell = font.getWidth() / 16;
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            final int ch = s.charAt(i) & 0xFF;
            final int gx = (ch % 16) * cell;
            final int gy = (ch / 16) * cell;
            for (int row = 0; row < cell; row++) {
                for (int col = 0; col < cell; col++) {
                    if ((font.getRGB(gx + col, gy + row) >>> 24) != 0) {
                        final int px = cx + col * 8 / cell;
                        final int py = y + row * 8 / cell;
                        if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                            blend(px, py, argb | 0xFF000000);
                        }
                    }
                }
            }
            cx += advance[ch];
        }
    }

    @Override
    public void bold(String s, int x, int y, int argb) {
        runs.add(new TextRun(s, x, y, boldWidth(s)));
        draw(s, x, y, argb);
        draw(s, x + 1, y, argb);
    }

    @Override
    public int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance[s.charAt(i) & 0xFF];
        }
        return w;
    }

    void write(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        PreviewImages.write(image, "png", out.toFile());
    }

    void writeScaled(Path out, int scale) throws IOException {
        final BufferedImage big = new BufferedImage(image.getWidth() * scale, image.getHeight() * scale,
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < big.getHeight(); y++) {
            for (int x = 0; x < big.getWidth(); x++) {
                big.setRGB(x, y, image.getRGB(x / scale, y / scale));
            }
        }
        Files.createDirectories(out.getParent());
        PreviewImages.write(big, "png", out.toFile());
    }
}
