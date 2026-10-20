package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// jade and boss bars draw after us, read last frame's. one frame late, nobody notices
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class TopClearance {
    private static final String JADE_OVERLAY = "snownee.jade.overlay.OverlayRenderer";

    private static final int BOSS_BAR_HEIGHT = 5;

    private static int bossDrawing;
    private static int bossBottom;

    private static boolean jadeResolved;
    private static Field jadeShown;
    private static Field jadeRect;
    private static Field jadeRectRect;

    private TopClearance() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frameStart(RenderGuiEvent.Pre event) {
        bossBottom = bossDrawing;
        bossDrawing = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!event.isCanceled()) {
            bossDrawing = Math.max(bossDrawing, event.getY() + BOSS_BAR_HEIGHT);
        }
    }

    public static int blockedBottom(int x0, int x1, int band) {
        int bottom = bossBottom;
        final Rect2i jade = jadeBox();
        if (jade != null && jade.getWidth() > 0 && jade.getY() < band
                && jade.getX() < x1 && x0 < jade.getX() + jade.getWidth()) {
            bottom = Math.max(bottom, jade.getY() + jade.getHeight());
        }
        return bottom;
    }

    private static Rect2i jadeBox() {
        if (!resolveJade()) {
            return null;
        }
        try {
            if (!jadeShown.getBoolean(null)) {
                return null;
            }
            final Object rect = jadeRect.get(null);
            return rect == null ? null : (Rect2i) jadeRectRect.get(rect);
        } catch (ReflectiveOperationException | RuntimeException e) {
            jadeShown = null;
            CreateStats.LOGGER.warn("[Driver Helmet] reading Jade's tooltip box failed; the HUD's top plate "
                    + "will not move out of its way: {}", e.toString());
            return null;
        }
    }

    private static boolean resolveJade() {
        if (jadeResolved) {
            return jadeShown != null;
        }
        jadeResolved = true;
        try {
            final Class<?> overlay = Class.forName(JADE_OVERLAY);
            jadeShown = overlay.getField("shown");
            jadeRect = overlay.getField("rect");
            jadeRectRect = jadeRect.getType().getField("rect");
        } catch (ClassNotFoundException e) {
            jadeShown = null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            jadeShown = null;
            CreateStats.LOGGER.warn("[Driver Helmet] Jade is installed but its tooltip box could not be found; "
                    + "the HUD's top plate will not move out of its way: {}", e.toString());
        }
        return jadeShown != null;
    }
}
