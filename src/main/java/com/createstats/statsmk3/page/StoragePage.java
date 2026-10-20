package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StoragePage implements Mk3Page {
    @Override
    public String id() {
        return "storage";
    }

    @Override
    public String title() {
        return "Storage";
    }

    @Override
    public boolean handles(SourceFamilyDefinition family) {
        return false;
    }

    @Override
    public boolean appliesTo(StatsDisplayPanelBE panel) {
        for (final SourceProbe.Unit unit : SourceProbe.all(panel)) {
            if (!StorageReadings.variantsOf(unit).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StudioView create(StudioContext ctx) {
        return new StorageView(ctx);
    }

    @Override
    public int priority() {
        return 92;
    }
}
