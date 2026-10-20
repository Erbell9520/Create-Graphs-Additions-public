package com.createstats.display;

import com.createstats.ModConfiguration;

public class AeroStatsLinkDisplaySource extends AutoStatsLinkDisplaySource {
    @Override
    public int getPassiveRefreshTicks() {
        return ModConfiguration.getEffectiveStatsLinkRefreshTicks();
    }
}
