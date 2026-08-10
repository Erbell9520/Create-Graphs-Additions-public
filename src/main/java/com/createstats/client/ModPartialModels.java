package com.createstats.client;

import com.createstats.CreateStats;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class ModPartialModels {

    public static final PartialModel SAFETY_CLUTCH_GAUGE_NEEDLE = block("safety_clutch/gauge_needle");
    public static final PartialModel AUTO_TRANSMISSION_CLUTCH_GAUGE_NEEDLE = block("auto_transmission_clutch/gauge_needle");
    public static final PartialModel SMART_GEARSHIFT_GAUGE_NEEDLE = block("smart_gearshift/gauge_needle");
    public static final PartialModel RPM_GOVERNOR_GAUGE_NEEDLE = block("rpm_governor/gauge_needle");
    public static final PartialModel DATA_DIAL_NEEDLE = block("data_dial/needle");
    public static final PartialModel FLYWHEEL_WHEEL = block("kinetic_flywheel/wheel");
    public static final PartialModel FLUID_PUMP_IMPELLER = block("fluid_pump/impeller");

    public static final PartialModel STAGE_LIGHT_BASE = block("stage_light/base");
    public static final PartialModel STAGE_LIGHT_YOKE = block("stage_light/yoke");
    public static final PartialModel STAGE_LIGHT_HEAD = block("stage_light/head");
    public static final PartialModel STAGE_LIGHT_HEAD_LENS = block("stage_light/head_lens");
    public static final PartialModel STAGE_LIGHT_COG = block("stage_light/cog");

    public static final PartialModel STAGE_LIGHT_MK2_BASE = block("stage_light_mk2/base");
    public static final PartialModel STAGE_LIGHT_MK2_YOKE = block("stage_light_mk2/yoke");
    public static final PartialModel STAGE_LIGHT_MK2_HEAD = block("stage_light_mk2/head");
    public static final PartialModel STAGE_LIGHT_MK2_HEAD_LENS = block("stage_light_mk2/head_lens");
    public static final PartialModel STAGE_LIGHT_MK2_COG = block("stage_light_mk2/cog");

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "block/" + path));
    }

    public static void init() {
    }
}
