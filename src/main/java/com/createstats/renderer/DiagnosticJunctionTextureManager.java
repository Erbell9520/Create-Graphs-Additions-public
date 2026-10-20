package com.createstats.renderer;

import com.createstats.block.DiagnosticJunctionBlock;
import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.power.DiagnosticJunctionStuds;
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

// 128x64 to match the glass aspect, a square texture squashed every glyph
@OnlyIn(Dist.CLIENT)
public final class DiagnosticJunctionTextureManager {
    static final int W = 128;
    static final int H = 64;
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
    private static final int HOT = 0xFFF09A3C;
    private static final int BAD = 0xFFFF5D49;
    private static final int COOL = 0xFF63D0E4;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private DiagnosticJunctionTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        int signature = Integer.MIN_VALUE;
    }

    public static ResourceLocation getOrUpdateTexture(DiagnosticJunctionBE be) {
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
        final int signature = signature(be);
        if (signature != entry.signature) {
            entry.signature = signature;
            draw(entry.image, face(be));
            entry.texture.upload();
        }
        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static int signature(DiagnosticJunctionBE be) {
        int result = be.kind().ordinal();
        result = result * 31 + be.flow();
        result = result * 31 + be.capacity();
        result = result * 31 + be.buffer();
        result = result * 31 + be.maxBuffer();
        result = result * 31 + be.fault().ordinal();
        result = result * 31 + (be.isMerged() ? 1 : 0);
        result = result * 31 + (be.isCutOff() ? 1 : 0);
        for (int i = 0; i < DiagnosticJunctionStuds.CHANNELS; i++) {
            result = result * 31 + be.channel(i).ordinal();
            result = result * 31 + be.channelFlow(i);
            result = result * 31 + be.channelCapacity(i);
            result = result * 31 + be.channelBuffer(i);
        }
        return result;
    }

    // ---------- Drawing ----------

    record Face(boolean wire, int flow, int capacity, int buffer, int maxBuffer, DiagnosticJunctionBE.Fault fault,
                boolean merged, boolean cutOff, DiagnosticJunctionBE.Fault[] channels, int[] channelFlows,
                int[] channelCapacities) {
        DiagnosticJunctionBE.Fault channel(int index) {
            return index >= 0 && index < channels.length ? channels[index] : DiagnosticJunctionBE.Fault.NO_BUS;
        }

        int channelFlow(int index) {
            return index >= 0 && index < channelFlows.length ? channelFlows[index] : 0;
        }

        int channelCapacity(int index) {
            return index >= 0 && index < channelCapacities.length ? channelCapacities[index] : 0;
        }

        float loadFraction() {
            return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, flow / (float) capacity));
        }
    }

    static Face face(DiagnosticJunctionBE be) {
        final int n = DiagnosticJunctionStuds.CHANNELS;
        final DiagnosticJunctionBE.Fault[] channels = new DiagnosticJunctionBE.Fault[n];
        final int[] flows = new int[n];
        final int[] capacities = new int[n];
        for (int i = 0; i < n; i++) {
            channels[i] = be.channel(i);
            flows[i] = be.channelFlow(i);
            capacities[i] = be.channelCapacity(i);
        }
        return new Face(be.kind() == DiagnosticJunctionBlock.Kind.WIRE, be.flow(), be.capacity(), be.buffer(),
                be.maxBuffer(), be.fault(), be.isMerged(), be.isCutOff(), channels, flows, capacities);
    }

    static void draw(NativeImage image, Face be) {
        final boolean wire = be.wire();
        fill(image, 0, 0, W, H, BACK);

        for (int y = 14; y < H; y += 9) {
            hline(image, 3, y, W - 3, GRID);
        }
        for (int x = 8; x < W; x += 12) {
            vline(image, x, 14, H - 3, GRID);
        }
        fill(image, 20, 18, W - 40, H - 34, GLOW);

        fill(image, 0, 0, W, 12, GLOW);
        hline(image, 0, 12, W, RULE);
        BitmapFont.drawString(image, wire ? "WIRE TERMINAL" : "CABLE JUNCTION", 4, 3, MUTED, 1);
        final int code = colourOf(be.fault());
        BitmapFont.drawRight(image, be.fault().code(), W - 4, 3, code, 1);

        if (be.cutOff()) {
            drawIsolated(image);
        } else if (wire) {
            drawWire(image, be);
        } else {
            drawCable(image, be);
        }

        rect(image, 0, 0, W, H, RULE);
        sheen(image);
    }

    private static void drawIsolated(NativeImage image) {
        BitmapFont.drawString(image, "ISOLATED", 26, 22, BAD, 2);
        BitmapFont.drawString(image, "HANDLE OPEN, NOTHING PASSES", 10, 44, WARN, 1);
        for (int x = 6; x < W - 6; x += 8) {
            fill(image, x, 54, 4, 3, x % 16 == 6 ? BAD : GLOW);
        }
    }

    private static void drawCable(NativeImage image, Face be) {
        final boolean hot = be.fault() == DiagnosticJunctionBE.Fault.OVERLOAD;

        BitmapFont.drawString(image, compact(be.flow()), 5, 17, hot ? BAD : BRIGHT, 2);
        BitmapFont.drawString(image, "FE/t", 5, 33, MUTED, 1);
        BitmapFont.drawRight(image, "OF " + compact(be.capacity()), W - 5, 33, MUTED, 1);

        bar(image, 5, 42, W - 10, 6, be.loadFraction(), hot ? BAD : TEXT, "LOAD");
        final float fill = be.maxBuffer() <= 0 ? 0.0f
                : Math.min(1.0f, be.buffer() / (float) be.maxBuffer());
        bar(image, 5, 53, W - 10, 6, fill,
                be.fault() == DiagnosticJunctionBE.Fault.BUFFER_FULL ? HOT : COOL, "BUF");
    }

    private static void drawWire(NativeImage image, Face be) {
        if (be.merged()) {
            BitmapFont.drawString(image, "MERGED BUS", 5, 17, WARN, 1);
            BitmapFont.drawString(image, compact(be.flow()), 5, 28, BRIGHT, 2);
            BitmapFont.drawString(image, "FE/t", 5, 44, MUTED, 1);
            BitmapFont.drawRight(image, "OF " + compact(be.capacity()), W - 5, 44, MUTED, 1);
            BitmapFont.drawString(image, "ALL SIX STUDS TIED", 5, 54, MUTED, 1);
            return;
        }
        BitmapFont.drawString(image, "SEPARATE", 5, 15, MUTED, 1);
        BitmapFont.drawRight(image, "3 CIRCUITS", W - 5, 15, MUTED, 1);
        for (int channel = 0; channel < DiagnosticJunctionStuds.CHANNELS; channel++) {
            drawChannel(image, be, channel, 26 + channel * 12);
        }
    }

    private static void drawChannel(NativeImage image, Face be, int channel, int y) {
        final DiagnosticJunctionBE.Fault fault = be.channel(channel);
        final int colour = colourOf(fault);
        BitmapFont.drawString(image, String.valueOf((char) ('A' + channel)), 5, y, MUTED, 1);
        final int rate = be.channelCapacity(channel);
        final float load = rate <= 0 ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, be.channelFlow(channel) / (float) rate));
        fill(image, 14, y, 34, 7, 0xFF0B1A13);
        fill(image, 14, y, fault == DiagnosticJunctionBE.Fault.NO_BUS ? 3
                : Math.max(2, Math.round(34 * load)), 7, colour);
        rect(image, 14, y, 34, 7, RULE);
        BitmapFont.drawString(image, rate <= 0 ? "--" : compact(be.channelFlow(channel)), 52, y,
                fault == DiagnosticJunctionBE.Fault.NO_BUS ? MUTED : TEXT, 1);
        BitmapFont.drawRight(image, DiagnosticJunctionBE.shortCode(fault), W - 5, y, colour, 1);
    }

    private static void bar(NativeImage image, int x, int y, int w, int h, float fraction,
                            int colour, String label) {
        fill(image, x, y, w, h, 0xFF0B1A13);
        final int inner = w - 22;
        fill(image, x + 21, y, Math.max(0, Math.min(inner, Math.round(inner * fraction))), h, colour);
        rect(image, x, y, w, h, RULE);
        vline(image, x + 20, y, y + h, RULE);
        BitmapFont.drawString(image, label, x + 2, y, MUTED, 1);
    }

    private static void sheen(NativeImage image) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                final int d = Math.abs((x + y * 2) - 66);
                if (d > 9) {
                    continue;
                }
                final float lift = (1.0f - d / 9.0f) * 0.16f;
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

    private static String compact(int value) {
        final int magnitude = Math.abs(value);
        if (magnitude < 10_000) {
            return String.valueOf(value);
        }
        if (magnitude < 1_000_000) {
            return String.format(Locale.ROOT, "%dk", value / 1000);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0f);
    }

    private static int colourOf(DiagnosticJunctionBE.Fault fault) {
        return switch (fault) {
            case OVERLOAD, ROUTE_BLOCKED, ISOLATED -> BAD;
            case BUFFER_FULL -> HOT;
            case OPEN_CIRCUIT, SOURCE_STARVED, UNSTABLE -> WARN;
            case BACKFEED -> COOL;
            case NO_BUS -> MUTED;
            default -> TEXT;
        };
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
                    "create_stats/diagnostic_junction_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
