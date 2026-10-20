package com.createstats.aviatorhelmet;

public final class AviatorModes {
    private AviatorModes() {
    }

    public static boolean gogglesActive(boolean down) {
        return down;
    }

    public static boolean hudActive(boolean down) {
        return down;
    }

    public static boolean hudInView(boolean down, boolean firstPerson, boolean riding) {
        return hudActive(down) && (firstPerson || riding);
    }

    public static boolean lensesInView(boolean firstPerson) {
        return firstPerson;
    }

    public static boolean acceptsFlick(long lastStrokeTick, long nowTick) {
        return nowTick - lastStrokeTick >= GoggleMotion.STROKE_LOCK_TICKS;
    }
}
