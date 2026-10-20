package com.createstats.renderer;

import com.createstats.blockentity.EInkPanelBE;
import com.createstats.data.MetricType;
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

// four tones, real e-ink has four
@OnlyIn(Dist.CLIENT)
public final class EInkPanelTextureManager {
    private static final int SIZE = 128;
    private static final int MARGIN = 9;

    private static final int PAPER = 0xFFE9E7E1;
    private static final int GHOST = 0xFFC0BEB8;
    private static final int MUTED = 0xFF76746F;
    private static final int INK = 0xFF1C1C1E;

    private static final int[][] FLASH_PHASES = {{3, INK}, {2, PAPER}, {2, INK}, {2, PAPER}};
    private static final int FLASH_TICKS;

    private static final int GHOST_TICKS = 24;

    private static final int HISTORY = 48;
    private static final int IDLE_REDRAW_TICKS = 10;
    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    static {
        int total = 0;
        for (int[] phase : FLASH_PHASES) {
            total += phase[0];
        }
        FLASH_TICKS = total;
    }

    private EInkPanelTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        long lastDrawTick = Long.MIN_VALUE;
        int lastDrawnPhase = Integer.MIN_VALUE;

        String metricId = "";
        String valueText = "";
        String ghostText = "";
        int ghostScale = 1;
        long changedAtTick = Long.MIN_VALUE;

