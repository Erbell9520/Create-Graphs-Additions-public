package com.createstats.power.ponder;

import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
import com.createstats.power.ponder.FePonderPlant.Kind;
import com.createstats.power.ponder.FePonderPlant.Machine;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Script;
import com.createstats.power.ponder.FePonderPlant.Setup;
import com.createstats.power.ponder.FePonderPlant.Tuning;

import java.util.ArrayList;
import java.util.List;

public final class FePonderChapters {
    private FePonderChapters() {
    }

    public static final int FAN = 0;
    public static final int REPELLER = 1;
    public static final int RANGE = 2;
    public static final int MILL = 3;
    public static final int LAMP = 4;
    public static final int MACHINES = 5;

    public static final int DYNAMOS = 3;
    public static final int FULL_RPM = 256;

    public static final int WIRE_CEILING = 5 * 8_192;

    public static final int FEED_CEILING = 16_384;

    public static final int[] TIERS = {FePriority.CRITICAL.ordinal(), FePriority.HIGH.ordinal(),
            FePriority.NORMAL.ordinal(), FePriority.LOW.ordinal(), FePriority.OPTIONAL.ordinal()};

    public static List<Machine> hero(boolean full, boolean tiered) {
        final int normal = FePriority.DEFAULT.ordinal();
        final List<Machine> m = new ArrayList<>();
        m.add(new Machine("fan", tiered ? TIERS[FAN] : normal, 288, 64_000, 576, Kind.MOTOR, full ? 64_000 : 0));
        m.add(new Machine("repeller", tiered ? TIERS[REPELLER] : normal, 64, 32_768, 2_048, Kind.REPELLER,
                full ? 32_768 : 0));
        m.add(new Machine("range", tiered ? TIERS[RANGE] : normal, 256, 2_000, 512, Kind.RANGE, full ? 2_000 : 0));
        m.add(new Machine("mill", tiered ? TIERS[MILL] : normal, 72, 64_000, 144, Kind.MOTOR, full ? 64_000 : 0));
        m.add(new Machine("lamp", tiered ? TIERS[LAMP] : normal, 1, 100, 64, Kind.LAMP, full ? 100 : 0));
        return m;
    }

    public static final class Warp {
        public static final int RAMP = 12;

        private final double[] position;
        private final double[] speed;

        private Warp(double[] position, double[] speed) {
            this.position = position;
            this.speed = speed;
        }

        public int length() {
            return position.length;
        }

        public double at(int s) {
            return position[Math.max(0, Math.min(position.length - 1, s))];
        }

        public int tick(int s) {
            return (int) Math.floor(at(s) + 1.0e-9);
        }

        public double factor(int s) {
            return speed[Math.max(0, Math.min(speed.length - 1, s))];
        }

        public int scene(int replay) {
            for (int s = 0; s < position.length; s++) if (tick(s) >= replay) return s;
            return position.length - 1;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private final List<Double> position = new ArrayList<>();
            private final List<Double> speed = new ArrayList<>();
            private double at;

            private void push(double v) {
                position.add(at);
                speed.add(v);
                at += v;
            }

            public Builder hold(int sceneTicks) {
                for (int i = 0; i < sceneTicks; i++) push(0.0);
                return this;
            }

            public Builder playTo(int replayTick) {
                while (at < replayTick - 1.0e-9) push(1.0);
                return this;
            }

            public Builder playUntilScene(int sceneTick) {
                while (position.size() < sceneTick) push(1.0);
                return this;
            }

            public Builder lapseTo(int replayTick, int sceneTicks) {
                final double distance = replayTick - at;
                final int r = RAMP;
                final int t = Math.max(2 * r + 1, sceneTicks);
                final double f = (distance - r) / (t - r);
                if (f <= 1.5) return playTo(replayTick);
                for (int i = 0; i < r; i++) push(1.0 + (f - 1.0) * smooth((i + 0.5) / r));
                for (int i = 0; i < t - 2 * r; i++) push(f);
                for (int i = 0; i < r; i++) push(1.0 + (f - 1.0) * smooth(1.0 - (i + 0.5) / r));

                if (at > replayTick) {
                    final double over = at - replayTick;
                    at = replayTick;
                    final int last = position.size() - 1;
                    speed.set(last, Math.max(0.0, speed.get(last) - over));
                }
                return playTo(replayTick);
            }

            public Warp build() {
                final double[] p = new double[position.size()];
                final double[] v = new double[speed.size()];
                for (int i = 0; i < p.length; i++) {
                    p[i] = position.get(i);
                    v[i] = speed.get(i);
                }
                return new Warp(p, v);
            }

            private static double smooth(double t) {
                t = Math.max(0.0, Math.min(1.0, t));
                return t * t * (3.0 - 2.0 * t);
            }
        }
    }

