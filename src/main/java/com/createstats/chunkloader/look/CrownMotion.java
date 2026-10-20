package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownState;

import static com.createstats.chunkloader.look.CrownEase.flicker;
import static com.createstats.chunkloader.look.CrownEase.inCubic;
import static com.createstats.chunkloader.look.CrownEase.outBack;
import static com.createstats.chunkloader.look.CrownEase.outBounce;
import static com.createstats.chunkloader.look.CrownEase.outCubic;
import static com.createstats.chunkloader.look.CrownEase.seg;
import static com.createstats.chunkloader.look.CrownEase.smooth;
import static com.createstats.chunkloader.look.CrownGeometry.MAX;
import static com.createstats.chunkloader.look.CrownGeometry.OMEGA;
import static com.createstats.chunkloader.look.CrownGeometry.OMEGA_MIN;
import static com.createstats.chunkloader.look.CrownGeometry.TIERS;

public final class CrownMotion {
    public static final int UP_SECTOR = 8;

    public static final int UP_TIER = 13;

    public static final int ULTIMATE_CLOSE = 30;

    public static final int UNDO_ULTIMATE = 10;

    public static final int DOWN_SECTOR = 4;
    public static final int DOWN_TIER = 5;
    public static final int DOWN_TOP = 3;

    public static final int COLLAPSE = 56;

    public static final int CLAW_SHUT = 6;
    public static final int CLAW_OPEN = 9;

    public static final int PULSE = 18;

    public static final int SIGIL_FADE = 20;

    public static final int CATCH_UP = 40;

    public static final double EMBER = 0.25;

    public static final double RIPEN = 0.06;

    public static final double SPIN_RATE = OMEGA_MIN / 26.0;

    public enum Kind { NONE, UP, ULTIMATE, UNDO, DOWN, COLLAPSE }

    public static final int CUE_LATCH = 1;

    public static final int CUE_RING = 2;

    public static final int CUE_UNLATCH = 4;

    public static final int CUE_LOCK = 8;

    public static final int CUE_FOLD = 16;

    public static final int CUE_LANDED = 32;

    private int cues;

    private boolean falling;

    private final CrownPose prev = new CrownPose();
    private final CrownPose cur = new CrownPose();

    private final CrownPose from = new CrownPose();

    private boolean started;
    private long tick;
    private long lastGameTime;
    private boolean advanced;

    private int shown;

    private boolean ultimate;

    private int latched;
    private Kind kind = Kind.NONE;
    private int age;
    private int len;
    private int to;
    private double top0;
    private double r0a;
    private double r0b;
    private double horn0;
    private double horn1;
    private double hatch0;
    private double cap0;
    private double sigil0;

    private final double[] ramp = new double[MAX];

    private final double[] ripe = new double[MAX];

    private double omega;
    private double wobble;
    private double capTarget;
    private double capFrom;
    private int capAge = Integer.MAX_VALUE;
    private int pulseAge = -1;
    private int lastHeartbeat;

    public void advanceTo(long gameTime, CrownState state) {
        advanced = false;
        if (!started) {
            tick(state);
            lastGameTime = gameTime;
            advanced = true;
            return;
        }
        final long behind = gameTime - lastGameTime;
        if (behind <= 0) {
            if (behind < -CATCH_UP) {
                started = false;
                tick(state);
                lastGameTime = gameTime;
                advanced = true;
            }
            return;
        }
        if (behind > CATCH_UP) {
            started = false;
            tick(state);
        } else {
            for (long i = 0; i < behind; i++) {
                tick(state);
            }
        }
        lastGameTime = gameTime;
        advanced = true;
    }

    public boolean advanced() {
        return advanced;
    }

    public int cues() {
        return cues;
    }

    public void tick(CrownState s) {
        cues = 0;
        if (!started) {
            snap(s);
            started = true;
            prev.copyFrom(cur);
            return;
        }
        prev.copyFrom(cur);
        tick++;
        final int target = Math.max(0, Math.min(MAX, s.form()));
        final boolean dark = s.held() <= 0;

        if (dark && kind != Kind.COLLAPSE && (shown > 0 || kind != Kind.NONE)) {
            startCollapse();
        }
        if (kind == Kind.NONE) {
            choose(target);
        }
        if (kind != Kind.NONE) {
            step();
        }
        skin(s);
    }

    public void pose(float partial, CrownPose out) {
        out.lerp(prev, cur, Math.max(0f, Math.min(1f, partial)));
    }

    public CrownPose current() {
        return cur;
    }

    public int shown() {
        return shown;
    }

    public Kind kind() {
        return kind;
    }

