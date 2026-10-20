package com.createstats.stormcoil.ponder;

import com.createstats.power.WireTier;
import com.createstats.stormcoil.CoilCatch;
import com.createstats.stormcoil.CoilLaw;
import com.createstats.stormcoil.CoilStage;
import com.createstats.stormcoil.CoilStores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoilPonderScript {
    public static final int BASE = CoilLaw.DEFAULT_BASE;

    public static final WireTier IN_WIRE_TIER = WireTier.SHIELDED;

    public static final WireTier OUT_WIRE_TIER = WireTier.COPPER;

    public static final int RANGE_DRAW = 256;

    public static final int BUILT = 40;
    public static final int IN_WIRE = 70;
    public static final int OUT_WIRE = 200;
    public static final int UNSPOOL = 20;

    public static final int HEIGHT = 340;
    public static final int STAGE_STEP = 40;

    public static final int MOBS = 560;

    public static final int FIRST_VOLLEY = 592;

    public static final int HITS_TO_KILL = 4;
    public static final int HUSKS = 2;

    public static final int STORM = 700;

    public static final int STRIKE = 830;

    public static final int STORM_END = 1010;
    public static final int LENGTH = 1120;

    public record Frame(int tick, int stage, int capacitor, int capacitorSize, int bank, int bankSize, int in, int out,
                        long lastZap, int volley, int huskHits, boolean corona, boolean primed, long lastStrike,
                        boolean inWired, boolean outWired) {
    }

    private static List<Frame> frames;

    private CoilPonderScript() {
    }

    public static boolean inWired(int tick) {
        return tick >= IN_WIRE + UNSPOOL;
    }

    public static boolean outWired(int tick) {
        return tick >= OUT_WIRE + UNSPOOL;
    }

    public static int stage(int tick) {
        return tick < HEIGHT ? 1 : Math.min(4, 2 + (tick - HEIGHT) / STAGE_STEP);
    }

    public static boolean volleyTick(int tick) {
        return tick >= FIRST_VOLLEY && (tick - FIRST_VOLLEY) % CoilLaw.ZAP_INTERVAL == 0;
    }

    public static long aimAt(int tick) {
        for (int v = tick + 1; v <= tick + CoilLaw.WIND_UP; v++) {
            if (v <= LENGTH && at(v).lastZap() == v) {
                return v - CoilLaw.WIND_UP;
            }
        }
        return Long.MIN_VALUE;
    }

    public static boolean storm(int tick) {
        return tick >= STORM && tick < STORM_END;
    }

    public static boolean primed(int tick) {
        return CoilCatch.primed(tick, STRIKE);
    }

    public static synchronized List<Frame> frames() {
        if (frames == null) {
            frames = Collections.unmodifiableList(replay());
        }
        return frames;
    }

    public static Frame at(int tick) {
        final List<Frame> all = frames();
        return all.get(Math.max(0, Math.min(all.size() - 1, tick)));
    }

    public static int lastVolley() {
        int last = -1;
        for (Frame f : frames()) {
            if (f.lastZap() == f.tick()) {
                last = f.tick();
            }
        }
        return last;
    }

    private static List<Frame> replay() {
        final List<Frame> out = new ArrayList<>(LENGTH + 1);
        final CoilStores stores = new CoilStores();
        long lastZap = Long.MIN_VALUE;
        long lastStrike = Long.MIN_VALUE;
        int volley = 0;
        int hits = 0;
        final int wire = BASE * IN_WIRE_TIER.multiplier();
        for (int t = 0; t <= LENGTH; t++) {
            final CoilStage stage = CoilStage.of(stage(t));
            final CoilStores.Terms terms = CoilLaw.terms(stage, true, BASE);
            final int in = inWired(t) ? stores.receiveIn(wire, false, t, terms) : 0;
            final int given = outWired(t) ? stores.extractOut(RANGE_DRAW, false, t, terms) : 0;
            if (volleyTick(t) && hits < HITS_TO_KILL) {
                int fired = 0;
                for (int h = 0; h < HUSKS; h++) {
                    if (stores.payZap(stage.zapCost)) {
                        fired++;
                    }
                }
                if (fired > 0) {
                    volley = fired;
                    lastZap = t;
                    hits++;
                }
            }
            if (t == STRIKE) {
                stores.strike(terms);
                lastStrike = t;
            }
            out.add(new Frame(t, stage.number(), stores.capacitor(), stage.capacitorSize(), stores.bank(),
                    terms.bankSize(), in, given, lastZap, volley, hits, storm(t), primed(t), lastStrike, inWired(t),
                    outWired(t)));
        }
        return out;
    }

    public static final int PLATE = 9;
    public static final float SCALE = 0.5f;
    public static final float OFFSET_Y = -1.0f;
    public static final int FREE_Y = 40;

    public static final int[] COIL = {4, 1, 4};

    public static final int[] IN_CONNECTOR = {6, 2, 1};
    public static final int[] BREAKER = {7, 1, 0};

    public static final int[] RANGE = {2, 1, 1};
    public static final int[] OUT_CONNECTOR = {2, 2, 1};

    public static final double[][] HUSK_AT = {{7.5, 1.0, 4.5}, {7.0, 1.0, 7.0}};
    public static final double[] PIG_AT = {1.6, 1.0, 6.4};

    public record Cue(String name, int tick, int hold, double[] target, int freeY, boolean keyframe) {
        public boolean free() {
            return target == null;
        }
    }

    public static List<Cue> cues() {
        final List<Cue> c = new ArrayList<>();
        final double[] coil = {COIL[0] + 0.5, COIL[1] + 1.0, COIL[2] + 0.5};
        final double[] pig = {PIG_AT[0], PIG_AT[1] + 0.9, PIG_AT[2]};
        add(c, "what", 52, 60, coil, true);
        add(c, "in", IN_WIRE + UNSPOOL + 8, 80, null, true);
        add(c, "out", OUT_WIRE + UNSPOOL + 8, 90, null, true);
        add(c, "height", HEIGHT + 3 * STAGE_STEP + 10, 80, null, true);
        add(c, "hostile", FIRST_VOLLEY + 8, 90, pig, true);
        add(c, "storm", STORM + 20, 80, null, true);
        add(c, "bank", STRIKE + 8, 70, null, true);
        add(c, "out_gives", STRIKE + 90, 90, null, true);
        return c;
    }

    private static void add(List<Cue> c, String name, int tick, int hold, double[] target, boolean keyframe) {
        final int after = c.isEmpty() ? 0 : c.get(c.size() - 1).tick() + c.get(c.size() - 1).hold() + 4;
        c.add(new Cue(name, Math.max(tick, after), hold, target, target == null ? FREE_Y : 0, keyframe));
    }

    public static Cue cue(String name) {
        for (Cue c : cues()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("no cue " + name);
    }
}