    public enum CueKind {
        MOTION,
        CAPTION
    }

    public record Cue(CueKind kind, String name, int tick, int hold, boolean keyframe, String about) {
        public static Cue motion(String name, int tick) {
            return new Cue(CueKind.MOTION, name, tick, 0, false, null);
        }

        public static Cue caption(String name, int tick, int hold, boolean keyframe, String about) {
            return new Cue(CueKind.CAPTION, name, tick, hold, keyframe, about);
        }
    }

    public record Sheet(String chapter, int length, List<Cue> cues) {
        public Cue get(String name) {
            for (Cue c : cues) if (c.name().equals(name)) return c;
            throw new IllegalArgumentException(chapter + " has no cue " + name);
        }

        public int tick(String name) {
            return get(name).tick();
        }

        public List<Cue> captions() {
            final List<Cue> out = new ArrayList<>();
            for (Cue c : cues) if (c.kind() == CueKind.CAPTION) out.add(c);
            return out;
        }
    }

    public static final class Cables {
        public static final int LAY = 60;

        public static final int ANCHOR = LAY + 6;
        public static final int ROUTE = LAY + 40;
        public static final int SPIN = 200;
        public static final int GAUGE = 320;
        public static final int LADDER = 460;

        public static final int LAMP = 600;

        public static final int GOGGLES = 700;
        public static final int GOGGLES_END = 800;

        public static final int DYE = 810;
        public static final int RIP = 910;

        public static final int RIPPED = RIP + 6;

        public static final int LAMP_COAST = 100;
        public static final int END = 1_020;

        public static Sheet sheet() {
            return new Sheet("fe_cables", END, List.of(
                    Cue.motion("build", 0),
                    Cue.motion("anchor", ANCHOR),
                    Cue.caption("lay_caption", ANCHOR + 8, 90, true, "anchor"),
                    Cue.motion("route", ROUTE),
                    Cue.motion("power", SPIN),
                    Cue.caption("flow_caption", SPIN + 8, 80, true, "power"),
                    Cue.motion("gauge", GAUGE),
                    Cue.caption("gauge_caption", GAUGE + 8, 90, false, "gauge"),
                    Cue.motion("ladder", LADDER),
                    Cue.caption("ladder_caption", LADDER + 8, 90, true, "ladder"),
                    Cue.motion("lamp", LAMP),
                    Cue.caption("lamp_caption", LAMP + 8, 80, true, "lamp"),
                    Cue.motion("goggles", GOGGLES),
                    Cue.caption("goggles_caption", GOGGLES + 8, 80, false, "goggles"),
                    Cue.motion("dye", DYE),
                    Cue.caption("dye_caption", DYE + 8, 90, true, "dye"),
                    Cue.motion("rip", RIP),
                    Cue.caption("rip_caption", RIP + 8, 80, true, "rip"),
                    Cue.motion("rest", END - 20)));
        }

        public static final double[] GRADE = {0.5, 1.0, 2.0, 4.0};

        private Cables() {
        }

        public static int[] flow(Tuning tune, int ticks) {
            final int[] out = new int[ticks];
            int dynamo = 0;
            int motor = 0;
            for (int t = 0; t < ticks; t++) {
                final int made = FePonderPlant.potentialOf(FULL_RPM, tune);
                dynamo = Math.min(tune.dynamoBuffer(), dynamo + made);
                final int take = Math.min(Math.min(dynamo, tune.dynamoRate()), Math.min(576, 64_000 - motor));
                dynamo -= take;
                motor += take;
                if (motor >= 288) motor -= 288;
                out[t] = take;
            }
            return out;
        }
    }

    public static final class Pdu {
        public static final int PLACE = 70;
        public static final int LEGS = 180;

        public static final int WIRE_TICKS = 22;
        public static final int WIRE_GAP = 16;
        public static final int TIERS = 340;

        public static final int REPLAY = 440;
        public static final int POWER_AT = 20;

        public static final int LEVER_AT = 700;
        public static final int LENGTH = 1_500;

        public static final int PAYOFF = 100;
        public static final int SCREEN = 190;
        public static final int TAIL = 310;

        private Pdu() {
        }

        public static Recording replay(Tuning tune) {
            final Setup setup = new Setup(hero(false, true), DYNAMOS, WIRE_CEILING, FEED_CEILING, 0, PduMode.MANUAL, tune);
            final Script script = new Script(LENGTH);
            for (int d = 0; d < DYNAMOS; d++) script.rpm(POWER_AT, d, FULL_RPM);
            script.rpm(LEVER_AT, 1, 0).rpm(LEVER_AT, 2, 0);
            return FePonderPlant.run(setup, script);
        }

