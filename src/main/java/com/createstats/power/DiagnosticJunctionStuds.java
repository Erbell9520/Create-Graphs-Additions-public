package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class DiagnosticJunctionStuds {
    private DiagnosticJunctionStuds() {
    }

    public static final int COUNT = 6;
    public static final int CHANNELS = 3;

    private static final double[] COLUMN_X = {4.0 / 16.0, 8.0 / 16.0, 12.0 / 16.0};
    private static final double[] ROW_Y = {13.6 / 16.0, 1.1 / 16.0};
    private static final double STUD_Z = 7.15 / 16.0;
    private static final double PICK_RADIUS = 1.45 / 16.0;

    public static boolean isStudIndex(int stud) {
        return stud >= 0 && stud < COUNT;
    }

    public static int channelOf(int stud) {
        return Math.floorMod(stud, CHANNELS);
    }

    public static int pairedStud(int stud) {
        return isTop(stud) ? stud + CHANNELS : stud - CHANNELS;
    }

    public static boolean isTop(int stud) {
        return stud >= 0 && stud < CHANNELS;
    }

    public static double localX(int stud) {
        return COLUMN_X[Math.floorMod(stud, CHANNELS)];
    }

    public static double localY(int stud) {
        return ROW_Y[isTop(stud) ? 0 : 1];
    }

    public static int pick(Direction facing, double x, double y, double z) {
        final double[] local = PduStuds.toModelSpace(facing, x, z);
        int best = -1;
        double bestDistance = PICK_RADIUS * PICK_RADIUS;
        for (int stud = 0; stud < COUNT; stud++) {
            final double dx = local[0] - localX(stud);
            final double dy = y - localY(stud);
            final double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = stud;
            }
        }
        return best;
    }

    public static Vec3 attachPoint(Direction facing, int stud) {
        final double[] block = PduStuds.toBlockSpace(facing, localX(stud), STUD_Z);
        return new Vec3(block[0], localY(stud), block[1]);
    }

    public static double[] boxOf(Direction facing, int stud) {
        final double halfX = 0.75 / 16.0;
        final double halfY = 0.75 / 16.0;
        final double halfZ = 0.24 / 16.0;
        final double[] a = PduStuds.toBlockSpace(facing, localX(stud) - halfX, STUD_Z - halfZ);
        final double[] b = PduStuds.toBlockSpace(facing, localX(stud) + halfX, STUD_Z + halfZ);
        return new double[]{
                Math.min(a[0], b[0]), localY(stud) - halfY, Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), localY(stud) + halfY, Math.max(a[1], b[1])
        };
    }
}
