package com.createstats.compat.geckolib;

import com.createstats.item.PilotHelmetItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

public final class GeckolibCompat {
    private GeckolibCompat() {
    }

    public static PilotHelmetItem createPilotHelmet(Holder<ArmorMaterial> material,
                                                    ArmorItem.Type type,
                                                    Item.Properties properties) {
        return new GeoPilotHelmetItem(material, type, properties);
    }

    public static void registerSyncedAnimatable() {
        final com.createstats.item.PilotHelmetItem helmet = com.createstats.registry.ModItems.PILOT_HELMET.get();
        if (helmet instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
            software.bernie.geckolib.animatable.GeoItem.registerSyncedAnimatable(geoItem);
        }
    }
}