        public static int allOn(Recording r) {
            int last = POWER_AT;
            for (int i = 0; i < MACHINES; i++) last = Math.max(last, r.flip(i, true, POWER_AT));
            return last;
        }

        public static int triage(Recording r) {
            return r.flip(RANGE, false, LEVER_AT);
        }

        public static Sheet sheet(Recording r, Warp w) {
            final int power = w.scene(POWER_AT);
            final int lever = w.scene(LEVER_AT);
            final int dry = w.scene(triage(r));
            return new Sheet("fe_pdu", w.length(), List.of(
                    Cue.motion("build", 0),
                    Cue.motion("place", PLACE),
                    Cue.caption("place_caption", PLACE + 10, 80, true, "place"),
                    Cue.motion("legs", LEGS),
                    Cue.caption("legs_caption", LEGS + 8, 90, false, "legs"),
                    Cue.motion("legs_done", LEGS + 4 * WIRE_GAP + WIRE_TICKS),
                    Cue.motion("tiers", TIERS),
                    Cue.caption("tiers_caption", TIERS + 8, 90, true, "tiers"),
                    Cue.motion("power", power),
                    Cue.caption("power_caption", power + 8, 90, true, "power"),
                    Cue.motion("range_on", w.scene(r.flip(RANGE, true, POWER_AT))),
                    Cue.motion("lamp_on", w.scene(r.flip(LAMP, true, POWER_AT))),
                    Cue.motion("charge", w.scene(allOn(r) + 40)),
                    Cue.motion("lever", lever),
                    Cue.motion("drain", lever + 60),
                    Cue.motion("triage", dry),
                    Cue.caption("triage_caption", dry + 8, 90, true, "triage"),
                    Cue.motion("payoff", dry + PAYOFF),
                    Cue.caption("payoff_caption", dry + PAYOFF + 8, 70, false, "payoff"),
                    Cue.motion("screen", dry + SCREEN),
                    Cue.caption("screen_caption", dry + SCREEN + 8, 80, true, "screen"),
                    Cue.motion("rest", w.length() - 20)));
        }

        public static Warp warp(Recording r) {
            final int on = allOn(r);
            final int dry = triage(r);
            return Warp.builder()
                    .hold(REPLAY)
                    .playTo(on + 40)
                    .lapseTo(LEVER_AT - 30, 70)
                    .playTo(LEVER_AT + 60)
                    .lapseTo(dry - 30, 60)
                    .playTo(Math.min(r.length - 1, dry + TAIL))
                    .build();
        }
    }

    public static final class Auto {
        public static final int REPLAY = 70;

        public static final int STRIP = 80;
        public static final int LEVER_AT = 160;
        public static final int BACK_AT = 1_700;
        public static final int LENGTH = 2_300;
        public static final float START_FILL = 0.80f;

        public static final int PAYOFF = 70;
        public static final int LOCKS = 150;
        public static final int TAIL = 270;

        private Auto() {
        }

        public static Recording replay(Tuning tune) {
            final Setup setup = new Setup(hero(true, true), DYNAMOS, WIRE_CEILING, FEED_CEILING,
                    Math.round(tune.bufferCapacity() * START_FILL), PduMode.AUTO, tune);
            final Script script = new Script(LENGTH);
            for (int d = 0; d < DYNAMOS; d++) script.rpm(0, d, FULL_RPM);
            script.rpm(LEVER_AT, 1, 0).rpm(LEVER_AT, 2, 0).rpm(BACK_AT, 1, FULL_RPM).rpm(BACK_AT, 2, FULL_RPM);
            return FePonderPlant.run(setup, script);
        }

        public static int mark(Recording r) {
            return r.flip(RANGE, false, LEVER_AT);
        }

        public static int deeper(Recording r) {
            return r.flip(LAMP, false, mark(r));
        }

        public static int restored(Recording r) {
            return r.flip(RANGE, true, BACK_AT);
        }

