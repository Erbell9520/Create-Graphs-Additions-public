package com.createstats.renderer;

import com.createstats.blockentity.PowerMeterBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class PowerMeterTextureManager {
    private static final int W = 128;
    private static final int H = 88;

    private static final int TRACE_INTERVAL_TICKS = 10;

    private static final int TRACE_SAMPLES = 116;

    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final int BACK = 0xFF06110C;
    private static final int GLOW = 0xFF0C1E15;
    private static final int GRID = 0xFF122E20;
    private static final int RULE = 0xFF27604A;
    private static final int TEXT = 0xFFB8F5B5;
    private static final int BRIGHT = 0xFFE4FFDC;
    private static final int MUTED = 0xFF5F8F73;
    private static final int WARN = 0xFFFFD56A;
    private static final int BAD = 0xFFFF5D49;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private PowerMeterTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        int signature = Integer.MIN_VALUE;

        final int[] trace = new int[TRACE_SAMPLES];
        int traceHead;
        int traceFilled;
        long lastTraceEpoch = Long.MIN_VALUE;
    }

    public static ResourceLocation getOrUpdateTexture(PowerMeterBE be) {
        Entry entry = ENTRIES.get(be.getBlockPos());
        if (entry == null) {
            entry = create(be.getBlockPos());
            if (entry == null) {
                return null;
            }
            ENTRIES.put(be.getBlockPos(), entry);
        }
        final long now = gameTime();
        entry.lastAccessTick = now;

        final long epoch = now / TRACE_INTERVAL_TICKS;
        if (epoch != entry.lastTraceEpoch) {
            entry.lastTraceEpoch = epoch;
            entry.trace[entry.traceHead] = be.getRateFePerTick();
            entry.traceHead = (entry.traceHead + 1) % TRACE_SAMPLES;
            if (entry.traceFilled < TRACE_SAMPLES) {
                entry.traceFilled++;
            }
        }

        final int signature = signature(be, epoch);
        if (signature != entry.signature) {
            entry.signature = signature;
            draw(entry.image, be, entry);
            entry.texture.upload();
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static int signature(PowerMeterBE be, long traceEpoch) {
        int result = be.getRateFePerTick();
        result = result * 31 + be.getPeakFePerTick();
        result = result * 31 + Long.hashCode(be.getTotalFe());
        result = result * 31 + be.status().ordinal();
        result = result * 31 + be.getAnalogOutputSignal();
        result = result * 31 + Long.hashCode(traceEpoch);
        return result;
    }

    // ---------- Drawing ----------

    private static void draw(NativeImage image, PowerMeterBE be, Entry entry) {
        fill(image, 0, 0, W, H, BACK);

        for (int y = 16; y < H; y += 9) {
            hline(image, 3, y, W - 3, GRID);
        }
        for (int x = 8; x < W; x += 12) {
            vline(image, x, 16, H - 3, GRID);
        }
        fill(image, 18, 20, W - 36, 22, GLOW);

        fill(image, 0, 0, W, 12, GLOW);
        hline(image, 0, 12, W, RULE);
        BitmapFont.drawString(image, "POWER METER", 4, 3, MUTED, 1);

        final PowerMeterBE.Status state = be.status();
        if (state == PowerMeterBE.Status.LOOPED || state == PowerMeterBE.Status.NO_SUPPLY
                || state == PowerMeterBE.Status.NO_LOAD) {
            BitmapFont.drawRight(image, state == PowerMeterBE.Status.LOOPED ? "LOOP" : "NC", W - 4, 3,
                    BAD, 1);
            drawFault(image, state);
            rect(image, 0, 0, W, H, RULE);
            sheen(image);
            return;
        }

        final int rate = be.getRateFePerTick();
        final int accent = state == PowerMeterBE.Status.FLOWING ? BRIGHT
                : state == PowerMeterBE.Status.BACKED_UP ? WARN : MUTED;
        BitmapFont.drawRight(image, state == PowerMeterBE.Status.FLOWING ? "RUN"
                : state == PowerMeterBE.Status.BACKED_UP ? "FULL" : "IDLE", W - 4, 3, accent, 1);

        BitmapFont.drawString(image, compact(rate), 5, 16, accent, 2);
        BitmapFont.drawString(image, "FE/T", 5, 33, MUTED, 1);

        BitmapFont.drawRight(image, compact(rate * 20L) + "/S", W - 5, 33, MUTED, 1);

        final int peak = be.getPeakFePerTick();
        final float load = peak <= 0 ? 0.0f : Math.min(1.0f, rate / (float) peak);
        bar(image, 5, 43, W - 10, 8, load, accent, "LOAD");

        BitmapFont.drawString(image, "PEAK", 5, 54, MUTED, 1);
        BitmapFont.drawString(image, compact(peak), 34, 54, TEXT, 1);
        BitmapFont.drawRight(image, "CMP " + be.getAnalogOutputSignal(), W - 5, 54, WARN, 1);
        BitmapFont.drawString(image, "TOTAL", 5, 64, MUTED, 1);
        BitmapFont.drawRight(image, compact(be.getTotalFe()), W - 5, 64, TEXT, 1);

        drawTrace(image, entry, be);

        rect(image, 0, 0, W, H, RULE);
        sheen(image);
    }

    private static void drawFault(NativeImage image, PowerMeterBE.Status state) {
        final String[] lines = switch (state) {
            case LOOPED -> new String[]{"LOOPED", "ONE CIRCUIT ON BOTH", "TERMINALS"};
            case NO_SUPPLY -> new String[]{"NO SUPPLY", "NOTHING WIRED TO THE", "BRASS TERMINAL"};
            default -> new String[]{"NO LOAD", "NOTHING ON THE GLAND", "END TO FEED"};
        };
        BitmapFont.drawCentered(image, lines[0], 0, 28, W, BAD, 2);
        BitmapFont.drawCentered(image, lines[1], 0, 50, W, WARN, 1);
        BitmapFont.drawCentered(image, lines[2], 0, 60, W, WARN, 1);
        for (int x = 6; x < W - 6; x += 8) {
            fill(image, x, 74, 4, 3, x % 16 == 6 ? BAD : GLOW);
        }
    }

    private static void drawTrace(NativeImage image, Entry entry, PowerMeterBE be) {
        final int x0 = 5;
        final int y0 = 74;
        final int w = W - 10;
        final int h = 10;
        fill(image, x0, y0, w, h, 0xFF0B1A13);
        rect(image, x0, y0, w, h, RULE);
        if (entry.traceFilled == 0) {
            return;
        }

        int scale = 1;
        for (int i = 0; i < entry.traceFilled; i++) {
            scale = Math.max(scale, entry.trace[i]);
        }

        final int columns = Math.min(entry.traceFilled, w - 2);
        for (int c = 0; c < columns; c++) {
            final int age = columns - 1 - c;
            final int index = ((entry.traceHead - 1 - age) % TRACE_SAMPLES + TRACE_SAMPLES) % TRACE_SAMPLES;
            final int value = entry.trace[index];
            final int height = Math.max(1, Math.round((h - 2) * (value / (float) scale)));
            fill(image, x0 + 1 + c, y0 + h - 1 - height, 1, height, TEXT);
        }
        if (be.getRateFePerTick() != 0) {
            vline(image, x0 + columns, y0 + 1, y0 + h - 1, MUTED);
        }
    }

    private static void bar(NativeImage image, int x, int y, int w, int h, float fraction,
                            int colour, String label) {
        fill(image, x, y, w, h, 0xFF0B1A13);
        final int inner = w - 30;
        fill(image, x + 29, y + 1, Math.max(0, Math.min(inner, Math.round(inner * fraction))), h - 2, colour);
        rect(image, x, y, w, h, RULE);
        vline(image, x + 28, y, y + h, RULE);
        BitmapFont.drawString(image, label, x + 2, y + 1, MUTED, 1);
    }

    private static void sheen(NativeImage image) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                final int d = Math.abs((x + y * 2) - 74);
                if (d > 10) {
                    continue;
                }
                final float lift = (1.0f - d / 10.0f) * 0.14f;
                image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(lighten(
                        BitmapFont.argbToAbgr(image.getPixelRGBA(x, y)), lift)));
            }
        }
    }

    private static int lighten(int argb, float amount) {
        final int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) + 255 * amount));
        final int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) + 255 * amount));
        final int b = Math.min(255, Math.round((argb & 0xFF) + 255 * amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String compact(long value) {
        final long magnitude = Math.abs(value);
        if (magnitude < 10_000L) {
            return String.valueOf(value);
        }
        if (magnitude < 1_000_000L) {
            return String.format(Locale.ROOT, "%dK", value / 1000L);
        }
        if (magnitude < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000.0);
    }

    private static void fill(NativeImage image, int x, int y, int w, int h, int argb) {
        final int abgr = BitmapFont.argbToAbgr(argb);
        for (int py = Math.max(0, y); py < Math.min(image.getHeight(), y + h); py++) {
            for (int pxl = Math.max(0, x); pxl < Math.min(image.getWidth(), x + w); pxl++) {
                image.setPixelRGBA(pxl, py, abgr);
            }
        }
    }

    private static void hline(NativeImage image, int x0, int y, int x1, int argb) {
        fill(image, x0, y, x1 - x0, 1, argb);
    }

    private static void vline(NativeImage image, int x, int y0, int y1, int argb) {
        fill(image, x, y0, 1, y1 - y0, argb);
    }

    private static void rect(NativeImage image, int x, int y, int w, int h, int argb) {
        hline(image, x, y, x + w, argb);
        hline(image, x, y + h - 1, x + w, argb);
        vline(image, x, y, y + h, argb);
        vline(image, x + w - 1, y, y + h, argb);
    }

    private static Entry create(BlockPos pos) {
        try {
            final NativeImage image = new NativeImage(W, H, false);
            final DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/power_meter_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                    texture);
            entry.lastAccessTick = gameTime();
            return entry;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long gameTime() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    private static void cleanupStale(long now) {
        final Iterator<Map.Entry<BlockPos, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            final Entry entry = it.next().getValue();
            if (now - entry.lastAccessTick > MAX_IDLE_TICKS) {
                dispose(entry);
                it.remove();
            }
        }
    }

    private static void dispose(Entry entry) {
        try {
            Minecraft.getInstance().getTextureManager().release(entry.location);
            entry.texture.close();
        } catch (Exception ignored) {
        }
    }

    public static void disposeAll() {
        for (Entry entry : ENTRIES.values()) {
            dispose(entry);
        }
        ENTRIES.clear();
    }
}
