package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicRig;

public final class SeismicPonderBeats {
    private SeismicPonderBeats() {
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

    public static double[] onRig(int[] rig, double px, double py, double pz) {
        return new double[] {rig[0] + px / 16.0, rig[1] + py / 16.0, rig[2] + pz / 16.0};
    }

    public static double[] lever(int[] rig) {
        return onRig(rig, 15.3, 12.6, 5.6);
    }

    public static double[] grip(int[] rig) {
        return onRig(rig, 8.0, 22.0, 4.0);
    }

    public static final int DWELL_TICKS = 10;

    public static final int SLOW = 10;

    public static final class Setup {
        public static final int DIRT = 6;
        public static final int GRASS = 9;
        public static final int RIG = 14;
        public static final int INTRO = 26;
        public static final int DECOY = 126;
        public static final int RED = 142;
        public static final int NATURAL = 230;
        public static final int DECOY_OUT = 310;
        public static final int HOLD = 336;
        public static final int HUB = 342;
        public static final int TUNE = 350;
        public static final int PICK = 362;
        public static final int SCROLL = 412;
        public static final int R48 = 418;
        public static final int RANGE = 422;
        public static final int R64 = 444;
        public static final int RELEASE = 486;
        public static final int COST = 496;
        public static final int BANK = 576;
        public static final int CABLE = 582;
        public static final int FEED = 588;
        public static final int FEED_CAPTION = 596;
        public static final int HOIST = 664;
        public static final int LENGTH = 830;

        private Setup() {
        }

        static double[] lamp(int[] rig) {
            return onRig(rig, com.createstats.seismic.SeismicGeometry.LAMP_X, com.createstats.seismic.SeismicGeometry.LAMP_Y,
                    com.createstats.seismic.SeismicGeometry.LAMP_Z);
        }

        static double[] plank() {
            final int[] p = SeismicStage.PAD_PLANK;
            return new double[] {p[0] + 0.5, p[1] + 0.5, p[2] + 0.5};
        }

        public static double[] dogs() {
            return onRig(SeismicStage.PAD_RIG, 12.5, 30.0, 6.0);
        }

        public static double[] hub() {
            final int[] r = SeismicStage.PAD_RIG;
            return new double[] {r[0] + 0.5, r[1] + 1.0, r[2] + 0.5};
        }

        public static final int HUB_DX = -100;

        public static final int[] CELL_TAG = {-18, 0};
        public static final int[] DOGS_TAG = {-62, -4};

        public static double[] cell() {
            return onRig(SeismicStage.PAD_RIG, 3.5, 2.0, 0.55);
        }

        public static com.createstats.client.ponder.fe.FePaths.Path run() {
            final int[] bank = SeismicStage.PAD_BANK;
            final int[] rig = SeismicStage.PAD_RIG;
            final int[] c = SeismicStage.PAD_CABLE;
            return com.createstats.client.ponder.fe.FePaths.floorRun(new net.minecraft.world.phys.Vec3(bank[0] + 1.0, rig[1], bank[2] + 0.5),
                    new net.minecraft.core.BlockPos[] {new net.minecraft.core.BlockPos(c[0], c[1], c[2])},
                    new net.minecraft.world.phys.Vec3(rig[0] + 5.0 / 16.0, rig[1], rig[2] + 0.5), com.createstats.power.CableTier.SURFACE);
        }

        public static double[] cable() {
            final int[] c = SeismicStage.PAD_CABLE;
            return new double[] {c[0] + 0.5, c[1] + 0.15, c[2] + 0.5};
        }

        public static java.util.List<Cue> cues() {
            final int[] g0 = SeismicStage.PAD_GROUND[0];
            final int[] g1 = SeismicStage.PAD_GROUND[1];
            return java.util.List.of(
                    Cue.free("intro", INTRO, 90, RIG + 3, true),
                    Cue.at("red", RED, 80, DECOY + 7, plank(), true),
                    Cue.at("natural", NATURAL, 70, GRASS, new double[] {g0[0] + 0.5, g1[1] + 0.9, g1[2] - 0.5}, false),
                    Cue.free("tune", TUNE, 62, HOLD, true),
                    Cue.free("range", RANGE, 60, SCROLL, false),
                    Cue.free("cost", COST, 70, RELEASE, true),
                    Cue.at("feed", FEED_CAPTION, 62, FEED, cable(), true),
                    Cue.free("hoist", HOIST, 60, FEED + 8, false),
                    Cue.at("ready", readyCaption(), 80, ready(), lamp(SeismicStage.PAD_RIG), true));
        }

        public static RigScript hero() {
            return RigScript.of("", SeismicCharge.DEFAULT_RADIUS, 0)
                    .tune(RELEASE, SeismicPonderSurvey.IRON, SeismicCharge.MAX_RADIUS)
                    .feed(FEED, SeismicCharge.WINCH_FE_PER_TICK)
                    .play(LENGTH);
        }

        public static RigScript decoy() {
            return RigScript.of("", SeismicCharge.DEFAULT_RADIUS, 0).uncoupled().play(LENGTH);
        }

        public static int ready() {
            return hero().first(SeismicRig.State.READY, FEED);
        }

        public static int readyCaption() {
            return Math.max(ready() + 8, HOIST + 66);
        }
    }

    public static final class Survey {
        public static final int FIRST_LAYER = 6;
        public static final int LAYER_STEP = 3;
        public static final int RIG = 26;
        public static final int WITNESS = 36;
        public static final int CUT = 48;
        public static final int BOWL = 56;
        public static final int RECORDER = 100;

        public static final int LEVER = 132;
        public static final int PULL = 148;

        public static final int DROP_CAPTION = PULL + 6;

        public static final int RECORDER_SPAN = 48;
        public static final int PEN_HOLD = 34;
        public static final int ECHO_HOLD = 70;
        public static final int READING_HOLD = 66;
        public static final int COAL_HOLD = 66;
        public static final int LABELS_HOLD = 70;
        public static final int BACK_HOLD = 80;

        public static final float ORBIT_DEGREES = -24f;
        public static final int ORBIT_TICKS = 40;

        private Survey() {
        }

        static int lastEchoTick() {
            return impact() + ground().lastEcho();
        }

        public static int penCaption() {
            return lastEchoTick() + 1 + 4;
        }

        public static int echoCaption() {
            return penCaption() + PEN_HOLD + 4;
        }

        public static int readingCaption() {
            return Math.max(echoCaption() + ECHO_HOLD + 4, reading() + 6);
        }

        public static int orbit() {
            return readingCaption() + 20;
        }

        public static int coalCaption() {
            return readingCaption() + READING_HOLD + 4;
        }

        public static int labelsCaption() {
            return coalCaption() + COAL_HOLD + 4;
        }

        public static int back() {
            return labelsCaption() + LABELS_HOLD + 8;
        }

        public static int backCaption() {
            return back() + 8;
        }

        public static int length() {
            return backCaption() + BACK_HOLD + 22;
        }

        public static int readingTicks() {
            return readingCaption() + READING_HOLD - reading() + 8;
        }

        public static java.util.List<Cue> cues() {
            final SeismicPonderSurvey.Survey s = ground();
            final int impact = impact();
            final int last = lastEchoTick();
            final int[] coal = SeismicStage.RIG_COAL[0];
            final double[] label = s.veins().get(0).centre();
            return java.util.List.of(
                    Cue.free("bowl", BOWL, 76, CUT, true),
                    Cue.free("drop", DROP_CAPTION, last + 1 - DROP_CAPTION, PULL, true),
                    Cue.free("pen", penCaption(), PEN_HOLD, impact, false),
                    Cue.free("echo", echoCaption(), ECHO_HOLD, last, true),
                    Cue.free("reading", readingCaption(), READING_HOLD, reading(), false),
                    Cue.at("coal", coalCaption(), COAL_HOLD, CUT, new double[] {coal[0] + 0.02, coal[1] + 0.5, coal[2] + 0.5}, false),
                    Cue.at("labels", labelsCaption(), LABELS_HOLD, last, new double[] {label[0], label[1] + 0.6, label[2]}, false),
                    Cue.free("back", backCaption(), BACK_HOLD, back(), true));
        }

        static final int PLAY = 1200;

        public static RigScript hero(SeismicPonderSurvey.Survey ground) {
            return RigScript.of(SeismicPonderSurvey.IRON, SeismicCharge.MAX_RADIUS, SeismicCharge.burstFor(SeismicCharge.MAX_RADIUS))
                    .ground(ground).pull(PULL).play(PLAY);
        }

        public static SeismicPonderSurvey.Survey ground() {
            return SeismicPonderSurvey.survey(SeismicStage.RIG, SeismicCharge.MAX_RADIUS, SeismicStage.ore(SeismicStage.RIG_VEINS));
        }

        public static int impact() {
            return hero(ground()).at(PLAY - 1).impactTick();
        }

        public static java.util.function.IntUnaryOperator slow() {
            final int impact = impact();
            final int last = impact + ground().lastEcho();
            return SlowMotion.ramp(PULL, impact, last + 3, last + 15, SLOW);
        }

        public static int reading() {
            return impact() + ground().readingTick();
        }
    }

    public static final class Reading {
        public static final int CHUNK = 6;
        public static final int FIRST_CHUNK = 6;
        public static final int CHUNK_STEP = 3;
        public static final int RIG = 26;
        public static final int PARROT = 34;
        public static final int YOU = 42;
        public static final int RECORDER = 44;

        public static final int LEVER = 44;
        public static final int PULL = 62;
        public static final int WITNESS_CAPTION = 80;
        public static final int OVERVIEW_CAPTION = 156;
        public static final int DRAIN_CAPTION = 220;
        public static final int RINGS = 284;
        public static final int RINGS_CAPTION = 290;
        public static final int WALK = 366;
        public static final int WALK_CAPTION = 374;
        public static final int LOOK = 530;
        public static final int LOOK_CAPTION = 548;

        public static final int WITNESS_RING = PULL - 8;
        public static final int WITNESS_RING_END = WITNESS_CAPTION + 70 + 4;

        public static final int TIP_RING = LOOK_CAPTION + 80 + 4;
        public static final int TIP_CAPTION = TIP_RING + 8;

        public static final int LENGTH = TIP_CAPTION + 80 + 24;
        public static final int LOOK_END = LENGTH;

        public static final int RECORDER_SPAN = 60;

        public static final double[] START = {34.6, 5.6};
        public static final double[] END = {1.5, 5.6};

        private Reading() {
        }

        public static java.util.List<Cue> cues() {
            final int impact = impact();
            final int last = impact + ground().lastEcho();
            return java.util.List.of(
                    Cue.free("witness", WITNESS_CAPTION, 70, impact + ground().veins().get(0).echo(), true),
                    Cue.free("overview", OVERVIEW_CAPTION, 60, last, false),
                    Cue.free("drain", DRAIN_CAPTION, 62, last + com.createstats.seismic.VeinVisibility.OVERVIEW_TICKS, true),
                    Cue.free("rings", RINGS_CAPTION, 70, RINGS, true),
                    Cue.free("walk", WALK_CAPTION, 70, WALK, false),
                    Cue.free("look", LOOK_CAPTION, 80, LOOK + DWELL_TICKS, true),
                    Cue.free("tip", TIP_CAPTION, 80, TIP_RING, true));
        }

        public static SeismicPonderSurvey.Survey ground() {
            return SeismicPonderSurvey.survey(SeismicStage.STRIP_RIG, SeismicCharge.MAX_RADIUS,
                    SeismicStage.ore(SeismicStage.STRIP_VEINS));
        }

        public static RigScript hero(SeismicPonderSurvey.Survey ground) {
            return RigScript.of(SeismicPonderSurvey.IRON, SeismicCharge.MAX_RADIUS, SeismicCharge.burstFor(SeismicCharge.MAX_RADIUS))
                    .ground(ground).pull(PULL).play(LENGTH);
        }

        public static int impact() {
            return hero(ground()).at(LENGTH - 1).impactTick();
        }

        public static int walkEnd() {
            return WALK + Walker.Walk.ticksFor(START[0] - END[0]);
        }

        public static Walker.Walk walk() {
            final double top = SeismicStage.STRIP_GROUND[1][1] + 1;
            final int[] rig = SeismicStage.STRIP_RIG;
            return new Walker.Walk(top, new int[] {0, WALK, walkEnd(), LENGTH},
                    new double[][] {START, START, END, END})
                    .look(new net.minecraft.world.phys.Vec3(rig[0] + 0.5, rig[1] + 1.0, rig[2] + 0.5), LOOK, LOOK_END);
        }
    }
}
