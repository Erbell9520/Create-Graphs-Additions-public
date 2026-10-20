package com.createstats.book.content;

public enum Symptom {
    READS_ZERO("reads_zero"),

    SHED_OR_OFF("shed_or_off"),

    NO_SUPPLY("no_supply"),

    WILL_NOT_START("will_not_start"),

    RUNS_THEN_STOPS("runs_then_stops"),

    WRONG_NUMBER("wrong_number");

    private final String key;

    Symptom(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String nameKey() {
        return "create_stats.book.symptom." + key;
    }
}
