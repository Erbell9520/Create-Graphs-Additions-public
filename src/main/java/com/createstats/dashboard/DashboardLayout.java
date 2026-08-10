package com.createstats.dashboard;

import java.util.Iterator;
import java.util.Collections;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class DashboardLayout
{
    private final List<DashboardZone> allZones;
    private final DashboardZone mainZone;
    private final DashboardZone headerZone;
    private final DashboardZone bottomZone;
    private final List<DashboardZone> sparklineZones;
    private final List<DashboardZone> cornerZones;
    
    public DashboardLayout(List<DashboardZone> zones) {
        this.allZones = Collections.unmodifiableList((List<? extends DashboardZone>)new ArrayList<DashboardZone>(zones));
        DashboardZone main = null;
        DashboardZone header = null;
        DashboardZone bottom = null;
        final List<DashboardZone> sparks = new ArrayList<DashboardZone>();
        final List<DashboardZone> corners = new ArrayList<DashboardZone>();
        for (DashboardZone z : zones) {
            switch (z.type) {
                case MAIN: {
                    main = z;
                    continue;
                }
                case HEADER: {
                    header = z;
                    continue;
                }
                case BOTTOM: {
                    bottom = z;
                    continue;
                }
                case SPARKLINE: {
                    sparks.add(z);
                    continue;
                }
                case CORNER_TL:
                case CORNER_TR:
                case CORNER_BL:
                case CORNER_BR: {
                    corners.add(z);
                    continue;
                }
            }
        }
        this.mainZone = main;
        this.headerZone = header;
        this.bottomZone = bottom;
        this.sparklineZones = Collections.unmodifiableList((List<? extends DashboardZone>)sparks);
        this.cornerZones = Collections.unmodifiableList((List<? extends DashboardZone>)corners);
    }
    
    public List<DashboardZone> getAllZones() {
        return this.allZones;
    }
    
    public DashboardZone getMainZone() {
        return this.mainZone;
    }
    
    public DashboardZone getHeaderZone() {
        return this.headerZone;
    }
    
    public DashboardZone getBottomZone() {
        return this.bottomZone;
    }
    
    public List<DashboardZone> getSparklineZones() {
        return this.sparklineZones;
    }
    
    public List<DashboardZone> getCornerZones() {
        return this.cornerZones;
    }
}
