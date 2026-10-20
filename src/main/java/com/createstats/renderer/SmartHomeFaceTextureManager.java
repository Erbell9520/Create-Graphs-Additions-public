package com.createstats.renderer;

import com.createstats.block.SmartHomePanelBlock;
import com.createstats.blockentity.SmartHomePanelBE;
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

@OnlyIn(Dist.CLIENT)
public final class SmartHomeFaceTextureManager {
    private static final int W = 256;
    private static final int H = 320;

    private static final int GROUND_TOP = 0xFFFFFFFF;
    private static final int GROUND_BOTTOM = 0xFFE8F0EB;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFE2EAE5;
    private static final int TRACK = 0xFFE4EDE8;
    private static final int TEXT = 0xFF0F1A14;
    private static final int TEXT_DIM = 0xFF5B6B62;
    private static final int GREEN = 0xFF18A957;
    private static final int AMBER = 0xFFE0A03A;
    private static final int SHADOW = 0x38101C15;

    private static final int IDLE_REDRAW_TICKS = 100;
    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private SmartHomeFaceTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        long lastDrawTick = Long.MIN_VALUE;

        String signature = "";
    }

    public static ResourceLocation getOrUpdateTexture(SmartHomePanelBE be) {
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

        final String signature = signatureOf(be);
        if (!signature.equals(entry.signature) || now - entry.lastDrawTick >= IDLE_REDRAW_TICKS) {
            entry.signature = signature;
            entry.lastDrawTick = now;
            draw(entry, be);
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStale(now);
        }
        return entry.location;
    }

    private static String signatureOf(SmartHomePanelBE be) {
        return be.houseName().getString() + '|' + be.shownOn() + '/' + be.shownDevices()
                + '|' + be.shownDrawFe() + '|' + band(be) + '|' + be.shownStarved() + '|' + be.shownRunning();
    }

    static String pillText(boolean alert, int devices, int starved, int running) {
        if (alert) {
            if (devices == 0) {
                return "nothing found";
            }
            return starved > 0 ? starved + " no power" : "alert";
        }
        return running > 0 ? running + " running" : "live";
    }

    private static SmartHomePanelBlock.Screen band(SmartHomePanelBE be) {
        final var state = be.getBlockState();
        return state.hasProperty(SmartHomePanelBlock.SCREEN)
                ? state.getValue(SmartHomePanelBlock.SCREEN)
                : SmartHomePanelBlock.Screen.DARK;
    }

    private static void draw(Entry entry, SmartHomePanelBE be) {
        final NativeImage image = entry.image;
        final int devices = Math.max(0, be.shownDevices());
        final int on = Math.max(0, Math.min(be.shownOn(), devices));
        final boolean alert = band(be) == SmartHomePanelBlock.Screen.ALERT;
        final int accent = alert ? AMBER : GREEN;

        HiFiCanvas.verticalGradient(image, GROUND_TOP, GROUND_BOTTOM);

        HiFiCanvas.dot(image, 27.0f, 33.0f, 7.0f, accent);
        final String house = HiFiFont.clip(be.houseName().getString(), 15.0f, 174.0f);
        HiFiFont.draw(image, house, 44.0f, 26.0f, 15.0f, 2.4f, TEXT_DIM);

        final float cardX = 16.0f;
        final float cardY = 56.0f;
        final float cardW = 224.0f;
        final float cardH = 166.0f;
        HiFiCanvas.dropShadow(image, cardX, cardY, cardW, cardH, 28.0f, 10.0f, 5.0f, SHADOW);
        HiFiCanvas.fillRoundRect(image, cardX, cardY, cardW, cardH, 28.0f, CARD);
        HiFiCanvas.strokeRoundRect(image, cardX, cardY, cardW, cardH, 28.0f, 1.0f, LINE);

        final float ringX = 128.0f;
        final float ringY = 128.0f;
        final float ringR = 52.0f;
        final float ringWeight = 14.0f;
        HiFiCanvas.ring(image, ringX, ringY, ringR, ringWeight, TRACK);
        if (devices > 0 && on > 0) {
            HiFiCanvas.arc(image, ringX, ringY, ringR, -90.0f,
                    -90.0f + 360.0f * on / devices, ringWeight, accent);
        }

        if (devices == 0) {
            HiFiFont.drawCentred(image, "0", ringX, ringY - 30.0f, 60.0f, 8.0f, TEXT_DIM);
        } else {
            HiFiFont.drawCentred(image, Integer.toString(on), ringX, ringY - 30.0f, 60.0f, 8.5f, TEXT);
        }
        final String caption = devices == 0 ? "nothing found" : "of " + devices + " on";
        HiFiFont.drawCentred(image, caption, ringX, 196.0f, 13.0f, 2.1f, TEXT_DIM);

        drawPill(image, 16.0f, 240.0f, 130.0f, 54.0f, CARD, LINE,
                com.createstats.power.FeRates.perTick(be.shownDrawFe()), TEXT);
        drawPill(image, 154.0f, 240.0f, 86.0f, 54.0f, accent, accent,
                pillText(alert, devices, be.shownStarved(), be.shownRunning()), 0xFFFFFFFF);

        entry.texture.upload();

        entry.texture.setFilter(true, false);
    }

    private static void drawPill(NativeImage image, float x, float y, float w, float h,
                                 int fill, int border, String label, int ink) {
        final float radius = h / 2.0f;
        HiFiCanvas.dropShadow(image, x, y, w, h, radius, 8.0f, 4.0f, SHADOW);
        HiFiCanvas.fillRoundRect(image, x, y, w, h, radius, fill);
        HiFiCanvas.strokeRoundRect(image, x, y, w, h, radius, 1.0f, border);

        float size = 17.0f;
        while (size > 9.0f && HiFiFont.width(label, size) > w - 22.0f) {
            size -= 0.5f;
        }
        HiFiFont.drawCentred(image, label, x + w / 2.0f, y + (h - size) / 2.0f, size,
                Math.max(1.8f, size / 6.5f), ink);
    }

    private static Entry create(BlockPos pos) {
        try {
            final NativeImage image = new NativeImage(W, H, false);
            final DynamicTexture texture = new DynamicTexture(image);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/smart_home_face_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
