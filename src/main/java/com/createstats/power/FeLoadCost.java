package com.createstats.power;

/**
 * What one device on a managed branch costs, split into the two numbers that were previously the same
 * number and should never have been.
 *
 * pure for the same reason {@link FeGridSolver} and {@link PduAutoController} are - the bug this
 * class prevents isn't a crash, it's a device costed at 32x its real draw, which compiles, runs,
 * hurts nothing on an idle rig, and quietly sheds two thirds of a branch under load. only
 * catchable by asserting on the arithmetic, and the arithmetic needs no world.
 *
 * @param budgetCost   FE per tick the Active Auto controller reserves to let this device run. Must be
 *                     stable regardless of how full the device's own buffer happens to be, or the
 *                     admit decision flaps and, worse, ratchets. See {@link FeRatedLoad}.
 * @param reportedDraw FE per tick the panel shows this device taking. What actually happened, so a row
 *                     and the load balance bar and the tier roll call all agree with each other.
 */
public record FeLoadCost(int budgetCost, int reportedDraw) {

    public static final FeLoadCost NONE = new FeLoadCost(0, 0);

    public FeLoadCost {
        budgetCost = Math.max(0, budgetCost);
        reportedDraw = Math.max(0, reportedDraw);
    }

    /** [1] */
    public static FeLoadCost rated(int ratedDraw, int measuredDraw) {
        return new FeLoadCost(ratedDraw, measuredDraw);
    }

    /** [2] */
    public static FeLoadCost consumer(int measuredDraw, int activeDrain) {
        return new FeLoadCost(measuredDraw > 0 ? measuredDraw : activeDrain, measuredDraw);
    }

    /** [3] */
    public static FeLoadCost opaque(int headroom) {
        return new FeLoadCost(headroom, headroom);
    }
}