        public static Sheet sheet(Recording r, Warp w) {
            final int lever = w.scene(LEVER_AT);
            final int markAt = w.scene(mark(r));
            final int deeperAt = w.scene(deeper(r));
            final int backAt = w.scene(BACK_AT);
            final int restoredAt = w.scene(restored(r));
            return new Sheet("fe_pdu_auto", w.length(), List.of(
                    Cue.motion("build", 0),
                    Cue.motion("running", REPLAY),
                    Cue.motion("strip", STRIP),
                    Cue.caption("strip_caption", STRIP + 8, 90, true, "strip"),
                    Cue.motion("lined_up", STRIP + 6 * (MACHINES - 1) + 24),
                    Cue.motion("lever", lever),
                    Cue.caption("ride_caption", lever + 8, 80, false, "lever"),
                    Cue.motion("ride", lever + 40),
                    Cue.motion("mark", markAt),
                    Cue.caption("mark_caption", markAt + 8, 90, true, "mark"),
                    Cue.motion("skip", markAt + 100),
                    Cue.caption("skip_caption", markAt + 108, 90, false, "skip"),
                    Cue.motion("sink", markAt + 200),
                    Cue.motion("deeper", deeperAt),
                    Cue.caption("deeper_caption", deeperAt + 8, 80, true, "deeper"),
                    Cue.motion("back", backAt),
                    Cue.motion("refill", backAt + 40),
                    Cue.motion("restored", restoredAt),
                    Cue.caption("restored_caption", restoredAt + 8, 90, true, "restored"),
                    Cue.motion("payoff", restoredAt + PAYOFF),
                    Cue.motion("locks", restoredAt + LOCKS),
                    Cue.caption("locks_caption", restoredAt + LOCKS + 8, 80, true, "locks"),
                    Cue.motion("rest", w.length() - 20)));
        }

        public static Warp warp(Recording r) {
            final int mark = mark(r);
            final int deeper = deeper(r);
            final int back = restored(r);
            return Warp.builder()
                    .hold(REPLAY)
                    .playTo(LEVER_AT + 60)
                    .lapseTo(mark - 40, 70)
                    .playTo(mark + 200)
                    .lapseTo(deeper - 40, 70)
                    .playTo(deeper + 110)
                    .lapseTo(BACK_AT - 20, 24)
                    .playTo(BACK_AT + 40)
                    .lapseTo(back - 40, 50)
                    .playTo(Math.min(r.length - 1, back + TAIL))
                    .build();
        }
    }

    public static final class Spans {
        public static final int STRING = 50;

        public static final int CLICK_B = 80;
        public static final int SPAN_TICKS = 22;
        public static final int POWER = 150;
        public static final int GOGGLES = 250;

        public static final int CARD_OFF = 600;
        public static final int CARD_GONE = 1_080;
        public static final int SECOND = 350;
        public static final int THIRD = 450;
        public static final int GRADES = 610;
        public static final int PAIR = 740;

        public static final int JOIN = 830;
        public static final int JOIN_TICKS = 12;
        public static final int TENSION = 900;

        public static final int REHANG = TENSION + 10;
        public static final int REHANG_STEP = 25;
        public static final com.createstats.power.WireTension[] REHUNG = {com.createstats.power.WireTension.SLACK,
                com.createstats.power.WireTension.TAUT, com.createstats.power.WireTension.DRUM,
                com.createstats.power.WireTension.STANDARD};
        public static final int STRIP = 1_010;

        public static final int STRIPPED = STRIP + 10;
        public static final int LENGTH = 1_130;

        public static final int DYNAMO = 0;
        public static final int RANGE = 1;
        public static final int LAMP = 2;

        private Spans() {
        }

        public static int landed(int span) {
            return switch (span) {
                case 0 -> CLICK_B + 4 + SPAN_TICKS;
                case 1 -> SECOND + 26 + SPAN_TICKS;
                default -> THIRD + 26 + SPAN_TICKS;
            };
        }

        public static int joined() {
            return JOIN + 14 + JOIN_TICKS;
        }

        public static FePonderGrid.Recording replay(Tuning tune) {
            final List<FePonderGrid.Part> parts = List.of(
                    new FePonderGrid.Part("dynamo", FePonderGrid.Kind.DYNAMO, tune.dynamoBuffer(), tune.dynamoRate(), 0, 0),
                    new FePonderGrid.Part("range", FePonderGrid.Kind.MACHINE, 2_000, 512, 256, 0),
                    new FePonderGrid.Part("lamp", FePonderGrid.Kind.MACHINE, 100, 64, 1, 0));
            final FePonderGrid.Script script = new FePonderGrid.Script(LENGTH)
                    .link(0, DYNAMO, true)
                    .link(landed(0), RANGE, true)
                    .rpm(POWER, DYNAMO, FULL_RPM)
                    .link(joined(), LAMP, true)
                    .link(STRIPPED, RANGE, false);
            return FePonderGrid.run(parts, script, tune.fePerRpm());
        }

        public static com.createstats.power.WireTension tensionAt(int t) {
            if (t < REHANG) return com.createstats.power.WireTension.STANDARD;
            return REHUNG[Math.min(REHUNG.length - 1, (t - REHANG) / REHANG_STEP)];
        }

        public static int spansAt(int t) {
            if (t >= STRIPPED) return 0;
            int n = 0;
            for (int i = 0; i < 3; i++) if (t >= landed(i)) n++;
            return n;
        }

