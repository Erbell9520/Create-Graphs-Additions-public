package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.AuxAxis;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AerialParts {
    public static final double TAIL = 2.0;

    public static final double ALONG = 0.7;

    public static final int RUDDER_THROW = 30;
    public static final int ELEVATOR_THROW = 20;
    public static final int TOOL_THROW = AuxAxis.DEFAULT_TRAVEL_DEG;

    public static final double SLEW_DEG = AuxAxis.SLEW_DEG;

    private AerialParts() {
    }

    public record SwivelSpec(long pos, double lon, double hingeUp, double hingeRight, boolean onShip,
                             boolean machineTurns) {
    }

    public record MachineSpec(long pos, boolean runner, boolean gunnery) {
        public MachineSpec(long pos, boolean runner) {
            this(pos, runner, false);
        }
    }

    public record Assigned(Map<Long, AerialSetup.Surface> surfaces, Map<Long, AerialSetup.Tool> tools) {
    }

    public static AerialSetup.Surface propose(SwivelSpec s) {
        if (s.machineTurns()) return new AerialSetup.Surface(AerialSetup.SurfaceRole.OFF, TOOL_THROW, false, PartInput.OFF, false);
        boolean tail = s.onShip() && s.lon() < -TAIL;
        if (tail && Math.abs(s.hingeUp()) > ALONG) {
            return new AerialSetup.Surface(AerialSetup.SurfaceRole.RUDDER, RUDDER_THROW, false, PartInput.OFF, false);
        }
        if (tail && Math.abs(s.hingeRight()) > ALONG) {
            return new AerialSetup.Surface(AerialSetup.SurfaceRole.ELEVATOR, ELEVATOR_THROW, false, PartInput.OFF, false);
        }
        return null;
    }

    public static Assigned assign(AerialSetup setup, List<SwivelSpec> swivels, List<MachineSpec> machines) {
        boolean[] taken = new boolean[PartInput.AUTO_ORDER.length];
        for (SwivelSpec s : swivels) {
            AerialSetup.Surface chosen = setup.surfaces.get(s.pos());
            if (chosen != null && chosen.role() == AerialSetup.SurfaceRole.TOOL) take(taken, chosen.input());
        }
        for (MachineSpec m : machines) {
            AerialSetup.Tool chosen = setup.tools.get(m.pos());
            if (chosen != null) take(taken, chosen.input());
        }
        for (AerialSetup.Light l : setup.lights.values()) {
            if (l.job().control != null && l.job().control.isAux()) take(taken, PartInput.of(l.job().control));
        }
        Map<Long, AerialSetup.Surface> surfaces = new LinkedHashMap<>();
        for (SwivelSpec s : swivels) {
            AerialSetup.Surface chosen = setup.surfaces.get(s.pos());
            if (chosen != null) {
                surfaces.put(s.pos(), chosen);
                continue;
            }
            AerialSetup.Surface proposal = propose(s);
            if (proposal == null) {
                PartInput key = next(taken);
                proposal = key == PartInput.OFF
                        ? new AerialSetup.Surface(AerialSetup.SurfaceRole.OFF, TOOL_THROW, false, PartInput.OFF, false)
                        : new AerialSetup.Surface(AerialSetup.SurfaceRole.TOOL, TOOL_THROW, false, key, key.latchedByDefault());
            }
            surfaces.put(s.pos(), proposal);
        }
        Map<Long, AerialSetup.Tool> tools = new LinkedHashMap<>();
        for (MachineSpec m : machines) {
            AerialSetup.Tool chosen = setup.tools.get(m.pos());
            if (chosen == null && m.gunnery()) chosen = new AerialSetup.Tool(PartInput.OFF, MachineAxis.defaultSetting(m.runner()));
            tools.put(m.pos(), chosen != null ? chosen : new AerialSetup.Tool(next(taken), MachineAxis.defaultSetting(m.runner())));
        }
        return new Assigned(surfaces, tools);
    }

    public static double target(AerialSetup.Surface surface, SwivelSpec s, double turn, double trim) {
        return target(surface, s, turn, trim, 1.0);
    }

    public static double target(AerialSetup.Surface surface, SwivelSpec s, double turn, double trim, double flow) {
        double sign = (surface.invert() ? -1.0 : 1.0) * (flow < 0.0 ? -1.0 : 1.0);

        double lever = s.lon() < 0.0 ? 1.0 : -1.0;
        return switch (surface.role()) {
            case RUDDER -> sign * lever * Math.signum(nonZero(s.hingeUp())) * clamp(turn) * surface.degrees();

            case ELEVATOR -> -sign * lever * Math.signum(nonZero(s.hingeRight())) * clamp(trim) * surface.degrees();
            default -> 0.0;
        };
    }

    public static double slew(double from, double to) {
        if (!Double.isFinite(from)) from = 0.0;
        if (!Double.isFinite(to)) return from;
        return from + Math.max(-SLEW_DEG, Math.min(SLEW_DEG, to - from));
    }

    public static AuxAxis.Setting auxSetting(AerialSetup.Surface s) {
        return new AuxAxis.Setting(s.degrees(), s.latched(), s.invert()).sane();
    }

    public static Control control(PartInput input) {
        return input == null ? null : input.control();
    }

    private static PartInput next(boolean[] taken) {
        for (int i = 0; i < taken.length; i++) {
            if (!taken[i]) {
                taken[i] = true;
                return PartInput.AUTO_ORDER[i];
            }
        }
        return PartInput.OFF;
    }

    private static void take(boolean[] taken, PartInput input) {
        for (int i = 0; i < taken.length; i++) taken[i] |= input == PartInput.AUTO_ORDER[i];
    }

    private static double nonZero(double v) {
        return v == 0.0 ? 1.0 : v;
    }

    private static double clamp(double v) {
        return Double.isFinite(v) ? Math.max(-1.0, Math.min(1.0, v)) : 0.0;
    }
}
