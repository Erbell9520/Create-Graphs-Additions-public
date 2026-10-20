package com.createstats.driverhelmet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// parse the mk4's own action bar line, it's the one place the vehicle reports itself
public record DashLine(char gear, double speed, double throttle, double steer, boolean boosting,
                       String extras, String problem) {
    private static final Pattern HEAD = Pattern.compile(
            "^VCC  (\\S)  (-?\\d+(?:\\.\\d+)?) b/s  drive ([+-]\\d+)%  steer ([+-]\\d+)%(.*)$");

    public static DashLine parse(String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = HEAD.matcher(text);
        if (!m.matches()) {
            return null;
        }
        String tail = m.group(5);
        String problem = "";
        final int bar = tail.indexOf("  | ");
        if (bar >= 0) {
            problem = tail.substring(bar + 4).trim();
            tail = tail.substring(0, bar);
        }
        boolean boosting = false;
        if (tail.startsWith("  BOOST")) {
            boosting = true;
            tail = tail.substring("  BOOST".length());
        }
        return new DashLine(m.group(1).charAt(0), Double.parseDouble(m.group(2)),
                Integer.parseInt(m.group(3)) / 100.0, Integer.parseInt(m.group(4)) / 100.0,
                boosting, tail.trim(), problem);
    }

    public double kmh() {
        return speed * 3.6;
    }

    public boolean braking() {
        return gear == 'B';
    }

    public boolean holding() {
        return gear == 'H';
    }
}
