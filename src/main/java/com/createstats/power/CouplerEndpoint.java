package com.createstats.power;

public enum CouplerEndpoint {
    NONE(false, Family.NONE),

    BOLTED_NEW_AGE(false, Family.NEW_AGE),

    BOLTED_CREATE_ADDITION(false, Family.CREATE_ADDITION),

    BOLTED_GENERIC(false, Family.GENERIC),

    WIRED_NEW_AGE(true, Family.NEW_AGE),

    WIRED_CREATE_ADDITION(true, Family.CREATE_ADDITION),

    WIRED_GENERIC(true, Family.GENERIC);

    public enum Family {
        NONE, NEW_AGE, CREATE_ADDITION, GENERIC
    }

    private final boolean wired;
    private final Family family;

    CouplerEndpoint(boolean wired, Family family) {
        this.wired = wired;
        this.family = family;
    }

    public boolean wired() {
        return this.wired;
    }

    public Family family() {
        return this.family;
    }

    public boolean present() {
        return this != NONE;
    }

    public boolean isNewAge() {
        return this.family == Family.NEW_AGE;
    }

    public boolean isCreateAddition() {
        return this.family == Family.CREATE_ADDITION;
    }

    public String key() {
        return switch (this.family) {
            case NEW_AGE -> "new_age";
            case CREATE_ADDITION -> "create_addition";
            case GENERIC -> "generic";
            case NONE -> "none";
        };
    }

    public static CouplerEndpoint of(boolean wired, Family family) {
        return switch (family) {
            case NONE -> NONE;
            case NEW_AGE -> wired ? WIRED_NEW_AGE : BOLTED_NEW_AGE;
            case CREATE_ADDITION -> wired ? WIRED_CREATE_ADDITION : BOLTED_CREATE_ADDITION;
            case GENERIC -> wired ? WIRED_GENERIC : BOLTED_GENERIC;
        };
    }

    public static CouplerEndpoint byOrdinal(int ordinal) {
        final CouplerEndpoint[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
    }
}