    public int stepAge() {
        return age;
    }

    public boolean ultimate() {
        return ultimate;
    }

    public long ticks() {
        return tick;
    }

    private void snap(CrownState s) {
        final int f = Math.max(0, Math.min(MAX, s.form()));
        cur.setSettled(f);
        shown = f;
        latched = f;
        ultimate = f == MAX;
        kind = Kind.NONE;
        for (int i = 0; i < MAX; i++) {
            ramp[i] = i < f ? 1.0 : 0.0;
            ripe[i] = s.ticking(i) ? 1.0 : EMBER;
        }
        cur.store = clamp(s.store());
        omega = s.held() > 0 || cur.store > 5e-4 ? speedFor(cur.store) : 0.0;
        final boolean fed = s.phase() == CrownHold.Phase.FED;
        cur.capOpen = ultimate && fed ? 1.0 : 0.0;
        capTarget = cur.capOpen;
        capAge = Integer.MAX_VALUE;
        cur.sigilAlpha = ultimate && fed ? 1.0 : 0.0;
        cur.arch = 0.0;
        cur.intensity = CrownGeometry.drawLevel(s.drawFePerTick());
        cur.loadA = s.studLoad(0);
        cur.loadB = s.studLoad(1);
        lastHeartbeat = s.heartbeat();
        pulseAge = -1;
        skin(s);
    }

    private void choose(int target) {
        if (shown == MAX && target == MAX && !ultimate) {
            kind = Kind.ULTIMATE;
            len = ULTIMATE_CLOSE;
            age = 0;
            sigil0 = cur.sigilAlpha;
        } else if (shown == MAX && ultimate && target < MAX) {
            kind = Kind.UNDO;
            len = UNDO_ULTIMATE;
            age = 0;
            cap0 = cur.capOpen;
            sigil0 = cur.sigilAlpha;
        } else if (shown < target) {
            startUp(shown + 1);
        } else if (shown > target) {
            startDown(shown);
        }
    }

    private static boolean newTier(int k) {
        return k > 1 && CrownGeometry.slotOf(k) == 0;
    }

    private void startUp(int n) {
        kind = Kind.UP;
        to = n;
        age = 0;
        final int t = CrownGeometry.tierOf(n);
        final boolean nt = newTier(n);
        len = nt ? UP_TIER : UP_SECTOR;
        top0 = cur.stemTop;
        r0a = cur.ring0Y;
        r0b = CrownGeometry.ring0YFor(n);
        horn0 = cur.hornTop;
        horn1 = CrownGeometry.hornTopFor(n);
        hatch0 = cur.hatch;
        if (nt && t == TIERS - 1) {
            cur.yokeStub = 0.0;
        }
    }

    private void startDown(int n) {
        kind = Kind.DOWN;
        to = n - 1;
        age = 0;
        final int t = CrownGeometry.tierOf(n);
        final boolean last = newTier(n);
        len = DOWN_SECTOR + (last ? DOWN_TIER : 0) + (last && t == TIERS - 1 ? DOWN_TOP : 0);
        top0 = cur.stemTop;
        horn0 = cur.hornTop;
        horn1 = CrownGeometry.hornTopFor(n - 1);
        r0a = cur.ring0Y;
        r0b = CrownGeometry.ring0YFor(n - 1);
        hatch0 = cur.hatch;
    }

    private void startCollapse() {
        kind = Kind.COLLAPSE;
        len = COLLAPSE;
        age = 0;
        from.copyFrom(cur);
        ultimate = false;
        latched = 0;
        cues |= CUE_FOLD;
        falling = false;
        for (int i = 0; i < MAX; i++) {
            falling |= from.mount[i] > 0.0;
        }
    }

    private void step() {
        age++;
        final double u = Math.min(1.0, age / (double) len);
        switch (kind) {
            case UP -> stepUp(u);
            case DOWN -> stepDown(u);
            case ULTIMATE -> {
                cur.yokeSwing = outBack(seg(u, 0.0, 0.4), 1.2);
                cur.yokeExt = outBack(seg(u, 0.25, 0.62), 0.9);
                if (age >= len) {
                    ultimate = true;
                    cur.yokeSwing = 1.0;
                    cur.yokeExt = 1.0;
                    kind = Kind.NONE;
                    cues |= CUE_LOCK;
                }
            }
            case UNDO -> {
                cur.yokeExt = 1.0 - smooth(seg(u, 0.0, 0.5));
                cur.yokeSwing = 1.0 - smooth(seg(u, 0.3, 0.9));
                if (age >= len) {
                    ultimate = false;
                    cur.yokeSwing = 0.0;
                    cur.yokeExt = 0.0;
                    kind = Kind.NONE;
                }
            }
            case COLLAPSE -> stepCollapse(u);
            default -> {
            }
        }
    }

