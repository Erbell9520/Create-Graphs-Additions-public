package com.createstats.compat.linktablet;

import com.modpack.linktablet.api.RegisterTabletProgramsEvent;
import com.modpack.linktablet.api.TabletProgram;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class StatsTabletProgram implements TabletProgram {
    // never change this, it's stored on tablets
    public static final String KEY = "create_stats:stats";

    public static final StatsTabletProgram INSTANCE = new StatsTabletProgram();

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("create_stats", "stats_display_panel");

    private StatsTabletProgram() {
    }

    public static void onRegisterPrograms(RegisterTabletProgramsEvent event) {
        event.register(INSTANCE);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Component displayName() {
        return Component.translatable("program.create_stats.stats");
    }

    @Override
    public Component storeDescription() {
        return Component.translatable("program.create_stats.stats.desc");
    }

    @Override
    public int chipColor() {
        return 0xFF2E9DD8;
    }

    @Override
    public ResourceLocation iconItem() {
        return ICON;
    }
}
