package com.createstats.vccmk4.core;

import java.util.List;

// not every jet is a boost jet. a fan pointing left in front of the CoM should help you turn left
public final class JetMixer {
    public enum Job {
        PUSH("push"), TURN("turn"), LIFT("lift"), SLIDE("slide");

        private final String key;

        Job(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Job byOrdinal(int ordinal) {
            Job[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    public static final double VERTICAL = 0.7;

    public static final double TURN_ARM = 0.75;

    public static final double DIFFERENTIAL_ARM = 0.5;

    public record Jet(double right, double up, double forward, double axisRight, double axisUp, double axisForward,
                      JetRole role) {
    }

    public record Demand(boolean boost, double pushDir, double steer, boolean reversing, double throttle,
                         double throttleKey, boolean braking, boolean brakeKey, double lift, double slide,
                         double[] aux) {
        public static final Demand IDLE = new Demand(false, 1.0, 0.0, false, 0.0, 0.0, false, false, 0.0, 0.0,
                new double[4]);

        double auxOf(Control c) {
            int i = switch (c) {
                case AUX_A -> 0;
                case AUX_B -> 1;
                case AUX_C -> 2;
                case AUX_D -> 3;
                default -> -1;
            };
            return i >= 0 && aux != null && i < aux.length ? aux[i] : 0.0;
        }
    }

    public record Mode(boolean steerWithoutBoost, boolean jetDrive) {
        public static final Mode DEFAULT = new Mode(false, false);
    }

    private JetMixer() {
    }

    public static double yawArm(Jet j) {
        return j.forward() * j.axisRight() - j.right() * j.axisForward();
    }

    public static Job autoJob(Jet j) {
        if (Math.abs(j.axisUp()) >= VERTICAL) return Job.LIFT;
        if (Math.abs(j.axisForward()) >= Math.abs(j.axisRight())) return Job.PUSH;
        return Math.abs(yawArm(j)) >= TURN_ARM ? Job.TURN : Job.SLIDE;
    }

    public static Job jobOf(Jet j) {
        JetRole role = j.role() == null ? JetRole.AUTO : j.role();
        if (role.job() != null) return role.job();
        return role == JetRole.AUTO ? autoJob(j) : null;
    }

    public static double[] mix(List<Jet> jets, Demand d, Mode m) {
        double[] out = new double[jets.size()];
        for (int i = 0; i < out.length; i++) out[i] = command(jets.get(i), d, m);
        return out;
    }

    public static double command(Jet j, Demand d, Mode m) {
        JetRole role = j.role() == null ? JetRole.AUTO : j.role();
        double u;
        if (role == JetRole.OFF) {
            u = 0.0;
        } else if (role == JetRole.THROTTLE) {
            u = d.throttle() * sign(j.axisForward());
        } else if (role.auxControl() != null) {
            u = d.auxOf(role.auxControl());
        } else {
            Job job = jobOf(j);
            u = job == null ? 0.0 : switch (job) {
                case PUSH -> push(j, d, m);
                case TURN -> turnDemand(d, m) * sign(yawArm(j));
                case LIFT -> d.lift() * sign(j.axisUp());
                case SLIDE -> d.slide() * sign(j.axisRight());
            };
        }
        return GroundSteer.clamp(u, -1.0, 1.0);
    }

    private static double push(Jet j, Demand d, Mode m) {
        double push = pushDemand(d, m);
        if (push == 0.0 && !m.jetDrive()) return 0.0;
        double u = push * sign(j.axisForward());
        double arm = yawArm(j);
        if (Math.abs(arm) >= DIFFERENTIAL_ARM) u += turnDemand(d, m) * sign(arm);
        return u;
    }

    static double pushDemand(Demand d, Mode m) {
        if (m.jetDrive()) {
            if (d.brakeKey()) return 0.0;
            return d.boost() ? d.pushDir() : GroundSteer.clamp(d.throttleKey(), -1.0, 1.0);
        }

        if (!d.boost() || d.braking()) return 0.0;
        return d.pushDir();
    }

    public static double turnDemand(Demand d, Mode m) {
        boolean open = d.boost() || m.steerWithoutBoost() || m.jetDrive();
        if (!open) return 0.0;
        double s = GroundSteer.clamp(d.steer(), -1.0, 1.0);
        return !m.jetDrive() && d.reversing() ? -s : s;
    }

    private static double sign(double v) {
        return Math.abs(v) < 1.0e-6 ? 0.0 : Math.signum(v);
    }
}
