package com.createstats.inventory.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BinCardLedger {
    public static final String OTHER = StockFlow.OTHER;
    public static final int MAX_KEYS = 64;
    public static final long TICKS_A_DAY = 24_000L;

    public static final class Card {
        long opening;
        long received;
        long issued;
        long todayIn;
        long todayOut;
        long yesterdayIn;
        long yesterdayOut;
        long yesterdayClose;
        boolean hasYesterday;

        public long opening() {
            return opening;
        }

        public long received() {
            return received;
        }

        public long issued() {
            return issued;
        }

        public long balance() {
            return opening + received - issued;
        }

        public long todayIn() {
            return todayIn;
        }

        public long todayOut() {
            return todayOut;
        }

        public long yesterdayIn() {
            return yesterdayIn;
        }

        public long yesterdayOut() {
            return yesterdayOut;
        }

        public long yesterdayClose() {
            return yesterdayClose;
        }

        public boolean hasYesterday() {
            return hasYesterday;
        }

        void add(Card o) {
            opening += o.opening;
            received += o.received;
            issued += o.issued;
            todayIn += o.todayIn;
            todayOut += o.todayOut;
            yesterdayIn += o.yesterdayIn;
            yesterdayOut += o.yesterdayOut;
            yesterdayClose += o.yesterdayClose;
            hasYesterday |= o.hasYesterday;
        }
    }

    private final int maxKeys;
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private int today = Integer.MIN_VALUE;
    private int openedDay = Integer.MIN_VALUE;

    public BinCardLedger() {
        this(MAX_KEYS);
    }

    public BinCardLedger(int maxKeys) {
        this.maxKeys = Math.max(1, maxKeys);
    }

    public static int dayOf(long dayTime) {
        return (int) Math.floorDiv(dayTime, TICKS_A_DAY);
    }

    public boolean isOpen() {
        return this.openedDay != Integer.MIN_VALUE;
    }

    public int openedDay() {
        return this.openedDay;
    }

    public int today() {
        return this.today;
    }

    public void open(int day, Map<String, Long> counts) {
        this.cards.clear();
        this.today = day;
        this.openedDay = day;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            final long v = e.getValue() == null ? 0L : e.getValue();
            if (v != 0L) {
                cardFor(e.getKey()).opening += v;
            }
        }
    }

    public void reconcile(int day, Map<String, Long> counts) {
        if (!isOpen()) {
            open(day, counts);
            return;
        }
        roll(day);
        final Map<String, Long> deltas = new LinkedHashMap<>();
        final Map<String, Long> books = new LinkedHashMap<>();
        for (Map.Entry<String, Card> e : this.cards.entrySet()) {
            books.put(e.getKey(), e.getValue().balance());
        }
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            final String key = this.cards.containsKey(e.getKey()) || namedCards() < this.maxKeys ? e.getKey() : OTHER;
            final long now = e.getValue() == null ? 0L : e.getValue();
            deltas.merge(key, now, Long::sum);
        }
        for (Map.Entry<String, Long> e : books.entrySet()) {
            deltas.merge(e.getKey(), -e.getValue(), Long::sum);
        }
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            if (e.getValue() != 0L) {
                book(e.getKey(), e.getValue());
            }
        }
    }

    public void apply(int day, Map<String, Long> deltas) {
        if (!isOpen()) {
            this.openedDay = day;
        }
        roll(day);
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            final long v = e.getValue() == null ? 0L : e.getValue();
            if (v != 0L) {
                book(e.getKey(), v);
            }
        }
    }

    private void book(String key, long delta) {
        final Card c = cardFor(key);
        if (delta > 0L) {
            c.received += delta;
            c.todayIn += delta;
        } else {
            c.issued += -delta;
            c.todayOut += -delta;
        }
    }

    public void roll(int day) {
        if (this.today == Integer.MIN_VALUE) {
            this.today = day;
            return;
        }
        if (day == this.today) {
            return;
        }
        final boolean next = day == this.today + 1;
        for (Card c : this.cards.values()) {
            if (next) {
                c.yesterdayIn = c.todayIn;
                c.yesterdayOut = c.todayOut;
                c.yesterdayClose = c.balance();
                c.hasYesterday = true;
            } else {
                c.yesterdayIn = 0L;
                c.yesterdayOut = 0L;
                c.yesterdayClose = 0L;
                c.hasYesterday = false;
            }
            c.todayIn = 0L;
            c.todayOut = 0L;
        }
        this.today = day;
    }

    public void close(int day) {
        roll(day);
        for (Card c : this.cards.values()) {
            c.opening = c.balance();
            c.received = 0L;
            c.issued = 0L;
        }
        this.openedDay = day;
    }

    private Card cardFor(String key) {
        Card c = this.cards.get(key);
        if (c != null) {
            return c;
        }
        final String name = OTHER.equals(key) || namedCards() < this.maxKeys ? key : OTHER;
        return this.cards.computeIfAbsent(name, k -> new Card());
    }

    private int namedCards() {
        return this.cards.containsKey(OTHER) ? this.cards.size() - 1 : this.cards.size();
    }

    public Card card(String key) {
        final Card c = this.cards.get(key);
        return c != null ? c : new Card();
    }

    public Card total() {
        final Card t = new Card();
        for (Card c : this.cards.values()) {
            t.add(c);
        }
        return t;
    }

    public List<String> keys() {
        return Collections.unmodifiableList(new ArrayList<>(this.cards.keySet()));
    }

    public record Row(String key, long opening, long received, long issued, long todayIn, long todayOut,
                      long yesterdayIn, long yesterdayOut, long yesterdayClose, boolean hasYesterday) {
        public long balance() {
            return opening + received - issued;
        }
    }

    public List<Row> export() {
        final List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Card> e : this.cards.entrySet()) {
            final Card c = e.getValue();
            out.add(new Row(e.getKey(), c.opening, c.received, c.issued, c.todayIn, c.todayOut, c.yesterdayIn,
                    c.yesterdayOut, c.yesterdayClose, c.hasYesterday));
        }
        return out;
    }

    public void restore(int today, int openedDay, List<Row> rows) {
        this.cards.clear();
        this.today = today;
        this.openedDay = openedDay;
        for (Row r : rows) {
            final Card c = new Card();
            c.opening = r.opening();
            c.received = r.received();
            c.issued = r.issued();
            c.todayIn = r.todayIn();
            c.todayOut = r.todayOut();
            c.yesterdayIn = r.yesterdayIn();
            c.yesterdayOut = r.yesterdayOut();
            c.yesterdayClose = r.yesterdayClose();
            c.hasYesterday = r.hasYesterday();
            if (this.cards.size() < this.maxKeys + 1) {
                this.cards.put(r.key(), c);
            }
        }
    }

    public void clear() {
        this.cards.clear();
        this.today = Integer.MIN_VALUE;
        this.openedDay = Integer.MIN_VALUE;
    }
}
