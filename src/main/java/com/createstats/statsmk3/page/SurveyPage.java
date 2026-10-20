package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SurveyPage implements Mk3Page {
    public static final String FAMILY = "survey.seismic";

    @Override
    public String id() {
        return "survey";
    }

    @Override
    public String title() {
        return "Survey";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return family != null && FAMILY.equals(family.getFamilyId());
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        if (panel == null) {
            return false;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE() != null ? panel.getControllerBE() : panel;
        return controller.seismicLinks().hasRigs();
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new SurveyView(ctx);
    }

    @Override
    public int priority() {
        return 93;
    }
}
