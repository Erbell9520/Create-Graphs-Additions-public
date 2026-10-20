package com.createstats.stormcoil;

public final class CoilTargets {
    public enum Mode {
        HOSTILE, TRANSFORM, STORM_ONLY;

        public boolean zaps() {
            return this != STORM_ONLY;
        }

        public static Mode of(int ordinal) {
            final Mode[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    public enum Kind {
        HOSTILE,
        CREEPER,
        VILLAGER,
        PIG,
        MOOSHROOM,

        GOLEM,
        PLAYER,

        OTHER
    }

    public record Target(Kind kind, boolean alive, boolean tamedOrOwned, boolean named, boolean leashed,
                         boolean creativeOrSpectator, boolean charged, boolean alreadyZapped) {
    }

    private CoilTargets() {
    }

    public static boolean allows(Mode mode, Target t) {
        if (!mode.zaps() || !t.alive() || t.creativeOrSpectator()) {
            return false;
        }
        if (t.kind() == Kind.PLAYER || t.kind() == Kind.GOLEM || t.tamedOrOwned() || t.named() || t.leashed()) {
            return false;
        }
        return switch (mode) {
            case HOSTILE -> t.kind() == Kind.HOSTILE || t.kind() == Kind.CREEPER;
            case TRANSFORM -> transforms(t.kind()) && !t.alreadyZapped()
                    && !(t.kind() == Kind.CREEPER && t.charged());
            case STORM_ONLY -> false;
        };
    }

    public static boolean transforms(Kind kind) {
        return kind == Kind.CREEPER || kind == Kind.VILLAGER || kind == Kind.PIG || kind == Kind.MOOSHROOM;
    }
}
