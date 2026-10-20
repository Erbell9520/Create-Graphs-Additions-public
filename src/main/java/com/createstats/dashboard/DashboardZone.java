package com.createstats.dashboard;

import com.createstats.blockentity.StatsDisplayPanelBE;

public class DashboardZone
{
    public final ZoneType type;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public StatsDisplayPanelBE.DataChannel assignedChannel;
    public int sparklineIndex;

    public DashboardZone(ZoneType type, int x, int y, int w, int h) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    public enum ZoneType
    {
        HEADER,
        MAIN,
        SIDE_LEFT,
        SIDE_RIGHT,
        BOTTOM,
        CORNER_TL,
        CORNER_TR,
        CORNER_BL,
        CORNER_BR,
        SPARKLINE;
    }
}
