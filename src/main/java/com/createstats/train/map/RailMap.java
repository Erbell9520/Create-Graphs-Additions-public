package com.createstats.train.map;

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

import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

public final class RailMap {
    public static final double COMPLEX_RADIUS = 48.0;

    private static final int MIN_STEM = 3;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final Map<UUID, Integer> nodeByStation = new HashMap<>();

    private RailSnapshot snapshot = RailSnapshot.EMPTY;

    private LineChainage[] chainageCache;

    public static final class Node {
        public UUID id;
        public String name = "";
        public String dimension = "";

        public double x;
        public double z;
        public double y;

        public final List<RailSnapshot.Station> members = new ArrayList<>();

        public String groupKey = "";

        public final List<Integer> lines = new ArrayList<>();

        public boolean terminus;

        public boolean pinned;
        public float pinX;
        public float pinY;

        public boolean isGroup() {
            return this.members.size() > 1;
        }

        public boolean isInterchange() {
            return this.lines.size() > 1;
        }

        public UUID presentTrain() {
            for (final RailSnapshot.Station s : this.members) {
                if (s.presentTrain != null) {
                    return s.presentTrain;
                }
            }
            return null;
        }

        public UUID imminentTrain() {
            for (final RailSnapshot.Station s : this.members) {
                if (s.imminentTrain != null) {
                    return s.imminentTrain;
                }
            }
            return null;
        }

        public boolean assembling() {
            for (final RailSnapshot.Station s : this.members) {
                if (s.assembling) {
                    return true;
                }
            }
            return false;
        }

        public int packagePorts() {
            int n = 0;
            for (final RailSnapshot.Station s : this.members) {
                n += s.packagePorts;
            }
            return n;
        }

        public int occupiedPlatforms() {
            int n = 0;
            for (final RailSnapshot.Station s : this.members) {
                if (s.presentTrain != null) {
                    n++;
                }
            }
            return n;
        }
    }

    public static final class Edge {
        public int a;
        public int b;

        public final List<Integer> lines = new ArrayList<>();

        public double length;

        public float[] path = new float[0];

        public boolean surveyed() {
            return this.path.length >= 4;
        }

        public boolean inferred;

        int other(int node) {
            return node == this.a ? this.b : this.a;
        }
    }

    public static final class Line {
        public static final int TIER_RUNNING = 0;

        public static final int TIER_IDLE = 1;

        public static final int TIER_DORMANT = 2;

        public String key = "";
        public String title = "";
        public int colorIndex;

        public String disambiguator = "";

        public final List<Integer> nodes = new ArrayList<>();
        public final List<UUID> trains = new ArrayList<>();

        public boolean loop;

        public String label(RailMap map) {
            if (!this.title.isEmpty()) {
                return this.disambiguator.isEmpty()
                        ? this.title : this.title + "  .  " + this.disambiguator;
            }
            if (this.nodes.isEmpty()) {
                return "Unscheduled";
            }
            final String first = map.node(this.nodes.get(0)).name;
            if (this.nodes.size() == 1) {
                return first;
            }
            if (this.loop) {
                return first + " circular";
            }
            return first + " to " + map.node(this.nodes.get(this.nodes.size() - 1)).name;
        }

        public int activeTrains(RailSnapshot snap) {
            int n = 0;
            for (final UUID id : this.trains) {
                final RailSnapshot.TrainState t = snap.train(id);
                if (t != null && t.moving()) {
                    n++;
                }
            }
            return n;
        }

        public int tier(RailSnapshot snap) {
            boolean live = false;
            for (final UUID id : this.trains) {
                final RailSnapshot.TrainState t = snap.train(id);
                if (t == null) {
                    continue;
                }
                if (t.moving()) {
                    return TIER_RUNNING;
                }

                live |= t.hasSchedule && !t.schedulePaused && !t.unhealthy();
            }
            return live ? TIER_IDLE : TIER_DORMANT;
        }
    }

    public List<Node> nodes() {
        return this.nodes;
    }

    public List<Edge> edges() {
        return this.edges;
    }

    public List<Line> lines() {
        return this.lines;
    }