    private void stepUp(double u) {
        final int n = to;
        final int t = CrownGeometry.tierOf(n);
        final boolean nt = newTier(n);
        final double mA;
        final double mB;
        final double gA;
        final double latch;
        if (nt) {
            cur.stemTop = top0 + (CrownGeometry.stemTopFor(t + 1, cur.ring0Y) - top0) * outBack(seg(u, 0.0, 0.4), 1.4);
            cur.tierUp[t] = outBack(seg(u, 0.12, 0.55), 1.6);
            cur.hornTop = horn0 + (horn1 - horn0) * smooth(seg(u, 0.1, 0.55));
            if (t == TIERS - 1) {
                cur.yokeStub = outBack(seg(u, 0.55, 0.85), 1.5);
            }
            mA = 0.45;
            mB = 0.85;
            gA = 0.7;
            latch = 0.72;
        } else {
            if (t == 0) {
                cur.ring0Y = r0a + (r0b - r0a) * outBack(seg(u, 0.0, 0.6), 1.3);
                cur.stemTop = CrownGeometry.stemTopFor(1, cur.ring0Y);
            }
            mA = 0.0;
            mB = 0.7;
            gA = 0.45;
            latch = 0.55;
        }
        if (n == 1) {
            cur.hatch = hatch0 + (1.0 - hatch0) * smooth(seg(u, 0.0, 0.45));
        }
        cur.mount[n - 1] = u > mA ? outBack(seg(u, mA, mB), 1.7) : 0.0;
        ramp[n - 1] = smooth(seg(u, gA, 1.0));
        if (u >= latch && latched < n) {
            cues |= CUE_LATCH | (nt ? CUE_RING : 0);
        }
        if (u >= latch) {
            latched = n;
        }
        if (age >= len) {
            cur.mount[n - 1] = 1.0;
            ramp[n - 1] = 1.0;
            shown = n;
            latched = n;
            kind = Kind.NONE;
        }
    }

    private void stepDown(double u) {
        final int n = to + 1;
        final int i = n - 1;
        final int t = CrownGeometry.tierOf(n);
        final boolean last = newTier(n);
        final double a = DOWN_SECTOR / (double) len;
        final double su = seg(u, 0.0, a);
        ramp[i] = 1.0 - smooth(seg(su, 0.0, 0.4));
        cur.mount[i] = 1.0 - inCubic(seg(su, 0.15, 1.0));
        if (su >= 0.2 && latched >= n) {
            cues |= CUE_UNLATCH;
        }
        if (su >= 0.2) {
            latched = n - 1;
        }
        if (t == 0) {
            cur.ring0Y = r0a + (r0b - r0a) * smooth(su);
            cur.stemTop = CrownGeometry.stemTopFor(1, cur.ring0Y);
            if (n == 1) {
                cur.hatch = hatch0 * (1.0 - smooth(su));
            }
        }
        if (last) {
            double tu = seg(u, a, 1.0);
            if (t == TIERS - 1) {
                cur.yokeStub = 1.0 - smooth(seg(tu, 0.0, 0.35));
                tu = seg(tu, 0.3, 1.0);
            }
            cur.tierUp[t] = 1.0 - inCubic(tu);
            cur.stemTop = top0 + (CrownGeometry.stemTopFor(t, cur.ring0Y) - top0) * smooth(seg(tu, 0.2, 1.0));
            cur.hornTop = horn0 + (horn1 - horn0) * smooth(seg(tu, 0.0, 0.9));
        }
        if (age >= len) {
            cur.mount[i] = 0.0;
            ramp[i] = 0.0;
            shown = n - 1;
            latched = n - 1;
            if (last) {
                cur.tierUp[t] = 0.0;
            }
            kind = Kind.NONE;
        }
    }

