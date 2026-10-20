package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DrivePage implements Mk3Page {
    public static final String FAMILY = "vehicle.vcc_ground";

    @Override
    public String id() {
        return "drive";
    }

    @Override
    public String title() {
        return "Drive";
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
        return controller.groundLinks().hasLinks();
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new DriveView(ctx);
    }

    @Override
    public int priority() {
        return 94;
    }
}