        public static Sheet sheet() {
            return new Sheet("fe_wires", LENGTH, List.of(
                    Cue.motion("build", 0),
                    Cue.motion("string", STRING),
                    Cue.caption("string_caption", STRING + 8, 90, true, "string"),
                    Cue.motion("click_b", CLICK_B),
                    Cue.motion("power", POWER),
                    Cue.caption("power_caption", POWER + 8, 80, true, "power"),
                    Cue.motion("goggles", GOGGLES),
                    Cue.caption("goggles_caption", GOGGLES + 8, 90, true, "goggles"),
                    Cue.motion("second", SECOND),
                    Cue.motion("second_span", landed(1)),
                    Cue.caption("second_caption", landed(1) + 8, 90, false, "second_span"),
                    Cue.motion("third", THIRD),
                    Cue.motion("third_span", landed(2)),
                    Cue.caption("third_caption", landed(2) + 8, 90, false, "third_span"),
                    Cue.motion("grades", GRADES),
                    Cue.caption("grades_caption", GRADES + 8, 100, true, "grades"),
                    Cue.motion("pair", PAIR),
                    Cue.caption("pair_caption", PAIR + 8, 90, false, "pair"),
                    Cue.motion("join", JOIN),
                    Cue.motion("tension", TENSION),
                    Cue.caption("tension_caption", TENSION + 8, 90, false, "tension"),
                    Cue.motion("strip", STRIP),
                    Cue.caption("strip_caption", STRIP + 8, 80, true, "strip"),
                    Cue.motion("rest", LENGTH - 20)));
        }
    }

    public static final class Bank {
        public static final int PLACE = 60;

        public static final int PLACE_GAP = 8;
        public static final int[] BUILT = {1, 2, 4, 8};
        public static final int BLOCKS = 8;
        public static final int POWER = 170;

        public static final int CHARGE_FROM = 200;
        public static final int CHARGE_TICKS = 130;
        public static final int STOP = 360;

        public static final int DRAIN_FROM = 400;
        public static final int DRAIN_TICKS = 100;
        public static final int NEVER = 560;
        public static final int BREAK = 720;

        public static final int MERGE = 820;
        public static final int LENGTH = 930;

        public static final int CHARGE_REPLAY = 58_000;

        public static final int SETTLE = 40;

        public static final int DYNAMO = 0;
        public static final int RANGE = 1;
        public static final int BANK = 2;
        public static final int SPARE = 3;

        private Bank() {
        }

        public record Stock(int blockCapacity, double exponent, int transferRate) {
            public static Stock defaults() {
                return new Stock(1_024_000, 1.1, 32_768);
            }

            public int capacity(int blocks) {
                return com.createstats.power.FeBankMerge.capacity(blocks, blockCapacity, exponent);
            }
        }

        public record Run(FePonderGrid.Recording rec, Warp warp, int stopped, int empty) {
        }

        public static int placed() {
            return PLACE + PLACE_GAP * (BUILT.length - 1);
        }

        public static int builtAt(int t) {
            int n = 0;
            for (int k = 0; k < BUILT.length; k++) if (t >= PLACE + PLACE_GAP * k) n = BUILT[k];
            return n;
        }

        private static List<FePonderGrid.Part> parts(Tuning tune, Stock stock) {
            return List.of(
                    new FePonderGrid.Part("dynamo", FePonderGrid.Kind.DYNAMO, tune.dynamoBuffer(), tune.dynamoRate(), 0, 0),
                    new FePonderGrid.Part("range", FePonderGrid.Kind.MACHINE, 2_000, 512, 256, 0),
                    new FePonderGrid.Part("bank", FePonderGrid.Kind.BANK, stock.capacity(BLOCKS), stock.transferRate(), 0,
                            0),
                    new FePonderGrid.Part("spare", FePonderGrid.Kind.BANK, stock.capacity(1), stock.transferRate(), 0,
                            stock.capacity(1)));
        }

        private static Warp.Builder charged(int stopReplay) {
            return Warp.builder().playUntilScene(CHARGE_FROM)
                    .lapseTo(stopReplay - (STOP - CHARGE_FROM - CHARGE_TICKS), CHARGE_TICKS)
                    .playUntilScene(DRAIN_FROM);
        }

        private static FePonderGrid.Script script(int length, int stopped) {
            return new FePonderGrid.Script(length)
                    .link(0, DYNAMO, true)
                    .link(0, RANGE, true)
                    .link(placed(), BANK, true)
                    .rpm(POWER, DYNAMO, FULL_RPM)
                    .rpm(stopped, DYNAMO, 0);
        }

