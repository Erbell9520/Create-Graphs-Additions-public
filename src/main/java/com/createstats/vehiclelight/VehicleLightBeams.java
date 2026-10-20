package com.createstats.vehiclelight;

import com.createstats.dynlight.BeamCone;
import com.createstats.dynlight.BeamLight;

// aimed slightly down, a level beam runs parallel to the road and never lands
public final class VehicleLightBeams {
    public static final double DIPPED_DOWN = 9.0;

    public static final double FULL_DOWN = 4.0;

    public static final double LENS_OFFSET = 0.3;

    public static final double DIPPED_LANDS = 10.0;

    public static final double FULL_LANDS = 21.0;

    public static final double MIN_TIP = 1.0;
    public static final double MAX_TIP = 25.0;

    public static final int UNDERLAY_DIM = 4;

    public static final int BEAM_COLOUR = 0xFFF4E2;

    private VehicleLightBeams() {
    }

    public static double restingTip(VehicleLightGlow glow) {
        return glow == VehicleLightGlow.BRIGHT ? FULL_DOWN : DIPPED_DOWN;
    }

    public static double tipFor(double height, VehicleLightGlow glow) {
        if (!(height > 0.0) || !Double.isFinite(height)) {
            return restingTip(glow);
        }
        final double lands = glow == VehicleLightGlow.BRIGHT ? FULL_LANDS : DIPPED_LANDS;
        return Math.max(MIN_TIP, Math.min(MAX_TIP, Math.toDegrees(Math.atan(height / lands))));
    }

    public static double[] angles(double restingTip, double levelledTip, HailSequence.Aim show, double bend) {
        final HailSequence.Aim a = show == null ? HailSequence.Aim.LEVELLED : show;
        return new double[] {restingTip + (levelledTip - restingTip) * a.settle() + a.down(), a.left() + bend};
    }

    public static double[] aim(double fx, double fy, double fz, VehicleLightGlow glow) {
        return aim(fx, fy, fz, restingTip(glow), 0.0);
    }

    public static double[] aim(double fx, double fy, double fz, double downDeg, double leftDeg) {
        if (Math.abs(fy) >= 0.5) {
            return new double[] {fx, fy, fz};
        }
        final double down = Math.toRadians(downDeg);
        final double c = Math.cos(down);
        final double s = Math.sin(down);
        double x = fx * c;
        final double y = fy * c - s;
        double z = fz * c;
        if (leftDeg != 0.0) {
            final double turn = Math.toRadians(leftDeg);
            final double ct = Math.cos(turn);
            final double st = Math.sin(turn);
            final double tx = x * ct + z * st;
            z = -x * st + z * ct;
            x = tx;
        }
        final double len = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / len, y / len, z / len};
    }

    public static BeamLight headlight(double[] pose, VehicleLightGlow glow) {
        return headlight(pose, glow, 0);
    }

    public static BeamLight headlight(double[] pose, VehicleLightGlow glow, int dim) {
        final boolean full = glow == VehicleLightGlow.BRIGHT;
        final int down = Math.max(0, dim);
        return BeamLight.from(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5])
                .level((full ? 14 : 12) - down)
                .reach(full ? 44 : 28)
                .fullTo(full ? 18 : 10)
                .fadeTo(full ? 44 : 28, Math.max(0, (full ? 4 : 3) - down))
                .pool(1.0, full ? 0.16 : 0.14, full ? 5.0 : 3.5)
                .spill(full ? 12 : 10)
                .build();
    }

    public static BeamCone drawn(VehicleLightGlow glow) {
        return glow == VehicleLightGlow.BRIGHT
                ? new BeamCone(5.5, 13.0, 2.5, 0.3, 18.0, 44.0, false, 6.5, BEAM_COLOUR)
                : new BeamCone(4.5, 11.0, 2.0, 0.3, 10.0, 28.0, true, 5.0, BEAM_COLOUR);
    }
}
