package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerPage implements Mk3Page {
    @Override
    public String id() {
        return "power";
    }

    @Override
    public String title() {
        return "Power";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        if (family == null) {
            return false;
        }
        for (final String id : PowerView.FAMILIES) {
            if (id.equals(family.getFamilyId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        return SourceProbe.hasFamily(panel, PowerView.FAMILIES);
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new PowerView(ctx);
    }

    @Override
    public int priority() {
        return 95;
    }
}
