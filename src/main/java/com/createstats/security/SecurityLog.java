package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class SecurityLog {
    public static final int CAPACITY = 200;

    public static final int SENT = 60;

    // writeUtf(s, max) throws instead of truncating, mid packet. cap it here
    public static final int MAX_WHO = 32;
    public static final int MAX_TARGET = 64;
    public static final int MAX_DETAIL = 64;

    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public enum Verdict {
        PASSED,

        DENIED,

        CHANGED,

        FAULT;

        private static final Verdict[] ORDER = values();

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Verdict byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : PASSED;
        }
    }

    public record Event(long gameTime, String who, String target, String detail, Verdict verdict) {
    }

    private final Deque<Event> events = new ArrayDeque<>();

    private Runnable onChange = () -> {
    };

    public void onChange(Runnable callback) {
        this.onChange = callback == null ? () -> {
        } : callback;
    }

    public void add(Event event) {
        if (event == null) {
            return;
        }
        this.events.addFirst(bounded(event));
        while (this.events.size() > CAPACITY) {
            this.events.removeLast();
        }
        this.onChange.run();
    }

    public void add(long gameTime, String who, String target, String detail, Verdict verdict) {
        add(new Event(gameTime, who, target, detail, verdict));
    }

    private static Event bounded(Event e) {
        return new Event(e.gameTime(), clip(e.who(), MAX_WHO), clip(e.target(), MAX_TARGET),
                clip(e.detail(), MAX_DETAIL), e.verdict());
    }

    public void access(long gameTime, String who, String target, boolean owner, int held,
                       int required, boolean passed) {
        final String detail = owner
                ? "owner bypass"
                : held + (passed ? " >= " : " < ") + required;
        add(gameTime, who, target, detail, passed ? Verdict.PASSED : Verdict.DENIED);
    }

    public List<Event> recent(int limit) {
        final List<Event> out = new ArrayList<>(Math.min(limit, this.events.size()));
        for (Event e : this.events) {
            if (out.size() >= limit) {
                break;
            }
            out.add(e);
        }
        return out;
    }

    public int size() {
        return this.events.size();
    }

    public void clear() {
        if (!this.events.isEmpty()) {
            this.events.clear();
            this.onChange.run();
        }
    }

    public ListTag save() {
        final ListTag list = new ListTag();
        for (Event e : this.events) {
            final CompoundTag row = new CompoundTag();
            row.putLong("T", e.gameTime());
            row.putString("W", e.who());
            row.putString("D", e.target());
            row.putString("X", e.detail());
            row.putInt("V", e.verdict().ordinal());
            list.add(row);
        }
        return list;
    }

    public void load(ListTag list) {
        this.events.clear();
        for (int i = 0; i < list.size() && i < CAPACITY; i++) {
            final CompoundTag row = list.getCompound(i);
            this.events.addLast(bounded(new Event(row.getLong("T"), row.getString("W"),
                    row.getString("D"), row.getString("X"), Verdict.byOrdinal(row.getInt("V")))));
        }
    }

    public static int listType() {
        return Tag.TAG_COMPOUND;
    }
}
