package com.createstats.client.ponder.powertrack;

import com.createstats.power.FeRates;
import com.createstats.power.PortableFeLadder;
import com.createstats.powertrack.core.FeederPlan;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;

import java.util.List;

public final class PowerTrackPonderBeats {
    private PowerTrackPonderBeats() {
    }

    public record Cue(String name, int tick, int hold, int after, double[] at, int independent, boolean key) {
        static Cue free(String name, int tick, int hold, int after, boolean key) {
            return new Cue(name, tick, hold, after, null, UPPER, key);
        }

        static Cue at(String name, int tick, int hold, int after, double[] at, boolean key) {
            return new Cue(name, tick, hold, after, at, 0, key);
        }

        public int end() {
            return tick + hold;
        }
    }

    public static final int UPPER = 40;

    public static final int BANK_FE = 1_024_000;

    public static int base() {
        return FeRates.base();
    }

    public static int ceiling() {
        return RailPower.sectionCeiling(base());
    }

    public static int defaultDialFe() {
        return PortableFeLadder.feAt(PortableFeLadder.defaultDial(ceiling()), ceiling());
    }

    public static int trainDialFe() {
        final int carRate = RailPower.carRate(base(), RailPower.MAX_COLLECTORS_PER_CAR);
        return PortableFeLadder.feAt(PortableFeLadder.dialFor(2 * carRate, ceiling()), ceiling());
    }

    public static int dialValue(FeederPlan.Mode mode, int fe) {
        final int width = PortableFeLadder.maxDial(ceiling()) + 1;
        return mode.row() * width + PortableFeLadder.dialFor(fe, ceiling());
    }

    public static final int CASCADE = 2;

    public static final int CAR_STEP = 4;

