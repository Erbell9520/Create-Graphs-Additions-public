package com.createstats.chunkloader.ponder;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.look.CrownMotion;
import com.createstats.power.WireTier;
import com.createstats.stormcoil.CoilLaw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CrownPonderScript {
    public static final CrownLaw LAW = CrownLaw.DEFAULT;

    public static final int LINE_RATE = CoilLaw.DEFAULT_BASE * WireTier.HV.multiplier();

    public static final int BUILT = 40;

    public static final int LINE_A = 100;

    public static final int LINE_B = 112;

    public static final int UNSPOOL = 20;

    public static final int MAP = 270;

    public static final int PICK = 300;

    public static final int CUT = 690;

    public static final int RESTORE = 910;

    public static final int LENGTH = 1250;

    public static final int PICKS = 25;

    static final int HEARTBEAT_TICKS = 40;

    static final int TICKING_TICKS = 10;

    public record Frame(int tick, CrownState state, boolean lineA, boolean lineB, boolean closed, int offered) {
    }

    private static List<Frame> frames;

    private CrownPonderScript() {
    }

    public static boolean landed(int stud, int tick) {
        return tick >= (stud == 0 ? LINE_A : LINE_B) + UNSPOOL;
    }

    public static boolean closed(int tick) {
        return tick < CUT || tick >= RESTORE;
    }

    public static int picks(int tick) {
        return tick >= PICK ? PICKS : 1;
    }

    public static int[] tile(int index) {
        final long c = CrownRegion.spiralAt(0, 0, index);
        return new int[] {CrownRegion.x(c), CrownRegion.z(c)};
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

    public static int firstHolding(int held, int from) {
        for (Frame f : frames()) {
            if (f.tick() >= from && f.state().held() == held) {
                return f.tick();
            }
        }
        return -1;
    }

    public static int firstDark(int from) {
        for (Frame f : frames()) {
            if (f.tick() >= from && f.state().held() == 0) {
                return f.tick();
            }
        }
        return -1;
    }

    private static List<Frame> replay() {
        final List<Frame> out = new ArrayList<>(LENGTH + 1);
        final CrownHold hold = new CrownHold();
        int held = 0;
        int heartbeat = 0;
        int sinceHeartbeat = 0;
        int ticking = 0;
        for (int t = 0; t <= LENGTH; t++) {
            final boolean a = landed(0, t);
            final boolean b = landed(1, t);
            final boolean on = closed(t);
            final int picked = picks(t);
            final long capacity = LAW.storeCapacity(picked);
            final int offered = on ? (a ? LINE_RATE : 0) + (b ? LINE_RATE : 0) : 0;
            final int rate = 2 * Math.max(LAW.totalDraw(1), LAW.totalDraw(picked));
            hold.accept(Math.min(offered, rate), capacity, LAW.totalDraw(held));
            final CrownHold.Verdict v = hold.step(held, picked, LAW, true);
            if (v == CrownHold.Verdict.ACQUIRE && held < picked) {
                held++;
            } else if (v == CrownHold.Verdict.RELEASE_ALL) {
                held = 0;
            }
            if (held > 0 && ++sinceHeartbeat >= HEARTBEAT_TICKS) {
                sinceHeartbeat = 0;
                heartbeat++;
            }
            if (t % TICKING_TICKS == 0) {
                ticking = held >= 31 ? -1 : (1 << held) - 1;
            }
            final int mask = held >= 31 ? ticking : ticking & ((1 << held) - 1);
            final int studs = (a ? 1 : 0) | (b ? 2 : 0);
            final CrownState s = new CrownState(held, picked, held, mask, hold.phase(),
                    capacity <= 0 ? 0f : (float) Math.min(1.0, hold.stored() / (double) capacity), hold.stored(),
                    capacity, LAW.totalDraw(held), LAW.totalDraw(picked),
                    (int) Math.min(Integer.MAX_VALUE, hold.lastArrived()), LAW.nextPrice(picked), picked,
                    LAW.maxPerStation(), LAW.maxPerPlayer(), studs, a ? LINE_RATE : 0, b ? LINE_RATE : 0, heartbeat,
                    LAW.lightLevel(held), held > 0 ? CrownState.Reason.NONE : CrownState.Reason.NO_POWER, 0L, false, 0, 0,
                    0, "", 0, 0);
            out.add(new Frame(t, s, a, b, on, offered));
        }
        return out;
    }

    public record Cue(String name, int tick, int hold, double[] target, int freeY, boolean keyframe) {
        public boolean free() {
            return target == null;
        }
    }

    public static final int[] CROWN = {5, 1, 7};
    public static final int[] CONNECTOR_A = {4, 2, 4};
    public static final int[] CONNECTOR_B = {6, 2, 4};
    public static final int[] BREAKER = {5, 1, 2};

    public static final int[] MAP_CENTRE = {9, 1, 3};
    public static final int PLATE = 12;
    public static final float SCALE = 0.6f;
    public static final float OFFSET_Y = 0.0f;

    public static final int FREE_Y = 40;

    public static final double[] TAG_AT = {CROWN[0] + 0.5, CROWN[1] + 3.2, CROWN[2] + 0.5};

    public static final int TAG_DX = -76;
    public static final int TAG_DY = -14;

    public static List<Cue> cues() {
        final List<Cue> c = new ArrayList<>();
        final double[] crown = {CROWN[0] + 0.5, CROWN[1] + 1.2, CROWN[2] + 0.5};
        final double[] map = {MAP_CENTRE[0] + 0.5, MAP_CENTRE[1] + 0.1, MAP_CENTRE[2] + 0.5};

        final int own = firstHolding(1, 0);
        final int fourth = firstHolding(4, PICK);
        final int past = firstHolding(21, PICK);
        final int full = firstHolding(PICKS, PICK);
        final int dark = firstDark(CUT);
        final int back = firstHolding(3, RESTORE);
        add(c, "what", 52, 70, crown, true);
        add(c, "lines", own + 8, 70, null, true);
        add(c, "map", MAP + 8, 70, null, true);
        add(c, "price", fourth + 8, 90, null, false);
        add(c, "lines_carry", past + 8, 80, null, false);

        add(c, "ultimate", Math.max(full, firstClosed()) + 6, 80, null, true);
        add(c, "ride", CUT + 10, 80, null, true);
        add(c, "all_go", dark + 8, 90, map, true);
        add(c, "comeback", back + 8, 100, null, true);
        return c;
    }

    public static int firstClosed() {
        final CrownMotion m = new CrownMotion();
        for (Frame f : frames()) {
            m.tick(f.state());
            if (m.ultimate()) {
                return f.tick();
            }
        }
        return -1;
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
