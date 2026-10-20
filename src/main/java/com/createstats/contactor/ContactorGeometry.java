package com.createstats.contactor;

public final class ContactorGeometry {
    private ContactorGeometry() {
    }

    public static final double[] POLE_X = {4.5, 8.0, 11.5};

    public static final double UNIT_PITCH = 7.0;

    public static final double STROKE = 1.5;

    public static final double NO_GAP = 1.25;

    public static final double NC_HOLD = 1.0;

    public static final double ARM_Y0 = 9.5;
    public static final double ARM_Y1 = 10.1;

    public static final double CONTACT_Z = 2.7;

    public static final double BAR_PIVOT_X = 8.0;
    public static final double BAR_PIVOT_Y = 8.25;
    public static final double BAR_PIVOT_Z = 4.46;
    public static final double BAR_SPAN = 3.3;

    public static final double FLAG_PIVOT_Y = 12.7;
    public static final double FLAG_PIVOT_Z = 5.28;

    public static final double HOOD_Y1 = 13.45;
    public static final double VENT_Z = 5.1;

    public static final double PILOT_X0 = 1.22;
    public static final double PILOT_X1 = 1.98;

    public static final double LINE_Y = 14.45;
    public static final double LOAD_Y = 1.55;
    public static final double ANCHOR_Z = 2.65;

    public static final double BOX_HALF = 0.72;
    public static final double BOX_Z0 = 1.9;
    public static final double BOX_Z1 = 2.75;

    public static double[] studAnchor(int stud) {
        final int pole = ContactorLogic.poleOf(stud);
        return new double[] {POLE_X[pole], ContactorLogic.isLine(stud) ? LINE_Y : LOAD_Y, ANCHOR_Z};
    }

    public static double gapY(boolean normallyClosed) {
        return normallyClosed ? ARM_Y0 : ARM_Y1;
    }

    public static final double[][] SHAPE = {
        {0.9, 0.4, 0.0, 15.1, 15.6, 1.0},
        {3.35, 13.65, 1.0, 12.65, 15.25, 2.65},
        {3.35, 0.75, 1.0, 12.65, 2.35, 2.65},
        {2.6, 11.95, 1.0, 13.4, 13.45, 5.42},
        {1.05, 2.6, 1.0, 14.95, 11.95, 4.92},
    };
}
