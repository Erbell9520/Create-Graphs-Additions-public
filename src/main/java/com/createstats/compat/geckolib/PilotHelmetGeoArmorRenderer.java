package com.createstats.compat.geckolib;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class PilotHelmetGeoArmorRenderer extends GeoArmorRenderer<GeoPilotHelmetItem> {
    public PilotHelmetGeoArmorRenderer() {
        super(new PilotHelmetGeoModel());

        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(GeoPilotHelmetItem animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.armorCutoutNoCull(texture);
    }
}
