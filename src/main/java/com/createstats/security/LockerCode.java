package com.createstats.security;

import net.minecraft.nbt.CompoundTag;

// pure, a security rule you can't test without launching mc is one nobody tests
public final class LockerCode {
    public static final int DIGITS = 4;

    public static final int STRIKES = 3;

    public static final int LOCKOUT_TICKS = 1200;

    public enum Result {
        OPENED,

        WRONG,

        TAMPER,

        LOCKED_OUT,

        NO_CODE,

        MALFORMED
    }

    private String code = "";

    private int strikes;

    private long lockedUntil = Long.MIN_VALUE;

    public static boolean wellFormed(String candidate) {
        if (candidate == null || candidate.length() != DIGITS) {
            return false;
        }
        for (int i = 0; i < DIGITS; i++) {
            final char c = candidate.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public boolean isSet() {
        return !this.code.isEmpty();
    }

    public boolean set(String digits) {
        if (!wellFormed(digits)) {
            return false;
        }
        this.code = digits;
        this.strikes = 0;
        this.lockedUntil = Long.MIN_VALUE;
        return true;
    }

    public void clear() {
        this.code = "";
        this.strikes = 0;
        this.lockedUntil = Long.MIN_VALUE;
    }

    public boolean lockedOut(long now) {
        return now < this.lockedUntil;
    }

    public int lockoutRemaining(long now) {
        return lockedOut(now) ? (int) Math.min(Integer.MAX_VALUE, this.lockedUntil - now) : 0;
    }

    public int strikes() {
        return this.strikes;
    }

    public Result offer(String typed, long now) {
        if (lockedOut(now)) {
            return Result.LOCKED_OUT;
        }
        if (!isSet()) {
            return Result.NO_CODE;
        }
        if (!wellFormed(typed)) {
            return Result.MALFORMED;
        }
        if (this.code.equals(typed)) {
            this.strikes = 0;
            return Result.OPENED;
        }
        this.strikes++;
        if (this.strikes >= STRIKES) {
            this.strikes = 0;
            this.lockedUntil = now + LOCKOUT_TICKS;
            return Result.TAMPER;
        }
        return Result.WRONG;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Code", this.code);
        tag.putInt("Strikes", this.strikes);
        tag.putLong("LockedUntil", this.lockedUntil);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.code = tag.getString("Code");
        this.strikes = tag.getInt("Strikes");
        this.lockedUntil = tag.contains("LockedUntil") ? tag.getLong("LockedUntil") : Long.MIN_VALUE;
    }

    public LockerCode copy() {
        final LockerCode out = new LockerCode();
        out.code = this.code;
        out.strikes = this.strikes;
        out.lockedUntil = this.lockedUntil;
        return out;
    }
}
