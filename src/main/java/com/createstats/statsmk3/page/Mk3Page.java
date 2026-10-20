package com.createstats.statsmk3.page;

import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Mk3Page {
    String id();

    String title();

    boolean handles(SourceFamilyDefinition family);

    StudioView create(StudioContext ctx);

    default boolean appliesTo(com.createstats.blockentity.StatsDisplayPanelBE panel) {
        return false;
    }

    default int priority() {
        return 0;
    }
}