        public static Run replay(Tuning tune, Stock stock) {
            final List<FePonderGrid.Part> parts = parts(tune, stock);
            final int stopped = charged(POWER + CHARGE_REPLAY).build().tick(STOP);
            final FePonderGrid.Recording dry = FePonderGrid.run(parts,
                    script(stopped + stock.capacity(BLOCKS) / 256 + 400, stopped), tune.fePerRpm());
            int empty = dry.length - 1;
            for (int t = stopped; t < dry.length; t++) {
                if (dry.energy[BANK][t] == 0) {
                    empty = t;
                    break;
                }
            }
            final Warp warp = charged(POWER + CHARGE_REPLAY).lapseTo(empty + SETTLE, DRAIN_TICKS)
                    .playUntilScene(LENGTH).build();
            final FePonderGrid.Script all = script(warp.tick(LENGTH - 1) + 1, stopped)
                    .link(warp.tick(NEVER), SPARE, true)
                    .link(warp.tick(BREAK), SPARE, false);
            return new Run(FePonderGrid.run(parts, all, tune.fePerRpm()), warp, stopped, empty);
        }

        public static Sheet sheet(Run run) {
            return new Sheet("fe_capacitor_bank", LENGTH, List.of(
                    Cue.motion("build", 0),
                    Cue.motion("place", PLACE),
                    Cue.caption("place_caption", PLACE + 8, 90, true, "place"),
                    Cue.motion("power", POWER),
                    Cue.caption("power_caption", POWER + 8, 90, true, "power"),
                    Cue.motion("charge", CHARGE_FROM),
                    Cue.motion("stop", STOP),
                    Cue.caption("stop_caption", STOP + 8, 90, true, "stop"),
                    Cue.motion("drain", DRAIN_FROM),
                    Cue.motion("empty", run.warp().scene(run.empty())),
                    Cue.motion("never", NEVER),
                    Cue.caption("never_caption", NEVER + 8, 100, false, "never"),
                    Cue.motion("break", BREAK),
                    Cue.caption("break_caption", BREAK + 8, 90, true, "break"),
                    Cue.motion("merge", MERGE),
                    Cue.caption("merge_caption", MERGE + 8, 80, true, "merge"),
                    Cue.motion("rest", LENGTH - 20)));
        }
    }

    public static final class Controller {
        public static final int TERMINALS = 60;
        public static final int WIRES = 170;

        public static final int WIRE_GAP = 12;
        public static final int WIRE_TICKS = 14;
        public static final int POWER = 290;
        public static final int FLOOR = 400;

        public static final int CHARGE_FROM = 420;
        public static final int CHARGE_TICKS = 80;
        public static final int STOP = 540;

        public static final int DRAIN_FROM = 560;
        public static final int DRAIN_TICKS = 60;

        public static final int DROOP = 630;
        public static final int DROOP_TICKS = 100;

        public static final int DREGS = 745;
        public static final int DREGS_TICKS = 90;
        public static final int REFILL = 850;

        public static final int SCREEN = 950;
        public static final int LENGTH = 1_060;

        public static final int SETTLE = 40;

        public static final int START_PCT = 65;

        public static final int FLOOR_PCT = 70;

        public static final int DROOP_PCT = 15;
        public static final int REFILL_PCT = 6;
        public static final int RANGE = 0;
        public static final int CRITICAL_LAMP = 1;
        public static final int FLOORED_LAMP = 2;

        private Controller() {
        }

        public record Stock(int bankCapacity, int bankRate, int busCapacity, int portRate, int wireRate,
                            com.createstats.power.FeReserveBand.Band band, int rideTicks, int claimPct) {
            public static Stock defaults() {
                return new Stock(1_024_000, 32_768, 64_000, 32_768, 8_192,
                        com.createstats.power.FeReserveBand.Band.DEFAULT,
                        com.createstats.power.FeReserveBand.DEFAULT_RIDE_TICKS,
                        com.createstats.power.FeChargePolicy.DEFAULT_MAX_CLAIM_PCT);
            }
        }

        public record Run(FePonderController.Recording rec, Warp warp, int back, int stopped, int droop, int critical,
                          int low, int refilled) {
        }

        public static FePonderController.Setup setup(Tuning tune, Stock stock) {
            final int start = (int) ((long) stock.bankCapacity() * START_PCT / 100L);
            return new FePonderController.Setup(new int[] {stock.bankCapacity(), stock.bankCapacity()},
                    new int[] {start, start}, stock.bankRate(), tune.dynamoBuffer(), tune.dynamoRate(), tune.fePerRpm(),
                    stock.busCapacity(), Math.min(stock.portRate(), stock.wireRate()), stock.portRate(), stock.band(),
                    stock.rideTicks(), stock.claimPct(), List.of(
                            new FePonderController.Output(FePonderController.Load.RANGE, FePriority.NORMAL, 0),
                            new FePonderController.Output(FePonderController.Load.LAMP, FePriority.CRITICAL, 0),
                            new FePonderController.Output(FePonderController.Load.LAMP, FePriority.NORMAL, FLOOR_PCT)));
        }

