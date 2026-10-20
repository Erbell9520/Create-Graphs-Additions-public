package com.createstats.compat.linktablet;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.api.client.ProgramHost;
import com.modpack.linktablet.api.client.RegisterTabletProgramClientsEvent;
import com.modpack.linktablet.api.client.TabletFacePainter;
import com.modpack.linktablet.api.client.TabletProgramClient;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StatsTabletClient implements TabletProgramClient {
    private static final StatsTabletClient INSTANCE = new StatsTabletClient();

    private StatsTabletClient() {
    }

    public static void onRegisterClients(RegisterTabletProgramClientsEvent event) {
        event.register(StatsTabletProgram.KEY, INSTANCE);
    }

    @Override
    public Screen createScreen(ProgramHost host) {
        return new StatsAppScreen(host);
    }

    @Override
    public OverlayContent createOverlay(ProgramHost host) {
        return new StatsOverlayContent();
    }

    @Override
    public TabletFacePainter facePainter() {
        return StatsTabletFace::paint;
    }
}
