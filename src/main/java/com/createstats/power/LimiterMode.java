package com.createstats.power;

public enum LimiterMode {
    ALWAYS("always"),

    SIGNAL_OPENS("signal_opens"),

    SIGNAL_SHUTS("signal_shuts"),

    ANALOG("analog");

    private final String key;

    LimiterMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "block.create_stats.power_limiter.mode." + key;
    }

    public String descriptionKey() {
        return "block.create_stats.power_limiter.mode." + key + ".desc";
    }

    public LimiterMode next() {
        final LimiterMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static LimiterMode byOrdinal(int ordinal) {
        final LimiterMode[] all = values();
        return ordinal < 0 || ordinal >= all.length ? ALWAYS : all[ordinal];
    }

    public int apply(int limit, int strength) {
        final int clamped = Math.max(0, Math.min(15, strength));
        return switch (this) {
            case ALWAYS -> limit;
            case SIGNAL_OPENS -> clamped > 0 ? limit : 0;
            case SIGNAL_SHUTS -> clamped > 0 ? 0 : limit;

            case ANALOG -> (int) ((long) limit * clamped / 15L);
        };
    }
}
