package com.createstats.inventory.core;

public final class SetPointRelay {
    public enum Mode {
        STEADY,
        PULSE,
        OFF;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode byOrdinal(int i) {
            final Mode[] all = values();
            return i >= 0 && i < all.length ? all[i] : STEADY;
        }
    }

    public static final int PULSE_TICKS = 4;

    public enum Lamp {
        NORMAL,
        FLASHING,
        STEADY;

        public boolean lit() {
            return this != NORMAL;
        }
    }

    public static final class AlarmLamp {
        private Lamp state = Lamp.NORMAL;

        public Lamp update(boolean active) {
            if (!active) {
                this.state = Lamp.NORMAL;
            } else if (this.state == Lamp.NORMAL) {
                this.state = Lamp.FLASHING;
            }
            return this.state;
        }

        public boolean acknowledge() {
            if (this.state == Lamp.FLASHING) {
                this.state = Lamp.STEADY;
                return true;
            }
            return false;
        }

        public Lamp state() {
            return this.state;
        }

        public void reset() {
            this.state = Lamp.NORMAL;
        }

        public void restore(Lamp lamp) {
            this.state = lamp == null ? Lamp.NORMAL : lamp;
        }
    }

    private boolean lowOn;
    private boolean highOn;
    private boolean belowLow;
    private boolean aboveHigh;
    private boolean primed;
    private int lowPulse;
    private int highPulse;
    private final AlarmLamp lowLamp = new AlarmLamp();
    private final AlarmLamp fullLamp = new AlarmLamp();

    public static int comparator(double q, double low, double high) {
        if (high <= low) {
            high = low + 1.0;
        }
        if (q <= low) {
            return 0;
        }
        if (q >= high) {
            return 15;
        }
        final int level = 1 + (int) Math.floor(14.0 * (q - low) / (high - low));
        return Math.max(1, Math.min(14, level));
    }

    public static double band(double low, double high) {
        if (high <= low) {
            high = low + 1.0;
        }
        return Math.max(1.0, 0.02 * (high - low));
    }

    public boolean update(double q, double low, double high, Mode lowMode, Mode highMode) {
        if (high <= low) {
            high = low + 1.0;
        }
        final double band = band(low, high);
        final boolean wasBelow = this.belowLow;
        final boolean wasAbove = this.aboveHigh;

        if (this.belowLow) {
            if (q > low + band) {
                this.belowLow = false;
            }
        } else if (q <= low) {
            this.belowLow = true;
        }
        if (this.aboveHigh) {
            if (q < high - band) {
                this.aboveHigh = false;
            }
        } else if (q >= high) {
            this.aboveHigh = true;
        }

        if (this.primed && this.belowLow && !wasBelow) {
            this.lowPulse = PULSE_TICKS;
        }
        if (this.primed && this.aboveHigh && !wasAbove) {
            this.highPulse = PULSE_TICKS;
        }
        this.primed = true;
        this.lowLamp.update(this.belowLow);
        this.fullLamp.update(this.aboveHigh);
        return refreshOutputs(lowMode, highMode);
    }

    private Mode lastLowMode = Mode.STEADY;
    private Mode lastHighMode = Mode.STEADY;

    private boolean refreshOutputs(Mode lowMode, Mode highMode) {
        this.lastLowMode = lowMode;
        this.lastHighMode = highMode;
        final boolean low = switch (lowMode) {
            case STEADY -> this.belowLow;
            case PULSE -> this.lowPulse > 0;
            case OFF -> false;
        };
        final boolean high = switch (highMode) {
            case STEADY -> this.aboveHigh;
            case PULSE -> this.highPulse > 0;
            case OFF -> false;
        };
        final boolean changed = low != this.lowOn || high != this.highOn;
        this.lowOn = low;
        this.highOn = high;
        return changed;
    }

    public boolean tick() {
        if (this.lowPulse > 0) {
            this.lowPulse--;
        }
        if (this.highPulse > 0) {
            this.highPulse--;
        }
        return refreshOutputs(this.lastLowMode, this.lastHighMode);
    }

    public boolean lowOutput() {
        return this.lowOn;
    }

    public boolean highOutput() {
        return this.highOn;
    }

    public boolean belowLow() {
        return this.belowLow;
    }

    public boolean aboveHigh() {
        return this.aboveHigh;
    }

    public Lamp lowLamp() {
        return this.lowLamp.state();
    }

    public Lamp fullLamp() {
        return this.fullLamp.state();
    }

    public boolean acknowledge() {
        final boolean a = this.lowLamp.acknowledge();
        final boolean b = this.fullLamp.acknowledge();
        return a || b;
    }

    public void reset() {
        this.lowOn = false;
        this.highOn = false;
        this.belowLow = false;
        this.aboveHigh = false;
        this.primed = false;
        this.lowPulse = 0;
        this.highPulse = 0;
        this.lowLamp.reset();
        this.fullLamp.reset();
    }
}
