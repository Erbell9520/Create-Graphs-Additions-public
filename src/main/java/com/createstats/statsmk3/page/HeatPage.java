package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HeatPage implements Mk3Page {
    @Override
    public String id() {
        return "heat";
    }

    @Override
    public String title() {
        return "Heat";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        if (family == null) {
            return false;
        }
        for (final String id : HeatView.FAMILIES) {
            if (id.equals(family.getFamilyId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        return SourceProbe.hasFamily(panel, HeatView.FAMILIES);
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new HeatView(ctx);
    }

    @Override
    public int priority() {
        return 88;
    }
}
