package com.createstats.driverhelmet;

public final class VisorModes {
    private VisorModes() {
    }

    public static boolean gogglesActive(boolean visorOpen) {
        return visorOpen;
    }

    public static boolean hudActive(boolean visorOpen) {
        return !visorOpen;
    }

    // F5 and sable's two ride cameras. FIRST_PERSON only killed the hud in every chase view. sable tax #7
    public static boolean hudInView(boolean visorOpen, boolean firstPerson, boolean riding) {
        return hudActive(visorOpen) && (firstPerson || riding);
    }

    public static boolean glassInView(boolean firstPerson) {
        return firstPerson;
    }

    public static boolean nightVisionKeyAccepted(boolean visorOpen) {
        return !visorOpen;
    }

    public static boolean nightVisionActive(boolean worn, boolean visorOpen, boolean switchedOn) {
        return worn && !visorOpen && switchedOn;
    }

    public static boolean acceptsFlick(long lastStrokeTick, long nowTick) {
        return nowTick - lastStrokeTick >= VisorMotion.STROKE_LOCK_TICKS;
    }
}
