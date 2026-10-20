package com.createstats.inventory.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

// the link announces itself instead of us scanning a cube every second
public final class FeederBook {
    public static final int MAX_LINKS = 4;

    public record Entry(String dimension, long pos, long announced) {
        public boolean same(String dim, long p) {
            return this.pos == p && Objects.equals(this.dimension, dim);
        }
    }

    public enum State {
        LOADED,

        UNLOADED,

        GONE
    }

    private final List<Entry> entries = new ArrayList<>();

    public boolean announce(String dimension, long pos, long gameTime) {
        int found = -1;
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).same(dimension, pos)) {
                found = i;
                break;
            }
        }
        final boolean reordered = found != 0;
        if (found >= 0) {
            this.entries.remove(found);
        }
        this.entries.add(0, new Entry(dimension, pos, gameTime));
        while (this.entries.size() > MAX_LINKS) {
            this.entries.remove(this.entries.size() - 1);
        }
        return reordered;
    }

    public boolean drop(String dimension, long pos) {
        return this.entries.removeIf(e -> e.same(dimension, pos));
    }

    public Optional<Choice> choose(Function<Entry, State> world) {
        Entry loaded = null;
        Entry kept = null;
        final List<Entry> gone = new ArrayList<>();
        for (Entry e : this.entries) {
            final State s = world.apply(e);
            if (s == State.GONE) {
                gone.add(e);
            } else if (s == State.LOADED && loaded == null) {
                loaded = e;
            } else if (s == State.UNLOADED && kept == null) {
                kept = e;
            }
        }
        this.entries.removeAll(gone);
        if (loaded != null) {
            return Optional.of(new Choice(loaded, true));
        }
        return kept != null ? Optional.of(new Choice(kept, false)) : Optional.empty();
    }

    public record Choice(Entry entry, boolean loaded) {
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public void clear() {
        this.entries.clear();
    }

    public void restore(List<Entry> saved) {
        this.entries.clear();
        for (Entry e : saved) {
            if (this.entries.size() >= MAX_LINKS) {
                break;
            }
            if (this.entries.stream().noneMatch(x -> x.same(e.dimension(), e.pos()))) {
                this.entries.add(e);
            }
        }
    }
}
