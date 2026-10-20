package com.createstats.compat.geckolib;

import com.createstats.CreateStats;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class PilotHelmetGeoModel extends GeoModel<GeoPilotHelmetItem> {
    private static final ResourceLocation MODEL =
        ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "geo/pilot_helmet.geo.json");
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "textures/armor/pilot_helmet_geo.png");
    private static final ResourceLocation ANIMATION =
        ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "animations/pilot_helmet.animation.json");

    @Override
    public ResourceLocation getModelResource(GeoPilotHelmetItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GeoPilotHelmetItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(GeoPilotHelmetItem animatable) {
        return ANIMATION;
    }
}
