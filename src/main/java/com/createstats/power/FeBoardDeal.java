package com.createstats.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// one controller promised everything to two boards. promised twice, kept once
public final class FeBoardDeal {
    private FeBoardDeal() {
    }

    public record Request(long boardPos, int critical, int running, int restore) {
        public static final Request NONE = new Request(0L, 0, 0, 0);

        public Request {
            critical = Math.max(0, critical);
            running = Math.max(0, running);
            restore = Math.max(0, restore);
        }

        public int total() {
            return FeRates.plus(critical, FeRates.plus(running, restore));
        }
    }

    public enum Reason {
        GRANTED("Granted in full"),
        SHARED("Shared with the other boards on this reserve"),
        CHARGING("Generation held back to refill the banks"),
        BAND("The reserve is held to its band"),
        UPSTREAM("A board above is short, so this one is too"),
        FLOOR("The banks are below their floor: Critical only"),
        WIRE("The wire between them is full"),
        HELD("That output is switched off or held"),
        NOTHING("Nothing is generating and nothing is stored");

        private final String sentence;

        Reason(String sentence) {
            this.sentence = sentence;
        }

        public String sentence() {
            return sentence;
        }
    }

    public record Answer(long sourcePos, boolean own, Request asked, int critical, int running,
                         int restore, Reason reason, FeReserveReport.Stance stance, int socPct,
                         long untilTick, int spare) {
        public static final Answer NONE = new Answer(0L, false, Request.NONE, 0, 0, 0,
                Reason.NOTHING, FeReserveReport.Stance.CONSERVE, -1, Long.MIN_VALUE, 0);

        public Answer {
            asked = asked == null ? Request.NONE : asked;
            critical = Math.max(0, critical);
            running = Math.max(0, running);
            restore = Math.max(0, restore);
            reason = reason == null ? Reason.GRANTED : reason;
            stance = stance == null ? FeReserveReport.Stance.CONSERVE : stance;
            spare = Math.max(0, spare);
        }

        public Answer(long sourcePos, boolean own, Request asked, int critical, int running, int restore,
                      Reason reason, FeReserveReport.Stance stance, int socPct, long untilTick) {
            this(sourcePos, own, asked, critical, running, restore, reason, stance, socPct, untilTick, 0);
        }

        public int total() {
            return FeRates.plus(critical, FeRates.plus(running, restore));
        }

        public int shortfall() {
            return Math.max(0, asked.total() - total());
        }

        public boolean isPresent() {
            return this != NONE && untilTick != Long.MIN_VALUE;
        }

        public Answer until(long until) {
            return new Answer(sourcePos, own, asked, critical, running, restore, reason, stance,
                    socPct, until, spare);
        }
    }

    public record Offer(int runPool, int criticalPool, int claim, FeReserveReport.Stance stance,
                        int socPct, boolean upstreamHold) {
        public Offer {
            runPool = Math.max(0, runPool);
            criticalPool = Math.max(runPool, criticalPool);
            claim = Math.max(0, claim);
            stance = stance == null ? FeReserveReport.Stance.CONSERVE : stance;
        }

        public static Offer of(FeReserveReport report, int otherSpend, boolean upstreamHold) {
            final int other = Math.max(0, otherSpend);
            return new Offer(Math.max(0, report.runBudget() - other),
                    Math.max(0, report.criticalBudget() - other), report.chargeClaim(),
                    report.stance(), report.isPresent() ? report.socPct() : -1, upstreamHold);
        }
    }

    public static List<Answer> split(long sourcePos, boolean own, Offer offer, List<Request> requests,
                                     int[] caps, boolean[] held, long untilTick) {
        return split(sourcePos, own, offer, requests, caps, held, null, untilTick);
    }

