package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RefinedStoragePage implements Mk3Page {
    @Override
    public String id() {
        return "refined_storage";
    }

    @Override
    public String title() {
        return "Storage Net";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return family != null && RsReadings.FAMILY.equals(family.getFamilyId());
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        return RsReadings.any(panel);
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new RefinedStorageView(ctx);
    }

    @Override
    public int priority() {
        return 96;
    }
}
