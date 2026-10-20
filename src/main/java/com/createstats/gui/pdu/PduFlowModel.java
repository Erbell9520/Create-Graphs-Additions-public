package com.createstats.gui.pdu;

import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PduFlowModel {
    private PduFlowModel() {
    }

    public record Budgets(int run, int admit, int critical, int forecastDrop, int standing,
                          int chargeClaim, int ride, boolean belowFloor, boolean linked,
                          boolean surplus, boolean wireLimited, int ceiling) {
        public static final Budgets NONE =
                new Budgets(0, 0, 0, 0, 0, 0, 0, false, false, false, false, 0);

        public Budgets {
            run = Math.max(0, run);
            admit = Math.max(0, admit);
            critical = Math.max(run, Math.max(0, critical));
            forecastDrop = Math.max(0, forecastDrop);
            standing = Math.max(0, standing);
            chargeClaim = Math.max(0, chargeClaim);
            ride = Math.max(0, ride);
            ceiling = Math.max(0, ceiling);
        }
    }

    public record SupplyParts(int priced, int weatherShare, int observed, int linked, int legs,
                              boolean allPriced, boolean controllerLinked) {
        public SupplyParts {
            priced = Math.max(0, priced);
            weatherShare = Math.max(0, Math.min(priced, weatherShare));
            observed = Math.max(0, observed);
            linked = Math.max(0, linked);
            legs = Math.max(0, legs);
        }

        public SupplyParts(int priced, int weatherShare, int observed, int linked, int legs) {
            this(priced, weatherShare, observed, linked, legs, false);
        }

        public SupplyParts(int priced, int weatherShare, int observed, int linked, int legs,
                           boolean allPriced) {
            this(priced, weatherShare, observed, linked, legs, allPriced, false);
        }

        public int sideSupply() {
            return Math.max(Math.max(priced, observed), linked);
        }

        public int total() {
            return plus(sideSupply(), legs);
        }
    }

    public enum Kind {
        SOLAR(true, "Solar"),
        GENERATORS(true, "Generators"),
        CONTROLLER(true, "Battery Controller"),
        UNPRICED(true, "Unpriced feed"),

        SOURCE_BUFFERS(true, "Source buffers"),
        LEG_SOURCES(true, "Leg sources"),
        FROM_BUFFER(true, "From buffer"),
        CRITICAL(false, "Critical"),
        HIGH(false, "High"),
        NORMAL(false, "Normal"),
        LOW(false, "Low"),
        OPTIONAL(false, "Optional"),
        EXPORT(false, "Back boss export"),
        CHARGING(false, "Charging banks"),
        UPKEEP(false, "Panel upkeep"),
        SPARE(false, "Spare"),

        OTHER_IN(true, "Others"),

        OTHER_OUT(false, "Others");

        private final boolean source;
        private final String label;

        Kind(boolean source, String label) {
            this.source = source;
            this.label = label;
        }

        public boolean isSource() {
            return source;
        }

        public String label() {
            return label;
        }

        public static Kind forTier(FePriority tier) {
            return switch (tier) {
                case CRITICAL -> CRITICAL;
                case HIGH -> HIGH;
                case NORMAL -> NORMAL;
                case LOW -> LOW;
                case OPTIONAL -> OPTIONAL;
            };
        }

        public FePriority tier() {
            return switch (this) {
                case CRITICAL -> FePriority.CRITICAL;
                case HIGH -> FePriority.HIGH;
                case NORMAL -> FePriority.NORMAL;
                case LOW -> FePriority.LOW;
                case OPTIONAL -> FePriority.OPTIONAL;
                default -> null;
            };
        }
    }

    public record Node(Kind kind, int fe) {
    }

    public record Inputs(SupplyParts supply, int[] tierServed, int backExport, int bankCharging,
                         int upkeep) {
    }

    public record Flow(List<Node> in, List<Node> out, int total) {
        public int sum(boolean sources) {
            int s = 0;
            for (Node n : sources ? in : out) {
                s = plus(s, n.fe());
            }
            return s;
        }
    }

    public static Flow flow(Inputs inputs) {
        final SupplyParts s = inputs.supply();
        final List<Node> in = new ArrayList<>(6);
        final int solar = s.weatherShare();
        final int generators = s.priced() - solar;
        final int extra = s.sideSupply() - s.priced();
        add(in, Kind.SOLAR, solar);
        add(in, Kind.GENERATORS, generators);

        add(in, s.controllerLinked() || s.linked() >= s.observed() ? Kind.CONTROLLER
                : s.allPriced() ? Kind.SOURCE_BUFFERS : Kind.UNPRICED, extra);
        add(in, Kind.LEG_SOURCES, s.legs());
        final int supplied = s.total();

        final List<Node> out = new ArrayList<>(9);
        int used = 0;
        final FePriority[] tiers = FePriority.values();
        for (int t = 0; t < tiers.length; t++) {
            final int fe = inputs.tierServed() != null && t < inputs.tierServed().length
                    ? Math.max(0, inputs.tierServed()[t]) : 0;
            add(out, Kind.forTier(tiers[t]), fe);
            used = plus(used, fe);
        }
        add(out, Kind.EXPORT, inputs.backExport());
        add(out, Kind.CHARGING, inputs.bankCharging());
        add(out, Kind.UPKEEP, inputs.upkeep());
        used = plus(used, plus(Math.max(0, inputs.backExport()),
                plus(Math.max(0, inputs.bankCharging()), Math.max(0, inputs.upkeep()))));

        if (used > supplied) {
            add(in, Kind.FROM_BUFFER, used - supplied);
        } else {
            add(out, Kind.SPARE, supplied - used);
        }
        return new Flow(List.copyOf(in), List.copyOf(out), Math.max(supplied, used));
    }

    private static void add(List<Node> nodes, Kind kind, int fe) {
        if (fe > 0) {
            nodes.add(new Node(kind, fe));
        }
    }

    public static int capacity(int height, int gap, int minH) {
        return Math.max(1, (Math.max(0, height) + Math.max(0, gap)) / Math.max(1, minH + Math.max(0, gap)));
    }

    public static List<Node> fit(List<Node> nodes, int maxNodes, boolean sources) {
        if (nodes.size() <= Math.max(1, maxNodes)) {
            return nodes;
        }
        final int keep = Math.max(1, maxNodes) - 1;
        final List<Node> bySize = new ArrayList<>(nodes);
        bySize.sort((a, b) -> Integer.compare(b.fe(), a.fe()));
        final java.util.Set<Node> kept = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < keep; i++) {
            kept.add(bySize.get(i));
        }
        final List<Node> out = new ArrayList<>(maxNodes);
        int merged = 0;
        for (Node n : nodes) {
            if (kept.contains(n)) {
                out.add(n);
            } else {
                merged = plus(merged, n.fe());
            }
        }
        if (merged > 0) {
            out.add(new Node(sources ? Kind.OTHER_IN : Kind.OTHER_OUT, merged));
        }
        return out;
    }

    public static int[] layout(List<Node> nodes, int top, int height, int gap, int minH) {
        final int n = nodes.size();
        final int[] ys = new int[n * 2];
        if (n == 0 || height <= 0) {
            return ys;
        }
        final int gaps = Math.max(0, gap) * (n - 1);
        final int avail = Math.max(n, height - gaps);
        final int floor = Math.max(1, Math.min(minH, avail / n));
        int total = 0;
        for (Node node : nodes) {
            total = plus(total, node.fe());
        }
        final int spare = avail - floor * n;
        int y = top;
        int given = 0;
        for (int i = 0; i < n; i++) {
            int h = floor;
            if (i == n - 1) {
                h += spare - given;
            } else if (total > 0) {
                final int share = (int) ((long) spare * nodes.get(i).fe() / total);
                h += share;
                given += share;
            }
            ys[i * 2] = y;
            ys[i * 2 + 1] = y + h;
            y += h + Math.max(0, gap);
        }
        return ys;
    }

    public static int[] busSlots(List<Node> nodes, int total, int busTop, int busH) {
        final int n = nodes.size();
        final int[] ys = new int[n * 2];
        if (n == 0 || busH <= 0) {
            return ys;
        }
        int sum = 0;
        for (Node node : nodes) {
            sum = plus(sum, node.fe());
        }
        final int denominator = Math.max(1, Math.max(total, sum));
        int y = busTop;
        for (int i = 0; i < n; i++) {
            final int h = i == n - 1 ? busTop + busH - y
                    : (int) ((long) busH * nodes.get(i).fe() / denominator);
            ys[i * 2] = y;
            ys[i * 2 + 1] = y + Math.max(0, h);
            y += Math.max(0, h);
        }
        return ys;
    }

    public static float smooth(float t) {
        final float c = Math.max(0.0f, Math.min(1.0f, t));
        return c * c * (3.0f - 2.0f * c);
    }

    public static float particleSpeed(int fe) {
        if (fe <= 0) {
            return 0.0f;
        }
        return 10.0f + 14.0f * (float) Math.log10(1.0 + fe);
    }

    public static float particlePhase(long millis, int fe, float spacing) {
        if (spacing <= 0.0f) {
            return 0.0f;
        }
        final double travelled = (millis / 1000.0) * particleSpeed(fe);
        return (float) (travelled % spacing);
    }

    public static int ladderScale(Budgets b, int generation, int demand) {
        final int top = Math.max(Math.max(Math.max(1, generation), demand),
                Math.max(b.run(), b.admit() + b.forecastDrop()));
        return niceCeil((long) Math.ceil(top * 1.12));
    }

    public static int niceCeil(long v) {
        if (v <= 1) {
            return 1;
        }
        long p = 1;
        while (p * 10 <= v && p <= Integer.MAX_VALUE / 10) {
            p *= 10;
        }
        for (long m : new long[] {1, 2, 5, 10}) {
            if (m * p >= v) {
                return (int) Math.min(Integer.MAX_VALUE, m * p);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, 10 * p);
    }

    public static int ladderX(int value, int scale, int left, int width) {
        if (scale <= 0 || width <= 0) {
            return left;
        }
        final double f = Math.max(0.0, Math.min(1.0, value / (double) scale));
        return left + (int) Math.round(width * f);
    }

    public record TierSpan(FePriority tier, int start, int servedEnd, int end) {
        public int demand() {
            return end - start;
        }

        public int shed() {
            return end - servedEnd;
        }
    }

    public static List<TierSpan> tierSpans(int[] tierDemand, int[] tierServed) {
        final FePriority[] tiers = FePriority.values();
        final List<TierSpan> spans = new ArrayList<>(tiers.length);
        int at = 0;
        for (int t = 0; t < tiers.length; t++) {
            final int want = tierDemand != null && t < tierDemand.length ? Math.max(0, tierDemand[t]) : 0;
            if (want <= 0) {
                continue;
            }
            final int got = tierServed != null && t < tierServed.length
                    ? Math.max(0, Math.min(want, tierServed[t])) : 0;
            spans.add(new TierSpan(tiers[t], at, plus(at, got), plus(at, want)));
            at = plus(at, want);
        }
        return spans;
    }

    public static String verdict(PduMode mode, Budgets b, int generation, int[] shedByReason,
                                 int shedCount) {
        if (mode == PduMode.OFF) {
            return "Off: every device is served in turn until the buffer runs dry. No budget is kept.";
        }
        if (mode == PduMode.MANUAL) {
            return "Manual: each tier runs while the buffer is above its threshold. No budget is kept.";
        }
        if (b.belowFloor()) {
            return "The buffer is at its reserve floor: only Critical draws until it recovers.";
        }
        if (shedCount <= 0) {
            return b.surplus()
                    ? "Everything is running, with supply to spare."
                    : String.format(Locale.ROOT, "Everything is running inside the %s FE/t budget.",
                            fe(b.run()));
        }
        final PduReason top = dominant(shedByReason);
        final int held = top == null ? 0 : shedByReason[top.ordinal()];
        final String devices = shedCount == 1 ? "1 device" : shedCount + " devices";
        if (top == null) {
            return devices + " held back.";
        }
        return switch (top) {
            case SUPPLY_FALLING -> String.format(Locale.ROOT,
                    "%s (%s FE/t) held: the supply is forecast to fall %s FE/t within the minute.",
                    devices, fe(held), fe(b.forecastDrop()));
            case HEADROOM -> String.format(Locale.ROOT,
                    "%s (%s FE/t) held: they fit the %s FE/t budget, not yet with headroom to spare.",
                    devices, fe(held), fe(b.admit()));
            case OVER_CAPACITY -> String.format(Locale.ROOT,
                    "%s (%s FE/t) shed: the legs are full at %s FE/t. More wire, not more generation.",
                    devices, fe(held), fe(b.ceiling()));
            case CORE_RESERVE -> String.format(Locale.ROOT,
                    "%s (%s FE/t) held to keep the last of the reserve for Critical.", devices, fe(held));
            case AWAITING_SUPPLY -> String.format(Locale.ROOT,
                    "%s (%s FE/t) waiting: they fit, and restart once supply is spare.", devices, fe(held));
            case STAGED, AWAITING_DWELL -> String.format(Locale.ROOT,
                    "%s (%s FE/t) restarting in stages, one at a time.", devices, fe(held));
            case BELOW_FLOOR -> "The buffer is at its reserve floor: only Critical draws until it recovers.";
            case TIER_SHED -> String.format(Locale.ROOT,
                    "%s (%s FE/t) off: the buffer is below their tier's threshold.", devices, fe(held));
            default -> String.format(Locale.ROOT,
                    "%s (%s FE/t) shed: %s FE/t of generation stops short of them.",
                    devices, fe(held), fe(generation));
        };
    }

    public static PduReason dominant(int[] shedByReason) {
        if (shedByReason == null) {
            return null;
        }
        PduReason best = null;
        int bestFe = 0;
        final PduReason[] reasons = PduReason.values();
        for (int i = 0; i < Math.min(reasons.length, shedByReason.length); i++) {
            if (shedByReason[i] > bestFe) {
                bestFe = shedByReason[i];
                best = reasons[i];
            }
        }
        return best;
    }

    public static String fe(int v) {
        final int a = Math.abs(v);
        if (a >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        }
        if (a >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1_000.0);
        }
        return String.format(Locale.ROOT, "%,d", v);
    }

    static int plus(int a, int b) {
        final long s = (long) Math.max(0, a) + Math.max(0, b);
        return s > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) s;
    }
}
