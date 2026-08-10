package com.createstats.compat.lambdynlights;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import org.jetbrains.annotations.Nullable;

/** [1] */
public final class StageLightDynamicLights implements DynamicLightsInitializer {

    @Nullable
    private static volatile DynamicLightBehaviorManager behaviorManager;

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        behaviorManager = context.dynamicLightBehaviorManager();
        StageLightDynamicLightClient.init();
    }

    @Override
    @SuppressWarnings("removal")
    public void onInitializeDynamicLights(dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager itemLightSourceManager) {
        // Legacy entrypoint overload; nothing to register here.
    }

    /** The captured behavior manager, or {@code null} if LambDynamicLights has not initialised yet. */
    @Nullable
    public static DynamicLightBehaviorManager behaviorManager() {
        return behaviorManager;
    }
}
