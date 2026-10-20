package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

final class FilmTime {
    @Nullable private static Field residual;
    @Nullable private static Field eyeNow;
    @Nullable private static Field eyeOld;
    private static boolean looked;
    private static boolean shifted;
    private static float saved;

    private FilmTime() {
    }

    private static void look() {
        if (looked) return;
        looked = true;
        residual = field(DeltaTracker.Timer.class, RESIDUAL);
        eyeNow = field(Camera.class, EYE_HEIGHT);
        eyeOld = field(Camera.class, EYE_HEIGHT_OLD);
    }

    static final String RESIDUAL = "deltaTickResidual";
    static final String EYE_HEIGHT = "eyeHeight";
    static final String EYE_HEIGHT_OLD = "eyeHeightOld";

    @Nullable
    static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            if (f.getType() == float.class) return f;
            CreateStats.LOGGER.warn("Cine Camera: {}.{} is not a float here; the film runs without it", owner.getSimpleName(), name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CreateStats.LOGGER.warn("Cine Camera: {}.{} cannot be reached here ({}); the film runs without it",
                    owner.getSimpleName(), name, e.toString());
        }
        return null;
    }

    static boolean shift(Minecraft mc, float partial) {
        look();
        restore(mc);
        if (residual == null || mc.isPaused() || !(mc.getTimer() instanceof DeltaTracker.Timer timer)) return false;
        try {
            saved = residual.getFloat(timer);
            residual.setFloat(timer, Math.max(0f, Math.min(1f, partial)));
            shifted = true;
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    static void restore(Minecraft mc) {
        if (!shifted) return;
        shifted = false;
        if (residual != null && mc.getTimer() instanceof DeltaTracker.Timer timer) {
            try {
                residual.setFloat(timer, saved);
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
    }

    @Nullable
    static float[] eyeHeight(Camera camera, float height) {
        look();
        if (eyeNow == null || eyeOld == null) return null;
        try {
            float[] was = {eyeNow.getFloat(camera), eyeOld.getFloat(camera)};
            eyeNow.setFloat(camera, height);
            eyeOld.setFloat(camera, height);
            return was;
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    static void eyeHeight(Camera camera, @Nullable float[] was) {
        if (was == null || eyeNow == null || eyeOld == null) return;
        try {
            eyeNow.setFloat(camera, was[0]);
            eyeOld.setFloat(camera, was[1]);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }
}