    public RailSnapshot snapshot() {
        return this.snapshot;
    }

    public Node node(int index) {
        return index >= 0 && index < this.nodes.size() ? this.nodes.get(index) : null;
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public LineChainage chainage(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= this.lines.size()) {
            return LineChainage.EMPTY;
        }
        if (this.chainageCache == null || this.chainageCache.length != this.lines.size()) {
            this.chainageCache = new LineChainage[this.lines.size()];
        }
        if (this.chainageCache[lineIndex] == null) {
            this.chainageCache[lineIndex] = LineChainage.of(this, lineIndex);
        }
        return this.chainageCache[lineIndex];
    }

    public int nodeOfStation(UUID stationId) {
        final Integer i = this.nodeByStation.get(stationId);
        return i == null ? -1 : i;
    }

    public int nodeOfFilter(String filter, String preferredDimension) {
        final RailSnapshot.Station s = this.snapshot.resolveFilter(filter, preferredDimension);
        return s == null ? -1 : nodeOfStation(s.id);
    }

    public int originNodeOf(RailSnapshot.TrainState t) {
        if (t == null || t.stops.isEmpty()) {
            return -1;
        }
        final int count = t.stops.size();
        final int previous = Math.floorMod(t.currentEntry - 1, count);
        return nodeOfFilter(t.stops.get(previous), t.dimension);
    }

    public Line lineOf(UUID trainId) {
        for (final Line l : this.lines) {
            if (l.trains.contains(trainId)) {
                return l;
            }
        }
        return null;
    }

    public int lineIndexOf(UUID trainId) {
        for (int i = 0; i < this.lines.size(); i++) {
            if (this.lines.get(i).trains.contains(trainId)) {
                return i;
            }
        }
        return -1;
    }

    public Edge edgeBetween(int a, int b) {
        if (a < 0 || b < 0 || a == b) {
            return null;
        }
        for (final Edge e : this.edges) {
            if ((e.a == a && e.b == b) || (e.a == b && e.b == a)) {
                return e;
            }
        }
        return null;
    }

    public List<Edge> edgesAt(int node) {
        final List<Edge> out = new ArrayList<>();
        for (final Edge e : this.edges) {
            if (e.a == node || e.b == node) {
                out.add(e);
            }
        }
        return out;
    }

    public static RailMap build(RailSnapshot snapshot, List<String> groupPatterns,
                                boolean autoGroup, Set<String> expanded) {
        final RailMap map = new RailMap();
        if (snapshot == null) {
            return map;
        }
        map.snapshot = snapshot;
        map.buildNodes(groupPatterns, autoGroup, expanded);
        map.buildLines();
        map.buildEdges();
        map.markTermini();
        return map;
    }

    public static RailMap flat(RailSnapshot snapshot) {
        return build(snapshot, List.of(), false, Set.of());
    }

