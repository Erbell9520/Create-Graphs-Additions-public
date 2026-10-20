package com.createstats.seismic.archive;

import com.createstats.seismic.SurveyShells;
import com.createstats.seismic.VeinBook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// per dimension, the record belongs to the ground
public final class SeismicArchive {
    public static final int MAX_BLOWS = 2_048;
    public static final int MAX_CELLS = 2_000_000;

    static final double CLEAR_STEP = 8.0;

    static final long STALE_TICKS = 200L;

    private final List<ArchiveBlow> blows = new ArrayList<>();
    private final Map<Long, ArchiveBlow> open = new HashMap<>();

    private final Map<Integer, Double> cleared = new HashMap<>();
    private final Map<Long, Long> rigRevision = new HashMap<>();
    private int nextId = 1;
    private long revision;
    private long cellTotal;

    public long revision() {
        return revision;
    }

    public long revisionOf(long rigKey) {
        final Long r = rigRevision.get(rigKey);
        return r == null ? 0L : r;
    }

    public List<ArchiveBlow> blows() {
        return Collections.unmodifiableList(blows);
    }

    public long cellTotal() {
        return cellTotal;
    }

    public SurveyRecall recall(int x, int y, int z, String target) {
        final long key = ArchiveBlow.key(x, y, z);
        for (int i = blows.size() - 1; i >= 0; i--) {
            final ArchiveBlow b = blows.get(i);
            if (b.done && b.rigKey() == key && (target == null || b.target.equals(target))) {
                return SurveyRecall.of(b);
            }
        }
        return null;
    }

    public List<SurveyRecall> recallAll(int x, int y, int z) {
        final List<SurveyRecall> out = new ArrayList<>();
        for (final ArchiveBlow b : newestOfEachTarget(x, y, z)) {
            out.add(SurveyRecall.of(b));
        }
        return Collections.unmodifiableList(out);
    }

    List<ArchiveBlow> newestOfEachTarget(int x, int y, int z) {
        final long key = ArchiveBlow.key(x, y, z);
        final java.util.Set<String> seen = new java.util.HashSet<>();
        final List<ArchiveBlow> out = new ArrayList<>();
        for (int i = blows.size() - 1; i >= 0; i--) {
            final ArchiveBlow b = blows.get(i);
            if (b.done && b.rigKey() == key && seen.add(b.target)) {
                out.add(b.snapshot());
            }
        }
        return out;
    }