    private void stepCollapse(double u) {
        final double drop = seg(u, 0.06, 0.36);
        final double home = Math.max(0.0, 1.0 - outBounce(drop));
        for (int i = 0; i < MAX; i++) {
            ramp[i] = 0.0;
            cur.mount[i] = from.mount[i] > 0.0 ? from.mount[i] * home : 0.0;
        }
        // outBounce first lands at 1/2.75

        if (falling && drop >= 1.0 / 2.75) {
            falling = false;
            cues |= CUE_LANDED;
        }
        cur.yokeExt = from.yokeExt * (1.0 - smooth(seg(u, 0.06, 0.28)));
        cur.yokeSwing = from.yokeSwing * (1.0 - smooth(seg(u, 0.18, 0.4)));
        cur.yokeStub = from.yokeStub * (1.0 - smooth(seg(u, 0.36, 0.48)));
        int live = 0;
        for (int t = TIERS - 1; t >= 1; t--) {
            final int k = TIERS - 1 - t;
            cur.tierUp[t] = from.tierUp[t] * (1.0 - inCubic(seg(u, 0.32 + 0.1 * k, 0.48 + 0.1 * k)));
            if (live == 0 && cur.tierUp[t] > 0.02) {
                live = t;
            }
        }
        cur.ring0Y = from.ring0Y + (CrownGeometry.RING0_LOW - from.ring0Y) * smooth(seg(u, 0.75, 1.0));
        final double target = live > 0 ? CrownGeometry.stemTopFor(live + 1, cur.ring0Y)
                : CrownGeometry.stemTopFor(1, cur.ring0Y);
        cur.stemTop += (target - cur.stemTop) * 0.35;
        cur.hornTop = from.hornTop + (CrownGeometry.HORN_ROOT_Y - from.hornTop) * smooth(seg(u, 0.45, 0.95));
        cur.hatch = from.hatch * (1.0 - smooth(seg(u, 0.85, 1.0)));
        if (age >= len) {
            for (int i = 0; i < MAX; i++) {
                cur.mount[i] = 0.0;
            }
            for (int t = 1; t < TIERS; t++) {
                cur.tierUp[t] = 0.0;
            }
            cur.tierUp[0] = 1.0;
            cur.yokeExt = 0.0;
            cur.yokeSwing = 0.0;
            cur.yokeStub = 0.0;
            cur.ring0Y = CrownGeometry.RING0_LOW;
            cur.stemTop = CrownGeometry.stemTopFor(1, cur.ring0Y);
            cur.hornTop = CrownGeometry.HORN_ROOT_Y;
            cur.hatch = 0.0;
            shown = 0;
            latched = 0;
            kind = Kind.NONE;
        }
    }

