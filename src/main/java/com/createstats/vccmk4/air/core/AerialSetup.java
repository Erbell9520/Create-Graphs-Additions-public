package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.AuxAxis;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AerialSetup {
    public static final int[] LIMITS = {25, 50, 75, 100};

    public static final int[] THROWS = {10, 15, 20, 30, 45};
    // tools get half a turn, the fins' 45 cap kept hatches half shut
    public static final int TOOL_STEP = 5;

    public static final int CAP_STEP = 8;

    public static final List<Control> CONTROLS = List.of(Control.THROTTLE, Control.STEER, Control.LIFT, Control.BOOST,
            Control.TRIM, Control.SLIDE, Control.BEAM, Control.AUX_A, Control.AUX_B, Control.AUX_C, Control.AUX_D);

    public enum EngineJob {
        AUTO("auto"),

        PUSH("push"),

        LIFT("lift"),

        OFF("off");

        public final String key;

        EngineJob(String key) {
            this.key = key;
        }

        public EngineJob next() {
            EngineJob[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public static EngineJob byOrdinal(int i) {
            EngineJob[] v = values();
            return i >= 0 && i < v.length ? v[i] : AUTO;
        }

        public static EngineJob byKey(String key) {
            for (EngineJob j : values()) if (j.key.equals(key)) return j;
            return AUTO;
        }
    }

    public record Engine(EngineJob job, boolean reverse, int limit) {
        public static final Engine DEFAULT = new Engine(EngineJob.AUTO, true, 100);

        public Engine {
            job = job == null ? EngineJob.AUTO : job;
            limit = Math.max(LIMITS[0], Math.min(100, limit));
        }

        public Engine withJob(EngineJob j) {
            return new Engine(j, reverse, limit);
        }

        public Engine withReverse(boolean r) {
            return new Engine(job, r, limit);
        }

        public Engine withLimit(int l) {
            return new Engine(job, reverse, l);
        }
    }

    public enum SurfaceRole {
        OFF("off"),

        RUDDER("rudder"),

        ELEVATOR("elevator"),

        TOOL("tool");

        public final String key;

        SurfaceRole(String key) {
            this.key = key;
        }

        public SurfaceRole next() {
            SurfaceRole[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public static SurfaceRole byOrdinal(int i) {
            SurfaceRole[] v = values();
            return i >= 0 && i < v.length ? v[i] : OFF;
        }

        public static SurfaceRole byKey(String key) {
            for (SurfaceRole r : values()) if (r.key.equals(key)) return r;
            return OFF;
        }
    }

    public record Surface(SurfaceRole role, int degrees, boolean invert, PartInput input, boolean latched) {
        public Surface {
            role = role == null ? SurfaceRole.OFF : role;
            degrees = role == SurfaceRole.TOOL
                    ? Math.max(AuxAxis.MIN_TRAVEL_DEG, Math.min(AuxAxis.MAX_TRAVEL_DEG, degrees))
                    : Math.max(THROWS[0], Math.min(THROWS[THROWS.length - 1], degrees));
            input = input == null ? PartInput.OFF : input;
        }

        public Surface withRole(SurfaceRole r) {
            return new Surface(r, degrees, invert, input, latched);
        }

        public Surface withDegrees(int d) {
            return new Surface(role, d, invert, input, latched);
        }

        public Surface withInvert(boolean i) {
            return new Surface(role, degrees, i, input, latched);
        }

        public Surface withInput(PartInput in) {
            return new Surface(role, degrees, invert, in, latched);
        }

        public Surface withLatched(boolean l) {
            return new Surface(role, degrees, invert, input, l);
        }
    }

    public record Tool(PartInput input, MachineAxis.Setting setting) {
        public Tool {
            input = input == null ? PartInput.OFF : input;
            setting = setting == null ? MachineAxis.Setting.SPRUNG : setting.sane();
        }

        public Tool withInput(PartInput in) {
            return new Tool(in, setting);
        }

        public Tool withSetting(MachineAxis.Setting s) {
            return new Tool(input, s);
        }
    }

    public record Light(AirLight job, boolean toggle) {
        public Light {
            job = job == null ? AirLight.OFF : job;
        }

        public Light withJob(AirLight j) {
            return new Light(j, toggle);
        }

        public Light withToggle(boolean t) {
            return new Light(job, t);
        }
    }

    public Feel feel = Feel.STANDARD;
    public LeaveMode leave = LeaveMode.ANCHOR;

    public int noseTurn;

    public boolean noseSet;

    public double trim;

    public double altitudeCap = Double.NaN;

    public boolean bell = true;
    public Keymap keymap = defaultKeys();

    public final Map<Long, Engine> engines = new HashMap<>();

    public final Map<Long, Boolean> balloonOff = new HashMap<>();
    public final Map<Long, Surface> surfaces = new HashMap<>();
    public final Map<Long, Tool> tools = new HashMap<>();
    public final Map<Long, Light> lights = new HashMap<>();

    public static Keymap defaultKeys() {
        Keymap k = Keymap.defaults();
        k.setPlus(Control.LIFT, Channel.JUMP.id());
        k.setMinus(Control.LIFT, Channel.AUX2.id());
        k.setPlus(Control.BRAKE, Channel.NONE.id());
        k.setMinus(Control.AUX_A, Channel.NONE.id());
        return k;
    }

    public Engine engine(long pos) {
        return engines.getOrDefault(pos, Engine.DEFAULT);
    }

    public boolean balloonDriven(long firstHeater) {
        return !balloonOff.getOrDefault(firstHeater, false);
    }

    public Surface surface(long pos, Surface proposal) {
        return surfaces.getOrDefault(pos, proposal);
    }

    public Tool tool(long pos, Tool proposal) {
        return tools.getOrDefault(pos, proposal);
    }

    public Light light(long pos, Light proposal) {
        return lights.getOrDefault(pos, proposal);
    }

    public void setCap(double y, double worldBottom, double worldTop) {
        altitudeCap = Double.isFinite(y) ? Math.max(worldBottom, Math.min(worldTop, Math.round(y))) : Double.NaN;
    }

    public void setTrim(double t) {
        trim = Math.round(Math.max(-1.0, Math.min(1.0, t)) * 20.0) / 20.0;
    }

    public static int stepLimit(int current, int dir) {
        return step(LIMITS, current, dir);
    }

    public static int stepThrow(SurfaceRole role, int current, int dir) {
        if (role == SurfaceRole.TOOL) {
            int next = current + (dir > 0 ? TOOL_STEP : -TOOL_STEP);
            return Math.max(AuxAxis.MIN_TRAVEL_DEG, Math.min(AuxAxis.MAX_TRAVEL_DEG, next));
        }
        return step(THROWS, current, dir);
    }

    private static int step(int[] steps, int current, int dir) {
        if (dir > 0) {
            for (int v : steps) if (v > current) return v;
            return steps[steps.length - 1];
        }
        for (int i = steps.length - 1; i >= 0; i--) if (steps[i] < current) return steps[i];
        return steps[0];
    }

    public void resetParts() {
        engines.clear();
        balloonOff.clear();
        surfaces.clear();
        tools.clear();
        lights.clear();
    }
}
