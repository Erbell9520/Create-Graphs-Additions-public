package com.createstats.display;

public enum CustomScreenModuleType {
    TITLE("atom_title"),
    SUBTITLE("atom_subtitle"),
    DIVIDER("atom_divider"),
    STATUS_BADGE("atom_status_badge"),
    LOGO("atom_logo"),
    BIG_VALUE("widget_big_value"),
    ANALOG_GAUGE("widget_analog_gauge"),
    STATUS_STRIP("widget_status_strip"),
    DUAL_CARD("widget_dual_card"),
    TRIPLE_CARD("widget_triple_card"),
    CHANNEL_LIST("widget_channel_list"),
    AERO_FLIGHT_DECK("scene_aero_flight_deck"),
    AEROLINE_DIRECTOR("scene_aeroline_director"),
    DIESEL_ENGINE_PAGE("scene_diesel_engine_page"),
    BATTLE_SCREEN_COMPOSITION("scene_battle_screen_composition"),
    BATTLE_TACTICAL_V2("scene_battle_tactical_v2");

    private final String serializedName;

    CustomScreenModuleType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public static CustomScreenModuleType fromSerializedName(String serializedName) {
        for (CustomScreenModuleType value : values()) {
            if (value.serializedName.equals(serializedName)) {
                return value;
            }
        }
        return null;
    }
}