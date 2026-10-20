package com.createstats.book.content;

import com.createstats.book.BookTokens;

// book section = creative tab section, whatever package the code lives in
public enum Section {
    BASICS("basics", null, "create_stats:students_manual"),
    STATS("stats", "stats", "create_stats:stats_display_mk3"),
    POWER("power", "power", "create_stats:power_meter"),
    MACHINES("machines", "machines", "create_stats:load_balancer"),
    TRANSPORT("transport", "transport", "create_stats:vcc_mk4"),
    HOME("home", "home", "create_stats:coffee_machine"),
    SECURITY("security", "security", "create_stats:tamper_sensor"),
    FAULTS("faults", null, "create_stats:klaxon_alarm");

    private final String key;

    private final String creativeKey;
    private final String icon;

    Section(String key, String creativeKey, String icon) {
        this.key = key;
        this.creativeKey = creativeKey;
        this.icon = icon;
    }

    public int number() {
        return ordinal() + 1;
    }

    public String key() {
        return key;
    }

    public String creativeKey() {
        return creativeKey;
    }

    public String icon() {
        return icon;
    }

    public int colour() {
        return BookTokens.SECTION[ordinal()];
    }

    public String nameKey() {
        return "create_stats.book.section." + key;
    }

    public String hookKey() {
        return "create_stats.book.section." + key + ".hook";
    }

    public static Section ofCreativeKey(String creativeKey) {
        for (final Section s : values()) {
            if (creativeKey.equals(s.creativeKey)) {
                return s;
            }
        }
        throw new IllegalArgumentException("no book section for creative section " + creativeKey);
    }
}