        public static int wired(int port) {
            return WIRES + WIRE_GAP * port + WIRE_TICKS;
        }

        private static FePonderController.Script wiring(int length) {
            final FePonderController.Script s = new FePonderController.Script(length);
            for (int port = 0; port < 6; port++) s.wire(wired(port), port);
            return s;
        }

        public static Run replay(Tuning tune, Stock stock) {
            final FePonderController.Setup setup = setup(tune, stock);
            final int horizon = 30_000;
            final FePonderController.Recording charging = FePonderController.run(setup,
                    wiring(horizon).rpm(POWER, FULL_RPM));
            final int back = firstLit(charging, FLOORED_LAMP, POWER);
            final int stopped = Warp.builder().playUntilScene(CHARGE_FROM).lapseTo(back + SETTLE, CHARGE_TICKS)
                    .playUntilScene(DRAIN_FROM).build().tick(STOP);
            final FePonderController.Recording draining = FePonderController.run(setup,
                    wiring(stopped + horizon).rpm(POWER, FULL_RPM).rpm(stopped, 0));
            final int droop = firstBelow(draining, DROOP_PCT, stopped);
            int critical = draining.firstStance(com.createstats.power.FeReserveReport.Stance.CRITICAL, droop);
            if (critical < 0) critical = draining.length - 1;

            final int low = firstBelow(draining, REFILL_PCT, critical);
            final Warp warp = Warp.builder().playUntilScene(CHARGE_FROM).lapseTo(back + SETTLE, CHARGE_TICKS)
                    .playUntilScene(DRAIN_FROM).lapseTo(droop, DRAIN_TICKS)
                    .playUntilScene(DROOP).lapseTo(critical + SETTLE, DROOP_TICKS)
                    .playUntilScene(DREGS).lapseTo(low, DREGS_TICKS)
                    .playUntilScene(LENGTH).build();
            final int refilled = warp.tick(REFILL);
            final FePonderController.Recording rec = FePonderController.run(setup,
                    wiring(warp.tick(LENGTH - 1) + 1).rpm(POWER, FULL_RPM).rpm(stopped, 0).rpm(refilled, FULL_RPM));
            return new Run(rec, warp, back, stopped, droop, critical, low, refilled);
        }

        static int firstLit(FePonderController.Recording r, int i, int from) {
            for (int t = Math.max(0, from); t < r.length; t++) if (r.level[i][t] > 0) return t;
            return r.length - 1;
        }

        static int firstBelow(FePonderController.Recording r, int pct, int from) {
            for (int t = Math.max(0, from); t < r.length; t++) if (r.socPct[t] < pct) return t;
            return r.length - 1;
        }

        public static Sheet sheet(Run run) {
            final int critical = run.warp().scene(run.critical());
            return new Sheet("fe_battery_controller", LENGTH, List.of(
                    Cue.motion("build", 0),
                    Cue.motion("terminals", TERMINALS),
                    Cue.caption("terminals_caption", TERMINALS + 8, 90, true, "terminals"),
                    Cue.motion("wires", WIRES),
                    Cue.caption("wires_caption", WIRES + 8, 100, false, "wires"),
                    Cue.motion("power", POWER),
                    Cue.caption("power_caption", POWER + 8, 90, true, "power"),
                    Cue.motion("floor", FLOOR),
                    Cue.caption("floor_caption", FLOOR + 8, 100, false, "floor"),
                    Cue.motion("charge", CHARGE_FROM),
                    Cue.motion("back", run.warp().scene(run.back())),
                    Cue.motion("stop", STOP),
                    Cue.caption("stop_caption", STOP + 8, 80, true, "stop"),
                    Cue.motion("drain", DRAIN_FROM),
                    Cue.motion("droop", DROOP),

                    Cue.caption("droop_caption", DROOP + 8, Math.max(60, Math.min(90, critical - DROOP)), false,
                            "droop"),
                    Cue.motion("critical", critical),
                    Cue.caption("critical_caption", critical + 8, 90, false, "critical"),
                    Cue.motion("dregs", DREGS),
                    Cue.motion("refill", REFILL),
                    Cue.caption("refill_caption", REFILL + 8, 90, true, "refill"),
                    Cue.motion("screen", SCREEN),
                    Cue.caption("screen_caption", SCREEN + 8, 80, true, "screen"),
                    Cue.motion("rest", LENGTH - 20)));
        }
    }

