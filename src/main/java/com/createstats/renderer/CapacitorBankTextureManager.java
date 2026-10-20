package com.createstats.renderer;

import com.createstats.blockentity.CapacitorBankBE;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// rasterised on change. it used to emit everything 4x per frame
@OnlyIn(Dist.CLIENT)
public final class CapacitorBankTextureManager {
    private static final int TEXELS_PER_BLOCK = 128;

    private static final int MAX_TEXTURE_DIMENSION = 1024;

    private static final int MIN_REDRAW_TICKS = 2;

    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final float MARGIN = 0.10f;
    private static final float BEZEL = 0.055f;

    private static final float LINE = 0.115f;

    private static final float GAP = 0.07f;

    private static final String[] TITLES = {"CAPACITOR BANK", "CAP. BANK", "BANK"};

    private static final int COL_WELL     = 0xFF070A0E;
    private static final int COL_PANEL    = 0xFF10161D;
    private static final int COL_GAP      = 0xFF0D1218;
    private static final int COL_BEZEL    = 0xFF6E5530;
    private static final int COL_BEZEL_LIT= 0xFF9C7A42;
    private static final int COL_SCREW    = 0xFF3D4653;
    private static final int COL_EMPTY    = 0xFF171E26;
    private static final int COL_HEADER   = 0xFF1A222C;
    private static final int COL_LABEL    = 0xFF6E8093;
    private static final int COL_VALUE    = 0xFFD8E4EE;
    private static final int COL_RULE     = 0xFF2A3541;
    private static final int COL_LOUVER   = 0xFF141A21;
    private static final int COL_LOUVER_EDGE = 0xFF2B3642;