    public static List<Answer> split(long sourcePos, boolean own, Offer offer, List<Request> requests,
                                     int[] caps, boolean[] held, int[] tiers, long untilTick) {
        final int n = requests.size();
        final int[] cap = new int[n];
        for (int i = 0; i < n; i++) {
            final boolean off = held != null && i < held.length && held[i];
            cap[i] = off ? 0 : caps != null && i < caps.length ? Math.max(0, caps[i]) : Integer.MAX_VALUE;
        }

        final int[] wantCritical = new int[n];
        for (int i = 0; i < n; i++) {
            wantCritical[i] = Math.min(requests.get(i).critical(), cap[i]);
        }
        final int[] critical = share(wantCritical, offer.criticalPool());
        int used = sum(critical);

        final int[] wantRunning = new int[n];
        for (int i = 0; i < n; i++) {
            wantRunning[i] = Math.min(requests.get(i).running(), Math.max(0, cap[i] - critical[i]));
        }
        final int[] running = shareByTier(wantRunning, Math.max(0, offer.runPool() - used), tiers);
        used = FeRates.plus(used, sum(running));

        final boolean rationing = offer.stance().isRationing();
        final int[] wantRestore = new int[n];
        for (int i = 0; i < n; i++) {
            wantRestore[i] = rationing ? 0 : Math.min(requests.get(i).restore(),
                    Math.max(0, cap[i] - critical[i] - running[i]));
        }
        final int[] restore = shareByTier(wantRestore, Math.max(0, offer.runPool() - used), tiers);
        used = FeRates.plus(used, sum(restore));

        final int poolLeft = Math.max(0, offer.runPool() - used);

        final List<Answer> answers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final Request r = requests.get(i);
            final boolean off = held != null && i < held.length && held[i];
            final int given = FeRates.plus(critical[i], FeRates.plus(running[i], restore[i]));

            final int spare = off ? 0 : Math.min(poolLeft, Math.max(0, cap[i] - given));
            answers.add(new Answer(sourcePos, own, r, critical[i], running[i], restore[i],
                    reasonFor(r, given, cap[i], off, offer, n, rationing && r.restore() > 0),
                    offer.stance(), offer.socPct(), untilTick, spare));
        }
        return answers;
    }

    static Reason reasonFor(Request r, int given, int cap, boolean held, Offer offer, int boards) {
        return reasonFor(r, given, cap, held, offer, boards, false);
    }

    static Reason reasonFor(Request r, int given, int cap, boolean held, Offer offer, int boards,
                            boolean restoreWithheld) {
        final int asked = r.total();
        if (given >= asked) {
            return Reason.GRANTED;
        }
        if (held) {
            return Reason.HELD;
        }
        if (asked > cap && given >= cap) {
            return Reason.WIRE;
        }
        if (offer.criticalPool() <= 0) {
            return Reason.NOTHING;
        }
        if (offer.stance() == FeReserveReport.Stance.CRITICAL) {
            return Reason.FLOOR;
        }
        if (offer.upstreamHold()) {
            return Reason.UPSTREAM;
        }
        if (restoreWithheld && given >= FeRates.plus(r.critical(), r.running())) {
            return Reason.BAND;
        }
        final int alone = Math.min(asked, cap);
        if (boards > 1 && alone <= offer.runPool()) {
            return Reason.SHARED;
        }
        if (offer.claim() > 0 && alone <= FeRates.plus(offer.runPool(), offer.claim())) {
            return Reason.CHARGING;
        }
        return Reason.BAND;
    }

    public static Answer combine(Collection<Answer> answers) {
        Answer single = null;
        int present = 0;
        for (Answer a : answers) {
            if (a != null && a.isPresent()) {
                single = a;
                present++;
            }
        }
        if (present <= 1) {
            return single == null ? Answer.NONE : single;
        }
        int critical = 0;
        int running = 0;
        int restore = 0;
        int spare = 0;
        int askedCritical = 0;
        int askedRunning = 0;
        int askedRestore = 0;
        long until = Long.MAX_VALUE;
        Answer worst = null;
        for (Answer a : answers) {
            if (a == null || !a.isPresent()) {
                continue;
            }
            critical = FeRates.plus(critical, a.critical());
            running = FeRates.plus(running, a.running());
            restore = FeRates.plus(restore, a.restore());
            spare = FeRates.plus(spare, a.spare());
            askedCritical = FeRates.plus(askedCritical, a.asked().critical());
            askedRunning = FeRates.plus(askedRunning, a.asked().running());
            askedRestore = FeRates.plus(askedRestore, a.asked().restore());
            until = Math.min(until, a.untilTick());
            if (worst == null || a.shortfall() > worst.shortfall()) {
                worst = a;
            }
        }
        final Request asked = new Request(worst.asked().boardPos(), askedCritical, askedRunning, askedRestore);
        final int granted = FeRates.plus(critical, FeRates.plus(running, restore));
        return new Answer(worst.sourcePos(), false, asked, critical, running, restore,
                granted < asked.total() ? worst.reason() : Reason.GRANTED, worst.stance(),
                worst.socPct(), until, spare);
    }

    public static Request residual(Request asked, Answer answer) {
        final Request r = asked == null ? Request.NONE : asked;
        final Answer a = answer == null ? Answer.NONE : answer;
        return new Request(r.boardPos(), Math.max(0, r.critical() - a.critical()),
                Math.max(0, r.running() - a.running()), Math.max(0, r.restore() - a.restore()));
    }

    public static Answer topUp(Answer primary, Answer extra) {
        if (primary == null || !primary.isPresent()) {
            return extra == null ? Answer.NONE : extra;
        }
        if (extra == null || !extra.isPresent()) {
            return primary;
        }
        final int critical = FeRates.plus(primary.critical(), extra.critical());
        final int running = FeRates.plus(primary.running(), extra.running());
        final int restore = FeRates.plus(primary.restore(), extra.restore());
        final int granted = FeRates.plus(critical, FeRates.plus(running, restore));
        return new Answer(primary.sourcePos(), primary.own(), primary.asked(), critical, running, restore,
                granted >= primary.asked().total() ? Reason.GRANTED : primary.reason(), primary.stance(),
                primary.socPct(), Math.min(primary.untilTick(), extra.untilTick()),
                FeRates.plus(primary.spare(), extra.spare()));
    }

    public static int runBudget(int answered, int ride, int spare, int ceiling) {
        final int room = FeRates.plus(Math.max(0, ride), Math.max(0, spare));
        return Math.max(0, Math.min(ceiling, FeRates.plus(Math.max(0, answered), room)));
    }

    public static Request[] splitRequest(Request whole, int[] weights) {
        final int n = weights == null ? 0 : weights.length;
        if (n == 0) {
            return new Request[0];
        }
        final Request r = whole == null ? Request.NONE : whole;
        if (n == 1) {
            return new Request[] {r};
        }
        final int[] critical = apportion(r.critical(), weights);
        final int[] running = apportion(r.running(), weights);
        final int[] restore = apportion(r.restore(), weights);
        final Request[] out = new Request[n];
        for (int i = 0; i < n; i++) {
            out[i] = new Request(r.boardPos(), critical[i], running[i], restore[i]);
        }
        return out;
    }

    public static int[] shareWire(int[] asks, int wire) {
        final int n = asks == null ? 0 : asks.length;
        final int[] got = new int[n];
        if (n == 0 || wire <= 0) {
            return got;
        }
        if (wire == Integer.MAX_VALUE || n == 1) {
            java.util.Arrays.fill(got, wire);
            return got;
        }
        return apportion(wire, asks);
    }

    static int[] apportion(int amount, int[] weights) {
        final int n = weights.length;
        final int[] got = new int[n];
        final int total = Math.max(0, amount);
        if (n == 0 || total == 0) {
            return got;
        }
        long sum = 0;
        for (int w : weights) {
            sum += Math.max(0, w);
        }
        if (sum <= 0) {
            for (int i = 0; i < n; i++) {
                got[i] = total / n + (i < total % n ? 1 : 0);
            }
            return got;
        }
        long given = 0;
        for (int i = 0; i < n; i++) {
            got[i] = (int) (Math.max(0, weights[i]) * (long) total / sum);
            given += got[i];
        }

        long left = total - given;
        for (int i = 0; i < n && left > 0; i++) {
            if (weights[i] > 0) {
                got[i]++;
                left--;
            }
        }
        return got;
    }

    public static int fromStorage(int intake, int discharged, int collected) {
        if (intake <= 0 || discharged <= 0 || collected <= 0) {
            return 0;
        }
        return (int) Math.min(intake, (long) intake * Math.min(discharged, collected) / collected);
    }

    static int[] shareByTier(int[] want, int pool, int[] tiers) {
        final int n = want.length;
        if (tiers == null || tiers.length < n) {
            return share(want, pool);
        }
        final int[] got = new int[n];
        int left = Math.max(0, pool);
        final int[] order = java.util.Arrays.stream(tiers, 0, n).distinct().sorted().toArray();
        for (int tier : order) {
            final int[] here = new int[n];
            for (int i = 0; i < n; i++) {
                here[i] = tiers[i] == tier ? want[i] : 0;
            }
            final int[] gave = share(here, left);
            for (int i = 0; i < n; i++) {
                got[i] = FeRates.plus(got[i], gave[i]);
            }
            left = Math.max(0, left - sum(gave));
        }
        return got;
    }

    static int[] share(int[] want, int pool) {
        final int n = want.length;
        final int[] got = new int[n];
        long total = 0;
        for (int w : want) {
            total += Math.max(0, w);
        }
        if (total <= pool) {
            for (int i = 0; i < n; i++) {
                got[i] = Math.max(0, want[i]);
            }
            return got;
        }
        if (pool <= 0) {
            return got;
        }
        long given = 0;
        for (int i = 0; i < n; i++) {
            got[i] = (int) (Math.max(0, want[i]) * (long) pool / total);
            given += got[i];
        }
        long left = pool - given;
        for (int i = 0; i < n && left > 0; i++) {
            if (got[i] < want[i]) {
                got[i]++;
                left--;
            }
        }
        return got;
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s = FeRates.plus(s, v);
        }
        return s;
    }
}
