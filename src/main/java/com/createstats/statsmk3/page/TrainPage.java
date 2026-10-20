package com.createstats.statsmk3.page;

import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrainPage implements Mk3Page {
    public static final String FAMILY = "logistics.train";

    @Override
    public String id() {
        return "trains";
    }

    @Override
    public String title() {
        return "Trains";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return family != null && FAMILY.equals(family.getFamilyId());
    }

    @Override
    public boolean appliesTo(com.createstats.blockentity.StatsDisplayPanelBE panel) {
        // the map belongs to the network, not a station

        return panel != null && panel.hasTrainConnection();
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new TrainView(ctx);
    }

    @Override
    public int priority() {
        return 90;
    }
}
