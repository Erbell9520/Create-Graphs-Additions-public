package com.createstats.battle.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

// a sentence because "2 of 4 fired" doesn't tell you what to wait for
public final class SalvoResult {
    public record NotReady(String gun, GunState state, int seconds) {
    }

    private final List<String> fired;
    private final List<NotReady> notReady;

    public SalvoResult(List<String> fired, List<NotReady> notReady) {
        this.fired = List.copyOf(fired);
        this.notReady = List.copyOf(notReady);
    }

    public List<String> fired() {
        return fired;
    }

    public List<NotReady> notReady() {
        return notReady;
    }

    public static final class Builder {
        private final List<String> fired = new ArrayList<>();
        private final List<NotReady> notReady = new ArrayList<>();

        public Builder fired(String gun) {
            fired.add(gun);
            return this;
        }

        public Builder notReady(String gun, GunState state, int seconds) {
            notReady.add(new NotReady(gun, state, state == GunState.SLEWING ? seconds : -1));
            return this;
        }

        public SalvoResult build() {
            return new SalvoResult(fired, notReady);
        }
    }

    public String describe(BiFunction<String, Object[], String> tr) {
        if (fired.isEmpty() && notReady.isEmpty()) return tr.apply("salvo.none", new Object[0]);
        String reasons = reasons(tr);
        if (fired.isEmpty()) return tr.apply("salvo.nothing", new Object[]{reasons});
        String head = tr.apply("salvo.fired", new Object[]{String.join(", ", fired)});
        return notReady.isEmpty() ? head : head + tr.apply("salvo.not_ready", new Object[]{reasons});
    }

    private String reasons(BiFunction<String, Object[], String> tr) {
        List<String> parts = new ArrayList<>(notReady.size());
        for (NotReady n : notReady) {
            String word = tr.apply("gun.word." + n.state().key(), new Object[0]);
            parts.add(n.seconds() >= 0 ? tr.apply("salvo.item_eta", new Object[]{n.gun(), word, n.seconds()})
                    : tr.apply("salvo.item", new Object[]{n.gun(), word}));
        }
        return String.join(", ", parts);
    }

    public String encode() {
        StringBuilder b = new StringBuilder();
        b.append(fired.size());
        for (String f : fired) b.append('\u0001').append(f);
        b.append('\u0001').append(notReady.size());
        for (NotReady n : notReady) {
            b.append('\u0001').append(n.gun()).append('\u0001').append(n.state().ordinal()).append('\u0001').append(n.seconds());
        }
        return b.toString();
    }

    public static SalvoResult decode(String s) {
        if (s == null || s.isEmpty()) return new SalvoResult(List.of(), List.of());
        try {
            String[] p = s.split("\u0001", -1);
            int i = 0;
            int nf = Integer.parseInt(p[i++]);
            List<String> fired = new ArrayList<>(nf);
            for (int k = 0; k < nf; k++) fired.add(p[i++]);
            int nn = Integer.parseInt(p[i++]);
            List<NotReady> notReady = new ArrayList<>(nn);
            for (int k = 0; k < nn; k++) {
                String gun = p[i++];
                GunState st = GunState.byOrdinal(Integer.parseInt(p[i++]));
                int sec = Integer.parseInt(p[i++]);
                notReady.add(new NotReady(gun, st, sec));
            }
            return new SalvoResult(fired, notReady);
        } catch (RuntimeException e) {
            return new SalvoResult(List.of(), List.of());
        }
    }
}
