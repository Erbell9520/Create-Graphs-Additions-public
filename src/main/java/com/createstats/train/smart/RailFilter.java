package com.createstats.train.smart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RailFilter {
    private static Path file() {
        try {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("create_stats").resolve("train_filters.txt");
        } catch (final RuntimeException | LinkageError ignored) {
            return java.nio.file.Paths.get("config", "create_stats", "train_filters.txt");
        }
    }

    private static final int MAX_RULES = 256;

    private static RailFilter instance;

    public static final class Rule {
        public final String pattern;
        public boolean hidden;

        public Rule(String pattern, boolean hidden) {
            this.pattern = pattern;
            this.hidden = hidden;
        }

        public String label() {
            final String cleaned = this.pattern.replace("*", "").trim();
            return cleaned.isEmpty() ? this.pattern : cleaned;
        }
    }

    private final List<Rule> stationRules = new ArrayList<>();
    private final List<Rule> trainRules = new ArrayList<>();

    private boolean hideDocked;

    private boolean hideParked;

    private boolean onlyProblems;

    private String dimension = "";

    private int revision;

    private RailSnapshot memoInput;
    private int memoRevision = -1;
    private RailSnapshot memoOutput;

    private boolean loaded;
    private boolean dirty;

    public static RailFilter get() {
        if (instance == null) {
            instance = new RailFilter();
            instance.load();
        }
        return instance;
    }

    public List<Rule> stationRules() {
        return this.stationRules;
    }

    public List<Rule> trainRules() {
        return this.trainRules;
    }

    public Rule offerStation(String pattern) {
        return offer(this.stationRules, pattern);
    }

    public Rule offerTrain(String pattern) {
        return offer(this.trainRules, pattern);
    }

    private Rule offer(List<Rule> into, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        final String cleaned = pattern.trim();
        for (final Rule rule : into) {
            if (rule.pattern.equalsIgnoreCase(cleaned)) {
                return rule;
            }
        }
        if (into.size() >= MAX_RULES) {
            return null;
        }
        final Rule rule = new Rule(cleaned, false);
        into.add(rule);
        return rule;
    }

    public void toggle(Rule rule) {
        if (rule != null) {
            rule.hidden = !rule.hidden;
            changed();
        }
    }

    public void remove(Rule rule) {
        if (this.stationRules.remove(rule) || this.trainRules.remove(rule)) {
            changed();
        }
    }

    public void showEverything() {
        for (final Rule rule : this.stationRules) {
            rule.hidden = false;
        }
        for (final Rule rule : this.trainRules) {
            rule.hidden = false;
        }
        this.hideDocked = false;
        this.hideParked = false;
        this.onlyProblems = false;
        this.dimension = "";
        changed();
    }

    public int hiddenCount() {
        int n = 0;
        for (final Rule rule : this.stationRules) {
            if (rule.hidden) {
                n++;
            }
        }
        for (final Rule rule : this.trainRules) {
            if (rule.hidden) {
                n++;
            }
        }
        if (this.hideDocked) {
            n++;
        }
        if (this.hideParked) {
            n++;
        }
        if (this.onlyProblems) {
            n++;
        }
        if (!this.dimension.isEmpty()) {
            n++;
        }
        return n;
    }

    public boolean active() {
        return hiddenCount() > 0;
    }

    public int revision() {
        return this.revision;
    }

    public boolean hideDocked() {
        return this.hideDocked;
    }

    public void setHideDocked(boolean on) {
        this.hideDocked = on;
        changed();
    }

    public boolean hideParked() {
        return this.hideParked;
    }

    public void setHideParked(boolean on) {
        this.hideParked = on;
        changed();
    }

    public boolean onlyProblems() {
        return this.onlyProblems;
    }

    public void setOnlyProblems(boolean on) {
        this.onlyProblems = on;
        changed();
    }

    public String dimension() {
        return this.dimension;
    }

    public void setDimension(String id) {
        this.dimension = id == null ? "" : id;
        changed();
    }

    public RailSnapshot apply(RailSnapshot in) {
        if (in == null) {
            return RailSnapshot.EMPTY;
        }
        if (!active()) {
            return in;
        }
        if (in == this.memoInput && this.revision == this.memoRevision && this.memoOutput != null) {
            return this.memoOutput;
        }
        final RailSnapshot base = this.dimension.isEmpty() ? in : in.inDimension(this.dimension);
        final RailSnapshot out = new RailSnapshot();
        out.serverTick = base.serverTick;
        out.topologyChecksum = base.topologyChecksum;
        out.tracksTruncated = base.tracksTruncated;

        final Set<java.util.UUID> keptStations = new LinkedHashSet<>();
        for (final RailSnapshot.Station s : base.stations()) {
            if (!hides(this.stationRules, s.name)) {
                out.stations().add(s);
                keptStations.add(s.id);
            }
        }
        for (final RailSnapshot.Link l : base.links()) {
            if (keptStations.contains(l.from) && keptStations.contains(l.to)) {
                out.links().add(l);
            }
        }

        out.tracks().addAll(base.tracks());

        final Set<java.util.UUID> keptTrains = new LinkedHashSet<>();
        for (final RailSnapshot.TrainState t : base.trains()) {
            if (keepTrain(t)) {
                out.trains().add(t);
                keptTrains.add(t.id);
            }
        }
        for (final RailSnapshot.Arrival a : base.arrivals()) {
            if (keptTrains.contains(a.trainId) && !hides(this.stationRules, a.station)) {
                out.arrivals().add(a);
            }
        }

        this.memoInput = in;
        this.memoRevision = this.revision;
        this.memoOutput = out;
        return out;
    }

    private boolean keepTrain(RailSnapshot.TrainState t) {
        if (hides(this.trainRules, t.name) || hides(this.trainRules, t.scheduleTitle)) {
            return false;
        }
        if (this.onlyProblems) {
            return t.unhealthy() || t.waitingForSignal || t.schedulePaused;
        }
        if (this.hideDocked && t.docked()) {
            return false;
        }
        return !this.hideParked || t.hasSchedule;
    }

    private static boolean hides(List<Rule> rules, String name) {
        if (name == null) {
            return false;
        }
        for (final Rule rule : rules) {
            if (rule.hidden && matches(rule.pattern, name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String pattern, String name) {
        if (pattern == null || name == null) {
            return false;
        }
        final String p = pattern.toLowerCase(Locale.ROOT).trim();
        final String n = name.toLowerCase(Locale.ROOT).trim();
        if (p.isEmpty()) {
            return false;
        }
        if (p.equals(n)) {
            return true;
        }
        if (p.startsWith("*") && p.endsWith("*") && p.length() > 2) {
            return n.contains(p.substring(1, p.length() - 1));
        }
        if (p.startsWith("*")) {
            return n.endsWith(p.substring(1));
        }
        return p.endsWith("*") && n.startsWith(p.substring(0, p.length() - 1));
    }

    private void changed() {
        this.revision++;
        this.dirty = true;
        this.memoOutput = null;
    }

    public void flush() {
        if (!this.dirty) {
            return;
        }
        this.dirty = false;
        try {
            final Path file = file();
            Files.createDirectories(file.getParent());

            final List<String> lines = new ArrayList<>();
            for (final Rule rule : this.stationRules) {
                if (rule.hidden) {
                    lines.add("station\t1\t" + rule.pattern);
                }
            }
            for (final Rule rule : this.trainRules) {
                if (rule.hidden) {
                    lines.add("train\t1\t" + rule.pattern);
                }
            }
            lines.add("flags\t" + (this.hideDocked ? 1 : 0) + '\t' + (this.hideParked ? 1 : 0)
                    + '\t' + (this.onlyProblems ? 1 : 0));
            if (!this.dimension.isEmpty()) {
                lines.add("dimension\t" + this.dimension);
            }
            Files.write(file, lines);
        } catch (final IOException | RuntimeException ignored) {
        }
    }

    private void load() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        try {
            final Path file = file();
            if (!Files.isRegularFile(file)) {
                return;
            }
            for (final String line : Files.readAllLines(file)) {
                final String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }
                switch (parts[0]) {
                    case "station" -> {
                        if (parts.length >= 3) {
                            this.stationRules.add(new Rule(parts[2], "1".equals(parts[1])));
                        }
                    }
                    case "train" -> {
                        if (parts.length >= 3) {
                            this.trainRules.add(new Rule(parts[2], "1".equals(parts[1])));
                        }
                    }
                    case "flags" -> {
                        this.hideDocked = "1".equals(parts[1]);
                        this.hideParked = parts.length > 2 && "1".equals(parts[2]);
                        this.onlyProblems = parts.length > 3 && "1".equals(parts[3]);
                    }
                    case "dimension" -> this.dimension = parts[1];
                    default -> {
                    }
                }
                if (this.stationRules.size() + this.trainRules.size() > MAX_RULES) {
                    return;
                }
            }
        } catch (final IOException | RuntimeException ignored) {
        }
    }

    public static void reset() {
        if (instance != null) {
            instance.flush();
        }
        instance = null;
    }
}
