package com.createstats.train.smart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrainIntel {
    private TrainIntel() {
    }

    public static final float TICKS_PER_SECOND = 20.0f;

    public static final int SIGNAL_HOLD_ALERT_TICKS = 600;

    // never print "-1s"
    public static String eta(int ticks) {
        if (ticks == -2) {
            return "no route";
        }
        if (ticks < 0) {
            return "--";
        }

        if (ticks < 200) {
            return "due";
        }
        return duration(ticks / TICKS_PER_SECOND);
    }

    public static String duration(float seconds) {
        if (seconds < 0.0f) {
            return "--";
        }
        if (seconds < 60.0f) {
            return Math.round(seconds) + "s";
        }
        final int minutes = (int) (seconds / 60.0f);
        if (minutes < 60) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes,
                    Math.round(seconds - minutes * 60.0f));
        }
        return String.format(Locale.ROOT, "%dh %02dm", minutes / 60, minutes % 60);
    }

    public static String distance(double blocks) {
        if (blocks < 0.0) {
            return "--";
        }
        return blocks < 1000.0
                ? Math.round(blocks) + " m"
                : String.format(Locale.ROOT, "%.1f km", blocks / 1000.0);
    }

    public static String speed(float blocksPerSecond) {
        final float v = Math.abs(blocksPerSecond);
        if (v < 0.05f) {
            return "0";
        }
        return v < 10.0f ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v));
    }

    public static final String SPEED_UNIT = "b/s";

    public static final class Line {
        public final String key;
        public final List<String> stops = new ArrayList<>();
        public final List<UUID> trains = new ArrayList<>();
        public int colorIndex;

        public String title = "";

        Line(String key) {
            this.key = key;
        }

        public String label() {
            if (!this.title.isEmpty()) {
                return this.title;
            }
            if (this.stops.isEmpty()) {
                return "Unscheduled";
            }
            if (this.stops.size() == 1) {
                return this.stops.get(0);
            }
            return this.stops.get(0) + " to " + this.stops.get(this.stops.size() - 1);
        }
    }

    public static List<Line> lines(RailSnapshot snapshot) {
        final Map<String, Line> byKey = new LinkedHashMap<>();
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            final String key = routeKey(t);
            final Line line = byKey.computeIfAbsent(key, Line::new);
            if (line.stops.isEmpty()) {
                line.stops.addAll(canonicalOrder(t.stops));
            }
            if (line.title.isEmpty() && !t.scheduleTitle.isEmpty()) {
                line.title = t.scheduleTitle;
            }
            line.trains.add(t.id);
        }
        final List<Line> lines = new ArrayList<>(byKey.values());
        lines.sort(Comparator.comparing(l -> l.key));
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).colorIndex = i;
        }
        return lines;
    }

    static String routeKey(RailSnapshot.TrainState t) {
        if (t.stops.isEmpty()) {
            return "";
        }
        return canonicalRoute(t.stops);
    }

    public static String canonicalRoute(List<String> stops) {
        return String.join("|", canonicalOrder(stops));
    }

    public static List<String> canonicalOrder(List<String> stops) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }
        if (stops.size() < 3) {
            final List<String> pair = new ArrayList<>(stops);
            java.util.Collections.sort(pair);
            return pair;
        }
        List<String> best = null;
        for (int direction = 0; direction < 2; direction++) {
            final List<String> order = new ArrayList<>(stops);
            if (direction == 1) {
                java.util.Collections.reverse(order);
            }
            for (int offset = 0; offset < order.size(); offset++) {
                final List<String> candidate = new ArrayList<>(order.size());
                for (int i = 0; i < order.size(); i++) {
                    candidate.add(order.get((offset + i) % order.size()));
                }
                if (best == null || less(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean less(List<String> a, List<String> b) {
        for (int i = 0; i < a.size() && i < b.size(); i++) {
            final int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) {
                return cmp < 0;
            }
        }
        return a.size() < b.size();
    }

    public static Line lineOf(List<Line> lines, UUID trainId) {
        for (final Line line : lines) {
            if (line.trains.contains(trainId)) {
                return line;
            }
        }
        return null;
    }

    public static final class BoardRow {
        public UUID trainId;
        public String train = "";
        public String scheduleTitle = "";
        public int ticks;

        public boolean present;

        public boolean imminent;

        public String blockedBy = "";

        public String destination = "";
        public final List<String> callingAt = new ArrayList<>();

        public String platform = "";

        public String station = "";

        public float lateBy = Float.NaN;

        public String cause = "";

        public Severity causeSeverity = Severity.INFO;

        public float speed;

        public float maxSpeed;

        public int carriages;
        public float length;

        public int mapColor = -1;

        public float progress;

        public boolean manual;

        public boolean terminates;

        public String eta() {
            if (this.present) {
                return "here";
            }
            if (!this.blockedBy.isEmpty()) {
                return "held";
            }
            return TrainIntel.eta(this.ticks);
        }
    }

    // annotate what create can't know, a 4 minute eta for a train with no driver is a lie
    public static List<BoardRow> board(RailSnapshot snapshot, RailSnapshot.Station station,
                                       int limit) {
        final List<BoardRow> rows = new ArrayList<>();
        if (station == null) {
            return rows;
        }
        collect(snapshot, station, rows);
        sortBoard(rows);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    public static List<BoardRow> board(RailSnapshot snapshot, List<RailSnapshot.Station> stations,
                                       int limit) {
        final List<BoardRow> rows = new ArrayList<>();
        if (stations == null || stations.isEmpty()) {
            return rows;
        }
        for (final RailSnapshot.Station station : stations) {
            collect(snapshot, station, rows);
        }
        sortBoard(rows);

        final List<BoardRow> unique = new ArrayList<>(rows.size());
        final Set<UUID> seen = new HashSet<>();
        for (final BoardRow row : rows) {
            if (row.trainId == null || seen.add(row.trainId)) {
                unique.add(row);
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return unique;
    }

    public static List<RailSnapshot.Station> stationsMatching(RailSnapshot snapshot, String filter) {
        final List<RailSnapshot.Station> out = new ArrayList<>();
        if (snapshot == null || filter == null || filter.isBlank()) {
            return out;
        }
        final Set<UUID> taken = new HashSet<>();
        for (final String raw : filter.split("[,;]")) {
            final String one = raw.trim();
            if (one.isEmpty()) {
                continue;
            }
            for (final RailSnapshot.Station s : snapshot.stations()) {
                if (RailSnapshot.matches(one, s.name) && taken.add(s.id)) {
                    out.add(s);
                }
            }
        }

        out.sort(Comparator
                .comparingInt((RailSnapshot.Station s) -> s.presentTrain != null ? 0
                        : s.imminentTrain != null ? 1 : 2)
                .thenComparing(s -> s.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static void collect(RailSnapshot snapshot, RailSnapshot.Station station,
                                List<BoardRow> rows) {
        for (final RailSnapshot.Arrival arrival : snapshot.arrivals()) {
            if (!RailSnapshot.matches(arrival.station, station.name)
                    && !arrival.station.equals(station.name)) {
                continue;
            }
            final BoardRow row = new BoardRow();
            row.trainId = arrival.trainId;
            row.train = arrival.trainName;
            row.scheduleTitle = arrival.scheduleTitle;
            row.ticks = arrival.ticks;
            row.station = station.name;
            row.present = arrival.trainId.equals(station.presentTrain);
            row.imminent = arrival.trainId.equals(station.imminentTrain);
            final RailSnapshot.TrainState train = snapshot.train(arrival.trainId);
            row.blockedBy = blockedReason(train);
            if (train != null) {
                row.lateBy = train.secondsLate();
                row.cause = cause(snapshot, train);
                row.causeSeverity = causeSeverity(train);
                row.speed = train.metresPerSecond();
                row.maxSpeed = Math.abs(train.maxSpeed) * TICKS_PER_SECOND;
                row.carriages = train.carriages;
                row.length = train.length;
                row.mapColor = train.mapColor;
                row.progress = train.legProgress();
                row.manual = train.manual;
                fillRoute(row, train, station);
            }
            rows.add(row);
        }
    }

    private static void sortBoard(List<BoardRow> rows) {
        rows.sort(Comparator
                .comparingInt((BoardRow r) -> r.present ? 0 : r.imminent ? 1 : 2)
                .thenComparingInt(r -> r.ticks < 0 ? Integer.MAX_VALUE : r.ticks)
                .thenComparing(r -> r.train));
    }

    public static String cause(RailSnapshot snapshot, RailSnapshot.TrainState t) {
        if (t == null) {
            return "";
        }
        if (t.derailed) {
            return "derailed";
        }
        if (!t.conductorOk) {
            return "no driver";
        }
        if (!t.trackOk) {
            return "track ends ahead";
        }
        if (!t.navigationOk) {
            return "no route to next stop";
        }
        if (t.schedulePaused) {
            return "schedule paused";
        }
        if (t.waitingForSignal) {
            final RailSnapshot.TrainState ahead = t.blockedByTrain == null || snapshot == null
                    ? null : snapshot.train(t.blockedByTrain);
            final String held = duration(t.ticksWaitingForSignal / TICKS_PER_SECOND);
            if (ahead != null) {
                return "behind " + ahead.name + " " + held;
            }
            if (!t.blockedAt.isEmpty()) {
                return "signal at " + t.blockedAt + " " + held;
            }
            return "signal ahead " + held;
        }
        if (t.manual) {
            return "under manual control";
        }
        if (!t.hasSchedule) {
            return "no schedule";
        }

        if (!t.waitingStatus.isEmpty() && !t.moving()) {
            return t.waitingStatus.toLowerCase(Locale.ROOT);
        }
        if (!t.moving() && t.phase == RailSnapshot.SchedulePhase.IN_TRANSIT) {
            return "stopped in section";
        }
        return "";
    }

    public static Severity causeSeverity(RailSnapshot.TrainState t) {
        if (t == null) {
            return Severity.INFO;
        }
        if (t.derailed || !t.conductorOk) {
            return Severity.CRITICAL;
        }
        if (!t.trackOk || !t.navigationOk) {
            return Severity.WARN;
        }
        if (t.waitingForSignal) {
            return t.ticksWaitingForSignal >= SIGNAL_HOLD_ALERT_TICKS
                    ? Severity.WARN : Severity.INFO;
        }
        return Severity.INFO;
    }

    private static void fillRoute(BoardRow row, RailSnapshot.TrainState t,
                                  RailSnapshot.Station station) {
        final int here = indexOfStop(t, station.name);
        if (here < 0) {
            return;
        }
        final int count = t.stops.size();
        for (int step = 1; step < count; step++) {
            final String stop = t.stops.get((here + step) % count);
            if (stop == null || stop.isEmpty() || RailSnapshot.matches(stop, station.name)) {
                break;
            }
            row.callingAt.add(stop);
            if (row.callingAt.size() >= 6) {
                break;
            }
        }
        if (row.callingAt.isEmpty()) {
            row.terminates = true;
            row.destination = station.name;
        } else {
            row.destination = row.callingAt.get(row.callingAt.size() - 1);
        }
    }

    private static int indexOfStop(RailSnapshot.TrainState t, String stationName) {
        for (int i = 0; i < t.stops.size(); i++) {
            final String stop = t.stops.get(i);
            if (stop != null && RailSnapshot.matches(stop, stationName)) {
                return i;
            }
        }
        return -1;
    }

    public static String lateness(float seconds) {
        if (Float.isNaN(seconds)) {
            return "";
        }
        if (Math.abs(seconds) < 12.0f) {
            return "on time";
        }
        return seconds > 0.0f ? duration(seconds) + " late" : duration(-seconds) + " early";
    }

    public static Severity latenessSeverity(float seconds) {
        if (Float.isNaN(seconds) || seconds < 12.0f) {
            return Severity.INFO;
        }
        return seconds > 60.0f ? Severity.CRITICAL : Severity.WARN;
    }

    private static String blockedReason(RailSnapshot.TrainState t) {
        if (t == null) {
            return "";
        }
        if (t.derailed) {
            return "derailed";
        }
        if (!t.conductorOk) {
            return "no driver";
        }
        if (!t.navigationOk) {
            return "no route";
        }
        if (!t.trackOk) {
            return "off track";
        }
        if (t.schedulePaused) {
            return "paused";
        }
        return "";
    }

    public enum Severity {
        INFO,

        WARN,

        CRITICAL
    }

    public static final class Alert {
        public final Severity severity;
        public final String subject;
        public final String detail;
        public final UUID trainId;

        public static Alert of(Severity severity, String subject, String detail, UUID trainId) {
            return new Alert(severity, subject, detail, trainId);
        }

        Alert(Severity severity, String subject, String detail, UUID trainId) {
            this.severity = severity;
            this.subject = subject;
            this.detail = detail;
            this.trainId = trainId;
        }
    }

    public static final class Stall {
        public final List<UUID> trains = new ArrayList<>();
        public final List<String> names = new ArrayList<>();

        public final Map<UUID, UUID> waitsOn = new LinkedHashMap<>();

        public final boolean circular;

        public int longestHold;

        public boolean causeUnhealthy;

        Stall(boolean circular) {
            this.circular = circular;
        }

        public UUID cause() {
            return this.trains.isEmpty() ? null : this.trains.get(this.trains.size() - 1);
        }
    }

    public static Stall worstStall(List<Stall> stalls) {
        Stall best = null;
        for (final Stall s : stalls) {
            if (best == null
                    || (s.circular && !best.circular)
                    || (s.circular == best.circular && s.longestHold > best.longestHold)) {
                best = s;
            }
        }
        return best;
    }

    public static List<Stall> stalls(RailSnapshot snapshot) {
        final Map<UUID, UUID> waitsOn = new LinkedHashMap<>();
        final Map<UUID, RailSnapshot.TrainState> byId = new LinkedHashMap<>();
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            byId.put(t.id, t);
        }
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            if (t.waitingForSignal && t.blockedByTrain != null
                    && t.ticksWaitingForSignal >= SIGNAL_HOLD_ALERT_TICKS
                    && byId.containsKey(t.blockedByTrain)
                    && !t.blockedByTrain.equals(t.id)) {
                waitsOn.put(t.id, t.blockedByTrain);
            }
        }

        final Map<UUID, Integer> state = new HashMap<>();
        final Set<UUID> inCycle = new LinkedHashSet<>();
        final List<List<UUID>> rings = new ArrayList<>();
        final List<UUID> walk = new ArrayList<>();
        for (final UUID start : waitsOn.keySet()) {
            if (state.containsKey(start)) {
                continue;
            }
            walk.clear();
            UUID at = start;
            while (at != null && !state.containsKey(at)) {
                state.put(at, 1);
                walk.add(at);
                at = waitsOn.get(at);
            }
            if (at != null && state.get(at) == 1) {
                final List<UUID> ring = new ArrayList<>(
                        walk.subList(walk.indexOf(at), walk.size()));
                rings.add(ring);
                inCycle.addAll(ring);
            }
            for (final UUID id : walk) {
                state.put(id, 2);
            }
        }

        final List<Stall> out = new ArrayList<>();
        for (final List<UUID> ring : rings) {
            if (ring.size() < 2) {
                continue;
            }
            final Stall stall = new Stall(true);
            fill(stall, ring, byId, waitsOn);
            out.add(stall);
        }

        final Map<UUID, List<UUID>> byCause = new LinkedHashMap<>();
        for (final UUID id : waitsOn.keySet()) {
            if (inCycle.contains(id)) {
                continue;
            }
            final UUID cause = terminus(id, waitsOn, inCycle);
            if (cause != null) {
                byCause.computeIfAbsent(cause, k -> new ArrayList<>()).add(id);
            }
        }
        for (final Map.Entry<UUID, List<UUID>> entry : byCause.entrySet()) {
            final RailSnapshot.TrainState cause = byId.get(entry.getKey());

            if (cause == null || stallReason(cause).isEmpty()) {
                continue;
            }
            final List<UUID> all = new ArrayList<>(entry.getValue());
            all.add(entry.getKey());
            final Stall stall = new Stall(false);
            fill(stall, all, byId, waitsOn);
            stall.causeUnhealthy = cause.unhealthy();
            out.add(stall);
        }
        return out;
    }

    private static UUID terminus(UUID from, Map<UUID, UUID> waitsOn, Set<UUID> inCycle) {
        UUID at = from;
        for (int guard = 0; guard < 4096; guard++) {
            final UUID next = waitsOn.get(at);
            if (next == null) {
                return at;
            }
            if (inCycle.contains(next)) {
                return null;
            }
            at = next;
        }
        return null;
    }

    private static void fill(Stall stall, List<UUID> ids,
                             Map<UUID, RailSnapshot.TrainState> byId, Map<UUID, UUID> waitsOn) {
        for (final UUID id : ids) {
            final RailSnapshot.TrainState t = byId.get(id);
            stall.trains.add(id);
            stall.names.add(t == null ? "a train" : t.name);
            if (t != null) {
                stall.longestHold = Math.max(stall.longestHold, t.ticksWaitingForSignal);
            }
        }

        for (final UUID id : stall.trains) {
            final UUID blocker = waitsOn.get(id);
            if (blocker != null && stall.trains.contains(blocker)) {
                stall.waitsOn.put(id, blocker);
            }
        }
    }

    private static String stallReason(RailSnapshot.TrainState t) {
        if (t.derailed) {
            return "it is derailed";
        }
        if (!t.conductorOk) {
            return "it has no driver";
        }
        if (!t.trackOk) {
            return "it has run out of track";
        }
        if (!t.navigationOk) {
            return "it has no path to its next stop";
        }
        if (t.hasSchedule && t.schedulePaused) {
            return "its schedule is paused";
        }
        if (!t.hasSchedule) {
            return "it has no schedule";
        }
        return "";
    }

    private static String chain(Stall stall) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stall.names.size() && i < 5; i++) {
            if (i > 0) {
                sb.append(" waits on ");
            }
            sb.append(stall.names.get(i));
        }
        if (stall.names.size() > 5) {
            sb.append(" +").append(stall.names.size() - 5);
        }
        return sb.toString();
    }

    public static List<Alert> alerts(RailSnapshot snapshot) {
        final List<Alert> alerts = new ArrayList<>();

        final Set<UUID> explained = new HashSet<>();
        for (final Stall stall : stalls(snapshot)) {
            final String held = duration(stall.longestHold / TICKS_PER_SECOND);
            if (stall.circular) {
                explained.addAll(stall.trains);
                alerts.add(new Alert(Severity.CRITICAL,
                        stall.trains.size() + " trains in a circular wait",
                        chain(stall) + ", and back again. Longest hold " + held
                                + ". This will not clear on its own: move one of them by hand.",
                        stall.trains.get(0)));
                continue;
            }
            final RailSnapshot.TrainState cause = snapshot.train(stall.cause());
            if (cause == null) {
                continue;
            }

            explained.addAll(stall.trains);
            explained.remove(cause.id);
            final int queued = stall.trains.size() - 1;
            alerts.add(new Alert(Severity.WARN, cause.name,
                    queued + (queued == 1 ? " train is" : " trains are") + " held behind it because "
                            + stallReason(cause) + ". Longest wait " + held + ".", cause.id));
        }

        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            if (t.derailed) {
                alerts.add(new Alert(Severity.CRITICAL, t.name,
                        "Derailed. It will not move again until it is relocated onto track.", t.id));
            }
            if (!t.conductorOk) {
                alerts.add(new Alert(Severity.CRITICAL, t.name,
                        "No driver in a seat facing the direction of travel.", t.id));
            }
            if (!t.navigationOk) {
                alerts.add(new Alert(Severity.WARN, t.name,
                        "No path to its next stop. Check the schedule's station filter.", t.id));
            }
            if (!t.trackOk) {
                alerts.add(new Alert(Severity.WARN, t.name,
                        "Ran out of track and stopped where the rail ends.", t.id));
            }
            if (t.waitingForSignal && t.ticksWaitingForSignal >= SIGNAL_HOLD_ALERT_TICKS
                    && !explained.contains(t.id)) {
                final String where = t.blockedAt.isEmpty() ? "" : " behind " + t.blockedAt;
                alerts.add(new Alert(Severity.WARN, t.name,
                        "Held at a signal" + where + " for "
                                + duration(t.ticksWaitingForSignal / TICKS_PER_SECOND)
                                + ". Nothing is occupying the block ahead that we can see.", t.id));
            }
            if (t.hasSchedule && t.schedulePaused) {
                alerts.add(new Alert(Severity.INFO, t.name,
                        "Schedule paused. It will stay where it is until resumed.", t.id));
            }

            if (t.fuelTicks > 0 && t.fuelTicks < 1200) {
                alerts.add(new Alert(Severity.INFO, t.name,
                        "Fuel runs out in " + duration(t.fuelTicks / TICKS_PER_SECOND) + ".", t.id));
            }
        }
        alerts.sort(Comparator.comparingInt((Alert a) -> -a.severity.ordinal())
                .thenComparing(a -> a.subject));
        return alerts;
    }

    public static final class Summary {
        public int trains;
        public int moving;
        public int held;
        public int docked;
        public int broken;
        public int stations;
        public int lines;

        public float averageSpeed;
        public float fastestSpeed;

        public int longestHold;
        public String longestHoldTrain = "";

        public UUID longestHoldTrainId;

        public float utilisation() {
            return this.trains == 0 ? 0.0f : this.moving / (float) this.trains;
        }
    }

    public static Summary summarise(RailSnapshot snapshot) {
        final Summary s = new Summary();
        s.stations = snapshot.stations().size();
        s.lines = lines(snapshot).size();

        float speedSum = 0.0f;
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            s.trains++;
            if (t.unhealthy()) {
                s.broken++;
            }
            if (t.waitingForSignal) {
                s.held++;
            } else if (t.docked()) {
                s.docked++;
            } else if (t.moving()) {
                s.moving++;
                speedSum += t.metresPerSecond();
            }
            s.fastestSpeed = Math.max(s.fastestSpeed, t.metresPerSecond());
            if (t.waitingForSignal && t.ticksWaitingForSignal > s.longestHold) {
                s.longestHold = t.ticksWaitingForSignal;
                s.longestHoldTrain = t.name;
                s.longestHoldTrainId = t.id;
            }
        }
        s.averageSpeed = s.moving == 0 ? 0.0f : speedSum / s.moving;
        return s;
    }

    public static String status(RailSnapshot.TrainState t) {
        if (t == null) {
            return "";
        }
        if (t.derailed) {
            return "derailed";
        }
        if (!t.conductorOk) {
            return "no driver";
        }
        if (t.waitingForSignal) {
            return "held at signal " + duration(t.ticksWaitingForSignal / TICKS_PER_SECOND);
        }
        if (t.schedulePaused) {
            return "schedule paused";
        }

        if (!t.waitingStatus.isEmpty()) {
            return t.waitingStatus.toLowerCase(Locale.ROOT);
        }
        if (t.docked()) {
            return "at platform";
        }
        if (!t.destinationName.isEmpty()) {
            return "to " + t.destinationName;
        }
        if (!t.hasSchedule) {
            return "no schedule";
        }
        return t.moving() ? "in transit" : "idle";
    }

    public static String route(RailSnapshot.TrainState t, int maxStops) {
        if (t == null || t.stops.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final String stop : t.stops) {
            if (stop == null || stop.isEmpty()) {
                continue;
            }
            if (shown > 0) {
                sb.append(" . ");
            }
            sb.append(stop);
            if (++shown >= maxStops) {
                break;
            }
        }
        if (shown == 0) {
            return "";
        }
        if (t.stops.size() > shown) {
            sb.append(" +").append(t.stops.size() - shown);
        }
        return sb.toString();
    }

    public static final class Projection {
        private float scale = 1.0f;
        private float offsetX;
        private float offsetY;
        private float centreX;
        private float centreZ;

        public float x(double worldX) {
            return this.offsetX + (float) (worldX - this.centreX) * this.scale;
        }

        public float y(double worldZ) {
            return this.offsetY + (float) (worldZ - this.centreZ) * this.scale;
        }

        public float scale() {
            return this.scale;
        }

        public double worldX(float screenX) {
            return this.centreX + (screenX - this.offsetX) / this.scale;
        }

        public double worldZ(float screenY) {
            return this.centreZ + (screenY - this.offsetY) / this.scale;
        }
    }

    public static Projection project(RailSnapshot snapshot, float x, float y, float w, float h,
                                     float zoom, float panX, float panZ) {
        final Projection p = new Projection();
        if (w <= 0.0f || h <= 0.0f) {
            return p;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final RailSnapshot.Station s : snapshot.stations()) {
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x);
            minZ = Math.min(minZ, s.z);
            maxZ = Math.max(maxZ, s.z);
        }
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            if (t.positionValid) {
                minX = Math.min(minX, t.x);
                maxX = Math.max(maxX, t.x);
                minZ = Math.min(minZ, t.z);
                maxZ = Math.max(maxZ, t.z);
            }
        }
        if (minX > maxX) {
            p.offsetX = x + w * 0.5f;
            p.offsetY = y + h * 0.5f;
            return p;
        }

        final double spanX = Math.max(maxX - minX, 16.0);
        final double spanZ = Math.max(maxZ - minZ, 16.0);
        final float margin = 0.88f;
        p.scale = (float) Math.min(w * margin / spanX, h * margin / spanZ) * Math.max(0.05f, zoom);
        p.centreX = (float) ((minX + maxX) * 0.5) - panX;
        p.centreZ = (float) ((minZ + maxZ) * 0.5) - panZ;
        p.offsetX = x + w * 0.5f;
        p.offsetY = y + h * 0.5f;
        return p;
    }

    public static float[] positionOf(RailSnapshot snapshot, RailSnapshot.TrainState t) {
        if (t.positionValid) {
            return new float[]{(float) t.x, (float) t.z};
        }
        final RailSnapshot.Station to = snapshot.station(t.destinationStation);
        final RailSnapshot.Station from = snapshot.station(t.currentStation);
        if (to == null) {
            return from == null ? null : new float[]{(float) from.x, (float) from.z};
        }
        if (from == null) {
            return new float[]{(float) to.x, (float) to.z};
        }
        final float f = t.legProgress();
        return new float[]{
                (float) (from.x + (to.x - from.x) * f),
                (float) (from.z + (to.z - from.z) * f)};
    }

    public static Map<UUID, RailSnapshot.Station> index(RailSnapshot snapshot) {
        final Map<UUID, RailSnapshot.Station> byId = new HashMap<>();
        for (final RailSnapshot.Station s : snapshot.stations()) {
            byId.put(s.id, s);
        }
        return byId;
    }
}