    public static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }

    public static double[] lantern(int[] feeder) {
        return p(feeder[0] + 0.5, feeder[1] + 17.0 / 16.0 + 0.1, feeder[2] + 0.5);
    }

    public static double[] dial(int[] feeder) {
        return PowerTrackStage.onDoor(feeder, 0.5, 8.0 / 16.0, 3.9 / 16.0);
    }

    public static double[] dialWindow(int[] feeder) {
        return PowerTrackStage.onDoor(feeder, 4.5 / 16.0, 4.5 / 16.0, 3.9 / 16.0);
    }

    public static double[] floorWindow(int[] feeder) {
        return PowerTrackStage.onDoor(feeder, 10.5 / 16.0, 4.5 / 16.0, 3.9 / 16.0);
    }

    public static double[] underDoor(int[] feeder) {
        return PowerTrackStage.onDoor(feeder, 0.5, 0.05, 0.2);
    }

    public static double[] cableTop(int[] cable) {
        return p(cable[0] + 0.5, cable[1] + 0.15, cable[2] + 0.5);
    }

    public static double[] railAt(double x, int lineZ) {
        return p(x, PowerTrackStage.TRACK_Y + 5.6 / 16.0, lineZ + 0.5);
    }

    public static double[] lamp(int[] insulator, int i) {
        final double lampX = i == 0 ? 4.0 : 12.0;
        return p(insulator[0] + lampX / 16.0, insulator[1] + 15.7 / 16.0, insulator[2] + 0.5);
    }

    public static final class Laying {
        public static final int TRACK = 6;

        public static final int CHAIRS = 40;
        public static final double CHAIR_SPEED = 0.75;
        public static final int RAIL = 50;
        public static final double RAIL_SPEED = 0.6;
        public static final int INTRO = 48;
        public static final int CHAIRS_CAPTION = 136;
        public static final int FEEDER_CUE = 214;
        public static final int FEEDER = 236;
        public static final int TAP = 240;
        public static final int FEEDER_CAPTION = 246;
        public static final int CABLE = 334;
        public static final int BANK = 340;

        public static final int WIRED = BANK + 4;
        public static final int WIRE_CAPTION = 348;
        public static final int LIVE_CAPTION = 416;
        public static final int FIGURES = 504;
        public static final int DIAL_CUE = 584;
        public static final int SCROLL_CUE = 612;
        public static final int MODE_TAG = 600;
        public static final int MODE_TAG_END = 720;
        public static final int DIAL_CAPTION = 628;
        public static final int GOGGLES = 716;
        public static final int LENGTH = 810;

        private Laying() {
        }

        public static int trackDone() {
            return TRACK + CASCADE * (PowerTrackStage.LINE_X[1] - PowerTrackStage.LINE_X[0]);
        }

        public static int railDone() {
            return RAIL + (int) Math.ceil(PowerTrackStage.lineLength() / RAIL_SPEED);
        }

        public static double chairFront(double tick) {
            return Math.max(0.0, (tick - CHAIRS) * CHAIR_SPEED);
        }

        public static double railFront(double tick) {
            return Math.max(0.0, (tick - RAIL) * RAIL_SPEED);
        }

        public static List<Cue> cues() {
            final int[] a = PowerTrackStage.FEEDER_A;
            return List.of(
                    Cue.free("intro", INTRO, 80, CHAIRS, true),
                    Cue.at("chairs", CHAIRS_CAPTION, 70, railDone(), railAt(PowerTrackStage.LINE_X[0] + 2.25, PowerTrackStage.LINE_Z), false),
                    Cue.at("feeder", FEEDER_CAPTION, 80, FEEDER, railAt(a[0] + 0.5, PowerTrackStage.LINE_Z), true),
                    Cue.at("wire", WIRE_CAPTION, 60, BANK, cableTop(PowerTrackStage.CABLE_A), false),
                    Cue.at("live", LIVE_CAPTION, 80, WIRED, lantern(a), true),
                    Cue.free("figures", FIGURES, 70, WIRED, false),
                    Cue.free("dial", DIAL_CAPTION, 80, SCROLL_CUE, true),
                    Cue.at("goggles", GOGGLES, 70, trackDone(), railAt(PowerTrackStage.LINE_X[1] - 2.5, PowerTrackStage.LINE_Z), false));
        }

        public static PowerTrackStage.Stage stage() {
            return PowerTrackStage.line(false);
        }

        public static RailStory story(RailLayout layout) {
            return new RailStory(layout,
                    List.of(new RailStory.Station(BANK_FE, BANK_FE)),
                    List.of(new RailStory.FeederIn(0, PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST,
                            PowerTrackStage.lineAt(PowerTrackStage.FEEDER_A[0]), WIRED)),
                    List.of(new RailStory.Setting(TAP, 0, FeederPlan.Mode.SUPPLY, defaultDialFe(), 50)),
                    null, PowerTrackStage.TRACK_Y + 1, PowerTrackStage.LINE_Z, base());
        }
    }

    public static final class Collector {
        public static final int TRACK = 4;
        public static final int RAIL = 36;
        public static final double RAIL_SPEED = 0.75;
        public static final int STATION_A = 40;
        public static final int INSULATOR = 50;
        public static final int STATION_B = 54;
        public static final int CAR = 70;
        public static final int HANG = 96;
        public static final int BOGIE = 184;

        public static final int GLIDE_1 = 260;
        public static final double GLIDE_1_BLOCKS = 6.0;
        public static final int CHARGE = 330;
        public static final int STANDING = 428;

        public static final int GLIDE_2 = 480;
        public static final double GLIDE_2_BLOCKS = 7.0;

        public static final int SLOW = 4;
        public static final int SLOW_IN = GLIDE_2 + 22;
        public static final int SLOW_FULL = GLIDE_2 + 27;
        public static final int SLOW_OUT = GLIDE_2 + 43;
        public static final int SLOW_END = GLIDE_2 + 50;
        public static final int INSULATOR_CAPTION = GLIDE_2 + 30;
        public static final int GIVE = 560;
        public static final int LENGTH = 800;

        public static final double CAR_START = 0.3;

        public static final int FLOOR_B = 40;

        private Collector() {
        }

        public static int railDone() {
            return RAIL + (int) Math.ceil(PowerTrackStage.lineLength() / RAIL_SPEED);
        }

        public static double railFront(double tick) {
            return Math.max(0.0, (tick - RAIL) * RAIL_SPEED);
        }

        public static CarRun run() {
            return new CarRun(PowerTrackStage.LINE_CAR_X).glide(GLIDE_1, GLIDE_1_BLOCKS).glide(GLIDE_2, GLIDE_2_BLOCKS);
        }

        public static java.util.function.IntUnaryOperator slow() {
            return com.createstats.client.ponder.seismic.SlowMotion.ramp(SLOW_IN, SLOW_FULL, SLOW_OUT, SLOW_END, SLOW);
        }

        public static List<Cue> cues() {
            final int lineZ = PowerTrackStage.LINE_Z;
            final int[] b = PowerTrackStage.FEEDER_B;
            final double carAtCharge = run().at(CHARGE);
            final double shoe = PowerTrackStage.LINE_CAR_X + carAtCharge + PowerTrackStage.CAR_COLLECTORS[0] + 0.5 + 0.33;
            return List.of(
                    Cue.at("hang", HANG, 80, CAR + CAR_STEP, p(PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_COLLECTORS[0] + 0.5,
                            PowerTrackStage.TRACK_Y + 1.35, lineZ + 0.5), true),
                    Cue.free("bogie", BOGIE, 70, CAR + 3 * CAR_STEP, false),
                    Cue.free("charge", CHARGE, 90, GLIDE_1 + run().glides().get(0).ticks(), true),
                    Cue.at("standing", STANDING, 60, GLIDE_1 + run().glides().get(0).ticks(), railAt(shoe, lineZ), false),
                    Cue.free("insulator", INSULATOR_CAPTION, 44, SLOW_IN, true),
                    Cue.at("give", GIVE, 70, GLIDE_2 + run().glides().get(1).ticks(), dial(b), false),
                    Cue.free("floor", floorCaption(), 70, floorReached(), false));
        }

        public static int floorCaption() {
            return Math.max(GIVE + 70 + 4, floorReached() + 8);
        }

        public static int floorReached() {
            final List<RailStory.Frame> f = frames();
            int last = GLIDE_2;
            for (RailStory.Frame fr : f) {
                if (fr.carFlow() != 0) {
                    last = fr.tick();
                }
            }
            return last + 1;
        }

        public static PowerTrackStage.Stage stage() {
            return PowerTrackStage.line(true);
        }

        private static List<RailStory.Frame> cached;

        public static synchronized List<RailStory.Frame> frames() {
            if (cached == null) {
                cached = story(stage().layout()).play(LENGTH);
            }
            return cached;
        }

        public static RailStory story(RailLayout layout) {
            return new RailStory(layout,
                    List.of(new RailStory.Station(BANK_FE, BANK_FE), new RailStory.Station(BANK_FE, 0)),
                    List.of(new RailStory.FeederIn(0, PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST,
                                    PowerTrackStage.lineAt(PowerTrackStage.FEEDER_A[0]), STATION_A + 6),
                            new RailStory.FeederIn(1, PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST,
                                    PowerTrackStage.lineAt(PowerTrackStage.FEEDER_B[0]), STATION_B + 6)),
                    List.of(new RailStory.Setting(0, 0, FeederPlan.Mode.SUPPLY, trainDialFe(), 50),
                            new RailStory.Setting(0, 1, FeederPlan.Mode.COLLECT, trainDialFe(), FLOOR_B)),
                    new RailStory.CarIn(BANK_FE, (int) (BANK_FE * CAR_START), PowerTrackStage.LINE_CAR_X,
                            PowerTrackStage.CAR_COLLECTORS, run(), CAR),
                    PowerTrackStage.TRACK_Y + 1, PowerTrackStage.LINE_Z, base());
        }
    }

    public static final class Feeder {
        public static final int TRACK = 4;
        public static final int RAIL = 36;
        public static final double RAIL_SPEED = 0.75;
        public static final int STATION_A = 40;
        public static final int INSULATOR = 50;
        public static final int STATION_B = 54;
        public static final int CAR = 70;
        public static final int INTRO = 66;

        public static final int SUPPLY_CUE = 150;
        public static final int SUPPLY_CAPTION = 166;
        public static final int FLOOR_CAPTION = 232;
        public static final int GLIDE_1 = 340;
        public static final int COLLECT_CUE = 410;
        public static final int COLLECT_CAPTION = 426;

        public static final int AUTO_FLOOR_CUE = 530;
        public static final int AUTO_A_CUE = 556;
        public static final int AUTO_A = 560;
        public static final int AUTO_B_CUE = 582;
        public static final int AUTO_B = 586;
        public static final int GLIDE_2 = 600;
        public static final int AUTO_CAPTION = 668;
        public static final int GLIDE_3 = 760;
        public static final int NEEDLE_CAPTION = 838;
        public static final int LANTERN_CAPTION = 926;
        public static final int LENGTH = 1040;

        public static final double CAR_START = 0.3;
        public static final int FLOOR_A_SUPPLY = 80;
        public static final int FLOOR_B = 40;
        public static final int FLOOR_AUTO = 40;

        private Feeder() {
        }

        public static int railDone() {
            return RAIL + (int) Math.ceil(PowerTrackStage.lineLength() / RAIL_SPEED);
        }

        public static double railFront(double tick) {
            return Math.max(0.0, (tick - RAIL) * RAIL_SPEED);
        }

        public static final double AT_A = PowerTrackStage.LINE_X[0];
        public static final double TRIP = 7.0;

        public static CarRun run() {
            return new CarRun(AT_A).glide(GLIDE_1, TRIP).glide(GLIDE_2, -TRIP).glide(GLIDE_3, TRIP);
        }

        public static int autoCharging() {
            for (RailStory.Frame f : frames()) {
                if (f.tick() > AUTO_B && f.carFlow() > 0) {
                    return f.tick();
                }
            }
            return GLIDE_2 + run().glides().get(1).ticks();
        }

        public static int floorHeld() {
            for (RailStory.Frame f : frames()) {
                if (f.feeders()[0].floorHolding()) {
                    return f.tick();
                }
            }
            return SUPPLY_CAPTION;
        }

        public static List<Cue> cues() {
            final int[] a = PowerTrackStage.FEEDER_A;
            final int[] b = PowerTrackStage.FEEDER_B;
            return List.of(
                    Cue.free("intro", INTRO, 80, STATION_B + 6, true),
                    Cue.free("supply", SUPPLY_CAPTION, 60, SUPPLY_CUE, true),
                    Cue.at("floor", FLOOR_CAPTION, 70, floorHeld(), lantern(a), false),
                    Cue.free("collect", COLLECT_CAPTION, 80, COLLECT_CUE, true),
                    Cue.free("auto", AUTO_CAPTION, 100, autoCharging(), true),
                    Cue.at("needle", NEEDLE_CAPTION, 80, GLIDE_3 + run().glides().get(2).ticks(), dial(b), false),
                    Cue.at("lantern", LANTERN_CAPTION, 70, GLIDE_3 + run().glides().get(2).ticks(), lantern(a), false));
        }

        public static PowerTrackStage.Stage stage() {
            return PowerTrackStage.line(true);
        }

        private static List<RailStory.Frame> cached;

        public static synchronized List<RailStory.Frame> frames() {
            if (cached == null) {
                cached = story(stage().layout()).play(LENGTH);
            }
            return cached;
        }

        public static RailStory story(RailLayout layout) {
            return new RailStory(layout,
                    List.of(new RailStory.Station(BANK_FE, BANK_FE), new RailStory.Station(BANK_FE, 0)),
                    List.of(new RailStory.FeederIn(0, PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST,
                                    PowerTrackStage.lineAt(PowerTrackStage.FEEDER_A[0]), STATION_A + 6),
                            new RailStory.FeederIn(1, PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST,
                                    PowerTrackStage.lineAt(PowerTrackStage.FEEDER_B[0]), STATION_B + 6)),
                    List.of(new RailStory.Setting(0, 0, FeederPlan.Mode.SUPPLY, trainDialFe(), FLOOR_A_SUPPLY),
                            new RailStory.Setting(0, 1, FeederPlan.Mode.COLLECT, trainDialFe(), FLOOR_B),
                            new RailStory.Setting(AUTO_A, 0, FeederPlan.Mode.AUTO, trainDialFe(), FLOOR_AUTO),
                            new RailStory.Setting(AUTO_B, 1, FeederPlan.Mode.AUTO, trainDialFe(), FLOOR_AUTO)),
                    new RailStory.CarIn(BANK_FE, (int) (BANK_FE * CAR_START), PowerTrackStage.LINE_CAR_X,
                            PowerTrackStage.CAR_COLLECTORS, run(), CAR),
                    PowerTrackStage.TRACK_Y + 1, PowerTrackStage.LINE_Z, base());
        }
    }

    public static final class Gaps {
        public static final int TRACK = 4;
        public static final int BRANCH = 50;
        public static final int SLOPE = 54;
        public static final int RAIL = 90;
        public static final double RAIL_SPEED = 0.6;
        public static final int STATION_A = 96;
        public static final int INSULATOR = 110;
        public static final int INSULATOR_CAPTION = 140;
        public static final int STATION_B = 236;
        public static final int LAMP_CAPTION = 252;
        public static final int JUNCTION = 330;
        public static final int POINTS = 428;
        public static final int GRADE = 520;
        public static final int SLOPE_CAPTION = 618;
        public static final int CAR = 700;
        public static final int TAGS = 740;
        public static final int GAP_CAPTION = 750;
        public static final int END = 848;
        public static final int LENGTH = 950;

        private Gaps() {
        }

        public static double railFront(double tick) {
            return Math.max(0.0, (tick - RAIL) * RAIL_SPEED);
        }

        public static int slopeDone() {
            return SLOPE + CASCADE * (PowerTrackStage.YARD_SLOPE_HIGH[1] - PowerTrackStage.YARD_SLOPE_LOW[0]);
        }

        public static int railDone() {
            final double longest = PowerTrackStage.yardNode(PowerTrackStage.YARD_EAST)[0]
                    - PowerTrackStage.yardNode(PowerTrackStage.YARD_JUNCTION)[0];
            return RAIL + (int) Math.ceil(longest / RAIL_SPEED);
        }

        public static CarRun run() {
            return new CarRun(0.0);
        }

        public static List<Cue> cues() {
            final PowerTrackStage.Stage stage = stage();
            final int lineZ = PowerTrackStage.LINE_Z;
            final int[] ins = PowerTrackStage.YARD_INSULATOR;
            final RailLayout.Shape west = stage.layout().shape(PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION);
            final RailLayout.Shape curve = stage.layout().shape(PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_CURVE_END);
            final RailLayout.Shape slope = stage.layout().shape(PowerTrackStage.YARD_SLOPE_FOOT, PowerTrackStage.YARD_SLOPE_CREST);
            final RailLayout.Shape branch = stage.layout().shape(PowerTrackStage.YARD_CURVE_END, PowerTrackStage.YARD_BRANCH);

            final double westEnd = stage.layout().sections().pieces(west.lo(), west.hi()).get(1).railTo();
            final double[] jumper = west.point(westEnd + 0.2);
            final double curveStart = stage.layout().sections().pieces(curve.lo(), curve.hi()).get(0).railFrom();
            final double[] points = curve.point(curveStart);
            final double[] foot = slope.point(0.2);
            final double[] mid = slope.point(slope.length() / 2.0);
            final double[] end = branch.point(stage.layout().sections().pieces(branch.lo(), branch.hi()).get(0).railTo() - 0.2);
            return List.of(
                    Cue.at("insulator", INSULATOR_CAPTION, 90, INSULATOR, p(ins[0] + 0.5, ins[1] + 0.95, ins[2] + 0.5), true),
                    Cue.at("lamp", LAMP_CAPTION, 70, STATION_B + 6, lamp(ins, 1), false),
                    Cue.at("junction", JUNCTION, 90, railDone(), p(jumper[0], jumper[1] + 0.2, jumper[2] + 0.25), true),
                    Cue.at("points", POINTS, 80, railDone(), p(points[0], points[1] + 0.3, points[2]), false),
                    Cue.at("grade", GRADE, 90, railDone(), p(foot[0], foot[1] + 0.3, foot[2]), true),
                    Cue.at("slope", SLOPE_CAPTION, 80, railDone(), p(mid[0], mid[1] + 0.35, mid[2]), false),
                    Cue.free("gap", GAP_CAPTION, 90, TAGS, true),
                    Cue.at("end", END, 70, railDone(), p(end[0], end[1] + 0.3, end[2]), false));
        }

        private static PowerTrackStage.Stage cachedStage;

        public static synchronized PowerTrackStage.Stage stage() {
            if (cachedStage == null) {
                cachedStage = PowerTrackStage.yard();
            }
            return cachedStage;
        }

        private static List<RailStory.Frame> cached;

        public static synchronized List<RailStory.Frame> frames() {
            if (cached == null) {
                cached = story(stage().layout()).play(LENGTH);
            }
            return cached;
        }

        public static RailStory story(RailLayout layout) {
            return new RailStory(layout,
                    List.of(new RailStory.Station(BANK_FE, BANK_FE), new RailStory.Station(BANK_FE, 0)),
                    List.of(new RailStory.FeederIn(0, PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION,
                                    PowerTrackStage.yardWestAt(PowerTrackStage.YARD_FEEDER_A[0]), STATION_A + 10),
                            new RailStory.FeederIn(1, PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_EAST,
                                    PowerTrackStage.yardEastAt(PowerTrackStage.YARD_FEEDER_B[0]), STATION_B + 10)),
                    List.of(new RailStory.Setting(STATION_A, 0, FeederPlan.Mode.SUPPLY, trainDialFe(), 50),
                            new RailStory.Setting(STATION_B, 1, FeederPlan.Mode.COLLECT, trainDialFe(), 40)),
                    new RailStory.CarIn(BANK_FE, BANK_FE / 2, PowerTrackStage.YARD_CAR_X, PowerTrackStage.CAR_COLLECTORS, run(),
                            CAR),
                    PowerTrackStage.TRACK_Y + 1, PowerTrackStage.LINE_Z, base());
        }
    }
}
