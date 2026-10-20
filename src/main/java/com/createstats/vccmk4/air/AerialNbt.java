package com.createstats.vccmk4.air;

import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.V3;
import com.createstats.vccmk4.air.core.AerialPilot;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.PropLearner;
import com.createstats.vccmk4.air.core.VerticalObserver;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AerialNbt {
    private AerialNbt() {
    }

    static CompoundTag pilot(AerialPilot.State s) {
        CompoundTag t = new CompoundTag();
        t.putInt("Mode", s.mode());
        t.putInt("Leave", s.leave());
        t.putInt("Feel", s.feel());
        t.putDouble("Telegraph", s.telegraph());
        putFinite(t, "TargetAltitude", s.targetAltitude());
        putFinite(t, "Heading", s.heading());
        putFinite(t, "SpeedOrder", s.speedOrder());
        putFinite(t, "AnchorX", s.anchorX());
        putFinite(t, "AnchorZ", s.anchorZ());
        if (s.vertical() != null) {
            ListTag v = new ListTag();
            for (double d : s.vertical()) v.add(net.minecraft.nbt.DoubleTag.valueOf(Double.isFinite(d) ? d : 0.0));
            t.put("Vertical", v);
        }
        putFinite(t, "Drag", s.drag());
        putFinite(t, "TurnDrag", s.turnDrag());
        return t;
    }

    static AerialPilot.State readPilot(CompoundTag t) {
        return readPilot(t, true);
    }

    static AerialPilot.State readPilot(CompoundTag t, boolean keepVertical) {
        double[] vertical = null;
        if (keepVertical && t.contains("Vertical", Tag.TAG_LIST)) {
            ListTag v = t.getList("Vertical", Tag.TAG_DOUBLE);
            if (v.size() == VerticalObserver.TERMS) {
                vertical = new double[v.size()];
                for (int i = 0; i < v.size(); i++) vertical[i] = v.getDouble(i);
            }
        }
        return new AerialPilot.State(t.getInt("Mode"), t.getInt("Leave"), t.getInt("Feel"), t.getDouble("Telegraph"),
                finite(t, "TargetAltitude"), finite(t, "Heading"), finite(t, "SpeedOrder"), finite(t, "AnchorX"),
                finite(t, "AnchorZ"), vertical, finite(t, "Drag"), finite(t, "TurnDrag"));
    }

    static CompoundTag setup(AerialSetup s) {
        CompoundTag t = tuning(s);
        t.putInt("Nose", Math.floorMod(s.noseTurn, 4));
        t.putBoolean("NoseSet", s.noseSet);
        t.putDouble("Trim", s.trim);
        CompoundTag engines = new CompoundTag();
        for (Map.Entry<Long, AerialSetup.Engine> e : s.engines.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putString("Job", e.getValue().job().key);
            p.putBoolean("Reverse", e.getValue().reverse());
            p.putInt("Limit", e.getValue().limit());
            engines.put(Long.toString(e.getKey()), p);
        }
        t.put("Engines", engines);
        List<Long> off = new ArrayList<>();
        for (Map.Entry<Long, Boolean> e : s.balloonOff.entrySet()) if (Boolean.TRUE.equals(e.getValue())) off.add(e.getKey());
        t.putLongArray("BalloonsOff", off);
        CompoundTag surfaces = new CompoundTag();
        for (Map.Entry<Long, AerialSetup.Surface> e : s.surfaces.entrySet()) {
            AerialSetup.Surface v = e.getValue();
            CompoundTag p = new CompoundTag();
            p.putString("Role", v.role().key);
            p.putInt("Throw", v.degrees());
            p.putBoolean("Invert", v.invert());
            p.putString("Input", v.input().key());
            p.putBoolean("Latched", v.latched());
            surfaces.put(Long.toString(e.getKey()), p);
        }
        t.put("Surfaces", surfaces);
        CompoundTag tools = new CompoundTag();
        for (Map.Entry<Long, AerialSetup.Tool> e : s.tools.entrySet()) {
            AerialSetup.Tool v = e.getValue();
            CompoundTag p = new CompoundTag();
            p.putString("Input", v.input().key());
            p.putInt("Rpm", v.setting().rpm());
            p.putBoolean("Latched", v.setting().latched());
            p.putBoolean("Invert", v.setting().inverted());
            tools.put(Long.toString(e.getKey()), p);
        }
        t.put("Tools", tools);
        CompoundTag lights = new CompoundTag();
        for (Map.Entry<Long, AerialSetup.Light> e : s.lights.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putString("Job", e.getValue().job().key);
            p.putBoolean("Toggle", e.getValue().toggle());
            lights.put(Long.toString(e.getKey()), p);
        }
        t.put("Lights", lights);
        return t;
    }

    static CompoundTag tuning(AerialSetup s) {
        CompoundTag t = new CompoundTag();
        t.putString("Feel", s.feel.name());
        t.putString("Leave", s.leave.name());
        putFinite(t, "Cap", s.altitudeCap);
        t.putBoolean("Bell", s.bell);
        t.putIntArray("Keymap", s.keymap.toInts());
        return t;
    }

    record Tuning(Feel feel, LeaveMode leave, double altitudeCap, boolean bell, Keymap keymap) {
    }

    @org.jetbrains.annotations.Nullable
    static Tuning readTuning(CompoundTag t) {
        if (!t.contains("Feel") || !t.contains("Keymap")) {
            return null;
        }
        return new Tuning(byName(Feel.class, t.getString("Feel"), Feel.STANDARD),
                byName(LeaveMode.class, t.getString("Leave"), LeaveMode.ANCHOR), finite(t, "Cap"),
                !t.contains("Bell") || t.getBoolean("Bell"), Keymap.fromInts(t.getIntArray("Keymap")));
    }

    static void readSetup(CompoundTag t, AerialSetup s) {
        s.feel = byName(Feel.class, t.getString("Feel"), Feel.STANDARD);
        s.leave = byName(LeaveMode.class, t.getString("Leave"), LeaveMode.ANCHOR);
        s.noseTurn = Math.floorMod(t.getInt("Nose"), 4);

        s.noseSet = t.contains("NoseSet") ? t.getBoolean("NoseSet") : s.noseTurn != 0;
        s.setTrim(t.getDouble("Trim"));
        s.altitudeCap = finite(t, "Cap");
        s.bell = !t.contains("Bell") || t.getBoolean("Bell");
        s.keymap = t.contains("Keymap") ? Keymap.fromInts(t.getIntArray("Keymap")) : AerialSetup.defaultKeys();
        s.resetParts();
        CompoundTag engines = t.getCompound("Engines");
        for (String key : engines.getAllKeys()) {
            Long pos = parse(key);
            if (pos == null) continue;
            CompoundTag p = engines.getCompound(key);
            s.engines.put(pos, new AerialSetup.Engine(AerialSetup.EngineJob.byKey(p.getString("Job")),
                    !p.contains("Reverse") || p.getBoolean("Reverse"), p.contains("Limit") ? p.getInt("Limit") : 100));
        }
        for (long id : t.getLongArray("BalloonsOff")) s.balloonOff.put(id, true);
        CompoundTag surfaces = t.getCompound("Surfaces");
        for (String key : surfaces.getAllKeys()) {
            Long pos = parse(key);
            if (pos == null) continue;
            CompoundTag p = surfaces.getCompound(key);
            s.surfaces.put(pos, new AerialSetup.Surface(AerialSetup.SurfaceRole.byKey(p.getString("Role")), p.getInt("Throw"),
                    p.getBoolean("Invert"), PartInput.byKey(p.getString("Input")), p.getBoolean("Latched")));
        }
        CompoundTag tools = t.getCompound("Tools");
        for (String key : tools.getAllKeys()) {
            Long pos = parse(key);
            if (pos == null) continue;
            CompoundTag p = tools.getCompound(key);
            s.tools.put(pos, new AerialSetup.Tool(PartInput.byKey(p.getString("Input")),
                    new MachineAxis.Setting(p.getInt("Rpm"), p.getBoolean("Latched"), p.getBoolean("Invert")).sane()));
        }
        CompoundTag lights = t.getCompound("Lights");
        for (String key : lights.getAllKeys()) {
            Long pos = parse(key);
            if (pos == null) continue;
            CompoundTag p = lights.getCompound(key);
            s.lights.put(pos, new AerialSetup.Light(AirLight.byKey(p.getString("Job")), p.getBoolean("Toggle")));
        }
    }

    static CompoundTag angles(Map<Long, Double> angles) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<Long, Double> e : angles.entrySet()) {
            if (Double.isFinite(e.getValue())) t.putDouble(Long.toString(e.getKey()), e.getValue());
        }
        return t;
    }

    static Map<Long, Double> readAngles(CompoundTag t) {
        Map<Long, Double> out = new HashMap<>();
        for (String key : t.getAllKeys()) {
            Long pos = parse(key);
            if (pos != null) out.put(pos, t.getDouble(key));
        }
        return out;
    }

    private static Long parse(String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E byName(Class<E> type, String name, E fallback) {
        for (E e : type.getEnumConstants()) if (e.name().equals(name)) return e;
        return fallback;
    }

    static CompoundTag learned(Map<Long, PropLearner> learned) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<Long, PropLearner> e : learned.entrySet()) {
            PropLearner l = e.getValue();
            if (!l.signSeen) continue;
            CompoundTag p = new CompoundTag();
            p.putInt("Sign", l.sign);
            putFinite(p, "Gain", l.gain);
            putFinite(p, "Flow", l.flow);
            putFinite(p, "Ratio", l.ratio);
            t.put(Long.toString(e.getKey()), p);
        }
        return t;
    }

    static Map<Long, PropLearner> readLearned(CompoundTag t) {
        Map<Long, PropLearner> out = new HashMap<>();
        for (String key : t.getAllKeys()) {
            Long pos = parse(key);
            if (pos == null) continue;
            CompoundTag p = t.getCompound(key);
            if (!p.contains("Sign")) continue;
            out.put(pos, PropLearner.restored(p.getInt("Sign"), finite(p, "Gain"), finite(p, "Flow"), finite(p, "Ratio")));
        }
        return out;
    }

    static CompoundTag route(AerialSnapshot.Route r) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", r.name());
        t.putInt("Active", r.active());
        ListTag points = new ListTag();
        for (AerialSnapshot.Waypoint w : r.points()) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", w.position().x());
            p.putDouble("Y", w.position().y());
            p.putDouble("Z", w.position().z());
            putFinite(p, "Speed", w.speed());
            points.add(p);
        }
        t.put("Points", points);
        return t;
    }

    static AerialSnapshot.Route readRoute(CompoundTag t) {
        ListTag points = t.getList("Points", Tag.TAG_COMPOUND);
        if (points.isEmpty()) return AerialSnapshot.Route.NONE;
        List<AerialSnapshot.Waypoint> out = new ArrayList<>();
        for (int i = 0; i < points.size() && i < AerialSnapshot.Route.MAX_POINTS; i++) {
            CompoundTag p = points.getCompound(i);
            out.add(new AerialSnapshot.Waypoint(new V3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")),
                    finite(p, "Speed")));
        }
        return new AerialSnapshot.Route(t.getString("Name"), out, t.getInt("Active"));
    }

    private static void putFinite(CompoundTag t, String key, double v) {
        if (Double.isFinite(v)) t.putDouble(key, v);
    }

    private static double finite(CompoundTag t, String key) {
        return t.contains(key) ? t.getDouble(key) : Double.NaN;
    }
}
