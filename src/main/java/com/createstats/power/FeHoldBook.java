package com.createstats.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// one hold per asker. a later small request used to replace a live big one
public final class FeHoldBook {
    public static final long UNNAMED = Long.MIN_VALUE;

    public record Hold(int fe, long untilTick) {
        public static final Hold NONE = new Hold(0, Long.MIN_VALUE);

        public boolean isPresent() {
            return fe > 0;
        }
    }

    private final Map<Long, Hold> byAsker = new HashMap<>();

    public void accept(long fromPos, int holdFe, long untilTick) {
        if (holdFe <= 0) {
            byAsker.remove(fromPos);
            return;
        }
        byAsker.put(fromPos, new Hold(holdFe, untilTick));
    }

    public Hold largest(long now) {
        Hold best = Hold.NONE;
        for (Iterator<Hold> it = byAsker.values().iterator(); it.hasNext(); ) {
            final Hold h = it.next();
            if (h.untilTick() < now) {
                it.remove();
                continue;
            }
            if (h.fe() > best.fe() || (h.fe() == best.fe() && h.untilTick() > best.untilTick())) {
                best = h;
            }
        }
        return best;
    }

    public int askers(long now) {
        largest(now);
        return byAsker.size();
    }

    public void clear() {
        byAsker.clear();
    }
}
