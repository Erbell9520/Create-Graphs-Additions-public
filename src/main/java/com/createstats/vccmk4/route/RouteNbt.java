package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.PlantModel;
import com.createstats.surveyor.core.RouteRunner;
import net.minecraft.nbt.CompoundTag;

// its own tag, old saves just have no route
public final class RouteNbt {
    public static final int VERSION = 1;

    private RouteNbt() {
    }

    public static CompoundTag writeSaved(RouteRunner.Saved s) {
        CompoundTag t = new CompoundTag();
        t.putString("State", s.state().key);
        t.putString("Reason", s.reason());
        t.putDouble("S", s.s());
        t.putInt("NextStop", s.nextStop());
        t.putInt("WaitingAt", s.waitingAt());
        t.putLong("WaitingSince", s.waitingSince());
        t.putInt("SendTo", s.sendTo());
        t.putBoolean("Recall", s.recallToStart());
        t.putInt("Laps", s.laps());
        t.putDouble("LastLap", s.lastLap());
        return t;
    }

    public static RouteRunner.Saved readSaved(CompoundTag t) {
        RouteRunner.State state = RouteRunner.State.PAUSED;
        String key = t.getString("State");
        for (RouteRunner.State s : RouteRunner.State.values()) if (s.key.equals(key)) state = s;
        return new RouteRunner.Saved(state, t.getString("Reason"), t.getDouble("S"), t.getInt("NextStop"),
                t.contains("WaitingAt") ? t.getInt("WaitingAt") : -1, t.getLong("WaitingSince"),
                t.contains("SendTo") ? t.getInt("SendTo") : -1, t.getBoolean("Recall"), t.getInt("Laps"),
                t.contains("LastLap") ? t.getDouble("LastLap") : -1.0);
    }

    public static CompoundTag writePlant(PlantModel p) {
        CompoundTag t = new CompoundTag();
        t.putDouble("K", p.k());
        t.putDouble("Tau", p.tau());
        t.putDouble("TauBrake", p.tauBrake());
        t.putInt("Samples", p.samples());
        t.putInt("BrakeSamples", p.brakeSamples());
        t.putDouble("SkidGain", p.skidGain());
        return t;
    }

    public static PlantModel readPlant(CompoundTag t, PlantModel fallback) {
        if (t == null || !t.contains("K")) return fallback;
        return new PlantModel(t.getDouble("K"), t.getDouble("Tau"), t.getDouble("TauBrake"), t.getInt("Samples"),
                t.getInt("BrakeSamples"), t.getDouble("SkidGain"));
    }

    public static CompoundTag writeOptions(RouteRunner.Options o) {
        CompoundTag t = new CompoundTag();
        t.putDouble("Cruise", o.cruiseOverride());
        t.putString("Comfort", o.comfortOverride() == null ? "" : o.comfortOverride().key);
        t.putBoolean("StartOnAssign", o.startOnAssign());
        t.putBoolean("Horn", o.hornWhenBlocked());
        return t;
    }

    public static RouteRunner.Options readOptions(CompoundTag t) {
        if (t == null || t.isEmpty()) return RouteRunner.Options.DEFAULT;
        String c = t.getString("Comfort");
        return new RouteRunner.Options(t.getDouble("Cruise"), c.isEmpty() ? null : Comfort.byKey(c),
                !t.contains("StartOnAssign") || t.getBoolean("StartOnAssign"), !t.contains("Horn") || t.getBoolean("Horn"));
    }
}
