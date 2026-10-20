package com.createstats.power;

// shortfall held 5s, dark grid is just the extreme case
public final class StarterTrigger {
    public static final int SUSTAIN_TICKS = 100;

    public static final int CLOSE_TICKS = 7;
    public static final int SWING_TICKS = 5;

    public static final int RELEASE_TICKS = 12;

    public enum Phase { IDLE, CLOSING, FIRING, RELEASING }

    public record Input(StarterMode mode, boolean shortNow, boolean full, boolean hasCharge,
                        boolean redstone, boolean siblingFiring) {
    }

    private Phase phase = Phase.IDLE;
    private int phaseTick;
    private int shortTicks;
    private boolean lastRedstone;

    public boolean tick(Input in) {
        final boolean edge = in.redstone() && !lastRedstone;
        lastRedstone = in.redstone();
        switch (phase) {
            case IDLE -> {
                if (in.mode() != StarterMode.AUTO) {
                    shortTicks = 0;
                } else if (!in.siblingFiring()) {
                    shortTicks = in.shortNow() ? Math.min(SUSTAIN_TICKS, shortTicks + 1) : 0;
                }
                final boolean auto = in.mode() == StarterMode.AUTO && in.full() && !in.siblingFiring()
                        && in.shortNow() && shortTicks >= SUSTAIN_TICKS;
                final boolean manual = in.mode() == StarterMode.REDSTONE && edge && in.hasCharge()
                        && !in.siblingFiring();
                if (auto || manual) {
                    enter(Phase.CLOSING);
                    return true;
                }
            }
            case CLOSING -> {
                if (in.mode() == StarterMode.HOLD) {
                    enter(Phase.RELEASING);
                } else if (++phaseTick >= CLOSE_TICKS) {
                    enter(Phase.FIRING);
                }
            }
            case FIRING -> {
                if (in.mode() == StarterMode.HOLD || !in.hasCharge()) {
                    enter(Phase.RELEASING);
                } else if (++phaseTick >= CapacitorDischarge.BURST_TICKS) {
                    enter(Phase.RELEASING);
                }
            }
            case RELEASING -> {
                if (++phaseTick >= RELEASE_TICKS) {
                    enter(Phase.IDLE);
                }
            }
        }
        return false;
    }

    private void enter(Phase next) {
        phase = next;
        phaseTick = 0;
        shortTicks = 0;
    }

    public void primeRedstone(boolean powered) {
        lastRedstone = powered;
    }

    public Phase phase() {
        return phase;
    }

    public int phaseTick() {
        return phaseTick;
    }

    public int shortTicks() {
        return shortTicks;
    }

    public boolean accepting() {
        return phase == Phase.IDLE;
    }

    public boolean claiming() {
        return phase == Phase.CLOSING || phase == Phase.FIRING;
    }

    public void restore(Phase phase, int phaseTick, int shortTicks) {
        this.phase = phase == null ? Phase.IDLE : phase;
        this.phaseTick = Math.max(0, phaseTick);
        this.shortTicks = Math.max(0, Math.min(SUSTAIN_TICKS, shortTicks));
    }
}
