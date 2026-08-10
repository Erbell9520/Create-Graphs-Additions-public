package com.createstats.vccmk2;

import com.createstats.CreateStats;
import com.createstats.renderer.BitmapFont;
import com.createstats.renderer.TextureGraphRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class VccMk2ScreenTextureManager {

    private static final Map<BlockPos, Entry> TEXTURES = new HashMap<>();
    private static final int W = 160;
    private static final int H = 128;
    private static final int UPDATE_INTERVAL = 3;
    private static final int MAX_IDLE_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 100;
    private static int cleanupCounter;

    private VccMk2ScreenTextureManager() {
    }

    public static ResourceLocation getOrUpdateTexture(BlockPos pos, VccMk2VisualState state) {
        Entry entry = TEXTURES.get(pos);
        if (entry == null) {
            entry = createEntry(pos);
            if (entry == null) {
                return null;
            }
            TEXTURES.put(pos, entry);
        }
        long now = gameTime();
        entry.lastAccessTick = now;
        if (now - entry.lastUpdateTick >= UPDATE_INTERVAL || entry.lastState != state) {
            renderScreen(entry.image, state, now);
            entry.dynamicTexture.upload();
            entry.lastUpdateTick = now;
            entry.lastState = state;
        }
        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static Entry createEntry(BlockPos pos) {
        try {
            NativeImage image = new NativeImage(W, H, false);
            DynamicTexture dyn = new DynamicTexture(image);
            dyn.setFilter(false, false); // NEAREST: crisp CRT pixels, no edge bleed
            String name = "create_stats/vcc_mk2_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            ResourceLocation loc = Minecraft.getInstance().getTextureManager().register(name, dyn);
            Entry e = new Entry();
            e.image = image;
            e.dynamicTexture = dyn;
            e.location = loc;
            e.lastAccessTick = gameTime();
            return e;
        } catch (Exception ex) {
            CreateStats.LOGGER.warn("Failed to create VCC Mk II screen texture", ex);
            return null;
        }
    }

    // themes
    private record Theme(int accent, int dim, int sky, int ground, boolean powered) {}

    private static Theme themeFor(VccMk2VisualState state) {
        return switch (state) {
            case STANDBY -> new Theme(0xFFF5CD78, 0xFF8A6E3C, 0xFF2C2410, 0xFF1C160A, true);
            case ACTIVE  -> new Theme(0xFF78F0C8, 0xFF328E72, 0xFF103030, 0xFF0A1A1A, true);
            case WARNING -> new Theme(0xFFFF6E6E, 0xFF8E2C2C, 0xFF2C1010, 0xFF180A0A, true);
            case OFF     -> new Theme(0xFF39414C, 0xFF20262E, 0xFF0C0E12, 0xFF080A0D, false);
        };
    }

    // draw
    private static void renderScreen(NativeImage img, VccMk2VisualState state, long t) {
        Theme th = themeFor(state);
        TextureGraphRenderer.fillRect(img, 0, 0, W, H, 0xFF05080B);
        int ix = 4, iy = 4, iw = W - 8, ih = H - 8;
        TextureGraphRenderer.fillRect(img, ix, iy, iw, ih, 0xFF080C10);
        TextureGraphRenderer.drawBorder(img, ix, iy, iw, ih, 1, th.dim());

        if (!th.powered()) {
            BitmapFont.drawString(img, "OFFLINE", ix + 10, iy + ih / 2 - 3, th.accent(), 1);
            scanlines(img, ix, iy, iw, ih);
            vignette(img, ix, iy, iw, ih);
            powerLed(img, ix + iw - 8, iy + 5, 0xFF3A2222, t, false);
            return;
        }

        // header + mode badge
        BitmapFont.drawString(img, "VCC MK II", ix + 5, iy + 4, th.accent(), 1);
        String badge = state.getSerializedName().toUpperCase();
        int badgeW = BitmapFont.measureString(badge, 1) + 8;
        int badgeX = ix + iw - badgeW - 2;
        TextureGraphRenderer.fillRect(img, badgeX, iy + 3, badgeW, 11, dark(th.dim()));
        TextureGraphRenderer.drawBorder(img, badgeX, iy + 3, badgeW, 11, 1, th.accent());
        BitmapFont.drawString(img, badge, badgeX + 4, iy + 5, th.accent(), 1);

        // artificial horizon (left in texture space)
        int cx = ix + 38, cy = iy + 64, r = 30;
        boolean act = state == VccMk2VisualState.ACTIVE;
        boolean warn = state == VccMk2VisualState.WARNING;
        double tilt;
        double pitch;
        if (act) {
            tilt = Math.toRadians(7.0 * Math.sin(t * 0.045));
            pitch = 5.0 * Math.sin(t * 0.03);
        } else if (warn) {
            tilt = Math.toRadians(13.0);
            pitch = 4.0;
        } else {
            tilt = Math.toRadians(1.5 * Math.sin(t * 0.02));
            pitch = 0.0;
        }
        drawHorizon(img, cx, cy, r, tilt, pitch, th);

        // heading tape (scrolls only while ACTIVE; steady idle otherwise)
        drawHeadingTape(img, ix + iw / 2 + 6, iy + 22, iw / 2 - 12, act ? t : 0L, th);

        // telemetry block (right). Live demo values only while ACTIVE; steady idle otherwise.
        int spdTenths = act ? (120 + (int) (40 * Math.sin(t * 0.05))) : 0;
        int rpm = act ? (180 + (int) (60 * Math.sin(t * 0.08 + 1))) : 0;
        int hdg = act ? (int) ((t / 2) % 360) : 127;
        int alt = act ? (80 + (int) (8 * Math.sin(t * 0.02))) : 84;
        String[][] rows = {
                {"SPD", String.format("%.1f", spdTenths / 10.0)},
                {"ALT", Integer.toString(alt)},
                {"HDG", pad3(hdg)},
                {"RPM", Integer.toString(rpm)},
        };
        int tx = ix + iw / 2 + 8;
        for (int i = 0; i < rows.length; i++) {
            int ry = iy + 52 + i * 16;
            BitmapFont.drawString(img, rows[i][0], tx, ry, th.dim(), 1);
            BitmapFont.drawString(img, rows[i][1], tx + 30, ry, th.accent(), 1);
        }

        sweep(img, ix, iy, iw, ih, t, th.accent());
        scanlines(img, ix, iy, iw, ih);
        vignette(img, ix, iy, iw, ih);
        powerLed(img, ix + iw - 8, iy + 5, th.accent(), t, true);
    }

    private static void drawHorizon(NativeImage img, int cx, int cy, int r, double tilt, double pitch, Theme th) {
        double tan = Math.tan(tilt);
        int pitchPx = (int) Math.round(pitch);
        for (int yy = cy - r; yy <= cy + r; yy++) {
            for (int xx = cx - r; xx <= cx + r; xx++) {
                int dx = xx - cx;
                int dy = yy - cy;
                if (dx * dx + dy * dy > r * r) {
                    continue;
                }
                double line = (dy + pitchPx) - tan * dx;
                setPx(img, xx, yy, line < 0 ? th.sky() : th.ground());
            }
        }
        // horizon line + wings + center pip
        drawCircle(img, cx, cy, r, th.dim());
        int wing = th.accent();
        hline(img, cx - r + 5, cy, 10, wing);
        hline(img, cx + r - 15, cy, 10, wing);
        setPx(img, cx, cy - 1, wing);
        setPx(img, cx, cy, wing);
        setPx(img, cx - 1, cy, wing);
        setPx(img, cx + 1, cy, wing);
        // pitch ladder
        for (int p = -2; p <= 2; p++) {
            if (p == 0) {
                continue;
            }
            int ly = cy + p * 8 + pitchPx;
            if (ly > cy - r + 3 && ly < cy + r - 3) {
                hline(img, cx - 6, ly, 12, th.dim());
            }
        }
    }

    private static void drawHeadingTape(NativeImage img, int x, int y, int w, long t, Theme th) {
        hline(img, x, y, w, th.dim());
        int scroll = (int) (t % 12);
        for (int i = 0; i < w / 12 + 2; i++) {
            int hx = x + i * 12 - scroll;
            if (hx >= x && hx < x + w) {
                vline(img, hx, y - 3, 4, th.accent());
                int label = ((i * 3) % 36);
                BitmapFont.drawString(img, pad2(label), hx - 4, y + 3, th.dim(), 1);
            }
        }
        // center marker
        int mid = x + w / 2;
        fillTri(img, mid, y - 6, 4, th.accent());
    }

    // effects
    private static void sweep(NativeImage img, int x, int y, int w, int h, long t, int accent) {
        int center = y + (int) ((t % 110L) * h / 110.0);
        int half = Math.max(4, h / 10);
        for (int py = Math.max(y, center - half); py < Math.min(y + h, center + half); py++) {
            float d = Math.abs(py - center) / (float) half;
            int a = Math.round((1.0f - d) * 16);
            for (int px = x + 1; px < x + w - 1; px++) {
                blend(img, px, py, withAlpha(accent, a));
            }
        }
    }

    private static void scanlines(NativeImage img, int x, int y, int w, int h) {
        for (int py = y; py < y + h; py += 2) {
            for (int px = x; px < x + w; px++) {
                int argb = abgrToArgb(img.getPixelRGBA(px, py));
                img.setPixelRGBA(px, py, BitmapFont.argbToAbgr(mul(argb, 0.80f)));
            }
        }
    }

    private static void vignette(NativeImage img, int x, int y, int w, int h) {
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float maxd = (cx - x) * (cx - x) + (cy - y) * (cy - y);
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                float dx = px - cx;
                float dy = py - cy;
                float v = 1.0f - 0.5f * ((dx * dx + dy * dy) / maxd);
                if (v >= 0.999f) {
                    continue;
                }
                int argb = abgrToArgb(img.getPixelRGBA(px, py));
                img.setPixelRGBA(px, py, BitmapFont.argbToAbgr(mul(argb, Math.max(0.0f, v))));
            }
        }
    }

    private static void powerLed(NativeImage img, int x, int y, int color, long t, boolean on) {
        int a = on ? (160 + (int) (80 * Math.sin(t * 0.2))) : 90;
        int c = withAlpha(color, Math.max(0, Math.min(255, a)));
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = 0; dx < 3; dx++) {
                blend(img, x + dx, y + dy, c);
            }
        }
    }

    // pixel helpers
    private static void setPx(NativeImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        img.setPixelRGBA(x, y, BitmapFont.argbToAbgr(argb));
    }

    private static void blend(NativeImage img, int x, int y, int srcArgb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        int a = (srcArgb >>> 24) & 0xFF;
        if (a == 0) {
            return;
        }
        if (a == 255) {
            img.setPixelRGBA(x, y, BitmapFont.argbToAbgr(srcArgb));
            return;
        }
        int dst = abgrToArgb(img.getPixelRGBA(x, y));
        int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
        int sr = (srcArgb >> 16) & 0xFF, sg = (srcArgb >> 8) & 0xFF, sb = srcArgb & 0xFF;
        int rr = (sr * a + dr * (255 - a)) / 255;
        int rg = (sg * a + dg * (255 - a)) / 255;
        int rb = (sb * a + db * (255 - a)) / 255;
        img.setPixelRGBA(x, y, BitmapFont.argbToAbgr(0xFF000000 | (rr << 16) | (rg << 8) | rb));
    }

    private static void hline(NativeImage img, int x, int y, int w, int argb) {
        for (int i = 0; i < w; i++) {
            setPx(img, x + i, y, argb);
        }
    }

    private static void vline(NativeImage img, int x, int y, int h, int argb) {
        for (int i = 0; i < h; i++) {
            setPx(img, x, y + i, argb);
        }
    }

    private static void drawCircle(NativeImage img, int cx, int cy, int r, int argb) {
        for (int a = 0; a < 360; a += 4) {
            double rad = Math.toRadians(a);
            setPx(img, cx + (int) Math.round(Math.cos(rad) * r), cy + (int) Math.round(Math.sin(rad) * r), argb);
        }
    }

    private static void fillTri(NativeImage img, int cx, int topY, int size, int argb) {
        for (int dy = 0; dy < size; dy++) {
            hline(img, cx - dy, topY + dy, dy * 2 + 1, argb);
        }
    }

    private static int withAlpha(int argb, int a) {
        return ((a & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private static int dark(int argb) {
        return mul(argb, 0.35f);
    }

    private static int mul(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int abgrToArgb(int abgr) {
        return BitmapFont.argbToAbgr(abgr); // swap is its own inverse
    }

    private static String pad2(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }

    private static String pad3(int v) {
        return String.format("%03d", ((v % 360) + 360) % 360);
    }

    // lifecycle
    private static void cleanupStale(long now) {
        Iterator<Map.Entry<BlockPos, Entry>> it = TEXTURES.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            if (now - e.lastAccessTick > MAX_IDLE_TICKS) {
                release(e);
                it.remove();
            }
        }
    }

    private static void release(Entry e) {
        try {
            Minecraft.getInstance().getTextureManager().release(e.location);
            e.dynamicTexture.close();
        } catch (Exception ignored) {
        }
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture dynamicTexture;
        ResourceLocation location;
        long lastUpdateTick = Long.MIN_VALUE;
        long lastAccessTick;
        VccMk2VisualState lastState;
    }
}