    private void skin(CrownState s) {
        final boolean dark = s.held() <= 0;
        final boolean fed = !dark && s.phase() == CrownHold.Phase.FED;
        final boolean riding = !dark && s.phase() == CrownHold.Phase.RIDING;
        final double u = kind == Kind.NONE ? 1.0 : Math.min(1.0, age / (double) len);

        final double want = clamp(s.store());
        cur.store += (want - cur.store) * 0.35;
        if (Math.abs(want - cur.store) < 1e-3) {
            cur.store = want;
        }
        final double target = !dark || cur.store > 5e-4 ? speedFor(cur.store) : 0.0;
        omega += Math.max(-SPIN_RATE, Math.min(SPIN_RATE, target - omega));
        for (int t = 0; t < TIERS; t++) {
            cur.tierAngle[t] += CrownGeometry.dir(t) * OMEGA * omega;
        }
        cur.sigilAngle += 0.9 * omega;
        cur.haloPhase += 2.25;
        cur.arcSeed = tick / 2;

        if (riding) {
            wobble += 0.9 * omega;
        }
        final double sag = 0.25 + 0.75 * cur.store;
        final double wob = 1.0 - 0.22 * (0.5 + 0.5 * Math.sin(wobble));
        for (int i = 0; i < MAX; i++) {
            final double ripeTo = s.ticking(i) ? 1.0 : EMBER;
            ripe[i] += Math.max(-RIPEN, Math.min(RIPEN, ripeTo - ripe[i]));
            if (kind == Kind.COLLAPSE || ramp[i] <= 1e-3) {
                cur.ink[i] = CrownPose.INK_OFF;
                cur.glow[i] = 0.0;
            } else if (riding) {
                cur.ink[i] = CrownPose.INK_WARN;
                cur.glow[i] = Math.max(0.05, Math.min(1.0, sag * wob)) * ramp[i];
            } else {
                cur.ink[i] = CrownPose.INK_ROSE;
                cur.glow[i] = ramp[i] * ripe[i];
            }
        }
        cur.dim = riding ? 0.45 + 0.55 * cur.store * wob : 1.0;
        cur.arcScale = riding ? 0.25 + 0.75 * cur.store : 1.0;
        cur.feed = !dark && s.intakeFePerTick() > 0;
        cur.intensity += (CrownGeometry.drawLevel(s.drawFePerTick()) - cur.intensity) * 0.2;
        cur.spin = OMEGA * omega;

        final boolean crowned = ultimate && shown == MAX && fed;
        cur.shockRadius = 0.0;
        cur.shockAlpha = 0.0;
        switch (kind) {
            case ULTIMATE -> {
                cur.capOpen = fed ? outBack(seg(u, 0.55, 0.85), 1.4) : approach(cur.capOpen, 0.0, 1.0 / CLAW_SHUT);
                cur.sigilAlpha = fed ? smooth(seg(u, 0.6, 1.0)) : approach(cur.sigilAlpha, 0.0, 1.0 / SIGIL_FADE);
                cur.arch = fed ? smooth(seg(u, 0.7, 0.9)) * flicker(tick) : 0.0;
                final double w = seg(u, 0.6, 1.0);
                if (w > 0.0 && w < 1.0) {
                    cur.shockRadius = CrownGeometry.RING_R[TIERS - 1] + 3.0 + 30.0 * outCubic(w);
                    cur.shockAlpha = (1.0 - w) * 0.9;
                }
                capTarget = fed ? 1.0 : 0.0;
                capAge = Integer.MAX_VALUE;
            }
            case UNDO -> {
                cur.capOpen = Math.min(cap0, 1.0 - smooth(seg(u, 0.0, 0.4)));
                cur.sigilAlpha = sigil0 * (1.0 - smooth(u));
                cur.arch = 0.0;
                capTarget = 0.0;
                capAge = Integer.MAX_VALUE;
            }
            case COLLAPSE -> {
                cur.capOpen = 0.0;
                cur.sigilAlpha = 0.0;
                cur.arch = 0.0;
                capTarget = 0.0;
                capAge = Integer.MAX_VALUE;
            }
            default -> {
                final double capWant = crowned ? 1.0 : 0.0;
                if (capWant != capTarget) {
                    capTarget = capWant;
                    capFrom = cur.capOpen;
                    capAge = 0;
                }
                if (capAge != Integer.MAX_VALUE) {
                    capAge++;
                    if (capTarget > capFrom) {
                        cur.capOpen = capFrom + (capTarget - capFrom) * outBack(capAge / (double) CLAW_OPEN, 1.4);
                        if (capAge >= CLAW_OPEN) {
                            cur.capOpen = capTarget;
                            capAge = Integer.MAX_VALUE;
                        }
                    } else {
                        cur.capOpen = capFrom * (1.0 - smooth(capAge / (double) CLAW_SHUT));
                        if (capAge >= CLAW_SHUT) {
                            cur.capOpen = capTarget;
                            capAge = Integer.MAX_VALUE;
                        }
                    }
                }
                cur.sigilAlpha = approach(cur.sigilAlpha, crowned ? 1.0 : 0.0, 1.0 / SIGIL_FADE);
                cur.arch = crowned && cur.capOpen > 0.5 ? flicker(tick) : 0.0;
            }
        }

        cur.flash = kind == Kind.COLLAPSE && age == 1 ? 1.0 : 0.0;

        if (s.heartbeat() != lastHeartbeat) {
            lastHeartbeat = s.heartbeat();
            if (!dark && kind != Kind.COLLAPSE) {
                pulseAge = 0;
            }
        }
        if (pulseAge >= 0) {
            cur.pulse = pulseAge / (double) PULSE;
            pulseAge = pulseAge >= PULSE ? -1 : pulseAge + 1;
        } else {
            cur.pulse = -1.0;
        }

        final int held = Math.max(0, s.held());
        cur.count = held == s.form() && Math.abs(held - latched) <= 1 ? latched : held;
        cur.nixieLit = held > 0 && (!riding || (tick / (cur.store > 0.4 ? 4 : 2)) % 2 == 0);
        cur.lamps = held > 0;
        cur.pilot = fed ? CrownPose.PILOT_GREEN : riding ? CrownPose.PILOT_WARN
                : (tick / 10) % 2 == 0 ? CrownPose.PILOT_RED : CrownPose.PILOT_OFF;
        cur.needleWarn = !fed;
        cur.loadA += (s.studLoad(0) - cur.loadA) * 0.35;
        cur.loadB += (s.studLoad(1) - cur.loadB) * 0.35;
        cur.tick = tick;
        cur.blocked = s.blocked() && held > s.form();
        cur.blockRight = s.blockRight();
        cur.blockUp = s.blockUp();
        cur.blockFront = s.blockFront();
        cur.wantForm = held;
    }

    static double speedFor(double store) {
        return Math.sqrt(OMEGA_MIN * OMEGA_MIN + (1.0 - OMEGA_MIN * OMEGA_MIN) * clamp(store));
    }

    private static double approach(double v, double target, double rate) {
        return v + Math.max(-rate, Math.min(rate, target - v));
    }

    private static double clamp(double v) {
        return v < 0 ? 0.0 : v > 1 ? 1.0 : v;
    }
}
