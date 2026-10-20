package com.createstats.client.ponder.seismic;

import java.util.ArrayList;
import java.util.List;

public final class SeismicStage {
    private SeismicStage() {
    }

    // Y 64 so the labels don't read like bedrock
    public static final int RIG_Y = 64;

    public static double worldY(double y, int rigY) {
        return RIG_Y + (y - rigY);
    }

    public static final int PAD_PLATE = 7;
    public static final float PAD_SCALE = 1.0f;
    public static final float PAD_OFFSET_Y = -1.2f;

    public static final float PAD_PITCH = -28f;

    public static final int[][] PAD_GROUND = {{1, 1, 2}, {4, 2, 4}};
    public static final int[] PAD_RIG = {3, 3, 3};
    public static final int[] PAD_CABLE = {2, 3, 3};
    public static final int[] PAD_BANK = {1, 3, 3};
    public static final int[] PAD_PLANK = {5, 1, 1};
    public static final int[] PAD_DECOY = {5, 2, 1};

    public static final int RIG_PLATE = 9;
    public static final float RIG_SCALE = 0.6f;
    public static final float RIG_OFFSET_Y = -3.0f;

    public static final float RIG_PITCH = -26f;

    public static final int[][] RIG_GROUND = {{1, 1, 1}, {7, 6, 7}};
    public static final int[] RIG = {4, 7, 4};

    public static final int[][] NOTCH = {{1, 1, 1}, {3, 6, 3}};

    public static final int[][][] RIG_VEINS = {
            {{2, 4, 4}, {3, 4, 4}, {3, 3, 4}},
            {{6, 4, 5}, {6, 4, 6}, {5, 3, 6}, {6, 3, 6}},
            {{6, 4, 1}, {7, 4, 1}, {7, 3, 2}},
            {{4, 2, 1}, {4, 1, 2}, {4, 1, 1}},
            {{1, 2, 6}, {1, 1, 6}, {2, 1, 7}},
    };

    public static final int[][] RIG_COAL = {{4, 4, 1}, {4, 4, 2}, {5, 4, 1}};

    public static final double[] RIG_WITNESS = {5.9, 7.0, 2.4};

    public static final int STRIP_LENGTH = 36;
    public static final int STRIP_DEPTH = 9;
    public static final int STRIP_PLATE = 9;
    public static final int STRIP_PLATE_X = 13;
    public static final float STRIP_SCALE = 0.38f;

    public static final float STRIP_OFFSET_Y = -3.0f;

    public static final float STRIP_YAW = 172f;
    public static final float STRIP_PITCH = -40f;

    public static final float YAW = 145f;

    public static final int[][] STRIP_GROUND = {{0, 1, 2}, {35, 4, 6}};
    public static final int[] STRIP_RIG = {33, 5, 4};
    public static final int[][][] STRIP_VEINS = {
            {{31, 3, 4}, {30, 3, 4}, {30, 2, 5}},
            {{26, 2, 3}, {25, 2, 3}, {25, 3, 2}, {26, 1, 3}, {24, 2, 4}},
            {{21, 3, 5}, {20, 3, 5}, {20, 2, 6}, {21, 2, 4}},
            {{16, 2, 3}, {15, 2, 3}, {15, 1, 3}, {16, 1, 4}, {14, 2, 2}, {15, 3, 2}},
            {{11, 3, 5}, {10, 2, 5}, {11, 2, 6}, {10, 3, 4}},
            {{6, 2, 3}, {5, 2, 4}, {6, 1, 4}, {5, 3, 3}, {4, 2, 4}},
            {{0, 3, 5}, {1, 2, 5}, {0, 2, 4}},
    };
    public static final int[][] STRIP_COAL = {{28, 3, 6}, {28, 2, 6}, {18, 1, 2}, {18, 1, 3}, {8, 3, 2}, {8, 2, 3}};

    public static List<int[]> ore(int[][][] veins) {
        final List<int[]> out = new ArrayList<>();
        for (final int[][] v : veins) {
            for (final int[] c : v) {
                out.add(c.clone());
            }
        }
        return out;
    }

    public static boolean inside(int[][] box, int x, int y, int z) {
        return x >= box[0][0] && x <= box[1][0] && y >= box[0][1] && y <= box[1][1] && z >= box[0][2] && z <= box[1][2];
    }
}
