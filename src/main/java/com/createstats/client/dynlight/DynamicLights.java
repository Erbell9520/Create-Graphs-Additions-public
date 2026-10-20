package com.createstats.client.dynlight;

import com.createstats.dynlight.BeamLight;
import com.createstats.dynlight.Light;
import com.createstats.dynlight.PointLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

// replaced LambDynamicLights. own light engine cells, works under sodium and iris
public final class DynamicLights {
    private DynamicLights() {
    }

    public static void registerItemLight(ItemLike item, ItemLight light) {
        ClientDynamicLights.registerItemLight(item.asItem(), light);
    }

    public static void registerSource(ResourceLocation id, LightSource source) {
        ClientDynamicLights.registerSource(id, source);
    }

    public static void submit(Object key, Light light) {
        ClientDynamicLights.submit(key, light);
    }

    public static void remove(Object key) {
        ClientDynamicLights.remove(key);
    }

    public static boolean isActive() {
        return ClientDynamicLights.isActive();
    }
}
