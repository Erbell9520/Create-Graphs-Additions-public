package com.createstats.security;

public final class PanelAccess {
    private PanelAccess() {
    }

    public static final int KEYHOLDER = Clearance.MAX;

    public enum Entry {
        OPEN,

        PASSWORD
    }

    public static Entry demand(boolean hasPassword, boolean alwaysAsk, boolean owner, int clearance) {
        if (!hasPassword) {
            return Entry.OPEN;
        }
        if (alwaysAsk) {
            return Entry.PASSWORD;
        }
        if (owner) {
            return Entry.OPEN;
        }
        return clearance >= KEYHOLDER ? Entry.OPEN : Entry.PASSWORD;
    }

    public static Entry demandOnJoin(boolean hasPassword) {
        return hasPassword ? Entry.PASSWORD : Entry.OPEN;
    }
}
