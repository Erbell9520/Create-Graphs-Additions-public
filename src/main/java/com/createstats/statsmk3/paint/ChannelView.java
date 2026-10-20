package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// no BE reference, the painter must never reach into the world mid draw
@OnlyIn(Dist.CLIENT)
public final class ChannelView {
    public final String id;
    public String label = "";
    public String unit = "";

    public float value;

    public float min;
    public float max = 1.0f;

    public float[] history = new float[0];
    public int historyCount;

    long historyRevision = Long.MIN_VALUE;
    int historyBudget = -1;
    long usedInBuild;

    public boolean alerting;
    public boolean stale;

    private int color;
    private boolean colorSet;

    public ChannelView(String id) {
        this.id = id == null ? "" : id;
    }

    public int color() {
        return this.colorSet ? this.color : Tokens.seriesFor(this.id);
    }

    public ChannelView setColor(int argb) {
        this.color = argb;
        this.colorSet = true;
        return this;
    }

    public float fraction() {
        final float span = this.max - this.min;
        if (span <= 1.0e-6f) {
            return 0.0f;
        }
        final float f = (this.value - this.min) / span;
        return f < 0.0f ? 0.0f : Math.min(f, 1.0f);
    }

    public float[] historyExtent(int count, float pad) {
        final float[] out = new float[2];
        historyExtent(count, pad, out);
        return out;
    }

    public void historyExtent(int count, float pad, float[] out) {
        if (out == null || out.length < 2) {
            return;
        }
        final int n = Math.min(count, this.historyCount);
        if (n <= 0) {
            out[0] = this.min;
            out[1] = this.max;
            return;
        }
        final int start = this.historyCount - n;
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (int i = start; i < this.historyCount; i++) {
            final float v = this.history[i];
            if (v < lo) {
                lo = v;
            }
            if (v > hi) {
                hi = v;
            }
        }
        if (lo == hi) {
            lo -= 1.0f;
            hi += 1.0f;
        }
        final float p = (hi - lo) * pad;
        out[0] = lo - p;
        out[1] = hi + p;
    }

    void ensureHistoryCapacity(int capacity) {
        if (this.history.length < capacity) {
            this.history = new float[Math.max(capacity, this.history.length * 2 + 8)];
        }
    }

    public boolean hasTrend() {
        return this.historyCount >= 2;
    }
}
