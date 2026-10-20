package com.createstats.vccmk4.core;

import java.util.Locale;

public final class FaultRule {
    public static final long SLOW_NANOS = 1_000_000_000L;

    public static final int SLOW_RUN = 3;

    public static final long STUCK_NANOS = 5_000_000_000L;

    static final int MESSAGE_MAX = 120;

    private static final String OWN_PACKAGE = "com.createstats.";
    private static final String SELF = "com.createstats.vccmk4.ConsoleFault";

    private int slowRun;

    public boolean overBudget(long tickNanos) {
        if (tickNanos >= STUCK_NANOS) return true;
        if (tickNanos < SLOW_NANOS) {
            slowRun = 0;
            return false;
        }
        return ++slowRun >= SLOW_RUN;
    }

    public int slowRun() {
        return slowRun;
    }

    public static String summary(Throwable t) {
        if (t == null) return "unknown error";
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName());
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) {
            String line = msg.strip().replaceAll("\\s+", " ");
            if (line.length() > MESSAGE_MAX) line = line.substring(0, MESSAGE_MAX - 3) + "...";
            sb.append(": ").append(line);
        }
        StackTraceElement at = where(t);
        if (at != null) {
            sb.append(" at ").append(simpleName(at.getClassName())).append('.').append(at.getMethodName());
            if (at.getLineNumber() > 0) sb.append(':').append(at.getLineNumber());
        }
        return sb.toString();
    }

    public static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1.0e9);
    }

    private static StackTraceElement where(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        if (frames == null || frames.length == 0) return null;
        for (StackTraceElement f : frames) {
            String c = f.getClassName();
            if (c.startsWith(OWN_PACKAGE) && !c.startsWith(SELF) && !c.equals(FaultRule.class.getName())) return f;
        }
        return frames[0];
    }

    private static String simpleName(String className) {
        String s = className.substring(className.lastIndexOf('.') + 1);
        int lambda = s.indexOf("$$");
        return lambda > 0 ? s.substring(0, lambda) : s;
    }
}
