package com.createstats.chunkloader.look;

import static com.createstats.chunkloader.look.CrownGeometry.MAX;
import static com.createstats.chunkloader.look.CrownGeometry.TIERS;

public final class CrownPose {
    public static final int INK_OFF = 0;

    public static final int INK_ROSE = 1;

    public static final int INK_WARN = 2;

    public static final int PILOT_OFF = 0;
    public static final int PILOT_GREEN = 1;
    public static final int PILOT_WARN = 2;
    public static final int PILOT_RED = 3;

    public final double[] mount = new double[MAX];

    public final double[] glow = new double[MAX];
    public final int[] ink = new int[MAX];

    public final double[] tierUp = new double[TIERS];

    public final double[] tierAngle = new double[TIERS];

    public double ring0Y;
    public double stemTop;
    public double hornTop;
    public double yokeStub;
    public double yokeSwing;
    public double yokeExt;

    public double capOpen;

    public double hatch;

    public int count;
    public boolean nixieLit;

    public double store;

    public boolean lamps;
    public boolean needleWarn;
    public int pilot;

    public double loadA;
    public double loadB;

    public double intensity;

    public double dim = 1.0;

    public double arcScale = 1.0;

    public boolean feed;
    public double sigilAlpha;
    public double sigilAngle;
    public double haloPhase;

    public double pulse = -1.0;

    public double shockRadius;
    public double shockAlpha;

    public double flash;

    public double arch;

    public long arcSeed;

    public double spin;

    public long tick;

    public boolean blocked;

    public int blockRight;
    public int blockUp;
    public int blockFront;

    public int wantForm;

    public static CrownPose settled(int n) {
        final CrownPose p = new CrownPose();
        p.setSettled(n);
        return p;
    }

    public CrownPose setSettled(int n) {
        final int c = Math.max(0, Math.min(MAX, n));
        final int tp = CrownGeometry.tiersFor(c);
        for (int t = 0; t < TIERS; t++) {
            tierUp[t] = t < tp ? 1.0 : 0.0;
            tierAngle[t] = CrownGeometry.phase0(t);
        }
        for (int k = 1; k <= MAX; k++) {
            final boolean held = k <= c;
            mount[k - 1] = held ? 1.0 : 0.0;
            glow[k - 1] = held ? 1.0 : 0.0;
            ink[k - 1] = held ? INK_ROSE : INK_OFF;
        }
        ring0Y = CrownGeometry.ring0YFor(c);
        stemTop = CrownGeometry.stemTopFor(tp, ring0Y);
        hornTop = CrownGeometry.hornTopFor(c);

        yokeStub = c > 4 * CrownGeometry.PER_TIER ? 1.0 : 0.0;
        final boolean full = c == MAX;
        yokeSwing = full ? 1.0 : 0.0;
        yokeExt = full ? 1.0 : 0.0;
        capOpen = full ? 1.0 : 0.0;
        hatch = c > 0 ? 1.0 : 0.0;
        count = c;
        nixieLit = c > 0;
        lamps = c > 0;
        store = 1.0;
        needleWarn = false;
        pilot = c > 0 ? PILOT_GREEN : PILOT_OFF;
        loadA = 0.0;
        loadB = 0.0;
        intensity = CrownGeometry.drawLevel(c <= 0 ? 0 : 1024.0 * (Math.pow(1.1, c) - 1.0) / 0.1);
        dim = 1.0;
        arcScale = 1.0;
        feed = c > 0;
        sigilAlpha = full ? 1.0 : 0.0;
        sigilAngle = 0.0;
        haloPhase = 0.0;
        pulse = -1.0;
        shockRadius = 0.0;
        shockAlpha = 0.0;
        flash = 0.0;
        arch = full ? 1.0 : 0.0;
        arcSeed = 0;
        spin = c > 0 ? CrownGeometry.OMEGA : 0.0;
        tick = 0;
        blocked = false;
        blockRight = 0;
        blockUp = 0;
        blockFront = 0;
        wantForm = c;
        return this;
    }

