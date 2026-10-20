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

    public static final PartialModel PORTABLE_POWER_INTERFACE_MIDDLE = block("portable_power_interface/block_middle");
    public static final PartialModel PORTABLE_POWER_INTERFACE_MIDDLE_CHARGE = block("portable_power_interface/block_middle_charge");
    public static final PartialModel PORTABLE_POWER_INTERFACE_MIDDLE_DRAW = block("portable_power_interface/block_middle_draw");
    public static final PartialModel PORTABLE_POWER_INTERFACE_TOP = block("portable_power_interface/block_top");

    public static final PartialModel[] FLYWHEEL_GAUGE = {
        block("kinetic_flywheel/gauge_0"), block("kinetic_flywheel/gauge_1"),
        block("kinetic_flywheel/gauge_2"), block("kinetic_flywheel/gauge_3"),
        block("kinetic_flywheel/gauge_4"), block("kinetic_flywheel/gauge_5"),
        block("kinetic_flywheel/gauge_6"), block("kinetic_flywheel/gauge_7"),
    };

    public static final PartialModel WINDOW_SHUTTER_SLAT = block("window_shutter/slat");
    public static final PartialModel BLAST_SHUTTER_SLAT = block("blast_shutter/slat");

    public static final PartialModel GARAGE_DOOR_SLAT = block("garage_door/slat");
    public static final PartialModel GARAGE_DOOR_SLAT_VISION = block("garage_door/slat_vision");
    public static final PartialModel GARAGE_DOOR_BOTTOM_BAR = block("garage_door/bottom_bar");

    // two models, mirroring = negative scale = flipped winding = culled from the outside
    public static final PartialModel GARAGE_DOOR_CAP_LEFT = block("garage_door/cap_left");
    public static final PartialModel GARAGE_DOOR_CAP_RIGHT = block("garage_door/cap_right");

    public static final PartialModel LOCKER_DOOR = block("security_locker/door");
    public static final PartialModel LOCKER_BOLT = block("security_locker/bolt");
    public static final PartialModel LOCKER_DIAL = block("security_locker/dial");
    public static final PartialModel LOCKER_SPOKE = block("security_locker/spoke");

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

    public static final PartialModel COFFEE_LEVER = block("coffee_machine/lever");
    public static final PartialModel COFFEE_NEEDLE = block("coffee_machine/needle");
    public static final PartialModel COFFEE_WAND = block("coffee_machine/wand");
    public static final PartialModel COFFEE_ROCKER = block("coffee_machine/rocker");
    public static final PartialModel COFFEE_LAMP_OFF = block("coffee_machine/lamp_off");
    public static final PartialModel COFFEE_LAMP_ON = block("coffee_machine/lamp_on");
    public static final PartialModel COFFEE_PORTAFILTER = block("coffee_machine/portafilter");
    public static final PartialModel COFFEE_BREW = block("coffee_machine/brew");
    public static final PartialModel COFFEE_FILL_MILK = block("coffee_machine/fill_milk");
    public static final PartialModel COFFEE_FILL_LATTE = block("coffee_machine/fill_latte");
    public static final PartialModel COFFEE_STREAM = block("coffee_machine/stream");

    public static final PartialModel AIR_FRYER_DRAWER = block("air_fryer/drawer");
    public static final PartialModel AIR_FRYER_WINDOW_OFF = block("air_fryer/window_off");
    public static final PartialModel AIR_FRYER_WINDOW_ON = block("air_fryer/window_on");
    public static final PartialModel AIR_FRYER_IMPELLER = block("air_fryer/impeller");
    public static final PartialModel AIR_FRYER_ROCKER = block("air_fryer/rocker");
    public static final PartialModel AIR_FRYER_START_OFF = block("air_fryer/start_off");
    public static final PartialModel AIR_FRYER_START_ON = block("air_fryer/start_on");
    public static final PartialModel AIR_FRYER_LAMP_OFF = block("air_fryer/lamp_off");
    public static final PartialModel AIR_FRYER_LAMP_GREEN = block("air_fryer/lamp_green");
    public static final PartialModel AIR_FRYER_LAMP_BLUE = block("air_fryer/lamp_blue");
    public static final PartialModel AIR_FRYER_LAMP_AMBER = block("air_fryer/lamp_amber");
    public static final PartialModel AIR_FRYER_COLON_ON = block("air_fryer/colon_on");
    public static final PartialModel AIR_FRYER_COLON_OFF = block("air_fryer/colon_off");
    public static final PartialModel AIR_FRYER_DIGIT_0 = block("air_fryer/digit_0");
    public static final PartialModel AIR_FRYER_DIGIT_1 = block("air_fryer/digit_1");
    public static final PartialModel AIR_FRYER_DIGIT_2 = block("air_fryer/digit_2");
    public static final PartialModel AIR_FRYER_DIGIT_3 = block("air_fryer/digit_3");
    public static final PartialModel AIR_FRYER_DIGIT_4 = block("air_fryer/digit_4");
    public static final PartialModel AIR_FRYER_DIGIT_5 = block("air_fryer/digit_5");
    public static final PartialModel AIR_FRYER_DIGIT_6 = block("air_fryer/digit_6");
    public static final PartialModel AIR_FRYER_DIGIT_7 = block("air_fryer/digit_7");
    public static final PartialModel AIR_FRYER_DIGIT_8 = block("air_fryer/digit_8");
    public static final PartialModel AIR_FRYER_DIGIT_9 = block("air_fryer/digit_9");

    public static final PartialModel BULK_WASHER_DOOR_FRAME = block("bulk_washer/door_frame");

    public static final PartialModel BULK_WASHER_GLASS_OFF = block("bulk_washer/glass_off");
    public static final PartialModel BULK_WASHER_GLASS_ON = block("bulk_washer/glass_on");
    public static final PartialModel BULK_WASHER_DRUM = block("bulk_washer/drum");
    public static final PartialModel BULK_WASHER_WATER = block("bulk_washer/water");
    public static final PartialModel BULK_WASHER_BTN_OFF = block("bulk_washer/btn_off");
    public static final PartialModel BULK_WASHER_BTN_GREEN = block("bulk_washer/btn_green");
    public static final PartialModel BULK_WASHER_BTN_BLUE = block("bulk_washer/btn_blue");
    public static final PartialModel BULK_WASHER_BTN_AMBER = block("bulk_washer/btn_amber");
    public static final PartialModel BULK_WASHER_PIP_OFF = block("bulk_washer/pip_off");
    public static final PartialModel BULK_WASHER_PIP_ON = block("bulk_washer/pip_on");
    public static final PartialModel BULK_WASHER_LAMP_OFF = block("bulk_washer/lamp_off");
    public static final PartialModel BULK_WASHER_LAMP_GREEN = block("bulk_washer/lamp_green");
    public static final PartialModel BULK_WASHER_LAMP_BLUE = block("bulk_washer/lamp_blue");
    public static final PartialModel BULK_WASHER_LAMP_AMBER = block("bulk_washer/lamp_amber");
    public static final PartialModel BULK_WASHER_COLON_ON = block("bulk_washer/colon_on");
    public static final PartialModel BULK_WASHER_COLON_OFF = block("bulk_washer/colon_off");
    public static final PartialModel BULK_WASHER_DIGIT_0 = block("bulk_washer/digit_0");
    public static final PartialModel BULK_WASHER_DIGIT_1 = block("bulk_washer/digit_1");
    public static final PartialModel BULK_WASHER_DIGIT_2 = block("bulk_washer/digit_2");
    public static final PartialModel BULK_WASHER_DIGIT_3 = block("bulk_washer/digit_3");
    public static final PartialModel BULK_WASHER_DIGIT_4 = block("bulk_washer/digit_4");
    public static final PartialModel BULK_WASHER_DIGIT_5 = block("bulk_washer/digit_5");
    public static final PartialModel BULK_WASHER_DIGIT_6 = block("bulk_washer/digit_6");
    public static final PartialModel BULK_WASHER_DIGIT_7 = block("bulk_washer/digit_7");
    public static final PartialModel BULK_WASHER_DIGIT_8 = block("bulk_washer/digit_8");
    public static final PartialModel BULK_WASHER_DIGIT_9 = block("bulk_washer/digit_9");

    public static final PartialModel COOLER_PADDLE_SPRING = block("water_cooler/paddle_spring");
    public static final PartialModel COOLER_PADDLE_CHILLED = block("water_cooler/paddle_chilled");
    public static final PartialModel COOLER_PADDLE_SPARKLING = block("water_cooler/paddle_sparkling");
    public static final PartialModel COOLER_ROCKER = block("water_cooler/rocker");
    public static final PartialModel COOLER_LAMP_OFF = block("water_cooler/lamp_off");
    public static final PartialModel COOLER_LAMP_GREEN = block("water_cooler/lamp_green");
    public static final PartialModel COOLER_LAMP_CYAN = block("water_cooler/lamp_cyan");
    public static final PartialModel COOLER_LAMP_AMBER = block("water_cooler/lamp_amber");
    public static final PartialModel COOLER_LAMP_WHITE = block("water_cooler/lamp_white");
    public static final PartialModel COOLER_WATER_TAPER = block("water_cooler/water_taper");
    public static final PartialModel COOLER_WATER_BODY = block("water_cooler/water_body");
    public static final PartialModel COOLER_BUBBLE = block("water_cooler/bubble");
    public static final PartialModel COOLER_STREAM_SPRING = block("water_cooler/stream_spring");
    public static final PartialModel COOLER_STREAM_CHILLED = block("water_cooler/stream_chilled");
    public static final PartialModel COOLER_STREAM_SPARKLING = block("water_cooler/stream_sparkling");
    public static final PartialModel COOLER_FILL_SPRING = block("water_cooler/fill_spring");
    public static final PartialModel COOLER_FILL_CHILLED = block("water_cooler/fill_chilled");
    public static final PartialModel COOLER_FILL_SPARKLING = block("water_cooler/fill_sparkling");

    public static final PartialModel COFFEE_MUG_CERAMIC = block("coffee_machine/mug_ceramic");
    public static final PartialModel COFFEE_MUG_ENAMEL = block("coffee_machine/mug_enamel");
    public static final PartialModel COFFEE_MUG_STEEL = block("coffee_machine/mug_steel");

    public static final PartialModel VCC_MK3_HORIZON_BAR = block("vcc_mk3/horizon_bar");
    public static final PartialModel VCC_MK3_ATT_FLAG = block("vcc_mk3/att_flag");
    public static final PartialModel VCC_MK3_SHUTTLE_IDLE = block("vcc_mk3/shuttle_idle");
    public static final PartialModel VCC_MK3_SHUTTLE_MANUAL = block("vcc_mk3/shuttle_manual");
    public static final PartialModel VCC_MK3_SHUTTLE_AUTO = block("vcc_mk3/shuttle_auto");

    public static final PartialModel PA_BRAIN_FAN = block("pa_brain/fan");
    public static final PartialModel PA_BRAIN_BAR = block("pa_brain/bar");
    public static final PartialModel PA_BRAIN_PILOT_ON = block("pa_brain/pilot_on");
    public static final PartialModel PA_BRAIN_LENS_NOTICE = block("pa_brain/lens_notice");
    public static final PartialModel PA_BRAIN_LENS_WARNING = block("pa_brain/lens_warning");
    public static final PartialModel PA_BRAIN_LENS_CRITICAL = block("pa_brain/lens_critical");

    public static final PartialModel ARC_ELECTRODE = block("arc_furnace/electrode");
    public static final PartialModel ARC_HATCH = block("arc_furnace/hatch");
    public static final PartialModel ARC_DRAWER = block("arc_furnace/drawer");
    public static final PartialModel ARC_FAN_BLADE = block("arc_furnace/fan_blade");

    public static final PartialModel SMELTER_LEAF_LEFT = block("induction_smelter/leaf_left");
    public static final PartialModel SMELTER_LEAF_RIGHT = block("induction_smelter/leaf_right");
    public static final PartialModel SMELTER_NEEDLE = block("induction_smelter/needle");
    public static final PartialModel SMELTER_LEVER = block("induction_smelter/lever");
    public static final PartialModel SMELTER_FAN_BLADE = block("induction_smelter/fan_blade");

    public static final PartialModel TOASTER_CARRIAGE = block("toaster/carriage");
    public static final PartialModel TOASTER_LEVER = block("toaster/lever");
    public static final PartialModel TOASTER_DIAL = block("toaster/dial");
    public static final PartialModel TOASTER_TRAY = block("toaster/tray");
    public static final PartialModel TOASTER_BTN_OFF = block("toaster/btn_off");
    public static final PartialModel TOASTER_BTN_BLUE = block("toaster/btn_blue");
    public static final PartialModel TOASTER_BTN_RED = block("toaster/btn_red");
    public static final PartialModel TOASTER_ELEMENT_0 = block("toaster/element_0");
    public static final PartialModel TOASTER_ELEMENT_1 = block("toaster/element_1");
    public static final PartialModel TOASTER_ELEMENT_2 = block("toaster/element_2");
    public static final PartialModel TOASTER_ELEMENT_3 = block("toaster/element_3");
    public static final PartialModel TOASTER_SLICE_0 = block("toaster/slice_0");
    public static final PartialModel TOASTER_SLICE_1 = block("toaster/slice_1");
    public static final PartialModel TOASTER_SLICE_2 = block("toaster/slice_2");
    public static final PartialModel TOASTER_SLICE_3 = block("toaster/slice_3");
    public static final PartialModel TOASTER_SLICE_4 = block("toaster/slice_4");
    public static final PartialModel TOASTER_SLICE_5 = block("toaster/slice_5");

    public static final PartialModel BREAD_SLICER_HOOD_FRAME = block("bread_slicer/hood_frame");
    public static final PartialModel BREAD_SLICER_HOOD_GLASS = block("bread_slicer/hood_glass");
    public static final PartialModel BREAD_SLICER_BLADE = block("bread_slicer/blade");
    public static final PartialModel BREAD_SLICER_PUSHER = block("bread_slicer/pusher");
    public static final PartialModel BREAD_SLICER_LOAF_WHOLE = block("bread_slicer/loaf_whole");
    public static final PartialModel BREAD_SLICER_LOAF = block("bread_slicer/loaf");
    public static final PartialModel BREAD_SLICER_SLICE = block("bread_slicer/slice");
    public static final PartialModel BREAD_SLICER_LEVER = block("bread_slicer/lever");
    public static final PartialModel BREAD_SLICER_BTN_START = block("bread_slicer/btn_start");
    public static final PartialModel BREAD_SLICER_BTN_START_LIT = block("bread_slicer/btn_start_lit");
    public static final PartialModel BREAD_SLICER_BTN_START_DEAD = block("bread_slicer/btn_start_dead");
    public static final PartialModel BREAD_SLICER_BTN_STOP = block("bread_slicer/btn_stop");

    public static final PartialModel POWER_LIMITER_CAM = block("power_limiter/cam");

    public static final PartialModel STARTER_CAPACITOR_ARM = block("starter_capacitor_arm");
    public static final PartialModel POWER_LIMITER_ARC_LIT = block("power_limiter/arc_lit");
    public static final PartialModel POWER_LIMITER_ARC_SHUT = block("power_limiter/arc_shut");

    public static final PartialModel MAIN_BREAKER_HANDLE = block("main_breaker/handle");
    public static final PartialModel MAIN_BREAKER_STRUT_TUBE = block("main_breaker/strut_tube");
    public static final PartialModel MAIN_BREAKER_STRUT_ROD = block("main_breaker/strut_rod");
    public static final PartialModel MAIN_BREAKER_PILOT = block("main_breaker/pilot");
    public static final PartialModel MAIN_BREAKER_MARK_O = block("main_breaker/mark_o");
    public static final PartialModel MAIN_BREAKER_MARK_I = block("main_breaker/mark_i");
    public static final PartialModel MAIN_BREAKER_PADLOCK = block("main_breaker/padlock");
    public static final PartialModel MAIN_BREAKER_SELECTOR = block("main_breaker/selector");
    public static final PartialModel[] MAIN_BREAKER_BAR = bar("main_breaker");

    public static final PartialModel CREATIVE_MAIN_BREAKER_HANDLE = block("creative_main_breaker/handle");
    public static final PartialModel CREATIVE_MAIN_BREAKER_STRUT_TUBE = block("creative_main_breaker/strut_tube");
    public static final PartialModel CREATIVE_MAIN_BREAKER_STRUT_ROD = block("creative_main_breaker/strut_rod");
    public static final PartialModel CREATIVE_MAIN_BREAKER_PILOT = block("creative_main_breaker/pilot");
    public static final PartialModel CREATIVE_MAIN_BREAKER_MARK_O = block("creative_main_breaker/mark_o");
    public static final PartialModel CREATIVE_MAIN_BREAKER_MARK_I = block("creative_main_breaker/mark_i");
    public static final PartialModel CREATIVE_MAIN_BREAKER_PADLOCK = block("creative_main_breaker/padlock");
    public static final PartialModel CREATIVE_MAIN_BREAKER_SELECTOR = block("creative_main_breaker/selector");
    public static final PartialModel[] CREATIVE_MAIN_BREAKER_BAR = bar("creative_main_breaker");

    public static final int MAIN_BREAKER_BAR_SEGMENTS = 4;

    private static PartialModel[] bar(String variant) {
        final PartialModel[] out = new PartialModel[MAIN_BREAKER_BAR_SEGMENTS];
        for (int i = 0; i < out.length; i++) {
            out[i] = block(variant + "/bar_" + i);
        }
        return out;
    }

    public static final PartialModel UNIVERSAL_POWER_COUPLER_SHUTTLE = block("universal_power_coupler/shuttle");
    public static final PartialModel UNIVERSAL_POWER_COUPLER_LINK = block("universal_power_coupler/link_lit");

    public record DoorModels(PartialModel leaf, PartialModel leafL, PartialModel leafR,
                             PartialModel seamHi, PartialModel seamLo,
                             PartialModel seamR, PartialModel seamL,
                             PartialModel glassL, PartialModel glassR,
                             PartialModel lampShut, PartialModel lampOpen,
                             PartialModel lampMoving, PartialModel lampDenied) {
        private static DoorModels of(String material) {
            return new DoorModels(
                    door(material, "leaf_full"),
                    door(material, "leaf_half_l"),
                    door(material, "leaf_half_r"),
                    door(material, "leaf_full_seam_hi"),
                    door(material, "leaf_full_seam_lo"),
                    door(material, "leaf_half_r_seam_hi"),
                    door(material, "leaf_half_l_seam_lo"),
                    door(material, "glass_l"),
                    door(material, "glass_r"),
                    door(material, "lamp_shut"),
                    door(material, "lamp_open"),
                    door(material, "lamp_moving"),
                    door(material, "lamp_denied"));
        }
    }

    private static final java.util.EnumMap<com.createstats.block.DoorMaterial, DoorModels>
            POWERED_DOORS = buildDoorModels();

    private static java.util.EnumMap<com.createstats.block.DoorMaterial, DoorModels> buildDoorModels() {
        final java.util.EnumMap<com.createstats.block.DoorMaterial, DoorModels> out =
                new java.util.EnumMap<>(com.createstats.block.DoorMaterial.class);
        for (com.createstats.block.DoorMaterial material : com.createstats.block.DoorMaterial.all()) {
            out.put(material, DoorModels.of(material.key()));
        }
        return out;
    }

    public static DoorModels poweredDoor(com.createstats.block.DoorMaterial material) {
        return POWERED_DOORS.get(material);
    }

    public static final PartialModel OVEN_DOOR = block("oven/door");
    public static final PartialModel OVEN_GLASS_OFF = block("oven/glass_off");
    public static final PartialModel OVEN_GLASS_ON = block("oven/glass_on");
    public static final PartialModel OVEN_ELEMENTS_OFF = block("oven/elements_off");
    public static final PartialModel OVEN_ELEMENTS_ON = block("oven/elements_on");
    public static final PartialModel OVEN_TIN = block("oven/tin");
    public static final PartialModel OVEN_KNOB = block("oven/knob");
    public static final PartialModel OVEN_BTN_OFF = block("oven/btn_off");
    public static final PartialModel OVEN_BTN_GREEN = block("oven/btn_green");
    public static final PartialModel OVEN_BTN_AMBER = block("oven/btn_amber");
    public static final PartialModel OVEN_COLON_ON = block("oven/colon_on");
    public static final PartialModel OVEN_COLON_OFF = block("oven/colon_off");
    public static final PartialModel OVEN_DIGIT_BLANK = block("oven/digit_blank");
    public static final PartialModel OVEN_DIGIT_0 = block("oven/digit_0");
    public static final PartialModel OVEN_DIGIT_1 = block("oven/digit_1");
    public static final PartialModel OVEN_DIGIT_2 = block("oven/digit_2");
    public static final PartialModel OVEN_DIGIT_3 = block("oven/digit_3");
    public static final PartialModel OVEN_DIGIT_4 = block("oven/digit_4");
    public static final PartialModel OVEN_DIGIT_5 = block("oven/digit_5");
    public static final PartialModel OVEN_DIGIT_6 = block("oven/digit_6");
    public static final PartialModel OVEN_DIGIT_7 = block("oven/digit_7");
    public static final PartialModel OVEN_DIGIT_8 = block("oven/digit_8");
    public static final PartialModel OVEN_DIGIT_9 = block("oven/digit_9");

    public static final java.util.Map<com.createstats.baking.CakeFlavor, PartialModel> OVEN_BATTER =
            bakeModels("batter_");

    public static final java.util.Map<com.createstats.baking.CakeFlavor, PartialModel> OVEN_CAKE =
            bakeModels("cake_");

    public static final PartialModel DRIVER_HELMET_VISOR = block("driver_helmet_visor");

    public static final PartialModel[] DRIVER_HELMET_VISOR_LIVERIES = {
        DRIVER_HELMET_VISOR, block("driver_helmet_visor_dark"), block("driver_helmet_visor_racing"),
        block("driver_helmet_visor_create"), block("driver_helmet_visor_punk"),
    };

    public static final PartialModel AVIATOR_HELMET_CAP = block("aviator_helmet");
    public static final PartialModel AVIATOR_HELMET_GOGGLES = block("aviator_helmet_goggles");
    public static final PartialModel AVIATOR_HELMET_STRAP = block("aviator_helmet_strap");

    public static final PartialModel BTA_G1_WHEEL = block("bta_g1/wheel");

    public static final PartialModel AERIAL_YOKE = block("vcc_mk4_aerial_cockpit/yoke");
    public static final PartialModel AERIAL_THROTTLE_L = block("vcc_mk4_aerial_cockpit/throttle_l");
    public static final PartialModel AERIAL_THROTTLE_R = block("vcc_mk4_aerial_cockpit/throttle_r");
    public static final PartialModel AERIAL_NEEDLE_ASI = block("vcc_mk4_aerial_cockpit/needle_asi");
    public static final PartialModel AERIAL_NEEDLE_ALT = block("vcc_mk4_aerial_cockpit/needle_alt");
    public static final PartialModel AERIAL_NEEDLE_TURN = block("vcc_mk4_aerial_cockpit/needle_turn");
    public static final PartialModel AERIAL_NEEDLE_HDG = block("vcc_mk4_aerial_cockpit/needle_hdg");
    public static final PartialModel AERIAL_NEEDLE_VSI = block("vcc_mk4_aerial_cockpit/needle_vsi");
    public static final PartialModel AERIAL_BUG_ALT = block("vcc_mk4_aerial_cockpit/bug_alt");
    public static final PartialModel AERIAL_BUG_HDG = block("vcc_mk4_aerial_cockpit/bug_hdg");
    public static final PartialModel AERIAL_HORIZON_STRIP = block("vcc_mk4_aerial_cockpit/horizon_strip");
    public static final PartialModel AERIAL_SYMBOL = block("vcc_mk4_aerial_cockpit/symbol");
    public static final PartialModel AERIAL_COMPASS = block("vcc_mk4_aerial_cockpit/compass");
    public static final PartialModel AERIAL_LAMP_CAUTION = block("vcc_mk4_aerial_cockpit/lamp_caution");
    public static final PartialModel AERIAL_LAMP_HELM = block("vcc_mk4_aerial_cockpit/lamp_helm");
    public static final PartialModel AERIAL_LAMP_ANCHOR_ARMED = block("vcc_mk4_aerial_cockpit/lamp_anchor_armed");
    public static final PartialModel AERIAL_LAMP_ANCHOR_ENGAGED = block("vcc_mk4_aerial_cockpit/lamp_anchor_engaged");
    public static final PartialModel AERIAL_LAMP_KEEP_ARMED = block("vcc_mk4_aerial_cockpit/lamp_keep_armed");
    public static final PartialModel AERIAL_LAMP_KEEP_ENGAGED = block("vcc_mk4_aerial_cockpit/lamp_keep_engaged");
    public static final PartialModel AERIAL_LAMP_GOTO_ARMED = block("vcc_mk4_aerial_cockpit/lamp_goto_armed");
    public static final PartialModel AERIAL_LAMP_GOTO_ENGAGED = block("vcc_mk4_aerial_cockpit/lamp_goto_engaged");
    public static final PartialModel AERIAL_LAMP_MOORED = block("vcc_mk4_aerial_cockpit/lamp_moored");

    public static final PartialModel AERIAL_DECK_TRIM_WHEEL = block("vcc_mk4_aerial_flight_deck/trim_wheel");
    public static final PartialModel BTA_G1_NEEDLE = block("bta_g1/needle");
    public static final PartialModel BTA_G1_THROTTLE = block("bta_g1/throttle");
    public static final PartialModel BTA_G1_BRAKE = block("bta_g1/brake");
    public static final PartialModel BTA_G1_DIAL_LIT = block("bta_g1/dial_lit");
    public static final PartialModel BTA_G1_GLASS = block("bta_g1/glass");
    public static final PartialModel BTA_G1_LENS_BRAKE = block("bta_g1/lens_brake");
    public static final PartialModel BTA_G1_LENS_PARK = block("bta_g1/lens_park");
    public static final PartialModel BTA_G1_LENS_HOLD = block("bta_g1/lens_hold");
    public static final PartialModel BTA_G1_LENS_REV = block("bta_g1/lens_rev");
    public static final PartialModel BTA_G1_STRIP_LEFT = block("bta_g1/strip_left");
    public static final PartialModel BTA_G1_STRIP_READY = block("bta_g1/strip_ready");
    public static final PartialModel BTA_G1_STRIP_AUTO = block("bta_g1/strip_auto");
    public static final PartialModel BTA_G1_STRIP_BOOST = block("bta_g1/strip_boost");
    public static final PartialModel BTA_G1_STRIP_RIGHT = block("bta_g1/strip_right");

    private static java.util.Map<com.createstats.baking.CakeFlavor, PartialModel> bakeModels(
            String prefix) {
        final java.util.EnumMap<com.createstats.baking.CakeFlavor, PartialModel> out =
                new java.util.EnumMap<>(com.createstats.baking.CakeFlavor.class);
        for (com.createstats.baking.CakeFlavor flavour : com.createstats.baking.CakeFlavor.values()) {
            out.put(flavour, block("oven/" + prefix + flavour.getSerializedName()));
        }
        return out;
    }

    private static PartialModel door(String material, String name) {
        return block("powered_door/" + material + "_" + name);
    }

    public static final PartialModel CINE_CROWN = block("cine_camera/tripod/crown");
    public static final PartialModel CINE_LUG = block("cine_camera/tripod/lug");
    public static final PartialModel CINE_LEG = block("cine_camera/tripod/leg");
    public static final PartialModel CINE_FOOT = block("cine_camera/tripod/foot");
    public static final PartialModel CINE_BRACE = block("cine_camera/tripod/brace");
    public static final PartialModel CINE_AZ_WHEEL = block("cine_camera/head/az_wheel");
    public static final PartialModel CINE_HEAD = block("cine_camera/head/head");
    public static final PartialModel CINE_EL_WHEEL = block("cine_camera/head/el_wheel");
    public static final PartialModel CINE_BODY = block("cine_camera/head/body");
    public static final PartialModel CINE_REEL_F = block("cine_camera/head/reel_f");
    public static final PartialModel CINE_REEL_R = block("cine_camera/head/reel_r");
    public static final PartialModel CINE_WHEEL = block("cine_camera/head/fwheel");
    public static final PartialModel CINE_DRIVER = block("cine_camera/head/driver");
    public static final PartialModel CINE_EYEGLASS = block("cine_camera/head/eyeglass");
    public static final PartialModel CINE_TALLY = block("cine_camera/head/tally");
    public static final PartialModel CINE_PLUNGER = block("cine_camera/head/plunger");
    public static final PartialModel[] CINE_FILTERS = {
        block("cine_camera/head/filter0"), block("cine_camera/head/filter1"),
        block("cine_camera/head/filter2"), block("cine_camera/head/filter3"),
    };

    public static final PartialModel SEISMIC_WEIGHT = block("seismic_scanner/weight");
    public static final PartialModel SEISMIC_ROPE = block("seismic_scanner/rope");
    public static final PartialModel SEISMIC_SHEAVE = block("seismic_scanner/sheave");
    public static final PartialModel SEISMIC_DRUM = block("seismic_scanner/drum");
    public static final PartialModel SEISMIC_RATCHET = block("seismic_scanner/ratchet");
    public static final PartialModel SEISMIC_PAWL = block("seismic_scanner/pawl");
    public static final PartialModel SEISMIC_LEVER = block("seismic_scanner/lever");
    public static final PartialModel SEISMIC_PAPER = block("seismic_scanner/paper");
    public static final PartialModel SEISMIC_PEN = block("seismic_scanner/pen");
    public static final PartialModel SEISMIC_TICK = block("seismic_scanner/tick");
    public static final PartialModel SEISMIC_LAMP = block("seismic_scanner/lamp");

    public static final PartialModel CONTACTOR_CARRIER = block("contactor/carrier");
    public static final PartialModel CONTACTOR_SLIDER = block("contactor/slider");
    public static final PartialModel CONTACTOR_EVENER = block("contactor/evener");
    public static final PartialModel CONTACTOR_BRIDGE_NO = block("contactor/bridge_no");
    public static final PartialModel CONTACTOR_BRIDGE_NC = block("contactor/bridge_nc");
    public static final PartialModel CONTACTOR_BRIDGE_NO_MIDDLE = block("contactor/bridge_no_middle");
    public static final PartialModel CONTACTOR_BRIDGE_NC_MIDDLE = block("contactor/bridge_nc_middle");
    public static final PartialModel CONTACTOR_FLAG = block("contactor/flag");
    public static final PartialModel CONTACTOR_PILOT = block("contactor/pilot");
    public static final PartialModel CONTACTOR_ARC = block("contactor/arc");
    public static final PartialModel CONTACTOR_RING = block("contactor/ring");

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "block/" + path));
    }

    public static void init() {
        com.createstats.powertrack.client.PowerTrackPartials.init();
    }
}
