package com.createstats.vccmk4.air.core;

public final class AerialPonderChapters {
    private AerialPonderChapters() {
    }

    public static final class FirstFlight {
        public static final int INTRO = 80;
        public static final int PLACE = 180;
        public static final int SCAN = 270;
        public static final int DECIDE = 368;
        public static final int FLY = 454;
        public static final int SIT = FLY + 30;

        public static final int GAS_CAPTION = SIT + 48;
        public static final int END = 880;

        private FirstFlight() {
        }

        public static AerialPonderFlight.Script script() {
            return new AerialPonderFlight.Script(AerialPonderFlight.Start.GROUNDED, END).seat(SIT);
        }

        public static int off(AerialPonderFlight.Recording r) {
            int t = SIT;
            while (t < r.length() - 1 && r.y(t) <= r.restY() + 0.02) t++;
            return t;
        }

        public static int parked(AerialPonderFlight.Recording r) {
            int t = off(r);
            while (t < r.length() - 1 && Math.abs(r.y(t) - r.held(t)) >= 0.05) t++;
            return t;
        }
    }

    public static final class Altitude {
        public static final int RAISE = 110;
        public static final int RAISE_FOR = 50;
        public static final int TWIN_IN = 340;
        public static final int COMPARE = 380;
        public static final int COMPARE_FOR = 134;
        public static final int LOAD = 920;

        public static final double CARGO = 120.0;
        public static final int END = 1120;

        private Altitude() {
        }

        public static AerialPonderFlight.Script script() {
            return new AerialPonderFlight.Script(AerialPonderFlight.Start.HOVERING, END)
                    .lift(RAISE, RAISE + RAISE_FOR, 1.0)
                    .lift(COMPARE, COMPARE + COMPARE_FOR, 1.0)
                    .cargo(LOAD, CARGO);
        }

        public static AerialPonderFlight.Recording twin(AerialPonderFlight.Recording aerial) {
            double[] asked = new double[END - COMPARE];
            for (int k = 0; k < asked.length; k++) asked[k] = aerial.held(COMPARE + k) - aerial.y(COMPARE);
            return AerialPonderFlight.lever(asked, aerial.y(COMPARE));
        }

        public static int arrived(AerialPonderFlight.Recording aerial) {
            int t = COMPARE + COMPARE_FOR;
            while (t < aerial.length() - 1 && Math.abs(aerial.y(t) - aerial.held(t)) >= 0.05) t++;
            return t;
        }

        public static int past(AerialPonderFlight.Recording aerial, AerialPonderFlight.Recording twin) {
            double mark = aerial.held(END - 1);
            int t = COMPARE;
            while (t < END - 1 && twin.y(t - COMPARE) <= mark) t++;
            return t;
        }
    }

    public static final class Telegraph {
        public static final int SLOW = 130;
        public static final int HALF = 330;
        public static final int FULL = 530;
        public static final int STOP = 730;
        public static final int ASTERN = 1050;
        public static final int REST = 1250;
        public static final int END = 1400;

        private Telegraph() {
        }

        public static AerialPonderFlight.Script script() {
            return new AerialPonderFlight.Script(AerialPonderFlight.Start.HOVERING, END)
                    .tap(SLOW, 1).tap(HALF, 1).tap(FULL, 1)
                    .tap(STOP, -1).tap(STOP + 10, -1).tap(STOP + 20, -1)
                    .tap(ASTERN, -1).tap(REST, 1);
        }
    }
}
