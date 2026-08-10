package com.createstats.display;

import com.simibubi.create.content.redstone.displayLink.source.ItemThroughputDisplaySource;

public class TunnelStatsLinkDisplaySource extends ItemThroughputDisplaySource implements StatsLinkSource
{
    protected String getTranslationKey() {
        return "stats_link";
    }
}
