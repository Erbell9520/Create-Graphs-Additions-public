package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class OilfieldPage implements Mk3Page {
    @Override
    public String id() {
        return "oilfield";
    }

    @Override
    public String title() {
        return "Oilfield";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return family != null && OilfieldView.FAMILIES[0].equals(family.getFamilyId());
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        return SourceProbe.hasFamily(panel, OilfieldView.FAMILIES);
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new OilfieldView(ctx);
    }

    @Override
    public int priority() {
        return 90;
    }
}
