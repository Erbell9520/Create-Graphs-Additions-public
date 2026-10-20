package com.createstats.statsmk3.page;

import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class EfficiencyPage implements Mk3Page {
    @Override
    public String id() {
        return "efficiency";
    }

    @Override
    public String title() {
        return "Efficiency";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return false;
    }

    @Override
    public boolean appliesTo(com.createstats.blockentity.StatsDisplayPanelBE panel) {
        return panel != null && panel.getLatestEfficiencyScore() != null;
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new EfficiencyView(ctx);
    }

    @Override
    public int priority() {
        return 70;
    }
}
