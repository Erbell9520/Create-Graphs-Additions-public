package com.createstats.statsmk3.page;

import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StressometerPage implements Mk3Page {
    public static final String FAMILY = "kinetics.stressometer";

    @Override
    public String id() {
        return "stress";
    }

    @Override
    public String title() {
        return "Stress";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return family != null && FAMILY.equals(family.getFamilyId());
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new StressometerView(ctx);
    }

    @Override
    public int priority() {
        return 100;
    }
}
