package com.createstats.compat.linktablet;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.client.StressometerHudDataResolver;
import com.createstats.client.StressometerHudReadout;
import com.createstats.display.StressometerHudState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class StatsStressReadout {
    private static final StressometerHudState STATE = new StressometerHudState();

    private StatsStressReadout() {
    }

    static StressometerHudReadout paired() {
        final StatsDisplayPanelBE panel = StatsTabletPairing.resolvePairedPanel();
        if (panel == null) {
            return null;
        }
        final StressometerHudReadout readout = StressometerHudDataResolver.resolve(panel, STATE, false);
        return readout.hasLiveData() ? readout : null;
    }
}
