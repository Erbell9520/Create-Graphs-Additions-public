package com.createstats.statsmk3.page;

import com.createstats.display.SourceFamilyDefinition;
import com.createstats.netscan.NetscanLink;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopologyPage implements Mk3Page {
    @Override
    public String id() {
        return "topology";
    }

    @Override
    public String title() {
        return "Topology";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return false;
    }

    @Override
    public boolean appliesTo(com.createstats.blockentity.StatsDisplayPanelBE panel) {
        return !NetscanLink.scannersFor(panel).isEmpty();
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new TopologyView(ctx);
    }

    @Override
    public int priority() {
        return 80;
    }
}
