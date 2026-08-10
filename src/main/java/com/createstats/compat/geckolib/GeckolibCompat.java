package com.createstats.compat.geckolib;

import com.createstats.item.PilotHelmetItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

/** [1] */
public final class GeckolibCompat {

    private GeckolibCompat() {
    }

    /**
     * Build the GeckoLib-animated pilot helmet. Returned as {@link PilotHelmetItem} so callers need
     * no GeckoLib types.
     */
    public static PilotHelmetItem createPilotHelmet(Holder<ArmorMaterial> material,
                                                    ArmorItem.Type type,
                                                    Item.Properties properties) {
        return new GeoPilotHelmetItem(material, type, properties);
    }

    /** [2] */
    public static void registerSyncedAnimatable() {
        final com.createstats.item.PilotHelmetItem helmet = com.createstats.registry.ModItems.PILOT_HELMET.get();
        if (helmet instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
            software.bernie.geckolib.animatable.GeoItem.registerSyncedAnimatable(geoItem);
        }
    }
}