    public static final class Deal {
        public static final int ASK = 70;
        public static final int ANSWER = 190;
        public static final int FORECAST = 300;

        public static final int HELD_AT = 60;
        public static final int PLAY = 410;

        public static final int SHARE_FROM = 590;
        public static final int SHARE_TICKS = 80;
        public static final int FLOOR_FROM = 760;
        public static final int FLOOR_TICKS = 90;

        public static final int FEED = 110;
        public static final int LENGTH = 1_110;
        public static final int SETTLE = 30;
        public static final int START_PCT = 55;
        public static final int BOARD_1 = 0;
        public static final int BOARD_2 = 1;

        public static final int RANGE_1 = 0;
        public static final int LAMP_1 = 1;
        public static final int LAMP_2 = 0;
        public static final int RANGE_2 = 1;

        private Deal() {
        }

        public record Run(FePonderDeal.Recording rec, Warp warp, int split, int lamp, int floor) {
        }

        public static FePonderDeal.Setup setup(Tuning tune, Controller.Stock stock, long controller, long board1,
                                               long board2) {
            final int start = (int) ((long) stock.bankCapacity() * START_PCT / 100L);
            return new FePonderDeal.Setup(controller, new int[] {stock.bankCapacity(), stock.bankCapacity()},
                    new int[] {start, start}, stock.bankRate(), stock.busCapacity(),
                    Math.min(stock.portRate(), stock.wireRate()), stock.portRate(), stock.band(), stock.rideTicks(),
                    stock.claimPct(), tune, stock.wireRate(), stock.wireRate(), List.of(
                            new FePonderDeal.Board(board1, List.of(
                                    new FePonderDeal.Device(FePonderDeal.Load.RANGE, FePriority.CRITICAL),
                                    new FePonderDeal.Device(FePonderDeal.Load.LAMP, FePriority.NORMAL))),
                            new FePonderDeal.Board(board2, List.of(
                                    new FePonderDeal.Device(FePonderDeal.Load.LAMP, FePriority.CRITICAL),
                                    new FePonderDeal.Device(FePonderDeal.Load.RANGE, FePriority.NORMAL)))));
        }

        public static Run replay(Tuning tune, Controller.Stock stock, long controller, long board1, long board2) {
            final FePonderDeal.Recording rec = FePonderDeal.run(setup(tune, stock, controller, board1, board2), 12_000);
            final int split = Math.max(HELD_AT + 1, rec.firstShed(BOARD_2, RANGE_2, 0));
            int lamp = rec.firstShed(BOARD_1, LAMP_1, split);
            if (lamp < 0) lamp = split + 1;
            int floor = rec.firstReason(BOARD_1, com.createstats.power.FeBoardDeal.Reason.FLOOR, lamp);
            if (floor < 0) floor = lamp + 1;
            final Warp warp = Warp.builder().playTo(HELD_AT).hold(PLAY - HELD_AT).lapseTo(split - 20, 40)
                    .playUntilScene(SHARE_FROM).lapseTo(lamp + SETTLE, SHARE_TICKS)
                    .playUntilScene(FLOOR_FROM).lapseTo(floor + SETTLE, FLOOR_TICKS)
                    .playUntilScene(LENGTH).build();
            return new Run(rec, warp, split, lamp, floor);
        }

        public static Sheet sheet(Run run) {
            final int split = run.warp().scene(run.split());
            final int floor = run.warp().scene(run.floor());
            return new Sheet("fe_board_and_reserve", LENGTH, List.of(
                    Cue.motion("build", 0),
                    Cue.motion("ask", ASK),
                    Cue.caption("ask_caption", ASK + 8, 100, true, "ask"),
                    Cue.motion("answer", ANSWER),
                    Cue.caption("answer_caption", ANSWER + 8, 90, false, "answer"),
                    Cue.motion("forecast", FORECAST),
                    Cue.caption("forecast_caption", FORECAST + 8, 100, true, "forecast"),
                    Cue.motion("play", PLAY),
                    Cue.motion("split", split),
                    Cue.caption("split_caption", split + 8, 110, true, "split"),
                    Cue.motion("share", SHARE_FROM),

                    Cue.motion("lamp", Math.max(SHARE_FROM + 1, Math.min(FLOOR_FROM - 1, run.warp().scene(run.lamp())))),
                    Cue.motion("floor_lapse", FLOOR_FROM),
                    Cue.motion("floor", floor),
                    Cue.caption("floor_caption", floor + 8, 90, false, "floor"),
                    Cue.motion("feed", floor + FEED),
                    Cue.caption("feed_caption", floor + FEED + 8, 80, true, "feed"),
                    Cue.motion("rest", LENGTH - 20)));
        }
    }
}
