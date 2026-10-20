package com.createstats.block;

import java.util.Locale;

public final class GarageDoorFrame {
    public static final int MIN_WIDTH = 1;

    public static final int MAX_WIDTH = 12;

    public static final int MIN_HEIGHT = 1;

    public static final int MAX_DROP = 12;

    private GarageDoorFrame() {
    }

    public enum Fault {
        OK,

        NOT_A_HOOD,

        TOO_WIDE,

        NO_ROOM;

        public boolean ok() {
            return this == OK;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public interface Probe {
        boolean hood(int h, int v);

        boolean clear(int h, int v);
    }

    public record Result(Fault fault, int left, int width, int height, int headerV) {
        public static Result fail(Fault fault) {
            return new Result(fault, 0, 0, 0, 0);
        }

        public boolean ok() {
            return fault.ok();
        }

        public int right() {
            return left + width - 1;
        }

        public int interiorMinH() {
            return left;
        }

        public int interiorMaxH() {
            return right();
        }

        public int topV() {
            return headerV - 1;
        }

        public int bottomV() {
            return headerV - height;
        }

        public int area() {
            return width * height;
        }
    }

    public static Result detect(Probe probe, int h0, int v0) {
        if (!probe.hood(h0, v0)) {
            return Result.fail(Fault.NOT_A_HOOD);
        }

        int left = h0;
        for (int i = 0; i <= MAX_WIDTH; i++) {
            if (!probe.hood(left - 1, v0)) {
                break;
            }
            left--;
        }
        int right = h0;
        for (int i = 0; i <= MAX_WIDTH; i++) {
            if (!probe.hood(right + 1, v0)) {
                break;
            }
            right++;
        }

        final int width = right - left + 1;
        if (width > MAX_WIDTH) {
            return Result.fail(Fault.TOO_WIDE);
        }

        final int height = drop(probe, left, right, v0);
        if (height < MIN_HEIGHT) {
            return Result.fail(Fault.NO_ROOM);
        }
        return new Result(Fault.OK, left, width, height, v0);
    }

    private static int drop(Probe probe, int left, int right, int headerV) {
        int rows = 0;
        while (rows < MAX_DROP && rowClear(probe, left, right, headerV - 1 - rows)) {
            rows++;
        }
        return rows;
    }

    private static boolean rowClear(Probe probe, int left, int right, int v) {
        for (int h = left; h <= right; h++) {
            if (!probe.clear(h, v)) {
                return false;
            }
        }
        return true;
    }
}
