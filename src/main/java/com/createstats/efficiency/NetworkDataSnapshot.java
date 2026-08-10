package com.createstats.efficiency;

import java.util.Iterator;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import com.createstats.blockentity.StatsDisplayPanelBE;

public class NetworkDataSnapshot
{
    public float stressSU;
    public float capacitySU;
    public float stressPct;
    public float remainingSU;
    public float rpm;
    public boolean isOverstressed;
    public float rpmAvg;
    public float rpmStdDev;
    public float rpmMax;
    public float stressAvg;
    public float stressStdDev;
    public float stressRateOfChange;
    public float overstressRate;
    public long computedAtTick;
    
    private NetworkDataSnapshot() {
    }
    
    @Nullable
    public static NetworkDataSnapshot fromPanel(StatsDisplayPanelBE panel) {
        final Map<String, StatsDisplayPanelBE.DataChannel> allChannels = panel.getAllChannels();
        final StatsDisplayPanelBE.DataChannel stressSuCh = findChannelBySuffix(allChannels, "_auto_stress_su");
        final StatsDisplayPanelBE.DataChannel capacityCh = findChannelBySuffix(allChannels, "_auto_capacity_su");
        final StatsDisplayPanelBE.DataChannel stressPctCh = findChannelBySuffix(allChannels, "_auto_stress_pct");
        final StatsDisplayPanelBE.DataChannel remainingCh = findChannelBySuffix(allChannels, "_auto_remaining_su");
        final StatsDisplayPanelBE.DataChannel rpmCh = findChannelBySuffix(allChannels, "_auto_rpm");
        final StatsDisplayPanelBE.DataChannel overstCh = findChannelBySuffix(allChannels, "_auto_overstressed");
        if (stressSuCh == null || capacityCh == null) {
            return null;
        }
        if (stressSuCh.getStore().getSampleCount() < 20 || capacityCh.getStore().getSampleCount() < 20) {
            return null;
        }
        final int window = 120;
        final NetworkDataSnapshot snap = new NetworkDataSnapshot();
        snap.stressSU = stressSuCh.getStore().getLatestValue();
        snap.capacitySU = capacityCh.getStore().getLatestValue();
        snap.stressPct = ((stressPctCh != null) ? stressPctCh.getStore().getLatestValue() : ((snap.capacitySU > 0.0f) ? (snap.stressSU / snap.capacitySU * 100.0f) : 0.0f));
        snap.remainingSU = ((remainingCh != null) ? remainingCh.getStore().getLatestValue() : Math.max(0.0f, snap.capacitySU - snap.stressSU));
        snap.rpm = ((rpmCh != null) ? rpmCh.getStore().getLatestValue() : 0.0f);
        snap.isOverstressed = (overstCh != null && overstCh.getStore().getLatestValue() >= 0.5f);
        snap.rpmAvg = ((rpmCh != null) ? rpmCh.getStore().getAverageWindow(window) : 0.0f);
        snap.rpmStdDev = ((rpmCh != null) ? rpmCh.getStore().getStdDevWindow(window) : 0.0f);
        snap.rpmMax = ((rpmCh != null) ? rpmCh.getStore().getMaxWindow(window) : 0.0f);
        snap.stressAvg = stressSuCh.getStore().getAverageWindow(window);
        snap.stressStdDev = stressSuCh.getStore().getStdDevWindow(window);
        snap.stressRateOfChange = Math.abs(stressSuCh.getStore().getAvgRateOfChange(30));
        if (overstCh != null) {
            final int sampleCount = Math.min(600, overstCh.getStore().getSampleCount());
            final float[] data = overstCh.getStore().getDataWindow(sampleCount);
            int over = 0;
            for (float v : data) {
                if (v >= 0.5f) {
                    ++over;
                }
            }
            snap.overstressRate = ((data.length > 0) ? (over / (float)data.length) : 0.0f);
        }
        else {
            snap.overstressRate = 0.0f;
        }
        snap.computedAtTick = ((panel.getLevel() != null) ? panel.getLevel().getGameTime() : 0L);
        return snap;
    }
    
    private static StatsDisplayPanelBE.DataChannel findChannelBySuffix(Map<String, StatsDisplayPanelBE.DataChannel> channels, String suffix) {
        for (Map.Entry<String, StatsDisplayPanelBE.DataChannel> entry : channels.entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
