package com.createstats.vccmk4.console;

import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.network.Vcc4Snapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// mirrors the server's rules so legend, keys page and highlight can't disagree
final class PartIndex {
    enum Kind { WHEEL, JET, SWIVEL, GOVERNOR, MACHINE, LINK }

    record Ref(Kind kind, int index) {
    }

    private PartIndex() {
    }

    static Set<Ref> partsOf(Control c, Vcc4Snapshot s) {
        Set<Ref> out = new LinkedHashSet<>();
        SteerPreset preset = SteerPreset.byOrdinal(s.preset());
        for (int i = 0; i < s.wheels().size(); i++) {
            Vcc4Snapshot.Wheel w = s.wheels().get(i);
            boolean driven = drives(s, w);
            boolean own = w.body() == 0;
            Vcc4Snapshot.Body body = own ? null : bodyOf(s, w.body());
            boolean inclusion;
            if (body != null && body.takesMode()) {
                TrailerMode mode = TrailerMode.byOrdinal(body.mode());
                inclusion = mode != TrailerMode.OFF && switch (c) {
                    case THROTTLE -> driven;
                    case STEER -> mode == TrailerMode.ASSIST;
                    case BRAKE -> s.brakesAvailable();
                    default -> false;
                };
            } else if (body != null && body.roleOf().neverTouched()) {
                inclusion = false;
            } else {
                boolean steered = own && w.commanded() && roleOf(s, w.axle()) == AxleRole.STEER && preset != SteerPreset.SKID;
                inclusion = switch (c) {
                    case THROTTLE -> driven;
                    case STEER -> steered || (own && preset == SteerPreset.SKID && driven);
                    case BRAKE -> s.brakesAvailable() && (!own || roleOf(s, w.axle()) != AxleRole.FREE);
                    default -> false;
                };
            }
            if (inclusion) out.add(new Ref(Kind.WHEEL, i));
        }
        for (int i = 0; i < s.jets().size(); i++) {
            if (jetAnswers(s.jets().get(i), c, s)) out.add(new Ref(Kind.JET, i));
        }
        for (int i = 0; i < s.swivels().size(); i++) {
            if (PartInput.byOrdinal(s.swivels().get(i).input()).control() == c) out.add(new Ref(Kind.SWIVEL, i));
        }
        for (int i = 0; i < s.machines().size(); i++) {
            if (PartInput.byOrdinal(s.machines().get(i).input()).control() == c) out.add(new Ref(Kind.MACHINE, i));
        }
        for (int i = 0; i < s.links().size(); i++) {
            if (LinkSource.byOrdinal(s.links().get(i).source()).shownUnder() == c) out.add(new Ref(Kind.LINK, i));
        }
        return out;
    }

    static boolean jetAnswers(Vcc4Snapshot.Jet j, Control c, Vcc4Snapshot s) {
        JetRole role = JetRole.byOrdinal(j.role());
        if (role == JetRole.OFF) return false;
        if (role == JetRole.THROTTLE) return c == Control.THROTTLE;
        if (role.auxControl() != null) return role.auxControl() == c;
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (job == null) return false;
        boolean arm = Math.abs(yawArm(j)) >= JetMixer.DIFFERENTIAL_ARM;
        return switch (job) {
            case PUSH -> c == Control.BOOST || (s.jetDrive() && (c == Control.THROTTLE || c == Control.BRAKE))
                    || (arm && c == Control.STEER);
            case TURN -> c == Control.STEER || (c == Control.BOOST && !s.jetDrive() && !s.jetsSteerWithoutBoost());
            case LIFT -> c == Control.LIFT;
            case SLIDE -> c == Control.SLIDE;
        };
    }

