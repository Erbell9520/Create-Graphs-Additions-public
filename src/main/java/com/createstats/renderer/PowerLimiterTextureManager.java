package com.createstats.renderer;

import com.createstats.blockentity.PowerLimiterBE;
import com.createstats.power.LimiterLadder;
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
public final class PowerLimiterTextureManager {
    private static final int W = 144;
    private static final int H = 96;

    private static final int TRACE_INTERVAL_TICKS = 10;

    private static final int TRACE_SAMPLES = 132;

    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final int BACK = 0xFF06110C;
    private static final int GLOW = 0xFF0C1E15;
    private static final int GRID = 0xFF122E20;
    private static final int RULE = 0xFF27604A;
    private static final int TEXT = 0xFFB8F5B5;
    private static final int BRIGHT = 0xFFE4FFDC;
    private static final int MUTED = 0xFF5F8F73;
    private static final int AMBER = 0xFFFFD56A;
    private static final int BAD = 0xFFFF5D49;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private PowerLimiterTextureManager() {
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

    public static ResourceLocation getOrUpdateTexture(PowerLimiterBE be) {
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

    private static int signature(PowerLimiterBE be, long traceEpoch) {
        int result = be.getRateFePerTick();
        result = result * 31 + be.effectiveLimitFePerTick();
        result = result * 31 + be.limitFePerTick();
        result = result * 31 + be.mode().ordinal();
        result = result * 31 + be.redstoneStrength();
        result = result * 31 + be.status().ordinal();
        result = result * 31 + Long.hashCode(be.getTotalFe());
        result = result * 31 + Long.hashCode(traceEpoch);
        return result;
    }

    // ---------- Drawing ----------

    private static void draw(NativeImage image, PowerLimiterBE be, Entry entry) {
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
        BitmapFont.drawString(image, "POWER LIMITER", 4, 3, MUTED, 1);

        final PowerLimiterBE.Status state = be.status();
        if (state == PowerLimiterBE.Status.LOOPED || state == PowerLimiterBE.Status.NO_SUPPLY
                || state == PowerLimiterBE.Status.NO_LOAD) {
            BitmapFont.drawRight(image, state == PowerLimiterBE.Status.LOOPED ? "LOOP" : "NC", W - 4, 3,
                    BAD, 1);
            drawFault(image, state);
            rect(image, 0, 0, W, H, RULE);
            sheen(image);
            return;
        }

        final int rate = be.getRateFePerTick();
        final int cap = be.effectiveLimitFePerTick();
        final int accent = switch (state) {
            case LIMITING -> AMBER;
            case PASSING -> BRIGHT;
            case SHUT -> BAD;
            default -> MUTED;
        };
        BitmapFont.drawRight(image, switch (state) {
            case LIMITING -> "LIM";
            case PASSING -> "PASS";
            case SHUT -> "SHUT";
            default -> "IDLE";
        }, W - 4, 3, accent, 1);

        BitmapFont.drawString(image, compact(rate), 5, 16, accent, 2);
        BitmapFont.drawString(image, "FE/T", 5, 33, MUTED, 1);

        BitmapFont.drawRight(image, "CAP " + compact(cap), W - 5, 33, AMBER, 1);

        final float load = cap <= 0 ? 0.0f : Math.min(1.0f, rate / (float) cap);
        bar(image, 5, 43, W - 10, 9, load, accent, "LOAD");

        drawModeRow(image, be);

        BitmapFont.drawString(image, "TOTAL", 5, 68, MUTED, 1);
        BitmapFont.drawRight(image, compact(be.getTotalFe()), W - 5, 68, TEXT, 1);

        drawTrace(image, entry, be, cap);

        rect(image, 0, 0, W, H, RULE);
        sheen(image);
    }

    private static void drawModeRow(NativeImage image, PowerLimiterBE be) {
        BitmapFont.drawString(image, "RS", 5, 57, MUTED, 1);
        if (be.mode() == com.createstats.power.LimiterMode.ALWAYS) {
            BitmapFont.drawString(image, "OFF", 22, 57, MUTED, 1);
            BitmapFont.drawRight(image, "SET " + String.format(Locale.ROOT, "%,d", be.limitFePerTick()),
                    W - 5, 57, MUTED, 1);
            return;
        }
        final String label = switch (be.mode()) {
            case SIGNAL_OPENS -> "ON=OPEN";
            case SIGNAL_SHUTS -> "ON=SHUT";
            default -> "ANALOG";
        };
        BitmapFont.drawString(image, label, 22, 57, AMBER, 1);
        BitmapFont.drawRight(image, be.redstoneStrength() + "/15", W - 5, 57,
                be.redstoneStrength() > 0 ? AMBER : MUTED, 1);
    }

    private static void drawFault(NativeImage image, PowerLimiterBE.Status state) {
        final String[] lines = switch (state) {
            case LOOPED -> new String[]{"LOOPED", "ONE CIRCUIT ON BOTH", "TERMINALS"};
            case NO_SUPPLY -> new String[]{"NO SUPPLY", "NOTHING WIRED TO THE", "BRASS TERMINAL"};
            default -> new String[]{"NO LOAD", "NOTHING ON THE GLAND", "END TO FEED"};
        };
        BitmapFont.drawCentered(image, lines[0], 0, 30, W, BAD, 2);
        BitmapFont.drawCentered(image, lines[1], 0, 54, W, AMBER, 1);
        BitmapFont.drawCentered(image, lines[2], 0, 64, W, AMBER, 1);
        for (int x = 6; x < W - 6; x += 8) {
            fill(image, x, 80, 4, 3, x % 16 == 6 ? BAD : GLOW);
        }
    }

    private static void drawTrace(NativeImage image, Entry entry, PowerLimiterBE be, int cap) {
        final int x0 = 5;
        final int y0 = 78;
        final int w = W - 10;
        final int h = 13;
        fill(image, x0, y0, w, h, 0xFF0B1A13);
        rect(image, x0, y0, w, h, RULE);
        if (entry.traceFilled == 0) {
            return;
        }

        int scale = Math.max(1, cap);
        for (int i = 0; i < entry.traceFilled; i++) {
            scale = Math.max(scale, entry.trace[i]);
        }

        final int columns = Math.min(entry.traceFilled, w - 2);
        for (int c = 0; c < columns; c++) {
            final int age = columns - 1 - c;
            final int index = ((entry.traceHead - 1 - age) % TRACE_SAMPLES + TRACE_SAMPLES) % TRACE_SAMPLES;
            final int value = entry.trace[index];
            if (value <= 0) {
                continue;
            }
            final int height = Math.max(1, Math.round((h - 2) * (value / (float) scale)));
            fill(image, x0 + 1 + c, y0 + h - 1 - height, 1, height,
                    cap > 0 && value >= cap ? AMBER : TEXT);
        }
        if (cap > 0 && cap < scale) {
            final int capY = y0 + h - 1 - Math.round((h - 2) * (cap / (float) scale));
            for (int x = x0 + 1; x < x0 + w - 1; x += 3) {
                fill(image, x, capY, 2, 1, AMBER);
            }
        }
    }

    private static String compact(long fe) {
        return LimiterLadder.compact(fe).toUpperCase(Locale.ROOT);
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
                final int d = Math.abs((x + y * 2) - 82);
                if (d > 11) {
                    continue;
                }
                final float lift = (1.0f - d / 11.0f) * 0.13f;
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
                    "create_stats/power_limiter_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
