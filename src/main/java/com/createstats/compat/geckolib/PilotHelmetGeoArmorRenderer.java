package com.createstats.compat.geckolib;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** [1] */
public final class PilotHelmetGeoArmorRenderer extends GeoArmorRenderer<GeoPilotHelmetItem> {

    public PilotHelmetGeoArmorRenderer() {
        super(new PilotHelmetGeoModel());
        // Emissive accents (HUD lens / status lights / crest) glow regardless of world light.
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(GeoPilotHelmetItem animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        // [2]
        return RenderType.armorCutoutNoCull(texture);
    }
}
