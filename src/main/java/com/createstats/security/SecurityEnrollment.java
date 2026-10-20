package com.createstats.security;

public record SecurityEnrollment(String kind, long enrolledAt, long missingSince) {
    public static final long NEVER = Long.MIN_VALUE;

    public SecurityEnrollment {
        kind = kind == null ? "" : kind;
    }

    public boolean missing() {
        return this.missingSince != NEVER;
    }

    public SecurityEnrollment markedMissing(long now) {
        return missing() ? this : new SecurityEnrollment(this.kind, this.enrolledAt, now);
    }

    public SecurityEnrollment present() {
        return missing() ? new SecurityEnrollment(this.kind, this.enrolledAt, NEVER) : this;
    }
}
