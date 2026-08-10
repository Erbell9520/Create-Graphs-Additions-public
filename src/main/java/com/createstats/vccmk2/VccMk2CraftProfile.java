package com.createstats.vccmk2;

import net.minecraft.util.StringRepresentable;

/** [1] */
public enum VccMk2CraftProfile implements StringRepresentable {
    AIRSHIP("airship", "Airship", "lift + thrust"),
    MULTIROTOR("multirotor", "Multirotor", "quad / hexa"),
    PLANE("plane", "Plane", "wings + surfaces"),
    VTOL("vtol", "VTOL", "hover -> forward"),
    OTOMOBIL("otomobil", "Otomobil", "4+ wheel car"),
    TANK("tank", "Tank", "differential"),
    BOAT("boat", "Boat", "thrust + yaw"),
    CUSTOM("custom", "Custom", "you decide");

    private final String id;
    private final String label;
    private final String sub;

    VccMk2CraftProfile(String id, String label, String sub) {
        this.id = id;
        this.label = label;
        this.sub = sub;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String label() {
        return label;
    }

    public String sub() {
        return sub;
    }

    /** True for ground vehicles (wheel/track drive-steer domain). */
    public boolean isGround() {
        return this == OTOMOBIL || this == TANK;
    }

    public static VccMk2CraftProfile byOrdinal(int ordinal) {
        VccMk2CraftProfile[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : AIRSHIP;
    }
}
