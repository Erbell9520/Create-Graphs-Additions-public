package com.createstats.renderer;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.WeatherPanelBE;
import com.createstats.gui.weather.WeatherAnim;
import com.createstats.weather.ClimateModel;
import com.createstats.weather.Condition;
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
public final class WeatherFaceTextureManager {
    public static final int W = 384;
    public static final int H = 192;

    private static final int IDLE_REDRAW_TICKS = 100;
    private static final int MAX_IDLE_TICKS = 400;
    private static final int CLEANUP_INTERVAL = 200;

    private static final int INK = 0xFFFFFFFF;

    public static final float GLYPH_X = 14f;
    public static final float GLYPH_Y = 48f;
    public static final float GLYPH_SIZE = 96f;
    private static final float TEXT_X = 128f;
    private static final float TEXT_RIGHT = 372f;
    private static final float TEMP_Y = 20f;
    private static final float TEMP_SIZE = 70f;
    private static final float WORD_Y = 102f;
    private static final float WORD_SIZE = 28f;
    private static final float PRESSURE_Y = 108f;
    private static final float PRESSURE_SIZE = 22f;
    private static final float COUNTDOWN_Y = 142f;
    private static final float COUNTDOWN_SIZE = 22f;

    public static final float BAR_Y = 170f;
    public static final float BAR_H = 10f;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int cleanupCounter;

    private WeatherFaceTextureManager() {
    }

    private static final class Entry {
        NativeImage image;
        DynamicTexture texture;
        ResourceLocation location;
        long lastAccessTick;
        long lastDrawTick = Long.MIN_VALUE;
        String signature = "";

        final WeatherAnim anim = new WeatherAnim();
    }

    public static WeatherAnim animFor(WeatherPanelBE be) {
        final Entry entry = entryFor(be.getBlockPos());
        return entry == null ? new WeatherAnim() : entry.anim;
    }

    public static ResourceLocation getOrUpdateTexture(WeatherPanelBE be) {
        final Entry entry = entryFor(be.getBlockPos());
        if (entry == null) {
            return null;
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

    private static Entry entryFor(BlockPos pos) {
        Entry entry = ENTRIES.get(pos);
        if (entry == null) {
            entry = create(pos);
            if (entry != null) {
                ENTRIES.put(pos, entry);
            }
        }
        return entry;
    }

    private static String signatureOf(WeatherPanelBE be) {
        return be.condition() + "|" + Math.round(displayTemperature(be))
                + "|" + (fahrenheit() ? 'F' : 'C')
                + "|" + (be.ticksToNextChange() / 1000)
                + "|" + (Float.isNaN(be.pressure()) ? "--" : Math.round(be.pressure()));
    }

    public static boolean fahrenheit() {
        try {
            return ModConfiguration.CLIENT.weatherFahrenheit.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static float displayTemperature(WeatherPanelBE be) {
        return fahrenheit() ? ClimateModel.toFahrenheit(be.temperatureC()) : be.temperatureC();
    }

    private static void draw(Entry entry, WeatherPanelBE be) {
        final NativeImage image = entry.image;
        HiFiCanvas.clear(image, 0x00000000);

        if (be.condition() == Condition.NONE) {
            HiFiFont.draw(image, "NO SKY", TEXT_X, 60f, 46f, 5.5f, INK);
            HiFiFont.draw(image, "NO WEATHER HERE", TEXT_X, 118f, 22f, 2.6f, INK);
            upload(entry);
            return;
        }

        final int whole = Math.round(displayTemperature(be));
        final String digits = Integer.toString(whole);
        HiFiFont.draw(image, digits, TEXT_X, TEMP_Y, TEMP_SIZE, TEMP_SIZE / 7.5f, INK);

        final float digitsWidth = HiFiFont.width(digits, TEMP_SIZE);
        final float ringX = TEXT_X + digitsWidth + 13f;
        HiFiCanvas.ring(image, ringX, TEMP_Y + 13f, 9f, 5f, INK);
        HiFiFont.draw(image, fahrenheit() ? "F" : "C", ringX + 15f, TEMP_Y + 2f, 30f, 3.6f, INK);

        HiFiFont.draw(image, word(be.condition()), TEXT_X, WORD_Y, WORD_SIZE, 3.2f, INK);
        if (!Float.isNaN(be.pressure())) {
            final String hpa = Math.round(be.pressure()) + " HPA";
            HiFiFont.draw(image, hpa, TEXT_RIGHT - HiFiFont.width(hpa, PRESSURE_SIZE),
                    PRESSURE_Y, PRESSURE_SIZE, 2.4f, INK);
        }

        final int next = be.ticksToNextChange();
        if (next >= 0) {
            final String label = (be.condition() == Condition.CLEAR ? "RAIN IN " : "CLEARING IN ")
                    + WeatherPanelBE.mcTime(next).toUpperCase(java.util.Locale.ROOT);
            HiFiFont.draw(image, HiFiFont.clip(label, COUNTDOWN_SIZE, TEXT_RIGHT - TEXT_X),
                    TEXT_X, COUNTDOWN_Y, COUNTDOWN_SIZE, 2.4f, INK);
        }
        upload(entry);
    }

    private static String word(Condition condition) {
        return switch (condition) {
            case CLEAR -> "CLEAR";
            case RAIN -> "RAIN";
            case STORM -> "STORM";
            case SNOW -> "SNOW";

            case DRY -> "DRY / RAIN NEARBY";
            case NONE -> "NO SKY";
        };
    }

    private static void upload(Entry entry) {
        entry.texture.upload();

        entry.texture.setFilter(true, false);
    }

    private static Entry create(BlockPos pos) {
        try {
            final NativeImage image = new NativeImage(W, H, false);
            final DynamicTexture texture = new DynamicTexture(image);
            final Entry entry = new Entry();
            entry.image = image;
            entry.texture = texture;
            entry.location = Minecraft.getInstance().getTextureManager().register(
                    "create_stats/weather_face_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
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
