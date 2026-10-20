package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.CargoSnapshot;
import com.createstats.surveyor.core.PathNetwork;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.ScheduleRun;
import com.createstats.surveyor.core.StopRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class TravelNbt {
    private TravelNbt() {}

    public static CompoundTag progress(StopRules.Progress.Saved s) {
        CompoundTag t = new CompoundTag();
        t.putLong("Arrived", s.arrived()); t.putIntArray("Current", s.current());
        t.put("Since", new LongArrayTag(s.since()));
        return t;
    }

    public static StopRules.Progress.Saved progress(CompoundTag t) {
        return new StopRules.Progress.Saved(t.getLong("Arrived"), t.getIntArray("Current"), t.getLongArray("Since"));
    }

    public static CompoundTag schedule(ScheduleRun.Saved s) {
        CompoundTag t = new CompoundTag();
        t.putInt("Entry", s.entry()); t.putString("State", s.state().name()); t.putInt("Cooldown", s.cooldown());
        t.putBoolean("Paused", s.paused()); t.putBoolean("Completed", s.completed()); t.putBoolean("Held", s.held());
        t.putString("Title", s.title()); t.putInt("Throttle", s.throttle()); t.putInt("Target", s.target());
        t.putString("Status", s.status()); if (s.progress() != null) t.put("Wait", progress(s.progress()));
        return t;
    }

    public static ScheduleRun.Saved schedule(CompoundTag t) {
        ScheduleRun.State state;
        try { state = ScheduleRun.State.valueOf(t.getString("State")); }
        catch (IllegalArgumentException e) { state = ScheduleRun.State.PRE_TRANSIT; }
        return new ScheduleRun.Saved(t.getInt("Entry"), state, t.getInt("Cooldown"), t.getBoolean("Paused"),
                t.getBoolean("Completed"), t.getBoolean("Held"), t.getString("Title"), t.getInt("Throttle"),
                t.getInt("Target"), t.contains("Wait") ? progress(t.getCompound("Wait")) : null, t.getString("Status"));
    }

    public static CompoundTag runtime(RouteRunner.Travel s) {
        CompoundTag t = new CompoundTag();
        t.putString("Resume", s.resumeTo().key); t.putLong("StateSince", s.stateSince());
        t.putDouble("LapTravel", s.lapTravel()); t.putLong("LapStart", s.lapStart());
        t.putDouble("Departed", s.departedFrom()); t.putDouble("LastToStop", s.lastToStop());
        t.putInt("Joined", s.joinedTicks()); t.putInt("Blocked", s.blockedTicks()); t.putInt("Clear", s.clearTicks());
        t.putInt("Stuck", s.stuckTicks()); t.putInt("Tries", s.tries()); t.putInt("TryTicks", s.tryTicks());
        t.putDouble("TryFrom", s.tryFrom()); t.putDouble("TryOffset", s.tryOffset()); t.putString("StuckWhy", s.stuckWhy());
        return t;
    }

    public static RouteRunner.Travel runtime(CompoundTag t) {
        RouteRunner.State resume = RouteRunner.State.RUNNING;
        for (var state : RouteRunner.State.values()) if (state.key.equals(t.getString("Resume"))) resume = state;
        return new RouteRunner.Travel(resume, t.getLong("StateSince"), t.getDouble("LapTravel"), t.getLong("LapStart"),
                t.getDouble("Departed"), t.getDouble("LastToStop"), t.getInt("Joined"), t.getInt("Blocked"),
                t.getInt("Clear"), t.getInt("Stuck"), t.getInt("Tries"), t.getInt("TryTicks"),
                t.getDouble("TryFrom"), t.getDouble("TryOffset"), t.getString("StuckWhy"));
    }

    private static CompoundTag counts(Map<String, Long> values) {
        CompoundTag t = new CompoundTag(); values.forEach(t::putLong); return t;
    }

    private static Map<String, Long> counts(CompoundTag t) {
        Map<String, Long> out = new HashMap<>();
        for (String k : t.getAllKeys()) out.put(k, Math.max(0L, t.getLong(k)));
        return out;
    }

    public static CompoundTag cargo(CargoSnapshot s) {
        CompoundTag t = new CompoundTag();
        t.put("Items", counts(s.itemCounts())); t.put("Stacks", counts(s.stackCounts())); t.put("Fluids", counts(s.fluidCounts()));
        t.putInt("Idle", s.idleAtSample()); t.putLong("At", s.sampledAt()); t.putBoolean("Known", s.known());
        return t;
    }

    public static CargoSnapshot cargo(CompoundTag t) {
        return new CargoSnapshot(counts(t.getCompound("Items")), counts(t.getCompound("Stacks")), counts(t.getCompound("Fluids")),
                t.getInt("Idle"), t.getLong("At"), t.getBoolean("Known"));
    }

    public static CompoundTag network(PathNetwork.Route r) {
        CompoundTag t = new CompoundTag(); ListTag legs = new ListTag(); ListTag turns = new ListTag();
        for (var l : r.legs()) {
            CompoundTag c = new CompoundTag(); c.putUUID("Pathway", l.pathway()); c.putDouble("From", l.from()); c.putDouble("To", l.to()); legs.add(c);
        }
        for (var x : r.transfers()) {
            CompoundTag c = new CompoundTag(); c.putUUID("From", x.from()); c.putUUID("To", x.to());
            c.putDouble("FromS", x.fromS()); c.putDouble("ToS", x.toS()); c.putDouble("Turn", x.turn());
            c.putDouble("X", x.x()); c.putDouble("Z", x.z()); turns.add(c);
        }
        t.put("Legs", legs); t.put("Turns", turns); t.putDouble("Seconds", r.seconds());
        t.putUUID("Station", r.station()); t.putInt("Node", r.stationNode()); return t;
    }

    public static PathNetwork.Route network(CompoundTag t) {
        try {
            var legs = new ArrayList<PathNetwork.Leg>(); var turns = new ArrayList<PathNetwork.Transfer>();
            ListTag ls = t.getList("Legs", Tag.TAG_COMPOUND); ListTag ts = t.getList("Turns", Tag.TAG_COMPOUND);
            for (int i = 0; i < ls.size(); i++) { var c = ls.getCompound(i); legs.add(new PathNetwork.Leg(c.getUUID("Pathway"), c.getDouble("From"), c.getDouble("To"))); }
            for (int i = 0; i < ts.size(); i++) {
                var c = ts.getCompound(i); turns.add(new PathNetwork.Transfer(c.getUUID("From"), c.getDouble("FromS"),
                        c.getUUID("To"), c.getDouble("ToS"), c.getDouble("Turn"), c.getDouble("X"), c.getDouble("Z")));
            }
            if (legs.isEmpty() || turns.size() != legs.size() - 1) return null;
            return new PathNetwork.Route(legs, turns, t.getDouble("Seconds"), t.getUUID("Station"), t.getInt("Node"));
        } catch (RuntimeException e) { return null; }
    }
}
