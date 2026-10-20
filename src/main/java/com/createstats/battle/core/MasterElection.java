package com.createstats.battle.core;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

// smallest packed pos wins. leave on unload/removal, window only covers a bridge that vanished silently
public final class MasterElection {
    public static final int WINDOW = 20;

    private final Long2LongOpenHashMap lastCheckIn = new Long2LongOpenHashMap();
    private boolean hasMaster;
    private long master;

    public void checkIn(long pos, long tick) {
        lastCheckIn.put(pos, tick);
    }

    public void leave(long pos) {
        lastCheckIn.remove(pos);
    }

    public boolean update(long tick) {
        boolean found = false;
        long best = 0L;
        LongIterator it = lastCheckIn.keySet().iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            if (tick - lastCheckIn.get(pos) > WINDOW) {
                it.remove();
                continue;
            }
            if (!found || pos < best) {
                best = pos;
                found = true;
            }
        }
        boolean changed = found != hasMaster || (found && best != master);
        hasMaster = found;
        master = found ? best : 0L;
        return changed;
    }

    public boolean hasMaster() {
        return hasMaster;
    }

    public long master() {
        return master;
    }

    public boolean isMaster(long pos) {
        return hasMaster && master == pos;
    }

    public int members() {
        return lastCheckIn.size();
    }

    public long[] positions() {
        return lastCheckIn.keySet().toLongArray();
    }
}
