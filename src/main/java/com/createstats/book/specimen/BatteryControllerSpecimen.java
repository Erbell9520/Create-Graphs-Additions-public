package com.createstats.book.specimen;

import com.createstats.gui.BatteryControllerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BatteryControllerSpecimen {
    private BatteryControllerSpecimen() {
    }

    public static ManualSpecimen create(String id) {
        return BatteryControllerScreen.specimen(BatteryPlates.view(), BatteryPlates.tabOf(id), BatteryPlates.selected(id),
                BatteryPlates.history(), BatteryPlates.RANGE, BatteryPlates.portRate(), BatteryPlates.W, BatteryPlates.H,
                () -> BatteryPlates.anatomy(id));
    }
}