        final float[] history = new float[HISTORY];
        int historyCount;
    }

    public static ResourceLocation getOrUpdateTexture(EInkPanelBE be) {
        final BlockPos pos = be.getBlockPos();
        Entry entry = ENTRIES.get(pos);
        if (entry == null) {
            entry = create(pos);
            if (entry == null) {
                return null;
            }
            ENTRIES.put(pos, entry);
        }
        final long now = gameTime();
        entry.lastAccessTick = now;

        observe(entry, be, now);
        if (shouldRedraw(entry, now)) {
            draw(entry, be, now);
            entry.lastDrawTick = now;
            entry.lastDrawnPhase = phaseOf(entry, now);
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static void observe(Entry entry, EInkPanelBE be, long now) {
        final EInkPanelBE.Reading reading = be.selectedReading();
        final String metricId = reading == null ? "" : reading.id();
        final String text = reading == null ? "" : formatValue(reading);

        if (!metricId.equals(entry.metricId)) {
            entry.historyCount = 0;
            entry.metricId = metricId;
        }
        if (text.equals(entry.valueText)) {
            return;
        }
        entry.ghostText = entry.valueText;
        entry.ghostScale = valueScale(entry.valueText);
        entry.valueText = text;
        entry.changedAtTick = now;
        if (reading != null) {
            push(entry, reading.value());
        }
    }

    private static void push(Entry entry, float value) {
        if (entry.historyCount < HISTORY) {
            entry.history[entry.historyCount++] = value;
            return;
        }
        System.arraycopy(entry.history, 1, entry.history, 0, HISTORY - 1);
        entry.history[HISTORY - 1] = value;
    }

    private static boolean shouldRedraw(Entry entry, long now) {
        if (entry.lastDrawTick == Long.MIN_VALUE) {
            return true;
        }
        if (phaseOf(entry, now) != entry.lastDrawnPhase) {
            return true;
        }
        return now - entry.lastDrawTick >= IDLE_REDRAW_TICKS;
    }

    private static int phaseOf(Entry entry, long now) {
        if (entry.changedAtTick == Long.MIN_VALUE) {
            return -1;
        }
        final long elapsed = now - entry.changedAtTick;
        if (elapsed < 0 || elapsed >= FLASH_TICKS) {
            return elapsed < FLASH_TICKS + GHOST_TICKS ? -2 : -1;
        }
        long at = 0;
        for (int i = 0; i < FLASH_PHASES.length; i++) {
            at += FLASH_PHASES[i][0];
            if (elapsed < at) {
                return i;
            }
        }
        return -1;
    }

    private static void draw(Entry entry, EInkPanelBE be, long now) {
        final NativeImage image = entry.image;
        final int phase = phaseOf(entry, now);
        if (phase >= 0) {
            fill(image, 0, 0, SIZE, SIZE, FLASH_PHASES[phase][1]);
            entry.texture.upload();
            return;
        }

        fill(image, 0, 0, SIZE, SIZE, PAPER);
        paperTooth(image);

        final EInkPanelBE.Reading reading = be.selectedReading();
        if (reading == null) {
            drawIdle(image, be);
            entry.texture.upload();
            return;
        }

        final int right = SIZE - MARGIN;

        final String header = clip(be.getSourceType().isEmpty() ? "NO SOURCE"
                : be.getSourceType().toUpperCase(java.util.Locale.ROOT), 17);
        BitmapFont.drawString(image, header, MARGIN, MARGIN, MUTED, 1);
        if (be.readingCount() > 1) {
            BitmapFont.drawRight(image, (be.selectedIndex() + 1) + "/" + be.readingCount(),
                    right, MARGIN, MUTED, 1);
        }
        hline(image, MARGIN, MARGIN + 12, right, GHOST);

        final boolean stale = be.isStale();
        BitmapFont.drawString(image, clip(reading.label(), stale ? 14 : 20), MARGIN, MARGIN + 19, INK, 1);
        if (stale) {
            BitmapFont.drawRight(image, "HOLD", right, MARGIN + 19, MUTED, 1);
        }

        final int scale = valueScale(entry.valueText);
        final int valueY = MARGIN + 34;
        BitmapFont.drawString(image, entry.valueText, MARGIN, valueY, INK, scale);

        final String unit = unitOf(reading.type());
        if (!unit.isEmpty()) {
            final int valueW = BitmapFont.measureString(entry.valueText, scale);
            final int unitY = valueY + BitmapFont.textHeight(scale) - BitmapFont.textHeight(1);
            BitmapFont.drawString(image, unit, MARGIN + valueW + 4, unitY, MUTED, 1);
        }

        final long sinceChange = now - entry.changedAtTick;
        if (!entry.ghostText.isEmpty() && sinceChange >= FLASH_TICKS
                && sinceChange < FLASH_TICKS + GHOST_TICKS) {
            BitmapFont.drawString(image, entry.ghostText, MARGIN, valueY, GHOST, entry.ghostScale);
            BitmapFont.drawString(image, entry.valueText, MARGIN, valueY, INK, scale);
        }

        hline(image, MARGIN, SIZE - MARGIN - 30, right, GHOST);
        drawHistory(image, entry, MARGIN, SIZE - MARGIN - 26, right - MARGIN, 24);
        entry.texture.upload();
    }

    private static void drawIdle(NativeImage image, EInkPanelBE be) {
        BitmapFont.drawString(image, "E-INK PANEL", MARGIN, MARGIN, MUTED, 1);
        hline(image, MARGIN, MARGIN + 12, SIZE - MARGIN, GHOST);
        BitmapFont.drawString(image, "NO DATA", MARGIN, MARGIN + 30, INK, 2);
        BitmapFont.drawString(image, "Link a Display Link", MARGIN, MARGIN + 52, MUTED, 1);
        BitmapFont.drawString(image, "to this panel.", MARGIN, MARGIN + 62, MUTED, 1);
    }

    private static void drawHistory(NativeImage image, Entry entry, int x, int y, int w, int h) {
        if (entry.historyCount < 2) {
            BitmapFont.drawString(image, "COLLECTING", x, y + h - 8, GHOST, 1);
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < entry.historyCount; i++) {
            min = Math.min(min, entry.history[i]);
            max = Math.max(max, entry.history[i]);
        }

        final float span = max - min;
        final boolean flat = span <= 1.0e-4f;

        final int count = entry.historyCount;
        for (int col = 0; col < w; col++) {
            final int idx = count == 1 ? 0 : (col * (count - 1)) / (w - 1);
            final float t = flat ? 0.5f : (entry.history[idx] - min) / span;
            final int top = y + h - 1 - Math.round(t * (h - 2));
            for (int py = top + 1; py < y + h; py++) {
                set(image, x + col, py, GHOST);
            }
            set(image, x + col, top, INK);
        }
    }

    private static String formatValue(EInkPanelBE.Reading reading) {
        final MetricType type = reading.type() == null ? MetricType.GENERIC : reading.type();
        final float value = reading.value();
        if (type == MetricType.BOOLEAN_FLAG) {
            return value != 0.0f ? "ON" : "OFF";
        }
        final float magnitude = Math.abs(value);
        if (magnitude >= 1.0e6f) {
            return trim(value / 1.0e6f) + "M";
        }
        if (magnitude >= 1.0e4f) {
            return trim(value / 1.0e3f) + "k";
        }
        if (magnitude >= 100.0f || value == Math.rint(value)) {
            return String.valueOf(Math.round(value));
        }
        return trim(value);
    }

    private static String trim(float value) {
        final String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private static String unitOf(MetricType type) {
        if (type == null || type == MetricType.BOOLEAN_FLAG) {
            return "";
        }
        return type.getUnit() == null ? "" : type.getUnit();
    }

    private static int valueScale(String text) {
        final int available = SIZE - MARGIN * 2;
        for (int scale = 5; scale > 1; scale--) {
            if (BitmapFont.measureString(text, scale) <= available) {
                return scale;
            }
        }
        return 1;
    }

    private static String clip(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static void set(NativeImage image, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(argb));
        }
    }

    private static void fill(NativeImage image, int x, int y, int w, int h, int argb) {
        final int abgr = BitmapFont.argbToAbgr(argb);
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    image.setPixelRGBA(px, py, abgr);
                }
            }
        }
    }

    private static void hline(NativeImage image, int x0, int y, int x1, int argb) {
        for (int x = x0; x < x1; x++) {
            set(image, x, y, argb);
        }
    }

    private static void paperTooth(NativeImage image) {
        for (int y = 1; y < SIZE; y += 3) {
            for (int x = (y / 3) % 3; x < SIZE; x += 3) {
                set(image, x, y, 0xFFE3E1DB);
            }
        }
    }

    private static Entry create(BlockPos pos) {
        try {
            final NativeImage image = new NativeImage(SIZE, SIZE, false);
            final DynamicTexture texture = new DynamicTexture(image);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/eink_panel_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                    texture);
            entry.lastAccessTick = gameTime();
            return entry;
        } catch (Exception ignored) {
            return null;
        }
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

    public static void disposeFor(BlockPos pos) {
        final Entry entry = ENTRIES.remove(pos);
        if (entry != null) {
            dispose(entry);
        }
    }

    private static long gameTime() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }
}
