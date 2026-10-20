package com.createstats.support;

// pure so the access rules get tested
public final class SupportPolicy {
    private SupportPolicy() {
    }

    public enum Level {
        NONE,

        SAFE,

        TIER1,

        TIER2;

        public boolean atLeast(Level other) {
            return this.compareTo(other) >= 0;
        }

        public static Level max(Level a, Level b) {
            return a.compareTo(b) >= 0 ? a : b;
        }
    }

    public enum Basis {
        NONE("no access"),
        AUTHORITY("host or operator"),
        CREATOR_KEY("key verified"),
        CREATOR_ONLINE("Mojang-authenticated"),
        CREATOR_NAME_ONLY("name only, offline server"),
        GRANT("granted by the server");

        private final String label;

        Basis(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public boolean borrowed() {
            return this == CREATOR_KEY || this == GRANT;
        }
    }

    public record Facts(boolean creator, boolean onlineMode, boolean keyVerified, boolean hostOrOperator,
                        Level granted) {
    }

    public record Decision(Level level, Basis basis, String tier2Note) {
        public static final Decision NONE = new Decision(Level.NONE, Basis.NONE, "");

        public boolean atLeast(Level other) {
            return this.level.atLeast(other);
        }
    }

    public static Decision decide(Facts facts) {
        Level granted = facts.granted() == null ? Level.NONE : facts.granted();

        if (!facts.creator()) {
            if (granted == Level.NONE) {
                return Decision.NONE;
            }
            return new Decision(granted, Basis.GRANT,
                granted.atLeast(Level.TIER2) ? "" : "Tier 2 not granted");
        }

        if (facts.hostOrOperator()) {
            return new Decision(Level.TIER2, Basis.AUTHORITY, "");
        }

        if (facts.onlineMode() && facts.keyVerified()) {
            return new Decision(Level.TIER2, Basis.CREATOR_KEY, "");
        }
        if (granted.atLeast(Level.TIER2)) {
            return new Decision(Level.TIER2, Basis.GRANT, "");
        }
        if (facts.onlineMode()) {
            return new Decision(Level.TIER1, Basis.CREATOR_ONLINE,
                "Tier 2 needs the key proof (" + SupportKeyProof.KEY_FILE_NAME + ")");
        }
        if (granted.atLeast(Level.TIER1)) {
            return new Decision(Level.TIER1, Basis.GRANT, "offline server: Tier 2 needs an owner grant");
        }
        return new Decision(Level.SAFE, Basis.CREATOR_NAME_ONLY,
            "offline server: identity unverified, ask the owner for a grant");
    }
}
