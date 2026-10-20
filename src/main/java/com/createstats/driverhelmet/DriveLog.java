package com.createstats.driverhelmet;

// peak g over the last 10s, friction circle (lat with lon)
public final class DriveLog {
    public static final int WINDOW_TICKS = 200;

    public static final double IMPACT_G = 5.0;

    public static final double IMPACT_END_G = 2.0;

    public static final int MIN_JUMP_TICKS = 5;

    private final float[] window = new float[WINDOW_TICKS];
    private int head;
    private int size;

    private boolean inImpact;
    private double lastImpact;
    private long lastImpactTick = Long.MIN_VALUE;
    private double tripImpact;

    private long airborneSince = Long.MIN_VALUE;
    private double lastAir;

    public void tick(long now, double horizontalG, double rawG, boolean allAirborne) {
        window[head] = (float) Math.abs(horizontalG);
        head = (head + 1) % WINDOW_TICKS;
        size = Math.min(WINDOW_TICKS, size + 1);

        if (rawG >= IMPACT_G) {
            if (!inImpact) {
                inImpact = true;
                lastImpact = 0.0;
                lastImpactTick = now;
            }
            lastImpact = Math.max(lastImpact, rawG);
            tripImpact = Math.max(tripImpact, rawG);
        } else if (inImpact && rawG < IMPACT_END_G) {
            inImpact = false;
        }

        if (allAirborne) {
            if (airborneSince == Long.MIN_VALUE) {
                airborneSince = now;
            }
        } else if (airborneSince != Long.MIN_VALUE) {
            if (now - airborneSince >= MIN_JUMP_TICKS) {
                lastAir = (now - airborneSince) / 20.0;
            }
            airborneSince = Long.MIN_VALUE;
        }
    }

    public double peak() {
        double best = 0.0;
        for (int i = 0; i < size; i++) {
            best = Math.max(best, window[i]);
        }
        return best;
    }

    public double lastImpact() {
        return lastImpact;
    }

    public int impactAge(long now) {
        return lastImpactTick == Long.MIN_VALUE ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - lastImpactTick));
    }

    public double tripImpact() {
        return tripImpact;
    }

    public double airNow(long now) {
        return airborneSince == Long.MIN_VALUE ? 0.0 : (now - airborneSince) / 20.0;
    }

    public double lastAir() {
        return lastAir;
    }
}
