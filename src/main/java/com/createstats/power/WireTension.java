package com.createstats.power;

public enum WireTension {
    SLACK(0, "slack", 1.02),

    STANDARD(1, "standard", 1.004),

    TAUT(2, "taut", 1.0015),

    DRUM(3, "drum", 1.0004);

    public static final double REACH = 8.0;

    private final int id;
    private final String key;
    private final double slack;

    WireTension(int id, String key, double slack) {
        this.id = id;
        this.key = key;
        this.slack = slack;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public double slack() {
        return slack;
    }

    public double sagPerBlock() {
        return sagPerBlock(slack);
    }

    public double sagOver(double span) {
        return Math.max(0.0, span) * sagPerBlock();
    }

    public static double sagPerBlock(double slack) {
        return Math.sqrt(3.0 * Math.max(0.0, slack - 1.0) / 8.0);
    }

    public static WireTension loosest() {
        WireTension out = STANDARD;
        for (WireTension t : values()) {
            if (t.slack > out.slack) {
                out = t;
            }
        }
        return out;
    }

    public WireTension step(int direction) {
        final WireTension[] all = values();
        final int at = Math.max(0, Math.min(all.length - 1, ordinal() + Integer.signum(direction)));
        return all[at];
    }

    public WireTension cycle() {
        final WireTension[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static WireTension byId(int id) {
        for (WireTension t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return STANDARD;
    }
}
