package com.createstats.powertrack.client;

import com.createstats.CreateStats;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public final class PowerTrackPartials {
    public static final PartialModel FEEDER_NEEDLE = block("track_feeder_needle");
    public static final PartialModel FEEDER_LAMP_GREEN = block("track_feeder_lamp_green");
    public static final PartialModel FEEDER_LAMP_AMBER = block("track_feeder_lamp_amber");
    public static final PartialModel INSULATOR_LAMP_GREEN = block("section_insulator_lamp_green");
    public static final PartialModel INSULATOR_LAMP_AMBER = block("section_insulator_lamp_amber");
    public static final PartialModel COLLECTOR_BEAM = block("current_collector_beam");
    public static final PartialModel COLLECTOR_BOOM = block("current_collector_boom");
    public static final PartialModel COLLECTOR_BRACKET = block("current_collector_bracket");
    public static final PartialModel COLLECTOR_ARM = block("current_collector_arm");
    public static final PartialModel COLLECTOR_SHOE = block("current_collector_shoe");

    private PowerTrackPartials() {
    }

    private static PartialModel block(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "block/power_track/" + name));
    }

    public static void init() {
    }
}