    public static CrownPose item() {
        final CrownPose p = settled(0);
        p.pilot = PILOT_OFF;
        p.store = 0.0;
        p.hatch = 0.0;
        p.feed = false;
        return p;
    }

    public void copyFrom(CrownPose o) {
        System.arraycopy(o.mount, 0, mount, 0, MAX);
        System.arraycopy(o.glow, 0, glow, 0, MAX);
        System.arraycopy(o.ink, 0, ink, 0, MAX);
        System.arraycopy(o.tierUp, 0, tierUp, 0, TIERS);
        System.arraycopy(o.tierAngle, 0, tierAngle, 0, TIERS);
        ring0Y = o.ring0Y;
        stemTop = o.stemTop;
        hornTop = o.hornTop;
        yokeStub = o.yokeStub;
        yokeSwing = o.yokeSwing;
        yokeExt = o.yokeExt;
        capOpen = o.capOpen;
        hatch = o.hatch;
        count = o.count;
        nixieLit = o.nixieLit;
        lamps = o.lamps;
        store = o.store;
        needleWarn = o.needleWarn;
        pilot = o.pilot;
        loadA = o.loadA;
        loadB = o.loadB;
        intensity = o.intensity;
        dim = o.dim;
        arcScale = o.arcScale;
        feed = o.feed;
        sigilAlpha = o.sigilAlpha;
        sigilAngle = o.sigilAngle;
        haloPhase = o.haloPhase;
        pulse = o.pulse;
        shockRadius = o.shockRadius;
        shockAlpha = o.shockAlpha;
        flash = o.flash;
        arch = o.arch;
        arcSeed = o.arcSeed;
        spin = o.spin;
        tick = o.tick;
        blocked = o.blocked;
        blockRight = o.blockRight;
        blockUp = o.blockUp;
        blockFront = o.blockFront;
        wantForm = o.wantForm;
    }

    // discrete stuff from the later tick, never half a colour change
    public void lerp(CrownPose a, CrownPose b, double t) {
        copyFrom(b);
        for (int i = 0; i < MAX; i++) {
            mount[i] = mix(a.mount[i], b.mount[i], t);
            glow[i] = mix(a.glow[i], b.glow[i], t);
        }
        for (int i = 0; i < TIERS; i++) {
            tierUp[i] = mix(a.tierUp[i], b.tierUp[i], t);
            tierAngle[i] = mix(a.tierAngle[i], b.tierAngle[i], t);
        }
        ring0Y = mix(a.ring0Y, b.ring0Y, t);
        stemTop = mix(a.stemTop, b.stemTop, t);
        hornTop = mix(a.hornTop, b.hornTop, t);
        yokeStub = mix(a.yokeStub, b.yokeStub, t);
        yokeSwing = mix(a.yokeSwing, b.yokeSwing, t);
        yokeExt = mix(a.yokeExt, b.yokeExt, t);
        capOpen = mix(a.capOpen, b.capOpen, t);
        hatch = mix(a.hatch, b.hatch, t);
        store = mix(a.store, b.store, t);
        loadA = mix(a.loadA, b.loadA, t);
        loadB = mix(a.loadB, b.loadB, t);
        intensity = mix(a.intensity, b.intensity, t);
        dim = mix(a.dim, b.dim, t);
        arcScale = mix(a.arcScale, b.arcScale, t);
        sigilAlpha = mix(a.sigilAlpha, b.sigilAlpha, t);
        sigilAngle = mix(a.sigilAngle, b.sigilAngle, t);
        haloPhase = mix(a.haloPhase, b.haloPhase, t);
        if (a.pulse >= 0 && b.pulse >= 0) {
            pulse = mix(a.pulse, b.pulse, t);
        }
        if (a.shockAlpha > 0 && b.shockAlpha > 0) {
            shockRadius = mix(a.shockRadius, b.shockRadius, t);
            shockAlpha = mix(a.shockAlpha, b.shockAlpha, t);
        }
        arch = mix(a.arch, b.arch, t);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
