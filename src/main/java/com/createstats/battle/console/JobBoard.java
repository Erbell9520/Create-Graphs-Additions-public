package com.createstats.battle.console;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.HeaderStatus;
import com.createstats.battle.core.Places;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobBoard {
    public static final int RED = 0;
    public static final int AMBER = 1;
    public static final int WAIT = 2;

    public static final long DONE_TICKS = 1200L;

    public static final int MAX_DONE = 3;

    public enum Kind { FIRE, GUN, LOAD, PLACE }

    public record Job(String key, Kind kind, int number, int urgency, String place, int gunId, int state, int placeId,
                      int count, int anchor) {
    }

    public record Done(String key, Kind kind, int number, String place, int gunId, long at) {
    }

    private final Map<String, Integer> numbers = new HashMap<>();
    private final Map<String, Job> open = new LinkedHashMap<>();
    private final List<Done> done = new ArrayList<>();
    private int next = 1;
    @Nullable
    private String ship;

    public List<Job> jobs() {
        return List.copyOf(open.values());
    }

    public List<Done> done(long clock) {
        List<Done> out = new ArrayList<>();
        for (int i = done.size() - 1; i >= 0 && out.size() < MAX_DONE; i--) {
            Done d = done.get(i);
            if (clock - d.at() <= DONE_TICKS) out.add(d);
        }
        return out;
    }

    public void update(BattleReport r, @Nullable DetailView detail, long clock) {
        if (ship == null || !ship.equals(r.key())) {
            ship = r.key();
            numbers.clear();
            open.clear();
            done.clear();
            next = 1;
        }
        boolean detailOk = detail != null && detail.key().equals(r.key());
        List<Job> fresh = new ArrayList<>();

        if (!r.firePlace().isEmpty()) {
            int burning = detailOk ? detail.burning().length : 0;
            int anchor = detailOk && detail.burning().length > 0 ? detail.burning()[0] : -1;
            fresh.add(new Job("fire", Kind.FIRE, 0, RED, r.firePlace(), -1, -1, -1, burning, anchor));
        }

        if (r.powered()) {
            List<BattleReport.GunLine> guns = new ArrayList<>(r.guns());
            guns.sort(Comparator.comparingInt(BattleReport.GunLine::id));
            for (BattleReport.GunLine g : guns) {
                GunState st = g.gunState();
                if (st == GunState.HOLD || st == GunState.NO_POWER) continue;
                if (st.lamp() == HeaderStatus.Lamp.RED && st != GunState.EMPTY) {
                    fresh.add(new Job("gun:" + g.id(), Kind.GUN, 0, RED, "", g.id(), st.ordinal(), -1, 0, -1));
                }
                if (st == GunState.EMPTY || (g.load() == 0 && st == GunState.IDLE)) {
                    fresh.add(new Job("load:" + g.id(), Kind.LOAD, 0, RED, "", g.id(), -1, -1, 0, -1));
                }
            }
        } else {
            for (Job j : open.values()) if (j.kind() == Kind.GUN || j.kind() == Kind.LOAD) fresh.add(j);
        }

        if (detailOk) {
            List<Job> places = new ArrayList<>();
            for (DetailView.Place p : detail.places()) {
                char first = p.name().isEmpty() ? ' ' : p.name().charAt(0);
                int urgency = first == 'P' || first == 'G' ? RED : AMBER;
                if (p.kind() == Places.KIND_BROKE_AWAY) urgency = WAIT;
                if (!r.firePlace().isEmpty() && p.name().equals(r.firePlace())) urgency = WAIT;
                places.add(new Job("place:" + p.id(), Kind.PLACE, 0, urgency, p.name(), -1, -1, p.id(), p.size(), p.anchor()));
            }
            places.sort(Comparator.comparingInt(Job::urgency).thenComparing(Comparator.comparingInt(Job::count).reversed())
                    .thenComparingInt(Job::placeId));
            fresh.addAll(places);
        } else {
            for (Job j : open.values()) if (j.kind() == Kind.PLACE) fresh.add(j);
        }

        List<Job> ordered = new ArrayList<>(fresh.size());
        for (int u = RED; u <= WAIT; u++) {
            for (Job j : fresh) if (j.urgency() == u && j.kind() == Kind.FIRE) ordered.add(j);
            for (Job j : fresh) if (j.urgency() == u && (j.kind() == Kind.GUN || j.kind() == Kind.LOAD)) ordered.add(j);
            for (Job j : fresh) if (j.urgency() == u && j.kind() == Kind.PLACE) ordered.add(j);
        }

        Map<String, Job> now = new LinkedHashMap<>();
        for (Job j : ordered) {
            Integer n = numbers.get(j.key());
            if (n == null) {
                n = next++;
                numbers.put(j.key(), n);
            }
            now.put(j.key(), new Job(j.key(), j.kind(), n, j.urgency(), j.place(), j.gunId(), j.state(), j.placeId(), j.count(),
                    j.anchor()));
        }
        for (Job was : open.values()) {
            if (now.containsKey(was.key())) continue;
            numbers.remove(was.key());
            if (was.kind() == Kind.GUN || was.kind() == Kind.LOAD) {
                BattleReport.GunLine g = gun(r, was.gunId());

                if (g == null || g.gunState() == GunState.HOLD) continue;
            }
            done.add(new Done(was.key(), was.kind(), was.number(), was.place(), was.gunId(), clock));
        }
        while (done.size() > 16) done.remove(0);
        open.clear();
        open.putAll(now);
        if (open.isEmpty()) {
            numbers.clear();
            next = 1;
        }
    }

    @Nullable
    private static BattleReport.GunLine gun(BattleReport r, int id) {
        for (BattleReport.GunLine g : r.guns()) if (g.id() == id) return g;
        return null;
    }
}
