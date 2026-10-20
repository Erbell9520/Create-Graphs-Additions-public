package com.createstats.statsmk3.instrument;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Instrument {
    String id();

    String displayName();

    String description();

    void draw(PanelSurface surface, Rect area, InstrumentCtx ctx);

    default boolean needsHistory() {
        return true;
    }

    default float minSize() {
        return 28.0f;
    }
}
