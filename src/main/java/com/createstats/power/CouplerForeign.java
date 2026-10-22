package com.createstats.power;

import org.jetbrains.annotations.Nullable;

public final class CouplerForeign {
    public static final String OUR_PACKAGE = "com.createstats.";

    public static final String CREATE_ADDITION_CONNECTORS = "com.mrh0.createaddition.blocks.connector.";

    private CouplerForeign() {
    }

    public enum Verdict {
        EMPTY,

        OURS,

        NEW_AGE_CONNECTOR,

        NEW_AGE_ELSEWHERE,

        CREATE_ADDITION,

        GENERIC;

        public boolean isEndpoint() {
            return this == NEW_AGE_CONNECTOR || this == CREATE_ADDITION || this == GENERIC;
        }

        public CouplerEndpoint.Family family() {
            return switch (this) {
                case NEW_AGE_CONNECTOR -> CouplerEndpoint.Family.NEW_AGE;
                case CREATE_ADDITION -> CouplerEndpoint.Family.CREATE_ADDITION;
                case GENERIC -> CouplerEndpoint.Family.GENERIC;
                default -> CouplerEndpoint.Family.NONE;
            };
        }
    }

    public static boolean createAdditionOpposes(@Nullable String theirMode, boolean couplerPulling) {
        if ("push".equals(theirMode)) {
            return !couplerPulling;
        }
        if ("pull".equals(theirMode)) {
            return couplerPulling;
        }
        return false;
    }

    public static boolean createAdditionDrives(@Nullable String theirMode) {
        return "push".equals(theirMode) || "pull".equals(theirMode);
    }

    public static boolean isOurs(@Nullable String blockEntityClass, @Nullable String blockClass) {
        return (blockEntityClass != null && blockEntityClass.startsWith(OUR_PACKAGE))
                || (blockClass != null && blockClass.startsWith(OUR_PACKAGE));
    }

    public static boolean isCreateAdditionConnector(@Nullable String blockEntityClass) {
        return blockEntityClass != null && blockEntityClass.startsWith(CREATE_ADDITION_CONNECTORS);
    }

    public static Verdict bolted(@Nullable String blockEntityClass, @Nullable String blockClass,
                                 boolean newAgeConnector, boolean supportsThis, boolean holdsFe) {
        if (isOurs(blockEntityClass, blockClass)) {
            return Verdict.OURS;
        }
        if (newAgeConnector) {
            return supportsThis ? Verdict.NEW_AGE_CONNECTOR : Verdict.NEW_AGE_ELSEWHERE;
        }
        if (!holdsFe) {
            return Verdict.EMPTY;
        }
        return isCreateAdditionConnector(blockEntityClass) ? Verdict.CREATE_ADDITION : Verdict.GENERIC;
    }

    public static Verdict wired(@Nullable String blockEntityClass, @Nullable String blockClass,
                                boolean newAgeConnector, boolean holdsFe) {
        if (isOurs(blockEntityClass, blockClass)) {
            return Verdict.OURS;
        }
        if (newAgeConnector) {
            return Verdict.NEW_AGE_CONNECTOR;
        }
        if (!holdsFe) {
            return Verdict.EMPTY;
        }
        return isCreateAdditionConnector(blockEntityClass) ? Verdict.CREATE_ADDITION : Verdict.GENERIC;
    }
}
