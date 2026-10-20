package com.createstats.client.ponder;

import com.createstats.CreateStats;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;

import java.lang.reflect.Method;

// ponder's shadow stays where the plate was. simulated mixes in a fade, we drive it
public final class ShadowFade {
    private static final String EXTENSION = "dev.simulated_team.simulated.mixin_interface.ponder.PonderSceneExtension";
    private static Method toggle;
    private static Method timer;
    private static boolean looked;
    private static boolean warned;

    private ShadowFade() {
    }

    private static synchronized boolean find() {
        if (!looked) {
            looked = true;
            try {
                Class<?> ext = Class.forName(EXTENSION);
                toggle = ext.getMethod("simulated$toggleRenderBasePlateShadow");
                timer = ext.getMethod("simulated$getBasePlateAnimationTimer", float.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                toggle = null;
                timer = null;
            }
        }
        return toggle != null && timer != null;
    }

    public static boolean available() {
        return find();
    }

    public static void removeIfUnavailable(SceneBuilder scene) {
        if (!available()) scene.removeShadow();
    }

    public static KitInstruction fade(boolean shown, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (!find()) return;
                try {
                    float now = (Float) timer.invoke(scene, 0f);
                    if ((now > 0.5f) != shown) toggle.invoke(scene);
                } catch (ReflectiveOperationException | ClassCastException e) {
                    if (!warned) {
                        warned = true;
                        CreateStats.LOGGER.warn("[Ponder] the base plate's shadow could not be faded: {}", e.toString());
                    }
                }
            }
        };
    }
}
