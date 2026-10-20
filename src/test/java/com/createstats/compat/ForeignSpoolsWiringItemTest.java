package com.createstats.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignSpoolsWiringItemTest {
    @Test
    void aNullStackIsNeverAWiringItem() {
        assertFalse(ForeignSpools.isWiringItem(null));
    }

    @Test
    void bothModsWiresSpoolsAndConnectorsAreRecognised() {
        for (String id : new String[] {
                "create_new_age:copper_wire",
                "create_new_age:overcharged_diamond_wire",
                "create_new_age:electrical_connector",
                "createaddition:copper_spool",
                "createaddition:electrum_spool",
                "createaddition:spool",
                "createaddition:iron_wire",
                "createaddition:connector",
                "createaddition:large_connector"}) {
            assertTrue(ForeignSpools.isWiringItemId(ResourceLocation.parse(id)),
                    id + " should be a wiring item");
        }
    }

    @Test
    void unrelatedIdsAreNotWiringItems() {
        assertFalse(ForeignSpools.isWiringItemId(ResourceLocation.parse("minecraft:stick")));
        assertFalse(ForeignSpools.isWiringItemId(ResourceLocation.parse("create_stats:copper_wire")));
        assertFalse(ForeignSpools.isWiringItemId(ResourceLocation.parse("createaddition:capacitor")));
        assertFalse(ForeignSpools.isWiringItemId(null));
    }
}
