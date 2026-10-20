package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FluidsPage implements Mk3Page {
    @Override
    public String id() {
        return "fluids";
    }

    @Override
    public String title() {
        return "Fluids";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        if (family == null) {
            return false;
        }
        for (final String id : FluidsView.FAMILIES) {
            if (id.equals(family.getFamilyId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        return SourceProbe.hasFamily(panel, FluidsView.FAMILIES);
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new FluidsView(ctx);
    }

    @Override
    public int priority() {
        return 86;
    }
}