    public List<ArchiveBlow> snapshot(java.util.Collection<Long> rigKeys, long since) {
        final java.util.Set<Long> keys = new java.util.HashSet<>(rigKeys);
        final List<ArchiveBlow> out = new ArrayList<>();
        for (final ArchiveBlow b : blows) {
            if (keys.contains(b.rigKey()) && b.at >= since) {
                out.add(b.snapshot());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public ArchiveBlow open(int x, int y, int z) {
        return open.get(ArchiveBlow.key(x, y, z));
    }

    public ArchiveBlow begin(int x, int y, int z, String target, int reach, long at, int floor, long holeLo,
                             long holeHi) {
        final long key = ArchiveBlow.key(x, y, z);
        final ArchiveBlow before = open.remove(key);
        if (before != null) {
            finish(before);
        }
        final ArchiveBlow b = new ArchiveBlow(nextId++, x, y, z, target, reach, at, floor, holeLo, holeHi);
        blows.add(b);
        open.put(key, b);
        cleared.put(b.id, 0.0);
        touch(b);
        trim();
        return b;
    }

    public void step(ArchiveBlow b, double front, boolean full, List<VeinBook.Vein> closed, boolean done) {
        if (b == null || b.done) {
            return;
        }
        boolean changed = false;
        if (full && !b.full) {
            b.full = true;
            changed = true;
        }
        final double f = Math.max(b.front, Math.min(b.reach, front));
        if (f > b.front) {
            b.front = f;
            changed = true;
        }
        if (!b.full && b.recorded < b.front) {
            b.recorded = b.front;
        }
        if (closed != null && !closed.isEmpty()) {
            add(b, closed);
            changed = true;
        }
        final double was = cleared.getOrDefault(b.id, 0.0);
        if (done || b.recorded - was >= CLEAR_STEP) {
            if (clearOlder(b)) {
                changed = true;
            }
            cleared.put(b.id, b.recorded);
        }
        if (done) {
            finish(b);
            changed = true;
        }
        if (changed) {
            touch(b);
        }
        trim();
    }

    public void closeStale(long now) {
        if (open.isEmpty()) {
            return;
        }
        final List<ArchiveBlow> stale = new ArrayList<>();
        for (final ArchiveBlow b : open.values()) {
            if (now - b.at > STALE_TICKS || now < b.at) {
                stale.add(b);
            }
        }
        for (final ArchiveBlow b : stale) {
            clearOlder(b);
            finish(b);
            touch(b);
        }
    }

    public void closeAll() {
        for (final ArchiveBlow b : new ArrayList<>(open.values())) {
            clearOlder(b);
            finish(b);
            touch(b);
        }
    }

    private void finish(ArchiveBlow b) {
        b.done = true;
        open.remove(b.rigKey());
        cleared.remove(b.id);
    }

    private void touch(ArchiveBlow b) {
        revision++;
        rigRevision.put(b.rigKey(), revision);
    }

    private void add(ArchiveBlow b, List<VeinBook.Vein> closed) {
        final List<ArchiveBlow> newer = new ArrayList<>();
        for (final ArchiveBlow o : blows) {
            if (o != b && o.target.equals(b.target) && o.newerThan(b) && o.mayOverlap(b)) {
                newer.add(o);
            }
        }
        int n = 0;
        for (final VeinBook.Vein v : closed) {
            n += v.blocks().length;
        }
        final int[] adding = new int[n];
        int k = 0;
        for (final VeinBook.Vein v : closed) {
            b.veinsFound++;
            b.blocksFound += v.count();
            if (b.nearestFound < 0.0f || v.nearest() < b.nearestFound) {
                b.nearestFound = v.nearest();
            }
            for (final int p : v.blocks()) {
                final int wx = b.x + SurveyShells.dx(p);
                final int wy = b.y + SurveyShells.dy(p);
                final int wz = b.z + SurveyShells.dz(p);
                boolean taken = false;
                for (final ArchiveBlow o : newer) {
                    if (o.covers(wx, wy, wz)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    adding[k++] = ArchiveBlow.cell(v.kind(), p);
                }
            }
        }
        if (k == 0) {
            return;
        }
        final int[] merged = Arrays.copyOf(b.cells, b.cells.length + k);
        System.arraycopy(adding, 0, merged, b.cells.length, k);
        Arrays.sort(merged);

        int w = 0;
        for (int i = 0; i < merged.length; i++) {
            if (i == 0 || merged[i] != merged[i - 1]) {
                merged[w++] = merged[i];
            }
        }
        final int[] kept = w == merged.length ? merged : Arrays.copyOf(merged, w);
        cellTotal += kept.length - b.cells.length;
        b.cells = kept;
    }

    private boolean clearOlder(ArchiveBlow b) {
        if (b.recorded <= 0.0) {
            return false;
        }
        boolean any = false;
        for (final ArchiveBlow o : blows) {
            if (o == b || !o.target.equals(b.target) || !b.newerThan(o) || !o.mayOverlap(b) || o.cells.length == 0) {
                continue;
            }
            final int[] cells = o.cells;
            int w = 0;
            int[] kept = null;
            for (int i = 0; i < cells.length; i++) {
                final int c = cells[i];
                if (b.covers(o.cellX(c), o.cellY(c), o.cellZ(c))) {
                    if (kept == null) {
                        kept = Arrays.copyOf(cells, cells.length);
                        w = i;
                    }
                    continue;
                }
                if (kept != null) {
                    kept[w++] = c;
                }
            }
            if (kept != null) {
                o.cells = Arrays.copyOf(kept, w);
                cellTotal -= cells.length - w;
                touch(o);
                any = true;
            }
        }
        return any;
    }

    private void trim() {
        int i = 0;
        while ((blows.size() > MAX_BLOWS || cellTotal > MAX_CELLS) && i < blows.size()) {
            final ArchiveBlow b = blows.get(i);
            if (!b.done) {
                i++;
                continue;
            }
            blows.remove(i);
            cellTotal -= b.cells.length;
            revision++;
            rigRevision.put(b.rigKey(), revision);
        }
    }

    int nextId() {
        return nextId;
    }

    static SeismicArchive restore(List<ArchiveBlow> saved, int nextId) {
        final SeismicArchive a = new SeismicArchive();
        final List<ArchiveBlow> sorted = new ArrayList<>(saved);
        sorted.sort((p, q) -> Integer.compare(p.id, q.id));
        int maxId = 0;
        for (final ArchiveBlow b : sorted) {
            b.done = true;
            a.blows.add(b);
            a.cellTotal += b.cells.length;
            maxId = Math.max(maxId, b.id);
            a.rigRevision.put(b.rigKey(), 1L);
        }
        a.nextId = Math.max(nextId, maxId + 1);
        a.revision = 1L;
        a.trim();
        return a;
    }
}
