package com.createstats.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LinkWeb {
    public static final double RANGE = 32.0;

    public static final double REACH = 64.0;

    public static final double PERIOD = 0.25;

    public static final double BEAD = 0.125;

    public static final double MARCH_TICKS = 10.0;

    public static final int[] PALETTE = {
            0xF4F1E8,
            0xF2692D,
            0x2EF0E6,
            0x1E90FF,
            0xE040FF,
            0xFFB3EC,
            0xFF4D6D,
    };

    private static final double EPS = 1.0e-6;

    public record Spot(int x, int y, int z) {
        public double distanceSq(double px, double py, double pz) {
            final double dx = x + 0.5 - px;
            final double dy = y + 0.5 - py;
            final double dz = z + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Link(Spot controller, Spot fixture) {
    }

    public record Mark(Spot spot, int colour) {
    }

    public static final class Selection {
        public static final Selection NONE = new Selection(null, List.of(), List.of());

        private final Spot looked;
        private final List<Link> links;
        private final List<Mark> outlined;
        private final Set<Link> chosen;

        private Selection(Spot looked, List<Link> links, List<Mark> outlined) {
            this.looked = looked;
            this.links = links;
            this.outlined = outlined;
            this.chosen = Set.copyOf(links);
        }

        public Spot looked() {
            return looked;
        }

        public List<Link> links() {
            return links;
        }

        public List<Mark> outlined() {
            return outlined;
        }

        public boolean isEmpty() {
            return links.isEmpty();
        }

        public boolean contains(Link link) {
            return chosen.contains(link);
        }
    }

    private final List<Link> links;
    private final Map<Spot, List<Link>> byController = new HashMap<>();
    private final Map<Spot, List<Link>> byFixture = new HashMap<>();

    private LinkWeb(List<Link> links) {
        this.links = List.copyOf(links);
        for (final Link link : this.links) {
            byController.computeIfAbsent(link.controller(), k -> new ArrayList<>(4)).add(link);
            byFixture.computeIfAbsent(link.fixture(), k -> new ArrayList<>(2)).add(link);
        }
    }

    public static LinkWeb near(Collection<Link> bindings, double px, double py, double pz) {
        final Set<Link> kept = new LinkedHashSet<>();
        for (final Link link : bindings) {
            if (link != null && !link.controller().equals(link.fixture()) && inRange(link, px, py, pz)) {
                kept.add(link);
            }
        }
        final List<Link> sorted = new ArrayList<>(kept);
        sorted.sort(Comparator.comparingDouble(link -> nearerEndSq(link, px, py, pz)));
        return new LinkWeb(sorted);
    }

    public static boolean inRange(Link link, double px, double py, double pz) {
        final double a = link.controller().distanceSq(px, py, pz);
        final double b = link.fixture().distanceSq(px, py, pz);
        return Math.min(a, b) <= RANGE * RANGE && Math.max(a, b) <= REACH * REACH;
    }

    private static double nearerEndSq(Link link, double px, double py, double pz) {
        return Math.min(link.controller().distanceSq(px, py, pz), link.fixture().distanceSq(px, py, pz));
    }

    public List<Link> links() {
        return links;
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public Selection select(Spot looked) {
        if (looked == null) {
            return Selection.NONE;
        }
        final List<Link> asController = byController.getOrDefault(looked, List.of());
        final List<Link> asFixture = byFixture.getOrDefault(looked, List.of());
        if (asController.isEmpty() && asFixture.isEmpty()) {
            return Selection.NONE;
        }
        final Set<Link> chosen = new LinkedHashSet<>(asController);
        chosen.addAll(asFixture);
        final Map<Spot, Mark> marks = new LinkedHashMap<>();
        for (final Link link : chosen) {
            final Spot other = link.controller().equals(looked) ? link.fixture() : link.controller();
            if (!other.equals(looked)) {
                marks.putIfAbsent(other, new Mark(other, colourOf(link.controller())));
            }
        }
        return new Selection(looked, List.copyOf(chosen), List.copyOf(marks.values()));
    }

    public static int colourOf(Spot controller) {
        final int i = Math.floorMod(controller.x() + 2 * controller.y() + 3 * controller.z(), PALETTE.length);
        return PALETTE[i];
    }

    public static double marchAt(double ticks) {
        return ticks / MARCH_TICKS;
    }

    public static int beadCount(double length) {
        return length > 0 ? (int) Math.ceil(length / PERIOD) + 1 : 0;
    }

    public static double[] beads(double length, double phase) {
        if (!(length > EPS)) {
            return new double[0];
        }
        final double shift = (phase - Math.floor(phase)) * PERIOD;
        final double[] out = new double[2 * beadCount(length) + 2];
        int n = 0;
        for (int k = -1; k * PERIOD + shift < length; k++) {
            final double start = Math.max(0.0, k * PERIOD + shift);
            final double end = Math.min(length, k * PERIOD + shift + BEAD);
            if (end - start > EPS) {
                out[n++] = start;
                out[n++] = end;
            }
        }
        return Arrays.copyOf(out, n);
    }
}
