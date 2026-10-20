package com.createstats.train.map;

import java.util.List;
import java.util.Set;

import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailFilter;
import com.createstats.train.smart.RailSnapshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// one shared fold for every block face
@OnlyIn(Dist.CLIENT)
public final class ClientRailMap {
    private ClientRailMap() {
    }

    private static RailMap map = RailMap.flat(RailSnapshot.EMPTY);
    private static int signature = Integer.MIN_VALUE;
    private static int filterRevision = -1;

    public static RailMap get() {
        final RailSnapshot snap = RailFilter.get().apply(ClientRailNetwork.get());
        final int roster = RailMap.rosterSignature(snap);
        final int filter = RailFilter.get().revision();
        if (roster == signature && filter == filterRevision) {
            map.refresh(snap);
            return map;
        }
        signature = roster;
        filterRevision = filter;

        map = RailMap.build(snap, List.of(), true, Set.of());
        return map;
    }

    public static RailMap sampled() {
        final RailMap current = get();
        RailHistory.sample(current.snapshot(), current);
        return current;
    }

    public static int lineNamed(RailMap on, String label) {
        if (on == null || on.lines().isEmpty()) {
            return -1;
        }
        if (label != null && !label.isEmpty()) {
            for (int i = 0; i < on.lines().size(); i++) {
                if (on.lines().get(i).label(on).equalsIgnoreCase(label)) {
                    return i;
                }
            }
        }
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < on.lines().size(); i++) {
            final RailMap.Line line = on.lines().get(i);
            if (line.nodes.size() < 2) {
                continue;
            }
            final int score = line.trains.size() * 100 + line.nodes.size();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public static void clear() {
        map = RailMap.flat(RailSnapshot.EMPTY);
        signature = Integer.MIN_VALUE;
        filterRevision = -1;
    }
}
