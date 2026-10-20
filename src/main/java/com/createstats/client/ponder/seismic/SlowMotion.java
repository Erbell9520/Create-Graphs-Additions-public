package com.createstats.client.ponder.seismic;

import com.createstats.CreateStats;
import com.createstats.client.ponder.KitInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntUnaryOperator;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class SlowMotion {
    public static final int MAX_STRETCH = 10;

    private static final Map<PonderScene, IntUnaryOperator> WANTED = new WeakHashMap<>();
    private static Field length;
    private static boolean looked;
    private static boolean warned;

    private static int span;
    private static int spanAt = Integer.MIN_VALUE;
    private static PonderScene spanScene;

    private SlowMotion() {
    }

    private static synchronized boolean find() {
        if (!looked) {
            looked = true;
            try {
                length = PonderUI.class.getDeclaredField("extendedTickLength");
                length.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                length = null;
                warn(e);
            }
        }
        return length != null;
    }

    private static void warn(Exception e) {
        if (!warned) {
            warned = true;
            CreateStats.LOGGER.warn("[Ponder] slow motion is unavailable, scenes play at full speed: {}", e.toString());
        }
    }

    public static int stretch(double slower) {
        return Math.max(1, Math.min(MAX_STRETCH, (int) Math.round(slower)));
    }

    public static KitInstruction slow(IntUnaryOperator factor, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            @Override
            protected void begin(PonderScene scene) {
                synchronized (WANTED) {
                    WANTED.put(scene, factor);
                }
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                apply(scene, tick, true);
                if (last) {
                    synchronized (WANTED) {
                        WANTED.remove(scene);
                    }
                }
            }
        };
    }

    private static void apply(PonderScene scene, int tick, boolean atTick) {
        if (!(Minecraft.getInstance().screen instanceof PonderUI ui) || ui.getActiveScene() != scene || !find()) {
            return;
        }
        final IntUnaryOperator f;
        synchronized (WANTED) {
            f = WANTED.get(scene);
        }
        if (f == null) {
            return;
        }
        final int stretch = Math.max(1, Math.min(MAX_STRETCH, f.applyAsInt(tick)));
        span = stretch - 1;
        spanAt = tick;
        spanScene = scene;
        try {
            if (length.getInt(ui) < span) {
                length.setInt(ui, span);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            length = null;
            warn(e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (spanScene == null || !(Minecraft.getInstance().screen instanceof PonderUI ui)) {
            return;
        }
        final PonderScene scene = ui.getActiveScene();
        if (scene != spanScene || length == null) {
            return;
        }
        final boolean held;
        synchronized (WANTED) {
            held = WANTED.containsKey(scene);
        }

        if (!held || scene.getCurrentTime() < spanAt || scene.getCurrentTime() > spanAt + 1) {
            spanScene = null;
            return;
        }
        try {
            if (length.getInt(ui) < span) {
                length.setInt(ui, span);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            length = null;
            warn(e);
        }
    }

    public static IntUnaryOperator ramp(int in, int full, int out, int end, int slower) {
        return t -> {
            final double k;
            if (t < in || t >= end) {
                k = 0.0;
            } else if (t < full) {
                k = smooth((t - in) / (double) (full - in));
            } else if (t < out) {
                k = 1.0;
            } else {
                k = 1.0 - smooth((t - out) / (double) (end - out));
            }
            return stretch(1.0 + (slower - 1.0) * k);
        };
    }

    private static double smooth(double u) {
        final double v = Math.max(0.0, Math.min(1.0, u));
        return v * v * (3.0 - 2.0 * v);
    }
}
