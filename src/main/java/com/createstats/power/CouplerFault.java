package com.createstats.power;

public enum CouplerFault {
    NONE("none"),

    WRONG_FACE("wrong_face"),

    LOOPED("looped"),

    FOREIGN_REFUSED("foreign_refused"),

    CONNECTOR_FACES_AWAY("connector_faces_away"),

    NO_FOREIGN("no_foreign"),

    NO_GRID("no_grid"),

    SWITCHED_OFF("switched_off"),

    FORCED_OFF("forced_off"),

    NEEDS_THEIR_PULL("needs_their_pull"),

    NO_PULL_SOURCES("no_pull_sources"),

    CONNECTOR_OPPOSES("connector_opposes"),

    SHED("shed"),

    THEIRS_NOT_TAKING("theirs_not_taking"),

    THEIRS_NOT_GIVING("theirs_not_giving"),

    OURS_NOT_TAKING("ours_not_taking"),

    OURS_NOT_GIVING("ours_not_giving");

    private final String key;

    CouplerFault(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String langKey() {
        return "create_stats.universal_power_coupler.fault." + this.key;
    }

    public boolean isFault() {
        return this != NONE;
    }

    public boolean blocksFlow() {
        return switch (this) {
            case WRONG_FACE, LOOPED, FOREIGN_REFUSED, CONNECTOR_FACES_AWAY, NO_FOREIGN, NO_GRID, SWITCHED_OFF,
                 FORCED_OFF, NEEDS_THEIR_PULL, NO_PULL_SOURCES, CONNECTOR_OPPOSES, SHED -> true;
            default -> false;
        };
    }

    public boolean isTheirSide() {
        return switch (this) {
            case WRONG_FACE, FOREIGN_REFUSED, CONNECTOR_FACES_AWAY, NO_FOREIGN, NEEDS_THEIR_PULL, NO_PULL_SOURCES,
                 CONNECTOR_OPPOSES, THEIRS_NOT_TAKING, THEIRS_NOT_GIVING -> true;
            default -> false;
        };
    }

    public boolean isOurSide() {
        return switch (this) {
            case NO_GRID, OURS_NOT_TAKING, OURS_NOT_GIVING -> true;
            default -> false;
        };
    }

    public static CouplerFault byOrdinal(int ordinal) {
        final CouplerFault[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
    }

    public record Inputs(boolean pulling, boolean wrongFace, boolean looped, CouplerEndpoint endpoint,
                         boolean ownBlockThere, boolean facesAway, boolean gridConnected, boolean forcedOff,
                         boolean shed, boolean theirInert, int pullSources, boolean connectorOpposes,
                         int crossing, int stored, int capacity, boolean switchedOff) {
        public Inputs(boolean pulling, boolean wrongFace, boolean looped, CouplerEndpoint endpoint,
                      boolean ownBlockThere, boolean facesAway, boolean gridConnected, boolean forcedOff,
                      boolean shed, boolean theirInert, int pullSources, boolean connectorOpposes,
                      int crossing, int stored, int capacity) {
            this(pulling, wrongFace, looped, endpoint, ownBlockThere, facesAway, gridConnected, forcedOff, shed,
                    theirInert, pullSources, connectorOpposes, crossing, stored, capacity, false);
        }
    }

    public static CouplerFault judge(Inputs in) {
        final boolean wired = in.endpoint().wired();
        if (in.wrongFace() && !wired) {
            return WRONG_FACE;
        }
        if (in.looped()) {
            return LOOPED;
        }
        if (in.ownBlockThere()) {
            return FOREIGN_REFUSED;
        }
        if (in.facesAway() && !wired) {
            return CONNECTOR_FACES_AWAY;
        }
        if (!in.endpoint().present()) {
            return NO_FOREIGN;
        }
        if (!in.gridConnected()) {
            return NO_GRID;
        }

        if (in.switchedOff()) {
            return SWITCHED_OFF;
        }
        if (in.forcedOff()) {
            return FORCED_OFF;
        }
        if (!in.pulling() && in.endpoint() == CouplerEndpoint.BOLTED_NEW_AGE && in.theirInert()) {
            return NEEDS_THEIR_PULL;
        }
        if (in.pulling() && in.endpoint() == CouplerEndpoint.WIRED_NEW_AGE && in.pullSources() == 0) {
            return NO_PULL_SOURCES;
        }
        if (in.connectorOpposes() && in.endpoint() == CouplerEndpoint.BOLTED_CREATE_ADDITION) {
            return CONNECTOR_OPPOSES;
        }
        if (!in.pulling() && in.shed()) {
            return SHED;
        }
        if (in.crossing() > 0) {
            return NONE;
        }
        if (in.pulling()) {
            return in.capacity() > 0 && in.stored() >= in.capacity() ? OURS_NOT_TAKING : THEIRS_NOT_GIVING;
        }
        return in.stored() <= 0 ? OURS_NOT_GIVING : THEIRS_NOT_TAKING;
    }
}
