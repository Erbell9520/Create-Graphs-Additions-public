package com.createstats.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny client-side persistence for cross-session OTOMOBIL HUD stats. Stored as
 * pretty JSON at {@code <gameDir>/config/create_stats/otomobil_stats.json}.
 * Loaded lazily on first access; saved at most once per {@link #SAVE_THROTTLE_NANOS}
 * via {@link #saveThrottled()} plus a forced flush on JVM shutdown.
 *
 * <p>Schema:
 * <pre>{@code
 * { "totalTripM": 0.0, "totalDriveTimeSec": 0.0, "topSpeedAllTimeMps": 0.0 }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class OtomobilPersistedStats {

    private static final String CONFIG_DIR = "create_stats";
    private static final String FILE_NAME  = "otomobil_stats.json";
    private static final long SAVE_THROTTLE_NANOS = 5_000_000_000L; // 5 s

    private static boolean loaded = false;
    private static long lastSaveNanos = 0L;
    private static boolean dirty = false;
    private static boolean shutdownHookInstalled = false;

    private static double totalTripM = 0.0;
    private static double totalDriveTimeSec = 0.0;
    private static float topSpeedAllTimeMps = 0.0f;

    private OtomobilPersistedStats() {}

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        installShutdownHookOnce();
        Path path = resolvePath();
        if (path == null || !Files.exists(path)) return;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("totalTripM"))         totalTripM         = obj.get("totalTripM").getAsDouble();
            if (obj.has("totalDriveTimeSec"))  totalDriveTimeSec  = obj.get("totalDriveTimeSec").getAsDouble();
            if (obj.has("topSpeedAllTimeMps")) topSpeedAllTimeMps = obj.get("topSpeedAllTimeMps").getAsFloat();
        } catch (Exception ignored) {
            // Corrupt file → treat as zero; next save will overwrite cleanly.
        }
    }

    private static void installShutdownHookOnce() {
        if (shutdownHookInstalled) return;
        shutdownHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(OtomobilPersistedStats::saveNow, "OtomobilStats-Flush"));
    }

    private static Path resolvePath() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            Path dir = mc.gameDirectory.toPath().resolve("config").resolve(CONFIG_DIR);
            Files.createDirectories(dir);
            return dir.resolve(FILE_NAME);
        } catch (IOException e) {
            return null;
        }
    }

    public static double getTotalTripM() {
        ensureLoaded();
        return totalTripM;
    }

    public static double getTotalDriveTimeSec() {
        ensureLoaded();
        return totalDriveTimeSec;
    }

    public static float getTopSpeedAllTimeMps() {
        ensureLoaded();
        return topSpeedAllTimeMps;
    }

    public static void addTripDistanceM(double deltaM) {
        if (!Double.isFinite(deltaM) || deltaM <= 0.0) return;
        ensureLoaded();
        totalTripM = Math.min(9_999_999_999.0, totalTripM + deltaM);
        dirty = true;
    }

    public static void addDriveTimeSec(double deltaSec) {
        if (!Double.isFinite(deltaSec) || deltaSec <= 0.0) return;
        ensureLoaded();
        totalDriveTimeSec = Math.min(9_999_999_999.0, totalDriveTimeSec + deltaSec);
        dirty = true;
    }

    public static void recordTopSpeedSample(float mps) {
        if (!Float.isFinite(mps) || mps <= 0.0f) return;
        ensureLoaded();
        if (mps > topSpeedAllTimeMps) {
            topSpeedAllTimeMps = mps;
            dirty = true;
        }
    }

    /** Save no more than once per {@link #SAVE_THROTTLE_NANOS}; cheap to call every frame. */
    public static void saveThrottled() {
        if (!dirty) return;
        long now = System.nanoTime();
        if (now - lastSaveNanos < SAVE_THROTTLE_NANOS) return;
        lastSaveNanos = now;
        saveNow();
    }

    public static synchronized void saveNow() {
        if (!loaded || !dirty) return;
        Path path = resolvePath();
        if (path == null) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("totalTripM", totalTripM);
        obj.addProperty("totalDriveTimeSec", totalDriveTimeSec);
        obj.addProperty("topSpeedAllTimeMps", topSpeedAllTimeMps);
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(obj.toString());
            dirty = false;
        } catch (IOException ignored) {
            // Best-effort; will retry on next saveThrottled() call.
        }
    }
}
