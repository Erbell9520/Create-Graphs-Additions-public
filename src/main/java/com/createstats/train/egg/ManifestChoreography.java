package com.createstats.train.egg;

public final class ManifestChoreography {
    public static final int WIDTH = 12;

    public static final int HEIGHT = 18;

    public static final int FRAMES = ManifestEgg.BEATS_PER_BAR * 2;

    public static final int FRAME_TICKS = ManifestEgg.HALF_BEAT_TICKS;

    public static final int LOOP_TICKS = FRAMES * FRAME_TICKS;

    public static final String PALETTE = ".kehcdw";

    public static final String[] MOVES = {"wave in", "wave out", "step right", "step left", "point", "turn", "clap",
            "ta-da"};

    public static final int SKIN = 0xF0C29A;

    public static final int WHITE = 0xF6F2FF;

    public static final int EYE = 0x2A1820;

    public static final int[] HAIR = {0x43291B, 0x7A4424, 0xE7C66E, 0xA8452A, 0x35253D, 0x5C3A28};

    private static final String[][] POSES = {
            {
                    "............",
                    "............",
                    "....hhhh....",
                    "..whhhhhh...",
                    "..khhhkkh...",
                    ".k.hekkeh...",
                    ".k.hkkkkh...",
                    "..khhkkhh...",
                    "...kcccck...",
                    "....cccc.k..",
                    "....wwwwk...",
                    "...cccccc...",
                    "..ccdccdcc..",
                    "..dddddddd..",
                    "....k..k....",
                    "....k..k....",
                    "....w..w....",
                    "...ww..ww...",
            },

            {
                    "............",
                    "............",
                    "....hhhh....",
                    "w..hhhhhh...",
                    "k..hhhkkh...",
                    ".k.hekkeh...",
                    ".k.hkkkkh...",
                    "..khhkkhh...",
                    "...kcccck...",
                    "....cccc.k..",
                    "....wwwwk...",
                    "...cccccc...",
                    "..ccdccdcc..",
                    "..dddddddd..",
                    "....k..k....",
                    "....k..k....",
                    "....w..w....",
                    "...ww..ww...",
            },

            {
                    "............",
                    "............",
                    ".....hhhh...",
                    "....hhhhhh..",
                    "....hhhkkh..",
                    "....hekkeh..",
                    "....hkkkkh..",
                    "....hhkkhh..",
                    "....kcccckkw",
                    "...k.cccc...",
                    "..w..wwww...",
                    "....cccccc..",
                    "...ccdccdcc.",
                    "...dddddddd.",
                    ".....k..k...",
                    "....k...k...",
                    "...w....w...",
                    "..w.....ww..",
            },

            {
                    "............",
                    "............",
                    "...hhhh.....",
                    "..hhhhhh....",
                    "..hhhkkh....",
                    "..hekkeh....",
                    "..hkkkkh....",
                    "..hhkkhh....",
                    "wkkcccck....",
                    "...cccc.k...",
                    "...wwww..w..",
                    "..cccccc....",
                    ".ccdccdcc...",
                    ".dddddddd...",
                    "...k..k.....",
                    "...k...k....",
                    "...w....w...",
                    "..ww.....w..",
            },

            {
                    "w...........",
                    "k...........",
                    ".k..hhhh....",
                    ".k.hhhhhh...",
                    "..khhhkkh...",
                    "..khekkeh...",
                    "..khkkkkh...",
                    "...khkkhh...",
                    "...kcccck...",
                    "....cccc.k..",
                    "....wwwwk...",
                    "....cccccc..",
                    "...ccdccdcc.",
                    "...dddddddd.",
                    ".....k..k...",
                    ".....k..k...",
                    ".....w..w...",
                    "....ww..ww..",
            },

            {
                    "............",
                    "............",
                    "....hhhh....",
                    "...hhhhhh...",
                    "...hhhhhh...",
                    "...hhhhhh...",
                    "...hhhhhh...",
                    "...hhhhhh...",
                    "...khhhhk...",
                    "...khhhhk...",
                    "...kwwwwk...",
                    "...cccccc...",
                    "..ccdccdcc..",
                    "..dddddddd..",
                    "....k..k....",
                    "....k..k....",
                    "....w..w....",
                    "...ww..ww...",
            },

            {
                    ".....ww.....",
                    "....k..k....",
                    "...khhhhk...",
                    "..khhhhhhk..",
                    "..khhhkkhk..",
                    "..khekkehk..",
                    "..khkkkkhk..",
                    "..khhkkhhk..",
                    "...kcccck...",
                    "....cccc....",
                    "....wwww....",
                    "...cccccc...",
                    "..ccdccdcc..",
                    "..dddddddd..",
                    "....k..k....",
                    "....k..k....",
                    "....w..w....",
                    "...ww..ww...",
            },

            {
                    "w..........w",
                    "k...hhhh...k",
                    ".k.hhhhhh.k.",
                    ".k.hhhkkh.k.",
                    "..khekkehk..",
                    "..khkkkkhk..",
                    "..khhkkhhk..",
                    "...kcccck...",
                    "....cccc....",
                    "....wwww....",
                    "...cccccc...",
                    "..ccdccdcc..",
                    "..dddddddd..",
                    "....k..k....",
                    "....k..k....",
                    "...w....w...",
                    "............",
                    "............",
            },
    };

    private ManifestChoreography() {
    }

    public static int frameAt(long gameTime) {
        return (int) (Math.floorMod(gameTime, (long) LOOP_TICKS) / FRAME_TICKS);
    }

    public static String[] poseAt(long gameTime) {
        return pose(frameAt(gameTime));
    }

    public static String[] pose(int frame) {
        return POSES[Math.floorMod(frame, POSES.length)].clone();
    }

    static int poseCount() {
        return POSES.length;
    }
}
