package com.createstats.renderer;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
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
public final class PowerDistributionUnitTextureManager {
    private static final int TEXELS_PER_BLOCK = 256;

    private static final float U0 = PowerDistributionUnitRenderer.SCREEN_L;
    private static final float V0 = PowerDistributionUnitRenderer.GLASS_B;
    private static final float U1 = PowerDistributionUnitRenderer.SCREEN_R;
    private static final float V1 = PowerDistributionUnitRenderer.SCREEN_T;

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
    private static final int COL_RESERVE = 0xFFFF6A3C;

    private static final String[] TIER_LABELS = {"CRIT", "HIGH", "NORM", "LOW", "OPT"};

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private PowerDistributionUnitTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        long lastDrawTick = FaceRedrawGate.NEVER;
        int signature = Integer.MIN_VALUE;
    }

    public static ResourceLocation getOrUpdateTexture(PowerDistributionUnitBE be) {
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

    record Face(PduMode mode, int storedEnergy, float fill, float reserveFloor,
                int generation, int upkeep, int demand, int servedDemand,
                int[] tierTotal, int[] tierServed, int branchServed, int branchShed) {
        boolean noInput() {
            return storedEnergy <= 0;
        }

        boolean auto() {
            return mode == PduMode.AUTO;
        }

        boolean anyTierBlinking() {
            for (int i = 0; i < tierTotal.length; i++) {
                if (tierTotal[i] > 0 && !noInput() && tierServed[i] == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static Face read(PowerDistributionUnitBE be) {
        final FeGridSolver.BranchResult branch = be.lastBranchResult();
        final PowerDistributionUnitBE.TierRollCall roll = be.tierRollCall();
        return new Face(be.mode(), be.storedEnergy(), be.fillFraction(), be.reserveFloorFraction(),
                be.generationFePerTick(), be.upkeepFePerTick(), be.branchDemandFePerTick(),
                be.servedDemandFePerTick(), roll.total(), roll.served(),
                branch.served(), branch.shed());
    }

    static int signatureOf(Face face, long blinkPhase) {
        int result = face.mode().ordinal();

        result = result * 31 + (face.noInput() ? 1 : 0);

        result = result * 31 + Math.round(Mth.clamp(face.fill(), 0.0f, 1.0f) * 1024.0f);
        result = result * 31 + Math.round(Mth.clamp(face.reserveFloor(), 0.0f, 1.0f) * 1024.0f);
        if (face.auto()) {
            result = result * 31 + face.generation();
            result = result * 31 + face.upkeep();
            result = result * 31 + face.demand();
            result = result * 31 + face.servedDemand();
        }
        for (int i = 0; i < face.tierTotal().length; i++) {
            result = result * 31 + face.tierTotal()[i];
            result = result * 31 + face.tierServed()[i];
        }
        result = result * 31 + face.branchServed();
        result = result * 31 + face.branchShed();
        result = result * 31 + (face.anyTierBlinking() ? Long.hashCode(blinkPhase) : 0);
        return result;
    }

    // ---------- Drawing ----------

    static void draw(FaceCanvas c, Face face, long blinkPhase) {
        c.clear(0);

        final float fill = face.fill();
        final boolean noInput = face.noInput();
        final PduMode mode = face.mode();
        final boolean auto = face.auto();

        c.quad(U0, V0, U1, V1, COL_GLASS);

        final float pad = 0.018f;
        final float x0 = U0 + pad;
        final float x1 = U1 - pad;
        final float w = x1 - x0;

        float y = V1 - pad;

        final float titleH = 0.042f;
        final float titleScale = 0.0058f;
        final int modeColour = auto ? COL_GREEN : mode == PduMode.MANUAL ? COL_STEEL : COL_RULE;

        c.textRight(mode.name(), x1, y - titleH, modeColour, titleScale);

        c.textShrunk("DISTRIBUTION", x0, y - titleH,
                w - FaceCanvas.width(mode.name(), titleScale) - 0.030f, COL_LABEL, titleScale);
        y -= titleH + 0.010f;

        c.quad(x0, y - 0.005f, x1, y, COL_RULE);
        y -= 0.005f + 0.011f;

        final float barH = 0.052f;
        final int barColour = fill < 0.15f ? COL_RED : fill < 0.30f ? COL_AMBER : COL_STEEL;
        final float barFill = x0 + w * Mth.clamp(fill, 0.0f, 1.0f);
        c.quad(x0, y - barH, barFill, y, barColour);
        c.quad(barFill, y - barH, x1, y, COL_TROUGH);

        final float floorX = x0 + w * Mth.clamp(face.reserveFloor(), 0.0f, 1.0f);
        c.quad(floorX, y - barH - 0.005f, floorX + 0.004f, y + 0.005f, COL_RESERVE);
        y -= barH + 0.013f;

        if (auto) {
            final int gen = face.generation();

            final int upkeep = face.upkeep();
            final int demand = face.demand() + upkeep;
            final int served = face.servedDemand() + upkeep;
            final int scale = Math.max(1, Math.max(gen, demand));
            final float cueH = 0.024f;

            final float genW = w * (Math.min(gen, scale) / (float) scale);
            c.quad(x0, y - cueH, x0 + genW, y, COL_STEEL);
            c.quad(x0 + genW, y - cueH, x1, y, COL_TROUGH);
            y -= cueH + 0.008f;

            final float servedW = w * (Math.min(served, scale) / (float) scale);

            final float demandW = Math.max(servedW, w * (Math.min(demand, scale) / (float) scale));
            c.quad(x0, y - cueH, x0 + servedW, y, COL_GREEN);
            c.quad(x0 + servedW, y - cueH, x0 + demandW, y, COL_AMBER);
            c.quad(x0 + demandW, y - cueH, x1, y, COL_TROUGH);
            y -= cueH + 0.012f;
        }

        final float lampH = 0.048f;
        final float gap = 0.010f;
        final int tiers = FePriority.values().length;
        final float lampW = (w - gap * (tiers - 1)) / tiers;
        final boolean blink = blinkPhase == 0L;
        for (int i = 0; i < tiers; i++) {
            final float lx = x0 + i * (lampW + gap);
            final int total = face.tierTotal()[i];
            final int served = face.tierServed()[i];
            final int colour;
            if (total == 0) {
                colour = COL_TROUGH;
            } else if (noInput) {
                colour = COL_RED;
            } else if (served == total) {
                colour = COL_GREEN;
            } else if (served == 0) {
                colour = blink ? COL_AMBER : dim(COL_AMBER, 0.35f);
            } else {
                colour = COL_STEEL;
            }
            c.quad(lx, y - lampH, lx + lampW, y, colour);
            c.textCentred(TIER_LABELS[i], lx + lampW * 0.5f, y - lampH - 0.036f,
                    total == 0 ? COL_RULE : COL_LABEL, 0.0044f);
        }

        final String line = noInput
                ? "NO INPUT"
                : String.format(Locale.ROOT, "%d SERVED   %d SHED",
                        face.branchServed(), face.branchShed());
        final int lineColour = noInput ? COL_RED : face.branchShed() > 0 ? COL_AMBER : COL_VALUE;

        c.textShrunk(line, x0, PowerDistributionUnitRenderer.SCREEN_B + 0.012f, w, lineColour,
                0.0054f);
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
                    "create_stats/pdu_face_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
