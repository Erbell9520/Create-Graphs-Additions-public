package com.createstats.book;

import com.createstats.book.layout.BookCanvas;
import com.createstats.book.layout.Grain;
import com.createstats.book.layout.Rect;
import com.createstats.util.PreviewImages;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PngBookCanvas implements BookCanvas {
    static final Path CLIENT_JAR = Path.of(System.getProperty("user.home"),
            "AppData/Roaming/ModrinthApp/meta/versions/1.21.1-21.1.251/1.21.1-21.1.251.jar");

    record Drawn(String what, Rect bounds) {
    }

    private final BufferedImage image;
    private final int scale;
    private final BufferedImage font;
    private final int[] advance = new int[256];
    private final Map<String, BufferedImage> icons = new HashMap<>();
    private final Map<String, BufferedImage> sprites = new HashMap<>();
    private BufferedImage capture;

    final List<Drawn> drawn = new ArrayList<>();
    final List<Drawn> hots = new ArrayList<>();

    PngBookCanvas(int w, int h, int scale) throws IOException {
        this.image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        this.scale = scale;
        this.font = ImageIO.read(Path.of("assets_ascii.png").toFile());
        for (int ch = 0; ch < 256; ch++) {
            advance[ch] = glyphAdvance(ch);
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

    private Rect clip;

    void clip(Rect r) {
        this.clip = r;
    }

    BufferedImage image() {
        return image;
    }

    private void blend(int px, int py, int argb) {
        if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
            return;
        }
        if (clip != null && (px < clip.x() * scale || py < clip.y() * scale || px >= clip.right() * scale
                || py >= clip.bottom() * scale)) {
            return;
        }
        final int a = argb >>> 24;
        if (a == 0) {
            return;
        }
        if (a == 255) {
            image.setRGB(px, py, argb);
            return;
        }
        final int under = image.getRGB(px, py);
        final int r = mix((argb >> 16) & 0xFF, (under >> 16) & 0xFF, a);
        final int g = mix((argb >> 8) & 0xFF, (under >> 8) & 0xFF, a);
        final int b = mix(argb & 0xFF, under & 0xFF, a);
        final int ua = under >>> 24;
        final int oa = Math.min(255, a + ua * (255 - a) / 255);
        image.setRGB(px, py, (oa << 24) | (r << 16) | (g << 8) | b);
    }

    private static int mix(int top, int bottom, int a) {
        return (top * a + bottom * (255 - a)) / 255;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0 * scale; y < y1 * scale; y++) {
            for (int x = x0 * scale; x < x1 * scale; x++) {
                blend(x, y, argb);
            }
        }
    }

    @Override
    public int width(String s, boolean bold) {
        if (s == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance[s.charAt(i) & 0xFF] + (bold ? 1 : 0);
        }
        return w;
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean bold) {
        drawn.add(new Drawn("text:" + s, new Rect(x, y, width(s, bold), 8)));
        glyphs(s, x * scale, y * scale, scale, argb, bold);
    }

    @Override
    public void shadowText(String s, int x, int y, int argb) {
        drawn.add(new Drawn("text:" + s, new Rect(x, y, width(s, false) + 1, 9)));
        glyphs(s, (x + 1) * scale, (y + 1) * scale, scale, BookTokens.BUTTON_SHADOW, false);
        glyphs(s, x * scale, y * scale, scale, argb, false);
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, int k) {
        drawn.add(new Drawn("text:" + s, new Rect(x, y, width(s, false) * k, 8 * k)));
        glyphs(s, x * scale, y * scale, scale * k, argb, false);
    }

    private void glyphs(String s, int ox, int oy, int px, int argb, boolean bold) {
        final int cell = font.getWidth() / 16;
        int cx = ox;
        for (int i = 0; i < s.length(); i++) {
            final int ch = s.charAt(i) & 0xFF;
            final int gx = (ch % 16) * cell;
            final int gy = (ch / 16) * cell;
            for (int row = 0; row < cell; row++) {
                for (int col = 0; col < cell; col++) {
                    if ((font.getRGB(gx + col, gy + row) >>> 24) == 0) {
                        continue;
                    }
                    for (int b = 0; b < (bold ? 2 : 1); b++) {
                        final int x0 = cx + (col + b) * px;
                        final int y0 = oy + row * px;
                        for (int yy = 0; yy < px; yy++) {
                            for (int xx = 0; xx < px; xx++) {
                                blend(x0 + xx, y0 + yy, argb | 0xFF000000);
                            }
                        }
                    }
                }
            }
            cx += (advance[ch] + (bold ? 1 : 0)) * px;
        }
    }

    @Override
    public void item(String registryId, int x, int y) {
        drawn.add(new Drawn("item:" + registryId, new Rect(x, y, 16, 16)));
        final BufferedImage icon = icons.computeIfAbsent(registryId, PngBookCanvas::loadIcon);
        if (icon == null) {
            return;
        }
        paste(icon, x * scale, y * scale, 16 * scale, 16 * scale);
    }

    private static BufferedImage loadIcon(String registryId) {
        final String namespace = registryId.contains(":") ? registryId.substring(0, registryId.indexOf(':')) : "create_stats";
        final String path = registryId.substring(registryId.indexOf(':') + 1);
        final Path file = "create_stats".equals(namespace) ? Path.of("creative_tab_preview/icons", path + ".png")
                : Path.of("students_manual_preview/icons", namespace, path + ".png");
        try {
            return Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void paste(BufferedImage src, int ox, int oy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                blend(ox + x, oy + y, src.getRGB(x * src.getWidth() / w, y * src.getHeight() / h));
            }
        }
    }

    @Override
    public void sprite(String spriteId, int x, int y, int w, int h) {
        drawn.add(new Drawn("sprite:" + spriteId, new Rect(x, y, w, h)));
        final BufferedImage img = sprites.computeIfAbsent(spriteId, PngBookCanvas::loadSprite);
        if (img == null) {
            fill(x, y, x + w, y + h, 0xFF6F6F6F);
            return;
        }
        if (img.getWidth() == w && img.getHeight() == h) {
            paste(img, x * scale, y * scale, w * scale, h * scale);
            return;
        }

        final int b = 3;
        for (int yy = 0; yy < h; yy++) {
            final int sy = yy < b ? yy : yy >= h - b ? img.getHeight() - (h - yy) : b + (yy - b) % (img.getHeight() - 2 * b);
            for (int xx = 0; xx < w; xx++) {
                final int sx = xx < b ? xx : xx >= w - b ? img.getWidth() - (w - xx) : b + (xx - b) % (img.getWidth() - 2 * b);
                fill(x + xx, y + yy, x + xx + 1, y + yy + 1, img.getRGB(sx, sy));
            }
        }
    }

    private static BufferedImage loadSprite(String spriteId) {
        if (!Files.isRegularFile(CLIENT_JAR)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(CLIENT_JAR.toFile())) {
            final ZipEntry e = zip.getEntry("assets/minecraft/textures/gui/sprites/" + spriteId + ".png");
            if (e == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(e)) {
                return ImageIO.read(in);
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void paper(int x0, int y0, int x1, int y1) {
        grain(x0, y0, x1, y1, BookTokens.PAPER, BookTokens.PAPER_GRAIN, 21);
    }

    @Override
    public void vinyl(int x0, int y0, int x1, int y1) {
        grain(x0, y0, x1, y1, BookTokens.VINYL, BookTokens.VINYL_GRAIN, 11);
    }

    private void grain(int x0, int y0, int x1, int y1, int base, float amp, int seed) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                fill(x, y, x + 1, y + 1, Grain.colour(base, amp, x - x0, y - y0, BookTokens.GRAIN_CELL, seed));
            }
        }
    }

    @Override
    public void specimen(String specimenId, int x, int y, int num, int den, int mouseX, int mouseY) {
        final com.createstats.book.specimen.SpecimenAnatomy a =
                com.createstats.book.specimen.Specimens.anatomy(specimenId).orElseThrow();
        final int w = (int) Math.ceil(a.width() * num / (double) den);
        final int h = (int) Math.ceil(a.height() * num / (double) den);
        drawn.add(new Drawn("specimen:" + specimenId, new Rect(x, y, w, h)));
        if (a.drawing()) {
            return;
        }
        if (a.block()) {
            standIn(specimenId, x, y, w, h);
            return;
        }
        if (com.createstats.book.specimen.BatteryPlates.isPlate(specimenId)) {
            plate(specimenId, x, y, num, den, mouseX, mouseY);
            return;
        }
        if (com.createstats.book.specimen.RingPlates.isPlate(specimenId)) {
            ringPlate(specimenId, x, y, num, den, mouseX, mouseY);
            return;
        }
        if (com.createstats.book.specimen.PduPlates.isPlate(specimenId)) {
            pduPlate(specimenId, x, y, num, den, mouseX, mouseY);
            return;
        }
        if (capture == null) {
            try {
                capture = ImageIO.read(Path.of("power_meter_preview/rating_plate/iter08/01_flowing.png").toFile());
            } catch (IOException e) {
                fill(x, y, x + w, y + h, 0xFF404448);
                return;
            }
        }

        final double k = capture.getWidth() / 853.0;
        final int ppx = num * scale / den;
        for (int py = 0; py < 220; py++) {
            for (int px = 0; px < 352; px++) {
                final int rgb = capture.getRGB((int) ((250 + px + 0.5) * k), (int) ((156 + py + 0.5) * k));
                for (int yy = 0; yy < ppx; yy++) {
                    for (int xx = 0; xx < ppx; xx++) {
                        blend(x * scale + px * ppx + xx, y * scale + py * ppx + yy, rgb | 0xFF000000);
                    }
                }
            }
        }
    }

    private void plate(String id, int x, int y, int num, int den, int mouseX, int mouseY) {
        final int pw = com.createstats.book.specimen.BatteryPlates.W;
        final int ph = com.createstats.book.specimen.BatteryPlates.H;
        if (num * scale / den <= 0) {
            return;
        }
        final PngBookCanvas panel;
        try {
            panel = new PngBookCanvas(pw, ph, 1);
        } catch (IOException e) {
            fill(x, y, x + pw * num / den, y + ph * num / den, 0xFF404448);
            return;
        }
        final double k = num / (double) den;
        final boolean inside = mouseX >= x && mouseY >= y && mouseX < x + pw * k && mouseY < y + ph * k;
        final int mx = inside ? (int) Math.floor((mouseX - x) / k) : -10_000;
        final int my = inside ? (int) Math.floor((mouseY - y) / k) : -10_000;
        com.createstats.book.specimen.BatteryPlates.draw(new com.createstats.gui.battery.BatteryCanvas() {
            @Override
            public void fill(int x0, int y0, int x1, int y1, int argb) {
                panel.fill(x0, y0, x1, y1, argb);
            }

            @Override
            public void text(String s, int tx, int ty, int argb) {
                panel.text(s, tx, ty, argb, false);
            }

            @Override
            public int width(String s) {
                return panel.width(s, false);
            }

            @Override
            public void bold(String s, int tx, int ty, int argb) {
                panel.text(s, tx, ty, argb, true);
            }

            @Override
            public int boldWidth(String s) {
                return panel.width(s, true);
            }
        }, id, mx, my);
        final int ppx = num * scale / den;
        for (int v = 0; v < ph; v++) {
            for (int u = 0; u < pw; u++) {
                final int argb = panel.image.getRGB(u, v);
                for (int yy = 0; yy < ppx; yy++) {
                    for (int xx = 0; xx < ppx; xx++) {
                        blend(x * scale + u * ppx + xx, y * scale + v * ppx + yy, argb | 0xFF000000);
                    }
                }
            }
        }
    }

    private void pduPlate(String id, int x, int y, int num, int den, int mouseX, int mouseY) {
        final int pw = com.createstats.book.specimen.PduPlates.W;
        final int ph = com.createstats.book.specimen.PduPlates.H;
        final int ppx = num * scale / den;
        if (ppx <= 0) {
            return;
        }
        final PngBookCanvas panel;
        try {
            panel = new PngBookCanvas(pw, ph, 1);
        } catch (IOException e) {
            fill(x, y, x + pw * num / den, y + ph * num / den, 0xFF404448);
            return;
        }
        final double k = num / (double) den;
        final boolean inside = mouseX >= x && mouseY >= y && mouseX < x + pw * k && mouseY < y + ph * k;
        final int mx = inside ? (int) Math.floor((mouseX - x) / k) : -10_000;
        final int my = inside ? (int) Math.floor((mouseY - y) / k) : -10_000;
        PduRig.paint(id, panel, mx, my);
        for (int v = 0; v < ph; v++) {
            for (int u = 0; u < pw; u++) {
                final int argb = panel.image.getRGB(u, v);
                for (int yy = 0; yy < ppx; yy++) {
                    for (int xx = 0; xx < ppx; xx++) {
                        blend(x * scale + u * ppx + xx, y * scale + v * ppx + yy, argb | 0xFF000000);
                    }
                }
            }
        }
        if (com.createstats.book.specimen.PduPlates.FLOW.equals(id)) {
            final int[] vp = com.createstats.book.specimen.PduPlates.drawing(id).part("flow.viewport");
            standInPx(com.createstats.book.specimen.PduPlates.FLOW_SCENE, x * scale + vp[0] * ppx,
                    y * scale + vp[1] * ppx, vp[2] * ppx, vp[3] * ppx);
        }
    }

    private void ringPlate(String id, int x, int y, int num, int den, int mouseX, int mouseY) {
        final int pw = com.createstats.book.specimen.RingPlates.width(id);
        final int ph = com.createstats.book.specimen.RingPlates.height(id);
        final int ppx = num * scale / den;
        if (ppx <= 0) {
            return;
        }
        final int w = (int) Math.ceil(pw * num / (double) den);
        final int h = (int) Math.ceil(ph * num / (double) den);
        fill(x, y, x + w, y + h, BookTokens.WORLD_SKY);
        standIn(com.createstats.book.specimen.RingPlates.world(id).id(), x, y, w, h);
        final PngBookCanvas layer;
        try {
            layer = new PngBookCanvas(pw, ph, 1);
        } catch (IOException e) {
            return;
        }
        final double k = num / (double) den;
        final boolean inside = mouseX >= x && mouseY >= y && mouseX < x + pw * k && mouseY < y + ph * k;
        final int mx = inside ? (int) Math.floor((mouseX - x) / k) : -10_000;
        final int my = inside ? (int) Math.floor((mouseY - y) / k) : -10_000;
        final com.createstats.client.wire.WireCanvas c = new com.createstats.client.wire.WireCanvas() {
            @Override
            public void fill(int x0, int y0, int x1, int y1, int argb) {
                layer.fill(x0, y0, x1, y1, argb);
            }

            @Override
            public void text(String s, int tx, int ty, int argb) {
                layer.text(s, tx, ty, argb, false);
            }

            @Override
            public int width(String s) {
                return layer.width(s, false);
            }
        };
        if (com.createstats.book.specimen.RingPlates.WRENCH.equals(id)) {
            final int[] aim = com.createstats.book.specimen.RingPlates.aim();
            layer.sprite("hud/crosshair", aim[0] - 7, aim[1] - 7, 15, 15);
            com.createstats.book.specimen.RingPlates.drawWrench(c);
        } else {
            com.createstats.book.specimen.RingPlates.drawRing(c, mx, my);
        }
        for (int v = 0; v < ph; v++) {
            for (int u = 0; u < pw; u++) {
                final int argb = layer.image.getRGB(u, v);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                for (int yy = 0; yy < ppx; yy++) {
                    for (int xx = 0; xx < ppx; xx++) {
                        blend(x * scale + u * ppx + xx, y * scale + v * ppx + yy, argb);
                    }
                }
            }
        }
    }

    static final Path STAND_INS = Path.of("students_manual_preview/figures");

    static final int STAND_IN_RES = 3;

    private final Map<String, BufferedImage> standIns = new HashMap<>();

    private void standIn(String id, int x, int y, int w, int h) {
        if (!standInPx(id, x * scale, y * scale, w * scale, h * scale)) {
            fill(x, y, x + w, y + h, 0xFF9AA0A6);
        }
    }

    private boolean standInPx(String id, int ix, int iy, int tw, int th) {
        final BufferedImage src = standIns.computeIfAbsent(id, k -> {
            try {
                final Path file = STAND_INS.resolve(k + ".png");
                return Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
            } catch (IOException e) {
                return null;
            }
        });
        if (src == null) {
            return false;
        }
        for (int ty = 0; ty < th; ty++) {
            final double sy0 = ty * (double) src.getHeight() / th;
            final double sy1 = (ty + 1) * (double) src.getHeight() / th;
            for (int tx = 0; tx < tw; tx++) {
                final double sx0 = tx * (double) src.getWidth() / tw;
                final double sx1 = (tx + 1) * (double) src.getWidth() / tw;
                double a = 0;
                double r = 0;
                double g = 0;
                double b = 0;
                double area = 0;
                for (int sy = (int) sy0; sy < Math.min(src.getHeight(), Math.ceil(sy1)); sy++) {
                    final double hy = Math.min(sy + 1, sy1) - Math.max(sy, sy0);
                    for (int sx = (int) sx0; sx < Math.min(src.getWidth(), Math.ceil(sx1)); sx++) {
                        final double wx = Math.min(sx + 1, sx1) - Math.max(sx, sx0);
                        final double k = wx * hy;
                        final int argb = src.getRGB(sx, sy);
                        final double al = (argb >>> 24) / 255.0;
                        a += al * k;
                        r += ((argb >> 16) & 0xFF) * al * k;
                        g += ((argb >> 8) & 0xFF) * al * k;
                        b += (argb & 0xFF) * al * k;
                        area += k;
                    }
                }
                if (a <= 0 || area <= 0) {
                    continue;
                }
                final int alpha = (int) Math.round(255 * a / area);
                final int rgb = ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a);
                blend(ix + tx, iy + ty, (Math.min(255, alpha) << 24) | rgb);
            }
        }
        return true;
    }

    @Override
    public void hot(String id, int x0, int y0, int x1, int y1) {
        hots.add(new Drawn(id, new Rect(x0, y0, x1 - x0, y1 - y0)));
    }

    @Override
    public void pushFlip(int pivotX, float scaleX) {
    }

    @Override
    public void popFlip() {
    }

    @Override
    public void raise(int z) {
    }

    void backdrop(int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
    }

    void write(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        PreviewImages.write(image, "png", out.toFile());
    }
}
