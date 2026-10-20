package com.createstats.renderer;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class BatteryControllerTextureManager {
    private static final int TEXELS_PER_BLOCK = 256;

    private static final float U0 = BatteryControllerRenderer.SCREEN_L;
    private static final float V0 = BatteryControllerRenderer.SCREEN_B;
    private static final float U1 = BatteryControllerRenderer.SCREEN_R;
    private static final float V1 = BatteryControllerRenderer.SCREEN_T;

    private static final int W = Math.round((U1 - U0) * TEXELS_PER_BLOCK);
    private static final int H = Math.round((V1 - V0) * TEXELS_PER_BLOCK);

    private static final int MIN_REDRAW_TICKS = 2;

    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final int COL_GLASS  = 0xFF070A0E;
    private static final int COL_TROUGH = 0xFF161C24;
    private static final int COL_RULE   = 0xFF2A3541;
    private static final int COL_LABEL  = 0xFF6E8093;
    private static final int COL_VALUE  = 0xFFD8E4EE;
    private static final int COL_STEEL  = 0xFF7C8C9C;
    private static final int COL_GREEN  = 0xFF58DC7A;
    private static final int COL_AMBER  = 0xFFFFBA3C;
    private static final int COL_RED    = 0xFFFF4A4A;
    private static final int COL_INFO   = 0xFF5AA9D6;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private BatteryControllerTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        long lastDrawTick = FaceRedrawGate.NEVER;
        int signature = Integer.MIN_VALUE;
    }

    public static ResourceLocation getOrUpdateTexture(BatteryControllerBE be) {
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

        final Face face = read(be);
        final int signature = signatureOf(face, blinkPhase());
        if (signature != entry.signature
                && FaceRedrawGate.due(now, entry.lastDrawTick, MIN_REDRAW_TICKS)) {
            entry.signature = signature;
            entry.lastDrawTick = now;
            draw(new FaceCanvas(entry.image, U0, V0, U1, V1), face, blinkPhase());
            entry.texture.upload();
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static long blinkPhase() {
        return (System.currentTimeMillis() / 900L) % 2L;
    }

    record Face(int reserveCapacity, int reservePct, int rate, int enduranceTicks,
                int generation, int delivered, BatteryControllerPlan.Reason[] lamps, int horizonKind) {
        static final int HORIZON_CLOCK = 0;

        static final int HORIZON_SHORT = 1;

        static final int HORIZON_FULL = 2;

        static final int HORIZON_SETTLE = 3;

        static final int HORIZON_NONE = 4;

        Face(int reserveCapacity, int reservePct, int rate, int enduranceTicks, int generation, int delivered,
             BatteryControllerPlan.Reason[] lamps) {
            this(reserveCapacity, reservePct, rate, enduranceTicks, generation, delivered, lamps, HORIZON_CLOCK);
        }

        boolean noStorage() {
            return reserveCapacity <= 0;
        }

        float fill() {
            return noStorage() ? 0.0f : Mth.clamp(reservePct / 100.0f, 0.0f, 1.0f);
        }

        boolean anyLampBlinking() {
            for (BatteryControllerPlan.Reason reason : lamps) {
                if (reason == BatteryControllerPlan.Reason.FLOOR) {
                    return true;
                }
            }
            return false;
        }
    }

    static Face read(BatteryControllerBE be) {
        final BatteryControllerPlan.Reason[] lamps =
                new BatteryControllerPlan.Reason[BatteryControllerPorts.OUTPUTS];
        for (int i = 0; i < lamps.length; i++) {
            lamps[i] = be.outputReason(i);
        }
        return face(be.reserveCapacity(), be.reservePct(), be.reserveRateFePerTick(), be.generationFePerTick(),
                be.deliveredFePerTick(), lamps, be.outlook());
    }

    static Face face(int reserveCapacity, int reservePct, int rate, int generation, int delivered,
                     BatteryControllerPlan.Reason[] lamps, com.createstats.power.ReserveProjection.Result r) {
        final com.createstats.power.ReserveProjection.Milestone firstShort =
                r.first(com.createstats.power.ReserveProjection.Kind.SHORT);
        final com.createstats.power.ReserveProjection.Milestone full =
                r.first(com.createstats.power.ReserveProjection.Kind.FULL);
        final com.createstats.power.ReserveProjection.Milestone rest =
                r.first(com.createstats.power.ReserveProjection.Kind.SETTLES);
        final int kind;
        final int value;
        if (r.trace().length == 0) {
            kind = Face.HORIZON_NONE;
            value = -1;
        } else if (r.shortNow()) {
            kind = Face.HORIZON_SHORT;
            value = 0;
        } else if (firstShort != null) {
            kind = Face.HORIZON_SHORT;
            value = firstShort.tick();
        } else if (full != null) {
            kind = Face.HORIZON_FULL;
            value = full.tick();
        } else if (rest != null) {
            kind = Face.HORIZON_SETTLE;
            value = rest.value();
        } else {
            kind = Face.HORIZON_NONE;
            value = -1;
        }
        return new Face(reserveCapacity, reservePct, rate, value, generation, delivered, lamps, kind);
    }

    static String horizonText(Face face) {
        final int v = face.enduranceTicks();
        return switch (face.horizonKind()) {
            case Face.HORIZON_CLOCK -> v > 0 ? clock(v) : "";
            case Face.HORIZON_SHORT -> v > 0 ? "SHORT " + clock(v) : "SHORT NOW";
            case Face.HORIZON_FULL -> v > 0 ? "FULL " + clock(v) : "";
            case Face.HORIZON_SETTLE -> v >= 0 ? "SETTLE " + v + "%" : "";
            default -> "";
        };
    }

    static int signatureOf(Face face, long blinkPhase) {
        int result = face.noStorage() ? 1 : 0;

        result = result * 31 + face.reservePct();
        result = result * 31 + face.rate();

        result = result * 31 + (face.enduranceTicks() > 0 ? face.enduranceTicks() / 20 : -1);

        result = result * 31 + face.horizonKind();
        result = result * 31 + (face.horizonKind() == Face.HORIZON_SETTLE ? face.enduranceTicks() : 0);
        result = result * 31 + face.generation();
        result = result * 31 + face.delivered();
        for (BatteryControllerPlan.Reason reason : face.lamps()) {
            result = result * 31 + reason.ordinal();
        }
        result = result * 31 + (face.anyLampBlinking() ? Long.hashCode(blinkPhase) : 0);
        return result;
    }

    // ---------- Drawing ----------

    static void draw(FaceCanvas c, Face face, long blinkPhase) {
        c.clear(0);
        c.quad(U0, V0, U1, V1, COL_GLASS);

        final float pad = 0.020f;
        final float x0 = U0 + pad;
        final float x1 = U1 - pad;
        final float w = x1 - x0;
        float y = V1 - pad;

        final boolean noStorage = face.noStorage();
        final float fill = face.fill();
        final int rate = face.rate();

        final float titleH = 0.046f;
        c.text("RESERVE", x0, y - titleH, COL_LABEL, 0.0058f);
        c.textRight(noStorage ? "--" : face.reservePct() + "%", x1, y - titleH,
                noStorage ? COL_RULE : fill < 0.15f ? COL_RED : fill < 0.30f ? COL_AMBER : COL_VALUE,
                0.0058f);
        y -= titleH + 0.012f;

        c.quad(x0, y - 0.005f, x1, y, COL_RULE);
        y -= 0.005f + 0.013f;

        final float barH = 0.070f;
        final int barColour = noStorage ? COL_TROUGH
                : fill < 0.15f ? COL_RED : fill < 0.30f ? COL_AMBER : COL_STEEL;
        final float barFill = x0 + w * fill;
        c.quad(x0, y - barH, barFill, y, barColour);
        c.quad(barFill, y - barH, x1, y, COL_TROUGH);
        y -= barH + 0.014f;

        final String flow = noStorage ? "NO STORAGE WIRED"
                : String.format(Locale.ROOT, "%s %s   %s",
                        rate > 0 ? "CHG" : rate < 0 ? "DIS" : "IDLE",
                        rate == 0 ? "" : compact(Math.abs(rate)) + "/t",
                        horizonText(face));
        c.textShrunk(flow.trim(), x0, y - 0.040f, w,
                noStorage ? COL_AMBER : rate > 0 ? COL_GREEN : rate < 0 ? COL_AMBER : COL_LABEL,
                0.0050f);
        y -= 0.040f + 0.016f;

        final float lampH = 0.058f;
        final float gap = 0.014f;
        final int outputs = BatteryControllerPorts.OUTPUTS;
        final float lampW = (w - gap * (outputs - 1)) / outputs;
        final boolean blink = blinkPhase == 0L;
        for (int i = 0; i < outputs; i++) {
            final float lx = x0 + i * (lampW + gap);
            final BatteryControllerPlan.Reason reason = face.lamps()[i];
            final int colour = switch (reason) {
                case SERVED -> COL_GREEN;
                case PARTIAL -> COL_STEEL;

                case RATIONED, RECHARGE -> COL_INFO;
                case FLOOR -> blink ? COL_AMBER : dim(COL_AMBER, 0.35f);
                case SHED, NO_SURPLUS -> COL_AMBER;
                case OFF -> COL_RED;
                case IDLE -> COL_INFO;
                case UNWIRED -> COL_TROUGH;
            };
            c.quad(lx, y - lampH, lx + lampW, y, colour);
            c.textCentred(String.valueOf(i + 1), lx + lampW * 0.5f, y - lampH - 0.040f,
                    reason == BatteryControllerPlan.Reason.UNWIRED ? COL_RULE : COL_LABEL, 0.0046f);
        }

        c.textShrunk(String.format(Locale.ROOT, "IN %s   OUT %s",
                        compact(face.generation()), compact(face.delivered())),
                x0, V0 + pad, w, COL_VALUE, 0.0050f);
    }

    private static String compact(int value) {
        final int abs = Math.abs(value);
        if (abs >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0f);
        }
        if (abs >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0f);
        }
        return String.valueOf(value);
    }

    private static String clock(int ticks) {
        final int secs = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    private static int dim(int argb, float factor) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Math.round(((argb >> 16) & 0xFF) * factor);
        final int g = Math.round(((argb >> 8) & 0xFF) * factor);
        final int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static Entry create(BlockPos pos) {
        try {
            final NativeImage image = new NativeImage(W, H, false);

            new FaceCanvas(image, U0, V0, U1, V1).clear(0);
            final DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/battery_controller_face_"
                            + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