    private record Key(BlockPos controller, int faceWidth) {
    }

    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private CapacitorBankTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        int faceWidth;
        int height;
        long lastAccessTick;
        long lastDrawTick = FaceRedrawGate.NEVER;
        int signature = Integer.MIN_VALUE;
    }

    @Nullable
    public static ResourceLocation getOrUpdateTexture(CapacitorBankBE be, int faceWidth, int height) {
        if (faceWidth <= 0 || height <= 0) {
            return null;
        }
        final Key key = new Key(be.getBlockPos(), faceWidth);
        Entry entry = ENTRIES.get(key);

        if (entry != null && entry.height != height) {
            dispose(entry);
            ENTRIES.remove(key);
            entry = null;
        }
        if (entry == null) {
            entry = create(be.getBlockPos(), faceWidth, height);
            if (entry == null) {
                return null;
            }
            ENTRIES.put(key, entry);
        }
        final long now = gameTime();
        entry.lastAccessTick = now;

        final Face face = read(be);
        final int signature = signatureOf(face);
        if (signature != entry.signature
                && FaceRedrawGate.due(now, entry.lastDrawTick, MIN_REDRAW_TICKS)) {
            entry.signature = signature;
            entry.lastDrawTick = now;
            drawFace(new FaceCanvas(entry.image, 0.0f, 0.0f, faceWidth, height), face,
                    faceWidth, height);
            entry.texture.upload();
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    record Face(float fill, int stored, int capacity, int rate, int in, int out,
                int enduranceTicks, boolean managed, int managedSettlePct, int managedFullTicks) {
        Face(float fill, int stored, int capacity, int rate, int in, int out, int enduranceTicks) {
            this(fill, stored, capacity, rate, in, out, enduranceTicks, false, -1, -1);
        }
    }

    static Face read(CapacitorBankBE be) {
        return new Face(be.groupFillFraction(), be.groupEnergy(), be.groupCapacity(),
                be.chargeRatePerTick(), be.chargeFePerTick(), be.dischargeFePerTick(),
                be.enduranceTicks(), be.isManaged(), be.managedSettlePct(), be.managedFullInTicks());
    }

    static int signatureOf(Face face) {
        int result = Math.round(Mth.clamp(face.fill(), 0.0f, 1.0f) * 1024.0f);
        result = result * 31 + compactKey(face.stored());
        result = result * 31 + compactKey(face.capacity());

        result = result * 31 + Integer.signum(face.rate());
        result = result * 31 + compactKey(Math.abs(face.rate()));
        result = result * 31 + Integer.signum(face.in());
        result = result * 31 + compactKey(face.in());
        result = result * 31 + Integer.signum(face.out());
        result = result * 31 + compactKey(face.out());

        result = result * 31 + (face.enduranceTicks() < 0 ? -1 : face.enduranceTicks() / 20);

        result = result * 31 + (face.managed() ? 1 : 0);
        result = result * 31 + face.managedSettlePct();
        result = result * 31 + (face.managedFullTicks() < 0 ? -1 : face.managedFullTicks() / 20);
        return result;
    }

    // ---------- Composition ----------

    static void drawFace(FaceCanvas c, Face face, int faceWidth, int height) {
        c.clear(0);

        final float fill = face.fill();
        final int colour = chargeColour(fill);
        final float w = faceWidth;
        final float h = height;

        final float b0 = MARGIN;
        final float i0 = MARGIN + BEZEL;
        final float i1x = w - MARGIN - BEZEL;
        final float i1y = h - MARGIN - BEZEL;
        if (i1x <= i0 || i1y <= i0) {
            return;
        }
        final int bezel = fill > 0.02f ? COL_BEZEL_LIT : COL_BEZEL;

        c.quad(b0, b0, w - MARGIN, i0, bezel);
        c.quad(b0, i1y, w - MARGIN, h - MARGIN, bezel);
        c.quad(b0, i0, i0, i1y, bezel);
        c.quad(i1x, i0, w - MARGIN, i1y, bezel);

        final float s = Math.min(0.035f, BEZEL * 0.5f);
        final float sc = (MARGIN + i0) * 0.5f - s * 0.5f;
        c.quad(sc, sc, sc + s, sc + s, COL_SCREW);
        c.quad(w - sc - s, sc, w - sc, sc + s, COL_SCREW);
        c.quad(sc, h - sc - s, sc + s, h - sc, COL_SCREW);
        c.quad(w - sc - s, h - sc - s, w - sc, h - sc, COL_SCREW);

        if (faceWidth <= 1) {
            drawColumn(c, i0, i0, i1x, i1y, fill, colour);
            return;
        }

        final float spineW = Mth.clamp((i1x - i0) * 0.13f, 0.16f, 0.30f);
        drawColumn(c, i0, i0, i0 + spineW, i1y, fill, colour);
        final float gutter = 0.04f;
        c.quad(i0 + spineW, i0, i0 + spineW + gutter, i1y, COL_WELL);

        drawInstrument(c, face, i0 + spineW + gutter, i0, i1x, i1y, faceWidth, height, fill, colour);
    }

    private static void drawInstrument(FaceCanvas c, Face face,
                                       float u0, float v0, float u1, float v1,
                                       int faceWidth, int height, float fill, int colour) {
        if (u1 - u0 < 0.4f) {
            drawLouvers(c, u0, v0, u1, v1);
            return;
        }
        final int rate = face.rate();
        final int stored = face.stored();

        final float headerH = Math.min(0.20f, (v1 - v0) * 0.16f);
        c.quad(u0, v1 - headerH, u1, v1, COL_HEADER);
        final int lampColour = stored <= 0 ? 0xFFFF4A4A
                : rate > 0 ? 0xFF58DC7A
                : rate < 0 ? 0xFFFFBA3C
                : 0xFF4C7BA8;
        final float lamp = Math.min(0.07f, headerH * 0.45f);
        final float lampY = v1 - headerH * 0.5f - lamp * 0.5f;
        c.quad(u1 - 0.05f - lamp, lampY, u1 - 0.05f, lampY + lamp, lampColour);

        final float titleScale = Math.min(LINE, headerH * 0.72f) / 9.0f;
        final float titleRoom = (u1 - u0) - 0.10f - lamp - GAP;
        textFitted(c, TITLES, u0 + 0.05f, v1 - headerH + 0.045f, titleRoom, COL_LABEL, titleScale);
        final float contentTop = v1 - headerH - 0.014f;
        c.quad(u0, contentTop, u1, v1 - headerH, COL_RULE);

        final float available = contentTop - v0;
        final float moduleH = Mth.clamp(available * 0.55f, 0.50f, 1.05f);
        final float slack = Math.max(0.0f, available - moduleH);
        final float moduleBottom = v0 + Math.min(slack, slack * 0.34f);
        final float moduleTop = Math.min(contentTop, moduleBottom + moduleH);

        drawLouvers(c, u0, moduleTop, u1, contentTop);
        if (faceWidth >= 3 && moduleBottom - v0 > 0.22f) {
            drawDistribution(c, u0, v0, u1, moduleBottom, height, fill, colour);
        } else {
            drawLouvers(c, u0, v0, u1, moduleBottom);
        }

        drawModule(c, face, u0, moduleBottom, u1, moduleTop, fill, colour);
    }

    private static void drawModule(FaceCanvas c, Face face,
                                   float u0, float v0, float u1, float v1, float fill, int colour) {
        if (v1 - v0 < 0.2f) {
            return;
        }

        final float lip = 0.022f;
        c.quad(u0, v0, u1, v0 + lip, COL_RULE);
        c.quad(u0, v1 - lip, u1, v1, COL_RULE);
        c.quad(u0, v0 + lip, u0 + lip, v1 - lip, COL_RULE);
        c.quad(u1 - lip, v0 + lip, u1, v1 - lip, COL_RULE);
        final float su0 = u0 + lip;
        final float sv0 = v0 + lip;
        final float su1 = u1 - lip;
        final float sv1 = v1 - lip;

        final int stored = face.stored();
        final int capacity = face.capacity();
        final int rate = face.rate();
        final int endurance = face.enduranceTicks();
        final float pad = 0.05f;
        final float innerH = sv1 - sv0;

        final float bigH = Mth.clamp(innerH * 0.38f, 0.16f, 0.30f);
        final float barH = Mth.clamp(innerH * 0.16f, 0.07f, 0.15f);
        final float barTop = sv1 - pad - bigH - 0.035f;
        final float barBottom = barTop - barH;
        final boolean showBar = barBottom > sv0 + 0.02f;

        if (showBar) {
            c.quad(su0, barTop, su1, sv1, COL_PANEL);
            c.quad(su0, barBottom, su0 + pad, barTop, COL_PANEL);
            drawQuarteredBar(c, su0 + pad, barBottom, su1 - pad, barTop, fill, colour);
            c.quad(su1 - pad, barBottom, su1, barTop, COL_PANEL);
            c.quad(su0, sv0, su1, barBottom, COL_PANEL);
        } else {
            c.quad(su0, sv0, su1, sv1, COL_PANEL);
        }

        final float lineScale = LINE / 9.0f;
        final float bigScale = bigH / 9.0f;
        final float left = su0 + pad;
        final float right = su1 - pad;
        final float innerW = right - left;

        final String pct = String.format(Locale.ROOT, "%.0f%%", fill * 100.0f);
        final float pctW = Math.min(FaceCanvas.width(pct, bigScale), innerW);
        textFitted(c, new String[] {pct}, left, sv1 - pad - bigH, innerW, colour, bigScale);

        final String amount = compact(stored) + " / " + compact(capacity) + " FE";
        final float amountW = FaceCanvas.width(amount, lineScale);
        final float besideX = left + pctW + GAP;
        final float ownRow = barBottom - LINE - 0.02f;
        final boolean ownRowFits = showBar && ownRow > sv0 + pad * 0.6f + LINE + 0.03f;
        if (amountW <= right - besideX) {
            c.text(amount, besideX, sv1 - pad - bigH * 0.78f, COL_VALUE, lineScale);
        } else if (ownRowFits) {
            textFitted(c, new String[] {amount, compact(stored) + " / " + compact(capacity)},
                    left, ownRow, innerW, COL_VALUE, lineScale);
        } else {
            textFitted(c, new String[] {amount,
                    compact(stored) + " / " + compact(capacity), compact(stored)},
                    besideX, sv1 - pad - bigH * 0.78f, right - besideX, COL_VALUE, lineScale);
        }

        if (barBottom - LINE <= sv0) {
            return;
        }

        final float row = sv0 + pad * 0.6f;

        final int in = face.in();
        final int out = face.out();
        final boolean passing = in > 0 && out > 0;
        final int flowColour = passing ? 0xFF4C7BA8
                : rate > 0 ? 0xFF58DC7A : rate < 0 ? 0xFFFFBA3C : COL_LABEL;
        final String flowLong = passing
                ? "IN " + compact(in) + " OUT " + compact(out)
                : rate == 0 ? "IDLE"
                        : (rate > 0 ? "CHARGE +" : "DRAW -") + compact(Math.abs(rate)) + " FE/t";
        final String flowShort = passing
                ? compact(in) + "/" + compact(out)
                : rate == 0 ? "IDLE"
                        : (rate > 0 ? "+" : "-") + compact(Math.abs(rate)) + " FE/t";

        final String time = face.managed()
                ? face.managedFullTicks() > 0 ? "FULL " + clock(face.managedFullTicks())
                        : face.managedSettlePct() >= 0 ? "SETTLE " + face.managedSettlePct() + "%" : null
                : endurance < 0 ? null : (rate > 0 ? "FULL " : "EMPTY ") + clock(endurance);
        final float timeW = time == null ? 0.0f : FaceCanvas.width(time, lineScale);
        final String shared = time == null ? null
                : fit(new String[] {flowLong, flowShort}, innerW - timeW - GAP, lineScale);
        if (shared != null) {
            c.text(shared, left, row, flowColour, lineScale);
            c.text(time, right - timeW, row, COL_LABEL, lineScale);
        } else {
            textFitted(c, new String[] {flowLong, flowShort}, left, row, innerW, flowColour, lineScale);
        }
    }

    private static void drawLouvers(FaceCanvas c, float u0, float v0, float u1, float v1) {
        if (v1 - v0 < 0.06f || u1 <= u0) {
            return;
        }
        final int slats = Math.max(1, Math.round((v1 - v0) / 0.16f));
        final float slatH = (v1 - v0) / slats;
        final float edge = Math.min(0.028f, slatH * 0.3f);
        for (int i = 0; i < slats; i++) {
            final float base = v0 + i * slatH;
            c.quad(u0, base, u1, base + edge, COL_LOUVER_EDGE);
            c.quad(u0, base + edge, u1, base + slatH, COL_LOUVER);
        }
    }

    private static void drawColumn(FaceCanvas c, float u0, float v0, float u1, float v1,
                                   float fill, int colour) {
        final int cells = Math.max(4, Math.round((v1 - v0) * 4.0f));
        final float cellH = (v1 - v0) / cells;
        final float gap = Math.min(cellH * 0.22f, 0.03f);
        final float litCells = fill * cells;

        for (int i = 0; i < cells; i++) {
            final float base = v0 + i * cellH;
            final float bodyTop = base + cellH - gap;
            final boolean lit = i < (int) litCells;
            final boolean partial = !lit && i == (int) litCells && litCells % 1.0f > 0.05f;
            c.quad(u0, base, u1, bodyTop, lit ? colour : partial ? dim(colour, 0.4f) : COL_EMPTY);
            c.quad(u0, bodyTop, u1, base + cellH, COL_GAP);
        }
    }

    private static void drawQuarteredBar(FaceCanvas c, float u0, float v0, float u1, float v1,
                                         float fill, int colour) {
        final float tick = Math.min(0.03f, (u1 - u0) * 0.012f);
        final float quarterW = ((u1 - u0) - tick * 3.0f) / 4.0f;
        float x = u0;
        for (int q = 0; q < 4; q++) {
            final float qEnd = x + quarterW;
            final float local = Mth.clamp(fill * 4.0f - q, 0.0f, 1.0f);
            final float litEnd = x + quarterW * local;
            c.quad(x, v0, litEnd, v1, colour);
            c.quad(litEnd, v0, qEnd, v1, COL_EMPTY);
            x = qEnd;
            if (q < 3) {
                c.quad(x, v0, x + tick, v1, COL_WELL);
                x += tick;
            }
        }
    }

    private static void drawDistribution(FaceCanvas c, float u0, float v0, float u1, float v1,
                                         int layers, float fill, int colour) {
        final float inner = 0.02f;
        final float gu0 = u0 + inner;
        final float gu1 = u1 - inner;
        final float gv0 = v0 + inner;
        final float gv1 = v1 - inner;
        if (gu1 <= gu0 || gv1 <= gv0 || layers <= 0) {
            c.quad(u0, v0, u1, v1, COL_WELL);
            return;
        }

        c.quad(u0, v0, u1, gv0, COL_WELL);
        c.quad(u0, gv1, u1, v1, COL_WELL);
        c.quad(u0, gv0, gu0, gv1, COL_WELL);
        c.quad(gu1, gv0, u1, gv1, COL_WELL);
        final float colGap = Math.min(0.04f, (gu1 - gu0) / (layers * 5.0f));
        final float colW = ((gu1 - gu0) - colGap * (layers - 1)) / layers;
        float cx = gu0;
        for (int i = 0; i < layers; i++) {
            final float cEnd = cx + colW;
            final float layerFill = Mth.clamp(fill * layers - i, 0.0f, 1.0f);
            final float top = gv0 + (gv1 - gv0) * layerFill;
            c.quad(cx, gv0, cEnd, top, dim(colour, 0.85f));
            c.quad(cx, top, cEnd, gv1, COL_EMPTY);
            cx = cEnd;
            if (i < layers - 1) {
                c.quad(cx, gv0, cx + colGap, gv1, COL_WELL);
                cx += colGap;
            }
        }
    }

    private static void textFitted(FaceCanvas c, String[] options, float u, float v,
                                   float maxWidth, int argb, float scale) {
        if (options.length == 0 || maxWidth <= 0.0f) {
            return;
        }
        final String chosen = fitOrLast(options, maxWidth, scale);
        final float w = FaceCanvas.width(chosen, scale);
        c.text(chosen, u, v, argb, w > maxWidth ? scale * (maxWidth / w) : scale);
    }

    @Nullable
    private static String fit(String[] options, float maxWidth, float scale) {
        for (String option : options) {
            if (FaceCanvas.width(option, scale) <= maxWidth) {
                return option;
            }
        }
        return null;
    }

    private static String fitOrLast(String[] options, float maxWidth, float scale) {
        final String fitted = fit(options, maxWidth, scale);
        return fitted != null ? fitted : options[options.length - 1];
    }

    static String compact(int value) {
        if (value >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fG", value / 1.0e9);
        }
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fM", value / 1.0e6);
        }
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", value / 1.0e3);
        }
        if (value >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1.0e3);
        }
        return String.valueOf(value);
    }

    // key on what compact() prints, raw FE redraws every tick and String.format per frame is what we're avoiding
    static int compactKey(int value) {
        if (value >= 1_000_000_000) {
            return 200_000 + (int) Math.round(value / 1.0e8);
        }
        if (value >= 1_000_000) {
            return 20_000 + (int) Math.round(value / 1.0e4);
        }
        if (value >= 10_000) {
            return 4_000 + (int) Math.round(value / 1.0e3);
        }
        if (value >= 1_000) {
            return 1_000 + (int) Math.round(value / 1.0e2);
        }
        return value;
    }

    private static String clock(int ticks) {
        final int seconds = ticks / 20;
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%dh%02dm", seconds / 3600, (seconds % 3600) / 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static int chargeColour(float fill) {
        if (fill < 0.2f) {
            return 0xFFFF4A4A;
        }
        if (fill < 0.5f) {
            return 0xFFFFBA3C;
        }
        return 0xFF58DC7A;
    }

    private static int dim(int argb, float factor) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Math.round(((argb >> 16) & 0xFF) * factor);
        final int g = Math.round(((argb >> 8) & 0xFF) * factor);
        final int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Nullable
    private static Entry create(BlockPos pos, int faceWidth, int height) {
        final int texW = Math.min(MAX_TEXTURE_DIMENSION, faceWidth * TEXELS_PER_BLOCK);
        final int texH = Math.min(MAX_TEXTURE_DIMENSION, height * TEXELS_PER_BLOCK);
        try {
            final NativeImage image = new NativeImage(texW, texH, false);

            new FaceCanvas(image, 0.0f, 0.0f, faceWidth, height).clear(0);
            final DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.faceWidth = faceWidth;
            entry.height = height;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/capacitor_bank_face_" + pos.getX() + "_" + pos.getY() + "_"
                            + pos.getZ() + "_" + faceWidth,
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
        final Iterator<Map.Entry<Key, Entry>> it = ENTRIES.entrySet().iterator();
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
