package com.createstats.chunkloader;

import com.createstats.ModConfiguration;
import com.createstats.chunkloader.core.CrownLaw;

public final class CrownConfig {
    private CrownConfig() {
    }

    public static CrownLaw law() {
        try {
            final ModConfiguration.Common c = ModConfiguration.COMMON;
            return new CrownLaw(c.sparkCrownBaseFePerTick.get(), c.sparkCrownGrowthPercent.get(),
                    c.sparkCrownMaxPerStation.get(), c.sparkCrownMaxPerPlayer.get(), c.sparkCrownRideSeconds.get());
        } catch (RuntimeException notLoaded) {
            return CrownLaw.DEFAULT;
        }
    }
}