    static Control primaryControl(Vcc4Snapshot.Jet j, Vcc4Snapshot s) {
        JetRole role = JetRole.byOrdinal(j.role());
        if (role == JetRole.OFF) return null;
        if (role == JetRole.THROTTLE) return Control.THROTTLE;
        if (role.auxControl() != null) return role.auxControl();
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (job == null) return null;
        return switch (job) {
            case PUSH -> s.jetDrive() ? Control.THROTTLE : Control.BOOST;
            case TURN -> Control.STEER;
            case LIFT -> Control.LIFT;
            case SLIDE -> Control.SLIDE;
        };
    }

    static int firingSign(Vcc4Snapshot.Jet j, Vcc4Snapshot s) {
        if (!j.direct()) return 0;
        JetRole role = JetRole.byOrdinal(j.role());
        if (role == JetRole.OFF) return 0;
        if (role == JetRole.THROTTLE) return sign(j.axisForward());
        if (role.auxControl() != null) return 1;
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (job == null) return 0;
        return switch (job) {
            case PUSH -> sign(j.axisForward());
            case TURN -> sign(yawArm(j));
            case LIFT -> sign(j.axisUp());
            case SLIDE -> sign(j.axisRight());
        };
    }

    private static int sign(double v) {
        return Math.abs(v) < 1.0e-6 ? 0 : (v > 0 ? 1 : -1);
    }

    static boolean turnNeedsBoost(Vcc4Snapshot.Jet j, Vcc4Snapshot s) {
        JetRole role = JetRole.byOrdinal(j.role());
        boolean turning = role == JetRole.TURN || (role == JetRole.AUTO && j.job() == JetMixer.Job.TURN.ordinal());
        return turning && !s.jetDrive() && !s.jetsSteerWithoutBoost();
    }

    static double yawArm(Vcc4Snapshot.Jet j) {
        return j.armForward() * j.axisRight() - j.armRight() * j.axisForward();
    }

    static List<String> labels(Set<Ref> refs, Vcc4Snapshot s, Control c) {
        List<String> out = new ArrayList<>();
        List<String> wheels = new ArrayList<>();
        if (c == Control.STEER) {
            Set<Integer> axles = new LinkedHashSet<>();
            for (Ref r : refs) if (r.kind() == Kind.WHEEL) axles.add(s.wheels().get(r.index()).axle());
            for (int a : axles) if (a >= 0) wheels.add("A" + (a + 1));
        }
        if (!wheels.isEmpty()) out.add(String.join(" ", wheels));
        for (Kind kind : new Kind[]{Kind.JET, Kind.SWIVEL, Kind.MACHINE, Kind.LINK}) {
            StringBuilder line = new StringBuilder();
            for (Ref r : refs) {
                if (r.kind() != kind) continue;
                line.append(line.isEmpty() ? "" : " ").append(prefix(kind)).append(r.index() + 1);
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }

    static String prefix(Kind kind) {
        return switch (kind) {
            case WHEEL -> "W";
            case JET -> "J";
            case SWIVEL -> "S";
            case GOVERNOR -> "G";
            case MACHINE -> "M";
            case LINK -> "L";
        };
    }

    static int wheelCount(Set<Ref> refs) {
        int n = 0;
        for (Ref r : refs) if (r.kind() == Kind.WHEEL) n++;
        return n;
    }

    static Vcc4Snapshot.Body bodyOf(Vcc4Snapshot s, int index) {
        for (Vcc4Snapshot.Body b : s.bodies()) if (b.index() == index) return b;
        return null;
    }

    static AxleRole roleOf(Vcc4Snapshot s, int axle) {
        return axle >= 0 && axle < s.roles().length ? AxleRole.byOrdinal(s.roles()[axle]) : AxleRole.FIXED;
    }

    static boolean drives(Vcc4Snapshot s, Vcc4Snapshot.Wheel w) {
        if (w.governor() < 0 || w.governor() >= s.governors().size()) return false;
        if (s.governors().get(w.governor()).excluded()) return false;
        return w.axle() < 0 || w.axle() >= s.driven().length || s.driven()[w.axle()];
    }
}
