package com.createstats.surveyor.core;

import java.util.List;

public final class ScheduleRun {
    public static final int INTERVAL = 40;

    public static final int NO_MATCH = -1;

    public static final int NO_ROUTE = -2;

    public enum State {
        PRE_TRANSIT, IN_TRANSIT, POST_TRANSIT
    }

    public interface Host {
        int pick(String glob);

        int waitingAt();

        String name(int node);
    }

    public record Step(int sendTo, boolean pause, boolean resume, String status, List<List<StopRules.Bar>> bars) {
    }

    private final RouteSchedule schedule;
    private int entry;
    private State state = State.PRE_TRANSIT;
    private int cooldown;
    private boolean paused;
    private boolean completed;
    private boolean held;
    private String title = "";
    private int throttle = 100;
    private int target = -1;
    private StopRules.Progress wait;
    private String status = "";
    private List<List<StopRules.Bar>> bars = List.of();

    public ScheduleRun(RouteSchedule schedule) {
        this(schedule, 0);
    }

    public ScheduleRun(RouteSchedule schedule, int progress) {
        this.schedule = schedule;
        this.entry = schedule.entries().isEmpty() ? 0 : Math.max(0, Math.min(schedule.entries().size() - 1, progress));
    }

    public record Saved(int entry, State state, int cooldown, boolean paused, boolean completed, boolean held,
                        String title, int throttle, int target, StopRules.Progress.Saved progress, String status) {}

    public Saved save() {
        return new Saved(entry, state, cooldown, paused, completed, held, title, throttle, target,
                wait == null ? null : wait.save(), status);
    }

    public void restore(Saved s) {
        entry = Math.max(0, Math.min(schedule.entries().size(), s.entry()));
        state = s.state(); cooldown = Math.max(0, s.cooldown()); paused = s.paused(); completed = s.completed();
        held = s.held(); title = s.title(); throttle = Math.max(5, Math.min(100, s.throttle()));
        target = s.target(); status = s.status();
        if (s.progress() != null && entry < schedule.entries().size()) wait = StopRules.Progress.restore(schedule.entries().get(entry).asStop(), s.progress());
        else if (state == State.POST_TRANSIT) state = State.PRE_TRANSIT;
    }

    public RouteSchedule schedule() {
        return schedule;
    }

    public int entry() {
        return entry;
    }

    public State state() {
        return state;
    }

    public boolean completed() {
        return completed;
    }

    public boolean paused() {
        return paused;
    }

    public String title() {
        return title;
    }

    public int throttle() {
        return throttle;
    }

    public int target() {
        return target;
    }

    public String status() {
        return status;
    }

    public void skip() {
        state = State.PRE_TRANSIT;
        entry++;
        wait = null;
        cooldown = 0;
        target = -1;
        bars = List.of();
    }

    public void restart() {
        entry = 0;
        state = State.PRE_TRANSIT;
        paused = false;
        completed = false;
        cooldown = 0;
        target = -1;
        wait = null;
    }

    public Step tick(long now, Host host, StopRules.Cargo cargo) {
        if (paused) return new Step(-1, false, false, status, List.of());
        List<RouteSchedule.Entry> entries = schedule.entries();
        if (entries.isEmpty()) {
            paused = true;
            completed = true;
            status = "the schedule is empty";
            return new Step(-1, true, false, status, List.of());
        }
        if (state == State.IN_TRANSIT) {
            if (host.waitingAt() == target) {
                arrive(now);
            } else {
                status = "travelling to " + host.name(target);
                return new Step(-1, false, false, status, List.of());
            }
        }
        if (entry >= entries.size()) {
            entry = 0;
            if (!schedule.cyclic()) {
                paused = true;
                completed = true;
                status = "schedule completed";
                return new Step(-1, true, false, status, List.of());
            }
        }
        if (cooldown-- > 0) return new Step(-1, false, false, status, bars);
        RouteSchedule.Entry e = entries.get(entry);
        if (state == State.POST_TRANSIT) {
            if (!e.instruction().waits() || e.conditions().isEmpty()) {
                advance();
                return new Step(-1, false, false, status, List.of());
            }
            StopRules.Verdict v = wait.tick(now, cargo);
            bars = v.bars();
            status = "waiting at " + host.name(target) + ": " + progress(v);
            if (v.go()) advance();
            return new Step(-1, false, false, status, bars);
        }

        bars = List.of();
        switch (e.instruction()) {
            case RouteSchedule.Rename r -> {
                title = r.title();
                advance();
                return new Step(-1, false, false, status, List.of());
            }
            case RouteSchedule.Throttle t -> {
                throttle = t.percent();
                advance();
                return new Step(-1, false, false, status, List.of());
            }
            case RouteSchedule.Unsupported u -> {
                status = "skipped \"" + u.id() + "\": " + u.why();
                advance();
                return new Step(-1, false, false, status, List.of());
            }
            case RouteSchedule.TravelTo t -> {
                int node = host.pick(t.station());
                if (node < 0) {
                    status = node == NO_ROUTE ? "cannot get to " + t.station() + " from here: trying again"
                            : "no station called " + t.station() + " on this pathway: trying again";
                    cooldown = INTERVAL;
                    boolean hold = host.waitingAt() < 0 && !held;
                    held |= hold;
                    return new Step(-1, hold, false, status, List.of());
                }
                boolean resume = held;
                held = false;
                target = node;
                if (host.waitingAt() == node) {
                    arrive(now);
                    status = "at " + host.name(node);
                    return new Step(-1, false, resume, status, List.of());
                }
                state = State.IN_TRANSIT;
                status = "travelling to " + host.name(node);
                return new Step(node, false, resume, status, List.of());
            }
        }
    }

    private void arrive(long now) {
        state = State.POST_TRANSIT;
        RouteSchedule.Entry e = entry < schedule.entries().size() ? schedule.entries().get(entry) : null;
        wait = new StopRules.Progress(e == null ? Stop.DEFAULT.withRules(List.of()) : e.asStop(), now);
    }

    private void advance() {
        state = State.PRE_TRANSIT;
        entry++;
        wait = null;
    }

    private static String progress(StopRules.Verdict v) {
        for (List<StopRules.Bar> col : v.bars()) {
            for (StopRules.Bar b : col) {
                if (!b.met()) return b.now();
            }
        }
        return "done";
    }
}