    // roster signature, create's checksum covers track and not trains
    public static int rosterSignature(RailSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        int h = snapshot.topologyChecksum * 31 + snapshot.stations().size();

        int stationBits = 0;
        for (final RailSnapshot.Station s : snapshot.stations()) {
            int sh = s.id == null ? 0 : s.id.hashCode();
            sh = sh * 31 + (s.name == null ? 0 : s.name.hashCode());
            sh = sh * 31 + (s.assembling ? 1 : 0);
            stationBits += sh;
        }
        h = h * 31 + stationBits;
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            h = h * 31 + (t.id == null ? 0 : t.id.hashCode());
            h = h * 31 + String.join("|", t.stops).hashCode();
        }
        return h;
    }

    public void refresh(RailSnapshot live) {
        if (live == null || live == this.snapshot) {
            return;
        }
        this.snapshot = live;
        for (final Node n : this.nodes) {
            for (int i = 0; i < n.members.size(); i++) {
                final RailSnapshot.Station fresh = live.station(n.members.get(i).id);
                if (fresh != null) {
                    n.members.set(i, fresh);
                }
            }
        }
    }

    private void buildNodes(List<String> groupPatterns, boolean autoGroup, Set<String> expanded) {
        final List<RailSnapshot.Station> stations = this.snapshot.stations();
        if (stations.isEmpty()) {
            return;
        }
        final Set<String> open = expanded == null ? Set.of() : expanded;

        final Map<String, List<RailSnapshot.Station>> groups = new LinkedHashMap<>();
        final Set<UUID> claimed = new HashSet<>();

        if (groupPatterns != null) {
            for (final String pattern : groupPatterns) {
                if (pattern == null || pattern.isBlank() || open.contains(pattern)) {
                    continue;
                }
                final List<RailSnapshot.Station> members = new ArrayList<>();
                for (final RailSnapshot.Station s : stations) {
                    if (!claimed.contains(s.id) && RailSnapshot.matches(pattern, s.name)) {
                        members.add(s);
                    }
                }

                if (members.size() > 1) {
                    for (final RailSnapshot.Station s : members) {
                        claimed.add(s.id);
                    }
                    groups.put(pattern, members);
                }
            }
        }

        if (autoGroup) {
            for (final Map.Entry<String, List<RailSnapshot.Station>> e
                    : autoComplexes(stations, claimed).entrySet()) {
                if (open.contains(e.getKey())) {
                    continue;
                }
                for (final RailSnapshot.Station s : e.getValue()) {
                    claimed.add(s.id);
                }
                groups.put(e.getKey(), e.getValue());
            }
        }

        for (final Map.Entry<String, List<RailSnapshot.Station>> e : groups.entrySet()) {
            addNode(e.getKey(), e.getValue());
        }
        for (final RailSnapshot.Station s : stations) {
            if (!claimed.contains(s.id)) {
                addNode("", List.of(s));
            }
        }
    }

    private static Map<String, List<RailSnapshot.Station>> autoComplexes(
            List<RailSnapshot.Station> stations, Set<UUID> claimed) {
        final Map<String, List<RailSnapshot.Station>> byStem = new LinkedHashMap<>();
        for (final RailSnapshot.Station s : stations) {
            if (claimed.contains(s.id)) {
                continue;
            }
            final String stem = stem(s.name);
            if (stem.length() >= MIN_STEM) {
                byStem.computeIfAbsent(stem, k -> new ArrayList<>()).add(s);
            }
        }

        final Map<String, List<RailSnapshot.Station>> out = new LinkedHashMap<>();
        for (final Map.Entry<String, List<RailSnapshot.Station>> e : byStem.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }

            for (final List<RailSnapshot.Station> cluster : clusterByProximity(e.getValue())) {
                if (cluster.size() > 1) {
                    final String key = out.containsKey(e.getKey())
                            ? e.getKey() + " " + nearestName(cluster) : e.getKey();
                    out.put(key, cluster);
                }
            }
        }
        return out;
    }

    private static List<List<RailSnapshot.Station>> clusterByProximity(
            List<RailSnapshot.Station> candidates) {
        final List<List<RailSnapshot.Station>> clusters = new ArrayList<>();
        final boolean[] used = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) {
                continue;
            }
            final List<RailSnapshot.Station> cluster = new ArrayList<>();
            cluster.add(candidates.get(i));
            used[i] = true;

            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = 0; j < candidates.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    for (final RailSnapshot.Station in : cluster) {
                        if (near(in, candidates.get(j))) {
                            cluster.add(candidates.get(j));
                            used[j] = true;
                            grew = true;
                            break;
                        }
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static boolean near(RailSnapshot.Station a, RailSnapshot.Station b) {
        if (!a.dimension.equals(b.dimension)) {
            return false;
        }
        final double dx = a.x - b.x;
        final double dz = a.z - b.z;
        return dx * dx + dz * dz <= COMPLEX_RADIUS * COMPLEX_RADIUS;
    }

    static String stem(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim();

        final int bracket = s.lastIndexOf('(');
        final int square = s.lastIndexOf('[');
        final int cut = Math.max(bracket, square);
        if (cut > 0) {
            s = s.substring(0, cut).trim();
        }

        int end = s.length();
        while (end > 0 && Character.isDigit(s.charAt(end - 1))) {
            end--;
        }
        if (end == s.length() && end > 1 && Character.isLetter(s.charAt(end - 1))
                && !Character.isLetterOrDigit(s.charAt(end - 2))) {
            end--;
        }
        s = s.substring(0, end);

        while (!s.isEmpty()) {
            final char last = s.charAt(s.length() - 1);
            if (last == ' ' || last == '-' || last == '_' || last == '#' || last == '.') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String nearestName(List<RailSnapshot.Station> cluster) {
        return cluster.isEmpty() ? "" : cluster.get(0).name;
    }

    private void addNode(String groupKey, List<RailSnapshot.Station> members) {
        final Node n = new Node();
        n.groupKey = groupKey;
        n.members.addAll(members);

        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        for (final RailSnapshot.Station s : members) {
            sx += s.x;
            sy += s.y;
            sz += s.z;
        }
        n.x = sx / members.size();
        n.y = sy / members.size();
        n.z = sz / members.size();
        n.dimension = members.get(0).dimension;
        n.name = members.size() == 1 ? members.get(0).name : displayName(groupKey, members);

        n.id = members.size() == 1
                ? members.get(0).id
                : UUID.nameUUIDFromBytes(("group:" + groupKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        final int index = this.nodes.size();
        this.nodes.add(n);
        for (final RailSnapshot.Station s : members) {
            this.nodeByStation.put(s.id, index);
        }
    }

    private static String displayName(String groupKey, List<RailSnapshot.Station> members) {
        String prefix = members.get(0).name;
        for (final RailSnapshot.Station s : members) {
            prefix = commonPrefix(prefix, s.name);
            if (prefix.isEmpty()) {
                break;
            }
        }
        prefix = prefix.trim();
        while (!prefix.isEmpty()) {
            final char last = prefix.charAt(prefix.length() - 1);
            if (last == ' ' || last == '-' || last == '_' || last == '#' || last == '.') {
                prefix = prefix.substring(0, prefix.length() - 1);
            } else {
                break;
            }
        }
        if (prefix.length() >= MIN_STEM) {
            return prefix;
        }
        final String cleaned = groupKey.replace("*", "").trim();
        return cleaned.isEmpty() ? members.get(0).name : cleaned;
    }

    private static String commonPrefix(String a, String b) {
        final int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) {
            i++;
        }
        return a.substring(0, i);
    }

    private void buildLines() {
        for (final TrainIntel.Line source : TrainIntel.lines(this.snapshot)) {
            final Line line = new Line();
            line.key = source.key;
            line.title = source.title;
            line.colorIndex = source.colorIndex;
            line.trains.addAll(source.trains);

            final String dimension = preferredDimension(source.trains);
            int previous = -1;
            for (final String stop : source.stops) {
                final int node = nodeOfFilter(stop, dimension);
                if (node < 0 || node == previous) {
                    continue;
                }
                line.nodes.add(node);
                previous = node;
            }

            if (line.nodes.size() > 2
                    && line.nodes.get(0).equals(line.nodes.get(line.nodes.size() - 1))) {
                line.nodes.remove(line.nodes.size() - 1);
                line.loop = true;
            } else if (line.nodes.size() > 2 && !source.stops.isEmpty()) {
                line.loop = true;
            }

            this.lines.add(line);
        }

        disambiguateTitles();

        for (int i = 0; i < this.lines.size(); i++) {
            for (final int node : this.lines.get(i).nodes) {
                final Node n = node(node);
                if (n != null && !n.lines.contains(i)) {
                    n.lines.add(i);
                }
            }
        }
    }

    private void disambiguateTitles() {
        final Map<String, List<Line>> byTitle = new HashMap<>();
        for (final Line line : this.lines) {
            if (!line.title.isEmpty()) {
                byTitle.computeIfAbsent(line.title, k -> new ArrayList<>()).add(line);
            }
        }
        for (final List<Line> group : byTitle.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (final Line line : group) {
                line.disambiguator = distinguish(line, group);
            }
        }
    }

    private String distinguish(Line line, List<Line> group) {
        final Set<Integer> elsewhere = new HashSet<>();
        for (final Line other : group) {
            if (other != line) {
                elsewhere.addAll(other.nodes);
            }
        }
        for (final int node : line.nodes) {
            if (elsewhere.contains(node)) {
                continue;
            }
            final Node n = node(node);
            if (n != null && !n.name.isEmpty()) {
                return "via " + n.name;
            }
        }
        return line.nodes.size() + (line.nodes.size() == 1 ? " stop" : " stops");
    }

    private String preferredDimension(List<UUID> trains) {
        final Map<String, Integer> counts = new HashMap<>();
        for (final UUID id : trains) {
            final RailSnapshot.TrainState t = this.snapshot.train(id);
            if (t != null && !t.dimension.isEmpty()) {
                counts.merge(t.dimension, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private void buildEdges() {
        final Map<Long, Edge> byPair = new LinkedHashMap<>();

        for (final RailSnapshot.Link link : this.snapshot.links()) {
            final int a = nodeOfStation(link.from);
            final int b = nodeOfStation(link.to);
            if (a < 0 || b < 0 || a == b) {
                continue;
            }
            final Edge edge = byPair.computeIfAbsent(pairKey(a, b), k -> {
                final Edge e = new Edge();
                e.a = Math.min(a, b);
                e.b = Math.max(a, b);
                return e;
            });

            if (!edge.surveyed() || (link.length > 0.0 && link.length < edge.length)) {
                if (link.hasPath()) {
                    edge.path = link.path;
                }
                edge.length = link.length;
            }
        }

        for (final Line line : this.lines) {
            final int count = line.nodes.size();
            final int last = line.loop ? count : count - 1;
            for (int i = 0; i < last; i++) {
                final int a = line.nodes.get(i);
                final int b = line.nodes.get((i + 1) % count);
                if (a == b) {
                    continue;
                }
                final long key = pairKey(a, b);
                Edge edge = byPair.get(key);
                if (edge == null) {
                    edge = new Edge();
                    edge.a = Math.min(a, b);
                    edge.b = Math.max(a, b);
                    edge.inferred = true;
                    byPair.put(key, edge);
                }
            }
        }

        this.edges.addAll(byPair.values());

        for (int i = 0; i < this.lines.size(); i++) {
            final Line line = this.lines.get(i);
            final int count = line.nodes.size();
            final int last = line.loop ? count : count - 1;
            for (int j = 0; j < last; j++) {
                final int a = line.nodes.get(j);
                final int b = line.nodes.get((j + 1) % count);
                if (a == b) {
                    continue;
                }
                final Edge edge = byPair.get(pairKey(a, b));
                if (edge != null && !edge.lines.contains(i)) {
                    edge.lines.add(i);
                }
            }
        }
        for (final Edge e : this.edges) {
            e.lines.sort(Comparator.naturalOrder());
        }
    }

    private static long pairKey(int a, int b) {
        final int lo = Math.min(a, b);
        final int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private void markTermini() {
        for (final Line line : this.lines) {
            if (line.loop || line.nodes.size() < 2) {
                continue;
            }
            final Node first = node(line.nodes.get(0));
            final Node last = node(line.nodes.get(line.nodes.size() - 1));
            if (first != null) {
                first.terminus = true;
            }
            if (last != null) {
                last.terminus = true;
            }
        }
    }

    public Set<String> dimensions() {
        final Set<String> out = new LinkedHashSet<>();
        for (final Node n : this.nodes) {
            if (!n.dimension.isEmpty()) {
                out.add(n.dimension);
            }
        }
        return out;
    }

    public List<Node> groups() {
        final List<Node> out = new ArrayList<>();
        for (final Node n : this.nodes) {
            if (n.isGroup()) {
                out.add(n);
            }
        }
        out.sort(Comparator.comparing(n -> n.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    public static List<String> suggestGroups(RailSnapshot snapshot, List<String> existing) {
        final RailMap probe = build(snapshot, List.of(), true, Set.of());
        final List<String> out = new ArrayList<>();
        for (final Node n : probe.nodes) {
            if (!n.isGroup()) {
                continue;
            }
            final String pattern = n.name + " *";
            if (existing == null || !existing.contains(pattern)) {
                out.add(pattern);
            }
        }
        return out;
    }
}
