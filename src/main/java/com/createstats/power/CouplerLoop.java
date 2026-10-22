package com.createstats.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class CouplerLoop {
    private CouplerLoop() {
    }

    public record Node(boolean pulling, Set<Object> ourCircuits, Set<Object> theirIdentities, boolean endpointOnOurs) {
    }

    public static boolean circles(Node self, Node other) {
        if (other == null || other == self) {
            return false;
        }
        return self.pulling() != other.pulling()
                && meet(self.ourCircuits(), other.ourCircuits())
                && meet(self.theirIdentities(), other.theirIdentities());
    }

    public static boolean looped(Node self, Iterable<Node> others) {
        if (self.endpointOnOurs() && !self.ourCircuits().isEmpty()) {
            return true;
        }
        for (Node other : others) {
            if (circles(self, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean meet(Set<Object> a, Set<Object> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        final Set<Object> small = a.size() <= b.size() ? a : b;
        final Set<Object> large = small == a ? b : a;
        for (Object o : small) {
            if (large.contains(o)) {
                return true;
            }
        }
        return false;
    }

    public static final class Registry {
        public static final long STALE_TICKS = 40;

        private record Stamped(Node node, long at) {
        }

        private final Map<Long, Stamped> nodes = new HashMap<>();

        public boolean publishAndAsk(long id, Node node, long now) {
            this.nodes.put(id, new Stamped(node, now));
            final java.util.List<Node> others = new java.util.ArrayList<>(this.nodes.size());
            for (Iterator<Map.Entry<Long, Stamped>> it = this.nodes.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Long, Stamped> e = it.next();
                final long age = now - e.getValue().at();
                if (age < 0 || age > STALE_TICKS) {
                    it.remove();
                    continue;
                }
                if (e.getKey() != id) {
                    others.add(e.getValue().node());
                }
            }
            return looped(node, others);
        }

        public void forget(long id) {
            this.nodes.remove(id);
        }

        public int size() {
            return this.nodes.size();
        }
    }
}
