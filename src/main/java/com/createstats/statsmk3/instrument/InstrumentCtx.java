package com.createstats.statsmk3.instrument;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelStyle;
import com.createstats.statsmk3.YAxisRange;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InstrumentCtx {
    public ChannelView primary;

    public List<ChannelView> all = new ArrayList<>();
    public PanelStyle style = new PanelStyle();
    public Lod lod = Lod.FULL;

    public float time;

    public YAxisRange yAxis = YAxisRange.auto();

    public final Plot.Points plotPoints = new Plot.Points();
    public float[] linePoints = new float[0];
    public final float[] extent = new float[2];

    private final List<ChannelView> ordered = new ArrayList<>();

    public InstrumentCtx with(ChannelView channel) {
        this.primary = channel;
        return this;
    }

    public List<ChannelView> series() {
        if (this.all == null || this.all.isEmpty()) {
            return this.primary == null ? List.of() : List.of(this.primary);
        }
        if (this.primary == null || this.all.get(0).id.equals(this.primary.id)) {
            return this.all;
        }
        this.ordered.clear();
        this.ordered.add(this.primary);
        for (final ChannelView ch : this.all) {
            if (!ch.id.equals(this.primary.id)) {
                this.ordered.add(ch);
            }
        }
        return this.ordered;
    }
}
